package homeplanet.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.blerf.ftl.parser.SavedGameParser.CrewState;

import homeplanet.model.Crew;
import homeplanet.model.Skills;

/**
 * A crew member's report, drawn as the expedition picker's cards: portrait, name, race and sex, health (or the
 * infirmary), the six skills (a pip a level, green then gold as FTL marks them, a bar toward the next, the points), and
 * the service record. One window wherever a crew member's report opens.
 */
final class CrewReport extends JPanel {
	static final String[] SKILL_NAMES = {"Piloting", "Engines", "Shields", "Weapons", "Repair", "Combat"};
	/** Health as the station draws it: green for what's left, red for the rest; purple for the infirmary. */
	static final Color HEALTH = new Color(120, 230, 120), HURT = new Color(225, 70, 55), INFIRMARY = new Color(170, 110, 230);
	private static final Color TRACK = new Color(70, 82, 92), RIM = new Color(214, 230, 222, 90);
	private final CrewState c;
	private final boolean laidUp;

	CrewReport(CrewState c, boolean laidUp) {
		this.c = c; this.laidUp = laidUp;
		setOpaque(true);
		setBackground(MenuTheme.BG);
		setBorder(BorderFactory.createLineBorder(RIM));
	}

	/** Shows the report with these buttons; returns the one pressed (JOptionPane's numbering). */
	static int show(Component parent, CrewState c, boolean laidUp, Object[] options) { return show(parent, c, laidUp, options, null); }
	/** Promote pressed where the caller promotes them itself ({@link #show(Component, CrewState, boolean, Object[], String)}). */
	static final int PROMOTE = -3;
	/**
	 * The same; with a rank due, a Promote button (heromedel, 5.52). {@code inCaller} null: the station promotes them in
	 * their save (where it may; otherwise the button is off and says why). "" : the caller does it (the Cargo Bay, with
	 * its saves in hand), and gets {@link #PROMOTE}; any other text: the button is off, and that says why.
	 */
	static int show(Component parent, CrewState c, boolean laidUp, Object[] options, String inCaller) {
		// one of the fleet's own: Crew Log... opens their whole career (heromedel, 5.41); the caller's own buttons keep their numbers
		homeplanet.vault.Vault v = homeplanet.vault.Vault.isOpen() ? homeplanet.vault.Vault.get() : null;
		int id = v != null ? homeplanet.vault.CrewRegister.identify(v, c) : -1;
		int rank = id > 0 || "".equals(inCaller) ? homeplanet.model.Rank.due(c) : -1;
		String off = null;
		if (rank >= 0 && inCaller == null) {
			homeplanet.vault.CrewRegister.Member m = null;
			for (homeplanet.vault.CrewRegister.Member x : homeplanet.vault.CrewRegister.members(v)) if (x.id == id) m = x;
			off = m == null ? "Not on the station's records yet." : homeplanet.vault.CrewRegister.cannotPromote(v, m);
		} else if (rank >= 0 && !inCaller.isEmpty()) off = inCaller;
		final java.util.List<Object> labels = new java.util.ArrayList<Object>(java.util.Arrays.asList(options));
		if (id > 0) labels.add("Crew Log...");
		if (rank >= 0) labels.add("Promote");
		final JOptionPane pane = new JOptionPane(new CrewReport(c, laidUp), JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
		Object[] buttons = new Object[labels.size()];
		for (int i = 0; i < buttons.length; i++) {
			final javax.swing.JButton btn = new javax.swing.JButton(String.valueOf(labels.get(i)));
			final int n = i;
			btn.addActionListener(new java.awt.event.ActionListener() { public void actionPerformed(java.awt.event.ActionEvent e) { pane.setValue(Integer.valueOf(n)); } });
			if (labels.get(i).equals("Promote")) {
				btn.setToolTipText(off == null ? homeplanet.model.Rank.TOOLTIP : "<html>" + off + "<br>" + homeplanet.model.Rank.TOOLTIP + "</html>");
				btn.setEnabled(off == null);
			}
			buttons[i] = btn;
		}
		pane.setOptions(buttons);
		pane.setInitialValue(buttons[0]);
		javax.swing.JDialog d = pane.createDialog(parent, "Report for crewman " + c.getName());
		d.setVisible(true);
		d.dispose();
		Object v0 = pane.getValue();
		int r = v0 instanceof Integer ? (Integer) v0 : JOptionPane.CLOSED_OPTION;
		if (id > 0 && r == options.length) { SettingsDialog.openCrewLog(parent, id); return JOptionPane.CLOSED_OPTION; }
		if (rank >= 0 && r == labels.size() - 1) {
			if (inCaller != null) return PROMOTE;
			try {
				String now = homeplanet.vault.CrewRegister.promote(v, id);
				JOptionPane.showMessageDialog(parent, c.getName() + " is promoted to " + homeplanet.model.Rank.TITLE[rank] + ": the roster now lists " + now + ".", "Promoted", JOptionPane.INFORMATION_MESSAGE);
				SpaceDockUI dock = (SpaceDockUI) javax.swing.SwingUtilities.getAncestorOfClass(SpaceDockUI.class, parent);
				if (dock != null) dock.init(); // the new name in her crew list
			} catch (java.io.IOException e) {
				homeplanet.core.HomePlanet.showErrorDialog("The Home Planet Station could not promote " + c.getName() + ":\n" + e.getMessage());
			}
			return JOptionPane.CLOSED_OPTION;
		}
		return r;
	}

	@Override public Dimension getPreferredSize() { return new Dimension(330, 330); }

	@Override protected void paintComponent(Graphics g0) {
		super.paintComponent(g0);
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		Font base = getFont();
		Icon face = IconFactory.crewPortrait(c, 56);
		face.paintIcon(this, g, 14, 14);
		g.setFont(base.deriveFont(Font.BOLD, 15f));
		g.setColor(MenuTheme.WHITE);
		g.drawString(c.getName(), 84, 32);
		g.setFont(base.deriveFont(Font.PLAIN, 12f));
		g.setColor(MenuTheme.GREY_GREEN);
		g.drawString(Crew.raceTitle(c) + ", " + (c.isMale() ? "male" : "female"), 84, 50);
		int max = c.getRace() == null ? 100 : c.getRace().getMaxHealth();
		if (laidUp) {
			g.setColor(INFIRMARY); g.fillRect(84, 58, 90, 4);
			g.drawString("In the infirmary", 182, 63);
		} else if (c.getHealth() < max) {
			g.setColor(HURT); g.fillRect(84, 58, 90, 4);
			g.setColor(HEALTH); g.fillRect(84, 58, Math.max(1, 90 * c.getHealth() / max), 4);
			g.setColor(MenuTheme.GREY_GREEN);
			g.drawString("Injured", 182, 63);
		}
		int[] levels = Crew.skillLevels(c);
		int y = 92;
		heading(g, "Skills", y - 8);
		for (int i = 0; i < 6; i++) {
			y += 20;
			Icon skill = IconFactory.skillIcon(i, levels[i], 14); // FTL's own skill icon, tinted by level (5.41)
			if (skill != null) skill.paintIcon(this, g, 14, y - 12);
			g.setFont(base.deriveFont(Font.PLAIN, 12f));
			g.setColor(MenuTheme.TEXT);
			g.drawString(SKILL_NAMES[i], skill != null ? 33 : 16, y);
			for (int k = 0; k < 2; k++) { // a pip a level: green for the first, gold for the second
				g.setColor(k < levels[i] ? (k == 0 ? HEALTH : MenuTheme.GOLD) : TRACK);
				g.fillRect(100 + k * 14, y - 9, 10, 9);
			}
			int iv = Skills.interval(c, i), pts = Skills.points(c, i);
			float toNext = levels[i] >= 2 ? 1f : Math.max(0f, Math.min(1f, (pts - levels[i] * iv) / (float) iv));
			g.setColor(TRACK); g.fillRect(134, y - 6, 110, 3);
			g.setColor(levels[i] >= 2 ? MenuTheme.GOLD : new Color(160, 190, 175)); g.fillRect(134, y - 6, Math.round(110 * toNext), 3);
			g.setColor(MenuTheme.DIM);
			g.setFont(base.deriveFont(Font.PLAIN, 11f));
			String p = levels[i] >= 2 ? "mastered" : pts + "/" + (iv * (levels[i] + 1));
			g.drawString(p, 252, y);
		}
		y += 34;
		heading(g, "Service", y - 8);
		String[][] service = {{"Jumps survived", "" + c.getJumpsSurvived()}, {"Repairs", "" + c.getRepairs()}, {"Combat kills", "" + c.getCombatKills()},
				{"Piloted evasions", "" + c.getPilotedEvasions()}, {"Skill masteries", "" + c.getSkillMasteriesEarned()}};
		for (int i = 0; i < service.length; i++) {
			int col = i % 2, row = i / 2, x = 16 + col * 156, yy = y + 20 + row * 20;
			g.setFont(base.deriveFont(Font.PLAIN, 12f));
			g.setColor(MenuTheme.GREY_GREEN);
			g.drawString(service[i][0], x, yy);
			g.setColor(MenuTheme.WHITE);
			g.drawString(service[i][1], x + 112, yy);
		}
		g.dispose();
	}
	private void heading(Graphics2D g, String s, int y) {
		g.setFont(getFont().deriveFont(Font.BOLD, 12f));
		g.setColor(MenuTheme.GOLD);
		g.drawString(s, 16, y);
		g.setColor(new Color(MenuTheme.GOLD.getRed(), MenuTheme.GOLD.getGreen(), MenuTheme.GOLD.getBlue(), 90));
		g.setStroke(new BasicStroke(1f));
		g.drawLine(16 + g.getFontMetrics().stringWidth(s) + 8, y - 4, getWidth() - 16, y - 4);
	}

	/** A crew icon with a thin health bar under it, as the Cargo Bay draws one, for lists made of labels. */
	static Icon withHealth(final Icon icon, CrewState c) {
		int max = c.getRace() == null ? 100 : c.getRace().getMaxHealth();
		if (icon == null || c.getHealth() >= max) return icon;
		final float fill = c.getHealth() / (float) max;
		return new Icon() {
			public int getIconWidth() { return Math.max(icon.getIconWidth(), 24); }
			public int getIconHeight() { return icon.getIconHeight() + 4; }
			public void paintIcon(Component cmp, Graphics g, int x, int y) {
				int w = getIconWidth();
				icon.paintIcon(cmp, g, x + (w - icon.getIconWidth()) / 2, y);
				int bx = x + (w - 24) / 2, by = y + icon.getIconHeight() + 1;
				g.setColor(HURT); g.fillRect(bx, by, 24, 2);
				g.setColor(HEALTH); g.fillRect(bx, by, Math.max(1, Math.round(24 * fill)), 2);
			}
		};
	}
}
