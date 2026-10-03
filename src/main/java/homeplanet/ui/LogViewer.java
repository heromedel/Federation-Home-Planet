package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import homeplanet.core.SafeFiles;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * The Records page's log viewer: the station log (a fleet's history.log; Sandbox Mode and each career keep their own)
 * or one ship's voyage log, in the Records style, inside the page itself, or this run's debug log as plain text. Logs
 * are only ever shown here, read-only: nothing opens them in a text editor.
 */
final class LogViewer extends JPanel {
	private final JComboBox<String> fleet = new JComboBox<String>();
	private final List<String> slots = new ArrayList<String>();
	private final JComboBox<String> shipBox = new JComboBox<String>();
	private final List<Ship> ships = new ArrayList<Ship>();
	private final JScrollPane scroll = new JScrollPane();
	private final JLabel showing = new JLabel(), count = new JLabel();
	/** What's in view: the station log (null) or this ship's voyage log (the debug log is shown by {@link #showDebug}). */
	private Ship ship;

	LogViewer() {
		super(new BorderLayout(0, 6));
		Vault v = Vault.get();
		for (String s : Vault.SLOTS) {
			if (!new File(Vault.rootOf(v.saves, s), "history.log").isFile() && !s.equals(v.slot)) continue;
			slots.add(s);
			fleet.addItem(Vault.title(s) + (s.equals(v.slot) ? " (in use)" : ""));
		}
		fleet.setSelectedIndex(Math.max(0, slots.indexOf(v.slot)));
		fleet.setToolTipText("Whose station log: Sandbox Mode and each career keep their own");
		// docked ships first, then the Junkyard's hulls, then the boarded one
		ships.addAll(v.docked());
		ships.addAll(v.junked());
		if (v.boarded() != null) ships.add(v.boarded());
		for (Ship s : ships) shipBox.addItem(label(s));
		shipBox.setToolTipText("A ship of the fleet in use: docked, in the Junkyard, or boarded");

		JButton viewStation = new JButton("View Station Log");
		viewStation.setToolTipText("Everything The Home Planet Station has done: commissions, boardings, trades, switches, what it found when taking stock");
		viewStation.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { ship = null; fill(); }
		});
		fleet.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { ship = null; fill(); }
		});
		final JButton viewShip = new JButton("View Ship Log");
		viewShip.setToolTipText("Her voyage log: her jumps, sectors, battles, crew, what came aboard, upgrades and repairs");
		viewShip.setEnabled(!ships.isEmpty());
		shipBox.setEnabled(!ships.isEmpty());
		ActionListener toShip = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int i = shipBox.getSelectedIndex();
				if (i < 0 || i >= ships.size()) return;
				ship = ships.get(i);
				fill();
			}
		};
		viewShip.addActionListener(toShip);
		shipBox.addActionListener(toShip);

		// the options in one row across the top: the station log, a ship's log, the debug log
		JButton viewDebug = new JButton("View Debug Log");
		viewDebug.setToolTipText("This run's log: what the program did, and any errors");
		viewDebug.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { showDebug(); }
		});
		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		top.add(viewStation);
		top.add(javax.swing.Box.createHorizontalStrut(6));
		top.add(fleet);
		top.add(javax.swing.Box.createHorizontalStrut(18));
		top.add(viewShip);
		top.add(javax.swing.Box.createHorizontalStrut(6));
		top.add(shipBox);
		top.add(javax.swing.Box.createHorizontalStrut(18));
		top.add(viewDebug);
		JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		titleRow.add(showing);
		titleRow.add(javax.swing.Box.createHorizontalStrut(12));
		titleRow.add(count);
		showing.setFont(MenuTheme.LABEL_FONT);
		JPanel controls = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
		controls.add(top);
		controls.add(titleRow);

		scroll.getViewport().setBackground(RecordsLog.BG);
		scroll.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, MenuTheme.GOLD));
		scroll.setPreferredSize(new Dimension(640, 380));
		scroll.getVerticalScrollBar().setUnitIncrement(22);
		add(controls, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);
		fill();
	}

	private static String label(Ship s) {
		String name = s.name == null || s.name.trim().isEmpty() ? "(unnamed)" : s.name;
		return name + (s.isBoarded() ? "  (boarded)" : s.state == Ship.State.JUNKED ? "  (Junkyard)" : "");
	}

	/** Shows what's chosen, the latest in view. */
	private void fill() {
		if (ship != null) {
			showing.setText(ship.name + "'s voyage log");
			String text = homeplanet.vault.VoyageLog.read(Vault.get(), ship);
			int all = 0;
			for (String line : text.split("\r?\n")) if (!line.trim().isEmpty()) all++;
			count.setText(all + " lines");
			show(RecordsLog.voyage(text, "Nothing logged yet. The Home Planet Station writes her voyage log as FTL saves her,\n"
					+ "while the station is open (and on Refresh)."));
			return;
		}
		if (slots.isEmpty()) return;
		String slot = slots.get(Math.max(0, fleet.getSelectedIndex()));
		showing.setText("Station log, " + Vault.title(slot));
		File f = new File(Vault.rootOf(Vault.get().saves, slot), "history.log");
		String text = "";
		try { if (f.isFile()) text = new String(SafeFiles.read(f), StandardCharsets.UTF_8); }
		catch (Exception e) { text = "The Home Planet Station could not read " + f + ": " + e.getMessage(); }
		int entries = 0;
		for (String line : text.split("\r?\n")) if (!line.isEmpty() && !line.startsWith("  ")) entries++;
		count.setText(entries + (entries == 1 ? " entry" : " entries"));
		show(RecordsLog.station(text, "Nothing logged yet."));
	}

	/** This run's debug log (the newest in the program's log folder), as plain text: what to read before a bug report. */
	void showDebug() {
		File dir = homeplanet.core.HomePlanet.logDir();
		File newest = null;
		File[] fs = dir == null ? null : dir.listFiles();
		if (fs != null) for (File f : fs) if (f.getName().startsWith("home-planet-") && f.getName().endsWith(".log") && (newest == null || f.lastModified() > newest.lastModified())) newest = f;
		String text;
		if (newest == null) text = "No debug log has been written yet (the program's log folder beside Federation Home Planet.jar is empty or missing).";
		else {
			try { text = new String(SafeFiles.read(newest), StandardCharsets.UTF_8); }
			catch (Exception e) { text = "The Home Planet Station could not read " + newest + ": " + e.getMessage(); }
		}
		showing.setText(newest == null ? "Debug log" : "Debug log, " + newest.getName());
		int lines = 0;
		for (String line : text.split("\r?\n")) if (!line.isEmpty()) lines++;
		count.setText(newest == null ? "" : lines + " lines");
		javax.swing.JTextArea a = new javax.swing.JTextArea(text);
		a.setEditable(false);
		a.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
		a.setBackground(RecordsLog.BG);
		a.setForeground(MenuTheme.GREY_GREEN);
		a.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		scroll.setViewportView(a);
		SwingUtilities.invokeLater(new Runnable() { // the latest in view
			public void run() { javax.swing.JScrollBar b = scroll.getVerticalScrollBar(); b.setValue(b.getMaximum()); }
		});
	}

	private void show(RecordsLog log) {
		scroll.setViewportView(log);
		SwingUtilities.invokeLater(new Runnable() { // the latest in view
			public void run() { scroll.getViewport().revalidate(); javax.swing.JScrollBar b = scroll.getVerticalScrollBar(); b.setValue(b.getMaximum()); }
		});
	}
}
