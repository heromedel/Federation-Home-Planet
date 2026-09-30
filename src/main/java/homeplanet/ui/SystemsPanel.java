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
		Stored(String id, int level) { this.id = id; this.level = level; }
		// level 0 = no level of its own (a Clone Bay takes the Medbay's level)
		public String toString() { return DryDockShop.systemTitle(id) + (level > 0 ? " (level " + level + ")" : ""); }
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
	private final List<String> changes = new ArrayList<String>(); // for the history log
	private boolean storedSomething = false;

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
		remodelBtn.setToolTipText("Move her systems and doors, or rework her rooms (a retrofitted ship only)");
		remodelBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (RemodelDialog.open(bay)) { load(); bay.markDirty(); refresh(); bay.refreshTrade(); }
			}
		});
		retrofitBtn = new FtlButton("Retrofit", FtlFont.MENU, 220, 34);
		retrofitBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { retrofit(); } });
		return panel;
	}

	/** Rebuilds from the boarded ship and the file (throws away unsaved changes). */
	void init() {
		changes.clear();
		storedSomething = false;
		load();
		refresh();
	}
	String helpText() {
		return "Store takes a system off the ship at its level, to install on a ship later. Greyed out: hover to see why.";
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
			String why = storeReason(bs, t);
			SysRow r = new SysRow(DryDockShop.systemTitle(t.getId()), st.getCapacity(), "Store", why,
					why == null ? "Take the " + DryDockShop.systemTitle(t.getId()) + " off the ship; it keeps its level" : why,
					new ActionListener() { public void actionPerformed(ActionEvent e) { storeSystem(type); } });
			r.setBounds(0, y + i * 32, w, 28);
			sysList.add(r);
			i++;
		}
		y += i * 32 + 12;
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
			SysRow r = new SysRow(DryDockShop.systemTitle(s.id), s.level, "Install", why,
					why == null ? "Install the " + DryDockShop.systemTitle(s.id) + " on " + bay.currentSave.getPlayerShipName() : why,
					new ActionListener() { public void actionPerformed(ActionEvent e) { installSystem(s); } });
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
		remodelBtn.setBounds(0, y, 180, 34);
		lists.add(remodelBtn);
		retrofitBtn.setText(retro ? "Undo Retrofit" : "Retrofit");
		retrofitBtn.setToolTipText(retro ? "Return this ship to her original model (needs all her standard equipment installed)"
				: "Let this ship remove any system, including standard equipment (needs the " + homeplanet.parser.Retrofit.MOD_NAME + ")");
		retrofitBtn.setBounds(192, y, 220, 34);
		lists.add(retrofitBtn);
		y += 42;
		layoutHint.setText(retro ? "Remodel moves systems and doors, or reworks her rooms."
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
			b.setToolTipText(tip);
			b.addActionListener(a);
			b.setBounds(ROW_W - (action.length() > 5 ? 82 : 66), 3, action.length() > 5 ? 78 : 62, 22);
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
			if (level <= 0) CargoParts.text(g, "uses the Medbay's level", FtlFont.BODY, CargoParts.DIM, 250, 7);
			g.dispose();
		}
	}

	// ---- The file ----

	private void load() {
		stored.clear();
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
				if (SystemType.findById(p[0]) == null) { log.warn("Unknown system in {}: {}", f.getName(), line); continue; }
				int level = 1;
				try { if (p.length > 1) level = Math.max(1, Integer.parseInt(p[1])); } catch (NumberFormatException e) { }
				if (SystemType.findById(p[0]) == SystemType.CLONEBAY) level = 0; // the level stays with the Medbay
				stored.add(new Stored(p[0], level));
			}
		} catch (Exception e) {
			log.error("Could not read " + f, e);
			homeplanet.core.HomePlanet.showErrorDialog("Could not read the stored systems:\n" + f + "\n\n" + e);
		} finally {
			try { if (r != null) r.close(); } catch (Exception e) { }
		}
	}

	/** Adds the stored-systems list to the Cargo Bay's save, if it changed (written with the ships, or not at all). */
	void addTo(homeplanet.vault.Vault.Transaction tx) {
		if (changes.isEmpty()) return;
		File f = file();
		if (f == null) return;
		StringBuilder sb = new StringBuilder(HEADER).append("\n");
		for (Stored s : stored) sb.append(line(s.id, s.level)).append("\n");
		tx.put(f, sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}
	public static final String HEADER = "# Ship systems stored in the Cargo Bay: <system id> <level> (a Clone Bay has no level: it uses the Medbay's)";
	static String line(String id, int level) { return level > 0 ? id + " " + level : id; }

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
			return homeplanet.parser.Retrofit.blankAvailable(ship) ? STARTING + ". Press Retrofit (above) to allow removing it" : STARTING + ". Install the " + homeplanet.parser.Retrofit.MOD_NAME + " (Settings > Patch mods) to allow retrofitting";
		}
		return null;
	}

	// ---- Actions ----

	private void storeSystem(SystemType type) {
		Installed sel = new Installed(type, 0);
		SavedGameState save = bay.currentSave;
		ShipState bs = save.getPlayerShip();
		String why = storeReason(bs, sel.type);
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
		if (st.getDamagedBars() > 0) { // storing must not be a free repair
			JOptionPane.showMessageDialog(bay, name + " is damaged. Repair it before storing.", "Systems", JOptionPane.WARNING_MESSAGE);
			return;
		}
		int level = st.getCapacity();
		if (sel.type == SystemType.CLONEBAY) {
			// The level belongs to the room: the Medbay left behind keeps it, the Clone Bay goes without one
			SystemState mb = bs.getSystem(SystemType.MEDBAY);
			if (mb == null) { mb = new SystemState(SystemType.MEDBAY); bs.addSystem(mb); }
			mb.setCapacity(level);
			mb.setPower(st.getPower());
			mb.setDamagedBars(0);
			mb.setIonizedBars(0);
			clear(st);
			stored.add(new Stored(sel.type.getId(), 0));
			changes.add("Stored Clone Bay from " + save.getPlayerShipName() + " (a level " + level + " Medbay took its place)");
		} else {
			clear(st);
			stored.add(new Stored(sel.type.getId(), level));
			changes.add("Stored " + name + " (level " + level + ") from " + save.getPlayerShipName());
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
		st.setCapacity(level);
		st.setPower(type.isSubsystem() ? level : 0); // unpowered; subsystems don't use reactor power
		st.setDamagedBars(0);
		st.setIonizedBars(0);
		SaveHelper.ensureAdvancedInfo(bs, save.getFileFormat()); // Clone Bay, Battery, Cloaking, Hacking, Mind Control keep extra data
		homeplanet.parser.Retrofit.syncStations(bs); // a manned system needs its station in the save
		stored.remove(sel);
		changes.add("Installed " + name + " (level " + level + ") on " + save.getPlayerShipName());
		log.debug("Installed {} level {} on {}", type, level, save.getPlayerShipName());
		changed();
	}

	/**
	 * Scrapping with "scrap_keeps_systems" on: moves the wreck's storable systems into the stored-systems file
	 * (standard equipment and the Medbay stay with the hull; damaged systems are lost). Returns log lines.
	 */
	static List<String> scrapSystems(ShipState wreck, homeplanet.vault.Vault.Transaction tx) throws java.io.IOException {
		List<String> lines = new ArrayList<String>();
		List<String> add = new ArrayList<String>();
		for (SystemType t : SystemType.values()) {
			SystemState st = wreck.getSystem(t);
			if (st == null || st.getCapacity() <= 0 || storeReason(wreck, t) != null) continue;
			String name = DryDockShop.systemTitle(t.getId());
			if (st.getDamagedBars() > 0) { lines.add("- " + name + " (level " + st.getCapacity() + ") (system, damaged: lost with the hull)"); continue; }
			int level = t == SystemType.CLONEBAY ? 0 : st.getCapacity();
			add.add(line(t.getId(), level));
			lines.add("+ " + name + (level > 0 ? " (level " + level + ")" : "") + " (system)");
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
	/** What scrapping would move and lose, as lines for the confirmation. */
	static String scrapPreview(ShipState wreck) {
		List<String> moved = new ArrayList<String>(), lost = new ArrayList<String>();
		for (SystemType t : SystemType.values()) {
			SystemState st = wreck.getSystem(t);
			if (st == null || st.getCapacity() <= 0 || storeReason(wreck, t) != null) continue;
			String name = DryDockShop.systemTitle(t.getId()) + (t == SystemType.CLONEBAY ? "" : " (level " + st.getCapacity() + ")");
			(st.getDamagedBars() > 0 ? lost : moved).add(name);
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
					+ "Install it with Settings > Patch mods (it comes with Federation Home Planet), then restart the station.", "Retrofit", JOptionPane.INFORMATION_MESSAGE);
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
					+ "She will only load in FTL while the " + homeplanet.parser.Retrofit.MOD_NAME + " is installed.",
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
