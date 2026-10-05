package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import homeplanet.parser.XmlText;
import homeplanet.vault.MasterLog;
import homeplanet.vault.Vault;

/**
 * The Captain's Log (heromedel, 5.17), in Captain's Quarters: the career's days and what happened on each, from its
 * master log. The first day reads "Captains Log: Stardate Today", every later one "StarDate TD x.x.x.x" (TD: Today).
 * The station's technical entries (loads, profiles, settings, patches) are left out, and so are the reasons days pass:
 * the player never learns what a day is counted in. Days with nothing on them don't show.
 */
final class CaptainsLogDialog extends JDialog {
	/** The station log's kinds that are the station's own housekeeping, not the career's story. */
	private static final Set<String> TECHNICAL = new HashSet<String>(Arrays.asList("LOADED", "PROFILE", "SETTINGS", "PATCH", "SLIPSTREAM", "UPDATE",
			"VAULT", "CLEAN", "BLUEPRINTS", "BLUEPRINT", "CLAUDE", "SWITCH FLEET", "DESIGN", "RESTORE", "RECOVER"));

	static void open(java.awt.Component owner) { new CaptainsLogDialog(owner).setVisible(true); }

	private CaptainsLogDialog(java.awt.Component owner) {
		super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), "Captain's Log", ModalityType.APPLICATION_MODAL);
		javax.swing.JEditorPane page = new javax.swing.JEditorPane("text/html", page(Vault.get()));
		page.putClientProperty(javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		page.setEditable(false);
		page.setBackground(RecordsLog.BG);
		page.setFont(MenuTheme.TEXT_FONT);
		page.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 14));
		JScrollPane scroll = new JScrollPane(page);
		scroll.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, MenuTheme.GOLD));
		scroll.getViewport().setBackground(RecordsLog.BG);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(22);
		scroll.setPreferredSize(new Dimension(760, 460));
		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		JPanel south = new JPanel(new BorderLayout());
		south.add(close, BorderLayout.EAST);
		JPanel body = new JPanel(new BorderLayout(0, 8));
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		body.add(scroll, BorderLayout.CENTER);
		body.add(south, BorderLayout.SOUTH);
		getContentPane().add(body);
		getRootPane().setDefaultButton(close);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setLocationRelativeTo(getOwner());
		ScreenFit.keepOnScreen(this);
		final javax.swing.JScrollBar bar = scroll.getVerticalScrollBar();
		SwingUtilities.invokeLater(new Runnable() { public void run() { bar.setValue(bar.getMaximum()); } }); // the latest day in view
	}

	/** The log as a page: its title, then each day with something on it, its heading and its entries. */
	static String page(Vault v) {
		String gold = MenuTheme.HTML_GOLD, dim = MenuTheme.HTML_GREY_GREEN;
		StringBuilder h = new StringBuilder("<html><body style='color:#dce4eb'>");
		h.append("<p><font color='" + gold + "'><b>-- Captain's Log --</b></font></p>");
		boolean any = false;
		for (Map.Entry<Integer, List<MasterLog.Entry>> d : MasterLog.byDay(v).entrySet()) {
			StringBuilder day = new StringBuilder();
			for (MasterLog.Entry e : d.getValue()) {
				String line = shown(e);
				if (line == null) continue;
				String[] parts = line.split(" / ");
				day.append("<div style='margin-left:12px; margin-top:3px'>").append(XmlText.text(parts[0])).append("</div>");
				for (int i = 1; i < parts.length; i++)
					day.append("<div style='margin-left:28px'><font color='" + dim + "'>").append(XmlText.text(parts[i].trim())).append("</font></div>");
			}
			if (day.length() == 0) continue;
			any = true;
			String head = d.getKey() == 1 ? "Captains Log: Stardate Today" : "StarDate TD " + MasterLog.stardate(d.getKey());
			h.append("<p style='margin-top:12px'><font color='" + gold + "'><b>").append(head).append("</b></font></p>").append(day);
		}
		if (!any) h.append("<p><font color='" + dim + "'>Nothing written yet. The days will fill it.</font></p>");
		return h.append("</body></html>").toString();
	}
	/** One entry as the log tells it, or null for the station's housekeeping. */
	static String shown(MasterLog.Entry e) {
		if ("station".equals(e.log)) {
			String kind = e.text.split("  ", 2)[0].trim();
			if (TECHNICAL.contains(kind)) return null;
			String rest = e.text.contains("  ") ? e.text.substring(e.text.indexOf("  ") + 2).trim() : "";
			return rest.isEmpty() ? kind.charAt(0) + kind.substring(1).toLowerCase() : rest;
		}
		if (e.log.startsWith("voyage: ")) return e.log.substring(8) + ": " + e.text;
		if ("reputation".equals(e.log)) return "Reputation " + e.text;
		return e.text;
	}
}
