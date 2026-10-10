package homeplanet.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JToolTip;

/**
 * The Refit tab's hover, laid out as FTL's upgrade screen lays out its box (heromedel, 6.42): the title and FTL's own
 * words, then a column of levels from the most down, each a wide square, the gear and its price, a line, and what the
 * level gives. In the station's own colours, the Cargo Bay's box (heromedel: not FTL's); the prices of the levels
 * already had stay, in green, where FTL leaves them blank.
 */
final class InfoTip extends JToolTip {
	/** One line of the column. */
	static final class Row {
		/** Its number down the column (a level, or the reactor's bar). */
		final int num;
		/** Had already: its square and price green. */
		final boolean had;
		/** Its price, or -1 for none (a first level, or none known), or 0 for a dash (a level no upgrade buys). */
		final int price;
		/** What it gives, as FTL says it ("" for nothing). */
		final String says;
		Row(int num, boolean had, int price, String says) { this.num = num; this.had = had; this.price = price; this.says = says == null ? "" : says; }
	}
	/** What the box says. */
	static final class Model {
		final String title, text;
		final List<Row> rows = new ArrayList<Row>();
		/** Why the row is greyed out, in orange; or null. */
		String note;
		/** A line under the text, before the column (the reactor's bars, a weapon's power), or null. */
		String line;
		/** Green squares after the line (a weapon's or drone's power), 0 for none. */
		int squares;
		Model(String title, String text) { this.title = title; this.text = text == null ? "" : text; }
		/** The same as plain words (the row's tooltip text, for the tests and anything reading it). */
		String plain() {
			StringBuilder sb = new StringBuilder(title);
			if (!text.isEmpty()) sb.append("\n").append(text);
			if (line != null) sb.append("\n").append(line);
			for (Row r : rows) sb.append("\nlevel ").append(r.num).append(r.price > 0 ? ": " + r.price : r.price == 0 ? ": -" : "").append(r.had ? " (had)" : "").append(r.says.isEmpty() ? "" : " " + r.says);
			if (note != null) sb.append("\n").append(note);
			return sb.toString();
		}
	}

	static final int W = 340, PAD = 10, ROW = 22, SQ_W = 18, SQ_H = 14;
	static final Color FILL = new Color(22, 27, 34), LINE = CargoParts.BOX_LINE;
	static final Color GREEN = new Color(120, 230, 120), OLIVE = new Color(120, 116, 64), DIVIDER = new Color(150, 165, 160, 140);
	private final Model m;

	InfoTip(Model m) {
		this.m = m;
		setOpaque(true);
		setBorder(null);
	}
	@Override public Dimension getPreferredSize() { return new Dimension(W, height()); }

	private int height() {
		int h = PAD + 20 + wrap(m.text, FtlFont.CARGO, W - 2 * PAD).size() * 16;
		if (m.line != null) h += 20;
		if (!m.rows.isEmpty()) h += 8 + m.rows.size() * ROW;
		if (m.note != null) h += 6 + wrap(m.note, FtlFont.CARGO, W - 2 * PAD).size() * 16;
		return h + PAD;
	}

	@Override public void paint(Graphics g0) {
		Graphics2D g = (Graphics2D) g0.create();
		int w = getWidth(), h = getHeight();
		g.setColor(FILL);
		g.fillRect(0, 0, w, h);
		g.setColor(LINE);
		g.setStroke(new BasicStroke(1f));
		g.drawPolygon(CargoParts.cut(0, 0, w - 1, h - 1, 4));
		int y = PAD;
		CargoParts.text(g, m.title, FtlFont.BODY, CargoParts.GOLD, PAD, y);
		y += 20;
		for (String l : wrap(m.text, FtlFont.CARGO, w - 2 * PAD)) { CargoParts.text(g, l, FtlFont.CARGO, CargoParts.TEXT, PAD, y); y += 16; }
		if (m.line != null) {
			CargoParts.text(g, m.line, FtlFont.CARGO, CargoParts.TEXT, PAD, y + 2);
			int x = PAD + CargoParts.width(m.line, FtlFont.CARGO) + 8;
			for (int i = 0; i < m.squares && i < 8; i++) { g.setColor(GREEN); g.fillRect(x + i * 14, y + 4, 10, 11); }
			y += 20;
		}
		if (!m.rows.isEmpty()) {
			y += 8;
			Icon gear = IconFactory.supplyIcon("scrap");
			int priceX = PAD + SQ_W + 8, divX = priceX + 58;
			for (Row r : m.rows) {
				g.setColor(r.had ? GREEN : OLIVE);
				g.fillRect(PAD, y + 3, SQ_W, SQ_H);
				if (gear != null) paintGear(g, gear, priceX, y + 2);
				if (r.price >= 0) CargoParts.text(g, r.price == 0 ? "\u2013" : Integer.toString(r.price), FtlFont.CARGO, r.had ? GREEN : CargoParts.TEXT, priceX + 20, y + 4);
				g.setColor(DIVIDER);
				g.drawLine(divX, y + 1, divX, y + ROW - 2);
				if (!r.says.isEmpty()) CargoParts.text(g, FtlFont.CARGO.fit(r.says, w - divX - PAD - 6), FtlFont.CARGO, r.had ? GREEN : CargoParts.TEXT, divX + 6, y + 4);
				y += ROW;
			}
		}
		if (m.note != null) {
			y += 6;
			for (String l : wrap(m.note, FtlFont.CARGO, w - 2 * PAD)) { CargoParts.text(g, l, FtlFont.CARGO, CargoParts.ORANGE, PAD, y); y += 16; }
		}
		g.dispose();
	}
	/** The scrap gear, 16 pixels, as FTL puts it beside a price. */
	private static void paintGear(Graphics2D g, Icon gear, int x, int y) {
		Graphics2D s = (Graphics2D) g.create(x, y, 16, 16);
		double k = 16.0 / Math.max(gear.getIconWidth(), gear.getIconHeight());
		s.scale(k, k);
		s.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		gear.paintIcon(null, s, 0, 0);
		s.dispose();
	}
	/** Words wrapped to a width in FTL's font. */
	static List<String> wrap(String text, FtlFont f, int width) {
		List<String> out = new ArrayList<String>();
		if (text == null || text.isEmpty()) return out;
		for (String para : text.split("\n")) {
			StringBuilder line = new StringBuilder();
			for (String word : para.split(" ")) {
				String tryIt = line.length() == 0 ? word : line + " " + word;
				if (line.length() > 0 && CargoParts.width(tryIt, f) > width) { out.add(line.toString()); line = new StringBuilder(word); }
				else line = new StringBuilder(tryIt);
			}
			if (line.length() > 0) out.add(line.toString());
		}
		return out;
	}
}
