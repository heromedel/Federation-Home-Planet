package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import homeplanet.core.HomePlanet;
import homeplanet.core.HistoryLog;
import homeplanet.parser.ShipDesign;

/** Design Ship: the list of ship designs (new, open, delete). */
public class DesignListDialog extends JDialog {
	private final List<ShipDesign> designs = ShipDesign.load();
	private final DefaultListModel<String> model = new DefaultListModel<String>();
	private final JList<String> list = new JList<String>(model);
	private final List<ShipDesign> shown = new java.util.ArrayList<ShipDesign>(); // the list's rows (kept old versions aren't shown)
	/** The blueprints of every version of a design (the current one and any kept for ships). */
	private List<String> bpIds(String id) {
		List<String> out = new java.util.ArrayList<String>();
		for (ShipDesign x : designs) if (id.equals(x.id) && x.built && !x.isWorking()) out.add(homeplanet.parser.DesignExport.bpId(x));
		return out;
	}

	public static void open(Component owner) {
		new DesignListDialog(owner).setVisible(true);
	}

	private DesignListDialog(Component owner) {
		super(SwingUtilities.getWindowAncestor(owner), "Design Ship", ModalityType.APPLICATION_MODAL);
		refresh();
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) openSelected(); }
		});
		JScrollPane sp = new JScrollPane(list);
		sp.setPreferredSize(new Dimension(420, 220));
		JPanel body = new JPanel(new BorderLayout(0, 6));
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		body.add(new JLabel("<html>Ships you've designed from scratch. Open one and press Build blueprint to put her in the<br>Federation Home Planet Mod; tick her as a starter ship to commission her.</html>"), BorderLayout.NORTH);
		body.add(sp, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton nw = new JButton("New design");
		JButton game = new JButton("From a game ship...");
		game.setToolTipText("A new design that starts as a copy of one of the game's ships: her rooms, systems, art and loadout");
		JButton dup = new JButton("Duplicate");
		dup.setToolTipText("A new design that starts as a copy of the selected one");
		JButton op = new JButton("Open");
		JButton del = new JButton("Delete");
		JButton close = new JButton("Close");
		nw.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { newDesign(); } });
		game.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { fromGameShip(); } });
		dup.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { duplicateSelected(); } });
		op.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { openSelected(); } });
		del.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { deleteSelected(); } });
		close.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		buttons.add(nw); buttons.add(game); buttons.add(dup); buttons.add(op); buttons.add(del); buttons.add(close);
		getContentPane().add(body, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setLocationRelativeTo(owner);
	}

	private void refresh() {
		model.clear();
		shown.clear();
		for (ShipDesign d : designs) {
			if (!d.isWorking()) continue; // built copies and kept old versions, for the ships built from them
			shown.add(d);
			ShipDesign snap = ShipDesign.snapshot(designs, d.id);
			int ships = 0;
			for (String bp : bpIds(d.id)) ships += DesignDialog.shipsUsing(bp).size();
			String state;
			if (snap == null) state = "draft" + (homeplanet.parser.DesignExport.problems(d).isEmpty() ? "" : ", unfinished");
			else state = "built" + (snap.version > 1 ? " v" + snap.version : "") + (snap.starter ? ", starter ship" : "")
					+ (homeplanet.parser.DesignExport.changesBlueprint(snap, d) ? ", unbuilt changes" : "");
			model.addElement(d.name + "   (" + d.rooms.size() + " rooms, " + d.made + ")   - " + state
					+ (ships > 0 ? ", " + (ships == 1 ? "1 ship" : ships + " ships") : ""));
		}
	}
	/** The other designs' names (kept old versions aside), so the editor can refuse a duplicate. */
	private List<String> namesBut(String id) {
		List<String> out = new java.util.ArrayList<String>();
		for (ShipDesign x : designs) if (x.isWorking() && !x.id.equals(id)) out.add(x.name);
		return out;
	}
	private void newDesign() {
		ShipDesign nw = ShipDesign.create(designs);
		ShipDesign d = DesignDialog.open(this, nw, namesBut(nw.id));
		if (d == null) return;
		designs.add(d);
		commit(d);
	}
	/** A new design copied from a game ship the player picks. */
	private void fromGameShip() {
		String bpId = GameShipPicker.choose(this);
		if (bpId == null) return;
		ShipDesign nw = ShipDesign.create(designs);
		if (!ShipDesign.fromGameShip(nw, bpId)) {
			JOptionPane.showMessageDialog(this, "The Home Planet Station couldn't read that ship's layout.", "Design Ship", JOptionPane.WARNING_MESSAGE);
			return;
		}
		nw.name = uniqueName(nw.loadout != null && !nw.loadout.className.isEmpty() ? nw.loadout.className : bpId);
		ShipDesign d = DesignDialog.open(this, nw, namesBut(nw.id));
		if (d == null) return;
		designs.add(d);
		commit(d);
	}
	/** A new design copied from the selected one (its own id and name; not built yet). */
	private void duplicateSelected() {
		int row = list.getSelectedIndex();
		if (row < 0) return;
		ShipDesign nw = ShipDesign.copy(shown.get(row));
		ShipDesign fresh = ShipDesign.create(designs);
		nw.id = fresh.id; nw.made = fresh.made;
		nw.built = false; nw.starter = false; nw.version = 1; nw.frozenOf = null; nw.snapshotOf = null;
		nw.name = uniqueName(nw.name + " copy");
		ShipDesign d = DesignDialog.open(this, nw, namesBut(nw.id));
		if (d == null) return;
		designs.add(d);
		commit(d);
	}
	/** The name, or the name with a number after it, whichever no other design has. */
	private String uniqueName(String base) {
		java.util.Set<String> taken = new java.util.HashSet<String>();
		for (ShipDesign x : designs) taken.add(x.name.trim().toLowerCase());
		if (!taken.contains(base.trim().toLowerCase())) return base;
		for (int n = 2; ; n++) if (!taken.contains((base + " " + n).toLowerCase())) return base + " " + n;
	}
	private void openSelected() {
		int row = list.getSelectedIndex();
		if (row < 0) return;
		ShipDesign before = shown.get(row);
		int i = designs.indexOf(before);
		ShipDesign d = DesignDialog.open(this, before, namesBut(before.id));
		if (d == null) return;
		designs.set(i, d);
		commit(d);
		list.setSelectedIndex(row);
	}
	private void deleteSelected() {
		int row = list.getSelectedIndex();
		if (row < 0) return;
		ShipDesign d = shown.get(row);
		List<String> ships = new java.util.ArrayList<String>();
		for (String bp : bpIds(d.id)) ships.addAll(DesignDialog.shipsUsing(bp));
		java.util.Set<String> kept = homeplanet.vault.Vault.get().blueprintsInUseOrHistory(); // her ships, and their kept earlier versions
		boolean anyKept = false;
		for (String bp : bpIds(d.id)) if (kept.contains(bp)) anyKept = true;
		String ask = !anyKept ? "Delete the design \"" + d.name + "\"" + (d.version > 1 ? " and its older versions" : "") + "?"
				: (ships.isEmpty() ? "Ships' kept records still name " + d.name + "." : "Ships are still flying " + d.name + ": " + String.join(", ", ships) + ".")
						+ "\n\nThe Federation Home Planet will retire the design: it leaves this list and Commission, and its blueprint stays in the "
						+ homeplanet.parser.CompanionMod.TITLE + " for as long as any ship needs it.\n\nRetire \"" + d.name + "\"?";
		if (JOptionPane.showConfirmDialog(this, ask, "Design Ship", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
		store(ShipDesign.deleteOrRetire(designs, d.id, kept));
		HistoryLog.entry("DESIGN", (anyKept ? "Retired " : "Deleted ") + d.name + " (" + d.id + ")");
	}
	/**
	 * Saves a design the editor handed back. Save alone changes the working copy only; Build blueprint takes a fresh
	 * built copy (the old one is kept as an older version when ships were built from it), and the mod is rebuilt.
	 */
	private void commit(ShipDesign d) {
		if (!d.pendingBuild) { store(false); return; }
		d.pendingBuild = false;
		ShipDesign old = ShipDesign.snapshot(designs, d.id);
		if (d.pendingVersion > 0) {
			// keep the old version for the ships built from it (its pictures are its own: every import is a new file)
			if (old != null) { old.frozenOf = d.id; old.snapshotOf = null; }
			d.version = d.pendingVersion;
			d.pendingVersion = 0;
			HistoryLog.entry("DESIGN", d.name + ": built as v" + d.version + (old != null ? " (v" + old.version + " kept for the ships built from it)" : ""));
		} else if (old != null) designs.remove(old);
		d.built = true;
		ShipDesign snap = ShipDesign.copy(d);
		snap.snapshotOf = d.id;
		designs.add(snap);
		store(true);
	}
	/** Saves the designs; if a built one changed (or was deleted), rebuilds the companion mod and offers to patch it in. */
	private void store(boolean builtChanged) {
		try {
			ShipDesign.save(designs);
		} catch (Exception ex) {
			HomePlanet.showErrorDialog("The Home Planet Station couldn't save the designs to " + ShipDesign.file().getAbsolutePath() + ":\n" + ex);
		}
		homeplanet.parser.ShipArt.sweep(); // pictures of discarded imports
		refresh();
		if (!builtChanged) return;
		homeplanet.parser.CompanionMod.register(homeplanet.parser.CompanionMod.load()); // the station sees her at once
		java.io.File mod = homeplanet.core.Slipstream.writeMod();
		Object[] options = {"Patch Now", "Later"};
		int p = JOptionPane.showOptionDialog(this, (mod == null ? "Her blueprint is ready, but the " + homeplanet.parser.CompanionMod.TITLE + " could not be written." : "Her blueprint is in the " + homeplanet.parser.CompanionMod.TITLE + ".")
				+ "\nSend it to FTL via Slipstream to commission her (tick her as a starter ship to see her in Commission).",
				"Design Ship", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
		if (p == 0) PatchDialog.open(this);
	}
}
