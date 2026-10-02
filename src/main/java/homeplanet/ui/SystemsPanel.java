package homeplanet.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.image.BufferedImage;
import javax.swing.JList;
import javax.swing.JOptionPane;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.xml.ShipBlueprint;
import net.blerf.ftl.xml.SystemBlueprint;

import homeplanet.parser.SaveHelper;
import homeplanet.resource.ResourceClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Systems panel in the Cargo Bay: store a system from the boarded ship in the Cargo Bay (keeping its level),
 * or install a stored one. Stored systems live in a text file next to the storage save, one per line: "cloaking 3".
 * Like every Cargo Bay trade, nothing is written until Save; Reset/Pick throw the changes away.
 */
public class SystemsPanel {
	private static final Logger log = LoggerFactory.getLogger(SystemsPanel.class);
	static final String NO_ROOM = "This ship model was not designed with room for this system";
	static final String INSTALLED = "This system is already installed";
	static final String STARTING = "Standard equipment on this ship model. FTL rebuilds it if removed";
	static final String MEDBAY = "The Medbay is standard equipment. Install a Clone Bay to replace it";

	/** A system in the Cargo Bay. */
	static class Stored {
		final String id;
		final int level;
		/** Its broken bars: a damaged system keeps them in storage, and brings them aboard (the Dry Dock mends them). */
		final int broken;
		Stored(String id, int level) { this(id, level, 0); }
		Stored(String id, int level, int broken) { this.id = id; this.level = level; this.broken = Math.max(0, broken); }
		// level 0 = no level of its own (a Clone Bay takes the Medbay's level)
		public String toString() { return DryDockShop.systemTitle(id) + (level > 0 ? " (level " + level + ")" : "") + (broken > 0 ? " (" + broken + " broken)" : ""); }
	}
	/** A system installed on the boarded ship. */
	static class Installed {
		final SystemType type;
		final int level;
		Installed(SystemType type, int level) { this.type = type; this.level = level; }
		public String toString() { return DryDockShop.systemTitle(type.getId()) + " (level " + level + ")"; }
	}

	private final CargoBayUI bay;
	private final List<Stored> stored = new ArrayList<Stored>();
	private final List<String> unknownLines = new ArrayList<String>(); // lines naming a system this version doesn't know: written back as they were
	private final List<String> changes = new ArrayList<String>(); // for the history log
	private boolean storedSomething = false;
	/** What the Dry Dock's work since the last save costs the Cargo Hold (less what selling stored systems paid in): paid on Save, dropped on Reset. */
	private int bill = 0;

	SystemsPanel(CargoBayUI bay) { this.bay = bay; }


	/** The stored-systems list that goes with the storage hold (storage-systems.txt in the vault). */
	File file() {
		return bay.homeSave == null ? null : homeplanet.vault.Vault.get().systemsFile();
	}

	/** Rebuilds the panel from the boarded ship and the file (throws away unsaved changes). */

	// ---- The Refit tab ----
	private final JPanel panel = new JPanel(null);
	private final JLabel pic = new JLabel();
	private FtlButton info;
	private final CargoParts.Label name = new CargoParts.Label("", FtlFont.MENU, CargoParts.GOLD, 0);
	private final CargoParts.Label sub = new CargoParts.Label("", FtlFont.BODY, CargoParts.DIM, 0);
	/** What the Cargo Hold can still spend at the Dry Dock, always in view under her name. */
	private final CargoParts.Label holdLbl = new CargoParts.Label("", FtlFont.BODY, CargoParts.GOLD, 0);
	private final JPanel lists = new JPanel(null);
	/** The installed and stored systems scroll inside the top of the column; Layout and model stays pinned below. */
	private final JPanel sysList = new JPanel(null);
	private javax.swing.JScrollPane sysScroll;
	/** Row width: the column less room for the scroll bar. */
	private static final int ROW_W = 550, LAYOUT_H = 118;
	private FtlButton remodelBtn, retrofitBtn;
	private final CargoParts.Label layoutLbl = new CargoParts.Label("", FtlFont.BODY, CargoParts.TEXT, -1);
	private final CargoParts.Label layoutHint = new CargoParts.Label("", FtlFont.BODY, CargoParts.DIM, -1);

	JPanel panel() {
		if (remodelBtn != null) return panel;
		panel.setOpaque(false);
		pic.setBounds(16, 8, 640, 470);
		pic.setHorizontalAlignment(JLabel.CENTER);
		panel.add(pic);
		name.setBounds(16, 490, 640, 26);
		name.setToolTipText("Click for her report, and to rename her");
		name.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		name.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mouseClicked(java.awt.event.MouseEvent e) { bay.showCurrentShipInfo(); } });
		panel.add(name);
		sub.setBounds(16, 520, 640, 16);
		panel.add(sub);
		holdLbl.setBounds(16, 542, 640, 16);
		holdLbl.setToolTipText("The Dry Dock's work is paid from the Cargo Hold when you Save; this is what it has left to spend");
		panel.add(holdLbl);
		info = new CargoParts.IconButton(CargoParts.infoIcon(), "Her report, and to rename her", new ActionListener() { public void actionPerformed(ActionEvent e) { bay.showCurrentShipInfo(); } });
		panel.add(info);
		lists.setOpaque(false);
		lists.setBounds(700, 8, 564, CargoBayUI.H - 52 - 26 - 12);
		panel.add(lists);
		sysList.setOpaque(false);
		sysScroll = new javax.swing.JScrollPane(sysList, javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sysScroll.setOpaque(false);
		sysScroll.getViewport().setOpaque(false);
		sysScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		sysScroll.getVerticalScrollBar().setUI(new MenuTheme.DarkScrollBarUI(new Color(214, 230, 222, 170), new Color(40, 50, 58, 200)));
		sysScroll.getVerticalScrollBar().setPreferredSize(new java.awt.Dimension(10, 10));
		sysScroll.getVerticalScrollBar().setOpaque(false);
		sysScroll.getVerticalScrollBar().setUnitIncrement(32);
		remodelBtn = new FtlButton("Remodel...", FtlFont.MENU, 180, 34);
		remodelBtn.setToolTipText("Move her systems and doors, or overhaul her deck plan (a retrofitted ship only)");
		remodelBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String why = homeplanet.parser.Clearance.customReason(); // Immersive Mode: a Captain's work
				if (why != null) { JOptionPane.showMessageDialog(bay, why, "Remodel", JOptionPane.INFORMATION_MESSAGE); return; }
				if (RemodelDialog.open(bay)) { load(); refresh(); bay.refreshTrade(); } // the remodel saved the Cargo Bay: nothing left unsaved
			}
		});
		retrofitBtn = new FtlButton("Retrofit", FtlFont.MENU, 220, 34);
		retrofitBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { retrofit(); } });
		return panel;
	}

	/** Rebuilds from the boarded ship and the file (throws away unsaved changes). */
	void init() {
		changes.clear();
		bill = 0;
		storedSomething = false;
		load();
		refresh();
	}
	String helpText() {
		return "Store takes a system off at its level. Up upgrades it; the Dry Dock repairs her hull. Greyed out: hover to see why.";
	}

	/** Lays out the installed and stored systems and the layout section. */
	void refresh() {
		if (remodelBtn == null) panel();
		lists.removeAll();
		if (bay.currentPath == null) { lists.repaint(); return; }
		ShipState bs = bay.currentSave.getPlayerShip();
		net.blerf.ftl.xml.ShipBlueprint bp = DataManager.get().getShip(bs.getShipBlueprintId());
		BufferedImage img = bp == null ? null : bay.parent.getResourceImage("img/ship/" + bp.getGraphicsBaseName() + "_base.png", false);
		pic.setIcon(img == null ? null : new javax.swing.ImageIcon(SpaceDockUI.fitImage(img, 640, 470)));
		name.setText(bay.currentSave.getPlayerShipName().toUpperCase());
		// her grey line centred under her name, with the info button to its left (as on the Trade tab)
		String cls = CargoBayUI.shipClass(bs);
		sub.setText(cls);
		int tw = CargoParts.width(cls, FtlFont.BODY), gx = 16 + (640 - tw - 30) / 2;
		info.setBounds(gx, 517, 24, 22);
		sub.setBounds(gx + 30, 520, tw + 4, 16);
		holdLbl.setText("Cargo Hold: " + hold() + " scrap" + (bill > 0 ? " (" + bill + " spent here, paid on Save)" : bill < 0 ? " (" + (-bill) + " to come from sales, on Save)" : ""));
		int w = ROW_W, y = 0;
		sysList.removeAll();
		CargoParts.Header h1 = new CargoParts.Header("Installed systems", false);
		h1.setBounds(0, y, w, 22);
		sysList.add(h1);
		y += 26;
		int i = 0;
		for (SystemType t : SystemType.values()) {
			SystemState st = bs.getSystem(t);
			if (st == null || st.getCapacity() <= 0) continue;
			final SystemType type = t;
			String why = refitReason(bs, t);
			int fee = homeplanet.core.Economy.removalFee();
			SysRow r = new SysRow(DryDockShop.systemTitle(t.getId()), st.getCapacity(), "Store", why,
					why == null ? "Take the " + DryDockShop.systemTitle(t.getId()) + " off the ship; it keeps its level" + (fee > 0 ? " (the Dry Dock charges " + fee + " scrap)" : "") : why,
					new ActionListener() { public void actionPerformed(ActionEvent e) { storeSystem(type); } });
			int up = upgradePrice(bs, t), broken = st.getDamagedBars();
			if (broken > 0) { // mended first: then she can be upgraded
				int scrap = hold(), fix = broken * homeplanet.parser.Pricing.SYSTEM_REPAIR;
				r.addButton("Fix: " + fix, 78, ROW_W - 66 - 82, scrap >= fix,
						scrap >= fix ? "Mend the " + DryDockShop.systemTitle(t.getId()) + "'s " + broken + (broken == 1 ? " broken bar" : " broken bars") + " for " + fix + " scrap ("
								+ homeplanet.parser.Pricing.SYSTEM_REPAIR + " a bar)" : "Mending " + broken + (broken == 1 ? " bar" : " bars") + " costs " + fix + " scrap; the Cargo Hold has " + scrap,
						new ActionListener() { public void actionPerformed(ActionEvent e) { repairSystem(type); } });
			} else if (up > 0) {
				int scrap = hold();
				r.addButton("Up: " + up, 78, ROW_W - 66 - 82, scrap >= up,
						scrap >= up ? "Upgrade the " + DryDockShop.systemTitle(t.getId()) + " to level " + (st.getCapacity() + 1) + " for " + up + " scrap"
						: "Upgrading to level " + (st.getCapacity() + 1) + " costs " + up + " scrap; the Cargo Hold has " + scrap,
						new ActionListener() { public void actionPerformed(ActionEvent e) { upgradeSystem(type); } });
			}
			r.setBounds(0, y + i * 32, w, 28);
			sysList.add(r);
			i++;
		}
		y += i * 32 + 12;
		y = dryDock(bs, y, w);
		CargoParts.Header h2 = new CargoParts.Header("Systems in the Cargo Bay", false);
		h2.setBounds(0, y, w, 22);
		sysList.add(h2);
		y += 26;
		if (stored.isEmpty()) {
			CargoParts.Label none = new CargoParts.Label("None stored yet. Store one from the list above.", FtlFont.BODY, CargoParts.DIM, -1);
			none.setBounds(8, y + 6, w, 16);
			sysList.add(none);
			y += 32;
		}
		int j = 0;
		for (final Stored s : stored) {
			String why = reason(s.id);
			SysRow r = new SysRow(DryDockShop.systemTitle(s.id) + (s.broken > 0 ? " (" + s.broken + " broken)" : ""), s.level, "Install", why,
					why == null ? "Install the " + DryDockShop.systemTitle(s.id) + " on " + bay.currentSave.getPlayerShipName() : why,
					new ActionListener() { public void actionPerformed(ActionEvent e) { installSystem(s); } });
			if (homeplanet.core.HomePlanet.sellSystems()) r.addSell(salePrice(s), new ActionListener() { public void actionPerformed(ActionEvent e) { sellSystem(s); } });
			r.setBounds(0, y + j * 32, w, 28);
			sysList.add(r);
			j++;
		}
		y += j * 32 + 12;
		sysList.setPreferredSize(new java.awt.Dimension(ROW_W, y - 12));
		int H = lists.getHeight();
		sysScroll.setBounds(0, 0, 564, H - LAYOUT_H);
		lists.add(sysScroll);
		sysList.revalidate();
		w = 564;
		y = H - LAYOUT_H + 6;
		CargoParts.Header h3 = new CargoParts.Header("Layout and model", false);
		h3.setBounds(0, y, w, 22);
		lists.add(h3);
		y += 28;
		boolean retro = homeplanet.parser.Retrofit.isRetrofitted(bs);
		boolean inGame = homeplanet.parser.Retrofit.inGame(bs);
		String id = bs.getShipBlueprintId();
		String txt = !retro ? "Original" : homeplanet.parser.CompanionMod.isRemodelId(id)
				? "Custom " + homeplanet.parser.CompanionMod.numberOf(id) : "Original";
		layoutLbl.setText("Layout:  " + txt + (inGame ? "" : "  (not patched in)") + (retro ? "   ·   retrofitted: any system can be stored" : ""));
		layoutLbl.setColor(inGame ? CargoParts.TEXT : CargoParts.ORANGE);
		layoutLbl.setToolTipText(!retro ? "Her systems are where the ship model puts them"
				: "Blueprint " + id + (inGame ? "" : ". The game data doesn't have it yet: install the mod (Settings > Patch mods) before launching"));
		layoutLbl.setBounds(0, y, w, 18);
		lists.add(layoutLbl);
		y += 24;
		remodelBtn.setEnabled(retro);
		String remodelLock = homeplanet.parser.Clearance.customReason(); // Immersive Mode: a Captain's work
		remodelBtn.setToolTipText(remodelLock == null ? "Move her systems and doors, or overhaul her deck plan (a retrofitted ship only)"
				: "<html>" + homeplanet.parser.XmlText.text(remodelLock).replace("\n", "<br>") + "</html>");
		remodelBtn.setBounds(0, y, 180, 34);
		lists.add(remodelBtn);
		retrofitBtn.setText(retro ? "Undo Retrofit" : "Retrofit");
		retrofitBtn.setToolTipText(retro ? "Return this ship to her original model (needs all her standard equipment installed)"
				: "Let this ship remove any system, including standard equipment (needs the " + homeplanet.parser.Retrofit.MOD_NAME + ")");
		retrofitBtn.setBounds(192, y, 220, 34);
		lists.add(retrofitBtn);
		y += 42;
		layoutHint.setText(retro ? "Remodel moves systems and doors, or overhauls her deck plan."
				: "Retrofit first to remodel her or to store standard equipment.");
		layoutHint.setBounds(0, y, w, 16);
		lists.add(layoutHint);
		lists.revalidate();
		lists.repaint();
	}

	/** A system line: name, level bars, and its Store or Install button (greyed out with the reason on hover). */
	private static class SysRow extends JComponent {
		final String title; final int level; final boolean ok;
		SysRow(String title, int level, String action, String why, String tip, ActionListener a) {
			this.title = title; this.level = level; this.ok = why == null;
			setLayout(null);
			setToolTipText(tip);
			FtlButton b = new FtlButton(action, FtlFont.BODY, action.length() > 5 ? 78 : 62, 22);
			b.setEnabled(ok);
			if (action.isEmpty()) b.setVisible(false); // a row with only its own extra buttons
			b.setToolTipText(tip);
			b.addActionListener(a);
			b.setBounds(ROW_W - (action.length() > 5 ? 82 : 66), 3, action.length() > 5 ? 78 : 62, 22);
			add(b);
		}
		/** HR1: a Sell button beside the row's own. */
		void addSell(int price, ActionListener a) {
			addButton("Sell", 62, ROW_W - 82 - 70, true, "Sell it to the station for " + price + " scrap (paid to the boarded ship)", a);
		}
		/** Another button, left of the row's own. */
		void addButton(String text, int bw, int x, boolean enabled, String tip, ActionListener a) {
			FtlButton b = new FtlButton(text, FtlFont.BODY, bw, 22);
			b.setEnabled(enabled);
			b.setToolTipText(tip);
			b.addActionListener(a);
			b.setBounds(x, 3, bw, 22);
			add(b);
		}
		@Override protected void paintComponent(java.awt.Graphics g0) {
			java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
			CargoParts.paintBox(g, 0, 0, getWidth(), getHeight(), CargoParts.BOX_LINE);
			CargoParts.text(g, FtlFont.BODY.fit(title, 230), FtlFont.BODY, ok ? CargoParts.TEXT : CargoParts.DIM, 10, 7);
			CargoParts.text(g, level > 0 ? "level " + level : "", FtlFont.BODY, CargoParts.DIM, 250, 7);
			for (int k = 0; k < Math.min(level, 8); k++) {
				g.setColor(ok ? new Color(120, 230, 120) : new Color(90, 130, 95));
				g.fillRect(310 + k * 8, 9, 6, 11);
			}
			if (level == 0) CargoParts.text(g, "uses the Medbay's level", FtlFont.BODY, CargoParts.DIM, 250, 7); // (below 0: a Dry Dock row, no level)
			g.dispose();
		}
	}

	// ---- The file ----

	private void load() {
		stored.clear();
		unknownLines.clear();
		File f = file();
		if (f == null || !f.exists()) return;
		BufferedReader r = null;
		try {
			r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
			String line;
			while ((line = r.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] p = line.split("\\s+");
				if (SystemType.findById(p[0]) == null) { log.warn("Unknown system in {}: {}", f.getName(), line); unknownLines.add(line); continue; }
				int level = 1;
				try { if (p.length > 1) level = Math.max(1, Integer.parseInt(p[1])); } catch (NumberFormatException e) { }
				if (SystemType.findById(p[0]) == SystemType.CLONEBAY) level = 0; // the level stays with the Medbay
				int broken = 0;
				try { if (p.length > 2) broken = Math.max(0, Integer.parseInt(p[2])); } catch (NumberFormatException e) { }
				if (level > 0) broken = Math.min(broken, level);
				stored.add(new Stored(p[0], level, broken));
			}
		} catch (Exception e) {
			log.error("Could not read " + f, e);
			homeplanet.core.HomePlanet.showErrorDialog("Could not read the list of systems stored in the Cargo Bay:\n" + f + "\n\n" + e);
		} finally {
			try { if (r != null) r.close(); } catch (Exception e) { }
		}
	}

	/** Adds the stored-systems list to the Cargo Bay's save, if it changed (written with the ships, or not at all). */
	/** The Cargo Hold's scrap the Dry Dock can still spend: what it holds (as the Trade tab has it, when it's the partner) less the bill. */
	int hold() {
		int have;
		if (bay.partnerIsStorage() && bay.tradeState != null) have = bay.tradeState.getScrapAmt();
		else { try { have = homeplanet.vault.Vault.get().storageScrap(); } catch (Exception e) { have = 0; } }
		return have - bill;
	}
	/** Puts a price on the bill, if the Cargo Hold can pay it. */
	private boolean charge(int price) {
		if (price > hold()) return false;
		bill += price;
		return true;
	}
	/**
	 * The bill, paid from the Cargo Hold in the same save as her (the hold is the Trade tab's partner, or read fresh for
	 * it). Returns what was taken from the partner in memory, for the caller to put back if the save fails.
	 */
	int payBill(homeplanet.vault.Vault.Transaction tx) throws java.io.IOException {
		if (bill == 0) return 0;
		if (bay.partnerIsStorage() && bay.tradeState != null) {
			if (bay.tradeState.getScrapAmt() < bill) throw new java.io.IOException("The Cargo Hold holds " + bay.tradeState.getScrapAmt() + " scrap; the Dry Dock's work costs " + bill);
			bay.tradeState.setScrapAmt(bay.tradeState.getScrapAmt() - bill); // saved with the partner; init() reloads it after
			return bill;
		}
		homeplanet.vault.Vault v = homeplanet.vault.Vault.get();
		homeplanet.vault.Ship hold = v.storage();
		homeplanet.vault.Vault.Copy c = v.readCopy(hold);
		ShipState s = c.save.getPlayerShip();
		if (s.getScrapAmt() < bill) throw new java.io.IOException("The Cargo Hold holds " + s.getScrapAmt() + " scrap; the Dry Dock's work costs " + bill);
		s.setScrapAmt(s.getScrapAmt() - bill);
		tx.put(hold, c.save, c.hash);
		return 0;
	}
	void addTo(homeplanet.vault.Vault.Transaction tx) {
		if (changes.isEmpty()) return;
		File f = file();
		if (f == null) return;
		StringBuilder sb = new StringBuilder(HEADER).append("\n");
		for (Stored s : stored) sb.append(line(s.id, s.level, s.broken)).append("\n");
		for (String u : unknownLines) sb.append(u).append("\n"); // lines this version can't use are kept, not dropped
		tx.put(f, sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}
	public static final String HEADER = "# Ship systems stored in the Cargo Bay: <system id> <level> [<broken bars>] (a Clone Bay has no level: it uses the Medbay's)";
	static String line(String id, int level) { return line(id, level, 0); }
	/** A stored system's line; broken bars go third (an older station reads the first two, and keeps the rest of its lines). */
	public static String line(String id, int level, int broken) {
		if (broken > 0) return id + " " + level + " " + broken;
		return level > 0 ? id + " " + level : id;
	}

	/** True if a system was taken off the ship: then the file is written before the ship, so a failed save can't lose it. */
	boolean storedSomething() { return storedSomething; }
	List<String> changes() { return changes; }

	// ---- Rules ----

	/** Why this stored system can't go on the boarded ship, or null if it can. */
	String reason(String sysId) {
		if (bay.currentSave == null || bay.currentPath == null) return NO_ROOM;
		ShipState bs = bay.currentSave.getPlayerShip();
		SystemType type = SystemType.findById(sysId);
		ShipBlueprint bp = DataManager.get().getShip(bs.getShipBlueprintId());
		if (type == null || bp == null || bp.getSystemList() == null || bp.getSystemList().getSystemRoom(type) == null) return NO_ROOM;
		SystemState st = bs.getSystem(type);
		if (st != null && st.getCapacity() > 0) return INSTALLED;
		return null;
	}

	/** Is this system standard equipment on the ship's model (FTL rebuilds it if its room is left empty)? */
	static boolean isStarting(ShipState ship, SystemType type) {
		ShipBlueprint bp = DataManager.get().getShip(ship.getShipBlueprintId());
		if (bp == null || bp.getSystemList() == null) return false;
		ShipBlueprint.SystemList.SystemRoom[] r = bp.getSystemList().getSystemRoom(type);
		if (r == null || r.length == 0) return false;
		return r[0].getStart() == null || r[0].getStart().booleanValue();
	}
	static boolean hasRoomFor(ShipState ship, SystemType type) {
		ShipBlueprint bp = DataManager.get().getShip(ship.getShipBlueprintId());
		return bp != null && bp.getSystemList() != null && bp.getSystemList().getSystemRoom(type) != null;
	}
	/** Why this installed system can't be stored, or null if it can. */
	static String storeReason(ShipState ship, SystemType type) {
		if (type == SystemType.MEDBAY) return homeplanet.parser.Retrofit.isRetrofitted(ship) ? null : MEDBAY;
		if (type == SystemType.CLONEBAY) return hasRoomFor(ship, SystemType.MEDBAY) ? null : STARTING; // storing it leaves a Medbay in the room
		if (isStarting(ship, type)) {
			return homeplanet.parser.Retrofit.blankAvailable(ship) ? STARTING + ". Press Retrofit (below) to allow removing it" : STARTING + ". Send the " + homeplanet.parser.Retrofit.MOD_NAME + " to FTL via Slipstream (Settings > Patch mods) to allow retrofitting";
		}
		return null;
	}

	/** Why Refit can't take this system off her: the removal rule first, then {@link #storeReason}. */
	static String refitReason(ShipState ship, SystemType type) {
		if (homeplanet.core.Economy.removalFee() == homeplanet.core.Economy.NOT_ALLOWED)
			return "The Dry Dock doesn't take systems off ships" + (homeplanet.core.HomePlanet.immersiveMode ? "" : " (Settings, Rules: Refit removal)");
		return storeReason(ship, type);
	}

	// ---- Actions ----

	private void storeSystem(SystemType type) {
		Installed sel = new Installed(type, 0);
		SavedGameState save = bay.currentSave;
		ShipState bs = save.getPlayerShip();
		String why = refitReason(bs, sel.type);
		if (why != null) {
			JOptionPane.showMessageDialog(bay, why + ".", "Systems", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (sel.type == SystemType.WEAPONS && !bs.getWeaponList().isEmpty()) {
			JOptionPane.showMessageDialog(bay, "Move the weapons to cargo first.", "Systems", JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (sel.type == SystemType.DRONE_CTRL && !bs.getDroneList().isEmpty()) {
			JOptionPane.showMessageDialog(bay, "Move the drones to cargo first.", "Systems", JOptionPane.WARNING_MESSAGE);
			return;
		}
		SystemState st = bs.getSystem(sel.type);
		if (st == null || st.getCapacity() <= 0) return;
		String name = DryDockShop.systemTitle(sel.type.getId());
		int broken = st.getDamagedBars(); // a damaged system goes into storage damaged: storing is no free repair
		int fee = homeplanet.core.Economy.removalFee();
		if (fee > 0) {
			if (hold() < fee) {
				JOptionPane.showMessageDialog(bay, "The Dry Dock charges " + fee + " scrap to take the " + name + " off; the Cargo Hold has " + hold() + ".", "Systems", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			if (!homeplanet.core.HomePlanet.confirmNo(bay, "The Dry Dock charges " + fee + " scrap to take the " + name + " off " + save.getPlayerShipName() + ".\nThe Cargo Hold pays (on Save). Store it?", "Systems")) return;
			charge(fee);
			changes.add("Paid " + fee + " scrap to take the " + name + " off " + save.getPlayerShipName());
		}
		int level = st.getCapacity();
		if (sel.type == SystemType.CLONEBAY) {
			// The level belongs to the room: the Medbay left behind keeps it, the Clone Bay goes without one
			SystemState mb = bs.getSystem(SystemType.MEDBAY);
			if (mb == null) { mb = new SystemState(SystemType.MEDBAY); bs.addSystem(mb); }
			mb.setCapacity(level);
			mb.setPower(st.getPower());
			mb.setDamagedBars(broken); // the room's damage stays with the room
			mb.setIonizedBars(0);
			clear(st);
			stored.add(new Stored(sel.type.getId(), 0));
			changes.add("Stored Clone Bay from " + save.getPlayerShipName() + " (a level " + level + " Medbay took its place)");
		} else {
			clear(st);
			stored.add(new Stored(sel.type.getId(), level, broken));
			changes.add("Stored " + name + " (level " + level + (broken > 0 ? ", " + broken + " broken" : "") + ") from " + save.getPlayerShipName());
		}
		storedSomething = true;
		homeplanet.parser.Retrofit.syncStations(bs); // its room no longer has a station
		log.debug("Stored {} level {} from {}", sel.type, level, save.getPlayerShipName());
		changed();
	}

	private void installSystem(Stored sel) {
		String why = reason(sel.id);
		if (why != null) {
			JOptionPane.showMessageDialog(bay, why + ".", "Systems", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		SavedGameState save = bay.currentSave;
		ShipState bs = save.getPlayerShip();
		SystemType type = SystemType.findById(sel.id);
		String name = DryDockShop.systemTitle(sel.id);
		int level = sel.level;
		// Medbay and Clone Bay share a room, and the level belongs to the room
		SystemType other = type == SystemType.CLONEBAY ? SystemType.MEDBAY : type == SystemType.MEDBAY ? SystemType.CLONEBAY : null;
		SystemState os = other == null ? null : bs.getSystem(other);
		if (os != null && os.getCapacity() > 0) {
			level = os.getCapacity();
			if (other == SystemType.CLONEBAY) { // an old stored Medbay going in: the Clone Bay comes back to the Cargo Bay
				stored.add(new Stored(SystemType.CLONEBAY.getId(), 0));
				storedSomething = true;
				changes.add("Stored Clone Bay from " + save.getPlayerShipName() + " (replaced by the Medbay)");
			} // a Medbay being replaced by a Clone Bay is simply gone: its level carries over
			clear(os);
		}
		if (level <= 0) level = 1;
		SystemBlueprint sbp = DataManager.get().getSystem(sel.id);
		if (sbp != null && sbp.getMaxPower() > 0) level = Math.min(level, sbp.getMaxPower());
		SystemState st = bs.getSystem(type);
		if (st == null) {
			st = new SystemState(type);
			bs.addSystem(st);
		}
		int broken = Math.min(sel.broken, level); // a damaged system comes aboard damaged: Fix mends it
		st.setCapacity(level);
		st.setPower(type.isSubsystem() ? level - broken : 0); // unpowered; subsystems don't use reactor power
		st.setDamagedBars(broken);
		st.setIonizedBars(0);
		SaveHelper.ensureAdvancedInfo(bs, save.getFileFormat()); // Clone Bay, Battery, Cloaking, Hacking, Mind Control keep extra data
		homeplanet.parser.Retrofit.syncStations(bs); // a manned system needs its station in the save
		stored.remove(sel);
		changes.add("Installed " + name + " (level " + level + (broken > 0 ? ", " + broken + " broken" : "") + ") on " + save.getPlayerShipName());
		log.debug("Installed {} level {} on {}", type, level, save.getPlayerShipName());
		changed();
	}

	// ---- The Dry Dock: upgrades and repairs, at FTL's prices, paid by the boarded ship ----

	/** Upgrading this installed system one level: FTL's upgrade cost, or -1 at its limit (FTL's, or her room's). */
	static int upgradePrice(ShipState bs, SystemType t) {
		SystemState st = bs.getSystem(t);
		if (st == null || st.getCapacity() <= 0) return -1;
		ShipBlueprint bp = DataManager.get().getShip(bs.getShipBlueprintId());
		ShipBlueprint.SystemList.SystemRoom[] rooms = bp == null || bp.getSystemList() == null ? null : bp.getSystemList().getSystemRoom(t);
		if (rooms != null && rooms.length > 0 && rooms[0].getMaxPower() != null && st.getCapacity() >= rooms[0].getMaxPower()) return -1;
		return homeplanet.parser.Pricing.upgrade(t.getId(), st.getCapacity());
	}
	/** Her hull at full strength (her model's). */
	static int maxHull(ShipState bs) {
		ShipBlueprint bp = DataManager.get().getShip(bs.getShipBlueprintId());
		return bp == null || bp.getHealth() == null ? bs.getHullAmt() : bp.getHealth().amount;
	}
	/** The Dry Dock rows: reactor power and hull repairs. Returns the y below them. */
	private int dryDock(ShipState bs, int y, int w) {
		CargoParts.Header h = new CargoParts.Header("Dry Dock: the Cargo Hold pays (" + hold() + " scrap)", false);
		h.setBounds(0, y, w, 22);
		sysList.add(h);
		y += 26;
		int scrap = hold();
		int bars = bs.getReservePowerCapacity(), rp = homeplanet.parser.Pricing.reactorBar(bars + 1);
		SysRow reactor = new SysRow("Reactor", bars, "", null, "Reactor power: " + bars + " bars", null);
		if (bars < homeplanet.parser.Pricing.REACTOR_MAX) {
			reactor.addButton("Up: " + rp, 78, ROW_W - 82, scrap >= rp, scrap >= rp ? "One more bar of reactor power for " + rp + " scrap" : "One more bar costs " + rp + " scrap; the Cargo Hold has " + scrap,
					new ActionListener() { public void actionPerformed(ActionEvent e) { upgradeReactor(); } });
		}
		reactor.setBounds(0, y, w, 28);
		sysList.add(reactor);
		y += 32;
		int hull = bs.getHullAmt(), max = maxHull(bs), each = homeplanet.parser.Pricing.hullRepair();
		int gap = max - hull, all = gap * each;
		SysRow repair = new SysRow("Hull " + hull + " / " + max, -1, "", null, "Hull repairs: " + each + " scrap a point (The Federation charges a premium)", null);
		if (hull < max) {
			// as FTL's stores: one point at a time, or all of it at once, each with its price
			repair.addButton("+1: " + each, 78, ROW_W - 82 - 82, scrap >= each, scrap >= each ? "Repair 1 point of hull for " + each + " scrap"
					: "A point costs " + each + " scrap; the Cargo Hold has " + scrap,
					new ActionListener() { public void actionPerformed(ActionEvent e) { repairHull(1); } });
			repair.addButton("All: " + all, 78, ROW_W - 82, scrap >= all, scrap >= all ? "Repair all " + gap + (gap == 1 ? " point" : " points") + " for " + all + " scrap"
					: "Repairing all " + gap + " points costs " + all + " scrap; the Cargo Hold has " + scrap + " (use +1 for what it can pay)",
					new ActionListener() { public void actionPerformed(ActionEvent e) { repairHull(Integer.MAX_VALUE); } });
		}
		repair.setBounds(0, y, w, 28);
		sysList.add(repair);
		y += 32;
		int breaches = bs.getBreachMap().size();
		if (breaches > 0) {
			int seal = breaches * homeplanet.parser.Pricing.BREACH_REPAIR;
			SysRow br = new SysRow("Hull breaches: " + breaches, -1, "", null, "Holes in her hull, venting air: " + homeplanet.parser.Pricing.BREACH_REPAIR + " scrap each to seal", null);
			br.addButton("Seal: " + seal, 78, ROW_W - 82, scrap >= seal, scrap >= seal ? "Seal all " + breaches + " for " + seal + " scrap" : "Sealing them costs " + seal + " scrap; the Cargo Hold has " + scrap,
					new ActionListener() { public void actionPerformed(ActionEvent e) { sealBreaches(); } });
			br.setBounds(0, y, w, 28);
			sysList.add(br);
			y += 32;
		}
		y += 12;
		return y;
	}
	private void upgradeSystem(SystemType t) {
		ShipState bs = bay.currentSave.getPlayerShip();
		int price = upgradePrice(bs, t);
		if (price <= 0 || !charge(price)) return;
		SystemState st = bs.getSystem(t);
		st.setCapacity(st.getCapacity() + 1);
		if (t.isSubsystem()) st.setPower(st.getCapacity()); // subsystems run at their full level
		changes.add("Upgraded " + DryDockShop.systemTitle(t.getId()) + " to level " + st.getCapacity() + " for " + price + " scrap");
		changed();
		bay.help("Upgraded the " + DryDockShop.systemTitle(t.getId()) + " to level " + st.getCapacity() + ". Save makes it official.");
	}
	private void upgradeReactor() {
		ShipState bs = bay.currentSave.getPlayerShip();
		int bars = bs.getReservePowerCapacity(), price = homeplanet.parser.Pricing.reactorBar(bars + 1);
		if (bars >= homeplanet.parser.Pricing.REACTOR_MAX || !charge(price)) return;
		bs.setReservePowerCapacity(bars + 1);
		changes.add("Reactor upgraded to " + (bars + 1) + " bars for " + price + " scrap");
		changed();
		bay.help("Reactor power is now " + (bars + 1) + ". Save makes it official.");
	}
	private void repairSystem(SystemType t) {
		ShipState bs = bay.currentSave.getPlayerShip();
		SystemState st = bs.getSystem(t);
		if (st == null || st.getDamagedBars() <= 0) return;
		int n = st.getDamagedBars(), price = n * homeplanet.parser.Pricing.SYSTEM_REPAIR;
		if (!charge(price)) return;
		st.setDamagedBars(0);
		if (t.isSubsystem()) st.setPower(st.getCapacity()); // subsystems run at their full level
		changes.add("Mended the " + DryDockShop.systemTitle(t.getId()) + " (" + n + (n == 1 ? " bar" : " bars") + ") for " + price + " scrap");
		changed();
		bay.help("Mended the " + DryDockShop.systemTitle(t.getId()) + ". Save makes it official.");
	}
	private void sealBreaches() {
		ShipState bs = bay.currentSave.getPlayerShip();
		int n = bs.getBreachMap().size(), price = n * homeplanet.parser.Pricing.BREACH_REPAIR;
		if (n == 0 || !charge(price)) return;
		bs.getBreachMap().clear();
		changes.add("Sealed " + n + (n == 1 ? " hull breach" : " hull breaches") + " for " + price + " scrap");
		changed();
		bay.help("Sealed " + n + (n == 1 ? " breach" : " breaches") + ". Save makes it official.");
	}
	/** Repairs up to this many points of hull (all of it: Integer.MAX_VALUE), if the Cargo Hold can pay for them all. */
	private void repairHull(int points) {
		ShipState bs = bay.currentSave.getPlayerShip();
		int each = homeplanet.parser.Pricing.hullRepair();
		int n = Math.min(maxHull(bs) - bs.getHullAmt(), points);
		if (n <= 0 || !charge(n * each)) return;
		bs.setHullAmt(bs.getHullAmt() + n);
		changes.add("Hull repaired by " + n + " for " + n * each + " scrap");
		changed();
		bay.help("Repaired " + n + " hull for " + n * each + " scrap. Save makes it official.");
	}

	/** The systems stored in the Cargo Hold, as the Cargo Bay holds them now (unsaved sales gone). */
	java.util.List<Stored> storedList() { return new ArrayList<Stored>(stored); }
	/** Sells a stored system (the Trade tab's Stored systems, with no ship aboard): the hold is paid on Save. */
	void sell(Stored s) { if (stored.contains(s)) sellSystem(s); }
	/** HR1: what a stored system sells for. */
	static int salePrice(Stored s) {
		int full = homeplanet.parser.Pricing.systemSale(s.id, s.level, homeplanet.core.Economy.SYSTEM_SALE_PERCENT);
		return Math.max(0, full - s.broken * homeplanet.parser.Pricing.brokenBarValue(s.id)); // a buyer takes off what mending it costs
	}
	private void sellSystem(Stored sel) {
		String name = DryDockShop.systemTitle(sel.id) + (sel.level > 0 ? " (level " + sel.level + ")" : "");
		int price = salePrice(sel);
		if (!homeplanet.core.HomePlanet.confirmNo(bay, "Sell the " + name + " for " + price + " scrap?\nThe Cargo Hold is paid (on Save).", "Sell")) return;
		stored.remove(sel);
		bill -= price;
		changes.add("Sold " + name + " for " + price + " scrap (the Cargo Hold was paid)");
		changed();
	}

	/**
	 * Scrapping and stripping (where allowed): moves the wreck's storable systems into the stored-systems file
	 * (standard equipment and the Medbay stay with the hull; damaged systems are lost). Returns log lines.
	 */
	static List<String> scrapSystems(ShipState wreck, homeplanet.vault.Vault.Transaction tx) throws java.io.IOException {
		List<String> lines = new ArrayList<String>();
		List<String> add = new ArrayList<String>();
		for (SystemType t : SystemType.values()) {
			SystemState st = wreck.getSystem(t);
			if (st == null || st.getCapacity() <= 0 || storeReason(wreck, t) != null) continue;
			String name = DryDockShop.systemTitle(t.getId());
			int level = t == SystemType.CLONEBAY ? 0 : st.getCapacity(), broken = level > 0 ? st.getDamagedBars() : 0; // damaged systems are kept, damaged
			add.add(line(t.getId(), level, broken));
			lines.add("+ " + name + (level > 0 ? " (level " + level + (broken > 0 ? ", " + broken + " broken" : "") + ")" : "") + " (system)");
		}
		if (add.isEmpty()) return lines;
		File f = homeplanet.vault.Vault.get().systemsFile();
		List<String> keep = new ArrayList<String>();
		if (f.exists()) keep.addAll(java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8));
		else keep.add(HEADER);
		keep.addAll(add);
		tx.put(f, (String.join("\n", keep) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return lines;
	}
	/** How many of her systems stripping would move to the Cargo Bay (storable ones: damaged ones go too, damaged). */
	static int strippable(ShipState wreck) {
		int n = 0;
		for (SystemType t : SystemType.values()) {
			SystemState st = wreck.getSystem(t);
			if (st != null && st.getCapacity() > 0 && storeReason(wreck, t) == null) n++;
		}
		return n;
	}
	/** What scrapping would move and lose, as lines for the confirmation. */
	static String scrapPreview(ShipState wreck) {
		List<String> moved = new ArrayList<String>(), lost = new ArrayList<String>();
		for (SystemType t : SystemType.values()) {
			SystemState st = wreck.getSystem(t);
			if (st == null || st.getCapacity() <= 0 || storeReason(wreck, t) != null) continue;
			String name = DryDockShop.systemTitle(t.getId()) + (t == SystemType.CLONEBAY ? "" : " (level " + st.getCapacity() + (st.getDamagedBars() > 0 ? ", " + st.getDamagedBars() + " broken" : "") + ")");
			moved.add(name);
		}
		StringBuilder sb = new StringBuilder();
		if (moved.isEmpty()) sb.append("She has no systems that can be stored; standard equipment stays with the hull.\n");
		for (int i = 0; i < moved.size(); i += 3) {
			boolean more = i + 3 < moved.size();
			sb.append(i == 0 ? "Systems to the Cargo Bay: " : "    ").append(String.join(", ", moved.subList(i, Math.min(i + 3, moved.size())))).append(more ? ",\n" : "\n");
		}
		if (!lost.isEmpty()) sb.append("Damaged, lost with the hull: ").append(String.join(", ", lost)).append("\n");
		return sb.toString();
	}

	private static void clear(SystemState st) {
		st.setCapacity(0);
		st.setPower(0);
		st.setDamagedBars(0);
		st.setIonizedBars(0);
	}

	/**
	 * Retrofit (or undo it) the boarded ship: moves her onto the blank copy of her model from the companion mod.
	 * Like every Cargo Bay change it's made official by Save (so a half-finished trade is never written early).
	 */
	private void retrofit() {
		SavedGameState save = bay.currentSave;
		ShipState ship = save.getPlayerShip();
		String name = save.getPlayerShipName();
		boolean undo = homeplanet.parser.Retrofit.isRetrofitted(ship);
		if (!undo && !homeplanet.parser.Retrofit.blankAvailable(ship)) {
			JOptionPane.showMessageDialog(bay, "Retrofit needs the " + homeplanet.parser.Retrofit.MOD_NAME + ".\n"
					+ "Send it to FTL via Slipstream with Settings > Patch mods (it comes with Federation Home Planet), then restart The Home Planet Station.", "Retrofit", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (undo) {
			List<String> missing = homeplanet.parser.Retrofit.missingStandard(ship);
			if (!missing.isEmpty()) {
				List<String> names = new ArrayList<String>();
				for (String id : missing) names.add(DryDockShop.systemTitle(id));
				JOptionPane.showMessageDialog(bay, name + " is missing standard equipment of her original model:\n" + String.join(", ", names)
						+ "\n\nInstall these on her first; otherwise FTL would rebuild them for free.", "Undo Retrofit", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			String moves = "";
			if (homeplanet.parser.Retrofit.isRemodeled(ship)) {
				java.util.Map<String, homeplanet.parser.CompanionMod.Sys> now = homeplanet.parser.CompanionMod.layoutOf(DataManager.get().getShip(ship.getShipBlueprintId()));
				java.util.Map<String, homeplanet.parser.CompanionMod.Sys> orig = homeplanet.parser.CompanionMod.layoutOf(
						DataManager.get().getShip(homeplanet.parser.Retrofit.vanillaId(ship.getShipBlueprintId())));
				StringBuilder sb = new StringBuilder();
				for (homeplanet.parser.CompanionMod.Sys o : orig.values()) {
					homeplanet.parser.CompanionMod.Sys n = now.get(o.id);
					if (n != null && n.room != o.room) sb.append("\n  ").append(DryDockShop.systemTitle(o.id)).append(": room ").append(n.room).append(" -> room ").append(o.room);
				}
				homeplanet.parser.CompanionMod.Remodel mine = homeplanet.parser.CompanionMod.find(
						homeplanet.parser.CompanionMod.load(), ship.getShipBlueprintId());
				if (mine != null && mine.doors != null) sb.append("\n  Doors: back to the model's own");
				if (sb.length() > 0) moves = "\nHer remodel is set aside (kept on file) and these move back:" + sb + "\n";
			}
			if (JOptionPane.showConfirmDialog(bay, "Return " + name + " to her original ship model?\nHer standard equipment can no longer be stored afterwards.\n" + moves,
					"Undo Retrofit", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
		} else {
			if (JOptionPane.showConfirmDialog(bay, "Prepare " + name + " so any of her systems can be removed, including standard equipment.\n\n"
					+ "Warning: FTL may no longer count her as the original ship model for achievements.\n"
					+ "She will only load in FTL once the " + homeplanet.parser.Retrofit.MOD_NAME + " has been sent to FTL via Slipstream.",
					"Retrofit", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
		}
		String before = ship.getShipBlueprintId();
		homeplanet.parser.Retrofit.apply(save, undo);
		if (!undo) {
			// She may have had a custom layout before an Undo Retrofit: offer it back
			String vanilla = homeplanet.parser.Retrofit.vanillaId(before);
			for (homeplanet.parser.CompanionMod.Remodel r : homeplanet.parser.CompanionMod.load()) {
				if (!vanilla.equals(r.base) || !name.equals(r.ship)) continue;
				Object[] options = {"Custom layout", "Original layout"};
				int c = JOptionPane.showOptionDialog(bay, name + " had a custom layout on file (" + r.id + ", made " + r.made + ").\nUse it again?",
						"Retrofit", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
				if (c == 0) homeplanet.parser.Retrofit.switchTo(save, r.id);
				break;
			}
		}
		changes.add((undo ? "Undo Retrofit: " : "Retrofit: ") + before + " -> " + ship.getShipBlueprintId());
		changed();
		JOptionPane.showMessageDialog(bay, (undo ? name + " will return to her original ship model." : name + " will be retrofitted: any of her systems can be stored.")
				+ "\nPress Save to make it official.", undo ? "Undo Retrofit" : "Retrofit", JOptionPane.INFORMATION_MESSAGE);
	}

	private void changed() {
		bay.markDirty();
		refresh();
		bay.shop.systemsChanged(); // the Shop greys out systems the ship already has
		bay.refreshTrade(); // Drone Control and Weapons change the slots
	}
}
