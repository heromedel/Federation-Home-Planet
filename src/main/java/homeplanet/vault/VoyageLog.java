package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;

import homeplanet.core.SafeFiles;
import homeplanet.parser.SaveHelper;

/**
 * Each ship's voyage log (history/&lt;id&gt;/voyage.log): what FTL did to her between one look and the next (the
 * station looks at each save FTL writes while it's open, and on Refresh): jumps, sectors, ships defeated, crew joined
 * and lost, what came aboard and went, upgrades, damage and repairs, the Rebel Flagship. The last look is kept in
 * voyage.txt, with the sectors she has visited in all her journeys (FTL's save knows only the current one).
 * The station's own changes (a trade, a New Journey) move the last look silently: only FTL's doings are logged.
 */
public final class VoyageLog {
	private static final Logger log = LoggerFactory.getLogger(VoyageLog.class);
	private VoyageLog() { }

	static final String LOG = "voyage.log", LAST = "voyage.txt";
	/** Past this size the log keeps its newer half. */
	private static final long MAX_BYTES = 512 * 1024;

	/** Her log, oldest first (empty if none yet). */
	public static String read(Vault v, Ship s) {
		File f = new File(v.historyOf(s), LOG);
		try { return f.isFile() ? new String(SafeFiles.read(f), StandardCharsets.UTF_8) : ""; }
		catch (IOException e) { return "The Home Planet Station could not read her voyage log (" + f + "): " + e.getMessage(); }
	}
	/** The log of a ship by her id (she may have left the fleet: the museum), oldest first; empty if none. */
	public static String read(Vault v, String id) {
		File f = new File(new File(v.historyDir(), id), LOG);
		try { return f.isFile() ? new String(SafeFiles.read(f), StandardCharsets.UTF_8) : ""; }
		catch (IOException e) { return ""; }
	}
	/** The sectors a ship (by id) visited in all her journeys, at least the sector she's in in this save. */
	public static int visited(Vault v, String id, SavedGameState gs) {
		Properties p = new Properties();
		File f = new File(new File(v.historyDir(), id), LAST);
		try { if (f.isFile()) p.load(new StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); } catch (IOException e) { log.debug("Voyage log: {} could not be read: {}", f, e.toString()); }
		return Math.max(intOf(p, "visited", 0), gs == null ? 0 : gs.getSectorNumber() + 1);
	}
	/** The sectors she has visited in all her journeys, as far as the station has seen (at least the current one). */
	public static int visited(Vault v, Ship s) {
		Properties last = last(v, s);
		int n = intOf(last, "visited", 0);
		SavedGameState gs = s.save();
		int now = gs == null ? 0 : gs.getSectorNumber() + 1;
		return Math.max(n, now);
	}

	/** The sector (0 is the first) at her last look, or -1 if the station hasn't looked at her yet. */
	static int lastSector(Vault v, Ship s) { return intOf(last(v, s), "sector", -1); }

	/** The station's note for a New Journey (her log counts her journeys by it). */
	public static final String NEW_JOURNEY = "A new journey plotted from sector 1";
	/**
	 * Her journeys: the first (commissioning) and each New Journey since, as her voyage log tells (logs began in 4B.29,
	 * so an older ship counts from then). The count is kept in voyage.txt too, so a log cut down to its newer half
	 * doesn't lose any.
	 */
	public static int journeys(Vault v, Ship s) {
		int counted = 1;
		for (String line : read(v, s).split("\r?\n")) if (line.endsWith(NEW_JOURNEY)) counted++;
		Properties last = last(v, s);
		int kept = intOf(last, "journeys", 0), n = Math.max(kept, counted);
		if (n != kept && !last.isEmpty()) { last.setProperty("journeys", Integer.toString(n)); save(v, s, last); }
		return n;
	}
	/** What voyage.txt keeps across looks besides her summary. */
	private static void carry(Properties last, Properties now) {
		if (last.getProperty("journeys") != null) now.setProperty("journeys", last.getProperty("journeys"));
	}

	/** FTL has written her save: logs what changed since the last look. Nothing on the first look (it only starts the record). */
	static void observe(Vault v, Ship s, SavedGameState gs) {
		if (s == null || gs == null || s.state == Ship.State.STORAGE) return;
		Properties last = last(v, s);
		Properties now = summary(gs);
		carry(last, now);
		int visited = intOf(last, "visited", gs.getSectorNumber() + 1);
		if (last.isEmpty()) { now.setProperty("visited", Integer.toString(visited)); save(v, s, now); return; }
		List<String> lines = new ArrayList<String>();
		int sector = gs.getSectorNumber(), lastSector = intOf(last, "sector", sector);
		if (sector > lastSector) visited += sector - lastSector;
		now.setProperty("visited", Integer.toString(visited));
		changes(last, now, visited, lines);
		save(v, s, now);
		if (!lines.isEmpty()) append(v, s, lines);
	}
	/** The station changed her itself (a trade, a New Journey, her commissioning): the last look moves, nothing is logged. */
	static void baseline(Vault v, Ship s, SavedGameState gs) {
		if (s == null || gs == null || s.state == Ship.State.STORAGE) return;
		Properties last = last(v, s);
		Properties now = summary(gs);
		carry(last, now);
		now.setProperty("visited", Integer.toString(Math.max(intOf(last, "visited", 0), gs.getSectorNumber() + 1)));
		save(v, s, now);
	}
	/** A line of the station's own in her log (commissioned, a new journey plotted, rescued). */
	static void note(Vault v, Ship s, String text) {
		List<String> one = new ArrayList<String>();
		one.add(text);
		append(v, s, one);
	}

	// ---- what changed ----

	private static void changes(Properties a, Properties b, int visited, List<String> out) {
		int sector = intOf(b, "sector", 0), lastSector = intOf(a, "sector", 0);
		int beacons = intOf(b, "beacons", 0) - intOf(a, "beacons", 0);
		boolean moved = sector != lastSector || !b.getProperty("beacon", "").equals(a.getProperty("beacon", ""));
		if (sector > lastSector) out.add("Sector " + (sector + 1) + " reached (sectors visited: " + visited + ")");
		else if (sector < lastSector) out.add("Back to sector " + (sector + 1) + ": a new run");
		int hull = intOf(b, "hull", 0), lastHull = intOf(a, "hull", 0), scrap = intOf(b, "scrap", 0), lastScrap = intOf(a, "scrap", 0);
		if (moved || beacons > 0) {
			out.add((moved ? "Jumped" : "Waited") + ", hull " + hull + "/" + b.getProperty("maxHull", "?") + delta(hull - lastHull)
					+ ", scrap " + scrap + delta(scrap - lastScrap) + ", fuel " + b.getProperty("fuel", "?") + delta(intOf(b, "fuel", 0) - intOf(a, "fuel", 0))
					+ ", missiles " + b.getProperty("missiles", "?") + ", drone parts " + b.getProperty("drones", "?"));
		} else {
			if (hull > lastHull) out.add("Hull repaired to " + hull + "/" + b.getProperty("maxHull", "?") + delta(hull - lastHull));
			else if (hull < lastHull) out.add("Hull damaged to " + hull + "/" + b.getProperty("maxHull", "?") + delta(hull - lastHull));
			List<String> supplies = new ArrayList<String>();
			if (scrap != lastScrap) supplies.add("scrap " + scrap + delta(scrap - lastScrap));
			for (String[] k : new String[][] {{"fuel", "fuel"}, {"missiles", "missiles"}, {"drones", "drone parts"}}) {
				int now = intOf(b, k[0], 0), was = intOf(a, k[0], 0);
				if (now != was) supplies.add(k[1] + " " + now + delta(now - was));
			}
			if (!supplies.isEmpty()) {
				String line = String.join(", ", supplies);
				out.add(Character.toUpperCase(line.charAt(0)) + line.substring(1));
			}
		}
		int defeated = intOf(b, "defeated", 0) - intOf(a, "defeated", 0);
		if (defeated > 0) out.add(defeated + (defeated == 1 ? " ship" : " ships") + " defeated (" + b.getProperty("defeated") + " in all)");
		diff(a.getProperty("crew", ""), b.getProperty("crew", ""), "Crew joined: ", "Crew lost: ", out);
		diff(a.getProperty("items", ""), b.getProperty("items", ""), "Aboard now: ", "Gone: ", out);
		systems(a.getProperty("systems", ""), b.getProperty("systems", ""), out);
		int reactor = intOf(b, "reactor", 0), lastReactor = intOf(a, "reactor", 0);
		if (reactor != lastReactor) out.add("Reactor " + (reactor > lastReactor ? "upgraded" : "reduced") + " to " + reactor);
		int stage = intOf(b, "flagship", 0), lastStage = intOf(a, "flagship", 0);
		boolean near = "true".equals(b.getProperty("flagshipNear")), wasNear = "true".equals(a.getProperty("flagshipNear"));
		if (near && !wasNear) out.add("The Rebel Flagship is alongside (battle " + Math.max(1, stage) + ")");
		if (stage > lastStage && lastStage > 0) out.add("The Rebel Flagship withdrew after battle " + lastStage);
	}
	private static String delta(int d) { return d == 0 ? "" : " (" + (d > 0 ? "+" : "") + d + ")"; }
	/** Two lists of names ("a|b|b"): what's new, and what's gone, counting repeats. */
	private static void diff(String before, String after, String added, String removed, List<String> out) {
		Map<String, Integer> count = new LinkedHashMap<String, Integer>();
		for (String x : split(after)) count.put(x, (count.containsKey(x) ? count.get(x) : 0) + 1);
		for (String x : split(before)) count.put(x, (count.containsKey(x) ? count.get(x) : 0) - 1);
		List<String> plus = new ArrayList<String>(), minus = new ArrayList<String>();
		for (Map.Entry<String, Integer> e : count.entrySet()) {
			for (int i = 0; i < e.getValue(); i++) plus.add(e.getKey());
			for (int i = 0; i < -e.getValue(); i++) minus.add(e.getKey());
		}
		if (!plus.isEmpty()) out.add(added + String.join(", ", plus));
		if (!minus.isEmpty()) out.add(removed + String.join(", ", minus));
	}
	private static void systems(String before, String after, List<String> out) {
		Map<String, Integer> a = levels(before), b = levels(after);
		for (Map.Entry<String, Integer> e : b.entrySet()) {
			Integer was = a.get(e.getKey());
			if (was == null) out.add("New system: " + e.getKey() + " " + e.getValue());
			else if (e.getValue() > was) out.add(e.getKey() + " upgraded to " + e.getValue());
			else if (e.getValue() < was) out.add(e.getKey() + " reduced to " + e.getValue());
		}
		for (String k : a.keySet()) if (!b.containsKey(k)) out.add("System removed: " + k);
	}
	private static Map<String, Integer> levels(String s) {
		Map<String, Integer> m = new LinkedHashMap<String, Integer>();
		for (String x : split(s)) {
			int c = x.lastIndexOf(' ');
			try { if (c > 0) m.put(x.substring(0, c), Integer.parseInt(x.substring(c + 1))); } catch (NumberFormatException e) { }
		}
		return m;
	}
	private static List<String> split(String s) {
		List<String> out = new ArrayList<String>();
		for (String x : s.split("\\|")) if (!x.isEmpty()) out.add(x);
		return out;
	}

	// ---- a look at her ----

	private static Properties summary(SavedGameState gs) {
		ShipState s = gs.getPlayerShip();
		Properties p = new Properties();
		p.setProperty("sector", Integer.toString(gs.getSectorNumber()));
		p.setProperty("beacon", Integer.toString(gs.getCurrentBeaconId()));
		p.setProperty("beacons", Integer.toString(gs.getTotalBeaconsExplored()));
		p.setProperty("defeated", Integer.toString(gs.getTotalShipsDefeated()));
		p.setProperty("hull", Integer.toString(s.getHullAmt()));
		int maxHull = s.getHullAmt();
		try { maxHull = net.blerf.ftl.parser.DataManager.get().getShip(gs.getPlayerShipBlueprintId()).getHealth().amount; } catch (Exception e) { }
		p.setProperty("maxHull", Integer.toString(maxHull));
		p.setProperty("scrap", Integer.toString(s.getScrapAmt()));
		p.setProperty("fuel", Integer.toString(s.getFuelAmt()));
		p.setProperty("missiles", Integer.toString(s.getMissilesAmt()));
		p.setProperty("drones", Integer.toString(s.getDronePartsAmt()));
		p.setProperty("reactor", Integer.toString(s.getReservePowerCapacity()));
		List<String> crew = new ArrayList<String>();
		for (CrewState c : SaveHelper.getOwnCrew(s)) crew.add(c.getName() + " (" + race(c.getRace().getId()) + ")");
		p.setProperty("crew", String.join("|", crew));
		List<String> items = new ArrayList<String>();
		for (WeaponState w : s.getWeaponList()) items.add(title(w.getWeaponId()));
		for (DroneState d : s.getDroneList()) items.add(title(d.getDroneId()));
		for (String a : s.getAugmentIdList()) items.add(title(a));
		for (String c : gs.getCargoIdList()) items.add(title(c) + " (cargo)");
		p.setProperty("items", String.join("|", items));
		List<String> systems = new ArrayList<String>();
		for (SystemType t : SystemType.values()) {
			SystemState st = s.getSystem(t);
			if (st != null && st.getCapacity() > 0) systems.add(homeplanet.model.Items.systemTitle(t.getId()) + " " + st.getCapacity());
		}
		p.setProperty("systems", String.join("|", systems));
		if (gs.getRebelFlagshipState() != null) p.setProperty("flagship", Integer.toString(gs.getRebelFlagshipState().getPendingStage()));
		p.setProperty("flagshipNear", Boolean.toString(gs.isRebelFlagshipNearby()));
		return p;
	}
	private static String title(String id) {
		try { return homeplanet.model.Items.title(id); } catch (Exception e) { return id; }
	}
	private static String race(String id) {
		if ("energy".equals(id)) return "Zoltan";
		if ("anaerobic".equals(id)) return "Lanius";
		return id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1);
	}

	// ---- files ----

	private static Properties last(Vault v, Ship s) {
		Properties p = new Properties();
		File f = new File(v.historyOf(s), LAST);
		if (!f.isFile()) return p;
		try { p.load(new StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); }
		return p;
	}
	private static void save(Vault v, Ship s, Properties p) {
		File dir = v.historyOf(s);
		try {
			if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
			StringWriter w = new StringWriter();
			p.store(w, "Her last look, for the voyage log");
			SafeFiles.writeText(new File(dir, LAST), w.toString(), false);
		} catch (IOException e) {
			log.warn("Could not keep {}'s last look: {}", s, e.toString());
		}
	}
	private static void append(Vault v, Ship s, List<String> lines) {
		File dir = v.historyOf(s), f = new File(dir, LOG);
		String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
		StringBuilder sb = new StringBuilder(read(v, s));
		if (f.length() > MAX_BYTES) sb.delete(0, sb.indexOf("\n", sb.length() / 2) + 1); // the newer half stays
		for (String l : lines) sb.append(stamp).append("  ").append(l).append('\n');
		try {
			if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
			SafeFiles.writeText(f, sb.toString(), false);
		} catch (IOException e) {
			log.warn("Could not write {}'s voyage log: {}", s, e.toString());
		}
	}
	private static int intOf(Properties p, String k, int dflt) {
		try { return Integer.parseInt(p.getProperty(k, "").trim()); } catch (NumberFormatException e) { return dflt; }
	}
}
