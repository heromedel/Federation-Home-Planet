package homeplanet.parser;

import java.io.File;
import java.util.Arrays;
import java.util.Properties;

import homeplanet.vault.Vault;

/**
 * An Immersive career's difficulty: Easy, Normal, Hard, or Custom (any level of each rule), chosen when it begins and
 * fixed from then on. A career from before difficulties is "Custom (from before difficulties)", keeping the rules it
 * had. Kept in its career.txt; {@link #current()} is the open Immersive fleet's.
 */
public final class CareerRules {
	public static final String EASY = "easy", NORMAL = "normal", HARD = "hard", CUSTOM = "custom", EARLIER = "earlier";
	public static final String[] NAMES = {EASY, NORMAL, HARD, CUSTOM};

	/** The rules a difficulty sets, in this order (the rows of the briefing's table). */
	public static final int VICTORY = 0, JOURNEY = 1, REASSIGNMENT = 2, REMOVAL = 3, STRIPPING = 4, SUPPLIES = 5, STIPEND = 6, COMMISSION = 7, STARTING_SCRAP = 8, AUGMENTS = 9, WORK_ORDER = 10, PLEA = 11;
	public static final String[] RULES = {"After a final victory", "A New Journey costs", "Plead for New Ship grants", "Refit: taking a system off",
			"Stripping when scrapping", "Missiles and drone parts sell for", "The stipend comes every", "Commissioning a ship costs", "Scrap to start with",
			"An augment with no room aboard", "A custom work order (past the System Limit) costs", "A plea answered with reputation costs"};
	/** Each rule's Easy, Normal and Hard, in words. */
	public static final String[][] LEVELS = {
		{"Save her, or the museum buys her at full value", "Save her, or the museum buys her at half value", "The museum takes her, at half value"},
		{"200 scrap", "500 scrap", "1000 scrap"},
		{"any ship (or the Relief Ship Type A)", "a Kestrel Type A or the Relief Ship Type A", "the Relief Ship Type A"},
		{"25 scrap or reputation", "50 scrap or reputation", "75 scrap or reputation"},
		{"15 scrap or reputation a system", "30 scrap or reputation a system", "60 scrap or reputation a system"},
		{"half the store price", "a quarter of the store price", "1 scrap each"},
		{"one month", "two months", "three months"},
		{"half her price", "75% of her price", "her full price"},
		{"50 scrap", "25 scrap", "10 scrap"},
		{"is shipped home by her crew", "is shipped home by her crew", "is lost"},
		{"25 scrap and 25 reputation", "50 scrap and 50 reputation", "75 scrap and 75 reputation"},
		{"a tenth of her value", "a quarter of her value", "half her value"}};
	private static final int[] JOURNEY_FEES = {200, 500, 1000}, REMOVAL_FEES = {25, 50, 75}, STRIP_FEES = {15, 30, 60}, SUPPLY_PERCENT = {50, 25, 0},
			STIPEND_MONTHS = {1, 2, 3}, COMMISSION_PERCENT = {50, 75, 100}, START_SCRAP = {50, 25, 10}, WORK_ORDERS = {25, 50, 75}, PLEA_PERCENT = {10, 25, 50};
	private static final String[] REASSIGN = {FreeCommand.ANY, FreeCommand.KESTREL, FreeCommand.RELIEF};
	/** A career from before difficulties: its final victory stays the choice made in Settings (nothing, rescue or reward). */
	public static final int OWN_CHOICE = -1;

	/** easy, normal, hard, custom or earlier. */
	public final String name;
	private final int[] level;

	public CareerRules(String name, int[] level) {
		this.name = name;
		this.level = Arrays.copyOf(level, Math.max(level.length, RULES.length));
		for (int i = level.length; i < this.level.length; i++) this.level[i] = 1; // a rule added since: Normal
	}
	/** Easy, Normal or Hard: one level for every rule. */
	public static CareerRules of(String name) {
		int l = HARD.equals(name) ? 2 : NORMAL.equals(name) ? 1 : 0;
		int[] lv = new int[RULES.length];
		Arrays.fill(lv, l);
		return new CareerRules(HARD.equals(name) || NORMAL.equals(name) ? name : EASY, lv);
	}
	/** A career from before difficulties, as it was: final victory as chosen, journeys 200, a Kestrel or the Relief Ship on a plea, removal free, stripping as Settings had it (free), 25%, the stipend every two months (the nearest to its 60 beacons), full price. */
	public static CareerRules earlier(boolean stripped) {
		return new CareerRules(EARLIER, new int[] {OWN_CHOICE, 0, 1, 0, stripped ? 0 : 2, 1, 1, 2, 1, 1, 1, 0}); // (commission: full price, as it was; a plea a tenth, as it was)
	}

	/** This rule's level: 0 (Easy), 1 (Normal) or 2 (Hard); OWN_CHOICE for an earlier career's final victory. */
	public int level(int rule) { return level[rule]; }
	public int[] levels() { return level.clone(); }

	public String title() {
		return EASY.equals(name) ? "Easy" : NORMAL.equals(name) ? "Normal" : HARD.equals(name) ? "Hard" : EARLIER.equals(name) ? "Custom (from before difficulties)" : "Custom";
	}
	/** A rule's level in words. */
	public String words(int rule) {
		if (rule == VICTORY && level[rule] == OWN_CHOICE) return "as chosen in Settings";
		if (EARLIER.equals(name) && rule == REMOVAL) return "free";
		if (EARLIER.equals(name) && rule == STRIPPING) return stripAllowed() ? "allowed, free" : "not allowed";
		return LEVELS[rule][level[rule]];
	}

	public int journeyFee() { return JOURNEY_FEES[level[JOURNEY]]; }
	public String reassignment() { return REASSIGN[level[REASSIGNMENT]]; }
	/** A career from before difficulties keeps its own: removal free, and stripping free or not allowed, as Settings had it. */
	public int removalFee() { return EARLIER.equals(name) ? 0 : REMOVAL_FEES[level[REMOVAL]]; }
	public boolean stripAllowed() { return EARLIER.equals(name) ? level[STRIPPING] != 2 : STRIP_FEES[level[STRIPPING]] >= 0; }
	public int stripFee() { return EARLIER.equals(name) ? 0 : Math.max(0, STRIP_FEES[level[STRIPPING]]); }
	/** Missiles and drone parts sell at this share of the store price; 0 means 1 scrap each. */
	public int supplyPercent() { return SUPPLY_PERCENT[level[SUPPLIES]]; }
	/** Months between stipends (a month is Career.BEACONS_PER_MONTH beacons). */
	public int stipendMonths() { return STIPEND_MONTHS[level[STIPEND]]; }
	public int stipendBeacons() { return stipendMonths() * Career.BEACONS_PER_MONTH; }
	public int commissionPercent() { return COMMISSION_PERCENT[level[COMMISSION]]; }
	public int startingScrap() { return START_SCRAP[level[STARTING_SCRAP]]; }
	/** An augment thrown away for want of room comes home (Easy and Normal), or is lost (Hard). */
	public boolean augmentsHome() { return level[AUGMENTS] < 2; }
	/** A custom work order: this much scrap, and as much reputation. */
	public int workOrder() { return WORK_ORDERS[level[WORK_ORDER]]; }
	/** A plea answered with reputation: this share of what the Cargo Hold doesn't cover of her value. */
	public int pleaPercent() { return PLEA_PERCENT[level[PLEA]]; }
	/** After a final victory: FinalVictory.RESCUE or MUSEUM, or null for an earlier career's own choice. */
	public String victory() {
		int l = level[VICTORY];
		return l == OWN_CHOICE ? null : l == 2 ? FinalVictory.MUSEUM : FinalVictory.RESCUE;
	}
	/** What the museum pays, as a share of her value: full value on Easy, half otherwise (an earlier career: full). */
	public int museumPercent() { return level[VICTORY] <= 0 ? 100 : 50; }

	// ---- in career.txt ----

	/** The career's rules, from its properties; one from before difficulties is written down as it was, then fixed. */
	static CareerRules read(Properties p) {
		String n = p.getProperty("difficulty");
		if (n == null) return null;
		if (CUSTOM.equals(n) || EARLIER.equals(n)) {
			String[] s = p.getProperty("rules", "").split(",");
			int[] lv = new int[RULES.length];
			for (int i = 0; i < lv.length; i++) {
				try { lv[i] = Integer.parseInt(s[i].trim()); } catch (Exception e) { lv[i] = 1; }
				if (lv[i] < (i == VICTORY ? OWN_CHOICE : 0) || lv[i] > 2) lv[i] = 1;
			}
			return new CareerRules(n, lv);
		}
		return of(n);
	}
	void write(Properties p) {
		p.setProperty("difficulty", name);
		if (CUSTOM.equals(name) || EARLIER.equals(name)) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < level.length; i++) sb.append(i == 0 ? "" : ",").append(level[i]);
			p.setProperty("rules", sb.toString());
		} else {
			p.remove("rules");
		}
	}

	private static File cachedRoot;
	private static long cachedStamp;
	private static CareerRules cached;
	/** The open Immersive fleet's rules (Normal if it has no career yet), or null in Sandbox Mode. */
	public static synchronized CareerRules current() {
		if (!Vault.isOpen() || !Vault.get().immersive) return null;
		File root = Vault.get().root, f = Career.file(root);
		long stamp = f.lastModified();
		if (cached != null && root.equals(cachedRoot) && stamp == cachedStamp) return cached;
		CareerRules r = Career.rules(root);
		cachedRoot = root;
		cachedStamp = f.lastModified();
		cached = r != null ? r : of(NORMAL);
		return cached;
	}
	/** Forget the cached rules (after career.txt is written in the same second as it was read). */
	static synchronized void forget() { cached = null; }

	@Override public String toString() { return title() + " " + Arrays.toString(level); }

	/** For the history log: the rules in words. */
	public String describe() {
		StringBuilder sb = new StringBuilder(title());
		for (int i = 0; i < RULES.length; i++) sb.append("; ").append(RULES[i].toLowerCase()).append(": ").append(words(i));
		return sb.toString();
	}
}
