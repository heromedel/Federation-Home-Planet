package homeplanet.parser;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import homeplanet.core.Event;
import homeplanet.core.HistoryLog;
import homeplanet.core.Store;
import homeplanet.vault.Reputation;
import homeplanet.vault.Vault;

/**
 * Captain's Quarters (heromedel, 5.00): a day's rest passes one beacon of the fleet's time away from FTL, so what's
 * due comes due (a detail back, the infirmary, a ransom's clock, the Junkyard's next batch). Resting on and on isn't
 * looked on well: the first day is free, then each day in a row costs reputation, 1, 2, 3, 4, 5 and 5 from there,
 * with Reputation shown; anything else that moves the clock (a jump flown, a detail sent) ends the run. Kept in the
 * fleet's rest.txt: the beacon the last rest ended on, and the run so far.
 */
public final class Rest {
	private static final Logger log = LoggerFactory.getLogger(Rest.class);
	private Rest() { }

	/** The most a day in a row costs. */
	public static final int MAX_COST = 5;

	private static File file(Vault v) { return new File(v.root, "rest.txt"); }
	private static Properties read(Vault v) { return Store.read(file(v)); }

	/** Days rested in a row so far: the last rest ended on this very beacon, else 0. */
	public static int run(Vault v) {
		Properties p = read(v);
		return p.getProperty("last") != null && Store.num(p, "last", 0) == v.beaconsSeen() ? Store.num(p, "run", 0) : 0;
	}
	/** What another day would cost now: nothing the first day or without Reputation, else the run, up to MAX_COST. */
	public static int cost(Vault v) { return homeplanet.core.Economy.repSpends() ? Math.min(MAX_COST, run(v)) : 0; }
	/** The question, as heromedel wrote it: plain the first day, then the days so far with "What will people think." on a line of its own, and the cost on another when there is one. */
	public static String question(Vault v) {
		int run = run(v), cost = cost(v);
		String q = "Would you like to spend the rest of today in your quarters"
				+ (run == 0 ? "." : run == 1 ? " as you did yesterday.\nWhat will people think." : " as you have for the last " + run + " days.\nWhat will people think.");
		return cost > 0 ? q + "\n\n-" + cost + " reputation." : q;
	}
	/** A day in quarters: the cost (if any) off the reputation, the run kept, and one beacon of the fleet's time passed. */
	public static synchronized void rest(Vault v) throws IOException {
		int run = run(v), cost = cost(v);
		if (cost > 0) Reputation.spend(v, cost, "Rested in quarters again, " + (run == 1 ? "a second day" : "day " + (run + 1) + " in a row"));
		// logged before the clock moves: the rest belongs to the day spent resting (5.18)
		HistoryLog.entry("REST", "Rested in quarters" + (run > 0 ? " (" + (run + 1) + " days in a row" + (cost > 0 ? ", \u2212" + cost + " reputation" : "") + ")" : ""), null,
				Event.of("REST").put("days_in_a_row", run + 1).put("cost", cost));
		v.countBeacon("a day of rest in your quarters");
		Properties p = new Properties();
		p.setProperty("last", Integer.toString(v.beaconsSeen()));
		p.setProperty("run", Integer.toString(run + 1));
		Store.write(file(v), p, "Captain's Quarters: the last day rested, and the days in a row");
	}
}
