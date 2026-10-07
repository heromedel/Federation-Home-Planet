package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import homeplanet.core.Event;
import homeplanet.core.EventLog;
import homeplanet.core.HistoryLog;
import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;
import homeplanet.core.Store;

/**
 * The old logs converted to events once (docs/OVERHAUL-6.md §3.5, Phase 5 step 22; 5.73): the first time a fleet opens
 * at 5.73 or later, every entry of its station log (history.log), its ships' voyage logs, its reputation log and the
 * clock's days in the master log that has no event yet (from before 5.63, when the event log began) is written into
 * events.log as a two-line entry: the kind, the fields the old line gives away, {@code converted=true}, the entry's own
 * time and stardate (the master log's copy says the day), and the old human line exactly as it was. The old logs are
 * left as they are. A marker in logs/ says it was done, so it never runs twice. The old prose parsers' last job.
 */
public final class LogConvert {
	private static final Logger log = LoggerFactory.getLogger(LogConvert.class);
	private LogConvert() { }

	/** The marker, in logs/: when, by which station, how many of each. */
	public static final String MARK = "converted.txt";
	private static final Pattern STAMP = Pattern.compile("(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d)  (.*)");

	public static boolean done(Vault v) { return done(v.root); }
	/** Whether a fleet's old logs were read in, by its folder (a fleet not in use). */
	public static boolean done(File fleetRoot) { return new File(new File(fleetRoot, "logs"), MARK).isFile(); }

	/** Converts what has no event yet, and writes the marker. Never throws: a log it can't read is skipped and said in the debug log. */
	public static void run(Vault v) {
		if (done(v)) return;
		List<EventLog.Entry> have = EventLog.read(v);
		// the earliest event each log already has: entries before it are the ones without events (the boundary minute by its text)
		Map<String, String> first = new HashMap<String, String>(); // "station", "reputation", "clock", "voyage:<id>" -> earliest time
		Set<String> atFirst = new HashSet<String>(); // "<log>|<minute>|<human>": entries at the boundary minute, already there
		for (EventLog.Entry e : have) {
			String which = e.get("log", e.kind.equals("DAY") ? "clock" : "");
			if (which.equals("voyage")) which = "voyage:" + e.get("ship_id", "");
			if (which.isEmpty()) continue;
			String was = first.get(which);
			if (was == null || e.time.compareTo(was) < 0) first.put(which, e.time);
		}
		for (EventLog.Entry e : have) {
			String which = e.get("log", e.kind.equals("DAY") ? "clock" : "");
			if (which.equals("voyage")) which = "voyage:" + e.get("ship_id", "");
			String f = first.get(which);
			if (f != null && e.time.length() >= 16 && e.time.substring(0, 16).equals(f.substring(0, 16))) atFirst.add(which + "|" + f.substring(0, 16) + "|" + (which.equals("station") ? e.get("headline", e.human) : e.human));
		}
		Map<String, List<String[]>> master = masterCopies(v); // log -> {text, day}, in order
		int station = 0, voyage = 0, reputation = 0, days = 0;
		Map<String, String> known = new HashMap<String, String>(); // every ship the fleet has or remembers: id -> name, so a converted entry that names her by id goes into her log too (5.77)
		for (File d : v.shipFolders()) { ShipStore.Record r = ShipStore.read(d); if (r != null) known.put(r.id, r.name); }
		try { station = convertStation(v, first.get("station"), atFirst, master, known); } catch (Exception e) { log.warn("The station log could not be converted: {}", e.toString()); }
		for (File d : v.shipFolders()) {
			try { voyage += convertVoyage(v, d, first, atFirst, master); } catch (Exception e) { log.warn("{}'s voyage log could not be converted: {}", d.getName(), e.toString()); }
		}
		try { reputation = convertReputation(v, first.get("reputation"), atFirst, master); } catch (Exception e) { log.warn("The reputation log could not be converted: {}", e.toString()); }
		try { days = convertDays(v, first.get("clock"), atFirst); } catch (Exception e) { log.warn("The clock's days could not be converted: {}", e.toString()); }
		Properties p = new Properties();
		p.setProperty("station", HomePlanet.version()); p.setProperty("entries_station", Integer.toString(station)); p.setProperty("entries_voyage", Integer.toString(voyage));
		p.setProperty("entries_reputation", Integer.toString(reputation)); p.setProperty("days", Integer.toString(days));
		try { Store.write(new File(v.logsDir(), MARK), p, "The old logs were converted to events once (5.73); Federation Home Planet never does it again for this fleet"); }
		catch (IOException e) { log.warn("Could not write {}: {}", MARK, e.toString()); }
		int all = station + voyage + reputation + days;
		if (all > 0) HistoryLog.entry("LOGS_CONVERTED", "the old logs were read into the event log once: " + station + " station entries, " + voyage + " voyage entries, " + reputation + " reputation entries, " + days + " days", null,
				Event.of("LOGS_CONVERTED").put("entries_station", station).put("entries_voyage", voyage).put("entries_reputation", reputation).put("days", days)
						.human("The station read its old logs into its records once."));
	}

	/** The master log's E lines by log: what each entry said, and its day. */
	private static Map<String, List<String[]>> masterCopies(Vault v) {
		Map<String, List<String[]>> out = new HashMap<String, List<String[]>>();
		File f = new File(v.logsDir(), MasterLog.FILE);
		if (!f.isFile()) return out;
		try {
			for (String l : new String(SafeFiles.read(f), StandardCharsets.UTF_8).split("\r?\n")) {
				if (!l.startsWith("E\t")) continue;
				String[] w = l.split("\t", 5);
				if (w.length < 5) continue;
				List<String[]> list = out.get(w[3]);
				if (list == null) out.put(w[3], list = new ArrayList<String[]>());
				list.add(new String[] {w[4], w[2].trim()});
			}
		} catch (IOException e) { log.warn("Could not read the master log: {}", e.toString()); }
		return out;
	}
	/** The day of an entry: the master log's next copy that starts with its text (in order, as stationDays reads them); 0 when unknown. */
	private static final class Days {
		private final List<String[]> copies; private int at = 0;
		Days(List<String[]> copies) { this.copies = copies == null ? new ArrayList<String[]>() : copies; }
		int of(String text) {
			for (int i = at; i < copies.size(); i++) {
				if (!copies.get(i)[0].startsWith(text)) continue;
				at = i + 1;
				try { return Math.max(0, Integer.parseInt(copies.get(i)[1])); } catch (NumberFormatException e) { return 0; }
			}
			return 0;
		}
	}
	/** Before the log's first event, or at its minute and not among the entries there. */
	private static boolean wanted(String which, String stamp, String text, String firstTime, Set<String> atFirst) {
		if (firstTime == null) return true;
		String minute = firstTime.substring(0, 16);
		int c = stamp.compareTo(minute);
		return c < 0 || (c == 0 && !atFirst.contains(which + "|" + minute + "|" + text));
	}
	private static Event base(String kind, String which, String stamp, int day) {
		Event e = Event.of(kind).put("log", which.startsWith("voyage") ? "voyage" : which).put("converted", true).put("time", stamp + ":00");
		if (day > 0) e.put("day", day);
		return e;
	}

	private static final Pattern ID = Pattern.compile("\\b([0-9a-f]{16})\\b");
	private static int convertStation(Vault v, String firstTime, Set<String> atFirst, Map<String, List<String[]>> master, Map<String, String> known) throws IOException {
		File f = v.historyLog();
		if (!f.isFile()) return 0;
		String text = new String(SafeFiles.read(f), StandardCharsets.UTF_8);
		Days days = new Days(master.get("station"));
		int n = 0;
		String stamp = null, kind = null, headline = null;
		List<String> details = new ArrayList<String>();
		for (String line : (text + "\n\u0000").split("\r?\n")) {
			boolean end = line.equals("\u0000");
			if (!end && line.startsWith("  ") && stamp != null) { details.add(line.trim()); continue; }
			if (!end && line.trim().isEmpty()) continue;
			if (stamp != null) {
				String copy = kind + (headline.isEmpty() ? "" : "  " + headline);
				int day = days.of(copy);
				if (wanted("station", stamp, headline, firstTime, atFirst)) {
					Event e = base(kind, "station", stamp, day).put("headline", headline).details(details);
					String id = null;
					for (Matcher im = ID.matcher(headline); im.find();) if (known.containsKey(im.group(1))) { if (id != null && !id.equals(im.group(1))) { id = null; break; } id = im.group(1); } // one ship named by id: hers
					if (id != null) e.put("ship", known.get(id) + "." + id).put("ship_name", known.get(id)).put("ship_id", id);
					String human = !headline.isEmpty() ? headline : !details.isEmpty() ? String.join("; ", details) : kind.toLowerCase();
					EventLog.write(v, e.human(human));
					n++;
				}
				stamp = null; details = new ArrayList<String>();
			}
			if (end) break;
			Matcher m = STAMP.matcher(line);
			if (!m.matches()) continue;
			stamp = m.group(1);
			String rest = m.group(2);
			int sp = rest.indexOf("  ");
			kind = sp < 0 ? rest.trim() : rest.substring(0, sp).trim();
			headline = sp < 0 ? "" : rest.substring(sp + 2).trim();
			if (kind.isEmpty()) kind = "NOTE";
		}
		return n;
	}

	private static int convertVoyage(Vault v, File folder, Map<String, String> first, Set<String> atFirst, Map<String, List<String[]>> master) throws IOException {
		File f = new File(folder, VoyageLog.LOG);
		ShipStore.Record r = ShipStore.read(folder);
		if (!f.isFile() || r == null) return 0;
		String which = "voyage:" + r.id, firstTime = first.get(which);
		Days days = new Days(master.get("voyage: " + r.name));
		Event who = Event.of("SHIP").put("ship", r.name + "." + r.id).put("ship_name", r.name).put("ship_id", r.id);
		int n = 0;
		for (String line : new String(SafeFiles.read(f), StandardCharsets.UTF_8).split("\r?\n")) {
			Matcher m = STAMP.matcher(line);
			if (!m.matches()) continue;
			String stamp = m.group(1), text = m.group(2).trim();
			int day = days.of(text);
			if (!wanted(which, stamp, text, firstTime, atFirst)) continue;
			Event e = voyageEvent(text);
			EventLog.write(v, base(e.kind, which, stamp, day).putAll(who).putAll(e).human(text));
			n++;
		}
		return n;
	}
	/**
	 * Each ship's log in her folder filled from the fleet's event log, once (5.76): every entry that names her by id,
	 * in time order, that her log doesn't hold yet. From then on the writer puts each new entry in both.
	 */
	public static void fillShipLogs(Vault v) {
		File mark = new File(v.logsDir(), MARK);
		Properties p = Store.read(mark);
		if ("true".equals(p.getProperty("ship_logs"))) return;
		List<EventLog.Entry> all = EventLog.sorted(EventLog.read(v));
		List<File> folders = new ArrayList<File>(v.shipFolders());
		if (v.cargoHoldDir().isDirectory()) folders.add(v.cargoHoldDir());
		int n = 0;
		for (File d : folders) {
			String id = d.equals(v.cargoHoldDir()) ? Vault.STORAGE_ID : ShipStore.idOf(d);
			if (id == null) continue;
			File f = ShipStore.logFile(d);
			Set<String> has = new HashSet<String>();
			for (EventLog.Entry e : EventLog.read(f)) has.add(e.time + "|" + e.kind + "|" + e.human);
			StringBuilder sb = new StringBuilder();
			for (EventLog.Entry e : all) if (id.equals(e.get("ship_id")) && !has.contains(e.time + "|" + e.kind + "|" + e.human)) { sb.append(EventLog.text(e)); n++; }
			if (sb.length() == 0) continue;
			try { EventLog.append(f, sb.toString()); } catch (Exception e) { log.warn("Could not fill {}: {}", f, e.toString()); }
		}
		p.setProperty("ship_logs", "true");
		p.setProperty("ship_log_entries", Integer.toString(n));
		try { Store.write(mark, p, "The old logs were converted to events once (5.73), and each ship's log filled from them once (5.76); Federation Home Planet never does either again for this fleet"); }
		catch (IOException e) { log.warn("Could not write {}: {}", MARK, e.toString()); }
	}
	/** A voyage log that came with a ship from an older station (5.75): each line an event under her id here, its time its own, its day this career's today. */
	public static void importVoyage(Vault v, Ship s, String from, String text) {
		Event who = VoyageLog.shipFields(s).put("received_from", from).put("converted", true);
		for (String line : text.split("\r?\n")) {
			Matcher m = STAMP.matcher(line);
			if (!m.matches()) continue;
			Event e = voyageEvent(m.group(2).trim());
			EventLog.write(v, Event.of(e.kind).put("log", "voyage").put("time", m.group(1) + ":00").putAll(who).putAll(e).human(m.group(2).trim()));
		}
	}
	private static final Pattern SECTOR = Pattern.compile("Sector (\\d+) reached \\(sectors visited: (\\d+)\\)"), DEFEATED = Pattern.compile("(\\d+) ships? defeated \\((\\d+) in all\\)"),
			HULL = Pattern.compile("Hull (repaired|damaged) to (\\d+)/(\\d+).*"), NEW_RUN = Pattern.compile("Back to sector (\\d+): a new run"), CREW = Pattern.compile("(.+?) \\(([^()]+)\\)");
	/** The kind and fields an old voyage line gives away; the rest is a note with the line as its text. */
	static Event voyageEvent(String text) {
		Matcher m;
		if ((m = SECTOR.matcher(text)).matches()) return Event.of("SECTOR_REACHED").put("sector", Integer.parseInt(m.group(1))).put("visited", Integer.parseInt(m.group(2)));
		if ((m = NEW_RUN.matcher(text)).matches()) return Event.of("NEW_RUN").put("sector", Integer.parseInt(m.group(1)));
		if (text.endsWith(VoyageLog.NEW_JOURNEY)) return Event.of("NEW_RUN").put("sector", 1);
		if (text.startsWith("Crew joined: ") || text.startsWith("Crew lost: ")) {
			Event e = Event.of(text.startsWith("Crew joined") ? "CREW_JOINED" : "CREW_LOST");
			for (String one : text.substring(text.indexOf(':') + 1).split(", ")) {
				Matcher c = CREW.matcher(one.trim());
				if (c.matches()) e.put("crew", c.group(1)).put("race", c.group(2)); else e.put("crew", one.trim());
			}
			return e;
		}
		if ((m = DEFEATED.matcher(text)).matches()) return Event.of("SHIPS_DEFEATED").put("count", Integer.parseInt(m.group(1))).put("total", Integer.parseInt(m.group(2)));
		if ((m = HULL.matcher(text)).matches()) return Event.of(m.group(1).equals("repaired") ? "HULL_REPAIRED" : "HULL_DAMAGED").put("hull", Integer.parseInt(m.group(2))).put("max_hull", Integer.parseInt(m.group(3)));
		if (text.startsWith("Jumped,")) return Event.of("JUMPED");
		if (text.startsWith("Waited,")) return Event.of("WAITED");
		if (text.startsWith("Scrap ") || text.startsWith("Fuel ") || text.startsWith("Missiles ") || text.startsWith("Drone parts ")) return Event.of("SUPPLIES");
		if (text.startsWith("Aboard now: ")) return items("ITEMS_ABOARD", text.substring(12));
		if (text.startsWith("Gone: ")) return items("ITEMS_GONE", text.substring(6));
		if (text.startsWith("Bought at a store: ")) return items("BOUGHT", text.substring(19));
		if (text.startsWith("Picked up: ")) return items("PICKED_UP", text.substring(11));
		if (text.equals("Arrived at a store")) return Event.of("STORE_ARRIVED");
		if (text.startsWith("Beacon: ")) return Event.of("BEACON_HAZARDS");
		if (text.startsWith("Ship met: ")) return Event.of("SHIP_MET").put("met", text.substring(10));
		if (text.startsWith("The Rebel Flagship is alongside")) return Event.of("FLAGSHIP_ALONGSIDE");
		if (text.startsWith("The Rebel Flagship withdrew")) return Event.of("FLAGSHIP_WITHDREW");
		if (text.startsWith("New system: ")) return Event.of("SYSTEM_NEW");
		if (text.startsWith("System removed: ")) return Event.of("SYSTEM_REMOVED");
		if (text.contains(" upgraded to ")) return Event.of(text.startsWith("Reactor") ? "REACTOR_UPGRADED" : "SYSTEM_UPGRADED");
		if (text.contains(" reduced to ")) return Event.of(text.startsWith("Reactor") ? "REACTOR_REDUCED" : "SYSTEM_REDUCED");
		return Event.of("VOYAGE_NOTE").put("text", text);
	}
	private static Event items(String kind, String list) {
		Event e = Event.of(kind);
		for (String one : list.split(", ")) if (!one.trim().isEmpty()) e.put("item", one.trim());
		return e;
	}

	private static final Pattern POINTS = Pattern.compile("([+\u2212-]?\\d+)  (.*)");
	private static int convertReputation(Vault v, String firstTime, Set<String> atFirst, Map<String, List<String[]>> master) throws IOException {
		File f = new File(v.logsDir(), Reputation.LOG);
		if (!f.isFile()) return 0;
		Days days = new Days(master.get("reputation"));
		int n = 0;
		String stamp = null, why = null; int points = 0;
		List<String> details = new ArrayList<String>();
		for (String line : (new String(SafeFiles.read(f), StandardCharsets.UTF_8) + "\n\u0000").split("\r?\n")) {
			boolean end = line.equals("\u0000");
			if (!end && line.startsWith("  ") && stamp != null) { details.add(line.trim()); continue; }
			if (!end && line.trim().isEmpty()) continue;
			if (stamp != null) {
				int day = days.of(Reputation.signed(points) + "  " + why);
				if (wanted("reputation", stamp, why, firstTime, atFirst)) {
					EventLog.write(v, base("REPUTATION", "reputation", stamp, day).put("reason", "other").put("points", points).details(details).human(why));
					n++;
				}
				stamp = null; details = new ArrayList<String>();
			}
			if (end) break;
			Matcher m = STAMP.matcher(line);
			if (!m.matches()) continue;
			Matcher p = POINTS.matcher(m.group(2));
			if (!p.matches()) continue;
			stamp = m.group(1);
			try { points = Integer.parseInt(p.group(1).replace('\u2212', '-').replace("+", "")); } catch (NumberFormatException e) { points = 0; }
			why = p.group(2).trim();
		}
		return n;
	}

	private static int convertDays(Vault v, String firstTime, Set<String> atFirst) throws IOException {
		File f = new File(v.logsDir(), MasterLog.FILE);
		if (!f.isFile()) return 0;
		int n = 0;
		for (String l : new String(SafeFiles.read(f), StandardCharsets.UTF_8).split("\r?\n")) {
			if (!l.startsWith("D\t")) continue;
			String[] w = l.split("\t", 4);
			if (w.length < 4 || w[1].length() < 16) continue;
			String stamp = w[1].substring(0, 16), why = w[3];
			int day; try { day = Integer.parseInt(w[2].trim()); } catch (NumberFormatException e) { continue; }
			String human = "A day passed: " + why.replaceAll(" \\(\\d+ counted together\\)", "") + ".";
			if (!wanted("clock", stamp, human, firstTime, atFirst)) continue;
			EventLog.write(v, base("DAY", "clock", stamp, day).put("why", why).human(human));
			n++;
		}
		return n;
	}
}
