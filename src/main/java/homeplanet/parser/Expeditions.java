package homeplanet.parser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import homeplanet.model.Skills;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * Expeditions: the commander signs on to a posted job with up to three crew from the Cargo Hold, no ship of the
 * fleet's needed. The board holds three postings, each a kind of job (a rescue, an escort, a salvage run, a survey,
 * a delivery, a repair, a security detail). A job is one event of its kind, at one place, played as FTL plays a
 * beacon: the situation, numbered choices (blue where a crew member's race opens one), and behind some choices a
 * hidden roll between outcomes; an outcome may lead on to one more choice of the same moment. It pays as FTL's
 * events pay in the first sector, a few scrap at the end of some branches and nothing at the end of many, and crew
 * can come back hurt (laid up in the infirmary a while), taken (a ransom follows) or dead. The commander always
 * comes home. Each expedition counts as one beacon of the fleet's time. The board and the events met lately are
 * kept in the fleet's expeditions.txt; the infirmary in infirmary.txt; captives in captives.txt.
 */
public final class Expeditions {
	private static final Logger log = LoggerFactory.getLogger(Expeditions.class);
	private Expeditions() { }

	public static final int POSTINGS = 3, PARTY_MAX = 3;
	/** Events met in the last this many expeditions aren't met again while others of the kind are left. */
	public static final int RECENT = 12;
	/** An untaken posting comes down after POSTING_MIN to POSTING_MAX beacons (rolled for each, never shown). */
	public static final int POSTING_MIN = 1, POSTING_MAX = 7;
	/** The infirmary keeps a hurt crew member HEAL_MIN to HEAL_MAX beacons (rolled, never shown). */
	public static final int HEAL_MIN = 3, HEAL_MAX = 6;
	/**
	 * A ransom is asked RANSOM_DELAY_MIN to MAX beacons after the expedition and stands for RANSOM_STANDS beacons
	 * (the letters say "one month", never beacons: the count is the station's own); a reminder comes REMINDER_BEFORE
	 * beacons before the end.
	 */
	public static final int RANSOM_DELAY_MIN = 2, RANSOM_DELAY_MAX = 5, RANSOM_STANDS = 14, REMINDER_BEFORE = 3;
	/** A bad outcome's weight (hurt, taken, dead) is this much of itself, in %, with a crew member whose race or skill fits the choice. */
	static final int FIT_RACE = 50, FIT_SKILL_ONE = 80, FIT_SKILL_TWO = 60;

	/** The kinds of job, as the board titles them. */
	private static final String[][] KINDS = {
		{"rescue", "Rescue"}, {"escort", "Escort"}, {"salvage", "Salvage"}, {"survey", "Survey"},
		{"delivery", "Delivery"}, {"repair", "Repair"}, {"security", "Security"}};
	public static String kindTitle(String kind) { for (String[] k : KINDS) if (k[0].equals(kind)) return k[1]; return kind; }
	static boolean knownKind(String kind) { for (String[] k : KINDS) if (k[0].equals(kind)) return true; return false; }
	/** Every kind of job (for tests). */
	public static List<String> kinds() { List<String> out = new ArrayList<String>(); for (String[] k : KINDS) out.add(k[0]); return out; }

	/** FTL's race ids by the names the events use. */
	private static final Map<String, String> RACES = new LinkedHashMap<String, String>();
	static {
		RACES.put("human", "human"); RACES.put("engi", "engi"); RACES.put("mantis", "mantis"); RACES.put("rock", "rock"); RACES.put("slug", "slug");
		RACES.put("zoltan", "energy"); RACES.put("crystal", "crystal"); RACES.put("lanius", "anaerobic");
	}
	/**
	 * What a choice takes, besides a race's own option: a word for the race that's good at it, with the skill that goes
	 * with it if any (fight: Mantis, combat; tech: Engi, repair; heat: Rock; power: Zoltan; airless: Lanius; mind: Slug),
	 * and a skill by name (pilot, engines, shields, weapons, repair, combat), which overrides the word's. The crew member
	 * best suited takes the choice on: its risk falls on them first, they earn its experience, and its bad outcomes are
	 * rarer for them.
	 */
	private static final Map<String, String[]> FITS = new LinkedHashMap<String, String[]>();
	static {
		FITS.put("fight", new String[] {"mantis", "combat"}); FITS.put("tech", new String[] {"engi", "repair"});
		FITS.put("heat", new String[] {"rock", null}); FITS.put("power", new String[] {"zoltan", null});
		FITS.put("airless", new String[] {"lanius", null}); FITS.put("mind", new String[] {"slug", null});
	}
	/** A race as the option tags show it: "Rock", "Zoltan". */
	static String raceWord(String name) { return name.substring(0, 1).toUpperCase() + name.substring(1); }

	// ---- the events files ----

	/** The skills as the events name them, in Crew.skillLevels' order. */
	static final String[] SKILLS = {"pilot", "engines", "shields", "weapons", "repair", "combat"};
	static int skillIndex(String name) { return name == null ? -1 : java.util.Arrays.asList(SKILLS).indexOf(name); }
	/** What a choice comes to: its words, what it gives and costs, and the choice it may lead on to. */
	public static final class Outcome {
		int weight = 1;
		int scrapMin, scrapMax, injure, lose, taken, clone, fuel, missiles, parts;
		String item; // weapon, drone or augment
		String join; // a race, or "any": someone joins the crew
		String then; // the event's next step, or null: the job is over
		String text = "";
		/** Skill experience for the crew member the choice is about: points by skill. */
		final int[] xp = new int[SKILLS.length];
		boolean bad() { return injure > 0 || lose > 0 || taken > 0 || clone > 0; }
	}
	/** One way through a step. */
	public static final class Choice {
		public String text = "";
		/** The race it needs aboard (an events-file name), or null. */
		public String race;
		/** The race that's good at it without being needed (an events-file name), or null. */
		String fitRace;
		/** The skill it takes (an index into SKILLS), or -1. */
		int skill = -1;
		final List<Outcome> outcomes = new ArrayList<Outcome>();
		/** Does anything about it pick who takes it on (a race, a race that's good at it, a skill)? */
		boolean picksWho() { return race != null || fitRace != null || skill >= 0; }
		/** As the button shows it: the race in brackets first. */
		public String label() { return race == null ? text : "(" + raceWord(race) + ") " + text; }
	}
	/** A screen: words and choices. An event's first step is "", the rest named. */
	public static final class Step {
		public String text = "";
		public final List<Choice> choices = new ArrayList<Choice>();
	}
	public static final class Event {
		public String id = "", kind = "";
		/** Who takes crew on this job (the ransom letters' signature). */
		public String foe = "pirates";
		final Map<String, Step> steps = new LinkedHashMap<String, Step>();
		Step first() { return steps.get(""); }
	}
	public static final class Posting {
		public final String kind, text;
		/** The event this posting plays: its words were written for it. */
		final String event;
		Posting(String kind, String text) { this(kind, text, null); }
		Posting(String kind, String text, String event) { this.kind = kind; this.text = text; this.event = event; }
		public String title() { return kindTitle(kind); }
	}
	static final class Book {
		final List<Posting> postings = new ArrayList<Posting>();
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
					String[] p = line.split("\\s+", 3);
					String[] ke = p.length < 3 ? new String[0] : p[1].split(":", 2);
					if (ke.length != 2 || !knownKind(ke[0]) || ke[1].isEmpty()) throw new IllegalArgumentException("a posting needs: posting <kind>:<event> <words>");
					b.postings.add(new Posting(ke[0], p[2], ke[1]));
					ev = null; st = null; ch = null;
				} else if (line.startsWith("event ")) {
					String[] p = line.split("\\s+");
					if (p.length < 3 || !knownKind(p[2])) throw new IllegalArgumentException("an event needs: event <id> <kind> [foe:<who>]");
					ev = new Event();
					ev.id = p[1];
					ev.kind = p[2];
					for (int i = 3; i < p.length; i++) {
						if (p[i].startsWith("foe:")) ev.foe = p[i].substring(4).replace('_', ' ');
						else throw new IllegalArgumentException("unknown word " + p[i]);
					}
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
						String fitSkill = null;
						for (String tag : t.substring(1, t.indexOf(']')).trim().split("\\s+")) {
							if (RACES.containsKey(tag)) {
								if (ch.race != null) throw new IllegalArgumentException("a choice needs one race at most");
								ch.race = tag;
							} else if (FITS.containsKey(tag)) {
								if (ch.fitRace != null) throw new IllegalArgumentException("a choice takes one race word at most");
								ch.fitRace = FITS.get(tag)[0];
								fitSkill = FITS.get(tag)[1];
							} else if (skillIndex(tag) >= 0) {
								if (ch.skill >= 0) throw new IllegalArgumentException("a choice takes one skill at most");
								ch.skill = skillIndex(tag);
							} else throw new IllegalArgumentException("unknown tag " + tag);
						}
						if (ch.skill < 0) ch.skill = skillIndex(fitSkill);
						t = t.substring(t.indexOf(']') + 1).trim();
					}
					ch.text = t;
					st.choices.add(ch);
					b.words.add(t);
				} else if (ch == null) {
					st.text = st.text.isEmpty() ? line : st.text + " " + line;
				} else if (line.startsWith("= ")) {
					ch.outcomes.add(outcome(line.substring(2), 1, b));
				} else if (line.startsWith("~ ")) {
					String[] p = line.substring(2).trim().split("\\s+", 2);
					ch.outcomes.add(outcome(p.length > 1 ? p[1] : "", Integer.parseInt(p[0]), b));
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
	/** What's wrong with the book as a whole: steps without a way through for anyone, choices without an outcome, steps nobody reaches. */
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
					if (c.outcomes.isEmpty()) { b.problems.add(where + ": \"" + c.text + "\" has no outcome"); continue; }
					// the button names someone only where a race's option says who it will be
					if (c.race == null && (c.text.contains("{who}") || c.text.contains("{crew}"))) b.problems.add(where + ": \"" + c.text + "\" names someone, but only a race's option knows who");
					for (Outcome o : c.outcomes) {
						// experience goes to whoever took the choice on: anyone's choice must say which skill picks them
						for (int k = 0; k < SKILLS.length; k++)
							if (o.xp[k] > 0 && c.race == null && c.skill != k) b.problems.add(where + ": \"" + c.text + "\" gives " + SKILLS[k] + " experience but doesn't take " + SKILLS[k]);
						if (o.then == null) continue;
						if (!e.steps.containsKey(o.then) || o.then.isEmpty()) b.problems.add(where + ": no step " + o.then);
						reached.add(o.then);
					}
				}
				if (open < 1) b.problems.add(where + ": no way through without a race");
				if (s.text.isEmpty() && en.getKey().isEmpty()) b.problems.add(where + ": no text");
			}
			for (String s : e.steps.keySet()) if (!reached.contains(s)) b.problems.add("event " + e.id + ": step " + s + " is never reached");
			if (loops(e, "", new HashSet<String>())) b.problems.add("event " + e.id + ": its steps go round in a circle");
		}
		// each posting's words were written for one event: it must be there, and of the posting's kind
		Set<String> posted = new HashSet<String>();
		for (Posting p : b.postings) {
			Event e = null;
			for (Event x : b.events) if (x.id.equals(p.event)) e = x;
			if (e == null) b.problems.add("posting for " + p.event + ": no such event");
			else if (!e.kind.equals(p.kind)) b.problems.add("posting for " + p.event + ": a " + p.kind + " posting for a " + e.kind + " event");
			if (!posted.add(p.event)) b.problems.add("event " + p.event + " has two postings");
		}
		for (Event e : b.events) if (!posted.contains(e.id)) b.problems.add("event " + e.id + " has no posting");
		for (String[] k : KINDS) {
			int ev = 0, po = 0;
			for (Event e : b.events) if (e.kind.equals(k[0])) ev++;
			for (Posting p : b.postings) if (p.kind.equals(k[0])) po++;
			if (ev == 0) b.problems.add("no events for a " + k[0] + " job");
			if (po == 0) b.problems.add("no postings for a " + k[0] + " job");
		}
	}
	private static boolean loops(Event e, String step, Set<String> path) {
		if (!path.add(step)) return true;
		Step s = e.steps.get(step);
		if (s != null) for (Choice c : s.choices) for (Outcome o : c.outcomes)
			if (o.then != null && e.steps.containsKey(o.then) && loops(e, o.then, path)) return true;
		path.remove(step);
		return false;
	}
	/** "scrap 5-10 injure 1 then deeper | words" */
	private static Outcome outcome(String s, int weight, Book b) {
		Outcome o = new Outcome();
		o.weight = weight;
		if (weight < 1) throw new IllegalArgumentException("an outcome's weight must be 1 or more");
		int bar = s.indexOf('|');
		if (bar < 0) throw new IllegalArgumentException("an outcome needs | and its words");
		o.text = s.substring(bar + 1).trim();
		b.words.add(o.text);
		String[] t = s.substring(0, bar).trim().split("\\s+");
		for (int i = 0; i < t.length; i++) {
			String k = t[i];
			if (k.isEmpty()) continue;
			if (i + 1 >= t.length) throw new IllegalArgumentException(k + " needs a value");
			String v = t[++i];
			if (k.equals("scrap")) {
				String[] r = v.split("-");
				o.scrapMin = Integer.parseInt(r[0]);
				o.scrapMax = Integer.parseInt(r[r.length - 1]);
			} else if (k.equals("injure")) o.injure = Integer.parseInt(v);
			else if (k.equals("lose")) o.lose = Integer.parseInt(v);
			else if (k.equals("taken")) o.taken = Integer.parseInt(v);
			else if (k.equals("clone")) o.clone = Integer.parseInt(v);
			else if (k.equals("xp")) {
				int skill = skillIndex(v);
				if (skill < 0 || i + 1 >= t.length) throw new IllegalArgumentException("xp needs a skill (pilot, engines, shields, weapons, repair, combat) and points");
				o.xp[skill] += Integer.parseInt(t[++i]);
			}
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
		if (o.text.isEmpty()) throw new IllegalArgumentException("an outcome needs words");
		return o;
	}
	/** What's wrong with the events files, if anything (the harness checks it's nothing). */
	public static List<String> problems() { return book().problems; }
	public static int eventCount() { return book().events.size(); }
	/** Every line of words the events show, for the harness to hold against FTL's own. */
	public static List<String> allWords() { return book().words; }
	/** Events a job of this kind could be. */
	static List<Event> pool(String kind) {
		List<Event> out = new ArrayList<Event>();
		for (Event e : book().events) if (e.kind.equals(kind)) out.add(e);
		return out;
	}
	static Event event(String id) { for (Event e : book().events) if (e.id.equals(id)) return e; return null; }
	/** Every event (for tests). */
	public static List<Event> events() { return new ArrayList<Event>(book().events); }

	// ---- the board ----

	private static File boardFile(Vault v) { return new File(v.root, "expeditions.txt"); }
	/** The three postings, the board filled first where a place is empty. */
	public static synchronized List<Posting> board(Vault v) {
		Properties p = readBoard(v);
		boolean changed = false;
		List<Posting> out = new ArrayList<Posting>();
		Random rng = new Random();
		int now = v.beaconsSeen();
		List<String> recent = recent(p);
		for (int i = 0; i < POSTINGS; i++) {
			Posting x = posting(p, i);
			if (x != null && until(p, i) < 0) { p.setProperty(i + ".until", Integer.toString(now + POSTING_MIN + rng.nextInt(POSTING_MAX - POSTING_MIN + 1))); changed = true; }
			if (x != null && now >= until(p, i)) x = null; // its time is up: the job went to someone else
			if (x == null) {
				List<Posting> others = new ArrayList<Posting>(out);
				for (int k = i + 1; k < POSTINGS; k++) if (posting(p, k) != null) others.add(posting(p, k));
				x = pick(rng, others, recent);
				put(p, i, x);
				p.setProperty(i + ".until", Integer.toString(now + POSTING_MIN + rng.nextInt(POSTING_MAX - POSTING_MIN + 1)));
				changed = true;
			}
			out.add(x);
		}
		if (changed) try { writeBoard(v, p); } catch (IOException e) { log.warn("Could not write the expeditions board: {}", e.toString()); }
		return out;
	}
	/** When an untaken posting comes down (a beacon count, never shown). */
	private static int until(Properties p, int i) {
		try { return Integer.parseInt(p.getProperty(i + ".until", "").trim()); } catch (NumberFormatException e) { return -1; }
	}
	private static Posting posting(Properties p, int i) {
		String kind = p.getProperty(i + ".kind"), text = p.getProperty(i + ".text"), event = p.getProperty(i + ".event");
		if (kind == null || text == null || !knownKind(kind)) return null;
		if (event == null) for (Posting x : book().postings) if (x.text.equals(text)) event = x.event; // a board from before postings named their events
		return new Posting(kind, text, event);
	}
	private static void put(Properties p, int i, Posting x) {
		p.setProperty(i + ".kind", x.kind); p.setProperty(i + ".text", x.text);
		if (x.event != null) p.setProperty(i + ".event", x.event); else p.remove(i + ".event");
	}
	/**
	 * A posting not already on the board: where there's a choice, of a kind not already on it and for an event not met
	 * lately.
	 */
	static Posting pick(Random rng, List<Posting> taken, List<String> recent) {
		Book b = book();
		List<Posting> fresh = new ArrayList<Posting>(), freshKind = new ArrayList<Posting>(), unmet = new ArrayList<Posting>(), unmetKind = new ArrayList<Posting>();
		for (Posting x : b.postings) {
			boolean on = false, kindOn = false;
			for (Posting t : taken) { if (t.text.equals(x.text) || (t.event != null && t.event.equals(x.event))) on = true; if (t.kind.equals(x.kind)) kindOn = true; }
			if (on) continue;
			boolean met = recent.contains(x.event);
			fresh.add(x);
			if (!kindOn) freshKind.add(x);
			if (!met) unmet.add(x);
			if (!met && !kindOn) unmetKind.add(x);
		}
		List<Posting> from = !unmetKind.isEmpty() ? unmetKind : !unmet.isEmpty() ? unmet : !freshKind.isEmpty() ? freshKind : fresh;
		return from.isEmpty() ? b.postings.get(rng.nextInt(b.postings.size())) : from.get(rng.nextInt(from.size()));
	}
	private static Properties readBoard(Vault v) {
		Properties p = new Properties();
		File f = boardFile(v);
		if (!f.isFile()) return p;
		try { p.load(new java.io.StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); }
		return p;
	}
	/** The board for a change: a file that can't be read is an error, never written back empty. */
	private static Properties readBoardStrict(Vault v) throws IOException { return readPropsStrict(boardFile(v)); }
	private static void writeBoard(Vault v, Properties p) throws IOException {
		java.io.StringWriter w = new java.io.StringWriter();
		p.store(w, "The expeditions board: three postings, and the events met lately");
		SafeFiles.writeText(boardFile(v), w.toString(), false);
	}
	/** Events met lately, not to be met again while others are left. */
	static List<String> recent(Vault v) { return recent(readBoard(v)); }
	/** For the harness: the events met lately. */
	public static List<String> recentEvents(Vault v) { return recent(v); }
	private static List<String> recent(Properties p) {
		List<String> out = new ArrayList<String>();
		for (String s : p.getProperty("recent", "").split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
		return out;
	}

	// ---- an expedition ----

	/** The crew in the Cargo Hold who can be sent: not those laid up in the infirmary. */
	public static List<CrewState> holdCrew(Vault v) throws IOException {
		List<CrewState> out = new ArrayList<CrewState>();
		Set<String> laidUp = new HashSet<String>();
		for (Patient x : infirmary(v)) laidUp.add(x.key());
		for (CrewState c : SaveHelper.getOwnCrew(v.readCopy(v.storage()).save.getPlayerShip())) if (!laidUp.contains(key(c))) out.add(c);
		return out;
	}
	private static String key(CrewState c) { return c.getName() + "/" + (c.getRace() == null ? "human" : c.getRace().getId()); }

	/** The event for a job: the one its posting was written for (or, failing that, one of its kind not met lately). */
	static Event draw(Posting posting, List<String> recent, Random rng) {
		if (posting.event != null && event(posting.event) != null) return event(posting.event);
		List<Event> pool = pool(posting.kind), fresh = new ArrayList<Event>();
		for (Event e : pool) if (!recent.contains(e.id)) fresh.add(e);
		List<Event> from = fresh.isEmpty() ? pool : fresh;
		return from.isEmpty() ? null : from.get(rng.nextInt(from.size()));
	}

	/** One expedition under way: its event, the screen it's at, and what has come of it so far. */
	public static final class Run {
		public final int slot;
		public final Posting posting;
		public final List<CrewState> party;
		final Event event;
		private Step step;
		/** The words on the screen now: the situation, or the last outcome and the step it led to. */
		private String shown;
		private final Random rng;
		int scrap, fuel, missiles, parts;
		final List<String> items = new ArrayList<String>();
		final List<CrewState> joined = new ArrayList<CrewState>();
		final List<CrewState> hurt = new ArrayList<CrewState>();
		final List<CrewState> lost = new ArrayList<CrewState>();
		/** Of the lost, those taken rather than killed. */
		final List<CrewState> captured = new ArrayList<CrewState>();
		/** Killed and cloned by the hiring ship's clone bay: home alive, a level off each skill. */
		final List<CrewState> cloned = new ArrayList<CrewState>();
		/** Skill experience earned, by crew member. */
		final Map<CrewState, int[]> earned = new java.util.HashMap<CrewState, int[]>();

		Run(int slot, Posting posting, List<CrewState> party, List<String> recent, Random rng) {
			this.slot = slot; this.posting = posting; this.party = new ArrayList<CrewState>(party); this.rng = rng;
			this.event = draw(posting, recent, rng);
			this.step = event == null ? null : event.first();
			this.shown = step == null ? "" : fill(step.text, null);
		}
		public Event event() { return event; }
		public boolean over() { return step == null; }
		/** The words on the screen now. */
		public String text() { return shown; }
		public List<CrewState> alive() {
			List<CrewState> out = new ArrayList<CrewState>(party);
			out.removeAll(lost);
			return out;
		}
		/** The screen's choices open to this party: a race's only with one of that race still with them. */
		public List<Choice> choices() {
			List<Choice> out = new ArrayList<Choice>();
			if (step == null) return out;
			for (Choice c : step.choices) if (c.race == null || of(c.race) != null) out.add(c);
			return out;
		}
		private CrewState of(String race) {
			for (CrewState c : alive()) if (isRace(c, race)) return c;
			return null;
		}
		private boolean isRace(CrewState c, String race) { return c.getRace() != null && c.getRace().getId().equals(RACES.get(race)); }
		/**
		 * How much of itself a bad outcome's weight keeps, in %, with this crew member taking the choice on: half for the
		 * race that's good at it (a race's own option always is), less again for the skill it takes.
		 */
		int fitOf(Choice c, CrewState x) {
			int f = 100;
			String race = c.race != null ? c.race : c.fitRace;
			if (race != null && isRace(x, race)) f = f * FIT_RACE / 100;
			if (c.skill >= 0) {
				int level = homeplanet.model.Crew.skillLevels(x)[c.skill];
				if (level > 0) f = f * (level >= 2 ? FIT_SKILL_TWO : FIT_SKILL_ONE) / 100;
			}
			return f;
		}
		/** The best a choice's bad outcomes can be weighed down, by whoever is best suited to it (100: no one is). */
		int fit(Choice c) {
			CrewState x = doer(c);
			return x == null ? 100 : fitOf(c, x);
		}
		/**
		 * Who takes a choice on: for a race's option, the best suited of that race (the first, if they're alike, so the
		 * button and the outcome name the same one); for one that takes a race or a skill, the best suited of everyone
		 * (one of them, if they're alike); otherwise anyone.
		 */
		CrewState doer(Choice c) {
			List<CrewState> from = new ArrayList<CrewState>();
			for (CrewState x : alive()) if (c.race == null || isRace(x, c.race)) from.add(x);
			if (from.isEmpty()) return null;
			if (!c.picksWho()) return from.get(rng.nextInt(from.size()));
			List<CrewState> best = new ArrayList<CrewState>();
			int least = Integer.MAX_VALUE;
			for (CrewState x : from) {
				int f = fitOf(c, x);
				if (f < least) { least = f; best.clear(); }
				if (f == least) best.add(x);
			}
			return c.race != null ? best.get(0) : best.get(rng.nextInt(best.size()));
		}
		/** Rolls one of a choice's outcomes by weight, the bad ones weighed down by who takes it on (and out, with no crew left to lose). */
		Outcome roll(Choice c, CrewState who) {
			int fit = who == null ? 100 : fitOf(c, who), total = 0;
			boolean anyone = !alive().isEmpty();
			int[] w = new int[c.outcomes.size()];
			for (int i = 0; i < w.length; i++) {
				Outcome o = c.outcomes.get(i);
				w[i] = o.bad() ? (anyone ? Math.max(1, o.weight * 100 * fit / 100) : 0) : o.weight * 100;
				total += w[i];
			}
			if (total == 0) return c.outcomes.get(0);
			int r = rng.nextInt(total);
			for (int i = 0; i < w.length; i++) { r -= w[i]; if (r < 0) return c.outcomes.get(i); }
			return c.outcomes.get(w.length - 1);
		}
		/** Takes a choice: its outcome rolled and applied; the screen moves on. Returns the outcome's words, with what it gave. */
		public String choose(Choice c) {
			CrewState who = doer(c);
			Outcome o = c.outcomes.size() == 1 ? c.outcomes.get(0) : roll(c, who);
			String said = fill(o.text, who);
			StringBuilder extra = new StringBuilder();
			for (int i = 0; i < o.lose + o.taken && !alive().isEmpty(); i++) {
				CrewState l = i == 0 && alive().contains(who) ? who : alive().get(rng.nextInt(alive().size()));
				lost.add(l);
				if (i >= o.lose) captured.add(l); // taken: nobody knows yet
			}
			for (int i = 0; i < o.injure && !alive().isEmpty(); i++) {
				CrewState h = i == 0 && alive().contains(who) ? who : alive().get(rng.nextInt(alive().size()));
				if (!hurt.contains(h)) hurt.add(h);
			}
			for (int i = 0; i < o.clone && !alive().isEmpty(); i++) {
				CrewState h = i == 0 && alive().contains(who) ? who : alive().get(rng.nextInt(alive().size()));
				if (!cloned.contains(h)) cloned.add(h);
			}
			if (who != null && alive().contains(who)) {
				int[] got = earned.get(who);
				if (got == null) earned.put(who, got = new int[SKILLS.length]);
				for (int i = 0; i < SKILLS.length; i++) got[i] += o.xp[i];
			}
			int got = o.scrapMax <= 0 ? 0 : o.scrapMin + rng.nextInt(o.scrapMax - o.scrapMin + 1);
			scrap += got;
			fuel += o.fuel; missiles += o.missiles; parts += o.parts;
			if (got > 0) extra.append("\n\nYou receive ").append(got).append(" scrap.");
			if (o.fuel > 0) extra.append(got > 0 ? " " : "\n\n").append("You receive ").append(o.fuel).append(" fuel.");
			if (o.missiles > 0) extra.append(got > 0 || o.fuel > 0 ? " " : "\n\n").append("You receive ").append(o.missiles).append(o.missiles == 1 ? " missile." : " missiles.");
			if (o.parts > 0) extra.append(got > 0 || o.fuel > 0 || o.missiles > 0 ? " " : "\n\n").append("You receive ").append(o.parts).append(" drone parts.");
			if (o.item != null) {
				String id = gear(o.item, rng);
				if (id != null) { items.add(id); extra.append("\n\nYou receive a ").append(homeplanet.model.Items.title(id)).append("."); }
			}
			if (o.join != null) {
				List<String> races = new ArrayList<String>(RACES.values());
				String race = "any".equals(o.join) ? races.get(rng.nextInt(races.size())) : RACES.get(o.join);
				CrewState n = Commission.volunteer(race, rng);
				if (n != null) { joined.add(n); extra.append("\n\n").append(n.getName()).append(" (").append(homeplanet.model.Crew.raceTitle(n)).append(") joins your crew."); }
			}
			step = o.then == null ? null : event.steps.get(o.then);
			shown = said + extra;
			if (step != null && !step.text.isEmpty()) shown += "\n\n" + fill(step.text, who);
			return shown;
		}
		/** The words filled in: {who} the crew member the choice is about, {crew} anyone along. */
		private String fill(String t, CrewState who) {
			List<CrewState> here = alive();
			CrewState any = here.isEmpty() ? null : here.get(rng.nextInt(here.size()));
			String w = who != null ? who.getName() : any != null ? any.getName() : "your crew";
			return t.replace("{who}", w).replace("{crew}", any == null ? w : any.getName());
		}
		/** A choice's words as its button shows them: {who} is the crew member of its race who would take it on. */
		public String label(Choice c) {
			CrewState who = c.race != null ? doer(c) : null;
			String name = who == null ? "someone" : who.getName();
			return c.label().replace("{who}", name).replace("{crew}", name);
		}
	}

	/** Signs on to the posting in this place with these crew (from the Cargo Hold). */
	public static Run start(Vault v, int slot, List<CrewState> party, Random rng) throws IOException {
		if (party.isEmpty()) throw new IOException("At least one crew member must go with you");
		if (party.size() > PARTY_MAX) throw new IOException("At most " + PARTY_MAX + " can go");
		Posting x = board(v).get(slot);
		Run r = new Run(slot, x, party, recent(v), rng);
		if (r.event == null) throw new IOException("There are no events for a " + x.kind + " job");
		// signed on: the job comes off the board now, so closing the station mid-job can't play it again
		Properties p = readBoardStrict(v);
		List<Posting> others = new ArrayList<Posting>();
		for (int i = 0; i < POSTINGS; i++) if (i != slot && posting(p, i) != null) others.add(posting(p, i));
		others.add(x); // not the same job again at once
		List<String> recent = recent(p);
		recent.remove(r.event.id); recent.add(r.event.id);
		while (recent.size() > RECENT) recent.remove(0);
		p.setProperty("recent", String.join(",", recent));
		put(p, slot, pick(new Random(), others, recent));
		p.setProperty(slot + ".until", Integer.toString(v.beaconsSeen() + 1 + POSTING_MIN + new Random().nextInt(POSTING_MAX - POSTING_MIN + 1)));
		writeBoard(v, p);
		return r;
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
	 * The expedition is over: its scrap and gear to the Cargo Hold, recruits aboard, the hurt into the infirmary, the
	 * taken held for a ransom, the lost gone, all in one write; one beacon of the fleet's time; and the history log.
	 * (The job came off the board when the commander signed on.) Returns word of anyone carried to the infirmary, or "".
	 */
	public static synchronized String finish(Vault v, Run r) throws IOException {
		Ship st = v.storage();
		Vault.Copy c = v.readCopy(st);
		Properties inf = readPropsStrict(infirmaryFile(v)), cap = readPropsStrict(captivesFile(v)); // unreadable: nothing changes
		ShipState hold = c.save.getPlayerShip();
		List<CrewState> crew = hold.getCrewList();
		hold.setScrapAmt(hold.getScrapAmt() + r.scrap);
		hold.setFuelAmt(hold.getFuelAmt() + r.fuel);
		hold.setMissilesAmt(hold.getMissilesAmt() + r.missiles);
		hold.setDronePartsAmt(hold.getDronePartsAmt() + r.parts);
		for (String id : r.items) {
			if (homeplanet.model.Items.isWeapon(id)) hold.getWeaponList().add(SaveHelper.newIdleWeapon(id));
			else if (homeplanet.model.Items.isDrone(id)) hold.getDroneList().add(SaveHelper.newIdleDrone(id));
			else hold.getAugmentIdList().add(id);
		}
		List<String> lostNames = new ArrayList<String>(), hurtNames = new ArrayList<String>(), joinedNames = new ArrayList<String>();
		int now = v.beaconsSeen() + 1; // the beacon this expedition takes
		Random rng = new Random();
		for (CrewState sent : r.party) {
			CrewState mine = match(crew, sent);
			if (mine == null) throw new IOException(sent.getName() + " is no longer in the Cargo Hold; nothing was changed");
			if (r.lost.contains(sent)) {
				crew.remove(mine);
				lostNames.add(sent.getName());
				if (r.captured.contains(sent)) takeCaptive(cap, mine, r.event.foe, now, rng); // a ransom will be asked
				continue;
			}
			if (r.hurt.contains(sent)) { mine.setHealth(Math.max(1, mine.getHealth() / 4)); hurtNames.add(sent.getName()); admit(inf, mine, now, rng); }
			if (r.cloned.contains(sent)) { Skills.cloned(mine); continue; } // the clone bay: a level down from before the job, and the job's experience gone with the body
			int[] got = r.earned.get(sent);
			if (got != null) for (int i = 0; i < SKILLS.length; i++) if (got[i] > 0) Skills.add(mine, i, got[i]);
		}
		for (CrewState n : r.joined) {
			if (!SaveHelper.placeCrew(hold, n, true)) continue; // no room: they find other work
			crew.add(n);
			joinedNames.add(n.getName());
		}
		v.begin().put(st, c.save, c.hash).put(infirmaryFile(v), propsBytes(inf, INFIRMARY_NOTE)).put(captivesFile(v), propsBytes(cap, CAPTIVES_NOTE)).commit();
		v.countBeacon();
		// the last outcome has told the rest: only the infirmary is news
		StringBuilder sb = new StringBuilder();
		if (!hurtNames.isEmpty()) sb.append(String.join(" and ", hurtNames)).append(hurtNames.size() > 1 ? " are" : " is").append(" carried to the infirmary when the shuttle docks.");
		HistoryLog.entry("EXPEDITION", r.posting.title() + " (\"" + r.posting.text + "\", " + r.event.id + "): " + r.scrap + " scrap"
				+ (r.items.isEmpty() ? "" : ", " + String.join(", ", r.items)) + (joinedNames.isEmpty() ? "" : "; joined: " + String.join(", ", joinedNames))
				+ (lostNames.isEmpty() ? "" : "; did not come back: " + String.join(", ", lostNames))
				+ (hurtNames.isEmpty() ? "" : "; to the infirmary: " + String.join(", ", hurtNames)));
		return sb.toString();
	}
	private static CrewState match(List<CrewState> crew, CrewState sent) {
		if (crew.contains(sent)) return sent;
		for (CrewState c : crew) if (c.getName().equals(sent.getName()) && c.getRace() == sent.getRace()) return c;
		return null;
	}

	// ---- the infirmary ----

	private static File infirmaryFile(Vault v) { return new File(v.root, "infirmary.txt"); }
	private static final String INFIRMARY_NOTE = "Crew hurt on expeditions, and when they're on their feet again";
	/** A crew member laid up: they stay in the Cargo Hold's save, but can't be sent or moved until their time is up. */
	public static final class Patient {
		public final String name, race;
		public final int until;
		/** The last beacon their lay-up cost them a point of skill. */
		final int drained;
		Patient(String name, String race, int until, int drained) { this.name = name; this.race = race; this.until = until; this.drained = drained; }
		String key() { return name + "/" + race; }
	}
	public static synchronized List<Patient> infirmary(Vault v) {
		List<Patient> out = new ArrayList<Patient>();
		Properties p = readProps(infirmaryFile(v));
		for (int i = 0; p.getProperty(i + ".name") != null; i++) out.add(new Patient(p.getProperty(i + ".name"), p.getProperty(i + ".race", "human"), intOf(p, i + ".until", 0), intOf(p, i + ".drained", 0)));
		return out;
	}
	/** Is this crew member laid up in the infirmary? */
	public static boolean laidUp(Vault v, CrewState c) { return laidUpKeys(v).contains(key(c)); }
	/** Everyone laid up, for a list of crew to check against with {@link #crewKey} (one read of the infirmary). */
	public static Set<String> laidUpKeys(Vault v) {
		Set<String> out = new HashSet<String>();
		for (Patient x : infirmary(v)) out.add(x.key());
		return out;
	}
	public static String crewKey(CrewState c) { return key(c); }
	/** Lays a crew member up in the infirmary (into its file's properties, written with the Cargo Hold). */
	private static void admit(Properties p, CrewState c, int now, Random rng) {
		int i = 0;
		while (p.getProperty(i + ".name") != null) i++;
		p.setProperty(i + ".name", c.getName());
		p.setProperty(i + ".race", c.getRace() == null ? "human" : c.getRace().getId());
		p.setProperty(i + ".until", Integer.toString(now + HEAL_MIN + rng.nextInt(HEAL_MAX - HEAL_MIN + 1)));
		p.setProperty(i + ".drained", Integer.toString(now));
	}
	/**
	 * The station's care, at each look: crew hurt in the game, in the Cargo Hold or aboard a docked ship (never the
	 * boarded one: she may be in FTL), are healed a full beacon after the station first sees them there (a move to another
	 * place starts the beacon again), each with a line in the history log; the infirmary's laid up lose a point of skill
	 * for each beacon laid up (from a random skill they have points in), and whoever's time is up is on their feet,
	 * whole, and back among the crew who can be sent. Returns the names out of the infirmary.
	 */
	public static synchronized List<String> checkInfirmary(Vault v) {
		List<String> back = new ArrayList<String>();
		Properties p;
		try { p = readPropsStrict(infirmaryFile(v)); } catch (IOException e) { log.warn("Could not read the infirmary: {}", e.toString()); return back; } // never written back empty
		int now = v.beaconsSeen();
		List<Patient> all = infirmary(v), keep = new ArrayList<Patient>();
		for (Patient x : all) if (now < x.until) keep.add(x);
		Properties q = new Properties();
		q.setProperty("healed_at", Integer.toString(now));
		Random rng = new Random();
		try {
			// the Cargo Hold: the infirmary, and the station's medbay
			Ship st = v.storage();
			Vault.Copy c = v.readCopy(st);
			boolean changed = false;
			for (CrewState x : c.save.getPlayerShip().getCrewList()) {
				if (x.getRace() == null || !SaveHelper.hasBody(x)) continue;
				int max = x.getRace().getMaxHealth();
				Patient mine = null;
				for (Patient y : all) if (y.key().equals(key(x))) mine = y;
				if (mine != null) {
					for (int b = mine.drained; b < Math.min(now, mine.until); b++) changed |= Skills.drain(x, rng);
					if (now >= mine.until) { // on their feet: whole again, and free to go
						if (x.getHealth() != max) { x.setHealth(max); changed = true; }
						back.add(x.getName());
					}
				} else changed |= tend(x, "hold", now, p, q);
			}
			if (changed) v.begin().put(st, c.save, c.hash).commit();
			// the docked ships: the medbay alone
			for (Ship d : v.docked()) {
				net.blerf.ftl.parser.SavedGameParser.SavedGameState seen = d.save();
				boolean anyHurt = false;
				if (seen != null) for (CrewState x : SaveHelper.getOwnCrew(seen.getPlayerShip())) if (x.getRace() != null && x.getHealth() < x.getRace().getMaxHealth()) anyHurt = true;
				if (!anyHurt) continue; // nobody to tend: her file isn't opened
				Vault.Copy dc = v.readCopy(d);
				boolean dChanged = false;
				for (CrewState x : SaveHelper.getOwnCrew(dc.save.getPlayerShip())) if (x.getRace() != null && SaveHelper.hasBody(x)) dChanged |= tend(x, d.id, now, p, q);
				if (dChanged) v.begin().put(d, dc.save, dc.hash).commit();
			}
		} catch (IOException e) { log.warn("Could not tend the station's crew: {}", e.toString()); return new ArrayList<String>(); }
		// those whose time is up are let go (any no longer in the Cargo Hold, retired or moved, quietly)
		for (int i = 0; i < keep.size(); i++) { q.setProperty(i + ".name", keep.get(i).name); q.setProperty(i + ".race", keep.get(i).race); q.setProperty(i + ".until", Integer.toString(keep.get(i).until)); q.setProperty(i + ".drained", Integer.toString(now)); }
		try { writeProps(infirmaryFile(v), q, INFIRMARY_NOTE); } catch (IOException e) { log.warn("Could not write the infirmary: {}", e.toString()); }
		for (String n : back) HistoryLog.entry("EXPEDITION", n + " is out of the infirmary");
		return back;
	}
	/**
	 * One crew member hurt in the game, at one place: noted the first time the station sees them hurt there, healed a
	 * full beacon after (heromedel's line in the history log). Keeps the note in {@code q} while they wait. Healed?
	 */
	private static boolean tend(CrewState x, String place, int now, Properties was, Properties q) {
		int max = x.getRace().getMaxHealth();
		if (x.getHealth() >= max) return false;
		String k = "seen." + place + "." + key(x).replace(' ', '_');
		int seen = intOf(was, k, now);
		if (now > seen) {
			x.setHealth(max);
			HistoryLog.entry("MEDBAY", x.getName() + "'s visited The Station's Medbay");
			return true;
		}
		q.setProperty(k, Integer.toString(seen));
		return false;
	}
	private static Properties readProps(File f) {
		Properties p = new Properties();
		if (!f.isFile()) return p;
		try { p.load(new java.io.StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); }
		return p;
	}
	/** For a change: a file that can't be read is an error, so it's never written back empty. */
	private static Properties readPropsStrict(File f) throws IOException {
		Properties p = new Properties();
		if (f.isFile()) p.load(new java.io.StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8)));
		return p;
	}
	private static byte[] propsBytes(Properties p, String comment) throws IOException {
		java.io.StringWriter w = new java.io.StringWriter();
		p.store(w, comment);
		return w.toString().getBytes(StandardCharsets.UTF_8);
	}
	private static void writeProps(File f, Properties p, String comment) throws IOException {
		java.io.StringWriter w = new java.io.StringWriter();
		p.store(w, comment);
		SafeFiles.writeText(f, w.toString(), false);
	}

	// ---- captives and ransoms ----

	private static File captivesFile(Vault v) { return new File(v.root, "captives.txt"); }
	/** Crew taken on an expedition: a ransom is asked a few beacons later, and stands a while. */
	public static final class Captive {
		public final int index;
		public final String name, race, captors;
		public final boolean male;
		public final int ransom, asked, until;
		Captive(int index, String name, String race, boolean male, String captors, int ransom, int asked, int until) {
			this.index = index; this.name = name; this.race = race; this.male = male; this.captors = captors; this.ransom = ransom; this.asked = asked; this.until = until;
		}
	}
	private static final String CAPTIVES_NOTE = "Crew taken on expeditions, and the ransoms asked for them";
	private static Properties readCaptives(Vault v) { return readProps(captivesFile(v)); }
	private static void writeCaptives(Vault v, Properties p) throws IOException { writeProps(captivesFile(v), p, CAPTIVES_NOTE); }
	/**
	 * Holds a crew member taken (into the captives file's properties, written with the Cargo Hold): their whole record
	 * kept, to come back as they were; the ransom is asked a few beacons on, and its month runs from the asking.
	 */
	static void takeCaptive(Properties p, CrewState c, String captors, int now, Random rng) {
		int i = 0;
		while (p.getProperty(i + ".name") != null) i++;
		p.setProperty(i + ".name", c.getName());
		p.setProperty(i + ".race", c.getRace() == null ? "human" : c.getRace().getId());
		p.setProperty(i + ".male", Boolean.toString(c.isMale()));
		p.setProperty(i + ".captors", captors);
		p.setProperty(i + ".ransom", Integer.toString(20 + rng.nextInt(21)));
		p.setProperty(i + ".asked", Integer.toString(now + RANSOM_DELAY_MIN + rng.nextInt(RANSOM_DELAY_MAX - RANSOM_DELAY_MIN + 1)));
		p.setProperty(i + ".state", "held");
		if (c.getRace() != null) for (Map.Entry<String, String> e : homeplanet.comm.Line.crewFields(c).entrySet()) p.setProperty(i + ".crew." + e.getKey(), e.getValue());
	}
	/** What a ransom check found new, for a pop-up when the inbox is off: the ask, the reminder, or word of the loss. */
	public static final class RansomNews {
		public final Captive captive;
		/** "ask", "remind" or "lost". */
		public final String kind;
		RansomNews(Captive captive, String kind) { this.captive = captive; this.kind = kind; }
		public String title() { return kind.equals("lost") ? "Presumed dead: " + captive.name : "Ransom: " + captive.name; }
		public String text() { return kind.equals("ask") ? askLetter(captive) : kind.equals("remind") ? reminderLetter(captive) : lostLetter(captive); }
	}
	private static Captive captive(Properties p, int i) {
		return new Captive(i, p.getProperty(i + ".name"), p.getProperty(i + ".race", "human"), "true".equals(p.getProperty(i + ".male")),
				p.getProperty(i + ".captors", "pirates"), intOf(p, i + ".ransom", 30), intOf(p, i + ".asked", 0), intOf(p, i + ".until", intOf(p, i + ".asked", 0) + RANSOM_STANDS));
	}
	/**
	 * The ransoms' clock: a letter when a ransom is asked, a reminder near the end, and the Federation Ambassador's
	 * letter when it runs out (they're lost for good). Returns what's new, for a pop-up when the inbox is off.
	 */
	public static synchronized List<RansomNews> checkRansoms(Vault v) {
		List<RansomNews> out = new ArrayList<RansomNews>();
		Properties p;
		try { p = readPropsStrict(captivesFile(v)); } catch (IOException e) { log.warn("Could not read the captives: {}", e.toString()); return out; } // never written back empty
		boolean changed = false;
		int now = v.beaconsSeen();
		for (int i = 0; p.getProperty(i + ".name") != null; i++) {
			String state = p.getProperty(i + ".state", "held");
			Captive c = captive(p, i);
			if (state.equals("held") && now >= c.asked) {
				state = "asked";
				p.setProperty(i + ".state", state);
				p.setProperty(i + ".until", Integer.toString(now + RANSOM_STANDS)); // the month runs from the letter, however late the station sees it
				c = captive(p, i);
				changed = true;
				Transmissions.deliver("ransom:" + i, capitalised(c.captors), "Ransom: " + c.name, askLetter(c));
				out.add(new RansomNews(c, "ask"));
			}
			if (state.equals("asked") && now >= c.until - REMINDER_BEFORE && now <= c.until) {
				state = "reminded";
				p.setProperty(i + ".state", state);
				changed = true;
				Transmissions.deliver("ransom-reminder:" + i, capitalised(c.captors), "Ransom: " + c.name + ", time is short", reminderLetter(c));
				out.add(new RansomNews(c, "remind"));
			}
			if ((state.equals("asked") || state.equals("reminded")) && now > c.until) {
				p.setProperty(i + ".state", "gone");
				changed = true;
				lost(c, "the ransom went unpaid");
				out.add(new RansomNews(c, "lost"));
			}
		}
		if (changed) try { writeCaptives(v, p); } catch (IOException e) { log.warn("Could not update the captives: {}", e.toString()); }
		return out;
	}
	/** Word of a captive lost for good: the Ambassador's letter, and the history log. */
	private static void lost(Captive c, String why) {
		Transmissions.deliver("presumed:" + c.index + ":" + c.name, AMBASSADOR, "Presumed dead: " + c.name, lostLetter(c));
		HistoryLog.entry("EXPEDITION", c.name + ", taken by " + c.captors + ": " + why + "; presumed dead");
	}
	/** The ransom a letter is about, if it can still be paid or refused (its key: "ransom:<n>" or "ransom-reminder:<n>"), else null. */
	public static synchronized Captive openRansom(Vault v, String key) {
		int i;
		try { i = Integer.parseInt(key.substring(key.indexOf(':') + 1).trim()); } catch (RuntimeException e) { return null; }
		Properties p = readCaptives(v);
		String state = p.getProperty(i + ".state", "");
		if (!state.equals("asked") && !state.equals("reminded")) return null;
		Captive c = captive(p, i);
		return v.beaconsSeen() > c.until ? null : c;
	}
	public static boolean isRansom(String key) { return key.startsWith("ransom:") || key.startsWith("ransom-reminder:"); }
	/** Refuses a ransom: they're lost for good, and the Ambassador writes. */
	public static synchronized void refuseRansom(Vault v, Captive c) throws IOException {
		Properties p = readPropsStrict(captivesFile(v));
		p.setProperty(c.index + ".state", "refused");
		writeCaptives(v, p);
		lost(c, "the ransom was refused");
	}

	static final String AMBASSADOR = "Federation Ambassador";
	static String askLetter(Captive c) {
		return c.name + " is alive. For now, that is our doing, and it can stay that way.\n\n"
				+ "We ask " + c.ransom + " scrap for their return. Pay it, and they will be on the next transport to your Cargo Hold, "
				+ "fed and in one piece. You have one month.\n\n"
				+ "Do not send ships. We will know, and " + c.name + " will be the one who pays for it.\n\n~ " + capitalised(c.captors);
	}
	static String reminderLetter(Captive c) {
		return "Time is running short, and so is our patience.\n\n"
				+ c.ransom + " scrap, and " + c.name + " comes home. Silence, and we will take it as your answer.\n\n~ " + capitalised(c.captors);
	}
	/** The Ambassador's letter, for a notice when the inbox is off. */
	public static String lostWord(Captive c) { return lostLetter(c); }
	static String lostLetter(Captive c) {
		return "Commander,\n\n"
				+ "It is my duty to tell you that the Federation's efforts on behalf of " + c.name + " have come to nothing. "
				+ "Their captors have broken off all contact, and every channel we have tried has gone quiet. "
				+ c.name + " is listed from today as missing, presumed dead.\n\n"
				+ "I have written letters like this one more often than I would like, and they do not grow easier. "
				+ c.name + " went out under the Federation's banner, and the Federation does not forget its own.\n\n"
				+ "With deepest regret,\n\n~ " + AMBASSADOR;
	}
	private static String capitalised(String s) { return s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1); }
	/** Pays a ransom from the Cargo Hold: they come back to it, shaken but whole. */
	public static synchronized void payRansom(Vault v, Captive c) throws IOException {
		Properties p = readPropsStrict(captivesFile(v));
		String state = p.getProperty(c.index + ".state", "");
		if (!state.equals("asked") && !state.equals("reminded")) throw new IOException(c.name + "'s ransom is no longer asked");
		if (v.beaconsSeen() > c.until) throw new IOException("The offer for " + c.name + " has run out");
		Ship st = v.storage();
		Vault.Copy cp = v.readCopy(st);
		ShipState hold = cp.save.getPlayerShip();
		if (hold.getScrapAmt() < c.ransom) throw new IOException("The Cargo Hold holds " + hold.getScrapAmt() + " scrap; the ransom is " + c.ransom);
		CrewState back = kept(p, c.index);
		if (back == null) { // a captive from before their record was kept
			back = Commission.volunteer(c.race, new Random());
			if (back == null) throw new IOException("Unknown crew race " + c.race);
			back.setName(c.name);
			back.setMale(c.male);
		}
		back.setHealth(back.getRace().getMaxHealth()); // shaken, but whole
		if (!SaveHelper.placeCrew(hold, back, true)) throw new IOException("The Cargo Hold has no room for another crew member");
		hold.getCrewList().add(back);
		hold.setScrapAmt(hold.getScrapAmt() - c.ransom);
		p.setProperty(c.index + ".state", "ransomed");
		v.begin().put(st, cp.save, cp.hash).put(captivesFile(v), propsBytes(p, CAPTIVES_NOTE)).commit(); // paid and marked together: never twice
		HistoryLog.entry("EXPEDITION", c.name + " ransomed from " + c.captors + " for " + c.ransom + " scrap, back in the Cargo Hold");
	}
	/** A captive's kept record (skills, service, looks), or null for one taken before records were kept. */
	private static CrewState kept(Properties p, int i) {
		String pre = i + ".crew.";
		Map<String, String> f = new LinkedHashMap<String, String>();
		for (String k : p.stringPropertyNames()) if (k.startsWith(pre)) f.put(k.substring(pre.length()), p.getProperty(k));
		if (f.isEmpty()) return null;
		try { return homeplanet.comm.Line.crewFrom(f); }
		catch (Exception e) { log.warn("Could not read {}'s record: {}", p.getProperty(i + ".name"), e.toString()); return null; }
	}
	private static int intOf(Properties p, String key, int dflt) {
		try { return Integer.parseInt(p.getProperty(key, "").trim()); } catch (NumberFormatException e) { return dflt; }
	}

	// ---- hiring ----

	/** Crew in the whole fleet: every ship (docked, boarded, in the Junkyard) and the Cargo Hold. */
	public static int fleetCrew(Vault v) {
		int n = 0;
		for (Ship s : v.all()) {
			net.blerf.ftl.parser.SavedGameParser.SavedGameState g = s.save();
			if (g != null) n += SaveHelper.getOwnCrew(g.getPlayerShip()).size();
		}
		try { if (!v.all().contains(v.storage())) n += SaveHelper.getOwnCrew(v.readCopy(v.storage()).save.getPlayerShip()).size(); } catch (IOException e) { }
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
		if (u != null && u.missing()) return new ArrayList<String>(java.util.Collections.singletonList("human")); // FTL hasn't made the profile yet: only the Kestrel A, as Commission has it
		boolean all = u == null || u.problem() != null;
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
