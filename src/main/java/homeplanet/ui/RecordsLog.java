package homeplanet.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JComponent;
import javax.swing.Scrollable;

/**
 * A ship's log as the Records window shows it (FTL's gold headings, Sans Serif 12 text, docs/STYLE.md): the voyage log under a heading for each sector, each
 * event with a coloured mark for its kind and its gains and losses in green and red; or the station's log under a
 * heading for each day, each entry with its kind as a tag. The time shows once for each save (each entry). Oldest
 * first, as the files are; long lines wrap.
 */
class RecordsLog extends JComponent implements Scrollable {
	static final Color BG = new Color(20, 27, 34), LINE = new Color(70, 86, 96), TXT = MenuTheme.WHITE, DIM = MenuTheme.GREY_GREEN,
			GOLD = MenuTheme.GOLD, GOOD = MenuTheme.GREEN, BAD = MenuTheme.RED,
			BLUE = new Color(110, 170, 235), PURPLE = new Color(170, 140, 235), FLAG = new Color(235, 70, 70); // the last three: marks only
	private static final java.awt.Font PLAIN = MenuTheme.TEXT_FONT, BOLD = MenuTheme.LABEL_FONT;
	private static final java.awt.FontMetrics PLAIN_FM, BOLD_FM;
	static {
		java.awt.Graphics2D pg = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
		PLAIN_FM = pg.getFontMetrics(PLAIN);
		BOLD_FM = pg.getFontMetrics(BOLD);
		pg.dispose();
	}
	private static final Pattern STAMP = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2})  (.*)$");
	private static final Pattern DELTA = Pattern.compile("\\([+-]\\d+\\)");
	private static final int PAD = 16, TEXT_GAP = 10;

	/** A piece of text in one colour, plain or bold. */
	private static final class Seg {
		final String text; final Color color; final boolean bold;
		Seg(String text, Color color) { this(text, color, false); }
		Seg(String text, Color color, boolean bold) { this.text = text; this.color = color; this.bold = bold; }
		int width() { return (bold ? BOLD_FM : PLAIN_FM).stringWidth(text); }
	}
	/** One entry: a heading (with a dim note after it), or an event (a time, a dot or a tag, and its text). */
	private static final class Item {
		boolean heading, day;
		String title, time, tag;
		Color mark;
		final List<Seg> segs = new ArrayList<Seg>();
		/** Indented detail lines under a station log entry. */
		final List<String> details = new ArrayList<String>();
	}
	/** An item laid out for the current width: its lines of pieces, and where it starts. */
	private static final class Laid {
		Item item; int y, h, detailFrom;
		final List<List<Seg>> lines = new ArrayList<List<Seg>>();
	}

	private final List<Item> items = new ArrayList<Item>();
	private final String empty;
	private int tagW = 0, textX;
	private final List<Laid> laid = new ArrayList<Laid>();
	private int laidWidth = -1, laidHeight = 0;
	private final int lineH = PLAIN_FM.getHeight() + 6;
	private final int headH = FtlFont.MENU.render("AG", GOLD).getHeight() + 18;

	private RecordsLog(String empty) {
		this.empty = empty;
		setOpaque(true);
		setBackground(BG);
	}

	/** The voyage log (voyage.log's text), or the empty text if there's nothing in it yet. */
	static RecordsLog voyage(String text, String empty) {
		RecordsLog r = new RecordsLog(empty);
		String lastStamp = null;
		for (String line : text.split("\r?\n")) {
			if (line.trim().isEmpty()) continue;
			Matcher m = STAMP.matcher(line);
			String stamp = m.matches() ? m.group(1) + " " + m.group(2) : null, t = m.matches() ? m.group(3) : line;
			Item h = sectorHeading(t);
			if (h != null) { r.items.add(h); continue; }
			Item it = new Item();
			if (stamp != null && (lastStamp == null || !lastStamp.startsWith(m.group(1)))) {
				Item day = new Item(); // a new day: its date, dim, above her first event that day
				day.day = true;
				day.title = m.group(1);
				r.items.add(day);
			}
			if (stamp != null && !stamp.equals(lastStamp)) it.time = m.group(2);
			if (stamp != null) lastStamp = stamp;
			it.mark = voyageMark(t);
			split(t, it.segs);
			r.items.add(it);
		}
		r.textX = PAD + r.timeW() + TEXT_GAP + 16;
		return r;
	}
	/** The station's log (her entries in history.log), or the empty text if there are none. */
	static RecordsLog station(String text, String empty) {
		RecordsLog r = new RecordsLog(empty);
		String day = null;
		Item last = null;
		for (String line : text.split("\r?\n")) {
			if (line.trim().isEmpty()) continue;
			if (line.startsWith("  ") && last != null) { last.details.add(line.trim()); continue; }
			Matcher m = STAMP.matcher(line);
			Item it = new Item();
			if (m.matches()) {
				if (!m.group(1).equals(day)) {
					day = m.group(1);
					Item h = new Item();
					h.heading = true;
					h.title = day;
					r.items.add(h);
				}
				it.time = m.group(2);
				String rest = m.group(3);
				int sp = rest.indexOf("  ");
				it.tag = sp < 0 ? rest : rest.substring(0, sp);
				it.mark = tagColour(it.tag);
				it.segs.add(new Seg(sp < 0 ? "" : rest.substring(sp + 2), TXT));
				r.tagW = Math.max(r.tagW, BOLD_FM.stringWidth(it.tag) + 16);
			} else {
				it.segs.add(new Seg(line, TXT)); // not an entry (the log couldn't be read, say): shown as it is
			}
			r.items.add(it);
			last = it;
		}
		r.textX = PAD + r.timeW() + TEXT_GAP + (r.tagW > 0 ? r.tagW + TEXT_GAP : 0);
		return r;
	}
	private int timeW() {
		int w = PLAIN_FM.stringWidth("00:00");
		for (Item it : items) if (it.time != null) w = Math.max(w, PLAIN_FM.stringWidth(it.time));
		return w;
	}

	/** "Sector 4 reached (sectors visited: 10)" and "Back to sector 1: a new run" start a new heading. */
	private static Item sectorHeading(String t) {
		Matcher m = Pattern.compile("^Sector (\\d+) reached(?: \\((.*)\\))?$").matcher(t);
		Matcher b = Pattern.compile("^Back to sector (\\d+): (.*)$").matcher(t);
		boolean reached = m.matches();
		if (!reached && !b.matches()) return null;
		Item h = new Item();
		h.heading = true;
		h.title = "SECTOR " + (reached ? m.group(1) : b.group(1));
		String note = reached ? m.group(2) : b.group(2);
		if (note != null) h.segs.add(new Seg(note, DIM));
		return h;
	}
	/** The mark's colour for a voyage log line: jumps, battles, crew, cargo and supplies, systems, the Rebel Flagship. */
	private static Color voyageMark(String t) {
		if (t.startsWith("Jumped") || t.startsWith("Waited")) return BLUE;
		if (t.startsWith("The Rebel Flagship")) return FLAG;
		if (t.contains(" defeated (") || t.startsWith("Crew lost") || t.startsWith("Hull damaged")) return BAD;
		if (t.startsWith("Crew joined") || t.startsWith("Hull repaired")) return GOOD;
		if (t.startsWith("Aboard now")) return GOLD;
		if (t.startsWith("Gone")) return DIM;
		if (t.startsWith("New system") || t.startsWith("System removed") || t.startsWith("Reactor") || t.contains(" upgraded to ") || t.contains(" reduced to ")) return PURPLE;
		if (t.startsWith("Scrap") || t.startsWith("Fuel") || t.startsWith("Missiles") || t.startsWith("Drone parts")) return GOLD;
		return TXT; // the station's own notes
	}
	/** A station log tag's colour, by its kind. */
	private static Color tagColour(String kind) {
		if (kind.startsWith("+")) return GOOD; // the reputation log: its change is its tag
		if (kind.startsWith("\u2212")) return BAD;
		String k = "," + kind + ",";
		if (",VICTORY,REWARD,STIPEND,CLAIM,SALVAGE,".contains(k)) return GOOD;
		if (",MUSEUM,COMMISSION,TRADE,BUY,SELL,SCRAP,".contains(k)) return GOLD;
		if (",LOST,OVERWRITTEN,DISBAND,DECOMMISSION,JUNK,RETIRE,DESTROY,".contains(k)) return BAD;
		if (",BOARD,DOCK,NEW JOURNEY,TRANSMISSION,SENT,REASSIGN,RESTORE,RECOVER,CAREER,".contains(k)) return BLUE;
		if (",DESIGN,REMODEL,RENAME,SYSTEMS,BLUEPRINT,BLUEPRINTS,".contains(k)) return PURPLE;
		return DIM;
	}
	/** A line's lead in bold white, the rest grey-green, with gains green and losses red: "Jumped, hull 27/30 (-3), ...". */
	private static void split(String t, List<Seg> out) {
		int cut = -1, skip = 0;
		if (t.startsWith("Jumped") || t.startsWith("Waited")) { cut = t.indexOf(", "); skip = 2; }
		else if (t.indexOf(": ") > 0 && t.indexOf(": ") < 24) { cut = t.indexOf(": "); skip = 2; }
		else if (t.indexOf(" (") > 0) { cut = t.indexOf(" ("); skip = 1; }
		if (cut < 0) { out.add(new Seg(t, TXT, true)); return; }
		out.add(new Seg(t.substring(0, cut) + "  ", TXT, true));
		String rest = t.substring(cut + skip);
		Matcher d = DELTA.matcher(rest);
		int at = 0;
		while (d.find()) {
			if (d.start() > at) out.add(new Seg(rest.substring(at, d.start()), DIM));
			out.add(new Seg(d.group(), d.group().charAt(1) == '+' ? GOOD : BAD));
			at = d.end();
		}
		if (at < rest.length()) out.add(new Seg(rest.substring(at), DIM));
	}

	// ---- layout ----

	private void layOut(int width) {
		if (width == laidWidth) return;
		laidWidth = width;
		laid.clear();
		int y = 10;
		for (Item it : items) {
			Laid l = new Laid();
			l.item = it;
			l.y = y;
			if (it.heading) {
				l.h = headH;
			} else if (it.day) {
				l.h = lineH;
			} else {
				int x0 = textX, max = Math.max(120, width - PAD - x0);
				wrap(it.segs, max, l.lines);
				l.detailFrom = l.lines.size();
				for (String d : it.details) wrap(java.util.Collections.singletonList(new Seg(d, DIM)), max - 16, l.lines);
				l.h = Math.max(1, l.lines.size()) * lineH + (it.tag != null ? 4 : 0);
			}
			y += l.h;
			laid.add(l);
		}
		laidHeight = y + 10;
	}
	/** Breaks the pieces into lines no wider than max, at spaces (a word longer than a line gets a line of its own). */
	private static void wrap(List<Seg> segs, int max, List<List<Seg>> lines) {
		List<Seg> line = new ArrayList<Seg>();
		int w = 0;
		for (Seg s : segs) {
			String[] words = s.text.split("(?<= )");
			for (String word : words) {
				int ww = new Seg(word, s.color, s.bold).width();
				if (w > 0 && w + ww > max && !word.trim().isEmpty()) {
					lines.add(line);
					line = new ArrayList<Seg>();
					w = 0;
					if (word.trim().isEmpty()) continue;
				}
				Seg prev = line.isEmpty() ? null : line.get(line.size() - 1);
				if (prev != null && prev.color.equals(s.color) && prev.bold == s.bold) line.set(line.size() - 1, new Seg(prev.text + word, s.color, s.bold));
				else line.add(new Seg(word, s.color, s.bold));
				w += ww;
			}
		}
		if (!line.isEmpty() || lines.isEmpty()) lines.add(line);
	}

	@Override
	public Dimension getPreferredSize() {
		int w = getParent() != null ? getParent().getWidth() : 600;
		layOut(Math.max(300, w));
		return new Dimension(Math.max(300, w), items.isEmpty() ? 80 : laidHeight);
	}
	@Override
	protected void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(BG);
		g.fillRect(0, 0, getWidth(), getHeight());
		if (items.isEmpty()) {
			int y = 14;
			for (String line : empty.split("\n")) { text(g, new Seg(line, DIM), PAD, y); y += lineH; }
			return;
		}
		layOut(getWidth());
		Rectangle clip = g.getClipBounds();
		for (Laid l : laid) {
			if (clip != null && (l.y + l.h < clip.y || l.y > clip.y + clip.height)) continue;
			Item it = l.item;
			if (it.heading) {
				int y = l.y + 6;
				g.drawImage(FtlFont.MENU.render(it.title, GOLD), PAD, y, null);
				int x = PAD + FtlFont.MENU.width(it.title) + 14;
				for (Seg s : it.segs) text(g, s, x, y + 5);
				g.setColor(LINE);
				g.drawLine(PAD, l.y + headH - 8, getWidth() - PAD, l.y + headH - 8);
				continue;
			}
			int y = l.y + 2;
			if (it.day) {
				text(g, new Seg(it.title, DIM), PAD, y);
				continue;
			}
			if (it.time != null) text(g, new Seg(it.time, DIM), PAD, y);
			int markX = PAD + timeW() + TEXT_GAP;
			if (it.tag != null) {
				Color c = it.mark;
				g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
				g.fillRoundRect(markX, y - 4, tagW, lineH, 6, 6);
				g.setColor(c);
				g.drawRoundRect(markX, y - 4, tagW, lineH, 6, 6);
				Seg tag = new Seg(it.tag, c, true);
				text(g, tag, markX + (tagW - tag.width()) / 2, y);
			} else if (it.mark != null) {
				g.setColor(it.mark);
				g.fillOval(markX, y + (PLAIN_FM.getHeight() - 9) / 2, 9, 9);
			}
			int lineIndex = 0, detailFrom = l.detailFrom;
			for (List<Seg> line : l.lines) {
				int x = textX + (lineIndex >= detailFrom ? 16 : 0);
				for (Seg s : line) {
					if (s.text.isEmpty()) continue;
					text(g, s, x, y);
						x += s.width();
				}
				y += lineH;
				lineIndex++;
			}
		}
	}
	/** A piece of text with its top at y. */
	private static void text(Graphics2D g, Seg s, int x, int y) {
		g.setFont(s.bold ? BOLD : PLAIN);
		g.setColor(s.color);
		g.drawString(s.text, x, y + (s.bold ? BOLD_FM : PLAIN_FM).getAscent());
	}

	// ---- scrolling: as wide as the view, as tall as the log ----

	public Dimension getPreferredScrollableViewportSize() { return new Dimension(700, 320); }
	public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return lineH; }
	public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return Math.max(lineH, r.height - lineH); }
	public boolean getScrollableTracksViewportWidth() { return true; }
	public boolean getScrollableTracksViewportHeight() { return false; }
}
