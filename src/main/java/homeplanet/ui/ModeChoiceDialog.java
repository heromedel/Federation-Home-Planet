package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * First setup: Sandbox Mode or Immersive Mode, each explained; then Sandbox Mode's house rules, or Immersive Mode's briefing.
 * Shown once the station's folders and fleet exist, so Immersive Mode can be entered from here.
 */
public class ModeChoiceDialog extends JDialog {
	private String chosen = null; // a Vault slot: SANDBOX, or an Immersive career

	/** Asks, enters Immersive Mode if chosen (its briefing first), then shows that mode's rules. Waits; the caller saves the config. */
	public static void ask() {
		while (true) {
			ModeChoiceDialog d = new ModeChoiceDialog();
			d.setVisible(true);
			boolean immersive = d.chosen != null && !homeplanet.vault.Vault.SANDBOX.equals(d.chosen);
			if (immersive) {
				if (!ImmersiveDialog.enter(null, d.chosen)) continue; // the briefing cancelled: choose again
				return; // the briefing asked everything Immersive Mode leaves to the player
			}
			HouseRulesDialog.ask(false);
			return;
		}
	}

	private ModeChoiceDialog() {
		super((java.awt.Window) null, "Welcome to The Home Planet Station", ModalityType.APPLICATION_MODAL);
		homeplanet.vault.Vault v = homeplanet.vault.Vault.get();
		boolean found = false;
		for (String slot : homeplanet.vault.Vault.SLOTS) found |= ModeRows.begun(v, slot);
		JPanel body = new JPanel(new BorderLayout(0, 12));
		body.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
		body.add(new JLabel("<html><b>How would you like to serve?</b> You can switch at any time in Settings (Switch Game Mode)."
				+ (found ? "<br>The Home Planet Station found fleets in this saves folder: continue one, or begin a new career." : "") + "</html>"), BorderLayout.NORTH);
		JPanel choices = new JPanel(new BorderLayout(14, 0));
		int sandboxShips = ModeRows.ships(v, homeplanet.vault.Vault.SANDBOX);
		choices.add(card("Sandbox Mode", "Recommended for FTL as you know it",
				"FTL with a station behind it. Your fleet, your rules: commission any ship you've unlocked, trade between your ships, "
				+ "design your own. Nothing is locked, and every rule can be changed in Settings whenever you like."
				+ (sandboxShips > 0 ? "<br><br><font color='" + MenuTheme.HTML_GREY_GREEN + "'>Your Sandbox fleet: " + sandboxShips + (sandboxShips == 1 ? " ship" : " ships") + "</font>" : ""),
				button(sandboxShips > 0 ? "Continue in Sandbox Mode" : "Play in Sandbox Mode", homeplanet.vault.Vault.SANDBOX, null)), BorderLayout.WEST);
		// the careers as Switch Game Mode shows them, so one already begun is never taken for a new one
		JPanel careers = new JPanel(new java.awt.GridLayout(0, 1, 0, 6));
		for (String slot : new String[] {homeplanet.vault.Vault.EASY, homeplanet.vault.Vault.NORMAL, homeplanet.vault.Vault.HARD, homeplanet.vault.Vault.CUSTOM}) {
			boolean begun = ModeRows.begun(v, slot);
			JButton b = button(begun ? "Continue..." : "Begin...", slot, begun ? "Continue this career where you left it" : "The briefing, then this career begins");
			careers.add(ModeRows.row(v, slot, false, 360, b));
		}
		JPanel immersive = card("Immersive Mode", "A Federation career",
				"You start with a Kestrel Type A and rise in rank as you earn FTL's achievements. Ships cost scrap; The Federation Home "
				+ "Planet stays in contact with you, sends rewards and a monthly stipend, and sets most of the rules. Each career has a "
				+ "fleet of its own, and can keep an FTL profile of its own too.", careers);
		choices.add(immersive, BorderLayout.CENTER);
		body.add(choices, BorderLayout.CENTER);
		getContentPane().add(body);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE); // closing it is Sandbox Mode, as before
		pack();
		setResizable(false);
		setLocationRelativeTo(null);
		ScreenFit.keepOnScreen(this);
	}

	/** A mode's card: its title, what it is, and what to press. */
	private JPanel card(String title, String tag, String text, java.awt.Component bottom) {
		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(MenuTheme.GREY_GREEN), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
		JLabel head = new JLabel("<html><font size='+1' color='" + MenuTheme.HTML_GOLD + "'><b>" + title + "</b></font><br><i>" + tag + "</i></html>");
		p.add(head, BorderLayout.NORTH);
		JLabel words = new JLabel("<html><div style='width:" + ("Sandbox Mode".equals(title) ? 230 : 500) + "px'>" + text + "</div></html>");
		words.setVerticalAlignment(JLabel.TOP);
		p.add(words, BorderLayout.CENTER);
		p.add(bottom, BorderLayout.SOUTH);
		return p;
	}
	private JButton button(String text, final String slot, String tip) {
		JButton b = new JButton(text);
		b.setToolTipText(tip);
		b.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { chosen = slot; dispose(); } });
		if (homeplanet.vault.Vault.SANDBOX.equals(slot)) getRootPane().setDefaultButton(b);
		return b;
	}
}
