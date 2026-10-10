package homeplanet.parser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;

import homeplanet.core.Event;
import homeplanet.core.HistoryLog;
import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;
import homeplanet.core.Store;
import homeplanet.model.Skills;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * Crew expeditions, heromedel's second system (expedition_type 2 in the cfg; the first is {@link Expeditions}, which this
 * shares nothing with but the infirmary, the captives and the hire button). The board offers three sectors; the
 * commander picks one and sends one to three crew from the Cargo Hold, and they leave it for a while. What job they
 * find there is drawn from the sector's weights, unseen. Each rolls a d20 for their outcome, with one reroll when the
 * sector or the job suits their race; the pot is 2d10 scrap, grown or cut by the headcount, each one's outcome, their
 * race's fit for the sector and the job, their role's skill (6.39: each gets a role from the job's primary, secondary and
 * other skills, lore/expeditions.xml's roles) and any hazard. A natural 20 may find an item; a Hijack,
 * Salvage or Rescue that went well may bring a prize. When they're back, a report says what happened in plain words,
 * never a roll or a percentage: the player learns who to send where from the reports alone.
 * Kept in the fleet's expeditions/expeditions.xml (the board, who's away); the words and the jobs' roles in lore/expeditions.xml.
 */
public final class Assignments {
	private static final Logger log = LoggerFactory.getLogger(Assignments.class);
	private Assignments() { }

	public static final int OFFERS = 3, PARTY_MAX = 3;
	/**
	 * A detail is away AWAY_MIN to AWAY_MAX beacons, and longer as the job went ({@link #days}: a nebula, Abandoned or
	 * Crystal space, Got Lost, failures, things to carry home, Rock crew; a Scout is quicker), never past AWAY_CAP and
	 * never shown (heromedel, 5.16: a week or two, not a day or three). Setting out passes no time: arranging a second
	 * detail must not bring the first home. The result is rolled when they set out (so the delay can know it) and told
	 * only when they're back.
	 */
	public static final int AWAY_MIN = 7, AWAY_MAX = 14, AWAY_CAP = 21;
	/** An offer not taken comes down after OFFER_MIN to OFFER_MAX beacons (rolled, never shown). */
	public static final int OFFER_MIN = 3, OFFER_MAX = 8;
	/** The pot: 2d10, times 1 + 10% a head + everyone's modifiers; never under 1. */
	public static final int BASE_DICE = 2, BASE_DIE = 10, PER_HEAD = 10;
	/** One expedition in this many meets a hazard. */
	public static final int HAZARD_ONE_IN = 10;

	// ---- the tables, as heromedel set them ----

	/** The sectors: id, title, FTL's own name; Crystal is drawn rarely. */
	public static final String[][] SECTORS = {
		{"civilian", "Civilian Sector"}, {"engi", "Engi Controlled Sector"}, {"zoltan", "Zoltan Controlled Sector"},
		{"mantis", "Mantis Controlled Sector"}, {"pirate", "Pirate Controlled Sector"}, {"rebel", "Rebel Controlled Sector"},
		{"rock", "Rock Controlled Sector"}, {"nebula", "Nebula"}, {"abandoned", "Abandoned Sector"}, {"crystal", "Hidden Crystal Worlds"}};
	public static String sectorTitle(String id) { for (String[] s : SECTORS) if (s[0].equals(id)) return s[1]; return id; }
	/** How often a sector is drawn for the board (Crystal rarely). */
	static int sectorWeight(String id) { return "crystal".equals(id) ? 1 : 4; }

	/** The jobs: id, title, base weight, and the skill that helped before 6.39 (unused: each job's roles are in lore/expeditions.xml, read by {@link #roles}). */
	public static final Object[][] JOBS = {
		{"defend", "Defend", 14, "shields"}, {"attack", "Attack", 11, "weapons"}, {"negotiate", "Negotiate", 10, null}, {"boarded", "Get Boarded", 9, "combat"},
		{"rescue", "Rescue", 9, null}, {"salvage", "Salvage", 8, "repair"}, {"scout", "Scout", 8, "pilot"}, {"repair", "Repair", 8, "repair"},
		{"transport", "Transport", 8, "engines"}, {"lost", "Got Lost", 8, "pilot"}, {"escort", "Escort", 8, "engines"}, {"capture", "Capture", 8, "combat"},
		{"board", "Board", 7, "combat"}, {"hijack", "Hijack", 7, "pilot"}, {"infection", "Infection", 7, "repair"}, {"spiders", "Giant Spiders", 6, "combat"}};
	public static String jobTitle(String id) { for (Object[] j : JOBS) if (j[0].equals(id)) return (String) j[1]; return id; }
	/** The job's primary skill (6.39: from its roles), or -1 for a job of race alone. */
	public static int jobSkill(String id) { return roles(id).primary; }
	/** Each sector adds 5 to two jobs and takes 5 from two, so every sector still totals 136. */
	static final Map<String, String[][]> SECTOR_JOBS = new LinkedHashMap<String, String[][]>();
	static {
		SECTOR_JOBS.put("civilian", new String[][] {{"negotiate", "transport"}, {"spiders", "capture"}});
		SECTOR_JOBS.put("engi", new String[][] {{"repair", "negotiate"}, {"defend", "capture"}});
		SECTOR_JOBS.put("zoltan", new String[][] {{"defend", "scout"}, {"rescue", "transport"}});
		SECTOR_JOBS.put("mantis", new String[][] {{"boarded", "lost"}, {"negotiate", "transport"}});
		SECTOR_JOBS.put("pirate", new String[][] {{"defend", "hijack"}, {"repair", "escort"}});
		SECTOR_JOBS.put("rebel", new String[][] {{"defend", "capture"}, {"negotiate", "rescue"}});
		SECTOR_JOBS.put("rock", new String[][] {{"defend", "boarded"}, {"infection", "lost"}});
		SECTOR_JOBS.put("nebula", new String[][] {{"lost", "scout"}, {"escort", "capture"}});
		SECTOR_JOBS.put("abandoned", new String[][] {{"salvage", "infection"}, {"negotiate", "escort"}});
		SECTOR_JOBS.put("crystal", new String[][] {{"negotiate", "scout"}, {"infection", "hijack"}});
	}
	/** A job's weight in a sector. */
	public static int jobWeight(String sector, String job) {
		int w = 0;
		for (Object[] j : JOBS) if (j[0].equals(job)) w = (Integer) j[2];
		String[][] adj = SECTOR_JOBS.get(sector);
		if (adj != null) {
			for (String s : adj[0]) if (s.equals(job)) w += 5;
			for (String s : adj[1]) if (s.equals(job)) w -= 5;
		}
		return Math.max(0, w);
	}
	/** Races by FTL's ids: what each is good and bad at (±10% of the pot). */
	static final Map<String, String[][]> RACE_JOBS = new LinkedHashMap<String, String[][]>();
	static {
		RACE_JOBS.put("human", new String[][] {{"negotiate", "escort"}, {"infection", "spiders"}});
		RACE_JOBS.put("engi", new String[][] {{"repair", "salvage"}, {"attack", "board"}});
		RACE_JOBS.put("energy", new String[][] {{"defend", "repair"}, {"board", "hijack"}});
		RACE_JOBS.put("mantis", new String[][] {{"board", "attack"}, {"repair", "negotiate"}});
		RACE_JOBS.put("rock", new String[][] {{"rescue", "salvage"}, {"scout", "hijack"}});
		RACE_JOBS.put("slug", new String[][] {{"attack", "capture"}, {"defend", "escort"}});
		RACE_JOBS.put("anaerobic", new String[][] {{"board", "boarded"}, {"rescue", "negotiate"}});
		RACE_JOBS.put("crystal", new String[][] {{"defend", "boarded"}, {"negotiate", "scout"}});
	}
	/** A race's fit for a job: +10, -10 or 0. */
	public static int raceJob(String race, String job) {
		String[][] t = RACE_JOBS.get(race);
		if (t == null) return 0;
		for (String s : t[0]) if (s.equals(job)) return 10;
		for (String s : t[1]) if (s.equals(job)) return -10;
		return 0;
	}
	/** A race's fit for a sector, in percent of the pot (a race's own bonus wins over Abandoned's penalty to everyone). */
	public static int raceSector(String race, String sector) {
		if ("slug".equals(race)) return "nebula".equals(sector) ? 10 : "mantis".equals(sector) ? -10 : 0;
		if ("human".equals(race)) return "civilian".equals(sector) || "rebel".equals(sector) ? 10 : "rock".equals(sector) ? -10 : "abandoned".equals(sector) ? -10 : 0;
		if ("engi".equals(race)) return "engi".equals(sector) ? 10 : "pirate".equals(sector) ? -10 : "abandoned".equals(sector) ? -10 : 0;
		if ("anaerobic".equals(race)) return "engi".equals(sector) || "mantis".equals(sector) ? 20 : "civilian".equals(sector) ? -10 : 0;
		if ("mantis".equals(race)) return "pirate".equals(sector) ? 10 : "zoltan".equals(sector) ? -10 : "abandoned".equals(sector) ? -10 : 0;
		if ("rock".equals(race)) return "rock".equals(sector) || "abandoned".equals(sector) ? 10 : 0;
		if ("crystal".equals(race)) return "crystal".equals(sector) ? 10 : "civilian".equals(sector) ? -10 : 0;
		if ("energy".equals(race)) return "abandoned".equals(sector) ? -10 : 0;
		return "abandoned".equals(sector) ? -10 : 0;
	}
	/**
	 * The hazards: id, title, where (null: any sector), the races that shrug it off. The rebels' Anti-Ship Battery
	 * (FTL's planetary guns) is shrugged off by nobody, but each Engi sent is a one in BATTERY_HACK chance
	 * of hacking it for the whole detail (three Engi, always).
	 */
	static final Object[][] HAZARDS = {
		{"flare", "a solar flare", null, new String[] {"rock", "anaerobic"}}, {"asteroids", "an asteroid field", null, new String[] {"energy"}},
		{"pulsar", "a pulsar", null, new String[] {"engi"}}, {"storm", "a plasma storm", "nebula", new String[] {"slug"}},
		{"battery", "an Anti-Ship Battery", "rebel", new String[0]}};
	public static final int BATTERY_HACK = 3;
	static boolean cancels(String hazard, String race) {
		for (Object[] h : HAZARDS) if (h[0].equals(hazard)) for (String r : (String[]) h[3]) if (r.equals(race)) return true;
		return false;
	}
	/** The d20's bands: died, injured, failed, success, high, top; and what each does to the pot. */
	public static final String[] BANDS = {"died", "injured", "failed", "success", "high", "top"};
	/** How a band reads in the report (heromedel, 6.40): a cross for a failure, a star to three for the rest; nothing for the dead, the hurt, the taken. */
	public static final String[] STARS = {"", "", "\u2717 ", "\u2605 ", "\u2605\u2605 ", "\u2605\u2605\u2605 "};
	public static final int[] BAND_MOD = {-30, -20, -10, 10, 20, 30};
	public static int band(int roll) { return roll <= 1 ? 0 : roll <= 5 ? 1 : roll <= 9 ? 2 : roll <= 15 ? 3 : roll <= 19 ? 4 : 5; }
	/**
	 * Skill points a role's skill earns, by skill (pilot, engines, shields, weapons, repair, combat) and band (died,
	 * injured, failed, success, high, top): fixed whole points, as FTL pays them, sized to each skill's interval so every
	 * skill trains at about the same pace (heromedel, 6.39: combat had levelled far faster). An "other" role earns half,
	 * rounded down, at least 1 where the role's own would be any.
	 */
	static final int[][] TRAIN = {
		{0, 1, 1, 2, 2, 4}, {0, 1, 1, 2, 2, 4}, {0, 3, 3, 7, 7, 14}, {0, 5, 5, 10, 10, 20}, {0, 1, 1, 2, 2, 4}, {0, 0, 0, 1, 1, 2}}; // weapons a little more: it has the fewest roles (6.39)
	/** The points a role earns for this band. */
	public static int training(int skill, int band, boolean other) {
		if (skill < 0 || skill >= TRAIN.length) return 0;
		int p = TRAIN[skill][band];
		return other && p > 0 ? Math.max(1, p / 2) : p;
	}

	// ---- the roles (heromedel and McCarthy, 6.39) ----

	/** A job's roles: its primary skill (-1: none, a job of race alone), its secondaries, and the skills with no place in it. */
	public static final class Roles {
		public final int primary;
		public final int[] secondary;
		public final boolean[] na = new boolean[6];
		Roles(int primary, int[] secondary) { this.primary = primary; this.secondary = secondary; }
		/** The skills left for anyone over: not the primary, a secondary or an NA one. */
		List<Integer> others() {
			List<Integer> o = new ArrayList<Integer>();
			for (int s = 0; s < 6; s++) {
				boolean taken = na[s] || s == primary;
				for (int x : secondary) if (x == s) taken = true;
				if (!taken) o.add(s);
			}
			return o;
		}
	}
	private static Map<String, Roles> roles;
	private static long rolesStamp = -2;
	/** The roles of every job: the station's own from the jar, each job's replaced by a player's copy's (lore/expeditions.xml). */
	public static synchronized Roles roles(String job) {
		long stamp = homeplanet.core.Lore.stamp(homeplanet.core.Lore.EXPEDITIONS);
		if (roles == null || stamp != rolesStamp) {
			Map<String, Roles> out = new LinkedHashMap<String, Roles>();
			byte[] jar = homeplanet.core.Lore.jarBytes(homeplanet.core.Lore.EXPEDITIONS);
			try { if (jar != null) out.putAll(readRoles(jar, null)); } catch (IOException e) { log.warn("Could not read the expedition roles: {}", e.toString()); }
			java.io.File copy = homeplanet.core.Lore.copy(homeplanet.core.Lore.EXPEDITIONS);
			if (copy != null) {
				try { out.putAll(readRoles(SafeFiles.read(copy), "lore/" + homeplanet.core.Lore.EXPEDITIONS)); }
				catch (IOException e) { homeplanet.core.Lore.problem("lore/" + homeplanet.core.Lore.EXPEDITIONS + " could not be read for its roles (" + e.getMessage() + "); the station's own are used"); }
			}
			roles = out;
			rolesStamp = stamp;
		}
		Roles r = roles.get(job);
		return r != null ? r : new Roles(-1, new int[0]);
	}
	private static Map<String, Roles> readRoles(byte[] bytes, String where) throws IOException {
		org.w3c.dom.Element root;
		try {
			javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
			f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			f.setExpandEntityReferences(false);
			javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
			b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
				@Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
			});
			root = b.parse(new java.io.ByteArrayInputStream(bytes)).getDocumentElement();
		} catch (Exception e) {
			throw new IOException("broken XML: " + e.getMessage(), e);
		}
		Map<String, Roles> out = new LinkedHashMap<String, Roles>();
		org.w3c.dom.NodeList nl = root.getElementsByTagName("role");
		for (int i = 0; i < nl.getLength(); i++) {
			org.w3c.dom.Element x = (org.w3c.dom.Element) nl.item(i);
			String job = x.getAttribute("job").trim();
			try {
				if (jobTitle(job).equals(job)) throw new IOException("no job " + job);
				int primary = x.getAttribute("primary").trim().isEmpty() ? -1 : skill(x.getAttribute("primary"));
				List<Integer> sec = new ArrayList<Integer>();
				for (String t : x.getAttribute("secondary").split(",")) if (!t.trim().isEmpty()) sec.add(skill(t));
				int[] secondary = new int[sec.size()];
				for (int k = 0; k < secondary.length; k++) secondary[k] = sec.get(k);
				Roles r = new Roles(primary, secondary);
				for (String t : x.getAttribute("na").split(",")) if (!t.trim().isEmpty()) r.na[skill(t)] = true;
				if (primary >= 0 && r.na[primary]) throw new IOException("its primary is NA");
				for (int s2 : secondary) if (r.na[s2] || s2 == primary) throw new IOException("a secondary is NA or the primary");
				out.put(job, r);
			} catch (IOException e) {
				if (where == null) log.warn("The station's own role for {} is broken: {}", job, e.getMessage());
				else homeplanet.core.Lore.problem(where + ", the role for " + job + ": " + e.getMessage() + "; the station's own is used");
			}
		}
		return out;
	}
	private static int skill(String name) throws IOException {
		int s = Expeditions.skillIndex(name.trim());
		if (s < 0) throw new IOException("no skill " + name.trim());
		return s;
	}

	/** What each race is best at, as FTL has it, counted half a level more when roles are handed out (pilot 0 ... combat 5). */
	static int raceSkill(String race) {
		if ("engi".equals(race)) return 4;
		if ("mantis".equals(race) || "anaerobic".equals(race)) return 5;
		if ("slug".equals(race)) return 0;
		if ("rock".equals(race)) return 3;
		if ("energy".equals(race)) return 1;
		if ("crystal".equals(race)) return 2;
		return -1;
	}
	/** How good they are at a skill for the roles: their level with its fraction (0 to 2), half a level more for their race's skill. */
	static double ability(CrewState c, int skill) {
		double a = Skills.points(c, skill) / (double) Math.max(1, Skills.interval(c, skill));
		return Math.min(2, a) + (raceSkill(race(c)) == skill ? 0.5 : 0);
	}
	/** Their best skill by ability (-1 when they have none at all). */
	static int bestSkill(CrewState c) {
		int best = -1;
		double top = 0;
		for (int s = 0; s < 6; s++) { double a = ability(c, s); if (a > top) { top = a; best = s; } }
		return best;
	}
	/**
	 * Hands out the job's roles, each to a crew member without one: the primary, a 75% chance for whoever is best at it,
	 * then 50% and 25% for the next best; each secondary 50% for the best of those whose own best skill it is, then 25% and
	 * 12.5%; a role nobody wins goes to someone at random. Anyone left gets one of the job's other skills at random (an
	 * "other" role), never an NA one. A job of race alone gives nobody a role.
	 */
	public static void assignRoles(String job, List<Fate> fates, Random rng) {
		Roles r = roles(job);
		if (r.primary < 0 && r.secondary.length == 0) return;
		List<Fate> free = new ArrayList<Fate>(fates);
		if (r.primary >= 0) give(free, r.primary, false, new double[] {0.75, 0.5, 0.25}, rng);
		for (int s : r.secondary) give(free, s, true, new double[] {0.5, 0.25, 0.125}, rng);
		List<Integer> others = r.others();
		if (others.isEmpty()) { for (int s = 0; s < 6; s++) if (!r.na[s]) others.add(s); } // every skill has a role: anyone left helps with one of them
		for (Fate f : free) { f.skill = others.get(rng.nextInt(others.size())); f.other = true; }
	}
	private static void give(List<Fate> free, final int skill, boolean ownBest, double[] chances, Random rng) {
		if (free.isEmpty()) return;
		List<Fate> suited = new ArrayList<Fate>();
		for (Fate f : free) if (ownBest ? bestSkill(f.crew) == skill : ability(f.crew, skill) > 0) suited.add(f);
		java.util.Collections.sort(suited, new java.util.Comparator<Fate>() {
			public int compare(Fate a, Fate b) { return Double.compare(ability(b.crew, skill), ability(a.crew, skill)); }
		});
		Fate got = null;
		for (int i = 0; i < suited.size() && i < chances.length && got == null; i++) if (rng.nextDouble() < chances[i]) got = suited.get(i);
		if (got == null) got = free.get(rng.nextInt(free.size()));
		got.skill = skill;
		free.remove(got);
	}

	// ---- the words ----

	private static Map<String, List<String>> words;
	private static long wordsStamp = -2;
	/**
	 * The words in force, by key ("band defend died", "event defend zoltan"), each key's lines in order (6.0 step 9a,
	 * 5.991: lore/expeditions.xml, assignments.txt before): the station's own from the jar, a key's lines replaced by the
	 * player's copy's lines for it, those that keep the rules and use only the {tokens} the station's own lines for that
	 * key do. Read again when the copy changes.
	 */
	private static synchronized Map<String, List<String>> words() {
		long stamp = homeplanet.core.Lore.stamp(homeplanet.core.Lore.EXPEDITIONS);
		if (words != null && stamp == wordsStamp) return words;
		Map<String, List<String>> out = new LinkedHashMap<String, List<String>>();
		byte[] jar = homeplanet.core.Lore.jarBytes(homeplanet.core.Lore.EXPEDITIONS);
		if (jar == null) log.warn("The expedition words (lore/{}) are missing from the program", homeplanet.core.Lore.EXPEDITIONS);
		else {
			try { out = lines(jar); }
			catch (IOException e) { log.warn("Could not read the expedition words: {}", e.toString()); }
		}
		java.io.File copy = homeplanet.core.Lore.copy(homeplanet.core.Lore.EXPEDITIONS);
		if (copy != null) {
			String where = "lore/" + homeplanet.core.Lore.EXPEDITIONS;
			try {
				Map<String, List<String>> theirs = lines(SafeFiles.read(copy));
				for (Map.Entry<String, List<String>> k : theirs.entrySet()) {
					java.util.Set<String> may = new java.util.HashSet<String>(java.util.Arrays.asList("he", "him", "his", "He", "His"));
					if (out.get(k.getKey()) != null) for (String l : out.get(k.getKey())) may.addAll(tokens(l));
					List<String> good = new ArrayList<String>();
					for (String l : k.getValue()) {
						String why = homeplanet.core.Lore.rule(l);
						if (why == null) for (String t : tokens(l)) if (!may.contains(t)) { why = "{" + t + "} isn't one of these lines'"; break; }
						if (why == null) good.add(l);
						else homeplanet.core.Lore.problem(where + ", " + k.getKey() + ": " + why + " (\"" + l + "\"); left out");
					}
					if (!good.isEmpty()) out.put(k.getKey(), good);
				}
			} catch (IOException e) {
				homeplanet.core.Lore.problem(where + " could not be read (" + e.getMessage() + "); the station's own words are used");
			}
		}
		wordsStamp = stamp;
		return words = out;
	}
	/** The start-up check: the words read once, so a broken copy is named in the debug log from the start. Never throws. */
	public static void loreCheck() {
		try { words(); } catch (RuntimeException e) { log.warn("The expedition words could not be checked: {}", e.toString()); }
	}
	/** The marks a line's key is made of, in the key's order (an offer's is its sector alone). */
	private static final String[] MARKS = {"job", "hazard", "band", "cause", "prize", "race", "captors", "skill", "sector"};
	/** The lines of an expeditions.xml by key, in order: each line's key made from its marks. */
	static Map<String, List<String>> lines(byte[] bytes) throws IOException {
		org.w3c.dom.Element root;
		try {
			javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
			f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			f.setExpandEntityReferences(false);
			javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
			b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
				@Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
			});
			root = b.parse(new java.io.ByteArrayInputStream(bytes)).getDocumentElement();
		} catch (Exception e) {
			throw new IOException("broken XML: " + e.getMessage(), e);
		}
		Map<String, List<String>> out = new LinkedHashMap<String, List<String>>();
		org.w3c.dom.NodeList nl = root.getElementsByTagName("line");
		for (int i = 0; i < nl.getLength(); i++) {
			org.w3c.dom.Element x = (org.w3c.dom.Element) nl.item(i);
			String kind = x.getAttribute("kind").trim(), text = x.getTextContent().trim();
			if (kind.isEmpty() || text.isEmpty()) continue;
			StringBuilder key = new StringBuilder(kind);
			for (String m : MARKS) {
				String v = x.getAttribute(m).trim();
				if (!v.isEmpty()) key.append(' ').append(m.equals("cause") ? "cause" : v);
			}
			String k = key.toString();
			if (!out.containsKey(k)) out.put(k, new ArrayList<String>());
			out.get(k).add(text);
		}
		return out;
	}
	private static final java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile("\\{([A-Za-z0-9_.]+)\\}");
	private static java.util.Set<String> tokens(String line) {
		java.util.Set<String> out = new java.util.HashSet<String>();
		java.util.regex.Matcher m = TOKEN.matcher(line);
		while (m.find()) out.add(m.group(1));
		return out;
	}
	/** One of the lines for this key, or the fallback ("\n" in the file is a line break: a long line broken where the sense breaks). */
	static String say(Random rng, String fallback, String... key) {
		List<String> l = words().get(String.join(" ", key));
		return (l == null || l.isEmpty() ? fallback : l.get(rng.nextInt(l.size()))).replace("\\n", "\n");
	}
	/**
	 * One of the lines for this key, the lines marked for this place joining them ("event defend zoltan | ..." is drawn
	 * with "event defend | ..." in Zoltan space), or the fallback.
	 */
	static String sayAt(Random rng, String fallback, String mark, String... key) {
		String k = String.join(" ", key);
		List<String> l = new ArrayList<String>();
		if (words().get(k) != null) l.addAll(words().get(k));
		if (mark != null && words().get(k + " " + mark) != null) l.addAll(words().get(k + " " + mark));
		return (l.isEmpty() ? fallback : l.get(rng.nextInt(l.size()))).replace("\\n", "\n"); // ("\n" in the file is a line break)
	}
	/** One of the lines under any of these keys together not used yet in this report (any of them, once all are), or the fallback. */
	static String fresh(Random rng, String fallback, java.util.Set<String> used, String... keys) {
		List<String> all = new ArrayList<String>();
		for (String k : keys) if (words().get(k) != null) all.addAll(words().get(k));
		List<String> left = new ArrayList<String>();
		for (String s : all) if (!used.contains(s.replace("\\n", "\n"))) left.add(s); // used holds lines as told, line breaks made
		List<String> l = left.isEmpty() ? all : left;
		return l.isEmpty() ? fallback : l.get(rng.nextInt(l.size())).replace("\\n", "\n");
	}
	/** One of the lines under any of these keys together, or the fallback. */
	static String pick(Random rng, String fallback, String... keys) {
		List<String> l = new ArrayList<String>();
		for (String k : keys) if (words().get(k) != null) l.addAll(words().get(k));
		return (l.isEmpty() ? fallback : l.get(rng.nextInt(l.size()))).replace("\\n", "\n"); // ("\n" in the file is a line break)
	}
	/** He or she for a crew member: {he}, {him}, {his} in a line after their name ({He}, {His} to begin a sentence). */
	static String pronouns(String line, CrewState c) {
		boolean m = c == null || c.isMale();
		return line.replace("{he}", m ? "he" : "she").replace("{him}", m ? "him" : "her").replace("{his}", m ? "his" : "her")
				.replace("{He}", m ? "He" : "She").replace("{His}", m ? "His" : "Her");
	}
	/** The keys that stand alone, besides each job's, hazard's and sector's. */
	private static final String[] OTHER_KEYS = {"frame", "hacked", "captured", "infirmary", "prize hijack ship", "prize hijack part", "prize salvage part", "prize rescue recruit"};
	/** Who holds a captive, as the captured lines are marked: slavers, pirates, rebels or mantis. */
	public static final String[] CAPTORS = {"slavers", "pirates", "rebels", "mantis"};
	static String captorsMark(String sector) {
		return "rebel".equals(sector) ? "rebels" : "mantis".equals(sector) ? "mantis" : "pirate".equals(sector) ? "pirates" : "slavers";
	}
	/** Lines whose key isn't one the report asks for, nor one marked for a real sector (or, captured, real captors) (for tests). */
	public static List<String> strayWords() {
		java.util.Set<String> base = new java.util.HashSet<String>(java.util.Arrays.asList(OTHER_KEYS));
		java.util.Set<String> sectors = new java.util.HashSet<String>();
		for (Object[] j : JOBS) { base.add("event " + j[0]); for (String b : BANDS) base.add("band " + j[0] + " " + b); }
		for (Object[] h : HAZARDS) base.add("hazard " + h[0]);
		for (String[] x : SECTORS) { base.add("offer " + x[0]); sectors.add(x[0]); }
		for (Object[] h : HAZARDS) for (String race : (String[]) h[3]) base.add("shrug " + h[0] + " " + race);
		for (Object[] j : JOBS) { base.add("band " + j[0] + " injured cause"); base.add("frame " + j[0]); }
		for (Object[] j : JOBS) for (int b = 2; b < BANDS.length; b++) for (String sk : Expeditions.SKILLS) base.add("band " + j[0] + " " + BANDS[b] + " " + sk);
		List<String> out = new ArrayList<String>();
		for (String k : words().keySet()) {
			if (base.contains(k)) continue;
			int sp = k.lastIndexOf(' ');
			String b = sp < 0 ? "" : k.substring(0, sp), m = sp < 0 ? "" : k.substring(sp + 1);
			boolean ok = base.contains(b) && !b.startsWith("offer ") && ("captured".equals(b) ? java.util.Arrays.asList(CAPTORS).contains(m) : sectors.contains(m));
			if (!ok) out.add(k);
		}
		return out;
	}
	/** Every line in the words file (for tests). */
	public static List<String> allWords() {
		List<String> out = new ArrayList<String>();
		for (List<String> l : words().values()) out.addAll(l);
		return out;
	}
	/** What's missing from the words file (for tests): every job's event and six bands, every hazard, the prizes. */
	public static List<String> missingWords() {
		List<String> out = new ArrayList<String>();
		for (Object[] j : JOBS) {
			if (!words().containsKey("event " + j[0])) out.add("event " + j[0]);
			for (String b : BANDS) if (!words().containsKey("band " + j[0] + " " + b)) out.add("band " + j[0] + " " + b);
		}
		for (Object[] h : HAZARDS) if (!words().containsKey("hazard " + h[0])) out.add("hazard " + h[0]);
		for (String[] s : SECTORS) if (!words().containsKey("offer " + s[0])) out.add("offer " + s[0]);
		for (String k : OTHER_KEYS) if (!words().containsKey(k)) out.add(k);
		for (Object[] h : HAZARDS) for (String race : (String[]) h[3]) if (!words().containsKey("shrug " + h[0] + " " + race)) out.add("shrug " + h[0] + " " + race);
		for (Object[] j : JOBS) if (!words().containsKey("frame " + j[0])) out.add("frame " + j[0]);
		for (Object[] j : JOBS) if (!"spiders".equals(j[0]) && !words().containsKey("band " + j[0] + " injured cause")) out.add("band " + j[0] + " injured cause"); // on Giant Spiders an injury is a death
		return out;
	}

	// ---- the board and who's away (expeditions/expeditions.xml in the fleet; assignments.txt at its root before 5.85) ----

	/** The expeditions' file: the sectors on offer, and the crew away on one. */
	public static File file(Vault v) { return new File(v.expeditionsDir(), "expeditions.xml"); }
	public static final String NOTE = "Crew expeditions: the sectors on offer, and the crew away on one";

	/** A sector on offer. */
	public static final class Offer {
		public final int slot;
		public final String sector, words;
		Offer(int slot, String sector, String words) { this.slot = slot; this.sector = sector; this.words = words; }
		public String title() { return sectorTitle(sector); }
	}
	/** A detail away: who went, where, and when they're due (the beacon count, hidden). */
	public static final class Away {
		public final int index;
		public final String sector;
		public final int sentAt, until;
		/** The roll's seed: the result was rolled at setting out, and is rolled again, the same, when they're back. */
		public final long seed;
		public final List<CrewState> crew;
		/** Whether the Anti-Ship Battery could come up when they set out (5.05 on; a detail from before rolls as it did, without it). */
		public final boolean battery;
		Away(int index, String sector, int sentAt, int until, long seed, List<CrewState> crew, boolean battery) { this.index = index; this.sector = sector; this.sentAt = sentAt; this.until = until; this.seed = seed; this.crew = crew; this.battery = battery; }
		/** What came of it (the same every time: the seed). */
		public Result result() { return roll(sector, crew, new Random(seed), battery); }
		public List<String> names() { List<String> n = new ArrayList<String>(); for (CrewState c : crew) n.add(c.getName()); return n; }
	}

	/** The three sectors on offer (drawn afresh where one is empty or has come down). */
	public static synchronized List<Offer> board(Vault v) {
		Properties p = read(v);
		int now = v.beaconsSeen();
		boolean changed = false;
		Random rng = new Random();
		for (int i = 0; i < OFFERS; i++) {
			String s = p.getProperty("offer." + i);
			if (s != null && now < Store.num(p, "offer." + i + ".until", 0)) continue;
			List<String> taken = new ArrayList<String>();
			for (int k = 0; k < OFFERS; k++) if (k != i && p.getProperty("offer." + k) != null) taken.add(p.getProperty("offer." + k));
			if (s != null) taken.add(s); // not the one that just came down or was taken
			String pick = drawSector(rng, taken);
			p.setProperty("offer." + i, pick);
			p.setProperty("offer." + i + ".words", say(rng, "Work on offer.", "offer", pick));
			p.setProperty("offer." + i + ".until", Integer.toString(now + OFFER_MIN + rng.nextInt(OFFER_MAX - OFFER_MIN + 1)));
			changed = true;
		}
		if (changed) try { write(v, p); } catch (IOException e) { log.warn("Could not keep the expedition board: {}", e.toString()); }
		List<Offer> out = new ArrayList<Offer>();
		for (int i = 0; i < OFFERS; i++) out.add(new Offer(i, p.getProperty("offer." + i), p.getProperty("offer." + i + ".words", "")));
		return out;
	}
	/** A sector not on the board already, by weight. */
	public static String drawSector(Random rng, List<String> taken) {
		List<String> ids = new ArrayList<String>();
		int total = 0;
		for (String[] s : SECTORS) {
			if (taken.contains(s[0])) continue;
			ids.add(s[0]); total += sectorWeight(s[0]);
		}
		int r = rng.nextInt(total);
		for (String id : ids) { r -= sectorWeight(id); if (r < 0) return id; }
		return ids.get(ids.size() - 1);
	}

	/** Who's away now. */
	public static synchronized List<Away> away(Vault v) { return away(read(v)); }
	private static List<Away> away(Properties p) {
		List<Away> out = new ArrayList<Away>();
		for (int i = 0; p.getProperty("away." + i + ".sector") != null; i++) {
			List<CrewState> crew = new ArrayList<CrewState>();
			for (int k = 0; p.getProperty("away." + i + ".crew." + k + ".name") != null; k++) {
				Map<String, String> f = new LinkedHashMap<String, String>();
				String pre = "away." + i + ".crew." + k + ".";
				for (String key : p.stringPropertyNames()) if (key.startsWith(pre)) f.put(key.substring(pre.length()), p.getProperty(key));
				try { crew.add(homeplanet.vault.CrewRecord.crew(f)); } catch (IOException e) { log.warn("A crew member away on an expedition can't be read: {}", e.toString()); }
			}
			long seed = 0;
			try { seed = Long.parseLong(p.getProperty("away." + i + ".seed", "0")); } catch (NumberFormatException e) { }
			out.add(new Away(i, p.getProperty("away." + i + ".sector"), Store.num(p, "away." + i + ".sentAt", 0), Store.num(p, "away." + i + ".until", 0), seed, crew,
					"true".equals(p.getProperty("away." + i + ".ae"))));
		}
		return out;
	}

	/** One in this many rescued recruits comes with a skill already (one, at level 1). */
	public static final int SKILLED_ONE_IN = 20;
	/** A rescued one who asks to sign on: a volunteer of a race the commander has unlocked, one in SKILLED_ONE_IN with a skill. */
	public static CrewState recruit(Random rng) {
		List<String> races = Expeditions.hireableRaces();
		CrewState c = Commission.volunteer(races.get(rng.nextInt(races.size())), rng);
		if (c != null && rng.nextInt(SKILLED_ONE_IN) == 0) { int k = rng.nextInt(6); Skills.set(c, k, Skills.interval(c, k)); }
		return c;
	}

	/** A prize waiting on the commander's word: a ship brought home (the Space Dock, the Junkyard, or not taken) or a rescued one asking to sign on. */
	public static final class Pending {
		public final int index;
		/** "ship" or "recruit". */
		public final String kind, name;
		public final CrewState crew;
		public final File save;
		/** The letter that carries the question (Immersive Notifications on), or null: then the Space Dock asks. */
		public final String letter;
		Pending(int index, String kind, String name, CrewState crew, File save, String letter) { this.index = index; this.kind = kind; this.name = name; this.crew = crew; this.save = save; this.letter = letter; }
		public String question() {
			return "recruit".equals(kind) ? name + " (" + homeplanet.model.Crew.raceTitle(crew) + "), rescued on an expedition, asks to sign on with your fleet.\nTake them into the Cargo Hold?"
					: "Your crew brought a ship home from an expedition: " + name + ".\nTake her in as she is? To the Space Dock to fly, to the Junkyard to be set right or scrapped, or not at all.";
		}
	}
	/** The prizes waiting on an answer. */
	public static synchronized List<Pending> pending(Vault v) { return pending(v, read(v)); }
	private static List<Pending> pending(Vault v, Properties p) {
		List<Pending> out = new ArrayList<Pending>();
		for (int i = 0; i < 100; i++) {
			String kind = p.getProperty("pending." + i + ".kind");
			if (kind == null) continue;
			CrewState c = null;
			if ("recruit".equals(kind)) {
				Map<String, String> f = new LinkedHashMap<String, String>();
				String pre = "pending." + i + ".crew.";
				for (String key : p.stringPropertyNames()) if (key.startsWith(pre)) f.put(key.substring(pre.length()), p.getProperty(key));
				try { c = homeplanet.vault.CrewRecord.crew(f); } catch (IOException e) { log.warn("A recruit waiting on an answer can't be read: {}", e.toString()); continue; }
			}
			out.add(new Pending(i, kind, p.getProperty("pending." + i + ".name", ""), c, "ship".equals(kind) ? new File(v.root, p.getProperty("pending." + i + ".file", "")) : null, p.getProperty("pending." + i + ".letter")));
		}
		return out;
	}
	/** The prizes the Space Dock asks about itself: those no letter carries. */
	public static List<Pending> pendingToAsk(Vault v) {
		List<Pending> out = new ArrayList<Pending>();
		for (Pending x : pending(v)) if (x.letter == null) out.add(x);
		return out;
	}
	/** The prize a letter carries, or null (answered, or none). */
	public static Pending pendingFor(Vault v, String letterKey) {
		for (Pending x : pending(v)) if (letterKey.equals(x.letter)) return x;
		return null;
	}
	/** Yes: the recruit into the Cargo Hold, or the ship to the Space Dock ({@code dock}) or the Junkyard, set out at the station as she is. */
	public static synchronized void accept(Vault v, Pending x, boolean dock) throws IOException {
		Properties p = readStrict(v);
		if (p.getProperty("pending." + x.index + ".kind") == null) throw new IOException("That was answered already");
		if ("recruit".equals(x.kind)) {
			Ship st = v.storage();
			Vault.Copy c = v.readCopy(st);
			if (!SaveHelper.placeCrew(c.save.getPlayerShip(), x.crew, true)) throw new IOException("The Cargo Hold has no room for " + x.name + "; make room and look again");
			c.save.getPlayerShip().getCrewList().add(x.crew);
			forget(p, x.index);
			v.begin().put(st, c.save, c.hash).put(file(v), bytes(p)).commit();
			HistoryLog.entry("HIRE", x.name + " (" + race(x.crew) + "), rescued on an expedition, signed on: in the Cargo Hold", null,
				Event.of("HIRE").put("how", "rescued").put("crew", x.name).put("race", race(x.crew)).put("to", "hold"));
			return;
		}
		SavedGameState gs = HomePlanet.savedGameParser.readSavedGame(x.save);
		Ship s = dock ? v.adopt(gs, "brought_home") : v.adoptJunked(gs, "brought_home");
		v.setOut(s, gs, "Brought home by an expedition");
		forget(p, x.index);
		write(v, p);
		x.save.delete();
		HistoryLog.entry("EXPEDITION", gs.getPlayerShipName() + " (" + gs.getPlayerShip().getShipBlueprintId() + "), brought home by an expedition, kept: " + (dock ? "at the Space Dock" : "in the Junkyard"), null,
				Vault.shipEvent("EXPEDITION", s).put("what", "prize_ship").put("ship_class", gs.getPlayerShipBlueprintId()).put("to", dock ? "ships" : "junkyard"));
	}
	/** No: the recruit goes their way, the ship is left where she lies. */
	public static synchronized void decline(Vault v, Pending x) throws IOException {
		Properties p = readStrict(v);
		forget(p, x.index);
		write(v, p);
		if (x.save != null) x.save.delete();
		HistoryLog.entry("EXPEDITION", "recruit".equals(x.kind) ? x.name + ", rescued on an expedition, was sent on their way" : x.name + ", brought home by an expedition, was not taken", null,
				Event.of("EXPEDITION").put("what", "recruit".equals(x.kind) ? "recruit_declined" : "prize_ship_declined").put("name", x.name));
	}
	private static void forget(Properties p, int index) {
		for (String key : new ArrayList<String>(p.stringPropertyNames())) if (key.startsWith("pending." + index + ".")) p.remove(key);
	}
	private static int keep(Properties p, String kind, String name) {
		int i = 0;
		while (p.getProperty("pending." + i + ".kind") != null) i++;
		p.setProperty("pending." + i + ".kind", kind);
		p.setProperty("pending." + i + ".name", name);
		return i;
	}

	/** The crew in the Cargo Hold who can be sent: not those in the infirmary. */
	public static List<CrewState> holdCrew(Vault v) throws IOException { return Expeditions.holdCrew(v); }

	/**
	 * Sends a detail to the sector on offer in this slot: they leave the Cargo Hold's save for the fleet's assignments
	 * file, due back in AWAY_MIN to AWAY_MAX beacons (and the job's extras); setting out passes no time; the offer is replaced.
	 */
	/**
	 * The hold's own record of a crew member picked from a list read on its own (never the same objects): the same object,
	 * else the same whole record (name, race, skills, service, looks, health), else the same name and race; never one
	 * already picked. Namesakes can't be avoided: by name alone two picked were one person sent twice, the other left
	 * behind, and the fleet a crew member richer when they came home (5.33).
	 */
	static CrewState picked(List<CrewState> hold, CrewState sent, List<CrewState> taken) {
		for (CrewState x : hold) if (x == sent && !among(taken, x)) return x;
		Map<String, String> whole = sent.getRace() == null ? null : homeplanet.vault.CrewRecord.of(sent);
		if (whole != null) for (CrewState x : hold) if (!among(taken, x) && x.getRace() != null && homeplanet.vault.CrewRecord.of(x).equals(whole)) return x;
		for (CrewState x : hold) if (!among(taken, x) && x.getName().equals(sent.getName()) && x.getRace() == sent.getRace()) return x;
		return null;
	}
	private static boolean among(List<CrewState> l, CrewState c) { for (CrewState x : l) if (x == c) return true; return false; }

	public static synchronized void send(Vault v, int slot, List<CrewState> party, Random rng) throws IOException {
		if (party.isEmpty() || party.size() > PARTY_MAX) throw new IOException("A detail is one to " + PARTY_MAX + " crew");
		Properties p = readStrict(v);
		String sector = p.getProperty("offer." + slot);
		if (sector == null) throw new IOException("That offer is no longer on the board");
		Ship st = v.storage();
		Vault.Copy c = v.readCopy(st);
		ShipState hold = c.save.getPlayerShip();
		int i = 0;
		while (p.getProperty("away." + i + ".sector") != null) i++;
		int now = v.beaconsSeen() + 1; // the beacon the setting out takes
		List<CrewState> going = new ArrayList<CrewState>();
		for (CrewState sent : party) {
			CrewState mine = picked(hold.getCrewList(), sent, going);
			if (mine == null) throw new IOException(sent.getName() + " is not in the Cargo Hold; nothing was changed");
			going.add(mine);
		}
		// rolled now, so the delay knows how it went; the record keeps the seed and the crew as they left, so it rolls the same when they're back
		long seed = rng.nextLong();
		List<CrewState> asLeft = new ArrayList<CrewState>();
		int k = 0;
		for (CrewState mine : going) {
			hold.getCrewList().remove(mine);
			Map<String, String> f = homeplanet.vault.CrewRecord.of(mine);
			for (Map.Entry<String, String> e : f.entrySet()) p.setProperty("away." + i + ".crew." + k + "." + e.getKey(), e.getValue());
			asLeft.add(homeplanet.vault.CrewRecord.crew(f));
			k++;
		}
		Result r = roll(sector, asLeft, new Random(seed));
		p.setProperty("away." + i + ".sector", sector);
		p.setProperty("away." + i + ".ae", "true"); // the battery can come up (the key's name is from 5.05)
		p.setProperty("away." + i + ".sentAt", Integer.toString(now));
		p.setProperty("away." + i + ".seed", Long.toString(seed));
		p.setProperty("away." + i + ".until", Integer.toString(now + days(r, asLeft, rng)));
		p.setProperty("offer." + slot + ".until", "0"); p.remove("offer." + slot + ".words"); // comes down now: redrawn, not the same sector, at the next look
		v.begin().put(st, c.save, c.hash).put(file(v), bytes(p)).commit();
		List<String> names = new ArrayList<String>();
		Event sent = Event.of("EXPEDITION").put("what", "sent").put("sector", sectorTitle(sector)).put("sector_id", sector).put("party", i);
		for (CrewState x : party) { names.add(x.getName()); sent.put("crew", x.getName()).put("race", race(x)); }
		HistoryLog.entry("EXPEDITION", String.join(", ", names) + " sent to " + sectorTitle(sector), null, sent);
	}

	/**
	 * How long they're away: AWAY_MIN to AWAY_MAX days, then a day more for a nebula (half the time), Abandoned or Crystal
	 * space (always), a Mantis sector (half the time); Got Lost a day, and a day for every failed roll on it; on any other
	 * job a failed roll a day half the time; each item found a day, a part or a recruit one more, a ship two; each Rock
	 * one time in three; a Scout a day less; never under 1 nor over AWAY_CAP. Nothing the report says.
	 */
	public static int days(Result r, List<CrewState> party, Random rng) {
		int d = AWAY_MIN + rng.nextInt(AWAY_MAX - AWAY_MIN + 1);
		if ("nebula".equals(r.sector) && rng.nextBoolean()) d++;
		if ("abandoned".equals(r.sector) || "crystal".equals(r.sector)) d++;
		if ("mantis".equals(r.sector) && rng.nextBoolean()) d++;
		boolean lost = "lost".equals(r.job);
		if (lost) d++;
		for (Fate f : r.fates) {
			if (f.band == 2 && (lost || rng.nextBoolean())) d++;
			if (f.item != null) d++;
		}
		if ("ship".equals(r.prize)) d += 2;
		else if (r.prize != null) d++;
		for (CrewState c : party) if ("rock".equals(race(c)) && rng.nextInt(3) == 0) d++;
		if ("scout".equals(r.job)) d--;
		return Math.max(1, Math.min(AWAY_CAP, d));
	}

	// ---- the roll ----

	/** One crew member's outcome. */
	public static final class Fate {
		public final CrewState crew;
		public int roll, band;
		/** The d20 as it fell (after the race's reroll), before their role's skill (5.43; the job's one skill before 6.39). */
		public int natural;
		public boolean rerolled, died, captured, infirmary;
		/** Sent hurt and hurt again, and it wasn't worse: half of what they had. */
		public boolean worn;
		/** The item a 20 found (an id, or "fuel:3", "missiles:2", "parts:2"), or null. */
		public String item;
		/** Their role's skill (-1: none, a job of race alone), and whether it's one of the job's "other" skills (6.39). */
		public int skill = -1;
		public boolean other;
		public Fate(CrewState c) { crew = c; }
		public String name() { return crew.getName(); }
	}
	/** What came of an expedition. */
	public static final class Result {
		public String sector, job, hazard;
		/** The Engi who hacked the Anti-Ship Battery (its hazard then costs nobody anything), or null. */
		public CrewState hacker;
		public final List<Fate> fates = new ArrayList<Fate>();
		public int base, multiplier, scrap;
		/** "ship", "part" or "recruit", or null. */
		public String prize;
		public String prizeDetail;
		public CrewState recruit;
		public String report;
		/** The words' own seed: the report reads the same whenever it's told again. */
		public long seed;
		public List<Fate> dead() { List<Fate> l = new ArrayList<Fate>(); for (Fate f : fates) if (f.died) l.add(f); return l; }
	}

	/** The job the sector gives, by its weights. */
	static String drawJob(String sector, Random rng) {
		int total = 0;
		for (Object[] j : JOBS) total += jobWeight(sector, (String) j[0]);
		int r = rng.nextInt(Math.max(1, total));
		for (Object[] j : JOBS) { r -= jobWeight(sector, (String) j[0]); if (r < 0) return (String) j[0]; }
		return (String) JOBS[0][0];
	}
	/** A hazard, one time in HAZARD_ONE_IN (a plasma storm only in a nebula, the Anti-Ship Battery only in rebel space), or null. */
	static String drawHazard(String sector, boolean battery, Random rng) {
		if (rng.nextInt(HAZARD_ONE_IN) != 0) return null;
		List<String> fit = new ArrayList<String>();
		for (Object[] h : HAZARDS) if ((h[2] == null || h[2].equals(sector)) && (battery || !"battery".equals(h[0]))) fit.add((String) h[0]);
		return fit.get(rng.nextInt(fit.size()));
	}
	static String race(CrewState c) { return c.getRace() == null ? "human" : c.getRace().getId(); }
	/** Whether this crew member was sent hurt (their own bonuses count half). */
	static boolean hurt(CrewState c) { return c.getRace() != null && c.getHealth() < c.getRace().getMaxHealth(); }

	/**
	 * Rolls an expedition for this detail in this sector (nothing is written): the job, a hazard, each one's d20 with
	 * its reroll, the pot, the items and the prize, and the report in words.
	 */
	public static Result roll(String sector, List<CrewState> party, Random rng) { return roll(sector, party, rng, true); }
	/** The same, with or without the Anti-Ship Battery (a detail sent before 5.05 was rolled without it). */
	/**
	 * Their role's skill on the d20 (heromedel, 5.43; the job's one skill before 6.39): +2 a level over none, +3 a level when their race suits the job too;
	 * a natural 1 or 20 stays as it fell, and anything else stays between 2 and 19, so only a natural 1 is death and only
	 * a natural 20 the top (and its find). The skill's +10% of the pot a level stays as well.
	 */
	public static int skilled(int natural, int level, boolean raceSuits) {
		if (natural <= 1 || natural >= 20 || level <= 0) return natural;
		return Math.max(2, Math.min(19, natural + level * (raceSuits ? 3 : 2)));
	}
	public static Result roll(String sector, List<CrewState> party, Random rng, boolean battery) {
		Result r = new Result();
		r.seed = rng.nextLong();
		r.sector = sector;
		r.job = drawJob(sector, rng);
		r.hazard = drawHazard(sector, battery, rng);
		if ("battery".equals(r.hazard)) { // each Engi a chance in BATTERY_HACK of hacking it, for everyone
			int engi = 0;
			for (CrewState c : party) if ("engi".equals(race(c))) { engi++; if (r.hacker == null) r.hacker = c; }
			if (engi == 0 || rng.nextInt(BATTERY_HACK) >= engi) r.hacker = null;
		}
		int mods = PER_HEAD * party.size(), best = 0;
		for (CrewState c : party) r.fates.add(new Fate(c));
		assignRoles(r.job, r.fates, rng); // each their own role's skill (6.39), not the job's one skill for everyone
		for (Fate f : r.fates) {
			CrewState c = f.crew;
			int skill = f.skill;
			String race = race(c);
			int sec = raceSector(race, sector), job = raceJob(race, r.job);
			f.roll = 1 + rng.nextInt(20);
			if (f.roll <= 9) { // one reroll, when the sector or the job suits them (one good and one bad cancel out)
				int good = (sec > 0 ? 1 : 0) + (job > 0 ? 1 : 0), bad = (sec < 0 ? 1 : 0) + (job < 0 ? 1 : 0), chance = Math.max(0, good - bad);
				if (chance > 0 && rng.nextInt(4) < chance) { f.roll = 1 + rng.nextInt(20); f.rerolled = true; }
			}
			f.natural = f.roll;
			f.roll = skilled(f.natural, skill < 0 ? 0 : homeplanet.model.Crew.skillLevels(c)[skill], job > 0);
			f.band = band(f.roll);
			best = Math.max(best, f.roll);
			int own = sec + job + (skill < 0 ? 0 : 10 * homeplanet.model.Crew.skillLevels(c)[skill]);
			if (hurt(c) && own > 0) own /= 2;
			int m = BAND_MOD[f.band] + own;
			if (r.hazard != null && r.hacker == null && !cancels(r.hazard, race)) m -= 10;
			mods += m;
			if (f.band == 0) f.died = true;
			else if (f.band == 1) {
				if ("spiders".equals(r.job)) f.died = true;
				else if ("boarded".equals(r.job) && rng.nextBoolean()) f.captured = true;
				else if (sec < 0 && job < 0) f.infirmary = true;
				if (!f.died && !f.captured && hurt(c)) { // hurt twice (heromedel): sent wounded and wounded again, a third each: dead, the infirmary, or half what they had
					int d = rng.nextInt(3); // a third each
					if (d == 0) { f.died = true; f.infirmary = false; }
					else if (d == 1) f.infirmary = true;
					else if (!f.infirmary) f.worn = true;
				}
			}
		}
		r.base = 0;
		for (int i = 0; i < BASE_DICE; i++) r.base += 1 + rng.nextInt(BASE_DIE);
		r.multiplier = 100 + mods;
		r.scrap = Math.max(1, (int) Math.round(r.base * r.multiplier / 100.0));
		for (Fate f : r.fates) if (f.roll == 20 && 1 + rng.nextInt(20) >= 10) f.item = findItem(r.scrap * 2, rng);
		boolean wentWell = r.dead().isEmpty() && best >= 16;
		if (wentWell && ("hijack".equals(r.job) || "salvage".equals(r.job) || "rescue".equals(r.job))) {
			int d = 1 + rng.nextInt(20);
			if (d == 20) r.prize = "hijack".equals(r.job) ? "ship" : "salvage".equals(r.job) ? "part" : "recruit";
			else if (d >= 15 && "hijack".equals(r.job)) r.prize = "part";
		}
		r.report = report(r, new Random(r.seed));
		return r;
	}
	/** An item worth up to this much: the dearest kind that fits (a weapon, drone or augment FTL's stores sell; else supplies), or null. */
	static String findItem(int cap, Random rng) {
		List<String> gear = new ArrayList<String>();
		for (net.blerf.ftl.xml.WeaponBlueprint w : DataManager.get().getWeapons().values()) if (w.getCost() > 0 && w.getCost() <= cap && w.getRarity() > 0 && !w.getId().startsWith("ARTILLERY")) gear.add(w.getId());
		for (net.blerf.ftl.xml.DroneBlueprint d : DataManager.get().getDrones().values()) if (d.getCost() > 0 && d.getCost() <= cap && d.getRarity() > 0) gear.add(d.getId());
		for (net.blerf.ftl.xml.AugBlueprint a : DataManager.get().getAugments().values()) if (a.getCost() > 0 && a.getCost() <= cap && a.getRarity() > 0) gear.add(a.getId());
		Collections.sort(gear);
		if (!gear.isEmpty() && rng.nextInt(3) > 0) return gear.get(rng.nextInt(gear.size()));
		int k = rng.nextInt(3);
		int price = k == 0 ? Pricing.FUEL : k == 1 ? Pricing.MISSILE : Pricing.DRONE_PART;
		int n = Math.max(1, Math.min(6, cap / price));
		return (k == 0 ? "fuel" : k == 1 ? "missiles" : "parts") + ":" + n;
	}
	/** An item in words: its title, or "3 fuel". */
	public static String itemWords(String item) {
		if (item == null) return "";
		int colon = item.indexOf(':');
		if (colon < 0) return homeplanet.model.Items.title(item);
		String kind = item.substring(0, colon), n = item.substring(colon + 1);
		return n + " " + ("parts".equals(kind) ? "drone parts" : kind);
	}
	private static String aOrAn(String s) { return homeplanet.model.Words.a(s); }

	/** The report, as heromedel laid it out: the frame, the job line, a hazard, a line per crew member, the prize, the total. Never a roll. */
	/** heromedel's frame, the first setup line: one of the general ones, and the fallback for every other. */
	static final String FRAME = "Due to events during the assignment the crew";
	static String report(Result r, Random rng) {
		StringBuilder sb = new StringBuilder("-- Expedition Report --\n");
		sb.append("Sector: ").append(sectorTitle(r.sector)).append("\n\n");
		// the setup: half the time one of the job's own (and its sector's), else one of the general ones (heromedel's among them)
		String frame = rng.nextBoolean() ? sayAt(rng, FRAME, r.sector, "frame", r.job) : say(rng, FRAME, "frame");
		sb.append(frame).append("\n").append(sayAt(rng, "took on a job", r.sector, "event", r.job)).append(".\n");
		if (r.hazard != null) sb.append(sayAt(rng, "The weather was against them.", r.sector, "hazard", r.hazard)).append("\n");
		if (r.hacker != null) sb.append(sayAt(rng, "{name} hacked it.", r.sector, "hacked").replace("{name}", r.hacker.getName())).append("\n");
		sb.append("\n");
		java.util.Set<String> used = new java.util.HashSet<String>(); // two crew members with the same outcome get different lines where there are any
		for (Fate f : r.fates) {
			String line;
			String race = f.crew.getRace() == null ? null : f.crew.getRace().getId();
			boolean shrugged = r.hazard != null && race != null && !f.died && !f.captured && !f.infirmary && cancels(r.hazard, race);
			boolean hurt = !f.died && BANDS[f.band].equals("injured");
			// a role's own line (6.40, heromedel): a whole sentence with the name where it falls, the stars before it
			String role = f.captured || f.infirmary || f.died || hurt || f.skill < 0 ? null
					: fresh(rng, null, used, "band " + r.job + " " + BANDS[f.band] + " " + Expeditions.SKILLS[f.skill], "band " + r.job + " " + BANDS[f.band] + " " + Expeditions.SKILLS[f.skill] + " " + r.sector);
			String stars = f.captured || f.infirmary || f.died || hurt ? "" : STARS[f.band];
			if (role != null) {
				used.add(role);
				String said = role.replace("{name}", f.name());
				if (f.item != null) said += " {He} also brought back " + (f.item.indexOf(':') < 0 ? aOrAn(itemWords(f.item)) : itemWords(f.item)) + ".";
				if (shrugged) said += " " + sayAt(rng, "", r.sector, "shrug", r.hazard, race);
				sb.append(stars).append(pronouns(said, f.crew).trim()).append("\n");
				continue;
			}
			if (f.captured) line = sayAt(rng, "was taken by the boarders.", captorsMark(r.sector), "captured");
			else if (f.infirmary) line = sayAt(rng, "was badly hurt and is in the infirmary.", r.sector, "infirmary");
			else {
				String fallback = f.died ? "was killed" : BANDS[f.band].equals("top") ? "was extremely successful" : "was " + BANDS[f.band];
				// an injury trumps a hazard shrugged off: its line names a cause that isn't the hazard, and nothing follows it
				if (hurt && shrugged) line = fresh(rng, fallback, used, "band " + r.job + " injured cause");
				else if (hurt) line = fresh(rng, fallback, used, "band " + r.job + " injured", "band " + r.job + " injured cause", "band " + r.job + " injured " + r.sector);
				else { String b = f.died ? "died" : BANDS[f.band]; line = fresh(rng, fallback, used, "band " + r.job + " " + b, "band " + r.job + " " + b + " " + r.sector); } // a wound that killed reads as a death
				used.add(line);
				if (f.item != null) line += (line.contains("brought back") ? ", " : " and brought back ") + (f.item.indexOf(':') < 0 ? aOrAn(itemWords(f.item)) : itemWords(f.item));
				line += ".";
			}
			// a race that shrugged off the hazard says so, a sentence of its own (never for the dead, the taken, the infirmary or the injured)
			if (shrugged && !hurt) line += " " + sayAt(rng, "", r.sector, "shrug", r.hazard, race);
			sb.append(stars).append(f.name()).append(" ").append(pronouns(line, f.crew).trim()).append("\n");
		}
		if (r.prize != null) sb.append("\n").append(sayAt(rng, "They brought something back.", r.sector, "prize", r.job, r.prize).replace("{name}", r.prizeDetail == null ? "" : r.prizeDetail)).append("\n"); // the prize stands apart
		sb.append("\nTotal Reward: ").append(r.scrap).append(" scrap");
		return sb.toString();
	}

	// ---- back home ----

	/** A detail back: the report, and whether anything went to the Junkyard or the stored systems. */
	public static final class Report {
		public final String sector, text;
		public final List<String> names;
		/** Each crew member as they came home, for the report's faces. */
		public final List<Face> faces;
		public Report(String sector, String text, List<String> names) { this(sector, text, names, new ArrayList<Face>()); }
		public Report(String sector, String text, List<String> names, List<Face> faces) { this.sector = sector; this.text = text; this.names = names; this.faces = faces; }
		public String title() { return "Back from " + ("nebula".equals(sector) ? "the nebula" : "the " + sectorTitle(sector)); }
	}

	/** A crew member as they came home, drawn beside their line in the report: "dead", "taken", "infirmary", or "" (back in the Cargo Hold, at their health now). */
	public static final class Face {
		public final CrewState crew;
		public final String state;
		public Face(CrewState crew, String state) { this.crew = crew; this.state = state; }
	}
	/** How many reports keep their faces for the inbox's letters (the oldest go first). */
	static final int FACES_KEPT = 40;
	/** Keeps a report's faces under its letter's key, newest first, at most FACES_KEPT. */
	private static void keepFaces(Properties q, String letter, List<Face> faces) {
		Properties old = new Properties();
		for (String k : q.stringPropertyNames()) if (k.startsWith("face.")) old.setProperty(k, q.getProperty(k));
		for (String k : old.stringPropertyNames()) q.remove(k);
		q.setProperty("face.0.letter", letter);
		for (int i = 0; i < faces.size(); i++) {
			Face f = faces.get(i);
			q.setProperty("face.0." + i + ".state", f.state);
			for (Map.Entry<String, String> e : homeplanet.vault.CrewRecord.of(f.crew).entrySet()) q.setProperty("face.0." + i + ".crew." + e.getKey(), e.getValue());
		}
		for (int n = 0; n + 1 < FACES_KEPT && old.getProperty("face." + n + ".letter") != null; n++) {
			String pre = "face." + n + ".";
			for (String k : old.stringPropertyNames()) if (k.startsWith(pre)) q.setProperty("face." + (n + 1) + "." + k.substring(pre.length()), old.getProperty(k));
		}
	}
	/** The faces kept for this letter (an expedition report's), or none. */
	public static synchronized List<Face> facesFor(Vault v, String letter) {
		List<Face> out = new ArrayList<Face>();
		Properties p = read(v);
		for (int n = 0; p.getProperty("face." + n + ".letter") != null; n++) {
			if (!letter.equals(p.getProperty("face." + n + ".letter"))) continue;
			for (int i = 0; p.getProperty("face." + n + "." + i + ".state") != null; i++) {
				Map<String, String> fields = new LinkedHashMap<String, String>();
				String pre = "face." + n + "." + i + ".crew.";
				for (String k : p.stringPropertyNames()) if (k.startsWith(pre)) fields.put(k.substring(pre.length()), p.getProperty(k));
				try { out.add(new Face(homeplanet.vault.CrewRecord.crew(fields), p.getProperty("face." + n + "." + i + ".state"))); }
				catch (Exception e) { log.debug("A face in an expedition report can't be read: {}", e.toString()); }
			}
			break;
		}
		return out;
	}

	/** The details whose time is up, each rolled and brought home: the hold, the infirmary, the captives, the Junkyard and the stored systems written. */
	public static synchronized List<Report> checkReturns(Vault v) {
		List<Report> out = new ArrayList<Report>();
		if (!Vault.isOpen()) return out;
		int now = v.beaconsSeen();
		// one at a time, the list read afresh each time: bringing one home renumbers the rest (5.16: with the numbers
		// read once, a second detail due on the same look came home twice and another's record was struck off)
		java.util.Set<String> tried = new java.util.HashSet<String>();
		while (true) {
			Away due = null;
			for (Away a : away(read(v))) if (now >= a.until && !tried.contains(key(a))) { due = a; break; }
			if (due == null) break;
			tried.add(key(due));
			try { out.add(bringHome(v, due, due.result())); }
			catch (Exception e) { log.warn("A detail could not be brought home: {}", e.toString()); }
		}
		return out;
	}
	/** Its report's key: by its lasting name (5.20), never its number, which two details sent on one day could share in turn. */
	public static String letterKey(Away a) { return "expedition:" + key(a); }
	/** A detail's lasting name (its number changes as others come home): when it set out, its seed, and who went. */
	static String key(Away a) { return a.sentAt + ":" + a.seed + ":" + String.join(",", a.names()); }
	/** Brings a detail home with this result (for the station's round, and tests). */
	public static synchronized Report bringHome(Vault v, Away a, Result r) throws IOException {
		Properties p = readStrict(v);
		Away here = null; // the record as the file has it now, found by its lasting name, never by a number read earlier
		for (Away x : away(p)) if (key(x).equals(key(a))) { here = x; break; }
		if (here == null) throw new IOException(String.join(", ", a.names()) + " are no longer away; nothing was changed");
		a = here;
		Ship st = v.storage();
		Vault.Copy c = v.readCopy(st);
		ShipState hold = c.save.getPlayerShip();
		// what they brought back comes with their letter, to claim like anything else shipped home (heromedel, 6.02); with no
		// inbox there's no letter, and it goes straight into the Cargo Hold as before
		boolean byLetter = HomePlanet.immersiveNotifications();
		List<String> brought = new ArrayList<String>();
		if (!byLetter) hold.setScrapAmt(hold.getScrapAmt() + r.scrap);
		else if (r.scrap > 0) brought.add("scrap " + r.scrap);
		List<CrewState> hurt = new ArrayList<CrewState>(), taken = new ArrayList<CrewState>();
		for (Fate f : r.fates) {
			CrewState m = f.crew;
			if (f.died) continue;
			if (f.captured) { taken.add(m); continue; }
			int max = m.getRace() == null ? 100 : m.getRace().getMaxHealth();
			if (f.infirmary) { m.setHealth(Math.max(1, Math.min(m.getHealth(), max / 4))); hurt.add(m); }
			else if (f.worn) m.setHealth(Math.max(1, m.getHealth() / 2));
			else if (f.band == 1) m.setHealth(Math.max(1, Math.min(m.getHealth(), max / 2)));
			int earned = training(f.skill, f.band, f.other);
			if (earned > 0) Skills.add(m, f.skill, earned);
			if (!SaveHelper.placeCrew(hold, m, true)) throw new IOException("The Cargo Hold has no room for " + m.getName() + "; the detail waits");
			hold.getCrewList().add(m);
			if (f.item != null) { if (byLetter) brought.add(reward(f.item)); else give(hold, f.item); }
		}
		List<String> stored = new ArrayList<String>();
		File prizeFile = null;
		byte[] prizeBytes = null;
		int pendingIndex = -1; // the prize's question, if one waits
		if ("ship".equals(r.prize)) { // built now, kept for the commander's answer; if she can't be, a part comes home instead
			try {
				SavedGameState gs = Derelicts.prizeShip(v, new Random());
				prizeBytes = SaveHelper.toBytes(gs);
				File dir = new File(v.root, "assignments");
				if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
				// by the detail's lasting name, never its number (5.20): two sent on one day, one renumbered, shared a file; and never over another prize
				String base = "prize-" + a.sentAt + "-" + Integer.toHexString(key(a).hashCode());
				prizeFile = new File(dir, base + ".sav");
				for (int n = 2; prizeFile.exists(); n++) prizeFile = new File(dir, base + "-" + n + ".sav");
				net.blerf.ftl.xml.ShipBlueprint bp = DataManager.get().getShip(gs.getPlayerShip().getShipBlueprintId());
				r.prizeDetail = gs.getPlayerShipName() + (bp == null ? "" : " (" + bp.getName() + ")");
				pendingIndex = keep(p, "ship", r.prizeDetail);
				p.setProperty("pending." + pendingIndex + ".file", "assignments/" + prizeFile.getName());
			} catch (Exception e) { log.warn("The hijacked ship could not be built: {}", e.toString()); r.prize = "part"; }
		}
		if ("part".equals(r.prize)) {
			String id = partId();
			r.prizeDetail = homeplanet.model.Items.systemTitle(id);
			if (byLetter) brought.add("system " + homeplanet.vault.StoredSystems.line(id, 1, 1));
			else part(stored, v, id);
		}
		if ("recruit".equals(r.prize)) {
			CrewState n = recruit(new Random());
			if (n == null) r.prize = null;
			else {
				r.recruit = n; r.prizeDetail = n.getName() + " (" + homeplanet.model.Crew.raceTitle(n) + ")";
				pendingIndex = keep(p, "recruit", n.getName());
				for (Map.Entry<String, String> e : homeplanet.vault.CrewRecord.of(n).entrySet()) p.setProperty("pending." + pendingIndex + ".crew." + e.getKey(), e.getValue());
			}
		}
		String letter = letterKey(a);
		List<Face> faces = new ArrayList<Face>(); // as they came home: the health they're at now
		for (Fate f : r.fates) faces.add(new Face(f.crew, f.died ? "dead" : f.captured ? "taken" : f.infirmary ? "infirmary" : ""));
		if (HomePlanet.immersiveNotifications() && pendingIndex >= 0) p.setProperty("pending." + pendingIndex + ".letter", letter); // the letter asks, with buttons
		Vault.Transaction tx = v.begin().put(st, c.save, c.hash);
		if (prizeFile != null) tx.put(prizeFile, prizeBytes);
		Expeditions.admitAndTake(tx, v, hurt, taken, sectorCaptors(r.sector), v.beaconsSeen(), new Random());
		homeplanet.vault.StoredSystems.add(tx, v, stored);
		// the away record goes, and the rest are renumbered without a gap
		Properties q = new Properties();
		int n = 0;
		for (int i = 0; i < 100; i++) {
			if (i == a.index || p.getProperty("away." + i + ".sector") == null) continue;
			for (String key : p.stringPropertyNames()) if (key.startsWith("away." + i + ".")) q.setProperty("away." + n + key.substring(("away." + i).length()), p.getProperty(key));
			n++;
		}
		for (String key : p.stringPropertyNames()) if (!key.startsWith("away.")) q.setProperty(key, p.getProperty(key));
		keepFaces(q, letter, faces);
		tx.put(file(v), bytes(q)).commit();
		String text = r.report = report(r, new Random(r.seed)); // told again now everything is settled (the prize, the recruit's name), in the same words
		// the reputation, scored as the game's events are: the scrap, the dead, how it went
		int good = 0, bad = 0;
		int takenCount = 0;
		for (Fate f : r.fates) { if (f.band >= 3) good++; else bad++; if (f.captured) takenCount++; }
		homeplanet.vault.Reputation.expedition(v, sectorTitle(r.sector) + ", " + jobTitle(r.job), r.scrap, r.dead().size(), takenCount, bad == 0 ? 1 : good == 0 ? -1 : 0);
		List<String> dead = new ArrayList<String>();
		for (Fate f : r.dead()) dead.add(f.name());
		Event back = Event.of("EXPEDITION").put("what", "back").put("sector", sectorTitle(r.sector)).put("sector_id", r.sector).put("job", jobTitle(r.job)).put("job_id", r.job)
				.put("scrap", r.scrap).put("prize", r.prize).put("prize_detail", r.prizeDetail).put("captured", takenCount).put("good", good).put("bad", bad);
		for (String x : a.names()) back.put("crew", x);
		for (String x : dead) back.put("killed", x);
		Roles jr = roles(r.job);
		for (Fate f : r.fates) { // each one's role and what it taught (6.39): name:skill:kind:band:points
			if (f.skill < 0) continue;
			String kind = f.other ? "other" : f.skill == jr.primary ? "primary" : "secondary";
			back.put("role", f.name() + ":" + Expeditions.SKILLS[f.skill] + ":" + kind + ":" + BANDS[f.band] + ":" + (f.died ? 0 : training(f.skill, f.band, f.other)));
		}
		HistoryLog.entry("EXPEDITION", String.join(", ", a.names()) + " back from " + sectorTitle(r.sector) + " (" + jobTitle(r.job) + "): " + r.scrap + " scrap"
				+ (r.prize == null ? "" : "; " + r.prize + (r.prizeDetail == null ? "" : " " + r.prizeDetail)) + (dead.isEmpty() ? "" : "; killed: " + String.join(", ", dead))
				+ fatesNamed(r, true) + fatesNamed(r, false), null, back);
		if (byLetter) Transmissions.deliver(letter, "Expedition Command", "Back from " + sectorTitle(r.sector), text, String.join(", ", brought));
		return new Report(r.sector, text, a.names(), faces);
	}
	/** "; taken: …" (captive) or "; to the infirmary: …" for the station log, or nothing. */
	private static String fatesNamed(Result r, boolean taken) {
		List<String> n = new ArrayList<String>();
		for (Fate f : r.fates) if (taken ? f.captured : f.infirmary && !f.died) n.add(f.name());
		return n.isEmpty() ? "" : (taken ? "; taken: " : "; to the infirmary: ") + String.join(", ", n);
	}
	/** Who holds a captive taken in this sector. */
	static String sectorCaptors(String sector) {
		return "rebel".equals(sector) ? "the rebels" : "mantis".equals(sector) ? "a Mantis clan" : "pirate".equals(sector) ? "pirates" : "slavers";
	}
	/** A crew member's find as a letter's reward: "item ID", or "fuel N" / "missiles N" / "parts N". */
	private static String reward(String item) {
		int colon = item.indexOf(':');
		if (colon < 0) return "item " + item;
		String kind = item.substring(0, colon), n = item.substring(colon + 1);
		return ("fuel".equals(kind) || "missiles".equals(kind) ? kind : "parts") + " " + n;
	}
	private static void give(ShipState hold, String item) {
		int colon = item.indexOf(':');
		if (colon < 0) {
			if (homeplanet.model.Items.isWeapon(item)) hold.getWeaponList().add(SaveHelper.newIdleWeapon(item));
			else if (homeplanet.model.Items.isDrone(item)) hold.getDroneList().add(SaveHelper.newIdleDrone(item));
			else hold.getAugmentIdList().add(item);
			return;
		}
		String kind = item.substring(0, colon);
		int n = Integer.parseInt(item.substring(colon + 1));
		if ("fuel".equals(kind)) hold.setFuelAmt(hold.getFuelAmt() + n);
		else if ("missiles".equals(kind)) hold.setMissilesAmt(hold.getMissilesAmt() + n);
		else hold.setDronePartsAmt(hold.getDronePartsAmt() + n);
	}
	/** A system a part can be (a Junkyard-style part): any but artillery and the Clone Bay. */
	private static String partId() {
		List<String> kinds = new ArrayList<String>();
		for (net.blerf.ftl.parser.SavedGameParser.SystemType t : net.blerf.ftl.parser.SavedGameParser.SystemType.values())
			if (t != net.blerf.ftl.parser.SavedGameParser.SystemType.ARTILLERY && t != net.blerf.ftl.parser.SavedGameParser.SystemType.CLONEBAY && DataManager.get().getSystem(t.getId()) != null) kinds.add(t.getId());
		return kinds.get(new Random().nextInt(kinds.size()));
	}
	/** The part for the stored systems, at level 1 with a bar broken: its line added to those to store, and its title. */
	private static String part(List<String> lines, Vault v, String id) throws IOException {
		lines.add(homeplanet.vault.StoredSystems.line(id, 1, 1));
		return homeplanet.model.Items.systemTitle(id);
	}

	// ---- the file ----

	private static Properties read(Vault v) { return Store.read(file(v)); }
	/** The file as it stands, read without this class's lock (the crew register, taking stock, never takes it). */
	public static Properties asIs(Vault v) throws IOException { return Store.load(file(v)); }
	private static Properties readStrict(Vault v) throws IOException { return Store.load(file(v)); }
	private static byte[] bytes(Properties p) throws IOException { return Store.xml(p, NOTE); }
	private static void write(Vault v, Properties p) throws IOException { Store.write(file(v), p, NOTE); }
	/** Every sector's job weights total the same (for tests). */
	public static int weightTotal(String sector) { int t = 0; for (Object[] j : JOBS) t += jobWeight(sector, (String) j[0]); return t; }
	/** The sector ids (for tests). */
	public static List<String> sectors() { List<String> l = new ArrayList<String>(); for (String[] s : SECTORS) l.add(s[0]); return l; }
	/** The job ids (for tests). */
	public static List<String> jobs() { List<String> l = new ArrayList<String>(); for (Object[] j : JOBS) l.add((String) j[0]); return l; }
}
