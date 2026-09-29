package homeplanet.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import net.blerf.ftl.parser.DataManager;

/**
 * FTL's own bitmap fonts, read from the game's fonts/*.font files, so buttons and headers look like the game's.
 * Format (FTL 1.6): "FONT", then 16-byte glyph records from offset 27 (char, x, y, w, h, baseline, bearing, extra, -, spacing),
 * then "TEX\n" with a 28-byte header (texture width and height big-endian at +4/+6) and the 8-bit alpha texture.
 * If a font can't be read, text falls back to an ordinary Java font.
 */
public class FtlFont {

	/** The menu font (buttons like the pause menu's) and the body font (ship names, small buttons). */
	public static final FtlFont MENU = new FtlFont("fonts/HL2.font", 2, new Font(Font.SANS_SERIF, Font.BOLD, 15));
	public static final FtlFont BODY = new FtlFont("fonts/JustinFont12Bold.font", 1, new Font(Font.SANS_SERIF, Font.BOLD, 12));

	private static class Glyph { int x, y, w, h, base, bearing, advance; }

	private final String path;
	private final int scale;
	private final Font fallback;
	private boolean loaded = false;
	private int texW, texH;
	private byte[] tex;
	private final Map<Character, Glyph> glyphs = new HashMap<Character, Glyph>();
	private int ascent = 0, descent = 0;
	private final Map<String, BufferedImage> cache = new HashMap<String, BufferedImage>();

	private FtlFont(String path, int scale, Font fallback) {
		this.path = path;
		this.scale = scale;
		this.fallback = fallback;
	}

	private synchronized void load() {
		if (loaded) return;
		loaded = true;
		InputStream in = null;
		try {
			in = DataManager.get().getResourceInputStream(path);
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			for (int n; (n = in.read(buf)) > 0;) bo.write(buf, 0, n);
			byte[] d = bo.toByteArray();
			int t = -1;
			for (int i = 27; i + 4 <= d.length; i++) {
				if (d[i] == 'T' && d[i + 1] == 'E' && d[i + 2] == 'X' && d[i + 3] == '\n') { t = i; break; }
			}
			if (t < 0 || d[0] != 'F' || d[1] != 'O') throw new IllegalStateException("not an FTL font");
			texW = ((d[t + 8] & 0xff) << 8) | (d[t + 9] & 0xff);
			texH = ((d[t + 10] & 0xff) << 8) | (d[t + 11] & 0xff);
			int start = t + 32;
			if (texW <= 0 || texH <= 0 || start + texW * texH > d.length) throw new IllegalStateException("bad texture");
			tex = new byte[texW * texH];
			System.arraycopy(d, start, tex, 0, tex.length);
			for (int o = 27; o + 16 <= t; o += 16) {
				char c = (char) ((d[o] & 0xff) | ((d[o + 1] & 0xff) << 8));
				if (glyphs.containsKey(c)) break; // a second table follows; the first is the one we want
				Glyph g = new Glyph();
				g.x = (d[o + 2] & 0xff) | ((d[o + 3] & 0xff) << 8);
				g.y = d[o + 4] & 0xff;
				g.w = d[o + 5] & 0xff;
				g.h = d[o + 6] & 0xff;
				g.base = d[o + 7]; // signed
				g.bearing = d[o + 8];
				g.advance = g.bearing + g.w + d[o + 9] + Math.max(1, (int) d[o + 11]);
				if (g.x + g.w > texW || g.y + g.h > texH) continue;
				glyphs.put(c, g);
				if (c > ' ' && c < 128) {
					ascent = Math.max(ascent, g.base);
					descent = Math.max(descent, g.h - g.base);
				}
			}
			if (glyphs.isEmpty()) tex = null;
		} catch (Exception e) {
			tex = null;
			System.err.println("FTL font " + path + " not read, using a system font: " + e);
		} finally {
			try { if (in != null) in.close(); } catch (Exception e) { }
		}
	}

	/** True if FTL's font has every character (otherwise the Java font is used for that text). */
	private boolean covers(String s) {
		load();
		if (tex == null) return false;
		for (int i = 0; i < s.length(); i++) if (!glyphs.containsKey(s.charAt(i))) return false;
		return true;
	}

	/** The text drawn in this font and colour (cached). */
	public synchronized BufferedImage render(String s, Color color) {
		String key = s + "\u0000" + color.getRGB();
		BufferedImage img = cache.get(key);
		if (img != null) return img;
		if (covers(s)) {
			int w = 0;
			for (int i = 0; i < s.length(); i++) w += glyphs.get(s.charAt(i)).advance;
			w = Math.max(1, w);
			int h = Math.max(1, ascent + descent);
			img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
			int rgb = color.getRGB() & 0xffffff, alpha = color.getAlpha();
			int x = 0;
			for (int i = 0; i < s.length(); i++) {
				Glyph g = glyphs.get(s.charAt(i));
				int gx = x + g.bearing, gy = ascent - g.base;
				for (int yy = 0; yy < g.h; yy++) {
					for (int xx = 0; xx < g.w; xx++) {
						int a = tex[(g.y + yy) * texW + g.x + xx] & 0xff;
						if (a == 0) continue;
						int px = gx + xx, py = gy + yy;
						if (px < 0 || py < 0 || px >= w || py >= h) continue;
						int argb = ((a * alpha / 255) << 24) | rgb;
						for (int sy = 0; sy < scale; sy++)
							for (int sx = 0; sx < scale; sx++) img.setRGB(px * scale + sx, py * scale + sy, argb);
					}
				}
				x += g.advance;
			}
		} else {
			BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			Graphics2D pg = probe.createGraphics();
			java.awt.FontMetrics fm = pg.getFontMetrics(fallback);
			pg.dispose();
			img = new BufferedImage(Math.max(1, fm.stringWidth(s)), fm.getAscent() + fm.getDescent(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(fallback);
			g.setColor(color);
			g.drawString(s, 0, fm.getAscent());
			g.dispose();
		}
		if (cache.size() > 500) cache.clear();
		cache.put(key, img);
		return img;
	}

	/** Cuts the text down with "..." until it fits the width. */
	public String fit(String s, int maxWidth) {
		if (render(s, Color.white).getWidth() <= maxWidth) return s;
		for (int n = s.length() - 1; n > 0; n--) {
			String t = s.substring(0, n).trim() + "...";
			if (render(t, Color.white).getWidth() <= maxWidth) return t;
		}
		return s;
	}
}
