package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import homeplanet.parser.Career;
import homeplanet.parser.CareerRules;
import homeplanet.vault.Vault;

/**
 * Switch Game Mode: Sandbox Mode and the four Immersive careers (Easy, Normal, Hard, Custom), each with a fleet of its
 * own (and, for a career, an FTL profile of its own if it chose one). Shows what each holds; switches to one (a career
 * not yet begun is briefed first) or ends a career.
 */
final class SwitchModeDialog extends JDialog {
	private static final String[] TAGS = {
		"Your fleet, your rules: every rule can be changed in Settings.",
		"A gentler career: commissions at 75%, a Kestrel Type A on reassignment, the stipend every 2 sectors, 50 scrap to start.",
		"The Federation's standard: full prices, a ship by what you surrender on reassignment, the stipend every 3 sectors.",
		"No favours: 1000-scrap journeys, a relief ship on reassignment, no stripping, and the museum takes her after a final victory.",
		"Choose the level of each rule yourself, once, when the career begins."};

	/** Did the mode in use change (or a career end)? */
	boolean changed = false;
	private final JPanel rows = new JPanel(new GridBagLayout());

	/** Shows the window; true if the mode in use changed or a career ended. */
	static boolean show(Component owner) {
		SwitchModeDialog d = new SwitchModeDialog(owner);
		d.setVisible(true);
		return d.changed;
	}

	private SwitchModeDialog(Component owner) {
		super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), "Switch Game Mode", ModalityType.APPLICATION_MODAL);
		JPanel body = new JPanel(new BorderLayout(0, 10));
		body.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
		body.add(new JLabel("<html><div style='width:640px'>Each mode has a fleet of its own, and each Immersive career can keep an FTL profile of its own. "
				+ "Switching docks the boarded ship first; she's boarded again when you come back. FTL must be closed.</div></html>"), BorderLayout.NORTH);
		body.add(rows, BorderLayout.CENTER);
		JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		south.add(close);
		body.add(south, BorderLayout.SOUTH);
		getContentPane().add(body);
		getRootPane().setDefaultButton(close);
		fill();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setResizable(false);
		setLocationRelativeTo(getOwner());
	}

	/** One row per mode: its name, what it is, what it holds, and its buttons. */
	private void fill() {
		rows.removeAll();
		Vault v = Vault.get();
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(0, 0, 8, 0);
		c.weightx = 1;
		c.gridx = 0;
		for (int i = 0; i < Vault.SLOTS.length; i++) {
			c.gridy = i;
			rows.add(row(v, Vault.SLOTS[i], TAGS[i]), c);
		}
		rows.revalidate();
		rows.repaint();
		pack();
	}
	private JPanel row(Vault v, final String slot, String tag) {
		boolean inUse = v.slot.equals(slot), sandbox = Vault.SANDBOX.equals(slot);
		File root = Vault.rootOf(v.saves, slot);
		boolean begun = sandbox || Career.started(root);
		JPanel p = new JPanel(new BorderLayout(12, 0));
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(inUse ? MenuTheme.GOLD : MenuTheme.GREY_GREEN),
				BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		String state;
		int n = inUse ? v.docked().size() + v.junked().size() + (v.boarded() == null ? 0 : 1) : Vault.shipCount(root);
		if (sandbox) state = n + (n == 1 ? " ship" : " ships");
		else if (!begun) state = "Not begun";
		else {
			CareerRules r = Career.rules(root);
			state = "Begun" + (r != null && Vault.CUSTOM.equals(slot) ? " (" + r.title() + ")" : "") + ": " + n + (n == 1 ? " ship" : " ships")
					+ (Career.ownProfile(root) ? ", its own FTL profile" : "");
		}
		if (inUse) state = "In use. " + state;
		if (Vault.CUSTOM.equals(slot) && begun && CareerRules.EARLIER.equals(Career.rules(root).name)) tag = "Your first Immersive career, from before difficulties, with the rules it had.";
		JLabel words = new JLabel("<html><div style='width:430px'><font size='+1' color='" + MenuTheme.HTML_GOLD + "'><b>" + Vault.title(slot) + "</b></font><br>"
				+ tag + "<br><font color='" + MenuTheme.HTML_GREY_GREEN + "'>" + state + "</font></div></html>");
		p.add(words, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(2, 0, 2, 0);
		c.gridx = 0;
		JButton go = new JButton(inUse ? "In use" : begun ? "Switch..." : "Begin...");
		go.setEnabled(!inUse);
		go.setToolTipText(inUse ? "The mode in use now" : sandbox ? "Back to your Sandbox fleet and rules (careers are kept)"
				: begun ? "Continue this career where you left it" : "The briefing, then this career begins");
		go.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { switchTo(slot); } });
		c.gridy = 0;
		buttons.add(go, c);
		if (!sandbox && begun) {
			JButton end = new JButton("End career...");
			end.setToolTipText("Ends this career for good (a copy is kept); the next time you choose it, a new one begins");
			end.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { end(slot); } });
			c.gridy = 1;
			buttons.add(end, c);
		}
		p.add(buttons, BorderLayout.EAST);
		return p;
	}

	private void switchTo(String slot) {
		boolean done = Vault.SANDBOX.equals(slot) ? ImmersiveDialog.toSandbox(this) : ImmersiveDialog.enter(this, slot);
		if (done) changed = true;
		fill();
	}
	private void end(String slot) {
		if (ImmersiveDialog.endCareer(this, slot)) changed = true;
		fill();
	}
}
