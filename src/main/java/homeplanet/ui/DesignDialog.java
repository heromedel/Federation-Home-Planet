package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import homeplanet.core.HistoryLog;
import homeplanet.model.Items;
import homeplanet.parser.CompanionMod;
import homeplanet.parser.DesignExport;
import homeplanet.parser.ShipChecks;
import homeplanet.parser.ShipDesign;

/**
 * Design Ship: lay out a new ship on a blank grid (rooms, doors, systems), give her art and weapon mounts, and build
 * her blueprint. The window is the shared {@link ShipEditorDialog}; what's hers is the name and the blueprint's
 * numbers above the help, Clear all, and Build blueprint.
 */
public class DesignDialog extends ShipEditorDialog {

	static final int COLS = 24, ROWS = 14;

	private final ShipDesign original;
	/** Her built copy, if she has been built: what the mod holds now, and what a rebuild is compared against. */
	private final ShipDesign snapshot;
	private final List<String> otherNames;
	private boolean saved = false;
	private final JTextField nameField = new JTextField(20);
	private final JSpinner hull, reactor, droneSlots;

	/**
	 * Opens the editor on a copy of the design; {@code otherNames} are the other designs' names (a duplicate is refused).
	 * Returns the edited design if it was saved, else null.
	 */
	public static ShipDesign open(Component owner, ShipDesign design, List<String> otherNames) {
		DesignDialog dlg = new DesignDialog(SwingUtilities.getWindowAncestor(owner), design, otherNames);
		dlg.setVisible(true);
		return dlg.saved ? dlg.d : null;
	}

	private DesignDialog(Window owner, ShipDesign design, List<String> otherNames) {
		super(owner, "Design Ship", ShipDesign.copy(design));
		this.original = design;
		this.snapshot = ShipDesign.snapshot(ShipDesign.load(), design.id);
		this.otherNames = otherNames;
		editor = new LayoutEditor(d, this, true, COLS, ROWS);

		// her name and the blueprint's numbers, above the help
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		row.add(new JLabel("Design name:"));
		nameField.setText(d.name);
		nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
		});
		row.add(nameField);
		hull = spinner(d.hull, 1, 60, "Hull points (the game's ships have 30)");
		reactor = spinner(d.reactor, 1, 30, "Reactor power at the start (the game's ships have 8)");
		droneSlots = spinner(d.droneSlots, 0, 3, "Drone slots (weapon slots are one per mount)");
		row.add(new JLabel("   Hull:")); row.add(hull);
		row.add(new JLabel(" Reactor:")); row.add(reactor);
		row.add(new JLabel(" Drone slots:")); row.add(droneSlots);

		addSideButton(button("Clear all", "Remove every room, door and system", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (JOptionPane.showConfirmDialog(DesignDialog.this, "Remove every room, door and system?", "Design Ship", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
				d.rooms.clear(); d.doors.clear(); d.systems.clear();
				for (java.util.Iterator<ShipDesign.Mount> it = d.mounts.iterator(); it.hasNext();) if (it.next().artillery) it.remove();
				editor.reset();
				changed();
			}
		}));
		addBottomButton(button("Loadout...", "Her class, default name, crew, weapons, drones, augments, missiles and drone parts, and which systems she starts with", new ActionListener() {
			public void actionPerformed(ActionEvent e) { loadoutThenMaybeBuild(); }
		}));
		addBottomButton(button("Build blueprint...", "Her report as she'd be commissioned, what's wrong or worth knowing, then into the Federation Home Planet Mod", new ActionListener() {
			public void actionPerformed(ActionEvent e) { buildBlueprint(); }
		}));
		addBottomButton(button("Save design", "Saves the design to work on later, without building the blueprint or making it available to commission ships (Ctrl+S)", new ActionListener() {
			public void actionPerformed(ActionEvent e) { save(); }
		}));
		addBottomButton(button("Close", null, new ActionListener() {
			public void actionPerformed(ActionEvent e) { closeGuarded(); }
		}));
		addHelpRowButton(button("Preview files", "The layout, chassis and blueprint text the game would get for her, as they stand", new ActionListener() {
			public void actionPerformed(ActionEvent e) { sync(); showText("Files for " + (d.name.isEmpty() ? d.id : d.name), DesignExport.preview(d)); }
		}));
		buildUi(row, true);
		if (d.rooms.isEmpty()) { editor.startPlacing(); say("Click the grid to place a 2 x 2 room."); }
		changed();
		editor.resetHistory();
		fitToScreen();
	}
	private JSpinner spinner(int value, int min, int max, String tip) {
		final JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
		s.setToolTipText(tip);
		((JSpinner.DefaultEditor) s.getEditor()).getTextField().setColumns(2);
		s.addChangeListener(new javax.swing.event.ChangeListener() {
			public void stateChanged(javax.swing.event.ChangeEvent e) { changed(); }
		});
		return s;
	}
	/** The fields into the design. */
	private void sync() {
		d.name = nameField.getText().trim();
		d.hull = (Integer) hull.getValue();
		d.reactor = (Integer) reactor.getValue();
		d.droneSlots = (Integer) droneSlots.getValue();
	}

	// ---- ShipEditorDialog ----

	protected ShipChecks.Report check() {
		sync();
		return ShipChecks.check(d, ShipChecks.Context.DESIGN, null, otherNames);
	}
	protected String primaryVerb() { return "saves the design"; }
	protected String primaryName() { return "Save"; }
	protected void primaryAction() { save(); }
	protected boolean dirty() {
		sync();
		return !ShipDesign.xmlOf(original).equals(ShipDesign.xmlOf(d));
	}
	public String cannotTakeOff(String id) { return null; }
	public CompanionMod.Sys original(String id) { return null; }

	// ---- her blueprint ----

	/**
	 * The dialog Build blueprint uses, on its own: her loadout, screen position, and which systems she starts with at
	 * what level (Loadout... opens it any time; Build opens it as the last look). True when accepted and applied.
	 */
	private enum After { CANCEL, OK, BUILD }
	private After loadoutDialog(String title, String verb, String andThen) {
		sync();
		// her screen position, and which systems she starts with at what level
		JPanel extra = new JPanel(new BorderLayout(0, 6));
		int[] auto = DesignExport.offsets(d);
		final JCheckBox autoPos = new JCheckBox("worked out from the art", d.offX < 0 && d.offY < 0);
		final JSpinner ox = new JSpinner(new SpinnerNumberModel(auto[0], 0, 10, 1)), oy = new JSpinner(new SpinnerNumberModel(auto[1], 0, 10, 1));
		JPanel pos = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		pos.add(new JLabel("Weapon slots: " + DesignExport.weaponSlots(d) + " (one per mount).   Place on screen, in squares (check in game):  across")); pos.add(ox);
		pos.add(new JLabel("down")); pos.add(oy); pos.add(autoPos);
		ox.setEnabled(!autoPos.isSelected()); oy.setEnabled(!autoPos.isSelected());
		autoPos.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { ox.setEnabled(!autoPos.isSelected()); oy.setEnabled(!autoPos.isSelected()); }
		});
		extra.add(pos, BorderLayout.NORTH);
		JPanel sys = new JPanel(new GridLayout(0, 3, 10, 2));
		final java.util.Map<String, JCheckBox> starts = new java.util.LinkedHashMap<String, JCheckBox>();
		final java.util.Map<String, JSpinner> levels = new java.util.LinkedHashMap<String, JSpinner>();
		final String[] artilleryWeapon = {null};
		for (CompanionMod.Sys s : d.systems.values()) {
			net.blerf.ftl.xml.SystemBlueprint sb = net.blerf.ftl.parser.DataManager.get().getSystem(s.id);
			int max = sb != null && sb.getMaxPower() > 0 ? sb.getMaxPower() : 8;
			JCheckBox cb = new JCheckBox(Items.systemTitle(s.id), !d.notAtStart.contains(s.id));
			cb.setToolTipText("Ticked: she starts with it installed. Unticked: its room is ready, to buy or install later");
			JSpinner lv = new JSpinner(new SpinnerNumberModel(Math.max(1, Math.min(max, s.power)), 1, max, 1));
			lv.setToolTipText("Starting level");
			starts.put(s.id, cb);
			levels.put(s.id, lv);
			JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			cell.add(cb); cell.add(new JLabel("level")); cell.add(lv);
			if (s.id.equals("artillery")) {
				final JButton weapon = new JButton(s.weapon == null ? "Choose weapon..." : ArtilleryPicker.label(s.weapon));
				weapon.setToolTipText(s.weapon == null ? "Which weapon her artillery fires" : ArtilleryPicker.tip(s.weapon));
				weapon.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						String w = ArtilleryPicker.choose(DesignDialog.this, artilleryWeapon[0]);
						if (w == null) return;
						artilleryWeapon[0] = w;
						weapon.setText(ArtilleryPicker.label(w));
						weapon.setToolTipText(ArtilleryPicker.tip(w));
					}
				});
				artilleryWeapon[0] = s.weapon;
				cell.add(weapon);
			}
			sys.add(cell);
		}
		JPanel sysWrap = new JPanel(new BorderLayout());
		sysWrap.add(new JLabel("Systems (ticked: installed at the start):"), BorderLayout.NORTH);
		sysWrap.add(sys, BorderLayout.CENTER);
		extra.add(sysWrap, BorderLayout.CENTER);

		CompanionMod.Loadout start = d.loadout != null ? CompanionMod.copy(d.loadout) : new CompanionMod.Loadout();
		if (d.loadout == null) {
			start.className = d.name;
			start.shipName = "The " + d.name;
			start.crew.put("human", 3);
			start.missiles = 8;
			start.droneParts = 2;
		}
		BlueprintDialog.Result r = BlueprintDialog.open(this, title, null, start, null,
				DesignExport.weaponSlots(d), Math.max(1, d.droneSlots), d.systems.containsKey("drones"), extra, true, d.starter, verb, andThen);
		if (r == null) return After.CANCEL;
		if (autoPos.isSelected()) { d.offX = -1; d.offY = -1; } else { d.offX = (Integer) ox.getValue(); d.offY = (Integer) oy.getValue(); }
		if (d.systems.containsKey("artillery") && artilleryWeapon[0] != null) d.systems.get("artillery").weapon = artilleryWeapon[0];
		d.notAtStart.clear();
		for (java.util.Map.Entry<String, JCheckBox> e : starts.entrySet()) {
			if (!e.getValue().isSelected()) d.notAtStart.add(e.getKey());
			d.systems.get(e.getKey()).power = (Integer) levels.get(e.getKey()).getValue();
		}
		d.loadout = r.loadout;
		d.starter = r.starter;
		changed();
		refreshChecks();
		return r.andThen ? After.BUILD : After.OK;
	}
	/** Loadout... : the loadout dialog, and on to the build screen if its Build... was pressed. */
	private void loadoutThenMaybeBuild() {
		After a = loadoutDialog("Loadout: " + d.name, "OK", "Build...");
		if (a == After.OK) say("Loadout set.");
		else if (a == After.BUILD) buildBlueprint();
	}

	/** Build blueprint... : the build screen; Loadout... on it goes to the loadout dialog and, from there, back here. */
	private void buildBlueprint() {
		sync();
		while (true) {
			BuildDialog.Choice c = BuildDialog.open(this, d, snapshot, otherNames);
			if (c == BuildDialog.Choice.CANCEL) return;
			if (c == BuildDialog.Choice.LOADOUT) {
				if (loadoutDialog("Loadout: " + d.name, "OK", "Build...") != After.BUILD) return;
				continue;
			}
			break;
		}
		if (!confirmVersion()) return;
		d.pendingBuild = true;
		saved = true;
		HistoryLog.entry("DESIGN", "Built " + d.name + " (" + DesignExport.bpId(d) + (d.pendingVersion > 0 ? ", v" + d.pendingVersion : "") + ")");
		dispose();
	}

	private void save() {
		sync();
		if (d.name.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Give the design a name first.", "Design Ship", JOptionPane.INFORMATION_MESSAGE);
			nameField.requestFocus();
			return;
		}
		List<String> p = check().problems;
		if (!p.isEmpty()) {
			Object[] opts = {"Save", "Keep working"};
			int r = JOptionPane.showOptionDialog(this, "She isn't finished yet:\n  " + String.join("\n  ", p)
					+ "\n\nSaving keeps the design to work on later, without building the blueprint or making it available to commission ships.",
					"Design Ship", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opts, opts[0]);
			if (r != 0) return;
		}
		saved = true;
		List<String> lines = new ArrayList<String>();
		lines.add("Rooms " + d.rooms.size() + ", doors " + d.doors.size() + ", systems " + d.systems.keySet());
		HistoryLog.entry("DESIGN", d.name + " (" + d.id + ")", lines);
		dispose();
	}
	/**
	 * A built blueprint that changed in any way the game sees: ships already built from it could be altered by the game
	 * (a newly added system given to them, say) or stop loading, so the change becomes a new version (always, when ships
	 * use it; otherwise the player chooses). False: keep editing.
	 */
	private boolean confirmVersion() {
		d.pendingVersion = 0;
		if (snapshot == null || !DesignExport.changesBlueprint(snapshot, d)) return true;
		List<String> ships = shipsUsing(DesignExport.bpId(snapshot));
		int next = nextVersion(snapshot.id);
		String cur = snapshot.name + " v" + snapshot.version, nw = d.name + " v" + next;
		if (!ships.isEmpty()) {
			Object[] opts = {"Build as " + nw, "Keep editing"};
			int r = JOptionPane.showOptionDialog(this, (ships.size() == 1 ? "1 ship was" : ships.size() + " ships were") + " built from " + cur + ": " + String.join(", ", ships) + ".\n"
					+ "Any change to her blueprint reaches ships already built from it: the game can alter them (give them a system you\n"
					+ "added, say), and room or door changes stop their saves loading. So this is built as a new blueprint, " + nw + ".\n"
					+ "They keep " + cur + "; ships commissioned from now on use " + nw + ".",
					"New blueprint version", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opts, opts[0]);
			if (r != 0) return false;
			d.pendingVersion = next;
			return true;
		}
		Object[] opts = {"New blueprint (" + nw + ")", "Change " + cur, "Keep editing"};
		int r = JOptionPane.showOptionDialog(this, cur + " is a built blueprint, and no ship uses it right now.\n"
				+ "Build these changes as a new blueprint, or replace the existing one?",
				"Blueprint version", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
		if (r == 0) { d.pendingVersion = next; return true; }
		return r == 1;
	}
	/** The next version number for a design: one past the highest it (or a kept old version of it) has had. */
	static int nextVersion(String id) {
		int max = 1;
		for (ShipDesign x : ShipDesign.load()) if (id.equals(x.id)) max = Math.max(max, x.version);
		return max + 1;
	}
	/** The names of the player's ships (docked, boarded or in the junkyard) whose saves name this blueprint. */
	static List<String> shipsUsing(String bpId) {
		List<String> out = new ArrayList<String>();
		for (homeplanet.vault.Ship s : homeplanet.vault.Vault.get().usingBlueprint(bpId)) out.add(s.name);
		out.addAll(homeplanet.vault.Vault.get().otherFleetUsing(bpId));
		return out;
	}
}
