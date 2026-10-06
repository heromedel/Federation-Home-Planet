package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
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
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.FleetPresence;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;

import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;
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

	static final String FILE = "reputation.txt", LOG = "reputation.log";

	// ---- the scoring (docs/ROADMAP.md) ----
	public static final int SECTOR = 6, DEFEATED = 4, REBEL_DEFEATED = 6, FLAGSHIP = 100;
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

	/** Does the fleet in use have a reputation (Settings' Reputation rule, always on in Immersive Mode)? */
	public static boolean shown() { return Vault.isOpen() && HomePlanet.reputation(); }

	/** The fleet's reputation, its service reviewed first if it never was, and any new FTL achievements counted. */
	public static synchronized int total(Vault v) {
		Properties p = read(v);
		if (!counted(p)) { review(v); p = read(v); }
		else if (shown()) { achievements(v, p); p = read(v); }
		return num(p, "total");
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
		int pts = fresh.size() * ACHIEVEMENT;
		p.setProperty("total", Integer.toString(num(p, "total") + pts));
		if (write(v, p)) entry(v, pts, (fresh.size() == 1 ? "An achievement: " : fresh.size() + " achievements: ") + String.join(", ", names) + " (+" + pts + ")", null);
	}
	/** FTL's real achievements earned since the fleet's record began (empty if the profile can't be read). */
	private static List<String> newAchievements() {
		List<String> out = new ArrayList<String>();
		try {
			homeplanet.parser.Unlocks u = homeplanet.parser.Unlocks.read();
			if (u.problem() != null) return out;
			for (String id : homeplanet.parser.UnlockGrants.newAchievements(u)) {
				net.blerf.ftl.xml.Achievement a = net.blerf.ftl.parser.DataManager.get().getAchievement(id);
				if (a != null && !a.isVictory() && !a.isQuest()) out.add(id);
			}
		} catch (Exception e) {
			log.warn("Could not read the FTL profile's achievements: {}", e.toString());
		}
		return out;
	}
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
	/** The Career Reputation Log, oldest first (empty if none yet). */
	public static String log(Vault v) {
		File f = new File(v.root, LOG);
		try { return f.isFile() ? new String(SafeFiles.read(f), StandardCharsets.UTF_8) : ""; }
		catch (IOException e) { return "The Home Planet Station could not read the reputation log (" + f + "): " + e.getMessage(); }
	}

	// ---- counting as FTL plays ----

	/** FTL has written her save (the voyage log's look): what she did since her last count scores. */
	static synchronized void look(Vault v, Ship s, SavedGameState gs) {
		if (s == null || gs == null || s.state == Ship.State.STORAGE) return;
		Properties p = read(v);
		if (!shown()) { if (counted(p)) rebase(v, s, gs); return; } // careers switched off: her count moves on, so nothing done meanwhile scores later
		if (!counted(p)) { review(v); return; } // the review counts her as she is now
		Props was = new Props(p, s.id);
		Props now = Props.of(gs);
		if (!was.known()) { now.put(p, s.id, 0); write(v, p); return; } // a ship the count hasn't met: she starts here
		List<String> why = new ArrayList<String>();
		int points = 0;
		boolean lastStand = now.sector >= LAST_STAND || was.sector >= LAST_STAND;
		if (now.sector > was.sector) {
			int n = now.sector - was.sector;
			points += n * SECTOR;
			why.add((n == 1 ? "sector " + (now.sector + 1) + " reached" : n + " sectors further") + " (+" + n * SECTOR + ")");
		}
		int scrap = Math.max(0, now.collected - was.collected) + was.rest;
		int fromScrap = scrap / SCRAP_PER_POINT;
		if (fromScrap > 0) { points += fromScrap; why.add((scrap - was.rest) + " scrap collected (+" + fromScrap + ")"); }
		int defeated = Math.max(0, now.defeated - was.defeated);
		if (defeated > 0) {
			boolean rebel = rebel(now.enemy) || rebel(was.enemy);
			int pts = defeated * DEFEATED + (rebel ? REBEL_DEFEATED - DEFEATED : 0);
			points += pts;
			why.add((rebel ? (defeated == 1 ? "a rebel ship" : defeated + " ships, a rebel among them") : defeated == 1 ? "a ship" : defeated + " ships") + " defeated (+" + pts + ")");
		}
		// a death: FTL's lost-crew count went up and the crew member is gone (a clone came back; a dismissal isn't a death)
		int died = Math.min(Math.max(0, now.lost - was.lost), gone(was.crew, now.crew).size());
		if (died > 0 && !lastStand) {
			points += died * CREW_DIED;
			List<String> names = gone(was.crew, now.crew);
			why.add((died == 1 ? names.get(0) + " died" : died + " crew died") + " (" + signed(died * CREW_DIED) + ")");
		}
		// caught by the rebel fleet: at a beacon it holds that she wasn't caught at already (never in the last stand)
		boolean moved = now.beacon != was.beacon || now.sector != was.sector;
		if (now.rebel && (moved || !was.rebel) && !lastStand) {
			points += CAUGHT;
			why.add("caught by the rebel fleet (" + signed(CAUGHT) + ")");
		}
		// an event's outcome: a jump within the sector to a beacon with no fight, no ship and no store, nor a store left behind
		if (moved && now.sector == was.sector && defeated == 0 && now.enemy.isEmpty() && !now.store && !was.store) {
			int outcome = outcome(was, now, died);
			if (outcome > 0) { points += EVENT_GOOD; why.add("a good outcome (+" + EVENT_GOOD + ")"); }
			else if (outcome < 0 && !lastStand) { points += EVENT_BAD; why.add("a bad outcome (" + signed(EVENT_BAD) + ")"); }
		}
		now.put(p, s.id, scrap % SCRAP_PER_POINT);
		if (points != 0 || !why.isEmpty()) {
			p.setProperty("total", Integer.toString(num(p, "total") + points));
			if (write(v, p)) entry(v, points, s.name + ": " + String.join(", ", why), null);
		} else {
			write(v, p);
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
	static synchronized void rebase(Vault v, Ship s, SavedGameState gs) {
		if (s == null || gs == null || s.state == Ship.State.STORAGE) return;
		Properties p = read(v);
		if (!counted(p)) return; // the review will count her as she is (or there's no career, and never was)
		Props.of(gs).put(p, s.id, new Props(p, s.id).rest);
		write(v, p);
	}
	/** She was lost in action: the fleet's loss, unless it was the last stand (sector 8). */
	static synchronized int lost(Vault v, Ship s) {
		if (s == null) return 0;
		Properties p = read(v);
		if (!counted(p)) return 0; // the review counts her loss from her fate
		Props was = new Props(p, s.id);
		int sector = was.known() ? was.sector : VoyageLog.lastSector(v, s);
		Props.forget(p, s.id);
		if (sector >= LAST_STAND || !shown()) { write(v, p); return 0; }
		p.setProperty("total", Integer.toString(num(p, "total") + SHIP_LOST));
		if (write(v, p)) { entry(v, SHIP_LOST, s.name + " was lost in action (" + signed(SHIP_LOST) + ")", null); return SHIP_LOST; }
		return 0;
	}
	/** She was restored after FTL's New Game wrote over her (heromedel, 5.55): what her loss took is given back. */
	static synchronized void restored(Vault v, Ship s, int taken) {
		if (taken == 0) return;
		Properties p = read(v);
		p.setProperty("total", Integer.toString(num(p, "total") - taken));
		if (write(v, p)) entry(v, -taken, s.name + " was restored after FTL's New Game wrote over her (" + signed(-taken) + ")", null);
	}
	/**
	 * A crew expedition, scored as the game is (heromedel, 5.00): the pot a tenth, each crew member killed CREW_DIED, a good
	 * outcome (everyone successful or better) EVENT_GOOD, a bad one (nobody successful) EVENT_BAD. Nothing for items or prizes.
	 * {@code outcome}: 1 good, -1 bad, 0 neither.
	 */
	public static synchronized void expedition(Vault v, String what, int scrap, int died, int outcome) { expedition(v, what, scrap, died, 0, outcome); }
	/** As above, with the crew taken captive. */
	public static synchronized void expedition(Vault v, String what, int scrap, int died, int taken, int outcome) {
		if (!shown()) return;
		int points = scrap / SCRAP_PER_POINT + died * CREW_DIED + taken * CAPTURED + (outcome > 0 ? EVENT_GOOD : outcome < 0 ? EVENT_BAD : 0);
		List<String> why = new ArrayList<String>();
		if (scrap / SCRAP_PER_POINT > 0) why.add(scrap + " scrap (+" + scrap / SCRAP_PER_POINT + ")");
		if (died > 0) why.add((died == 1 ? "a crew member killed" : died + " crew killed") + " (" + signed(died * CREW_DIED) + ")");
		if (taken > 0) why.add((taken == 1 ? "a crew member taken captive" : taken + " crew taken captive") + " (" + signed(taken * CAPTURED) + ")");
		if (outcome > 0) why.add("a good outcome (+" + EVENT_GOOD + ")");
		if (outcome < 0) why.add("a bad outcome (" + EVENT_BAD + ")");
		if (points == 0 && why.isEmpty()) return;
		Properties p = read(v);
		if (!counted(p)) { review(v); p = read(v); }
		p.setProperty("total", Integer.toString(num(p, "total") + points));
		if (write(v, p)) entry(v, points, "Expedition: " + what + " (" + signed(points) + ")", why);
	}
	/** Crew taken captive on the board of jobs (the crew expeditions count them in their report's entry). */
	public static synchronized void captured(Vault v, List<String> names) {
		if (!shown() || names.isEmpty()) return;
		int points = names.size() * CAPTURED;
		Properties p = read(v);
		if (!counted(p)) { review(v); p = read(v); }
		p.setProperty("total", Integer.toString(num(p, "total") + points));
		if (write(v, p)) entry(v, points, "Taken captive: " + String.join(", ", names) + " (" + signed(points) + ")", null);
	}
	/** A captive brought home: the ransom paid. */
	public static synchronized void ransomed(Vault v, String name) {
		if (!shown()) return;
		Properties p = read(v);
		if (!counted(p)) { review(v); p = read(v); }
		p.setProperty("total", Integer.toString(num(p, "total") + RANSOMED));
		if (write(v, p)) entry(v, RANSOMED, "Ransomed: " + name + " brought home (" + signed(RANSOMED) + ")", null);
	}
	/** Can this much be spent without going below zero (the fees reputation may pay; a plea, a promise and rest may go below)? */
	public static synchronized boolean canSpend(Vault v, int cost) { return shown() && (cost <= 0 || total(v) >= cost); }
	/** A plea's new ship, answered for with the career's reputation: what it costs, and why. */
	public static synchronized void plea(Vault v, int cost, String why) { spend(v, cost, why); }
	/** Reputation spent on anything the career pays for with it (a plea's ship, a promise of adventure): what it costs, and why. */
	public static synchronized void spend(Vault v, int cost, String why) {
		if (!shown() || cost <= 0) return;
		Properties p = read(v);
		if (!counted(p)) { review(v); p = read(v); }
		p.setProperty("total", Integer.toString(num(p, "total") - cost));
		if (write(v, p)) entry(v, -cost, why + " (" + signed(-cost) + ")", null);
	}
	/** She won the last battle: the Rebel Flagship defeated. */
	public static synchronized void flagship(Vault v, String name) {
		if (!shown()) return;
		Properties p = read(v);
		if (!counted(p)) { review(v); return; } // the review finds her in the Hall of Victors
		p.setProperty("total", Integer.toString(num(p, "total") + FLAGSHIP));
		if (write(v, p)) entry(v, FLAGSHIP, name + " defeated the Rebel Flagship (+" + FLAGSHIP + ")", null);
	}

	// ---- the first count: the service so far ----

	/**
	 * Reviews the career's service once: every ship the fleet has kept a record of (in the fleet, or gone from it),
	 * her FTL totals since she joined (her trade, or her commissioning), her loss if she was lost before the last stand,
	 * her victories over the Rebel Flagship, and the FTL achievements earned in the fleet's service. Older records can't tell
	 * rebel ships apart (they count as ships), nor events or the rebel fleet catching her (not counted).
	 */
	static synchronized void review(Vault v) {
		Properties p = read(v);
		if (counted(p)) return;
		int total = 0;
		List<String> details = new ArrayList<String>();
		Map<String, Ship> inFleet = new LinkedHashMap<String, Ship>();
		for (Ship s : v.all()) if (s.state != Ship.State.STORAGE) inFleet.put(s.id, s);
		List<String> ids = new ArrayList<String>(inFleet.keySet());
		File[] dirs = v.historyDir().listFiles();
		if (dirs != null) {
			java.util.Arrays.sort(dirs);
			for (File d : dirs) if (d.isDirectory() && !ids.contains(d.getName()) && served(d)) ids.add(d.getName());
		}
		for (String id : ids) {
			Ship s = inFleet.get(id);
			SavedGameState gs = s != null ? s.save() : lastSave(new File(v.historyDir(), id));
			String name = s != null ? s.name : departedName(v, id);
			List<String> why = new ArrayList<String>();
			int pts = 0;
			TradeMark m = TradeMark.of(v, id);
			if (gs != null) {
				int journeys = m == null && s != null ? VoyageLog.journeys(v, s) : journeysSince(v, id, m); // each began in sector 1: not a jump
				int sectors = Math.max(0, VoyageLog.visited(v, id, gs) - (m == null ? 0 : m.sectors) - journeys);
				if (sectors > 0) { pts += sectors * SECTOR; why.add(sectors + (sectors == 1 ? " sector" : " sectors") + " (+" + sectors * SECTOR + ")"); }
				int scrap = Math.max(0, gs.getTotalScrapCollected() - (m == null ? 0 : m.scrap));
				if (scrap / SCRAP_PER_POINT > 0) { pts += scrap / SCRAP_PER_POINT; why.add(scrap + " scrap (+" + scrap / SCRAP_PER_POINT + ")"); }
				int defeated = Math.max(0, gs.getTotalShipsDefeated() - (m == null ? 0 : m.defeated));
				if (defeated > 0) { pts += defeated * DEFEATED; why.add(defeated + (defeated == 1 ? " ship" : " ships") + " defeated (+" + defeated * DEFEATED + ")"); }
				int died = m != null || !gs.hasStateVar("lost_crew") ? 0 : gs.getStateVar("lost_crew"); // a traded ship's losses before she came aren't told apart
				if (died > 0) { pts += died * CREW_DIED; why.add(died + " crew lost (" + signed(died * CREW_DIED) + ")"); }
				if (s != null) Props.of(gs).put(p, id, 0); // counted from here on
			}
			int won = homeplanet.parser.Museum.victories(v, id);
			if (won > 0) { pts += won * FLAGSHIP; why.add((won == 1 ? "the Rebel Flagship defeated" : "the Rebel Flagship defeated " + won + " times") + " (+" + won * FLAGSHIP + ")"); }
			if (s == null && Vault.Fate.LOST.name().equals(fate(v, id)) && won == 0 && lastSectorOf(v, id) < LAST_STAND) {
				pts += SHIP_LOST;
				why.add("lost in action (" + signed(SHIP_LOST) + ")");
			}
			if (why.isEmpty()) continue;
			total += pts;
			details.add(name + ": " + String.join(", ", why) + "  = " + signed(pts));
		}
		List<String> earned = newAchievements(); // FTL's achievements earned in the fleet's service so far
		if (!earned.isEmpty()) {
			List<String> names = new ArrayList<String>();
			for (String id : earned) names.add(achievementName(id));
			total += earned.size() * ACHIEVEMENT;
			details.add("Achievements: " + String.join(", ", names) + "  = " + signed(earned.size() * ACHIEVEMENT));
			p.setProperty("achievements", String.join("|", earned));
		}
		p.setProperty("counted", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
		p.setProperty("total", Integer.toString(total));
		if (write(v, p)) entry(v, total, "Service record reviewed: the fleet's service so far", details);
	}
	/** A history folder of a ship that served (a voyage log or a kept save), not the Cargo Hold's. */
	private static boolean served(File d) {
		if (new File(d, VoyageLog.LAST).isFile()) return true;
		File[] saves = d.listFiles(new java.io.FileFilter() { public boolean accept(File x) { return x.getName().endsWith(".sav"); } });
		return saves != null && saves.length > 0 && new File(d, "fate.txt").isFile();
	}
	/** New Journeys since her trade (each starts from sector 1 again, which isn't a jump): all of them if never traded. */
	private static int journeysSince(Vault v, String id, TradeMark m) {
		int n = 0;
		for (String line : VoyageLog.read(v, id).split("\r?\n")) {
			if (!line.endsWith(VoyageLog.NEW_JOURNEY)) continue;
			if (m == null || line.length() < 16 || line.substring(0, 16).compareTo(m.date) >= 0) n++;
		}
		return n + (m == null ? 1 : 0); // her first journey began in sector 1 as well
	}
	private static SavedGameState lastSave(File dir) {
		File[] saves = dir.listFiles(new java.io.FileFilter() { public boolean accept(File x) { return x.isFile() && x.getName().endsWith(".sav"); } });
		if (saves == null || saves.length == 0) return null;
		File newest = saves[0];
		for (File f : saves) if (f.lastModified() > newest.lastModified()) newest = f;
		try { return HomePlanet.savedGameParser.readSavedGame(newest); } catch (Exception e) { return null; }
	}
	private static String fate(Vault v, String id) {
		try { return new String(SafeFiles.read(new File(new File(v.historyDir(), id), "fate.txt")), StandardCharsets.UTF_8).split("\n")[0].trim(); }
		catch (Exception e) { return ""; }
	}
	private static String departedName(Vault v, String id) {
		try {
			String[] l = new String(SafeFiles.read(new File(new File(v.historyDir(), id), "fate.txt")), StandardCharsets.UTF_8).split("\n");
			if (l.length > 1 && !l[1].trim().isEmpty()) return l[1].trim();
		} catch (Exception e) { }
		return id;
	}
	private static int lastSectorOf(Vault v, String id) {
		Properties p = new Properties();
		File f = new File(new File(v.historyDir(), id), VoyageLog.LAST);
		try { if (f.isFile()) p.load(new StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); } catch (IOException e) { }
		return num(p, "sector");
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
			sector = num(p, id + ".sector", -1);
			collected = num(p, id + ".collected");
			defeated = num(p, id + ".defeated");
			lost = num(p, id + ".lost");
			rest = num(p, id + ".rest");
			crew = p.getProperty(id + ".crew", "");
			enemy = p.getProperty(id + ".enemy", "");
			beacon = num(p, id + ".beacon", -1);
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
			List<String> gear = new ArrayList<String>();
			for (WeaponState w : ship.getWeaponList()) gear.add(w.getWeaponId());
			for (DroneState d : ship.getDroneList()) gear.add(d.getDroneId());
			gear.addAll(ship.getAugmentIdList());
			gear.addAll(gs.getCargoIdList());
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
	private static Properties read(Vault v) {
		Properties p = new Properties();
		File f = new File(v.root, FILE);
		if (!f.isFile()) return p;
		try { p.load(new StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); }
		return p;
	}
	private static boolean write(Vault v, Properties p) {
		try {
			StringWriter w = new StringWriter();
			p.store(w, "The career's reputation: the total, and where each ship's count stands");
			SafeFiles.writeText(new File(v.root, FILE), w.toString(), false);
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
	public static synchronized Map<String, Integer> tally(Vault v) {
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
	private static void entry(Vault v, int points, String why, List<String> details) {
		StringBuilder sb = new StringBuilder(log(v));
		sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())).append("  ").append(signed(points)).append("  ").append(why).append('\n');
		if (details != null) for (String d : details) sb.append("  ").append(d).append('\n');
		try { SafeFiles.writeText(new File(v.root, LOG), sb.toString(), false); }
		catch (IOException e) { log.warn("Could not write the reputation log: {}", e.toString()); }
		StringBuilder t = new StringBuilder(signed(points) + "  " + why);
		if (details != null) for (String d : details) t.append("\n").append(d);
		MasterLog.entry(v, "reputation", t.toString());
	}
	public static String signed(int n) { return n > 0 ? "+" + n : n < 0 ? "−" + (-n) : "0"; }
	private static int num(Properties p, String k) { return num(p, k, 0); }
	private static int num(Properties p, String k, int dflt) {
		try { return Integer.parseInt(p.getProperty(k, "").trim()); } catch (NumberFormatException e) { return dflt; }
	}
}
