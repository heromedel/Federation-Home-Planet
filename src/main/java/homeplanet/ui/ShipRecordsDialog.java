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
import java.nio.charset.StandardCharsets;
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
import homeplanet.core.HistoryLog;
import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * A ship's records: the earlier versions of her the station keeps (newest first), with Restore this version; her
 * voyage log (events in FTL, save by save); and her entries in history.log.
 */
public class ShipRecordsDialog extends JDialog {
	private static final Color ROW = new Color(36, 46, 56), GOLD = FtlButton.GOLD, TXT = MenuTheme.TEXT, DIM = MenuTheme.DIM;
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
		versions.addAll(Vault.get().history(ship));
		Collections.reverse(versions); // newest first
		DefaultListModel<Version> model = new DefaultListModel<Version>();
		for (File f : versions) model.addElement(new Version(f));
		list = new JList<Version>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new VersionRow());
		list.setBackground(RecordsLog.BG);
		list.setVisibleRowCount(Math.max(3, Math.min(5, versions.size())));
		list.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) { restore.setEnabled(list.getSelectedIndex() >= 0 && !HomePlanet.immersiveMode); }
		});
		restore.setEnabled(false);
		restore.setToolTipText(HomePlanet.immersiveMode ? "Immersive Mode: what's done is done. Earlier versions can't be restored"
				: "Put her back as she was in this version. Her current version is kept here too, so this can be undone.");
		restore.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { restoreSelected(); }
		});

		JPanel top = new JPanel(new BorderLayout(0, 8));
		top.setOpaque(false);
		JPanel heads = new JPanel(new BorderLayout(0, 6));
		heads.setOpaque(false);
		heads.add(header(ship), BorderLayout.NORTH);
		JPanel kept = new JPanel(new BorderLayout(0, 2));
		kept.setOpaque(false);
		kept.add(text("KEPT VERSIONS", FtlFont.MENU, GOLD), BorderLayout.NORTH);
		JPanel intro = new JPanel(new BorderLayout(0, 2));
		intro.setOpaque(false);
		intro.add(text("The Home Planet Station keeps her last " + Vault.KEEP + " versions: the one before each change the station makes,", FtlFont.BODY, DIM), BorderLayout.NORTH);
		intro.add(text("and the one she had when she was boarded. Newest first.", FtlFont.BODY, DIM), BorderLayout.SOUTH);
		kept.add(intro, BorderLayout.SOUTH);
		heads.add(kept, BorderLayout.SOUTH);
		top.add(heads, BorderLayout.NORTH);
		JScrollPane vs = new JScrollPane(list);
		vs.getViewport().setBackground(RecordsLog.BG);
		vs.setBorder(BorderFactory.createLineBorder(RecordsLog.LINE));
		top.add(versions.isEmpty() ? text("No earlier versions of her are kept yet.", FtlFont.BODY, DIM) : vs, BorderLayout.CENTER);
		JPanel restoreRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		restoreRow.setOpaque(false);
		restoreRow.add(restore);
		restoreRow.add(Box.createHorizontalStrut(14));
		restoreRow.add(text(HomePlanet.immersiveMode ? "Immersive Mode: what's done is done." : "Her current version is kept too, so a restore can be undone.",
				FtlFont.BODY, DIM));
		if (!versions.isEmpty()) top.add(restoreRow, BorderLayout.SOUTH);

		String voyage = homeplanet.vault.VoyageLog.read(Vault.get(), ship);
		final java.awt.CardLayout cards = new java.awt.CardLayout();
		final JPanel logs = new JPanel(cards);
		logs.add(logTab(RecordsLog.voyage(voyage, "Nothing logged yet. The Home Planet Station writes her voyage log as FTL saves her,\n"
				+ "while the station is open (and on Refresh): jumps, sectors, battles, crew, what came aboard, upgrades and repairs.")), "voyage");
		logs.add(logTab(RecordsLog.station(logLines(ship), "No entries for her yet.")), "station");
		final Tab voyageTab = new Tab("Voyage log", "Events in FTL, save by save (newest last)"),
				stationTab = new Tab("Station log", "Her entries in the station's log (history.log), newest last");
		voyageTab.on = true;
		voyageTab.onClick = new Runnable() { public void run() { voyageTab.on = true; stationTab.on = false; cards.show(logs, "voyage"); voyageTab.repaint(); stationTab.repaint(); } };
		stationTab.onClick = new Runnable() { public void run() { voyageTab.on = false; stationTab.on = true; cards.show(logs, "station"); voyageTab.repaint(); stationTab.repaint(); } };
		JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		tabs.setOpaque(false);
		tabs.add(voyageTab);
		tabs.add(Box.createHorizontalStrut(6));
		tabs.add(stationTab);
		JPanel logPanel = new JPanel(new BorderLayout(0, 0));
		logPanel.setOpaque(false);
		logPanel.add(tabs, BorderLayout.NORTH);
		logPanel.add(logs, BorderLayout.CENTER);

		JPanel body = new JPanel(new BorderLayout(0, 14));
		body.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
		body.add(top, BorderLayout.NORTH);
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
		setLocationRelativeTo(getOwner());
	}

	/** Her name in gold, and under it her class, the sectors she has visited, beacons explored and ships defeated. */
	private static JComponent header(Ship ship) {
		SavedGameState gs = ship.save();
		List<String> facts = new ArrayList<String>();
		if (gs != null) {
			try {
				String cls = String.valueOf(DataManager.get().getShip(gs.getPlayerShipBlueprintId()).getShipClass());
				if (!cls.isEmpty() && !"null".equals(cls)) facts.add(cls);
			} catch (Exception e) { } // a ship the game data doesn't have: no class shown
		}
		facts.add("Sectors visited " + homeplanet.vault.VoyageLog.visited(Vault.get(), ship));
		if (gs != null) {
			facts.add("Beacons explored " + gs.getTotalBeaconsExplored());
			facts.add("Ships defeated " + gs.getTotalShipsDefeated());
		}
		JPanel p = new JPanel(new BorderLayout(0, 4));
		p.setOpaque(false);
		p.add(text(ship.name.toUpperCase(), FtlFont.MENU, GOLD), BorderLayout.NORTH);
		p.add(text(String.join("   -   ", facts), FtlFont.BODY, DIM), BorderLayout.CENTER);
		JComponent rule = new JComponent() { protected void paintComponent(Graphics g) { g.setColor(new Color(70, 86, 96)); g.drawLine(0, 6, getWidth(), 6); } };
		rule.setPreferredSize(new Dimension(10, 10));
		p.add(rule, BorderLayout.SOUTH);
		return p;
	}
	/** A line of text in FTL's font. */
	private static JComponent text(String s, FtlFont font, Color c) {
		final BufferedImage img = font.render(s, c);
		JComponent comp = new JComponent() { protected void paintComponent(Graphics g) { g.drawImage(img, 0, 0, null); } };
		comp.setPreferredSize(new Dimension(img.getWidth(), img.getHeight() + 2));
		return comp;
	}
	/** A log in its scroll pane, scrolled to its latest lines. */
	private static JScrollPane logTab(RecordsLog log) {
		final JScrollPane sp = new JScrollPane(log);
		sp.getViewport().setBackground(RecordsLog.BG);
		sp.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, GOLD)); // the tabs sit on this line
		sp.setPreferredSize(new Dimension(900, 320));
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
		Tab(String label, String tip) {
			this.label = label;
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
		VersionRow() { setPreferredSize(new Dimension(860, 34)); }
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
			int y = (getHeight() - FtlFont.BODY.render("Ag", TXT).getHeight()) / 2;
			draw(g, v.when, 12, y, selected ? TXT : DIM);
			if (v.error != null) { draw(g, v.error, 176, y, DIM); return; }
			draw(g, "Sector " + v.sector, 176, y, TXT);
			draw(g, v.beacons + (v.beacons == 1 ? " beacon" : " beacons"), 266, y, DIM);
			draw(g, "Hull", 390, y, DIM);
			boolean low = v.maxHull > 0 && v.hull * 3 < v.maxHull;
			if (v.maxHull > 0) {
				int bx = 428, by = getHeight() / 2 - 5, bw = 100;
				g.setColor(new Color(20, 26, 30));
				g.fillRect(bx, by, bw, 10);
				g.setColor(low ? RecordsLog.BAD : v.hull * 3 < v.maxHull * 2 ? GOLD : RecordsLog.GOOD);
				g.fillRect(bx, by, Math.max(0, Math.min(bw, bw * v.hull / v.maxHull)), 10);
				g.setColor(RecordsLog.LINE);
				g.drawRect(bx, by, bw, 10);
			}
			draw(g, v.maxHull > 0 ? v.hull + "/" + v.maxHull : Integer.toString(v.hull), v.maxHull > 0 ? 538 : 428, y, low ? RecordsLog.BAD : TXT);
			draw(g, "Scrap " + v.scrap, 620, y, TXT);
			draw(g, "Fuel " + v.fuel, 730, y, TXT);
		}
		private static void draw(Graphics2D g, String s, int x, int y, Color c) { g.drawImage(FtlFont.BODY.render(s, c), x, y, null); }
	}

	/** Her entries in history.log: those whose headline names her (by name or by her file's id). */
	static String logLines(Ship ship) {
		File f = HistoryLog.file();
		StringBuilder out = new StringBuilder();
		if (!f.isFile()) return "";
		try {
			String[] lines = new String(SafeFiles.read(f), StandardCharsets.UTF_8).split("\r?\n");
			boolean mine = false;
			for (String line : lines) {
				if (!line.startsWith("  ")) mine = line.contains(ship.id) || (ship.name != null && !ship.name.isEmpty() && line.contains(ship.name));
				if (mine) out.append(line).append('\n');
			}
		} catch (Exception e) {
			return "The Home Planet Station could not read its log (" + f + "): " + e.getMessage();
		}
		return out.length() == 0 ? "No entries for her yet." : out.toString();
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
				+ "Everything since then is undone: her crew, cargo, scrap and journey go back to how they were.\n"
				+ "Her current version is kept in her records, so you can restore it again.", "Restore this version")) return;
		if (ship.isBoarded() && !GameGuard.allows(this, "restore her")) return;
		try {
			Vault.get().restore(ship, version);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not restore her; her current save was not changed:\n" + e.getMessage());
			return;
		}
		restored = true;
		JOptionPane.showMessageDialog(this, ship.name + " is restored.", "Restore this version", JOptionPane.INFORMATION_MESSAGE);
		dispose();
	}
}
