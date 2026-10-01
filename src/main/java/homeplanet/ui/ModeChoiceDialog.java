package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
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
	private Boolean chosen = null; // true: Immersive Mode

	/** Asks, enters Immersive Mode if chosen (its briefing first), then shows that mode's rules. Waits; the caller saves the config. */
	public static void ask() {
		while (true) {
			ModeChoiceDialog d = new ModeChoiceDialog();
			d.setVisible(true);
			boolean immersive = Boolean.TRUE.equals(d.chosen);
			if (immersive) {
				if (!ImmersiveDialog.enter(null)) continue; // the briefing cancelled: choose again
				return; // the briefing asked everything Immersive Mode leaves to the player
			}
			HouseRulesDialog.ask(false);
			return;
		}
	}

	private ModeChoiceDialog() {
		super((java.awt.Window) null, "Welcome to The Home Planet Station", ModalityType.APPLICATION_MODAL);
		JPanel body = new JPanel(new BorderLayout(0, 12));
		body.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
		body.add(new JLabel("<html><b>How would you like to serve?</b> You can switch at any time in Settings.</html>"), BorderLayout.NORTH);
		JPanel choices = new JPanel(new GridLayout(1, 2, 14, 0));
		choices.add(choice("Sandbox Mode", "Recommended for FTL as you know it",
				"FTL with a station behind it. Your fleet, your rules: commission any ship you've unlocked, trade between your ships, "
				+ "design your own. Nothing is locked, and every rule can be changed in Settings whenever you like.",
				"Play in Sandbox Mode", false));
		choices.add(choice("Immersive Mode", "A Federation career",
				"You start with a Kestrel Type A and rise in rank as you earn FTL's achievements. Ships cost scrap; The Federation Home "
				+ "Planet stays in contact with you, sends rewards and a monthly stipend, and sets most of the rules. This mode has a "
				+ "fleet of its own, and can keep an FTL profile of its own too.",
				"Begin an Immersive career...", true));
		body.add(choices, BorderLayout.CENTER);
		getContentPane().add(body);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE); // closing it is Sandbox Mode, as before
		pack();
		setResizable(false);
		setLocationRelativeTo(null);
	}

	private JPanel choice(String title, String tag, String text, String button, final boolean immersive) {
		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(MenuTheme.GREY_GREEN), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
		JLabel head = new JLabel("<html><font size='+1' color='" + MenuTheme.HTML_GOLD + "'><b>" + title + "</b></font><br><i>" + tag + "</i></html>");
		p.add(head, BorderLayout.NORTH);
		JLabel words = new JLabel("<html><div style='width:270px'>" + text + "</div></html>");
		words.setVerticalAlignment(JLabel.TOP);
		p.add(words, BorderLayout.CENTER);
		JButton b = new JButton(button);
		b.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { chosen = immersive; dispose(); } });
		if (!immersive) getRootPane().setDefaultButton(b);
		p.add(b, BorderLayout.SOUTH);
		return p;
	}
}
