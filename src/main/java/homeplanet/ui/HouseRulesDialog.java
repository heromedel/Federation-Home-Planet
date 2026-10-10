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
	private javax.swing.JRadioButton[] victory = null;

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
		SettingsDialog.heading(body, c, "Rules");
		rules.addTo(body, c, immersive == null);
		if (Boolean.FALSE.equals(immersive)) {
			SettingsDialog.heading(body, c, "After a final victory");
			victory = new javax.swing.JRadioButton[homeplanet.parser.FinalVictory.CHOICES.length];
			javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
			String was = homeplanet.parser.FinalVictory.choice();
			for (int i = 0; i < victory.length; i++) {
				String ch = homeplanet.parser.FinalVictory.CHOICES[i];
				victory[i] = new javax.swing.JRadioButton(homeplanet.parser.FinalVictory.label(ch), ch.equals(was));
				group.add(victory[i]);
				body.add(victory[i], (GridBagConstraints) c.clone());
				c.gridy++;
			}
			victory[1].setToolTipText(homeplanet.model.Words.cap(homeplanet.model.Words.she()) + " comes back as " + homeplanet.model.Words.she() + " was moments before the final engagement, ready for a new journey; or take " + homeplanet.model.Words.her() + " full value for the museum");
			victory[2].setToolTipText(homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " full value, as the shipyard would charge for " + homeplanet.model.Words.herObj() + ", goes to the Cargo Hold");
			JLabel note = new JLabel("<html><div style='width:520px'><font color='" + MenuTheme.HTML_GREY_GREEN + "'>For a rescue or a reward, The Home Planet Station must be open while you play: "
					+ "it keeps " + homeplanet.model.Words.herObj() + " as the Rebel Flagship heads for the last battle.</font></div></html>");
			note.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
			body.add(note, (GridBagConstraints) c.clone());
			c.gridy++;
		}

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
		if (victory != null) {
			for (int i = 0; i < victory.length; i++) {
				if (!victory[i].isSelected()) continue;
				try { homeplanet.parser.FinalVictory.setChoice(homeplanet.parser.FinalVictory.CHOICES[i]); }
				catch (java.io.IOException e) { homeplanet.core.HomePlanet.showErrorDialog("The Home Planet Station could not record the choice after a final victory:\n" + e.getMessage()); }
			}
		}
	}
}
