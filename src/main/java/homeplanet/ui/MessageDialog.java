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
import homeplanet.comm.Wire;
import homeplanet.core.HomePlanet;

/**
 * A message to another commander over Long Range Comm., without a channel: to their inbox, or with Priority ticked
 * (it isn't, to begin with) as a pop-up on their screen. Opened from the list of stations, a message in the inbox, or
 * a pop-up's Reply.
 */
public final class MessageDialog extends JDialog {
	private final JTextArea text = new JTextArea(7, 44);
	private final JCheckBox priority = new JCheckBox("Priority: it pops up on their screen (unticked, it goes to their inbox)");
	/** The packed shipment, to go with the message (shown only while one is packed). */
	private final JCheckBox attach = new JCheckBox();
	private final homeplanet.comm.Shipments.Parcel packed = homeplanet.vault.Vault.isOpen() ? homeplanet.comm.Shipments.packed() : null;
	private final JButton send = new JButton("Send"), cancel = new JButton("Cancel");
	private final JLabel count = new JLabel();

	/**
	 * Opens the window for a message to that station. done (may be null) hears where it went ("Delivered to ..."),
	 * once it's sent. A station that can't be reached is offered the Outbox (port 0: its frequencies were closed when
	 * it last wrote, so it goes there straight away); one that turns it away says why, and the message stays to try again.
	 */
	public static void open(Component owner, String toStation, String host, int port, String toTitle, java.util.function.Consumer<String> done) {
		if (!Commander.ensure(owner)) return;
		Window w = owner instanceof Window ? (Window) owner : SwingUtilities.getWindowAncestor(owner);
		new MessageDialog(w, toStation, host, port, toTitle, done).setVisible(true);
	}

	private MessageDialog(Window owner, final String toStation, final String host, final int port, final String toTitle, final java.util.function.Consumer<String> done) {
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
		JPanel ticks = new JPanel(new java.awt.GridLayout(0, 1));
		ticks.add(priority);
		if (packed != null) {
			homeplanet.comm.Contacts.Entry c = homeplanet.comm.Contacts.get(toStation);
			boolean takes = c != null && c.shipments;
			attach.setText("Attach shipment (" + packed.words() + ")");
			attach.setEnabled(takes);
			attach.setToolTipText(takes ? "It goes with the message, and waits in their inbox until they accept it or send it back"
					: toTitle + "'s station can't take shipments (it needs a newer version)");
			ticks.add(attach);
		}
		south.add(ticks, BorderLayout.NORTH);
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
				final homeplanet.comm.Shipments.Parcel parcel = attach.isSelected() ? packed : null;
				String typed = Notes.clean(text.getText());
				if (typed.isEmpty() && parcel != null) typed = "(A shipment: " + parcel.words() + ".)";
				final String t = typed;
				if (t.isEmpty()) { text.requestFocusInWindow(); return; }
				final boolean pri = priority.isSelected();
				if (port <= 0) { toOutbox(toStation, toTitle, host, port, t, pri, parcel, done, toTitle + " is out of range."); return; }
				send.setEnabled(false);
				text.setEnabled(false);
				count.setText("Sending...");
				new Thread(new Runnable() {
					public void run() {
						String where = null, fail = null;
						boolean away = false;
						try {
							Wire.Msg note = Notes.note(HomePlanet.APP_VERSION, Commander.stationId(), Commander.title(), t, pri, LongRangeCommUI.listeningPort);
							if (parcel != null) homeplanet.comm.Shipments.attach(note, parcel);
							where = Notes.send(host, port, note, toTitle);
							if (parcel != null) homeplanet.comm.Shipments.sent(parcel, toStation, toTitle); // taken: the goods are theirs to accept
						} catch (Notes.Refused x) {
							fail = x.getMessage();
						} catch (IOException x) {
							fail = x.getMessage();
							away = true; // not reachable: the Outbox can wait for them
						}
						final String w = where, f = fail;
						final boolean unreachable = away;
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								if (f != null && unreachable) {
									send.setEnabled(true);
									text.setEnabled(true);
									counted();
									toOutbox(toStation, toTitle, host, port, t, pri, parcel, done, toTitle + "'s station isn't answering.");
									return;
								}
								if (f != null) {
									send.setEnabled(true);
									text.setEnabled(true);
									counted();
									JOptionPane.showMessageDialog(MessageDialog.this, "The message was not delivered:\n" + f, "Long Range Comm.", JOptionPane.INFORMATION_MESSAGE);
									return;
								}
								dispose();
								String said = parcel != null ? "Delivered to " + toTitle + "'s inbox, with the shipment (" + parcel.words() + ")." : Notes.POPUP.equals(w) ? "Shown to " + toTitle + "." : "Delivered to " + toTitle + "'s inbox."
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
	/** Asks to leave the message in the Outbox, to go when this station finds theirs; done hears so. */
	private void toOutbox(String toStation, String toTitle, String host, int port, String t, boolean pri, homeplanet.comm.Shipments.Parcel parcel,
			java.util.function.Consumer<String> done, String why) {
		if (!HomePlanet.confirmNo(this, why + "\nLeave the message in the Outbox" + (parcel != null ? ", with the shipment," : "") + " to go when your station finds theirs?\n(Your hailing frequencies must be open for it to go.)", "Outbox")) return;
		try {
			homeplanet.comm.Outbox.add(toStation, toTitle, host, port, t, pri, parcel == null ? "" : parcel.id);
		} catch (IOException x) {
			JOptionPane.showMessageDialog(this, x.getMessage(), "Outbox", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		dispose();
		String said = "Left in the Outbox for " + toTitle + ": it goes when your station finds theirs.";
		if (done != null) done.accept(said);
		else JOptionPane.showMessageDialog(getOwner(), said, "Long Range Comm.", JOptionPane.INFORMATION_MESSAGE);
	}
	private void counted() { count.setText(text.getDocument().getLength() + " / " + Notes.MAX + "   "); }
}
