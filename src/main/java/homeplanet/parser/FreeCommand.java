package homeplanet.parser;

import java.io.File;
import java.nio.charset.StandardCharsets;

import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;

import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * Which ship the free command brings: Settings chooses (a Kestrel Type A, any ship, or a relief ship), except in
 * Immersive Mode, where a new career starts on a Kestrel Type A as a new FTL game does, and a Report for Reassignment
 * earns a ship by what was surrendered: 1000 scrap's worth or more, any ship; 500 or more, a Kestrel Type A; less,
 * a relief ship.
 */
public final class FreeCommand {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FreeCommand.class);
	private FreeCommand() { }

	public static final String KESTREL = "kestrel", ANY = "any", RELIEF = "relief", VARIABLE = "variable";
	public static final int ANY_FROM = 1000, KESTREL_FROM = 500;

	/**
	 * The free ship now. Immersive Mode: the one a report earned, and otherwise (a new career, or a fleet from before
	 * this was recorded) a Kestrel Type A. Normal mode: Settings'.
	 */
	public static String ship() {
		if (!Vault.isOpen()) return HomePlanet.freeShip;
		Vault v = Vault.get();
		String k = v.freeCommandShip();
		if (k != null) return k;
		if (!v.freeCommandReassigned()) return KESTREL; // a new fleet or career starts with a Kestrel Type A, as FTL does
		String k2 = homeplanet.core.Economy.reassignment();
		return VARIABLE.equals(k2) ? KESTREL : k2; // (a report from before Variable recorded nothing)
	}
	/** The free ship in words, for messages and the Space Dock: "a Kestrel Type A", "any ship you choose", "a Federation relief ship". */
	public static String words(String kind) {
		return ANY.equals(kind) ? "any ship you choose" : RELIEF.equals(kind) ? "a Federation relief ship"
				: VARIABLE.equals(kind) ? "a ship by what you surrender" : "a Kestrel Type A";
	}
	/** The free ship a Report for Reassignment would earn now: Settings' or the career's, or, for Variable, by what it surrenders. */
	public static String onReport(Vault v) {
		return byValue(v) ? earned(surrenderValue(v)) : homeplanet.core.Economy.reassignment();
	}
	/** Does a report earn its ship by what it surrenders (Variable, in Settings or the career's difficulty)? */
	public static boolean byValue(Vault v) {
		return VARIABLE.equals(homeplanet.core.Economy.reassignment());
	}
	/** The ship a Report for Reassignment earns in Immersive Mode, by the value surrendered. */
	public static String earned(int value) {
		return value >= ANY_FROM ? ANY : value >= KESTREL_FROM ? KESTREL : RELIEF;
	}

	/**
	 * Everything of value a Report for Reassignment would surrender, in scrap: the Cargo Hold (scrap, supplies, items,
	 * crew, stored systems) and the Junkyard's hulls at their full price.
	 */
	public static int surrenderValue(Vault v) {
		int total = 0;
		try {
			SavedGameState gs = v.storage().save();
			if (gs != null) {
				ShipState s = gs.getPlayerShip();
				total += s.getScrapAmt() + s.getFuelAmt() * Pricing.FUEL + s.getMissilesAmt() * Pricing.MISSILE + s.getDronePartsAmt() * Pricing.DRONE_PART;
				for (WeaponState w : s.getWeaponList()) total += Pricing.item(w.getWeaponId());
				for (DroneState d : s.getDroneList()) total += Pricing.item(d.getDroneId());
				for (String a : s.getAugmentIdList()) total += Pricing.item(a);
				for (String c : gs.getCargoIdList()) total += Pricing.item(c);
				for (CrewState c : SaveHelper.getOwnCrew(s)) total += Pricing.crew(c.getRace().getId());
			}
		} catch (Exception e) { log.debug("Free command: the Cargo Hold could not be read, counted as empty: {}", e.toString()); } // an unreadable hold counts as empty
		File sys = v.systemsFile();
		if (sys.isFile()) {
			try {
				for (String line : new String(SafeFiles.read(sys), StandardCharsets.UTF_8).split("\r?\n")) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) continue;
					String[] p = line.split("\\s+");
					int level = 1;
					try { if (p.length > 1) level = Math.max(1, Integer.parseInt(p[1])); } catch (NumberFormatException e) { }
					total += Pricing.system(p[0], level);
				}
			} catch (Exception e) { log.debug("Free command: the stored systems could not be read: {}", e.toString()); }
		}
		for (Ship j : v.junked()) {
			try { SavedGameState g = j.save(); if (g != null) total += Pricing.ship(g, 100).total(); } catch (Exception e) { log.debug("Free command: {} could not be priced: {}", j.name, e.toString()); }
		}
		return total;
	}
}
