package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import java.util.List;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.Store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The fleet's clock (5.86): clock.xml. It holds the sectors and beacons the fleet has been seen to travel ({@code sectors}, {@code beacons}),
 * where the boarded ship's progress was last counted ({@code last.ship}, {@code last.sector}, {@code last.beacons}),
 * each boarded ship's work at her beacon ({@code work.<id>.*}) and the clock's count on the career's day 1
 * ({@code start}). The station is its only writer, so what it last wrote is kept in memory and read again only when
 * the file changes under it.
 */
public final class Clock {
	private Clock() { }
	private static final Logger log = LoggerFactory.getLogger(Clock.class);

	public static final String FILE = "clock.xml";
	public static final String NOTE = "The fleet's clock: the sectors and beacons travelled, where the boarded ship was last counted, each ship's work at her beacon, and the count on day 1";
	public static File file(Vault v) { return new File(v.root, FILE); }

	private static final Map<String, Object[]> CACHE = new HashMap<String, Object[]>(); // path: lastModified, length, properties

	/**
	 * A copy of the clock's file (empty if there isn't one yet). An unreadable file is the caller's to deal with: it's
	 * never written back from a failed read.
	 */
	public static synchronized Properties read(Vault v) throws IOException {
		File f = file(v);
		if (!f.exists()) return new Properties();
		Object[] c = CACHE.get(f.getAbsolutePath());
		if (c == null || (Long) c[0] != f.lastModified() || (Long) c[1] != f.length()) {
			Properties p = Store.load(f);
			c = new Object[] {f.lastModified(), f.length(), p};
			CACHE.put(f.getAbsolutePath(), c);
		}
		Properties out = new Properties();
		out.putAll((Properties) c[2]);
		return out;
	}
	/** A number on the clock, or the default (also when the file can't be read). */
	public static int num(Vault v, String key, int dflt) {
		try { return Store.num(read(v), key, dflt); } catch (IOException e) { return dflt; }
	}
	/** Sets these keys (a null value removes one), the rest as they are. */
	public static synchronized void set(Vault v, String... keyValues) throws IOException {
		Properties p = read(v);
		for (int i = 0; i + 1 < keyValues.length; i += 2) {
			if (keyValues[i + 1] == null) p.remove(keyValues[i]); else p.setProperty(keyValues[i], keyValues[i + 1]);
		}
		write(v, p);
	}
	/** Writes the whole clock. */
	public static synchronized void write(Vault v, Properties p) throws IOException {
		File f = file(v);
		Store.write(f, p, NOTE);
		Properties kept = new Properties();
		kept.putAll(p);
		CACHE.put(f.getAbsolutePath(), new Object[] {f.lastModified(), f.length(), kept});
	}

	/** Sectors this fleet's boarded ships have been seen to advance, in all (FTL's progress, not the station's own changes). */
	static int sectorsSeen(Vault v) { return Clock.num(v, "sectors", 0); }

	static void addSectors(Vault v, int n) {
		if (n <= 0) return;
		try { Clock.set(v, "sectors", Integer.toString(sectorsSeen(v) + n)); }
		catch (IOException e) { log.warn("Could not count the sectors travelled: {}", e.toString()); }
	}

	/** Beacons this fleet's boarded ships have been seen to explore, in all (FTL's progress, as with the sectors). */
	static int beaconsSeen(Vault v) { return Clock.num(v, "beacons", 0); }

	/** One beacon of the fleet's time passes away from FTL (a finished expedition): everything timed counts it. */
	static void countBeacon(Vault v) { countBeacon(v, "time passed"); }

	/** As above, with why (the master log's day line: rest, a job, business in the Cargo Bay). */
	static void countBeacon(Vault v, String why) { addBeacons(v, 1, why); }

	static void addBeacons(Vault v, int n, String why) {
		if (n <= 0) return;
		int was = beaconsSeen(v);
		try { Clock.set(v, "beacons", Integer.toString(was + n)); }
		catch (IOException e) { log.warn("Could not count the beacons travelled: {}", e.toString()); return; }
		for (int i = 1; i <= n; i++) Calendar.day(v, was + i, why); // each day, and why it passed
	}

	/** Where the boarded ship's progress was last counted: her sector and beacons, or null if never (she's counted from her next look). */
	static int[] lastCounted(Vault v, Ship b) {
		java.util.Properties p;
		try { p = Clock.read(v); } catch (IOException e) { log.warn("Could not read the fleet's clock: {}", e.toString()); p = new java.util.Properties(); }
		try {
			if (b.id.equals(p.getProperty("last.ship"))) return new int[] {Integer.parseInt(p.getProperty("last.sector").trim()), Integer.parseInt(p.getProperty("last.beacons").trim())};
		} catch (RuntimeException e) { }
		return null;
	}

	/** Sets where the boarded ship's progress was last counted (after the station writes her, or boards her: nothing to count). */
	static void setClock(Vault v, Ship b, int sector, int beacons) {
		try { Clock.set(v, "last.ship", b.id, "last.sector", Integer.toString(sector), "last.beacons", Integer.toString(beacons)); }
		catch (IOException e) { log.warn("Could not record the fleet's clock: {}", e.toString()); }
	}

	/**
	 * Counts the boarded ship's progress since it was last counted: the beacons and sectors she has gained, each once.
	 * FTL's saves, a dock, the station's own writes and a look at the Space Dock all call it, so none of her voyage is
	 * missed. A total gone down (a New Journey, an earlier save put back) counts nothing.
	 */
	static void countProgress(Vault v, Ship b, SavedGameState gs) {
		if (b == null || gs == null) return;
		int sector = gs.getSectorNumber(), beacons = gs.getTotalBeaconsExplored();
		int[] last = lastCounted(v, b);
		if (last != null) {
			addSectors(v, sector - last[0]);
			addBeacons(v, beacons - last[1], beacons - last[1] == 1 ? "a jump" : "a jump (" + (beacons - last[1]) + " counted together)");
			if (last[0] == sector && last[1] == beacons) return;
		}
		setClock(v, b, sector, beacons);
	}

	/** Before the station writes the boarded ship (or docks her): FTL's progress in continue.sav is counted first. */
	static void countBefore(Vault v, Ship s) {
		if (s == null || s.state != Ship.State.BOARDED || !v.continueFile().isFile()) return;
		SavedGameState gs;
		try { gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(v.continueFile()); } catch (Exception e) { return; }
		if (s.marks != null && !s.marks.isEmpty() && !ContinueWatch.sameShip(s.marks, gs)) return; // not her: the next look records her lost
		if (v.ignoring(s)) return; // not the career's ship: nothing of hers counts (5.54)
		countProgress(v, s, gs);
	}
}
