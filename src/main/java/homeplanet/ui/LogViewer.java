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
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import homeplanet.core.SafeFiles;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * The Records page's log viewer: the station log (a fleet's history.log; Sandbox Mode and each career keep their own)
 * or one ship's voyage log, in the Records style, inside the page itself. Logs are only ever shown here, read-only:
 * nothing opens them in a text editor. The search box keeps the entries (or voyage lines) that mention what's typed.
 */
final class LogViewer extends JPanel {
	private final JComboBox<String> fleet = new JComboBox<String>();
	private final List<String> slots = new ArrayList<String>();
	private final JComboBox<String> shipBox = new JComboBox<String>();
	private final List<Ship> ships = new ArrayList<Ship>();
	private final JTextField search = new JTextField(14);
	private final JScrollPane scroll = new JScrollPane();
	private final JLabel showing = new JLabel(), count = new JLabel();
	/** What's in view: the station log (null) or this ship's voyage log. */
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

		JPanel stationRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		stationRow.add(viewStation);
		stationRow.add(javax.swing.Box.createHorizontalStrut(8));
		stationRow.add(fleet);
		JPanel shipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		shipRow.add(viewShip);
		shipRow.add(javax.swing.Box.createHorizontalStrut(8));
		shipRow.add(shipBox);
		if (ships.isEmpty()) { shipRow.add(javax.swing.Box.createHorizontalStrut(8)); shipRow.add(new JLabel("(no ships in this fleet yet)")); }
		JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		searchRow.add(showing);
		searchRow.add(javax.swing.Box.createHorizontalStrut(16));
		searchRow.add(new JLabel("Search:  "));
		searchRow.add(search);
		searchRow.add(javax.swing.Box.createHorizontalStrut(10));
		searchRow.add(count);
		showing.setFont(MenuTheme.LABEL_FONT);
		search.setToolTipText("Only what mentions this (a ship's name, a tag such as COMMISSION, a word), in any case");
		search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) { fill(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e) { fill(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { fill(); }
		});
		JPanel controls = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
		controls.add(stationRow);
		controls.add(shipRow);
		controls.add(searchRow);

		scroll.getViewport().setBackground(RecordsLog.BG);
		scroll.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, MenuTheme.GOLD));
		scroll.setPreferredSize(new Dimension(640, 300));
		scroll.getVerticalScrollBar().setUnitIncrement(22);
		add(controls, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);
		fill();
	}

	private static String label(Ship s) {
		String name = s.name == null || s.name.trim().isEmpty() ? "(unnamed)" : s.name;
		return name + (s.isBoarded() ? "  (boarded)" : s.state == Ship.State.JUNKED ? "  (Junkyard)" : "");
	}

	/** Shows what's chosen, kept to what matches the search, the latest in view. */
	private void fill() {
		String q = search.getText().trim().toLowerCase();
		String none = "No entries mention \"" + search.getText().trim() + "\".";
		if (ship != null) {
			showing.setText(ship.name + "'s voyage log");
			String text = homeplanet.vault.VoyageLog.read(Vault.get(), ship);
			int all = 0, shown = 0;
			StringBuilder out = new StringBuilder();
			for (String line : text.split("\r?\n")) {
				if (line.trim().isEmpty()) continue;
				all++;
				if (q.isEmpty() || line.toLowerCase().contains(q)) { out.append(line).append('\n'); shown++; }
			}
			count.setText(q.isEmpty() ? all + " lines" : shown + " of " + all + " lines");
			show(RecordsLog.voyage(out.toString(), q.isEmpty() ? "Nothing logged yet. The Home Planet Station writes her voyage log as FTL saves her,\n"
					+ "while the station is open (and on Refresh)." : none));
			return;
		}
		if (slots.isEmpty()) return;
		String slot = slots.get(Math.max(0, fleet.getSelectedIndex()));
		showing.setText("Station log, " + Vault.title(slot));
		File f = new File(Vault.rootOf(Vault.get().saves, slot), "history.log");
		String text = "";
		try { if (f.isFile()) text = new String(SafeFiles.read(f), StandardCharsets.UTF_8); }
		catch (Exception e) { text = "The Home Planet Station could not read " + f + ": " + e.getMessage(); }
		int entries = 0, shown = 0;
		StringBuilder out = new StringBuilder(), entry = new StringBuilder();
		for (String line : text.split("\r?\n")) {
			if (!line.startsWith("  ")) { // a new entry: the last one goes in if it matched
				if (entry.length() > 0 && (q.isEmpty() || entry.toString().toLowerCase().contains(q))) { out.append(entry); shown++; }
				if (entry.length() > 0) entries++;
				entry.setLength(0);
			}
			if (!line.trim().isEmpty()) entry.append(line).append('\n');
		}
		if (entry.length() > 0) { entries++; if (q.isEmpty() || entry.toString().toLowerCase().contains(q)) { out.append(entry); shown++; } }
		count.setText(q.isEmpty() ? entries + " entries" : shown + " of " + entries + " entries");
		show(RecordsLog.station(out.toString(), q.isEmpty() ? "Nothing logged yet." : none));
	}

	private void show(RecordsLog log) {
		scroll.setViewportView(log);
		SwingUtilities.invokeLater(new Runnable() { // the latest in view
			public void run() { scroll.getViewport().revalidate(); javax.swing.JScrollBar b = scroll.getVerticalScrollBar(); b.setValue(b.getMaximum()); }
		});
	}
}
