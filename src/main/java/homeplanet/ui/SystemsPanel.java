package homeplanet.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
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
	/** A system past FTL's System Limit, on hover: FTL's own sentence, then the custom work order (heromedel's words). */
	static String workOrderTip() {
		return "You've reached the System Limit. Home Planet Station can fit it in as a custom work order. But it will cost " + homeplanet.core.Economy.workOrderWords() + ".";
	}

	/** Asked before a system goes past FTL's System Limit (heromedel's words, as written): true for Install. Cancel is the default, and closing it cancels. */
	static boolean confirmWorkOrder(Component owner) {
		Object[] options = {"Install", "Cancel"};
		String msg = "This ship is at maximum capacity for systems.\nHome Planet Station can fit it in as a custom work order.\nBut it will cost "
				+ homeplanet.core.Economy.workOrderWords() + ".\n\n(This would excede the Vanilla FTL system Limit)";
		return JOptionPane.showOptionDialog(owner, msg, "System Limit", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]) == 0;
	}

	/** A system in the Cargo Bay. */
	static class Stored {
		final String id;
		final int level;
		/** Its broken bars: a damaged system keeps them in storage, and brings them aboard (the Refit mends them). */
		final int broken;
		Stored(String id, int level) { this(id, level, 0); }
		Stored(String id, int level, int broken) { this.id = id; this.level = level; this.broken = Math.max(0, broken); }
		// level 0 = no level of its own (a Clone Bay takes the Medbay's level)
		public String toString() { return RefitShop.systemTitle(id) + (level > 0 ? " (level " + level + ")" : "") + (broken > 0 ? " (" + broken + " broken)" : ""); }
	}
	/** A system installed on the boarded ship. */
	static class Installed {
		final SystemType type;
		final int level;
		Installed(SystemType type, int level) { this.type = type; this.level = level; }
		public String toString() { return RefitShop.systemTitle(type.getId()) + " (level " + level + ")"; }
	}

	private final CargoBayUI bay;
	private final List<Stored> stored = new ArrayList<Stored>();
	private final List<String> unknownLines = new ArrayList<String>(); // lines naming a system this version doesn't know: written back as they were
	private final List<String> changes = new ArrayList<String>(); // for the history log
	private boolean storedSomething = false;
	/** What the Refit's work since the last save costs the Cargo Hold (less what selling stored systems paid in): paid on Save, dropped on Reset. */
	private int bill = 0;
	/** The reputation the Refit's work since the last save costs (a removal paid in reputation, custom work orders), spent on Save, with why. */
	private int repBill = 0;
	private final List<String> repWhy = new ArrayList<String>();

	SystemsPanel(CargoBayUI bay) { this.bay = bay; }


	/** The stored-systems list that goes with the storage hold (systems.txt in the Cargo Hold's folder). */
	File file() {
		return bay.homeSave == null ? null : homeplanet.vault.Vault.get().systemsFile();
	}

	/** Rebuilds the panel from the boarded ship and the file (throws away unsaved changes). */

	// ---- The Refit tab ----
	private final JPanel panel = new JPanel(null);
	/** Her floor plan over her picture: rooms, doors, the icons of what she has; the row under the pointer lights its room. */
	private final ShipPlanView pic = new ShipPlanView();
	private FtlButton info;
	private final CargoParts.Label name = new CargoParts.Label("", FtlFont.MENU, CargoParts.GOLD, 0).small(FtlFont.BODY);
	private final CargoParts.Label sub = new CargoParts.Label("", FtlFont.CARGO, CargoParts.DIM, 0);
	/** What the Cargo Hold can still spend at the Refit, always in view under her name. */
	/** Who pays the Refit (6.44, heromedel): the ship or the Cargo Bay's hold, remembered; and what that side can spend. The Shop's is the same control (6.45). */
	private final PayFrom payer = new PayFrom();
	private final CargoParts.ScrapCount scrapCount = new CargoParts.ScrapCount();
	/** The cfg line for who pays at Refit: "ship", or the Cargo Hold (the default, as before 6.44). */
	static final String CFG_PAYS = "refit_pays";
	private final JPanel lists = new JPanel(null);
	/** The installed and stored systems scroll inside the top of the column; Layout and model stays pinned below. */
	private final JPanel sysList = new JPanel(null);
	private javax.swing.JScrollPane sysScroll;
	/** The uninstalled systems, a list of their own under the installed ones (6.44, heromedel: both always in view). */
	private final JPanel storedList = new JPanel(null);
	private javax.swing.JScrollPane storedScroll;
	/** The installed list's share of the column: its heading and rows; the uninstalled systems take the rest. */
	private static final int INST_H = 250; // seven rows exactly
	/** Row width: the column less room for the scroll bar. */
	private static final int ROW_W = 550;
	/** Retrofit and Remodel: wide enough for "Undo Retrofit" (6.45, heromedel). */
	private static final int BTN_W = Math.max(104, CargoParts.width("Undo Retrofit", FtlFont.CARGO) + 24);
	/** The power distribution simulation under the ship and the lists: its heading and FTL's panel (6.42). */
	private static final int POWER_H = 184;
	private final PowerPanel power = new PowerPanel();
	private FtlButton remodelBtn, retrofitBtn;
	private final CargoParts.Label layoutLbl = new CargoParts.Label("", FtlFont.SLOT, CargoParts.DIM, -1); // a size under the class (6.44)

	JPanel panel() {
		if (remodelBtn != null) return panel;
		panel.setOpaque(false);
		pic.setBounds(16, 8, 640, 344); // two lines under it now, not three: the picture gets the room (6.44, heromedel)
		pic.setToolTipText(homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " rooms and what's in them; a faint icon is a room kept for a system " + homeplanet.model.Words.she() + " doesn't have");
		panel.add(pic);
		name.setBounds(16, 354, 640, 26);
		name.setToolTipText("Click for " + homeplanet.model.Words.her() + " report, and to rename " + homeplanet.model.Words.herObj());
		name.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		name.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mouseClicked(java.awt.event.MouseEvent e) { bay.showCurrentShipInfo(); } });
		panel.add(name);
		sub.setBounds(16, 384, 640, 16);
		panel.add(sub);
		payer.setCargo(!shipPays());
		java.awt.Dimension ps = payer.getPreferredSize();
		payer.setBounds(640 - ps.width / 2, 0, ps.width, ps.height); // under the Shop tab's button, outside every bar (6.45, heromedel); "PAY FROM" over the switch
		payer.setToolTipText("Who pays for the refit work: the ship's own scrap, or the Cargo Hold's (Cargo; paid on Save)");
		payer.onChange(new Runnable() { public void run() { switchPayer(); } });
		panel.add(payer);
		scrapCount.setBounds(0, 8, 120, 22); // the scrap that side has, just left of Pay From (placed in refresh, by its width)
		panel.add(scrapCount);
		panel.setComponentZOrder(payer, 0);
		panel.setComponentZOrder(scrapCount, 0);
		info = new CargoParts.IconButton(CargoParts.infoIcon(), homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " report, and to rename " + homeplanet.model.Words.herObj(), new ActionListener() { public void actionPerformed(ActionEvent e) { bay.showCurrentShipInfo(); } });
		panel.add(info);
		lists.setOpaque(false);
		lists.setBounds(700, 8, 564, CargoBayUI.H - 52 - 26 - 12 - POWER_H);
		panel.add(lists);
		// the power distribution simulation along the bottom, FTL's own panel at its own size (6.42, heromedel)
		CargoParts.Header ph = new CargoParts.Header("Power Distribution Simulation", false);
		ph.setBounds(16, CargoBayUI.H - 52 - 26 - 4 - POWER_H, 1248, 22);
		panel.add(ph);
		power.setBounds(16, CargoBayUI.H - 52 - 26 - 4 - POWER_H + 24, 1248, POWER_H - 24);
		panel.add(power);
		power.onReorder(new PowerPanel.Reorder() { public void moved(boolean drones, int from, int to) { rearranged(drones, from, to); } });
		FtlButton powerReset = new FtlButton("Reset", FtlFont.CARGO, 70, 22);
		powerReset.setToolTipText("Put the power back as " + homeplanet.model.Words.her() + " save has it (the simulation saves nothing)");
		powerReset.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { power.reset(); } });
		powerReset.setBounds(16 + 1248 - 74, CargoBayUI.H - 52 - 26 - 4 - POWER_H, 70, 22);
		panel.add(powerReset);
		panel.setComponentZOrder(powerReset, 0);
		sysList.setOpaque(false);
		sysScroll = new javax.swing.JScrollPane(sysList, javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sysScroll.setOpaque(false);
		sysScroll.getViewport().setOpaque(false);
		sysScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		sysScroll.getVerticalScrollBar().setUI(new MenuTheme.DarkScrollBarUI(new Color(214, 230, 222, 170), new Color(40, 50, 58, 200)));
		sysScroll.getVerticalScrollBar().setPreferredSize(new java.awt.Dimension(10, 10));
		sysScroll.getVerticalScrollBar().setOpaque(false);
		sysScroll.getVerticalScrollBar().setUnitIncrement(32);
		storedList.setOpaque(false);
		storedScroll = new javax.swing.JScrollPane(storedList, javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		storedScroll.setOpaque(false);
		storedScroll.getViewport().setOpaque(false);
		storedScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		storedScroll.getVerticalScrollBar().setUI(new MenuTheme.DarkScrollBarUI(new Color(214, 230, 222, 170), new Color(40, 50, 58, 200)));
		storedScroll.getVerticalScrollBar().setPreferredSize(new java.awt.Dimension(10, 10));
		storedScroll.getVerticalScrollBar().setOpaque(false);
		storedScroll.getVerticalScrollBar().setUnitIncrement(32);
		remodelBtn = new FtlButton("Remodel...", FtlFont.CARGO, BTN_W, 24);
		remodelBtn.setToolTipText("Move " + homeplanet.model.Words.her() + " systems and doors, or overhaul " + homeplanet.model.Words.her() + " deck plan (a retrofitted ship only)");
		remodelBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String why = homeplanet.parser.Clearance.remodelReason(); // Immersive Mode: a Commander's work (5.56)
				if (why != null) { JOptionPane.showMessageDialog(bay, why, "Remodel", JOptionPane.INFORMATION_MESSAGE); return; }
				if (RemodelDialog.open(bay)) { load(); refresh(); bay.refreshTrade(); } // the remodel saved the Cargo Bay: nothing left unsaved
			}
		});
		retrofitBtn = new FtlButton("Retrofit", FtlFont.CARGO, BTN_W, 24);
		retrofitBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { retrofit(); } });
		// Retrofit with Remodel under it, just left of the uninstalled systems (6.44, heromedel); the layout under the ship's class
		retrofitBtn.setBounds(700 - 8 - BTN_W, 8 + INST_H + 6, BTN_W, 24);
		remodelBtn.setBounds(700 - 8 - BTN_W, 8 + INST_H + 6 + 30, BTN_W, 24);
		panel.add(remodelBtn); panel.add(retrofitBtn); panel.add(layoutLbl); // (6.44, heromedel: no hint line under it; the buttons' tooltips say it)
		// in front of her picture, which reaches under them and took their clicks (heromedel, 6.47)
		panel.setComponentZOrder(remodelBtn, 0);
		panel.setComponentZOrder(retrofitBtn, 0);
		return panel;
	}

	/** Rebuilds from the boarded ship and the file (throws away unsaved changes). */
	void init() {
		changes.clear();
		bill = 0;
		paidFromShip = 0;
		repBill = 0;
		repWhy.clear();
		storedSomething = false;
		load();
		refresh();
	}
	/** The line under the ship's name (6.44, heromedel): the info button, her class, then the layout a size smaller, centred together. */
	private void lineTwo(String cls) {
		int tw = CargoParts.width(cls, FtlFont.CARGO), lw = layoutLbl.getText().isEmpty() ? 0 : CargoParts.width(layoutLbl.getText(), FtlFont.SLOT);
		int all = 30 + tw + (lw > 0 ? 14 + lw : 0), gx = 16 + Math.max(0, (640 - all) / 2);
		info.setBounds(gx, 381, 24, 22);
		sub.setBounds(gx + 30, 384, tw + 4, 16);
		layoutLbl.setBounds(gx + 30 + tw + 14, 386, Math.min(lw + 4, 640 - (gx - 16) - 30 - tw - 14), 14);
	}
	String helpText() {
		return "Uninstall takes a system off at its level, Up upgrades it, and the Hull row repairs " + homeplanet.model.Words.her() + " hull. Greyed out: hover to see why.";
	}

	/** Lays out the installed and stored systems and the layout section. */
	void refresh() {
		if (remodelBtn == null) panel();
		lists.removeAll();
		if (bay.currentPath == null) { lists.repaint(); return; }
		ShipState bs = bay.currentSave.getPlayerShip();
		java.util.Set<String> have = new java.util.HashSet<String>();
		for (SystemType t : SystemType.values()) { SystemState st = bs.getSystem(t); if (st != null && st.getCapacity() > 0) have.add(t.getId()); }
		pic.show(bs.getShipBlueprintId(), have);
		name.setText(bay.currentSave.getPlayerShipName().toUpperCase());
		// as wide as her name, centred where it was: only the name opens her report, not the blank line beside it (6.47)
		int nw = CargoParts.width(name.getText(), FtlFont.MENU);
		if (nw > 638) nw = Math.min(638, CargoParts.width(name.getText(), FtlFont.BODY));
		name.setBounds(16 + (640 - nw - 4) / 2, 354, nw + 4, 26);
		// her grey line centred under her name, with the info button to its left (as on the Trade tab)
		String cls = CargoBayUI.shipClass(bs);
		sub.setText(cls);
		lineTwo(cls);
		power.show(bs);
		payer.setCargo(!shipPays());
		scrapCount.set(hold(), ""); // what's spent here and on Save: in its tooltip
		scrapCount.setBounds(payer.getX() - 10 - scrapCount.textWidth(), 8, scrapCount.textWidth() + 2, 22);
		scrapCount.setToolTipText(cap(payerName()) + " has " + hold() + " scrap left to spend here" + (bill > 0 ? " (" + bill + " spent, paid on Save)" : bill < 0 ? " (" + (-bill) + " to come from sales, on Save)" : "")
				+ (repBill > 0 ? ", and " + repBill + " reputation on Save" : ""));
		int w = ROW_W, y = 0;
		sysList.removeAll();
		storedList.removeAll();
		CargoParts.Header h1 = new CargoParts.Header("Installed systems", false);
		h1.setBounds(0, 0, 564, 22); // the heading as it always was: Pay From and the scrap sit outside it
		lists.add(h1);
		y = repairRows(bs, y, w); // the hull and the reactor first, no Refit heading of their own (6.44, heromedel)
		int i = 0;
		for (SystemType t : SystemType.values()) {
			SystemState st = bs.getSystem(t);
			if (st == null || st.getCapacity() <= 0) continue;
			final SystemType type = t;
			String why = refitReason(bs, t);
			int fee = homeplanet.core.Economy.removalFee();
			SysRow r = new SysRow(RefitShop.systemTitle(t.getId()), st.getCapacity(), "Uninstall", why,
					why == null ? "Take the " + RefitShop.systemTitle(t.getId()) + " off the ship; it keeps its level" + (fee > 0 ? " (taking it off costs " + fee + " scrap)" : "") : why,
					new ActionListener() { public void actionPerformed(ActionEvent e) { storeSystem(type); } });
			int up = upgradePrice(bs, t), broken = st.getDamagedBars();
			r.broken = broken;
			r.max = maxLevel(bs, t);
			r.info(info(t.getId(), st.getCapacity(), r.max, why)); // FTL's own words and what's left to buy, as its upgrade screen shows on hover
			if (broken > 0) { // mended first: then she can be upgraded
				int scrap = hold(), fix = broken * homeplanet.parser.Pricing.SYSTEM_REPAIR;
				r.addButton("Fix: " + fix, 78, ROW_W - 108 - 82, scrap >= fix,
						scrap >= fix ? "Mend the " + RefitShop.systemTitle(t.getId()) + "'s " + broken + (broken == 1 ? " broken bar" : " broken bars") + " for " + fix + " scrap ("
								+ homeplanet.parser.Pricing.SYSTEM_REPAIR + " a bar)" : "Mending " + broken + (broken == 1 ? " bar" : " bars") + " costs " + fix + " scrap; " + payerName() + " has " + scrap,
						new ActionListener() { public void actionPerformed(ActionEvent e) { repairSystem(type); } });
			} else if (up > 0) {
				int scrap = hold();
				r.addButton("Up: " + up, 78, ROW_W - 108 - 82, scrap >= up,
						scrap >= up ? "Upgrade the " + RefitShop.systemTitle(t.getId()) + " to level " + (st.getCapacity() + 1) + " for " + up + " scrap"
						: "Upgrading to level " + (st.getCapacity() + 1) + " costs " + up + " scrap; " + payerName() + " has " + scrap,
						new ActionListener() { public void actionPerformed(ActionEvent e) { upgradeSystem(type); } });
			}
			r.lights(pic, t.getId()); // (after its buttons, so they light it too)
			r.setBounds(0, y + i * 32, w, 28);
			sysList.add(r);
			i++;
		}
		y += i * 32;
		sysList.setPreferredSize(new java.awt.Dimension(ROW_W, y));
		int H = lists.getHeight();
		sysScroll.setBounds(0, 26, 564, INST_H - 26);
		lists.add(sysScroll);
		sysList.revalidate();
		CargoParts.Header h2 = new CargoParts.Header("Uninstalled systems in the Cargo Bay", false);
		h2.setBounds(0, INST_H + 6, 564, 22);
		lists.add(h2);
		y = 0;
		if (stored.isEmpty()) {
			CargoParts.Label none = new CargoParts.Label("None yet.", FtlFont.CARGO, CargoParts.DIM, -1);
			none.setBounds(8, y + 6, w, 16);
			storedList.add(none);
			y += 32;
		}
		int j = 0;
		for (final Stored s : stored) {
			String why = installReason(s.id);
			boolean order = why == null && pastLimit(s.id);
			String damage = s.broken > 0 ? " (" + s.broken + " of its " + s.level + (s.level == 1 ? " bar" : " bars") + " broken, in red: the Refit tab mends them once it's aboard)" : "";
			SysRow r = new SysRow(RefitShop.systemTitle(s.id), s.level, "Install", why,
					(why != null ? why : order ? workOrderTip() : "Install the " + RefitShop.systemTitle(s.id) + " on " + bay.currentSave.getPlayerShipName()) + damage,
					new ActionListener() { public void actionPerformed(ActionEvent e) { installSystem(s); } });
			r.broken = s.broken;
			SystemType st2 = SystemType.findById(s.id);
			if (st2 != null) {
				r.max = maxLevel(bay.currentSave.getPlayerShip(), st2);
				String note = (why != null ? why : order ? workOrderTip() : "") + damage;
				r.info(info(s.id, s.level, r.max, note.trim().isEmpty() ? null : note.trim()));
			}
			if (homeplanet.core.HomePlanet.sellSystems()) r.addSell(salePrice(s), payerName(), new ActionListener() { public void actionPerformed(ActionEvent e) { sellSystem(s); } });
			r.lights(pic, s.id); // the room it would go into
			r.setBounds(0, y + j * 32, w, 28);
			storedList.add(r);
			j++;
		}
		y += j * 32;
		storedList.setPreferredSize(new java.awt.Dimension(ROW_W, y));
		storedScroll.setBounds(0, INST_H + 32, 564, H - INST_H - 32);
		lists.add(storedScroll);
		storedList.revalidate();
		boolean retro = homeplanet.parser.Retrofit.isRetrofitted(bs);
		boolean inGame = homeplanet.parser.Retrofit.inGame(bs);
		String id = bs.getShipBlueprintId();
		String txt = !retro ? "Original" : homeplanet.parser.CompanionMod.isRemodelId(id)
				? "Custom " + homeplanet.parser.CompanionMod.numberOf(id) : "Original";
		layoutLbl.setText("Layout: " + txt + (inGame ? "" : " (not patched in)") + (retro ? " · retrofitted" : ""));
		lineTwo(cls);
		layoutLbl.setColor(inGame ? CargoParts.TEXT : CargoParts.ORANGE);
		layoutLbl.setToolTipText(!retro ? homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " systems are where the ship model puts them"
				: "Retrofitted: any system can be stored. Blueprint " + id + (inGame ? "" : ". The game data doesn't have it yet: install the mod (Settings > Mods > Patch mods) before launching"));
		remodelBtn.setEnabled(retro);
		String remodelLock = homeplanet.parser.Clearance.remodelReason(); // Immersive Mode: a Commander's work (5.56)
		remodelBtn.setToolTipText(remodelLock == null ? (retro ? "Move " + homeplanet.model.Words.her() + " systems and doors, or overhaul " + homeplanet.model.Words.her() + " deck plan"
				: "Retrofit first to remodel " + homeplanet.model.Words.herObj() + " or to uninstall standard equipment")
				: "<html>" + homeplanet.parser.XmlText.text(remodelLock).replace("\n", "<br>") + "</html>");
		retrofitBtn.setText(retro ? "Undo Retrofit" : "Retrofit");
		retrofitBtn.setToolTipText(retro ? "Return this ship to " + homeplanet.model.Words.her() + " original model (needs all " + homeplanet.model.Words.her() + " standard equipment installed)"
				: "Let this ship remove any system, including standard equipment (needs the " + homeplanet.parser.Retrofit.MOD_NAME + ")");
		lists.revalidate();
		lists.repaint();
	}

	/** A system line: name, level bars, and its Uninstall or Install button (greyed out with the reason on hover). */
	/** Where a row's bars start (6.42: left of where they were, so FTL's eight always clear the Up button). */
	private static final int BARS_X = 296;
	private static class SysRow extends JComponent {
		final String title; final int level; final boolean ok;
		/** Broken bars, drawn red at the end of the level bar (as FTL draws damage). */
		int broken = 0;
		/** FTL's most for this system on her (6.42): the bars past her level drawn dim, as FTL's upgrade screen draws them. */
		int max = 0;
		/** Its hover as FTL's upgrade screen draws it (6.42), or null for a plain tooltip. */
		private InfoTip.Model info;
		void info(InfoTip.Model m) { info = m; setToolTipText(m.plain()); }
		@Override public javax.swing.JToolTip createToolTip() {
			if (info == null) return super.createToolTip();
			InfoTip t = new InfoTip(info);
			t.setComponent(this);
			return t;
		}
		SysRow(String title, int level, String action, String why, String tip, ActionListener a) {
			this.title = title; this.level = level; this.ok = why == null;
			setLayout(null);
			setToolTipText(tip);
			int bw = action.length() > 7 ? 104 : action.length() > 5 ? 78 : 62;
			FtlButton b = new FtlButton(action, FtlFont.CARGO, bw, 22);
			b.setEnabled(ok);
			if (action.isEmpty()) b.setVisible(false); // a row with only its own extra buttons
			b.setToolTipText(tip);
			b.addActionListener(a);
			b.setBounds(ROW_W - bw - 4, 3, bw, 22);
			add(b);
		}
		/** Under the pointer, the plan lights this system's room. */
		void lights(final ShipPlanView plan, final String systemId) {
			java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
				@Override public void mouseEntered(java.awt.event.MouseEvent e) { plan.light(systemId); }
				@Override public void mouseExited(java.awt.event.MouseEvent e) { if (!contains(javax.swing.SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), SysRow.this))) plan.light(null); }
			};
			addMouseListener(m);
			for (Component c : getComponents()) c.addMouseListener(m);
		}
		/** HR1: a Sell button beside the row's own. */
		void addSell(int price, String payTo, ActionListener a) {
			addButton("Sell", 62, ROW_W - 82 - 70, true, "Sell it to the station for " + price + " scrap (paid to " + payTo + ", on Save)", a);
		}
		/** Another button, left of the row's own. */
		void addButton(String text, int bw, int x, boolean enabled, String tip, ActionListener a) {
			FtlButton b = new FtlButton(text, FtlFont.CARGO, bw, 22);
			b.setEnabled(enabled);
			b.setToolTipText(tip);
			b.addActionListener(a);
			b.setBounds(x, 3, bw, 22);
			add(b);
		}
		@Override protected void paintComponent(java.awt.Graphics g0) {
			java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
			CargoParts.paintBox(g, 0, 0, getWidth(), getHeight(), CargoParts.BOX_LINE);
			CargoParts.text(g, FtlFont.CARGO.fit(title, 230), FtlFont.CARGO, ok ? CargoParts.TEXT : CargoParts.DIM, 10, 7);
			CargoParts.text(g, level > 0 ? "level " + level : "", FtlFont.CARGO, CargoParts.DIM, 232, 7);
			int all = Math.max(level, max), red = Math.min(broken, Math.max(0, level));
			// up to 8 bars as before; more (the reactor's 25) narrower, in the same 64 pixels
			int step = all <= 8 ? 7 : Math.max(2, 56 / Math.max(1, all)), bw = all <= 8 ? 5 : Math.max(1, step - 1);
			for (int k = 0; k < all && BARS_X + k * step + bw <= BARS_X + 56; k++) { // clear of the Up button
				if (k >= level) { // not had yet: FTL's empty bar, outlined
					g.setColor(new Color(70, 80, 84));
					g.drawRect(BARS_X + k * step, 9, bw - 1, 10);
					continue;
				}
				boolean bad = k >= level - red; // the broken ones last, red
				g.setColor(bad ? (ok ? new Color(225, 70, 55) : new Color(140, 70, 60)) : ok ? new Color(120, 230, 120) : new Color(90, 130, 95));
				g.fillRect(BARS_X + k * step, 9, bw, 11);
			}
			if (level == 0) CargoParts.text(g, "uses Medbay level", FtlFont.CARGO, CargoParts.DIM, 232, 7); // (below 0: a Refit row, no level)
			g.dispose();
		}
	}

	// ---- The file ----

	private void load() {
		stored.clear();
		unknownLines.clear();
		File f = file();
		if (f == null || !f.exists()) return;
		try {
			for (homeplanet.vault.StoredSystems.Entry e : homeplanet.vault.StoredSystems.read(f)) {
				if (SystemType.findById(e.id) == null) { log.warn("Unknown system in {}: {}", f.getName(), e.line); unknownLines.add(e.line); continue; }
				int level = SystemType.findById(e.id) == SystemType.CLONEBAY ? 0 : e.level; // a Clone Bay's level stays with the Medbay
				stored.add(new Stored(e.id, level, level > 0 ? Math.min(e.broken, level) : e.broken));
			}
		} catch (Exception e) {
			log.error("Could not read " + f, e);
			homeplanet.core.HomePlanet.showErrorDialog("Could not read the list of systems stored in the Cargo Bay:\n" + f + "\n\n" + e);
		}
	}

	/** Adds the stored-systems list to the Cargo Bay's save, if it changed (written with the ships, or not at all). */
	/** True when the ship pays the Refit (the switch on Ship), else the Cargo Hold does. */
	static boolean shipPays() { return "ship".equals(homeplanet.core.HomePlanet.config.getProperty(CFG_PAYS)); }
	/** Who pays, as a sentence names it: "the Kestrel" or "the Cargo Hold". */
	String payerName() { return shipPays() && bay.currentSave != null ? homeplanet.parser.ShipNames.the(bay.currentSave.getPlayerShipName()) : "the Cargo Hold"; }
	private static String cap(String s) { return homeplanet.model.Words.cap(s); }
	/** The switch flipped: the other side pays from now on, if it can cover what's on the bill. */
	private void switchPayer() {
		boolean toShip = !payer.cargo();
		boolean was = shipPays();
		if (toShip) homeplanet.core.HomePlanet.config.setProperty(CFG_PAYS, "ship");
		else homeplanet.core.HomePlanet.config.remove(CFG_PAYS); // the Cargo Hold is the default: the line only while the ship pays
		if (bill > 0 && hold() < 0) { // the other side can't cover the work already done here
			if (was) homeplanet.core.HomePlanet.config.setProperty(CFG_PAYS, "ship"); else homeplanet.core.HomePlanet.config.remove(CFG_PAYS);
			payer.setCargo(!was);
			JOptionPane.showMessageDialog(bay, "The refit work so far costs " + bill + " scrap, more than " + payerNameIf(toShip) + " has.\nSave or Reset first, then switch.", "Refit", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		homeplanet.core.HomePlanet.saveConfig();
		refresh();
		bay.shop.systemsChanged(); // the Shop's scrap leaves out the Refit's bill on the same side
	}
	private String payerNameIf(boolean ship) { return ship && bay.currentSave != null ? homeplanet.parser.ShipNames.the(bay.currentSave.getPlayerShipName()) : "the Cargo Hold"; }
	/** The scrap the Refit can still spend: the paying side's (the Cargo Hold as the Trade tab has it, when it's the partner) less the bill. */
	int hold() {
		if (shipPays()) return bay.currentSave == null ? 0 : bay.currentSave.getPlayerShip().getScrapAmt() - bill;
		int have;
		if (bay.partnerIsStorage() && bay.tradeState != null) have = bay.tradeState.getScrapAmt();
		else {
			SavedGameState shopHold = null; // the Shop's copy of the hold, if it read one: what the Shop spent from it shows here too (6.45)
			try { shopHold = bay.shop.copyOf(homeplanet.vault.Vault.get().storage()); } catch (Exception e) { }
			if (shopHold != null) have = shopHold.getPlayerShip().getScrapAmt();
			else { try { have = homeplanet.vault.Vault.get().storageScrap(); } catch (Exception e) { have = 0; } }
		}
		return have - bill;
	}
	/** What the Refit's bill will take from this side on Save (the Cargo Hold, or the ship): the Shop spends only what's left (6.45). */
	int billOn(boolean cargoHold) { return bill > 0 && cargoHold != shipPays() ? bill : 0; }
	/** The reputation the Refit can still spend: the career's, less what's on the bill (0 with Reputation off). */
	int repHave() {
		if (!homeplanet.vault.Reputation.shown()) return 0;
		return homeplanet.vault.Reputation.total(homeplanet.vault.Vault.get()) - repBill;
	}
	/** Puts reputation on the bill, if it stays at zero or above. */
	boolean chargeRep(int n, String why) {
		if (n <= 0) return true;
		if (n > repHave()) return false;
		repBill += n;
		repWhy.add(why);
		return true;
	}
	/** After the save: the reputation on the bill is spent (logged with what it bought). */
	void spendRepBill() {
		if (repBill <= 0) return;
		homeplanet.vault.Reputation.spend(homeplanet.vault.Vault.get(), repBill, "Refit work: " + String.join("; ", repWhy));
		repBill = 0;
		repWhy.clear();
	}
	/** Puts a price on the bill, if the paying side (ship or Cargo Hold) can pay it. */
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
		billedShop = null;
		if (bill == 0 || paidFromShip != 0) return 0; // the ship paid already, in her own save (payFromShip)
		if (bay.partnerIsStorage() && bay.tradeState != null) {
			if (bay.tradeState.getScrapAmt() < bill) throw new java.io.IOException("The Cargo Hold holds " + bay.tradeState.getScrapAmt() + " scrap; the refit work costs " + bill);
			bay.tradeState.setScrapAmt(bay.tradeState.getScrapAmt() - bill); // saved with the partner; init() reloads it after
			return bill;
		}
		homeplanet.vault.Vault v = homeplanet.vault.Vault.get();
		homeplanet.vault.Ship hold = v.storage();
		SavedGameState shopHold = bay.shop.copyOf(hold);
		if (shopHold != null) { // the shop read the hold for purchases: the bill comes from that same copy, so both are saved (5.61: a fresh copy replaced it, and the purchases vanished)
			ShipState s = shopHold.getPlayerShip();
			if (s.getScrapAmt() < bill) throw new java.io.IOException("The Cargo Hold holds " + s.getScrapAmt() + " scrap; the refit work costs " + bill);
			s.setScrapAmt(s.getScrapAmt() - bill);
			bay.shop.putCopy(tx, hold);
			billedShop = shopHold;
			return 0;
		}
		homeplanet.vault.Vault.Copy c = v.readCopy(hold);
		ShipState s = c.save.getPlayerShip();
		if (s.getScrapAmt() < bill) throw new java.io.IOException("The Cargo Hold holds " + s.getScrapAmt() + " scrap; the refit work costs " + bill);
		s.setScrapAmt(s.getScrapAmt() - bill);
		tx.put(hold, c.save, c.hash);
		return 0;
	}
	/** What the ship paid toward the bill in her own save, for the Save to put back if it fails. */
	private int paidFromShip;
	/**
	 * The bill, from the ship's own scrap when she pays (the switch on Ship): taken in memory before the Save looks at
	 * whether her save changed, so it's written with her. Nothing when the Cargo Hold pays.
	 */
	void payFromShip() throws java.io.IOException {
		paidFromShip = 0;
		if (bill == 0 || !shipPays() || bay.currentSave == null) return;
		ShipState s = bay.currentSave.getPlayerShip();
		if (s.getScrapAmt() < bill) throw new java.io.IOException(cap(payerName()) + " has " + s.getScrapAmt() + " scrap; the refit work costs " + bill);
		s.setScrapAmt(s.getScrapAmt() - bill);
		paidFromShip = bill;
	}
	/** The save failed: the ship gets back what she paid. */
	void giveBackShipBill() {
		if (paidFromShip != 0 && bay.currentSave != null) bay.currentSave.getPlayerShip().setScrapAmt(bay.currentSave.getPlayerShip().getScrapAmt() + paidFromShip);
		paidFromShip = 0;
	}
	/** The shop's copy of the hold the bill was just taken from in memory, if it was (given back if the save fails). */
	private SavedGameState billedShop;
	/** The save failed: the bill taken from the shop's copy of the hold goes back to it, so a second Save doesn't pay twice. */
	void giveBackBill() {
		if (billedShop != null) billedShop.getPlayerShip().setScrapAmt(billedShop.getPlayerShip().getScrapAmt() + bill);
		billedShop = null;
	}
	void addTo(homeplanet.vault.Vault.Transaction tx) {
		if (changes.isEmpty()) return;
		File f = file();
		if (f == null) return;
		List<String> lines = new ArrayList<String>();
		for (Stored s : stored) lines.add(homeplanet.vault.StoredSystems.line(s.id, s.level, s.broken));
		homeplanet.vault.StoredSystems.write(tx, homeplanet.vault.Vault.get(), lines, unknownLines); // lines this version can't use are kept, not dropped
	}

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
	/** True if this system would take the boarded ship past FTL's System Limit (then it's a custom work order). */
	boolean pastLimit(String sysId) {
		return bay.currentSave != null && SaveHelper.pastSystemLimit(bay.currentSave.getPlayerShip(), SystemType.findById(sysId));
	}
	/** Why Refit can't install this stored system: {@link #reason}, or a custom work order the Cargo Hold can't pay for. */
	private String installReason(String sysId) {
		String why = reason(sysId);
		if (why == null && pastLimit(sysId) && hold() < homeplanet.core.Economy.workOrderScrap())
			why = "A custom work order costs " + homeplanet.core.Economy.workOrderWords() + "; " + payerName() + " has " + hold() + " scrap";
		else if (why == null && pastLimit(sysId) && repHave() < homeplanet.core.Economy.workOrderRep())
			why = "A custom work order costs " + homeplanet.core.Economy.workOrderWords() + "; your reputation is " + homeplanet.vault.Reputation.signed(repHave());
		return why;
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
			return homeplanet.parser.Retrofit.blankAvailable(ship) ? STARTING + ". Press Retrofit (below) to allow removing it" : STARTING + ". Send the " + homeplanet.parser.Retrofit.MOD_NAME + " to FTL via Slipstream (Settings > Mods > Patch mods) to allow retrofitting";
		}
		return null;
	}

	/** Why Refit can't take this system off her: the removal rule first, then {@link #storeReason}. */
	static String refitReason(ShipState ship, SystemType type) {
		if (homeplanet.core.Economy.removalFee() == homeplanet.core.Economy.NOT_ALLOWED)
			return "Systems can't be taken off ships" + (homeplanet.core.HomePlanet.immersiveMode ? "" : " (Settings, Rules: Refit removal)");
		return storeReason(ship, type);
	}

	// ---- Actions ----

	/** Why this system can't be uninstalled from the bay's ship now (the rules, weapons or drones aboard), or null. */
	String uninstallReason(SystemType type) {
		ShipState bs = bay.currentSave.getPlayerShip();
		String why = refitReason(bs, type);
		if (why != null) return why;
		if (type == SystemType.WEAPONS && !bs.getWeaponList().isEmpty()) return "move the weapons to cargo first";
		if (type == SystemType.DRONE_CTRL && !bs.getDroneList().isEmpty()) return "move the drones to cargo first";
		return null;
	}
	/** Remodel's Uninstall (6.42): as the Uninstall button, the player already asked; true if it came off. */
	boolean uninstallForRemodel(SystemType type) { return storeSystem(type, true); }

	private void storeSystem(SystemType type) { storeSystem(type, false); }
	/** Takes a system off her into the Cargo Bay for the Refit's fee; {@code asked}: the player has already said yes. True if it came off. */
	private boolean storeSystem(SystemType type, boolean asked) {
		Installed sel = new Installed(type, 0);
		SavedGameState save = bay.currentSave;
		ShipState bs = save.getPlayerShip();
		String why = uninstallReason(sel.type);
		if (why != null) {
			JOptionPane.showMessageDialog(bay, Character.toUpperCase(why.charAt(0)) + why.substring(1) + ".", "Systems", JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
		SystemState st = bs.getSystem(sel.type);
		if (st == null || st.getCapacity() <= 0) return false;
		String name = RefitShop.systemTitle(sel.type.getId());
		int broken = st.getDamagedBars(); // a damaged system goes into storage damaged: storing is no free repair
		int fee = homeplanet.core.Economy.removalFee();
		if (fee > 0 && homeplanet.core.Economy.repForVanillaBreaking()) { // scrap or reputation (heromedel, 5.13)
			int[] pay = RepPay.choose(bay, "Systems", "Taking the " + name + " off " + save.getPlayerShipName() + " costs", fee, hold(), repHave(), cap(payerName()));
			if (pay == null) return false;
			if (pay[0] > 0) charge(pay[0]);
			chargeRep(pay[1], "the " + name + " taken off " + save.getPlayerShipName());
			changes.add("Paid " + RepPay.words(pay) + " to take the " + name + " off " + save.getPlayerShipName());
		} else if (fee > 0) {
			if (hold() < fee) {
				JOptionPane.showMessageDialog(bay, "Taking the " + name + " off costs " + fee + " scrap.\n" + cap(payerName()) + " has " + hold() + ".", "Systems", JOptionPane.INFORMATION_MESSAGE);
				return false;
			}
			if (!asked && !homeplanet.core.HomePlanet.confirmNo(bay, "Taking the " + name + " off " + save.getPlayerShipName() + " costs " + fee + " scrap.\n" + cap(payerName()) + " pays (on Save).\n\nUninstall it?", "Systems")) return false;
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
		return true;
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
		String name = RefitShop.systemTitle(sel.id);
		if (SaveHelper.pastSystemLimit(bs, type)) { // past FTL's System Limit: a custom work order, on the Refit's bill
			int fee = homeplanet.core.Economy.workOrderScrap(), rep = homeplanet.core.Economy.workOrderRep();
			if (hold() < fee || repHave() < rep) {
				JOptionPane.showMessageDialog(bay, "A custom work order costs " + homeplanet.core.Economy.workOrderWords() + ".\n" + cap(payerName()) + " has " + hold() + " scrap"
						+ (rep > 0 ? " and your reputation is " + homeplanet.vault.Reputation.signed(repHave()) : "") + ".", "System Limit", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			if (!confirmWorkOrder(bay)) return;
			charge(fee);
			chargeRep(rep, "a custom work order for the " + name);
			changes.add("Custom work order: the " + name + " fitted past FTL's System Limit for " + homeplanet.core.Economy.workOrderWords());
		}
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

	// ---- The Refit: upgrades and repairs, at FTL's prices, paid by the boarded ship ----

	/** Upgrading this installed system one level: FTL's upgrade cost, or -1 at its limit (FTL's, or her room's). */
	static int upgradePrice(ShipState bs, SystemType t) {
		SystemState st = bs.getSystem(t);
		if (st == null || st.getCapacity() <= 0) return -1;
		ShipBlueprint bp = DataManager.get().getShip(bs.getShipBlueprintId());
		ShipBlueprint.SystemList.SystemRoom[] rooms = bp == null || bp.getSystemList() == null ? null : bp.getSystemList().getSystemRoom(t);
		if (rooms != null && rooms.length > 0 && rooms[0].getMaxPower() != null && st.getCapacity() >= rooms[0].getMaxPower()) return -1;
		return homeplanet.parser.Pricing.upgrade(t.getId(), st.getCapacity());
	}
	/** FTL's most for this system on her: her model's own limit for its room, else the system's (6.42). */
	static int maxLevel(ShipState bs, SystemType t) {
		ShipBlueprint bp = DataManager.get().getShip(bs.getShipBlueprintId());
		ShipBlueprint.SystemList.SystemRoom[] rooms = bp == null || bp.getSystemList() == null ? null : bp.getSystemList().getSystemRoom(t);
		if (rooms != null && rooms.length > 0 && rooms[0].getMaxPower() != null) return rooms[0].getMaxPower();
		net.blerf.ftl.xml.SystemBlueprint sb = DataManager.get().getSystem(t.getId());
		return sb == null ? 0 : sb.getMaxPower();
	}
	/**
	 * A system's hover, as FTL's upgrade screen shows it (6.42, heromedel): its title and FTL's own description, then its
	 * levels from the most down, each with its price and what it gives, the ones already had in green; and why it's
	 * greyed out, if it is.
	 */
	static InfoTip.Model info(String id, int level, int max, String why) {
		net.blerf.ftl.xml.SystemBlueprint sb = DataManager.get().getSystem(id);
		String title = sb != null && sb.getTitle() != null ? sb.getTitle().getTextValue() : RefitShop.systemTitle(id);
		String desc = sb != null && sb.getDescription() != null ? sb.getDescription().getTextValue() : "";
		InfoTip.Model m = new InfoTip.Model(title, desc);
		// past the system's most, FTL lists a level no upgrade buys (the doors' and sensors' fourth), its price a dash
		String beyond = sb != null && max == sb.getMaxPower() && !"weapons".equals(id) && !"drones".equals(id) ? levelLabel(id, max + 1) : "";
		if (!beyond.isEmpty()) m.rows.add(new InfoTip.Row(max + 1, false, 0, beyond));
		for (int lv = max; lv >= 1; lv--) // level 1 has no price: it comes with the system
			m.rows.add(new InfoTip.Row(lv, lv <= Math.max(0, level), lv > 1 ? homeplanet.parser.Pricing.upgrade(id, lv - 1) : -1, levelLabel(id, lv)));
		m.note = why;
		return m;
	}
	/** FTL's text for an id (its text files), or null. */
	static String ftlText(String id) {
		return DataManager.get() instanceof net.blerf.ftl.parser.DefaultDataManager ? ((net.blerf.ftl.parser.DefaultDataManager) DataManager.get()).getTextById(id) : null;
	}
	/**
	 * What a level of a system gives, as FTL's upgrade screen says beside its price (6.42): FTL's own lines from its text
	 * files; where FTL fills in a number itself (dodge, boosts, seconds), the numbers as FTL 1.6 shows them on that screen,
	 * read off it level by level. Empty when FTL says nothing for that level.
	 */
	static String levelLabel(String id, int lv) {
		if (lv < 1) return "";
		String t;
		if ("pilot".equals(id)) t = ftlText("pilot_" + lv);
		else if ("doors".equals(id)) t = ftlText("door_" + lv);
		else if ("sensors".equals(id)) t = ftlText("sensor_" + lv);
		else if ("mind".equals(id)) t = ftlText("mind_" + lv);
		else if ("artillery".equals(id)) t = ftlText("artillery_" + lv);
		else if ("shields".equals(id)) t = lv % 2 == 0 ? ftlText("shields_" + (lv / 2 - 1)) : null; // a barrier every two levels
		else if ("weapons".equals(id) || "drones".equals(id)) t = ftlText("system_power");
		else if ("engines".equals(id)) {
			int[] dodge = {5, 10, 15, 20, 25, 28, 31, 35};
			t = lv > dodge.length ? null : fill(ftlText("engine"), dodge[lv - 1] + "", num(1 + 0.25 * (lv - 1)));
		}
		else if ("medbay".equals(id)) t = pick(lv, new String[] {"1", "1.5", "3"}, ftlText("medbay_healing"));
		else if ("oxygen".equals(id)) t = pick(lv, new String[] {"1", "3", "6"}, ftlText("oxygen_on"));
		else if ("cloaking".equals(id)) t = pick(lv, new String[] {"5", "10", "15"}, ftlText("cloak"));
		else if ("teleporter".equals(id)) t = pick(lv, new String[] {"20", "15", "10"}, ftlText("teleporter_on"));
		else if ("hacking".equals(id)) t = pick(lv, new String[] {"4", "7", "10"}, ftlText("hacking_duration"));
		else if ("battery".equals(id)) t = pick(lv, new String[] {"2", "4"}, ftlText("battery_power"));
		else if ("clonebay".equals(id)) {
			String[] sec = {"12", "9", "7"}, hp = {"8", "16", "25"};
			t = lv > sec.length ? null : fill(ftlText("clone_full"), sec[lv - 1], hp[lv - 1]);
		}
		else t = null;
		return t == null ? "" : t;
	}
	private static String pick(int lv, String[] v, String template) { return lv > v.length ? null : fill(template, v[lv - 1], null); }
	private static String fill(String template, String a, String b) {
		if (template == null) return null;
		String t = template.replace("\\1", a);
		return b == null ? t : t.replace("\\2", b);
	}
	private static String num(double d) { return d == Math.floor(d) ? Integer.toString((int) d) : (Math.round(d * 100) % 10 == 0 ? String.format("%.1f", d) : String.format("%.2f", d)); }

	/** The reactor's hover: FTL's own words for it, its bars, and the next ones' prices. */
	static InfoTip.Model reactorInfo(int bars) {
		InfoTip.Model m = new InfoTip.Model("Reactor", ftlText("reactor_desc"));
		m.line = "Power bars: " + bars + " of " + homeplanet.parser.Pricing.REACTOR_MAX;
		for (int n = Math.min(homeplanet.parser.Pricing.REACTOR_MAX, bars + 5); n > bars; n--)
			m.rows.add(new InfoTip.Row(n, false, homeplanet.parser.Pricing.reactorBar(n), "bar " + n));
		return m;
	}

	/** Her hull at full strength (her model's). */
	static int maxHull(ShipState bs) {
		ShipBlueprint bp = DataManager.get().getShip(bs.getShipBlueprintId());
		return bp == null || bp.getHealth() == null ? bs.getHullAmt() : bp.getHealth().amount;
	}
	/**
	 * The Refit's rows at the top of the installed list (6.44, heromedel: no section of their own): the hull and its
	 * repairs, the hull breaches when there are any, then the reactor with its Up. Returns the y below them.
	 */
	private int repairRows(ShipState bs, int y, int w) {
		int scrap = hold();
		int hull = bs.getHullAmt(), max = maxHull(bs), each = homeplanet.parser.Pricing.hullRepair();
		int gap = max - hull, all = gap * each;
		SysRow repair = new SysRow("Hull " + hull + " / " + max, -1, "", null, "Hull repairs: " + each + " scrap a point (The Federation charges a premium)", null);
		if (hull < max) {
			// as FTL's stores: one point at a time, or all of it at once, each with its price
			repair.addButton("+1: " + each, 78, ROW_W - 82 - 82, scrap >= each, scrap >= each ? "Repair 1 point of hull for " + each + " scrap"
					: "A point costs " + each + " scrap; " + payerName() + " has " + scrap,
					new ActionListener() { public void actionPerformed(ActionEvent e) { repairHull(1); } });
			repair.addButton("All: " + all, 78, ROW_W - 82, scrap >= all, scrap >= all ? "Repair all " + gap + (gap == 1 ? " point" : " points") + " for " + all + " scrap"
					: "Repairing all " + gap + " points costs " + all + " scrap; " + payerName() + " has " + scrap + " (use +1 for what it can pay)",
					new ActionListener() { public void actionPerformed(ActionEvent e) { repairHull(Integer.MAX_VALUE); } });
		}
		repair.setBounds(0, y, w, 28);
		sysList.add(repair);
		y += 32;
		int breaches = bs.getBreachMap().size();
		if (breaches > 0) {
			int seal = breaches * homeplanet.parser.Pricing.BREACH_REPAIR;
			SysRow br = new SysRow("Hull breaches: " + breaches, -1, "", null, "Holes in " + homeplanet.model.Words.her() + " hull, venting air: " + homeplanet.parser.Pricing.BREACH_REPAIR + " scrap each to seal", null);
			br.addButton("Seal: " + seal, 78, ROW_W - 82, scrap >= seal, scrap >= seal ? "Seal all " + breaches + " for " + seal + " scrap" : "Sealing them costs " + seal + " scrap; " + payerName() + " has " + scrap,
					new ActionListener() { public void actionPerformed(ActionEvent e) { sealBreaches(); } });
			br.setBounds(0, y, w, 28);
			sysList.add(br);
			y += 32;
		}
		int bars = bs.getReservePowerCapacity(), rp = homeplanet.parser.Pricing.reactorBar(bars + 1);
		SysRow reactor = new SysRow("Reactor", bars, "", null, "Reactor power: " + bars + " bars", null);
		reactor.max = homeplanet.parser.Pricing.REACTOR_MAX;
		reactor.info(reactorInfo(bars));
		if (bars < homeplanet.parser.Pricing.REACTOR_MAX) {
			reactor.addButton("Up: " + rp, 78, ROW_W - 82, scrap >= rp, scrap >= rp ? "One more bar of reactor power for " + rp + " scrap" : "One more bar costs " + rp + " scrap; " + payerName() + " has " + scrap,
					new ActionListener() { public void actionPerformed(ActionEvent e) { upgradeReactor(); } });
		}
		reactor.setBounds(0, y, w, 28);
		sysList.add(reactor);
		return y + 32;
	}
	private void upgradeSystem(SystemType t) {
		ShipState bs = bay.currentSave.getPlayerShip();
		int price = upgradePrice(bs, t);
		if (price <= 0 || !charge(price)) return;
		SystemState st = bs.getSystem(t);
		st.setCapacity(st.getCapacity() + 1);
		if (t.isSubsystem()) st.setPower(st.getCapacity()); // subsystems run at their full level
		changes.add("Upgraded " + RefitShop.systemTitle(t.getId()) + " to level " + st.getCapacity() + " for " + price + " scrap");
		changed();
		bay.help("Upgraded the " + RefitShop.systemTitle(t.getId()) + " to level " + st.getCapacity() + ". Save makes it official.");
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
		changes.add("Mended the " + RefitShop.systemTitle(t.getId()) + " (" + n + (n == 1 ? " bar" : " bars") + ") for " + price + " scrap");
		changed();
		bay.help("Mended the " + RefitShop.systemTitle(t.getId()) + ". Save makes it official.");
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

	/** A system bought from a store for the Cargo Hold (5.30): stored at its starting level, to fit at Refit; kept on Save. */
	void storeBought(String id) {
		SystemType t = SystemType.findById(id);
		net.blerf.ftl.xml.SystemBlueprint sbp = net.blerf.ftl.parser.DataManager.get().getSystem(id);
		int level = t == SystemType.CLONEBAY ? 0 : sbp != null ? Math.max(1, sbp.getStartPower()) : 1; // a Clone Bay takes the Medbay's level
		stored.add(new Stored(id, level));
		changes.add("Bought " + RefitShop.systemTitle(id) + (level > 0 ? " (level " + level + ")" : "") + " into the Cargo Hold");
		changed();
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
		String name = RefitShop.systemTitle(sel.id) + (sel.level > 0 ? " (level " + sel.level + ")" : "");
		int price = salePrice(sel);
		if (!homeplanet.core.HomePlanet.confirmNo(bay, "Sell the " + name + " for " + price + " scrap?\n" + cap(payerName()) + " is paid (on Save).", "Sell")) return;
		stored.remove(sel);
		bill -= price;
		changes.add("Sold " + name + " for " + price + " scrap (" + payerName() + " was paid)");
		changed();
	}

	/**
	 * Scrapping and stripping (where allowed): the wreck's storable systems, as lines for the stored systems (into
	 * {@code store}; standard equipment and the Medbay stay with the hull; damaged ones go too, damaged). Returns log lines.
	 */
	static List<String> scrapSystems(ShipState wreck, List<String> store) {
		List<String> lines = new ArrayList<String>();
		for (SystemType t : SystemType.values()) {
			SystemState st = wreck.getSystem(t);
			if (st == null || st.getCapacity() <= 0 || storeReason(wreck, t) != null) continue;
			String name = RefitShop.systemTitle(t.getId());
			int level = t == SystemType.CLONEBAY ? 0 : st.getCapacity(), broken = level > 0 ? st.getDamagedBars() : 0; // damaged systems are kept, damaged
			store.add(homeplanet.vault.StoredSystems.line(t.getId(), level, broken));
			lines.add("+ " + name + (level > 0 ? " (level " + level + (broken > 0 ? ", " + broken + " broken" : "") + ")" : "") + " (system)");
		}
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
			String name = RefitShop.systemTitle(t.getId()) + (t == SystemType.CLONEBAY ? "" : " (level " + st.getCapacity() + (st.getDamagedBars() > 0 ? ", " + st.getDamagedBars() + " broken" : "") + ")");
			moved.add(name);
		}
		StringBuilder sb = new StringBuilder();
		if (moved.isEmpty()) sb.append(homeplanet.model.Words.cap(homeplanet.model.Words.she()) + " has no systems that can be stored; standard equipment stays with the hull.\n");
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
					+ "Send it to FTL via Slipstream with Settings > Mods > Patch mods (it comes with Federation Home Planet), then restart The Home Planet Station.", "Retrofit", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (undo) {
			List<String> missing = homeplanet.parser.Retrofit.missingStandard(ship);
			if (!missing.isEmpty()) {
				List<String> names = new ArrayList<String>();
				for (String id : missing) names.add(RefitShop.systemTitle(id));
				JOptionPane.showMessageDialog(bay, name + " is missing standard equipment of " + homeplanet.model.Words.her() + " original model:\n" + String.join(", ", names)
						+ "\n\nInstall these on " + homeplanet.model.Words.herObj() + " first; otherwise FTL would rebuild them for free.", "Undo Retrofit", JOptionPane.INFORMATION_MESSAGE);
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
					if (n != null && n.room != o.room) sb.append("\n  ").append(RefitShop.systemTitle(o.id)).append(": room ").append(n.room).append(" -> room ").append(o.room);
				}
				homeplanet.parser.CompanionMod.Remodel mine = homeplanet.parser.CompanionMod.find(
						homeplanet.parser.CompanionMod.load(), ship.getShipBlueprintId());
				if (mine != null && mine.doors != null) sb.append("\n  Doors: back to the model's own");
				if (sb.length() > 0) moves = "\n" + homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " remodel is set aside (kept on file) and these move back:" + sb + "\n";
			}
			// a system her original model has no room for (artillery, on anything but a Federation Cruiser) comes off her into the stored systems
			List<SystemType> homeless = homeplanet.parser.Retrofit.homeless(ship);
			if (!homeless.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				for (SystemType t : homeless) sb.append("\n  ").append(RefitShop.systemTitle(t.getId())).append(": no room on " + homeplanet.model.Words.her() + " original model, to the stored systems");
				moves += "\nTaken off " + homeplanet.model.Words.herObj() + ":" + sb + "\n";
			}
			if (JOptionPane.showConfirmDialog(bay, "Return " + name + " to " + homeplanet.model.Words.her() + " original ship model?\n" + homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " standard equipment can no longer be stored afterwards.\n" + moves,
					"Undo Retrofit", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
			for (SystemType t : homeless) {
				SystemState st = ship.getSystem(t);
				int level = st.getCapacity(), broken = st.getDamagedBars();
				clear(st);
				stored.add(new Stored(t.getId(), level, broken));
				storedSomething = true;
				changes.add("Stored " + RefitShop.systemTitle(t.getId()) + " (level " + level + (broken > 0 ? ", " + broken + " broken" : "") + ") from " + name + ": no room on " + homeplanet.model.Words.her() + " original model");
			}
		} else {
			if (JOptionPane.showConfirmDialog(bay, "Prepare " + name + " so any of " + homeplanet.model.Words.her() + " systems can be removed, including standard equipment.\n\n"
					+ "Warning: FTL may no longer count " + homeplanet.model.Words.herObj() + " as the original ship model for achievements.\n"
					+ homeplanet.model.Words.cap(homeplanet.model.Words.she()) + " will only load in FTL once the " + homeplanet.parser.Retrofit.MOD_NAME + " has been sent to FTL via Slipstream.",
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
		JOptionPane.showMessageDialog(bay, (undo ? name + " will return to " + homeplanet.model.Words.her() + " original ship model." : name + " will be retrofitted: any of " + homeplanet.model.Words.her() + " systems can be stored.")
				+ "\nPress Save to make it official.", undo ? "Undo Retrofit" : "Retrofit", JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * A weapon or drone dragged to another slot in the simulation (6.49): her save takes the new order (FTL's slots are
	 * her list's order), kept by Save and undone by Reset; one line in the changes says how they stand now.
	 */
	private void rearranged(boolean drones, int from, int to) {
		if (bay.currentSave == null) return;
		net.blerf.ftl.parser.SavedGameParser.ShipState ship = bay.currentSave.getPlayerShip();
		List<String> names = new ArrayList<String>();
		if (drones) {
			List<net.blerf.ftl.parser.SavedGameParser.DroneState> l = ship.getDroneList();
			if (from >= l.size() || to >= l.size()) return;
			l.add(to, l.remove(from));
			for (net.blerf.ftl.parser.SavedGameParser.DroneState d : l) names.add(homeplanet.model.Items.title(d.getDroneId()));
		} else {
			List<net.blerf.ftl.parser.SavedGameParser.WeaponState> l = ship.getWeaponList();
			if (from >= l.size() || to >= l.size()) return;
			l.add(to, l.remove(from));
			for (net.blerf.ftl.parser.SavedGameParser.WeaponState w : l) names.add(homeplanet.model.Items.title(w.getWeaponId()));
		}
		String head = drones ? "Drones rearranged: " : "Weapons rearranged: ";
		for (java.util.Iterator<String> it = changes.iterator(); it.hasNext();) if (it.next().startsWith(head)) it.remove();
		changes.add(head + String.join(", ", names) + " (on " + bay.currentSave.getPlayerShipName() + ")");
		bay.markDirty(); // not a refresh: the simulation's power stays as the player left it
		bay.refreshTrade(); // the Trade tab lists her weapons and drones in slot order
	}

	private void changed() {
		bay.markDirty();
		refresh();
		bay.shop.systemsChanged(); // the Shop greys out systems the ship already has
		bay.refreshTrade(); // Drone Control and Weapons change the slots
	}
}
