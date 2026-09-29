package homeplanet.parser;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

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
