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
	static final Color TAB_TEXT = new Color(32, 58, 66), ZOLTAN = new Color(255, 230, 60);
	/** FTL's order along the panel; the ones a ship hasn't got leave no gap. Doors, Piloting, Sensors and the Backup Battery are left out (heromedel). */
	static final SystemType[] ORDER = {SystemType.SHIELDS, SystemType.ENGINES, SystemType.MEDBAY, SystemType.CLONEBAY, SystemType.OXYGEN,
			SystemType.TELEPORTER, SystemType.CLOAKING, SystemType.ARTILLERY, SystemType.MIND, SystemType.HACKING};

	/** A powered system as the panel holds it. */
	static final class Sys {
		final SystemType type; final int level, damaged; int power, zoltan;
		Sys(SystemType type, int level, int damaged, int power) { this.type = type; this.level = level; this.damaged = Math.min(damaged, level); this.power = Math.min(power, level - this.damaged); }
		int usable() { return level - damaged; }
		/** The bars it takes from the reactor: a Zoltan's are its own. */
		int fromReactor() { return Math.max(0, power - zoltan); }
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

	/** As the save had it, for Reset: each system's power, each weapon's and drone's on or off. */
	private final Map<Object, Integer> saved = new HashMap<Object, Integer>();
	/** A refusal flashing over the panel, as FTL's warnings do, and when it goes. */
	private String warning; private long warningUntil; private boolean warningAtReactor; private int warningX;
	/** Each slot's centre, so a warning flashes over the slot that was refused. */
	private final Map<Object, Integer> centreX = new HashMap<Object, Integer>();
	/** Where each thing was drawn, for the mouse. */
	private final List<Object[]> hits = new ArrayList<Object[]>();

	PowerPanel() {
		setOpaque(false);
		setToolTipText(""); // tips come from getToolTipText(MouseEvent)
		addMouseListener(new java.awt.event.MouseAdapter() {
			@Override public void mousePressed(java.awt.event.MouseEvent e) { click(at(e.getX(), e.getY()), javax.swing.SwingUtilities.isRightMouseButton(e)); }
		});
	}

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
		zoltans(s, bp);
		if (weapons != null) { weapons.power = 0; for (Slot w : weaponSlots) if (w.on) weapons.power += w.power; weapons.power = Math.min(weapons.power, weapons.usable()); }
		if (drones != null) { drones.power = 0; for (Slot d : droneSlots) if (d.on) drones.power += d.power; drones.power = Math.min(drones.power, drones.usable()); }
		manned.clear();
		if (bp != null && bp.getSystemList() != null) for (SystemType t : new SystemType[] {SystemType.SHIELDS, SystemType.ENGINES, SystemType.WEAPONS}) {
			net.blerf.ftl.xml.ShipBlueprint.SystemList.SystemRoom[] rooms = bp.getSystemList().getSystemRoom(t);
			if (rooms == null || rooms.length == 0) continue;
			for (net.blerf.ftl.parser.SavedGameParser.CrewState c : s.getCrewList()) if (c.getRoomId() == rooms[0].getRoomId()) { manned.add(t); break; }
		}
		weaponSlotCount = Math.max(weaponSlotCount, weaponSlots.size());
		droneSlotCount = Math.max(droneSlotCount, droneSlots.size());
		saved.clear();
		for (Sys x : all()) saved.put(x, x.power);
		for (Slot x : weaponSlots) saved.put(x, x.on ? 1 : 0);
		for (Slot x : droneSlots) saved.put(x, x.on ? 1 : 0);
		warning = null;
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

	/** A Zoltan powers the system in the room they stand in, a bar each, free (FTL: "Zoltan Bonus Power"). */
	private void zoltans(ShipState s, net.blerf.ftl.xml.ShipBlueprint bp) {
		if (bp == null || bp.getSystemList() == null) return;
		for (Sys x : all()) {
			net.blerf.ftl.xml.ShipBlueprint.SystemList.SystemRoom[] rooms = bp.getSystemList().getSystemRoom(x.type);
			if (rooms == null || rooms.length == 0) continue;
			int n = 0;
			for (net.blerf.ftl.parser.SavedGameParser.CrewState c : s.getCrewList()) if (c.getRace() == net.blerf.ftl.parser.SavedGameParser.CrewType.ENERGY && c.getRoomId() == rooms[0].getRoomId()) n++;
			x.zoltan = Math.min(n, x.usable());
			if (x.type != SystemType.WEAPONS && x.type != SystemType.DRONE_CTRL) x.power = Math.min(x.usable(), x.power + x.zoltan);
		}
	}
	/** Every powered system the panel shows, Weapons and Drones too. */
	private List<Sys> all() {
		List<Sys> a = new ArrayList<Sys>(systems);
		if (weapons != null) a.add(weapons);
		if (drones != null) a.add(drones);
		return a;
	}
	/** The reactor's bars not in use. */
	int left() { int used = 0; for (Sys x : all()) used += x.fromReactor(); return reactor - used; }

	// ---- The rules (FTL's, as its screen and its own warnings have them) ----

	/** A click on something: a system takes a bar (left) or gives one back (right); a weapon or drone goes on or off. */
	void click(Object what, boolean right) {
		if (what == null || "reactor".equals(what)) return;
		warningX = centreX.containsKey(what) ? centreX.get(what) : getWidth() / 2;
		boolean done;
		if (what instanceof Sys) {
			Sys x = (Sys) what;
			if (x == weapons || x == drones) done = right ? lastOff(x == weapons ? weaponSlots : droneSlots, x) : firstOn(x == weapons ? weaponSlots : droneSlots, x);
			else done = right ? remove(x) : add(x);
		} else if (what instanceof Slot) {
			Slot sl = (Slot) what;
			boolean drone = droneSlots.contains(sl);
			Sys x = drone ? drones : weapons;
			done = sl.on ? off(sl, x) : !right && on(sl, x);
		} else return;
		sound(done ? (right || (what instanceof Slot && !((Slot) what).on) ? "select_down2" : "select_up1") : "select_b_fail1");
		repaint();
	}
	/** One more bar (Shields: a barrier, two bars). */
	boolean add(Sys x) {
		int step = x.type == SystemType.SHIELDS ? 2 : 1;
		if (x.power + step > x.usable()) {
			if (x.type == SystemType.SHIELDS && x.damaged == 0 && x.power + 1 == x.usable()) warn("warning_needs_upgrade", "REQUIRES\nSYSTEM\nUPGRADE", false);
			return false;
		}
		if (left() < step) { warn("warning_no_power", "NOT\nENOUGH\nPOWER", true); return false; }
		x.power += step;
		return true;
	}
	/** One bar fewer (Shields: a barrier), never a Zoltan's. */
	boolean remove(Sys x) {
		int step = x.type == SystemType.SHIELDS && (x.power - x.zoltan) % 2 == 0 ? 2 : 1;
		if (x.power - step < x.zoltan) return false;
		x.power -= step;
		return true;
	}
	/** A weapon or drone on, with its whole power, if it fits. */
	boolean on(Slot sl, Sys x) {
		if (x == null || sl.power > x.level) { warn("warning_needs_upgrade", "REQUIRES\nSYSTEM\nUPGRADE", false); return false; }
		if (sl.power > x.usable()) { warn("warning_system_broken", "SYSTEM\nBROKEN", false); return false; }
		if (x.power + sl.power > x.usable()) { warn("warning_no_system_power", "NOT ENOUGH\nSYSTEM POWER", false); return false; }
		int fromReactor = Math.max(0, x.power + sl.power - x.zoltan) - x.fromReactor();
		if (left() < fromReactor) { warn("warning_no_power", "NOT\nENOUGH\nPOWER", true); return false; }
		sl.on = true;
		x.power += sl.power;
		return true;
	}
	boolean off(Slot sl, Sys x) {
		sl.on = false;
		if (x != null) x.power = Math.max(0, x.power - sl.power);
		return true;
	}
	/** Clicking Weapons' or Drones' own icon: the first one off that fits goes on (FTL's Add Power on the system). */
	private boolean firstOn(List<Slot> slots, Sys x) {
		for (Slot sl : slots) if (!sl.on && sl.power > 0 && x.power + sl.power <= x.usable()) {
			String w = warning; long u = warningUntil;
			if (on(sl, x)) return true;
			warning = w; warningUntil = u;
		}
		for (Slot sl : slots) if (!sl.on && sl.power > 0) return on(sl, x); // none fits: FTL's warning for the first
		return false;
	}
	/** Right on the icon: the rightmost one on goes off (as FTL depowers). */
	private boolean lastOff(List<Slot> slots, Sys x) {
		for (int i = slots.size() - 1; i >= 0; i--) if (slots.get(i).on) return off(slots.get(i), x);
		return false;
	}
	/** Back as the save had it. */
	void reset() {
		for (Sys x : all()) if (saved.containsKey(x)) x.power = saved.get(x);
		for (Slot x : weaponSlots) if (saved.containsKey(x)) x.on = saved.get(x) == 1;
		for (Slot x : droneSlots) if (saved.containsKey(x)) x.on = saved.get(x) == 1;
		warning = null;
		repaint();
	}
	/** FTL's own warning, in its words from its text files, flashing for a moment. */
	private void warn(String id, String fallback, boolean atReactor) {
		String t = SystemsPanel.ftlText(id);
		warning = (t == null ? fallback : t).replace("\\n", "\n");
		warningAtReactor = atReactor;
		warningUntil = System.currentTimeMillis() + 1600;
		javax.swing.Timer tm = new javax.swing.Timer(1700, new java.awt.event.ActionListener() { public void actionPerformed(java.awt.event.ActionEvent e) { repaint(); } });
		tm.setRepeats(false);
		tm.start();
	}

	/** FTL's own clicks from its ftl.dat (audio/waves/ui), played as the game plays them; quiet when there's no sound device. */
	private static final Map<String, byte[]> SOUNDS = new HashMap<String, byte[]>();
	static void sound(final String name) {
		new Thread("power-sound") {
			@Override public void run() {
				try {
					byte[] b;
					synchronized (SOUNDS) {
						b = SOUNDS.get(name);
						if (b == null) {
							java.io.InputStream in = DataManager.get().getResourceInputStream("audio/waves/ui/" + name + ".wav");
							java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
							byte[] buf = new byte[8192];
							for (int n; (n = in.read(buf)) > 0;) bo.write(buf, 0, n);
							in.close();
							b = bo.toByteArray();
							SOUNDS.put(name, b);
						}
					}
					final javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
					clip.open(javax.sound.sampled.AudioSystem.getAudioInputStream(new java.io.ByteArrayInputStream(b)));
					clip.addLineListener(new javax.sound.sampled.LineListener() {
						public void update(javax.sound.sampled.LineEvent e) { if (e.getType() == javax.sound.sampled.LineEvent.Type.STOP) clip.close(); }
					});
					clip.start();
				} catch (Throwable t) { // no sound: the panel works the same
				}
			}
		}.start();
	}

	/** What's under the mouse: a system, a weapon or drone slot, the reactor ("reactor"), or null. */
	private Object at(int x, int y) {
		for (int i = hits.size() - 1; i >= 0; i--) if (((java.awt.Rectangle) hits.get(i)[0]).contains(x, y)) return hits.get(i)[1];
		return null;
	}
	@Override public String getToolTipText(java.awt.event.MouseEvent e) {
		Object o = at(e.getX(), e.getY());
		return o == null ? null : tip(o);
	}
	@Override public java.awt.Point getToolTipLocation(java.awt.event.MouseEvent e) { return new java.awt.Point(e.getX() + 14, e.getY() - 10 - tipHeight(e)); }
	private int tipHeight(java.awt.event.MouseEvent e) { String t = getToolTipText(e); return t == null ? 0 : FtlTip.size(t).height; }
	@Override public javax.swing.JToolTip createToolTip() { FtlTip t = new FtlTip(); t.setComponent(this); return t; }

	/** The hover, in FTL's words: its tooltip for the system, the level, the status; a weapon's name and power. */
	String tip(Object o) {
		if ("reactor".equals(o)) { String t = SystemsPanel.ftlText("tooltip_powerTotal"); return (t == null ? "Reactor: Unused reactor energy available to power your systems." : t) + "\n\n" + left() + " of " + reactor + " bars free"; }
		if (o instanceof Sys) {
			Sys x = (Sys) o;
			String id = x.type.getId();
			String head = SystemsPanel.ftlText("tooltip_" + (id.equals("pilot") ? "pilot" : id));
			StringBuilder b = new StringBuilder(head == null ? DryDockShop.systemTitle(id) : head);
			String said = SystemsPanel.levelLabel(id, x.level), lv = SystemsPanel.ftlText("level");
			if (!said.isEmpty()) b.append("\n\n").append(lv == null ? "Level " + x.level + ": " + said : lv.replace("\\1", Integer.toString(x.level)).replace("\\2", said));
			b.append("\n\n").append(word("status", "Status:"));
			b.append("\n-").append(x.power >= x.level ? word("full_powered", "Fully Powered") : x.power > 0 ? word("partial_powered", "Partially Powered") : word("unpowered", "Unpowered"));
			if (x.damaged > 0) b.append("\n-").append(x.damaged >= x.level ? word("destroyed", "Destroyed") : word("damaged", "Damaged"));
			if (x.zoltan > 0) b.append("\n-").append(word("zoltan", "Zoltan Bonus Power"));
			String add = SystemsPanel.ftlText("add_power"), rem = SystemsPanel.ftlText("remove_power");
			b.append("\n\n").append(add == null ? "Add Power: Left Click" : add.replace("\\1", "Left Click"));
			b.append("\n").append(rem == null ? "Remove Power: Right Click" : rem.replace("\\1", "Right Click"));
			return b.toString();
		}
		Slot sl = (Slot) o;
		return sl.name + "\nPower: " + sl.power + "\n\n" + (sl.on ? "Click to depower" : "Click to power");
	}
	private static String word(String id, String fallback) { String t = SystemsPanel.ftlText(id); return t == null ? fallback : t; }

	/** FTL's tooltip: a black box, a cream border, its words in white. */
	static final class FtlTip extends javax.swing.JToolTip {
		FtlTip() { setOpaque(true); setBorder(null); }
		static Dimension size(String t) {
			int w = 0, h = 0;
			for (String line : lines(t)) { w = Math.max(w, FtlFont.BODY.width(line)); h += 16; }
			return new Dimension(w + 20, h + 14);
		}
		static List<String> lines(String t) {
			List<String> out = new ArrayList<String>();
			for (String para : t.split("\n", -1)) { if (para.isEmpty()) out.add(""); else out.addAll(InfoTip.wrap(para, FtlFont.BODY, 440)); }
			return out;
		}
		@Override public Dimension getPreferredSize() { return size(getTipText() == null ? "" : getTipText()); }
		@Override public void paint(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			g.setColor(Color.black);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(CREAM);
			g.setStroke(new BasicStroke(2f));
			g.drawRect(1, 1, getWidth() - 2, getHeight() - 2);
			int y = 8;
			for (String line : lines(getTipText() == null ? "" : getTipText())) { if (!line.isEmpty()) text(g, line, FtlFont.BODY, Color.white, 10, y); y += 16; }
			g.dispose();
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
		hits.clear();
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
		if (warning != null && System.currentTimeMillis() < warningUntil) { // FTL's warning, over the panel where it was refused
			String[] lines = warning.split("\n");
			int wx = warningAtReactor ? l.reactorX + REACTOR_W + HATCH_W + 8 : warningX, wy = 4;
			for (String line : lines) {
				int lw = FtlFont.MENU.width(line);
				CargoParts.text(g, line, FtlFont.MENU, Color.white, warningAtReactor ? wx : Math.max(4, wx - lw / 2), wy);
				wy += 18;
			}
		}
		g.dispose();
	}

	/** The reactor column: the power left in green, what's in use as empty bars; the hatched strip beside it, bright beside the green. */
	private void paintReactor(Graphics2D g, Layout l) {
		int left = Math.max(0, left());
		hits.add(new Object[] {new java.awt.Rectangle(l.reactorX - 2, 0, REACTOR_W + HATCH_W + 6, l.baseline1), "reactor"});
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
		if (left == 0 && (warning == null || System.currentTimeMillis() >= warningUntil || !warningAtReactor)) { // FTL's note over an empty reactor, in grey
			String t = SystemsPanel.ftlText("warning_no_power");
			int ty = 4;
			for (String line : (t == null ? "NOT\\nENOUGH\\nPOWER" : t).split("\\\\n")) { text(g, line, FtlFont.SLOT, GREY, hx + HATCH_W + 8, ty); ty += 11; }
		}
	}

	/** One system: its round icon (green, grey, orange or red as FTL colours it), its bars above, the tick down to the wire, its side panel. */
	private void paintSystem(Graphics2D g, Sys s, int cx, int baseline) {
		hits.add(new Object[] {new java.awt.Rectangle(cx - 17, baseline - 46 - Math.max(1, s.level) * BAR_STEP, 34, Math.max(1, s.level) * BAR_STEP + 36), s});
		centreX.put(s, cx);
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
			} else if (k < s.zoltan) { g.setColor(ZOLTAN); g.fillRect(x, top, BAR_W, BAR_H); } // a Zoltan's bar, free
			else if (k < s.power) { g.setColor(GREEN); g.fillRect(x, top, BAR_W, BAR_H); }
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
		if (s != null) hits.add(new Object[] {new java.awt.Rectangle(x, y, SLOT_W, SLOT_H), s});
		if (s != null) centreX.put(s, x + SLOT_W / 2);
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
