package homeplanet.vault;

import java.io.File;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One ship the station knows about: a save file in the vault (docked or in the junkyard), or the one FTL is
 * flying (continue.sav). Her id never changes, whatever file she's in, so the log and the history folder can
 * follow her. The two storage holds are ships too, of a kind (see {@link State#STORAGE}).
 */
public final class Ship {
	private static final Logger log = LoggerFactory.getLogger(Ship.class);

	public enum State {
		/** In the vault's ships folder, waiting at the Space Dock. */
		DOCKED("docked"),
		/** The ship FTL flies: her save is continue.sav in FTL's own folder. At most one ship is boarded. */
		BOARDED("boarded"),
		/** Disbanded: in the vault's junkyard folder, awaiting salvage or scrap. */
		JUNKED("junked"),
		/** A storage hold (the Cargo Hold): a save FTL never loads, used as a warehouse. */
		STORAGE("storage");
		public final String key;
		State(String key) { this.key = key; }
		public static State of(String key) {
			for (State s : values()) if (s.key.equals(key)) return s;
			return DOCKED;
		}
	}

	public final String id;
	/** Her name, as last read from the save (kept in the manifest so an unreadable save still shows a name). */
	public String name;
	public State state;
	/** Whether the save is an Advanced Edition one (a ship trades only with her own kind of storage). */
	public boolean dlc;
	/** Fingerprint of the file as last written or seen, to notice when FTL changed it. */
	public String hash = "";
	/**
	 * The boarded ship as the station last saw her (model, name, and the journey's totals, which only go up), so a
	 * continue.sav that isn't her (FTL's New Game) is noticed. Empty until first seen.
	 */
	String marks = "";
	/** A ship The Home Planet Station didn't commission: a continue.sav it didn't know (Immersive Mode asks about her). */
	public boolean stranger;
	/**
	 * Where the station last set her out (sector|beacon: commissioned, a New Journey, rescued): until she leaves that
	 * beacon she counts as still at The Home Planet Station, and may trade. Empty otherwise.
	 */
	String fresh = "";
	/** How she came into the fleet (6.10: "commissioned.6.10.20261008-174200"), kept in her record; empty until known. */
	String origin = "";

	private SavedGameState save;
	private String readError;
	private String readHash;   // the file the parsed save came from
	private String failedHash; // the file that couldn't be parsed (not tried again until it changes)

	Ship(String id, String name, State state, boolean dlc) {
		this.id = id;
		this.name = name;
		this.state = state;
		this.dlc = dlc;
	}

	/** Where her save is right now. */
	public File file() {
		return Vault.get().fileOf(this);
	}

	/** Her save, parsed (and kept until the file changes). Null if it can't be read; see {@link #readError()}. */
	public synchronized SavedGameState save() {
		File f = file();
		if (!f.isFile()) { save = null; readError = "no file"; readHash = null; return null; }
		String h;
		try { h = SafeFiles.hash(f); } catch (Exception e) { h = null; }
		if (save != null && h != null && h.equals(readHash)) return save;
		if (save == null && h != null && h.equals(failedHash)) return null;
		save = null;
		readError = null;
		try {
			save = isStorage() ? homeplanet.parser.HoldXml.read(f) : HomePlanet.savedGameParser.readSavedGame(f); // the Cargo Hold's is its xml (5.84)
			readHash = h;
			failedHash = null;
			if (save != null) {
				name = save.getPlayerShipName();
				dlc = save.isDLCEnabled();
			}
		} catch (Exception e) {
			// an IOException for an unknown format, a RuntimeException for a ship the game data doesn't have (mod missing)
			log.warn("Could not read {}: {}", f, e.toString());
			readError = e.toString();
			failedHash = h;
		}
		return save;
	}
	/** Why the save couldn't be read, or null. */
	public String readError() { return readError; }
	/** Forgets the parsed save (and any failure), so the next {@link #save()} reads the file again. */
	public synchronized void invalidate() { save = null; readHash = null; failedHash = null; }
	/** Takes a save just written for her as the current one (no re-read needed). */
	synchronized void written(SavedGameState s, String hashNow) {
		save = s;
		readHash = hashNow;
		hash = hashNow;
		if (s != null) { name = s.getPlayerShipName(); dlc = s.isDLCEnabled(); }
	}

	public boolean isStorage() { return state == State.STORAGE; }
	public boolean isBoarded() { return state == State.BOARDED; }

	@Override public String toString() { return name + " (" + id + ", " + state.key + ")"; }
}
