package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;

import homeplanet.core.HomePlanet;
import homeplanet.core.ProfileSwap;
import homeplanet.parser.Career;
import homeplanet.parser.CareerRules;
import homeplanet.parser.Clearance;
import homeplanet.parser.XmlText;
import homeplanet.vault.Vault;

/**
 * The Immersive Mode briefing, in three pages: what changes, the career's choices, and what to do before beginning.
 * Confirm is on the last page only; Cancel on every one. The caller does the switching.
 */
final class ImmersiveBriefing extends JDialog {
	static final Color HEAD = MenuTheme.GOLD;
	private static final int TEXT_W = 560;
	private static final String[] TITLES = {"What changes", "Your career", "Before you begin"};

	final JCheckBox own = new JCheckBox("Give Immersive Mode its own FTL profile (recommended)", true);
	final JRadioButton salaryNew = new JRadioButton("Only achievements earned from now on", true);
	final JRadioButton salaryAll = new JRadioButton("Every achievement already in your FTL profile");
	/** The difficulty: Easy, Normal, Hard or Custom (CareerRules.NAMES). */
	private final JRadioButton[] difficulty = new JRadioButton[CareerRules.NAMES.length];
	/** Each rule's level, chosen freely for Custom; the difficulty's otherwise. */
	@SuppressWarnings("unchecked")
	private final JComboBox<String>[] levels = new JComboBox[CareerRules.RULES.length];
	/** For each rule, the level each of its choices stands for (Commission's Normal and Hard are the same, so it offers two). */
	private final int[][] levelOf = new int[CareerRules.RULES.length][];
	boolean confirmed = false;

	private final boolean begun;
	/** Which career: Vault.EASY, NORMAL, HARD (its difficulty) or CUSTOM (any level of each rule). */
	private final String slot;
	private final File immersiveRoot;
	private final CardLayout cards = new CardLayout();
	private final JPanel deck = new JPanel(cards);
	private final JLabel pageTitle = new JLabel(), pageCount = new JLabel();
	private final JButton back = new JButton("< Back"), next = new JButton("Next >"), confirm = new JButton("Confirm"), cancel = new JButton("Cancel");
	private final JLabel summary = new JLabel();
	private int page = 0;

	ImmersiveBriefing(Component owner, boolean begun, File immersiveRoot, File profile, String slot) {
		super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), Vault.title(slot), ModalityType.APPLICATION_MODAL);
		this.begun = begun;
		this.immersiveRoot = immersiveRoot;
		this.slot = slot;
		deck.add(whatChanges(), "0");
		deck.add(career(), "1");
		deck.add(beforeYouBegin(profile), "2");

		JPanel top = new JPanel(new BorderLayout());
		top.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 16));
		pageTitle.setFont(new Font(Font.DIALOG, Font.BOLD, 16));
		pageTitle.setForeground(HEAD);
		top.add(pageTitle, BorderLayout.WEST);
		top.add(pageCount, BorderLayout.EAST);

		for (Component c : deck.getComponents()) ((JPanel) c).add(Box.createVerticalGlue()); // a short page keeps its text at the top
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		for (JButton b : new JButton[] {cancel, back, next, confirm}) buttons.add(b);
		cancel.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		back.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { show(page - 1); } });
		next.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { show(page + 1); } });
		confirm.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { confirmed = true; dispose(); } });

		getContentPane().add(top, BorderLayout.NORTH);
		getContentPane().add(ScreenFit.wrap(deck, 170, owner), BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		show(0);
		pack();
		setResizable(false);
		setLocationRelativeTo(getOwner());
		ScreenFit.keepOnScreen(this);
	}

	private void show(int p) {
		page = Math.max(0, Math.min(2, p));
		if (page == 2) summary.setText(html(summaryText()));
		cards.show(deck, Integer.toString(page));
		pageTitle.setText(TITLES[page]);
		pageCount.setText("Page " + (page + 1) + " of 3");
		back.setVisible(page > 0);
		next.setVisible(page < 2);
		confirm.setVisible(page == 2);
		getRootPane().setDefaultButton(page < 2 ? next : cancel); // Enter never confirms by accident
	}

	// ---- the pages ----

	private JPanel whatChanges() {
		JPanel p = page();
		p.add(section("A fleet of its own", "Each Immersive career (Easy, Normal, Hard and Custom) has a fleet of its own. Your Sandbox fleet (the Space Dock, "
				+ "the Junkyard, the Cargo Hold and their records) is kept exactly as it is, and comes back when you switch to Sandbox Mode. Your designs and remodels are shared by all."
				+ (begun ? "" : " Your career begins with an empty shipyard, a free Kestrel and some scrap in the Cargo Hold (by its difficulty).")));
		p.add(section("The rules", "Set and locked while it's on:",
				"Trading, scrapping and New Journey need a station (a beacon with a store).",
				"Commissioning costs scrap from the Cargo Hold. With no ship left, commission one, sell a hull from the Junkyard, or report for reassignment (surrender the Cargo Hold and the Junkyard for a free new command).",
				"Each ship you unlock in FTL from now on can be commissioned free, once. Locked ships can't be commissioned.",
				"Fees and prices are set by the career's difficulty, chosen on the next page: a New Journey, taking systems off, missiles and drone parts sold. Stored systems sell at half their price.",
				"Lost ships stay lost: no restoring earlier versions, no recovering, and a report for reassignment is final."));
		p.add(section("Rank", "You start as a Commander.",
				"Captain: design ships, remodel, overhaul, commission custom ships. " + oneLine(Clearance.HOW_CAPTAIN),
				"Commodore: the Federation's artillery. " + oneLine(Clearance.HOW_COMMODORE),
				"The plans for the Rebel Flagship's weapons: earn Rule Ten: Greed is Eternal."));
		p.add(section("Transmissions and the stipend", "An inbox on the Space Dock brings commission orders, promotions and a reward for each FTL achievement "
				+ "earned from now on. Every 30 to 60 beacons your ships explore (by difficulty), a stipend of " + Career.STIPEND_BASE
				+ " scrap, plus 1 to 3 more for each achievement counted (by rank), is paid into the Cargo Hold."));
		return p;
	}

	private JPanel career() {
		JPanel p = page();
		for (Component c : new Component[] {own, salaryNew, salaryAll}) ((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
		if (!begun) {
			p.add(heading("Choices fixed once made"));
			own.setToolTipText("Your current profile is set aside, not deleted, and comes back when you return to Sandbox Mode");
			p.add(own);
			p.add(note("FTL starts a fresh profile: every ship locked but the Kestrel, no achievements. Your own comes back when you return to Sandbox Mode."));
			p.add(label("The stipend counts:"));
			ButtonGroup g = new ButtonGroup();
			g.add(salaryNew);
			g.add(salaryAll);
			p.add(salaryNew);
			p.add(salaryAll);
			p.add(note("A fresh profile has no achievements yet, so with its own profile the stipend counts those earned from now on."));
			ActionListener sync = new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					salaryAll.setEnabled(!own.isSelected()); // a fresh profile has none to count
					if (own.isSelected()) salaryNew.setSelected(true);
				}
			};
			own.addActionListener(sync);
			sync.actionPerformed(null);
		} else {
			p.add(heading("Your career continues"));
			p.add(note("Your Immersive career continues where you left it" + (Career.ownProfile(immersiveRoot) ? ", with its own FTL profile" : "")
					+ ". Its choices were fixed when it began."));
		}
		p.add(Box.createRigidArea(new Dimension(1, 8)));
		boolean custom = Vault.CUSTOM.equals(slot);
		CareerRules was = begun ? Career.rules(immersiveRoot) : custom ? new CareerRules(CareerRules.CUSTOM, CareerRules.of(CareerRules.NORMAL).levels()) : CareerRules.of(slot);
		p.add(heading("Difficulty: " + was.title() + (begun ? "" : custom ? " (choose each rule; fixed once chosen)" : "")));
		JPanel pick = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		pick.setAlignmentX(Component.LEFT_ALIGNMENT);
		ButtonGroup dg = new ButtonGroup();
		String[] titles = {"Easy", "Normal", "Hard", "Custom"};
		ActionListener chosen = new ActionListener() { public void actionPerformed(ActionEvent e) { syncLevels(); } };
		for (int i = 0; i < difficulty.length; i++) {
			difficulty[i] = new JRadioButton(titles[i], CareerRules.NAMES[i].equals(was.name) || (i == 3 && CareerRules.EARLIER.equals(was.name)));
			difficulty[i].setEnabled(!begun);
			difficulty[i].addActionListener(chosen);
			dg.add(difficulty[i]);
			pick.add(difficulty[i]);
			pick.add(Box.createHorizontalStrut(14));
		}
		pick.setMaximumSize(pick.getPreferredSize());
		// the career's slot decides its difficulty; the buttons only keep the table in step
		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 0));
		java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
		c.anchor = java.awt.GridBagConstraints.WEST;
		c.insets = new java.awt.Insets(1, 0, 1, 12);
		for (int i = 0; i < levels.length; i++) {
			java.util.List<String> words = new java.util.ArrayList<String>();
			java.util.List<Integer> lv = new java.util.ArrayList<Integer>();
			for (int l = 0; l < 3; l++) if (!words.contains(CareerRules.LEVELS[i][l])) { words.add(CareerRules.LEVELS[i][l]); lv.add(l); }
			if (i == CareerRules.VICTORY && was.level(i) == CareerRules.OWN_CHOICE) { words.add(was.words(i)); lv.add(CareerRules.OWN_CHOICE); }
			levelOf[i] = new int[lv.size()];
			for (int k = 0; k < levelOf[i].length; k++) levelOf[i][k] = lv.get(k);
			levels[i] = new JComboBox<String>(words.toArray(new String[0]));
			levels[i].setSelectedIndex(Math.max(0, lv.indexOf(was.level(i))));
			c.gridx = 0; c.gridy = i;
			grid.add(new JLabel(CareerRules.RULES[i]), c);
			c.gridx = 1;
			grid.add(levels[i], c);
		}
		grid.setMaximumSize(grid.getPreferredSize());
		p.add(grid);
		p.add(note(begun ? "Its rules were fixed when it began. To begin " + Vault.title(slot) + " afresh (and, for Custom, choose its rules again), end this career in Settings > Switch Game Mode: a copy is kept." : (custom ? "Choose each rule's level. " : "") + "Every other rule is The Federation Home Planet's, the same at every difficulty. "
				+ "A rescue brings her back as she was moments before the final engagement; The Home Planet Station must be open while you play."));
		syncLevels();
		return p;
	}
	/** The difficulty's levels in the table; Custom leaves them to the player (and a career already begun, to no one). */
	private void syncLevels() {
		int d = chosen();
		boolean custom = d == 3;
		for (int i = 0; i < levels.length; i++) {
			if (!custom && !begun) {
				int k = 0;
				while (k + 1 < levelOf[i].length && levelOf[i][k + 1] <= d) k++; // Commission offers two: Hard is Normal's
				levels[i].setSelectedIndex(k);
			}
			levels[i].setEnabled(custom && !begun);
		}
	}
	private int chosen() {
		for (int i = 0; i < difficulty.length; i++) if (difficulty[i].isSelected()) return i;
		return 1;
	}
	/** The difficulty chosen, with its levels. */
	CareerRules rules() {
		int d = chosen();
		if (d < 3) return CareerRules.of(CareerRules.NAMES[d]);
		int[] lv = new int[levels.length];
		for (int i = 0; i < lv.length; i++) lv[i] = levelOf[i][Math.max(0, levels[i].getSelectedIndex())];
		return new CareerRules(CareerRules.CUSTOM, lv);
	}

	private JPanel beforeYouBegin(final File profile) {
		JPanel p = page();
		p.add(section("Steam Cloud", "If your FTL is the Steam version, turn off Steam Cloud for FTL first: right-click FTL in your Steam library, "
				+ "Properties, General, and untick keeping saves in the Steam Cloud. With it on, Steam can bring back an old profile over the one in use, "
				+ "or a docked ship as a copy."));
		if (profile != null && (begun ? !Career.ownProfile(immersiveRoot) : true)) {
			p.add(section("Your FTL profile", "Achievements and ships already earned in FTL don't bring rewards or commission orders; only what you earn from now on does. "
					+ "Back up your profile first: it's " + profile.getName() + " in " + profile.getParent() + "."));
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.setBorder(BorderFactory.createEmptyBorder(0, 16, 10, 0));
			JButton backup = new JButton("Back up my FTL profile");
			backup.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					try {
						File to = ProfileSwap.backup(Vault.get().saves, Vault.get().shared);
						JOptionPane.showMessageDialog(ImmersiveBriefing.this, "Your FTL profile was copied to:\n" + to, "Back up my FTL profile", JOptionPane.INFORMATION_MESSAGE);
					} catch (IOException ex) {
						HomePlanet.showErrorDialog("The Home Planet Station could not back up your FTL profile:\n" + ex.getMessage());
					}
				}
			});
			row.add(backup);
			row.setMaximumSize(row.getPreferredSize()); // no taller than its button
			p.add(row);
		}
		p.add(heading("Your choices"));
		summary.setAlignmentX(Component.LEFT_ALIGNMENT);
		summary.setFont(summary.getFont().deriveFont(Font.PLAIN));
		summary.setBorder(BorderFactory.createEmptyBorder(2, 16, 8, 0));
		p.add(summary);
		p.add(note("Confirm to enter Immersive Mode. FTL must be closed."));
		return p;
	}
	private String summaryText() {
		StringBuilder sb = new StringBuilder();
		if (!begun) {
			sb.append("• ").append(own.isSelected() ? "Its own FTL profile (a fresh one)" : "Your current FTL profile").append("<br>");
			sb.append("• The stipend counts ").append(salaryAll.isSelected() && !own.isSelected() ? "every achievement in your profile" : "achievements earned from now on").append("<br>");
		} else {
			sb.append("• Your career continues as it began (").append(Career.rules(immersiveRoot).title()).append(")");
		}
		if (begun) return sb.toString();
		CareerRules r = rules();
		sb.append("• Difficulty: ").append(r.title());
		for (int i = 0; i < CareerRules.RULES.length; i++) sb.append("<br>&nbsp;&nbsp;&nbsp;").append(XmlText.text(CareerRules.RULES[i])).append(": ").append(XmlText.text(r.words(i)));
		return sb.toString();
	}

	// ---- layout ----

	private static JPanel page() {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 16));
		return p;
	}
	/** A headed section: its text, then its points, if any. */
	private static JPanel section(String title, String text, String... points) {
		JPanel s = new JPanel();
		s.setLayout(new BoxLayout(s, BoxLayout.Y_AXIS));
		s.setAlignmentX(Component.LEFT_ALIGNMENT);
		s.add(heading(title));
		StringBuilder sb = new StringBuilder(XmlText.text(text));
		for (String pt : points) sb.append("<br>&bull; ").append(XmlText.text(pt));
		s.add(note(sb.toString(), true));
		return s;
	}
	private static JLabel heading(String s) {
		JLabel l = new JLabel(s);
		l.setFont(l.getFont().deriveFont(Font.BOLD));
		l.setForeground(HEAD);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		return l;
	}
	private static JLabel label(String s) {
		JLabel l = new JLabel(s);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		return l;
	}
	private static JLabel note(String s) { return note(XmlText.text(s), true); }
	private static JLabel note(String htmlText, boolean indent) {
		JLabel l = new JLabel(html(htmlText));
		l.setFont(l.getFont().deriveFont(Font.PLAIN));
		l.setBorder(BorderFactory.createEmptyBorder(0, indent ? 16 : 0, 8, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}
	private static String html(String s) { return "<html><div style='width:" + TEXT_W + "px'>" + s + "</div></html>"; }
	private static String oneLine(String s) { return s.replace("\n", " "); }
}
