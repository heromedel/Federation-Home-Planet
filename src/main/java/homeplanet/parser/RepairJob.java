package homeplanet.parser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.model.shiplayout.ShipLayout;
import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.RoomState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.core.Event;
import homeplanet.core.HistoryLog;
import homeplanet.core.Store;
import homeplanet.vault.Borrowed;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * The repair job (a reply chain): after the fleet has made three unflyable ships fly, a collector in the Civilian
 * Sector asks for her wrecked Stealth, the Nightjar, to be restored. Accepted, she's delivered to the Junkyard; whole
 * again, the Cargo Bay returns her for the repair cost and a bonus. Not returned 200 beacons after she came, her
 * owner demands her back; refused, the Federation Office of Salvage and Claims takes her value (and her, if docked).
 * Kept in the fleet's repair-job.txt.
 */
public final class RepairJob {
	private static final Logger log = LoggerFactory.getLogger(RepairJob.class);
	private RepairJob() {}

	public static final String BLUEPRINT = "PLAYER_SHIP_STEALTH";
	public static final String NAME = "Nightjar";
	/** Her Zoltan shield: an augment, as FTL gives it to its own Zoltan cruisers. */
	public static final String SHIELD = "ENERGY_SHIELD";
	static final String NOTE = "Delivered to the Junkyard for restoration, on behalf of a collector in the Civilian Sector";
	/** Ships made to fly again before the offer comes. */
	public static final int RESTORATIONS = 3;
	/** Beacons after her delivery before her owner demands her back. */
	public static final int OVERDUE = 200;
	public static final int EXTRA_MIN = 200, EXTRA_MAX = 500;
	/** Her owner, as the letters sign and her Borrowed mark names her. */
	public static final String OWNER = "A Collector, Civilian Sector";

	public static final String OFFER = "chain:repair-job", ACCEPTED = OFFER + ":accepted", READY = OFFER + ":ready", PAID = OFFER + ":paid", LATE = OFFER + ":returned-late",
			OVERDUE_LETTER = OFFER + ":overdue", DEFIED = OFFER + ":defied", SEIZED = OFFER + ":seized", COLLECTED = OFFER + ":collected", HIDDEN = OFFER + ":hidden";
	/** Every letter's key starts with this. */
	public static boolean isJob(String key) { return key != null && key.startsWith(OFFER); }

	// ---- the state ----

	private static File file(Vault v) { return Store.file(v.root, "repair-job"); } // repair-job.xml (5.86)
	static Properties read(Vault v) { return Store.read(file(v)); }
	private static void write(Vault v, Properties p) throws IOException {
		Store.write(file(v), p, "The repair job (the Nightjar): Federation Home Planet rewrites this file");
	}
	private static Set<String> ids(Properties p, String k) {
		Set<String> out = new LinkedHashSet<String>();
		for (String s : p.getProperty(k, "").split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
		return out;
	}
	private static void setIds(Properties p, String k, Set<String> ids) {
		StringBuilder sb = new StringBuilder();
		for (String s : ids) sb.append(sb.length() == 0 ? "" : ",").append(s);
		p.setProperty(k, sb.toString());
	}

	/** Can she fly: hull left, and Engines and Piloting installed with a bar that isn't broken? */
	public static boolean flyable(ShipState s) {
		if (s == null || s.getHullAmt() <= 0) return false;
		for (SystemType t : new SystemType[] {SystemType.ENGINES, SystemType.PILOT}) {
			SystemState st = s.getSystem(t);
			if (st == null || st.getCapacity() <= 0 || st.getDamagedBars() >= st.getCapacity()) return false;
		}
		return true;
	}
	/** Whole again, as her owner asks: flyable, every hull point back and no breach. */
	public static boolean whole(ShipState s) {
		return flyable(s) && Pricing.missingHull(s) == 0 && s.getBreachMap().isEmpty();
	}
	/** What the Dry Dock would charge to make her whole: hull points, broken bars and breaches. */
	public static int repairCost(ShipState s) {
		return Pricing.HULL_REPAIR * Pricing.missingHull(s) + Pricing.SYSTEM_REPAIR * Pricing.brokenBars(s) + Pricing.BREACH_REPAIR * s.getBreachMap().size();
	}

	/** The Nightjar, in the fleet (docked, boarded or in the Junkyard), or null. */
	public static Ship ship(Vault v) {
		String id = read(v).getProperty("ship");
		return id == null ? null : v.byId(id);
	}

	// ---- what's due (Transmissions asks each time it looks) ----

	/**
	 * Notes which ships can fly now, does what's due (the seizure, her collection), and returns the letters now due, in
	 * order: the offer, the demand, the notice of recovery, the foreman's. Transmissions sends each once.
	 */
	public static synchronized List<String> due(Vault v, Set<String> sent) {
		List<String> out = new ArrayList<String>();
		Properties p = read(v);
		boolean changed = noteFlyable(v, p);
		int now = v.beaconsSeen();
		try {
			if (!sent.contains(OFFER) && ids(p, "restored").size() >= RESTORATIONS) {
				if (p.getProperty("extra") == null) {
					p.setProperty("extra", Integer.toString(EXTRA_MIN + new Random().nextInt(EXTRA_MAX - EXTRA_MIN + 1)));
					changed = true;
				}
				out.add(OFFER);
			}
			String stage = p.getProperty("stage", "");
			int at = Store.num(p, "deliveredAt", -1);
			if (at >= 0 && stage.isEmpty() && now >= at + OVERDUE && !sent.contains(OVERDUE_LETTER)) {
				p.setProperty("late", "true"); // her owner has had to ask: the bonus is gone
				changed = true;
				out.add(OVERDUE_LETTER);
			}
			if ("defied".equals(stage) && now >= Store.num(p, "seizeAt", Integer.MAX_VALUE) && !sent.contains(SEIZED)) {
				seize(v, p);
				changed = false; // seize() wrote it
				out.add(SEIZED);
			}
			Ship s = ship(v);
			// whole again, before her owner had to ask: The Home Planet Station says so, and offers to send her home
			if (stage.isEmpty() && !"true".equals(p.getProperty("late")) && !sent.contains(READY) && !sent.contains(OVERDUE_LETTER) && s != null
					&& (s.state == Ship.State.DOCKED || s.state == Ship.State.BOARDED) && s.save() != null && whole(s.save().getPlayerShip())) out.add(READY);
			if ("true".equals(p.getProperty("collectLater")) && s != null && s.state == Ship.State.DOCKED && !sent.contains(COLLECTED)) {
				v.remove(s, null, Vault.Fate.SEIZED);
				p.setProperty("collectLater", "false");
				HistoryLog.entry("SEIZED", s.name + " (" + s.id + "): collected by the Federation Office of Salvage and Claims", null, Vault.shipEvent("SEIZED", s).put("what", "collected").put("by", "Federation Office of Salvage and Claims"));
				changed = true;
				out.add(COLLECTED);
			}
			if ("true".equals(p.getProperty("escaped")) && s != null && s.state != Ship.State.JUNKED && !sent.contains(HIDDEN)) out.add(HIDDEN);
			if (changed) write(v, p);
		} catch (IOException e) {
			log.warn("The repair job couldn't go on (tried again next time): {}", e.toString());
		}
		return out;
	}
	/** Ships seen unable to fly, and those seen flying again after it (her id once each). True if anything new was noted. */
	private static boolean noteFlyable(Vault v, Properties p) {
		Set<String> down = ids(p, "unflyable"), restored = ids(p, "restored");
		if (restored.size() >= RESTORATIONS) return false; // counted enough: the offer comes once
		boolean changed = false;
		String nightjar = p.getProperty("ship", "");
		List<Ship> all = new ArrayList<Ship>(v.fleet());
		all.addAll(v.junked());
		for (Ship s : all) {
			if (s.id.equals(nightjar)) continue;
			SavedGameState gs = s.save();
			if (gs == null) continue;
			boolean flies = flyable(gs.getPlayerShip());
			if (!flies && down.add(s.id)) changed = true;
			else if (flies && down.contains(s.id) && restored.add(s.id)) changed = true;
		}
		if (changed) { setIds(p, "unflyable", down); setIds(p, "restored", restored); }
		return changed;
	}

	/** The {placeholders} the job's letters use, from the state: {class}, {extra}, {pay}, {waiting}, {value}, {taken}, {paidline}. */
	public static String fill(Vault v, String s) {
		if (!s.contains("{")) return s;
		Properties p = read(v);
		String cls = "Stealth Cruiser";
		try {
			ShipBlueprint bp = DataManager.get().getShip(BLUEPRINT);
			if (bp != null && bp.getShipClass() != null && bp.getShipClass().getTextValue() != null) cls = bp.getShipClass().getTextValue();
		} catch (Exception e) { }
		int paid = Store.num(p, "paid", 0);
		int waiting = Store.num(p, "cost", 0) + ("true".equals(p.getProperty("late")) ? 0 : Store.num(p, "extra", EXTRA_MIN));
		return s.replace("{waiting}", Integer.toString(waiting)).replace("{class}", cls).replace("{extra}", p.getProperty("extra", Integer.toString(EXTRA_MIN)))
				.replace("{pay}", Integer.toString(paid)).replace("{value}", p.getProperty("value", "0")).replace("{taken}", p.getProperty("taken", "nothing"))
				.replace("{paidline}", paid > 0 ? "Your payment for the work is enclosed: " + paid + " scrap. Nothing more." : homeplanet.model.Words.cap(homeplanet.model.Words.she()) + " came back unfinished, and I do not pay for unfinished work.");
	}
	/** Her name for the letters, and the job's fills (for a letter sent outside the chain). */
	public static Map<String, String> fills(Vault v) {
		Map<String, String> m = new java.util.LinkedHashMap<String, String>();
		m.put("name", NAME);
		for (String k : new String[] {"class", "extra", "pay", "waiting", "value", "taken", "paidline"}) m.put(k, fill(v, "{" + k + "}"));
		return m;
	}

	// ---- the replies ----

	/**
	 * A reply to one of the job's letters, before its answer is scheduled: the offer taken or turned down, the demand met
	 * (she goes back now: refused, with the reason, if she can't) or defied (the claims office comes in 7 to 14 beacons).
	 */
	public static synchronized void replied(Vault v, String key, int option) throws IOException {
		Properties p = read(v);
		if (OFFER.equals(key)) {
			p.setProperty("answer", option == 0 ? "accepted" : "refused");
			write(v, p);
		} else if (READY.equals(key)) {
			if (option == 0) {
				Ship s = ship(v);
				String why = whyNot(v, s, true);
				if (why != null) throw new IOException(why);
				giveBack(v, s, p, late(v), true);
			}
			// Not yet: she stays; the Cargo Bay's Return button sends her when you're aboard her
		} else if (OVERDUE_LETTER.equals(key)) {
			if (option == 0) {
				Ship s = ship(v);
				String why = whyNot(v, s, false);
				if (why != null) throw new IOException(why);
				giveBack(v, s, p, true, false); // her owner's answer follows by itself (the reply's letter)
			} else {
				p.setProperty("stage", "defied");
				p.setProperty("seizeAt", Integer.toString(v.beaconsSeen() + 7 + new Random().nextInt(8)));
				write(v, p);
				HistoryLog.entry("REPAIR JOB", "The " + NAME + " is kept: " + homeplanet.model.Words.her() + " owner's attorneys will come for " + homeplanet.model.Words.her() + " value", null, Event.of("REPAIR_JOB").put("stage", "defied").put("ship_name", NAME));
			}
		}
	}

	// ---- delivery, return ----

	/** The wreck as the letter describes her: a Stealth Cruiser on her blank copy, with a teleporter and a Zoltan shield. */
	public static SavedGameState build(Random rng) {
		SavedGameState gs = Commission.build(BLUEPRINT, NAME, net.blerf.ftl.constants.Difficulty.NORMAL, rng);
		Retrofit.apply(gs, false); // onto the blank copy: the teleporter has a room there
		ShipState ship = gs.getPlayerShip();
		ship.getCrewList().clear();
		gs.setTotalCrewHired(0);

		level(ship, SystemType.TELEPORTER, 1, 1);
		level(ship, SystemType.ENGINES, 2, 2); // the brother's doing: she can't fly
		level(ship, SystemType.PILOT, 1, 1);
		SystemState cloak = ship.getSystem(SystemType.CLOAKING);
		if (cloak != null && cloak.getCapacity() > 0) cloak.setDamagedBars(1);
		SystemState weapons = ship.getSystem(SystemType.WEAPONS);
		if (weapons != null && weapons.getCapacity() > 1) weapons.setDamagedBars(1);

		ship.getAugmentIdList().clear();
		ship.getAugmentIdList().add(SHIELD);
		ship.getDroneList().clear();
		ship.setDronePartsAmt(0);
		ship.setMissilesAmt(0);
		ship.setScrapAmt(0);
		ship.setFuelAmt(2);
		ship.setHullAmt(Math.max(1, (ship.getHullAmt() + Pricing.missingHull(ship)) / 4));

		SaveHelper.ensureAdvancedInfo(ship, gs.getFileFormat());
		Retrofit.syncStations(ship);
		Commission.fillPower(ship);

		// breaches in the airlock and the teleporter's room, and the air thin everywhere
		ShipLayout lay = DataManager.get().getShipLayout(ship.getShipLayoutId());
		ShipBlueprint bp = DataManager.get().getShip(ship.getShipBlueprintId());
		for (RoomState r : ship.getRoomList()) r.setOxygen(25);
		Derelict.breach(ship, lay, Derelict.airlock(lay));
		ShipBlueprint.SystemList.SystemRoom[] tr = bp == null ? null : bp.getSystemList().getSystemRoom(SystemType.TELEPORTER);
		if (tr != null && tr.length > 0) Derelict.breach(ship, lay, tr[0].getRoomId());
		return gs;
	}
	private static void level(ShipState ship, SystemType type, int capacity, int damaged) {
		SystemState st = ship.getSystem(type);
		if (st == null) {
			st = new SystemState(type);
			ship.addSystem(st);
		}
		st.setCapacity(capacity);
		st.setPower(0);
		st.setDamagedBars(damaged);
		st.setIonizedBars(0);
	}

	/** The offer accepted: she's built and filed in the Junkyard (once), her repair cost and value assessed as she arrives. */
	public static synchronized Ship deliver(Vault v) throws Exception {
		Properties p = read(v);
		Ship here = p.getProperty("ship") == null ? null : v.byId(p.getProperty("ship"));
		if (here != null) return here;
		SavedGameState gs = build(new Random());
		Ship s = v.adoptJunked(gs);
		v.setOut(s, gs, NOTE);
		Borrowed.mark(v, s.id, OWNER, "repair-job");
		p.setProperty("ship", s.id);
		p.setProperty("deliveredAt", Integer.toString(v.beaconsSeen()));
		p.setProperty("cost", Integer.toString(repairCost(gs.getPlayerShip())));
		p.setProperty("value", Integer.toString(Pricing.saleValue(gs)));
		write(v, p);
		HistoryLog.entry("REPAIR JOB", "The " + NAME + " delivered to the Junkyard (" + s.id + ")", null, Vault.shipEvent("REPAIR_JOB", s).put("stage", "delivered").put("to", "junkyard").put("cost", p.getProperty("cost")).put("value", p.getProperty("value")));
		return s;
	}

	/**
	 * Said when the offer is taken and FTL's data doesn't have her blueprint yet: the job works without Slipstream (she's
	 * repaired and returned at The Home Planet Station), only flying her in FTL needs it. Null when it's there.
	 */
	public static String patchNote() {
		if (CompanionMod.inGameData(BLUEPRINT + Retrofit.SUFFIX)) return null;
		return "The " + NAME + " is built on a blueprint FTL doesn't have yet.\n" + homeplanet.model.Words.cap(homeplanet.model.Words.she()) + " can be repaired and returned at The Home Planet Station as " + homeplanet.model.Words.she() + " is.\n"
				+ "To fly " + homeplanet.model.Words.herObj() + " in FTL, The Home Planet Station must first send the " + CompanionMod.TITLE + " to FTL via Slipstream (Settings > Mods > Patch mods).";
	}

	/**
	 * Why she can't go home by a reply now, or null if she can: she must be in the fleet; aboard her, FTL closed and her
	 * at a station; and, to be paid as agreed, whole (a demand takes her back as she is, from the Junkyard too).
	 */
	public static String whyNot(Vault v, Ship s, boolean mustBeWhole) {
		if (s == null) return "The " + NAME + " is no longer in your fleet, so " + homeplanet.model.Words.she() + " can't be sent back.";
		if (s.state == Ship.State.BOARDED) {
			if (homeplanet.core.GameGuard.isFtlRunning()) return "FTL is running with the " + NAME + " aboard. " + homeplanet.core.GameGuard.CLOSE_FTL + " Then reply again.";
			if (!v.mayTrade(s)) return "The " + NAME + " isn't at a station. Take " + homeplanet.model.Words.herObj() + " to a beacon with a store, then reply again.";
		}
		if (mustBeWhole && s.state == Ship.State.JUNKED) return "The " + NAME + " is in the Junkyard. Salvage " + homeplanet.model.Words.herObj() + ", then reply again.";
		SavedGameState gs = s.save();
		if (mustBeWhole && (gs == null || !whole(gs.getPlayerShip())))
			return "The " + NAME + " has been damaged since. Repair " + homeplanet.model.Words.herObj() + " (every hull point, every breach, Engines and Piloting working), then reply again.";
		return null;
	}
	/** Has her owner had to ask for her (the bonus is gone)? */
	public static boolean late(Vault v) { return "true".equals(read(v).getProperty("late")); }

	/** Can she be returned: this is her, docked or boarded, whole, and the job still open? (The Cargo Bay offers it only aboard her.) */
	public static boolean ready(Vault v, Ship s) {
		if (s == null || (s.state != Ship.State.DOCKED && s.state != Ship.State.BOARDED)) return false;
		Properties p = read(v);
		if (!s.id.equals(p.getProperty("ship")) || !p.getProperty("stage", "").isEmpty()) return false;
		SavedGameState gs = s.save();
		return gs != null && whole(gs.getPlayerShip());
	}
	/** What returning her pays now: the repair cost, and the bonus unless her owner has had to ask for her. */
	public static int payment(Vault v, boolean late) {
		Properties p = read(v);
		return Store.num(p, "cost", 0) + (late ? 0 : Store.num(p, "extra", EXTRA_MIN));
	}
	/** The Cargo Bay's Return button: she leaves the fleet (RETURNED), her payment goes to the Cargo Hold, and her owner writes. */
	public static synchronized int returnHer(Vault v, Ship s) throws IOException {
		if (!ready(v, s)) throw new IOException("The " + NAME + " can't be returned yet: " + homeplanet.model.Words.she() + " must be whole (every hull point back, no breaches).");
		return giveBack(v, s, read(v), late(v), true);
	}
	/** She goes back: paid in full when whole (less the bonus if late), nothing when she isn't; her owner writes now if {@code letter}. */
	private static int giveBack(Vault v, Ship s, Properties p, boolean late, boolean letter) throws IOException {
		SavedGameState gs = s.save();
		int pay = gs != null && whole(gs.getPlayerShip()) ? payment(v, late) : 0;
		if (s.state == Ship.State.BOARDED) v.dock(); // continue.sav comes back into the vault first (refused while FTL runs)
		v.remove(s, null, Vault.Fate.RETURNED);
		p.setProperty("stage", late ? "returned-late" : "returned");
		p.setProperty("paid", Integer.toString(pay));
		write(v, p);
		if (pay > 0) {
			try { v.depositToStorage(pay); }
			catch (IOException e) { log.error("The " + NAME + "'s payment of " + pay + " scrap could not reach the Cargo Hold", e); throw new IOException("The " + NAME + " was returned, but " + homeplanet.model.Words.her() + " payment of " + pay + " scrap could not be put in the Cargo Hold: " + e.getMessage()); }
		}
		HistoryLog.entry("RETURNED", s.name + " (" + s.id + ") to her owner" + (pay > 0 ? ", for " + pay + " scrap" : ", unpaid"), null, Vault.shipEvent("RETURNED", s).put("what", "to_owner").put("paid", pay).put("late", late));
		if (letter) Transmissions.post(late ? LATE : PAID, late ? LATE : PAID, fills(v));
		return pay;
	}

	// ---- the claims office ----

	/**
	 * The Federation Office of Salvage and Claims recovers her value: from the Cargo Hold's scrap if it holds enough,
	 * else a docked ship (never the boarded one), else everything in the Cargo Hold (its crew stay: people aren't
	 * property). She goes too if docked; boarded, she's collected when next docked; in the Junkyard, she isn't found.
	 */
	private static void seize(Vault v, Properties p) throws IOException {
		int value = Store.num(p, "value", 0);
		Ship nightjar = ship(v);
		List<String> taken = new ArrayList<String>();
		if (v.storageScrap() >= value) {
			v.payFromStorage(value);
			taken.add(value + " scrap from the Cargo Hold");
		} else {
			List<Ship> docked = new ArrayList<Ship>(v.docked());
			docked.remove(nightjar);
			if (!docked.isEmpty()) {
				Ship pick = docked.get(new Random().nextInt(docked.size()));
				v.remove(pick, null, Vault.Fate.SEIZED);
				taken.add(ShipNames.the(pick.name) + ", in its place");
			} else {
				taken.add(emptyHold(v));
			}
		}
		if (nightjar != null) {
			if (nightjar.state == Ship.State.DOCKED) { v.remove(nightjar, null, Vault.Fate.SEIZED); taken.add("and the " + NAME + " " + homeplanet.model.Words.herself()); }
			else if (nightjar.state == Ship.State.BOARDED) { p.setProperty("collectLater", "true"); taken.add("and the " + NAME + " " + homeplanet.model.Words.herself() + ", as soon as " + homeplanet.model.Words.she() + " docks"); }
			else p.setProperty("escaped", "true");
		}
		p.setProperty("stage", "seized");
		p.setProperty("taken", join(taken));
		write(v, p);
		HistoryLog.entry("SEIZED", "The Federation Office of Salvage and Claims took " + join(taken), null, Event.of("SEIZED").put("what", "office").put("taken", join(taken)));
	}
	/** "a, b and c" (a part starting "and" joins as it is). */
	static String join(List<String> parts) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			String x = parts.get(i);
			if (i > 0) sb.append(x.startsWith("and ") ? ", " : i == parts.size() - 1 ? " and " : ", ");
			sb.append(x);
		}
		return sb.toString();
	}
	/** Everything in the Cargo Hold but its crew, in words. */
	private static String emptyHold(Vault v) throws IOException {
		Ship st = v.storage();
		Vault.Copy c = v.readCopy(st);
		ShipState s = c.save.getPlayerShip();
		int items = s.getWeaponList().size() + s.getDroneList().size() + s.getAugmentIdList().size() + (c.save.getCargoIdList() == null ? 0 : c.save.getCargoIdList().size());
		String words = "everything in the Cargo Hold (" + s.getScrapAmt() + " scrap, " + s.getFuelAmt() + " fuel, " + s.getMissilesAmt() + " missiles, "
				+ s.getDronePartsAmt() + " drone parts and " + items + (items == 1 ? " item" : " items") + "; its crew were left alone)";
		s.setScrapAmt(0);
		s.setFuelAmt(0);
		s.setMissilesAmt(0);
		s.setDronePartsAmt(0);
		s.getWeaponList().clear();
		s.getDroneList().clear();
		s.getAugmentIdList().clear();
		if (c.save.getCargoIdList() != null) c.save.getCargoIdList().clear();
		v.begin().put(st, c.save, c.hash).commit();
		return words;
	}

	/** The keys this chain's letters use (for tests). */
	static final List<String> KEYS = Arrays.asList(OFFER, ACCEPTED, PAID, LATE, OVERDUE_LETTER, DEFIED, SEIZED, COLLECTED, HIDDEN);
}
