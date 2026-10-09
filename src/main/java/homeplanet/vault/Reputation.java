package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.parser.SavedGameParser.BeaconState;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.FleetPresence;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;

import homeplanet.core.HomePlanet;
import homeplanet.core.Store;
import homeplanet.parser.SaveHelper;

/**
 * A career's standing with The Federation Home Planet (reputation.txt and reputation.log in the fleet's folder): earned
 * by what the fleet's ships do in FTL, lost by what they lose. Counted at each look the station takes at a save FTL
 * wrote (the voyage log's moment), against the last count kept here for each ship, so nothing counts twice; the
 * station's own changes (a trade, a New Journey, commissioning) move the count without scoring. The first time a
 * career is counted, its service so far is reviewed once from what the station keeps. Nothing is ever lost in sector 8:
 * the last stand at the Federation's own worlds is honourable. Only careers have it (HomePlanet.career()).
 */
public final class Reputation {
	private static final Logger log = LoggerFactory.getLogger(Reputation.class);
	private Reputation() { }

	static final String FILE = "reputation"; // reputation.xml (5.86)

	// ---- the scoring (docs/ROADMAP.md) ----
	public static final int SECTOR = 6, DEFEATED = 4, REBEL_DEFEATED = 6, FLAGSHIP = 100;
	/** Responding to a distress signal (heromedel, 6.21): +1 for arriving, whatever it turns out to be. */
	public static final int DISTRESS = 1;
	/** Scrap collected counts a tenth (FTL's own total: sales at stores and the Scrap Recovery Arm don't add to it). */
	public static final int SCRAP_PER_POINT = 10;
	public static final int CREW_DIED = -10, SHIP_LOST = -50;
	/** An event's outcome at a beacon with no fight and no store: gains only +2, losses only -1. */
	public static final int EVENT_GOOD = 2, EVENT_BAD = -1;
	/** Caught by the rebel fleet: at a beacon it holds (arrived at one, or overtaken while waiting). */
	public static final int CAUGHT = -5;
	/** Each FTL achievement earned in the fleet's service (FTL's real ones, not its hidden markers). */
	public static final int ACHIEVEMENT = 10;
	/** A crew member taken captive (heromedel): -4; brought home by paying the ransom: +2. */
	public static final int CAPTURED = -4, RANSOMED = 2;
	/** FTL's sector 8 (0 is the first): nothing is lost there. */
	static final int LAST_STAND = 7;

	/**
	 * Reputation's lock is the fleet's own vault (5.61): the vault locks first and then calls in here (a ship lost, a
	 * look), while a review read the vault inside a lock of Reputation's own, and the two orders could freeze the
	 * station. With one lock there is no order to get wrong.
	 */
	private static Object lock(Vault v) { return v != null ? v : Reputation.class; }

	/** Does the fleet in use have a reputation (Settings' Reputation rule, always on in Immersive Mode)? */
	public static boolean shown() { return Vault.isOpen() && HomePlanet.reputation(); }

	// ---- the rate (heromedel, 6.13): what's earned counts x1 on Easy, x1.5 on Normal, x2 on Hard; losses and spending never ----

	/** The rates in words, by level: 0, 1, 2. */
	public static final String[] RATE_WORDS = {"x1 (as on Easy)", "x1.5 (as on Normal)", "x2 (as on Hard)"};
	/** Sandbox Mode's: the player's, in Settings, changeable anytime. */
	public static final String RATE_FREE = "free";
	/** Easy, Normal or Hard: the difficulty's. */
	public static final String RATE_FIXED = "fixed";
	/** A Custom career (or one from before difficulties) that chose its rate, fixed from then on. */
	public static final String RATE_CHOSEN = "chosen";
	/** A Custom career that hasn't chosen yet: the Space Dock asks, as its briefing would have (x1 meanwhile). */
	public static final String RATE_ASK = "ask";
	/** How the rate stands for a career with these rules and this saved answer (null: none); null rules, Sandbox Mode. */
	public static String rateRule(homeplanet.parser.CareerRules r, String saved) {
		if (r == null) return RATE_FREE;
		if (homeplanet.parser.CareerRules.CUSTOM.equals(r.name) || homeplanet.parser.CareerRules.EARLIER.equals(r.name)) return level(saved) < 0 ? RATE_ASK : RATE_CHOSEN;
		return RATE_FIXED; // no saved answer is read: deleting one changes nothing
	}
	/** How it stands for the fleet in use. */
	public static String rateRule() {
		homeplanet.parser.CareerRules r = homeplanet.parser.CareerRules.current();
		return rateRule(r, r == null ? null : homeplanet.parser.Career.repRate(Vault.get().root));
	}
	/** The rate's level (0 x1, 1 x1.5, 2 x2) for a career with these rules and this saved answer; null rules, Sandbox Mode's setting. */
	public static int rateLevel(homeplanet.parser.CareerRules r, String saved) {
		if (r == null) return Math.max(0, Math.min(2, HomePlanet.reputationRate));
		if (homeplanet.parser.CareerRules.HARD.equals(r.name)) return 2;
		if (homeplanet.parser.CareerRules.NORMAL.equals(r.name)) return 1;
		if (homeplanet.parser.CareerRules.EASY.equals(r.name)) return 0;
		return Math.max(0, level(saved));
	}
	/** The fleet in use's. */
	public static int rateLevel() {
		if (!Vault.isOpen()) return 0;
		homeplanet.parser.CareerRules r = homeplanet.parser.CareerRules.current();
		return rateLevel(r, r == null ? null : homeplanet.parser.Career.repRate(Vault.get().root));
	}
	private static int level(String saved) {
		try { int l = Integer.parseInt(saved.trim()); return l >= 0 && l <= 2 ? l : -1; } catch (RuntimeException e) { return -1; }
	}
	/** A Custom career's rate, chosen once (its briefing, or the Space Dock's question), and logged. */
	public static void chooseRate(File immersiveRoot, int level) throws IOException {
		homeplanet.parser.Career.setRepRate(immersiveRoot, level);
		homeplanet.core.HistoryLog.entry("CAREER", "Reputation earned at " + RATE_WORDS[level] + " (chosen for the career, fixed)", null,
				homeplanet.core.Event.of("CAREER").put("what", "reputation_rate").put("rate", RATE[level]).put("level", level));
	}
	/** Each level as a number for the log. */
	static final String[] RATE = {"1", "1.5", "2"};
	// ---- in FTL (heromedel, 6.17): her own difficulty and the sector it happened in, on top of the career's rate ----

	/** A ship's rate by FTL's difficulty, in words for the log: Easy 1, Normal 1.25, Hard 1.5. */
	static final String[] SHIP_RATE = {"1", "1.25", "1.5"};
	/** Her rate's level from her save's difficulty (0 Easy, 1 Normal, 2 Hard; Easy if unknown). */
	static int shipLevel(SavedGameState gs) {
		net.blerf.ftl.constants.Difficulty d = gs == null ? null : gs.getDifficulty();
		return d == net.blerf.ftl.constants.Difficulty.HARD ? 2 : d == net.blerf.ftl.constants.Difficulty.NORMAL ? 1 : 0;
	}
	/** The sector bonus's index from FTL's sector (0 is sector 1: x1; 7 is sector 8: x1.7). */
	static int sectorIndex(int ftlSector) { return Math.max(0, Math.min(LAST_STAND, ftlSector)); }
	/** The sector bonus in words for the log: 1 + 0.1 a sector after the first. */
	static String sectorRate(int index) { return index == 0 ? "1" : "1." + index; }

	// ---- the total: its whole part in "total", as always (every reader reads it), and a hidden remainder in ten-thousandths
	// in "rest" (6.17; 6.13 kept a half point in "half", read as 5000) ----

	/** Ten-thousandths of a point. */
	static final long UNIT = 10000;
	static long units(Properties p) {
		long rest = Store.num(p, "rest", "1".equals(p.getProperty("half")) ? 5000 : 0);
		return num(p, "total") * UNIT + Math.max(0, Math.min(UNIT - 1, rest));
	}
	private static void setUnits(Properties p, long u) {
		p.setProperty("total", Long.toString(Math.floorDiv(u, UNIT)));
		long rest = Math.floorMod(u, UNIT);
		p.remove("half");
		if (rest != 0) p.setProperty("rest", Long.toString(rest)); else p.remove("rest");
	}
	/** Moves the total by points as they are (a loss, spending, a loss given back). */
	private static void plain(Properties p, int points) { setUnits(p, units(p) + points * UNIT); }

	/**
	 * One change, piece by piece (6.13): each earned piece at its rate, each loss as it is. Every piece is shown at what it
	 * added to the total ("10 scrap collected (+2)" at x2), the remainder carried silently from piece to piece, so the
	 * pieces always add up to the change the player sees, and no rate or bonus is ever shown as a piece of its own.
	 * What a ship does in FTL (6.17) counts at her difficulty's rate and her sector's bonus besides.
	 */
	private static final class Change {
		/** The career's rate, doubled: 2, 3 or 4. */
		private final int career = 2 + rateLevel();
		private final List<String> texts = new ArrayList<String>();
		private final List<Long> units = new ArrayList<Long>();
		/** Her rate's level and the sector bonus's index, for the machine line; -1 if nothing was earned in FTL. */
		int ship = -1, sector = -1;
		/** Earned away from FTL (an achievement, an expedition, a ransom): at the career's rate. */
		Change earned(String text, int points) { texts.add(text); units.add(points * (UNIT / 2) * career); return this; }
		/** Earned in FTL: her difficulty's rate (x1, x1.25, x1.5) times the sector's (x1 to x1.7) times the career's. */
		Change inFtl(String text, int points, int shipLevel, int sectorIndex) {
			ship = shipLevel; sector = sectorIndex;
			texts.add(text);
			units.add(points * (UNIT / 80) * (4 + shipLevel) * (10 + sectorIndex) * career); // (4..6)/4 x (10..17)/10 x (2..4)/2
			return this;
		}
		/** Lost: as it is. */
		Change lost(String text, int points) { texts.add(text); units.add(points * UNIT); return this; }
		boolean isEmpty() { return texts.isEmpty(); }
		/** The whole points it moved the total by, and the exact change in ten-thousandths (the machine line's); set by {@link #apply}. */
		int moved;
		long movedUnits;
		/** Puts it into the total; returns its pieces in words, each with what it added. */
		List<String> apply(Properties p) {
			List<String> out = new ArrayList<String>();
			long before = units(p), u = before;
			for (int i = 0; i < texts.size(); i++) {
				long was = Math.floorDiv(u, UNIT);
				u += units.get(i);
				out.add(texts.get(i) + " (" + signed((int) (Math.floorDiv(u, UNIT) - was)) + ")");
			}
			setUnits(p, u);
			moved = (int) (Math.floorDiv(u, UNIT) - Math.floorDiv(before, UNIT));
			movedUnits = u - before;
			return out;
		}
	}

	/** The fleet's reputation, its service reviewed first if it never was, and any new FTL achievements counted. */
	public static int total(Vault v) {
		synchronized (lock(v)) {
			Properties p = read(v);
			if (!counted(p)) { review(v); p = read(v); }
			else if (shown()) { achievements(v, p); cruisers(v, read(v)); p = read(v); }
			return num(p, "total");
		}
	}
	/** New FTL achievements (the profile, as the career's rewards count them: earned in this fleet's service) score. */
	private static void achievements(Vault v, Properties p) {
		List<String> fresh = new ArrayList<String>();
		java.util.Set<String> had = new java.util.HashSet<String>(java.util.Arrays.asList(p.getProperty("achievements", "").split("\\|")));
		for (String id : newAchievements()) if (!had.contains(id)) fresh.add(id);
		if (fresh.isEmpty()) return;
		List<String> names = new ArrayList<String>();
		for (String id : fresh) names.add(achievementName(id));
		had.addAll(fresh);
		had.remove("");
		p.setProperty("achievements", String.join("|", had));
		Change ch = new Change().earned((fresh.size() == 1 ? "An achievement: " : fresh.size() + " achievements: ") + String.join(", ", names), fresh.size() * ACHIEVEMENT);
		String why = ch.apply(p).get(0);
		if (write(v, p)) entry(v, "achievement", ch, why, null);
	}
	/**
	 * Each Federation Cruiser layout unlocked in the career's service (Ranks From Rep, heromedel, 5.56): +100, once. Not
	 * those unlocked before the record began, or while an uncommissioned ship was boarded.
	 */
	private static void cruisers(Vault v, Properties p) {
		if (homeplanet.parser.PlayerRank.mode() != homeplanet.parser.PlayerRank.FROM_REP) return;
		homeplanet.parser.Unlocks u = homeplanet.parser.Unlocks.read();
		if (u.problem() != null) return;
		java.util.Set<String> had = new java.util.LinkedHashSet<String>(java.util.Arrays.asList(p.getProperty("cruisers", "").split("\\|")));
		had.remove("");
		int before = had.size();
		boolean known = homeplanet.parser.UnlockGrants.recorded();
		List<String> fresh = new ArrayList<String>();
		for (int n = 0; n < 3; n++) {
			String k = "PLAYER_SHIP_FED " + n;
			if (had.contains(k) || !u.unlocked("PLAYER_SHIP_FED", n)) continue;
			had.add(k);
			if (known && !homeplanet.parser.UnlockGrants.seen(k)) fresh.add(k); // with no record of what came before, a baseline only
		}
		if (had.size() == before) return;
		p.setProperty("cruisers", String.join("|", had));
		int pts = fresh.size() * homeplanet.parser.PlayerRank.CRUISER_BONUS;
		List<String> names = new ArrayList<String>();
		for (String k : fresh) names.add(homeplanet.parser.UnlockGrants.describe(k));
		Change ch = new Change();
		String why = pts > 0 ? ch.earned(String.join(", ", names) + " unlocked", pts).apply(p).get(0) : null;
		if (write(v, p) && pts > 0) entry(v, "cruiser", ch, why, null);
	}
	/** FTL's real achievements earned since the fleet's record began (empty if the profile can't be read). */
	private static List<String> newAchievements() {
		List<String> out = new ArrayList<String>();
		try {
			homeplanet.parser.Unlocks u = homeplanet.parser.Unlocks.read();
			if (u.problem() != null) return out;
			for (String id : homeplanet.parser.UnlockGrants.newAchievements(u)) {
				net.blerf.ftl.xml.Achievement a = net.blerf.ftl.parser.DataManager.get().getAchievement(id);
				if (a != null && !a.isVictory() && !a.isQuest() && !victory(id)) out.add(id);
			}
		} catch (Exception e) {
			log.warn("Could not read the FTL profile's achievements: {}", e.toString());
		}
		return out;
	}
	/**
	 * FTL's victory achievements (Federation Victory, Easy and Normal): told in their own letter when won, then never
	 * scored or told again, as the war goes on (hard rule 1; heromedel, 5.60). The parser's isVictory() marks only its own
	 * PLAYER_SHIP_*_VICTORY markers, not these.
	 */
	public static boolean victory(String achievementId) { return "ACH_WIN_EASY".equals(achievementId) || "ACH_WIN_NORMAL".equals(achievementId); }
	private static String achievementName(String id) {
		try { return net.blerf.ftl.parser.DataManager.get().getAchievement(id).getName().getTextValue(); } catch (Exception e) { return id; }
	}
	/** The newest entries (headline lines), newest first, at most n. */
	public static List<String> recent(Vault v, int n) {
		List<String> out = new ArrayList<String>();
		String[] lines = log(v).split("\r?\n");
		for (int i = lines.length - 1; i >= 0 && out.size() < n; i--) if (!lines[i].isEmpty() && !lines[i].startsWith("  ")) out.add(lines[i]);
		return out;
	}
	/**
	 * The Career Reputation Log, oldest first (empty if none yet), in its own form: each change's minute, the change, why,
	 * and its details under it. From the event log (5.92; reputation.log before): each REPUTATION entry's points, its
	 * why (the human line for one written before 5.92, which the lore never words), its details.
	 */
	public static String log(Vault v) {
		StringBuilder sb = new StringBuilder();
		for (homeplanet.core.EventLog.Entry e : homeplanet.core.EventLog.sorted(homeplanet.core.EventLog.read(v))) {
			if (!e.kind.equals("REPUTATION") || !"reputation".equals(e.get("log"))) continue;
			int points;
			try { points = Integer.parseInt(e.get("points", "0").trim()); } catch (NumberFormatException x) { points = 0; }
			sb.append(e.time.length() >= 16 ? e.time.substring(0, 16) : e.time).append("  ").append(signed(points)).append("  ").append(e.get("why", e.human)).append('\n');
			for (String[] kv : e.fields()) if (kv[0].startsWith("detail.")) sb.append("  ").append(kv[1]).append('\n');
		}
		return sb.toString();
	}

	// ---- counting as FTL plays ----

	/** FTL has written her save (the voyage log's look): what she did since her last count scores. */
	static void look(Vault v, Ship s, SavedGameState gs) {
		synchronized (lock(v)) {
			if (s == null || gs == null || s.state == Ship.State.STORAGE) return;
			Properties p = read(v);
			if (!shown()) { if (counted(p)) rebase(v, s, gs); return; } // careers switched off: her count moves on, so nothing done meanwhile scores later
			if (!counted(p)) { review(v); return; } // the review counts her as she is now
			Props was = new Props(p, s.id);
			Props now = Props.of(gs);
			if (!was.known()) { now.put(p, s.id, 0); write(v, p); return; } // a ship the count hasn't met: she starts here
			Change ch = new Change(); // earned at the rates, lost as it is
			int ship = shipLevel(gs), at = sectorIndex(now.sector); // her difficulty, and the sector it happened in (6.17)
			boolean lastStand = now.sector >= LAST_STAND || was.sector >= LAST_STAND;
			if (now.sector > was.sector) {
				int n = now.sector - was.sector;
				ch.inFtl(n == 1 ? "sector " + (now.sector + 1) + " reached" : n + " sectors further", n * SECTOR, ship, at);
			}
			if ((now.beacon != was.beacon || now.sector != was.sector) && homeplanet.parser.Distress.arrivedAt(gs)) ch.inFtl("responded to a distress signal", DISTRESS, ship, at); // once an arrival
			int scrap = Math.max(0, now.collected - was.collected) + was.rest;
			int fromScrap = scrap / SCRAP_PER_POINT;
			if (fromScrap > 0) ch.inFtl((scrap - was.rest) + " scrap collected", fromScrap, ship, at);
			int defeated = Math.max(0, now.defeated - was.defeated);
			if (defeated > 0) {
				boolean rebel = rebel(now.enemy) || rebel(was.enemy);
				int pts = defeated * DEFEATED + (rebel ? REBEL_DEFEATED - DEFEATED : 0);
				ch.inFtl((rebel ? (defeated == 1 ? "a rebel ship" : defeated + " ships, a rebel among them") : defeated == 1 ? "a ship" : defeated + " ships") + " defeated", pts, ship, at);
			}
			// a death: FTL's lost-crew count went up and the crew member is gone (a clone came back; a dismissal isn't a death)
			int died = Math.min(Math.max(0, now.lost - was.lost), gone(was.crew, now.crew).size());
			if (died > 0 && !lastStand) {
				List<String> names = gone(was.crew, now.crew);
				ch.lost(died == 1 ? names.get(0) + " died" : died + " crew died", died * CREW_DIED);
			}
			// caught by the rebel fleet: at a beacon it holds that she wasn't caught at already (never in the last stand)
			boolean moved = now.beacon != was.beacon || now.sector != was.sector;
			if (now.rebel && (moved || !was.rebel) && !lastStand) {
				ch.lost("caught by the rebel fleet", CAUGHT);
			}
			// an event's outcome: a jump within the sector to a beacon with no fight, no ship and no store, nor a store left behind
			if (moved && now.sector == was.sector && defeated == 0 && now.enemy.isEmpty() && !now.store && !was.store) {
				int outcome = outcome(was, now, died);
				if (outcome > 0) ch.inFtl("a good outcome", EVENT_GOOD, ship, at);
				else if (outcome < 0 && !lastStand) ch.lost("a bad outcome", EVENT_BAD);
			}
			now.put(p, s.id, scrap % SCRAP_PER_POINT);
			if (!ch.isEmpty()) {
				List<String> why = ch.apply(p);
				if (write(v, p)) entry(v, "voyage", ch, s.name + ": " + String.join(", ", why), null, Vault.where(Vault.shipEvent("SHIP", s), gs));
			} else {
				write(v, p);
			}
		}
	}
	/**
	 * What a beacon's event did (FTL keeps no record of the choice, only of its results): 1 if she only gained (scrap, crew,
	 * gear, missiles or drone parts), -1 if she only lost (hull, crew, gear, scrap, missiles or drone parts), 0 if both or
	 * neither. The jump's own fuel isn't counted.
	 */
	private static int outcome(Props was, Props now, int died) {
		boolean gained = now.collected > was.collected || !gone(now.crew, was.crew).isEmpty() || !gone(now.items, was.items).isEmpty() || now.ammo > was.ammo;
		boolean lost = now.hull < was.hull || died > 0 || !gone(was.items, now.items).isEmpty() || (now.scrapNow < was.scrapNow && now.collected == was.collected) || now.ammo < was.ammo;
		return gained == lost ? 0 : gained ? 1 : -1;
	}
	/** The station changed her itself (a trade, a New Journey, commissioning): her count moves, nothing scores. */
	static void rebase(Vault v, Ship s, SavedGameState gs) {
		synchronized (lock(v)) {
			if (s == null || gs == null || s.state == Ship.State.STORAGE) return;
			Properties p = read(v);
			if (!counted(p)) return; // the review will count her as she is (or there's no career, and never was)
			Props.of(gs).put(p, s.id, new Props(p, s.id).rest);
			write(v, p);
		}
	}
	/** She was lost in action: the fleet's loss, unless it was the last stand (sector 8). */
	static int lost(Vault v, Ship s) {
		synchronized (lock(v)) {
			if (s == null) return 0;
			Properties p = read(v);
			if (!counted(p)) return 0; // the review counts her loss from her fate
			Props was = new Props(p, s.id);
			int sector = was.known() ? was.sector : VoyageLog.lastSector(v, s);
			Props.forget(p, s.id);
			if (sector >= LAST_STAND || !shown()) { write(v, p); return 0; }
			plain(p, SHIP_LOST);
			if (write(v, p)) { entry(v, "ship_lost", plainChange(SHIP_LOST), s.name + " was lost in action (" + signed(SHIP_LOST) + ")", null, Vault.shipEvent("SHIP", s)); return SHIP_LOST; }
			return 0;
		}
	}
	/** She was restored after FTL's New Game wrote over her (heromedel, 5.55): what her loss took is given back. */
	static void restored(Vault v, Ship s, int taken) {
		synchronized (lock(v)) {
			if (taken == 0) return;
			Properties p = read(v);
			plain(p, -taken);
			if (write(v, p)) entry(v, "restored", plainChange(-taken), s.name + " was restored after FTL's New Game wrote over her (" + signed(-taken) + ")", null, Vault.shipEvent("SHIP", s));
		}
	}
	/**
	 * A crew expedition, scored as the game is (heromedel, 5.00): the pot a tenth, each crew member killed CREW_DIED, a good
	 * outcome (everyone successful or better) EVENT_GOOD, a bad one (nobody successful) EVENT_BAD. Nothing for items or prizes.
	 * {@code outcome}: 1 good, -1 bad, 0 neither.
	 */
	public static void expedition(Vault v, String what, int scrap, int died, int outcome) { synchronized (lock(v)) { expedition(v, what, scrap, died, 0, outcome); } }
	/** As above, with the crew taken captive. */
	public static void expedition(Vault v, String what, int scrap, int died, int taken, int outcome) {
		synchronized (lock(v)) {
			if (!shown()) return;
			Change ch = new Change();
			if (scrap / SCRAP_PER_POINT > 0) ch.earned(scrap + " scrap", scrap / SCRAP_PER_POINT);
			if (died > 0) ch.lost(died == 1 ? "a crew member killed" : died + " crew killed", died * CREW_DIED);
			if (taken > 0) ch.lost(taken == 1 ? "a crew member taken captive" : taken + " crew taken captive", taken * CAPTURED);
			if (outcome > 0) ch.earned("a good outcome", EVENT_GOOD);
			if (outcome < 0) ch.lost("a bad outcome", EVENT_BAD);
			if (ch.isEmpty()) return;
			Properties p = read(v);
			if (!counted(p)) { review(v); p = read(v); }
			List<String> why = ch.apply(p);
			if (write(v, p)) entry(v, "expedition", ch, "Expedition: " + what + " (" + signed(ch.moved) + ")", why);
		}
	}
	/** Crew taken captive on the board of jobs (the crew expeditions count them in their report's entry). */
	public static void captured(Vault v, List<String> names) {
		synchronized (lock(v)) {
			if (!shown() || names.isEmpty()) return;
			int points = names.size() * CAPTURED;
			Properties p = read(v);
			if (!counted(p)) { review(v); p = read(v); }
			plain(p, points);
			if (write(v, p)) entry(v, "captive", points, "Taken captive: " + String.join(", ", names) + " (" + signed(points) + ")", null);
		}
	}
	/** A captive brought home: the ransom paid. */
	public static void ransomed(Vault v, String name) {
		synchronized (lock(v)) {
			if (!shown()) return;
			Properties p = read(v);
			if (!counted(p)) { review(v); p = read(v); }
			Change ch = new Change().earned("Ransomed: " + name + " brought home", RANSOMED);
			String why = ch.apply(p).get(0);
			if (write(v, p)) entry(v, "ransomed", ch, why, null);
		}
	}
	/** Can this much be spent without going below zero (the fees reputation may pay; a plea, a promise and rest may go below)? */
	public static boolean canSpend(Vault v, int cost) { synchronized (lock(v)) { return shown() && (cost <= 0 || total(v) >= cost); } }
	/** A plea's new ship, answered for with the career's reputation: what it costs, and why. */
	public static void plea(Vault v, int cost, String why) { synchronized (lock(v)) { spend(v, cost, why); } }
	/** Reputation spent on anything the career pays for with it (a plea's ship, a promise of adventure): what it costs, and why. */
	public static void spend(Vault v, int cost, String why) {
		synchronized (lock(v)) {
			if (!shown() || cost <= 0) return;
			Properties p = read(v);
			if (!counted(p)) { review(v); p = read(v); }
			plain(p, -cost);
			if (write(v, p)) entry(v, "spent", -cost, why + " (" + signed(-cost) + ")", null);
		}
	}
	/** She won the last battle: the Rebel Flagship driven off. */
	public static void flagship(Vault v, String name) { flagship(v, name, null); }
	/** As above, at her difficulty's rate (her save as she won: 6.17) and sector 8's bonus. */
	public static void flagship(Vault v, String name, SavedGameState gs) {
		synchronized (lock(v)) {
			if (!shown()) return;
			Properties p = read(v);
			if (!counted(p)) { review(v); return; } // the review finds her in the Hall of Victors
			Change ch = new Change().inFtl(name + " drove off the Rebel Flagship", FLAGSHIP, shipLevel(gs), LAST_STAND); // hard rule 1: never that she destroyed it, as the museum and the Captain's Log say it
			String why = ch.apply(p).get(0);
			if (write(v, p)) entry(v, "flagship", ch, why, null, Vault.where(homeplanet.core.Event.of("SHIP").put("ship_name", name), gs));
		}
	}

	// ---- the first count: the service so far ----

	/**
	 * Reviews the career's service once: every ship the fleet has kept a record of (in the fleet, or gone from it),
	 * her FTL totals since she joined (her trade, or her commissioning), her loss if she was lost before the last stand,
	 * her victories over the Rebel Flagship, and the FTL achievements earned in the fleet's service. Older records can't tell
	 * rebel ships apart (they count as ships), nor events or the rebel fleet catching her (not counted).
	 */
	static void review(Vault v) {
		synchronized (lock(v)) {
			Properties p = read(v);
			if (counted(p)) return;
			setUnits(p, 0); // counted from nothing, ship by ship
			List<String> details = new ArrayList<String>();
			Map<String, Ship> inFleet = new LinkedHashMap<String, Ship>();
			for (Ship s : v.all()) if (s.state != Ship.State.STORAGE) inFleet.put(s.id, s);
			List<String> ids = new ArrayList<String>(inFleet.keySet());
			List<File> dirs = v.departedFolders();
			java.util.Collections.sort(dirs);
			for (File d : dirs) { String id = ShipStore.idOf(d); if (id != null && !ids.contains(id) && served(d)) ids.add(id); }
			for (String id : ids) {
				Ship s = inFleet.get(id);
				SavedGameState gs = s != null ? s.save() : lastSave(v.folderOfId(id));
				String name = s != null ? s.name : departedName(v, id);
				Change ch = new Change();
				TradeMark m = TradeMark.of(v, id);
				if (gs != null) {
					int journeys = m == null && s != null ? VoyageLog.journeys(v, s) : journeysSince(v, id, m); // each began in sector 1: not a jump
					int sectors = Math.max(0, VoyageLog.visited(v, id, gs) - (m == null ? 0 : m.sectors) - journeys);
					int ship = shipLevel(gs); // her difficulty; where each was earned isn't known now, so no sector bonus (6.17)
					if (sectors > 0) ch.inFtl(sectors + (sectors == 1 ? " sector" : " sectors"), sectors * SECTOR, ship, 0);
					int scrap = Math.max(0, gs.getTotalScrapCollected() - (m == null ? 0 : m.scrap));
					if (scrap / SCRAP_PER_POINT > 0) ch.inFtl(scrap + " scrap", scrap / SCRAP_PER_POINT, ship, 0);
					int defeated = Math.max(0, gs.getTotalShipsDefeated() - (m == null ? 0 : m.defeated));
					if (defeated > 0) ch.inFtl(defeated + (defeated == 1 ? " ship" : " ships") + " defeated", defeated * DEFEATED, ship, 0);
					int died = m != null || !gs.hasStateVar("lost_crew") ? 0 : gs.getStateVar("lost_crew"); // a traded ship's losses before she came aren't told apart
					if (died > 0) ch.lost(died + " crew lost", died * CREW_DIED);
					if (s != null) Props.of(gs).put(p, id, 0); // counted from here on
				}
				int won = homeplanet.parser.Museum.victories(v, id);
				if (won > 0) ch.inFtl(won == 1 ? "the Rebel Flagship driven off" : "the Rebel Flagship driven off " + homeplanet.model.Words.number(won) + " times", won * FLAGSHIP, shipLevel(gs), LAST_STAND); // in sector 8; hard rule 1: driven off, never defeated
				if (s == null && Vault.Fate.LOST.name().equals(fate(v, id)) && won == 0 && lastSectorOf(v, id) < LAST_STAND) ch.lost("lost in action", SHIP_LOST);
				if (ch.isEmpty()) continue;
				List<String> why = ch.apply(p);
				details.add(name + ": " + String.join(", ", why) + "  = " + signed(ch.moved));
			}
			List<String> earned = newAchievements(); // FTL's achievements earned in the fleet's service so far
			if (!earned.isEmpty()) {
				List<String> names = new ArrayList<String>();
				for (String id : earned) names.add(achievementName(id));
				Change ch = new Change().earned("Achievements", earned.size() * ACHIEVEMENT);
				ch.apply(p);
				details.add("Achievements: " + String.join(", ", names) + "  = " + signed(ch.moved));
				p.setProperty("achievements", String.join("|", earned));
			}
			p.setProperty("counted", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
			p.setProperty("rated", RATED); // counted at the rates from the start: never offered a re-evaluation (6.19)
			Change all = new Change(); // the review as one change: its whole total, and its exact one
			all.moved = num(p, "total"); all.movedUnits = units(p);
			if (write(v, p)) entry(v, "review", all, "Service record reviewed: the fleet's service so far", details);
		}
	}
	/**
	 * Enemy ships the career's ships have defeated (the rank letters, 5.60): every ship that served, in the fleet now or
	 * gone from it, each from her own save since she joined or her last trade, as the review counts them. A ship gone
	 * counts only with a fate recorded (lost, destroyed, traded away...): an uncommissioned ship sent to the other fleet
	 * leaves a history folder without one, and was never the career's.
	 */
	public static int defeatedInService(Vault v) {
		int n = 0;
		java.util.Set<String> inFleet = new java.util.HashSet<String>();
		for (Ship s : v.all()) {
			if (s.state == Ship.State.STORAGE) continue;
			inFleet.add(s.id);
			if (!v.ignoring(s)) n += defeatedSince(v, s.id, s.save()); // an ignored one isn't the career's ship (5.54)
		}
		for (File d : v.departedFolders()) {
			String id = ShipStore.idOf(d);
			if (id != null && !inFleet.contains(id) && ShipStore.fate(d) != null) n += defeatedSince(v, id, lastSave(d));
		}
		return n;
	}
	private static int defeatedSince(Vault v, String id, SavedGameState gs) {
		if (gs == null) return 0;
		TradeMark m = TradeMark.of(v, id);
		return Math.max(0, gs.getTotalShipsDefeated() - (m == null ? 0 : m.defeated));
	}
	/** A history folder of a ship that served (a voyage log or a kept save), not the Cargo Hold's. */
	private static boolean served(File d) {
		if (!ShipStore.notes(d, ShipStore.LAST).isEmpty()) return true;
		return (!ShipStore.versions(d, false).isEmpty() || !ShipStore.versions(d, true).isEmpty()) && ShipStore.fate(d) != null;
	}
	/** New Journeys since her trade (each starts from sector 1 again, which isn't a jump): all of them if never traded. */
	private static int journeysSince(Vault v, String id, TradeMark m) {
		int n = 0;
		for (homeplanet.core.EventLog.Entry e : homeplanet.core.EventLog.voyage(ShipStore.entries(v.folderOfId(id)), id)) { // her own log (5.76)
			if (!VoyageLog.newJourney(e)) continue;
			if (m == null || e.time.length() < 16 || e.time.substring(0, 16).compareTo(m.date) >= 0) n++;
		}
		return n + (m == null ? 1 : 0); // her first journey began in sector 1 as well
	}
	private static SavedGameState lastSave(File dir) {
		File newest = Vault.newestKept(dir); // her newest version, not a copy kept for a reason of its own (5.61)
		if (newest == null) return null;
		try { return HomePlanet.savedGameParser.readSavedGame(newest); } catch (Exception e) { return null; }
	}
	private static String fate(Vault v, String id) {
		String[] f = ShipStore.fate(v.folderOfId(id));
		return f == null ? "" : f[0];
	}
	private static String departedName(Vault v, String id) {
		String[] f = ShipStore.fate(v.folderOfId(id));
		return f != null && !f[1].isEmpty() ? f[1] : id;
	}
	private static int lastSectorOf(Vault v, String id) {
		return VoyageLog.lastSector(v, id);
	}

	// ---- re-evaluation (heromedel, 6.19): a score counted before the rates (6.13, 6.17), re-scored once if the player wants ----

	/** reputation.xml's mark of a score counted at the rates, or answered about: never offered again. */
	static final String RATED = "6.17";
	/** The offer: the score as it stands, and as its log re-scored at the rates would make it. */
	public static final class Reevaluation {
		public final int current, reevaluated;
		final long units;
		Reevaluation(int current, long units) { this.current = current; this.units = units; this.reevaluated = (int) Math.floorDiv(units, UNIT); }
	}
	/**
	 * The offer for this fleet, or null: no reputation shown, a Custom career's rate not chosen yet, counted at the rates
	 * already, answered already, or nothing would change (then marked, quietly, so it isn't worked out again).
	 */
	public static Reevaluation offer(Vault v) {
		synchronized (lock(v)) {
			if (!shown() || RATE_ASK.equals(rateRule())) return null;
			Properties p = read(v);
			if (!counted(p) || p.getProperty("rated") != null) return null;
			long u = rescored(v);
			Reevaluation r = new Reevaluation(num(p, "total"), u);
			if (r.reevaluated == r.current) { p.setProperty("rated", RATED); write(v, p); return null; }
			return r;
		}
	}
	/** The player's answer: re-evaluated (the total re-scored, the difference one entry in the log) or kept. Logged either way, never asked again. */
	public static void answer(Vault v, Reevaluation r, boolean reevaluate) {
		synchronized (lock(v)) {
			Properties p = read(v);
			if (p.getProperty("rated") != null) return;
			p.setProperty("rated", RATED);
			p.setProperty("reevaluated", Boolean.toString(reevaluate));
			if (reevaluate) {
				int was = num(p, "total");
				long before = units(p);
				setUnits(p, r.units);
				Change ch = plainChange(num(p, "total") - was);
				ch.movedUnits = r.units - before;
				if (write(v, p)) entry(v, "records", ch, "Records updated (" + signed(ch.moved) + ")", null);
			} else if (write(v, p)) {
				entry(v, "records", plainChange(0), "Records updated: the current score kept", null);
			}
		}
	}
	/**
	 * The fleet's reputation log re-scored at the rates (6.19): each earned piece of an entry written before them at the
	 * career's rate, and if earned in FTL at her difficulty's rate (her save now, a departed ship's as Easy) and the
	 * sector's bonus where the entry names her sector; losses, spending and anything unexplained as they were. An entry
	 * written at the career's rate already (6.13) gets her difficulty and sector only; one written at every rate (6.17), as it is.
	 */
	static long rescored(Vault v) {
		int career = 2 + rateLevel();
		Map<String, Integer> ships = new java.util.HashMap<String, Integer>();
		for (Ship s : v.all()) if (s.state != Ship.State.STORAGE) ships.put(s.name, shipLevel(s.save()));
		long total = 0;
		for (homeplanet.core.EventLog.Entry e : homeplanet.core.EventLog.sorted(homeplanet.core.EventLog.read(v))) {
			if (!e.kind.equals("REPUTATION") || !"reputation".equals(e.get("log"))) continue;
			int points;
			try { points = Integer.parseInt(e.get("points", "0").trim()); } catch (NumberFormatException x) { points = 0; }
			String why = e.get("why", e.human), reason = e.get("reason", "other");
			if ("other".equals(reason)) reason = reasonOf(why); // brought across from the old reputation.log (5.92): what it was, from its words
			boolean allRates = e.get("ship_rate") != null || RATED.equals(e.get("rates")) || "records".equals(reason), careerDone = e.get("rate") != null;
			if (allRates || reason.equals("spent") || reason.equals("captive") || reason.equals("ship_lost") || reason.equals("restored") || reason.equals("other")) { total += points * UNIT; continue; }
			int c = careerDone ? 2 : career; // the career's rate, doubled; 2 where it was counted already
			List<String> lines = new ArrayList<String>();
			if (reason.equals("review") || reason.equals("expedition")) { for (String[] kv : e.fields()) if (kv[0].startsWith("detail.")) lines.add(kv[1]); }
			else lines.add(why);
			long u = 0; int base = 0;
			for (String line : lines) {
				int colon = line.indexOf(": ");
				String who = colon > 0 ? line.substring(0, colon) : "";
				Integer ship = ships.get(who);
				boolean away = reason.equals("expedition") || reason.equals("achievement") || reason.equals("cruiser") || reason.equals("ransomed");
				int sl = away ? -1 : ship != null ? ship : 0; // -1: not in FTL (an achievement's name may say "sector": never read as a jump)
				java.util.regex.Matcher sm = java.util.regex.Pattern.compile("sector (\\d) reached").matcher(line);
				int at = sm.find() ? sectorIndex(Integer.parseInt(sm.group(1)) - 1) : 0;
				List<String[]> pcs = pieces(line);
				if (pcs.isEmpty() && reason.equals("review")) { // "Achievements: ...  = +30": earned away from FTL
					java.util.regex.Matcher am = java.util.regex.Pattern.compile("=\\s*\\+(\\d+)").matcher(line);
					if (am.find()) { int n = Integer.parseInt(am.group(1)); base += n; u += n * (UNIT / 2) * c; }
					continue;
				}
				for (String[] pc : pcs) {
					int n = Integer.parseInt(pc[1]);
					base += n;
					String t = pc[0].toLowerCase();
					boolean ftl = sl >= 0 && (t.contains("sector") || t.contains("defeated") || t.contains("scrap") || t.contains("outcome") || t.contains("flagship"));
					if (reason.equals("flagship")) { ftl = true; sl = ships.containsKey(why.split(" drove off")[0]) ? ships.get(why.split(" drove off")[0]) : 0; }
					if (n <= 0) u += n * UNIT; // a loss, as it is
					else if (ftl) u += n * (UNIT / 80) * (4 + sl) * (10 + (reason.equals("flagship") ? LAST_STAND : at)) * c;
					else u += n * (UNIT / 2) * c;
				}
			}
			u += (points - base) * UNIT; // whatever the pieces don't explain, as it was
			total += u;
		}
		return total;
	}

	/** What an entry from the old reputation.log was, from its words (as the tally reads them); "other" if they don't say. */
	static String reasonOf(String why) {
		if (why == null) return "other";
		for (String sp : SPENT_STARTS) if (why.startsWith(sp)) return "spent";
		if (why.startsWith("Service record reviewed")) return "review";
		if (why.startsWith("Expedition:")) return "expedition";
		if (why.startsWith("An achievement") || why.matches("\\d+ achievements:.*")) return "achievement";
		if (why.startsWith("Ransomed:")) return "ransomed";
		if (why.startsWith("Taken captive:")) return "captive";
		if (why.contains(" drove off the Rebel Flagship")) return "flagship";
		if (why.contains(" was lost in action")) return "ship_lost";
		if (why.contains(" was restored after")) return "restored";
		if (why.contains(" unlocked (+")) return "cruiser";
		if (why.matches("[^:]+: .*\\([+\u2212-]\\d+\\).*")) return "voyage";
		return "other";
	}

	// ---- a ship's count ----

	/** What the count keeps of a ship: FTL's totals at her last count, where she was, who was aboard, who she fought. */
	private static final class Props {
		int sector = -1, collected, defeated, lost, rest, beacon = -1, hull, scrapNow, ammo;
		String crew = "", enemy = "", items = "";
		/** At her beacon: a store; the rebel fleet. */
		boolean store, rebel;
		private Props() { }
		Props(Properties p, String id) {
			sector = Store.num(p, id + ".sector", -1);
			collected = num(p, id + ".collected");
			defeated = num(p, id + ".defeated");
			lost = num(p, id + ".lost");
			rest = num(p, id + ".rest");
			crew = p.getProperty(id + ".crew", "");
			enemy = p.getProperty(id + ".enemy", "");
			beacon = Store.num(p, id + ".beacon", -1);
			hull = num(p, id + ".hull");
			scrapNow = num(p, id + ".scrapNow");
			ammo = num(p, id + ".ammo");
			items = p.getProperty(id + ".items", "");
			store = "true".equals(p.getProperty(id + ".store"));
			rebel = "true".equals(p.getProperty(id + ".rebel"));
		}
		boolean known() { return sector >= 0; }
		static Props of(SavedGameState gs) {
			Props x = new Props();
			x.sector = gs.getSectorNumber();
			x.collected = gs.getTotalScrapCollected();
			x.defeated = gs.getTotalShipsDefeated();
			x.lost = gs.hasStateVar("lost_crew") ? gs.getStateVar("lost_crew") : 0;
			List<String> names = new ArrayList<String>();
			for (CrewState c : SaveHelper.getOwnCrew(gs.getPlayerShip())) names.add(c.getName());
			x.crew = String.join("|", names);
			x.enemy = gs.getNearbyShip() == null ? "" : gs.getNearbyShip().getShipBlueprintId();
			ShipState ship = gs.getPlayerShip();
			x.beacon = gs.getCurrentBeaconId();
			x.hull = ship.getHullAmt();
			x.scrapNow = ship.getScrapAmt();
			x.ammo = ship.getMissilesAmt() + ship.getDronePartsAmt();
			List<String> gear = homeplanet.parser.SaveHelper.gearAndCargo(gs);
			x.items = String.join("|", gear);
			List<BeaconState> beacons = gs.getBeaconList();
			BeaconState here = beacons != null && x.beacon >= 0 && x.beacon < beacons.size() ? beacons.get(x.beacon) : null;
			x.store = here != null && here.getStore() != null;
			x.rebel = here != null && (here.getFleetPresence() == FleetPresence.REBEL || here.getFleetPresence() == FleetPresence.BOTH);
			return x;
		}
		void put(Properties p, String id, int rest) {
			p.setProperty(id + ".sector", Integer.toString(sector));
			p.setProperty(id + ".collected", Integer.toString(collected));
			p.setProperty(id + ".defeated", Integer.toString(defeated));
			p.setProperty(id + ".lost", Integer.toString(lost));
			p.setProperty(id + ".rest", Integer.toString(rest));
			p.setProperty(id + ".crew", crew);
			p.setProperty(id + ".enemy", enemy == null ? "" : enemy);
			p.setProperty(id + ".beacon", Integer.toString(beacon));
			p.setProperty(id + ".hull", Integer.toString(hull));
			p.setProperty(id + ".scrapNow", Integer.toString(scrapNow));
			p.setProperty(id + ".ammo", Integer.toString(ammo));
			p.setProperty(id + ".items", items);
			p.setProperty(id + ".store", Boolean.toString(store));
			p.setProperty(id + ".rebel", Boolean.toString(rebel));
		}
		static void forget(Properties p, String id) {
			for (String k : new String[] {"sector", "collected", "defeated", "lost", "rest", "crew", "enemy", "beacon", "hull", "scrapNow", "ammo", "items", "store", "rebel"}) p.remove(id + "." + k);
		}
	}
	/** FTL's rebel ships: the rebellion's crewed ships and its automated scouts. */
	static boolean rebel(String blueprint) {
		return blueprint != null && (blueprint.startsWith("REBEL_") || blueprint.startsWith("AUTO_"));
	}
	/** The names on the first list that aren't on the second (counting repeats). */
	private static List<String> gone(String before, String after) {
		List<String> left = new ArrayList<String>(), out = new ArrayList<String>();
		for (String x : after.split("\\|")) if (!x.isEmpty()) left.add(x);
		for (String x : before.split("\\|")) if (!x.isEmpty() && !left.remove(x)) out.add(x);
		return out;
	}

	// ---- files ----

	private static boolean counted(Properties p) { return p.getProperty("counted") != null; }
	private static Properties read(Vault v) { return Store.read(Store.file(v.root, FILE)); }
	private static boolean write(Vault v, Properties p) {
		try {
			Store.write(Store.file(v.root, FILE), p, "The career's reputation: the total, and where each ship's count stands");
			return true;
		} catch (IOException e) {
			log.warn("Could not keep the reputation: {}", e.toString());
			return false;
		}
	}
	// ---- where it came from: the log's pieces, pooled (heromedel, 5.15: a running tally under the log) ----

	/** The pools, in the order the tally shows them. */
	public static final String[] POOLS = {"Travel", "Combat", "Crew", "Scrap", "Events", "Achievements", "Spent", "Other"};
	/** How the log's spending entries begin (what reputation paid for): the whole entry is Spent. */
	private static final String[] SPENT_STARTS = {"A new ship on your plea", "A promise of adventure", "Rested in quarters", "A new journey plotted",
			"Stripping ", "The Dry Dock:", "A plea"};
	private static final java.util.regex.Pattern PIECE = java.util.regex.Pattern.compile("([^,:;(]*)\\(([+\u2212-])(\\d+)\\)");
	/**
	 * The reputation by pool, read from the log: each entry's pieces ("sector 3 reached (+6)", "a crew member killed
	 * (-10)") sorted by what they say, so a jump that did three things counts in three pools. The pools add up to the
	 * log's own changes; pools at zero are left out.
	 */
	public static Map<String, Integer> tally(Vault v) {
		synchronized (lock(v)) {
			Map<String, Integer> pools = new LinkedHashMap<String, Integer>();
			for (String k : POOLS) pools.put(k, 0);
			String header = null; int points = 0; List<String> details = new ArrayList<String>();
			for (String line : log(v).split("\r?\n")) {
				if (line.startsWith("  ")) { if (header != null) details.add(line.trim()); continue; }
				if (header != null) pool(pools, header, points, details);
				header = null; details.clear();
				String[] w = line.split("  ", 3); // date time, the change, why
				if (w.length < 3) continue;
				try { points = Integer.parseInt(w[1].trim().replace('\u2212', '-').replace("+", "")); } catch (NumberFormatException e) { continue; }
				header = w[2];
			}
			if (header != null) pool(pools, header, points, details); // the last entry
			Map<String, Integer> out = new LinkedHashMap<String, Integer>();
			for (Map.Entry<String, Integer> e : pools.entrySet()) if (e.getValue() != 0) out.put(e.getKey(), e.getValue());
			return out;
		}
	}
	private static void pool(Map<String, Integer> pools, String header, int points, List<String> details) {
		for (String s : SPENT_STARTS) if (header.startsWith(s)) { add(pools, "Spent", points); return; }
		if (header.startsWith("An achievement") || header.matches("\\d+ achievements:.*")) { add(pools, "Achievements", points); return; }
		if (header.startsWith("Taken captive:") || header.startsWith("Ransomed:")) { add(pools, "Crew", points); return; }
		List<String[]> pieces = new ArrayList<String[]>();
		for (String d : details) pieces.addAll(pieces(d));
		if (pieces.isEmpty()) pieces = pieces(header); // the details carry the pieces when they have them (the header then shows the sum)
		int counted = 0;
		for (String[] pc : pieces) {
			int n = Integer.parseInt(pc[1]);
			String t = pc[0].toLowerCase();
			String k = t.contains("caught by the rebel fleet") || t.contains("sector") ? "Travel"
					: t.contains("defeated") || t.contains("lost in action") || t.contains("flagship") ? "Combat"
					: t.contains("died") || t.contains("crew lost") || t.contains("killed") || t.contains("captive") ? "Crew"
					: t.contains("scrap") ? "Scrap"
					: t.contains("outcome") ? "Events"
					: n < 0 ? "Spent" : "Other";
			add(pools, k, n);
			counted += n;
		}
		if (counted != points) add(pools, points < 0 && pieces.isEmpty() ? "Spent" : "Other", points - counted); // whatever the pieces don't explain
	}
	private static List<String[]> pieces(String s) {
		List<String[]> out = new ArrayList<String[]>();
		java.util.regex.Matcher m = PIECE.matcher(s);
		while (m.find()) out.add(new String[] {m.group(1).trim(), (m.group(2).equals("+") ? "" : "-") + m.group(3)});
		return out;
	}
	private static void add(Map<String, Integer> pools, String k, int n) { pools.put(k, pools.get(k) + n); }

	/** One entry in the reputation log, in the station log's form: its time, the change as its tag, why, and details under it. */
	private static void entry(Vault v, int points, String why, List<String> details) { entry(v, "other", points, why, details); }
	/** As above, with what the change was for (the event's reason field: achievement, cruiser, voyage, ship_lost, restored, expedition, captive, ransomed, spent, flagship, review). */
	private static void entry(Vault v, String reason, int points, String why, List<String> details) { entry(v, reason, plainChange(points), why, details); }
	/** A change of points as they are, for an entry. */
	private static Change plainChange(int points) {
		Change plain = new Change();
		plain.moved = points; plain.movedUnits = points * UNIT;
		return plain;
	}
	/** As above, from a {@link Change}: the whole points it moved the total by, and on the machine line the exact change and its rates (6.13, 6.17). */
	private static void entry(Vault v, String reason, Change ch, String why, List<String> details) { entry(v, reason, ch, why, details, null); }
	/** As above, about a ship: her id, name, sector and difficulty on the machine line too (6.18). */
	private static void entry(Vault v, String reason, Change ch, String why, List<String> details, homeplanet.core.Event ship) {
		Properties p = read(v); // the event log alone (5.93): reputation.log and the master log's copy are no longer written
		homeplanet.core.Event e = homeplanet.core.Event.of("REPUTATION").put("log", "reputation").put("reason", reason).put("points", ch.moved)
				.put("exact", unitWords(ch.movedUnits)).put("rate", RATE[rateLevel()]).put("rates", RATED); // written at every rate (6.17): a re-evaluation leaves it as it is
		if (ship != null) for (String k : new String[] {"ship", "ship_name", "ship_id", "ship_state", "stranger", "ship_sector", "ship_difficulty"}) if (ship.get(k) != null) e.put(k, ship.get(k));
		if (ch.ship >= 0) e.put("ship_rate", SHIP_RATE[ch.ship]).put("sector_rate", sectorRate(ch.sector));
		homeplanet.core.EventLog.write(v, e.put("total", num(p, "total")).put("total_exact", unitWords(units(p))).put("why", why).details(details).human(why));
	}
	/** Ten-thousandths as a number to read: 9, -4, 4.5, 2.0625. */
	private static String unitWords(long u) {
		java.math.BigDecimal d = java.math.BigDecimal.valueOf(u, 4).stripTrailingZeros();
		return d.scale() < 0 ? d.setScale(0).toPlainString() : d.toPlainString();
	}
	public static String signed(int n) { return n > 0 ? "+" + n : n < 0 ? "−" + (-n) : "0"; }
	private static int num(Properties p, String k) { return Store.num(p, k, 0); }
}
