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
	static int show(Component parent, CrewState c, boolean laidUp, Object[] options) {
		return JOptionPane.showOptionDialog(parent, new CrewReport(c, laidUp), "Report for crewman " + c.getName(),
				JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
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
			g.setFont(base.deriveFont(Font.PLAIN, 12f));
			g.setColor(MenuTheme.TEXT);
			g.drawString(SKILL_NAMES[i], 16, y);
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
