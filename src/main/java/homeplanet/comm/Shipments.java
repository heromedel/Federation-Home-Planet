package homeplanet.comm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;

import homeplanet.core.Event;
import homeplanet.core.HistoryLog;
import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;
import homeplanet.core.Store;
import homeplanet.parser.SaveHelper;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * Goods sent to another commander without a channel: a parcel. Outgoing, it's packed (the goods taken off their ships
 * into escrow, as a trade's are), then sent with a message, straight away or from the Outbox. Incoming, it's held in
 * the inbox until the commander accepts it (into the Cargo Hold), delivers it to another of their fleets that may trade
 * with the sender (that fleet's Cargo Hold file is written), or returns it (through the Outbox). Each parcel is a file
 * in the vault's comm folder (parcel-ID.txt), whose state says where its goods are, and changes in the same
 * transaction as the save the goods go into, so they're never in two places or none.
 */
public final class Shipments {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Shipments.class);
	private Shipments() { }

	/** Outgoing: packed (goods in escrow), in the Outbox, sent (gone), unpacked (back in the Cargo Hold). */
	public static final String PACKED = "packed", OUTBOX = "outbox", SENT = "sent", UNPACKED = "unpacked";
	/** Incoming: held (in the inbox), accepted, delivered to another fleet, returning (in the Outbox), returned. */
	public static final String HELD = "held", ACCEPTED = "accepted", ELSEWHERE = "elsewhere", RETURNING = "returning", RETURNED = "returned";

	public static final class Parcel {
		public String id, state, peerStation = "", peerTitle = "", peerMode = Vault.SANDBOX, host = "", date = "";
		public boolean incoming, peerAnyLevel;
		public int port;
		public List<Line> lines = new ArrayList<Line>();
		public String words() { return Exchange.words(lines); }
		/** The other commander as a hello would describe them: for the mode rules. */
		Session.Peer peer() {
			Session.Peer p = new Session.Peer();
			p.station = peerStation;
			p.title = peerTitle;
			p.mode = peerMode;
			p.anyLevel = peerAnyLevel;
			return p;
		}
	}

	static File fileOf(String id) { return new File(Exchange.dir(), "parcel-" + id + ".txt"); }

	static byte[] bytes(Parcel p) throws IOException {
		Properties pr = new Properties();
		pr.setProperty("id", p.id);
		pr.setProperty("state", p.state);
		pr.setProperty("incoming", Boolean.toString(p.incoming));
		pr.setProperty("peerStation", p.peerStation);
		pr.setProperty("peerTitle", p.peerTitle);
		pr.setProperty("peerMode", p.peerMode);
		pr.setProperty("peerAnyLevel", Boolean.toString(p.peerAnyLevel));
		pr.setProperty("host", p.host == null ? "" : p.host);
		pr.setProperty("port", Integer.toString(p.port));
		pr.setProperty("date", p.date);
		Wire.Msg m = new Wire.Msg("LINES");
		Line.writeLines(m, p.lines);
		for (Map.Entry<String, String> e : m.fields().entrySet()) pr.setProperty("lines." + e.getKey(), e.getValue());
		return Store.bytes(pr, "A Long Range Comm. shipment (state: " + p.state + "). Federation Home Planet rewrites this file.");
	}
	/** An event about a parcel: its id and state, whose it is, and its goods in words. */
	private static Event parcelEvent(String kind, Parcel p) {
		return Event.of(kind).put("shipment", p.id).put("state", p.state).put("incoming", p.incoming).put("peer", p.peerTitle).put("peer_station", p.peerStation).put("goods", p.words());
	}
	static Parcel read(File f) throws IOException {
		Properties pr = Store.load(f);
		Parcel p = new Parcel();
		p.id = pr.getProperty("id", "");
		p.state = pr.getProperty("state", "");
		p.incoming = "true".equals(pr.getProperty("incoming"));
		p.peerStation = pr.getProperty("peerStation", "");
		p.peerTitle = pr.getProperty("peerTitle", "");
		p.peerMode = java.util.Arrays.asList(Vault.SLOTS).contains(pr.getProperty("peerMode")) ? pr.getProperty("peerMode") : Vault.SANDBOX;
		p.peerAnyLevel = "true".equals(pr.getProperty("peerAnyLevel"));
		p.host = pr.getProperty("host", "");
		p.port = Store.num(pr, "port", 0);
		p.date = pr.getProperty("date", "");
		Wire.Msg m = new Wire.Msg("LINES");
		for (String k : pr.stringPropertyNames()) if (k.startsWith("lines.")) m.put(k.substring(6), pr.getProperty(k));
		p.lines = Line.readLines(m);
		return p;
	}
	/** A parcel by its id, or null. */
	public static Parcel find(String id) {
		if (id == null || !id.matches("[0-9a-z-]{1,64}")) return null;
		File f = fileOf(id);
		try { return f.isFile() ? read(f) : null; } catch (IOException e) { return null; }
	}
	/**
	 * The outgoing shipment the Long Range screen shows: packed, or waiting in the Outbox with its message (the newest,
	 * if more than one waits), or null.
	 */
	public static Parcel showing() {
		Parcel best = null;
		File[] fs = Exchange.dir().listFiles();
		if (fs != null) for (File f : fs) {
			if (!f.getName().startsWith("parcel-") || !f.getName().endsWith(".txt")) continue;
			try {
				Parcel p = read(f);
				if (p.incoming) continue;
				if (PACKED.equals(p.state)) return p;
				if (OUTBOX.equals(p.state) && (best == null || p.date.compareTo(best.date) > 0)) best = p;
			} catch (IOException e) { /* not one to show */ }
		}
		return best;
	}
	/** The one packed shipment waiting to be sent, or null. */
	public static Parcel packed() {
		File[] fs = Exchange.dir().listFiles();
		if (fs != null) for (File f : fs) {
			if (!f.getName().startsWith("parcel-") || !f.getName().endsWith(".txt")) continue;
			try { Parcel p = read(f); if (!p.incoming && PACKED.equals(p.state)) return p; } catch (IOException e) { /* not one to send */ }
		}
		return null;
	}
	private static String now() { return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date()); }

	// ---- sending ----

	/** Packs these lines (taken off their ships into escrow, all in one save). One packed shipment at a time; no whole ships. */
	public static Parcel pack(List<Line> lines) throws IOException {
		if (packed() != null) throw new IOException("A shipment is already packed: send it, or unpack it first.");
		if (lines.isEmpty()) throw new IOException("Nothing to pack.");
		Vault v = Vault.get();
		v.storage(); // made if missing
		Exchange.Sources src = new Exchange.Sources();
		for (Line l : lines) {
			if (l.kind == Line.Kind.SHIP) throw new IOException("A whole ship can't be shipped: hail to trade her.");
			Exchange.take(src, l);
		}
		Parcel p = new Parcel();
		p.id = Exchange.newId(Commander.stationId());
		p.state = PACKED;
		p.date = now();
		p.lines = new ArrayList<Line>(lines);
		Vault.Transaction tx = v.begin();
		for (Map.Entry<String, Vault.Copy> e : src.copies.entrySet()) tx.put(src.ships.get(e.getKey()), e.getValue().save, e.getValue().hash);
		tx.put(fileOf(p.id), bytes(p));
		Exchange.dir().mkdirs();
		tx.commit();
		HistoryLog.entry("SHIPMENT PACKED", p.words(), null, parcelEvent("SHIPMENT_PACKED", p));
		return p;
	}
	/** Goods into this fleet's Cargo Hold, and the parcel's new state, in one save. */
	private static void intoHold(Parcel p, String state) throws IOException {
		Vault v = Vault.get();
		Ship st = v.storage();
		Vault.Copy c = v.readCopy(st);
		ShipState s = c.save.getPlayerShip();
		for (Line l : p.lines) Exchange.give(s, l);
		p.state = state;
		v.begin().put(st, c.save, c.hash).put(fileOf(p.id), bytes(p)).commit();
	}
	/** Unpacks an outgoing parcel (packed, or waiting in the Outbox): its goods back in the Cargo Hold. */
	public static void unpack(Parcel p) throws IOException {
		log.debug("Shipment {}: unpacked into the Cargo Hold ({})", p.id, p.words());
		if (p.incoming || !(PACKED.equals(p.state) || OUTBOX.equals(p.state))) throw new IOException("This shipment isn't waiting to be sent (" + p.state + ")");
		intoHold(p, UNPACKED);
		HistoryLog.entry("SHIPMENT UNPACKED", p.words() + ": back in the Cargo Hold", null, parcelEvent("SHIPMENT_UNPACKED", p).put("to", "hold"));
	}
	/** Marks a parcel waiting in the Outbox for that commander. */
	static void inOutbox(Parcel p, String toStation, String toTitle) throws IOException {
		log.debug("Shipment {}: in the outbox for {}", p.id, toTitle);
		p.state = OUTBOX;
		p.peerStation = toStation;
		p.peerTitle = toTitle;
		SafeFiles.write(fileOf(p.id), bytes(p));
	}
	/** Sent and taken by the other station: an outgoing one is gone; a returned one has gone home. */
	static void delivered(Parcel p, String toTitle) throws IOException {
		log.debug("Shipment {}: delivered to {}", p.id, toTitle);
		p.state = p.incoming ? RETURNED : SENT;
		if (!p.incoming) p.peerTitle = toTitle;
		SafeFiles.write(fileOf(p.id), bytes(p));
		HistoryLog.entry(p.incoming ? "SHIPMENT RETURNED" : "SHIPMENT SENT", p.words() + (p.incoming ? " back to " : " to ") + toTitle, null, parcelEvent(p.incoming ? "SHIPMENT_RETURNED" : "SHIPMENT_SENT", p).put("to_commander", toTitle));
	}
	/** Taken out of the Outbox: an outgoing one is unpacked; a return goes back to waiting in the inbox. */
	static void cancelled(Parcel p) throws IOException {
		log.debug("Shipment {}: cancelled", p.id);
		if (p.incoming) {
			p.state = HELD;
			SafeFiles.write(fileOf(p.id), bytes(p));
		} else if (OUTBOX.equals(p.state)) {
			unpack(p);
		}
	}
	/** The message a parcel travels with: the commander's text, the parcel's id and lines, and the sender's mode (for the rules). */
	public static Wire.Msg attach(Wire.Msg note, Parcel p) {
		// a returned parcel travels under a new id: its sender's station already has a record under the old one
		note.put("shipment", p.incoming ? "ret-" + p.id : p.id).put("mode", Vault.get().slot).put("anyLevel", HomePlanet.immersiveAnyLevel);
		Line.writeLines(note, p.lines);
		return note;
	}
	/** The parcel to send with a message: the packed one, or one in the Outbox for that commander. */
	public static Parcel sendable(String id) {
		Parcel p = find(id);
		return p != null && (PACKED.equals(p.state) || OUTBOX.equals(p.state) || RETURNING.equals(p.state)) ? p : null;
	}
	/** Sent straight away: taken by their station. */
	public static void sent(Parcel p, String toStation, String toTitle) throws IOException {
		p.peerStation = toStation;
		delivered(p, toTitle);
	}

	// ---- receiving ----

	/**
	 * Why this station turns a parcel away at the door (an item or race its game data lacks), or null to take it.
	 * The mode rules don't turn it away: it's held, to deliver to another fleet or return.
	 */
	public static String refuses(List<Line> lines) {
		for (Line l : lines) {
			if (l.kind == Line.Kind.SHIP) return "A whole ship can't come as a shipment";
			String why = Exchange.refuses(l);
			if (why != null) return l.title() + ": " + why;
		}
		return null;
	}
	/**
	 * Holds an arrived parcel (filed once: one that arrives again, after a lost answer, is only acknowledged) and puts
	 * it in the inbox. True if it was new.
	 */
	public static boolean receive(Notes.Note n, String host) throws IOException {
		if (fileOf(n.shipment).exists()) return false;
		Parcel p = new Parcel();
		p.id = n.shipment;
		p.incoming = true;
		p.state = HELD;
		p.peerStation = n.station;
		p.peerTitle = n.title;
		p.peerMode = n.mode;
		p.peerAnyLevel = n.anyLevel;
		p.host = host;
		p.port = n.replyPort;
		p.date = now();
		p.lines = n.lines;
		Exchange.dir().mkdirs();
		SafeFiles.write(fileOf(p.id), bytes(p));
		HistoryLog.entry("SHIPMENT ARRIVED", p.words() + " from " + p.peerTitle, null, parcelEvent("SHIPMENT_ARRIVED", p));
		homeplanet.parser.Transmissions.deliver("parcel:" + p.id, n.title, "Shipment from " + n.title,
				n.text + "\n~ " + n.title + Notes.waitedNote(n) + "\n\nThe shipment: " + p.words() + ".");
		return true;
	}
	/** Why this fleet can't accept the parcel (the mode rules), or null if it can. */
	public static String whyNot(Parcel p) {
		return Session.cantTrade(p.peer(), Vault.get().slot, HomePlanet.immersiveAnyLevel);
	}
	/** Accepts a held parcel into this fleet's Cargo Hold. */
	public static void accept(Parcel p) throws IOException {
		if (!p.incoming || !HELD.equals(p.state)) throw new IOException("This shipment was already dealt with (" + p.state + ")");
		String why = whyNot(p);
		if (why != null) throw new IOException(why);
		intoHold(p, ACCEPTED);
		HistoryLog.entry("SHIPMENT ACCEPTED", p.words() + " from " + p.peerTitle + ": in the Cargo Hold", null, parcelEvent("SHIPMENT_ACCEPTED", p).put("to", "hold"));
	}
	/** This commander's other fleets that may take the parcel (by the mode rules), with a Cargo Hold to put it in. */
	public static List<String> otherFleets(Parcel p) {
		List<String> out = new ArrayList<String>();
		Vault v = Vault.get();
		for (String k : Vault.SLOTS) {
			if (k.equals(v.slot)) continue;
			if (!Vault.holdFileIn(Vault.rootOf(v.saves, k)).isFile()) continue;
			if (Session.cantTrade(p.peer(), k, HomePlanet.immersiveAnyLevel) == null) out.add(k);
		}
		return out;
	}
	/**
	 * Delivers a held parcel to another of this commander's fleets: its Cargo Hold file is written (that fleet needn't
	 * be the one in use), with the parcel's state, in one transaction.
	 */
	public static void deliverTo(Parcel p, String slot) throws IOException {
		if (!p.incoming || !HELD.equals(p.state)) throw new IOException("This shipment was already dealt with (" + p.state + ")");
		if (!otherFleets(p).contains(slot)) throw new IOException("The " + Vault.title(slot) + " fleet can't take it");
		Vault v = Vault.get();
		File hold = Vault.holdFileIn(Vault.rootOf(v.saves, slot));
		SavedGameState gs;
		try { gs = homeplanet.parser.HoldXml.read(hold); }
		catch (Exception e) { throw new IOException("The " + Vault.title(slot) + " fleet's Cargo Hold couldn't be read: " + e.getMessage()); }
		for (Line l : p.lines) Exchange.give(gs.getPlayerShip(), l);
		p.state = ELSEWHERE;
		v.begin().put(hold, Vault.holdBytes(hold, gs)).put(fileOf(p.id), bytes(p)).commit();
		HistoryLog.entry("SHIPMENT ACCEPTED", p.words() + " from " + p.peerTitle + ": in the " + Vault.title(slot) + " fleet's Cargo Hold", null, parcelEvent("SHIPMENT_ACCEPTED", p).put("to", "hold").put("to_fleet", Vault.title(slot)));
	}
	/** Returns a held parcel: it waits in the Outbox, addressed back to its sender, and goes when their station is found. */
	public static void returnIt(Parcel p) throws IOException {
		if (!p.incoming || !HELD.equals(p.state)) throw new IOException("This shipment was already dealt with (" + p.state + ")");
		Outbox.add(p.peerStation, p.peerTitle, p.host, p.port, "Returned: " + p.words() + ".", false, p.id);
		p.state = RETURNING;
		SafeFiles.write(fileOf(p.id), bytes(p));
		HistoryLog.entry("SHIPMENT RETURNING", p.words() + " to " + p.peerTitle, null, parcelEvent("SHIPMENT_RETURNING", p));
	}
}
