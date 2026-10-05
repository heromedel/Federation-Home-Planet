package homeplanet.ui;

import java.awt.BasicStroke;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.core.HomePlanet;
import homeplanet.model.Items;
import homeplanet.model.Crew;
import homeplanet.parser.Dlc;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;
import homeplanet.parser.SaveHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Cargo Bay: trade with the Cargo Hold or another docked ship (Trade), buy at the stores your ships are docked at (Shop),
 * and store, install and rearrange systems (Refit). Nothing is written until Save; Reset throws the changes away.
 *
 * Built in the Space Dock's style: FTL fonts and buttons over the hangar art. Your ship is always on the left and the
 * trading partner on the right; everything that moves between them goes through the column in the middle.
 */
public class CargoBayUI extends JPanel implements Scrollable {
	private static final Logger log = LoggerFactory.getLogger(CargoBayUI.class);
	static final int W = 1280, H = 680; // fits a 768-pixel-tall screen with the taskbar and title bar
	// the Trade tab's columns: your ship, the middle, the partner
	private static final int LX = 16, LW = 520, GX = 546, GW = 188, RX = 744, RW = 520;

	final MainFrame parent;
	Ship currentShip;
	Ship tradeShip;
	Ship homeSave;
	ArrayList<Ship> shipSelect = new ArrayList<Ship>();
	SavedGameState currentSave; // the boarded ship's save as the Cargo Bay has it (unsaved changes included)
	SavedGameState tradeSave;
	ShipState currentState;
	ShipState tradeState;
	File currentPath;
	private static final String NO_SHIP = "No ship picked: pick one with the button above (or board one at the Space Dock) to move things onto her. The Cargo Hold's goods can be sold or junked here.";
	/** The ship the Cargo Bay works on, picked here (heromedel: everything on the screen follows the pick, nothing the boarded ship); null: the boarded ship. */
	private Ship picked;
	File tradePath;
	/** Fingerprints of the two files as they were read: Save refuses if FTL changed either since (see Vault.Transaction). */
	String currentHash, tradeHash;
	private int partnerIndex = 0; // in shipSelect

	final DryDockShop shop = new DryDockShop(this);
	final SystemsPanel systems = new SystemsPanel(this);

	private boolean dirty = false;
	// Crew renamed since the last Save: crew -> name on disk (for the history log)
	private final java.util.IdentityHashMap<CrewState, String> crewRenames = new java.util.IdentityHashMap<CrewState, String>();
	/** Something junked, sold or retired since the last Save: for the history log. */
	private static class Disposal {
		final SavedGameState save; final String kind, invKey, line; final int scrap, amount;
		Disposal(SavedGameState save, String kind, String invKey, int scrap, String line) { this(save, kind, invKey, scrap, line, 1); }
		Disposal(SavedGameState save, String kind, String invKey, int scrap, String line, int amount) { this.save = save; this.kind = kind; this.invKey = invKey; this.scrap = scrap; this.line = line; this.amount = amount; }
	}
	private final List<Disposal> disposals = new ArrayList<Disposal>();

	// ---- the frame: top bar, tabs, help line ----
	private final JPanel stage = new JPanel(null);
	private final CardLayout cards = new CardLayout();
	private final JPanel tabs = new JPanel(cards);
	private final FtlButton[] tabButtons = new FtlButton[3];
	private final String[] tabNames = {"trade", "shop", "refit"};
	private static final String[] TAB_TIPS = {"Swap equipment, crew and supplies with the Cargo Hold or another docked ship", "Buy at the stores your ships are docked at",
			"Store, install and upgrade systems, upgrade the reactor, repair the hull, remodel, retrofit"};
	/** No ship aboard: the systems stored in the Cargo Hold, to sell. */
	private FtlButton storedBtn;
	private String currentTab = "trade";
	private final FtlButton saveBtn, resetBtn;
	private final CargoParts.Label help = new CargoParts.Label("", FtlFont.BODY, CargoParts.TEXT, -1);
	private final CargoParts.Label notice = new CargoParts.Label("", FtlFont.MENU, CargoParts.TEXT, 0);
	private final JPanel noticePanel = new JPanel(null);

	// ---- the Trade tab ----
	private final JPanel trade = new JPanel(null);
	private final JLabel myPic = new JLabel(), theirPic = new JLabel();
	private final CargoParts.Label mySub = new CargoParts.Label("", FtlFont.BODY, CargoParts.DIM, -1);
	private final FtlButton partnerBtn;
	/** Her name, as a drop-down of the ships she could swap for (mirrors the partner's). */
	private final FtlButton boardBtn;
	/** Where the two drop-downs sit in from the ship pictures, and their width: the sides mirror each other. */
	static final int DROP_IN = 132, DROP_W = 320;
	/** Info buttons beside each ship's line, mirrored: they open the same report as clicking her picture. */
	private FtlButton myInfo, theirInfo;
	private final CargoParts.Label partnerNote = new CargoParts.Label("", FtlFont.BODY, CargoParts.DIM, 1);
	/** Return (ship): shown aboard a borrowed ship (the repair job's Nightjar) that's ready to go home. */
	private FtlButton returnBtn;
	private static final String[][] SUPPLIES = {{"scrap", "Scrap"}, {"fuel", "Fuel"}, {"missiles", "Missiles"}, {"drones", "Parts"}};
	/** What FTL's stores charge for one (fuel 3, missile 6, drone part 8); selling, where allowed, pays half. */
	private static final int[] SUPPLY_PRICE = {0, homeplanet.parser.Pricing.FUEL, homeplanet.parser.Pricing.MISSILE, homeplanet.parser.Pricing.DRONE_PART};
	private final SupplyCell[] mySupply = new SupplyCell[4], theirSupply = new SupplyCell[4];
	private FtlButton myJunkSupply, mySellSupply, theirJunkSupply, theirSellSupply;
	private int supplyIdx = 0;
	private final CargoParts.Label moveLabel = new CargoParts.Label("", FtlFont.BODY, CargoParts.DIM, 0);
	private final JSpinner moveAmount = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
	/** The four item categories: 0 weapons, 1 drones, 2 augments, 3 crew. */
	private final Category[] cats = new Category[4];

	public CargoBayUI(MainFrame p) {
		this.parent = p;
		setLayout(null);
		setOpaque(true);
		setBackground(Color.black);
		stage.setOpaque(false);
		add(stage);

		// top bar
		FtlButton back = new FtlButton("", FtlFont.MENU, 262, 34) {
			@Override protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				boolean hot = getModel().isRollover() && !getModel().isPressed();
				BufferedImage t = FtlFont.MENU.render("RETURN TO DOCK", hot ? FtlButton.TEXT_HOT : FtlButton.TEXT);
				g.drawImage(t, 48, (getHeight() - t.getHeight()) / 2 + 1, null);
				g.setColor(CargoParts.GOLD);
				g.fillPolygon(new int[] {14, 26, 26}, new int[] {17, 8, 26}, 3);
				g.fillRect(26, 14, 12, 7);
			}
		};
		back.setToolTipText("Back to the Space Dock (unsaved changes are kept until you leave the Cargo Bay)");
		back.setBounds(16, 12, 262, 34);
		back.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (confirmLeave("return to the Space Dock")) parent.showSpaceDock();
			}
		});
		stage.add(back);
		String[] titles = {"Trade", "Shop", "Refit"};
		String[] tips = TAB_TIPS;
		for (int i = 0; i < 3; i++) {
			final String name = tabNames[i];
			tabButtons[i] = new FtlButton(titles[i], FtlFont.MENU, 120, 34);
			tabButtons[i].setBounds(452 + i * 128, 12, 120, 34);
			tabButtons[i].setToolTipText(tips[i]);
			tabButtons[i].addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) { showTab(name); }
			});
			stage.add(tabButtons[i]);
		}
		resetBtn = new FtlButton("Reset", FtlFont.MENU, 110, 34);
		resetBtn.setBounds(1000, 12, 110, 34);
		resetBtn.setToolTipText("Throw away the changes you haven't saved");
		resetBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { init(); help("Changes discarded. Everything stands as it was last saved."); }
		});
		stage.add(resetBtn);
		saveBtn = new FtlButton("Save", FtlFont.MENU, 146, 34) {
			@Override protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				if (dirty) { g.setColor(CargoParts.GOLD); g.fillOval(16, 13, 9, 9); }
			}
		};
		saveBtn.setBounds(1118, 12, 146, 34);
		saveBtn.setToolTipText("Write every change: trades, purchases, systems (the gold dot means there are unsaved changes)");
		saveBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { saveAll(); }
		});
		stage.add(saveBtn);

		tabs.setOpaque(false);
		tabs.setBounds(0, 52, W, H - 52 - 26);
		stage.add(tabs);
		JComponent helpStrip = new JComponent() {
			@Override protected void paintComponent(Graphics g) { g.setColor(new Color(10, 14, 20, 210)); g.fillRect(0, 0, getWidth(), getHeight()); }
		};
		helpStrip.setBounds(0, H - 24, W, 24);
		help.setBounds(16, H - 24, W - 32, 24);
		stage.add(help);
		stage.add(helpStrip);

		// the notice shown instead of the tabs (no boarded ship, or not at a station)
		noticePanel.setOpaque(false);
		notice.setBounds(290, 250, 700, 120);
		noticePanel.add(notice);

		partnerBtn = dropButton();
		boardBtn = dropButton();
		buildTrade();
		tabs.add(trade, "trade");
		tabs.add(shop.panel(), "shop");
		tabs.add(systems.panel(), "refit");
		tabs.add(noticePanel, "notice");
	}

	// ---- layout: the 1280 x 720 stage centred over the hangar, which fills the window ----

	/** The stage's empty margin each side (nothing is drawn in it, so a narrow window may cut into it), and how far left of centre it sits. */
	static final int MARGIN = 12, SHIFT = 8;
	@Override public void doLayout() {
		int x = Math.max(-MARGIN, (getWidth() - W) / 2 - SHIFT), y = Math.max(0, (getHeight() - H) / 2);
		stage.setBounds(x, y, W, H);
	}
	@Override public Dimension getPreferredSize() { return new Dimension(W - 2 * MARGIN, H); }
	public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
	public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 20; }
	public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 200; }
	public boolean getScrollableTracksViewportWidth() { return getParent() != null && getParent().getWidth() >= W - 2 * MARGIN; }
	public boolean getScrollableTracksViewportHeight() { return getParent() != null && getParent().getHeight() >= H; }

	/** The Cargo Hold's deck plan, rolled at startup and on Ctrl+Shift+B (with the Space Dock's ships); null if it couldn't be drawn. */
	private static BufferedImage hold = null;
	private static final java.util.Random holdRng = new java.util.Random();
	private BufferedImage hangar, hangarScaled;

	/** The Cargo Hold's deck plan as last rolled (Long Range Comm. stands in the hold too), or null. */
	static BufferedImage holdBackdrop() { return hold; }
	/** A new roll of the Cargo Hold's contents. */
	static void rerollHold(MainFrame frame) {
		BufferedImage b = CargoHoldBackdrop.make(frame, holdRng);
		if (b != null) hold = b;
	}

	@Override protected void paintComponent(Graphics g0) {
		super.paintComponent(g0);
		Graphics2D g = (Graphics2D) g0;
		if (hold != null && hangar != hold) { // a new roll: scale it again
			hangar = hold;
			hangarScaled = null;
		}
		if (hangar != null) {
			int w = getWidth(), h = getHeight();
			double s = Math.max(w / (double) hangar.getWidth(), h / (double) hangar.getHeight());
			int dw = (int) Math.ceil(hangar.getWidth() * s), dh = (int) Math.ceil(hangar.getHeight() * s);
			if (hangarScaled == null || hangarScaled.getWidth() != dw || hangarScaled.getHeight() != dh) {
				hangarScaled = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
				Graphics2D sg = hangarScaled.createGraphics();
				sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR); // keep the pixel art sharp
				sg.drawImage(hangar, 0, 0, dw, dh, null);
				sg.setColor(new Color(6, 10, 16, 150));
				sg.fillRect(0, 0, dw, dh);
				sg.dispose();
			}
			g.drawImage(hangarScaled, (w - dw) / 2, 0, null);
		}
	}

	// ---- tabs, help, dirty ----

	void showTab(String name) {
		if (tradeUnavailable()) name = "notice";
		else if (holdOnly()) name = "trade"; // the Shop and the Refit tab work on the picked ship
		currentTab = name;
		cards.show(tabs, name);
		for (int i = 0; i < 3; i++) tabButtons[i].setLit(tabNames[i].equals(name));
		if (name.equals("trade")) help(describeSelection());
		else if (name.equals("shop")) { shop.rebuild(); help(shop.helpText()); } // scrap moved in Trade (or made by selling) since the last look counts
		else if (name.equals("refit")) help(systems.helpText());
	}
	void help(String s) { help.setText(s == null ? "" : s); }
	/** Something changed that Save would write. */
	void markDirty() { dirty = true; saveBtn.repaint(); }
	boolean isDirty() { return dirty; }
	/** Asks before throwing away unsaved changes; true to go on. */
	boolean confirmLeave(String doing) {
		if (!dirty) return true;
		Object[] opts = {"Save first", "Discard changes", "Cancel"};
		int r = JOptionPane.showOptionDialog(this, "You have unsaved changes in the Cargo Bay.\nSave them before you " + doing + "?", "Unsaved changes",
				JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
		if (r == 0) { saveAll(); return !dirty; }
		return r == 1;
	}
	private boolean tradeUnavailable() {
		if (currentPath == null) return !holdOnly(); // no ship aboard: the Cargo Hold alone can still be looked over
		return currentSave != null && !Vault.get().mayTrade(currentShip);
	}
	/** No ship aboard, and the Cargo Hold readable: the Trade tab shows the hold alone, its goods to sell or junk. */
	boolean holdOnly() {
		return currentPath == null && homeSave != null && homeSave.save() != null;
	}

	// ---- loading ----

	/** Opens on the boarded ship (the Cargo Hold alone with none aboard), as the Long Range does: the pick is made afresh. */
	public void openOnBoarded() { picked = null; init(); }
	/** Reads the picked ship, the storage and the chosen partner from disk (throwing away unsaved changes) and rebuilds every tab. */
	public void init() {
		crewRenames.clear();
		disposals.clear();
		dirty = false;
		loadCurrent();
		try {
			homeSave = Vault.get().storage();
		} catch (java.io.IOException e) {
			homeSave = null;
			HomePlanet.showErrorDialog("Could not open the Cargo Hold:\n" + e);
		}
		if (homeSave != null && homeSave.save() == null) {
			HomePlanet.showErrorDialog("Could not read the Cargo Hold's file:\n" + homeSave.file() + "\n\n" + homeSave.readError());
		}
		String partnerId = tradeShip == null ? null : tradeShip.id;
		shipSelect = new ArrayList<Ship>();
		if (homeSave != null && homeSave.save() != null) shipSelect.add(homeSave);
		if (currentShip != null) for (Ship s : tradeableShips()) if (s != currentShip && Vault.get().mayTrade(s)) shipSelect.add(s); // no ship aboard: the hold alone
		// keep the same partner across a Reset or Save, if she's still there
		partnerIndex = 0;
		if (partnerId != null) for (int i = 0; i < shipSelect.size(); i++) if (shipSelect.get(i).id.equals(partnerId)) partnerIndex = i;
		loadPartner();
		shop.init();
		systems.init();
		refreshTrade();
		if (currentPath == null) notice.setText("No ship picked: pick one above, or board one at the Space Dock.");
		else if (!Vault.get().mayTrade(currentShip)) notice.setText(currentSave.getPlayerShipName() + " is not within range of a station. Take her to a beacon with a store to trade.");
		for (int i = 0; i < tabButtons.length; i++) {
			boolean shipTab = i > 0; // the Shop and the Refit tab
			tabButtons[i].setEnabled(!tradeUnavailable() && !(shipTab && holdOnly()));
			tabButtons[i].setToolTipText(shipTab && holdOnly() ? "Pick a ship first (the button above): the " + (i == 1 ? "Shop sells to" : "Refit tab works on") + " the picked ship" : TAB_TIPS[i]);
		}
		storedBtn.setVisible(holdOnly() && !systems.storedList().isEmpty());
		saveBtn.setEnabled(!tradeUnavailable());
		resetBtn.setEnabled(!tradeUnavailable());
		showTab(tradeUnavailable() ? "notice" : currentTab);
		saveBtn.repaint();
		revalidate();
		repaint();
	}
	/** Kept for the Space Dock's call after init(): the partner is loaded by init(). */
	public void tradeShipInit() { }

	private void loadCurrent() {
		currentShip = picked != null && Vault.get().fleet().contains(picked) ? picked : Vault.get().boarded();
		if (currentShip != picked) picked = null; // she's gone (boarded elsewhere, decommissioned): back to the boarded ship
		if (currentShip == null || !currentShip.file().exists()) {
			currentShip = null;
			currentPath = null;
			currentSave = new SavedGameState();
			currentSave.setPlayerShipName("No Ship Selected");
			currentSave.setDLCEnabled(false);
			currentState = new ShipState("No Ship Selected", new ShipBlueprint(), false);
			currentSave.setPlayerShip(currentState);
			return;
		}
		currentPath = currentShip.file();
		currentHash = null;
		try {
			Vault.Copy c = Vault.get().readCopy(currentShip); // the Cargo Bay's own copy: nothing sticks until Save
			currentSave = c.save;
			currentHash = c.hash;
		} catch (Exception e) {
			log.error("Could not read " + currentPath, e);
			HomePlanet.showErrorDialog("Could not read " + currentPath + "\n\n" + e);
			currentSave = currentShip.save();
			if (currentSave == null) { currentShip = null; loadCurrent(); return; }
		}
		currentState = currentSave.getPlayerShip();
	}
	private void loadPartner() {
		if (shipSelect.isEmpty()) {
			tradeShip = null;
			tradePath = null;
			tradeSave = new SavedGameState();
			tradeSave.setPlayerShipName("No Ship Selected");
			tradeSave.setPlayerShip(new ShipState("No Ship Selected", new ShipBlueprint(), false));
		} else {
			tradeShip = shipSelect.get(Math.min(partnerIndex, shipSelect.size() - 1));
			tradePath = tradeShip.file();
			tradeHash = null;
			try {
				Vault.Copy c = Vault.get().readCopy(tradeShip);
				tradeSave = c.save;
				tradeHash = c.hash;
			} catch (Exception e) {
				log.error("Could not read " + tradePath, e);
				HomePlanet.showErrorDialog("Could not read " + tradePath + "\n\n" + e);
				tradeSave = tradeShip.save();
				if (tradeSave == null) { shipSelect.remove(tradeShip); partnerIndex = 0; loadPartner(); return; }
			}
		}
		tradeState = tradeSave.getPlayerShip();
	}
	boolean partnerIsStorage() { return tradeShip == homeSave; }

	/** The ships at the Space Dock (boarded and docked) whose saves can be read: any of them can trade (see {@link Dlc} for what may move). */
	ArrayList<Ship> tradeableShips() {
		ArrayList<Ship> list = new ArrayList<Ship>();
		for (Ship s : Vault.get().fleet()) if (s.save() != null) list.add(s);
		return list;
	}

	private void pickPartner() {
		JPopupMenu m = new JPopupMenu();
		for (int i = 0; i < shipSelect.size(); i++) {
			final int idx = i;
			Ship s = shipSelect.get(i);
			JMenuItem it = new JMenuItem((s == homeSave ? "Cargo Hold" : s.name) + (i == partnerIndex ? "   (now)" : ""));
			it.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if (idx == partnerIndex) return;
					if (!confirmLeave("switch trading partners")) return;
					partnerIndex = idx;
					tradeShip = shipSelect.get(idx);
					init();
					help("Now trading with " + partnerName() + ".");
				}
			});
			m.add(it);
		}
		if (shipSelect.size() <= 1) {
			JMenuItem none = new JMenuItem("No other ships are docked at a station");
			none.setEnabled(false);
			m.add(none);
		}
		CargoParts.darkPopup(m);
		m.show(partnerBtn, 0, partnerBtn.getHeight());
	}
	/** A drop-down button: the name, with a small arrow at the right. */
	static FtlButton dropButton() {
		FtlButton b = new FtlButton("", FtlFont.MENU, DROP_W, 30) {
			@Override protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				g.setColor(getModel().isRollover() ? FtlButton.TEXT_HOT : CargoParts.TEXT);
				int x = getWidth() - 20;
				g.fillPolygon(new int[] {x, x + 10, x + 5}, new int[] {12, 12, 18}, 3);
			}
		};
		b.reserveRight = 26;
		return b;
	}
	/** Pick another ship to work on, without boarding anyone: the ships at the Space Dock, aboard or docked, as the partner list has them. */
	private void pickBoard() {
		JPopupMenu m = new JPopupMenu();
		Ship aboard = Vault.get().boarded();
		if (currentPath != null) {
			JMenuItem now = new JMenuItem(currentSave.getPlayerShipName() + (currentShip == aboard ? "   (aboard, shown)" : "   (shown)"));
			now.setEnabled(false);
			m.add(now);
		}
		boolean any = false;
		for (final Ship s : boardable()) {
			any = true;
			JMenuItem it = new JMenuItem(s.name + (s == aboard ? "   (aboard)" : ""));
			it.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { pick(s); } });
			m.add(it);
		}
		if (!any) {
			JMenuItem none = new JMenuItem(currentPath == null ? "No ships are docked at a station" : "No other ships are docked at a station");
			none.setEnabled(false);
			m.add(none);
		}
		CargoParts.darkPopup(m);
		m.show(boardBtn, 0, boardBtn.getHeight());
	}
	/** The ships that may be picked from here: at the Space Dock, readable (with no ship picked the partner list holds the Cargo Hold alone, so they come from the fleet). */
	private List<Ship> boardable() {
		List<Ship> out = new ArrayList<Ship>();
		List<Ship> from = new ArrayList<Ship>(shipSelect);
		if (currentPath == null) for (Ship s : tradeableShips()) if (!from.contains(s) && Vault.get().mayTrade(s)) from.add(s);
		for (Ship s : from) if (s != homeSave && s != currentShip && s.save() != null) out.add(s);
		return out;
	}
	/**
	 * Works on her from now on: the screen switches at once, nobody is boarded or docked. The trading partner stays,
	 * unless she's the ship picked: then the ship shown before takes her place.
	 */
	void pick(Ship s) {
		if (s == null || s.save() == null || s == currentShip) return;
		if (!confirmLeave("switch to " + the(s.name))) return;
		Ship left = currentShip;
		if (s == tradeShip) tradeShip = left;
		picked = s;
		init();
		help("The Cargo Bay now works on " + the(s.name) + "." + (left != null && left == tradeShip ? " " + left.name + " is your trading partner." : ""));
	}
	/** "the Kestrel", but "The Theseus" as she is (no "the The"). */
	private static String the(String name) { return name.toLowerCase().startsWith("the ") ? name : "the " + name; }
	/** What the storage is, for its info button. */
	void storageInfo() {
		JOptionPane.showMessageDialog(this, "<html><div style='width:360px'><b>The Cargo Hold</b><br><br>"
				+ "The Home Planet Station's own hold. Weapons, drones, augments, crew and supplies (scrap, fuel, missiles and drone parts) "
				+ "wait here when they aren't aboard any ship, with no slot limits.<br><br>"
				+ "Send things here from your ship, then take them aboard any ship docked at a station. "
				+ "Systems taken off in the Refit tab are kept on that tab's own list.</div></html>",
				"Cargo Hold", JOptionPane.PLAIN_MESSAGE);
	}
	private String partnerName() { return partnerIsStorage() ? "Cargo Hold" : tradeSave.getPlayerShipName(); }

	// ============================================================== Trade tab

	/** One item category row: its two lists and the middle buttons. */
	private class Category {
		final int kind; // 0 weapons, 1 drones, 2 augments, 3 crew
		final CargoParts.Header myHead, theirHead;
		final CargoParts.RowList mine = new CargoParts.RowList(), theirs = new CargoParts.RowList();
		final List<Component> myButtons = new ArrayList<Component>(), theirButtons = new ArrayList<Component>();
		Category(int kind, int y) {
			this.kind = kind;
			String title = kind == 0 ? "Weapons" : kind == 1 ? "Drones" : kind == 2 ? "Augments" : "Crew";
			myHead = new CargoParts.Header(title, false);
			theirHead = new CargoParts.Header(title, true);
			myHead.setBounds(LX, y, LW, 22);
			theirHead.setBounds(RX, y, RW, 22);
			mine.setBounds(LX, y + 22, LW, 86);
			theirs.setBounds(RX, y + 22, RW, 86);
			trade.add(myHead); trade.add(theirHead); trade.add(mine); trade.add(theirs);
			int by = y + 22 + 16;
			if (kind == 3) {
				myButtons.add(icon(CargoParts.infoIcon(), 4, by, "Her report (and rename)", new ActionListener() { public void actionPerformed(ActionEvent e) { crewInfo(true); } }));
				myButtons.add(button("Retire", 30, by, 70, "Retire this crew member: they leave for good", new ActionListener() { public void actionPerformed(ActionEvent e) { retire(true); } }));
				myButtons.add(button("Send >", 104, by, 80, "Send this crew member to the partner", new ActionListener() { public void actionPerformed(ActionEvent e) { sendCrew(true); } }));
				theirButtons.add(button("< Take", 4, by + 28, 80, "Take this crew member aboard your ship", new ActionListener() { public void actionPerformed(ActionEvent e) { sendCrew(false); } }));
				theirButtons.add(button("Retire", 88, by + 28, 70, "Retire this crew member: they leave for good", new ActionListener() { public void actionPerformed(ActionEvent e) { retire(false); } }));
				theirButtons.add(icon(CargoParts.infoIcon(), 160, by + 28, "Her report (and rename)", new ActionListener() { public void actionPerformed(ActionEvent e) { crewInfo(false); } }));
				mine.setEmptyText("No crew");
				theirs.setEmptyText("No crew");
				mine.onDoubleClick(new Runnable() { public void run() { crewInfo(true); } });
				theirs.onDoubleClick(new Runnable() { public void run() { crewInfo(false); } });
			} else {
				myButtons.add(icon(CargoParts.infoIcon(), 4, by, "What it does: power, damage, price", new ActionListener() { public void actionPerformed(ActionEvent e) { itemInfo(true, Category.this.kind); } }));
				myButtons.add(icon(CargoParts.trashIcon(), 30, by, "Junk it: it's gone, you get nothing", new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(true, Category.this.kind, false); } }));
				myButtons.add(icon(IconFactory.supplyIcon("scrap"), 56, by, "Sell it for half its price", new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(true, Category.this.kind, true); } }));
				myButtons.add(button("Send >", 86, by, 98, "Send it to the partner", new ActionListener() { public void actionPerformed(ActionEvent e) { sendItem(true, Category.this.kind); } }));
				theirButtons.add(button("< Take", 4, by + 28, 98, "Take it onto your ship", new ActionListener() { public void actionPerformed(ActionEvent e) { sendItem(false, Category.this.kind); } }));
				theirButtons.add(icon(IconFactory.supplyIcon("scrap"), 108, by + 28, "Sell it for half its price", new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(false, Category.this.kind, true); } }));
				theirButtons.add(icon(CargoParts.trashIcon(), 134, by + 28, "Junk it: it's gone, you get nothing", new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(false, Category.this.kind, false); } }));
				theirButtons.add(icon(CargoParts.infoIcon(), 160, by + 28, "What it does: power, damage, price", new ActionListener() { public void actionPerformed(ActionEvent e) { itemInfo(false, Category.this.kind); } }));
				mine.onDoubleClick(new Runnable() { public void run() { itemInfo(true, Category.this.kind); } });
				theirs.onDoubleClick(new Runnable() { public void run() { itemInfo(false, Category.this.kind); } });
			}
			Runnable mineSel = new Runnable() { public void run() { if (mine.selectedValue() != null) { clearOthers(Category.this, true); } updateButtons(); help(describeSelection()); } };
			Runnable theirSel = new Runnable() { public void run() { if (theirs.selectedValue() != null) { clearOthers(Category.this, false); } updateButtons(); help(describeSelection()); } };
			mine.onChange(mineSel);
			theirs.onChange(theirSel);
		}
		void updateButtons() {
			boolean m = mine.selectedValue() != null, t = theirs.selectedValue() != null;
			for (Component c : myButtons) c.setEnabled(m);
			for (Component c : theirButtons) c.setEnabled(t);
		}
	}
	private FtlButton button(String text, int x, int y, int w, String tip, ActionListener a) {
		FtlButton b = new FtlButton(text, FtlFont.BODY, w, 22);
		b.setBounds(GX + x, y, w, 22);
		b.setToolTipText(tip);
		b.addActionListener(a);
		b.setEnabled(false);
		trade.add(b);
		return b;
	}
	private FtlButton icon(javax.swing.Icon ic, int x, int y, String tip, ActionListener a) {
		CargoParts.IconButton b = new CargoParts.IconButton(ic, tip, a);
		b.setBounds(GX + x, y, 24, 22);
		b.setEnabled(false);
		trade.add(b);
		return b;
	}
	/** Only one thing is selected at a time, so the middle buttons always act on what you last clicked. */
	private boolean clearing = false;
	private void clearOthers(Category keep, boolean keepMine) {
		if (clearing) return;
		clearing = true;
		for (Category c : cats) {
			if (c != keep || !keepMine) c.mine.list.clearSelection();
			if (c != keep || keepMine) c.theirs.list.clearSelection();
			c.updateButtons();
		}
		clearing = false;
	}

	private void buildTrade() {
		trade.setOpaque(false);
		// the stage is offset 52 px for the tabs; Trade's coordinates below are the mockup's, less 52
		int o = -52;
		myPic.setBounds(LX, 60 + o, 120, 62);
		myPic.setHorizontalAlignment(JLabel.CENTER);
		myPic.setToolTipText("Click for her report, and to rename her");
		myPic.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		myPic.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { showCurrentShipInfo(); } });
		trade.add(myPic);
		storedBtn = new FtlButton("Stored Systems", FtlFont.BODY, 170, 24);
		storedBtn.setBounds(LX + DROP_IN, 107 + o, 170, 24); // under "No ship aboard", where her report's line would be
		storedBtn.setToolTipText("The ship systems stored in the Cargo Hold: sell them here (the hold is paid on Save)");
		storedBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { storedSystems(); } });
		storedBtn.setVisible(false);
		trade.add(storedBtn);
		// the two sides mirror: a small label, the ship's drop-down, then her grey line with an info button on the outside
		CargoParts.Label ca = new CargoParts.Label("CURRENTLY ABOARD", FtlFont.BODY, CargoParts.DIM, -1);
		ca.setBounds(LX + DROP_IN, 58 + o, DROP_W, 16);
		trade.add(ca);
		boardBtn.setBounds(LX + DROP_IN, 74 + o, DROP_W, 30);
		boardBtn.setToolTipText("Board another of your ships docked at a station, without leaving the Cargo Bay");
		boardBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { pickBoard(); } });
		trade.add(boardBtn);
		myInfo = new CargoParts.IconButton(CargoParts.infoIcon(), "Her report, and to rename her", new ActionListener() { public void actionPerformed(ActionEvent e) { showCurrentShipInfo(); } });
		myInfo.setBounds(LX + DROP_IN, 107 + o, 24, 22);
		trade.add(myInfo);
		mySub.setBounds(LX + DROP_IN + 30, 110 + o, 400, 16);
		trade.add(mySub);
		theirPic.setBounds(RX + RW - 120, 60 + o, 120, 62);
		theirPic.setHorizontalAlignment(JLabel.CENTER);
		theirPic.setToolTipText("Click for her report, and to rename her");
		theirPic.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		theirPic.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) { if (!partnerIsStorage()) showPartnerInfo(); }
		});
		trade.add(theirPic);
		CargoParts.Label tw = new CargoParts.Label("TRADING WITH", FtlFont.BODY, CargoParts.DIM, 1);
		tw.setBounds(RX + RW - DROP_IN - DROP_W, 58 + o, DROP_W, 16);
		trade.add(tw);
		partnerBtn.setBounds(RX + RW - DROP_IN - DROP_W, 74 + o, DROP_W, 30);
		partnerBtn.setToolTipText("Choose who to trade with: the Cargo Hold, or another of your ships docked at a station");
		partnerBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { pickPartner(); } });
		trade.add(partnerBtn);
		theirInfo = new CargoParts.IconButton(CargoParts.infoIcon(), "Her report", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (partnerIsStorage()) storageInfo();
				else showPartnerInfo();
			}
		});
		theirInfo.setBounds(RX + RW - DROP_IN - 24, 107 + o, 24, 22);
		trade.add(theirInfo);
		partnerNote.setBounds(RX + RW - DROP_IN - 30 - 400, 110 + o, 400, 16);
		trade.add(partnerNote);
		// beside the ship you're aboard: it's her it returns
		returnBtn = new FtlButton("Return", FtlFont.BODY, 180, 24);
		returnBtn.setBounds(LX + LW - 180, 106 + o, 180, 24);
		returnBtn.setToolTipText("The Home Planet Station reports her fully repaired: send her back to her owner, and be paid");
		returnBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { returnHer(); } });
		returnBtn.setVisible(false);
		trade.add(returnBtn);
		JComponent how = new JComponent() {
			@Override protected void paintComponent(Graphics g0) {
				Graphics2D g = (Graphics2D) g0.create();
				String s = "SELECT, THEN SEND";
				CargoParts.text(g, s, FtlFont.BODY, CargoParts.DIM, (getWidth() - CargoParts.width(s, FtlFont.BODY)) / 2, 0);
				g.setColor(CargoParts.GOLD);
				g.setStroke(new BasicStroke(2f));
				int y = 24, w = getWidth();
				g.drawLine(30, y, w - 30, y);
				g.fillPolygon(new int[] {w - 22, w - 32, w - 32}, new int[] {y, y - 6, y + 6}, 3);
				g.fillPolygon(new int[] {22, 32, 32}, new int[] {y, y - 6, y + 6}, 3);
				g.dispose();
			}
		};
		how.setBounds(GX, 70 + o, GW, 34);
		trade.add(how);

		// supplies
		int y = 124 + o;
		CargoParts.Header sh = new CargoParts.Header("Supplies", false), th = new CargoParts.Header("Supplies", true);
		sh.setBounds(LX, y, LW, 22);
		th.setBounds(RX, y, RW, 22);
		trade.add(sh); trade.add(th);
		for (int i = 0; i < 4; i++) {
			final int idx = i;
			mySupply[i] = new SupplyCell(i, true);
			mySupply[i].setBounds(LX + i * 130, y + 26, 122, 44);
			theirSupply[i] = new SupplyCell(i, false);
			theirSupply[i].setBounds(RX + 2 + i * 130, y + 26, 122, 44);
			MouseAdapter pick = new MouseAdapter() { @Override public void mousePressed(MouseEvent e) { selectSupply(idx); } };
			mySupply[i].addMouseListener(pick);
			theirSupply[i].addMouseListener(pick);
			trade.add(mySupply[i]);
			trade.add(theirSupply[i]);
		}
		moveLabel.setBounds(GX, y + 8, GW, 16);
		trade.add(moveLabel);
		FtlButton left = new FtlButton("<", FtlFont.MENU, 36, 32), right = new FtlButton(">", FtlFont.MENU, 36, 32);
		left.setBounds(GX + 8, y + 32, 36, 32);
		right.setBounds(GX + GW - 44, y + 32, 36, 32);
		left.setToolTipText("Take that much from the partner (hold to keep taking)");
		right.setToolTipText("Send that much to the partner (hold to keep sending)");
		AutoRepeat.attach(left, new Runnable() { public void run() { moveSupply(false); } });
		AutoRepeat.attach(right, new Runnable() { public void run() { moveSupply(true); } });
		trade.add(left); trade.add(right);
		moveAmount.setBounds(GX + 50, y + 32, 88, 32);
		moveAmount.setToolTipText("How much each arrow moves");
		styleSpinner(moveAmount);
		trade.add(moveAmount);
		// everything at once, either way (heromedel: the arrows start at 1; these move the lot)
		FtlButton allLeft = new FtlButton("< all", FtlFont.BODY, 84, 22), allRight = new FtlButton("all >", FtlFont.BODY, 84, 22);
		allLeft.setBounds(GX + 8, y + 70, 84, 22);
		allRight.setBounds(GX + GW - 92, y + 70, 84, 22);
		allLeft.setToolTipText("Take all of it from the partner");
		allRight.setToolTipText("Send all of it to the partner");
		allLeft.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { moveAllSupply(false); } });
		allRight.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { moveAllSupply(true); } });
		trade.add(allLeft); trade.add(allRight);
		// missiles and drone parts can be junked (and sold, when the house rule allows): that many, from the side chosen
		myJunkSupply = icon(CargoParts.trashIcon(), 8, y + 68, "Junk that many of your ship's missiles or drone parts", new ActionListener() { public void actionPerformed(ActionEvent e) { disposeSupply(true, false); } });
		mySellSupply = icon(IconFactory.supplyIcon("scrap"), 34, y + 68, "Sell that many of your ship's missiles or drone parts (half the store price)", new ActionListener() { public void actionPerformed(ActionEvent e) { disposeSupply(true, true); } });
		theirSellSupply = icon(IconFactory.supplyIcon("scrap"), GW - 58, y + 68, "Sell that many of the partner's missiles or drone parts (half the store price)", new ActionListener() { public void actionPerformed(ActionEvent e) { disposeSupply(false, true); } });
		theirJunkSupply = icon(CargoParts.trashIcon(), GW - 32, y + 68, "Junk that many of the partner's missiles or drone parts", new ActionListener() { public void actionPerformed(ActionEvent e) { disposeSupply(false, false); } });
		updateSupplyButtons();

		// items and crew
		for (int k = 0; k < 4; k++) cats[k] = new Category(k, 204 + o + k * 112);
	}
	private static void styleSpinner(JSpinner sp) {
		sp.setOpaque(false);
		sp.setBorder(javax.swing.BorderFactory.createLineBorder(CargoParts.BOX_LINE));
		javax.swing.JFormattedTextField tf = ((JSpinner.DefaultEditor) sp.getEditor()).getTextField();
		tf.setHorizontalAlignment(javax.swing.JTextField.CENTER);
		tf.setFont(tf.getFont().deriveFont(java.awt.Font.BOLD, 16f));
		tf.setForeground(CargoParts.GOLD);
		tf.setBackground(new Color(16, 20, 26));
		tf.setCaretColor(CargoParts.GOLD);
		tf.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		for (Component c : sp.getComponents()) if (c instanceof javax.swing.JButton) c.setBackground(new Color(28, 36, 44));
	}

	/** A supply box: icon, name, amount. Click one to move that supply. */
	private class SupplyCell extends CargoParts.Cell {
		final int idx; final boolean mine;
		SupplyCell(int idx, boolean mine) {
			this.idx = idx; this.mine = mine;
			setToolTipText("Click, then use the arrows in the middle to move " + SUPPLIES[idx][1].toLowerCase());
			setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		}
		@Override protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			javax.swing.Icon ic = IconFactory.supplyIcon(SUPPLIES[idx][0]);
			if (ic != null) ic.paintIcon(this, g, 8, 6);
			CargoParts.text(g, SUPPLIES[idx][1], FtlFont.BODY, CargoParts.DIM, 28, 7);
			ShipState s = mine ? currentState : tradeState;
			if (s == null) return;
			String v = "" + supply(s, idx);
			CargoParts.text(g, v, FtlFont.MENU, CargoParts.TEXT, getWidth() - 8 - CargoParts.width(v, FtlFont.MENU), 20);
		}
	}
	private static int supply(ShipState s, int i) {
		return i == 0 ? s.getScrapAmt() : i == 1 ? s.getFuelAmt() : i == 2 ? s.getMissilesAmt() : s.getDronePartsAmt();
	}
	private static void setSupply(ShipState s, int i, int v) {
		if (i == 0) s.setScrapAmt(v); else if (i == 1) s.setFuelAmt(v); else if (i == 2) s.setMissilesAmt(v); else s.setDronePartsAmt(v);
	}
	private void selectSupply(int i) {
		supplyIdx = i;
		for (int k = 0; k < 4; k++) { mySupply[k].selected = k == i; theirSupply[k].selected = k == i; mySupply[k].repaint(); theirSupply[k].repaint(); }
		moveLabel.setText("MOVE " + SUPPLIES[i][1].toUpperCase());
		updateSupplyButtons();
		if (currentTab.equals("trade")) help("Moving " + SUPPLIES[i][1].toLowerCase() + ": set the amount in the middle, then > sends it to " + partnerName() + " and < takes it from them.");
	}
	/** The junk and sell buttons under the arrows: only for missiles and drone parts; sell only under the house rule. */
	void updateSupplyButtons() {
		if (myJunkSupply == null) return;
		boolean can = supplyIdx >= 2 && currentState != null && tradeState != null;
		mySellSupply.setVisible(HomePlanet.sellSupplies());
		String share = homeplanet.core.Economy.supplyShare();
		mySellSupply.setToolTipText("Sell that many of your ship's missiles or drone parts (" + share + ")");
		theirSellSupply.setToolTipText("Sell that many of the partner's missiles or drone parts (" + share + ")");
		theirSellSupply.setVisible(HomePlanet.sellSupplies());
		myJunkSupply.setEnabled(can); mySellSupply.setEnabled(can);
		theirJunkSupply.setEnabled(can); theirSellSupply.setEnabled(can);
	}
	/** Junks or sells the chosen amount of one side's missiles or drone parts. */
	private void disposeSupply(boolean mine, boolean sell) {
		if (supplyIdx < 2) { help("Only missiles and drone parts can be " + (sell ? "sold" : "junked") + "; scrap and fuel move with the arrows."); return; }
		SavedGameState save = mine ? currentSave : tradeSave;
		ShipState state = mine ? currentState : tradeState;
		String what = supplyIdx == 2 ? "missiles" : "drone parts", key = supplyIdx == 2 ? "Missiles" : "Drone parts";
		int have = supply(state, supplyIdx);
		if (have <= 0) { help(save.getPlayerShipName() + " has no " + what + "."); return; }
		int n = Math.min((Integer) moveAmount.getValue(), have);
		if (n == 1) what = supplyIdx == 2 ? "missile" : "drone part";
		int price = sell ? homeplanet.core.Economy.supplySale(n, SUPPLY_PRICE[supplyIdx]) : 0;
		String q = sell ? "Sell " + n + " " + what + " for " + price + " scrap?" : "Junk " + n + " " + what + "?\nYou get nothing for " + (n == 1 ? "it." : "them.");
		if (!HomePlanet.confirmNo(this, q, sell ? "Sell" : "Junk")) return;
		setSupply(state, supplyIdx, have - n);
		if (sell) state.setScrapAmt(state.getScrapAmt() + price);
		disposals.add(new Disposal(save, sell ? "SELL" : "JUNK", key, price, n + " " + what + (sell ? " for " + price + " scrap" : "") + "  (" + save.getPlayerShipName() + ")", n));
		markDirty();
		for (int k = 0; k < 4; k++) { mySupply[k].repaint(); theirSupply[k].repaint(); }
		help((sell ? "Sold " : "Junked ") + n + " " + what + (sell ? " for " + price + " scrap." : "."));
	}
	/** All of the chosen supply, one way or the other. */
	private void moveAllSupply(boolean send) {
		if (currentPath == null) { help(NO_SHIP); return; }
		moveSupply(send, Integer.MAX_VALUE);
	}
	private void moveSupply(boolean send) { moveSupply(send, (Integer) moveAmount.getValue()); }
	private void moveSupply(boolean send, int n) {
		if (currentPath == null) { help(NO_SHIP); return; }
		ShipState from = send ? currentState : tradeState, to = send ? tradeState : currentState;
		int have = supply(from, supplyIdx);
		if (have <= 0) { help((send ? "Your ship has" : partnerName() + " has") + " no " + SUPPLIES[supplyIdx][1].toLowerCase() + " to move."); return; }
		n = Math.min(n, have);
		setSupply(from, supplyIdx, have - n);
		setSupply(to, supplyIdx, supply(to, supplyIdx) + n);
		markDirty();
		for (int k = 0; k < 4; k++) { mySupply[k].repaint(); theirSupply[k].repaint(); }
		help("Moved " + n + " " + SUPPLIES[supplyIdx][1].toLowerCase() + (send ? " to " + partnerName() : " to your ship") + ".");
	}

	// ---- filling the Trade tab from the saves ----

	/** An item in a list: its id and whether it sits in the cargo hold rather than on the ship. */
	static final class ItemRef {
		final String id; final boolean inCargo;
		ItemRef(String id, boolean inCargo) { this.id = id; this.inCargo = inCargo; }
		@Override public boolean equals(Object o) { return o instanceof ItemRef && ((ItemRef) o).id.equals(id) && ((ItemRef) o).inCargo == inCargo; }
		@Override public int hashCode() { return id.hashCode() * 2 + (inCargo ? 1 : 0); }
	}
	private static int kindOf(String id) { return Items.isWeapon(id) ? 0 : Items.isDrone(id) ? 1 : 2; }
	/** One side's rows for a category: fitted items, then cargo ("in cargo"); repeats counted. */
	private List<CargoParts.Row> itemRows(SavedGameState save, ShipState s, int kind) {
		LinkedHashMap<ItemRef, Integer> n = new LinkedHashMap<ItemRef, Integer>();
		List<String> ids = new ArrayList<String>();
		if (kind == 0) for (WeaponState w : s.getWeaponList()) ids.add(w.getWeaponId());
		else if (kind == 1) for (DroneState d : s.getDroneList()) ids.add(d.getDroneId());
		else for (String a : s.getAugmentIdList()) ids.add(a);
		for (String id : ids) { ItemRef r = new ItemRef(id, false); n.put(r, n.containsKey(r) ? n.get(r) + 1 : 1); }
		if (save.getCargoIdList() != null) {
			for (String id : save.getCargoIdList()) {
				if (kindOf(id) != kind) continue;
				ItemRef r = new ItemRef(id, true);
				n.put(r, n.containsKey(r) ? n.get(r) + 1 : 1);
			}
		}
		List<CargoParts.Row> rows = new ArrayList<CargoParts.Row>();
		for (Map.Entry<ItemRef, Integer> e : n.entrySet()) {
			String note = (e.getValue() > 1 ? "x" + e.getValue() : "") + (e.getKey().inCargo ? (e.getValue() > 1 ? "  " : "") + "in cargo" : "");
			rows.add(new CargoParts.Row(IconFactory.itemIcon(e.getKey().id), Items.title(e.getKey().id), note.isEmpty() ? null : note, e.getKey(),
					itemTip(e.getKey().id), false));
		}
		// the storage hold is a warehouse: alphabetical. A ship's fitted items keep FTL's slot order; her cargo hold goes alphabetical
		List<CargoParts.Row> fitted = new ArrayList<CargoParts.Row>(), loose = new ArrayList<CargoParts.Row>();
		boolean storage = save == tradeSave && partnerIsStorage();
		for (CargoParts.Row r : rows) (storage || ((ItemRef) r.value).inCargo ? loose : fitted).add(r);
		Collections.sort(loose, BY_NAME);
		fitted.addAll(loose);
		return fitted;
	}
	/** The item's full stats (as the Shop shows them), or its name if the game data has none. */
	private static String itemTip(String id) {
		String t = ItemTooltips.tooltip(id);
		return t != null ? t : Items.title(id);
	}
	/** Info (or double-click): the item's stats in a window. */
	private void itemInfo(boolean mine, int kind) {
		ItemRef r = (ItemRef) (mine ? cats[kind].mine : cats[kind].theirs).selectedValue();
		if (r == null) return;
		String t = ItemTooltips.tooltip(r.id);
		JOptionPane.showMessageDialog(this, new JLabel(t != null ? t : Items.title(r.id)), Items.title(r.id), JOptionPane.PLAIN_MESSAGE, IconFactory.itemIcon(r.id));
	}
	/** The infirmary's purple (FTL's colour for a crew member not yours to command just now). */
	private static final Color INFIRMARY = new Color(170, 110, 230);
	private static final String INFIRMARY_HTML = "#aa6ee6";
	/** A crew member's health, and what they've lost of it: the green and red of the station's system bars. */
	private static final Color HEALTH = CrewReport.HEALTH, HURT = CrewReport.HURT;
	private static final Comparator<CargoParts.Row> BY_NAME = new Comparator<CargoParts.Row>() {
		public int compare(CargoParts.Row a, CargoParts.Row b) { return a.name.compareToIgnoreCase(b.name); }
	};
	private List<CargoParts.Row> crewRows(ShipState s) {
		List<CargoParts.Row> rows = new ArrayList<CargoParts.Row>();
		boolean hold = s == tradeState && partnerIsStorage();
		java.util.Set<String> laidUp = hold && Vault.isOpen() ? homeplanet.parser.Expeditions.laidUpKeys(Vault.get()) : java.util.Collections.<String>emptySet();
		for (CrewState c : SaveHelper.getOwnCrew(s)) {
			boolean body = SaveHelper.hasBody(c);
			// health, as FTL draws it: no bar when whole; green with the rest red when hurt in the game (a station heals
			// that by the next beacon); purple, full, in the infirmary (laid up, not to be moved or sent)
			boolean resting = homeplanet.parser.Expeditions.isLaidUp(laidUp, c);
			int max = c.getRace() == null ? 100 : c.getRace().getMaxHealth();
			boolean hurt = body && !resting && c.getHealth() < max;
			String state = resting ? "<br><font color='" + INFIRMARY_HTML + "'>In the infirmary: can't be moved, traded or sent until they're on their feet</font>"
					: hurt ? "<br><font color='#e1463c'>" + (s == currentState ? "Injured: the station's medbay will see to them once she's docked"
							: "Injured: the station's medbay will have them on their feet after some time here") + "</font>" : "";
			CargoParts.Row row = new CargoParts.Row(IconFactory.crewIcon(c), c.getName(), body ? Crew.raceTitle(c) : "being cloned", c,
					Crew.tooltip(c).replace("</html>", state + "<br><i>(double-click for her report)</i></html>"), !body);
			if (resting) row.bar(1f, INFIRMARY, null);
			else if (hurt) row.bar(c.getHealth() / (float) max, HEALTH, HURT);
			rows.add(row);
		}
		if (s == tradeState && partnerIsStorage()) Collections.sort(rows, BY_NAME); // the hold's crew by name; a ship's stay in her own order
		return rows;
	}
	static int weaponSlots(ShipState s) {
		ShipBlueprint bp = DataManager.get().getShip(s.getShipBlueprintId());
		return bp != null && bp.getWeaponSlots() != null ? bp.getWeaponSlots() : 4;
	}
	static int droneSlots(ShipState s) {
		if (!SaveHelper.hasSystem(s, SystemType.DRONE_CTRL)) return 0;
		ShipBlueprint bp = DataManager.get().getShip(s.getShipBlueprintId());
		return bp != null && bp.getDroneSlots() != null ? bp.getDroneSlots() : 3;
	}
	private String counts(ShipState s, int kind) {
		if (kind == 0) return s.getWeaponList().size() + "/" + weaponSlots(s);
		if (kind == 1) { int d = droneSlots(s); return d == 0 ? "no drone control" : s.getDroneList().size() + "/" + d; }
		if (kind == 2) return s.getAugmentIdList().size() + "/3";
		return SaveHelper.getOwnCrew(s).size() + "/8";
	}
	private static final String[] CAT = {"Weapons", "Drones", "Augments", "Crew"};

	/** Refills the Trade tab from the two saves in memory. */
	void refreshTrade() {
		if (currentState == null || tradeState == null) return;
		updateSupplyButtons();
		boardBtn.setText(currentPath == null ? "Pick a ship" : currentSave.getPlayerShipName());
		String cls = currentPath == null ? "" : shipClass(currentState); // no ship aboard: the lists say so, and Stored Systems sits here
		mySub.setText(cls);

		myPic.setIcon(shipIcon(currentSave));
		partnerBtn.setText(partnerName());
		partnerNote.setText(shipSelect.isEmpty() ? "Nothing to trade with" : partnerIsStorage() ? "The Cargo Hold has no limit on slots for storage."
				: shipClass(tradeState));
		boolean ready = currentShip != null && homeplanet.vault.Borrowed.of(Vault.get(), currentShip.id) != null && homeplanet.parser.RepairJob.ready(Vault.get(), currentShip);
		if (ready) returnBtn.setText("Return " + currentSave.getPlayerShipName());
		returnBtn.setVisible(ready && !tradeUnavailable());
		theirPic.setToolTipText(partnerIsStorage() || shipSelect.isEmpty() ? null : "Click for her report, and to rename her");
		theirPic.setIcon(partnerIsStorage() ? null : shipIcon(tradeSave));
		theirInfo.setVisible(!shipSelect.isEmpty());
		theirInfo.setToolTipText(partnerIsStorage() ? "What the Cargo Hold is" : "Her report, and to rename her");
		myInfo.setVisible(currentPath != null);
		for (int k = 0; k < 4; k++) {
			Category c = cats[k];
			c.myHead.setText(currentPath == null ? CAT[k] : CAT[k] + "  " + (k == 1 && droneSlots(currentState) == 0 ? "" : counts(currentState, k)));
			c.mine.setEmptyText(currentPath == null ? "No ship picked" : k == 3 ? "No crew" : "None");
			c.theirHead.setText(partnerIsStorage() ? CAT[k] : (k == 1 && droneSlots(tradeState) == 0 ? "" : counts(tradeState, k)) + "  " + CAT[k]);
			if (k < 3) {
				c.mine.setRows(itemRows(currentSave, currentState, k));
				c.theirs.setRows(itemRows(tradeSave, tradeState, k));
			} else {
				c.mine.setRows(crewRows(currentState));
				c.theirs.setRows(crewRows(tradeState));
			}
			c.updateButtons();
		}
		selectSupply(supplyIdx);
		if (currentTab.equals("trade")) help(describeSelection());
		trade.repaint();
	}
	static String shipClass(ShipState s) {
		ShipBlueprint bp = DataManager.get().getShip(s.getShipBlueprintId());
		return bp == null || bp.getShipClass() == null ? "" : String.valueOf(bp.getShipClass());
	}
	javax.swing.Icon shipIcon(SavedGameState s) {
		ShipBlueprint bp = DataManager.get().getShips().get(s.getPlayerShipBlueprintId());
		if (bp == null) bp = DataManager.get().getAutoShips().get(s.getPlayerShipBlueprintId());
		if (bp == null) return null;
		BufferedImage img = parent.getResourceImage("img/ship/" + bp.getGraphicsBaseName() + "_base.png", false);
		return img == null ? null : new ImageIcon(SpaceDockUI.fitImage(img, 120, 62));
	}

	/** The help line for whatever is selected in the Trade tab. */
	private String describeSelection() {
		for (Category c : cats) {
			for (int side = 0; side < 2; side++) {
				Object v = (side == 0 ? c.mine : c.theirs).selectedValue();
				if (v == null) continue;
				String where = side == 0 ? "your ship" : partnerName();
				if (v instanceof CrewState) {
					CrewState cs = (CrewState) v;
					return cs.getName() + " (" + where + "):  " + (side == 0 ? "Send > moves them to " + partnerName() : "< Take brings them aboard your ship")
							+ ".  Retire: they leave for good.  Double-click for their report and to rename.";
				}
				ItemRef r = (ItemRef) v;
				int p = sellPrice(r.id);
				return Items.title(r.id) + " (" + where + (r.inCargo ? ", in cargo" : "") + "): " + (side == 0 ? "Send > gives it to " + partnerName() : "< Take puts it on your ship")
						+ ". Trash junks it." + (p > 0 ? " The scrap button sells it for " + p + " scrap." : " It can't be sold.");
			}
		}
		return "Select something in either list, then use the buttons in the middle. Click a supply to move it. Nothing changes until you Save.";
	}

	// ---- no ship aboard ----

	/** The systems stored in the Cargo Hold, each to sell (the hold is paid on Save, as on the Refit tab). */
	private void storedSystems() {
		while (true) {
			java.util.List<SystemsPanel.Stored> list = systems.storedList();
			if (list.isEmpty()) { help("No systems are stored in the Cargo Hold."); return; }
			String[] names = new String[list.size()];
			for (int i = 0; i < names.length; i++) names[i] = list.get(i) + ": sells for " + SystemsPanel.salePrice(list.get(i)) + " scrap";
			javax.swing.JList<String> l = new javax.swing.JList<String>(names);
			l.setSelectedIndex(0);
			l.setVisibleRowCount(Math.min(10, names.length));
			JPanel p = new JPanel(new java.awt.BorderLayout(0, 8));
			p.add(new JLabel("Systems stored in the Cargo Hold. Selling one pays the hold when you Save."), java.awt.BorderLayout.NORTH);
			p.add(new javax.swing.JScrollPane(l), java.awt.BorderLayout.CENTER);
			Object[] opts = {"Sell", "Close"};
			int r = JOptionPane.showOptionDialog(this, p, "Stored systems", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[1]);
			if (r != 0 || l.getSelectedIndex() < 0) return;
			systems.sell(list.get(l.getSelectedIndex()));
		}
	}

	// ---- the repair job ----

	/** Sends the borrowed ship you're aboard back to her owner (saved changes first: she goes as she was last saved). */
	private void returnHer() {
		if (currentShip == null || !confirmLeave("return her")) return;
		if (!homeplanet.core.GameGuard.allows(this, "return her")) return;
		Ship s = currentShip;
		String title = "Return " + s.name;
		int pay = homeplanet.parser.RepairJob.payment(Vault.get(), homeplanet.parser.RepairJob.late(Vault.get()));
		if (!HomePlanet.confirmNo(this, "Return the " + s.name + " to her owner?\nShe leaves the fleet, and " + pay + " scrap is paid into the Cargo Hold.\n"
				+ "You'll have no ship boarded: board another at the Space Dock.", title)) return;
		try {
			int paid = homeplanet.parser.RepairJob.returnHer(Vault.get(), s);
			JOptionPane.showMessageDialog(this, "The " + s.name + " is on her way home. " + paid + " scrap has been paid into the Cargo Hold.", title, JOptionPane.INFORMATION_MESSAGE);
		} catch (java.io.IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not return her:\n" + e.getMessage());
		}
		init();
	}

	// ---- moving items ----

	/** Moves the selected item across (fromMine: your ship to the partner). The same rules as FTL: slots, Drone Control, the cargo hold. */
	private void sendItem(boolean fromMine, int kind) {
		if (currentPath == null) { help(NO_SHIP); return; }
		Category c = cats[kind];
		ItemRef r = (ItemRef) (fromMine ? c.mine : c.theirs).selectedValue();
		if (r == null) return;
		SavedGameState startSave = fromMine ? currentSave : tradeSave, destSave = fromMine ? tradeSave : currentSave;
		ShipState startState = startSave.getPlayerShip(), destState = destSave.getPlayerShip();
		boolean destIsStorage = fromMine && partnerIsStorage();
		String id = r.id;
		String title = Items.title(id);
		boolean toCargo = false;
		String refused = destIsStorage ? null : Dlc.refusesItem(destSave, id);
		if (refused != null) { JOptionPane.showMessageDialog(this, refused, "Advanced Edition only", JOptionPane.INFORMATION_MESSAGE); return; }
		if (!destIsStorage) {
			int room, used;
			if (kind == 0) { room = weaponSlots(destState); used = destState.getWeaponList().size(); }
			else if (kind == 1) { room = droneSlots(destState); used = destState.getDroneList().size(); }
			else { room = 3; used = destState.getAugmentIdList().size(); }
			if (used >= room) {
				String who = destSave.getPlayerShipName();
				if (kind == 2) { HomePlanet.showErrorDialog(who + "'s augment slots are full (3). Send one of hers away first."); return; }
				if (destSave.getCargoIdList().size() >= 4) { HomePlanet.showErrorDialog(who + " has no room for the " + title + ", and her cargo hold is full too."); return; }
				String q = kind == 1 && room == 0 ? who + " has no Drone Control system. Put the drone in her cargo hold?" : who + " has no free " + (kind == 0 ? "weapon" : "drone") + " slot. Put the " + title + " in her cargo hold?";
				if (JOptionPane.showConfirmDialog(this, q, "Send to cargo?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) return;
				toCargo = true;
			}
		}
		// take it from the sender (releasing its power if it was powered)
		DroneState srcDrone = null;
		if (r.inCargo) {
			if (!startSave.getCargoIdList().remove(id)) { HomePlanet.showErrorDialog("That item is no longer in the cargo hold."); return; }
		} else if (kind == 0) {
			WeaponState w = SaveHelper.findWeapon(startState.getWeaponList(), id);
			if (w == null) { HomePlanet.showErrorDialog("That weapon is no longer aboard."); return; }
			SaveHelper.removeWeapon(startState, w);
		} else if (kind == 1) {
			srcDrone = SaveHelper.findDrone(startState.getDroneList(), id);
			if (srcDrone == null) { HomePlanet.showErrorDialog("That drone is no longer aboard."); return; }
			SaveHelper.removeDrone(startState, srcDrone);
		} else if (!startState.getAugmentIdList().remove(id)) { HomePlanet.showErrorDialog("That augment is no longer aboard."); return; }
		// give it to the receiver, unpowered
		if (toCargo) destSave.getCargoIdList().add(id);
		else if (kind == 0) destState.getWeaponList().add(SaveHelper.newIdleWeapon(id));
		else if (kind == 1) destState.getDroneList().add(srcDrone != null ? SaveHelper.copyDroneForTransfer(srcDrone) : SaveHelper.newIdleDrone(id));
		else destState.getAugmentIdList().add(id);
		log.debug("Sent {} from {} to {}{}", id, startSave.getPlayerShipName(), toCargo ? "cargo of " : "", destSave.getPlayerShipName());
		markDirty();
		refreshTrade();
		help("Sent the " + title + " to " + (fromMine ? partnerName() : "your ship") + (toCargo ? "'s cargo hold" : "") + ".");
	}

	// ---- junk, sell, retire ----

	/** What FTL's stores pay: half the price, rounded down. */
	static int sellPrice(String id) {
		int p = DryDockShop.priceOf(id);
		return p <= 0 ? 0 : p / 2;
	}
	private void dispose(boolean mine, int kind, boolean sell) {
		Category c = cats[kind];
		ItemRef r = (ItemRef) (mine ? c.mine : c.theirs).selectedValue();
		if (r == null) return;
		SavedGameState save = mine ? currentSave : tradeSave;
		ShipState state = save.getPlayerShip();
		String title = Items.title(r.id);
		int price = sell ? sellPrice(r.id) : 0;
		if (sell && price <= 0) { HomePlanet.showErrorDialog(title + " has no price, so no one will buy it."); return; }
		String q = sell ? "Sell the " + title + " for " + price + " scrap?" : "Junk the " + title + "?\nYou get nothing for it.";
		if (!HomePlanet.confirmNo(this, q, sell ? "Sell" : "Junk")) return;
		if (r.inCargo) {
			if (!save.getCargoIdList().remove(r.id)) { HomePlanet.showErrorDialog("That item is no longer in the cargo hold."); return; }
		} else if (kind == 0) {
			WeaponState w = SaveHelper.findWeapon(state.getWeaponList(), r.id);
			if (w == null) { HomePlanet.showErrorDialog("That weapon is no longer aboard."); return; }
			SaveHelper.removeWeapon(state, w);
		} else if (kind == 1) {
			DroneState d = SaveHelper.findDrone(state.getDroneList(), r.id);
			if (d == null) { HomePlanet.showErrorDialog("That drone is no longer aboard."); return; }
			SaveHelper.removeDrone(state, d);
		} else if (!state.getAugmentIdList().remove(r.id)) { HomePlanet.showErrorDialog("That augment is no longer aboard."); return; }
		if (sell) state.setScrapAmt(state.getScrapAmt() + price);
		String from = save.getPlayerShipName() + (r.inCargo ? " (cargo)" : "");
		disposals.add(new Disposal(save, sell ? "SELL" : "JUNK", title + (r.inCargo ? " (cargo)" : ""), price,
				title + (sell ? " for " + price + " scrap" : "") + "  (" + from + ")"));
		log.debug("{} {} from {}", sell ? "Sold" : "Junked", r.id, from);
		markDirty();
		refreshTrade();
		help(sell ? "Sold the " + title + " for " + price + " scrap." : "Junked the " + title + ".");
	}
	private void retire(boolean mine) {
		Category c = cats[3];
		CrewState cs = (CrewState) (mine ? c.mine : c.theirs).selectedValue();
		if (cs == null) return;
		ShipState state = mine ? currentState : tradeState;
		SavedGameState save = mine ? currentSave : tradeSave;
		if (!SaveHelper.hasBody(cs)) { HomePlanet.showErrorDialog(cs.getName() + " is waiting to be cloned and can't retire right now."); return; }
		if (!HomePlanet.confirmNo(this, "Retire " + cs.getName() + "?\nThey leave " + save.getPlayerShipName() + " for good.", "Retire")) return;
		state.getCrewList().remove(cs);
		String diskName = crewRenames.containsKey(cs) ? crewRenames.remove(cs) : cs.getName();
		disposals.add(new Disposal(save, "RETIRE", "Crew " + diskName, 0, cs.getName() + " (" + Crew.raceTitle(cs) + ")  (" + save.getPlayerShipName() + ")"));
		log.debug("Retired {} from {}", cs.getName(), save.getPlayerShipName());
		markDirty();
		refreshTrade();
		help(cs.getName() + " has retired.");
	}

	// ---- crew ----

	private void sendCrew(boolean fromMine) {
		if (currentPath == null) { help(NO_SHIP); return; }
		Category c = cats[3];
		CrewState cs = (CrewState) (fromMine ? c.mine : c.theirs).selectedValue();
		if (cs == null) return;
		ShipState startState = fromMine ? currentState : tradeState, destState = fromMine ? tradeState : currentState;
		// a ship may be left with no one aboard (fixing up a derelict, say): FTL just won't launch her until someone is (HomePlanet.noOneAboard)
		boolean destIsStorage = fromMine && partnerIsStorage();
		if (!destIsStorage && SaveHelper.getOwnCrew(destState).size() >= 8) { HomePlanet.showErrorDialog("No room for more crew: a ship carries 8 at most."); return; }
		if (!SaveHelper.hasBody(cs)) { HomePlanet.showErrorDialog(cs.getName() + " is waiting to be cloned and can't be moved right now."); return; }
		if (!fromMine && partnerIsStorage() && Vault.isOpen() && homeplanet.parser.Expeditions.laidUp(Vault.get(), cs)) {
			JOptionPane.showMessageDialog(this, cs.getName() + " is in the infirmary, and stays there until they're on their feet.", "Infirmary", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String refused = destIsStorage ? null : Dlc.refusesCrew(fromMine ? tradeSave : currentSave, cs);
		if (refused != null) { JOptionPane.showMessageDialog(this, refused, "Advanced Edition only", JOptionPane.INFORMATION_MESSAGE); return; }
		// their room and square referred to the old ship: stand them on a free square of the new one
		if (!SaveHelper.placeCrew(destState, cs, destIsStorage)) { HomePlanet.showErrorDialog("That ship has no free floor space for more crew."); return; }
		startState.getCrewList().remove(cs);
		destState.getCrewList().add(cs);
		log.debug("Sent crew {} to {}", cs.getName(), destState.getShipName());
		markDirty();
		refreshTrade();
		help(cs.getName() + (fromMine ? " went to " + partnerName() + "." : " came aboard your ship."));
	}
	/** One history line for each crew member on this side now who wasn't before the save. */
	private static void assigned(ShipState now, Map<String, Integer> before, SavedGameState save, boolean hold) {
		if (now == null || before == null) return;
		Map<String, Integer> seen = new HashMap<String, Integer>();
		for (CrewState c : SaveHelper.getOwnCrew(now)) {
			int n = seen.containsKey(c.getName()) ? seen.get(c.getName()) + 1 : 1;
			seen.put(c.getName(), n);
			Integer had = before.get("Crew " + c.getName());
			if (had != null && n <= had) continue;
			String ship = save.getPlayerShipName();
			String place = hold ? "the Cargo Hold" : ship.startsWith("The ") ? ship : "the " + ship;
			homeplanet.core.HistoryLog.entry("CREW", c.getName() + " assigned to " + place + ".");
		}
	}
	private void crewInfo(boolean mine) {
		Category c = cats[3];
		CrewState cs = (CrewState) (mine ? c.mine : c.theirs).selectedValue();
		if (cs == null) return;
		// the infirmary knows them by name: no new one until they're out
		boolean resting = !mine && partnerIsStorage() && Vault.isOpen() && homeplanet.parser.Expeditions.laidUp(Vault.get(), cs);
		Object[] options = resting ? new Object[] {"OK"} : new Object[] {"OK", "Rename"};
		int choice = CrewReport.show(this, cs, resting, options);
		if (choice != 1) return;
		String newName = SpaceDockUI.promptForName("Enter a new name for " + cs.getName() + ":", "Rename Crew", cs.getName());
		if (newName == null || newName.equals(cs.getName())) return;
		if (!crewRenames.containsKey(cs)) crewRenames.put(cs, cs.getName()); // remember the saved name
		cs.setName(newName);
		markDirty();
		refreshTrade();
		help("The roster now lists " + newName + ". Save to make it official.");
	}

	/** Ship Info for the boarded ship as it stands in the Cargo Bay; Rename takes effect on Save. */
	void showCurrentShipInfo() {
		if (currentPath == null || currentSave == null || parent.spaceDock == null) return;
		if (!parent.spaceDock.showReport(currentSave)) return;
		String oldName = currentSave.getPlayerShipName();
		String newName = SpaceDockUI.promptForName("Enter a new name for the ship:", "Rename Ship", oldName);
		if (newName == null || newName.equals(oldName)) return;
		currentSave.setPlayerShipName(newName);
		currentSave.getPlayerShip().setShipName(newName);
		markDirty();
		refreshTrade();
		systems.refresh();
		help(oldName + " will be known as " + newName + ". Save to make it official.");
	}

	/** Ship Info for the trading partner; Rename takes effect on Save, as for the boarded ship. */
	void showPartnerInfo() {
		if (tradePath == null || partnerIsStorage() || parent.spaceDock == null) return;
		if (!parent.spaceDock.showReport(tradeSave)) return;
		String oldName = tradeSave.getPlayerShipName();
		String newName = SpaceDockUI.promptForName("Enter a new name for the ship:", "Rename Ship", oldName);
		if (newName == null || newName.equals(oldName)) return;
		tradeSave.setPlayerShipName(newName);
		tradeSave.getPlayerShip().setShipName(newName);
		markDirty();
		refreshTrade();
		help(oldName + " will be known as " + newName + ". Save to make it official.");
	}

	/** A Dry Dock purchase changed a save in memory: show it. */
	void showPurchase(SavedGameState buyer, String id, boolean toCargo) {
		markDirty();
		refreshTrade();
	}

	// ============================================================== Save

	/** Writes every pending change (ship, trade partner, shop, systems, and the history log entries); true if all of it was written (false after telling the player why not). */
	public boolean saveAll() {
		boolean holdAlone = currentShip == null && holdOnly(); // no ship picked: only the Cargo Hold (and the stored systems) change
		if ((currentPath == null || currentShip == null) && !holdAlone) {
			HomePlanet.showErrorDialog("Nothing to save: no ship is picked. Pick one with the button above, or board one at the Space Dock.");
			return false;
		}
		if (currentShip != null && currentShip.isBoarded() && !homeplanet.core.GameGuard.allows(this, "save the Cargo Bay")) return false;
		int billed = 0; // taken from the Cargo Hold in memory (it's the partner): given back if the save fails
		try {
			Map<String, Integer> curBefore = null, tradeBefore = null;
			String nameBefore = null, tradeNameBefore = null;
			try {
				// the ships as their files have them: the vault's own parse (a fresh read could fail after a remodel changed her layout)
				SavedGameState onDisk = currentShip == null ? null : currentShip.save();
				if (onDisk != null) { nameBefore = onDisk.getPlayerShipName(); curBefore = homeplanet.core.HistoryLog.inventory(onDisk); }
				SavedGameState tradeDisk = tradeShip != null && tradePath != null ? tradeShip.save() : null;
				if (tradeDisk != null) { tradeNameBefore = tradeDisk.getPlayerShipName(); tradeBefore = homeplanet.core.HistoryLog.inventory(tradeDisk); }
			} catch (Exception e) {
				log.warn("Could not read saves for the history log", e);
			}
			shop.countPurchasesAsBefore(curBefore, currentSave); // purchases get their own BUY entry
			shop.countPurchasesAsBefore(tradeBefore, tradeSave);
			// every file together, or none: the ships, the storage, the shops bought from, the stored-systems list
			Vault.Transaction tx = Vault.get().begin();
			if (currentShip != null) tx.put(currentShip, currentSave, currentHash);
			if (tradeShip != null && tradePath != null) tx.put(tradeShip, tradeSave, tradeHash);
			shop.addTo(tx);
			systems.addTo(tx);
			billed = systems.payBill(tx); // the Dry Dock's work, from the Cargo Hold, in the same save
			tx.commit();
			systems.spendRepBill(); // and the reputation it took, once the save stands
			// real business at the station passes a day (heromedel, 5.17): buying, selling, the Dry Dock's work, a system in
			// or out; never moving your own things about. Not twice running: a Save after a Cargo Bay day, with nothing else
			// moving the clock between, passes none (selling one missile at a time can't run the clock)
			boolean business = !systems.changes().isEmpty() || !shop.purchases().isEmpty();
			for (Disposal dp : disposals) if ("SELL".equals(dp.kind) && (dp.save == currentSave || dp.save == tradeSave)) business = true;
			if (business) homeplanet.vault.MasterLog.businessDay(Vault.get());
			if (!systems.changes().isEmpty())
				homeplanet.core.HistoryLog.entry("SYSTEMS", currentSave.getPlayerShipName(), new ArrayList<String>(systems.changes()));
			for (String c : systems.changes()) if (c.startsWith("Installed ")) { homeplanet.parser.ThirdFleet.partInstalled(Vault.get()); break; } // the technicians tell the Third Fleet Commander
			if (!shop.purchases().isEmpty())
				homeplanet.core.HistoryLog.entry("BUY", shop.purchases().size() == 1 ? "1 purchase" : shop.purchases().size() + " purchases",
						new ArrayList<String>(shop.purchases()));
			if (currentShip != null && nameBefore != null && !nameBefore.equals(currentSave.getPlayerShipName()))
				homeplanet.core.HistoryLog.entry("RENAME", nameBefore + " -> " + currentSave.getPlayerShipName() + "  (" + currentShip.id + ")");
			if (tradeNameBefore != null && !partnerIsStorage() && !tradeNameBefore.equals(tradeSave.getPlayerShipName()))
				homeplanet.core.HistoryLog.entry("RENAME", tradeNameBefore + " -> " + tradeSave.getPlayerShipName() + "  (" + tradeShip.id + ")");
			// crew renames get their own lines, not a "left / joined" pair in the trade
			for (Map.Entry<CrewState, String> r : crewRenames.entrySet()) {
				String oldN = r.getValue(), newN = r.getKey().getName();
				if (oldN.equals(newN)) continue;
				String ship = currentState.getCrewList().contains(r.getKey()) ? currentSave.getPlayerShipName() : (tradeSave != null ? tradeSave.getPlayerShipName() : "");
				for (Map<String, Integer> m : java.util.Arrays.asList(curBefore, tradeBefore)) {
					if (m != null && m.containsKey("Crew " + oldN)) {
						int n = m.remove("Crew " + oldN);
						m.put("Crew " + newN, (m.containsKey("Crew " + newN) ? m.get("Crew " + newN) : 0) + n);
					}
				}
				homeplanet.core.HistoryLog.entry("RENAME CREW", oldN + " -> " + newN + "  (" + ship + ")");
			}
			// crew who came aboard a ship or into the Cargo Hold: "Lisandra assigned to the Kestrel." (their arrival at the
			// station's medbay counts from here)
			assigned(currentShip != null ? currentState : null, curBefore, currentSave, false);
			if (tradeShip != null && tradePath != null) assigned(tradeState, tradeBefore, tradeSave, partnerIsStorage());
			// junked, sold and retired get entries of their own, not lines in the trade
			Map<String, List<String>> byKind = new LinkedHashMap<String, List<String>>();
			Map<String, Integer> countByKind = new LinkedHashMap<String, Integer>();
			int sellTotal = 0;
			for (Disposal dp : disposals) {
				if (dp.save != currentSave && dp.save != tradeSave) continue;
				Map<String, Integer> m = dp.save == currentSave ? curBefore : tradeBefore;
				if (m != null) {
					Integer n = m.get(dp.invKey);
					if (n != null) { if (n <= dp.amount) m.remove(dp.invKey); else m.put(dp.invKey, n - dp.amount); }
					if (dp.scrap > 0) m.put("Scrap", (m.containsKey("Scrap") ? m.get("Scrap") : 0) + dp.scrap);
				}
				sellTotal += dp.scrap;
				if (!byKind.containsKey(dp.kind)) byKind.put(dp.kind, new ArrayList<String>());
				byKind.get(dp.kind).add(dp.line);
				countByKind.put(dp.kind, (countByKind.containsKey(dp.kind) ? countByKind.get(dp.kind) : 0) + dp.amount);
			}
			for (Map.Entry<String, List<String>> k : byKind.entrySet()) {
				int n = countByKind.get(k.getKey());
				String head = k.getKey().equals("RETIRE") ? (n == 1 ? "1 crew member" : n + " crew members") : (n == 1 ? "1 item" : n + " items");
				if (k.getKey().equals("SELL")) head += " for " + sellTotal + " scrap";
				homeplanet.core.HistoryLog.entry(k.getKey(), head, k.getValue());
			}
			List<String> lines = new ArrayList<String>();
			if (curBefore != null) {
				List<String> c = homeplanet.core.HistoryLog.changes(curBefore, homeplanet.core.HistoryLog.inventory(currentSave));
				if (!c.isEmpty()) { lines.add(currentSave.getPlayerShipName() + ":"); for (String l : c) lines.add("  " + l); }
			}
			if (tradeBefore != null) {
				List<String> c = homeplanet.core.HistoryLog.changes(tradeBefore, homeplanet.core.HistoryLog.inventory(tradeSave));
				if (!c.isEmpty()) { lines.add(tradeSave.getPlayerShipName() + ":"); for (String l : c) lines.add("  " + l); }
			}
			if (!lines.isEmpty())
				homeplanet.core.HistoryLog.entry("TRADE", currentShip == null ? tradeSave.getPlayerShipName() : currentSave.getPlayerShipName() + (tradeSave != null ? " <-> " + tradeSave.getPlayerShipName() : ""), lines);
		} catch (Vault.StaleException e) {
			if (billed != 0) tradeState.setScrapAmt(tradeState.getScrapAmt() + billed);
			log.warn("Save refused: {}", e.getMessage());
			HomePlanet.showErrorDialog(e.getMessage() + "\n\nPress Reset to load her as she is now, then make the changes again.");
			return false;
		} catch (Exception e) {
			if (billed != 0) tradeState.setScrapAmt(tradeState.getScrapAmt() + billed);
			log.error("Saving failed", e);
			HomePlanet.showErrorDialog("The Home Planet Station could not save the changes:\n" + e);
			return false;
		}
		init(); // everything fresh from the files, so what's shown is what's saved
		help("Saved.");
		return true;
	}
}
