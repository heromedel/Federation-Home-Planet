package homeplanet.parser;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import net.blerf.ftl.model.shiplayout.ShipLayout;
import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.xml.ShipBlueprint;
import net.blerf.ftl.xml.ShipChassis;

/** Ship art for Design Ship: the game's pictures or the player's own, and gibs cut from a hull picture. */
public class ShipArt {
	/** Where copies of imported pictures are kept: the vault's art folder. */
	public static File dir() { return homeplanet.vault.Vault.get().artDir(); }
	/** The file a "file:" source names: a relative path is inside the vault. */
	public static File file(String source) {
		String p = source.startsWith("file:") ? source.substring(5) : source;
		File f = new File(p);
		return f.isAbsolute() ? f : new File(homeplanet.vault.Vault.get().shared, p); // the art is shared by both fleets
	}

	/** A piece of the hull for the explosion: its picture and where it sits on the hull art. */
	public static class Gib {
		public BufferedImage img;
		public int x, y;
		public Gib(BufferedImage img, int x, int y) { this.img = img; this.x = x; this.y = y; }
	}

	/** The picture a source names ("game:kestral" + "_base", or "file:art/DESIGN_1_base.png" in the vault), or null. */
	public static BufferedImage load(String source, String suffix) {
		if (source == null || source.isEmpty()) return null;
		try {
			if (source.startsWith("game:")) {
				InputStream in = DataManager.get().getResourceInputStream("img/ship/" + source.substring(5) + suffix + ".png");
				try { return ImageIO.read(in); } finally { in.close(); }
			}
			if (source.startsWith("file:")) return ImageIO.read(file(source));
		} catch (Exception e) {
			return null;
		}
		return null;
	}

	/** Whether a source names a picture that's there (cheaper than reading it; the checks use this). */
	public static boolean available(String source, String suffix) {
		if (source == null || source.isEmpty()) return false;
		if (source.startsWith("game:")) return DataManager.get().hasResourceInputStream("img/ship/" + source.substring(5) + suffix + ".png");
		if (source.startsWith("file:")) return file(source).isFile();
		return false;
	}

	/** A picture at a percentage of its size (smooth, so in-between sizes soften a little); the same picture at 100%. */
	public static BufferedImage scaled(BufferedImage img, int percent) {
		if (img == null || percent == 100 || percent <= 0) return img;
		int w = Math.max(1, Math.round(img.getWidth() * percent / 100f)), h = Math.max(1, Math.round(img.getHeight() * percent / 100f));
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = out.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(img, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	/**
	 * Copies a PNG into the vault's art folder and returns its source name. Every import gets a file of its own
	 * (<design>_<kind>_<n>.png), so a kept old version of a design, or an edit that's discarded, never has its
	 * picture changed under it; {@link #sweep} clears the ones nothing uses any more.
	 */
	public static String importFile(File png, String designId, String kind) throws java.io.IOException {
		BufferedImage img = ImageIO.read(png);
		if (img == null) throw new java.io.IOException(png.getName() + " isn't a picture " + homeplanet.core.HomePlanet.APP_NAME + " can read");
		File dir = dir();
		dir.mkdirs();
		String stem = homeplanet.core.SafeFiles.safeName(designId + "_" + kind);
		File out = null;
		for (int n = 1; out == null || out.exists(); n++) out = new File(dir, stem + "_" + n + ".png");
		ImageIO.write(img, "png", out);
		return "file:" + dir.getName() + "/" + out.getName();
	}

	/** Keeps a picture in the vault's art folder (a flipped copy, say), the way {@link #importFile} does. */
	public static String importImage(BufferedImage img, String designId, String kind) throws java.io.IOException {
		File dir = dir();
		dir.mkdirs();
		String stem = homeplanet.core.SafeFiles.safeName(designId + "_" + kind);
		File out = null;
		for (int n = 1; out == null || out.exists(); n++) out = new File(dir, stem + "_" + n + ".png");
		ImageIO.write(img, "png", out);
		return "file:" + dir.getName() + "/" + out.getName();
	}
	/** A picture upside down. */
	public static BufferedImage flipped(BufferedImage img) {
		BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = out.createGraphics();
		g.drawImage(img, 0, img.getHeight(), img.getWidth(), 0, 0, 0, img.getWidth(), img.getHeight(), null);
		g.dispose();
		return out;
	}
	/**
	 * Turns her pictures upside down (as copies of her own in the art folder), with the floor's offset, the mounts and
	 * her gibs. The rooms are {@link ShipDesign#flipVertically}'s job. False if she has no hull art.
	 */
	public static boolean flipVertically(ShipDesign d) throws java.io.IOException {
		BufferedImage base = load(d.art, d.art.startsWith("game:") ? "_base" : "");
		if (base == null) return false;
		int hs = scaled(base, d.artScale).getHeight(); // the mounts and offsets are at the art's current size
		BufferedImage floor = d.floorFromRooms() ? null : load(d.floor, d.floor.startsWith("game:") ? "_floor" : ""); // one drawn from the rooms follows them
		if ("files".equals(d.gibs)) {
			List<String> gibs = new ArrayList<String>();
			for (int i = 0; i < d.gibFiles.size(); i++) {
				BufferedImage g = load(d.gibFiles.get(i), "");
				if (g != null) gibs.add(importImage(flipped(g), d.id, "gib" + (i + 1)));
			}
			d.gibFiles.clear(); d.gibFiles.addAll(gibs);
			if (gibs.isEmpty()) d.gibs = "cut";
		} else d.gibs = "cut"; // the game ship's gibs are the right way up
		d.art = importImage(flipped(base), d.id, "base");
		if (floor != null) {
			int fh = scaled(floor, d.artScale).getHeight();
			d.floor = importImage(flipped(floor), d.id, "floor");
			d.floorY = hs - (d.floorY + fh);
		}
		for (ShipDesign.Mount m : d.mounts) {
			m.y = hs - m.y;
			if (m.rotate) m.mirror = !m.mirror; // forward-facing: top edge <-> bottom edge
			if ("up".equals(m.slide)) m.slide = "down"; else if ("down".equals(m.slide)) m.slide = "up";
		}
		return true;
	}

	/** A picture turned a quarter turn clockwise. */
	public static BufferedImage rotated(BufferedImage img) {
		BufferedImage out = new BufferedImage(img.getHeight(), img.getWidth(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) out.setRGB(img.getHeight() - 1 - y, x, img.getRGB(x, y));
		return out;
	}
	/** A picture mirrored left to right. */
	public static BufferedImage mirrored(BufferedImage img) {
		BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = out.createGraphics();
		g.drawImage(img, img.getWidth(), 0, 0, img.getHeight(), 0, 0, img.getWidth(), img.getHeight(), null);
		g.dispose();
		return out;
	}
	/**
	 * Turns her pictures a quarter turn clockwise (as copies of her own in the art folder): the hull, a floor picture, her
	 * gib pictures; the mounts and the shield ellipse turn with the picture so they keep their spots on it (a mount's
	 * facing is left as set). The rooms don't move: this is for a picture drawn facing the wrong way. The picture's middle
	 * stays where it is. False if she has no hull art.
	 */
	public static boolean rotate(ShipDesign d) throws java.io.IOException {
		BufferedImage base = load(d.art, d.art.startsWith("game:") ? "_base" : "");
		if (base == null) return false;
		BufferedImage shown = scaled(base, d.artScale);
		int ws = shown.getWidth(), hs = shown.getHeight();
		BufferedImage floor = d.floorFromRooms() ? null : load(d.floor, d.floor.startsWith("game:") ? "_floor" : "");
		turnGibs(d, true);
		d.art = importImage(rotated(base), d.id, "base");
		if (floor != null) {
			int fh = scaled(floor, d.artScale).getHeight();
			d.floor = importImage(rotated(floor), d.id, "floor");
			int fx = d.floorX, fy = d.floorY;
			d.floorX = hs - (fy + fh); d.floorY = fx;
		}
		for (ShipDesign.Mount m : d.mounts) { int x = m.x, y = m.y; m.x = hs - y; m.y = x; }
		int ew = d.ellipseW, eh = d.ellipseH, ex = d.ellipseX, ey = d.ellipseY;
		d.ellipseW = eh; d.ellipseH = ew; d.ellipseX = -ey; d.ellipseY = ex;
		double cx = d.artX + ws / 2.0, cy = d.artY + hs / 2.0; // the middle stays: the picture is now hs wide and ws tall
		d.artX = (int) Math.round(cx - hs / 2.0); d.artY = (int) Math.round(cy - ws / 2.0);
		return true;
	}
	/** Mirrors her pictures left to right (copies of her own), the mounts, the floor's offset and the shield with them; the rooms stay. False with no hull art. */
	public static boolean flipHorizontally(ShipDesign d) throws java.io.IOException {
		BufferedImage base = load(d.art, d.art.startsWith("game:") ? "_base" : "");
		if (base == null) return false;
		int ws = scaled(base, d.artScale).getWidth();
		BufferedImage floor = d.floorFromRooms() ? null : load(d.floor, d.floor.startsWith("game:") ? "_floor" : "");
		turnGibs(d, false);
		d.art = importImage(mirrored(base), d.id, "base");
		if (floor != null) {
			int fw = scaled(floor, d.artScale).getWidth();
			d.floor = importImage(mirrored(floor), d.id, "floor");
			d.floorX = ws - (d.floorX + fw);
		}
		for (ShipDesign.Mount m : d.mounts) m.x = ws - m.x;
		d.ellipseX = -d.ellipseX;
		return true;
	}
	/** Her gib pictures turned or mirrored with the hull; the game ship's own gibs no longer fit, so she's cut from the hull art instead. */
	private static void turnGibs(ShipDesign d, boolean rotate) throws java.io.IOException {
		if ("files".equals(d.gibs)) {
			List<String> gibs = new ArrayList<String>();
			for (int i = 0; i < d.gibFiles.size(); i++) {
				BufferedImage g = load(d.gibFiles.get(i), "");
				if (g != null) gibs.add(importImage(rotate ? rotated(g) : mirrored(g), d.id, "gib" + (i + 1)));
			}
			d.gibFiles.clear(); d.gibFiles.addAll(gibs);
			if (gibs.isEmpty()) d.gibs = "cut";
		} else d.gibs = "cut";
	}

	/** The pictures a design refers to, as source names. */
	public static List<String> filesOf(ShipDesign d) {
		List<String> out = new ArrayList<String>();
		if (d == null) return out;
		if (d.art.startsWith("file:")) out.add(d.art);
		if (d.floor.startsWith("file:")) out.add(d.floor);
		for (String g : d.gibFiles) if (g.startsWith("file:")) out.add(g);
		return out;
	}
	/**
	 * Deletes pictures in the art folder that no design (kept old versions included) and no remodel uses any more:
	 * imports that were discarded, or replaced. Returns how many went.
	 */
	public static int sweep() {
		File dir = dir();
		if (!dir.isDirectory()) return 0;
		if (!ShipDesign.intact() || !CompanionMod.intact()) return 0; // a damaged list can't say what's still used: delete nothing
		java.util.Set<File> used = new java.util.HashSet<File>();
		for (ShipDesign d : ShipDesign.load()) for (String f : filesOf(d)) used.add(file(f).getAbsoluteFile());
		for (CompanionMod.Remodel r : CompanionMod.load()) for (String f : filesOf(r.geometry)) used.add(file(f).getAbsoluteFile());
		int gone = 0;
		File[] files = dir.listFiles();
		if (files == null) return 0;
		for (File f : files) {
			if (!f.isFile() || !f.getName().toLowerCase().endsWith(".png") || used.contains(f.getAbsoluteFile())) continue;
			if (f.delete()) gone++;
		}
		return gone;
	}

	/** The names of the game's ship pictures (the gfx names ship blueprints use), sorted. */
	public static List<String> gameArt() {
		TreeSet<String> names = new TreeSet<String>();
		for (ShipBlueprint bp : DataManager.get().getShips().values()) if (bp.getGraphicsBaseName() != null) names.add(bp.getGraphicsBaseName());
		for (ShipBlueprint bp : DataManager.get().getAutoShips().values()) if (bp.getGraphicsBaseName() != null) names.add(bp.getGraphicsBaseName());
		List<String> out = new ArrayList<String>();
		for (String n : names) if (DataManager.get().hasResourceInputStream("img/ship/" + n + "_base.png")) out.add(n);
		return out;
	}

	/**
	 * Fills a design's art settings from a game ship that uses this picture: where the art sits over the rooms, the floor,
	 * the weapon mounts and the shield ellipse. The rooms themselves aren't touched.
	 */
	/** What a game ship has to offer with her art: {floor, mounts and shield, gibs}. */
	public static boolean[] gameExtras(String gfx) {
		boolean mounts = false;
		for (ShipBlueprint bp : DataManager.get().getShips().values()) if (gfx.equals(bp.getGraphicsBaseName())) { mounts = true; break; }
		return new boolean[] {DataManager.get().hasResourceInputStream("img/ship/" + gfx + "_floor.png"), mounts,
				DataManager.get().hasResourceInputStream("img/ship/" + gfx + "_gib1.png")};
	}
	/**
	 * Uses a game ship's art, taking what's asked for with it: her floor, her weapon mounts and shield ellipse, her gibs.
	 * What isn't taken is cleared (no floor, no mounts, the ellipse sized from the art, gibs cut from the art). The art's
	 * position is left to the caller.
	 */
	public static void useGameArt(ShipDesign d, String gfx, boolean floor, boolean mounts, boolean gibs) {
		d.art = "game:" + gfx;
		boolean[] has = gameExtras(gfx);
		d.floor = floor && has[0] ? "game:" + gfx : "";
		d.floorX = 0; d.floorY = 0;
		d.mounts.clear();
		d.ellipseW = d.ellipseH = d.ellipseX = d.ellipseY = 0;
		if (mounts || floor) {
			int ax = d.artX, ay = d.artY;
			ShipDesign tmp = ShipDesign.copy(d);
			if (adoptGameShip(tmp, gfx)) {
				if (floor) { d.floorX = tmp.floorX; d.floorY = tmp.floorY; }
				if (mounts) {
					for (ShipDesign.Mount m : tmp.mounts) d.mounts.add(m.copy());
					d.ellipseW = tmp.ellipseW; d.ellipseH = tmp.ellipseH; d.ellipseX = tmp.ellipseX; d.ellipseY = tmp.ellipseY;
				}
			}
			d.artX = ax; d.artY = ay;
		}
		d.gibs = gibs && has[2] ? "game" : "cut";
		d.gibFiles.clear();
	}
	public static boolean adoptGameShip(ShipDesign d, String gfx) {
		for (ShipBlueprint bp : DataManager.get().getShips().values()) {
			if (!gfx.equals(bp.getGraphicsBaseName())) continue;
			ShipChassis ch;
			ShipLayout lay;
			try { ch = DataManager.get().getShipChassis(bp.getLayoutId()); lay = DataManager.get().getShipLayout(bp.getLayoutId()); } catch (Exception e) { continue; }
			if (ch == null || ch.getImageBounds() == null) continue;
			d.art = "game:" + gfx;
			d.artX = ch.getImageBounds().x;
			d.artY = ch.getImageBounds().y;
			boolean hasFloor = DataManager.get().hasResourceInputStream("img/ship/" + gfx + "_floor.png");
			d.floor = hasFloor ? "game:" + gfx : "";
			if (ch.getOffsets() != null && ch.getOffsets().floorOffset != null) { d.floorX = ch.getOffsets().floorOffset.x; d.floorY = ch.getOffsets().floorOffset.y; }
			else { d.floorX = 0; d.floorY = 0; }
			mountsFrom(d, ch, bp);
			if (lay != null && lay.getShieldEllipse() != null) {
				java.awt.Rectangle e = lay.getShieldEllipse();
				d.ellipseW = e.width; d.ellipseH = e.height; d.ellipseX = e.x; d.ellipseY = e.y;
			}
			d.gibs = "game";
			d.gibFiles.clear();
			return true;
		}
		return false;
	}

	/**
	 * A game ship's mounts: her weapon mounts, then (if she has artillery, like the Federation Cruiser) the mount after them for
	 * each artillery gun. Other extra mounts in the game's files are test mounts and are left out.
	 */
	public static void mountsFrom(ShipDesign d, ShipChassis ch, ShipBlueprint bp) {
		d.mounts.clear();
		if (ch.getWeaponMountList() == null) return;
		ShipBlueprint.SystemList.SystemRoom[] art = bp == null || bp.getSystemList() == null ? null : bp.getSystemList().getSystemRoom(SystemType.ARTILLERY);
		int artillery = art == null || !d.systems.containsKey("artillery") ? 0 : art.length; // her gun's mount only if she has the gun
		int slots = bp != null && bp.getWeaponSlots() != null ? bp.getWeaponSlots() : 4;
		int weapons = artillery > 0 ? slots : Math.max(slots, 4);
		int i = 0;
		for (ShipChassis.WeaponMount m : ch.getWeaponMountList()) {
			if (i >= weapons + artillery) break;
			ShipDesign.Mount mt = new ShipDesign.Mount(m.x, m.y);
			mt.rotate = m.rotate; mt.mirror = m.mirror; mt.slide = m.slide == null ? "no" : m.slide;
			mt.artillery = i >= weapons;
			d.mounts.add(mt);
			i++;
		}
	}

	/** A shield ellipse from the art's size when none has been set (a little larger than the hull; unverified in game). */
	/** The box of a picture's visible pixels (alpha above a whisker), or the whole picture if it has none. */
	public static java.awt.Rectangle opaqueBounds(BufferedImage img) {
		int minX = img.getWidth(), minY = img.getHeight(), maxX = -1, maxY = -1;
		for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
			if (((img.getRGB(x, y) >>> 24) & 0xFF) < 16) continue;
			if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y;
		}
		if (maxX < 0) return new java.awt.Rectangle(0, 0, img.getWidth(), img.getHeight());
		return new java.awt.Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}
	/** FTL's floor colour: the grey walls its own floor pictures draw round each room. */
	private static final java.awt.Color FLOOR_WALL = new java.awt.Color(0x5e, 0x66, 0x6e);
	/**
	 * A floor picture drawn from her rooms, the size of her hull picture as shown (resized as the art is, since the rooms
	 * sit on it at that size; FTL wants the floor at the hull's corner): a grey wall round each room, open at the doors,
	 * the rooms themselves left clear for FTL to tile. At offset 0, 0.
	 */
	public static BufferedImage floorFromRooms(ShipDesign d, BufferedImage base) {
		BufferedImage s = scaled(base, d.artScale);
		return floorFromRooms(d, s.getWidth(), s.getHeight());
	}
	/** The same, for a hull picture of this size (the art's size as shown). */
	public static BufferedImage floorFromRooms(ShipDesign d, int w, int h) {
		BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = out.createGraphics();
		int sq = DesignExport.SQ, wall = 7;
		java.util.List<java.awt.Rectangle> rooms = new java.util.ArrayList<java.awt.Rectangle>();
		for (ShipDesign.Room r : d.rooms) rooms.add(new java.awt.Rectangle(r.x * sq - d.artX, r.y * sq - d.artY, r.w * sq, r.h * sq));
		g.setColor(FLOOR_WALL);
		for (java.awt.Rectangle r : rooms) g.fillRect(r.x - wall, r.y - wall, r.width + 2 * wall, r.height + 2 * wall);
		g.setComposite(java.awt.AlphaComposite.Clear);
		for (java.awt.Rectangle r : rooms) g.fillRect(r.x, r.y, r.width, r.height);
		// the doors: a gap through the wall where each one stands
		int gap = 14;
		for (CompanionMod.Door x : d.doors) {
			int cx = x.x * sq - d.artX, cy = x.y * sq - d.artY;
			if (x.v == 1) g.fillRect(cx - wall - 1, cy + sq / 2 - gap / 2, 2 * wall + 2, gap); // a door in a vertical wall
			else g.fillRect(cx + sq / 2 - gap / 2, cy - wall - 1, gap, 2 * wall + 2);
		}
		g.dispose();
		return out;
	}
	/**
	 * Her floor as shown and as sent to FTL (at the art's size), or null for none: a floor drawn from her rooms is drawn
	 * now, from the rooms and the hull as they are; a picture (hers or the game's) is read and resized with the art.
	 */
	public static BufferedImage floorOf(ShipDesign d) {
		if (d.floor.isEmpty()) return null;
		if (d.floorFromRooms()) {
			BufferedImage hull = load(d.art, d.art.startsWith("game:") ? "_base" : "");
			return hull == null ? null : floorFromRooms(d, hull);
		}
		return scaled(load(d.floor, d.floor.startsWith("game:") ? "_floor" : ""), d.artScale);
	}
	public static int[] ellipseOf(ShipDesign d, BufferedImage base) {
		if (d.ellipseW > 0 && d.ellipseH > 0) return new int[] {d.ellipseW, d.ellipseH, d.ellipseX, d.ellipseY};
		if (base == null) return null;
		return new int[] {base.getWidth() / 2 + 24, base.getHeight() / 2 + 12, 0, 0};
	}

	// ---- gibs ----

	/** The design's gibs: her own gib pictures (hull-sized, cropped here), or pieces cut from the hull art. */
	public static List<Gib> gibsOf(ShipDesign d, BufferedImage base) {
		if ("files".equals(d.gibs) && !d.gibFiles.isEmpty()) {
			List<Gib> out = new ArrayList<Gib>();
			for (String f : d.gibFiles) {
				BufferedImage img = scaled(load(f, ""), d.artScale);
				if (img != null) { Gib g = crop(img, 0, 0); if (g != null) out.add(g); }
			}
			if (!out.isEmpty()) return out;
		}
		return base == null ? new ArrayList<Gib>() : autoGibs(base, 5, d.id.hashCode());
	}

	/**
	 * Cuts the hull art into n pieces: each opaque pixel goes to its nearest seed (seeds spread over the hull), with a
	 * little noise so the breaks are ragged rather than ruler-straight. Each piece is cropped to its own box.
	 */
	public static List<Gib> autoGibs(BufferedImage base, int n, long seed) {
		int w = base.getWidth(), h = base.getHeight();
		Random rng = new Random(seed);
		// seeds: a jittered grid over the opaque area
		int minX = w, minY = h, maxX = -1, maxY = -1;
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if ((base.getRGB(x, y) >>> 24) > 16) {
			if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y;
		}
		if (maxX < 0) return new ArrayList<Gib>();
		int cols = n >= 4 ? (n + 1) / 2 : n, rows = n >= 4 ? 2 : 1;
		List<int[]> seeds = new ArrayList<int[]>();
		for (int i = 0; i < n; i++) {
			int c = i % cols, r = i / cols;
			int cx = minX + (int) ((c + 0.5 + (rng.nextDouble() - 0.5) * 0.5) * (maxX - minX) / cols);
			int cy = minY + (int) ((r + 0.5 + (rng.nextDouble() - 0.5) * 0.5) * (maxY - minY) / Math.max(1, rows));
			seeds.add(new int[] {cx, cy});
		}
		BufferedImage[] pieces = new BufferedImage[n];
		for (int i = 0; i < n; i++) pieces[i] = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		double noise = Math.max(w, h) / 45.0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = base.getRGB(x, y);
				if ((argb >>> 24) == 0) continue;
				// cheap smooth noise: a couple of sines, so neighbouring pixels break the same way
				double nx = x + noise * Math.sin(y / 41.0 + seed % 7) + noise * 0.4 * Math.sin(y / 17.0);
				double ny = y + noise * Math.sin(x / 37.0 + seed % 5) + noise * 0.4 * Math.sin(x / 15.0);
				int best = 0;
				double bd = Double.MAX_VALUE;
				for (int i = 0; i < n; i++) {
					double dx = nx - seeds.get(i)[0], dy = ny - seeds.get(i)[1];
					double dd = dx * dx + dy * dy;
					if (dd < bd) { bd = dd; best = i; }
				}
				pieces[best].setRGB(x, y, argb);
			}
		}
		List<Gib> out = new ArrayList<Gib>();
		for (BufferedImage p : pieces) {
			Gib g = crop(p, 0, 0);
			if (g != null) out.add(g);
		}
		return out;
	}

	/** Crops a hull-sized picture to its opaque box. */
	static Gib crop(BufferedImage img, int ox, int oy) {
		int minX = img.getWidth(), minY = img.getHeight(), maxX = -1, maxY = -1;
		for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) if ((img.getRGB(x, y) >>> 24) != 0) {
			if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y;
		}
		if (maxX < 0) return null;
		BufferedImage sub = new BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB);
		sub.getGraphics().drawImage(img, -minX, -minY, null);
		return new Gib(sub, ox + minX, oy + minY);
	}

	/** Which gib (1-based) holds this point of the hull art, or the nearest one. */
	public static int gibAt(List<Gib> gibs, int x, int y) {
		int best = 1;
		double bd = Double.MAX_VALUE;
		for (int i = 0; i < gibs.size(); i++) {
			Gib g = gibs.get(i);
			int lx = x - g.x, ly = y - g.y;
			if (lx >= 0 && ly >= 0 && lx < g.img.getWidth() && ly < g.img.getHeight() && (g.img.getRGB(lx, ly) >>> 24) != 0) return i + 1;
			double cx = g.x + g.img.getWidth() / 2.0, cy = g.y + g.img.getHeight() / 2.0;
			double dd = (cx - x) * (cx - x) + (cy - y) * (cy - y);
			if (dd < bd) { bd = dd; best = i + 1; }
		}
		return best;
	}
}
