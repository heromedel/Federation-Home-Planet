package homeplanet.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;

/** A button drawn like FTL's own: dark fill, pale cut-corner outline, the game's font; it lights up under the mouse. */
public class FtlButton extends JButton {

	static final Color FILL = new Color(28, 36, 44, 235);
	static final Color FILL_DOWN = new Color(60, 72, 80, 240);
	static final Color LINE = new Color(214, 230, 222);
	static final Color HOT = new Color(235, 245, 240);
	static final Color TEXT = new Color(235, 242, 238);
	static final Color TEXT_HOT = new Color(20, 24, 28);
	static final Color DIM = new Color(120, 130, 125);
	static final Color GOLD = new Color(250, 210, 120);

	private final FtlFont font;
	private boolean lit = false;
	/** Shows the button lit, as if the mouse were over it (the current tab). */
	public void setLit(boolean on) { lit = on; repaint(); }

	public FtlButton(String text, FtlFont font, int w, int h) {
		super(text);
		this.font = font;
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setOpaque(false);
		setRolloverEnabled(true);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		Dimension d = new Dimension(w, h);
		setPreferredSize(d);
		setMinimumSize(d);
		setMaximumSize(d);
	}

	/** Room kept clear at each end (a drop-down's arrow): the text is shortened to fit between. 0: none. */
	int reserveRight = 0;

	@Override
	protected void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0.create();
		int w = getWidth() - 2, h = getHeight() - 2, c = 5;
		Polygon p = new Polygon(new int[] {c, w - c, w, w, w - c, c, 0, 0}, new int[] {0, 0, c, h - c, h, h, h - c, c}, 8);
		p.translate(1, 1);
		ButtonModel m = getModel();
		boolean on = isEnabled();
		boolean hot = on && ((m.isRollover() && !m.isPressed()) || lit);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setColor(hot ? HOT : (on && m.isPressed() ? FILL_DOWN : FILL));
		g.fillPolygon(p);
		g.setStroke(new BasicStroke(2f));
		g.setColor(on ? LINE : DIM);
		g.drawPolygon(p);
		String label = getText().toUpperCase();
		if (reserveRight > 0) label = font.fit(label, getWidth() - 2 * reserveRight);
		BufferedImage t = font.render(label, hot ? TEXT_HOT : (on ? TEXT : DIM));
		g.drawImage(t, (getWidth() - t.getWidth()) / 2, (getHeight() - t.getHeight()) / 2 + 1, null);
		g.dispose();
	}

	/** A section header: gold FTL text with a rule running to the right. */
	public static class Header extends JComponent {
		private final String text;
		/** A heading that folds its group of buttons away: lighter under the mouse, a small arrow when folded. */
		private boolean foldable = false, folded = false, hover = false;
		static final Color HOVER_GOLD = new Color(255, 236, 180);
		/** The title centred, with a line on each side (---- ABOARD ----); otherwise at the left, the line after it. */
		private final boolean centred;
		public Header(String text, int w) { this(text, w, false); }
		public Header(String text, int w, boolean centred) {
			this.centred = centred;
			this.text = text.toUpperCase();
			Dimension d = new Dimension(w, FtlFont.MENU.render(this.text, GOLD).getHeight() + 6);
			setPreferredSize(d);
			setMinimumSize(new Dimension(Math.min(w, 40), d.height)); // a tight column squeezes the gaps, never the lettering
			setMaximumSize(new Dimension(Integer.MAX_VALUE, d.height));
			setAlignmentX(LEFT_ALIGNMENT);
		}
		/** Makes it fold: a click toggles, calling back with the new state (true: folded). */
		public Header foldable(boolean startFolded, final java.util.function.Consumer<Boolean> toggled) {
			foldable = true;
			folded = startFolded;
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("Click to fold or unfold");
			addMouseListener(new java.awt.event.MouseAdapter() {
				@Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; repaint(); }
				@Override public void mouseExited(java.awt.event.MouseEvent e) { hover = false; repaint(); }
				@Override public void mouseClicked(java.awt.event.MouseEvent e) { folded = !folded; repaint(); toggled.accept(folded); }
			});
			return this;
		}
		public boolean folded() { return folded; }
		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			Color c = hover ? HOVER_GOLD : GOLD;
			BufferedImage t = FtlFont.MENU.render(text, c);
			int x = centred ? Math.max(0, (getWidth() - t.getWidth()) / 2) : 0;
			g.drawImage(t, x, 2, null);
			g.setColor(c);
			g.setStroke(new BasicStroke(2f));
			int y = 2 + t.getHeight() / 2, end = getWidth() - 2;
			if (foldable && folded) { // a small arrow at the line's end: there's more under this heading
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.fillPolygon(new Polygon(new int[] {end - 8, end, end - 8}, new int[] {y - 5, y, y + 5}, 3));
				end -= 12;
			}
			if (x - 10 > 1) g.drawLine(1, y, x - 10, y);
			if (x + t.getWidth() + 10 < end) g.drawLine(x + t.getWidth() + 10, y, end, y);
			g.dispose();
		}
	}

	/** One line of text in an FTL font with a drop shadow, cut to fit its width. */
	public static class Text extends JComponent {
		private final String text;
		private final FtlFont font;
		private final Color color;
		public Text(String text, FtlFont font, Color color, int w) {
			this.font = font;
			this.color = color;
			this.text = font.fit(text, w - 2);
			BufferedImage t = font.render(this.text, color);
			Dimension d = new Dimension(w, t.getHeight() + 2);
			setPreferredSize(d);
			setMaximumSize(d);
			setAlignmentX(LEFT_ALIGNMENT);
			if (!this.text.equals(text)) setToolTipText(text);
		}
		@Override
		protected void paintComponent(Graphics g) {
			g.drawImage(font.render(text, Color.black), 1, 1, null);
			g.drawImage(font.render(text, color), 0, 0, null);
		}
	}
}
