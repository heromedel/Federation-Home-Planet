package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import homeplanet.vault.Reputation;
import homeplanet.vault.Vault;

/**
 * The Career Reputation Log: every change to the career's standing with The Federation Home Planet, in the station
 * log's style (days, times, the change as a green or red tag, why, details under it), newest last. Opened by clicking
 * the reputation on the Space Dock; the search box keeps the entries that mention what's typed (a ship's name, say).
 */
final class ReputationLogDialog extends JDialog {
	private final JTextField search = new JTextField(18);
	private final JScrollPane scroll = new JScrollPane();
	private final JLabel count = new JLabel();

	static void open(java.awt.Component owner) {
		new ReputationLogDialog(owner).setVisible(true);
	}

	private ReputationLogDialog(java.awt.Component owner) {
		super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), "Career Reputation Log", ModalityType.APPLICATION_MODAL);
		int total = Reputation.total(Vault.get());
		JLabel standing = new JLabel("Your reputation with The Federation Home Planet: " + Reputation.signed(total));
		standing.setFont(MenuTheme.LABEL_FONT);
		standing.setForeground(total < 0 ? MenuTheme.RED : MenuTheme.GOLD);
		JPanel top = new JPanel(new BorderLayout(0, 6));
		top.add(standing, BorderLayout.NORTH);
		JPanel find = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		find.add(new JLabel("Search:  "));
		find.add(search);
		find.add(javax.swing.Box.createHorizontalStrut(12));
		find.add(count);
		top.add(find, BorderLayout.SOUTH);
		search.setToolTipText("Only the entries that mention this (a ship's name, a word), in any case");
		search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) { fill(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e) { fill(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { fill(); }
		});
		scroll.getViewport().setBackground(RecordsLog.BG);
		scroll.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, MenuTheme.GOLD));
		scroll.setPreferredSize(new Dimension(900, 400)); // room for the summary below it on a scaled screen
		scroll.getVerticalScrollBar().setUnitIncrement(22);
		JPanel south = new JPanel(new BorderLayout());
		JLabel how = new JLabel("<html><div style='width:620px'><font color='" + MenuTheme.HTML_GREY_GREEN + "'>Earned: each sector +" + Reputation.SECTOR + ", a tenth of the scrap collected, each ship defeated +"
				+ Reputation.DEFEATED + " (a rebel +" + Reputation.REBEL_DEFEATED + "), a good outcome +" + Reputation.EVENT_GOOD + ", each FTL achievement +" + Reputation.ACHIEVEMENT
				+ ", the Rebel Flagship +" + Reputation.FLAGSHIP + ".<br>Lost: each crew member killed " + Reputation.signed(Reputation.CREW_DIED) + ", each ship lost in action "
				+ Reputation.signed(Reputation.SHIP_LOST) + ", caught by the rebel fleet " + Reputation.signed(Reputation.CAUGHT) + ", a bad outcome " + Reputation.signed(Reputation.EVENT_BAD)
				+ ". Nothing is lost in the last stand of sector 8.</font></div></html>"); // wraps, so the Close button keeps its room
		south.add(how, BorderLayout.CENTER);
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

	/** The log, kept to the entries that match the search, the latest in view. */
	private void fill() {
		String text = Reputation.log(Vault.get());
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
		scroll.setViewportView(RecordsLog.station(out.toString(), q.isEmpty() ? "Nothing yet: your ships' service will be noted here." : "No entries mention \"" + search.getText().trim() + "\"."));
		SwingUtilities.invokeLater(new Runnable() {
			public void run() { scroll.getViewport().revalidate(); javax.swing.JScrollBar b = scroll.getVerticalScrollBar(); b.setValue(b.getMaximum()); }
		});
	}
}
