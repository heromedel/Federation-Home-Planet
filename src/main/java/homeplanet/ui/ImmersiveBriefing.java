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
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;

import homeplanet.core.HomePlanet;
import homeplanet.core.ProfileSwap;
import homeplanet.parser.Career;
import homeplanet.parser.Clearance;
import homeplanet.parser.FinalVictory;
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
	final JRadioButton[] victory = new JRadioButton[FinalVictory.CHOICES.length];
	/** The one house rule Immersive Mode leaves to the player, chosen here rather than on a screen of its own. */
	final JCheckBox scrapKeeps = new JCheckBox("Allow stripping when scrapping: her systems can go to the Cargo Bay, 10 scrap each", HomePlanet.stripAllowed);
	boolean confirmed = false;

	private final boolean begun;
	private final File immersiveRoot;
	private final CardLayout cards = new CardLayout();
	private final JPanel deck = new JPanel(cards);
	private final JLabel pageTitle = new JLabel(), pageCount = new JLabel();
	private final JButton back = new JButton("< Back"), next = new JButton("Next >"), confirm = new JButton("Confirm"), cancel = new JButton("Cancel");
	private final JLabel summary = new JLabel();
	private int page = 0;

	ImmersiveBriefing(Component owner, boolean begun, File immersiveRoot, File profile) {
		super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), "Immersive Mode", ModalityType.APPLICATION_MODAL);
		this.begun = begun;
		this.immersiveRoot = immersiveRoot;
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
		p.add(section("A fleet of its own", "Your current fleet (the Space Dock, the Junkyard, the Cargo Hold and their records) is kept exactly as it is, "
				+ "and comes back when you return to Sandbox Mode. Your designs and remodels are shared by both."
				+ (begun ? "" : " Your career begins with an empty shipyard, a free Kestrel and " + Career.STARTING_SCRAP + " scrap in the Cargo Hold.")));
		p.add(section("The rules", "Set and locked while it's on:",
				"Trading, scrapping and New Journey need a station (a beacon with a store).",
				"Commissioning costs scrap from the Cargo Hold, at full price. With no ship left, commission one or report for reassignment (surrender the Cargo Hold and the Junkyard for a free new command).",
				"Each ship you unlock in FTL from now on can be commissioned free, once. Locked ships can't be commissioned.",
				"A New Journey costs " + homeplanet.core.Economy.IMMERSIVE_JOURNEY_FEE + " scrap. Missiles and drone parts sell at 25%, stored systems at half their price.",
				"Lost ships stay lost: no restoring earlier versions, no recovering, and a report for reassignment is final."));
		p.add(section("Rank", "You start as a Commander.",
				"Captain: design ships, remodel, overhaul, commission custom ships. " + oneLine(Clearance.HOW_CAPTAIN),
				"Commodore: the Federation's artillery. " + oneLine(Clearance.HOW_COMMODORE),
				"The plans for the Rebel Flagship's weapons: earn Rule Ten: Greed is Eternal."));
		p.add(section("Transmissions and the stipend", "An inbox on the Space Dock brings commission orders, promotions and a reward for each FTL achievement "
				+ "earned from now on. Every " + Career.SECTORS_PER_MONTH + " sectors your ships travel, a stipend of " + Career.STIPEND_BASE
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
		p.add(heading("After a final victory (you can change this later in Settings)"));
		String was = begun ? Career.finalVictory(immersiveRoot) : FinalVictory.NOTHING;
		ButtonGroup vg = new ButtonGroup();
		for (int i = 0; i < victory.length; i++) {
			victory[i] = new JRadioButton(FinalVictory.label(FinalVictory.CHOICES[i]), FinalVictory.CHOICES[i].equals(was));
			victory[i].setAlignmentX(Component.LEFT_ALIGNMENT);
			vg.add(victory[i]);
			p.add(victory[i]);
		}
		if (vg.getSelection() == null) victory[0].setSelected(true);
		p.add(note("Ships are precious in Immersive Mode. A rescue brings her back as she was moments before the final engagement, "
				+ "or The Federation Home Planet buys her for the museum at her full value; a reward pays her full value instead. "
				+ "The Home Planet Station must be open while you play."));
		p.add(Box.createRigidArea(new Dimension(1, 8)));
		p.add(heading("Scrapping a ship (you can change this later in Settings)"));
		scrapKeeps.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(scrapKeeps);
		p.add(note("Otherwise her systems are scrapped with her. Every other rule is The Federation Home Planet's."));
		return p;
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
			sb.append("• Your career continues as it began<br>");
		}
		for (int i = 0; i < victory.length; i++) if (victory[i].isSelected()) sb.append("• After a final victory: ").append(XmlText.text(FinalVictory.label(FinalVictory.CHOICES[i]))).append("<br>");
		sb.append("• Scrapping a ship ").append(scrapKeeps.isSelected() ? "may strip her systems into the Cargo Bay" : "scraps her systems too");
		return sb.toString();
	}

	/** The final-victory choice made. */
	String victoryChoice() {
		for (int i = 0; i < victory.length; i++) if (victory[i].isSelected()) return FinalVictory.CHOICES[i];
		return FinalVictory.NOTHING;
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
