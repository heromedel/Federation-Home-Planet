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
 * Board copies a ship's file to continue.sav; Dock copies it back. Every write of a ship's save is preceded by
 * a snapshot into her history folder, so the last KEEP versions can always be recovered by hand.
 */
public final class Vault {
	private static final Logger log = LoggerFactory.getLogger(Vault.class);

	public static final String FOLDER = "FederationHomePlanet";
	public static final String MANIFEST = "manifest.xml";
	/** How many earlier versions of a ship's save are kept. */
	public static final int KEEP = 10;

	private static Vault instance;
	public static Vault get() {
		if (instance == null) throw new IllegalStateException("The vault hasn't been opened yet");
		return instance;
	}
	public static boolean isOpen() { return instance != null; }
	/** Opens (creating if needed) the vault inside this saves folder and makes it the one in use. */
	public static Vault open(File savesFolder) throws IOException {
		Vault v = new Vault(savesFolder);
		instance = v; // before load(), so what load() logs goes into the vault's own log
		v.load();
		return v;
	}

	public final File saves;
	public final File root;
	private final List<Ship> ships = new ArrayList<Ship>();

	private Vault(File savesFolder) {
		this.saves = savesFolder;
		this.root = new File(savesFolder, FOLDER);
	}

	// ---- places ----

	public File continueFile() { return new File(saves, "continue.sav"); }
	public File shipsDir() { return new File(root, "ships"); }
	public File junkyardDir() { return new File(root, "junkyard"); }
	public File historyDir() { return new File(root, "history"); }
	public File artDir() { return new File(root, "art"); }
	public File designsFile() { return new File(root, "designs.xml"); }
	public File remodelsFile() { return new File(root, "remodels.xml"); }
	public File removedBlueprintsLog() { return new File(root, "removed-blueprints.log"); }
	public File historyLog() { return new File(root, "history.log"); }
	public File manifestFile() { return new File(root, MANIFEST); }
	/** The stored-systems list that goes with the storage hold. */
	public File systemsFile() { return new File(root, "storage-systems.txt"); }
	/** The storage hold's id (and file stem). Before 4B there were two holds; the old AE one's stem is kept for its file name. */
	static final String STORAGE_ID = "storage";

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
		if (b != null && !cont.isFile()) {
			notes.add(b.name + " was boarded, and continue.sav is gone: lost in action (FTL ends a run by deleting the save). "
					+ (historyOf(b).isDirectory() ? "Her last versions are in history/" + b.id : ""));
			ships.remove(b);
			b = null;
		}
		if (b == null && cont.isFile()) {
			Ship n = new Ship(newId(), "Unknown ship", Ship.State.BOARDED, true);
			n.name = ""; // filled in by takeStock() once the game data is loaded
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
		if (changed) saveManifest();
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
			sb.append("/>\r\n");
		}
		sb.append("</manifest>\r\n");
		SafeFiles.writeText(manifestFile(), sb.toString(), true);
	}

	// ---- history ----

	private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyyMMdd-HHmmss");

	/** Copies her current save into her history folder (before it's changed), keeping the last KEEP. */
	public synchronized void snapshot(Ship s) throws IOException {
		File f = fileOf(s);
		if (!f.isFile()) return;
		File dir = historyOf(s);
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		String stamp = STAMP.format(new Date());
		File target = new File(dir, stamp + ".sav");
		for (int n = 2; target.exists(); n++) target = new File(dir, stamp + "-" + n + ".sav");
		SafeFiles.copy(f, target);
		prune(dir);
	}
	private void prune(File dir) {
		File[] files = dir.listFiles();
		if (files == null || files.length <= KEEP) return;
		java.util.Arrays.sort(files);
		for (int i = 0; i < files.length - KEEP; i++) {
			if (!files[i].delete()) log.warn("Could not prune {}", files[i]);
		}
	}
	/** Her earlier versions, oldest first. */
	public List<File> history(Ship s) {
		File[] files = historyOf(s).listFiles();
		List<File> out = new ArrayList<File>();
		if (files != null) for (File f : files) if (f.isFile() && f.getName().endsWith(".sav")) out.add(f);
		Collections.sort(out);
		return out;
	}

	// ---- writing ----

	/** Writes a ship's save: her history gets the old version first, then the file is replaced in one move. */
	public synchronized void write(Ship s, SavedGameState state) throws IOException {
		snapshot(s);
		writeQuietly(s, state);
		saveManifest();
	}
	private void writeQuietly(Ship s, SavedGameState state) throws IOException {
		File f = fileOf(s);
		byte[] bytes = SaveHelper.toBytes(state);
		SafeFiles.write(f, bytes);
		s.written(state, SafeFiles.hash(f));
	}

	/**
	 * Several ships written together, or none: every save is serialized and written to a temporary file first
	 * (any failure there changes nothing), then the temporaries replace the real files one after another.
	 */
	public final class Transaction {
		private final Map<Ship, SavedGameState> pending = new LinkedHashMap<Ship, SavedGameState>();
		private final Map<File, byte[]> extra = new LinkedHashMap<File, byte[]>();
		public Transaction put(Ship s, SavedGameState state) { pending.put(s, state); return this; }
		/** Another file that belongs with the change (a stored-systems list), written with the same care. */
		public Transaction put(File f, byte[] bytes) { extra.put(f, bytes); return this; }
		public void commit() throws IOException {
			synchronized (Vault.this) {
				Map<File, byte[]> bytes = new LinkedHashMap<File, byte[]>();
				for (Map.Entry<Ship, SavedGameState> e : pending.entrySet()) bytes.put(fileOf(e.getKey()), SaveHelper.toBytes(e.getValue()));
				bytes.putAll(extra);
				List<File> tmps = new ArrayList<File>();
				try {
					for (Map.Entry<File, byte[]> e : bytes.entrySet()) {
						File tmp = new File(e.getKey().getAbsoluteFile().getParentFile(), e.getKey().getName() + ".tx");
						tmp.getParentFile().mkdirs();
						java.nio.file.Files.write(tmp.toPath(), e.getValue());
						tmps.add(tmp);
					}
				} catch (IOException e) {
					for (File t : tmps) t.delete();
					throw e;
				}
				for (Ship s : pending.keySet()) snapshot(s);
				int i = 0;
				for (File f : bytes.keySet()) SafeFiles.replace(tmps.get(i++), f);
				for (Map.Entry<Ship, SavedGameState> e : pending.entrySet()) e.getKey().written(e.getValue(), SafeFiles.hash(fileOf(e.getKey())));
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
		SafeFiles.copy(from, to);
		String hash = SafeFiles.hash(to);
		// her vault copy goes into her history: from now on continue.sav is the only current version
		moveToHistory(s, from);
		s.state = Ship.State.BOARDED;
		s.hash = hash;
		saveManifest();
		HistoryLog.entry("BOARD", s.name + "  ships/" + s.id + ".sav -> continue.sav");
	}
	/** Docks the boarded ship: continue.sav comes back into the vault. */
	public synchronized void dock() throws IOException {
		Ship b = boarded();
		if (b == null) return;
		File from = continueFile();
		if (!from.isFile()) throw new IOException("continue.sav is missing: " + b.name + " may have been lost in FTL. Refresh to take stock.");
		b.state = Ship.State.DOCKED;
		File to = fileOf(b);
		SafeFiles.copy(from, to);
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
		File f = fileOf(s);
		if (f.isFile()) moveToHistory(s, f);
		ships.remove(s);
		saveManifest();
		if (why != null) HistoryLog.entry(why, s.name + "  " + s.state.key + "/" + s.id + ".sav -> history/" + s.id + "/");
	}
	/** A ship just built (commissioned): written into the ships folder, docked. */
	public synchronized Ship adopt(SavedGameState state) throws IOException {
		Ship s = new Ship(newId(), state.getPlayerShipName(), Ship.State.DOCKED, state.isDLCEnabled());
		ships.add(s);
		writeQuietly(s, state);
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
		String stamp = STAMP.format(new Date());
		File target = new File(dir, stamp + ".sav");
		for (int n = 2; target.exists(); n++) target = new File(dir, stamp + "-" + n + ".sav");
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
	/** True if any ship's save couldn't even be scanned (so "unused" can't be trusted). */
	public synchronized boolean anyUnscannable() {
		for (Ship s : ships) {
			if (s.state == Ship.State.STORAGE) continue;
			if (homeplanet.parser.Retrofit.blueprintIds(fileOf(s)) == null) return true;
		}
		return false;
	}
}
