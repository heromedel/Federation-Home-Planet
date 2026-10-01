package homeplanet.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.xml.ShipBlueprint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Space Dock's backdrop, put together from the player's own FTL data so the repo carries none of it: FTL's
 * starfield, a populated planet low on the left, The Home Planet Station (a Federation Cruiser, cut behind her engine
 * bays and mirrored) and two or three ships leaving her. The ships are drawn from those Commission would list, so the
 * unlock rules apply to them too. Every effect (running lights, planetshine, engine glow) is placed from where its
 * ship actually landed, so they always line up.
 */
final class StationBackdrop {
	private static final Logger log = LoggerFactory.getLogger(StationBackdrop.class);

	/** The picture's size; the station's centre, and the left edge of her hull at her widest. */
	static final int W = 1920, H = 1080, STATION_X = 1420;
	/** The cruiser's scale on the station, and how far behind her engine bays she's cut (in her own pixels). */
	private static final double STATION_SCALE = 0.78;
	private static final int CUT = 180;
	/** The planet: its scale and where its top-left corner goes (low on the left, mostly the night side). */
	private static final double PLANET_SCALE = 2.6;
	private static final int PLANET_X = -560, PLANET_BELOW = 700;

	/**
	 * Open space the ships may fly through, clear of the panels and buttons at 16:9: a centre, a heading (degrees,
	 * counter-clockwise from pointing right, always away from the station) and a length in pixels (shorter is further).
	 */
	private static final int[][] LANES = {
		{1120, 470, 168, 100},
		{860, 610, 196, 66},
		{1700, 890, -28, 80},
		{1060, 790, 205, 74},
		{1640, 720, -8, 58},
		{1160, 990, 222, 62},
	};
	/** How many ships, at least and at most. */
	private static final int SHIPS_MIN = 2, SHIPS_MAX = 3;

	final BufferedImage image;
	/** The left edge of the station's hull at her widest, in the picture (the boarded ship's panel keeps left of it). */
	final int saucerLeft;

	private StationBackdrop(BufferedImage image, int saucerLeft) {
		this.image = image;
		this.saucerLeft = saucerLeft;
	}

	/** A new backdrop with a fresh roll of ships, or null if FTL's pictures can't be read (the stock picture is used then). */
	static StationBackdrop make(MainFrame frame, Random rng) {
		try {
			BufferedImage stars = frame.getResourceImage("img/stars/bg_dullstars.png", false);
			BufferedImage planet = frame.getResourceImage("img/stars/planet_populated_brown.png", false);
			BufferedImage cruiser = frame.getResourceImage("img/ship/fed_cruiser_base.png", false);
			if (stars == null || planet == null || cruiser == null) return null;

			BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = out.createGraphics();
			smooth(g);
			g.drawImage(stars, 0, 0, W, H, null);
			int pw = (int) Math.round(planet.getWidth() * PLANET_SCALE), ph = (int) Math.round(planet.getHeight() * PLANET_SCALE);
			g.drawImage(planet, PLANET_X, H - ph + PLANET_BELOW, pw, ph, null);

			BufferedImage station = station(cruiser);
			int sx = STATION_X - station.getWidth() / 2, sy = (H - station.getHeight()) / 2;
			g.drawImage(station, sx, sy, null);
			int[] edges = hullEdges(station);
			lights(g, sx, sy, station, edges);

			for (Ship s : roll(frame, rng)) drawShip(g, s);
			g.dispose();
			return new StationBackdrop(out, sx + edges[0]);
		} catch (Exception e) {
			log.warn("Could not build the Space Dock backdrop from FTL's pictures; using the stock one", e);
			return null;
		}
	}

	// ---- the station ----

	/** The cruiser nose up, cut behind her engine bays, with her mirror image below: one station, no seam. */
	private static BufferedImage station(BufferedImage cruiser) {
		BufferedImage piece = cruiser.getSubimage(CUT, 0, cruiser.getWidth() - CUT, cruiser.getHeight()); // the nose end, facing right
		int len = (int) Math.round(piece.getWidth() * STATION_SCALE), wid = (int) Math.round(piece.getHeight() * STATION_SCALE);
		BufferedImage st = new BufferedImage(wid, len * 2, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = st.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR); // crisp pixels, as FTL draws ships
		// upper half: turned a quarter left, so the nose points up and the cut edge is at the bottom
		AffineTransform up = new AffineTransform();
		up.translate(0, len);
		up.rotate(-Math.PI / 2);
		up.scale(STATION_SCALE, STATION_SCALE);
		g.drawImage(piece, up, null);
		// lower half: the upper half's mirror image
		AffineTransform down = new AffineTransform();
		down.translate(0, len * 2);
		down.scale(1, -1);
		down.concatenate(up);
		g.drawImage(piece, down, null);
		// planetshine: a faint blue from the planet below and to the left, only on the hull
		g.setComposite(AlphaComposite.SrcAtop);
		g.setPaint(new GradientPaint(0, len * 2, new Color(90, 150, 255, 70), wid, 0, new Color(90, 150, 255, 0)));
		g.fillRect(0, 0, wid, len * 2);
		g.dispose();
		return st;
	}

	/** Her solid hull: {left, right} at her widest row, {top, bottom} down her middle (the soft glow around her left out). */
	private static int[] hullEdges(BufferedImage st) {
		int row = st.getHeight() / 2, col = st.getWidth() / 2;
		int left = 0, right = st.getWidth() - 1, top = 0, bottom = st.getHeight() - 1;
		while (left < right && alpha(st, left, row) < 230) left++;
		while (right > left && alpha(st, right, row) < 230) right--;
		while (top < bottom && alpha(st, col, top) < 230) top++;
		while (bottom > top && alpha(st, col, bottom) < 230) bottom--;
		return new int[] {left, right, top, bottom};
	}
	private static int alpha(BufferedImage im, int x, int y) {
		return im.getRGB(x, y) >>> 24;
	}

	/** Running lights: red to port and green to starboard on her ring, white at both ends. */
	private static void lights(Graphics2D g, int sx, int sy, BufferedImage st, int[] e) {
		int mid = sy + st.getHeight() / 2, cx = sx + st.getWidth() / 2;
		light(g, sx + e[0] + 3, mid, new Color(255, 60, 50));
		light(g, sx + e[1] - 3, mid, new Color(60, 255, 110));
		light(g, cx, sy + e[2] + 3, Color.white);
		light(g, cx, sy + e[3] - 3, Color.white);
	}
	private static void light(Graphics2D g, double x, double y, Color c) {
		glow(g, x, y, 14, c, 0.85f);
		g.setColor(c);
		g.fillOval((int) Math.round(x) - 2, (int) Math.round(y) - 2, 5, 5);
	}

	/** A soft round glow fading to nothing at its edge. */
	private static void glow(Graphics2D g, double x, double y, double r, Color c, float strength) {
		if (r < 1) return;
		Color full = new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * strength));
		Color quarter = new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(64 * strength));
		Color none = new Color(c.getRed(), c.getGreen(), c.getBlue(), 0);
		g.setPaint(new RadialGradientPaint((float) x, (float) y, (float) r, new float[] {0f, 0.5f, 1f}, new Color[] {full, quarter, none},
				MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fillOval((int) Math.floor(x - r), (int) Math.floor(y - r), (int) Math.ceil(r * 2), (int) Math.ceil(r * 2));
	}

	// ---- the ships leaving her ----

	private static final class Ship {
		final BufferedImage art;
		final double x, y, heading, length;
		final float alpha;
		Ship(BufferedImage art, double x, double y, double heading, double length, float alpha) {
			this.art = art; this.x = x; this.y = y; this.heading = heading; this.length = length; this.alpha = alpha;
		}
	}

	/** Two or three ships Commission would list, each in a different lane, with a little jitter. */
	private static List<Ship> roll(MainFrame frame, Random rng) {
		List<BufferedImage> pool = new ArrayList<BufferedImage>();
		for (String id : CommissionDialog.shownBlueprintIds()) {
			ShipBlueprint bp = DataManager.get().getShip(id);
			if (bp == null) continue;
			BufferedImage art = frame.getResourceImage("img/ship/" + bp.getGraphicsBaseName() + "_base.png", false);
			if (art != null) pool.add(art);
		}
		List<Ship> ships = new ArrayList<Ship>();
		if (pool.isEmpty()) return ships;
		List<int[]> lanes = new ArrayList<int[]>();
		Collections.addAll(lanes, LANES);
		Collections.shuffle(lanes, rng);
		int n = SHIPS_MIN + rng.nextInt(SHIPS_MAX - SHIPS_MIN + 1);
		for (int i = 0; i < n && i < lanes.size(); i++) {
			int[] l = lanes.get(i);
			BufferedImage art = pool.get(rng.nextInt(pool.size()));
			double length = l[3] * (0.85 + rng.nextDouble() * 0.3);
			float alpha = (float) Math.max(0.7, Math.min(0.95, 0.55 + length / 250)); // further (smaller) is fainter
			ships.add(new Ship(art, l[0] + rng.nextInt(41) - 20, l[1] + rng.nextInt(41) - 20, l[2] + rng.nextInt(21) - 10, length, alpha));
		}
		return ships;
	}

	/** One ship, scaled to her length and turned to her heading, with her engines glowing behind her. */
	private static void drawShip(Graphics2D g, Ship s) {
		double scale = s.length / s.art.getWidth(); // FTL's hulls face right, so width is length
		double rad = Math.toRadians(s.heading);
		// her rear is half a length behind her centre, opposite her heading (screen y points down)
		double rx = s.x - Math.cos(rad) * s.length / 2, ry = s.y + Math.sin(rad) * s.length / 2;
		glow(g, rx, ry, s.length * 0.35, new Color(120, 190, 255), 0.75f * s.alpha);
		AffineTransform t = new AffineTransform();
		t.translate(s.x, s.y);
		t.rotate(-rad);
		t.scale(scale, scale);
		t.translate(-s.art.getWidth() / 2.0, -s.art.getHeight() / 2.0);
		java.awt.Composite old = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, s.alpha));
		smooth(g);
		g.drawImage(s.art, t, null);
		g.setComposite(old);
	}

	private static void smooth(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}
}
