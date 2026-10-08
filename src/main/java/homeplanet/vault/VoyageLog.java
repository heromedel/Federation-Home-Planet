package homeplanet.vault;

import java.io.File;
import java.io.IOException;
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
import net.blerf.ftl.parser.SavedGameParser.EnvironmentState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;

import homeplanet.core.SafeFiles;
import homeplanet.core.Event;
import homeplanet.core.EventLog;
import homeplanet.core.Store;
import homeplanet.parser.SaveHelper;

/**
 * Each ship's voyage: what FTL did to her between one look and the next, in her own log and the fleet's event log (her
 * voyage.log until 5.93, kept as it was, no longer written; it still travels in her package for an older station) (the
 * station looks at each save FTL writes while it's open, and on Refresh): jumps, sectors, ships defeated, crew joined
 * and lost, what came aboard and went, upgrades, damage and repairs, the Rebel Flagship. The last look is kept in
 * voyage.txt, with the sectors she has visited in all her journeys (FTL's save knows only the current one).
 * The station's own changes (a trade, a New Journey) move the last look silently: only FTL's doings are logged.
 */
public final class VoyageLog {
	private static final Logger log = LoggerFactory.getLogger(VoyageLog.class);
	private VoyageLog() { }

	public static final String LOG = "voyage.log", LAST = "voyage.txt";
	/** The sectors a ship (by id) visited in all her journeys, at least the sector she's in in this save. */
	public static int visited(Vault v, String id, SavedGameState gs) {
		return Math.max(Store.num(last(v, id), "visited", 0), gs == null ? 0 : gs.getSectorNumber() + 1);
	}
	/** The sector her last look saw her in (by id; she may have left the fleet), or 0. */
	public static int lastSector(Vault v, String id) { return Store.num(last(v, id), "sector", 0); }
	/** The sectors she has visited in all her journeys, as far as the station has seen (at least the current one). */
	public static int visited(Vault v, Ship s) {
		Properties last = last(v, s);
		int n = Store.num(last, "visited", 0);
		SavedGameState gs = s.save();
		int now = gs == null ? 0 : gs.getSectorNumber() + 1;
		return Math.max(n, now);
	}

	/** The sector (0 is the first) at her last look, or -1 if the station hasn't looked at her yet. */
	static int lastSector(Vault v, Ship s) { return Store.num(last(v, s), "sector", -1); }

	/** The station's note for a New Journey (her log counts her journeys by it). */
	public static final String NEW_JOURNEY = "A new journey plotted from sector 1";
	/** A New Journey in her log: its own kind, or the note the station writes when it plots one (by its text field; an older note by its line). */
	public static boolean newJourney(EventLog.Entry e) { return e.kind.equals("NEW_RUN") || e.get("text", e.human).endsWith(NEW_JOURNEY); }
	/**
	 * Her journeys: the first (commissioning) and each New Journey since, as her own log tells (5.93; her voyage.log
	 * before, which began in 4B.29, so an older ship counts from then). The count is kept in voyage.txt too, so none is
	 * lost to a log cut down.
	 */
	public static int journeys(Vault v, Ship s) {
		int counted = 1;
		for (EventLog.Entry e : EventLog.voyage(ShipStore.entries(v.folderOf(s)), s.id)) if (newJourney(e)) counted++;
		Properties last = last(v, s);
		int kept = Store.num(last, "journeys", 0), n = Math.max(kept, counted);
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
		Reputation.look(v, s, gs); // what she did scores for the career, if one runs
		Properties last = last(v, s);
		Properties now = summary(gs);
		carry(last, now);
		int visited = Store.num(last, "visited", gs.getSectorNumber() + 1);
		if (last.isEmpty()) { now.setProperty("visited", Integer.toString(visited)); save(v, s, now); return; }
		List<Event> lines = new ArrayList<Event>();
		int sector = gs.getSectorNumber(), lastSector = Store.num(last, "sector", sector);
		if (sector > lastSector) visited += sector - lastSector;
		now.setProperty("visited", Integer.toString(visited));
		changes(last, now, visited, lines);
		save(v, s, now);
		if (!lines.isEmpty()) append(v, s, lines);
	}
	/** The station changed her itself (a trade, a New Journey, her commissioning): the last look moves, nothing is logged. */
	static void baseline(Vault v, Ship s, SavedGameState gs) {
		if (s == null || gs == null || s.state == Ship.State.STORAGE) return;
		Reputation.rebase(v, s, gs);
		Properties last = last(v, s);
		Properties now = summary(gs);
		carry(last, now);
		now.setProperty("visited", Integer.toString(Math.max(Store.num(last, "visited", 0), gs.getSectorNumber() + 1)));
		save(v, s, now);
	}
	/** A line of the station's own in her log (commissioned, a new journey plotted, rescued). */
	static void note(Vault v, Ship s, String text) { note(v, s, Event.of("VOYAGE_NOTE").put("text", text).human(text)); }
	/** As above, with the event's own kind and fields (the human line is what her log shows). */
	static void note(Vault v, Ship s, Event e) {
		List<Event> one = new ArrayList<Event>();
		one.add(e);
		append(v, s, one);
	}

	// ---- what changed ----

	private static void changes(Properties a, Properties b, int visited, List<Event> out) {
		int sector = Store.num(b, "sector", 0), lastSector = Store.num(a, "sector", 0);
		int beacons = Store.num(b, "beacons", 0) - Store.num(a, "beacons", 0);
		boolean moved = sector != lastSector || !b.getProperty("beacon", "").equals(a.getProperty("beacon", ""));
		if (sector > lastSector) out.add(Event.of("SECTOR_REACHED").put("sector", sector + 1).put("visited", visited).human("Sector " + (sector + 1) + " reached (sectors visited: " + visited + ")"));
		else if (sector < lastSector) out.add(Event.of("NEW_RUN").put("sector", sector + 1).put("was", lastSector + 1).human("Back to sector " + (sector + 1) + ": a new run"));
		int hull = Store.num(b, "hull", 0), lastHull = Store.num(a, "hull", 0), scrap = Store.num(b, "scrap", 0), lastScrap = Store.num(a, "scrap", 0);
		Event state = Event.of("STATE").put("hull", hull).put("max_hull", b.getProperty("maxHull", "?")).put("hull_change", hull - lastHull)
				.put("scrap", scrap).put("scrap_change", scrap - lastScrap).put("fuel", b.getProperty("fuel", "?")).put("fuel_change", Store.num(b, "fuel", 0) - Store.num(a, "fuel", 0))
				.put("missiles", b.getProperty("missiles", "?")).put("missiles_change", Store.num(b, "missiles", 0) - Store.num(a, "missiles", 0))
				.put("drone_parts", b.getProperty("drones", "?")).put("drone_parts_change", Store.num(b, "drones", 0) - Store.num(a, "drones", 0))
				.put("sector", sector + 1).put("beacon", b.getProperty("beacon")).put("beacons_total", b.getProperty("beacons")).put("beacons_jumped", beacons).put("at_store", b.getProperty("store"));
		if (moved || beacons > 0) {
			out.add(Event.of(moved ? "JUMPED" : "WAITED").putAll(state).human((moved ? "Jumped" : "Waited") + ", hull " + hull + "/" + b.getProperty("maxHull", "?") + delta(hull - lastHull)
					+ ", scrap " + scrap + delta(scrap - lastScrap) + ", fuel " + b.getProperty("fuel", "?") + delta(Store.num(b, "fuel", 0) - Store.num(a, "fuel", 0))
					+ ", missiles " + b.getProperty("missiles", "?") + ", drone parts " + b.getProperty("drones", "?")));
		} else {
			if (hull > lastHull) out.add(Event.of("HULL_REPAIRED").putAll(state).human("Hull repaired to " + hull + "/" + b.getProperty("maxHull", "?") + delta(hull - lastHull)));
			else if (hull < lastHull) out.add(Event.of("HULL_DAMAGED").putAll(state).human("Hull damaged to " + hull + "/" + b.getProperty("maxHull", "?") + delta(hull - lastHull)));
			List<String> supplies = new ArrayList<String>();
			if (scrap != lastScrap) supplies.add("scrap " + scrap + delta(scrap - lastScrap));
			for (String[] k : new String[][] {{"fuel", "fuel"}, {"missiles", "missiles"}, {"drones", "drone parts"}}) {
				int now = Store.num(b, k[0], 0), was = Store.num(a, k[0], 0);
				if (now != was) supplies.add(k[1] + " " + now + delta(now - was));
			}
			if (!supplies.isEmpty()) {
				String line = String.join(", ", supplies);
				out.add(Event.of("SUPPLIES").putAll(state).human(homeplanet.model.Words.cap(line)));
			}
		}
		int defeated = Store.num(b, "defeated", 0) - Store.num(a, "defeated", 0);
		if (defeated > 0) out.add(Event.of("SHIPS_DEFEATED").put("count", defeated).put("total", b.getProperty("defeated")).human(defeated + (defeated == 1 ? " ship" : " ships") + " defeated (" + b.getProperty("defeated") + " in all)"));
		crewDiff(a.getProperty("crew", ""), b.getProperty("crew", ""), out);
		List<String>[] items = diff(a.getProperty("items", ""), b.getProperty("items", ""));
		if (!items[0].isEmpty()) out.add(list(Event.of("ITEMS_ABOARD"), "item", items[0]).human("Aboard now: " + String.join(", ", items[0])));
		if (!items[1].isEmpty()) out.add(list(Event.of("ITEMS_GONE"), "item", items[1]).human("Gone: " + String.join(", ", items[1])));
		gear(a, b, scrap < lastScrap, out);
		if (moved && "true".equals(b.getProperty("store"))) out.add(Event.of("STORE_ARRIVED").put("sector", sector + 1).put("beacon", b.getProperty("beacon")).human("Arrived at a store"));
		beacon(a, b, moved, out);
		systems(a.getProperty("systems", ""), b.getProperty("systems", ""), out);
		int reactor = Store.num(b, "reactor", 0), lastReactor = Store.num(a, "reactor", 0);
		if (reactor != lastReactor) out.add(Event.of(reactor > lastReactor ? "REACTOR_UPGRADED" : "REACTOR_REDUCED").put("level", reactor).put("was", lastReactor).human("Reactor " + (reactor > lastReactor ? "upgraded" : "reduced") + " to " + reactor));
		int stage = Store.num(b, "flagship", 0), lastStage = Store.num(a, "flagship", 0);
		boolean near = "true".equals(b.getProperty("flagshipNear")), wasNear = "true".equals(a.getProperty("flagshipNear"));
		if (near && !wasNear) out.add(Event.of("FLAGSHIP_ALONGSIDE").put("battle", Math.max(1, stage)).human("The Rebel Flagship is alongside (battle " + Math.max(1, stage) + ")"));
		if (stage > lastStage && lastStage > 0) out.add(Event.of("FLAGSHIP_WITHDREW").put("battle", lastStage).put("next", stage).human("The Rebel Flagship withdrew after battle " + lastStage));
	}
	/** A list as repeated fields of one key. */
	private static Event list(Event e, String key, List<String> values) {
		for (String x : values) e.put(key, x);
		return e;
	}
	/** Crew who came aboard and crew lost since the last look, each with name and race ("Name (Race)" in the summary). */
	private static void crewDiff(String before, String after, List<Event> out) {
		List<String>[] d = diff(before, after);
		String[] kinds = {"CREW_JOINED", "CREW_LOST"}, words = {"Crew joined: ", "Crew lost: "};
		for (int i = 0; i < 2; i++) {
			if (d[i].isEmpty()) continue;
			Event e = Event.of(kinds[i]);
			for (String x : d[i]) {
				int c = x.lastIndexOf(" (");
				e.put("crew", c > 0 ? x.substring(0, c) : x).put("race", c > 0 && x.endsWith(")") ? x.substring(c + 2, x.length() - 1) : null);
			}
			out.add(e.human(words[i] + String.join(", ", d[i])));
		}
	}
	/**
	 * Gear new to her (moved between cargo and fittings doesn't count): bought, if she was at a store and spent scrap
	 * there, else picked up (an event's gift, a salvage). For the Captain's Log (5.18); the Aboard now / Gone lines stay.
	 */
	private static void gear(Properties a, Properties b, boolean spent, List<Event> out) {
		Map<String, Integer> count = new LinkedHashMap<String, Integer>();
		for (String x : split(b.getProperty("items", ""))) { String k = x.replace(" (cargo)", ""); count.put(k, (count.containsKey(k) ? count.get(k) : 0) + 1); }
		for (String x : split(a.getProperty("items", ""))) { String k = x.replace(" (cargo)", ""); count.put(k, (count.containsKey(k) ? count.get(k) : 0) - 1); }
		List<String> plus = new ArrayList<String>();
		for (Map.Entry<String, Integer> e : count.entrySet()) for (int i = 0; i < e.getValue(); i++) plus.add(e.getKey());
		if (plus.isEmpty()) return;
		boolean bought = spent && "true".equals(a.getProperty("store")); // where she was when she had it: the stop before the jump
		out.add(list(Event.of(bought ? "BOUGHT" : "PICKED_UP"), "item", plus).human((bought ? "Bought at a store: " : "Picked up: ") + String.join(", ", plus)));
	}
	/**
	 * What her new beacon held (heromedel, 5.19, for the Captain's Log): the hazards there, and a ship met. The save keeps
	 * a star (FTL's flare star), a pulsar, an Anti-Ship Battery and an asteroid field; a nebula shows only in FTL's own count of
	 * nebula jumps, and an ion storm as a jump into danger that names none of those. A ship can turn up after the jump
	 * (her next look): "Ship met" then, on its own.
	 */
	private static void beacon(Properties a, Properties b, boolean moved, List<Event> out) {
		if (moved) {
			List<String> there = new ArrayList<String>(), ids = new ArrayList<String>(), hazards = split(b.getProperty("hazards", ""));
			int nebula = Store.num(b, "nebulaJumps", 0) - Store.num(a, "nebulaJumps", Store.num(b, "nebulaJumps", 0)); // no count kept before 5.19: no change
			int danger = Store.num(b, "dangerJumps", 0) - Store.num(a, "dangerJumps", Store.num(b, "dangerJumps", 0));
			if (danger > 0 && hazards.isEmpty()) { there.add("an ion storm"); ids.add("storm"); }
			else if (nebula > 0) { there.add("a nebula"); ids.add("nebula"); }
			for (String h : hazards) { there.add(HAZARDS.containsKey(h) ? HAZARDS.get(h) : h); ids.add(h); }
			if (!there.isEmpty()) out.add(list(Event.of("BEACON_HAZARDS"), "hazard", ids).put("sector", Store.num(b, "sector", 0) + 1).put("beacon", b.getProperty("beacon")).human("Beacon: " + String.join(", ", there)));
		}
		String met = b.getProperty("met", ""), was = a.getProperty("met");
		if (!met.isEmpty() && (moved || (was != null && !met.equals(was)))) out.add(Event.of("SHIP_MET").put("met", met).put("sector", Store.num(b, "sector", 0) + 1).put("beacon", b.getProperty("beacon")).human("Ship met: " + met));
	}
	private static final Map<String, String> HAZARDS = new LinkedHashMap<String, String>();
	static {
		HAZARDS.put("asteroids", "an asteroid field");
		HAZARDS.put("sun", "a star"); // FTL: "dangerously close to a star"
		HAZARDS.put("pulsar", "a pulsar");
		HAZARDS.put("pds", "an Anti-Ship Battery"); // FTL's name for it (docs/LORE_COMPONENTS.md 22)
	}
	private static String delta(int d) { return d == 0 ? "" : " (" + (d > 0 ? "+" : "") + d + ")"; }
	/** Two lists of names ("a|b|b"): {what's new, what's gone}, counting repeats. */
	@SuppressWarnings("unchecked")
	private static List<String>[] diff(String before, String after) {
		Map<String, Integer> count = new LinkedHashMap<String, Integer>();
		for (String x : split(after)) count.put(x, (count.containsKey(x) ? count.get(x) : 0) + 1);
		for (String x : split(before)) count.put(x, (count.containsKey(x) ? count.get(x) : 0) - 1);
		List<String> plus = new ArrayList<String>(), minus = new ArrayList<String>();
		for (Map.Entry<String, Integer> e : count.entrySet()) {
			for (int i = 0; i < e.getValue(); i++) plus.add(e.getKey());
			for (int i = 0; i < -e.getValue(); i++) minus.add(e.getKey());
		}
		return new List[] {plus, minus};
	}
	private static void systems(String before, String after, List<Event> out) {
		Map<String, Integer> a = levels(before), b = levels(after);
		for (Map.Entry<String, Integer> e : b.entrySet()) {
			Integer was = a.get(e.getKey());
			if (was == null) out.add(Event.of("SYSTEM_NEW").put("system", e.getKey()).put("level", e.getValue()).human("New system: " + e.getKey() + " " + e.getValue()));
			else if (e.getValue() > was) out.add(Event.of("SYSTEM_UPGRADED").put("system", e.getKey()).put("level", e.getValue()).put("was", was).human(e.getKey() + " upgraded to " + e.getValue()));
			else if (e.getValue() < was) out.add(Event.of("SYSTEM_REDUCED").put("system", e.getKey()).put("level", e.getValue()).put("was", was).human(e.getKey() + " reduced to " + e.getValue()));
		}
		for (String k : a.keySet()) if (!b.containsKey(k)) out.add(Event.of("SYSTEM_REMOVED").put("system", k).put("was", a.get(k)).human("System removed: " + k));
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
		p.setProperty("store", Boolean.toString(SaveHelper.isAtStation(gs))); // a store at her beacon (5.18: arriving at a station, buying there)
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
		for (CrewState c : SaveHelper.getOwnCrew(s)) crew.add(c.getName() + " (" + homeplanet.model.Crew.racePeople(c.getRace().getId()) + ")"); // the people's name: "Rock" (the register reads "Rockman" too)
		p.setProperty("crew", String.join("|", crew));
		List<String> items = new ArrayList<String>();
		for (String g : SaveHelper.gear(s)) items.add(title(g));
		for (String c : SaveHelper.cargo(gs)) items.add(title(c) + " (cargo)"); // not the augment FTL is asking about (5.52)
		p.setProperty("items", String.join("|", items));
		List<String> systems = new ArrayList<String>();
		for (SystemType t : SystemType.values()) {
			SystemState st = s.getSystem(t);
			if (st != null && st.getCapacity() > 0) systems.add(homeplanet.model.Items.systemTitle(t.getId()) + " " + st.getCapacity());
		}
		p.setProperty("systems", String.join("|", systems));
		if (gs.getRebelFlagshipState() != null) p.setProperty("flagship", Integer.toString(gs.getRebelFlagshipState().getPendingStage()));
		p.setProperty("flagshipNear", Boolean.toString(gs.isRebelFlagshipNearby()));
		List<String> hazards = new ArrayList<String>(); // her beacon's (5.19)
		EnvironmentState env = gs.getEnvironment();
		if (env != null) {
			if (env.getAsteroidField() != null) hazards.add("asteroids");
			if (env.isRedGiantPresent()) hazards.add("sun");
			if (env.isPulsarPresent()) hazards.add("pulsar");
			if (env.isPDSPresent()) hazards.add("pds");
		}
		p.setProperty("hazards", String.join("|", hazards));
		p.setProperty("nebulaJumps", Integer.toString(gs.hasStateVar("nebula") ? gs.getStateVar("nebula") : 0));
		p.setProperty("dangerJumps", Integer.toString(gs.hasStateVar("env_danger") ? gs.getStateVar("env_danger") : 0));
		p.setProperty("met", met(gs));
		return p;
	}
	/** The ship alongside her, in words ("a Rock pirate", "a rebel ship"), or "" (none, or the Rebel Flagship: told on its own). */
	private static String met(SavedGameState gs) {
		ShipState n = gs.getNearbyShip();
		if (n == null || gs.isRebelFlagshipNearby()) return "";
		String event = null, list = null;
		try { event = gs.getBeaconList().get(gs.getCurrentBeaconId()).getShipEventId(); } catch (Exception e) { }
		try { if (event != null) { net.blerf.ftl.xml.ShipEvent se = net.blerf.ftl.parser.DataManager.get().getShipEventById(event); if (se != null) list = se.getAutoBlueprintId(); } }
		catch (Exception e) { } // a mod's event, or no ftl.dat: her crew tell
		Map<String, Integer> races = new LinkedHashMap<String, Integer>();
		String crew = "";
		for (CrewState c : n.getCrewList()) {
			String r = c.getRace() == null ? "" : c.getRace().getId();
			if (r.isEmpty() || r.equals("battle")) continue; // a boarding drone
			races.put(r, (races.containsKey(r) ? races.get(r) : 0) + 1);
			if (crew.isEmpty() || races.get(r) > races.get(crew)) crew = r;
		}
		return shipWords(event, list, crew);
	}
	/**
	 * A ship in words, from her ship event, its list of ships (SHIPS_ROCK_PIRATE…) and her crew's commonest race: "a Rock
	 * pirate", "a Mantis ship", "a rebel ship", "an automated ship", "a Federation ship", "a civilian ship", "a ship".
	 */
	public static String shipWords(String event, String list, String crew) {
		String l = list == null ? "" : list.toUpperCase(), e = event == null ? "" : event.toUpperCase();
		boolean pirate = l.contains("PIRATE") || e.contains("PIRATE");
		String race = homeplanet.model.Crew.peopleOf(l);
		if (race == null && crew != null && !crew.isEmpty()) race = homeplanet.model.Crew.racePeople(crew);
		String what;
		if (pirate) what = race == null ? "pirate ship" : race + " pirate";
		else if (l.contains("AUTO")) what = "automated ship";
		else if (l.contains("REBEL")) what = "rebel ship";
		else if (l.contains("FED")) what = "Federation ship";
		else if (l.contains("CIVILIAN")) what = "civilian ship";
		else what = race == null ? "ship" : race + " ship";
		return homeplanet.model.Words.a(what);
	}
	private static String title(String id) {
		try { return homeplanet.model.Items.title(id); } catch (Exception e) { return id; }
	}

	// ---- files ----

	private static Properties last(Vault v, Ship s) { return Store.read(new File(v.historyOf(s), LAST)); }
	private static Properties last(Vault v, String id) { return Store.read(new File(v.folderOfId(id), LAST)); }
	private static void save(Vault v, Ship s, Properties p) {
		try {
			Store.write(new File(v.historyOf(s), LAST), p, "Her last look, for the voyage log");
		} catch (IOException e) {
			log.warn("Could not keep {}'s last look: {}", s, e.toString());
		}
	}
	/** The fields every event in her log carries: who she is. */
	public static Event shipFields(Ship s) {
		return Event.of("SHIP").put("ship", s.name + "." + s.id).put("ship_name", s.name).put("ship_id", s.id).put("ship_state", s.state == null ? null : s.state.name().toLowerCase());
	}
	private static void append(Vault v, Ship s, List<Event> events) {
		// her own log and the fleet's event log alone (5.93): voyage.log and the master log's copy are no longer written
		Event who = shipFields(s);
		for (Event e : events) EventLog.write(v, Event.of(e.kind).put("log", "voyage").putAll(who).putAll(e).human(e.human()));
	}
}
