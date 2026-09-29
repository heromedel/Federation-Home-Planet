package homeplanet.parser;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.blerf.ftl.model.AchievementRecord;
import net.blerf.ftl.model.Profile;
import net.blerf.ftl.model.ShipAvailability;
import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.ProfileParser;
import net.blerf.ftl.xml.Achievement;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.core.HomePlanet;

/**
 * Which player ship layouts the FTL profile has unlocked (for the Commission rules).
 * Layout A and C: the profile's own flags. Layout B: 2 of that ship's achievements (the game's rule: "Complete 2/3 of the
 * ... Achievements to unlock this ship"). The Kestrel A is always available.
 */
public class Unlocks {
	private final Profile profile;
	private final String problem;
	private final File file;

	private Unlocks(File file, Profile profile, String problem) {
		this.file = file;
		this.profile = profile;
		this.problem = problem;
	}

	/** Reads the profile beside the saves: ae_prof.sav (Advanced Edition), else prof.sav. */
	public static Unlocks read() {
		File dir = HomePlanet.save_location;
		File ae = new File(dir, "ae_prof.sav"), plain = new File(dir, "prof.sav");
		File f = ae.isFile() ? ae : plain.isFile() ? plain : null;
		if (f == null) return new Unlocks(null, null, "No FTL profile (ae_prof.sav or prof.sav) was found in the saves folder.");
		try {
			return new Unlocks(f, new ProfileParser().readProfile(f), null);
		} catch (Exception e) {
			return new Unlocks(f, null, "The FTL profile " + f.getName() + " could not be read: " + e.getMessage());
		}
	}

	/** Null if the profile was read; otherwise why not (then nothing counts as locked). */
	public String problem() { return problem; }

	/** Is layout n (0 = A, 1 = B, 2 = C) of this base ship (PLAYER_SHIP_HARD...) unlocked? */
	public boolean unlocked(String baseId, int n) {
		if (profile == null) return true;
		if ("PLAYER_SHIP_HARD".equals(baseId) && n == 0) return true;
		ShipAvailability a = profile.getShipUnlockMap().get(baseId);
		if (n == 0) return a != null && a.isUnlockedA();
		if (n == 2) return a != null && a.isUnlockedC();
		int count = 0;
		List<AchievementRecord> recs = profile.getAchievements();
		for (AchievementRecord rec : recs) {
			Achievement ach = DataManager.get().getAchievement(rec.getAchievementId());
			if (ach != null && baseId.equals(ach.getShipId()) && !ach.isVictory()) count++;
		}
		return count >= 2;
	}

	/** Is this blueprint (a player ship layout, e.g. PLAYER_SHIP_STEALTH_3) unlocked? Unknown ids count as unlocked. */
	public boolean unlockedBlueprint(String bpId) {
		int[] n = new int[1];
		String base = baseOf(bpId, n);
		return base == null || unlocked(base, n[0]);
	}

	private static Map<String, String[]> layouts = null;
	/** The base ship id for a player blueprint id, with its layout number in n[0]; null if it isn't one. */
	static synchronized String baseOf(String bpId, int[] n) {
		if (layouts == null) {
			layouts = new HashMap<String, String[]>();
			for (String base : DataManager.get().getPlayerShipBaseIds(true)) {
				for (int v = 0; v < 3; v++) {
					ShipBlueprint bp;
					try { bp = DataManager.get().getPlayerShipVariant(base, v, true); } catch (Exception e) { bp = null; }
					if (bp != null) layouts.put(bp.getId(), new String[] {base, String.valueOf(v)});
				}
			}
		}
		String id = Retrofit.vanillaId(bpId);
		if (id.endsWith(Retrofit.SUFFIX)) id = id.substring(0, id.length() - Retrofit.SUFFIX.length());
		String[] hit = layouts.get(id);
		if (hit == null) return null;
		n[0] = Integer.parseInt(hit[1]);
		return hit[0];
	}

	public File file() { return file; }
}
