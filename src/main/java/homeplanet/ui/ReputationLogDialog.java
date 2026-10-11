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
		JPanel find = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		find.add(new JLabel("Search:  "));
		find.add(search);
		find.add(javax.swing.Box.createHorizontalStrut(12));
		find.add(count);
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
		JPanel south = new JPanel(new BorderLayout(12, 0));
		south.add(tallyLine(), BorderLayout.CENTER); // where it came from, a running tally (heromedel, 5.15)
		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		south.add(close, BorderLayout.EAST);
		JPanel body = new JPanel(new BorderLayout(0, 8));
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		JPanel logPage = new JPanel(new BorderLayout(0, 6));
		logPage.add(find, BorderLayout.NORTH);
		logPage.add(scroll, BorderLayout.CENTER);
		// How Rep Works (a tab beside the log, 5.13) is off for now: heromedel may write its words (howPage() keeps the first draft)
		body.add(standing, BorderLayout.NORTH);
		body.add(logPage, BorderLayout.CENTER);
		body.add(south, BorderLayout.SOUTH);
		getContentPane().add(body);
		getRootPane().setDefaultButton(close);
		fill();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setLocationRelativeTo(getOwner());
		ScreenFit.keepOnScreen(this);
	}

	/** "Total Reputation From:" and each pool's net, green or red; pools at zero left out. */
	private static JLabel tallyLine() {
		java.util.Map<String, Integer> t = Reputation.tally(Vault.get());
		StringBuilder h = new StringBuilder("<html><div style='width:700px'><font color='" + MenuTheme.HTML_GOLD + "'><b>Total Reputation From:</b></font>&nbsp;&nbsp;");
		if (t.isEmpty()) h.append("<font color='" + MenuTheme.HTML_GREY_GREEN + "'>nothing yet</font>");
		boolean first = true;
		for (java.util.Map.Entry<String, Integer> e : t.entrySet()) {
			if (!first) h.append("&nbsp;&nbsp;<font color='" + MenuTheme.HTML_GREY_GREEN + "'>&middot;</font>&nbsp;&nbsp;");
			first = false;
			h.append(e.getKey()).append("&nbsp;<font color='").append(e.getValue() < 0 ? MenuTheme.HTML_RED : "#78d78c").append("'><b>")
					.append(Reputation.signed(e.getValue())).append("</b></font>");
		}
		return new JLabel(h.append("</div></html>").toString());
	}

	/**
	 * How Rep Works: what earns reputation, what loses it, what it pays for (at this career's prices) and what may take
	 * it below zero, in short sections. The numbers come from the rules themselves, so the page can't disagree with them.
	 */
	private static javax.swing.JEditorPane howPage() {
		String gold = MenuTheme.HTML_GOLD, dim = MenuTheme.HTML_GREY_GREEN;
		StringBuilder h = new StringBuilder("<html><body style='color:#dce4eb'>");
		h.append("<p>Your reputation is your standing with The Federation Home Planet. Good service earns it; losses cost it. "
				+ "It can also be spent, where The Federation Home Planet is willing to bend for a commander it trusts.</p>");
		section(h, gold, "Earned");
		item(h, "Each sector reached", "+" + Reputation.SECTOR);
		item(h, "Scrap collected", "a tenth of it");
		item(h, "Each ship defeated", "+" + Reputation.DEFEATED + " (a rebel ship +" + Reputation.REBEL_DEFEATED + ")");
		item(h, "A good outcome at a beacon, or on an expedition", "+" + Reputation.EVENT_GOOD);
		item(h, "Each FTL achievement", "+" + Reputation.ACHIEVEMENT);
		item(h, "A final victory", "+" + Reputation.FLAGSHIP);
		item(h, "A captive brought home by paying the ransom", "+" + Reputation.RANSOMED);
		section(h, gold, "Lost");
		item(h, "Each crew member killed", Reputation.signed(Reputation.CREW_DIED));
		item(h, "Each ship lost in action", Reputation.signed(Reputation.SHIP_LOST));
		item(h, "Each crew member taken captive", Reputation.signed(Reputation.CAPTURED));
		item(h, "Caught by the rebel fleet", Reputation.signed(Reputation.CAUGHT));
		item(h, "A bad outcome at a beacon, or on an expedition", Reputation.signed(Reputation.EVENT_BAD));
		h.append("<p><font color='" + dim + "'>Nothing is lost in the last stand of sector 8.</font></p>");
		section(h, gold, "Spent");
		int journey = homeplanet.core.Economy.journeyFee(), removal = homeplanet.core.Economy.removalFee();
		item(h, "A New Journey", journey > 0 ? journey + " scrap or reputation, or the scrap there is and reputation for the rest" : "free");
		item(h, "Refit: taking a system off a ship", removal == homeplanet.core.Economy.NOT_ALLOWED ? "not allowed" : removal == 0 ? "free"
				: removal + " scrap or reputation, or the scrap there is and reputation for the rest");
		item(h, "Stripping a ship's systems when " + homeplanet.model.Words.she() + "'s scrapped", !homeplanet.core.Economy.stripAllowed() ? "not allowed" : homeplanet.core.Economy.stripFee() == 0 ? "free"
				: homeplanet.core.Economy.stripFee() + " scrap or reputation a system");
		item(h, "A custom work order, fitting a system past FTL's System Limit in the Cargo Bay", homeplanet.core.Economy.workOrderWords());
		item(h, "A plea for a new ship, if you keep the Cargo Hold", homeplanet.core.Economy.share(homeplanet.core.Economy.pleaPercent())
				+ " of " + homeplanet.model.Words.her() + " value (giving up the hold, " + homeplanet.core.Economy.share(homeplanet.core.Economy.pleaPercent()) + " of what it doesn't cover)");
		item(h, "A promise of adventure, with no crew anywhere", Integer.toString(homeplanet.parser.Expeditions.PROMISE_REP));
		item(h, "Resting in your quarters", "the first day free, then 1 more for each day in a row, up to " + homeplanet.parser.Rest.MAX_COST);
		section(h, gold, "Below zero");
		h.append("<p>A plea for a new ship, a promise of adventure and resting in your quarters are never refused: they may take your reputation below zero.</p>");
		h.append("<p>Everything else stops at zero. If paying in reputation would take it below, pay in scrap, or earn more first.</p>");
		h.append("</body></html>");
		javax.swing.JEditorPane l = new javax.swing.JEditorPane("text/html", h.toString()); // wraps to the window's width
		l.putClientProperty(javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		l.setEditable(false);
		l.setOpaque(true);
		l.setBackground(RecordsLog.BG);
		l.setFont(MenuTheme.TEXT_FONT);
		l.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 14));
		l.setCaretPosition(0);
		return l;
	}
	private static void section(StringBuilder h, String color, String title) {
		h.append("<p style='margin-top:12px'><font color='").append(color).append("'><b>").append(title).append("</b></font></p>");
	}
	private static void item(StringBuilder h, String what, String value) {
		h.append("<div style='margin-left:12px'>").append(what).append(": <b>").append(value).append("</b></div>");
	}

	/** The log, kept to the entries that match the search, the latest in view. */
	private void fill() {
		java.util.List<homeplanet.core.EventLog.Entry> all = homeplanet.core.EventLog.ofLog(homeplanet.core.EventLog.read(Vault.get()), "reputation"); // its events (5.74)
		String q = search.getText().trim().toLowerCase();
		java.util.List<homeplanet.core.EventLog.Entry> out = new java.util.ArrayList<homeplanet.core.EventLog.Entry>();
		for (homeplanet.core.EventLog.Entry e : all) {
			StringBuilder words = new StringBuilder(Reputation.signed(e.num("points", 0))).append(' ').append(e.human);
			for (int i = 1; e.get("detail." + i) != null; i++) words.append(' ').append(e.get("detail." + i));
			if (q.isEmpty() || words.toString().toLowerCase().contains(q)) out.add(e);
		}
		int entries = all.size(), shown = out.size();
		count.setText(q.isEmpty() ? entries + (entries == 1 ? " entry" : " entries") : shown + " of " + entries + (entries == 1 ? " entry" : " entries"));
		scroll.setViewportView(RecordsLog.station(out, q.isEmpty() ? "Nothing yet: your ships' service will be noted here." : "No entries mention \"" + search.getText().trim() + "\".", false));
		SwingUtilities.invokeLater(new Runnable() {
			public void run() { scroll.getViewport().revalidate(); javax.swing.JScrollBar b = scroll.getVerticalScrollBar(); b.setValue(b.getMaximum()); }
		});
	}
}
