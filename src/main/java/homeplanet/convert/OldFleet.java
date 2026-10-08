package homeplanet.convert;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.Event;
import homeplanet.core.HistoryLog;
import homeplanet.core.SafeFiles;
import homeplanet.core.Store;
import homeplanet.vault.Clock;
import homeplanet.vault.CrewRegister;
import homeplanet.vault.Journal;
import homeplanet.vault.ShipStore;
import homeplanet.vault.Vault;

/**
 * A fleet from before 6.0 brought across, the first time it opens (5.97; docs/OVERHAUL-6.md). Everything that reads a
 * fleet's old shapes lives in this package, so that once no one has a fleet from before 6.0 the package can go whole:
 * the compiler then points at each call into it, and at the few things outside it marked {@link Before6}. A fleet
 * would then need a 6.x station to open it once first.
 *
 * The steps, in the order they run, each doing nothing when its old files aren't there (so a 6.0 fleet passes straight
 * through), and each its own journal note, finished at the next opening if the station stops partway:
 * <ol>
 * <li>the logs from the fleet's root into logs/ (5.71);</li>
 * <li>the career's small files into xml, one per concern, the clock's five files into clock.xml (5.86);</li>
 * <li>manifest.xml, ships/, junkyard/ and history/ into a folder per ship, the whole fleet zipped beside it first ({@link Layout}, 5.69);</li>
 * <li>the Cargo Hold's files from the root into cargohold/ (5.72), and its save into cargohold.xml (5.84);</li>
 * <li>the expeditions', the infirmary's and the captives' files into folders of their own, as xml (5.85);</li>
 * <li>the old logs read into the event log, each ship's entries into her own log, and put on their own days ({@link LogConvert}, 5.73, 5.76, 5.81);</li>
 * </ol>
 * then the ships are read, and after them:
 * <ol start="7">
 * <li>the crew register's places in the old logs as places in the event log (5.91), and its crew.txt into a file per crew member (5.83).</li>
 * </ol>
 * A ship from an older station in a trade is the other way in ({@link OldPackage}).
 */
public final class OldFleet {
	private static final Logger log = LoggerFactory.getLogger(OldFleet.class);
	private OldFleet() { }

	/** Steps 1 to 6: before the ships are read. */
	public static void before(Vault v) throws IOException {
		moveLogs(v);
		moveSmallFiles(v); // first of the moves after the logs: every log entry after it reads the clock
		if (manifest(v.root).isFile() || shipsDir(v.root).isDirectory() || historyDir(v.root).isDirectory()) Layout.convert(v);
		moveCargoHold(v);
		holdReady(v);
		moveExpeditions(v);
		LogConvert.run(v); // the old logs read into the event log once (5.73)
		LogConvert.fillShipLogs(v); // each ship's entries into her own log, once (5.76)
		LogConvert.repairDays(v); // converted entries put on their own days, once (5.81)
	}
	/** Step 7: after the ships, whose folders hold their crew. */
	public static void after(Vault v) {
		CrewRegister.convertPositions(v); // a register from before 5.91: how far it had read the old logs, as places in the event log
		CrewRegister.convert(v); // a 5.x crew.txt into a file per crew member, once (5.83)
	}

	// ---- the old places ----

	/** The fleet's index before 5.69. */
	public static final String MANIFEST = "manifest.xml";
	static File manifest(File fleetRoot) { return new File(fleetRoot, MANIFEST); }
	/** The old layout's folders (before 5.69): ships/ and junkyard/ held the saves, history/ each ship's kept versions and side files. */
	public static final String SHIPS = "ships";
	static File shipsDir(File fleetRoot) { return new File(fleetRoot, SHIPS); }
	static File historyDir(File fleetRoot) { return new File(fleetRoot, "history"); }
	/** The names of the station's logs, at the fleet's root before 5.71. */
	static final String[] LOG_FILES = {"history.log", "master.log", "events.log", "reputation.log"};
	/** The Cargo Hold's save at the root before 5.72, and in cargohold/ from 5.72 to 5.83. */
	static final String STORAGE_FILE = Vault.STORAGE_ID + ".sav", HOLD_SAV = "cargohold.sav";

	/** Another fleet (not in use) not yet opened since 5.69: it still adopts saves from its old folders. */
	public static boolean notConverted(File fleetRoot) { return manifest(fleetRoot).isFile() || shipsDir(fleetRoot).isDirectory(); }
	/** Ship ids and names from another fleet's manifest (empty if there's none, or it can't be read). */
	public static Map<String, String> manifestNames(File fleetRoot) {
		Map<String, String> out = new LinkedHashMap<String, String>();
		File manifest = manifest(fleetRoot);
		if (!manifest.isFile()) return out;
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest);
			NodeList list = doc.getElementsByTagName("ship");
			for (int i = 0; i < list.getLength(); i++) {
				Element e = (Element) list.item(i);
				out.put(e.getAttribute("id"), e.getAttribute("name"));
			}
		} catch (Exception e) {
			log.warn("Could not read {}: {}", manifest, e.toString());
		}
		return out;
	}
	/** Another fleet's Cargo Hold file if that fleet keeps it the old way (cargohold.sav from 5.72, storage.sav at the root before), else null. */
	public static File holdFileIn(File fleetRoot) {
		File d = new File(fleetRoot, Vault.HOLD_DIR), sav = new File(d, HOLD_SAV), xml = new File(d, Vault.HOLD_FILE), old = new File(fleetRoot, STORAGE_FILE);
		if (sav.isFile()) return sav; // not opened since 5.84: the xml beside it is still its record
		return !xml.isFile() && old.isFile() ? old : null;
	}
	/** Another fleet's station log in prose (logs/ since 5.71, the root before), for a fleet not opened since 5.73. */
	public static File historyLogIn(File fleetRoot) {
		File f = new File(new File(fleetRoot, "logs"), "history.log");
		return f.isFile() || !new File(fleetRoot, "history.log").isFile() ? f : new File(fleetRoot, "history.log");
	}

	// ---- the steps ----

	/** The station's logs from the root into logs/ (5.71), as one journal note: all-or-nothing, finished at the next opening if interrupted. */
	private static void moveLogs(Vault v) throws IOException {
		Journal.Note n = Journal.begin(v, "MOVE_LOGS");
		for (String name : LOG_FILES) {
			File old = new File(v.root, name), now = new File(v.logsDir(), name);
			if (old.isFile() && !now.exists()) n.rename(old, now);
		}
		if (!n.isEmpty()) n.commit();
	}
	/** The career's small files that became xml at 5.86, by stem (each owner names its file with {@link Store#file}). */
	static final String[] SMALL_FILES = {"career", "reputation", "rest", "rank", "repair-job", "events", "crew-register"};
	/**
	 * The career's small files into xml, one per concern (5.86), as one journal note: each properties file at the root
	 * becomes its .xml (nothing in it changed, its comment kept), and the five files of the fleet's clock become one,
	 * clock.xml ({@link Clock}). The lists that aren't settings (free-command.txt, unlock-grants.txt) stay as they are.
	 */
	private static void moveSmallFiles(Vault v) throws IOException {
		Journal.Note n = Journal.begin(v, "MOVE_SMALL_FILES");
		List<String> moved = new ArrayList<String>();
		for (String stem : SMALL_FILES) {
			File txt = new File(v.root, stem + ".txt"), xml = new File(v.root, stem + ".xml");
			if (!txt.isFile() || xml.exists()) continue;
			byte[] b = SafeFiles.read(txt);
			String comment = null;
			for (String line : new String(b, java.nio.charset.StandardCharsets.UTF_8).split("\\r?\\n", 3)) if (line.startsWith("#")) { comment = line.substring(1).trim(); break; }
			n.replace(xml, Store.xml(Store.parse(b), comment));
			n.delete(txt);
			moved.add(txt.getName() + ">" + xml.getName());
		}
		Properties clock = Clock.file(v).exists() ? null : oldClock(v);
		if (clock != null) n.replace(Clock.file(v), Store.xml(clock, Clock.NOTE));
		for (String old : CLOCK_FILES) if (new File(v.root, old).isFile()) { n.delete(new File(v.root, old)); moved.add(old + ">" + Clock.FILE); } // already taken in, if the clock was written first
		if (n.isEmpty()) return;
		n.commit();
		Event e = Event.of("SMALL_FILES").put("what", "moved").put("files", moved.size());
		for (int i = 0; i < moved.size(); i++) e.put("file." + i, moved.get(i));
		HistoryLog.entry("SMALL_FILES", "the career's small files written as xml, one per concern: " + String.join(", ", moved), null,
				e.human("The station's records were tidied into one file for each concern."));
	}
	/** The five 5.x files a fleet's clock was, at its root. */
	static final String[] CLOCK_FILES = {"sectors.txt", "beacons.txt", "clock.txt", "work.txt", "stardate.txt"};
	/**
	 * The 5.x files' contents as the clock's keys; null if the fleet has none of them. Read for the move, and by the
	 * clock itself until the move is done (the career's day 1 is asked for before the fleet loads).
	 */
	public static Properties oldClock(Vault v) throws IOException {
		boolean any = false;
		for (String n : CLOCK_FILES) any |= new File(v.root, n).isFile();
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
		String s = new String(SafeFiles.read(f), java.nio.charset.StandardCharsets.UTF_8).trim();
		return s.isEmpty() ? null : s;
	}
	/** The Cargo Hold's files from the root into cargohold/ (5.72), as one journal note: its save, its record, its lists and its versions. */
	private static void moveCargoHold(Vault v) throws IOException {
		File hold = v.cargoHoldDir();
		Journal.Note n = Journal.begin(v, "MOVE_CARGO_HOLD");
		String[][] files = {{STORAGE_FILE, HOLD_SAV}, {Vault.STORAGE_ID + ".xml", Vault.HOLD_STEM + ".xml"}, {"storage-systems.txt", "systems.txt"}, {"parts.txt", "parts.txt"}, {"overflow.txt", "overflow.txt"}};
		for (String[] f : files) {
			File old = new File(v.root, f[0]), now = new File(hold, f[1]);
			if (old.isFile() && !now.exists()) n.rename(old, now);
		}
		File oldVersions = new File(new File(v.shipyardDir(), ShipStore.stem("Spacedock Storage", Vault.STORAGE_ID)), ShipStore.VERSIONS); // 5.69 to 5.71 kept its snapshots there
		if (oldVersions.isDirectory() && !ShipStore.versions(hold).exists()) n.rename(oldVersions, ShipStore.versions(hold));
		if (n.isEmpty()) return;
		n.commit();
		if (oldVersions.getParentFile().isDirectory()) oldVersions.getParentFile().delete(); // empty now
	}
	/** The fleets whose hold is being converted just now (reading its save asks for the hold again). */
	private static final Set<File> CONVERTING = new HashSet<File>();
	/**
	 * The Cargo Hold's pretend ship into its xml (5.84), once, as one journal note: cargohold.sav read, cargohold.xml
	 * (its record until now) written with what it holds, the save gone. Its kept versions stay as they are. A fleet
	 * whose hold has a record and no save gets a new hold when it is next asked for, as before. Asked on opening and
	 * at every look at the hold, since the save can't be read until FTL's blueprints are.
	 */
	public static void holdReady(Vault v) {
		synchronized (v) {
			File sav = new File(v.cargoHoldDir(), HOLD_SAV), xml = new File(v.cargoHoldDir(), Vault.HOLD_FILE);
			File key = v.root.getAbsoluteFile();
			if (CONVERTING.contains(key) || !sav.isFile() && !xml.isFile()) return;
			if (!sav.isFile()) {
				if (!homeplanet.parser.HoldXml.isHold(xml) && !xml.delete()) log.warn("Could not remove the Cargo Hold's old record {}", xml);
				return;
			}
			if (homeplanet.parser.HoldXml.isHold(xml)) return; // both: the xml is the hold, the save an old copy left beside it (kept, not read)
			if (net.blerf.ftl.parser.DataManager.get() == null) return; // FTL's blueprints aren't read yet: the next look at the hold converts it
			CONVERTING.add(key);
			try { convertCargoHold(v, sav, xml); }
			catch (Exception e) { log.warn("Could not give the Cargo Hold its xml ({} stays as it is): {}", sav, e.toString()); }
			finally { CONVERTING.remove(key); }
		}
	}
	/** The hold's 5.x save, if it is still waiting to be converted (it couldn't be read). */
	public static File holdWaiting(Vault v) {
		File sav = new File(v.cargoHoldDir(), HOLD_SAV);
		return sav.isFile() && !homeplanet.parser.HoldXml.isHold(new File(v.cargoHoldDir(), Vault.HOLD_FILE)) ? sav : null;
	}
	private static void convertCargoHold(Vault v, File sav, File xml) throws IOException {
		SavedGameState gs = new net.blerf.ftl.parser.SavedGameParser().readSavedGame(sav);
		Journal.Note n = Journal.begin(v, "CONVERT_CARGO_HOLD");
		n.replace(xml, homeplanet.parser.HoldXml.toBytes(gs));
		n.delete(sav);
		n.commit();
		net.blerf.ftl.parser.SavedGameParser.ShipState p = gs.getPlayerShip();
		int items = p.getWeaponList().size() + p.getDroneList().size() + p.getAugmentIdList().size() + (gs.getCargoIdList() == null ? 0 : gs.getCargoIdList().size());
		HistoryLog.entry("HOLD_FILE", "the Cargo Hold's contents were written into cargohold.xml (" + p.getScrapAmt() + " scrap, " + items + " items, " + p.getCrewList().size() + " crew)", null,
				Event.of("HOLD_FILE").put("what", "converted").put("scrap", p.getScrapAmt()).put("fuel", p.getFuelAmt()).put("missiles", p.getMissilesAmt()).put("drone_parts", p.getDronePartsAmt())
						.put("items", items).put("crew", p.getCrewList().size()).put("folder", Vault.HOLD_DIR).put("file", Vault.HOLD_DIR + "/" + Vault.HOLD_FILE)
						.human("The Cargo Hold's inventory was written up in a new ledger."));
	}
	/**
	 * The expeditions', the infirmary's and the captives' files from the root into folders of their own, as xml (5.85),
	 * as one journal note; the old board of jobs (expeditions.txt, unread since 5.67) is kept beside them.
	 */
	private static void moveExpeditions(Vault v) throws IOException {
		File[][] files = {{new File(v.root, "assignments.txt"), homeplanet.parser.Assignments.file(v)},
				{new File(v.root, "infirmary.txt"), homeplanet.parser.Expeditions.infirmaryFile(v)},
				{new File(v.root, "captives.txt"), homeplanet.parser.Expeditions.captivesFile(v)}};
		String[] notes = {homeplanet.parser.Assignments.NOTE, homeplanet.parser.Expeditions.INFIRMARY_NOTE, homeplanet.parser.Expeditions.CAPTIVES_NOTE};
		File board = new File(v.root, "expeditions.txt"), boardNow = new File(v.expeditionsDir(), "board-before-5.67.txt");
		Journal.Note n = Journal.begin(v, "MOVE_EXPEDITIONS");
		List<String> moved = new ArrayList<String>();
		for (int i = 0; i < files.length; i++) {
			File old = files[i][0], now = files[i][1];
			if (!old.isFile() || now.exists()) continue;
			File dir = now.getParentFile();
			if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
			n.replace(now, Store.xml(Store.load(old), notes[i]));
			n.delete(old);
			moved.add(old.getName() + ">" + dir.getName() + "/" + now.getName());
		}
		if (board.isFile() && !boardNow.exists()) {
			if (!v.expeditionsDir().isDirectory() && !v.expeditionsDir().mkdirs()) throw new IOException("Could not create " + v.expeditionsDir());
			n.rename(board, boardNow);
			moved.add(board.getName() + ">" + v.expeditionsDir().getName() + "/" + boardNow.getName());
		}
		if (n.isEmpty()) return;
		n.commit();
		Event e = Event.of("EXPEDITION_FILES").put("what", "moved").put("files", moved.size());
		for (int i = 0; i < moved.size(); i++) e.put("file." + i, moved.get(i));
		HistoryLog.entry("EXPEDITION_FILES", "the expeditions', the infirmary's and the captives' files moved into folders of their own: " + String.join(", ", moved), null,
				e.human("The expeditions office, the infirmary and the captives' records were filed in rooms of their own."));
	}
}
