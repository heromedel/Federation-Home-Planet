package homeplanet.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.xml.AugBlueprint;
import net.blerf.ftl.xml.DroneBlueprint;
import net.blerf.ftl.xml.WeaponBlueprint;

/** What kind of thing an item id is (weapon, drone, augment), and the name players see for it. */
public final class Items {
	private Items() { }

	// Looked up in the maps directly, because the single-item getters log an error for every miss.
	public static boolean isWeapon(String id) { return DataManager.get().getWeapons().containsKey(id); }
	public static boolean isAugment(String id) { return DataManager.get().getAugments().containsKey(id); }
	public static boolean isDrone(String id) { return DataManager.get().getDrones().containsKey(id); }

	// Display names. Falls back to the raw id if the game data doesn't know the item (e.g. a mod was removed).
	public static String weaponTitle(String id) {
		WeaponBlueprint b = DataManager.get().getWeapons().get(id);
		return (b != null && b.getTitle() != null) ? b.getTitle().getTextValue() : id;
	}
	public static String augmentTitle(String id) {
		AugBlueprint b = DataManager.get().getAugments().get(id);
		return (b != null && b.getTitle() != null) ? b.getTitle().getTextValue() : id;
	}
	public static String droneTitle(String id) {
		DroneBlueprint b = DataManager.get().getDrones().get(id);
		return (b != null && b.getTitle() != null) ? b.getTitle().getTextValue() : id;
	}
	/** The name of any item, whatever its kind; the id itself when nothing knows it. */
	public static String title(String id) {
		if (isWeapon(id)) return weaponTitle(id);
		if (isDrone(id)) return droneTitle(id);
		if (isAugment(id)) return augmentTitle(id);
		return id;
	}
	/** The weapons FTL's stores sell (a price and a rarity: no artillery, no Flagship guns), in id order (7.01). */
	public static List<String> soldWeapons() {
		List<String> out = new ArrayList<String>();
		for (WeaponBlueprint w : DataManager.get().getWeapons().values())
			if (w.getCost() > 0 && w.getRarity() > 0 && !w.getId().startsWith("ARTILLERY")) out.add(w.getId());
		Collections.sort(out);
		return out;
	}
	/** The drones FTL's stores sell, in id order. */
	public static List<String> soldDrones() {
		List<String> out = new ArrayList<String>();
		for (DroneBlueprint d : DataManager.get().getDrones().values()) if (d.getCost() > 0 && d.getRarity() > 0) out.add(d.getId());
		Collections.sort(out);
		return out;
	}
	/** The augments FTL's stores sell, in id order. */
	public static List<String> soldAugments() {
		List<String> out = new ArrayList<String>();
		for (AugBlueprint a : DataManager.get().getAugments().values()) if (a.getCost() > 0 && a.getRarity() > 0) out.add(a.getId());
		Collections.sort(out);
		return out;
	}
	/** A ship system's name ("Clone Bay" for clonebay); the id itself when the game data doesn't have it. */
	public static String systemTitle(String id) {
		try {
			net.blerf.ftl.xml.SystemBlueprint s = DataManager.get().getSystem(id);
			String t = (s == null || s.getTitle() == null) ? null : s.getTitle().getTextValue();
			if (t != null && !t.isEmpty()) return t;
		} catch (Exception e) { }
		net.blerf.ftl.parser.SavedGameParser.SystemType t = net.blerf.ftl.parser.SavedGameParser.SystemType.findById(id);
		return t != null ? t.toString() : id;
	}
}
