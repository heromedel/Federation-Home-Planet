package homeplanet.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import homeplanet.resource.ResourceClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.DefaultDataManager;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.xml.ShipBlueprint;

/**
 * The companion mod: the .ftl Slipstream installs so FTL can load ships the station has retrofitted
 * (the plain _HP copies of every player ship, bundled in the jar) or remodeled (a copy per ship with
 * systems in other rooms, kept in remodels.xml in the vault). The station rebuilds the mod
 * whenever a remodel changes, and registers all of these blueprints in its own game data at startup.
 */
public class CompanionMod {
	private static final Logger log = LoggerFactory.getLogger(CompanionMod.class);

	public static final String TITLE = "Federation Home Planet Mod";
	public static final String AUTHOR = "Federation Home Planet";
	public static final String FILE = TITLE + ".ftl";
	public static final String DESCRIPTION = "Companion mod for Federation Home Planet. Lets FTL load ships the station has retrofitted "
			+ "(any system removable), remodeled (systems moved to other rooms) or designed from scratch. A ship that uses it needs "
			+ "this mod installed; use Undo Retrofit at the station to return her to the original model.";
	/** The blueprint files the player ships live in; a remodel goes into the same file as its original. */
	public static final String[] FILES = {"blueprints.xml", "dlcBlueprints.xml", "dlcBlueprintsOverwrite.xml"};
	/** The remodels file (remodels.xml) and the log retired remodels go to, both in the vault. */
	public static File remodelsFile() { return homeplanet.vault.Vault.get().remodelsFile(); }
	public static File removedLog() { return homeplanet.vault.Vault.get().removedBlueprintsLog(); }
	private static final String CRLF = "\r\n";

	/** One system's place on a remodeled ship, where it differs from the plain copy (or is added / left out). */
	public static class Sys {
		public String id;
		public int room = -1;       // -1: not on this ship (left out of the blueprint)
		public int power = 1;
		public String dir;          // station facing, or null
		public Integer square;      // station square (Medbay/Clone Bay: the square kept clear), or null
		public String weapon;       // artillery only: the weapon it fires
		public Sys(String id) { this.id = id; }
		public Sys copy() { Sys c = new Sys(id); c.room = room; c.power = power; c.dir = dir; c.square = square; c.weapon = weapon; return c; }
	}

	/** A door, as a layout file has it: wall square x,y, the rooms on either side (-1 = space: an airlock), v = 1 for a vertical wall. */
	public static class Door {
		public int x, y, a, b, v;
		public Door(int x, int y, int a, int b, int v) { this.x = x; this.y = y; this.a = a; this.b = b; this.v = v; }
		public boolean sameWall(Door o) { return o != null && x == o.x && y == o.y && v == o.v; }
		/** The same wall between the same rooms (whichever way round the rooms are listed). */
		public boolean equals(Object o) {
			if (!(o instanceof Door) || !sameWall((Door) o)) return false;
			Door x = (Door) o;
			return (a == x.a && b == x.b) || (a == x.b && b == x.a);
		}
		public int hashCode() { return (x * 131 + y) * 7 + v; }
	}

	/** The doors of a layout, in the layout's order. */
	public static List<Door> doorsOf(net.blerf.ftl.model.shiplayout.ShipLayout lay) {
		List<Door> out = new ArrayList<Door>();
		if (lay == null) return out;
		for (Map.Entry<net.blerf.ftl.model.shiplayout.DoorCoordinate, net.blerf.ftl.model.shiplayout.ShipLayoutDoor> e : lay.getDoorMap().entrySet())
			out.add(new Door(e.getKey().x, e.getKey().y, e.getValue().roomIdA, e.getValue().roomIdB, e.getKey().v));
		return out;
	}

	/** A remodeled blueprint: the plain _HP copy of a model plus the systems that moved (and, optionally, its own doors). */
	public static class Remodel {
		public String id;      // PLAYER_SHIP_X_R1_HP
		public String base;    // PLAYER_SHIP_X
		public String file;    // which blueprint file the original is in
		public String ship;    // the ship's name when it was made (a label)
		public String made;    // date (a label)
		/** A starter ship: she can be commissioned. */
		public boolean starter;
		/** Her own name, class and starting loadout, or null to keep the model's. */
		public Loadout loadout;
		/**
		 * Her overhauled shape (rooms, art, weapon mounts, shield), or null for the model's rooms. When set, her doors and
		 * systems are the geometry's, and her layout, chassis and pictures are written the way a designed ship's are.
		 */
		public ShipDesign geometry;
		public final Map<String, Sys> systems = new LinkedHashMap<String, Sys>();
		/** Her own doors, or null to keep the model's (then she uses the model's layout). */
		public List<Door> doors;
		/** A copy that shares nothing with this one (the cached list hands out copies, so an abandoned edit changes nothing). */
		public Remodel copy() {
			Remodel c = new Remodel();
			c.id = id; c.base = base; c.file = file; c.ship = ship; c.made = made; c.starter = starter;
			c.loadout = loadout == null ? null : CompanionMod.copy(loadout);
			c.geometry = geometry == null ? null : ShipDesign.copy(geometry);
			for (Sys s : systems.values()) c.systems.put(s.id, s.copy());
			if (doors != null) { c.doors = new ArrayList<Door>(); for (Door d : doors) c.doors.add(new Door(d.x, d.y, d.a, d.b, d.v)); }
			return c;
		}
	}

	/** A station blueprint's own class and default name, and what a new game (a commission) starts her with. */
	public static class Loadout {
		public String className = "", shipName = "";
		public final List<String> weapons = new ArrayList<String>(), drones = new ArrayList<String>(), augs = new ArrayList<String>();
		/** Starting crew: race (human, engi, mantis, rock, slug, energy, crystal, anaerobic) -> how many. */
		public final Map<String, Integer> crew = new LinkedHashMap<String, Integer>();
		public int missiles, droneParts;
		public int crewTotal() { int n = 0; for (int v : crew.values()) n += v; return n; }
	}

	/** Crew races a blueprint can start with (FTL's ids), in the game's usual order. */
	public static final String[] RACES = {"human", "engi", "mantis", "rock", "slug", "energy", "crystal", "anaerobic"};

	/** What this blueprint starts with now (her own loadout if she has one, else the blueprint's). */
	public static Loadout loadoutOf(String bpId) {
		Remodel r = find(load(), bpId);
		if (r != null && r.loadout != null) return copy(r.loadout);
		Loadout l = new Loadout();
		net.blerf.ftl.xml.ShipBlueprint bp = DataManager.get().getShip(bpId);
		if (bp == null) return l;
		l.className = textOf(bp.getShipClass());
		l.shipName = textOf(bp.getName());
		if (bp.getWeaponList() != null) {
			l.missiles = bp.getWeaponList().missiles;
			if (bp.getWeaponList().getWeaponIds() != null)
				for (net.blerf.ftl.xml.ShipBlueprint.WeaponList.WeaponId w : bp.getWeaponList().getWeaponIds()) l.weapons.add(w.name);
		}
		if (bp.getDroneList() != null) {
			l.droneParts = bp.getDroneList().drones;
			if (bp.getDroneList().getDroneIds() != null)
				for (net.blerf.ftl.xml.ShipBlueprint.DroneList.DroneId d : bp.getDroneList().getDroneIds()) l.drones.add(d.name);
		}
		if (bp.getAugments() != null) for (net.blerf.ftl.xml.ShipBlueprint.AugmentId a : bp.getAugments()) l.augs.add(a.name);
		for (Commission.CrewGroup g : Commission.crewOf(bpId)) {
			Integer had = l.crew.get(g.race);
			l.crew.put(g.race, (had == null ? 0 : had) + g.amount);
		}
		return l;
	}
	/** The ship's own weapons, drones and augments on top of her blueprint's name, crew and supplies. */
	public static Loadout loadoutFromShip(net.blerf.ftl.parser.SavedGameParser.ShipState ship, String bpId) {
		Loadout l = loadoutOf(bpId);
		l.weapons.clear(); l.drones.clear(); l.augs.clear();
		for (net.blerf.ftl.parser.SavedGameParser.WeaponState w : ship.getWeaponList()) l.weapons.add(w.getWeaponId());
		for (net.blerf.ftl.parser.SavedGameParser.DroneState d : ship.getDroneList()) l.drones.add(d.getDroneId());
		l.augs.addAll(ship.getAugmentIdList());
		return l;
	}
	public static Loadout copy(Loadout o) {
		Loadout l = new Loadout();
		l.className = o.className; l.shipName = o.shipName; l.missiles = o.missiles; l.droneParts = o.droneParts;
		l.weapons.addAll(o.weapons); l.drones.addAll(o.drones); l.augs.addAll(o.augs); l.crew.putAll(o.crew);
		return l;
	}
	private static String textOf(net.blerf.ftl.xml.DefaultDeferredText t) {
		try { String v = t == null ? null : t.getTextValue(); return v == null ? "" : v; } catch (Exception e) { return ""; }
	}

	/** The layout id the model's plain copy uses, e.g. circle_cruiser. */
	public static String origLayoutOf(Remodel r) {
		String block = blockOf(baseText(r.file), r.base + Retrofit.SUFFIX);
		if (block == null) return null;
		Matcher m = Pattern.compile("layout=\"([^\"]+)\"").matcher(block);
		return m.find() ? m.group(1) : null;
	}
	/** The layout id a remodel with her own doors uses (circle_cruiser_r1_hw), or the model's when she keeps its doors. */
	/** Whether she has a layout of her own (her own doors, or an overhauled shape) rather than the model's. */
	public static boolean ownLayout(Remodel r) { return r.doors != null || r.geometry != null; }
	public static String layoutIdOf(Remodel r) {
		String orig = origLayoutOf(r);
		if (!ownLayout(r) || orig == null) return orig;
		return orig + "_" + r.id.substring(r.base.length() + 1).toLowerCase();
	}

	/** The remodel's layout file: the model's rooms and offsets, with her own doors. */
	public static String layoutText(Remodel r) {
		if (r.geometry != null) return DesignExport.layoutText(r.geometry);
		String src = gameText("data/" + origLayoutOf(r) + ".txt");
		if (src == null) return null;
		String[] lines = src.split("\\r?\\n");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].trim().equals("DOOR")) { i += 5; continue; }
			if (lines[i].trim().length() == 0) continue;
			sb.append(lines[i].trim()).append(CRLF);
		}
		for (Door d : r.doors) sb.append("DOOR").append(CRLF).append(d.x).append(CRLF).append(d.y).append(CRLF)
				.append(d.a).append(CRLF).append(d.b).append(CRLF).append(d.v).append(CRLF);
		return sb.toString();
	}
	/** The remodel's chassis file: an exact copy of the model's (picture placement, weapon mounts). */
	public static String chassisText(Remodel r) {
		if (r.geometry != null) return DesignExport.chassisText(r.geometry);
		return gameText("data/" + origLayoutOf(r) + ".xml");
	}
	private static String gameText(String path) {
		DataManager dm = DataManager.get();
		return dm instanceof DefaultDataManager ? ((DefaultDataManager) dm).gameText(path) : null;
	}

	private static final Map<String, String> baseCache = new HashMap<String, String>();

	/** The bundled text of one blueprint file's plain _HP copies. */
	public static synchronized String baseText(String file) {
		String t = baseCache.get(file);
		if (t == null) {
			try {
				InputStream in = new ResourceClass().getClass().getResourceAsStream("mod/" + file + ".append");
				if (in == null) throw new IOException("missing resource mod/" + file + ".append");
				t = new String(homeplanet.core.SafeFiles.readAll(in), "UTF-8");
			} catch (IOException e) {
				log.error("Could not read the bundled companion mod text for " + file, e);
				t = "";
			}
			baseCache.put(file, t);
		}
		return t;
	}

	/** Which blueprint file a model's plain copy is in, or null if it isn't a player ship the station knows. */
	public static String fileOf(String vanillaId) {
		for (String f : FILES) if (baseText(f).contains("<shipBlueprint name=\"" + vanillaId + Retrofit.SUFFIX + "\"")) return f;
		return null;
	}

	/** The text of one shipBlueprint element (with its trailing line break) out of a blueprint file, or null. */
	static String blockOf(String text, String bpId) {
		Matcher m = Pattern.compile("[ \\t]*<shipBlueprint name=\"" + Pattern.quote(bpId) + "\"[\\s\\S]*?</shipBlueprint>[ \\t]*\\r?\\n?").matcher(text);
		return m.find() ? m.group() : null;
	}

	private static final String SYS_NAMES = "pilot|doors|sensors|medbay|oxygen|shields|engines|weapons|drones|teleporter|cloaking|artillery|battery|clonebay|mind|hacking";
	/** One system element inside a systemList, on its own line(s), with any trailing comment and the line break. */
	private static final Pattern SYS_ELEM = Pattern.compile("[ \\t]*<(" + SYS_NAMES + ")\\b[^>]*?(?:/>|>[\\s\\S]*?</\\1>)[^\\r\\n]*\\r?\\n?");

	/** Renders a remodel as blueprint text: the plain copy's block, renamed, with the moved systems rewritten. */
	public static String render(Remodel r) {
		String block = blockOf(baseText(r.file), r.base + Retrofit.SUFFIX);
		if (block == null) return "";
		block = block.replaceFirst("name=\"" + Pattern.quote(r.base + Retrofit.SUFFIX) + "\"", "name=\"" + r.id + "\"");
		if (ownLayout(r)) {
			String orig = origLayoutOf(r);
			block = block.replaceFirst("layout=\"" + Pattern.quote(orig) + "\"", "layout=\"" + layoutIdOf(r) + "\"");
		}
		int a = block.indexOf("<systemList>"), b = block.indexOf("</systemList>");
		if (a < 0 || b < 0) return block;
		if (r.geometry != null) {
			// an overhauled ship: her own pictures' name, and every system where the new rooms have it
			block = block.replaceFirst("img=\"[^\"]*\"", "img=\"" + DesignExport.gfx(r.geometry) + "\"");
			a = block.indexOf("<systemList>"); b = block.indexOf("</systemList>");
			StringBuilder all = new StringBuilder(CRLF);
			for (Sys s : r.geometry.systems.values()) all.append(element(s, false));
			block = block.substring(0, a + "<systemList>".length()) + all + "\t" + block.substring(b);
			return r.loadout == null ? block : applyLoadout(block, r.loadout);
		}
		String list = block.substring(a + "<systemList>".length(), b);
		StringBuilder out = new StringBuilder();
		Matcher m = SYS_ELEM.matcher(list);
		int last = 0;
		java.util.Set<String> done = new java.util.HashSet<String>();
		while (m.find()) {
			out.append(list, last, m.start());
			last = m.end();
			String id = m.group(1);
			Sys s = r.systems.get(id);
			if (s == null) { out.append(m.group()); continue; } // untouched: verbatim
			done.add(id);
			if (s.room >= 0) out.append(element(s, false));
		}
		out.append(list, last, list.length());
		// systems the model never had, added by the remodel: before the closing tag
		for (Sys s : r.systems.values()) {
			if (done.contains(s.id) || s.room < 0) continue;
			out.append(element(s, false));
		}
		block = block.substring(0, a + "<systemList>".length()) + out + block.substring(b);
		return r.loadout == null ? block : applyLoadout(block, r.loadout);
	}

	/** Writes her own class, name, weapons, drones, augments and crew into the blueprint text. */
	static String applyLoadout(String block, Loadout l) {
		if (l.className.trim().length() > 0)
			block = block.replaceFirst("<class\\b[^>]*?(/>|>[^<]*</class>)", Matcher.quoteReplacement("<class>" + XmlText.text(l.className) + "</class>"));
		if (l.shipName.trim().length() > 0)
			block = block.replaceFirst("<name\\b[^>]*?(/>|>[^<]*</name>)", Matcher.quoteReplacement("<name>" + XmlText.text(l.shipName) + "</name>"));
		StringBuilder w = new StringBuilder();
		w.append("<weaponList count=\"").append(l.weapons.size()).append("\" missiles=\"").append(l.missiles).append("\">").append(CRLF);
		for (String id : l.weapons) w.append("\t\t<weapon name=\"").append(id).append("\"/>").append(CRLF);
		w.append("\t</weaponList>");
		block = replaceOrAdd(block, "<weaponList[\\s\\S]*?</weaponList>|<weaponList[^>]*/>", w.toString());
		StringBuilder d = new StringBuilder();
		d.append("<droneList count=\"").append(l.drones.size()).append("\" drones=\"").append(l.droneParts).append("\">").append(CRLF);
		for (String id : l.drones) d.append("\t\t<drone name=\"").append(id).append("\"/>").append(CRLF);
		d.append("\t</droneList>");
		block = replaceOrAdd(block, "<droneList[\\s\\S]*?</droneList>|<droneList[^>]*/>", d.toString());
		block = block.replaceAll("[ \\t]*<aug\\s+name\\s*=\\s*\"[^\"]*\"\\s*/>[^\\r\\n]*\\r?\\n?", "");
		block = block.replaceAll("[ \\t]*<crewCount\\b[^>]*/>[^\\r\\n]*\\r?\\n?", "");
		StringBuilder tail = new StringBuilder();
		for (String id : l.augs) tail.append("\t<aug name=\"").append(id).append("\"/>").append(CRLF);
		for (Map.Entry<String, Integer> c : l.crew.entrySet()) {
			if (c.getValue() > 0) tail.append("\t<crewCount amount=\"").append(c.getValue()).append("\" class=\"").append(c.getKey()).append("\"/>").append(CRLF);
		}
		int end = block.lastIndexOf("</shipBlueprint>");
		return block.substring(0, end) + tail + block.substring(end);
	}
	private static String replaceOrAdd(String block, String regex, String element) {
		Matcher m = Pattern.compile(regex).matcher(block);
		if (m.find()) return block.substring(0, m.start()) + element + block.substring(m.end());
		int end = block.lastIndexOf("</shipBlueprint>");
		return block.substring(0, end) + "\t" + element + CRLF + block.substring(end);
	}
	/**
	 * A system element in the vanilla files' style (tabs, CRLF), with its start flag and station. Moved systems drop the
	 * room picture: FTL manages without one. Remodels write start="false" for every system: that is what lets one be
	 * taken off (the ship's save says what she really has); designs write what the designer chose.
	 */
	public static String element(Sys s, boolean start) {
		StringBuilder sb = new StringBuilder();
		sb.append("\t\t<").append(s.id).append(" power=\"").append(Math.max(1, s.power)).append("\" room=\"").append(s.room).append("\" start=\"").append(start).append("\"");
		if (s.weapon != null) sb.append(" weapon=\"").append(s.weapon).append("\"");
		if (s.square == null) return sb.append("/>").append(CRLF).toString();
		sb.append(">").append(CRLF).append("\t\t\t<slot>").append(CRLF);
		if (s.dir != null) sb.append("\t\t\t\t<direction>").append(s.dir).append("</direction>").append(CRLF);
		sb.append("\t\t\t\t<number>").append(s.square).append("</number>").append(CRLF);
		sb.append("\t\t\t</slot>").append(CRLF).append("\t\t</").append(s.id).append(">").append(CRLF);
		return sb.toString();
	}

	/** The full text of one blueprint file for the mod: the plain copies plus every remodel that belongs there. */
	public static String fullText(String file, List<Remodel> remodels) {
		StringBuilder sb = new StringBuilder(baseText(file));
		for (Remodel r : remodels) {
			if (!file.equals(r.file)) continue;
			sb.append(CRLF).append("<!-- Remodeled for ").append(XmlText.comment(r.ship)).append(", ").append(r.made).append(" -->").append(CRLF);
			sb.append(render(r));
		}
		if ("blueprints.xml".equals(file)) {
			for (ShipDesign d : DesignExport.built()) {
				sb.append(CRLF).append("<!-- Designed at Federation Home Planet: ").append(XmlText.comment(d.name)).append(", ").append(d.made).append(" -->").append(CRLF);
				sb.append(DesignExport.blueprintText(d));
			}
		}
		return sb.toString();
	}

	/** Writes the mod as a Slipstream .ftl (a zip). */
	public static void build(File out, List<Remodel> remodels, String version) throws IOException {
		ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out));
		try {
			for (String f : FILES) {
				z.putNextEntry(new ZipEntry("data/" + f + ".append"));
				z.write(fullText(f, remodels).getBytes("UTF-8"));
				z.closeEntry();
			}
			for (Remodel r : remodels) {
				if (!ownLayout(r)) continue;
				if (r.geometry != null) {
					for (Map.Entry<String, byte[]> e : DesignExport.images(r.geometry).entrySet()) {
						z.putNextEntry(new ZipEntry(e.getKey()));
						z.write(e.getValue());
						z.closeEntry();
					}
				}
				String txt = layoutText(r), xml = chassisText(r);
				if (txt == null || xml == null) throw new IOException("Could not read the layout files of " + origLayoutOf(r) + " from the game data");
				z.putNextEntry(new ZipEntry("data/" + layoutIdOf(r) + ".txt"));
				z.write(txt.getBytes("UTF-8"));
				z.closeEntry();
				z.putNextEntry(new ZipEntry("data/" + layoutIdOf(r) + ".xml"));
				z.write(xml.getBytes("UTF-8"));
				z.closeEntry();
			}
			for (ShipDesign d : DesignExport.built()) {
				z.putNextEntry(new ZipEntry("data/" + DesignExport.layoutId(d) + ".txt"));
				z.write(DesignExport.layoutText(d).getBytes("UTF-8"));
				z.closeEntry();
				z.putNextEntry(new ZipEntry("data/" + DesignExport.layoutId(d) + ".xml"));
				z.write(DesignExport.chassisText(d).getBytes("UTF-8"));
				z.closeEntry();
				for (Map.Entry<String, byte[]> e : DesignExport.images(d).entrySet()) {
					z.putNextEntry(new ZipEntry(e.getKey()));
					z.write(e.getValue());
					z.closeEntry();
				}
			}
			z.putNextEntry(new ZipEntry("mod-appendix/metadata.xml"));
			String meta = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + CRLF + "<metadata>" + CRLF
					+ "\t<title><![CDATA[" + TITLE + "]]></title>" + CRLF
					+ "\t<author><![CDATA[" + AUTHOR + "]]></author>" + CRLF
					+ "\t<version><![CDATA[" + version + "]]></version>" + CRLF
					+ "\t<description><![CDATA[" + DESCRIPTION + "]]></description>" + CRLF
					+ "</metadata>" + CRLF;
			z.write(meta.getBytes("UTF-8"));
			z.closeEntry();
		} finally {
			z.close();
		}
		log.debug("Built {} with {} remodel(s)", out, remodels.size());
	}

	/** Registers the plain copies and all remodels in the station's game data, so their ships can be read and drawn. */
	public static void register(List<Remodel> remodels) {
		DataManager dm = DataManager.get();
		if (!(dm instanceof DefaultDataManager)) return;
		DesignExport.register(DesignExport.built()); // designed ships' layouts and pictures (their blueprints come with the text below)
		for (Remodel r : remodels) {
			if (!ownLayout(r)) continue;
			try {
				String txt = layoutText(r), xml = chassisText(r);
				if (txt != null && xml != null) ((DefaultDataManager) dm).addExtraLayout(layoutIdOf(r), txt, xml);
				if (r.geometry != null)
					for (Map.Entry<String, byte[]> e : DesignExport.images(r.geometry).entrySet()) ((DefaultDataManager) dm).addExtraResource(e.getKey(), e.getValue());
			} catch (Exception e) {
				log.error("Could not register the layout of " + r.id, e);
			}
		}
		for (String f : FILES) {
			try {
				((DefaultDataManager) dm).addExtraShipBlueprints(fullText(f, remodels), f);
			} catch (Exception e) {
				log.error("Could not register the station's blueprints from " + f, e);
			}
		}
	}

	/** True if the game data (ftl.dat as patched) has this blueprint, not just the station. */
	public static boolean inGameData(String id) {
		DataManager dm = DataManager.get();
		if (!(dm instanceof DefaultDataManager)) return false;
		DefaultDataManager ddm = (DefaultDataManager) dm;
		if (!ddm.shipInGameData(id)) return false;
		ShipBlueprint game = ddm.gameShip(id), ours = dm.getShip(id);
		if (game == null || ours == null || game == ours) return game != null;
		// the station's version is newer than the last patch if the systems, the layout name or the doors differ
		if (!game.getLayoutId().equals(ours.getLayoutId())) return false;
		if (!sameLayout(layoutOf(game), layoutOf(ours))) return false;
		net.blerf.ftl.model.shiplayout.ShipLayout gl = ddm.gameLayout(ours.getLayoutId());
		if (gl == null) return false;
		net.blerf.ftl.model.shiplayout.ShipLayout ol = dm.getShipLayout(ours.getLayoutId());
		if (gl.getRoomCount() != ol.getRoomCount()) return false;
		for (int i = 0; i < gl.getRoomCount(); i++) {
			net.blerf.ftl.model.shiplayout.ShipLayoutRoom x = gl.getRoom(i), y = ol.getRoom(i);
			if (x.locationX != y.locationX || x.locationY != y.locationY || x.squaresH != y.squaresH || x.squaresV != y.squaresV) return false;
		}
		return new java.util.HashSet<Door>(doorsOf(gl)).equals(new java.util.HashSet<Door>(doorsOf(dm.getShipLayout(ours.getLayoutId()))));
	}
	static boolean sameLayout(Map<String, Sys> a, Map<String, Sys> b) {
		if (!a.keySet().equals(b.keySet())) return false;
		for (Sys s : a.values()) {
			Sys t = b.get(s.id);
			if (s.room != t.room || !eq(s.dir, t.dir) || !eq(s.square, t.square)) return false;
		}
		return true;
	}
	private static boolean eq(Object x, Object y) { return x == null ? y == null : x.equals(y); }

	// ---- remodels.xml ----

	// The parsed file, kept until it changes on disk (or is saved): load() is called from every screen that needs a blueprint
	private static List<Remodel> cache;
	private static long cacheStamp, cacheSize;

	/** The remodels on file, as fresh copies: change them and call {@link #save} to keep the change. */
	public static synchronized List<Remodel> load() {
		File f = remodelsFile();
		if (cache != null && f.lastModified() == cacheStamp && f.length() == cacheSize) {
			List<Remodel> out = new ArrayList<Remodel>();
			for (Remodel r : cache) out.add(r.copy());
			return out;
		}
		List<Remodel> parsed = parse(f);
		cache = parsed;
		cacheStamp = f.lastModified();
		cacheSize = f.length();
		List<Remodel> out = new ArrayList<Remodel>();
		for (Remodel r : parsed) out.add(r.copy());
		return out;
	}
	private static List<Remodel> parse(File REMODELS) {
		List<Remodel> out = new ArrayList<Remodel>();
		if (!REMODELS.isFile()) { BlueprintBackup.restoreRemodels(out); return out; }
		try {
			readInto(REMODELS, out);
		} catch (Exception e) {
			log.error("Could not read " + REMODELS, e);
		}
		BlueprintBackup.restoreRemodels(out); // any a ship needs that the file lacks (damaged, or lost)
		return out;
	}
	/** True if remodels.xml is missing (nothing to lose) or reads in full. A damaged file must never be written over. */
	public static boolean intact() {
		File f = remodelsFile();
		if (!f.isFile()) return true;
		try {
			readInto(f, new ArrayList<Remodel>());
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	static void readInto(File REMODELS, List<Remodel> out) throws Exception {
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(REMODELS);
		NodeList rs = doc.getElementsByTagName("remodel");
		for (int i = 0; i < rs.getLength(); i++) {
			Element e = (Element) rs.item(i);
			Remodel r = new Remodel();
			r.id = e.getAttribute("id");
			r.base = e.getAttribute("base");
			r.file = e.getAttribute("file");
			r.ship = e.getAttribute("ship");
			r.made = e.getAttribute("made");
			r.starter = "true".equals(e.getAttribute("starter"));
			for (Element se : children(e, "sys")) {
				Sys s = new Sys(se.getAttribute("id"));
				s.room = Integer.parseInt(se.getAttribute("room"));
				if (se.hasAttribute("power")) s.power = Integer.parseInt(se.getAttribute("power"));
				if (se.hasAttribute("dir")) s.dir = se.getAttribute("dir");
				if (se.hasAttribute("square")) s.square = Integer.valueOf(se.getAttribute("square"));
				if (se.hasAttribute("weapon")) s.weapon = se.getAttribute("weapon");
				r.systems.put(s.id, s);
			}
			List<Element> doorsEl = children(e, "doors");
			if (!doorsEl.isEmpty()) {
				r.doors = new ArrayList<Door>();
				for (Element de : children(doorsEl.get(0), "door")) {
					r.doors.add(new Door(Integer.parseInt(de.getAttribute("x")), Integer.parseInt(de.getAttribute("y")),
							Integer.parseInt(de.getAttribute("a")), Integer.parseInt(de.getAttribute("b")), Integer.parseInt(de.getAttribute("v"))));
				}
			}
			r.loadout = readLoadout(e);
			List<Element> geo = children(e, "design");
			if (!geo.isEmpty()) r.geometry = ShipDesign.parse(geo.get(0));
			if (r.id.length() > 0 && r.file.length() > 0) out.add(r);
		}
	}

	public static synchronized void save(List<Remodel> remodels) throws IOException {
		cache = null; // whatever happens below, the next load() reads the file
		if (!intact()) throw ShipDesign.damaged(remodelsFile()); // the list in hand may be short: writing it would lose remodels
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append(CRLF);
		sb.append("<!-- Ship blueprints remodeled with Federation Home Planet, which rebuilds the companion mod from this file. -->").append(CRLF);
		sb.append("<remodels>").append(CRLF);
		for (Remodel r : remodels) sb.append(remodelXml(r));
		sb.append("</remodels>").append(CRLF);
		homeplanet.core.SafeFiles.writeText(remodelsFile(), sb.toString(), true);
		BlueprintBackup.keepRemodels(remodels);
	}
	static String remodelXml(Remodel r) {
		StringBuilder sb = new StringBuilder();
		sb.append("\t<remodel id=\"").append(r.id).append("\" base=\"").append(r.base).append("\" file=\"").append(r.file)
				.append("\" ship=\"").append(attr(r.ship)).append("\" made=\"").append(r.made).append(r.starter ? "\" starter=\"true" : "").append("\">").append(CRLF);
		for (Sys s : r.systems.values()) {
			sb.append("\t\t<sys id=\"").append(s.id).append("\" room=\"").append(s.room).append("\" power=\"").append(s.power).append("\"");
			if (s.weapon != null) sb.append(" weapon=\"").append(s.weapon).append("\"");
			if (s.dir != null) sb.append(" dir=\"").append(s.dir).append("\"");
			if (s.square != null) sb.append(" square=\"").append(s.square).append("\"");
			sb.append("/>").append(CRLF);
		}
		if (r.doors != null) {
			sb.append("\t\t<doors>").append(CRLF);
			for (Door d : r.doors) sb.append("\t\t\t<door x=\"").append(d.x).append("\" y=\"").append(d.y).append("\" a=\"").append(d.a)
					.append("\" b=\"").append(d.b).append("\" v=\"").append(d.v).append("\"/>").append(CRLF);
			sb.append("\t\t</doors>").append(CRLF);
		}
		if (r.loadout != null) sb.append(loadoutXml(r.loadout, "\t\t"));
		if (r.geometry != null) sb.append(ShipDesign.xmlOf(r.geometry).replaceAll("(?m)^", "\t"));
		sb.append("\t</remodel>").append(CRLF);
		return sb.toString();
	}
	/** A loadout element (her class, name and new-game loadout) inside a remodel or a design, or null. */
	/** The element's own children with this tag (not their children's). */
	static List<Element> children(Element e, String tag) {
		List<Element> out = new ArrayList<Element>();
		for (org.w3c.dom.Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n instanceof Element && tag.equals(((Element) n).getTagName())) out.add((Element) n);
		}
		return out;
	}
	static Loadout readLoadout(Element e) {
		List<Element> ls = children(e, "loadout");
		if (ls.isEmpty()) return null;
		Element le = ls.get(0);
		Loadout l = new Loadout();
		l.className = le.getAttribute("class");
		l.shipName = le.getAttribute("name");
		try { l.missiles = Integer.parseInt(le.getAttribute("missiles")); } catch (Exception x) { }
		try { l.droneParts = Integer.parseInt(le.getAttribute("droneParts")); } catch (Exception x) { }
		NodeList items = le.getChildNodes();
		for (int j = 0; j < items.getLength(); j++) {
			if (!(items.item(j) instanceof Element)) continue;
			Element it = (Element) items.item(j);
			String tag = it.getTagName(), id = it.getAttribute("id");
			if ("weapon".equals(tag)) l.weapons.add(id);
			else if ("drone".equals(tag)) l.drones.add(id);
			else if ("aug".equals(tag)) l.augs.add(id);
			else if ("crew".equals(tag)) {
				try { l.crew.put(it.getAttribute("race"), Integer.parseInt(it.getAttribute("n"))); } catch (Exception x) { }
			}
		}
		return l;
	}
	static String loadoutXml(Loadout l, String indent) {
		StringBuilder sb = new StringBuilder();
		sb.append(indent).append("<loadout class=\"").append(attr(l.className)).append("\" name=\"").append(attr(l.shipName))
				.append("\" missiles=\"").append(l.missiles).append("\" droneParts=\"").append(l.droneParts).append("\">").append(CRLF);
		for (String id : l.weapons) sb.append(indent).append("\t<weapon id=\"").append(attr(id)).append("\"/>").append(CRLF);
		for (String id : l.drones) sb.append(indent).append("\t<drone id=\"").append(attr(id)).append("\"/>").append(CRLF);
		for (String id : l.augs) sb.append(indent).append("\t<aug id=\"").append(attr(id)).append("\"/>").append(CRLF);
		for (Map.Entry<String, Integer> c : l.crew.entrySet())
			sb.append(indent).append("\t<crew race=\"").append(attr(c.getKey())).append("\" n=\"").append(c.getValue()).append("\"/>").append(CRLF);
		sb.append(indent).append("</loadout>").append(CRLF);
		return sb.toString();
	}
	static String attr(String s) { return XmlText.attr(s); }

	/** Moves a remodel out of the file into Removed Blueprints.log (it can be pasted back by hand). */
	public static void retire(Remodel r) throws IOException { logRemoved(remodelXml(r)); }
	/** A retired design's built copy, into the removed-blueprints log the same way. */
	public static void retire(ShipDesign d) throws IOException { logRemoved(ShipDesign.xmlOf(d)); }
	private static void logRemoved(String xml) throws IOException {
		java.io.Writer w = new java.io.OutputStreamWriter(new FileOutputStream(removedLog(), true), "UTF-8");
		try {
			w.write("<!-- removed " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()) + " -->" + CRLF);
			w.write(xml);
		} finally { w.close(); }
	}

	public static Remodel find(List<Remodel> remodels, String id) {
		for (Remodel r : remodels) if (r.id.equals(id)) return r;
		return null;
	}

	private static final Pattern REMODEL_ID = Pattern.compile("^(PLAYER_SHIP_[A-Z0-9_]+?)_R(\\d+)" + Retrofit.SUFFIX + "$");
	public static boolean isRemodelId(String id) { return id != null && REMODEL_ID.matcher(id).matches(); }
	/** The original model of a remodel id (or of any of the station's ids), e.g. PLAYER_SHIP_ANAEROBIC. */
	public static String baseOf(String id) {
		Matcher m = REMODEL_ID.matcher(id);
		return m.matches() ? m.group(1) : Retrofit.vanillaId(id);
	}
	/** A short label: "Lanius Cruiser A #1" style is the caller's job; this gives "#1". */
	public static String numberOf(String id) {
		Matcher m = REMODEL_ID.matcher(id);
		return m.matches() ? "#" + m.group(2) : "";
	}

	/** The next unused remodel id for a model. */
	public static String nextId(String vanillaId, List<Remodel> remodels) {
		for (int n = 1; ; n++) {
			String id = vanillaId + "_R" + n + Retrofit.SUFFIX;
			if (find(remodels, id) == null) return id;
		}
	}

	/** A new, empty remodel of a model (no system moved yet). */
	public static Remodel create(String vanillaId, String shipName, List<Remodel> remodels) {
		Remodel r = new Remodel();
		r.base = vanillaId;
		r.file = fileOf(vanillaId);
		r.id = nextId(vanillaId, remodels);
		r.ship = shipName;
		r.made = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		return r;
	}

	/** The usual starting level of a system on player ships (the vanilla "power" attribute), for systems a remodel adds. */
	public static int usualPower(String sysId) {
		Map<Integer, Integer> votes = new HashMap<Integer, Integer>();
		Pattern p = Pattern.compile("<" + sysId + "\\b[^>]*?power=\"(\\d+)\"");
		for (String f : FILES) {
			Matcher m = p.matcher(baseText(f));
			while (m.find()) {
				int v = Integer.parseInt(m.group(1));
				votes.put(v, votes.containsKey(v) ? votes.get(v) + 1 : 1);
			}
		}
		int best = 1, bestVotes = -1;
		for (Map.Entry<Integer, Integer> e : votes.entrySet()) if (e.getValue() > bestVotes) { best = e.getKey(); bestVotes = e.getValue(); }
		return best;
	}

	/** The systems as the blueprint has them now (room, station), for the Remodel window and Undo Retrofit's move list. */
	public static Map<String, Sys> layoutOf(ShipBlueprint bp) {
		Map<String, Sys> out = new LinkedHashMap<String, Sys>();
		if (bp == null || bp.getSystemList() == null) return out;
		for (SystemType t : SystemType.values()) {
			ShipBlueprint.SystemList.SystemRoom[] rs = bp.getSystemList().getSystemRoom(t);
			if (rs == null || rs.length == 0) continue;
			Sys s = new Sys(t.getId());
			s.room = rs[0].getRoomId();
			s.power = rs[0].getPower();
			s.weapon = rs[0].getWeapon();
			if (rs[0].getSlot() != null) {
				s.dir = rs[0].getSlot().getDirection();
				s.square = rs[0].getSlot().getNumber();
			}
			out.put(s.id, s);
		}
		return out;
	}
}
