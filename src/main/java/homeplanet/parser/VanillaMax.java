package homeplanet.parser;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.xml.ShipBlueprint;
import net.blerf.ftl.xml.SystemBlueprint;

/**
 * The highest any of the game's own player ships has of each number a design sets (hull, reactor, weapon and drone
 * slots, missiles, drone parts), read from the game's data, and each system's top level. The design editor shows
 * these beside the player's own numbers as "vanilla max": a guide, never a cap (past them FTL still takes the ship,
 * but its bars and upgrade screen are drawn for the vanilla numbers). The crew ceiling is the one number FTL itself
 * fixes, for every ship.
 */
public final class VanillaMax {
	private VanillaMax() { }

	/** FTL's crew limit, built into the game: no blueprint or save can move it. */
	public static final int CREW = 8;

	private static int hull = -1, reactor, weaponSlots, droneSlots, missiles, droneParts;

	private static synchronized void read() {
		if (hull >= 0) return;
		int h = 0, r = 0, ws = 0, ds = 0, m = 0, dp = 0;
		try {
			for (ShipBlueprint bp : DataManager.get().getPlayerShips().values()) {
				if (bp.getHealth() != null) h = Math.max(h, bp.getHealth().amount);
				if (bp.getMaxPower() != null) r = Math.max(r, bp.getMaxPower().amount);
				if (bp.getWeaponSlots() != null) ws = Math.max(ws, bp.getWeaponSlots());
				if (bp.getDroneSlots() != null) ds = Math.max(ds, bp.getDroneSlots());
				if (bp.getWeaponList() != null) m = Math.max(m, bp.getWeaponList().missiles);
				if (bp.getDroneList() != null) dp = Math.max(dp, bp.getDroneList().drones);
			}
		} catch (Exception e) { // no game data loaded (a test without it): the game's usual numbers
		}
		hull = h > 0 ? h : 30; reactor = r > 0 ? r : 8; weaponSlots = ws > 0 ? ws : 4; droneSlots = ds > 0 ? ds : 3;
		missiles = m > 0 ? m : 28; droneParts = dp > 0 ? dp : 15;
	}
	public static int hull() { read(); return hull; }
	public static int reactor() { read(); return reactor; }
	public static int weaponSlots() { read(); return weaponSlots; }
	public static int droneSlots() { read(); return droneSlots; }
	public static int missiles() { read(); return missiles; }
	public static int droneParts() { read(); return droneParts; }
	/** A system's top level in the game (its blueprint's max power), or 8 when unknown. */
	public static int system(String id) {
		try {
			SystemBlueprint sb = DataManager.get().getSystem(id);
			if (sb != null && sb.getMaxPower() > 0) return sb.getMaxPower();
		} catch (Exception e) { }
		return 8;
	}
}
