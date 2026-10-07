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
import homeplanet.core.Store;
import homeplanet.model.Skills;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * What the crew expeditions share (heromedel, 5.00; the old board of jobs they were tried beside went at 5.67): the
 * infirmary (crew hurt on an expedition, laid up a while in infirmary.txt), the captives and their ransoms (captives.txt),
 * and hiring (a posting for volunteers, who wait in the Cargo Hold). Expedition_type 0 is hiring alone, 2 the crew
 * expeditions (Assignments).
 */
public final class Expeditions {
	private static final Logger log = LoggerFactory.getLogger(Expeditions.class);
	private Expeditions() { }

	public static final int PARTY_MAX = 3;
	/** The infirmary keeps a hurt crew member HEAL_MIN to HEAL_MAX beacons, a day each (rolled, never shown). */
	public static final int HEAL_MIN = 6, HEAL_MAX = 12;
	/**
	 * A ransom is asked RANSOM_DELAY_MIN to MAX beacons after the expedition and stands for RANSOM_STANDS beacons
	 * (the letters say "one month", never beacons: the count is the station's own); a reminder comes REMINDER_BEFORE
	 * beacons before the end.
	 */
	public static final int RANSOM_DELAY_MIN = 2, RANSOM_DELAY_MAX = 5, RANSOM_STANDS = 28, REMINDER_BEFORE = 6;

	/** The skills as the expeditions' words name them, in Crew.skillLevels' order. */
	static final String[] SKILLS = {"pilot", "engines", "shields", "weapons", "repair", "combat"};
	static int skillIndex(String name) { return name == null ? -1 : java.util.Arrays.asList(SKILLS).indexOf(name); }

	// ---- the crew who can go, and who they are ----

	/** The crew in the Cargo Hold who can be sent: not those laid up in the infirmary. */
	public static List<CrewState> holdCrew(Vault v) throws IOException {
		List<CrewState> out = new ArrayList<CrewState>();
		Set<String> laidUp = laidUpKeys(v);
		for (CrewState c : SaveHelper.getOwnCrew(v.readCopy(v.storage()).save.getPlayerShip())) if (!isLaidUp(laidUp, c)) out.add(c);
		return out;
	}
	private static String key(CrewState c) { return c.getName() + "/" + (c.getRace() == null ? "human" : c.getRace().getId()); }
	/**
	 * BAND-AID (docs/CONCERNS.md, 2): the station's records know a crew member by name and race, so two of a name and race
	 * get mixed up. Until crew who are away leave the Cargo Hold's save (the real fix), this mark tells namesakes apart:
	 * sex, colouring and the service record, none of which change while they sit in the hold. Records keep it beside the
	 * name; a record without one (from before) matches any namesake, as it always did.
	 */
	static String mark(CrewState c) {
		StringBuilder t = new StringBuilder();
		for (Integer i : c.getSpriteTintIndeces()) t.append(i).append('.');
		return (c.isMale() ? "m" : "f") + "/" + t + "/" + c.getRepairs() + "," + c.getCombatKills() + "," + c.getPilotedEvasions() + "," + c.getJumpsSurvived() + "," + c.getSkillMasteriesEarned();
	}

	/**
	 * For the crew expeditions ({@link Assignments}): hurt crew into the infirmary and taken ones among the captives (a
	 * ransom follows), in the station's own files, written with the transaction given.
	 */
	static void admitAndTake(Vault.Transaction tx, Vault v, List<CrewState> hurt, List<CrewState> taken, String captors, int now, Random rng) throws IOException {
		if (hurt.isEmpty() && taken.isEmpty()) return;
		Properties inf = readPropsStrict(infirmaryFile(v)), cap = readPropsStrict(captivesFile(v));
		for (CrewState c : hurt) admit(inf, c, now, rng);
		for (CrewState c : taken) takeCaptive(cap, c, captors, now, rng);
		if (!hurt.isEmpty()) tx.put(infirmaryFile(v), propsBytes(inf, INFIRMARY_NOTE));
		if (!taken.isEmpty()) tx.put(captivesFile(v), propsBytes(cap, CAPTIVES_NOTE));
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
		/** Their {@link #mark}, or null for a record from before marks (it matches any namesake). */
		final String mark;
		Patient(String name, String race, int until, int drained, String mark) { this.name = name; this.race = race; this.until = until; this.drained = drained; this.mark = mark; }
		String key() { return name + "/" + race; }
		/** Is this crew member the one laid up: a namesake with the mark, or any namesake for a record without one. */
		boolean is(CrewState c) { return key().equals(Expeditions.key(c)) && (mark == null || mark.equals(Expeditions.mark(c))); }
	}
	public static synchronized List<Patient> infirmary(Vault v) {
		List<Patient> out = new ArrayList<Patient>();
		Properties p = readProps(infirmaryFile(v));
		for (int i = 0; p.getProperty(i + ".name") != null; i++) out.add(new Patient(p.getProperty(i + ".name"), p.getProperty(i + ".race", "human"), Store.num(p, i + ".until", 0), Store.num(p, i + ".drained", 0), p.getProperty(i + ".mark")));
		return out;
	}
	/** Is this crew member laid up in the infirmary? */
	public static boolean laidUp(Vault v, CrewState c) { return isLaidUp(laidUpKeys(v), c); }
	/** Everyone laid up, for a list of crew to check against with {@link #isLaidUp} (one read of the infirmary): name/race|mark, or |* for a record without a mark. */
	public static Set<String> laidUpKeys(Vault v) {
		Set<String> out = new HashSet<String>();
		for (Patient x : infirmary(v)) out.add(x.key() + "|" + (x.mark == null ? "*" : x.mark));
		return out;
	}
	/** Is this crew member among those laid up ({@link #laidUpKeys})? */
	public static boolean isLaidUp(Set<String> laidUp, CrewState c) { return laidUp.contains(key(c) + "|" + mark(c)) || laidUp.contains(key(c) + "|*"); }
	/** Lays a crew member up in the infirmary (into its file's properties, written with the Cargo Hold). */
	private static void admit(Properties p, CrewState c, int now, Random rng) {
		int i = 0;
		while (p.getProperty(i + ".name") != null) i++;
		p.setProperty(i + ".name", c.getName());
		p.setProperty(i + ".race", c.getRace() == null ? "human" : c.getRace().getId());
		p.setProperty(i + ".until", Integer.toString(now + HEAL_MIN + rng.nextInt(HEAL_MAX - HEAL_MIN + 1)));
		p.setProperty(i + ".drained", Integer.toString(now));
		p.setProperty(i + ".mark", mark(c));
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
				for (Patient y : all) if (y.is(x) && (mine == null || y.mark != null)) mine = y; // a marked record over one without
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
		for (int i = 0; i < keep.size(); i++) { q.setProperty(i + ".name", keep.get(i).name); q.setProperty(i + ".race", keep.get(i).race); q.setProperty(i + ".until", Integer.toString(keep.get(i).until)); q.setProperty(i + ".drained", Integer.toString(now)); if (keep.get(i).mark != null) q.setProperty(i + ".mark", keep.get(i).mark); }
		try { writeProps(infirmaryFile(v), q, INFIRMARY_NOTE); } catch (IOException e) { log.warn("Could not write the infirmary: {}", e.toString()); }
		for (String n : back) HistoryLog.entry("EXPEDITION", n + " is out of the infirmary", null, homeplanet.core.Event.of("EXPEDITION").put("what", "out_of_infirmary").put("crew", n));
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
		int seen = Store.num(was, k, now);
		if (now > seen) {
			x.setHealth(max);
			HistoryLog.entry("MEDBAY", x.getName() + "'s visited The Station's Medbay", null, homeplanet.core.Event.of("MEDBAY").put("crew", x.getName()).put("race", x.getRace().getId()).put("place", place));
			return true;
		}
		q.setProperty(k, Integer.toString(seen));
		return false;
	}
	private static Properties readProps(File f) { return Store.read(f); }
	/** For a change: a file that can't be read is an error, so it's never written back empty. */
	private static Properties readPropsStrict(File f) throws IOException { return Store.load(f); }
	private static byte[] propsBytes(Properties p, String comment) throws IOException { return Store.bytes(p, comment); }
	private static void writeProps(File f, Properties p, String comment) throws IOException { Store.write(f, p, comment); }

	// ---- captives and ransoms ----

	private static File captivesFile(Vault v) { return new File(v.root, "captives.txt"); }
	/** The captives' file as it stands, read without a lock (for the crew register taking stock). */
	public static Properties captivesAsIs(Vault v) throws IOException { return Store.load(captivesFile(v)); }
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
				p.getProperty(i + ".captors", "pirates"), Store.num(p, i + ".ransom", 30), Store.num(p, i + ".asked", 0), Store.num(p, i + ".until", Store.num(p, i + ".asked", 0) + RANSOM_STANDS));
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
		HistoryLog.entry("EXPEDITION", c.name + ", taken by " + c.captors + ": " + why + "; presumed dead", null,
				homeplanet.core.Event.of("EXPEDITION").put("what", "captive_lost").put("crew", c.name).put("race", c.race).put("captors", c.captors).put("why", why));
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
		HistoryLog.entry("EXPEDITION", c.name + " ransomed from " + c.captors + " for " + c.ransom + " scrap, back in the Cargo Hold", null,
				homeplanet.core.Event.of("EXPEDITION").put("what", "ransomed").put("crew", c.name).put("race", c.race).put("captors", c.captors).put("ransom", c.ransom).put("to", "hold"));
		homeplanet.vault.Reputation.ransomed(v, c.name); // +2: brought home
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
	/** What posting for volunteers costs: 5 for each crew member the commander has, at most 60 (FTL's dearest crew); no scrap with none (the promise of adventure, which costs reputation). */
	public static int hireCost(int crew) { return Math.min(60, 5 * crew); }
	/** What a promise of adventure costs in reputation, with Reputation on (heromedel: nothing was too little; a battle's worth). */
	public static final int PROMISE_REP = 15;
	/** The promise's cost right now: PROMISE_REP with Reputation on and no crew anywhere, else 0. */
	public static int promiseRep(Vault v) { return Vault.isOpen() && homeplanet.core.Economy.repSpends() && fleetCrew(v) == 0 ? PROMISE_REP : 0; }
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
		int have = fleetCrew(v), cost = hireCost(have), rep = promiseRep(v);
		Ship st = v.storage();
		Vault.Copy c = v.readCopy(st);
		ShipState hold = c.save.getPlayerShip();
		if (hold.getScrapAmt() < cost) throw new IOException("The Cargo Hold holds " + hold.getScrapAmt() + " scrap; posting costs " + cost);
		if (rep > 0 && homeplanet.vault.Reputation.total(v) < rep) throw new IOException("A promise of adventure costs " + rep + " reputation; the career has " + homeplanet.vault.Reputation.total(v));
		hold.setScrapAmt(hold.getScrapAmt() - cost);
		CrewState hired = null;
		if (rng.nextInt(100) < (cost == 0 ? FREE_CHANCE : PAID_CHANCE)) {
			List<String> races = hireableRaces();
			hired = Commission.volunteer(races.get(rng.nextInt(races.size())), rng);
			if (hired != null && !SaveHelper.placeCrew(hold, hired, true)) throw new IOException("The Cargo Hold has no room for another crew member; nothing was spent");
			if (hired != null) hold.getCrewList().add(hired);
		}
		if (cost > 0 || hired != null) v.begin().put(st, c.save, c.hash).commit();
		if (rep > 0) homeplanet.vault.Reputation.spend(v, rep, "A promise of adventure posted" + (hired == null ? ", unanswered" : ": " + hired.getName() + " answered"));
		HistoryLog.entry("HIRE", (cost == 0 ? "A promise of adventure" + (rep > 0 ? ", " + rep + " reputation" : "") : "Posted for volunteers, " + cost + " scrap") + ": "
				+ (hired == null ? "no one answered" : hired.getName() + " (" + hired.getRace().getId() + ") joined, in the Cargo Hold"), null,
				homeplanet.core.Event.of("HIRE").put("how", cost == 0 ? "promise" : "posted").put("cost", cost).put("reputation", rep).put("crew", hired == null ? null : hired.getName())
						.put("race", hired == null ? null : hired.getRace().getId()).put("to", hired == null ? null : "hold"));
		return hired;
	}
}
