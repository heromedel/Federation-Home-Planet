package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;

import homeplanet.core.HomePlanet;

/**
 * The user manual (McCarthy's User-Manual.html and User-Manual.txt, bundled in the jar), from Settings' About tab
 * (heromedel, 6.73): in a window of the station's own, or in the web browser or text editor, as the player chooses.
 */
public final class ManualWindow {
	/** The cfg's line for "Open in The Home Planet Station": there only while it's Off (On is the default). */
	public static final String CFG_IN_STATION = "manual_in_station";
	static final String HTML = "User-Manual.html", TEXT = "User-Manual.txt";
	/** The widest a picture is drawn in the station's window. */
	private static final int PICTURE_W = 620;

	private ManualWindow() { }

	/** Whether the manual opens in the station's own window. */
	public static boolean inStation() { return !"false".equals(HomePlanet.config.getProperty(CFG_IN_STATION)); }
	/** The cfg's line for the manual's Dark mode: there only while it's off (heromedel, 6.73: on by default). */
	public static final String CFG_DARK = "manual_dark";
	static boolean dark() { return !"false".equals(HomePlanet.config.getProperty(CFG_DARK)); }
	/** Kept at once: the switch is in the manual's own window, not one of the settings OK waits for. */
	static void setDark(boolean on) {
		if (on) HomePlanet.config.remove(CFG_DARK);
		else HomePlanet.config.setProperty(CFG_DARK, "false");
		HomePlanet.saveConfig();
	}
	private static JCheckBox darkBox() {
		JCheckBox b = new JCheckBox("Dark mode", dark());
		b.setToolTipText("The manual in light words on a dark page, or dark on light");
		return b;
	}

	/** Opens the manual: the HTML one or the text one, in the station or outside it. */
	public static void open(Component owner, boolean html, boolean inStation) {
		String text;
		try { text = bundled(html ? HTML : TEXT); }
		catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not read its manual (" + (html ? HTML : TEXT) + ") from this build:\n" + e.getMessage()
					+ "\n\nThe manual is also in the source download, on the project's page.");
			return;
		}
		if (!inStation) { outside(owner, html, text); return; }
		// owned by the window it was opened from (Settings, modal): owned by the station's window, Settings kept it behind
		// itself and blocked it (heromedel, 6.75)
		java.awt.Window from = owner instanceof java.awt.Window ? (java.awt.Window) owner : SwingUtilities.getWindowAncestor(owner);
		JDialog d = new JDialog(from, "The Home Planet Station User Manual");
		d.setModalityType(java.awt.Dialog.ModalityType.MODELESS); // read beside the station, not instead of it
		d.getContentPane().add(html ? htmlPage(text, d) : textPage(text, d));
		d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		d.setSize(html ? 820 : 760, 760);
		d.setLocationRelativeTo(d.getOwner());
		ScreenFit.keepOnScreen(d);
		d.setVisible(true);
	}

	private static String bundled(String name) throws IOException {
		InputStream in = ManualWindow.class.getResourceAsStream("/" + name);
		if (in == null) throw new IOException(name + " is not bundled");
		try { return new String(homeplanet.core.SafeFiles.readAll(in), "UTF-8"); }
		finally { in.close(); }
	}

	/** Off: a copy in the system's temporary folder, opened by the browser (HTML) or the text editor (text). */
	private static void outside(Component owner, boolean html, String text) {
		File f = new File(System.getProperty("java.io.tmpdir"), "Home Planet Station User Manual " + HomePlanet.APP_VERSION + (html ? ".html" : ".txt"));
		try {
			java.nio.file.Files.write(f.toPath(), text.getBytes("UTF-8"));
			java.awt.Desktop desk = java.awt.Desktop.isDesktopSupported() ? java.awt.Desktop.getDesktop() : null;
			if (desk == null) throw new IOException("this system can't open files from Java");
			if (html) desk.browse(f.toURI());
			else desk.open(f);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not open the manual outside the station:\n" + e.getMessage()
					+ "\n\nIt was written to " + f.getPath() + "\nOpen it from there, or set Open in The Home Planet Station to On (Settings, About).");
		}
	}

	// ---- the HTML manual, in the station ----

	/** The page as Swing can draw it: its pictures unpacked to files and drawn to fit, its contents links working, its own styles left out for plainer ones; dark or light. */
	static JComponent htmlPage(String html, JDialog d) {
		final JEditorPane pane = new JEditorPane();
		final String page = forSwing(html);
		pane.setEditable(false);
		pane.addHyperlinkListener(new HyperlinkListener() {
			public void hyperlinkUpdate(HyperlinkEvent e) {
				if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
				String to = e.getDescription();
				if (to != null && to.startsWith("#")) { pane.scrollToReference(to.substring(1)); return; } // the contents
				try { java.awt.Desktop.getDesktop().browse(e.getURL().toURI()); } catch (Exception x) { } // a link off the page: the browser
			}
		});
		final JScrollPane sp = new JScrollPane(pane);
		sp.getVerticalScrollBar().setUnitIncrement(24);
		paint(pane, page, dark());
		pane.setCaretPosition(0);
		final JCheckBox darkBox = darkBox();
		darkBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final javax.swing.JScrollBar bar = sp.getVerticalScrollBar();
				final double at = bar.getMaximum() <= 0 ? 0 : bar.getValue() / (double) bar.getMaximum();
				setDark(darkBox.isSelected());
				paint(pane, page, darkBox.isSelected());
				SwingUtilities.invokeLater(new Runnable() { public void run() { bar.setValue((int) Math.round(at * bar.getMaximum())); } }); // the same place in the manual
			}
		});
		JPanel row = findRow(pane, d); // the same Find as the text manual's (heromedel, 6.76: the HTML one had none)
		row.add(javax.swing.Box.createHorizontalStrut(24));
		row.add(darkBox);
		JPanel p = new JPanel(new BorderLayout());
		p.add(row, BorderLayout.NORTH);
		p.add(sp, BorderLayout.CENTER);
		return p;
	}
	/** The page drawn again in light or dark: its own style sheet each time, never Swing's shared one (every HTML label in the station uses that). */
	private static void paint(JEditorPane pane, String page, boolean dark) {
		final javax.swing.text.html.StyleSheet css = new javax.swing.text.html.StyleSheet();
		String bg = dark ? "#10141b" : "#fbfaf7", fg = dark ? "#dce4eb" : "#1d2430", accent = dark ? "#e2b44f" : "#9a6a12", link = dark ? "#8fb8ff" : "#1f5fbf", muted = dark ? "#94a0ae" : "#5b6474";
		css.addRule("body { font-family: Serif; font-size: 13pt; margin: 14px 22px; color: " + fg + "; background-color: " + bg + "; }");
		css.addRule("h1 { font-family: SansSerif; font-size: 22pt; color: " + fg + "; }");
		css.addRule("h2 { font-family: SansSerif; font-size: 17pt; color: " + accent + "; margin-top: 18px; }");
		css.addRule("h3 { font-family: SansSerif; font-size: 14pt; color: " + fg + "; margin-top: 12px; }");
		css.addRule("p, li, div, span, i { color: " + fg + "; }");
		css.addRule("a { color: " + link + "; }");
		css.addRule("pre { font-family: Monospaced; font-size: 11pt; color: " + fg + "; }");
		css.addRule(".fair { font-size: 10pt; color: " + muted + "; }");
		css.addRule("h1, h2, h3 { font-weight: bold; margin-bottom: 4px; }");
		css.addRule("p { margin-top: 0px; margin-bottom: 9px; }");
		css.addRule("pre { margin-top: 4px; margin-bottom: 9px; }");
		css.addRule(".n { color: " + accent + "; }");
		pane.setEditorKit(new HTMLEditorKit() {
			@Override public javax.swing.text.html.StyleSheet getStyleSheet() { return css; }
		});
		pane.setBackground(java.awt.Color.decode(bg));
		pane.setSelectionColor(dark ? new java.awt.Color(107, 84, 32) : new java.awt.Color(243, 214, 140));
		pane.putClientProperty(FOUND, new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(pane.getSelectionColor()));
		pane.setText(named(page));
	}
	/**
	 * Swing finds a link's target only as an old-style <a name="..."> (scrollToReference): the manual marks its sections
	 * with id="...", as browsers read them, so each gets a named anchor in front of it here (heromedel, 6.76: the contents
	 * and "back to top" links went nowhere). The file itself stays as browsers want it.
	 */
	static String named(String page) {
		return page.replaceAll("<([a-zA-Z][a-zA-Z0-9]*)([^>]*?)\\sid=\"([^\"]+)\"", "<a name=\"$3\"></a><$1$2 id=\"$3\"");
	}

	private static File pictures;
	/** McCarthy's page, made plain enough for Swing (which knows HTML 3.2 and no data: pictures). */
	static String forSwing(String html) {
		String s = html.replaceAll("(?is)<head>.*?</head>", "").replaceAll("(?is)<style.*?</style>", "").replaceAll("(?i)<meta[^>]*>", "");
		s = s.replaceAll("(?i)\\sstyle=\"[^\"]*\"", ""); // the page's own inline styles (its colours are variables Swing can't read)
		s = s.replaceAll("(<span class=\"n\">[^<]*)</span>", "$1&nbsp;</span>"); // "[1] Welcome", not "[1]Welcome"
		// the contents: each entry carries its own [1]; a browser hides the list's numbers by the page's style, Swing would show both
		Matcher toc = Pattern.compile("(?is)<ol>(.*?)</ol>").matcher(s);
		if (toc.find() && toc.group(1).contains("class=\"n\""))
			s = s.substring(0, toc.start()) + "<div>" + toc.group(1).replaceAll("(?i)<li>", "<div>").replaceAll("(?i)</li>", "</div>") + "</div>" + s.substring(toc.end());
		s = s.replaceAll("(?i)<figure>", "<div>").replaceAll("(?i)</figure>", "</div>"); // a picture, then its caption under it in italics
		s = s.replaceAll("(?i)<figcaption>", "<br><i>").replaceAll("(?i)</figcaption>", "</i><br><br>");
		s = s.replaceAll("<span class=\"fair\">", "<br><span class=\"fair\">");
		// a section's id becomes a named anchor: Swing scrolls to those
		s = s.replaceAll("(?i)(<(h[1-6]|section|div|p)\\b[^>]*\\sid=\"([^\"]+)\"[^>]*>)", "$1<a name=\"$3\"></a>");
		Matcher m = Pattern.compile("(?i)<img([^>]*?)src=\"data:image/(png|jpeg|gif);base64,([^\"]+)\"([^>]*)>").matcher(s);
		StringBuffer out = new StringBuffer();
		int n = 0;
		while (m.find()) {
			String tag;
			try {
				byte[] bytes = java.util.Base64.getDecoder().decode(m.group(3));
				BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
				if (pictures == null) { pictures = java.nio.file.Files.createTempDirectory("hps-manual").toFile(); pictures.deleteOnExit(); }
				File f = new File(pictures, "picture-" + (++n) + "." + m.group(2));
				java.nio.file.Files.write(f.toPath(), bytes);
				f.deleteOnExit();
				int w = img == null ? PICTURE_W : Math.min(PICTURE_W, img.getWidth()), h = img == null ? 0 : img.getHeight() * w / Math.max(1, img.getWidth());
				String alt = attr(m.group(1) + m.group(4), "alt");
				tag = "<img src=\"" + f.toURI() + "\" width=\"" + w + "\"" + (h > 0 ? " height=\"" + h + "\"" : "") + (alt == null ? "" : " alt=\"" + alt + "\"") + ">";
			} catch (Exception e) {
				tag = ""; // a picture that can't be unpacked is left out; the words stay
			}
			m.appendReplacement(out, Matcher.quoteReplacement(tag));
		}
		m.appendTail(out);
		return out.toString();
	}
	private static String attr(String in, String name) {
		Matcher m = Pattern.compile("(?i)\\b" + name + "=\"([^\"]*)\"").matcher(in);
		return m.find() ? m.group(1) : null;
	}

	// ---- the text manual, in the station ----

	/** The text as written (its own line breaks and columns), with a Find row: the manual tells the reader to search for a section's number. */
	static JComponent textPage(String text, JDialog d) {
		final JTextArea ta = new JTextArea(text);
		ta.setEditable(false);
		colour(ta, dark());
		ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		ta.setCaretPosition(0);
		ta.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		JPanel row = findRow(ta, d);
		final JCheckBox darkBox = darkBox();
		darkBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { setDark(darkBox.isSelected()); colour(ta, darkBox.isSelected()); }
		});
		row.add(javax.swing.Box.createHorizontalStrut(24));
		row.add(darkBox);
		JPanel p = new JPanel(new BorderLayout());
		p.add(row, BorderLayout.NORTH);
		p.add(new JScrollPane(ta), BorderLayout.CENTER);
		p.setPreferredSize(new Dimension(760, 760));
		return p;
	}
	/** The Find row (a box, Find next) for either manual, and Ctrl+F to it, as the manual says. */
	private static JPanel findRow(final javax.swing.text.JTextComponent page, JDialog d) {
		final JTextField find = new JTextField(18);
		JButton next = new JButton("Find next");
		ActionListener go = new ActionListener() {
			public void actionPerformed(ActionEvent e) { findNext(page, find.getText()); }
		};
		find.addActionListener(go);
		next.addActionListener(go);
		find.setToolTipText("Search the manual: a section's number in brackets, [10] say, jumps to it. Enter finds the next");
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		row.add(new JLabel("Find:"));
		row.add(find);
		row.add(next);
		d.getRootPane().registerKeyboardAction(new ActionListener() {
			public void actionPerformed(ActionEvent e) { find.requestFocusInWindow(); find.selectAll(); }
		}, KeyStroke.getKeyStroke(KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
		return row;
	}
	/** The page's client property holding the found words' marker (its colour follows dark or light). */
	private static final String FOUND = "manual.found";
	/** The text in dark or light, and the marker for found words to match. */
	private static void colour(JTextArea ta, boolean dark) {
		ta.setBackground(dark ? new java.awt.Color(16, 20, 27) : new java.awt.Color(251, 250, 247));
		ta.setForeground(dark ? new java.awt.Color(220, 228, 235) : new java.awt.Color(29, 36, 48));
		ta.setCaretColor(ta.getForeground());
		ta.setSelectedTextColor(ta.getForeground());
		ta.setSelectionColor(dark ? new java.awt.Color(107, 84, 32) : new java.awt.Color(243, 214, 140));
		ta.putClientProperty(FOUND, new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(ta.getSelectionColor()));
		javax.swing.text.Highlighter.Highlight[] old = ta.getHighlighter().getHighlights();
		ta.getHighlighter().removeAllHighlights();
		for (javax.swing.text.Highlighter.Highlight h : old) try { ta.getHighlighter().addHighlight(h.getStartOffset(), h.getEndOffset(), (javax.swing.text.Highlighter.HighlightPainter) ta.getClientProperty(FOUND)); } catch (javax.swing.text.BadLocationException e) { }
	}
	/** The next place the words appear after the last one found, round to the top again; marked and scrolled to. */
	static boolean findNext(javax.swing.text.JTextComponent ta, String what) {
		Pattern pat = TextSearch.of(what); // the station's one way of searching (a phrase may run over a line: the text manual is wrapped)
		if (pat == null) return false;
		String all;
		try { all = ta.getDocument().getText(0, ta.getDocument().getLength()); } // the words as shown (an HTML page's getText is its markup)
		catch (javax.swing.text.BadLocationException e) { return false; }
		Matcher m = pat.matcher(all);
		if (!m.find(Math.min(ta.getSelectionEnd(), all.length())) && !m.find(0)) { java.awt.Toolkit.getDefaultToolkit().beep(); return false; }
		int at = m.start(), end = m.end();
		ta.select(at, end); // where the next search starts; the cursor stays in the Find box, so Enter finds the next
		ta.getHighlighter().removeAllHighlights();
		try { ta.getHighlighter().addHighlight(at, end, (javax.swing.text.Highlighter.HighlightPainter) ta.getClientProperty(FOUND)); } catch (javax.swing.text.BadLocationException e) { } // shown though the text isn't focused
		try {
			java.awt.Rectangle r = ta.modelToView(at);
			if (r != null) { r.height = Math.max(r.height, ta.getVisibleRect().height / 3); ta.scrollRectToVisible(r); }
		} catch (javax.swing.text.BadLocationException e) { }
		return true;
	}
}
