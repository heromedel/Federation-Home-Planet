package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.blerf.ftl.parser.SavedGameParser.CrewState;

import homeplanet.core.HomePlanet;
import homeplanet.parser.Expeditions;
import homeplanet.parser.XmlText;
import homeplanet.vault.Vault;

/**
 * What the crew expeditions' windows share (the old board of jobs went at 5.67): FTL's event box and type for the
 * pop-ups, the crew picker with its crew cards, and the Hire Crew button and its posting for volunteers.
 */
final class ExpeditionsDialog {
	private ExpeditionsDialog() { }
	/** For the harness: the random rolls. */
	static Random rng = new Random();
	/** FTL's blue for an option a crew member's race opens. */
	static final String BLUE = "#6ab8ff";

	/**
	 * The event box: the station's own dark panel, FTL's type (JustinFont, from ftl.dat), words wrapped at TEXT_W, a line
	 * every LINE, the choices two lines under the words and CHOICE_GAP apart, the box as tall as what's in it.
	 */
	static final int TEXT_W = 540, PAD = 18, LINE = 17, CHOICE_GAP = 9, MIN_H = 200;
	static final Color BOX_BG = MenuTheme.BG, WORDS = MenuTheme.TEXT,
			HOVER = new Color(255, 214, 90), RACE = new Color(106, 184, 255);


	/** The event box: the station's dark panel, no rim, its contents one under the other. */
	static final class Box extends JPanel {
		Box() {
			setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD + 4, PAD));
		}
		@Override public java.awt.Dimension getPreferredSize() {
			java.awt.Dimension d = super.getPreferredSize();
			return new java.awt.Dimension(TEXT_W + 2 * PAD, Math.max(MIN_H, d.height));
		}
		@Override protected void paintComponent(java.awt.Graphics g) {
			g.setColor(BOX_BG);
			g.fillRect(0, 0, getWidth(), getHeight());
		}
	}
	/** Words in FTL's type, wrapped to the box (blank lines kept). Its text is the words, for whoever asks. */
	static final class Words extends JLabel {
		final List<String> lines;
		final Color color;
		final boolean ftl;
		Words(String text, Color color, boolean ftl) {
			super(text);
			this.color = color; this.ftl = ftl;
			lines = wrapLines(text, TEXT_W, ftl);
			setAlignmentX(LEFT_ALIGNMENT);
		}
		@Override public java.awt.Dimension getPreferredSize() { return new java.awt.Dimension(TEXT_W, lines.size() * LINE); }
		@Override public java.awt.Dimension getMaximumSize() { return getPreferredSize(); }
		@Override protected void paintComponent(java.awt.Graphics g) {
			for (int i = 0; i < lines.size(); i++) if (!lines.get(i).isEmpty()) draw(g, lines.get(i), color, 0, i * LINE, ftl);
		}
	}
	/** A choice: a line (or two) of words, white, or blue for a race's; gold under the pointer, as FTL's. */
	static final class Pick extends JButton {
		final List<String> lines;
		final Color color;
		final boolean ftl;
		Pick(String text, Color color, boolean ftl) {
			super(text);
			this.color = color; this.ftl = ftl;
			lines = wrapLines(text, TEXT_W - 16, ftl);
			setAlignmentX(LEFT_ALIGNMENT);
			setBorderPainted(false); setContentAreaFilled(false); setFocusPainted(false); setOpaque(false);
			setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			setRolloverEnabled(true);
		}
		@Override public java.awt.Dimension getPreferredSize() { return new java.awt.Dimension(TEXT_W, lines.size() * LINE); }
		@Override public java.awt.Dimension getMaximumSize() { return getPreferredSize(); }
		@Override protected void paintComponent(java.awt.Graphics g) {
			Color c = getModel().isRollover() || getModel().isArmed() ? HOVER : color;
			for (int i = 0; i < lines.size(); i++) draw(g, lines.get(i), c, i == 0 ? 0 : 16, i * LINE, ftl); // a wrapped choice's second line sits under its words, not its number
		}
	}
	/** The style guide's normal text, where FTL's type can't be had. */
	static final java.awt.Font SANS = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12);
	private static final java.awt.FontMetrics SANS_METRICS = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(SANS);
	/** A line of words at (x, y), in FTL's type or the fallback, centred in its LINE. */
	static void draw(java.awt.Graphics g0, String s, Color c, int x, int y, boolean ftl) {
		if (ftl) {
			java.awt.image.BufferedImage img = FtlFont.BODY.render(s, c);
			g0.drawImage(img, x, y + (LINE - img.getHeight()) / 2, null);
			return;
		}
		java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
		g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(SANS);
		g.setColor(c);
		g.drawString(s, x, y + (LINE + SANS_METRICS.getAscent() - SANS_METRICS.getDescent()) / 2);
		g.dispose();
	}
	static int width(String s, boolean ftl) { return ftl ? FtlFont.BODY.width(s) : SANS_METRICS.stringWidth(s); }
	static List<String> wrapLines(String text, int width) { return wrapLines(text, width, true); }
	/** Text cut into lines no wider than this in the type given, at spaces; a line break kept, a blank line between paragraphs. */
	static List<String> wrapLines(String text, int width, boolean ftl) {
		List<String> out = new ArrayList<String>();
		for (String para : text.split("\n", -1)) {
			StringBuilder line = new StringBuilder();
			for (String word : para.split(" ")) {
				if (word.isEmpty()) continue;
				String next = line.length() == 0 ? word : line + " " + word;
				if (line.length() > 0 && width(next, ftl) > width) { out.add(line.toString()); line = new StringBuilder(word); }
				else line = new StringBuilder(next);
			}
			out.add(line.toString());
		}
		return out;
	}
	/** Up to three crew from the Cargo Hold, ticked. */
	/**
	 * Who goes: three seats side by side, each a drop-down of the Cargo Hold's crew (or no one), and under it the one
	 * chosen: portrait, race, health if hurt, and the six skills (a pip a level, a thin bar toward the next). Someone
	 * picked for a place leaves the other places. The first three are picked to begin with.
	 */
	static List<CrewState> pickParty(java.awt.Component owner, String heading, final List<CrewState> crew) {
		JPanel p = new JPanel(new BorderLayout(0, 12));
		JLabel head = new JLabel(heading);
		head.setForeground(MenuTheme.GOLD);
		head.setFont(head.getFont().deriveFont(java.awt.Font.BOLD, 14f));
		p.add(head, BorderLayout.NORTH);
		JPanel slots = new JPanel(new GridLayout(1, Expeditions.PARTY_MAX, 12, 0));
		final List<javax.swing.JComboBox<Object>> picks = new ArrayList<javax.swing.JComboBox<Object>>();
		final List<CrewCard> cards = new ArrayList<CrewCard>();
		final String nobody = "No one";
		for (int i = 0; i < Expeditions.PARTY_MAX; i++) {
			Object[] items = new Object[crew.size() + 1];
			items[0] = nobody;
			for (int k = 0; k < crew.size(); k++) items[k + 1] = crew.get(k);
			final javax.swing.JComboBox<Object> box = new javax.swing.JComboBox<Object>(items);
			box.setRenderer(new CrewRow());
			if (i < crew.size()) box.setSelectedIndex(i + 1);
			final CrewCard card = new CrewCard();
			card.show(box.getSelectedItem() instanceof CrewState ? (CrewState) box.getSelectedItem() : null);
			picks.add(box); cards.add(card);
			JPanel col = new JPanel(new BorderLayout(0, 8));
			JLabel n = new JLabel("Seat " + (i + 1));
			n.setForeground(MenuTheme.GREY_GREEN);
			col.add(n, BorderLayout.NORTH);
			JPanel inner = new JPanel(new BorderLayout(0, 8));
			inner.add(box, BorderLayout.NORTH);
			inner.add(card, BorderLayout.CENTER);
			col.add(inner, BorderLayout.CENTER);
			slots.add(col);
		}
		for (int i = 0; i < picks.size(); i++) {
			final int me = i;
			picks.get(i).addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
				Object chosen = picks.get(me).getSelectedItem();
				if (chosen instanceof CrewState) // one person, one place: they leave any other
					for (int k = 0; k < picks.size(); k++) if (k != me && picks.get(k).getSelectedItem() == chosen) { picks.get(k).setSelectedIndex(0); cards.get(k).show(null); }
				cards.get(me).show(chosen instanceof CrewState ? (CrewState) chosen : null);
			} });
		}
		p.add(slots, BorderLayout.CENTER);
		Object[] opts = {"Set out", "Cancel"};
		while (true) {
			if (JOptionPane.showOptionDialog(owner, p, "Expeditions", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]) != 0) return null;
			List<CrewState> out = new ArrayList<CrewState>();
			for (javax.swing.JComboBox<Object> b : picks) if (b.getSelectedItem() instanceof CrewState && !out.contains(b.getSelectedItem())) out.add((CrewState) b.getSelectedItem());
			if (!out.isEmpty()) return out;
			JOptionPane.showMessageDialog(owner, "Choose at least one crew member to go.", "Expeditions", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	/**
	 * A crew member in the picker's list: portrait, name and race, and after them FTL's icon for each skill they have a
	 * level in, grey for one level, gold for two (heromedel, 5.80), so the list shows who's good at what before a pick.
	 */
	static final class CrewRow extends JPanel implements javax.swing.ListCellRenderer<Object> {
		final JLabel who = new JLabel(), skills = new JLabel();
		CrewRow() {
			super(new BorderLayout(10, 0));
			setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 4));
			add(who, BorderLayout.CENTER);
			add(skills, BorderLayout.EAST);
		}
		@Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> l, Object v, int idx, boolean sel, boolean foc) {
			setOpaque(true);
			setBackground(sel ? l.getSelectionBackground() : l.getBackground());
			who.setForeground(sel ? l.getSelectionForeground() : l.getForeground());
			who.setFont(l.getFont());
			if (v instanceof CrewState) {
				CrewState c = (CrewState) v;
				who.setText(c.getName() + "  (" + homeplanet.model.Crew.raceTitle(c) + ")");
				who.setIcon(IconFactory.crewIcon(c));
				skills.setIcon(IconFactory.skillMarks(homeplanet.model.Crew.skillLevels(c), 12));
			} else {
				who.setText(String.valueOf(v)); who.setIcon(null); skills.setIcon(null);
			}
			return this;
		}
	}

	/** A crew member as the picker shows them: portrait, name and race, health if hurt, and the six skills. */
	static final class CrewCard extends JPanel {
		private static final String[] SKILL_NAMES = {"Piloting", "Engines", "Shields", "Weapons", "Repair", "Combat"};
		private CrewState c;
		CrewCard() { setOpaque(true); setBackground(MenuTheme.BG); setBorder(BorderFactory.createLineBorder(new Color(214, 230, 222, 90))); }
		void show(CrewState who) { c = who; revalidate(); repaint(); }
		@Override public java.awt.Dimension getPreferredSize() { return new java.awt.Dimension(200, 196); }
		@Override protected void paintComponent(java.awt.Graphics g0) {
			super.paintComponent(g0);
			java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
			g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			if (c == null) {
				g.setColor(MenuTheme.DIM);
				g.setFont(getFont());
				String s = "No one";
				g.drawString(s, (getWidth() - g.getFontMetrics().stringWidth(s)) / 2, getHeight() / 2);
				g.dispose();
				return;
			}
			javax.swing.Icon face = IconFactory.crewPortrait(c, 40);
			face.paintIcon(this, g, 10, 10);
			g.setFont(getFont().deriveFont(java.awt.Font.BOLD, 13f));
			g.setColor(MenuTheme.WHITE);
			g.drawString(c.getName(), 58, 26);
			g.setFont(getFont().deriveFont(java.awt.Font.PLAIN, 12f));
			g.setColor(MenuTheme.GREY_GREEN);
			g.drawString(homeplanet.model.Crew.raceTitle(c), 58, 43);
			int max = c.getRace() == null ? 100 : c.getRace().getMaxHealth();
			if (c.getHealth() < max) { // hurt in the game: as the Cargo Bay shows it
				g.setColor(new Color(225, 70, 55)); g.fillRect(58, 49, 60, 3);
				g.setColor(new Color(120, 230, 120)); g.fillRect(58, 49, Math.max(1, 60 * c.getHealth() / max), 3);
			}
			int[] levels = homeplanet.model.Crew.skillLevels(c);
			int y = 70;
			for (int i = 0; i < 6; i++) {
				g.setColor(MenuTheme.TEXT);
				g.drawString(SKILL_NAMES[i], 12, y + 10);
				for (int k = 0; k < 2; k++) { // a pip a level: green for the first, gold for the second, as FTL marks them
					g.setColor(k < levels[i] ? (k == 0 ? new Color(120, 230, 120) : MenuTheme.GOLD) : new Color(70, 82, 92));
					g.fillRect(100 + k * 14, y + 2, 10, 9);
				}
				int iv = homeplanet.model.Skills.interval(c, i), pts = homeplanet.model.Skills.points(c, i);
				float toNext = levels[i] >= 2 ? 1f : Math.max(0f, Math.min(1f, (pts - levels[i] * iv) / (float) iv));
				g.setColor(new Color(70, 82, 92)); g.fillRect(132, y + 5, 56, 3);
				g.setColor(levels[i] >= 2 ? MenuTheme.GOLD : new Color(160, 190, 175)); g.fillRect(132, y + 5, Math.round(56 * toNext), 3);
				y += 20;
			}
			g.dispose();
		}
	}

	/** The volunteer board (shared by every kind of expeditions, and the Space Dock's Hire Crew with none): did anyone join? */
	static boolean hire(java.awt.Component owner) {
		Vault v = Vault.get();
		int fleet = Expeditions.fleetCrew(v), cost = Expeditions.hireCost(fleet), rep = Expeditions.promiseRep(v);
		if (cost > 0 && !HomePlanet.confirmNo(owner, "Post for volunteers for " + cost + " scrap from the Cargo Hold?\nThe scrap is spent whether or not anyone answers.", "Expeditions")) return false;
		if (rep > 0 && !HomePlanet.confirmNo(owner, "Post a promise of adventure for " + rep + " reputation?\nIt is spent whether or not anyone answers.", "Expeditions")) return false;
		CrewState c;
		try { c = Expeditions.hire(v, rng); }
		catch (IOException e) { HomePlanet.showErrorDialog("The posting was called off. Nothing was changed:\n" + e.getMessage()); return false; }
		JOptionPane.showMessageDialog(owner, c == null ? (cost == 0 ? "No one answered the promise of adventure." + (rep > 0 ? "" : " It costs nothing to try again.") : "No one answered this time.")
				: c.getName() + " (" + homeplanet.model.Crew.raceTitle(c) + ") answered, and is waiting in the Cargo Hold.", "Expeditions", JOptionPane.INFORMATION_MESSAGE);
		return true;
	}
	/** The hire button's words and tooltip, for whichever board shows it. */
	static void hireButton(JButton b, Vault v) {
		int fleet = Expeditions.fleetCrew(v), cost = Expeditions.hireCost(fleet), rep = Expeditions.promiseRep(v);
		b.setText(fleet == 0 ? "Post a promise of adventure" + (rep > 0 ? ": " + rep + " reputation" : "") : "Post for volunteers: " + cost + " scrap");
		b.setToolTipText(fleet == 0 ? (rep > 0 ? rep + " reputation, spent whether or not anyone answers: " : "Free: ") + "with no crew anywhere, a promise of adventure is all you can offer. Someone may answer."
				: "5 scrap for each crew member in your fleet (" + fleet + "), at most 60: paid whether or not anyone answers. New crew wait in the Cargo Hold.");
		b.setEnabled(cost <= v.storageScrap() && rep <= homeplanet.vault.Reputation.total(v));
	}
}
