package homeplanet.parser;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;

/**
 * Advanced Edition rules for trading. A save made with AE content off can't hold items or crew that only exist in
 * the Advanced Edition (the game data lists the standard items separately, so "AE only" is what's missing there).
 * The storage hold keeps everything, and these checks decide what may leave it for which ship.
 */
public final class Dlc {
	private Dlc() { }

	/** True if the item (weapon, drone or augment) exists only in the Advanced Edition. */
	public static boolean aeOnlyItem(String id) {
		DataManager dm = DataManager.get();
		if (dm.getWeapons(false).containsKey(id) || dm.getDrones(false).containsKey(id) || dm.getAugments(false).containsKey(id)) return false;
		return dm.getWeapons(true).containsKey(id) || dm.getDrones(true).containsKey(id) || dm.getAugments(true).containsKey(id);
	}
	/** True if the crew member's race (Lanius, say) exists only in the Advanced Edition. */
	public static boolean aeOnlyCrew(CrewState c) {
		if (c == null || c.getRace() == null) return false;
		return !DataManager.get().getCrews(false).containsKey(c.getRace().getId());
	}
	/** The Advanced Edition's own systems: a ship that starts with one needs AE content on. */
	private static final SystemType[] AE_SYSTEMS = {SystemType.HACKING, SystemType.MIND, SystemType.CLONEBAY, SystemType.BATTERY};

	/**
	 * Why a new ship must be made with Advanced Edition content on, or null if she may go without it. FTL keeps the
	 * Type C layouts and the Lanius ships for AE content alone; any ship (a remodel or a design too) that starts with
	 * AE-only crew, gear or systems needs it as well.
	 */
	public static String needsAE(String blueprintId, SavedGameState built) {
		DataManager dm = DataManager.get();
		String base = blueprintId == null ? "" : blueprintId.endsWith(Retrofit.SUFFIX) ? blueprintId.substring(0, blueprintId.length() - Retrofit.SUFFIX.length()) : blueprintId;
		if (!base.isEmpty() && !dm.getShips(false).containsKey(base) && dm.getShips(true).containsKey(base))
			return "This layout exists only in the Advanced Edition.";
		if (built == null || built.getPlayerShip() == null) return null;
		ShipState ship = built.getPlayerShip();
		for (CrewState c : ship.getCrewList()) if (aeOnlyCrew(c)) return homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " crew includes an Advanced Edition race.";
		for (String g : SaveHelper.gear(ship)) if (aeOnlyItem(g)) return homeplanet.model.Items.title(g) + " is Advanced Edition only.";
		for (SystemType t : AE_SYSTEMS) {
			SystemState sys = ship.getSystem(t);
			if (sys != null && sys.getCapacity() > 0) return homeplanet.model.Words.cap(homeplanet.model.Words.she()) + " starts with an Advanced Edition system.";
		}
		return null;
	}
	/** Why this ship can't take the item, or null if she can. */
	public static String refusesItem(SavedGameState receiver, String id) {
		if (receiver == null || receiver.isDLCEnabled() || !aeOnlyItem(id)) return null;
		return receiver.getPlayerShipName() + " was made with Advanced Edition content off; " + homeplanet.model.Items.title(id) + " is Advanced Edition only.";
	}
	/** Why this ship can't take the crew member, or null if she can. */
	public static String refusesCrew(SavedGameState receiver, CrewState c) {
		if (receiver == null || receiver.isDLCEnabled() || !aeOnlyCrew(c)) return null;
		return receiver.getPlayerShipName() + " was made with Advanced Edition content off; " + c.getName() + "'s race is Advanced Edition only.";
	}
}
