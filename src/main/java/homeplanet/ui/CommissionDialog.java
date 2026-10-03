package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import net.blerf.ftl.constants.Difficulty;
import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.core.HomePlanet;
import homeplanet.core.HistoryLog;
import homeplanet.parser.Commission;
import homeplanet.parser.CompanionMod;
import homeplanet.parser.SaveHelper;

/**
 * Commission Ship: choose a blueprint, name her, pick a difficulty; the station builds a brand-new ship save
 * (as a new game would start her) and docks her at the Space Dock.
 */
public class CommissionDialog extends JDialog {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CommissionDialog.class);
	/** A row in the list: a header, or a blueprint. */
	private static class Entry {
		final String id, label;
		Entry(String id, String label) { this.id = id; this.label = label; }
		boolean header() { return id == null; }
		public String toString() { return label; }
	}

	private final SpaceDockUI dock;
	private final DefaultListModel<Entry> model = new DefaultListModel<Entry>();
	private final JList<Entry> list = new JList<Entry>(model);
	private final JPanel preview = new JPanel(new BorderLayout());
	private final JTextField nameField = new JTextField(18);
	private final JComboBox<String> difficulty = new JComboBox<String>(new String[] {"Easy", "Normal", "Hard"});
	/** Advanced Edition content on (the default, as the station has always made ships) or off: an Original run. */
	private final AeSwitch aeSwitch = new AeSwitch();
	private final Random rng = new Random();
	/** Her starting crew as previewed (names and looks): the crew she's built with. Rolled when a ship is chosen. */
	private List<net.blerf.ftl.parser.SavedGameParser.CrewState> crew = null;
	private String crewFor = null;
	/** HR2: her price, under the name and difficulty (hidden when commissioning is free). */
	private final JLabel priceLabel = new JLabel(" ");
	private homeplanet.vault.Ship made = null;
	/** The relief ship's row (not a blueprint of its own: a Kestrel A, stripped). */
	private static final String RELIEF = "RELIEF";
	/** What the list shows, under the rules in force (the Space Dock's backdrop draws from it too). */
	private final Listing listing = new Listing(HomePlanet.commissionCosts() && homeplanet.vault.Vault.get().shipyardEmpty() && homeplanet.vault.Vault.get().freeCommandOpen());

	/** Opens the window. Returns the new ship (docked in the vault), or null if nothing was commissioned. */
	public static homeplanet.vault.Ship open(SpaceDockUI dock) {
		CommissionDialog d = new CommissionDialog(dock);
		d.setVisible(true);
		return d.made;
	}

	private CommissionDialog(SpaceDockUI dock) {
		super(SwingUtilities.getWindowAncestor(dock), "Commission Ship", ModalityType.APPLICATION_MODAL);
		this.dock = dock;
		for (Entry e : listing.rows) model.addElement(e);

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
				super.getListCellRendererComponent(l, v, i, sel && !((Entry) v).header(), focus);
				Entry e = (Entry) v;
				if (e.header()) {
					setFont(getFont().deriveFont(java.awt.Font.BOLD));
					setBorder(BorderFactory.createEmptyBorder(6, 2, 2, 2));
				} else {
					setBorder(BorderFactory.createEmptyBorder(1, 14, 1, 2));
					String why = rankReason(e.id); // Immersive Mode: why it's not cleared, and how to be
					setToolTipText(why == null ? null : "<html>" + homeplanet.parser.XmlText.text(why).replace("\n", "<br>") + "</html>");
				}
				return this;
			}
		});
		list.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
			public void valueChanged(javax.swing.event.ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) return;
				Entry sel = list.getSelectedValue();
				if (sel == null) return;
				if (sel.header()) { list.setSelectedIndex(Math.min(list.getSelectedIndex() + 1, model.size() - 1)); return; }
				showPreview(sel);
			}
		});
		JScrollPane sp = new JScrollPane(list);
		sp.setPreferredSize(new Dimension(300, 460));

		JPanel form = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 4, 2, 4);
		c.anchor = GridBagConstraints.WEST;
		form.add(new JLabel("Ship name:"), c);
		c.gridx = 1;
		JPanel named = new JPanel(new java.awt.BorderLayout(2, 0));
		named.add(nameField, java.awt.BorderLayout.CENTER);
		named.add(DiceIcon.button("A new name for her", new Runnable() { public void run() { rollShipName(); } }), java.awt.BorderLayout.EAST);
		form.add(named, c);
		c.gridx = 2;
		form.add(new JLabel("  Difficulty:"), c);
		c.gridx = 3;
		difficulty.setToolTipText("How dangerous her first journey will be");
		difficulty.setSelectedIndex(1); // Normal, as FTL starts
		form.add(difficulty, c);
		c.gridx = 4;
		form.add(new JLabel("  Content:"), c);
		c.gridx = 5;
		form.add(aeSwitch, c);

		JPanel right = new JPanel(new BorderLayout(0, 6));
		preview.setPreferredSize(new Dimension(520, 440));
		right.add(new JScrollPane(preview), BorderLayout.CENTER);
		if (HomePlanet.commissionCosts()) {
			JPanel south = new JPanel(new BorderLayout(0, 4));
			south.add(form, BorderLayout.NORTH);
			priceLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
			south.add(priceLabel, BorderLayout.SOUTH);
			right.add(south, BorderLayout.SOUTH);
		} else {
			right.add(form, BorderLayout.SOUTH);
		}

		JPanel body = new JPanel(new BorderLayout(10, 8));
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		JLabel intro = new JLabel("<html>Choose a ship to commission. The Home Planet Station builds her to FTL specifications: first sector, "
				+ "starting crew, weapons and supplies. She will wait at the Space Dock.</html>");
		if (listing.listNote != null) {
			JPanel top = new JPanel(new BorderLayout(0, 4));
			top.add(intro, BorderLayout.NORTH);
			JLabel note = new JLabel(listing.listNote);
			note.setForeground(new Color(255, 170, 90));
			top.add(note, BorderLayout.SOUTH);
			body.add(top, BorderLayout.NORTH);
		} else {
			body.add(intro, BorderLayout.NORTH);
		}
		body.add(sp, BorderLayout.WEST);
		body.add(right, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new BorderLayout());
		JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		if (!listing.locked.isEmpty()) {
			JButton lockedBtn = new JButton("Locked ships...");
			lockedBtn.setToolTipText("The ships not yet unlocked in your FTL profile, and how FTL unlocks each");
			lockedBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { LockedShipsDialog.open(CommissionDialog.this, listing.locked); } });
			JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
			leftButtons.add(lockedBtn);
			buttons.add(leftButtons, BorderLayout.WEST);
		}
		buttons.add(rightButtons, BorderLayout.EAST);
		JButton ok = new JButton("Commission");
		JButton cancel = new JButton("Cancel");
		ok.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { commission(); } });
		cancel.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		rightButtons.add(ok);
		rightButtons.add(cancel);
		getRootPane().setDefaultButton(ok);

		getContentPane().add(body, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setLocationRelativeTo(dock);
		for (int i = 0; i < model.size(); i++) if (!model.get(i).header()) { list.setSelectedIndex(i); break; }
	}

	/** The list's rows under the rules in force: what Commission offers, why some are hidden, and which are free. */
	static final class Listing {
		/** HR2: the free command is waiting (granted once when the fleet starts, and with each report for reassignment) and no ship is here. */
		final boolean emptyYard;
		/** HR2 with the unlock-once rule: standard layouts unlocked in FTL since the rule was turned on, not yet claimed. */
		final java.util.Set<String> unlockFree = new java.util.HashSet<String>();
		/** Immersive Mode: the player's rank (custom ships need a Captain, artillery on them a Commodore); -1 outside it. */
		int rank = -1;
		/** Why some ships are missing from the list (rules, or an unreadable profile), or null. */
		String listNote = null;
		/** The standard layouts the profile hasn't unlocked (hidden from the list; Locked ships... shows them). */
		final List<LockedShipsDialog.Locked> locked = new ArrayList<LockedShipsDialog.Locked>();
		/** Headers and blueprints, in order. */
		final List<Entry> rows = new ArrayList<Entry>();


		/** Vanilla ships by model (A, B, C), then the station's own blueprints flagged as starter ships; the unlock rules may hide some. */
		Listing(boolean emptyYard) {
			this.emptyYard = emptyYard;
			String[] letters = {"A", "B", "C"};
			boolean lockRule = HomePlanet.commissionUnlockedOnly();
			boolean customRule = lockRule && HomePlanet.commissionCustomUnlockedOnly();
			homeplanet.parser.Unlocks unlocks = lockRule ? homeplanet.parser.Unlocks.read() : null;
			if (unlocks != null && unlocks.missing() && lockRule) {
				listNote = "FTL hasn't made its profile yet (it does the first time it starts): only the Kestrel Type A is unlocked.";
			}
			if (unlocks != null && unlocks.problem() != null) {
				listNote = unlocks.problem() + " Every ship is shown.";
				unlocks = null;
			}
			int hidden = 0;
			if (HomePlanet.immersiveMode) rank = homeplanet.parser.UnlockGrants.rank(unlocks != null ? unlocks : homeplanet.parser.Unlocks.read());
			if (HomePlanet.commissionCosts() && HomePlanet.unlockFreeShips()) {
				homeplanet.parser.Unlocks u = unlocks != null ? unlocks : homeplanet.parser.Unlocks.read();
				for (String base : DataManager.get().getPlayerShipBaseIds(true)) {
					for (int n = 0; n < 3; n++) {
						ShipBlueprint bp;
						try { bp = DataManager.get().getPlayerShipVariant(base, n, true); } catch (Exception e) { bp = null; }
						if (bp != null && homeplanet.parser.UnlockGrants.freeNow(u, bp.getId())) unlockFree.add(bp.getId());
					}
				}
			}
			if (emptyYard && homeplanet.parser.FreeCommand.RELIEF.equals(homeplanet.parser.FreeCommand.ship())) {
				rows.add(new Entry(null, "Relief"));
				rows.add(new Entry(RELIEF, "Federation relief ship (free)"));
			}
			List<String> bases = DataManager.get().getPlayerShipBaseIds(true);
			rows.add(new Entry(null, "Standard ships"));
			for (String base : bases) {
				for (int n = 0; n < 3; n++) {
					ShipBlueprint bp;
					try { bp = DataManager.get().getPlayerShipVariant(base, n, true); } catch (Exception e) { bp = null; }
					if (bp == null) continue;
					if (unlocks != null && !unlocks.unlocked(base, n)) { hidden++; locked.add(new LockedShipsDialog.Locked(base, bp, n)); continue; }
					String id = bp.getId();
					rows.add(new Entry(id, classOf(bp) + " " + letters[n] + (free(id) ? " (free)" : "")));
				}
			}
			List<Entry> custom = new ArrayList<Entry>();
			for (CompanionMod.Remodel r : CompanionMod.load()) {
				if (!r.starter) continue; // only blueprints made starter ships can be commissioned
				if (!CompanionMod.inGameData(r.id)) continue; // she couldn't fly yet
				ShipBlueprint bp = DataManager.get().getShip(r.id);
				if (bp == null) continue;
				if (customRule && unlocks != null && !unlocks.unlockedBlueprint(r.base)) { hidden++; continue; }
				boolean named = r.loadout != null && r.loadout.className.length() > 0;
				custom.add(new Entry(r.id, (named ? r.loadout.className : classOf(bp) + " " + CompanionMod.numberOf(r.id)) + " (" + r.ship + "'s layout)" + rankNote(r.id)));
			}
			for (homeplanet.parser.ShipDesign d : homeplanet.parser.DesignExport.built()) {
				if (!d.starter || d.frozenOf != null || d.retired) continue; // kept old versions and retired designs only fly for the ships already built from them
				String id = homeplanet.parser.DesignExport.bpId(d);
				if (!CompanionMod.inGameData(id)) continue; // not patched in yet
				ShipBlueprint bp = DataManager.get().getShip(id);
				if (bp == null) continue;
				custom.add(new Entry(id, classOf(bp) + " (designed: " + d.name + (d.version > 1 ? " v" + d.version : "") + ")" + rankNote(id)));
			}
			if (!custom.isEmpty()) {
				rows.add(new Entry(null, "Your blueprints"));
				rows.addAll(custom);
			}
			if (hidden > 0 && listNote == null) {
				listNote = (hidden == 1 ? "1 ship is" : hidden + " ships are") + " not shown: locked in your FTL profile (see Settings, Rules).";
			}
		}

		/** HR2 with an empty shipyard: is this row the free ship? (The unlock and hiding rules still apply to it.) */
		boolean free(String id) {
			if (unlockFree.contains(id)) return true;
			return emptyFree(id);
		}
		boolean emptyFree(String id) {
			if (!emptyYard) return false;
			String free = homeplanet.parser.FreeCommand.ship(); // Settings', or what an Immersive career or report earned
			if (homeplanet.parser.FreeCommand.ANY.equals(free)) return true;
			if (homeplanet.parser.FreeCommand.RELIEF.equals(free)) return RELIEF.equals(id);
			return homeplanet.parser.Commission.RELIEF_BASE.equals(id); // the Kestrel A
		}
		/** Why the player's rank doesn't clear this blueprint (Immersive Mode), or null. */
		String rankReason(String bpId) {
			return rank < 0 ? null : homeplanet.parser.Clearance.commissionReason(bpId);
		}
		String rankNote(String bpId) {
			if (rankReason(bpId) == null) return "";
			if (homeplanet.parser.Clearance.customReason() != null) return " (Captains only)";
			String w = Commission.artilleryWeapon(bpId);
			return w != null && w.startsWith("ARTILLERY_FED") ? " (Commodores only)" : " (needs Rule Ten: Greed is Eternal)";
		}
	}

	/** The blueprints Commission would list right now (the unlock rules applied; no relief row), for the Space Dock's backdrop. */
	static List<String> shownBlueprintIds() {
		List<String> ids = new ArrayList<String>();
		for (Entry e : new Listing(false).rows) if (!e.header() && !RELIEF.equals(e.id)) ids.add(e.id);
		return ids;
	}

	private boolean free(String id) { return listing.free(id); }
	private boolean emptyFree(String id) { return listing.emptyFree(id); }
	private String rankReason(String bpId) { return listing.rankReason(bpId); }

	/** Builds the ship a row stands for. */
	private static SavedGameState make(String id, String name, Difficulty d, Random rng) {
		return RELIEF.equals(id) ? Commission.buildRelief(name, d, rng) : Commission.build(id, name, d, rng);
	}

	static String classOf(ShipBlueprint bp) {
		try {
			String t = bp.getShipClass() == null ? null : bp.getShipClass().getTextValue();
			if (t != null && t.length() > 0) return t;
		} catch (Exception e) { }
		return bp.getId();
	}
	static String defaultName(ShipBlueprint bp) {
		try {
			String t = bp.getName() == null ? null : bp.getName().getTextValue();
			if (t != null && t.length() > 0) return t;
		} catch (Exception e) { }
		return classOf(bp);
	}

	private Difficulty chosenDifficulty() {
		int i = difficulty.getSelectedIndex();
		return i == 1 ? Difficulty.NORMAL : i == 2 ? Difficulty.HARD : Difficulty.EASY;
	}

	/** The same report the Info button shows, for the ship as she'd be commissioned. */
	private void showPreview(Entry e) {
		ShipBlueprint bp = DataManager.get().getShip(RELIEF.equals(e.id) ? Commission.RELIEF_BASE : e.id);
		boolean another = !e.id.equals(previewFor);
		if (another) nameField.setText(RELIEF.equals(e.id) ? "Federation Relief" : defaultName(bp)); // a new crew roll keeps the name typed
		previewFor = e.id;
		preview.removeAll();
		try {
			SavedGameState s = make(e.id, nameField.getText(), Difficulty.EASY, new Random(0));
			if (e.id.equals(crewFor)) Commission.sameCrew(s.getPlayerShip(), crew);
			else { s = make(e.id, nameField.getText(), Difficulty.EASY, rng); crew = SaveHelper.getOwnCrew(s.getPlayerShip()); crewFor = e.id; }
			aeSwitch.lock(homeplanet.parser.Dlc.needsAE(RELIEF.equals(e.id) ? Commission.RELIEF_BASE : e.id, s));
			final Entry shown = e;
			final List<net.blerf.ftl.parser.SavedGameParser.CrewState> aboard = SaveHelper.getOwnCrew(s.getPlayerShip());
			JPanel p = dock.shipSummaryPanel(s, new java.util.function.Consumer<net.blerf.ftl.parser.SavedGameParser.CrewState>() {
				public void accept(net.blerf.ftl.parser.SavedGameParser.CrewState c) { renameCrew(shown, aboard.indexOf(c), c); }
			}, new Runnable() { public void run() { crewFor = null; showPreview(shown); } });
			JLabel stats = new JLabel("<html>" + classOf(bp) + ": hull " + bp.getHealth().amount + ", reactor "
					+ s.getPlayerShip().getReservePowerCapacity() + ", " + (bp.getWeaponSlots() == null ? 4 : bp.getWeaponSlots())
					+ " weapon slots, " + (bp.getDroneSlots() == null ? 3 : bp.getDroneSlots()) + " drone slots</html>");
			stats.setBorder(BorderFactory.createEmptyBorder(4, 6, 8, 6));
			preview.add(stats, BorderLayout.NORTH);
			preview.add(p, BorderLayout.CENTER);
			if (HomePlanet.commissionCosts()) {
				if (emptyFree(e.id)) priceLabel.setText("<html><b>Free.</b> The Federation Home Planet grants you a new command at no cost (once; a report for reassignment grants another).</html>");
				else if (free(e.id)) priceLabel.setText("<html><b>Free, once.</b> Newly unlocked in FTL: The Federation Home Planet commissions the first of her line at no cost.</html>");
				else showPrice(quote(e.id, s));
			}
		} catch (Exception ex) {
			preview.add(new JLabel("The shipyard can't build this ship: " + ex.getMessage()), BorderLayout.NORTH);
		}
		preview.revalidate();
		preview.repaint();
	}

	private String previewFor = null;
	/** Names the die has rolled in this window (not rolled again until they run out). */
	private final java.util.Set<String> rolled = new java.util.HashSet<String>();
	/** The die beside the ship name: a name for her model, unlike any ship's in the fleet (or the one shown). */
	private void rollShipName() {
		Entry e = list.getSelectedValue();
		if (e == null || e.header()) return;
		List<String> taken = new ArrayList<String>();
		for (homeplanet.vault.Ship s : homeplanet.vault.Vault.get().all()) taken.add(s.name);
		taken.add(nameField.getText());
		String id = RELIEF.equals(e.id) ? Commission.RELIEF_BASE : e.id;
		List<String> all = new ArrayList<String>(taken);
		all.addAll(rolled); // no repeats in this window...
		String n = homeplanet.parser.ShipNames.roll(id, all, rng);
		if (n == null) { rolled.clear(); n = homeplanet.parser.ShipNames.roll(id, taken, rng); } // ...until they run out
		if (n == null) return;
		rolled.add(n);
		nameField.setText(n);
	}
	/** Clicking a crew member's name in the preview: her name in the crew she'll be built with. */
	private void renameCrew(Entry e, int i, net.blerf.ftl.parser.SavedGameParser.CrewState shown) {
		if (crew == null || i < 0 || i >= crew.size()) return;
		String name = SpaceDockUI.promptForName("New name for " + shown.getName() + " (" + homeplanet.model.Crew.raceTitle(shown) + "):", "Rename Crew", shown.getName());
		if (name == null) return;
		crew.get(i).setName(name);
		showPreview(e);
	}

	/** HR2: her price as built, with a custom design's rooms and doors. */
	static homeplanet.parser.Pricing.Quote quote(String bpId, SavedGameState s) {
		return homeplanet.parser.Pricing.ship(s, homeplanet.core.Economy.commissionPercent());
	}
	private void showPrice(homeplanet.parser.Pricing.Quote q) {
		int have = homeplanet.vault.Vault.get().storageScrap();
		StringBuilder sb = new StringBuilder("<html><b>Price: " + q.total() + " scrap</b>");
		if (q.percent != 100) sb.append(" (" + q.percent + "% of " + q.subtotal + ")");
		sb.append(", paid from the Cargo Hold, which has " + have + ".");
		if (have < q.total()) sb.append(" <font color='" + MenuTheme.HTML_ORANGE + "'>Not enough scrap.</font>");
		sb.append("<br><font size='-2'>").append(String.join(" · ", q.lines)).append("</font></html>");
		priceLabel.setText(sb.toString());
	}

	private void commission() {
		Entry e = list.getSelectedValue();
		if (e == null || e.header()) return;
		String why = rankReason(e.id);
		if (why != null) { JOptionPane.showMessageDialog(this, why, "Commission Ship", JOptionPane.INFORMATION_MESSAGE); return; }
		String name = nameField.getText().trim();
		if (name.isEmpty()) { JOptionPane.showMessageDialog(this, "She needs a name.", "Commission Ship", JOptionPane.INFORMATION_MESSAGE); return; }
		SavedGameState s;
		try {
			s = make(e.id, name, chosenDifficulty(), rng);
			if (e.id.equals(crewFor)) Commission.sameCrew(s.getPlayerShip(), crew); // the crew in the preview, as named there
			// Original: Advanced Edition content off for her runs, unless she needs it (then the switch was locked on)
			if (!aeSwitch.ae() && homeplanet.parser.Dlc.needsAE(RELIEF.equals(e.id) ? Commission.RELIEF_BASE : e.id, s) == null) s.setDLCEnabled(false);
		} catch (Exception ex) {
			HomePlanet.showErrorDialog("The shipyard could not build her:\n" + ex);
			return;
		}
		homeplanet.vault.Vault vault = homeplanet.vault.Vault.get();
		int price = 0;
		byte[] storageBefore = null;
		boolean isFree = free(e.id);
		if (HomePlanet.commissionCosts() && !isFree) {
			homeplanet.parser.Pricing.Quote q = quote(e.id, s);
			price = q.total();
			log.debug("Commission quote for {}: {} scrap ({})", e.id, price, q.lines);
			int have = vault.storageScrap();
			if (have < price) {
				JOptionPane.showMessageDialog(this, "The shipyard asks " + price + " scrap for her, and the Cargo Hold has " + have + ".\n"
						+ "Store more scrap in the Cargo Bay, or choose a smaller ship.", "Commission Ship", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			if (JOptionPane.showConfirmDialog(this, "Commission " + name + " for " + price + " scrap from the Cargo Hold?",
					"Commission Ship", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) return;
			try {
				storageBefore = vault.payFromStorage(price);
			} catch (Exception ex) {
				HomePlanet.showErrorDialog("The Home Planet Station could not take the scrap from the Cargo Hold. Nothing was changed:\n" + ex.getMessage());
				return;
			}
		}
		homeplanet.vault.Ship ship;
		try {
			ship = vault.adopt(s);
			vault.setOut(ship, s, "Commissioned at The Home Planet Station"); // she waits there until her first jump
		} catch (Exception ex) {
			String refund = "";
			if (storageBefore != null) {
				try { vault.refundStorage(storageBefore); refund = "\nThe " + price + " scrap was returned to the Cargo Hold."; }
				catch (Exception again) { refund = "\nThe " + price + " scrap could not be returned to the Cargo Hold: " + again.getMessage(); }
			}
			HomePlanet.showErrorDialog("The new ship could not be docked; her save could not be written:\n" + ex + refund);
			return;
		}
		log.debug("Commissioned {} ({}): {}, difficulty {}, AE {}, crew {}, paid {}", name, ship.id, e.id, difficulty.getSelectedItem(), s.isDLCEnabled(),
				s.getPlayerShip().getCrewList().size(), price);
		List<String> lines = new ArrayList<String>();
		lines.add(e.label + " (" + e.id + "), difficulty " + difficulty.getSelectedItem() + (s.isDLCEnabled() ? "" : ", Original (Advanced Edition content off)"));
		if (price > 0) lines.add("Paid " + price + " scrap from the Cargo Hold");
		if (isFree && emptyFree(e.id)) {
			lines.add("Free: the free command");
			homeplanet.vault.Vault.get().useFreeCommand("commissioned " + name);
		}
		else if (isFree) {
			lines.add("Free: newly unlocked in FTL (claimed)");
			try { homeplanet.parser.UnlockGrants.claim(e.id); }
			catch (Exception ex) { HomePlanet.showErrorDialog("The Home Planet Station could not record that this free ship was claimed:\n" + ex.getMessage()); }
		}
		lines.add("Crew: " + s.getPlayerShip().getCrewList().size());
		HistoryLog.entry("COMMISSION", name + "  (" + ship.id + ")", lines);
		made = ship;
		dispose();
	}
}
