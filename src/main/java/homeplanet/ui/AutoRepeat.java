package homeplanet.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Makes a button act again while it's held down: once on the click as usual, then after a pause, faster and faster.
 * A plain click still acts once (the click's own action is skipped when the hold already acted).
 */
public final class AutoRepeat {
	private static final int FIRST_DELAY = 400, START_INTERVAL = 200, FASTEST = 50;

	private AutoRepeat() { }

	public static void attach(final AbstractButton button, final Runnable action) {
		final boolean[] repeated = {false};
		final Timer timer = new Timer(START_INTERVAL, null);
		timer.setInitialDelay(FIRST_DELAY);
		timer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!button.isEnabled() || !button.getModel().isPressed()) { timer.stop(); return; }
				repeated[0] = true;
				action.run();
				timer.setDelay(Math.max(FASTEST, (int) (timer.getDelay() * 0.85))); // quickens the longer it's held
			}
		});
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (repeated[0]) { repeated[0] = false; return; } // the hold did the work
				action.run();
			}
		});
		button.addMouseListener(new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) {
				if (!SwingUtilities.isLeftMouseButton(e) || !button.isEnabled()) return;
				repeated[0] = false;
				timer.setDelay(START_INTERVAL);
				timer.restart();
			}
			@Override public void mouseReleased(MouseEvent e) { timer.stop(); }
			@Override public void mouseExited(MouseEvent e) { timer.stop(); }
		});
	}
}
