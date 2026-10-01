package homeplanet.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.DefaultDataManager;
import net.blerf.ftl.parser.SavedGameParser;
import net.vhati.modmanager.core.FTLUtilities;

import homeplanet.parser.CompanionMod;
import homeplanet.parser.Retrofit;
import homeplanet.ui.MainFrame;
import homeplanet.vault.Vault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Federation Home Planet: a space station for FTL: Faster Than Light, where a captain keeps a fleet of ships and
 * boards whichever one the next journey calls for. This class is the program's entry point: it reads the config,
 * finds FTL's data and saves, opens the vault, loads the game data, and shows the station.
 *
 * Federation Home Planet grew out of FTL Homeworld by ManApart (iceburg333); see CREDITS.md.
 */
public class HomePlanet {
	private static final Logger log = LoggerFactory.getLogger(HomePlanet.class);

	public static final String APP_NAME = "Federation Home Planet";
	public static final String APP_VERSION = "4B.60";
	public static String version() { return APP_VERSION; }

	/** FTL's saves folder (continue.sav lives here; the vault is a folder inside it). */
	public static File save_location = null;
	/** FTL's data folder (ftl.dat). */
	public static File datsPath = null;
	public static SavedGameParser savedGameParser;

	// ---- settings (all kept in the cfg) ----
	public static boolean launchThroughSteam = false;
	/** Ships can only trade in the Cargo Bay while docked at a station (a beacon with a store). */
	public static boolean storeRequirement = false;
	/** A New Journey can only begin while the boarded ship is docked at a station. */
	public static boolean journeyStoreRequirement = false;
	/** Scrapping a ship may strip her (optional) systems into the Cargo Bay, at a fee each (see Economy.stripFee). */
	public static boolean stripAllowed = false;
	/** Sandbox Mode: what taking a system off at Refit costs (Economy.NOT_ALLOWED, 0, 25 or 50); Immersive Mode's is the career's. */
	public static int removalFee = 0;
	/** Sandbox Mode: what a New Journey costs (0, 200, 500 or 1000); Immersive Mode's is the career's. */
	public static int journeyFee = 0;
	/** House rule: missiles and drone parts can be sold at half the store price (FTL's own stores don't buy them). */
	public static boolean sellSupplies = false;
	/** Commission: only ship layouts the FTL profile has unlocked; and (nested) custom ships only if their base layout is unlocked. */
	public static boolean commissionUnlockedOnly = false, commissionCustomUnlockedOnly = false;
	/** HR1: stored systems can be sold, for half their price and half the upgrades paid for. */
	public static boolean sellSystems = false;
	/** HR2: commissioning a ship costs scrap from the storage hold, at this percent of her price (50, 75 or 100). */
	public static boolean commissionCosts = false;
	public static int commissionPercent = 100;
	/** With HR2: the free ship an empty shipyard (no ship docked, boarded or in the Junkyard) offers: "kestrel", "any" or "relief". */
	public static String freeShip = "relief";
	/** With HR2: each ship layout unlocked in the FTL profile after this was turned on can be commissioned free, once. */
	public static boolean unlockFreeShips = false;
	/**
	 * Immersive Mode: sets and locks the rules (see {@link #applyImmersive}); its fees and sale prices are in
	 * {@link Economy}; restoring and recovering are off.
	 */
	public static boolean immersiveMode = false;
	/** Transmissions from The Federation Home Planet (the inbox on the Space Dock). Immersive Mode turns it on. */
	public static boolean immersiveNotifications = false;
	/** Sandbox Mode's Career messages (with Immersive Notifications): the welcome, promotions, achievement rewards, the stipend. */
	public static boolean careerMessages = false;
	/** Is a career running in the fleet in use: always in Immersive Mode, and in Sandbox Mode with Career messages on. */
	public static boolean career() { return immersiveMode || (immersiveNotifications && careerMessages); }
	/** The normal fleet's choice after a final victory: nothing, rescue or reward (see parser.FinalVictory; the Immersive fleet's is in its career). */
	public static String finalVictory = "nothing";
	/** The rules Immersive Mode sets, as the player had them: kept apart, written to the cfg, and back when it's turned off. */
	public static final class Rules {
		public boolean store, journey, sellSupplies, sellSystems, costs, unlockFree, lockedOnly, customLockedOnly, notifications;
		public int percent;
		static Rules current() {
			Rules r = new Rules();
			r.store = HomePlanet.storeRequirement; r.journey = HomePlanet.journeyStoreRequirement; r.sellSupplies = HomePlanet.sellSupplies;
			r.sellSystems = HomePlanet.sellSystems; r.costs = HomePlanet.commissionCosts; r.percent = HomePlanet.commissionPercent;
			r.unlockFree = HomePlanet.unlockFreeShips;
			r.lockedOnly = HomePlanet.commissionUnlockedOnly; r.customLockedOnly = HomePlanet.commissionCustomUnlockedOnly;
			r.notifications = HomePlanet.immersiveNotifications;
			return r;
		}
		void set() {
			HomePlanet.storeRequirement = store; HomePlanet.journeyStoreRequirement = journey; HomePlanet.sellSupplies = sellSupplies;
			HomePlanet.sellSystems = sellSystems; HomePlanet.commissionCosts = costs; HomePlanet.commissionPercent = percent;
			HomePlanet.unlockFreeShips = unlockFree;
			HomePlanet.commissionUnlockedOnly = lockedOnly; HomePlanet.commissionCustomUnlockedOnly = customLockedOnly;
			HomePlanet.immersiveNotifications = notifications;
		}
	}
	private static Rules normalRules = null;
	/** The player's own rules (what the rules in effect would be without Immersive Mode). */
	public static Rules normalRules() { return normalRules != null ? normalRules : Rules.current(); }
	/** Immersive Mode is off again: the player's own rules come back. */
	public static void leaveImmersive() {
		immersiveMode = false;
		if (normalRules != null) { normalRules.set(); normalRules = null; }
	}
	/** Immersive Mode's rules, set over the player's own (which are kept, see {@link #normalRules}). */
	public static void applyImmersive() {
		if (!immersiveMode) return;
		if (normalRules == null) normalRules = Rules.current();
		unlockFreeShips = true;
		immersiveNotifications = true;
		commissionUnlockedOnly = true;
		commissionCustomUnlockedOnly = true;
		storeRequirement = true;
		journeyStoreRequirement = true;
		commissionCosts = true;
		commissionPercent = 100;
		sellSupplies = true;
		sellSystems = true;
	}
	public static boolean debugLogging = false;

	/** The config file, beside the program (whatever folder it was started from), and its values (the Settings window changes and saves them). */
	public static final File propFile = new File(appDir(), "federation-home-planet.cfg");
	public static final Properties config = new Properties();

	/**
	 * True once the companion mod, as last built, was patched in this session. The game data was read before that
	 * patch, so the in-game check would still think the mod is missing until a restart.
	 */
	public static boolean modPatchedThisSession = false;

	public static void main(String[] args) {
		homeplanet.ui.MenuTheme.install(); // pop-up windows in the station's dark blue
		ImageIO.setUseCache(false); // small images don't need disk buffering
		savedGameParser = new SavedGameParser();
		boolean writeConfig = loadConfig();
		startFileLog();
		setDebugLogging(Boolean.parseBoolean(config.getProperty("debug_logging", "false")) || Boolean.getBoolean("homeplanet.debug"));
		log.info("{} {} on Java {} ({} {}), in {}", APP_NAME, APP_VERSION, System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.arch"), appDir());
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, Throwable e) { log.error("Uncaught in " + t.getName(), e); }
		});
		launchThroughSteam = flag("launch_through_steam");
		storeRequirement = flag("store_requirement");
		journeyStoreRequirement = flag("new_journey_store_requirement");
		stripAllowed = flag("strip_when_scrapping");
		removalFee = Economy.removalFee(config.getProperty("refit_removal_fee"));
		journeyFee = Economy.journeyFee(config.getProperty("new_journey_fee"));
		sellSupplies = flag("sell_supplies");
		commissionUnlockedOnly = flag("commission_unlocked_only");
		commissionCustomUnlockedOnly = flag("commission_custom_unlocked_only");
		sellSystems = flag("sell_systems");
		commissionCosts = flag("commission_costs_scrap");
		commissionPercent = percent(config.getProperty("commission_price_percent"));
		freeShip = config.getProperty("free_ship", "relief"); // the relief ship unless chosen otherwise
		if (!"any".equals(freeShip) && !"kestrel".equals(freeShip) && !"variable".equals(freeShip)) freeShip = "relief";
		unlockFreeShips = flag("unlock_free_ships");
		immersiveMode = flag("immersive_mode");
		Vault.immersiveSlot = Vault.slotOf(config.getProperty("immersive_slot")); // which Immersive career (a fleet from before difficulties is Custom's)
		immersiveNotifications = flag("immersive_notifications");
		careerMessages = flag("career_messages");
		finalVictory = config.getProperty("final_victory", "nothing");
		applyImmersive();
		Music.enabled = Boolean.parseBoolean(config.getProperty("title_music", "true"));
		log.debug("{} {} starting on Java {}", APP_NAME, APP_VERSION, System.getProperty("java.version"));

		// FTL's data
		String datsPathString = config.getProperty("ftlDatsPath");
		if (datsPathString != null && datsPathString.length() > 0) {
			datsPath = new File(datsPathString);
			if (!isDatsPathValid(datsPath)) datsPath = null;
		}
		if (datsPath == null) {
			datsPath = FTLUtilities.findDatsDir(); // the usual Steam, GOG and Humble folders
			if (datsPath != null && !confirm("The Home Planet Station found FTL's files in:\n" + datsPath.getPath() + "\nIs this correct?", "Confirm")) datsPath = null;
			if (datsPath == null) datsPath = promptForFtlPath();
			if (datsPath != null) { config.setProperty("ftlDatsPath", datsPath.getAbsolutePath()); writeConfig = true; }
		}
		if (datsPath == null) {
			showErrorDialog("FTL's files were not found. The Home Planet Station can't open without them.\nIt will now close.");
			System.exit(1);
		}
		// First setup, asked once: Steam launching (for the Steam version), then the House Rules window while any rule was never set.
		// A rule missing from the config starts ticked, except the selling and pricing house rules; rules already set keep their value.
		if (config.getProperty("launch_through_steam") == null && datsPath.getAbsolutePath().toLowerCase().contains("steamapps")) {
			launchThroughSteam = confirm("This looks like the Steam version of FTL.\nLaunch FTL through Steam?", "Launch through Steam");
			onEdt(new java.util.concurrent.Callable<Void>() { public Void call() {
				JOptionPane.showMessageDialog(null, "One more thing for the Steam version: turn off Steam Cloud for FTL.\n\n"
						+ "In your Steam library, right-click FTL, then Properties, General, and untick keeping saves in the Steam Cloud.\n\n"
						+ "With it on, Steam can bring back a ship you docked as a second copy, or restore an old FTL profile.", "Steam Cloud", JOptionPane.WARNING_MESSAGE);
				return null;
			} });
			config.setProperty("launch_through_steam", Boolean.toString(launchThroughSteam));
			writeConfig = true;
		}
		boolean rulesMissing = false, chooseMode = false;
		int missing = 0;
		for (String key : RULE_KEYS) if (config.getProperty(key) == null) { rulesMissing = true; missing++; }
		if (rulesMissing) {
			storeRequirement = flag("store_requirement", true);
			journeyStoreRequirement = flag("new_journey_store_requirement", true);
			stripAllowed = flag("strip_when_scrapping", true);
			sellSupplies = flag("sell_supplies", false);
			commissionUnlockedOnly = flag("commission_unlocked_only", true);
			commissionCustomUnlockedOnly = flag("commission_custom_unlocked_only", true);
			applyImmersive();
			// a first startup chooses its mode (once the folders and the fleet exist, so Immersive Mode can be entered, below);
			// an older station missing only a newer rule just sees the rules again
			if (missing == RULE_KEYS.length) chooseMode = true;
			else onEdt(new java.util.concurrent.Callable<Void>() { public Void call() { homeplanet.ui.HouseRulesDialog.ask(); return null; } });
			writeConfig = true; // saveConfig writes every rule, so this is asked once
		}

		// FTL's saves
		String savePathString = config.getProperty("ftlSavePath");
		if (savePathString != null) {
			save_location = new File(savePathString);
			if (!save_location.isDirectory()) save_location = null;
		}
		if (save_location == null) {
			// FTL 1.5.4+ keeps its profile in ae_prof.sav; older versions used prof.sav
			for (String known : new String[] {"ae_prof.sav", "prof.sav", "continue.sav"}) {
				for (File file : getPossibleUserDataLocations(known)) if (file.exists()) { save_location = file.getParentFile(); break; }
				if (save_location != null) break;
			}
			if (save_location != null && !confirm("The Home Planet Station found FTL's saves in:\n" + save_location.getPath() + "\nIs this correct?", "Confirm")) save_location = null;
			if (save_location == null) save_location = promptForSavePath();
			if (save_location != null) { config.setProperty("ftlSavePath", save_location.getAbsolutePath()); writeConfig = true; }
		}
		if (save_location == null) {
			showErrorDialog("The Home Planet Station was unable to find FTL's saves folder. The Inter-Station Services cannot function without it.\nIt will now close.");
			System.exit(1);
		}
		if (writeConfig) saveConfig();

		// The vault (files only so far; the ships are read once the game data is in)
		try {
			Vault.open(save_location, immersiveMode); // Immersive Mode has a fleet of its own
		} catch (IOException e) {
			log.error("Could not open the vault in " + save_location, e);
			showErrorDialog("The Home Planet Station could not open its fleet records in:\n" + save_location + "\n\n" + e);
			System.exit(1);
		}
		// Slipstream, offered once before anything needs it (after the vault opens, so its log entry goes there).
		// On the event thread: a file chooser opened from this thread comes up empty on Windows, and every later one with it.
		final boolean[] offered = {false};
		try {
			javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
				public void run() { offered[0] = Slipstream.offerAtStart(); }
			});
		} catch (Exception e) {
			log.warn("Could not offer Slipstream", e);
		}
		if (offered[0]) saveConfig();

		// The game data, plus our own blueprints (retrofit copies, remodels, designs) so their ships can be read
		try {
			DefaultDataManager dataManager = new DefaultDataManager(datsPath);
			DataManager.setInstance(dataManager);
			dataManager.setDLCEnabledByDefault(true);
			CompanionMod.register(CompanionMod.load());
		} catch (Exception e) {
			log.error("Error parsing FTL resources in " + datsPath, e);
			showErrorDialog("The Home Planet Station could not read FTL's files in:\n" + datsPath + "\n\n" + e);
			System.exit(1);
		}

		try {
			Vault.get().takeStock();
			Vault.get().storage();
		} catch (IOException e) {
			log.error("Could not take stock of the vault", e);
			showErrorDialog("The Home Planet Station could not take stock of the fleet:\n" + e);
		}
		// First setup: Sandbox Mode or Immersive Mode, then the rules the chosen mode leaves to the player
		if (chooseMode) {
			onEdt(new java.util.concurrent.Callable<Void>() { public Void call() { homeplanet.ui.ModeChoiceDialog.ask(); return null; } });
			saveConfig();
		}

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					// a click outside an open dropdown list only closes it; it doesn't also press whatever is underneath
					javax.swing.UIManager.put("PopupMenu.consumeEventOnClose", Boolean.TRUE);
					MainFrame frame = new MainFrame(APP_NAME, APP_VERSION);
					frame.setVisible(true);
					Music.refresh();
					SaveWatcher.start(); // FTL's writes to continue.sav, for final victories
				} catch (Exception e) {
					log.error("Exception while creating the main window.", e);
					showErrorDialog("Communication with The Home Planet Station could not be opened:\n" + e);
					System.exit(1);
				}
			}
		});
	}

	// ---- config ----

	private static boolean flag(String key) { return flag(key, false); }
	/** A price multiplier from the cfg: 50, 75 or 100 (anything else is 100). */
	private static int percent(String v) {
		try { int p = Integer.parseInt(v == null ? "" : v.trim()); if (p == 50 || p == 75) return p; } catch (NumberFormatException e) { }
		return 100;
	}
	private static boolean flag(String key, boolean dflt) { return Boolean.parseBoolean(config.getProperty(key, Boolean.toString(dflt))); }

	/** The rules the first-run House Rules window sets. */
	private static final String[] RULE_KEYS = {"store_requirement", "new_journey_store_requirement", "strip_when_scrapping", "sell_supplies",
			"commission_unlocked_only", "commission_custom_unlocked_only"};

	/** Reads the config; imports FTL Homeworld's old one when there's none yet. Returns true if it should be written. */
	private static boolean loadConfig() {
		File from = propFile;
		if (!from.isFile()) {
			// before 4B.04 the config was looked for in the folder the program was started from: carry it over
			File old = new File("federation-home-planet.cfg").getAbsoluteFile();
			if (!old.isFile() || old.equals(propFile.getAbsoluteFile())) return true;
			from = old;
		}
		InputStream in = null;
		try {
			in = new FileInputStream(from);
			config.load(in);
		} catch (IOException e) {
			showErrorDialog("The Home Planet Station could not read its settings from " + from.getPath());
			log.error("Could not read " + from, e);
		} finally {
			try { if (in != null) in.close(); } catch (IOException e) { }
		}
		// before 4B.58 scrapping either kept the systems or didn't: now stripping is allowed or not
		String old = config.getProperty("scrap_keeps_systems");
		if (old != null && config.getProperty("strip_when_scrapping") == null) config.setProperty("strip_when_scrapping", old);
		config.remove("scrap_keeps_systems");
		return from != propFile; // an old config: written to its new place
	}

	/** Writes the current settings to the cfg (a temporary file, then one move). Returns false, after telling the user, on failure. */
	public static boolean saveConfig() {
		if (save_location != null) config.setProperty("ftlSavePath", save_location.getAbsolutePath());
		if (datsPath != null) config.setProperty("ftlDatsPath", datsPath.getAbsolutePath());
		config.setProperty("launch_through_steam", Boolean.toString(launchThroughSteam));
		config.setProperty("debug_logging", Boolean.toString(debugLogging));
		Rules own = normalRules(); // Immersive Mode's rules are never written over the player's own
		config.setProperty("store_requirement", Boolean.toString(own.store));
		config.setProperty("new_journey_store_requirement", Boolean.toString(own.journey));
		config.setProperty("strip_when_scrapping", Boolean.toString(stripAllowed));
		config.setProperty("refit_removal_fee", Integer.toString(removalFee));
		config.setProperty("new_journey_fee", Integer.toString(journeyFee));
		config.setProperty("career_messages", Boolean.toString(careerMessages));
		config.setProperty("sell_supplies", Boolean.toString(own.sellSupplies));
		config.setProperty("commission_unlocked_only", Boolean.toString(own.lockedOnly));
		config.setProperty("commission_custom_unlocked_only", Boolean.toString(own.customLockedOnly));
		config.setProperty("sell_systems", Boolean.toString(own.sellSystems));
		config.setProperty("commission_costs_scrap", Boolean.toString(own.costs));
		config.setProperty("commission_price_percent", Integer.toString(own.percent));
		config.setProperty("free_ship", freeShip);
		config.setProperty("unlock_free_ships", Boolean.toString(own.unlockFree));
		config.setProperty("immersive_mode", Boolean.toString(immersiveMode));
		config.setProperty("immersive_slot", Vault.immersiveSlot);
		config.setProperty("final_victory", finalVictory);
		config.setProperty("immersive_notifications", Boolean.toString(own.notifications));
		config.setProperty("title_music", Boolean.toString(Music.enabled));
		try {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			config.store(buf, APP_NAME + " - config file");
			SafeFiles.write(propFile, buf.toByteArray(), false);
			return true;
		} catch (IOException e) {
			log.error("Error saving config to " + propFile.getPath(), e);
			showErrorDialog("The Home Planet Station could not save its settings to " + propFile.getPath());
			return false;
		}
	}

	// ---- dialogs ----

	/**
	 * Runs a dialog on the event thread and waits for its answer (directly, when already on it). Startup runs on the
	 * main thread, and Swing windows opened from there misbehave: a Windows file chooser comes up empty, and every
	 * later one with it.
	 */
	@SuppressWarnings("unchecked")
	static <T> T onEdt(final java.util.concurrent.Callable<T> c) {
		try {
			if (javax.swing.SwingUtilities.isEventDispatchThread()) return c.call();
			final Object[] out = {null};
			final Exception[] err = {null};
			javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
				public void run() { try { out[0] = c.call(); } catch (Exception e) { err[0] = e; } }
			});
			if (err[0] != null) throw err[0];
			return (T) out[0];
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static boolean confirm(final String message, final String title) {
		return onEdt(new java.util.concurrent.Callable<Boolean>() { public Boolean call() {
			return JOptionPane.showConfirmDialog(null, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
		}});
	}
	/** Yes or No, starting on No: for anything that can't be undone (selling, junking, retiring). Closing the window means No. */
	public static boolean confirmNo(java.awt.Component owner, String message, String title) {
		Object[] options = {"Yes", "No"};
		return JOptionPane.showOptionDialog(owner, message, title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]) == 0;
	}
	public static void showErrorDialog(final String message) {
		onEdt(new java.util.concurrent.Callable<Void>() { public Void call() {
			JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
			return null;
		}});
	}

	// ---- FTL itself ----

	public static void launchFTL() {
		// a retrofitted ship can't load without the companion mod: don't let FTL try
		File cont = new File(save_location, "continue.sav");
		if (cont.exists() && !modPatchedThisSession) {
			List<String> missing = Retrofit.missingBlueprints(cont);
			if (!missing.isEmpty()) {
				showErrorDialog("The boarded ship flies on blueprints from the " + Retrofit.MOD_NAME + ", which isn't in FTL yet ("
						+ String.join(", ", missing) + ").\n\nSend it to FTL via Slipstream first (Settings > Patch mods), or board a different ship.");
				return;
			}
		}
		String empty = noOneAboard(cont);
		if (empty != null) { showErrorDialog(empty); return; }
		Music.stop(); // FTL has its own music
		if (launchThroughSteam) {
			String steamUri = "steam://rungameid/" + FTLUtilities.STEAM_APPID_FTL;
			log.debug("Running FTL through Steam: {}", steamUri);
			try {
				if (System.getProperty("os.name").startsWith("Windows")) new ProcessBuilder("cmd", "/c", "start", "", steamUri).start();
				else java.awt.Desktop.getDesktop().browse(new java.net.URI(steamUri));
			} catch (Exception ex) {
				log.error("Could not launch FTL through Steam.", ex);
				showErrorDialog("The Home Planet Station could not launch FTL through Steam:\n" + ex);
			}
			return;
		}
		// FTL 1.6+ keeps FTLGame.exe beside ftl.dat; older versions had it one folder up from resources/
		File ftl = FTLUtilities.findGameExe(datsPath);
		if (ftl == null) {
			log.warn("Could not find the FTL executable near {}", datsPath);
			showErrorDialog("The Home Planet Station could not find FTL's executable near:\n" + datsPath + "\n\nCheck the game folder in Settings.");
			return;
		}
		log.debug("Running FTL: {}", ftl.getAbsolutePath());
		try {
			ProcessBuilder builder = new ProcessBuilder(ftl.getAbsolutePath());
			builder.directory(ftl.getParentFile()); // the exe expects its own folder as the working directory
			builder.start();
		} catch (IOException ex) {
			log.error("An exception occurred while executing FTL.", ex);
			showErrorDialog("FTL could not be started:\n" + ex);
		}
	}

	/**
	 * FTL ends a game the moment no crew is alive, so a boarded ship with no one aboard (the Collective's derelict)
	 * mustn't fly. Why not, or null if she may (or there's no boarded ship to check).
	 */
	public static String noOneAboard(File cont) {
		if (cont == null || !cont.isFile()) return null;
		try {
			net.blerf.ftl.parser.SavedGameParser.SavedGameState g = savedGameParser.readSavedGame(cont);
			if (!homeplanet.parser.SaveHelper.getOwnCrew(g.getPlayerShip()).isEmpty()) return null;
			return g.getPlayerShipName() + " has no one aboard, and FTL would end her journey the moment she launched.\n\n"
					+ "Open the Cargo Bay and move at least one crew member to her (from the Cargo Hold or another ship), then launch FTL.";
		} catch (Exception e) {
			return null; // unreadable: FTL will say so itself
		}
	}

	// ---- finding FTL ----

	private static boolean isDatsPathValid(File path) {
		// FTL 1.6+: ftl.dat. Older versions: data.dat and resource.dat.
		return path.exists() && path.isDirectory() && FTLUtilities.isDatsDirValid(path);
	}
	public static File promptForFtlPath() {
		return onEdt(new java.util.concurrent.Callable<File>() { public File call() { return promptForFtlPathHere(); } });
	}
	private static File promptForFtlPathHere() {
		JOptionPane.showMessageDialog(null, "The Home Planet Station's interface draws its images and data from FTL,\nbut its search could not find FTL's files on its own.\n\n"
				+ "Select 'ftl.dat' in your FTL folder (FTL 1.6 and newer),\nor '(FTL dir)/resources/data.dat' for older versions,\nor 'FTL.app' on a Mac.",
				"FTL Not Found", JOptionPane.INFORMATION_MESSAGE);
		final JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Find ftl.dat, data.dat or FTL.app");
		fc.setFileHidingEnabled(false);
		fc.addChoosableFileFilter(new FileFilter() {
			@Override public String getDescription() { return "FTL Resources (ftl.dat; data.dat; FTL.app)"; }
			@Override public boolean accept(File f) {
				return f.isDirectory() || f.getName().equals("ftl.dat") || f.getName().equals("data.dat") || f.getName().equals("FTL.app");
			}
		});
		fc.setMultiSelectionEnabled(false);
		File ftlPath = null;
		if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			File f = fc.getSelectedFile();
			if (f.getName().equals("ftl.dat") || f.getName().equals("data.dat")) ftlPath = f.getParentFile();
			else if (f.getName().endsWith(".app") && f.isDirectory()) {
				File contentsPath = new File(f, "Contents");
				if (new File(contentsPath, "Resources").exists()) ftlPath = new File(contentsPath, "Resources");
			}
		}
		return ftlPath != null && isDatsPathValid(ftlPath) ? ftlPath : null;
	}
	public static File promptForSavePath() {
		return onEdt(new java.util.concurrent.Callable<File>() { public File call() { return promptForSavePathHere(); } });
	}
	private static File promptForSavePathHere() {
		JOptionPane.showMessageDialog(null, "The Home Planet Station sends ships out using FTL's saves,\nbut its search could not find FTL's saves folder on its own.\n\n"
				+ "Select '/Documents/My Games/FasterThanLight/continue.sav' (or ae_prof.sav).", "FTL Save Not Found", JOptionPane.INFORMATION_MESSAGE);
		final JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Find continue.sav or ae_prof.sav");
		fc.addChoosableFileFilter(new FileFilter() {
			@Override public String getDescription() { return "FTL save files (continue.sav, ae_prof.sav, prof.sav)"; }
			@Override public boolean accept(File f) {
				return f.isDirectory() || f.getName().equals("continue.sav") || f.getName().equals("ae_prof.sav") || f.getName().equals("prof.sav");
			}
		});
		fc.setMultiSelectionEnabled(false);
		File path = null;
		if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) path = fc.getSelectedFile().getParentFile();
		return path != null && path.isDirectory() ? path : null;
	}
	public static File[] getPossibleUserDataLocations(String fileName) {
		if (fileName == null) fileName = "";
		String xdgDataHome = System.getenv("XDG_DATA_HOME");
		if (xdgDataHome == null) xdgDataHome = System.getProperty("user.home") + "/.local/share";
		String home = System.getProperty("user.home");
		return new File[] {
				new File(home + "/My Documents/My Games/FasterThanLight/" + fileName), // Windows XP
				new File(home + "/Documents/My Games/FasterThanLight/" + fileName), // Windows Vista and later
				new File(xdgDataHome + "/FasterThanLight/" + fileName), // Linux
				new File(home + "/Library/Application Support/FasterThanLight/" + fileName) }; // macOS
	}

	/** The folder the program runs from: the jar's, or the working directory when that can't be told. */
	public static File appDir() {
		try {
			File jar = new File(HomePlanet.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (jar.isFile()) return jar.getAbsoluteFile().getParentFile();
		} catch (Exception e) { }
		return new File(".").getAbsoluteFile().getParentFile();
	}

	// ---- logging: the console, and a file per run in logs/ beside the program ----

	private static File logDir;
	private static final int KEEP_LOGS = 8;
	/** Where the log files go (null if none could be opened). */
	public static File logDir() { return logDir; }
	private static ch.qos.logback.classic.Logger rootLogger() {
		org.slf4j.Logger root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		return root instanceof ch.qos.logback.classic.Logger ? (ch.qos.logback.classic.Logger) root : null;
	}
	/**
	 * Opens this run's log file: logs/home-planet-<date>-<time>.log beside the program, the last {@link #KEEP_LOGS} kept.
	 * Warnings and errors always go in it; everything does when debug logging is on. The console shows only what debug
	 * logging allows, as before.
	 */
	private static void startFileLog() {
		ch.qos.logback.classic.Logger root = rootLogger();
		if (root == null) return;
		try {
			File dir = new File(appDir(), "logs");
			dir.mkdirs();
			File[] old = dir.listFiles(new java.io.FilenameFilter() {
				public boolean accept(File d, String n) { return n.startsWith("home-planet-") && n.endsWith(".log"); }
			});
			if (old != null && old.length >= KEEP_LOGS) {
				java.util.Arrays.sort(old);
				for (int i = 0; i <= old.length - KEEP_LOGS; i++) old[i].delete();
			}
			File f = new File(dir, "home-planet-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date()) + ".log");
			ch.qos.logback.classic.LoggerContext ctx = root.getLoggerContext();
			ch.qos.logback.classic.encoder.PatternLayoutEncoder enc = new ch.qos.logback.classic.encoder.PatternLayoutEncoder();
			enc.setContext(ctx);
			enc.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger{0} - %msg%n");
			enc.start();
			ch.qos.logback.core.FileAppender<ch.qos.logback.classic.spi.ILoggingEvent> app = new ch.qos.logback.core.FileAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
			app.setContext(ctx);
			app.setName("FILE");
			app.setFile(f.getPath());
			app.setEncoder(enc);
			app.start();
			root.addAppender(app);
			root.setLevel(ch.qos.logback.classic.Level.DEBUG); // the appenders' own thresholds decide what's kept
			logDir = dir;
		} catch (Exception e) {
			System.err.println("No log file: " + e);
		}
	}
	private static void threshold(ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> app, ch.qos.logback.classic.Level level) {
		if (app == null) return;
		app.clearAllFilters();
		ch.qos.logback.classic.filter.ThresholdFilter f = new ch.qos.logback.classic.filter.ThresholdFilter();
		f.setLevel(level.toString());
		f.start();
		app.addFilter(f);
	}
	/** Turns debug logging on or off (off by default): the console then shows everything, and so does the log file. */
	public static void setDebugLogging(boolean debug) {
		debugLogging = debug;
		ch.qos.logback.classic.Logger root = rootLogger();
		if (root == null) return;
		if (logDir == null) { root.setLevel(debug ? ch.qos.logback.classic.Level.DEBUG : ch.qos.logback.classic.Level.WARN); return; }
		threshold(root.getAppender("CONSOLE"), debug ? ch.qos.logback.classic.Level.DEBUG : ch.qos.logback.classic.Level.WARN);
		threshold(root.getAppender("FILE"), debug ? ch.qos.logback.classic.Level.DEBUG : ch.qos.logback.classic.Level.INFO);
	}
}
