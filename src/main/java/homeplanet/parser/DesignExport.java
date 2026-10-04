package homeplanet.parser;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.DefaultDataManager;
import net.blerf.ftl.xml.ShipBlueprint;

/**
 * Turns a finished Design Ship design into game files: the blueprint, the layout (.txt), the chassis (.xml) and, for
 * art of her own, the pictures (hull, floor, a cloak glow and the gibs). They go into the companion mod, and are
 * registered in the station so she can be drawn and commissioned before she's patched in.
 */
public class DesignExport {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DesignExport.class);
	private static final String CRLF = "\r\n";
	static final int SQ = 35;

	/** The design's id plus its version from 2 on (version 1 keeps the plain id, so older designs are unchanged). */
	public static String exportId(ShipDesign d) { return d.id + (d.version > 1 ? "_V" + d.version : ""); }
	public static String bpId(ShipDesign d) { return "PLAYER_SHIP_" + exportId(d) + Retrofit.SUFFIX; }
	public static String layoutId(ShipDesign d) { return exportId(d).toLowerCase() + "_hp"; }
	/** The picture name: the game's own when she uses game art, else hp_design_n. */
	public static String gfx(ShipDesign d) { return gamePictures(d) ? d.art.substring(5) : "hp_" + exportId(d).toLowerCase(); }
	/**
	 * Whether an edit changes anything the game is given for her (blueprint, layout, art, mounts, systems, loadout, name).
	 * Any such change can alter ships already flying the old blueprint (FTL may give them a newly added system), so it
	 * becomes a new version. The date made, version and built flags aren't part of it.
	 */
	public static boolean changesBlueprint(ShipDesign before, ShipDesign after) {
		return !gameKey(before).equals(gameKey(after));
	}
	private static String gameKey(ShipDesign d) {
		ShipDesign c = ShipDesign.copy(d);
		c.made = ""; c.version = 1; c.frozenOf = null; c.snapshotOf = null; c.built = true; c.starter = false; // none of these reach the game
		return ShipDesign.xmlOf(c);
	}
	static boolean gameArt(ShipDesign d) { return d.art.startsWith("game:"); }
	static boolean gameHas(String gfx, String suffix) { return DataManager.get().hasResourceInputStream("img/ship/" + gfx + suffix + ".png"); }
	/**
	 * Whether she can simply use the game's picture set by name. FTL finds the floor by the same name as the hull, so a
	 * game hull with no floor (or another floor) needs a picture set of her own, copied under the station's name.
	 */
	static boolean gamePictures(ShipDesign d) {
		if (!gameArt(d) || d.artScale != 100) return false; // resized: her own copies of the pictures
		String g = d.art.substring(5);
		if (!d.gameGibs() || !gameHas(g, "_gib1")) return false; // other gibs: FTL would find the game's by name
		if (d.floor.equals(d.art)) return true;
		return d.floor.isEmpty() && !gameHas(g, "_floor");
	}

	/** Why she can't be built yet (empty when she can): a design needs art and a mount on top of a sound layout. */
	public static List<String> problems(ShipDesign d) {
		List<String> out = d.problems();
		if (d.art.isEmpty()) out.add("She needs hull art.");
		return out;
	}

	static BufferedImage base(ShipDesign d) {
		BufferedImage b = ShipArt.load(d.art, gameArt(d) ? "_base" : "");
		if (b == null && !d.art.isEmpty()) { // her picture is gone (a vault moved without its art): the Kestrel's stands in, so her ships still load
			log.warn("The hull art of {} ({}) is missing: the Kestrel's stands in", d.name, d.art);
			b = ShipArt.load("game:kestral", "_base");
		}
		return ShipArt.scaled(b, d.artScale);
	}
	static BufferedImage floor(ShipDesign d) { return ShipArt.floorOf(d); }

	/** Rooms start at square (0, 0) in FTL's files, as the game's own ships do: how far hers are shifted to get there. */
	static int[] shift(ShipDesign d) {
		int mx = Integer.MAX_VALUE, my = Integer.MAX_VALUE;
		for (ShipDesign.Room r : d.rooms) { mx = Math.min(mx, r.x); my = Math.min(my, r.y); }
		return d.rooms.isEmpty() ? new int[] {0, 0} : new int[] {mx, my};
	}
	/** The picture's placement over the (shifted) rooms: FTL's img x, y. */
	static int[] imgXY(ShipDesign d) {
		int[] s = shift(d);
		return new int[] {d.artX - s[0] * SQ, d.artY - s[1] * SQ};
	}
	/**
	 * Where she sits on screen, in squares. Unless set by hand: chosen so the picture lands about where the game's own
	 * ships' pictures do (their img position plus the offset averages roughly -45, -40 pixels). Unverified; check in game.
	 */
	public static int[] offsets(ShipDesign d) {
		int[] xy = imgXY(d);
		int ox = d.offX >= 0 ? d.offX : Math.max(0, Math.min(6, Math.round((-45f - xy[0]) / SQ)));
		int oy = d.offY >= 0 ? d.offY : Math.max(0, Math.min(3, Math.round((-40f - xy[1]) / SQ)));
		return new int[] {ox, oy};
	}

	// ---- the layout (.txt) ----

	public static String layoutText(ShipDesign d) {
		int[] s = shift(d), off = offsets(d);
		BufferedImage b = base(d);
		int[] e = ShipArt.ellipseOf(d, b);
		if (e == null) e = new int[] {300, 200, 0, 0};
		StringBuilder sb = new StringBuilder();
		line(sb, "X_OFFSET"); line(sb, off[0]);
		line(sb, "Y_OFFSET"); line(sb, off[1]);
		line(sb, "VERTICAL"); line(sb, 0);
		line(sb, "ELLIPSE"); line(sb, e[0]); line(sb, e[1]); line(sb, e[2]); line(sb, e[3]);
		for (int i = 0; i < d.rooms.size(); i++) {
			ShipDesign.Room r = d.rooms.get(i);
			line(sb, "ROOM"); line(sb, i); line(sb, r.x - s[0]); line(sb, r.y - s[1]); line(sb, r.w); line(sb, r.h);
		}
		for (CompanionMod.Door x : d.doors) {
			line(sb, "DOOR"); line(sb, x.x - s[0]); line(sb, x.y - s[1]); line(sb, x.a); line(sb, x.b); line(sb, x.v);
		}
		return sb.toString();
	}
	private static void line(StringBuilder sb, Object v) { sb.append(v).append(CRLF); }

	// ---- the chassis (.xml) ----

	public static String chassisText(ShipDesign d) {
		BufferedImage b = base(d);
		int[] xy = imgXY(d);
		List<ShipArt.Gib> gibs = gibs(d, b);
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>").append(CRLF);
		sb.append("<!-- Designed at Federation Home Planet: ").append(XmlText.comment(d.name)).append(" -->").append(CRLF);
		sb.append("<FTL>").append(CRLF);
		sb.append("<img x=\"").append(xy[0]).append("\" y=\"").append(xy[1]).append("\" w=\"").append(b == null ? 0 : b.getWidth())
				.append("\" h=\"").append(b == null ? 0 : b.getHeight()).append("\"/>").append(CRLF);
		int[] cloak = cloakOffset(d);
		sb.append("<offsets>").append(CRLF);
		sb.append("\t<floor x=\"").append(d.floorX).append("\" y=\"").append(d.floorY).append("\"/>").append(CRLF);
		sb.append("\t<cloak x=\"").append(cloak[0]).append("\" y=\"").append(cloak[1]).append("\"/>").append(CRLF);
		sb.append("</offsets>").append(CRLF);
		sb.append("<weaponMounts>").append(CRLF);
		for (ShipDesign.Mount m : mountsInOrder(d)) {
			sb.append("\t<mount x=\"").append(m.x).append("\" y=\"").append(m.y).append("\" rotate=\"").append(m.rotate)
					.append("\" mirror=\"").append(m.mirror).append("\" gib=\"").append(gibs.isEmpty() ? 1 : ShipArt.gibAt(gibs, m.x, m.y))
					.append("\" slide=\"").append(m.slide).append("\"/>").append(CRLF);
		}
		sb.append("</weaponMounts>").append(CRLF);
		String gameExplosion = d.gameGibs() && d.artScale == 100 && gameHas(d.art.substring(5), "_gib1") ? gameChassisPart(d.art.substring(5), "explosion") : null;
		if (gameExplosion != null) sb.append(gameExplosion).append(CRLF);
		else sb.append(explosion(gibs, b));
		sb.append("</FTL>").append(CRLF);
		return sb.toString();
	}

	/**
	 * The explosion: each gib drifts away from the middle of the ship, spinning a little. FTL's direction is in degrees;
	 * 0 = right and 90 = down is assumed here (unverified; check in game).
	 */
	static String explosion(List<ShipArt.Gib> gibs, BufferedImage b) {
		StringBuilder sb = new StringBuilder("<explosion>" + CRLF);
		double cx = b == null ? 0 : b.getWidth() / 2.0, cy = b == null ? 0 : b.getHeight() / 2.0;
		for (int i = 0; i < gibs.size(); i++) {
			ShipArt.Gib g = gibs.get(i);
			double gx = g.x + g.img.getWidth() / 2.0 - cx, gy = g.y + g.img.getHeight() / 2.0 - cy;
			int dir = (int) Math.round(Math.toDegrees(Math.atan2(gy, gx))); // -180..180
			boolean spinLeft = (i % 2) == 0;
			String n = "gib" + (i + 1);
			sb.append("\t<").append(n).append(">").append(CRLF);
			sb.append("\t\t<velocity min=\"0.2\" max=\"0.8\"/>").append(CRLF);
			sb.append("\t\t<direction min=\"").append(dir - 30).append("\" max=\"").append(dir + 30).append("\"/>").append(CRLF);
			sb.append("\t\t<angular min=\"").append(spinLeft ? "-0.4" : "0").append("\" max=\"").append(spinLeft ? "0" : "0.4").append("\"/>").append(CRLF);
			sb.append("\t\t<x>").append(g.x).append("</x>").append(CRLF);
			sb.append("\t\t<y>").append(g.y).append("</y>").append(CRLF);
			sb.append("\t</").append(n).append(">").append(CRLF);
		}
		sb.append("</explosion>").append(CRLF);
		return sb.toString();
	}

	/** Her gibs: the game ship's own (game art, automatic), her gib pictures, or pieces cut from her art. */
	public static List<ShipArt.Gib> gibs(ShipDesign d, BufferedImage b) {
		if (d.gameGibs()) {
			List<ShipArt.Gib> out = new ArrayList<ShipArt.Gib>();
			String block = gameChassisPart(d.art.substring(5), "explosion");
			for (int i = 1; i <= 6; i++) {
				BufferedImage img = ShipArt.scaled(ShipArt.load(d.art, "_gib" + i), d.artScale);
				if (img == null) break;
				int x = 0, y = 0;
				if (block != null) {
					Matcher m = Pattern.compile("<gib" + i + ">[\\s\\S]*?<x>\\s*(-?\\d+)\\s*</x>[\\s\\S]*?<y>\\s*(-?\\d+)\\s*</y>").matcher(block);
					if (m.find()) { x = Integer.parseInt(m.group(1)); y = Integer.parseInt(m.group(2)); }
				}
				out.add(new ShipArt.Gib(img, x * d.artScale / 100, y * d.artScale / 100));
			}
			if (!out.isEmpty()) return out;
		}
		return ShipArt.gibsOf(d, b);
	}

	/** A block of the chassis file of a game ship that uses this picture (its "explosion" or "offsets"), or null. */
	static String gameChassisPart(String gfx, String tag) {
		DataManager dm = DataManager.get();
		if (!(dm instanceof DefaultDataManager)) return null;
		for (ShipBlueprint bp : dm.getShips().values()) {
			if (!gfx.equals(bp.getGraphicsBaseName())) continue;
			String text = ((DefaultDataManager) dm).gameText("data/" + bp.getLayoutId() + ".xml");
			if (text == null) continue;
			Matcher m = Pattern.compile("<" + tag + ">[\\s\\S]*?</" + tag + ">").matcher(text);
			if (m.find()) return m.group();
		}
		return null;
	}
	static int[] cloakOffset(ShipDesign d) {
		if (!gameArt(d) || !gameHas(d.art.substring(5), "_cloak")) return new int[] {0, 0}; // the station's own cloak picture is hull-sized
		String offs = gameChassisPart(d.art.substring(5), "offsets");
		if (offs != null) {
			Matcher m = Pattern.compile("<cloak\\s+x=\"(-?\\d+)\"\\s+y=\"(-?\\d+)\"").matcher(offs);
			// the game's offset is for the game's picture at full size: resized art, resized offset (the cloak picture is scaled with the hull)
			if (m.find()) return new int[] {Math.round(Integer.parseInt(m.group(1)) * d.artScale / 100f), Math.round(Integer.parseInt(m.group(2)) * d.artScale / 100f)};
		}
		return new int[] {0, 0};
	}

	// ---- the blueprint ----

	public static String blueprintText(ShipDesign d) {
		StringBuilder sb = new StringBuilder();
		sb.append("<shipBlueprint name=\"").append(bpId(d)).append("\" layout=\"").append(layoutId(d)).append("\" img=\"").append(gfx(d)).append("\">").append(CRLF);
		sb.append("\t<class>").append(XmlText.text(d.name)).append("</class>").append(CRLF);
		sb.append("\t<name>").append(XmlText.text(d.name)).append("</name>").append(CRLF);
		sb.append("\t<desc>Designed at the Federation Home Planet space dock.</desc>").append(CRLF);
		sb.append("\t<systemList>").append(CRLF);
		for (CompanionMod.Sys s : d.systems.values()) sb.append(CompanionMod.element(s, !d.notAtStart.contains(s.id)));
		sb.append("\t</systemList>").append(CRLF);
		sb.append("\t<weaponSlots>").append(weaponSlots(d)).append("</weaponSlots>").append(CRLF);
		sb.append("\t<droneSlots>").append(d.droneSlots).append("</droneSlots>").append(CRLF);
		sb.append("\t<weaponList count=\"0\" missiles=\"0\">").append(CRLF).append("\t</weaponList>").append(CRLF);
		sb.append("\t<droneList count=\"0\" drones=\"0\">").append(CRLF).append("\t</droneList>").append(CRLF);
		sb.append("\t<health amount=\"").append(d.hull).append("\"/>").append(CRLF);
		sb.append("\t<maxPower amount=\"").append(d.reactor).append("\"/>").append(CRLF);
		sb.append("\t<crewCount amount=\"1\" class=\"human\"/>").append(CRLF);
		sb.append("</shipBlueprint>").append(CRLF);
		String block = sb.toString();
		CompanionMod.Loadout l = d.loadout != null ? CompanionMod.copy(d.loadout) : new CompanionMod.Loadout();
		if (l.crew.isEmpty()) l.crew.put("human", 1);
		while (l.weapons.size() > weaponSlots(d)) l.weapons.remove(l.weapons.size() - 1);
		while (l.drones.size() > d.droneSlots) l.drones.remove(l.drones.size() - 1);
		return CompanionMod.applyLoadout(block, l);
	}
	/** One weapon slot per mount, up to FTL's four. */
	public static int weaponSlots(ShipDesign d) {
		int n = 0;
		for (ShipDesign.Mount m : d.mounts) if (!m.artillery) n++;
		return Math.max(1, Math.min(4, n));
	}
	/** The chassis' mounts: the weapon mounts, then the artillery's (as the Federation Cruiser has hers; unverified that FTL requires it). */
	static List<ShipDesign.Mount> mountsInOrder(ShipDesign d) {
		List<ShipDesign.Mount> out = new ArrayList<ShipDesign.Mount>();
		for (ShipDesign.Mount m : d.mounts) if (!m.artillery) out.add(m);
		for (ShipDesign.Mount m : d.mounts) if (m.artillery) out.add(m);
		return out;
	}

	// ---- pictures (her own art only) ----

	/** The pictures she brings: inner path -> PNG bytes. Game art needs none (the game has them). */
	public static Map<String, byte[]> images(ShipDesign d) throws java.io.IOException {
		Map<String, byte[]> out = new LinkedHashMap<String, byte[]>();
		if (gamePictures(d)) return out;
		BufferedImage b = base(d);
		if (b == null) return out;
		String g = "img/ship/" + gfx(d);
		out.put(g + "_base.png", png(b));
		BufferedImage f = floor(d);
		if (f != null) out.put(g + "_floor.png", png(f));
		BufferedImage gameCloak = gameArt(d) ? ShipArt.scaled(ShipArt.load(d.art, "_cloak"), d.artScale) : null;
		out.put(g + "_cloak.png", png(gameCloak != null ? gameCloak : cloak(b)));
		List<ShipArt.Gib> gibs = gibs(d, b);
		for (int i = 0; i < gibs.size(); i++) out.put(g + "_gib" + (i + 1) + ".png", png(gibs.get(i).img));
		return out;
	}
	/** A cloak picture: the hull as a pale blue glow (the game's own are hand-drawn; this is a stand-in). */
	static BufferedImage cloak(BufferedImage b) {
		BufferedImage out = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < b.getHeight(); y++) {
			for (int x = 0; x < b.getWidth(); x++) {
				int a = b.getRGB(x, y) >>> 24;
				if (a == 0) continue;
				out.setRGB(x, y, ((a * 140 / 255) << 24) | (150 << 16) | (210 << 8) | 255);
			}
		}
		return out;
	}
	static byte[] png(BufferedImage img) throws java.io.IOException {
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		ImageIO.write(img, "png", bo);
		return bo.toByteArray();
	}

	/** Everything the game would get for her, as text: for looking over before she's built (or by the curious). */
	public static String preview(ShipDesign d) {
		StringBuilder sb = new StringBuilder();
		sb.append("== data/").append(layoutId(d)).append(".txt (the layout: her rooms and doors)").append(CRLF).append(layoutText(d)).append(CRLF);
		sb.append("== data/").append(layoutId(d)).append(".xml (the chassis: her pictures, mounts and gibs)").append(CRLF).append(chassisText(d)).append(CRLF);
		sb.append("== the blueprint (in ").append(CompanionMod.FILES[0]).append(".append)").append(CRLF).append(blueprintText(d)).append(CRLF);
		sb.append("== pictures").append(CRLF);
		try {
			Map<String, byte[]> imgs = images(d);
			if (imgs.isEmpty()) sb.append("none of her own: the game's ").append(d.art.startsWith("game:") ? d.art.substring(5) : "").append(" pictures are used by name").append(CRLF);
			for (Map.Entry<String, byte[]> e : imgs.entrySet()) sb.append(e.getKey()).append("  (").append(e.getValue().length / 1024).append(" KB)").append(CRLF);
		} catch (Exception e) { sb.append("could not be made: ").append(e).append(CRLF); }
		return sb.toString();
	}

	/** Built designs, as the companion mod and the station use them. */
	public static List<ShipDesign> built() {
		List<ShipDesign> out = new ArrayList<ShipDesign>();
		// the built copies (and kept old versions), never the working copies: editing and saving a design changes nothing in the mod
		for (ShipDesign d : ShipDesign.load()) if (d.built && !d.isWorking() && problems(d).isEmpty()) out.add(d);
		return out;
	}
	/**
	 * Registers a design as she stands, in memory only, so a trial ship can be commissioned from her (the build screen's
	 * report and "will she load" test). The next real registration replaces it.
	 */
	public static void registerPreview(ShipDesign d) throws Exception {
		DataManager dm = DataManager.get();
		if (!(dm instanceof DefaultDataManager)) throw new IllegalStateException("no game data");
		((DefaultDataManager) dm).addExtraLayout(layoutId(d), layoutText(d), chassisText(d));
		for (Map.Entry<String, byte[]> e : images(d).entrySet()) ((DefaultDataManager) dm).addExtraResource(e.getKey(), e.getValue());
		((DefaultDataManager) dm).addExtraShipBlueprints(blueprintText(d), CompanionMod.FILES[0]);
	}

	/** Registers the built designs in the station's game data (layouts, pictures); their blueprints come with the mod's text. */
	public static void register(List<ShipDesign> designs) {
		DataManager dm = DataManager.get();
		if (!(dm instanceof DefaultDataManager)) return;
		for (ShipDesign d : designs) {
			try {
				((DefaultDataManager) dm).addExtraLayout(layoutId(d), layoutText(d), chassisText(d));
				for (Map.Entry<String, byte[]> e : images(d).entrySet()) ((DefaultDataManager) dm).addExtraResource(e.getKey(), e.getValue());
			} catch (Exception e) {
				org.slf4j.LoggerFactory.getLogger(DesignExport.class).error("Could not register the design " + d.id, e);
			}
		}
	}
}
