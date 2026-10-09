package homeplanet.ui;

import java.awt.Component;
import java.awt.KeyEventPostProcessor;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;

import javax.swing.SwingUtilities;

/**
 * Esc as the close box (heromedel, 6.20): in any window but the station's own, an Esc nothing else used (an open list, a
 * menu, the layout editor's deselect, a message box's own) sends the window the same close request its X does, so it
 * closes, asks first, or stays, exactly as the X would. The station's own window keeps its Esc: at the Space Dock it asks
 * before quitting.
 */
public final class EscClose implements KeyEventPostProcessor {
	private EscClose() { }
	private static boolean installed;

	/** Once, as the station starts (before its first window). */
	public static synchronized void install() {
		if (installed) return;
		installed = true;
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventPostProcessor(new EscClose());
	}

	@Override public boolean postProcessKeyEvent(KeyEvent e) { return handle(e); }

	/** The key, after everything else had its chance: true if it closed (or asked to close) a window. */
	public static boolean handle(KeyEvent e) {
		if (e.getID() != KeyEvent.KEY_PRESSED || e.getKeyCode() != KeyEvent.VK_ESCAPE || e.isConsumed() || e.getModifiersEx() != 0) return false;
		Component c = e.getComponent();
		Window w = c instanceof Window ? (Window) c : c == null ? null : SwingUtilities.getWindowAncestor(c);
		if (w == null || w instanceof MainFrame || !w.isShowing()) return false;
		w.dispatchEvent(new WindowEvent(w, WindowEvent.WINDOW_CLOSING)); // the close box's own request: the window decides
		e.consume();
		return true;
	}
}
