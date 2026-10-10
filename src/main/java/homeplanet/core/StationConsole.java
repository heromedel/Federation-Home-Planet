package homeplanet.core;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import homeplanet.parser.Assignments;
import homeplanet.parser.Expeditions;
import homeplanet.vault.Vault;

/**
 * The station's console (heromedel, 5.22; opened with ~): what each command line answers. Admin commands start locked:
 * "/admin dc on" unlocks the dev commands for this run only (6.35: never written to the cfg, locked again when the
 * station closes or a career switch opens another fleet), "/admin dc off" locks them again.
 * The first dev command is /passtime: days passed one at a time, each with the station's round, as after a rest.
 * The public ones (6.35): /? lists /? and /easter; /easter ? hints at what's hidden; /easter girlpower is girl power.
 */
public final class StationConsole {
	private static final Logger log = LoggerFactory.getLogger(StationConsole.class);
	private StationConsole() { }

	/** The cfg line dev commands were kept in before 6.35: removed at startup (they last one run now). */
	public static final String DEV = "dev_commands";
	/** Why a day passed, in the master log (the Captain's Log tells such days as quiet ones). */
	public static final String DAY_WHY = "a dev command";
	public static final int PASS_MAX = 365;

	private static volatile boolean dev;
	public static boolean devOn() { return dev; }
	/** Locks the dev commands (a career switch opening another fleet, 6.35: never left on). */
	public static void lock() {
		if (dev) log.info("Dev commands off (another fleet opened)");
		dev = false;
	}
	private static void setDev(boolean on) { dev = on; } // this run only: never in the cfg (heromedel, 6.35)

	/** A line's answer, and the days it asks to pass (0: none). */
	public static final class Reply {
		public final String text;
		public final int days;
		Reply(String text, int days) { this.text = text; this.days = days; }
	}
	private static final String LIST = "Dev commands:\n  /passtime <days>   pass that many days (1 to " + PASS_MAX + ")\n  /admin dc off   lock them again";
	/** /? (heromedel, 6.35): the public commands, and only those: nothing hints at /admin. */
	private static final String PUBLIC = "/?\n/easter";
	/** /easter ?: a hint a line for each hidden thing (heromedel's words, as written). */
	private static final String HINTS = "* When sisters unite they have..";
	/** Girl power (heromedel, 6.22): in the cfg only while on; a missing line is off. */
	public static final String GIRL_POWER = "girlpower";
	/** Is every crew member the station makes a woman (commissions, hiring, recruits, the Cargo Bay's store, rewards)? */
	public static boolean girlPower() { return "true".equals(HomePlanet.config.getProperty(GIRL_POWER)); }

	public static Reply answer(String line) {
		String s = line == null ? "" : line.trim().replaceAll("\\s+", " "), low = s.toLowerCase();
		if (low.isEmpty()) return new Reply("", 0);
		if (low.matches("/admin (dc|dev commands) (on|off)")) {
			boolean on = low.endsWith(" on");
			setDev(on);
			log.info("Dev commands {}", on ? "on" : "off");
			return new Reply(on ? "Dev commands on." : "Dev commands off.", 0); // no list: /admin ? shows it (heromedel, 5.23)
		}
		// while locked, everything else under /admin is an unknown command: nothing says there is anything to unlock
		if (devOn() && low.equals("/admin ?")) return new Reply(LIST, 0);
		if (devOn() && low.equals("/admin")) return new Reply("Dev commands are on. /admin ? lists them.", 0);
		if (devOn() && (low.equals("/admin dc") || low.equals("/admin dev commands"))) return new Reply("Dev commands are on. /admin dc off locks them.", 0);
		if (low.equals("/?")) return new Reply(PUBLIC, 0);
		if (low.equals("/easter")) return new Reply("Wouldn't you like to know, Weather Boy.", 0);
		if (low.equals("/easter ?")) return new Reply(HINTS, 0);
		if (low.equals("/easter girlpower")) { // an easter egg now, not a dev command (heromedel, 6.35)
			boolean on = !girlPower();
			if (on) HomePlanet.config.setProperty(GIRL_POWER, "true"); else HomePlanet.config.remove(GIRL_POWER); // never written as false: off leaves no trace
			if (HomePlanet.propFile != null) HomePlanet.saveConfig();
			String said = "Girl power: " + (on ? "on" : "off");
			HistoryLog.entry("SETTINGS", "settings changed", java.util.Collections.singletonList(said), Event.of("SETTINGS").put("girlpower", on).put("by", "easter").detail(said));
			return new Reply(on ? "Girl power on: every crew member The Home Planet Station makes from now on is a woman. Crew already aboard, FTL and trades are unchanged."
					: "Girl power off.", 0);
		}
		if (devOn() && (low.equals("/passtime") || low.startsWith("/passtime "))) {
			int n;
			try { n = Integer.parseInt(low.substring(9).trim()); } catch (NumberFormatException e) { n = 0; }
			if (n < 1 || n > PASS_MAX) return new Reply("Usage: /passtime <days>, from 1 to " + PASS_MAX + ".", 0);
			if (!Vault.isOpen()) return new Reply("No fleet is open.", 0);
			return new Reply("Passing " + n + (n == 1 ? " day" : " days") + "...", n);
		}
		return new Reply("Unknown command: " + s, 0); // locked commands too: they stay hidden
	}

	/** What the days passed brought, for the Space Dock to tell once they're all done. */
	public static final class Round {
		public final List<Assignments.Report> back = new ArrayList<Assignments.Report>();
		public final List<Expeditions.RansomNews> ransomNews = new ArrayList<Expeditions.RansomNews>();
		public final List<String> upAgain = new ArrayList<String>();
	}
	/** One day passed by a dev command, and the station's round on it, so whatever falls due lands on its own day. */
	public static void passDay(Vault v, Round into) {
		v.countBeacon(DAY_WHY);
		if (HomePlanet.expeditionType == 2) into.back.addAll(Assignments.checkReturns(v));
		into.ransomNews.addAll(Expeditions.checkRansoms(v));
		into.upAgain.addAll(Expeditions.checkInfirmary(v));
		homeplanet.parser.Transmissions.check(); // the inbox, as the Space Dock reads it: a stipend or letter due lands on its own day (5.23)
	}
	/** The debug log notes it, and only the debug log (5.23): the master log's hidden day lines mark each day passed. */
	public static void noted(int days) {
		log.info("Dev command used: passing {}", days + (days == 1 ? " day" : " days"));
	}
}
