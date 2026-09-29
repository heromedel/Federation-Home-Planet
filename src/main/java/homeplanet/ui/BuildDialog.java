package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import net.blerf.ftl.constants.Difficulty;
import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.parser.Commission;
import homeplanet.parser.DesignExport;
import homeplanet.parser.ShipChecks;
import homeplanet.parser.ShipDesign;

/**
 * The last look before a design goes into the companion mod: the ship's report as she'd be commissioned (a trial ship is
 * built from the design in memory, which is also the test that she loads), what stops her being built, what's worth
 * knowing, and what Build does. Cancel is the default; Loadout... goes to the loadout dialog instead.
 */
public final class BuildDialog extends JDialog {
	public enum Choice { CANCEL, LOADOUT, BUILD }

	private Choice choice = Choice.CANCEL;

	/** Opens the screen; {@code snapshot} is her built copy (null if never built). */
	public static Choice open(Component owner, ShipDesign d, ShipDesign snapshot, List<String> otherNames) {
		BuildDialog dlg = new BuildDialog(SwingUtilities.getWindowAncestor(owner), d, snapshot, otherNames);
		dlg.setVisible(true);
		return dlg.choice;
	}

	private BuildDialog(Window owner, ShipDesign d, ShipDesign snapshot, List<String> otherNames) {
		super(owner, "Build blueprint: " + d.name, ModalityType.APPLICATION_MODAL);
		String bpId = DesignExport.bpId(d);

		// what stops her, and what's worth knowing
		ShipChecks.Report r = ShipChecks.check(d, ShipChecks.Context.DESIGN, null, otherNames);
		List<String> fix = new ArrayList<String>(r.problems), notes = new ArrayList<String>(r.warnings);
		// the checker's own loadout/art problems that Build can't do without
		if (d.art.isEmpty()) fix.add("She needs hull art.");
		if (d.loadout == null) notes.add("No loadout set: she'd start with three humans, 8 missiles, 2 drone parts and nothing fitted (Loadout... sets it).");

		// the trial ship: her report, and the proof that she loads
		JComponent report;
		String stats = "";
		if (fix.isEmpty()) {
			try {
				DesignExport.registerPreview(d);
				MainFrame frame = (MainFrame) SwingUtilities.getAncestorOfClass(MainFrame.class, owner);
				if (frame == null && owner instanceof MainFrame) frame = (MainFrame) owner;
				if (frame == null) throw new IllegalStateException("no Space Dock to draw the report");
				frame.forgetImages("img/ship/" + DesignExport.gfx(d));
				ShipBlueprint bp = DataManager.get().getShip(bpId);
				String name = d.loadout != null && !d.loadout.shipName.isEmpty() ? d.loadout.shipName : "The " + d.name;
				SavedGameState s = Commission.build(bpId, name, Difficulty.EASY, new Random(0));
				report = frame.spaceDock.shipSummaryPanel(s);
				stats = CommissionDialog.classOf(bp) + ": hull " + bp.getHealth().amount + ", reactor "
						+ (bp.getMaxPower() == null ? "?" : bp.getMaxPower().amount) + ", " + (bp.getWeaponSlots() == null ? 4 : bp.getWeaponSlots())
						+ " weapon slots, " + (bp.getDroneSlots() == null ? 3 : bp.getDroneSlots()) + " drone slots";
			} catch (Exception ex) {
				fix.add("A ship can't be built from her as she stands: " + ex);
				report = new JLabel("(no report: see To fix)");
			}
		} else report = new JLabel("(no report until she can be built)");

		// what Build does
		List<String> does = new ArrayList<String>();
		does.add("Blueprint " + bpId + " (layout " + DesignExport.layoutId(d) + ") goes into the companion mod, which then needs patching in (Settings > Patch mods).");
		if (snapshot != null && DesignExport.changesBlueprint(snapshot, d)) {
			List<String> ships = DesignDialog.shipsUsing(DesignExport.bpId(snapshot));
			if (!ships.isEmpty()) does.add("She was built before (v" + snapshot.version + ") and " + (ships.size() == 1 ? "1 ship flies it" : ships.size() + " ships fly it")
					+ ": " + String.join(", ", ships) + ". This build becomes v" + DesignDialog.nextVersion(d.id) + "; they keep v" + snapshot.version + ".");
			else does.add("She was built before (v" + snapshot.version + ") and no ship flies it: you'll be asked whether to replace it or make v" + DesignDialog.nextVersion(d.id) + ".");
		} else if (snapshot != null) does.add("Nothing the game sees has changed since her last build; building again changes nothing in the mod.");
		does.add(d.starter ? "She's a starter ship: she'll be listed in Commission once the mod is patched in." : "Not a starter ship: she won't be listed in Commission (the Loadout... tick box).");

		JPanel top = new JPanel(new BorderLayout(0, 4));
		JLabel sl = new JLabel(stats);
		sl.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		top.add(sl, BorderLayout.NORTH);
		top.add(report, BorderLayout.CENTER);

		StringBuilder html = new StringBuilder("<html><body style='width:560px'>");
		if (!fix.isEmpty()) { html.append("<p><b style='color:#c04040'>To fix (stops the build)</b><ul>"); for (String x : fix) html.append("<li>").append(esc(x)).append("</li>"); html.append("</ul></p>"); }
		if (!notes.isEmpty()) { html.append("<p><b style='color:#b08a00'>Notes (her choice, not the station's)</b><ul>"); for (String x : notes) html.append("<li>").append(esc(x)).append("</li>"); html.append("</ul></p>"); }
		html.append("<p><b>What Build does</b><ul>"); for (String x : does) html.append("<li>").append(esc(x)).append("</li>"); html.append("</ul></p></body></html>");
		JLabel lines = new JLabel(html.toString());
		lines.setVerticalAlignment(JLabel.TOP);
		lines.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

		JPanel body = new JPanel(new BorderLayout(0, 8));
		body.add(top, BorderLayout.NORTH);
		body.add(lines, BorderLayout.CENTER);
		JScrollPane sp = new JScrollPane(body);
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.getVerticalScrollBar().setUnitIncrement(16);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton preview = new JButton("Preview files");
		preview.setToolTipText("The layout, chassis and blueprint text the game would get for her");
		preview.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { ShipEditorDialog.showTextWindow(BuildDialog.this, "Files for " + d.name, DesignExport.preview(d)); } });
		JButton loadout = new JButton("Loadout...");
		loadout.setToolTipText("Her class, name, crew, weapons, drones, augments, missiles, drone parts and starting systems (closes this screen)");
		loadout.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { choice = Choice.LOADOUT; dispose(); } });
		JButton build = new JButton(notes.isEmpty() ? "Build blueprint" : "Build anyway");
		build.setEnabled(fix.isEmpty());
		build.setToolTipText(fix.isEmpty() ? "Put her blueprint in the companion mod" : "Not until the To fix list is empty");
		build.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { choice = Choice.BUILD; dispose(); } });
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		buttons.add(preview); buttons.add(loadout); buttons.add(build); buttons.add(cancel);
		getRootPane().setDefaultButton(cancel);
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
		getRootPane().getActionMap().put("close", new javax.swing.AbstractAction() { public void actionPerformed(ActionEvent e) { dispose(); } });

		getContentPane().add(sp, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);
		pack();
		java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		setSize(Math.min(getWidth() + 20, screen.width), Math.min(getHeight() + 20, screen.height - 40));
		setLocationRelativeTo(owner);
	}

	private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

}
