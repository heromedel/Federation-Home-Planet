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
	/** A shipment held in the inbox: into this fleet's Cargo Hold, another fleet's, or back to its sender. */
	private final JButton takeIt = new JButton("Accept"), elsewhere = new JButton("Deliver to another fleet..."), sendBack = new JButton("Return to sender");
	private final JButton keep = new JButton("Keep her"), museum = new JButton("Accept the museum's offer");
	private final JButton payRansom = new JButton("Pay"), refuseRansom = new JButton("Refuse");
	/** An expedition's prize: a recruit to sign on or send on their way; a ship to the Space Dock, the Junkyard, or not taken. */
	private final JButton prizeYes = new JButton("Sign them on"), prizeDock = new JButton("Space Dock"), prizeJunk = new JButton("Junkyard"), prizeNo = new JButton("Send them on their way");
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

	/** The two panes' widths: the window is always these and the button space, whatever the letter (5.58). */
	private static final int LIST_W = 360, LETTER_W = 520;

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
		ls.setPreferredSize(new Dimension(LIST_W, 420));

		text.setEditable(false);
		text.setFont(MenuTheme.TEXT_FONT);
		text.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		JScrollPane ts = new JScrollPane(text);
		ts.setPreferredSize(new Dimension(LETTER_W, 420));
		JPanel right = new JPanel(new BorderLayout(0, 6));
		right.add(ts, BorderLayout.CENTER);
		// a fixed space for the letter's buttons, the letter's width and two rows high (heromedel, 5.58): the window is sized from
		// it, never from the buttons switched on when it opens (all of them, before the first letter is shown: 2,000 pixels wide)
		JPanel act = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4)) {
			@Override public Dimension getPreferredSize() { return new Dimension(LETTER_W, 2 * archive.getPreferredSize().height + 12); }
			@Override public Dimension getMinimumSize() { return getPreferredSize(); }
		};
		act.add(reply);
		act.add(claim);
		act.add(commission);
		act.add(keep);
		act.add(museum);
		act.add(payRansom);
		act.add(refuseRansom);
		act.add(prizeYes);
		act.add(prizeDock);
		act.add(prizeJunk);
		act.add(prizeNo);
		act.add(archive);
		act.add(delete);
		act.add(takeIt);
		act.add(elsewhere);
		act.add(sendBack);
		takeIt.setToolTipText("Into this fleet's Cargo Hold");
		sendBack.setToolTipText("It waits in your Outbox, addressed back to them, and goes when your station finds theirs");
		takeIt.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { parcelAction(0); } });
		elsewhere.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { parcelAction(1); } });
		sendBack.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { parcelAction(2); } });
		act.add(rewardLabel);
		right.add(act, BorderLayout.SOUTH);
		reply.setToolTipText("Choose your answer: the reply comes in a few days");
		reply.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { replySelected(); } });
		claim.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { claimSelected(); } });
		commission.setToolTipText("Go to Commission: the ship this order grants is marked free there");
		commission.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { openCommission = true; dispose(); } });
		archive.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { archiveSelected(); } });
		delete.setToolTipText("Delete this receipt for good: the trade stays in the station's history");
		delete.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { deleteSelected(); } });
		keep.setToolTipText("She docks at the Space Dock, ready for a new journey from the first sector");
		keep.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { decide(true); } });
		museum.setToolTipText("Her full value goes to the Cargo Hold, and she to the Federation Museum");
		museum.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { decide(false); } });
		payRansom.setToolTipText("Paid from the Cargo Hold; they come back to it");
		payRansom.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { ransom(true); } });
		refuseRansom.setToolTipText("They will not be coming back");
		refuseRansom.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { ransom(false); } });
		prizeYes.setToolTipText("Into the Cargo Hold");
		prizeYes.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { prize(0); } });
		prizeDock.setToolTipText("Docked at the Space Dock, as she is");
		prizeDock.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { prize(1); } });
		prizeJunk.setToolTipText("To the Junkyard, as she is, to be set right or scrapped");
		prizeJunk.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { prize(2); } });
		prizeNo.setToolTipText("Gone for good");
		prizeNo.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { prize(3); } });
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
		if (m == null || Transmissions.unclaimedStipend(m) && !m.archived) return; // claimed first: the pay can't be lost
		try {
			if (Transmissions.deletable(m)) { Transmissions.delete(m); all.remove(m); fill(); return; } // a paid stipend, a used order: nothing to keep
			Transmissions.setArchived(m, !m.archived);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not file the transmission:\n" + e.getMessage());
		}
		fill();
	}

	/** Deletes a receipt for good (it asks first). */
	/** A shipment's transmission's parcel, or null if it isn't one. */
	private static homeplanet.comm.Shipments.Parcel parcelOf(Transmissions.Message m) {
		return m != null && m.key.startsWith("parcel:") && homeplanet.vault.Vault.isOpen() ? homeplanet.comm.Shipments.find(m.key.substring(7)) : null;
	}
	/** Where a commander's message (or shipment) can be answered: their station, host and port; null if it can't. */
	private static String[] replyTo(Transmissions.Message m) {
		homeplanet.comm.Shipments.Parcel p = parcelOf(m);
		if (p != null) return new String[] {p.peerStation, p.host, Integer.toString(p.port)};
		return Transmissions.noteFrom(m);
	}
	/** A shipment's state, under its message. */
	private static String parcelState(homeplanet.comm.Shipments.Parcel p) {
		if (p == null) return "";
		if (homeplanet.comm.Shipments.HELD.equals(p.state)) {
			String why = homeplanet.comm.Shipments.whyNot(p);
			return "\n\nWaiting for you: accept it, deliver it to another of your fleets, or return it." + (why == null ? "" : "\n\nThis fleet can't accept it: " + why);
		}
		if (homeplanet.comm.Shipments.ACCEPTED.equals(p.state)) return "\n\nAccepted: it's in the Cargo Hold.";
		if (homeplanet.comm.Shipments.ELSEWHERE.equals(p.state)) return "\n\nDelivered to another of your fleets' Cargo Hold.";
		if (homeplanet.comm.Shipments.RETURNING.equals(p.state)) return "\n\nReturning: it waits in your Outbox, and goes when your station finds " + p.peerTitle + "'s.";
		if (homeplanet.comm.Shipments.RETURNED.equals(p.state)) return "\n\nReturned to " + p.peerTitle + ".";
		return "";
	}
	/** 0 accept, 1 deliver to another fleet, 2 return to sender. */
	private void parcelAction(int what) {
		Transmissions.Message m = list.getSelectedValue();
		homeplanet.comm.Shipments.Parcel p = parcelOf(m);
		if (p == null) return;
		try {
			if (what == 0) {
				homeplanet.comm.Shipments.accept(p);
			} else if (what == 1) {
				java.util.List<String> fleets = homeplanet.comm.Shipments.otherFleets(p);
				if (fleets.isEmpty()) return;
				Object[] names = new Object[fleets.size()];
				for (int i = 0; i < names.length; i++) names[i] = homeplanet.vault.Vault.title(fleets.get(i)) + " fleet";
				Object pick = JOptionPane.showInputDialog(this, "Deliver " + p.words() + " to which fleet's Cargo Hold?", "Deliver to another fleet", JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
				if (pick == null) return;
				for (int i = 0; i < names.length; i++) if (names[i].equals(pick)) homeplanet.comm.Shipments.deliverTo(p, fleets.get(i));
			} else {
				if (!HomePlanet.confirmNo(this, "Return " + p.words() + " to " + p.peerTitle + "?\nIt waits in your Outbox, and goes when your station finds theirs.", "Return to sender")) return;
				homeplanet.comm.Shipments.returnIt(p);
			}
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not do that:\n" + e.getMessage());
		}
		show(m);
		outboxTab.setText("Outbox (" + OutboxPanel.count() + ")");
	}
	private void deleteSelected() {
		Transmissions.Message m = list.getSelectedValue();
		boolean report = m != null && m.key.startsWith("expedition:"); // an expedition report (heromedel, 5.16)
		boolean shipped = m != null && Transmissions.isShipped(m);
		if (m == null || !(Transmissions.isReceipt(m) || Transmissions.isNote(m) || m.key.startsWith("parcel:") || report || shipped)) return;
		String unclaimed = m.hasReward() && !m.claimed ? Transmissions.describeReward(m) : null; // what goes with it (6.02: an expedition's finds come with its letter)
		homeplanet.parser.Assignments.Pending prize = report && homeplanet.vault.Vault.isOpen() ? homeplanet.parser.Assignments.pendingFor(homeplanet.vault.Vault.get(), m.key) : null;
		String ask = shipped ? "Delete this letter?" + (unclaimed == null ? "" : "\n\nThe " + unclaimed + " it carries, not yet claimed, is lost with it.")
				: report ? "Delete this expedition report?" + (unclaimed == null ? "" : "\n\nWhat they brought back, not yet claimed (" + unclaimed + "), is lost with it.")
				+ (prize == null ? "" : "ship".equals(prize.kind)
				? "\n\nThe ship waiting on your answer is turned away with it." : "\n\nThe recruit waiting on your answer goes on their way with it.")
				: Transmissions.isNote(m) ? "Delete this message from " + m.from + "?" : "Delete this receipt?\nThe trade stays in the station's history.";
		if (!HomePlanet.confirmNo(this, ask, "Delete")) return;
		try {
			if (prize != null) homeplanet.parser.Assignments.decline(homeplanet.vault.Vault.get(), prize); // a deleted question is answered No
			Transmissions.delete(m);
			all.remove(m);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not delete the transmission:\n" + e.getMessage());
		}
		fill();
	}

	/** Shows a transmission (a title and a line of who and when, if given, then the text). */
	private void message(String title, String meta, String body) { message(title, meta, body, null); }
	/** The same, with an expedition report's faces beside its crew's lines. */
	private void message(String title, String meta, String body, java.util.List<homeplanet.parser.Assignments.Face> faces) {
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
			if (faces != null && !faces.isEmpty()) ReportFaces.insert(doc, body, faces, null);
			else underlined(doc, body);
			javax.swing.text.SimpleAttributeSet p = new javax.swing.text.SimpleAttributeSet();
			javax.swing.text.StyleConstants.setLineSpacing(p, 0.2f);
			doc.setParagraphAttributes(0, doc.getLength(), p, false);
		} catch (javax.swing.text.BadLocationException e) {
			text.setText(body); // not expected: the plain text, at least
		}
		text.setCaretPosition(0);
	}

	/** A letter's words, with &lt;u&gt;…&lt;/u&gt; underlined (heromedel's "Approved", 5.57); everything else as it is. */
	private static void underlined(javax.swing.text.StyledDocument doc, String body) throws javax.swing.text.BadLocationException {
		javax.swing.text.SimpleAttributeSet u = new javax.swing.text.SimpleAttributeSet();
		javax.swing.text.StyleConstants.setUnderline(u, true);
		int at = 0;
		while (true) {
			int s = body.indexOf("<u>", at), e = s < 0 ? -1 : body.indexOf("</u>", s);
			if (s < 0 || e < 0) { doc.insertString(doc.getLength(), body.substring(at), null); return; }
			doc.insertString(doc.getLength(), body.substring(at, s), null);
			doc.insertString(doc.getLength(), body.substring(s + 3, e), u);
			at = e + 4;
		}
	}

	private void show(Transmissions.Message m) {
		if (m == null) {
			message(null, null, "");
			reply.setVisible(false);
			claim.setVisible(false);
			commission.setVisible(false);
			keep.setVisible(false);
			museum.setVisible(false);
			payRansom.setVisible(false);
			refuseRansom.setVisible(false);
			prizeYes.setVisible(false); prizeDock.setVisible(false); prizeJunk.setVisible(false); prizeNo.setVisible(false);
			archive.setVisible(false);
			delete.setVisible(false);
			takeIt.setVisible(false);
			elsewhere.setVisible(false);
			sendBack.setVisible(false);
			rewardLabel.setText(" ");
			return;
		}
		boolean answered = m.replied != null && !m.replied.isEmpty();
		homeplanet.comm.Shipments.Parcel parcel = parcelOf(m);
		java.util.List<homeplanet.parser.Assignments.Face> faces = m.key.startsWith("expedition:") && homeplanet.vault.Vault.isOpen()
				? homeplanet.parser.Assignments.facesFor(homeplanet.vault.Vault.get(), m.key) : null; // an expedition report's crew, as they came home
		message(m.subject, m.from + "  \u00b7  " + m.date, (answered ? m.body + "\n\nYou replied: \u201c" + m.replied + "\u201d" : m.body) + parcelState(parcel), faces);
		String[] from = replyTo(m);
		reply.setVisible(Transmissions.canReply(m) || from != null);
		reply.setEnabled(true);
		reply.setToolTipText(from == null ? "Choose your answer: the reply comes in a few days"
				: "Write back to " + m.from + " over Long Range Comm. (if their station can't be reached, it can wait in the Outbox)");
		boolean canClaim = m.hasReward() && !m.claimed;
		claim.setVisible(m.hasReward());
		claim.setEnabled(canClaim);
		commission.setVisible(m.isOrder() && !Transmissions.deletable(m)); // a used order has nothing left to commission
		boolean open = Transmissions.isRescue(m) && !m.claimed;
		keep.setVisible(open);
		museum.setVisible(open);
		homeplanet.parser.Expeditions.Captive captive = homeplanet.parser.Expeditions.isRansom(m.key) && homeplanet.vault.Vault.isOpen()
				? homeplanet.parser.Expeditions.openRansom(homeplanet.vault.Vault.get(), m.key) : null;
		payRansom.setVisible(captive != null);
		refuseRansom.setVisible(captive != null);
		if (captive != null) payRansom.setText("Pay " + captive.ransom + " scrap");
		homeplanet.parser.Assignments.Pending prize = m.key.startsWith("expedition:") && homeplanet.vault.Vault.isOpen() ? homeplanet.parser.Assignments.pendingFor(homeplanet.vault.Vault.get(), m.key) : null;
		boolean recruit = prize != null && "recruit".equals(prize.kind), ship = prize != null && "ship".equals(prize.kind);
		prizeYes.setVisible(recruit); prizeDock.setVisible(ship); prizeJunk.setVisible(ship); prizeNo.setVisible(prize != null);
		prizeNo.setText(ship ? "Don't take her" : "Send them on their way");
		archive.setVisible(true);
		boolean held = parcel != null && (homeplanet.comm.Shipments.HELD.equals(parcel.state) || homeplanet.comm.Shipments.RETURNING.equals(parcel.state));
		delete.setVisible(Transmissions.isReceipt(m) || Transmissions.isNote(m) || (m.key.startsWith("parcel:") && !held) || m.key.startsWith("expedition:") || Transmissions.isShipped(m)); // they pile up: archive one or be rid of it (not a shipment still to deal with)
		boolean waiting = parcel != null && homeplanet.comm.Shipments.HELD.equals(parcel.state);
		String whyNot = waiting ? homeplanet.comm.Shipments.whyNot(parcel) : null;
		takeIt.setVisible(waiting);
		takeIt.setEnabled(whyNot == null);
		takeIt.setToolTipText(whyNot == null ? "Into this fleet's Cargo Hold" : "<html><div style='width:320px'>" + homeplanet.parser.XmlText.text(whyNot) + "</div></html>");
		java.util.List<String> fleets = waiting ? homeplanet.comm.Shipments.otherFleets(parcel) : new java.util.ArrayList<String>();
		elsewhere.setVisible(waiting);
		elsewhere.setEnabled(!fleets.isEmpty());
		elsewhere.setToolTipText(fleets.isEmpty() ? "None of your other fleets may take it (the trading rules), or they have no Cargo Hold yet"
				: "Into the Cargo Hold of another of your fleets that may trade with them (it needn't be the one in use)");
		sendBack.setVisible(waiting);
		delete.setToolTipText(Transmissions.isShipped(m) ? "Delete this letter for good" + (m.hasReward() && !m.claimed ? ", and the augment with it" : "")
				: m.key.startsWith("expedition:") ? "Delete this report for good: the expedition stays in the station's history"
				: Transmissions.isNote(m) ? "Delete this message for good" : "Delete this receipt for good: the trade stays in the station's history");
		boolean stipend = Transmissions.deletable(m);
		boolean unclaimed = Transmissions.unclaimedStipend(m) && !m.archived;
		archive.setText(stipend || unclaimed ? "Delete" : m.archived ? "Move to Inbox" : "Archive");
		archive.setEnabled(!unclaimed);
		archive.setToolTipText(unclaimed ? "Claim the stipend first: it stays in the inbox until its scrap is in the Cargo Hold"
				: stipend ? (Transmissions.isStipend(m) ? "Delete this notice: the scrap is already in the Cargo Hold" : "Delete this order: its free command has been taken") : m.archived ? "Back to the inbox" : "Store it in the Archive tab, out of the inbox");
		rewardLabel.setForeground(canClaim ? new Color(40, 150, 60) : Color.GRAY);
		rewardLabel.setText(Transmissions.isRescue(m) ? (m.claimed ? m.claimedWhat : " ") : !m.hasReward() ? " " : m.claimed ? "Claimed: " + m.claimedWhat : "Reward: " + Transmissions.describeReward(m)
				+ (Transmissions.price(m) > 0 ? ", for " + Transmissions.price(m) + " scrap" : ""));
		if (Transmissions.isRescue(m)) rewardLabel.setForeground(Color.GRAY);
		Transmissions.markRead(m);
		list.repaint();
	}

	/** An expedition letter's prize: 0 the recruit signed on, 1 the ship to the Space Dock, 2 to the Junkyard, 3 not taken. */
	private void prize(int choice) {
		Transmissions.Message m = list.getSelectedValue();
		if (m == null) return;
		homeplanet.vault.Vault v = homeplanet.vault.Vault.get();
		homeplanet.parser.Assignments.Pending x = homeplanet.parser.Assignments.pendingFor(v, m.key);
		if (x == null) { show(m); return; }
		String settled;
		try {
			if (choice == 3) {
				if (!HomePlanet.confirmNo(this, ("ship".equals(x.kind) ? x.name + " will be left where she lies." : x.name + " will go their own way.") + " Gone for good?", "Expeditions")) return;
				homeplanet.parser.Assignments.decline(v, x);
				settled = "ship".equals(x.kind) ? "Not taken" : "Sent on their way";
			} else {
				homeplanet.parser.Assignments.accept(v, x, choice == 1);
				settled = choice == 0 ? x.name + " signed on: in the Cargo Hold" : x.name + (choice == 1 ? " docked at the Space Dock" : " to the Junkyard");
			}
		} catch (Exception e) {
			HomePlanet.showErrorDialog("That could not be done. Nothing was changed:\n" + e.getMessage());
			return;
		}
		try { Transmissions.decided(m, settled); }
		catch (Exception e) { HomePlanet.showErrorDialog("Done, but the letter could not be marked as settled (it will show no note of it):\n" + e.getMessage()); }
		all = Transmissions.load();
		fill();
	}
	/** A ransom letter: pay it from the Cargo Hold (they come back), or refuse (they don't). */
	private void ransom(boolean pay) {
		Transmissions.Message m = list.getSelectedValue();
		if (m == null) return;
		homeplanet.vault.Vault v = homeplanet.vault.Vault.get();
		homeplanet.parser.Expeditions.Captive c = homeplanet.parser.Expeditions.openRansom(v, m.key);
		if (c == null) { show(m); return; }
		String settled; // the ransom first: if that fails, nothing has changed; the letter's note after, and a failure there says only what it is
		try {
			if (pay) {
				if (!HomePlanet.confirmNo(this, "Pay " + c.ransom + " scrap from the Cargo Hold for " + c.name + "'s return?", "Ransom")) return;
				homeplanet.parser.Expeditions.payRansom(v, c);
				settled = "Paid " + c.ransom + " scrap: " + c.name + " is back in the Cargo Hold";
			} else {
				if (!HomePlanet.confirmNo(this, "Refuse the ransom? " + c.name + " will not be coming back.", "Ransom")) return;
				homeplanet.parser.Expeditions.refuseRansom(v, c);
				settled = "Refused";
			}
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The ransom wasn't settled. Nothing was changed:\n" + e.getMessage());
			all = Transmissions.load();
			fill();
			return;
		}
		try {
			Transmissions.decided(m, settled);
		} catch (Exception e) {
			HomePlanet.showErrorDialog((pay ? "The ransom is paid and " + c.name + " is back in the Cargo Hold" : "The ransom was refused")
					+ ", but the letter could not be marked as settled (it will show no note of it):\n" + e.getMessage());
		}
		if (pay) JOptionPane.showMessageDialog(this, c.name + " is back in the Cargo Hold: shaken, thinner, but whole.", "Ransom", JOptionPane.INFORMATION_MESSAGE);
		all = Transmissions.load(); // the Ambassador's letter, if one came
		fill();
	}

	/** A rescued ship's offer: keep her, or the museum's price. */
	private void decide(boolean keepHer) {
		Transmissions.Message m = list.getSelectedValue();
		if (m == null || m.claimed) return;
		try {
			homeplanet.vault.Vault.FinalBattle f = homeplanet.parser.FinalVictory.offer(Transmissions.rescueId(m));
			net.blerf.ftl.constants.Difficulty d = null;
			if (f != null && keepHer && (d = SpaceDockUI.keptDifficulty(this, f)) == null) return; // the offer stays open
			String what = f == null ? "Already settled." : keepHer ? homeplanet.parser.FinalVictory.keep(f, d) : homeplanet.parser.FinalVictory.museum(f);
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
		String[] from = m == null ? null : replyTo(m);
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
			JOptionPane.showMessageDialog(this, "Reply sent. Expect an answer within a few days." + (note == null ? "" : "\n\n" + note), "Reply", JOptionPane.INFORMATION_MESSAGE);
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
