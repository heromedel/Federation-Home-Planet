package hw2fhp;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.DefaultDataManager;
import net.blerf.ftl.parser.SavedGameParser;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;

import homeplanet.core.HistoryLog;
import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;
import homeplanet.core.Slipstream;
import homeplanet.parser.CompanionMod;
import homeplanet.parser.SaveHelper;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * The conversion itself: an FTL Homeworld saves folder (docked ships as continue_N.sav beside continue.sav,
 * Homeworld.sav and HomeworldAE.sav as storage, a Junkyard folder) and the designs, remodels and art beside the old
 * program go into Federation Home Planet's vault, with the blueprint names converted. Works whether or not Home
 * Planet has been started on the folder already. The old files end up in "Homeworld saves backup", untouched
 * apart from continue.sav, which is FTL's own file and is converted in place (a copy is kept).
 */
public final class Convert {
	private Convert() { }

	public static final String BACKUP = "Homeworld saves backup";

	/** Where the lines of the report go as the conversion runs. */
	public interface Log { void line(String s); }

	/** True if the saves folder (or the old program's folder) has anything in the old layout. */
	public static boolean anythingToConvert(File saves, File oldApp) {
		if (saves == null || !saves.isDirectory()) return false;
		if (oldShipFiles(saves).length > 0) return true;
		if (new File(saves, "Homeworld.sav").isFile() || new File(saves, "HomeworldAE.sav").isFile()) return true;
		if (new File(saves, "Junkyard").isDirectory()) return true;
		if (new File(saves, "homeworld-history.log").isFile()) return true;
		if (oldApp != null && (new File(oldApp, "homeworld-designs.xml").isFile() || new File(oldApp, "homeworld-remodels.xml").isFile()
				|| new File(oldApp, "homeworld-art").isDirectory())) return true;
		return false;
	}

	private static File[] oldShipFiles(File saves) {
		File[] fs = saves.listFiles();
		List<File> out = new ArrayList<File>();
		if (fs != null) for (File f : fs) if (f.isFile() && f.getName().matches("continue_\\d+\\.sav")) out.add(f);
		Collections.sort(out, new Comparator<File>() {
			public int compare(File a, File b) { return Integer.compare(number(a), number(b)); }
			int number(File f) { return Integer.parseInt(f.getName().replaceAll("\\D", "")); }
		});
		return out.toArray(new File[0]);
	}

	/**
	 * Runs the whole conversion.
	 * @param saves FTL's saves folder (the old Homeworld files are in it; the vault goes inside it)
	 * @param oldApp the old FTL Homeworld program folder (designs, remodels, art), or null
	 * @param game FTL's folder (ftl.dat), needed to read the ships
	 * @param slipstream Slipstream's folder, or null: the old companion mod is taken out of its mods folder and the new one written
	 */
	public static void run(File saves, File oldApp, File game, File slipstream, Log log) throws Exception {
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		File backup = new File(saves, BACKUP);
		backup.mkdirs();

		// 1. everything as it was, zipped, before a byte changes
		File zip = new File(backup, "before-conversion-" + stamp + ".zip");
		SafeFiles.zipFolder(saves, zip, new File(saves, Vault.FOLDER));
		log.line("Zipped the saves folder to " + BACKUP + "/" + zip.getName());
		if (oldApp != null) {
			File staging = new File(backup, "staging-" + stamp);
			staging.mkdirs();
			for (String n : new String[] {"homeworld-designs.xml", "homeworld-remodels.xml", Legacy.OLD_CFG, "Removed Blueprints.log", "homeworld-history.log"}) {
				File f = new File(oldApp, n);
				if (f.isFile()) SafeFiles.copy(f, new File(staging, n));
			}
			File art = new File(oldApp, "homeworld-art");
			if (art.isDirectory()) copyTree(art, new File(staging, "homeworld-art"));
			if (staging.list() != null && staging.list().length > 0) {
				File appZip = new File(backup, "old-program-files-" + stamp + ".zip");
				SafeFiles.zipFolder(staging, appZip, null);
				log.line("Zipped the old program's files to " + BACKUP + "/" + appZip.getName());
			}
			SafeFiles.deleteTree(staging);
		}

		// 2. the vault and the game data
		HomePlanet.savedGameParser = new SavedGameParser();
		HomePlanet.save_location = saves;
		HomePlanet.datsPath = game;
		if (slipstream != null) HomePlanet.config.setProperty(Slipstream.CFG_DIR, slipstream.getAbsolutePath());
		Vault v = Vault.open(saves);
		if (DataManager.get() == null) {
			DefaultDataManager dm = new DefaultDataManager(game);
			DataManager.setInstance(dm);
			dm.setDLCEnabledByDefault(true);
		}
		log.line("Vault: " + v.root.getPath());

		// 3. designs, remodels, art and the removed-blueprints log, from beside the old program
		if (oldApp != null) {
			File d = new File(oldApp, "homeworld-designs.xml");
			if (d.isFile()) {
				keepAside(v.designsFile(), stamp, log);
				SafeFiles.copy(d, v.designsFile());
				Legacy.convertText(v.designsFile());
				log.line("homeworld-designs.xml -> designs.xml");
			}
			File r = new File(oldApp, "homeworld-remodels.xml");
			if (r.isFile()) {
				keepAside(v.remodelsFile(), stamp, log);
				SafeFiles.copy(r, v.remodelsFile());
				Legacy.convertText(v.remodelsFile());
				log.line("homeworld-remodels.xml -> remodels.xml (blueprint names converted)");
			}
			File rl = new File(oldApp, "Removed Blueprints.log");
			if (rl.isFile()) {
				SafeFiles.copy(rl, v.removedBlueprintsLog());
				Legacy.convertText(v.removedBlueprintsLog());
				log.line("Removed Blueprints.log -> removed-blueprints.log");
			}
			File art = new File(oldApp, "homeworld-art");
			if (art.isDirectory()) {
				copyTree(art, v.artDir());
				log.line("homeworld-art/ -> art/");
			}
		}
		// their blueprints must be known before the ships are read, so the names come out right
		CompanionMod.register(CompanionMod.load());

		// 4. the ships: continue.sav stays FTL's (converted in place), the rest go into the vault
		int ships = 0, junked = 0;
		File cont = v.continueFile();
		if (cont.isFile()) {
			SafeFiles.copy(cont, new File(backup, "continue.sav"));
			boolean changed = Legacy.convertFile(cont);
			Ship s = v.boarded();
			if (s == null) s = v.adoptFile(cont, Ship.State.BOARDED, "continue.sav");
			else { s.invalidate(); s.save(); s.hash = SafeFiles.hash(cont); v.saveManifest(); }
			ships++;
			log.line("continue.sav: " + s.name + " stays boarded" + (changed ? " (blueprint names converted; the untouched copy is in " + BACKUP + ")" : ""));
		}
		for (File f : oldShipFiles(saves)) {
			SafeFiles.copy(f, new File(backup, f.getName()));
			boolean changed = Legacy.convertFile(f);
			Ship s = v.adoptFile(f, Ship.State.DOCKED, f.getName());
			ships++;
			log.line(f.getName() + " -> ships/" + s.id + ".sav: " + s.name + (changed ? " (blueprint names converted)" : ""));
		}
		File junk = new File(saves, "Junkyard");
		File[] jf = junk.listFiles();
		if (jf != null) {
			Arrays.sort(jf);
			File junkBackup = new File(backup, "Junkyard");
			for (File f : jf) {
				if (!f.isFile() || !f.getName().toLowerCase().endsWith(".sav")) continue;
				junkBackup.mkdirs();
				SafeFiles.copy(f, new File(junkBackup, f.getName()));
				boolean changed = Legacy.convertFile(f);
				String stem = f.getName().substring(0, f.getName().length() - 4);
				Ship s = v.adoptFile(f, Ship.State.JUNKED, stem);
				junked++;
				log.line("Junkyard/" + f.getName() + " -> junkyard/" + s.id + ".sav: " + s.name + (changed ? " (blueprint names converted)" : ""));
			}
			moveLeftovers(junk, new File(backup, "Junkyard"));
		}

		// 5. storage: the two old holds (AE and not) become one, kept as an AE save; anything already in the vault's hold stays
		File oldStd = new File(saves, "Homeworld.sav"), oldAe = new File(saves, "HomeworldAE.sav");
		if (oldStd.isFile() || oldAe.isFile()) {
			Ship st = v.storageEntry();
			File target = v.fileOf(st);
			List<File> sources = new ArrayList<File>();
			if (oldAe.isFile()) sources.add(oldAe);
			if (oldStd.isFile()) sources.add(oldStd);
			if (target.isFile()) sources.add(target); // a hold Home Planet already made (empty, unless things were traded into it)
			mergeStorage(sources, target);
			st.invalidate();
			for (File f : new File[] {oldStd, oldAe}) if (f.isFile()) SafeFiles.move(f, new File(backup, f.getName()));
			log.line("Homeworld.sav and HomeworldAE.sav -> storage.sav (one hold now)");
		}
		StringBuilder systems = new StringBuilder();
		for (String oldName : new String[] {"Homeworld", "HomeworldAE"}) {
			File oldSys = new File(saves, oldName + "-systems.txt");
			if (!oldSys.isFile()) continue;
			for (String line : new String(SafeFiles.read(oldSys), StandardCharsets.UTF_8).split("\\r?\\n")) {
				if (line.trim().isEmpty() || line.startsWith("#")) continue;
				systems.append(line.trim()).append("\n");
			}
			SafeFiles.move(oldSys, new File(backup, oldSys.getName()));
			log.line(oldSys.getName() + " -> storage-systems.txt");
		}
		if (systems.length() > 0) {
			File f = v.systemsFile();
			String head = f.isFile() ? new String(SafeFiles.read(f), StandardCharsets.UTF_8) : homeplanet.ui.SystemsPanel.HEADER + "\n";
			if (!head.endsWith("\n")) head += "\n";
			SafeFiles.writeText(f, head + systems, false);
		}

		// 6. the old log's entries go in front of the new log's
		for (File oldLog : new File[] {new File(saves, "homeworld-history.log"), oldApp == null ? null : new File(oldApp, "homeworld-history.log")}) {
			if (oldLog == null || !oldLog.isFile()) continue;
			byte[] old = SafeFiles.read(oldLog);
			File newLog = v.historyLog();
			byte[] cur = newLog.isFile() ? SafeFiles.read(newLog) : new byte[0];
			byte[] both = new byte[old.length + cur.length];
			System.arraycopy(old, 0, both, 0, old.length);
			System.arraycopy(cur, 0, both, old.length, cur.length);
			SafeFiles.write(newLog, both);
			SafeFiles.move(oldLog, new File(backup, oldLog.getName()));
			log.line(oldLog.getName() + " -> history.log (the old entries come first)");
		}

		// 7. the companion mod: the old one out of Slipstream's way, the new one written
		v.takeStock();
		CompanionMod.register(CompanionMod.load());
		if (slipstream != null) {
			File mods = Slipstream.modsDir(slipstream);
			File[] fs = mods.listFiles();
			if (fs != null) {
				for (File f : fs) {
					if (!f.isFile() || !f.getName().toLowerCase().endsWith(".ftl")) continue;
					String[] meta = Slipstream.metadataOf(f);
					boolean old = false;
					for (String a : Legacy.OLD_MOD_AUTHORS) if (a.equals(meta[1])) old = true;
					for (String t : Legacy.OLD_MOD_TITLES) if (meta[0] != null && meta[0].startsWith(t)) old = true;
					if (old) { SafeFiles.move(f, new File(backup, f.getName())); log.line("Old companion mod " + f.getName() + " moved out of Slipstream's mods folder"); }
				}
			}
		}
		File mod = Slipstream.writeMod();
		if (mod != null) log.line("New companion mod written: " + mod.getPath());
		else log.line("The new companion mod could not be written; Home Planet writes it again when a blueprint changes.");

		// 8. the old program's data files are in the vault now: they go into a backup folder beside the old program
		if (oldApp != null) {
			File appBackup = new File(oldApp, "Homeworld backup");
			for (String n : new String[] {"homeworld-designs.xml", "homeworld-remodels.xml", "Removed Blueprints.log", "homeworld-history.log"}) {
				File f = new File(oldApp, n);
				if (f.isFile()) { appBackup.mkdirs(); SafeFiles.move(f, new File(appBackup, n)); }
			}
			File art = new File(oldApp, "homeworld-art");
			if (art.isDirectory()) { copyTree(art, new File(appBackup, "homeworld-art")); SafeFiles.deleteTree(art); } // a folder: copied, then removed
			if (appBackup.isDirectory()) log.line("The old program's data files are in " + appBackup.getPath() + " (they were zipped as well).");
		}

		String summary = ships + (ships == 1 ? " ship" : " ships") + ", " + junked + " in the junkyard, "
				+ (oldStd.isFile() || oldAe.isFile() || new File(backup, "HomeworldAE.sav").isFile() || new File(backup, "Homeworld.sav").isFile() ? "1 storage hold" : "no storage hold");
		HistoryLog.entry("CONVERTED", "the FTL Homeworld files are now in the vault (" + summary + "); the old files are in " + BACKUP);
		log.line("Done: " + summary + ". The old files are in " + backup.getPath() + ".");
		if (!v.blueprintsInUse().isEmpty()) log.line("Ships using custom blueprints can't fly until the new companion mod is patched in: Settings > Patch mods in Home Planet.");
	}

	/** A file Home Planet already has under that name is kept beside it, dated, rather than lost. */
	private static void keepAside(File f, String stamp, Log log) throws IOException {
		if (!f.isFile()) return;
		File kept = new File(f.getParentFile(), f.getName().replaceFirst("\\.xml$", "") + ".before-conversion-" + stamp + ".xml");
		SafeFiles.move(f, kept);
		log.line(f.getName() + " already existed in the vault: kept as " + kept.getName());
	}
	private static void moveLeftovers(File dir, File to) throws IOException {
		File[] fs = dir.listFiles();
		if (fs == null) return;
		for (File f : fs) { to.mkdirs(); SafeFiles.move(f, new File(to, f.getName())); }
		dir.delete();
	}
	private static void copyTree(File from, File to) throws IOException {
		File[] fs = from.listFiles();
		if (fs == null) return;
		to.mkdirs();
		for (File f : fs) {
			File t = new File(to, f.getName());
			if (f.isDirectory()) copyTree(f, t);
			else SafeFiles.copy(f, t);
		}
	}

	/**
	 * Joins storage holds into one AE-flagged hold at {@code target}: the first source is the base (an AE hold, or a
	 * fresh one), and everything in the others (supplies, weapons, drones, augments, cargo, crew) is added to it.
	 */
	static void mergeStorage(List<File> sources, File target) throws IOException {
		SavedGameParser parser = new SavedGameParser();
		SavedGameState into = null;
		List<SavedGameState> rest = new ArrayList<SavedGameState>();
		for (File f : sources) {
			SavedGameState s = parser.readSavedGame(f);
			if (into == null && s.isDLCEnabled()) into = s; else rest.add(s);
		}
		if (into == null) into = SaveHelper.createStorageSave("Spacedock Storage", true);
		into.setDLCEnabled(true);
		into.setPlayerShipName("Spacedock Storage");
		into.getPlayerShip().setShipName("Spacedock Storage");
		ShipState t = into.getPlayerShip();
		for (SavedGameState from : rest) {
			ShipState f = from.getPlayerShip();
			t.setScrapAmt(t.getScrapAmt() + f.getScrapAmt());
			t.setFuelAmt(t.getFuelAmt() + f.getFuelAmt());
			t.setMissilesAmt(t.getMissilesAmt() + f.getMissilesAmt());
			t.setDronePartsAmt(t.getDronePartsAmt() + f.getDronePartsAmt());
			for (WeaponState w : f.getWeaponList()) t.getWeaponList().add(SaveHelper.newIdleWeapon(w.getWeaponId()));
			for (DroneState d : f.getDroneList()) t.getDroneList().add(SaveHelper.copyDroneForTransfer(d));
			t.getAugmentIdList().addAll(f.getAugmentIdList());
			into.getCargoIdList().addAll(from.getCargoIdList());
			for (CrewState c : SaveHelper.getOwnCrew(f)) {
				if (SaveHelper.hasBody(c) && SaveHelper.placeCrew(t, c, true)) t.getCrewList().add(c);
			}
		}
		SaveHelper.writeSavedGame(target, into);
	}
}
