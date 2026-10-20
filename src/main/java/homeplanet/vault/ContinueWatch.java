package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import homeplanet.core.Event;
import homeplanet.core.StationLog;
import homeplanet.core.SafeFiles;
import homeplanet.core.Store;
import homeplanet.model.Words;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Watching continue.sav (7.02, out of Vault): is it still the boarded ship, Steam Cloud's copies set aside, a career ship FTL's New Game wrote over offered back, and what a new save says (one point of hull, work at a store). */
final class ContinueWatch {
	private ContinueWatch() { }
	private static final Logger log = LoggerFactory.getLogger(ContinueWatch.class);

	/** What the player is told of a copy continue.sav turned out to be (set aside since this was last asked), or null. */
	static String takeCloudCopy(Vault v) { String c = v.cloudCopy; v.cloudCopy = null; return c; }

	/**
	 * A continue.sav no boarded ship owns, marked (ShipMark, 6.08) as a ship of this career the station knows (in the
	 * fleet, or one that has left it) or as a ship of another career: a copy FTL handed back (Steam Cloud, most likely),
	 * set aside rather than adopted as a second ship. Unmarked, or marked as a ship of this career the station has no
	 * record of: left to the older checks.
	 */
	static void setAsideMarked(Vault v, File cont, List<String> notes) {
		ShipMark.Found m = ShipMark.read(cont);
		if (m == null) return;
		boolean ours = v.slot.equals(m.career);
		Ship s = ours ? v.byId(m.id) : null;
		if (s != null && s.state == Ship.State.STORAGE) return;
		File dir;
		try { dir = s != null ? v.settleFolder(s) : ours ? v.folderOfId(m.id) : new File(v.root, Vault.SET_ASIDE); }
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
				synchronized (ShipStore.STAMP) { stamp = ShipStore.STAMP.format(new Date()); }
				kept = new File(dir, m.career + "." + m.id + "." + stamp + ".sav");
				SafeFiles.write(kept, copy);
			}
			if (!cont.delete()) throw new IOException("Could not remove " + cont);
			String where = (ours ? dir.getParentFile().getName() + "/" + dir.getName() + "/" + ShipStore.VERSIONS : Vault.SET_ASIDE) + "/" + kept.getName();
			String career = Vault.title(m.career);
			v.cloudCopy = ours
					? "Steam Cloud brought back an old copy of " + homeplanet.parser.ShipNames.the(name) + (s != null ? ", who is already in your fleet" : ", who has left your fleet") + ".\n"
							+ "The copy was set aside in " + Words.her() + " records, not added as a second ship."
					: "FTL's saves held a ship from your " + career + " fleet, not this one's.\n"
							+ Words.She() + " was set aside in " + v.root.getName() + "/" + Vault.SET_ASIDE + ", not added to this fleet: switch to " + career + " to fly " + Words.herObj() + " there.";
			notes.add("continue.sav was " + (ours ? "a copy of " + name : "a ship of the " + career + " fleet") + " (marked " + m.boards + (ours ? " of " + now : "") + " boardings): set aside in " + where);
			Event e = (s != null ? Vault.shipEvent("VAULT", s) : Event.of("VAULT").put("ship", (name != null ? name : "") + "." + m.id).put("ship_name", name).put("ship_id", m.id))
					.put("what", "set_aside").put("file", "continue.sav").put("to", where).put("mark_career", m.career).put("mark_boards", m.boards)
					.put("mark_day", m.day > 0 ? m.day : null).put("boards_now", ours ? now : null).put("in_fleet", s != null).put("other_career", !ours);
			StationLog.entry("VAULT", "continue.sav set aside: " + (ours ? "a copy of " + name : "a ship of the " + career + " fleet"), null, e);
		} catch (IOException e) {
			log.warn("Could not set aside the marked continue.sav ({} {}): {}", m.career, m.id, e.toString());
		}
	}

	/**
	 * The fleet's ship this continue.sav is a copy of, byte for byte: her current save, or one of her kept versions
	 * (Steam Cloud restoring the last continue.sav it uploaded after she was docked). Null if it's none of them.
	 */
	static Ship cloudCopyOf(Vault v, File cont) {
		String h;
		try { h = SafeFiles.hash(cont); } catch (IOException e) { return null; }
		for (Ship s : v.ships) {
			if (s.state == Ship.State.STORAGE || s.state == Ship.State.BOARDED) continue;
			try { if (v.fileOf(s).isFile() && h.equals(SafeFiles.hash(v.fileOf(s)))) return s; } catch (IOException e) { }
			for (File f : v.kept(s)) { // her versions/ (since 5.69 not her folder itself: concern 6, 5.97)
				try { if (h.equals(SafeFiles.hash(f))) return s; } catch (IOException e) { }
			}
		}
		return null;
	}

	/** This save is hers byte for byte: the save her record names, or one of her kept versions (Board keeps one as it takes her save). */
	static boolean isHers(Vault v, Ship s, File save) {
		String h;
		try { h = SafeFiles.hash(save); } catch (IOException e) { return false; }
		if (h.equals(s.hash)) return true;
		for (File f : v.kept(s)) {
			try { if (h.equals(SafeFiles.hash(f))) return true; } catch (IOException e) { }
		}
		return false;
	}

	/** A boarded ship's new save: down to one point of hull with the fight over (no enemy alongside, or one beaten). */
	static void noteHull(Vault v, Ship b, SavedGameState gs) {
		net.blerf.ftl.parser.SavedGameParser.ShipState s = gs.getPlayerShip(), enemy = gs.getNearbyShip();
		if (s == null || v.event(Vault.EVENT_ONE_HULL) != null) return;
		String at = gs.getSectorNumber() + ":" + gs.getCurrentBeaconId();
		String seen = v.event(ONE_HULL_SEEN + b.id);
		if (seen != null) { // she was at one point of hull: the letter comes only once she is alive and has jumped away (6.65)
			if (s.getHullAmt() > 0 && !seen.equals(at)) v.recordEvent(Vault.EVENT_ONE_HULL, gs.getPlayerShipName());
			return;
		}
		if (s.getHullAmt() != 1) return;
		if (enemy != null && enemy.getHullAmt() > 0 && enemy.isHostile()) return; // still in the fight
		v.recordEvent(ONE_HULL_SEEN + b.id, at);
	}

	/** Where (sector:beacon) a ship was first seen at one point of hull, until she jumps away from it (then EVENT_ONE_HULL). */
	static final String ONE_HULL_SEEN = "one-hull-seen.";

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
	static void noteWork(Vault v, Ship b, SavedGameState gs) {
		java.util.Properties p;
		try { p = Clock.read(v); }
		catch (IOException e) { log.warn("Could not read the fleet's clock: {}", e.toString()); return; } // never written back from a failed read
		String k = "work." + b.id + "."; // each ship's own stop: switching ships at a beacon doesn't count her work again
		int work = workDone(gs), beacons = gs.getTotalBeaconsExplored();
		boolean here = Integer.toString(beacons).equals(p.getProperty(k + "beacons"));
		int before = Store.num(p, k + "work", -1);
		boolean wasCredited = here && "true".equals(p.getProperty(k + "credited")), credited = wasCredited;
		if (here && before >= 0 && work > before && !credited) {
			VoyageLog.note(v, b, "Time spent on work at the beacon (buying, repairs or upgrades)"); // before the day moves (5.20): told on the stop's own day
			Clock.addBeacons(v, 1, "work at a store in FTL");
			credited = true;
		}
		if (here && before == work && credited == wasCredited) return; // nothing new
		try { Clock.set(v, k + "beacons", Integer.toString(beacons), k + "work", Integer.toString(work), k + "credited", Boolean.toString(credited)); }
		catch (IOException e) { log.warn("Could not record her work: {}", e.toString()); }
	}

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

	/** The name of a boarded ship FTL overwrote since this was last asked (her last seen version is in her records), or null. */
	static String takeOverwritten(Vault v) { String o = v.overwritten; v.overwritten = null; return o; }

	/**
	 * Checks continue.sav against the boarded ship as last seen. If it's her, FTL's progress is noted (and kept in
	 * her records). If not (FTL's New Game wrote over her), she is recorded lost, and continue.sav becomes a new,
	 * uncommissioned ship. True if the manifest changed.
	 */
	static boolean checkBoarded(Vault v) throws IOException {
		Ship b = v.boarded();
		if (b == null) return adoptContinue(v);
		SavedGameState gs = b.save();
		if (gs == null) return false;
		String now = marksOf(gs);
		// her voyage log first, then the clock: what she did at a stop belongs to that stop's day (5.18, the Captain's Log)
		if (b.marks == null || b.marks.isEmpty()) {
			if (ignoring(v, b)) quietly(v, b, gs, true);
			else { VoyageLog.observe(v, b, gs); Clock.countProgress(v, b, gs); noteHull(v, b, gs); }
			b.marks = now;
			return true;
		}
		if (sameShip(b.marks, gs)) {
			if (ignoring(v, b)) quietly(v, b, gs);
			else { VoyageLog.observe(v, b, gs); noteWork(v, b, gs); Clock.countProgress(v, b, gs); noteHull(v, b, gs); Overflow.note(v, b, gs); } // her voyage log (repairs, trades at a store... change no marks)
		}
		if (now.equals(b.marks)) return false;
		if (sameShip(b.marks, gs)) {
			v.snapshot(b); // FTL's progress, kept: if FTL later writes over her, this is what comes back
			b.marks = now;
			return true;
		}
		String lostName = b.marks.split("\\|", -1)[1];
		b.name = lostName;
		v.recordFate(b, Vault.Fate.LOST);
		int took = Reputation.lost(v, b);
		if (v.immersive) offerBack(v, b, took); // by accident, perhaps: she may be offered back (5.55)
		v.ships.remove(b);
		Ship n = new Ship(Vault.newId(), gs.getPlayerShipName(), Ship.State.BOARDED, gs.isDLCEnabled());
		n.origin = Strays.originNow("adopted");
		n.stranger = true;
		n.hash = SafeFiles.hash(v.continueFile());
		n.marks = now;
		v.ships.add(n);
		if (ignoring(v, n)) quietly(v, n, gs, true); // Immersive Mode: none of her run is the career's (heromedel, 5.54)
		else { Clock.setClock(v, n, 0, 0); Clock.countProgress(v, n, gs); } // FTL's New Game: her run so far was flown in the fleet's time
		v.overwritten = lostName;
		StationLog.entry("OVERWRITTEN", lostName + " (" + b.id + ") was boarded, and continue.sav is now another ship: " + n.name
				+ " (FTL's New Game, most likely). " + Words.Her() + " last seen version is in " + v.place(b), null,
				Event.of("OVERWRITTEN").put("ship", lostName + "." + b.id).put("ship_name", lostName).put("ship_id", b.id).put("versions", v.place(b))
						.put("by", n.name + "." + n.id).put("by_name", n.name).put("by_id", n.id).put("by_stranger", n.stranger));
		return true;
	}

	/**
	 * A ship the station didn't commission, boarded in Immersive Mode (heromedel, 5.54): the station doesn't watch FTL for
	 * her (no voyage log, reputation, clock, stipend time, parcels, crew, final battle) until the player decides what
	 * becomes of her. In Sandbox Mode any ship is the fleet's.
	 */
	static boolean ignoring(Vault v, Ship b) { return v.immersive && b != null && b.stranger && b.state == Ship.State.BOARDED; }

	/**
	 * What an ignored ship's look does: keeps up with where she is, so nothing she did is counted later either; FTL's
	 * unlocks and achievements since are seen, never the career's. Not on her first look: what's new in FTL's profile
	 * then is most likely the ship before her, her run ending.
	 */
	static void quietly(Vault v, Ship b, SavedGameState gs) { quietly(v, b, gs, false); }

	static void quietly(Vault v, Ship b, SavedGameState gs, boolean first) {
		VoyageLog.baseline(v, b, gs);
		Clock.setClock(v, b, gs.getSectorNumber(), gs.getTotalBeaconsExplored());
		if (first) return;
		try { homeplanet.parser.UnlockGrants.strangerSeen(homeplanet.parser.Unlocks.read()); } // noted as hers too: the player may have them taken back out (5.55)
		catch (RuntimeException e) { log.warn("Could not note FTL's unlocks while an uncommissioned ship is boarded: {}", e.toString()); }
	}

	/** Nothing boarded, and continue.sav there: FTL started a New Game. She's an uncommissioned ship, boarded (as on opening the fleet). */
	static boolean adoptContinue(Vault v) throws IOException {
		File cont = v.continueFile();
		if (!cont.isFile()) return false;
		Ship n = new Ship(Vault.newId(), "Unknown ship", Ship.State.BOARDED, true);
		n.origin = Strays.originNow("adopted");
		n.stranger = true;
		v.ships.add(n);
		SavedGameState gs = n.save();
		if (gs == null) { v.ships.remove(n); return false; } // FTL still writing it: the next look
		n.name = gs.getPlayerShipName();
		n.hash = SafeFiles.hash(cont);
		n.marks = marksOf(gs);
		if (ignoring(v, n)) quietly(v, n, gs, true);
		else Clock.setClock(v, n, gs.getSectorNumber(), gs.getTotalBeaconsExplored()); // counted from now, as one found on opening the fleet
		StationLog.entry("VAULT", "taking stock", java.util.Collections.singletonList("continue.sav is a ship the station didn't know (a new game started in FTL, most likely): " + Words.she() + " is now boarded"),
				Vault.shipEvent("VAULT", n).put("what", "adopted_continue").put("file", "continue.sav"));
		return true;
	}

	/** Notes her as one to offer back, if her last kept version can be: out of battle, or in one she can go back into. */
	static void offerBack(Vault v, Ship b, int repTaken) {
		List<File> kept = v.history(b);
		if (kept.isEmpty()) return;
		SavedGameState gs;
		try { gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(kept.get(kept.size() - 1)); } catch (Exception e) { return; }
		if (!restorable(gs)) return;
		java.util.Properties p = new java.util.Properties();
		p.setProperty("reputation", Integer.toString(repTaken));
		try { v.setNotes(v.folderOf(b), ShipStore.OVERWRITTEN, p); } // her record's overwritten section (5.98; overwritten.txt before)
		catch (IOException e) { log.warn("Could not note {} as one to offer back: {}", b, e.toString()); }
	}

	/**
	 * Can this version of her be put back? With no hostile ship alongside, yes. In a battle, only outside sector 8 and
	 * with her hull above 5: she goes back into that same battle.
	 */
	static boolean restorable(SavedGameState gs) {
		net.blerf.ftl.parser.SavedGameParser.ShipState s = gs.getPlayerShip(), enemy = gs.getNearbyShip();
		if (s == null) return false;
		boolean fight = enemy != null && enemy.isHostile() && enemy.getHullAmt() > 0;
		return !fight || (gs.getSectorNumber() != 7 && s.getHullAmt() > 5);
	}

	/** Career ships FTL's New Game wrote over that are to be offered back, newest first. */
	static List<Vault.Departed> offeredBack(Vault v) {
		List<Vault.Departed> out = new ArrayList<Vault.Departed>();
		for (Vault.Departed d : v.recoverable()) if (d.fate == Vault.Fate.LOST && !ShipStore.notes(d.folder, ShipStore.OVERWRITTEN).isEmpty()) out.add(d);
		return out;
	}

	/** The player said no: she stays lost, and isn't asked about again. */
	static void declineBack(Vault v, Vault.Departed d) {
		try { v.setNotes(d.folder, ShipStore.OVERWRITTEN, null); }
		catch (IOException e) { log.warn("Could not note that {} stays lost: {}", d.name, e.toString()); }
	}

	/**
	 * Brings her back as the boarded ship from her last kept version (into the same battle, if she was in one): an
	 * uncommissioned ship in continue.sav goes to the Sandbox fleet's Space Dock first; a ship of the fleet's own there
	 * is left boarded, and she comes back docked. Her fate is cleared and what her loss cost in reputation given back.
	 * FTL must be closed. Then {@link CrewRegister#shipBack} for her crew (outside the fleet's lock).
	 */
	static Ship restoreBack(Vault v, Vault.Departed d) throws IOException {
		if (v.byId(d.id) != null) throw new IOException(d.name + " is already in the fleet");
		int taken = Store.num(ShipStore.notes(d.folder, ShipStore.OVERWRITTEN), "reputation", 0); // what her loss took (a negative number)
		Ship now = v.boarded();
		if (now != null && now.stranger) { v.sendToOtherFleet(now, false); now = v.boarded(); }
		Ship s;
		if (now != null) {
			s = v.recover(d); // the fleet's own ship is boarded: she comes back to the Space Dock
		} else {
			s = new Ship(d.id, d.name, Ship.State.BOARDED, true);
			File to = new File(v.shipyardDir(), d.folder.getName());
			if (to.exists()) throw new IOException(to + " is already there");
			if (!v.shipyardDir().isDirectory() && !v.shipyardDir().mkdirs()) throw new IOException("Could not create " + v.shipyardDir());
			ActionProtection.Note n = ActionProtection.begin(v, "RESTORE"); // one note (5.88): continue.sav back, her fate gone (and the offer: she's back), her folder to the shipyard
			n.replace(v.continueFile(), SafeFiles.read(d.last));
			byte[] record = v.recordWithout(d.folder, ShipStore.FATE, ShipStore.OVERWRITTEN);
			if (record != null) n.replace(ShipStore.xml(d.folder), record);
			n.rename(d.folder, to);
			n.commit();
			v.folders.put(s.id, to);
			s.hash = SafeFiles.hash(v.continueFile());
			v.ships.add(s);
			SavedGameState gs = s.save();
			if (gs != null) {
				s.name = gs.getPlayerShipName();
				s.marks = marksOf(gs);
				Clock.setClock(v, s, gs.getSectorNumber(), gs.getTotalBeaconsExplored()); // the clock carries on from her restored point
				VoyageLog.baseline(v, s, gs);
			}
			saveManifest(v);
		}
		Reputation.restored(v, s, taken); // the offer went with her fate, in the same note that brought her back
		log.info("Restored {} after FTL's New Game: {}/versions/{} -> {}", s.name, v.place(s), d.last.getName(), s.isBoarded() ? "continue.sav" : v.place(s));
		StationLog.entry("RESTORE", "Restored " + homeplanet.parser.ShipNames.the(s.name) + " after FTL's New Game wrote over " + Words.herObj(), null,
				Vault.shipEvent("RESTORE", s).put("why", "overwritten").put("from", v.place(s) + "/" + ShipStore.VERSIONS + "/" + d.last.getName()).put("to", s.isBoarded() ? "continue.sav" : v.place(s)).put("reputation_back", taken));
		return s;
	}

	/** After the station writes the boarded ship: her marks follow (a rename, a New Journey, a retrofit are the station's own). */
	static void marked(Vault v, Ship s, SavedGameState state) {
		if (s.state == Ship.State.BOARDED && state != null) {
			s.marks = marksOf(state);
			Clock.setClock(v, s, state.getSectorNumber(), state.getTotalBeaconsExplored()); // her progress was counted before the write (countBefore)
		}
		VoyageLog.baseline(v, s, state); // the station's own change: not in her voyage log
	}

	/** FTL has written continue.sav (the save watcher): the boarded ship's voyage log takes note, if it's her. */
	static void observeBoarded(Vault v) {
		Ship b = v.boarded();
		if (b == null || !v.continueFile().isFile()) return;
		SavedGameState gs;
		try { gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(v.continueFile()); } catch (Exception e) { return; } // mid-write: Refresh catches up
		if (b.marks != null && !b.marks.isEmpty() && !sameShip(b.marks, gs)) return;
		if (ignoring(v, b)) { quietly(v, b, gs); return; } // not the career's ship (5.54)
		VoyageLog.observe(v, b, gs); // her voyage log first: what she did at a stop belongs to that stop's day
		Clock.countProgress(v, b, gs); // the fleet's clock moves as she flies
		noteHull(v, b, gs);
		Overflow.note(v, b, gs); // an augment she had no room for
	}

	/**
	 * Writes every ship's record into her folder (the manifest's successor, 5.69): a ship whose name changed gets her
	 * folder and files renamed with it; one whose folder sits in the wrong place is moved. The Cargo Hold needs none:
	 * its xml says what it is (5.84).
	 */
	static void saveManifest(Vault v) throws IOException {
		for (Ship s : v.ships) {
			if (s.state == Ship.State.STORAGE) { v.settleFolder(s); continue; } // its xml is all it needs (5.84)
			File d = v.settleFolder(s);
			ShipStore.Record r = v.recordOf(s);
			if (!d.getName().equals(ShipStore.stem(s.name, s.id))) {
				ShipStore.Record old = ShipStore.read(d);
				String was = old == null ? null : old.name;
				r.pastNames.clear(); if (old != null) r.pastNames.addAll(old.pastNames);
				if (was != null && !was.isEmpty() && !was.equals(s.name)) r.pastNames.add(was);
				d = ShipStore.renameTo(d, r);
				v.folders.put(s.id, d);
			} else {
				ShipStore.write(d, r);
			}
		}
	}
}
