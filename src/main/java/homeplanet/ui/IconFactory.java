package homeplanet.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicComboPopup;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.DefaultDataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.xml.AnimSheet;
import net.blerf.ftl.xml.DroneBlueprint;
import net.blerf.ftl.xml.WeaponAnim;
import net.blerf.ftl.xml.WeaponBlueprint;

import homeplanet.model.Items;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small icons for the Cargo Bay, cut from FTL's own art in ftl.dat.
 * Anything without art (or with unreadable art) gets no icon, so it shows as text only.
 */
public class IconFactory {
	private static final Logger log = LoggerFactory.getLogger(IconFactory.class);

	private static final int ROW_H = 16;       // fits a 20px combo row
	private static final int WEAPON_W = 30;    // weapons are long, so they get a wider box

	private static final Map<String, Icon> cache = new HashMap<String, Icon>();
	private static final Icon NONE = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

	/** Icon for a weapon or drone blueprint id; null for anything else (augments, placeholders). */
	public static Icon itemIcon(String id) {
		if (id == null || id.isEmpty()) return null;
		String key = "item:" + id;
		if (!cache.containsKey(key)) {
			Icon icon = null;
			try {
				WeaponBlueprint w = DataManager.get().getWeapons().get(id);
				DroneBlueprint d = DataManager.get().getDrones().get(id);
				if (w != null) {
					icon = weaponArt(w);
				} else if (d != null && d.getDroneImage() != null) {
					// most drones have <image>_base.png; the shield drone only has _off / _charged / _glow (checked in ftl.dat 1.6.14)
					BufferedImage img = null;
					for (String suffix : new String[] {"_base", "_off", "_charged"}) {
						if (DataManager.get().hasResourceInputStream("img/ship/drones/" + d.getDroneImage() + suffix + ".png")) {
							img = load("img/ship/drones/" + d.getDroneImage() + suffix + ".png");
							break;
						}
					}
					icon = fit(img, ROW_H, ROW_H);
				} else if (d != null) {
					// Drones that walk around inside ships use crew-style sprite sheets.
					String sheet = null;
					if ("REPAIR".equals(d.getType())) sheet = "img/people/repair_base.png";
					else if ("BATTLE".equals(d.getType())) sheet = "img/people/battle_base.png";
					else if ("BOARDER".equals(d.getType())) sheet = "img/people/boarder_ion_base.png";
					BufferedImage img = (sheet != null ? load(sheet) : null);
					if (img != null) {
						icon = fit(img.getSubimage(0, 0, Math.min(35, img.getWidth()), Math.min(35, img.getHeight())), ROW_H, ROW_H);
					}
				}
			} catch (Exception e) {
				log.debug("No icon for " + id, e);
			}
			cache.put(key, icon != null ? icon : NONE);
		}
		Icon icon = cache.get(key);
		return icon == NONE ? null : icon;
	}

	/** Race portrait (first frame of the crew sprite sheet); null if unavailable. */
	public static Icon crewIcon(CrewState cs) {
		if (cs == null || cs.getRace() == null) return null;
		String race = cs.getRace().getId();
		if ("human".equals(race) && !cs.isMale()) race = "female";
		String key = "crew:" + race;
		if (!cache.containsKey(key)) {
			Icon icon = null;
			try {
				BufferedImage sheet = load("img/people/" + race + "_base.png");
				if (sheet != null) {
					icon = fit(sheet.getSubimage(0, 0, Math.min(35, sheet.getWidth()), Math.min(35, sheet.getHeight())), ROW_H, ROW_H);
				}
			} catch (Exception e) {
				log.debug("No crew icon for " + race, e);
			}
			cache.put(key, icon != null ? icon : NONE);
		}
		Icon icon = cache.get(key);
		return icon == NONE ? null : icon;
	}

	/** FTL's white HUD icon for a supply: "missiles", "drones", "scrap" or "fuel". */
	public static Icon supplyIcon(String name) {
		String key = "supply:" + name;
		if (!cache.containsKey(key)) {
			Icon icon = null;
			try {
				icon = fit(load("img/ui_icons/icon_" + name + ".png"), ROW_H, ROW_H);
			} catch (Exception e) {
				log.debug("No supply icon for " + name, e);
			}
			cache.put(key, icon != null ? icon : NONE);
		}
		Icon icon = cache.get(key);
		return icon == NONE ? null : icon;
	}

	/** A larger race portrait for the crew report; null if unavailable. */
	public static Icon crewPortrait(CrewState cs, int size) {
		if (cs == null || cs.getRace() == null) return null;
		String race = cs.getRace().getId();
		if ("human".equals(race) && !cs.isMale()) race = "female";
		String key = "portrait:" + race + ":" + size;
		if (!cache.containsKey(key)) {
			Icon icon = null;
			try {
				BufferedImage sheet = load("img/people/" + race + "_base.png");
				if (sheet != null) {
					icon = fit(sheet.getSubimage(0, 0, Math.min(35, sheet.getWidth()), Math.min(35, sheet.getHeight())), size, size);
				}
			} catch (Exception e) {
				log.debug("No crew portrait for " + race, e);
			}
			cache.put(key, icon != null ? icon : NONE);
		}
		Icon icon = cache.get(key);
		return icon == NONE ? null : icon;
	}


	// First frame of the weapon's animation, turned to lie flat (barrel to the right).
	private static Icon weaponArt(WeaponBlueprint w) throws Exception {
		if (!(DataManager.get() instanceof DefaultDataManager)) return null;
		DefaultDataManager dm = (DefaultDataManager) DataManager.get();
		WeaponAnim anim = dm.getWeaponAnim(w.getWeaponAnimId());
		if (anim == null) return null;
		AnimSheet sheet = dm.getAnimSheet(anim.getSheetId());
		if (sheet == null) return null;
		BufferedImage img = load("img/" + sheet.getInnerPath());
		if (img == null) return null;
		int fw = Math.min(sheet.getFrameWidth(), img.getWidth());
		int fh = Math.min(sheet.getFrameHeight(), img.getHeight());
		BufferedImage frame = img.getSubimage(0, 0, fw, fh);
		// Rotate 90 degrees clockwise: the art points up, the icon points right.
		BufferedImage flat = new BufferedImage(fh, fw, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < fh; y++) {
			for (int x = 0; x < fw; x++) {
				flat.setRGB(fh - 1 - y, x, frame.getRGB(x, y));
			}
		}
		return fit(flat, WEAPON_W, ROW_H);
	}

	private static final String[] SKILL_ART = {"pilot", "engines", "shields", "weapons", "repair", "combat"};
	private static final Map<String, Icon> skillIcons = new java.util.HashMap<String, Icon>();
	/**
	 * FTL's own skill icon (img/people/skill_*_white.png), tinted as FTL tints a skill: grey untrained, green at the
	 * first level, gold mastered (the Crew Log, 5.41). Null if the game's art can't be read.
	 */
	public static synchronized Icon skillIcon(int skill, int level, int size) {
		String key = skill + "/" + level + "/" + size;
		if (skillIcons.containsKey(key)) return skillIcons.get(key);
		BufferedImage src = load("img/people/skill_" + SKILL_ART[skill] + "_white.png");
		Icon out = null;
		if (src != null) {
			java.awt.Color tint = level >= 2 ? new java.awt.Color(250, 210, 120) : level == 1 ? new java.awt.Color(120, 230, 120) : new java.awt.Color(110, 122, 130);
			BufferedImage t = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
				int argb = src.getRGB(x, y), alpha = argb >>> 24, lum = ((argb >> 16 & 255) + (argb >> 8 & 255) + (argb & 255)) / 3;
				t.setRGB(x, y, alpha << 24 | (tint.getRed() * lum / 255) << 16 | (tint.getGreen() * lum / 255) << 8 | (tint.getBlue() * lum / 255));
			}
			out = new javax.swing.ImageIcon(t.getScaledInstance(size, size * t.getHeight() / Math.max(1, t.getWidth()), java.awt.Image.SCALE_SMOOTH));
		}
		skillIcons.put(key, out);
		return out;
	}

	private static BufferedImage load(String innerPath) {
		InputStream in = null;
		try {
			in = DataManager.get().getResourceInputStream(innerPath);
			return ImageIO.read(in);
		} catch (Exception e) {
			log.debug("Missing art " + innerPath);
			return null;
		} finally {
			try { if (in != null) in.close(); } catch (Exception e) { }
		}
	}

	// Trims empty borders, then scales to fit maxW x maxH keeping proportions.
	private static Icon fit(BufferedImage src, int maxW, int maxH) {
		if (src == null) return null;
		BufferedImage img = trim(src);
		if (img == null) return null;
		double scale = Math.min((double) maxW / img.getWidth(), (double) maxH / img.getHeight());
		int w = Math.max(1, (int) Math.round(img.getWidth() * scale));
		int h = Math.max(1, (int) Math.round(img.getHeight() * scale));
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		// Pixel art: keep it crisp when enlarging, smooth when shrinking.
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale >= 1
				? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(img, 0, 0, w, h, null);
		g.dispose();
		return new ImageIcon(out);
	}

	/** The image cut down to its visible pixels (alpha above the threshold); null if it's empty. */
	static BufferedImage trim(BufferedImage img) { return trim(img, 16); }
	static BufferedImage trim(BufferedImage img, int alphaOver) {
		int minX = img.getWidth(), minY = img.getHeight(), maxX = -1, maxY = -1;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				if ((img.getRGB(x, y) >>> 24) > alphaOver) {
					if (x < minX) minX = x;
					if (x > maxX) maxX = x;
					if (y < minY) minY = y;
					if (y > maxY) maxY = y;
				}
			}
		}
		if (maxX < 0) return null;
		return img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

}
