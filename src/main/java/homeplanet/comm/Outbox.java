package homeplanet.comm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import homeplanet.core.Store;

/**
 * What waits to go to another commander whose station couldn't be reached: kept in the vault's comm/outbox folder (one
 * file an item, so it survives a restart) and delivered the next time this station, its hailing frequencies open,
 * finds theirs. Stations are matched by their id, not their address, which can change. An item their station turns
 * away (it answered, but wouldn't take it) stops trying, saying why, until the commander tries again or cancels it.
 */
public final class Outbox {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Outbox.class);
	private Outbox() { }

	/** At most this many items wait, and this many for any one commander. */
	public static final int MAX = 20, MAX_EACH = 5;

	/** One item waiting: a message (a shipment may ride with one later). */
	public static final class Item {
		public String id, toStation, toTitle, host, text;
		public int port;
		public boolean priority;
		public long written;
		/** Why their station turned it away ("" while it's still trying). */
		public String refused = "";
		/** A shipment that goes with it: its parcel's id ("" for none). */
		public String shipment = "";
	}

	static File dir() { return new File(Exchange.dir(), "outbox"); }
	private static File fileOf(String id) { return new File(dir(), "out-" + id + ".txt"); }

	/** Everything waiting, oldest first (an unreadable file is left alone, and out of the list). */
	public static synchronized List<Item> list() {
		List<Item> out = new ArrayList<Item>();
		File[] fs = dir().listFiles();
		if (fs != null) for (File f : fs) {
			String n = f.getName();
			if (!n.startsWith("out-") || !n.endsWith(".txt")) continue;
			try { out.add(read(f, n.substring(4, n.length() - 4))); } catch (IOException e) { /* not one of ours, or damaged: kept, not shown */ }
		}
		Collections.sort(out, new Comparator<Item>() { public int compare(Item a, Item b) { return Long.compare(a.written, b.written); } });
		return out;
	}
	private static Item read(File f, String id) throws IOException {
		Properties p = Store.load(f);
		Item i = new Item();
		i.id = id;
		i.toStation = p.getProperty("to", "");
		if (!i.toStation.matches("[0-9a-f]{16}")) throw new IOException("no station");
		i.toTitle = p.getProperty("toTitle", "A commander");
		i.host = p.getProperty("host", "");
		i.port = Store.num(p, "port", -1);
		i.written = Store.longOf(p, "written", -1);
		if (i.port < 0 || i.written < 0) throw new IOException("damaged");
		i.text = Notes.clean(p.getProperty("text", ""));
		i.priority = "true".equals(p.getProperty("priority"));
		i.refused = p.getProperty("refused", "");
		i.shipment = p.getProperty("shipment", "");
		return i;
	}
	private static void write(Item i) throws IOException {
		Properties p = new Properties();
		p.setProperty("to", i.toStation);
		p.setProperty("toTitle", i.toTitle);
		p.setProperty("host", i.host == null ? "" : i.host);
		p.setProperty("port", Integer.toString(i.port));
		p.setProperty("written", Long.toString(i.written));
		p.setProperty("text", i.text);
		p.setProperty("priority", Boolean.toString(i.priority));
		p.setProperty("refused", i.refused == null ? "" : i.refused);
		p.setProperty("shipment", i.shipment == null ? "" : i.shipment);
		Store.write(fileOf(i.id), p, "Long Range Comm. outbox");
	}

	/** Puts a message in the outbox. Throws, saying why, if it's full. */
	public static synchronized Item add(String toStation, String toTitle, String host, int port, String text, boolean priority) throws IOException {
		return add(toStation, toTitle, host, port, text, priority, "");
	}
	/** As add, with a shipment (a parcel's id): a packed one is marked as waiting in the Outbox for that commander. */
	public static synchronized Item add(String toStation, String toTitle, String host, int port, String text, boolean priority, String shipment) throws IOException {
		List<Item> all = list();
		int each = 0;
		for (Item i : all) if (i.toStation.equals(toStation)) each++;
		if (all.size() >= MAX) throw new IOException("The Outbox is full (" + MAX + " waiting): cancel something there first.");
		if (each >= MAX_EACH) throw new IOException(MAX_EACH + " messages already wait for " + toTitle + ": cancel one first.");
		Item i = new Item();
		i.written = System.currentTimeMillis();
		i.id = Long.toString(i.written, 36) + "-" + Integer.toString((int) (Math.random() * 46656), 36);
		i.toStation = toStation;
		i.toTitle = Line.text(toTitle, 48);
		i.host = host;
		i.port = port;
		i.text = Notes.clean(text);
		i.priority = priority;
		i.shipment = shipment == null ? "" : shipment;
		Shipments.Parcel parcel = i.shipment.isEmpty() ? null : Shipments.find(i.shipment);
		if (parcel != null && !parcel.incoming) Shipments.inOutbox(parcel, toStation, i.toTitle);
		write(i);
		homeplanet.core.HistoryLog.entry("LONG RANGE OUTBOX", "a message for " + i.toTitle + " waits to go", null,
				homeplanet.core.Event.of("LONG_RANGE_OUTBOX").put("what", "waiting").put("to_commander", i.toTitle).put("to_station", i.toStation).put("message_id", i.id).put("priority", i.priority).put("shipment", i.shipment.isEmpty() ? null : i.shipment));
		return i;
	}
	/** Cancels an item: a shipment with it is unpacked (or, a return, goes back to waiting in the inbox). */
	public static synchronized void cancel(Item i) throws IOException {
		Shipments.Parcel p = i.shipment.isEmpty() ? null : Shipments.find(i.shipment);
		if (p != null) Shipments.cancelled(p);
		remove(i);
	}
	/** Cancels the Outbox item a shipment waits with (its goods come back). False if none carries it. */
	public static synchronized boolean cancelShipment(String parcelId) throws IOException {
		for (Item i : list()) if (parcelId.equals(i.shipment)) { cancel(i); return true; }
		return false;
	}
	/** Takes an item out: cancelled, or delivered. */
	public static synchronized void remove(Item i) throws IOException {
		File f = fileOf(i.id);
		if (f.exists() && !f.delete()) throw new IOException("Could not remove " + f);
	}
	/** Marks an item turned away (it stops trying), or clears that to try again. */
	public static synchronized void refused(Item i, String why) throws IOException {
		i.refused = why == null ? "" : why;
		write(i);
	}
	/** Anything still trying for this station? */
	public static synchronized boolean waitingFor(String station) {
		for (Item i : list()) if (i.toStation.equals(station) && i.refused.isEmpty()) return true;
		return false;
	}
	/** Anything still trying at all? */
	public static synchronized boolean anyWaiting() {
		for (Item i : list()) if (i.refused.isEmpty()) return true;
		return false;
	}

	/**
	 * Delivers what waits for that station, now found at host:port (off the event thread). Returns a line for each
	 * delivered (or turned away) item; an item that couldn't get through stays, to try again later. replyPort: this
	 * station's own frequency, for a reply.
	 */
	public static List<String> deliver(String station, String host, int port, String version, String myStation, String myTitle, int replyPort) {
		return deliver(station, host, port, version, myStation, myTitle, replyPort, true);
	}
	/** shipmentsOk: their station takes shipments (its search answer says so); one that doesn't is never sent one. */
	public static List<String> deliver(String station, String host, int port, String version, String myStation, String myTitle, int replyPort, boolean shipmentsOk) {
		List<String> said = new ArrayList<String>();
		for (Item i : list()) {
			if (!i.toStation.equals(station) || !i.refused.isEmpty()) continue;
			Shipments.Parcel parcel = i.shipment.isEmpty() ? null : Shipments.sendable(i.shipment);
			if (!i.shipment.isEmpty() && parcel == null) { // its shipment was unpacked meanwhile: the message would promise what isn't there
				try { refused(i, "Its shipment was unpacked: cancel this message."); } catch (IOException x) { log.debug("Outbox: a message could not be marked refused: {}", x.toString()); }
				continue;
			}
			if (parcel != null && !shipmentsOk) {
				try { refused(i, i.toTitle + "'s station can't take shipments (it needs a newer version)."); } catch (IOException x) { log.debug("Outbox: a message could not be marked refused: {}", x.toString()); }
				said.add("A shipment for " + i.toTitle + " can't go: their station needs a newer version.");
				continue;
			}
			try {
				Wire.Msg note = Notes.note(version, myStation, myTitle, i.text, i.priority, replyPort).put("written", i.written);
				if (parcel != null) Shipments.attach(note, parcel);
				String where = Notes.send(host, port, note, i.toTitle);
				if (parcel != null) Shipments.delivered(parcel, i.toTitle);
				remove(i);
				said.add((Notes.POPUP.equals(where) ? "Shown to " + i.toTitle : "Delivered to " + i.toTitle + "'s inbox") + " (it waited " + waited(i.written) + ")"
						+ (parcel != null ? ", with the shipment (" + parcel.words() + ")." : "."));
				homeplanet.core.HistoryLog.entry("LONG RANGE OUTBOX", "a message for " + i.toTitle + " delivered, after " + waited(i.written), null,
						homeplanet.core.Event.of("LONG_RANGE_OUTBOX").put("what", "delivered").put("to_commander", i.toTitle).put("to_station", i.toStation).put("message_id", i.id).put("waited", waited(i.written)).put("shipment", parcel == null ? null : parcel.id));
			} catch (Notes.Refused e) {
				if (e.getMessage().contains(Notes.TOO_MANY)) { // busy for a minute: it stays waiting, and goes on a later search
					said.add(i.toTitle + "'s station is busy: the Outbox tries again shortly.");
					break;
				}
				try { refused(i, e.getMessage()); } catch (IOException x) { /* it stays trying */ }
				said.add("A message for " + i.toTitle + " was turned away: " + e.getMessage());
			} catch (IOException e) {
				break; // not reachable after all: the rest wait too
			}
		}
		return said;
	}
	/** "3 minutes", "2 hours", "4 days". */
	public static String waited(long since) {
		long m = Math.max(0, (System.currentTimeMillis() - since) / 60000);
		if (m < 60) return m + (m == 1 ? " minute" : " minutes");
		long h = m / 60;
		if (h < 48) return h + (h == 1 ? " hour" : " hours");
		long d = h / 24;
		return d + " days";
	}
}
