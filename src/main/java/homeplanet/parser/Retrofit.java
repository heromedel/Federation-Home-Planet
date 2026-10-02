package homeplanet.parser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.xml.ShipBlueprint;

/**
 * Retrofit: moving a ship onto the "blank" copy of its model (Any System Removal)
 * (same ship, named like the original plus _HP, with no system marked as standard equipment), so FTL
 * won't rebuild systems the station removes.
 */
public class Retrofit {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Retrofit.class);
	public static final String SUFFIX = "_HP";
	public static final String MOD_NAME = CompanionMod.TITLE;
	private static final Pattern BLANK_ID = Pattern.compile("[A-Z0-9_]+" + SUFFIX);

	public static boolean isRetrofitted(ShipState ship) {
		return ship != null && ship.getShipBlueprintId() != null && ship.getShipBlueprintId().endsWith(SUFFIX);
	}
	/** The original model behind one of the station's blueprint ids (plain _HP copy or _R<n>_HP remodel). */
	public static String vanillaId(String id) {
		if (id == null || !id.endsWith(SUFFIX)) return id;
		String v = id.substring(0, id.length() - SUFFIX.length());
		return v.replaceFirst("_R\\d+$", "");
	}
	public static boolean isRemodeled(ShipState ship) {
		return ship != null && CompanionMod.isRemodelId(ship.getShipBlueprintId());
	}
	/** True if the game data (ftl.dat as patched) has the blueprint this ship uses; always true for vanilla models. */
	public static boolean inGame(ShipState ship) {
		return ship != null && (!isRetrofitted(ship) || CompanionMod.inGameData(ship.getShipBlueprintId()));
	}
	private static ShipBlueprint ship(String id) {
		try { return DataManager.get().getShip(id); } catch (Exception e) { return null; }
	}
	/** True if the game data (ftl.dat, as patched by Slipstream) has the blank copy of this ship's model. */
	public static boolean blankAvailable(ShipState ship) {
		return ship != null && CompanionMod.inGameData(vanillaId(ship.getShipBlueprintId()) + SUFFIX);
	}

	/** The standard systems of the ship's original model that aren't installed (empty = Undo Retrofit is safe). */
	public static List<String> missingStandard(ShipState ship) {
		List<String> missing = new ArrayList<String>();
		ShipBlueprint bp = ship(vanillaId(ship.getShipBlueprintId()));
		if (bp == null || bp.getSystemList() == null) return missing;
		for (SystemType t : SystemType.values()) {
			ShipBlueprint.SystemList.SystemRoom[] r = bp.getSystemList().getSystemRoom(t);
			if (r == null || r.length == 0) continue;
			if (r[0].getStart() != null && !r[0].getStart().booleanValue()) continue; // optional on the original
			if (installed(ship, t)) continue;
			// Medbay and Clone Bay share a room: either one fills it
			if (t == SystemType.MEDBAY && installed(ship, SystemType.CLONEBAY)) continue;
			if (t == SystemType.CLONEBAY && installed(ship, SystemType.MEDBAY)) continue;
			missing.add(t.getId());
		}
		return missing;
	}
	private static boolean installed(ShipState ship, SystemType t) {
		SystemState st = ship.getSystem(t);
		return st != null && st.getCapacity() > 0;
	}

	/** Moves the ship onto the blank copy of its model (or back, if undo). */
	public static void apply(SavedGameState save, boolean undo) {
		String id = vanillaId(save.getPlayerShip().getShipBlueprintId());
		switchTo(save, undo ? id : id + SUFFIX);
	}

	/** Systems a crew member mans at a station (hacking isn't one: FTL saves its room with no station). */
	public static boolean isManned(SystemType t) {
		return t == SystemType.PILOT || t == SystemType.DOORS || t == SystemType.SENSORS || t == SystemType.SHIELDS
				|| t == SystemType.ENGINES || t == SystemType.WEAPONS;
	}

	/**
	 * Sets every room's station the way FTL saves it: a room with an installed manned system gets that system's
	 * station (the blueprint's slot, or FTL's usual spot when the blueprint doesn't say), every other room gets none.
	 * FTL reads stations from the save, not the blueprint, so this must follow any change of rooms or systems.
	 */
	public static void syncStations(ShipState ship) {
		ShipBlueprint bp = ship(ship.getShipBlueprintId());
		if (bp == null || bp.getSystemList() == null) return;
		java.util.List<net.blerf.ftl.parser.SavedGameParser.RoomState> rooms = ship.getRoomList();
		java.util.Map<Integer, int[]> keep = new java.util.HashMap<Integer, int[]>();
		for (SystemType t : SystemType.values()) {
			if (!isManned(t) || !installed(ship, t)) continue;
			ShipBlueprint.SystemList.SystemRoom[] r = bp.getSystemList().getSystemRoom(t);
			if (r == null || r.length == 0 || r[0].getRoomId() < 0 || r[0].getRoomId() >= rooms.size()) continue;
			int room = r[0].getRoomId();
			int sq; String dir;
			if (r[0].getSlot() != null) { sq = r[0].getSlot().getNumber(); dir = r[0].getSlot().getDirection(); }
			else { // FTL's defaults, as its own saves show them for blueprints without a slot
				sq = t == SystemType.ENGINES ? 2 : t == SystemType.WEAPONS ? 1 : 0;
				dir = t == SystemType.PILOT ? "right" : t == SystemType.ENGINES ? "down" : t == SystemType.SHIELDS ? "left" : "up";
			}
			if (dir == null) dir = "down";
			keep.put(room, new int[] {sq, "down".equals(dir) ? 0 : "right".equals(dir) ? 1 : "up".equals(dir) ? 2 : 3});
		}
		net.blerf.ftl.parser.SavedGameParser.StationDirection[] dirs = {net.blerf.ftl.parser.SavedGameParser.StationDirection.DOWN,
				net.blerf.ftl.parser.SavedGameParser.StationDirection.RIGHT, net.blerf.ftl.parser.SavedGameParser.StationDirection.UP,
				net.blerf.ftl.parser.SavedGameParser.StationDirection.LEFT};
		for (int i = 0; i < rooms.size(); i++) {
			int[] k = keep.get(i);
			rooms.get(i).setStationSquare(k == null ? -1 : k[0]);
			rooms.get(i).setStationDirection(k == null ? net.blerf.ftl.parser.SavedGameParser.StationDirection.NONE : dirs[k[1]]);
		}
	}

	/**
	 * Puts the ship on another blueprint of her model: the blueprint name, her layout name, and her door list
	 * (the save keeps one entry per door of the layout; doors on the same wall keep their state, new ones start closed).
	 */
	public static void switchTo(SavedGameState save, String bpId) { switchTo(save, bpId, 0, 0); }
	/** As above; dx, dy: how far (in squares) the old rooms moved in the new layout's coordinates (an overhaul can shift them). */
	public static void switchTo(SavedGameState save, String bpId, int dx, int dy) {
		ShipState ship = save.getPlayerShip();
		ship.setShipBlueprintId(bpId);
		save.setPlayerShipBlueprintId(bpId);
		ShipBlueprint bp = ship(bpId);
		if (bp == null) return;
		String layoutId = bp.getLayoutId();
		net.blerf.ftl.model.shiplayout.ShipLayout oldLay = null;
		try { oldLay = DataManager.get().getShipLayout(ship.getShipLayoutId()); } catch (Exception e) { log.debug("Retrofit: her old layout {} could not be read: {}", ship.getShipLayoutId(), e.toString()); }
		ship.setShipLayoutId(layoutId);
		net.blerf.ftl.model.shiplayout.ShipLayout lay = DataManager.get().getShipLayout(layoutId);
		if (lay == null) return;
		syncRooms(ship, oldLay, lay, dx, dy);
		java.util.Map<net.blerf.ftl.model.shiplayout.DoorCoordinate, net.blerf.ftl.parser.SavedGameParser.DoorState> old =
				new java.util.LinkedHashMap<net.blerf.ftl.model.shiplayout.DoorCoordinate, net.blerf.ftl.parser.SavedGameParser.DoorState>(ship.getDoorMap());
		net.blerf.ftl.parser.SavedGameParser.DoorState template = null;
		for (net.blerf.ftl.parser.SavedGameParser.DoorState d : old.values()) if (d != null) { template = d; break; }
		ship.getDoorMap().clear();
		for (net.blerf.ftl.model.shiplayout.DoorCoordinate c : lay.getDoorMap().keySet()) {
			net.blerf.ftl.parser.SavedGameParser.DoorState d = old.get(c);
			if (d == null) {
				d = template == null ? new net.blerf.ftl.parser.SavedGameParser.DoorState() : new net.blerf.ftl.parser.SavedGameParser.DoorState(template);
				d.setOpen(false);
				d.setWalkingThrough(false);
			}
			ship.setDoor(c.x, c.y, c.v, d);
		}
		syncStations(ship);
	}

	/**
	 * After her rooms changed (an overhaul): one room state per new room, keeping the air of the old room it overlaps
	 * most; hull breaches outside the new rooms go; crew standing outside the rooms are moved to a free square (crew keep
	 * their square when it's still inside a room). Nothing changes when the rooms are the same.
	 */
	static void syncRooms(ShipState ship, net.blerf.ftl.model.shiplayout.ShipLayout oldLay, net.blerf.ftl.model.shiplayout.ShipLayout lay, int dx, int dy) {
		if (dx == 0 && dy == 0 && oldLay != null && sameRooms(oldLay, lay) && ship.getRoomList().size() == lay.getRoomCount()) return;
		// everything positioned in the old coordinates moves with the rooms
		int sqs = SaveHelper.SQUARE_SIZE;
		for (net.blerf.ftl.parser.SavedGameParser.CrewState c : ship.getCrewList()) { c.setSpriteX(c.getSpriteX() + dx * sqs); c.setSpriteY(c.getSpriteY() + dy * sqs); }
		if (dx != 0 || dy != 0) {
			java.util.Map<net.blerf.ftl.model.XYPair, Integer> moved = new java.util.LinkedHashMap<net.blerf.ftl.model.XYPair, Integer>();
			for (java.util.Map.Entry<net.blerf.ftl.model.XYPair, Integer> e : ship.getBreachMap().entrySet())
				moved.put(new net.blerf.ftl.model.XYPair(e.getKey().x + dx, e.getKey().y + dy), e.getValue());
			ship.getBreachMap().clear();
			ship.getBreachMap().putAll(moved);
		}
		List<net.blerf.ftl.parser.SavedGameParser.RoomState> old = new ArrayList<net.blerf.ftl.parser.SavedGameParser.RoomState>(ship.getRoomList());
		ship.getRoomList().clear();
		for (int i = 0; i < lay.getRoomCount(); i++) {
			net.blerf.ftl.model.shiplayout.ShipLayoutRoom r = lay.getRoom(i);
			net.blerf.ftl.parser.SavedGameParser.RoomState rs = new net.blerf.ftl.parser.SavedGameParser.RoomState();
			for (int k = 0; k < r.squaresH * r.squaresV; k++) rs.addSquare(new net.blerf.ftl.parser.SavedGameParser.SquareState(0, 0, -1));
			int best = -1, bestOverlap = 0;
			if (oldLay != null) {
				for (int j = 0; j < oldLay.getRoomCount() && j < old.size(); j++) {
					net.blerf.ftl.model.shiplayout.ShipLayoutRoom o = oldLay.getRoom(j);
					int ox = o.locationX + dx, oy = o.locationY + dy;
					int ov = Math.max(0, Math.min(r.locationX + r.squaresH, ox + o.squaresH) - Math.max(r.locationX, ox))
							* Math.max(0, Math.min(r.locationY + r.squaresV, oy + o.squaresV) - Math.max(r.locationY, oy));
					if (ov > bestOverlap) { bestOverlap = ov; best = j; }
				}
			}
			rs.setOxygen(best >= 0 ? old.get(best).getOxygen() : 100);
			ship.addRoom(rs);
		}
		// hull breaches: only inside rooms
		for (java.util.Iterator<net.blerf.ftl.model.XYPair> it = ship.getBreachMap().keySet().iterator(); it.hasNext();) {
			net.blerf.ftl.model.XYPair p = it.next();
			if (roomAt(lay, p.x, p.y) < 0) it.remove();
		}
		// crew: on a square of a room, one each where possible
		java.util.Set<String> taken = new java.util.HashSet<String>();
		List<net.blerf.ftl.parser.SavedGameParser.CrewState> lost = new ArrayList<net.blerf.ftl.parser.SavedGameParser.CrewState>();
		int sq = SaveHelper.SQUARE_SIZE;
		for (net.blerf.ftl.parser.SavedGameParser.CrewState c : ship.getCrewList()) {
			int tx = Math.floorDiv(c.getSpriteX(), sq), ty = Math.floorDiv(c.getSpriteY(), sq);
			int room = roomAt(lay, tx, ty);
			if (room < 0 || !taken.add(tx + "," + ty)) { lost.add(c); continue; }
			place(c, lay, room, tx, ty);
		}
		for (net.blerf.ftl.parser.SavedGameParser.CrewState c : lost) {
			boolean done = false;
			for (int i = 0; i < lay.getRoomCount() && !done; i++) {
				net.blerf.ftl.model.shiplayout.ShipLayoutRoom r = lay.getRoom(i);
				for (int y = r.locationY; y < r.locationY + r.squaresV && !done; y++)
					for (int x = r.locationX; x < r.locationX + r.squaresH && !done; x++)
						if (taken.add(x + "," + y)) { place(c, lay, i, x, y); done = true; }
			}
			if (!done) place(c, lay, 0, lay.getRoom(0).locationX, lay.getRoom(0).locationY); // more crew than squares: share
		}
	}
	private static void place(net.blerf.ftl.parser.SavedGameParser.CrewState c, net.blerf.ftl.model.shiplayout.ShipLayout lay, int room, int tx, int ty) {
		net.blerf.ftl.model.shiplayout.ShipLayoutRoom r = lay.getRoom(room);
		int square = (ty - r.locationY) * r.squaresH + (tx - r.locationX);
		c.setRoomId(room);
		c.setRoomSquare(square);
		c.setSpriteX(tx * SaveHelper.SQUARE_SIZE + SaveHelper.SQUARE_SIZE / 2);
		c.setSpriteY(ty * SaveHelper.SQUARE_SIZE + SaveHelper.SQUARE_SIZE / 2);
		if (c.getSavedRoomId() >= lay.getRoomCount()) { c.setSavedRoomId(room); c.setSavedRoomSquare(square); }
	}
	static int roomAt(net.blerf.ftl.model.shiplayout.ShipLayout lay, int x, int y) {
		for (int i = 0; i < lay.getRoomCount(); i++) {
			net.blerf.ftl.model.shiplayout.ShipLayoutRoom r = lay.getRoom(i);
			if (x >= r.locationX && x < r.locationX + r.squaresH && y >= r.locationY && y < r.locationY + r.squaresV) return i;
		}
		return -1;
	}
	static boolean sameRooms(net.blerf.ftl.model.shiplayout.ShipLayout a, net.blerf.ftl.model.shiplayout.ShipLayout b) {
		if (a.getRoomCount() != b.getRoomCount()) return false;
		for (int i = 0; i < a.getRoomCount(); i++) {
			net.blerf.ftl.model.shiplayout.ShipLayoutRoom x = a.getRoom(i), y = b.getRoom(i);
			if (x.locationX != y.locationX || x.locationY != y.locationY || x.squaresH != y.squaresH || x.squaresV != y.squaresV) return false;
		}
		return true;
	}

	/**
	 * Every one of our blueprint ids (…_HP) named in a save file, read from the raw bytes so it works on saves the
	 * parser can't open. Null if the file can't be read at all (locked, missing): callers must treat that as "unknown",
	 * never as "uses nothing".
	 */
	public static List<String> blueprintIds(File save) {
		List<String> out = new ArrayList<String>();
		try {
			String raw = new String(java.nio.file.Files.readAllBytes(save.toPath()), "ISO-8859-1");
			Matcher m = BLANK_ID.matcher(raw);
			while (m.find()) {
				String id = m.group();
				if (id.startsWith("PLAYER_SHIP_") && !out.contains(id)) out.add(id);
			}
		} catch (Exception e) {
			return null;
		}
		return out;
	}

	/** Our blueprint names this save refers to that the game data doesn't have (FTL can't load the ship without the mod). */
	public static List<String> missingBlueprints(File save) {
		List<String> out = new ArrayList<String>();
		List<String> ids = blueprintIds(save);
		if (ids != null) for (String id : ids) if (!CompanionMod.inGameData(id)) out.add(id);
		return out;
	}
}
