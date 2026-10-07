package homeplanet.ui;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import homeplanet.core.Event;
import homeplanet.core.HistoryLog;
import homeplanet.parser.CompanionMod;
import homeplanet.parser.DesignExport;
import homeplanet.parser.ShipChecks;
import homeplanet.parser.ShipDesign;

/**
 * Design Ship: lay out a new ship on a blank grid (rooms, doors, systems), give her art and weapon mounts, and build
 * her blueprint. The window is the shared {@link ShipEditorDialog}; what's hers is the name above the ship, the Loadout
 * step, Clear all, and Build blueprint.
 */
public class DesignDialog extends ShipEditorDialog {

	static final int COLS = DesignExport.COLS, ROWS = DesignExport.ROWS;

	private final ShipDesign original;
	/** Her built copy, if she has been built: what the mod holds now, and what a rebuild is compared against. */
	private final ShipDesign snapshot;
	private final List<String> otherNames;
	private boolean saved = false;
	private final JTextField nameField = new JTextField(20);

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

		// her name, above the ship
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		row.add(new JLabel("Design name:"));
		nameField.setText(d.name);
		nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
		});
		row.add(nameField);
		setLoadoutStep(new LoadoutPanel(d, this));

		addSideButton(button("Clear all", "Remove every room, door and system", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (JOptionPane.showConfirmDialog(DesignDialog.this, "Remove every room, door and system?", "Design Ship", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
				d.rooms.clear(); d.doors.clear(); d.systems.clear();
				for (java.util.Iterator<ShipDesign.Mount> it = d.mounts.iterator(); it.hasNext();) if (it.next().artillery) it.remove();
				editor.reset();
				changed();
			}
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
		if (d.rooms.isEmpty()) { editor.startPlacing(); say("Place her first room: click the grid for a 2 x 2 room."); }
		changed();
		editor.resetHistory();
		fitToScreen();
	}
	/** The name field into the design (the Loadout step writes its own fields as they're edited). */
	private void sync() {
		d.name = nameField.getText().trim();
	}
	protected String nextHint() {
		if (d.rooms.isEmpty()) return null;
		if (d.art.isEmpty()) return "Next: the Art step, her hull picture.";
		if (d.loadout == null) return "Next: the Loadout step.";
		return "Ready to build.";
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

	/** Build blueprint... : the build screen; Loadout... on it comes back here with the Loadout step in front. */
	private void buildBlueprint() {
		sync();
		BuildDialog.Choice c = BuildDialog.open(this, d, snapshot, otherNames);
		if (c == BuildDialog.Choice.CANCEL) return;
		if (c == BuildDialog.Choice.LOADOUT) { showStep("Loadout"); say("The Loadout step: set it, then Build blueprint again."); return; }
		if (!confirmVersion()) return;
		d.pendingBuild = true;
		saved = true;
		HistoryLog.entry("DESIGN", "Built " + d.name + " (" + DesignExport.bpId(d) + (d.pendingVersion > 0 ? ", v" + d.pendingVersion : "") + ")", null,
				Event.of("DESIGN").put("what", "built").put("design", d.name).put("design_id", d.id).put("blueprint", DesignExport.bpId(d)).put("version", d.pendingVersion > 0 ? String.valueOf(d.pendingVersion) : null));
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
		HistoryLog.entry("DESIGN", d.name + " (" + d.id + ")", lines, Event.of("DESIGN").put("what", "saved").put("design", d.name).put("design_id", d.id).put("rooms", d.rooms.size()).put("doors", d.doors.size()).details(lines));
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
