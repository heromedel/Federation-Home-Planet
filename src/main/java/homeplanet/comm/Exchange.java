package homeplanet.comm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;

import homeplanet.core.Event;
import homeplanet.core.HistoryLog;
import homeplanet.core.SafeFiles;
import homeplanet.core.Store;
import homeplanet.model.Items;
import homeplanet.parser.SaveHelper;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * What a Long Range Comm. trade does to the fleet, in three steps, each one save of every file it touches:
 *
 * <ol>
 * <li><b>Escrow</b>: once both commanders accept, each station takes what it gives off its ships (and out of the Cargo
 * Hold) and writes a trade record holding both sides' lines. Nothing can be lost from here on: the record is the goods.
 * <li><b>Done</b>: what the other station gives goes into the Cargo Hold (no slot limits, so it always fits).
 * <li>Or <b>called off</b>: what was taken goes into the Cargo Hold instead.
 * </ol>
 *
 * The station that hailed leads: only it decides between done and called off, and the other follows its word. A link
 * lost in between leaves a record in escrow, settled the next time the two stations talk (or by hand, in Other...).
 */
public final class Exchange {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Exchange.class);
	private Exchange() { }

	public static final String ESCROW = "escrow", DONE = "done", CALLED_OFF = "called-off";

	/** A trade as its record has it. */
	public static final class Record {
		public String id, peerStation, peerTitle, state, date;
		public boolean leader;
		public List<Line> out = new ArrayList<Line>(), in = new ArrayList<Line>();
		public File file;
		/** Set when a trade brought a blueprint new to this station: the mod was rebuilt, and needs sending to FTL. */
		public transient boolean needsPatch;
		public String outWords() { return words(out); }
		public String inWords() { return words(in); }
	}

	// ---- the records ----

	public static File dir() { return new File(Vault.get().root, "comm"); }
	static File fileOf(String id) { return new File(dir(), "trade-" + id + ".txt"); }
	/** Where a trade keeps the ships it carries: out-N.zip (this station's, until done) and in-N.zip (the other's). */
	static File folderOf(String id) { return new File(dir(), "trade-" + id); }
	private static File pkgFile(Record r, boolean out, int n) { return new File(folderOf(r.id), (out ? "out-" : "in-") + n + ".zip"); }

	static byte[] bytes(Record r) throws IOException {
		Wire.Msg m = new Wire.Msg("RECORD");
		m.put("id", r.id).put("leader", r.leader).put("peerStation", r.peerStation).put("peerTitle", r.peerTitle).put("state", r.state).put("date", r.date);
		Wire.Msg out = new Wire.Msg("OUT"), in = new Wire.Msg("IN");
		Line.writeLines(out, r.out);
		Line.writeLines(in, r.in);
		Properties p = new Properties();
		for (Map.Entry<String, String> e : m.fields().entrySet()) p.setProperty(e.getKey(), e.getValue());
		for (Map.Entry<String, String> e : out.fields().entrySet()) p.setProperty("out." + e.getKey(), e.getValue());
		for (Map.Entry<String, String> e : in.fields().entrySet()) p.setProperty("in." + e.getKey(), e.getValue());
		return Store.bytes(p, "A Long Range Comm. trade (state: " + r.state + "). Federation Home Planet rewrites this file.");
	}
	static Record read(File f) throws IOException {
		Properties p = Store.load(f);
		Record r = new Record();
		r.file = f;
		r.id = p.getProperty("id", "");
		r.leader = "true".equals(p.getProperty("leader"));
		r.peerStation = p.getProperty("peerStation", "");
		r.peerTitle = p.getProperty("peerTitle", "");
		r.state = p.getProperty("state", "");
		r.date = p.getProperty("date", "");
		Wire.Msg out = new Wire.Msg("OUT"), in = new Wire.Msg("IN");
		for (String k : p.stringPropertyNames()) {
			if (k.startsWith("out.")) out.put(k.substring(4), p.getProperty(k));
			else if (k.startsWith("in.")) in.put(k.substring(3), p.getProperty(k));
		}
		r.out = Line.readLines(out);
		r.in = Line.readLines(in);
		return r;
	}
	/** A trade's record, or null if this station has none. */
	public static Record find(String id) {
		if (!id.matches("[0-9a-z-]{1,64}")) return null;
		File f = fileOf(id);
		try { return f.isFile() ? read(f) : null; } catch (IOException e) { log.warn("Could not read " + f + ": " + e); return null; }
	}
	/** Trades left in escrow (a link lost mid-exchange), oldest first. */
	public static List<Record> unfinished() {
		List<Record> out = new ArrayList<Record>();
		File[] fs = dir().listFiles();
		if (fs == null) return out;
		java.util.Arrays.sort(fs);
		for (File f : fs) {
			if (!f.getName().startsWith("trade-") || !f.getName().endsWith(".txt")) continue;
			try {
				Record r = read(f);
				if (ESCROW.equals(r.state)) out.add(r);
			} catch (IOException e) {
				log.warn("Could not read " + f + ": " + e);
			}
		}
		return out;
	}

	public static String newId(String station) {
		return station + "-" + Long.toString(System.currentTimeMillis(), 36) + "-" + Integer.toString(new java.util.Random().nextInt(1 << 20), 36);
	}

	// ---- what the other side offers: can this station take it? ----

	/** Why this station can't take a line the other station offers, or null if it can. */
	public static String refuses(Line l) {
		switch (l.kind) {
			case WEAPON: return Items.isWeapon(l.id) ? null : unknown(l);
			case DRONE: return Items.isDrone(l.id) ? null : unknown(l);
			case AUGMENT: return Items.isAugment(l.id) ? null : unknown(l);
			case CREW: return net.blerf.ftl.parser.DataManager.get().getCrews().containsKey(l.crew.getRace().getId()) ? null
					: "Crew of this race aren't in this station's game data (a mod?)";
			case SHIP: return shipRefused(l.id);
			case OTHER: return "This station doesn't know what this is: the other station's Long Range Comm. is newer";
			default: return null;
		}
	}
	/**
	 * Why a ship of this blueprint can't change hands, or null. A remodel's or a design's blueprint travels with her
	 * (homeplanet.parser.ShipPapers); any other must be in this station's game data.
	 */
	public static String shipRefused(String blueprint) {
		if (homeplanet.parser.ShipPapers.custom(blueprint)) return null;
		if (!net.blerf.ftl.parser.DataManager.get().getShips().containsKey(blueprint))
			return "Her blueprint (" + blueprint + ") isn't in this station's game data";
		return null;
	}
	private static String unknown(Line l) { return l.id + " isn't in this station's game data (a mod the other station uses?)"; }

	// ---- escrow ----

	/** What every source ship looks like as read now, and what to write back. */
	static final class Sources {
		final Map<String, Ship> ships = new LinkedHashMap<String, Ship>();
		final Map<String, Vault.Copy> copies = new LinkedHashMap<String, Vault.Copy>();
		Vault.Copy of(String shipId) throws IOException {
			if (copies.containsKey(shipId)) return copies.get(shipId);
			Vault v = Vault.get();
			Ship s = v.byId(shipId);
			if (s == null) throw new IOException("A ship in your offer is no longer at the Space Dock");
			if (!s.isStorage()) {
				if (s.state != Ship.State.DOCKED && s.state != Ship.State.BOARDED) throw new IOException(s.name + " is no longer at the Space Dock");
				if (!v.mayTrade(s)) throw new IOException(s.name + " has left the station: she can only trade at a beacon with a store");
			}
			Vault.Copy c = v.readCopy(s);
			ships.put(shipId, s);
			copies.put(shipId, c);
			return c;
		}
	}

	/**
	 * Takes this station's lines off their ships and writes the record with both sides' lines, all in one save.
	 * Throws, changing nothing, if anything offered is no longer there (the reason says what).
	 */
	public static Record escrow(String id, boolean leader, String peerStation, String peerTitle, List<Line> out, List<Line> in) throws IOException {
		if (!id.matches("[0-9a-z-]{1,64}")) throw new IOException("Bad trade id");
		if (fileOf(id).exists()) throw new IOException("This trade was already made");
		for (Line l : in) {
			String why = refuses(l);
			if (why != null) throw new IOException(l.title() + ": " + why);
		}
		Vault v = Vault.get();
		v.storage(); // made if missing
		Sources src = new Sources();
		List<Ship> leaving = new ArrayList<Ship>();
		for (Line l : out) if (l.kind == Line.Kind.SHIP) leaving.add(leaving(v, l, out));
		for (Line l : out) if (l.kind != Line.Kind.SHIP) take(src, l);
		Record r = new Record();
		r.id = id;
		r.leader = leader;
		r.peerStation = peerStation;
		r.peerTitle = peerTitle;
		r.state = ESCROW;
		r.date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());
		r.out = out;
		r.in = in;
		r.file = fileOf(id);
		dir().mkdirs();
		// a ship goes as her package, kept in the trade's folder until it's settled
		for (int i = 0; i < leaving.size(); i++) {
			File f = null;
			for (Line l : out) if (l.kind == Line.Kind.SHIP && l.from.equals(leaving.get(i).id)) f = pkgFile(r, true, l.n);
			f.getParentFile().mkdirs();
			SafeFiles.write(f, v.packageOf(leaving.get(i)));
		}
		Vault.Transaction tx = v.begin();
		for (Map.Entry<String, Vault.Copy> e : src.copies.entrySet()) tx.put(src.ships.get(e.getKey()), e.getValue().save, e.getValue().hash);
		tx.put(r.file, bytes(r));
		try {
			tx.commit();
		} catch (IOException e) {
			SafeFiles.deleteTree(folderOf(id));
			throw e;
		}
		for (Ship s : leaving) {
			try { v.sendAway(s, peerTitle); }
			catch (IOException e) { log.warn("Could not send {} away yet (done when the trade settles): {}", s, e.toString()); }
		}
		return r;
	}
	/** A ship offered whole: she must be docked at a station, with no final battle to settle and nothing else of hers in the offer. */
	private static Ship leaving(Vault v, Line l, List<Line> out) throws IOException {
		Ship s = v.byId(l.from);
		if (s == null) throw new IOException(l.name + " is no longer at the Space Dock");
		if (s.state != Ship.State.DOCKED) throw new IOException(s.name + " must be docked to change hands" + (s.isBoarded() ? ": board another ship first" : ""));
		if (!v.mayTrade(s)) throw new IOException(s.name + " has left the station: she can only change hands at a beacon with a store");
		if (v.finalBattlePending(s)) throw new IOException(s.name + " has a final battle still to settle");
		SavedGameState gs = s.save();
		if (gs == null) throw new IOException(s.name + "'s save can't be read");
		String why = shipRefused(gs.getPlayerShipBlueprintId());
		if (why != null) throw new IOException(s.name + ": " + why);
		for (Line o : out) if (o != l && o.from.equals(s.id)) throw new IOException(s.name + " is offered whole: everything aboard goes with her");
		return s;
	}

	// ---- ships' packages ----

	/** This station's ships in a trade, as their packages: line number to bytes. */
	public static Map<Integer, byte[]> outPackages(Record r) throws IOException {
		Map<Integer, byte[]> m = new LinkedHashMap<Integer, byte[]>();
		for (Line l : r.out) if (l.kind == Line.Kind.SHIP) m.put(l.n, SafeFiles.read(pkgFile(r, true, l.n)));
		return m;
	}
	/**
	 * Keeps the other station's ship for this line, once she's checked: a package of the right files, a save that
	 * reads, her blueprint the one offered and known here. Throws (a reason) if she isn't.
	 */
	public static void keepIncoming(Record r, int n, byte[] pkg) throws IOException {
		Line l = null;
		for (Line x : r.in) if (x.n == n && x.kind == Line.Kind.SHIP) l = x;
		if (l == null) throw new IOException("A ship arrived that wasn't in the offer");
		if (homeplanet.parser.ShipPapers.custom(l.id)) {
			// her save can't be read yet (her blueprint isn't here, or a different ship has its number): her papers are checked instead
			Map<String, byte[]> files = Vault.unpack(pkg);
			try { homeplanet.parser.ShipPapers.check(files, l.id); }
			catch (IOException e) { throw new IOException(l.name + ": " + e.getMessage()); }
			if (!namesBlueprint(files.get("ship.sav"), l.id)) throw new IOException(l.name + " isn't the ship that was offered");
		} else {
			SavedGameState gs = readShip(pkg);
			if (!gs.getPlayerShipBlueprintId().equals(l.id)) throw new IOException(l.name + " isn't the ship that was offered");
			String why = shipRefused(gs.getPlayerShipBlueprintId());
			if (why != null) throw new IOException(l.name + ": " + why);
		}
		File f = pkgFile(r, false, n);
		f.getParentFile().mkdirs();
		SafeFiles.write(f, pkg);
	}
	/** Does this save name this blueprint (scanned, not read: the blueprint may not be here yet)? */
	private static boolean namesBlueprint(byte[] sav, String bpId) throws IOException {
		dir().mkdirs();
		File tmp = File.createTempFile("incoming-", ".sav", dir());
		try {
			SafeFiles.write(tmp, sav);
			List<String> ids = homeplanet.parser.Retrofit.blueprintIds(tmp);
			return ids != null && ids.contains(bpId);
		} finally {
			tmp.delete();
		}
	}
	/** The save in a ship's package, read (from a temporary file beside the trades). */
	static SavedGameState readShip(byte[] pkg) throws IOException { return readSave(Vault.unpack(pkg).get("ship.sav")); }
	static SavedGameState readSave(byte[] sav) throws IOException {
		dir().mkdirs();
		File tmp = File.createTempFile("incoming-", ".sav", dir());
		try {
			SafeFiles.write(tmp, sav);
			try { return new net.blerf.ftl.parser.SavedGameParser().readSavedGame(tmp); }
			catch (Exception e) { throw new IOException("Her save couldn't be read: " + e.getMessage()); }
		} finally {
			tmp.delete();
		}
	}
	/** Takes one line off its ship (in the copy, written by escrow). */
	static void take(Sources src, Line l) throws IOException {
		Vault.Copy c = src.of(l.from);
		Ship ship = src.ships.get(l.from);
		SavedGameState gs = c.save;
		ShipState s = gs.getPlayerShip();
		String where = ship.isStorage() ? "the Cargo Hold" : ship.name;
		if (l.kind.isSupply()) {
			int have = supply(s, l.kind);
			if (have < l.amount) throw new IOException(where + " has only " + have + " " + supplyName(l.kind) + " now, not " + l.amount);
			setSupply(s, l.kind, have - l.amount);
		} else if (l.kind.isItem()) {
			// a ship's own cargo slots, or fitted (the Cargo Hold keeps everything in its lists); the other place if it moved
			boolean done = l.inCargo && !ship.isStorage() && gs.getCargoIdList() != null && gs.getCargoIdList().remove(l.id);
			if (!done) done = removeFitted(s, l);
			if (!done && !ship.isStorage() && gs.getCargoIdList() != null) done = gs.getCargoIdList().remove(l.id);
			if (!done) throw new IOException(Items.title(l.id) + " is no longer aboard " + where);
		} else if (l.kind == Line.Kind.CREW) {
			String sig = Line.signature(l.crew);
			CrewState found = null;
			for (CrewState cs : SaveHelper.getOwnCrew(s)) if (Line.signature(cs).equals(sig)) { found = cs; break; }
			if (found == null) throw new IOException(l.crew.getName() + " is no longer aboard " + where);
			if (!SaveHelper.hasBody(found)) throw new IOException(found.getName() + " is waiting to be cloned and can't leave " + where + " right now");
			if (!ship.isStorage() && SaveHelper.getOwnCrew(s).size() <= 1) throw new IOException("At least one crew member must stay aboard " + where);
			s.getCrewList().remove(found);
		}
	}
	private static boolean removeFitted(ShipState s, Line l) {
		if (l.kind == Line.Kind.WEAPON) {
			WeaponState w = SaveHelper.findWeapon(s.getWeaponList(), l.id);
			if (w == null) return false;
			SaveHelper.removeWeapon(s, w);
			return true;
		}
		if (l.kind == Line.Kind.DRONE) {
			DroneState d = SaveHelper.findDrone(s.getDroneList(), l.id);
			if (d == null) return false;
			SaveHelper.removeDrone(s, d);
			return true;
		}
		return s.getAugmentIdList().remove(l.id);
	}

	// ---- done, or called off: goods go into the Cargo Hold, ships to the Space Dock ----

	/** Where these lines end up, in words: "in the Cargo Hold", "docked at the Space Dock", or both. */
	public static String whereTheyGo(List<Line> lines) {
		int ships = 0, goods = 0;
		for (Line l : lines) { if (l.kind == Line.Kind.SHIP) ships++; else goods++; }
		if (ships == 0) return goods == 0 ? "" : "in the Cargo Hold";
		String docked = ships == 1 ? "she is docked at the Space Dock" : "they are docked at the Space Dock";
		return goods == 0 ? docked : docked + ", and the rest is in the Cargo Hold";
	}

	/** The other station's lines go into the Cargo Hold, and the record is marked done. */
	public static void complete(Record r) throws IOException {
		if (!ESCROW.equals(r.state)) throw new IOException("This trade was already settled (" + r.state + ")");
		Vault v = Vault.get();
		// the ships first: received once only (her mark names the trade), so trying again after a failure is safe
		for (Line l : r.in) {
			if (l.kind != Line.Kind.SHIP) continue;
			File f = pkgFile(r, false, l.n);
			if (!f.isFile()) throw new IOException(l.name + "'s papers never arrived from " + r.peerTitle + "'s station");
			byte[] pkg = SafeFiles.read(f);
			Map<String, byte[]> files = Vault.unpack(pkg);
			byte[] sav = files.get("ship.sav");
			homeplanet.parser.ShipPapers.Installed papers = null;
			if (homeplanet.parser.ShipPapers.custom(l.id)) {
				papers = homeplanet.parser.ShipPapers.install(files, sav, l.id); // her blueprint fitted in here, her save renamed to match
				sav = papers.save;
			}
			SavedGameState gs;
			try {
				gs = readSave(sav);
			} catch (IOException e) {
				if (papers != null && papers.newBlueprint) homeplanet.parser.ShipPapers.uninstall(papers.bpId);
				throw new IOException(l.name + " couldn't be read on her blueprint here: " + e.getMessage());
			}
			v.receive(pkg, sav, gs, r.id + "#" + l.n, r.peerTitle);
			if (papers != null && papers.newBlueprint) r.needsPatch = true;
		}
		if (r.needsPatch) homeplanet.core.Slipstream.writeMod(); // her blueprint goes into the Federation Home Planet Mod
		settle(r, r.in, DONE);
		for (Line l : r.out) if (l.kind == Line.Kind.SHIP) v.transferred(l.from, l.name, r.peerTitle);
		cleanUp(r);
		List<String> lines = new ArrayList<String>();
		lines.add("gave: " + r.outWords());
		String where = whereTheyGo(r.in);
		lines.add("received" + (where.isEmpty() ? "" : " (" + where + ")") + ": " + r.inWords()); // a one-sided trade receives nothing, and goes nowhere
		HistoryLog.entry("LONG RANGE TRADE", "with " + r.peerTitle + "  (trade " + r.id + ")", lines,
				Event.of("LONG_RANGE_TRADE").put("trade", r.id).put("peer", r.peerTitle).put("peer_station", r.peerStation).put("gave", r.outWords()).put("received", r.inWords()).put("received_to", where).details(lines));
		homeplanet.parser.Transmissions.deliver("trade:" + r.id, "Home Planet Quartermaster", RECEIPT_SUBJECT, receipt(r));
	}
	/** The Quartermaster's receipt: a title nobody takes for the other commander's own message. */
	public static final String RECEIPT_SUBJECT = "Receipt of Transfer: Signed by Quartermaster";
	private static final String[] RECEIPT_OPEN = {
		"The transfer from %s came through the long range channel clean.",
		"%s's station kept its end of the bargain.",
		"The long range channel to %s is quiet again, and the manifest is closed.",
		"Another transfer with %s, logged and filed.",
	};
	private static final String[] RECEIPT_CLOSE = {
		"Counted twice. Fair trade.",
		"Every crate tallied and stamped. Pleasure doing business.",
		"Inventory reconciled, in triplicate, as regulations require.",
		"Weighed, counted and initialled. The books balance.",
	};
	/** The receipt's words: which flavour a trade gets follows from its id, so a receipt reads the same every time it's made. */
	static String receipt(Record r) {
		int pick = r.id.hashCode() & 0x7fffffff;
		String where = whereTheyGo(r.in);
		return homeplanet.parser.Transmissions.rank() + ",\n\n"
				+ RECEIPT_OPEN[pick % RECEIPT_OPEN.length].replace("%s", r.peerTitle) + "\n\n"
				+ (r.in.isEmpty() ? "Nothing came back from " + r.peerTitle + ". Generous of you.\n\n"
						: "Signed for at The Home Planet Station: " + r.inWords() + ". " + capital(where.startsWith("in the") ? "all of it " + where : where) + ".\n\n")
				+ (r.out.isEmpty() ? "Nothing went out for it. I won't ask how you managed that."
						: "Sent to " + r.peerTitle + ": " + r.outWords() + ".\n\n" + RECEIPT_CLOSE[(pick / RECEIPT_OPEN.length) % RECEIPT_CLOSE.length])
				+ "\n~ Home Planet Quartermaster";
	}
	/**
	 * Any ship already received for it goes back (a completion that failed part way: the other station keeps her), this
	 * station's own lines come back, into the Cargo Hold, and the record is marked called off.
	 */
	public static void callOff(Record r, String why) throws IOException {
		if (!ESCROW.equals(r.state)) throw new IOException("This trade was already settled (" + r.state + ")");
		List<String> sentBack = new ArrayList<String>();
		for (Line l : r.in) {
			if (l.kind != Line.Kind.SHIP) continue;
			homeplanet.vault.Ship s = Vault.get().unreceive(r.id + "#" + l.n, r.peerTitle); // 5.61: she stayed, and was in both fleets
			if (s != null) sentBack.add(s.name);
		}
		for (Line l : r.out) if (l.kind == Line.Kind.SHIP) Vault.get().comeBack(l.from, SafeFiles.read(pkgFile(r, true, l.n)));
		settle(r, r.out, CALLED_OFF);
		cleanUp(r);
		List<String> lines = new ArrayList<String>();
		if (!sentBack.isEmpty()) lines.add("sent back (received before it was called off): " + String.join(", ", sentBack));
		lines.add("came back (" + whereTheyGo(r.out) + "): " + r.outWords());
		if (why != null && !why.isEmpty()) lines.add("why: " + why);
		HistoryLog.entry("TRADE CALLED OFF", "with " + r.peerTitle + "  (trade " + r.id + ")", lines,
				Event.of("TRADE_CALLED_OFF").put("trade", r.id).put("peer", r.peerTitle).put("peer_station", r.peerStation).put("why", why).put("came_back", r.outWords()).put("sent_back", sentBack.isEmpty() ? null : String.join(", ", sentBack)).details(lines));
	}
	/** A settled trade's ships' packages have done their job: only the record stays, as a receipt. */
	private static void cleanUp(Record r) {
		File f = folderOf(r.id);
		if (f.isDirectory() && !SafeFiles.deleteTree(f)) log.warn("Could not clear {}", f);
	}
	private static String capital(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
	private static void settle(Record r, List<Line> into, String state) throws IOException {
		if (!ESCROW.equals(r.state)) throw new IOException("This trade was already settled (" + r.state + ")");
		Vault v = Vault.get();
		Ship st = v.storage();
		Vault.Copy c = v.readCopy(st);
		ShipState s = c.save.getPlayerShip();
		for (Line l : into) give(s, l);
		r.state = state;
		v.begin().put(st, c.save, c.hash).put(r.file, bytes(r)).commit();
	}
	/** Puts one line into the Cargo Hold (in the copy). */
	static void give(ShipState s, Line l) throws IOException {
		if (l.kind.isSupply()) setSupply(s, l.kind, supply(s, l.kind) + l.amount);
		else if (l.kind == Line.Kind.WEAPON) s.getWeaponList().add(SaveHelper.newIdleWeapon(l.id));
		else if (l.kind == Line.Kind.DRONE) s.getDroneList().add(SaveHelper.newIdleDrone(l.id));
		else if (l.kind == Line.Kind.AUGMENT) s.getAugmentIdList().add(l.id);
		else if (l.kind == Line.Kind.CREW) {
			CrewState c = new CrewState(l.crew);
			c.setHealth(c.getRace().getMaxHealth()); // the trip home mends them
			if (!SaveHelper.placeCrew(s, c, true)) throw new IOException("The Cargo Hold has no room for " + c.getName());
			s.getCrewList().add(c);
		}
		// a ship isn't the hold's: she was received (or came back) on her own
	}

	// ---- supplies ----

	static int supply(ShipState s, Line.Kind k) {
		return k == Line.Kind.SCRAP ? s.getScrapAmt() : k == Line.Kind.FUEL ? s.getFuelAmt() : k == Line.Kind.MISSILES ? s.getMissilesAmt() : s.getDronePartsAmt();
	}
	private static void setSupply(ShipState s, Line.Kind k, int v) {
		if (k == Line.Kind.SCRAP) s.setScrapAmt(v); else if (k == Line.Kind.FUEL) s.setFuelAmt(v); else if (k == Line.Kind.MISSILES) s.setMissilesAmt(v); else s.setDronePartsAmt(v);
	}
	static String supplyName(Line.Kind k) { return k == Line.Kind.SCRAP ? "scrap" : k == Line.Kind.FUEL ? "fuel" : k == Line.Kind.MISSILES ? "missiles" : "drone parts"; }

	/** Lines in words: "Burst Laser II, 30 scrap and Ripley (Engi)"; "nothing" for none. */
	public static String words(List<Line> lines) {
		List<String> w = new ArrayList<String>();
		for (Line l : lines) w.add(l.title());
		return wordsOf(w);
	}
	static String wordsOf(List<String> w) {
		if (w.isEmpty()) return "nothing";
		if (w.size() == 1) return w.get(0);
		return String.join(", ", w.subList(0, w.size() - 1)) + " and " + w.get(w.size() - 1);
	}
}
