package homeplanet.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

/**
 * The pieces the Cargo Bay is built from, in the Space Dock's style: dark lists with scroll bars, gold section headers,
 * FTL-font text, small icon buttons, and the dark cut-corner boxes.
 */
final class CargoParts {
	private CargoParts() { }

	static final Color GOLD = FtlButton.GOLD, TEXT = FtlButton.TEXT, LINE = FtlButton.LINE;
	static final Color DIM = MenuTheme.GREY_GREEN;
	static final Color BOX = new Color(16, 20, 26, 215), BOX_LINE = new Color(214, 230, 222, 150);
	static final Color SEL = new Color(250, 210, 120, 70);
	static final Color ORANGE = MenuTheme.ORANGE;

	/** A cut-corner outline, the shape of every box and button. */
	static Polygon cut(int x, int y, int w, int h, int c) {
		return new Polygon(new int[] {x + c, x + w - c, x + w, x + w, x + w - c, x + c, x, x},
				new int[] {y, y, y + c, y + h - c, y + h, y + h, y + h - c, y + c}, 8);
	}
	/** Paints the dark box behind a list or a cell. */
	static void paintBox(Graphics2D g, int x, int y, int w, int h, Color line) {
		Polygon p = cut(x, y, w - 1, h - 1, 4);
		g.setColor(BOX);
		g.fillPolygon(p);
		g.setColor(line);
		g.setStroke(new BasicStroke(1f));
		g.drawPolygon(p);
	}
	/** FTL text with a drop shadow. */
	static void text(Graphics g, String s, FtlFont f, Color c, int x, int y) {
		g.drawImage(f.render(s, Color.black), x + 1, y + 1, null);
		g.drawImage(f.render(s, c), x, y, null);
	}
	static int width(String s, FtlFont f) { return f.render(s, TEXT).getWidth(); }

	/** A gold section header with a rule running out from it (to the right, or to the left when the text sits right). */
	static class Header extends JComponent {
		private String text;
		private final boolean right;
		Header(String text, boolean right) { this.text = text.toUpperCase(); this.right = right; setOpaque(false); }
		void setText(String t) { text = t.toUpperCase(); repaint(); }
		@Override protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			BufferedImage t = FtlFont.MENU.render(text, GOLD);
			int y = (getHeight() - t.getHeight()) / 2;
			int x = right ? getWidth() - t.getWidth() : 0;
			g.drawImage(FtlFont.MENU.render(text, Color.black), x + 1, y + 1, null);
			g.drawImage(t, x, y, null);
			g.setColor(GOLD);
			g.setStroke(new BasicStroke(2f));
			int ly = y + t.getHeight() / 2;
			if (right) { if (x - 10 > 0) g.drawLine(0, ly, x - 10, ly); }
			else if (t.getWidth() + 10 < getWidth()) g.drawLine(t.getWidth() + 10, ly, getWidth() - 1, ly);
			g.dispose();
		}
	}

	/** A line of FTL text (with shadow), cut to fit; left, right or centred. */
	static class Label extends JComponent {
		private String text;
		private final FtlFont font;
		private Color color;
		private final int align; // -1 left, 0 centre, 1 right
		Label(String text, FtlFont font, Color color, int align) { this.text = text; this.font = font; this.color = color; this.align = align; setOpaque(false); }
		void setText(String t) { text = t == null ? "" : t; repaint(); }
		void setColor(Color c) { color = c; repaint(); }
		String getText() { return text; }
		@Override protected void paintComponent(Graphics g) {
			String s = font.fit(text, getWidth() - 2);
			int w = width(s, font);
			int x = align < 0 ? 0 : align == 0 ? (getWidth() - w) / 2 : getWidth() - w - 1;
			int y = (getHeight() - font.render(s, color).getHeight()) / 2;
			text(g, s, font, color, x, y);
		}
	}

	/** An FTL button showing an icon instead of text (the trash can, the scrap cog). */
	static class IconButton extends FtlButton {
		private final Icon icon;
		IconButton(Icon icon, String tip, ActionListener a) {
			super("", FtlFont.BODY, 24, 22);
			this.icon = icon;
			setToolTipText(tip);
			addActionListener(a);
		}
		@Override protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (icon == null) return;
			Graphics2D g2 = (Graphics2D) g.create();
			if (!isEnabled()) g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f));
			boolean hot = isEnabled() && getModel().isRollover() && !getModel().isPressed();
			Icon ic = hot && icon instanceof ImageIcon ? dark((ImageIcon) icon) : icon;
			ic.paintIcon(this, g2, (getWidth() - ic.getIconWidth()) / 2, (getHeight() - ic.getIconHeight()) / 2);
			g2.dispose();
		}
		private Icon darkIcon;
		private Icon dark(ImageIcon src) { // on the light hover fill, the white icon goes dark
			if (darkIcon == null) {
				BufferedImage b = new BufferedImage(src.getIconWidth(), src.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = b.createGraphics(); g.drawImage(src.getImage(), 0, 0, null); g.dispose();
				for (int y = 0; y < b.getHeight(); y++) for (int x = 0; x < b.getWidth(); x++) b.setRGB(x, y, (b.getRGB(x, y) & 0xFF000000) | 0x1a2026);
				darkIcon = new ImageIcon(b);
			}
			return darkIcon;
		}
	}

	/** Styles a pop-up menu dark, like the rest of the Cargo Bay. */
	static void darkPopup(javax.swing.JPopupMenu m) {
		m.setBorder(BorderFactory.createLineBorder(LINE));
		m.setBackground(new Color(28, 36, 44));
		for (Component c : m.getComponents()) {
			if (!(c instanceof javax.swing.JMenuItem)) continue;
			javax.swing.JMenuItem it = (javax.swing.JMenuItem) c;
			it.setOpaque(true);
			it.setBackground(new Color(28, 36, 44));
			it.setForeground(it.isEnabled() ? TEXT : DIM);
			it.setFont(it.getFont().deriveFont(java.awt.Font.BOLD, 13f));
			it.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		}
	}

	private static Icon info;
	/** A small white "i" in a ring (Info). */
	static Icon infoIcon() {
		if (info != null) return info;
		BufferedImage b = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = b.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(TEXT);
		g.setStroke(new BasicStroke(1.6f));
		g.drawOval(1, 1, 13, 13);
		g.fillRect(7, 6, 2, 6);
		g.fillRect(7, 3, 2, 2);
		g.dispose();
		return info = new ImageIcon(b);
	}

	private static Icon trash;
	/** A small white trash can (Junk). */
	static Icon trashIcon() {
		if (trash != null) return trash;
		BufferedImage b = new BufferedImage(14, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = b.createGraphics();
		g.setColor(TEXT);
		g.fillRect(1, 2, 12, 2);
		g.fillRect(5, 0, 4, 2);
		g.fillRect(2, 5, 10, 11);
		g.setComposite(java.awt.AlphaComposite.Clear);
		g.fillRect(4, 7, 1, 7); g.fillRect(6, 7, 1, 7); g.fillRect(9, 7, 1, 7);
		g.dispose();
		return trash = new ImageIcon(b);
	}

	/** One row of a Cargo Bay list: an icon, a name, a note on the right (a count, "in cargo", a race), and what it stands for. */
	static class Row {
		final Icon icon;
		final String name, note, tip;
		final Object value;
		final boolean dim;
		/** A thin bar under the icon (a crew member's health), 0 to 1, or -1 for none; its colour, and the colour of the rest (null: dark). */
		float bar = -1;
		Color barColor, barRest;
		Row(Icon icon, String name, String note, Object value) { this(icon, name, note, value, null, false); }
		Row(Icon icon, String name, String note, Object value, String tip, boolean dim) {
			this.icon = icon; this.name = name; this.note = note; this.value = value; this.tip = tip; this.dim = dim;
		}
		Row bar(float fill, Color c, Color rest) { bar = fill; barColor = c; barRest = rest; return this; }
	}

	/** A dark list of rows in a scroll pane with a dark scroll bar. */
	static class RowList extends JScrollPane {
		final DefaultListModel<Row> model = new DefaultListModel<Row>();
		final JList<Row> list = new JList<Row>(model) {
			@Override public String getToolTipText(MouseEvent e) {
				int i = locationToIndex(e.getPoint());
				if (i < 0 || !getCellBounds(i, i).contains(e.getPoint())) return null;
				return model.get(i).tip;
			}
		};
		private String empty = "None";
		RowList() {
			super(VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
			list.setOpaque(false);
			list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			list.setCellRenderer(new RowRenderer());
			list.setFixedCellHeight(21);
			list.setToolTipText(""); // turns tooltips on; each row gives its own
			setViewportView(list);
			setOpaque(false);
			getViewport().setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));
			getVerticalScrollBar().setUI(new MenuTheme.DarkScrollBarUI(new Color(214, 230, 222, 170), new Color(40, 50, 58, 200)));
			getVerticalScrollBar().setPreferredSize(new Dimension(10, 10));
			getVerticalScrollBar().setOpaque(false);
			getVerticalScrollBar().setUnitIncrement(21);
		}
		void setEmptyText(String s) { empty = s; }
		/** Replaces the rows, keeping the selection on the same thing when it's still there. */
		void setRows(List<Row> rows) {
			Object keep = selectedValue();
			model.clear();
			int sel = -1;
			for (int i = 0; i < rows.size(); i++) {
				model.addElement(rows.get(i));
				if (keep != null && sel < 0 && keep.equals(rows.get(i).value)) sel = i;
			}
			if (sel >= 0) list.setSelectedIndex(sel); else list.clearSelection();
		}
		Object selectedValue() { Row r = list.getSelectedValue(); return r == null ? null : r.value; }
		void onChange(final Runnable r) {
			list.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
				public void valueChanged(javax.swing.event.ListSelectionEvent e) { if (!e.getValueIsAdjusting()) r.run(); }
			});
		}
		void onDoubleClick(final Runnable r) {
			list.addMouseListener(new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2 && list.getSelectedIndex() >= 0) r.run(); }
			});
		}
		@Override protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			paintBox(g, 0, 0, getWidth(), getHeight(), BOX_LINE);
			if (model.isEmpty()) {
				String s = empty;
				text(g, s, FtlFont.BODY, DIM, (getWidth() - width(s, FtlFont.BODY)) / 2, getHeight() / 2 - 7);
			}
			g.dispose();
		}
	}

	/** Where a row's bar sits: under the icon, clear of the selection's outline. */
	static final int BAR_Y = 17, BAR_H = 2, BAR_W = 24;
	static class RowRenderer extends JComponent implements ListCellRenderer<Row> {
		private Row row;
		private boolean selected;
		private int w;
		public Component getListCellRendererComponent(JList<? extends Row> list, Row value, int index, boolean isSelected, boolean hasFocus) {
			row = value;
			selected = isSelected;
			w = list.getWidth();
			return this;
		}
		@Override public Dimension getPreferredSize() { return new Dimension(100, 21); }
		@Override protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			int width = getWidth();
			if (selected) {
				g.setColor(SEL);
				g.fillRect(1, 0, width - 3, 20);
				g.setColor(GOLD);
				g.drawRect(1, 0, width - 3, 19);
			}
			int tx = 6;
			if (row.icon != null) {
				row.icon.paintIcon(this, g, 5 + (30 - row.icon.getIconWidth()) / 2, 10 - row.icon.getIconHeight() / 2);
				tx = 40;
			}
			int noteW = row.note == null ? 0 : width(row.note, FtlFont.BODY) + 14;
			Color c = row.dim ? DIM : selected ? GOLD : TEXT;
			text(g, FtlFont.BODY.fit(row.name, width - tx - noteW - 6), FtlFont.BODY, c, tx, 4);
			if (row.note != null) text(g, row.note, FtlFont.BODY, DIM, width - noteW + 4, 4);
			if (row.bar >= 0) { // under the icon, as FTL draws a crew member's health under the portrait: the fill, then the rest
				int bx = 5 + (30 - BAR_W) / 2, fill = Math.max(1, Math.round(BAR_W * Math.min(1f, row.bar)));
				g.setColor(row.barRest != null ? row.barRest : new Color(0, 0, 0, 110));
				g.fillRect(bx, BAR_Y, BAR_W, BAR_H);
				g.setColor(row.barColor);
				g.fillRect(bx, BAR_Y, fill, BAR_H);
			}
			g.dispose();
		}
	}

	/** A dark box that can be clicked to select it (the supply cells). */
	static class Cell extends JComponent {
		boolean selected;
		Cell() { setOpaque(false); }
		@Override protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			paintBox(g, 0, 0, getWidth(), getHeight(), selected ? GOLD : BOX_LINE);
			if (selected) {
				g.setColor(GOLD);
				g.setStroke(new BasicStroke(2f));
				g.drawPolygon(cut(1, 1, getWidth() - 3, getHeight() - 3, 4));
			}
			g.dispose();
		}
	}
}
