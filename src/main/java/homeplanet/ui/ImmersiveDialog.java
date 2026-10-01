package homeplanet.ui;

import java.awt.Component;
import java.io.File;
import java.io.IOException;

import javax.swing.JOptionPane;

import homeplanet.core.GameGuard;
import homeplanet.core.HomePlanet;
import homeplanet.core.ProfileSwap;
import homeplanet.parser.Career;
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

	/** The briefing, and Immersive Mode on if the player confirms. True if it's on now. */
	public static boolean enter(Component owner) {
		Vault v = Vault.get();
		File immersiveRoot = v.otherRoot();
		boolean begun = Career.started(immersiveRoot);
		File profile = ProfileSwap.current(v.saves);

		ImmersiveBriefing brief = new ImmersiveBriefing(owner, begun, immersiveRoot, profile);
		brief.setVisible(true);
		if (!brief.confirmed) return false;
		if (!ftlClosed(owner, "Immersive Mode")) return false;
		boolean ownProfile = begun ? Career.ownProfile(immersiveRoot) : brief.own.isSelected();
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
			HomePlanet.scrapKeepsSystems = brief.scrapKeeps.isSelected(); // the rule Immersive Mode leaves to the player
			HomePlanet.saveConfig();
			if (!begun) Career.start(brief.salaryAll.isSelected() && !ownProfile, ownProfile);
			Career.setFinalVictory(Vault.get().root, brief.victoryChoice());
			UnlockGrants.returning(Unlocks.read()); // a new career starts its record here
			homeplanet.parser.CompanionMod.register(homeplanet.parser.CompanionMod.load());
			Vault.get().takeStock();
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not enter Immersive Mode:\n" + e.getMessage()
					+ "\n\nThe fleet in use now is the " + (Vault.get().immersive ? "Immersive" : "Sandbox") + " one.");
			return Vault.get().immersive;
		}
		return true;
	}

	/** Back to the normal fleet (and profile), after a confirmation. True if it's done. */
	public static boolean leave(Component owner) {
		Vault v = Vault.get();
		Ship b = v.boarded();
		boolean ownProfile = Career.ownProfile(v.root);
		String message = "Return to Sandbox Mode?\n\nYour Sandbox fleet and your own rules return" + (ownProfile ? ", with your own FTL profile." : ".")
				+ (b == null ? "" : "\n" + b.name + " docks first.")
				+ "\n\nKeep your Immersive career, and it comes back exactly as it is when you enter Immersive Mode again.\n"
				+ "Or end it: everything in it is lost, and the next time you enter Immersive Mode a new career begins.";
		Object[] opts = {"Return and keep my career", "Return and end my career...", "Cancel"};
		int c = JOptionPane.showOptionDialog(owner, message, "Return to Sandbox Mode", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
		if (c != 0 && c != 1) return false;
		boolean end = c == 1;
		if (end && !confirmEnd(owner, v, ownProfile)) return false;
		if (!ftlClosed(owner, "Return to Sandbox Mode")) return false;
		try {
			leaveNow(null);
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not return to Sandbox Mode:\n" + e.getMessage()
					+ "\n\nThe fleet in use now is the " + (Vault.get().immersive ? "Immersive" : "Sandbox") + " one."
					+ (end ? " Your Immersive career was not ended." : ""));
			return !Vault.get().immersive;
		}
		if (end) {
			try {
				File zip = Vault.endImmersiveCareer();
				JOptionPane.showMessageDialog(owner, "Your Immersive career has ended. The next time you enter Immersive Mode, a new career begins.\n\n"
						+ "A copy was kept, just in case, in:\n" + zip, "Return to Sandbox Mode", JOptionPane.INFORMATION_MESSAGE);
			} catch (IOException e) {
				HomePlanet.showErrorDialog("You're back in Sandbox Mode, but The Home Planet Station could not end the Immersive career:\n" + e.getMessage());
			}
		}
		return true;
	}
	/** The second confirmation: what ending the career loses, in its own numbers. Cancel is the default. */
	private static boolean confirmEnd(Component owner, Vault v, boolean ownProfile) {
		int docked = v.docked().size(), junked = v.junked().size(), boarded = v.boarded() == null ? 0 : 1, ships = docked + junked + boarded;
		Unlocks u = Unlocks.read();
		String rank = UnlockGrants.rankName(UnlockGrants.rank(u.problem() == null ? u : null));
		String message = "End your Immersive career?\n\nThis can't be undone in The Home Planet Station. Lost for good:\n"
				+ " \u2022 " + ships + (ships == 1 ? " ship" : " ships") + (ships == 0 ? "" : " (" + (docked + boarded) + " at the Space Dock, " + junked + " in the Junkyard)")
				+ " and the Cargo Hold (" + v.storageScrap() + " scrap)\n"
				+ " \u2022 Your rank (" + rank + "), transmissions and stipend record\n"
				+ (ownProfile ? " \u2022 Immersive Mode's own FTL profile (its unlocks and achievements)\n" : "")
				+ "\nYour Sandbox fleet, your own FTL profile, and your designs and remodels are not touched.\n"
				+ "A copy of the career is kept in " + Vault.FOLDER + "\\" + Vault.OLD_CAREERS + ", in case of a mistake.";
		Object[] opts = {"End my career", "Cancel"};
		return JOptionPane.showOptionDialog(owner, message, "End your Immersive career", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, opts, opts[1]) == 0;
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
}
