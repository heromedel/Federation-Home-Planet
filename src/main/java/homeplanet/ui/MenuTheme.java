package homeplanet.ui;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.awt.event.HierarchyEvent;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JWindow;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.text.JTextComponent;

/**
 * Dresses the station's pop-up windows (Settings, reports, messages...) in the station's dark blue, to match the Space Dock
 * buttons. Only dialogs: the main window, and so the Cargo Bay, keep their own look.
 * Colours a program sets on purpose (a warning in orange, a picture's backdrop) are kept; only the default ones change.
 */
public final class MenuTheme {
	public static final Color BG = new Color(28, 36, 44);
	public static final Color FIELD = new Color(44, 56, 68);
	public static final Color BUTTON = new Color(52, 66, 78);
	public static final Color TEXT = new Color(228, 237, 232);
	public static final Color DIM = new Color(140, 152, 150);
	public static final Color SELECT = new Color(78, 106, 122);
	// The style guide's colours (docs/STYLE.md): one of each
	public static final Color GOLD = new Color(250, 210, 120), WHITE = Color.white, GREY_GREEN = new Color(170, 185, 180),
			GREEN = new Color(120, 215, 140), ORANGE = new Color(255, 170, 90), RED = new Color(235, 110, 95);
	/** The same colours for HTML text (#rrggbb). */
	public static final String HTML_GOLD = "#fad278", HTML_GREY_GREEN = "#aab9b4", HTML_ORANGE = "#ffaa5a", HTML_RED = "#eb6e5f";
	/** Normal text and labels (docs/STYLE.md). */
	public static final Font TEXT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12), LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
	/** Section headings in a dialog (docs/STYLE.md), in gold. */
	public static final Font HEADING_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);

	private static boolean installed = false;

	private MenuTheme() { }

	/** Call once at startup. */
	public static synchronized void install() {
		if (installed) return;
		installed = true;
		// Colours Swing only uses inside message boxes and titled frames (not in the Cargo Bay)
		UIManager.put("OptionPane.background", new ColorUIResource(BG));
		UIManager.put("OptionPane.messageForeground", new ColorUIResource(TEXT));
		UIManager.put("TitledBorder.titleColor", new ColorUIResource(TEXT));
		UIManager.put("OptionPaneUI", WrappingOptionPaneUI.class.getName()); // every pop-up wraps its long lines (heromedel, 6.27: one ran off the screen)
		UIManager.put(WrappingOptionPaneUI.class.getName(), WrappingOptionPaneUI.class);
		UIManager.put("ToolTipUI", WrappingToolTipUI.class.getName()); // and every long tooltip (6.28: 41 ran out in one strip)
		UIManager.put(WrappingToolTipUI.class.getName(), WrappingToolTipUI.class);
		Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
			public void eventDispatched(AWTEvent e) {
				try {
					if (e instanceof HierarchyEvent) {
						HierarchyEvent h = (HierarchyEvent) e;
						if ((h.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && h.getChanged() instanceof Window
								&& h.getChanged() == h.getComponent() && h.getChanged().isDisplayable() && themed((Window) h.getChanged())) {
							apply(h.getChanged());
						}
					} else if (e instanceof ContainerEvent && e.getID() == ContainerEvent.COMPONENT_ADDED) {
						ContainerEvent c = (ContainerEvent) e;
						Window w = c.getContainer() instanceof Window ? (Window) c.getContainer() : javax.swing.SwingUtilities.getWindowAncestor(c.getContainer());
						if (w != null && themed(w)) apply(c.getChild());
					}
				} catch (RuntimeException ex) {
					// a colour is never worth a crash
				}
			}
		}, AWTEvent.HIERARCHY_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
	}

	/** Dialogs, and the drop-down lists that pop out of them. */
	private static boolean themed(Window w) {
		if (w instanceof JDialog) return true;
		if (w instanceof JWindow && w.getOwner() instanceof JDialog) return true;
		return false;
	}

	private static boolean isDefault(Color c) {
		return c == null || c instanceof UIResource;
	}
	private static boolean dark(Color c) {
		return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) < 110;
	}

	/** A combo box whose arrow is drawn in the text colour on our button colour, and whose list is themed like the fields. */
	private static class DarkComboBoxUI extends javax.swing.plaf.basic.BasicComboBoxUI {
		@Override protected JButton createArrowButton() {
			// drawn here, enabled or not: Swing's own greys out with a white outline that glares on the dark theme
			JButton b = new JButton() {
				@Override protected void paintComponent(java.awt.Graphics g0) {
					java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
					g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
					boolean on = comboBox == null || comboBox.isEnabled();
					g.setColor(on ? BUTTON : BG);
					g.fillRect(0, 0, getWidth(), getHeight());
					int w = 8, h = 4, x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
					g.setColor(on ? TEXT : DIM);
					g.fillPolygon(new int[] {x, x + w, x + w / 2}, new int[] {y, y, y + h + 1}, 3);
					g.dispose();
				}
			};
			b.setName("ComboBox.arrowButton");
			b.setBorder(javax.swing.BorderFactory.createEmptyBorder());
			b.setFocusable(false);
			b.setContentAreaFilled(false);
			b.setPreferredSize(new java.awt.Dimension(18, 18));
			return b;
		}
		/** Greyed out (a rule Immersive Mode sets, say): dimmed on the theme's own colours, not Swing's white. */
		@Override public void paintCurrentValueBackground(java.awt.Graphics g, java.awt.Rectangle bounds, boolean hasFocus) {
			g.setColor(comboBox.isEnabled() ? comboBox.getBackground() : BG);
			g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
		}
		@Override public void paintCurrentValue(java.awt.Graphics g, java.awt.Rectangle bounds, boolean hasFocus) {
			if (comboBox.isEnabled()) { super.paintCurrentValue(g, bounds, hasFocus); return; }
			@SuppressWarnings("unchecked")
			javax.swing.ListCellRenderer<Object> r = (javax.swing.ListCellRenderer<Object>) comboBox.getRenderer();
			Component c = r.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);
			c.setFont(comboBox.getFont());
			c.setForeground(DIM);
			c.setBackground(BG);
			currentValuePane.paintComponent(g, c, comboBox, bounds.x, bounds.y, bounds.width, bounds.height, c instanceof javax.swing.JPanel);
		}
		@Override protected javax.swing.plaf.basic.ComboPopup createPopup() {
			javax.swing.plaf.basic.BasicComboPopup p = (javax.swing.plaf.basic.BasicComboPopup) super.createPopup();
			p.getList().setBackground(FIELD);
			p.getList().setForeground(TEXT);
			p.getList().setSelectionBackground(SELECT);
			p.getList().setSelectionForeground(Color.white);
			return p;
		}
	}

	/** Recolours the component and everything inside it. */
	/** The open tab's name in dark on its light tab, the others as the theme draws them (as Settings does). */
	public static void markOpenTab(final javax.swing.JTabbedPane t) {
		final Color normal = new Color(220, 228, 235), open = new Color(20, 28, 40);
		javax.swing.event.ChangeListener mark = new javax.swing.event.ChangeListener() {
			public void stateChanged(javax.swing.event.ChangeEvent e) {
				for (int i = 0; i < t.getTabCount(); i++) t.setForegroundAt(i, i == t.getSelectedIndex() ? open : normal);
			}
		};
		t.addChangeListener(mark);
		mark.stateChanged(null);
	}
	public static void apply(Component c) {
		if (c == null || c instanceof FtlButton) return;
		boolean field = c instanceof JTextComponent || c instanceof JList || c instanceof JTable || c instanceof JComboBox;
		boolean button = c instanceof JButton || (c instanceof JToggleButton && !(c instanceof JCheckBox) && !(c instanceof JRadioButton));
		if (c instanceof JScrollBar) {
			JScrollBar sb = (JScrollBar) c;
			if (!(sb.getUI() instanceof DarkScrollBarUI)) sb.setUI(new DarkScrollBarUI(SELECT, FIELD));
		} else if (isDefault(c.getBackground())) {
			c.setBackground(field ? FIELD : button ? BUTTON : BG);
		}
		// an HTML view keeps its own black text unless told to use the component's colour and font
		if (c instanceof javax.swing.JEditorPane) ((javax.swing.JEditorPane) c).putClientProperty(javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		Color fg = c.getForeground();
		if (isDefault(fg)) {
			c.setForeground(TEXT);
		} else if (fg != null && dark(fg) && !(c instanceof JScrollBar)) {
			c.setForeground(TEXT); // a dark colour chosen for the old light grey would vanish on the blue
		}
		if (c instanceof JTextComponent) {
			JTextComponent t = (JTextComponent) c;
			if (isDefault(t.getCaretColor())) t.setCaretColor(TEXT);
			if (isDefault(t.getSelectionColor())) t.setSelectionColor(SELECT);
			if (isDefault(t.getSelectedTextColor())) t.setSelectedTextColor(Color.white);
			if (isDefault(t.getDisabledTextColor())) t.setDisabledTextColor(DIM);
		}
		if (c instanceof JComboBox && !(((JComboBox<?>) c).getUI() instanceof DarkComboBoxUI)) {
			((JComboBox<?>) c).setUI(new DarkComboBoxUI()); // the Windows look paints its arrow with its own skin, which vanishes on our dark button
		}
		if (c instanceof JList) {
			JList<?> l = (JList<?>) c;
			if (isDefault(l.getSelectionBackground())) l.setSelectionBackground(SELECT);
			if (isDefault(l.getSelectionForeground())) l.setSelectionForeground(Color.white);
		}
		if (c instanceof JTable) {
			JTable t = (JTable) c;
			if (isDefault(t.getSelectionBackground())) t.setSelectionBackground(SELECT);
			if (isDefault(t.getSelectionForeground())) t.setSelectionForeground(Color.white);
			if (isDefault(t.getGridColor())) t.setGridColor(BUTTON);
		}
		if (c instanceof AbstractButton && !button) ((AbstractButton) c).setOpaque(false); // check boxes and radio buttons sit on the panel
		if (c instanceof JSpinner) {
			JComponent ed = ((JSpinner) c).getEditor();
			if (ed != null) apply(ed);
		}
		if (c instanceof JLabel && !((JLabel) c).isOpaque()) { /* the panel shows through */ }
		if (c instanceof Container) {
			for (Component child : ((Container) c).getComponents()) apply(child);
		}
		if (c instanceof javax.swing.JRootPane) {
			javax.swing.JRootPane r = (javax.swing.JRootPane) c;
			apply(r.getContentPane());
		}
	}

	/** A plain dark scroll bar: a flat thumb on a flat track, no arrow buttons. */
	public static class DarkScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
		private final Color thumb, track;
		public DarkScrollBarUI(Color thumb, Color track) { this.thumb = thumb; this.track = track; }
		@Override protected void configureScrollBarColors() { thumbColor = thumb; trackColor = track; }
		@Override protected JButton createDecreaseButton(int o) { return noArrow(); }
		@Override protected JButton createIncreaseButton(int o) { return noArrow(); }
		private static JButton noArrow() { JButton b = new JButton(); b.setPreferredSize(new java.awt.Dimension(0, 0)); return b; }
		@Override protected void paintTrack(java.awt.Graphics g, JComponent c, java.awt.Rectangle r) {
			if (track.getAlpha() == 0) return;
			g.setColor(track);
			g.fillRect(r.x, r.y, r.width, r.height);
		}
		@Override protected void paintThumb(java.awt.Graphics g, JComponent c, java.awt.Rectangle r) {
			if (r.isEmpty()) return;
			g.setColor(thumb);
			g.fillRect(r.x + 3, r.y + 2, r.width - 6, r.height - 4);
		}
	}

	/** A pop-up's plain text wrapped at about 100 characters a line (as HomePlanet.wrap does errors), between words; html sets its own width and is left as it is. */
	public static final class WrappingOptionPaneUI extends javax.swing.plaf.basic.BasicOptionPaneUI {
		public static javax.swing.plaf.ComponentUI createUI(JComponent c) { return new WrappingOptionPaneUI(); }
		@Override protected int getMaxCharactersPerLineCount() { return 100; }
		@Override protected void addMessageComponents(Container c, java.awt.GridBagConstraints g, Object msg, int maxll, boolean internal) {
			if (msg instanceof String && ((String) msg).regionMatches(true, 0, "<html>", 0, 6)) maxll = Integer.MAX_VALUE;
			super.addMessageComponents(c, g, msg, maxll, internal);
		}
	}

	/** A long plain tooltip wrapped to 400 pixels; short ones and html (which sets its own width) are left as they are. */
	public static final class WrappingToolTipUI extends javax.swing.plaf.metal.MetalToolTipUI {
		public static javax.swing.plaf.ComponentUI createUI(JComponent c) { return new WrappingToolTipUI(); }
		@Override public java.awt.Dimension getPreferredSize(JComponent c) {
			javax.swing.JToolTip tip = (javax.swing.JToolTip) c;
			String t = tip.getTipText();
			if (t != null && t.length() > 80 && !t.regionMatches(true, 0, "<html>", 0, 6))
				tip.setTipText("<html><div style='width:400px'>" + t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</div></html>");
			return super.getPreferredSize(c);
		}
	}
}
