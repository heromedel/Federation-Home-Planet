package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import homeplanet.comm.Commander;
import homeplanet.comm.Notes;
import homeplanet.core.HomePlanet;

/**
 * A message to another commander over Long Range Comm., without a channel: to their inbox, or with Priority ticked
 * (it isn't, to begin with) as a pop-up on their screen. Opened from the list of stations, a message in the inbox, or
 * a pop-up's Reply.
 */
public final class MessageDialog extends JDialog {
	private final JTextArea text = new JTextArea(7, 44);
	private final JCheckBox priority = new JCheckBox("Priority: it pops up on their screen (unticked, it goes to their inbox)");
	private final JButton send = new JButton("Send"), cancel = new JButton("Cancel");
	private final JLabel count = new JLabel();

	/**
	 * Opens the window for a message to that station. done (may be null) hears where it went ("Delivered to ..."),
	 * once it's sent; a failure is shown here, and the message stays to try again.
	 */
	public static void open(Component owner, String host, int port, String toTitle, java.util.function.Consumer<String> done) {
		if (!Commander.ensure(owner)) return;
		Window w = owner instanceof Window ? (Window) owner : SwingUtilities.getWindowAncestor(owner);
		new MessageDialog(w, host, port, toTitle, done).setVisible(true);
	}

	private MessageDialog(Window owner, final String host, final int port, final String toTitle, final java.util.function.Consumer<String> done) {
		super(owner, "Message to " + toTitle, ModalityType.APPLICATION_MODAL);
		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		p.add(new JLabel("From " + Commander.title() + " to " + toTitle + ", over Long Range Comm.:"), BorderLayout.NORTH);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setDocument(new javax.swing.text.PlainDocument() {
			@Override public void insertString(int offs, String str, javax.swing.text.AttributeSet a) throws javax.swing.text.BadLocationException {
				if (str == null) return;
				int room = Notes.MAX - getLength();
				if (room <= 0) return;
				super.insertString(offs, str.length() > room ? str.substring(0, room) : str, a);
			}
		});
		text.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) { counted(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e) { counted(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { counted(); }
		});
		p.add(new JScrollPane(text), BorderLayout.CENTER);
		JPanel south = new JPanel(new BorderLayout());
		south.add(priority, BorderLayout.NORTH);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.add(count);
		buttons.add(send);
		buttons.add(cancel);
		south.add(buttons, BorderLayout.SOUTH);
		p.add(south, BorderLayout.SOUTH);
		setContentPane(p);
		counted();
		cancel.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		send.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final String t = Notes.clean(text.getText());
				if (t.isEmpty()) { text.requestFocusInWindow(); return; }
				final boolean pri = priority.isSelected();
				send.setEnabled(false);
				text.setEnabled(false);
				count.setText("Sending...");
				new Thread(new Runnable() {
					public void run() {
						String where = null, fail = null;
						try {
							where = Notes.send(host, port, Notes.note(HomePlanet.APP_VERSION, Commander.stationId(), Commander.title(), t, pri, LongRangeCommUI.listeningPort), toTitle);
						} catch (IOException x) {
							fail = x.getMessage();
						}
						final String w = where, f = fail;
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								if (f != null) {
									send.setEnabled(true);
									text.setEnabled(true);
									counted();
									JOptionPane.showMessageDialog(MessageDialog.this, "The message was not delivered:\n" + f, "Long Range Comm.", JOptionPane.INFORMATION_MESSAGE);
									return;
								}
								dispose();
								String said = Notes.POPUP.equals(w) ? "Shown to " + toTitle + "." : "Delivered to " + toTitle + "'s inbox."
										+ (pri ? " (They take priority messages in their inbox, or had one from you within the minute.)" : "");
								if (done != null) done.accept(said);
								else JOptionPane.showMessageDialog(getOwner(), said, "Long Range Comm.", JOptionPane.INFORMATION_MESSAGE);
							}
						});
					}
				}, "Long Range Comm. message").start();
			}
		});
		getRootPane().setDefaultButton(send);
		pack();
		setLocationRelativeTo(owner);
	}
	private void counted() { count.setText(text.getDocument().getLength() + " / " + Notes.MAX + "   "); }
}
