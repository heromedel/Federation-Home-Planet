package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import homeplanet.core.Event;
import homeplanet.core.EventLog;
import homeplanet.core.StationLog;
import homeplanet.core.SafeFiles;
import homeplanet.core.Store;
import homeplanet.model.Words;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Long Range Comm.: ships that change hands (7.02, out of Vault): the package a ship travels as, sending her away, her coming back, receiving another station's. */
final class Trades {
	private Trades() { }
	private static final Logger log = LoggerFactory.getLogger(Trades.class);

	/** What travels with a ship: her save, her voyage's last look, her last trade mark and her papers. */
	static final String[] PACKAGE = {"ship.sav", VoyageLog.LAST, TradeMark.FILE, "papers.txt"};

	/** Her record and her events in a package (5.75). */
	static final String PACKAGE_RECORD = "record.xml", PACKAGE_EVENTS = "events.log";

	/** Her crew's files from the crew register, under crew/ in her package (5.90), as they are on disk (7.0). */
	static final String PACKAGE_CREW = "crew/";

	static final int PACKAGE_MAX = 16 * 1024 * 1024;

	/** A docked ship's package for another station (a zip of {@link #PACKAGE}). */
	static byte[] packageOf(Vault v, Ship s) throws IOException {
		java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
		java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(bo);
		try {
			// her save, her last look and her trade mark: the last two from her record (5.98), as files of their own
			java.util.Properties last = ShipStore.notes(v.folderOf(s), ShipStore.LAST), mark = ShipStore.notes(v.folderOf(s), ShipStore.TRADE);
			byte[][] files = {SafeFiles.read(v.fileOf(s)), last.isEmpty() ? null : Store.bytes(last, VoyageLog.LAST_NOTE), mark.isEmpty() ? null : TradeMark.fileBytes(mark)};
			for (int i = 0; i < files.length; i++) {
				if (files[i] == null) continue;
				z.putNextEntry(new java.util.zip.ZipEntry(PACKAGE[i]));
				z.write(files[i]);
				z.closeEntry();
			}
			// her papers: when she was first commissioned, carried through every trade
			TradeMark old = TradeMark.of(v, s.id);
			String commissioned = old != null && !old.commissioned.isEmpty() ? old.commissioned : homeplanet.parser.Museum.commissioned(v, s.id);
			java.util.Properties papers = new java.util.Properties();
			papers.setProperty("commissioned", commissioned);
			z.putNextEntry(new java.util.zip.ZipEntry(PACKAGE[3]));
			z.write(Store.bytes(papers, "Her papers"));
			z.closeEntry();
			// a custom ship's blueprint and pictures: another station can't fly her without them
			SavedGameState gs = s.save();
			if (gs == null) throw new IOException(s.name + "'s save can't be read");
			for (Map.Entry<String, byte[]> e : homeplanet.parser.ShipPapers.papersOf(gs.getPlayerShipBlueprintId()).entrySet()) {
				z.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));
				z.write(e.getValue());
				z.closeEntry();
			}
			// her record (owners, past names) and her events, two lines each, as the log holds them (5.75); her record's
			// sections stay here (5.98): they are this fleet's notes on her, and what travels has its own files above
			ShipStore.Record record = v.recordOf(s);
			record.sections.clear();
			z.putNextEntry(new java.util.zip.ZipEntry(PACKAGE_RECORD));
			z.write(ShipStore.bytes(record));
			z.closeEntry();
			StringBuilder events = new StringBuilder();
			for (EventLog.Entry e : EventLog.voyage(ShipStore.entries(v.folderOf(s)), s.id)) events.append(EventLog.text(e)); // her own log (5.76)
			z.putNextEntry(new java.util.zip.ZipEntry(PACKAGE_EVENTS));
			z.write(events.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			z.closeEntry();
			File[] crew = new File(v.folderOf(s), CrewRegister.CREW_DIR).listFiles(); // her crew's files: their past goes with them (5.90)
			if (crew != null) {
				java.util.Arrays.sort(crew);
				for (File c : crew) {
					if (!c.isFile() || !c.getName().endsWith(".xml")) continue;
					z.putNextEntry(new java.util.zip.ZipEntry(PACKAGE_CREW + c.getName()));
					z.write(SafeFiles.read(c));
					z.closeEntry();
				}
			}
		} finally {
			z.close();
		}
		return bo.toByteArray();
	}

	/** A crew member's file in a package: crew/ and a plain file name, nothing that could climb out of her folder. */
	static boolean crewEntry(String name) {
		if (!name.startsWith(PACKAGE_CREW) || !name.endsWith(".xml")) return false;
		String f = name.substring(PACKAGE_CREW.length());
		return !f.isEmpty() && f.indexOf('/') < 0 && f.indexOf('\\') < 0 && !f.contains("..") && !f.startsWith(".");
	}

	/** A package's files by name: only the ones a package holds, each within its limit. */
	static Map<String, byte[]> unpack(byte[] pkg) throws IOException {
		Map<String, byte[]> out = new LinkedHashMap<String, byte[]>();
		java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(pkg));
		try {
			int total = 0;
			for (java.util.zip.ZipEntry e; (e = z.getNextEntry()) != null;) {
				if (out.containsKey(e.getName())) throw new IOException("a ship's package names " + e.getName() + " twice");
				boolean known = java.util.Arrays.asList(PACKAGE).contains(e.getName()) || e.getName().equals(homeplanet.parser.ShipPapers.BLUEPRINT)
						|| e.getName().startsWith(homeplanet.parser.ShipPapers.ART) || e.getName().equals(PACKAGE_RECORD) || e.getName().equals(PACKAGE_EVENTS)
						|| crewEntry(e.getName());
				java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
				byte[] buf = new byte[8192];
				for (int n; (n = z.read(buf)) > 0;) {
					b.write(buf, 0, n);
					total += n;
					if (total > PACKAGE_MAX) throw new IOException("a ship's package is too large");
				}
				if (known) out.put(e.getName(), b.toByteArray()); // a newer station's extra files are counted against the limit, then left out
			}
		} finally {
			z.close();
		}
		if (!out.containsKey(PACKAGE[0])) throw new IOException("a ship's package has no save in it");
		return out;
	}

	/** She leaves the fleet in a trade's escrow: her save goes into her history, and her fate says where she went. */
	static void sendAway(Vault v, Ship s, String to) throws IOException {
		if (s.state != Ship.State.DOCKED) throw new IOException(s.name + " isn't docked");
		v.leave(s, Vault.Fate.TRANSFERRED, to);
		v.ships.remove(s);
		v.saveManifest();
		StationLog.entry("SENT AWAY", s.name + " (" + s.id + ") to " + to + "'s fleet, over Long Range Comm.", null, Vault.shipEvent("SENT_AWAY", s).put("to_commander", to).put("to", v.place(s)));
	}

	/** A trade with her in it was called off: she comes back docked, from her package (unless she's here already). */
	static Ship comeBack(Vault v, String id, byte[] pkg) throws IOException {
		Ship here = v.byId(id);
		if (here != null) return here;
		byte[] sav = unpack(pkg).get(PACKAGE[0]);
		Ship s = new Ship(id, "", Ship.State.DOCKED, true);
		File was = v.folders.get(id);
		File to = ShipStore.sav(v.comeHome(s, was != null && was.isDirectory() ? was : v.settleFolder(s), sav)); // her folder from the memorial (or made afresh), as one note (5.88)
		s.hash = SafeFiles.hash(to);
		v.ships.add(s);
		s.save();
		if (s.name == null || s.name.isEmpty()) s.name = "Unknown ship";
		v.saveManifest();
		StationLog.entry("RETURNED", s.name + " (" + id + "): the trade was called off, and " + Words.she() + " is back at the Space Dock", null, Vault.shipEvent("RETURNED", s).put("why", "trade_called_off").put("to", v.place(s)));
		return s;
	}

	/**
	 * A trade called off after this ship had already been received for it (a later ship's papers missing, say): she goes
	 * back as sent away, since the other station keeps her (5.61: she stayed, and was in both fleets). Her save goes into
	 * her history, and her fate says where she went. Only a ship still docked: one flown since is left, and logged.
	 * Returns her, or null if no ship came for this trade line.
	 */
	static Ship unreceive(Vault v, String tradeLine, String to) throws IOException {
		for (Ship s : new ArrayList<Ship>(v.ships)) {
			TradeMark m = TradeMark.of(v, s.id);
			if (m == null || !m.trade.equals(tradeLine)) continue;
			if (s.state != Ship.State.DOCKED) {
				log.warn("{} came in trade line {}, which was called off, but isn't docked any more: she stays", s, tradeLine);
				StationLog.entry("TRADE CALLED OFF", s.name + " (" + s.id + ") came in it and isn't docked any more, so " + Words.she() + " stays here as well as with " + to + "'s fleet", null,
						Vault.shipEvent("TRADE_CALLED_OFF", s).put("what", "stays_both").put("trade", tradeLine).put("peer", to));
				return null;
			}
			v.leave(s, Vault.Fate.TRANSFERRED, to);
			v.ships.remove(s);
			v.saveManifest();
			StationLog.entry("SENT BACK", s.name + " (" + s.id + "): the trade was called off, and " + Words.she() + " stays with " + to + "'s fleet", null, Vault.shipEvent("SENT_BACK", s).put("trade", tradeLine).put("to_commander", to));
			return s;
		}
		return null;
	}

	/** The trade went through: she flies for the other fleet now (her history stays here, as a record). */
	static void transferred(Vault v, String id, String name, String to) throws IOException {
		Ship still = v.byId(id);
		if (still != null && still.state == Ship.State.DOCKED) sendAway(v, still, to); // escrow didn't get as far as sending her
		writeFate(v, id, Vault.Fate.TRANSFERRED, name, to);
	}

	static void writeFate(Vault v, String id, Vault.Fate fate, String name, String detail) throws IOException {
		File dir = v.folders.containsKey(id) ? v.folders.get(id) : new File(v.memorialDir(), ShipStore.stem(name, id));
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		v.folders.put(id, dir);
		v.setNotes(dir, ShipStore.FATE, ShipStore.fateNotes(fate.name(), name, detail));
	}

	/**
	 * A ship arriving from another station, from her package: docked under a new id, her voyage log kept, a trade
	 * mark (see {@link TradeMark}) naming the sender and her original owner, and set out at The Home Planet Station as
	 * a newly commissioned ship is. A ship already received for this trade line isn't received twice.
	 */
	static Ship receive(Vault v, byte[] pkg, byte[] save, SavedGameState gs, String tradeLine, String from) throws IOException {
		for (Ship s : v.ships) {
			TradeMark m = TradeMark.of(v, s.id);
			if (m != null && m.trade.equals(tradeLine)) return s;
		}
		Map<String, byte[]> files = unpack(pkg);
		try { save = ShipMark.strip(save); } // her old station's mark off: this one marks her at her first Board here (6.08)
		catch (IOException e) { log.warn("Could not take her old station's mark off {}: {}", gs.getPlayerShipName(), e.toString()); }
		Ship s = new Ship(Vault.newId(), gs.getPlayerShipName(), Ship.State.DOCKED, gs.isDLCEnabled());
		s.origin = Strays.originNow("received");
		File dir = v.settleFolder(s);
		try {
			// her files in one note (5.88): her save as it arrived (or renamed onto a blueprint here, homeplanet.parser.ShipPapers), her
			// record with her last look and her trade mark (5.98: they were files of their own), her owners and past names from the record she came with (5.75)
			ActionProtection.Note note = ActionProtection.begin(v, "RECEIVE");
			String original = TradeMark.originalIn(files.get(TradeMark.FILE));
			String commissioned = papersCommissioned(files.get(PACKAGE[3]));
			int sectors = VoyageLog.visited(v, s.id, gs);
			ShipStore.Record record = v.recordOf(s);
			if (files.containsKey(PACKAGE_RECORD)) { // her id, name and state are this fleet's; her sections are her old fleet's notes on her, and stay there
				try {
					ShipStore.Record theirs = ShipStore.parse(files.get(PACKAGE_RECORD), s.id);
					for (String o : theirs.owners) if (!record.owners.contains(o)) record.owners.add(o);
					for (String n : theirs.pastNames) if (!record.pastNames.contains(n)) record.pastNames.add(n);
				} catch (IOException e) { log.warn("{}'s record didn't travel well: {}", s.name, e.toString()); }
			}
			if (files.containsKey(VoyageLog.LAST)) {
				try { record.section(ShipStore.LAST).putAll(Store.parse(files.get(VoyageLog.LAST))); }
				catch (IOException e) { log.warn("{}'s last look didn't travel well: {}", s.name, e.toString()); }
			}
			record.section(ShipStore.TRADE).putAll(TradeMark.mark(tradeLine, from, original == null ? from : original, commissioned, gs, sectors));
			note.replace(ShipStore.xml(dir), ShipStore.bytes(record));
			File f = v.fileOf(s);
			note.replace(f, save);
			File arrived = new File(new File(dir, CrewRegister.CREW_DIR), CrewRegister.ARRIVED); // her crew's files from the other station, taken in as the register meets each (5.90)
			for (Map.Entry<String, byte[]> c : files.entrySet()) if (crewEntry(c.getKey())) note.replace(new File(arrived, c.getKey().substring(PACKAGE_CREW.length())), c.getValue());
			note.commit();
			s.hash = SafeFiles.hash(f);
			v.ships.add(s);
			if (files.containsKey(PACKAGE_EVENTS)) { // her events, under her new id (5.75); their time is their own, their day this career's today
				Event who = VoyageLog.shipFields(s).put("received_from", from);
				for (EventLog.Entry e : EventLog.parse(new String(files.get(PACKAGE_EVENTS), java.nio.charset.StandardCharsets.UTF_8))) {
					Event x = Event.of(e.kind).put("log", "voyage").putAll(who);
					for (String[] kv : e.fields()) if (!kv[0].matches("log|ship|ship_name|ship_id|ship_state|ship_sector|ship_difficulty|day|station|received_from")) x.put(kv[0], kv[1]); // where she was comes with who she is (6.47: it doubled with each trade)
					if (x.get("time") == null) x.put("time", e.time);
					EventLog.write(v, x.human(e.human));
				}
			}
			v.setOut(s, gs, "Received from " + from + "'s fleet at The Home Planet Station");
			homeplanet.parser.Museum.setCommissioned(v, s.id, commissioned); // her own date, not her arrival
		} catch (IOException e) {
			v.ships.remove(s);
			SafeFiles.deleteTree(dir);
			v.folders.remove(s.id);
			try { v.saveManifest(); } catch (IOException again) { log.error("Could not write the records", again); }
			throw e;
		}
		StationLog.entry("RECEIVED", s.name + " (" + s.id + ") from " + from + "'s fleet, over Long Range Comm.: docked", null, Vault.shipEvent("RECEIVED", s).put("from_commander", from).put("trade", tradeLine).put("to", v.place(s)));
		return s;
	}

	/** The commission date a ship's papers give, or "". */
	static String papersCommissioned(byte[] papers) {
		if (papers == null) return "";
		java.util.Properties p;
		try { p = Store.parse(papers); } catch (IOException e) { return ""; }
		String d = p.getProperty("commissioned", "").trim();
		return d.length() > 40 ? "" : d;
	}
}
