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
 * A little two-way switch drawn as Commission's Original | AE switch is (6.44, heromedel): Pay From's Ship | Cargo on the
 * Shop and the Refit tabs. Each half is sized to its own word, in a small font. Click flips it; the lit half is the one chosen.
 */
final class FlipSwitch extends JComponent {
	private static final Color TRACK = new Color(36, 46, 56), EDGE = new Color(90, 108, 120), LIT = new Color(235, 140, 50), LIT_TEXT = new Color(24, 20, 16);
	private final String left, right;
	private boolean onRight, hover;
	private Runnable onChange;

	FlipSwitch(String left, String right) {
		this.left = left;
		this.right = right;
		setFont(MenuTheme.LABEL_FONT.deriveFont(11f));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				onRight = !onRight;
				repaint();
				if (onChange != null) onChange.run();
			}
			public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
			public void mouseExited(MouseEvent e) { hover = false; repaint(); }
		});
	}

	boolean right() { return onRight; }
	void setRight(boolean r) { onRight = r; repaint(); }
	void onChange(Runnable r) { onChange = r; }

	public Dimension getPreferredSize() {
		FontMetrics fm = getFontMetrics(getFont());
		return new Dimension(fm.stringWidth(left) + fm.stringWidth(right) + 32, fm.getHeight() + 4);
	}

	protected void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		FontMetrics fm = g.getFontMetrics(getFont());
		int w = getWidth() - 1, h = getHeight() - 1, r = h, half = fm.stringWidth(left) + 16; // each half its own word's width
		g.setColor(TRACK);
		g.fillRoundRect(0, 0, w, h, r, r);
		g.setColor(LIT);
		if (onRight) g.fillRoundRect(half, 2, w - half - 2, h - 3, r - 4, r - 4);
		else g.fillRoundRect(2, 2, half - 2, h - 3, r - 4, r - 4);
		g.setColor(hover ? MenuTheme.GOLD : EDGE);
		g.drawRoundRect(0, 0, w, h, r, r);
		int base = (h - fm.getHeight()) / 2 + fm.getAscent() + 1;
		g.setFont(getFont());
		g.setColor(!onRight ? LIT_TEXT : MenuTheme.GREY_GREEN);
		g.drawString(left, (half - fm.stringWidth(left)) / 2 + 1, base);
		g.setColor(onRight ? LIT_TEXT : MenuTheme.GREY_GREEN);
		g.drawString(right, half + (w - half - fm.stringWidth(right)) / 2, base);
		g.dispose();
	}
}
