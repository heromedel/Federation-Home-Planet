package homeplanet.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * The Third Fleet Commander (heromedel's chain): a word 7 to 21 days into a fleet about rebuilding ships from the
 * Junkyard (Interested, Not interested: neither ends it); the first derelict bought from the Junkyard brings a working
 * part she's missing, to the stored systems; the first part installed after that, if none has been bought from the
 * Junkyard yet, brings a word about the Junkyard's parts. Each letter goes once. What the fleet has done is kept in the
 * vault's events; with the inbox on the letters come by the inbox (Transmissions), with it off as pop-ups at the Space Dock.
 */
public final class ThirdFleet {
	private static final Logger log = LoggerFactory.getLogger(ThirdFleet.class);
	private ThirdFleet() {}

	/** The letters: the first word, its two answers, the part (two versions, by the reply), and the parts word. */
	public static final String HELLO = "fleet3:hello", NO = "fleet3:no", YES = "fleet3:yes", PART = "fleet3:part", PART_NO = "fleet3:part:no", PARTS = "fleet3:parts";
	/** The first word comes this many days into the fleet (from when the station first looks), at random. */
	public static final int DUE_MIN = 7, DUE_MAX = 21;
	static final String E_DUE = "fleet3-due", E_REPLY = "fleet3-reply", E_DERELICT = "fleet3-derelict", E_PART = "fleet3-part",
			E_INSTALLED = "fleet3-installed", E_PARTS_BOUGHT = "fleet3-parts-bought", E_SENT = "fleet3-sent:";
	/** The systems a ship can't fly without come first when he picks her part, then the rest. */
	private static final SystemType[] FIRST = {SystemType.PILOT, SystemType.ENGINES, SystemType.OXYGEN};
	private static final SystemType[] THEN = {SystemType.SHIELDS, SystemType.WEAPONS, SystemType.MEDBAY, SystemType.DOORS, SystemType.SENSORS,
			SystemType.DRONE_CTRL, SystemType.TELEPORTER, SystemType.CLOAKING, SystemType.BATTERY, SystemType.HACKING, SystemType.MIND};

	// ---- what the fleet has done (called where it happens) ----

	/** A derelict bought from the Junkyard (only a purchase counts): the first one is his project ship. */
	public static void derelictBought(Vault v, Ship s) { if (v != null && s != null) v.recordEvent(E_DERELICT, s.id); }
	/** A part bought from the Junkyard: the parts word is then never needed. */
	public static void partBought(Vault v) { if (v != null) v.recordEvent(E_PARTS_BOUGHT, Integer.toString(v.beaconsSeen())); }
	/** A stored part installed on a ship (at the Cargo Bay's Save): counts once his part has been sent. */
	public static void partInstalled(Vault v) { if (v != null && (sent(v, PART) || sent(v, PART_NO))) v.recordEvent(E_INSTALLED, Integer.toString(v.beaconsSeen())); }
	/** The reply to the first word: 0 Not interested, 1 Interested. */
	public static void replied(Vault v, int option) { if (v != null) v.recordEvent(E_REPLY, option == 0 ? "no" : "yes"); }
	public static boolean isHello(String key) { return HELLO.equals(key); }

	// ---- the letters ----

	public static boolean sent(Vault v, String key) { return v.event(E_SENT + key) != null; }
	public static void markSent(Vault v, String key) { v.recordEvent(E_SENT + key, Integer.toString(v.beaconsSeen())); }

	/** The letters due now, in order (the first word is skipped once a derelict has been bought: he goes straight to her part). */
	public static List<String> due(Vault v) {
		List<String> out = new ArrayList<String>();
		if (v == null) return out;
		int now = v.beaconsSeen();
		if (v.event(E_DUE) == null) v.recordEvent(E_DUE, Integer.toString(now + DUE_MIN + new Random().nextInt(DUE_MAX - DUE_MIN + 1)));
		int due;
		try { due = Integer.parseInt(v.event(E_DUE)); } catch (NumberFormatException e) { due = now; }
		boolean bought = v.event(E_DERELICT) != null;
		boolean partSent = sent(v, PART) || sent(v, PART_NO);
		if (!bought && !sent(v, HELLO) && now >= due && shipClass(v) != null) out.add(HELLO);
		if (bought && !partSent) out.add("no".equals(v.event(E_REPLY)) ? PART_NO : PART);
		if (partSent && v.event(E_INSTALLED) != null && v.event(E_PARTS_BOUGHT) == null && !sent(v, PARTS)) out.add(PARTS);
		return out;
	}

	/** What fills a letter's {name}: the ship's class for the first word, the part for the part letters, else nothing. */
	public static String fill(Vault v, String key) throws IOException {
		if (HELLO.equals(key)) return shipClass(v);
		if (PART.equals(key) || PART_NO.equals(key)) return part(v);
		return "";
	}

	/** The class the first word names: the boarded ship's, else a random docked ship's; null with neither (it waits). */
	static String shipClass(Vault v) {
		Ship s = v.boarded();
		if (s == null && !v.docked().isEmpty()) s = v.docked().get(new Random().nextInt(v.docked().size()));
		if (s == null) return null;
		try {
			ShipState st = v.readCopy(s).save.getPlayerShip();
			ShipBlueprint bp = DataManager.get().getShip(st.getShipBlueprintId());
			if (bp != null && bp.getShipClass() != null && bp.getShipClass().getTextValue() != null && !bp.getShipClass().getTextValue().isEmpty())
				return bp.getShipClass().getTextValue();
		} catch (Exception e) { log.debug("Could not read {}'s class: {}", s.name, e.toString()); }
		return "ship";
	}

	/**
	 * His part for the project ship, chosen and put in the stored systems once ("an Engines system"): a system her rooms
	 * allow that she doesn't have, the ones she can't fly without first; one she has room for if she lacks nothing; the
	 * Engines if she can't be read. In working order (a gift, not salvage).
	 */
	static synchronized String part(Vault v) throws IOException {
		String given = v.event(E_PART);
		if (given != null) return given;
		SystemType pick = null;
		ShipState her = null;
		String id = v.event(E_DERELICT);
		List<Ship> all = new ArrayList<Ship>(v.junked()); // she's bought into the Junkyard; she may have been rebuilt since
		all.addAll(v.fleet());
		for (Ship s : all) if (s.id.equals(id) && her == null) { try { her = v.readCopy(s).save.getPlayerShip(); } catch (Exception e) { her = null; } }
		if (her != null) {
			pick = missing(her, FIRST);
			if (pick == null) pick = missing(her, THEN);
			if (pick == null) for (SystemType t : FIRST) if (room(her, t)) { pick = t; break; }
		}
		if (pick == null) pick = SystemType.ENGINES;
		Vault.Transaction tx = v.begin();
		homeplanet.vault.StoredSystems.add(tx, v, java.util.Collections.singletonList(homeplanet.vault.StoredSystems.line(pick.getId(), 1, 0)));
		tx.commit();
		String title = homeplanet.model.Items.systemTitle(pick.getId());
		String words = homeplanet.model.Words.a(title) + " system";
		v.recordEvent(E_PART, words);
		homeplanet.core.HistoryLog.entry("GIFT", "The Third Fleet Commander sent " + words + " for the project ship, to the stored systems", null,
				homeplanet.core.Event.of("GIFT").put("from", "Third Fleet Commander").put("system", pick.getId()).put("title", title).put("to", "stored_systems"));
		return words;
	}
	private static SystemType missing(ShipState her, SystemType[] order) {
		for (SystemType t : order) {
			if (!room(her, t)) continue;
			if (t == SystemType.MEDBAY && her.getSystem(SystemType.CLONEBAY) != null && her.getSystem(SystemType.CLONEBAY).getCapacity() > 0) continue; // the Clone Bay holds that room
			SystemState s = her.getSystem(t);
			if (s == null || s.getCapacity() <= 0) return t;
		}
		return null;
	}
	private static boolean room(ShipState her, SystemType t) {
		ShipBlueprint bp = DataManager.get().getShip(her.getShipBlueprintId());
		return bp != null && bp.getSystemList() != null && bp.getSystemList().getSystemRoom(t) != null && bp.getSystemList().getSystemRoom(t).length > 0;
	}
}
