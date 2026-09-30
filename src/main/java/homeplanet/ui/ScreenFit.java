package homeplanet.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;

import javax.swing.JComponent;
import javax.swing.JScrollPane;

/** Keeps a tall window on the screen it opens on: its contents scroll when they're taller than that screen has room for. */
final class ScreenFit {
	private ScreenFit() { }

	/** The usable part of the screen the component is on (or the main one): the screen less its taskbar. Null without a screen. */
	static Rectangle usable(Component on) {
		try {
			GraphicsConfiguration gc = on != null && on.getGraphicsConfiguration() != null ? on.getGraphicsConfiguration()
					: GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
			Rectangle r = new Rectangle(gc.getBounds());
			Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
			r.x += in.left; r.y += in.top; r.width -= in.left + in.right; r.height -= in.top + in.bottom;
			return r;
		} catch (Exception e) {
			return null; // headless
		}
	}

	/**
	 * The contents as they are when they fit, or in a scroll pane as tall as the screen allows; {@code around} is the
	 * height the window adds (its title bar, its buttons), {@code on} the window it opens over.
	 */
	static JComponent wrap(JComponent contents, int around, Component on) {
		Rectangle u = usable(on);
		if (u == null) return contents;
		Dimension d = contents.getPreferredSize();
		int room = u.height - around;
		if (d.height <= room) return contents;
		JScrollPane sp = new JScrollPane(contents, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sp.setBorder(null);
		sp.getVerticalScrollBar().setUnitIncrement(16);
		sp.setPreferredSize(new Dimension(d.width + sp.getVerticalScrollBar().getPreferredSize().width + 2, Math.max(200, room)));
		return sp;
	}

	/** Moves a window, once placed, so none of it is off its screen or under the taskbar. */
	static void keepOnScreen(Window w) {
		Rectangle u = usable(w);
		if (u == null) return;
		Rectangle b = w.getBounds();
		b.height = Math.min(b.height, u.height);
		b.width = Math.min(b.width, u.width);
		b.x = Math.max(u.x, Math.min(b.x, u.x + u.width - b.width));
		b.y = Math.max(u.y, Math.min(b.y, u.y + u.height - b.height));
		w.setBounds(b);
	}
}
