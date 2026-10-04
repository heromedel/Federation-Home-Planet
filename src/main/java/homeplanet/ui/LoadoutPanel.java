package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import homeplanet.model.Items;
import homeplanet.parser.CompanionMod;
import homeplanet.parser.ShipDesign;
import homeplanet.parser.VanillaMax;

/**
 * Design Ship's third step, in the editor beside the ship: who she is (class, default name, a starter ship or not),
 * her numbers (hull, reactor, weapon and drone slots, missiles, drone parts, each a {@link NumberRow} against the
 * vanilla max), who's aboard at the start, what she carries (weapons, drones, augments in the slots she has), and which
 * systems are installed at the start at what level. Everything writes straight into the design.
 */
public class LoadoutPanel extends JPanel {
	private final ShipDesign d;
	private final LayoutEditor.Host host;
	private boolean filling = false;

	private final JTextField classField = new JTextField(16), nameField = new JTextField(16);
	private final JCheckBox starterBox = new JCheckBox("Starter ship (available to commission)");
	private NumberRow hull, reactor, weaponSlots, droneSlots, missiles, droneParts;
	private final Map<String, JSpinner> crew = new LinkedHashMap<String, JSpinner>();
	private final JLabel crewHeading = heading("Starting crew");
	private final JPanel carries = new JPanel(), systems = new JPanel();
	private final List<JComboBox<BlueprintDialog.Item>> weaponBoxes = new ArrayList<JComboBox<BlueprintDialog.Item>>(),
			droneBoxes = new ArrayList<JComboBox<BlueprintDialog.Item>>(), augBoxes = new ArrayList<JComboBox<BlueprintDialog.Item>>();
	private String systemsKey = null;

	public LoadoutPanel(ShipDesign d, LayoutEditor.Host host) {
		this.d = d;
		this.host = host;
		build();
		fill();
	}

	/** Her loadout, made with the station's usual start when she has none yet (three humans, 8 missiles, 2 drone parts). */
	private CompanionMod.Loadout loadout() {
		if (d.loadout == null) {
			CompanionMod.Loadout l = new CompanionMod.Loadout();
			l.className = d.name;
			l.shipName = "The " + d.name;
			l.crew.put("human", 3);
			l.missiles = 8;
			l.droneParts = 2;
			d.loadout = l;
		}
		return d.loadout;
	}

	private void build() {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBorder(BorderFactory.createEmptyBorder(0, 4, 8, 4));

		p.add(heading("Who she is"));
		p.add(labelled("Class", classField, "What kind of ship she is, e.g. \"Kestrel Cruiser\""));
		p.add(labelled("Default name", nameField, "What a newly commissioned ship is called unless you rename her"));
		javax.swing.event.DocumentListener names = new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) { namesTyped(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e) { namesTyped(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { namesTyped(); }
		};
		classField.getDocument().addDocumentListener(names);
		nameField.getDocument().addDocumentListener(names);
		starterBox.setToolTipText("Listed in Commission once the mod is sent to FTL via Slipstream");
		starterBox.setAlignmentX(LEFT_ALIGNMENT);
		starterBox.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { if (!filling) { d.starter = starterBox.isSelected(); host.changed(); } } });
		p.add(starterBox);

		p.add(heading("Her numbers"));
		p.add(hint("Hers / vanilla max: the most any of the game's ships has. Type what you like; past it, FTL's bars are drawn for the vanilla number."));
		ActionListener numbers = new ActionListener() { public void actionPerformed(ActionEvent e) { numbersChanged(); } };
		p.add(hull = row("Hull", d.hull, VanillaMax.hull(), 1, "Hull points at the start (the game's ships have 30)", numbers));
		p.add(reactor = row("Reactor", d.reactor, VanillaMax.reactor(), 1, "Reactor power at the start (the game's ships have 8)", numbers));
		p.add(weaponSlots = row("Weapon slots", d.weaponSlots, VanillaMax.weaponSlots(), 1, "How many weapons she can carry. FTL draws slot n's weapon on mount n: give her as many mounts (Art step)", numbers));
		p.add(droneSlots = row("Drone slots", d.droneSlots, VanillaMax.droneSlots(), 0, "How many drones she can carry (with Drone Control)", numbers));
		p.add(missiles = row("Missiles", d.loadout == null ? 8 : d.loadout.missiles, VanillaMax.missiles(), 0, "Missiles aboard at the start", numbers));
		p.add(droneParts = row("Drone parts", d.loadout == null ? 2 : d.loadout.droneParts, VanillaMax.droneParts(), 0, "Drone parts aboard at the start (Hacking needs one to launch)", numbers));

		p.add(crewHeading);
		JPanel cg = new JPanel(new GridLayout(0, 2, 12, 2));
		String[] raceNames = {"Human", "Engi", "Mantis", "Rock", "Slug", "Zoltan", "Crystal", "Lanius"};
		javax.swing.event.ChangeListener crewEdit = new javax.swing.event.ChangeListener() { public void stateChanged(javax.swing.event.ChangeEvent e) { crewChanged(); } };
		for (int i = 0; i < CompanionMod.RACES.length; i++) {
			JSpinner sp = new JSpinner(new SpinnerNumberModel(0, 0, VanillaMax.CREW, 1));
			((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setColumns(2);
			sp.addChangeListener(crewEdit);
			crew.put(CompanionMod.RACES[i], sp);
			JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			JLabel race = new JLabel(raceNames[i]);
			race.setPreferredSize(new java.awt.Dimension(56, race.getPreferredSize().height));
			cell.add(race); cell.add(sp);
			cg.add(cell);
		}
		cg.setAlignmentX(LEFT_ALIGNMENT);
		p.add(cg);

		p.add(heading("What she carries"));
		carries.setLayout(new BoxLayout(carries, BoxLayout.Y_AXIS));
		carries.setAlignmentX(LEFT_ALIGNMENT);
		p.add(carries);

		p.add(heading("Systems at the start"));
		p.add(hint("Ticked: installed at the start, at that level. Unticked: the room is ready, to buy or install later."));
		systems.setLayout(new BoxLayout(systems, BoxLayout.Y_AXIS));
		systems.setAlignmentX(LEFT_ALIGNMENT);
		p.add(systems);

		JPanel holder = new JPanel(new BorderLayout());
		holder.add(p, BorderLayout.NORTH);
		JScrollPane sp = new JScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.getVerticalScrollBar().setUnitIncrement(16);
		setLayout(new BorderLayout());
		add(sp, BorderLayout.CENTER);
		setPreferredSize(new java.awt.Dimension(472, 10));
	}
	private NumberRow row(String name, int value, int max, int floor, String tip, ActionListener a) {
		NumberRow r = new NumberRow(name, value, max, floor, false);
		r.setTip(tip);
		r.onChange(a);
		return r;
	}
	private static JLabel heading(String t) {
		JLabel l = new JLabel(t);
		l.setFont(MenuTheme.HEADING_FONT);
		l.setForeground(MenuTheme.GOLD);
		l.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}
	private static JLabel hint(String t) {
		JLabel l = new JLabel("<html><div style='width:318pt'>" + t + "</div></html>"); // pt: Swing's HTML draws a px a third too big
		l.setFont(MenuTheme.TEXT_FONT);
		l.setForeground(MenuTheme.GREY_GREEN);
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}
	private static JPanel labelled(String name, JTextField f, String tip) {
		JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
		JLabel l = new JLabel(name + ":");
		l.setPreferredSize(new java.awt.Dimension(106, l.getPreferredSize().height));
		r.add(l); r.add(f);
		f.setToolTipText(tip);
		r.setAlignmentX(LEFT_ALIGNMENT);
		return r;
	}

	// ---- the design into the fields ----

	/** Shows the design as it is now (after an undo, or a system placed or removed on the Rooms step). */
	public void fill() {
		filling = true;
		try {
			CompanionMod.Loadout l = d.loadout;
			classField.setText(l == null ? d.name : l.className);
			nameField.setText(l == null ? "The " + d.name : l.shipName);
			starterBox.setSelected(d.starter);
			hull.set(d.hull, false); reactor.set(d.reactor, false); weaponSlots.set(d.weaponSlots, false); droneSlots.set(d.droneSlots, false);
			missiles.set(l == null ? 8 : l.missiles, false); droneParts.set(l == null ? 2 : l.droneParts, false);
			for (Map.Entry<String, JSpinner> e : crew.entrySet()) {
				Integer v = l == null ? ("human".equals(e.getKey()) ? Integer.valueOf(3) : null) : l.crew.get(e.getKey());
				e.getValue().setValue(Math.min(VanillaMax.CREW, v == null ? 0 : v));
			}
			crewHeading.setText("Starting crew  (" + crewTotal() + " of " + VanillaMax.CREW + ")");
			rebuildCarries();
			rebuildSystems();
		} finally {
			filling = false;
		}
	}
	private int crewTotal() { int t = 0; for (JSpinner sp : crew.values()) t += (Integer) sp.getValue(); return t; }

	/** The weapon, drone and augment boxes, as many as she has slots. */
	private void rebuildCarries() {
		carries.removeAll();
		weaponBoxes.clear(); droneBoxes.clear(); augBoxes.clear();
		CompanionMod.Loadout l = d.loadout;
		carries.add(hint("Weapons (" + d.weaponSlots + (d.weaponSlots == 1 ? " slot" : " slots") + ")"));
		carries.add(boxes(weaponBoxes, BlueprintDialog.weapons(), Math.max(1, d.weaponSlots), l == null ? new ArrayList<String>() : l.weapons));
		boolean hasDrones = d.systems.containsKey("drones");
		carries.add(hint("Drones (" + d.droneSlots + (d.droneSlots == 1 ? " slot" : " slots") + ")" + (hasDrones ? "" : " - she has no Drone Control yet")));
		if (d.droneSlots > 0) carries.add(boxes(droneBoxes, BlueprintDialog.drones(), d.droneSlots, l == null ? new ArrayList<String>() : l.drones));
		carries.add(hint("Augments"));
		carries.add(boxes(augBoxes, BlueprintDialog.augments(), BlueprintDialog.MAX_AUGS, l == null ? new ArrayList<String>() : l.augs));
		carries.revalidate();
		carries.repaint();
	}
	private JPanel boxes(List<JComboBox<BlueprintDialog.Item>> into, List<BlueprintDialog.Item> items, int n, List<String> ids) {
		JPanel g = new JPanel(new GridLayout(0, 1, 0, 2));
		for (int i = 0; i < n; i++) {
			JComboBox<BlueprintDialog.Item> b = BlueprintDialog.itemBox(items);
			b.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { if (!filling) carriesChanged(); } });
			into.add(b);
			g.add(b);
		}
		boolean was = filling; filling = true;
		try { BlueprintDialog.setBoxes(into, ids); } finally { filling = was; }
		g.setAlignmentX(LEFT_ALIGNMENT);
		g.setMaximumSize(new java.awt.Dimension(420, Integer.MAX_VALUE));
		return g;
	}
	/** One row per placed system, built again when the systems on the ship change. */
	private void rebuildSystems() {
		String key = d.systems.keySet().toString();
		boolean same = key.equals(systemsKey);
		systemsKey = key;
		if (same) { // the rows stand: show their values
			for (java.awt.Component c : systems.getComponents()) {
				if (!(c instanceof NumberRow)) continue;
				NumberRow r = (NumberRow) c;
				CompanionMod.Sys s = d.systems.get((String) r.getClientProperty("system"));
				if (s == null) continue;
				r.set(s.power, false);
				r.tick().setSelected(!d.notAtStart.contains(s.id));
				JButton weapon = (JButton) r.getClientProperty("weapon");
				if (weapon != null) weaponLabel(weapon, s.weapon);
			}
			return;
		}
		systems.removeAll();
		if (d.systems.isEmpty()) systems.add(hint("No systems placed yet (the Rooms step)."));
		// a design from before with both bays ticked shows the Clone Bay alone, as Commission builds her
		if (d.systems.containsKey("medbay") && d.systems.containsKey("clonebay") && !d.notAtStart.contains("medbay") && !d.notAtStart.contains("clonebay")) d.notAtStart.add("medbay");
		final Map<String, NumberRow> rows = new LinkedHashMap<String, NumberRow>();
		for (final CompanionMod.Sys s : d.systems.values()) {
			int max = VanillaMax.system(s.id);
			final NumberRow r = new NumberRow(Items.systemTitle(s.id), Math.max(1, s.power), max, 1, true);
			r.putClientProperty("system", s.id);
			r.tick().setSelected(!d.notAtStart.contains(s.id));
			r.tick().setToolTipText("Ticked: she starts with it installed. Unticked: its room is ready, to buy or install later");
			r.setTip("Starting level");
			r.onChange(new ActionListener() { public void actionPerformed(ActionEvent e) { if (!filling) systemChanged(s.id, r, rows); } });
			if (s.id.equals("artillery")) {
				final JButton weapon = new JButton();
				weapon.setMargin(new java.awt.Insets(0, 6, 0, 6));
				weaponLabel(weapon, s.weapon);
				weapon.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						CompanionMod.Sys now = d.systems.get("artillery"); // the design's own (an undo replaces the systems)
						if (now == null) return;
						String w = ArtilleryPicker.choose(LoadoutPanel.this, now.weapon);
						if (w == null) return;
						now.weapon = w;
						weaponLabel(weapon, w);
						host.changed();
					}
				});
				r.putClientProperty("weapon", weapon);
				r.addExtra(weapon);
			}
			rows.put(s.id, r);
			systems.add(r);
		}
		systems.revalidate();
		systems.repaint();
	}

	private static void weaponLabel(JButton b, String weapon) {
		b.setText(weapon == null ? "Choose weapon..." : ArtilleryPicker.label(weapon));
		b.setToolTipText(weapon == null ? "Which weapon her artillery fires" : ArtilleryPicker.tip(weapon));
	}

	// ---- the fields into the design ----

	private void namesTyped() {
		if (filling) return;
		CompanionMod.Loadout l = loadout();
		l.className = classField.getText().trim();
		l.shipName = nameField.getText().trim();
		host.changed();
	}
	private void numbersChanged() {
		if (filling) return;
		boolean slots = d.weaponSlots != weaponSlots.get() || d.droneSlots != droneSlots.get();
		d.hull = hull.get(); d.reactor = reactor.get(); d.weaponSlots = weaponSlots.get(); d.droneSlots = droneSlots.get();
		CompanionMod.Loadout l = loadout();
		l.missiles = missiles.get(); l.droneParts = droneParts.get();
		if (slots) { carriesChanged(); rebuildCarries(); } // keep what's chosen, then as many boxes as slots
		host.changed();
	}
	private void crewChanged() {
		if (filling) return;
		CompanionMod.Loadout l = loadout();
		l.crew.clear();
		for (Map.Entry<String, JSpinner> e : crew.entrySet()) { int v = (Integer) e.getValue().getValue(); if (v > 0) l.crew.put(e.getKey(), v); }
		crewHeading.setText("Starting crew  (" + crewTotal() + " of " + VanillaMax.CREW + ")");
		host.changed();
	}
	private void carriesChanged() {
		if (filling) return;
		CompanionMod.Loadout l = loadout();
		l.weapons.clear(); l.drones.clear(); l.augs.clear();
		for (JComboBox<BlueprintDialog.Item> b : weaponBoxes) { BlueprintDialog.Item it = (BlueprintDialog.Item) b.getSelectedItem(); if (it != null && it.id != null) l.weapons.add(it.id); }
		for (JComboBox<BlueprintDialog.Item> b : droneBoxes) { BlueprintDialog.Item it = (BlueprintDialog.Item) b.getSelectedItem(); if (it != null && it.id != null) l.drones.add(it.id); }
		for (JComboBox<BlueprintDialog.Item> b : augBoxes) { BlueprintDialog.Item it = (BlueprintDialog.Item) b.getSelectedItem(); if (it != null && it.id != null) l.augs.add(it.id); }
		host.changed();
	}
	private void systemChanged(String id, NumberRow r, Map<String, NumberRow> rows) {
		CompanionMod.Sys s = d.systems.get(id);
		if (s == null) return;
		s.power = r.get();
		if (r.tick().isSelected()) {
			d.notAtStart.remove(id);
			// a Medbay and a Clone Bay take each other's place (heromedel): ticking one unticks the other
			String other = id.equals("medbay") ? "clonebay" : id.equals("clonebay") ? "medbay" : null;
			if (other != null && rows.containsKey(other) && rows.get(other).tick().isSelected()) { rows.get(other).tick().setSelected(false); d.notAtStart.add(other); }
		} else d.notAtStart.add(id);
		host.changed();
	}
	/** After any edit elsewhere (a system placed or removed, an undo): the system rows follow. */
	public void systemsChanged() {
		if (filling) return;
		filling = true;
		try { rebuildSystems(); } finally { filling = false; }
	}
}
