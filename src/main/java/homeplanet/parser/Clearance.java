package homeplanet.parser;

import homeplanet.core.HomePlanet;

/**
 * What the player's rank clears them for in Immersive Mode (outside it, everything is cleared): custom ships, remodels
 * and overhauls for Captains; the Federation's artillery for Commodores; the Rebel Flagship's weapons once "Rule Ten:
 * Greed is Eternal" is earned in Immersive Mode.
 */
public final class Clearance {
	private Clearance() { }

	/** The player's rank in Immersive Mode, or -1 outside it. */
	public static int rank() {
		return HomePlanet.immersiveMode ? UnlockGrants.rank(Unlocks.read()) : -1;
	}
	/** Why custom ships (commissioning, designing, remodels, overhauls) aren't cleared, or null if they are. */
	public static String customReason() {
		int r = rank();
		if (r < 0 || r >= 1) return null;
		return "The Federation Home Planet clears custom ships, remodels and overhauls for Captains and above. You are a " + UnlockGrants.rankName(r) + ".";
	}
	/** The Rebel Flagship's weapons: cleared by "Rule Ten: Greed is Eternal", earned in Immersive Mode. */
	public static boolean flagshipCleared() {
		for (Transmissions.Message m : Transmissions.load()) if (m.key.equals("ach:ACH_SCRAP")) return true;
		return false;
	}
	/** Why this artillery weapon isn't cleared, or null if it is (or there's none). */
	public static String artilleryReason(String weaponId) {
		int r = rank();
		if (r < 0 || weaponId == null) return null;
		if (weaponId.startsWith("ARTILLERY_FED")) {
			return r >= 2 ? null : "The Federation's artillery is cleared for Commodores. You are a " + UnlockGrants.rankName(r) + ".";
		}
		return flagshipCleared() ? null : "The Rebel Flagship's weapons are cleared once you earn \"Rule Ten: Greed is Eternal\" in Immersive Mode.";
	}
	/** Why this blueprint can't be commissioned (custom, or its artillery), or null if it can. */
	public static String commissionReason(String bpId) {
		if (!bpId.endsWith(Retrofit.SUFFIX)) return null; // FTL's own ships: the unlock rules decide
		String why = customReason();
		if (why != null) return why;
		return artilleryReason(Commission.artilleryWeapon(bpId));
	}
}
