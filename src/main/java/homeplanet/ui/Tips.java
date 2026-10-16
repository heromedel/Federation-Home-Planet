package homeplanet.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;

import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

/**
 * Every tooltip in the station (heromedel, 6.57): up after a short wait, there as long as the mouse is on the control,
 * gone the moment it leaves; never under the mouse, and never over a button-sized control (above it, or below it when
 * there's no room above), always on the screen; a little see-through. A component that places its own tip (the Refit's
 * power panel) keeps its place, moved only off the mouse.
 */
public final class Tips {
	/** The wait before a tip comes up (heromedel: a quarter to half a second). */
	static final int DELAY = 400;
	/** How solid a tip is: 1 is solid (heromedel: maybe 25% see-through). */
	static float opacity = 0.85f;
	/** A control this tall or less is button-sized: its tip never covers it. */
	static final int SMALL = 64;
	/** The gap between a tip and its control or the mouse. */
	static final int GAP = 4;

	private Tips() { }

	/** Once, at startup (and by the tests). */
	public static void install() {
		ToolTipManager m = ToolTipManager.sharedInstance();
		m.setInitialDelay(DELAY);
		m.setDismissDelay(Integer.MAX_VALUE); // Swing's own took a tip away after four seconds, the mouse still on the control
		m.setReshowDelay(300); // moving along a row of buttons, the next tip comes at once
		if (!(PopupFactory.getSharedInstance() instanceof Factory)) PopupFactory.setSharedInstance(new Factory());
	}

	/** Swing's popups, with a tooltip moved to its place and made see-through; every other popup as Swing makes it. */
	static final class Factory extends PopupFactory {
		@Override public Popup getPopup(Component owner, Component contents, int x, int y) throws IllegalArgumentException {
			if (!(contents instanceof JToolTip) || owner == null || !owner.isShowing()) return super.getPopup(owner, contents, x, y);
			Point at = place(owner, contents.getPreferredSize(), new Point(x, y), mouse());
			return new SeeThrough(owner, contents, at.x, at.y); // its own window, inside the station's window as outside it, so it can be see-through anywhere
		}
	}

	/** Where the mouse is on the screen, or null if it can't be told. */
	static Point mouse() {
		try {
			PointerInfo p = MouseInfo.getPointerInfo();
			return p == null ? null : p.getLocation();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * Where a tip of this size goes, on the screen: Swing's place (by the mouse, or the component's own), moved above a
	 * button-sized control (below when there's no room above), then off the mouse, then onto the screen.
	 */
	static Point place(Component owner, Dimension size, Point at, Point mouse) {
		Rectangle screen = screenOf(owner, at);
		Rectangle tip = new Rectangle(at.x, at.y, size.width, size.height);
		Point o = owner.getLocationOnScreen();
		Rectangle ctl = new Rectangle(o.x, o.y, owner.getWidth(), owner.getHeight());
		if (!placesOwn(owner) && ctl.height <= SMALL) {
			tip.y = ctl.y - GAP - size.height;
			if (tip.y < screen.y) tip.y = ctl.y + ctl.height + GAP;
		}
		if (covers(tip, mouse)) {
			tip.y = mouse.y - 2 * GAP - size.height;
			if (tip.y < screen.y) tip.y = mouse.y + 20; // Swing's own distance below the mouse
		}
		onScreen(tip, screen);
		if (covers(tip, mouse)) { // too tall for above or below: beside the mouse instead
			tip.x = mouse.x + 16;
			if (tip.x + tip.width > screen.x + screen.width) tip.x = mouse.x - 2 * GAP - tip.width;
			onScreen(tip, screen);
		}
		return tip.getLocation();
	}
	private static boolean covers(Rectangle tip, Point mouse) {
		return mouse != null && new Rectangle(tip.x - GAP, tip.y - GAP, tip.width + 2 * GAP, tip.height + 2 * GAP).contains(mouse);
	}
	private static void onScreen(Rectangle tip, Rectangle screen) {
		tip.x = Math.max(screen.x, Math.min(tip.x, screen.x + screen.width - tip.width));
		tip.y = Math.max(screen.y, Math.min(tip.y, screen.y + screen.height - tip.height));
	}
	/** The screen the tip is on, less the taskbar. */
	private static Rectangle screenOf(Component owner, Point at) {
		GraphicsConfiguration gc = owner.getGraphicsConfiguration();
		for (GraphicsDevice d : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
			if (d.getDefaultConfiguration().getBounds().contains(at)) { gc = d.getDefaultConfiguration(); break; }
		if (gc == null) return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
		Rectangle r = gc.getBounds();
		Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
		return new Rectangle(r.x + in.left, r.y + in.top, r.width - in.left - in.right, r.height - in.top - in.bottom);
	}
	/** True if the component says where its tips go (it knows its own layout: the power panel's hover beside the mouse). */
	private static boolean placesOwn(Component c) {
		if (!(c instanceof JComponent)) return false;
		try { return c.getClass().getMethod("getToolTipLocation", java.awt.event.MouseEvent.class).getDeclaringClass() != JComponent.class; }
		catch (NoSuchMethodException e) { return false; }
	}

	/** A tip in a window of its own (Swing's own heavyweight popup, made new for each tip and thrown away after), see-through where the screen allows it. */
	static final class SeeThrough extends Popup {
		private final Component contents;
		SeeThrough(Component owner, Component contents, int x, int y) { super(owner, contents, x, y); this.contents = contents; }
		@Override public void show() {
			Window w = SwingUtilities.getWindowAncestor(contents);
			if (w != null && opacity < 1f && seeThrough(w)) {
				try { w.setOpacity(opacity); } catch (RuntimeException e) { } // not on this screen: solid, as before
			}
			super.show();
		}
		private static boolean seeThrough(Window w) {
			GraphicsConfiguration gc = w.getGraphicsConfiguration();
			return gc != null && gc.getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
		}
	}
}
