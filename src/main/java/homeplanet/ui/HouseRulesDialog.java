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
	/** Sandbox Mode's first setup: what happens after a final victory, as Settings shows it. */
	private VictoryRules victory = null;

	/** Shows the window and waits; the choices are set on HomePlanet when it closes. */
	public static void ask() { ask(null); }
	/**
	 * First setup, Sandbox Mode chosen ({@code immersive} false): every house rule, and what happens after a final
	 * victory. Null: every rule with the Immersive Mode row (an older station missing a newer rule).
	 */
	public static void ask(Boolean immersive) {
		HouseRulesDialog d = new HouseRulesDialog(immersive);
		d.setVisible(true);
	}

	private HouseRulesDialog(Boolean immersive) {
		super((java.awt.Window) null, immersive == null ? "House Rules" : "Sandbox Mode: house rules", ModalityType.APPLICATION_MODAL);
		JPanel body = new JPanel(new GridBagLayout());
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(2, 0, 2, 0);
		c.weightx = 1;

		body.add(new JLabel("Choose how strict The Home Planet Station's functionality is. You can change these any time in Settings."), (GridBagConstraints) c.clone());
		c.gridy++;
		if (immersive == null) SettingsDialog.heading(body, c, "Rules"); // over Switch Game Mode; Sandbox Mode's first setup goes straight to the rules
		rules.addTo(body, c, immersive == null);
		if (Boolean.FALSE.equals(immersive)) {
			SettingsDialog.heading(body, c, "After a final victory");
			victory = new VictoryRules(); // Nothing, Choice or Museum, and the museum's reward (6.47)
			body.add(victory, (GridBagConstraints) c.clone()); // with its note and Rescued Ships moved to Hard, as Settings has them
			c.gridy++;
		}

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton ok = new JButton("OK");
		ok.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { dispose(); }
		});
		buttons.add(ok);
		getRootPane().setDefaultButton(ok);

		getContentPane().add(ScreenFit.wrap(body, 110, null), BorderLayout.CENTER); // scrolls on a short screen, so OK and the last rules are never off it (6.53)
		getContentPane().add(buttons, BorderLayout.SOUTH);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setResizable(false);
		setLocationRelativeTo(null);
		ScreenFit.keepOnScreen(this);
	}

	/** OK and the close button both end here, and both keep what is ticked (before setVisible returns to ask()). */
	@Override
	public void dispose() {
		apply();
		super.dispose();
	}

	private void apply() {
		rules.apply();
		if (victory != null) {
			try { victory.apply(); }
			catch (java.io.IOException e) { homeplanet.core.HomePlanet.showErrorDialog("The Home Planet Station could not record the choice after a final victory:\n" + e.getMessage()); }
		}
	}
}
