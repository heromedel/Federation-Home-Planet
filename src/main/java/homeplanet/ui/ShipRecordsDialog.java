package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.GameGuard;
import homeplanet.core.HistoryLog;
import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * A ship's records: the earlier versions of her the station keeps (newest first), with Restore this version; her
 * voyage log (events in FTL, save by save); and her entries in history.log.
 */
public class ShipRecordsDialog extends JDialog {
	private final Ship ship;
	private final List<File> versions = new ArrayList<File>();
	private final JList<String> list;
	private final JButton restore = new JButton("Restore this version");
	private boolean restored;

	/** Shows her records; true if an earlier version was restored (the Space Dock should take stock again). */
	public static boolean open(Component owner, Ship ship) {
		ShipRecordsDialog d = new ShipRecordsDialog(owner, ship);
		d.setVisible(true);
		return d.restored;
	}

	private ShipRecordsDialog(Component owner, Ship ship) {
		super(SwingUtilities.getWindowAncestor(owner), "Ship's records: " + ship.name, ModalityType.APPLICATION_MODAL);
		this.ship = ship;
		versions.addAll(Vault.get().history(ship));
		Collections.reverse(versions); // newest first
		DefaultListModel<String> model = new DefaultListModel<String>();
		for (File f : versions) model.addElement(describe(f));
		list = new JList<String>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setVisibleRowCount(Math.max(4, Math.min(Vault.KEEP, versions.size())));
		list.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) { restore.setEnabled(list.getSelectedIndex() >= 0 && !HomePlanet.immersiveMode); }
		});
		restore.setEnabled(false);
		restore.setToolTipText(HomePlanet.immersiveMode ? "Immersive Mode: what's done is done. Earlier versions can't be restored"
				: "Put her back as she was in this version. Her current version is kept here too, so this can be undone.");
		restore.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { restoreSelected(); }
		});

		JPanel top = new JPanel(new BorderLayout(0, 6));
		top.add(new JLabel("<html>The Home Planet Station keeps her last " + Vault.KEEP + " versions: the one before each change the station<br>"
				+ "makes, and the one she had when she was boarded. Newest first.</html>"), BorderLayout.NORTH);
		JScrollPane vs = new JScrollPane(list);
		vs.setPreferredSize(new Dimension(560, 190));
		top.add(versions.isEmpty() ? new JLabel("No earlier versions of her are kept yet.") : vs, BorderLayout.CENTER);
		JPanel restoreRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		restoreRow.add(restore);
		if (!versions.isEmpty()) top.add(restoreRow, BorderLayout.SOUTH);

		String voyage = homeplanet.vault.VoyageLog.read(Vault.get(), ship);
		javax.swing.JTabbedPane logPanel = new javax.swing.JTabbedPane();
		logPanel.addTab("Voyage log", logTab(voyage.isEmpty() ? "Nothing logged yet. The Home Planet Station writes her voyage log as FTL saves her,\n"
				+ "while the station is open (and on Refresh): jumps, sectors, battles, crew, what came aboard, upgrades and repairs." : voyage,
				"Events in FTL, save by save (newest last). Sectors visited in all her journeys: " + homeplanet.vault.VoyageLog.visited(Vault.get(), ship)));
		logPanel.addTab("Station log", logTab(logLines(ship), "Her entries in the station's log (history.log):"));

		JPanel body = new JPanel(new BorderLayout(0, 14));
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		body.add(top, BorderLayout.NORTH);
		body.add(logPanel, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { dispose(); }
		});
		buttons.add(close);
		getContentPane().add(body, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(close);
		pack();
		setLocationRelativeTo(getOwner());
	}

	/** A log's text, scrolled to its latest lines, under a line saying what it is. */
	private static JPanel logTab(String text, String what) {
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		area.setCaretPosition(area.getDocument().getLength()); // the latest entries in view
		JScrollPane sp = new JScrollPane(area);
		sp.setPreferredSize(new Dimension(640, 220));
		JPanel p = new JPanel(new BorderLayout(0, 4));
		p.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		p.add(new JLabel(what), BorderLayout.NORTH);
		p.add(sp, BorderLayout.CENTER);
		return p;
	}
	/** One line for a kept version: when it was kept, and where she was. */
	private static String describe(File f) {
		String when = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(f.lastModified()));
		try {
			SavedGameState gs = HomePlanet.savedGameParser.readSavedGame(f);
			return String.format("%s   %s: sector %d, %d beacons, hull %d, scrap %d, fuel %d", when, gs.getPlayerShipName(),
					gs.getSectorNumber() + 1, gs.getTotalBeaconsExplored(), gs.getPlayerShip().getHullAmt(),
					gs.getPlayerShip().getScrapAmt(), gs.getPlayerShip().getFuelAmt());
		} catch (Exception e) {
			return when + "   (can't be read: " + e.getMessage() + ")";
		}
	}

	/** Her entries in history.log: those whose headline names her (by name or by her file's id). */
	static String logLines(Ship ship) {
		File f = HistoryLog.file();
		StringBuilder out = new StringBuilder();
		if (!f.isFile()) return "";
		try {
			String[] lines = new String(SafeFiles.read(f), StandardCharsets.UTF_8).split("\r?\n");
			boolean mine = false;
			for (String line : lines) {
				if (!line.startsWith("  ")) mine = line.contains(ship.id) || (ship.name != null && !ship.name.isEmpty() && line.contains(ship.name));
				if (mine) out.append(line).append('\n');
			}
		} catch (Exception e) {
			return "The Home Planet Station could not read its log (" + f + "): " + e.getMessage();
		}
		return out.length() == 0 ? "No entries for her yet." : out.toString();
	}

	private void restoreSelected() {
		int i = list.getSelectedIndex();
		if (i < 0) return;
		File version = versions.get(i);
		if (HomePlanet.immersiveMode) return;
		if (!HomePlanet.confirmNo(this, "Restore " + ship.name + " to this version?\n\n" + describe(version) + "\n\n"
				+ "Everything since then is undone: her crew, cargo, scrap and journey go back to how they were.\n"
				+ "Her current version is kept in her records, so you can restore it again.", "Restore this version")) return;
		if (ship.isBoarded() && !GameGuard.allows(this, "restore her")) return;
		try {
			Vault.get().restore(ship, version);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not restore her; her current save was not changed:\n" + e.getMessage());
			return;
		}
		restored = true;
		JOptionPane.showMessageDialog(this, ship.name + " is restored.", "Restore this version", JOptionPane.INFORMATION_MESSAGE);
		dispose();
	}
}
