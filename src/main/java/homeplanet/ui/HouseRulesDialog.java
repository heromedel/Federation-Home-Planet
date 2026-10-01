package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * First run: the station's rules in one window, starting from HomePlanet's current values.
 * OK, or closing the window, keeps what is ticked; the caller saves the config.
 */
public class HouseRulesDialog extends JDialog {

	private final RuleBoxes rules = new RuleBoxes();

	/** Shows the window and waits; the choices are set on HomePlanet when it closes. */
	public static void ask() { ask(null); }
	/**
	 * First setup, after the mode is chosen: Sandbox Mode ({@code immersive} false) shows every rule, Immersive Mode
	 * only those it leaves to the player. Null: every rule with the Immersive Mode row (as before the mode choice).
	 */
	public static void ask(Boolean immersive) {
		HouseRulesDialog d = new HouseRulesDialog(immersive);
		d.setVisible(true);
	}

	private HouseRulesDialog(Boolean immersive) {
		super((java.awt.Window) null, immersive == null ? "House Rules" : immersive ? "Immersive Mode: your rules" : "Sandbox Mode: house rules", ModalityType.APPLICATION_MODAL);
		JPanel body = new JPanel(new GridBagLayout());
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(2, 0, 2, 0);
		c.weightx = 1;

		String intro = Boolean.TRUE.equals(immersive)
				? "The Federation Home Planet sets most of the rules now. These are yours to choose; you can change them any time in Settings."
				: "Choose how strict The Home Planet Station's functionality is. You can change these any time in Settings.";
		body.add(new JLabel(intro), (GridBagConstraints) c.clone());
		c.gridy++;
		SettingsDialog.heading(body, c, "Rules");
		if (Boolean.TRUE.equals(immersive)) rules.addImmersiveOwn(body, c);
		else rules.addTo(body, c, immersive == null);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton ok = new JButton("OK");
		ok.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { dispose(); }
		});
		buttons.add(ok);
		getRootPane().setDefaultButton(ok);

		getContentPane().add(body, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setResizable(false);
		setLocationRelativeTo(null);
	}

	/** OK and the close button both end here, and both keep what is ticked (before setVisible returns to ask()). */
	@Override
	public void dispose() {
		apply();
		super.dispose();
	}

	private void apply() {
		rules.apply();
	}
}
