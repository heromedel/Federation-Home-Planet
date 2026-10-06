package homeplanet.parser;

import homeplanet.core.HomePlanet;

/**
 * What the player's rank clears them for in Immersive Mode (outside it, everything is cleared), on the reputation ladder
 * (heromedel, 5.56): remodels for Commanders; designing, deck plan overhauls and custom ships for Commodores; the
 * Federation's artillery for Admirals. The Rebel Flagship's weapons once "Rule Ten: Greed is Eternal" is earned in
 * Immersive Mode (on a custom ship, so a Commodore's too). A career from before 5.56 keeps what its cruiser rank cleared.
 */
public final class Clearance {
	private Clearance() { }

	/** The player's rank in Immersive Mode (always Ranks From Rep), or -1 outside it. */
	public static int rank() {
		return HomePlanet.immersiveMode ? PlayerRank.rank(Unlocks.read()) : -1;
	}
	private static boolean kept(String what) { return homeplanet.vault.Vault.isOpen() && PlayerRank.kept(homeplanet.vault.Vault.get(), what); }
	/** How to reach a rank, with the reputation the player has now. */
	public static String howTo(int r) {
		String have = "";
		try { if (homeplanet.vault.Vault.isOpen()) have = " You have " + homeplanet.vault.Reputation.total(homeplanet.vault.Vault.get()) + "."; } catch (RuntimeException e) { }
		return "Promotion to " + PlayerRank.REP_RANKS[r] + ": reach " + String.format("%,d", PlayerRank.REP_STEPS[r]) + " reputation." + have;
	}
	/** Why remodels aren't cleared (Commanders and above), or null if they are. */
	public static String remodelReason() {
		int r = rank();
		if (r < 0 || r >= PlayerRank.COMMANDER || kept("custom")) return null;
		return "The Federation Home Planet clears remodels for Commanders and above. You are a " + PlayerRank.name(r) + ".\n\n" + howTo(PlayerRank.COMMANDER);
	}
	/** Why custom ships (designing, deck plan overhauls, commissioning them) aren't cleared (Commodores and above), or null if they are. */
	public static String customReason() {
		int r = rank();
		if (r < 0 || r >= PlayerRank.COMMODORE || kept("custom")) return null;
		return "The Federation Home Planet clears custom ships, designs and deck plan overhauls for Commodores and above. You are a " + PlayerRank.name(r) + ".\n\n"
				+ howTo(PlayerRank.COMMODORE);
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
			return r >= PlayerRank.ADMIRAL || kept("artillery") ? null : "The Federation's artillery is cleared for Admirals. You are a " + PlayerRank.name(r) + ".\n\n" + howTo(PlayerRank.ADMIRAL);
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
