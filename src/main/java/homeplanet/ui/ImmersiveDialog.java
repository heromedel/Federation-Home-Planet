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

	/** The briefing for the Immersive career last used, and Immersive Mode on if the player confirms. True if it's on now. */
	public static boolean enter(Component owner) { return enter(owner, Vault.immersiveSlot); }
	/**
	 * The briefing for this Immersive career (Vault.EASY, NORMAL, HARD or CUSTOM), and, if the player confirms, that
	 * career in use: from another career, by way of Sandbox Mode. True if it's in use now.
	 */
	public static boolean enter(Component owner, String slot) {
		Vault v = Vault.get();
		if (v.slot.equals(slot)) return true;
		File immersiveRoot = Vault.rootOf(v.saves, slot);
		boolean begun = Career.started(immersiveRoot);
		File profile = ProfileSwap.current(v.saves);

		ImmersiveBriefing brief = new ImmersiveBriefing(owner, begun, immersiveRoot, profile, slot);
		brief.setVisible(true);
		if (!brief.confirmed) return false;
		if (!ftlClosed(owner, Vault.title(slot))) return false;
		if (v.immersive) {
			try {
				leaveNow(null); // to Sandbox Mode first: its fleet and profile are the way between careers
			} catch (IOException e) {
				HomePlanet.showErrorDialog("The Home Planet Station could not leave " + Vault.title(v.slot) + ":\n" + e.getMessage()
						+ "\n\nThe fleet in use now is " + Vault.title(Vault.get().slot) + "'s.");
				return false;
			}
			v = Vault.get();
		}
		boolean ownProfile = begun ? Career.ownProfile(immersiveRoot) : brief.own.isSelected();
		File normalRoot = v.root;
		try {
			if (ownProfile) ProfileSwap.swap(v.saves, normalRoot, immersiveRoot);
			try {
				Vault.switchFleet(slot);
			} catch (IOException e) {
				if (ownProfile) try { ProfileSwap.swap(v.saves, immersiveRoot, normalRoot); } catch (IOException again) { e.addSuppressed(again); }
				throw e;
			}
			Vault.immersiveSlot = slot;
			HomePlanet.immersiveMode = true;
			HomePlanet.applyImmersive();
			HomePlanet.saveConfig();
			if (!begun) Career.start(brief.salaryAll.isSelected() && !ownProfile, ownProfile, brief.rules());
			UnlockGrants.returning(Unlocks.read()); // a new career starts its record here
			homeplanet.parser.CompanionMod.register(homeplanet.parser.CompanionMod.load());
			Vault.get().takeStock();
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not begin " + Vault.title(slot) + ":\n" + e.getMessage()
					+ "\n\nThe fleet in use now is " + Vault.title(Vault.get().slot) + "'s.");
			return Vault.get().slot.equals(slot);
		}
		return true;
	}

	/** Back to Sandbox Mode's fleet and profile, the career kept, after a confirmation. True if it's done. */
	public static boolean toSandbox(Component owner) {
		Vault v = Vault.get();
		if (!v.immersive) return true;
		Ship b = v.boarded();
		boolean ownProfile = Career.ownProfile(v.root);
		String message = "Switch to Sandbox Mode?\n\nYour Sandbox fleet and your own rules return" + (ownProfile ? ", with your own FTL profile." : ".")
				+ (b == null ? "" : "\n" + b.name + " docks first.") + "\n\nYour " + Vault.title(v.slot) + " career is kept exactly as it is.";
		Object[] opts = {"Switch to Sandbox Mode", "Cancel"};
		if (JOptionPane.showOptionDialog(owner, message, "Switch Game Mode", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]) != 0) return false;
		if (!ftlClosed(owner, "Switch Game Mode")) return false;
		try {
			leaveNow(null);
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not switch to Sandbox Mode:\n" + e.getMessage()
					+ "\n\nThe fleet in use now is " + Vault.title(Vault.get().slot) + "'s.");
			return !Vault.get().immersive;
		}
		return true;
	}
	/**
	 * Ends this Immersive career, after two confirmations: if it's in use, Sandbox Mode first. Its folder is zipped
	 * into old-immersive-careers, then deleted. True if it's ended.
	 */
	public static boolean endCareer(Component owner, String slot) {
		Vault v = Vault.get();
		File root = Vault.rootOf(v.saves, slot);
		if (!root.isDirectory()) return false;
		boolean inUse = v.slot.equals(slot), ownProfile = Career.ownProfile(root);
		if (!confirmEnd(owner, slot, root, inUse ? v : null, ownProfile)) return false;
		if (!ftlClosed(owner, "End your career")) return false;
		if (inUse) {
			try {
				leaveNow(null);
			} catch (IOException e) {
				HomePlanet.showErrorDialog("The Home Planet Station could not switch to Sandbox Mode:\n" + e.getMessage() + "\n\nYour " + Vault.title(slot) + " career was not ended.");
				return false;
			}
		}
		try {
			File zip = Vault.endCareer(slot);
			JOptionPane.showMessageDialog(owner, "Your " + Vault.title(slot) + " career has ended. The next time you choose it, a new career begins.\n\n"
					+ "A copy was kept, just in case, in:\n" + zip, "End your career", JOptionPane.INFORMATION_MESSAGE);
			return true;
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not end the " + Vault.title(slot) + " career:\n" + e.getMessage());
			return false;
		}
	}

	/** The second confirmation: what ending the career loses, in its own numbers. Cancel is the default. */
	private static boolean confirmEnd(Component owner, String slot, File root, Vault v, boolean ownProfile) {
		String ships, rank;
		if (v != null) { // the career in use: its numbers
			int docked = v.docked().size(), junked = v.junked().size(), boarded = v.boarded() == null ? 0 : 1, n = docked + junked + boarded;
			Unlocks u = Unlocks.read();
			rank = " (" + UnlockGrants.rankName(UnlockGrants.rank(u.problem() == null ? u : null)) + ")";
			ships = n + (n == 1 ? " ship" : " ships") + (n == 0 ? "" : " (" + (docked + boarded) + " at the Space Dock, " + junked + " in the Junkyard)")
					+ " and the Cargo Hold (" + v.storageScrap() + " scrap)";
		} else {
			int n = Vault.shipCount(root);
			rank = "";
			ships = n + (n == 1 ? " ship" : " ships") + " and the Cargo Hold";
		}
		String message = "End your " + Vault.title(slot) + " career?\n\nThis can't be undone in The Home Planet Station. Lost for good:\n"
				+ " \u2022 " + ships + "\n"
				+ " \u2022 Your rank" + rank + ", transmissions and stipend record\n"
				+ (ownProfile ? " \u2022 Its own FTL profile (its unlocks and achievements)\n" : "")
				+ "\nYour Sandbox fleet, your other careers, your own FTL profile, and your designs and remodels are not touched.\n"
				+ "A copy of the career is kept in " + Vault.FOLDER + "\\" + Vault.OLD_CAREERS + ", in case of a mistake.";
		Object[] opts = {"End my career", "Cancel"};
		return JOptionPane.showOptionDialog(owner, message, "End your " + Vault.title(slot) + " career", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, opts, opts[1]) == 0;
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
