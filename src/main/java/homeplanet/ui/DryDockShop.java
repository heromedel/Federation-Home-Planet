package homeplanet.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.event.ChangeListener;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.StoreItem;
import net.blerf.ftl.parser.SavedGameParser.StoreItemType;
import net.blerf.ftl.parser.SavedGameParser.StoreShelf;
import net.blerf.ftl.parser.SavedGameParser.StoreState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.model.Items;
import homeplanet.vault.Ship;
import homeplanet.parser.SaveHelper;
import homeplanet.resource.ResourceClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Dry Dock shop in the Cargo Bay: buy from the shop at any of your ships' current beacons.
 * Purchases change the saves in memory only; the Cargo Bay's Save button writes them (buyer first,
 * then each shop), and Reset/Pick throw them away like any other unsaved change.
 */
class DryDockShop {
	private static final Logger log = LoggerFactory.getLogger(DryDockShop.class);
	// Store prices for supplies (per unit)
	static final int FUEL_PRICE = homeplanet.parser.Pricing.FUEL, MISSILE_PRICE = homeplanet.parser.Pricing.MISSILE, DRONE_PART_PRICE = homeplanet.parser.Pricing.DRONE_PART;
	// Where the panel sits (top left, level with the Save panel)
	static final int PX = 6, PY = 48; // below the Return to Dock panel

	enum Kind { HEADER, ITEM, SYSTEM, CREW, FUEL, MISSILES, PARTS }

	/** One line in the shop list. Items remember which save and shelf they came from. */
	static class Entry {
		Kind kind;
		String title;      // text shown in the list
		String id;         // item blueprint id (items only)
		String shipName;   // whose shop
		Ship ship;         // the ship whose beacon the store is at
		int shelf, slot;   // position in that shop (items only)
		int price, count;
		Entry(Kind kind, String title) { this.kind = kind; this.title = title; }
		public String toString() { return title; }
	}

	private final CargoBayUI bay;
	private final Map<Ship, SavedGameState> otherSaves = new LinkedHashMap<Ship, SavedGameState>(); // saves read just for the shop
	private final Map<Ship, String> otherHashes = new LinkedHashMap<Ship, String>(); // their files' fingerprints as read
	private final Set<Ship> dirty = new LinkedHashSet<Ship>();
	private final List<String> purchases = new ArrayList<String>(); // for the history log
	// What purchases changed on each buyer, keyed like HistoryLog.inventory, so the TRADE entry leaves them out
	private final Map<SavedGameState, Map<String, Integer>> bought = new java.util.IdentityHashMap<SavedGameState, Map<String, Integer>>();


	DryDockShop(CargoBayUI bay) { this.bay = bay; }

	// ---- The Shop tab ----
	private final JPanel panel = new JPanel(null);
	private final JPanel content = new JPanel(null);
	private final JScrollPane scroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
	private final CargoParts.Label scrapLbl = new CargoParts.Label("", FtlFont.MENU, CargoParts.TEXT, -1);
	private final CargoParts.Label storesLbl = new CargoParts.Label("", FtlFont.BODY, CargoParts.DIM, 1);
	private FtlButton buyerBtn, info;
	private final JLabel shipPic = new JLabel();
	private final CargoParts.Label classLbl = new CargoParts.Label("", FtlFont.BODY, CargoParts.DIM, -1);
	private final Aboard aboard = new Aboard();
	private boolean toStorage = false; // buying for the Cargo Bay rather than the picked ship

	/** The Shop tab (built once; its contents are rebuilt from the saves). */
	JPanel panel() {
		if (buyerBtn != null) return panel;
		panel.setOpaque(false);
		// the Trade tab's left side: her picture, a small label, the drop-down, and her grey line with an info button
		int dx = 16 + CargoBayUI.DROP_IN;
		shipPic.setBounds(16, 8, 120, 62);
		shipPic.setHorizontalAlignment(JLabel.CENTER);
		shipPic.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		shipPic.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (!toStorage) bay.showCurrentShipInfo(); } });
		panel.add(shipPic);
		CargoParts.Label bf = new CargoParts.Label("BUYING FOR", FtlFont.BODY, CargoParts.DIM, -1);
		bf.setBounds(dx, 6, CargoBayUI.DROP_W, 16);
		panel.add(bf);
		buyerBtn = CargoBayUI.dropButton();
		buyerBtn.setBounds(dx, 22, CargoBayUI.DROP_W, 30);
		info = new CargoParts.IconButton(CargoParts.infoIcon(), "", new ActionListener() {
			public void actionPerformed(ActionEvent e) { if (toStorage) bay.storageInfo(); else bay.showCurrentShipInfo(); }
		});
		info.setBounds(dx, 55, 24, 22);
		panel.add(info);
		classLbl.setBounds(dx + 30, 58, CargoBayUI.DROP_W - 30, 16);
		panel.add(classLbl);
		buyerBtn.setToolTipText("Buy for the ship picked in the Cargo Bay, or for the Cargo Hold");
		buyerBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
				javax.swing.JMenuItem a = new javax.swing.JMenuItem(bay.currentSave.getPlayerShipName() + " (your ship)");
				javax.swing.JMenuItem b = new javax.swing.JMenuItem("Cargo Hold (items, supplies and systems)");
				a.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { toStorage = false; rebuild(); } });
				b.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { toStorage = true; rebuild(); } });
				m.add(a); m.add(b);
				CargoParts.darkPopup(m);
				m.show(buyerBtn, 0, buyerBtn.getHeight());
			}
		});
		panel.add(buyerBtn);
		scrapLbl.setBounds(dx + CargoBayUI.DROP_W + 20, 22, 260, 30);
		panel.add(scrapLbl);
		aboard.setBounds(dx + CargoBayUI.DROP_W + 20, 54, 1264 - (dx + CargoBayUI.DROP_W + 20), 22);
		panel.add(aboard);
		CargoParts.Label title = new CargoParts.Label("STORES AT YOUR SHIPS' BEACONS", FtlFont.MENU, CargoParts.GOLD, 1);
		title.setBounds(664, 6, 600, 26);
		panel.add(title);
		storesLbl.setBounds(664, 34, 600, 16);
		panel.add(storesLbl);
		content.setOpaque(false);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUI(new MenuTheme.DarkScrollBarUI(new Color(214, 230, 222, 170), new Color(40, 50, 58, 200)));
		scroll.getVerticalScrollBar().setPreferredSize(new java.awt.Dimension(12, 12));
		scroll.getVerticalScrollBar().setUnitIncrement(34);
		scroll.getVerticalScrollBar().setOpaque(false);
		scroll.setBounds(16, 84, 1248, CargoBayUI.H - 52 - 26 - 88);
		panel.add(scroll);
		return panel;
	}

	/** Rebuilds from the saves (throws away any unsaved purchases). */
	void init() {
		otherSaves.clear();
		otherHashes.clear();
		dirty.clear();
		purchases.clear();
		bought.clear();
		toStorage = false;
		rebuild();
	}

	/** Why this store system can't be bought for the chosen buyer, or null. */
	String systemReason(String sysId) {
		if (toStorage) return null; // into the stored systems, to fit at Refit (5.30: the hold keeps systems now)
		return bay.systems.reason(sysId);
	}
	/** The boarded ship's systems changed (Refit): the greying may have too. */
	void systemsChanged() { rebuild(); }

	private SavedGameState buyer() { return toStorage ? resolve(bay.homeSave) : bay.currentSave; }
	String helpText() {
		return "Supplies show how many the store has left and the price of one. Greyed out: hover to see why. Nothing is paid until you Save.";
	}

	/** Lays out every store: its items in three columns, then its systems and supplies. */
	void rebuild() {
		if (buyerBtn == null) panel();
		content.removeAll();
		if (bay.currentPath == null) { content.revalidate(); content.repaint(); return; }
		SavedGameState buyer = buyer();
		int scrap = buyer == null ? 0 : buyer.getPlayerShip().getScrapAmt();
		buyerBtn.setText(toStorage ? "Cargo Hold" : bay.currentSave.getPlayerShipName());
		shipPic.setIcon(toStorage ? null : bay.shipIcon(bay.currentSave));
		shipPic.setToolTipText(toStorage ? null : "Click for her report, and to rename her");
		classLbl.setText(toStorage ? "Items, supplies, and systems to fit at Refit" : CargoBayUI.shipClass(bay.currentState));
		info.setToolTipText(toStorage ? "What the Cargo Hold is" : "Her report, and to rename her");
		scrapLbl.setText(scrap + " scrap to spend");
		aboard.show(buyer, toStorage);
		List<Entry> entries = buildEntries();
		int stores = 0;
		for (Entry e : entries) if (e.kind == Kind.HEADER && e.ship != null) stores++;
		storesLbl.setText(stores == 0 ? "No stores in range: dock a ship at a station" : (stores == 1 ? "1 store" : stores + " stores") + " in range  ·  anything bought waits for Save");
		int w = 1234, colW = (w - 20) / 3, y = 4;
		int i = 0;
		while (i < entries.size()) {
			Entry h = entries.get(i++);
			if (h.kind != Kind.HEADER || h.ship == null) continue;
			List<Entry> items = new ArrayList<Entry>();
			while (i < entries.size() && entries.get(i).kind != Kind.HEADER) items.add(entries.get(i++));
			CargoParts.Header head = new CargoParts.Header(h.title, false);
			head.setBounds(0, y, w - 90, 22);
			content.add(head);
			SavedGameState src = resolve(h.ship);
			CargoParts.Label sector = new CargoParts.Label(src == null ? "" : "Sector " + (src.getSectorNumber() + 1), FtlFont.BODY, CargoParts.DIM, 1);
			sector.setBounds(w - 90, y + 3, 90, 16);
			content.add(sector);
			y += 26;
			String[] cols = {"Weapons", "Drones", "Augments"};
			int[] colY = {y + 20, y + 20, y + 20};
			for (int c = 0; c < 3; c++) {
				CargoParts.Label l = new CargoParts.Label(cols[c], FtlFont.BODY, CargoParts.GOLD, -1);
				l.setBounds(c * (colW + 10), y, colW, 18);
				content.add(l);
			}
			List<Entry> sys = new ArrayList<Entry>(), sup = new ArrayList<Entry>(), hire = new ArrayList<Entry>();
			for (Entry e : items) {
				if (e.kind == Kind.SYSTEM) { sys.add(e); continue; }
				if (e.kind == Kind.CREW) { hire.add(e); continue; }
				if (e.kind != Kind.ITEM) { sup.add(e); continue; }
				int c = Items.isWeapon(e.id) ? 0 : Items.isDrone(e.id) ? 1 : 2;
				StoreRow r = new StoreRow(e, scrap);
				r.setBounds(c * (colW + 10), colY[c], colW, 30);
				content.add(r);
				colY[c] += 34;
			}
			for (int c = 0; c < 3; c++) if (colY[c] == y + 20) { emptyNote(c * (colW + 10), colY[c], "Sold out"); colY[c] += 34; }
			y = Math.max(colY[0], Math.max(colY[1], colY[2])) + 4;
			// systems, then (when the store hires) crew, then supplies: crew takes the middle column and the supplies stack in the last
			boolean hires = !hire.isEmpty();
			CargoParts.Label sl = new CargoParts.Label("Systems", FtlFont.BODY, CargoParts.GOLD, -1), ul = new CargoParts.Label("Supplies", FtlFont.BODY, CargoParts.GOLD, -1);
			sl.setBounds(0, y, colW, 18);
			ul.setBounds(hires ? 2 * (colW + 10) : colW + 10, y, colW, 18);
			content.add(sl); content.add(ul);
			int hy = y + 20;
			if (hires) {
				CargoParts.Label hl = new CargoParts.Label("Crew", FtlFont.BODY, CargoParts.GOLD, -1);
				hl.setBounds(colW + 10, y, colW, 18);
				content.add(hl);
				for (Entry e : hire) {
					StoreRow r = new StoreRow(e, scrap);
					r.setBounds(colW + 10, hy, colW, 30);
					content.add(r);
					hy += 34;
				}
			}
			int sy = y + 20;
			if (sys.isEmpty()) { emptyNote(0, sy, "None for sale"); sy += 34; }
			for (Entry e : sys) {
				StoreRow r = new StoreRow(e, scrap);
				r.setBounds(0, sy, colW, 30);
				content.add(r);
				sy += 34;
			}
			int ux = colW + 10, uw = w - colW - 10, cw = (uw - 20) / 3, uy = y + 20, ubottom = uy + 34;
			if (hires) { ux = 2 * (colW + 10); cw = colW; }
			if (sup.isEmpty()) { emptyNote(ux, uy, "Out of supplies"); }
			for (int k = 0; k < sup.size(); k++) {
				StoreRow r = new StoreRow(sup.get(k), scrap);
				if (hires) r.setBounds(ux, uy + k * 34, cw, 30);
				else r.setBounds(ux + k * (cw + 10), uy, cw, 30);
				content.add(r);
			}
			if (hires) ubottom = uy + Math.max(1, sup.size()) * 34;
			y = Math.max(Math.max(sy, hy), ubottom) + 16;
		}
		if (stores == 0) {
			CargoParts.Label none = new CargoParts.Label("None of your ships is docked at a station with a store.", FtlFont.MENU, CargoParts.DIM, 0);
			none.setBounds(0, 60, w, 30);
			content.add(none);
			y = 120;
		}
		content.setPreferredSize(new java.awt.Dimension(w, y));
		content.revalidate();
		content.repaint();
		scroll.revalidate();
	}
	private void emptyNote(int x, int y, String s) {
		CargoParts.Label l = new CargoParts.Label(s, FtlFont.BODY, CargoParts.DIM, -1);
		l.setBounds(x + 8, y + 7, 200, 16);
		content.add(l);
	}

	/** One thing for sale: icon, name, price and a Buy button. */
	private class StoreRow extends JComponent {
		final Entry e;
		final boolean can, installed;
		StoreRow(final Entry e, int scrap) {
			this.e = e;
			String why = e.kind == Kind.SYSTEM ? systemReason(e.id) : e.kind == Kind.ITEM && !toStorage ? homeplanet.parser.Dlc.refusesItem(bay.currentSave, e.id)
					: e.kind == Kind.CREW ? crewReason(e.id) : null;
			boolean order = e.kind == Kind.SYSTEM && why == null && !toStorage && bay.systems.pastLimit(e.id); // past FTL's System Limit: a custom work order too; the hold has no limit (5.79: its Buy was greyed by the picked ship's)
			int cost = e.price + (order ? homeplanet.core.Economy.workOrderScrap() : 0);
			boolean repShort = order && bay.systems.repHave() < homeplanet.core.Economy.workOrderRep();
			can = why == null && cost <= scrap && !repShort;
			installed = e.kind == Kind.SYSTEM && SystemsPanel.INSTALLED.equals(why);
			setLayout(null);
			setToolTipText(why != null ? why : order ? SystemsPanel.workOrderTip() : tipFor(e));
			FtlButton buy = new FtlButton("Buy", FtlFont.BODY, 54, 22);
			buy.setEnabled(can);
			buy.setToolTipText(why != null ? why : cost > scrap ? (order ? "Not enough scrap: with the custom work order, " + cost : "Not enough scrap")
					: repShort ? "Not enough reputation for the custom work order: it costs " + homeplanet.core.Economy.workOrderWords()
					: order ? SystemsPanel.workOrderTip() : "Buy one (Save makes it official)");
			buy.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent ae) { buy(e); } });
			add(buy);
			this.buy = buy;
		}
		private final FtlButton buy;
		@Override public void doLayout() { buy.setBounds(getWidth() - 60, 4, 54, 22); }
		@Override protected void paintComponent(java.awt.Graphics g0) {
			java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
			CargoParts.paintBox(g, 0, 0, getWidth(), getHeight(), CargoParts.BOX_LINE);
			javax.swing.Icon ic = iconFor(e);
			int tx = 8;
			if (ic != null) { ic.paintIcon(this, g, 6 + (30 - ic.getIconWidth()) / 2, 15 - ic.getIconHeight() / 2); tx = 42; }
			String name = e.kind == Kind.ITEM ? Items.title(e.id) : e.kind == Kind.SYSTEM ? systemTitle(e.id) + (installed ? "  (installed)" : "")
					: e.kind == Kind.CREW ? raceTitle(e.id) : supplyName(e.kind) + "  x" + e.count;
			int px = getWidth() - 110;
			CargoParts.text(g, FtlFont.BODY.fit(name, px - tx - 8), FtlFont.BODY, can ? CargoParts.TEXT : CargoParts.DIM, tx, 8);
			javax.swing.Icon sc = IconFactory.supplyIcon("scrap");
			if (sc != null) sc.paintIcon(this, g, px, 7);
			CargoParts.text(g, "" + e.price, FtlFont.BODY, can ? CargoParts.GOLD : CargoParts.DIM, px + 18, 8);
			g.dispose();
		}
	}

	/**
	 * What the buyer already has, beside the scrap: her fuel, missiles and drone parts, then her weapon, drone and
	 * cargo slots in use (hover for their names). For the Cargo Hold, its supplies only (it has no slots).
	 */
	private static class Aboard extends JComponent {
		private SavedGameState g;
		private boolean hold;
		void show(SavedGameState g, boolean hold) {
			this.g = g;
			this.hold = hold;
			setToolTipText(g == null || hold ? null : tip(g));
			repaint();
		}
		private static String tip(SavedGameState g) {
			ShipState s = g.getPlayerShip();
			List<String> w = new ArrayList<String>(), d = new ArrayList<String>(), c = new ArrayList<String>();
			for (net.blerf.ftl.parser.SavedGameParser.WeaponState x : s.getWeaponList()) w.add(Items.title(x.getWeaponId())); // by kind: the gear helper would mix them
			for (net.blerf.ftl.parser.SavedGameParser.DroneState x : s.getDroneList()) d.add(Items.title(x.getDroneId()));
			for (String id : SaveHelper.cargo(g)) c.add(Items.title(id)); // not the augment FTL is asking about (5.52)
			return "<html>Weapons: " + (w.isEmpty() ? "none" : String.join(", ", w)) + "<br>Drones: " + (d.isEmpty() ? "none" : String.join(", ", d))
					+ "<br>Cargo: " + (c.isEmpty() ? "empty" : String.join(", ", c)) + "</html>";
		}
		@Override protected void paintComponent(java.awt.Graphics g0) {
			if (g == null) return;
			java.awt.Graphics2D gr = (java.awt.Graphics2D) g0.create();
			ShipState s = g.getPlayerShip();
			String[] icons = {"fuel", "missiles", "drones"};
			int[] counts = {s.getFuelAmt(), s.getMissilesAmt(), s.getDronePartsAmt()};
			int x = 0;
			for (int i = 0; i < 3; i++) {
				javax.swing.Icon ic = IconFactory.supplyIcon(icons[i]);
				if (ic != null) { ic.paintIcon(this, gr, x, (getHeight() - ic.getIconHeight()) / 2); x += ic.getIconWidth() + 4; }
				String n = "" + counts[i];
				CargoParts.text(gr, n, FtlFont.BODY, CargoParts.TEXT, x, 3);
				x += CargoParts.width(n, FtlFont.BODY) + 18;
			}
			if (!hold) {
				int ds = CargoBayUI.droneSlots(s);
				String slots = "Weapons " + s.getWeaponList().size() + "/" + CargoBayUI.weaponSlots(s)
						+ "  \u00b7  Drones " + (ds == 0 ? "none" : s.getDroneList().size() + "/" + ds)
						+ "  \u00b7  Cargo " + SaveHelper.cargo(g).size() + "/" + SaveHelper.CARGO_SLOTS;
				CargoParts.text(gr, FtlFont.BODY.fit(slots, getWidth() - x - 12), FtlFont.BODY, CargoParts.TEXT, x + 12, 3);
			}
			gr.dispose();
		}
	}

	/** Why the buyer can't take on this crew member, or null: a ship carries 8, and a ship made without Advanced Edition can't take its races. The Cargo Hold takes anyone. */
	private String crewReason(String race) {
		if (toStorage) return null;
		SavedGameState b = bay.currentSave;
		if (b == null) return null;
		if (SaveHelper.getOwnCrew(b.getPlayerShip()).size() >= SaveHelper.CREW_MAX) return "No room for more crew: a ship carries " + SaveHelper.CREW_MAX + " at most. Buy for the Cargo Hold instead.";
		net.blerf.ftl.parser.SavedGameParser.CrewState probe = new net.blerf.ftl.parser.SavedGameParser.CrewState();
		probe.setRace(SavedGameParser.CrewType.findById(race));
		probe.setName("the crew member");
		return homeplanet.parser.Dlc.refusesCrew(b, probe);
	}


	// ---- Building the list ----

	private List<Entry> buildEntries() {
		List<Entry> list = new ArrayList<Entry>();
		for (Ship ss : bay.tradeableShips()) {
			SavedGameState gs = resolve(ss);
			if (gs == null || !SaveHelper.isAtStation(gs)) continue;
			StoreState store = gs.getBeaconList().get(gs.getCurrentBeaconId()).getStore();
			String ship = gs.getPlayerShipName();
			List<Entry> items = new ArrayList<Entry>();
			List<StoreShelf> shelves = store.getShelfList();
			for (int s = 0; s < shelves.size(); s++) {
				StoreShelf shelf = shelves.get(s);
				StoreItemType t = shelf.getItemType();
				boolean sys = t == StoreItemType.SYSTEM, crew = t == StoreItemType.CREW;
				for (int i = 0; i < shelf.getItems().size(); i++) {
					StoreItem it = shelf.getItems().get(i);
					if (!it.isAvailable()) continue; // already bought
					int price = sys ? systemPrice(it.getItemId()) : crew ? crewPrice(it.getItemId()) : priceOf(it.getItemId());
					if (price < 0) continue; // not in the game data (removed mod?)
					String title = sys ? systemTitle(it.getItemId()) : crew ? raceTitle(it.getItemId()) : Items.title(it.getItemId());
					Entry e = new Entry(sys ? Kind.SYSTEM : crew ? Kind.CREW : Kind.ITEM, title + " · " + price);
					e.id = it.getItemId();
					e.shelf = s;
					e.slot = i;
					e.price = price;
					items.add(e);
				}
			}
			addSupply(items, Kind.FUEL, "Fuel", store.getFuel(), FUEL_PRICE);
			addSupply(items, Kind.MISSILES, "Missiles", store.getMissiles(), MISSILE_PRICE);
			addSupply(items, Kind.PARTS, "Drone parts", store.getDroneParts(), DRONE_PART_PRICE);
			if (items.isEmpty()) continue;
			Entry h = new Entry(Kind.HEADER, "Store at " + ship + "'s beacon");
			h.shipName = ship;
			h.ship = ss;
			list.add(h);
			for (Entry e : items) { e.shipName = ship; e.ship = ss; list.add(e); }
		}
		if (list.isEmpty()) list.add(new Entry(Kind.HEADER, "No stores in range"));
		return list;
	}

	private static void addSupply(List<Entry> items, Kind kind, String name, int count, int price) {
		if (count <= 0) return;
		Entry e = new Entry(kind, supplyTitle(name, count, price));
		e.count = count;
		e.price = price;
		items.add(e);
	}
	private static String supplyTitle(String name, int count, int price) {
		return name + " ×" + count + " · " + price + " each";
	}

	/** The ship's save as the Cargo Bay has it in memory right now (boarded ship, trade partner, or one read for the shop). */
	SavedGameState resolve(Ship ship) {
		if (ship == null) return null;
		if (ship == bay.currentShip) return bay.currentSave;
		if (ship == bay.tradeShip) return bay.tradeSave;
		SavedGameState gs = otherSaves.get(ship);
		if (gs == null) {
			try {
				homeplanet.vault.Vault.Copy c = homeplanet.vault.Vault.get().readCopy(ship); // its own copy: purchases stay unsaved until Save
				gs = c.save;
				otherSaves.put(ship, gs);
				otherHashes.put(ship, c.hash);
			} catch (Exception e) {
				log.warn("Shop: could not read " + ship.file(), e);
			}
		}
		return gs;
	}


	// ---- Buying ----

	void buy(Entry e) {
		if (e == null) return;
		SavedGameState buyer = toStorage ? resolve(bay.homeSave) : bay.currentSave;
		SavedGameState source = resolve(e.ship);
		if (buyer == null || source == null) {
			homeplanet.core.HomePlanet.showErrorDialog("The Home Planet Station could not read the saves needed for this purchase.");
			return;
		}
		ShipState bs = buyer.getPlayerShip();
		String buyerName = toStorage ? "The Cargo Bay" : buyer.getPlayerShipName();
		String name = e.kind == Kind.ITEM ? Items.title(e.id) : e.kind == Kind.SYSTEM ? systemTitle(e.id) : e.kind == Kind.CREW ? raceTitle(e.id) + " crew member" : supplyName(e.kind);
		if (e.kind == Kind.SYSTEM && !toStorage && systemBlocked(buyer, e.id, name)) return; // before the scrap check: "already has" says more than "not enough scrap"
		String refused = e.kind == Kind.ITEM && !toStorage ? homeplanet.parser.Dlc.refusesItem(buyer, e.id) : null;
		if (refused != null) { JOptionPane.showMessageDialog(bay, refused, "Advanced Edition only", JOptionPane.INFORMATION_MESSAGE); return; }
		String noCrew = e.kind == Kind.CREW ? crewReason(e.id) : null;
		if (noCrew != null) { JOptionPane.showMessageDialog(bay, noCrew, "Shop", JOptionPane.INFORMATION_MESSAGE); return; }
		// past FTL's System Limit a system takes a custom work order to fit, paid with it
		boolean order = e.kind == Kind.SYSTEM && !toStorage && SaveHelper.pastSystemLimit(bs, SystemType.findById(e.id)); // the hold has no System Limit
		int fee = order ? homeplanet.core.Economy.workOrderScrap() : 0, cost = e.price + fee, rep = order ? homeplanet.core.Economy.workOrderRep() : 0;
		if (bs.getScrapAmt() < cost) {
			JOptionPane.showMessageDialog(bay, name + " costs " + e.price + " scrap" + (order ? ", and the custom work order to fit it " + fee + " more" : "") + ".\n"
					+ buyerName + " has " + bs.getScrapAmt() + ".", "Not enough scrap", JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (rep > bay.systems.repHave()) {
			JOptionPane.showMessageDialog(bay, "The custom work order to fit " + name + " costs " + rep + " reputation as well as its scrap.\nYour reputation is "
					+ homeplanet.vault.Reputation.signed(bay.systems.repHave()) + ".", "Not enough reputation", JOptionPane.WARNING_MESSAGE);
			return;
		}
		StoreState store = source.getBeaconList().get(source.getCurrentBeaconId()).getStore();
		boolean toCargo = false;
		String hired = null;

		if (e.kind == Kind.CREW) {
			StoreItem it = store.getShelfList().get(e.shelf).getItems().get(e.slot);
			if (!it.isAvailable() || !e.id.equals(it.getItemId())) {
				homeplanet.core.HomePlanet.showErrorDialog(name + " is no longer in that store.");
				return;
			}
			net.blerf.ftl.parser.SavedGameParser.CrewState c = homeplanet.parser.Commission.volunteer(e.id, new java.util.Random());
			if (c == null) { homeplanet.core.HomePlanet.showErrorDialog("The Home Planet Station doesn't know the race " + e.id + "."); return; }
			if (!SaveHelper.placeCrew(bs, c, toStorage)) { homeplanet.core.HomePlanet.showErrorDialog(buyerName + " has no free floor space for more crew."); return; }
			bs.getCrewList().add(c);
			it.setAvailable(false);
			if (!toStorage) buyer.setTotalCrewHired(buyer.getTotalCrewHired() + 1);
			hired = c.getName();
		} else if (e.kind == Kind.SYSTEM) {
			StoreItem it = store.getShelfList().get(e.shelf).getItems().get(e.slot);
			if (!it.isAvailable() || !e.id.equals(it.getItemId())) {
				homeplanet.core.HomePlanet.showErrorDialog(name + " is no longer in that store.");
				return;
			}
			if (order && !SystemsPanel.confirmWorkOrder(bay)) return;
			if (toStorage) bay.systems.storeBought(e.id); // into the stored systems, to fit at Refit (5.30)
			else if (!installSystem(buyer, e.id, name)) return; // refused or cancelled
			it.setAvailable(false);
		} else if (e.kind == Kind.ITEM) {
			StoreItem it = store.getShelfList().get(e.shelf).getItems().get(e.slot);
			if (!it.isAvailable() || !e.id.equals(it.getItemId())) { // shouldn't happen, but never sell something twice
				homeplanet.core.HomePlanet.showErrorDialog(name + " is no longer in that store.");
				return;
			}
			if (!toStorage) {
				Boolean c = roomCheck(bs, buyer, e.id);
				if (c == null) return; // refused or cancelled
				toCargo = c;
			}
			it.setAvailable(false);
			if (toCargo) {
				SaveHelper.addCargo(buyer, e.id);
			} else if (Items.isWeapon(e.id)) {
				bs.getWeaponList().add(SaveHelper.newIdleWeapon(e.id));
			} else if (Items.isDrone(e.id)) {
				bs.getDroneList().add(SaveHelper.newIdleDrone(e.id));
			} else {
				bs.getAugmentIdList().add(e.id);
			}
		} else {
			int left;
			if (e.kind == Kind.FUEL) { left = store.getFuel() - 1; if (left < 0) return; store.setFuel(left); bs.setFuelAmt(bs.getFuelAmt() + 1); }
			else if (e.kind == Kind.MISSILES) { left = store.getMissiles() - 1; if (left < 0) return; store.setMissiles(left); bs.setMissilesAmt(bs.getMissilesAmt() + 1); }
			else { left = store.getDroneParts() - 1; if (left < 0) return; store.setDroneParts(left); bs.setDronePartsAmt(bs.getDronePartsAmt() + 1); }
			e.count = left;
		}
		bs.setScrapAmt(bs.getScrapAmt() - cost);
		bay.systems.chargeRep(rep, "a custom work order for the " + name); // spent on Save, with the Dry Dock's bill

		// Anything not already written by the Cargo Bay's Save gets written by us
		markDirty(buyer);
		markDirty(source);
		note(buyer, "Scrap", -cost);
		if (e.kind == Kind.ITEM) note(buyer, Items.title(e.id) + (toCargo ? " (cargo)" : ""), 1);
		else if (e.kind == Kind.CREW) note(buyer, "Crew " + hired, 1);
		else if (e.kind != Kind.SYSTEM) note(buyer, supplyName(e.kind), 1); // systems aren't in the TRADE inventory
		if (hired != null) name = hired + " (" + raceTitle(e.id) + ")";
		if (e.kind == Kind.SYSTEM && !name.toLowerCase().endsWith("system")) name = name + " system"; // "a Cloaking system", in the receipt and the Captain's Log
		purchases.add(name + " (" + e.price + " scrap) from the store at " + e.shipName + "'s beacon -> " + buyerName + (toCargo ? " (cargo)" : "")
				+ (order ? ", fitted past FTL's System Limit by a custom work order (" + fee + " scrap" + (rep > 0 ? " and " + rep + " reputation" : "") + ")" : ""));
		log.debug("Bought {} for {} (work order {}) from {} -> {}", name, e.price, fee, e.ship.name, buyerName);

		bay.showPurchase(buyer, e.kind == Kind.ITEM ? e.id : null, toCargo);
		bay.systems.refresh(); // a bought system shows on the Refit tab
		rebuild();
		bay.help((hired != null ? "Hired " : "Bought ") + name + " for " + e.price + " scrap" + (order ? ", and " + fee + (rep > 0 ? " scrap and " + rep + " reputation" : "") + " for the custom work order to fit it" : "")
				+ (toCargo ? " (into the cargo hold)" : hired != null && toStorage ? " (waiting in the Cargo Hold)" : e.kind == Kind.SYSTEM && toStorage ? " (stored in the Cargo Hold, to fit at Refit)" : "") + ". Save to make it official.");
	}

	/**
	 * Installs a system on the boarded ship, the way a store does: at the system's starting level, unpowered.
	 * Medbay and Clone Bay share a room: buying one replaces the other and keeps its level.
	 * Returns false if the ship can't take it (after telling the player why) or the player cancels.
	 */
	private boolean installSystem(SavedGameState buyer, String sysId, String name) {
		ShipState bs = buyer.getPlayerShip();
		if (systemBlocked(buyer, sysId, name)) return false;
		SystemType type = SystemType.findById(sysId);
		SavedGameParser.SystemState st = bs.getSystem(type);
		net.blerf.ftl.xml.SystemBlueprint sbp = DataManager.get().getSystem(sysId);
		int level = sbp != null ? Math.max(1, sbp.getStartPower()) : 1;
		// Medbay <-> Clone Bay share a room
		SystemType other = type == SystemType.CLONEBAY ? SystemType.MEDBAY : type == SystemType.MEDBAY ? SystemType.CLONEBAY : null;
		SavedGameParser.SystemState os = other == null ? null : bs.getSystem(other);
		if (os != null && os.getCapacity() > 0) {
			String otherName = systemTitle(other.getId());
			if (!homeplanet.core.HomePlanet.confirmNo(bay, "Replace the " + otherName + " with a " + name + "? It keeps the " + otherName + "'s level.",
					"Replace " + otherName + "?")) return false;
			level = os.getCapacity();
			os.setCapacity(0);
			os.setPower(0);
			os.setDamagedBars(0);
			os.setIonizedBars(0);
		}
		if (st == null) {
			st = new SavedGameParser.SystemState(type);
			bs.addSystem(st);
		}
		st.setCapacity(level);
		st.setPower(type.isSubsystem() ? level : 0); // subsystems don't use reactor power (as FTL sets up a new ship)
		st.setDamagedBars(0);
		st.setIonizedBars(0);
		SaveHelper.ensureAdvancedInfo(bs, buyer.getFileFormat()); // Clone Bay, Battery, Cloaking, Hacking and Mind Control keep extra data in the save
		homeplanet.parser.Retrofit.syncStations(bs); // a manned system needs its station in the save
		return true;
	}

	/** True (after telling the player) if this ship has no room for the system or already has it. */
	private boolean systemBlocked(SavedGameState buyer, String sysId, String name) {
		ShipState bs = buyer.getPlayerShip();
		String ship = buyer.getPlayerShipName();
		SystemType type = SystemType.findById(sysId);
		ShipBlueprint bp = DataManager.get().getShip(bs.getShipBlueprintId());
		if (type == null || bp == null || bp.getSystemList() == null || bp.getSystemList().getSystemRoom(type) == null) {
			JOptionPane.showMessageDialog(bay, SystemsPanel.NO_ROOM + ".", "No room", JOptionPane.WARNING_MESSAGE);
			return true;
		}
		SavedGameParser.SystemState st = bs.getSystem(type);
		if (st != null && st.getCapacity() > 0) {
			JOptionPane.showMessageDialog(bay, SystemsPanel.INSTALLED + ".", "Already installed", JOptionPane.INFORMATION_MESSAGE);
			return true;
		}
		return false;
	}

	/** Room on the boarded ship: TRUE = send to cargo, FALSE = fits, null = stop. */
	private Boolean roomCheck(ShipState bs, SavedGameState buyer, String id) {
		ShipBlueprint bp = DataManager.get().getShip(bs.getShipBlueprintId());
		String what;
		int slots, used;
		String question;
		if (Items.isAugment(id)) {
			if (bs.getAugmentIdList().size() >= 3) {
				JOptionPane.showMessageDialog(bay, "This ship's augment slots are full (3).", "No room", JOptionPane.WARNING_MESSAGE);
				return null;
			}
			return Boolean.FALSE;
		} else if (Items.isWeapon(id)) {
			what = "weapon";
			slots = (bp != null && bp.getWeaponSlots() != null) ? bp.getWeaponSlots() : 4;
			used = bs.getWeaponList().size();
			question = "No free weapon slot. Put the weapon in the cargo hold?";
		} else {
			what = "drone";
			slots = (bp != null && bp.getDroneSlots() != null) ? bp.getDroneSlots() : 3;
			if (!SaveHelper.hasSystem(bs, SystemType.DRONE_CTRL)) slots = 0;
			used = bs.getDroneList().size();
			question = slots == 0 ? "This ship has no Drone Control system. Put the drone in the cargo hold?"
					: "No free drone slot. Put the drone in the cargo hold?";
		}
		if (used < slots) return Boolean.FALSE;
		if (SaveHelper.cargo(buyer).size() >= 4) {
			JOptionPane.showMessageDialog(bay, "No room for the " + what + ", and the cargo hold is full too.", "No room", JOptionPane.WARNING_MESSAGE);
			return null;
		}
		int r = JOptionPane.showConfirmDialog(bay, question, "Send to cargo?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		return r == JOptionPane.YES_OPTION ? Boolean.TRUE : null;
	}

	private void markDirty(SavedGameState gs) {
		for (Map.Entry<Ship, SavedGameState> m : otherSaves.entrySet()) {
			if (m.getValue() == gs) dirty.add(m.getKey());
		}
	}

	@SuppressWarnings("unchecked")
	// ---- Save ----

	/** Adds the saves only the shop touched (storage bought for, other ships' stores) to the Cargo Bay's save. */
	void addTo(homeplanet.vault.Vault.Transaction tx) {
		for (Ship s : dirty) tx.put(s, otherSaves.get(s), otherHashes.get(s));
	}
	/** The ships whose saves only the shop changed (the Cargo Hold bought for, a store bought from): what {@link #addTo} writes. */
	java.util.Set<Ship> touched() { return new java.util.LinkedHashSet<Ship>(dirty); }
	/** The shop's own copy of a ship's save (the Cargo Hold read for purchases), or null if it read none: another change to her belongs in it (5.61). */
	SavedGameState copyOf(Ship s) { return otherSaves.get(s); }
	/** Puts the shop's copy of her into the save after a change made to it outside the shop (the Dry Dock's bill). */
	void putCopy(homeplanet.vault.Vault.Transaction tx, Ship s) {
		dirty.add(s);
		tx.put(s, otherSaves.get(s), otherHashes.get(s));
	}
	List<String> purchases() { return purchases; }

	private void note(SavedGameState gs, String key, int n) {
		Map<String, Integer> m = bought.get(gs);
		if (m == null) bought.put(gs, m = new LinkedHashMap<String, Integer>());
		m.put(key, (m.containsKey(key) ? m.get(key) : 0) + n);
	}
	/** Adds this save's purchases to its "before" inventory, so the TRADE log entry only lists actual trades. */
	void countPurchasesAsBefore(Map<String, Integer> before, SavedGameState gs) {
		Map<String, Integer> m = bought.get(gs);
		if (before == null || m == null) return;
		for (Map.Entry<String, Integer> e : m.entrySet()) {
			int v = (before.containsKey(e.getKey()) ? before.get(e.getKey()) : 0) + e.getValue();
			if (v == 0) before.remove(e.getKey()); else before.put(e.getKey(), v);
		}
	}

	// ---- Names, prices, icons ----

	static int priceOf(String id) { return homeplanet.parser.Pricing.store(id); } // FTL's own store prices, -1 when the data doesn't know the item (Pricing is the one home)
	static int crewPrice(String race) { return homeplanet.parser.Pricing.crewStore(race); }
	static String raceTitle(String race) { return homeplanet.model.Crew.raceTitle(race); }
	static int systemPrice(String id) { return homeplanet.parser.Pricing.systemStore(id); }
	static String systemTitle(String id) { return Items.systemTitle(id); }
	private static String supplyName(Kind k) {
		return k == Kind.FUEL ? "Fuel" : k == Kind.MISSILES ? "Missiles" : "Drone parts";
	}
	private static javax.swing.Icon iconFor(Entry e) {
		switch (e.kind) {
			case ITEM: return IconFactory.itemIcon(e.id);
			case SYSTEM: return null;
			case CREW: {
				net.blerf.ftl.parser.SavedGameParser.CrewState c = new net.blerf.ftl.parser.SavedGameParser.CrewState();
				c.setRace(SavedGameParser.CrewType.findById(e.id));
				c.setMale(true);
				return IconFactory.crewIcon(c);
			}
			case FUEL: return IconFactory.supplyIcon("fuel");
			case MISSILES: return IconFactory.supplyIcon("missiles");
			case PARTS: return IconFactory.supplyIcon("drones");
			default: return null;
		}
	}
	private static String tipFor(Entry e) {
		String from = "This store is at the beacon " + e.shipName + " is visiting.";
		if (e.kind == Kind.SYSTEM) {
			net.blerf.ftl.xml.SystemBlueprint s = DataManager.get().getSystem(e.id);
			String desc = (s == null || s.getDescription() == null) ? "" : s.getDescription().getTextValue();
			return "<html><b>" + homeplanet.parser.XmlText.text(systemTitle(e.id)) + "</b><div style='width:260px; margin-top:4px'>" + homeplanet.parser.XmlText.text(desc) + "</div>"
					+ "<div style='margin-top:4px'>Price: " + e.price + " scrap</div><div style='margin-top:4px'><i>" + homeplanet.parser.XmlText.text(from) + "</i></div></html>";
		}
		if (e.kind == Kind.CREW) {
			net.blerf.ftl.xml.CrewBlueprint b = DataManager.get().getCrews().get(e.id);
			String desc = b == null || b.getDescription() == null ? "" : b.getDescription().getTextValue();
			return "<html><b>" + homeplanet.parser.XmlText.text(raceTitle(e.id)) + "</b> crew member<div style='width:260px; margin-top:4px'>" + homeplanet.parser.XmlText.text(desc == null ? "" : desc) + "</div>"
					+ "<div style='margin-top:4px'>Price: " + e.price + " scrap. Joins with a name of their own.</div><div style='margin-top:4px'><i>" + homeplanet.parser.XmlText.text(from) + "</i></div></html>";
		}
		if (e.kind == Kind.ITEM) {
			String t = ItemTooltips.tooltip(e.id);
			if (t != null) return t.replace("</html>", "<div style='margin-top:4px'><i>" + homeplanet.parser.XmlText.text(from) + "</i></div></html>");
			return "<html>" + homeplanet.parser.XmlText.text(Items.title(e.id)) + "<br><i>" + homeplanet.parser.XmlText.text(from) + "</i></html>";
		}
		return "<html>" + homeplanet.parser.XmlText.text(supplyName(e.kind)) + ": " + e.count + " in stock, " + e.price + " scrap each<br><i>" + homeplanet.parser.XmlText.text(from) + "</i></html>";
	}
}
