package homeplanet.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;

/**
 * The Refit tab's power distribution simulation (6.42, heromedel; docs/retrofit-screen-redux-roadmap.md): FTL's bottom
 * panel drawn as the game draws it, from the player's own ftl.dat (the system icons, the weapons boxes, its fonts), at
 * FTL's own sizes, measured off FTL 1.6.14's screen. The reactor column (the power left in green), each powered system
 * with its bars, the weapons and drones in their slots. No subsystems (heromedel). Nothing it shows is saved.
 */
final class PowerPanel extends JComponent {
	// FTL's colours, read off its screen
	static final Color GREEN = new Color(100, 255, 100), CREAM = new Color(243, 255, 230), GREY = new Color(150, 150, 150);
	static final Color RED = new Color(255, 60, 50), HATCH_DIM = new Color(150, 150, 150), SLOT_DARK = new Color(30, 24, 28, 200);
	static final Color TAB_TEXT = new Color(32, 58, 66);
	/** FTL's order along the panel; the ones a ship hasn't got leave no gap. Doors, Piloting, Sensors and the Backup Battery are left out (heromedel). */
	static final SystemType[] ORDER = {SystemType.SHIELDS, SystemType.ENGINES, SystemType.MEDBAY, SystemType.CLONEBAY, SystemType.OXYGEN,
			SystemType.TELEPORTER, SystemType.CLOAKING, SystemType.ARTILLERY, SystemType.MIND, SystemType.HACKING};

	/** A powered system as the panel holds it. */
	static final class Sys {
		final SystemType type; final int level, damaged; int power;
		Sys(SystemType type, int level, int damaged, int power) { this.type = type; this.level = level; this.damaged = Math.min(damaged, level); this.power = Math.min(power, level - this.damaged); }
	}
	/** A weapon or drone in its slot. */
	static final class Slot {
		final String id, name; final int power; final boolean missiles; final String icon; boolean on;
		Slot(String id, String name, int power, boolean missiles, String icon, boolean on) { this.id = id; this.name = name; this.power = power; this.missiles = missiles; this.icon = icon; this.on = on; }
	}

	int reactor;
	final List<Sys> systems = new ArrayList<Sys>();
	Sys weapons, drones;
	final List<Slot> weaponSlots = new ArrayList<Slot>(), droneSlots = new ArrayList<Slot>();
	int weaponSlotCount, droneSlotCount;
	/** The systems a crew member stands at (FTL draws a little figure over them while they're powered). */
	final java.util.Set<SystemType> manned = new java.util.HashSet<SystemType>();

	PowerPanel() { setOpaque(false); }

	/** Takes the ship as her save has her: her reactor, her systems' power and damage, which weapons and drones are on. */
	void show(ShipState s) {
		systems.clear(); weaponSlots.clear(); droneSlots.clear(); weapons = null; drones = null;
		reactor = s.getReservePowerCapacity();
		for (SystemType t : ORDER) { Sys x = sys(s, t); if (x != null) systems.add(x); }
		weapons = sys(s, SystemType.WEAPONS);
		drones = sys(s, SystemType.DRONE_CTRL);
		net.blerf.ftl.xml.ShipBlueprint bp = DataManager.get().getShip(s.getShipBlueprintId());
		weaponSlotCount = bp != null && bp.getWeaponSlots() != null ? bp.getWeaponSlots() : 4;
		droneSlotCount = bp != null && bp.getDroneSlots() != null ? bp.getDroneSlots() : 2;
		for (WeaponState w : s.getWeaponList()) {
			net.blerf.ftl.xml.WeaponBlueprint b = DataManager.get().getWeapon(w.getWeaponId());
			weaponSlots.add(new Slot(w.getWeaponId(), shortName(b == null ? null : b.getShortTitle(), b == null ? null : b.getTitle(), w.getWeaponId()),
					b == null ? 0 : b.getPower(), b != null && b.getMissiles() > 0, b == null ? null : iconImage(w.getWeaponId(), true), w.isArmed()));
		}
		for (DroneState d : s.getDroneList()) {
			net.blerf.ftl.xml.DroneBlueprint b = DataManager.get().getDrone(d.getDroneId());
			droneSlots.add(new Slot(d.getDroneId(), shortName(b == null ? null : b.getShortTitle(), b == null ? null : b.getTitle(), d.getDroneId()),
					b == null ? 0 : b.getPower(), false, b == null ? null : iconImage(d.getDroneId(), false), d.isArmed()));
		}
		manned.clear();
		if (bp != null && bp.getSystemList() != null) for (SystemType t : new SystemType[] {SystemType.SHIELDS, SystemType.ENGINES, SystemType.WEAPONS}) {
			net.blerf.ftl.xml.ShipBlueprint.SystemList.SystemRoom[] rooms = bp.getSystemList().getSystemRoom(t);
			if (rooms == null || rooms.length == 0) continue;
			for (net.blerf.ftl.parser.SavedGameParser.CrewState c : s.getCrewList()) if (c.getRoomId() == rooms[0].getRoomId()) { manned.add(t); break; }
		}
		weaponSlotCount = Math.max(weaponSlotCount, weaponSlots.size());
		droneSlotCount = Math.max(droneSlotCount, droneSlots.size());
		revalidate();
		repaint();
	}
	private static Sys sys(ShipState s, SystemType t) {
		SystemState st = s.getSystem(t);
		return st == null || st.getCapacity() <= 0 ? null : new Sys(t, st.getCapacity(), st.getDamagedBars(), st.getPower());
	}
	private static String shortName(net.blerf.ftl.xml.DefaultDeferredText shortT, net.blerf.ftl.xml.DefaultDeferredText title, String id) {
		String s = shortT != null ? shortT.getTextValue() : null;
		if (s == null || s.isEmpty()) s = title != null ? title.getTextValue() : null;
		return s == null || s.isEmpty() ? id : s;
	}
	/** The weapon's or drone's little picture for its slot (its blueprint's iconImage: weapbox_icon_W_missile and the like), or null. */
	private static final Map<String, String> ICON_IMAGES = new HashMap<String, String>();
	private static String iconImage(String id, boolean weapon) {
		synchronized (ICON_IMAGES) {
			if (ICON_IMAGES.isEmpty()) readIconImages();
			String i = ICON_IMAGES.get(id);
			return i == null ? null : "img/systemUI/weapbox_icon_" + (weapon ? "W_" : "D_") + i + ".png";
		}
	}
	/** Every blueprint's <iconImage>, read once from FTL's blueprint files (the parser doesn't keep it). */
	private static void readIconImages() {
		ICON_IMAGES.put("", "");
		for (String f : new String[] {"data/blueprints.xml", "data/dlcBlueprints.xml"}) {
			java.io.InputStream in = null;
			try {
				in = DataManager.get().getResourceInputStream(f);
				java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
				byte[] buf = new byte[65536];
				for (int n; (n = in.read(buf)) > 0;) bo.write(buf, 0, n);
				java.util.regex.Matcher m = java.util.regex.Pattern.compile("<(weapon|drone)Blueprint name=\"([^\"]+)\"(.*?)</\\1Blueprint>", java.util.regex.Pattern.DOTALL).matcher(new String(bo.toByteArray(), "UTF-8"));
				while (m.find()) {
					java.util.regex.Matcher i = java.util.regex.Pattern.compile("<iconImage>([^<]+)</iconImage>").matcher(m.group(3));
					if (i.find()) ICON_IMAGES.put(m.group(2), i.group(1).trim());
				}
			} catch (Exception e) { // no pictures, then: the slots show their names alone
			} finally { try { if (in != null) in.close(); } catch (Exception e) { } }
		}
	}

	// ---- FTL's sizes (pixels, as on its 1280x720 screen) ----

	static final int REACTOR_W = 28, REACTOR_H = 7, REACTOR_STEP = 9, HATCH_W = 10;
	static final int BAR_W = 16, BAR_H = 6, BAR_STEP = 8, ICON_STEP = 36, SIDE_PANEL = 19;
	static final int SLOT_W = 95, SLOT_H = 38, SLOT_PITCH = 98;
	/** How far below the panel's top FTL's wire runs (the baseline everything sits on), for one row. */
	static final int ROW_H = 150, BOX_ROW_H = 104;

	/** Where each piece goes: worked out for the width it's given (one row, as FTL, when it fits; else the boxes on a second row). */
	private static final class Layout {
		int baseline1, baseline2, reactorX, wireEnd1, wireEnd2;
		final Map<Object, Integer> cx = new HashMap<Object, Integer>();
		int weaponsBoxX, dronesBoxX;
		boolean twoRows;
	}
	private Layout layout(int width) {
		Layout l = new Layout();
		l.reactorX = 6;
		int x = l.reactorX + REACTOR_W + HATCH_W + 44; // the first icon's centre: FTL's 90 from a reactor at 12
		for (Sys s : systems) { l.cx.put(s, x); x += ICON_STEP + (sidePanel(s.type) ? SIDE_PANEL : 0); }
		int rowEnd = x;
		int weaponsW = weapons == null ? 0 : 10 + boxWidth(weaponSlotCount) + 24;
		int dronesW = drones == null && droneSlots.isEmpty() ? 0 : 10 + boxWidth(droneSlotCount) + 24;
		l.twoRows = rowEnd + weaponsW + dronesW > width;
		l.baseline1 = ROW_H - 8;
		int bx = l.twoRows ? l.reactorX + REACTOR_W + HATCH_W + 44 : rowEnd;
		l.baseline2 = l.twoRows ? l.baseline1 + BOX_ROW_H : l.baseline1;
		if (weapons != null) { l.cx.put("weapons", bx); l.weaponsBoxX = bx + 10; bx = l.weaponsBoxX + boxWidth(weaponSlotCount) + 24; }
		if (drones != null || !droneSlots.isEmpty()) { l.cx.put("drones", bx); l.dronesBoxX = bx + 10; bx = l.dronesBoxX + boxWidth(droneSlotCount) + 4; }
		l.wireEnd1 = l.twoRows ? rowEnd - 20 : bx;
		l.wireEnd2 = bx;
		return l;
	}
	private static boolean sidePanel(SystemType t) { return t == SystemType.TELEPORTER || t == SystemType.CLOAKING || t == SystemType.MIND || t == SystemType.HACKING; }
	private static int boxWidth(int slots) { return Math.max(1, slots) * SLOT_PITCH + 7; }

	@Override public Dimension getPreferredSize() {
		Layout l = layout(getWidth() > 0 ? getWidth() : 640);
		return new Dimension(640, l.baseline2 + 12);
	}

	@Override protected void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		Layout l = layout(getWidth());
		// FTL draws its panel over dark space; here the station's dark box behind it, as wide as what's in it
		g.setColor(CargoParts.BOX);
		g.fillPolygon(CargoParts.cut(0, 0, Math.min(getWidth(), Math.max(l.wireEnd1, l.wireEnd2) + 14), getHeight(), 6));
		paintReactor(g, l);
		// the wire: under the reactor's strip, along under every icon, to the boxes
		g.setColor(CREAM);
		g.setStroke(new BasicStroke(2f));
		int hx = l.reactorX + REACTOR_W + 2 + HATCH_W;
		g.drawLine(hx, l.baseline1 - 8, hx + 8, l.baseline1);
		g.drawLine(hx + 8, l.baseline1, l.wireEnd1, l.baseline1);
		if (l.twoRows) g.drawLine(hx + 8, l.baseline2, l.wireEnd2, l.baseline2);
		for (Sys s : systems) paintSystem(g, s, l.cx.get(s), l.baseline1);
		if (weapons != null) { paintSystem(g, weapons, l.cx.get("weapons"), l.baseline2); paintBox(g, l.weaponsBoxX, l.baseline2, weaponSlots, weaponSlotCount, "WEAPONS", 1); }
		if (l.cx.containsKey("drones")) {
			paintSystem(g, drones != null ? drones : new Sys(SystemType.DRONE_CTRL, 0, 0, 0), l.cx.get("drones"), l.baseline2);
			paintBox(g, l.dronesBoxX, l.baseline2, droneSlots, droneSlotCount, "DRONES", weaponSlotCount + 1);
		}
		g.dispose();
	}

	/** The reactor column: the power left in green, what's in use as empty bars; the hatched strip beside it, bright beside the green. */
	private void paintReactor(Graphics2D g, Layout l) {
		int used = 0;
		for (Sys s : systems) used += s.power;
		if (weapons != null) used += weapons.power;
		if (drones != null) used += drones.power;
		int left = Math.max(0, reactor - used);
		int room = l.baseline1 - 17 - 4, step = reactor * REACTOR_STEP > room ? Math.max(3, room / Math.max(1, reactor)) : REACTOR_STEP, h = Math.max(2, step - 2);
		int x = l.reactorX, hx = x + REACTOR_W + 2;
		for (int k = 0; k < reactor; k++) {
			int bottom = l.baseline1 - 17 - k * step, top = bottom - h;
			boolean green = k < left;
			if (green) { g.setColor(GREEN); g.fillRect(x, top, REACTOR_W, h); }
			else { g.setColor(CREAM); g.setStroke(new BasicStroke(2f)); g.drawRect(x + 1, top + 1, REACTOR_W - 2, h - 2); }
			g.setColor(green ? CREAM : HATCH_DIM);
			g.setStroke(new BasicStroke(2f));
			g.drawLine(hx + 1, top, hx + HATCH_W - 2, top + step - 2); // FTL's strip: one stroke beside each bar
		}
		g.setColor(left > 0 ? CREAM : HATCH_DIM);
		g.setStroke(new BasicStroke(2f));
		int topAll = l.baseline1 - 17 - (reactor - 1) * step - h;
		g.drawLine(hx + HATCH_W, Math.min(topAll, l.baseline1 - 17), hx + HATCH_W, l.baseline1 - 8);
		if (left == 0) for (int i = 0; i < 3; i++) text(g, new String[] {"NOT", "ENOUGH", "POWER"}[i], FtlFont.SLOT, GREY, x, topAll - 40 + i * 12);
	}

	/** One system: its round icon (green, grey, orange or red as FTL colours it), its bars above, the tick down to the wire, its side panel. */
	private void paintSystem(Graphics2D g, Sys s, int cx, int baseline) {
		g.setColor(CREAM);
		g.setStroke(new BasicStroke(2f));
		g.drawLine(cx - 10, baseline, cx - 3, baseline - 8);
		String state = s.level > 0 && s.damaged >= s.level ? "red" : s.damaged > 0 ? "orange" : s.power > 0 ? "green" : "grey";
		String id = s.type.getId();
		BufferedImage icon = image("img/icons/s_" + id + "_" + state + "1.png");
		int cy = baseline - 27;
		if (icon != null) g.drawImage(icon, cx - icon.getWidth() / 2, cy - icon.getHeight() / 2, null);
		for (int k = 0; k < s.level; k++) {
			int bottom = baseline - 44 - k * BAR_STEP, top = bottom - BAR_H, x = cx - BAR_W / 2;
			if (k >= s.level - s.damaged) { // broken, at the top: red, slashed
				g.setColor(RED); g.setStroke(new BasicStroke(1f)); g.drawRect(x, top, BAR_W - 1, BAR_H - 1); g.drawLine(x + 2, bottom - 2, x + BAR_W - 3, top + 1);
			} else if (k < s.power) { g.setColor(GREEN); g.fillRect(x, top, BAR_W, BAR_H); }
			else { g.setColor(CREAM); g.setStroke(new BasicStroke(1f)); g.drawRect(x, top, BAR_W - 1, BAR_H - 1); }
		}
		if (sidePanel(s.type)) { // the plate and its buttons: layers on one canvas, as FTL keeps them
			String[] layers = s.type == SystemType.TELEPORTER ? new String[] {"button_teleport_base", "button_teleport_top_off", "button_teleport_bottom_off"}
					: s.type == SystemType.CLOAKING ? new String[] {"button_cloaking1_base", "button_cloaking1_off"}
					: s.type == SystemType.HACKING ? new String[] {"button_hack_base", "button_hack_drone_off"} : new String[] {"button_default_base", "button_default_off"};
			for (String layer : layers) {
				BufferedImage b = image("img/systemUI/" + layer + ".png");
				int dy = layer.equals("button_hack_drone_off") ? 26 : 0; // its button sits at the canvas's top; FTL puts it on the plate
				if (b != null) g.drawImage(b, cx + 3, baseline - 13 - b.getHeight() + dy, null);
			}
		}
		if (s.power > 0 && manned.contains(s.type)) { // a crew member at the station: FTL's little figure over the bars
			BufferedImage m = image("img/systemUI/manning_white.png");
			int top = baseline - 44 - (s.level - 1) * BAR_STEP - BAR_H;
			if (m != null) g.drawImage(m, cx - m.getWidth() / 2, top - m.getHeight() - 2, null);
		}
		if (s.type == SystemType.ARTILLERY) {
			BufferedImage b = image("img/systemUI/button_artillery_1.png");
			if (b != null) g.drawImage(b, cx + 9, baseline - 26 - b.getHeight(), null);
		}
	}

	/** A weapons or drones box: FTL's frame, a slot each (empty ones dark), the tab under it. */
	private void paintBox(Graphics2D g, int x, int baseline, List<Slot> slots, int count, String tab, int firstKey) {
		int top = baseline - 92;
		BufferedImage frame = image("img/box_weapons_bottom" + Math.max(2, Math.min(4, count)) + ".png");
		int w = boxWidth(count);
		if (frame != null && Math.max(2, Math.min(4, count)) == count) g.drawImage(frame, x - 9, top - 9, null);
		else { g.setColor(new Color(243, 255, 230, 40)); g.fillRect(x, top, w, 48); g.setColor(CREAM); g.setStroke(new BasicStroke(2f)); g.drawRect(x, top, w, 48); }
		for (int i = 0; i < count; i++) paintSlot(g, x + 5 + i * SLOT_PITCH, top + 6, i < slots.size() ? slots.get(i) : null, firstKey + i);
		// the tab: cream, the name in FTL's dark slate, its right end slanted
		int tw = FtlFont.TAB.width(tab) + 30, ty = top + 48;
		java.awt.Polygon p = new java.awt.Polygon(new int[] {x, x + tw, x + tw - 24, x}, new int[] {ty, ty, ty + 24, ty + 24}, 4);
		g.setColor(CREAM);
		g.fillPolygon(p);
		text(g, tab, FtlFont.TAB, TAB_TEXT, x + 6, ty + 3);
		if (firstKey == 1) { // FTL's Autofire button, at the weapons box's bottom right (drawn as FTL has it; the simulation doesn't fire)
			int aw = FtlFont.TAB.width("AUTOFIRE") + 16, ax = x + w - aw - 2, ay = ty - 2;
			g.setColor(new Color(150, 162, 152));
			g.fillRoundRect(ax, ay, aw, 26, 8, 8);
			g.setColor(CREAM);
			g.setStroke(new BasicStroke(2f));
			g.drawRoundRect(ax, ay, aw, 26, 8, 8);
			text(g, "AUTOFIRE", FtlFont.TAB, TAB_TEXT, ax + 8, ay + 4);
		}
	}

	/**
	 * One slot as FTL draws it: grey when off, cream when on. A narrow charge strip down the left with its notch on top,
	 * the slot's box beside it with the name (centred, two lines when long), the power pips stacked at its bottom left
	 * and its key at the bottom right. An empty slot is a dark box.
	 */
	private void paintSlot(Graphics2D g, int x, int y, Slot s, int key) {
		int bx = x + 10;
		if (s == null) { g.setColor(SLOT_DARK); g.fillRect(x, y, SLOT_W, SLOT_H); g.setColor(GREY); g.setStroke(new BasicStroke(2f)); g.drawRect(x + 1, y + 1, SLOT_W - 2, SLOT_H - 2); return; }
		boolean drone = droneSlots.contains(s);
		Color c = !s.on ? GREY : drone ? GREEN : CREAM; // a drone on is deployed: FTL draws it green
		g.setColor(c);
		g.setStroke(new BasicStroke(2f));
		// one frame, its top-left corner cut; the charge strip inside it on the left, then the slot's own box
		g.drawPolygon(new int[] {x + 1, x + 9, x + SLOT_W - 1, x + SLOT_W - 1, x + 1}, new int[] {y + 9, y + 1, y + 1, y + SLOT_H - 1, y + SLOT_H - 1}, 5);
		g.fillPolygon(new int[] {x + 1, x + 9, x + 9}, new int[] {y + 9, y + 1, y + 9}, 3);
		g.drawLine(bx, y + 1, bx, y + SLOT_H - 1);
		if (s.on) g.fillRect(x + 3, y + SLOT_H - 9, 6, 7); // charging: FTL fills the strip from the bottom
		int areaX = x + (drone ? 25 : 33), ty = y + 7; // FTL starts the name at the same place, one line or two
		List<String> lines = InfoTip.wrap(s.name, FtlFont.SLOT, drone ? 58 : 52);
		boolean pic = s.icon != null && lines.size() == 1 && (s.missiles || (s.on && drone));
		for (String line : lines) { text(g, line, FtlFont.SLOT, c, areaX, ty); ty += 15; }
		if (pic) {
			BufferedImage i = image(s.icon);
			if (i != null) { BufferedImage t = tint(i, c); g.drawImage(t, x + 43, y + 12, null); }
		}
		for (int k = 0; k < s.power; k++) { // the pips, bottom left, stacked
			int bottom = y + SLOT_H - 5 - k * 8, top = bottom - 6;
			if (s.on) g.fillRect(bx + 4, top, 16, 6);
			else { g.setStroke(new BasicStroke(1f)); g.drawRect(bx + 4, top, 15, 5); }
		}
		g.setStroke(new BasicStroke(1f)); // the key: a little box at the bottom right with its number
		g.drawRect(x + SLOT_W - 12, y + SLOT_H - 14, 10, 12);
		text(g, Integer.toString(key), FtlFont.NUM, c, x + SLOT_W - 9, y + SLOT_H - 12);
	}

	/** FTL's panel text: no shadow (the station's own text has one). */
	private static void text(Graphics2D g, String s, FtlFont f, Color c, int x, int y) { g.drawImage(f.render(s, c), x, y, null); }

	private static final Map<String, BufferedImage> IMAGES = new HashMap<String, BufferedImage>();
	static BufferedImage image(String path) {
		synchronized (IMAGES) {
			if (!IMAGES.containsKey(path)) IMAGES.put(path, LayoutEditor.image(path));
			return IMAGES.get(path);
		}
	}
	/** A white line picture in the slot's colour (FTL draws its slot pictures in the slot's grey or cream). */
	private static BufferedImage tint(BufferedImage src, Color c) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int yy = 0; yy < src.getHeight(); yy++) for (int xx = 0; xx < src.getWidth(); xx++) {
			int p = src.getRGB(xx, yy), a = p >>> 24;
			if (a == 0) continue;
			int lum = ((p >> 16) & 255) + ((p >> 8) & 255) + (p & 255);
			if (lum > 380) out.setRGB(xx, yy, (Math.min(255, a * lum / 765 + 40) << 24) | (c.getRGB() & 0xffffff)); // the light lines take the slot's colour; the dark is left out
		}
		return out;
	}
}
