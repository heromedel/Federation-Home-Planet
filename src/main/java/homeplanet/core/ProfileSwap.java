package homeplanet.core;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * FTL's profile (ae_prof.sav, or prof.sav before the Advanced Edition: unlocks, achievements, scores) set aside in a
 * fleet's folder and brought back, so Immersive Mode can have one of its own. FTL must be closed. Nothing is deleted:
 * a profile is only ever moved between FTL's saves folder and a fleet's ftl-profile folder.
 */
public final class ProfileSwap {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProfileSwap.class);
	private ProfileSwap() { }

	public static final String[] NAMES = {"ae_prof.sav", "prof.sav"};
	private static final String STASH = "ftl-profile";

	/** The profile FTL uses in this saves folder (the Advanced Edition one first), or null if there's none yet. */
	public static File current(File saves) {
		for (String n : NAMES) { File f = new File(saves, n); if (f.isFile()) return f; }
		return null;
	}
	/** Moves FTL's profile files out of the saves folder into this fleet's folder (replacing what it had kept). */
	public static void setAside(File saves, File fleetRoot) throws IOException {
		log.debug("FTL profile set aside into {}", fleetRoot);
		File dir = new File(fleetRoot, STASH);
		for (String n : NAMES) {
			File f = new File(saves, n);
			if (f.isFile()) SafeFiles.move(f, new File(dir, n));
		}
	}
	/** Moves the profile this fleet kept back into the saves folder, if it kept one (else FTL starts a fresh one). */
	public static void bringBack(File saves, File fleetRoot) throws IOException {
		log.debug("FTL profile brought back from {}", fleetRoot);
		File dir = new File(fleetRoot, STASH);
		List<File> moved = new ArrayList<File>();
		try {
			for (String n : NAMES) {
				File f = new File(dir, n), to = new File(saves, n);
				if (!f.isFile()) continue;
				if (to.exists()) throw new IOException("FTL's saves folder already has " + n + ": it would be written over");
				SafeFiles.move(f, to);
				moved.add(to);
			}
		} catch (IOException e) {
			for (File f : moved) { try { SafeFiles.move(f, new File(dir, f.getName())); } catch (IOException again) { log.warn("FTL profile {} could not be put back after a failed swap: {}", f, again.toString()); } }
			throw e;
		}
	}
	/** Swaps profiles between two fleets: this one's goes aside into {@code from}, and {@code to}'s comes back. All or nothing. */
	public static void swap(File saves, File from, File to) throws IOException {
		log.debug("FTL profile swap: {} out, {} in", from, to);
		boolean kept = false;
		for (String n : NAMES) if (new File(new File(to, STASH), n).isFile()) kept = true;
		setAside(saves, from);
		try {
			bringBack(saves, to);
		} catch (IOException e) {
			try { bringBack(saves, from); } catch (IOException again) { e.addSuppressed(again); }
			throw e;
		}
		log.info("FTL profile set aside in {}/{}{}", from.getName(), STASH, kept ? "; " + to.getName() + "'s brought back" : "; FTL will start a fresh one"); // the debug log's (heromedel, 5.53)
	}
	/** Copies FTL's profile to a dated backup in this folder's profile-backups. Returns the copy. */
	public static File backup(File saves, File folder) throws IOException {
		File f = current(saves);
		if (f == null) throw new IOException("There's no FTL profile (ae_prof.sav or prof.sav) in " + saves);
		String stamp = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date());
		File to = new File(new File(folder, "profile-backups"), f.getName().replace(".sav", "") + " " + stamp + ".sav");
		SafeFiles.copy(f, to);
		log.info("FTL profile backed up: {}", to);
		return to;
	}
}
