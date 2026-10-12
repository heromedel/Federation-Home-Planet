package homeplanet.ui;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import homeplanet.parser.FinalVictory;

/**
 * After a final victory: Nothing | Choice | Museum, and the Museum reward value under them (Full value | Half value |
 * None, greyed on Nothing); 6.47, heromedel. Settings and the House Rules both show it, for the fleet in use: a
 * career's difficulty fills both in, greyed.
 */
final class VictoryRules extends JPanel {
	private final JRadioButton[] choices = new JRadioButton[FinalVictory.CHOICES.length];
	private final JRadioButton[] pays = new JRadioButton[FinalVictory.PAYS.length];
	private final ButtonGroup choiceGroup = new ButtonGroup(), payGroup = new ButtonGroup();
	private final JLabel payLabel = new JLabel("Museum reward value:");
	private String choiceWas, payWas;
	private boolean fixedChoice, fixedPay;
	private Runnable onChange;

	VictoryRules() {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		ActionListener changed = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				sync();
				if (onChange != null) onChange.run();
			}
		};
		for (int i = 0; i < choices.length; i++) {
			choices[i] = new JRadioButton(FinalVictory.label(FinalVictory.CHOICES[i]));
			choices[i].setToolTipText(FinalVictory.tip(FinalVictory.explain(FinalVictory.CHOICES[i])));
			choices[i].setAlignmentX(LEFT_ALIGNMENT);
			choices[i].addActionListener(changed);
			choiceGroup.add(choices[i]);
			add(choices[i]);
		}
		JPanel payRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		payRow.setOpaque(false);
		payRow.setAlignmentX(LEFT_ALIGNMENT);
		payRow.setBorder(BorderFactory.createEmptyBorder(2, 22, 0, 0));
		payLabel.setToolTipText(FinalVictory.tip(FinalVictory.explainPay()));
		payRow.add(payLabel);
		for (int i = 0; i < pays.length; i++) {
			pays[i] = new JRadioButton(FinalVictory.payLabel(FinalVictory.PAYS[i]));
			pays[i].setToolTipText(FinalVictory.tip(FinalVictory.explainPay()));
			payGroup.add(pays[i]);
			payRow.add(javax.swing.Box.createHorizontalStrut(i == 0 ? 8 : 4));
			payRow.add(pays[i]);
		}
		add(payRow);
		refresh();
	}

	/** Runs when the choice is changed (Settings greys Rescued Ships moved to Hard unless it's Choice). */
	void onChange(Runnable r) { onChange = r; }

	/** As the fleet in use has it (entering or leaving Immersive Mode switches fleets). */
	void refresh() {
		choiceWas = FinalVictory.choice();
		payWas = FinalVictory.pay();
		fixedChoice = FinalVictory.fixed() != null;
		fixedPay = FinalVictory.fixedPay() != null;
		choiceGroup.clearSelection();
		payGroup.clearSelection();
		for (int i = 0; i < choices.length; i++) choices[i].setSelected(FinalVictory.CHOICES[i].equals(choiceWas));
		for (int i = 0; i < pays.length; i++) pays[i].setSelected(FinalVictory.PAYS[i].equals(payWas));
		sync();
	}
	/** Greyed where a career decides, and the museum's reward on Nothing. */
	private void sync() {
		for (JRadioButton b : choices) b.setEnabled(!fixedChoice);
		boolean payOn = !fixedPay && !FinalVictory.NOTHING.equals(choice());
		for (JRadioButton b : pays) b.setEnabled(payOn);
		payLabel.setEnabled(!FinalVictory.NOTHING.equals(choice()));
	}

	/** The choice shown: the one ticked, or the career's. */
	String choice() {
		if (fixedChoice) return choiceWas;
		for (int i = 0; i < choices.length; i++) if (choices[i].isSelected()) return FinalVictory.CHOICES[i];
		return FinalVictory.NOTHING;
	}
	String pay() {
		if (fixedPay) return payWas;
		for (int i = 0; i < pays.length; i++) if (pays[i].isSelected()) return FinalVictory.PAYS[i];
		return payWas;
	}

	/** For the station log's list of Settings changes. */
	void describeChanges(java.util.List<String> changed) {
		if (!fixedChoice && !choice().equals(choiceWas)) changed.add("After a final victory: " + FinalVictory.label(choice()));
		if (!fixedPay && !pay().equals(payWas)) changed.add("Museum reward value: " + FinalVictory.payLabel(pay()));
	}

	/** Keeps what was changed, where it's the player's to change (the normal fleet's go in the cfg: the caller saves it). */
	void apply() throws IOException {
		if (!fixedChoice && !choice().equals(choiceWas)) FinalVictory.setChoice(choice());
		if (!fixedPay && !pay().equals(payWas)) FinalVictory.setPay(pay());
	}
}
