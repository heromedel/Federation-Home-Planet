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
 * over a viewport on the Space Dock and kept there, owned by the station's window so it stays just above it and below
 * the station's own windows. FTL stays its own program: if anything here fails, it simply runs as a normal window.
 *
 * Its settings.ini is edited in place, one key only (fullscreen, to windowed), never copied over: the player's other
 * settings are theirs. The key's old value is kept in the cfg and put back when the option is turned off, unless the
 * player has changed it since.
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

	/** FTL's settings.ini, beside its saves. */
	static File settingsFile() { return HomePlanet.save_location == null ? null : new File(HomePlanet.save_location, "settings.ini"); }

	/** Before a docked launch: fullscreen to windowed (0), keeping the old value the first time. */
	public static void prepareSettings() throws IOException {
		File f = settingsFile();
		if (f == null) return;
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
	private static Rectangle where;

	/** A docked run is on: from the docked launch until FTL's window closes (or isn't found). */
	public static boolean active() { return active; }
	/** Starts a docked run (FTL being launched): the Space Dock lays out its viewport. */
	public static void begin() { active = true; window = null; shown = true; where = null; }
	/** Ends it: FTL closed, or its window never turned up. */
	public static void end() { active = false; window = null; where = null; }

	/** Looks for FTL's window (true once found and made borderless, owned by the station's window). Windows only. */
	public static boolean find(java.awt.Window station) {
		if (window != null) return true;
		if (!System.getProperty("os.name", "").startsWith("Windows")) return false;
		try {
			Object w = Win.find(TITLE);
			if (w == null) return false;
			Win.adopt(w, station);
			window = w;
			log.info("FTL docked: its window was found");
			if (where != null) place(where);
			return true;
		} catch (Throwable t) { // no JNA, a refused call: FTL just runs as a normal window
			log.debug("FTL docked: its window could not be docked: {}", t.toString());
			return false;
		}
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
		}
		/** SetWindowLongPtrW exists only in 64-bit user32 (in 32-bit it's SetWindowLongW). */
		interface User32x64 extends com.sun.jna.win32.StdCallLibrary {
			User32x64 I = com.sun.jna.Native.POINTER_SIZE == 8 ? com.sun.jna.Native.load("user32", User32x64.class) : null;
			com.sun.jna.Pointer SetWindowLongPtrW(com.sun.jna.Pointer hwnd, int index, com.sun.jna.Pointer value);
		}
		static final int GWL_STYLE = -16, GWLP_HWNDPARENT = -8;
		static final int WS_CAPTION = 0x00C00000, WS_THICKFRAME = 0x00040000, WS_SYSMENU = 0x00080000, WS_MINIMIZEBOX = 0x00020000, WS_MAXIMIZEBOX = 0x00010000;
		static final int SWP_NOACTIVATE = 0x0010, SWP_FRAMECHANGED = 0x0020, SWP_SHOWWINDOW = 0x0040, SWP_NOZORDER = 0x0004;
		static final int SW_HIDE = 0, SW_SHOWNOACTIVATE = 4;

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
		/** Borderless, and owned by the station's window: above it, below its dialogs, minimized with it. */
		static void adopt(Object w, java.awt.Window station) {
			com.sun.jna.Pointer hwnd = (com.sun.jna.Pointer) w;
			int style = User32.I.GetWindowLongW(hwnd, GWL_STYLE);
			User32.I.SetWindowLongW(hwnd, GWL_STYLE, style & ~(WS_CAPTION | WS_THICKFRAME | WS_SYSMENU | WS_MINIMIZEBOX | WS_MAXIMIZEBOX));
			com.sun.jna.Pointer owner = com.sun.jna.Native.getComponentPointer(station);
			if (User32x64.I != null) User32x64.I.SetWindowLongPtrW(hwnd, GWLP_HWNDPARENT, owner);
			else User32.I.SetWindowLongW(hwnd, GWLP_HWNDPARENT, (int) com.sun.jna.Pointer.nativeValue(owner));
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
