package homeplanet.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.vhati.modmanager.core.FTLUtilities;

/**
 * Every Steam library on this computer (7.13): Steam's own, and the ones it keeps elsewhere, such as a Steam Deck's SD card
 * or a second drive on Windows. Steam lists them in libraryfolders.vdf. The station looks in each for FTL's files, and for
 * the saves the Windows FTL keeps under Proton.
 */
public final class SteamLibraries {
	private SteamLibraries() { }

	/** FTL's folder in a library. */
	public static final String FTL_FOLDER = "common/FTL Faster Than Light";
	/** Where the Windows FTL keeps its saves when Steam runs it through Proton on Linux, inside the library it's installed in. */
	public static final String PROTON_SAVES = "compatdata/" + FTLUtilities.STEAM_APPID_FTL
			+ "/pfx/drive_c/users/steamuser/Documents/My Games/FasterThanLight";

	/** Each library's steamapps folder, Steam's own first; none if Steam isn't installed where the station looks. */
	public static List<File> steamapps() {
		Map<String, File> out = new LinkedHashMap<String, File>(); // by real path: ~/.steam/steam is a link to Steam's folder
		for (File root : roots()) {
			add(out, new File(root, "steamapps"));
			for (String vdf : new String[] {"steamapps/libraryfolders.vdf", "config/libraryfolders.vdf"}) {
				File f = new File(root, vdf);
				if (!f.isFile()) continue;
				try { for (String path : paths(new String(SafeFiles.read(f), StandardCharsets.UTF_8))) add(out, new File(path, "steamapps")); }
				catch (IOException e) { /* unreadable: the libraries found so far */ }
			}
		}
		return new ArrayList<File>(out.values());
	}
	/** The folders Steam is installed in, by system. */
	private static List<File> roots() {
		List<File> out = new ArrayList<File>();
		String home = System.getProperty("user.home"), xdg = System.getenv("XDG_DATA_HOME");
		if (System.getProperty("os.name", "").startsWith("Windows")) {
			for (String pf : new String[] {System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles")}) if (pf != null) out.add(new File(pf, "Steam"));
		} else {
			out.add(new File(xdg != null ? xdg : home + "/.local/share", "Steam")); // Linux, the Steam Deck included
			out.add(new File(home, ".steam/steam"));
			out.add(new File(home, ".var/app/com.valvesoftware.Steam/.local/share/Steam")); // Steam as a Flatpak
			out.add(new File(home, "Library/Application Support/Steam")); // macOS
		}
		return out;
	}
	private static final Pattern PATH = Pattern.compile("\"path\"\\s+\"((?:[^\"\\\\]|\\\\.)*)\"");
	/** The libraries' folders in a libraryfolders.vdf (its "path" entries; Windows' backslashes come doubled). */
	static List<String> paths(String vdf) {
		List<String> out = new ArrayList<String>();
		Matcher m = PATH.matcher(vdf);
		while (m.find()) out.add(m.group(1).replace("\\\\", "\\"));
		return out;
	}
	private static void add(Map<String, File> out, File steamapps) {
		if (!steamapps.isDirectory()) return;
		String key;
		try { key = steamapps.getCanonicalPath(); } catch (IOException e) { key = steamapps.getAbsolutePath(); }
		if (!out.containsKey(key)) out.put(key, steamapps);
	}
}
