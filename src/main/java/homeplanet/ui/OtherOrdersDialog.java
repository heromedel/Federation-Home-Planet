package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;

import homeplanet.parser.XmlText;

/**
 * Other...: the station's rarely used orders, in a window like the others: each order's button with what it does
 * beside it, and, when it can't be given now, why not.
 */
public final class OtherOrdersDialog extends JDialog {
	/** An order: its button text, what it does, why it can't be given now (null if it can), and what it runs. */
	public static final class Order {
		final String name, what, whyNot;
		final Runnable run;
		final boolean groupStart;
		public Order(String name, String what, String whyNot, Runnable run, boolean groupStart) {
			this.name = name; this.what = what; this.whyNot = whyNot; this.run = run; this.groupStart = groupStart;
		}
	}

	private Runnable chosen = null;

	/** Shows the orders; the one chosen runs once this window has closed. */
	public static void open(Component owner, List<Order> orders) {
		OtherOrdersDialog d = new OtherOrdersDialog(owner, orders);
		d.setVisible(true);
		if (d.chosen != null) d.chosen.run();
	}

	private OtherOrdersDialog(Component owner, List<Order> orders) {
		super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), "Other Orders", ModalityType.APPLICATION_MODAL);
		JPanel body = new JPanel(new GridBagLayout());
		body.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = 0;
		c.anchor = GridBagConstraints.NORTHWEST;
		List<JButton> buttons = new ArrayList<JButton>();
		int widest = 0;
		for (final Order o : orders) {
			if (o.groupStart && c.gridy > 0) {
				c.gridx = 0; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(4, 0, 10, 0);
				body.add(new JSeparator(), c);
				c.gridy++; c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
			}
			JButton b = new JButton(o.name + "...");
			b.setEnabled(o.whyNot == null);
			b.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) { chosen = o.run; dispose(); }
			});
			buttons.add(b);
			widest = Math.max(widest, b.getPreferredSize().width);
			String text = XmlText.text(o.what);
			if (o.whyNot != null) text += "<br><font color='" + MenuTheme.HTML_ORANGE + "'>Not now: " + XmlText.text(o.whyNot) + "</font>";
			JLabel l = new JLabel("<html><div style='width:360px'>" + text + "</div></html>");
			if (o.whyNot != null) l.setForeground(Color.GRAY);
			c.gridx = 0; c.insets = new Insets(0, 0, 12, 12);
			body.add(b, c);
			c.gridx = 1; c.insets = new Insets(3, 0, 12, 0);
			body.add(l, c);
			c.gridy++;
		}
		for (JButton b : buttons) b.setPreferredSize(new Dimension(widest, b.getPreferredSize().height)); // one column of equal buttons
		JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { dispose(); }
		});
		south.add(close);
		getContentPane().add(body, BorderLayout.CENTER);
		getContentPane().add(south, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(close);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setResizable(false);
		setLocationRelativeTo(getOwner());
	}
}
