package homeplanet.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;

import homeplanet.core.GameGuard;
import homeplanet.core.HomePlanet;
import homeplanet.core.ProfileSwap;
import homeplanet.parser.Career;
import homeplanet.parser.Clearance;
import homeplanet.parser.FinalVictory;
import homeplanet.parser.UnlockGrants;
import homeplanet.parser.Unlocks;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * Entering and leaving Immersive Mode: the briefing (what it is, the career's choices, the FTL profile), then the
 * switch to the other fleet, and its profile, at once. FTL must be closed.
 */
public final class ImmersiveDialog {
	private ImmersiveDialog() { }

	private static final String STEAM_CLOUD = "If your FTL is the Steam version, turn off Steam Cloud for FTL first: right-click FTL in your Steam library, "
			+ "Properties, General, and untick keeping saves in the Steam Cloud. With it on, Steam can bring back an old profile over the one in use, "
			+ "or a docked ship as a copy.";

	/** The briefing, and Immersive Mode on if the player confirms. True if it's on now. */
	public static boolean enter(Component owner) {
		Vault v = Vault.get();
		File immersiveRoot = v.otherRoot();
		boolean begun = Career.started(immersiveRoot);
		File profile = ProfileSwap.current(v.saves);

		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		JEditorPane brief = new JEditorPane("text/html", briefing(begun));
		brief.setEditable(false);
		brief.setCaretPosition(0);
		JScrollPane bs = new JScrollPane(brief);
		bs.setPreferredSize(new Dimension(640, 330));
		bs.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(bs);

		final JCheckBox own = new JCheckBox("Give Immersive Mode its own FTL profile (recommended)", true);
		final JRadioButton salaryNew = new JRadioButton("Only achievements earned from now on", true);
		final JRadioButton salaryAll = new JRadioButton("Every achievement already in your FTL profile");
		if (!begun) {
			p.add(gap());
			p.add(heading("Your career's choices (fixed once made)"));
			own.setToolTipText("Your current profile is set aside, not deleted, and comes back when you return to normal mode");
			own.setAlignmentX(Component.LEFT_ALIGNMENT);
			p.add(own);
			p.add(note("FTL starts a fresh profile: every ship locked but the Kestrel, no achievements. " + STEAM_CLOUD));
			JLabel sl = new JLabel("The stipend counts:");
			sl.setAlignmentX(Component.LEFT_ALIGNMENT);
			p.add(sl);
			ButtonGroup g = new ButtonGroup();
			g.add(salaryNew);
			g.add(salaryAll);
			salaryNew.setAlignmentX(Component.LEFT_ALIGNMENT);
			salaryAll.setAlignmentX(Component.LEFT_ALIGNMENT);
			p.add(salaryNew);
			p.add(salaryAll);
			ActionListener sync = new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					salaryAll.setEnabled(!own.isSelected()); // a fresh profile has none to count
					if (own.isSelected()) salaryNew.setSelected(true);
				}
			};
			own.addActionListener(sync);
			sync.actionPerformed(null);
		} else {
			p.add(gap());
			p.add(note("Your Immersive career continues where you left it" + (Career.ownProfile(immersiveRoot) ? ", with its own FTL profile" : "") + "."));
		}
		p.add(gap());
		p.add(heading("After a final victory (you can change this later in Settings)"));
		String was = begun ? Career.finalVictory(immersiveRoot) : FinalVictory.NOTHING;
		final JRadioButton[] victory = new JRadioButton[FinalVictory.CHOICES.length];
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
		if (profile != null && (begun ? !Career.ownProfile(immersiveRoot) : true)) {
			p.add(gap());
			p.add(heading("Your FTL profile"));
			p.add(note("Achievements and ships already earned in FTL don't bring rewards or commission orders; only what you earn from now on does. "
					+ "For the full experience, some captains start FTL with a fresh profile. If you do, back up your old one first: it's "
					+ profile.getName() + " in " + profile.getParent() + " (copy it somewhere safe; to go back, quit FTL and copy it back in)."));
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			JButton backup = new JButton("Back up my FTL profile");
			final Component parent = owner;
			backup.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					try {
						File to = ProfileSwap.backup(Vault.get().saves, Vault.get().shared);
						JOptionPane.showMessageDialog(parent, "Your FTL profile was copied to:\n" + to, "Back up my FTL profile", JOptionPane.INFORMATION_MESSAGE);
					} catch (IOException ex) {
						HomePlanet.showErrorDialog("The Home Planet Station could not back up your FTL profile:\n" + ex.getMessage());
					}
				}
			});
			row.add(backup);
			p.add(row);
		}

		Object[] opts = {"Confirm", "Cancel"};
		if (JOptionPane.showOptionDialog(owner, p, "Immersive Mode", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[1]) != 0) return false;
		if (!ftlClosed(owner, "Immersive Mode")) return false;
		boolean ownProfile = begun ? Career.ownProfile(immersiveRoot) : own.isSelected();
		File normalRoot = v.root;
		try {
			if (ownProfile) ProfileSwap.swap(v.saves, normalRoot, immersiveRoot);
			try {
				Vault.switchFleet(true);
			} catch (IOException e) {
				if (ownProfile) try { ProfileSwap.swap(v.saves, immersiveRoot, normalRoot); } catch (IOException again) { e.addSuppressed(again); }
				throw e;
			}
			HomePlanet.immersiveMode = true;
			HomePlanet.applyImmersive();
			HomePlanet.saveConfig();
			if (!begun) Career.start(salaryAll.isSelected() && !ownProfile, ownProfile);
			for (int i = 0; i < victory.length; i++) if (victory[i].isSelected()) Career.setFinalVictory(Vault.get().root, FinalVictory.CHOICES[i]);
			UnlockGrants.returning(Unlocks.read()); // a new career starts its record here
			homeplanet.parser.CompanionMod.register(homeplanet.parser.CompanionMod.load());
			Vault.get().takeStock();
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not enter Immersive Mode:\n" + e.getMessage()
					+ "\n\nThe fleet in use now is the " + (Vault.get().immersive ? "Immersive" : "normal") + " one.");
			return Vault.get().immersive;
		}
		return true;
	}

	/** Back to the normal fleet (and profile), after a confirmation. True if it's done. */
	public static boolean leave(Component owner) {
		Ship b = Vault.get().boarded();
		String message = "Return to normal mode?\n\nYour Immersive fleet is kept exactly as it is, and comes back when you enter Immersive Mode again.\n"
				+ "Your normal fleet and your own rules return" + (Career.ownProfile(Vault.get().root) ? ", with your own FTL profile." : ".")
				+ (b == null ? "" : "\n\n" + b.name + " docks here first, and will be boarded again when you return.");
		Object[] opts = {"Return to Normal Mode", "Cancel"};
		if (JOptionPane.showOptionDialog(owner, message, "Return to Normal Mode", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[1]) != 0) return false;
		if (!ftlClosed(owner, "Return to Normal Mode")) return false;
		try {
			leaveNow(null);
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not return to normal mode:\n" + e.getMessage()
					+ "\n\nThe fleet in use now is the " + (Vault.get().immersive ? "Immersive" : "normal") + " one.");
			return !Vault.get().immersive;
		}
		return true;
	}

	/**
	 * Leaves Immersive Mode now (FTL closed, already confirmed): the unlock record notes what's unlocked, the fleets and
	 * profiles swap back. With {@code handOver}, that boarded ship goes with the player to the normal fleet (an
	 * uncommissioned ship) instead of docking.
	 */
	static void leaveNow(Ship handOver) throws IOException {
		Vault v = Vault.get();
		File immersiveRoot = v.root, normalRoot = v.otherRoot();
		boolean ownProfile = Career.ownProfile(immersiveRoot);
		UnlockGrants.leaving(Unlocks.read());
		if (handOver != null) Vault.handOverBoarded(handOver);
		else Vault.switchFleet(false);
		if (ownProfile) ProfileSwap.swap(v.saves, immersiveRoot, normalRoot);
		HomePlanet.leaveImmersive();
		HomePlanet.saveConfig();
		homeplanet.parser.CompanionMod.register(homeplanet.parser.CompanionMod.load());
		Vault.get().takeStock();
	}

	private static boolean ftlClosed(Component owner, String title) {
		if (!GameGuard.isFtlRunning()) return true;
		JOptionPane.showMessageDialog(owner, "FTL is running. Quit FTL first: the fleets and FTL's profile change over.\nNothing was changed.", title, JOptionPane.INFORMATION_MESSAGE);
		return false;
	}

	/** The briefing: everything Immersive Mode does. */
	static String briefing(boolean begun) {
		return "<html><body style='font-family:sans-serif; font-size:11px; margin:6px'>"
				+ "<h3 style='margin-top:0'>Immersive Mode</h3>"
				+ "<p>The Home Planet Station runs by The Federation Home Planet's rules, and your service becomes a career.</p>"
				+ "<p><b>A fleet of its own.</b> Your current fleet (the Space Dock, the Junkyard, Spacedock Storage, their records) is kept exactly as it is "
				+ "and comes back when you return to normal mode. Your designs and remodels are shared by both. "
				+ (begun ? "" : "Your career begins with an empty shipyard, a free Kestrel and " + Career.STARTING_SCRAP + " scrap in Spacedock Storage.") + "</p>"
				+ "<p><b>The rules</b> (set and locked while it's on):</p><ul>"
				+ "<li>Trading, scrapping and New Journey need a station (a beacon with a store).</li>"
				+ "<li>Commissioning costs scrap from Spacedock Storage, at full price. An empty shipyard earns one free ship.</li>"
				+ "<li>Each ship you unlock in FTL from now on can be commissioned free, once. Locked ships can't be commissioned.</li>"
				+ "<li>A New Journey costs " + HomePlanet.JOURNEY_FEE + " scrap from Spacedock Storage.</li>"
				+ "<li>Missiles, drone parts and stored systems sell at 25% of the store price.</li>"
				+ "<li>Earlier versions of a ship can't be restored, lost ships can't be recovered, and a report for reassignment is final.</li></ul>"
				+ "<p><b>Rank.</b> You start as a Commander.</p><ul>"
				+ "<li><b>Captain</b>: design ships, remodel, overhaul, commission custom ships. " + Clearance.HOW_CAPTAIN.replace("\n", " ") + "</li>"
				+ "<li><b>Commodore</b>: the Federation's artillery. " + Clearance.HOW_COMMODORE.replace("\n", " ") + "</li>"
				+ "<li>The plans for the Rebel Flagship's weapons are released by the achievement <i>Rule Ten: Greed is Eternal</i>.</li></ul>"
				+ "<p><b>Transmissions.</b> An inbox on the Space Dock brings commission orders, promotions, and a reward for each FTL achievement "
				+ "earned from now on (claimed into Spacedock Storage).</p>"
				+ "<p><b>The stipend.</b> Every " + Career.SECTORS_PER_MONTH + " sectors your ships travel, " + Career.STIPEND_BASE
				+ " scrap plus, for each achievement counted, 1 as a Commander, 2 as a Captain, 3 as a Commodore: paid into Spacedock Storage.</p>"
				+ "</body></html>";
	}
	private static JLabel heading(String s) {
		JLabel l = new JLabel("<html><b>" + s + "</b></html>");
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}
	private static JLabel note(String s) {
		JLabel l = new JLabel("<html><div style='width:600px'>" + homeplanet.parser.XmlText.text(s) + "</div></html>");
		l.setBorder(BorderFactory.createEmptyBorder(2, 22, 6, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}
	private static Component gap() { return javax.swing.Box.createRigidArea(new Dimension(1, 10)); }
}
