package homeplanet.parser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

import homeplanet.core.HistoryLog;
import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;
import homeplanet.model.Skills;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * Crew expeditions, heromedel's second system (expedition_type 2 in the cfg; the first is {@link Expeditions}, which this
 * shares nothing with but the infirmary, the captives and the hire button). The board offers three sectors; the
 * commander picks one and sends one to three crew from the Cargo Hold, and they leave it for a while. What job they
 * find there is drawn from the sector's weights, unseen. Each rolls a d20 for their outcome, with one reroll when the
 * sector or the job suits their race; the pot is 2d10 scrap, grown or cut by the headcount, each one's outcome, their
 * race's fit for the sector and the job, the job's skill and any hazard. A natural 20 may find an item; a Hijack,
 * Salvage or Rescue that went well may bring a prize. When they're back, a report says what happened in plain words,
 * never a roll or a percentage: the player learns who to send where from the reports alone.
 * Kept in the fleet's assignments.txt (the board, who's away); the words in resource/assignments.txt.
 */
public final class Assignments {
	private static final Logger log = LoggerFactory.getLogger(Assignments.class);
	private Assignments() { }

	public static final int OFFERS = 3, PARTY_MAX = 3;
	/**
	 * A detail is away AWAY_MIN to AWAY_MAX beacons, and longer as the job went ({@link #days}: a nebula, Abandoned or
	 * Crystal space, Got Lost, failures, things to carry home, Rock crew; a Scout is quicker), never past AWAY_CAP and
	 * never shown; setting out counts a beacon of the fleet's time. The result is rolled when they set out (so the delay
	 * can know it) and told only when they're back.
	 */
	public static final int AWAY_MIN = 1, AWAY_MAX = 3, AWAY_CAP = 10;
	/** An offer not taken comes down after OFFER_MIN to OFFER_MAX beacons (rolled, never shown). */
	public static final int OFFER_MIN = 3, OFFER_MAX = 8;
	/** The pot: 2d10, times 1 + 10% a head + everyone's modifiers; never under 1. */
	public static final int BASE_DICE = 2, BASE_DIE = 10, PER_HEAD = 10;
	/** One expedition in this many meets a hazard. */
	public static final int HAZARD_ONE_IN = 10;

	// ---- the tables, as heromedel set them ----

	/** The sectors: id, title, FTL's own name; Abandoned needs Advanced Edition, Crystal is drawn rarely. */
	public static final String[][] SECTORS = {
		{"civilian", "Civilian Sector"}, {"engi", "Engi Controlled Sector"}, {"zoltan", "Zoltan Controlled Sector"},
		{"mantis", "Mantis Controlled Sector"}, {"pirate", "Pirate Controlled Sector"}, {"rebel", "Rebel Controlled Sector"},
		{"rock", "Rock Controlled Sector"}, {"nebula", "Nebula"}, {"abandoned", "Abandoned Sector"}, {"crystal", "Hidden Crystal Worlds"}};
	public static String sectorTitle(String id) { for (String[] s : SECTORS) if (s[0].equals(id)) return s[1]; return id; }
	/** How often a sector is drawn for the board (Crystal rarely). */
	static int sectorWeight(String id) { return "crystal".equals(id) ? 1 : 4; }

	/** The jobs: id, title, base weight, the skill that helps (pilot, engines, shields, weapons, repair, combat; none for Negotiate and Rescue). */
	public static final Object[][] JOBS = {
		{"defend", "Defend", 14, "shields"}, {"attack", "Attack", 11, "weapons"}, {"negotiate", "Negotiate", 10, null}, {"boarded", "Get Boarded", 9, "combat"},
		{"rescue", "Rescue", 9, null}, {"salvage", "Salvage", 8, "repair"}, {"scout", "Scout", 8, "pilot"}, {"repair", "Repair", 8, "repair"},
		{"transport", "Transport", 8, "engines"}, {"lost", "Got Lost", 8, "pilot"}, {"escort", "Escort", 8, "engines"}, {"capture", "Capture", 8, "combat"},
		{"board", "Board", 7, "combat"}, {"hijack", "Hijack", 7, "pilot"}, {"infection", "Infection", 7, "repair"}, {"spiders", "Giant Spiders", 6, "combat"}};
	public static String jobTitle(String id) { for (Object[] j : JOBS) if (j[0].equals(id)) return (String) j[1]; return id; }
	static int jobSkill(String id) { for (Object[] j : JOBS) if (j[0].equals(id)) return Expeditions.skillIndex((String) j[3]); return -1; }
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
	/** The hazards: id, title, where (null: any sector), the races that shrug it off. */
	static final Object[][] HAZARDS = {
		{"flare", "a solar flare", null, new String[] {"rock", "anaerobic"}}, {"asteroids", "an asteroid field", null, new String[] {"energy"}},
		{"pulsar", "a pulsar", null, new String[] {"engi"}}, {"storm", "a plasma storm", "nebula", new String[] {"slug"}}};
	static boolean cancels(String hazard, String race) {
		for (Object[] h : HAZARDS) if (h[0].equals(hazard)) for (String r : (String[]) h[3]) if (r.equals(race)) return true;
		return false;
	}
	/** The d20's bands: died, injured, failed, success, high, top; and what each does to the pot. */
	public static final String[] BANDS = {"died", "injured", "failed", "success", "high", "top"};
	public static final int[] BAND_MOD = {-30, -20, -10, 10, 20, 30};
	public static int band(int roll) { return roll <= 1 ? 0 : roll <= 5 ? 1 : roll <= 9 ? 2 : roll <= 15 ? 3 : roll <= 19 ? 4 : 5; }
	/** Skill points a job pays its skill, by band (nothing for the dead, the infirmary's drain aside). */
	static final int[] BAND_XP = {0, 1, 1, 4, 6, 8};

	// ---- the words ----

	private static Map<String, List<String>> words;
	private static synchronized Map<String, List<String>> words() {
		if (words != null) return words;
		Map<String, List<String>> out = new HashMap<String, List<String>>();
		InputStream in = Assignments.class.getResourceAsStream("/homeplanet/resource/assignments.txt");
		if (in != null) {
			try {
				for (String line : new String(SafeFiles.readAll(in), StandardCharsets.UTF_8).split("\r?\n")) {
					line = line.trim();
					int bar = line.indexOf('|');
					if (line.isEmpty() || line.startsWith("#") || bar < 0) continue;
					String key = line.substring(0, bar).trim().replaceAll("\\s+", " "), text = line.substring(bar + 1).trim();
					if (!out.containsKey(key)) out.put(key, new ArrayList<String>());
					out.get(key).add(text);
				}
			} catch (IOException e) { log.warn("Could not read the expedition words: {}", e.toString()); }
		}
		return words = out;
	}
	/** One of the lines for this key, or the fallback. */
	static String say(Random rng, String fallback, String... key) {
		List<String> l = words().get(String.join(" ", key));
		return l == null || l.isEmpty() ? fallback : l.get(rng.nextInt(l.size()));
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
		for (String k : new String[] {"captured", "infirmary", "prize hijack ship", "prize hijack part", "prize salvage part", "prize rescue recruit"}) if (!words().containsKey(k)) out.add(k);
		return out;
	}

	// ---- the board and who's away (assignments.txt in the fleet) ----

	private static File file(Vault v) { return new File(v.root, "assignments.txt"); }
	private static final String NOTE = "Crew expeditions: the sectors on offer, and the crew away on one";

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
		Away(int index, String sector, int sentAt, int until, long seed, List<CrewState> crew) { this.index = index; this.sector = sector; this.sentAt = sentAt; this.until = until; this.seed = seed; this.crew = crew; }
		/** What came of it (the same every time: the seed). */
		public Result result() { return roll(sector, crew, new Random(seed)); }
		public List<String> names() { List<String> n = new ArrayList<String>(); for (CrewState c : crew) n.add(c.getName()); return n; }
	}

	/** The three sectors on offer (drawn afresh where one is empty or has come down). */
	public static synchronized List<Offer> board(Vault v) {
		Properties p = read(v);
		int now = v.beaconsSeen();
		boolean changed = false;
		Random rng = new Random();
		boolean ae = advancedEdition(v);
		for (int i = 0; i < OFFERS; i++) {
			String s = p.getProperty("offer." + i);
			if (s != null && now < intOf(p, "offer." + i + ".until", 0) && (ae || !"abandoned".equals(s))) continue;
			List<String> taken = new ArrayList<String>();
			for (int k = 0; k < OFFERS; k++) if (k != i && p.getProperty("offer." + k) != null) taken.add(p.getProperty("offer." + k));
			if (s != null) taken.add(s); // not the one that just came down or was taken
			String pick = drawSector(rng, taken, ae);
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
	/** A sector not on the board already, by weight; Abandoned only with Advanced Edition. */
	public static String drawSector(Random rng, List<String> taken, boolean ae) {
		List<String> ids = new ArrayList<String>();
		int total = 0;
		for (String[] s : SECTORS) {
			if (taken.contains(s[0]) || (!ae && "abandoned".equals(s[0]))) continue;
			ids.add(s[0]); total += sectorWeight(s[0]);
		}
		int r = rng.nextInt(total);
		for (String id : ids) { r -= sectorWeight(id); if (r < 0) return id; }
		return ids.get(ids.size() - 1);
	}
	/** Whether the fleet plays with Advanced Edition content (the Cargo Hold's save says). */
	static boolean advancedEdition(Vault v) {
		try { return v.readCopy(v.storage()).save.isDLCEnabled(); } catch (Exception e) { return true; }
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
				try { crew.add(homeplanet.comm.Line.crewFrom(f)); } catch (IOException e) { log.warn("A crew member away on an expedition can't be read: {}", e.toString()); }
			}
			long seed = 0;
			try { seed = Long.parseLong(p.getProperty("away." + i + ".seed", "0")); } catch (NumberFormatException e) { }
			out.add(new Away(i, p.getProperty("away." + i + ".sector"), intOf(p, "away." + i + ".sentAt", 0), intOf(p, "away." + i + ".until", 0), seed, crew));
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
				try { c = homeplanet.comm.Line.crewFrom(f); } catch (IOException e) { log.warn("A recruit waiting on an answer can't be read: {}", e.toString()); continue; }
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
			HistoryLog.entry("HIRE", x.name + " (" + race(x.crew) + "), rescued on an expedition, signed on: in the Cargo Hold");
			return;
		}
		SavedGameState gs = HomePlanet.savedGameParser.readSavedGame(x.save);
		Ship s = dock ? v.adopt(gs) : v.adoptJunked(gs);
		v.setOut(s, gs, "Brought home by an expedition");
		forget(p, x.index);
		write(v, p);
		x.save.delete();
		HistoryLog.entry("EXPEDITION", gs.getPlayerShipName() + " (" + gs.getPlayerShip().getShipBlueprintId() + "), brought home by an expedition, kept: " + (dock ? "at the Space Dock" : "in the Junkyard"));
	}
	/** No: the recruit goes their way, the ship is left where she lies. */
	public static synchronized void decline(Vault v, Pending x) throws IOException {
		Properties p = readStrict(v);
		forget(p, x.index);
		write(v, p);
		if (x.save != null) x.save.delete();
		HistoryLog.entry("EXPEDITION", "recruit".equals(x.kind) ? x.name + ", rescued on an expedition, was sent on their way" : x.name + ", brought home by an expedition, was not taken");
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
	 * file, due back in AWAY_MIN to AWAY_MAX beacons; setting out counts a beacon; the offer is replaced.
	 */
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
			CrewState mine = null;
			for (CrewState x : hold.getCrewList()) if (x == sent || (mine == null && x.getName().equals(sent.getName()) && x.getRace() == sent.getRace())) mine = x;
			if (mine == null) throw new IOException(sent.getName() + " is not in the Cargo Hold; nothing was changed");
			going.add(mine);
		}
		// rolled now, so the delay knows how it went; the record keeps the seed and the crew as they left, so it rolls the same when they're back
		long seed = rng.nextLong();
		List<CrewState> asLeft = new ArrayList<CrewState>();
		int k = 0;
		for (CrewState mine : going) {
			hold.getCrewList().remove(mine);
			Map<String, String> f = homeplanet.comm.Line.crewFields(mine);
			for (Map.Entry<String, String> e : f.entrySet()) p.setProperty("away." + i + ".crew." + k + "." + e.getKey(), e.getValue());
			asLeft.add(homeplanet.comm.Line.crewFrom(f));
			k++;
		}
		Result r = roll(sector, asLeft, new Random(seed));
		p.setProperty("away." + i + ".sector", sector);
		p.setProperty("away." + i + ".sentAt", Integer.toString(now));
		p.setProperty("away." + i + ".seed", Long.toString(seed));
		p.setProperty("away." + i + ".until", Integer.toString(now + days(r, asLeft, rng)));
		p.setProperty("offer." + slot + ".until", "0"); p.remove("offer." + slot + ".words"); // comes down now: redrawn, not the same sector, at the next look
		v.begin().put(st, c.save, c.hash).put(file(v), bytes(p)).commit();
		v.countBeacon();
		List<String> names = new ArrayList<String>();
		for (CrewState x : party) names.add(x.getName());
		HistoryLog.entry("EXPEDITION", String.join(", ", names) + " sent to " + sectorTitle(sector));
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
		public boolean rerolled, died, captured, infirmary;
		/** The item a 20 found (an id, or "fuel:3", "missiles:2", "parts:2"), or null. */
		public String item;
		public Fate(CrewState c) { crew = c; }
		public String name() { return crew.getName(); }
	}
	/** What came of an expedition. */
	public static final class Result {
		public String sector, job, hazard;
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
	/** A hazard, one time in HAZARD_ONE_IN (a plasma storm only in a nebula), or null. */
	static String drawHazard(String sector, Random rng) {
		if (rng.nextInt(HAZARD_ONE_IN) != 0) return null;
		List<String> fit = new ArrayList<String>();
		for (Object[] h : HAZARDS) if (h[2] == null || h[2].equals(sector)) fit.add((String) h[0]);
		return fit.get(rng.nextInt(fit.size()));
	}
	static String race(CrewState c) { return c.getRace() == null ? "human" : c.getRace().getId(); }
	/** Whether this crew member was sent hurt (their own bonuses count half). */
	static boolean hurt(CrewState c) { return c.getRace() != null && c.getHealth() < c.getRace().getMaxHealth(); }

	/**
	 * Rolls an expedition for this detail in this sector (nothing is written): the job, a hazard, each one's d20 with
	 * its reroll, the pot, the items and the prize, and the report in words.
	 */
	public static Result roll(String sector, List<CrewState> party, Random rng) {
		Result r = new Result();
		r.seed = rng.nextLong();
		r.sector = sector;
		r.job = drawJob(sector, rng);
		r.hazard = drawHazard(sector, rng);
		int skill = jobSkill(r.job);
		int mods = PER_HEAD * party.size(), best = 0;
		for (CrewState c : party) {
			Fate f = new Fate(c);
			r.fates.add(f);
			String race = race(c);
			int sec = raceSector(race, sector), job = raceJob(race, r.job);
			f.roll = 1 + rng.nextInt(20);
			if (f.roll <= 9) { // one reroll, when the sector or the job suits them (one good and one bad cancel out)
				int good = (sec > 0 ? 1 : 0) + (job > 0 ? 1 : 0), bad = (sec < 0 ? 1 : 0) + (job < 0 ? 1 : 0), chance = Math.max(0, good - bad);
				if (chance > 0 && rng.nextInt(4) < chance) { f.roll = 1 + rng.nextInt(20); f.rerolled = true; }
			}
			f.band = band(f.roll);
			best = Math.max(best, f.roll);
			int own = sec + job + (skill < 0 ? 0 : 10 * homeplanet.model.Crew.skillLevels(c)[skill]);
			if (hurt(c) && own > 0) own /= 2;
			int m = BAND_MOD[f.band] + own;
			if (r.hazard != null && !cancels(r.hazard, race)) m -= 10;
			mods += m;
			if (f.band == 0) f.died = true;
			else if (f.band == 1) {
				if ("spiders".equals(r.job)) f.died = true;
				else if ("boarded".equals(r.job) && rng.nextBoolean()) f.captured = true;
				else if (sec < 0 && job < 0) f.infirmary = true;
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
	private static String aOrAn(String s) { return (s.isEmpty() ? "" : "aeiouAEIOU".indexOf(s.charAt(0)) >= 0 ? "an " : "a ") + s; }

	/** The report, as heromedel laid it out: the frame, the job line, a hazard, a line per crew member, the prize, the total. Never a roll. */
	static String report(Result r, Random rng) {
		StringBuilder sb = new StringBuilder("-- Expedition Report --\n");
		sb.append("Sector: ").append(sectorTitle(r.sector)).append("\n");
		sb.append("Due to events during the assignment the crew\n").append(say(rng, "took on a job", "event", r.job)).append(".\n");
		if (r.hazard != null) sb.append(say(rng, "The weather was against them.", "hazard", r.hazard)).append("\n");
		sb.append("\n");
		for (Fate f : r.fates) {
			String line;
			if (f.captured) line = say(rng, "was taken by the boarders. Word may come.", "captured");
			else if (f.infirmary) line = say(rng, "was badly hurt and is in the infirmary.", "infirmary");
			else {
				line = say(rng, f.died ? "was killed" : BANDS[f.band].equals("top") ? "was extremely successful" : "was " + BANDS[f.band], "band", r.job, BANDS[f.band]);
				if (f.item != null) line += (line.contains("brought back") ? ", " : " and brought back ") + (f.item.indexOf(':') < 0 ? aOrAn(itemWords(f.item)) : itemWords(f.item));
				line += ".";
			}
			sb.append(f.name()).append(" ").append(line).append("\n");
		}
		if (r.prize != null) sb.append(say(rng, "They brought something back.", "prize", r.job, r.prize).replace("{name}", r.prizeDetail == null ? "" : r.prizeDetail)).append("\n");
		sb.append("\nTotal Reward: ").append(r.scrap).append(" scrap");
		return sb.toString();
	}

	// ---- back home ----

	/** A detail back: the report, and whether anything went to the Junkyard or the stored systems. */
	public static final class Report {
		public final String sector, text;
		public final List<String> names;
		public Report(String sector, String text, List<String> names) { this.sector = sector; this.text = text; this.names = names; }
		public String title() { return "Back from " + ("nebula".equals(sector) ? "the nebula" : "the " + sectorTitle(sector)); }
	}

	/** The details whose time is up, each rolled and brought home: the hold, the infirmary, the captives, the Junkyard and the stored systems written. */
	public static synchronized List<Report> checkReturns(Vault v) {
		List<Report> out = new ArrayList<Report>();
		if (!Vault.isOpen()) return out;
		Properties p = read(v);
		int now = v.beaconsSeen();
		for (Away a : away(p)) {
			if (now < a.until) continue;
			try { out.add(bringHome(v, a, a.result())); }
			catch (Exception e) { log.warn("A detail could not be brought home: {}", e.toString()); }
		}
		return out;
	}
	/** Brings a detail home with this result (for the station's round, and tests). */
	public static synchronized Report bringHome(Vault v, Away a, Result r) throws IOException {
		Properties p = readStrict(v);
		Ship st = v.storage();
		Vault.Copy c = v.readCopy(st);
		ShipState hold = c.save.getPlayerShip();
		hold.setScrapAmt(hold.getScrapAmt() + r.scrap);
		List<CrewState> hurt = new ArrayList<CrewState>(), taken = new ArrayList<CrewState>();
		int skill = jobSkill(r.job);
		for (Fate f : r.fates) {
			CrewState m = f.crew;
			if (f.died) continue;
			if (f.captured) { taken.add(m); continue; }
			int max = m.getRace() == null ? 100 : m.getRace().getMaxHealth();
			if (f.infirmary) { m.setHealth(Math.max(1, Math.min(m.getHealth(), max / 4))); hurt.add(m); }
			else if (f.band == 1) m.setHealth(Math.max(1, Math.min(m.getHealth(), max / 2)));
			if (skill >= 0 && BAND_XP[f.band] > 0) Skills.add(m, skill, BAND_XP[f.band]);
			if (!SaveHelper.placeCrew(hold, m, true)) throw new IOException("The Cargo Hold has no room for " + m.getName() + "; the detail waits");
			hold.getCrewList().add(m);
			if (f.item != null) give(hold, f.item);
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
				prizeFile = new File(dir, "prize-" + a.sentAt + "-" + a.index + ".sav");
				net.blerf.ftl.xml.ShipBlueprint bp = DataManager.get().getShip(gs.getPlayerShip().getShipBlueprintId());
				r.prizeDetail = gs.getPlayerShipName() + (bp == null ? "" : " (" + bp.getName() + ")");
				pendingIndex = keep(p, "ship", r.prizeDetail);
				p.setProperty("pending." + pendingIndex + ".file", "assignments/" + prizeFile.getName());
			} catch (Exception e) { log.warn("The hijacked ship could not be built: {}", e.toString()); r.prize = "part"; }
		}
		if ("part".equals(r.prize)) { r.prizeDetail = part(stored, v); }
		if ("recruit".equals(r.prize)) {
			CrewState n = recruit(new Random());
			if (n == null) r.prize = null;
			else {
				r.recruit = n; r.prizeDetail = n.getName() + " (" + homeplanet.model.Crew.raceTitle(n) + ")";
				pendingIndex = keep(p, "recruit", n.getName());
				for (Map.Entry<String, String> e : homeplanet.comm.Line.crewFields(n).entrySet()) p.setProperty("pending." + pendingIndex + ".crew." + e.getKey(), e.getValue());
			}
		}
		String letter = "expedition:" + a.sentAt + ":" + a.index + ":" + String.join(",", a.names());
		if (HomePlanet.immersiveNotifications() && pendingIndex >= 0) p.setProperty("pending." + pendingIndex + ".letter", letter); // the letter asks, with buttons
		Vault.Transaction tx = v.begin().put(st, c.save, c.hash);
		if (prizeFile != null) tx.put(prizeFile, prizeBytes);
		Expeditions.admitAndTake(tx, v, hurt, taken, sectorCaptors(r.sector), v.beaconsSeen(), new Random());
		if (!stored.isEmpty()) tx.put(v.systemsFile(), (String.join("\n", stored) + "\n").getBytes(StandardCharsets.UTF_8));
		// the away record goes, and the rest are renumbered without a gap
		Properties q = new Properties();
		int n = 0;
		for (int i = 0; i < 100; i++) {
			if (i == a.index || p.getProperty("away." + i + ".sector") == null) continue;
			for (String key : p.stringPropertyNames()) if (key.startsWith("away." + i + ".")) q.setProperty("away." + n + key.substring(("away." + i).length()), p.getProperty(key));
			n++;
		}
		for (String key : p.stringPropertyNames()) if (!key.startsWith("away.")) q.setProperty(key, p.getProperty(key));
		tx.put(file(v), bytes(q)).commit();
		String text = r.report = report(r, new Random(r.seed)); // told again now everything is settled (the prize, the recruit's name), in the same words
		List<String> dead = new ArrayList<String>();
		for (Fate f : r.dead()) dead.add(f.name());
		HistoryLog.entry("EXPEDITION", String.join(", ", a.names()) + " back from " + sectorTitle(r.sector) + " (" + jobTitle(r.job) + "): " + r.scrap + " scrap"
				+ (r.prize == null ? "" : "; " + r.prize + (r.prizeDetail == null ? "" : " " + r.prizeDetail)) + (dead.isEmpty() ? "" : "; killed: " + String.join(", ", dead)));
		if (HomePlanet.immersiveNotifications()) Transmissions.deliver(letter, "Expedition Command", "Back from " + sectorTitle(r.sector), text);
		return new Report(r.sector, text, a.names());
	}
	/** Who holds a captive taken in this sector. */
	static String sectorCaptors(String sector) {
		return "rebel".equals(sector) ? "the rebels" : "mantis".equals(sector) ? "a Mantis clan" : "pirate".equals(sector) ? "pirates" : "slavers";
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
	/** A system for the stored systems (a Junkyard-style part, at level 1 with a bar broken): the lines to write, and its title. */
	private static String part(List<String> lines, Vault v) throws IOException {
		List<String> kinds = new ArrayList<String>();
		for (net.blerf.ftl.parser.SavedGameParser.SystemType t : net.blerf.ftl.parser.SavedGameParser.SystemType.values())
			if (t != net.blerf.ftl.parser.SavedGameParser.SystemType.ARTILLERY && t != net.blerf.ftl.parser.SavedGameParser.SystemType.CLONEBAY && DataManager.get().getSystem(t.getId()) != null) kinds.add(t.getId());
		String id = kinds.get(new Random().nextInt(kinds.size()));
		File f = v.systemsFile();
		if (f.isFile()) lines.addAll(java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
		else lines.add(homeplanet.ui.SystemsPanel.HEADER);
		lines.add(homeplanet.ui.SystemsPanel.line(id, 1, 1));
		return homeplanet.model.Items.systemTitle(id);
	}

	// ---- the file ----

	private static Properties read(Vault v) {
		Properties p = new Properties();
		File f = file(v);
		if (!f.isFile()) return p;
		try { p.load(new java.io.StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); }
		return p;
	}
	private static Properties readStrict(Vault v) throws IOException {
		Properties p = new Properties();
		File f = file(v);
		if (f.isFile()) p.load(new java.io.StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8)));
		return p;
	}
	private static byte[] bytes(Properties p) throws IOException {
		java.io.StringWriter w = new java.io.StringWriter();
		p.store(w, NOTE);
		return w.toString().getBytes(StandardCharsets.UTF_8);
	}
	private static void write(Vault v, Properties p) throws IOException { SafeFiles.writeText(file(v), new String(bytes(p), StandardCharsets.UTF_8), false); }
	private static int intOf(Properties p, String key, int dflt) {
		try { return Integer.parseInt(p.getProperty(key, "").trim()); } catch (NumberFormatException e) { return dflt; }
	}
	/** Every sector's job weights total the same (for tests). */
	public static int weightTotal(String sector) { int t = 0; for (Object[] j : JOBS) t += jobWeight(sector, (String) j[0]); return t; }
	/** The sector ids (for tests). */
	public static List<String> sectors() { List<String> l = new ArrayList<String>(); for (String[] s : SECTORS) l.add(s[0]); return l; }
	/** The job ids (for tests). */
	public static List<String> jobs() { List<String> l = new ArrayList<String>(); for (Object[] j : JOBS) l.add((String) j[0]); return l; }
}
