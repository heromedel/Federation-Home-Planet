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
	/** One high-danger expedition of two or more crew in this many goes missing (the lost expedition, "event lost ... saga"). */
	public static final int LOST_ONE_IN = 300;
	/** An injury on a moderate or high-risk event is fatal this often, in %: the walking wounded don't always walk. */
	public static final int FATAL_MODERATE = 40, FATAL_HIGH = 65;
	/** A gamble on a moderate or high-risk event is this much harder: dangerous jobs are dangerous. */
	public static final int RISK_MODERATE = 5, RISK_HIGH = 10;
	/** One posting in this many wants outfitted crew, at OUTFIT_MIN to OUTFIT_MAX scrap a head. */
	public static final int OUTFIT_ONE_IN = 4, OUTFIT_MIN = 5, OUTFIT_MAX = 12;
	/** Outfitted crew: this much on every gamble, this much more scrap (in %) from the events; a finished job returns 50-200% of the outfitting. */
	public static final int OUTFIT_ODDS = 10, OUTFIT_SCRAP = 25, PAYBACK_MIN = 50, PAYBACK_MAX = 200;
	/** One crew member lost in this many was taken, not killed: a ransom is asked a few beacons later. */
	public static final int CAPTURED_ONE_IN = 3;
	/**
	 * A ransom is asked RANSOM_DELAY_MIN to MAX beacons after the expedition and stands for RANSOM_STANDS beacons
	 * (the letters say "one month", never beacons: the count is the station's own); a reminder comes REMINDER_BEFORE
	 * beacons before the end.
	 */
	public static final int RANSOM_DELAY_MIN = 2, RANSOM_DELAY_MAX = 5, RANSOM_STANDS = 14, REMINDER_BEFORE = 3;
	/** For the harness: every eligible expedition goes missing. */
	static boolean alwaysLost = false;

	/** A ship that comes home from each sector: models that fit it (FTL's player ships, by their base ids). */
	static String[] shipModels(String kind) {
		String k = kind.endsWith("_home") ? parentOf(kind) : kind;
		if (k.equals("engi")) return new String[] {"PLAYER_SHIP_CIRCLE"};
		if (k.equals("zoltan")) return new String[] {"PLAYER_SHIP_ENERGY"};
		if (k.equals("mantis")) return new String[] {"PLAYER_SHIP_MANTIS"};
		if (k.equals("rock")) return new String[] {"PLAYER_SHIP_ROCK"};
		if (k.equals("slug")) return new String[] {"PLAYER_SHIP_JELLY"};
		if (k.equals("nebula")) return new String[] {"PLAYER_SHIP_STEALTH", "PLAYER_SHIP_JELLY"};
		if (k.equals("pirate")) return new String[] {"PLAYER_SHIP_HARD", "PLAYER_SHIP_MANTIS", "PLAYER_SHIP_CIRCLE"};
		if (k.equals("rebel")) return new String[] {"PLAYER_SHIP_FED"};
		if (k.equals("abandoned")) return new String[] {"PLAYER_SHIP_ANAEROBIC"};
		if (k.equals("crystal")) return new String[] {"PLAYER_SHIP_CRYSTAL"};
		return new String[] {"PLAYER_SHIP_HARD"}; // a civilian's Kestrel
	}
	/** The lost expedition's prize: Federation-built, for getting behind enemy lines. */
	static final String STEALTH = "PLAYER_SHIP_STEALTH";
	/** Who the lost expedition was last seen fighting, by sector. */
	static String enemyOf(String kind) {
		String k = kind.endsWith("_home") ? parentOf(kind) : kind;
		if (k.equals("mantis")) return "Mantis raiders";
		if (k.equals("slug") || k.equals("nebula")) return "Slug privateers";
		if (k.equals("pirate") || k.equals("civilian")) return "pirates";
		if (k.equals("abandoned")) return "Lanius scavengers";
		if (k.equals("engi") || k.equals("zoltan") || k.equals("rock") || k.equals("crystal")) return "pirates";
		return "the rebels";
	}

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
		/** A ship home: "sector:condition[:system]" (wrecked, limping with a system broken through, towed, or new). */
		String ship;
		/** The lost expedition: all but one of the party stay lost; the Stealth Cruiser "new" or "dented"; beacons of waiting. */
		boolean survivor;
		int beacons;
		/** Now and then, an extra line (an aside): its chance in %, and its words. */
		int asideChance;
		String aside;
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
		/** Outfitted crew wanted: scrap a head, paid for each crew member sent (0: not an outfitted job). */
		public final int outfit;
		Posting(String kind, int danger, String text) { this(kind, danger, text, false, 0); }
		Posting(String kind, int danger, String text, boolean sealed) { this(kind, danger, text, sealed, 0); }
		Posting(String kind, int danger, String text, boolean sealed, int outfit) { this.kind = kind; this.danger = danger; this.text = text; this.sealed = sealed; this.outfit = outfit; }
		Posting outfitted(int perHead) { return new Posting(kind, danger, text, sealed, perHead); }
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
		Outcome last = null;
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
						if (!knownKind(k) && !"saga".equals(k)) throw new IllegalArgumentException("unknown sector " + k);
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
					last = null;
					String name = line.substring(5).trim();
					if (ev.steps.containsKey(name)) throw new IllegalArgumentException("step " + name + " twice in " + ev.id);
					st = new Step();
					ev.steps.put(name, st);
					ch = null;
				} else if (line.startsWith("*")) {
					last = null;
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
					ch.win = last = outcome(line.substring(4), b);
				} else if (line.startsWith("lose ")) {
					ch.lose = last = outcome(line.substring(5), b);
				} else if (line.startsWith("ok ")) {
					ch.sure = last = outcome(line.substring(3), b);
				} else if (line.startsWith("aside ")) {
					if (last == null) throw new IllegalArgumentException("an aside needs an outcome before it");
					int bar = line.indexOf('|');
					if (bar < 0) throw new IllegalArgumentException("an aside needs | and its words");
					last.asideChance = Integer.parseInt(line.substring(6, bar).trim());
					last.aside = line.substring(bar + 1).trim();
					b.words.add(last.aside);
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
				if (open < (e.kinds.contains("saga") ? 1 : 2)) b.problems.add(where + ": fewer than two ways through without a race");
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
		b.words.add(o.text);
		String[] t = s.substring(0, bar).trim().split("\\s+");
		for (int i = 0; i < t.length; i++) {
			String k = t[i];
			if (k.isEmpty()) continue;
			if (k.equals("end")) { o.end = true; continue; }
			if (k.equals("survivor")) { o.survivor = true; continue; }
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
			else if (k.equals("beacons")) o.beacons = Integer.parseInt(v);
			else if (k.equals("ship")) {
				String[] sp = v.split(":");
				if (sp.length < 2 || !(knownKind(sp[0]) && !"any".equals(sp[0]) || "stealth".equals(sp[0]) || "sector".equals(sp[0]))) throw new IllegalArgumentException("ship needs sector:condition, not " + v);
				if (!sp[1].equals("wrecked") && !sp[1].equals("limping") && !sp[1].equals("towed") && !sp[1].equals("new") && !sp[1].equals("dented"))
					throw new IllegalArgumentException("unknown ship condition " + sp[1]);
				if (sp[1].equals("limping") && (sp.length < 3 || net.blerf.ftl.parser.SavedGameParser.SystemType.findById(sp[2]) == null))
					throw new IllegalArgumentException("a limping ship needs the system that failed: " + v);
				o.ship = v;
			}
			else if (k.equals("join")) {
				if (!v.equals("any") && !RACES.containsKey(v)) throw new IllegalArgumentException("unknown race " + v);
				o.join = v;
			} else if (k.equals("item")) {
				if (!v.equals("weapon") && !v.equals("drone") && !v.equals("augment")) throw new IllegalArgumentException("unknown item " + v);
				o.item = v;
			} else throw new IllegalArgumentException("unknown effect " + k);
		}
		if (o.text.isEmpty() && o.then == null) throw new IllegalArgumentException("an outcome needs words (only one that leads on to another step may go without)");
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
	/** The lost expedition's event (sector "saga"), or null. */
	static Event saga() { for (Event e : book().events) if (e.kinds.contains("saga")) return e; return null; }
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
		try { return new Posting(kind, Integer.parseInt(p.getProperty(i + ".danger", "1")), text, "true".equals(p.getProperty(i + ".sealed")), Integer.parseInt(p.getProperty(i + ".outfit", "0").trim())); }
		catch (NumberFormatException e) { return null; }
	}
	private static void put(Properties p, int i, Posting x) {
		p.setProperty(i + ".kind", x.kind); p.setProperty(i + ".danger", Integer.toString(x.danger)); p.setProperty(i + ".text", x.text);
		p.setProperty(i + ".sealed", Boolean.toString(x.sealed));
		p.setProperty(i + ".outfit", Integer.toString(x.outfit));
	}
	/** A posting not already on the board: one in SEALED_ONE_IN sealed (its sector rolled now, kept hidden), one in RARE_ONE_IN somewhere rare. */
	static Posting pick(Random rng, List<Posting> taken) {
		Posting x = pickPlain(rng, taken);
		return rng.nextInt(OUTFIT_ONE_IN) == 0 ? x.outfitted(OUTFIT_MIN + rng.nextInt(OUTFIT_MAX - OUTFIT_MIN + 1)) : x;
	}
	private static Posting pickPlain(Random rng, List<Posting> taken) {
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
		/** Ships coming home with the crew. */
		final List<HomeShip> ships = new ArrayList<HomeShip>();
		/** Extra beacons of the fleet's time (the lost expedition's weeks of waiting). */
		int waited;
		/** The lost expedition's survivor, who comes home aboard the Stealth Cruiser rather than to the Cargo Hold. */
		CrewState survivor;
		private boolean ended = false;
		/** Turned for home before the job was done (or no one left to finish it): no job pay, the outfitting lost. */
		boolean turnedBack = false;
		/** Crew sent out already injured: hurt again, they're lost. */
		final java.util.Set<CrewState> injuredBefore = new java.util.HashSet<CrewState>();
		/** Of the lost, those taken rather than killed. */
		final List<CrewState> captured = new ArrayList<CrewState>();

		Run(int slot, Posting posting, List<CrewState> party, List<String> recent, Random rng) {
			this.slot = slot; this.posting = posting; this.party = new ArrayList<CrewState>(party); this.rng = rng;
			this.events = draw(posting, recent, rng);
			for (CrewState c : party) if (Expeditions.injured(c)) injuredBefore.add(c);
			Event saga = saga();
			// the lost expedition: very rarely, a dangerous job with two or more crew goes missing at its end
			if (saga != null && posting.danger >= 3 && party.size() >= 2 && (alwaysLost || rng.nextInt(LOST_ONE_IN) == 0)) events.add(saga);
		}
		/** Has this one gone missing (the lost expedition is under way, or still to come)? */
		public boolean lostExpedition() { Event s = saga(); return s != null && events.contains(s); }
		/** The ships coming home, for the Space Dock or Junkyard question. */
		public List<HomeShip> ships() { return new ArrayList<HomeShip>(ships); }
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
			if (event() != null && event().kinds.contains("saga")) return c.roll; // the lost expedition's odds are its own, whoever is left
			List<CrewState> here = alive();
			int injured = 0;
			for (CrewState x : here) if (hurt.containsKey(x) || injuredBefore.contains(x)) injured++;
			int p = c.roll + CREW_ODDS * (here.size() - 1) - INJURED_ODDS * injured;
			if (c.fight) p += MANTIS_FIGHT * count("mantis") + ROCK_FIGHT * count("rock") - ENGI_FIGHT * count("engi");
			if (posting.outfit > 0) p += OUTFIT_ODDS;
			Event ev = event();
			if (ev != null) p -= ev.risk >= 3 ? RISK_HIGH : ev.risk == 2 ? RISK_MODERATE : 0;
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
			if (o.aside != null && rng.nextInt(100) < o.asideChance) said += " " + fill(o.aside, who, anyone);
			StringBuilder extra = new StringBuilder();
			int got = o.scrapMax <= 0 ? 0 : o.scrapMin + rng.nextInt(o.scrapMax - o.scrapMin + 1);
			if (posting.outfit > 0) got = got * (100 + OUTFIT_SCRAP) / 100; // outfitted crew bring back more
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
				if (rng.nextInt(CAPTURED_ONE_IN) == 0) captured.add(l); // taken, not killed: nobody knows yet
				extra.append("\n\n").append(l.getName()).append(" did not come back.");
			}
			for (int i = 0; i < o.injure && !alive().isEmpty(); i++) {
				CrewState h = i == 0 && alive().contains(who) ? who : alive().get(rng.nextInt(alive().size()));
				int times = (hurt.containsKey(h) ? hurt.get(h) + 1 : 1) + (injuredBefore.contains(h) ? 1 : 0);
				Event ev = event();
				int fatal = ev == null || ev.kinds.contains("saga") ? 0 : ev.risk >= 3 ? FATAL_HIGH : ev.risk == 2 ? FATAL_MODERATE : 0;
				if (times >= 2) { // hurt twice (or sent out hurt): too much
					lost.add(h);
					extra.append("\n\n").append(h.getName()).append(" was hurt again, and did not make it.");
				} else if (rng.nextInt(100) < fatal) {
					lost.add(h);
					extra.append("\n\n").append(h.getName()).append(" was hurt worse than anyone knew, and did not make it.");
				} else {
					hurt.put(h, times);
					extra.append("\n\n").append(h.getName()).append(" is injured.");
				}
			}
			if (got > 0) extra.append("\n\n").append(got).append(" scrap.");
			if (o.fuel > 0) extra.append(" Fuel: ").append(o.fuel).append(".");
			if (o.missiles > 0) extra.append(" Missiles: ").append(o.missiles).append(".");
			if (o.parts > 0) extra.append(" Drone parts: ").append(o.parts).append(".");
			if (o.survivor && survivor == null && !alive().isEmpty()) {
				List<CrewState> here2 = alive();
				survivor = here2.get(0); // the one the story has been naming
				for (CrewState x : here2) if (x != survivor) { lost.add(x); extra.append("\n\n").append(x.getName()).append(" did not come back."); }
			}
			waited += o.beacons;
			if (o.ship != null) ships.add(new HomeShip(o.ship, posting.kind));
			Event saga = saga();
			if (o.end && saga != null && events.indexOf(saga) > index) { turnedBack = true; index = events.indexOf(saga); step = ""; return said + extra; } // turning for home: the lost expedition goes missing all the same
			if (o.end) { ended = true; turnedBack = true; }
			if (o.then != null && !o.end && !alive().isEmpty()) step = o.then;
			else { index++; step = ""; }
			return said + extra;
		}
		private String fill(String t, CrewState who, CrewState anyone) {
			CrewState sv = survivor != null ? survivor : who;
			return t.replace("{who}", who.getName()).replace("{crew}", anyone.getName()).replace("{sector}", posting.realSector())
					.replace("{enemy}", enemyOf(posting.kind)).replace("{survivor}", sv.getName());
		}
		/** A choice's words as its button shows them: {who} is the crew member it would be about. */
		public String label(Choice c) {
			CrewState who = c.race != null ? of(c.race) : null;
			if (who == null && !alive().isEmpty()) who = alive().get(0);
			String name = who == null ? "someone" : who.getName();
			return c.label().replace("{who}", name).replace("{crew}", name).replace("{sector}", posting.realSector());
		}
		/** The job's own pay, for anyone who comes back: more for a dangerous one, half again for a sealed one. */
		/** Was the job done: someone left, and nobody turned for home early? */
		public boolean finished() { return !turnedBack && !alive().isEmpty(); }
		/** What the outfitting cost: per head, for everyone sent. */
		public int outfitting() { return posting.outfit * party.size(); }
		int pay() {
			if (!finished()) return 0; // the job's pay is for the job done
			int lo = posting.danger <= 1 ? 3 : posting.danger == 2 ? 5 : 8, hi = posting.danger <= 1 ? 6 : posting.danger == 2 ? 10 : 14;
			int p = lo + new Random(posting.text.hashCode() ^ party.size()).nextInt(hi - lo + 1);
			return posting.sealed ? p * 3 / 2 : p;
		}
		public String fillEvent(String t) {
			String one = survivor != null ? survivor.getName() : alive().isEmpty() ? "your crew" : alive().get(0).getName();
			return t.replace("{sector}", posting.realSector()).replace("{crew}", alive().isEmpty() ? "your crew" : alive().get(0).getName())
					.replace("{enemy}", enemyOf(posting.kind)).replace("{survivor}", one);
		}
	}

	/** A ship coming home from an expedition: where from, in what state, and where she's to go. */
	public static final class HomeShip {
		/** "sector:condition[:system]", the sector "stealth" for the lost expedition's cruiser. */
		public final String spec;
		final String sector;
		/** Send her to the Space Dock (true) or the Junkyard (false, unless asked). */
		public boolean toDock = false;
		/** Once home: her place in the fleet. */
		public Ship ship;
		HomeShip(String spec, String postingKind) {
			this.spec = spec;
			String s = spec.split(":")[0];
			this.sector = s.equals("sector") ? postingKind : s;
		}
		public String condition() { return spec.split(":")[1]; }
		public String system() { String[] p = spec.split(":"); return p.length > 2 ? p[2] : null; }
		public boolean stealth() { return spec.startsWith("stealth"); }
	}
	/** Builds a ship coming home: a derelict of a model fitting her sector, in the condition her story says; the lost expedition's cruiser near new. */
	static net.blerf.ftl.parser.SavedGameParser.SavedGameState build(HomeShip h, java.util.Collection<String> taken, Random rng) {
		String[] models = h.stealth() ? new String[] {STEALTH} : shipModels(h.sector);
		String base = models[rng.nextInt(models.length)];
		List<String> variants = new ArrayList<String>();
		for (int n = 0; n < 3; n++) {
			String id = n == 0 ? base : base + "_" + (n + 1);
			if (DataManager.get().getShips().get(id) != null && (h.stealth() || CompanionMod.fileOf(id) != null)) variants.add(id);
		}
		String id = variants.get(rng.nextInt(variants.size()));
		String name = ShipNames.roll(id, taken, rng);
		if (name == null) name = "Expedition Prize";
		net.blerf.ftl.parser.SavedGameParser.SavedGameState gs;
		net.blerf.ftl.parser.SavedGameParser.SystemType broken = h.system() == null ? null : net.blerf.ftl.parser.SavedGameParser.SystemType.findById(h.system());
		if (h.stealth()) {
			// her own blueprint (no companion mod needed), everything standard aboard; "dented": some hull and a bar or two
			gs = Commission.build(id, name, net.blerf.ftl.constants.Difficulty.NORMAL, rng);
			gs.getPlayerShip().getCrewList().clear();
			gs.setTotalCrewHired(0);
			if ("dented".equals(h.condition())) {
				ShipState s = gs.getPlayerShip();
				s.setHullAmt(Math.max(1, s.getHullAmt() * (60 + rng.nextInt(16)) / 100));
				dent(s, 1 + rng.nextInt(2), rng);
			}
			return gs;
		}
		gs = Derelicts.build(id, name, rng);
		ShipState s = gs.getPlayerShip();
		int max = Derelicts.maxHull(s);
		if ("towed".equals(h.condition())) { // a prize, not a wreck: most of her hull, most of her systems working, no breaches
			s.setHullAmt(Math.max(s.getHullAmt(), (max * (55 + rng.nextInt(26)) + 99) / 100));
			for (net.blerf.ftl.parser.SavedGameParser.SystemType t : net.blerf.ftl.parser.SavedGameParser.SystemType.values()) {
				net.blerf.ftl.parser.SavedGameParser.SystemState st = s.getSystem(t);
				if (st == null || st.getCapacity() <= 0 || st.getDamagedBars() == 0 || rng.nextInt(3) == 0) continue;
				st.setDamagedBars(0);
				if (t.isSubsystem()) st.setPower(st.getCapacity());
			}
			s.getBreachMap().clear();
		}
		if (broken != null) { // she made it home just as this gave out: broken through, or gone
			net.blerf.ftl.parser.SavedGameParser.SystemState st = s.getSystem(broken);
			if (st == null || st.getCapacity() <= 0 || rng.nextInt(3) == 0) {
				if (st != null) { st.setCapacity(0); st.setPower(0); st.setDamagedBars(0); }
			} else {
				st.setDamagedBars(st.getCapacity());
				st.setPower(0);
			}
			Retrofit.syncStations(s);
		}
		return gs;
	}
	/** Breaks a bar or two of her working systems (the lost expedition's cruiser, under fire). */
	private static void dent(ShipState s, int bars, Random rng) {
		List<net.blerf.ftl.parser.SavedGameParser.SystemState> on = new ArrayList<net.blerf.ftl.parser.SavedGameParser.SystemState>();
		for (net.blerf.ftl.parser.SavedGameParser.SystemType t : net.blerf.ftl.parser.SavedGameParser.SystemType.values()) {
			net.blerf.ftl.parser.SavedGameParser.SystemState st = s.getSystem(t);
			if (st != null && st.getCapacity() > 0) on.add(st);
		}
		for (int i = 0; i < bars && !on.isEmpty(); i++) {
			net.blerf.ftl.parser.SavedGameParser.SystemState st = on.get(rng.nextInt(on.size()));
			if (st.getDamagedBars() < st.getCapacity()) st.setDamagedBars(st.getDamagedBars() + 1);
			if (st.getPower() > st.getCapacity() - st.getDamagedBars()) st.setPower(st.getCapacity() - st.getDamagedBars());
		}
	}

	/** Sends these crew (from the Cargo Hold) on the posting in this place. */
	public static Run start(Vault v, int slot, List<CrewState> party, Random rng) throws IOException {
		if (party.isEmpty()) throw new IOException("No one was sent");
		if (party.size() > PARTY_MAX) throw new IOException("At most " + PARTY_MAX + " can go");
		Posting x = board(v).get(slot);
		int cost = x.outfit * party.size();
		if (cost > 0 && v.storageScrap() < cost) throw new IOException("Outfitting " + party.size() + " costs " + cost + " scrap; the Cargo Hold holds " + v.storageScrap());
		return new Run(slot, x, party, recent(v), rng);
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
		int pay = r.pay(), outfit = r.outfitting();
		int payback = outfit > 0 && r.finished() ? outfit * (PAYBACK_MIN + new Random().nextInt(PAYBACK_MAX - PAYBACK_MIN + 1)) / 100 : 0;
		if (hold.getScrapAmt() + pay + r.scrap + payback < outfit) throw new IOException("The Cargo Hold can no longer pay the outfitting (" + outfit + " scrap); nothing was changed");
		hold.setScrapAmt(hold.getScrapAmt() + pay + r.scrap + payback - outfit);
		hold.setFuelAmt(hold.getFuelAmt() + r.fuel);
		hold.setMissilesAmt(hold.getMissilesAmt() + r.missiles);
		hold.setDronePartsAmt(hold.getDronePartsAmt() + r.parts);
		for (String id : r.items) {
			if (homeplanet.model.Items.isWeapon(id)) hold.getWeaponList().add(SaveHelper.newIdleWeapon(id));
			else if (homeplanet.model.Items.isDrone(id)) hold.getDroneList().add(SaveHelper.newIdleDrone(id));
			else hold.getAugmentIdList().add(id);
		}
		List<String> lostNames = new ArrayList<String>(), hurtNames = new ArrayList<String>(), joinedNames = new ArrayList<String>();
		CrewState aboard = null; // the lost expedition's survivor, moved from the hold to her cruiser
		for (CrewState sent : r.party) {
			CrewState mine = match(crew, sent);
			if (mine == null) throw new IOException(sent.getName() + " is no longer in the Cargo Hold; nothing was changed");
			if (r.lost.contains(sent)) { crew.remove(mine); lostNames.add(sent.getName()); continue; }
			if (r.hurt.containsKey(sent)) { mine.setHealth(Math.max(1, mine.getHealth() / 2)); hurtNames.add(sent.getName()); }
			if (sent == r.survivor) { crew.remove(mine); aboard = mine; }
		}
		// the ships coming home, built before anything is written
		List<String> taken = new ArrayList<String>();
		for (Ship x : v.all()) if (x.name != null) taken.add(x.name);
		List<net.blerf.ftl.parser.SavedGameParser.SavedGameState> built = new ArrayList<net.blerf.ftl.parser.SavedGameParser.SavedGameState>();
		Random rng = new Random();
		for (HomeShip h : r.ships) {
			net.blerf.ftl.parser.SavedGameParser.SavedGameState g = build(h, taken, rng);
			taken.add(g.getPlayerShipName());
			if (h.stealth() && aboard != null) { SaveHelper.placeCrew(g.getPlayerShip(), aboard, false); g.getPlayerShip().getCrewList().add(aboard); }
			built.add(g);
		}
		for (CrewState n : r.joined) {
			if (!SaveHelper.placeCrew(hold, n, true)) continue; // no room: they find other work
			crew.add(n);
			joinedNames.add(n.getName());
		}
		v.begin().put(st, c.save, c.hash).commit();
		List<String> shipNames = new ArrayList<String>();
		for (int i = 0; i < built.size(); i++) {
			HomeShip h = r.ships.get(i);
			net.blerf.ftl.parser.SavedGameParser.SavedGameState g = built.get(i);
			h.ship = h.toDock ? v.adopt(g) : v.adoptJunked(g);
			v.setOut(h.ship, g, h.stealth() ? "Came home from the " + r.posting.realSector() + " under the command of " + (aboard == null ? "her crew" : aboard.getName())
					: "Brought home from an expedition to the " + r.posting.realSector());
			shipNames.add(g.getPlayerShipName() + " (to the " + (h.toDock ? "Space Dock" : "Junkyard") + ")");
		}
		v.countBeacon();
		for (int i = 0; i < r.waited; i++) v.countBeacon(); // weeks of waiting for word
		for (CrewState x : r.captured) takeCaptive(v, x, r.posting); // a ransom will be asked
		StringBuilder sb = new StringBuilder();
		String where = r.posting.realSector();
		sb.append(r.alive().isEmpty() ? "No one came back from the expedition to the " + where + "."
				: !r.finished() ? "The expedition to the " + where + " turned back before the job was done: there's no pay for it" + (r.scrap > 0 ? ", but " + r.scrap + " scrap came of it" : "") + "."
				: "The expedition to the " + where + " is over. " + (pay > 0 ? "The job paid " + pay + " scrap" : "")
				+ (r.scrap > 0 ? (pay > 0 ? ", and " : "") + r.scrap + " scrap more came of it" : "") + ".");
		if (outfit > 0) sb.append(payback > 0 ? "\nThe outfitting (" + outfit + " scrap) came back as " + payback + " scrap."
				: "\nThe outfitting (" + outfit + " scrap) is lost.");
		if (!r.items.isEmpty()) { List<String> t = new ArrayList<String>(); for (String id : r.items) t.add(homeplanet.model.Items.title(id)); sb.append("\nBrought back: ").append(String.join(", ", t)).append("."); }
		if (!joinedNames.isEmpty()) sb.append("\nNew crew: ").append(String.join(", ", joinedNames)).append(".");
		if (!shipNames.isEmpty()) sb.append("\nShips home: ").append(String.join(", ", shipNames)).append(".");
		if (!hurtNames.isEmpty()) sb.append("\nInjured: ").append(String.join(", ", hurtNames)).append(".");
		if (!lostNames.isEmpty()) sb.append("\nDid not come back: ").append(String.join(", ", lostNames)).append(".");
		sb.append(aboard != null ? "\n\n" + aboard.getName() + " stays aboard the ship they brought home. Everything else is in the Cargo Hold." : "\n\nEverything else is in the Cargo Hold.");
		HistoryLog.entry("EXPEDITION", where + (r.posting.sealed ? " (sealed orders)" : "") + " (\"" + r.posting.text + "\"): " + (pay + r.scrap) + " scrap"
				+ (r.items.isEmpty() ? "" : ", " + String.join(", ", r.items)) + (joinedNames.isEmpty() ? "" : "; joined: " + String.join(", ", joinedNames))
				+ (shipNames.isEmpty() ? "" : "; ships: " + String.join(", ", shipNames)) + (r.waited > 0 ? "; missing for " + r.waited + " beacons" : "")
				+ (outfit > 0 ? "; outfitting " + outfit + " scrap, back " + payback : "") + (r.finished() ? "" : "; turned back")
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
	private static Properties readCaptives(Vault v) {
		Properties p = new Properties();
		File f = captivesFile(v);
		if (!f.isFile()) return p;
		try { p.load(new java.io.StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); }
		return p;
	}
	private static void writeCaptives(Vault v, Properties p) throws IOException {
		java.io.StringWriter w = new java.io.StringWriter();
		p.store(w, "Crew taken on expeditions, and the ransoms asked for them");
		SafeFiles.writeText(captivesFile(v), w.toString(), false);
	}
	static synchronized void takeCaptive(Vault v, CrewState c, Posting from) {
		Properties p = readCaptives(v);
		int i = 0;
		while (p.getProperty(i + ".name") != null) i++;
		Random rng = new Random();
		int asked = v.beaconsSeen() + RANSOM_DELAY_MIN + rng.nextInt(RANSOM_DELAY_MAX - RANSOM_DELAY_MIN + 1);
		p.setProperty(i + ".name", c.getName());
		p.setProperty(i + ".race", c.getRace() == null ? "human" : c.getRace().getId());
		p.setProperty(i + ".male", Boolean.toString(c.isMale()));
		p.setProperty(i + ".captors", enemyOf(from.kind));
		p.setProperty(i + ".ransom", Integer.toString(20 + 5 * from.danger + rng.nextInt(16)));
		p.setProperty(i + ".asked", Integer.toString(asked));
		p.setProperty(i + ".until", Integer.toString(asked + RANSOM_STANDS));
		p.setProperty(i + ".state", "held");
		try { writeCaptives(v, p); } catch (IOException e) { log.warn("Could not record a captive: {}", e.toString()); }
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
				p.getProperty(i + ".captors", "pirates"), intOf(p, i + ".ransom", 30), intOf(p, i + ".asked", 0), intOf(p, i + ".until", 0));
	}
	/**
	 * The ransoms' clock: a letter when a ransom is asked, a reminder near the end, and the Federation Ambassador's
	 * letter when it runs out (they're lost for good). Returns what's new, for a pop-up when the inbox is off.
	 */
	public static synchronized List<RansomNews> checkRansoms(Vault v) {
		Properties p = readCaptives(v);
		List<RansomNews> out = new ArrayList<RansomNews>();
		boolean changed = false;
		int now = v.beaconsSeen();
		for (int i = 0; p.getProperty(i + ".name") != null; i++) {
			String state = p.getProperty(i + ".state", "held");
			Captive c = captive(p, i);
			if (state.equals("held") && now >= c.asked) {
				state = "asked";
				p.setProperty(i + ".state", state);
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
		Properties p = readCaptives(v);
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
		Properties p = readCaptives(v);
		String state = p.getProperty(c.index + ".state", "");
		if (!state.equals("asked") && !state.equals("reminded")) throw new IOException(c.name + "'s ransom is no longer asked");
		if (v.beaconsSeen() > c.until) throw new IOException("The offer for " + c.name + " has run out");
		Ship st = v.storage();
		Vault.Copy cp = v.readCopy(st);
		ShipState hold = cp.save.getPlayerShip();
		if (hold.getScrapAmt() < c.ransom) throw new IOException("The Cargo Hold holds " + hold.getScrapAmt() + " scrap; the ransom is " + c.ransom);
		CrewState back = Commission.volunteer(c.race, new Random());
		if (back == null) throw new IOException("Unknown crew race " + c.race);
		back.setName(c.name);
		back.setMale(c.male);
		if (!SaveHelper.placeCrew(hold, back, true)) throw new IOException("The Cargo Hold has no room for another crew member");
		hold.getCrewList().add(back);
		hold.setScrapAmt(hold.getScrapAmt() - c.ransom);
		v.begin().put(st, cp.save, cp.hash).commit();
		p.setProperty(c.index + ".state", "ransomed");
		try { writeCaptives(v, p); } catch (IOException e) { log.warn("Could not mark {} ransomed: {}", c.name, e.toString()); }
		HistoryLog.entry("EXPEDITION", c.name + " ransomed from " + c.captors + " for " + c.ransom + " scrap, back in the Cargo Hold");
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
