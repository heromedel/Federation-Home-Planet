package homeplanet.core;

/**
 * What things cost and pay at The Home Planet Station, in one place: Sandbox Mode takes them from Settings, Immersive
 * Mode from the career.
 */
public final class Economy {
	private Economy() { }

	/** Refit: taking a system off a ship isn't allowed at all. */
	public static final int NOT_ALLOWED = -1;
	/** Refit removal fees Sandbox Mode can choose. */
	public static final int[] REMOVAL_FEES = {NOT_ALLOWED, 0, 25, 50, 75};
	/** New Journey fees Sandbox Mode can choose. */
	public static final int[] JOURNEY_FEES = {0, 200, 500, 1000};
	/** Stored systems sell for half their price and upgrades, in every mode. */
	public static final int SYSTEM_SALE_PERCENT = 50;

	/** The Immersive career's difficulty, or null in Sandbox Mode. */
	private static homeplanet.parser.CareerRules career() { return HomePlanet.immersiveMode ? homeplanet.parser.CareerRules.current() : null; }

	/** What taking a system off at Refit costs, paid by the boarded ship, or {@link #NOT_ALLOWED}. */
	public static int removalFee() {
		homeplanet.parser.CareerRules c = career();
		return c != null ? c.removalFee() : HomePlanet.immersiveMode ? 0 : HomePlanet.removalFee;
	}
	/** Scrapping a ship may strip her systems into the Cargo Bay (at {@link #stripFee} each). */
	public static boolean stripAllowed() {
		homeplanet.parser.CareerRules c = career();
		return c != null ? c.stripAllowed() : HomePlanet.stripAllowed;
	}
	/** Stripping one system when scrapping: in Sandbox Mode a discount on Refit's removal fee (10 for 25, 20 for 50, 30 for 75; 10 where Refit doesn't allow it). */
	public static int stripFee() {
		homeplanet.parser.CareerRules c = career();
		if (c != null) return c.stripFee();
		int r = removalFee();
		return r == 0 ? 0 : r == 75 ? 30 : r == 50 ? 20 : 10;
	}
	/** What plotting a New Journey costs, paid from the Cargo Hold. */
	public static int journeyFee() {
		homeplanet.parser.CareerRules c = career();
		return c != null ? c.journeyFee() : HomePlanet.immersiveMode ? 200 : HomePlanet.journeyFee;
	}
	/** Missiles and drone parts sell at this share of the store price: half in Sandbox Mode, the difficulty's in a career (0: 1 scrap each). */
	public static int supplyPercent() {
		homeplanet.parser.CareerRules c = career();
		return c != null ? c.supplyPercent() : HomePlanet.immersiveMode ? 25 : 50;
	}
	/** What n missiles or drone parts at this store price sell for. */
	public static int supplySale(int n, int storePrice) {
		int p = supplyPercent();
		return p <= 0 ? n : n * storePrice * p / 100;
	}
	/** How missiles and drone parts sell, for tooltips. */
	public static String supplyShare() {
		int p = supplyPercent();
		if (!HomePlanet.immersiveMode) return "half the store price";
		return (p <= 0 ? "1 scrap each" : p == 50 ? "half the store price" : p + "% of the store price") + ", set by your career";
	}
	/** What Plead for New Ship grants: FreeCommand's any, kestrel or relief (Settings', or the career's; the Relief Ship always too). */
	public static String reassignment() {
		homeplanet.parser.CareerRules c = career();
		return homeplanet.parser.FreeCommand.norm(c != null ? c.reassignment() : HomePlanet.immersiveMode ? homeplanet.parser.FreeCommand.KESTREL : HomePlanet.freeShip);
	}
	/** The share of a ship's price that counts wherever one is priced (Commission, sales, the Junkyard; never the stores): Settings', or the career's (Easy 50, Normal 75, Hard 100). */
	public static int commissionPercent() {
		homeplanet.parser.CareerRules c = career();
		return c != null ? c.commissionPercent() : HomePlanet.immersiveMode ? 100 : HomePlanet.commissionPercent; // (a career from before difficulties: full price)
	}

	/** An augment a ship had no room for is shipped home: Settings', or the career's (Easy and Normal yes, Hard no). */
	public static boolean augmentsHome() {
		homeplanet.parser.CareerRules c = career();
		return c != null ? c.augmentsHome() : HomePlanet.immersiveMode || HomePlanet.augmentsHome;
	}

	/**
	 * A custom work order past FTL's System Limit (heromedel, 5.13): this much scrap and as much reputation (Easy 25,
	 * Normal 50, Hard 75 of each; Sandbox 50 of each). With Reputation off, the reputation's share is paid in scrap too.
	 */
	public static int workOrderScrap() { int b = workOrderBase(); return homeplanet.vault.Reputation.shown() ? b : 2 * b; }
	/** The reputation a custom work order costs beside its scrap (0 with Reputation off). */
	public static int workOrderRep() { return homeplanet.vault.Reputation.shown() ? workOrderBase() : 0; }
	private static int workOrderBase() {
		homeplanet.parser.CareerRules c = career();
		return c != null ? c.workOrder() : 50;
	}
	/** A custom work order in a commissioned ship's price: scrap only, both shares (heromedel: Easy 50, Normal 100 as it was, Hard 150; Sandbox 100). */
	public static int commissionWorkOrder() { return 2 * workOrderBase(); }
	/** A custom work order's price in words: "50 scrap and 50 reputation", or "100 scrap". */
	public static String workOrderWords() { return workOrderScrap() + " scrap" + (workOrderRep() > 0 ? " and " + workOrderRep() + " reputation" : ""); }
	/** A plea answered with reputation: this share of what the Cargo Hold doesn't cover of her value (Easy 10, Normal 25, Hard 50; Sandbox 10). */
	public static int pleaPercent() {
		homeplanet.parser.CareerRules c = career();
		return c != null ? c.pleaPercent() : 10;
	}
	/** A share in words: "a tenth", "a quarter", "half", or "N%". */
	public static String share(int percent) { return percent == 10 ? "a tenth" : percent == 25 ? "a quarter" : percent == 50 ? "half" : percent + "%"; }

	/** A removal fee as Settings words it. */
	public static String removalTitle(int fee) { return fee == NOT_ALLOWED ? "not allowed" : fee == 0 ? "free" : fee + " scrap"; }
	/** A Sandbox removal fee from the cfg (anything unknown is free). */
	static int removalFee(String v) {
		try { int f = Integer.parseInt(v == null ? "" : v.trim()); for (int k : REMOVAL_FEES) if (k == f) return f; } catch (NumberFormatException e) { }
		return 0;
	}
	/** A Sandbox journey fee from the cfg (anything unknown is free). */
	static int journeyFee(String v) {
		try { int f = Integer.parseInt(v == null ? "" : v.trim()); for (int k : JOURNEY_FEES) if (k == f) return f; } catch (NumberFormatException e) { }
		return 0;
	}
}
