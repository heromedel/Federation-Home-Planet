package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import homeplanet.core.HomePlanet;
import homeplanet.parser.Transmissions;

/** The transmissions inbox: messages from The Federation Home Planet, newest first, with Claim for their rewards. */
public class InboxDialog extends JDialog {
	private final java.awt.Component dock;
	private final DefaultListModel<Transmissions.Message> model = new DefaultListModel<Transmissions.Message>();
	private final JList<Transmissions.Message> list = new JList<Transmissions.Message>(model);
	/** The transmission: its subject as a gold title, who sent it and when, then the message, with a little air between lines. */
	private final javax.swing.JTextPane text = new javax.swing.JTextPane();
	private final JLabel rewardLabel = new JLabel(" ");
	private final JButton claim = new JButton("Claim");
	private final JButton reply = new JButton("Reply...");
	private final JButton commission = new JButton("Commission...");
	private final JButton archive = new JButton("Archive");
	private final JButton delete = new JButton("Delete");
	private final JButton keep = new JButton("Keep her"), museum = new JButton("Accept the museum's offer");
	private final javax.swing.JToggleButton inboxTab = new javax.swing.JToggleButton(), archiveTab = new javax.swing.JToggleButton(), outboxTab = new javax.swing.JToggleButton();
	/** The Inbox and Archive share one view; the Outbox has its own. */
	private final java.awt.CardLayout cards = new java.awt.CardLayout();
	private final JPanel cardPanel = new JPanel(cards);
	private final OutboxPanel outbox = new OutboxPanel(new Runnable() { public void run() { outboxTab.setText("Outbox (" + OutboxPanel.count() + ")"); } });
	private java.util.List<Transmissions.Message> all;
	private boolean openCommission;

	/** Opens the inbox. True if the player asked to go to Commission (a commission order). */
	public static boolean open(SpaceDockUI dock) { return open(dock, false); }
	/** Opens the inbox, on its Outbox tab if asked (the Long Range screen's Outbox button). */
	public static boolean open(java.awt.Component dock, boolean atOutbox) {
		InboxDialog d = new InboxDialog(dock);
		if (atOutbox) { d.outboxTab.setSelected(true); d.fill(); }
		d.setVisible(true);
		return d.openCommission;
	}

	private InboxDialog(java.awt.Component dock) {
		super(SwingUtilities.getWindowAncestor(dock), "Transmissions", ModalityType.APPLICATION_MODAL);
		this.dock = dock;
		all = Transmissions.load();
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
				Transmissions.Message m = (Transmissions.Message) v;
				String mark = m.hasReward() && !m.claimed ? "  [reward]" : Transmissions.isRescue(m) && !m.claimed ? "  [your decision]" : "";
				super.getListCellRendererComponent(l, "<html>" + (m.read ? "" : "<b>") + homeplanet.parser.XmlText.text(m.subject) + (m.read ? "" : "</b>")
						+ "<br><font color='" + MenuTheme.HTML_GREY_GREEN + "'>" + homeplanet.parser.XmlText.text(m.from) + " · " + m.date + mark + "</font></html>", i, sel, focus);
				setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
				return this;
			}
		});
		list.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) { if (!e.getValueIsAdjusting()) show(list.getSelectedValue()); }
		});
		JScrollPane ls = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		ls.setPreferredSize(new Dimension(360, 420));

		text.setEditable(false);
		text.setFont(MenuTheme.TEXT_FONT);
		text.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		JScrollPane ts = new JScrollPane(text);
		ts.setPreferredSize(new Dimension(520, 420));
		JPanel right = new JPanel(new BorderLayout(0, 6));
		right.add(ts, BorderLayout.CENTER);
		JPanel act = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		act.add(reply);
		act.add(claim);
		act.add(commission);
		act.add(keep);
		act.add(museum);
		act.add(archive);
		act.add(delete);
		act.add(rewardLabel);
		right.add(act, BorderLayout.SOUTH);
		reply.setToolTipText("Choose your answer: the reply comes in a few beacons later");
		reply.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { replySelected(); } });
		claim.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { claimSelected(); } });
		commission.setToolTipText("Go to Commission: the ship this order grants is marked free there");
		commission.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { openCommission = true; dispose(); } });
		archive.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { archiveSelected(); } });
		delete.setToolTipText("Delete this receipt for good: the trade stays in the station's history");
		delete.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { deleteSelected(); } });
		keep.setToolTipText("She docks at the Space Dock, ready for a new journey from the first sector");
		keep.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { decide(true); } });
		museum.setToolTipText("Her full value goes to the Cargo Hold, and she to the Federation museum");
		museum.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { decide(false); } });
		javax.swing.ButtonGroup tabs = new javax.swing.ButtonGroup();
		tabs.add(inboxTab);
		tabs.add(archiveTab);
		tabs.add(outboxTab);
		outboxTab.setToolTipText("Long Range Comm. messages waiting to go to commanders whose stations couldn't be reached");
		inboxTab.setSelected(true);
		ActionListener refill = new ActionListener() { public void actionPerformed(ActionEvent e) { fill(); } };
		inboxTab.addActionListener(refill);
		archiveTab.addActionListener(refill);
		outboxTab.addActionListener(refill);
		JPanel tabRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		tabRow.add(inboxTab);
		tabRow.add(archiveTab);
		tabRow.add(outboxTab);

		JPanel body = new JPanel(new BorderLayout(10, 8));
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		JPanel mail = new JPanel(new BorderLayout(10, 8));
		mail.add(ls, BorderLayout.WEST);
		mail.add(right, BorderLayout.CENTER);
		cardPanel.add(mail, "mail");
		cardPanel.add(outbox, "outbox");
		body.add(cardPanel, BorderLayout.CENTER);
		body.add(tabRow, BorderLayout.NORTH);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		buttons.add(close);
		getContentPane().add(body, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(close);
		pack();
		setLocationRelativeTo(getOwner());
		fill();
	}

	/** The list for the tab shown: the inbox, or the Archive. */
	private void fill() {
		outboxTab.setText("Outbox (" + OutboxPanel.count() + ")");
		if (outboxTab.isSelected()) { cards.show(cardPanel, "outbox"); outbox.fill(); return; }
		cards.show(cardPanel, "mail");
		boolean arch = archiveTab.isSelected();
		int in = 0, out = 0;
		for (Transmissions.Message m : all) { if (m.archived) out++; else in++; }
		inboxTab.setText("Inbox (" + in + ")");
		archiveTab.setText("Archive (" + out + ")");
		model.clear();
		for (Transmissions.Message m : all) if (m.archived == arch) model.addElement(m);
		if (!model.isEmpty()) list.setSelectedIndex(0);
		else show(null);
		if (model.isEmpty()) message(null, null, arch ? "Nothing archived. Archive a transmission to keep it here." : "No transmissions. The Federation Home Planet will be in touch.");
	}
	private void archiveSelected() {
		Transmissions.Message m = list.getSelectedValue();
		if (m == null) return;
		try {
			if (Transmissions.deletable(m)) { Transmissions.delete(m); all.remove(m); fill(); return; } // a paid stipend, a used order: nothing to keep
			Transmissions.setArchived(m, !m.archived);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not file the transmission:\n" + e.getMessage());
		}
		fill();
	}

	/** Deletes a receipt for good (it asks first). */
	private void deleteSelected() {
		Transmissions.Message m = list.getSelectedValue();
		if (m == null || !(Transmissions.isReceipt(m) || Transmissions.isNote(m))) return;
		if (!HomePlanet.confirmNo(this, Transmissions.isNote(m) ? "Delete this message from " + m.from + "?" : "Delete this receipt?\nThe trade stays in the station's history.", "Delete")) return;
		try {
			Transmissions.delete(m);
			all.remove(m);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not delete the transmission:\n" + e.getMessage());
		}
		fill();
	}

	/** Shows a transmission (a title and a line of who and when, if given, then the text). */
	private void message(String title, String meta, String body) {
		javax.swing.text.StyledDocument doc = text.getStyledDocument();
		try {
			doc.remove(0, doc.getLength());
			if (title != null) {
				javax.swing.text.SimpleAttributeSet t = new javax.swing.text.SimpleAttributeSet();
				javax.swing.text.StyleConstants.setFontFamily(t, Font.DIALOG);
				javax.swing.text.StyleConstants.setFontSize(t, 16);
				javax.swing.text.StyleConstants.setBold(t, true);
				javax.swing.text.StyleConstants.setForeground(t, MenuTheme.GOLD);
				doc.insertString(doc.getLength(), title + "\n", t);
			}
			if (meta != null) {
				javax.swing.text.SimpleAttributeSet a = new javax.swing.text.SimpleAttributeSet();
				javax.swing.text.StyleConstants.setForeground(a, MenuTheme.GREY_GREEN);
				doc.insertString(doc.getLength(), meta + "\n\n", a);
			}
			doc.insertString(doc.getLength(), body, null);
			javax.swing.text.SimpleAttributeSet p = new javax.swing.text.SimpleAttributeSet();
			javax.swing.text.StyleConstants.setLineSpacing(p, 0.2f);
			doc.setParagraphAttributes(0, doc.getLength(), p, false);
		} catch (javax.swing.text.BadLocationException e) {
			text.setText(body); // not expected: the plain text, at least
		}
		text.setCaretPosition(0);
	}

	private void show(Transmissions.Message m) {
		if (m == null) {
			message(null, null, "");
			reply.setVisible(false);
			claim.setVisible(false);
			commission.setVisible(false);
			keep.setVisible(false);
			museum.setVisible(false);
			archive.setVisible(false);
			delete.setVisible(false);
			rewardLabel.setText(" ");
			return;
		}
		boolean answered = m.replied != null && !m.replied.isEmpty();
		message(m.subject, m.from + "  \u00b7  " + m.date, answered ? m.body + "\n\nYou replied: \u201c" + m.replied + "\u201d" : m.body);
		String[] from = Transmissions.noteFrom(m);
		reply.setVisible(Transmissions.canReply(m) || from != null);
		reply.setEnabled(true);
		reply.setToolTipText(from == null ? "Choose your answer: the reply comes in a few beacons later"
				: "Write back to " + m.from + " over Long Range Comm. (if their station can't be reached, it can wait in the Outbox)");
		boolean canClaim = m.hasReward() && !m.claimed;
		claim.setVisible(m.hasReward());
		claim.setEnabled(canClaim);
		commission.setVisible(m.isOrder() && !Transmissions.deletable(m)); // a used order has nothing left to commission
		boolean open = Transmissions.isRescue(m) && !m.claimed;
		keep.setVisible(open);
		museum.setVisible(open);
		archive.setVisible(true);
		delete.setVisible(Transmissions.isReceipt(m) || Transmissions.isNote(m)); // receipts and messages pile up: archive one or be rid of it
		delete.setToolTipText(Transmissions.isNote(m) ? "Delete this message for good" : "Delete this receipt for good: the trade stays in the station's history");
		boolean stipend = Transmissions.deletable(m);
		archive.setText(stipend ? "Delete" : m.archived ? "Move to Inbox" : "Archive");
		archive.setToolTipText(stipend ? (Transmissions.isStipend(m) ? "Delete this notice: the scrap is already in the Cargo Hold" : "Delete this order: its free command has been taken") : m.archived ? "Back to the inbox" : "Store it in the Archive tab, out of the inbox");
		rewardLabel.setForeground(canClaim ? new Color(40, 150, 60) : Color.GRAY);
		rewardLabel.setText(Transmissions.isRescue(m) ? (m.claimed ? m.claimedWhat : " ") : !m.hasReward() ? " " : m.claimed ? "Claimed: " + m.claimedWhat : "Reward: " + Transmissions.describeReward(m)
				+ (Transmissions.price(m) > 0 ? ", for " + Transmissions.price(m) + " scrap" : ""));
		if (Transmissions.isRescue(m)) rewardLabel.setForeground(Color.GRAY);
		Transmissions.markRead(m);
		list.repaint();
	}

	/** A rescued ship's offer: keep her, or the museum's price. */
	private void decide(boolean keepHer) {
		Transmissions.Message m = list.getSelectedValue();
		if (m == null || m.claimed) return;
		try {
			homeplanet.vault.Vault.FinalBattle f = homeplanet.parser.FinalVictory.offer(Transmissions.rescueId(m));
			String what = f == null ? "Already settled." : keepHer ? homeplanet.parser.FinalVictory.keep(f) : homeplanet.parser.FinalVictory.museum(f);
			Transmissions.decided(m, what);
			JOptionPane.showMessageDialog(this, what, m.subject, JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not do that:\n" + e.getMessage());
		}
		show(m);
	}

	/** Answers a letter that asks for one: the choice of replies, then the answer is on its way. */
	private void replySelected() {
		Transmissions.Message m = list.getSelectedValue();
		String[] from = m == null ? null : Transmissions.noteFrom(m);
		if (from != null) { // another commander's message: written back to them over Long Range Comm.
			MessageDialog.open(this, from[0], from[1], Integer.parseInt(from[2]), m.from, null);
			return;
		}
		if (m == null || !Transmissions.canReply(m)) return;
		List<String> options = Transmissions.replyTexts(m);
		Object[] names = new Object[options.size() + 1];
		for (int i = 0; i < options.size(); i++) names[i] = options.get(i);
		names[options.size()] = "Cancel";
		int choice = JOptionPane.showOptionDialog(this, "How will you answer?", "Reply to " + m.from, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, names, names[names.length - 1]);
		if (choice < 0 || choice >= options.size()) return;
		try {
			Transmissions.reply(m, choice);
			String note = homeplanet.parser.RepairJob.OFFER.equals(m.key) && choice == 0 ? homeplanet.parser.RepairJob.patchNote() : null;
			JOptionPane.showMessageDialog(this, "Reply sent. Expect an answer within a few beacons." + (note == null ? "" : "\n\n" + note), "Reply", JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not send the reply. Nothing was changed:\n" + e.getMessage());
		}
		show(m);
	}

	private void claimSelected() {
		Transmissions.Message m = list.getSelectedValue();
		if (m == null) return;
		int choice = -1;
		List<String> options = Transmissions.choices(m);
		if (!options.isEmpty()) {
			Object[] names = new Object[options.size() + 1];
			for (int i = 0; i < options.size(); i++) names[i] = Transmissions.describe(options.get(i));
			names[options.size()] = "Cancel";
			choice = JOptionPane.showOptionDialog(this, "Which would you like?", "Claim", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, names, names[names.length - 1]);
			if (choice < 0 || choice >= options.size()) return;
		}
		try {
			String what = Transmissions.claim(m, choice);
			JOptionPane.showMessageDialog(this, "Delivered to the Cargo Hold: " + what + ".", "Claim", JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not take delivery. Nothing was changed:\n" + e.getMessage());
		}
		show(m);
	}
}
