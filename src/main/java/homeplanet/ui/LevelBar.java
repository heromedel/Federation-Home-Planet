package homeplanet.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JPanel;

/**
 * A bar of segments for a level: green to the value, grey to the vanilla max, amber past it (the player's call), and
 * the broken bars in red at the top. The design screen's number rows and the ship report's systems share it, so the
 * two screens speak one language.
 */
public class LevelBar extends JPanel {
	/** The bar's width: the segments share it, however many (a 30-point hull as long as a 3-level system). */
	public static final int WIDTH = 92;
	static final Color GREEN = new Color(67, 192, 74), AMBER = new Color(224, 176, 32), EMPTY = new Color(70, 84, 94), BROKEN = new Color(214, 76, 60);

	private int value, max, broken;
	private final int width, height;

	public LevelBar(int value, int max) { this(value, max, 0, WIDTH, 14); }
	/** @param broken how many of the value's bars are broken (drawn in red from the top) */
	public LevelBar(int value, int max, int broken, int width, int height) {
		this.value = value; this.max = max; this.broken = broken; this.width = width; this.height = height;
		setOpaque(false);
		setToolTipText("Green to the vanilla max; amber past it" + (broken > 0 ? "; red: broken" : ""));
	}
	public void set(int value, int max) { this.value = value; this.max = max; repaint(); }

	protected void paintComponent(Graphics g0) {
		super.paintComponent(g0);
		Graphics2D g = (Graphics2D) g0.create();
		int segs = Math.max(1, Math.max(max, value)), gap = segs > 12 ? 1 : 2, w = Math.max(2, (width - (segs - 1) * gap) / segs), h = getHeight() - 2;
		for (int i = 0; i < segs; i++) {
			Color c = i >= value ? EMPTY : i >= value - broken ? BROKEN : i >= max ? AMBER : GREEN;
			g.setColor(c);
			g.fillRect(i * (w + gap), 1, w, h);
		}
		g.dispose();
	}
	public Dimension getPreferredSize() { return new Dimension(width, height); }
	public Dimension getMaximumSize() { return getPreferredSize(); }
	public Dimension getMinimumSize() { return getPreferredSize(); }
}
