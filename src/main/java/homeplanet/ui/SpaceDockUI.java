package homeplanet.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.core.HomePlanet;
import homeplanet.core.GameGuard;
import homeplanet.core.HistoryLog;
import homeplanet.core.SafeFiles;
import homeplanet.model.Items;
import homeplanet.parser.CompanionMod;
import homeplanet.parser.Retrofit;
import homeplanet.parser.SaveHelper;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * The Space Dock: the station's main screen. The boarded ship sits at the berth beside the saucer, the docked ships
 * wait in the grid below, and the controls (Launch, New Journey, Cargo Bay, Commission, Design, Junkyard, Disband,
 * Settings, Refresh) run down the right. Every ship here is a {@link Ship} in the {@link Vault}.
 */
public class SpaceDockUI extends JPanel implements ActionListener {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SpaceDockUI.class);
	private final Map<JButton, Ship> boardButtons = new HashMap<JButton, Ship>();
	private final Map<JButton, Ship> infoButtons = new HashMap<JButton, Ship>();
	private JButton museumBtn;
	private JButton dockLaunchBtn, flipBtn;
	private JButton inboxBtn, repBtn, expeditionsBtn, otherBtn, settingsBtn, disbandBtn, salvageBtn, journeyBtn, commissionBtn, refreshBtn, launchBtn, cargoBtn, designBtn, commBtn, quartersBtn;
	final MainFrame parent;

	/** Width of one docked ship's place in the list. */
	static final int CELL_W = 172;
	/** The boarded ship's picture box, and the room kept between her and the saucer. */
	static final int BERTH_W = 400, BERTH_H = 250, BERTH_PIC_Y = 8, SAUCER_CLEAR = 24, STATS_W = 150;
	// Past this many characters, warn that FTL might not fit the name. Not verified against FTL.
	static final int LONG_NAME = 20;

	public SpaceDockUI(MainFrame p) {
		this.parent = p;
		init();
		homeplanet.core.SafeFiles.setListener(new homeplanet.core.SafeFiles.Listener() { public void written(File f) { fleetChanged(f); } });
		// a click on the station's window brings it over a docked FTL: FTL is lifted back a moment later (heromedel, 5.33)
		java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(new java.awt.event.AWTEventListener() { public void eventDispatched(java.awt.AWTEvent e) {
			if (e.getID() != java.awt.event.MouseEvent.MOUSE_RELEASED || !homeplanet.core.FtlDock.found() || !(e.getSource() instanceof java.awt.Component)) return;
			if (javax.swing.SwingUtilities.getWindowAncestor(SpaceDockUI.this) == javax.swing.SwingUtilities.getWindowAncestor((java.awt.Component) e.getSource())) liftSoon();
		} }, java.awt.AWTEvent.MOUSE_EVENT_MASK);
		// a station popup opens: a docked FTL steps aside till the last one closes, as for other screens (heromedel, 5.35)
		java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(new java.awt.event.AWTEventListener() { public void eventDispatched(java.awt.AWTEvent e) {
			if (homeplanet.core.FtlDock.active() || overForPopup) javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { popupsChanged(); } });
		} }, java.awt.AWTEvent.WINDOW_EVENT_MASK);
	}
	private boolean overForPopup;
	/** Windows keeps popups just over the station's window, which sits under FTL's: while one is open, the station comes over FTL instead (5.39). */
	private void popupsChanged() {
		boolean popup = false;
		for (java.awt.Window w : java.awt.Window.getWindows()) if (w instanceof java.awt.Dialog && w.isShowing()) { popup = true; break; }
		if (popup && !overForPopup && homeplanet.core.FtlDock.active() && !homeplanet.core.FtlDock.aside() && !homeplanet.core.FtlDock.attached() && parent != null && parent.atSpaceDock()) { // attached, popups come over FTL by themselves
			// the station's window over FTL's, its popups over both; FTL still shows through the viewport's hole (heromedel, 5.39)
			overForPopup = true;
			log.debug("FTL docked: the station over FTL for a popup, FTL seen through its viewport");
			homeplanet.core.FtlDock.stationOnTop(javax.swing.SwingUtilities.getWindowAncestor(this));
		} else if (!popup && overForPopup) {
			overForPopup = false;
			log.debug("FTL docked: back over the station after the popups");
			if (homeplanet.core.FtlDock.active() && parent != null && parent.atSpaceDock()) {
				placeViewport();
				liftSoon();
			}
		}
	}
	private final javax.swing.Timer lift = new javax.swing.Timer(200, new ActionListener() { public void actionPerformed(ActionEvent e) { liftFtl(); } });
	{ lift.setRepeats(false); }
	/** A docked FTL lifted back over its viewport a moment from now (after a click, a rebuild, the station brought forward). */
	/** The station's window closed and opened again (borderless full screen switched): a docked FTL put back. */
	void windowReopened() {
		homeplanet.core.FtlDock.stationReopened(javax.swing.SwingUtilities.getWindowAncestor(this));
		revalidate();
		javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() {
			if (homeplanet.core.FtlDock.active() && viewportPanel != null) fitWindow(); // out of full screen while docked: room for FTL again
			placeViewport();
			liftSoon();
		} });
	}
	void liftSoon() { if (homeplanet.core.FtlDock.found()) lift.restart(); }
	/** Lifted now, without taking the keyboard: not while the docked ships are shown, nor over a station popup. */
	void liftFtl() {
		if (!homeplanet.core.FtlDock.found() || homeplanet.core.FtlDock.aside() || homeplanet.core.FtlDock.attached() || !isShowing() || overForPopup) return; // attached, it stays over the station by itself
		java.awt.Window active = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
		java.awt.Window station = javax.swing.SwingUtilities.getWindowAncestor(this);
		if (active != null && active != station && active.isShowing()) return; // a popup is up: FTL stays under it (one just closed may still count as active a moment)
		if (active == station) homeplanet.core.FtlDock.raise(station); // the station in use: FTL over it, and over a program left between them by an Alt+Tab (heromedel, 5.43)
		homeplanet.core.FtlDock.tuckUnder(station); // the station's own window under FTL's: Windows refuses lifting another program's window (5.34)
	}

	// ---- keeping itself current: a rebuild a moment after anything in the fleet's folder is written ----

	/** How long after the last write the rebuild comes: several writes in a row (one action) make one rebuild. */
	private static final int SETTLE_MS = 250;
	private final javax.swing.Timer settle = new javax.swing.Timer(SETTLE_MS, new ActionListener() { public void actionPerformed(ActionEvent e) {
		if (rebuilding || !isShowing()) return; // not showing: the return to the Space Dock rebuilds it (MainFrame.showSpaceDock)
		log.debug("Space Dock: the fleet's files changed, rebuilding");
		init();
	} });
	{ settle.setRepeats(false); }
	private boolean rebuilding;
	/** How long the last rebuild's look took (the debug log's timing of Board and Dock, 6.01). */
	private long lastLookMs;
	/**
	 * Something in the fleet's folder was written (a letter read, a job finished, a parcel landed over the Long Range, a
	 * ransom settled), from whatever thread: the Space Dock rebuilds itself a moment later, behind whatever window is
	 * open, so its counts and lists are current without the window being closed. Writes of its own rebuild are ignored.
	 */
	private void fleetChanged(File f) {
		if (rebuilding || !Vault.isOpen()) return;
		try {
			String root = Vault.get().root.getCanonicalPath() + File.separator;
			if (!f.getCanonicalPath().startsWith(root)) return;
		} catch (IOException e) { return; }
		settle.restart(); // safe from any thread: the rebuild runs on the Swing thread
	}

	private static boolean loggedStartup = false;

	/** Rebuilds the screen from the vault (after taking stock of the files, since FTL may have changed them). */
	public void init() {
		if (rebuilding) return;
		rebuilding = true;
		try { build(); } finally { rebuilding = false; }
	}
	private void build() {
		removeAll();
		// the safety check (heromedel, 5.57): still in the docked view, but FTL's window gone: the normal Space Dock
		if (homeplanet.core.FtlDock.active() && homeplanet.core.FtlDock.found() && !homeplanet.core.FtlDock.alive()) {
			log.info("FTL docked: its window is gone; the Space Dock as usual");
			endDockView();
		}
		Vault vault = Vault.get();
		long look = System.nanoTime();
		try {
			vault.takeStock();
			lastLookMs = (System.nanoTime() - look) / 1000000;
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not take stock of the fleet:\n" + e);
		}
		final List<homeplanet.parser.FinalVictory.Notice> victories = homeplanet.parser.FinalVictory.settle(); // before the inbox counts its messages
		boardButtons.clear();
		infoButtons.clear();
		setLayout(new java.awt.BorderLayout(0, 0));
		setOpaque(false);

		// Left: the docked ships, three across; this list scrolls on its own, over the fixed backdrop
		JPanel grid = new JPanel(new GridLayout(0, 3, 0, 0));
		grid.setOpaque(false);
		for (Ship s : vault.docked()) grid.add(shipPanel(s));
		JPanel gridTop = new JPanel(new java.awt.BorderLayout());
		gridTop.setOpaque(false);
		gridTop.add(grid, java.awt.BorderLayout.NORTH);
		javax.swing.JScrollPane gridScroll = new javax.swing.JScrollPane(gridTop);
		gridScroll.setOpaque(false);
		gridScroll.getViewport().setOpaque(false);
		gridScroll.setBorder(null);
		gridScroll.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		gridScroll.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		gridScroll.getVerticalScrollBar().setUnitIncrement(24);
		gridScroll.getVerticalScrollBar().setOpaque(false);
		gridScroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 10));
		gridScroll.getVerticalScrollBar().setUI(new MenuTheme.DarkScrollBarUI(new Color(214, 230, 222, 150), new Color(0, 0, 0, 0))); // slim, to suit the station
		final JPanel docked = new JPanel(new java.awt.BorderLayout(0, 6));
		docked.setOpaque(false);
		docked.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 14, 0, 0));
		String title = "Docked";
		timeRound(true); // ransoms and the infirmary, once the screen is up
		boolean longRange = parent != null && parent.comm != null && parent.comm.inboxWanted(); // a commander's mail needs an inbox, whatever the setting
		if (HomePlanet.immersiveNotifications() || longRange) {
			homeplanet.parser.Transmissions.check(); // anything new from The Federation Home Planet (without the inbox, only augments shipped home)
			int unread = homeplanet.parser.Transmissions.unread();
			inboxBtn = new TransmissionButton(unread);
			inboxBtn.addActionListener(this);
			if (homeplanet.core.FtlDock.found() && lastUnread >= 0 && unread > lastUnread) noticeOverFtl(); // a letter while FTL is docked (5.29)
			lastUnread = unread;
		} else {
			inboxBtn = null;
		}
		// the reputation (Settings' Reputation rule; always in Immersive Mode), in gold to the inbox's right: clicking opens its log
		repBtn = homeplanet.vault.Reputation.shown() ? new ReputationButton(homeplanet.vault.Reputation.total(vault)) : null;
		if (repBtn != null) repBtn.addActionListener(this);
		boolean inboxHere = vault.boarded() == null; // with a ship aboard, the inbox and reputation sit on her heading instead
		int inboxW = inboxHere ? inboxWidth() : 0;
		FtlButton.Header dockedHeader = new FtlButton.Header(title, CELL_W * 3 - inboxW, true);
		if (HomePlanet.immersiveMode) dockedHeader.setToolTipText("Immersive Mode: your rank. Captains may commission custom ships; Commodores, custom ships with artillery");
		docked.add(withInbox(dockedHeader, inboxHere), java.awt.BorderLayout.NORTH);
		docked.add(gridScroll, java.awt.BorderLayout.CENTER);
		final int dockedW = 14 + CELL_W * 3 + 18;

		// Right: the station's controls, in a column
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setOpaque(false);
		controls.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 10, 10, homeplanet.core.FtlDock.active() ? 0 : 16)); // flush right while FTL is docked: more room for it (5.31)
		launchBtn = controlButton("Launch FTL", "Play FTL");
		journeyBtn = controlButton("New Journey", "Set out from the first sector with the boarded ship, crew and cargo");
		commissionBtn = controlButton("Commission", "Have a brand-new ship built, as a new game would start her");
		salvageBtn = controlButton("Junkyard", "The Junkyard: salvage, scrap or sell a ship, or buy derelicts and parts");
		disbandBtn = controlButton("Decommission", "Decommission the boarded ship: she goes to the Junkyard");
		settingsBtn = controlButton("Settings", "Folders, launching and rules");
		quartersBtn = controlButton("Quarters", "Click here to head to quarters for a quick rest.");
		refreshBtn = new RefreshButton(); // a small square beside Helm, over the main panel's edge
		refreshBtn.setToolTipText("Take stock of the Space Dock again (after playing FTL, or changing save files)");
		refreshBtn.addActionListener(this);
		cargoBtn = controlButton("Cargo Bay", "Trade, store and shop: the boarded ship's cargo, crew, weapons and systems");
		expeditionsBtn = HomePlanet.expeditionType == 0 ? controlButton("Hire Crew", "Post for volunteers: new crew wait in the Cargo Hold")
				: controlButton("Expeditions", "Jobs for crew without a ship: send crew from the Cargo Hold, or post for volunteers");
		commBtn = new FtlButton("Long Range", FtlFont.MENU, 180, 40) {
			@Override protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				// a lamp: green while powered up (hailing frequencies left open), orange for a hail that went unanswered
				int lamp = parent == null || parent.comm == null ? 0 : parent.comm.lamp();
				if (lamp == 0) return;
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(lamp == 2 ? CargoParts.ORANGE : MenuTheme.GREEN);
				g2.fillOval(getWidth() - 20, 9, 9, 9);
				g2.setColor(Color.black);
				g2.drawOval(getWidth() - 20, 9, 9, 9);
				g2.dispose();
			}
		};
		commBtn.setToolTipText("Long Range Comm.: trade with another commander's Home Planet Station over the network");
		commBtn.addActionListener(this);
		commBtn.setAlignmentX(LEFT_ALIGNMENT);
		controlGroup(controls, "Helm", launchBtn, journeyBtn);
		if (homeplanet.core.FtlDock.optionOn()) { // heromedel, 5.29: the docked launch, a small icon under Refresh
			dockLaunchBtn = new DockLaunchButton();
			dockLaunchBtn.setToolTipText(homeplanet.core.FtlDock.active() ? "Undock or close FTL" : "Launch FTL docked in the station window"); // an X while docked (heromedel, 5.57)
			dockLaunchBtn.addActionListener(this);
			launchBtn.addComponentListener(new java.awt.event.ComponentAdapter() { @Override public void componentMoved(java.awt.event.ComponentEvent e) { alignDockLaunch(); } });
		} else {
			dockLaunchBtn = null;
		}
		otherBtn = controlButton("Other...", "Orders the station rarely needs: recover a lost or destroyed ship, clean up blueprints, report for reassignment");
		if (homeplanet.parser.Museum.anything(vault)) { // once a ship has won, or been lost in action
			museumBtn = controlButton("Museum", "The Federation Museum: the Hall of Victors, and the Memorial to ships lost in action");
			controlGroup(controls, "Station", cargoBtn, commBtn, expeditionsBtn, quartersBtn, settingsBtn, museumBtn);
		} else {
			museumBtn = null;
			controlGroup(controls, "Station", cargoBtn, commBtn, expeditionsBtn, quartersBtn, settingsBtn);
		}
		String designLock = homeplanet.parser.Clearance.customReason();
		designBtn = controlButton("Design Ship", designLock == null ? "Lay out a new ship of your own on a blank grid"
				: "<html>" + homeplanet.parser.XmlText.text(designLock).replace("\n", "<br>") + "</html>");
		controlGroup(controls, "Shipyard", commissionBtn, designBtn, salvageBtn, disbandBtn, otherBtn); // its orders are all shipyard business: recover, blueprints, reassignment

		// The ship at your command, large, at the top beside the station's saucer (not touching it), a few of her
		// particulars to her left when there's room; the docked ships below. Nothing of her at all when none is boarded.
		final Ship boarded = vault.boarded();
		final JPanel berth = boarded == null ? null : berthPanel(boarded);
		final JPanel stats = boarded == null ? null : statsPanel(boarded);
		final JPanel aboard = boarded == null ? null : aboardRow;
		final JPanel view = homeplanet.core.FtlDock.active() && !homeplanet.core.FtlDock.aside() ? viewport() : null; // FTL docked in her place (5.29); flipped, the Space Dock as without FTL (5.34)
		viewportPanel = view;
		JPanel main = new JPanel(null) {
			@Override
			public void doLayout() {
				int w = SpaceDockUI.this.getWidth(), h = SpaceDockUI.this.getHeight();
				int top = 0;
				if (view != null) { // her heading (inbox, reputation) on top, then FTL's viewport; the docked ships below
					int y0 = 10;
					Dimension vs = homeplanet.core.FtlDock.size();
					int ah = aboard == null ? 0 : aboard.getPreferredSize().height;
					if (aboard != null) y0 = 10 + ah + 6;
					// smaller than chosen when the window can't hold it (FTL's window resizes), keeping its 16:9
					int vw = Math.min(vs.width, getWidth() - 30 - RefreshButton.SIZE), vh = Math.min(vs.height, getHeight() - y0 - DOCK_BELOW - 2); // FTL's own size inside the frame
					if (vw * vs.height > vh * vs.width) vw = vh * vs.width / vs.height; else vh = vw * vs.height / vs.width;
					vw = Math.max(160, vw); vh = Math.max(90, vh);
					if (aboard != null) aboard.setBounds(14, 10, Math.min(aboard.getPreferredSize().width, vw), ah); // her heading its usual length, the inbox and reputation after it
					view.setBounds(14, y0, vw + 2, vh + 2); // the gold frame a pixel outside FTL all round (5.39: FTL covered its bottom and right)
					docked.setVisible(false); // nothing below FTL: the flip shows the Space Dock as usual instead (5.34)
					javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { placeViewport(); } });
				} else if (berth != null) {
					Dimension d = berth.getPreferredSize();
					double sc = SpaceDockScrollPane.scale(w, h);
					int saucerLeft = (int) Math.round(SpaceDockScrollPane.offsetX(w, h) + SpaceDockScrollPane.saucerLeft() * sc);
					int x = Math.max(14, Math.min(saucerLeft - SAUCER_CLEAR - d.width, getWidth() - d.width - 10));
					Dimension sd = stats.getPreferredSize();
					// her particulars stand beside her picture itself (it's centred in her berth), and she moves right a little
					// to make room for them rather than have them hidden
					int inset = pictureInset(berth), need = 14 + sd.width + 12 - inset;
					if (x < need) x = Math.max(x, Math.min(need, getWidth() - d.width - 10));
					Dimension ad = aboard.getPreferredSize();
					int ah = ad.height + 6, y0 = 10 + ah; // her heading, then her berth below it
					aboard.setBounds(14, 10, x + ad.width - 14, ad.height); // from the left margin to where it ends over her berth
					berth.setBounds(x, y0, d.width, d.height);
					int sx = x + inset - 12 - sd.width;
					boolean room = sx >= 14;
					stats.setVisible(room);
					if (room) stats.setBounds(sx, y0 + berth.getComponent(0).getPreferredSize().height + BERTH_PIC_Y, sd.width, sd.height);
					top = y0 + d.height + 6;
					if (room) top = Math.max(top, stats.getY() + sd.height + 6); // a tall stats column pushes the docked ships down, not under it
				}
				if (view == null) docked.setBounds(0, top, Math.min(dockedW, getWidth()), Math.max(0, getHeight() - top)); // while docked, nothing below FTL (5.32)
				refreshBtn.setBounds(getWidth() - RefreshButton.SIZE - 2, 14, RefreshButton.SIZE, RefreshButton.SIZE); // at the top right, left of Helm, past the column's edge
				if (dockLaunchBtn != null) { // the docked launch, under it, level with Launch FTL's middle
					dockLaunchBtn.setBounds(getWidth() - RefreshButton.SIZE - 2, 14 + RefreshButton.SIZE + 6, RefreshButton.SIZE, RefreshButton.SIZE);
					javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { alignDockLaunch(); } });
				}
			}
		};
		main.setOpaque(false);
		main.add(refreshBtn);
		if (dockLaunchBtn != null) main.add(dockLaunchBtn);
		if (view != null) { if (aboard != null) main.add(aboard); main.add(view); }
		else if (berth != null) { main.add(aboard); main.add(berth); main.add(stats); }
		main.add(docked);

		add(main, java.awt.BorderLayout.CENTER);
		add(controls, java.awt.BorderLayout.EAST);
		revalidate(); // lay out and redraw the new contents at once (Refresh, Settings...)
		repaint();
		liftSoon(); // a docked FTL back over its rebuilt viewport (5.33)
		if (!loggedStartup) {
			HistoryLog.loaded("startup");
			loggedStartup = true;
		}
		// FTL's New Game wrote over the boarded ship, or continue.sav is a ship the station never commissioned
		final String cloud = vault.takeCloudCopy();
		if (cloud != null) {
			javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() {
				JOptionPane.showMessageDialog(null, cloud + "\n\n" // what was set aside, and where (Vault, 6.08: the ship mark)
						+ "To stop this, turn off Steam Cloud for FTL: in your Steam library, right-click FTL, Properties, General.", "Steam Cloud", JOptionPane.WARNING_MESSAGE);
			} });
		}
		if (!askingToHard && !toHardPutOff && homeplanet.parser.FinalVictory.TO_HARD_ASK.equals(homeplanet.parser.FinalVictory.toHardRule())) { // before any rescue offer it would decide
			askingToHard = true;
			javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { askToHard(); } });
		}
		if (!askingRate && !ratePutOff && homeplanet.vault.Reputation.RATE_ASK.equals(homeplanet.vault.Reputation.rateRule())) { // a Custom career from before 6.13
			askingRate = true;
			javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { askRepRate(); } });
		}
		if (!askingFound && !vault.found().isEmpty()) { // saves and ships found as the fleet opened, waiting on the player's word (6.10)
			askingFound = true;
			javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { askFound(); } });
		}
		for (final homeplanet.parser.FinalVictory.Notice n : victories) {
			if (n.offer != null && deferredOffers.contains(n.offer.id)) continue;
			javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { victoryNotice(n); } });
		}
		final String over = vault.takeOverwritten();
		final Ship stranger = vault.boarded() != null && vault.boarded().stranger && !deferredStrangers.contains(vault.boarded().id) ? vault.boarded() : null;
		final List<Vault.Departed> back = new java.util.ArrayList<Vault.Departed>(); // career ships FTL's New Game may have written over by accident (5.55)
		if (HomePlanet.immersiveMode) for (Vault.Departed d : vault.offeredBack()) if (!deferredStrangers.contains(d.id)) back.add(d);
		if (over != null || (stranger != null && HomePlanet.immersiveMode) || !back.isEmpty()) {
			javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { newGameNotice(over, stranger, back); } });
		}
		// what came into FTL's profile while an uncommissioned ship was boarded (5.55): asked with FTL closed, once
		if (HomePlanet.immersiveMode && !profileAsked && !homeplanet.parser.UnlockGrants.strangers().isEmpty() && !GameGuard.isFtlRunning()) {
			javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { strangerUnlocks(); } });
		}
	}

	/**
	 * The station's round after time has passed: ransoms asked, reminded or run out (letters in the inbox, or with the
	 * inbox off, pop-ups here), and crew out of the infirmary (a word here, never a letter). At each look at the Space
	 * Dock, and after each expedition, since jobs follow one another without a look (the board reopens by itself).
	 * Deferred while the screen is being built.
	 */
	void timeRound(boolean later) {
		Vault vault = Vault.get();
		tell(HomePlanet.expeditionType == 2 ? homeplanet.parser.Assignments.checkReturns(vault) : new java.util.ArrayList<homeplanet.parser.Assignments.Report>(),
				homeplanet.parser.Expeditions.checkRansoms(vault), homeplanet.parser.Expeditions.checkInfirmary(vault), later);
	}
	/** What a round brought, told (the console's /passtime gathers several days' worth and tells them once, 5.22). */
	void tell(final List<homeplanet.parser.Assignments.Report> back, final List<homeplanet.parser.Expeditions.RansomNews> ransomNews, final List<String> upAgain, boolean later) {
		Vault vault = Vault.get();
		final boolean prizes = HomePlanet.expeditionType == 2 && !homeplanet.parser.Assignments.pendingToAsk(vault).isEmpty();
		final List<String> fleet3 = HomePlanet.immersiveNotifications() ? new java.util.ArrayList<String>() : homeplanet.parser.ThirdFleet.due(vault); // with the inbox on, his letters go there
		if ((HomePlanet.immersiveNotifications() || (ransomNews.isEmpty() && back.isEmpty())) && upAgain.isEmpty() && !prizes && fleet3.isEmpty()) return;
		Runnable word = new Runnable() { public void run() {
			if (!HomePlanet.immersiveNotifications()) for (homeplanet.parser.Assignments.Report r : back) AssignmentsDialog.showReport(null, r);
			if (prizes) AssignmentsDialog.askPending(null); // a recruit to take on, a ship to keep: asked whatever the inbox setting
			if (!HomePlanet.immersiveNotifications()) for (homeplanet.parser.Expeditions.RansomNews n : ransomNews) ransomNotice(n);
			for (String key : fleet3) thirdFleetNotice(key);
			if (!upAgain.isEmpty())
				JOptionPane.showMessageDialog(null, String.join(" and ", upAgain) + (upAgain.size() > 1 ? " are" : " is") + " out of the infirmary, on their feet and waiting in the Cargo Hold.", "Infirmary", JOptionPane.INFORMATION_MESSAGE);
		} };
		if (later) javax.swing.SwingUtilities.invokeLater(word); else word.run();
	}
	/** With the inbox off: one of the Third Fleet Commander's letters as a pop-up (the first asks, and his answer follows at once). */
	private void thirdFleetNotice(String key) {
		Vault v = Vault.get();
		java.util.Map<String, String> fill = new java.util.HashMap<String, String>();
		try { fill.put("name", homeplanet.parser.ThirdFleet.fill(v, key)); }
		catch (IOException e) { HomePlanet.showErrorDialog("The Home Planet Station could not ready a letter from the Third Fleet Commander (it comes next time):\n" + e.getMessage()); return; }
		String[] t = homeplanet.parser.Transmissions.text(key, fill);
		if (t == null) return;
		homeplanet.parser.ThirdFleet.markSent(v, key);
		if (!homeplanet.parser.ThirdFleet.isHello(key)) { JOptionPane.showMessageDialog(null, letterArea(t[2]), t[1], JOptionPane.PLAIN_MESSAGE); return; }
		Object[] opts = {"Not interested", "Interested"};
		int r = JOptionPane.showOptionDialog(null, letterArea(t[2]), t[1], JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[1]);
		if (r < 0) return; // closed: no reply, as a letter left unanswered (the chain goes on)
		homeplanet.parser.ThirdFleet.replied(v, r);
		String answer = r == 0 ? homeplanet.parser.ThirdFleet.NO : homeplanet.parser.ThirdFleet.YES;
		String[] a = homeplanet.parser.Transmissions.text(answer, new java.util.HashMap<String, String>());
		homeplanet.parser.ThirdFleet.markSent(v, answer);
		if (a != null) JOptionPane.showMessageDialog(null, letterArea(a[2]), a[1], JOptionPane.PLAIN_MESSAGE);
	}
	private static javax.swing.JTextArea letterArea(String text) {
		javax.swing.JTextArea t = new javax.swing.JTextArea(text);
		t.setEditable(false); t.setLineWrap(true); t.setWrapStyleWord(true); t.setOpaque(false); t.setColumns(52);
		t.setFont(MenuTheme.TEXT_FONT);
		t.setSize(new Dimension(520, 10));
		return t;
	}

	/** With the inbox off: a ransom's ask or reminder as a pop-up (Pay, Refuse, or Later: the reminder asks again), or word of the loss. */
	private void ransomNotice(homeplanet.parser.Expeditions.RansomNews n) {
		javax.swing.JTextArea t = new javax.swing.JTextArea(n.text());
		t.setEditable(false); t.setLineWrap(true); t.setWrapStyleWord(true); t.setOpaque(false); t.setColumns(52);
		t.setFont(MenuTheme.TEXT_FONT);
		t.setSize(new Dimension(520, 10));
		if (n.kind.equals("lost")) { JOptionPane.showMessageDialog(null, t, n.title(), JOptionPane.INFORMATION_MESSAGE); return; }
		Vault v = Vault.get();
		homeplanet.parser.Expeditions.Captive c = homeplanet.parser.Expeditions.openRansom(v, "ransom:" + n.captive.index);
		if (c == null) return; // settled meanwhile
		Object[] opts = {"Pay " + c.ransom + " scrap", "Refuse", "Later"};
		int r = JOptionPane.showOptionDialog(null, t, n.title(), JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[2]);
		try {
			if (r == 0) {
				homeplanet.parser.Expeditions.payRansom(v, c);
				JOptionPane.showMessageDialog(null, c.name + " is back in the Cargo Hold: shaken, thinner, but whole.", "Ransom", JOptionPane.INFORMATION_MESSAGE);
			} else if (r == 1 && HomePlanet.confirmNo(null, "Refuse the ransom? " + c.name + " will not be coming back.", "Ransom")) {
				homeplanet.parser.Expeditions.refuseRansom(v, c);
				JOptionPane.showMessageDialog(null, homeplanet.parser.Expeditions.lostWord(c), "Presumed dead: " + c.name, JOptionPane.INFORMATION_MESSAGE);
			}
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The ransom wasn't settled. Nothing was changed:\n" + e.getMessage());
		}
		init();
	}

	/** Rescue offers the player put off deciding: asked again at the next start (or in the inbox, with Transmissions on). */
	private final java.util.Set<String> deferredOffers = new java.util.HashSet<String>();
	private final java.util.Set<String> askingOffers = new java.util.HashSet<String>();
	/** A final victory, with Transmissions off: the reward's notice, or the rescue's offer (keep her, or the museum's price). */
	private void victoryNotice(homeplanet.parser.FinalVictory.Notice n) {
		javax.swing.JTextArea t = new javax.swing.JTextArea(n.text);
		t.setEditable(false);
		t.setLineWrap(true);
		t.setWrapStyleWord(true);
		t.setOpaque(false);
		t.setColumns(52);
		t.setFont(MenuTheme.TEXT_FONT);
		t.setSize(new Dimension(520, 10)); // wraps to this width before the dialog measures it
		if (n.offer == null) {
			JOptionPane.showMessageDialog(null, t, n.title, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (!askingOffers.add(n.offer.id)) return; // already on screen
		try {
			Object[] options = {"Keep her", "Accept the museum's offer (" + n.value + " scrap)", "Decide later"};
			int c = JOptionPane.showOptionDialog(null, t, n.title, JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
			if (c != 0 && c != 1) { deferredOffers.add(n.offer.id); return; }
			homeplanet.vault.Vault.FinalBattle f = homeplanet.parser.FinalVictory.offer(n.offer.id);
			if (f == null) return; // settled meanwhile
			net.blerf.ftl.constants.Difficulty d = null;
			if (c == 0 && (d = keptDifficulty(null, f)) == null) { deferredOffers.add(n.offer.id); return; }
			String what = c == 0 ? homeplanet.parser.FinalVictory.keep(f, d) : homeplanet.parser.FinalVictory.museum(f);
			JOptionPane.showMessageDialog(null, what, n.title, JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not do that:\n" + e.getMessage());
		} finally {
			askingOffers.remove(n.offer.id);
		}
		init();
	}

	/**
	 * Keeping a rescued ship: the difficulty of her next journey (heromedel, 6.02), asked unless Rescued Ships after
	 * Victory moved to Hard difficulty is on. Null if the player cancels (the offer stays open).
	 */
	static net.blerf.ftl.constants.Difficulty keptDifficulty(java.awt.Component parent, homeplanet.vault.Vault.FinalBattle f) throws IOException {
		if (homeplanet.parser.FinalVictory.toHard()) return net.blerf.ftl.constants.Difficulty.HARD;
		net.blerf.ftl.constants.Difficulty was = HomePlanet.savedGameParser.readSavedGame(f.copy).getDifficulty();
		net.blerf.ftl.constants.Difficulty[] diffs = {net.blerf.ftl.constants.Difficulty.EASY, net.blerf.ftl.constants.Difficulty.NORMAL, net.blerf.ftl.constants.Difficulty.HARD};
		Object[] options = {"Easy", "Normal", "Hard", "Cancel"};
		int now = was == null ? 1 : was.ordinal();
		int c = JOptionPane.showOptionDialog(parent, f.name + " will set out once more from the first sector, with the rebel fleet in pursuit.\n"
				+ "Her last journey was on " + options[now] + ". How dangerous will her next one be?", "Keep " + f.name, JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE, null, options, options[now]);
		return c >= 0 && c <= 2 ? diffs[c] : null;
	}

	private boolean askingFound = false;
	/** What the player closed without an answer, asked again at the next start. */
	private final java.util.Set<String> foundPutOff = new java.util.HashSet<String>();
	/** Each save or ship found as the fleet opened (6.10, Plan N), one at a time: taken in (or sent to Sandbox Mode's fleet), or left. */
	private void askFound() {
		boolean any = false;
		try {
			boolean sandbox = Vault.SANDBOX.equals(Vault.get().slot);
			for (Vault.Found f : Vault.get().found()) {
				String key = f.kind + ":" + f.file.getAbsolutePath();
				if (foundPutOff.contains(key)) continue;
				String where = f.file.getParentFile().getName();
				where = where.equals("shipyard") ? "the shipyard" : where.equals("junkyard") ? "the Junkyard" : "FTL's saves folder";
				String text, yes, no = "Leave it", title;
				if (f.kind == Vault.Found.Kind.NO_SAVE) {
					title = "A ship's save is missing";
					text = f.name + "'s save is missing from her folder in " + where + ".\n\n"
							+ ("marked".equals(f.fix()) ? "A save carrying her mark was found: " + f.source().getName() + ". Put it back in her folder?"
							: "version".equals(f.fix()) ? "Her newest kept version can be put back, as she was then."
							: "Nothing of her save is left, but she can be rebuilt from her records: her class, her crew and her supplies;\nher gear and systems as her class comes.")
							+ "\n\nLeft, she goes to the memorial, as a ship whose save is gone always has.";
					yes = "rebuild".equals(f.fix()) ? "Rebuild her" : "Put it back";
					no = "Leave her";
				} else {
					title = f.kind == Vault.Found.Kind.LOOSE ? "A ship's save was found" : "A ship the fleet doesn't know";
					String what = f.kind == Vault.Found.Kind.LOOSE ? f.file.getName() + " was found in " + where + ": " + f.name + ", a ship FTL can fly."
							: f.kind == Vault.Found.Kind.OTHER ? f.name + "'s folder is in " + where + ", but her record says she belongs to the " + Vault.title(f.career) + " fleet."
							: f.name + "'s folder is in " + where + ", but nothing shows she belongs to this fleet:\nher save doesn't match the station's last copy of it, and the fleet's log has no entry for her.";
					boolean here = sandbox;
					text = what + "\n\n" + (here ? "Take her into the fleet?" : "Only ships this career knows can join it. She can go to Sandbox Mode's fleet instead.")
							+ "\n\nLeft, she isn't asked about again.";
					yes = here ? "Take her in" : "Send her to Sandbox";
				}
				Object[] options = {yes, no};
				int c = JOptionPane.showOptionDialog(null, text, title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
				if (c != 0 && c != 1) { foundPutOff.add(key); continue; }
				try {
					if (c == 0) JOptionPane.showMessageDialog(null, Vault.get().accept(f), title, JOptionPane.INFORMATION_MESSAGE);
					else Vault.get().decline(f);
					any = true;
				} catch (IOException e) {
					HomePlanet.showErrorDialog("The Home Planet Station could not do that; nothing was changed for " + f.name + ":\n" + e.getMessage());
					foundPutOff.add(key);
				}
			}
		} finally {
			askingFound = false;
		}
		if (any) init();
	}
	private boolean askingToHard = false, toHardPutOff = false;
	/** A Custom career with no answer to Rescued Ships after Victory moved to Hard difficulty: asked as its briefing would (6.03), then fixed. Closed: asked again at the next start. */
	private void askToHard() {
		try {
			if (!homeplanet.parser.FinalVictory.TO_HARD_ASK.equals(homeplanet.parser.FinalVictory.toHardRule())) return;
			Object[] options = {"Yes: always to Hard", "No: I'll choose her difficulty"};
			int c = JOptionPane.showOptionDialog(null, "Your Custom career hasn't chosen one of its rules yet:\n\n    Rescued Ships after Victory moved to Hard difficulty\n\n"
					+ "When you keep a ship rescued after a final victory, does she set out on Hard without asking?\n"
					+ "Like the career's other rules, it is chosen once and fixed from then on.", "Your Custom career", JOptionPane.DEFAULT_OPTION,
					JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
			if (c != 0 && c != 1) { toHardPutOff = true; return; }
			homeplanet.parser.FinalVictory.chooseToHard(Vault.get().root, c == 0);
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not record the career's choice in its file (it will ask again):\n" + e.getMessage());
		} finally {
			askingToHard = false;
		}
	}

	private boolean askingRate = false, ratePutOff = false;
	/** A Custom career with no reputation rate: asked as its briefing would (heromedel, 6.13), then fixed. Closed: asked again at the next start (x1 meanwhile). */
	private void askRepRate() {
		try {
			if (!homeplanet.vault.Reputation.RATE_ASK.equals(homeplanet.vault.Reputation.rateRule())) return;
			Object[] options = homeplanet.vault.Reputation.RATE_WORDS;
			int c = JOptionPane.showOptionDialog(null, "Your Custom career hasn't chosen one of its rules yet:\n\n    Reputation earned\n\n"
					+ "What your ships earn (sectors, ships defeated, scrap, good outcomes, achievements, expeditions) counts at this rate.\n"
					+ "Losses and spending count as they are. Like the career's other rules, it is chosen once and fixed from then on.", "Your Custom career", JOptionPane.DEFAULT_OPTION,
					JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
			if (c < 0 || c > 2) { ratePutOff = true; return; }
			homeplanet.vault.Reputation.chooseRate(Vault.get().root, c);
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not record the career's choice in its file (it will ask again):\n" + e.getMessage());
		} finally {
			askingRate = false;
		}
	}

	private boolean askingAboutStranger = false;
	private static String cap(String s) { return homeplanet.model.Words.cap(s); }
	/** The offer about a stranger's achievements and unlocks was put off (closed): asked again at the next start. */
	private boolean profileAsked = false;
	/** What came into FTL's profile while an uncommissioned ship was boarded: heromedel's offer to take it back out (5.55). */
	private void strangerUnlocks() {
		if (profileAsked || askingAboutStranger || GameGuard.isFtlRunning()) return;
		java.util.Set<String> keys = homeplanet.parser.UnlockGrants.strangers();
		if (keys.isEmpty()) return;
		profileAsked = true;
		StringBuilder list = new StringBuilder();
		List<String> names = new java.util.ArrayList<String>();
		for (String k : keys) { String n = homeplanet.parser.UnlockGrants.describe(k); names.add(n); list.append("\n  \u2022 ").append(n); }
		Object[] options = {"Remove them", "Keep them"};
		int c = JOptionPane.showOptionDialog(null, "Achievements and unlocks were found that may not have come from an Immersive Commissioned ship, so you may not receive their bonuses and unlocks in this mode.\n"
				+ "Would you like them removed from the FTL profile this career uses?\n" + list,
				"Achievements and unlocks", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (c < 0) return; // closed: asked again at the next start
		if (c == 1) { homeplanet.parser.UnlockGrants.strangersAnswered(java.util.Collections.<String>emptySet()); return; }
		if (GameGuard.isFtlRunning()) {
			JOptionPane.showMessageDialog(null, "FTL is running. " + GameGuard.CLOSE_FTL + " The Home Planet Station will ask again.", "Achievements and unlocks", JOptionPane.INFORMATION_MESSAGE);
			profileAsked = false;
			return;
		}
		try {
			File backup = homeplanet.parser.UnlockGrants.removeStrangers(keys, names);
			JOptionPane.showMessageDialog(null, "Removed from FTL's profile. A backup was made first: " + backup.getName(), "Achievements and unlocks", JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not change FTL's profile:\n" + e.getMessage() + "\nNothing was removed.");
		}
	}
	/** Uncommissioned ships the player put off deciding about: asked again at the next start. */
	private final java.util.Set<String> deferredStrangers = new java.util.HashSet<String>();
	/** Tells the player a boarded ship was overwritten; in Immersive Mode, offers back a career ship overwritten by accident, and asks what's to become of an uncommissioned ship. */
	private void newGameNotice(String over, Ship stranger, List<Vault.Departed> back) {
		if (askingAboutStranger) return;
		for (Vault.Departed d : back) { // heromedel's words (5.55)
			askingAboutStranger = true;
			int c;
			try {
				c = JOptionPane.showConfirmDialog(null, "An Immersive Ship from this career, " + homeplanet.parser.ShipNames.the(d.name) + ", is suspected to have been overwritten by accident.\n"
						+ "If this is the case, would you like it restored from its last known point?\n\nDo not select yes if she was destroyed in battle.",
						"Overwritten by accident?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
			} finally {
				askingAboutStranger = false;
			}
			if (c == JOptionPane.CLOSED_OPTION) { deferredStrangers.add(d.id); return; } // asked again at the next start
			if (c == JOptionPane.NO_OPTION) { Vault.get().declineBack(d); if (d.name.equals(over)) over = null; continue; }
			if (GameGuard.isFtlRunning()) {
				JOptionPane.showMessageDialog(null, "FTL is running. " + GameGuard.CLOSE_FTL + " The Home Planet Station will ask again.", "Overwritten by accident?", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			try {
				Ship s = Vault.get().restoreBack(d);
				homeplanet.vault.CrewRegister.shipBack(Vault.get(), s);
				JOptionPane.showMessageDialog(null, cap(homeplanet.parser.ShipNames.the(s.name)) + (s.isBoarded() ? " is boarded again" : " waits at the Space Dock")
						+ ", as she was at her last known point." + (stranger != null && s.isBoarded() ? "\n" + stranger.name + " waits at the Sandbox fleet's Space Dock." : ""),
						"Restored", JOptionPane.INFORMATION_MESSAGE);
			} catch (IOException e) {
				HomePlanet.showErrorDialog("The Home Planet Station could not restore " + d.name + ":\n" + e.getMessage());
			}
			init();
			return;
		}
		if (over == null && (stranger == null || !HomePlanet.immersiveMode)) return;
		String lost = over == null ? "" : over + " was boarded, and FTL started a new game over her.\n"
				+ (HomePlanet.immersiveMode ? "She is lost. Her last version is in the station's records.\n"
						: "Her last version is in the station's records: Other... > Recover a ship brings her back.\n");
		if (stranger == null || !HomePlanet.immersiveMode) {
			JOptionPane.showMessageDialog(null, lost + (stranger == null ? "" : "\n" + stranger.name + " is now boarded."), "New game in FTL", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		askingAboutStranger = true;
		try {
			askAboutStranger(lost, stranger);
		} finally {
			askingAboutStranger = false;
		}
	}
	private void askAboutStranger(String lost, Ship stranger) {
		String message = (lost.isEmpty() ? "" : lost + "\n")
				+ "Uncommissioned ship detected.\n\n" + stranger.name + " was not commissioned by The Federation Home Planet: this save was not made in Immersive Mode.\n"
				+ "What should be done with her?";
		Object[] options = {"Send her to the Sandbox fleet's Space Dock", "Decommission her", "Switch to Sandbox Mode now", "Close The Home Planet Station"};
		int c = JOptionPane.showOptionDialog(null, message, "Uncommissioned ship", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
		if (c == 3) { if (parent != null) parent.dispatchEvent(new java.awt.event.WindowEvent(parent, java.awt.event.WindowEvent.WINDOW_CLOSING)); return; }
		if (c < 0) { deferredStrangers.add(stranger.id); init(); return; } // closed: asked again at the next start
		if (GameGuard.isFtlRunning()) {
			JOptionPane.showMessageDialog(null, "FTL is running. " + GameGuard.CLOSE_FTL + " The Home Planet Station will ask again.", "Uncommissioned ship", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Vault v = Vault.get();
		try {
			if (c == 0) {
				v.sendToOtherFleet(stranger, false);
				JOptionPane.showMessageDialog(null, stranger.name + " waits at the Sandbox fleet's Space Dock.", "Uncommissioned ship", JOptionPane.INFORMATION_MESSAGE);
			} else if (c == 1) {
				Object[] how = {"Send her to the normal Junkyard", "Destroy her", "Cancel"};
				int d = JOptionPane.showOptionDialog(null, "Decommission " + stranger.name + ":\n\n"
						+ "Send her to the Sandbox fleet's Junkyard, or destroy her? (A destroyed ship's last version stays in the station's records.)",
						"Decommission", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, how, how[2]);
				if (d == 0) v.sendToOtherFleet(stranger, true);
				else if (d == 1) v.remove(stranger, "DESTROY");
				else { deferredStrangers.add(stranger.id); init(); return; }
			} else {
				ImmersiveDialog.leaveNow(stranger); // her fleet, rules and FTL profile: the normal ones
				JOptionPane.showMessageDialog(null, "Immersive Mode is off. " + stranger.name + " is boarded in your Sandbox fleet.\n"
						+ "Your Immersive fleet is kept as it was.", "Uncommissioned ship", JOptionPane.INFORMATION_MESSAGE);
			}
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not do that:\n" + e.getMessage());
		}
		init();
	}

	/**
	 * Why the Cargo Bay can't open now (her save unreadable), or null if it can. With no ship aboard it opens on the Cargo
	 * Hold. Away from a store it opens all the same (heromedel, 5.52; the Cargo Bay follows the ship picked on it since
	 * 5.00): she can't trade there and it says so, and another ship can be picked.
	 */
	private FtlButton controlButton(String text, String tip) {
		FtlButton b = new FtlButton(text, FtlFont.MENU, 180, 40);
		b.setToolTipText(tip);
		b.addActionListener(this);
		b.setAlignmentX(LEFT_ALIGNMENT);
		return b;
	}
	/** A gold heading and its buttons; a click on the heading folds them away or back (remembered between runs). */
	private static void controlGroup(final JPanel column, String title, javax.swing.JComponent... buttons) {
		final String key = "fold_" + title.toLowerCase().replace(' ', '_');
		final JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		body.setAlignmentX(LEFT_ALIGNMENT);
		body.add(gap(10));
		for (javax.swing.JComponent b : buttons) {
			body.add(b);
			body.add(gap(10));
		}
		boolean folded = "true".equals(HomePlanet.config.getProperty(key));
		body.setVisible(!folded);
		column.add(new FtlButton.Header(title, 186).foldable(folded, new java.util.function.Consumer<Boolean>() {
			public void accept(Boolean f) {
				body.setVisible(!f);
				HomePlanet.config.setProperty(key, Boolean.toString(f));
				HomePlanet.saveConfig();
				column.revalidate();
				column.repaint();
			}
		}));
		column.add(body);
		column.add(gap(16));
	}
	/** Space between the column's pieces that gives way first when the window is short (down to 2 pixels). */
	private static Box.Filler gap(int h) {
		return new Box.Filler(new Dimension(1, 2), new Dimension(1, h), new Dimension(1, h));
	}

	// ---- pictures ----

	/** Fitted ship pictures, by picture name, box and greying: the same hull is drawn many times over a session. */
	private static final Map<String, BufferedImage> pictureCache = new HashMap<String, BufferedImage>();

	/** The ship's picture, trimmed to the hull and fitted in the box (greyed while she's away from a station). */
	private java.awt.Image shipPicture(Ship ship0, int maxW, int maxH, boolean grey) {
		SavedGameState gs = ship0.save();
		ShipBlueprint ship = gs == null ? null : blueprintOf(gs.getPlayerShipBlueprintId());
		if (ship == null) return new BufferedImage(maxW, maxH, BufferedImage.TYPE_INT_ARGB); // unknown ship or missing art
		String key = ship.getGraphicsBaseName() + "|" + maxW + "x" + maxH + (grey ? "|grey" : "");
		BufferedImage cached = pictureCache.get(key);
		if (cached != null) return cached;
		BufferedImage base = parent.getResourceImage("img/ship/" + ship.getGraphicsBaseName() + "_base.png", false);
		if (base == null) return new BufferedImage(maxW, maxH, BufferedImage.TYPE_INT_ARGB);
		BufferedImage trimmed = IconFactory.trim(base, 8);
		BufferedImage fitted = fitImage(trimmed == null ? base : trimmed, maxW, maxH);
		if (grey) {
			java.awt.Image g = javax.swing.GrayFilter.createDisabledImage(fitted);
			BufferedImage out = new BufferedImage(fitted.getWidth(), fitted.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D gg = out.createGraphics();
			gg.drawImage(g, 0, 0, null);
			gg.dispose();
			fitted = out;
		}
		pictureCache.put(key, fitted);
		return fitted;
	}
	/** A ship blueprint by id: a player ship, or an auto (enemy) one. */
	static ShipBlueprint blueprintOf(String id) {
		ShipBlueprint bp = DataManager.get().getShips().get(id);
		return bp != null ? bp : DataManager.get().getAutoShips().get(id);
	}
	/** Scaled smoothly to fit the box, keeping its shape. */
	static BufferedImage fitImage(BufferedImage img, int maxW, int maxH) {
		double r = Math.min(maxW / (double) img.getWidth(), maxH / (double) img.getHeight());
		int w = Math.max(1, (int) Math.round(img.getWidth() * r)), h = Math.max(1, (int) Math.round(img.getHeight() * r));
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(img, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	// ---- the ship panels ----

	/** A small label with a drop shadow, so it reads over the planet. */
	static JLabel shadowLabel(String text, Color color, boolean bold) {
		JLabel l = new JLabel(text) {
			@Override
			protected void paintComponent(Graphics g0) {
				Graphics2D g = (Graphics2D) g0.create();
				g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g.setFont(getFont());
				int y = g.getFontMetrics().getAscent();
				g.setColor(Color.black);
				g.drawString(getText(), 1, y + 1);
				g.setColor(getForeground());
				g.drawString(getText(), 0, y);
				g.dispose();
			}
		};
		l.setFont(bold ? l.getFont().deriveFont(java.awt.Font.BOLD) : MenuTheme.TEXT_FONT);
		l.setForeground(color);
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}
	private JLabel smallLabel(String text, Color color) { return shadowLabel(text, color, false); }

	/** The picture as a flat button: clicking it opens her report. */
	private JButton pictureButton(Ship ship0, java.awt.Image pic, int w, int h) {
		JButton b = new JButton(new ImageIcon(pic));
		b.setOpaque(false);
		b.setContentAreaFilled(false);
		b.setBorderPainted(false);
		b.setFocusPainted(false);
		b.setBorder(null);
		b.setPreferredSize(new Dimension(w, h));
		b.setMaximumSize(new Dimension(w, h));
		b.setAlignmentX(LEFT_ALIGNMENT);
		b.setToolTipText("Ship's report");
		b.addActionListener(this);
		infoButtons.put(b, ship0);
		return b;
	}
	/** Board/Dock and Info, side by side. */
	private JPanel buttonRow(Ship ship0, int w, int h) {
		FtlButton board = new FtlButton(ship0.isBoarded() ? "Dock" : "Board", FtlFont.BODY, w, h);
		board.setToolTipText(ship0.isBoarded() ? "Dock her here until she's needed again" : "Take command: she becomes the ship you fly in FTL");
		boardButtons.put(board, ship0);
		board.addActionListener(this);
		if (homeplanet.core.FtlDock.active()) { board.setEnabled(false); board.setToolTipText(GameGuard.CLOSE_FTL); } // she can't change ships mid-flight (5.29)
		FtlButton infobtn = new FtlButton("Info", FtlFont.BODY, w, h);
		infobtn.setToolTipText("Ship's report, and rename her");
		infobtn.addActionListener(this);
		infoButtons.put(infobtn, ship0);
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(board);
		row.add(Box.createRigidArea(new Dimension(6, 0)));
		row.add(infobtn);
		row.setMaximumSize(row.getPreferredSize());
		return row;
	}
	private static String beacons(Ship s) {
		SavedGameState gs = s.save();
		return gs == null ? "save can't be read" : gs.getTotalBeaconsExplored() + (gs.getTotalBeaconsExplored() == 1 ? " beacon explored" : " beacons explored");
	}
	private static boolean offStation(Ship s) {
		return s.save() != null && !Vault.get().mayTrade(s);
	}
	/** One docked ship: name, beacons, picture, Board and Info. */
	private JPanel shipPanel(Ship ship0) {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		p.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 0, 14, 0));
		p.add(new FtlButton.Text(ship0.name, FtlFont.BODY, Color.white, CELL_W - 12));
		p.add(smallLabel(beacons(ship0), MenuTheme.GREY_GREEN));
		boolean off = offStation(ship0);
		JLabel away = smallLabel(off ? "Not within range of a station" : " ", MenuTheme.ORANGE);
		if (off) away.setToolTipText("She must reach a beacon with a station before she can trade.");
		p.add(away);
		p.add(Box.createRigidArea(new Dimension(1, 4)));
		p.add(pictureButton(ship0, shipPicture(ship0, 150, 86, off), 154, 90));
		p.add(Box.createRigidArea(new Dimension(1, 6)));
		p.add(buttonRow(ship0, 74, 26));
		return p;
	}
	/** The boarded ship: header and name above, a large picture, Dock and Info below. */
	/** The Aboard heading (with the inbox and reputation when they go there), above the boarded ship's berth. */
	private JPanel aboardRow;
	private JPanel berthPanel(Ship ship0) {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		JPanel head = new JPanel();
		head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
		head.setOpaque(false);
		head.setAlignmentX(LEFT_ALIGNMENT);
		int inboxW = inboxWidth();
		// her heading stands apart, from the left margin as the Docked one does (the Space Dock lays it out above her)
		flipBtn = null;
		if (homeplanet.core.FtlDock.active()) { // FTL docked: the icon that flips its place to the docked ships and back (heromedel, 5.32)
			flipBtn = new FlipButton();
			flipBtn.setToolTipText(homeplanet.core.FtlDock.aside() ? "Back to FTL" : "Show the Space Dock (FTL waits behind it)");
			flipBtn.addActionListener(this);
		}
		int flipW = flipBtn == null ? 0 : FlipButton.SIZE + 8;
		aboardRow = withInbox(new FtlButton.Header("Aboard", BERTH_W - inboxW - flipW, true), true, flipBtn); // aboard her: the ship you're on
		head.add(new FtlButton.Text(ship0.name, FtlFont.BODY, Color.white, BERTH_W));
		head.add(smallLabel(beacons(ship0), MenuTheme.GREY_GREEN));
		boolean off = offStation(ship0);
		if (off) head.add(smallLabel("Not within range of a station", MenuTheme.ORANGE));
		head.setSize(head.getPreferredSize());
		p.add(head);
		p.add(Box.createRigidArea(new Dimension(1, BERTH_PIC_Y)));
		p.add(pictureButton(ship0, shipPicture(ship0, BERTH_W, BERTH_H, off), BERTH_W, BERTH_H));
		p.add(Box.createRigidArea(new Dimension(1, 10)));
		p.add(buttonRow(ship0, 110, 32));
		p.setSize(p.getPreferredSize());
		p.setToolTipText("The ship at your command, berthed at The Home Planet Station");
		return p;
	}
	/**
	 * A heading with the transmissions light at the end of its line (where the eye goes first) and the reputation to its
	 * right, if they go here (either may be off).
	 */
	private JPanel withInbox(FtlButton.Header header, boolean here) { return withInbox(header, here, null); }
	/** The same, with a small icon first (the docked flip, 5.32) before the inbox. */
	private JPanel withInbox(FtlButton.Header header, boolean here, JButton first) {
		JPanel row = new JPanel(new java.awt.BorderLayout(8, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(header, java.awt.BorderLayout.CENTER);
		if (here && repBtn == null && inboxBtn != null && first == null) row.add(inboxBtn, java.awt.BorderLayout.EAST);
		else if (here && (repBtn != null || inboxBtn != null || first != null)) { // the flip, the inbox, the reputation to its right
			JPanel both = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
			both.setOpaque(false);
			if (first != null) both.add(first);
			if (inboxBtn != null) both.add(inboxBtn);
			if (repBtn != null) both.add(repBtn);
			row.add(both, java.awt.BorderLayout.EAST);
		}
		row.setMaximumSize(row.getPreferredSize());
		return row;
	}
	/** The inbox's room in a heading, if there's an inbox (the reputation to its right reaches past the heading's end, so the title keeps its room). */
	private int inboxWidth() {
		return inboxBtn == null ? 0 : inboxBtn.getPreferredSize().width + 8;
	}
	/** The empty space left of her picture inside her berth (the picture is centred in it). */
	private static int pictureInset(JPanel berth) {
		for (java.awt.Component c : berth.getComponents()) {
			if (!(c instanceof JButton) || ((JButton) c).getIcon() == null) continue;
			return Math.max(0, (c.getPreferredSize().width - ((JButton) c).getIcon().getIconWidth()) / 2);
		}
		return 0;
	}
	/** A few of her particulars, shown to the left of her picture when there's room. */
	private JPanel statsPanel(Ship ship0) {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		SavedGameState g = ship0.save();
		Color k = MenuTheme.GREY_GREEN, v = MenuTheme.WHITE;
		if (g == null) {
			statRow(p, "Save", "unreadable", k, v);
			p.setSize(p.getPreferredSize());
			return p;
		}
		ShipBlueprint bp = DataManager.get().getShip(g.getPlayerShipBlueprintId());
		ShipState ship = g.getPlayerShip();
		if (bp != null) statRow(p, "Class", CommissionDialog.classOf(bp), k, v);
		statPair(p, "Sector", String.valueOf(g.getSectorNumber() + 1), "Visited", String.valueOf(homeplanet.vault.VoyageLog.visited(Vault.get(), ship0)), k, v); // visited: all her journeys
		statRow(p, "Crew", String.valueOf(ship.getCrewList().size()), k, v);
		statRow(p, "Hull", ship.getHullAmt() + (bp != null && bp.getHealth() != null ? " / " + bp.getHealth().amount : ""), k, v);
		statRow(p, "Scrap", String.valueOf(ship.getScrapAmt()), k, v);
		statRow(p, "Fuel", String.valueOf(ship.getFuelAmt()), k, v);
		statRow(p, "Missiles", String.valueOf(ship.getMissilesAmt()), k, v);
		statRow(p, "Drone parts", String.valueOf(ship.getDronePartsAmt()), k, v);
		p.setSize(p.getPreferredSize());
		return p;
	}
	/** A stat's label to its value, and one stat to the next (the column must still fit beside her picture). */
	private static final int STAT_GAP = 3, STAT_ROW_GAP = 3;
	/** Two stats side by side on one row (the column must fit beside her picture). */
	private void statPair(JPanel p, String key1, String value1, String key2, String value2, Color k, Color v) {
		JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 12, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		for (String[] kv : new String[][] {{key1, value1}, {key2, value2}}) {
			JPanel cell = new JPanel();
			cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
			cell.setOpaque(false);
			cell.add(smallLabel(kv[0], k));
			cell.add(Box.createRigidArea(new Dimension(1, STAT_GAP)));
			cell.add(new FtlButton.Text(kv[1], FtlFont.BODY, v, Math.min(STATS_W / 2, FtlFont.BODY.render(kv[1], v).getWidth() + 2)));
			row.add(cell);
		}
		row.setMaximumSize(new Dimension(STATS_W, row.getPreferredSize().height));
		p.add(row);
		p.add(Box.createRigidArea(new Dimension(1, STAT_ROW_GAP)));
	}
	private void statRow(JPanel p, String key, String value, Color k, Color v) {
		p.add(smallLabel(key, k));
		p.add(Box.createRigidArea(new Dimension(1, STAT_GAP)));
		int w = Math.min(STATS_W, FtlFont.BODY.render(value, v).getWidth() + 2); // one line, cut with "..." only if very long
		p.add(new FtlButton.Text(value, FtlFont.BODY, v, w));
		p.add(Box.createRigidArea(new Dimension(1, STAT_ROW_GAP)));
	}

	// ---- actions ----

	public void actionPerformed(ActionEvent ae) {
		JButton o = (JButton) ae.getSource();
		if (boardButtons.containsKey(o)) {
			Ship ship = boardButtons.get(o);
			String was = o.getText();
			java.awt.Cursor cursorWas = o.getCursor();
			busy(o, ship.isBoarded() ? "Docking..." : "Boarding...");
			try {
				if (ship.isBoarded()) dock();
				else board(ship);
			} finally { // the button is usually rebuilt by then; if not (FTL running, or a failure), it's itself again
				o.setText(was);
				if (o instanceof FtlButton) ((FtlButton) o).setHeld(false);
				o.setCursor(cursorWas);
				o.repaint();
				setCursor(null);
			}
		} else if (o == settingsBtn) {
			SettingsDialog.open(this);
			init(); // rules or the saves folder may have changed
		} else if (o == refreshBtn) {
			refresh();
		} else if (o == quartersBtn) {
			quarters();
		} else if (o == museumBtn && museumBtn != null) {
			parent.showMuseum();
		} else if (o == otherBtn) {
			otherOrders();
		} else if (o == repBtn && repBtn != null) {
			ReputationLogDialog.open(this);
		} else if (o == inboxBtn) {
			boolean go = InboxDialog.open(this);
			init();
			if (go) commissionShip();
		} else if (o == journeyBtn) {
			newJourney();
		} else if (o == commissionBtn) {
			commissionShip();
		} else if (o == expeditionsBtn) {
			if (HomePlanet.expeditionType == 0) ExpeditionsDialog.hire(this);
			else AssignmentsDialog.open(this);
			init(); // every return rebuilds, whatever the window reports
		} else if (o == salvageBtn) {
			salvageShip();
		} else if (o == disbandBtn) {
			disbandCurrentShip();
		} else if (o == designBtn && homeplanet.parser.Clearance.customReason() != null) {
			JOptionPane.showMessageDialog(this, homeplanet.parser.Clearance.customReason(), "Design Ship", JOptionPane.INFORMATION_MESSAGE);
		} else if (o == designBtn) {
			DesignListDialog.open(this);
		} else if (o == cargoBtn) {
			parent.showCargoBay(); // always opens: a boarded ship whose save can't be read is left out of it, and the note says why (5.81)
			String unreadable = CargoBayUI.unreadableNote();
			if (unreadable != null) JOptionPane.showMessageDialog(this, unreadable, "Cargo Bay", JOptionPane.WARNING_MESSAGE);
		} else if (o == commBtn) {
			parent.showLongRangeComm();
		} else if (o == launchBtn) {
			HomePlanet.launchFTL();
		} else if (o == dockLaunchBtn) {
			if (homeplanet.core.FtlDock.active()) askUndock();
			else launchDocked();
		} else if (o == flipBtn) {
			flip();
		} else if (infoButtons.containsKey(o)) {
			o.setFocusPainted(false);
			showShipInfo(infoButtons.get(o));
		}
	}

	// ---- FTL docked in the station window (heromedel, 5.29) ----

	private JPanel viewportPanel;
	private long dockStarted;
	private Dimension sizeBeforeDock, dockedSize;
	/** The room kept under FTL's viewport: the docked ships flip into its place instead (5.32). */
	private static final int DOCK_BELOW = 16;
	private final javax.swing.Timer dockWatch = new javax.swing.Timer(500, new ActionListener() { public void actionPerformed(ActionEvent e) { watchDock(); } });

	/** Where FTL sits: a dark screen in a gold frame, with a word while FTL's window is on its way. */
	private JPanel viewport() {
		final JPanel p = new JPanel() {
			@Override protected void paintComponent(Graphics g) {
				g.setColor(new Color(8, 10, 14));
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(MenuTheme.GOLD);
				g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
				g.setFont(MenuTheme.TEXT_FONT);
				java.awt.FontMetrics fm = g.getFontMetrics();
				if (!homeplanet.core.FtlDock.found()) {
					String s = "FTL is starting...";
					g.setColor(MenuTheme.GREY_GREEN);
					g.drawString(s, (getWidth() - fm.stringWidth(s)) / 2, getHeight() / 2);
					return;
				}
				// seen only when FTL has fallen behind the station (heromedel, 5.33): FTL's paused look, a click brings it back
				java.util.Random r = new java.util.Random(7);
				for (int i = 0; i < getWidth() * getHeight() / 2500; i++) {
					int c = 90 + r.nextInt(120);
					g.setColor(new Color(c, c, Math.min(255, c + 25)));
					g.fillRect(1 + r.nextInt(Math.max(1, getWidth() - 2)), 1 + r.nextInt(Math.max(1, getHeight() - 2)), 1 + r.nextInt(2), 1);
				}
				java.awt.image.BufferedImage word = FtlFont.MENU.render("UNPAUSE", Color.white);
				int sc = 3, ww = word.getWidth() * sc, wh = word.getHeight() * sc;
				g.drawImage(word, (getWidth() - ww) / 2, getHeight() / 2 - wh, ww, wh, null);
				String s = "Click to return to FTL";
				g.setColor(MenuTheme.GREY_GREEN);
				g.drawString(s, (getWidth() - fm.stringWidth(s)) / 2, getHeight() / 2 + fm.getHeight() + 6);
			}
		};
		p.setOpaque(true);
		p.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mousePressed(java.awt.event.MouseEvent e) {
			if (!homeplanet.core.FtlDock.found()) return;
			log.debug("FTL docked: Unpause clicked, FTL to the front");
			homeplanet.core.FtlDock.focus();
		} });
		p.addHierarchyBoundsListener(new java.awt.event.HierarchyBoundsAdapter() {
			@Override public void ancestorMoved(java.awt.event.HierarchyEvent e) { placeViewport(); }
			@Override public void ancestorResized(java.awt.event.HierarchyEvent e) { placeViewport(); }
		});
		return p;
	}
	/** FTL's window over the viewport, wherever the station's window is. */
	void placeViewport() {
		JPanel v = viewportPanel;
		if (v == null || !v.isShowing() || !homeplanet.core.FtlDock.active()) return;
		if (v.getWidth() <= 2 || v.getHeight() <= 2) return; // a rebuild's new viewport, not laid out yet: FTL shrank to nothing there a moment, every save (heromedel, 5.42)
		java.awt.Point at = v.getLocationOnScreen();
		homeplanet.core.FtlDock.place(new java.awt.Rectangle(at.x + 1, at.y + 1, v.getWidth() - 2, v.getHeight() - 2)); // inside the gold frame
	}
	/** The docked launch: FTL windowed, launched, and the Space Dock laid out around its viewport. */
	private void launchDocked() {
		try { homeplanet.core.FtlDock.prepareSettings(); }
		catch (IOException e) { HomePlanet.showErrorDialog("The Home Planet Station could not set FTL to windowed in its settings.ini:\n" + e.getMessage() + "\n\nFTL may start full screen; it will still be docked if it can be."); }
		if (!HomePlanet.launchFTL(true)) return;
		homeplanet.core.FtlDock.begin(javax.swing.SwingUtilities.getWindowAncestor(this));
		dockStarted = System.currentTimeMillis();
		ftlSeen = false;
		init();
		javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { fitWindow(); } });
		dockWatch.start();
	}
	/** The station's window grown to hold the viewport, if the screen has room (put back afterwards). */
	private void fitWindow() {
		if (parent != null && parent.isBorderless()) return; // full screen already: the viewport has the room there is
		JPanel v = viewportPanel;
		java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(this);
		if (v == null || w == null) return;
		java.awt.Point at = javax.swing.SwingUtilities.convertPoint(v, 0, 0, this);
		Dimension want = homeplanet.core.FtlDock.size(); // the size chosen, not what fits now
		int controlsW = getComponentCount() > 1 ? getComponent(1).getPreferredSize().width : 220;
		int needW = want.width + 30 + RefreshButton.SIZE + controlsW, needH = at.y + want.height + 2 + DOCK_BELOW; // as the layout wants it, so FTL fits exactly; nothing below it (5.32)
		int dw = Math.max(0, needW - getWidth()), dh = Math.max(0, needH - getHeight());
		if (dw == 0 && dh == 0) return;
		java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		if (sizeBeforeDock == null) sizeBeforeDock = w.getSize();
		w.setSize(Math.min(screen.width, w.getWidth() + dw), Math.min(screen.height, w.getHeight() + dh));
		dockedSize = w.getSize();
		if (w.getX() + w.getWidth() > screen.x + screen.width || w.getY() + w.getHeight() > screen.y + screen.height) w.setLocation(screen.x + (screen.width - w.getWidth()) / 2, screen.y + (screen.height - w.getHeight()) / 2);
	}
	/** Every half second while docked: find FTL's window, keep it placed, and notice when it's gone. */
	private void watchDock() {
		if (!homeplanet.core.FtlDock.active()) { dockWatch.stop(); return; }
		keepDockedSize();
		if (!homeplanet.core.FtlDock.found()) {
			checkFtlRunning(); // closed before its window turned up; once it's found, its window going says FTL closed (5.32: tasklist can misread)
			homeplanet.core.FtlDock.Found f = homeplanet.core.FtlDock.find();
			if (f == homeplanet.core.FtlDock.Found.DOCKED) { placeViewport(); liftFtl(); if (viewportPanel != null) viewportPanel.repaint(); return; }
			if (f == homeplanet.core.FtlDock.Found.FULLSCREEN) { // left as it is: say what to change in FTL, once
				endDock();
				JOptionPane.showMessageDialog(this, "FTL is set to full screen. To play it docked, set Options > Fullscreen to Off in FTL, then launch docked again.",
						"Play FTL, docked", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			long wait = HomePlanet.launchThroughSteam ? 300000 : 180000; // Steam may start, update or sync first
			if (System.currentTimeMillis() - dockStarted > wait) { log.info("FTL docked: no FTL window after {} minutes; it runs as a normal window", wait / 60000); endDock(); }
			return;
		}
		if (!homeplanet.core.FtlDock.alive()) { endDock(); return; } // FTL closed
		if (homeplanet.core.FtlDock.inFront()) liftFtl(); // Alt+Tab straight to FTL: the station comes up under it, no other program round it (heromedel, 5.43)
	}
	/**
	 * FTL starting up can put the station's window back to its old size (seen under Wine, 5.32): for its first 45 seconds
	 * the docked size is kept. After that, a window the player resizes stays as they leave it.
	 */
	private void keepDockedSize() {
		java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(this);
		if (dockedSize == null || w == null || System.currentTimeMillis() - dockStarted > 45000) return;
		if (w.getWidth() >= dockedSize.width && w.getHeight() >= dockedSize.height) return;
		log.debug("FTL docked: the station's window was shrunk while FTL started; its docked size again");
		w.setSize(Math.max(w.getWidth(), dockedSize.width), Math.max(w.getHeight(), dockedSize.height));
	}
	private long lastRunCheck;
	/** FTL has been seen running since this docked launch (Cloud-C-BugsandFeedback's handoff, 5.33). */
	private volatile boolean ftlSeen;
	private volatile boolean checkingRun;
	/** Every few seconds, off the Swing thread, until FTL's window turns up: FTL seen running, then gone, ends the docked view (it closed first). */
	private void checkFtlRunning() {
		long now = System.currentTimeMillis();
		if (checkingRun || now - lastRunCheck < 3000) return;
		lastRunCheck = now;
		checkingRun = true;
		new Thread(new Runnable() { public void run() {
			boolean running = GameGuard.isFtlRunning();
			checkingRun = false;
			if (running) { ftlSeen = true; return; }
			// "not running" counts only once FTL has been seen: through Steam, starting, updating or syncing can take a while (5.33)
			if (ftlSeen) javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { if (homeplanet.core.FtlDock.active()) endDock(); } });
		} }, "ftl-dock-check").start();
	}
	/** FTL closed (or never turned up): the Space Dock as usual, at its old size, taking stock. */
	private void endDock() {
		log.info("FTL docked: the docked view ends (FTL closed, or its window not found)");
		endDockView();
		init();
	}
	/** The docked view ended: FTL let go, the window at its old size (the rebuild follows). */
	private void endDockView() {
		homeplanet.core.FtlDock.end();
		dockWatch.stop();
		java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(this);
		if (w != null && sizeBeforeDock != null && (parent == null || !parent.isBorderless())) w.setSize(sizeBeforeDock);
		sizeBeforeDock = null;
		dockedSize = null;
	}
	/** The X while FTL is docked (heromedel, 5.57): Cancel, Remove from Dock (FTL keeps running in its own window), or Close (confirmed first). */
	private void askUndock() {
		Object[] options = {"Cancel", "Remove from Dock", "Close"};
		int c = JOptionPane.showOptionDialog(this, "FTL is docked. Remove it from the dock and keep playing in its own window, or close FTL?",
				"Docked FTL", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (c == 1) {
			log.info("FTL docked: removed from the dock by the player");
			homeplanet.core.FtlDock.release();
			endDockView();
			init();
		} else if (c == 2) {
			Object[] sure = {"Cancel", "Close FTL"};
			int d = JOptionPane.showOptionDialog(this, "Close FTL?\n\nAnything FTL did not or does not save on its own may be lost.",
					"Close FTL", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, sure, sure[0]);
			if (d != 1) return;
			if (!homeplanet.core.FtlDock.close()) { endDock(); return; } // no window to ask: FTL is gone already
			new Thread(new Runnable() { public void run() { // FTL gone a few seconds later ends the docked view; still open, it's left be
				boolean open = true;
				for (int i = 0; i < 10 && open; i++) {
					try { Thread.sleep(700); } catch (InterruptedException e) { return; }
					open = GameGuard.isFtlRunning();
				}
				final boolean still = open;
				javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() {
					if (!homeplanet.core.FtlDock.active()) return;
					if (!still) { endDock(); return; }
					JOptionPane.showMessageDialog(SpaceDockUI.this, "FTL is still open (it may be asking something itself). The Home Planet Station has left it be.",
							"Close FTL", JOptionPane.INFORMATION_MESSAGE);
				} });
			} }, "ftl-close-check").start();
		}
	}
	private static int lastUnread = -1;
	/**
	 * A new letter while FTL is docked: a small notice in the viewport's top right that doesn't take the keyboard, so
	 * FTL keeps it. A click opens the inbox; left alone, it goes after a few seconds.
	 */
	private void noticeOverFtl() {
		JPanel v = viewportPanel;
		if (v == null || !v.isShowing()) return;
		homeplanet.parser.Transmissions.Message newest = null;
		for (homeplanet.parser.Transmissions.Message m : homeplanet.parser.Transmissions.load()) if (!m.read && !m.archived) newest = m;
		String what = newest == null ? "A new message" : "From " + newest.from + ": " + newest.subject;
		final javax.swing.JWindow w = new javax.swing.JWindow(javax.swing.SwingUtilities.getWindowAncestor(this));
		w.setFocusableWindowState(false); // FTL keeps the keyboard
		w.setAlwaysOnTop(true);
		JLabel l = new JLabel("<html><font color='" + MenuTheme.HTML_GOLD + "'><b>Incoming transmission</b></font><br>" + homeplanet.parser.XmlText.text(what) + "<br><font color='"
				+ MenuTheme.HTML_GREY_GREEN + "'>Click to open the inbox</font></html>");
		l.setFont(MenuTheme.TEXT_FONT);
		l.setForeground(new Color(0xdc, 0xe4, 0xeb));
		l.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(MenuTheme.GOLD, 2), javax.swing.BorderFactory.createEmptyBorder(8, 12, 8, 12)));
		l.setOpaque(true);
		l.setBackground(new Color(16, 20, 26));
		l.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		l.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mousePressed(java.awt.event.MouseEvent e) {
			w.dispose();
			if (inboxBtn != null) inboxBtn.doClick();
		} });
		w.getContentPane().add(l);
		w.pack();
		w.setSize(Math.min(w.getWidth(), 420), w.getHeight());
		java.awt.Point at = v.getLocationOnScreen();
		w.setLocation(at.x + v.getWidth() - w.getWidth() - 12, at.y + 12);
		w.setVisible(true);
		javax.swing.Timer t = new javax.swing.Timer(8000, new ActionListener() { public void actionPerformed(ActionEvent e) { w.dispose(); } });
		t.setRepeats(false);
		t.start();
	}
	/** The docked launch's icon centred on Launch FTL's height (they sit in different panels). */
	private void alignDockLaunch() {
		JButton d = dockLaunchBtn;
		if (d == null || d.getParent() == null || !launchBtn.isShowing()) return;
		java.awt.Point p = javax.swing.SwingUtilities.convertPoint(launchBtn, 0, 0, d.getParent());
		d.setLocation(d.getX(), p.y + (launchBtn.getHeight() - d.getHeight()) / 2);
	}
	/** The docked launch's icon: a small screen, beside Launch FTL. */
	static final class DockLaunchButton extends FtlButton {
		DockLaunchButton() { super("", FtlFont.MENU, RefreshButton.SIZE, RefreshButton.SIZE); }
		@Override protected void paintComponent(Graphics g0) {
			super.paintComponent(g0);
			Graphics2D g = (Graphics2D) g0.create();
			g.setColor(isEnabled() ? (getModel().isRollover() ? TEXT_HOT : TEXT) : Color.gray);
			g.setStroke(new BasicStroke(2f));
			int w = getWidth(), h = getHeight();
			if (homeplanet.core.FtlDock.active()) { // FTL docked: an X, to undock or close it (heromedel, 5.57)
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.drawLine(w / 2 - 7, h / 2 - 7, w / 2 + 7, h / 2 + 7);
				g.drawLine(w / 2 + 7, h / 2 - 7, w / 2 - 7, h / 2 + 7);
				g.dispose();
				return;
			}
			g.drawRect(w / 2 - 9, h / 2 - 8, 18, 12); // the screen
			g.fillRect(w / 2 - 6, h / 2 - 5, 12, 6);
			g.drawLine(w / 2, h / 2 + 4, w / 2, h / 2 + 7); // its stand
			g.drawLine(w / 2 - 5, h / 2 + 8, w / 2 + 5, h / 2 + 8);
			g.dispose();
		}
	}

	/** The flip (5.32): the docked ships in FTL's place, FTL hidden but running; again, FTL back where it was. */
	private void flip() {
		boolean ships = !homeplanet.core.FtlDock.aside();
		log.debug("FTL docked: {}", ships ? "the Space Dock shown as without FTL" : "back to FTL");
		homeplanet.core.FtlDock.setAside(ships);
		init(); // the Space Dock as usual (her berth, the saucer, the docked ships), or FTL's viewport again; the rebuild lifts FTL back
	}
	/** The flip's icon: a small ship (to show the Space Dock as without FTL), or a small screen (back to FTL). */
	static final class FlipButton extends FtlButton {
		static final int SIZE = 26;
		FlipButton() { super("", FtlFont.MENU, SIZE, SIZE); }
		@Override protected void paintComponent(Graphics g0) {
			super.paintComponent(g0);
			Graphics2D g = (Graphics2D) g0.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(getModel().isRollover() ? TEXT_HOT : TEXT);
			g.setStroke(new BasicStroke(2f));
			int w = getWidth(), h = getHeight();
			if (homeplanet.core.FtlDock.aside()) { // back to FTL: the docked launch's screen
				g.drawRect(w / 2 - 8, h / 2 - 7, 16, 10);
				g.fillRect(w / 2 - 5, h / 2 - 4, 10, 4);
				g.drawLine(w / 2, h / 2 + 3, w / 2, h / 2 + 6);
				g.drawLine(w / 2 - 4, h / 2 + 7, w / 2 + 4, h / 2 + 7);
			} else { // the docked ships: a hull, nose to the right
				java.awt.Polygon hull = new java.awt.Polygon(new int[] {w / 2 - 8, w / 2 + 3, w / 2 + 9, w / 2 + 3, w / 2 - 8, w / 2 - 6},
						new int[] {h / 2 - 5, h / 2 - 5, h / 2, h / 2 + 5, h / 2 + 5, h / 2}, 6);
				g.fill(hull);
				g.fillRect(w / 2 - 10, h / 2 - 7, 6, 3); // engines
				g.fillRect(w / 2 - 10, h / 2 + 5, 6, 3);
			}
			g.dispose();
		}
	}

	/** Reads the vault and every changed file again (after playing FTL, or changing files by hand). */
	/** Captain's Quarters: a day's rest, asked first (No to begin with), then the station's round and the screen rebuilt. */
	private void quarters() {
		Vault v = Vault.get();
		if (homeplanet.core.FtlDock.active()) { // aboard a ship, not at the station: the log only (5.29)
			Object[] only = {"Cancel", "Captain's Log"};
			int pick = JOptionPane.showOptionDialog(this, "You're aboard a ship, not at the station: no rest until you're back.\n" + GameGuard.CLOSE_FTL, "Captain's Quarters",
					JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, only, only[0]);
			if (pick == 1) CaptainsLogDialog.open(this);
			return;
		}
		Object[] options = {"Cancel", "Rest", "Captain's Log"}; // heromedel: Cancel, Rest, Captain's Log
		javax.swing.JTextArea t = new javax.swing.JTextArea(homeplanet.parser.Rest.question(v));
		t.setEditable(false); t.setOpaque(false); t.setFont(MenuTheme.TEXT_FONT);
		int pick = JOptionPane.showOptionDialog(this, t, "Captain's Quarters", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (pick == 2) { CaptainsLogDialog.open(this); quarters(); return; } // read, then back to the question
		if (pick != 1) return; // Cancel, or closed
		try { homeplanet.parser.Rest.rest(v); }
		catch (IOException e) { HomePlanet.showErrorDialog("The Home Planet Station could not record the day's rest:\n" + e.getMessage()); return; }
		init();
		timeRound(false);
	}
	/** The refresh button: a small square with the big buttons' rim and two chasing arrows, lit under the pointer like them. */
	static final class RefreshButton extends FtlButton {
		static final int SIZE = 30;
		RefreshButton() { super("", FtlFont.MENU, SIZE, SIZE); }
		@Override protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			int w = getWidth() - 2, h = getHeight() - 2, c = 5;
			java.awt.Polygon p = new java.awt.Polygon(new int[] {c, w - c, w, w, w - c, c, 0, 0}, new int[] {0, 0, c, h - c, h, h, h - c, c}, 8);
			p.translate(1, 1);
			javax.swing.ButtonModel m = getModel();
			boolean hot = isEnabled() && m.isRollover() && !m.isPressed();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g.setColor(hot ? HOT : (m.isPressed() ? FILL_DOWN : FILL));
			g.fillPolygon(p);
			g.setStroke(new BasicStroke(2f));
			g.setColor(LINE);
			g.drawPolygon(p);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(hot ? TEXT_HOT : TEXT);
			g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			int cx = getWidth() / 2, cy = getHeight() / 2, r = 8;
			g.draw(new java.awt.geom.Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 30, 130, java.awt.geom.Arc2D.OPEN));
			g.draw(new java.awt.geom.Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 210, 130, java.awt.geom.Arc2D.OPEN));
			double a1 = Math.toRadians(30), x1 = cx + r * Math.cos(a1), y1 = cy - r * Math.sin(a1);
			java.awt.geom.Path2D q = new java.awt.geom.Path2D.Double(); q.moveTo(x1 + 1, y1 - 6); q.lineTo(x1 + 2, y1 + 1); q.lineTo(x1 - 5, y1 - 1); q.closePath(); g.fill(q);
			double a2 = Math.toRadians(210), x2 = cx + r * Math.cos(a2), y2 = cy - r * Math.sin(a2);
			q = new java.awt.geom.Path2D.Double(); q.moveTo(x2 - 1, y2 + 6); q.lineTo(x2 - 2, y2 - 1); q.lineTo(x2 + 5, y2 + 1); q.closePath(); g.fill(q);
			g.dispose();
		}
	}
	void refresh() {
		log.debug("Space Dock: Refresh");
		try {
			Vault.get().reload();
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not take stock of the fleet again:\n" + e);
		}
		homeplanet.core.Music.refresh();
		SpaceDockScrollPane.reroll(parent, false); // now and then, different ships leaving the station
		init();
		HistoryLog.loaded("refresh");
		if (homeplanet.core.FtlDock.active()) new Thread(new Runnable() { public void run() { // and on Refresh, FTL not running at all (5.57)
			if (homeplanet.core.FtlDock.found() || !ftlSeen || GameGuard.isFtlRunning()) return; // once its window is found, the window says (the build checks it: tasklist can misread); a launch still starting isn't ended
			javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { if (homeplanet.core.FtlDock.active()) endDock(); } });
		} }, "ftl-refresh-check").start();
	}

	/** The transmissions icon: an antenna, and a green light with the unread count. */
	/**
	 * The transmissions light, at the end of the Docked heading (or the Aboard heading, with a ship boarded): a mast and dish, and with anything unread a green
	 * light and "N NEW" in gold, and a small hop every few seconds until the inbox is opened.
	 */
	private static final class TransmissionButton extends JButton {
		private static final int HOP_EVERY = 3000, HOP_MS = 360, HOP_PX = 6;
		private final int unread;
		private javax.swing.Timer every, frames;
		private long hopStart = 0;
		TransmissionButton(int unread) {
			this.unread = unread;
			setPreferredSize(new Dimension(unread > 0 ? 104 : 46, 38));
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			setToolTipText(unread == 0 ? "Transmissions from The Federation Home Planet" : unread + " new transmission" + (unread == 1 ? "" : "s") + " from The Federation Home Planet");
		}
		@Override public void addNotify() {
			super.addNotify();
			if (unread == 0 || every != null) return;
			frames = new javax.swing.Timer(30, new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					if (System.currentTimeMillis() - hopStart >= HOP_MS) frames.stop();
					repaint();
				}
			});
			every = new javax.swing.Timer(HOP_EVERY, new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) { hopStart = System.currentTimeMillis(); frames.restart(); }
			});
			every.setInitialDelay(800);
			every.start();
		}
		@Override public void removeNotify() { // the Space Dock is rebuilt: this one's timers stop with it
			if (every != null) every.stop();
			if (frames != null) frames.stop();
			every = null;
			super.removeNotify();
		}
		private int hop() {
			long t = System.currentTimeMillis() - hopStart;
			return t < 0 || t >= HOP_MS ? 0 : (int) Math.round(HOP_PX * Math.sin(Math.PI * t / HOP_MS));
		}
		@Override protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			int h = getHeight() - HOP_PX; // room above for the hop
			g.translate(0, HOP_PX - hop());
			boolean hot = getModel().isRollover();
			Color line = hot ? new Color(255, 230, 160) : unread > 0 ? FtlButton.GOLD : new Color(214, 230, 222);
			g.setColor(new Color(20, 28, 34, 220));
			g.fillRoundRect(1, 1, getWidth() - 3, h - 3, 8, 8);
			g.setColor(line);
			g.setStroke(new java.awt.BasicStroke(1.8f));
			g.drawRoundRect(1, 1, getWidth() - 3, h - 3, 8, 8);
			// a mast with a dish, and waves
			int cx = 14, cy = h / 2;
			g.drawLine(cx, cy - 2, cx, h - 6);
			g.drawLine(cx - 5, h - 6, cx + 5, h - 6);
			g.fillOval(cx - 2, cy - 5, 5, 5);
			g.drawArc(cx - 7, cy - 10, 14, 14, 30, 120);
			g.drawArc(cx - 11, cy - 14, 22, 22, 30, 120);
			// the light, and how many are new
			int lx = 28, ly = cy - 6;
			g.setColor(unread > 0 ? new Color(70, 220, 90) : new Color(60, 80, 70));
			g.fillOval(lx, ly, 12, 12);
			if (unread > 0) {
				String n = (unread > 99 ? "99+" : String.valueOf(unread)) + " NEW";
				java.awt.image.BufferedImage t = FtlFont.MENU.render(n, FtlButton.GOLD);
				int tw = Math.min(t.getWidth(), getWidth() - lx - 20);
				g.drawImage(t, lx + 17, (h - 2 - t.getHeight()) / 2 + 1, tw, t.getHeight(), null);
			}
			g.dispose();
		}
	}

	/**
	 * The career's reputation with The Federation Home Planet, as plain text in gold (red below zero) to the inbox's
	 * right: "REP: 179". Its tooltip has the latest changes, and clicking opens the Career Reputation Log.
	 */
	private final class ReputationButton extends JButton {
		private final java.awt.image.BufferedImage text;
		ReputationButton(int total) {
			text = FtlFont.MENU.render("REP: " + (total < 0 ? "-" + (-total) : String.valueOf(total)), total < 0 ? MenuTheme.RED : FtlButton.GOLD);
			setPreferredSize(new Dimension(text.getWidth() + 4, 38));
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			StringBuilder tip = new StringBuilder("<html>Your standing with The Federation Home Planet: earned by your ships' service, lost by their losses."
					+ "<br>Click for the Career Reputation Log.");
			java.util.List<String> recent = homeplanet.vault.Reputation.recent(Vault.get(), 5);
			if (!recent.isEmpty()) tip.append("<br><br><b>Latest:</b>");
			for (String r : recent) tip.append("<br>").append(homeplanet.parser.XmlText.text(r.length() > 18 ? r.substring(18) : r));
			setToolTipText(tip.append("</html>").toString());
		}
		@Override protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			int h = getHeight() - 6; // level with the inbox's box (it keeps room above for its hop)
			if (getModel().isRollover()) g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.8f));
			g.drawImage(text, 2, 6 + (h - 2 - text.getHeight()) / 2 + 1, null);
			g.dispose();
		}
	}

	/** Other...: the station's rarely used orders, in a window of their own. */
	private void otherOrders() {
		java.util.List<OtherOrdersDialog.Order> orders = new java.util.ArrayList<OtherOrdersDialog.Order>();
		orders.add(new OtherOrdersDialog.Order("Recover a ship", "For when a ship is lost to a bug or a malfunction: restores her from the station's last record of her.",
				HomePlanet.immersiveMode ? "Immersive Mode: ships lost or destroyed stay gone." : null,
				new Runnable() { public void run() { recoverShip(); } }, false));
		orders.add(new OtherOrdersDialog.Order("Clean up blueprints", "Remove old blueprints no ship uses any more from the Federation Home Planet Mod. Rarely needed.",
				null, new Runnable() { public void run() { BlueprintCleanup.run(SpaceDockUI.this); } }, false));
		Vault v = Vault.get();
		boolean waiting = v.freeCommandOpen();
		orders.add(new OtherOrdersDialog.Order("Plead for New Ship", "Ask The Federation Home Planet for a new ship, paid for with the Cargo Hold or against your reputation.",
				!HomePlanet.commissionCosts() ? "commissioning is free (Settings, Rules): Commission a new ship instead."
						: waiting ? "a ship's order is already waiting for you at Commission." : null,
				new Runnable() { public void run() { plead(); } }, true));
		final File last = v.freeCommandForfeit() ? v.lastSurrender() : null;
		if (last != null) { // an old Report for Reassignment, its ship not yet taken
			orders.add(new OtherOrdersDialog.Order("Undo Reassignment", "Take back the Cargo Hold and hulls surrendered in the last report for reassignment.",
					HomePlanet.immersiveMode ? "Immersive Mode: a report for reassignment is final." : null,
					new Runnable() { public void run() { undoReassignment(last); } }, false));
		} else if (waiting && v.freeCommandReassigned()) {
			orders.add(new OtherOrdersDialog.Order("Withdraw Plea", "Cancel the new ship's order waiting at Commission. Nothing was taken for it yet.",
					null, new Runnable() { public void run() { withdrawPlea(); } }, false));
		}
		if (!homeplanet.comm.Exchange.unfinished().isEmpty()) {
			orders.add(new OtherOrdersDialog.Order("Unfinished trades", "Long Range Comm. trades a lost link left unsettled: what you gave is held until they're settled.",
					null, new Runnable() { public void run() { LongRangeCommUI.reviewUnfinished(SpaceDockUI.this); init(); } }, true));
		}
		OtherOrdersDialog.open(this, orders);
	}
	/** What an empty shipyard grants, in words. */
	private static String freeShipWords() {
		return homeplanet.parser.FreeCommand.words(homeplanet.parser.FreeCommand.ship());
	}
	/** HR2: surrender the storage hold and the Junkyard for a free new command, then open Commission. */
	/**
	 * Plead for New Ship: The Federation Home Planet agrees to send one, whatever is at the Space Dock. Nothing is taken
	 * now: at Commission the captain picks her from what the plea offers, and pays with the Cargo Hold or (with
	 * Reputation on) against the career's reputation.
	 */
	void plead() {
		Vault v = Vault.get();
		String offered = homeplanet.parser.FreeCommand.offered(homeplanet.core.Economy.reassignment());
		boolean rep = homeplanet.core.Economy.repForJourneysAndPleas();
		String message = "Plead for a new ship?\n\n"
				+ "You put your case to The Federation Home Planet: one more ship, and you'll bring her home. They listen.\n"
				+ "They will send " + offered + ". Her order will wait for you at Commission.\n\n"
				+ "Nothing is taken now. When you commission her, you choose how to pay:\n"
				+ "  - Give up the Cargo Hold: everything in it but the crew, at what it would sell for (the Junkyard isn't touched).\n"
				+ (rep ? "  - Keep the Cargo Hold, and answer for her with your reputation.\n"
						+ "Whatever the hold doesn't cover of her value, " + homeplanet.core.Economy.share(homeplanet.core.Economy.pleaPercent()) + " of it comes off your reputation.\n"
						: homeplanet.vault.Reputation.shown() ? "" // How Reputation Can be Used: Only as a score
						: "  (With the Reputation rule on, you could keep the Cargo Hold and answer for her with your reputation.)\n")
				+ "\nUntil she's commissioned, the plea can be withdrawn (Other... > Withdraw Plea).";
		Object[] options = {"Plead", "Cancel"};
		if (JOptionPane.showOptionDialog(this, message, "Plead for New Ship", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]) != 0) return;
		v.plead();
		init();
		String rank = homeplanet.parser.Transmissions.rank();
		if (HomePlanet.immersiveNotifications()) {
			JOptionPane.showMessageDialog(null, "Your plea is heard, " + rank + ". The order for your new ship is in Transmissions.", "Plead for New Ship", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JOptionPane.showMessageDialog(null, "Your plea is heard, " + rank + ". The shipyard stands ready to build your new ship.", "Plead for New Ship", JOptionPane.INFORMATION_MESSAGE);
	}
	/** Withdraw Plea: the order waiting at Commission is cancelled (nothing was taken for it). */
	void withdrawPlea() {
		if (!HomePlanet.confirmNo(this, "Withdraw your plea for a new ship?\n\nHer order at Commission is cancelled. Nothing was taken for it.", "Withdraw Plea")) return;
		try {
			Vault.get().withdrawPlea();
			homeplanet.parser.Transmissions.pleaWithdrawn(); // her order leaves the inbox, and the Shipyard says so
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not withdraw the plea:\n" + e.getMessage());
		}
		init();
	}
	void undoReassignment(File dir) {
		String hulls;
		try { hulls = String.join(", ", Vault.get().surrenderedNames(dir)); } catch (IOException e) { hulls = "?"; }
		if (!HomePlanet.confirmNo(this, "Take back what was surrendered in the last report for reassignment?\n\n"
				+ "The Cargo Hold returns as it was, and these hulls return to the Junkyard: " + (hulls.isEmpty() ? "(none)" : hulls) + ".\n"
				+ "The free command it earned is given up.", "Undo Reassignment")) return;
		try {
			Vault.get().undoSurrender(dir);
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not undo the report for reassignment:\n" + e.getMessage()
					+ "\n\nWhat was surrendered is still kept in " + dir);
			init();
			return;
		}
		init();
		JOptionPane.showMessageDialog(null, "The Cargo Hold and the Junkyard are as they were before your report.", "Undo Reassignment", JOptionPane.INFORMATION_MESSAGE);
	}
	/** Brings a destroyed or lost ship back to the Space Dock from her last kept version. */
	void recoverShip() {
		List<Vault.Departed> gone = Vault.get().recoverable();
		if (gone.isEmpty()) {
			JOptionPane.showMessageDialog(null, "The Home Planet Station has no records of a destroyed or lost ship to recover.\n"
					+ "(Scrapped ships can't be recovered: everything aboard them went into the Cargo Hold.)", "Recover a Ship", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String[] names = new String[gone.size()];
		java.text.SimpleDateFormat when = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
		for (int i = 0; i < names.length; i++) {
			Vault.Departed d = gone.get(i);
			names[i] = d.name + "  (" + (d.fate == Vault.Fate.LOST ? "lost in action" : "destroyed") + "; last kept " + when.format(new java.util.Date(d.last.lastModified())) + ")";
		}
		javax.swing.JComboBox<String> pick = new javax.swing.JComboBox<String>(names);
		JPanel panel = new JPanel(new java.awt.BorderLayout(0, 8));
		panel.add(new JLabel("<html>The Home Planet Station keeps the last version of every ship that leaves the fleet.<br>"
				+ "A recovered ship returns to the Space Dock as she was in that version: her crew, cargo and journey with her.<br>"
				+ "(A ship lost in action returns as she was when the station last saw her, before her final battle.)<br>&nbsp;</html>"), java.awt.BorderLayout.NORTH);
		panel.add(pick, java.awt.BorderLayout.CENTER);
		Object[] options = {"Recover", "Cancel"};
		int choice = JOptionPane.showOptionDialog(null, panel, "Recover a Ship", JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE, null, options, options[1]); // Cancel is the default
		if (choice != 0) return;
		Vault.Departed d = gone.get(pick.getSelectedIndex());
		Ship back;
		try {
			back = Vault.get().recover(d);
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not recover " + d.name + ":\n" + e.getMessage()
					+ "\n\nHer records are still in " + d.folder);
			init();
			return;
		}
		init();
		JOptionPane.showMessageDialog(null, back.name + " has been recovered. She waits at the Space Dock.", "Recover a Ship", JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * The clicked Board or Dock answers at once (heromedel, 6.02: "it still feels like its taking 1-2 full seconds"):
	 * its new word and the wait cursor painted now, before the work holds the screen.
	 */
	private void busy(JButton b, String text) {
		java.awt.Cursor wait = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR);
		setCursor(wait);
		b.setCursor(wait);
		if (FtlFont.BODY.render(text.toUpperCase(), Color.WHITE).getWidth() + 8 <= b.getWidth()) b.setText(text); // the small cards' buttons are too narrow: held down alone
		if (b instanceof FtlButton) ((FtlButton) b).setHeld(true);
		b.paintImmediately(0, 0, b.getWidth(), b.getHeight());
	}

	/** Takes command of a docked ship (docking the boarded one first). True if she was boarded. */
	public boolean board(Ship ship) {
		if (ship == null || ship.isBoarded()) return false;
		long g0 = System.nanoTime();
		if (!GameGuard.allows(this, "board a ship")) return false;
		long t0 = System.nanoTime();
		try {
			Vault.get().board(ship);
		} catch (IOException e) {
			HomePlanet.showErrorDialog(ship.name + " could not be boarded; command was not transferred:\n" + e.getMessage());
			init();
			return false;
		}
		long t1 = System.nanoTime();
		init();
		timed("Board", ship.name, g0, t0, t1);
		return true;
	}
	/** Docks the boarded ship. True if she was docked. */
	public boolean dock() {
		Ship b = Vault.get().boarded();
		if (b == null) return false;
		long g0 = System.nanoTime();
		if (!GameGuard.allows(this, "dock her")) return false;
		long t0 = System.nanoTime();
		try {
			Vault.get().dock();
		} catch (IOException e) {
			HomePlanet.showErrorDialog(b.name + " could not be docked:\n" + e.getMessage());
			init();
			return false;
		}
		long t1 = System.nanoTime();
		init();
		timed("Dock", b.name, g0, t0, t1);
		return true;
	}
	/** Where a Board's or Dock's time went, in the debug log (6.01): the check that FTL isn't running (6.02), the move, the look after it, and the Space Dock redrawn. */
	private void timed(String what, String name, long g0, long t0, long t1) {
		long check = (t0 - g0) / 1000000, move = (t1 - t0) / 1000000, all = (System.nanoTime() - g0) / 1000000;
		log.debug("{} {}: {} ms in all (the FTL check {} ms, the move {} ms, the look {} ms, the Space Dock redrawn {} ms)", what, name, all, check, move, lastLookMs,
				Math.max(0, all - check - move - lastLookMs));
	}

	/** Shows the ship report, with the option to rename the ship. */
	private void showShipInfo(Ship ship) {
		SavedGameState sgs = ship.save();
		if (sgs == null) {
			JOptionPane.showMessageDialog(this, "The Home Planet Station can't read " + ship.name + "'s save:\n" + ship.readError()
					+ (Retrofit.missingBlueprints(ship.file()).isEmpty() ? "" : "\n\nShe can't fly until The Home Planet Station sends the " + Retrofit.MOD_NAME + " to FTL via Slipstream (Settings > Mods > Patch mods)."),
					"Ship's report", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		int choice = reportChoice(ship, sgs, true);
		if (choice == 2) {
			ShipRecordsDialog.open(this, ship);
			init();
			return;
		}
		if (choice != 1) return;
		String oldName = sgs.getPlayerShipName();
		String newName = promptForName("What shall she be called?", "Rename Ship", oldName);
		if (newName == null || newName.equals(oldName)) return;
		if (ship.isBoarded() && !GameGuard.allows(this, "rename her")) return;
		try {
			homeplanet.vault.SpaceDock.rename(Vault.get(), ship, sgs, newName);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("She could not be renamed; her save could not be written:\n" + e);
			return;
		}
		JOptionPane.showMessageDialog(null, oldName + " is now known as " + newName + ".", "Rename Ship", JOptionPane.INFORMATION_MESSAGE);
		init();
	}
	/**
	 * The ship report window, the one both the Space Dock and the Cargo Bay open: picture, supplies, crew, weapons,
	 * drones, augments, and the retrofit/remodel status in the title. Returns true if the player pressed Rename.
	 */
	public boolean showReport(SavedGameState sgs) {
		return reportChoice(null, sgs, false) == 1;
	}
	/** The report, with Records (her kept versions and log) and a Stats tab when she's one of the fleet. 0 OK, 1 Rename, 2 Records. */
	private int reportChoice(Ship ship, SavedGameState sgs, boolean records) {
		boolean retrofitted = Retrofit.isRetrofitted(sgs.getPlayerShip());
		String bpId = sgs.getPlayerShip().getShipBlueprintId();
		String tag = !retrofitted ? "" : CompanionMod.isRemodelId(bpId) ? " (Remodeled " + CompanionMod.numberOf(bpId) + ")" : " (Retrofitted)";
		if (retrofitted && !Retrofit.inGame(sgs.getPlayerShip())) tag += " - needs the mod sent to FTL via Slipstream";
		Object[] options = records ? new Object[] {"OK", "Rename", "Records"} : new Object[] {"OK", "Rename"};
		java.awt.Component body = fitToScreen(shipSummaryPanel(sgs));
		if (ship != null) {
			javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
			tabs.addTab("Report", body);
			tabs.addTab("Stats", fitToScreen(shipStatsTab(ship, sgs)));
			tabs.setToolTipTextAt(1, "This journey, her whole service, and her crew's best");
			MenuTheme.markOpenTab(tabs);
			body = tabs;
		}
		return JOptionPane.showOptionDialog(null, body,
				String.format("Ship's report: %s%s", sgs.getPlayerShipName(), tag),
				JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
	}
	/** The panel as it is, or in a scroll pane when it's taller than the screen leaves room for (a big crew and cargo). */
	static java.awt.Component fitToScreen(JPanel panel) {
		int room = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height - 160; // title bar, buttons, margin
		java.awt.Dimension pref = panel.getPreferredSize();
		if (pref.height <= room) return panel;
		javax.swing.JScrollPane sp = new javax.swing.JScrollPane(panel, javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sp.setBorder(null);
		sp.getVerticalScrollBar().setUnitIncrement(16);
		sp.setPreferredSize(new java.awt.Dimension(pref.width + sp.getVerticalScrollBar().getPreferredSize().width, room));
		return sp;
	}
	/** Asks for a name. Returns the trimmed name, or null if cancelled or left blank. */
	public static String promptForName(String message, String title, String current) {
		while (true) {
			Object answer = JOptionPane.showInputDialog(null, message, title, JOptionPane.PLAIN_MESSAGE, null, null, current);
			if (answer == null) return null;
			String name = answer.toString().trim();
			if (name.isEmpty()) return null;
			if (name.length() > LONG_NAME) {
				int ok = JOptionPane.showConfirmDialog(null, "That's a long name. FTL may cut it off on screen.\nKeep it anyway?",
						title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
				if (ok != JOptionPane.YES_OPTION) { current = name; continue; }
			}
			return name;
		}
	}

	/** Moves the boarded ship's save into the junkyard, after a warning. Nothing is deleted. */
	void disbandCurrentShip() {
		Ship ship = Vault.get().boarded();
		if (ship == null || !ship.file().exists()) {
			JOptionPane.showMessageDialog(null, "No ship is at your command.\nBoard a ship before giving the order to decommission her.", "Decommission", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String message = "Decommission " + ship.name + "?\n\n"
				+ "Her crew will stand down and every scrap, supply and part aboard goes with her to the Junkyard.\n"
				+ "Should she ever be salvaged, her crew will return to their posts.";
		Object[] options = {"Decommission", "Cancel"};
		int choice = JOptionPane.showOptionDialog(null, message, "Decommission", JOptionPane.DEFAULT_OPTION,
				JOptionPane.WARNING_MESSAGE, null, options, options[1]); // Cancel is the default
		if (choice != 0) return;
		if (!GameGuard.allows(this, "decommission her")) return;
		try {
			Vault.get().disband();
		} catch (IOException e) {
			HomePlanet.showErrorDialog("She could not be towed to the Junkyard; her save was not moved:\n" + e.getMessage());
			return;
		}
		init();
	}

	/** Commission Ship: build a new ship save, then offer to board her. */
	void commissionShip() {
		Ship made = CommissionDialog.open(this);
		if (made == null) return;
		init();
		int r = JOptionPane.showConfirmDialog(null, "The Federation Home Planet has commissioned " + made.name + ". She waits at the Space Dock.\n\nBoard her now?",
				"Commission Ship", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (r == JOptionPane.YES_OPTION) board(made);
	}

	void newJourney() {
		Ship ship = Vault.get().boarded();
		log.debug("Space Dock: New Journey for {}", ship == null ? "(no ship aboard)" : ship.name);
		if (ship == null || !ship.file().exists()) {
			JOptionPane.showMessageDialog(null, "No ship is at your command.\nBoard a ship before setting out on a new journey.", "New Journey", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		ship.invalidate();
		SavedGameState gs = ship.save();
		if (gs == null) {
			HomePlanet.showErrorDialog("The Home Planet Station could not read her save:\n" + ship.file() + "\n\n" + ship.readError());
			return;
		}
		if (!Vault.get().mayJourney(ship)) {
			JOptionPane.showMessageDialog(null, gs.getPlayerShipName() + " is not within range of a station.\n"
					+ "The Federation Home Planet can only approve or assist in plotting a new journey from a beacon with a station.", "New Journey", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String message = "Prepare " + gs.getPlayerShipName() + " for a new journey?\n\n"
				+ "Crew, cargo and supplies stay aboard. The old star charts are wiped, and the ship sets out\n"
				+ "once more from the first sector with the rebel fleet in pursuit.\n\n"
				+ "How dangerous will this journey be?";
		Object[] options = {"Easy", "Normal", "Hard", "Cancel"};
		int choice = JOptionPane.showOptionDialog(null, message, "New Journey", JOptionPane.DEFAULT_OPTION,
				JOptionPane.WARNING_MESSAGE, null, options, options[3]); // Cancel is the default
		if (choice < 0 || choice > 2) return;
		int fee = homeplanet.core.Economy.journeyFee();
		int[] pay = {fee, 0}; // scrap, reputation
		if (fee > 0 && homeplanet.core.Economy.repForJourneysAndPleas()) { // scrap or reputation, never below zero (heromedel, 5.13)
			pay = RepPay.choose(this, "New Journey", "The Federation Home Planet charges to plot a new journey:", fee, Vault.get().storageScrap(),
					homeplanet.vault.Reputation.total(Vault.get()), "The Cargo Hold");
			if (pay == null) return;
		} else if (fee > 0) {
			int have = Vault.get().storageScrap();
			if (have < fee) {
				JOptionPane.showMessageDialog(null, "The Federation Home Planet charges " + fee + " scrap to plot a new journey, paid from the Cargo Hold,\n"
						+ "which holds " + have + ". Store more scrap in the Cargo Bay first.", "New Journey", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			if (!HomePlanet.confirmNo(this, "The Federation Home Planet charges " + fee + " scrap to plot a new journey,\npaid from the Cargo Hold (which holds " + have + "). Pay it?", "New Journey")) return;
		}
		if (!GameGuard.allows(this, "start her new journey")) return;
		net.blerf.ftl.constants.Difficulty[] diffs = {net.blerf.ftl.constants.Difficulty.EASY,
				net.blerf.ftl.constants.Difficulty.NORMAL, net.blerf.ftl.constants.Difficulty.HARD};
		SaveHelper.startJourney(gs, diffs[choice]);
		ShipState ps = gs.getPlayerShip();
		ps.setJumpChargeTicks(0);
		ps.setJumping(false);
		ps.setJumpAnimTicks(0);
		java.util.Iterator<CrewState> it = ps.getCrewList().iterator();
		while (it.hasNext()) if (!SaveHelper.isOwnCrew(it.next())) it.remove();
		try {
			homeplanet.vault.SpaceDock.newJourney(Vault.get(), ship, gs, (String) options[choice], fee, pay[0], pay[1], fee > 0 ? RepPay.words(pay) : null);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not save her new journey. Nothing was changed" + (pay[0] > 0 ? " (the fee stays in the Cargo Hold)" : "") + ":\n" + e.getMessage());
			return;
		}
		JOptionPane.showMessageDialog(null, gs.getPlayerShipName() + " is ready to depart: a new journey is plotted, Captain.",
				"New Journey", JOptionPane.INFORMATION_MESSAGE);
		init();
	}

	private boolean confirmIrreversible(String title, String message, String action) {
		Object[] options = {action, "Cancel"};
		return JOptionPane.showOptionDialog(null, message, title, JOptionPane.DEFAULT_OPTION,
				JOptionPane.WARNING_MESSAGE, null, options, options[1]) == 0; // Cancel is the default
	}

	/** Strips a junked ship: everything aboard goes to the Cargo Hold, then her save goes into her history. */
	void scrapShip(Ship wreckShip) {
		log.debug("Junkyard: Scrap {} ({})", wreckShip.name, wreckShip.id);
		SavedGameState wreck = wreckShip.save();
		if (wreck == null) {
			HomePlanet.showErrorDialog("The Home Planet Station can't read " + wreckShip.name + "'s save, so she can't be stripped:\n" + wreckShip.readError());
			return;
		}
		String name = wreckShip.name;
		if (!Vault.get().mayTrade(wreckShip)) { // the station rule, as for trading: a store, or just set out at The Home Planet Station
			JOptionPane.showMessageDialog(null, name + " is not within range of a station.\n"
					+ "The Home Planet Station cannot scrap her for supplies unless you salvage her and fly her to a beacon with a station first.", "Scrap Ship", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		// stripping her systems, where allowed, costs a fee for each, paid from the Cargo Hold and her own scrap together
		int systems = homeplanet.core.Economy.stripAllowed() ? SystemsPanel.strippable(wreck.getPlayerShip()) : 0;
		final int stripCost = systems * homeplanet.core.Economy.stripFee();
		final boolean strip;
		int[] pay = {stripCost, 0}; // scrap, reputation (heromedel, 5.13: either, never below zero)
		boolean repOn = homeplanet.core.Economy.repForVanillaBreaking();
		if (systems > 0) {
			int have = Vault.get().storageScrap() + wreck.getPlayerShip().getScrapAmt(), repHave = repOn ? homeplanet.vault.Reputation.total(Vault.get()) : 0;
			boolean can = stripCost <= have || repOn && repHave >= stripCost - Math.max(0, have);
			String fee = stripCost == 0 ? "free of charge" : "for " + stripCost + (repOn ? " scrap or reputation (" : " scrap (") + homeplanet.core.Economy.stripFee() + " a system)"
					+ (repOn ? "" : ", paid from the Cargo Hold");
			String message = "Strip " + name + " for parts?\n\nWeapons, drones, augments, cargo, supplies and crew will be moved to the Cargo Hold.\n"
					+ "Her systems can be stripped too, " + fee + ":\n" + SystemsPanel.scrapPreview(wreck.getPlayerShip())
					+ (can ? "" : "The Cargo Hold and her own scrap come to " + have + (repOn ? ", your reputation " + homeplanet.vault.Reputation.signed(repHave) : "")
							+ ": not enough to strip her systems.\n")
					+ "\nThe hull will be broken up and can never be recovered.";
			Object[] options = !can ? new Object[] {"Scrap, systems lost", "Cancel"}
					: new Object[] {stripCost == 0 ? "Scrap and strip" : "Scrap and strip (" + stripCost + ")", "Scrap, systems lost", "Cancel"};
			int c = JOptionPane.showOptionDialog(null, message, "Scrap Ship", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[options.length - 1]);
			if (c < 0 || c == options.length - 1) return;
			strip = options.length == 3 && c == 0;
			if (strip && stripCost > 0 && repOn) {
				pay = RepPay.choose(null, "Scrap Ship", "Stripping " + name + "'s systems costs", stripCost, have, repHave, "The Cargo Hold, with her own scrap,");
				if (pay == null) return;
			}
		} else {
			String aboard = "Everything aboard will be moved to the Cargo Hold. Her systems are lost with the hull"
					+ (homeplanet.core.Economy.stripAllowed() ? " (none of them can be stored).\n" : ".\n");
			if (!confirmIrreversible("Scrap Ship", "Strip " + name + " for parts?\n\n" + aboard
					+ "The hull will be broken up and can never be recovered.", "Scrap")) return;
			strip = false;
		}
		try {
			List<String> store = null, said = null;
			if (strip) { store = new java.util.ArrayList<String>(); said = SystemsPanel.scrapSystems(wreck.getPlayerShip(), store); } // the Refit tab's rules: what of hers can be stored
			homeplanet.vault.SpaceDock.scrap(Vault.get(), wreckShip, wreck, store, said, pay[0], pay[1]);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The order to scrap was called off. Nothing was changed:\n" + e);
			return;
		}
		init();
	}
	/**
	 * Trade In (half her value, less her hull damage) or Auction (a quarter to three quarters of her value, less her hull
	 * damage, the same bid for the same save): her scrap and crew go to the Cargo Hold, the payment with them, and
	 * she leaves the fleet with everything else aboard.
	 */
	void sellShip(Ship ship, boolean auction) {
		SavedGameState gs = ship.save();
		if (gs == null) {
			HomePlanet.showErrorDialog("The Home Planet Station can't read " + ship.name + "'s save, so she can't be sold:\n" + ship.readError());
			return;
		}
		String name = ship.name;
		if (!Vault.get().mayTrade(ship)) { // the station rule, as Scrap
			JOptionPane.showMessageDialog(null, name + " is not within range of a station.\n"
					+ "Buyers only come to a beacon with a store. Salvage her and fly her to one first.", auction ? "Auction" : "Trade In", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		ShipState from = gs.getPlayerShip();
		int value = homeplanet.parser.Pricing.saleValue(gs), missing = homeplanet.parser.Pricing.missingHull(from), broken = homeplanet.parser.Pricing.brokenBars(from);
		int damage = homeplanet.parser.Pricing.damage(from);
		int breaches = from.getBreachMap().size();
		List<String> parts = new java.util.ArrayList<String>();
		if (missing > 0) parts.add(missing + " points of missing hull");
		if (broken > 0) parts.add(broken + (broken == 1 ? " broken system bar" : " broken system bars"));
		if (breaches > 0) parts.add(breaches + (breaches == 1 ? " breach" : " breaches"));
		List<SystemType> core = homeplanet.parser.Pricing.missingCore(from);
		String coreNote = "";
		if (!core.isEmpty()) {
			List<String> names = new java.util.ArrayList<String>();
			for (SystemType t : core) names.add(DryDockShop.systemTitle(t.getId()));
			coreNote = "No " + String.join(" or ", names) + ": buyers pay " + homeplanet.parser.Pricing.CORE_PENALTY * core.size() + " points less.\n";
		}
		String hurt = parts.size() <= 1 ? (parts.isEmpty() ? "" : parts.get(0))
				: String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
		final int price;
		String message;
		if (auction) {
			long seed = 0;
			try { seed = java.util.Arrays.hashCode(SafeFiles.read(ship.file())) * 31L + ship.id.hashCode(); } catch (IOException e) { seed = ship.id.hashCode(); }
			price = homeplanet.parser.Pricing.auction(gs, seed);
			int base = homeplanet.parser.Pricing.auctionBase(gs);
			message = "Put " + name + " up for auction?\n\n"
					+ "Her value: " + value + " scrap" + (damage > 0 ? ", less " + damage + " for " + hurt + ": " + base : "") + ".\n"
					+ coreNote
					+ "Bidders will offer between " + homeplanet.parser.Pricing.auctionRange(from)[0] + "% and " + homeplanet.parser.Pricing.auctionRange(from)[1] + "% of that ("
					+ base * homeplanet.parser.Pricing.auctionRange(from)[0] / 100 + " to " + base * homeplanet.parser.Pricing.auctionRange(from)[1] / 100 + " scrap).\n"
					+ "The Home Planet Station accepts the highest bid automatically: once the auction is held, she is sold.\n\n";
		} else {
			price = homeplanet.parser.Pricing.tradeIn(gs);
			int share = Math.max(5, 50 - homeplanet.parser.Pricing.CORE_PENALTY * core.size());
			message = "The Federation Home Planet's shipyard offers " + price + " scrap for " + name + " in trade:\n"
					+ (share == 50 ? "half her value of " : share + "% of her value of ") + value + " scrap" + (damage > 0 ? ", less " + damage + " for " + hurt : "") + ".\n"
					+ coreNote + "\n";
		}
		int crew = SaveHelper.getOwnCrew(from).size();
		message += "Her scrap (" + from.getScrapAmt() + ")" + (crew > 0 ? " and crew (" + crew + ")" : "") + " go to the Cargo Hold first, with the payment.\n"
				+ "Her fuel, missiles, drone parts, weapons, drones, augments, cargo and systems go with her.\nShe leaves the fleet for good.";
		if (!confirmIrreversible(auction ? "Auction" : "Trade In", message, auction ? "Hold Auction" : "Trade In")) return;
		try {
			homeplanet.vault.SpaceDock.sell(Vault.get(), ship, gs, price, auction);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The sale of " + name + " was called off. Nothing was changed:\n" + e);
			return;
		}
		if (auction) {
			int base = homeplanet.parser.Pricing.auctionBase(gs);
			Object[] accept = {"Accept Bid"};
			JOptionPane.showOptionDialog(null, "The auction is over. The highest bid for " + name + ": " + price + " scrap"
					+ (base > 0 ? " (" + (price * 100 / base) + "% of her value)" : "") + ".\n\n" + price + " scrap, her own scrap and her crew are in the Cargo Hold.",
					"Auction", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, accept, accept[0]);
		} else {
			JOptionPane.showMessageDialog(null, name + " is traded in. " + price + " scrap is in the Cargo Hold.", "Trade In", JOptionPane.INFORMATION_MESSAGE);
		}
		init();
	}
	/** A junked ship in a few lines, for the Junkyard list's tooltip: her class, hull, crew, what's wrong, and what she'd sell for. */
	private static String junkTip(Ship ship) {
		SavedGameState gs = ship.save();
		if (gs == null) return ship.name + ": her save can't be read";
		ShipState s = gs.getPlayerShip();
		int max = SystemsPanel.maxHull(s), crew = SaveHelper.getOwnCrew(s).size(), broken = homeplanet.parser.Pricing.brokenBars(s), breaches = s.getBreachMap().size();
		StringBuilder sb = new StringBuilder("<html><b>").append(homeplanet.parser.XmlText.text(gs.getPlayerShipName())).append("</b>, ")
				.append(homeplanet.parser.XmlText.text(CargoBayUI.shipClass(s))).append("<br>Hull ").append(s.getHullAmt()).append(" / ").append(max)
				.append(", crew ").append(crew).append(", scrap ").append(s.getScrapAmt());
		if (broken > 0 || breaches > 0) sb.append("<br>").append(broken).append(broken == 1 ? " broken bar" : " broken bars").append(", ").append(breaches).append(breaches == 1 ? " breach" : " breaches");
		java.util.List<SystemType> core = homeplanet.parser.Pricing.missingCore(s);
		if (!core.isEmpty()) {
			java.util.List<String> names = new java.util.ArrayList<String>();
			for (SystemType t : core) names.add(DryDockShop.systemTitle(t.getId()));
			sb.append("<br>Missing: ").append(String.join(", ", names));
		}
		sb.append("<br>Trade In: ").append(homeplanet.parser.Pricing.tradeIn(gs)).append(" scrap</html>");
		return sb.toString();
	}
	/** The foreman's derelicts for sale. */
	void browseDerelicts() {
		DerelictsDialog.open(this);
		init();
	}
	/** Removes a junked ship for good (her last save stays in her history folder). */
	void destroyShip(Ship ship) {
		if (HomePlanet.immersiveMode) { // or on to Sandbox Mode's fleet, gone from this career all the same (heromedel, 6.10)
			Object[] options = {"Cancel", "Destroy", "Send to Sandbox"};
			int c = JOptionPane.showOptionDialog(null, "Destroy " + ship.name + "?\n\n"
					+ "The ship, her cargo and her crew will be lost to this career. This cannot be undone.\n\n"
					+ "Or send her to Sandbox Mode's Junkyard, her crew with her: this career counts her as gone all the same.",
					"Destroy Ship", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
			if (c != 1 && c != 2) return;
			try {
				if (c == 1) Vault.get().remove(ship, "DESTROY");
				else JOptionPane.showMessageDialog(null, Vault.get().sendToSandbox(ship), "Send to Sandbox", JOptionPane.INFORMATION_MESSAGE);
			} catch (IOException e) {
				HomePlanet.showErrorDialog(c == 1 ? "She could not be destroyed; her save was not removed:\n" + e
						: "The Home Planet Station could not send her to Sandbox Mode's fleet; she is still in the Junkyard:\n" + e.getMessage());
			}
			init();
			return;
		}
		if (!confirmIrreversible("Destroy Ship", "Destroy " + ship.name + "?\n\n"
				+ "The ship, her cargo and her crew will be lost. "
				+ "The Home Planet Station keeps her last records,\nso she could be recovered later (Other... > Recover a ship).", "Destroy")) return;
		try {
			Vault.get().remove(ship, "DESTROY");
		} catch (IOException e) {
			HomePlanet.showErrorDialog("She could not be destroyed; her save was not removed:\n" + e);
			return;
		}
		init();
	}
	/** Salvage, scrap or destroy a ship from the junkyard. */
	void salvageShip() {
		List<Ship> junk = Vault.get().junked();
		if (junk.isEmpty()) {
			Object[] opts = {"Derelicts...", "Parts...", "Close"};
			int r = JOptionPane.showOptionDialog(null, "None of your ships are in the Junkyard. The foreman has derelicts and parts for sale, though.", "Junkyard",
					JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opts, opts[2]);
			if (r == 0) browseDerelicts();
			else if (r == 1) PartsDialog.open(this);
			return;
		}
		String[] names = new String[junk.size()];
		for (int i = 0; i < names.length; i++) names[i] = junk.get(i).name;
		javax.swing.JComboBox<String> pick = new javax.swing.JComboBox<String>(names);
		JPanel panel = new JPanel(new java.awt.BorderLayout(0, 8));
		panel.add(new JLabel("<html>The Junkyard foreman awaits your orders. Choose a hull and what's to be done with her:<br><br>"
				+ "<b>Salvage:</b> haul her back to the Space Dock, crew and cargo intact.<br>"
				+ "<b>Scrap:</b> strip her down. Weapons, drones, augments, cargo, supplies and crew"
				+ (homeplanet.core.Economy.stripAllowed() ? ", and her optional systems<br>if you pay to strip them," : "") + " are sent to "
				+ (homeplanet.core.Economy.stripAllowed() ? "" : "<br>") + "the Cargo Hold, and the hull is broken up for good.<br>"
				+ "<b>Trade In:</b> The Federation Home Planet's shipyard takes her for half her value, less her damage (less still without Engines, Piloting or Oxygen).<br>"
				+ "<b>Auction:</b> sell her to the highest bidder: a quarter to three quarters of her value, less her damage.<br>"
				+ "&nbsp;&nbsp;&nbsp;&nbsp;(Selling her sends her scrap and crew to the Cargo Hold; all else goes with her.)<br>"
				+ "<b>Derelicts:</b> see the hulls the foreman has for sale.<br>"
				+ "<b>Parts:</b> see the damaged systems the foreman has pulled from wrecks.<br>"
				+ "<b>Destroy:</b> reduce her to space debris, with everything aboard. Nothing is recovered,<br>"
				+ "and her crew are retired from service.<br>&nbsp;</html>"), java.awt.BorderLayout.NORTH);
		// each hull's short report as the list's tooltip; Info... opens her full report
		final String[] tips = new String[junk.size()];
		for (int i = 0; i < tips.length; i++) tips[i] = junkTip(junk.get(i));
		pick.setRenderer(new javax.swing.DefaultListCellRenderer() {
			@Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus) {
				java.awt.Component c = super.getListCellRendererComponent(list, value, index, selected, focus);
				if (selected && index >= 0 && index < tips.length) list.setToolTipText(tips[index]);
				return c;
			}
		});
		pick.setToolTipText(tips[0]);
		pick.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { int i = pick.getSelectedIndex(); if (i >= 0) pick.setToolTipText(tips[i]); } });
		JButton info = new JButton("Info...");
		info.setToolTipText("Her report: what's aboard, her crew, her systems");
		info.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				SavedGameState gs = junk.get(pick.getSelectedIndex()).save();
				if (gs == null) { HomePlanet.showErrorDialog("Her save can't be read: " + junk.get(pick.getSelectedIndex()).readError()); return; }
				JOptionPane.showMessageDialog(info, fitToScreen(shipSummaryPanel(gs)), "Ship's report: " + gs.getPlayerShipName(), JOptionPane.PLAIN_MESSAGE);
			}
		});
		JPanel pickRow = new JPanel(new java.awt.BorderLayout(8, 0));
		pickRow.add(pick, java.awt.BorderLayout.CENTER);
		pickRow.add(info, java.awt.BorderLayout.EAST);
		panel.add(pickRow, java.awt.BorderLayout.CENTER);
		Object[] options = {"Salvage", "Scrap", "Trade In", "Auction", "Destroy", "Derelicts...", "Parts...", "Cancel"};
		int choice = JOptionPane.showOptionDialog(null, panel, "Junkyard", JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE, null, options, options[7]); // Cancel is the default
		if (choice == 5) { browseDerelicts(); return; }
		if (choice == 6) { PartsDialog.open(this); return; }
		if (choice < 0 || choice > 4) return;
		Ship ship = junk.get(pick.getSelectedIndex());
		if (choice == 1) { scrapShip(ship); return; }
		if (choice == 2) { sellShip(ship, false); return; }
		if (choice == 3) { sellShip(ship, true); return; }
		if (choice == 4) { destroyShip(ship); return; }
		try {
			Vault.get().salvage(ship);
		} catch (IOException e) {
			HomePlanet.showErrorDialog("She could not be hauled out of the Junkyard; her save data was not moved:\n" + e);
			return;
		}
		init();
		// A retrofitted hull needs the companion mod in the game data before she can fly
		List<String> missing = Retrofit.missingBlueprints(ship.file());
		if (!missing.isEmpty()) {
			Object[] opts = {"Patch Now", "Later"};
			int r = JOptionPane.showOptionDialog(this, ship.name + " is a retrofitted hull (" + String.join(", ", missing) + ").\n"
					+ "She'll wait at the Space Dock, but can't fly until The Home Planet Station sends the " + CompanionMod.TITLE + " to FTL via Slipstream.",
					"Salvage", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opts, opts[0]);
			if (r == 0) PatchDialog.open(this);
		}
	}

	// ---- the report ----

	/** The ship report: a picture of the ship, then supplies, crew, weapons, drones and augments, with FTL's icons. */
	/** The tallest a ship's picture is drawn in her report. */
	private static final int REPORT_PIC_H = 170;

	public JPanel shipSummaryPanel(SavedGameState sgs) { return shipSummaryPanel(sgs, null, null); }
	/**
	 * As {@link #shipSummaryPanel(SavedGameState)}; with {@code rename}, clicking a crew member's name asks for a new one,
	 * and with {@code reroll} a die beside the Crew heading rolls new names (Commission).
	 */
	public JPanel shipSummaryPanel(SavedGameState sgs, final java.util.function.Consumer<CrewState> rename, Runnable reroll) {
		ShipState state = sgs.getPlayerShip();
		JPanel p = new JPanel(new java.awt.BorderLayout(18, 4));
		JLabel pic = reportPicture(sgs);
		// two columns from the top: her picture, supplies and crew on the left; weapons, drones, augments, cargo and systems on the right
		JPanel left = column(), right = column();
		if (pic != null) { pic.setAlignmentX(LEFT_ALIGNMENT); left.add(pic); }
		JPanel crew = left, systems = right;
		reportHeading(left, "Supplies");
		int maxHull = 0;
		ShipBlueprint bp = DataManager.get().getShips().get(sgs.getPlayerShipBlueprintId());
		if (bp != null && bp.getHealth() != null) maxHull = bp.getHealth().amount;
		reportRow(left, null, "Hull: " + state.getHullAmt() + (maxHull > 0 ? " / " + maxHull : ""));
		reportRow(left, IconFactory.supplyIcon("fuel"), "Fuel: " + state.getFuelAmt());
		reportRow(left, IconFactory.supplyIcon("missiles"), "Missiles: " + state.getMissilesAmt());
		reportRow(left, IconFactory.supplyIcon("drones"), "Drone Parts: " + state.getDronePartsAmt());
		reportRow(left, IconFactory.supplyIcon("scrap"), "Scrap: " + state.getScrapAmt());
		if (reroll == null) reportHeading(crew, "Crew");
		else {
			crew.add(Box.createRigidArea(new Dimension(0, 6)));
			JPanel head = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
			head.setOpaque(false);
			head.setAlignmentX(LEFT_ALIGNMENT);
			head.add(shadowLabel("Crew", null, true));
			head.add(DiceIcon.button("New names for her crew (or click a name to choose one)", reroll));
			head.setMaximumSize(head.getPreferredSize());
			crew.add(head);
		}
		for (final CrewState c : SaveHelper.getOwnCrew(state)) {
			final JLabel row = reportRow(crew, CrewReport.withHealth(IconFactory.crewIcon(c), c), c.getName() + " (" + homeplanet.model.Crew.raceTitle(c) + ")");
			row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			if (rename == null) { // a click opens their report
				row.setToolTipText("Click for " + c.getName() + "'s report");
				row.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mouseClicked(java.awt.event.MouseEvent e) { CrewReport.show(row, c, false, new Object[] {"OK"}); } });
				continue;
			}
			row.setToolTipText("Click to rename " + c.getName());
			row.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mouseClicked(java.awt.event.MouseEvent e) { rename.accept(c); } });
		}
		reportHeading(right, "Weapons");
		for (WeaponState w : state.getWeaponList()) reportRow(right, IconFactory.itemIcon(w.getWeaponId()), Items.weaponTitle(w.getWeaponId()));
		reportHeading(right, "Drones");
		for (DroneState d : state.getDroneList()) reportRow(right, IconFactory.itemIcon(d.getDroneId()), Items.droneTitle(d.getDroneId()));
		reportHeading(right, "Augments");
		for (String augmentId : state.getAugmentIdList()) reportRow(right, null, Items.augmentTitle(augmentId));
		List<String> cargo = SaveHelper.cargo(sgs); // not the augment FTL is asking about (5.52)
		reportHeading(right, "Cargo (" + cargo.size() + " of " + SaveHelper.CARGO_SLOTS + ")");
		for (String id : cargo) reportRow(right, IconFactory.itemIcon(id), Items.title(id));
		reportHeading(systems, "Systems");
		systemRow(systems, null, "Reactor", state.getReservePowerCapacity(), homeplanet.parser.VanillaMax.reactor(), 0);
		for (Object[] sys : SYSTEM_NAMES) {
			net.blerf.ftl.parser.SavedGameParser.SystemType t = (net.blerf.ftl.parser.SavedGameParser.SystemType) sys[0];
			net.blerf.ftl.parser.SavedGameParser.SystemState st = state.getSystem(t);
			if (st != null && st.getCapacity() > 0) systemRow(systems, t.getId(), (String) sys[1], st.getCapacity(), homeplanet.parser.VanillaMax.system(t.getId()), st.getDamagedBars());
		}
		JPanel cols = new JPanel(new java.awt.GridBagLayout());
		java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
		gc.anchor = java.awt.GridBagConstraints.NORTHWEST;
		gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
		gc.weightx = 1; gc.weighty = 1; // from the top, whatever room the window gives
		gc.insets = new java.awt.Insets(0, 0, 0, 18);
		gc.gridx = 0; gc.gridy = 0; cols.add(left, gc);
		gc.gridx = 1; cols.add(right, gc);
		p.add(cols, java.awt.BorderLayout.CENTER);
		return p;
	}
	/** A system in the report: FTL's icon for it, the name, its level and a bar of it to the vanilla max (broken bars in red). */
	private static void systemRow(JPanel p, String systemId, String name, int level, int max, int broken) {
		JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		javax.swing.Icon icon = systemId == null ? null : systemIcon(systemId);
		JLabel l = new JLabel(name, icon, JLabel.LEFT);
		l.setIconTextGap(6);
		l.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, icon == null ? 40 : 34 - icon.getIconWidth(), 1, 0));
		l.setPreferredSize(new Dimension(150, l.getPreferredSize().height));
		row.add(l);
		// the level and its bar; past the bar's reach, the level in words ("12 / 2 broken") and no bar
		boolean words = LevelBar.asWords(level, max);
		JLabel n = new JLabel(words ? LevelBar.words(level, broken) : String.valueOf(level), words ? JLabel.LEFT : JLabel.RIGHT);
		if (!words) n.setPreferredSize(new Dimension(18, n.getPreferredSize().height));
		n.setForeground(level > max ? LevelBar.AMBER : MenuTheme.WHITE);
		n.setToolTipText(level > max ? "Past the vanilla max of " + max : "Of a vanilla max of " + max);
		row.add(n);
		if (!words) { row.add(Box.createRigidArea(new Dimension(8, 1))); row.add(new LevelBar(level, max, broken, LevelBar.WIDTH, 12)); }
		row.setMaximumSize(row.getPreferredSize());
		p.add(row);
	}
	private static final java.util.Map<String, javax.swing.Icon> SYSTEM_ICONS = new java.util.HashMap<String, javax.swing.Icon>();
	/** FTL's icon for a system (its room overlay, scaled to the report's rows), or null without one. */
	static synchronized javax.swing.Icon systemIcon(String id) {
		if (SYSTEM_ICONS.containsKey(id)) return SYSTEM_ICONS.get(id);
		javax.swing.Icon icon = null;
		BufferedImage img = LayoutEditor.image("img/icons/s_" + id + "_overlay.png");
		if (img != null) {
			int h = 16, w = Math.max(1, img.getWidth() * h / Math.max(1, img.getHeight()));
			BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = small.createGraphics();
			g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(img, 0, 0, w, h, null);
			g.dispose();
			icon = new ImageIcon(small);
		}
		SYSTEM_ICONS.put(id, icon);
		return icon;
	}
	/** Her picture for the report: half the Space Dock size, and no taller than REPORT_PIC_H (the Lanius would push the lists off the screen). */
	private JLabel reportPicture(SavedGameState sgs) {
		ShipBlueprint ship = blueprintOf(sgs.getPlayerShipBlueprintId());
		if (ship == null) return null;
		BufferedImage img = parent.getResourceImage("img/ship/" + ship.getGraphicsBaseName() + "_base.png", false);
		if (img == null) return null;
		double scale = Math.min(0.5, (double) REPORT_PIC_H / img.getHeight());
		int w = Math.max(1, (int) Math.round(img.getWidth() * scale)), h = Math.max(1, (int) Math.round(img.getHeight() * scale));
		BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = small.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(img, 0, 0, w, h, null);
		g.dispose();
		JLabel pic = new JLabel(new ImageIcon(small));
		pic.setHorizontalAlignment(JLabel.LEFT);
		return pic;
	}

	/** The report's Stats tab: her picture as on the Report tab, then this journey, her service and her crew's standouts, side by side. */
	JPanel shipStatsTab(Ship ship, SavedGameState sgs) {
		homeplanet.parser.ShipStats st = homeplanet.parser.ShipStats.of(Vault.get(), ship, sgs);
		JPanel p = new JPanel(new java.awt.BorderLayout(18, 4));
		JLabel pic = reportPicture(sgs);
		if (pic != null) p.add(pic, java.awt.BorderLayout.NORTH);
		JPanel journey = column(), service = column(), crew = column();
		reportHeading(journey, "This Journey");
		statLines(journey, st.journey);
		if (!st.journeyKnown) statNote(journey, "Her journey's own counts begin with her next New Journey; until then they're in her service.");
		reportHeading(service, "Her Service");
		statLines(service, st.service);
		if (st.traded != null) statNote(service, st.traded);
		reportHeading(crew, "Her Crew");
		if (st.crew.isEmpty()) statNote(crew, "No standouts yet: her crew's records grow as they serve.");
		// each crew member once, with every title she holds beneath her name
		java.util.LinkedHashMap<CrewState, StringBuilder> titles = new java.util.LinkedHashMap<CrewState, StringBuilder>();
		for (homeplanet.parser.ShipStats.Standout o : st.crew) {
			if (!titles.containsKey(o.crew)) titles.put(o.crew, new StringBuilder());
			titles.get(o.crew).append("<b><font color='").append(MenuTheme.HTML_GOLD).append("'>").append(o.title).append("</font></b>&nbsp;&nbsp;<font color='")
					.append(MenuTheme.HTML_GREY_GREEN).append("'>").append(o.earned).append("</font><br>");
		}
		for (java.util.Map.Entry<CrewState, StringBuilder> e : titles.entrySet()) {
			JLabel who = reportRow(crew, IconFactory.crewIcon(e.getKey()), e.getKey().getName());
			who.setForeground(MenuTheme.WHITE);
			who.setFont(MenuTheme.LABEL_FONT);
			JLabel why = new JLabel("<html>" + e.getValue() + "</html>");
			why.setFont(MenuTheme.TEXT_FONT);
			why.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 40, 6, 0));
			why.setAlignmentX(LEFT_ALIGNMENT);
			crew.add(why);
		}
		JPanel cols = new JPanel(new java.awt.GridBagLayout());
		java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
		gc.anchor = java.awt.GridBagConstraints.NORTHWEST;
		gc.insets = new java.awt.Insets(0, 0, 0, 28);
		gc.gridy = 0;
		gc.gridx = 0; cols.add(journey, gc);
		gc.gridx = 1; cols.add(service, gc);
		gc.gridx = 2; gc.insets = new java.awt.Insets(0, 0, 0, 0); cols.add(crew, gc);
		p.add(cols, java.awt.BorderLayout.CENTER);
		return p;
	}
	/** Stats as label and value: the label in grey-green, the value in white (a gain green, a loss red), in two neat columns. */
	private static void statLines(JPanel p, java.util.List<homeplanet.parser.ShipStats.Line> lines) {
		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		grid.setAlignmentX(LEFT_ALIGNMENT);
		grid.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 12, 0, 0));
		java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
		c.insets = new java.awt.Insets(2, 0, 2, 14);
		c.anchor = java.awt.GridBagConstraints.WEST;
		for (int i = 0; i < lines.size(); i++) {
			homeplanet.parser.ShipStats.Line l = lines.get(i);
			JLabel k = new JLabel(l.label);
			k.setFont(MenuTheme.LABEL_FONT);
			k.setForeground(MenuTheme.GREY_GREEN);
			JLabel v = new JLabel(l.value);
			v.setFont(MenuTheme.TEXT_FONT);
			v.setForeground(l.tone > 0 ? MenuTheme.GREEN : l.tone < 0 ? MenuTheme.RED : MenuTheme.WHITE);
			c.gridy = i;
			c.gridx = 0; c.anchor = java.awt.GridBagConstraints.WEST; grid.add(k, c);
			c.gridx = 1; c.anchor = java.awt.GridBagConstraints.EAST; grid.add(v, c);
		}
		grid.setMaximumSize(grid.getPreferredSize());
		p.add(grid);
	}
	private static void statNote(JPanel p, String text) {
		JLabel n = new JLabel("<html><div style='width:200px'><font color='" + MenuTheme.HTML_GREY_GREEN + "'>" + homeplanet.parser.XmlText.text(text) + "</font></div></html>");
		n.setFont(MenuTheme.TEXT_FONT);
		n.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 12, 0, 0));
		n.setAlignmentX(LEFT_ALIGNMENT);
		p.add(n);
	}

	private static JPanel column() {
		JPanel c = new JPanel();
		c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
		return c;
	}
	// Systems in FTL's order, with the names players see
	private static final Object[][] SYSTEM_NAMES = {
		{net.blerf.ftl.parser.SavedGameParser.SystemType.SHIELDS, "Shields"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.ENGINES, "Engines"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.OXYGEN, "Oxygen"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.WEAPONS, "Weapons"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.DRONE_CTRL, "Drone Control"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.MEDBAY, "Medbay"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.CLONEBAY, "Clone Bay"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.TELEPORTER, "Crew Teleporter"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.CLOAKING, "Cloaking"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.MIND, "Mind Control"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.HACKING, "Hacking"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.ARTILLERY, "Artillery"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.BATTERY, "Backup Battery"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.PILOT, "Piloting"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.SENSORS, "Sensors"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.DOORS, "Door System"},
	};
	private static void reportHeading(JPanel p, String text) {
		p.add(Box.createRigidArea(new Dimension(0, 6)));
		p.add(shadowLabel(text, null, true));
	}
	private static JLabel reportRow(JPanel p, javax.swing.Icon icon, String text) {
		JLabel l = new JLabel(text, icon, JLabel.LEFT);
		l.setIconTextGap(6);
		l.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, (icon == null ? 40 : 34 - icon.getIconWidth()), 1, 0));
		l.setAlignmentX(LEFT_ALIGNMENT);
		p.add(l);
		return l;
	}
}
