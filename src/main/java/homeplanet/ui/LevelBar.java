package homeplanet.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JPanel;

/**
 * A bar of segments for a level: green to the value, grey to the vanilla max, amber past it (the player's call), and
 * the broken bars in red at the top. Past MAX_SEGMENTS the bar stands down and the row says it in words instead
 * ({@link #words}: "12 / 2 broken"), so a big reactor or a design past the max never runs off the screen (heromedel).
 * The design screen's number rows and the ship report's systems share it, so the two screens speak one language.
 */
public class LevelBar extends JPanel {
	/** The bar's width: the segments share it, however many (a 30-point hull as long as a 3-level system). */
	public static final int WIDTH = 92;
	/** A segment's width: the same for every bar. */
	public static final int SEGMENT = 8;
	/** The most segments a bar draws: past this it's words (every vanilla max is 8 or under, so a stock ship always has bars). */
	public static final int MAX_SEGMENTS = 10;
	/** Whether this level is said in words rather than drawn. */
	public static boolean asWords(int value, int max) { return Math.max(value, max) > MAX_SEGMENTS; }
	/** The level in words, for a row past the bar's reach: "12", or "12 / 2 broken". */
	public static String words(int value, int broken) { return value + (broken > 0 ? " / " + broken + " broken" : ""); }
	/** Whether this bar is standing down for words. */
	public boolean words() { return asWords(value, max); }
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
	public void set(int value, int max) { boolean was = words(); this.value = value; this.max = max; if (was != words()) revalidate(); repaint(); }

	protected void paintComponent(Graphics g0) {
		super.paintComponent(g0);
		if (words()) return;
		Graphics2D g = (Graphics2D) g0.create();
		// every segment the same small size, whatever the max (a 3-level system's as wide as a reactor's)
		int segs = Math.max(1, Math.max(max, value)), gap = 2, w = SEGMENT, h = getHeight() - 2;
		for (int i = 0; i < segs; i++) {
			Color c = i >= value ? EMPTY : i >= value - broken ? BROKEN : i >= max ? AMBER : GREEN;
			g.setColor(c);
			g.fillRect(i * (w + gap), 1, w, h);
		}
		g.dispose();
	}
	public Dimension getPreferredSize() { return new Dimension(words() ? 0 : width, height); }
	public Dimension getMaximumSize() { return getPreferredSize(); }
	public Dimension getMinimumSize() { return getPreferredSize(); }
}
