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
	/** Custom designs: each room and door the shipyard builds. */
	public static final int PER_ROOM = 10, PER_DOOR = 5;
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

	/** A system at a level: its price plus each upgrade to that level. UNPRICED_SYSTEM if FTL gives it no price. */
	public static int system(String id, int level) {
		SystemBlueprint b = DataManager.get().getSystem(id);
		if (b == null || b.getCost() <= 0) return UNPRICED_SYSTEM;
		int p = b.getCost();
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

	/** A priced list: its lines (for the player) and total, before and after the multiplier. */
	public static final class Quote {
		public final List<String> lines = new ArrayList<String>();
		public int subtotal;
		public int percent = 100;
		public int total() { return (subtotal * percent + 50) / 100; }
		void add(String what, int price) { if (price <= 0) return; lines.add(what + ": " + price); subtotal += price; }
	}

	/**
	 * HR2: what the shipyard charges for a new ship, as commissioned: her systems and their levels, reactor, weapons,
	 * drones, augments (her cargo too) and crew. A custom design also pays for each room and door
	 * ({@code rooms}, {@code doors}; 0 for FTL's own ships and remodels of them).
	 */
	public static Quote ship(SavedGameState gs, int rooms, int doors, int percent) {
		Quote q = new Quote();
		q.percent = percent;
		ShipState s = gs.getPlayerShip();
		int sys = 0;
		for (SystemType t : SystemType.values()) {
			SystemState st = s.getSystem(t);
			if (st == null || st.getCapacity() <= 0) continue;
			sys += system(t.getId(), st.getCapacity());
		}
		q.add("Systems and their levels", sys);
		q.add("Reactor (" + s.getReservePowerCapacity() + " power)", reactor(s.getReservePowerCapacity()));
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
		if (rooms > 0) q.add("Custom hull (" + rooms + " rooms, " + doors + " doors)", rooms * PER_ROOM + doors * PER_DOOR);
		return q;
	}
}
