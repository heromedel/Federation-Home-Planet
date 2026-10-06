package homeplanet.core;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Playing FTL docked in the station window (heromedel, 5.29; Windows only): FTL in windowed mode, borderless, placed
 * over a viewport on the Space Dock and kept there, lifted just above the station's window whenever that's activated.
 * Never owned by it (5.31): a window owned across programs shares their input, and FTL's hidden cursor with it. FTL
 * stays its own program: if anything here fails, it simply runs as a normal window.
 *
 * Its settings.ini (%APPDATA%\FasterThanLight on Windows, 5.31; not beside the saves) is edited in place, one key only
 * (fullscreen, to 0: windowed), never copied over: the player's other settings are theirs. The key's old value is kept in
 * the cfg and put back when the option is turned off, unless the player has changed it since.
 */
public final class FtlDock {
	private static final Logger log = LoggerFactory.getLogger(FtlDock.class);
	private FtlDock() { }

	public static final String CFG_ON = "ftl_docked", CFG_SIZE = "ftl_docked_size", CFG_WAS = "ftl_docked_fullscreen_was";
	/** The sizes offered: FTL's own 16:9 steps (1280x720 is the smallest it draws at). */
	public static final String[] SIZES = {"1280x720", "1600x900", "1920x1080"};
	/** FTL's window title: how its window is found. */
	static final String TITLE = "FTL: Faster Than Light";
	/** "Value kept" marker for a settings.ini with no fullscreen line. */
	private static final String NONE = "(none)";

	/** Docking is offered: on Windows (or, for screenshots of the layout, when asked; JNA is never loaded then). */
	public static boolean supported() {
		return System.getProperty("os.name", "").startsWith("Windows") || Boolean.getBoolean("homeplanet.dockAnyOS");
	}
	/** The option is on (Settings > Launching). */
	public static boolean optionOn() { return supported() && Boolean.parseBoolean(HomePlanet.config.getProperty(CFG_ON, "false")); }
	/** The docked size chosen. */
	public static Dimension size() {
		String s = HomePlanet.config.getProperty(CFG_SIZE, SIZES[0]);
		Matcher m = Pattern.compile("(\\d+)x(\\d+)").matcher(s);
		if (m.matches()) return new Dimension(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
		return new Dimension(1280, 720);
	}

	// ---- settings.ini: one key, edited in place ----

	private static final Pattern FULLSCREEN = Pattern.compile("(?m)^([ \\t]*fullscreen[ \\t]*=[ \\t]*)([^\\r\\n]*)$");

	/** FTL's settings.ini: %APPDATA%\FasterThanLight on Windows (the harness names its own). */
	static File settingsFile() {
		String test = System.getProperty("homeplanet.ftlSettings");
		if (test != null) return new File(test);
		String appData = System.getenv("APPDATA");
		return appData == null ? null : new File(new File(appData, "FasterThanLight"), "settings.ini");
	}
	/**
	 * 5.29 wrote its fullscreen line beside the saves, where FTL never reads it, and often made the file to do so: that
	 * file goes if the line is still all it holds, with the old value kept for it (which wasn't the player's).
	 */
	public static void cleanUpStray() {
		File saves = HomePlanet.save_location;
		File stray = saves == null ? null : new File(saves, "settings.ini");
		try {
			if (stray == null || !stray.isFile() || stray.equals(settingsFile())) return;
			if (!new String(SafeFiles.read(stray), StandardCharsets.UTF_8).trim().equals("fullscreen=0")) return;
			if (stray.delete()) log.info("FTL docked: removed the station's stray settings.ini beside the saves");
			if (HomePlanet.config.remove(CFG_WAS) != null) HomePlanet.saveConfig();
		} catch (IOException e) { log.debug("FTL docked: could not read {}: {}", stray, e.toString()); }
	}

	/** Before a docked launch: fullscreen to windowed (0), keeping the old value the first time. */
	public static void prepareSettings() throws IOException {
		cleanUpStray();
		File f = settingsFile();
		if (f == null) return;
		if (!f.isFile() && (f.getParentFile() == null || !f.getParentFile().isDirectory())) return; // no FTL settings yet: it starts windowed or as it likes
		String text = f.isFile() ? new String(SafeFiles.read(f), StandardCharsets.UTF_8) : "";
		Matcher m = FULLSCREEN.matcher(text);
		String was = m.find() ? m.group(2).trim() : null;
		if (HomePlanet.config.getProperty(CFG_WAS) == null) { // the player's own value, kept once
			HomePlanet.config.setProperty(CFG_WAS, was == null ? NONE : was);
			HomePlanet.saveConfig();
		}
		if ("0".equals(was)) return; // windowed already
		String out = was == null ? text + (text.isEmpty() || text.endsWith("\n") ? "" : "\n") + "fullscreen=0\n" : m.replaceFirst(Matcher.quoteReplacement(m.group(1)) + "0");
		SafeFiles.writeText(f, out, false);
		log.info("FTL docked: settings.ini set to windowed (fullscreen was {})", was == null ? "unset" : was);
	}
	/** The option turned off: the player's fullscreen value back, unless they've changed it since (it isn't 0 any more). */
	public static void restoreSettings() throws IOException {
		String was = HomePlanet.config.getProperty(CFG_WAS);
		if (was == null) return;
		HomePlanet.config.remove(CFG_WAS);
		HomePlanet.saveConfig();
		File f = settingsFile();
		if (f == null || !f.isFile()) return;
		String text = new String(SafeFiles.read(f), StandardCharsets.UTF_8);
		Matcher m = FULLSCREEN.matcher(text);
		if (!m.find() || !"0".equals(m.group(2).trim())) return; // theirs now: left alone
		String out = NONE.equals(was) ? text.substring(0, m.start()) + text.substring(Math.min(text.length(), m.end() + (text.startsWith("\r\n", m.end()) ? 2 : text.startsWith("\n", m.end()) ? 1 : 0)))
				: m.replaceFirst("$1" + Matcher.quoteReplacement(was));
		SafeFiles.writeText(f, out, false);
		log.info("FTL docked: settings.ini's fullscreen put back to {}", was);
	}

	// ---- the session: one docked run of FTL ----

	private static volatile boolean active = false;
	private static Object window; // FTL's window (a JNA Pointer), once found
	private static boolean shown = true;
	private static boolean aside; // the Space Dock shows the docked ships in FTL's place (5.32)
	private static Rectangle where;

	/** A docked run is on: from the docked launch until FTL's window closes (or isn't found). */
	public static boolean active() { return active; }
	/** Starts a docked run (FTL being launched): the Space Dock lays out its viewport. */
	public static void begin() { active = true; window = null; shown = true; aside = false; where = null; }
	/** Ends it: FTL closed, or its window never turned up. */
	public static void end() { active = false; window = null; where = null; aside = false; }
	/** The Space Dock shows the docked ships in FTL's place (heromedel, 5.32): FTL hidden, still running. */
	public static boolean aside() { return aside; }
	public static void setAside(boolean ships) { aside = ships; show(!ships); }
	/** Back at the Space Dock: FTL shown in its viewport, unless the docked ships are shown there instead. */
	public static void backAtDock() { show(!aside); }

	/** What a look for FTL's window found. */
	public enum Found { NOT_YET, DOCKED, FULLSCREEN }
	/** Looks for FTL's window: docked once found (made borderless), unless it's full screen (then left alone). Windows only. */
	public static Found find() {
		if (window != null) return Found.DOCKED;
		if (!System.getProperty("os.name", "").startsWith("Windows")) return Found.NOT_YET;
		try {
			Object w = Win.find(TITLE);
			if (w == null) return Found.NOT_YET;
			java.awt.Rectangle r = Win.bounds(w);
			java.awt.Rectangle screen = Win.screenOf(w);
			if (screen != null && r.width >= screen.width && r.height >= screen.height) { log.info("FTL docked: FTL is full screen; left as it is"); return Found.FULLSCREEN; }
			Win.adopt(w);
			window = w;
			if (!shown) Win.show(w, false); // found while the station shows something else: hidden till it's back
			log.info("FTL docked: its window was found");
			if (where != null) place(where);
			return Found.DOCKED;
		} catch (Throwable t) { // no JNA, a refused call: FTL just runs as a normal window
			log.debug("FTL docked: its window could not be docked: {}", t.toString());
			return Found.NOT_YET;
		}
	}
	/** FTL's window just above the station's (when the station is activated), without taking the keyboard. */
	public static void raise() {
		if (window == null || !shown) return;
		try { Win.raise(window); } catch (Throwable t) { log.debug("FTL docked: could not lift its window: {}", t.toString()); }
	}
	/**
	 * The station's own window put just under FTL's (5.34): Windows may refuse to lift another program's window over the
	 * one in use, but a program may always arrange its own. Logged, so a debug log shows whether Windows took it.
	 */
	public static void tuckUnder(java.awt.Window station) {
		if (window == null || !shown || station == null || !station.isDisplayable()) return;
		try {
			boolean ok = Win.under(station, window);
			log.debug("FTL docked: the station's window put under FTL's: {}", ok ? "done" : "refused by Windows");
		} catch (Throwable t) { log.debug("FTL docked: could not put the station's window under FTL's: {}", t.toString()); }
	}
	/** FTL to the front with the keyboard (the Unpause screen clicked, 5.33): the station has the keyboard to give. */
	public static void focus() {
		if (window == null || !shown) return;
		try { Win.focus(window); } catch (Throwable t) { log.debug("FTL docked: could not bring it to the front: {}", t.toString()); }
	}
	/** FTL's window has been found and docked. */
	public static boolean found() { return window != null; }
	/** FTL's window is still there. */
	public static boolean alive() {
		try { return window != null && Win.alive(window); } catch (Throwable t) { return false; }
	}
	/** Places FTL's window over the viewport (screen coordinates, in Java's units). */
	public static void place(Rectangle screen) {
		where = screen;
		if (window == null || !shown) return;
		try { Win.place(window, screen); } catch (Throwable t) { log.debug("FTL docked: could not place its window: {}", t.toString()); }
	}
	/** Shows it in its viewport (back at the Space Dock) or hides it (another screen); FTL keeps running either way. */
	public static void show(boolean visible) {
		if (shown == visible) return;
		shown = visible;
		if (window == null) return;
		try {
			if (visible) { Win.show(window, true); if (where != null) Win.place(window, where); }
			else Win.show(window, false);
		} catch (Throwable t) { log.debug("FTL docked: could not {} its window: {}", visible ? "show" : "hide", t.toString()); }
	}

	/** Windows' own calls, through JNA: loaded only when docking runs on Windows. */
	private static final class Win {
		interface User32 extends com.sun.jna.win32.StdCallLibrary {
			User32 I = com.sun.jna.Native.load("user32", User32.class);
			interface Each extends com.sun.jna.win32.StdCallLibrary.StdCallCallback { boolean callback(com.sun.jna.Pointer hwnd, com.sun.jna.Pointer data); }
			boolean EnumWindows(Each each, com.sun.jna.Pointer data);
			int GetWindowTextW(com.sun.jna.Pointer hwnd, char[] text, int max);
			boolean IsWindow(com.sun.jna.Pointer hwnd);
			boolean IsWindowVisible(com.sun.jna.Pointer hwnd);
			int GetWindowLongW(com.sun.jna.Pointer hwnd, int index);
			int SetWindowLongW(com.sun.jna.Pointer hwnd, int index, int value);
			boolean SetWindowPos(com.sun.jna.Pointer hwnd, com.sun.jna.Pointer after, int x, int y, int w, int h, int flags);
			boolean ShowWindow(com.sun.jna.Pointer hwnd, int cmd);
			boolean SetForegroundWindow(com.sun.jna.Pointer hwnd);
			boolean GetWindowRect(com.sun.jna.Pointer hwnd, int[] rect); // left, top, right, bottom
			com.sun.jna.Pointer MonitorFromWindow(com.sun.jna.Pointer hwnd, int flags);
			boolean GetMonitorInfoW(com.sun.jna.Pointer monitor, int[] info); // cbSize, monitor rect (4), work rect (4), flags
		}
		static final int GWL_STYLE = -16;
		static final int WS_CAPTION = 0x00C00000, WS_THICKFRAME = 0x00040000, WS_SYSMENU = 0x00080000, WS_MINIMIZEBOX = 0x00020000, WS_MAXIMIZEBOX = 0x00010000;
		static final int SWP_NOSIZE = 0x0001, SWP_NOMOVE = 0x0002, SWP_NOACTIVATE = 0x0010, SWP_FRAMECHANGED = 0x0020, SWP_SHOWWINDOW = 0x0040, SWP_NOZORDER = 0x0004;
		static final int SW_HIDE = 0, SW_SHOWNOACTIVATE = 4, SW_SHOW = 5;

		static Object find(final String title) {
			final com.sun.jna.Pointer[] found = new com.sun.jna.Pointer[1];
			User32.I.EnumWindows(new User32.Each() {
				public boolean callback(com.sun.jna.Pointer hwnd, com.sun.jna.Pointer data) {
					char[] buf = new char[256];
					int n = User32.I.GetWindowTextW(hwnd, buf, buf.length);
					if (n > 0 && title.equals(new String(buf, 0, n)) && User32.I.IsWindowVisible(hwnd)) { found[0] = hwnd; return false; }
					return true;
				}
			}, null);
			return found[0];
		}
		/** Borderless; never owned by the station's window (that would share the two programs' input). */
		static void adopt(Object w) {
			com.sun.jna.Pointer hwnd = (com.sun.jna.Pointer) w;
			int style = User32.I.GetWindowLongW(hwnd, GWL_STYLE);
			User32.I.SetWindowLongW(hwnd, GWL_STYLE, style & ~(WS_CAPTION | WS_THICKFRAME | WS_SYSMENU | WS_MINIMIZEBOX | WS_MAXIMIZEBOX));
		}
		static java.awt.Rectangle bounds(Object w) {
			int[] r = new int[4];
			User32.I.GetWindowRect((com.sun.jna.Pointer) w, r);
			return new java.awt.Rectangle(r[0], r[1], r[2] - r[0], r[3] - r[1]);
		}
		/** The screen it's on, in the same (device) units as its bounds. */
		static java.awt.Rectangle screenOf(Object w) {
			com.sun.jna.Pointer m = User32.I.MonitorFromWindow((com.sun.jna.Pointer) w, 2); // the nearest
			if (m == null) return null;
			int[] info = new int[10];
			info[0] = 40;
			if (!User32.I.GetMonitorInfoW(m, info)) return null;
			return new java.awt.Rectangle(info[1], info[2], info[3] - info[1], info[4] - info[2]);
		}
		static void raise(Object w) {
			User32.I.SetWindowPos((com.sun.jna.Pointer) w, null, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE); // HWND_TOP
		}
		static boolean under(java.awt.Window station, Object w) {
			com.sun.jna.Pointer mine = com.sun.jna.Native.getComponentPointer(station);
			return mine != null && User32.I.SetWindowPos(mine, (com.sun.jna.Pointer) w, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE); // just after FTL's in the order: under it
		}
		static void focus(Object w) {
			com.sun.jna.Pointer h = (com.sun.jna.Pointer) w;
			User32.I.ShowWindow(h, SW_SHOW);
			User32.I.SetWindowPos(h, null, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE); // HWND_TOP
			User32.I.SetForegroundWindow(h);
		}
		static boolean alive(Object w) { return User32.I.IsWindow((com.sun.jna.Pointer) w); }
		static void place(Object w, Rectangle r) {
			double scale = 1;
			try { scale = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX(); } catch (Exception e) { }
			User32.I.SetWindowPos((com.sun.jna.Pointer) w, null, (int) Math.round(r.x * scale), (int) Math.round(r.y * scale), (int) Math.round(r.width * scale), (int) Math.round(r.height * scale),
					SWP_NOACTIVATE | SWP_FRAMECHANGED | SWP_SHOWWINDOW | SWP_NOZORDER);
		}
		static void show(Object w, boolean visible) { User32.I.ShowWindow((com.sun.jna.Pointer) w, visible ? SW_SHOWNOACTIVATE : SW_HIDE); }
	}
}
