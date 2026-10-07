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
	static final String NOTE = "The fleet's clock: the sectors and beacons travelled, where the boarded ship was last counted, each ship's work at her beacon, and the count on day 1";
	public static File file(Vault v) { return new File(v.root, FILE); }

	private static final Map<String, Object[]> CACHE = new HashMap<String, Object[]>(); // path: lastModified, length, properties

	/**
	 * A copy of the clock's file (empty if there isn't one yet). Until a 5.x fleet's opening moves its five files into
	 * it, they are read instead (the career's day 1 is asked for before the fleet loads), and the first write takes them
	 * in. An unreadable file is the caller's to deal with: it's never written back from a failed read.
	 */
	public static synchronized Properties read(Vault v) throws IOException {
		File f = file(v);
		if (!f.exists()) { Properties old = fromOld(v); return old == null ? new Properties() : old; }
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

	/** The five 5.x files a fleet's clock was, at its root. */
	static final String[] OLD = {"sectors.txt", "beacons.txt", "clock.txt", "work.txt", "stardate.txt"};
	/** The 5.x files' contents as the clock's keys (for the move on opening); null if the fleet has none of them. */
	static Properties fromOld(Vault v) throws IOException {
		boolean any = false;
		for (String n : OLD) any |= new File(v.root, n).isFile();
		if (!any) return null;
		Properties p = new Properties();
		String sectors = text(new File(v.root, "sectors.txt")), beacons = text(new File(v.root, "beacons.txt"));
		if (sectors != null) p.setProperty("sectors", sectors);
		if (beacons != null) p.setProperty("beacons", beacons);
		Properties last = Store.load(new File(v.root, "clock.txt"));
		for (String k : new String[] {"ship", "sector", "beacons"}) if (last.getProperty(k) != null) p.setProperty("last." + k, last.getProperty(k).trim());
		Properties work = Store.load(new File(v.root, "work.txt"));
		for (String k : work.stringPropertyNames()) if (k.indexOf('.') > 0) p.setProperty("work." + k, work.getProperty(k)); // the one-ship form from before 5.2x is dropped: her next look starts it again
		Properties start = Store.load(new File(v.root, "stardate.txt"));
		if (start.getProperty("start") != null) p.setProperty("start", start.getProperty("start").trim());
		return p;
	}
	private static String text(File f) throws IOException {
		if (!f.isFile()) return null;
		String s = new String(homeplanet.core.SafeFiles.read(f), java.nio.charset.StandardCharsets.UTF_8).trim();
		return s.isEmpty() ? null : s;
	}
}
