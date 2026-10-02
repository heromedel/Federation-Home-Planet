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

import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

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
	/** FTL's sector 8 (0 is the first): nothing is lost there. */
	static final int LAST_STAND = 7;

	/** Does the fleet in use have a reputation (a career runs in it)? */
	public static boolean shown() { return Vault.isOpen() && HomePlanet.career(); }

	/** The fleet's reputation, its service reviewed first if it never was. */
	public static synchronized int total(Vault v) {
		Properties p = read(v);
		if (!counted(p)) { review(v); p = read(v); }
		return num(p, "total");
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
		now.put(p, s.id, scrap % SCRAP_PER_POINT);
		if (points != 0 || !why.isEmpty()) {
			p.setProperty("total", Integer.toString(num(p, "total") + points));
			if (write(v, p)) entry(v, points, s.name + ": " + String.join(", ", why), null);
		} else {
			write(v, p);
		}
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
	static synchronized void lost(Vault v, Ship s) {
		if (s == null) return;
		Properties p = read(v);
		if (!counted(p)) return; // the review counts her loss from her fate
		Props was = new Props(p, s.id);
		int sector = was.known() ? was.sector : VoyageLog.lastSector(v, s);
		Props.forget(p, s.id);
		if (sector >= LAST_STAND || !shown()) { write(v, p); return; }
		p.setProperty("total", Integer.toString(num(p, "total") + SHIP_LOST));
		if (write(v, p)) entry(v, SHIP_LOST, s.name + " was lost in action (" + signed(SHIP_LOST) + ")", null);
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
	 * and her victories over the Rebel Flagship. Rebel ships can't be told apart in older records: they count as ships.
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
		int sector = -1, collected, defeated, lost, rest;
		String crew = "", enemy = "";
		private Props() { }
		Props(Properties p, String id) {
			sector = num(p, id + ".sector", -1);
			collected = num(p, id + ".collected");
			defeated = num(p, id + ".defeated");
			lost = num(p, id + ".lost");
			rest = num(p, id + ".rest");
			crew = p.getProperty(id + ".crew", "");
			enemy = p.getProperty(id + ".enemy", "");
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
		}
		static void forget(Properties p, String id) {
			for (String k : new String[] {"sector", "collected", "defeated", "lost", "rest", "crew", "enemy"}) p.remove(id + "." + k);
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
	/** One entry in the reputation log, in the station log's form: its time, the change as its tag, why, and details under it. */
	private static void entry(Vault v, int points, String why, List<String> details) {
		StringBuilder sb = new StringBuilder(log(v));
		sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())).append("  ").append(signed(points)).append("  ").append(why).append('\n');
		if (details != null) for (String d : details) sb.append("  ").append(d).append('\n');
		try { SafeFiles.writeText(new File(v.root, LOG), sb.toString(), false); }
		catch (IOException e) { log.warn("Could not write the reputation log: {}", e.toString()); }
	}
	public static String signed(int n) { return n > 0 ? "+" + n : n < 0 ? "−" + (-n) : "0"; }
	private static int num(Properties p, String k) { return num(p, k, 0); }
	private static int num(Properties p, String k, int dflt) {
		try { return Integer.parseInt(p.getProperty(k, "").trim()); } catch (NumberFormatException e) { return dflt; }
	}
}
