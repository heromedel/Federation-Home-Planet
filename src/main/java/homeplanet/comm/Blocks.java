package homeplanet.comm;

import java.util.ArrayList;
import java.util.List;

import homeplanet.core.HomePlanet;

/**
 * Commanders this station won't talk to. There are no accounts, so a block holds on to what a station sends: its id,
 * and the address it hailed from (an id can be made anew; an address over the internet changes less often). A
 * blocked station's hails go unanswered, as if this one weren't listening, and its scans get no answer. Kept in the
 * cfg as one line: id|name|address entries, separated by ";".
 */
public final class Blocks {
	private Blocks() { }

	static final String CFG = "long_range_comm_blocked";

	/** One blocked commander: their station's id, the name they went by, and the address they last hailed from (may be empty). */
	public static final class Entry {
		public final String station, name, address;
		Entry(String station, String name, String address) { this.station = station; this.name = name; this.address = address; }
	}

	public static synchronized List<Entry> list() {
		List<Entry> out = new ArrayList<Entry>();
		for (String e : HomePlanet.config.getProperty(CFG, "").split(";")) {
			String[] f = e.split("\\|", -1);
			if (f.length < 3 || !f[0].matches("[0-9a-f]{16}")) continue;
			out.add(new Entry(f[0], f[1], f[2]));
		}
		return out;
	}
	private static void save(List<Entry> l) {
		StringBuilder sb = new StringBuilder();
		for (Entry e : l) sb.append(sb.length() == 0 ? "" : ";").append(e.station).append('|').append(e.name).append('|').append(e.address);
		HomePlanet.config.setProperty(CFG, sb.toString());
		HomePlanet.saveConfig();
	}

	/** Blocks a station (again, with what's known now). The address is the host alone, without a port. */
	public static synchronized void block(String station, String name, String address) {
		if (station == null || !station.matches("[0-9a-f]{16}")) return;
		List<Entry> l = list();
		for (int i = 0; i < l.size(); i++) if (l.get(i).station.equals(station)) l.remove(i--);
		l.add(new Entry(station, plain(name), plain(kept(address))));
		save(l);
	}
	public static synchronized void unblock(String station) {
		List<Entry> l = list();
		for (int i = 0; i < l.size(); i++) if (l.get(i).station.equals(station)) l.remove(i--);
		save(l);
	}

	/** Is this station blocked: by its id, or by the address it comes from (host, or host:port)? */
	public static synchronized boolean blocked(String station, String address) {
		String h = host(address);
		for (Entry e : list()) {
			if (station != null && e.station.equals(station)) return true;
			if (!h.isEmpty() && e.address.equals(h)) return true;
		}
		return false;
	}

	/**
	 * The address worth keeping with a block: one from beyond the local network. This computer and a home network's
	 * addresses are left out: another station on them (a second station here, a housemate) would be blocked too.
	 */
	static String kept(String address) {
		String h = host(address);
		if (!h.matches("[0-9.]+|[0-9a-fA-F:]+")) return ""; // only a literal address, never a name to look up
		try {
			java.net.InetAddress a = java.net.InetAddress.getByName(h);
			if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()) return "";
			return a.getHostAddress();
		} catch (java.io.IOException e) {
			return "";
		}
	}
	/** The host of "host:port" (or the host as it is). */
	static String host(String address) {
		if (address == null) return "";
		String a = address.trim();
		int c = a.lastIndexOf(':');
		return c > 0 && a.indexOf(':') == c ? a.substring(0, c) : a;
	}
	/** Without the cfg line's separators. */
	private static String plain(String s) { return s == null ? "" : s.replace('|', ' ').replace(';', ' ').trim(); }
}
