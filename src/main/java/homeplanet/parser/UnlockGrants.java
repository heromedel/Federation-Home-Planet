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
		for (String base : DataManager.get().getPlayerShipBaseIds(true)) {
			for (int n = 0; n < 3; n++) {
				String k = base + " " + n;
				if (u.unlocked(base, n) && !seen.contains(k)) add.add(k);
			}
		}
		try { append("seen", add); } catch (Exception e) { log.warn("Could not record the unlocked ships: {}", e.toString()); }
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
