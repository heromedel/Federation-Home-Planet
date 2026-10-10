package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.GameGuard;
import homeplanet.core.HomePlanet;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * A ship's records: the earlier versions of her the station keeps (newest first), with Restore this version; her
 * voyage log (events in FTL, save by save); and her entries in history.log.
 */
public class ShipRecordsDialog extends JDialog {
	private static final Color ROW = new Color(36, 46, 56), GOLD = MenuTheme.GOLD, TXT = MenuTheme.WHITE, DIM = MenuTheme.GREY_GREEN;
	private final Ship ship;
	private final List<File> versions = new ArrayList<File>();
	private final JList<Version> list;
	private final JButton restore = new FtlButton("Restore this version", FtlFont.BODY, 200, 32);
	private boolean restored;

	/** A kept version, read once: when it was kept and where she was (or why it can't be read). */
	private static final class Version {
		final String when;
		String error;
		int sector, beacons, hull, maxHull, scrap, fuel;
		Version(File f) {
			when = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(f.lastModified()));
			try {
				SavedGameState gs = HomePlanet.savedGameParser.readSavedGame(f);
				sector = gs.getSectorNumber() + 1;
				beacons = gs.getTotalBeaconsExplored();
				hull = gs.getPlayerShip().getHullAmt();
				scrap = gs.getPlayerShip().getScrapAmt();
				fuel = gs.getPlayerShip().getFuelAmt();
				try { maxHull = DataManager.get().getShip(gs.getPlayerShipBlueprintId()).getHealth().amount; } catch (Exception e) { maxHull = 0; }
			} catch (Exception e) {
				error = "can't be read: " + e.getMessage();
			}
		}
	}

	/** Shows her records; true if an earlier version was restored (the Space Dock should take stock again). */
	public static boolean open(Component owner, Ship ship) {
		ShipRecordsDialog d = new ShipRecordsDialog(owner, ship);
		d.setVisible(true);
		return d.restored;
	}

	private ShipRecordsDialog(Component owner, Ship ship) {
		super(SwingUtilities.getWindowAncestor(owner), "Ship's records: " + ship.name, ModalityType.APPLICATION_MODAL);
		this.ship = ship;
		versions.addAll(Vault.get().kept(ship)); // her versions, and the copies kept for a reason of their own
		Collections.reverse(versions); // newest first
		DefaultListModel<Version> model = new DefaultListModel<Version>();
		for (File f : versions) model.addElement(new Version(f));
		list = new JList<Version>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new VersionRow());
		list.setBackground(RecordsLog.BG);
		list.setVisibleRowCount(Math.max(3, Math.min(Vault.KEEP, versions.size())));
		list.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) { restore.setEnabled(list.getSelectedIndex() >= 0 && !HomePlanet.immersiveMode); }
		});
		restore.setEnabled(false);
		restore.setToolTipText(HomePlanet.immersiveMode ? "Immersive Mode: what's done is done. Earlier versions can't be restored"
				: "Put " + homeplanet.model.Words.herObj() + " back as " + homeplanet.model.Words.she() + " was in this version. " + homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " current version is kept here too, so this can be undone.");
		restore.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { restoreSelected(); }
		});

		// the Kept versions page: what they are, the list, and Restore
		JPanel kept = new JPanel(new BorderLayout(0, 10));
		kept.setBackground(RecordsLog.BG);
		kept.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, GOLD), BorderFactory.createEmptyBorder(12, 14, 12, 14)));
		kept.add(text("The Home Planet Station keeps " + homeplanet.model.Words.her() + " last " + Vault.KEEP + " versions: the one before each change the station makes, "
				+ "and the one " + homeplanet.model.Words.she() + " had when " + homeplanet.model.Words.she() + " was boarded. Newest first.", DIM), BorderLayout.NORTH);
		JScrollPane vs = new JScrollPane(list);
		vs.getViewport().setBackground(RecordsLog.BG);
		vs.setBorder(BorderFactory.createLineBorder(RecordsLog.LINE));
		kept.add(versions.isEmpty() ? text("No earlier versions of " + homeplanet.model.Words.herObj() + " are kept yet.", DIM) : vs, BorderLayout.CENTER);
		JPanel restoreRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		restoreRow.setOpaque(false);
		restoreRow.add(restore);
		restoreRow.add(Box.createHorizontalStrut(14));
		javax.swing.JLabel note = new javax.swing.JLabel(HomePlanet.immersiveMode ? "Immersive Mode: what's done is done." : homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " current version is kept too, so a restore can be undone.");
		note.setFont(MenuTheme.TEXT_FONT);
		note.setForeground(DIM);
		restoreRow.add(note);
		if (!versions.isEmpty()) kept.add(restoreRow, BorderLayout.SOUTH);

		// three pages under her header: the voyage log, the station's log, and the kept versions
		List<homeplanet.core.EventLog.Entry> events = homeplanet.core.EventLog.read(Vault.get()); // her events (5.74)
		final java.awt.CardLayout cards = new java.awt.CardLayout();
		final JPanel pages = new JPanel(cards);
		pages.add(logTab(RecordsLog.voyage(homeplanet.core.EventLog.voyage(homeplanet.vault.ShipStore.entries(Vault.get().folderOf(ship)), ship.id), "Nothing logged yet. The Home Planet Station writes " + homeplanet.model.Words.her() + " voyage log as FTL saves " + homeplanet.model.Words.herObj() + ",\n"
				+ "while the station is open (and on Refresh): jumps, sectors, battles, crew, what came aboard, upgrades and repairs.")), "voyage");
		pages.add(logTab(RecordsLog.station(logEntries(events, ship), "No entries for " + homeplanet.model.Words.herObj() + " yet.", false)), "station");
		pages.add(kept, "kept");
		final Tab[] tabs = {new Tab("Voyage log", "Events in FTL, save by save (newest last)", "voyage"),
				new Tab("Station log", homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " entries in the station's log, newest last", "station"),
				new Tab("Kept versions (" + versions.size() + ")", homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " earlier versions, to look back on or restore", "kept")};
		JPanel tabRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		tabRow.setOpaque(false);
		for (final Tab t : tabs) {
			t.onClick = new Runnable() {
				public void run() {
					for (Tab o : tabs) { o.on = o == t; o.repaint(); }
					cards.show(pages, t.page);
				}
			};
			if (tabRow.getComponentCount() > 0) tabRow.add(Box.createHorizontalStrut(6));
			tabRow.add(t);
		}
		tabs[0].on = true;
		JPanel logPanel = new JPanel(new BorderLayout(0, 0));
		logPanel.setOpaque(false);
		logPanel.add(tabRow, BorderLayout.NORTH);
		logPanel.add(pages, BorderLayout.CENTER);

		JPanel body = new JPanel(new BorderLayout(0, 12));
		body.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
		body.add(header(ship), BorderLayout.NORTH);
		body.add(logPanel, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 10));
		JButton close = new FtlButton("Close", FtlFont.BODY, 100, 32);
		close.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { dispose(); }
		});
		buttons.add(close);
		getContentPane().add(body, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(close);
		pack();
		fitScreen();
		setLocationRelativeTo(getOwner());
	}

	/** Her name in gold, and under it her class, current sector, sectors visited, journeys, beacons explored and ships defeated. */
	private static JComponent header(Ship ship) {
		SavedGameState gs = ship.save();
		List<String> facts = new ArrayList<String>();
		if (gs != null) {
			try {
				String cls = String.valueOf(DataManager.get().getShip(gs.getPlayerShipBlueprintId()).getShipClass());
				if (!cls.isEmpty() && !"null".equals(cls)) facts.add(cls);
			} catch (Exception e) { } // a ship the game data doesn't have: no class shown
		}
		if (gs != null) facts.add("Current sector " + (gs.getSectorNumber() + 1));
		facts.add("Sectors visited " + homeplanet.vault.VoyageLog.visited(Vault.get(), ship));
		facts.add("Journeys " + homeplanet.vault.VoyageLog.journeys(Vault.get(), ship));
		if (gs != null) {
			facts.add("Beacons explored " + gs.getTotalBeaconsExplored());
			facts.add("Ships defeated " + gs.getTotalShipsDefeated());
		}
		homeplanet.vault.TradeMark mark = homeplanet.vault.TradeMark.of(ship);
		if (mark != null) {
			facts.add("Original owner " + mark.original);
			if (!mark.from.equals(mark.original)) facts.add("Received from " + mark.from);
		}
		JPanel p = new JPanel(new BorderLayout(0, 4));
		p.setOpaque(false);
		p.add(ftlText(ship.name.toUpperCase(), FtlFont.MENU, GOLD), BorderLayout.NORTH);
		p.add(text(String.join("   \u00b7   ", facts), DIM), BorderLayout.CENTER);
		JComponent rule = new JComponent() { protected void paintComponent(Graphics g) { g.setColor(new Color(70, 86, 96)); g.drawLine(0, 6, getWidth(), 6); } };
		rule.setPreferredSize(new Dimension(10, 10));
		p.add(rule, BorderLayout.SOUTH);
		return p;
	}
	/** A line of text in FTL's font. */
	private static JComponent ftlText(String s, FtlFont font, Color c) {
		final BufferedImage img = font.render(s, c);
		JComponent comp = new JComponent() { protected void paintComponent(Graphics g) { g.drawImage(img, 0, 0, null); } };
		comp.setPreferredSize(new Dimension(img.getWidth(), img.getHeight() + 2));
		return comp;
	}
	/** A line of text in the style guide's normal text (Sans Serif 12), wrapping if the window is narrow. */
	private static JComponent text(String s, Color c) {
		javax.swing.JTextArea t = new javax.swing.JTextArea(s);
		t.setEditable(false);
		t.setFocusable(false);
		t.setLineWrap(true);
		t.setWrapStyleWord(true);
		t.setOpaque(false);
		t.setFont(MenuTheme.TEXT_FONT);
		t.setForeground(c);
		return t;
	}
	/** No bigger than the screen (less its taskbar): with Windows' display scaling, the window can be larger than it. */
	private void fitScreen() {
		java.awt.GraphicsConfiguration gc = getOwner() != null ? getOwner().getGraphicsConfiguration() : getGraphicsConfiguration();
		java.awt.Rectangle b = gc.getBounds();
		java.awt.Insets in = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc);
		int maxW = b.width - in.left - in.right - 20, maxH = b.height - in.top - in.bottom - 20;
		if (getWidth() > maxW || getHeight() > maxH) setSize(Math.min(getWidth(), maxW), Math.min(getHeight(), maxH));
		setLocationRelativeTo(getOwner());
	}
	/** A log in its scroll pane, scrolled to its latest lines. */
	private static JScrollPane logTab(RecordsLog log) {
		final JScrollPane sp = new JScrollPane(log);
		sp.getViewport().setBackground(RecordsLog.BG);
		sp.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, GOLD)); // the tabs sit on this line
		sp.setPreferredSize(new Dimension(760, 380));
		sp.getVerticalScrollBar().setUnitIncrement(22);
		SwingUtilities.invokeLater(new Runnable() { // the latest entries in view, once it's laid out
			public void run() { sp.getViewport().revalidate(); javax.swing.JScrollBar b = sp.getVerticalScrollBar(); b.setValue(b.getMaximum()); }
		});
		return sp;
	}

	/** A tab over the logs: gold when it's the one showing. */
	private static final class Tab extends JComponent {
		boolean on;
		Runnable onClick;
		private final String label;
		final String page;
		Tab(String label, String tip, String page) {
			this.label = label;
			this.page = page;
			setToolTipText(tip);
			setPreferredSize(new Dimension(FtlFont.BODY.width(label) + 36, 32));
			setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			addMouseListener(new java.awt.event.MouseAdapter() {
				@Override public void mousePressed(java.awt.event.MouseEvent e) { if (onClick != null) onClick.run(); }
			});
		}
		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0;
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(on ? GOLD : ROW);
			g.fillRoundRect(0, 0, getWidth(), getHeight() + 8, 8, 8); // square at the bottom, on the gold line
			BufferedImage t = FtlFont.BODY.render(label, on ? FtlButton.TEXT_HOT : TXT);
			g.drawImage(t, (getWidth() - t.getWidth()) / 2, (getHeight() - t.getHeight()) / 2, null);
		}
	}

	/** A kept version's row: when, where she was, her hull (a bar), scrap and fuel. */
	private static final class VersionRow extends JComponent implements javax.swing.ListCellRenderer<Version> {
		private Version v;
		private boolean selected;
		VersionRow() { setPreferredSize(new Dimension(700, 30)); }
		public Component getListCellRendererComponent(JList<? extends Version> list, Version value, int index, boolean isSelected, boolean hasFocus) {
			v = value;
			selected = isSelected;
			return this;
		}
		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0;
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(RecordsLog.BG);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(selected ? MenuTheme.SELECT : ROW);
			g.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 6, 6);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			java.awt.FontMetrics fm = g.getFontMetrics(MenuTheme.TEXT_FONT);
			int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
			draw(g, v.when, 12, y, selected ? TXT : DIM, false);
			if (v.error != null) { draw(g, v.error, 140, y, DIM, false); return; }
			draw(g, "Sector " + v.sector, 140, y, TXT, true);
			draw(g, v.beacons + (v.beacons == 1 ? " beacon" : " beacons"), 212, y, DIM, false);
			draw(g, "Hull", 312, y, DIM, false);
			boolean low = v.maxHull > 0 && v.hull * 3 < v.maxHull;
			if (v.maxHull > 0) {
				int bx = 342, by = getHeight() / 2 - 5, bw = 100;
				g.setColor(new Color(20, 26, 30));
				g.fillRect(bx, by, bw, 10);
				g.setColor(low ? RecordsLog.BAD : v.hull * 3 < v.maxHull * 2 ? GOLD : RecordsLog.GOOD);
				g.fillRect(bx, by, Math.max(0, Math.min(bw, bw * v.hull / v.maxHull)), 10);
				g.setColor(RecordsLog.LINE);
				g.drawRect(bx, by, bw, 10);
			}
			draw(g, v.maxHull > 0 ? v.hull + "/" + v.maxHull : Integer.toString(v.hull), v.maxHull > 0 ? 452 : 342, y, low ? RecordsLog.BAD : TXT, false);
			draw(g, "Scrap " + v.scrap, 530, y, TXT, false);
			draw(g, "Fuel " + v.fuel, 620, y, TXT, false);
		}
		private static void draw(Graphics2D g, String s, int x, int baseline, Color c, boolean bold) {
			g.setFont(bold ? MenuTheme.LABEL_FONT : MenuTheme.TEXT_FONT);
			g.setColor(c);
			g.drawString(s, x, baseline);
		}
	}

	/** Her entries in the station's log: those that name her (by her id in their fields, or her name or id in the headline). */
	static List<homeplanet.core.EventLog.Entry> logEntries(List<homeplanet.core.EventLog.Entry> events, Ship ship) {
		List<homeplanet.core.EventLog.Entry> out = new ArrayList<homeplanet.core.EventLog.Entry>();
		for (homeplanet.core.EventLog.Entry e : homeplanet.core.EventLog.ofLog(events, "station")) {
			String head = e.get("headline", e.human);
			if (ship.id.equals(e.get("ship_id")) || head.contains(ship.id) || (ship.name != null && !ship.name.isEmpty() && head.contains(ship.name))) out.add(e);
		}
		return out;
	}

	/** A kept version in words, for the confirmation. */
	private static String describe(Version v) {
		if (v.error != null) return v.when + "   (" + v.error + ")";
		return String.format("%s   sector %d, %d beacons, hull %d, scrap %d, fuel %d", v.when, v.sector, v.beacons, v.hull, v.scrap, v.fuel);
	}

	private void restoreSelected() {
		int i = list.getSelectedIndex();
		if (i < 0) return;
		File version = versions.get(i);
		if (HomePlanet.immersiveMode) return;
		if (!HomePlanet.confirmNo(this, "Restore " + ship.name + " to this version?\n\n" + describe(list.getSelectedValue()) + "\n\n"
				+ "Everything since then is undone: " + homeplanet.model.Words.her() + " crew, cargo, scrap and journey go back to how they were.\n"
				+ homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " current version is kept in " + homeplanet.model.Words.her() + " records, so you can restore it again.", "Restore this version")) return;
		if (ship.isBoarded() && !GameGuard.allows(this, "restore " + homeplanet.model.Words.herObj())) return;
		try {
			Vault.get().restore(ship, version);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not restore " + homeplanet.model.Words.herObj() + "; " + homeplanet.model.Words.her() + " current save was not changed:\n" + e.getMessage());
			return;
		}
		restored = true;
		JOptionPane.showMessageDialog(this, ship.name + " is restored.", "Restore this version", JOptionPane.INFORMATION_MESSAGE);
		dispose();
	}
}
