package homeplanet.ui;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;

import javax.swing.JComponent;
import javax.swing.JScrollPane;

/** Keeps a tall window on the screen: its contents scroll when they're taller than the screen has room for. */
final class ScreenFit {
	private ScreenFit() { }

	/**
	 * The contents as they are when they fit, or in a scroll pane as tall as the screen allows; {@code around} is the
	 * height the window adds (its title bar, its buttons).
	 */
	static JComponent wrap(JComponent contents, int around) {
		Dimension d = contents.getPreferredSize();
		int room;
		try {
			room = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height - around; // the screen, less the taskbar
		} catch (Exception e) {
			return contents; // no screen to measure (headless)
		}
		if (d.height <= room) return contents;
		JScrollPane sp = new JScrollPane(contents, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sp.setBorder(null);
		sp.getVerticalScrollBar().setUnitIncrement(16);
		sp.setPreferredSize(new Dimension(d.width + sp.getVerticalScrollBar().getPreferredSize().width + 2, Math.max(200, room)));
		return sp;
	}
}
