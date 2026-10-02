package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

import homeplanet.comm.Outbox;
import homeplanet.core.HomePlanet;

/**
 * The inbox's Outbox tab: what waits to go to commanders whose stations couldn't be reached, oldest first. Cancel
 * takes an item out; one their station turned away can be tried again. Delivery itself happens on its own, while the
 * hailing frequencies are open (see LongRangeCommUI).
 */
final class OutboxPanel extends JPanel {
	private final DefaultListModel<Outbox.Item> model = new DefaultListModel<Outbox.Item>();
	private final JList<Outbox.Item> list = new JList<Outbox.Item>(model);
	private final JTextArea text = new JTextArea();
	private final JLabel status = new JLabel(" ");
	private final JButton cancel = new JButton("Cancel it"), again = new JButton("Try again");
	/** Told when the count changes (the tab's label). */
	private final Runnable changed;

	OutboxPanel(Runnable changed) {
		super(new BorderLayout(10, 8));
		this.changed = changed;
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer() {
			@Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
				Outbox.Item it = (Outbox.Item) v;
				String state = it.refused.isEmpty() ? "waiting " + Outbox.waited(it.written) : "turned away";
				super.getListCellRendererComponent(l, "<html>To " + homeplanet.parser.XmlText.text(it.toTitle) + (it.priority ? " (priority)" : "")
						+ "<br><font color='" + MenuTheme.HTML_GREY_GREEN + "'>" + state + "</font></html>", i, sel, focus);
				setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
				return this;
			}
		});
		list.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
			public void valueChanged(javax.swing.event.ListSelectionEvent e) { if (!e.getValueIsAdjusting()) show(list.getSelectedValue()); }
		});
		JScrollPane ls = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		ls.setPreferredSize(new Dimension(360, 420));
		text.setEditable(false);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setFont(MenuTheme.TEXT_FONT);
		text.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		JScrollPane ts = new JScrollPane(text);
		ts.setPreferredSize(new Dimension(520, 420));
		JPanel right = new JPanel(new BorderLayout(0, 6));
		right.add(ts, BorderLayout.CENTER);
		JPanel act = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		act.add(cancel);
		act.add(again);
		act.add(status);
		right.add(act, BorderLayout.SOUTH);
		cancel.setToolTipText("Take it out of the Outbox: it won't be sent");
		again.setToolTipText("Their station turned it away: try once more, the next time your station finds theirs");
		cancel.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { cancelSelected(); } });
		again.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { againSelected(); } });
		add(ls, BorderLayout.WEST);
		add(right, BorderLayout.CENTER);
		fill();
	}

	/** How many wait (for the tab's label). */
	static int count() { return homeplanet.vault.Vault.isOpen() ? Outbox.list().size() : 0; }

	void fill() {
		List<Outbox.Item> all = Outbox.list();
		model.clear();
		for (Outbox.Item i : all) model.addElement(i);
		if (!model.isEmpty()) list.setSelectedIndex(0);
		else show(null);
		status.setText(all.isEmpty() ? " " : all.size() + " of " + Outbox.MAX + " places in use");
		if (changed != null) changed.run();
	}
	private void show(Outbox.Item i) {
		cancel.setVisible(i != null);
		again.setVisible(i != null && !i.refused.isEmpty());
		if (i == null) {
			text.setText("Nothing waits to go.\n\nA message for a commander whose station can't be reached can wait here: it goes the next time your station, its hailing frequencies open, finds theirs.");
			return;
		}
		text.setText("To " + i.toTitle + (i.priority ? " (priority)" : "") + ", waiting " + Outbox.waited(i.written) + "\n\n" + i.text
				+ (i.refused.isEmpty() ? "" : "\n\nTurned away by their station: " + i.refused));
		text.setCaretPosition(0);
	}
	private void cancelSelected() {
		Outbox.Item i = list.getSelectedValue();
		if (i == null || !HomePlanet.confirmNo(this, "Cancel the message to " + i.toTitle + "?\nIt won't be sent.", "Outbox")) return;
		try {
			Outbox.remove(i);
			homeplanet.core.HistoryLog.entry("LONG RANGE OUTBOX", "a message for " + i.toTitle + " cancelled");
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not cancel it:\n" + e.getMessage());
		}
		fill();
	}
	private void againSelected() {
		Outbox.Item i = list.getSelectedValue();
		if (i == null) return;
		try { Outbox.refused(i, ""); } catch (IOException e) { HomePlanet.showErrorDialog("The Home Planet Station could not change it:\n" + e.getMessage()); }
		fill();
	}
}
