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
	public static final String APP_VERSION = "4B.03";
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
	/** Scrapping a ship also moves her (optional) systems to the Cargo Bay. Off by default. */
	public static boolean scrapKeepsSystems = false;
	/** House rule: missiles and drone parts can be sold at half the store price (FTL's own stores don't buy them). */
	public static boolean sellSupplies = false;
	/** Commission: only ship layouts the FTL profile has unlocked; and (nested) custom ships only if their base layout is unlocked. */
	public static boolean commissionUnlockedOnly = false, commissionCustomUnlockedOnly = false;
	public static boolean debugLogging = false;

	/** The config file, beside the program, and its values (the Settings window changes and saves them). */
	public static final File propFile = new File("federation-home-planet.cfg");
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
		scrapKeepsSystems = flag("scrap_keeps_systems");
		sellSupplies = flag("sell_supplies");
		commissionUnlockedOnly = flag("commission_unlocked_only");
		commissionCustomUnlockedOnly = flag("commission_custom_unlocked_only");
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
			if (datsPath != null && !confirm("FTL's files were found in:\n" + datsPath.getPath() + "\nIs this correct?", "Confirm")) datsPath = null;
			if (datsPath == null) datsPath = promptForFtlPath();
			if (datsPath != null) { config.setProperty("ftlDatsPath", datsPath.getAbsolutePath()); writeConfig = true; }
		}
		if (datsPath == null) {
			showErrorDialog("FTL's files were not found.\n" + APP_NAME + " will now exit.");
			System.exit(1);
		}
		// First setup, asked once: Steam launching (for the Steam version), then the House Rules window while any rule was never set.
		// A rule missing from the config starts ticked, except selling missiles and drone parts; rules already set keep their value.
		if (config.getProperty("launch_through_steam") == null && datsPath.getAbsolutePath().toLowerCase().contains("steamapps")) {
			launchThroughSteam = confirm("This looks like the Steam version of FTL.\nLaunch FTL through Steam?", "Launch through Steam");
			config.setProperty("launch_through_steam", Boolean.toString(launchThroughSteam));
			writeConfig = true;
		}
		boolean rulesMissing = false;
		for (String key : RULE_KEYS) if (config.getProperty(key) == null) rulesMissing = true;
		if (rulesMissing) {
			storeRequirement = flag("store_requirement", true);
			journeyStoreRequirement = flag("new_journey_store_requirement", true);
			scrapKeepsSystems = flag("scrap_keeps_systems", true);
			sellSupplies = flag("sell_supplies", false);
			commissionUnlockedOnly = flag("commission_unlocked_only", true);
			commissionCustomUnlockedOnly = flag("commission_custom_unlocked_only", true);
			homeplanet.ui.HouseRulesDialog.ask();
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
			if (save_location != null && !confirm("FTL's saves were found in:\n" + save_location.getPath() + "\nIs this correct?", "Confirm")) save_location = null;
			if (save_location == null) save_location = promptForSavePath();
			if (save_location != null) { config.setProperty("ftlSavePath", save_location.getAbsolutePath()); writeConfig = true; }
		}
		if (save_location == null) {
			showErrorDialog("FTL's saves folder was not found.\n" + APP_NAME + " will now exit.");
			System.exit(1);
		}
		if (writeConfig) saveConfig();

		// The vault (files only so far; the ships are read once the game data is in)
		try {
			Vault.open(save_location);
		} catch (IOException e) {
			log.error("Could not open the vault in " + save_location, e);
			showErrorDialog("Could not open the vault in:\n" + save_location + "\n\n" + e);
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
			showErrorDialog("Error reading FTL's files in:\n" + datsPath + "\n\n" + e);
			System.exit(1);
		}

		try {
			Vault.get().takeStock();
			Vault.get().storage();
		} catch (IOException e) {
			log.error("Could not take stock of the vault", e);
			showErrorDialog("Could not take stock of the vault:\n" + e);
		}

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					// a click outside an open dropdown list only closes it; it doesn't also press whatever is underneath
					javax.swing.UIManager.put("PopupMenu.consumeEventOnClose", Boolean.TRUE);
					MainFrame frame = new MainFrame(APP_NAME, APP_VERSION);
					frame.setVisible(true);
					Music.refresh();
				} catch (Exception e) {
					log.error("Exception while creating the main window.", e);
					showErrorDialog("The station could not be opened:\n" + e);
					System.exit(1);
				}
			}
		});
	}

	// ---- config ----

	private static boolean flag(String key) { return flag(key, false); }
	private static boolean flag(String key, boolean dflt) { return Boolean.parseBoolean(config.getProperty(key, Boolean.toString(dflt))); }

	/** The rules the first-run House Rules window sets. */
	private static final String[] RULE_KEYS = {"store_requirement", "new_journey_store_requirement", "scrap_keeps_systems", "sell_supplies",
			"commission_unlocked_only", "commission_custom_unlocked_only"};

	/** Reads the config; imports FTL Homeworld's old one when there's none yet. Returns true if it should be written. */
	private static boolean loadConfig() {
		if (!propFile.isFile()) return true;
		InputStream in = null;
		try {
			in = new FileInputStream(propFile);
			config.load(in);
		} catch (IOException e) {
			showErrorDialog("Error loading the config from " + propFile.getPath());
			log.error("Could not read " + propFile, e);
		} finally {
			try { if (in != null) in.close(); } catch (IOException e) { }
		}
		return false;
	}

	/** Writes the current settings to the cfg (a temporary file, then one move). Returns false, after telling the user, on failure. */
	public static boolean saveConfig() {
		if (save_location != null) config.setProperty("ftlSavePath", save_location.getAbsolutePath());
		if (datsPath != null) config.setProperty("ftlDatsPath", datsPath.getAbsolutePath());
		config.setProperty("launch_through_steam", Boolean.toString(launchThroughSteam));
		config.setProperty("debug_logging", Boolean.toString(debugLogging));
		config.setProperty("store_requirement", Boolean.toString(storeRequirement));
		config.setProperty("new_journey_store_requirement", Boolean.toString(journeyStoreRequirement));
		config.setProperty("scrap_keeps_systems", Boolean.toString(scrapKeepsSystems));
		config.setProperty("sell_supplies", Boolean.toString(sellSupplies));
		config.setProperty("commission_unlocked_only", Boolean.toString(commissionUnlockedOnly));
		config.setProperty("commission_custom_unlocked_only", Boolean.toString(commissionCustomUnlockedOnly));
		config.setProperty("title_music", Boolean.toString(Music.enabled));
		try {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			config.store(buf, APP_NAME + " - config file");
			SafeFiles.write(propFile, buf.toByteArray(), false);
			return true;
		} catch (IOException e) {
			log.error("Error saving config to " + propFile.getPath(), e);
			showErrorDialog("Error saving the config to " + propFile.getPath());
			return false;
		}
	}

	// ---- dialogs ----

	private static boolean confirm(String message, String title) {
		return JOptionPane.showConfirmDialog(null, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
	}
	/** Yes or No, starting on No: for anything that can't be undone (selling, junking, retiring). Closing the window means No. */
	public static boolean confirmNo(java.awt.Component owner, String message, String title) {
		Object[] options = {"Yes", "No"};
		return JOptionPane.showOptionDialog(owner, message, title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]) == 0;
	}
	public static void showErrorDialog(String message) {
		JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
	}

	// ---- FTL itself ----

	public static void launchFTL() {
		// a retrofitted ship can't load without the companion mod: don't let FTL try
		File cont = new File(save_location, "continue.sav");
		if (cont.exists() && !modPatchedThisSession) {
			List<String> missing = Retrofit.missingBlueprints(cont);
			if (!missing.isEmpty()) {
				showErrorDialog("The boarded ship needs the " + Retrofit.MOD_NAME + ", which isn't in the game data ("
						+ String.join(", ", missing) + ").\n\nInstall it first (Settings > Patch mods), or board a different ship.");
				return;
			}
		}
		Music.stop(); // FTL has its own music
		if (launchThroughSteam) {
			String steamUri = "steam://rungameid/" + FTLUtilities.STEAM_APPID_FTL;
			log.debug("Running FTL through Steam: {}", steamUri);
			try {
				if (System.getProperty("os.name").startsWith("Windows")) new ProcessBuilder("cmd", "/c", "start", "", steamUri).start();
				else java.awt.Desktop.getDesktop().browse(new java.net.URI(steamUri));
			} catch (Exception ex) {
				log.error("Could not launch FTL through Steam.", ex);
				showErrorDialog("Could not launch FTL through Steam:\n" + ex);
			}
			return;
		}
		// FTL 1.6+ keeps FTLGame.exe beside ftl.dat; older versions had it one folder up from resources/
		File ftl = FTLUtilities.findGameExe(datsPath);
		if (ftl == null) {
			log.warn("Could not find the FTL executable near {}", datsPath);
			showErrorDialog("Could not find FTL's executable near:\n" + datsPath);
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

	// ---- finding FTL ----

	private static boolean isDatsPathValid(File path) {
		// FTL 1.6+: ftl.dat. Older versions: data.dat and resource.dat.
		return path.exists() && path.isDirectory() && FTLUtilities.isDatsDirValid(path);
	}
	public static File promptForFtlPath() {
		JOptionPane.showMessageDialog(null, APP_NAME + " uses images and data from FTL,\nbut the path to FTL's files could not be guessed.\n\n"
				+ "Select 'ftl.dat' in your FTL folder (FTL 1.6 and newer),\nor '(FTL dir)/resources/data.dat' for older versions,\nor 'FTL.app' on a Mac.",
				"FTL Not Found", JOptionPane.INFORMATION_MESSAGE);
		File ftlPath = null;
		File f = pickFile(null, "Find ftl.dat, data.dat or FTL.app", "FTL Resources (ftl.dat; data.dat; FTL.app)", firstFolder(
				new File(System.getenv("ProgramFiles(x86)") + "/Steam/steamapps/common"), new File("C:/Program Files (x86)/Steam/steamapps/common"),
				new File(System.getenv("ProgramFiles") + "/Steam/steamapps/common")), "*.dat", "ftl.dat", "data.dat", "FTL.app");
		if (f != null) {
			if (f.getName().equalsIgnoreCase("ftl.dat") || f.getName().equalsIgnoreCase("data.dat")) ftlPath = f.getParentFile();
			else if (f.getName().endsWith(".app") && f.isDirectory()) {
				File contentsPath = new File(f, "Contents");
				if (new File(contentsPath, "Resources").exists()) ftlPath = new File(contentsPath, "Resources");
			}
		}
		return ftlPath != null && isDatsPathValid(ftlPath) ? ftlPath : null;
	}
	public static File promptForSavePath() {
		JOptionPane.showMessageDialog(null, APP_NAME + " manages saves from FTL,\nbut the path to FTL's saves could not be guessed.\n\n"
				+ "Select '/Documents/My Games/FasterThanLight/continue.sav' (or ae_prof.sav).", "FTL Save Not Found", JOptionPane.INFORMATION_MESSAGE);
		File f = pickFile(null, "Find continue.sav or ae_prof.sav", "FTL save files (continue.sav, ae_prof.sav, prof.sav)",
				firstFolder(new File(System.getProperty("user.home"), "Documents/My Games/FasterThanLight"), new File(System.getProperty("user.home"), "Documents/My Games")),
				"*.sav", "continue.sav", "ae_prof.sav", "prof.sav");
		File path = f == null ? null : f.getParentFile();
		return path != null && path.isDirectory() ? path : null;
	}

	/**
	 * Asks for one file. On Windows it's Windows' own Open window (Quick Access, recent folders, search); elsewhere Java's
	 * file chooser, showing folders and the named files. {@code pattern} filters Windows' window ("*.dat"). Starts in
	 * {@code startIn} when it's a folder. Returns the file picked, or null.
	 */
	public static File pickFile(java.awt.Component owner, String title, String description, File startIn, String pattern, final String... names) {
		if (System.getProperty("os.name").startsWith("Windows")) {
			java.awt.Window w = owner == null ? null : owner instanceof java.awt.Window ? (java.awt.Window) owner : javax.swing.SwingUtilities.getWindowAncestor(owner);
			java.awt.FileDialog fd = w instanceof java.awt.Dialog ? new java.awt.FileDialog((java.awt.Dialog) w, title, java.awt.FileDialog.LOAD)
					: new java.awt.FileDialog(w instanceof java.awt.Frame ? (java.awt.Frame) w : null, title, java.awt.FileDialog.LOAD);
			if (startIn != null) fd.setDirectory(startIn.getAbsolutePath());
			fd.setFile(pattern);
			fd.setVisible(true); // waits
			return fd.getFile() == null ? null : new File(fd.getDirectory(), fd.getFile());
		}
		JFileChooser fc = new JFileChooser(startIn);
		fc.setDialogTitle(title);
		fc.setFileHidingEnabled(false);
		fc.addChoosableFileFilter(new FileFilter() {
			@Override public String getDescription() { return description; }
			@Override public boolean accept(File f) {
				if (f.isDirectory()) return true;
				for (String n : names) if (f.getName().equalsIgnoreCase(n)) return true;
				return false;
			}
		});
		return fc.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
	}
	/** The first of these that is a folder, or null. */
	private static File firstFolder(File... candidates) {
		for (File f : candidates) if (f.isDirectory()) return f;
		return null;
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
