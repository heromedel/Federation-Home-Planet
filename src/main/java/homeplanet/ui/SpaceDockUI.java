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
 * wait in the grid below, and the controls (Launch, New Journey, Cargo Bay, Commission, Design, Salvage, Disband,
 * Settings, Refresh) run down the right. Every ship here is a {@link Ship} in the {@link Vault}.
 */
public class SpaceDockUI extends JPanel implements ActionListener {
	private final Map<JButton, Ship> boardButtons = new HashMap<JButton, Ship>();
	private final Map<JButton, Ship> infoButtons = new HashMap<JButton, Ship>();
	private JButton museumBtn;
	private JButton inboxBtn, repBtn, otherBtn, settingsBtn, disbandBtn, salvageBtn, journeyBtn, commissionBtn, refreshBtn, launchBtn, cargoBtn, designBtn, commBtn;
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
		String title = "Docked";
		boolean longRange = parent != null && parent.comm != null && parent.comm.inboxWanted(); // a commander's mail needs an inbox, whatever the setting
		if (HomePlanet.immersiveNotifications() || longRange) {
			if (HomePlanet.immersiveNotifications()) homeplanet.parser.Transmissions.check(); // anything new from The Federation Home Planet
			inboxBtn = new TransmissionButton(homeplanet.parser.Transmissions.unread());
			inboxBtn.addActionListener(this);
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
		controls.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 10, 10, 16));
		launchBtn = controlButton("Launch FTL", "Play FTL");
		journeyBtn = controlButton("New Journey", "Set out from the first sector with the boarded ship, crew and cargo");
		commissionBtn = controlButton("Commission", "Have a brand-new ship built, as a new game would start her");
		salvageBtn = controlButton("Salvage", "Salvage, scrap or destroy a ship in the Junkyard");
		disbandBtn = controlButton("Decommission", "Decommission the boarded ship: she goes to the Junkyard");
		settingsBtn = controlButton("Settings", "Folders, launching and rules");
		refreshBtn = controlButton("Refresh", "Take stock of the Space Dock again (after playing FTL, or changing save files)");
		cargoBtn = controlButton("Cargo Bay", "Trade, store and shop: the boarded ship's cargo, crew, weapons and systems");
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
		otherBtn = controlButton("Other...", "Orders the station rarely needs: recover a lost or destroyed ship, clean up blueprints, report for reassignment");
		if (homeplanet.parser.Museum.anything(vault)) { // once a ship has won, or been lost in action
			museumBtn = controlButton("Museum", "The Federation Museum: the Hall of Victors, and the Memorial to ships lost in action");
			controlGroup(controls, "Station", cargoBtn, commBtn, settingsBtn, refreshBtn, museumBtn);
		} else {
			museumBtn = null;
			controlGroup(controls, "Station", cargoBtn, commBtn, settingsBtn, refreshBtn);
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
		JPanel main = new JPanel(null) {
			@Override
			public void doLayout() {
				int w = SpaceDockUI.this.getWidth(), h = SpaceDockUI.this.getHeight();
				int top = 0;
				if (berth != null) {
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
				docked.setBounds(0, top, Math.min(dockedW, getWidth()), Math.max(0, getHeight() - top));
			}
		};
		main.setOpaque(false);
		if (berth != null) { main.add(aboard); main.add(berth); main.add(stats); }
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
		Object[] options = {"Send her to the Sandbox fleet's Space Dock", "Decommission her", "Switch to Sandbox Mode now", "Close The Home Planet Station"};
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

	/** Why the Cargo Bay can't open now (her save unreadable, or she's away from a station), or null if it can. With no ship aboard it opens on the Cargo Hold. */
	private String cargoBayClosedReason() {
		Ship ship = Vault.get().boarded();
		if (ship == null) return null; // the Cargo Hold alone: its goods can be sold (CargoBayUI.holdOnly)
		if (ship.save() == null) return ship.name + "'s save can't be read.\nBoard another ship, or check her Records, before returning to the Cargo Bay to trade.";
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
		column.add(gap(10));
		for (JButton b : buttons) {
			column.add(b);
			column.add(gap(10));
		}
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
		aboardRow = withInbox(new FtlButton.Header("Aboard", BERTH_W - inboxW, true), true); // aboard her: the ship you're on
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
	private JPanel withInbox(FtlButton.Header header, boolean here) {
		JPanel row = new JPanel(new java.awt.BorderLayout(8, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(header, java.awt.BorderLayout.CENTER);
		if (here && repBtn == null && inboxBtn != null) row.add(inboxBtn, java.awt.BorderLayout.EAST);
		else if (here && repBtn != null) { // the reputation, to the inbox's right
			JPanel both = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
			both.setOpaque(false);
			if (inboxBtn != null) both.add(inboxBtn);
			both.add(repBtn);
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
		} else if (o == commBtn) {
			parent.showLongRangeComm();
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
		SpaceDockScrollPane.reroll(parent, false); // now and then, different ships leaving the station
		init();
		HistoryLog.loaded("refresh");
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
		boolean rep = homeplanet.vault.Reputation.shown();
		String message = "Plead for a new ship?\n\n"
				+ "You put your case to The Federation Home Planet: one more ship, and you'll bring her home. They listen.\n"
				+ "They will send " + offered + ". Her order will wait for you at Commission.\n\n"
				+ "Nothing is taken now. When you commission her, you choose how to pay:\n"
				+ "  - Give up the Cargo Hold: everything in it but the crew, at what it would sell for (the Junkyard isn't touched).\n"
				+ (rep ? "  - Keep the Cargo Hold, and answer for her with your reputation.\n"
						+ "Whatever the hold doesn't cover of her value, a tenth of it comes off your reputation.\n"
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
		int choice = reportChoice(ship, sgs, true);
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
		if (!Vault.get().mayTrade(wreckShip)) { // the station rule, as for trading: a store, or just set out at The Home Planet Station
			JOptionPane.showMessageDialog(null, name + " is not within range of a station.\n"
					+ "The Home Planet Station cannot scrap her for supplies unless you salvage her and fly her to a beacon with a station first.", "Scrap Ship", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		// stripping her systems, where allowed, costs a fee for each, paid from the Cargo Hold and her own scrap together
		int systems = homeplanet.core.Economy.stripAllowed() ? SystemsPanel.strippable(wreck.getPlayerShip()) : 0;
		final int stripCost = systems * homeplanet.core.Economy.stripFee();
		final boolean strip;
		if (systems > 0) {
			int have = Vault.get().storageScrap() + wreck.getPlayerShip().getScrapAmt();
			String fee = stripCost == 0 ? "free of charge" : "for " + stripCost + " scrap (" + homeplanet.core.Economy.stripFee() + " a system), paid from the Cargo Hold";
			String message = "Strip " + name + " for parts?\n\nWeapons, drones, augments, cargo, supplies and crew will be moved to the Cargo Hold.\n"
					+ "Her systems can be stripped too, " + fee + ":\n" + SystemsPanel.scrapPreview(wreck.getPlayerShip())
					+ (stripCost > have ? "The Cargo Hold and her own scrap come to " + have + ": not enough to strip her systems.\n" : "")
					+ "\nThe hull will be broken up and can never be recovered.";
			Object[] options = stripCost > have ? new Object[] {"Scrap, systems lost", "Cancel"}
					: new Object[] {stripCost == 0 ? "Scrap and strip" : "Scrap and strip (" + stripCost + ")", "Scrap, systems lost", "Cancel"};
			int c = JOptionPane.showOptionDialog(null, message, "Scrap Ship", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[options.length - 1]);
			if (c < 0 || c == options.length - 1) return;
			strip = options.length == 3 && c == 0;
		} else {
			String aboard = "Everything aboard will be moved to the Cargo Hold. Her systems are lost with the hull"
					+ (homeplanet.core.Economy.stripAllowed() ? " (none of them can be stored).\n" : ".\n");
			if (!confirmIrreversible("Scrap Ship", "Strip " + name + " for parts?\n\n" + aboard
					+ "The hull will be broken up and can never be recovered.", "Scrap")) return;
			strip = false;
		}
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
			if (strip) {
				scrapped.addAll(SystemsPanel.scrapSystems(from, tx));
				if (stripCost > 0) {
					if (to.getScrapAmt() < stripCost) throw new IOException("the Cargo Hold holds " + to.getScrapAmt() + " scrap, short of the " + stripCost + " stripping costs");
					to.setScrapAmt(to.getScrapAmt() - stripCost);
					scrapped.add("- " + stripCost + " scrap (stripping her systems)");
				}
			}
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
			Vault vault = Vault.get();
			Ship storageShip = vault.storage();
			Vault.Copy storageCopy;
			try { storageCopy = vault.readCopy(storageShip); } catch (IOException e) { throw new IOException("The Cargo Hold can't be read: " + e.getMessage()); }
			SavedGameState storage = storageCopy.save;
			File storageFile = storageShip.file();
			byte[] storageBefore = SafeFiles.read(storageFile);
			ShipState to = storage.getPlayerShip();
			to.setScrapAmt(to.getScrapAmt() + from.getScrapAmt() + price);
			for (CrewState c : SaveHelper.getOwnCrew(from)) {
				if (SaveHelper.hasBody(c) && SaveHelper.placeCrew(to, c, true)) to.getCrewList().add(c);
			}
			vault.begin().put(storageShip, storage, storageCopy.hash).commit();
			try {
				vault.remove(ship, null, Vault.Fate.SOLD);
			} catch (IOException e) {
				SafeFiles.write(storageFile, storageBefore); // she's still in the Junkyard: the hold mustn't keep her scrap and crew too
				storageShip.invalidate();
				throw e;
			}
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The sale of " + name + " was called off. Nothing was changed:\n" + e);
			return;
		}
		HistoryLog.entry("SELL", name + (auction ? " sold at auction" : " traded in") + " for " + price + " scrap; her scrap and crew to the Cargo Hold");
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
		if (DerelictsDialog.open(this)) init();
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
			Object[] opts = {"Browse derelicts...", "Close"};
			if (JOptionPane.showOptionDialog(null, "None of your ships are in the Junkyard. The foreman has some derelicts for sale, though.", "Salvage Ship",
					JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opts, opts[1]) == 0) browseDerelicts();
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
		Object[] options = {"Salvage", "Scrap", "Trade In", "Auction", "Destroy", "Derelicts...", "Cancel"};
		int choice = JOptionPane.showOptionDialog(null, panel, "Salvage Ship", JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE, null, options, options[6]); // Cancel is the default
		if (choice == 5) { browseDerelicts(); return; }
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
	private static final int REPORT_PIC_H = 200;

	public JPanel shipSummaryPanel(SavedGameState sgs) { return shipSummaryPanel(sgs, null, null); }
	/**
	 * As {@link #shipSummaryPanel(SavedGameState)}; with {@code rename}, clicking a crew member's name asks for a new one,
	 * and with {@code reroll} a die beside the Crew heading rolls new names (Commission).
	 */
	public JPanel shipSummaryPanel(SavedGameState sgs, final java.util.function.Consumer<CrewState> rename, Runnable reroll) {
		ShipState state = sgs.getPlayerShip();
		JPanel p = new JPanel(new java.awt.BorderLayout(18, 4));
		JLabel pic = reportPicture(sgs);
		if (pic != null) p.add(pic, java.awt.BorderLayout.NORTH);
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
			JLabel row = reportRow(crew, IconFactory.crewIcon(c), c.getName() + " (" + homeplanet.model.Crew.raceTitle(c) + ")");
			if (rename == null) continue;
			row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			row.setToolTipText("Click to rename " + c.getName());
			row.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mouseClicked(java.awt.event.MouseEvent e) { rename.accept(c); } });
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
