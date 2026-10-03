package homeplanet.parser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.blerf.ftl.constants.Difficulty;
import net.blerf.ftl.model.shiplayout.ShipLayout;
import net.blerf.ftl.model.shiplayout.ShipLayoutRoom;
import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.DefaultDataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.CrewType;
import net.blerf.ftl.parser.SavedGameParser.DoorState;
import net.blerf.ftl.parser.SavedGameParser.EncounterState;
import net.blerf.ftl.parser.SavedGameParser.EnvironmentState;
import net.blerf.ftl.parser.SavedGameParser.RebelFlagshipState;
import net.blerf.ftl.parser.SavedGameParser.RoomState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.StartingCrewState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.xml.CrewBlueprint;
import net.blerf.ftl.xml.ShipBlueprint;

/**
 * Commission Ship: a brand-new ship save built from a blueprint, the way FTL starts a new game.
 * The values follow a real FTL new-game save (a Kestrel A on Easy): fuel 16, scrap 0, jump drive charged,
 * weapons unpowered, doors closed at 4 HP per doors level, crew at the manned stations.
 */
public class Commission {

	/** The save format FTL 1.6 writes. */
	public static final int FILE_FORMAT = 11;
	/** Order FTL fills stations with the starting crew (the Kestrel's three went to Piloting, Engines, Weapons). */
	private static final SystemType[] STATION_ORDER = {SystemType.PILOT, SystemType.ENGINES, SystemType.WEAPONS,
			SystemType.SHIELDS, SystemType.SENSORS, SystemType.DOORS};

	/** A starting crew group: how many of which race. */
	public static class CrewGroup {
		public final String race;
		public final int amount;
		CrewGroup(String race, int amount) { this.race = race; this.amount = amount; }
	}

	/** Builds the whole save. The blueprint must be known to the station (vanilla, or the station's own). */
	public static SavedGameState build(String blueprintId, String shipName, Difficulty difficulty, Random rng) {
		ShipBlueprint bp = DataManager.get().getShip(blueprintId);
		if (bp == null) throw new IllegalArgumentException("No blueprint " + blueprintId);

		SavedGameState gs = new SavedGameState();
		gs.setFileFormat(FILE_FORMAT);
		gs.setRandomNative(false);
		gs.setDLCEnabled(true);
		gs.setPlayerShipName(shipName);
		gs.setPlayerShipBlueprintId(blueprintId);

		ShipState ship = buildShip(bp, shipName, difficulty, rng);
		gs.setPlayerShip(ship);

		gs.setEnvironment(new EnvironmentState());
		gs.setRebelFlagshipState(new RebelFlagshipState());
		SaveHelper.startJourney(gs, difficulty);
		// The opening event exactly as a new game has it: no event id, one choice
		EncounterState enc = gs.getEncounter();
		enc.setLastEventId("");
		enc.setChoiceList(new ArrayList<Integer>()); // a brand-new game has no choice recorded yet (Testy Boy)
		// Starting scrap by difficulty: Normal 10 (Testy Boy, a fresh Normal game); Easy 30 and Hard 0 per the difficulty guide
		gs.getPlayerShip().setScrapAmt(difficulty == Difficulty.EASY ? 30 : difficulty == Difficulty.HARD ? 0 : 10);

		gs.setTotalShipsDefeated(0);
		gs.setTotalBeaconsExplored(1);
		gs.setTotalScrapCollected(0);
		gs.setTotalCrewHired(ship.getCrewList().size());
		return gs;
	}

	/** The Relief Ship Type A: a Kestrel A stripped to basics, always among what a plea offers. */
	public static final String RELIEF_BASE = "PLAYER_SHIP_HARD";
	/**
	 * Builds the Relief Ship Type A: a Kestrel A with one human crew, a Burst Laser I and an Ion Blast, no missiles, no
	 * drones or augments, every system at its minimum (a shield layer, two bars of weapons for her two guns) and a
	 * reactor of 6.
	 */
	public static SavedGameState buildRelief(String shipName, Difficulty difficulty, Random rng) {
		SavedGameState gs = build(RELIEF_BASE, shipName, difficulty, rng);
		ShipState ship = gs.getPlayerShip();
		while (ship.getCrewList().size() > 1) ship.getCrewList().remove(ship.getCrewList().size() - 1);
		ship.getWeaponList().clear();
		ship.addWeapon(SaveHelper.newIdleWeapon("LASER_BURST_1"));
		ship.addWeapon(SaveHelper.newIdleWeapon("ION_1"));
		ship.getDroneList().clear();
		ship.setDronePartsAmt(0);
		ship.setMissilesAmt(0);
		ship.getAugmentIdList().clear();
		for (SystemType t : SystemType.values()) {
			SystemState st = ship.getSystem(t);
			if (st == null || st.getCapacity() <= 0) continue;
			st.setCapacity(t == SystemType.SHIELDS || t == SystemType.WEAPONS ? 2 : 1);
			if (t.isSubsystem()) st.setPower(st.getCapacity());
		}
		ship.setReservePowerCapacity(6);
		fillPower(ship);
		net.blerf.ftl.parser.SavedGameParser.ShieldsInfo sh = ship.getExtendedSystemInfo(net.blerf.ftl.parser.SavedGameParser.ShieldsInfo.class);
		SystemState shields = ship.getSystem(SystemType.SHIELDS);
		if (sh != null) sh.setShieldLayers(shields == null ? 0 : shields.getPower() / 2);
		gs.setTotalCrewHired(ship.getCrewList().size());
		return gs;
	}

	static ShipState buildShip(ShipBlueprint bp, String shipName, Difficulty difficulty, Random rng) {
		ShipState ship = new ShipState(shipName, bp, false);
		ship.refit(); // systems at their starting levels and power, rooms, doors, augments, hull, missiles, drone parts
		ship.setHostile(true);            // as FTL writes it for the player ship
		ship.setFuelAmt(16);
		ship.setScrapAmt(0);
		ship.setJumpChargeTicks(0);       // the drive charges from empty, as in a fresh game
		if (bp.getMaxPower() != null) ship.setReservePowerCapacity(bp.getMaxPower().amount);

		// Doors: closed, at full strength for the doors level and difficulty (set once the levels are known, below)
		for (DoorState d : ship.getDoorMap().values()) {
			d.setOpen(false);
			d.setWalkingThrough(false);
		}
		for (RoomState r : ship.getRoomList()) r.setOxygen(100);

		// Starting weapons (unpowered) and drones
		if (bp.getWeaponList() != null && bp.getWeaponList().getWeaponIds() != null) {
			for (ShipBlueprint.WeaponList.WeaponId w : bp.getWeaponList().getWeaponIds()) ship.addWeapon(SaveHelper.newIdleWeapon(w.name));
		}
		if (bp.getDroneList() != null && bp.getDroneList().getDroneIds() != null) {
			for (ShipBlueprint.DroneList.DroneId d : bp.getDroneList().getDroneIds()) ship.addDrone(SaveHelper.newIdleDrone(d.name));
		}

		setLevelsAndPower(ship, bp);
		SystemState doors = ship.getSystem(SystemType.DOORS);
		int hp = doorHp(doors == null ? 1 : doors.getCapacity(), difficulty);
		for (DoorState d : ship.getDoorMap().values()) { d.setCurrentMaxHealth(hp); d.setHealth(hp); d.setNominalHealth(hp); }
		Retrofit.syncStations(ship);
		addCrew(ship, bp, rng);
		SaveHelper.ensureAdvancedInfo(ship, FILE_FORMAT);
		// Artillery (the Federation cruisers): each installed artillery system keeps its own weapon's state
		SystemState art = ship.getSystem(SystemType.ARTILLERY);
		if (art != null && art.getCapacity() > 0 && ship.getExtendedSystemInfoList(net.blerf.ftl.parser.SavedGameParser.ArtilleryInfo.class).isEmpty()) {
			String block = rawBlock(bp.getId());
			Matcher m = block == null ? null : Pattern.compile("<artillery[^>]*weapon=\"([^\"]+)\"").matcher(block);
			String weapon = m != null && m.find() ? m.group(1) : null;
			net.blerf.ftl.parser.SavedGameParser.ArtilleryInfo ai = new net.blerf.ftl.parser.SavedGameParser.ArtilleryInfo();
			ai.setWeaponModule(SaveHelper.newIdleWeaponModule(weapon));
			ship.addExtendedSystemInfo(ai);
		}
		// Shields up as the game starts: one bubble per two bars, raise animation finished
		net.blerf.ftl.parser.SavedGameParser.ShieldsInfo sh = ship.getExtendedSystemInfo(net.blerf.ftl.parser.SavedGameParser.ShieldsInfo.class);
		SystemState shields = ship.getSystem(SystemType.SHIELDS);
		if (sh != null) {
			sh.setShieldLayers(shields == null ? 0 : shields.getPower() / 2);
			sh.setShieldRaiseAnimOn(true);
			sh.setShieldRaiseAnimTicks(1000);
		}
		return ship;
	}

	/**
	 * Door strength: level 1 is 4 on every difficulty (FTL's own saves, Easy and Normal); levels 2-4 follow the
	 * FTL wiki's table (Door System page): Easy 12/16/20, Normal 8/12/18, Hard 6/10/15. Your Normal saves match it.
	 */
	static int doorHp(int level, Difficulty difficulty) {
		if (level <= 1) return 4;
		int i = Math.min(level, 4) - 2;
		int[] easy = {12, 16, 20}, normal = {8, 12, 18}, hard = {6, 10, 15};
		return difficulty == Difficulty.EASY ? easy[i] : difficulty == Difficulty.HARD ? hard[i] : normal[i];
	}

	/** Main systems FTL powers at the start, in the order it fills them (the Kestrel A: shields, oxygen, medbay, then engines). */
	private static final SystemType[] POWER_ORDER = {SystemType.SHIELDS, SystemType.OXYGEN, SystemType.MEDBAY, SystemType.CLONEBAY,
			SystemType.ENGINES, SystemType.CLOAKING, SystemType.TELEPORTER, SystemType.MIND, SystemType.HACKING, SystemType.ARTILLERY};

	/**
	 * Starting levels are the blueprint's room power attributes (systems marked start="false" are absent).
	 * Power: subsystems get their full level; weapons and drones start unpowered but their reactor share is held back
	 * for the starting weapons and drones; what's left fills the other systems in FTL's order. With the Kestrel A this
	 * gives exactly what FTL's own new-game save has (reactor 8: weapons 3 held back, shields 2, oxygen 1, medbay 1, engines 1).
	 * Oxygen's first bar is given before anything, weapons included, so no new ship starts airless.
	 */
	static void setLevelsAndPower(ShipState ship, ShipBlueprint bp) {
		// the station's copies mark every system optional (that's what lets the station remove any of them), so which
		// systems a new ship starts with, and at what level, comes from the original model
		ShipBlueprint model = bp;
		if (bp.getId().endsWith(Retrofit.SUFFIX)) {
			ShipBlueprint v = DataManager.get().getShips().get(Retrofit.vanillaId(bp.getId())); // (a designed ship has no model)
			if (v != null) model = v;
		}
		for (SystemType t : SystemType.values()) {
			SystemState st = ship.getSystem(t);
			if (st == null) continue;
			ShipBlueprint.SystemList.SystemRoom[] mine = bp.getSystemList().getSystemRoom(t);
			if (mine == null || mine.length == 0) { st.setCapacity(0); st.setPower(0); st.setDeionizationTicks(0); continue; }
			ShipBlueprint.SystemList.SystemRoom[] r = model.getSystemList().getSystemRoom(t);
			boolean present = r != null && r.length > 0 && (r[0].getStart() == null || r[0].getStart());
			st.setCapacity(present ? Math.max(1, r[0].getPower()) : 0);
			st.setPower(present && t.isSubsystem() ? st.getCapacity() : 0);
			st.setDeionizationTicks(0);
		}
		fillPower(ship);
	}
	/** Powers a new ship's main systems from her reactor, as FTL does at the start (see {@link #setLevelsAndPower}). Subsystems keep their power. */
	static void fillPower(ShipState ship) {
		for (SystemType t : SystemType.values()) {
			SystemState st = ship.getSystem(t);
			if (st != null && !t.isSubsystem()) st.setPower(0);
		}
		int budget = ship.getReservePowerCapacity();
		// oxygen's first bar comes before everything else (a ship with big shields and guns could otherwise start airless)
		SystemState oxy = ship.getSystem(SystemType.OXYGEN);
		if (oxy != null && oxy.getCapacity() > 0 && budget > 0) { oxy.setPower(1); budget--; }
		for (net.blerf.ftl.parser.SavedGameParser.WeaponState w : ship.getWeaponList()) {
			net.blerf.ftl.xml.WeaponBlueprint wb = DataManager.get().getWeapon(w.getWeaponId());
			if (wb != null) budget -= wb.getPower();
		}
		for (net.blerf.ftl.parser.SavedGameParser.DroneState d : ship.getDroneList()) {
			net.blerf.ftl.xml.DroneBlueprint db = DataManager.get().getDrone(d.getDroneId());
			if (db != null) budget -= db.getPower();
		}
		for (SystemType t : POWER_ORDER) {
			SystemState st = ship.getSystem(t);
			if (st == null || st.getCapacity() == 0 || budget <= 0) continue;
			int p = Math.min(st.getCapacity() - st.getPower(), budget);
			if (t == SystemType.SHIELDS) p -= p % 2; // a shield layer takes 2 bars: an odd one would do nothing, so it goes on down the list
			if (p <= 0) continue;
			st.setPower(st.getPower() + p);
			budget -= p;
		}
	}

	/** The blueprint's starting crew, named from FTL's name lists, placed at stations first, as a new game does. */
	static void addCrew(ShipState ship, ShipBlueprint bp, Random rng) {
		List<int[]> stations = new ArrayList<int[]>();
		for (SystemType t : STATION_ORDER) {
			SystemState st = ship.getSystem(t);
			if (st == null || st.getCapacity() == 0) continue;
			ShipBlueprint.SystemList.SystemRoom[] r = bp.getSystemList().getSystemRoom(t);
			if (r == null || r.length == 0) continue;
			int room = r[0].getRoomId();
			if (room < 0 || room >= ship.getRoomList().size()) continue;
			int sq = ship.getRoomList().get(room).getStationSquare();
			if (sq >= 0) stations.add(new int[] {room, sq});
		}
		ShipLayout layout = DataManager.get().getShipLayout(ship.getShipLayoutId());
		Set<String> used = new HashSet<String>();
		int next = 0;
		for (CrewGroup g : crewOf(bp.getId())) {
			CrewType race = CrewType.findById(g.race);
			if (race == null) continue;
			for (int i = 0; i < g.amount; i++) {
				CrewState c = new CrewState();
				c.setRace(race);
				boolean male = race != CrewType.HUMAN || rng.nextBoolean();
				c.setMale(male);
				c.setName(uniqueName(male, used));
				c.setHealth(race.getMaxHealth());
				c.setPlayerControlled(true);
				c.getTeleportAnim().setPlaying(true);
				c.getTeleportAnim().setX(0);
				c.getTeleportAnim().setY(0);
				c.setSpriteTintIndeces(tints(race, rng));
				boolean placed = false;
				if (next < stations.size() && layout != null) {
					int[] s = stations.get(next++);
					ShipLayoutRoom lr = layout.getRoom(s[0]);
					int sx = layout.getOffsetX() + lr.locationX + s[1] % lr.squaresH;
					int sy = layout.getOffsetY() + lr.locationY + s[1] / lr.squaresH;
					c.setRoomId(s[0]); c.setRoomSquare(s[1]);
					c.setSavedRoomId(s[0]); c.setSavedRoomSquare(s[1]);
					c.setSpriteX(sx * SaveHelper.SQUARE_SIZE + SaveHelper.SQUARE_SIZE / 2);
					c.setSpriteY(sy * SaveHelper.SQUARE_SIZE + SaveHelper.SQUARE_SIZE / 2);
					placed = true;
				}
				if (!placed) SaveHelper.placeCrew(ship, c, false);
				ship.addCrewMember(c);
				StartingCrewState sc = new StartingCrewState();
				sc.setName(c.getName());
				sc.setRace(race);
				ship.addStartingCrewMember(sc);
			}
		}
	}

	/**
	 * Gives a new ship the crew chosen for her (the Commission preview's: names, and looks), member by member, where
	 * the races match. Her starting-crew record (FTL's end-of-game screen) takes the names too.
	 */
	public static void sameCrew(ShipState ship, List<CrewState> chosen) {
		if (chosen == null) return;
		List<CrewState> mine = SaveHelper.getOwnCrew(ship);
		for (int i = 0; i < mine.size() && i < chosen.size(); i++) {
			CrewState c = mine.get(i), from = chosen.get(i);
			if (c.getRace() != from.getRace()) continue;
			String old = c.getName();
			c.setName(from.getName());
			c.setMale(from.isMale());
			c.setSpriteTintIndeces(new ArrayList<Integer>(from.getSpriteTintIndeces()));
			for (StartingCrewState sc : ship.getStartingCrewList()) if (old.equals(sc.getName()) && sc.getRace() == c.getRace()) { sc.setName(from.getName()); break; }
		}
	}

	/** A crew volunteer of this race (a reward), named and tinted as a new game's crew are, placed nowhere yet. Null for an unknown race. */
	public static CrewState volunteer(String raceId, Random rng) {
		CrewType race = CrewType.findById(raceId);
		if (race == null) return null;
		CrewState c = new CrewState();
		c.setRace(race);
		boolean male = race != CrewType.HUMAN || rng.nextBoolean();
		c.setMale(male);
		c.setName(uniqueName(male, new HashSet<String>()));
		c.setHealth(race.getMaxHealth());
		c.setPlayerControlled(true);
		c.setSpriteTintIndeces(tints(race, rng));
		return c;
	}

	private static String uniqueName(boolean male, Set<String> used) {
		String n = null;
		for (int tries = 0; tries < 50; tries++) {
			n = DataManager.get().getCrewName(male);
			if (n != null && used.add(n)) return n;
		}
		return n == null ? "Crew" : n;
	}

	/** One random colour per tint layer of the race, as FTL gives new crew. */
	private static List<Integer> tints(CrewType race, Random rng) {
		List<Integer> out = new ArrayList<Integer>();
		CrewBlueprint cb = DataManager.get().getCrew(race.getId());
		if (cb == null || cb.getSpriteTintLayerList() == null) return out;
		for (CrewBlueprint.SpriteTintLayer layer : cb.getSpriteTintLayerList()) {
			int n = layer.tintList == null ? 0 : layer.tintList.size();
			out.add(n == 0 ? 0 : rng.nextInt(n));
		}
		return out;
	}

	// ---- starting crew from the blueprint text (the parser keeps only one crewCount line) ----

	private static final Pattern CREW = Pattern.compile("<crewCount\\s+amount\\s*=\\s*\"(\\d+)\"\\s+class\\s*=\\s*\"(\\w+)\"");

	/** The blueprint's crewCount lines, as the game would read them. */
	public static List<CrewGroup> crewOf(String blueprintId) {
		List<CrewGroup> out = new ArrayList<CrewGroup>();
		String block = rawBlock(blueprintId);
		if (block == null) return out;
		Matcher m = CREW.matcher(block);
		while (m.find()) out.add(new CrewGroup(m.group(2), Integer.parseInt(m.group(1))));
		return out;
	}

	/** The weapon a blueprint's artillery fires (its artillery line), or null if she has none. */
	public static String artilleryWeapon(String blueprintId) {
		String block = rawBlock(blueprintId);
		if (block == null) return null;
		Matcher m = Pattern.compile("<artillery[^>]*weapon=\"([^\"]+)\"").matcher(block);
		return m.find() ? m.group(1) : null;
	}

	/** The raw text of a ship blueprint: the station's own first, else the game data's last definition (later files win). */
	static String rawBlock(String id) {
		List<CompanionMod.Remodel> remodels = CompanionMod.load();
		CompanionMod.Remodel r = CompanionMod.find(remodels, id);
		if (r != null) return CompanionMod.render(r);
		for (ShipDesign d : DesignExport.built()) if (DesignExport.bpId(d).equals(id)) return DesignExport.blueprintText(d);
		for (String f : CompanionMod.FILES) {
			String b = CompanionMod.blockOf(CompanionMod.baseText(f), id);
			if (b != null) return b;
		}
		DataManager dm = DataManager.get();
		if (!(dm instanceof DefaultDataManager)) return null;
		String[] files = {"data/dlcBlueprintsOverwrite.xml", "data/dlcBlueprints.xml", "data/blueprints.xml"};
		for (String f : files) {
			String text = ((DefaultDataManager) dm).gameText(f);
			if (text == null) continue;
			Matcher m = Pattern.compile("<shipBlueprint name=\"" + Pattern.quote(id) + "\"[\\s\\S]*?</shipBlueprint>").matcher(text);
			String last = null;
			while (m.find()) last = m.group();
			if (last != null) return last;
		}
		return null;
	}

	/** Total starting crew. */
	public static int crewTotal(String blueprintId) {
		int n = 0;
		for (CrewGroup g : crewOf(blueprintId)) n += g.amount;
		return n;
	}
}
