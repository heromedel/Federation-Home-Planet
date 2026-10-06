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

	public static final String CFG_ON = "ftl_docked", CFG_SIZE = "ftl_docked_size", CFG_WAS = "ftl_docked_fullscreen_was", CFG_ATTACHED = "ftl_docked_attached";
	/** How FTL docks (heromedel, 5.36): its own window kept over the station, or owned by the station's window (testing). */
	public static final String[] HOW = {"as its own window", "attached to the station (testing)"};
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
	/** FTL's window is to be owned by the station's (the attached way, 5.36), its input kept apart. */
	public static boolean attachedChosen() { return Boolean.parseBoolean(HomePlanet.config.getProperty(CFG_ATTACHED, "false")); }
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
	public static void begin() { begin(null); }
	/** The same, with the station's window to attach FTL's to if the attached way is chosen (5.36). */
	public static void begin(java.awt.Window stationWindow) {
		active = true; window = null; shown = true; aside = false; where = null;
		asked = null; placedAt = null; framed = false; cutAs = null; behind = false;
		station = stationWindow;
		attachedRun = stationWindow != null && attachedChosen();
		started = System.currentTimeMillis();
	}
	private static java.awt.Window station;
	private static boolean attachedRun;
	private static long started;
	/** The station's window was closed and opened again (a new window to Windows): attached again, if attached. */
	public static void stationReopened(java.awt.Window stationWindow) {
		station = stationWindow;
		cutAs = null; // a new window has no viewport cut out of it yet
		if (!attachedRun || window == null) return;
		try { log.info("FTL docked, attached again: {}", Win.own(window, station)); }
		catch (Throwable t) { log.info("FTL docked: could not attach it again ({})", t.toString()); }
	}
	/** This docked run has FTL's window owned by the station's: it stays over it, and popups over both, by themselves. */
	public static boolean attached() { return attachedRun && window != null; }
	/** Ends it: FTL closed, or its window never turned up. */
	public static void end() { cut(false); active = false; window = null; where = null; aside = false; asked = null; placedAt = null; framed = false; cutAs = null; behind = false; }
	/** The Space Dock shows the docked ships in FTL's place (heromedel, 5.32): FTL hidden, still running. */
	public static boolean aside() { return aside; }
	/**
	 * Remove from Dock (heromedel, 5.57): FTL's title bar and border back, the station no longer owning it, the hole in the
	 * station's window closed, and FTL out in the middle of its screen, still running. The docked view ends. False if
	 * FTL's window wasn't there to give back.
	 */
	public static boolean release() {
		Object w = window;
		boolean attached = attachedRun;
		cut(false);
		end();
		if (w == null || !supported()) return false;
		try { log.info("FTL undocked: {}", Win.release(w, attached)); return true; }
		catch (Throwable t) { log.warn("Could not give FTL's window back: {}", t.toString()); return false; }
	}
	/** Close (heromedel, 5.57): FTL is asked to close, as the X on its own window does; it closes as FTL does. False if there's no window to ask. */
	public static boolean close() {
		Object w = window;
		if (w == null || !supported()) return false;
		try { Win.close(w); log.info("FTL docked: asked to close"); return true; }
		catch (Throwable t) { log.warn("Could not ask FTL to close: {}", t.toString()); return false; }
	}
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
			log.info("FTL docked: its window was found, {} s after the launch", (System.currentTimeMillis() - started) / 1000);
			if (attachedRun) {
				try { log.info("FTL docked, attached: {}", Win.own(w, station)); }
				catch (Throwable t) { attachedRun = false; log.info("FTL docked: could not attach it to the station's window ({}); docked as its own window", t.toString()); }
			}
			if (where != null) place(where);
			return Found.DOCKED;
		} catch (Throwable t) { // no JNA, a refused call: FTL just runs as a normal window
			log.debug("FTL docked: its window could not be docked: {}", t.toString());
			return Found.NOT_YET;
		}
	}
	/**
	 * FTL's window lifted over the station's and any other program's, without taking the keyboard: only while the
	 * station is the window in use (brought forward, clicked), never over a program the player is in (heromedel, 5.43:
	 * after an Alt+Tab back to the station, FTL stayed under the program in between).
	 */
	public static void raise(java.awt.Window station) {
		if (window == null || !shown) return;
		try { Win.raise(window, station); } catch (Throwable t) { log.debug("FTL docked: could not lift its window: {}", t.toString()); }
	}
	/** FTL is the window in use (played, or Alt+Tabbed straight to). */
	public static boolean inFront() {
		try { return window != null && shown && Win.foreground(window); } catch (Throwable t) { return false; }
	}
	/**
	 * The station's own window put just under FTL's (5.34): Windows may refuse to lift another program's window over the
	 * one in use, but a program may always arrange its own. Logged, so a debug log shows whether Windows took it. Not
	 * when another program's window covers FTL (5.43): the station would sink under it too; the viewport is closed
	 * instead, showing Unpause, and a click there brings FTL back.
	 */
	public static void tuckUnder(java.awt.Window station) {
		if (window == null || !shown || station == null || !station.isDisplayable()) return;
		int r;
		try { r = Win.under(station, window); }
		catch (Throwable t) { log.debug("FTL docked: could not put the station's window under FTL's: {}", t.toString()); return; }
		if (r != Win.ALREADY) log.debug("FTL docked: the station's window put under FTL's: {}", r == Win.DONE ? "done" : r == Win.BEHIND ? "not, FTL is behind another program's window (Unpause shown)" : "refused by Windows");
		behind = r == Win.BEHIND;
		cut(true);
	}
	private static boolean behind; // FTL under another program's window: the viewport closed, Unpause shown in it
	/** The station's own window over FTL's (a popup is open, 5.39): FTL still shows through the viewport's hole. */
	public static void stationOnTop(java.awt.Window station) {
		if (window == null || !shown || station == null || !station.isDisplayable()) return;
		try { Win.top(station); } catch (Throwable t) { log.debug("FTL docked: could not bring the station's window over FTL's: {}", t.toString()); }
	}
	/** FTL to the front with the keyboard (the Unpause screen clicked, 5.33): the station has the keyboard to give. */
	public static void focus() {
		if (window == null || !shown) return;
		try { Win.focus(window); } catch (Throwable t) { log.debug("FTL docked: could not bring it to the front: {}", t.toString()); return; }
		behind = false;
		cut(true);
	}
	/** FTL's window has been found and docked. */
	public static boolean found() { return window != null; }
	/** FTL's window is still there. */
	public static boolean alive() {
		try { return window != null && Win.alive(window); } catch (Throwable t) { return false; }
	}
	/**
	 * Places FTL's window over the viewport (screen coordinates, in Java's units). Only when it isn't there already
	 * (heromedel, 5.42): every save FTL writes rebuilds the Space Dock, and placing it again each time (its frame redrawn,
	 * the station's window cut again) made the two windows flicker while playing.
	 */
	public static void place(Rectangle screen) {
		where = screen;
		if (window == null || !shown) return;
		if (!screen.equals(asked) || moved()) put(screen);
		cut(true);
	}
	private static Rectangle asked; // where FTL's window was last put, in Java's units
	private static Rectangle placedAt; // where Windows says it went (its own units), to notice it moved since
	private static boolean framed; // its frame redrawn once, after it was made borderless
	/** FTL's window put over the viewport: its frame redrawn only the first time, when it has just lost its border. */
	private static void put(Rectangle screen) {
		asked = new Rectangle(screen);
		placedAt = null;
		try { Win.place(window, screen, !framed); framed = true; placedAt = Win.bounds(window); }
		catch (Throwable t) { log.debug("FTL docked: could not place its window: {}", t.toString()); }
	}
	/** FTL's window isn't where it was put (FTL or Windows moved it); not known counts as not moved. */
	private static boolean moved() {
		if (placedAt == null) return false;
		try { return !placedAt.equals(Win.bounds(window)); } catch (Throwable t) { return false; }
	}
	/**
	 * The viewport cut out of the station's window while FTL sits in it (heromedel, 5.37): if FTL slips behind the
	 * station, it shows through, and clicks there reach it. Closed whenever FTL isn't shown there, so the desktop never
	 * shows through. Not in the attached way, where FTL stays over the station by itself. A cut the same as the one there
	 * already is skipped (5.42): Windows redraws the whole window for each.
	 */
	private static void cut(boolean open) {
		if (station == null || attachedRun) return;
		boolean want = open && window != null && shown && where != null && !behind;
		String as = cutKey(want);
		if (as != null && as.equals(cutAs)) return;
		cutAs = as;
		try { Win.cut(station, want ? where : null); }
		catch (Throwable t) { log.debug("FTL docked: could not {} the station's window: {}", want ? "cut the viewport out of" : "close the viewport in", t.toString()); }
	}
	private static String cutAs; // the cut the station's window has now (cutKey), null when not known
	/** The cut wanted, as the window sees it (the hole from its corner, its size and scale), or null when it isn't on screen. */
	private static String cutKey(boolean want) {
		if (!want) return "whole";
		try {
			java.awt.Point at = station.getLocationOnScreen();
			double scale = 1;
			try { scale = station.getGraphicsConfiguration().getDefaultTransform().getScaleX(); } catch (Exception e) { }
			return (where.x - at.x) + "," + (where.y - at.y) + " " + where.width + "x" + where.height + " in " + station.getWidth() + "x" + station.getHeight() + " at " + scale;
		} catch (Exception e) { return null; }
	}
	/** Shows it in its viewport (back at the Space Dock) or hides it (another screen); FTL keeps running either way. */
	public static void show(boolean visible) {
		if (shown == visible) return;
		shown = visible;
		behind = false; // hidden, or back: the next lift sees where it is
		if (window == null) return;
		try { Win.show(window, visible); }
		catch (Throwable t) { log.debug("FTL docked: could not {} its window: {}", visible ? "show" : "hide", t.toString()); }
		if (visible && where != null) put(where);
		cut(visible);
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
			int SetWindowRgn(com.sun.jna.Pointer hwnd, com.sun.jna.Pointer region, boolean redraw);
			int GetWindowThreadProcessId(com.sun.jna.Pointer hwnd, com.sun.jna.Pointer pid);
			boolean AttachThreadInput(int attach, int to, boolean on);
			boolean GetWindowRect(com.sun.jna.Pointer hwnd, int[] rect); // left, top, right, bottom
			com.sun.jna.Pointer GetWindow(com.sun.jna.Pointer hwnd, int cmd);
			com.sun.jna.Pointer GetForegroundWindow();
			com.sun.jna.Pointer MonitorFromWindow(com.sun.jna.Pointer hwnd, int flags);
			boolean PostMessageW(com.sun.jna.Pointer hwnd, int msg, com.sun.jna.Pointer wParam, com.sun.jna.Pointer lParam);
			boolean AdjustWindowRect(int[] rect, int style, boolean menu); // left, top, right, bottom
			boolean GetMonitorInfoW(com.sun.jna.Pointer monitor, int[] info); // cbSize, monitor rect (4), work rect (4), flags
		}
		static final int GWL_STYLE = -16, GWLP_HWNDPARENT = -8, GW_HWNDNEXT = 2;
		/** SetWindowLongPtrW exists only in 64-bit user32 (in 32-bit, SetWindowLongW does the same). */
		interface User32x64 extends com.sun.jna.win32.StdCallLibrary {
			User32x64 I = com.sun.jna.Native.POINTER_SIZE == 8 ? com.sun.jna.Native.load("user32", User32x64.class) : null;
			com.sun.jna.Pointer SetWindowLongPtrW(com.sun.jna.Pointer hwnd, int index, com.sun.jna.Pointer value);
		}
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
		/**
		 * Made topmost and at once not (5.43): that lifts another program's window over all the others without activating
		 * it, where HWND_TOP is refused. Not when it's just over the station already.
		 */
		static void raise(Object w, java.awt.Window station) {
			com.sun.jna.Pointer ftl = (com.sun.jna.Pointer) w, mine = station == null ? null : com.sun.jna.Native.getComponentPointer(station);
			if (mine != null && mine.equals(below(ftl))) return;
			User32.I.SetWindowPos(ftl, new com.sun.jna.Pointer(-1), 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE); // HWND_TOPMOST
			User32.I.SetWindowPos(ftl, new com.sun.jna.Pointer(-2), 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE); // HWND_NOTOPMOST: the top of the others
		}
		static boolean foreground(Object w) { return w.equals(User32.I.GetForegroundWindow()); }
		/**
		 * FTL's window owned by the station's (it stays over it; the station's popups come over both), then the two
		 * programs' input taken apart again: owning links their input (one cursor, one queue), which hid the pointer
		 * and slowed FTL's loading in 5.29. What Windows did is returned, for the log.
		 */
		static String own(Object w, java.awt.Window station) {
			com.sun.jna.Pointer hwnd = (com.sun.jna.Pointer) w, owner = com.sun.jna.Native.getComponentPointer(station);
			if (owner == null) return "the station's window has no handle yet: not attached";
			if (User32x64.I != null) User32x64.I.SetWindowLongPtrW(hwnd, GWLP_HWNDPARENT, owner);
			else User32.I.SetWindowLongW(hwnd, GWLP_HWNDPARENT, (int) com.sun.jna.Pointer.nativeValue(owner));
			int ftlThread = User32.I.GetWindowThreadProcessId(hwnd, null), stationThread = User32.I.GetWindowThreadProcessId(owner, null);
			boolean apart = User32.I.AttachThreadInput(ftlThread, stationThread, false);
			return "owned by the station's window; input kept apart: " + (apart ? "yes" : "no (Windows refused, or they weren't linked)");
		}
		/** FTL's window as it was before docking: its frame back, owned by nobody, shown, centred on its screen at its own size. */
		static String release(Object w, boolean attached) {
			com.sun.jna.Pointer hwnd = (com.sun.jna.Pointer) w;
			if (!User32.I.IsWindow(hwnd)) return "FTL's window is gone";
			if (attached) {
				if (User32x64.I != null) User32x64.I.SetWindowLongPtrW(hwnd, GWLP_HWNDPARENT, null);
				else User32.I.SetWindowLongW(hwnd, GWLP_HWNDPARENT, 0);
			}
			java.awt.Rectangle inside = bounds(w); // borderless: the window is all game
			int style = User32.I.GetWindowLongW(hwnd, GWL_STYLE) | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX;
			User32.I.SetWindowLongW(hwnd, GWL_STYLE, style);
			int[] r = {0, 0, inside.width, inside.height};
			User32.I.AdjustWindowRect(r, style, false); // the frame around the same game area
			int fw = r[2] - r[0], fh = r[3] - r[1];
			java.awt.Rectangle screen = screenOf(w);
			int x = screen == null ? inside.x : screen.x + Math.max(0, (screen.width - fw) / 2), y = screen == null ? inside.y : screen.y + Math.max(0, (screen.height - fh) / 2);
			User32.I.SetWindowPos(hwnd, null, x, y, fw, fh, SWP_NOZORDER | SWP_FRAMECHANGED | SWP_SHOWWINDOW);
			User32.I.ShowWindow(hwnd, SW_SHOW);
			User32.I.SetForegroundWindow(hwnd);
			return "its frame back, " + fw + "x" + fh + " at " + x + "," + y + (attached ? ", no longer owned" : "");
		}
		static final int WM_CLOSE = 0x0010;
		/** The close request a window's own X sends. */
		static void close(Object w) { User32.I.PostMessageW((com.sun.jna.Pointer) w, WM_CLOSE, null, null); }
		interface Gdi32 extends com.sun.jna.win32.StdCallLibrary {
			Gdi32 I = com.sun.jna.Native.load("gdi32", Gdi32.class);
			com.sun.jna.Pointer CreateRectRgn(int left, int top, int right, int bottom);
			int CombineRgn(com.sun.jna.Pointer dest, com.sun.jna.Pointer a, com.sun.jna.Pointer b, int mode);
			boolean DeleteObject(com.sun.jna.Pointer object);
		}
		static final int RGN_DIFF = 4;
		/** The station's window with a screen rectangle cut out of it, or whole again (null). */
		static void cut(java.awt.Window station, Rectangle hole) {
			com.sun.jna.Pointer hwnd = com.sun.jna.Native.getComponentPointer(station);
			if (hwnd == null) return;
			if (hole == null) { User32.I.SetWindowRgn(hwnd, null, true); return; }
			double scale = 1;
			try { scale = station.getGraphicsConfiguration().getDefaultTransform().getScaleX(); } catch (Exception e) { }
			java.awt.Point at = station.getLocationOnScreen();
			com.sun.jna.Pointer all = Gdi32.I.CreateRectRgn(0, 0, (int) Math.round(station.getWidth() * scale), (int) Math.round(station.getHeight() * scale));
			com.sun.jna.Pointer gap = Gdi32.I.CreateRectRgn((int) Math.round((hole.x - at.x) * scale), (int) Math.round((hole.y - at.y) * scale),
					(int) Math.round((hole.x + hole.width - at.x) * scale), (int) Math.round((hole.y + hole.height - at.y) * scale));
			Gdi32.I.CombineRgn(all, all, gap, RGN_DIFF);
			Gdi32.I.DeleteObject(gap);
			User32.I.SetWindowRgn(hwnd, all, true); // Windows keeps the region from here on
		}
		static void top(java.awt.Window station) {
			com.sun.jna.Pointer mine = com.sun.jna.Native.getComponentPointer(station);
			if (mine != null) User32.I.SetWindowPos(mine, null, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE); // HWND_TOP: its popups come with it
		}
		/** The first window on show under this one in the order (hidden ones, like a program's input window, skipped). */
		static com.sun.jna.Pointer below(com.sun.jna.Pointer w) {
			com.sun.jna.Pointer h = w;
			for (int i = 0; i < 64; i++) {
				h = User32.I.GetWindow(h, GW_HWNDNEXT);
				if (h == null || User32.I.IsWindowVisible(h)) return h;
			}
			return null;
		}
		static final int REFUSED = 0, DONE = 1, ALREADY = 2, BEHIND = 3;
		/**
		 * The station's window put just under FTL's, unless it's there already (5.42), or FTL is under another program's
		 * window that covers it, below the station (5.43: put under FTL, the station would sink under that window too).
		 */
		static int under(java.awt.Window station, Object w) {
			final com.sun.jna.Pointer mine = com.sun.jna.Native.getComponentPointer(station), ftl = (com.sun.jna.Pointer) w;
			if (mine == null) return REFUSED;
			if (mine.equals(below(ftl))) return ALREADY;
			final java.awt.Rectangle hole = bounds(ftl);
			final int me = User32.I.GetWindowThreadProcessId(mine, null);
			final boolean[] seen = new boolean[3]; // the station, then a window covering FTL, then FTL
			User32.I.EnumWindows(new User32.Each() { // top to bottom
				public boolean callback(com.sun.jna.Pointer h, com.sun.jna.Pointer data) {
					if (h.equals(ftl)) { seen[2] = seen[0] && seen[1]; return false; }
					if (h.equals(mine)) seen[0] = true;
					else if (seen[0] && !seen[1] && User32.I.IsWindowVisible(h) && User32.I.GetWindowThreadProcessId(h, null) != me && !cloaked(h) && bounds(h).intersects(hole)) seen[1] = true;
					return true;
				}
			}, null);
			if (seen[2]) return BEHIND;
			return User32.I.SetWindowPos(mine, ftl, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE) ? DONE : REFUSED; // just after FTL's in the order: under it
		}
		interface Dwm extends com.sun.jna.win32.StdCallLibrary {
			Dwm I = com.sun.jna.Native.load("dwmapi", Dwm.class);
			int DwmGetWindowAttribute(com.sun.jna.Pointer hwnd, int attribute, int[] value, int size);
		}
		/** A window Windows keeps but doesn't show (another desktop's, a suspended app's): it covers nothing. */
		static boolean cloaked(com.sun.jna.Pointer h) {
			try { int[] c = new int[1]; return Dwm.I.DwmGetWindowAttribute(h, 14, c, 4) == 0 && c[0] != 0; } // DWMWA_CLOAKED
			catch (Throwable t) { return false; }
		}
		static void focus(Object w) {
			com.sun.jna.Pointer h = (com.sun.jna.Pointer) w;
			User32.I.ShowWindow(h, SW_SHOW);
			User32.I.SetWindowPos(h, null, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE); // HWND_TOP
			User32.I.SetForegroundWindow(h);
		}
		static boolean alive(Object w) { return User32.I.IsWindow((com.sun.jna.Pointer) w); }
		/** Its frame redrawn too (frame) only just after it lost its border: that redraws the whole window. */
		static void place(Object w, Rectangle r, boolean frame) {
			double scale = 1;
			try { scale = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX(); } catch (Exception e) { }
			User32.I.SetWindowPos((com.sun.jna.Pointer) w, null, (int) Math.round(r.x * scale), (int) Math.round(r.y * scale), (int) Math.round(r.width * scale), (int) Math.round(r.height * scale),
					SWP_NOACTIVATE | (frame ? SWP_FRAMECHANGED : 0) | SWP_SHOWWINDOW | SWP_NOZORDER);
		}
		static void show(Object w, boolean visible) { User32.I.ShowWindow((com.sun.jna.Pointer) w, visible ? SW_SHOWNOACTIVATE : SW_HIDE); }
	}
}
