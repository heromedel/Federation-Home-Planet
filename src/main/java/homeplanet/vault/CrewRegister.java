package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import homeplanet.core.SafeFiles;
import homeplanet.core.Store;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

/**
 * The crew register (heromedel, 5.41): every crew member of a fleet with an id of the station's own, in the fleet's
 * crew.txt. FTL's saves carry no id, so each look ({@link #sweep}) finds everyone where they are (the ships, the
 * Junkyard's hulls, the Cargo Hold, away on assignment, held captive) and matches them to the register: race, sex and
 * colouring never change and the service record only grows, so those decide; name and last known place break ties.
 * A rename keeps the id. What changed since the last look (a move, a rename, an assignment, a capture) is written
 * against the id, a day each. Someone who can no longer be found is killed, retired or transferred only on solid evidence (a ship's
 * fate, the captives file, the station's own log since they were last seen); otherwise missing, until found again.
 */
public final class CrewRegister {
	private static final Logger log = LoggerFactory.getLogger(CrewRegister.class);
	private CrewRegister() { }

	static final String FILE = "crew.txt";
	private static final String NOTE = "The crew register: an id for every crew member of this fleet, and what became of them. Kept by The Home Planet Station.";

	public enum Status { PRESENT, CAPTIVE, MISSING, KILLED, RETIRED, TRANSFERRED }

	/** One crew member, as the register knows them. */
	public static final class Member {
		public final int id;
		public String name, race, title = "";
		public boolean male;
		String tints = "", record = "";
		/** Where they were last found ("ship:<id>", "hold", "away:<sector>", "captive"), and in words. */
		String place = "";
		public String where = "";
		public Status status = Status.PRESENT;
		/** Laid up in the infirmary (in the Cargo Hold): for the Present tab only, not kept. */
		public boolean laidUp;
		int histPos, masterPos;
		public final List<Event> events = new ArrayList<Event>();
		/** Their whole record as last seen (skills, masteries, service, looks): to draw and describe them, gone or not. */
		final Map<String, String> rec = new LinkedHashMap<String, String>();
		/** The ships they served on, in order: each her name now, then the names she had before, oldest first, tab-separated (5.51; see {@link #shipOf}, {@link #formerNames}). */
		public final List<String> served = new ArrayList<String>();
		/** Who they served with (heromedel, 5.52): each one's id, and where (the ships by name, {@link #WITH_EXPEDITION} for an expedition). */
		public final Map<Integer, List<String>> with = new LinkedHashMap<Integer, List<String>>();
		/** The furthest sector they've been in aboard a ship in flight; -1 not known yet (counted from the next look, not made up). */
		int sector = -1;
		Member(int id) { this.id = id; }
		/** Them as last seen, as FTL's crew record (for their portrait and skills), or null if none is known. */
		public CrewState crew() {
			if (rec.isEmpty()) return null;
			try { return homeplanet.comm.Line.crewFrom(rec); } catch (Exception e) { return null; }
		}
		/** "Human", "Engi"...: the race as FTL shows it. */
		public String raceTitle() { return title != null && !title.isEmpty() ? title : homeplanet.model.Crew.raceTitle(race); }
	}
	/** Something that happened to a crew member, on a day (0 or less: before the career's first stardate). */
	public static final class Event {
		public final int day;
		public final String text;
		Event(int day, String text) { this.day = day; this.text = text; }
	}

	/** A crew member found on this look, and where. */
	private static final class Found {
		final String name, race, tints, record, place, where;
		String title = "", ship = "", party = "";
		/** Her sector (from 1) when aboard the ship FTL flies, else 0. */
		int sector;
		Map<String, String> fields;
		final boolean male;
		final int[] counts;
		Found(String name, String race, boolean male, String tints, int[] counts, String place, String where) {
			this.name = name; this.race = race; this.male = male; this.tints = tints; this.counts = counts; this.record = counts == null ? "" : join(counts);
			this.place = place; this.where = where;
		}
	}

	// ---- reading and writing crew.txt ----

	static File file(Vault v) { return new File(v.root, FILE); }

	/** In a member's {@link Member#with}: they went on an expedition together. */
	public static final String WITH_EXPEDITION = "@expedition";
	/** A ship in a member's {@link Member#served} list: her name now. */
	public static String shipOf(String entry) { int t = entry.indexOf('\t'); return t < 0 ? entry : entry.substring(0, t); }
	/** The names she had before, oldest first (empty if never renamed). */
	public static List<String> formerNames(String entry) {
		List<String> out = new ArrayList<String>();
		String[] w = entry.split("\t");
		for (int i = 1; i < w.length; i++) if (!w[i].isEmpty()) out.add(w[i]);
		return out;
	}
	/** The ships served on, as kept: 2 since 5.51 (voyage logs and trades read, a renamed ship once); older lists are rebuilt once. */
	private static final int SERVED_VERSION = 2;

	/** The register as it stands (empty if there's none yet). */
	public static synchronized List<Member> members(Vault v) {
		return read(Store.read(file(v)));
	}
	private static List<Member> read(Properties p) {
		List<Member> out = new ArrayList<Member>();
		int next = Store.num(p, "next", 1);
		for (int id = 1; id < next; id++) {
			String k = id + ".";
			if (p.getProperty(k + "name") == null) continue;
			Member m = new Member(id);
			m.name = p.getProperty(k + "name");
			m.race = p.getProperty(k + "race", "human");
			m.title = p.getProperty(k + "title", "");
			m.male = !"false".equals(p.getProperty(k + "male"));
			m.tints = p.getProperty(k + "tints", "");
			m.record = p.getProperty(k + "record", "");
			m.place = p.getProperty(k + "place", "");
			m.where = p.getProperty(k + "where", "");
			String st = p.getProperty(k + "status", "PRESENT");
			if (st.equals("DISCHARGED")) st = "RETIRED"; // 5.41's word for it
			try { m.status = Status.valueOf(st); } catch (IllegalArgumentException e) { m.status = Status.MISSING; }
			m.histPos = Store.num(p, k + "hist", 0);
			for (String key : p.stringPropertyNames()) if (key.startsWith(k + "rec.")) m.rec.put(key.substring((k + "rec.").length()), p.getProperty(key));
			String served = p.getProperty(k + "served", "");
			if (!served.isEmpty()) for (String sh : served.split("\\|")) m.served.add(sh);
			m.masterPos = Store.num(p, k + "master", 0);
			m.sector = Store.num(p, k + "sector", -1);
			for (String key : p.stringPropertyNames()) {
				if (!key.startsWith(k + "with.")) continue;
				try { m.with.put(Integer.parseInt(key.substring((k + "with.").length())), new ArrayList<String>(java.util.Arrays.asList(p.getProperty(key).split("\\|")))); }
				catch (NumberFormatException e) { /* not one of ours */ }
			}
			for (int i = 0; p.getProperty(k + "e." + i) != null; i++) {
				String e = p.getProperty(k + "e." + i);
				int bar = e.indexOf('|');
				try { m.events.add(new Event(Integer.parseInt(e.substring(0, bar)), e.substring(bar + 1))); } catch (RuntimeException x) { m.events.add(new Event(0, e)); }
			}
			out.add(m);
		}
		return out;
	}
	/** How far the station's log and the master log had been read at the last look (for who's new and how they came). */
	private static int[] seen(Vault v) {
		Properties p = Store.read(file(v)); // unreadable: from the start
		return new int[] {Store.num(p, "seen.hist", 0), Store.num(p, "seen.master", 0), Store.num(p, "served.v", 1)};
	}
	private static void write(Vault v, List<Member> members, int histLen, int masterLen) throws IOException {
		StringBuilder sb = new StringBuilder("# ").append(NOTE).append("\n");
		sb.append("seen.hist=").append(histLen).append("\nseen.master=").append(masterLen).append("\nserved.v=").append(SERVED_VERSION).append("\n");
		int next = 1;
		for (Member m : members) next = Math.max(next, m.id + 1);
		sb.append("next=").append(next).append("\n");
		for (Member m : members) {
			String k = m.id + ".";
			line(sb, k + "name", m.name); line(sb, k + "race", m.race); line(sb, k + "title", m.title); line(sb, k + "male", Boolean.toString(m.male));
			line(sb, k + "tints", m.tints); line(sb, k + "record", m.record);
			line(sb, k + "place", m.place); line(sb, k + "where", m.where); line(sb, k + "status", m.status.name());
			line(sb, k + "hist", Integer.toString(m.histPos)); line(sb, k + "master", Integer.toString(m.masterPos));
			if (!m.served.isEmpty()) line(sb, k + "served", String.join("|", m.served));
			if (m.sector >= 0) line(sb, k + "sector", Integer.toString(m.sector));
			for (Map.Entry<Integer, List<String>> w : m.with.entrySet()) line(sb, k + "with." + w.getKey(), String.join("|", w.getValue()));
			for (Map.Entry<String, String> r : m.rec.entrySet()) line(sb, k + "rec." + r.getKey(), r.getValue());
			for (int i = 0; i < m.events.size(); i++) line(sb, k + "e." + i, m.events.get(i).day + "|" + m.events.get(i).text);
		}
		SafeFiles.writeText(file(v), sb.toString(), true);
	}
	private static void line(StringBuilder sb, String key, String value) {
		Properties one = new Properties();
		one.setProperty(key, value == null ? "" : value);
		java.io.StringWriter w = new java.io.StringWriter();
		try { one.store(w, null); } catch (IOException e) { return; }
		for (String l : w.toString().split("\n")) if (!l.startsWith("#")) sb.append(l.trim()).append("\n"); // its own escaping, one line
	}

	// ---- the look ----

	/**
	 * Everyone found where they are, matched to the register, and what changed written down. Run as the station takes
	 * stock; never throws (the register is a record, never in the way). Leaves everything as it was if any place
	 * couldn't be read (FTL saving, a damaged file): a crew member is never called missing on a misread.
	 */
	public static synchronized void sweep(Vault v) {
		try { sweepNow(v); }
		catch (Throwable t) { log.warn("The crew register could not take stock: {}", t.toString()); }
	}
	private static void sweepNow(Vault v) throws IOException {
		List<Found> found = findAll(v);
		if (found == null) return;
		File f = file(v);
		boolean fresh = !f.isFile();
		List<Member> members = members(v);
		int[] seen = seen(v);
		int today = MasterLog.today(v);
		String hist = text(v.historyLog()), master = text(new File(v.logsDir(), MasterLog.FILE));
		int histLen = hist.length(), masterLen = master.length();
		boolean changed = fresh;

		// the likeliest pairs first: a crew member is matched once, and to one entry
		List<Member> open = new ArrayList<Member>();
		for (Member m : members) if (m.status == Status.PRESENT || m.status == Status.CAPTIVE || m.status == Status.MISSING) open.add(m);
		List<int[]> pairs = new ArrayList<int[]>();
		for (int i = 0; i < found.size(); i++) for (int j = 0; j < open.size(); j++) {
			int s = score(found.get(i), open.get(j));
			if (s > 0) pairs.add(new int[] {s, i, j});
		}
		Collections.sort(pairs, new Comparator<int[]>() { public int compare(int[] a, int[] b) { return a[0] != b[0] ? b[0] - a[0] : a[2] - b[2]; } });
		Member[] matchOf = new Member[found.size()];
		boolean[] taken = new boolean[open.size()];
		for (int[] p : pairs) {
			if (matchOf[p[1]] != null || taken[p[2]]) continue;
			matchOf[p[1]] = open.get(p[2]);
			taken[p[2]] = true;
		}

		// those found: moved, renamed, back, ransomed, found again
		Member[] memberOf = new Member[found.size()];
		for (int i = 0; i < found.size(); i++) {
			Found x = found.get(i);
			Member m = matchOf[i];
			boolean known = m != null;
			if (m == null) {
				m = new Member(nextId(members));
				m.name = x.name; m.race = x.race; m.male = x.male;
				m.title = x.title;
				if (!fresh) m.events.add(new Event(today, joined(x, hist, seen[0])));
				members.add(m);
			} else {
				if (!m.name.equals(x.name) && !onRecord(m.name, x.name)) {
					String rank = homeplanet.model.Rank.promotion(m.name, x.name); // a rank put on their name (heromedel, 5.52)
					m.events.add(new Event(today, rank != null ? "Promoted to " + rank + "." : "Now known as " + x.name + " (was " + m.name + ")."));
				}
				for (String e : milestones(m, x)) m.events.add(new Event(today, e));
				if (m.status == Status.MISSING) m.events.add(new Event(today, "Found again, " + x.where + "."));
				else if (!m.place.equals(x.place)) m.events.add(new Event(today, moved(m, x)));
			}
			if (x.sector > 0) { // the first time ever in sector 5, and in sector 8, aboard a ship in flight; arriving where she already is counts for nothing
				if (known && m.sector >= 0 && m.place.equals(x.place))
					for (int at : new int[] {5, 8}) if (m.sector < at && x.sector >= at) m.events.add(new Event(today, "Reached sector " + at + " for the first time, aboard " + the(x.ship) + "."));
				if (x.sector > m.sector) { m.sector = x.sector; changed = true; }
			}
			memberOf[i] = m;
			Status now = x.place.equals("captive") ? Status.CAPTIVE : Status.PRESENT;
			String name = onRecord(m.name, x.name) ? m.name : x.name; // a rank given on the record alone stays there
			if (!m.name.equals(name) || m.status != now || !m.place.equals(x.place) || !m.where.equals(x.where) || !m.record.equals(x.record) || !m.tints.equals(x.tints)) changed = true;
			m.name = name; m.status = now; m.place = x.place; m.where = x.where; m.tints = x.tints; m.record = x.record;
			if (!x.title.isEmpty()) m.title = x.title;
			if (x.fields != null && !x.fields.equals(m.rec)) { m.rec.clear(); m.rec.putAll(x.fields); changed = true; }
			if (x.place.startsWith("ship:") && !x.ship.isEmpty() && (m.served.isEmpty() || !shipOf(m.served.get(m.served.size() - 1)).equals(x.ship))) { m.served.add(x.ship); changed = true; }
			m.histPos = histLen; m.masterPos = masterLen;
		}

		// who served with whom: everyone aboard the same ship, or out on the same expedition, has each other (heromedel, 5.52)
		Map<String, List<Integer>> together = new LinkedHashMap<String, List<Integer>>();
		for (int i = 0; i < found.size(); i++) {
			Found x = found.get(i);
			String k = x.place.startsWith("ship:") ? x.place : !x.party.isEmpty() ? x.party : null;
			if (k == null) continue;
			if (!together.containsKey(k)) together.put(k, new ArrayList<Integer>());
			together.get(k).add(i);
		}
		for (List<Integer> group : together.values()) {
			for (int a : group) for (int b : group) {
				if (a == b || memberOf[a] == memberOf[b]) continue;
				Found x = found.get(a);
				if (servedWith(memberOf[a], memberOf[b].id, x.place.startsWith("ship:") ? x.ship : WITH_EXPEDITION)) changed = true;
			}
		}

		// those not found: what became of them, on solid evidence only
		for (int j = 0; j < open.size(); j++) {
			if (taken[j]) continue;
			Member m = open.get(j);
			if (m.status == Status.MISSING) continue;
			String since = m.histPos <= hist.length() ? hist.substring(m.histPos) : hist;
			String flown = m.masterPos <= master.length() ? master.substring(m.masterPos) : master;
			String[] fate = fate(v, m, since, flown);
			m.status = Status.valueOf(fate[0]);
			m.events.add(new Event(today, fate[1]));
			if (m.status != Status.MISSING) m.where = fate[2];
			changed = true;
		}

		if (fresh) {
			Map<Member, List<String>> now = new LinkedHashMap<Member, List<String>>(); // the ship they're aboard comes after those the logs tell of
			for (Member m : members) { now.put(m, new ArrayList<String>(m.served)); m.served.clear(); }
			backfill(v, members);
			for (Map.Entry<Member, List<String>> e : now.entrySet()) for (String sh : e.getValue()) servedOn(e.getKey(), sh);
			for (Member m : members) if (m.events.isEmpty() && m.status == Status.PRESENT) m.events.add(new Event(today, "On the station's records from this day, " + m.where + "."));
		} else if (seen[2] < SERVED_VERSION) {
			rebuildServed(v, hist, members);
			changed = true;
		}
		if (renamedShips(v, hist, members)) changed = true;
		for (Member m : members) if (m.rec.isEmpty() && invent(m)) changed = true;
		if (changed || seen[0] != histLen || seen[1] != masterLen) write(v, members, histLen, masterLen);
	}
	private static int nextId(List<Member> members) { int n = 1; for (Member m : members) n = Math.max(n, m.id + 1); return n; }

	/** Everyone, everywhere; null if any place couldn't be read just now. */
	private static List<Found> findAll(Vault v) {
		List<Found> out = new ArrayList<Found>();
		Ship b = v.boarded();
		List<Ship> ships = new ArrayList<Ship>();
		if (b != null) ships.add(b);
		ships.addAll(v.docked());
		ships.addAll(v.junked());
		for (Ship s : ships) {
			if (v.ignoring(s)) continue; // an uncommissioned ship in Immersive Mode: her crew aren't the career's (heromedel, 5.54)
			if (!v.fileOf(s).isFile()) { if (s == b) return null; continue; } // continue.sav away: FTL is saving, or between runs
			SavedGameState gs = s.save();
			if (gs == null || gs.getPlayerShip() == null) return null;
			String where = s.state == Ship.State.JUNKED ? "aboard " + the(s.name) + ", in the Junkyard" : "aboard " + the(s.name);
			int sector = s == b ? gs.getSectorNumber() + 1 : 0;
			for (CrewState c : homeplanet.parser.SaveHelper.getOwnCrew(gs.getPlayerShip())) { Found x = found(c, "ship:" + s.id, where); x.ship = s.name; x.sector = sector; out.add(x); }
		}
		Ship hold = v.storageEntry();
		if (hold != null && v.fileOf(hold).isFile()) {
			SavedGameState gs = hold.save();
			if (gs == null || gs.getPlayerShip() == null) return null;
			for (CrewState c : homeplanet.parser.SaveHelper.getOwnCrew(gs.getPlayerShip())) out.add(found(c, "hold", "in the Cargo Hold"));
		}
		Properties asg; // the file as it stands, without Assignments' lock: never taken while taking stock
		try { asg = homeplanet.parser.Assignments.asIs(v); } catch (IOException e) { return null; }
		{
			Properties p = asg;
			for (int i = 0; i < 64; i++) {
				String sectorId = p.getProperty("away." + i + ".sector");
				if (sectorId == null) continue;
				String sector = homeplanet.parser.Assignments.sectorTitle(sectorId);
				for (int k = 0; p.getProperty("away." + i + ".crew." + k + ".name") != null; k++) {
					Map<String, String> fields = new LinkedHashMap<String, String>();
					String pre = "away." + i + ".crew." + k + ".";
					for (String key : p.stringPropertyNames()) if (key.startsWith(pre)) fields.put(key.substring(pre.length()), p.getProperty(key));
					CrewState c;
					try { c = homeplanet.comm.Line.crewFrom(fields); } catch (Exception e) { return null; }
					Found x = found(c, "away:" + sector, "on an expedition " + sectorPhrase(sector));
						x.party = "away." + i; // those sent out together
						out.add(x);
				}
			}
		}
		Properties cap;
		try { cap = homeplanet.parser.Expeditions.captivesAsIs(v); } catch (IOException e) { return null; }
		{
			Properties p = cap;
			for (int i = 0; p.getProperty(i + ".name") != null; i++) {
				String state = p.getProperty(i + ".state", "held");
				if (!state.equals("held") && !state.equals("asked") && !state.equals("reminded")) continue;
				String captors = p.getProperty(i + ".captors", "pirates");
				Map<String, String> fields = new LinkedHashMap<String, String>();
				for (String k : p.stringPropertyNames()) if (k.startsWith(i + ".crew.")) fields.put(k.substring((i + ".crew.").length()), p.getProperty(k));
				CrewState c = null;
				try { if (!fields.isEmpty()) c = homeplanet.comm.Line.crewFrom(fields); } catch (Exception e) { c = null; }
				if (c != null) out.add(found(c, "captive", "held captive by " + captors));
				else out.add(new Found(p.getProperty(i + ".name"), p.getProperty(i + ".race", "human"), "true".equals(p.getProperty(i + ".male")), "", null, "captive", "held captive by " + captors));
			}
		}
		return out;
	}
	private static Found found(CrewState c, String place, String where) {
		StringBuilder t = new StringBuilder();
		List<Integer> tints = c.getSpriteTintIndeces();
		if (tints != null) for (Integer n : tints) t.append(t.length() == 0 ? "" : ".").append(n);
		int[] counts = {c.getRepairs(), c.getCombatKills(), c.getPilotedEvasions(), c.getJumpsSurvived(), c.getSkillMasteriesEarned()};
		Found x = new Found(c.getName(), c.getRace() == null ? "human" : c.getRace().getId(), c.isMale(), t.toString(), counts, place, where);
		try { x.title = homeplanet.model.Crew.raceTitle(c); } catch (RuntimeException e) { x.title = ""; } // FTL's own name for the race (Zoltan, Lanius)
		try { x.fields = homeplanet.comm.Line.crewFields(c); } catch (RuntimeException e) { x.fields = null; }
		return x;
	}

	/**
	 * How likely this is the one, or 0 if it can't be: race and sex never change; nor does the colouring; the service
	 * record only grows. Then the same record, the same name and the same place make it likelier.
	 */
	static int score(Found x, Member m) {
		if (!x.race.equals(m.race) || x.male != m.male) return 0;
		if (!x.tints.isEmpty() && !m.tints.isEmpty() && !x.tints.equals(m.tints)) return 0;
		int s = 1;
		if (x.counts != null && !m.record.isEmpty()) {
			String[] was = m.record.split(",");
			for (int i = 0; i < was.length && i < x.counts.length; i++) {
				try { if (x.counts[i] < Integer.parseInt(was[i])) return 0; } catch (NumberFormatException e) { /* an odd value: no say */ }
			}
			if (x.record.equals(m.record)) s += 4;
		}
		if (!x.tints.isEmpty() && x.tints.equals(m.tints)) s += 2;
		if (x.name.equals(m.name) || homeplanet.model.Rank.bare(x.name).equals(homeplanet.model.Rank.bare(m.name))) s += 8; // a rank on one name and not the other: the same name
		else { // a new name (renamed in the Cargo Bay): only on the whole service record, and the colouring or the place besides
			boolean record = x.counts != null && !m.record.isEmpty() && x.record.equals(m.record);
			boolean looks = !x.tints.isEmpty() && x.tints.equals(m.tints);
			if (!record || !(looks || x.place.equals(m.place))) return 0;
		}
		if (x.place.equals(m.place)) s += 2;
		return s;
	}

	/** One met another, there: true if it's new. */
	private static boolean servedWith(Member m, int other, String where) {
		List<String> l = m.with.get(other);
		if (l == null) m.with.put(other, l = new ArrayList<String>());
		if (l.contains(where)) return false;
		l.add(where);
		return true;
	}
	private static final String[] SKILL = {"Piloting", "Engines", "Shields", "Weapons", "Repair", "Combat"};
	/**
	 * What they did since the last look worth a line in their career (heromedel, 5.52): a skill mastery earned, the first
	 * kill, a hundred jumps survived. Only against a look before: nothing is made up for a record first seen now.
	 */
	private static List<String> milestones(Member m, Found x) {
		List<String> out = new ArrayList<String>();
		if (x.fields == null || m.rec.isEmpty()) return out;
		String was = m.rec.get("mastery"), now = x.fields.get("mastery");
		if (was != null && now != null && was.length() == 12 && now.length() == 12) {
			for (int i = 0; i < 12; i++) {
				if (was.charAt(i) == '1' || now.charAt(i) != '1') continue;
				out.add(i % 2 == 0 ? "Earned the first " + SKILL[i / 2] + " mastery." : "Mastered " + SKILL[i / 2] + ".");
			}
		}
		int kills0 = intOr(m.rec.get("kills")), kills = intOr(x.fields.get("kills")), jumps0 = intOr(m.rec.get("jumps")), jumps = intOr(x.fields.get("jumps"));
		String aboard = x.place.startsWith("ship:") ? ", aboard " + the(x.ship) : "";
		if (kills0 == 0 && kills > 0) out.add("First kill" + aboard + ".");
		if (jumps0 >= 0 && jumps0 < 100 && jumps >= 100) out.add("Survived a hundred jumps" + aboard + ".");
		return out;
	}
	private static int intOr(String s) { try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return -1; } }

	/**
	 * Her crew, lost with her when FTL's New Game wrote over her, are back aboard (heromedel, 5.55): restored to the
	 * register as they were, the loss taken out of their careers.
	 */
	public static synchronized void shipBack(Vault v, Ship s) {
		try {
			List<Member> members = members(v);
			boolean changed = false;
			for (Member m : members) {
				if (m.status != Status.KILLED || !m.place.equals("ship:" + s.id)) continue;
				for (int i = m.events.size() - 1; i >= 0; i--) if (m.events.get(i).text.startsWith("Lost with ")) { m.events.remove(i); break; }
				m.status = Status.PRESENT;
				m.where = "aboard " + the(s.name);
				m.events.add(new Event(MasterLog.today(v), "Back aboard " + the(s.name) + ": she was restored after FTL's New Game wrote over her."));
				changed = true;
			}
			if (changed) { int[] seen = seen(v); write(v, members, seen[0], seen[1]); }
			sweep(v);
		} catch (IOException e) { log.warn("Could not bring {}'s crew back to the register: {}", s, e.toString()); }
	}

	// ---- promotions (heromedel, 5.52) ----

	/** Their record wears a higher rank than the name found in a save: promoted where no save could be changed. */
	private static boolean onRecord(String record, String found) {
		return homeplanet.model.Rank.bare(record).equals(homeplanet.model.Rank.bare(found)) && homeplanet.model.Rank.worn(record) > homeplanet.model.Rank.worn(found);
	}
	/** The rank they're due ({@link homeplanet.model.Rank}), or -1: by their skills as they stand, or as last seen for those gone. */
	public static int rankDue(Member m) {
		CrewState c = m.crew();
		if (c == null) return -1;
		c.setName(m.name);
		return homeplanet.model.Rank.due(c);
	}
	/** The name their save has (their record can wear a rank it doesn't). */
	private static String savedName(Member m) { String n = m.rec.get("name"); return n == null ? m.name : n; }
	/** The save they're in that the station may change: the Cargo Hold, a ship of the fleet; null for anyone else (the rank goes on their record alone). */
	private static Ship saveOf(Vault v, Member m) {
		if (m.status != Status.PRESENT) return null;
		if (m.place.equals("hold")) return v.storageEntry();
		return m.place.startsWith("ship:") ? v.byId(m.place.substring(5)) : null;
	}
	/**
	 * The rank Promote gives them now, or -1: the rank they're due; else one their record wears and their save doesn't
	 * (promoted while away, back with their old name), to put on their name in FTL too.
	 */
	public static int rankToGive(Vault v, Member m) {
		int r = rankDue(m);
		if (r >= 0) return r;
		int worn = homeplanet.model.Rank.worn(m.name);
		return worn >= 0 && worn > homeplanet.model.Rank.worn(savedName(m)) && saveOf(v, m) != null ? worn : -1;
	}
	/** Why they can't be promoted just now, or null if they can: only while FTL is flying their ship. */
	public static String cannotPromote(Vault v, Member m) {
		Ship s = saveOf(v, m);
		if (s != null && s.isBoarded() && homeplanet.core.GameGuard.isFtlRunning()) return "Not while FTL is flying their ship: close FTL first, or promote them in the Cargo Bay.";
		return null;
	}
	/**
	 * Promotes them: the rank put on their name in their save (FTL shows it), logged as a crew rename; anyone the
	 * station has no save of to change (MIA, captive, away, gone, the KIA posthumously) on their record alone. Returns
	 * their name on the record.
	 */
	public static synchronized String promote(Vault v, int id) throws IOException {
		List<Member> members = members(v);
		Member m = null;
		for (Member x : members) if (x.id == id) m = x;
		int r = m == null ? -1 : rankToGive(v, m);
		if (r < 0) throw new IOException("No promotion is due.");
		String why = cannotPromote(v, m);
		if (why != null) throw new IOException(why);
		Ship s = saveOf(v, m);
		if (s == null) {
			String was = m.name;
			m.name = homeplanet.model.Rank.promoted(was, r);
			boolean dead = m.status == Status.KILLED;
			m.events.add(new Event(MasterLog.today(v), (dead ? "Promoted posthumously to " : "Promoted to ") + homeplanet.model.Rank.TITLE[r] + "."));
			int[] seen = seen(v);
			write(v, members, seen[0], seen[1]);
			homeplanet.core.HistoryLog.entry("RENAME CREW", was + " -> " + m.name + "  (" + (dead ? "posthumously" : "on the record") + ")", null,
					homeplanet.core.Event.of("RENAME_CREW").put("what", "promoted").put("from", was).put("to", m.name).put("crew_id", m.id).put("race", m.race).put("rank", homeplanet.model.Rank.TITLE[r]).put("posthumously", dead));
			return m.name;
		}
		String was = savedName(m), now = homeplanet.model.Rank.promoted(was, r);
		Vault.Copy c = v.readCopy(s);
		CrewState who = null; // the one the register knows: name, looks and service record
		for (CrewState x : homeplanet.parser.SaveHelper.getOwnCrew(c.save.getPlayerShip())) {
			Found f = found(x, m.place, m.where);
			if (x.getName().equals(was) && score(f, m) > 0 && (m.record.isEmpty() || f.record.equals(m.record))) { who = x; break; }
		}
		if (who == null) throw new IOException(was + " could not be found " + m.where + " just now; nothing was changed.");
		who.setName(now);
		v.begin().put(s, c.save, c.hash).commit();
		homeplanet.core.HistoryLog.entry("RENAME CREW", was + " -> " + now + "  (" + (m.place.equals("hold") ? HOLD_NAME : s.name) + ")", null,
				homeplanet.core.Event.of("RENAME_CREW").put("what", "renamed").put("from", was).put("to", now).put("crew_id", m.id).put("race", m.race).put("place", m.place).put("ship_name", m.place.equals("hold") ? null : s.name).put("ship_id", m.place.equals("hold") ? null : s.id));
		sweep(v); // the register sees the new name: "Promoted to ..." (none if their record already wore it)
		return now;
	}

	/** Words for a move between looks. */
	private static String moved(Member m, Found x) {
		if (x.place.equals("captive")) return "Taken captive; " + x.where + ".";
		if (m.place.equals("captive")) return "Ransomed; back " + x.where + ".";
		if (x.place.startsWith("away:")) return "Sent on an expedition " + sectorPhrase(x.place.substring(5)).replaceFirst("^in ", "to ") + ".";
		if (m.place.startsWith("away:")) return "Back from an expedition " + sectorPhrase(m.place.substring(5)) + "; " + x.where + ".";
		if (x.place.equals("hold")) return "Moved to the Cargo Hold.";
		return "Assigned " + x.where.replaceFirst("^aboard ", "to ") + ".";
	}
	/** How a crew member new to the register came: the station's log since the last look says, else where they are. */
	private static String joined(Found x, String hist, int since) {
		String recent = (since <= hist.length() ? hist.substring(since) : "").replace("\r", "");
		String peer = traded(recent, "received", x.name + " (" + x.title + ")");
		if (peer != null) return "Transferred from " + peer + "'s fleet; " + x.where + ".";
		for (String l : recent.split("\n")) {
			if (!l.contains("  HIRE  ") || !l.contains(x.name + " (")) continue;
			if (l.contains("rescued on an expedition")) return "Rescued on an expedition and signed on, serving " + x.where + ".";
			return "Hired, serving " + x.where + ".";
		}
		if (x.place.startsWith("ship:")) return "Came " + x.where + "."; // "Came aboard the Kestrel."
		return "Joined, serving " + x.where + ".";
	}
	/** {status, event, where} for someone no longer found anywhere. */
	private static String[] fate(Vault v, Member m, String since, String flown) {
		since = since.replace("\r", ""); flown = flown.replace("\r", ""); // the logs are written with \r\n on Windows
		String named = m.name + " (";
		String peer = traded(since, "gave: ", m.name + " (" + m.raceTitle() + ")"); // traded away over the Long Range (Cloud-C-BugsandFeedback's handoff, 5.47)
		if (peer != null) return new String[] {"TRANSFERRED", "Transferred to " + peer + "'s fleet.", "transferred to " + peer + "'s fleet"};
		for (String l : since.split("\n")) { // retired in the Cargo Bay
			if (l.startsWith("  ") && l.contains(named) && retireDetail(since, l)) return new String[] {"RETIRED", "Retired from the station's service.", "retired"};
		}
		for (String l : since.split("\n")) { // an expedition's end
			if (!l.contains("  EXPEDITION  ")) continue;
			if (listed(l, "killed: ", m.name) || listed(l, "did not come back: ", m.name)) return new String[] {"KILLED", "Killed on an expedition.", "killed on an expedition"};
		}
		if (m.place.equals("captive")) {
			{
				Properties p;
				try { p = homeplanet.parser.Expeditions.captivesAsIs(v); } catch (IOException e) { p = new Properties(); }
				for (int i = 0; p.getProperty(i + ".name") != null; i++) {
					if (!m.name.equals(p.getProperty(i + ".name")) || !m.race.equals(p.getProperty(i + ".race", "human"))) continue;
					String st = p.getProperty(i + ".state", "");
					if (st.equals("gone") || st.equals("refused")) return new String[] {"KILLED", "Never ransomed: lost to " + p.getProperty(i + ".captors", "their captors") + ", presumed dead.", "presumed dead, never ransomed"};
				}
			}
		}
		if (m.place.startsWith("ship:")) {
			String id = m.place.substring(5);
			Ship s = v.byId(id);
			String shipName = m.where.replaceFirst("^aboard ", "").replaceFirst(", in the Junkyard$", "");
			if (s == null) {
				String f = fateOf(v, id);
				if (f.equals("LOST") || f.equals("DESTROYED")) return new String[] {"KILLED", "Lost with " + homeplanet.parser.ShipNames.the(shipName) + ".", "lost with " + homeplanet.parser.ShipNames.the(shipName)};
				if (f.equals("TRANSFERRED")) return new String[] {"TRANSFERRED", "Transferred with " + homeplanet.parser.ShipNames.the(shipName) + " to another fleet.", "transferred with " + homeplanet.parser.ShipNames.the(shipName)};
				if (!f.isEmpty() && !f.equals("SCRAPPED")) return new String[] {"TRANSFERRED", "Left the fleet with " + homeplanet.parser.ShipNames.the(shipName) + ".", "left the fleet with " + homeplanet.parser.ShipNames.the(shipName)};
			} else if (s.isBoarded() && flown.contains("Crew lost: ") && (listed(flown.replace("Crew lost: ", "\nCrew lost: "), "Crew lost: ", m.name + " (" + m.raceTitle() + ")")
					|| listed(flown.replace("Crew lost: ", "\nCrew lost: "), "Crew lost: ", m.name + " (" + homeplanet.model.Crew.racePeople(m.race) + ")"))) { // FTL's title (Rockman), or the voyage log's own word for the race (Rock): 5.61
				return new String[] {"KILLED", "Lost aboard " + the(shipName) + ".", "lost aboard " + the(shipName)};
			}
		}
		return new String[] {"MISSING", "Not found anywhere in the fleet: whereabouts unknown.", ""};
	}
	/**
	 * The commander a Long Range trade in this log text gave this crew member to ("gave: ...") or received them from
	 * ("received...: ..."), or null. The detail lists things joined with ", " and " and "; a crew member is "Name (Race)".
	 */
	private static String traded(String text, String label, String who) {
		String peer = null;
		for (String l : text.split("\n")) {
			if (!l.startsWith("  ")) {
				int at = l.indexOf("  LONG RANGE TRADE  with ");
				if (at < 0) { peer = null; continue; }
				String rest = l.substring(at + 25);
				int end = rest.indexOf("  (trade");
				peer = (end < 0 ? rest : rest.substring(0, end)).trim();
				continue;
			}
			if (peer == null) continue;
			String d = l.trim();
			if (!d.startsWith(label)) continue;
			int colon = d.indexOf(": ");
			if (colon < 0) continue;
			for (String one : d.substring(colon + 2).split(", | and ")) if (one.trim().equals(who)) return peer;
		}
		return null;
	}
	/** A RETIRE entry's detail line (the entry line above it says RETIRE). */
	private static boolean retireDetail(String text, String detail) {
		int at = text.indexOf(detail);
		if (at < 0) return false;
		int head = text.lastIndexOf("\n", at - 1);
		while (head > 0) {
			int start = text.lastIndexOf("\n", head - 1) + 1;
			String l = text.substring(start, head);
			if (!l.startsWith("  ")) return l.contains("  RETIRE  ");
			head = start - 1;
		}
		return text.startsWith("") && text.substring(0, Math.max(0, text.indexOf("\n"))).contains("  RETIRE  ");
	}
	/** The name is in the list after this label ("killed: Ash, Bob; taken: ..."). */
	private static boolean listed(String line, String label, String name) {
		int at = line.indexOf(label);
		while (at >= 0) {
			String rest = line.substring(at + label.length());
			int end = rest.indexOf(';'), nl = rest.indexOf('\n');
			if (nl >= 0 && (end < 0 || nl < end)) end = nl;
			for (String n : (end < 0 ? rest : rest.substring(0, end)).split(",|\\|")) if (n.trim().equals(name)) return true;
			at = line.indexOf(label, at + 1);
		}
		return false;
	}
	private static String fateOf(Vault v, String id) {
		File f = new File(v.folderOfId(id), "fate.txt");
		if (!f.isFile()) return "";
		String t = text(f).trim();
		int nl = t.indexOf('\n');
		return (nl < 0 ? t : t.substring(0, nl)).trim();
	}

	// ---- a new register: the logs read once, for what came before ----

	/**
	 * The station's log and the ships' voyage logs read once, by name, for what came before the register: the past of
	 * those found now, and those already gone (killed, lost, retired) as entries of their own. The past only: from now
	 * on everything is kept against the id.
	 */
	private static void backfill(Vault v, List<Member> members) {
		Map<String, List<Member>> byName = new LinkedHashMap<String, List<Member>>();
		for (Member m : members) {
			if (!byName.containsKey(m.name)) byName.put(m.name, new ArrayList<Member>());
			byName.get(m.name).add(m);
		}
		Map<String, String> renamedFrom = new LinkedHashMap<String, String>(); // a past name -> the name now
		List<String[]> past = pastEntries(v); // {day, log, first line, whole entry}
		for (String[] e : past) {
			if (!e[2].startsWith("RENAME CREW")) continue;
			String[] w = e[2].substring(11).trim().replaceAll("\\s+\\(.*$", "").split(" -> ", 2);
			if (w.length == 2) renamedFrom.put(w[0].trim(), w[1].trim());
		}
		for (String[] e : past) {
			int day = Integer.parseInt(e[0]);
			if (e[1].equals("station")) station(e[2], e[3], day, byName, renamedFrom, members);
			else if (e[1].startsWith("voyage: ")) voyage(e[1].substring(8), e[2], day, byName, renamedFrom, members);
		}
	}
	/**
	 * Everything logged before the register, oldest first: the master log's entries (those before the first stardate
	 * too, as day 0), and before it began (5.17), the station log's own (day 0). A master entry's details are its
	 * lines joined with " / ".
	 */
	private static List<String[]> pastEntries(Vault v) {
		List<String[]> out = new ArrayList<String[]>();
		String firstReal = null;
		for (String l : text(new File(v.logsDir(), MasterLog.FILE)).split("\r?\n")) {
			if (!l.startsWith("E\t")) continue;
			String[] w = l.split("\t", 5);
			if (w.length < 5) continue;
			int day;
			try { day = Math.max(0, Integer.parseInt(w[2].trim())); } catch (NumberFormatException e) { continue; }
			if (firstReal == null) firstReal = w[1];
			String whole = w[4].replace(" / ", "\n");
			out.add(new String[] {Integer.toString(day), w[3], whole.split("\n", 2)[0], whole});
		}
		// the station log from before the master log began (its lines are stamped to the minute; the master's to the second)
		final String before = firstReal == null ? null : firstReal.substring(0, Math.min(16, firstReal.length()));
		List<Object[]> older = new ArrayList<Object[]>(); // {stamp, entry}
		String kindLine = null, stamp = null; StringBuilder whole = null;
		for (String l : (text(v.historyLog()) + "\n").split("\r?\n")) {
			boolean detail = l.startsWith("  ");
			if (!detail && kindLine != null) { older.add(new Object[] {stamp, new String[] {"0", "station", kindLine, whole.toString()}}); kindLine = null; }
			if (detail) { if (whole != null) whole.append("\n").append(l); continue; }
			whole = null;
			if (l.length() < 18 || (before != null && l.substring(0, 16).compareTo(before) >= 0)) continue;
			stamp = l.substring(0, 16);
			kindLine = l.substring(16).trim();
			whole = new StringBuilder(kindLine);
		}
		// and each ship's voyage log from before it began: who joined her and who was lost; the master log has the rest (5.51)
		for (File d : v.shipFolders()) {
			File f = new File(d, "voyage.log");
			if (!f.isFile()) continue;
			String ship = shipNamed(v, d);
			if (ship == null) continue;
			for (String l : text(f).split("\r?\n")) {
				if (l.length() < 18 || (before != null && l.substring(0, 16).compareTo(before) >= 0)) continue;
				String line = l.substring(16).trim();
				if (line.startsWith("Crew joined: ") || line.startsWith("Crew lost: ")) older.add(new Object[] {l.substring(0, 16), new String[] {"0", "voyage: " + ship, line, line}});
			}
		}
		Collections.sort(older, new Comparator<Object[]>() { public int compare(Object[] a, Object[] b) { return ((String) a[0]).compareTo((String) b[0]); } }); // stable: a minute's lines keep their order
		List<String[]> all = new ArrayList<String[]>();
		for (Object[] o : older) all.add((String[]) o[1]);
		all.addAll(out);
		return all;
	}
	/** A ship's name from her history folder: hers in the fleet now, else the one her fate was written under; null if neither. */
	private static String shipNamed(Vault v, File dir) {
		String id = ShipStore.idOf(dir);
		Ship s = id == null ? null : v.byId(id);
		if (s != null) return s.name;
		File fate = new File(dir, "fate.txt");
		if (!fate.isFile()) return null;
		String[] w = text(fate).split("\r?\n");
		return w.length > 1 && !w[1].trim().isEmpty() ? w[1].trim() : null;
	}
	private static List<Member> whoever(String name, String race, Map<String, List<Member>> byName, Map<String, String> renamedFrom, List<Member> members, Status ifNew) {
		String now = name;
		for (int i = 0; i < 10 && renamedFrom.containsKey(now); i++) now = renamedFrom.get(now);
		List<Member> l = byName.get(now);
		if (l != null && ifNew == null) return l;
		if (l != null) { // an ending (killed, lost, retired): never pinned on someone found alive now, a namesake's past
			List<Member> ended = new ArrayList<Member>();
			for (Member m : l) if (m.status != Status.PRESENT && m.status != Status.CAPTIVE) ended.add(m);
			if (!ended.isEmpty()) return ended;
		}
		if (ifNew == null) return Collections.emptyList();
		Member m = new Member(nextId(members));
		m.name = now; m.race = race == null ? "human" : race.toLowerCase(); m.title = race == null ? "" : race; m.male = true; m.status = ifNew;
		members.add(m);
		if (l == null) byName.put(now, l = new ArrayList<Member>());
		l.add(m);
		return Collections.singletonList(m);
	}
	private static void station(String head, String whole, int day, Map<String, List<Member>> byName, Map<String, String> renamedFrom, List<Member> members) {
		java.util.regex.Matcher x;
		if ((x = java.util.regex.Pattern.compile("^HIRE\\s+(?:A promise of adventure|Posted for volunteers)[^:]*: (.+?) \\((\\w+)\\) joined").matcher(head)).find()) { // the job board's wording (5.61)
			for (Member m : whoever(x.group(1), x.group(2), byName, renamedFrom, members, null)) m.events.add(new Event(day, "Hired."));
		} else if ((x = java.util.regex.Pattern.compile("^HIRE\\s+(.+?) \\((\\w+)\\)(,? rescued on an expedition)?").matcher(head)).find()) {
			for (Member m : whoever(x.group(1), x.group(2), byName, renamedFrom, members, null)) m.events.add(new Event(day, x.group(3) != null ? "Rescued on an expedition, and signed on." : "Hired."));
		} else if ((x = java.util.regex.Pattern.compile("^CREW\\s+(.+?) assigned to (.+?)\\.?$").matcher(head)).find()) {
			String to = x.group(2);
			for (Member m : whoever(x.group(1), null, byName, renamedFrom, members, null)) {
				m.events.add(new Event(day, to.endsWith("Cargo Hold") ? "Moved to the Cargo Hold." : "Assigned to " + to + "."));
				if (!to.endsWith("Cargo Hold")) servedOn(m, to.replaceFirst("^the ", ""));
			}
		} else if ((x = java.util.regex.Pattern.compile("^EXPEDITION\\s+(.+?) sent to (.+)$").matcher(head)).find()) {
			String sector = x.group(2).trim();
			for (String n : x.group(1).split(", ")) for (Member m : whoever(n.trim(), null, byName, renamedFrom, members, null)) m.events.add(new Event(day, "Sent on an expedition " + sectorPhrase(sector).replaceFirst("^in ", "to ") + "."));
		} else if ((x = java.util.regex.Pattern.compile("^EXPEDITION\\s+(.+?) back from (.+?) \\(").matcher(head)).find()) {
			String sector = x.group(2).trim();
			for (String n : x.group(1).split(", ")) {
				n = n.trim();
				if (listed(head, "killed: ", n)) { for (Member m : whoever(n, null, byName, renamedFrom, members, Status.KILLED)) m.events.add(new Event(day, "Killed on an expedition " + sectorPhrase(sector) + ".")); continue; }
				if (listed(head, "taken: ", n)) { for (Member m : whoever(n, null, byName, renamedFrom, members, null)) m.events.add(new Event(day, "Taken captive on an expedition " + sectorPhrase(sector) + ".")); continue; }
				for (Member m : whoever(n, null, byName, renamedFrom, members, null)) m.events.add(new Event(day, "Back from an expedition " + sectorPhrase(sector) + (listed(head, "to the infirmary: ", n) ? "; to the infirmary." : ".")));
			}
		} else if (head.startsWith("EXPEDITION") && head.contains("did not come back: ")) {
			for (String n : head.substring(head.indexOf("did not come back: ") + 19).split(";")[0].split(", "))
				for (Member m : whoever(n.trim(), null, byName, renamedFrom, members, Status.KILLED)) m.events.add(new Event(day, "Did not come back from an expedition."));
		} else if (head.startsWith("RENAME CREW")) {
			String[] w = head.substring(11).trim().replaceAll("\\s+\\(.*$", "").split(" -> ", 2);
			String rank = w.length == 2 ? homeplanet.model.Rank.promotion(w[0].trim(), w[1].trim()) : null;
			if (w.length == 2) for (Member m : whoever(w[1].trim(), null, byName, renamedFrom, members, null)) m.events.add(new Event(day, rank != null ? "Promoted to " + rank + "." : "Now known as " + w[1].trim() + " (was " + w[0].trim() + ")."));
		} else if (head.startsWith("TRADE  ")) { // a Cargo Bay trade: who left a ship, then who came aboard one (5.51)
			List<String[]> off = new ArrayList<String[]>(), on = new ArrayList<String[]>();
			String ship = null;
			String[] lines = whole.split("\n");
			for (int i = 1; i < lines.length; i++) {
				String l = lines[i].trim();
				if (l.endsWith(":") && !l.startsWith("- ") && !l.startsWith("+ ")) { ship = l.substring(0, l.length() - 1).trim(); continue; }
				if (ship == null || ship.equals(HOLD_NAME)) continue; // the Cargo Hold is no ship
				if (l.startsWith("- Crew ")) off.add(new String[] {ship, l.substring(7)});
				else if (l.startsWith("+ Crew ")) on.add(new String[] {ship, l.substring(7)});
			}
			off.addAll(on);
			for (String[] w : off) { // a trade names no race: only a name one member alone has
				List<Member> l = whoever(w[1], null, byName, renamedFrom, members, null);
				if (l.size() == 1) servedOn(l.get(0), w[0]);
			}
		} else if (head.startsWith("COMMISSION  ")) { // her starting crew, named since 5.51
			String ship = head.substring(12).replaceAll("\\s+\\([0-9a-f]+\\)\\s*$", "").trim();
			for (String l : whole.split("\n")) {
				l = l.trim();
				if (!l.startsWith("Crew: ")) continue;
				for (String one : l.substring(6).split(", ")) {
					java.util.regex.Matcher r = java.util.regex.Pattern.compile("^\\s*(.+?) \\((\\w[\\w ]*)\\)").matcher(one);
					if (!r.find()) continue;
					List<Member> l2 = ofRace(whoever(r.group(1), r.group(2), byName, renamedFrom, members, null), r.group(2));
					if (l2.size() != 1) continue;
					l2.get(0).events.add(new Event(day, "Came aboard " + the(ship) + ", newly commissioned."));
					servedOn(l2.get(0), ship);
				}
			}
		} else if (head.startsWith("RETIRE")) {
			String[] lines = whole.split("\n");
			for (int i = 1; i < lines.length; i++) { // its detail lines (indented in the station log, not in the master log's copy)
				String l = lines[i];
				java.util.regex.Matcher r = java.util.regex.Pattern.compile("^\\s*(.+?) \\((\\w[\\w ]*)\\)").matcher(l);
				if (r.find()) for (Member m : whoever(r.group(1), r.group(2), byName, renamedFrom, members, Status.RETIRED)) { if (m.status != Status.PRESENT) m.where = "retired"; m.events.add(new Event(day, "Retired from the station's service.")); }
			}
		}
	}
	private static void voyage(String ship, String text, int day, Map<String, List<Member>> byName, Map<String, String> renamedFrom, List<Member> members) {
		for (String[] kind : new String[][] {{"Crew joined: ", "Came aboard " + the(ship) + "."}, {"Crew lost: ", "Lost aboard " + the(ship) + "."}}) {
			if (!text.startsWith(kind[0])) continue;
			for (String one : text.substring(kind[0].length()).split(",")) {
				java.util.regex.Matcher r = java.util.regex.Pattern.compile("^\\s*(.+?) \\((\\w[\\w ]*)\\)").matcher(one);
				if (!r.find()) continue;
				boolean lost = kind[0].startsWith("Crew lost");
				List<Member> who = whoever(r.group(1), r.group(2), byName, renamedFrom, members, lost ? Status.KILLED : null);
				if (!lost && !ofRace(who, r.group(2)).isEmpty()) who = ofRace(who, r.group(2)); // a namesake of another race never joined her
				for (Member m : who) {
					m.events.add(new Event(day, kind[1]));
					servedOn(m, ship);
					if (lost && m.status != Status.PRESENT && m.status != Status.CAPTIVE) m.where = "lost aboard " + the(ship);
				}
			}
		}
	}

	/** A ship to the end of their list of ships, once in a row. */
	private static void servedOn(Member m, String ship) { if (m.served.isEmpty() || !shipOf(m.served.get(m.served.size() - 1)).equals(shipOf(ship))) m.served.add(ship); }
	/** Those of this race, by FTL's title for it or its id. */
	private static List<Member> ofRace(List<Member> l, String race) {
		List<Member> out = new ArrayList<Member>();
		for (Member m : l) if (race.equalsIgnoreCase(m.raceTitle()) || race.equalsIgnoreCase(m.race)) out.add(m);
		return out;
	}
	private static final String HOLD_NAME = "Spacedock Storage";

	/**
	 * A register from before 5.51: everyone's ships read again from the logs (the voyage logs and trades too), then any
	 * ship only the register knew, then the one they're aboard now. Only the ships: their careers stay as written.
	 */
	private static void rebuildServed(Vault v, String hist, List<Member> members) {
		List<Member> copies = new ArrayList<Member>();
		for (Member m : members) {
			Member c = new Member(m.id);
			c.name = m.name; c.race = m.race; c.title = m.title; c.male = m.male; c.status = m.status;
			copies.add(c);
		}
		backfill(v, copies); // on copies: their events, and anyone the logs alone know of, are let go
		renamedShips(v, hist, copies);
		for (int i = 0; i < members.size(); i++) {
			Member m = members.get(i);
			List<String> was = new ArrayList<String>(m.served);
			m.served.clear();
			for (String sh : copies.get(i).served) servedOn(m, sh);
			Ship s = m.status == Status.PRESENT && m.place.startsWith("ship:") ? v.byId(m.place.substring(5)) : null;
			if (s != null && !m.served.isEmpty() && shipOf(m.served.get(m.served.size() - 1)).equals(s.name)) m.served.remove(m.served.size() - 1); // hers comes last
			for (String sh : was) {
				boolean known = s != null && shipOf(sh).equals(s.name);
				for (String k : m.served) if (shipOf(k).equals(shipOf(sh)) || formerNames(k).contains(shipOf(sh))) known = true;
				if (!known) servedOn(m, sh);
			}
			if (s != null) servedOn(m, s.name);
		}
	}

	/**
	 * A ship renamed (the station log's RENAME, with her id) is one ship: in every list she's kept once, under her name
	 * now, with the names she had before. A name another ship has now, or more than one renamed ship had, is left alone.
	 */
	private static boolean renamedShips(Vault v, String hist, List<Member> members) {
		Map<String, List<String>> names = new LinkedHashMap<String, List<String>>(); // id -> her names, oldest first
		java.util.regex.Pattern p = java.util.regex.Pattern.compile("^\\S+ \\S+  RENAME  (.+?) -> (.+?)  \\(([0-9a-f]+)\\)\\s*$");
		for (String l : hist.split("\r?\n")) {
			java.util.regex.Matcher x = p.matcher(l);
			if (!x.find()) continue;
			List<String> n = names.get(x.group(3));
			if (n == null) names.put(x.group(3), n = new ArrayList<String>());
			if (n.isEmpty()) n.add(x.group(1).trim());
			n.remove(x.group(2).trim()); // renamed back: her latest use of a name counts
			n.add(x.group(2).trim());
		}
		if (names.isEmpty()) return false;
		Map<String, String> shipOfName = new LinkedHashMap<String, String>(); // a name -> the one ship that had it
		java.util.Set<String> shared = new java.util.HashSet<String>();
		for (Map.Entry<String, List<String>> e : names.entrySet()) for (String n : e.getValue()) { if (shipOfName.containsKey(n) && !shipOfName.get(n).equals(e.getKey())) shared.add(n); shipOfName.put(n, e.getKey()); }
		for (Ship s : v.all()) if (shipOfName.containsKey(s.name) && !shipOfName.get(s.name).equals(s.id)) shared.add(s.name);
		boolean changed = false;
		for (Member m : members) {
			List<String> now = new ArrayList<String>();
			for (String entry : m.served) {
				String id = shared.contains(shipOf(entry)) ? null : shipOfName.get(shipOf(entry));
				if (id != null) {
					List<String> n = names.get(id);
					String latest = n.get(n.size() - 1);
					java.util.LinkedHashSet<String> before = new java.util.LinkedHashSet<String>();
					for (String f : formerNames(entry)) if (!n.contains(f)) before.add(f);
					before.addAll(n.subList(0, n.size() - 1));
					before.remove(latest);
					entry = joinNames(latest, before);
				}
				if (!now.isEmpty() && shipOf(now.get(now.size() - 1)).equals(shipOf(entry))) { // the same ship twice in a row: once, with all her names
					java.util.LinkedHashSet<String> f = new java.util.LinkedHashSet<String>(formerNames(now.remove(now.size() - 1)));
					f.addAll(formerNames(entry));
					entry = joinNames(shipOf(entry), f);
				}
				now.add(entry);
			}
			if (!now.equals(m.served)) { m.served.clear(); m.served.addAll(now); changed = true; }
			for (List<String> where : m.with.values()) { // who they served with: the ship under her name now
				List<String> named = new ArrayList<String>();
				for (String sh : where) {
					String id = shared.contains(sh) ? null : shipOfName.get(sh);
					String to = id == null ? sh : names.get(id).get(names.get(id).size() - 1);
					if (!named.contains(to)) named.add(to);
				}
				if (!named.equals(where)) { where.clear(); where.addAll(named); changed = true; }
			}
		}
		return changed;
	}
	private static String joinNames(String now, java.util.Collection<String> before) {
		StringBuilder sb = new StringBuilder(now);
		for (String f : before) sb.append('\t').append(f);
		return sb.toString();
	}
	/**
	 * Crew known only from the old logs get a look as a new volunteer of their race would (heromedel, 5.41), rolled from
	 * their id so it's always the same, and kept: their name, their race, no skills known.
	 */
	private static boolean invent(Member m) {
		try {
			CrewState c = homeplanet.parser.Commission.volunteer(raceId(m), new java.util.Random(m.id * 7919L + 17));
			c.setName(m.name);
			m.male = c.isMale();
			m.rec.putAll(homeplanet.comm.Line.crewFields(c));
			if (m.title.isEmpty()) m.title = homeplanet.model.Crew.raceTitle(c);
			return true;
		} catch (Exception e) { return false; }
	}
	/** FTL's race id for a member (one read from the logs has FTL's title: Zoltan is energy, Lanius anaerobic). */
	private static String raceId(Member m) {
		String r = m.race == null ? "human" : m.race;
		try {
			java.util.Map<String, net.blerf.ftl.xml.CrewBlueprint> crews = net.blerf.ftl.parser.DataManager.get().getCrews();
			if (crews.containsKey(r)) return r;
			for (Map.Entry<String, net.blerf.ftl.xml.CrewBlueprint> e : crews.entrySet()) {
				net.blerf.ftl.xml.CrewBlueprint b = e.getValue();
				if (b.getTitle() != null && r.equalsIgnoreCase(b.getTitle().getTextValue())) { m.race = e.getKey(); return e.getKey(); }
			}
		} catch (RuntimeException e) { /* no game data: human */ }
		return "human";
	}
	/**
	 * Which register entry this crew member is (a crew popup's "Crew Log..."), matched as a look matches them; the
	 * living first. -1 if none.
	 */
	public static synchronized int identify(Vault v, CrewState c) {
		Found x = found(c, "", "");
		int best = -1, bestScore = 0;
		for (Member m : members(v)) {
			int s = score(x, m);
			if (s > 0 && (m.status == Status.PRESENT || m.status == Status.CAPTIVE)) s += 100;
			if (s > bestScore) { bestScore = s; best = m.id; }
		}
		return best;
	}

	// ---- small helpers ----

	/** "the Kestrel", but "The Adjudicator" as she is (never "the The..."). */
	static String the(String ship) { return ship == null ? "" : homeplanet.parser.ShipNames.the(ship); }
	/** "in a Rebel Controlled Sector", "in the Crystal Worlds". */
	static String sectorPhrase(String sector) {
		if (sector.endsWith("Worlds") || sector.toLowerCase().startsWith("the ")) return "in " + the(sector);
		return "in " + homeplanet.model.Words.a(sector);
	}
	private static String join(int[] n) { StringBuilder s = new StringBuilder(); for (int i = 0; i < n.length; i++) s.append(i == 0 ? "" : ",").append(n[i]); return s.toString(); }
	private static String text(File f) {
		if (f == null || !f.isFile()) return "";
		try { return new String(SafeFiles.read(f), StandardCharsets.UTF_8); } catch (IOException e) { return ""; }
	}
}
