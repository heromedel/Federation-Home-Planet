package homeplanet.vault;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Captain's Log as a story (heromedel, 5.18; docs/STYLE.md, "The Captain's Log"): each day's entries from the
 * master log, sorted into kinds, repeats merged, written short in the captain's own voice. Things that simply happened
 * go in beside what the captain did. On a day of more than one line, the action that moved the day on comes last and
 * begins "Then". Prices, reputation and who-went-where are the details, shown only when asked. Housekeeping, reputation
 * as its own lines and the reasons days pass are never told, so it never says what a day is counted in. Built each time
 * the log is opened: nothing of it is stored, so better wording reads back over old days too.
 */
public final class CaptainsLog {
	private CaptainsLog() { }

	/** One line of the log: its words, its details, and what kind of thing it was (to find the day's last action). */
	public static final class Line {
		public String text;
		public final List<String> details = new ArrayList<String>();
		final String kind;
		/** The captain did it ("Sold four missiles": "Then I sold…"), or it happened ("The Kestrel pressed on": "Then the Kestrel…"). */
		final boolean byMe;
		/** For merging: lines with the same key on a day are one line. */
		final String key;
		Line(String kind, String key, boolean byMe, String text) { this.kind = kind; this.key = key; this.byMe = byMe; this.text = text; }
		/** The whole crew did it ("We jumped to sector 3": "Then we jumped…"). */
		boolean byWe;
		/** Aboard a ship in FTL (her voyage log), or at The Home Planet Station (everything else). */
		boolean aboard;
		/** For a ship's jumps: the sector she reached (0: none), and whether she came to a station. */
		int sector;
		boolean station;
		/** For a ship's jumps: what her beacon held ("a nebula", "a star") and the ships met there ("a Rock pirate"). */
		final List<String> hazards = new ArrayList<String>(), met = new ArrayList<String>();
		/** For a ship's jumps: to a distress beacon (6.21): "We responded to a distress signal." */
		boolean distress;
		/** For a day's fight: the ships beaten, where the station saw who ("the rebel ship"; 6.09). */
		final List<String> beaten = new ArrayList<String>();
		// what's merged into it
		final Map<String, Integer> things = new LinkedHashMap<String, Integer>();
		int count;
		String ship;
	}
	/** A day, or a stretch of quiet days (from to to, with no lines). */
	public static final class Day {
		public final int from, to;
		public final List<Line> lines;
		Day(int from, int to, List<Line> lines) { this.from = from; this.to = to; this.lines = lines; }
		/** The heading: "Stardate Today" for the first day, "Stardate 1.1.1.2", or a quiet stretch's "Stardates a – b". */
		public String heading() {
			if (from == to) return from == 1 ? "Stardate Today" : "Stardate " + MasterLog.stardate(from);
			return "Stardates " + (from == 1 ? "Today" : MasterLog.stardate(from)) + " – " + MasterLog.stardate(to);
		}
	}

	/** The career's days from its first to today, the quiet ones folded together. */
	public static List<Day> days(Vault v) {
		Map<Integer, List<MasterLog.Entry>> byDay = MasterLog.byDay(v);
		Map<Integer, String> began = MasterLog.dayReasons(v);
		int today = Math.max(1, MasterLog.today(v));
		for (int d : byDay.keySet()) today = Math.max(today, d);
		List<Day> out = new ArrayList<Day>();
		int quiet = 0;
		Boolean wasAboard = null; // where the last day with lines left the captain
		Map<String, Line> lastJump = new LinkedHashMap<String, Line>(); // each ship's latest jump line, for what's learned of her beacon later
		for (int d = 1; d <= today; d++) {
			List<MasterLog.Entry> es = byDay.get(d);
			List<Line> later = new ArrayList<Line>();
			List<Line> lines = es == null ? new ArrayList<Line>() : day(es, began.get(d + 1), wasAboard, later);
			for (Line b : later) { // a ship met after the jump: it goes with that jump, on whatever day it was
				Line j = lastJump.get(b.ship);
				if (j == null) continue;
				for (String x : b.hazards) if (!j.hazards.contains(x)) j.hazards.add(x);
				for (String x : b.met) if (!j.met.contains(x)) j.met.add(x);
				if (b.distress) j.distress = true;
				boolean then = j.text.startsWith("Then ");
				j.text = then ? "Then " + lower(jumpText(j)) : jumpText(j);
			}
			for (Line l : lines) if (l.kind.equals("move")) lastJump.put(l.ship, l);
			if (lines.isEmpty()) { if (quiet == 0) quiet = d; continue; }
			wasAboard = lines.get(lines.size() - 1).aboard;
			if (quiet > 0) { out.add(new Day(quiet, d - 1, lines(0))); quiet = 0; }
			out.add(new Day(d, d, lines));
		}
		if (quiet > 0) out.add(new Day(quiet, today, lines(0)));
		return out;
	}
	private static List<Line> lines(int n) { return new ArrayList<Line>(n); }

	/** One day's lines: each entry read, the repeats merged, and the action that moved the day on (why the next began) last, with "Then". */
	static List<Line> day(List<MasterLog.Entry> entries, String endedBy) { return day(entries, endedBy, null); }
	/** As above, knowing where the day before left the captain (aboard, at the station, or not known). */
	static List<Line> day(List<MasterLog.Entry> entries, String endedBy, Boolean wasAboard) { return day(entries, endedBy, wasAboard, new ArrayList<Line>()); }
	/** As above; what was learned of a beacon reached on an earlier day goes into {@code later}, for that day's jump. */
	static List<Line> day(List<MasterLog.Entry> entries, String endedBy, Boolean wasAboard, List<Line> later) {
		Map<String, Line> merged = new LinkedHashMap<String, Line>();
		for (MasterLog.Entry e : entries) read(e, merged);
		List<Line> lines = new ArrayList<Line>();
		boolean boughtAboard = false;
		for (Line l : merged.values()) if (l.kind.equals("ftlbuy")) boughtAboard = true;
		for (Line l : merged.values()) {
			if (l.kind.equals("beacon")) { later.add(l); continue; }
			if (boughtAboard && l.kind.equals("work") && l.things.isEmpty()) continue; // the purchase tells it (what was fitted or mended, it doesn't)
			finish(l);
			if (l.text != null && !l.text.isEmpty()) lines.add(l);
		}
		List<String> movers = endedBy == null ? new ArrayList<String>() : moverKinds(endedBy);
		List<Line> last = new ArrayList<Line>();
		if (!movers.isEmpty()) {
			for (Line l : lines) if (movers.contains(l.kind)) last.add(l);
			if (movers.contains("move") && last.size() > 1) { // a ship moving on: one line of it, the sector's if she reached one
				Line keep = last.get(last.size() - 1);
				last.clear(); last.add(keep);
			}
			lines.removeAll(last);
			lines.addAll(last);
		}
		if (last.size() > 1) { // several things closed the day (one visit to the Cargo Bay): one sentence, "…and sold five missiles."
			Line one = last.get(0);
			List<String> said = new ArrayList<String>();
			for (Line l : last) { said.add(lower(l.text.replaceAll("\\.$", ""))); if (l != one) one.details.addAll(l.details); }
			one.text = homeplanet.model.Words.cap(join(said)) + ".";
			lines.removeAll(last.subList(1, last.size()));
			last = last.subList(0, 1);
		}
		where(lines, wasAboard);
		if (lines.size() > 1 && !last.isEmpty()) {
			Line first = last.get(0);
			first.text = first.byMe ? "Then I " + lower(first.text) : first.byWe ? "Then " + lower(first.text)
					: "Then " + (first.text.startsWith("The ") ? "the " + first.text.substring(4) : first.text);
		}
		return lines;
	}
	/**
	 * Where the captain is, as the day goes (heromedel): a day aboard opens "On board the …"; time at the station after
	 * time aboard opens "I returned to The Home Planet Station"; time aboard after the station opens "Set out on the …".
	 */
	private static void where(List<Line> lines, Boolean wasAboard) {
		Boolean at = wasAboard;
		for (int i = 0; i < lines.size(); i++) {
			Line l = lines.get(i);
			if (i == 0 && l.aboard) {
				Line on = new Line("where", "where", false, (Boolean.FALSE.equals(at) ? "Set out on " : "On board ") + theShip(l.ship) + ":");
				on.aboard = true;
				lines.add(0, on); i++;
			} else if (at != null && l.aboard != at) {
				Line w = new Line("where", "where", true, l.aboard ? "Set out on " + theShip(l.ship) + ":" : "I returned to The Home Planet Station.");
				w.aboard = l.aboard;
				lines.add(i, w); i++;
			}
			at = l.aboard;
		}
	}
	/** The kinds of line that moved the day on, by the master log's reason for the day after. */
	private static List<String> moverKinds(String why) {
		if (why.startsWith("a day of rest")) return Arrays.asList("rest");
		if (why.equals(MasterLog.CARGO_BAY)) return Arrays.asList("buy", "sell", "systems", "junk", "retire");
		if (why.startsWith("work at a store")) return Arrays.asList("work", "ftlbuy");
		if (why.startsWith("a jump")) return Arrays.asList("move");
		if (why.startsWith("a job")) return Arrays.asList("job");
		return new ArrayList<String>();
	}

	// ---- reading entries ----

	private static Line line(Map<String, Line> m, String kind, String key, boolean byMe, String text) {
		Line l = m.get(key);
		if (l == null) m.put(key, l = new Line(kind, key, byMe, text));
		return l;
	}
	private static Line once(Map<String, Line> m, String kind, boolean byMe, String text) { return line(m, kind, kind + ":" + m.size(), byMe, text); }

	private static void read(MasterLog.Entry e, Map<String, Line> m) {
		if ("station".equals(e.log)) station(e.text, e.event, m);
		else if (e.log.startsWith("voyage: ")) voyage(e.log.substring(8), e.text, e.event, m);
		// reputation has its own log and tally: never a line here
	}
	private static final Pattern NUM = Pattern.compile("^(\\d+) (.+)$");

	private static void station(String text, homeplanet.core.EventLog.Entry event, Map<String, Line> m) {
		String[] parts = text.split(" / ");
		String first = parts[0];
		int gap = first.indexOf("  ");
		String kind = gap < 0 ? first.trim() : first.substring(0, gap).trim();
		String head = gap < 0 ? "" : first.substring(gap + 2).trim();
		List<String> det = new ArrayList<String>();
		for (int i = 1; i < parts.length; i++) if (!parts[i].trim().isEmpty()) det.add(parts[i].trim());
		if (kind.equals("REST")) {
			Matcher r = Pattern.compile("\\((\\d+) days in a row(?:, [\\u2212-]?(\\d+) reputation)?\\)").matcher(head);
			int run = 1, cost = 0;
			if (r.find()) { run = Integer.parseInt(r.group(1)); if (r.group(2) != null) cost = Integer.parseInt(r.group(2)); }
			Line l = once(m, "rest", true, run <= 1 ? "Rested in my quarters." : "Rested in my quarters for the " + ordinal(run) + " day in a row.");
			if (cost > 0) l.details.add("−" + cost + " reputation");
		} else if (kind.equals("BUY")) {
			if (head.contains(", a derelict, for ")) {
				String name = head.substring(0, head.indexOf(" (") > 0 ? head.indexOf(" (") : head.indexOf(","));
				Line l = once(m, "buy", true, "Bought " + theShip(name) + ", a derelict, from the Junkyard.");
				Matcher p = Pattern.compile("for (\\d+) scrap").matcher(head);
				if (p.find()) l.details.add(p.group(1) + " scrap");
				return;
			}
			Line l = line(m, "buy", "buy", true, "");
			for (String d : det) {
				String item = d.contains(" (") ? d.substring(0, d.indexOf(" (")) : d;
				add(l, item, 1);
				Matcher p = Pattern.compile("\\((\\d+) scrap\\)").matcher(d);
				l.details.add(item + (p.find() ? ", " + p.group(1) + " scrap" : ""));
			}
		} else if (kind.equals("SELL") || kind.equals("JUNK")) {
			boolean sell = kind.equals("SELL");
			Line l = line(m, sell ? "sell" : "junk", sell ? "sell" : "junk", true, "");
			for (String d : det) {
				String what = unowned(d).trim(); // the ship's name in brackets at the end
				String price = null;
				int f = what.lastIndexOf(" for ");
				if (f > 0 && what.endsWith(" scrap")) { price = what.substring(f + 5); what = what.substring(0, f); }
				int n = 1;
				Matcher nm = NUM.matcher(what);
				if (nm.find()) { n = Integer.parseInt(nm.group(1)); what = nm.group(2); }
				add(l, what, n);
				l.details.add(n + " " + what + (price == null ? "" : ", " + price));
			}
		} else if (kind.equals("RETIRE")) {
			Line l = line(m, "retire", "retire", true, "");
			for (String d : det) add(l, unowned(d).replaceAll("\\s*\\([^)]*\\)$", "").trim(), 1);
		} else if (kind.equals("SYSTEMS")) {
			systems(head, det, m);
		} else if (kind.equals("BOARD")) {
			Line l = line(m, "board", "board", true, "");
			l.ship = shipName(head);
		} else if (kind.equals("NEW JOURNEY")) {
			Line l = once(m, "journey", true, "Plotted a new journey for " + theShip(shipName(head)) + ".");
			int d = head.indexOf("difficulty ");
			if (d >= 0) l.details.add(head.substring(d));
		} else if (kind.equals("COMMISSION")) {
			String model = det.isEmpty() ? null : det.get(0).replaceAll("\\s*\\([A-Z0-9_]+\\).*$", "");
			Line l = once(m, "commission", true, "Commissioned " + theShip(shipName(head)) + (model == null ? "." : ", a " + model + "."));
			List<String> crew = event == null ? new ArrayList<String>() : event.all("crew"); // who came aboard with her, by name (heromedel, 6.30); an older entry keeps its Crew: line
			for (int i = 1; i < det.size(); i++) if (crew.isEmpty() || !det.get(i).startsWith("Crew: ")) l.details.add(det.get(i));
			if (!crew.isEmpty()) l.details.add(join(crew) + " came aboard with her.");
		} else if (kind.equals("DISBAND")) {
			once(m, "ships", true, "Decommissioned " + theShip(shipName(head)) + ".");
		} else if (kind.equals("SCRAP")) {
			Line l = once(m, "ships", true, "Broke " + theShip(shipName(head.replace(" stripped into storage, hull broken up", ""))) + " up for parts.");
			l.details.addAll(det);
		} else if (kind.equals("DESTROY")) {
			once(m, "ships", true, "Had " + theShip(shipName(head)) + " broken up for good.");
		} else if (kind.equals("SALVAGE")) {
			String name = shipName(head);
			once(m, "ships", true, name.contains("/") || name.endsWith(".sav") ? "Salvaged a ship from the Junkyard." : "Salvaged " + theShip(name) + " from the Junkyard.");
		} else if (kind.equals("RENAME")) {
			String[] w = unowned(head).split(" -> ", 2);
			if (w.length == 2) once(m, "ships", true, "Renamed " + theShip(w[0].trim()) + " " + theShip(w[1].trim()) + ".");
		} else if (kind.equals("CREW")) { // the Cargo Bay's crew moves, one destination a line (heromedel, 5.40)
			Matcher a = Pattern.compile("^(.+?) assigned to (.+?)\\.?$").matcher(head.trim());
			if (a.find()) { Line l = line(m, "assign", "assign:" + a.group(2), true, ""); l.ship = a.group(2); add(l, a.group(1), 1); }
		} else if (kind.equals("RENAME CREW")) {
			String[] w = unowned(head).split(" -> ", 2);
			String rank = w.length == 2 ? homeplanet.model.Rank.promotion(w[0].trim(), w[1].trim()) : null; // a rank put on (heromedel, 5.52)
			if (head.endsWith("; on the record already)")) return; // the promotion was told the day it was given on the record
			if (rank != null) once(m, "crew", false, "I promoted " + homeplanet.model.Rank.bare(w[1].trim()) + " to " + rank + (head.contains("(posthumously)") ? ", posthumously." : ".")); // heromedel's words (5.53)
			else if (w.length == 2) once(m, "crew", false, w[0].trim() + " is now " + w[1].trim() + ".");
		} else if (kind.equals("REMODEL")) {
			once(m, "ships", true, "Had " + theShip(head.split(" -> ")[0].trim()) + " remodeled.");
		} else if (kind.equals("EXPEDITION")) {
			expedition(head, m);
		} else if (kind.equals("HIRE")) {
			hire(head, m);
		} else if (kind.equals("TRANSMISSION")) {
			letter(head, m);
		} else if (kind.equals("REPLY")) {
			int c = head.indexOf(": ");
			if (c > 0) once(m, "letter", true, "Answered " + the(head.substring(0, c)) + ": " + head.substring(c + 2) + ".");
		} else if (kind.equals("GIFT")) {
			once(m, "letter", false, sentence(head.replace(", to the stored systems", "")));
		} else if (kind.equals("CLAIM")) {
			int c = head.lastIndexOf(": ");
			if (c > 0) { Line l = once(m, "letter", true, "Claimed " + head.substring(c + 2).replace(" to the Cargo Hold", "") + "."); l.details.add(head.substring(0, c)); }
		} else if (kind.equals("STIPEND")) {
			Line l = once(m, "letter", false, "The stipend came in.");
			l.details.add(head.replaceAll(",? to claim from the inbox.*$", ""));
		} else if (kind.equals("PLEAD")) {
			if (head.startsWith("The Federation Home Planet agreed")) once(m, "plea", true, "Pleaded for a new ship, and The Federation Home Planet agreed to send one.");
			else if (head.startsWith("the Cargo Hold given")) once(m, "plea", true, "Gave up the Cargo Hold for the new ship.");
		} else if (kind.equals("UNDO PLEA")) {
			once(m, "plea", true, "Withdrew my plea for a new ship.");
		} else if (kind.equals("VICTORY")) {
			String name = head.split(" won | was rescued ")[0].trim();
			if (head.contains(" was rescued after")) once(m, "ships", false, startShip(name) + " was brought home after the final battle.");
			else once(m, "ships", false, startShip(name) + " drove the Rebel Flagship off."); // hard rule 1: never that she destroyed it
		} else if (kind.equals("FINAL BATTLE")) {
			once(m, "ships", false, startShip(head.split(":")[0].trim()) + " went into the final battle.");
		} else if (kind.equals("MUSEUM")) {
			if (head.contains(" is honoured in")) once(m, "ships", false, startShip(head.split(" is honoured")[0].trim()) + " went to the Federation Museum.");
			else { int f = head.lastIndexOf(" for "); if (f > 0) { Line l = once(m, "ships", false, "The museum paid for " + theShip(head.substring(f + 5).trim()) + "."); l.details.add(head.substring(0, f)); } }
		} else if (kind.equals("REWARD")) {
			int f = head.lastIndexOf(" for ");
			if (f > 0) { Line l = once(m, "ships", false, "A reward came in for " + theShip(head.substring(f + 5).trim()) + "."); l.details.add(head.substring(0, f)); }
		} else if (kind.equals("OVERWRITTEN")) {
			once(m, "ships", false, startShip(head.replaceAll("\\s*\\([0-9a-f]+\\).*$", "").trim()) + " was lost.");
		} else if (kind.equals("LONG RANGE TRADE")) {
			Line l = once(m, "comm", true, "Traded with " + head.replaceAll("^with ", "").replaceAll("\\s+\\(trade .*$", "").trim() + ".");
			l.details.addAll(det);
		} else if (kind.equals("SHIPMENT ARRIVED")) {
			int f = head.lastIndexOf(" from ");
			if (f > 0) { Line l = once(m, "comm", false, "A shipment arrived from " + head.substring(f + 6).trim() + "."); l.details.add(head.substring(0, f)); }
		} else if (kind.equals("SENT AWAY")) {
			Matcher s = Pattern.compile("^(.+?) \\([0-9a-f]+\\) to (.+?)'s fleet").matcher(head);
			if (s.find()) once(m, "comm", true, "Sent " + theShip(s.group(1)) + " to " + s.group(2) + "'s fleet.");
		} else if (kind.equals("RECEIVED")) {
			Matcher s = Pattern.compile("^(.+?) \\([0-9a-f]+\\) from (.+?)'s fleet").matcher(head);
			if (s.find()) once(m, "comm", false, startShip(s.group(1)) + " arrived from " + s.group(2) + "'s fleet.");
		} else if (kind.equals("RETURNED") && head.contains(" to her owner")) {
			once(m, "ships", true, "Returned " + theShip(head.replaceAll("\\s*\\([0-9a-f]+\\).*$", "").trim()) + " to her owner.");
		} else if (kind.equals("SEIZED")) {
			once(m, "ships", false, sentence(head.replaceAll("\\s*\\([0-9a-f]+\\)", "").replace(": collected by", " was collected by")));
		} else if (kind.equals("OVERFLOW")) {
			Matcher s = Pattern.compile("^(.+?) had no room for (\\S+): her crew ship it home").matcher(head);
			if (s.find()) once(m, "letter", false, startShip(s.group(1)) + "'s crew shipped " + article(homeplanet.model.Items.title(s.group(2))) + " home.");
		} else if (kind.equals("CAREER") && head.contains(" career begun")) { // not a rule chosen later (6.03)
			once(m, "career", false, "My service with The Federation Home Planet began.");
		}
		// everything else is the station's own housekeeping, or told by another line (DOCK, TRADE, MEDBAY, CREW, LOADED…)
	}

	private static void systems(String ship, List<String> det, Map<String, Line> m) {
		Line l = line(m, "systems", "systems:" + ship, true, "");
		l.ship = ship;
		for (String d : det) {
			if (d.startsWith("Sold ")) { // a stored system sold: told with the day's sales
				Line s = line(m, "sell", "sell", true, "");
				String what = d.substring(5).replaceAll(" for \\d+ scrap.*$", "");
				add(s, what, 1);
				s.details.add(d.replaceAll(" \\(the Cargo Hold was paid\\)", ""));
				continue;
			}
			if (d.startsWith("Bought ")) continue; // a system bought into the Cargo Hold: its BUY line tells it (5.30)
			if (d.startsWith("Paid ") || d.startsWith("Custom work order")) { l.details.add(d); continue; }
			String act;
			if (d.startsWith("Installed ")) act = "fit:" + d.substring(10).replaceAll(" \\(level.*$", "");
			else if (d.startsWith("Stored ")) act = "off:" + d.substring(7).replaceAll(" \\(level.*$| from .*$", "");
			else if (d.startsWith("Upgraded ")) act = "up:" + d.substring(9).replaceAll(" to level.*$", "");
			else if (d.startsWith("Reactor upgraded")) act = "up:reactor";
			else if (d.startsWith("Mended ") || d.startsWith("Sealed ") || d.startsWith("Hull repaired")) act = "mend";
			else if (d.startsWith("Retrofit") || d.startsWith("Undo Retrofit")) act = "retrofit";
			else act = null;
			if (act != null) add(l, act, 1);
			l.details.add(d);
		}
	}
	private static void expedition(String head, Map<String, Line> m) {
		if (head.contains(" sent to ")) {
			String[] w = head.split(" sent to ", 2);
			String sector = w[1].trim(); // one of many such sectors: "a Rebel Controlled Sector"; the Crystal worlds are the only ones
			Line l = once(m, "expedition", true, "Sent " + names(w[0]) + " to " + (sector.endsWith("Worlds") || sector.toLowerCase().startsWith("the ") ? the(sector) : article(sector)) + ".");
			return;
		}
		int back = head.indexOf(" back from ");
		if (back > 0) {
			List<String> party = new ArrayList<String>(Arrays.asList(head.substring(0, back).split(", ")));
			String rest = head.substring(back + 11);
			List<String> killed = after(rest, "; killed: "), taken = after(rest, "; taken: "), laid = after(rest, "; to the infirmary: ");
			List<String> came = new ArrayList<String>(party);
			for (String k : killed) came.remove(k);
			for (String k : taken) came.remove(k);
			StringBuilder s = new StringBuilder();
			s.append(came.isEmpty() ? "No one came back from the expedition" : join(came) + " came back from the expedition");
			if (!killed.isEmpty()) s.append("; ").append(join(killed)).append(!came.isEmpty() ? " did not" : killed.size() == 1 ? " was killed" : " were killed");
			if (!taken.isEmpty()) s.append("; ").append(join(taken)).append(taken.size() == 1 ? " was" : " were").append(" taken captive");
			if (!laid.isEmpty()) s.append("; ").append(join(laid)).append(" went to the infirmary");
			Line l = once(m, "expedition", false, s.append(".").toString());
			String where = rest.replaceAll(";.*$", "");
			l.details.add(where);
			return;
		}
		if (head.contains(", brought home by an expedition, kept: ")) {
			once(m, "expedition", true, "Kept " + theShip(head.replaceAll("\\s*\\([A-Z0-9_]+\\).*$", "").trim()) + ", brought home by an expedition.");
		} else if (head.contains(", rescued on an expedition, was sent on their way")) {
			once(m, "expedition", true, "Sent " + head.split(",")[0].trim() + " on their way.");
		} else if (head.contains(", brought home by an expedition, was not taken")) {
			once(m, "expedition", true, "Turned down " + theShip(head.split(",")[0].trim()) + ".");
		} else if (head.contains(" (\"")) { // the board of jobs
			Line l = once(m, "job", true, "Took a job: " + head.substring(0, head.indexOf(" (\"")).trim() + ".");
			List<String> lost = after(head, "; did not come back: ");
			if (!lost.isEmpty()) l.text = l.text.substring(0, l.text.length() - 1) + "; " + join(lost) + " did not come back.";
		}
	}
	private static void hire(String head, Map<String, Line> m) {
		Matcher j = Pattern.compile(": (.+?) \\((\\w+)\\) joined").matcher(head);
		if (head.contains(", rescued on an expedition, signed on")) {
			once(m, "crew", false, head.split(" \\(")[0].trim() + ", rescued on an expedition, signed on.");
		} else if (j.find()) {
			once(m, "crew", true, "Hired " + j.group(1) + ", " + article(race(j.group(2))) + ".");
		} else if (head.endsWith("no one answered")) {
			once(m, "crew", true, (head.startsWith("A promise") ? "Posted a promise of adventure" : "Posted for volunteers") + "; no one answered.");
		}
	}
	private static void letter(String head, Map<String, Line> m) {
		int c = head.indexOf(": ");
		if (c < 0) return;
		String from = head.substring(0, c), subject = head.substring(c + 2);
		if (from.equals("Expedition Command") || subject.startsWith("Received from") || subject.startsWith("Trade") || subject.startsWith("Long Range")) return; // told by its own line
		String meaning;
		if (subject.startsWith("Commission order: ")) meaning = "I can now commission a new " + subject.substring(18);
		else if (subject.startsWith("Priority: promoted to ")) meaning = "I've been promoted to " + subject.substring(22);
		else meaning = subject;
		once(m, "letter", false, "Got a letter from " + the(from) + ": " + meaning + (meaning.endsWith(".") || meaning.endsWith("!") || meaning.endsWith("?") ? "" : "."));
	}
	private static void voyage(String ship, String text, homeplanet.core.EventLog.Entry x, Map<String, Line> m) {
		java.util.Set<String> before = new java.util.HashSet<String>(m.keySet());
		if (!fought(ship, x, m)) voyageLine(ship, text, m);
		for (Map.Entry<String, Line> e : m.entrySet()) if (!before.contains(e.getKey())) { e.getValue().aboard = true; if (e.getValue().ship == null) e.getValue().ship = ship; }
	}
	/**
	 * A ship met or ships defeated, read from the event's fields (6.09: their words are lore's now, and may change): the
	 * ship met joins the jump's line, the ships defeated the day's fight, by name where the station saw who. False for any
	 * other entry, and for one too old to have its event.
	 */
	private static boolean fought(String ship, homeplanet.core.EventLog.Entry x, Map<String, Line> m) {
		if (x == null) return false;
		if (x.kind.equals("DISTRESS")) { beacon(ship, m).distress = true; return true; } // 6.21: the jump's line says she responded
		if (x.kind.equals("SHIP_MET")) {
			String met = x.get("met", "");
			if (met.isEmpty()) return false;
			Line l = beacon(ship, m);
			if (!l.met.contains(met)) l.met.add(met);
			return true;
		}
		if (x.kind.equals("SHIPS_DEFEATED")) {
			Line l = line(m, "fight", "fight:" + ship, false, "");
			l.ship = ship;
			l.count += x.num("count", 0);
			String who = x.get("defeated");
			if (who != null && !who.isEmpty()) l.beaten.add(who);
			return true;
		}
		return false;
	}
	private static void voyageLine(String ship, String text, Map<String, Line> m) {
		Matcher sector = Pattern.compile("^Sector (\\d+) reached").matcher(text);
		if (sector.find()) { jump(ship, m).sector = Integer.parseInt(sector.group(1)); return; }
		if (text.startsWith("Jumped")) {
			Line l = jump(ship, m);
			l.details.add(text.replaceFirst("^Jumped, ", ""));
			beating(ship, text, m);
			return;
		}
		if (text.equals("Arrived at a store")) { jump(ship, m).station = true; return; }
		if (text.startsWith("Beacon: ")) { beacon(ship, m).hazards.addAll(Arrays.asList(text.substring(8).split(", "))); return; }
		if (text.startsWith("Ship met: ")) { Line l = beacon(ship, m); String x = text.substring(10); if (!l.met.contains(x)) l.met.add(x); return; }
		if (text.startsWith("Bought at a store: ")) { Line l = line(m, "ftlbuy", "ftlbuy:" + ship, true, ""); for (String x : text.substring(19).split(", ")) add(l, x, 1); return; }
		if (text.startsWith("Picked up: ")) { Line l = line(m, "found", "found:" + ship, false, ""); l.byWe = true; for (String x : text.substring(11).split(", ")) add(l, x, 1); return; }
		if (text.startsWith("Hull damaged")) { beating(ship, text, m); return; }
		Matcher def = Pattern.compile("^(\\d+) ships? defeated").matcher(text);
		if (def.find()) { Line l = line(m, "fight", "fight:" + ship, false, ""); l.ship = ship; l.count += Integer.parseInt(def.group(1)); return; }
		if (text.startsWith("Crew lost: ")) { once(m, "crew", false, "Lost " + join(strip(text.substring(11))) + "."); return; }
		if (text.startsWith("Crew joined: ")) { once(m, "crew", false, join(strip(text.substring(13))) + " came aboard " + theShip(ship) + "."); return; }
		if (text.startsWith("The Rebel Flagship is alongside")) { once(m, "fight", false, "The Rebel Flagship came alongside " + theShip(ship) + "."); return; }
		if (text.startsWith("The Rebel Flagship withdrew")) { once(m, "fight", false, "The Rebel Flagship withdrew."); return; }
		// a stop's work at a store says what was new aboard (heromedel, 5.80); told only on a day with the work note, as a
		// repair drone, an event's free system or a crew member mending the hull aren't work at a station
		if (text.startsWith("Time spent on work")) { work(ship, m).station = true; return; }
		Matcher fitted = Pattern.compile("^New system: (.+?) \\d+$").matcher(text);
		if (fitted.find()) { add(work(ship, m), "fit:" + fitted.group(1), 1); return; }
		if (text.startsWith("Reactor upgraded")) { add(work(ship, m), "up:reactor", 1); return; }
		Matcher upped = Pattern.compile("^(.+?) upgraded to \\d+$").matcher(text);
		if (upped.find()) { add(work(ship, m), "up:" + upped.group(1), 1); return; }
		if (text.startsWith("Hull repaired")) { add(work(ship, m), "mend", 1); return; }
	}
	/** Her stop's work at a store, the day's one line of it. */
	private static Line work(String ship, Map<String, Line> m) {
		Line l = line(m, "work", "work:" + ship, true, "");
		l.ship = ship;
		return l;
	}
	/**
	 * A stop's work at a store in words: "Had a Clone Bay installed and got the Kestrel repaired.", "Had the Shields
	 * upgraded."; with the work noted and nothing known of it, as before; null on a day without the note.
	 */
	static String workText(Line l) {
		if (!l.station) return null;
		if (l.things.isEmpty()) return "Did some shopping and repairs at a station.";
		List<String> fitted = new ArrayList<String>(), upped = new ArrayList<String>();
		for (String a : l.things.keySet()) {
			if (a.startsWith("fit:")) fitted.add(article(systemWords(a.substring(4))));
			else if (a.equals("up:reactor")) upped.add("the reactor");
			else if (a.startsWith("up:")) upped.add("the " + systemWords(a.substring(3)));
		}
		String had = fitted.isEmpty() ? "" : join(fitted) + " installed";
		if (!upped.isEmpty()) had += (had.isEmpty() ? "" : " and ") + join(upped) + " upgraded";
		String mended = l.things.containsKey("mend") ? "got " + theShip(l.ship) + " repaired" : "";
		String s = had.isEmpty() ? mended : "had " + had + (mended.isEmpty() ? "" : (fitted.isEmpty() || upped.isEmpty() ? " and " : ", and ") + mended);
		return homeplanet.model.Words.cap(s) + ".";
	}
	/** A system as said aboard: "Clone Bay", "Shields", but "Hacking system", "Mind Control system" (FTL's titles that aren't things). */
	private static String systemWords(String title) {
		return java.util.Arrays.asList("Hacking", "Cloaking", "Mind Control", "Drone Control", "Piloting", "Oxygen").contains(title) ? title + " system" : title;
	}
	/** The day's jump of hers: one line, "We jumped to sector 3", "…to a station", "…to a new beacon". */
	private static Line jump(String ship, Map<String, Line> m) {
		Line l = line(m, "move", "move:" + ship, false, "");
		l.byWe = true;
		return l;
	}
	/** Where a beacon's news goes: the day's jump of hers, or, with none yet today, a note for her last jump (an earlier day's). */
	private static Line beacon(String ship, Map<String, Line> m) {
		Line j = m.get("move:" + ship);
		return j != null ? j : line(m, "beacon", "beacon:" + ship, false, "");
	}
	/** A hard knock (a quarter of her hull or more at once): she took a beating. */
	private static void beating(String ship, String text, Map<String, Line> m) {
		Matcher h = Pattern.compile("hull (\\d+)/(\\d+) \\((-\\d+)\\)|Hull damaged to (\\d+)/(\\d+) \\((-\\d+)\\)").matcher(text);
		if (!h.find()) return;
		int max = Integer.parseInt(h.group(2) != null ? h.group(2) : h.group(5)), lost = -Integer.parseInt(h.group(3) != null ? h.group(3) : h.group(6));
		if (max > 0 && lost * 4 >= max) line(m, "fight", "beating:" + ship, false, startShip(ship) + " took a beating.");
	}

	/** Who she beat in a day: by name where the station saw who ("the rebel ship and the Rock pirate"), the rest counted (6.09). */
	private static String beaten(Line l) {
		List<String> said = new ArrayList<String>(l.beaten);
		int unknown = l.count - said.size();
		if (unknown > 0) said.add(said.isEmpty() ? (unknown == 1 ? "a ship" : number(unknown) + " ships") : (unknown == 1 ? "one other ship" : number(unknown) + " other ships"));
		return join(said);
	}
	/** A merged line's words, once everything of the day is in it. */
	private static void finish(Line l) {
		if (l.kind.equals("buy") && l.text.isEmpty()) l.text = l.things.isEmpty() ? null : "Bought " + things(l.things) + ".";
		else if (l.kind.equals("sell") && l.text.isEmpty()) l.text = l.things.isEmpty() ? null : "Sold " + things(l.things) + ".";
		else if (l.kind.equals("junk")) l.text = l.things.isEmpty() ? null : "Threw out " + things(l.things) + ".";
		else if (l.kind.equals("assign")) l.text = l.things.isEmpty() ? null : (l.ship.endsWith("Cargo Hold") ? "Moved " : "Assigned ") + join(each(l)) + " to " + l.ship + ".";
		else if (l.kind.equals("retire")) l.text = l.things.isEmpty() ? null : "Let " + join(new ArrayList<String>(l.things.keySet())) + " go.";
		else if (l.kind.equals("board")) l.text = "Took command of " + theShip(l.ship) + ".";
		else if (l.kind.equals("move")) l.text = jumpText(l);
		else if (l.kind.equals("ftlbuy")) l.text = l.things.isEmpty() ? null : "Bought " + things(l.things) + " at a station.";
		else if (l.kind.equals("found")) l.text = l.things.isEmpty() ? null : "We picked up " + things(l.things) + ".";
		else if (l.kind.equals("fight") && l.text.isEmpty()) l.text = l.count <= 0 ? null : startShip(l.ship) + " defeated " + beaten(l) + ".";
		else if (l.kind.equals("systems")) l.text = systemsText(l);
		else if (l.kind.equals("work")) l.text = workText(l);
	}
	/**
	 * A jump in words (heromedel, 5.19): where to, what was there, who she met. "We jumped into a nebula and met a Rock
	 * pirate.", "We jumped to sector 3, to a beacon near a star.", "We jumped to a station in an asteroid field."
	 */
	static String jumpText(Line l) {
		String into = null;
		List<String> near = new ArrayList<String>();
		for (String h : l.hazards) {
			h = h.equals("a red giant") ? "a star" : h.equals("a planetary defence system") ? "an Anti-Ship Battery" : h; // as 5.19 to 5.21 logged them
			if (h.equals("a nebula") || h.equals("an ion storm") || h.equals("an asteroid field")) { if (into == null) into = h; }
			else near.add(h.equals("an Anti-Ship Battery") ? "within range of " + h : "near " + h);
		}
		String nearby = near.isEmpty() ? "" : join(near);
		String where;
		if (l.sector > 0) where = "to sector " + l.sector + (into != null ? ", into " + into + (near.isEmpty() ? "" : " " + nearby) : near.isEmpty() ? "" : ", to a beacon " + nearby);
		else if (l.station) where = "to a station" + (into != null ? " in " + into : "") + (near.isEmpty() ? "" : " " + nearby);
		else where = into != null ? "into " + into + (near.isEmpty() ? "" : " " + nearby) : near.isEmpty() ? "to a new beacon" : "to a beacon " + nearby;
		if (l.distress) { // heromedel (6.21): "We responded to a distress signal." then "We encountered a rebel ship."
			String at = l.sector > 0 ? " in sector " + l.sector : "";
			if (into != null) at += (at.isEmpty() ? "" : ",") + " in " + into;
			if (!near.isEmpty()) at += " " + nearby;
			return "We responded to a distress signal" + at + "." + (l.met.isEmpty() ? "" : " We encountered " + join(l.met) + ".");
		}
		return "We jumped " + where + (l.met.isEmpty() ? "" : " and met " + join(l.met)) + ".";
	}
	private static String systemsText(Line l) {
		if (l.things.isEmpty()) return l.details.isEmpty() ? null : "Had the Dry Dock work on " + theShip(l.ship) + ".";
		if (l.things.size() > 1) return "Had the Dry Dock work on " + theShip(l.ship) + ".";
		String a = l.things.keySet().iterator().next();
		if (a.startsWith("fit:")) return "Had " + article(a.substring(4)) + (a.endsWith("system") ? "" : " system") + " fitted to " + theShip(l.ship) + ".";
		if (a.startsWith("off:")) return "Had the " + a.substring(4) + " taken off " + theShip(l.ship) + ".";
		if (a.equals("up:reactor")) return "Had " + theShip(l.ship) + "'s reactor upgraded.";
		if (a.startsWith("up:")) return "Had " + theShip(l.ship) + "'s " + a.substring(3) + " upgraded.";
		if (a.equals("mend")) return "Had " + theShip(l.ship) + " patched up.";
		if (a.equals("retrofit")) return "Had " + theShip(l.ship) + " retrofitted.";
		return "Had the Dry Dock work on " + theShip(l.ship) + ".";
	}

	// ---- words ----

	/** A merged line's names, a namesake as often as they came. */
	private static List<String> each(Line l) {
		List<String> out = new ArrayList<String>();
		for (Map.Entry<String, Integer> e : l.things.entrySet()) for (int i = 0; i < e.getValue(); i++) out.add(e.getKey());
		return out;
	}
	private static void add(Line l, String thing, int n) { l.things.put(thing, (l.things.containsKey(thing) ? l.things.get(thing) : 0) + n); }
	/** "five missiles and a Burst Laser II"; past three kinds, "supplies and gear" (the list in the details). */
	static String things(Map<String, Integer> t) {
		if (t.size() > 3) return "supplies and gear";
		List<String> out = new ArrayList<String>();
		for (Map.Entry<String, Integer> e : t.entrySet()) out.add(thing(e.getKey(), e.getValue()));
		return join(out);
	}
	static String thing(String name, int n) {
		String low = name.toLowerCase();
		if (low.equals("missiles") || low.equals("missile")) return n == 1 ? "a missile" : number(n) + " missiles";
		if (low.equals("drone parts") || low.equals("drone part")) return n == 1 ? "a drone part" : number(n) + " drone parts";
		if (low.equals("fuel")) return number(n) + " fuel";
		if (low.equals("scrap")) return number(n) + " scrap";
		return n == 1 ? article(name) : number(n) + " " + name + (name.endsWith("s") ? "" : "s");
	}
	static String number(int n) { return homeplanet.model.Words.number(n); } // one home (6.0 step 10)
	private static final String[] ORDINALS = {"", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth"};
	static String ordinal(int n) {
		if (n > 0 && n < ORDINALS.length) return ORDINALS[n];
		int t = n % 100;
		return n + (t >= 11 && t <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th");
	}
	static String article(String name) { return homeplanet.model.Words.a(name); }
	/** A ship's name after "the" (ShipNames.the): "the Kestrel", but "The Adjudicator" as she is, never "the The" (5.31). */
	static String theShip(String name) { return homeplanet.parser.ShipNames.the(name); }
	/** The same, starting a sentence: "The Kestrel", "The Adjudicator". */
	static String startShip(String name) { return homeplanet.parser.ShipNames.theStart(name); }
	/** "the Home Planet Liaison", "Commander Wolfy", "the Nebula": a title gets "the", a name doesn't. */
	static String the(String who) {
		if (who.startsWith("Commander ") || who.startsWith("The ") || who.startsWith("the ") || who.startsWith("Your ")) return who;
		return "the " + who;
	}
	static String join(List<String> l) {
		if (l.isEmpty()) return "";
		if (l.size() == 1) return l.get(0);
		return String.join(", ", l.subList(0, l.size() - 1)) + " and " + l.get(l.size() - 1);
	}
	private static String names(String list) { return join(Arrays.asList(list.split(", "))); }
	private static List<String> after(String s, String marker) {
		int i = s.indexOf(marker);
		if (i < 0) return new ArrayList<String>();
		String rest = s.substring(i + marker.length());
		int end = rest.indexOf(';');
		return new ArrayList<String>(Arrays.asList((end < 0 ? rest : rest.substring(0, end)).split(", ")));
	}
	private static List<String> strip(String list) {
		List<String> out = new ArrayList<String>();
		for (String x : list.split(", ")) out.add(x.replaceAll("\\s*\\([^)]*\\)$", "").trim());
		return out;
	}
	/** A line without the ship's name in brackets at its end ("  (Kestrel (cargo))"): cut at its double space, so brackets inside it don't matter. */
	static String unowned(String s) {
		int i = s.lastIndexOf("  (");
		if (i > 0 && s.trim().endsWith(")")) return s.substring(0, i).trim();
		return s.replaceAll("\\s+\\([^)]*\\)\\s*$", "");
	}
	private static String shipName(String head) { return head.split("  ")[0].replaceAll("\\s*\\([0-9a-f]{16}\\)", "").trim(); }
	private static String race(String id) { return homeplanet.model.Crew.raceTitle(id); }
	private static String sentence(String s) { s = s.trim(); return s.isEmpty() ? s : homeplanet.model.Words.cap(s) + (s.endsWith(".") ? "" : "."); }
	private static String lower(String s) { return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1); }
}
