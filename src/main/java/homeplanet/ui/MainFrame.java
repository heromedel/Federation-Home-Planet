package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import net.blerf.ftl.parser.DataManager;

import homeplanet.core.HomePlanet;
import homeplanet.resource.ResourceClass;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The main window: the Space Dock and the Cargo Bay, two screens that swap in place. */
public class MainFrame extends JFrame {
	private static final Logger log = LoggerFactory.getLogger(MainFrame.class);

	private final JPanel tasksPane;
	private final java.awt.CardLayout screens = new java.awt.CardLayout();
	public final SpaceDockUI spaceDock;
	public final CargoBayUI cargoBay;
	/** Pictures read from the game data, by path, and the same pictures scaled for a report. */
	private final HashMap<String, BufferedImage> imageCache = new HashMap<String, BufferedImage>();
	private final HashMap<String, BufferedImage> scaledCache = new HashMap<String, BufferedImage>();

	/**
	 * The game mode changed: every window of the station's closes (Settings, reports, the switch itself) and the Space
	 * Dock shows the new mode's fleet, so nothing of the old one is left on screen. False if the station's window isn't up.
	 */
	public static boolean modeSwitched() {
		for (java.awt.Frame fr : java.awt.Frame.getFrames()) {
			if (!(fr instanceof MainFrame) || !fr.isDisplayable()) continue;
			final MainFrame f = (MainFrame) fr;
			for (java.awt.Window w : f.getOwnedWindows()) if (w.isShowing()) w.dispose();
			f.showSpaceDock();
			return true;
		}
		return false;
	}
	/** Asks first where leaving would lose something (Long Range Comm., the Cargo Bay's unsaved changes): true to go on. */
	public boolean mayClose(String doing) {
		if (atComm && !comm.confirmLeave(doing)) return false;
		if (!atSpaceDock && !atMuseum && !atComm && !cargoBay.confirmLeave(doing)) return false;
		return true;
	}
	/** Closes the station (after {@link #mayClose}): the window remembered, then the program ends. */
	public void closeNow() {
		rememberWindow(); // its size, position and maximized state
		System.exit(0);
	}

	public MainFrame(String appName, String appVersion) {
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // closing asks first when the Cargo Bay has unsaved changes
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				if (!mayClose("close The Home Planet Station interface")) return;
				closeNow();
			}
			@Override
			public void windowActivated(java.awt.event.WindowEvent e) {
				// back from another program (FTL, most likely): the Space Dock takes stock, as Refresh does. Not when one of
				// the station's own windows closes, and not in the Cargo Bay (unsaved trades)
				if (!atSpaceDock) return;
				if (homeplanet.core.GameGuard.isFtlRunning() && !homeplanet.vault.Vault.get().continueFile().exists()) return; // FTL is saving or still on its game-over screen: judged once it's closed
				boolean gone = homeplanet.core.SaveWatcher.takeGone(); // FTL ended a run meanwhile
				if (gone) spaceDock.refresh(); // her save is gone: the fleet is read again, so she leaves the Space Dock (lost in action)
				else if (e.getOppositeWindow() == null) spaceDock.init();
			}
		});
		setTitle("The Home Planet Station  -  " + appName + " " + appVersion + (HomePlanet.secondStation ? "  (second station: " + HomePlanet.propFile.getParent() + ")" : ""));
		// the station's icon, in every size it's drawn at, so Windows picks a sharp one for the title bar and the taskbar
		java.util.List<Image> icons = new java.util.ArrayList<Image>();
		for (int size : new int[] {16, 32, 48, 64, 128, 256}) {
			java.net.URL u = (new ResourceClass()).getClass().getResource("icon/StationIcon" + size + ".png");
			if (u != null) icons.add(new ImageIcon(u).getImage());
		}
		setIconImages(icons);
		tasksPane = new JPanel(screens);
		JPanel contentPane = new JPanel();
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);
		contentPane.add(tasksPane, BorderLayout.CENTER);
		spaceDock = new SpaceDockUI(this);
		// a lost ship is judged when the player comes back to the station (windowActivated above), never while FTL is
		// saving: FTL rewrites continue.sav by deleting it first, and a look in that instant would lose her
		tasksPane.add(new SpaceDockScrollPane(this, spaceDock), "dock");
		cargoBay = new CargoBayUI(this);
		JScrollPane cargoBayPane = new JScrollPane(cargoBay);
		cargoBayPane.setBorder(javax.swing.BorderFactory.createEmptyBorder()); // the border alone could tip a just-fitting window into scroll bars
		tasksPane.add(cargoBayPane, "cargo");
		museum = new MuseumUI(this);
		tasksPane.add(museum, "museum");
		comm = new LongRangeCommUI(this);
		JScrollPane commPane = new JScrollPane(comm);
		commPane.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		tasksPane.add(commPane, "comm");
		// big enough for the Cargo Bay without scroll bars (never bigger than the screen); a remembered size wins
		java.awt.Dimension want = cargoBay.getPreferredSize();
		java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		setSize(Math.min(screen.width, Math.max(900, want.width + 20)), Math.min(screen.height, Math.max(720, want.height + 50)));
		setLocationRelativeTo(null);
		restoreWindow();
		// ~ opens the console (heromedel, 5.22), on any screen of this window, never while typing in a box (a ship's name)
		java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new java.awt.KeyEventDispatcher() {
			public boolean dispatchKeyEvent(java.awt.event.KeyEvent e) {
				if (e.getID() != java.awt.event.KeyEvent.KEY_TYPED || (e.getKeyChar() != '~' && e.getKeyChar() != '`')) return false;
				java.awt.Component c = e.getComponent();
				if (c instanceof javax.swing.text.JTextComponent) return false;
				java.awt.Window w = c instanceof java.awt.Window ? (java.awt.Window) c : javax.swing.SwingUtilities.getWindowAncestor(c);
				if (w != MainFrame.this) return false; // the station's own window only, with no dialog over it
				ConsoleDialog.open(MainFrame.this);
				return true;
			}
		});
		// Esc at the Space Dock, with nothing else open, asks before quitting (a dialog on top takes the key itself)
		getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "quitPrompt");
		getRootPane().getActionMap().put("quitPrompt", new javax.swing.AbstractAction() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				if (!atSpaceDock || !isFocused()) return;
				Object[] opts = {"Yes", "No"};
				int r = javax.swing.JOptionPane.showOptionDialog(MainFrame.this, "Leave the Space Dock and close The Home Planet Station interface?", "Quit",
						javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE, null, opts, opts[1]);
				if (r == 0) dispatchEvent(new java.awt.event.WindowEvent(MainFrame.this, java.awt.event.WindowEvent.WINDOW_CLOSING)); // the same path as the close box
			}
		});
	}
	private boolean atSpaceDock = true, atMuseum = false, atComm = false;
	public final MuseumUI museum;
	public final LongRangeCommUI comm;

	/** Is the Cargo Bay the screen showing (the one with work that can be left unsaved)? */
	public boolean atCargoBay() { return !atSpaceDock && !atMuseum && !atComm; }
	/** Is the Space Dock the screen showing? */
	public boolean atSpaceDock() { return atSpaceDock; }
	/** Is the Long Range Comm. screen the one showing? */
	public boolean atLongRangeComm() { return atComm; }
	/** Opens Long Range Comm. (asking for the commander's name the first time). */
	public void showLongRangeComm() {
		screens.show(tasksPane, "comm");
		atSpaceDock = false;
		atMuseum = false;
		atComm = true;
		if (!comm.init()) { showSpaceDock(); return; }
		comm.revalidate();
		comm.repaint();
	}

	/** Opens the Federation Museum. */
	public void showMuseum() {
		atSpaceDock = false;
		atMuseum = true;
		atComm = false;
		screens.show(tasksPane, "museum");
		museum.init();
	}

	/** Opens the Cargo Bay, fresh from the saves. */
	public void showCargoBay() {
		cargoBay.openOnBoarded();
		atSpaceDock = false;
		atMuseum = false;
		atComm = false;
		screens.show(tasksPane, "cargo");
		cargoBay.revalidate();
		cargoBay.repaint();
	}
	/** Back to the Space Dock, fresh from the saves. */
	public void showSpaceDock() {
		spaceDock.init();
		atSpaceDock = true;
		atMuseum = false;
		atComm = false;
		screens.show(tasksPane, "dock");
		spaceDock.revalidate();
		spaceDock.repaint();
	}

	/** Drops cached pictures whose path starts like this (a design's pictures change between previews). */
	public void forgetImages(String prefix) {
		for (java.util.Iterator<String> it = imageCache.keySet().iterator(); it.hasNext();) if (it.next().startsWith(prefix)) it.remove();
		for (java.util.Iterator<String> it = scaledCache.keySet().iterator(); it.hasNext();) if (it.next().startsWith(prefix)) it.remove();
	}
	/** A picture from the game data (img/ship/kestral_base.png, say), cached; scaled down to report size if asked. */
	public BufferedImage getResourceImage(String innerPath, boolean scale) {
		BufferedImage result = imageCache.get(innerPath);
		if (result == null) {
			InputStream in = null;
			try {
				in = DataManager.get().getResourceInputStream(innerPath);
				result = ImageIO.read(in);
				imageCache.put(innerPath, result);
			} catch (IOException e) {
				log.warn("Failed to load {}: {}", innerPath, e.toString());
				return null;
			} finally {
				try { if (in != null) in.close(); } catch (IOException e) { }
			}
		}
		if (result == null || !scale) return result;
		BufferedImage small = scaledCache.get(innerPath);
		if (small == null) {
			small = scaleToReport(result);
			scaledCache.put(innerPath, small);
		}
		return small;
	}
	/** The report's picture box; larger pictures are shrunk to fit it, keeping their shape. */
	private static final int REPORT_W = 191, REPORT_H = 121;
	private static BufferedImage scaleToReport(BufferedImage image) {
		if (image.getWidth() <= REPORT_W && image.getHeight() <= REPORT_H) return image;
		double r = Math.min(REPORT_W / (double) image.getWidth(), REPORT_H / (double) image.getHeight());
		int w = Math.max(1, (int) Math.round(image.getWidth() * r)), h = Math.max(1, (int) Math.round(image.getHeight() * r));
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(image, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	// Window memory: window_bounds=x,y,width,height and window_maximized=true/false in the cfg.
	private void restoreWindow() {
		try {
			String b = HomePlanet.config.getProperty("window_bounds");
			if (b != null) {
				String[] p = b.split(",");
				java.awt.Rectangle r = new java.awt.Rectangle(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
						Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()));
				if (r.width >= 300 && r.height >= 200 && isOnScreen(r)) setBounds(r);
			}
			if (Boolean.parseBoolean(HomePlanet.config.getProperty("window_maximized", "false"))) setExtendedState(getExtendedState() | MAXIMIZED_BOTH);
		} catch (Exception e) {
			// a bad value in the cfg: keep the default size
		}
	}
	private void rememberWindow() {
		boolean max = (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
		java.util.Properties cfg = HomePlanet.config;
		cfg.setProperty("window_maximized", Boolean.toString(max));
		if (!max) { // when maximized, keep the last normal size for un-maximizing
			java.awt.Rectangle r = getBounds();
			cfg.setProperty("window_bounds", r.x + "," + r.y + "," + r.width + "," + r.height);
		}
		HomePlanet.saveConfig();
	}
	/** True if at least a good chunk of the title bar area is on some screen. */
	private static boolean isOnScreen(java.awt.Rectangle r) {
		java.awt.Rectangle title = new java.awt.Rectangle(r.x + 20, r.y, Math.max(1, r.width - 40), 30);
		for (java.awt.GraphicsDevice gd : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			if (gd.getDefaultConfiguration().getBounds().intersects(title)) return true;
		}
		return false;
	}
}
