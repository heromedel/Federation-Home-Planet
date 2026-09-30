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
			HomePlanet.saveConfig();
			if (!begun) Career.start(brief.salaryAll.isSelected() && !ownProfile, ownProfile);
			Career.setFinalVictory(Vault.get().root, brief.victoryChoice());
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
}
