package homeplanet.parser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.model.shiplayout.ShipLayout;
import net.blerf.ftl.model.shiplayout.ShipLayoutRoom;
import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.RoomState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.xml.ShipBlueprint;
import net.blerf.ftl.xml.SystemBlueprint;

import homeplanet.core.HistoryLog;
import homeplanet.core.SafeFiles;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * The Junkyard's derelicts for sale: three hulls nobody wanted, on the station's blank copies of FTL's ships, always
 * badly damaged and stripped of nearly everything (each of her own weapons and drones survives 1 time in 12), often odd (systems missing, added or at strange levels), now and
 * then a locked model, and rarely one rebuilt strangely (two systems' rooms swapped, a door welded shut), which
 * becomes a blueprint of her own only once she's bought. New ones come in after 15 to 45 beacons the fleet travels
 * (5 times 3 to 9, rolled each time: 30 on average).
 * Kept in the fleet's derelicts/ folder.
 */
public final class Derelicts {
	private static final Logger log = LoggerFactory.getLogger(Derelicts.class);
	private Derelicts() { }

	/** Listings at a time; beacons the fleet travels before new ones come in. */
	public static final int LISTINGS = 3;
	/** Beacons until new ones come in: 5 times 3 to 9, rolled with each set (30 on average). */
	static int interval(Random rng) { return 5 * (3 + rng.nextInt(7)); }
	/** A set from before the interval was rolled waits this long. */
	private static final int OLD_INTERVAL = 30;
	/** One listing in this many is a model the FTL profile hasn't unlocked; one in this many is oddly built. */
	public static final int LOCKED_ONE_IN = 30, ODD_ONE_IN = 15;
	/** The price: this share of her value as she is (her hull damage taken off). */
	public static final int PRICE_MIN = 25, PRICE_MAX = 75; // her missing systems don't lower it: they lower what she resells for
	/** Each of her own weapons and drones survives one time in this many, and as often there's one she didn't come with. */
	public static final int SURVIVES_ONE_IN = 12;
	/** Systems no derelict's oddity moves (their room holds more than the system: a clear square, a gun). */
	private static final Set<SystemType> FIXED = java.util.EnumSet.of(SystemType.MEDBAY, SystemType.CLONEBAY, SystemType.ARTILLERY);

	/** One derelict for sale. */
	public static final class Listing {
		public final int index;
		public final SavedGameState save;
		/** Her rebuild, as listings.txt keeps it ("swap:oxygen,hacking", "door:x,y,v"), or "" for none. */
		public final String oddity;
		public final int price;
		public final boolean locked;
		Listing(int index, SavedGameState save, String oddity, int price, boolean locked) {
			this.index = index; this.save = save; this.oddity = oddity == null ? "" : oddity; this.price = price; this.locked = locked;
		}
		/** Her rebuild in words, or "" for none. */
		public String oddityWords() { return words(oddity); }
	}

	public static File dir(Vault v) { return new File(v.root, "derelicts"); }
	private static File index(Vault v) { return new File(dir(v), "listings.txt"); }
	private static File saveFile(Vault v, int i) { return new File(dir(v), "listing-" + i + ".sav"); }

	/** Beacons until new derelicts come in. */
	public static int beaconsToNext(Vault v) {
		Properties p = read(v);
		int at = intOf(p, "rolledAt", -1);
		return at < 0 ? 0 : Math.max(0, at + intOf(p, "interval", OLD_INTERVAL) - v.beaconsSeen());
	}

	/** What's for sale now: new listings first if it's time (or there have never been any). */
	public static synchronized List<Listing> current(Vault v) {
		Properties p = read(v);
		int at = intOf(p, "rolledAt", -1);
		if (at < 0 || v.beaconsSeen() >= at + intOf(p, "interval", OLD_INTERVAL)) {
			try { roll(v, new Random()); p = read(v); }
			catch (Exception e) { log.warn("Could not bring in new derelicts: {}", e.toString()); }
		}
		List<Listing> out = new ArrayList<Listing>();
		for (int i = 0; i < LISTINGS; i++) {
			if (!"true".equals(p.getProperty(i + ".open"))) continue;
			try {
				SavedGameState gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(saveFile(v, i));
				// priced when shown, from her share (so a change to the prices reaches listings already in); a listing from before shares keeps its price
				int pct = intOf(p, i + ".percent", -1);
				int price = pct < 0 ? intOf(p, i + ".price", 0) : price(gs, pct);
				out.add(new Listing(i, gs, p.getProperty(i + ".oddity", ""), price, "true".equals(p.getProperty(i + ".locked"))));
			} catch (Exception e) {
				log.warn("Could not read derelict listing {}: {}", i, e.toString());
			}
		}
		return out;
	}

	/** New listings, now (the old ones go). */
	public static synchronized void roll(Vault v, Random rng) throws Exception {
		File d = dir(v);
		if (!d.isDirectory() && !d.mkdirs()) throw new IOException("Could not create " + d);
		Unlocks u = Unlocks.read();
		Set<String> taken = new HashSet<String>();
		for (Ship s : v.all()) if (s.name != null) taken.add(s.name);
		Properties p = new Properties();
		p.setProperty("rolledAt", Integer.toString(v.beaconsSeen()));
		p.setProperty("interval", Integer.toString(interval(rng)));
		for (int i = 0; i < LISTINGS; i++) {
			boolean wantLocked = rng.nextInt(LOCKED_ONE_IN) == 0;
			String id = pickModel(rng, u, wantLocked);
			if (id == null) continue;
			boolean locked = u.problem() == null && !u.unlockedBlueprint(id);
			String name = ShipNames.roll(id, taken, rng);
			if (name == null) name = "Derelict " + (i + 1);
			taken.add(name);
			SavedGameState gs = build(id, name, rng);
			String odd = rng.nextInt(ODD_ONE_IN) == 0 ? oddity(gs, rng) : "";
			int pct = PRICE_MIN + rng.nextInt(PRICE_MAX - PRICE_MIN + 1);
			SafeFiles.write(saveFile(v, i), SaveHelper.toBytes(gs));
			p.setProperty(i + ".open", "true");
			p.setProperty(i + ".oddity", odd);
			p.setProperty(i + ".percent", Integer.toString(pct));
			p.setProperty(i + ".locked", Boolean.toString(locked));
		}
		write(v, p);
	}

	/** Her price at this share of her value as she is (her damage off), never under 10. */
	static int price(SavedGameState gs, int percent) { return Math.max(10, Pricing.auctionBase(gs) * percent / 100); }

	/** A model for a derelict: a locked one if asked and there is one, otherwise one the profile has unlocked. */
	public static String pickModel(Random rng, Unlocks u, boolean locked) {
		List<String> open = new ArrayList<String>(), shut = new ArrayList<String>();
		for (String base : DataManager.get().getPlayerShipBaseIds(true)) {
			for (int n = 0; n < 3; n++) {
				String id = n == 0 ? base : base + "_" + (n + 1);
				if (DataManager.get().getShips().get(id) == null || CompanionMod.fileOf(id) == null) continue; // (no Type C for some)
				boolean unlocked = u == null || u.problem() != null || u.unlockedBlueprint(id);
				(unlocked ? open : shut).add(id);
			}
		}
		List<String> from = locked && !shut.isEmpty() ? shut : open.isEmpty() ? shut : open;
		return from.isEmpty() ? null : from.get(rng.nextInt(from.size()));
	}

	/**
	 * A derelict of this model: on the blank copy, no crew, hull at 15-50%, systems missing, added, re-levelled or
	 * damaged, breaches and thin air, almost never a weapon or drone, no missiles, drone parts or scrap.
	 */
	public static SavedGameState build(String blueprintId, String name, Random rng) {
		SavedGameState gs = Commission.build(blueprintId, name, net.blerf.ftl.constants.Difficulty.NORMAL, rng);
		Retrofit.apply(gs, false); // the blank copy: any system can come off, or go in where she has a room for it
		ShipState ship = gs.getPlayerShip();
		ship.getCrewList().clear();
		gs.setTotalCrewHired(0);
		ShipBlueprint bp = DataManager.get().getShip(ship.getShipBlueprintId());

		// systems: some gone, some at odd levels, some she never had, many broken
		int kept = 0;
		for (SystemType t : SystemType.values()) {
			SystemState st = ship.getSystem(t);
			if (st == null || st.getCapacity() <= 0 || t == SystemType.ARTILLERY) continue;
			if (rng.nextInt(100) < 15) { clear(st); continue; }
			if (rng.nextInt(100) < 30) st.setCapacity(1 + rng.nextInt(maxLevel(bp, t)));
			kept++;
		}
		if (kept == 0) { SystemState pil = ship.getSystem(SystemType.PILOT); if (pil != null) pil.setCapacity(1); } // never an empty shell
		if (rng.nextInt(100) < 35 && SaveHelper.systemCount(ship) < SaveHelper.SYSTEMS_MAX) { // a system she didn't come with, where she has a room for it (never past FTL's System Limit)
			List<SystemType> could = new ArrayList<SystemType>();
			for (SystemType t : SystemType.values()) {
				if (FIXED.contains(t) || bp == null || bp.getSystemList() == null || bp.getSystemList().getSystemRoom(t) == null) continue;
				SystemState st = ship.getSystem(t);
				if (st == null || st.getCapacity() <= 0) could.add(t);
			}
			if (!could.isEmpty()) {
				SystemType t = could.get(rng.nextInt(could.size()));
				SystemState st = ship.getSystem(t);
				if (st == null) { st = new SystemState(t); ship.addSystem(st); }
				st.setCapacity(1 + rng.nextInt(Math.min(2, maxLevel(bp, t))));
			}
		}
		for (SystemType t : SystemType.values()) {
			SystemState st = ship.getSystem(t);
			if (st == null || st.getCapacity() <= 0) continue;
			st.setIonizedBars(0);
			st.setDamagedBars(rng.nextInt(100) < 55 ? 1 + rng.nextInt(st.getCapacity()) : 0);
			if (t.isSubsystem()) st.setPower(st.getCapacity() - st.getDamagedBars());
		}

		// stripped: each of her own weapons and drones survives one time in 12, and one time in 12 someone left another
		// (a free slot permitting); a launcher may still have a few missiles, a drone bay or a hacking system a few parts
		List<net.blerf.ftl.parser.SavedGameParser.WeaponState> guns = new ArrayList<net.blerf.ftl.parser.SavedGameParser.WeaponState>(ship.getWeaponList());
		ship.getWeaponList().clear();
		SystemState weapons = ship.getSystem(SystemType.WEAPONS);
		if (weapons != null && weapons.getCapacity() > 0) {
			for (net.blerf.ftl.parser.SavedGameParser.WeaponState w : guns)
				if (rng.nextInt(SURVIVES_ONE_IN) == 0) ship.addWeapon(SaveHelper.newIdleWeapon(w.getWeaponId()));
			int slots = bp == null || bp.getWeaponSlots() == null ? 4 : bp.getWeaponSlots();
			if (ship.getWeaponList().size() < slots && rng.nextInt(SURVIVES_ONE_IN) == 0) {
				String extra = any(storeWeapons(), rng);
				if (extra != null) ship.addWeapon(SaveHelper.newIdleWeapon(extra));
			}
		}
		List<net.blerf.ftl.parser.SavedGameParser.DroneState> drones = new ArrayList<net.blerf.ftl.parser.SavedGameParser.DroneState>(ship.getDroneList());
		ship.getDroneList().clear();
		SystemState bay = ship.getSystem(SystemType.DRONE_CTRL);
		if (bay != null && bay.getCapacity() > 0) {
			for (net.blerf.ftl.parser.SavedGameParser.DroneState d : drones)
				if (rng.nextInt(SURVIVES_ONE_IN) == 0) ship.addDrone(SaveHelper.newIdleDrone(d.getDroneId()));
			int slots = bp == null || bp.getDroneSlots() == null ? 2 : bp.getDroneSlots();
			if (ship.getDroneList().size() < slots && rng.nextInt(SURVIVES_ONE_IN) == 0) {
				String extra = any(storeDrones(), rng);
				if (extra != null) ship.addDrone(SaveHelper.newIdleDrone(extra));
			}
		}
		List<String> augs = new ArrayList<String>(ship.getAugmentIdList());
		ship.getAugmentIdList().clear();
		if (!augs.isEmpty() && rng.nextInt(8) == 0) ship.getAugmentIdList().add(augs.get(rng.nextInt(augs.size())));
		int launchers = 0;
		for (net.blerf.ftl.parser.SavedGameParser.WeaponState w : ship.getWeaponList()) {
			net.blerf.ftl.xml.WeaponBlueprint wb = DataManager.get().getWeapons().get(w.getWeaponId());
			if (wb != null && wb.getMissiles() > 0) launchers++;
		}
		ship.setMissilesAmt(launchers > 0 && rng.nextBoolean() ? 1 + rng.nextInt(3) + (launchers - 1) : 0);
		int droneCount = ship.getDroneList().size();
		SystemState hacking = ship.getSystem(SystemType.HACKING);
		int parts = droneCount > 0 && rng.nextBoolean() ? 1 + rng.nextInt(3) + (droneCount - 1) : 0;
		if (hacking != null && hacking.getCapacity() > 0 && rng.nextBoolean()) parts += 1;
		ship.setDronePartsAmt(parts);
		gs.getCargoIdList().clear();
		ship.setScrapAmt(0);
		ship.setFuelAmt(rng.nextInt(4));
		ship.setReservePowerCapacity(Math.max(2, ship.getReservePowerCapacity() - rng.nextInt(4)));
		int max = maxHull(ship);
		ship.setHullAmt(Math.max(1, max * (15 + rng.nextInt(36)) / 100));

		SaveHelper.ensureAdvancedInfo(ship, gs.getFileFormat());
		Retrofit.syncStations(ship);
		Commission.fillPower(ship);
		for (SystemType t : SystemType.values()) { // a broken bar carries no power
			SystemState st = ship.getSystem(t);
			if (st != null && !t.isSubsystem() && st.getPower() > st.getCapacity() - st.getDamagedBars()) st.setPower(Math.max(0, st.getCapacity() - st.getDamagedBars()));
		}

		// breaches and thin air
		ShipLayout lay = DataManager.get().getShipLayout(ship.getShipLayoutId());
		for (RoomState r : ship.getRoomList()) r.setOxygen(rng.nextInt(41));
		if (lay != null && lay.getRoomCount() > 0) {
			int n = 1 + rng.nextInt(3);
			for (int k = 0; k < n; k++) {
				ShipLayoutRoom r = lay.getRoom(rng.nextInt(lay.getRoomCount()));
				ship.setBreach(r.locationX + rng.nextInt(Math.max(1, r.squaresH)), r.locationY + rng.nextInt(Math.max(1, r.squaresV)), 100);
			}
		}
		return gs;
	}
	/** Weapons and drones FTL's stores sell (a price and a rarity: no artillery, no Flagship guns), for the one someone left aboard. */
	private static List<String> storeWeapons() {
		List<String> out = new ArrayList<String>();
		for (net.blerf.ftl.xml.WeaponBlueprint w : DataManager.get().getWeapons().values())
			if (w.getCost() > 0 && w.getRarity() > 0 && !w.getId().startsWith("ARTILLERY")) out.add(w.getId());
		return out;
	}
	private static List<String> storeDrones() {
		List<String> out = new ArrayList<String>();
		for (net.blerf.ftl.xml.DroneBlueprint d : DataManager.get().getDrones().values()) if (d.getCost() > 0 && d.getRarity() > 0) out.add(d.getId());
		return out;
	}
	private static String any(List<String> l, Random rng) { return l.isEmpty() ? null : l.get(rng.nextInt(l.size())); }
	private static void clear(SystemState st) {
		st.setCapacity(0); st.setPower(0); st.setDamagedBars(0); st.setIonizedBars(0);
	}
	/** The highest level this system can have on her: her room's limit, else FTL's. */
	private static int maxLevel(ShipBlueprint bp, SystemType t) {
		ShipBlueprint.SystemList.SystemRoom[] r = bp == null || bp.getSystemList() == null ? null : bp.getSystemList().getSystemRoom(t);
		if (r != null && r.length > 0 && r[0].getMaxPower() != null) return Math.max(1, r[0].getMaxPower());
		SystemBlueprint s = DataManager.get().getSystem(t.getId());
		return s == null || s.getMaxPower() <= 0 ? 2 : Math.max(1, s.getMaxPower());
	}
	static int maxHull(ShipState s) {
		ShipBlueprint bp = DataManager.get().getShip(s.getShipBlueprintId());
		return bp == null || bp.getHealth() == null ? s.getHullAmt() : bp.getHealth().amount;
	}

	// ---- oddities: rebuilt strangely (her own blueprint, only once bought) ----

	/** A strange rebuild for her: two systems' rooms swapped, or else a door welded shut; "" if neither can be done. */
	public static String oddity(SavedGameState gs, Random rng) {
		ShipBlueprint bp = DataManager.get().getShip(gs.getPlayerShip().getShipBlueprintId());
		if (bp != null && bp.getSystemList() != null && rng.nextBoolean()) {
			List<SystemType> movable = new ArrayList<SystemType>();
			for (SystemType t : SystemType.values()) {
				if (FIXED.contains(t) || Retrofit.isManned(t)) continue; // a station needs its square: only unmanned systems swap
				ShipBlueprint.SystemList.SystemRoom[] r = bp.getSystemList().getSystemRoom(t);
				if (r != null && r.length > 0 && r[0].getRoomId() >= 0) movable.add(t);
			}
			if (movable.size() >= 2) {
				SystemType a = movable.remove(rng.nextInt(movable.size())), b = movable.get(rng.nextInt(movable.size()));
				return "swap:" + a.getId() + "," + b.getId();
			}
		}
		ShipLayout lay = DataManager.get().getShipLayout(gs.getPlayerShip().getShipLayoutId());
		List<CompanionMod.Door> doors = CompanionMod.doorsOf(lay);
		List<CompanionMod.Door> weldable = new ArrayList<CompanionMod.Door>();
		for (CompanionMod.Door d : doors) {
			if (d.a < 0 || d.b < 0) continue; // an airlock stays
			List<CompanionMod.Door> without = new ArrayList<CompanionMod.Door>(doors);
			without.remove(d);
			if (connected(lay.getRoomCount(), without)) weldable.add(d);
		}
		if (weldable.isEmpty()) return "";
		CompanionMod.Door d = weldable.get(rng.nextInt(weldable.size()));
		return "door:" + d.x + "," + d.y + "," + d.v;
	}
	/** Can every room still be reached from every other through these doors? */
	static boolean connected(int rooms, List<CompanionMod.Door> doors) {
		if (rooms <= 1) return true;
		Map<Integer, List<Integer>> next = new HashMap<Integer, List<Integer>>();
		for (CompanionMod.Door d : doors) {
			if (d.a < 0 || d.b < 0) continue;
			if (!next.containsKey(d.a)) next.put(d.a, new ArrayList<Integer>());
			if (!next.containsKey(d.b)) next.put(d.b, new ArrayList<Integer>());
			next.get(d.a).add(d.b);
			next.get(d.b).add(d.a);
		}
		Set<Integer> seen = new HashSet<Integer>();
		List<Integer> todo = new ArrayList<Integer>();
		todo.add(0);
		while (!todo.isEmpty()) {
			int r = todo.remove(todo.size() - 1);
			if (!seen.add(r)) continue;
			if (next.containsKey(r)) todo.addAll(next.get(r));
		}
		return seen.size() == rooms;
	}
	static String words(String oddity) {
		if (oddity == null || oddity.isEmpty()) return "";
		if (oddity.startsWith("swap:")) {
			String[] s = oddity.substring(5).split(",");
			// the rooms are swapped whether or not the systems are installed: say so of the rooms
			return "Rebuilt strangely: the rooms built for her " + homeplanet.model.Items.systemTitle(s[0]) + " and " + homeplanet.model.Items.systemTitle(s[1])
					+ " have been swapped, so each installs where the other would";
		}
		if (oddity.startsWith("door:")) return "Rebuilt strangely: one of her doors has been welded shut";
		return "";
	}
	/** Her rebuild as a remodel of her model (not yet on file). */
	static CompanionMod.Remodel remodel(SavedGameState gs, String oddity, List<CompanionMod.Remodel> all) {
		String vanilla = Retrofit.vanillaId(gs.getPlayerShip().getShipBlueprintId());
		CompanionMod.Remodel r = CompanionMod.create(vanilla, gs.getPlayerShipName(), all);
		ShipBlueprint bp = DataManager.get().getShip(vanilla + Retrofit.SUFFIX);
		if (oddity.startsWith("swap:")) {
			String[] s = oddity.substring(5).split(",");
			SystemType a = SystemType.findById(s[0]), b = SystemType.findById(s[1]);
			ShipBlueprint.SystemList.SystemRoom ra = bp.getSystemList().getSystemRoom(a)[0], rb = bp.getSystemList().getSystemRoom(b)[0];
			CompanionMod.Sys sa = new CompanionMod.Sys(a.getId()), sb = new CompanionMod.Sys(b.getId());
			sa.room = rb.getRoomId(); sa.power = Math.max(1, ra.getPower());
			sb.room = ra.getRoomId(); sb.power = Math.max(1, rb.getPower());
			r.systems.put(sa.id, sa);
			r.systems.put(sb.id, sb);
		} else if (oddity.startsWith("door:")) {
			String[] s = oddity.substring(5).split(",");
			int x = Integer.parseInt(s[0]), y = Integer.parseInt(s[1]), v = Integer.parseInt(s[2]);
			r.doors = new ArrayList<CompanionMod.Door>();
			for (CompanionMod.Door d : CompanionMod.doorsOf(DataManager.get().getShipLayout(gs.getPlayerShip().getShipLayoutId())))
				if (!(d.x == x && d.y == y && d.v == v)) r.doors.add(d);
		}
		return r;
	}

	// ---- buying ----

	/**
	 * Buys a listing: the price from the Cargo Hold, her rebuild written as a blueprint of her own (if she has one),
	 * and she goes into the Junkyard, set out at The Home Planet Station. Returns her.
	 */
	public static synchronized Ship buy(Vault v, Listing l) throws IOException {
		Properties p = read(v);
		if (!"true".equals(p.getProperty(l.index + ".open"))) throw new IOException("She has already been sold");
		if (v.storageScrap() < l.price) throw new IOException("The Cargo Hold holds " + v.storageScrap() + " scrap; she costs " + l.price);
		File remodels = v.remodelsFile();
		byte[] remodelsBefore = remodels.isFile() ? SafeFiles.read(remodels) : null;
		SavedGameState gs = l.save;
		if (!l.oddity.isEmpty()) {
			if (!CompanionMod.intact()) throw new IOException("The station's remodels file can't be read in full, so her blueprint can't be written");
			List<CompanionMod.Remodel> all = CompanionMod.load();
			CompanionMod.Remodel r = remodel(gs, l.oddity, all);
			all.add(r);
			CompanionMod.save(all);
			CompanionMod.register(all);
			homeplanet.core.Slipstream.writeMod(); // her blueprint goes into the mod's file, to be sent to FTL
			Retrofit.switchTo(gs, r.id);
			Retrofit.syncStations(gs.getPlayerShip());
		}
		byte[] storageBefore = null;
		try {
			storageBefore = v.payFromStorage(l.price);
			Ship s = v.adoptJunked(gs);
			v.setOut(s, gs, "Bought as a derelict from the Junkyard");
			p.setProperty(l.index + ".open", "false");
			write(v, p);
			saveFile(v, l.index).delete();
			HistoryLog.entry("BUY", gs.getPlayerShipName() + " (" + gs.getPlayerShip().getShipBlueprintId() + "), a derelict, for " + l.price + " scrap from the Cargo Hold"
					+ (l.oddity.isEmpty() ? "" : "; " + words(l.oddity)));
			return s;
		} catch (IOException e) {
			if (storageBefore != null) try { v.refundStorage(storageBefore); } catch (IOException again) { e.addSuppressed(again); }
			if (!l.oddity.isEmpty()) {
				try {
					if (remodelsBefore == null) remodels.delete(); else SafeFiles.write(remodels, remodelsBefore);
					CompanionMod.register(CompanionMod.load());
				} catch (IOException again) { e.addSuppressed(again); }
			}
			throw e;
		}
	}

	// ---- listings.txt ----

	private static Properties read(Vault v) {
		Properties p = new Properties();
		File f = index(v);
		if (!f.isFile()) return p;
		try { p.load(new java.io.StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); }
		return p;
	}
	private static void write(Vault v, Properties p) throws IOException {
		java.io.StringWriter w = new java.io.StringWriter();
		p.store(w, "The Junkyard's derelicts for sale");
		SafeFiles.writeText(index(v), w.toString(), false);
	}
	private static int intOf(Properties p, String key, int dflt) {
		try { return Integer.parseInt(p.getProperty(key, "").trim()); } catch (NumberFormatException e) { return dflt; }
	}
}
