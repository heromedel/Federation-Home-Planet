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
	/** In the marker: the station that put the converted entries on their own days (5.81), by converting or by repairDays. */
	static final String DAYS_FIXED = "days_fixed";
	private static final Pattern STAMP = Pattern.compile("(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d)  (.*)");

	public static boolean done(Vault v) { return done(v.root); }
	/** Whether a fleet's old logs were read in, by its folder (a fleet not in use). */
	public static boolean done(File fleetRoot) { return new File(new File(fleetRoot, "logs"), MARK).isFile(); }

	/** Converts what has no event yet, and writes the marker. Never throws: a log it can't read is skipped and said in the debug log. */
	public static void run(Vault v) {
		if (done(v)) return;
		Bounds b = new Bounds(EventLog.read(v));
		Map<String, List<String[]>> master = masterCopies(v); // log -> {text, day, time}, in order
		int[] n = convertAll(v, b, master, null);
		int station = n[0], voyage = n[1], reputation = n[2], days = n[3];
		Properties p = new Properties();
		p.setProperty("station", HomePlanet.version()); p.setProperty("entries_station", Integer.toString(station)); p.setProperty("entries_voyage", Integer.toString(voyage));
		p.setProperty("entries_reputation", Integer.toString(reputation)); p.setProperty("days", Integer.toString(days));
		p.setProperty(DAYS_FIXED, HomePlanet.version()); // converted with each entry on its own day: nothing for repairDays to do
		try { Store.write(new File(v.logsDir(), MARK), p, "The old logs were converted to events once (5.73); Federation Home Planet never does it again for this fleet"); }
		catch (IOException e) { log.warn("Could not write {}: {}", MARK, e.toString()); }
		int all = station + voyage + reputation + days;
		if (all > 0) HistoryLog.entry("LOGS_CONVERTED", "the old logs were read into the event log once: " + station + " station entries, " + voyage + " voyage entries, " + reputation + " reputation entries, " + days + " days", null,
				Event.of("LOGS_CONVERTED").put("entries_station", station).put("entries_voyage", voyage).put("entries_reputation", reputation).put("days", days)
						.human("The station read its old logs into its records once."));
	}
	/** The earliest event each old log already has (entries before it are the ones without events), and the entries at that minute. */
	private static final class Bounds {
		final Map<String, String> first = new HashMap<String, String>(); // "station", "reputation", "clock", "voyage:<id>" -> earliest time
		final Set<String> atFirst = new HashSet<String>(); // "<log>|<minute>|<human>": entries at the boundary minute, already there
		Bounds(List<EventLog.Entry> have) {
			for (EventLog.Entry e : have) {
				String which = which(e);
				if (which.isEmpty()) continue;
				String was = first.get(which);
				if (was == null || e.time.compareTo(was) < 0) first.put(which, e.time);
			}
			for (EventLog.Entry e : have) {
				String which = which(e);
				String f = first.get(which);
				if (f != null && e.time.length() >= 16 && e.time.substring(0, 16).equals(f.substring(0, 16))) atFirst.add(which + "|" + f.substring(0, 16) + "|" + (which.equals("station") ? e.get("headline", e.human) : e.human));
			}
		}
		private static String which(EventLog.Entry e) {
			String which = e.get("log", e.kind.equals("DAY") ? "clock" : "");
			return which.equals("voyage") ? "voyage:" + e.get("ship_id", "") : which;
		}
	}
	/** Every old log converted, each event written (or, with a sink, collected instead): the counts of station, voyage, reputation and day entries. */
	private static int[] convertAll(Vault v, Bounds b, Map<String, List<String[]>> master, List<Event> sink) {
		int station = 0, voyage = 0, reputation = 0, days = 0;
		Map<String, String> known = new HashMap<String, String>(); // every ship the fleet has or remembers: id -> name, so a converted entry that names her by id goes into her log too (5.77)
		for (File d : v.shipFolders()) { ShipStore.Record r = ShipStore.read(d); if (r != null) known.put(r.id, r.name); }
		try { station = convertStation(v, b.first.get("station"), b.atFirst, master, known, sink); } catch (Exception e) { log.warn("The station log could not be converted: {}", e.toString()); }
		Days voyageDays = new Days(voyageCopies(master)); // one for all her logs: a renamed ship's copies sit under her old name
		for (File d : v.shipFolders()) {
			try { voyage += convertVoyage(v, d, b.first, b.atFirst, voyageDays, sink); } catch (Exception e) { log.warn("{}'s voyage log could not be converted: {}", d.getName(), e.toString()); }
		}
		try { reputation = convertReputation(v, b.first.get("reputation"), b.atFirst, master, sink); } catch (Exception e) { log.warn("The reputation log could not be converted: {}", e.toString()); }
		try { days = convertDays(v, b.first.get("clock"), b.atFirst, sink); } catch (Exception e) { log.warn("The clock's days could not be converted: {}", e.toString()); }
		return new int[] {station, voyage, reputation, days};
	}
	private static void write(Vault v, Event e, List<Event> sink) { if (sink != null) sink.add(e); else EventLog.write(v, e); }

	/** The master log's E lines by log: what each entry said, its day and its time. */
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
				list.add(new String[] {w[4], w[2].trim(), w[1].trim()});
			}
		} catch (IOException e) { log.warn("Could not read the master log: {}", e.toString()); }
		return out;
	}
	/** Every ship's voyage copies together, in time order. */
	private static List<String[]> voyageCopies(Map<String, List<String[]>> master) {
		List<String[]> out = new ArrayList<String[]>();
		for (Map.Entry<String, List<String[]>> e : master.entrySet()) if (e.getKey().startsWith("voyage: ")) out.addAll(e.getValue());
		java.util.Collections.sort(out, new java.util.Comparator<String[]>() { public int compare(String[] a, String[] b) { return a[2].compareTo(b[2]); } });
		return out;
	}
	/**
	 * The day of an entry: the master log's copy written in the same minute (or the next: the two logs' clocks were read a
	 * moment apart) that starts with its text, each copy used once; 0 when there is none, as for everything from before
	 * the master log began (5.17): Prior. Matched by text alone and in order, a common line ("LOADED  (refresh)") from
	 * before then took a copy from days later, the rest ran out of copies, and over two thousand old entries were put on
	 * the day of the conversion (heromedel's Captain's Log, 5.81).
	 */
	private static final class Days {
		private final List<String[]> copies; private final boolean[] used;
		Days(List<String[]> copies) { this.copies = copies == null ? new ArrayList<String[]>() : copies; this.used = new boolean[this.copies.size()]; }
		int of(String stamp, String text) {
			String next = nextMinute(stamp);
			for (int i = firstAt(stamp); i < copies.size(); i++) {
				String m = minute(copies.get(i));
				if (m.compareTo(next) > 0) break;
				if (used[i] || m.compareTo(stamp) < 0 || !copies.get(i)[0].startsWith(text)) continue;
				used[i] = true;
				try { return Math.max(0, Integer.parseInt(copies.get(i)[1])); } catch (NumberFormatException e) { return 0; }
			}
			return 0;
		}
		/** The first copy at or after this minute (the master log is written in time order). */
		private int firstAt(String stamp) {
			int lo = 0, hi = copies.size();
			while (lo < hi) { int mid = (lo + hi) >>> 1; if (minute(copies.get(mid)).compareTo(stamp) < 0) lo = mid + 1; else hi = mid; }
			return lo;
		}
		private static String minute(String[] copy) { return copy.length > 2 && copy[2].length() >= 16 ? copy[2].substring(0, 16) : ""; }
	}
	/** "yyyy-MM-dd HH:mm" a minute later. */
	static String nextMinute(String stamp) {
		try {
			java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
			return f.format(new java.util.Date(f.parse(stamp).getTime() + 60000L));
		} catch (java.text.ParseException e) { return stamp; }
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
		return e.put("day", Math.max(0, day)); // 0 is Prior; with no day at all, the writer would put it on today's (5.81)
	}

	private static final Pattern ID = Pattern.compile("\\b([0-9a-f]{16})\\b");
	private static int convertStation(Vault v, String firstTime, Set<String> atFirst, Map<String, List<String[]>> master, Map<String, String> known, List<Event> sink) throws IOException {
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
				int day = days.of(stamp, copy);
				if (wanted("station", stamp, headline, firstTime, atFirst)) {
					Event e = base(kind, "station", stamp, day).put("headline", headline).details(details);
					String id = null;
					for (Matcher im = ID.matcher(headline); im.find();) if (known.containsKey(im.group(1))) { if (id != null && !id.equals(im.group(1))) { id = null; break; } id = im.group(1); } // one ship named by id: hers
					if (id != null) e.put("ship", known.get(id) + "." + id).put("ship_name", known.get(id)).put("ship_id", id);
					String human = !headline.isEmpty() ? headline : !details.isEmpty() ? String.join("; ", details) : kind.toLowerCase();
					write(v, e.human(human), sink);
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

	private static int convertVoyage(Vault v, File folder, Map<String, String> first, Set<String> atFirst, Days days, List<Event> sink) throws IOException {
		File f = new File(folder, VoyageLog.LOG);
		ShipStore.Record r = ShipStore.read(folder);
		if (!f.isFile() || r == null) return 0;
		String which = "voyage:" + r.id, firstTime = first.get(which);
		Event who = Event.of("SHIP").put("ship", r.name + "." + r.id).put("ship_name", r.name).put("ship_id", r.id);
		int n = 0;
		for (String line : new String(SafeFiles.read(f), StandardCharsets.UTF_8).split("\r?\n")) {
			Matcher m = STAMP.matcher(line);
			if (!m.matches()) continue;
			String stamp = m.group(1), text = m.group(2).trim();
			int day = days.of(stamp, text);
			if (!wanted(which, stamp, text, firstTime, atFirst)) continue;
			Event e = voyageEvent(text);
			write(v, base(e.kind, which, stamp, day).putAll(who).putAll(e).human(text), sink);
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
	/**
	 * The days of a fleet's converted entries put right, once (heromedel, 5.81): converted before 5.81, an entry from before
	 * the master log began, and many after it, were put on the day of the conversion (see Days), and a ship's voyage
	 * from another station on the day she arrived. The old logs, left as they were, are converted again into a list (not
	 * written), and each converted entry in events.log and in the ship logs takes its day from its twin there (time,
	 * kind and words alike), a received ship's Prior. The one time the event log is rewritten rather than appended to:
	 * only converted entries' stardates and days change, in one journal note. Never throws.
	 */
	public static void repairDays(Vault v) {
		File mark = new File(v.logsDir(), MARK);
		if (!mark.isFile()) return; // nothing converted yet: run() converts with the right days
		Properties p = Store.read(mark);
		if (p.getProperty(DAYS_FIXED) != null) return;
		try {
			List<EventLog.Entry> native_ = new ArrayList<EventLog.Entry>();
			for (EventLog.Entry e : EventLog.read(v)) if (!"true".equals(e.get("converted"))) native_.add(e);
			List<Event> again = new ArrayList<Event>();
			convertAll(v, new Bounds(native_), masterCopies(v), again);
			Map<String, List<Integer>> dayOf = new HashMap<String, List<Integer>>(); // time|kind|words -> the days, in order
			for (Event e : again) {
				String k = e.get("time") + "|" + e.kind + "|" + e.human().replace('\r', ' ').replace('\n', ' ').trim();
				List<Integer> l = dayOf.get(k);
				if (l == null) dayOf.put(k, l = new ArrayList<Integer>());
				l.add(Integer.parseInt(e.get("day")));
			}
			List<File> files = new ArrayList<File>();
			files.add(EventLog.file(v));
			for (File d : v.shipFolders()) files.add(ShipStore.logFile(d));
			if (v.cargoHoldDir().isDirectory()) files.add(ShipStore.logFile(v.cargoHoldDir()));
			Journal.Note note = Journal.begin(v, "LOG_DAYS_REPAIRED");
			int[] count = new int[3]; // moved, now Prior, not found
			int changedFiles = 0;
			for (File f : files) {
				if (!f.isFile()) continue;
				String text = new String(SafeFiles.read(f), StandardCharsets.UTF_8);
				String fixed = withDays(text, dayOf, f.equals(EventLog.file(v)) ? count : new int[3]);
				if (fixed.equals(text)) continue;
				note.replace(f, fixed.getBytes(StandardCharsets.UTF_8));
				changedFiles++;
			}
			note.commit();
			p.setProperty(DAYS_FIXED, HomePlanet.version());
			Store.write(mark, p, "The old logs were converted to events once (5.73), and each ship's log filled from them once (5.76); Federation Home Planet never does either again for this fleet");
			if (count[0] + count[1] > 0) HistoryLog.entry("LOG_DAYS_REPAIRED", "the old entries read into the event log put on their own days: " + count[0] + " moved to their day, " + count[1] + " from before the stardates (Prior)"
					+ (count[2] > 0 ? ", " + count[2] + " left as they were" : ""), null,
					Event.of("LOG_DAYS_REPAIRED").put("entries_moved", count[0]).put("entries_prior", count[1]).put("entries_unmatched", count[2]).put("files", changedFiles)
							.human("The station put its old log entries back on their own days."));
		} catch (Exception e) { log.warn("The converted log entries' days could not be put right: {}", e.toString()); }
	}
	/** The text of an event log with each converted entry's stardate and day from its twin (a received ship's: Prior); count: moved, Prior, not found. */
	static String withDays(String text, Map<String, List<Integer>> dayOf, int[] count) {
		Map<String, Integer> used = new HashMap<String, Integer>();
		String[] lines = text.split("\n", -1);
		for (int i = 0; i + 1 < lines.length; i++) {
			String machine = lines[i].endsWith("\r") ? lines[i].substring(0, lines[i].length() - 1) : lines[i];
			if (machine.length() < 22 || machine.indexOf(" | ") != 19) continue;
			String human = lines[i + 1].endsWith("\r") ? lines[i + 1].substring(0, lines[i + 1].length() - 1) : lines[i + 1];
			List<EventLog.Entry> one = EventLog.parse(machine + "\n" + human);
			if (one.size() != 1 || !"true".equals(one.get(0).get("converted"))) continue;
			EventLog.Entry e = one.get(0);
			int day;
			if (e.get("received_from") != null) day = 0;
			else {
				String k = e.time + "|" + e.kind + "|" + e.human;
				List<Integer> l = dayOf.get(k);
				int at = used.containsKey(k) ? used.get(k) : 0;
				if (l == null || at >= l.size()) { count[2]++; continue; }
				used.put(k, at + 1);
				day = l.get(at);
			}
			if (day == e.day) continue;
			count[day < 1 ? 1 : 0]++;
			String[] head = machine.split(" \\| ", 4);
			if (head.length < 3) continue;
			head[1] = day < 1 ? "prior" : MasterLog.stardate(day);
			String fields = head.length == 4 ? head[3] : "";
			String f2 = fields.replaceFirst("(^| )day=-?\\d+(?= |$)", "$1day=" + day);
			if (f2.equals(fields)) f2 = fields + (fields.isEmpty() ? "" : " ") + "day=" + day;
			String rebuilt = head[0] + " | " + head[1] + " | " + head[2] + (f2.isEmpty() ? "" : " | " + f2);
			lines[i] = rebuilt + (lines[i].endsWith("\r") ? "\r" : "");
		}
		return String.join("\n", lines);
	}
	/**
	 * A voyage log that came with a ship from an older station (5.75): each line an event under her id here, its time its
	 * own, Prior in this career (5.81): it was another commander's, and on the day she arrived it filled this Captain's Log.
	 */
	public static void importVoyage(Vault v, Ship s, String from, String text) {
		Event who = VoyageLog.shipFields(s).put("received_from", from).put("converted", true);
		for (String line : text.split("\r?\n")) {
			Matcher m = STAMP.matcher(line);
			if (!m.matches()) continue;
			Event e = voyageEvent(m.group(2).trim());
			EventLog.write(v, Event.of(e.kind).put("log", "voyage").put("time", m.group(1) + ":00").put("day", 0).putAll(who).putAll(e).human(m.group(2).trim()));
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
	private static int convertReputation(Vault v, String firstTime, Set<String> atFirst, Map<String, List<String[]>> master, List<Event> sink) throws IOException {
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
				int day = days.of(stamp, Reputation.signed(points) + "  " + why);
				if (wanted("reputation", stamp, why, firstTime, atFirst)) {
					write(v, base("REPUTATION", "reputation", stamp, day).put("reason", "other").put("points", points).details(details).human(why), sink);
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

	private static int convertDays(Vault v, String firstTime, Set<String> atFirst, List<Event> sink) throws IOException {
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
			write(v, base("DAY", "clock", stamp, day).put("why", why).human(human), sink);
			n++;
		}
		return n;
	}
}
