package homeplanet.parser;

import java.util.ArrayList;
import java.util.List;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;
import net.blerf.ftl.xml.AugBlueprint;
import net.blerf.ftl.xml.CrewBlueprint;
import net.blerf.ftl.xml.DroneBlueprint;
import net.blerf.ftl.xml.SystemBlueprint;
import net.blerf.ftl.xml.WeaponBlueprint;

/**
 * What things cost, from FTL's own blueprints: systems (their price and upgrade costs), reactor power, weapons, drones,
 * augments and crew. The house rules that sell systems or charge for a new ship use these.
 */
public final class Pricing {
	private Pricing() { }

	/** A system with no price of its own (FTL's subsystems in some designs, a system a mod adds): what the station charges. */
	public static final int UNPRICED_SYSTEM = 25;
	/**
	 * Piloting, Oxygen and Engines at level 1 (heromedel's price, everywhere; FTL prices them as next to nothing, being
	 * standard): what Commission charges and what a Junkyard part is worth, FTL's upgrade costs on top.
	 */
	public static final int CORE_SYSTEM = 150;
	/**
	 * Her hull, strictly counted (heromedel, 5.00): each point of her model's hull, each room her blueprint reserves for a
	 * system (a tenth of that system's level-1 price), each room without one, and each door.
	 */
	public static final int HULL_POINT = 10, SYSTEM_ROOM_PERCENT = 10, EMPTY_ROOM = 2, DOOR = 2;
	/**
	 * The rate the difficulty sets on a ship's price, wherever one is counted: Commission, the plea, what she's worth at
	 * Trade In, Auction, the museum and a final victory, and the Junkyard's parts and derelicts (before the Junkyard's own
	 * discounts). Easy 50, Normal 75, Hard 100; Custom and Sandbox choose. Never the stores' prices: buying and repairing
	 * in the Cargo Bay cost the same on every difficulty.
	 */
	public static int rate() { return homeplanet.core.Economy.commissionPercent(); }
	/** A price at the rate, to the nearest scrap. */
	public static int rated(int price) { return (price * rate() + 50) / 100; }
	/** An artillery weapon with no price (the Federation cruiser's): what the station charges for it. */
	public static final int UNPRICED_ARTILLERY = 100;
	/** Artillery guns are a luxury (heromedel's prices): the Artillery Beam, the Type C's Flak Artillery, and each of the Rebel Flagship's weapons. */
	public static final int FEDERATION_ARTILLERY = 200, FLAK_ARTILLERY = 150, FLAGSHIP_ARTILLERY = 100;
	/** An artillery weapon's price: the luxury prices above, else FTL's price, else UNPRICED_ARTILLERY. */
	public static int artillery(String id) {
		if ("ARTILLERY_FED".equals(id)) return FEDERATION_ARTILLERY;
		if ("ARTILLERY_FED_C".equals(id)) return FLAK_ARTILLERY;
		if (id != null && id.startsWith("ARTILLERY_BOSS")) return FLAGSHIP_ARTILLERY;
		WeaponBlueprint w = id == null ? null : DataManager.get().getWeapons().get(id);
		return w != null && w.getCost() > 0 ? w.getCost() : UNPRICED_ARTILLERY;
	}

	/** A system at a level: its price plus each upgrade to that level; CORE_SYSTEM for Piloting, Oxygen and Engines; UNPRICED_SYSTEM if FTL gives it no price. */
	public static int system(String id, int level) {
		SystemBlueprint b = DataManager.get().getSystem(id);
		boolean core = isCore(id);
		if (b == null || (b.getCost() <= 0 && !core)) return UNPRICED_SYSTEM;
		int p = core ? CORE_SYSTEM : b.getCost();
		List<Integer> up = b.getUpgradeCosts();
		for (int l = 2; l <= level && up != null && l - 2 < up.size(); l++) p += up.get(l - 2);
		return p;
	}
	/** HR1: what a system sells for, half its price and half the upgrades paid for (level 0: a Clone Bay, which has no level of its own). */
	public static int systemSale(String id, int level) {
		return systemSale(id, level, 50);
	}
	/** A system's sale at this share of its price and upgrades (Immersive Mode pays 25%). */
	public static int systemSale(String id, int level, int percent) {
		return system(id, Math.max(1, level)) * percent / 100;
	}

	/** What upgrading a system from this level to the next costs, or -1 if FTL has no next level for it. */
	public static int upgrade(String id, int level) {
		SystemBlueprint b = DataManager.get().getSystem(id);
		if (b == null || b.getUpgradeCosts() == null || level < 1 || level - 1 >= b.getUpgradeCosts().size()) return -1;
		if (b.getMaxPower() > 0 && level >= b.getMaxPower()) return -1;
		return b.getUpgradeCosts().get(level - 1);
	}
	/** FTL's reactor limit (Advanced Edition). */
	public static final int REACTOR_MAX = 25;
	/** One point of hull repaired in the Dry Dock: a flat 4 scrap, the top of FTL's store prices (The Federation charges a premium). */
	public static final int HULL_REPAIR = 4;
	public static int hullRepair() { return HULL_REPAIR; }
	/** Mending one broken bar of a system, and sealing one hull breach, in the Dry Dock. */
	public static final int SYSTEM_REPAIR = 5, BREACH_REPAIR = 5;
	/** A custom work order: The Home Planet Station fits a system past FTL's System Limit (heromedel: always 100, whatever the system). */
	public static final int WORK_ORDER = 100;
	/** Her systems past FTL's System Limit, each a custom work order (a design can start with more than FTL allows). */
	public static int workOrders(ShipState s) {
		return Math.max(0, SaveHelper.systemCount(s) - SaveHelper.SYSTEMS_MAX);
	}

	/** The price of the reactor's nth bar (1-based), as FTL's upgrade screen charges: 15 for bars 1-5, then 5 more every 5 bars (35 for 21-25). */
	public static int reactorBar(int n) {
		return 15 + 5 * ((Math.max(1, n) - 1) / 5);
	}
	/** Reactor power, bar by bar from the first. */
	public static int reactor(int bars) {
		int p = 0;
		for (int n = 1; n <= bars; n++) p += reactorBar(n);
		return p;
	}

	/** A weapon, drone or augment at store price; an unpriced artillery weapon at UNPRICED_ARTILLERY; 0 if unknown. */
	public static int item(String id) {
		WeaponBlueprint w = DataManager.get().getWeapons().get(id);
		if (w != null) return id.toUpperCase().startsWith("ARTILLERY") ? artillery(id) : Math.max(0, w.getCost());
		DroneBlueprint d = DataManager.get().getDrones().get(id);
		if (d != null) return Math.max(0, d.getCost());
		AugBlueprint a = DataManager.get().getAugments().get(id);
		if (a != null) return Math.max(0, a.getCost());
		return 0;
	}
	/** A crew member of this race at hiring price (0 if unknown). */
	public static int crew(String race) {
		CrewBlueprint c = DataManager.get().getCrews().get(race);
		return c == null ? 0 : Math.max(0, c.getCost());
	}

	/** FTL's store prices for one fuel, missile and drone part. */
	public static final int FUEL = 3, MISSILE = 6, DRONE_PART = 8;
	/** Trade In and Auction: each point of missing hull takes this much off her value. */
	public static final int HULL_DAMAGE = 5;

	/** Her fuel, missiles and drone parts at store price (part of every ship's value). */
	public static int supplies(ShipState s) {
		return s.getFuelAmt() * FUEL + s.getMissilesAmt() * MISSILE + s.getDronePartsAmt() * DRONE_PART;
	}
	/** What she's worth to a buyer, before her hull damage: her price at the rate (with her fuel, missiles and drone parts), crew aside (they stay with the fleet). */
	public static int saleValue(SavedGameState gs) {
		ShipState s = gs.getPlayerShip();
		int crew = 0;
		for (CrewState c : SaveHelper.getOwnCrew(s)) crew += crew(c.getRace().getId());
		return rated(ship(gs, 100).subtotal - crew);
	}
	/** Her model's full hull (what she has, when her blueprint isn't in FTL's data). */
	public static int maxHull(ShipState s) {
		net.blerf.ftl.xml.ShipBlueprint bp = DataManager.get().getShip(s.getShipBlueprintId());
		return bp == null || bp.getHealth() == null ? s.getHullAmt() : bp.getHealth().amount;
	}
	/** Her missing hull points (her model's full hull, less what she has). */
	public static int missingHull(ShipState s) {
		return Math.max(0, maxHull(s) - s.getHullAmt());
	}
	/** Her broken system bars (each one mended in the Dry Dock for SYSTEM_REPAIR). */
	public static int brokenBars(ShipState s) {
		int n = 0;
		for (SystemType t : SystemType.values()) { SystemState st = s.getSystem(t); if (st != null && st.getCapacity() > 0) n += st.getDamagedBars(); }
		return n;
	}
	/** What a buyer takes off for her damage: 5 scrap a missing hull point, 5 a breach, and her broken bars ({@link #brokenBarValue}). */
	public static int damage(ShipState s) {
		return HULL_DAMAGE * missingHull(s) + brokenValue(s) + BREACH_REPAIR * s.getBreachMap().size();
	}
	/** A broken bar takes this much off a ship's or a part's value: 5, and 10 for Piloting, Oxygen and Engines (she can't do without them). */
	public static int brokenBarValue(String systemId) {
		return isCore(systemId) ? CORE_BAR_DAMAGE : SYSTEM_REPAIR;
	}
	/** Whether this is one of the three core systems. */
	public static boolean isCore(String systemId) {
		for (SystemType t : CORE) if (t.getId().equals(systemId)) return true;
		return false;
	}
	public static final int CORE_BAR_DAMAGE = 10;
	/** Her broken bars, each at {@link #brokenBarValue}. */
	public static int brokenValue(ShipState s) {
		int n = 0;
		for (SystemType t : SystemType.values()) { SystemState st = s.getSystem(t); if (st != null && st.getCapacity() > 0) n += st.getDamagedBars() * brokenBarValue(t.getId()); }
		return n;
	}
	/** Systems a buyer won't do without: no Engines or Piloting and she can't fly, no Oxygen and no one can live aboard. */
	public static final SystemType[] CORE = {SystemType.ENGINES, SystemType.PILOT, SystemType.OXYGEN};
	/** Each core system she's missing takes this many points off what Trade In and Auction pay. */
	public static final int CORE_PENALTY = 15;
	/** The core systems she doesn't have installed, whatever her model or design: few buyers want a ship without them. */
	public static List<SystemType> missingCore(ShipState s) {
		List<SystemType> out = new ArrayList<SystemType>();
		for (SystemType t : CORE) { SystemState st = s.getSystem(t); if (st == null || st.getCapacity() <= 0) out.add(t); }
		return out;
	}
	/** Trade In: half her value (15 points less for each missing core system), less her damage (never below 0). */
	public static int tradeIn(SavedGameState gs) {
		int share = Math.max(5, 50 - CORE_PENALTY * missingCore(gs.getPlayerShip()).size());
		return Math.max(0, saleValue(gs) * share / 100 - damage(gs.getPlayerShip()));
	}
	/** Auction (and a derelict's price): what the bidding starts from, her value less her damage (never below 0). */
	public static int auctionBase(SavedGameState gs) {
		return Math.max(0, saleValue(gs) - damage(gs.getPlayerShip()));
	}
	/** The lowest and highest share bidders offer: 25% to 75%, both 15 points less for each missing core system (never under 5%). */
	public static int[] auctionRange(ShipState s) {
		int k = CORE_PENALTY * missingCore(s).size();
		return new int[] {Math.max(5, 25 - k), Math.max(5, 75 - k)};
	}
	/** The best bid at auction: within {@link #auctionRange} of {@link #auctionBase}, the same for the same save (bidders don't change their minds). */
	public static int auction(SavedGameState gs, long seed) {
		int[] r = auctionRange(gs.getPlayerShip());
		int pct = r[0] + new java.util.Random(seed).nextInt(r[1] - r[0] + 1);
		return auctionBase(gs) * pct / 100;
	}

	/** A priced list: its lines (for the player) and total, before and after the multiplier. */
	public static final class Quote {
		public final List<String> lines = new ArrayList<String>();
		public int subtotal;
		public int percent = 100;
		/** Added after the multiplier: custom work orders, always their own price. */
		public int fixed;
		public int total() { return (subtotal * percent + 50) / 100 + fixed; }
		void add(String what, int price) { if (price <= 0) return; lines.add(what + ": " + price); subtotal += price; }
	}

	/**
	 * Her price, strictly counted (heromedel, 5.00): her hull (each point of it), reactor, systems and their levels,
	 * weapons, drones and augments (her cargo too), crew at hiring price, fuel, missiles and drone parts, the scrap aboard at
	 * face value, and her rooms and doors (each room her blueprint reserves for a system she has at a tenth of that system's
	 * level-1 price, each other room and each door at a flat rate). The same for FTL's own ships, remodels and custom designs; the
	 * rate ({@code percent}) is what the station charges of it.
	 */
	public static Quote ship(SavedGameState gs, int percent) {
		Quote q = new Quote();
		q.percent = percent;
		ShipState s = gs.getPlayerShip();
		int hull = maxHull(s);
		q.add("Hull (" + hull + " points)", hull * HULL_POINT);
		q.add("Reactor (" + s.getReservePowerCapacity() + " power)", reactor(s.getReservePowerCapacity()));
		int sys = 0;
		for (SystemType t : SystemType.values()) {
			SystemState st = s.getSystem(t);
			if (st == null || st.getCapacity() <= 0) continue;
			sys += system(t.getId(), st.getCapacity());
		}
		q.add("Systems and their levels", sys);
		int gear = 0;
		for (WeaponState w : s.getWeaponList()) gear += item(w.getWeaponId());
		for (DroneState d : s.getDroneList()) gear += item(d.getDroneId());
		for (String a : s.getAugmentIdList()) gear += item(a);
		for (String c : gs.getCargoIdList()) gear += item(c);
		SystemState art = s.getSystem(SystemType.ARTILLERY);
		if (art != null && art.getCapacity() > 0) gear += artillery(Commission.artilleryWeapon(gs.getPlayerShipBlueprintId())); // the gun her artillery fires
		q.add("Weapons, drones and augments", gear);
		int crew = 0, n = 0;
		for (CrewState c : SaveHelper.getOwnCrew(s)) { crew += crew(c.getRace().getId()); n++; }
		q.add("Crew (" + n + ")", crew);
		q.add("Fuel, missiles and drone parts", supplies(s));
		q.add("Scrap aboard", s.getScrapAmt());
		// her rooms: the ones her blueprint reserves for a system she has (one she starts without is just a room), then the rest, then her doors
		java.util.Set<Integer> reserved = new java.util.HashSet<Integer>();
		int roomPrice = 0;
		for (CompanionMod.Sys y : CompanionMod.layoutOf(DataManager.get().getShip(s.getShipBlueprintId())).values()) {
			SystemState st = s.getSystem(SystemType.findById(y.id));
			if (st == null || st.getCapacity() <= 0 || y.room < 0 || !reserved.add(y.room)) continue;
			roomPrice += system(y.id, 1) * SYSTEM_ROOM_PERCENT / 100;
		}
		int rooms = s.getRoomList().size(), empty = Math.max(0, rooms - reserved.size());
		q.add("Rooms with a system (" + reserved.size() + ")", roomPrice);
		q.add("Rooms without (" + empty + ")", empty * EMPTY_ROOM);
		q.add("Doors (" + s.getDoorMap().size() + ")", s.getDoorMap().size() * DOOR);
		return q;
	}
	/**
	 * What Commission charges for her: her price at this rate, and a custom work order for each system she starts with
	 * past FTL's System Limit (outside the rate: always WORK_ORDER). Never part of her value, so nothing of it comes back.
	 */
	public static Quote commission(SavedGameState gs, int percent) {
		Quote q = ship(gs, percent);
		int n = workOrders(gs.getPlayerShip());
		if (n > 0) {
			q.fixed = n * WORK_ORDER;
			q.lines.add((n == 1 ? "A custom work order (1 system" : n + " custom work orders (" + n + " systems") + " past FTL's System Limit): " + q.fixed);
		}
		return q;
	}
}
