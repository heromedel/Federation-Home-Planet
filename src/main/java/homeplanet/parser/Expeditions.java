package homeplanet.parser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.core.HistoryLog;
import homeplanet.core.SafeFiles;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * Expeditions: crew from the Cargo Hold sent out on a posted job, no ship of the fleet's needed. The board always
 * holds three postings, each in one of FTL's sector types (now and then a sealed one, its sector told only once under
 * way); a finished one is replaced at once. An expedition is two or three events from that sector's pool, of the
 * posting's danger, each a pop-up of choices that may lead on to more (the events files, read by this small engine).
 * Some choices open only with a crew member of a race (blue, or red where the race makes things worse); every gamble's
 * odds rise with more crew and fall with the injured. It pays a little scrap, now and then gear or a new crew member,
 * and crew can come back hurt, or not at all. Each finished expedition counts as one beacon of the fleet's time. The
 * board, and the events met lately (not met again soon), are kept in the fleet's expeditions.txt.
 */
public final class Expeditions {
	private static final Logger log = LoggerFactory.getLogger(Expeditions.class);
	private Expeditions() { }

	public static final int POSTINGS = 3, PARTY_MAX = 3;
	/** One posting in this many is in a rare place (a homeworld, the Hidden Crystal Worlds); one in SEALED_ONE_IN is sealed. */
	public static final int RARE_ONE_IN = 10, SEALED_ONE_IN = 8;
	/** One event in this many is a level riskier than the posting said. */
	public static final int SURPRISE_ONE_IN = 5;
	/** Each crew member beyond the first adds this to a gamble's odds; each injured one takes INJURED_ODDS off. */
	public static final int CREW_ODDS = 5, INJURED_ODDS = 3;
	/** In a fight: each Mantis adds this, each Rock ROCK_FIGHT, each Engi takes ENGI_FIGHT off (FTL's fighters and repairers). */
	public static final int MANTIS_FIGHT = 8, ROCK_FIGHT = 4, ENGI_FIGHT = 5;
	/** Events met in the last this many expeditions' worth aren't met again while others are left. */
	public static final int RECENT = 8;

	/** FTL's sector types, as its own data names them; the rare ones draw on their race's events as well as their own. */
	private static final String[][] SECTORS = {
		{"civilian", "Civilian Sector", ""}, {"engi", "Engi Controlled Sector", ""}, {"zoltan", "Zoltan Controlled Sector", ""},
		{"mantis", "Mantis Controlled Sector", ""}, {"rock", "Rock Controlled Sector", ""}, {"slug", "Slug Controlled Nebula", ""},
		{"nebula", "Uncharted Nebula", ""}, {"pirate", "Pirate Controlled Sector", ""}, {"rebel", "Rebel Controlled Sector", ""},
		{"abandoned", "Abandoned Sector", ""},
		{"engi_home", "Engi Homeworlds", "engi"}, {"mantis_home", "Mantis Homeworlds", "mantis"}, {"rock_home", "Rock Homeworlds", "rock"},
		{"zoltan_home", "Zoltan Homeworlds", "zoltan"}, {"slug_home", "Slug Home Nebula", "slug"}, {"crystal", "Hidden Crystal Worlds", ""}};
	public static String sectorName(String kind) { for (String[] s : SECTORS) if (s[0].equals(kind)) return s[1]; return kind; }
	static boolean rare(String kind) { return kind.endsWith("_home") || "crystal".equals(kind); }
	static String parentOf(String kind) { for (String[] s : SECTORS) if (s[0].equals(kind)) return s[2]; return ""; }
	static boolean knownKind(String kind) { for (String[] s : SECTORS) if (s[0].equals(kind)) return true; return "any".equals(kind); }
	/** Every sector kind (for tests). */
	public static List<String> kinds() { List<String> out = new ArrayList<String>(); for (String[] s : SECTORS) out.add(s[0]); return out; }

	/** FTL's race ids by the names the events use. */
	private static final Map<String, String> RACES = new LinkedHashMap<String, String>();
	static {
		RACES.put("human", "human"); RACES.put("engi", "engi"); RACES.put("mantis", "mantis"); RACES.put("rock", "rock"); RACES.put("slug", "slug");
		RACES.put("zoltan", "energy"); RACES.put("crystal", "crystal"); RACES.put("lanius", "anaerobic");
	}
	/** A race as the option tags show it: "Rock", "Zoltan". */
	static String raceWord(String name) { return name.substring(0, 1).toUpperCase() + name.substring(1); }

	// ---- the events files ----

	/** What a choice comes to: scrap, gear, a recruit, hurt or lost crew, a next step, and its words. */
	public static final class Outcome {
		int scrapMin, scrapMax, injure, lose, fuel, missiles, parts;
		String item; // weapon, drone or augment
		String join; // a race, or "any": someone joins the crew
		String then; // the event's next step, or null: on to the next event
		boolean end;
		String text = "";
	}
	/** One way through a step. */
	public static final class Choice {
		public String text = "";
		/** The race it needs aboard (an events-file name), or null. */
		public String race;
		/** A red option: the race makes things worse. */
		public boolean red;
		/** A fight: Mantis and Rock help, Engi don't. */
		public boolean fight;
		int roll = -1;
		Outcome sure, win, lose;
		/** As the button shows it: the race in brackets first. */
		public String label() { return race == null ? text : "(" + raceWord(race) + ") " + text; }
	}
	/** A pop-up: words and choices. An event's first step is "", the rest named. */
	public static final class Step {
		public String text = "";
		public final List<Choice> choices = new ArrayList<Choice>();
	}
	public static final class Event {
		public String id = "";
		final Set<String> kinds = new HashSet<String>();
		/** 1 low, 2 moderate, 3 high. */
		public int risk = 1;
		/** What it's about ("spiders", "patrol"): one run meets a theme once. */
		public String theme = "";
		final Map<String, Step> steps = new LinkedHashMap<String, Step>();
		Step first() { return steps.get(""); }
	}
	public static final class Posting {
		public final String kind, text;
		public final int danger;
		/** Sealed: the sector isn't told until the crew are under way. */
		public final boolean sealed;
		Posting(String kind, int danger, String text) { this(kind, danger, text, false); }
		Posting(String kind, int danger, String text, boolean sealed) { this.kind = kind; this.danger = danger; this.text = text; this.sealed = sealed; }
		/** The sector as the board shows it: "Destination undisclosed" for a sealed posting. */
		public String sector() { return sealed ? "Destination undisclosed" : sectorName(kind); }
		/** Where the job really is. */
		public String realSector() { return sectorName(kind); }
		public String dangerWord() { return sealed ? "Unknown" : danger <= 1 ? "Low" : danger == 2 ? "Moderate" : "High"; }
	}
	static final class Book {
		final List<Posting> postings = new ArrayList<Posting>(), sealed = new ArrayList<Posting>();
		final List<Event> events = new ArrayList<Event>();
		final List<String> problems = new ArrayList<String>();
		/** Every event's and choice's words, for the harness's check against FTL's own text. */
		final List<String> words = new ArrayList<String>();
	}
	private static Book book;
	static synchronized Book book() {
		if (book == null) {
			book = new Book();
			read("expeditions.txt", book, new HashSet<String>());
			check(book);
			for (String p : book.problems) log.warn("Expedition events: {}", p);
		}
		return book;
	}
	/** Reads an events file into the book, and the files it includes. */
	private static void read(String name, Book b, Set<String> seen) {
		if (!seen.add(name)) return;
		InputStream in = Expeditions.class.getResourceAsStream("/homeplanet/resource/" + name);
		if (in == null) { b.problems.add(name + " is missing"); return; }
		String all;
		try { all = new String(readAll(in), StandardCharsets.UTF_8); }
		catch (IOException e) { b.problems.add(name + " can't be read: " + e); return; }
		for (String inc : parse(all, b, name)) read(inc, b, seen);
	}
	private static byte[] readAll(InputStream in) throws IOException {
		try {
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
			return out.toByteArray();
		} finally { in.close(); }
	}

	/** Reads one events file (its header gives the format) into the book; returns the files it includes. */
	static List<String> parse(String all, Book b, String file) {
		List<String> includes = new ArrayList<String>();
		Event ev = null;
		Step st = null;
		Choice ch = null;
		int n = 0;
		for (String raw : all.split("\r?\n")) {
			n++;
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			try {
				if (line.startsWith("include ")) {
					includes.add(line.substring(8).trim());
				} else if (line.startsWith("posting ")) {
					String[] p = line.split("\\s+", 4);
					if ("sealed".equals(p[1])) b.sealed.add(new Posting("", Integer.parseInt(p[2]), p[3], true));
					else if (!knownKind(p[1]) || "any".equals(p[1])) throw new IllegalArgumentException("unknown sector " + p[1]);
					else b.postings.add(new Posting(p[1], Integer.parseInt(p[2]), p[3]));
					ev = null; st = null; ch = null;
				} else if (line.startsWith("event ")) {
					String[] p = line.split("\\s+");
					if (p.length < 4) throw new IllegalArgumentException("an event needs: event <id> <sectors> <low|moderate|high> [<theme>]");
					ev = new Event();
					ev.id = p[1];
					for (String k : p[2].split(",")) {
						if (!knownKind(k)) throw new IllegalArgumentException("unknown sector " + k);
						ev.kinds.add(k);
					}
					ev.risk = "low".equals(p[3]) ? 1 : "moderate".equals(p[3]) ? 2 : "high".equals(p[3]) ? 3 : 0;
					if (ev.risk == 0) throw new IllegalArgumentException("risk must be low, moderate or high: " + p[3]);
					ev.theme = p.length > 4 ? p[4] : ev.id;
					st = new Step();
					ev.steps.put("", st);
					b.events.add(ev);
					ch = null;
				} else if (ev == null) {
					throw new IllegalArgumentException("text outside an event");
				} else if (line.startsWith("step ")) {
					String name = line.substring(5).trim();
					if (ev.steps.containsKey(name)) throw new IllegalArgumentException("step " + name + " twice in " + ev.id);
					st = new Step();
					ev.steps.put(name, st);
					ch = null;
				} else if (line.startsWith("*")) {
					ch = new Choice();
					String t = line.substring(1).trim();
					if (t.startsWith("[")) {
						for (String tag : t.substring(1, t.indexOf(']')).trim().split("\\s+")) {
							if (tag.equals("red")) ch.red = true;
							else if (tag.equals("fight")) ch.fight = true;
							else if (RACES.containsKey(tag)) ch.race = tag;
							else throw new IllegalArgumentException("unknown tag " + tag);
						}
						if (ch.red && ch.race == null) throw new IllegalArgumentException("a red option needs a race");
						t = t.substring(t.indexOf(']') + 1).trim();
					}
					ch.text = t;
					st.choices.add(ch);
					b.words.add(t);
				} else if (ch == null) {
					st.text = st.text.isEmpty() ? line : st.text + " " + line;
				} else if (line.startsWith("roll ")) {
					ch.roll = Integer.parseInt(line.substring(5).trim());
				} else if (line.startsWith("win ")) {
					ch.win = outcome(line.substring(4), b);
				} else if (line.startsWith("lose ")) {
					ch.lose = outcome(line.substring(5), b);
				} else if (line.startsWith("ok ")) {
					ch.sure = outcome(line.substring(3), b);
				} else {
					throw new IllegalArgumentException("can't read: " + line);
				}
			} catch (RuntimeException e) {
				b.problems.add(file + " line " + n + ": " + e.getMessage());
			}
		}
		for (Event e : b.events) for (Step s : e.steps.values()) if (!s.text.isEmpty() && !b.words.contains(s.text)) b.words.add(s.text);
		return includes;
	}
	/** What's wrong with the book as a whole: steps without two ways through, outcomes missing, steps nobody reaches. */
	static void check(Book b) {
		Set<String> ids = new HashSet<String>();
		for (Event e : b.events) {
			if (!ids.add(e.id)) b.problems.add("event " + e.id + " twice");
			Set<String> reached = new HashSet<String>();
			reached.add("");
			for (Map.Entry<String, Step> en : e.steps.entrySet()) {
				String where = "event " + e.id + (en.getKey().isEmpty() ? "" : " step " + en.getKey());
				Step s = en.getValue();
				int open = 0;
				for (Choice c : s.choices) {
					if (c.race == null) open++;
					if (c.roll >= 0 ? c.win == null || c.lose == null : c.sure == null) { b.problems.add(where + ": \"" + c.text + "\" has no outcome"); continue; }
					for (Outcome o : c.roll >= 0 ? new Outcome[] {c.win, c.lose} : new Outcome[] {c.sure}) {
						if (o.then == null) continue;
						if (!e.steps.containsKey(o.then) || o.then.isEmpty()) b.problems.add(where + ": no step " + o.then);
						reached.add(o.then);
					}
				}
				if (open < 2) b.problems.add(where + ": fewer than two ways through without a race");
				if (s.text.isEmpty()) b.problems.add(where + ": no text");
			}
			for (String s : e.steps.keySet()) if (!reached.contains(s)) b.problems.add("event " + e.id + ": step " + s + " is never reached");
			if (loops(e, "", new HashSet<String>())) b.problems.add("event " + e.id + ": its steps go round in a circle");
		}
	}
	private static boolean loops(Event e, String step, Set<String> path) {
		if (!path.add(step)) return true;
		Step s = e.steps.get(step);
		if (s != null) for (Choice c : s.choices) for (Outcome o : new Outcome[] {c.sure, c.win, c.lose})
			if (o != null && o.then != null && e.steps.containsKey(o.then) && loops(e, o.then, path)) return true;
		path.remove(step);
		return false;
	}
	/** "scrap 5-10 injure 1 then deeper | words" */
	private static Outcome outcome(String s, Book b) {
		Outcome o = new Outcome();
		int bar = s.indexOf('|');
		if (bar < 0) throw new IllegalArgumentException("an outcome needs | and its words");
		o.text = s.substring(bar + 1).trim();
		if (o.text.isEmpty()) throw new IllegalArgumentException("an outcome needs words");
		b.words.add(o.text);
		String[] t = s.substring(0, bar).trim().split("\\s+");
		for (int i = 0; i < t.length; i++) {
			String k = t[i];
			if (k.isEmpty()) continue;
			if (k.equals("end")) { o.end = true; continue; }
			String v = t[++i];
			if (k.equals("scrap")) {
				String[] r = v.split("-");
				o.scrapMin = Integer.parseInt(r[0]);
				o.scrapMax = Integer.parseInt(r[r.length - 1]);
			} else if (k.equals("injure")) o.injure = Integer.parseInt(v);
			else if (k.equals("lose")) o.lose = Integer.parseInt(v);
			else if (k.equals("fuel")) o.fuel = Integer.parseInt(v);
			else if (k.equals("missiles")) o.missiles = Integer.parseInt(v);
			else if (k.equals("parts")) o.parts = Integer.parseInt(v);
			else if (k.equals("then")) o.then = v;
			else if (k.equals("join")) {
				if (!v.equals("any") && !RACES.containsKey(v)) throw new IllegalArgumentException("unknown race " + v);
				o.join = v;
			} else if (k.equals("item")) {
				if (!v.equals("weapon") && !v.equals("drone") && !v.equals("augment")) throw new IllegalArgumentException("unknown item " + v);
				o.item = v;
			} else throw new IllegalArgumentException("unknown effect " + k);
		}
		return o;
	}
	/** What's wrong with the events files, if anything (the harness checks it's nothing). */
	public static List<String> problems() { return book().problems; }
	public static int eventCount() { return book().events.size(); }
	/** Every line of words the events show, for the harness to hold against FTL's own. */
	public static List<String> allWords() { return book().words; }
	/** Events an expedition to this sector could meet. */
	static List<Event> pool(String kind) {
		List<Event> out = new ArrayList<Event>();
		String parent = parentOf(kind);
		for (Event e : book().events)
			if (e.kinds.contains(kind) || e.kinds.contains("any") && !rare(kind) || !parent.isEmpty() && e.kinds.contains(parent)) out.add(e);
		return out;
	}
	/** Events of this sector's own (not the general ones), for the harness. */
	public static int ownEvents(String kind) {
		int n = 0;
		for (Event e : book().events) if (e.kinds.contains(kind)) n++;
		return n;
	}

	// ---- the board ----

	private static File boardFile(Vault v) { return new File(v.root, "expeditions.txt"); }
	/** The three postings, the board filled first where a place is empty. */
	public static synchronized List<Posting> board(Vault v) {
		Properties p = readBoard(v);
		boolean changed = false;
		List<Posting> out = new ArrayList<Posting>();
		Random rng = new Random();
		for (int i = 0; i < POSTINGS; i++) {
			Posting x = posting(p, i);
			if (x == null) { x = pick(rng, out); put(p, i, x); changed = true; }
			out.add(x);
		}
		if (changed) try { writeBoard(v, p); } catch (IOException e) { log.warn("Could not write the expeditions board: {}", e.toString()); }
		return out;
	}
	private static Posting posting(Properties p, int i) {
		String kind = p.getProperty(i + ".kind"), text = p.getProperty(i + ".text");
		if (kind == null || text == null || !knownKind(kind) || "any".equals(kind)) return null;
		try { return new Posting(kind, Integer.parseInt(p.getProperty(i + ".danger", "1")), text, "true".equals(p.getProperty(i + ".sealed"))); }
		catch (NumberFormatException e) { return null; }
	}
	private static void put(Properties p, int i, Posting x) {
		p.setProperty(i + ".kind", x.kind); p.setProperty(i + ".danger", Integer.toString(x.danger)); p.setProperty(i + ".text", x.text);
		p.setProperty(i + ".sealed", Boolean.toString(x.sealed));
	}
	/** A posting not already on the board: one in SEALED_ONE_IN sealed (its sector rolled now, kept hidden), one in RARE_ONE_IN somewhere rare. */
	static Posting pick(Random rng, List<Posting> taken) {
		Book b = book();
		if (!b.sealed.isEmpty() && rng.nextInt(SEALED_ONE_IN) == 0) {
			Posting s = b.sealed.get(rng.nextInt(b.sealed.size()));
			List<String> where = new ArrayList<String>();
			boolean rare = rng.nextInt(6) == 0;
			for (String[] k : SECTORS) if (rare(k[0]) == rare) where.add(k[0]);
			boolean on = false;
			for (Posting t : taken) if (t.text.equals(s.text)) on = true;
			if (!on) return new Posting(where.get(rng.nextInt(where.size())), s.danger, s.text, true);
		}
		boolean wantRare = rng.nextInt(RARE_ONE_IN) == 0;
		List<Posting> common = new ArrayList<Posting>(), rare = new ArrayList<Posting>();
		for (Posting x : b.postings) {
			boolean on = false;
			for (Posting t : taken) if (t.text.equals(x.text)) on = true;
			if (!on) (rare(x.kind) ? rare : common).add(x);
		}
		List<Posting> from = wantRare && !rare.isEmpty() ? rare : common.isEmpty() ? rare : common;
		return from.isEmpty() ? new Posting("civilian", 1, "Hands wanted for a supply run") : from.get(rng.nextInt(from.size()));
	}
	private static Properties readBoard(Vault v) {
		Properties p = new Properties();
		File f = boardFile(v);
		if (!f.isFile()) return p;
		try { p.load(new java.io.StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); }
		return p;
	}
	private static void writeBoard(Vault v, Properties p) throws IOException {
		java.io.StringWriter w = new java.io.StringWriter();
		p.store(w, "The expeditions board: three postings, and the events met lately");
		SafeFiles.writeText(boardFile(v), w.toString(), false);
	}
	/** Events met lately, not to be met again while others are left. */
	static List<String> recent(Vault v) {
		List<String> out = new ArrayList<String>();
		for (String s : readBoard(v).getProperty("recent", "").split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
		return out;
	}

	// ---- an expedition ----

	/** The crew in the Cargo Hold, who can be sent. */
	public static List<CrewState> holdCrew(Vault v) throws IOException {
		return homeplanet.parser.SaveHelper.getOwnCrew(v.readCopy(v.storage()).save.getPlayerShip());
	}
	static boolean injured(CrewState c) { return c.getRace() != null && c.getHealth() < c.getRace().getMaxHealth(); }

	/** The events for a run: of the posting's danger (one in SURPRISE_ONE_IN a level riskier), none met lately, no theme twice. */
	static List<Event> draw(Posting posting, List<String> recent, Random rng) {
		List<Event> pool = pool(posting.kind);
		int n = posting.danger <= 1 ? 2 : posting.danger == 2 ? 2 + rng.nextInt(2) : 3;
		List<Event> out = new ArrayList<Event>();
		Set<String> themes = new HashSet<String>();
		for (int i = 0; i < n; i++) {
			int risk = Math.min(3, posting.danger + (rng.nextInt(SURPRISE_ONE_IN) == 0 ? 1 : 0));
			Event e = null;
			// the best fit first: its risk, not met lately; then any risk, not met lately; then anything not yet in this run
			for (int pass = 0; pass < 3 && e == null; pass++) {
				List<Event> fit = new ArrayList<Event>();
				for (Event x : pool) {
					if (out.contains(x) || themes.contains(x.theme)) continue;
					if (pass < 2 && recent.contains(x.id)) continue;
					if (pass == 0 && x.risk != risk) continue;
					if (pass == 1 && Math.abs(x.risk - risk) > 1) continue;
					fit.add(x);
				}
				if (!fit.isEmpty()) e = fit.get(rng.nextInt(fit.size()));
			}
			if (e == null) break;
			out.add(e);
			themes.add(e.theme);
		}
		return out;
	}

	/** One expedition under way: its events in order, the step it's at, and what has come of it so far. */
	public static final class Run {
		public final int slot;
		public final Posting posting;
		public final List<CrewState> party;
		private final List<Event> events;
		private int index = 0;
		private String step = "";
		private final Random rng;
		int scrap, fuel, missiles, parts;
		final List<String> items = new ArrayList<String>();
		final List<CrewState> joined = new ArrayList<CrewState>();
		final Map<CrewState, Integer> hurt = new HashMap<CrewState, Integer>();
		final List<CrewState> lost = new ArrayList<CrewState>();
		private boolean ended = false;

		Run(int slot, Posting posting, List<CrewState> party, List<String> recent, Random rng) {
			this.slot = slot; this.posting = posting; this.party = new ArrayList<CrewState>(party); this.rng = rng;
			this.events = draw(posting, recent, rng);
		}
		public boolean over() { return ended || index >= events.size() || alive().isEmpty(); }
		public Event event() { return over() ? null : events.get(index); }
		/** The pop-up showing now. */
		public Step current() { Event e = event(); return e == null ? null : e.steps.get(step); }
		/** Its words, filled in. */
		public String text() { Step s = current(); return s == null ? "" : fillEvent(s.text); }
		public int number() { return index + 1; }
		public int length() { return events.size(); }
		public List<Event> events() { return new ArrayList<Event>(events); }
		public List<CrewState> alive() {
			List<CrewState> out = new ArrayList<CrewState>(party);
			out.removeAll(lost);
			return out;
		}
		/** The step's choices open to this party: a race's only with one of that race still with them. */
		public List<Choice> choices() {
			List<Choice> out = new ArrayList<Choice>();
			Step s = current();
			if (s == null) return out;
			for (Choice c : s.choices) if (c.race == null || of(c.race) != null) out.add(c);
			return out;
		}
		private CrewState of(String race) {
			String id = RACES.get(race);
			for (CrewState c : alive()) if (c.getRace() != null && c.getRace().getId().equals(id)) return c;
			return null;
		}
		private int count(String race) {
			int n = 0;
			String id = RACES.get(race);
			for (CrewState c : alive()) if (c.getRace() != null && c.getRace().getId().equals(id)) n++;
			return n;
		}
		/** A gamble's odds for this party: more crew help, the injured don't, and in a fight, who they are matters. */
		public int odds(Choice c) {
			if (c.roll < 0) return 100;
			List<CrewState> here = alive();
			int injured = 0;
			for (CrewState x : here) if (hurt.containsKey(x) || Expeditions.injured(x)) injured++;
			int p = c.roll + CREW_ODDS * (here.size() - 1) - INJURED_ODDS * injured;
			if (c.fight) p += MANTIS_FIGHT * count("mantis") + ROCK_FIGHT * count("rock") - ENGI_FIGHT * count("engi");
			return Math.max(5, Math.min(95, p));
		}
		/** Takes a choice: its outcome (rolled, if it's a gamble) applied; returns its words. */
		public String choose(Choice c) {
			Outcome o = c.roll < 0 ? c.sure : rng.nextInt(100) < odds(c) ? c.win : c.lose;
			CrewState who = c.race != null ? of(c.race) : null;
			List<CrewState> here = alive();
			if (who == null) who = here.get(rng.nextInt(here.size()));
			CrewState anyone = here.get(rng.nextInt(here.size()));
			String said = fill(o.text, who, anyone);
			StringBuilder extra = new StringBuilder();
			int got = o.scrapMax <= 0 ? 0 : o.scrapMin + rng.nextInt(o.scrapMax - o.scrapMin + 1);
			scrap += got;
			fuel += o.fuel; missiles += o.missiles; parts += o.parts;
			if (o.item != null) {
				String id = gear(o.item, rng);
				if (id != null) { items.add(id); extra.append("\n\nFound: ").append(homeplanet.model.Items.title(id)).append("."); }
			}
			if (o.join != null) {
				List<String> races = new ArrayList<String>(RACES.values());
				String race = "any".equals(o.join) ? races.get(rng.nextInt(races.size())) : RACES.get(o.join);
				CrewState n = Commission.volunteer(race, rng);
				if (n != null) { joined.add(n); extra.append("\n\n").append(n.getName()).append(" (").append(homeplanet.model.Crew.raceTitle(n)).append(") joins the crew."); }
			}
			for (int i = 0; i < o.lose && !alive().isEmpty(); i++) {
				CrewState l = i == 0 && alive().contains(who) ? who : alive().get(rng.nextInt(alive().size()));
				lost.add(l);
				extra.append("\n\n").append(l.getName()).append(" did not come back.");
			}
			for (int i = 0; i < o.injure && !alive().isEmpty(); i++) {
				CrewState h = i == 0 && alive().contains(who) ? who : alive().get(rng.nextInt(alive().size()));
				int times = hurt.containsKey(h) ? hurt.get(h) + 1 : 1;
				if (times >= 2) { // hurt twice in one expedition: too much
					lost.add(h);
					extra.append("\n\n").append(h.getName()).append(" was hurt again, and did not make it.");
				} else {
					hurt.put(h, times);
					extra.append("\n\n").append(h.getName()).append(" is injured.");
				}
			}
			if (got > 0) extra.append("\n\n").append(got).append(" scrap.");
			if (o.fuel > 0) extra.append(" Fuel: ").append(o.fuel).append(".");
			if (o.missiles > 0) extra.append(" Missiles: ").append(o.missiles).append(".");
			if (o.parts > 0) extra.append(" Drone parts: ").append(o.parts).append(".");
			if (o.end) ended = true;
			if (o.then != null && !o.end && !alive().isEmpty()) step = o.then;
			else { index++; step = ""; }
			return said + extra;
		}
		private String fill(String t, CrewState who, CrewState anyone) {
			return t.replace("{who}", who.getName()).replace("{crew}", anyone.getName()).replace("{sector}", posting.realSector());
		}
		/** A choice's words as its button shows them: {who} is the crew member it would be about. */
		public String label(Choice c) {
			CrewState who = c.race != null ? of(c.race) : null;
			if (who == null && !alive().isEmpty()) who = alive().get(0);
			String name = who == null ? "someone" : who.getName();
			return c.label().replace("{who}", name).replace("{crew}", name).replace("{sector}", posting.realSector());
		}
		/** The job's own pay, for anyone who comes back: more for a dangerous one, half again for a sealed one. */
		int pay() {
			if (alive().isEmpty()) return 0;
			int lo = posting.danger <= 1 ? 3 : posting.danger == 2 ? 5 : 8, hi = posting.danger <= 1 ? 6 : posting.danger == 2 ? 10 : 14;
			int p = lo + new Random(posting.text.hashCode() ^ party.size()).nextInt(hi - lo + 1);
			return posting.sealed ? p * 3 / 2 : p;
		}
		public String fillEvent(String t) { return t.replace("{sector}", posting.realSector()).replace("{crew}", alive().isEmpty() ? "your crew" : alive().get(0).getName()); }
	}

	/** Sends these crew (from the Cargo Hold) on the posting in this place. */
	public static Run start(Vault v, int slot, List<CrewState> party, Random rng) throws IOException {
		if (party.isEmpty()) throw new IOException("No one was sent");
		if (party.size() > PARTY_MAX) throw new IOException("At most " + PARTY_MAX + " can go");
		return new Run(slot, board(v).get(slot), party, recent(v), rng);
	}

	/** A cheap piece of gear of this kind (weapon, drone, augment), as FTL's stores sell. */
	static String gear(String kind, Random rng) {
		List<String> from = new ArrayList<String>();
		if (kind.equals("weapon")) {
			for (net.blerf.ftl.xml.WeaponBlueprint w : DataManager.get().getWeapons().values())
				if (w.getCost() > 0 && w.getCost() <= 45 && w.getRarity() > 0 && !w.getId().startsWith("ARTILLERY")) from.add(w.getId());
		} else if (kind.equals("drone")) {
			for (net.blerf.ftl.xml.DroneBlueprint d : DataManager.get().getDrones().values()) if (d.getCost() > 0 && d.getCost() <= 50 && d.getRarity() > 0) from.add(d.getId());
		} else {
			for (net.blerf.ftl.xml.AugBlueprint a : DataManager.get().getAugments().values()) if (a.getCost() > 0 && a.getCost() <= 50 && a.getRarity() > 0) from.add(a.getId());
		}
		java.util.Collections.sort(from);
		return from.isEmpty() ? null : from.get(rng.nextInt(from.size()));
	}

	/**
	 * The expedition is over: its pay, scrap and gear to the Cargo Hold, recruits aboard, the injured hurt, the lost
	 * gone, all in one write; one beacon of the fleet's time; the history log; its events remembered; and a new posting
	 * in its place. Returns the summary.
	 */
	public static synchronized String finish(Vault v, Run r) throws IOException {
		Ship st = v.storage();
		Vault.Copy c = v.readCopy(st);
		ShipState hold = c.save.getPlayerShip();
		List<CrewState> crew = hold.getCrewList();
		int pay = r.pay();
		hold.setScrapAmt(hold.getScrapAmt() + pay + r.scrap);
		hold.setFuelAmt(hold.getFuelAmt() + r.fuel);
		hold.setMissilesAmt(hold.getMissilesAmt() + r.missiles);
		hold.setDronePartsAmt(hold.getDronePartsAmt() + r.parts);
		for (String id : r.items) {
			if (homeplanet.model.Items.isWeapon(id)) hold.getWeaponList().add(SaveHelper.newIdleWeapon(id));
			else if (homeplanet.model.Items.isDrone(id)) hold.getDroneList().add(SaveHelper.newIdleDrone(id));
			else hold.getAugmentIdList().add(id);
		}
		List<String> lostNames = new ArrayList<String>(), hurtNames = new ArrayList<String>(), joinedNames = new ArrayList<String>();
		for (CrewState sent : r.party) {
			CrewState mine = match(crew, sent);
			if (mine == null) throw new IOException(sent.getName() + " is no longer in the Cargo Hold; nothing was changed");
			if (r.lost.contains(sent)) { crew.remove(mine); lostNames.add(sent.getName()); }
			else if (r.hurt.containsKey(sent)) { mine.setHealth(Math.max(1, mine.getHealth() / 2)); hurtNames.add(sent.getName()); }
		}
		for (CrewState n : r.joined) {
			if (!SaveHelper.placeCrew(hold, n, true)) continue; // no room: they find other work
			crew.add(n);
			joinedNames.add(n.getName());
		}
		v.begin().put(st, c.save, c.hash).commit();
		v.countBeacon();
		StringBuilder sb = new StringBuilder();
		String where = r.posting.realSector();
		sb.append(r.alive().isEmpty() ? "No one came back from the expedition to the " + where + "."
				: "The expedition to the " + where + " is over. " + (pay > 0 ? "The job paid " + pay + " scrap" : "")
				+ (r.scrap > 0 ? (pay > 0 ? ", and " : "") + r.scrap + " scrap more came of it" : "") + ".");
		if (!r.items.isEmpty()) { List<String> t = new ArrayList<String>(); for (String id : r.items) t.add(homeplanet.model.Items.title(id)); sb.append("\nBrought back: ").append(String.join(", ", t)).append("."); }
		if (!joinedNames.isEmpty()) sb.append("\nNew crew: ").append(String.join(", ", joinedNames)).append(".");
		if (!hurtNames.isEmpty()) sb.append("\nInjured: ").append(String.join(", ", hurtNames)).append(".");
		if (!lostNames.isEmpty()) sb.append("\nDid not come back: ").append(String.join(", ", lostNames)).append(".");
		sb.append("\n\nEverything is in the Cargo Hold.");
		HistoryLog.entry("EXPEDITION", where + (r.posting.sealed ? " (sealed orders)" : "") + " (\"" + r.posting.text + "\"): " + (pay + r.scrap) + " scrap"
				+ (r.items.isEmpty() ? "" : ", " + String.join(", ", r.items)) + (joinedNames.isEmpty() ? "" : "; joined: " + String.join(", ", joinedNames))
				+ (lostNames.isEmpty() ? "" : "; did not come back: " + String.join(", ", lostNames))
				+ (hurtNames.isEmpty() ? "" : "; injured: " + String.join(", ", hurtNames)));
		Properties p = readBoard(v);
		List<Posting> others = new ArrayList<Posting>();
		for (int i = 0; i < POSTINGS; i++) if (i != r.slot && posting(p, i) != null) others.add(posting(p, i));
		others.add(r.posting); // not the same job again at once
		put(p, r.slot, pick(new Random(), others));
		List<String> recent = recent(v);
		for (Event e : r.events) { recent.remove(e.id); recent.add(e.id); }
		while (recent.size() > RECENT * 2) recent.remove(0); // about the last RECENT expeditions' worth
		p.setProperty("recent", String.join(",", recent));
		try { writeBoard(v, p); } catch (IOException e) { log.warn("Could not post a new expedition: {}", e.toString()); }
		return sb.toString();
	}
	private static CrewState match(List<CrewState> crew, CrewState sent) {
		if (crew.contains(sent)) return sent;
		for (CrewState c : crew) if (c.getName().equals(sent.getName()) && c.getRace() == sent.getRace()) return c;
		return null;
	}

	// ---- hiring ----

	/** Crew in the whole fleet: every ship (docked, boarded, in the Junkyard) and the Cargo Hold. */
	public static int fleetCrew(Vault v) {
		int n = 0;
		for (Ship s : v.all()) {
			net.blerf.ftl.parser.SavedGameParser.SavedGameState g = s.save();
			if (g != null) n += SaveHelper.getOwnCrew(g.getPlayerShip()).size();
		}
		try { if (!v.all().contains(v.storage())) n += holdCrew(v).size(); } catch (IOException e) { }
		return n;
	}
	/** What posting for volunteers costs: 5 for each crew member the commander has, at most 60 (FTL's dearest crew); free with none. */
	public static int hireCost(int crew) { return Math.min(60, 5 * crew); }
	/** The chance someone answers: a free promise of adventure, or a paid posting. */
	public static final int FREE_CHANCE = 50, PAID_CHANCE = 75;

	/** Races of the ships the commander has unlocked (each ship's own crew), as commissioning lists them. */
	public static List<String> hireableRaces() {
		Set<String> out = new java.util.TreeSet<String>();
		Unlocks u = Unlocks.read();
		boolean all = u == null || u.problem() != null || u.missing();
		for (String base : DataManager.get().getPlayerShipBaseIds(true)) {
			for (int n = 0; n < 3; n++) {
				ShipBlueprint bp;
				try { bp = DataManager.get().getPlayerShipVariant(base, n, true); } catch (Exception e) { bp = null; }
				if (bp == null || bp.getCrewCount() == null || bp.getCrewCount().race == null) continue;
				if (!all && !u.unlocked(base, n)) continue;
				if (net.blerf.ftl.parser.SavedGameParser.CrewType.findById(bp.getCrewCount().race) != null) out.add(bp.getCrewCount().race);
			}
		}
		if (out.isEmpty()) out.add("human"); // the Kestrel's
		return new ArrayList<String>(out);
	}

	/** Posts for crew: pays (if it costs), rolls whether anyone answers, and brings them to the Cargo Hold. Returns them, or null. */
	public static synchronized CrewState hire(Vault v, Random rng) throws IOException {
		int have = fleetCrew(v), cost = hireCost(have);
		Ship st = v.storage();
		Vault.Copy c = v.readCopy(st);
		ShipState hold = c.save.getPlayerShip();
		if (hold.getScrapAmt() < cost) throw new IOException("The Cargo Hold holds " + hold.getScrapAmt() + " scrap; posting costs " + cost);
		hold.setScrapAmt(hold.getScrapAmt() - cost);
		CrewState hired = null;
		if (rng.nextInt(100) < (cost == 0 ? FREE_CHANCE : PAID_CHANCE)) {
			List<String> races = hireableRaces();
			hired = Commission.volunteer(races.get(rng.nextInt(races.size())), rng);
			if (hired != null && !SaveHelper.placeCrew(hold, hired, true)) throw new IOException("The Cargo Hold has no room for another crew member; nothing was spent");
			if (hired != null) hold.getCrewList().add(hired);
		}
		if (cost > 0 || hired != null) v.begin().put(st, c.save, c.hash).commit();
		HistoryLog.entry("HIRE", (cost == 0 ? "A promise of adventure" : "Posted for volunteers, " + cost + " scrap") + ": "
				+ (hired == null ? "no one answered" : hired.getName() + " (" + hired.getRace().getId() + ") joined, in the Cargo Hold"));
		return hired;
	}
}
