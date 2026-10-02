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
 * holds three postings, each in one of FTL's sector types; a finished one is replaced at once. An expedition is two or
 * three short events from that sector's pool (expeditions.txt, read by this small engine), each with a few ways
 * through: some open only with a crew member of a race (blue, or red where the race makes things worse). It pays a
 * little scrap, now and then a cheap piece of gear, and crew can come back hurt, or not at all. Each finished
 * expedition counts as one beacon of the fleet's time. The board is kept in the fleet's expeditions.txt.
 */
public final class Expeditions {
	private static final Logger log = LoggerFactory.getLogger(Expeditions.class);
	private Expeditions() { }

	public static final int POSTINGS = 3, PARTY_MAX = 3;
	/** One posting in this many is in a rare place (a homeworld, the Hidden Crystal Worlds). */
	public static final int RARE_ONE_IN = 10;

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

	/** FTL's race ids by the names the events use. */
	private static final Map<String, String> RACES = new LinkedHashMap<String, String>();
	static {
		RACES.put("human", "human"); RACES.put("engi", "engi"); RACES.put("mantis", "mantis"); RACES.put("rock", "rock"); RACES.put("slug", "slug");
		RACES.put("zoltan", "energy"); RACES.put("crystal", "crystal"); RACES.put("lanius", "anaerobic");
	}
	/** A race as the option tags show it: "Rock", "Zoltan". */
	static String raceWord(String name) { return name.substring(0, 1).toUpperCase() + name.substring(1); }

	// ---- the events file ----

	/** What a choice comes to: scrap, gear, hurt or lost crew, and its words. */
	public static final class Outcome {
		int scrapMin, scrapMax, injure, lose, fuel, missiles, parts;
		String item; // weapon, drone or augment
		boolean end;
		String text = "";
	}
	/** One way through an event. */
	public static final class Choice {
		public String text = "";
		/** The race it needs aboard (an events-file name), or null. */
		public String race;
		/** A red option: the race makes things worse. */
		public boolean red;
		int roll = -1;
		Outcome sure, win, lose;
		/** As the button shows it: the race in brackets first. */
		public String label() { return race == null ? text : "(" + raceWord(race) + ") " + text; }
	}
	public static final class Event {
		public String id = "";
		final Set<String> kinds = new HashSet<String>();
		public String text = "";
		public final List<Choice> choices = new ArrayList<Choice>();
	}
	public static final class Posting {
		public final String kind, text;
		public final int danger;
		Posting(String kind, int danger, String text) { this.kind = kind; this.danger = danger; this.text = text; }
		public String sector() { return sectorName(kind); }
		public String dangerWord() { return danger <= 1 ? "Low" : danger == 2 ? "Moderate" : "High"; }
	}
	static final class Book {
		final List<Posting> postings = new ArrayList<Posting>();
		final List<Event> events = new ArrayList<Event>();
		final List<String> problems = new ArrayList<String>();
	}
	private static Book book;
	static synchronized Book book() {
		if (book == null) {
			InputStream in = Expeditions.class.getResourceAsStream("/homeplanet/resource/expeditions.txt");
			try {
				book = parse(in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8));
			} catch (IOException e) {
				book = new Book();
				book.problems.add("expeditions.txt can't be read: " + e);
			}
			for (String p : book.problems) log.warn("expeditions.txt: {}", p);
		}
		return book;
	}
	private static byte[] readAll(InputStream in) throws IOException {
		try {
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] b = new byte[8192];
			for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
			return out.toByteArray();
		} finally { in.close(); }
	}

	/** Reads the events file (see its header for the format); what's wrong with it goes in problems. */
	static Book parse(String all) {
		Book b = new Book();
		Event ev = null;
		Choice ch = null;
		int n = 0;
		for (String raw : all.split("\r?\n")) {
			n++;
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			try {
				if (line.startsWith("posting ")) {
					String[] p = line.split("\\s+", 4);
					if (!knownKind(p[1])) throw new IllegalArgumentException("unknown sector " + p[1]);
					b.postings.add(new Posting(p[1], Integer.parseInt(p[2]), p[3]));
					ev = null; ch = null;
				} else if (line.startsWith("event ")) {
					String[] p = line.split("\\s+");
					ev = new Event();
					ev.id = p[1];
					for (String k : p[2].split(",")) {
						if (!knownKind(k)) throw new IllegalArgumentException("unknown sector " + k);
						ev.kinds.add(k);
					}
					b.events.add(ev);
					ch = null;
				} else if (ev == null) {
					throw new IllegalArgumentException("text outside an event");
				} else if (line.startsWith("*")) {
					ch = new Choice();
					String t = line.substring(1).trim();
					if (t.startsWith("[")) {
						String tag = t.substring(1, t.indexOf(']')).trim();
						t = t.substring(t.indexOf(']') + 1).trim();
						if (tag.startsWith("red ")) { ch.red = true; tag = tag.substring(4).trim(); }
						if (!RACES.containsKey(tag)) throw new IllegalArgumentException("unknown race " + tag);
						ch.race = tag;
					}
					ch.text = t;
					ev.choices.add(ch);
				} else if (ch == null) {
					ev.text = ev.text.isEmpty() ? line : ev.text + " " + line;
				} else if (line.startsWith("roll ")) {
					ch.roll = Integer.parseInt(line.substring(5).trim());
				} else if (line.startsWith("win ")) {
					ch.win = outcome(line.substring(4));
				} else if (line.startsWith("lose ")) {
					ch.lose = outcome(line.substring(5));
				} else if (line.startsWith("ok ")) {
					ch.sure = outcome(line.substring(3));
				} else {
					throw new IllegalArgumentException("can't read: " + line);
				}
			} catch (RuntimeException e) {
				b.problems.add("line " + n + ": " + e.getMessage());
			}
		}
		for (Event e : b.events) {
			int open = 0;
			for (Choice c : e.choices) {
				if (c.race == null) open++;
				if (c.roll >= 0 ? c.win == null || c.lose == null : c.sure == null) b.problems.add("event " + e.id + ": \"" + c.text + "\" has no outcome");
			}
			if (open < 2) b.problems.add("event " + e.id + ": fewer than two ways through without a race");
			if (e.text.isEmpty()) b.problems.add("event " + e.id + ": no text");
		}
		return b;
	}
	/** "scrap 5-10 injure 1 | words" */
	private static Outcome outcome(String s) {
		Outcome o = new Outcome();
		int bar = s.indexOf('|');
		if (bar < 0) throw new IllegalArgumentException("an outcome needs | and its words");
		o.text = s.substring(bar + 1).trim();
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
			else if (k.equals("item")) {
				if (!v.equals("weapon") && !v.equals("drone") && !v.equals("augment")) throw new IllegalArgumentException("unknown item " + v);
				o.item = v;
			} else throw new IllegalArgumentException("unknown effect " + k);
		}
		return o;
	}
	/** What's wrong with the events file, if anything (the harness checks it's nothing). */
	public static List<String> problems() { return book().problems; }
	public static int eventCount() { return book().events.size(); }
	/** Events an expedition to this sector could meet. */
	static List<Event> pool(String kind) {
		List<Event> out = new ArrayList<Event>();
		String parent = parentOf(kind);
		for (Event e : book().events)
			if (e.kinds.contains(kind) || e.kinds.contains("any") && !rare(kind) || !parent.isEmpty() && e.kinds.contains(parent)) out.add(e);
		return out;
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
		if (kind == null || text == null || !knownKind(kind)) return null;
		try { return new Posting(kind, Integer.parseInt(p.getProperty(i + ".danger", "1")), text); } catch (NumberFormatException e) { return null; }
	}
	private static void put(Properties p, int i, Posting x) {
		p.setProperty(i + ".kind", x.kind); p.setProperty(i + ".danger", Integer.toString(x.danger)); p.setProperty(i + ".text", x.text);
	}
	/** A posting not already on the board; one in RARE_ONE_IN somewhere rare. */
	static Posting pick(Random rng, List<Posting> taken) {
		boolean wantRare = rng.nextInt(RARE_ONE_IN) == 0;
		List<Posting> common = new ArrayList<Posting>(), rare = new ArrayList<Posting>();
		for (Posting x : book().postings) {
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
		p.store(w, "The expeditions board: three postings");
		SafeFiles.writeText(boardFile(v), w.toString(), false);
	}

	// ---- an expedition ----

	/** The crew in the Cargo Hold, who can be sent. */
	public static List<CrewState> holdCrew(Vault v) throws IOException {
		return homeplanet.parser.SaveHelper.getOwnCrew(v.readCopy(v.storage()).save.getPlayerShip());
	}

	/** One expedition under way: its events in order, and what has come of them so far. */
	public static final class Run {
		public final int slot;
		public final Posting posting;
		public final List<CrewState> party;
		private final List<Event> events = new ArrayList<Event>();
		private int step = 0;
		private final Random rng;
		int scrap, fuel, missiles, parts;
		final List<String> items = new ArrayList<String>();
		final Map<CrewState, Integer> hurt = new HashMap<CrewState, Integer>();
		final List<CrewState> lost = new ArrayList<CrewState>();
		private boolean ended = false;

		Run(int slot, Posting posting, List<CrewState> party, Random rng) {
			this.slot = slot; this.posting = posting; this.party = new ArrayList<CrewState>(party); this.rng = rng;
			List<Event> pool = pool(posting.kind);
			int n = posting.danger <= 1 ? 2 : posting.danger == 2 ? 2 + rng.nextInt(2) : 3;
			while (events.size() < n && !pool.isEmpty()) events.add(pool.remove(rng.nextInt(pool.size())));
		}
		public boolean over() { return ended || step >= events.size() || alive().isEmpty(); }
		public Event event() { return over() ? null : events.get(step); }
		public int number() { return step + 1; }
		public int length() { return events.size(); }
		public List<CrewState> alive() {
			List<CrewState> out = new ArrayList<CrewState>(party);
			out.removeAll(lost);
			return out;
		}
		/** The event's choices open to this party: a race's only with one of that race still with them. */
		public List<Choice> choices() {
			List<Choice> out = new ArrayList<Choice>();
			Event e = event();
			if (e == null) return out;
			for (Choice c : e.choices) if (c.race == null || of(c.race) != null) out.add(c);
			return out;
		}
		private CrewState of(String race) {
			String id = RACES.get(race);
			for (CrewState c : alive()) if (c.getRace() != null && c.getRace().getId().equals(id)) return c;
			return null;
		}
		/** Takes a choice: its outcome (rolled, if it's a gamble) applied; returns its words. */
		public String choose(Choice c) {
			Outcome o = c.roll < 0 ? c.sure : rng.nextInt(100) < c.roll ? c.win : c.lose;
			CrewState who = c.race != null ? of(c.race) : null;
			List<CrewState> here = alive();
			if (who == null) who = here.get(rng.nextInt(here.size()));
			CrewState anyone = here.get(rng.nextInt(here.size()));
			CrewState victim = who;
			StringBuilder extra = new StringBuilder();
			int got = o.scrapMax <= 0 ? 0 : o.scrapMin + rng.nextInt(o.scrapMax - o.scrapMin + 1);
			scrap += got;
			fuel += o.fuel; missiles += o.missiles; parts += o.parts;
			if (o.item != null) {
				String id = gear(o.item, rng);
				if (id != null) { items.add(id); extra.append("\n\nFound: ").append(homeplanet.model.Items.title(id)).append("."); }
			}
			for (int i = 0; i < o.lose && !alive().isEmpty(); i++) {
				CrewState l = i == 0 && alive().contains(victim) ? victim : alive().get(rng.nextInt(alive().size()));
				lost.add(l);
				extra.append("\n\n").append(l.getName()).append(" did not come back.");
			}
			for (int i = 0; i < o.injure && !alive().isEmpty(); i++) {
				CrewState h = i == 0 && alive().contains(victim) ? victim : alive().get(rng.nextInt(alive().size()));
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
			step++;
			return fill(o.text, who, anyone) + extra;
		}
		private String fill(String t, CrewState who, CrewState anyone) {
			return t.replace("{who}", who.getName()).replace("{crew}", anyone.getName()).replace("{sector}", posting.sector());
		}
		/** The job's own pay, for anyone who comes back: more for a dangerous one. */
		int pay() {
			if (alive().isEmpty()) return 0;
			int lo = posting.danger <= 1 ? 3 : posting.danger == 2 ? 5 : 8, hi = posting.danger <= 1 ? 6 : posting.danger == 2 ? 10 : 14;
			return lo + new Random(posting.text.hashCode() ^ party.size()).nextInt(hi - lo + 1);
		}
		/** A choice's words as its button shows them: {who} is the crew member it would be about. */
		public String label(Choice c) {
			CrewState who = c.race != null ? of(c.race) : null;
			if (who == null && !alive().isEmpty()) who = alive().get(0);
			return c.label().replace("{who}", who == null ? "someone" : who.getName()).replace("{crew}", who == null ? "someone" : who.getName()).replace("{sector}", posting.sector());
		}
		public String fillEvent(String t) { return t.replace("{sector}", posting.sector()).replace("{crew}", alive().isEmpty() ? "your crew" : alive().get(0).getName()); }
	}

	/** Sends these crew (from the Cargo Hold) on the posting in this place. */
	public static Run start(Vault v, int slot, List<CrewState> party, Random rng) throws IOException {
		if (party.isEmpty()) throw new IOException("No one was sent");
		if (party.size() > PARTY_MAX) throw new IOException("At most " + PARTY_MAX + " can go");
		return new Run(slot, board(v).get(slot), party, rng);
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
	 * The expedition is over: its pay, scrap and gear to the Cargo Hold, the injured hurt, the lost gone, all in one
	 * write; one beacon of the fleet's time; the history log; and a new posting in its place. Returns the summary.
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
		List<String> lostNames = new ArrayList<String>(), hurtNames = new ArrayList<String>();
		for (CrewState sent : r.party) {
			CrewState mine = match(crew, sent);
			if (mine == null) throw new IOException(sent.getName() + " is no longer in the Cargo Hold; nothing was changed");
			if (r.lost.contains(sent)) { crew.remove(mine); lostNames.add(sent.getName()); }
			else if (r.hurt.containsKey(sent)) { mine.setHealth(Math.max(1, mine.getHealth() / 2)); hurtNames.add(sent.getName()); }
		}
		v.begin().put(st, c.save, c.hash).commit();
		v.countBeacon();
		StringBuilder sb = new StringBuilder();
		sb.append(r.alive().isEmpty() ? "No one came back from the expedition to the " + r.posting.sector() + "."
				: "The expedition to the " + r.posting.sector() + " is over. " + (pay > 0 ? "The job paid " + pay + " scrap" : "")
				+ (r.scrap > 0 ? (pay > 0 ? ", and " : "") + r.scrap + " scrap more came of it" : "") + ".");
		if (!r.items.isEmpty()) { List<String> t = new ArrayList<String>(); for (String id : r.items) t.add(homeplanet.model.Items.title(id)); sb.append("\nBrought back: ").append(String.join(", ", t)).append("."); }
		if (!hurtNames.isEmpty()) sb.append("\nInjured: ").append(String.join(", ", hurtNames)).append(".");
		if (!lostNames.isEmpty()) sb.append("\nDid not come back: ").append(String.join(", ", lostNames)).append(".");
		sb.append("\n\nEverything is in the Cargo Hold.");
		HistoryLog.entry("EXPEDITION", r.posting.sector() + " (\"" + r.posting.text + "\"): " + (pay + r.scrap) + " scrap"
				+ (r.items.isEmpty() ? "" : ", " + String.join(", ", r.items)) + (lostNames.isEmpty() ? "" : "; did not come back: " + String.join(", ", lostNames))
				+ (hurtNames.isEmpty() ? "" : "; injured: " + String.join(", ", hurtNames)));
		Properties p = readBoard(v);
		List<Posting> others = new ArrayList<Posting>();
		for (int i = 0; i < POSTINGS; i++) if (i != r.slot && posting(p, i) != null) others.add(posting(p, i));
		others.add(r.posting); // not the same job again at once
		put(p, r.slot, pick(new Random(), others));
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
