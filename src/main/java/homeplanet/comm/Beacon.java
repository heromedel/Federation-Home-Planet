package homeplanet.comm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finding other stations on the local network: a scan broadcasts "any stations?" to the stations' ports, and each
 * station with its hailing frequencies open answers with its commander, the ship they're aboard and where to hail it.
 * Text only, one short line each way; an answer that doesn't parse is ignored.
 * <p>
 * A scan asks twice: once with its station's id after the question (so a station that blocked it can stay silent),
 * and once without, for stations older than 4B.70, which answer only the bare question. A newer station answers the
 * first and ignores the bare one from the same scan.
 */
public final class Beacon {
	private static final Logger log = LoggerFactory.getLogger(Beacon.class);
	private Beacon() { }

	static final String ASK = "FHP-LRC?", ANSWER = "FHP-LRC!";

	/** A station that answered. */
	public static final class Found {
		public final String host, title, ship, version, station;
		/** Its mode: the vault's slot (sandbox, easy, normal, hard, custom). */
		public final String mode;
		public final int port;
		/** Its Long Range Comm. protocol: stations trade when theirs match. */
		public int protocol;
		/** It takes messages without a channel ({@link Notes}): 4B.72 and later. */
		public boolean notes;
		public boolean compatible() { return protocol == Session.PROTOCOL; }
		Found(String host, int port, String title, String ship, String version, String station, String mode) {
			this.host = host; this.port = port; this.title = title; this.ship = ship; this.version = version; this.station = station; this.mode = mode;
		}
	}

	/** What this station says about itself when asked (read each time: the ship aboard can change). */
	public interface Self { String answer(); }

	/** Answers scans while open (the Long Range Comm. screen). */
	public static final class Responder {
		private final DatagramSocket socket;
		private volatile boolean open = true;
		/** Where an id-bearing question last came from, and when (read and written on the beacon's own thread). */
		private final Map<String, Long> asked = new LinkedHashMap<String, Long>();
		public Responder(final Self self) throws IOException {
			DatagramSocket s = null;
			for (int i = 0; i < Channel.PORTS && s == null; i++) {
				try { s = new DatagramSocket(new InetSocketAddress(Channel.PORT0 + i)); }
				catch (IOException e) { s = null; }
			}
			if (s == null) throw new IOException("ports " + Channel.PORT0 + " to " + (Channel.PORT0 + Channel.PORTS - 1) + " are all in use");
			socket = s;
			Thread t = new Thread(new Runnable() {
				public void run() {
					byte[] buf = new byte[256];
					while (open) {
						try {
							DatagramPacket p = new DatagramPacket(buf, buf.length);
							socket.receive(p);
							String q = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
							String from = p.getSocketAddress().toString(), host = p.getAddress().getHostAddress();
							long now = System.currentTimeMillis();
							if (q.startsWith(ASK + "\n")) {
								asked.put(from, now); // its bare question, right behind, is the same scan
								while (asked.size() > 64) asked.remove(asked.keySet().iterator().next());
								if (Blocks.blocked(q.substring(ASK.length() + 1).trim(), host)) continue;
							} else if (q.equals(ASK)) {
								Long t = asked.get(from);
								if (t != null && now - t < 5000) continue;
								if (Blocks.blocked(null, host)) continue;
							} else {
								continue;
							}
							byte[] a = (ANSWER + "\n" + self.answer()).getBytes(StandardCharsets.UTF_8);
							socket.send(new DatagramPacket(a, a.length, p.getSocketAddress()));
						} catch (IOException e) {
							if (open) log.debug("Beacon: {}", e.toString());
						}
					}
				}
			}, "Long Range Comm. beacon");
			t.setDaemon(true);
			t.start();
		}
		public void close() { open = false; socket.close(); }
	}

	/**
	 * The answer's text: one field a line (tcp port, protocol, version, station id, title, ship, mode, then what it
	 * takes beyond trading, comma-separated: "notes"). Older stations read the first lines and ignore the rest.
	 */
	public static String answer(int tcpPort, String version, String station, String title, String ship, String mode) {
		return tcpPort + "\n" + Session.PROTOCOL + "\n" + version + "\n" + station + "\n" + title + "\n" + (ship == null ? "" : ship) + "\n" + mode + "\nnotes";
	}

	/** Asks every station in reach, listening for answers this long. Not on the event thread. Leaves out this station. */
	public static List<Found> scan(int ms, String self) {
		Map<String, Found> found = new LinkedHashMap<String, Found>();
		DatagramSocket s = null;
		try {
			s = new DatagramSocket();
			s.setBroadcast(true);
			byte[] q = (ASK + "\n" + self).getBytes(StandardCharsets.UTF_8), bare = ASK.getBytes(StandardCharsets.UTF_8);
			for (InetAddress a : targets()) {
				for (int i = 0; i < Channel.PORTS; i++) {
					try {
						s.send(new DatagramPacket(q, q.length, a, Channel.PORT0 + i));
						s.send(new DatagramPacket(bare, bare.length, a, Channel.PORT0 + i));
					} catch (IOException e) { /* that network refused: the others may not */ }
				}
			}
			long end = System.currentTimeMillis() + ms;
			byte[] buf = new byte[1024];
			while (true) {
				long left = end - System.currentTimeMillis();
				if (left <= 0) break;
				s.setSoTimeout((int) left);
				DatagramPacket p = new DatagramPacket(buf, buf.length);
				try { s.receive(p); } catch (SocketTimeoutException e) { break; }
				Found f = parse(p.getAddress().getHostAddress(), new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8));
				if (f == null || f.station.equals(self)) continue;
				// the same station heard on several networks (or as localhost too): the first answer stands
				if (!found.containsKey(f.station)) found.put(f.station, f);
			}
		} catch (IOException e) {
			log.warn("Long Range Comm. scan: {}", e.toString());
		} finally {
			if (s != null) s.close();
		}
		return new ArrayList<Found>(found.values());
	}
	static Found parse(String host, String text) {
		String[] l = text.split("\n", -1);
		if (l.length < 7 || !l[0].equals(ANSWER)) return null;
		try {
			int port = Integer.parseInt(l[1].trim());
			if (port < Channel.PORT0 || port >= Channel.PORT0 + Channel.PORTS) return null;
			if (!l[4].matches("[0-9a-f]{16}")) return null;
			String mode = l.length > 7 && java.util.Arrays.asList(homeplanet.vault.Vault.SLOTS).contains(l[7].trim()) ? l[7].trim() : homeplanet.vault.Vault.SANDBOX;
			Found f = new Found(host, port, Line.text(l[5], 48), Line.text(l[6], 64), Line.text(l[3], 16), l[4], mode);
			try { f.protocol = Integer.parseInt(l[2].trim()); } catch (NumberFormatException e) { f.protocol = -1; }
			f.notes = l.length > 8 && java.util.Arrays.asList(l[8].trim().split(",")).contains("notes");
			return f;
		} catch (NumberFormatException e) {
			return null;
		}
	}
	/** Where to ask: everywhere on each local network, and this computer itself (a second station on it). */
	private static Set<InetAddress> targets() {
		Set<InetAddress> out = new LinkedHashSet<InetAddress>();
		try {
			Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
			while (e != null && e.hasMoreElements()) {
				NetworkInterface ni = e.nextElement();
				try { if (!ni.isUp()) continue; } catch (IOException x) { continue; }
				for (InterfaceAddress ia : ni.getInterfaceAddresses()) if (ia.getBroadcast() != null) out.add(ia.getBroadcast());
			}
		} catch (IOException e) {
			log.debug("No network interfaces: {}", e.toString());
		}
		try { out.add(InetAddress.getByName("255.255.255.255")); } catch (IOException e) { }
		out.add(InetAddress.getLoopbackAddress());
		return out;
	}
}
