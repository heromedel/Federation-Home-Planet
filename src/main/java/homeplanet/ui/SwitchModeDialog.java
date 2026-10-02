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
			rows.add(row(v, Vault.SLOTS[i]), c);
		}
		rows.revalidate();
		rows.repaint();
		pack();
	}
	/** One mode's row (described as on the welcome screen: {@link ModeRows}), with Switch or Begin, and End career. */
	private JPanel row(Vault v, final String slot) {
		boolean inUse = v.slot.equals(slot), sandbox = Vault.SANDBOX.equals(slot);
		boolean begun = sandbox || Career.started(Vault.rootOf(v.saves, slot));
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
		return ModeRows.row(v, slot, inUse, 430, buttons);
	}

	private void switchTo(String slot) {
		boolean done = Vault.SANDBOX.equals(slot) ? ImmersiveDialog.toSandbox(this) : ImmersiveDialog.enter(this, slot);
		if (done) { changed = true; dispose(); return; } // a new mode: every window closes and the Space Dock shows it (MainFrame.modeSwitched)
		fill();
	}
	private void end(String slot) {
		String was = Vault.get().slot;
		if (ImmersiveDialog.endCareer(this, slot)) changed = true;
		if (!Vault.get().slot.equals(was)) { dispose(); return; } // the career in use ended: the mode changed too
		fill();
	}
}
