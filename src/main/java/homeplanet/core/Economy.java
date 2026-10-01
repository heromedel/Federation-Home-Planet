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
	public static final int[] REMOVAL_FEES = {NOT_ALLOWED, 0, 25, 50};
	/** New Journey fees Sandbox Mode can choose. */
	public static final int[] JOURNEY_FEES = {0, 200, 500, 1000};
	/** Immersive Mode's New Journey fee. */
	public static final int IMMERSIVE_JOURNEY_FEE = 200;
	/** Stored systems sell for half their price and upgrades, in every mode. */
	public static final int SYSTEM_SALE_PERCENT = 50;

	/** What taking a system off at Refit costs, paid by the boarded ship, or {@link #NOT_ALLOWED}. */
	public static int removalFee() { return HomePlanet.immersiveMode ? 0 : HomePlanet.removalFee; }
	/** Scrapping a ship may strip her systems into the Cargo Bay (at {@link #stripFee} each). */
	public static boolean stripAllowed() { return HomePlanet.stripAllowed; }
	/** Stripping one system when scrapping: a discount on Refit's removal fee (10 for 25, 20 for 50; 10 where Refit doesn't allow it). */
	public static int stripFee() {
		int r = removalFee();
		return r == 0 ? 0 : r == 50 ? 20 : 10;
	}
	/** What plotting a New Journey costs, paid from the Cargo Hold. */
	public static int journeyFee() { return HomePlanet.immersiveMode ? IMMERSIVE_JOURNEY_FEE : HomePlanet.journeyFee; }
	/** Missiles and drone parts sell at this share of the store price: half, or a quarter in Immersive Mode. */
	public static int supplyPercent() { return HomePlanet.immersiveMode ? 25 : 50; }
	/** What n missiles or drone parts at this store price sell for. */
	public static int supplySale(int n, int storePrice) { return n * storePrice * supplyPercent() / 100; }
	/** How missiles and drone parts sell, for tooltips. */
	public static String supplyShare() {
		int p = supplyPercent();
		return p == 50 ? "half the store price" : p + "% of the store price, set by Immersive Mode";
	}

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
