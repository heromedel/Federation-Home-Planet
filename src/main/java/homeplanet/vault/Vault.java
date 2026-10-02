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

import javax.xml.parsers.DocumentBuilderFactory;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.HistoryLog;
import homeplanet.core.SafeFiles;
import homeplanet.parser.SaveHelper;
import homeplanet.parser.XmlText;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The station's vault: a folder inside FTL's saves folder that holds every ship the player owns, apart from the
 * one FTL is flying (continue.sav, in FTL's folder as always).
 *
 * <pre>
 *   FederationHomePlanet/
 *     manifest.xml          every ship: id, name, state, fingerprint
 *     ships/&lt;id&gt;.sav        docked ships
 *     junkyard/&lt;id&gt;.sav     disbanded ships
 *     history/&lt;id&gt;/          earlier versions of each ship (the last KEEP), written before every change
 *     storage.sav            the storage hold; storage-systems.txt its stored-systems list
 *     designs.xml, remodels.xml, art/, history.log, removed-blueprints.log
 * </pre>
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
	public static final String MANIFEST = "manifest.xml";
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
		for (String dir : new String[] {"ships", "junkyard"}) {
			File[] fs = new File(fleetRoot, dir).listFiles();
			if (fs != null) for (File f : fs) if (f.isFile() && f.getName().endsWith(".sav")) n++;
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
	public File shipsDir() { return new File(root, "ships"); }
	public File junkyardDir() { return new File(root, "junkyard"); }
	public File historyDir() { return new File(root, "history"); }
	public File artDir() { return new File(shared, "art"); }
	public File designsFile() { return new File(shared, "designs.xml"); }
	public File remodelsFile() { return new File(shared, "remodels.xml"); }
	/** A copy of every blueprint on file, one per file (see homeplanet.parser.BlueprintBackup). */
	public File blueprintsDir() { return new File(shared, "blueprints"); }
	public File removedBlueprintsLog() { return new File(shared, "removed-blueprints.log"); }
	public File historyLog() { return new File(root, "history.log"); }
	public File manifestFile() { return new File(root, MANIFEST); }
	/** The stored-systems list that goes with the storage hold. */
	public File systemsFile() { return new File(root, "storage-systems.txt"); }
	/** The storage hold's id (and file stem). Before 4B there were two holds; the old AE one's stem is kept for its file name. */
	static final String STORAGE_ID = "storage";
	/** The Cargo Hold's file in a fleet's folder (written directly when a shipment goes to a fleet not in use). */
	public static final String STORAGE_FILE = STORAGE_ID + ".sav";

	/** Where a ship's save is, given her state. */
	public File fileOf(Ship s) {
		switch (s.state) {
			case BOARDED: return continueFile();
			case JUNKED: return new File(junkyardDir(), s.id + ".sav");
			case STORAGE: return new File(root, s.id + ".sav");
			default: return new File(shipsDir(), s.id + ".sav");
		}
	}
	/** A ship's history folder. */
	public File historyOf(Ship s) { return new File(historyDir(), s.id); }

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

	// ---- the free command (HR2): once when the fleet starts, and again with each Report for Reassignment ----

	private File freeCommandFile() { return new File(root, "free-command.txt"); }
	/**
	 * Is a free command granted and not yet taken? An empty shipyard alone never grants one: the captain commissions a
	 * ship with scrap, or reports for reassignment. (A fleet from before this was recorded: granted if its shipyard is
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
	/** Grants the free command (a new fleet or career, a report for reassignment); its ship is Settings'. */
	public synchronized void grantFreeCommand(String why) { setFreeCommand(true, why, null); }
	/** Grants the free command with the ship it brings ({@link homeplanet.parser.FreeCommand}: an Immersive career or report). */
	public synchronized void grantFreeCommand(String why, String ship) { setFreeCommand(true, why, ship); }
	/** The ship the open free command earned (Immersive Mode), or null: Settings' free ship. */
	public synchronized String freeCommandShip() { String[] l = freeCommandLines(); return l.length > 2 && l[0].startsWith("open") && l[2].startsWith("ship ") ? l[2].substring(5).trim() : null; }
	/** Was the open free command granted by a Report for Reassignment (rather than a new fleet or career)? */
	public synchronized boolean freeCommandReassigned() { String[] l = freeCommandLines(); return l.length > 1 && l[0].startsWith("open") && l[1].startsWith("reported for reassignment"); }
	private String[] freeCommandLines() {
		try { return new String(SafeFiles.read(freeCommandFile()), java.nio.charset.StandardCharsets.UTF_8).trim().split("\\r?\\n"); }
		catch (IOException e) { return new String[0]; }
	}
	/** The free command is taken (her commission), or taken back (an undone report). */
	public synchronized void useFreeCommand(String why) { setFreeCommand(false, why, null); }
	private void setFreeCommand(boolean open, String why) { setFreeCommand(open, why, null); }
	private void setFreeCommand(boolean open, String why, String ship) {
		try { SafeFiles.writeText(freeCommandFile(), (open ? "open" : "used") + "\n" + why + "\n" + (ship == null ? "" : "ship " + ship + "\n"), false); }
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

	// ---- Report for Reassignment ----

	/** Where each Report for Reassignment keeps what was surrendered (one folder each), so it can be undone. */
	public File surrenderedDir() { return new File(root, "surrendered"); }
	private static final String SURRENDER_SHIPS = "ships.txt", SURRENDER_HOLD = "storage.sav", SURRENDER_SYSTEMS = "storage-systems.txt", SURRENDER_AFTER = "after.txt";

	/**
	 * Report for Reassignment: the storage hold (scrap, supplies, items, crew, stored systems) and every hull in the
	 * Junkyard are surrendered to The Federation Home Planet. The hold starts again empty. All of it is kept in a new
	 * folder under surrendered/, for {@link #undoSurrender}. Returns that folder.
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
			for (Ship s : junk) { SafeFiles.move(fileOf(s), new File(dir, s.id + ".sav")); moved.add(s); }
		} catch (IOException e) {
			for (Ship s : moved) {
				try { SafeFiles.move(new File(dir, s.id + ".sav"), fileOf(s)); } catch (IOException again) { log.error("Could not put " + s + " back in the Junkyard", again); }
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
				try { SafeFiles.move(new File(dir, s.id + ".sav"), fileOf(s)); } catch (IOException again) { log.error("Could not put " + s + " back in the Junkyard; her save is in " + dir, again); return dirFailed(dir, e); }
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
		// the ship it earns goes by what was surrendered in Immersive Mode or with Variable chosen; otherwise Settings' free ship
		String earned = homeplanet.parser.FreeCommand.byValue(this) ? homeplanet.parser.FreeCommand.earned(value) : null;
		HistoryLog.entry("REASSIGN", "the Cargo Hold and " + junk.size() + " hull(s) from the Junkyard surrendered (worth " + value + " scrap"
				+ (earned == null ? "" : ": " + homeplanet.parser.FreeCommand.words(earned)) + "); kept in surrendered/" + dir.getName(), lines);
		grantFreeCommand("reported for reassignment", earned);
		return dir;
	}
	/** A hull couldn't be put back after a failed surrender: the folder keeps it, and the error says where. */
	private static File dirFailed(File dir, IOException e) throws IOException {
		throw new IOException(e.getMessage() + ". Some hulls could not be put back in the Junkyard: their saves are in " + dir, e);
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
	 * Undoes a Report for Reassignment: the hold as it was and the hulls back in the Junkyard. Refused once a ship is
	 * docked or boarded again (the new command was taken: undoing then would keep both), or if the hold has changed
	 * since (what it holds now would be lost).
	 */
	public synchronized void undoSurrender(File dir) throws IOException {
		if (!docked().isEmpty() || boarded() != null)
			throw new IOException("A new command has been taken since the report for reassignment: it can only be undone while no ship is at the Space Dock");
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
		try {
			for (String[] e : list) {
				if (byId(e[0]) != null) continue; // already back (recovered by hand)
				Ship s = new Ship(e[0], e[1], Ship.State.JUNKED, true);
				SafeFiles.move(new File(dir, e[0] + ".sav"), fileOf(s));
				back.add(s);
			}
		} catch (IOException e) {
			for (Ship s : back) {
				try { SafeFiles.move(fileOf(s), new File(dir, s.id + ".sav")); } catch (IOException again) { log.error("Could not return " + s + " to " + dir, again); }
			}
			throw e;
		}
		snapshot(st);
		SafeFiles.write(hold, SafeFiles.read(new File(dir, SURRENDER_HOLD)));
		st.invalidate();
		st.hash = SafeFiles.hash(hold);
		File sys = new File(dir, SURRENDER_SYSTEMS);
		if (sys.isFile()) SafeFiles.write(systemsFile(), SafeFiles.read(sys));
		ships.addAll(back);
		saveManifest();
		File done = new File(dir.getParentFile(), dir.getName() + "-undone");
		if (!new File(dir, SURRENDER_AFTER).delete() || !dir.renameTo(done)) log.warn("Could not mark {} as undone", dir);
		HistoryLog.entry("UNDO REASSIGN", "the Cargo Hold and " + back.size() + " hull(s) returned from surrendered/" + dir.getName());
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
		if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Could not create " + root);
		shipsDir().mkdirs();
		junkyardDir().mkdirs();
		historyDir().mkdirs();
		if (manifestFile().isFile()) readManifest();
		reconcile();
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

	private void readManifest() {
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile());
			NodeList ns = doc.getElementsByTagName("ship");
			for (int i = 0; i < ns.getLength(); i++) {
				Element e = (Element) ns.item(i);
				String id = e.getAttribute("id");
				if (id.isEmpty()) continue;
				Ship s = new Ship(id, e.getAttribute("name"), Ship.State.of(e.getAttribute("state")), "true".equals(e.getAttribute("dlc")));
				s.hash = e.getAttribute("hash");
				s.marks = e.getAttribute("marks");
				s.stranger = "true".equals(e.getAttribute("stranger"));
				s.fresh = e.getAttribute("fresh");
				ships.add(s);
			}
		} catch (Exception e) {
			log.error("Could not read " + manifestFile() + "; rebuilding it from the files", e);
			ships.clear();
		}
	}

	/** Makes the list match the files: adopts strays, drops ships whose files are gone, settles who's boarded. */
	private void reconcile() {
		List<String> notes = new ArrayList<String>();
		// ships whose files vanished
		for (Ship s : new ArrayList<Ship>(ships)) {
			if (s.state == Ship.State.BOARDED) continue;
			if (!fileOf(s).isFile()) {
				if (s.state == Ship.State.STORAGE) continue; // recreated on demand
				notes.add(s.name + " (" + s.state.key + "): her file is gone; " + (historyOf(s).isDirectory() ? "her history folder is kept" : "nothing left of her"));
				ships.remove(s);
			}
		}
		// the boarded ship: continue.sav is hers, if it's there
		Ship b = boarded();
		File cont = continueFile();
		// never while FTL is running: it rewrites continue.sav by deleting it first, so a missing file there proves nothing
		if (b != null && !cont.isFile() && !homeplanet.core.GameGuard.isFtlRunning()) {
			notes.add(b.name + " was boarded, and continue.sav is gone: lost in action (FTL ends a run by deleting the save). "
					+ (historyOf(b).isDirectory() ? "Her last versions are in history/" + b.id : ""));
			recordFate(b, Fate.LOST);
			ships.remove(b);
			b = null;
		}
		if (b == null && cont.isFile()) {
			Ship original = cloudCopyOf(cont);
			if (original != null) {
				// Steam Cloud brought back a continue.sav the station already has (a docked ship, or one of her kept versions)
				try {
					File dir = historyOf(original);
					if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
					SafeFiles.move(cont, new File(dir, "cloud-copy-" + STAMP.format(new Date()) + ".sav"));
					prune(dir);
					cloudCopy = original.name;
					notes.add("continue.sav was a copy of " + original.name + " (" + original.state.key + "), brought back by Steam Cloud most likely: set aside in history/" + original.id);
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
		adoptStrays(shipsDir(), Ship.State.DOCKED, notes);
		adoptStrays(junkyardDir(), Ship.State.JUNKED, notes);
		if (!notes.isEmpty()) HistoryLog.entry("VAULT", "taking stock", notes);
	}
	/**
	 * Reads every ship whose file changed since it was last seen (names, DLC flags and fingerprints). Needs the game
	 * data loaded, so it runs after {@link #load()} once that is, and again on Refresh.
	 */
	public synchronized void takeStock() throws IOException {
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
	private static final String FINAL = "final-battle.sav", FINAL_NOTE = "final-battle.txt";
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
			if (!flagshipOnHerWay(gs)) return false;
			boolean first = !new File(dir, FINAL).isFile();
			SafeFiles.move(tmp, new File(dir, FINAL));
			int scoresNow = victoriousScores.apply(gs.getPlayerShipName(), gs.getPlayerShipBlueprintId());
			if (victoriesNow < 0 || scoresNow < 0) { // the profile didn't read (FTL writing it?): the counts from the copy before stand
				java.util.Properties p = new java.util.Properties();
				try { p.load(new java.io.StringReader(new String(SafeFiles.read(new File(dir, FINAL_NOTE)), java.nio.charset.StandardCharsets.UTF_8))); } catch (IOException e) { }
				if (victoriesNow < 0) victoriesNow = intOf(p, "victoriesThen");
				if (scoresNow < 0) scoresNow = intOf(p, "scoresThen");
			}
			SafeFiles.writeText(new File(dir, FINAL_NOTE), "victoriesThen=" + victoriesNow + "\nscoresThen=" + scoresNow + "\n", false);
			if (first) HistoryLog.entry("FINAL BATTLE", b.name + ": the Rebel Flagship is on her way to the last battle. A copy is kept in history/" + b.id + "/" + FINAL);
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
		File[] dirs = historyDir().listFiles();
		if (dirs == null) return out;
		java.util.Arrays.sort(dirs);
		for (File d : dirs) {
			File copy = new File(d, FINAL), note = new File(d, FINAL_NOTE);
			if (!d.isDirectory() || byId(d.getName()) != null || !copy.isFile()) continue;
			java.util.Properties p = new java.util.Properties();
			try { p.load(new java.io.StringReader(new String(SafeFiles.read(note), java.nio.charset.StandardCharsets.UTF_8))); } catch (IOException e) { }
			String name = d.getName();
			try {
				String[] lines = new String(SafeFiles.read(new File(d, FATE_FILE)), java.nio.charset.StandardCharsets.UTF_8).split("\n");
				if (lines.length > 1) name = lines[1].trim();
			} catch (IOException e) { }
			out.add(new FinalBattle(d.getName(), name, copy, intOf(p, "victoriesThen"), intOf(p, "scoresThen"), p.getProperty("outcome", "")));
		}
		return out;
	}
	private static int intOf(java.util.Properties p, String key) {
		try { return Integer.parseInt(p.getProperty(key, "-1").trim()); } catch (NumberFormatException e) { return -1; }
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
		SafeFiles.writeText(new File(f.copy.getParentFile(), FINAL_NOTE), "victoriesThen=" + f.victoriesThen + "\nscoresThen=" + f.scoresThen
				+ (outcome.isEmpty() ? "" : "\noutcome=" + outcome) + "\n", false);
	}
	/**
	 * Closes a final battle: her copy stays in her history as a kept version, named for what came of it
	 * ("victory-…" or "final-battle-…").
	 */
	public synchronized void closeFinal(FinalBattle f, boolean victory) {
		File dir = f.copy.getParentFile();
		String stamp;
		synchronized (STAMP) { stamp = STAMP.format(new Date()); }
		if (!f.copy.renameTo(new File(dir, (victory ? "victory-" : "final-battle-") + stamp + ".sav"))) log.warn("Could not rename {}", f.copy);
		new File(dir, FINAL_NOTE).delete();
	}
	/**
	 * Brings a victorious ship home from her copy: docked, her journey reset as a New Journey would (the flagship and
	 * its fleet gone from her charts), her crew, cargo and damage as they were.
	 */
	public synchronized Ship bringHome(FinalBattle f) throws IOException {
		if (byId(f.id) != null) throw new IOException(f.name + " is already in the fleet");
		SavedGameState gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(f.copy);
		SaveHelper.startJourney(gs, gs.getDifficulty());
		java.util.Iterator<net.blerf.ftl.parser.SavedGameParser.CrewState> it = gs.getPlayerShip().getCrewList().iterator();
		while (it.hasNext()) if (!SaveHelper.isOwnCrew(it.next())) it.remove(); // boarders and the like stay behind
		Ship s = new Ship(f.id, gs.getPlayerShipName(), Ship.State.DOCKED, gs.isDLCEnabled());
		writeQuietly(s, gs);
		ships.add(s);
		s.fresh = position(gs); // she's back at The Home Planet Station
		VoyageLog.baseline(this, s, gs);
		VoyageLog.note(this, s, "Rescued after the final engagement: back at The Home Planet Station, ready for a new journey");
		homeplanet.parser.Museum.setOut(this, s, false);
		try {
			saveManifest();
		} catch (IOException e) {
			ships.remove(s);
			fileOf(s).delete();
			throw e;
		}
		new File(historyOf(s), FATE_FILE).delete();
		closeFinal(f, true);
		HistoryLog.entry("VICTORY", s.name + " was rescued after the last battle: docked, ready for a new journey");
		return s;
	}
	/** A rescued ship goes to the Federation museum instead: her fate recorded, her copy kept as her last version. */
	public synchronized void toMuseum(FinalBattle f) {
		Ship gone = new Ship(f.id, f.name, Ship.State.DOCKED, true);
		recordFate(gone, Fate.MUSEUM);
		closeFinal(f, true);
		HistoryLog.entry("MUSEUM", f.name + " is honoured in the Federation museum");
	}

	// ---- Steam Cloud's copies ----

	private String cloudCopy = null;
	/** The name of a ship whose copy continue.sav turned out to be (set aside since this was last asked), or null. */
	public synchronized String takeCloudCopy() { String c = cloudCopy; cloudCopy = null; return c; }
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
			File[] kept = historyOf(s).listFiles();
			if (kept != null) for (File f : kept) {
				if (!f.getName().endsWith(".sav")) continue;
				try { if (h.equals(SafeFiles.hash(f))) return s; } catch (IOException e) { }
			}
		}
		return null;
	}

	// ---- sectors travelled (the Immersive stipend) ----

	private File sectorsFile() { return new File(root, "sectors.txt"); }
	/** Sectors this fleet's boarded ships have been seen to advance, in all (FTL's progress, not the station's own changes). */
	public synchronized int sectorsSeen() {
		try { return Integer.parseInt(new String(SafeFiles.read(sectorsFile()), java.nio.charset.StandardCharsets.UTF_8).trim()); }
		catch (Exception e) { return 0; }
	}
	private void addSectors(int n) {
		if (n <= 0) return;
		try { SafeFiles.writeText(sectorsFile(), (sectorsSeen() + n) + "\n", false); }
		catch (IOException e) { log.warn("Could not count the sectors travelled: {}", e.toString()); }
	}

	// ---- beacons travelled (transmissions that answer a reply some beacons later) ----

	private File beaconsFile() { return new File(root, "beacons.txt"); }
	/** Beacons this fleet's boarded ships have been seen to explore, in all (FTL's progress, as with the sectors). */
	public synchronized int beaconsSeen() {
		try { return Integer.parseInt(new String(SafeFiles.read(beaconsFile()), java.nio.charset.StandardCharsets.UTF_8).trim()); }
		catch (Exception e) { return 0; }
	}
	private void addBeacons(int n) {
		if (n <= 0) return;
		try { SafeFiles.writeText(beaconsFile(), (beaconsSeen() + n) + "\n", false); }
		catch (IOException e) { log.warn("Could not count the beacons travelled: {}", e.toString()); }
	}

	// ---- one-time events (what the fleet has been through, for the transmissions that answer it) ----

	private File eventsFile() { return new File(root, "events.txt"); }
	private java.util.Properties events() {
		java.util.Properties p = new java.util.Properties();
		try { if (eventsFile().isFile()) p.load(new java.io.StringReader(new String(SafeFiles.read(eventsFile()), java.nio.charset.StandardCharsets.UTF_8))); }
		catch (IOException e) { log.warn("Could not read {}: {}", eventsFile(), e.toString()); }
		return p;
	}
	/** What an event recorded (a ship's name, say), or null if it hasn't happened. */
	public synchronized String event(String key) { return events().getProperty(key); }
	/** Records an event once: the first record stands. */
	public synchronized void recordEvent(String key, String value) {
		java.util.Properties p = events();
		if (p.getProperty(key) != null) return;
		p.setProperty(key, value);
		try {
			java.io.StringWriter w = new java.io.StringWriter();
			p.store(w, "What this fleet has been through, once each (Federation Home Planet's transmissions answer them)");
			SafeFiles.writeText(eventsFile(), w.toString(), false);
		} catch (IOException e) { log.warn("Could not record the event {}: {}", key, e.toString()); }
	}
	/** A boarded ship's new save: down to one point of hull with the fight over (no enemy alongside, or one beaten). */
	private void noteHull(Ship b, SavedGameState gs) {
		net.blerf.ftl.parser.SavedGameParser.ShipState s = gs.getPlayerShip(), enemy = gs.getNearbyShip();
		if (s == null || s.getHullAmt() != 1) return;
		if (enemy != null && enemy.getHullAmt() > 0 && enemy.isHostile()) return; // still in the fight
		recordEvent(EVENT_ONE_HULL, gs.getPlayerShipName());
	}
	/** One of the fleet's ships came out of a battle with one point of hull (her name). */
	public static final String EVENT_ONE_HULL = "one-hull";

	/** Adds scrap to the storage hold (a stipend). */
	public synchronized void depositToStorage(int scrap) throws IOException {
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
		if (b == null) return false;
		SavedGameState gs = b.save();
		if (gs == null) return false;
		String now = marksOf(gs);
		if (b.marks == null || b.marks.isEmpty()) { b.marks = now; VoyageLog.observe(this, b, gs); noteHull(b, gs); return true; }
		if (sameShip(b.marks, gs)) { VoyageLog.observe(this, b, gs); noteHull(b, gs); } // her voyage log (repairs, trades at a store... change no marks)
		if (now.equals(b.marks)) return false;
		if (sameShip(b.marks, gs)) {
			snapshot(b); // FTL's progress, kept: if FTL later writes over her, this is what comes back
			try { addSectors(gs.getSectorNumber() - Integer.parseInt(b.marks.split("\\|", -1)[2])); } catch (NumberFormatException e) { }
			try { addBeacons(gs.getTotalBeaconsExplored() - Integer.parseInt(b.marks.split("\\|", -1)[3])); } catch (NumberFormatException e) { }
			b.marks = now;
			return true;
		}
		String lostName = b.marks.split("\\|", -1)[1];
		b.name = lostName;
		recordFate(b, Fate.LOST);
		ships.remove(b);
		Ship n = new Ship(newId(), gs.getPlayerShipName(), Ship.State.BOARDED, gs.isDLCEnabled());
		n.stranger = true;
		n.hash = SafeFiles.hash(continueFile());
		n.marks = now;
		ships.add(n);
		overwritten = lostName;
		HistoryLog.entry("OVERWRITTEN", lostName + " (" + b.id + ") was boarded, and continue.sav is now another ship: " + n.name
				+ " (FTL's New Game, most likely). Her last seen version is in history/" + b.id);
		return true;
	}
	/** After the station writes the boarded ship: her marks follow (a rename, a New Journey, a retrofit are the station's own). */
	private void marked(Ship s, SavedGameState state) {
		if (s.state == Ship.State.BOARDED && state != null) s.marks = marksOf(state);
		VoyageLog.baseline(this, s, state); // the station's own change: not in her voyage log
	}
	/** FTL has written continue.sav (the save watcher): the boarded ship's voyage log takes note, if it's her. */
	public synchronized void observeBoarded() {
		Ship b = boarded();
		if (b == null || !continueFile().isFile()) return;
		SavedGameState gs;
		try { gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(continueFile()); } catch (Exception e) { return; } // mid-write: Refresh catches up
		if (b.marks != null && !b.marks.isEmpty() && !sameShip(b.marks, gs)) return;
		VoyageLog.observe(this, b, gs);
		noteHull(b, gs);
	}
	private void adoptStrays(File dir, Ship.State state, List<String> notes) {
		File[] files = dir.listFiles();
		if (files == null) return;
		java.util.Arrays.sort(files);
		for (File f : files) {
			if (!f.isFile() || !f.getName().toLowerCase().endsWith(".sav")) continue;
			String stem = f.getName().substring(0, f.getName().length() - 4);
			if (byId(stem) != null) continue;
			// give her an id of her own, renaming the file to match, so the entry and the file always agree
			Ship s = new Ship(newId(), stem, state, true);
			File target = new File(dir, s.id + ".sav");
			try {
				SafeFiles.move(f, target);
			} catch (IOException e) {
				log.warn("Could not adopt {}", f, e);
				continue;
			}
			ships.add(s);
			notes.add(f.getName() + " found in " + dir.getName() + "/: adopted (" + state.key + ")");
		}
	}

	/** Writes the manifest (a temporary file, then one move; the old one is kept as manifest.xml.bak). */
	public synchronized void saveManifest() throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
		sb.append("<!-- The ships in this vault. Federation Home Planet rewrites this file; edit it by hand only when it's closed. -->\r\n");
		sb.append("<manifest version=\"1\">\r\n");
		for (Ship s : ships) {
			sb.append("\t<ship id=\"").append(s.id).append("\" name=\"").append(XmlText.attr(s.name)).append("\" state=\"").append(s.state.key)
					.append("\" dlc=\"").append(s.dlc).append("\" hash=\"").append(s.hash == null ? "" : s.hash).append("\"");
			if (s.state == Ship.State.BOARDED && s.marks != null && !s.marks.isEmpty()) sb.append(" marks=\"").append(XmlText.attr(s.marks)).append("\"");
			if (s.stranger) sb.append(" stranger=\"true\"");
			if (s.fresh != null && !s.fresh.isEmpty()) sb.append(" fresh=\"").append(XmlText.attr(s.fresh)).append("\"");
			sb.append("/>\r\n");
		}
		sb.append("</manifest>\r\n");
		SafeFiles.writeText(manifestFile(), sb.toString(), true);
	}

	// ---- history ----

	/** History file names: UTC, so they keep their order across clock changes (daylight saving). */
	private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyyMMdd-HHmmss");
	static { STAMP.setTimeZone(java.util.TimeZone.getTimeZone("UTC")); }
	/** A history file's place in time: its stamp, then its counter ("…-2.sav" after "….sav" from the same second). */
	private static String order(File f) {
		String n = f.getName().replace(".sav", "");
		int dash = n.indexOf('-', 9); // past the date-time's own dash
		int count = 1;
		if (dash > 0) { try { count = Integer.parseInt(n.substring(dash + 1)); } catch (NumberFormatException e) { } n = n.substring(0, dash); }
		return n + String.format("%06d", count);
	}
	/** A new history file's name: this second's stamp, numbered past any from the same second (never reusing a pruned number). */
	private static File historyTarget(File dir) {
		String stamp = STAMP.format(new Date());
		int last = 0;
		File[] files = dir.listFiles();
		if (files != null) for (File f : files) {
			if (!f.getName().startsWith(stamp)) continue;
			String o = order(f);
			last = Math.max(last, Integer.parseInt(o.substring(o.length() - 6)));
		}
		return new File(dir, last == 0 ? stamp + ".sav" : stamp + "-" + (last + 1) + ".sav");
	}
	/**
	 * Oldest first: by when each was kept, then by name. (Names alone won't do: before 4B.04 they were in local time,
	 * now UTC, so an old name can sort after a new one.) A version is kept by a copy (stamped then) or a move of
	 * her current file (stamped when last written, which is after every version kept before it).
	 */
	private static final java.util.Comparator<File> OLDEST_FIRST = new java.util.Comparator<File>() {
		public int compare(File a, File b) {
			int t = Long.compare(a.lastModified(), b.lastModified());
			return t != 0 ? t : order(a).compareTo(order(b));
		}
	};

	/** Copies her current save into her history folder (before it's changed), keeping the last KEEP. Nothing if her newest kept version is the same. */
	public synchronized void snapshot(Ship s) throws IOException {
		File f = fileOf(s);
		if (!f.isFile()) return;
		File dir = historyOf(s);
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		List<File> kept = history(s);
		if (!kept.isEmpty() && SafeFiles.hash(kept.get(kept.size() - 1)).equals(SafeFiles.hash(f))) return;
		File target = historyTarget(dir);
		SafeFiles.copy(f, target);
		prune(dir);
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
	private void prune(File dir) {
		File[] files = dir.listFiles(new java.io.FileFilter() { public boolean accept(File f) { return f.isFile() && f.getName().endsWith(".sav"); } });
		if (files == null || files.length <= KEEP) return;
		java.util.Arrays.sort(files, OLDEST_FIRST);
		for (int i = 0; i < files.length - KEEP; i++) {
			if (!files[i].delete()) log.warn("Could not prune {}", files[i]);
		}
	}
	/** Her earlier versions, oldest first. */
	public List<File> history(Ship s) {
		File[] files = historyOf(s).listFiles();
		List<File> out = new ArrayList<File>();
		if (files != null) for (File f : files) if (f.isFile() && f.getName().endsWith(".sav")) out.add(f);
		Collections.sort(out, OLDEST_FIRST);
		return out;
	}

	// ---- writing ----

	/** Writes a ship's save: her history gets the old version first, then the file is replaced in one move. */
	public synchronized void write(Ship s, SavedGameState state) throws IOException {
		snapshot(s);
		writeQuietly(s, state);
		marked(s, state);
		keepBoarded(s);
		saveManifest();
	}
	private void writeQuietly(Ship s, SavedGameState state) throws IOException {
		File f = fileOf(s);
		byte[] bytes = SaveHelper.toBytes(state);
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
			SavedGameState g = new net.blerf.ftl.parser.SavedGameParser().readSavedGame(f);
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
		public Transaction put(Ship s, SavedGameState state) { pending.put(s, state); return this; }
		/** As {@link #put(Ship, SavedGameState)}, written only if her file is still the one fingerprinted {@code readHash} (from {@link #readCopy}). */
		public Transaction put(Ship s, SavedGameState state, String readHash) {
			pending.put(s, state);
			if (readHash != null) expected.put(s, readHash);
			return this;
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
				for (Map.Entry<Ship, SavedGameState> e : pending.entrySet()) bytes.put(fileOf(e.getKey()), SaveHelper.toBytes(e.getValue()));
				bytes.putAll(extra);
				List<File> tmps = new ArrayList<File>();
				try {
					for (Map.Entry<File, byte[]> e : bytes.entrySet()) {
						File tmp = new File(e.getKey().getAbsoluteFile().getParentFile(), e.getKey().getName() + ".tx");
						tmp.getParentFile().mkdirs();
						SafeFiles.writeSynced(tmp, e.getValue());
						tmps.add(tmp);
					}
				} catch (IOException e) {
					for (File t : tmps) t.delete();
					throw e;
				}
				// what each file holds now, to put back if a later replacement fails
				Map<File, byte[]> before = new LinkedHashMap<File, byte[]>();
				for (File f : bytes.keySet()) before.put(f, f.isFile() ? SafeFiles.read(f) : null);
				for (Ship s : pending.keySet()) snapshot(s);
				int i = 0;
				List<File> done = new ArrayList<File>();
				try {
					for (File f : bytes.keySet()) { SafeFiles.replace(tmps.get(i++), f); done.add(f); }
				} catch (IOException e) {
					for (File f : done) {
						try {
							if (before.get(f) == null) f.delete(); else SafeFiles.write(f, before.get(f));
						} catch (IOException again) {
							log.error("Could not put back " + f + " after a failed save", again);
						}
					}
					for (File t : tmps) t.delete();
					throw e;
				}
				for (Map.Entry<Ship, SavedGameState> e : pending.entrySet()) { e.getKey().written(e.getValue(), SafeFiles.hash(fileOf(e.getKey()))); marked(e.getKey(), e.getValue()); keepBoarded(e.getKey()); }
				saveManifest();
			}
		}
	}
	public Transaction begin() { return new Transaction(); }

	// ---- moving ships about ----

	/** Takes command of a docked ship: her save becomes continue.sav. Any ship already boarded is docked first. */
	public synchronized void board(Ship s) throws IOException {
		if (s.state != Ship.State.DOCKED) throw new IOException(s.name + " isn't docked");
		Ship b = boarded();
		if (b != null) dock();
		File from = fileOf(s), to = continueFile();
		if (to.exists()) throw new IOException("continue.sav is already there (a ship the station doesn't know?)");
		String hash;
		try {
			SafeFiles.copy(from, to);
			hash = SafeFiles.hash(to);
			// her vault copy goes into her history: from now on continue.sav is the only current version
			moveToHistory(s, from);
		} catch (IOException e) {
			to.delete(); // a copy left in continue.sav would come back as a second, boarded her on the next Refresh
			throw e;
		}
		s.state = Ship.State.BOARDED;
		s.hash = hash;
		s.marks = ""; // seen afresh at the next look
		saveManifest();
		HistoryLog.entry("BOARD", s.name + "  ships/" + s.id + ".sav -> continue.sav");
	}
	/** Docks the boarded ship: continue.sav comes back into the vault. */
	public synchronized void dock() throws IOException {
		Ship b = boarded();
		if (b == null) return;
		File from = continueFile();
		if (!from.isFile()) throw new IOException("continue.sav is missing: " + b.name + " may have been lost in FTL. Refresh to take stock.");
		File to = new File(shipsDir(), b.id + ".sav"); // where a docked ship's save lives (fileOf, once she's docked)
		SafeFiles.write(to, SafeFiles.read(from)); // a temporary file, then one move: a failure leaves her boarded, as she was
		b.state = Ship.State.DOCKED;
		b.hash = SafeFiles.hash(to);
		if (!from.delete()) {
			b.state = Ship.State.BOARDED;
			to.delete();
			throw new IOException("continue.sav could not be removed (is FTL running?)");
		}
		b.invalidate();
		saveManifest();
		HistoryLog.entry("DOCK", b.name + "  continue.sav -> ships/" + b.id + ".sav");
	}
	/** Disbands the boarded ship: continue.sav goes to the junkyard. */
	public synchronized void disband() throws IOException {
		Ship b = boarded();
		if (b == null) return;
		File from = continueFile();
		b.state = Ship.State.JUNKED;
		File to = fileOf(b);
		try {
			SafeFiles.move(from, to);
		} catch (IOException e) {
			b.state = Ship.State.BOARDED;
			throw e;
		}
		b.invalidate();
		saveManifest();
		HistoryLog.entry("DISBAND", b.name + "  continue.sav -> junkyard/" + b.id + ".sav");
	}
	/** Salvages a junked ship: back to the ships folder, docked. */
	public synchronized void salvage(Ship s) throws IOException {
		if (s.state != Ship.State.JUNKED) throw new IOException(s.name + " isn't in the junkyard");
		File from = fileOf(s);
		s.state = Ship.State.DOCKED;
		try {
			SafeFiles.move(from, fileOf(s));
		} catch (IOException e) {
			s.state = Ship.State.JUNKED;
			throw e;
		}
		s.invalidate();
		saveManifest();
		HistoryLog.entry("SALVAGE", s.name + "  junkyard/" + s.id + ".sav -> ships/" + s.id + ".sav");
	}
	/** Removes a ship for good (scrapped or destroyed): her last save goes into her history, and she leaves the manifest. Logged under {@code why} unless null. */
	public synchronized void remove(Ship s, String why) throws IOException {
		remove(s, why, "DESTROY".equals(why) ? Fate.DESTROYED : Fate.SCRAPPED);
	}
	/** The same, recording this fate (a ship traded in or auctioned off is SOLD). */
	public synchronized void remove(Ship s, String why, Fate fate) throws IOException {
		File f = fileOf(s);
		if (f.isFile()) moveToHistory(s, f);
		recordFate(s, fate);
		ships.remove(s);
		saveManifest();
		if (why != null) HistoryLog.entry(why, s.name + "  " + s.state.key + "/" + s.id + ".sav -> history/" + s.id + "/");
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
		/** Sold to the Federation museum after a final victory: her price was paid, so she doesn't come back. */
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
	private static final String FATE_FILE = "fate.txt";
	private void recordFate(Ship s, Fate fate) {
		try {
			File dir = historyOf(s);
			if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
			SafeFiles.writeText(new File(dir, FATE_FILE), fate.name() + "\n" + s.name + "\n", false);
		} catch (IOException e) {
			log.warn("Could not record what became of {}: {}", s, e.toString());
		}
	}

	/** A ship that left the fleet and can come back: her id, name, what became of her, and her last kept save. */
	public static final class Departed {
		public final String id, name;
		public final Fate fate;
		public final File last;
		Departed(String id, String name, Fate fate, File last) { this.id = id; this.name = name; this.fate = fate; this.last = last; }
	}
	/** Destroyed and lost ships with a kept save, newest departure first. Scrapped ships never come back (their gear is in storage). */
	public synchronized List<Departed> recoverable() {
		List<Departed> out = new ArrayList<Departed>();
		File[] dirs = historyDir().listFiles();
		if (dirs == null) return out;
		for (File d : dirs) {
			if (!d.isDirectory() || byId(d.getName()) != null) continue;
			File fate = new File(d, FATE_FILE);
			if (!fate.isFile()) continue; // left before fates were kept: what became of her isn't known
			try {
				String[] lines = new String(SafeFiles.read(fate), java.nio.charset.StandardCharsets.UTF_8).split("\n");
				Fate f = Fate.valueOf(lines[0].trim());
				if (f == Fate.SCRAPPED || f == Fate.MUSEUM || f == Fate.SOLD || f == Fate.TRANSFERRED || f == Fate.RETURNED || f == Fate.SEIZED) continue; // a traded ship brought back would be in two fleets
				File[] saves = d.listFiles(new java.io.FileFilter() { public boolean accept(File x) { return x.isFile() && x.getName().endsWith(".sav"); } });
				if (saves == null || saves.length == 0) continue;
				java.util.Arrays.sort(saves, OLDEST_FIRST);
				out.add(new Departed(d.getName(), lines.length > 1 ? lines[1].trim() : d.getName(), f, saves[saves.length - 1]));
			} catch (Exception e) {
				log.warn("Could not read {}: {}", fate, e.toString());
			}
		}
		java.util.Collections.sort(out, new java.util.Comparator<Departed>() {
			public int compare(Departed a, Departed b) { return Long.compare(new File(b.last.getParentFile(), FATE_FILE).lastModified(), new File(a.last.getParentFile(), FATE_FILE).lastModified()); }
		});
		return out;
	}
	/** Brings a departed ship back to the Space Dock, docked, from her last kept save (which stays in her history too). */
	public synchronized Ship recover(Departed d) throws IOException {
		if (byId(d.id) != null) throw new IOException(d.name + " is already in the fleet");
		Ship s = new Ship(d.id, d.name, Ship.State.DOCKED, true);
		File to = fileOf(s);
		SafeFiles.write(to, SafeFiles.read(d.last));
		s.hash = SafeFiles.hash(to);
		ships.add(s);
		new File(d.last.getParentFile(), FATE_FILE).delete();
		if (new File(d.last.getParentFile(), FINAL).isFile()) closeFinal(new FinalBattle(d.id, d.name, new File(d.last.getParentFile(), FINAL), -1, -1, ""), false); // settled by coming back
		saveManifest();
		s.save(); // her name and DLC flag, as the save has them
		HistoryLog.entry("RECOVER", s.name + " (" + d.fate.name().toLowerCase() + ")  history/" + s.id + "/" + d.last.getName() + " -> ships/" + s.id + ".sav");
		return s;
	}
	/** Puts one of her earlier versions back as her current save; the one it replaces goes into her history first. */
	public synchronized void restore(Ship s, File version) throws IOException {
		if (s.state == Ship.State.STORAGE) throw new IOException("The Cargo Hold has no earlier versions to go back to");
		byte[] bytes = SafeFiles.read(version); // before the snapshot below, which may prune it
		snapshot(s);
		File f = fileOf(s);
		SafeFiles.write(f, bytes);
		s.invalidate();
		s.hash = SafeFiles.hash(f);
		marked(s, s.save());
		keepBoarded(s);
		saveManifest();
		HistoryLog.entry("RESTORE", s.name + "  history/" + s.id + "/" + version.getName() + " -> " + (s.isBoarded() ? "continue.sav" : s.state.key + "/" + s.id + ".sav"));
	}

	// ---- Long Range Comm.: ships that change hands ----

	/** What travels with a ship: her save, her voyage log and its last look, and her last trade mark. */
	static final String[] PACKAGE = {"ship.sav", VoyageLog.LOG, VoyageLog.LAST, TradeMark.FILE, "papers.txt"};
	private static final int PACKAGE_MAX = 16 * 1024 * 1024;

	/** A docked ship's package for another station (a zip of {@link #PACKAGE}). */
	public synchronized byte[] packageOf(Ship s) throws IOException {
		java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
		java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(bo);
		try {
			File[] files = {fileOf(s), new File(historyOf(s), VoyageLog.LOG), new File(historyOf(s), VoyageLog.LAST), new File(historyOf(s), TradeMark.FILE)};
			for (int i = 0; i < files.length; i++) {
				if (!files[i].isFile()) { if (i == 0) throw new IOException(s.name + "'s save is missing"); continue; }
				z.putNextEntry(new java.util.zip.ZipEntry(PACKAGE[i]));
				z.write(SafeFiles.read(files[i]));
				z.closeEntry();
			}
			// her papers: when she was first commissioned, carried through every trade
			TradeMark old = TradeMark.of(this, s.id);
			String commissioned = old != null && !old.commissioned.isEmpty() ? old.commissioned : homeplanet.parser.Museum.commissioned(this, s.id);
			java.util.Properties papers = new java.util.Properties();
			papers.setProperty("commissioned", commissioned);
			java.io.StringWriter pw = new java.io.StringWriter();
			papers.store(pw, "Her papers");
			z.putNextEntry(new java.util.zip.ZipEntry(PACKAGE[4]));
			z.write(pw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			z.closeEntry();
			// a custom ship's blueprint and pictures: another station can't fly her without them
			SavedGameState gs = s.save();
			if (gs == null) throw new IOException(s.name + "'s save can't be read");
			for (Map.Entry<String, byte[]> e : homeplanet.parser.ShipPapers.papersOf(gs.getPlayerShipBlueprintId()).entrySet()) {
				z.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));
				z.write(e.getValue());
				z.closeEntry();
			}
		} finally {
			z.close();
		}
		return bo.toByteArray();
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
						|| e.getName().startsWith(homeplanet.parser.ShipPapers.ART);
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
		File f = fileOf(s);
		if (f.isFile()) moveToHistory(s, f);
		writeFate(s.id, Fate.TRANSFERRED, s.name, to);
		ships.remove(s);
		saveManifest();
		HistoryLog.entry("SENT AWAY", s.name + " (" + s.id + ") to " + to + "'s fleet, over Long Range Comm.");
	}
	/** A trade with her in it was called off: she comes back docked, from her package (unless she's here already). */
	public synchronized Ship comeBack(String id, byte[] pkg) throws IOException {
		Ship here = byId(id);
		if (here != null) return here;
		byte[] sav = unpack(pkg).get(PACKAGE[0]);
		Ship s = new Ship(id, "", Ship.State.DOCKED, true);
		File to = fileOf(s);
		SafeFiles.write(to, sav);
		s.hash = SafeFiles.hash(to);
		ships.add(s);
		s.save();
		if (s.name == null || s.name.isEmpty()) s.name = "Unknown ship";
		new File(historyOf(s), FATE_FILE).delete();
		saveManifest();
		HistoryLog.entry("RETURNED", s.name + " (" + id + "): the trade was called off, and she is back at the Space Dock");
		return s;
	}
	/** The trade went through: she flies for the other fleet now (her history stays here, as a record). */
	public synchronized void transferred(String id, String name, String to) throws IOException {
		Ship still = byId(id);
		if (still != null && still.state == Ship.State.DOCKED) sendAway(still, to); // escrow didn't get as far as sending her
		writeFate(id, Fate.TRANSFERRED, name, to);
	}
	private void writeFate(String id, Fate fate, String name, String detail) throws IOException {
		File dir = new File(historyDir(), id);
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		SafeFiles.writeText(new File(dir, FATE_FILE), fate.name() + "\n" + name + "\n" + (detail == null ? "" : detail) + "\n", false);
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
		Ship s = new Ship(newId(), gs.getPlayerShipName(), Ship.State.DOCKED, gs.isDLCEnabled());
		File dir = historyOf(s);
		try {
			if (!dir.mkdirs()) throw new IOException("Could not create " + dir);
			if (files.containsKey(VoyageLog.LOG)) SafeFiles.write(new File(dir, VoyageLog.LOG), files.get(VoyageLog.LOG));
			if (files.containsKey(VoyageLog.LAST)) SafeFiles.write(new File(dir, VoyageLog.LAST), files.get(VoyageLog.LAST));
			String original = TradeMark.originalIn(files.get(TradeMark.FILE));
			String commissioned = papersCommissioned(files.get(PACKAGE[4]));
			int sectors = VoyageLog.visited(this, s.id, gs);
			SafeFiles.write(new File(dir, TradeMark.FILE), TradeMark.text(tradeLine, from, original == null ? from : original, commissioned, gs, sectors));
			File f = fileOf(s);
			SafeFiles.write(f, save); // her save as it arrived, or renamed onto a blueprint here (homeplanet.parser.ShipPapers)
			s.hash = SafeFiles.hash(f);
			ships.add(s);
			setOut(s, gs, "Received from " + from + "'s fleet at The Home Planet Station");
			homeplanet.parser.Museum.setCommissioned(this, s.id, commissioned); // her own date, not her arrival
		} catch (IOException e) {
			ships.remove(s);
			fileOf(s).delete();
			SafeFiles.deleteTree(dir);
			try { saveManifest(); } catch (IOException again) { log.error("Could not write the manifest", again); }
			throw e;
		}
		HistoryLog.entry("RECEIVED", s.name + " (" + s.id + ") from " + from + "'s fleet, over Long Range Comm.: docked");
		return s;
	}
	/** The commission date a ship's papers give, or "". */
	private static String papersCommissioned(byte[] papers) {
		if (papers == null) return "";
		java.util.Properties p = new java.util.Properties();
		try { p.load(new java.io.StringReader(new String(papers, java.nio.charset.StandardCharsets.UTF_8))); } catch (IOException e) { return ""; }
		String d = p.getProperty("commissioned", "").trim();
		return d.length() > 40 ? "" : d;
	}
	/** Is a copy kept of her on the way to the last battle (a final battle not yet settled)? Such a ship stays in the fleet. */
	public boolean finalBattlePending(Ship s) { return new File(historyOf(s), FINAL).isFile(); }

	/** A ship just built (commissioned): written into the ships folder, docked. */
	public synchronized Ship adopt(SavedGameState state) throws IOException {
		Ship s = new Ship(newId(), state.getPlayerShipName(), Ship.State.DOCKED, state.isDLCEnabled());
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
	private void moveToHistory(Ship s, File f) throws IOException {
		File dir = historyOf(s);
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		List<File> kept = history(s);
		if (!kept.isEmpty() && SafeFiles.hash(kept.get(kept.size() - 1)).equals(SafeFiles.hash(f))) {
			if (!f.delete()) throw new IOException("Could not remove " + f); // her newest kept version is the same
			return;
		}
		File target = historyTarget(dir);
		SafeFiles.move(f, target);
		prune(dir);
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
		List<File> dirs = new ArrayList<File>();
		File[] h = historyDir().listFiles(), r = surrenderedDir().listFiles();
		if (h != null) dirs.addAll(java.util.Arrays.asList(h));
		if (r != null) dirs.addAll(java.util.Arrays.asList(r)); // a surrender can be undone: its ships still count
		for (File d : dirs) {
			File[] fs = d.listFiles();
			if (fs == null) continue;
			for (File f : fs) {
				if (!f.getName().endsWith(".sav")) continue;
				List<String> ids = homeplanet.parser.Retrofit.blueprintIds(f);
				if (ids != null) out.addAll(ids);
			}
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
			Map<String, String> names = manifestNames(new File(other, MANIFEST));
			for (String dir : new String[] {"ships", "junkyard"}) {
				File[] fs = new File(other, dir).listFiles();
				if (fs == null) continue;
				for (File f : fs) {
					if (!f.getName().endsWith(".sav")) continue;
					List<String> ids = homeplanet.parser.Retrofit.blueprintIds(f);
					if (ids != null && !ids.contains(bpId)) continue; // an unreadable one counts, as usingBlueprint does
					String id = f.getName().substring(0, f.getName().length() - 4);
					out.add((names.containsKey(id) ? names.get(id) : id) + " (" + title(k) + " fleet)");
				}
			}
		}
		return out;
	}
	private List<File> otherFleetSaves() {
		List<File> out = new ArrayList<File>();
		for (File other : otherRoots())
			for (String dir : new String[] {"ships", "junkyard", "history", "surrendered"}) collectSaves(new File(other, dir), out, 2);
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
	/** Ship ids and names from a manifest file (empty if there's none, or it can't be read). */
	static Map<String, String> manifestNames(File manifest) {
		Map<String, String> out = new LinkedHashMap<String, String>();
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
		HistoryLog.entry("SWITCH FLEET", "to the " + title(toSlot) + " fleet" + (b == null ? "" : "; " + b.name + " docked here, to be boarded again on return"));
		Vault to = open(from.saves, toSlot);
		File back = new File(to.root, PARKED);
		if (back.isFile()) {
			String id = new String(SafeFiles.read(back), java.nio.charset.StandardCharsets.UTF_8).trim();
			Ship s = to.byId(id);
			if (s != null && s.state == Ship.State.DOCKED && !to.continueFile().exists()) to.board(s);
			if (!back.delete()) log.warn("Could not remove {}", back);
		}
		HistoryLog.entry("SWITCH FLEET", "now the " + title(toSlot) + " fleet" + (to.boarded() == null ? "" : "; " + to.boarded().name + " boarded again"));
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
		try { zipped = z.size(); } finally { z.close(); }
		if (zipped != files) throw new IOException("The copy in " + zip + " is incomplete (" + zipped + " of " + files + " files): nothing was deleted");
		if (!SafeFiles.deleteTree(im))
			throw new IOException("Some of " + im + " could not be deleted (a file in use?). The whole career is kept in " + zip
					+ "; delete the folder by hand once The Home Planet Station is closed");
		HistoryLog.entry("CAREER ENDED", "the " + title(slot) + " career was ended; a copy is kept in " + OLD_CAREERS + "/" + zip.getName());
		return zip;
	}
	private static int countFiles(File dir) {
		File[] fs = dir.listFiles();
		int n = 0;
		if (fs != null) for (File f : fs) n += f.isDirectory() ? countFiles(f) : f.isFile() ? 1 : 0;
		return n;
	}

	public synchronized void sendToOtherFleet(Ship s, boolean junkyard) throws IOException {
		File dir = new File(otherRoot(), junkyard ? "junkyard" : "ships");
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		String stem = (s.name == null || s.name.trim().isEmpty() ? "Unknown ship" : s.name.trim()).replaceAll("[\\\\/:*?\"<>|]", "_");
		File to = new File(dir, stem + ".sav");
		for (int i = 2; to.exists(); i++) to = new File(dir, stem + " " + i + ".sav");
		SafeFiles.move(fileOf(s), to);
		ships.remove(s);
		saveManifest();
		HistoryLog.entry("SENT", s.name + "  " + (s.isBoarded() ? "continue.sav" : s.state.key + "/" + s.id + ".sav") + " -> the "
				+ (immersive ? "Sandbox" : title(immersiveSlot)) + " fleet's " + (junkyard ? "Junkyard" : "Space Dock"));
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
			from.saveManifest();
		}
		HistoryLog.entry("HANDED OVER", s.name + " (continue.sav) to the " + (from.immersive ? "Sandbox" : title(immersiveSlot)) + " fleet, now in use");
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
