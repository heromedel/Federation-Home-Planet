package homeplanet.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;

/**
 * A little flip switch for a new ship's content: Original on the left, AE (FTL's Advanced Edition content on) on the right. Click flips it;
 * locked on AE (greyed, with the reason as its tooltip) for ships that need the Advanced Edition.
 */
final class AeSwitch extends JComponent {
	private static final String ON = "AE", OFF = "Original";
	private static final Color TRACK = new Color(36, 46, 56), EDGE = new Color(90, 108, 120), LIT = new Color(235, 140, 50),
			LIT_TEXT = new Color(24, 20, 16), LOCKED = new Color(120, 96, 70);
	private boolean ae = true, hover;
	private String lockReason;
	private Runnable onChange;

	AeSwitch() {
		setFont(MenuTheme.LABEL_FONT);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (lockReason != null) return;
				ae = !ae;
				tip();
				repaint();
				if (onChange != null) onChange.run();
			}
			public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
			public void mouseExited(MouseEvent e) { hover = false; repaint(); }
		});
		tip();
	}

	/** True for Advanced Edition content on. */
	boolean ae() { return ae; }
	void onChange(Runnable r) { onChange = r; }

	/** Locks the switch on AE for this reason, or frees it (null). A ship that needs AE always gets it. */
	void lock(String reason) {
		lockReason = reason;
		if (reason != null) ae = true;
		setCursor(Cursor.getPredefinedCursor(reason == null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
		tip();
		repaint();
	}

	private void tip() {
		setToolTipText(lockReason != null ? "<html>Advanced Edition content: needed for her.<br>" + lockReason + "</html>"
				: ae ? "<html>Advanced Edition content on, as FTL's New Game has it: its sectors, events, crew and gear.<br>Click for an Original run.</html>"
				: "<html>Original: Advanced Edition content off for her runs, as FTL's New Game toggle has it.<br>Click to turn it back on.</html>");
	}

	public Dimension getPreferredSize() {
		FontMetrics fm = getFontMetrics(getFont());
		return new Dimension(fm.stringWidth(ON) + fm.stringWidth(OFF) + 44, fm.getHeight() + 8);
	}

	protected void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		FontMetrics fm = g.getFontMetrics(getFont());
		int w = getWidth() - 1, h = getHeight() - 1, r = h;
		int split = fm.stringWidth(OFF) + 22; // the Original half, then the AE half on the right
		g.setColor(TRACK);
		g.fillRoundRect(0, 0, w, h, r, r);
		// the lit half
		g.setColor(lockReason != null ? LOCKED : LIT);
		if (ae) g.fillRoundRect(split, 2, w - split - 2, h - 3, r - 4, r - 4);
		else g.fillRoundRect(2, 2, split - 2, h - 3, r - 4, r - 4);
		g.setColor(hover && lockReason == null ? MenuTheme.GOLD : EDGE);
		g.drawRoundRect(0, 0, w, h, r, r);
		int base = (h - fm.getHeight()) / 2 + fm.getAscent() + 1;
		g.setFont(getFont());
		g.setColor(!ae ? LIT_TEXT : lockReason != null ? new Color(90, 100, 104) : MenuTheme.GREY_GREEN);
		g.drawString(OFF, (split - fm.stringWidth(OFF)) / 2 + 1, base);
		g.setColor(ae ? LIT_TEXT : MenuTheme.GREY_GREEN);
		g.drawString(ON, split + (w - split - fm.stringWidth(ON)) / 2, base);
		g.dispose();
	}
}
