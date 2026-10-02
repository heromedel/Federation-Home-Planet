package homeplanet.parser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import net.blerf.ftl.constants.Difficulty;
import net.blerf.ftl.model.shiplayout.ShipLayout;
import net.blerf.ftl.model.shiplayout.ShipLayoutRoom;
import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser;
import net.blerf.ftl.parser.SavedGameParser.AnimState;
import net.blerf.ftl.parser.SavedGameParser.BatteryInfo;
import net.blerf.ftl.parser.SavedGameParser.BoarderDronePodInfo;
import net.blerf.ftl.parser.SavedGameParser.CloakingInfo;
import net.blerf.ftl.parser.SavedGameParser.ClonebayInfo;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.DronePodState;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.DroneType;
import net.blerf.ftl.parser.SavedGameParser.EmptyDronePodInfo;
import net.blerf.ftl.parser.SavedGameParser.EncounterState;
import net.blerf.ftl.parser.SavedGameParser.EnvironmentState;
import net.blerf.ftl.parser.SavedGameParser.ExtendedDroneInfo;
import net.blerf.ftl.parser.SavedGameParser.ExtendedDronePodInfo;
import net.blerf.ftl.parser.SavedGameParser.HackingDronePodInfo;
import net.blerf.ftl.parser.SavedGameParser.HackingInfo;
import net.blerf.ftl.parser.SavedGameParser.MindInfo;
import net.blerf.ftl.parser.SavedGameParser.RebelFlagshipState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShieldDronePodInfo;
import net.blerf.ftl.parser.SavedGameParser.ShieldsInfo;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.parser.SavedGameParser.WeaponModuleState;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;
import net.blerf.ftl.parser.SavedGameParser.ZigZagDronePodInfo;
import net.blerf.ftl.xml.DroneBlueprint;
import net.blerf.ftl.xml.ShipBlueprint;
import net.blerf.ftl.xml.WeaponBlueprint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Helpers that keep saved games valid when the station moves things between them.
 *
 * FTL 1.5.4+ (Advanced Edition) and 1.6.x saved games store extra state for
 * every equipped weapon (a weapon module) and drone (extended info and a drone
 * pod), plus per-system info (shields, battery, cloaking, ...). FTL Homeworld 3.1
 * predated most of that, so anything it added to a ship needs these filled in.
 *
 * Values for newly created weapon modules mirror what FTL 1.6.14 writes for an
 * idle, unpowered weapon.
 */
public final class SaveHelper {
	/** FTL's fixed limits for a player ship (the game's own numbers; blueprints set the weapon and drone slots). */
	public static final int CARGO_SLOTS = 4, AUGMENT_SLOTS = 3, CREW_MAX = 8, WEAPON_SLOTS_MAX = 4, DRONE_SLOTS_MAX = 3;
	/** One layout square, in pixels of the ship pictures. */
	public static final int SQUARE = 35;


	private static final Logger log = LoggerFactory.getLogger( SaveHelper.class );

	/** Pixel size of one ship floor square. */
	public static final int SQUARE_SIZE = 35;

	/** Blueprint used for the storage hold's save (the same one FTL Homeworld 3.1 used). */
	public static final String STORAGE_SHIP_BLUEPRINT = "PLAYER_SHIP_EASY";

	/** Saved game format written for new storage files (FTL 1.6.1+). */
	public static final int STORAGE_FILE_FORMAT = 11;


	private SaveHelper() {
	}


	private static boolean isAdvancedFormat( int fileFormat ) {
		return ( fileFormat == 7 || fileFormat == 8 || fileFormat == 9 || fileFormat == 11 );
	}


	// Weapons ---------------------------------------------------------------

	/**
	 * Returns an equipped weapon with the given id, preferring an unpowered one, or null.
	 */
	public static WeaponState findWeapon( List<WeaponState> weaponList, String weaponId ) {
		WeaponState armedMatch = null;
		for ( WeaponState w : weaponList ) {
			if ( weaponId.equals( w.getWeaponId() ) ) {
				if ( !w.isArmed() ) return w;
				if ( armedMatch == null ) armedMatch = w;
			}
		}
		return armedMatch;
	}

	/**
	 * Creates a weapon module like the one FTL writes for an idle, unpowered weapon.
	 */
	public static WeaponModuleState newIdleWeaponModule( String weaponId ) {
		WeaponModuleState weaponMod = new WeaponModuleState();

		WeaponBlueprint weaponBlueprint = DataManager.get().getWeapon( weaponId );
		if ( weaponBlueprint != null ) {
			weaponMod.setCooldownTicksGoal( Math.round( weaponBlueprint.getCooldown() * 1000 ) );
		}

		AnimState weaponAnim = new AnimState();
		weaponAnim.setPlaying( true );
		weaponAnim.setX( 0 );
		weaponAnim.setY( 0 );
		weaponMod.setWeaponAnim( weaponAnim );

		weaponMod.setChargeAnim( new AnimState() );  // Not playing, at -1000,-1000.
		return weaponMod;
	}

	/**
	 * Creates an unpowered weapon, ready to be equipped on any ship.
	 */
	public static WeaponState newIdleWeapon( String weaponId ) {
		WeaponState weapon = new WeaponState();
		weapon.setWeaponId( weaponId );
		weapon.setArmed( false );
		weapon.setCooldownTicks( 0 );
		weapon.setWeaponModule( newIdleWeaponModule( weaponId ) );
		return weapon;
	}

	/**
	 * Removes an equipped weapon from a ship, releasing its power if it was powered.
	 */
	public static void removeWeapon( ShipState shipState, WeaponState weapon ) {
		if ( weapon == null ) return;
		if ( !shipState.getWeaponList().remove( weapon ) ) return;

		if ( weapon.isArmed() ) {
			WeaponBlueprint weaponBlueprint = DataManager.get().getWeapon( weapon.getWeaponId() );
			if ( weaponBlueprint != null ) {
				releaseSystemPower( shipState, SystemType.WEAPONS, weaponBlueprint.getPower() );
			}
		}
	}


	// Drones ----------------------------------------------------------------

	/**
	 * Returns an equipped drone with the given id, preferring an unpowered one, or null.
	 */
	public static DroneState findDrone( List<DroneState> droneList, String droneId ) {
		DroneState armedMatch = null;
		for ( DroneState d : droneList ) {
			if ( droneId.equals( d.getDroneId() ) ) {
				if ( !d.isArmed() ) return d;
				if ( armedMatch == null ) armedMatch = d;
			}
		}
		return armedMatch;
	}

	private static DroneType getDroneType( String droneId ) {
		DroneBlueprint droneBlueprint = DataManager.get().getDrone( droneId );
		if ( droneBlueprint == null ) return null;
		return DroneType.findById( droneBlueprint.getType() );
	}

	/**
	 * Creates an empty drone pod of the right kind for a drone type, or null
	 * for types that don't have pods (battle and repair drones).
	 */
	public static DronePodState newIdleDronePod( DroneType droneType ) {
		if ( droneType == null ) return null;
		if ( DroneType.BATTLE.equals( droneType ) || DroneType.REPAIR.equals( droneType ) ) return null;

		ExtendedDronePodInfo podInfo;
		if ( DroneType.BOARDER.equals( droneType ) ) {
			podInfo = new BoarderDronePodInfo();
		}
		else if ( DroneType.HACKING.equals( droneType ) ) {
			podInfo = new HackingDronePodInfo();
		}
		else if ( DroneType.COMBAT.equals( droneType ) || DroneType.BEAM.equals( droneType ) || DroneType.SHIP_REPAIR.equals( droneType ) ) {
			podInfo = new ZigZagDronePodInfo();
		}
		else if ( DroneType.SHIELD.equals( droneType ) ) {
			podInfo = new ShieldDronePodInfo();
		}
		else {  // DEFENSE
			podInfo = new EmptyDronePodInfo();
		}

		DronePodState dronePod = new DronePodState();
		dronePod.setDroneType( droneType );
		dronePod.setExtendedInfo( podInfo );
		return dronePod;
	}

	/**
	 * Creates extended info for an undeployed, unpowered drone.
	 */
	public static ExtendedDroneInfo newIdleDroneInfo( String droneId ) {
		ExtendedDroneInfo droneInfo = new ExtendedDroneInfo();
		droneInfo.setDeployed( false );
		droneInfo.setArmed( false );
		droneInfo.setDronePod( newIdleDronePod( getDroneType( droneId ) ) );
		return droneInfo;
	}

	/**
	 * Creates an unpowered drone, ready to be equipped on any ship.
	 */
	public static DroneState newIdleDrone( String droneId ) {
		DroneState drone = new DroneState( droneId );
		DroneType droneType = getDroneType( droneId );
		if ( droneType != null ) drone.setHealth( droneType.getMaxHealth() );
		drone.setExtendedDroneInfo( newIdleDroneInfo( droneId ) );
		return drone;
	}

	/**
	 * Returns a copy of a drone, powered down and recalled, for another ship.
	 */
	public static DroneState copyDroneForTransfer( DroneState srcDrone ) {
		DroneState drone = new DroneState( srcDrone );
		drone.setArmed( false );
		drone.setPlayerControlled( false );
		drone.setBodyX( -1 );
		drone.setBodyY( -1 );
		drone.setBodyRoomId( -1 );
		drone.setBodyRoomSquare( -1 );

		if ( drone.getExtendedDroneInfo() != null ) {
			drone.getExtendedDroneInfo().commandeer();  // Undeploys and disarms, resets the pod.
		} else {
			drone.setExtendedDroneInfo( newIdleDroneInfo( drone.getDroneId() ) );
		}
		return drone;
	}

	/**
	 * Removes an equipped drone from a ship, releasing its power if it was powered.
	 */
	public static void removeDrone( ShipState shipState, DroneState drone ) {
		if ( drone == null ) return;
		if ( !shipState.getDroneList().remove( drone ) ) return;

		if ( drone.isArmed() ) {
			DroneBlueprint droneBlueprint = DataManager.get().getDrone( drone.getDroneId() );
			if ( droneBlueprint != null ) {
				releaseSystemPower( shipState, SystemType.DRONE_CTRL, droneBlueprint.getPower() );
			}
		}
	}


	// Power -----------------------------------------------------------------

	/**
	 * Takes power bars away from a system after something it powered was removed.
	 *
	 * Reactor bars go first, then battery bars. Zoltan bars are not stored in
	 * saved games, so whatever remains was coming from them.
	 */
	static void releaseSystemPower( ShipState shipState, SystemType systemType, int amount ) {
		SystemState system = shipState.getSystem( systemType );
		if ( system == null || amount <= 0 ) return;

		int fromReactor = Math.min( system.getPower(), amount );
		system.setPower( system.getPower() - fromReactor );
		int remaining = amount - fromReactor;

		if ( remaining > 0 && system.getBatteryPower() > 0 ) {
			int fromBattery = Math.min( system.getBatteryPower(), remaining );
			system.setBatteryPower( system.getBatteryPower() - fromBattery );

			BatteryInfo batteryInfo = shipState.getExtendedSystemInfo( BatteryInfo.class );
			if ( batteryInfo != null ) {
				batteryInfo.setUsedBattery( Math.max( 0, batteryInfo.getUsedBattery() - fromBattery ) );
			}
		}
		log.debug( "Released {} power from {} (reactor now {}, battery now {})", amount, systemType, system.getPower(), system.getBatteryPower() );
	}


	// Crew ------------------------------------------------------------------

	/**
	 * Returns true for the player's own crew aboard a ship (not enemy boarders
	 * or enemy boarding drones).
	 */
	public static boolean isOwnCrew( CrewState crew ) {
		return crew.isPlayerControlled() && !crew.isEnemyBoardingDrone();
	}

	public static List<CrewState> getOwnCrew( ShipState shipState ) {
		List<CrewState> result = new ArrayList<CrewState>();
		for ( CrewState crew : shipState.getCrewList() ) {
			if ( isOwnCrew( crew ) ) result.add( crew );
		}
		return result;
	}

	/**
	 * Returns true if this crew member currently has a body on the ship.
	 *
	 * Crew who died and are waiting on the clone bay have no room.
	 */
	public static boolean hasBody( CrewState crew ) {
		return crew.getRoomId() >= 0;
	}

	private static List<int[]> getBlockedSquares( ShipBlueprint shipBlueprint ) {
		List<int[]> result = new ArrayList<int[]>();
		if ( shipBlueprint == null || shipBlueprint.getSystemList() == null ) return result;

		// The medbay/clonebay's slot square can't be stood on.
		ShipBlueprint.SystemList.SystemRoom[] medicalRooms = new ShipBlueprint.SystemList.SystemRoom[] {
			shipBlueprint.getSystemList().getMedicalRoom(),
			shipBlueprint.getSystemList().getCloneRoom()
		};
		for ( ShipBlueprint.SystemList.SystemRoom room : medicalRooms ) {
			if ( room == null ) continue;
			int squareId = 1;  // FTL's default medbay/clonebay slot.
			if ( room.getSlot() != null ) squareId = room.getSlot().getNumber();
			if ( squareId >= 0 ) result.add( new int[] {room.getRoomId(), squareId} );
		}
		return result;
	}

	private static boolean isSquareTaken( ShipState shipState, int roomId, int squareId, List<int[]> blockedSquares ) {
		for ( int[] blocked : blockedSquares ) {
			if ( blocked[0] == roomId && blocked[1] == squareId ) return true;
		}
		for ( CrewState other : shipState.getCrewList() ) {
			if ( other.getRoomId() == roomId && other.getRoomSquare() == squareId ) return true;
		}
		for ( DroneState drone : shipState.getDroneList() ) {  // Battle/repair drone bodies.
			if ( drone.getBodyRoomId() == roomId && drone.getBodyRoomSquare() == squareId ) return true;
		}
		return false;
	}

	/**
	 * Puts a crew member on an unoccupied floor square of a ship.
	 *
	 * Their room, square, sprite position, and saved station are all set to
	 * match, since the old values referred to the other ship's layout.
	 *
	 * @param allowSharing if no square is free, reuse one (for the storage ship, which FTL never loads)
	 * @return false if there was no square to put them on
	 */
	public static boolean placeCrew( ShipState shipState, CrewState crew, boolean allowSharing ) {
		ShipLayout shipLayout = DataManager.get().getShipLayout( shipState.getShipLayoutId() );
		if ( shipLayout == null ) return false;
		ShipBlueprint shipBlueprint = DataManager.get().getShip( shipState.getShipBlueprintId() );
		List<int[]> blockedSquares = getBlockedSquares( shipBlueprint );

		int[] chosen = null;
		int[] fallback = null;
		for ( int roomId=0; roomId < shipLayout.getRoomCount() && chosen == null; roomId++ ) {
			ShipLayoutRoom layoutRoom = shipLayout.getRoom( roomId );
			for ( int s=0; s < layoutRoom.squaresH * layoutRoom.squaresV; s++ ) {
				boolean blocked = false;
				for ( int[] b : blockedSquares ) {
					if ( b[0] == roomId && b[1] == s ) blocked = true;
				}
				if ( blocked ) continue;
				if ( fallback == null ) fallback = new int[] {roomId, s};

				if ( !isSquareTaken( shipState, roomId, s, blockedSquares ) ) {
					chosen = new int[] {roomId, s};
					break;
				}
			}
		}
		if ( chosen == null && allowSharing ) chosen = fallback;
		if ( chosen == null ) return false;

		int roomId = chosen[0];
		int squareId = chosen[1];
		ShipLayoutRoom layoutRoom = shipLayout.getRoom( roomId );
		int squareX = shipLayout.getOffsetX() + layoutRoom.locationX + squareId % layoutRoom.squaresH;
		int squareY = shipLayout.getOffsetY() + layoutRoom.locationY + squareId / layoutRoom.squaresH;

		crew.setRoomId( roomId );
		crew.setRoomSquare( squareId );
		crew.setSpriteX( squareX * SQUARE_SIZE + SQUARE_SIZE/2 );
		crew.setSpriteY( squareY * SQUARE_SIZE + SQUARE_SIZE/2 );
		crew.setSavedRoomId( roomId );
		crew.setSavedRoomSquare( squareId );
		log.debug( "Placed crew {} on {} at room {} square {}", crew.getName(), shipState.getShipName(), roomId, squareId );
		return true;
	}


	// Whole ships and saves ---------------------------------------------------

	/**
	 * Adds any Advanced Edition info a ship needs before it can be written.
	 *
	 * Ships read from FTL 1.5.4+ saves already have all of this. It matters for
	 * newly created ships, and for weapons/drones that came from elsewhere.
	 */
	public static void ensureAdvancedInfo( ShipState shipState, int fileFormat ) {
		if ( !isAdvancedFormat( fileFormat ) ) return;

		if ( shipState.getExtendedSystemInfo( ShieldsInfo.class ) == null ) {
			shipState.addExtendedSystemInfo( new ShieldsInfo() );  // Always present, even without shields.
		}
		if ( hasCapacity( shipState, SystemType.CLONEBAY ) && shipState.getExtendedSystemInfo( ClonebayInfo.class ) == null ) {
			shipState.addExtendedSystemInfo( new ClonebayInfo() );
		}
		if ( hasCapacity( shipState, SystemType.BATTERY ) && shipState.getExtendedSystemInfo( BatteryInfo.class ) == null ) {
			shipState.addExtendedSystemInfo( new BatteryInfo() );
		}
		if ( hasCapacity( shipState, SystemType.CLOAKING ) && shipState.getExtendedSystemInfo( CloakingInfo.class ) == null ) {
			shipState.addExtendedSystemInfo( new CloakingInfo() );
		}
		if ( hasCapacity( shipState, SystemType.MIND ) && shipState.getExtendedSystemInfo( MindInfo.class ) == null ) {
			shipState.addExtendedSystemInfo( new MindInfo() );
		}
		if ( hasCapacity( shipState, SystemType.HACKING ) && shipState.getExtendedSystemInfo( HackingInfo.class ) == null ) {
			HackingInfo hackingInfo = new HackingInfo();
			hackingInfo.setDronePod( newIdleDronePod( DroneType.HACKING ) );
			shipState.addExtendedSystemInfo( hackingInfo );
		}

		for ( WeaponState weapon : shipState.getWeaponList() ) {
			if ( weapon.getWeaponModule() == null ) {
				weapon.setWeaponModule( newIdleWeaponModule( weapon.getWeaponId() ) );
			}
		}
		for ( DroneState drone : shipState.getDroneList() ) {
			if ( drone.getExtendedDroneInfo() == null ) {
				drone.setExtendedDroneInfo( newIdleDroneInfo( drone.getDroneId() ) );
			}
		}
	}

	/**
	 * Returns true if the ship has the given system installed.
	 */
	public static boolean hasSystem( ShipState shipState, SystemType systemType ) {
		return hasCapacity( shipState, systemType );
	}

	private static boolean hasCapacity( ShipState shipState, SystemType systemType ) {
		SystemState system = shipState.getSystem( systemType );
		return ( system != null && system.getCapacity() > 0 );
	}

	/**
	 * Creates a new storage save (storage.sav in the vault).
	 *
	 * Like FTL Homeworld 3.1, it holds a Kestrel with the blueprint's starting
	 * augments, missiles and drone parts, no fuel, crew, weapons or drones.
	 * FTL never loads this file; only the station does.
	 */
	public static SavedGameState createStorageSave( String shipName, boolean dlcEnabled ) {
		ShipBlueprint shipBlueprint = DataManager.get().getShip( STORAGE_SHIP_BLUEPRINT );
		if ( shipBlueprint == null ) {
			throw new IllegalStateException( "Could not find the storage ship's blueprint: "+ STORAGE_SHIP_BLUEPRINT );
		}

		SavedGameState gameState = new SavedGameState();
		gameState.setFileFormat( STORAGE_FILE_FORMAT );
		gameState.setRandomNative( false );
		gameState.setDLCEnabled( dlcEnabled );
		gameState.setDifficulty( Difficulty.EASY );
		gameState.setSectorNumber( 0 );

		ShipState shipState = new ShipState( shipName, shipBlueprint, false );
		shipState.refit();  // Systems, rooms, doors, augments, supplies.
		// an empty hold: the Kestrel's starting supplies and augments aren't stock
		shipState.setFuelAmt( 0 );
		shipState.setMissilesAmt( 0 );
		shipState.setDronePartsAmt( 0 );
		shipState.setScrapAmt( 0 );
		shipState.getAugmentIdList().clear();
		ensureAdvancedInfo( shipState, STORAGE_FILE_FORMAT );

		gameState.setPlayerShip( shipState );
		gameState.setPlayerShipName( shipName );
		gameState.setPlayerShipBlueprintId( shipBlueprint.getId() );

		gameState.setEncounter( new EncounterState() );
		gameState.setEnvironment( new EnvironmentState() );
		gameState.setRebelFlagshipState( new RebelFlagshipState() );
		return gameState;
	}


	/**
	 * Resets a save's run the way a new game starts one: sector 1, a new sector map (the player at a start beacon),
	 * the opening event, the Rebel fleet and flagship back at the start. The ship itself is left alone.
	 * Used by New Journey and by Commission Ship.
	 */
	public static void startJourney( SavedGameState gs, net.blerf.ftl.constants.Difficulty difficulty ) {
		java.util.Random rng = new java.util.Random();
		gs.setDifficulty(difficulty);
		// Map and position
		gs.getBeaconList().clear();
		gs.getQuestEventMap().clear();
		gs.getDistantQuestEventList().clear();
		gs.getSectorVisitation().clear();
		gs.setSectorNumber(0);
		gs.setCurrentBeaconId(0); // refined below once the new layout is predicted
		gs.setSectorIsHiddenCrystalWorlds(false);
		gs.setSectorHazardsVisible(false);
		gs.setSectorTreeSeed(rng.nextInt(Integer.MAX_VALUE));
		gs.setSectorLayoutSeed(rng.nextInt(Integer.MAX_VALUE));
		gs.setWaiting(false);
		gs.setWaitEventSeed(-1);
		// Whatever was happening at the current beacon
		gs.setNearbyShip(null);
		gs.setNearbyShipAI(null);
		gs.setRebelFlagshipNearby(false);
		gs.setUnknownXi(null);
		// Show FTL's opening "the Rebellion is after you" message instead of whatever event the new start beacon rolls
		// (Matches a real fresh-run save: no event id, alpha 1, the START_GAME text itself.)
		net.blerf.ftl.parser.SavedGameParser.EncounterState intro = new net.blerf.ftl.parser.SavedGameParser.EncounterState();
		String introText = null;
		if (DataManager.get() instanceof net.blerf.ftl.parser.DefaultDataManager) {
			introText = ((net.blerf.ftl.parser.DefaultDataManager) DataManager.get()).getTextById("event_START_GAME_text");
		}
		intro.setText(introText != null ? introText : "event_START_GAME_text");
		// Tie the popup to START_BEACON, an event with no choices, so FTL doesn't attach
		// the choices of whatever random event the regenerated start beacon rolled.
		intro.setLastEventId("START_BEACON");
		intro.setUnknownAlpha(1);
		gs.setEncounter(intro);
		gs.setEnvironment(new net.blerf.ftl.parser.SavedGameParser.EnvironmentState());
		gs.getProjectileList().clear();
		// Rebel fleet and flagship back to fresh-run values
		// A real fresh-sector save had offset -959; the fudge varies per sector (roughly 75-310)
		gs.setRebelFleetOffset(-959);
		gs.setRebelFleetFudge(150 + rng.nextInt(151));
		// FTL rebuilds the empty map from the layout seed. For FTL 1.6 runs its RNG is known, so predict
		// that map: the fleet fudge comes from it, and the start beacon is the next roll among the first
		// column of beacons. (Matched both real saves checked: "The Starter" and a regenerated Torus map.)
		if (gs.getFileFormat() == 11 && !gs.isRandomNative()) {
			try {
				net.blerf.ftl.parser.random.FTL_1_6_Random ftlRng = new net.blerf.ftl.parser.random.FTL_1_6_Random("FTL 1.6+");
				ftlRng.srand(gs.getSectorLayoutSeed());
				net.blerf.ftl.parser.sectormap.GeneratedSectorMap genMap =
						new net.blerf.ftl.parser.sectormap.RandomSectorMapGenerator().generateSectorMap(ftlRng, 11);
				java.util.List<net.blerf.ftl.parser.sectormap.GeneratedBeacon> beacons = genMap.getGeneratedBeaconList();
				int firstColumn = 1; // beacons are laid out top-to-bottom per column; count until y goes back up
				while (firstColumn < beacons.size() && firstColumn < 4
						&& beacons.get(firstColumn).getLocation().y > beacons.get(firstColumn - 1).getLocation().y) {
					firstColumn++;
				}
				int start = ftlRng.rand() % firstColumn;
				gs.setRebelFleetFudge(genMap.getRebelFleetFudge());
				gs.setCurrentBeaconId(start);
				System.out.println("New journey map: " + beacons.size() + " beacons, start beacon " + start);
			} catch (Exception e) {
				e.printStackTrace(); // fall back to beacon 0
			}
		}
		gs.setRebelPursuitMod(0);
		gs.setRebelFlagshipVisible(false);
		gs.setRebelFlagshipHop(0);
		gs.setRebelFlagshipMoving(false);
		gs.setRebelFlagshipRetreating(false);
		gs.setRebelFlagshipBaseTurns(0);
		gs.setRebelFlagshipState(new net.blerf.ftl.parser.SavedGameParser.RebelFlagshipState());
		// Ship: stop any jump in progress; enemy boarders don't come along.
		// (The player ship's "hostile" flag is left alone: FTL always saves it as true,
		// and setting it false makes enemy shots miss every time.)
	}

	/**
	 * The save as FTL will read it. Anything at the end of the file that the parser didn't understand (a field a
	 * newer game patch added, say) is kept and written back after the parsed part, so nothing is lost on a rewrite.
	 */
	public static byte[] toBytes( SavedGameState gameState ) throws IOException {
		ensureAdvancedInfo( gameState.getPlayerShip(), gameState.getFileFormat() );
		ByteArrayOutputStream buffer = new ByteArrayOutputStream( 16384 );
		new SavedGameParser().writeSavedGame( buffer, gameState );
		for ( net.blerf.ftl.parser.MysteryBytes m : gameState.getMysteryList() ) {
			if ( m.getBytes() != null ) buffer.write( m.getBytes() );
		}
		return buffer.toByteArray();
	}

	/**
	 * Writes a saved game without risking a half-written file: serialized in memory first, then written to a
	 * temporary file beside the target, which replaces the target at the end.
	 */
	public static void writeSavedGame( File saveFile, SavedGameState gameState ) throws IOException {
		byte[] bytes = toBytes( gameState );
		homeplanet.core.SafeFiles.write( saveFile, bytes );
		log.debug( "Wrote {} ({} bytes)", saveFile, bytes.length );
	}

	/** True if the ship is at a beacon with a store (a "station"). */
	public static boolean isAtStation(SavedGameState gs) {
		if (gs == null || gs.getBeaconList() == null) return false;
		int id = gs.getCurrentBeaconId();
		if (id < 0 || id >= gs.getBeaconList().size()) return false;
		return gs.getBeaconList().get(id).getStore() != null;
	}
	/** True unless the station rule is on and this ship isn't at a station. */
	public static boolean mayTrade(SavedGameState gs) {
		return !homeplanet.core.HomePlanet.storeRequirement() || isAtStation(gs);
	}
}
