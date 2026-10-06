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
 * Which ship the free command brings. A new fleet or career starts with a Kestrel Type A, as a new FTL game does. A plea
 * for a new ship (Plead for New Ship) offers what Settings or the career's difficulty grant: any ship (Easy), a Kestrel
 * Type A (Normal), or the Relief Ship Type A (Hard); the Relief Ship is always offered too. With Reputation on, the ship
 * chosen costs reputation: the difficulty's share (a tenth, a quarter or half; a tenth in Sandbox) of what the plea's
 * forfeit doesn't cover of her value (the Relief Ship by the same formula as any ship).
 */
public final class FreeCommand {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FreeCommand.class);
	private FreeCommand() { }

	/** "variable" was a fourth choice once (a ship by what was surrendered): read as the Kestrel Type A. */
	public static final String KESTREL = "kestrel", ANY = "any", RELIEF = "relief", VARIABLE = "variable";
	/** The Relief Ship Type A: what the shipyard charges for her, and her class and default name. */
	public static final String RELIEF_CLASS = "Relief Ship Type A", RELIEF_NAME = "Hinata";

	/** A free-ship choice as kept (Settings', a career's), with the old Variable read as the Kestrel Type A. */
	public static String norm(String kind) {
		return ANY.equals(kind) || RELIEF.equals(kind) ? kind : KESTREL;
	}
	/**
	 * The free ship now: what the open free command names (an Immersive career's start), the plea's (Settings' or the
	 * career's), or, for a new fleet, a Kestrel Type A.
	 */
	public static String ship() {
		if (!Vault.isOpen()) return norm(HomePlanet.freeShip);
		Vault v = Vault.get();
		String k = v.freeCommandShip();
		if (k != null) return norm(k);
		if (!v.freeCommandReassigned()) return KESTREL; // a new fleet or career starts with a Kestrel Type A, as FTL does
		return norm(homeplanet.core.Economy.reassignment());
	}
	/** The free ship in words, for messages: "any ship you choose", "a Kestrel Type A", "the Relief Ship Type A". */
	public static String words(String kind) {
		return ANY.equals(kind) ? "any ship you choose" : RELIEF.equals(kind) ? "the " + RELIEF_CLASS : "a Kestrel Type A";
	}
	/** What a plea offers, in words (the Relief Ship always among them). */
	public static String offered(String kind) {
		return ANY.equals(kind) ? "any ship you choose (the " + RELIEF_CLASS + " among them)" : RELIEF.equals(kind) ? "the " + RELIEF_CLASS
				: "a Kestrel Type A or the " + RELIEF_CLASS;
	}
	/** The reputation a plea's ship costs: the difficulty's share (a tenth on Easy and in Sandbox) of what the forfeit doesn't cover of her value (0 if it covers it). */
	public static int reputationCost(int value, int forfeited) {
		int shortfall = Math.max(0, value - forfeited);
		return (shortfall * homeplanet.core.Economy.pleaPercent() + 50) / 100;
	}

	/**
	 * What the Cargo Hold would fetch if it were sold, as the Cargo Bay pays: its scrap, weapons, drones, augments and
	 * cargo at half their price (FTL's stores' rate), missiles and drone parts at their sale price if selling them is
	 * allowed, stored systems at their sale price if selling them is allowed. Fuel and crew can't be sold: nothing (and
	 * the crew stay when the hold is given up).
	 */
	public static int holdSaleValue(Vault v) {
		int total = 0;
		try {
			SavedGameState gs = v.storage().save();
			if (gs != null) {
				ShipState s = gs.getPlayerShip();
				total += s.getScrapAmt();
				for (WeaponState w : s.getWeaponList()) total += Pricing.item(w.getWeaponId()) / 2;
				for (DroneState d : s.getDroneList()) total += Pricing.item(d.getDroneId()) / 2;
				for (String a : s.getAugmentIdList()) total += Pricing.item(a) / 2;
				for (String c : SaveHelper.cargo(gs)) total += Pricing.item(c) / 2;
				if (HomePlanet.sellSupplies())
					total += homeplanet.core.Economy.supplySale(s.getMissilesAmt(), Pricing.MISSILE) + homeplanet.core.Economy.supplySale(s.getDronePartsAmt(), Pricing.DRONE_PART);
			}
		} catch (Exception e) { } // an unreadable hold counts as empty
		File sys = v.systemsFile();
		if (HomePlanet.sellSystems() && sys.isFile()) {
			try {
				for (String line : new String(SafeFiles.read(sys), StandardCharsets.UTF_8).split("\r?\n")) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) continue;
					String[] p = line.split("\\s+");
					int level = 1;
					try { if (p.length > 1) level = Math.max(1, Integer.parseInt(p[1])); } catch (NumberFormatException e) { }
					total += Math.max(0, Pricing.systemSale(p[0], level, homeplanet.core.Economy.SYSTEM_SALE_PERCENT) - broken(p) * Pricing.brokenBarValue(p[0])); // as the Cargo Bay sells it
				}
			} catch (Exception e) { }
		}
		return total;
	}

	/** A stored system line's broken bars (its third field), or 0. */
	private static int broken(String[] p) {
		try { return p.length > 2 ? Math.max(0, Integer.parseInt(p[2])) : 0; } catch (NumberFormatException e) { return 0; }
	}
	/**
	 * Everything of value a plea would forfeit, in scrap: the Cargo Hold (scrap, supplies, items,
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
				for (String c : SaveHelper.cargo(gs)) total += Pricing.item(c);
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
					total += Math.max(0, Pricing.system(p[0], level) - broken(p) * Pricing.brokenBarValue(p[0]));
				}
			} catch (Exception e) { log.debug("Free command: the stored systems could not be read: {}", e.toString()); }
		}
		for (Ship j : v.junked()) {
			try { SavedGameState g = j.save(); if (g != null) total += Pricing.ship(g, Pricing.rate()).total(); } catch (Exception e) { log.debug("Free command: {} could not be priced: {}", j.name, e.toString()); }
		}
		return total;
	}
}
