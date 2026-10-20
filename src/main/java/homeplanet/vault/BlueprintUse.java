package homeplanet.vault;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Who uses a blueprint (7.02, out of Vault): the ships of this fleet and the others that fly a remodel, so it can't be removed under them. */
final class BlueprintUse {
	private BlueprintUse() { }

	/**
	 * The ships (docked, boarded or junked) whose saves name this blueprint id. A save that can't be read at all
	 * counts as using it, since it might: a locked or damaged file must never get a blueprint retired under it.
	 */
	static List<Ship> usingBlueprint(Vault v, String bpId) {
		List<Ship> out = new ArrayList<Ship>();
		for (Ship s : v.ships) {
			if (s.state == Ship.State.STORAGE) continue;
			List<String> ids = homeplanet.parser.Retrofit.blueprintIds(v.fileOf(s));
			if (ids == null || ids.contains(bpId)) out.add(s);
		}
		return out;
	}

	/** Every blueprint id any ship names (null entries for unreadable files are skipped; see {@link #usingBlueprint}). */
	static java.util.Set<String> blueprintsInUse(Vault v) {
		java.util.Set<String> out = new java.util.LinkedHashSet<String>();
		for (Ship s : v.ships) {
			if (s.state == Ship.State.STORAGE) continue;
			List<String> ids = homeplanet.parser.Retrofit.blueprintIds(v.fileOf(s));
			if (ids != null) out.addAll(ids);
		}
		return out;
	}

	/**
	 * As {@link #blueprintsInUse}, plus every blueprint the ships' kept earlier versions (history) name: a blueprint
	 * in here may still be needed to bring a ship back, so it isn't cleaned up.
	 */
	static java.util.Set<String> blueprintsInUseOrHistory(Vault v) {
		java.util.Set<String> out = blueprintsInUse(v);
		out.addAll(otherFleetBlueprints(v)); // the other fleet flies the same mod
		List<File> saves = new ArrayList<File>();
		for (File d : v.shipFolders()) collectSaves(d, saves, 2); // her save and her kept versions
		for (File f : saves) {
			List<String> ids = homeplanet.parser.Retrofit.blueprintIds(f);
			if (ids != null) out.addAll(ids);
		}
		return out;
	}

	/**
	 * Every blueprint the other fleet's saves name: its docked and junked ships, their kept versions and surrenders.
	 * (Its boarded ship, if any, was docked into it when the player switched fleets.)
	 */
	static java.util.Set<String> otherFleetBlueprints(Vault v) {
		java.util.Set<String> out = new java.util.LinkedHashSet<String>();
		for (File f : otherFleetSaves(v)) {
			List<String> ids = homeplanet.parser.Retrofit.blueprintIds(f);
			if (ids != null) out.addAll(ids);
		}
		return out;
	}

	/** The names of every other fleet's ships (docked or junked) whose saves name this blueprint. */
	static List<String> otherFleetUsing(Vault v, String bpId) {
		List<String> out = new ArrayList<String>();
		for (String k : Vault.SLOTS) {
			if (k.equals(v.slot)) continue;
			File other = Vault.rootOf(v.saves, k);
			for (String dir : new String[] {"junkyard", "shipyard"}) {
				File[] fs = new File(other, dir).listFiles();
				if (fs == null) continue;
				for (File f : fs) {
					String name;
					if (f.isDirectory()) { // her folder (5.69)
						ShipStore.Record r = ShipStore.read(f);
						if (r == null || !ShipStore.sav(f).isFile()) continue;
						f = ShipStore.sav(f);
						name = r.name;
					} else if (f.getName().endsWith(".sav")) { // a stray
						name = f.getName().substring(0, f.getName().length() - 4);
					} else continue;
					List<String> ids = homeplanet.parser.Retrofit.blueprintIds(f);
					if (ids != null && !ids.contains(bpId)) continue; // an unreadable one counts, as usingBlueprint does
					out.add(name + " (" + Vault.title(k) + " fleet)");
				}
			}
		}
		return out;
	}

	static List<File> otherFleetSaves(Vault v) {
		List<File> out = new ArrayList<File>();
		for (File other : v.otherRoots())
			for (String dir : new String[] {"ships", "junkyard", "history", "shipyard", "memorials_and_records", "surrendered"}) collectSaves(new File(other, dir), out, 4); // both layouts
		return out;
	}

	static void collectSaves(File dir, List<File> out, int depth) {
		File[] fs = dir.listFiles();
		if (fs == null) return;
		for (File f : fs) {
			if (f.isDirectory() && depth > 1) collectSaves(f, out, depth - 1);
			else if (f.isFile() && f.getName().endsWith(".sav")) out.add(f);
		}
	}
}
