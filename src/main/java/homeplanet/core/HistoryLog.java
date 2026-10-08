package homeplanet.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;

import homeplanet.model.Items;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;
import homeplanet.parser.SaveHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The station's log: what the station saw and did, each entry its kind, a headline and details, written to the event
 * log (logs/events.log) with its fields. Until 5.93 it was also history.log ("yyyy-MM-dd HH:mm  KIND  headline", details
 * indented two spaces); that file stays as it was, read only by the conversion of a fleet's old logs.
 */
public class HistoryLog {
	private static final Logger log = LoggerFactory.getLogger(HistoryLog.class);

	/** Where the old station log is (no longer written: the conversion and the harness read it). */
	public static File file() {
		return Vault.isOpen() ? Vault.get().historyLog() : new File(HomePlanet.save_location, "history.log");
	}
	private static final String NL = System.getProperty("line.separator");

	/** Writes one entry: a headline plus optional detail lines. Never throws. */
	public static void entry(String kind, String headline, List<String> details) { entry(kind, headline, details, null); }
	/**
	 * As above, with the event it is (Overhaul 6.0, step 9): the entry goes to the event log as two lines, the event's
	 * fields (its headline and details among them) and its human line (the headline when the event has none). With no
	 * event given, one is made from the kind, the headline and the details; it only lacks the fields.
	 */
	public static synchronized void entry(String kind, String headline, List<String> details, Event event) {
		// the event log alone (5.93): history.log and the master log's copy are no longer written; the old files stay as they are
		if (Vault.isOpen()) {
			Event e = event != null ? event : Event.of(kind);
			if (e.get("headline") == null) e.put("headline", headline); // the old wording kept beside the fields, always
			if (e.get("detail.1") == null) e.details(details);
			if (e.human().isEmpty()) e.human(headline != null && !headline.isEmpty() ? headline : details != null && !details.isEmpty() ? String.join("; ", details) : kind.toLowerCase());
			EventLog.write(Vault.get(), Event.of(e.kind).put("log", "station").putAll(e).human(e.human()));
		}
	}
	public static void entry(String kind, String headline) {
		entry(kind, headline, null);
	}

	/** Every ship in the vault, the storage holds and the junkyard: to the debug log, not the station log (heromedel, 5.53). */
	public static void loaded(String reason) {
		List<String> lines = new ArrayList<String>();
		Vault v = Vault.get();
		for (Ship s : v.fleet()) lines.add(shipLine(s));
		for (Ship s : v.all()) if (s.isStorage()) lines.add(pad(s.file().getName()) + s.name);
		for (Ship s : v.junked()) lines.add(shipLine(s));
		if (lines.isEmpty()) lines.add("(no ships found)");
		StringBuilder sb = new StringBuilder("Loaded (").append(reason).append("):");
		for (String l : lines) sb.append(NL).append("  ").append(l);
		log.info(sb.toString());
	}

	public static String shipLine(Ship ship) {
		SavedGameState gs = ship.save();
		String file = (ship.isBoarded() ? "" : ship.state.key + "/") + ship.file().getName();
		if (gs == null) return pad(file) + ship.name + "  (unreadable: " + ship.readError() + ")";
		ShipState s = gs.getPlayerShip();
		return pad(file) + gs.getPlayerShipName() + (ship.isBoarded() ? "  [boarded]" : "")
				+ "  sector " + (gs.getSectorNumber() + 1) + ", " + gs.getTotalBeaconsExplored() + " beacons, hull "
				+ s.getHullAmt() + ", scrap " + s.getScrapAmt();
	}

	private static String pad(String s) {
		StringBuilder b = new StringBuilder(s);
		while (b.length() < 30) b.append(' ');
		return b.append("  ").toString();
	}

	/** Contents of a ship that can be traded, counted by display name. */
	public static Map<String, Integer> inventory(SavedGameState gs) {
		Map<String, Integer> m = new LinkedHashMap<String, Integer>();
		if (gs == null) return m;
		ShipState s = gs.getPlayerShip();
		add(m, "Scrap", s.getScrapAmt());
		add(m, "Fuel", s.getFuelAmt());
		add(m, "Missiles", s.getMissilesAmt());
		add(m, "Drone parts", s.getDronePartsAmt());
		for (WeaponState w : s.getWeaponList()) add(m, Items.weaponTitle(w.getWeaponId()), 1);
		for (DroneState d : s.getDroneList()) add(m, Items.droneTitle(d.getDroneId()), 1);
		for (String a : s.getAugmentIdList()) add(m, Items.augmentTitle(a), 1);
		for (String c : homeplanet.parser.SaveHelper.cargo(gs)) add(m, Items.title(c) + " (cargo)", 1); // not the augment FTL is asking about (5.52)
		for (CrewState c : SaveHelper.getOwnCrew(s)) add(m, "Crew " + c.getName(), 1);
		return m;
	}
	private static void add(Map<String, Integer> m, String k, int n) {
		Integer v = m.get(k);
		m.put(k, (v == null ? 0 : v) + n);
	}

	/** Lines like "+ Heavy Laser Mark I", "- Scrap 30" for what changed between two inventories. */
	public static List<String> changes(Map<String, Integer> before, Map<String, Integer> after) {
		List<String> out = new ArrayList<String>();
		java.util.Set<String> keys = new java.util.LinkedHashSet<String>(before.keySet());
		keys.addAll(after.keySet());
		for (String k : keys) {
			int b = before.containsKey(k) ? before.get(k) : 0;
			int a = after.containsKey(k) ? after.get(k) : 0;
			if (a == b) continue;
			int d = a - b;
			boolean count = k.equals("Scrap") || k.equals("Fuel") || k.equals("Missiles") || k.equals("Drone parts");
			String amount = (count || Math.abs(d) != 1) ? " " + Math.abs(d) : "";
			out.add((d > 0 ? "+ " : "- ") + k + amount);
		}
		return out;
	}
}
