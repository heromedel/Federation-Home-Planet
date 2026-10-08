package homeplanet.parser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.parser.DataManager;

import homeplanet.core.SafeFiles;
import homeplanet.vault.Vault;

/**
 * The unlock-once house rule: each FTL ship layout unlocked in the profile after the rule was turned on can be
 * commissioned free, once. Kept in the vault (unlock-grants.txt), so a Report for Reassignment doesn't reset it:
 * "seen BASE n" for layouts already unlocked when the rule was turned on (they never count), "claimed BASE n" for
 * free ships already taken.
 */
public final class UnlockGrants {
	private static final Logger log = LoggerFactory.getLogger(UnlockGrants.class);
	private UnlockGrants() { }

	static File file() { return new File(Vault.get().root, "unlock-grants.txt"); }

	private static Set<String> read(String kind) {
		Set<String> out = new LinkedHashSet<String>();
		File f = file();
		if (!f.isFile()) return out;
		try {
			for (String line : new String(SafeFiles.read(f), StandardCharsets.UTF_8).split("\r?\n")) {
				if (line.startsWith(kind + " ")) out.add(line.substring(kind.length() + 1).trim());
			}
		} catch (Exception e) {
			log.warn("Could not read {}: {}", f, e.toString());
		}
		return out;
	}
	private static void append(String kind, Set<String> keys) throws java.io.IOException {
		if (keys.isEmpty()) return;
		File f = file();
		StringBuilder sb = new StringBuilder(f.isFile() ? new String(SafeFiles.read(f), StandardCharsets.UTF_8) : "");
		if (!f.isFile()) sb.append("# Free ships for FTL unlocks (the unlock-once house rule): seen = unlocked before the rule was on, claimed = taken\n");
		for (String k : keys) sb.append(kind).append(' ').append(k).append('\n');
		SafeFiles.writeText(f, sb.toString(), false);
	}

	/** The rule was just turned on (or first used): every layout unlocked now is seen, and won't count. */
	public static void turnedOn(Unlocks u) {
		if (u == null || u.problem() != null) return;
		Set<String> seen = read("seen"), add = new LinkedHashSet<String>();
		for (String k : unlockedNow(u)) if (!seen.contains(k)) add.add(k);
		try { append("seen", add); } catch (Exception e) { log.warn("Could not record the unlocked ships: {}", e.toString()); }
	}

	/**
	 * An uncommissioned ship boarded in Immersive Mode (5.54): what's unlocked now is seen, never the career's; and what's
	 * new is noted as hers (heromedel, 5.55), for the offer to take it back out of FTL's profile.
	 */
	public static void strangerSeen(Unlocks u) {
		if (u == null || u.problem() != null) return;
		if (!file().isFile()) { turnedOn(u); return; } // nothing known before: all seen, none told apart as hers
		Set<String> seen = read("seen"), add = new LinkedHashSet<String>();
		for (String k : unlockedNow(u)) if (!seen.contains(k)) add.add(k);
		try { append("seen", add); append("stranger", add); } catch (Exception e) { log.warn("Could not record the unlocks seen: {}", e.toString()); }
	}
	/** Is the record begun (what was unlocked before is known)? */
	public static boolean recorded() { return Vault.isOpen() && file().isFile(); }
	/** Was this unlocked before the record began, or while an uncommissioned ship was boarded (never the career's)? */
	public static boolean seen(String key) { return read("seen").contains(key); }
	/** What came into FTL's profile while an uncommissioned ship was boarded, not yet answered ("ACH:" and an id, or a base ship and a layout). */
	public static Set<String> strangers() { return Vault.isOpen() ? read("stranger") : new LinkedHashSet<String>(); }
	/** One of them in words: the achievement's name, or the layout's ("Engi Cruiser, Type A"). */
	public static String describe(String key) {
		if (key.startsWith(ACH)) {
			try { return DataManager.get().getAchievement(key.substring(ACH.length())).getName().getTextValue(); } catch (Exception e) { return key.substring(ACH.length()); }
		}
		String[] w = key.split(" ");
		try { return Transmissions.layoutName(w[0], Integer.parseInt(w[1])); } catch (Exception e) { return key; }
	}
	/** The offer answered: forgotten (and, when they were taken out of the profile, no longer seen: earned again by the career, they count). */
	public static void strangersAnswered(Set<String> removed) {
		File f = file();
		if (!f.isFile()) return;
		try {
			StringBuilder sb = new StringBuilder();
			for (String line : new String(SafeFiles.read(f), StandardCharsets.UTF_8).split("\r?\n")) {
				if (line.isEmpty() || line.startsWith("stranger ")) continue;
				if (line.startsWith("seen ") && removed.contains(line.substring(5).trim())) continue;
				sb.append(line).append('\n');
			}
			SafeFiles.writeText(f, sb.toString(), false);
		} catch (Exception e) {
			log.warn("Could not note the answer about the unlocks: {}", e.toString());
		}
	}
	/** The player's answer to the offer, Remove them: taken out of FTL's profile, the offer answered, logged. Returns the backup made first. */
	public static File removeStrangers(Set<String> keys, java.util.List<String> names) throws java.io.IOException {
		File backup = removeFromProfile(keys);
		strangersAnswered(keys);
		log.info("FTL profile backed up before the removal: {}", backup);
		homeplanet.core.HistoryLog.entry("PROFILE", "Removed from FTL's profile: " + String.join(", ", names), null,
				homeplanet.core.Event.of("PROFILE").put("what", "removed").put("removed", String.join(", ", names)).put("keys", String.join(", ", keys)));
		return backup;
	}
	/**
	 * Takes these out of FTL's profile (heromedel, 5.55), a dated backup made first: the achievements, and layouts A and C
	 * (a Type B follows its ship's achievements; the Kestrel A always stays). FTL must be closed. Returns the backup.
	 */
	public static File removeFromProfile(Set<String> keys) throws java.io.IOException {
		File saves = homeplanet.core.HomePlanet.save_location;
		File prof = homeplanet.core.ProfileSwap.current(saves);
		if (prof == null) throw new java.io.IOException("There's no FTL profile (ae_prof.sav or prof.sav) in " + saves);
		File backup = homeplanet.core.ProfileSwap.backup(saves, Vault.get().root);
		net.blerf.ftl.model.Profile p = new net.blerf.ftl.parser.ProfileParser().readProfile(prof);
		for (java.util.Iterator<net.blerf.ftl.model.AchievementRecord> it = p.getAchievements().iterator(); it.hasNext(); ) {
			if (keys.contains(ACH + it.next().getAchievementId())) it.remove();
		}
		for (String k : keys) {
			if (k.startsWith(ACH)) continue;
			String[] w = k.split(" ");
			if (w.length != 2 || (w[0].equals("PLAYER_SHIP_HARD") && w[1].equals("0"))) continue;
			net.blerf.ftl.model.ShipAvailability a = p.getShipUnlockMap().get(w[0]);
			if (a == null) continue;
			if (w[1].equals("0")) a.setUnlockedA(false);
			else if (w[1].equals("2")) a.setUnlockedC(false);
		}
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		new net.blerf.ftl.parser.ProfileParser().writeProfile(out, p);
		SafeFiles.write(prof, out.toByteArray());
		return backup;
	}

	/** A career begins (Sandbox Mode's Career messages): achievements earned until now are seen, and never rewarded. */
	public static void achievementsSeen(Unlocks u) {
		if (u == null || u.problem() != null) return;
		if (!file().isFile()) { turnedOn(u); return; }
		Set<String> seen = read("seen"), add = new LinkedHashSet<String>();
		for (String a : u.achievements()) if (!seen.contains(ACH + a)) add.add(ACH + a);
		try { append("seen", add); } catch (Exception e) { log.warn("Could not record the achievements earned: {}", e.toString()); }
	}

	/** The layouts unlocked now, as keys, and the achievements earned ("ACH:" and the id). */
	private static Set<String> unlockedNow(Unlocks u) {
		Set<String> out = new LinkedHashSet<String>();
		for (String base : DataManager.get().getPlayerShipBaseIds(true))
			for (int n = 0; n < 3; n++) if (u.unlocked(base, n)) out.add(base + " " + n);
		for (String a : u.achievements()) out.add(ACH + a);
		return out;
	}
	private static final String ACH = "ACH:";
	/** Achievements earned since the record began (in this fleet, not while away), for the rewards. */
	public static Set<String> newAchievements(Unlocks u) {
		Set<String> out = new LinkedHashSet<String>();
		if (u == null || u.problem() != null || !Vault.isOpen()) return out;
		if (!file().isFile()) turnedOn(u);
		Set<String> seen = read("seen");
		for (String a : u.achievements()) if (!seen.contains(ACH + a)) out.add(a);
		return out;
	}
	/** Leaving this fleet (Immersive Mode off): what's unlocked now is noted, so unlocks made while away never count. */
	public static void leaving(Unlocks u) {
		if (u == null || u.problem() != null || !file().isFile()) return;
		try {
			StringBuilder sb = new StringBuilder();
			for (String line : new String(SafeFiles.read(file()), StandardCharsets.UTF_8).split("\r?\n")) {
				if (!line.isEmpty() && !line.startsWith("away ")) sb.append(line).append('\n');
			}
			for (String k : unlockedNow(u)) sb.append("away ").append(k).append('\n');
			SafeFiles.writeText(file(), sb.toString(), false);
		} catch (Exception e) {
			log.warn("Could not note the unlocked ships: {}", e.toString());
		}
	}
	/** Back in this fleet: unlocks made while away are seen (they never count); unclaimed ones from before still do. */
	public static void returning(Unlocks u) {
		if (u == null || u.problem() != null) return;
		if (!file().isFile()) { turnedOn(u); return; }
		Set<String> away = read("away");
		if (away.isEmpty()) return;
		Set<String> seen = read("seen"), add = new LinkedHashSet<String>();
		for (String k : unlockedNow(u)) if (!away.contains(k) && !seen.contains(k)) add.add(k);
		try {
			StringBuilder sb = new StringBuilder();
			for (String line : new String(SafeFiles.read(file()), StandardCharsets.UTF_8).split("\r?\n")) {
				if (!line.isEmpty() && !line.startsWith("away ")) sb.append(line).append('\n');
			}
			for (String k : add) sb.append("seen ").append(k).append('\n');
			SafeFiles.writeText(file(), sb.toString(), false);
		} catch (Exception e) {
			log.warn("Could not record the unlocked ships: {}", e.toString());
		}
	}

	/** Is this standard layout free now: unlocked since the rule was turned on, and not yet claimed? */
	public static boolean freeNow(Unlocks u, String bpId) {
		if (u == null || u.problem() != null) return false;
		if (!file().isFile()) turnedOn(u); // on before this vault ever used it: from now on
		int[] n = new int[1];
		String base = Unlocks.baseOf(bpId, n);
		if (base == null || !u.unlocked(base, n[0])) return false;
		String k = base + " " + n[0];
		return !read("seen").contains(k) && !read("claimed").contains(k);
	}

	// ---- rank (Immersive Mode) ----

	/** The ranks, lowest first. */
	public static final String[] RANKS = {"Commander", "Captain", "Commodore"};
	/** The layouts whose unlock earns a promotion: the Federation Cruiser A, then its Type C. */
	private static final String[] PROMOTIONS = {"PLAYER_SHIP_FED 0", "PLAYER_SHIP_FED 2"};
	/**
	 * The player's rank in this fleet: one step for each promotion layout unlocked in FTL since the rule's record began
	 * (in either order). Promotions are recorded once seen, so they stay whatever the profile does later.
	 */
	public static int rank(Unlocks u) {
		if (!Vault.isOpen()) return 0;
		Set<String> promoted = read("promoted");
		if (u != null && u.problem() == null) {
			if (!file().isFile()) turnedOn(u);
			Set<String> seen = read("seen"), add = new LinkedHashSet<String>();
			for (String k : PROMOTIONS) {
				String[] p = k.split(" ");
				if (!promoted.contains(k) && !seen.contains(k) && u.unlocked(p[0], Integer.parseInt(p[1]))) add.add(k);
			}
			try { append("promoted", add); promoted.addAll(add); } catch (Exception e) { log.warn("Could not record a promotion: {}", e.toString()); }
		}
		return Math.min(promoted.size(), RANKS.length - 1);
	}
	/** The rank's title. */
	public static String rankName(int rank) { return RANKS[Math.max(0, Math.min(rank, RANKS.length - 1))]; }

	/** Records that this layout's free ship was taken. */
	public static void claim(String bpId) throws java.io.IOException {
		int[] n = new int[1];
		String base = Unlocks.baseOf(bpId, n);
		if (base == null) return;
		Set<String> k = new LinkedHashSet<String>();
		k.add(base + " " + n[0]);
		append("claimed", k);
	}
}
