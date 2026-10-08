package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import homeplanet.core.Store;

/**
 * The fleet's clock (5.86): clock.xml, one file for what were five (sectors.txt, beacons.txt, clock.txt, work.txt and
 * stardate.txt). It holds the sectors and beacons the fleet has been seen to travel ({@code sectors}, {@code beacons}),
 * where the boarded ship's progress was last counted ({@code last.ship}, {@code last.sector}, {@code last.beacons}),
 * each boarded ship's work at her beacon ({@code work.<id>.*}) and the clock's count on the career's day 1
 * ({@code start}). The station is its only writer, so what it last wrote is kept in memory and read again only when
 * the file changes under it.
 */
public final class Clock {
	private Clock() { }

	public static final String FILE = "clock.xml";
	public static final String NOTE = "The fleet's clock: the sectors and beacons travelled, where the boarded ship was last counted, each ship's work at her beacon, and the count on day 1";
	public static File file(Vault v) { return new File(v.root, FILE); }

	private static final Map<String, Object[]> CACHE = new HashMap<String, Object[]>(); // path: lastModified, length, properties

	/**
	 * A copy of the clock's file (empty if there isn't one yet). Until a 5.x fleet's opening moves its five files into
	 * it, they are read instead (the career's day 1 is asked for before the fleet loads), and the first write takes them
	 * in. An unreadable file is the caller's to deal with: it's never written back from a failed read.
	 */
	public static synchronized Properties read(Vault v) throws IOException {
		File f = file(v);
		if (!f.exists()) { Properties old = homeplanet.convert.OldFleet.oldClock(v); return old == null ? new Properties() : old; }
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
}
