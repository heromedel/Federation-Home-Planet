package homeplanet.vault;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.HistoryLog;
import homeplanet.core.SafeFiles;
import homeplanet.parser.SaveHelper;

/**
 * Augments a ship had no room for (heromedel): when FTL gives a fourth augment it asks which to throw away, and the save
 * holds all four while it asks. Away from a store, the station writes the four down; at the next save after a jump,
 * whichever is gone was the one thrown away, and her crew ship it home (a parcel the inbox delivers, Transmissions).
 * At a store nothing is noted: there one could be sold instead. Kept in the fleet's overflow.txt.
 */
public final class Overflow {
	private Overflow() { }

	/** FTL's augment slots: a fourth is the one asked about. */
	public static final int SLOTS = 3;

	/** An augment on its way home: its id, the ship's name, and its key (once per throw). */
	public static final class Parcel {
		public final String key, augment, ship;
		Parcel(String key, String augment, String ship) { this.key = key; this.augment = augment; this.ship = ship; }
	}

	private static java.io.File file(Vault v) { return new java.io.File(v.root, "overflow.txt"); }
	private static Properties read(Vault v) {
		Properties p = new Properties();
		try { if (file(v).isFile()) p.load(new StringReader(new String(SafeFiles.read(file(v)), StandardCharsets.UTF_8))); }
		catch (IOException e) { HistoryLog.entry("OVERFLOW", "overflow.txt could not be read: " + e); }
		return p;
	}
	private static void write(Vault v, Properties p) {
		try {
			StringWriter w = new StringWriter();
			p.store(w, "Augments the boarded ship had no room for: the four seen away from a store, and those shipped home (the inbox delivers them)");
			SafeFiles.writeText(file(v), w.toString(), false);
		} catch (IOException e) { HistoryLog.entry("OVERFLOW", "overflow.txt could not be written: " + e); }
	}
	/** Her augments, the ones FTL counts in its slots. */
	static List<String> augments(SavedGameState gs) {
		List<String> out = new ArrayList<String>();
		if (gs.getPlayerShip() == null) return out;
		for (String a : gs.getPlayerShip().getAugmentIdList()) if (a != null && !a.startsWith("HIDDEN")) out.add(a);
		return out;
	}

	/** A save of the boarded ship (the Vault, under its lock): notes four augments, or ships home the one gone after a jump. */
	static void note(Vault v, Ship b, SavedGameState gs) {
		Properties p = read(v);
		boolean changed = false;
		int beacons = gs.getTotalBeaconsExplored();
		List<String> now = augments(gs);
		String seen = p.getProperty("seen.augments");
		if (seen != null) {
			int at = -1;
			try { at = Integer.parseInt(p.getProperty("seen.beacons", "").trim()); } catch (NumberFormatException e) { }
			if (!b.id.equals(p.getProperty("seen.ship")) || beacons < at) { // another ship, or an earlier save put back: nothing to judge by
				clearSeen(p);
				changed = true;
			} else if (beacons > at) { // the jump: what's gone was thrown away
				List<String> left = new ArrayList<String>(now);
				int n = intOf(p, "parcels");
				for (String a : seen.split(",")) {
					if (a.isEmpty() || left.remove(a)) continue; // still aboard (each copy counted once)
					p.setProperty("parcel." + n, "shipped:" + b.id + ":" + at + ":" + n + "|" + a + "|" + gs.getPlayerShipName());
					HistoryLog.entry("OVERFLOW", gs.getPlayerShipName() + " had no room for " + a + ": her crew ship it home");
					n++;
				}
				p.setProperty("parcels", Integer.toString(n));
				clearSeen(p);
				changed = true;
			}
		}
		if (now.size() > SLOTS && !SaveHelper.isAtStation(gs)) {
			String list = String.join(",", now);
			if (!list.equals(p.getProperty("seen.augments")) || !Integer.toString(beacons).equals(p.getProperty("seen.beacons"))) {
				p.setProperty("seen.ship", b.id);
				p.setProperty("seen.beacons", Integer.toString(beacons));
				p.setProperty("seen.augments", list);
				changed = true;
			}
		}
		if (changed) write(v, p);
	}
	private static void clearSeen(Properties p) { p.remove("seen.ship"); p.remove("seen.beacons"); p.remove("seen.augments"); }
	private static int intOf(Properties p, String k) {
		try { return Integer.parseInt(p.getProperty(k, "0").trim()); } catch (NumberFormatException e) { return 0; }
	}

	/** The parcels waiting to be delivered, taken off the list (the caller delivers them, or lets them go). */
	public static List<Parcel> take(Vault v) {
		synchronized (v) {
			Properties p = read(v);
			List<Parcel> out = new ArrayList<Parcel>();
			int n = intOf(p, "parcels");
			boolean any = false;
			for (int i = 0; i < n; i++) {
				String x = p.getProperty("parcel." + i);
				if (x == null) continue;
				List<String> w = Arrays.asList(x.split("\\|", 3));
				if (w.size() == 3) out.add(new Parcel(w.get(0), w.get(1), w.get(2)));
				p.remove("parcel." + i);
				any = true;
			}
			if (any) write(v, p);
			return out;
		}
	}
}
