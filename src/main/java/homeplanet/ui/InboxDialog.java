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
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import homeplanet.core.HomePlanet;
import homeplanet.parser.Transmissions;

/** The transmissions inbox: messages from The Federation Home Planet, newest first, with Claim for their rewards. */
public class InboxDialog extends JDialog {
	private final SpaceDockUI dock;
	private final DefaultListModel<Transmissions.Message> model = new DefaultListModel<Transmissions.Message>();
	private final JList<Transmissions.Message> list = new JList<Transmissions.Message>(model);
	private final JTextArea text = new JTextArea();
	private final JLabel rewardLabel = new JLabel(" ");
	private final JButton claim = new JButton("Claim");
	private final JButton commission = new JButton("Commission...");
	private final JButton archive = new JButton("Archive");
	private final javax.swing.JToggleButton inboxTab = new javax.swing.JToggleButton(), archiveTab = new javax.swing.JToggleButton();
	private java.util.List<Transmissions.Message> all;
	private boolean openCommission;

	/** Opens the inbox. True if the player asked to go to Commission (a commission order). */
	public static boolean open(SpaceDockUI dock) {
		InboxDialog d = new InboxDialog(dock);
		d.setVisible(true);
		return d.openCommission;
	}

	private InboxDialog(SpaceDockUI dock) {
		super(SwingUtilities.getWindowAncestor(dock), "Transmissions", ModalityType.APPLICATION_MODAL);
		this.dock = dock;
		all = Transmissions.load();
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
				Transmissions.Message m = (Transmissions.Message) v;
				String mark = m.hasReward() && !m.claimed ? "  [reward]" : "";
				super.getListCellRendererComponent(l, "<html>" + (m.read ? "" : "<b>") + homeplanet.parser.XmlText.text(m.subject) + (m.read ? "" : "</b>")
						+ "<br><font color='#888888'>" + homeplanet.parser.XmlText.text(m.from) + " · " + m.date + mark + "</font></html>", i, sel, focus);
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
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setFont(new Font(Font.SERIF, Font.PLAIN, 14));
		text.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		JScrollPane ts = new JScrollPane(text);
		ts.setPreferredSize(new Dimension(520, 420));
		JPanel right = new JPanel(new BorderLayout(0, 6));
		right.add(ts, BorderLayout.CENTER);
		JPanel act = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		act.add(claim);
		act.add(commission);
		act.add(archive);
		act.add(rewardLabel);
		right.add(act, BorderLayout.SOUTH);
		claim.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { claimSelected(); } });
		commission.setToolTipText("Go to Commission: the ship this order grants is marked free there");
		commission.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { openCommission = true; dispose(); } });
		archive.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { archiveSelected(); } });
		javax.swing.ButtonGroup tabs = new javax.swing.ButtonGroup();
		tabs.add(inboxTab);
		tabs.add(archiveTab);
		inboxTab.setSelected(true);
		ActionListener refill = new ActionListener() { public void actionPerformed(ActionEvent e) { fill(); } };
		inboxTab.addActionListener(refill);
		archiveTab.addActionListener(refill);
		JPanel tabRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		tabRow.add(inboxTab);
		tabRow.add(archiveTab);

		JPanel body = new JPanel(new BorderLayout(10, 8));
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		body.add(ls, BorderLayout.WEST);
		body.add(right, BorderLayout.CENTER);
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
		boolean arch = archiveTab.isSelected();
		int in = 0, out = 0;
		for (Transmissions.Message m : all) { if (m.archived) out++; else in++; }
		inboxTab.setText("Inbox (" + in + ")");
		archiveTab.setText("Archive (" + out + ")");
		model.clear();
		for (Transmissions.Message m : all) if (m.archived == arch) model.addElement(m);
		archive.setText(arch ? "Move to Inbox" : "Archive");
		archive.setToolTipText(arch ? "Back to the inbox" : "Store it in the Archive tab, out of the inbox");
		if (!model.isEmpty()) list.setSelectedIndex(0);
		else show(null);
		if (model.isEmpty()) text.setText(arch ? "Nothing archived. Archive a transmission to keep it here." : "No transmissions. The Federation Home Planet will be in touch.");
	}
	private void archiveSelected() {
		Transmissions.Message m = list.getSelectedValue();
		if (m == null) return;
		try {
			Transmissions.setArchived(m, !m.archived);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not file the transmission:\n" + e.getMessage());
		}
		fill();
	}

	private void show(Transmissions.Message m) {
		if (m == null) {
			text.setText("");
			claim.setVisible(false);
			commission.setVisible(false);
			archive.setVisible(false);
			rewardLabel.setText(" ");
			return;
		}
		text.setText("From: " + m.from + "\nReceived: " + m.date + "\nSubject: " + m.subject + "\n\n" + m.body);
		text.setCaretPosition(0);
		boolean canClaim = m.hasReward() && !m.claimed;
		claim.setVisible(m.hasReward());
		claim.setEnabled(canClaim);
		commission.setVisible(m.isOrder());
		archive.setVisible(true);
		rewardLabel.setForeground(canClaim ? new Color(40, 150, 60) : Color.GRAY);
		rewardLabel.setText(!m.hasReward() ? " " : m.claimed ? "Claimed: " + m.claimedWhat : "Reward: " + Transmissions.describeReward(m));
		Transmissions.markRead(m);
		list.repaint();
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
			JOptionPane.showMessageDialog(this, "Delivered to Spacedock Storage: " + what + ".", "Claim", JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not take delivery. Nothing was changed:\n" + e.getMessage());
		}
		show(m);
	}
}
