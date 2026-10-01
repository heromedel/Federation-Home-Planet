package homeplanet.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Cargo Bay's backdrop: a deck plan of The Home Planet Station's hold in FTL's style (grey floor on FTL's 35-pixel
 * squares, black walls, orange doors), stacked with containers, crates, pallets and barrels (drawn here), and FTL's own
 * weapons in an armory, drones on their pads, hull pieces from the commissionable ships in a salvage bay, and teleporter pads (read from the player's
 * ftl.dat, so the repo carries none of FTL's art). What goes where is rolled, so the hold looks a little different each time.
 */
final class CargoHoldBackdrop {
	private static final Logger log = LoggerFactory.getLogger(CargoHoldBackdrop.class);

	/** Drawn at FTL's own scale, then doubled with sharp pixels. */
	private static final int S = 35, ZOOM = 2, W = 1920 / ZOOM, H = 1080 / ZOOM;
	private static final Color SPACE = new Color(18, 20, 24), FLOOR = new Color(104, 108, 114), GRID = new Color(88, 92, 98),
			WALL = new Color(10, 10, 12), DOOR = new Color(222, 150, 64), OUTLINE = new Color(14, 14, 16), SHADOW = new Color(40, 42, 46),
			RACK = new Color(78, 82, 88), PALLET = new Color(96, 78, 56), PAD_RING = new Color(250, 210, 120);
	/** Container paint, with the shade of its ribs. */
	private static final Color[][] PAINT = {
		{new Color(92, 98, 92), new Color(70, 76, 70)}, {new Color(120, 112, 84), new Color(92, 86, 64)},
		{new Color(86, 96, 112), new Color(64, 72, 86)}, {new Color(150, 152, 150), new Color(112, 114, 112)},
		{new Color(122, 74, 60), new Color(94, 56, 46)},
	};
	/** Markings: Federation orange, the station's gold, blue, white. */
	private static final Color[] BANDS = {new Color(236, 118, 36), new Color(250, 210, 120), new Color(90, 160, 230), new Color(230, 230, 225)};
	private static final Color[] BARRELS = {new Color(150, 60, 50), new Color(70, 120, 80), new Color(90, 100, 120), new Color(180, 150, 70)};

	/** The rooms, in squares: x, y, width, height. */
	private static final int[] YARD_A = {1, 1, 9, 5}, ARMORY = {10, 1, 8, 4}, YARD_B = {18, 1, 9, 6}, PADS = {1, 6, 5, 5},
			YARD_C = {6, 6, 8, 5}, DRONES = {14, 5, 4, 6}, SALVAGE = {18, 7, 9, 7}, BARREL_STORE = {1, 11, 8, 3}, YARD_D = {9, 11, 9, 3};
	private static final int[][] ROOMS = {YARD_A, ARMORY, YARD_B, PADS, YARD_C, DRONES, SALVAGE, BARREL_STORE, YARD_D};

	private static final String[] WEAPONS = {"img/weapons/ion_1_strip8.png:8", "img/weapons/beam_1_strip8.png:8",
		"img/weapons/missiles_1_strip3.png:3", "img/weapons/bomb_1_strip9.png:9"};
	private static final String[] DRONE_ART = {"drone_combat", "drone_defense", "drone_beam", "drone_shiprepair", "drone_anti"};
	/** The salvage bay's pieces when no commissionable ship's can be found. */
	private static final String[] GIBS = {"fed_cruiser_gib1", "fed_cruiser_gib2", "fed_cruiser_gib3", "fed_cruiser_gib4",
		"kestral_gib1", "kestral_gib2", "kestral_gib3", "kestral_gib4"};

	private final Graphics2D g;
	private final Random rng;
	private final MainFrame frame;

	private CargoHoldBackdrop(Graphics2D g, Random rng, MainFrame frame) {
		this.g = g; this.rng = rng; this.frame = frame;
	}

	/** A freshly rolled hold, 1920x1080; null if it can't be drawn (the Cargo Bay is plain then). */
	static BufferedImage make(MainFrame frame, Random rng) {
		try {
			BufferedImage small = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = small.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			new CargoHoldBackdrop(g, rng, frame).draw();
			g.dispose();
			tone(small);
			BufferedImage out = new BufferedImage(W * ZOOM, H * ZOOM, BufferedImage.TYPE_INT_RGB);
			Graphics2D o = out.createGraphics();
			o.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			o.drawImage(small, 0, 0, W * ZOOM, H * ZOOM, null);
			o.dispose();
			return out;
		} catch (Exception e) {
			log.warn("Could not draw the Cargo Hold backdrop", e);
			return null;
		}
	}

	/** How much colour the hold keeps (FTL's interiors are muted greys), and how bright it is. */
	private static final float SATURATION = 0.5f, BRIGHTNESS = 0.85f;
	/** Mutes the whole hold at once, drawn parts and FTL's pictures alike, toward FTL's own interiors. */
	private static void tone(BufferedImage img) {
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				int p = img.getRGB(x, y), a = p >>> 24, r = (p >> 16) & 255, gr = (p >> 8) & 255, b = p & 255;
				float lum = 0.3f * r + 0.59f * gr + 0.11f * b;
				r = clamp((lum + (r - lum) * SATURATION) * BRIGHTNESS);
				gr = clamp((lum + (gr - lum) * SATURATION) * BRIGHTNESS);
				b = clamp((lum + (b - lum) * SATURATION) * BRIGHTNESS);
				img.setRGB(x, y, (a << 24) | (r << 16) | (gr << 8) | b);
			}
		}
	}
	private static int clamp(float v) { return Math.max(0, Math.min(255, Math.round(v))); }

	private void draw() {
		fill(SPACE, 0, 0, W, H);
		for (int[] r : ROOMS) {
			fill(FLOOR, r[0] * S, r[1] * S, r[2] * S, r[3] * S);
			g.setColor(GRID);
			for (int i = r[0] + 1; i < r[0] + r[2]; i++) g.drawLine(i * S, r[1] * S, i * S, (r[1] + r[3]) * S);
			for (int j = r[1] + 1; j < r[1] + r[3]; j++) g.drawLine(r[0] * S, j * S, (r[0] + r[2]) * S, j * S);
		}
		g.setColor(WALL);
		g.setStroke(new BasicStroke(3));
		for (int[] r : ROOMS) g.drawRect(r[0] * S, r[1] * S, r[2] * S, r[3] * S);
		g.setStroke(new BasicStroke(1));
		for (int[] r : ROOMS) {
			int dx = (r[0] + rng.nextInt(r[2])) * S + 8;
			box(DOOR, WALL, dx, (r[1] + r[3]) * S - 3, 20, 7);
			int dy = (r[1] + rng.nextInt(r[3])) * S + 8;
			box(DOOR, WALL, (r[0] + r[2]) * S - 3, dy, 7, 20);
		}
		yard(YARD_A, 0.8);
		yard(YARD_B, 0.75);
		yard(YARD_C, 0.7);
		yard(YARD_D, 0.65);
		barrels();
		armory();
		droneBay();
		salvage();
		pads();
		fitting("room_weapons", ARMORY[0], ARMORY[1]);
		fitting("room_doors", YARD_A[0], YARD_A[1]);
		fitting("room_medbay", PADS[0], PADS[1]);
	}

	// ---- the station's own stock ----

	/** Containers both ways, crates, pallets and the odd barrel, with an aisle every third row. */
	private void yard(int[] r, double density) {
		Set<Integer> taken = new HashSet<Integer>();
		for (int j = 0; j < r[3]; j++) {
			if (j % 3 == 2) continue;
			for (int i = 0; i < r[2]; i++) {
				if (taken.contains(key(i, j)) || rng.nextDouble() > density) continue;
				double roll = rng.nextDouble();
				int px = (r[0] + i) * S + 4, py = (r[1] + j) * S + 4;
				if (roll < 0.4 && i + 1 < r[2] && !taken.contains(key(i + 1, j))) {
					container(px, py, false); taken.add(key(i, j)); taken.add(key(i + 1, j));
				} else if (roll < 0.6 && j % 3 == 0 && j + 1 < r[3] && !taken.contains(key(i, j + 1))) {
					container(px, py, true); taken.add(key(i, j)); taken.add(key(i, j + 1));
				} else if (roll < 0.75) {
					crate(px + 1, py + 1); taken.add(key(i, j));
				} else if (roll < 0.9) {
					pallet(px, py); taken.add(key(i, j));
				} else {
					barrel(px + 2, py + 2); taken.add(key(i, j));
				}
			}
		}
	}
	private static int key(int i, int j) { return i * 100 + j; }

	/** A long container, lying either way, ribbed across, sometimes banded, striped or with painted ends. */
	private void container(int x, int y, boolean upright) {
		int w = upright ? S - 8 : S * 2 - 6, h = upright ? S * 2 - 6 : S - 8;
		Color[] p = PAINT[rng.nextInt(PAINT.length)];
		fill(SHADOW, x + 2, y + 3, w + 1, h + 1);
		box(p[0], OUTLINE, x, y, w, h);
		g.setColor(p[1]);
		if (upright) for (int j = y + 6; j < y + h - 3; j += 5) g.drawLine(x + 3, j, x + w - 3, j);
		else for (int i = x + 6; i < x + w - 3; i += 5) g.drawLine(i, y + 3, i, y + h - 3);
		double roll = rng.nextDouble();
		Color b = BANDS[rng.nextInt(BANDS.length)];
		if (roll < 0.25) { // a band across her middle
			if (upright) fill(b, x + 2, y + h / 2 - 3, w - 3, 7); else fill(b, x + w / 2 - 3, y + 2, 7, h - 3);
		} else if (roll < 0.45) { // a stripe along her length
			if (upright) fill(b, x + w / 2 - 1, y + 3, 3, h - 5); else fill(b, x + 3, y + h / 2 - 1, w - 5, 3);
		} else if (roll < 0.6) { // painted ends
			if (upright) { fill(b, x + 2, y + 2, w - 3, 4); fill(b, x + 2, y + h - 5, w - 3, 4); }
			else { fill(b, x + 2, y + 2, 4, h - 3); fill(b, x + w - 5, y + 2, 4, h - 3); }
		}
	}
	private void crate(int x, int y) {
		Color[] p = PAINT[rng.nextInt(PAINT.length)];
		int w = S - 10;
		fill(SHADOW, x + 2, y + 3, w + 1, w + 1);
		box(p[0], OUTLINE, x, y, w, w);
		g.setColor(p[1]);
		if (rng.nextBoolean()) {
			g.setStroke(new BasicStroke(2));
			g.drawLine(x + 3, y + 3, x + w - 3, y + w - 3);
			g.drawLine(x + w - 3, y + 3, x + 3, y + w - 3);
			g.setStroke(new BasicStroke(1));
		} else {
			g.drawRect(x + 5, y + 5, w - 10, w - 10);
			g.drawRect(x + 6, y + 6, w - 12, w - 12);
		}
	}
	/** Four small boxes on a pallet. */
	private void pallet(int x, int y) {
		box(PALLET, OUTLINE, x, y, S - 6, S - 6);
		int[][] spots = {{3, 3}, {16, 3}, {3, 16}, {16, 16}};
		for (int[] s : spots) if (rng.nextDouble() < 0.85) box(PAINT[rng.nextInt(PAINT.length)][0], OUTLINE, x + s[0], y + s[1], 10, 10);
	}
	/** A fuel drum seen from above. */
	private void barrel(int x, int y) {
		Color c = BARRELS[rng.nextInt(BARRELS.length)];
		g.setColor(SHADOW);
		g.fillOval(x + 2, y + 3, 22, 22);
		g.setColor(c);
		g.fillOval(x, y, 22, 22);
		g.setColor(OUTLINE);
		g.setStroke(new BasicStroke(2));
		g.drawOval(x, y, 22, 22);
		g.setColor(c.darker());
		g.drawOval(x + 6, y + 6, 10, 10);
		g.setStroke(new BasicStroke(1));
		fill(new Color(30, 30, 34), x + 9, y + 9, 4, 4);
	}
	private void barrels() {
		int[] r = BARREL_STORE;
		for (int j = 0; j < r[3]; j++) {
			for (int i = 0; i < r[2]; i++) {
				if (j == 1 && i % 4 != 0) continue; // an aisle down the middle
				if (rng.nextDouble() < 0.8) barrel((r[0] + i) * S + 6, (r[1] + j) * S + 6);
			}
		}
	}

	// ---- FTL's own pieces, from the player's ftl.dat ----

	/** Two racks of weapons, laid out on end. */
	private void armory() {
		int[] r = ARMORY;
		for (int j = 0; j <= 2; j += 2) {
			box(RACK, OUTLINE, r[0] * S + 6, (r[1] + j) * S + 4, r[2] * S - 12, S - 2);
			for (int i = 0; i < r[2] - 1; i += 2) {
				String[] w = WEAPONS[rng.nextInt(WEAPONS.length)].split(":");
				BufferedImage strip = art(w[0]);
				if (strip == null) continue;
				int frames = Integer.parseInt(w[1]), fw = strip.getWidth() / frames;
				BufferedImage first = strip.getSubimage(0, 0, fw, strip.getHeight());
				int x = (r[0] + i) * S + 14 + rng.nextInt(21), cy = (r[1] + j) * S + 4 + (S - 2) / 2;
				AffineTransform t = new AffineTransform();
				t.translate(x + first.getHeight() / 2.0, cy);
				t.rotate(-Math.PI / 2); // on end, as in FTL's weapon bays
				t.translate(-fw / 2.0, -first.getHeight() / 2.0);
				g.drawImage(first, t, null);
			}
		}
	}
	/** Drones parked on gold-ringed pads. */
	private void droneBay() {
		int[] r = DRONES;
		for (int j = 0; j + 1 < r[3]; j += 2) {
			for (int i = 0; i + 1 < r[2]; i += 2) {
				int x = (r[0] + i) * S, y = (r[1] + j) * S;
				g.setColor(PAD_RING);
				g.drawOval(x + 6, y + 6, 58, 58);
				BufferedImage d = art("img/ship/drones/" + DRONE_ART[rng.nextInt(DRONE_ART.length)] + "_base.png");
				if (d != null) g.drawImage(d, x + 11, y + 11, 48, 48, null);
			}
		}
	}
	/** Hull pieces brought in from the Junkyard, kept inside their bay. */
	private void salvage() {
		int[] r = SALVAGE;
		java.util.List<String> pieces = salvagePieces();
		for (int k = 0; k < 10; k++) {
			BufferedImage gib = art(pieces.get(rng.nextInt(pieces.size())));
			if (gib == null) continue;
			double sc = 0.18 + rng.nextDouble() * 0.12, angle = rng.nextDouble() * Math.PI * 2;
			double w = gib.getWidth() * sc, h = gib.getHeight() * sc;
			double reach = Math.hypot(w, h) / 2; // her furthest corner from her centre, whichever way she lies
			double minX = r[0] * S + 6 + reach, maxX = (r[0] + r[2]) * S - 6 - reach;
			double minY = r[1] * S + 6 + reach, maxY = (r[1] + r[3]) * S - 6 - reach;
			double cx = maxX > minX ? minX + rng.nextDouble() * (maxX - minX) : (minX + maxX) / 2;
			double cy = maxY > minY ? minY + rng.nextDouble() * (maxY - minY) : (minY + maxY) / 2;
			AffineTransform t = new AffineTransform();
			t.translate(cx, cy);
			t.rotate(angle);
			t.scale(sc, sc);
			t.translate(-gib.getWidth() / 2.0, -gib.getHeight() / 2.0);
			g.drawImage(gib, t, null);
		}
	}
	/**
	 * The hull pieces the salvage bay may hold: those of every ship Commission would list (so the unlock rules apply, as
	 * they do to the Space Dock's traffic), or the Federation Cruiser's and the Kestrel's if none can be found.
	 */
	private static java.util.List<String> salvagePieces() {
		java.util.List<String> pieces = new java.util.ArrayList<String>();
		Set<String> seen = new HashSet<String>();
		for (String id : CommissionDialog.shownBlueprintIds()) {
			net.blerf.ftl.xml.ShipBlueprint bp = net.blerf.ftl.parser.DataManager.get().getShip(id);
			if (bp == null || !seen.add(bp.getGraphicsBaseName())) continue;
			for (int n = 1; n <= 6; n++) {
				String path = "img/ship/" + bp.getGraphicsBaseName() + "_gib" + n + ".png";
				if (net.blerf.ftl.parser.DataManager.get().hasResourceInputStream(path)) pieces.add(path);
			}
		}
		if (pieces.isEmpty()) for (String g : GIBS) pieces.add("img/ship/" + g + ".png");
		return pieces;
	}

	/** Teleporter pads, for moving goods and crew almost instantly. */
	private void pads() {
		BufferedImage pad = art("img/ship/interior/teleporter_off.png");
		if (pad == null) return;
		int[][] spots = {{1, 1}, {3, 1}, {1, 3}, {3, 3}};
		for (int[] s : spots) g.drawImage(pad, (PADS[0] + s[0]) * S + 6, (PADS[1] + s[1]) * S + 6, null);
	}
	/** One of FTL's wall fittings, in a room's top-left corner. */
	private void fitting(String name, int x, int y) {
		BufferedImage f = art("img/ship/interior/" + name + ".png");
		if (f != null) g.drawImage(f, x * S, y * S, null);
	}

	private BufferedImage art(String path) {
		return frame.getResourceImage(path, false);
	}
	private void fill(Color c, int x, int y, int w, int h) {
		g.setColor(c);
		g.fillRect(x, y, w, h);
	}
	private void box(Color fill, Color outline, int x, int y, int w, int h) {
		fill(fill, x, y, w, h);
		g.setColor(outline);
		g.drawRect(x, y, w - 1, h - 1);
		if (w > 6 && h > 6) g.drawRect(x + 1, y + 1, w - 3, h - 3);
	}
}
