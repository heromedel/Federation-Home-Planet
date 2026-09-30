package homeplanet.ui;

import java.awt.Component;
import java.io.File;

import javax.swing.JOptionPane;

import homeplanet.core.HomePlanet;

/**
 * Clean up blueprints (Other... on the Space Dock): removes the station's own blueprints that no ship uses any more
 * (replaced remodels, retired designs) from the Federation Home Planet Mod. Rarely needed.
 */
final class BlueprintCleanup {
	private BlueprintCleanup() { }

	/** Moves blueprints no ship names into Removed Blueprints.log, rebuilds the mod, and offers to patch. */
	static void run(Component owner) {
		if (!HomePlanet.confirmNo(owner, "Clean up blueprints?\n\n"
				+ "Every remodel and custom design The Home Planet Station draws up becomes a blueprint in the Federation Home Planet Mod,\n"
				+ "and stays there while any ship flies it, or could fly it again (her kept versions, the Junkyard, a surrender).\n"
				+ "When a remodel was replaced, or a design retired, its old blueprint can linger with nothing left to use it.\n\n"
				+ "This finds those and removes them, then rebuilds the mod. Only needed if the mod has grown large;\n"
				+ "nothing a ship needs is ever touched. Look for unused blueprints now?", "Clean up blueprints")) return;
		java.util.List<homeplanet.parser.CompanionMod.Remodel> all = homeplanet.parser.CompanionMod.load();
		java.util.List<homeplanet.parser.ShipDesign> designs = homeplanet.parser.ShipDesign.load();
		boolean anyRetired = false;
		for (homeplanet.parser.ShipDesign d : designs) if (d.retired) anyRetired = true;
		if (all.isEmpty() && !anyRetired) {
			JOptionPane.showMessageDialog(owner, "No ship has been remodeled, and no design retired: there's nothing to clean up.", "Clean up blueprints", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		homeplanet.vault.Vault vault = homeplanet.vault.Vault.get();
		if (vault.anyUnscannable()) {
			JOptionPane.showMessageDialog(owner, "One of the ships' saves can't be read right now (is FTL running?), so The Home Planet Station can't safely tell which blueprints are unused.\n"
					+ "Try again later.", "Clean up blueprints", JOptionPane.WARNING_MESSAGE);
			return;
		}
		java.util.Set<String> used = vault.blueprintsInUseOrHistory(); // a ship's kept earlier versions may still need one to come back
		java.util.List<homeplanet.parser.CompanionMod.Remodel> unused = new java.util.ArrayList<homeplanet.parser.CompanionMod.Remodel>();
		java.util.List<homeplanet.parser.ShipDesign> unusedDesigns = new java.util.ArrayList<homeplanet.parser.ShipDesign>();
		StringBuilder list = new StringBuilder();
		for (homeplanet.parser.CompanionMod.Remodel r : all) {
			if (used.contains(r.id)) continue;
			unused.add(r);
			list.append("\n  ").append(r.id).append("  (made for ").append(r.ship).append(", ").append(r.made).append(")");
		}
		for (homeplanet.parser.ShipDesign d : designs) {
			if (!d.retired || used.contains(homeplanet.parser.DesignExport.bpId(d))) continue;
			unusedDesigns.add(d);
			list.append("\n  ").append(homeplanet.parser.DesignExport.bpId(d)).append("  (retired design ").append(d.name).append(d.version > 1 ? " v" + d.version : "").append(")");
		}
		int count = unused.size() + unusedDesigns.size();
		if (count == 0) {
			JOptionPane.showMessageDialog(owner, "Every blueprint on file is still used by a ship, or by a ship's kept records.", "Clean up blueprints", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Object[] opts = {"Remove", "Cancel"};
		int r = JOptionPane.showOptionDialog(owner, (count == 1 ? "1 blueprint is" : count + " blueprints are") + " no longer used by any ship:" + list
				+ "\n\nRemove " + (count == 1 ? "it" : "them") + "? " + (count == 1 ? "It goes" : "They go") + " into " + homeplanet.parser.CompanionMod.removedLog().getName() + ", where "
				+ (count == 1 ? "it" : "they") + " can be pasted back by hand.",
				"Clean up blueprints", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, opts, opts[1]);
		if (r != 0) return;
		try {
			for (homeplanet.parser.CompanionMod.Remodel u : unused) { homeplanet.parser.CompanionMod.retire(u); all.remove(u); }
			for (homeplanet.parser.ShipDesign d : unusedDesigns) { homeplanet.parser.CompanionMod.retire(d); designs.remove(d); }
			if (!unused.isEmpty()) homeplanet.parser.CompanionMod.save(all);
			if (!unusedDesigns.isEmpty()) homeplanet.parser.ShipDesign.save(designs);
		} catch (Exception ex) {
			HomePlanet.showErrorDialog("The Home Planet Station could not update the blueprint files:\n" + ex);
			return;
		}
		java.util.List<String> ids = new java.util.ArrayList<String>();
		for (homeplanet.parser.CompanionMod.Remodel u : unused) ids.add(u.id);
		for (homeplanet.parser.ShipDesign d : unusedDesigns) ids.add(homeplanet.parser.DesignExport.bpId(d) + " (" + d.name + ")");
		homeplanet.core.HistoryLog.entry("CLEAN", "Removed " + count + " unused blueprint(s)", ids);
		File mod = homeplanet.core.Slipstream.writeMod();
		Object[] opts2 = {"Patch Now", "Later"};
		int p = JOptionPane.showOptionDialog(owner, "Removed. The Federation Home Planet Mod was rebuilt" + (mod == null ? "." : " at:\n" + mod.getPath())
				+ "\n\nSend the patch to FTL via Slipstream now so the game matches?",
				"Clean up blueprints", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opts2, opts2[0]);
		if (p == 0) PatchDialog.open(owner);
	}
}
