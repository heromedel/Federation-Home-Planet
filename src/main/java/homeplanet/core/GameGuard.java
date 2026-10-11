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
		if (Boolean.getBoolean("homeplanet.ftlRunning")) return true; // the harness's stand-in for a running FTL (read each time, 5.81)
		if (DISABLED) return false;
		String os = System.getProperty("os.name", "");
		try {
			Process p;
			if (os.startsWith("Windows")) {
				Boolean quick = Processes.running("FTLGame.exe");
				if (quick != null) return quick;
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
	/** Loads the Windows process check in the background at startup, so the first Board or Dock doesn't wait for JNA. */
	public static void warm() {
		if (DISABLED || !System.getProperty("os.name", "").startsWith("Windows")) return;
		Thread t = new Thread(new Runnable() { public void run() { Processes.running("FTLGame.exe"); } }, "FTL check warm-up");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Windows' own process list, through JNA (6.02): tasklist took most of a second before every Board and Dock
	 * (heromedel: "it still feels like its taking 1-2 full seconds"). Null when it can't be trusted (JNA missing, a
	 * call failed, or the list doesn't show the station itself): tasklist answers then.
	 */
	static final class Processes {
		interface Kernel32 extends com.sun.jna.win32.StdCallLibrary {
			Kernel32 I = com.sun.jna.Native.load("kernel32", Kernel32.class);
			com.sun.jna.Pointer CreateToolhelp32Snapshot(int flags, int pid);
			boolean Process32FirstW(com.sun.jna.Pointer snapshot, Entry entry);
			boolean Process32NextW(com.sun.jna.Pointer snapshot, Entry entry);
			boolean CloseHandle(com.sun.jna.Pointer handle);
			int GetCurrentProcessId();
		}
		/** PROCESSENTRY32W. */
		@com.sun.jna.Structure.FieldOrder({"dwSize", "cntUsage", "th32ProcessID", "th32DefaultHeapID", "th32ModuleID", "cntThreads",
				"th32ParentProcessID", "pcPriClassBase", "dwFlags", "szExeFile"})
		public static class Entry extends com.sun.jna.Structure {
			public int dwSize, cntUsage, th32ProcessID;
			public com.sun.jna.Pointer th32DefaultHeapID; // ULONG_PTR: the pointer's size
			public int th32ModuleID, cntThreads, th32ParentProcessID, pcPriClassBase, dwFlags;
			public char[] szExeFile = new char[260]; // wide characters
			public Entry() { dwSize = size(); }
		}
		static final int TH32CS_SNAPPROCESS = 0x2;
		static Boolean running(String exe) {
			try {
				com.sun.jna.Pointer snap = Kernel32.I.CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
				if (snap == null || com.sun.jna.Pointer.nativeValue(snap) == -1) return null;
				try {
					int me = Kernel32.I.GetCurrentProcessId();
					boolean found = false, sawMe = false;
					Entry e = new Entry();
					for (boolean more = Kernel32.I.Process32FirstW(snap, e); more; more = Kernel32.I.Process32NextW(snap, e)) {
						String name = com.sun.jna.Native.toString(e.szExeFile);
						if (e.th32ProcessID == me && name.toLowerCase().endsWith(".exe")) sawMe = true;
						if (exe.equalsIgnoreCase(name)) found = true;
					}
					return sawMe ? Boolean.valueOf(found) : null; // the station's own entry proves the list was read right
				} finally {
					Kernel32.I.CloseHandle(snap);
				}
			} catch (Throwable t) {
				log.debug("Windows' process list could not be read ({}): asking tasklist", t.toString());
				return null;
			}
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
		String nevermind = action.equals("board a ship") ? "Nevermind, save " + homeplanet.model.Words.herObj() + " in the Space Dock" : "Nevermind"; // heromedel's words for boarding
		Object[] opts = {nevermind, "Go ahead, FTL is at its menu"};
		int r = JOptionPane.showOptionDialog(owner, "FTL is running.\n\nWhile it is, it may write over the ship you're flying at any moment, "
				+ "and a change The Home Planet Station makes to " + homeplanet.model.Words.herObj() + " then is lost.\n\n"
				+ "If FTL is only at its main menu, " + homeplanet.model.Words.she() + " isn't loaded, and it's safe to " + action + ".\nOtherwise: " + CLOSE_FTL,
				"FTL is running", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
		return r == 1;
	}
}
