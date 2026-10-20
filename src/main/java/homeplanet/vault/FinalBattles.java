package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import homeplanet.core.Event;
import homeplanet.core.StationLog;
import homeplanet.core.SafeFiles;
import homeplanet.core.Store;
import homeplanet.model.Words;
import homeplanet.parser.SaveHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The final victory (7.02, out of Vault): the copy kept on the way to the last battle, the rescue or reward offered when a ship is lost there, and the museum. */
final class FinalBattles {
	private FinalBattles() { }
	private static final Logger log = LoggerFactory.getLogger(FinalBattles.class);

	/*
	 * FTL writes continue.sav as the Rebel Flagship heads for the last battle (her pending stage 3, not yet alongside),
	 * again as she arrives and as the player closes her message, then nothing until the fight ends; a win updates the
	 * profile (its victory count, a Top Scores entry) and deletes continue.sav a moment later. So the station keeps the
	 * last copy written while she is on her way, a calm save with no battle in it, and the profile's victory count then.
	 * When the ship is later found lost, a count gone up means she won.
	 */
	static final String FINAL = "final-battle.sav"; // the counts then: her record's final section (5.98; final-battle.txt before)

	/** Is this save the boarded ship with the Rebel Flagship on her way to the last battle? */
	static boolean flagshipOnHerWay(SavedGameState gs) {
		return !gs.isRebelFlagshipNearby() && gs.getRebelFlagshipState() != null && gs.getRebelFlagshipState().getPendingStage() >= 3;
	}

	/**
	 * Watching continue.sav: if it's the boarded ship with the flagship on her way to the last battle, keeps a copy (the
	 * latest replaces the one before), with the profile's victory count now and her victorious Top Scores entries now.
	 * True if kept. A save FTL is still writing doesn't read, and is left for the next look.
	 */
	static boolean watchContinue(Vault v, int victoriesNow, java.util.function.BiFunction<String, String, Integer> victoriousScores) {
		Ship b = v.boarded();
		File cont = v.continueFile();
		if (b == null || !cont.isFile()) return false;
		File dir = v.folderOf(b), tmp = new File(dir, FINAL + ".tmp");
		try {
			if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
			SafeFiles.copy(cont, tmp); // read from a copy: FTL may write again meanwhile
			SavedGameState gs;
			try { gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(tmp); } catch (Exception e) { return false; } // mid-write
			if (b.marks != null && !b.marks.isEmpty() && !ContinueWatch.sameShip(b.marks, gs)) return false; // not her: the next look sorts that out
			if (v.ignoring(b)) return false; // not the career's ship (5.54)
			if (!flagshipOnHerWay(gs)) return false;
			boolean first = !new File(dir, FINAL).isFile();
			SafeFiles.move(tmp, new File(dir, FINAL));
			int scoresNow = victoriousScores.apply(gs.getPlayerShipName(), gs.getPlayerShipBlueprintId());
			if (victoriesNow < 0 || scoresNow < 0) { // the profile didn't read (FTL writing it?): the counts from the copy before stand
				java.util.Properties p = ShipStore.notes(dir, ShipStore.FINAL);
				if (victoriesNow < 0) victoriesNow = Store.num(p, "victoriesThen", -1);
				if (scoresNow < 0) scoresNow = Store.num(p, "scoresThen", -1);
			}
			v.setNotes(dir, ShipStore.FINAL, finalNotes(victoriesNow, scoresNow, ""));
			if (first) StationLog.entry("FINAL BATTLE", b.name + ": the Rebel Flagship is on " + Words.her() + " way to the last battle. A copy is kept in " + v.place(b) + "/" + FINAL, null,
					Vault.shipEvent("FINAL_BATTLE", b).put("copy", v.place(b) + "/" + FINAL).put("sector", gs.getSectorNumber() + 1).put("victories_then", victoriesNow).put("scores_then", scoresNow));
			return true;
		} catch (IOException e) {
			log.warn("Could not keep {}'s copy before the last battle: {}", b, e.toString());
			return false;
		} finally {
			tmp.delete();
		}
	}

	/** This ship's final battle (lost, with a copy kept), or null. */
	static Vault.FinalBattle finalBattle(Vault v, String id) {
		for (Vault.FinalBattle f : v.finalBattles()) if (f.id.equals(id)) return f;
		return null;
	}

	/** Notes that a rescued ship's offer is out (so it isn't made twice). */
	static void finalOffered(Vault v, Vault.FinalBattle f) throws IOException { finalNote(v, f, "offered"); }

	static void finalNote(Vault v, Vault.FinalBattle f, String outcome) throws IOException {
		v.setNotes(f.copy.getParentFile(), ShipStore.FINAL, finalNotes(f.victoriesThen, f.scoresThen, outcome));
	}

	static java.util.Properties finalNotes(int victoriesThen, int scoresThen, String outcome) {
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
	static void closeFinal(Vault v, Vault.FinalBattle f, boolean victory) {
		File dir = v.folderOfId(f.id); // her folder as it is now (it moves when she's brought home)
		File copy = new File(dir, FINAL);
		try { // one note (6.87): her copy kept as a version and gone, the note of her battle closed
			ActionProtection.Note n = ActionProtection.begin(v, "CLOSE_FINAL");
			if (copy.isFile()) {
				n.replace(ShipStore.versionFile(dir, victory ? "victory-" : "final-battle-"), SafeFiles.read(copy));
				n.delete(copy);
			}
			byte[] record = v.recordWithout(dir, ShipStore.FINAL);
			if (record != null) n.replace(ShipStore.xml(dir), record);
			n.commit();
		} catch (IOException e) { log.warn("Could not close {}'s final battle: {}", f.name, e.toString()); }
	}

	/**
	 * Brings a victorious ship home from her copy: docked, her journey reset as a New Journey would (the flagship and
	 * its fleet gone from her charts), her crew, cargo and damage as they were. Her next journey is at this difficulty
	 * (null: the one she won on).
	 */
	static Ship bringHome(Vault v, Vault.FinalBattle f, net.blerf.ftl.constants.Difficulty difficulty) throws IOException {
		if (v.byId(f.id) != null) throw new IOException(f.name + " is already in the fleet");
		SavedGameState gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(f.copy);
		SaveHelper.startJourney(gs, difficulty != null ? difficulty : gs.getDifficulty());
		java.util.Iterator<net.blerf.ftl.parser.SavedGameParser.CrewState> it = gs.getPlayerShip().getCrewList().iterator();
		while (it.hasNext()) if (!SaveHelper.isOwnCrew(it.next())) it.remove(); // boarders and the like stay behind
		Ship s = new Ship(f.id, gs.getPlayerShipName(), Ship.State.DOCKED, gs.isDLCEnabled());
		File was = v.folders.get(f.id);
		v.comeHome(s, was != null && was.isDirectory() ? was : v.settleFolder(s), SaveHelper.toBytes(gs)); // her folder back to the shipyard with her save, as one note (5.88)
		s.written(gs, SafeFiles.hash(v.fileOf(s)));
		v.ships.add(s);
		s.fresh = Vault.position(gs); // she's back at The Home Planet Station
		VoyageLog.baseline(v, s, gs);
		String rescued = "Rescued after the final engagement: back at The Home Planet Station, ready for a new journey";
		VoyageLog.note(v, s, homeplanet.core.Event.of("VOYAGE_NOTE").put("text", rescued).put("difficulty", gs.getDifficulty() == null ? null : gs.getDifficulty().toString().toLowerCase())
				.put("difficulty_chosen", difficulty != null).human(rescued));
		homeplanet.parser.Museum.setOut(v, s, false);
		try {
			v.saveManifest();
		} catch (IOException e) {
			v.ships.remove(s);
			v.fileOf(s).delete();
			throw e;
		}
		v.setNotes(v.folderOf(s), ShipStore.FATE, null);
		closeFinal(v, f, true);
		StationLog.entry("VICTORY", s.name + " was rescued after the last battle: docked, ready for a new journey", null, Vault.shipEvent("VICTORY", s).put("what", "rescued"));
		return s;
	}

	/** A rescued ship goes to the Federation Museum instead: her fate recorded, her copy kept as her last version. */
	static void toMuseum(Vault v, Vault.FinalBattle f) {
		Ship gone = new Ship(f.id, f.name, Ship.State.DOCKED, true);
		v.recordFate(gone, Vault.Fate.MUSEUM);
		closeFinal(v, f, true);
		StationLog.entry("MUSEUM", f.name + " is honoured in the Federation Museum", null, Vault.shipEvent("MUSEUM", gone));
	}
}
