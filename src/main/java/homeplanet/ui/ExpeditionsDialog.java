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
 * The expeditions board: three postings the commander can sign on to with crew from the Cargo Hold, and hiring at the
 * foot. An expedition plays out as FTL plays a beacon: the situation, numbered choices (blue where a crew member's
 * race opens one), the outcome, "Continue...".
 */
final class ExpeditionsDialog extends JDialog {
	private final JPanel cols = new JPanel(new GridLayout(1, Expeditions.POSTINGS, 12, 0));
	private final JLabel foot = new JLabel();
	private final JButton hireBtn = new JButton();
	/** Did anything change in the Cargo Hold (the Space Dock redraws)? */
	boolean changed = false;
	/** Marks an expedition's own pop-ups (for the harness, which can't go by their titles). */
	static final String EXPEDITION = "homeplanet.expedition";
	/** For the harness: the random rolls. */
	static Random rng = new Random();
	/** An expedition is under way: Long Range messages pop up over it, and hails are turned away (it can't be left halfway). */
	private static volatile boolean underWay = false;
	static boolean underWay() { return underWay; }
	/** What a hailing commander is told while an expedition is under way, or null. */
	static String awayNotice(String commander) { return underWay ? commander + " is away on an expedition. Hail again shortly." : null; }
	/** FTL's blue for an option a crew member's race opens. */
	static final String BLUE = "#6ab8ff";

	/**
	 * The event box: the station's own dark panel, FTL's type (JustinFont, from ftl.dat), words wrapped at TEXT_W, a line
	 * every LINE, the choices two lines under the words and CHOICE_GAP apart, the box as tall as what's in it.
	 */
	static final int TEXT_W = 540, PAD = 18, LINE = 17, CHOICE_GAP = 9, MIN_H = 200;
	static final Color BOX_BG = MenuTheme.BG, WORDS = MenuTheme.TEXT,
			HOVER = new Color(255, 214, 90), RACE = new Color(106, 184, 255);

	/**
	 * The board; when the commander signs on, it closes while the job plays (in its own windows), and opens again,
	 * fresh, when the job is over. Did anything change in the Cargo Hold?
	 */
	static boolean open(java.awt.Component owner) {
		boolean changed = false;
		while (true) {
			ExpeditionsDialog d = new ExpeditionsDialog(owner);
			d.setVisible(true);
			changed |= d.changed;
			if (d.signedOn == null) return changed;
			changed |= play(owner, d.signedOn, Vault.get());
			afterJob(owner);
		}
	}
	/**
	 * A beacon has passed with the job: the station's round (who's out of the infirmary, a ransom asked or run out)
	 * before the board reopens, as it would at a look at the Space Dock. Jobs follow one another without that look.
	 */
	static void afterJob(java.awt.Component owner) {
		if (owner instanceof SpaceDockUI) ((SpaceDockUI) owner).timeRound(false);
	}
	/** The job signed on for, to play once the board has closed (null: the board was just closed). */
	Expeditions.Run signedOn;

	private ExpeditionsDialog(java.awt.Component owner) {
		super(javax.swing.SwingUtilities.getWindowAncestor(owner), "Expeditions", ModalityType.APPLICATION_MODAL);
		JPanel body = new JPanel(new BorderLayout(0, 10));
		body.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
		body.add(new JLabel("<html><div style='width:720px'>Jobs posted for crews without a ship of their own. Sign on, and take up to "
				+ Expeditions.PARTY_MAX + " from the Cargo Hold with you. The pay is what the job pays, if it pays. Not everyone comes back.</div></html>"), BorderLayout.NORTH);
		body.add(cols, BorderLayout.CENTER);
		JPanel south = new JPanel(new BorderLayout(10, 0));
		south.add(foot, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
		hireBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { hire(); } });
		buttons.add(hireBtn);
		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		buttons.add(close);
		south.add(buttons, BorderLayout.EAST);
		body.add(south, BorderLayout.SOUTH);
		getContentPane().add(body);
		getRootPane().setDefaultButton(close);
		fill();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setResizable(false);
		setLocationRelativeTo(getOwner());
		ScreenFit.keepOnScreen(this);
	}

	private void fill() {
		Vault v = Vault.get();
		List<Expeditions.Posting> board = Expeditions.board(v);
		cols.removeAll();
		for (int i = 0; i < board.size(); i++) cols.add(card(i, board.get(i)));
		int inHold = 0;
		try { inHold = Expeditions.holdCrew(v).size(); } catch (IOException e) { }
		List<String> laidUp = new ArrayList<String>();
		for (Expeditions.Patient x : Expeditions.infirmary(v)) laidUp.add(x.name);
		int fleet = Expeditions.fleetCrew(v), cost = Expeditions.hireCost(fleet);
		foot.setText("<html>Crew in the Cargo Hold: " + inHold + ".&nbsp;&nbsp; The Cargo Hold holds " + v.storageScrap() + " scrap."
				+ (laidUp.isEmpty() ? "" : "<br>In the infirmary: " + XmlText.text(String.join(", ", laidUp)) + ".") + "</html>");
		hireBtn.setText(fleet == 0 ? "Post a promise of adventure" : "Post for volunteers: " + cost + " scrap");
		hireBtn.setToolTipText(fleet == 0 ? "Free: with no crew anywhere, a promise of adventure is all you can offer. Someone may answer."
				: "5 scrap for each crew member in your fleet (" + fleet + "), at most 60: paid whether or not anyone answers. New crew wait in the Cargo Hold.");
		hireBtn.setEnabled(cost <= v.storageScrap());
		cols.revalidate();
		cols.repaint();
		pack();
	}
	private JPanel card(final int slot, Expeditions.Posting x) {
		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(MenuTheme.GREY_GREEN), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		JLabel words = new JLabel("<html><div style='width:210px'><font color='" + MenuTheme.HTML_GOLD + "'><b>" + XmlText.text(x.title()) + "</b></font><br><br>"
				+ XmlText.text(x.text) + "</div></html>");
		words.setVerticalAlignment(JLabel.TOP);
		p.add(words, BorderLayout.CENTER);
		JButton send = new JButton("Sign on...");
		send.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { send(slot); } });
		p.add(send, BorderLayout.SOUTH);
		return p;
	}

	private void send(int slot) {
		Vault v = Vault.get();
		List<CrewState> crew;
		try { crew = Expeditions.holdCrew(v); } catch (IOException e) { HomePlanet.showErrorDialog("The Cargo Hold can't be read:\n" + e.getMessage()); return; }
		if (crew.isEmpty()) {
			JOptionPane.showMessageDialog(this, "There is no crew in the Cargo Hold to take along.\nMove crew there in the Cargo Bay, or post for volunteers.", "Expeditions", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		List<CrewState> party = pickParty(crew);
		if (party == null || party.isEmpty()) return;
		try { signedOn = Expeditions.start(v, slot, party, rng); }
		catch (IOException e) { HomePlanet.showErrorDialog("The expedition could not set out:\n" + e.getMessage()); return; }
		dispose(); // the board steps aside while the job plays
	}
	/**
	 * An expedition, start to finish: the situation and its choices, each outcome, the last with word of anyone carried
	 * to the infirmary. Its windows can't be closed, only answered. Did it reach the Cargo Hold?
	 */
	static boolean play(java.awt.Component owner, Expeditions.Run run, Vault v) {
		String title = run.posting.title();
		underWay = true;
		try {
			while (!run.over()) {
				List<Expeditions.Choice> choices = run.choices();
				int c = -1;
				while (c < 0) c = ask(owner, run.text(), labels(run, choices), blue(choices), title); // an expedition can't be walked away from halfway
				String said = run.choose(choices.get(c));
				if (!run.over()) continue;
				String home;
				try { home = Expeditions.finish(v, run); }
				catch (IOException e) {
					ask(owner, said, new String[] {"1. Continue..."}, new boolean[1], title);
					HomePlanet.showErrorDialog("The Home Planet Station could not record the expedition; the Cargo Hold is as it was:\n" + e.getMessage());
					return false;
				}
				ask(owner, home.isEmpty() ? said : said + "\n\n" + home, new String[] {"1. Continue..."}, new boolean[1], title);
				return true;
			}
			return false;
		} finally { underWay = false; }
	}
	private static String[] labels(Expeditions.Run run, List<Expeditions.Choice> choices) {
		String[] out = new String[choices.size()];
		for (int i = 0; i < out.length; i++) out[i] = label(run, choices.get(i), i + 1);
		return out;
	}
	private static boolean[] blue(List<Expeditions.Choice> choices) {
		boolean[] out = new boolean[choices.size()];
		for (int i = 0; i < out.length; i++) out[i] = choices.get(i).race != null;
		return out;
	}
	/**
	 * A screen of the job, laid out as FTL's: the words, two lines down the numbered choices, each a line of words that
	 * lights up under the pointer (blue where a crew member's race opens it), picked by a click or its number key. No
	 * closing it, only a choice. Returns the one taken, or -1.
	 */
	private static int ask(java.awt.Component owner, String text, String[] choices, boolean[] blue, String title) {
		final JOptionPane op = new JOptionPane(null, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[0]);
		// FTL's type where ftl.dat has it for every letter on the screen; the style guide's Sans Serif 12 otherwise
		StringBuilder all = new StringBuilder(text);
		for (String c : choices) all.append(c);
		boolean ftl = FtlFont.BODY.covers(all.toString().replace("\n", ""));
		Box box = new Box();
		box.add(new Words(text, WORDS, ftl));
		box.add(javax.swing.Box.createVerticalStrut(LINE * 2));
		for (int i = 0; i < choices.length; i++) {
			final int n = i;
			Pick b = new Pick(choices[i], blue[i] ? RACE : WORDS, ftl);
			b.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { op.setValue(Integer.valueOf(n)); } });
			if (i > 0) box.add(javax.swing.Box.createVerticalStrut(CHOICE_GAP));
			box.add(b);
			if (i < 9) { // FTL's number keys
				String key = "pick" + (i + 1);
				box.getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke((char) ('1' + i)), key);
				box.getActionMap().put(key, new javax.swing.AbstractAction() { public void actionPerformed(ActionEvent e) { op.setValue(Integer.valueOf(n)); } });
			}
		}
		op.setMessage(box);
		JDialog d = op.createDialog(owner, title);
		d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE); // a choice must be made
		d.getRootPane().putClientProperty(EXPEDITION, Boolean.TRUE);
		d.setVisible(true);
		d.dispose();
		Object v = op.getValue();
		return v instanceof Integer && (Integer) v >= 0 && (Integer) v < choices.length ? (Integer) v : -1;
	}
	/** A choice as it reads, numbered: "2. (Mantis) Krik offers to..." */
	static String label(Expeditions.Run run, Expeditions.Choice c, int n) { return n + ". " + run.label(c); }

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
	private List<CrewState> pickParty(final List<CrewState> crew) {
		JPanel p = new JPanel(new BorderLayout(0, 12));
		JLabel head = new JLabel("Who goes with you? Up to " + Expeditions.PARTY_MAX + " from the Cargo Hold.");
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
			box.setRenderer(new javax.swing.DefaultListCellRenderer() {
				@Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> l, Object v, int idx, boolean sel, boolean foc) {
					super.getListCellRendererComponent(l, v, idx, sel, foc);
					if (v instanceof CrewState) { CrewState c = (CrewState) v; setText(c.getName() + "  (" + homeplanet.model.Crew.raceTitle(c) + ")"); setIcon(IconFactory.crewIcon(c)); }
					else { setText(String.valueOf(v)); setIcon(null); }
					return this;
				}
			});
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
			if (JOptionPane.showOptionDialog(this, p, "Expeditions", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]) != 0) return null;
			List<CrewState> out = new ArrayList<CrewState>();
			for (javax.swing.JComboBox<Object> b : picks) if (b.getSelectedItem() instanceof CrewState && !out.contains(b.getSelectedItem())) out.add((CrewState) b.getSelectedItem());
			if (!out.isEmpty()) return out;
			JOptionPane.showMessageDialog(this, "Choose at least one crew member to go with you.", "Expeditions", JOptionPane.INFORMATION_MESSAGE);
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

	private void hire() {
		Vault v = Vault.get();
		int fleet = Expeditions.fleetCrew(v), cost = Expeditions.hireCost(fleet);
		if (cost > 0 && !HomePlanet.confirmNo(this, "Post for volunteers for " + cost + " scrap from the Cargo Hold?\nThe scrap is spent whether or not anyone answers.", "Expeditions")) return;
		CrewState c;
		try { c = Expeditions.hire(v, rng); }
		catch (IOException e) { HomePlanet.showErrorDialog("The posting was called off. Nothing was changed:\n" + e.getMessage()); fill(); return; }
		changed = true;
		JOptionPane.showMessageDialog(this, c == null ? (cost == 0 ? "No one answered the promise of adventure. It costs nothing to try again." : "No one answered this time.")
				: c.getName() + " (" + homeplanet.model.Crew.raceTitle(c) + ") answered, and is waiting in the Cargo Hold.", "Expeditions", JOptionPane.INFORMATION_MESSAGE);
		fill();
	}
}
