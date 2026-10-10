package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.xml.AugBlueprint;
import net.blerf.ftl.xml.DroneBlueprint;
import net.blerf.ftl.xml.ShipBlueprint;
import net.blerf.ftl.xml.WeaponBlueprint;

import homeplanet.parser.CompanionMod;
import homeplanet.parser.CompanionMod.Loadout;

/**
 * A station blueprint's own identity and new-game loadout: class and default name, starting crew by race,
 * starting weapons, drones and augments, missiles and drone parts. Shown when a remodel is finalized, and from
 * Settings (Blueprints...) to change it later.
 */
@SuppressWarnings("unchecked")
public class BlueprintDialog extends JDialog {

	/** What the dialog returns. */
	public static class Result {
		public Loadout loadout;
		public boolean starter;
	}

	static final int MAX_CREW = 8, MAX_AUGS = 3;
	private static final String[] RACE_NAMES = {"Human", "Engi", "Mantis", "Rock", "Slug", "Zoltan", "Crystal", "Lanius"};

	/** A choice in a drop-down: an item id and what it's called. */
	static class Item {
		final String id, label;
		Item(String id, String label) { this.id = id; this.label = label; }
		public String toString() { return label; }
	}

	private final JTextField classField = new JTextField(22), nameField = new JTextField(22);
	private final JCheckBox starterBox = new JCheckBox("Make this blueprint a starter ship (available to commission)");
	private final Map<String, JSpinner> crewSpinners = new LinkedHashMap<String, JSpinner>();
	private final JLabel crewTotal = new JLabel();
	private final List<JComboBox<Item>> weaponBoxes = new ArrayList<JComboBox<Item>>(), droneBoxes = new ArrayList<JComboBox<Item>>(),
			augBoxes = new ArrayList<JComboBox<Item>>();
	private final JSpinner missiles = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1)), droneParts = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
	private Result result = null;

	/**
	 * Opens the window.
	 * @param intro text at the top (the Finalize warning), or null
	 * @param start what the fields start with
	 * @param current the ship's own loadout, for "Use her current loadout" (or null to leave that button out)
	 * @param modelId the model blueprint (for its weapon and drone slots)
	 * @param showStarter whether to show the starter-ship box (and its value)
	 * @param okText the OK button's label
	 */
	public static Result open(Component owner, String title, String intro, Loadout start, Loadout current, String modelId,
			boolean showStarter, boolean starter, String okText) {
		ShipBlueprint model = DataManager.get().getShip(modelId);
		int weaponSlots = model != null && model.getWeaponSlots() != null ? model.getWeaponSlots() : 4;
		int droneSlots = model != null && model.getDroneSlots() != null ? model.getDroneSlots() : 3;
		boolean hasDrones = model != null && model.getSystemList() != null && model.getSystemList().getDroneRoom() != null;
		return open(owner, title, intro, start, current, weaponSlots, droneSlots, hasDrones, showStarter, starter, okText);
	}
	/** As above, with her slot counts given (a ship with no blueprint of her own yet). */
	public static Result open(Component owner, String title, String intro, Loadout start, Loadout current, int weaponSlots, int droneSlots,
			boolean hasDrones, boolean showStarter, boolean starter, String okText) {
		BlueprintDialog d = new BlueprintDialog(owner, title, intro, start, current, weaponSlots, droneSlots, hasDrones, showStarter, starter, okText);
		d.setVisible(true);
		return d.result;
	}

	private BlueprintDialog(Component owner, String title, String intro, Loadout start, final Loadout current, int weaponSlots, int droneSlots,
			boolean hasDrones, boolean showStarter, boolean starter, String okText) {
		super(SwingUtilities.getWindowAncestor(owner), title, ModalityType.APPLICATION_MODAL);

		JPanel body = new JPanel(new GridBagLayout());
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
		c.insets = new Insets(2, 0, 2, 0);
		if (intro != null) {
			body.add(new JLabel("<html>" + intro + "</html>"), next(c));
		}
		heading(body, c, "Name");
		JPanel names = new JPanel(new GridBagLayout());
		GridBagConstraints n = new GridBagConstraints();
		n.anchor = GridBagConstraints.WEST; n.insets = new Insets(2, 0, 2, 8);
		names.add(new JLabel("Class:"), n);
		n.gridx = 1; names.add(classField, n);
		n.gridx = 2; names.add(hint("What kind of ship " + homeplanet.model.Words.she() + " is, e.g. \"Kestrel Cruiser\""), n);
		n.gridx = 0; n.gridy = 1; names.add(new JLabel("Default name:"), n);
		n.gridx = 1; names.add(nameField, n);
		n.gridx = 2; names.add(hint("What a newly commissioned ship is called unless you rename " + homeplanet.model.Words.herObj()), n);
		body.add(names, next(c));
		if (showStarter) {
			starterBox.setSelected(starter);
			body.add(starterBox, next(c));
		}

		heading(body, c, "Starting crew");
		JPanel crew = new JPanel(new GridLayout(0, 4, 24, 4));
		ChangeListener recount = new ChangeListener() {
			public void stateChanged(ChangeEvent e) { updateCrewTotal(); }
		};
		for (int i = 0; i < CompanionMod.RACES.length; i++) {
			JSpinner sp = new JSpinner(new SpinnerNumberModel(0, 0, MAX_CREW, 1));
			sp.addChangeListener(recount);
			crewSpinners.put(CompanionMod.RACES[i], sp);
			JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			JLabel race = new JLabel(RACE_NAMES[i]);
			race.setPreferredSize(new java.awt.Dimension(64, race.getPreferredSize().height));
			cell.add(race);
			cell.add(sp);
			crew.add(cell);
		}
		body.add(crew, next(c));
		body.add(crewTotal, next(c));

		heading(body, c, "Starting weapons (" + weaponSlots + " slots)");
		List<Item> weaponItems = weapons();
		JPanel wp = new JPanel(new GridLayout(0, 2, 10, 4));
		for (int i = 0; i < weaponSlots; i++) {
			JComboBox<Item> b = itemBox(weaponItems);
			weaponBoxes.add(b);
			wp.add(b);
		}
		body.add(wp, next(c));
		JPanel mp = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		mp.add(new JLabel("Missiles: "));
		mp.add(missiles);
		body.add(mp, next(c));

		heading(body, c, "Starting drones (" + droneSlots + " slots)" + (hasDrones ? "" : " - " + homeplanet.model.Words.she() + " has no Drone Control yet"));
		List<Item> droneItems = drones();
		JPanel dp = new JPanel(new GridLayout(0, 2, 10, 4));
		for (int i = 0; i < droneSlots; i++) {
			JComboBox<Item> b = itemBox(droneItems);
			droneBoxes.add(b);
			dp.add(b);
		}
		body.add(dp, next(c));
		JPanel pp = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		pp.add(new JLabel("Drone parts: "));
		pp.add(droneParts);
		body.add(pp, next(c));

		heading(body, c, "Starting augments");
		List<Item> augItems = augments();
		JPanel ap = new JPanel(new GridLayout(0, 3, 10, 4));
		for (int i = 0; i < MAX_AUGS; i++) {
			JComboBox<Item> b = itemBox(augItems);
			augBoxes.add(b);
			ap.add(b);
		}
		body.add(ap, next(c));

		fill(start);

		JPanel buttons = new JPanel(new BorderLayout());
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
		if (current != null) {
			JButton use = new JButton("Use " + homeplanet.model.Words.her() + " current loadout");
			use.setToolTipText("Fill the weapons, drones and augments with what " + homeplanet.model.Words.she() + " carries now");
			use.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					setBoxes(weaponBoxes, current.weapons);
					setBoxes(droneBoxes, current.drones);
					setBoxes(augBoxes, current.augs);
				}
			});
			left.add(use);
		}
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton ok = new JButton(okText);
		JButton cancel = new JButton("Cancel");
		ok.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { accept(); } });
		cancel.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		right.add(ok);
		right.add(cancel);
		buttons.add(left, BorderLayout.WEST);
		buttons.add(right, BorderLayout.EAST);
		getRootPane().setDefaultButton(ok);

		getContentPane().add(body, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setLocationRelativeTo(owner);
	}

	private void fill(Loadout l) {
		classField.setText(l.className);
		nameField.setText(l.shipName);
		for (Map.Entry<String, JSpinner> e : crewSpinners.entrySet()) {
			Integer v = l.crew.get(e.getKey());
			e.getValue().setValue(Math.min(MAX_CREW, v == null ? 0 : v));
		}
		setBoxes(weaponBoxes, l.weapons);
		setBoxes(droneBoxes, l.drones);
		setBoxes(augBoxes, l.augs);
		missiles.setValue(Math.min(99, Math.max(0, l.missiles)));
		droneParts.setValue(Math.min(99, Math.max(0, l.droneParts)));
		updateCrewTotal();
	}

	/** Puts the ids in the boxes in order; the rest are left empty. An id the lists don't have is added so nothing is lost. */
	static void setBoxes(List<JComboBox<Item>> boxes, List<String> ids) {
		for (int i = 0; i < boxes.size(); i++) {
			JComboBox<Item> b = boxes.get(i);
			String id = i < ids.size() ? ids.get(i) : null;
			b.setSelectedIndex(0);
			if (id == null) continue;
			boolean found = false;
			for (int k = 0; k < b.getItemCount(); k++) {
				if (id.equals(b.getItemAt(k).id)) { b.setSelectedIndex(k); found = true; break; }
			}
			if (!found) {
				Item extra = new Item(id, id);
				b.addItem(extra);
				b.setSelectedItem(extra);
			}
		}
	}

	private int crewCount() {
		int t = 0;
		for (JSpinner sp : crewSpinners.values()) t += (Integer) sp.getValue();
		return t;
	}
	private void updateCrewTotal() {
		int t = crewCount();
		crewTotal.setText("Total: " + t + " of " + MAX_CREW + (t == 0 ? "  (" + homeplanet.model.Words.she() + " needs at least one)" : t > MAX_CREW ? "  (too many: FTL ships hold " + MAX_CREW + ")" : ""));
		crewTotal.setForeground(t == 0 || t > MAX_CREW ? new Color(255, 170, 90) : null); // null: the panel's own text colour
	}

	private void accept() {
		int t = crewCount();
		if (t < 1 || t > MAX_CREW) {
			JOptionPane.showMessageDialog(this, "A starting crew is 1 to " + MAX_CREW + " people.", getTitle(), JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Loadout l = new Loadout();
		l.className = classField.getText().trim();
		l.shipName = nameField.getText().trim();
		for (Map.Entry<String, JSpinner> e : crewSpinners.entrySet()) {
			int v = (Integer) e.getValue().getValue();
			if (v > 0) l.crew.put(e.getKey(), v);
		}
		for (JComboBox<Item> b : weaponBoxes) { Item it = (Item) b.getSelectedItem(); if (it != null && it.id != null) l.weapons.add(it.id); }
		for (JComboBox<Item> b : droneBoxes) { Item it = (Item) b.getSelectedItem(); if (it != null && it.id != null) l.drones.add(it.id); }
		for (JComboBox<Item> b : augBoxes) { Item it = (Item) b.getSelectedItem(); if (it != null && it.id != null) l.augs.add(it.id); }
		l.missiles = (Integer) missiles.getValue();
		l.droneParts = (Integer) droneParts.getValue();
		result = new Result();
		result.loadout = l;
		result.starter = starterBox.isSelected();
		dispose();
	}

	// ---- the item lists: what a player ship can carry (no boss, enemy-only or artillery pieces) ----

	/**
	 * Store items (rarity above 0) plus anything a player ship starts with: the Basic Laser and other starting weapons
	 * have rarity 0 because stores never sell them.
	 */
	private static boolean playerItem(String id, int rarity) {
		if (id.contains("BOSS") || id.contains("ENEMY") || id.startsWith("ARTILLERY")) return false;
		return rarity > 0 || starting().contains(id);
	}
	private static java.util.Set<String> starting;
	private static java.util.Set<String> starting() {
		if (starting != null) return starting;
		java.util.Set<String> s = new java.util.HashSet<String>();
		for (net.blerf.ftl.xml.ShipBlueprint bp : DataManager.get().getShips().values()) {
			if (bp.getId() == null || !bp.getId().startsWith("PLAYER_SHIP")) continue;
			if (bp.getWeaponList() != null && bp.getWeaponList().getWeaponIds() != null)
				for (net.blerf.ftl.xml.ShipBlueprint.WeaponList.WeaponId w : bp.getWeaponList().getWeaponIds()) s.add(w.name);
			if (bp.getDroneList() != null && bp.getDroneList().getDroneIds() != null)
				for (net.blerf.ftl.xml.ShipBlueprint.DroneList.DroneId d : bp.getDroneList().getDroneIds()) s.add(d.name);
			if (bp.getAugments() != null) for (net.blerf.ftl.xml.ShipBlueprint.AugmentId a : bp.getAugments()) s.add(a.name);
		}
		return starting = s;
	}
	static List<Item> weapons() {
		List<Item> out = new ArrayList<Item>();
		for (WeaponBlueprint w : DataManager.get().getWeapons().values())
			if (playerItem(w.getId(), w.getRarity())) out.add(new Item(w.getId(), titled(w.getTitle(), w.getId(), w.getPower())));
		return sorted(out);
	}
	static List<Item> drones() {
		List<Item> out = new ArrayList<Item>();
		for (DroneBlueprint d : DataManager.get().getDrones().values())
			if (playerItem(d.getId(), d.getRarity())) out.add(new Item(d.getId(), titled(d.getTitle(), d.getId(), d.getPower())));
		return sorted(out);
	}
	static List<Item> augments() {
		List<Item> out = new ArrayList<Item>();
		for (AugBlueprint a : DataManager.get().getAugments().values())
			if (playerItem(a.getId(), a.getRarity())) out.add(new Item(a.getId(), titled(a.getTitle(), a.getId(), 0)));
		return sorted(out);
	}
	/** "Burst Laser II  ·  2 power" (the id is in the tooltip). */
	private static String titled(net.blerf.ftl.xml.DefaultDeferredText t, String id, int power) {
		String s = null;
		try { s = t == null ? null : t.getTextValue(); } catch (Exception e) { }
		return (s == null || s.isEmpty() ? id : s) + (power > 0 ? "  \u00b7  " + power + " power" : "");
	}
	/** A list of items whose rows (and the box itself) show the item's stats on hover, as the Cargo Bay's do. */
	static JComboBox<Item> itemBox(List<Item> items) {
		final JComboBox<Item> b = new WideComboBox(new javax.swing.DefaultComboBoxModel<Item>(items.toArray(new Item[0])));
		b.setRenderer(new javax.swing.DefaultListCellRenderer() {
			@Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean sel, boolean focus) {
				super.getListCellRendererComponent(list, value, index, sel, focus);
				Item it = (Item) value;
				setIcon(it == null || it.id == null ? null : IconFactory.itemIcon(it.id));
				String tip = it == null || it.id == null ? null : ItemTooltips.tooltip(it.id);
				setToolTipText(tip == null || it == null || it.id == null ? null : tip.replace("</html>", "<div style='margin-top:4px; color:gray'>" + it.id + "</div></html>"));
				return this;
			}
		});
		b.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Item it = (Item) b.getSelectedItem();
				b.setToolTipText(it == null || it.id == null ? null : ItemTooltips.tooltip(it.id));
			}
		});
		return b;
	}
	private static List<Item> sorted(List<Item> items) {
		Collections.sort(items, new Comparator<Item>() {
			public int compare(Item a, Item b) { return a.label.compareToIgnoreCase(b.label); }
		});
		items.add(0, new Item(null, "(empty)"));
		return items;
	}

	private static JLabel hint(String text) {
		JLabel l = new JLabel(text);
		l.setFont(MenuTheme.TEXT_FONT);
		l.setForeground(MenuTheme.GREY_GREEN);
		return l;
	}
	private static void heading(JPanel body, GridBagConstraints c, String text) {
		JLabel h = new JLabel(text);
		h.setFont(MenuTheme.HEADING_FONT);
		h.setForeground(MenuTheme.GOLD);
		h.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		body.add(h, next(c));
	}
	private static GridBagConstraints next(GridBagConstraints c) {
		GridBagConstraints copy = (GridBagConstraints) c.clone();
		c.gridy++;
		return copy;
	}
}
