package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.charset.StandardCharsets;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;

/**
 * Send Feedback (heromedel, 5.25; Settings, Records): the feedback form in the player's browser, the version already
 * filled in, and this run's debug log on the clipboard for its last question (the player decides whether to paste it:
 * a log can hold folder paths with their user name).
 */
final class Feedback {
	private static final Logger log = LoggerFactory.getLogger(Feedback.class);
	private Feedback() { }

	/** The form players open (public by design; its responses sheet stays out of the repo). */
	static final String FORM = "https://docs.google.com/forms/d/e/1FAIpQLSekVnRyrIP7alvuATyEe-BWHs3mAQH8wjN0C4kK_cj8d6RPVA/viewform";
	/** The form's "Do you know what game version you were on?" field: filled in from the link. */
	static final String VERSION_FIELD = "entry.1534582745";

	/** The form's link with this version filled in. */
	static String link(String version) { return FORM + "?usp=pp_url&" + VERSION_FIELD + "=" + version.replace(" ", "+"); }

	/** This run's debug log (the newest in the program's log folder), or null if none has been written. */
	static File newestLog() {
		File dir = HomePlanet.logDir();
		File newest = null;
		File[] fs = dir == null ? null : dir.listFiles();
		if (fs != null) for (File f : fs) if (f.getName().startsWith("home-planet-") && f.getName().endsWith(".log") && (newest == null || f.lastModified() > newest.lastModified())) newest = f;
		return newest;
	}

	/**
	 * Says what happens, then opens the form (or shows its link to copy if no browser could open). This run's debug log
	 * goes on the clipboard only when the player asks (heromedel, 6.02: copied without asking, it replaced what they had
	 * there).
	 */
	static void send(Component owner) {
		String url = link(HomePlanet.APP_VERSION);
		boolean copied = false;
		File f = newestLog();
		// said first, then the browser (heromedel, 5.26: opened at once, the browser covered the message before it could be read)
		String about = f != null ? "To send this run's debug log too, choose Copy Log and Open Form, then paste it into the last question." : "No debug log to copy this run.";
		Object[] go = f != null ? new Object[] {"Open Form", "Copy Log and Open Form", "Cancel"} : new Object[] {"Open Form", "Cancel"};
		int pick = JOptionPane.showOptionDialog(owner, "The feedback form opens in your web browser, with this version filled in.\n\n" + about, "Send Feedback",
				JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, go, go[0]);
		if (pick < 0 || go[pick].equals("Cancel")) return;
		if (go[pick].equals("Copy Log and Open Form")) {
			try {
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(new String(SafeFiles.read(f), StandardCharsets.UTF_8)), null);
				copied = true;
			} catch (Exception e) { log.debug("Feedback: the debug log could not be copied: {}", e.toString()); }
		}
		if (copied) about = "This run's debug log is copied: paste it into the last question.";
		try {
			if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
				java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
				return;
			}
		} catch (Exception e) { log.debug("Feedback: the form could not be opened: {}", e.toString()); }
		// no browser: the link, to copy by hand
		final JTextField field = new JTextField(url, 48);
		field.setEditable(false);
		field.selectAll();
		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.add(new javax.swing.JLabel("The Home Planet Station could not open your web browser. Open this link to send feedback:"), BorderLayout.NORTH);
		p.add(field, BorderLayout.CENTER);
		if (copied) p.add(new javax.swing.JLabel("<html>" + about + "<br>Copy Link puts the link on the clipboard in its place.</html>"), BorderLayout.SOUTH);
		Object[] options = {"Copy Link", "Close"};
		int r = JOptionPane.showOptionDialog(owner, p, "Send Feedback", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
		if (r == 0) {
			try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null); }
			catch (Exception e) { log.debug("Feedback: the link could not be copied: {}", e.toString()); }
		}
	}
}
