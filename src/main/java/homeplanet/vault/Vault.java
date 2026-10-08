package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.Event;
import homeplanet.core.EventLog;
import homeplanet.core.HistoryLog;
import homeplanet.core.SafeFiles;
import homeplanet.core.Store;
import homeplanet.parser.SaveHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The station's vault: a folder inside FTL's saves folder that holds every ship the player owns, apart from the
 * one FTL is flying (continue.sav, in FTL's folder as always).
 *
 * <pre>
 *   FederationHomePlanet/
 *     shipyard/&lt;Name&gt;.&lt;id&gt;/      a docked (or boarded) ship: her record (&lt;Name&gt;.&lt;id&gt;.xml, with her notes in it since 5.98:
 *                                her trade mark, journey, museum entry, last look, fate and the rest), her save (.sav), her log
 *                                (.log), and versions/ (the last KEEP, written before every change, and the
 *                                copies kept for a reason of their own: victory-, final-battle-, cloud-)
 *     junkyard/&lt;Name&gt;.&lt;id&gt;/      a disbanded ship, the same
 *     memorials_and_records/ships/&lt;Name&gt;.&lt;id&gt;/   a ship that left the fleet (how, in her record's fate), remembered
 *     <ship folder>/crew/        her crew, a file each (5.83: <Name>.<id>.xml, the crew register's id); the same in cargohold/crew/,
 *                                expeditions/crew/, captives/crew/ and memorials_and_records/crew/ (everyone who left); crew-register.xml the register's own state
 *     expeditions/               the crew expeditions (5.85): expeditions.xml (the sectors on offer, the crew away), crew/,
 *                                board-before-5.67.txt (the old board of jobs, kept, unread)
 *     infirmary/                 infirmary.xml (who is laid up and until when; they stay in the Cargo Hold) (5.85)
 *     captives/                  captives.xml (who was taken, the ransoms asked), crew/ (5.85)
 *     cargohold/                 the Cargo Hold (5.72): cargohold.xml (what it holds, 5.84; the pretend ship's save cargohold.sav
 *                                before), crew/, systems.txt (its stored systems), parts.txt, overflow.txt, versions/
 *     logs/                      the station's own logs (5.71): events.log (every entry, two lines each, since 5.63, the older
 *                                ones read in once at 5.73; the only log written since 5.93), converted.txt (the marks); a fleet
 *                                from before keeps its history.log, master.log and reputation.log as they were, unwritten
 *     set-aside/                 a continue.sav marked as another career's ship (6.08, ShipMark), set aside rather than taken in
 *     station-action-protection/ the notes of actions under way (5.71; heromedel's name, 5.78), only its why.txt when the station is at rest
 *     clock.xml                  the fleet's clock (5.86; five .txt files before): the sectors and beacons travelled, the day 1
 *     career.xml, reputation.xml, rest.xml, rank.xml, repair-job.xml, events.xml, crew-register.xml: the career's own state,
 *                                one file per concern (5.86; .txt before); free-command.txt and unlock-grants.txt, two lists
 *     designs.xml, remodels.xml, art/, removed-blueprints.log
 * </pre>
 *
 * A fleet from before 6.0 (manifest.xml, ships/&lt;id&gt;.sav, history/&lt;id&gt;/ before 5.69, and the files each later step
 * moved) is brought across the first time it opens, a zip of it as it was kept beside the folder: every step of that,
 * and the readers of a fleet not yet brought across, is in homeplanet.convert ({@link homeplanet.convert.OldFleet}), to go
 * whole one day. Old files a converted fleet still keeps (a hold's versions kept as saves, a surrender from before 5.69)
 * are read where they are used.
 *
 * Each Immersive career has a fleet of its own, by difficulty: FederationHomePlanet-Immersive-Easy, -Normal and -Hard,
 * and FederationHomePlanet-Immersive for Custom (the first Immersive fleet, from before difficulties). The same layout,
 * less the blueprint files: designs, remodels, their art and blueprint backups stay in FederationHomePlanet, shared by
 * every fleet, since FTL has one Federation Home Planet Mod for all.
 *
 * Board copies a ship's file to continue.sav; Dock copies it back. Every write of a ship's save is preceded by
 * a snapshot into her history folder, so the last KEEP versions can always be recovered by hand.
 */
public final class Vault {
	private static final Logger log = LoggerFactory.getLogger(Vault.class);

	public static final String FOLDER = "FederationHomePlanet";
	/** The Immersive Custom career's fleet (the first Immersive fleet, from before difficulties). */
	public static final String IMMERSIVE_FOLDER = "FederationHomePlanet-Immersive";
	/** The game modes, each with a fleet of its own: Sandbox Mode, and the Immersive careers by difficulty. */
	public static final String SANDBOX = "sandbox", EASY = "easy", NORMAL = "normal", HARD = "hard", CUSTOM = "custom";
	public static final String[] SLOTS = {SANDBOX, EASY, NORMAL, HARD, CUSTOM};
	/** A slot from the cfg (an unknown one is Custom, the Immersive fleet from before difficulties). */
	public static String slotOf(String s) {
		for (String k : SLOTS) if (k.equals(s) && !SANDBOX.equals(k)) return k;
		return CUSTOM;
	}
	/** A mode's fleet folder's name. */
	public static String folderOf(String slot) {
		if (SANDBOX.equals(slot)) return FOLDER;
		if (CUSTOM.equals(slot)) return IMMERSIVE_FOLDER;
		return IMMERSIVE_FOLDER + "-" + Character.toUpperCase(slot.charAt(0)) + slot.substring(1);
	}
	/** A mode's fleet folder in this saves folder. */
	public static File rootOf(File savesFolder, String slot) { return new File(savesFolder, folderOf(slot)); }
	/** A mode's name: "Sandbox Mode", "Immersive Easy"... */
	public static String title(String slot) {
		if (SANDBOX.equals(slot)) return "Sandbox Mode";
		return "Immersive " + Character.toUpperCase(slot.charAt(0)) + slot.substring(1);
	}
	/** The Immersive career Immersive Mode means when no other is named (the one last used). */
	public static String immersiveSlot = CUSTOM;
	/** In a fleet's folder: the ship that was boarded when the player switched to the other fleet, boarded again on return. */
	private static final String PARKED = "parked-boarded.txt";
	/** How many earlier versions of a ship's save are kept. */
	public static final int KEEP = 10;

	private static Vault instance;
	public static Vault get() {
		if (instance == null) throw new IllegalStateException("The vault hasn't been opened yet");
		return instance;
	}
	public static boolean isOpen() { return instance != null; }
	/** Opens (creating if needed) the normal fleet's vault inside this saves folder and makes it the one in use. */
	public static Vault open(File savesFolder) throws IOException {
		return open(savesFolder, false);
	}
	/** Opens the normal fleet's vault, or Immersive Mode's (the career last used), and makes it the one in use. */
	public static Vault open(File savesFolder, boolean immersive) throws IOException {
		return open(savesFolder, immersive ? immersiveSlot : SANDBOX);
	}
	/** Opens this mode's fleet and makes it the one in use. */
	public static Vault open(File savesFolder, String slot) throws IOException {
		Vault v = new Vault(savesFolder, slot);
		instance = v; // before load(), so what load() logs goes into the vault's own log
		MasterLog.start(v); // the career's day 1, the first time it's opened on 5.17 (a new one: its first moment)
		v.load();
		return v;
	}

	public final File saves;
	/** This fleet's folder. */
	public final File root;
	/** Where the blueprint files shared by both fleets are: the normal vault's folder. */
	public final File shared;
	/** An Immersive career's fleet. */
	public final boolean immersive;
	/** Which mode's fleet: SANDBOX, EASY, NORMAL, HARD or CUSTOM. */
	public final String slot;
	private final List<Ship> ships = new ArrayList<Ship>();

	private Vault(File savesFolder, String slot) {
		this.saves = savesFolder;
		this.slot = SANDBOX.equals(slot) ? SANDBOX : slotOf(slot);
		this.immersive = !SANDBOX.equals(this.slot);
		this.shared = new File(savesFolder, FOLDER);
		this.root = rootOf(savesFolder, this.slot);
	}
	/** The other fleet's folder: from an Immersive career the Sandbox one; from Sandbox Mode the Immersive career last used. */
	public File otherRoot() { return immersive ? shared : rootOf(saves, immersiveSlot); }
	/** How many ships a fleet folder holds (docked, boarded when it was left, and in the Junkyard), without opening it. */
	public static int shipCount(File fleetRoot) {
		int n = 0;
		for (String dir : new String[] {homeplanet.convert.OldFleet.SHIPS, "junkyard", "shipyard"}) {
			File[] fs = new File(fleetRoot, dir).listFiles();
			if (fs != null) for (File f : fs) {
				if (f.isFile() && f.getName().endsWith(".sav")) n++; // the old layout (before 5.69), or a stray
				else if (f.isDirectory() && ShipStore.sav(f).isFile()) n++; // her folder
			}
		}
		return n;
	}
	/** Every other mode's fleet folder that exists. */
	public List<File> otherRoots() {
		List<File> out = new ArrayList<File>();
		for (String k : SLOTS) if (!k.equals(slot) && rootOf(saves, k).isDirectory()) out.add(rootOf(saves, k));
		return out;
	}

	// ---- places ----

	public File continueFile() { return new File(saves, "continue.sav"); }
	/** The ships at the Space Dock, a folder each (docs/OVERHAUL-6.md §3.1; 5.69). */
	public File shipyardDir() { return new File(root, "shipyard"); }
	/** The hulls in the Junkyard, a folder each. */
	public File junkyardDir() { return new File(root, "junkyard"); }
	/** Every ship that left the fleet, remembered: a folder each, with how she left (fate.txt) and her kept versions. */
	public File memorialDir() { return new File(new File(root, "memorials_and_records"), "ships"); }
	/** Her folder by id, for every ship the fleet has or remembers (filled on opening, kept up as ships move). */
	private final Map<String, File> folders = new LinkedHashMap<String, File>();
	/** Where a ship's folder sits by her state. */
	private File parentOf(Ship.State state) { return state == Ship.State.JUNKED ? junkyardDir() : shipyardDir(); }
	/** Her folder: the one known for her id, else the one she'd have where her state puts her. */
	public File folderOf(Ship s) {
		if (s.state == Ship.State.STORAGE) return cargoHoldDir();
		File f = folders.get(s.id);
		return f != null ? f : new File(parentOf(s.state), ShipStore.stem(s.name, s.id));
	}
	/** A ship's folder by id (one the fleet has, or remembers in the memorial), or where a remembered one would go. */
	public File folderOfId(String id) {
		File f = folders.get(id);
		if (f != null) return f;
		Ship s = byId(id);
		if (s != null) return folderOf(s);
		for (File d : shipFolders()) if (id.equals(ShipStore.idOf(d))) { folders.put(id, d); return d; } // one made since opening
		return new File(memorialDir(), ShipStore.stem("", id));
	}
	/** Every ship folder: the shipyard's, the Junkyard's and the memorial's. */
	public List<File> shipFolders() {
		List<File> out = new ArrayList<File>();
		for (File d : new File[] {shipyardDir(), junkyardDir(), memorialDir()}) out.addAll(ShipStore.folders(d));
		return out;
	}
	/** The folders of ships that left the fleet (the memorial's, and any the fleet no longer lists). */
	public synchronized List<File> departedFolders() {
		List<File> out = new ArrayList<File>();
		for (File d : shipFolders()) { String id = ShipStore.idOf(d); if (id != null && byId(id) == null) out.add(d); }
		return out;
	}
	/** Puts her folder where her state says, moving it if it sits elsewhere (the Junkyard, the memorial); the folder as it is after. */
	private File settleFolder(Ship s) throws IOException {
		if (s.state == Ship.State.STORAGE) { File d = cargoHoldDir(); if (!d.isDirectory() && !d.mkdirs()) throw new IOException("Could not create " + d); return d; }
		File want = parentOf(s.state), have = folders.get(s.id);
		if (have != null && have.isDirectory() && !have.getParentFile().getAbsoluteFile().equals(want.getAbsoluteFile())) {
			have = ShipStore.move(have, want);
		} else if (have == null || !have.isDirectory()) {
			have = new File(want, ShipStore.stem(s.name, s.id));
			if (!have.isDirectory() && !have.mkdirs()) throw new IOException("Could not create " + have);
		}
		folders.put(s.id, have);
		return have;
	}
	/** Her folder goes to the memorial (she left the fleet), as one rename. */
	private void toMemorial(Ship s) throws IOException {
		File have = folders.get(s.id);
		if (have == null || !have.isDirectory()) return;
		if (!have.getParentFile().getAbsoluteFile().equals(memorialDir().getAbsoluteFile())) have = ShipStore.move(have, memorialDir());
		folders.put(s.id, have);
	}
	public File artDir() { return new File(shared, "art"); }
	public File designsFile() { return new File(shared, "designs.xml"); }
	public File remodelsFile() { return new File(shared, "remodels.xml"); }
	/** A copy of every blueprint on file, one per file (see homeplanet.parser.BlueprintBackup). */
	public File blueprintsDir() { return new File(shared, "blueprints"); }
	public File removedBlueprintsLog() { return new File(shared, "removed-blueprints.log"); }
	/** The station's own logs (history.log, master.log, events.log, reputation.log), in logs/ since 5.71 (heromedel's name; at the root before). */
	public File logsDir() { return new File(root, "logs"); }
	public File historyLog() { return new File(logsDir(), "history.log"); }
	/** The stored-systems list that goes with the storage hold. */
	public File systemsFile() { return new File(cargoHoldDir(), "systems.txt"); }
	/** The crew expeditions' folder (5.85): expeditions.xml (the sectors on offer, the crew away), crew/ (their files). */
	public File expeditionsDir() { return new File(root, "expeditions"); }
	/** The infirmary's folder (5.85): infirmary.xml (who is laid up, and until when; they stay in the Cargo Hold). */
	public File infirmaryDir() { return new File(root, "infirmary"); }
	/** The captives' folder (5.85): captives.xml (who was taken, and the ransoms asked), crew/ (their files). */
	public File captivesDir() { return new File(root, "captives"); }
	/** The Cargo Hold's folder (5.72): what it holds (cargohold.xml, 5.84), its crew's files, its stored-systems list, its parts and overflow lists, its versions. */
	public File cargoHoldDir() { return new File(root, HOLD_DIR); }
	/** The hold's xml (5.84; from 5.72 to 5.83 that name was its record, beside its save, the pretend ship cargohold.sav). */
	public static final String HOLD_DIR = "cargohold", HOLD_FILE = "cargohold.xml", HOLD_STEM = "cargohold";
	/**
	 * A fleet's Cargo Hold file, for a fleet not in use: cargohold.xml, or the save one not opened since before 5.84 keeps
	 * it in. Read it with {@link homeplanet.parser.HoldXml#read(File)}, which reads either.
	 */
	public static File holdFileIn(File fleetRoot) {
		File old = homeplanet.convert.OldFleet.holdFileIn(fleetRoot);
		return old != null ? old : new File(new File(fleetRoot, HOLD_DIR), HOLD_FILE);
	}
	/** A hold file's bytes, in the kind of file it is (the xml, or a 5.x fleet's save). */
	public static byte[] holdBytes(File f, SavedGameState gs) throws IOException {
		return f.getName().endsWith(".xml") ? homeplanet.parser.HoldXml.toBytes(gs) : SaveHelper.toBytes(gs);
	}
	/** Her save's bytes, as her file keeps them: the Cargo Hold's as its xml. */
	private byte[] bytesOf(Ship s, SavedGameState state) throws IOException {
		return s.state == Ship.State.STORAGE ? homeplanet.parser.HoldXml.toBytes(state) : SaveHelper.toBytes(state);
	}
	/** The storage hold's id (and file stem). Before 4B there were two holds; the old AE one's stem is kept for its file name. */
	public static final String STORAGE_ID = "storage";

	/** Where a ship's save is, given her state: FTL's continue.sav when boarded, her folder's otherwise (the Cargo Hold's at the root, until it has a folder of its own). */
	public File fileOf(Ship s) {
		switch (s.state) {
			case BOARDED: return continueFile();
			case STORAGE: homeplanet.convert.OldFleet.holdReady(this); return new File(cargoHoldDir(), HOLD_FILE);
			default: return ShipStore.sav(folderOf(s));
		}
	}
	/** Her own log, in her folder, for an entry that names her by id (5.76); null when the fleet has no folder for that id. */
	public File shipLogFor(String id) {
		if (id == null || id.isEmpty()) return null;
		File d = id.equals(STORAGE_ID) ? cargoHoldDir() : folders.get(id);
		return d != null && d.isDirectory() ? ShipStore.logFile(d) : null;
	}
	/** A ship's folder, where her log, her side files and her kept versions are (what history/&lt;id&gt;/ was before 5.69). */
	public File historyOf(Ship s) { return folderOf(s); }

	// ---- the fleet ----

	public synchronized List<Ship> all() { return new ArrayList<Ship>(ships); }
	public synchronized Ship boarded() {
		for (Ship s : ships) if (s.state == Ship.State.BOARDED) return s;
		return null;
	}
	public synchronized List<Ship> docked() { return of(Ship.State.DOCKED); }
	public synchronized List<Ship> junked() { return of(Ship.State.JUNKED); }
	/** Docked and boarded ships: the ones at the Space Dock. */
	public synchronized List<Ship> fleet() {
		List<Ship> out = new ArrayList<Ship>();
		for (Ship s : ships) if (s.state == Ship.State.DOCKED || s.state == Ship.State.BOARDED) out.add(s);
		return out;
	}
	private List<Ship> of(Ship.State st) {
		List<Ship> out = new ArrayList<Ship>();
		for (Ship s : ships) if (s.state == st) out.add(s);
		return out;
	}
	public synchronized Ship byId(String id) {
		for (Ship s : ships) if (s.id.equals(id)) return s;
		return null;
	}
	/** The manifest entry for the storage hold (made if missing); the file itself may not exist yet. */
	public synchronized Ship storageEntry() {
		Ship s = byId(STORAGE_ID);
		if (s == null) {
			s = new Ship(STORAGE_ID, "Spacedock Storage", Ship.State.STORAGE, true);
			ships.add(s);
		}
		return s;
	}
	/**
	 * The storage hold: one warehouse for every ship, kept as an Advanced Edition save (so it can hold AE items too).
	 * Created empty if it doesn't exist yet.
	 */
	public synchronized Ship storage() throws IOException {
		Ship s = storageEntry();
		if (!fileOf(s).isFile()) {
			File waiting = homeplanet.convert.OldFleet.holdWaiting(this);
			if (waiting != null) throw new IOException("The Cargo Hold's save (" + waiting + ") could not be read. Put back a copy from cargohold/versions, or send it with a bug report.");
			SavedGameState empty = SaveHelper.createStorageSave(s.name, true);
			writeQuietly(s, empty);
			saveManifest();
		}
		return s;
	}

	// ---- a ship just set out: still at The Home Planet Station ----

	private static String position(SavedGameState gs) { return gs.getSectorNumber() + "|" + gs.getCurrentBeaconId(); }
	/** The station has just set her out (commissioned, a New Journey, rescued): until she leaves this beacon she may trade. */
	public synchronized void setOut(Ship s, SavedGameState gs, String note) throws IOException {
		s.fresh = position(gs);
		saveManifest();
		JourneyStart.begin(this, s, gs, VoyageLog.lastSector(this, s)); // before the last look moves: it knows the journey just ended
		VoyageLog.baseline(this, s, gs);
		VoyageLog.note(this, s, note);
		homeplanet.parser.Museum.setOut(this, s, note.startsWith("Commissioned")); // her command starts: honours count from here
	}
	/** Hasn't she left the beacon the station set her out at? */
	public boolean stillAtHomePlanet(Ship s) {
		SavedGameState gs = s == null ? null : s.save();
		return gs != null && s.fresh != null && !s.fresh.isEmpty() && s.fresh.equals(position(gs));
	}
	/** May she trade (the station rule): at a beacon with a store, or not yet gone from where The Home Planet Station set her out. */
	public boolean mayTrade(Ship s) {
		SavedGameState gs = s == null ? null : s.save();
		return gs != null && (SaveHelper.mayTrade(gs) || stillAtHomePlanet(s));
	}
	/** May she set out on a New Journey (its own station rule): at a beacon with a store, or not yet gone from where she was set out. */
	public boolean mayJourney(Ship s) {
		SavedGameState gs = s == null ? null : s.save();
		return gs != null && (!homeplanet.core.HomePlanet.journeyStoreRequirement() || SaveHelper.isAtStation(gs) || stillAtHomePlanet(s));
	}

	/** No ship docked, boarded or in the Junkyard (the storage hold doesn't count). */
	public synchronized boolean shipyardEmpty() {
		return docked().isEmpty() && boarded() == null && junked().isEmpty();
	}

	// ---- the free command (HR2): once when the fleet starts, and again with each plea for a new ship ----

	private File freeCommandFile() { return new File(root, "free-command.txt"); }
	/**
	 * Is a free command granted and not yet taken? An empty shipyard alone never grants one: the captain commissions a
	 * ship with scrap, or pleads for a new one. (A fleet from before this was recorded: granted if its shipyard is
	 * empty now, once.)
	 */
	public synchronized boolean freeCommandOpen() {
		File f = freeCommandFile();
		if (!f.isFile()) {
			boolean open = shipyardEmpty();
			setFreeCommand(open, "recorded: " + (open ? "the shipyard is empty" : "the fleet has a ship"));
			return open;
		}
		try { return new String(SafeFiles.read(f), java.nio.charset.StandardCharsets.UTF_8).trim().startsWith("open"); }
		catch (IOException e) { return false; }
	}
	/** Grants the free command (a new fleet or career); its ship is Settings' or the career's. */
	public synchronized void grantFreeCommand(String why) { setFreeCommand(true, why, null); }
	/** Grants the free command with the ship it brings ({@link homeplanet.parser.FreeCommand}: an Immersive career). */
	public synchronized void grantFreeCommand(String why, String ship) { setFreeCommand(true, why, ship); }
	/** The ship the open free command names, or null: the plea's or Settings' (see FreeCommand). */
	public synchronized String freeCommandShip() { return freeCommandValue("ship "); }
	/** Why the open free command was granted. */
	private static final String PLEA = "pleaded for a new ship", OLD_PLEA = "reported for reassignment";
	/** Was the open free command granted by a plea for a new ship (rather than a new fleet or career)? */
	public synchronized boolean freeCommandReassigned() { String[] l = freeCommandLines(); return l.length > 1 && l[0].startsWith("open") && (l[1].startsWith(PLEA) || l[1].startsWith(OLD_PLEA)); }
	/** Is the open free command an old Report for Reassignment's, which took the Cargo Hold and the Junkyard (and can give them back)? */
	public synchronized boolean freeCommandForfeit() {
		String[] l = freeCommandLines();
		return freeCommandReassigned() && l[1].startsWith(OLD_PLEA);
	}
	/** A line of the open free command that starts with this, the rest of it; or null. */
	private String freeCommandValue(String key) {
		String[] l = freeCommandLines();
		if (l.length == 0 || !l[0].startsWith("open")) return null;
		for (int i = 2; i < l.length; i++) if (l[i].startsWith(key)) return l[i].substring(key.length()).trim();
		return null;
	}
	private String[] freeCommandLines() {
		try { return new String(SafeFiles.read(freeCommandFile()), java.nio.charset.StandardCharsets.UTF_8).trim().split("\\r?\\n"); }
		catch (IOException e) { return new String[0]; }
	}
	/** The free command is taken (her commission), or taken back (an undone report). */
	public synchronized void useFreeCommand(String why) { setFreeCommand(false, why, null); }
	private void setFreeCommand(boolean open, String why) { setFreeCommand(open, why, null); }
	private void setFreeCommand(boolean open, String why, String ship) { setFreeCommand(open, why, ship, null); }
	private void setFreeCommand(boolean open, String why, String ship, String more) {
		try { SafeFiles.writeText(freeCommandFile(), (open ? "open" : "used") + "\n" + why + "\n" + (ship == null ? "" : "ship " + ship + "\n") + (more == null ? "" : more + "\n"), false); }
		catch (IOException e) { log.warn("Could not record the free command: {}", e.toString()); }
	}
	/** The scrap in the storage hold (0 if it can't be read). */
	public int storageScrap() {
		try {
			SavedGameState g = storage().save();
			return g == null ? 0 : g.getPlayerShip().getScrapAmt();
		} catch (IOException e) {
			return 0;
		}
	}
	/**
	 * Pays scrap from the storage hold (a commission, a journey's fee). Refuses, changing nothing, if the hold has less.
	 * Returns the hold's file as it was, for {@link #refundStorage} if what was paid for then fails.
	 */
	public synchronized byte[] payFromStorage(int scrap) throws IOException {
		log.debug("Cargo Hold pays {} scrap", scrap);
		Ship st = storage();
		Copy c = readCopy(st);
		int have = c.save.getPlayerShip().getScrapAmt();
		if (have < scrap) throw new IOException("The Cargo Hold has " + have + " scrap; " + scrap + " is needed");
		byte[] before = SafeFiles.read(fileOf(st));
		c.save.getPlayerShip().setScrapAmt(have - scrap);
		begin().put(st, c.save, c.hash).commit();
		return before;
	}
	/** Puts the storage hold back as {@link #payFromStorage} found it. */
	public synchronized void refundStorage(byte[] before) throws IOException {
		Ship st = storage();
		File f = fileOf(st);
		SafeFiles.write(f, before);
		st.invalidate();
		st.hash = SafeFiles.hash(f);
		saveManifest();
	}

	// ---- Plead for New Ship ----

	/** Where each old Report for Reassignment kept what was surrendered (one folder each), so it can be undone. */
	public File surrenderedDir() { return new File(root, "surrendered"); }
	private static final String SURRENDER_SHIPS = "ships.txt", SURRENDER_HOLD = "storage.sav", SURRENDER_SYSTEMS = "storage-systems.txt", SURRENDER_AFTER = "after.txt";

	/**
	 * The old Report for Reassignment: the storage hold (scrap, supplies, items, crew, stored systems) and every hull in
	 * the Junkyard surrendered; kept in a new folder under surrendered/, for {@link #undoSurrender}. Returns that folder.
	 * (Plead for New Ship replaced it: see {@link #plead} and {@link #forfeitHold}; kept for the harness and old fleets.)
	 */
	public synchronized File surrender() throws IOException {
		File dir;
		String base;
		synchronized (STAMP) { base = STAMP.format(new java.util.Date()); }
		dir = new File(surrenderedDir(), base);
		for (int i = 2; dir.exists(); i++) dir = new File(surrenderedDir(), base + "-" + i); // two in one second
		if (!dir.mkdirs()) throw new IOException("Could not create " + dir);
				Ship st = storage();
		int value = homeplanet.parser.FreeCommand.surrenderValue(this); // before any of it moves
		File hold = fileOf(st), systems = systemsFile();
		List<Ship> junk = junked();
		List<Ship> moved = new ArrayList<Ship>();
		try {
			SafeFiles.copy(hold, new File(dir, SURRENDER_HOLD));
			if (systems.isFile()) SafeFiles.copy(systems, new File(dir, SURRENDER_SYSTEMS));
			StringBuilder list = new StringBuilder();
			for (Ship s : junk) list.append(s.id).append('\t').append(s.name).append('\n');
			SafeFiles.writeText(new File(dir, SURRENDER_SHIPS), list.toString(), false);
			for (Ship s : junk) { folders.put(s.id, ShipStore.move(settleFolder(s), dir)); moved.add(s); } // her whole folder (5.69)
		} catch (IOException e) {
			for (Ship s : moved) {
				try { settleFolder(s); } catch (IOException again) { log.error("Could not put " + s + " back in the Junkyard", again); }
			}
			SafeFiles.deleteTree(dir);
			throw e;
		}
		try {
			snapshot(st);
			writeQuietly(st, SaveHelper.createStorageSave(st.name, true));
		} catch (IOException e) {
			// the hold wasn't emptied: the hulls go back to the Junkyard, and nothing was surrendered
			for (Ship s : moved) {
				try { settleFolder(s); } catch (IOException again) { log.error("Could not put " + s + " back in the Junkyard; her folder is in " + dir, again); return dirFailed(dir, e); }
			}
			SafeFiles.deleteTree(dir);
			throw e;
		}
		ships.removeAll(junk);
		if (systems.isFile() && !systems.delete()) log.warn("Could not remove {}", systems);
		SafeFiles.writeText(new File(dir, SURRENDER_AFTER), SafeFiles.hash(hold) + "\n", false);
		saveManifest();
		List<String> lines = new ArrayList<String>();
		for (Ship s : junk) lines.add("hull: " + s.name);
		Event e = Event.of("REASSIGN").put("hulls", junk.size()).put("value", value).put("folder", "surrendered/" + dir.getName());
		for (Ship s : junk) e.put("hull", s.name + "." + s.id);
		HistoryLog.entry("REASSIGN", "the Cargo Hold and " + junk.size() + " hull(s) from the Junkyard surrendered (worth " + value + " scrap); kept in surrendered/" + dir.getName(), lines, e);
		grantFreeCommand(OLD_PLEA);
		return dir;
	}
	/** A hull couldn't be put back after a failed surrender: the folder keeps it, and the error says where. */
	private static File dirFailed(File dir, IOException e) throws IOException {
		throw new IOException(e.getMessage() + ". Some hulls could not be put back in the Junkyard: their folders are in " + dir, e);
	}

	/** The newest surrender not yet undone, or null. */
	public synchronized File lastSurrender() {
		File[] dirs = surrenderedDir().listFiles();
		if (dirs == null) return null;
		File best = null;
		for (File d : dirs) {
			if (!d.isDirectory() || !new File(d, SURRENDER_AFTER).isFile()) continue;
			if (best == null || d.getName().compareTo(best.getName()) > 0) best = d;
		}
		return best;
	}
	/**
	 * Plead for New Ship: The Federation Home Planet agrees to send a new ship. Nothing is taken yet: at Commission the
	 * captain chooses her, and whether the Cargo Hold pays for her ({@link #forfeitHold}) or the career's reputation does.
	 */
	public synchronized void plead() {
		HistoryLog.entry("PLEAD", "The Federation Home Planet agreed to send a new ship: her order waits at Commission", null, Event.of("PLEAD").put("stage", "agreed"));
		grantFreeCommand(PLEA);
	}
	/**
	 * The plea's ship is paid for with the Cargo Hold: everything in it (scrap, supplies, items, stored systems) goes to
	 * The Federation Home Planet, and the hold starts again with the refund (the difference, if the captain asked for it).
	 * The crew in it stay (they aren't counted, and aren't given up); the Junkyard is untouched. Returns the hold's file and stored systems as they were, for {@link #unforfeitHold}.
	 */
	public synchronized byte[][] forfeitHold(int saleValue, int refund) throws IOException {
		Ship st = storage();
		File hold = fileOf(st), systems = systemsFile();
		byte[][] before = {SafeFiles.read(hold), systems.isFile() ? SafeFiles.read(systems) : null};
		snapshot(st);
		SavedGameState was = readCopy(st).save;
		SavedGameState fresh = SaveHelper.createStorageSave(st.name, true);
		fresh.getPlayerShip().setScrapAmt(Math.max(0, refund));
		fresh.getPlayerShip().getCrewList().addAll(was.getPlayerShip().getCrewList()); // the crew stay
		writeQuietly(st, fresh);
		if (systems.isFile() && !systems.delete()) log.warn("Could not remove {}", systems);
		saveManifest();
		HistoryLog.entry("PLEAD", "the Cargo Hold given for the new ship (worth " + saleValue + " scrap at sale)" + (refund > 0 ? "; " + refund + " scrap refunded to it" : ""), null,
				Event.of("PLEAD").put("stage", "hold_given").put("value", saleValue).put("refund", refund));
		return before;
	}
	/** Puts the Cargo Hold back as {@link #forfeitHold} found it (her commission failed). */
	public synchronized void unforfeitHold(byte[][] before) throws IOException {
		Ship st = storage();
		File hold = fileOf(st);
		SafeFiles.write(hold, before[0]);
		st.invalidate();
		st.hash = SafeFiles.hash(hold);
		if (before[1] != null) SafeFiles.write(systemsFile(), before[1]);
		saveManifest();
	}
	/** Withdraws a waiting plea (nothing was taken: her order is simply cancelled). */
	public synchronized void withdrawPlea() throws IOException {
		if (!freeCommandOpen() || !freeCommandReassigned()) throw new IOException("There is no plea waiting: the new ship has been commissioned since");
		HistoryLog.entry("UNDO PLEA", "the plea for a new ship was withdrawn", null, Event.of("UNDO_PLEA"));
		useFreeCommand("the plea was withdrawn");
	}
	/** The hull names a surrender holds. */
	public List<String> surrenderedNames(File dir) throws IOException {
		List<String> out = new ArrayList<String>();
		for (String line : new String(SafeFiles.read(new File(dir, SURRENDER_SHIPS)), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
			int t = line.indexOf('\t');
			if (t > 0) out.add(line.substring(t + 1).trim());
		}
		return out;
	}
	/**
	 * Undoes an old Report for Reassignment: the hold as it was and the hulls back in the Junkyard. Refused once the new
	 * ship is commissioned (undoing then would keep both), or if the hold has changed since (what it holds now would be
	 * lost).
	 */
	public synchronized void undoSurrender(File dir) throws IOException {
		if (!freeCommandOpen() || !freeCommandForfeit())
			throw new IOException("The new ship has been commissioned since the report for reassignment: it can only be undone while her order waits at Commission");
		Ship st = storage();
		File hold = fileOf(st);
		String after = new String(SafeFiles.read(new File(dir, SURRENDER_AFTER)), java.nio.charset.StandardCharsets.UTF_8).trim();
		if (!SafeFiles.hash(hold).equals(after))
			throw new IOException("The Cargo Hold has changed since the report for reassignment: undoing it would lose what it holds now");
		List<String[]> list = new ArrayList<String[]>();
		for (String line : new String(SafeFiles.read(new File(dir, SURRENDER_SHIPS)), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
			int t = line.indexOf('\t');
			if (t > 0) list.add(new String[] {line.substring(0, t), line.substring(t + 1).trim()});
		}
		List<Ship> back = new ArrayList<Ship>();
		Map<String, File> kept = new LinkedHashMap<String, File>(); // her folder in the surrender, by id (5.69)
		for (File d : ShipStore.folders(dir)) { String id = ShipStore.idOf(d); if (id != null) kept.put(id, d); }
		try {
			for (String[] e : list) {
				if (byId(e[0]) != null) continue; // already back (recovered by hand)
				ShipStore.Record r = kept.containsKey(e[0]) ? ShipStore.read(kept.get(e[0])) : null;
				Ship s = new Ship(e[0], e[1], Ship.State.JUNKED, r == null || r.dlc);
				if (kept.containsKey(e[0])) {
					folders.put(s.id, ShipStore.move(kept.get(e[0]), junkyardDir()));
				} else { // a surrender from before 5.69: her save alone
					File old = new File(dir, e[0] + ".sav");
					if (!old.isFile()) throw new IOException("Could not find " + s.name + " in " + dir);
					folders.remove(s.id);
					SafeFiles.move(old, ShipStore.sav(settleFolder(s)));
				}
				back.add(s);
			}
		} catch (IOException e) {
			for (Ship s : back) {
				try {
					if (kept.containsKey(s.id)) folders.put(s.id, ShipStore.move(folders.get(s.id), dir));
					else { SafeFiles.move(fileOf(s), new File(dir, s.id + ".sav")); SafeFiles.deleteTree(folders.remove(s.id)); }
				} catch (IOException again) { log.error("Could not return " + s + " to " + dir, again); }
			}
			throw e;
		}
		snapshot(st);
		File keptHold = new File(dir, SURRENDER_HOLD); // the hold's file as it was: a save, if surrendered before 5.84
		SafeFiles.write(hold, homeplanet.parser.HoldXml.isHold(keptHold) ? SafeFiles.read(keptHold) : homeplanet.parser.HoldXml.toBytes(homeplanet.parser.HoldXml.read(keptHold)));
		st.invalidate();
		st.hash = SafeFiles.hash(hold);
		File sys = new File(dir, SURRENDER_SYSTEMS);
		if (sys.isFile()) SafeFiles.write(systemsFile(), SafeFiles.read(sys));
		ships.addAll(back);
		saveManifest();
		File done = new File(dir.getParentFile(), dir.getName() + "-undone");
		if (!new File(dir, SURRENDER_AFTER).delete() || !dir.renameTo(done)) log.warn("Could not mark {} as undone", dir);
		Event undo = Event.of("UNDO_REASSIGN").put("hulls", back.size()).put("folder", "surrendered/" + dir.getName());
		for (Ship s : back) undo.put("hull", s.name + "." + s.id);
		HistoryLog.entry("UNDO REASSIGN", "the Cargo Hold and " + back.size() + " hull(s) returned from surrendered/" + dir.getName(), null, undo);
		useFreeCommand("the report for reassignment was undone");
	}

	/** A new id: short, unique, safe in a file name. */
	static String newId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}

	// ---- loading and reconciling ----

	/** Reads the manifest, then checks it against the files (FTL may have deleted or replaced continue.sav). */
	public synchronized void load() throws IOException {
		ships.clear();
		folders.clear();
		if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Could not create " + root);
		if (!logsDir().isDirectory() && !logsDir().mkdirs()) throw new IOException("Could not create " + logsDir());
		Journal.ensure(this);
		Journal.settle(this); // an action a station stopped partway through, finished before anything else touches the fleet
		homeplanet.convert.OldFleet.before(this); // a fleet from before 6.0 brought across, once: its files, its Cargo Hold, its logs
		shipyardDir().mkdirs();
		junkyardDir().mkdirs();
		memorialDir().mkdirs();
		readFolders();
		reconcile();
		saveManifest();
		homeplanet.convert.OldFleet.after(this); // and its crew register, after the ships whose folders hold their crew
	}
	/** Re-reads everything (Refresh). Parsed saves whose files didn't change are kept. */
	public synchronized void reload() throws IOException {
		Map<String, Ship> old = new LinkedHashMap<String, Ship>();
		for (Ship s : ships) old.put(s.id, s);
		load();
		// keep the parsed saves of ships whose files are unchanged (Ship.save() re-reads when the hash differs)
		for (Ship s : ships) {
			Ship o = old.get(s.id);
			if (o != null && o.state == s.state) {
				ships.set(ships.indexOf(s), o);
				o.name = s.name; o.hash = s.hash; o.dlc = s.dlc;
			}
		}
	}

	/**
	 * The fleet from its folders: every ship folder in the shipyard (docked, or boarded if her record says so and
	 * continue.sav is there) and the Junkyard, the memorial's remembered without a ship; the Cargo Hold's record at the
	 * root. The folder is where she is; her record's flag says where she should be, and a disagreement is noted (§3.2).
	 */
	private void readFolders() {
		for (File d : shipFolders()) {
			String id = ShipStore.idOf(d);
			ShipStore.Record r = ShipStore.read(d);
			if (id == null || r == null) continue;
			folders.put(id, d);
			File parent = d.getParentFile().getAbsoluteFile();
			if (parent.equals(memorialDir().getAbsoluteFile())) continue; // remembered, not in the fleet
			Ship.State where = parent.equals(junkyardDir().getAbsoluteFile()) ? Ship.State.JUNKED : "boarded".equals(r.state) ? Ship.State.BOARDED : Ship.State.DOCKED; // boarded with no continue.sav: lost, as reconcile finds
			if (!where.key.equals(r.state)) log.info("{}'s record says {} but her folder is in {}: the folder stands", r.name, r.state, d.getParentFile().getName());
			Ship s = new Ship(r.id, r.name, where, r.dlc);
			s.hash = r.hash; s.marks = r.marks; s.stranger = r.stranger; s.fresh = r.fresh;
			ships.add(s);
		}
		File hold = new File(cargoHoldDir(), HOLD_FILE);
		if (hold.isFile() || homeplanet.convert.OldFleet.holdWaiting(this) != null) { // the hold needs no record (5.84): its file says what it is
			Ship s = new Ship(STORAGE_ID, "Spacedock Storage", Ship.State.STORAGE, true);
			try { s.hash = SafeFiles.hash(hold); } catch (IOException e) { s.hash = ""; }
			ships.add(s);
		}
	}
	/** A ship's record from what the fleet knows of her. */
	ShipStore.Record recordOf(Ship s) {
		ShipStore.Record r = new ShipStore.Record(s.id);
		r.name = s.name == null ? "" : s.name; r.state = s.state.key; r.dlc = s.dlc;
		r.hash = s.hash == null ? "" : s.hash; r.marks = s.marks == null ? "" : s.marks; r.stranger = s.stranger; r.fresh = s.fresh == null ? "" : s.fresh;
		File d = folders.get(s.id);
		ShipStore.Record was = d == null ? null : ShipStore.read(d);
		if (was != null) { r.owners.addAll(was.owners); r.pastNames.addAll(was.pastNames); r.sections.putAll(was.sections); } // what her record holds besides
		return r;
	}
	/**
	 * Her record's bytes as they'll be once a move is done (5.95): Board and Dock write it in the same note as her save,
	 * so a station stopped partway finds her record saying where her save is, never where it was. Marks null: kept.
	 */
	private byte[] recordAs(Ship s, Ship.State state, String hash, String marks) {
		ShipStore.Record r = recordOf(s);
		r.state = state.key; r.hash = hash == null ? "" : hash;
		if (marks != null) r.marks = marks;
		return ShipStore.bytes(r);
	}

	/**
	 * Sets one section of the record in her folder ({@link ShipStore#notes}; null or empty removes it), the rest of her
	 * record as it is (5.98: what her side files held). Under the fleet's lock, as every writer of a record is, so two
	 * changes to her record can't lose one another.
	 */
	public synchronized void setNotes(File folder, String section, java.util.Properties p) throws IOException {
		byte[] b = recordWithNotes(folder, section, p);
		if (b != null) SafeFiles.write(ShipStore.xml(folder), b);
	}
	public void setNotes(String id, String section, java.util.Properties p) throws IOException { setNotes(folderOfId(id), section, p); }
	/**
	 * Her record's bytes with one section set, for a protection note; null when there's nothing to remove. A folder with
	 * no record yet gets one from what the fleet knows of her, or else from the folder's name.
	 */
	synchronized byte[] recordWithNotes(File folder, String section, java.util.Properties p) throws IOException {
		boolean remove = p == null || p.isEmpty();
		ShipStore.Record r = ShipStore.read(folder);
		if (r == null) {
			if (remove) return null;
			String id = ShipStore.idOf(folder);
			if (id == null) throw new IOException(folder + " isn't a ship's folder");
			Ship s = byId(id);
			if (s != null) r = recordOf(s);
			else { r = new ShipStore.Record(id); r.name = folder.getName().substring(0, folder.getName().length() - id.length() - 1); }
		}
		if (remove) { if (r.sections.remove(section) == null) return null; }
		else { java.util.Properties c = new java.util.Properties(); c.putAll(p); r.sections.put(section, c); }
		return ShipStore.bytes(r);
	}
	/** Her record's bytes without these sections, for a protection note; null if she has none of them (or no record). */
	synchronized byte[] recordWithout(File folder, String... sections) {
		ShipStore.Record r = ShipStore.read(folder);
		if (r == null) return null;
		boolean any = false;
		for (String sec : sections) any |= r.sections.remove(sec) != null;
		return any ? ShipStore.bytes(r) : null;
	}

	/** Makes the list match the files: adopts strays, drops ships whose files are gone, settles who's boarded. */
	private void reconcile() {
		List<String> notes = new ArrayList<String>();
		File cont = continueFile();
		// a Board stopped partway before 5.95 (her record still saying docked): her save is continue.sav, and nobody else is boarded
		if (boarded() == null && cont.isFile()) {
			for (Ship s : ships) {
				if (s.state != Ship.State.DOCKED || fileOf(s).isFile() || !isHers(s, cont)) continue;
				s.state = Ship.State.BOARDED;
				try { s.hash = SafeFiles.hash(cont); } catch (IOException e) { s.hash = ""; }
				notes.add(s.name + " was being boarded when the station stopped: continue.sav is her save, so she is boarded");
				break;
			}
		}
		// ships whose files vanished
		for (Ship s : new ArrayList<Ship>(ships)) {
			if (s.state == Ship.State.BOARDED) continue;
			if (!fileOf(s).isFile()) {
				if (s.state == Ship.State.STORAGE) continue; // recreated on demand
				notes.add(s.name + " (" + s.state.key + "): her file is gone; " + (historyOf(s).isDirectory() ? "her folder is kept in the memorial" : "nothing left of her"));
				ships.remove(s);
				try { toMemorial(s); } catch (IOException e) { log.warn("Could not move {}'s folder to the memorial: {}", s, e.toString()); }
			}
		}
		// the boarded ship: continue.sav is hers, if it's there
		Ship b = boarded();
		// a Dock stopped partway before 5.95 (her record still saying boarded): her save is back in her folder, so she's docked, not lost
		if (b != null && !cont.isFile() && ShipStore.sav(folderOf(b)).isFile()) {
			b.state = Ship.State.DOCKED;
			try { b.hash = SafeFiles.hash(fileOf(b)); } catch (IOException e) { b.hash = ""; }
			notes.add(b.name + " was being docked when the station stopped: her save is in her folder, so she is docked");
			b = null;
		}
		// never while FTL is running: it rewrites continue.sav by deleting it first, so a missing file there proves nothing
		if (b != null && !cont.isFile() && !homeplanet.core.GameGuard.isFtlRunning()) {
			notes.add(b.name + " was boarded, and continue.sav is gone: lost in action (FTL ends a run by deleting the save). "
					+ (historyOf(b).isDirectory() ? "Her last versions are in " + place(b) : ""));
			recordFate(b, Fate.LOST);
			Reputation.lost(this, b);
			ships.remove(b);
			b = null;
		}
		if (b == null && cont.isFile()) setAsideMarked(cont, notes); // her mark says whose she is (6.08)
		if (b == null && cont.isFile()) {
			Ship original = cloudCopyOf(cont);
			if (original != null) {
				// Steam Cloud brought back a continue.sav the station already has (a docked ship, or one of her kept versions)
				try {
					File dir = settleFolder(original);
					ShipStore.keepVersion(dir, SafeFiles.read(cont), "cloud-");
					if (!cont.delete()) throw new IOException("Could not remove " + cont);
					cloudCopy = "Steam Cloud brought back an old copy of " + homeplanet.parser.ShipNames.the(original.name) + ", who is already in your fleet.\n"
							+ "The copy was set aside in her records, not added as a second ship.";
					notes.add("continue.sav was a copy of " + original.name + " (" + original.state.key + "), brought back by Steam Cloud most likely: set aside in " + dir.getParentFile().getName() + "/" + dir.getName() + "/" + ShipStore.VERSIONS + " (cloud-)");
				} catch (IOException e) {
					log.warn("Could not set aside the copy of {} in continue.sav: {}", original, e.toString());
				}
			}
		}
		if (b == null && cont.isFile()) {
			Ship n = new Ship(newId(), "Unknown ship", Ship.State.BOARDED, true);
			n.name = ""; // filled in by takeStock() once the game data is loaded
			n.stranger = true; // not commissioned here (FTL's New Game, most likely)
			ships.add(n);
			notes.add("continue.sav is a ship the station didn't know (a new game started in FTL, most likely): she is now boarded");
			b = n;
		}
		// strays: files in ships/ and junkyard/ that no entry names (a save copied in by hand)
		adoptStrays(shipyardDir(), Ship.State.DOCKED, notes);
		adoptStrays(junkyardDir(), Ship.State.JUNKED, notes);
		// a folder in the shipyard with no ship in it (her save gone, and not boarded): remembered, not listed
		for (Ship s : new ArrayList<Ship>(ships)) {
			if (s.state == Ship.State.BOARDED || s.state == Ship.State.STORAGE || fileOf(s).isFile()) continue;
			notes.add(s.name + " (" + s.state.key + "): her save is gone; her folder is kept in the memorial");
			ships.remove(s);
			try { toMemorial(s); } catch (IOException e) { log.warn("Could not move {}'s folder to the memorial: {}", s, e.toString()); }
		}
		if (!notes.isEmpty()) HistoryLog.entry("VAULT", "taking stock", notes, Event.of("VAULT").put("what", "taking_stock").details(notes));
	}
	/**
	 * Reads every ship whose file changed since it was last seen (names, DLC flags and fingerprints). Needs the game
	 * data loaded, so it runs after {@link #load()} once that is, and again on Refresh.
	 */
	public void takeStock() throws IOException {
		takeStockLocked();
		CrewRegister.sweep(this); // the crew register, outside the fleet's lock (5.41)
	}
	private synchronized void takeStockLocked() throws IOException {
		boolean changed = false;
		for (Ship s : ships) {
			File f = fileOf(s);
			if (!f.isFile()) continue;
			String h;
			try { h = SafeFiles.hash(f); } catch (IOException e) { log.warn("Could not fingerprint {}", f); continue; }
			if (h.equals(s.hash) && s.name != null && !s.name.isEmpty()) continue;
			s.invalidate();
			s.save(); // refreshes the name and the DLC flag when the file can be read
			if (s.name == null || s.name.isEmpty()) s.name = s.state == Ship.State.STORAGE ? "Spacedock Storage" : "Unknown ship";
			s.hash = h;
			changed = true;
		}
		if (checkBoarded()) changed = true;
		if (changed) saveManifest();
	}

	// ---- the final victory (rescue or reward) ----

	/*
	 * FTL writes continue.sav as the Rebel Flagship heads for the last battle (her pending stage 3, not yet alongside),
	 * again as she arrives and as the player closes her message, then nothing until the fight ends; a win updates the
	 * profile (its victory count, a Top Scores entry) and deletes continue.sav a moment later. So the station keeps the
	 * last copy written while she is on her way, a calm save with no battle in it, and the profile's victory count then.
	 * When the ship is later found lost, a count gone up means she won.
	 */
	private static final String FINAL = "final-battle.sav"; // the counts then: her record's final section (5.98; final-battle.txt before)
	/** Is this save the boarded ship with the Rebel Flagship on her way to the last battle? */
	static boolean flagshipOnHerWay(SavedGameState gs) {
		return !gs.isRebelFlagshipNearby() && gs.getRebelFlagshipState() != null && gs.getRebelFlagshipState().getPendingStage() >= 3;
	}
	/**
	 * Watching continue.sav: if it's the boarded ship with the flagship on her way to the last battle, keeps a copy (the
	 * latest replaces the one before), with the profile's victory count now and her victorious Top Scores entries now.
	 * True if kept. A save FTL is still writing doesn't read, and is left for the next look.
	 */
	public synchronized boolean watchContinue(int victoriesNow, java.util.function.BiFunction<String, String, Integer> victoriousScores) {
		Ship b = boarded();
		File cont = continueFile();
		if (b == null || !cont.isFile()) return false;
		File dir = historyOf(b), tmp = new File(dir, FINAL + ".tmp");
		try {
			if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
			SafeFiles.copy(cont, tmp); // read from a copy: FTL may write again meanwhile
			SavedGameState gs;
			try { gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(tmp); } catch (Exception e) { return false; } // mid-write
			if (b.marks != null && !b.marks.isEmpty() && !sameShip(b.marks, gs)) return false; // not her: the next look sorts that out
			if (ignoring(b)) return false; // not the career's ship (5.54)
			if (!flagshipOnHerWay(gs)) return false;
			boolean first = !new File(dir, FINAL).isFile();
			SafeFiles.move(tmp, new File(dir, FINAL));
			int scoresNow = victoriousScores.apply(gs.getPlayerShipName(), gs.getPlayerShipBlueprintId());
			if (victoriesNow < 0 || scoresNow < 0) { // the profile didn't read (FTL writing it?): the counts from the copy before stand
				java.util.Properties p = ShipStore.notes(dir, ShipStore.FINAL);
				if (victoriesNow < 0) victoriesNow = Store.num(p, "victoriesThen", -1);
				if (scoresNow < 0) scoresNow = Store.num(p, "scoresThen", -1);
			}
			setNotes(dir, ShipStore.FINAL, finalNotes(victoriesNow, scoresNow, ""));
			if (first) HistoryLog.entry("FINAL BATTLE", b.name + ": the Rebel Flagship is on her way to the last battle. A copy is kept in " + place(b) + "/" + FINAL, null,
					shipEvent("FINAL_BATTLE", b).put("copy", place(b) + "/" + FINAL).put("sector", gs.getSectorNumber() + 1).put("victories_then", victoriesNow).put("scores_then", scoresNow));
			return true;
		} catch (IOException e) {
			log.warn("Could not keep {}'s copy before the last battle: {}", b, e.toString());
			return false;
		} finally {
			tmp.delete();
		}
	}
	/** A ship lost with a copy kept before the last battle: her id, name, blueprint, the copy, and the profile's counts then. */
	public static final class FinalBattle {
		public final String id, name;
		public final File copy;
		public final int victoriesThen, scoresThen;
		/** "offered" once a rescued ship's offer is out (keep her, or the museum); empty before it's settled. */
		public final String outcome;
		FinalBattle(String id, String name, File copy, int victoriesThen, int scoresThen, String outcome) {
			this.id = id; this.name = name; this.copy = copy; this.victoriesThen = victoriesThen; this.scoresThen = scoresThen; this.outcome = outcome;
		}
	}
	/** Ships lost (no longer in the fleet) with a copy kept before the last battle, still to settle or with an offer open. */
	public synchronized List<FinalBattle> finalBattles() {
		List<FinalBattle> out = new ArrayList<FinalBattle>();
		for (File d : departedFolders()) {
			File copy = new File(d, FINAL);
			if (!copy.isFile()) continue;
			String id = ShipStore.idOf(d);
			java.util.Properties p = ShipStore.notes(d, ShipStore.FINAL);
			String[] fate = ShipStore.fate(d);
			String name = fate != null ? fate[1] : id;
			out.add(new FinalBattle(id, name, copy, Store.num(p, "victoriesThen", -1), Store.num(p, "scoresThen", -1), p.getProperty("outcome", "")));
		}
		return out;
	}
	/** This ship's final battle (lost, with a copy kept), or null. */
	public synchronized FinalBattle finalBattle(String id) {
		for (FinalBattle f : finalBattles()) if (f.id.equals(id)) return f;
		return null;
	}
	/** Notes that a rescued ship's offer is out (so it isn't made twice). */
	public synchronized void finalOffered(FinalBattle f) throws IOException { finalNote(f, "offered"); }
	/** Notes that her reward is being paid (so it isn't paid twice). */
	public synchronized void finalRewarded(FinalBattle f) throws IOException { finalNote(f, "rewarded"); }
	/** Takes back a note (a payment that failed). */
	public synchronized void finalUnsettled(FinalBattle f) {
		try { finalNote(f, ""); } catch (IOException e) { log.error("Could not take back the note on {}'s final battle", f.name, e); }
	}
	private void finalNote(FinalBattle f, String outcome) throws IOException {
		setNotes(f.copy.getParentFile(), ShipStore.FINAL, finalNotes(f.victoriesThen, f.scoresThen, outcome));
	}
	private static java.util.Properties finalNotes(int victoriesThen, int scoresThen, String outcome) {
		java.util.Properties p = new java.util.Properties();
		p.setProperty("victoriesThen", Integer.toString(victoriesThen));
		p.setProperty("scoresThen", Integer.toString(scoresThen));
		if (!outcome.isEmpty()) p.setProperty("outcome", outcome);
		return p;
	}
	/**
	 * Closes a final battle: her copy stays in her history as a kept version, named for what came of it
	 * ("victory-…" or "final-battle-…").
	 */
	public synchronized void closeFinal(FinalBattle f, boolean victory) {
		File dir = folderOfId(f.id); // her folder as it is now (it moves when she's brought home)
		File copy = new File(dir, FINAL);
		try {
			ShipStore.keepVersion(dir, SafeFiles.read(copy), victory ? "victory-" : "final-battle-");
			if (!copy.delete()) log.warn("Could not remove {}", copy);
		} catch (IOException e) { log.warn("Could not keep {}'s final battle copy: {}", f.name, e.toString()); }
		try { setNotes(dir, ShipStore.FINAL, null); }
		catch (IOException e) { log.warn("Could not close the note on {}'s final battle: {}", f.name, e.toString()); }
	}
	/**
	 * Brings a victorious ship home from her copy: docked, her journey reset as a New Journey would (the flagship and
	 * its fleet gone from her charts), her crew, cargo and damage as they were. Her next journey is at this difficulty
	 * (null: the one she won on).
	 */
	public synchronized Ship bringHome(FinalBattle f, net.blerf.ftl.constants.Difficulty difficulty) throws IOException {
		if (byId(f.id) != null) throw new IOException(f.name + " is already in the fleet");
		SavedGameState gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(f.copy);
		SaveHelper.startJourney(gs, difficulty != null ? difficulty : gs.getDifficulty());
		java.util.Iterator<net.blerf.ftl.parser.SavedGameParser.CrewState> it = gs.getPlayerShip().getCrewList().iterator();
		while (it.hasNext()) if (!SaveHelper.isOwnCrew(it.next())) it.remove(); // boarders and the like stay behind
		Ship s = new Ship(f.id, gs.getPlayerShipName(), Ship.State.DOCKED, gs.isDLCEnabled());
		File was = folders.get(f.id);
		if (was != null && was.isDirectory() && !was.getParentFile().getAbsoluteFile().equals(shipyardDir().getAbsoluteFile())) { // her folder back to the shipyard with her save, as one note (5.88)
			comeHome(s, was, SaveHelper.toBytes(gs));
			s.written(gs, SafeFiles.hash(fileOf(s)));
		} else { settleFolder(s); writeQuietly(s, gs); }
		ships.add(s);
		s.fresh = position(gs); // she's back at The Home Planet Station
		VoyageLog.baseline(this, s, gs);
		String rescued = "Rescued after the final engagement: back at The Home Planet Station, ready for a new journey";
		VoyageLog.note(this, s, homeplanet.core.Event.of("VOYAGE_NOTE").put("text", rescued).put("difficulty", gs.getDifficulty() == null ? null : gs.getDifficulty().toString().toLowerCase())
				.put("difficulty_chosen", difficulty != null).human(rescued));
		homeplanet.parser.Museum.setOut(this, s, false);
		try {
			saveManifest();
		} catch (IOException e) {
			ships.remove(s);
			fileOf(s).delete();
			throw e;
		}
		setNotes(historyOf(s), ShipStore.FATE, null);
		closeFinal(f, true);
		HistoryLog.entry("VICTORY", s.name + " was rescued after the last battle: docked, ready for a new journey", null, shipEvent("VICTORY", s).put("what", "rescued"));
		return s;
	}
	/** A rescued ship goes to the Federation Museum instead: her fate recorded, her copy kept as her last version. */
	public synchronized void toMuseum(FinalBattle f) {
		Ship gone = new Ship(f.id, f.name, Ship.State.DOCKED, true);
		recordFate(gone, Fate.MUSEUM);
		closeFinal(f, true);
		HistoryLog.entry("MUSEUM", f.name + " is honoured in the Federation Museum", null, shipEvent("MUSEUM", gone));
	}

	// ---- Steam Cloud's copies ----

	private String cloudCopy = null;
	/** What the player is told of a copy continue.sav turned out to be (set aside since this was last asked), or null. */
	public synchronized String takeCloudCopy() { String c = cloudCopy; cloudCopy = null; return c; }
	/** Where a copy marked as another career's ship is set aside (6.08). */
	public static final String SET_ASIDE = "set-aside";
	/**
	 * A continue.sav no boarded ship owns, marked (ShipMark, 6.08) as a ship of this career the station knows (in the
	 * fleet, or one that has left it) or as a ship of another career: a copy FTL handed back (Steam Cloud, most likely),
	 * set aside rather than adopted as a second ship. Unmarked, or marked as a ship of this career the station has no
	 * record of: left to the older checks.
	 */
	private void setAsideMarked(File cont, List<String> notes) {
		ShipMark.Found m = ShipMark.read(cont);
		if (m == null) return;
		boolean ours = slot.equals(m.career);
		Ship s = ours ? byId(m.id) : null;
		if (s != null && s.state == Ship.State.STORAGE) return;
		File dir;
		try { dir = s != null ? settleFolder(s) : ours ? folderOfId(m.id) : new File(root, SET_ASIDE); }
		catch (IOException e) { log.warn("Could not find {}'s folder to set her copy aside: {}", s.name, e.toString()); return; }
		ShipStore.Record r = ours && s == null && dir.isDirectory() ? ShipStore.read(dir) : null;
		if (ours && s == null && r == null) return; // no record of her here: a stranger, as before
		String name = s != null ? s.name : r != null ? r.name : null;
		int now = ours ? Store.num(ShipStore.notes(dir, ShipMark.SECTION), "boards", 0) : 0;
		try {
			byte[] copy = SafeFiles.read(cont);
			File kept;
			if (ours) kept = ShipStore.keepVersion(dir, copy, "cloud-");
			else {
				if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
				String stamp;
				synchronized (STAMP) { stamp = STAMP.format(new Date()); }
				kept = new File(dir, m.career + "." + m.id + "." + stamp + ".sav");
				SafeFiles.write(kept, copy);
			}
			if (!cont.delete()) throw new IOException("Could not remove " + cont);
			String where = (ours ? dir.getParentFile().getName() + "/" + dir.getName() + "/" + ShipStore.VERSIONS : SET_ASIDE) + "/" + kept.getName();
			String career = title(m.career);
			cloudCopy = ours
					? "Steam Cloud brought back an old copy of " + homeplanet.parser.ShipNames.the(name) + (s != null ? ", who is already in your fleet" : ", who has left your fleet") + ".\n"
							+ "The copy was set aside in her records, not added as a second ship."
					: "FTL's saves held a ship from your " + career + " fleet, not this one's.\n"
							+ "She was set aside in " + root.getName() + "/" + SET_ASIDE + ", not added to this fleet: switch to " + career + " to fly her there.";
			notes.add("continue.sav was " + (ours ? "a copy of " + name : "a ship of the " + career + " fleet") + " (marked " + m.boards + (ours ? " of " + now : "") + " boardings): set aside in " + where);
			Event e = (s != null ? shipEvent("VAULT", s) : Event.of("VAULT").put("ship", (name != null ? name : "") + "." + m.id).put("ship_name", name).put("ship_id", m.id))
					.put("what", "set_aside").put("file", "continue.sav").put("to", where).put("mark_career", m.career).put("mark_boards", m.boards)
					.put("mark_day", m.day > 0 ? m.day : null).put("boards_now", ours ? now : null).put("in_fleet", s != null).put("other_career", !ours);
			HistoryLog.entry("VAULT", "continue.sav set aside: " + (ours ? "a copy of " + name : "a ship of the " + career + " fleet"), null, e);
		} catch (IOException e) {
			log.warn("Could not set aside the marked continue.sav ({} {}): {}", m.career, m.id, e.toString());
		}
	}
	/**
	 * The fleet's ship this continue.sav is a copy of, byte for byte: her current save, or one of her kept versions
	 * (Steam Cloud restoring the last continue.sav it uploaded after she was docked). Null if it's none of them.
	 */
	private Ship cloudCopyOf(File cont) {
		String h;
		try { h = SafeFiles.hash(cont); } catch (IOException e) { return null; }
		for (Ship s : ships) {
			if (s.state == Ship.State.STORAGE || s.state == Ship.State.BOARDED) continue;
			try { if (fileOf(s).isFile() && h.equals(SafeFiles.hash(fileOf(s)))) return s; } catch (IOException e) { }
			for (File f : kept(s)) { // her versions/ (since 5.69 not her folder itself: concern 6, 5.97)
				try { if (h.equals(SafeFiles.hash(f))) return s; } catch (IOException e) { }
			}
		}
		return null;
	}
	/** This save is hers byte for byte: the save her record names, or one of her kept versions (Board keeps one as it takes her save). */
	private boolean isHers(Ship s, File save) {
		String h;
		try { h = SafeFiles.hash(save); } catch (IOException e) { return false; }
		if (h.equals(s.hash)) return true;
		for (File f : kept(s)) {
			try { if (h.equals(SafeFiles.hash(f))) return true; } catch (IOException e) { }
		}
		return false;
	}

	// ---- sectors travelled (the Immersive stipend) ----

	/** Sectors this fleet's boarded ships have been seen to advance, in all (FTL's progress, not the station's own changes). */
	public synchronized int sectorsSeen() { return Clock.num(this, "sectors", 0); }
	private void addSectors(int n) {
		if (n <= 0) return;
		try { Clock.set(this, "sectors", Integer.toString(sectorsSeen() + n)); }
		catch (IOException e) { log.warn("Could not count the sectors travelled: {}", e.toString()); }
	}

	// ---- beacons travelled (transmissions that answer a reply some beacons later) ----

	/** Beacons this fleet's boarded ships have been seen to explore, in all (FTL's progress, as with the sectors). */
	public synchronized int beaconsSeen() { return Clock.num(this, "beacons", 0); }
	/** One beacon of the fleet's time passes away from FTL (a finished expedition): everything timed counts it. */
	public synchronized void countBeacon() { countBeacon("time passed"); }
	/** As above, with why (the master log's day line: rest, a job, business in the Cargo Bay). */
	public synchronized void countBeacon(String why) { addBeacons(1, why); }
	private void addBeacons(int n, String why) {
		if (n <= 0) return;
		int was = beaconsSeen();
		try { Clock.set(this, "beacons", Integer.toString(was + n)); }
		catch (IOException e) { log.warn("Could not count the beacons travelled: {}", e.toString()); return; }
		for (int i = 1; i <= n; i++) MasterLog.day(this, was + i, why); // each day, and why it passed
	}

	// ---- the fleet's clock: every beacon and sector the boarded ship flies, counted once ----

	/** Where the boarded ship's progress was last counted: her sector and beacons, or null if never (she's counted from her next look). */
	private int[] lastCounted(Ship b) {
		java.util.Properties p;
		try { p = Clock.read(this); } catch (IOException e) { log.warn("Could not read the fleet's clock: {}", e.toString()); p = new java.util.Properties(); }
		try {
			if (b.id.equals(p.getProperty("last.ship"))) return new int[] {Integer.parseInt(p.getProperty("last.sector").trim()), Integer.parseInt(p.getProperty("last.beacons").trim())};
			String[] m = b.marks == null ? new String[0] : b.marks.split("\\|", -1);
			if (m.length == 6) return new int[] {Integer.parseInt(m[2]), Integer.parseInt(m[3])}; // a fleet from before the clock: her marks are where it stopped
		} catch (RuntimeException e) { }
		return null;
	}
	/** Sets where the boarded ship's progress was last counted (after the station writes her, or boards her: nothing to count). */
	private void setClock(Ship b, int sector, int beacons) {
		try { Clock.set(this, "last.ship", b.id, "last.sector", Integer.toString(sector), "last.beacons", Integer.toString(beacons)); }
		catch (IOException e) { log.warn("Could not record the fleet's clock: {}", e.toString()); }
	}
	/**
	 * Counts the boarded ship's progress since it was last counted: the beacons and sectors she has gained, each once.
	 * FTL's saves, a dock, the station's own writes and a look at the Space Dock all call it, so none of her voyage is
	 * missed. A total gone down (a New Journey, an earlier save put back) counts nothing.
	 */
	private void countProgress(Ship b, SavedGameState gs) {
		if (b == null || gs == null) return;
		int sector = gs.getSectorNumber(), beacons = gs.getTotalBeaconsExplored();
		int[] last = lastCounted(b);
		if (last != null) {
			addSectors(sector - last[0]);
			addBeacons(beacons - last[1], beacons - last[1] == 1 ? "a jump" : "a jump (" + (beacons - last[1]) + " counted together)");
			if (last[0] == sector && last[1] == beacons) return;
		}
		setClock(b, sector, beacons);
	}
	/** Before the station writes the boarded ship (or docks her): FTL's progress in continue.sav is counted first. */
	private void countBefore(Ship s) {
		if (s == null || s.state != Ship.State.BOARDED || !continueFile().isFile()) return;
		SavedGameState gs;
		try { gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(continueFile()); } catch (Exception e) { return; }
		if (s.marks != null && !s.marks.isEmpty() && !sameShip(s.marks, gs)) return; // not her: the next look records her lost
		if (ignoring(s)) return; // not the career's ship: nothing of hers counts (5.54)
		countProgress(s, gs);
	}

	// ---- one-time events (what the fleet has been through, for the transmissions that answer it) ----

	private File eventsFile() { return Store.file(root, "events"); } // events.xml (5.86)
	private java.util.Properties events() { return Store.read(eventsFile()); }
	/** What an event recorded (a ship's name, say), or null if it hasn't happened. */
	public synchronized String event(String key) { return events().getProperty(key); }
	/** Records an event once: the first record stands. */
	public synchronized void recordEvent(String key, String value) {
		java.util.Properties p = events();
		if (p.getProperty(key) != null) return;
		p.setProperty(key, value);
		try { Store.write(eventsFile(), p, "What this fleet has been through, once each (Federation Home Planet's transmissions answer them)"); }
		catch (IOException e) { log.warn("Could not record the event {}: {}", key, e.toString()); }
	}
	/** A boarded ship's new save: down to one point of hull with the fight over (no enemy alongside, or one beaten). */
	private void noteHull(Ship b, SavedGameState gs) {
		net.blerf.ftl.parser.SavedGameParser.ShipState s = gs.getPlayerShip(), enemy = gs.getNearbyShip();
		if (s == null || s.getHullAmt() != 1) return;
		if (enemy != null && enemy.getHullAmt() > 0 && enemy.isHostile()) return; // still in the fight
		recordEvent(EVENT_ONE_HULL, gs.getPlayerShipName());
	}
	/** FTL's own running counts of work done at a store or on the ship: buying, repairs, system and reactor upgrades. */
	static final String[] WORK = {"store_purchase", "store_repair", "system_upgrade", "reactor_upgrade"};
	static int workDone(SavedGameState gs) {
		int n = 0;
		for (String k : WORK) if (gs.hasStateVar(k)) n += gs.getStateVar(k);
		return n;
	}
	/**
	 * Time spent on work in FTL counts as a beacon: when the boarded ship has bought, been repaired or upgraded since the
	 * station last looked, with no jump in between. Once per beacon stop; crew walking about never counts.
	 */
	private void noteWork(Ship b, SavedGameState gs) {
		java.util.Properties p;
		try { p = Clock.read(this); }
		catch (IOException e) { log.warn("Could not read the fleet's clock: {}", e.toString()); return; } // never written back from a failed read
		String k = "work." + b.id + "."; // each ship's own stop: switching ships at a beacon doesn't count her work again
		int work = workDone(gs), beacons = gs.getTotalBeaconsExplored();
		boolean here = Integer.toString(beacons).equals(p.getProperty(k + "beacons"));
		int before = Store.num(p, k + "work", -1);
		boolean wasCredited = here && "true".equals(p.getProperty(k + "credited")), credited = wasCredited;
		if (here && before >= 0 && work > before && !credited) {
			VoyageLog.note(this, b, "Time spent on work at the beacon (buying, repairs or upgrades)"); // before the day moves (5.20): told on the stop's own day
			addBeacons(1, "work at a store in FTL");
			credited = true;
		}
		if (here && before == work && credited == wasCredited) return; // nothing new
		try { Clock.set(this, k + "beacons", Integer.toString(beacons), k + "work", Integer.toString(work), k + "credited", Boolean.toString(credited)); }
		catch (IOException e) { log.warn("Could not record her work: {}", e.toString()); }
	}
	/** One of the fleet's ships came out of a battle with one point of hull (her name). */
	public static final String EVENT_ONE_HULL = "one-hull";

	/** Adds scrap to the storage hold (a stipend). */
	public synchronized void depositToStorage(int scrap) throws IOException {
		log.debug("Cargo Hold receives {} scrap", scrap);
		Ship st = storage();
		Copy c = readCopy(st);
		c.save.getPlayerShip().setScrapAmt(c.save.getPlayerShip().getScrapAmt() + scrap);
		begin().put(st, c.save, c.hash).commit();
	}

	// ---- is continue.sav still her? ----

	/** A save's marks: model, name, sector, beacons explored, ships defeated, scrap collected. */
	static String marksOf(SavedGameState gs) {
		return gs.getPlayerShipBlueprintId() + "|" + gs.getPlayerShipName() + "|" + gs.getSectorNumber() + "|" + gs.getTotalBeaconsExplored()
				+ "|" + gs.getTotalShipsDefeated() + "|" + gs.getTotalScrapCollected();
	}
	/** Could this save be the ship last seen with these marks? Same model and name, and no total gone down. */
	static boolean sameShip(String marks, SavedGameState gs) {
		String[] a = marks.split("\\|", -1), b = marksOf(gs).split("\\|", -1);
		if (a.length != 6 || b.length != 6) return true; // marks from elsewhere: nothing to judge by
		if (!a[0].equals(b[0]) || !a[1].equals(b[1])) return false;
		try {
			for (int i = 2; i < 6; i++) if (Long.parseLong(b[i]) < Long.parseLong(a[i])) return false;
		} catch (NumberFormatException e) {
			return true;
		}
		return true;
	}
	private String overwritten = null;
	/** The name of a boarded ship FTL overwrote since this was last asked (her last seen version is in her records), or null. */
	public synchronized String takeOverwritten() { String o = overwritten; overwritten = null; return o; }

	/**
	 * Checks continue.sav against the boarded ship as last seen. If it's her, FTL's progress is noted (and kept in
	 * her records). If not (FTL's New Game wrote over her), she is recorded lost, and continue.sav becomes a new,
	 * uncommissioned ship. True if the manifest changed.
	 */
	private boolean checkBoarded() throws IOException {
		Ship b = boarded();
		if (b == null) return adoptContinue();
		SavedGameState gs = b.save();
		if (gs == null) return false;
		String now = marksOf(gs);
		// her voyage log first, then the clock: what she did at a stop belongs to that stop's day (5.18, the Captain's Log)
		if (b.marks == null || b.marks.isEmpty()) {
			if (ignoring(b)) quietly(b, gs, true);
			else { VoyageLog.observe(this, b, gs); countProgress(b, gs); noteHull(b, gs); }
			b.marks = now;
			return true;
		}
		if (sameShip(b.marks, gs)) {
			if (ignoring(b)) quietly(b, gs);
			else { VoyageLog.observe(this, b, gs); noteWork(b, gs); countProgress(b, gs); noteHull(b, gs); Overflow.note(this, b, gs); } // her voyage log (repairs, trades at a store... change no marks)
		}
		if (now.equals(b.marks)) return false;
		if (sameShip(b.marks, gs)) {
			snapshot(b); // FTL's progress, kept: if FTL later writes over her, this is what comes back
			b.marks = now;
			return true;
		}
		String lostName = b.marks.split("\\|", -1)[1];
		b.name = lostName;
		recordFate(b, Fate.LOST);
		int took = Reputation.lost(this, b);
		if (immersive) offerBack(b, took); // by accident, perhaps: she may be offered back (5.55)
		ships.remove(b);
		Ship n = new Ship(newId(), gs.getPlayerShipName(), Ship.State.BOARDED, gs.isDLCEnabled());
		n.stranger = true;
		n.hash = SafeFiles.hash(continueFile());
		n.marks = now;
		ships.add(n);
		if (ignoring(n)) quietly(n, gs, true); // Immersive Mode: none of her run is the career's (heromedel, 5.54)
		else { setClock(n, 0, 0); countProgress(n, gs); } // FTL's New Game: her run so far was flown in the fleet's time
		overwritten = lostName;
		HistoryLog.entry("OVERWRITTEN", lostName + " (" + b.id + ") was boarded, and continue.sav is now another ship: " + n.name
				+ " (FTL's New Game, most likely). Her last seen version is in " + place(b), null,
				Event.of("OVERWRITTEN").put("ship", lostName + "." + b.id).put("ship_name", lostName).put("ship_id", b.id).put("versions", place(b))
						.put("by", n.name + "." + n.id).put("by_name", n.name).put("by_id", n.id).put("by_stranger", n.stranger));
		return true;
	}
	/**
	 * A ship the station didn't commission, boarded in Immersive Mode (heromedel, 5.54): the station doesn't watch FTL for
	 * her (no voyage log, reputation, clock, stipend time, parcels, crew, final battle) until the player decides what
	 * becomes of her. In Sandbox Mode any ship is the fleet's.
	 */
	public boolean ignoring(Ship b) { return immersive && b != null && b.stranger && b.state == Ship.State.BOARDED; }
	/**
	 * What an ignored ship's look does: keeps up with where she is, so nothing she did is counted later either; FTL's
	 * unlocks and achievements since are seen, never the career's. Not on her first look: what's new in FTL's profile
	 * then is most likely the ship before her, her run ending.
	 */
	private void quietly(Ship b, SavedGameState gs) { quietly(b, gs, false); }
	private void quietly(Ship b, SavedGameState gs, boolean first) {
		VoyageLog.baseline(this, b, gs);
		setClock(b, gs.getSectorNumber(), gs.getTotalBeaconsExplored());
		if (first) return;
		try { homeplanet.parser.UnlockGrants.strangerSeen(homeplanet.parser.Unlocks.read()); } // noted as hers too: the player may have them taken back out (5.55)
		catch (RuntimeException e) { log.warn("Could not note FTL's unlocks while an uncommissioned ship is boarded: {}", e.toString()); }
	}
	/** Nothing boarded, and continue.sav there: FTL started a New Game. She's an uncommissioned ship, boarded (as on opening the fleet). */
	private boolean adoptContinue() throws IOException {
		File cont = continueFile();
		if (!cont.isFile()) return false;
		Ship n = new Ship(newId(), "Unknown ship", Ship.State.BOARDED, true);
		n.stranger = true;
		ships.add(n);
		SavedGameState gs = n.save();
		if (gs == null) { ships.remove(n); return false; } // FTL still writing it: the next look
		n.name = gs.getPlayerShipName();
		n.hash = SafeFiles.hash(cont);
		n.marks = marksOf(gs);
		if (ignoring(n)) quietly(n, gs, true);
		else setClock(n, gs.getSectorNumber(), gs.getTotalBeaconsExplored()); // counted from now, as one found on opening the fleet
		HistoryLog.entry("VAULT", "taking stock", java.util.Collections.singletonList("continue.sav is a ship the station didn't know (a new game started in FTL, most likely): she is now boarded"),
				shipEvent("VAULT", n).put("what", "adopted_continue").put("file", "continue.sav"));
		return true;
	}
	// ---- a career ship FTL's New Game wrote over by accident (heromedel, 5.55) ----

	/** Notes her as one to offer back, if her last kept version can be: out of battle, or in one she can go back into. */
	private void offerBack(Ship b, int repTaken) {
		List<File> kept = history(b);
		if (kept.isEmpty()) return;
		SavedGameState gs;
		try { gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(kept.get(kept.size() - 1)); } catch (Exception e) { return; }
		if (!restorable(gs)) return;
		java.util.Properties p = new java.util.Properties();
		p.setProperty("reputation", Integer.toString(repTaken));
		try { setNotes(historyOf(b), ShipStore.OVERWRITTEN, p); } // her record's overwritten section (5.98; overwritten.txt before)
		catch (IOException e) { log.warn("Could not note {} as one to offer back: {}", b, e.toString()); }
	}
	/**
	 * Can this version of her be put back? With no hostile ship alongside, yes. In a battle, only outside sector 8 and
	 * with her hull above 5: she goes back into that same battle.
	 */
	public static boolean restorable(SavedGameState gs) {
		net.blerf.ftl.parser.SavedGameParser.ShipState s = gs.getPlayerShip(), enemy = gs.getNearbyShip();
		if (s == null) return false;
		boolean fight = enemy != null && enemy.isHostile() && enemy.getHullAmt() > 0;
		return !fight || (gs.getSectorNumber() != 7 && s.getHullAmt() > 5);
	}
	/** Career ships FTL's New Game wrote over that are to be offered back, newest first. */
	public synchronized List<Departed> offeredBack() {
		List<Departed> out = new ArrayList<Departed>();
		for (Departed d : recoverable()) if (d.fate == Fate.LOST && !ShipStore.notes(d.folder, ShipStore.OVERWRITTEN).isEmpty()) out.add(d);
		return out;
	}
	/** The player said no: she stays lost, and isn't asked about again. */
	public synchronized void declineBack(Departed d) {
		try { setNotes(d.folder, ShipStore.OVERWRITTEN, null); }
		catch (IOException e) { log.warn("Could not note that {} stays lost: {}", d.name, e.toString()); }
	}
	/**
	 * Brings her back as the boarded ship from her last kept version (into the same battle, if she was in one): an
	 * uncommissioned ship in continue.sav goes to the Sandbox fleet's Space Dock first; a ship of the fleet's own there
	 * is left boarded, and she comes back docked. Her fate is cleared and what her loss cost in reputation given back.
	 * FTL must be closed. Then {@link CrewRegister#shipBack} for her crew (outside the fleet's lock).
	 */
	public synchronized Ship restoreBack(Departed d) throws IOException {
		if (byId(d.id) != null) throw new IOException(d.name + " is already in the fleet");
		int taken = Store.num(ShipStore.notes(d.folder, ShipStore.OVERWRITTEN), "reputation", 0); // what her loss took (a negative number)
		Ship now = boarded();
		if (now != null && now.stranger) { sendToOtherFleet(now, false); now = boarded(); }
		Ship s;
		if (now != null) {
			s = recover(d); // the fleet's own ship is boarded: she comes back to the Space Dock
		} else {
			s = new Ship(d.id, d.name, Ship.State.BOARDED, true);
			File to = new File(shipyardDir(), d.folder.getName());
			if (to.exists()) throw new IOException(to + " is already there");
			if (!shipyardDir().isDirectory() && !shipyardDir().mkdirs()) throw new IOException("Could not create " + shipyardDir());
			Journal.Note n = Journal.begin(this, "RESTORE"); // one note (5.88): continue.sav back, her fate gone (and the offer: she's back), her folder to the shipyard
			n.replace(continueFile(), SafeFiles.read(d.last));
			byte[] record = recordWithout(d.folder, ShipStore.FATE, ShipStore.OVERWRITTEN);
			if (record != null) n.replace(ShipStore.xml(d.folder), record);
			n.rename(d.folder, to);
			n.commit();
			folders.put(s.id, to);
			s.hash = SafeFiles.hash(continueFile());
			ships.add(s);
			SavedGameState gs = s.save();
			if (gs != null) {
				s.name = gs.getPlayerShipName();
				s.marks = marksOf(gs);
				setClock(s, gs.getSectorNumber(), gs.getTotalBeaconsExplored()); // the clock carries on from her restored point
				VoyageLog.baseline(this, s, gs);
			}
			saveManifest();
		}
		Reputation.restored(this, s, taken); // the offer went with her fate, in the same note that brought her back
		log.info("Restored {} after FTL's New Game: {}/versions/{} -> {}", s.name, place(s), d.last.getName(), s.isBoarded() ? "continue.sav" : place(s));
		HistoryLog.entry("RESTORE", "Restored " + homeplanet.parser.ShipNames.the(s.name) + " after FTL's New Game wrote over her", null,
				shipEvent("RESTORE", s).put("why", "overwritten").put("from", place(s) + "/" + ShipStore.VERSIONS + "/" + d.last.getName()).put("to", s.isBoarded() ? "continue.sav" : place(s)).put("reputation_back", taken));
		return s;
	}

	/** After the station writes the boarded ship: her marks follow (a rename, a New Journey, a retrofit are the station's own). */
	private void marked(Ship s, SavedGameState state) {
		if (s.state == Ship.State.BOARDED && state != null) {
			s.marks = marksOf(state);
			setClock(s, state.getSectorNumber(), state.getTotalBeaconsExplored()); // her progress was counted before the write (countBefore)
		}
		VoyageLog.baseline(this, s, state); // the station's own change: not in her voyage log
	}
	/** FTL has written continue.sav (the save watcher): the boarded ship's voyage log takes note, if it's her. */
	public synchronized void observeBoarded() {
		Ship b = boarded();
		if (b == null || !continueFile().isFile()) return;
		SavedGameState gs;
		try { gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(continueFile()); } catch (Exception e) { return; } // mid-write: Refresh catches up
		if (b.marks != null && !b.marks.isEmpty() && !sameShip(b.marks, gs)) return;
		if (ignoring(b)) { quietly(b, gs); return; } // not the career's ship (5.54)
		VoyageLog.observe(this, b, gs); // her voyage log first: what she did at a stop belongs to that stop's day
		countProgress(b, gs); // the fleet's clock moves as she flies
		noteHull(b, gs);
		Overflow.note(this, b, gs); // an augment she had no room for
	}
	/** A save lying in the shipyard or the Junkyard (copied in by hand, or sent from the other fleet): a ship of her own, in a folder. */
	private void adoptStrays(File dir, Ship.State state, List<String> notes) {
		File[] files = dir.listFiles();
		if (files == null) return;
		java.util.Arrays.sort(files);
		for (File f : files) {
			if (!f.isFile() || !f.getName().toLowerCase().endsWith(".sav")) continue;
			String stem = f.getName().substring(0, f.getName().length() - 4);
			Ship s = new Ship(newId(), stem, state, true);
			try {
				File folder = settleFolder(s);
				SafeFiles.move(f, ShipStore.sav(folder));
			} catch (IOException e) {
				log.warn("Could not adopt {}", f, e);
				folders.remove(s.id);
				continue;
			}
			ships.add(s);
			notes.add(f.getName() + " found in " + dir.getName() + "/: adopted (" + state.key + ")");
		}
	}

	/**
	 * Writes every ship's record into her folder (the manifest's successor, 5.69): a ship whose name changed gets her
	 * folder and files renamed with it; one whose folder sits in the wrong place is moved. The Cargo Hold needs none:
	 * its xml says what it is (5.84).
	 */
	public synchronized void saveManifest() throws IOException {
		for (Ship s : ships) {
			if (s.state == Ship.State.STORAGE) { settleFolder(s); continue; } // its xml is all it needs (5.84)
			File d = settleFolder(s);
			ShipStore.Record r = recordOf(s);
			if (!d.getName().equals(ShipStore.stem(s.name, s.id))) {
				ShipStore.Record old = ShipStore.read(d);
				String was = old == null ? null : old.name;
				r.pastNames.clear(); if (old != null) r.pastNames.addAll(old.pastNames);
				if (was != null && !was.isEmpty() && !was.equals(s.name)) r.pastNames.add(was);
				d = ShipStore.renameTo(d, r);
				folders.put(s.id, d);
			} else {
				ShipStore.write(d, r);
			}
		}
	}

	// ---- history ----

	/** History file names: UTC, so they keep their order across clock changes (daylight saving). */
	private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyyMMdd-HHmmss");
	static { STAMP.setTimeZone(java.util.TimeZone.getTimeZone("UTC")); }
	/** Oldest first: when each was kept, by the stamp in her name (ShipStore.OLDEST_FIRST, 6.08). */
	private static final java.util.Comparator<File> OLDEST_FIRST = ShipStore.OLDEST_FIRST;

	/** Copies her current save into her history folder (before it's changed), keeping the last KEEP. Nothing if her newest kept version is the same. */
	public synchronized void snapshot(Ship s) throws IOException {
		File f = fileOf(s);
		if (!f.isFile()) return;
		File dir = settleFolder(s);
		List<File> kept = history(s);
		if (!kept.isEmpty() && SafeFiles.hash(kept.get(kept.size() - 1)).equals(SafeFiles.hash(f))) return;
		ShipStore.keepVersion(dir, SafeFiles.read(f), null);
		ShipStore.prune(dir, KEEP);
	}
	/**
	 * After the station writes the boarded ship: her new version goes into her history too. FTL ends a run by deleting
	 * continue.sav, and then her history is all that's left of her; without this, a lost ship would come back as she
	 * was before the station's last change (with goods she had already traded away, say). Not fatal if it fails.
	 */
	private void keepBoarded(Ship s) {
		if (s.state != Ship.State.BOARDED) return;
		try { snapshot(s); } catch (IOException e) { log.warn("Could not keep {}'s new version in her history: {}", s, e.toString()); }
	}
	/**
	 * An ordinary kept version ("yyyyMMdd-HHmmss.sav", or "…-2.sav" from the same second): not a copy kept for a reason
	 * of its own (a victory's, a final battle's, the one waiting in final-battle.sav, Steam Cloud's). Only these count
	 * as her versions and are pruned (5.61: the special ones were pruned with the rest, the Museum's victories too).
	 */
	static boolean ordinary(File f) { return f.isFile() && f.getName().matches("\\d{8}-\\d{6}(-\\d+)?\\.(sav|xml)"); }
	/** Her earlier versions, oldest first: the ordinary ones (the copies kept for a reason of their own are in {@link #kept}). */
	public List<File> history(Ship s) { return ShipStore.versions(folderOf(s), false); }
	/** Every save kept of her, oldest first: her versions and the copies kept for a reason of their own (the Records' Restore list). */
	public List<File> kept(Ship s) {
		List<File> out = ShipStore.versions(folderOf(s), false);
		out.addAll(ShipStore.versions(folderOf(s), true));
		File waiting = new File(folderOf(s), FINAL); // the final battle's waiting copy, at her folder's root
		if (waiting.isFile()) out.add(waiting);
		Collections.sort(out, OLDEST_FIRST);
		return out;
	}
	/** The newest save kept in a ship's folder: her newest ordinary version, or the newest of any if she has none. */
	static File newestKept(File folder) {
		List<File> saves = ShipStore.versions(folder, false);
		if (saves.isEmpty()) saves = ShipStore.versions(folder, true);
		if (saves.isEmpty()) return null;
		Collections.sort(saves, OLDEST_FIRST);
		return saves.get(saves.size() - 1);
	}

	// ---- writing ----

	/** Writes a ship's save: her history gets the old version first, then the file is replaced in one move. */
	public synchronized void write(Ship s, SavedGameState state) throws IOException {
		countBefore(s);
		snapshot(s);
		writeQuietly(s, state);
		marked(s, state);
		keepBoarded(s);
		saveManifest();
	}
	private void writeQuietly(Ship s, SavedGameState state) throws IOException {
		if (s.state != Ship.State.BOARDED && s.state != Ship.State.STORAGE) settleFolder(s);
		File f = fileOf(s);
		byte[] bytes = bytesOf(s, state);
		SafeFiles.write(f, bytes);
		s.written(state, SafeFiles.hash(f));
	}

	/** A copy of a ship's save to change and write back, with the fingerprint of the file it came from. */
	public static final class Copy {
		public final SavedGameState save;
		public final String hash;
		Copy(SavedGameState save, String hash) { this.save = save; this.hash = hash; }
	}
	/**
	 * Reads a fresh copy of her save (not the shared parsed one), fingerprinted, so a later write can tell whether FTL
	 * changed the file in between (see {@link Transaction#put(Ship, SavedGameState, String)}).
	 */
	public Copy readCopy(Ship s) throws IOException {
		File f = fileOf(s);
		for (int tries = 0; tries < 3; tries++) {
			String before = SafeFiles.hash(f);
			SavedGameState g = s.state == Ship.State.STORAGE ? homeplanet.parser.HoldXml.read(f) : new net.blerf.ftl.parser.SavedGameParser().readSavedGame(f);
			if (before.equals(SafeFiles.hash(f))) return new Copy(g, before); // else FTL wrote it mid-read: again
		}
		throw new IOException(f.getName() + " kept changing while The Home Planet Station read it. Is FTL running?");
	}

	/** A save changed or vanished since it was read: nothing was written. */
	public static final class StaleException extends IOException {
		public final Ship ship;
		StaleException(Ship ship, boolean gone) {
			super(ship.name + "'s save " + (gone ? "is gone" : "changed") + " since it was read (FTL "
					+ (gone ? "ended her run" : "saved her") + ", most likely). Nothing was saved.");
			this.ship = ship;
		}
	}

	/**
	 * Several ships written together, or none: every save is serialized and written to a temporary file first
	 * (any failure there changes nothing), then the temporaries replace the real files one after another; if one of
	 * those replacements fails, the files already replaced get their old contents back.
	 */
	public final class Transaction {
		private final Map<Ship, SavedGameState> pending = new LinkedHashMap<Ship, SavedGameState>();
		private final Map<Ship, String> expected = new LinkedHashMap<Ship, String>();
		private final Map<File, byte[]> extra = new LinkedHashMap<File, byte[]>();
		public Transaction put(Ship s, SavedGameState state) { twice(s, state); pending.put(s, state); return this; }
		/** As {@link #put(Ship, SavedGameState)}, written only if her file is still the one fingerprinted {@code readHash} (from {@link #readCopy}). */
		public Transaction put(Ship s, SavedGameState state, String readHash) {
			twice(s, state);
			pending.put(s, state);
			if (readHash != null) expected.put(s, readHash);
			return this;
		}
		/** One ship put twice with different contents: the second replaces the first, which is lost (5.61: a Cargo Bay purchase was). Said in the log. */
		private void twice(Ship s, SavedGameState state) {
			SavedGameState had = pending.get(s);
			if (had != null && had != state) log.warn("One save holds two different versions of {}: the second replaces the first", s, new IllegalStateException("put twice"));
		}
		/** Another file that belongs with the change (a stored-systems list), written with the same care. */
		public Transaction put(File f, byte[] bytes) { extra.put(f, bytes); return this; }
		public void commit() throws IOException {
			synchronized (Vault.this) {
				for (Map.Entry<Ship, String> e : expected.entrySet()) {
					File f = fileOf(e.getKey());
					if (!f.isFile()) throw new StaleException(e.getKey(), true);
					if (!SafeFiles.hash(f).equals(e.getValue())) throw new StaleException(e.getKey(), false);
				}
				Map<File, byte[]> bytes = new LinkedHashMap<File, byte[]>();
				for (Map.Entry<Ship, SavedGameState> e : pending.entrySet()) bytes.put(fileOf(e.getKey()), bytesOf(e.getKey(), e.getValue()));
				bytes.putAll(extra);
				// one journal note (5.71): every file's new bytes wait beside it, then each is replaced in one move; a failure puts the rest back
				Journal.Note note = Journal.begin(Vault.this, "SAVE");
				for (Map.Entry<File, byte[]> e : bytes.entrySet()) note.replace(e.getKey(), e.getValue());
				for (Ship s : pending.keySet()) { countBefore(s); snapshot(s); }
				note.commit();
				for (Map.Entry<Ship, SavedGameState> e : pending.entrySet()) { e.getKey().written(e.getValue(), SafeFiles.hash(fileOf(e.getKey()))); marked(e.getKey(), e.getValue()); keepBoarded(e.getKey()); }
				saveManifest();
			}
		}
	}
	public Transaction begin() { return new Transaction(); }

	// ---- moving ships about ----

	/** Takes command of a docked ship: her save becomes continue.sav. Any ship already boarded is docked first. */
	public synchronized void board(Ship s) throws IOException {
		log.debug("Board {} ({})", s.name, s.id);
		if (s.state != Ship.State.DOCKED) throw new IOException(s.name + " isn't docked");
		Ship b = boarded();
		if (b != null) dock();
		File from = fileOf(s), to = continueFile();
		if (to.exists()) throw new IOException("continue.sav is already there (a ship the station doesn't know?)");
		// one journal note (5.71): her save into continue.sav, her vault copy kept as a version (from now on continue.sav is the only current one), her record saying boarded (5.95), her file gone
		byte[] save = SafeFiles.read(from);
		File dir = settleFolder(s);
		// the ship mark (6.08): her board count and today's date in her save's state variables, so a copy of her FTL hands back later is known as hers
		int boards = Store.num(ShipStore.notes(dir, ShipMark.SECTION), "boards", 0) + 1;
		ShipMark.Marked marked = null;
		try { marked = ShipMark.mark(from, slot, s.id, boards); }
		catch (Exception e) { log.warn("Could not mark {}'s save (boarded without it): {}", s.name, e.toString()); }
		byte[] boardedSave = marked != null ? marked.bytes : save;
		ShipStore.Record record = recordOf(s);
		record.state = Ship.State.BOARDED.key; record.hash = SafeFiles.hash(boardedSave); record.marks = "";
		if (marked != null) { record.section(ShipMark.SECTION).setProperty("boards", Integer.toString(boards)); record.section(ShipMark.SECTION).setProperty("day", Integer.toString(ShipMark.today())); }
		Journal.Note note = Journal.begin(this, "BOARD");
		note.replace(to, boardedSave);
		List<File> kept = history(s);
		if (kept.isEmpty() || !SafeFiles.hash(kept.get(kept.size() - 1)).equals(SafeFiles.hash(boardedSave))) note.replace(ShipStore.versionFile(dir, null), boardedSave); // as boarded, her mark in it: the newest version is continue.sav as it starts
		note.replace(ShipStore.xml(dir), ShipStore.bytes(record));
		note.delete(from);
		note.commit();
		ShipStore.prune(dir, KEEP);
		s.state = Ship.State.BOARDED;
		s.hash = SafeFiles.hash(to);
		s.marks = ""; // seen afresh at the next look
		try { SavedGameState gs = marked != null ? marked.gs : homeplanet.core.HomePlanet.savedGameParser.readSavedGame(to); setClock(s, gs.getSectorNumber(), gs.getTotalBeaconsExplored()); }
		catch (Exception e) { log.warn("Could not read her progress as boarded: {}", e.toString()); } // she's counted from her next look instead
		saveManifest();
		HistoryLog.entry("BOARD", s.name + "  " + place(s) + " -> continue.sav", null, shipEvent("BOARD", s).put("from", place(s)).put("to", "continue.sav")
				.put("boards", marked != null ? boards : null).put("mark", marked != null ? ShipMark.COUNT + slot + "." + s.id : null));
	}
	/** Docks the boarded ship: continue.sav comes back into the vault. */
	public synchronized void dock() throws IOException {
		Ship b = boarded();
		if (b == null) return;
		log.debug("Dock {} ({})", b.name, b.id);
		File from = continueFile();
		if (!from.isFile()) throw new IOException("continue.sav is missing: " + b.name + " may have been lost in FTL. Refresh to take stock.");
		countBefore(b); // her voyage since the last look counts before she leaves continue.sav
		File to = ShipStore.sav(settleFolder(b)); // where a docked ship's save lives (fileOf, once she's docked)
		byte[] save = SafeFiles.read(from);
		Journal.Note note = Journal.begin(this, "DOCK"); // one note (5.71): her save into her folder, her record saying docked (5.95), continue.sav gone; a failure leaves her boarded, as she was
		note.replace(to, save);
		note.replace(ShipStore.xml(to.getParentFile()), recordAs(b, Ship.State.DOCKED, SafeFiles.hash(save), null));
		note.delete(from);
		try { note.commit(); }
		catch (IOException e) { throw new IOException(e.getMessage() + " (is FTL running?)", e); }
		b.state = Ship.State.DOCKED;
		b.hash = SafeFiles.hash(to);
		b.invalidate();
		saveManifest();
		HistoryLog.entry("DOCK", b.name + "  continue.sav -> " + place(b), null, shipEvent("DOCK", b).put("from", "continue.sav").put("to", place(b)));
	}
	/** Disbands the boarded ship: continue.sav goes to the junkyard. */
	public synchronized void disband() throws IOException {
		Ship b = boarded();
		if (b == null) return;
		log.debug("Decommission {} ({})", b.name, b.id);
		File from = continueFile(), dir = settleFolder(b), to = new File(junkyardDir(), dir.getName());
		if (to.exists()) throw new IOException(to + " is already there");
		if (!junkyardDir().isDirectory() && !junkyardDir().mkdirs()) throw new IOException("Could not create " + junkyardDir());
		// one note (5.88): continue.sav into her folder, then her folder to the Junkyard; a failure leaves her boarded, as she was
		Journal.Note n = Journal.begin(this, "DISBAND");
		n.replace(ShipStore.sav(dir), SafeFiles.read(from));
		n.delete(from);
		n.rename(dir, to);
		n.commit();
		b.state = Ship.State.JUNKED;
		folders.put(b.id, to);
		b.invalidate();
		saveManifest();
		HistoryLog.entry("DISBAND", b.name + "  continue.sav -> " + place(b), null, shipEvent("DISBAND", b).put("from", "continue.sav").put("to", place(b)));
	}
	/** Salvages a junked ship: back to the ships folder, docked. */
	public synchronized void salvage(Ship s) throws IOException {
		if (s.state != Ship.State.JUNKED) throw new IOException(s.name + " isn't in the junkyard");
		String from = place(s);
		s.state = Ship.State.DOCKED;
		try {
			settleFolder(s); // her folder to the shipyard, as one rename
		} catch (IOException e) {
			s.state = Ship.State.JUNKED;
			throw e;
		}
		s.invalidate();
		saveManifest();
		HistoryLog.entry("SALVAGE", s.name + "  " + from + " -> " + place(s), null, shipEvent("SALVAGE", s).put("from", from).put("to", place(s)));
	}
	/** Removes a ship for good (scrapped or destroyed): her last save goes into her history, and she leaves the manifest. Logged under {@code why} unless null. */
	public synchronized void remove(Ship s, String why) throws IOException {
		remove(s, why, "DESTROY".equals(why) ? Fate.DESTROYED : Fate.SCRAPPED);
	}
	/** An event about a ship: her name and id together (as the log names her), apart, and where she is. */
	public static Event shipEvent(String kind, Ship s) {
		return Event.of(kind).put("ship", s.name + "." + s.id).put("ship_name", s.name).put("ship_id", s.id).put("ship_state", s.state == null ? null : s.state.key).put("stranger", s.stranger ? "true" : null);
	}
	/** The same, recording this fate (a ship traded in or auctioned off is SOLD). */
	public synchronized void remove(Ship s, String why, Fate fate) throws IOException {
		String from = place(s);
		leave(s, fate, null);
		ships.remove(s);
		saveManifest();
		if (why != null) HistoryLog.entry(why, s.name + "  " + from + " -> " + place(s), null,
				shipEvent(why, s).put("fate", fate == null ? null : fate.name().toLowerCase()).put("from", from).put("to", place(s)));
	}
	/**
	 * She leaves the fleet, as one protection note (5.88; by hand before): her save kept as her last version and gone,
	 * her fate written, her folder to the memorial. Her versions are pruned after. The caller takes her off the list.
	 */
	private void leave(Ship s, Fate fate, String detail) throws IOException {
		File dir = settleFolder(s), f = fileOf(s), to = new File(memorialDir(), dir.getName());
		boolean move = !dir.getParentFile().getAbsoluteFile().equals(memorialDir().getAbsoluteFile());
		if (move && to.exists()) throw new IOException(to + " is already there");
		if (!memorialDir().isDirectory() && !memorialDir().mkdirs()) throw new IOException("Could not create " + memorialDir());
		Journal.Note n = Journal.begin(this, "LEAVE");
		if (f.isFile()) {
			List<File> kept = history(s);
			if (kept.isEmpty() || !SafeFiles.hash(kept.get(kept.size() - 1)).equals(SafeFiles.hash(f))) n.replace(ShipStore.versionFile(dir, null), SafeFiles.read(f));
			n.delete(f);
		}
		n.replace(ShipStore.xml(dir), recordWithNotes(dir, ShipStore.FATE, ShipStore.fateNotes(fate.name(), s.name, detail)));
		if (move) n.rename(dir, to);
		n.commit();
		if (move) folders.put(s.id, to);
		ShipStore.prune(folders.get(s.id), KEEP);
	}
	/**
	 * A departed ship's folder back to the shipyard with this save, her fate gone, as one protection note (5.88): the
	 * save written in her folder where it is, then the folder moved. Returns her folder.
	 */
	private File comeHome(Ship s, File folder, byte[] save) throws IOException {
		File to = new File(shipyardDir(), folder.getName());
		if (to.exists()) throw new IOException(to + " is already there");
		if (!shipyardDir().isDirectory() && !shipyardDir().mkdirs()) throw new IOException("Could not create " + shipyardDir());
		Journal.Note n = Journal.begin(this, "COME_HOME");
		n.replace(ShipStore.sav(folder), save);
		byte[] record = recordWithout(folder, ShipStore.FATE, ShipStore.OVERWRITTEN); // her fate gone, and any offer to bring her back: she's back
		if (record != null) n.replace(ShipStore.xml(folder), record);
		n.rename(folder, to);
		n.commit();
		folders.put(s.id, to);
		return to;
	}
	/** Where her save or folder is, in words for the log: "shipyard/Kestrel.a3f2", "memorials_and_records/ships/Kestrel.a3f2". */
	String place(Ship s) {
		File d = folders.get(s.id);
		if (d == null) d = folderOf(s);
		File p = d.getParentFile();
		String parent = p.getAbsoluteFile().equals(memorialDir().getAbsoluteFile()) ? "memorials_and_records/ships" : p.getName();
		return parent + "/" + d.getName();
	}
	// ---- ships that left, and earlier versions ----

	/** Why a ship left the fleet, kept in her history folder: it decides whether she can be recovered. */
	public enum Fate {
		/** Destroyed from the Junkyard: her last save is whole. */
		DESTROYED,
		/** FTL ended her run while she was boarded (continue.sav deleted): her last save is the one she was boarded with. */
		LOST,
		/** Stripped for parts: everything aboard went into storage, so she can't come back without duplicating it. */
		SCRAPPED,
		/** Sold to the Federation Museum after a final victory: her price was paid, so she doesn't come back. */
		MUSEUM,
		/** Traded in or auctioned off from the Junkyard: she was paid for, so she doesn't come back. */
		SOLD,
		/** Traded to another commander's fleet over Long Range Comm. (or on her way, in escrow): she flies for them now. */
		TRANSFERRED,
		/** Given back to her owner (the repair job): she was never the fleet's to keep. */
		RETURNED,
		/** Taken by the Federation Office of Salvage and Claims (the repair job): she doesn't come back. */
		SEIZED
	}
	/** Her fate goes in her record's fate section (5.98; fate.txt before): {@link ShipStore#fate}. */
	private void recordFate(Ship s, Fate fate) {
		try {
			File dir = settleFolder(s);
			setNotes(dir, ShipStore.FATE, ShipStore.fateNotes(fate.name(), s.name, null));
			toMemorial(s); // she left the fleet: her folder is a record now
		} catch (IOException e) {
			log.warn("Could not record what became of {}: {}", s, e.toString());
		}
	}

	/** A ship that left the fleet and can come back: her id, name, what became of her, and her last kept save. */
	public static final class Departed {
		public final String id, name;
		public final Fate fate;
		/** Her last kept save, and her folder (her records; the save sits in its versions/). */
		public final File last, folder;
		Departed(String id, String name, Fate fate, File last, File folder) { this.id = id; this.name = name; this.fate = fate; this.last = last; this.folder = folder; }
	}
	/** Destroyed and lost ships with a kept save, newest departure first. Scrapped ships never come back (their gear is in storage). */
	public synchronized List<Departed> recoverable() {
		List<Departed> out = new ArrayList<Departed>();
		for (File d : departedFolders()) {
			String[] fate = ShipStore.fate(d);
			if (fate == null) continue; // left before fates were kept: what became of her isn't known
			try {
				Fate f = Fate.valueOf(fate[0]);
				if (f == Fate.SCRAPPED || f == Fate.MUSEUM || f == Fate.SOLD || f == Fate.TRANSFERRED || f == Fate.RETURNED || f == Fate.SEIZED) continue; // a traded ship brought back would be in two fleets
				File last = newestKept(d);
				if (last == null) continue;
				out.add(new Departed(ShipStore.idOf(d), fate[1].isEmpty() ? ShipStore.idOf(d) : fate[1], f, last, d));
			} catch (Exception e) {
				log.warn("Could not read {}'s fate ({}): {}", d.getName(), fate[0], e.toString());
			}
		}
		java.util.Collections.sort(out, new java.util.Comparator<Departed>() {
			public int compare(Departed a, Departed b) { return Long.compare(leftAt(b.folder), leftAt(a.folder)); }
		});
		return out;
	}
	/** When she left: her fate's time, or her record's for a fate written before 5.98 without one. */
	private static long leftAt(File folder) {
		try { return Long.parseLong(ShipStore.notes(folder, ShipStore.FATE).getProperty("when", "").trim()); }
		catch (NumberFormatException e) { return ShipStore.xml(folder).lastModified(); }
	}
	/** Brings a departed ship back to the Space Dock, docked, from her last kept save (which stays in her history too). */
	public synchronized Ship recover(Departed d) throws IOException {
		if (byId(d.id) != null) throw new IOException(d.name + " is already in the fleet");
		Ship s = new Ship(d.id, d.name, Ship.State.DOCKED, true);
		String from = place(s) + "/" + ShipStore.VERSIONS + "/" + d.last.getName();
		byte[] bytes = SafeFiles.read(d.last);
		File folder = comeHome(s, d.folder, bytes); // her folder back to the shipyard, her save in it
		File to = ShipStore.sav(folder);
		s.hash = SafeFiles.hash(to);
		ships.add(s);
		if (new File(folder, FINAL).isFile()) closeFinal(new FinalBattle(d.id, d.name, new File(folder, FINAL), -1, -1, ""), false); // settled by coming back
		saveManifest();
		s.save(); // her name and DLC flag, as the save has them
		HistoryLog.entry("RECOVER", s.name + " (" + d.fate.name().toLowerCase() + ")  " + from + " -> " + place(s), null,
				shipEvent("RECOVER", s).put("fate", d.fate.name().toLowerCase()).put("from", from).put("to", place(s)));
		return s;
	}
	/** Puts one of her earlier versions back as her current save; the one it replaces goes into her history first. */
	public synchronized void restore(Ship s, File version) throws IOException {
		if (s.state == Ship.State.STORAGE) throw new IOException("The Cargo Hold has no earlier versions to go back to");
		byte[] bytes = SafeFiles.read(version); // before the snapshot below, which may prune it
		countBefore(s);
		snapshot(s);
		File f = fileOf(s);
		SafeFiles.write(f, bytes);
		s.invalidate();
		s.hash = SafeFiles.hash(f);
		marked(s, s.save());
		keepBoarded(s);
		saveManifest();
		log.info("Restored {}: {}/versions/{} -> {}", s.name, place(s), version.getName(), s.isBoarded() ? "continue.sav" : place(s));
		HistoryLog.entry("RESTORE", "Restored " + homeplanet.parser.ShipNames.the(s.name) + " to an earlier version", null, // in words; the files in the debug log (heromedel, 5.53)
				shipEvent("RESTORE", s).put("why", "version").put("from", place(s) + "/" + ShipStore.VERSIONS + "/" + version.getName()).put("to", s.isBoarded() ? "continue.sav" : place(s)));
	}

	// ---- Long Range Comm.: ships that change hands ----

	/** What travels with a ship: her save, her voyage log and its last look, and her last trade mark. */
	static final String[] PACKAGE = {"ship.sav", VoyageLog.LOG, VoyageLog.LAST, TradeMark.FILE, "papers.txt"};
	/** Her record and her events in a package (5.75): an older station leaves them out (unknown files are dropped), and a package without them is read as before. */
	static final String PACKAGE_RECORD = "record.xml", PACKAGE_EVENTS = "events.log";
	/** Her crew's files from the crew register, under crew/ in her package (5.90): an older station leaves them out. */
	static final String PACKAGE_CREW = "crew/";
	private static final int PACKAGE_MAX = 16 * 1024 * 1024;

	/** A docked ship's package for another station (a zip of {@link #PACKAGE}). */
	public synchronized byte[] packageOf(Ship s) throws IOException {
		java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
		java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(bo);
		try {
			// her save, her voyage log in prose if she has one from before 5.76, her last look and her trade mark: the last two
			// from her record (5.98), as the files every station reads them from
			java.util.Properties last = ShipStore.notes(historyOf(s), ShipStore.LAST), mark = ShipStore.notes(historyOf(s), ShipStore.TRADE);
			File prose = new File(historyOf(s), VoyageLog.LOG);
			byte[][] files = {SafeFiles.read(fileOf(s)), prose.isFile() ? SafeFiles.read(prose) : null,
					last.isEmpty() ? null : Store.bytes(last, VoyageLog.LAST_NOTE), mark.isEmpty() ? null : TradeMark.fileBytes(mark)};
			for (int i = 0; i < files.length; i++) {
				if (files[i] == null) continue;
				z.putNextEntry(new java.util.zip.ZipEntry(PACKAGE[i]));
				z.write(files[i]);
				z.closeEntry();
			}
			// her papers: when she was first commissioned, carried through every trade
			TradeMark old = TradeMark.of(this, s.id);
			String commissioned = old != null && !old.commissioned.isEmpty() ? old.commissioned : homeplanet.parser.Museum.commissioned(this, s.id);
			java.util.Properties papers = new java.util.Properties();
			papers.setProperty("commissioned", commissioned);
			z.putNextEntry(new java.util.zip.ZipEntry(PACKAGE[4]));
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
			ShipStore.Record record = recordOf(s);
			record.sections.clear();
			z.putNextEntry(new java.util.zip.ZipEntry(PACKAGE_RECORD));
			z.write(ShipStore.bytes(record));
			z.closeEntry();
			StringBuilder events = new StringBuilder();
			for (EventLog.Entry e : EventLog.voyage(ShipStore.entries(folderOf(s)), s.id)) events.append(EventLog.text(e)); // her own log (5.76)
			z.putNextEntry(new java.util.zip.ZipEntry(PACKAGE_EVENTS));
			z.write(events.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			z.closeEntry();
			File[] crew = new File(folderOf(s), CrewRegister.CREW_DIR).listFiles(); // her crew's files: their past goes with them (5.90)
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
	public static Map<String, byte[]> unpack(byte[] pkg) throws IOException {
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
	public synchronized void sendAway(Ship s, String to) throws IOException {
		if (s.state != Ship.State.DOCKED) throw new IOException(s.name + " isn't docked");
		leave(s, Fate.TRANSFERRED, to);
		ships.remove(s);
		saveManifest();
		HistoryLog.entry("SENT AWAY", s.name + " (" + s.id + ") to " + to + "'s fleet, over Long Range Comm.", null, shipEvent("SENT_AWAY", s).put("to_commander", to).put("to", place(s)));
	}
	/** A trade with her in it was called off: she comes back docked, from her package (unless she's here already). */
	public synchronized Ship comeBack(String id, byte[] pkg) throws IOException {
		Ship here = byId(id);
		if (here != null) return here;
		byte[] sav = unpack(pkg).get(PACKAGE[0]);
		Ship s = new Ship(id, "", Ship.State.DOCKED, true);
		File was = folders.get(id), to;
		if (was != null && was.isDirectory() && !was.getParentFile().getAbsoluteFile().equals(shipyardDir().getAbsoluteFile())) to = ShipStore.sav(comeHome(s, was, sav)); // her folder from the memorial, as one note (5.88)
		else { to = ShipStore.sav(settleFolder(s)); SafeFiles.write(to, sav); setNotes(to.getParentFile(), ShipStore.FATE, null); }
		s.hash = SafeFiles.hash(to);
		ships.add(s);
		s.save();
		if (s.name == null || s.name.isEmpty()) s.name = "Unknown ship";
		saveManifest();
		HistoryLog.entry("RETURNED", s.name + " (" + id + "): the trade was called off, and she is back at the Space Dock", null, shipEvent("RETURNED", s).put("why", "trade_called_off").put("to", place(s)));
		return s;
	}
	/**
	 * A trade called off after this ship had already been received for it (a later ship's papers missing, say): she goes
	 * back as sent away, since the other station keeps her (5.61: she stayed, and was in both fleets). Her save goes into
	 * her history, and her fate says where she went. Only a ship still docked: one flown since is left, and logged.
	 * Returns her, or null if no ship came for this trade line.
	 */
	public synchronized Ship unreceive(String tradeLine, String to) throws IOException {
		for (Ship s : new ArrayList<Ship>(ships)) {
			TradeMark m = TradeMark.of(this, s.id);
			if (m == null || !m.trade.equals(tradeLine)) continue;
			if (s.state != Ship.State.DOCKED) {
				log.warn("{} came in trade line {}, which was called off, but isn't docked any more: she stays", s, tradeLine);
				HistoryLog.entry("TRADE CALLED OFF", s.name + " (" + s.id + ") came in it and isn't docked any more, so she stays here as well as with " + to + "'s fleet", null,
						shipEvent("TRADE_CALLED_OFF", s).put("what", "stays_both").put("trade", tradeLine).put("peer", to));
				return null;
			}
			leave(s, Fate.TRANSFERRED, to);
			ships.remove(s);
			saveManifest();
			HistoryLog.entry("SENT BACK", s.name + " (" + s.id + "): the trade was called off, and she stays with " + to + "'s fleet", null, shipEvent("SENT_BACK", s).put("trade", tradeLine).put("to_commander", to));
			return s;
		}
		return null;
	}
	/** The trade went through: she flies for the other fleet now (her history stays here, as a record). */
	public synchronized void transferred(String id, String name, String to) throws IOException {
		Ship still = byId(id);
		if (still != null && still.state == Ship.State.DOCKED) sendAway(still, to); // escrow didn't get as far as sending her
		writeFate(id, Fate.TRANSFERRED, name, to);
	}
	private void writeFate(String id, Fate fate, String name, String detail) throws IOException {
		File dir = folders.containsKey(id) ? folders.get(id) : new File(memorialDir(), ShipStore.stem(name, id));
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		folders.put(id, dir);
		setNotes(dir, ShipStore.FATE, ShipStore.fateNotes(fate.name(), name, detail));
	}
	/**
	 * A ship arriving from another station, from her package: docked under a new id, her voyage log kept, a trade
	 * mark (see {@link TradeMark}) naming the sender and her original owner, and set out at The Home Planet Station as
	 * a newly commissioned ship is. A ship already received for this trade line isn't received twice.
	 */
	public synchronized Ship receive(byte[] pkg, byte[] save, SavedGameState gs, String tradeLine, String from) throws IOException {
		for (Ship s : ships) {
			TradeMark m = TradeMark.of(this, s.id);
			if (m != null && m.trade.equals(tradeLine)) return s;
		}
		Map<String, byte[]> files = unpack(pkg);
		try { save = ShipMark.strip(save); } // her old station's mark off: this one marks her at her first Board here (6.08)
		catch (IOException e) { log.warn("Could not take her old station's mark off {}: {}", gs.getPlayerShipName(), e.toString()); }
		Ship s = new Ship(newId(), gs.getPlayerShipName(), Ship.State.DOCKED, gs.isDLCEnabled());
		File dir = settleFolder(s);
		try {
			// her files in one note (5.88): her save as it arrived (or renamed onto a blueprint here, homeplanet.parser.ShipPapers), her voyage, her
			// record with her last look and her trade mark (5.98: they were files of their own), her owners and past names from the record she came with (5.75)
			Journal.Note note = Journal.begin(this, "RECEIVE");
			if (files.containsKey(VoyageLog.LOG)) note.replace(new File(dir, VoyageLog.LOG), files.get(VoyageLog.LOG));
			String original = TradeMark.originalIn(files.get(TradeMark.FILE));
			String commissioned = papersCommissioned(files.get(PACKAGE[4]));
			int sectors = VoyageLog.visited(this, s.id, gs);
			ShipStore.Record record = recordOf(s);
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
			File f = fileOf(s);
			note.replace(f, save);
			File arrived = new File(new File(dir, CrewRegister.CREW_DIR), CrewRegister.ARRIVED); // her crew's files from the other station, taken in as the register meets each (5.90)
			for (Map.Entry<String, byte[]> c : files.entrySet()) if (crewEntry(c.getKey())) note.replace(new File(arrived, c.getKey().substring(PACKAGE_CREW.length())), c.getValue());
			note.commit();
			s.hash = SafeFiles.hash(f);
			ships.add(s);
			if (files.containsKey(PACKAGE_EVENTS)) { // her events, under her new id (5.75); their time is their own, their day this career's today
				Event who = VoyageLog.shipFields(s).put("received_from", from);
				for (EventLog.Entry e : EventLog.parse(new String(files.get(PACKAGE_EVENTS), java.nio.charset.StandardCharsets.UTF_8))) {
					Event x = Event.of(e.kind).put("log", "voyage").putAll(who);
					for (String[] kv : e.fields()) if (!kv[0].matches("log|ship|ship_name|ship_id|ship_state|day|station|received_from")) x.put(kv[0], kv[1]);
					if (x.get("time") == null) x.put("time", e.time);
					EventLog.write(this, x.human(e.human));
				}
			} else if (files.containsKey(VoyageLog.LOG)) { // an older station's package: her voyage log read in as the conversion reads one
				homeplanet.convert.OldPackage.voyage(this, s, from, new String(files.get(VoyageLog.LOG), java.nio.charset.StandardCharsets.UTF_8));
			}
			setOut(s, gs, "Received from " + from + "'s fleet at The Home Planet Station");
			homeplanet.parser.Museum.setCommissioned(this, s.id, commissioned); // her own date, not her arrival
		} catch (IOException e) {
			ships.remove(s);
			SafeFiles.deleteTree(dir);
			folders.remove(s.id);
			try { saveManifest(); } catch (IOException again) { log.error("Could not write the records", again); }
			throw e;
		}
		HistoryLog.entry("RECEIVED", s.name + " (" + s.id + ") from " + from + "'s fleet, over Long Range Comm.: docked", null, shipEvent("RECEIVED", s).put("from_commander", from).put("trade", tradeLine).put("to", place(s)));
		return s;
	}
	/** The commission date a ship's papers give, or "". */
	private static String papersCommissioned(byte[] papers) {
		if (papers == null) return "";
		java.util.Properties p;
		try { p = Store.parse(papers); } catch (IOException e) { return ""; }
		String d = p.getProperty("commissioned", "").trim();
		return d.length() > 40 ? "" : d;
	}
	/** Is a copy kept of her on the way to the last battle (a final battle not yet settled)? Such a ship stays in the fleet. */
	public boolean finalBattlePending(Ship s) { return new File(historyOf(s), FINAL).isFile(); }

	/** A ship just built (commissioned): written into the ships folder, docked. */
	public synchronized Ship adopt(SavedGameState state) throws IOException {
		Ship s = new Ship(newId(), state.getPlayerShipName(), Ship.State.DOCKED, state.isDLCEnabled());
		log.debug("New ship docked: {} ({}), {}, AE {}", s.name, s.id, state.getPlayerShipBlueprintId(), state.isDLCEnabled());
		writeQuietly(s, state); // her file first: a failed write leaves no entry without one
		ships.add(s);
		saveManifest();
		return s;
	}
	/** A ship that arrives as a wreck (a gift of salvage): written straight into the Junkyard. */
	public synchronized Ship adoptJunked(SavedGameState state) throws IOException {
		Ship s = new Ship(newId(), state.getPlayerShipName(), Ship.State.JUNKED, state.isDLCEnabled());
		writeQuietly(s, state); // her file first: a failed write leaves no entry without one
		ships.add(s);
		saveManifest();
		return s;
	}
	/** A save file from elsewhere (a file the player dropped in, a converter's) taken into the vault: the file is moved. */
	public synchronized Ship adoptFile(File f, Ship.State state, String cachedName) throws IOException {
		Ship s = new Ship(newId(), cachedName, state, true);
		if (state != Ship.State.BOARDED) settleFolder(s);
		File to = fileOf(s);
		if (state == Ship.State.BOARDED) {
			if (!to.getAbsoluteFile().equals(f.getAbsoluteFile())) SafeFiles.move(f, to);
		} else {
			SafeFiles.move(f, to);
		}
		ships.add(s);
		s.save();
		if (s.name == null || s.name.isEmpty()) s.name = cachedName;
		s.hash = SafeFiles.hash(to);
		saveManifest();
		return s;
	}

	// ---- who uses a blueprint ----

	/**
	 * The ships (docked, boarded or junked) whose saves name this blueprint id. A save that can't be read at all
	 * counts as using it, since it might: a locked or damaged file must never get a blueprint retired under it.
	 */
	public synchronized List<Ship> usingBlueprint(String bpId) {
		List<Ship> out = new ArrayList<Ship>();
		for (Ship s : ships) {
			if (s.state == Ship.State.STORAGE) continue;
			List<String> ids = homeplanet.parser.Retrofit.blueprintIds(fileOf(s));
			if (ids == null || ids.contains(bpId)) out.add(s);
		}
		return out;
	}
	/** Every blueprint id any ship names (null entries for unreadable files are skipped; see {@link #usingBlueprint}). */
	public synchronized java.util.Set<String> blueprintsInUse() {
		java.util.Set<String> out = new java.util.LinkedHashSet<String>();
		for (Ship s : ships) {
			if (s.state == Ship.State.STORAGE) continue;
			List<String> ids = homeplanet.parser.Retrofit.blueprintIds(fileOf(s));
			if (ids != null) out.addAll(ids);
		}
		return out;
	}
	/**
	 * As {@link #blueprintsInUse}, plus every blueprint the ships' kept earlier versions (history) name: a blueprint
	 * in here may still be needed to bring a ship back, so it isn't cleaned up.
	 */
	public synchronized java.util.Set<String> blueprintsInUseOrHistory() {
		java.util.Set<String> out = blueprintsInUse();
		out.addAll(otherFleetBlueprints()); // the other fleet flies the same mod
		List<File> saves = new ArrayList<File>();
		for (File d : shipFolders()) collectSaves(d, saves, 2); // her save and her kept versions
		collectSaves(surrenderedDir(), saves, 4); // a surrender can be undone: its ships still count (their folders, with versions, or an old one's saves)
		for (File f : saves) {
			List<String> ids = homeplanet.parser.Retrofit.blueprintIds(f);
			if (ids != null) out.addAll(ids);
		}
		return out;
	}
	/**
	 * Every blueprint the other fleet's saves name: its docked and junked ships, their kept versions and surrenders.
	 * (Its boarded ship, if any, was docked into it when the player switched fleets.)
	 */
	public java.util.Set<String> otherFleetBlueprints() {
		java.util.Set<String> out = new java.util.LinkedHashSet<String>();
		for (File f : otherFleetSaves()) {
			List<String> ids = homeplanet.parser.Retrofit.blueprintIds(f);
			if (ids != null) out.addAll(ids);
		}
		return out;
	}
	/** The names of every other fleet's ships (docked or junked) whose saves name this blueprint. */
	public List<String> otherFleetUsing(String bpId) {
		List<String> out = new ArrayList<String>();
		for (String k : SLOTS) {
			if (k.equals(slot)) continue;
			File other = rootOf(saves, k);
			Map<String, String> names = homeplanet.convert.OldFleet.manifestNames(other);
			for (String dir : new String[] {homeplanet.convert.OldFleet.SHIPS, "junkyard", "shipyard"}) {
				File[] fs = new File(other, dir).listFiles();
				if (fs == null) continue;
				for (File f : fs) {
					String name;
					if (f.isDirectory()) { // her folder (5.69)
						ShipStore.Record r = ShipStore.read(f);
						if (r == null || !ShipStore.sav(f).isFile()) continue;
						f = ShipStore.sav(f);
						name = r.name;
					} else if (f.getName().endsWith(".sav")) { // the old layout, or a stray
						String id = f.getName().substring(0, f.getName().length() - 4);
						name = names.containsKey(id) ? names.get(id) : id;
					} else continue;
					List<String> ids = homeplanet.parser.Retrofit.blueprintIds(f);
					if (ids != null && !ids.contains(bpId)) continue; // an unreadable one counts, as usingBlueprint does
					out.add(name + " (" + title(k) + " fleet)");
				}
			}
		}
		return out;
	}
	private List<File> otherFleetSaves() {
		List<File> out = new ArrayList<File>();
		for (File other : otherRoots())
			for (String dir : new String[] {"ships", "junkyard", "history", "shipyard", "memorials_and_records", "surrendered"}) collectSaves(new File(other, dir), out, 4); // both layouts
		return out;
	}
	private static void collectSaves(File dir, List<File> out, int depth) {
		File[] fs = dir.listFiles();
		if (fs == null) return;
		for (File f : fs) {
			if (f.isDirectory() && depth > 1) collectSaves(f, out, depth - 1);
			else if (f.isFile() && f.getName().endsWith(".sav")) out.add(f);
		}
	}
	// ---- switching fleets (Immersive Mode) ----

	/**
	 * Switches to the other fleet: the boarded ship is docked into this one (and noted, to be boarded again on
	 * return), then the other fleet's vault is opened and its noted ship boarded. FTL must be closed (the caller
	 * checks). Returns the vault now in use. On a failure before the other fleet opens, nothing has changed but a dock.
	 */
	public static Vault switchFleet(boolean toImmersive) throws IOException {
		return switchFleet(toImmersive ? immersiveSlot : SANDBOX);
	}
	/** As {@link #switchFleet(boolean)}, to this mode's fleet. */
	public static Vault switchFleet(String toSlot) throws IOException {
		Vault from = get();
		if (from.slot.equals(toSlot)) return from;
		from.reload();
		Ship b = from.boarded();
		File park = new File(from.root, PARKED);
		if (b != null) {
			from.dock();
			SafeFiles.writeText(park, b.id + "\n", false);
		} else if (park.isFile() && !park.delete()) {
			log.warn("Could not remove {}", park);
		}
		HistoryLog.entry("SWITCH FLEET", "to the " + title(toSlot) + " fleet" + (b == null ? "" : "; " + b.name + " docked here, to be boarded again on return"), null,
				Event.of("SWITCH_FLEET").put("stage", "leaving").put("to_fleet", title(toSlot)).put("parked", b == null ? null : b.name + "." + b.id));
		Vault to = open(from.saves, toSlot);
		File back = new File(to.root, PARKED);
		if (back.isFile()) {
			String id = new String(SafeFiles.read(back), java.nio.charset.StandardCharsets.UTF_8).trim();
			Ship s = to.byId(id);
			if (s != null && s.state == Ship.State.DOCKED && !to.continueFile().exists()) to.board(s);
			if (!back.delete()) log.warn("Could not remove {}", back);
		}
		HistoryLog.entry("SWITCH FLEET", "now the " + title(toSlot) + " fleet" + (to.boarded() == null ? "" : "; " + to.boarded().name + " boarded again"), null,
				Event.of("SWITCH_FLEET").put("stage", "arrived").put("to_fleet", title(toSlot)).put("boarded", to.boarded() == null ? null : to.boarded().name + "." + to.boarded().id));
		return to;
	}

	/**
	 * An uncommissioned ship leaves this fleet for the other one's Space Dock or Junkyard: her save is moved there
	 * under her name, and the other fleet takes her in the next time it opens (as it does any save dropped in).
	 */
	/** Where ended Immersive careers are kept, zipped (in the normal fleet's folder). */
	public static final String OLD_CAREERS = "old-immersive-careers";
	/**
	 * Ends the Immersive career (from the normal fleet, Immersive Mode already left): its whole folder (ships, storage,
	 * records, its own FTL profile) is zipped into old-immersive-careers/, checked, and only then deleted. Returns the zip.
	 */
	public static File endImmersiveCareer() throws IOException {
		return endCareer(immersiveSlot);
	}
	/** Ends this Immersive career, as {@link #endImmersiveCareer()}; its fleet mustn't be the one in use. */
	public static File endCareer(String slot) throws IOException {
		Vault v = get();
		if (SANDBOX.equals(slot)) throw new IOException("Sandbox Mode has no career to end");
		if (v.slot.equals(slot)) throw new IOException("Switch to another mode first");
		File im = rootOf(v.saves, slot);
		if (!im.isDirectory()) throw new IOException("There is no " + title(slot) + " career to end");
		String stamp;
		synchronized (STAMP) { stamp = STAMP.format(new Date()); }
		File zip = new File(new File(v.shared, OLD_CAREERS), title(slot) + " career " + stamp + ".zip");
		SafeFiles.zipFolder(im, zip, null);
		int files = countFiles(im), zipped;
		java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip);
		try { zipped = 0; for (java.util.Enumeration<? extends java.util.zip.ZipEntry> en = z.entries(); en.hasMoreElements(); ) if (!en.nextElement().isDirectory()) zipped++; } finally { z.close(); } // its files (its folders are entries too, 5.94)
		if (zipped != files) throw new IOException("The copy in " + zip + " is incomplete (" + zipped + " of " + files + " files): nothing was deleted");
		if (!SafeFiles.deleteTree(im))
			throw new IOException("Some of " + im + " could not be deleted (a file in use?). The whole career is kept in " + zip
					+ "; delete the folder by hand once The Home Planet Station is closed");
		HistoryLog.entry("CAREER ENDED", "the " + title(slot) + " career was ended; a copy is kept in " + OLD_CAREERS + "/" + zip.getName(), null,
				Event.of("CAREER_ENDED").put("fleet", title(slot)).put("copy", OLD_CAREERS + "/" + zip.getName()).put("files", files));
		return zip;
	}
	private static int countFiles(File dir) {
		File[] fs = dir.listFiles();
		int n = 0;
		if (fs != null) for (File f : fs) n += f.isDirectory() ? countFiles(f) : f.isFile() ? 1 : 0;
		return n;
	}

	public synchronized void sendToOtherFleet(Ship s, boolean junkyard) throws IOException {
		File other = otherRoot();
		boolean old = homeplanet.convert.OldFleet.notConverted(other); // not converted yet: it adopts strays from its old folders
		File dir = new File(other, junkyard ? "junkyard" : old ? homeplanet.convert.OldFleet.SHIPS : "shipyard");
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		String stem = (s.name == null || s.name.trim().isEmpty() ? "Unknown ship" : s.name.trim()).replaceAll("[\\\\/:*?\"<>|]", "_");
		File to = new File(dir, stem + ".sav");
		for (int i = 2; to.exists(); i++) to = new File(dir, stem + " " + i + ".sav");
		String from = s.isBoarded() ? "continue.sav" : place(s);
		SafeFiles.move(fileOf(s), to);
		ships.remove(s);
		toMemorial(s); // her folder stays here, as a record (a boarded one's too: her record must not read as boarded next time)
		saveManifest();
		HistoryLog.entry("SENT", s.name + "  " + from + " -> the "
				+ (immersive ? "Sandbox" : title(immersiveSlot)) + " fleet's " + (junkyard ? "Junkyard" : "Space Dock"), null,
				shipEvent("SENT", s).put("from", from).put("to_fleet", immersive ? "Sandbox" : title(immersiveSlot)).put("to", dir.getName() + "/" + to.getName()));
	}
	/**
	 * The player takes this boarded ship to the other fleet and switches to it (an uncommissioned ship in Immersive
	 * Mode, flown in the normal fleet instead): she leaves this fleet's records, continue.sav stays, and the other
	 * fleet opens with her boarded. Its own parked ship stays docked. Returns the vault now in use.
	 */
	public static Vault handOverBoarded(Ship s) throws IOException {
		Vault from = get();
		if (s.state != Ship.State.BOARDED || !from.ships.contains(s)) throw new IOException(s.name + " isn't the boarded ship");
		synchronized (from) {
			from.ships.remove(s);
			from.toMemorial(s); // her folder stays as a record; not read as boarded next time (continue.sav is the other fleet's now)
			from.saveManifest();
		}
		HistoryLog.entry("HANDED OVER", s.name + " (continue.sav) to the " + (from.immersive ? "Sandbox" : title(immersiveSlot)) + " fleet, now in use", null,
				shipEvent("HANDED_OVER", s).put("file", "continue.sav").put("to_fleet", from.immersive ? "Sandbox" : title(immersiveSlot)));
		Vault to = open(from.saves, from.immersive ? SANDBOX : immersiveSlot);
		File park = new File(to.root, PARKED);
		if (park.isFile() && !park.delete()) log.warn("Could not remove {}", park);
		Ship b = to.boarded();
		if (b != null) b.stranger = false; // she's this fleet's now
		to.saveManifest();
		return to;
	}

	/** True if any ship's save couldn't even be scanned (so "unused" can't be trusted). */
	public synchronized boolean anyUnscannable() {
		for (Ship s : ships) {
			if (s.state == Ship.State.STORAGE) continue;
			if (homeplanet.parser.Retrofit.blueprintIds(fileOf(s)) == null) return true;
		}
		return false;
	}
}
