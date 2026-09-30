package homeplanet.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import net.blerf.ftl.parser.SavedGameParser.SystemType;

/**
 * A ship designed from scratch in the station's Design Ship window: rooms on the grid, doors and airlocks, and which
 * system sits in which room (with its station). Kept in designs.xml in the vault. Art, mounts and the blueprint's numbers
 * come in later steps; this is the layout.
 */
public class ShipDesign {
	private static final Logger log = LoggerFactory.getLogger(ShipDesign.class);
	/** The designs file: designs.xml in the vault. */
	public static File file() { return homeplanet.vault.Vault.get().designsFile(); }
	private static final String CRLF = "\r\n";

	/** Room sizes FTL's own ships use. */
	public static final int[][] ROOM_SIZES = {{2, 2}, {2, 1}, {1, 2}};

	public static class Room {
		public int x, y, w, h;
		public Room(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
		public boolean covers(int tx, int ty) { return tx >= x && tx < x + w && ty >= y && ty < y + h; }
	}

	/** A weapon mount on the hull art: x, y in the art's pixels; FTL's rotate / mirror flags and the slide direction. */
	public static class Mount {
		public int x, y;
		public boolean rotate = true, mirror = false;
		public String slide = "up"; // up, down, left, right, no
		/** The artillery gun's mount (FTL puts these after the weapon mounts). */
		public boolean artillery = false;
		public Mount(int x, int y) { this.x = x; this.y = y; }
		public Mount copy() { Mount m = new Mount(x, y); m.rotate = rotate; m.mirror = mirror; m.slide = slide; m.artillery = artillery; return m; }
	}

	public String id = "";   // DESIGN_1...
	/** Hull art: "" (none yet), "game:<gfx>" (a ship's art from the game) or "file:<path>" (a PNG the station keeps a copy of, in the vault's art folder). */
	public String art = "", floor = "";
	/** Where the hull art's top-left sits, in pixels from the room grid's origin; the floor art's, from the hull art's. */
	public int artX, artY, floorX, floorY;
	/** The hull art's size, in percent (the floor, gibs and the cloak glow follow it). */
	public int artScale = 100;
	public final List<Mount> mounts = new ArrayList<Mount>();
	/** Shield ellipse: half-width, half-height, and offset (FTL's ELLIPSE line); 0 x 0 means "work it out from the art". */
	public int ellipseW, ellipseH, ellipseX, ellipseY;
	/** Gibs: "auto" (cut from the hull art) or "files" (the gib PNGs listed). */
	public String gibs = "auto"; // game (the game ship's own), cut (from the hull art), files (her own pictures); auto: game with game art, else cut
	/** Whether she uses the game ship's own gibs (her hull art is the game's, and she hasn't been given other gibs). */
	public boolean gameGibs() { return art.startsWith("game:") && ("game".equals(gibs) || "auto".equals(gibs)); }
	public final List<String> gibFiles = new ArrayList<String>();
	/** Step C, her blueprint: built into the companion mod; a starter ship (can be commissioned); hull, reactor, drone slots. */
	public boolean built = false, starter = false;
	/**
	 * A built copy of a deleted design, kept because ships still fly it (or their kept earlier versions do): it stays in
	 * the Federation Home Planet Mod, out of the Design list and Commission, until Clean up blueprints finds it unused.
	 */
	public boolean retired = false;
	public int hull = 30, reactor = 8, droneSlots = 2;
	/** Where she sits on screen, in squares (FTL's X_OFFSET / Y_OFFSET); -1 = worked out from the art. */
	public int offX = -1, offY = -1;
	/** Placed systems she doesn't start with (they can be bought or installed later). */
	public final java.util.Set<String> notAtStart = new java.util.TreeSet<String>();
	/** Her class, default name and new-game loadout (null until the blueprint is first built). */
	public CompanionMod.Loadout loadout;
	public String name = "";
	/** Blueprint version: a layout change on a design that has ships becomes a new version (the old one is kept for them). */
	public int version = 1;
	/** Set on a kept old version: the id of the design it belongs to (these aren't listed or commissioned). */
	public String frozenOf = null;
	/**
	 * Set on the built copy of a design: the id of the design it belongs to. The companion mod is made from these copies;
	 * the design itself is the working copy, edited and saved freely until Build blueprint takes a fresh copy.
	 */
	public String snapshotOf = null;
	/** Set by the editor when the edit should be saved as a new version (not written to the file). */
	public transient int pendingVersion = 0;
	/** Set by the editor when Build blueprint was pressed: the list takes the snapshot when it saves (not written to the file). */
	public transient boolean pendingBuild = false;
	/** A working copy the mod exports from: not a snapshot or a kept old version. */
	public boolean isWorking() { return snapshotOf == null && frozenOf == null; }
	/** The built copy of the design with this id, or null. */
	public static ShipDesign snapshot(List<ShipDesign> all, String id) {
		for (ShipDesign x : all) if (id.equals(x.snapshotOf)) return x;
		return null;
	}
	public String made = "";
	/** Rooms in id order (room id = index). */
	public final List<Room> rooms = new ArrayList<Room>();
	public final List<CompanionMod.Door> doors = new ArrayList<CompanionMod.Door>();
	public final Map<String, CompanionMod.Sys> systems = new LinkedHashMap<String, CompanionMod.Sys>();

	// ---- geometry ----

	/** The room on this tile, or -1. */
	public int roomAt(int tx, int ty) {
		for (int i = 0; i < rooms.size(); i++) if (rooms.get(i).covers(tx, ty)) return i;
		return -1;
	}
	/** Would a w x h room at (x, y) fit on the grid without overlapping another (ignoring room 'skip')? */
	public boolean fits(int x, int y, int w, int h, int cols, int rows, int skip) {
		if (x < 0 || y < 0 || x + w > cols || y + h > rows) return false;
		for (int i = 0; i < rooms.size(); i++) {
			if (i == skip) continue;
			Room r = rooms.get(i);
			if (x < r.x + r.w && r.x < x + w && y < r.y + r.h && r.y < y + h) return false;
		}
		return true;
	}
	/**
	 * The door that would sit on this wall: v=1 is the left wall of tile (x, y), v=0 its top wall (FTL's layout convention).
	 * Returns null when there's no wall there (both sides the same room, or both sides empty).
	 */
	public CompanionMod.Door doorFor(int x, int y, int v) {
		int here = roomAt(x, y), there = v == 1 ? roomAt(x - 1, y) : roomAt(x, y - 1);
		if (here == there) return null;
		if (here < 0) return new CompanionMod.Door(x, y, there, -1, v);
		if (there < 0) return new CompanionMod.Door(x, y, here, -1, v);
		return new CompanionMod.Door(x, y, Math.min(here, there), Math.max(here, there), v);
	}
	public CompanionMod.Door doorAt(int x, int y, int v) {
		for (CompanionMod.Door d : doors) if (d.x == x && d.y == y && d.v == v) return d;
		return null;
	}
	/**
	 * Recomputes every door's rooms from where it sits. A door whose wall is gone, or that would turn from a door into an
	 * airlock (or back) because a room moved, is dropped rather than quietly changed.
	 */
	public void refreshDoors() {
		List<CompanionMod.Door> keep = new ArrayList<CompanionMod.Door>();
		for (CompanionMod.Door d : doors) {
			CompanionMod.Door n = doorFor(d.x, d.y, d.v);
			if (n != null && (n.b < 0) == (d.b < 0)) keep.add(n);
		}
		doors.clear();
		doors.addAll(keep);
	}
	/** Removes a room: its systems come off, later rooms move down one number, and the doors follow. */
	public void removeRoom(int id) {
		rooms.remove(id);
		for (String sys : systemsIn(id)) removeSystem(sys);
		for (CompanionMod.Sys s : systems.values()) if (s.room > id) s.room--;
		refreshDoors();
	}
	/** Takes a system off the ship; the artillery's gun mount goes with it. */
	public void removeSystem(String id) {
		systems.remove(id);
		if ("artillery".equals(id)) for (java.util.Iterator<Mount> it = mounts.iterator(); it.hasNext();) if (it.next().artillery) it.remove();
	}
	/** Moves a room; doors and systems keep to it where they still can. */
	public void moveRoom(int id, int x, int y) {
		Room r = rooms.get(id);
		int dx = x - r.x, dy = y - r.y;
		r.x = x;
		r.y = y;
		// her own airlocks go with her; doors to other rooms stay where they are (and vanish if the wall is gone)
		for (CompanionMod.Door dr : doors) if (dr.b < 0 && dr.a == id) { dr.x += dx; dr.y += dy; }
		refreshDoors();
		for (CompanionMod.Sys s : systems.values()) {
			if (s.room == id && s.square != null && s.square >= r.w * r.h) s.square = 0;
		}
	}

	/** The rooms' bounding box in squares: {minX, minY, maxX, maxY} (max exclusive), or null with no rooms. */
	public int[] bounds() {
		if (rooms.isEmpty()) return null;
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (Room r : rooms) { b[0] = Math.min(b[0], r.x); b[1] = Math.min(b[1], r.y); b[2] = Math.max(b[2], r.x + r.w); b[3] = Math.max(b[3], r.y + r.h); }
		return b;
	}
	/** Moves the whole ship (rooms, doors and the art with them) by dx, dy squares. False if that would leave the grid. */
	public boolean shift(int dx, int dy) {
		int[] b = bounds();
		if (b != null && (b[0] + dx < 0 || b[1] + dy < 0)) return false;
		for (Room r : rooms) { r.x += dx; r.y += dy; }
		for (CompanionMod.Door d : doors) { d.x += dx; d.y += dy; }
		artX += dx * DesignExport.SQ;
		artY += dy * DesignExport.SQ;
		return true;
	}
	/**
	 * Mirrors the ship top to bottom (FTL's ships face right, so left-to-right would turn her round): rooms, doors,
	 * stations and the art's place. The pictures themselves and the mounts are the caller's to flip ({@link ShipArt#flip}).
	 */
	public void flipVertically(int artHeight) {
		int[] b = bounds();
		if (b == null) return;
		int top = b[1], bottom = b[3]; // rows top..bottom-1 become bottom-1..top
		for (Room r : rooms) r.y = top + bottom - (r.y + r.h);
		for (CompanionMod.Door d : doors) d.y = d.v == 1 ? top + bottom - 1 - d.y : top + bottom - d.y; // a tile's wall, or the wall between two tiles
		for (CompanionMod.Sys s : systems.values()) {
			if (s.room < 0 || s.room >= rooms.size()) continue;
			Room r = rooms.get(s.room);
			if (s.square != null && s.square >= 0 && s.square < r.w * r.h) s.square = (r.h - 1 - s.square / r.w) * r.w + s.square % r.w;
			if ("up".equals(s.dir)) s.dir = "down"; else if ("down".equals(s.dir)) s.dir = "up";
		}
		if (artHeight > 0) artY = (top + bottom) * DesignExport.SQ - (artY + artHeight);
		ellipseY = -ellipseY;
	}

	/**
	 * Fills a design from a game ship: her rooms, doors, systems (with stations and start flags), hull, reactor,
	 * slots, loadout and art, placed a couple of squares in from the grid's corner. The name is left alone.
	 */
	public static boolean fromGameShip(ShipDesign d, String bpId) {
		net.blerf.ftl.parser.DataManager dm = net.blerf.ftl.parser.DataManager.get();
		net.blerf.ftl.xml.ShipBlueprint bp = dm.getShips().get(bpId);
		if (bp == null) bp = dm.getAutoShips().get(bpId);
		if (bp == null) return false;
		net.blerf.ftl.model.shiplayout.ShipLayout lay = dm.getShipLayout(bp.getLayoutId());
		if (lay == null) return false;
		final int in = 2; // squares in from the corner
		d.rooms.clear(); d.doors.clear(); d.systems.clear(); d.notAtStart.clear(); d.mounts.clear();
		for (int i = 0; i < lay.getRoomCount(); i++) {
			net.blerf.ftl.model.shiplayout.ShipLayoutRoom r = lay.getRoom(i);
			d.rooms.add(new Room(r.locationX + in, r.locationY + in, r.squaresH, r.squaresV));
		}
		for (CompanionMod.Door x : CompanionMod.doorsOf(lay)) d.doors.add(new CompanionMod.Door(x.x + in, x.y + in, x.a, x.b, x.v));
		for (CompanionMod.Sys s : CompanionMod.layoutOf(bp).values()) {
			if (s.room < 0 || s.room >= d.rooms.size()) continue;
			d.systems.put(s.id, s.copy());
			SystemType t = SystemType.findById(s.id);
			net.blerf.ftl.xml.ShipBlueprint.SystemList.SystemRoom[] rs = t == null || bp.getSystemList() == null ? null : bp.getSystemList().getSystemRoom(t);
			if (rs != null && rs.length > 0 && rs[0].getStart() != null && !rs[0].getStart()) d.notAtStart.add(s.id);
		}
		if (bp.getHealth() != null) d.hull = bp.getHealth().amount;
		if (bp.getMaxPower() != null) d.reactor = bp.getMaxPower().amount;
		if (bp.getDroneSlots() != null) d.droneSlots = bp.getDroneSlots();
		d.loadout = CompanionMod.loadoutOf(bpId);
		d.offX = lay.getOffsetX(); d.offY = lay.getOffsetY();
		d.artScale = 100;
		if (bp.getGraphicsBaseName() != null && ShipArt.adoptGameShip(d, bp.getGraphicsBaseName())) {
			d.artX += in * DesignExport.SQ;
			d.artY += in * DesignExport.SQ;
		} else { d.art = ""; d.floor = ""; }
		return true;
	}

	/** The systems in a room. */
	public List<String> systemsIn(int room) {
		List<String> out = new ArrayList<String>();
		for (CompanionMod.Sys s : systems.values()) if (s.room == room) out.add(s.id);
		return out;
	}

	// ---- checks (see ShipChecks) ----

	/** What stops this design from working as a ship (empty when she's sound). */
	public List<String> problems() { return ShipChecks.check(this, ShipChecks.Context.DESIGN, null, null).problems; }
	/** Worth knowing, but she'd still fly. */
	public List<String> warnings() { return ShipChecks.check(this, ShipChecks.Context.DESIGN, null, null).warnings; }

	/** Manned systems get a station; FTL's own defaults where it has them (the square is kept inside the room). */
	public static String defaultDir(String id) {
		if ("pilot".equals(id)) return "right";
		if ("engines".equals(id)) return "down";
		if ("shields".equals(id)) return "left";
		return "up";
	}
	public static boolean manned(String id) {
		SystemType t = SystemType.findById(id);
		return t != null && Retrofit.isManned(t);
	}
	public static boolean bay(String id) { return "medbay".equals(id) || "clonebay".equals(id); }

	// ---- designs.xml ----

	/** One design element (from designs.xml, or kept inside a remodel). */
	public static ShipDesign parse(Element e) {
		ShipDesign d = new ShipDesign();
		d.id = e.getAttribute("id");
		d.name = e.getAttribute("name");
		d.made = e.getAttribute("made");
		d.art = e.getAttribute("art");
		d.floor = e.getAttribute("floor");
		d.artX = numOr(e, "artX", 0); d.artY = numOr(e, "artY", 0);
		d.floorX = numOr(e, "floorX", 0); d.floorY = numOr(e, "floorY", 0);
		d.ellipseW = numOr(e, "ellipseW", 0); d.ellipseH = numOr(e, "ellipseH", 0);
		d.ellipseX = numOr(e, "ellipseX", 0); d.ellipseY = numOr(e, "ellipseY", 0);
		if (e.hasAttribute("gibs")) d.gibs = e.getAttribute("gibs");
		d.built = "true".equals(e.getAttribute("built"));
		d.starter = "true".equals(e.getAttribute("starter"));
		d.hull = numOr(e, "hull", 30); d.reactor = numOr(e, "reactor", 8); d.droneSlots = numOr(e, "droneSlots", 2);
		d.offX = numOr(e, "offX", -1); d.offY = numOr(e, "offY", -1);
		d.version = numOr(e, "version", 1);
		d.artScale = numOr(e, "artScale", 100);
		if (e.hasAttribute("frozenOf")) d.frozenOf = e.getAttribute("frozenOf");
		if (e.hasAttribute("snapshotOf")) d.snapshotOf = e.getAttribute("snapshotOf");
		d.retired = "true".equals(e.getAttribute("retired"));
		NodeList ns = e.getElementsByTagName("nostart");
		for (int j = 0; j < ns.getLength(); j++) d.notAtStart.add(((Element) ns.item(j)).getAttribute("id"));
		d.loadout = CompanionMod.readLoadout(e);
		NodeList ms = e.getElementsByTagName("mount");
		for (int j = 0; j < ms.getLength(); j++) {
			Element m = (Element) ms.item(j);
			Mount mt = new Mount(num(m, "x"), num(m, "y"));
			mt.rotate = "true".equals(m.getAttribute("rotate"));
			mt.mirror = "true".equals(m.getAttribute("mirror"));
			mt.slide = m.getAttribute("slide");
			mt.artillery = "true".equals(m.getAttribute("artillery"));
			d.mounts.add(mt);
		}
		NodeList gs = e.getElementsByTagName("gib");
		for (int j = 0; j < gs.getLength(); j++) d.gibFiles.add(((Element) gs.item(j)).getAttribute("file"));
		NodeList rs = e.getElementsByTagName("room");
		for (int j = 0; j < rs.getLength(); j++) {
			Element r = (Element) rs.item(j);
			d.rooms.add(new Room(num(r, "x"), num(r, "y"), num(r, "w"), num(r, "h")));
		}
		NodeList dr = e.getElementsByTagName("door");
		for (int j = 0; j < dr.getLength(); j++) {
			Element r = (Element) dr.item(j);
			d.doors.add(new CompanionMod.Door(num(r, "x"), num(r, "y"), num(r, "a"), num(r, "b"), num(r, "v")));
		}
		NodeList ss = e.getElementsByTagName("sys");
		for (int j = 0; j < ss.getLength(); j++) {
			Element se = (Element) ss.item(j);
			CompanionMod.Sys s = new CompanionMod.Sys(se.getAttribute("id"));
			s.room = num(se, "room");
			if (se.hasAttribute("power")) s.power = num(se, "power");
			if (se.hasAttribute("dir")) s.dir = se.getAttribute("dir");
			if (se.hasAttribute("square")) s.square = num(se, "square");
			if (se.hasAttribute("weapon")) s.weapon = se.getAttribute("weapon");
			d.systems.put(s.id, s);
		}
		return d;
	}

	/**
	 * The designs on file. A file that can't be read in full gives what could be read (so the screens still work),
	 * and {@link #save} then refuses to write over it: see {@link #intact}.
	 */
	public static List<ShipDesign> load() {
		List<ShipDesign> out = new ArrayList<ShipDesign>();
		File FILE = file();
		if (!FILE.isFile()) return out;
		try {
			readInto(FILE, out);
			// files from before snapshots: a built design was its own blueprint, so its copy is the snapshot
			List<ShipDesign> add = new ArrayList<ShipDesign>();
			for (ShipDesign d : out) {
				if (!d.built || !d.isWorking() || snapshot(out, d.id) != null) continue;
				ShipDesign c = copy(d);
				c.snapshotOf = d.id;
				add.add(c);
			}
			out.addAll(add);
		} catch (Exception ex) {
			log.error("Could not read " + FILE, ex);
		}
		return out;
	}
	private static void readInto(File f, List<ShipDesign> out) throws Exception {
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
		NodeList ds = doc.getElementsByTagName("design");
		for (int i = 0; i < ds.getLength(); i++) {
			ShipDesign d = parse((Element) ds.item(i));
			if (d.id.length() > 0) out.add(d);
		}
	}
	/** True if the designs file is missing (nothing to lose) or reads in full. A damaged file must never be written over. */
	public static boolean intact() {
		File f = file();
		if (!f.isFile()) return true;
		try {
			readInto(f, new ArrayList<ShipDesign>());
			return true;
		} catch (Exception ex) {
			return false;
		}
	}
	/** Why a damaged file is left alone, for the player. */
	static IOException damaged(File f) {
		return new IOException("The Home Planet Station could not read " + f.getName() + " in full, so it won't write over it:\n" + f.getAbsolutePath()
				+ "\n\nThe previous version is beside it as " + f.getName() + ".bak. Put that one back, or move the damaged file aside.");
	}
	private static int num(Element e, String a) { return Integer.parseInt(e.getAttribute(a)); }
	private static int numOr(Element e, String a, int or) { try { return Integer.parseInt(e.getAttribute(a)); } catch (Exception x) { return or; } }

	/**
	 * Deletes a design from the list: its working copy and every built copy no ship needs go; a built copy whose
	 * blueprint is in {@code kept} (ships fly it, or their kept records name it) stays, retired. True if the mod changes.
	 */
	public static boolean deleteOrRetire(List<ShipDesign> designs, String id, java.util.Set<String> kept) {
		boolean modChanged = false;
		for (java.util.Iterator<ShipDesign> it = designs.iterator(); it.hasNext();) {
			ShipDesign x = it.next();
			if (!id.equals(x.id)) continue;
			if (x.built && !x.isWorking() && kept.contains(DesignExport.bpId(x))) { x.retired = true; continue; }
			if (x.built && !x.isWorking()) modChanged = true; // a built copy no ship needs leaves the mod
			it.remove();
		}
		return modChanged;
	}

	public static void save(List<ShipDesign> designs) throws IOException {
		if (!intact()) throw damaged(file()); // the list in hand may be short: writing it would lose the designs that didn't read
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append(CRLF);
		sb.append("<!-- Ships designed in Federation Home Planet's Design Ship window. -->").append(CRLF);
		sb.append("<designs>").append(CRLF);
		for (ShipDesign d : designs) sb.append(xmlOf(d));
		sb.append("</designs>").append(CRLF);
		homeplanet.core.SafeFiles.writeText(file(), sb.toString(), true);
	}

	/** One design as it's written to the file (also used to tell whether a design has changed). */
	public static String xmlOf(ShipDesign d) {
		StringBuilder sb = new StringBuilder();
			sb.append("\t<design id=\"").append(d.id).append("\" name=\"").append(CompanionMod.attr(d.name)).append("\" made=\"").append(d.made)
					.append("\" art=\"").append(CompanionMod.attr(d.art)).append("\" floor=\"").append(CompanionMod.attr(d.floor))
					.append("\" artX=\"").append(d.artX).append("\" artY=\"").append(d.artY).append("\" floorX=\"").append(d.floorX).append("\" floorY=\"").append(d.floorY)
					.append("\" ellipseW=\"").append(d.ellipseW).append("\" ellipseH=\"").append(d.ellipseH).append("\" ellipseX=\"").append(d.ellipseX).append("\" ellipseY=\"").append(d.ellipseY)
					.append("\" gibs=\"").append(d.gibs).append("\" built=\"").append(d.built).append("\" starter=\"").append(d.starter)
					.append("\" hull=\"").append(d.hull).append("\" reactor=\"").append(d.reactor).append("\" droneSlots=\"").append(d.droneSlots)
					.append("\" offX=\"").append(d.offX).append("\" offY=\"").append(d.offY).append("\" version=\"").append(d.version).append("\" artScale=\"").append(d.artScale)
					.append(d.frozenOf != null ? "\" frozenOf=\"" + d.frozenOf : "").append(d.snapshotOf != null ? "\" snapshotOf=\"" + d.snapshotOf : "").append(d.retired ? "\" retired=\"true" : "").append("\">").append(CRLF);
			for (String n : d.notAtStart) sb.append("\t\t<nostart id=\"").append(n).append("\"/>").append(CRLF);
			if (d.loadout != null) sb.append(CompanionMod.loadoutXml(d.loadout, "\t\t"));
			for (Mount m : d.mounts) sb.append("\t\t<mount x=\"").append(m.x).append("\" y=\"").append(m.y).append("\" rotate=\"").append(m.rotate)
					.append("\" mirror=\"").append(m.mirror).append("\" slide=\"").append(m.slide).append(m.artillery ? "\" artillery=\"true" : "").append("\"/>").append(CRLF);
			for (String f : d.gibFiles) sb.append("\t\t<gib file=\"").append(CompanionMod.attr(f)).append("\"/>").append(CRLF);
			for (Room r : d.rooms) sb.append("\t\t<room x=\"").append(r.x).append("\" y=\"").append(r.y).append("\" w=\"").append(r.w).append("\" h=\"").append(r.h).append("\"/>").append(CRLF);
			for (CompanionMod.Door r : d.doors) sb.append("\t\t<door x=\"").append(r.x).append("\" y=\"").append(r.y).append("\" a=\"").append(r.a).append("\" b=\"").append(r.b).append("\" v=\"").append(r.v).append("\"/>").append(CRLF);
			for (CompanionMod.Sys s : d.systems.values()) {
				sb.append("\t\t<sys id=\"").append(s.id).append("\" room=\"").append(s.room).append("\" power=\"").append(s.power).append("\"");
				if (s.weapon != null) sb.append(" weapon=\"").append(s.weapon).append("\"");
				if (s.dir != null) sb.append(" dir=\"").append(s.dir).append("\"");
				if (s.square != null) sb.append(" square=\"").append(s.square).append("\"");
				sb.append("/>").append(CRLF);
			}
			sb.append("\t</design>").append(CRLF);
				return sb.toString();
	}

	/** A new, empty design with the next free id. */
	public static ShipDesign create(List<ShipDesign> designs) {
		int n = 1;
		for (ShipDesign d : designs) {
			try { n = Math.max(n, Integer.parseInt(d.id.substring(d.id.lastIndexOf('_') + 1)) + 1); } catch (Exception e) { }
		}
		ShipDesign d = new ShipDesign();
		d.id = "DESIGN_" + n;
		d.made = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		return d;
	}
	/** What the editors change (rooms, doors, systems, art, mounts, shield, gibs), as a string for spotting edits. Not the name or blueprint. */
	public static String editKey(ShipDesign d) {
		StringBuilder sb = new StringBuilder();
		sb.append(d.art).append('|').append(d.floor).append('|').append(d.artScale).append('|').append(d.artX).append(',').append(d.artY).append(',').append(d.floorX).append(',').append(d.floorY)
				.append('|').append(d.ellipseW).append(',').append(d.ellipseH).append(',').append(d.ellipseX).append(',').append(d.ellipseY)
				.append('|').append(d.gibs).append(d.gibFiles).append('|');
		for (Mount m : d.mounts) sb.append(m.x).append(',').append(m.y).append(',').append(m.rotate).append(m.mirror).append(m.slide).append(m.artillery).append(';');
		sb.append('|');
		for (Room r : d.rooms) sb.append(r.x).append(',').append(r.y).append(',').append(r.w).append(',').append(r.h).append(';');
		sb.append('|');
		for (CompanionMod.Door r : d.doors) sb.append(r.x).append(',').append(r.y).append(',').append(r.a).append(',').append(r.b).append(',').append(r.v).append(';');
		sb.append('|');
		for (CompanionMod.Sys s : d.systems.values()) sb.append(s.id).append(',').append(s.room).append(',').append(s.power).append(',').append(s.dir).append(',').append(s.square).append(',').append(s.weapon).append(';');
		return sb.toString();
	}
	/** Puts what the editors change back from a snapshot (undo/redo), in place. */
	public static void copyEditable(ShipDesign o, ShipDesign d) {
		ShipDesign c = copy(o);
		d.art = c.art; d.floor = c.floor; d.artScale = c.artScale; d.artX = c.artX; d.artY = c.artY; d.floorX = c.floorX; d.floorY = c.floorY;
		d.ellipseW = c.ellipseW; d.ellipseH = c.ellipseH; d.ellipseX = c.ellipseX; d.ellipseY = c.ellipseY;
		d.gibs = c.gibs; d.gibFiles.clear(); d.gibFiles.addAll(c.gibFiles);
		d.mounts.clear(); d.mounts.addAll(c.mounts);
		d.rooms.clear(); d.rooms.addAll(c.rooms);
		d.doors.clear(); d.doors.addAll(c.doors);
		d.systems.clear(); d.systems.putAll(c.systems);
	}

	public static ShipDesign copy(ShipDesign o) {
		ShipDesign d = new ShipDesign();
		d.id = o.id; d.name = o.name; d.made = o.made;
		d.art = o.art; d.floor = o.floor; d.artX = o.artX; d.artY = o.artY; d.floorX = o.floorX; d.floorY = o.floorY;
		d.ellipseW = o.ellipseW; d.ellipseH = o.ellipseH; d.ellipseX = o.ellipseX; d.ellipseY = o.ellipseY;
		d.gibs = o.gibs; d.gibFiles.addAll(o.gibFiles);
		for (Mount m : o.mounts) d.mounts.add(m.copy());
		d.built = o.built; d.starter = o.starter; d.hull = o.hull; d.reactor = o.reactor; d.droneSlots = o.droneSlots;
		d.offX = o.offX; d.offY = o.offY; d.notAtStart.addAll(o.notAtStart);
		d.version = o.version; d.frozenOf = o.frozenOf; d.snapshotOf = o.snapshotOf; d.artScale = o.artScale; d.retired = o.retired;
		d.loadout = o.loadout == null ? null : CompanionMod.copy(o.loadout);
		for (Room r : o.rooms) d.rooms.add(new Room(r.x, r.y, r.w, r.h));
		for (CompanionMod.Door r : o.doors) d.doors.add(new CompanionMod.Door(r.x, r.y, r.a, r.b, r.v));
		for (CompanionMod.Sys s : o.systems.values()) {
			CompanionMod.Sys c = new CompanionMod.Sys(s.id);
			c.room = s.room; c.power = s.power; c.dir = s.dir; c.square = s.square; c.weapon = s.weapon;
			d.systems.put(c.id, c);
		}
		return d;
	}
}
