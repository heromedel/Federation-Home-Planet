package homeplanet.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
 * wait in the grid below, and the controls (Launch, New Journey, Cargo Bay, Commission, Design, Salvage, Disband,
 * Settings, Refresh) run down the right. Every ship here is a {@link Ship} in the {@link Vault}.
 */
public class SpaceDockUI extends JPanel implements ActionListener {
	private final Map<JButton, Ship> boardButtons = new HashMap<JButton, Ship>();
	private final Map<JButton, Ship> infoButtons = new HashMap<JButton, Ship>();
	private JButton museumBtn;
	private JButton inboxBtn, otherBtn, settingsBtn, disbandBtn, salvageBtn, journeyBtn, commissionBtn, refreshBtn, launchBtn, cargoBtn, designBtn;
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
	}

	private static boolean loggedStartup = false;

	/** Rebuilds the screen from the vault (after taking stock of the files, since FTL may have changed them). */
	public void init() {
		removeAll();
		Vault vault = Vault.get();
		try {
			vault.takeStock();
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
		String title = "Docked Ships";
		if (HomePlanet.immersiveMode) title += "  -  " + homeplanet.parser.UnlockGrants.rankName(homeplanet.parser.UnlockGrants.rank(homeplanet.parser.Unlocks.read())); // her captain's rank
		if (HomePlanet.immersiveNotifications) {
			homeplanet.parser.Transmissions.check(); // anything new from The Federation Home Planet
			inboxBtn = new TransmissionButton(homeplanet.parser.Transmissions.unread());
			inboxBtn.addActionListener(this);
		} else {
			inboxBtn = null;
		}
		boolean inboxHere = inboxBtn != null && vault.boarded() == null; // with a ship at your command, it sits on her heading instead
		int inboxW = inboxHere ? inboxBtn.getPreferredSize().width + 8 : 0;
		FtlButton.Header dockedHeader = new FtlButton.Header(title, CELL_W * 3 - inboxW);
		if (HomePlanet.immersiveMode) dockedHeader.setToolTipText("Immersive Mode: your rank. Captains may commission custom ships; Commodores, custom ships with artillery");
		docked.add(withInbox(dockedHeader, inboxHere), java.awt.BorderLayout.NORTH);
		docked.add(gridScroll, java.awt.BorderLayout.CENTER);
		final int dockedW = 14 + CELL_W * 3 + 18;

		// Right: the station's controls, in a column
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setOpaque(false);
		controls.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 10, 10, 16));
		launchBtn = controlButton("Launch FTL", "Play FTL");
		journeyBtn = controlButton("New Journey", "Set out from the first sector with the boarded ship, crew and cargo");
		commissionBtn = controlButton("Commission", "Have a brand-new ship built, as a new game would start her");
		salvageBtn = controlButton("Salvage", "Salvage, scrap or destroy a ship in the Junkyard");
		disbandBtn = controlButton("Decommission", "Decommission the boarded ship: she goes to the Junkyard");
		settingsBtn = controlButton("Settings", "Folders, launching and rules");
		refreshBtn = controlButton("Refresh", "Take stock of the Space Dock again (after playing FTL, or changing save files)");
		cargoBtn = controlButton("Cargo Bay", "Trade, store and shop: the boarded ship's cargo, crew, weapons and systems");
		controlGroup(controls, "Helm", launchBtn, journeyBtn);
		otherBtn = controlButton("Other...", "Orders the station rarely needs: recover a lost or destroyed ship, clean up blueprints, report for reassignment");
		if (homeplanet.parser.Museum.anything(vault)) { // once a ship has won, or been lost in action
			museumBtn = controlButton("Museum", "The Federation Museum: the Hall of Victors, and the Memorial to ships lost in action");
			controlGroup(controls, "Station", cargoBtn, settingsBtn, refreshBtn, otherBtn, museumBtn);
		} else {
			museumBtn = null;
			controlGroup(controls, "Station", cargoBtn, settingsBtn, refreshBtn, otherBtn);
		}
		String designLock = homeplanet.parser.Clearance.customReason();
		designBtn = controlButton("Design Ship", designLock == null ? "Lay out a new ship of your own on a blank grid"
				: "<html>" + homeplanet.parser.XmlText.text(designLock).replace("\n", "<br>") + "</html>");
		controlGroup(controls, "Shipyard", commissionBtn, designBtn, salvageBtn, disbandBtn);

		// The ship at your command, large, at the top beside the station's saucer (not touching it), a few of her
		// particulars to her left when there's room; the docked ships below. Nothing of her at all when none is boarded.
		final Ship boarded = vault.boarded();
		final JPanel berth = boarded == null ? null : berthPanel(boarded);
		final JPanel stats = boarded == null ? null : statsPanel(boarded);
		JPanel main = new JPanel(null) {
			@Override
			public void doLayout() {
				int w = SpaceDockUI.this.getWidth(), h = SpaceDockUI.this.getHeight();
				int top = 0;
				if (berth != null) {
					Dimension d = berth.getPreferredSize();
					double sc = SpaceDockScrollPane.scale(w, h);
					int saucerLeft = (int) Math.round(SpaceDockScrollPane.offsetX(w, h) + SpaceDockScrollPane.SAUCER_LEFT * sc);
					int x = Math.max(14, Math.min(saucerLeft - SAUCER_CLEAR - d.width, getWidth() - d.width - 10));
					berth.setBounds(x, 10, d.width, d.height);
					Dimension sd = stats.getPreferredSize();
					boolean room = x - 14 - 12 >= sd.width;
					stats.setVisible(room);
					if (room) stats.setBounds(x - 12 - sd.width, 10 + berth.getComponent(0).getPreferredSize().height + BERTH_PIC_Y, sd.width, sd.height);
					top = 10 + d.height + 6;
				}
				docked.setBounds(0, top, Math.min(dockedW, getWidth()), Math.max(0, getHeight() - top));
			}
		};
		main.setOpaque(false);
		if (berth != null) { main.add(berth); main.add(stats); }
		main.add(docked);

		add(main, java.awt.BorderLayout.CENTER);
		add(controls, java.awt.BorderLayout.EAST);
		revalidate(); // lay out and redraw the new contents at once (Refresh, Settings...)
		repaint();
		if (!loggedStartup) {
			HistoryLog.loaded("startup");
			loggedStartup = true;
		}
		// FTL's New Game wrote over the boarded ship, or continue.sav is a ship the station never commissioned
		final String cloud = vault.takeCloudCopy();
		if (cloud != null) {
			javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() {
				JOptionPane.showMessageDialog(null, "Steam Cloud brought back an old copy of " + cloud + ", who is already in your fleet.\n"
						+ "The copy was set aside in her records, not added as a second ship.\n\n"
						+ "To stop this, turn off Steam Cloud for FTL: in your Steam library, right-click FTL, Properties, General.", "Steam Cloud", JOptionPane.WARNING_MESSAGE);
			} });
		}
		for (final homeplanet.parser.FinalVictory.Notice n : victories) {
			if (n.offer != null && deferredOffers.contains(n.offer.id)) continue;
			javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { victoryNotice(n); } });
		}
		final String over = vault.takeOverwritten();
		final Ship stranger = vault.boarded() != null && vault.boarded().stranger && !deferredStrangers.contains(vault.boarded().id) ? vault.boarded() : null;
		if (over != null || (stranger != null && HomePlanet.immersiveMode)) {
			javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { newGameNotice(over, stranger); } });
		}
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
			String what = c == 0 ? homeplanet.parser.FinalVictory.keep(f) : homeplanet.parser.FinalVictory.museum(f);
			JOptionPane.showMessageDialog(null, what, n.title, JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not do that:\n" + e.getMessage());
		} finally {
			askingOffers.remove(n.offer.id);
		}
		init();
	}

	private boolean askingAboutStranger = false;
	/** Uncommissioned ships the player put off deciding about: asked again at the next start. */
	private final java.util.Set<String> deferredStrangers = new java.util.HashSet<String>();
	/** Tells the player a boarded ship was overwritten; in Immersive Mode, asks what's to become of an uncommissioned ship. */
	private void newGameNotice(String over, Ship stranger) {
		if (askingAboutStranger) return;
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
		Object[] options = {"Send her to the normal Space Dock", "Decommission her", "Switch to normal mode now", "Close The Home Planet Station"};
		int c = JOptionPane.showOptionDialog(null, message, "Uncommissioned ship", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
		if (c == 3) { if (parent != null) parent.dispatchEvent(new java.awt.event.WindowEvent(parent, java.awt.event.WindowEvent.WINDOW_CLOSING)); return; }
		if (c < 0) { deferredStrangers.add(stranger.id); init(); return; } // closed: asked again at the next start
		if (GameGuard.isFtlRunning()) {
			JOptionPane.showMessageDialog(null, "FTL is running. Quit FTL first; The Home Planet Station will ask again.", "Uncommissioned ship", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Vault v = Vault.get();
		try {
			if (c == 0) {
				v.sendToOtherFleet(stranger, false);
				JOptionPane.showMessageDialog(null, stranger.name + " waits at the normal Space Dock.", "Uncommissioned ship", JOptionPane.INFORMATION_MESSAGE);
			} else if (c == 1) {
				Object[] how = {"Send her to the normal Junkyard", "Destroy her", "Cancel"};
				int d = JOptionPane.showOptionDialog(null, "Decommission " + stranger.name + ":\n\n"
						+ "Send her to the normal fleet's Junkyard, or destroy her? (A destroyed ship's last version stays in the station's records.)",
						"Decommission", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, how, how[2]);
				if (d == 0) v.sendToOtherFleet(stranger, true);
				else if (d == 1) v.remove(stranger, "DESTROY");
				else { deferredStrangers.add(stranger.id); init(); return; }
			} else {
				ImmersiveDialog.leaveNow(stranger); // her fleet, rules and FTL profile: the normal ones
				JOptionPane.showMessageDialog(null, "Immersive Mode is off. " + stranger.name + " is boarded in your normal fleet.\n"
						+ "Your Immersive fleet is kept as it was.", "Uncommissioned ship", JOptionPane.INFORMATION_MESSAGE);
			}
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not do that:\n" + e.getMessage());
		}
		init();
	}

	/** Why the Cargo Bay can't open now (no ship boarded, or she's away from a station), or null if it can. */
	private String cargoBayClosedReason() {
		Ship ship = Vault.get().boarded();
		if (ship == null || ship.save() == null) return "No ship is at your command.\nBoard a ship before returning to the Cargo Bay to trade.";
		if (!Vault.get().mayTrade(ship))
			return ship.name + " is not within range of a station.\nFind a beacon with a station, then return to trade.";
		return null;
	}
	private FtlButton controlButton(String text, String tip) {
		FtlButton b = new FtlButton(text, FtlFont.MENU, 180, 40);
		b.setToolTipText(tip);
		b.addActionListener(this);
		b.setAlignmentX(LEFT_ALIGNMENT);
		return b;
	}
	private static void controlGroup(JPanel column, String title, JButton... buttons) {
		controlGroup(column, new FtlButton.Header(title, 186), buttons);
	}
	private static void controlGroup(JPanel column, javax.swing.JComponent header, JButton... buttons) {
		column.add(header);
		column.add(Box.createRigidArea(new Dimension(1, 10)));
		for (JButton b : buttons) {
			column.add(b);
			column.add(Box.createRigidArea(new Dimension(1, 10)));
		}
		column.add(Box.createRigidArea(new Dimension(1, 16)));
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
	private JPanel berthPanel(Ship ship0) {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		JPanel head = new JPanel();
		head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
		head.setOpaque(false);
		head.setAlignmentX(LEFT_ALIGNMENT);
		int inboxW = inboxBtn == null ? 0 : inboxBtn.getPreferredSize().width + 8;
		head.add(withInbox(new FtlButton.Header("At your command", BERTH_W - inboxW), inboxBtn != null));
		head.add(Box.createRigidArea(new Dimension(1, 6)));
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
	/** A heading with the transmissions light at the end of its line (where the eye goes first), if it goes here. */
	private JPanel withInbox(FtlButton.Header header, boolean here) {
		JPanel row = new JPanel(new java.awt.BorderLayout(8, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(header, java.awt.BorderLayout.CENTER);
		if (here) row.add(inboxBtn, java.awt.BorderLayout.EAST);
		row.setMaximumSize(row.getPreferredSize());
		return row;
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
			if (ship.isBoarded()) dock();
			else board(ship);
		} else if (o == settingsBtn) {
			SettingsDialog.open(this);
			init(); // rules or the saves folder may have changed
		} else if (o == refreshBtn) {
			refresh();
		} else if (o == museumBtn && museumBtn != null) {
			parent.showMuseum();
		} else if (o == otherBtn) {
			otherOrders();
		} else if (o == inboxBtn) {
			boolean go = InboxDialog.open(this);
			init();
			if (go) commissionShip();
		} else if (o == journeyBtn) {
			newJourney();
		} else if (o == commissionBtn) {
			commissionShip();
		} else if (o == salvageBtn) {
			salvageShip();
		} else if (o == disbandBtn) {
			disbandCurrentShip();
		} else if (o == designBtn && homeplanet.parser.Clearance.customReason() != null) {
			JOptionPane.showMessageDialog(this, homeplanet.parser.Clearance.customReason(), "Design Ship", JOptionPane.INFORMATION_MESSAGE);
		} else if (o == designBtn) {
			DesignListDialog.open(this);
		} else if (o == cargoBtn) {
			String why = cargoBayClosedReason();
			if (why == null) parent.showCargoBay();
			else JOptionPane.showMessageDialog(this, why, "Cargo Bay", JOptionPane.INFORMATION_MESSAGE);
		} else if (o == launchBtn) {
			HomePlanet.launchFTL();
		} else if (infoButtons.containsKey(o)) {
			o.setFocusPainted(false);
			showShipInfo(infoButtons.get(o));
		}
	}

	/** Reads the vault and every changed file again (after playing FTL, or changing files by hand). */
	void refresh() {
		try {
			Vault.get().reload();
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not take stock of the fleet again:\n" + e);
		}
		homeplanet.core.Music.refresh();
		init();
		HistoryLog.loaded("refresh");
	}

	/** The transmissions icon: an antenna, and a green light with the unread count. */
	/**
	 * The transmissions light, at the end of the Docked Ships heading: a mast and dish, and with anything unread a green
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

	/** Other...: the station's rarely used orders, in a window of their own. */
	private void otherOrders() {
		java.util.List<OtherOrdersDialog.Order> orders = new java.util.ArrayList<OtherOrdersDialog.Order>();
		orders.add(new OtherOrdersDialog.Order("Recover a ship", "For when a ship is lost to a bug or a malfunction: restores her from the station's last record of her.",
				HomePlanet.immersiveMode ? "Immersive Mode: ships lost or destroyed stay gone." : null,
				new Runnable() { public void run() { recoverShip(); } }, false));
		orders.add(new OtherOrdersDialog.Order("Clean up blueprints", "Remove old blueprints no ship uses any more from the Federation Home Planet Mod. Rarely needed.",
				null, new Runnable() { public void run() { BlueprintCleanup.run(SpaceDockUI.this); } }, false));
		Vault v = Vault.get();
		boolean taken = !v.docked().isEmpty() || v.boarded() != null;
		orders.add(new OtherOrdersDialog.Order("Report for Reassignment", "Surrender the Cargo Hold and the Junkyard's hulls in exchange for a free new command.",
				!HomePlanet.commissionCosts ? "commissioning is free (Settings, Rules): Commission a new ship instead."
						: taken ? "only a captain with no ship at the Space Dock can report for reassignment."
						: v.freeCommandOpen() ? "a free command is already waiting for you at Commission." : null,
				new Runnable() { public void run() { reportForReassignment(); } }, true));
		final File last = v.lastSurrender();
		if (last != null) {
			orders.add(new OtherOrdersDialog.Order("Undo Reassignment", "Take back the Cargo Hold and hulls surrendered in the last report for reassignment.",
					HomePlanet.immersiveMode ? "Immersive Mode: a report for reassignment is final."
							: taken ? "only before a new command is taken: no ship may be at the Space Dock." : null,
					new Runnable() { public void run() { undoReassignment(last); } }, false));
		}
		OtherOrdersDialog.open(this, orders);
	}
	/** What an empty shipyard grants, in words. */
	private static String freeShipWords() {
		return homeplanet.parser.FreeCommand.words(homeplanet.parser.FreeCommand.ship());
	}
	/** HR2: surrender the storage hold and the Junkyard for a free new command, then open Commission. */
	void reportForReassignment() {
		Vault v = Vault.get();
		List<Ship> junk = v.junked();
		StringBuilder hulls = new StringBuilder();
		for (int i = 0; i < junk.size(); i++) hulls.append(i == 0 ? "" : ", ").append(junk.get(i).name);
		String message = "Report for reassignment?\n\n"
				+ "You surrender to The Federation Home Planet:\n"
				+ "  - The Cargo Hold: its " + v.storageScrap() + " scrap, supplies, weapons, drones, augments, crew and stored systems\n"
				+ (junk.isEmpty() ? "  - (the Junkyard is empty)\n" : "  - every hull in the Junkyard: " + hulls + "\n")
				+ "\nIn exchange, The Federation Home Planet grants you a new command: " + freeShipWords() + ", free.\n\n"
				+ (HomePlanet.immersiveMode ? "This is final (Immersive Mode)."
				: "The Home Planet Station keeps a record of what was surrendered. Until you take your new command,\n"
				+ "this can be undone (Other... > Undo Reassignment).");
		if (!confirmIrreversible("Report for Reassignment", message, "Report")) return;
		try {
			v.surrender();
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not complete the report for reassignment. Nothing was surrendered:\n" + e.getMessage());
			init();
			return;
		}
		init();
		JOptionPane.showMessageDialog(null, "Your report is accepted, Captain. The shipyard stands ready to build your new command.", "Report for Reassignment", JOptionPane.INFORMATION_MESSAGE);
		commissionShip();
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
					+ "\n\nHer records are still in " + d.last.getParentFile());
			init();
			return;
		}
		init();
		JOptionPane.showMessageDialog(null, back.name + " has been recovered. She waits at the Space Dock.", "Recover a Ship", JOptionPane.INFORMATION_MESSAGE);
	}

	/** Takes command of a docked ship (docking the boarded one first). True if she was boarded. */
	public boolean board(Ship ship) {
		if (ship == null || ship.isBoarded()) return false;
		if (!GameGuard.allows(this, "board a ship")) return false;
		try {
			Vault.get().board(ship);
		} catch (IOException e) {
			HomePlanet.showErrorDialog(ship.name + " could not be boarded; command was not transferred:\n" + e.getMessage());
			init();
			return false;
		}
		init();
		return true;
	}
	/** Docks the boarded ship. True if she was docked. */
	public boolean dock() {
		Ship b = Vault.get().boarded();
		if (b == null) return false;
		if (!GameGuard.allows(this, "dock her")) return false;
		try {
			Vault.get().dock();
		} catch (IOException e) {
			HomePlanet.showErrorDialog(b.name + " could not be docked:\n" + e.getMessage());
			init();
			return false;
		}
		init();
		return true;
	}

	/** Shows the ship report, with the option to rename the ship. */
	private void showShipInfo(Ship ship) {
		SavedGameState sgs = ship.save();
		if (sgs == null) {
			JOptionPane.showMessageDialog(this, "The Home Planet Station can't read " + ship.name + "'s save:\n" + ship.readError()
					+ (Retrofit.missingBlueprints(ship.file()).isEmpty() ? "" : "\n\nShe can't fly until The Home Planet Station sends the " + Retrofit.MOD_NAME + " to FTL via Slipstream (Settings > Patch mods)."),
					"Ship's report", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		int choice = reportChoice(sgs, true);
		if (choice == 2) {
			if (ShipRecordsDialog.open(this, ship)) init();
			return;
		}
		if (choice != 1) return;
		String oldName = sgs.getPlayerShipName();
		String newName = promptForName("What shall she be called?", "Rename Ship", oldName);
		if (newName == null || newName.equals(oldName)) return;
		if (ship.isBoarded() && !GameGuard.allows(this, "rename her")) return;
		sgs.setPlayerShipName(newName);
		sgs.getPlayerShip().setShipName(newName);
		try {
			Vault.get().write(ship, sgs);
		} catch (Exception e) {
			sgs.setPlayerShipName(oldName);
			sgs.getPlayerShip().setShipName(oldName);
			HomePlanet.showErrorDialog("She could not be renamed; her save could not be written:\n" + e);
			return;
		}
		HistoryLog.entry("RENAME", oldName + " -> " + newName + "  (" + ship.id + ")");
		JOptionPane.showMessageDialog(null, oldName + " is now known as " + newName + ".", "Rename Ship", JOptionPane.INFORMATION_MESSAGE);
		init();
	}
	/**
	 * The ship report window, the one both the Space Dock and the Cargo Bay open: picture, supplies, crew, weapons,
	 * drones, augments, and the retrofit/remodel status in the title. Returns true if the player pressed Rename.
	 */
	public boolean showReport(SavedGameState sgs) {
		return reportChoice(sgs, false) == 1;
	}
	/** The report, with Records (her kept versions and log) when she's one of the fleet. 0 OK, 1 Rename, 2 Records. */
	private int reportChoice(SavedGameState sgs, boolean records) {
		boolean retrofitted = Retrofit.isRetrofitted(sgs.getPlayerShip());
		String bpId = sgs.getPlayerShip().getShipBlueprintId();
		String tag = !retrofitted ? "" : CompanionMod.isRemodelId(bpId) ? " (Remodeled " + CompanionMod.numberOf(bpId) + ")" : " (Retrofitted)";
		if (retrofitted && !Retrofit.inGame(sgs.getPlayerShip())) tag += " - needs the mod sent to FTL via Slipstream";
		Object[] options = records ? new Object[] {"OK", "Rename", "Records"} : new Object[] {"OK", "Rename"};
		return JOptionPane.showOptionDialog(null, fitToScreen(shipSummaryPanel(sgs)),
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
		if (HomePlanet.journeyStoreRequirement && !SaveHelper.isAtStation(gs) && !Vault.get().stillAtHomePlanet(ship)) {
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
		int fee = HomePlanet.immersiveMode ? HomePlanet.JOURNEY_FEE : 0;
		if (fee > 0) {
			int have = Vault.get().storageScrap();
			if (have < fee) {
				JOptionPane.showMessageDialog(null, "The Federation Home Planet charges " + fee + " scrap to plot a new journey, paid from the Cargo Hold,\n"
						+ "which holds " + have + ". Store more scrap in the Cargo Bay first.", "New Journey", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			if (!HomePlanet.confirmNo(this, "The Federation Home Planet charges " + fee + " scrap to plot a new journey,\npaid from the Cargo Hold (which holds " + have + "). Pay it?", "New Journey")) return;
		}
		if (!GameGuard.allows(this, "start her new journey")) return;
		byte[] storageBefore = null;
		if (fee > 0) {
			try {
				storageBefore = Vault.get().payFromStorage(fee);
			} catch (IOException e) {
				HomePlanet.showErrorDialog("The Home Planet Station could not take the fee from the Cargo Hold. Nothing was changed:\n" + e.getMessage());
				return;
			}
		}
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
			Vault.get().write(ship, gs);
			Vault.get().setOut(ship, gs, homeplanet.vault.VoyageLog.NEW_JOURNEY); // at The Home Planet Station until she jumps
			HistoryLog.entry("NEW JOURNEY", gs.getPlayerShipName() + "  difficulty " + options[choice] + (fee > 0 ? ", fee " + fee + " scrap from the Cargo Hold" : ""));
		} catch (Exception e) {
			ship.invalidate();
			String refund = "";
			if (storageBefore != null) {
				try { Vault.get().refundStorage(storageBefore); refund = "\nThe fee was returned to the Cargo Hold."; }
				catch (IOException again) { refund = "\nThe fee could not be returned to the Cargo Hold: " + again.getMessage(); }
			}
			HomePlanet.showErrorDialog("The Home Planet Station could not save her new journey:\n" + e + refund);
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
		SavedGameState wreck = wreckShip.save();
		if (wreck == null) {
			HomePlanet.showErrorDialog("The Home Planet Station can't read " + wreckShip.name + "'s save, so she can't be stripped:\n" + wreckShip.readError());
			return;
		}
		String name = wreckShip.name;
		if (HomePlanet.storeRequirement && !SaveHelper.isAtStation(wreck)) {
			JOptionPane.showMessageDialog(null, name + " is not within range of a station.\n"
					+ "The Home Planet Station cannot scrap her for supplies unless you salvage her and fly her to a beacon with a station first.", "Scrap Ship", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String aboard = "Everything aboard will be moved to the Cargo Hold.\n";
		if (HomePlanet.scrapKeepsSystems) {
			aboard = "Everything aboard, including her systems, will be moved to the Cargo Hold.\n" + SystemsPanel.scrapPreview(wreck.getPlayerShip());
		}
		if (!confirmIrreversible("Scrap Ship", "Strip " + name + " for parts?\n\n" + aboard
				+ "The hull will be broken up and can never be recovered.", "Scrap")) return;
		List<String> scrapped;
		try {
			Vault vault = Vault.get();
			Ship storageShip = vault.storage();
			// a fresh copy: the shared one must not keep the additions if anything below fails
			Vault.Copy storageCopy;
			try { storageCopy = vault.readCopy(storageShip); } catch (IOException e) { throw new IOException("The Cargo Hold can't be read: " + e.getMessage()); }
			SavedGameState storage = storageCopy.save;
			// what the hold and the stored-systems list hold now, to put back if the wreck can't be removed after them
			File storageFile = storageShip.file(), systemsFile = vault.systemsFile();
			byte[] storageBefore = SafeFiles.read(storageFile), systemsBefore = systemsFile.isFile() ? SafeFiles.read(systemsFile) : null;
			scrapped = HistoryLog.changes(new java.util.HashMap<String, Integer>(), HistoryLog.inventory(wreck));
			ShipState from = wreck.getPlayerShip();
			ShipState to = storage.getPlayerShip();
			to.setScrapAmt(to.getScrapAmt() + from.getScrapAmt());
			to.setFuelAmt(to.getFuelAmt() + from.getFuelAmt());
			to.setMissilesAmt(to.getMissilesAmt() + from.getMissilesAmt());
			to.setDronePartsAmt(to.getDronePartsAmt() + from.getDronePartsAmt());
			for (WeaponState w : from.getWeaponList()) to.getWeaponList().add(SaveHelper.newIdleWeapon(w.getWeaponId()));
			for (DroneState d : from.getDroneList()) to.getDroneList().add(SaveHelper.copyDroneForTransfer(d));
			to.getAugmentIdList().addAll(from.getAugmentIdList());
			// Storage keeps cargo sorted by kind
			for (String id : wreck.getCargoIdList()) {
				if (Items.isWeapon(id)) to.getWeaponList().add(SaveHelper.newIdleWeapon(id));
				else if (Items.isDrone(id)) to.getDroneList().add(SaveHelper.newIdleDrone(id));
				else if (Items.isAugment(id)) to.getAugmentIdList().add(id);
				else storage.getCargoIdList().add(id);
			}
			for (CrewState c : SaveHelper.getOwnCrew(from)) {
				if (SaveHelper.hasBody(c) && SaveHelper.placeCrew(to, c, true)) to.getCrewList().add(c);
			}
			Vault.Transaction tx = vault.begin().put(storageShip, storage, storageCopy.hash);
			if (HomePlanet.scrapKeepsSystems) scrapped.addAll(SystemsPanel.scrapSystems(from, tx));
			tx.commit();
			try {
				vault.remove(wreckShip, null); // logged below, with what came off her
			} catch (IOException e) {
				// she's still in the Junkyard with everything aboard: the hold must not keep a second copy
				SafeFiles.write(storageFile, storageBefore);
				if (systemsBefore != null) SafeFiles.write(systemsFile, systemsBefore); else systemsFile.delete();
				storageShip.invalidate();
				throw e;
			}
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The order to scrap was called off. Nothing was changed:\n" + e);
			return;
		}
		HistoryLog.entry("SCRAP", name + " stripped into storage, hull broken up", scrapped);
		init();
	}
	/** Removes a junked ship for good (her last save stays in her history folder). */
	void destroyShip(Ship ship) {
		if (!confirmIrreversible("Destroy Ship", "Destroy " + ship.name + "?\n\n"
				+ "The ship, her cargo and her crew will be lost. " + (HomePlanet.immersiveMode ? "This cannot be undone."
				: "The Home Planet Station keeps her last records,\nso she could be recovered later (Other... > Recover a ship)."), "Destroy")) return;
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
			JOptionPane.showMessageDialog(null, "The Junkyard is empty. Nothing left to salvage.", "Salvage Ship", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String[] names = new String[junk.size()];
		for (int i = 0; i < names.length; i++) names[i] = junk.get(i).name;
		javax.swing.JComboBox<String> pick = new javax.swing.JComboBox<String>(names);
		JPanel panel = new JPanel(new java.awt.BorderLayout(0, 8));
		panel.add(new JLabel("<html>The Junkyard foreman awaits your orders. Choose a hull and what's to be done with her:<br><br>"
				+ "<b>Salvage:</b> haul her back to the Space Dock, crew and cargo intact.<br>"
				+ "<b>Scrap:</b> strip her down. Weapons, drones, augments, cargo, supplies and crew"
				+ (HomePlanet.scrapKeepsSystems ? ", and her optional systems," : "") + " are sent to<br>"
				+ "the Cargo Hold, and the hull is broken up for good.<br>"
				+ "<b>Destroy:</b> reduce her to space debris, with everything aboard. Nothing is recovered,<br>"
				+ "and her crew are retired from service.<br>&nbsp;</html>"), java.awt.BorderLayout.NORTH);
		panel.add(pick, java.awt.BorderLayout.CENTER);
		Object[] options = {"Salvage", "Scrap", "Destroy", "Cancel"};
		int choice = JOptionPane.showOptionDialog(null, panel, "Salvage Ship", JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE, null, options, options[3]); // Cancel is the default
		if (choice < 0 || choice > 2) return;
		Ship ship = junk.get(pick.getSelectedIndex());
		if (choice == 1) { scrapShip(ship); return; }
		if (choice == 2) { destroyShip(ship); return; }
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
	private static final int REPORT_PIC_H = 200;

	public JPanel shipSummaryPanel(SavedGameState sgs) {
		ShipState state = sgs.getPlayerShip();
		JPanel p = new JPanel(new java.awt.BorderLayout(18, 4));
		ShipBlueprint ship = blueprintOf(sgs.getPlayerShipBlueprintId());
		if (ship != null) {
			BufferedImage img = parent.getResourceImage("img/ship/" + ship.getGraphicsBaseName() + "_base.png", false);
			if (img != null) { // half the Space Dock size, and no taller than REPORT_PIC_H (the Lanius would push the lists off the screen)
				double scale = Math.min(0.5, (double) REPORT_PIC_H / img.getHeight());
				int w = Math.max(1, (int) Math.round(img.getWidth() * scale)), h = Math.max(1, (int) Math.round(img.getHeight() * scale));
				BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = small.createGraphics();
				g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(img, 0, 0, w, h, null);
				g.dispose();
				JLabel pic = new JLabel(new ImageIcon(small));
				pic.setHorizontalAlignment(JLabel.LEFT);
				p.add(pic, java.awt.BorderLayout.NORTH);
			}
		}
		// two rows: Supplies beside the items, then Crew beside Systems, so the lower headings line up
		JPanel left = column(), right = column(), crew = column(), systems = column();
		reportHeading(left, "Supplies");
		int maxHull = 0;
		ShipBlueprint bp = DataManager.get().getShips().get(sgs.getPlayerShipBlueprintId());
		if (bp != null && bp.getHealth() != null) maxHull = bp.getHealth().amount;
		reportRow(left, null, "Hull: " + state.getHullAmt() + (maxHull > 0 ? " / " + maxHull : ""));
		reportRow(left, IconFactory.supplyIcon("fuel"), "Fuel: " + state.getFuelAmt());
		reportRow(left, IconFactory.supplyIcon("missiles"), "Missiles: " + state.getMissilesAmt());
		reportRow(left, IconFactory.supplyIcon("drones"), "Drone Parts: " + state.getDronePartsAmt());
		reportRow(left, IconFactory.supplyIcon("scrap"), "Scrap: " + state.getScrapAmt());
		reportHeading(crew, "Crew");
		for (CrewState c : SaveHelper.getOwnCrew(state)) {
			reportRow(crew, IconFactory.crewIcon(c), c.getName() + " (" + homeplanet.model.Crew.raceTitle(c) + ")");
		}
		reportHeading(right, "Weapons");
		for (WeaponState w : state.getWeaponList()) reportRow(right, IconFactory.itemIcon(w.getWeaponId()), Items.weaponTitle(w.getWeaponId()));
		reportHeading(right, "Drones");
		for (DroneState d : state.getDroneList()) reportRow(right, IconFactory.itemIcon(d.getDroneId()), Items.droneTitle(d.getDroneId()));
		reportHeading(right, "Augments");
		for (String augmentId : state.getAugmentIdList()) reportRow(right, null, Items.augmentTitle(augmentId));
		reportHeading(right, "Cargo (" + sgs.getCargoIdList().size() + " of " + SaveHelper.CARGO_SLOTS + ")");
		for (String id : sgs.getCargoIdList()) reportRow(right, IconFactory.itemIcon(id), Items.title(id));
		reportHeading(systems, "Systems");
		reportRow(systems, null, "Reactor: " + state.getReservePowerCapacity());
		for (Object[] sys : SYSTEM_NAMES) {
			net.blerf.ftl.parser.SavedGameParser.SystemState st = state.getSystem((net.blerf.ftl.parser.SavedGameParser.SystemType) sys[0]);
			if (st != null && st.getCapacity() > 0) reportRow(systems, null, sys[1] + ": " + st.getCapacity());
		}
		JPanel cols = new JPanel(new java.awt.GridBagLayout());
		java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
		gc.anchor = java.awt.GridBagConstraints.NORTHWEST;
		gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
		gc.weightx = 1;
		gc.insets = new java.awt.Insets(0, 0, 0, 18);
		gc.gridx = 0; gc.gridy = 0; cols.add(left, gc);
		gc.gridx = 1; cols.add(right, gc);
		gc.gridx = 0; gc.gridy = 1; cols.add(crew, gc);
		gc.gridx = 1; cols.add(systems, gc);
		p.add(cols, java.awt.BorderLayout.CENTER);
		return p;
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
	private static void reportRow(JPanel p, javax.swing.Icon icon, String text) {
		JLabel l = new JLabel(text, icon, JLabel.LEFT);
		l.setIconTextGap(6);
		l.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, (icon == null ? 40 : 34 - icon.getIconWidth()), 1, 0));
		l.setAlignmentX(LEFT_ALIGNMENT);
		p.add(l);
	}
}
