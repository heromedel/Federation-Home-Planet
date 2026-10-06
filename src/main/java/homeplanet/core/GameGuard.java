package homeplanet.core;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Whether FTL itself is running. While it is, the game owns continue.sav: it can rewrite it at any moment and
 * holds it open, so nothing here may board, dock, trade or otherwise touch the saves. Every such action asks
 * {@link #allows(Component, String)} first.
 */
public final class GameGuard {
	private static final Logger log = LoggerFactory.getLogger(GameGuard.class);
	private GameGuard() { }

	/** Set by the test harness (a system property) to skip the process check where no game could be running. */
	private static final boolean DISABLED = Boolean.getBoolean("homeplanet.noGameCheck");

	/** True if FTL's process is running: FTLGame.exe on Windows, "FTL" elsewhere (Steam's Linux and Mac builds). */
	public static boolean isFtlRunning() {
		if (DISABLED) return false;
		String os = System.getProperty("os.name", "");
		try {
			Process p;
			if (os.startsWith("Windows")) {
				p = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq FTLGame.exe", "/NH").redirectErrorStream(true).start();
				return outputContains(p, "ftlgame.exe");
			}
			// Linux and macOS: the Steam and GOG builds run as "FTL" (or FTL.amd64 / FTL.x86); ps lists every command name
			p = new ProcessBuilder("ps", "-A", "-o", "comm=").redirectErrorStream(true).start();
			return outputMatches(p);
		} catch (Exception e) {
			log.debug("Could not check whether FTL is running: {}", e.toString());
			return false;
		}
	}
	private static boolean outputContains(Process p, String needle) throws Exception {
		BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String l;
		boolean found = false;
		while ((l = r.readLine()) != null) if (l.toLowerCase().contains(needle)) found = true;
		p.waitFor();
		return found;
	}
	private static boolean outputMatches(Process p) throws Exception {
		BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String l;
		boolean found = false;
		while ((l = r.readLine()) != null) {
			String name = l.trim();
			int slash = name.lastIndexOf('/');
			if (slash >= 0) name = name.substring(slash + 1);
			if (name.equals("FTL") || name.startsWith("FTL.") || name.equalsIgnoreCase("FTLGame")) found = true;
		}
		p.waitFor();
		return found;
	}

	/**
	 * True if it's safe to go ahead with {@code action} ("board a ship", say). When FTL is running, says why the station
	 * would rather not and lets the player choose; the first answer is a plain "nevermind" (the default), never a
	 * warning that something is wrong. It's a question rather than a wall because FTL at its main menu hasn't loaded
	 * the save yet, and a player who knows that may want to carry on.
	 */
	/** heromedel's words (5.29), wherever the station says to close FTL first. */
	public static final String CLOSE_FTL = "Return to The Station to do this. (Close FTL)";
	public static boolean allows(Component owner, String action) {
		if (!isFtlRunning()) return true;
		String nevermind = action.equals("board a ship") ? "Nevermind, save her in the Space Dock" : "Nevermind"; // heromedel's words for boarding
		Object[] opts = {nevermind, "Go ahead, FTL is at its menu"};
		int r = JOptionPane.showOptionDialog(owner, "FTL is running. While it is, it may write over the ship you're flying at any moment,\n"
				+ "and a change The Home Planet Station makes to her then is lost.\n\n"
				+ "If FTL is only at its main menu, she isn't loaded, and it's safe to " + action + ". Otherwise: " + CLOSE_FTL,
				"FTL is running", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
		return r == 1;
	}
}
