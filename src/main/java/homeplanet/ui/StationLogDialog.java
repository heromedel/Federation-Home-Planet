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
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import homeplanet.core.SafeFiles;
import homeplanet.vault.Vault;

/**
 * The station log: a fleet's whole history.log in the Records style (days, times, coloured tags, details under each
 * entry), newest last. A dropdown picks the fleet (Sandbox Mode and each career keep their own); the search box keeps
 * the entries that mention what's typed.
 */
final class StationLogDialog extends JDialog {
	private final JComboBox<String> fleet = new JComboBox<String>();
	private final List<String> slots = new ArrayList<String>();
	private final JTextField search = new JTextField(18);
	private final JScrollPane scroll = new JScrollPane();
	private final JLabel count = new JLabel();

	static void open(java.awt.Component owner) {
		new StationLogDialog(owner).setVisible(true);
	}

	private StationLogDialog(java.awt.Component owner) {
		super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), "Station log", ModalityType.APPLICATION_MODAL);
		Vault v = Vault.get();
		for (String s : Vault.SLOTS) {
			if (!new File(Vault.rootOf(v.saves, s), "history.log").isFile() && !s.equals(v.slot)) continue;
			slots.add(s);
			fleet.addItem(Vault.title(s) + (s.equals(v.slot) ? " (in use)" : ""));
		}
		fleet.setSelectedIndex(Math.max(0, slots.indexOf(v.slot)));
		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		top.add(new JLabel("Fleet:  "));
		top.add(fleet);
		top.add(javax.swing.Box.createHorizontalStrut(16));
		top.add(new JLabel("Search:  "));
		top.add(search);
		top.add(javax.swing.Box.createHorizontalStrut(12));
		top.add(count);
		search.setToolTipText("Only the entries that mention this (a ship's name, a tag such as COMMISSION, a word), in any case");
		ActionListener redo = new ActionListener() { public void actionPerformed(ActionEvent e) { fill(); } };
		fleet.addActionListener(redo);
		search.addActionListener(redo);
		search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) { fill(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e) { fill(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { fill(); }
		});
		scroll.getViewport().setBackground(RecordsLog.BG);
		scroll.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, MenuTheme.GOLD));
		scroll.setPreferredSize(new Dimension(900, 520));
		scroll.getVerticalScrollBar().setUnitIncrement(22);
		JPanel south = new JPanel(new BorderLayout());
		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		south.add(close, BorderLayout.EAST);
		JPanel body = new JPanel(new BorderLayout(0, 8));
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		body.add(top, BorderLayout.NORTH);
		body.add(scroll, BorderLayout.CENTER);
		body.add(south, BorderLayout.SOUTH);
		getContentPane().add(body);
		getRootPane().setDefaultButton(close);
		fill();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setLocationRelativeTo(getOwner());
		ScreenFit.keepOnScreen(this);
	}

	/** The chosen fleet's log, kept to the entries that match the search, the latest in view. */
	private void fill() {
		if (slots.isEmpty()) return;
		File f = new File(Vault.rootOf(Vault.get().saves, slots.get(Math.max(0, fleet.getSelectedIndex()))), "history.log");
		String text = "";
		try { if (f.isFile()) text = new String(SafeFiles.read(f), StandardCharsets.UTF_8); }
		catch (Exception e) { text = "The Home Planet Station could not read " + f + ": " + e.getMessage(); }
		String q = search.getText().trim().toLowerCase();
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
		count.setText(q.isEmpty() ? entries + (entries == 1 ? " entry" : " entries") : shown + " of " + entries + (entries == 1 ? " entry" : " entries"));
		scroll.setViewportView(RecordsLog.station(out.toString(), q.isEmpty() ? "Nothing logged yet." : "No entries mention \"" + search.getText().trim() + "\"."));
		SwingUtilities.invokeLater(new Runnable() { // the latest entries in view
			public void run() { scroll.getViewport().revalidate(); javax.swing.JScrollBar b = scroll.getVerticalScrollBar(); b.setValue(b.getMaximum()); }
		});
	}
}
