package homeplanet.core;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Docking FTL on Linux (7.14): the window calls FtlDock makes on Windows, made with X11's own (Xlib, through JNA). X11
 * is the window system of most Linux desktops, the Steam Deck's Desktop Mode included. Under Wayland one program may not
 * move or stack another's window, so docking isn't offered there (usable()).
 *
 * The window manager (KDE's, on the Deck) owns the windows' frames and their order, so the asks go through it as the
 * standards for it say (EWMH and ICCCM): a frame left off through _MOTIF_WM_HINTS, a window activated through
 * _NET_ACTIVE_WINDOW, closed through WM_DELETE_WINDOW. X11 ends a program over any error it isn't told how to take
 * (a window that closed a moment before it was asked about, most often), so an error handler is in place before the
 * first call: an error there only means the call didn't take. A program has one handler, and Java's windows have theirs:
 * errors on Java's own connection are passed on to it.
 *
 * The attached way (FTL's window owned by the station's) is Windows' own: here FTL always docks as its own window.
 */
final class X11Windows implements FtlDock.WindowCalls {

	/** Docking can run: Linux, an X11 display, and not a Wayland session (where XWayland can't move another program's window). */
	static boolean usable() {
		if (!System.getProperty("os.name", "").equals("Linux")) return false;
		if (System.getenv("DISPLAY") == null) return false;
		if (System.getenv("WAYLAND_DISPLAY") != null) return false;
		return !"wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"));
	}

	interface Xlib extends Library {
		Xlib I = Native.load("X11", Xlib.class);
		interface ErrorHandler extends Callback { int apply(Pointer display, Pointer event); }
		ErrorHandler XSetErrorHandler(ErrorHandler handler);
		Pointer XOpenDisplay(String name);
		NativeLong XDefaultRootWindow(Pointer d);
		NativeLong XInternAtom(Pointer d, String name, boolean onlyIfExists);
		int XQueryTree(Pointer d, NativeLong w, NativeLongByReference root, NativeLongByReference parent, PointerByReference children, IntByReference n);
		int XFree(Pointer data);
		int XGetWindowProperty(Pointer d, NativeLong w, NativeLong property, NativeLong offset, NativeLong length, boolean delete, NativeLong type,
				NativeLongByReference actualType, IntByReference actualFormat, NativeLongByReference items, NativeLongByReference after, PointerByReference data);
		int XChangeProperty(Pointer d, NativeLong w, NativeLong property, NativeLong type, int format, int mode, Pointer data, int elements);
		int XGetWindowAttributes(Pointer d, NativeLong w, Attributes attributes);
		int XTranslateCoordinates(Pointer d, NativeLong from, NativeLong to, int x, int y, IntByReference toX, IntByReference toY, NativeLongByReference child);
		int XMoveResizeWindow(Pointer d, NativeLong w, int x, int y, int width, int height);
		int XMapWindow(Pointer d, NativeLong w);
		int XUnmapWindow(Pointer d, NativeLong w);
		int XRaiseWindow(Pointer d, NativeLong w);
		int XReconfigureWMWindow(Pointer d, NativeLong w, int screen, int mask, Changes changes);
		int XSendEvent(Pointer d, NativeLong w, boolean propagate, NativeLong mask, ClientMessage event);
		int XGetInputFocus(Pointer d, NativeLongByReference focus, IntByReference revert);
		int XDefaultScreen(Pointer d);
		int XSync(Pointer d, boolean discard);
		int XFlush(Pointer d);
	}
	interface Xext extends Library {
		Xext I = Native.load("Xext", Xext.class);
		void XShapeCombineRectangles(Pointer d, NativeLong w, int kind, int x, int y, Pointer rectangles, int n, int op, int ordering);
		void XShapeCombineMask(Pointer d, NativeLong w, int kind, int x, int y, NativeLong mask, int op);
	}
	/** XWindowAttributes: where a window is and whether it's on show. */
	public static final class Attributes extends Structure {
		public int x, y, width, height, border_width, depth;
		public Pointer visual;
		public NativeLong root;
		public int c_class, bit_gravity, win_gravity, backing_store;
		public NativeLong backing_planes, backing_pixel;
		public int save_under;
		public NativeLong colormap;
		public int map_installed, map_state;
		public NativeLong all_event_masks, your_event_mask;
		public short do_not_propagate_mask;
		public int override_redirect;
		public Pointer screen;
		@Override protected List<String> getFieldOrder() {
			return java.util.Arrays.asList("x", "y", "width", "height", "border_width", "depth", "visual", "root", "c_class", "bit_gravity", "win_gravity",
					"backing_store", "backing_planes", "backing_pixel", "save_under", "colormap", "map_installed", "map_state", "all_event_masks",
					"your_event_mask", "do_not_propagate_mask", "override_redirect", "screen");
		}
	}
	/** XWindowChanges: a window's place in the stacking order, asked of the window manager. */
	public static final class Changes extends Structure {
		public int x, y, width, height, border_width;
		public NativeLong sibling;
		public int stack_mode;
		@Override protected List<String> getFieldOrder() { return java.util.Arrays.asList("x", "y", "width", "height", "border_width", "sibling", "stack_mode"); }
	}
	/** XClientMessageEvent, padded to the size of any XEvent. */
	public static final class ClientMessage extends Structure {
		public int type;
		public NativeLong serial;
		public int send_event;
		public Pointer display;
		public NativeLong window, message_type;
		public int format;
		public NativeLong[] data = new NativeLong[5];
		public NativeLong[] pad = new NativeLong[12];
		@Override protected List<String> getFieldOrder() { return java.util.Arrays.asList("type", "serial", "send_event", "display", "window", "message_type", "format", "data", "pad"); }
	}
	private static final int IS_VIEWABLE = 2, CLIENT_MESSAGE = 33, PROP_MODE_REPLACE = 0, ICONIC = 3, BELOW = 1;
	private static final int CW_SIBLING = 1 << 5, CW_STACK_MODE = 1 << 6;
	private static final long SUBSTRUCTURE_REDIRECT_AND_NOTIFY = (1L << 20) | (1L << 19);
	private static final int SHAPE_BOUNDING = 0, SHAPE_SET = 0, UNSORTED = 0;

	/** Errors X11 reports on the station's connection: counted, never fatal (X11's own handler would end the station). */
	private static volatile int errors;
	private static Pointer mine; // the station's connection
	private static Xlib.ErrorHandler before; // Java's handler, for errors on its own connection
	private static final Xlib.ErrorHandler HANDLER = new Xlib.ErrorHandler() { // held here, so it's never collected
		public int apply(Pointer display, Pointer event) {
			if (display.equals(mine) || before == null) { errors++; return 0; }
			return before.apply(display, event);
		}
	};

	private final Pointer d;
	private final NativeLong root;

	/** The station's own connection to the display, its error handler in place first. */
	static X11Windows open() {
		java.awt.Toolkit.getDefaultToolkit(); // Java's windows set their handler first, so it's passed on, not set over this one later
		Pointer d = Xlib.I.XOpenDisplay(null);
		if (d == null) throw new IllegalStateException("the X11 display could not be opened");
		mine = d;
		before = Xlib.I.XSetErrorHandler(HANDLER);
		return new X11Windows(d);
	}
	private X11Windows(Pointer d) { this.d = d; this.root = Xlib.I.XDefaultRootWindow(d); }

	private static NativeLong id(Object w) { return (NativeLong) w; }
	private NativeLong atom(String name) { return Xlib.I.XInternAtom(d, name, false); }
	/** A Java window's own X11 window. */
	private static NativeLong id(java.awt.Window station) {
		long xid = Native.getWindowID(station);
		return xid == 0 ? null : new NativeLong(xid);
	}

	// ---- reading ----

	/** A window's name: _NET_WM_NAME (UTF-8), else WM_NAME. */
	private String name(NativeLong w) {
		byte[] b = property(w, atom("_NET_WM_NAME"), atom("UTF8_STRING"));
		if (b == null) b = property(w, atom("WM_NAME"), new NativeLong(0));
		return b == null ? null : new String(b, java.nio.charset.StandardCharsets.UTF_8);
	}
	/** A property's bytes (8-bit properties), or null if the window hasn't it. */
	private byte[] property(NativeLong w, NativeLong prop, NativeLong type) {
		NativeLongByReference actual = new NativeLongByReference(), items = new NativeLongByReference(), after = new NativeLongByReference();
		IntByReference format = new IntByReference();
		PointerByReference data = new PointerByReference();
		if (Xlib.I.XGetWindowProperty(d, w, prop, new NativeLong(0), new NativeLong(1024), false, type, actual, format, items, after, data) != 0) return null;
		Pointer p = data.getValue();
		try {
			if (p == null || format.getValue() != 8 || items.getValue().longValue() == 0) return null;
			return p.getByteArray(0, (int) items.getValue().longValue());
		} finally { if (p != null) Xlib.I.XFree(p); }
	}
	/** A 32-bit property's values (held as C longs), or null. */
	private long[] longs(NativeLong w, NativeLong prop, NativeLong type) {
		NativeLongByReference actual = new NativeLongByReference(), items = new NativeLongByReference(), after = new NativeLongByReference();
		IntByReference format = new IntByReference();
		PointerByReference data = new PointerByReference();
		if (Xlib.I.XGetWindowProperty(d, w, prop, new NativeLong(0), new NativeLong(64), false, type, actual, format, items, after, data) != 0) return null;
		Pointer p = data.getValue();
		try {
			if (p == null || format.getValue() != 32) return null;
			int n = (int) items.getValue().longValue();
			long[] out = new long[n];
			for (int i = 0; i < n; i++) out[i] = Native.LONG_SIZE == 8 ? p.getLong(i * 8L) : p.getInt(i * 4L) & 0xffffffffL;
			return out;
		} finally { if (p != null) Xlib.I.XFree(p); }
	}
	private List<NativeLong> children(NativeLong w) {
		NativeLongByReference r = new NativeLongByReference(), parent = new NativeLongByReference();
		PointerByReference kids = new PointerByReference();
		IntByReference n = new IntByReference();
		List<NativeLong> out = new ArrayList<NativeLong>();
		if (Xlib.I.XQueryTree(d, w, r, parent, kids, n) == 0) return out;
		Pointer p = kids.getValue();
		if (p == null) return out;
		for (int i = 0; i < n.getValue(); i++) out.add(new NativeLong(Native.LONG_SIZE == 8 ? p.getLong(i * 8L) : p.getInt(i * 4L) & 0xffffffffL)); // bottom to top
		Xlib.I.XFree(p);
		return out;
	}
	private NativeLong parent(NativeLong w) {
		NativeLongByReference r = new NativeLongByReference(), parent = new NativeLongByReference();
		PointerByReference kids = new PointerByReference();
		IntByReference n = new IntByReference();
		if (Xlib.I.XQueryTree(d, w, r, parent, kids, n) == 0) return null;
		if (kids.getValue() != null) Xlib.I.XFree(kids.getValue());
		return parent.getValue();
	}
	/** The window's top-level: itself, or the window manager's frame around it (a child of the root). */
	private NativeLong frame(NativeLong w) {
		NativeLong at = w;
		for (int i = 0; i < 8 && at != null; i++) {
			NativeLong up = parent(at);
			if (up == null || up.equals(root)) return at;
			at = up;
		}
		return w;
	}
	private Attributes attributes(NativeLong w) {
		Attributes a = new Attributes();
		return Xlib.I.XGetWindowAttributes(d, w, a) == 0 ? null : a;
	}

	// ---- the calls ----

	public synchronized Object find(String[] titles) {
		NativeLong found = find(root, titles, 0);
		Xlib.I.XSync(d, false);
		return found;
	}
	/** Down the tree from w: a window on show with one of the titles (FTL's sits inside the window manager's frame). */
	private NativeLong find(NativeLong w, String[] titles, int depth) {
		if (depth > 4) return null;
		for (NativeLong c : children(w)) {
			String n = name(c);
			if (n != null) for (String t : titles) if (t.equals(n)) {
				Attributes a = attributes(c);
				if (a != null && a.map_state == IS_VIEWABLE) return c;
			}
			NativeLong deeper = find(c, titles, depth + 1);
			if (deeper != null) return deeper;
		}
		return null;
	}
	/** Borderless: the window manager asked, through _MOTIF_WM_HINTS, to leave its frame off. */
	public synchronized void adopt(Object w) { decorations(id(w), false); }
	private void decorations(NativeLong w, boolean on) {
		Memory hints = new Memory(5L * Native.LONG_SIZE); // flags, functions, decorations, input mode, status
		hints.clear();
		setLong(hints, 0, 2); // MWM_HINTS_DECORATIONS
		setLong(hints, 2, on ? 1 : 0);
		NativeLong motif = atom("_MOTIF_WM_HINTS");
		Xlib.I.XChangeProperty(d, w, motif, motif, 32, PROP_MODE_REPLACE, hints, 5);
		Xlib.I.XFlush(d);
	}
	private static void setLong(Memory m, int i, long v) { if (Native.LONG_SIZE == 8) m.setLong(i * 8L, v); else m.setInt(i * 4L, (int) v); }
	public synchronized Rectangle bounds(Object w) {
		Attributes a = attributes(id(w));
		if (a == null) return new Rectangle();
		IntByReference x = new IntByReference(), y = new IntByReference();
		Xlib.I.XTranslateCoordinates(d, id(w), root, 0, 0, x, y, new NativeLongByReference());
		return new Rectangle(x.getValue(), y.getValue(), a.width, a.height);
	}
	/** The screen it's on (the one holding its middle), in the same units as its bounds. */
	public Rectangle screenOf(Object w) {
		Rectangle r = bounds(w);
		java.awt.Point mid = new java.awt.Point(r.x + r.width / 2, r.y + r.height / 2);
		Rectangle first = null;
		for (GraphicsDevice g : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			Rectangle s = g.getDefaultConfiguration().getBounds();
			if (first == null) first = s;
			if (s.contains(mid)) return s;
		}
		return first;
	}
	/** Over the station's window, without the keyboard: not when it's just over it already. */
	public synchronized void raise(Object w, java.awt.Window station) {
		NativeLong mine = station == null ? null : id(station);
		if (mine != null && justAbove(frame(id(w)), frame(mine))) return;
		Xlib.I.XRaiseWindow(d, id(w));
		Xlib.I.XFlush(d);
	}
	/** The top-level just under upper, on show, is lower. */
	private boolean justAbove(NativeLong upper, NativeLong lower) {
		List<NativeLong> order = children(root);
		int i = order.indexOf(upper);
		for (int j = i - 1; j >= 0; j--) {
			Attributes a = attributes(order.get(j));
			if (a == null || a.map_state != IS_VIEWABLE) continue;
			return order.get(j).equals(lower);
		}
		return false;
	}
	public synchronized boolean foreground(Object w) {
		long[] active = longs(root, atom("_NET_ACTIVE_WINDOW"), atom("WINDOW"));
		if (active != null && active.length > 0) return active[0] == id(w).longValue();
		NativeLongByReference focus = new NativeLongByReference();
		Xlib.I.XGetInputFocus(d, focus, new IntByReference());
		return id(w).equals(focus.getValue());
	}
	public String own(Object w, java.awt.Window station) {
		throw new UnsupportedOperationException("the attached way is Windows' own");
	}
	/** Its frame back, centred on its screen at its own size, shown and active. */
	public synchronized String release(Object w, boolean attached) {
		NativeLong h = id(w);
		if (!alive(w)) return "FTL's window is gone";
		decorations(h, true);
		Rectangle r = bounds(w), screen = screenOf(w);
		int x = screen == null ? r.x : screen.x + Math.max(0, (screen.width - r.width) / 2), y = screen == null ? r.y : screen.y + Math.max(0, (screen.height - r.height) / 2);
		Xlib.I.XMapWindow(d, h);
		Xlib.I.XMoveResizeWindow(d, h, x, y, r.width, r.height);
		activate(h);
		return "its frame back, " + r.width + "x" + r.height + " at " + x + "," + y;
	}
	/** The close request a window's own X sends: WM_DELETE_WINDOW. */
	public synchronized void close(Object w) {
		ClientMessage m = message(id(w), atom("WM_PROTOCOLS"), atom("WM_DELETE_WINDOW").longValue(), 0);
		Xlib.I.XSendEvent(d, id(w), false, new NativeLong(0), m);
		Xlib.I.XFlush(d);
	}
	private ClientMessage message(NativeLong w, NativeLong type, long a, long b) {
		ClientMessage m = new ClientMessage();
		m.type = CLIENT_MESSAGE;
		m.send_event = 1;
		m.display = d;
		m.window = w;
		m.message_type = type;
		m.format = 32;
		for (int i = 0; i < m.data.length; i++) m.data[i] = new NativeLong(0);
		for (int i = 0; i < m.pad.length; i++) m.pad[i] = new NativeLong(0);
		m.data[0] = new NativeLong(a);
		m.data[1] = new NativeLong(b);
		return m;
	}
	/** The window manager asked to make it the window in use (as a click on it would). */
	private void activate(NativeLong w) {
		ClientMessage m = message(w, atom("_NET_ACTIVE_WINDOW"), 2, 0); // 2: asked by the user's own tool, as a pager does
		Xlib.I.XSendEvent(d, root, false, new NativeLong(SUBSTRUCTURE_REDIRECT_AND_NOTIFY), m);
		Xlib.I.XRaiseWindow(d, w);
		Xlib.I.XFlush(d);
	}
	/** The station's window with a screen rectangle cut out of it (its shape, X11's Shape extension), or whole again (null). */
	public synchronized void cut(java.awt.Window station, Rectangle hole) {
		NativeLong mine = id(station);
		if (mine == null) return;
		if (hole == null) { Xext.I.XShapeCombineMask(d, mine, SHAPE_BOUNDING, 0, 0, new NativeLong(0), SHAPE_SET); Xlib.I.XFlush(d); return; }
		java.awt.Point at = station.getLocationOnScreen();
		java.awt.Insets in = station.getInsets(); // Java's window bounds take in the frame; its own X11 window starts inside it
		int w = station.getWidth() - in.left - in.right, h = station.getHeight() - in.top - in.bottom;
		int hx = hole.x - at.x - in.left, hy = hole.y - at.y - in.top, hw = hole.width, hh = hole.height;
		int[][] parts = { {0, 0, w, Math.max(0, hy)}, {0, hy + hh, w, Math.max(0, h - hy - hh)}, {0, hy, Math.max(0, hx), hh}, {hx + hw, hy, Math.max(0, w - hx - hw), hh} };
		Memory rects = new Memory(8L * parts.length); // XRectangle: x, y (short); width, height (unsigned short)
		for (int i = 0; i < parts.length; i++) {
			rects.setShort(i * 8L, (short) parts[i][0]); rects.setShort(i * 8L + 2, (short) parts[i][1]);
			rects.setShort(i * 8L + 4, (short) parts[i][2]); rects.setShort(i * 8L + 6, (short) parts[i][3]);
		}
		Xext.I.XShapeCombineRectangles(d, mine, SHAPE_BOUNDING, 0, 0, rects, parts.length, SHAPE_SET, UNSORTED);
		Xlib.I.XFlush(d);
	}
	public synchronized void top(java.awt.Window station) {
		NativeLong mine = id(station);
		if (mine != null) { Xlib.I.XRaiseWindow(d, mine); Xlib.I.XFlush(d); }
	}
	/**
	 * The station's window put just under FTL's, unless it's there already, or FTL is under another program's window that
	 * covers it, below the station (as on Windows: put under FTL, the station would sink under that window too).
	 */
	public synchronized int under(java.awt.Window station, Object w) {
		NativeLong mine = id(station);
		if (mine == null) return REFUSED;
		NativeLong ftl = frame(id(w)), me = frame(mine);
		if (justAbove(ftl, me)) return ALREADY;
		Rectangle hole = bounds(w);
		List<NativeLong> order = children(root); // bottom to top
		int f = order.indexOf(ftl), s = order.indexOf(me);
		if (f >= 0 && s > f) for (int j = f + 1; j < s; j++) { // between FTL and the station: a window on show over FTL
			NativeLong o = order.get(j);
			Attributes a = attributes(o);
			if (a != null && a.map_state == IS_VIEWABLE && a.override_redirect == 0 && new Rectangle(a.x, a.y, a.width, a.height).intersects(hole)) return BEHIND;
		}
		Changes c = new Changes();
		c.sibling = id(w);
		c.stack_mode = BELOW;
		int ok = Xlib.I.XReconfigureWMWindow(d, mine, Xlib.I.XDefaultScreen(d), CW_SIBLING | CW_STACK_MODE, c);
		Xlib.I.XFlush(d);
		return ok != 0 ? DONE : REFUSED;
	}
	public synchronized void bringBack(Object w) {
		Xlib.I.XMapWindow(d, id(w)); // back from minimized
		activate(id(w));
	}
	/** Minimized: its WM_STATE is IconicState. */
	public synchronized boolean minimized(Object w) {
		long[] state = longs(id(w), atom("WM_STATE"), atom("WM_STATE"));
		return state != null && state.length > 0 && state[0] == ICONIC;
	}
	public synchronized void focus(Object w) {
		Xlib.I.XMapWindow(d, id(w));
		activate(id(w));
	}
	/** Still there: X11 answers for it without an error. */
	public synchronized boolean alive(Object w) {
		Xlib.I.XSync(d, false);
		int before = errors;
		Attributes a = attributes(id(w));
		Xlib.I.XSync(d, false);
		return a != null && errors == before;
	}
	public synchronized void place(Object w, Rectangle r, boolean frame) {
		double scale = 1;
		try { scale = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX(); } catch (Exception e) { }
		Xlib.I.XMoveResizeWindow(d, id(w), (int) Math.round(r.x * scale), (int) Math.round(r.y * scale), (int) Math.round(r.width * scale), (int) Math.round(r.height * scale));
		Xlib.I.XFlush(d);
	}
	public synchronized void show(Object w, boolean visible) {
		if (visible) Xlib.I.XMapWindow(d, id(w));
		else Xlib.I.XUnmapWindow(d, id(w));
		Xlib.I.XFlush(d);
	}
}
