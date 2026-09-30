package homeplanet.ui;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.blerf.ftl.model.shiplayout.ShipLayout;
import net.blerf.ftl.model.shiplayout.ShipLayoutRoom;
import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.xml.ShipBlueprint;
import net.blerf.ftl.xml.ShipChassis;

import homeplanet.core.HomePlanet;
import homeplanet.core.HistoryLog;
import homeplanet.core.Slipstream;
import homeplanet.model.Items;
import homeplanet.parser.CompanionMod;
import homeplanet.parser.CompanionMod.Remodel;
import homeplanet.parser.CompanionMod.Sys;
import homeplanet.parser.Retrofit;
import homeplanet.parser.ShipArt;
import homeplanet.parser.ShipChecks;
import homeplanet.parser.ShipDesign;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remodel: move a retrofitted ship's systems to other rooms (and their stations to other squares) and her doors, or
 * overhaul her (rooms, art and mounts too), then finalize a custom blueprint for her, which goes into the companion
 * mod. The window is the shared {@link ShipEditorDialog}; here the rooms are her model's until an overhaul.
 */
public class RemodelDialog extends ShipEditorDialog {
	private static final Logger log = LoggerFactory.getLogger(RemodelDialog.class);

	private final CargoBayUI bay;
	private final SavedGameState save;
	private final ShipState ship;
	private final ShipBlueprint current;      // the blueprint she flies now
	private final ShipBlueprint plainBp;      // the plain _HP copy of her model: "original layout"
	private ShipBlueprint shown;              // whose rooms are on the grid: current, or plainBp after Restore original layout
	private ShipLayout layout;                // shown's
	private ShipChassis chassis;
	private final Map<String, Sys> original;  // systems as the plain copy has them
	private final List<CompanionMod.Door> origDoors; // the model's doors
	// the overhaul: rooms, art, weapon mounts and shield editable, with Design Ship's art tools
	private boolean overhaul = false;
	private JButton overhaulBtn;
	private int startMinX, startMinY;
	private String openKey;                   // the layout as the window opened (for the unsaved-changes guard)
	boolean finalized = false;
	static final int MARGIN = 3; // squares of room around the model's rooms while overhauling

	/** Opens the window for the boarded ship. Returns true if a blueprint was finalized (the bay was saved). */
	public static boolean open(CargoBayUI bay) {
		Window w = SwingUtilities.getWindowAncestor(bay);
		String id = bay.currentSave.getPlayerShip().getShipBlueprintId();
		ShipBlueprint current = DataManager.get().getShips().get(id), plain = DataManager.get().getShips().get(Retrofit.vanillaId(id) + Retrofit.SUFFIX);
		if (current == null || plain == null) {
			JOptionPane.showMessageDialog(w, "The Home Planet Station has no blueprint for " + id + ".", "Remodel", JOptionPane.WARNING_MESSAGE);
			return false;
		}
		RemodelDialog d = new RemodelDialog(w, bay, current, plain);
		d.setVisible(true);
		return d.finalized;
	}

	private RemodelDialog(Window owner, CargoBayUI bay, ShipBlueprint current, ShipBlueprint plainBp) {
		super(owner, "Remodel " + bay.currentSave.getPlayerShipName(), new ShipDesign());
		this.bay = bay;
		this.save = bay.currentSave;
		this.ship = save.getPlayerShip();
		this.current = current;
		this.plainBp = plainBp;
		show(current);
		original = CompanionMod.layoutOf(plainBp);
		origDoors = CompanionMod.doorsOf(DataManager.get().getShipLayout(plainBp.getLayoutId()));
		for (int i = 0; i < layout.getRoomCount(); i++) {
			ShipLayoutRoom r = layout.getRoom(i);
			d.rooms.add(new ShipDesign.Room(r.locationX, r.locationY, r.squaresH, r.squaresV));
		}
		d.doors.addAll(CompanionMod.doorsOf(layout));
		for (Sys s : CompanionMod.layoutOf(current).values()) d.systems.put(s.id, s.copy());

		editor = new LayoutEditor(d, this, false, 0, 0);
		showModelArt(current);
		overhaulBtn = button("Overhaul deck plan...", "Move, add and remove rooms, and move her art, weapon mounts and shield", new ActionListener() {
			public void actionPerformed(ActionEvent e) { askOverhaul(); }
		});
		addSideButton(overhaulBtn);
		addSideButton(button("Restore original layout", "Put every room, system and door back where this ship model has them", new ActionListener() {
			public void actionPerformed(ActionEvent e) { restoreOriginal(); }
		}));
		addBottomButton(button("Finalize blueprint", "Make this layout her blueprint (saves the Cargo Bay too; Ctrl+S)", new ActionListener() {
			public void actionPerformed(ActionEvent e) { finalizeBlueprint(); }
		}));
		addBottomButton(button("Cancel", null, new ActionListener() {
			public void actionPerformed(ActionEvent e) { closeGuarded(); }
		}));
		buildUi(null, false);
		Remodel had = CompanionMod.find(CompanionMod.load(), current.getId());
		if (had != null && had.geometry != null) enterOverhaul(had.geometry); // she's been overhauled: carry on where she was left
		editor.resetHistory();
		openKey = ShipDesign.editKey(d);
		refreshChecks();
		fitToScreen();
		say(overhaul ? "Overhaul: rooms, doors, systems, art, mounts and shield are all editable."
				: "Click a system to move it. Only systems and doors move here: Overhaul deck plan... unlocks her rooms, art and weapon mounts.");
	}

	// ---- ShipEditorDialog ----

	protected ShipChecks.Report check() {
		return ShipChecks.check(d, overhaul ? ShipChecks.Context.OVERHAUL : ShipChecks.Context.REMODEL, overhaul ? weaponSlots() : null, null);
	}
	protected String primaryVerb() { return "finalizes the blueprint"; }
	protected String primaryName() { return "Finalize"; }
	protected void primaryAction() { finalizeBlueprint(); }
	protected boolean dirty() { return !ShipDesign.editKey(d).equals(openKey); }
	public String cannotTakeOff(String id) {
		return installed(id) ? Items.systemTitle(id) + " is installed on the ship. Store it in the Cargo Bay first to take it off the blueprint." : null;
	}
	/** The model's own place for a system (null in an overhaul: the rooms have moved). */
	public Sys original(String id) { return overhaul ? null : original.get(id); }

	private int weaponSlots() { return current.getWeaponSlots() == null ? 4 : current.getWeaponSlots(); }

	// ---- systems ----

	/** Installed on the save (capacity > 0): such a system can be moved but not taken off the blueprint. */
	private boolean installed(String id) {
		SystemType t = SystemType.findById(id);
		SystemState st = t == null ? null : ship.getSystem(t);
		return st != null && st.getCapacity() > 0;
	}

	/** The blueprint whose rooms, art and mounts the grid shows (an overhaul starts from these). */
	private void show(ShipBlueprint bp) {
		shown = bp;
		layout = DataManager.get().getShipLayout(bp.getLayoutId());
		chassis = DataManager.get().getShipChassis(bp.getLayoutId());
	}

	private void restoreOriginal() {
		// the model's own rooms, doors, systems and art
		show(plainBp);
		ShipLayout plain = layout;
		d.rooms.clear();
		for (int i = 0; i < plain.getRoomCount(); i++) {
			ShipLayoutRoom r = plain.getRoom(i);
			d.rooms.add(new ShipDesign.Room(r.locationX, r.locationY, r.squaresH, r.squaresV));
		}
		d.systems.clear();
		for (Sys s : original.values()) d.systems.put(s.id, s.copy());
		d.doors.clear();
		for (CompanionMod.Door x : origDoors) d.doors.add(new CompanionMod.Door(x.x, x.y, x.a, x.b, x.v));
		boolean wasOverhaul = overhaul;
		if (overhaul) {
			overhaul = false;
			editor.setRoomsEditable(false);
			showArtPanel(false);
			overhaulBtn.setEnabled(true);
			d.mounts.clear();
		}
		showModelArt(plainBp);
		editor.reset();
		if (wasOverhaul) editor.resetHistory(); // the rooms can't change outside an overhaul, so there's no going back into one
		else editor.commit();
		refreshHelp();
		refreshChecks();
		fitToScreen();
		say("Original layout restored.");
	}
	private void showModelArt(ShipBlueprint bp) {
		ShipChassis ch = DataManager.get().getShipChassis(bp.getLayoutId());
		String gfx = bp.getGraphicsBaseName();
		int fx = 0, fy = 0;
		if (ch.getOffsets() != null && ch.getOffsets().floorOffset != null) { fx = ch.getOffsets().floorOffset.x; fy = ch.getOffsets().floorOffset.y; }
		int bx = ch.getImageBounds().x, by = ch.getImageBounds().y;
		editor.setArt(LayoutEditor.image("img/ship/" + gfx + "_base.png"), bx, by, LayoutEditor.image("img/ship/" + gfx + "_floor.png"), bx + fx, by + fy,
				ch.getImageBounds().w, ch.getImageBounds().h);
	}

	// ---- the overhaul ----

	private void askOverhaul() {
		int r = JOptionPane.showConfirmDialog(this, "Overhauling her deck plan lets you move, add and remove rooms, move her art and weapon mounts, reshape her shield,\n"
				+ "and drop or replace her floor art. Systems and doors stay where they can; rooms holding installed systems stay.\n"
				+ "Crew standing where a room no longer is are moved to a free square when you finalize.\n\n"
				+ "Restore original layout undoes the whole overhaul.", "Overhaul deck plan: " + save.getPlayerShipName(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (r != JOptionPane.OK_OPTION) return;
		enterOverhaul(null);
		fitToScreen();
		say("Overhaul: use the room tools on the right, and the art tools beside them. Finalize when she's ready.");
	}

	/** Opens the rooms and art for editing: from her earlier overhaul (g) if she had one, else from her current layout and art. */
	private void enterOverhaul(ShipDesign g) {
		if (overhaul) return;
		overhaul = true;
		// room to grow: the rooms move a little way from the grid's edge
		for (ShipDesign.Room r : d.rooms) { r.x += MARGIN; r.y += MARGIN; }
		for (CompanionMod.Door x : d.doors) { x.x += MARGIN; x.y += MARGIN; }
		startMinX = Integer.MAX_VALUE; startMinY = Integer.MAX_VALUE;
		for (ShipDesign.Room r : d.rooms) { startMinX = Math.min(startMinX, r.x); startMinY = Math.min(startMinY, r.y); }
		d.name = save.getPlayerShipName();
		d.id = "REMODEL_" + Retrofit.vanillaId(ship.getShipBlueprintId()).replace("PLAYER_SHIP_", "");
		if (g != null) {
			int gx = Integer.MAX_VALUE, gy = Integer.MAX_VALUE;
			for (ShipDesign.Room r : g.rooms) { gx = Math.min(gx, r.x); gy = Math.min(gy, r.y); }
			d.art = g.art; d.floor = g.floor; d.floorX = g.floorX; d.floorY = g.floorY; d.artScale = g.artScale;
			d.artX = g.artX - gx * LayoutEditor.SQ + MARGIN * LayoutEditor.SQ;
			d.artY = g.artY - gy * LayoutEditor.SQ + MARGIN * LayoutEditor.SQ;
			for (ShipDesign.Mount m : g.mounts) d.mounts.add(m.copy());
			d.ellipseW = g.ellipseW; d.ellipseH = g.ellipseH; d.ellipseX = g.ellipseX; d.ellipseY = g.ellipseY;
			d.gibs = g.gibs; d.gibFiles.addAll(g.gibFiles);
		} else {
			String gfx = shown.getGraphicsBaseName();
			d.art = "game:" + gfx;
			d.floor = DataManager.get().hasResourceInputStream("img/ship/" + gfx + "_floor.png") ? "game:" + gfx : "";
			d.artScale = 100;
			d.artX = chassis.getImageBounds().x + MARGIN * LayoutEditor.SQ;
			d.artY = chassis.getImageBounds().y + MARGIN * LayoutEditor.SQ;
			if (chassis.getOffsets() != null && chassis.getOffsets().floorOffset != null) {
				d.floorX = chassis.getOffsets().floorOffset.x; d.floorY = chassis.getOffsets().floorOffset.y;
			}
			ShipArt.mountsFrom(d, chassis, shown);
			if (layout.getShieldEllipse() != null) {
				java.awt.Rectangle e = layout.getShieldEllipse();
				d.ellipseW = e.width; d.ellipseH = e.height; d.ellipseX = e.x; d.ellipseY = e.y;
			}
			d.gibs = "auto";
		}
		editor.setRoomsEditable(true);
		showArtPanel(true);
		overhaulBtn.setEnabled(false);
		editor.reset();
		editor.resetHistory();
		refreshChecks();
	}

	// ---- finalize ----

	private static boolean same(Sys a, Sys b) {
		if (a == null || b == null) return a == b;
		return a.room == b.room && eq(a.dir, b.dir) && eq(a.square, b.square);
	}
	private static boolean eq(Object a, Object b) { return a == null ? b == null : a.equals(b); }

	/** The systems that differ from the plain copy: moved, added (room >= 0) or left out (room -1). */
	private Map<String, Sys> diffs() {
		Map<String, Sys> out = new LinkedHashMap<String, Sys>();
		for (Sys s : d.systems.values()) if (!same(s, original.get(s.id))) out.put(s.id, s.copy());
		for (Sys o : original.values()) if (!d.systems.containsKey(o.id)) { Sys gone = new Sys(o.id); gone.room = -1; out.put(o.id, gone); }
		return out;
	}

	private void finalizeBlueprint() {
		List<String> p = check().problems;
		if (!p.isEmpty()) {
			JOptionPane.showMessageDialog(this, "The " + (overhaul ? "overhaul" : "layout") + " isn't finished:\n  " + String.join("\n  ", p), "Finalize blueprint", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Map<String, Sys> moved = diffs();
		boolean doorsChanged = overhaul || !new java.util.HashSet<CompanionMod.Door>(d.doors).equals(new java.util.HashSet<CompanionMod.Door>(origDoors));
		String id = ship.getShipBlueprintId();
		boolean already = CompanionMod.isRemodelId(id);
		if (moved.isEmpty() && !doorsChanged && !already) {
			JOptionPane.showMessageDialog(this, "Nothing has moved: this is the model's own layout.", "Remodel", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		// FTL flying her would stop the save at the end, after her blueprint had already changed: ask first
		if (bay.currentShip != null && bay.currentShip.isBoarded() && !homeplanet.core.GameGuard.allows(this, "finalize her blueprint")) return;
		String name = save.getPlayerShipName();
		List<Remodel> all = CompanionMod.load();
		File remodelsFile = CompanionMod.remodelsFile();
		byte[] remodelsBefore;
		try { remodelsBefore = remodelsFile.isFile() ? homeplanet.core.SafeFiles.read(remodelsFile) : null; }
		catch (java.io.IOException e) { HomePlanet.showErrorDialog("The Home Planet Station couldn't read " + remodelsFile + ":\n" + e); return; }
		Remodel before = already ? CompanionMod.find(all, id) : null;
		String intro = "The Federation Home Planet draws up a custom blueprint for " + homeplanet.parser.XmlText.text(name) + " and saves the Cargo Bay as it stands.<br>"
				+ "She can't launch until the updated " + CompanionMod.TITLE + " is sent to FTL via Slipstream. ";
		boolean plain = moved.isEmpty() && !doorsChanged;
		BlueprintDialog.Result chosen = null;
		if (plain) {
			int r = JOptionPane.showConfirmDialog(this, "Nothing is moved any more: " + name + " goes back to the model's own layout.\n\nContinue?",
					"Finalize blueprint", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
			if (r != JOptionPane.OK_OPTION) return;
		} else {
			// her name, class and new-game loadout: what she carries now, or what her blueprint already says
			CompanionMod.Loadout now = CompanionMod.loadoutFromShip(ship, id);
			CompanionMod.Loadout start = before != null && before.loadout != null ? CompanionMod.copy(before.loadout) : now;
			chosen = BlueprintDialog.open(this, "Finalize blueprint", intro, start, now, id, true, before != null && before.starter, "Finalize");
			if (chosen == null) return;
		}
		String vanilla = Retrofit.vanillaId(id);
		Remodel mine = already ? CompanionMod.find(all, id) : null;
		if (mine != null && usedByAnotherSave(id)) mine = null; // a hand-copied save shares it: give this ship her own
		if (plain) {
			// back to the plain layout: she returns to the plain copy; her old remodel stays on file
			Retrofit.apply(save, false);
		} else {
			if (mine == null) {
				mine = CompanionMod.create(vanilla, name, all);
				all.add(mine);
			}
			mine.ship = name;
			mine.starter = chosen.starter;
			mine.loadout = chosen.loadout;
			mine.systems.clear();
			mine.systems.putAll(moved);
			mine.doors = doorsChanged ? new ArrayList<CompanionMod.Door>(d.doors) : null;
			int dx = 0, dy = 0;
			if (overhaul) {
				mine.geometry = ShipDesign.copy(d);
				mine.geometry.id = mine.id;
				int nx = Integer.MAX_VALUE, ny = Integer.MAX_VALUE;
				for (ShipDesign.Room r : d.rooms) { nx = Math.min(nx, r.x); ny = Math.min(ny, r.y); }
				dx = startMinX - nx; dy = startMinY - ny; // how far her old rooms sit from where they were, in the new file's squares
				// she stays where she was on screen: the file's origin moved by dx, dy squares
				mine.geometry.offX = Math.max(0, layout.getOffsetX() - dx);
				mine.geometry.offY = Math.max(0, layout.getOffsetY() - dy);
				// the layout file starts at square (0, 0): her own doors as the file has them
				mine.doors = new ArrayList<CompanionMod.Door>();
				for (CompanionMod.Door x : d.doors) mine.doors.add(new CompanionMod.Door(x.x - nx, x.y - ny, x.a, x.b, x.v));
			} else {
				mine.geometry = null;
			}
			try {
				CompanionMod.save(all);
			} catch (Exception e) {
				log.error("Could not write " + CompanionMod.remodelsFile(), e);
				HomePlanet.showErrorDialog("The Home Planet Station couldn't save her remodel to " + CompanionMod.remodelsFile().getAbsolutePath() + ":\n" + e + "\n\nHer blueprint is unchanged.");
				return;
			}
			CompanionMod.register(all); // she can be drawn right away
			Retrofit.switchTo(save, mine.id, dx, dy); // blueprint, layout, rooms, crew and doors together
		}
		List<String> lines = new ArrayList<String>();
		for (Sys s : moved.values()) lines.add(Items.systemTitle(s.id) + (s.room < 0 ? ": off the blueprint" : ": room " + s.room + (s.square != null ? ", square " + s.square + (s.dir != null ? " facing " + s.dir : "") : "")));
		if (doorsChanged) lines.add("Doors: " + d.doors.size() + " (the model has " + origDoors.size() + ")");
		if (overhaul) lines.add("Overhauled: " + d.rooms.size() + " rooms, " + d.mounts.size() + " weapon mounts, art " + d.art + (d.floor.isEmpty() ? ", no floor" : ""));
		if (!bay.saveAll()) {
			// her save wasn't written: the blueprints go back to how they were, and the Cargo Bay to what's on disk
			try {
				if (remodelsBefore == null) remodelsFile.delete(); else homeplanet.core.SafeFiles.write(remodelsFile, remodelsBefore);
				CompanionMod.register(CompanionMod.load());
			} catch (java.io.IOException e) {
				log.error("Could not put back " + remodelsFile, e);
			}
			bay.init();
			dispose(); // this window still holds the old copy of her save
			HomePlanet.showErrorDialog("Her remodel was called off: her save and her blueprint are as they were before.");
			return;
		}
		HistoryLog.entry("REMODEL", name + " -> " + ship.getShipBlueprintId(), lines);
		ShipArt.sweep(); // pictures her old overhaul no longer uses (only now that everything is saved)
		finalized = true;
		openKey = ShipDesign.editKey(d);
		File mod = Slipstream.writeMod();
		dispose();
		String where = mod == null ? "The " + CompanionMod.TITLE + " could not be written." : "The updated mod is at:\n" + mod.getPath();
		Object[] options = {"Patch Now", "OK"};
		int r = JOptionPane.showOptionDialog(bay, name + " now uses blueprint " + ship.getShipBlueprintId() + ".\n\n" + where + "\n\n"
				+ (Slipstream.dir() == null ? "Move it into Slipstream's mods folder and send it to FTL, or press Patch Now and The Home Planet Station will send it."
						: "Send it to FTL via Slipstream, or press Patch Now and The Home Planet Station will send it."),
				"Blueprint finalized", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
		if (r == 0) PatchDialog.open(bay);
	}

	/** True if another save file (docked, boarded or in the junkyard) also names this blueprint. */
	private boolean usedByAnotherSave(String bpId) {
		for (homeplanet.vault.Ship s : homeplanet.vault.Vault.get().usingBlueprint(bpId)) if (s != bay.currentShip) return true;
		return false;
	}
}
