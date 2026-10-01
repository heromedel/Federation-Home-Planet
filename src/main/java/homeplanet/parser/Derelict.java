package homeplanet.parser;

import java.util.Random;

import net.blerf.ftl.model.shiplayout.ShipLayout;
import net.blerf.ftl.model.shiplayout.ShipLayoutDoor;
import net.blerf.ftl.model.shiplayout.ShipLayoutRoom;
import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.RoomState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * The Engi Restoration Collective's derelict (the One Point of Hull chain): a Slug Cruiser A, retrofitted onto the
 * station's blank copy so her systems can be anything, built as a wreck and delivered to the Junkyard with no crew.
 */
public final class Derelict {
	private Derelict() {}

	public static final String BLUEPRINT = "PLAYER_SHIP_JELLY";
	public static final String NAME = "Patience";
	static final String NOTE = "Delivered to the Junkyard by the Engi Restoration Collective";

	/** Builds her and files her in the Junkyard, set out at The Home Planet Station (so, once salvaged, she may take on crew). */
	public static Ship deliver(Vault v) throws Exception {
		SavedGameState gs = build(new Random());
		Ship s = v.adoptJunked(gs);
		v.setOut(s, gs, NOTE);
		return s;
	}

	/** The wreck as the letter describes her. */
	public static SavedGameState build(Random rng) {
		SavedGameState gs = Commission.build(BLUEPRINT, NAME, net.blerf.ftl.constants.Difficulty.NORMAL, rng);
		Retrofit.apply(gs, false); // onto the blank copy: the hacking bay has a room there
		ShipState ship = gs.getPlayerShip();
		ship.getCrewList().clear();
		gs.setTotalCrewHired(0);

		// the medbay torn out, a hacking bay put in (and broken)
		level(ship, SystemType.MEDBAY, 0, 0);
		level(ship, SystemType.HACKING, 1, 1);
		level(ship, SystemType.OXYGEN, 1, 1);
		level(ship, SystemType.PILOT, 2, 0);
		level(ship, SystemType.ENGINES, 1, 0);
		SystemState weapons = ship.getSystem(SystemType.WEAPONS);
		if (weapons != null && weapons.getCapacity() > 0) weapons.setDamagedBars(1);

		ship.getWeaponList().clear();
		ship.addWeapon(SaveHelper.newIdleWeapon("BEAM_1"));
		ship.getDroneList().clear();
		ship.setDronePartsAmt(0);
		ship.getAugmentIdList().clear();
		ship.setMissilesAmt(0);
		ship.setScrapAmt(0);
		ship.setFuelAmt(3);
		ship.setHullAmt(6);

		SaveHelper.ensureAdvancedInfo(ship, gs.getFileFormat());
		Retrofit.syncStations(ship);
		Commission.fillPower(ship);

		// breaches in the airlock and the weapons room, and the air thin everywhere
		ShipLayout lay = DataManager.get().getShipLayout(ship.getShipLayoutId());
		ShipBlueprint bp = DataManager.get().getShip(ship.getShipBlueprintId());
		for (RoomState r : ship.getRoomList()) r.setOxygen(30);
		breach(ship, lay, airlock(lay));
		ShipBlueprint.SystemList.SystemRoom[] wr = bp == null ? null : bp.getSystemList().getSystemRoom(SystemType.WEAPONS);
		if (wr != null && wr.length > 0) breach(ship, lay, wr[0].getRoomId());
		return gs;
	}

	private static void level(ShipState ship, SystemType type, int capacity, int damaged) {
		SystemState st = ship.getSystem(type);
		if (st == null) {
			if (capacity == 0) return;
			st = new SystemState(type);
			ship.addSystem(st);
		}
		st.setCapacity(capacity);
		st.setPower(type.isSubsystem() ? capacity - damaged : 0);
		st.setDamagedBars(damaged);
		st.setIonizedBars(0);
	}

	/** A room with a door to space (the first found), or -1. */
	static int airlock(ShipLayout lay) {
		for (ShipLayoutDoor d : lay.getDoorMap().values()) {
			if (d.roomIdA < 0 && d.roomIdB >= 0) return d.roomIdB;
			if (d.roomIdB < 0 && d.roomIdA >= 0) return d.roomIdA;
		}
		return -1;
	}

	/** A breach on the room's first square. */
	private static void breach(ShipState ship, ShipLayout lay, int roomId) {
		if (roomId < 0 || roomId >= lay.getRoomCount()) return;
		ShipLayoutRoom r = lay.getRoom(roomId);
		ship.setBreach(r.locationX, r.locationY, 100);
	}
}
