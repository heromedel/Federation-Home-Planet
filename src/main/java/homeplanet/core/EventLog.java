package homeplanet.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import homeplanet.vault.MasterLog;
import homeplanet.vault.Vault;

/**
 * The event log, events.log in the career folder (CLAUDE.md, "Log lines"; Overhaul 6.0, Phase 1 step 9): every entry
 * any of the station's logs gets, as two lines each. The machine line: the real time, the stardate, the kind, then the
 * fields as key=value; the human line, written from it. Append-only, never rewritten. Until 6.0 switches the readers,
 * the old logs keep their own files and wording beside it; one day they read this instead.
 *
 * <pre>
 * 2026-10-07 14:02:11 | 1.2.3.4 | CREW_MOVE | crew=Bob.17 race=human ... to="ship:Shippy McShipface.c77a" station=6.00
 * Bob was transferred to the Shippy McShipface.
 * </pre>
 *
 * Written straight to the file, never through SafeFiles' notice (writing a log never makes the Space Dock rebuild), and
 * without the Vault's lock, so it can be written from anywhere.
 */
public final class EventLog {
	private static final Logger log = LoggerFactory.getLogger(EventLog.class);
	private EventLog() { }

	public static final String FILE = "events.log";
	private static final String NL = System.getProperty("line.separator");
	private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public static File file(Vault v) { return new File(v.logsDir(), FILE); }
	/** A fleet's event log by its folder (logs/ since 5.71, the root before): for a fleet not in use. */
	public static File fileIn(File fleetRoot) {
		File f = new File(new File(fleetRoot, "logs"), FILE);
		return f.isFile() || !new File(fleetRoot, FILE).isFile() ? f : new File(fleetRoot, FILE);
	}
	/** The entries in time order (a converted entry sits in the file where it was written, not where it happened), the file's order deciding a tie. */
	public static List<Entry> sorted(List<Entry> es) {
		List<Entry> out = new ArrayList<Entry>(es);
		java.util.Collections.sort(out, new java.util.Comparator<Entry>() { public int compare(Entry a, Entry b) { return a.time.compareTo(b.time); } });
		return out;
	}
	/** The entries of one of the old logs (`log=station`, `voyage`, `reputation`, `clock`), in time order. */
	public static List<Entry> ofLog(List<Entry> es, String log) {
		List<Entry> out = new ArrayList<Entry>();
		for (Entry e : es) if (log.equals(e.get("log"))) out.add(e);
		return sorted(out);
	}
	/** One ship's voyage entries, by her id, in time order. */
	public static List<Entry> voyage(List<Entry> es, String shipId) {
		List<Entry> out = new ArrayList<Entry>();
		for (Entry e : es) if ("voyage".equals(e.get("log")) && shipId.equals(e.get("ship_id"))) out.add(e);
		return sorted(out);
	}

	/** Writes the event's two lines. Never throws; with no fleet open, nothing is written (the debug log notes it). */
	public static void write(Vault v, Event e) { write(v, v == null ? null : file(v), e); }
	/** As above, into another log of the same shape (a ship's own, in her folder); the stardate is the fleet's. */
	public static void write(Vault v, File to, Event e) {
		if (v == null) { log.debug("No fleet open for the event {}: {}", e.kind, e.human()); return; }
		// an entry about something that happened earlier (a journal note finished at start-up) carries its own time and day, and the columns follow them
		int day = e.get("day") != null ? dayOf(e.get("day"), MasterLog.today(v)) : MasterLog.today(v);
		String stamp = e.get("time") != null && e.get("time").matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d") ? e.get("time") : null;
		if (stamp == null) synchronized (STAMP) { stamp = STAMP.format(new Date()); }
		String machine = stamp + " | " + (day < 1 ? "prior" : MasterLog.stardate(day)) + " | " + e.kind + " | " + e.fieldText()
				+ (e.get("day") == null ? " day=" + day : "") + " station=" + HomePlanet.version();
		String human = e.human().replace('\r', ' ').replace('\n', ' ').trim();
		append(to, machine + NL + human + NL);
	}
	/** An entry's two lines as the log holds them (to carry entries elsewhere: a ship's package, 5.75). */
	public static String text(Entry e) {
		Event f = Event.of(e.kind);
		for (String[] kv : e.fields()) f.put(kv[0], kv[1]);
		return e.time + " | " + e.stardate + " | " + e.kind + " | " + f.fieldText() + NL + e.human.replace('\r', ' ').replace('\n', ' ') + NL;
	}
	private static int dayOf(String s, int dflt) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return dflt; } }
	private static synchronized void append(File f, String text) {
		Writer w = null;
		try {
			w = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8);
			w.write(text);
		} catch (IOException e) {
			log.warn("Could not write to {}: {}", f, e.toString());
		} finally {
			try { if (w != null) w.close(); } catch (IOException e) { }
		}
	}

	// ---- reading: the one parser ----

	/** An entry read back: the machine line's parts, and the human line under it. */
	public static final class Entry {
		public final String time, stardate, kind, human;
		public final int day;
		private final List<String[]> fields;
		Entry(String time, String stardate, String kind, List<String[]> fields, String human) {
			this.time = time; this.stardate = stardate; this.kind = kind; this.fields = fields; this.human = human;
			int d = -1;
			for (String[] f : fields) if (f[0].equals("day")) { try { d = Integer.parseInt(f[1]); } catch (NumberFormatException e) { } break; }
			this.day = d;
		}
		/** The first value of a key, or null. */
		public String get(String key) {
			for (String[] f : fields) if (f[0].equals(key)) return f[1];
			return null;
		}
		public String get(String key, String dflt) { String s = get(key); return s == null ? dflt : s; }
		public int num(String key, int dflt) {
			try { return Integer.parseInt(get(key, "").trim()); } catch (NumberFormatException e) { return dflt; }
		}
		/** Every value of a key, in order. */
		public List<String> all(String key) {
			List<String> out = new ArrayList<String>();
			for (String[] f : fields) if (f[0].equals(key)) out.add(f[1]);
			return out;
		}
		public List<String[]> fields() { return new ArrayList<String[]>(fields); }
	}

	/** The log's entries, oldest first (empty if there's none). Lines that aren't an entry's are skipped. */
	public static List<Entry> read(Vault v) { return read(file(v)); }
	public static List<Entry> read(File f) {
		if (f == null || !f.isFile()) return new ArrayList<Entry>();
		try { return parse(new String(SafeFiles.read(f), StandardCharsets.UTF_8)); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); return new ArrayList<Entry>(); }
	}
	/** The entries in this text. A machine line is one that starts with a time stamp and " | "; the line after it is its human line. */
	public static List<Entry> parse(String text) {
		List<Entry> out = new ArrayList<Entry>();
		String[] lines = text.split("\r?\n");
		for (int i = 0; i < lines.length; i++) {
			if (!isMachine(lines[i])) continue;
			String machine = lines[i];
			String human = i + 1 < lines.length && !isMachine(lines[i + 1]) ? lines[++i] : "";
			Entry e = entry(machine, human);
			if (e != null) out.add(e);
		}
		return out;
	}
	private static boolean isMachine(String l) {
		return l.length() > 22 && Character.isDigit(l.charAt(0)) && l.charAt(4) == '-' && l.charAt(10) == ' ' && l.startsWith(" | ", 19);
	}
	/** One machine line taken apart: "time | stardate | KIND | fields". */
	static Entry entry(String machine, String human) {
		String[] head = machine.split(" \\| ", 4);
		if (head.length < 3) return null;
		String fieldText = head.length == 4 ? head[3] : "";
		return new Entry(head[0], head[1], head[2], fields(fieldText), human);
	}
	/** The fields of a machine line: key=value pairs split on spaces, a value in quotes read with its escapes (\" \\ \n \r \t). */
	static List<String[]> fields(String s) {
		List<String[]> out = new ArrayList<String[]>();
		int i = 0, n = s.length();
		while (i < n) {
			while (i < n && s.charAt(i) == ' ') i++;
			if (i >= n) break;
			int eq = s.indexOf('=', i);
			if (eq < 0) break;
			String key = s.substring(i, eq);
			i = eq + 1;
			StringBuilder v = new StringBuilder();
			if (i < n && s.charAt(i) == '"') {
				i++;
				while (i < n && s.charAt(i) != '"') {
					char c = s.charAt(i++);
					if (c == '\\' && i < n) {
						char x = s.charAt(i++);
						v.append(x == 'n' ? '\n' : x == 'r' ? '\r' : x == 't' ? '\t' : x);
					} else v.append(c);
				}
				i++; // the closing quote
			} else {
				while (i < n && s.charAt(i) != ' ') v.append(s.charAt(i++));
			}
			if (!key.isEmpty()) out.add(new String[] {key, v.toString()});
		}
		return out;
	}
}
