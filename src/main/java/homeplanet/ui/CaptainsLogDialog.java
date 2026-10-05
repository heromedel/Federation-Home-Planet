package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import homeplanet.parser.XmlText;
import homeplanet.vault.CaptainsLog;
import homeplanet.vault.Vault;

/**
 * The Captain's Log (heromedel, 5.17; written as a story in 5.18), in Captain's Quarters: each day of the career and
 * what happened on it, told by {@link homeplanet.vault.CaptainsLog}. "Detailed Log Entries" adds the specifics under each
 * line (prices, reputation, who went where). The first day reads "Stardate Today", every later one "Stardate x.x.x.x".
 */
final class CaptainsLogDialog extends JDialog {
	private final javax.swing.JEditorPane page = new javax.swing.JEditorPane("text/html", "");
	private final javax.swing.JCheckBox detailed = new javax.swing.JCheckBox("Detailed Log Entries");
	private final JScrollPane scroll;

	static void open(java.awt.Component owner) { new CaptainsLogDialog(owner).setVisible(true); }

	private CaptainsLogDialog(java.awt.Component owner) {
		super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), "Captain's Log", ModalityType.APPLICATION_MODAL);
		page.putClientProperty(javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		page.setEditable(false);
		page.setBackground(RecordsLog.BG);
		page.setFont(MenuTheme.TEXT_FONT);
		page.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 14));
		scroll = new JScrollPane(page);
		scroll.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, MenuTheme.GOLD));
		scroll.getViewport().setBackground(RecordsLog.BG);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(22);
		scroll.setPreferredSize(new Dimension(760, 460));
		detailed.setToolTipText("Under each line: what things cost, the reputation, who went where");
		detailed.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { fill(); } });
		JPanel top = new JPanel(new BorderLayout());
		top.add(detailed, BorderLayout.WEST);
		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		JPanel south = new JPanel(new BorderLayout());
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
	private void fill() {
		page.setText(page(Vault.get(), detailed.isSelected()));
		final javax.swing.JScrollBar bar = scroll.getVerticalScrollBar();
		SwingUtilities.invokeLater(new Runnable() { public void run() { bar.setValue(bar.getMaximum()); } }); // the latest day in view
	}

	/** The log as a page: its title, then each day's heading and lines (quiet stretches as "Nothing to report"). */
	static String page(Vault v, boolean details) {
		String gold = MenuTheme.HTML_GOLD, dim = MenuTheme.HTML_GREY_GREEN;
		StringBuilder h = new StringBuilder("<html><body style='color:#dce4eb'>");
		h.append("<p><font color='" + gold + "'><b>-- Captain's Log --</b></font></p>");
		for (CaptainsLog.Day d : CaptainsLog.days(v)) {
			h.append("<p style='margin-top:12px'><font color='" + gold + "'><b>").append(XmlText.text(d.heading())).append("</b></font></p>");
			if (d.lines.isEmpty()) h.append("<div style='margin-left:12px'><font color='" + dim + "'>Nothing to report.</font></div>");
			for (CaptainsLog.Line l : d.lines) {
				h.append("<div style='margin-left:12px; margin-top:3px'>").append(XmlText.text(l.text)).append("</div>");
				if (details) for (String x : l.details) h.append("<div style='margin-left:28px'><font color='" + dim + "'>").append(XmlText.text(x)).append("</font></div>");
			}
		}
		return h.append("</body></html>").toString();
	}
}
