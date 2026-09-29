package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import homeplanet.resource.ResourceClass;

/**
 * The Space Dock's backdrop: the station art, scaled to fill the window without stretching, and slid left so the
 * station stands clear of the button column. The Space Dock itself sits on top (its ship list scrolls on its own).
 */
public class SpaceDockScrollPane extends JPanel {
	/** The art's size, and the station's centre, saucer underside and saucer's left rim in it. */
	static final int ART_W = 1480, ART_H = 794, STATION_X = 1005, SAUCER_BOTTOM = 233, SAUCER_LEFT = 789;
	/** How far left of the window's right edge the station's centre stands. */
	static final int STATION_FROM_RIGHT = 497;

	private static BufferedImage art = null;

	public SpaceDockScrollPane(JPanel panel) {
		setLayout(new BorderLayout(0, 0));
		setBackground(Color.black);
		add(panel, BorderLayout.CENTER);
	}

	/** The backdrop's scale for a window of this size. */
	static double scale(int w, int h) {
		return Math.max(h / (double) ART_H, w / (double) ART_W);
	}
	/** Where the backdrop's left edge is drawn (0 or less). */
	static int offsetX(int w, int h) {
		double s = scale(w, h);
		long x = Math.round(w - STATION_FROM_RIGHT - STATION_X * s);
		x = Math.max(x, (long) Math.ceil(w - ART_W * s)); // never leave a bare strip on the right
		return (int) Math.min(0, x);
	}

	@Override
	public void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0;
		g.setColor(Color.black);
		g.fillRect(0, 0, getWidth(), getHeight());
		if (art == null) {
			try { art = ImageIO.read(new ResourceClass().getClass().getResource("SpaceDockSplash.png")); } catch (Exception e) { return; }
		}
		double s = scale(getWidth(), getHeight());
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(art, offsetX(getWidth(), getHeight()), 0, (int) Math.round(ART_W * s), (int) Math.round(ART_H * s), null);
	}
}
