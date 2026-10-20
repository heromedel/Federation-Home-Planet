package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.Event;
import homeplanet.core.StationLog;
import homeplanet.core.SafeFiles;
import homeplanet.core.Store;
import homeplanet.model.Words;
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
 *     <ship folder>/crew/        her crew, a file each (5.83: <Name>.<id>.xml, the crew register's id; plain tags since 6.11, CrewRecord); the same in cargohold/crew/,
 *                                expeditions/crew/, captives/crew/ and memorials_and_records/crew/ (everyone who left); crew-register.xml the register's own state
 *     expeditions/               the crew expeditions (5.85): expeditions.xml (the sectors on offer, the crew away), crew/,
 *                                board-before-5.67.txt (the old board of jobs, kept, unread)
 *     infirmary/                 infirmary.xml (who is laid up and until when; they stay in the Cargo Hold) (5.85)
 *     captives/                  captives.xml (who was taken, the ransoms asked), crew/ (5.85)
 *     cargohold/                 the Cargo Hold (5.72): cargohold.xml (what it holds, 5.84; the pretend ship's save cargohold.sav
 *                                before), crew/, systems.txt (its stored systems, StoredSystems), parts.txt, overflow.txt, versions/
 *     logs/                      the station's log (5.71): events.log (every entry, two lines each)
 *     set-aside/                 a continue.sav marked as another career's ship (6.08, ShipMark), set aside rather than taken in
 *     declined.xml               what the player said no to taking in (6.10: a loose save, a ship's folder): not asked again
 *     station-action-protection/ the notes of actions under way (5.71; heromedel's name, 5.78), only its why.txt when the station is at rest
 *     clock.xml                  the fleet's clock (5.86; five .txt files before): the sectors and beacons travelled, the day 1
 *     career.xml, reputation.xml, rest.xml, rank.xml, repair-job.xml, events.xml, crew-register.xml: the career's own state,
 *                                one file per concern (5.86; .txt before); free-command.txt and unlock-grants.txt, two lists
 *     designs.xml, remodels.xml, art/, removed-blueprints.log
 *     rules.xml                  Sandbox Mode's fleet only: its rules, as Settings sets them (6.55; the station's own settings are in
 *                                federation-home-planet.cfg beside the fleets, in FTL's saves folder)
 * </pre>
 *
 * A fleet from before 6.0 is not read (7.0): the conversion that brought one across went with 6.x.
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
	/** How many earlier versions of a ship's save are kept. */
	public static final int KEEP = 10;

	private static volatile Vault instance; // read off any thread (Long Range Comm., the clock), set on one
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
		Calendar.start(v); // the career's day 1, the first time it's opened on 5.17 (a new one: its first moment)
		v.load();
		homeplanet.core.HomePlanet.fleetOpened(v); // its own settings in force (6.55: how reputation can be used)
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
	final List<Ship> ships = new ArrayList<Ship>();
	/** What watches continue.sav for this fleet, and keeps what the player is told of it. */
	private final ContinueWatch watch = new ContinueWatch(this);

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
		for (String dir : new String[] {"junkyard", "shipyard"}) {
			File[] fs = new File(fleetRoot, dir).listFiles();
			if (fs != null) for (File f : fs) {
				if (f.isFile() && f.getName().endsWith(".sav")) n++; // a stray
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
	/** The ships at the Space Dock, a folder each (5.69). */
	public File shipyardDir() { return new File(root, "shipyard"); }
	/** The hulls in the Junkyard, a folder each. */
	public File junkyardDir() { return new File(root, "junkyard"); }
	/** Every ship that left the fleet, remembered: a folder each, with how she left (fate.txt) and her kept versions. */
	public File memorialDir() { return new File(new File(root, "memorials_and_records"), "ships"); }
	/** Her folder by id, for every ship the fleet has or remembers (filled on opening, kept up as ships move). */
	final Map<String, File> folders = new LinkedHashMap<String, File>();
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
	File settleFolder(Ship s) throws IOException {
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
	void toMemorial(Ship s) throws IOException {
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
	/** The station's log (events.log), in logs/ (heromedel's name, 5.71). */
	public File logsDir() { return new File(root, "logs"); }
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
	/** A fleet's Cargo Hold file (cargohold.xml), for a fleet not in use. */
	public static File holdFileIn(File fleetRoot) { return new File(new File(fleetRoot, HOLD_DIR), HOLD_FILE); }
	/** A hold file's bytes. */
	public static byte[] holdBytes(File f, SavedGameState gs) throws IOException {
		return homeplanet.parser.HoldXml.toBytes(gs);
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
			case STORAGE: return new File(cargoHoldDir(), HOLD_FILE);
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
			SavedGameState empty = SaveHelper.createStorageSave(s.name, true);
			writeQuietly(s, empty);
			saveManifest();
		}
		return s;
	}

	// ---- a ship just set out: still at The Home Planet Station ----

	static String position(SavedGameState gs) { return gs.getSectorNumber() + "|" + gs.getCurrentBeaconId(); }
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

	/**
	 * Is a free command granted and not yet taken? An empty shipyard alone never grants one: the captain commissions a
	 * ship with scrap, or pleads for a new one. (A fleet from before this was recorded: granted if its shipyard is
	 * empty now, once.)
	 */
	public synchronized boolean freeCommandOpen() { return Plea.freeCommandOpen(this); }
	/** Grants the free command (a new fleet or career); its ship is Settings' or the career's. */
	public synchronized void grantFreeCommand(String why) { Plea.grantFreeCommand(this, why); }
	/** Grants the free command with the ship it brings ({@link homeplanet.parser.FreeCommand}: an Immersive career). */
	public synchronized void grantFreeCommand(String why, String ship) { Plea.grantFreeCommand(this, why, ship); }
	/** The ship the open free command names, or null: the plea's or Settings' (see FreeCommand). */
	public synchronized String freeCommandShip() { return Plea.freeCommandShip(this); }
	/** Was the open free command granted by a plea for a new ship (rather than a new fleet or career)? */
	public synchronized boolean freeCommandReassigned() { return Plea.freeCommandReassigned(this); }
	/** The free command is taken (her commission), or taken back (an undone report). */
	public synchronized void useFreeCommand(String why) { Plea.useFreeCommand(this, why); }
	/** The scrap in the storage hold (0 if it can't be read). */
	public int storageScrap() { return Plea.storageScrap(this); }
	/**
	 * Pays scrap from the storage hold (a commission, a journey's fee). Refuses, changing nothing, if the hold has less.
	 * Returns the hold's file as it was. Paying for something written as well: {@link Transaction#pay}, in the same note.
	 */
	public synchronized byte[] payFromStorage(int scrap) throws IOException { return Plea.payFromStorage(this, scrap); }

	// ---- Plead for New Ship ----

	/**
	 * Plead for New Ship: The Federation Home Planet agrees to send a new ship. Nothing is taken yet: at Commission the
	 * captain chooses her, and whether the Cargo Hold pays for her ({@link #forfeitHold}) or the career's reputation does.
	 */
	public synchronized void plead() { Plea.plead(this); }
	/**
	 * The plea's ship is paid for with the Cargo Hold: everything in it (scrap, supplies, items, stored systems) goes to
	 * The Federation Home Planet, and the hold starts again with the refund (the difference, if the captain asked for it).
	 * The crew in it stay (they aren't counted, and aren't given up); the Junkyard is untouched. Written with this
	 * transaction (6.11: the new ship's, so the hold is given only if she comes; put back by hand before), logged once it stands.
	 */
	public synchronized void forfeitHold(final int saleValue, final int refund, Transaction tx) throws IOException { Plea.forfeitHold(this, saleValue, refund, tx); }
	/** Withdraws a waiting plea (nothing was taken: her order is simply cancelled). */
	public synchronized void withdrawPlea() throws IOException { Plea.withdrawPlea(this); }
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
		ActionProtection.ensure(this);
		ActionProtection.settle(this); // an action a station stopped partway through, finished before anything else touches the fleet
		shipyardDir().mkdirs();
		junkyardDir().mkdirs();
		memorialDir().mkdirs();
		found.clear();
		readFolders();
		reconcile();
		Strays.sortShips(this); // another career's ships, and any the fleet can't prove its own, wait for the player's word (Plan N)
		saveManifest();
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
			s.hash = r.hash; s.marks = r.marks; s.stranger = r.stranger; s.fresh = r.fresh; s.origin = r.origin;
			ships.add(s);
		}
		File hold = new File(cargoHoldDir(), HOLD_FILE);
		if (hold.isFile()) { // the hold needs no record (5.84): its file says what it is
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
		// her fleet and how she came into it (6.10): the fleet writing her record is hers
		r.origin = s.origin != null && !s.origin.isEmpty() ? s.origin : was != null ? was.origin : "";
		r.career = was != null && !was.career.isEmpty() ? was.career : !r.origin.isEmpty() ? slot : ""; // never overwritten here: another career's record waits for its offer
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
				if (s.state != Ship.State.DOCKED || fileOf(s).isFile() || !watch.isHers(s, cont)) continue;
				s.state = Ship.State.BOARDED;
				try { s.hash = SafeFiles.hash(cont); } catch (IOException e) { s.hash = ""; }
				notes.add(s.name + " was being boarded when the station stopped: continue.sav is " + Words.her() + " save, so " + Words.she() + " is boarded");
				break;
			}
		}
		// ships whose files vanished
		for (Ship s : new ArrayList<Ship>(ships)) {
			if (s.state == Ship.State.BOARDED) continue;
			if (!fileOf(s).isFile()) {
				if (s.state == Ship.State.STORAGE) continue; // recreated on demand
				if (Strays.saveGone(this, s, notes)) continue; // the player's word on putting it back (6.10)
				notes.add(s.name + " (" + s.state.key + "): " + Words.her() + " file is gone; " + (folderOf(s).isDirectory() ? Words.her() + " folder is kept in the memorial" : "nothing left of " + Words.herObj()));
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
			notes.add(b.name + " was being docked when the station stopped: " + Words.her() + " save is in " + Words.her() + " folder, so " + Words.she() + " is docked");
			b = null;
		}
		// never while FTL is running: it rewrites continue.sav by deleting it first, so a missing file there proves nothing
		if (b != null && !cont.isFile() && !homeplanet.core.GameGuard.isFtlRunning()) {
			notes.add(b.name + " was boarded, and continue.sav is gone: lost in action (FTL ends a run by deleting the save). "
					+ (folderOf(b).isDirectory() ? Words.Her() + " last versions are in " + place(b) : ""));
			recordFate(b, Fate.LOST);
			Reputation.lost(this, b);
			ships.remove(b);
			b = null;
		}
		if (b == null && cont.isFile()) watch.setAsideMarked(cont, notes); // her mark says whose she is (6.08)
		if (b == null && cont.isFile()) watch.setAsideCopy(cont, notes); // unmarked, but a copy of one she already has (Steam Cloud)
		if (b == null && cont.isFile()) {
			Ship n = new Ship(newId(), "Unknown ship", Ship.State.BOARDED, true);
			n.origin = Strays.originNow("adopted");
			n.name = ""; // filled in by takeStock() once the game data is loaded
			n.stranger = true; // not commissioned here (FTL's New Game, most likely)
			ships.add(n);
			notes.add("continue.sav is a ship the station didn't know (a new game started in FTL, most likely): " + Words.she() + " is now boarded");
			b = n;
		}
		// strays: a save in the shipyard or the Junkyard that no ship holds waits for the player's word, never taken in unasked (6.10: findLoose)
		// a folder in the shipyard with no ship in it (her save gone, and not boarded): remembered, not listed
		for (Ship s : new ArrayList<Ship>(ships)) {
			if (s.state == Ship.State.BOARDED || s.state == Ship.State.STORAGE || fileOf(s).isFile()) continue;
			if (Strays.saveGone(this, s, notes)) continue;
			notes.add(s.name + " (" + s.state.key + "): " + Words.her() + " save is gone; " + Words.her() + " folder is kept in the memorial");
			ships.remove(s);
			try { toMemorial(s); } catch (IOException e) { log.warn("Could not move {}'s folder to the memorial: {}", s, e.toString()); }
		}
		if (!notes.isEmpty()) StationLog.entry("VAULT", "taking stock", notes, Event.of("VAULT").put("what", "taking_stock").details(notes));
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
		if (watch.checkBoarded()) changed = true;
		if (changed) saveManifest();
		List<String> notes = new ArrayList<String>();
		Strays.findLoose(this, notes); // a save lying where no ship holds it waits for the player's word (6.10)
		Strays.markShips(this); // every save carries her mark (6.10)
		if (!notes.isEmpty()) StationLog.entry("VAULT", "taking stock", notes, Event.of("VAULT").put("what", "taking_stock").details(notes));
	}

	// ---- the final victory (rescue or reward) ----

	/**
	 * Watching continue.sav: if it's the boarded ship with the flagship on her way to the last battle, keeps a copy (the
	 * latest replaces the one before), with the profile's victory count now and her victorious Top Scores entries now.
	 * True if kept. A save FTL is still writing doesn't read, and is left for the next look.
	 */
	public synchronized boolean watchContinue(int victoriesNow, java.util.function.BiFunction<String, String, Integer> victoriousScores) { return FinalBattles.watchContinue(this, victoriesNow, victoriousScores); }
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
			File copy = new File(d, FinalBattles.FINAL);
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
	public synchronized FinalBattle finalBattle(String id) { return FinalBattles.finalBattle(this, id); }
	/** Notes that a rescued ship's offer is out (so it isn't made twice). */
	public synchronized void finalOffered(FinalBattle f) throws IOException { FinalBattles.finalOffered(this, f); }
	/**
	 * Closes a final battle: her copy stays in her history as a kept version, named for what came of it
	 * ("victory-…" or "final-battle-…").
	 */
	public synchronized void closeFinal(FinalBattle f, boolean victory) { FinalBattles.closeFinal(this, f, victory); }
	/**
	 * Brings a victorious ship home from her copy: docked, her journey reset as a New Journey would (the flagship and
	 * its fleet gone from her charts), her crew, cargo and damage as they were. Her next journey is at this difficulty
	 * (null: the one she won on).
	 */
	public synchronized Ship bringHome(FinalBattle f, net.blerf.ftl.constants.Difficulty difficulty) throws IOException { return FinalBattles.bringHome(this, f, difficulty); }
	/** A rescued ship goes to the Federation Museum instead: her fate recorded, her copy kept as her last version. */
	public synchronized void toMuseum(FinalBattle f) { FinalBattles.toMuseum(this, f); }

	// ---- Steam Cloud's copies (ContinueWatch) ----

	/** Where a copy marked as another career's ship is set aside (6.08). */
	public static final String SET_ASIDE = "set-aside";

	/** What the player is told of a copy continue.sav turned out to be (set aside since this was last asked), or null. */
	public synchronized String takeCloudCopy() { return watch.takeCloudCopy(); }

	// ---- sectors travelled (the Immersive stipend) ----

	/** Sectors this fleet's boarded ships have been seen to advance, in all (FTL's progress, not the station's own changes). */
	public synchronized int sectorsSeen() { return Clock.sectorsSeen(this); }

	// ---- beacons travelled (transmissions that answer a reply some beacons later) ----

	/** Beacons this fleet's boarded ships have been seen to explore, in all (FTL's progress, as with the sectors). */
	public synchronized int beaconsSeen() { return Clock.beaconsSeen(this); }
	/** One beacon of the fleet's time passes away from FTL (a finished expedition): everything timed counts it. */
	public synchronized void countBeacon() { Clock.countBeacon(this); }
	/** As above, with why (the master log's day line: rest, a job, business in the Cargo Bay). */
	public synchronized void countBeacon(String why) { Clock.countBeacon(this, why); }

	// ---- the fleet's clock: every beacon and sector the boarded ship flies, counted once ----


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

	/** One of the fleet's ships came out of a battle with one point of hull (her name): a one-time event (ContinueWatch.noteHull). */
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

	/** The name of a boarded ship FTL overwrote since this was last asked (her last seen version is in her records), or null. */
	public synchronized String takeOverwritten() { return watch.takeOverwritten(); }

	/**
	 * A ship the station didn't commission, boarded in Immersive Mode (heromedel, 5.54): the station doesn't watch FTL for
	 * her (no voyage log, reputation, clock, stipend time, parcels, crew, final battle) until the player decides what
	 * becomes of her. In Sandbox Mode any ship is the fleet's.
	 */
	public boolean ignoring(Ship b) { return watch.ignoring(b); }
	// ---- a career ship FTL's New Game wrote over by accident (heromedel, 5.55) ----

	/**
	 * Can this version of her be put back? With no hostile ship alongside, yes. In a battle, only outside sector 8 and
	 * with her hull above 5: she goes back into that same battle.
	 */
	public static boolean restorable(SavedGameState gs) { return ContinueWatch.restorable(gs); }
	/** Career ships FTL's New Game wrote over that are to be offered back, newest first. */
	public synchronized List<Departed> offeredBack() { return watch.offeredBack(); }
	/** The player said no: she stays lost, and isn't asked about again. */
	public synchronized void declineBack(Departed d) { watch.declineBack(d); }
	/**
	 * Brings her back as the boarded ship from her last kept version (into the same battle, if she was in one): an
	 * uncommissioned ship in continue.sav goes to the Sandbox fleet's Space Dock first; a ship of the fleet's own there
	 * is left boarded, and she comes back docked. Her fate is cleared and what her loss cost in reputation given back.
	 * FTL must be closed. Then {@link CrewRegister#shipBack} for her crew (outside the fleet's lock).
	 */
	public synchronized Ship restoreBack(Departed d) throws IOException { return watch.restoreBack(d); }

	/** FTL has written continue.sav (the save watcher): the boarded ship's voyage log takes note, if it's her. */
	public synchronized void observeBoarded() { watch.observeBoarded(); }
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


	/** Copies her current save into her history folder (before it's changed), keeping the last KEEP. Nothing if her newest kept version is the same. */
	public synchronized void snapshot(Ship s) throws IOException { ShipStore.snapshot(this, s); }
	/** Her earlier versions, oldest first: the ordinary ones (the copies kept for a reason of their own are in {@link #kept}). */
	public List<File> history(Ship s) { return ShipStore.history(this, s); }
	/** Every save kept of her, oldest first: her versions and the copies kept for a reason of their own (the Records' Restore list). */
	public List<File> kept(Ship s) { return ShipStore.kept(this, s); }

	// ---- writing ----

	/** Writes a ship's save: her history gets the old version first, then the file is replaced in one move. */
	public synchronized void write(Ship s, SavedGameState state) throws IOException {
		Clock.countBefore(this, s);
		snapshot(s);
		writeQuietly(s, state);
		watch.marked(s, state);
		ShipStore.keepBoarded(this, s);
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
					+ (gone ? "ended " + Words.her() + " run" : "saved " + Words.herObj()) + ", most likely). Nothing was saved.");
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

		// 6.11 (CONCERNS 8): what used to be written in a second step, and put back by hand if that failed, goes in the same note
		private final List<File> deletes = new ArrayList<File>();
		private final Map<Ship, SavedGameState> arriving = new LinkedHashMap<Ship, SavedGameState>();
		private Ship leaving;
		private Fate leavingFate;
		private final List<Runnable> after = new ArrayList<Runnable>();
		/** A file that goes with the change (the stored systems, when the Cargo Hold is given up), if it's there. */
		public Transaction delete(File f) { deletes.add(f); return this; }
		/** Scrap from the Cargo Hold, in this note: the hold as this transaction already has it, else a fresh copy. Throws if it hasn't enough. */
		public Transaction pay(int scrap) throws IOException {
			if (scrap <= 0) return this;
			Ship st = storage();
			SavedGameState hold = pending.get(st);
			if (hold == null) { Copy c = readCopy(st); hold = c.save; put(st, hold, c.hash); }
			int have = hold.getPlayerShip().getScrapAmt();
			if (have < scrap) throw new IOException("The Cargo Hold has " + have + " scrap; " + scrap + " is needed");
			hold.getPlayerShip().setScrapAmt(have - scrap);
			return this;
		}
		/** A new ship into the fleet (docked, or in the Junkyard) with this note: her folder, save and record. In the fleet once it stands. */
		public Ship adopt(SavedGameState state, Ship.State where, String how) {
			Ship s = new Ship(newId(), state.getPlayerShipName(), where, state.isDLCEnabled());
			s.origin = Strays.originNow(how);
			arriving.put(s, state);
			return s;
		}
		/** She leaves the fleet with this note (her last version kept, her fate written, her folder to the memorial). */
		public Transaction leave(Ship s, Fate fate) { leaving = s; leavingFate = fate; return this; }
		/** Done once the note stands (its log entries). */
		public Transaction then(Runnable r) { after.add(r); return this; }

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
				ActionProtection.Note note = ActionProtection.begin(Vault.this, "SAVE");
				for (Map.Entry<File, byte[]> e : bytes.entrySet()) note.replace(e.getKey(), e.getValue());
				for (Map.Entry<Ship, SavedGameState> e : arriving.entrySet()) { // her folder is made with her files (the note's own new bytes beside them)
					Ship s = e.getKey();
					byte[] save = bytesOf(s, e.getValue());
					s.hash = SafeFiles.hash(save);
					note.replace(fileOf(s), save);
					note.replace(ShipStore.xml(folderOf(s)), ShipStore.bytes(recordOf(s)));
				}
				for (File f : deletes) if (f.exists()) note.delete(f);
				File leftTo = leaving == null ? null : leaveSteps(note, leaving, leavingFate, null);
				for (Ship s : pending.keySet()) { Clock.countBefore(Vault.this, s); snapshot(s); }
				note.commit();
				for (Map.Entry<Ship, SavedGameState> e : pending.entrySet()) { e.getKey().written(e.getValue(), SafeFiles.hash(fileOf(e.getKey()))); watch.marked(e.getKey(), e.getValue()); ShipStore.keepBoarded(Vault.this, e.getKey()); }
				for (Map.Entry<Ship, SavedGameState> e : arriving.entrySet()) { Ship s = e.getKey(); folders.put(s.id, folderOf(s)); s.written(e.getValue(), s.hash); ships.add(s); }
				if (leaving != null) { left(leaving, leftTo); ships.remove(leaving); }
				saveManifest();
			}
			for (Runnable r : after) r.run();
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
		if (marked != null) {
			java.util.Properties sec = record.section(ShipMark.SECTION);
			sec.setProperty("boards", Integer.toString(boards)); sec.setProperty("day", Integer.toString(ShipMark.today()));
			sec.setProperty("marked", "true"); sec.setProperty("save", record.hash);
			if (marked.gs.getPlayerShipBlueprintId() != null) sec.setProperty("class", marked.gs.getPlayerShipBlueprintId());
		}
		try { // as she was boarded (6.66): repairs made aboard without a jump end the day when she docks (heromedel)
			SavedGameState was = marked != null ? marked.gs : homeplanet.core.HomePlanet.savedGameParser.readSavedGame(from);
			java.util.Properties ab = record.section(ShipStore.ABOARD);
			ab.setProperty("beacons", Integer.toString(was.getTotalBeaconsExplored()));
			ab.setProperty("damage", Integer.toString(repairable(was.getPlayerShip())));
		} catch (Exception e) { log.debug("Could not note {}'s damage as boarded: {}", s.name, e.toString()); }
		ActionProtection.Note note = ActionProtection.begin(this, "BOARD");
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
		try { SavedGameState gs = marked != null ? marked.gs : homeplanet.core.HomePlanet.savedGameParser.readSavedGame(to); Clock.setClock(this, s, gs.getSectorNumber(), gs.getTotalBeaconsExplored()); }
		catch (Exception e) { log.warn("Could not read her progress as boarded: {}", e.toString()); } // she's counted from her next look instead
		saveManifest();
		StationLog.entry("BOARD", s.name + "  " + place(s) + " -> continue.sav", null, shipEvent("BOARD", s).put("from", place(s)).put("to", "continue.sav")
				.put("boards", marked != null ? boards : null).put("mark", marked != null ? ShipMark.COUNT + slot + "." + s.id : null));
	}
	/** Docks the boarded ship: continue.sav comes back into the vault. */
	public synchronized void dock() throws IOException {
		Ship b = boarded();
		if (b == null) return;
		log.debug("Dock {} ({})", b.name, b.id);
		File from = continueFile();
		if (!from.isFile()) throw new IOException("continue.sav is missing: " + b.name + " may have been lost in FTL. Refresh to take stock.");
		Clock.countBefore(this, b); // her voyage since the last look counts before she leaves continue.sav
		File to = ShipStore.sav(settleFolder(b)); // where a docked ship's save lives (fileOf, once she's docked)
		byte[] save = SafeFiles.read(from);
		java.util.Properties markNotes = ShipStore.notes(to.getParentFile(), ShipMark.SECTION);
		boolean marking = !"true".equals(markNotes.getProperty("marked")); // boarded before the mark (6.10): she gets it as she comes home
		if (marking) {
			try { save = ShipMark.mark(from, slot, b.id, Store.num(markNotes, "boards", 0), Store.num(markNotes, "day", 0)).bytes; }
			catch (Exception e) { marking = false; log.warn("Could not mark {}'s save as she docked (marked at the next opening): {}", b.name, e.toString()); }
		}
		ActionProtection.Note note = ActionProtection.begin(this, "DOCK"); // one note (5.71): her save into her folder, her record saying docked (5.95), continue.sav gone; a failure leaves her boarded, as she was
		note.replace(to, save);
		byte[] rec = recordAs(b, Ship.State.DOCKED, SafeFiles.hash(save), null);
		if (marking) {
			ShipStore.Record r = ShipStore.parse(rec, b.id);
			java.util.Properties sec = r.section(ShipMark.SECTION);
			sec.setProperty("boards", Integer.toString(Store.num(markNotes, "boards", 0))); sec.setProperty("marked", "true"); sec.setProperty("save", SafeFiles.hash(save));
			rec = ShipStore.bytes(r);
		}
		note.replace(ShipStore.xml(to.getParentFile()), rec);
		note.delete(from);
		try { note.commit(); }
		catch (IOException e) { throw new IOException(e.getMessage() + " (is FTL running?)", e); }
		b.state = Ship.State.DOCKED;
		b.hash = SafeFiles.hash(to);
		b.invalidate();
		saveManifest();
		StationLog.entry("DOCK", b.name + "  continue.sav -> " + place(b), null, shipEvent("DOCK", b).put("from", "continue.sav").put("to", place(b)));
		repairsTookTheDay(b, to);
	}
	/** Broken system bars and hull breaches, weighed as her price weighs them: what her crew can mend aboard. */
	static int repairable(net.blerf.ftl.parser.SavedGameParser.ShipState s) {
		return homeplanet.parser.Pricing.brokenValue(s) + homeplanet.parser.Pricing.BREACH_REPAIR * s.getBreachMap().size();
	}
	/**
	 * heromedel, 6.66: a ship taken out of the dock, her systems or breaches mended, and brought back without a jump, took
	 * the day (a jump counted it already). Read against what was noted as she was boarded.
	 */
	private void repairsTookTheDay(Ship b, File save) {
		try {
			java.util.Properties ab = ShipStore.notes(save.getParentFile(), ShipStore.ABOARD);
			if (ab.getProperty("damage") == null) return;
			SavedGameState gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(save);
			if (gs.getTotalBeaconsExplored() == Store.num(ab, "beacons", -1) && repairable(gs.getPlayerShip()) < Store.num(ab, "damage", 0))
				countBeacon("a day of repairs aboard the " + b.name);
			setNotes(save.getParentFile(), ShipStore.ABOARD, null);
		} catch (Exception e) { log.debug("Could not tell whether {} was repaired aboard: {}", b.name, e.toString()); }
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
		ActionProtection.Note n = ActionProtection.begin(this, "DISBAND");
		n.replace(ShipStore.sav(dir), SafeFiles.read(from));
		n.delete(from);
		n.rename(dir, to);
		n.commit();
		b.state = Ship.State.JUNKED;
		folders.put(b.id, to);
		b.invalidate();
		saveManifest();
		StationLog.entry("DISBAND", b.name + "  continue.sav -> " + place(b), null, shipEvent("DISBAND", b).put("from", "continue.sav").put("to", place(b)));
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
		StationLog.entry("SALVAGE", s.name + "  " + from + " -> " + place(s), null, shipEvent("SALVAGE", s).put("from", from).put("to", place(s)));
	}
	/** Removes a ship for good (scrapped or destroyed): her last save goes into her history, and she leaves the manifest. Logged under {@code why} unless null. */
	public synchronized void remove(Ship s, String why) throws IOException {
		remove(s, why, "DESTROY".equals(why) ? Fate.DESTROYED : Fate.SCRAPPED);
	}
	/** An event about a ship: her name and id together (as the log names her), apart, and where she is. */
	public static Event shipEvent(String kind, Ship s) {
		return where(Event.of(kind).put("ship", s.name + "." + s.id).put("ship_name", s.name).put("ship_id", s.id).put("ship_state", s.state == null ? null : s.state.key).put("stranger", s.stranger ? "true" : null), s);
	}
	/**
	 * Where she was when it happened (heromedel, 6.18): her sector (1 to 8, as the player counts) and FTL's difficulty she
	 * flies on, from her save as it is now; left out where she has none (the Cargo Hold, a ship that has just left).
	 */
	public static Event where(Event e, Ship s) {
		if (s == null || s.state == Ship.State.STORAGE) return e;
		if (net.blerf.ftl.parser.DataManager.get() == null) return e; // the fleet opens before FTL's game data: a read now would fail, and her save be taken for unreadable until it changed
		SavedGameState gs;
		try { gs = s.save(); } catch (RuntimeException x) { gs = null; }
		if (gs == null) s.invalidate(); // a fleet opened at a career switch: her design may not be registered yet, and the failure isn't to be kept (6.22)
		return where(e, gs);
	}
	/** As above, from her save in hand. */
	public static Event where(Event e, SavedGameState gs) {
		if (gs == null) return e;
		return e.put("ship_sector", gs.getSectorNumber() + 1).put("ship_difficulty", gs.getDifficulty() == null ? null : gs.getDifficulty().toString().toLowerCase());
	}
	/** The same, recording this fate (a ship traded in or auctioned off is SOLD). */
	public synchronized void remove(Ship s, String why, Fate fate) throws IOException {
		String from = place(s);
		leave(s, fate, null);
		ships.remove(s);
		saveManifest();
		if (why != null) StationLog.entry(why, s.name + "  " + from + " -> " + place(s), null,
				shipEvent(why, s).put("fate", fate == null ? null : fate.name().toLowerCase()).put("from", from).put("to", place(s)));
	}
	/**
	 * She leaves the fleet, as one protection note (5.88; by hand before): her save kept as her last version and gone,
	 * her fate written, her folder to the memorial. Her versions are pruned after. The caller takes her off the list.
	 */
	void leave(Ship s, Fate fate, String detail) throws IOException {
		ActionProtection.Note n = ActionProtection.begin(this, "LEAVE");
		File to = leaveSteps(n, s, fate, detail);
		n.commit();
		left(s, to);
	}
	/** Her leaving's steps, added to a note (6.11: Scrap and Sell put the Cargo Hold's in the same one). Returns her memorial folder, or null if she's there. */
	File leaveSteps(ActionProtection.Note n, Ship s, Fate fate, String detail) throws IOException {
		File dir = settleFolder(s), f = fileOf(s), to = new File(memorialDir(), dir.getName());
		boolean move = !dir.getParentFile().getAbsoluteFile().equals(memorialDir().getAbsoluteFile());
		if (move && to.exists()) throw new IOException(to + " is already there");
		if (!memorialDir().isDirectory() && !memorialDir().mkdirs()) throw new IOException("Could not create " + memorialDir());
		if (f.isFile()) {
			List<File> kept = history(s);
			if (kept.isEmpty() || !SafeFiles.hash(kept.get(kept.size() - 1)).equals(SafeFiles.hash(f))) n.replace(ShipStore.versionFile(dir, null), SafeFiles.read(f));
			n.delete(f);
		}
		n.replace(ShipStore.xml(dir), recordWithNotes(dir, ShipStore.FATE, ShipStore.fateNotes(fate.name(), s.name, detail)));
		if (move) n.rename(dir, to);
		return move ? to : null;
	}
	/** Her leaving done: her folder where it went, her versions pruned. */
	void left(Ship s, File to) {
		if (to != null) folders.put(s.id, to);
		ShipStore.prune(folders.get(s.id), KEEP);
	}
	/**
	 * A departed ship's folder back to the shipyard with this save, her fate gone, as one protection note (5.88): the
	 * save written in her folder where it is, then the folder moved (6.87: or left, when it's in the shipyard already,
	 * or was just made there). Returns her folder.
	 */
	File comeHome(Ship s, File folder, byte[] save) throws IOException {
		boolean move = !folder.getParentFile().getAbsoluteFile().equals(shipyardDir().getAbsoluteFile());
		File to = move ? new File(shipyardDir(), folder.getName()) : folder;
		if (move && to.exists()) throw new IOException(to + " is already there");
		ActionProtection.Note n = ActionProtection.begin(this, "COME_HOME");
		n.replace(ShipStore.sav(folder), save);
		byte[] record = recordWithout(folder, ShipStore.FATE, ShipStore.OVERWRITTEN); // her fate gone, and any offer to bring her back: she's back
		if (record != null) n.replace(ShipStore.xml(folder), record);
		if (move) n.rename(folder, to);
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
	void recordFate(Ship s, Fate fate) {
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
				File last = ShipStore.newestKept(d);
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
		if (new File(folder, FinalBattles.FINAL).isFile()) closeFinal(new FinalBattle(d.id, d.name, new File(folder, FinalBattles.FINAL), -1, -1, ""), false); // settled by coming back
		saveManifest();
		s.save(); // her name and DLC flag, as the save has them
		Strays.remarkQuietly(this, s); // her last version, marked again (6.10)
		StationLog.entry("RECOVER", s.name + " (" + d.fate.name().toLowerCase() + ")  " + from + " -> " + place(s), null,
				shipEvent("RECOVER", s).put("fate", d.fate.name().toLowerCase()).put("from", from).put("to", place(s)));
		return s;
	}
	/** Puts one of her earlier versions back as her current save; the one it replaces goes into her history first. */
	public synchronized void restore(Ship s, File version) throws IOException {
		if (s.state == Ship.State.STORAGE) throw new IOException("The Cargo Hold has no earlier versions to go back to");
		byte[] bytes = SafeFiles.read(version); // before the version she has now is kept, which may prune it
		Clock.countBefore(this, s);
		File f = fileOf(s), dir = settleFolder(s);
		// one note (6.11): the save she has now kept as her newest version, the older one put back already marked (a docked or
		// Junkyard ship's: 6.10), her record with it. Marked after it was put back (6.10), the older save was kept again as her
		// newest version, above the one it replaced, so undoing a Restore took the second one down the list
		ShipStore.Record r = null;
		if (s.state == Ship.State.DOCKED || s.state == Ship.State.JUNKED) {
			try { ShipMark.Marked mk = Strays.markFrom(this, s, dir, version); bytes = mk.bytes; r = Strays.markedRecord(this, s, dir, mk); }
			catch (IOException e) { log.warn("Could not mark {}'s older save (marked at the next opening): {}", s.name, e.toString()); }
		}
		ActionProtection.Note n = ActionProtection.begin(this, "RESTORE_VERSION");
		if (f.isFile()) { // as snapshot() does: kept unless her newest version is the same, so undoing the Restore is restoring the newest
			byte[] now = SafeFiles.read(f);
			List<File> h = history(s);
			if (h.isEmpty() || !SafeFiles.hash(h.get(h.size() - 1)).equals(SafeFiles.hash(now))) n.replace(ShipStore.versionFile(dir, null), now);
		}
		n.replace(f, bytes);
		if (r != null) n.replace(ShipStore.xml(dir), ShipStore.bytes(r));
		n.commit();
		ShipStore.prune(dir, KEEP);
		s.invalidate();
		s.hash = SafeFiles.hash(f);
		watch.marked(s, s.save());
		ShipStore.keepBoarded(this, s);
		saveManifest();
		log.info("Restored {}: {}/versions/{} -> {}", s.name, place(s), version.getName(), s.isBoarded() ? "continue.sav" : place(s));
		StationLog.entry("RESTORE", "Restored " + homeplanet.parser.ShipNames.the(s.name) + " to an earlier version", null, // in words; the files in the debug log (heromedel, 5.53)
				shipEvent("RESTORE", s).put("why", "version").put("from", place(s) + "/" + ShipStore.VERSIONS + "/" + version.getName()).put("to", s.isBoarded() ? "continue.sav" : place(s)));
	}

	// ---- Long Range Comm.: ships that change hands ----


	/** A docked ship's package for another station (a zip of {@link #PACKAGE}). */
	public synchronized byte[] packageOf(Ship s) throws IOException { return Trades.packageOf(this, s); }
	/** A package's files by name: only the ones a package holds, each within its limit. */
	public static Map<String, byte[]> unpack(byte[] pkg) throws IOException { return Trades.unpack(pkg); }

	/** She leaves the fleet in a trade's escrow: her save goes into her history, and her fate says where she went. */
	public synchronized void sendAway(Ship s, String to) throws IOException { Trades.sendAway(this, s, to); }
	/** A trade with her in it was called off: she comes back docked, from her package (unless she's here already). */
	public synchronized Ship comeBack(String id, byte[] pkg) throws IOException { return Trades.comeBack(this, id, pkg); }
	/**
	 * A trade called off after this ship had already been received for it (a later ship's papers missing, say): she goes
	 * back as sent away, since the other station keeps her (5.61: she stayed, and was in both fleets). Her save goes into
	 * her history, and her fate says where she went. Only a ship still docked: one flown since is left, and logged.
	 * Returns her, or null if no ship came for this trade line.
	 */
	public synchronized Ship unreceive(String tradeLine, String to) throws IOException { return Trades.unreceive(this, tradeLine, to); }
	/** The trade went through: she flies for the other fleet now (her history stays here, as a record). */
	public synchronized void transferred(String id, String name, String to) throws IOException { Trades.transferred(this, id, name, to); }
	/**
	 * A ship arriving from another station, from her package: docked under a new id, her voyage log kept, a trade
	 * mark (see {@link TradeMark}) naming the sender and her original owner, and set out at The Home Planet Station as
	 * a newly commissioned ship is. A ship already received for this trade line isn't received twice.
	 */
	public synchronized Ship receive(byte[] pkg, byte[] save, SavedGameState gs, String tradeLine, String from) throws IOException { return Trades.receive(this, pkg, save, gs, tradeLine, from); }
	/** Is a copy kept of her on the way to the last battle (a final battle not yet settled)? Such a ship stays in the fleet. */
	public boolean finalBattlePending(Ship s) { return new File(folderOf(s), FinalBattles.FINAL).isFile(); }

	/** A ship just built (commissioned): written into the ships folder, docked. */
	public synchronized Ship adopt(SavedGameState state) throws IOException { return adopt(state, "commissioned"); }
	/** As above, saying how she came (6.10: her origin, "commissioned" or "brought_home"). */
	public synchronized Ship adopt(SavedGameState state, String how) throws IOException {
		Ship s = new Ship(newId(), state.getPlayerShipName(), Ship.State.DOCKED, state.isDLCEnabled());
		s.origin = Strays.originNow(how);
		log.debug("New ship docked: {} ({}), {}, AE {}", s.name, s.id, state.getPlayerShipBlueprintId(), state.isDLCEnabled());
		writeQuietly(s, state); // her file first: a failed write leaves no entry without one
		ships.add(s);
		saveManifest();
		return s;
	}
	/** A ship that arrives as a wreck (a gift of salvage): written straight into the Junkyard. */
	public synchronized Ship adoptJunked(SavedGameState state) throws IOException { return adoptJunked(state, "derelict"); }
	/** As above, saying how she came (6.10: "derelict", "brought_home"). */
	public synchronized Ship adoptJunked(SavedGameState state, String how) throws IOException {
		Ship s = new Ship(newId(), state.getPlayerShipName(), Ship.State.JUNKED, state.isDLCEnabled());
		s.origin = Strays.originNow(how);
		writeQuietly(s, state); // her file first: a failed write leaves no entry without one
		ships.add(s);
		saveManifest();
		return s;
	}
	/** A save file from elsewhere (a file the player dropped in, a converter's) taken into the vault: the file is moved. */
	public synchronized Ship adoptFile(File f, Ship.State state, String cachedName) throws IOException {
		Ship s = new Ship(newId(), cachedName, state, true);
		s.origin = Strays.originNow("taken_in");
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

	// ---- who she is: her fleet, how she came into it, her mark (6.10, heromedel's Plan N) ----


	/** Something found as the fleet opened that waits on the player's word (6.10). */
	public static final class Found {
		/**
		 * LOOSE: a save lying where no ship holds it (dropped in by hand); UNPROVEN: a ship's folder with nothing to show
		 * she's this fleet's; OTHER: a record of another career's fleet; NO_SAVE: a ship whose save is gone from her folder.
		 */
		public enum Kind { LOOSE, UNPROVEN, OTHER, NO_SAVE }
		public final Kind kind;
		/** The loose save, or her folder. */
		public final File file;
		public final String name, id, career;
		/** What a no thanks is remembered by ("file:" and the save's fingerprint, or "ship:" and her id). */
		final String key;
		/** NO_SAVE: where her save would come from: "marked" (a save found carrying her mark), "version" (her newest kept), "rebuild" (her records). */
		String fix = "";
		File source;
		Found(Kind kind, File file, String name, String id, String career, String key) {
			this.kind = kind; this.file = file; this.name = name == null || name.isEmpty() ? "an unnamed ship" : name; this.id = id; this.career = career == null ? "" : career; this.key = key;
		}
		public String fix() { return fix; }
		public File source() { return source; }
	}
	final List<Found> found = new ArrayList<Found>();
	/** The hash each marked ship's save had when its mark was last checked (Strays.markShips skips the unchanged). */
	final Map<String, String> markedSeen = new java.util.HashMap<String, String>();
	/** What waits on the player's word (6.10), oldest found first. */
	public synchronized List<Found> found() { return new ArrayList<Found>(found); }




	/**
	 * The player's yes to something found (6.10). In Sandbox Mode she's taken in here; in a career she goes to Sandbox
	 * Mode's fleet (a career takes only ships it knows); a ship whose save is gone gets one back. What came of it, in words.
	 */
	public synchronized String accept(Found f) throws IOException { return Strays.accept(this, f); }
	/** The player's no (6.10): left as it is, and not asked again; a ship whose save is gone goes to the memorial. */
	public synchronized void decline(Found f) throws IOException { Strays.decline(this, f); }


	/**
	 * Destroy in Immersive Mode, the other way (heromedel, 6.10): she leaves the career as if destroyed (no reward, no
	 * refund; her fate there "transferred", to Sandbox Mode), and lives on in Sandbox Mode's Junkyard with her crew.
	 */
	public synchronized String sendToSandbox(Ship s) throws IOException { return Strays.sendToSandbox(this, s); }

	// ---- who uses a blueprint ----

	/**
	 * The ships (docked, boarded or junked) whose saves name this blueprint id. A save that can't be read at all
	 * counts as using it, since it might: a locked or damaged file must never get a blueprint retired under it.
	 */
	public synchronized List<Ship> usingBlueprint(String bpId) { return BlueprintUse.usingBlueprint(this, bpId); }
	/** Every blueprint id any ship names (null entries for unreadable files are skipped; see {@link #usingBlueprint}). */
	public synchronized java.util.Set<String> blueprintsInUse() { return BlueprintUse.blueprintsInUse(this); }
	/**
	 * As {@link #blueprintsInUse}, plus every blueprint the ships' kept earlier versions (history) name: a blueprint
	 * in here may still be needed to bring a ship back, so it isn't cleaned up.
	 */
	public synchronized java.util.Set<String> blueprintsInUseOrHistory() { return BlueprintUse.blueprintsInUseOrHistory(this); }
	/**
	 * Every blueprint the other fleet's saves name: its docked and junked ships, their kept versions and surrenders.
	 * (Its boarded ship, if any, was docked into it when the player switched fleets.)
	 */
	public java.util.Set<String> otherFleetBlueprints() { return BlueprintUse.otherFleetBlueprints(this); }
	/** The names of every other fleet's ships (docked or junked) whose saves name this blueprint. */
	public List<String> otherFleetUsing(String bpId) { return BlueprintUse.otherFleetUsing(this, bpId); }
	// ---- switching fleets (Immersive Mode) ----

	/** Under the shared folder: the zips of ended Immersive careers (OtherFleets.endCareer). */
	public static final String OLD_CAREERS = "old-immersive-careers";

	/**
	 * Switches to the other fleet: the boarded ship is docked into this one (and noted, to be boarded again on
	 * return), then the other fleet's vault is opened and its noted ship boarded. FTL must be closed (the caller
	 * checks). Returns the vault now in use. On a failure before the other fleet opens, nothing has changed but a dock.
	 */
	public static Vault switchFleet(boolean toImmersive) throws IOException { return OtherFleets.switchFleet(toImmersive); }
	/** As {@link #switchFleet(boolean)}, to this mode's fleet. */
	public static Vault switchFleet(String toSlot) throws IOException { return OtherFleets.switchFleet(toSlot); }

	/**
	 * Ends the Immersive career (from the normal fleet, Immersive Mode already left): its whole folder (ships, storage,
	 * records, its own FTL profile) is zipped into old-immersive-careers/, checked, and only then deleted. Returns the zip.
	 */
	public static File endImmersiveCareer() throws IOException { return OtherFleets.endImmersiveCareer(); }
	/** Ends this Immersive career, as {@link #endImmersiveCareer()}; its fleet mustn't be the one in use. */
	public static File endCareer(String slot) throws IOException { return OtherFleets.endCareer(slot); }

	public synchronized void sendToOtherFleet(Ship s, boolean junkyard) throws IOException { OtherFleets.sendToOtherFleet(this, s, junkyard); }
	/**
	 * The player takes this boarded ship to the other fleet and switches to it (an uncommissioned ship in Immersive
	 * Mode, flown in the normal fleet instead): she leaves this fleet's records, continue.sav stays, and the other
	 * fleet opens with her boarded. Its own parked ship stays docked. Returns the vault now in use.
	 */
	public static Vault handOverBoarded(Ship s) throws IOException { return OtherFleets.handOverBoarded(s); }

	/** True if any ship's save couldn't even be scanned (so "unused" can't be trusted). */
	public synchronized boolean anyUnscannable() { return OtherFleets.anyUnscannable(this); }
}
