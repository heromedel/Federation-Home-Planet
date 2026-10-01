package homeplanet.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;
import javax.swing.JButton;

/** A small die showing five (drawn, not from the game: FTL has none), for the buttons that roll a random name. */
public class DiceIcon implements Icon {
	private final int size;
	public DiceIcon(int size) { this.size = size; }
	@Override public int getIconWidth() { return size; }
	@Override public int getIconHeight() { return size; }
	@Override public void paintIcon(Component c, Graphics g0, int x, int y) {
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int s = size - 1, arc = Math.max(3, size / 4);
		g.setColor(new Color(235, 235, 225));
		g.fillRoundRect(x, y, s, s, arc, arc);
		g.setColor(new Color(60, 60, 60));
		g.setStroke(new BasicStroke(1f));
		g.drawRoundRect(x, y, s, s, arc, arc);
		int d = Math.max(2, size / 5), lo = x + size / 4 - d / 2, hi = x + size * 3 / 4 - d / 2, mid = x + size / 2 - d / 2;
		int top = y + size / 4 - d / 2, bottom = y + size * 3 / 4 - d / 2, centre = y + size / 2 - d / 2;
		g.setColor(new Color(215, 90, 35)); // Federation orange, as the stripes on her hull
		g.fillOval(lo, top, d, d);
		g.fillOval(hi, top, d, d);
		g.fillOval(mid, centre, d, d);
		g.fillOval(lo, bottom, d, d);
		g.fillOval(hi, bottom, d, d);
		g.dispose();
	}

	/** A tiny borderless button with the die, for rolling a name. */
	public static JButton button(String tip, final Runnable roll) {
		JButton b = new JButton(new DiceIcon(16));
		b.setToolTipText(tip);
		b.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 4, 1, 4));
		b.setContentAreaFilled(false);
		b.setFocusPainted(false);
		b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		b.addActionListener(new java.awt.event.ActionListener() { public void actionPerformed(java.awt.event.ActionEvent e) { roll.run(); } });
		return b;
	}
}
