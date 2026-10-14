package homeplanet.ui;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import homeplanet.parser.FinalVictory;

/**
 * After a final victory: Nothing | Choice | Museum, and the Museum reward value under them (Full value | Half value |
 * None, greyed on Nothing); 6.47, heromedel. Then the note that the station must be open, and Rescued Ships moved to
 * Hard (6.53: all of it here, so Settings and the House Rules show the same). Both show it for the fleet in use: a
 * career's difficulty fills it in, greyed.
 */
final class VictoryRules extends JPanel {
	private final JRadioButton[] choices = new JRadioButton[FinalVictory.CHOICES.length];
	private final JRadioButton[] pays = new JRadioButton[FinalVictory.PAYS.length];
	private final ButtonGroup choiceGroup = new ButtonGroup(), payGroup = new ButtonGroup();
	private final JLabel payLabel = new JLabel("Museum reward value:");
	/** Rescued Ships after Victory moved to Hard difficulty (heromedel, 6.02 and 6.03): the player's in Sandbox Mode and Easy, the career's otherwise. */
	static final String TO_HARD = "Rescued Ships after Victory moved to Hard difficulty";
	private final JCheckBox toHardBox = new JCheckBox(TO_HARD);
	private String choiceWas, payWas;
	private boolean fixedChoice, fixedPay, toHardWas;

	VictoryRules() {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		ActionListener changed = new ActionListener() {
			public void actionPerformed(ActionEvent e) { sync(); }
		};
		JPanel choiceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); // on one row, to keep the windows short (6.53)
		choiceRow.setOpaque(false);
		choiceRow.setAlignmentX(LEFT_ALIGNMENT);
		for (int i = 0; i < choices.length; i++) {
			choices[i] = new JRadioButton(FinalVictory.label(FinalVictory.CHOICES[i]));
			choices[i].setToolTipText(FinalVictory.tip(FinalVictory.explain(FinalVictory.CHOICES[i])));
			choices[i].addActionListener(changed);
			choiceGroup.add(choices[i]);
			if (i > 0) choiceRow.add(javax.swing.Box.createHorizontalStrut(14));
			choiceRow.add(choices[i]);
		}
		add(choiceRow);
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
		JLabel note = new JLabel("<html><div style='width:640px'><font color='" + MenuTheme.HTML_GREY_GREEN + "'>For Choice or Museum, The Home Planet Station must be open while you play: "
				+ "it keeps " + homeplanet.model.Words.herObj() + " as " + homeplanet.model.Words.she() + " was as the Rebel Flagship heads for the last battle. Each fleet has its own choice.</font></div></html>");
		note.setAlignmentX(LEFT_ALIGNMENT);
		note.setBorder(BorderFactory.createEmptyBorder(2, 22, 2, 0));
		add(note);
		toHardBox.setAlignmentX(LEFT_ALIGNMENT);
		add(toHardBox);
		refresh();
	}

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
		String rule = FinalVictory.toHardRule();
		homeplanet.parser.CareerRules r = homeplanet.parser.CareerRules.current();
		boolean none = FinalVictory.TO_HARD_NONE.equals(rule), normal = r != null && homeplanet.parser.CareerRules.NORMAL.equals(r.name);
		toHardWas = FinalVictory.toHard();
		toHardBox.setSelected(toHardWas);
		toHardBox.setText(none ? "<html><s>" + TO_HARD + "</s></html>" : TO_HARD); // crossed out where it can never come into play
		toHardBox.setToolTipText(none ? (r != null && homeplanet.parser.CareerRules.HARD.equals(r.name) ? "A Hard career" : "This career")
				+ (r != null && FinalVictory.NOTHING.equals(r.victory()) ? ": nothing after a final victory, so no ship comes back to fly" : ": the museum takes every victor, so a rescued ship never comes back to fly")
				: FinalVictory.TO_HARD_ON.equals(rule) ? (normal ? "A Normal career: a rescued ship always sets out on Hard" : "Chosen when this career began: a rescued ship always sets out on Hard")
				: FinalVictory.TO_HARD_OFF.equals(rule) ? "Chosen when this career began: when you keep a rescued ship, you choose " + homeplanet.model.Words.her() + " difficulty"
				: "When you keep a rescued ship, " + homeplanet.model.Words.she() + " sets out on Hard without asking; otherwise you choose " + homeplanet.model.Words.her() + " difficulty. Each fleet has its own choice.");
		sync();
	}
	/** Greyed where a career decides, and the museum's reward on Nothing. */
	private void sync() {
		for (JRadioButton b : choices) b.setEnabled(!fixedChoice);
		boolean payOn = !fixedPay && !FinalVictory.NOTHING.equals(choice());
		for (JRadioButton b : pays) b.setEnabled(payOn);
		payLabel.setEnabled(!FinalVictory.NOTHING.equals(choice()));
		// the player's only where the fleet leaves it to them, and only with Choice (only a ship kept flies again; 6.47)
		toHardBox.setEnabled(FinalVictory.TO_HARD_FREE.equals(FinalVictory.toHardRule()) && FinalVictory.RESCUE.equals(choice()));
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
		if (toHardBox.isEnabled() && toHardBox.isSelected() != toHardWas) changed.add(TO_HARD + ": " + toHardBox.isSelected());
	}

	/** Keeps what was changed, where it's the player's to change (the normal fleet's go in the cfg: the caller saves it). */
	void apply() throws IOException {
		if (!fixedChoice && !choice().equals(choiceWas)) FinalVictory.setChoice(choice());
		if (!fixedPay && !pay().equals(payWas)) FinalVictory.setPay(pay());
		if (toHardBox.isEnabled() && toHardBox.isSelected() != toHardWas) FinalVictory.setToHard(toHardBox.isSelected());
	}
}
