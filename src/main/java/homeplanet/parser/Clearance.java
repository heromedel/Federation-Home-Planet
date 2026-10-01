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
		return "The Federation Home Planet clears custom ships, remodels and overhauls for Captains and above. You are a " + UnlockGrants.rankName(r) + ".\n\n"
				+ HOW_CAPTAIN;
	}
	/** How to be promoted, as FTL unlocks the Federation Cruiser (from the FTL wiki; FTL keeps these conditions in the game itself). */
	public static final String HOW_CAPTAIN = "Promotion to Captain: unlock the Federation Cruiser (Type A) in FTL. Win the fight at the Huge Rebel Shipyard\n"
			+ "in a Rebel Stronghold sector, or win a game with the Engi Cruiser.";
	public static final String HOW_COMMODORE = "Promotion to Commodore: unlock the Federation Cruiser Type C in FTL. Reach sector 8 with a Federation Cruiser,\n"
			+ "with Advanced Edition content on.";

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
			return r >= 2 ? null : "The Federation's artillery is cleared for Commodores. You are a " + UnlockGrants.rankName(r) + ".\n\n" + (r < 1 ? HOW_CAPTAIN + "\n" : "") + HOW_COMMODORE;
		}
		return flagshipCleared() ? null : "The plans for the Rebel Flagship's weapons are released once you earn the achievement \"Rule Ten: Greed is Eternal\" in Immersive Mode:\n"
				+ "collect 10,000 scrap across all your FTL games.";
	}
	/** Why this blueprint can't be commissioned (custom, or its artillery), or null if it can. */
	public static String commissionReason(String bpId) {
		if (!bpId.endsWith(Retrofit.SUFFIX)) return null; // FTL's own ships: the unlock rules decide
		String why = customReason();
		if (why != null) return why;
		return artilleryReason(Commission.artilleryWeapon(bpId));
	}
}
