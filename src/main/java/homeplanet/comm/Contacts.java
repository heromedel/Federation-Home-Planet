package homeplanet.comm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import homeplanet.core.SafeFiles;

/**
 * The commanders this station has met: found by a search, hailed, hailed by, or written to by. Kept per career in the
 * vault's comm/contacts.txt (one line each: station|name|mode|host|port|last seen|takes messages), so someone out of
 * range can still be picked in the list and written to (the message waits in the Outbox). Forgetting one takes them
 * off the list; they come back the next time they're met.
 */
public final class Contacts {
	private Contacts() { }

	/** At most this many are remembered: the longest unseen go first. */
	static final int MAX = 100;
	/** A sighting this soon after the last one isn't written down again (a search runs every few seconds). */
	static final long QUIET = 60000;

	public static final class Entry {
		public String station, title, mode, host;
		public int port;
		public long lastSeen;
		/** Its station takes messages without a channel (as its search answer last said). */
		public boolean notes;
	}

	static File file() { return new File(Exchange.dir(), "contacts.txt"); }

	/** Everyone remembered, the most recently seen first. */
	public static synchronized List<Entry> list() {
		List<Entry> out = new ArrayList<Entry>();
		File f = file();
		if (!f.isFile()) return out;
		try {
			for (String line : new String(SafeFiles.read(f), StandardCharsets.UTF_8).split("\n")) {
				String[] p = line.split("\\|", -1);
				if (p.length < 7 || !p[0].matches("[0-9a-f]{16}")) continue;
				Entry e = new Entry();
				e.station = p[0];
				e.title = Line.text(p[1], 48);
				e.mode = java.util.Arrays.asList(homeplanet.vault.Vault.SLOTS).contains(p[2]) ? p[2] : homeplanet.vault.Vault.SANDBOX;
				e.host = p[3];
				try { e.port = Integer.parseInt(p[4]); e.lastSeen = Long.parseLong(p[5]); } catch (NumberFormatException x) { continue; }
				e.notes = "true".equals(p[6]);
				out.add(e);
			}
		} catch (IOException e) {
			return out;
		}
		Collections.sort(out, new Comparator<Entry>() { public int compare(Entry a, Entry b) { return Long.compare(b.lastSeen, a.lastSeen); } });
		return out;
	}
	private static void save(List<Entry> l) throws IOException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < l.size() && i < MAX; i++) {
			Entry e = l.get(i);
			sb.append(e.station).append('|').append(plain(e.title)).append('|').append(e.mode).append('|').append(plain(e.host)).append('|')
					.append(e.port).append('|').append(e.lastSeen).append('|').append(e.notes).append('\n');
		}
		Exchange.dir().mkdirs();
		SafeFiles.write(file(), sb.toString().getBytes(StandardCharsets.UTF_8));
	}
	private static String plain(String s) { return s == null ? "" : s.replace('|', ' ').replace('\n', ' '); }

	public static synchronized Entry get(String station) {
		for (Entry e : list()) if (e.station.equals(station)) return e;
		return null;
	}

	/**
	 * Remembers a commander met just now. Anything not known this time (null, or port 0) keeps what was known before.
	 * notes: null when this meeting doesn't say (a hail, a message).
	 */
	public static synchronized void seen(String station, String title, String mode, String host, int port, Boolean notes) {
		if (station == null || !station.matches("[0-9a-f]{16}")) return;
		List<Entry> l = list();
		Entry e = null;
		for (Entry x : l) if (x.station.equals(station)) e = x;
		long now = System.currentTimeMillis();
		boolean changed = e == null;
		if (e == null) {
			e = new Entry();
			e.station = station;
			e.title = "";
			e.mode = homeplanet.vault.Vault.SANDBOX;
			e.host = "";
			l.add(0, e);
		}
		if (title != null && !title.equals(e.title)) { e.title = Line.text(title, 48); changed = true; }
		if (mode != null && !mode.equals(e.mode)) { e.mode = mode; changed = true; }
		if (host != null && !host.isEmpty() && !host.equals(e.host)) { e.host = host; changed = true; }
		if (port > 0 && port != e.port) { e.port = port; changed = true; }
		if (notes != null && notes != e.notes) { e.notes = notes; changed = true; }
		if (!changed && now - e.lastSeen < QUIET) return; // nothing new to write down
		e.lastSeen = now;
		Collections.sort(l, new Comparator<Entry>() { public int compare(Entry a, Entry b) { return Long.compare(b.lastSeen, a.lastSeen); } });
		try { save(l); } catch (IOException x) { /* remembered next time */ }
	}
	/** Remembers everyone a search found. */
	public static void seenAll(List<Beacon.Found> found) {
		for (Beacon.Found f : found) seen(f.station, f.title, f.mode, f.host, f.port, f.notes);
	}
	/** Forgets a commander (what waits for them in the Outbox stays). */
	public static synchronized void remove(String station) throws IOException {
		List<Entry> l = list();
		for (int i = 0; i < l.size(); i++) if (l.get(i).station.equals(station)) l.remove(i--);
		save(l);
	}
}
