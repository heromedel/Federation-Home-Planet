package homeplanet.ui;

import java.awt.BorderLayout;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import homeplanet.parser.Career;
import homeplanet.parser.CareerRules;
import homeplanet.vault.Vault;

/**
 * How each mode is described, the same in Switch Game Mode and on the welcome screen: its name, what it is, and what
 * its fleet holds now (so a career already begun is never mistaken for a new one).
 */
final class ModeRows {
	private ModeRows() {}

	/** Each slot's line, in Vault.SLOTS order. */
	private static final String[] TAGS = {
		"Your fleet, your rules: every rule can be changed in Settings.",
		"A gentler career: commissions at 75%, a Kestrel Type A on reassignment, the stipend every two months, 50 scrap to start.",
		"The Federation's standard: full prices, a ship by what you surrender on reassignment, the stipend every three months.",
		"No favours: 1000-scrap journeys, a relief ship on reassignment, no stripping, and the museum takes her after a final victory.",
		"Choose the level of each rule yourself, once, when the career begins."};

	/** The slot's line; the Custom slot holding the first career (from before difficulties) says so. */
	static String tag(Vault v, String slot) {
		File root = Vault.rootOf(v.saves, slot);
		if (Vault.CUSTOM.equals(slot) && Career.started(root)) {
			CareerRules r = Career.rules(root);
			if (r != null && CareerRules.EARLIER.equals(r.name)) return "Your first Immersive career, from before difficulties, with the rules it had.";
		}
		for (int i = 0; i < Vault.SLOTS.length; i++) if (Vault.SLOTS[i].equals(slot)) return TAGS[i];
		return "";
	}
	/** Has this mode a fleet already: a Sandbox fleet with ships, or a career begun? */
	static boolean begun(Vault v, String slot) {
		return Vault.SANDBOX.equals(slot) ? ships(v, slot) > 0 : Career.started(Vault.rootOf(v.saves, slot));
	}
	/** Its ships (docked, boarded and in the Junkyard). */
	static int ships(Vault v, String slot) {
		return v.slot.equals(slot) ? v.docked().size() + v.junked().size() + (v.boarded() == null ? 0 : 1) : Vault.shipCount(Vault.rootOf(v.saves, slot));
	}
	/** What its fleet holds now: "4 ships", "Not begun", "Begun: 4 ships, its own FTL profile". */
	static String state(Vault v, String slot) {
		File root = Vault.rootOf(v.saves, slot);
		int n = ships(v, slot);
		String count = n + (n == 1 ? " ship" : " ships");
		if (Vault.SANDBOX.equals(slot)) return count;
		if (!Career.started(root)) return "Not begun";
		return "Begun: " + count + (Career.ownProfile(root) ? ", its own FTL profile" : ""); // the line above says which kind of career
	}

	/** A mode's row: its name, its line and its state (gold-bordered when it's the one in use), with its buttons on the right. */
	static JPanel row(Vault v, String slot, boolean inUse, int width, JComponent buttons) {
		JPanel p = new JPanel(new BorderLayout(12, 0));
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(inUse ? MenuTheme.GOLD : MenuTheme.GREY_GREEN),
				BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		String state = (inUse ? "In use. " : "") + state(v, slot);
		p.add(new JLabel("<html><div style='width:" + width + "px'><font size='+1' color='" + MenuTheme.HTML_GOLD + "'><b>" + Vault.title(slot) + "</b></font><br>"
				+ tag(v, slot) + "<br><font color='" + MenuTheme.HTML_GREY_GREEN + "'>" + state + "</font></div></html>"), BorderLayout.CENTER);
		p.add(buttons, BorderLayout.EAST);
		return p;
	}
}
