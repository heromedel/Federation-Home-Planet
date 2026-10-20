package homeplanet.ui;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;
import net.blerf.ftl.xml.ShipBlueprint;
import homeplanet.model.Items;
import homeplanet.model.Words;
import homeplanet.parser.SaveHelper;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * The ship report (7.02, out of SpaceDockUI): a picture of the ship, then supplies, crew, weapons, drones and augments, with
 * FTL's icons; and the Stats tab beside it. The Space Dock, Build and Commission show it.
 */
public final class ShipReport {
	private ShipReport() { }


	/** The tallest a ship's picture is drawn in her report. */
	private static final int REPORT_PIC_H = 170;

	public static JPanel panel(SavedGameState sgs) { return panel(sgs, null, null); }
	/**
	 * As {@link #panel(SavedGameState)}; with {@code rename}, clicking a crew member's name asks for a new one,
	 * and with {@code reroll} a die beside the Crew heading rolls new names (Commission).
	 */
	public static JPanel panel(SavedGameState sgs, final java.util.function.Consumer<CrewState> rename, Runnable reroll) {
		ShipState state = sgs.getPlayerShip();
		JPanel p = new JPanel(new java.awt.BorderLayout(18, 4));
		JLabel pic = reportPicture(sgs);
		// two columns from the top: her picture, supplies and crew on the left; weapons, drones, augments, cargo and systems on the right
		JPanel left = column(), right = column();
		if (pic != null) { pic.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT); left.add(pic); }
		JPanel crew = left, systems = right;
		reportHeading(left, "Supplies");
		int maxHull = 0;
		ShipBlueprint bp = DataManager.get().getShips().get(sgs.getPlayerShipBlueprintId());
		if (bp != null && bp.getHealth() != null) maxHull = bp.getHealth().amount;
		reportRow(left, null, "Hull: " + state.getHullAmt() + (maxHull > 0 ? " / " + maxHull : ""));
		reportRow(left, IconFactory.supplyIcon("fuel"), "Fuel: " + state.getFuelAmt());
		reportRow(left, IconFactory.supplyIcon("missiles"), "Missiles: " + state.getMissilesAmt());
		reportRow(left, IconFactory.supplyIcon("drones"), "Drone Parts: " + state.getDronePartsAmt());
		reportRow(left, IconFactory.supplyIcon("scrap"), "Scrap: " + state.getScrapAmt());
		if (reroll == null) reportHeading(crew, "Crew");
		else {
			crew.add(Box.createRigidArea(new Dimension(0, 6)));
			JPanel head = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
			head.setOpaque(false);
			head.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
			head.add(SpaceDockUI.shadowLabel("Crew", null, true));
			head.add(DiceIcon.button("New names for " + Words.her() + " crew (or click a name to choose one)", reroll));
			head.setMaximumSize(head.getPreferredSize());
			crew.add(head);
		}
		for (final CrewState c : SaveHelper.getOwnCrew(state)) {
			final JLabel row = reportRow(crew, CrewReport.withHealth(IconFactory.crewIcon(c), c), c.getName() + " (" + homeplanet.model.Crew.raceTitle(c) + ")");
			row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			if (rename == null) { // a click opens their report
				row.setToolTipText("Click for " + c.getName() + "'s report");
				row.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mouseClicked(java.awt.event.MouseEvent e) { CrewReport.show(row, c, false, new Object[] {"OK"}); } });
				continue;
			}
			row.setToolTipText("Click to rename " + c.getName());
			row.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mouseClicked(java.awt.event.MouseEvent e) { rename.accept(c); } });
		}
		reportHeading(right, "Weapons");
		for (WeaponState w : state.getWeaponList()) reportRow(right, IconFactory.itemIcon(w.getWeaponId()), Items.weaponTitle(w.getWeaponId()));
		reportHeading(right, "Drones");
		for (DroneState d : state.getDroneList()) reportRow(right, IconFactory.itemIcon(d.getDroneId()), Items.droneTitle(d.getDroneId()));
		reportHeading(right, "Augments");
		for (String augmentId : state.getAugmentIdList()) reportRow(right, null, Items.augmentTitle(augmentId));
		List<String> cargo = SaveHelper.cargo(sgs); // not the augment FTL is asking about (5.52)
		reportHeading(right, "Cargo (" + cargo.size() + " of " + SaveHelper.CARGO_SLOTS + ")");
		for (String id : cargo) reportRow(right, IconFactory.itemIcon(id), Items.title(id));
		reportHeading(systems, "Systems");
		systemRow(systems, null, "Reactor", state.getReservePowerCapacity(), homeplanet.parser.VanillaMax.reactor(), 0);
		for (Object[] sys : SYSTEM_NAMES) {
			net.blerf.ftl.parser.SavedGameParser.SystemType t = (net.blerf.ftl.parser.SavedGameParser.SystemType) sys[0];
			net.blerf.ftl.parser.SavedGameParser.SystemState st = state.getSystem(t);
			if (st != null && st.getCapacity() > 0) systemRow(systems, t.getId(), (String) sys[1], st.getCapacity(), homeplanet.parser.VanillaMax.system(t.getId()), st.getDamagedBars());
		}
		JPanel cols = new JPanel(new java.awt.GridBagLayout());
		java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
		gc.anchor = java.awt.GridBagConstraints.NORTHWEST;
		gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
		gc.weightx = 1; gc.weighty = 1; // from the top, whatever room the window gives
		gc.insets = new java.awt.Insets(0, 0, 0, 18);
		gc.gridx = 0; gc.gridy = 0; cols.add(left, gc);
		gc.gridx = 1; cols.add(right, gc);
		p.add(cols, java.awt.BorderLayout.CENTER);
		return p;
	}
	/** A system in the report: FTL's icon for it, the name, its level and a bar of it to the vanilla max (broken bars in red). */
	private static void systemRow(JPanel p, String systemId, String name, int level, int max, int broken) {
		JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		javax.swing.Icon icon = systemId == null ? null : systemIcon(systemId);
		JLabel l = new JLabel(name, icon, JLabel.LEFT);
		l.setIconTextGap(6);
		l.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, icon == null ? 40 : 34 - icon.getIconWidth(), 1, 0));
		l.setPreferredSize(new Dimension(150, l.getPreferredSize().height));
		row.add(l);
		// the level and its bar; past the bar's reach, the level in words ("12 / 2 broken") and no bar
		boolean words = LevelBar.asWords(level, max);
		JLabel n = new JLabel(words ? LevelBar.words(level, broken) : String.valueOf(level), words ? JLabel.LEFT : JLabel.RIGHT);
		if (!words) n.setPreferredSize(new Dimension(18, n.getPreferredSize().height));
		n.setForeground(level > max ? LevelBar.AMBER : MenuTheme.WHITE);
		n.setToolTipText(level > max ? "Past the vanilla max of " + max : "Of a vanilla max of " + max);
		row.add(n);
		if (!words) { row.add(Box.createRigidArea(new Dimension(8, 1))); row.add(new LevelBar(level, max, broken, LevelBar.WIDTH, 12)); }
		row.setMaximumSize(row.getPreferredSize());
		p.add(row);
	}
	private static final java.util.Map<String, javax.swing.Icon> SYSTEM_ICONS = new java.util.HashMap<String, javax.swing.Icon>();
	/** FTL's icon for a system (its room overlay, scaled to the report's rows), or null without one. */
	static synchronized javax.swing.Icon systemIcon(String id) {
		if (SYSTEM_ICONS.containsKey(id)) return SYSTEM_ICONS.get(id);
		javax.swing.Icon icon = null;
		BufferedImage img = LayoutEditor.image("img/icons/s_" + id + "_overlay.png");
		if (img != null) {
			int h = 16, w = Math.max(1, img.getWidth() * h / Math.max(1, img.getHeight()));
			BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = small.createGraphics();
			g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(img, 0, 0, w, h, null);
			g.dispose();
			icon = new ImageIcon(small);
		}
		SYSTEM_ICONS.put(id, icon);
		return icon;
	}
	/** Her picture for the report: half the Space Dock size, and no taller than REPORT_PIC_H (the Lanius would push the lists off the screen). */
	private static JLabel reportPicture(SavedGameState sgs) {
		ShipBlueprint ship = SpaceDockUI.blueprintOf(sgs.getPlayerShipBlueprintId());
		if (ship == null) return null;
		BufferedImage img = IconFactory.load("img/ship/" + ship.getGraphicsBaseName() + "_base.png");
		if (img == null) return null;
		double scale = Math.min(0.5, (double) REPORT_PIC_H / img.getHeight());
		int w = Math.max(1, (int) Math.round(img.getWidth() * scale)), h = Math.max(1, (int) Math.round(img.getHeight() * scale));
		BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = small.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(img, 0, 0, w, h, null);
		g.dispose();
		JLabel pic = new JLabel(new ImageIcon(small));
		pic.setHorizontalAlignment(JLabel.LEFT);
		return pic;
	}

	/** The report's Stats tab: her picture as on the Report tab, then this journey, her service and her crew's standouts, side by side. */
	static JPanel statsTab(Ship ship, SavedGameState sgs) {
		homeplanet.parser.ShipStats st = homeplanet.parser.ShipStats.of(Vault.get(), ship, sgs);
		JPanel p = new JPanel(new java.awt.BorderLayout(18, 4));
		JLabel pic = reportPicture(sgs);
		if (pic != null) p.add(pic, java.awt.BorderLayout.NORTH);
		JPanel journey = column(), service = column(), crew = column();
		reportHeading(journey, "This Journey");
		statLines(journey, st.journey);
		if (!st.journeyKnown) statNote(journey, Words.Her() + " journey's own counts begin with " + Words.her() + " next New Journey; until then they're in " + Words.her() + " service.");
		reportHeading(service, Words.Her() + " Service");
		statLines(service, st.service);
		if (st.traded != null) statNote(service, st.traded);
		reportHeading(crew, Words.Her() + " Crew");
		if (st.crew.isEmpty()) statNote(crew, "No standouts yet: " + Words.her() + " crew's records grow as they serve.");
		// each crew member once, with every title she holds beneath her name
		java.util.LinkedHashMap<CrewState, StringBuilder> titles = new java.util.LinkedHashMap<CrewState, StringBuilder>();
		for (homeplanet.parser.ShipStats.Standout o : st.crew) {
			if (!titles.containsKey(o.crew)) titles.put(o.crew, new StringBuilder());
			titles.get(o.crew).append("<b><font color='").append(MenuTheme.HTML_GOLD).append("'>").append(o.title).append("</font></b>&nbsp;&nbsp;<font color='")
					.append(MenuTheme.HTML_GREY_GREEN).append("'>").append(o.earned).append("</font><br>");
		}
		for (java.util.Map.Entry<CrewState, StringBuilder> e : titles.entrySet()) {
			JLabel who = reportRow(crew, IconFactory.crewIcon(e.getKey()), e.getKey().getName());
			who.setForeground(MenuTheme.WHITE);
			who.setFont(MenuTheme.LABEL_FONT);
			JLabel why = new JLabel("<html>" + e.getValue() + "</html>");
			why.setFont(MenuTheme.TEXT_FONT);
			why.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 40, 6, 0));
			why.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
			crew.add(why);
		}
		JPanel cols = new JPanel(new java.awt.GridBagLayout());
		java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
		gc.anchor = java.awt.GridBagConstraints.NORTHWEST;
		gc.insets = new java.awt.Insets(0, 0, 0, 28);
		gc.gridy = 0;
		gc.gridx = 0; cols.add(journey, gc);
		gc.gridx = 1; cols.add(service, gc);
		gc.gridx = 2; gc.insets = new java.awt.Insets(0, 0, 0, 0); cols.add(crew, gc);
		p.add(cols, java.awt.BorderLayout.CENTER);
		return p;
	}
	/** Stats as label and value: the label in grey-green, the value in white (a gain green, a loss red), in two neat columns. */
	private static void statLines(JPanel p, java.util.List<homeplanet.parser.ShipStats.Line> lines) {
		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		grid.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		grid.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 12, 0, 0));
		java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
		c.insets = new java.awt.Insets(2, 0, 2, 14);
		c.anchor = java.awt.GridBagConstraints.WEST;
		for (int i = 0; i < lines.size(); i++) {
			homeplanet.parser.ShipStats.Line l = lines.get(i);
			JLabel k = new JLabel(l.label);
			k.setFont(MenuTheme.LABEL_FONT);
			k.setForeground(MenuTheme.GREY_GREEN);
			JLabel v = new JLabel(l.value);
			v.setFont(MenuTheme.TEXT_FONT);
			v.setForeground(l.tone > 0 ? MenuTheme.GREEN : l.tone < 0 ? MenuTheme.RED : MenuTheme.WHITE);
			c.gridy = i;
			c.gridx = 0; c.anchor = java.awt.GridBagConstraints.WEST; grid.add(k, c);
			c.gridx = 1; c.anchor = java.awt.GridBagConstraints.EAST; grid.add(v, c);
		}
		grid.setMaximumSize(grid.getPreferredSize());
		p.add(grid);
	}
	private static void statNote(JPanel p, String text) {
		JLabel n = new JLabel("<html><div style='width:200px'><font color='" + MenuTheme.HTML_GREY_GREEN + "'>" + homeplanet.parser.XmlText.text(text) + "</font></div></html>");
		n.setFont(MenuTheme.TEXT_FONT);
		n.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 12, 0, 0));
		n.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		p.add(n);
	}

	private static JPanel column() {
		JPanel c = new JPanel();
		c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
		return c;
	}
	// Systems in FTL's order, with the names players see
	private static final Object[][] SYSTEM_NAMES = {
		{net.blerf.ftl.parser.SavedGameParser.SystemType.SHIELDS, "Shields"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.ENGINES, "Engines"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.OXYGEN, "Oxygen"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.WEAPONS, "Weapons"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.DRONE_CTRL, "Drone Control"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.MEDBAY, "Medbay"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.CLONEBAY, "Clone Bay"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.TELEPORTER, "Crew Teleporter"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.CLOAKING, "Cloaking"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.MIND, "Mind Control"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.HACKING, "Hacking"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.ARTILLERY, "Artillery"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.BATTERY, "Backup Battery"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.PILOT, "Piloting"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.SENSORS, "Sensors"},
		{net.blerf.ftl.parser.SavedGameParser.SystemType.DOORS, "Door System"},
	};
	private static void reportHeading(JPanel p, String text) {
		p.add(Box.createRigidArea(new Dimension(0, 6)));
		p.add(SpaceDockUI.shadowLabel(text, null, true));
	}
	private static JLabel reportRow(JPanel p, javax.swing.Icon icon, String text) {
		JLabel l = new JLabel(text, icon, JLabel.LEFT);
		l.setIconTextGap(6);
		l.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, (icon == null ? 40 : 34 - icon.getIconWidth()), 1, 0));
		l.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		p.add(l);
		return l;
	}
}
