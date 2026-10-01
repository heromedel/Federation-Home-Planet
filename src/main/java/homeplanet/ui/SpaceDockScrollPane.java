package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.Random;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;


/**
 * The Space Dock's backdrop: the station art, scaled to fill the window without stretching, and slid left so the
 * station stands clear of the button column. The Space Dock itself sits on top (its ship list scrolls on its own).
 * The art is put together from the player's FTL data ({@link StationBackdrop}); if it can't be, the Space Dock is plain space.
 */
public class SpaceDockScrollPane extends JPanel {
	/** The layout's measures when there's no picture (FTL's couldn't be read): the old picture's size, station and rim. */
	static final int ART_W = 1480, ART_H = 794, STATION_X = 1005, SAUCER_LEFT = 789;
	/** How far left of the window's right edge the station's centre stands. */
	static final int STATION_FROM_RIGHT = 497;
	/** On Refresh, the ships leaving the station change about one time in this many (and always at startup). */
	static final int REROLL_ODDS = 15;

	private static StationBackdrop built = null;
	private static final Random rng = new Random();

	public SpaceDockScrollPane(final MainFrame frame, JPanel panel) {
		setLayout(new BorderLayout(0, 0));
		setBackground(Color.black);
		add(panel, BorderLayout.CENTER);
		built = StationBackdrop.make(frame, rng);
		CargoBayUI.rerollHold(frame);
		// Ctrl+Shift+B: a new roll of ships, and of the Cargo Hold's contents, to look at a few without restarting
		// (on the window itself, so it works in the Cargo Bay too, where this panel is hidden)
		frame.getRootPane().getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "reroll");
		frame.getRootPane().getActionMap().put("reroll", new AbstractAction() {
			public void actionPerformed(ActionEvent e) { reroll(frame, true); CargoBayUI.rerollHold(frame); frame.repaint(); }
		});
	}

	/** A new roll of the ships leaving the station: always if asked, otherwise one time in {@link #REROLL_ODDS}. */
	static void reroll(MainFrame frame, boolean always) {
		if (!always && rng.nextInt(REROLL_ODDS) != 0) return;
		StationBackdrop b = StationBackdrop.make(frame, rng);
		if (b != null) built = b;
		frame.repaint();
	}

	private static int artW() { return built != null ? StationBackdrop.W : ART_W; }
	private static int artH() { return built != null ? StationBackdrop.H : ART_H; }
	private static int stationX() { return built != null ? StationBackdrop.STATION_X : STATION_X; }
	/** The station's left rim in the art (the boarded ship's panel keeps left of it). */
	static int saucerLeft() { return built != null ? built.saucerLeft : SAUCER_LEFT; }

	/** The backdrop's scale for a window of this size. */
	static double scale(int w, int h) {
		return Math.max(h / (double) artH(), w / (double) artW());
	}
	/** Where the backdrop's left edge is drawn (0 or less). */
	static int offsetX(int w, int h) {
		double s = scale(w, h);
		long x = Math.round(w - STATION_FROM_RIGHT - stationX() * s);
		x = Math.max(x, (long) Math.ceil(w - artW() * s)); // never leave a bare strip on the right
		return (int) Math.min(0, x);
	}

	@Override
	public void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0;
		g.setColor(Color.black);
		g.fillRect(0, 0, getWidth(), getHeight());
		if (built == null) return; // FTL's pictures couldn't be read: plain space
		BufferedImage art = built.image;
		double s = scale(getWidth(), getHeight());
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(art, offsetX(getWidth(), getHeight()), 0, (int) Math.round(artW() * s), (int) Math.round(artH() * s), null);
	}
}
