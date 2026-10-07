package homeplanet.vault;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import homeplanet.core.SafeFiles;

/**
 * The career's days and stardates. Each career had a master log (heromedel, 5.17, logs/master.log): every day its clock
 * counted and why, and a copy of every entry its logs got. Since 5.93 nothing writes it: the days are the event log's
 * DAY entries (the Captain's Log and the Cargo Bay's day read them), and the entries are the event log's own. The old
 * file stays, read only by the conversion of a fleet's old logs (LogConvert).
 *
 * A career's day 1 is the day this was first asked of it: a new career's first moment, or the first look at an older
 * one on 5.17 (its earlier entries are Prior to 1.1.1.1). The day is stored as a plain number and shown as a stardate,
 * year.month.week.day: 7-day weeks, 28-day months, 13-month years. A day of 0 or less is Prior.
 */
public final class MasterLog {
	private MasterLog() { }

	static final String FILE = "master.log";
	/** Why a day passed (the reasons the station itself checks). */
	public static final String CARGO_BAY = "business in the Cargo Bay";
	public static final int WEEK = 7, MONTH = 28, YEAR = 13 * MONTH;

	/** The clock's count as its own file has it (no lock: Vault.beaconsSeen without it). */
	private static int clock(Vault v) { return Clock.num(v, "beacons", 0); }
	/** The clock's count on the career's day 1 (Stardate 1.1.1.1; entries before it are Prior), written down the first time it's asked. */
	public static synchronized int start(Vault v) {
		int at = Clock.num(v, "start", -1);
		if (at >= 0) return at;
		at = clock(v);
		try { Clock.set(v, "start", Integer.toString(at)); } catch (IOException e) { org.slf4j.LoggerFactory.getLogger(MasterLog.class).warn("Could not record the career's day 1: {}", e.toString()); }
		return at;
	}
	/** The career's day now: 1 on its first day. */
	public static int today(Vault v) { return clock(v) - start(v) + 1; }
	/** The career's day at this count of the clock. */
	public static int dayAt(Vault v, int clock) { return clock - start(v) + 1; }
	/** A day as a stardate, year.month.week.day; "Prior to 1.1.1.1" for a day before the first. */
	public static String stardate(int day) {
		if (day < 1) return "Prior to 1.1.1.1";
		int n = day - 1;
		return (n / YEAR + 1) + "." + (n % YEAR / MONTH + 1) + "." + (n % MONTH / WEEK + 1) + "." + (n % WEEK + 1);
	}

	/** A day the clock counted, and why (the clock already moved: this is its count after). */
	public static void day(Vault v, int clockAfter, String why) {
		int day = dayAt(v, clockAfter);
		homeplanet.core.EventLog.write(v, homeplanet.core.Event.of("DAY").put("log", "clock").put("day", day).put("clock", clockAfter).put("why", why)
				.human("A day passed: " + why.replaceAll(" \\(\\d+ counted together\\)", "") + "."));
	}
	/**
	 * Business in the Cargo Bay passes a day, unless the last day counted was already one (heromedel, 5.17: nothing else
	 * moved the clock since, so a string of Saves is one visit). True if a day passed.
	 */
	public static boolean businessDay(Vault v) {
		if (CARGO_BAY.equals(lastDayWhy(v))) return false;
		v.countBeacon(CARGO_BAY);
		return true;
	}
	/** Why the last day counted passed, or null if none is noted. */
	public static synchronized String lastDayWhy(Vault v) {
		String why = null; // the DAY events (5.93; the master log's D lines before)
		for (homeplanet.core.EventLog.Entry e : homeplanet.core.EventLog.sorted(homeplanet.core.EventLog.read(v))) if (e.kind.equals("DAY")) why = e.get("why", why);
		return why;
	}

	/** Why each day began (what moved the clock onto it: the DAY events, 5.74), by day. */
	public static synchronized Map<Integer, String> dayReasons(Vault v) {
		Map<Integer, String> out = new LinkedHashMap<Integer, String>();
		for (homeplanet.core.EventLog.Entry e : homeplanet.core.EventLog.sorted(homeplanet.core.EventLog.read(v))) {
			if (!e.kind.equals("DAY") || e.day < 1 || e.get("why") == null) continue;
			out.put(e.day, e.get("why"));
		}
		return out;
	}
	/** An entry, read back. */
	public static final class Entry {
		public final String real, log, text;
		public final int day;
		Entry(String real, int day, String log, String text) { this.real = real; this.day = day; this.log = log; this.text = text; }
	}
	/** The entries by day, oldest first, from the event log (5.74; the E lines before): entries with no proper day (Prior) are left out. */
	public static synchronized Map<Integer, List<Entry>> byDay(Vault v) {
		Map<Integer, List<Entry>> out = new LinkedHashMap<Integer, List<Entry>>();
		for (homeplanet.core.EventLog.Entry x : homeplanet.core.EventLog.sorted(homeplanet.core.EventLog.read(v))) {
			Entry e = of(x);
			if (e == null || e.day < 1) continue;
			List<Entry> d = out.get(e.day);
			if (d == null) out.put(e.day, d = new ArrayList<Entry>());
			d.add(e);
		}
		return out;
	}
	/**
	 * An event as the master log copied it (the readers that word the day, the Captain's Log first, read this form):
	 * a station entry as "KIND  headline / detail / detail" (the kind with spaces, as the station log writes it), a
	 * voyage entry as its line under "voyage: <her name>", a reputation entry as "+5  why / detail"; null for the rest.
	 */
	public static Entry of(homeplanet.core.EventLog.Entry x) {
		String log = x.get("log", "");
		if (log.equals("station")) {
			StringBuilder t = new StringBuilder(x.kind.replace('_', ' '));
			String head = x.get("headline", x.human);
			if (!head.isEmpty()) t.append("  ").append(head);
			for (String d : x.all("detail")) t.append(" / ").append(d);
			for (int i = 1; x.get("detail." + i) != null; i++) t.append(" / ").append(x.get("detail." + i));
			return new Entry(x.time, x.day, "station", t.toString());
		}
		if (log.equals("voyage")) return new Entry(x.time, x.day, "voyage: " + x.get("ship_name", ""), x.human);
		if (log.equals("reputation")) {
			StringBuilder t = new StringBuilder(Reputation.signed(x.num("points", 0))).append("  ").append(x.human);
			for (int i = 1; x.get("detail." + i) != null; i++) t.append(" / ").append(x.get("detail." + i));
			return new Entry(x.time, x.day, "reputation", t.toString());
		}
		return null;
	}
}
