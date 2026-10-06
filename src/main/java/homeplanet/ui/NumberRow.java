package homeplanet.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * One number of a design, the way heromedel drew it: hers over the vanilla max, the name, minus, a typed field, plus,
 * then a bar of green segments to the game's own ceiling and amber ones past it, with "Over vanilla max" beside.
 * Nothing is capped: past the ceiling is the player's call. An optional tick in front (a system installed at the start).
 */
public class NumberRow extends JPanel {
	private static final Color OVER = new Color(214, 160, 40);

	private final String name;
	private final int max, floor;
	private int value;
	private final JLabel fraction = new JLabel(), over = new JLabel("Over vanilla max");
	private final JTextField field = new JTextField(3);
	private final JCheckBox tick;
	private final LevelBar bar;
	private final JPanel extras = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
	private ActionListener onChange;

	/**
	 * @param name what the number is called
	 * @param value where it starts
	 * @param max the vanilla max (the bar's green length)
	 * @param floor the least it can be (0 for missiles, 1 for a system's level)
	 * @param tickable whether the row has a tick in front (a system: installed at the start)
	 */
	public NumberRow(String name, int value, int max, int floor, boolean tickable) {
		super(new FlowLayout(FlowLayout.LEFT, 3, 1));
		this.name = name; this.max = max; this.floor = floor; this.value = Math.max(floor, value);
		bar = new LevelBar(this.value, max);
		tick = tickable ? new JCheckBox() : null;
		fraction.setFont(MenuTheme.TEXT_FONT);
		fraction.setText("00/00");
		fraction.setPreferredSize(new Dimension(38, fraction.getPreferredSize().height));
		fraction.setHorizontalAlignment(JLabel.RIGHT);
		add(fraction);
		if (tick != null) { tick.setMargin(new java.awt.Insets(0, 0, 0, 0)); add(tick); }
		JLabel l = new JLabel(name + ":");
		l.setPreferredSize(new Dimension(108, l.getPreferredSize().height));
		add(l);
		add(small("−", -1));
		field.setHorizontalAlignment(JTextField.CENTER);
		field.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { typed(); } });
		field.addFocusListener(new java.awt.event.FocusAdapter() { public void focusLost(java.awt.event.FocusEvent e) { typed(); } });
		add(field);
		add(small("+", 1));
		add(bar);
		over.setFont(MenuTheme.TEXT_FONT.deriveFont(11f));
		over.setForeground(OVER);
		add(over);
		add(extras);
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);
		showValue();
	}
	private JButton small(String t, final int by) {
		JButton b = new JButton(t);
		b.setMargin(new java.awt.Insets(0, 4, 0, 4));
		b.setFocusable(false);
		b.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { set(typedValue() + by, true); } }); // from what's typed: the buttons take no focus, so the field hasn't committed
		return b;
	}
	private void typed() { set(typedValue(), true); }
	/** What the field says, or the value when it isn't a number. */
	private int typedValue() {
		try { return Integer.parseInt(field.getText().trim()); } catch (NumberFormatException e) { return value; }
	}
	private void showValue() {
		fraction.setText(value + "/" + max); // (past the bar's reach the bar stands down: the fraction says it)
		fraction.setForeground(value > max ? OVER : MenuTheme.DIM);
		field.setText(String.valueOf(value));
		over.setVisible(value > max);
		bar.set(value, max);
		revalidate();
	}
	/** Sets the value (never below the floor); {@code tell} runs the change listener. */
	public void set(int v, boolean tell) {
		v = Math.max(floor, v);
		boolean changed = v != value;
		value = v;
		showValue();
		if (changed && tell && onChange != null) onChange.actionPerformed(new ActionEvent(this, 0, name));
	}
	public int get() { return value; }
	public boolean overMax() { return value > max; }
	/** The tick in front (null when the row has none). */
	public JCheckBox tick() { return tick; }
	/** Something more at the row's end (the artillery's weapon button). */
	public void addExtra(JComponent c) { extras.add(c); extras.setOpaque(false); }
	/** Told after a change by the player (not by {@link #set} with tell false). */
	public void onChange(ActionListener a) { onChange = a; if (tick != null) tick.addActionListener(a); }
	public String name() { return name; }
	/** A tooltip on the name and the field. */
	public void setTip(String tip) { setToolTipText(tip); field.setToolTipText(tip); }
	/** A row with no border of its own, for a list. */
	public NumberRow plain() { setBorder(BorderFactory.createEmptyBorder()); return this; }
}
