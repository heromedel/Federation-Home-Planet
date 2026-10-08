package homeplanet.parser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import homeplanet.core.HomePlanet;
import homeplanet.core.Store;
import homeplanet.vault.Vault;

/**
 * The player's rank (heromedel, 5.56). Ranks From Rep (the default, and always Immersive Mode's): Major to Admiral,
 * climbed by reputation and never lost. Ranks From Cruiser: Commander, Captain, Commodore, as FTL's Federation Cruiser
 * unlocks (the rule before 5.56). No Ranks. The rank sets the stipend's multiple, and in Immersive Mode what the player
 * is cleared for ({@link Clearance}).
 */
public final class PlayerRank {
	private static final Logger log = LoggerFactory.getLogger(PlayerRank.class);
	private PlayerRank() { }

	public static final int FROM_REP = 0, FROM_CRUISER = 1, NONE = 2;
	/** The setting's choices, in heromedel's words and order. */
	public static final String[] OPTIONS = {"Ranks From Rep", "Ranks From Cruiser", "No Ranks"};
	/** heromedel's tooltip for Ranks From Rep. */
	public static final String REP_TIP = "Requires Reputation to be turned on.";
	public static final String CFG = "player_ranks";
	/** The player's own choice (Sandbox Mode; Immersive Mode is always Ranks From Rep). */
	public static int setting = FROM_REP;

	/** The ladder: the ranks, the reputation each needs, and the stipend's multiple for each achievement counted. */
	public static final String[] REP_RANKS = {"Major", "Colonel", "Commander", "Captain", "Commodore", "Admiral"};
	public static final int[] REP_STEPS = {0, 250, 500, 1000, 2500, 10000};
	private static final int[] REP_MULTIPLE = {1, 2, 2, 3, 3, 4};
	/** The ranks the clearances open at: remodels, then custom ships, then the Federation's artillery. */
	public static final int COMMANDER = 2, COMMODORE = 4, ADMIRAL = 5;
	/** Reputation for each Federation Cruiser layout unlocked in the career's service (Ranks From Rep). */
	public static final int CRUISER_BONUS = 100;

	/** The rule in force: Immersive Mode's is Ranks From Rep; without Reputation, Ranks From Rep falls back to the cruiser. */
	public static int mode() {
		if (HomePlanet.immersiveMode) return FROM_REP;
		if (setting == FROM_REP && !HomePlanet.reputation()) return FROM_CRUISER;
		return setting;
	}
	/** The rank now (an index into its ladder), or -1 with No Ranks. */
	public static int rank(Unlocks u) {
		switch (mode()) {
			case FROM_REP: return Vault.isOpen() ? reached(Vault.get()) : 0;
			case FROM_CRUISER: return UnlockGrants.rank(u);
			default: return -1;
		}
	}
	/** A rank's name; with No Ranks, Commander, as FTL addresses the player. */
	public static String name(int r) {
		if (r < 0) return UnlockGrants.RANKS[0];
		return mode() == FROM_REP ? REP_RANKS[Math.min(r, REP_RANKS.length - 1)] : UnlockGrants.rankName(r);
	}
	/** The stipend's multiple for each achievement counted at this rank. */
	public static int multiple(int r) {
		if (r < 0) return 1;
		return mode() == FROM_REP ? REP_MULTIPLE[Math.min(r, REP_MULTIPLE.length - 1)] : r + 1;
	}
	/** The rank on the ladder for this much reputation. */
	public static int forReputation(int rep) {
		int r = 0;
		for (int i = 0; i < REP_STEPS.length; i++) if (rep >= REP_STEPS[i]) r = i;
		return r;
	}
	/** Every rank's name, both ladders (a commander's title on the Long Range may start with any). */
	public static List<String> allNames() {
		List<String> out = new ArrayList<String>(java.util.Arrays.asList(REP_RANKS));
		for (String r : UnlockGrants.RANKS) if (!out.contains(r)) out.add(r);
		return out;
	}

	// ---- the ladder, kept with the fleet: the highest rank reached, never lowered ----

	private static File file(Vault v) { return Store.file(v.root, "rank"); } // rank.xml (5.86)
	private static Properties read(Vault v) { return Store.read(file(v)); }
	private static void write(Vault v, Properties p) {
		try { Store.write(file(v), p, "The player's rank on the reputation ladder (Ranks From Rep): the highest reached, never lowered"); }
		catch (IOException e) { log.warn("Could not record the rank: {}", e.toString()); }
	}
	/** The highest rank reached on the ladder. */
	public static int reached(Vault v) {
		return Store.num(read(v), "reached", 0);
	}
	/** A clearance an old career held under the cruiser ranks, kept ("custom": remodels and custom ships; "artillery"). */
	public static boolean kept(Vault v, String what) { return "true".equals(read(v).getProperty("kept." + what)); }
	/** A career begun now: on the ladder from its start, nothing carried over. */
	public static void begin(Vault v) {
		if (file(v).isFile()) return;
		Properties p = new Properties();
		p.setProperty("reached", "0");
		write(v, p);
	}

	/** What a look at the ladder found: the first look for a career from before 5.56, or the ranks newly reached. */
	public static final class Climb {
		public boolean first;
		public final List<Integer> promoted = new ArrayList<Integer>();
	}
	/**
	 * Brings the ladder up to this reputation. A career from before 5.56, the first time: the higher of its
	 * reputation's rank and its cruiser rank (Captain or Commodore), with any clearance that rank held kept.
	 */
	public static synchronized Climb climb(Vault v, int reputation, Unlocks u) {
		Climb c = new Climb();
		int now = forReputation(reputation);
		if (!file(v).isFile()) {
			int old = u == null ? 0 : UnlockGrants.rank(u); // 0 Commander, 1 Captain, 2 Commodore
			Properties p = new Properties();
			p.setProperty("reached", Integer.toString(Math.max(now, old >= 2 ? COMMODORE : old >= 1 ? COMMODORE - 1 : 0)));
			if (old >= 1) p.setProperty("kept.custom", "true");
			if (old >= 2) p.setProperty("kept.artillery", "true");
			write(v, p);
			c.first = true;
			return c;
		}
		Properties p = read(v);
		int was = Store.num(p, "reached", 0);
		if (now <= was) return c;
		for (int r = was + 1; r <= now; r++) c.promoted.add(r);
		p.setProperty("reached", Integer.toString(now));
		write(v, p);
		return c;
	}
}
