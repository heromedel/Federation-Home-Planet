package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import homeplanet.core.Event;
import homeplanet.core.StationLog;
import homeplanet.core.SafeFiles;
import homeplanet.core.Store;
import homeplanet.model.Words;
import homeplanet.parser.SaveHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** What the opening finds and waits on the player's word for (6.10, heromedel's Plan N; 7.02, out of Vault): the marks on ships and saves, a loose save or folder, a record without her save, taking in, declining, restoring a save, and the way to Sandbox Mode's Junkyard. */
final class Strays {
	private Strays() { }
	private static final Logger log = LoggerFactory.getLogger(Strays.class);

	/** How a ship came into the fleet, now: "commissioned.6.10.20261008-174200" (UTC, as her versions are stamped). */
	static String originNow(String how) {
		String stamp;
		synchronized (ShipStore.STAMP) { stamp = ShipStore.STAMP.format(new Date()); }
		return how + "." + homeplanet.core.HomePlanet.APP_VERSION + "." + stamp;
	}

	/** The things the player said no to, remembered so they're not asked again (6.10). */
	static File declinedFile(Vault v) { return Store.file(v.root, "declined"); }

	static boolean declined(Vault v, String key) { return Store.read(declinedFile(v)).containsKey(key); }

	/**
	 * Every ship's place in this fleet (6.10), as the fleet opens: another career's record waits for its offer, and so
	 * does one with no career or origin (7.0: nothing is taken on evidence). A ship sent here from another career's
	 * fleet has her arrival logged.
	 */
	static void sortShips(Vault v) {
		for (Ship s : new ArrayList<Ship>(v.ships)) {
			if (s.state != Ship.State.DOCKED && s.state != Ship.State.JUNKED) continue;
			File dir = v.folderOf(s);
			ShipStore.Record r = ShipStore.read(dir);
			if (r == null) continue;
			if (!r.career.isEmpty() && !r.career.equals(v.slot)) { // another career's: not this fleet's until the player says
				v.ships.remove(s);
				if (!declined(v, "ship:" + s.id)) v.found.add(new Vault.Found(Vault.Found.Kind.OTHER, dir, r.name, s.id, r.career, "ship:" + s.id));
				continue;
			}
			if (r.has(ARRIVAL)) { // sent here from a career (Destroy, or a ship taken in there): her arrival, in this fleet's log
				java.util.Properties a = r.sections.get(ARRIVAL);
				StationLog.entry("VAULT", s.name + " arrived from the " + a.getProperty("from", "") + " fleet", null, Vault.shipEvent("VAULT", s).put("what", "arrived")
						.put("from_career", a.getProperty("career")).put("from", a.getProperty("from")).put("how", a.getProperty("how")).put("origin", r.origin));
				try { v.setNotes(dir, ARRIVAL, null); } catch (IOException e) { log.warn("Could not clear {}'s arrival note: {}", s.name, e.toString()); }
			}
			if (!r.career.isEmpty() && !r.origin.isEmpty()) continue; // known
			v.ships.remove(s); // nothing shows she's this fleet's: the player's word, unless they said no before
			if (!declined(v, "ship:" + s.id)) v.found.add(new Vault.Found(Vault.Found.Kind.UNPROVEN, dir, r.name, s.id, v.slot, "ship:" + s.id));
		}
	}

	/**
	 * Her save is gone from her folder, her record still there (6.10): she waits for the player's word on putting it back
	 * (her newest kept version, or her records), off the fleet's list meanwhile. False if they said no before: the
	 * memorial, as before 6.10.
	 */
	static boolean saveGone(Vault v, Ship s, List<String> notes) {
		File dir = v.folderOf(s);
		if (!dir.isDirectory() || ShipStore.read(dir) == null || declined(v, "ship:" + s.id)) return false;
		Vault.Found f = new Vault.Found(Vault.Found.Kind.NO_SAVE, dir, s.name, s.id, v.slot, "ship:" + s.id);
		f.fix = ShipStore.newestKept(dir) != null ? "version" : "rebuild";
		v.found.add(f);
		v.ships.remove(s);
		notes.add(s.name + " (" + s.state.key + "): " + Words.her() + " save is gone; asking about putting it back (" + f.fix + ")");
		return true;
	}

	
	/** Her record's section for a ship sent here from another career's fleet, until this fleet logs her arrival. */
	static final String ARRIVAL = "arrival";

	/**
	 * A save lying where no ship holds it (6.10; adopted without a word before): in the shipyard, the Junkyard, or FTL's
	 * saves folder (any .sav but continue.sav and FTL's profiles). One carrying the mark of a ship whose save is gone is
	 * hers; one carrying the mark of a ship in the fleet is a copy, left alone; any other FTL could fly waits for its offer.
	 */
	static void findLoose(Vault v, List<String> notes) {
		for (java.util.Iterator<Vault.Found> it = v.found.iterator(); it.hasNext(); ) { Vault.Found x = it.next(); if (x.kind == Vault.Found.Kind.LOOSE && !x.file.isFile()) it.remove(); } // gone meanwhile
		java.util.Set<String> known = new java.util.HashSet<String>();
		for (Vault.Found x : v.found) known.add(x.key);
		List<File> files = new ArrayList<File>();
		for (File d : new File[] {v.shipyardDir(), v.junkyardDir(), v.saves}) {
			File[] fs = d.listFiles();
			if (fs == null) continue;
			java.util.Arrays.sort(fs);
			for (File f : fs) {
				String n = f.getName().toLowerCase();
				if (!f.isFile() || !n.endsWith(".sav")) continue;
				if (d == v.saves && (n.equals("continue.sav") || n.equals("ae_prof.sav") || n.equals("prof.sav"))) continue;
				files.add(f);
			}
		}
		for (File f : files) {
			String key;
			try { key = "file:" + SafeFiles.hash(f); } catch (IOException e) { continue; }
			if (known.contains(key) || declined(v, key)) continue;
			ShipMark.Found mk = ShipMark.read(f);
			if (mk != null) {
				boolean placed = false;
				for (Vault.Found x : v.found) if (x.kind == Vault.Found.Kind.NO_SAVE && mk.id.equals(x.id)) { x.fix = "marked"; x.source = f; placed = true; }
				if (placed) continue;
				if (v.slot.equals(mk.career) && v.byId(mk.id) != null) { // a copy of a ship already here: never a second her
					notes.add(f.getName() + " is a copy of " + v.byId(mk.id).name + ", who is in the fleet: left where it is");
					remember(v, key, f.getName() + " (a copy of " + v.byId(mk.id).name + ")");
					continue;
				}
			}
			SavedGameState gs;
			try { gs = homeplanet.core.HomePlanet.savedGameParser.readSavedGame(f); } catch (Exception e) { continue; } // not a save FTL wrote
			if (gs == null || gs.getPlayerShip() == null || !homeplanet.parser.Retrofit.missingBlueprints(f).isEmpty()) continue; // not one FTL could fly
			v.found.add(new Vault.Found(Vault.Found.Kind.LOOSE, f, gs.getPlayerShipName(), null, mk == null ? "" : mk.career, key));
		}
	}

	static void remember(Vault v, String key, String what) {
		try {
			java.util.Properties p = Store.read(declinedFile(v));
			p.setProperty(key, what);
			Store.write(declinedFile(v), p, "What the player said no to (Federation Home Planet 6.10): not asked about again");
		} catch (IOException e) { log.warn("Could not remember {}: {}", what, e.toString()); }
	}

	/**
	 * Every docked and Junkyard ship's save carries her mark (6.10): one without it (from before 6.08, a backup put back,
	 * a save edited elsewhere) gets it, her version kept first. A save marked and unchanged since is passed over unread.
	 */
	static void markShips(Vault v) {
		for (Ship s : new ArrayList<Ship>(v.ships)) {
			if (s.state != Ship.State.DOCKED && s.state != Ship.State.JUNKED) continue;
			File f = v.fileOf(s);
			if (!f.isFile()) continue;
			String h = s.hash; // as this look found it
			if (h != null && h.equals(v.markedSeen.get(s.id))) continue;
			File dir = v.folderOf(s);
			java.util.Properties m = ShipStore.notes(dir, ShipMark.SECTION);
			if ("true".equals(m.getProperty("marked")) && h != null && h.equals(m.getProperty("save", ""))) { v.markedSeen.put(s.id, h); continue; } // marked, and unchanged since
			try {
				ShipMark.Found mk = ShipMark.read(f);
				if (mk != null && v.slot.equals(mk.career) && s.id.equals(mk.id)) { // hers already: noted, so it isn't read again
					m.setProperty("marked", "true"); m.setProperty("save", h);
					if (mk.blueprint != null) m.setProperty("class", mk.blueprint);
					v.setNotes(dir, ShipMark.SECTION, m);
				} else {
					remark(v, s);
				}
				v.markedSeen.put(s.id, s.hash);
			} catch (IOException e) {
				log.warn("Could not mark {}'s save: {}", s.name, e.toString());
			}
		}
	}

	/** Writes her mark into her save (docked or in the Junkyard), her version kept first, as one note; her count and last day as her record has them. */
	static void remark(Vault v, Ship s) throws IOException {
		File dir = v.settleFolder(s), f = v.fileOf(s);
		ShipMark.Marked mk = markFrom(v, s, dir, f);
		ShipStore.Record r = markedRecord(v, s, dir, mk);
		ActionProtection.Note n = ActionProtection.begin(v, "MARK");
		byte[] was = SafeFiles.read(f);
		if (!alreadyKept(v, s, was)) n.replace(ShipStore.versionFile(dir, null), was);
		n.replace(f, mk.bytes);
		n.replace(ShipStore.xml(dir), ShipStore.bytes(r));
		n.commit();
		ShipStore.prune(dir, Vault.KEEP);
		s.hash = r.hash;
		s.invalidate();
		log.debug("Marked {} ({} {}, boarded {} times)", s.name, v.slot, s.id, r.section(ShipMark.SECTION).getProperty("boards"));
	}

	/** This save with her mark as her record has it (her Board count and day): the save itself is left as it is. */
	static ShipMark.Marked markFrom(Vault v, Ship s, File dir, File save) throws IOException {
		java.util.Properties m = ShipStore.notes(dir, ShipMark.SECTION);
		return ShipMark.mark(save, v.slot, s.id, Store.num(m, "boards", 0), Store.num(m, "day", 0));
	}

	/** Her record once her save is this marked one: its fingerprint, and her mark's section. */
	static ShipStore.Record markedRecord(Vault v, Ship s, File dir, ShipMark.Marked mk) throws IOException {
		int boards = Store.num(ShipStore.notes(dir, ShipMark.SECTION), "boards", 0);
		String h = SafeFiles.hash(mk.bytes);
		ShipStore.Record r = v.recordOf(s);
		r.hash = h;
		java.util.Properties sec = r.section(ShipMark.SECTION);
		sec.setProperty("boards", Integer.toString(boards)); sec.setProperty("marked", "true"); sec.setProperty("save", h);
		if (mk.gs.getPlayerShipBlueprintId() != null) sec.setProperty("class", mk.gs.getPlayerShipBlueprintId());
		return r;
	}

	/** One of her kept saves (a version or a copy kept for a reason) has these very bytes: no need to keep them again (6.11). */
	static boolean alreadyKept(Vault v, Ship s, byte[] save) throws IOException {
		String h = SafeFiles.hash(save);
		for (File k : v.kept(s)) if (h.equals(SafeFiles.hash(k))) return true;
		return false;
	}

	/** As {@link #remark}, for an action that just put an older save back (Restore, Recover): never fatal. */
	static void remarkQuietly(Vault v, Ship s) {
		if (s.state != Ship.State.DOCKED && s.state != Ship.State.JUNKED) return;
		try { remark(v, s); } catch (IOException e) { log.warn("Could not mark {}'s save (marked at the next opening): {}", s.name, e.toString()); }
	}

	/**
	 * The player's yes to something found (6.10). In Sandbox Mode she's taken in here; in a career she goes to Sandbox
	 * Mode's fleet (a career takes only ships it knows); a ship whose save is gone gets one back. What came of it, in words.
	 */
	static String accept(Vault v, Vault.Found f) throws IOException {
		boolean here = Vault.SANDBOX.equals(v.slot);
		String result;
		if (f.kind == Vault.Found.Kind.NO_SAVE) {
			result = restoreSave(v, f);
		} else if (f.kind == Vault.Found.Kind.LOOSE) {
			Ship.State state = f.file.getParentFile().getAbsoluteFile().equals(v.junkyardDir().getAbsoluteFile()) ? Ship.State.JUNKED : Ship.State.DOCKED;
			String origin = originNow("taken_in") + "." + f.file.getName().replaceAll("\\.sav$", "").replaceAll("[^A-Za-z0-9_-]", "_");
			if (here) {
				Ship s = v.adoptFile(f.file, state, f.name);
				s.origin = origin;
				v.saveManifest();
				remark(v, s);
				result = f.name + " was taken into the fleet: " + Words.she() + " " + (state == Ship.State.JUNKED ? "waits in the Junkyard." : "is docked at the Space Dock.");
			} else {
				ActionProtection.Note n = ActionProtection.begin(v, "SEND_TO_SANDBOX"); // one note (6.87): her files there, the loose save gone
				File to = toSandbox(v, n, null, SafeFiles.read(f.file), f.name, Vault.newId(), state, origin, "taken_in");
				n.delete(f.file);
				commitSandbox(n, to);
				result = f.name + " was sent to Sandbox Mode's fleet: " + Words.she() + " waits there " + (state == Ship.State.JUNKED ? "in the Junkyard." : "at the Space Dock.");
			}
			StationLog.entry("VAULT", f.name + " taken in from " + f.file.getName() + (here ? "" : ", to Sandbox Mode's fleet"), null,
					Event.of("VAULT").put("what", "taken_in").put("ship_name", f.name).put("file", f.file.getName()).put("to", here ? "here" : "sandbox").put("origin", origin));
		} else { // UNPROVEN or OTHER: her folder
			ShipStore.Record r = ShipStore.read(f.file);
			Ship.State state = f.file.getParentFile().getAbsoluteFile().equals(v.junkyardDir().getAbsoluteFile()) ? Ship.State.JUNKED : Ship.State.DOCKED;
			String origin = r != null && !r.origin.isEmpty() ? r.origin : originNow("taken_in");
			if (here) {
				Ship s = new Ship(f.id, f.name, state, r == null || r.dlc);
				s.origin = origin;
				if (r != null) { s.hash = r.hash; s.marks = r.marks; s.stranger = r.stranger; s.fresh = r.fresh; }
				v.folders.put(s.id, f.file);
				v.ships.add(s);
				ShipStore.Record mine = v.recordOf(s);
				mine.career = v.slot;
				SafeFiles.write(ShipStore.xml(f.file), ShipStore.bytes(mine));
				s.invalidate();
				s.save();
				remark(v, s);
				result = f.name + " was taken into the fleet.";
			} else {
				File save = ShipStore.sav(f.file);
				if (!save.isFile()) throw new IOException(f.name + "'s folder has no save to send");
				ActionProtection.Note n = ActionProtection.begin(v, "SEND_TO_SANDBOX"); // one note (6.87): her files there, her folder gone here
				File to = toSandbox(v, n, f.file, SafeFiles.read(save), f.name, f.id, state, origin, f.kind == Vault.Found.Kind.OTHER ? "other_career" : "taken_in");
				n.delete(f.file);
				commitSandbox(n, to);
				result = f.name + " was sent to Sandbox Mode's fleet.";
			}
			StationLog.entry("VAULT", f.name + " taken in from " + Words.her() + " folder" + (here ? "" : ", to Sandbox Mode's fleet"), null,
					Event.of("VAULT").put("what", "taken_in").put("ship", f.name + "." + f.id).put("ship_name", f.name).put("ship_id", f.id).put("file", f.file.getName())
							.put("to", here ? "here" : "sandbox").put("origin", origin).put("record_career", f.career));
		}
		v.found.remove(f);
		v.saveManifest();
		return result;
	}

	/** The player's no (6.10): left as it is, and not asked again; a ship whose save is gone goes to the memorial. */
	static void decline(Vault v, Vault.Found f) throws IOException {
		v.found.remove(f);
		if (f.kind == Vault.Found.Kind.NO_SAVE) {
			Ship s = new Ship(f.id, f.name, Ship.State.DOCKED, true);
			v.folders.put(s.id, f.file);
			v.toMemorial(s);
		} else {
			remember(v, f.key, f.name + " (" + f.file.getName() + ")");
		}
		StationLog.entry("VAULT", f.name + ": left as it is (" + f.kind.name().toLowerCase() + ")", null,
				Event.of("VAULT").put("what", "declined").put("ship_name", f.name).put("ship_id", f.id).put("found", f.kind.name().toLowerCase()).put("file", f.file.getName()));
	}

	/** Her save back in her folder: a save carrying her mark, her newest kept version, or one rebuilt from her records. */
	static String restoreSave(Vault v, Vault.Found f) throws IOException {
		ShipStore.Record r = ShipStore.read(f.file);
		if (r == null) throw new IOException(f.name + "'s record can't be read");
		Ship.State state = f.file.getParentFile().getAbsoluteFile().equals(v.junkyardDir().getAbsoluteFile()) ? Ship.State.JUNKED : Ship.State.DOCKED;
		byte[] save;
		String how;
		if ("marked".equals(f.fix) && f.source != null && f.source.isFile()) { save = SafeFiles.read(f.source); how = "a save carrying " + Words.her() + " mark (" + f.source.getName() + ")"; }
		else {
			File newest = ShipStore.newestKept(f.file);
			if (newest != null) { save = SafeFiles.read(newest); how = Words.her() + " newest kept version"; }
			else { save = SaveHelper.toBytes(rebuild(f.file, r)); how = Words.her() + " records (rebuilt)"; }
		}
		Ship s = new Ship(f.id, r.name, state, r.dlc);
		s.marks = r.marks; s.stranger = r.stranger; s.fresh = r.fresh;
		s.origin = !r.origin.isEmpty() ? r.origin : originNow("rebuilt");
		v.folders.put(s.id, f.file);
		SafeFiles.write(ShipStore.sav(f.file), save);
		if ("marked".equals(f.fix) && f.source != null && !f.source.delete()) log.warn("Could not remove {} once it was back in her folder", f.source);
		s.hash = SafeFiles.hash(ShipStore.sav(f.file));
		v.ships.add(s);
		s.save();
		remark(v, s);
		StationLog.entry("VAULT", s.name + "'s save put back from " + how, null, Vault.shipEvent("VAULT", s).put("what", how.endsWith("(rebuilt)") ? "rebuilt" : "save_restored").put("from", how));
		return s.name + "'s save was put back from " + how + ".";
	}

	/**
	 * A save made from her records alone (6.10): her class (her record's marks name it) and name, her crew from their
	 * files in her folder (names, races, looks, skills and records), her supplies from her last look; her gear and
	 * systems as her class comes. Throws if her class isn't known.
	 */
	static SavedGameState rebuild(File dir, ShipStore.Record r) throws IOException {
		java.util.Properties mark = r.sections.containsKey(ShipMark.SECTION) ? r.sections.get(ShipMark.SECTION) : new java.util.Properties();
		String bp = mark.getProperty("class", r.marks == null ? "" : r.marks.split("\\|", -1)[0]);
		if (bp.isEmpty() || net.blerf.ftl.parser.DataManager.get().getShip(bp) == null) throw new IOException((r.name.isEmpty() ? Words.She() : r.name) + " can't be rebuilt: " + Words.her() + " record doesn't name a ship class the game knows");
		SavedGameState gs = homeplanet.parser.Commission.build(bp, r.name, net.blerf.ftl.constants.Difficulty.NORMAL, new java.util.Random());
		net.blerf.ftl.parser.SavedGameParser.ShipState ps = gs.getPlayerShip();
		// her crew: their files in her folder, or wherever the register has put them since her save went (last seen aboard her)
		List<File> crew = new ArrayList<File>();
		for (File d : new File[] {new File(dir, CrewRegister.CREW_DIR), CrewRegister.memorialCrewDir(Vault.get())}) {
			File[] fs = d.listFiles();
			if (fs != null) for (File c : fs) if (c.isFile() && c.getName().endsWith(".xml")) crew.add(c);
		}
		java.util.Collections.sort(crew);
		List<net.blerf.ftl.parser.SavedGameParser.CrewState> theirs = new ArrayList<net.blerf.ftl.parser.SavedGameParser.CrewState>();
		{
			for (File c : crew) {
				java.util.Properties p = CrewRegister.readFile(c);
				if (p == null) continue;
				String status = p.getProperty("status", "PRESENT");
				if (!("ship:" + r.id).equals(p.getProperty("place")) || status.equals("KILLED") || status.equals("RETIRED") || status.equals("TRANSFERRED")) continue;
				java.util.Map<String, String> fields = new LinkedHashMap<String, String>();
				for (String k : p.stringPropertyNames()) if (k.startsWith("rec.")) fields.put(k.substring(4), p.getProperty(k));
				if (fields.isEmpty()) continue;
				try { theirs.add(CrewRecord.crew(fields)); } catch (Exception e) { log.warn("Could not rebuild {} from {}: {}", p.getProperty("name"), c.getName(), e.toString()); }
			}
		}
		if (!theirs.isEmpty()) {
			ps.getCrewList().clear();
			for (net.blerf.ftl.parser.SavedGameParser.CrewState c : theirs) if (SaveHelper.placeCrew(ps, c, true)) ps.getCrewList().add(c); // placed, then aboard
		}
		java.util.Properties last = r.sections.containsKey(ShipStore.LAST) ? r.sections.get(ShipStore.LAST) : new java.util.Properties();
		ps.setScrapAmt(Store.num(last, "scrap", ps.getScrapAmt()));
		ps.setFuelAmt(Store.num(last, "fuel", ps.getFuelAmt()));
		ps.setMissilesAmt(Store.num(last, "missiles", ps.getMissilesAmt()));
		ps.setDronePartsAmt(Store.num(last, "drones", ps.getDronePartsAmt()));
		return gs;
	}

	/**
	 * A ship into Sandbox Mode's fleet (6.10), as steps of a protection note (6.87; written by hand before), straight
	 * into its folders (it needn't be open): her folder's files copied (none for a loose save), her record saying Sandbox
	 * and how she came, her save marked for it, her crew's files waiting in crew/arrived/ for its register (as a traded
	 * ship's do). The caller adds her leaving here and commits ({@link #commitSandbox}); the fleet logs her arrival when
	 * it next opens. Returns her folder there.
	 */
	static File toSandbox(Vault v, ActionProtection.Note n, File dir, byte[] save, String name, String id, Ship.State state, String origin, String how) throws IOException {
		File sandbox = Vault.rootOf(v.saves, Vault.SANDBOX);
		File parent = new File(sandbox, state == Ship.State.JUNKED ? "junkyard" : "shipyard");
		String stem = ShipStore.stem(name, id);
		File to = new File(parent, stem);
		if (to.exists() || new File(new File(sandbox, state == Ship.State.JUNKED ? "shipyard" : "junkyard"), stem).exists()) { // her id taken there: a new one
			id = Vault.newId();
			stem = ShipStore.stem(name, id);
			to = new File(parent, stem);
		}
		ShipStore.Record was = dir == null ? null : ShipStore.read(dir);
		if (dir != null) copySteps(n, dir, to, stem);
		ShipStore.Record r = new ShipStore.Record(id);
		if (was != null) { r.dlc = was.dlc; r.marks = was.marks; r.stranger = was.stranger; r.owners.addAll(was.owners); r.pastNames.addAll(was.pastNames); r.sections.putAll(was.sections); }
		else r.dlc = true;
		r.name = name; r.state = state.key; r.career = Vault.SANDBOX;
		r.origin = origin != null && !origin.isEmpty() ? origin : was != null && !was.origin.isEmpty() ? was.origin : originNow("taken_in");
		java.util.Properties m = r.section(ShipMark.SECTION);
		ShipMark.Marked mk = ShipMark.mark(save, Vault.SANDBOX, id, Store.num(m, "boards", 0), Store.num(m, "day", 0));
		n.replace(ShipStore.sav(to), mk.bytes);
		r.hash = SafeFiles.hash(mk.bytes);
		m.setProperty("marked", "true"); m.setProperty("save", r.hash);
		if (mk.gs.getPlayerShipBlueprintId() != null) m.setProperty("class", mk.gs.getPlayerShipBlueprintId());
		java.util.Properties a = r.section(ARRIVAL);
		a.setProperty("from", Vault.title(v.slot)); a.setProperty("career", v.slot); a.setProperty("how", how);
		n.replace(ShipStore.xml(to), ShipStore.bytes(r));
		return to;
	}

	/**
	 * Her folder's files into a note, copied under her folder in the other fleet: named for her new stem; her save and
	 * record left out (written afresh by the caller); her crew's own files into crew/arrived/, for that fleet's register.
	 */
	static void copySteps(ActionProtection.Note n, File dir, File to, String stem) throws IOException {
		File[] fs = dir.listFiles();
		if (fs == null) return;
		String oldStem = dir.getName();
		for (File f : fs) {
			if (f.equals(ShipStore.sav(dir)) || f.equals(ShipStore.xml(dir))) continue;
			String name = f.getName().startsWith(oldStem + ".") ? stem + f.getName().substring(oldStem.length()) : f.getName();
			if (!f.isDirectory()) n.replace(new File(to, name), SafeFiles.read(f));
			else if (!name.equals(CrewRegister.CREW_DIR)) copySteps(n, f, new File(to, name));
			else {
				File arrived = new File(new File(to, CrewRegister.CREW_DIR), CrewRegister.ARRIVED);
				File[] cs = f.listFiles();
				if (cs != null) for (File c : cs) {
					if (c.isDirectory()) copySteps(n, c, arrived); // those already waiting (traded in, not yet taken on)
					else n.replace(new File(arrived, c.getName()), SafeFiles.read(c));
				}
			}
		}
	}

	/** A folder's files into a note, as they are. */
	static void copySteps(ActionProtection.Note n, File from, File to) throws IOException {
		File[] fs = from.listFiles();
		if (fs == null) return;
		for (File f : fs) {
			if (f.isDirectory()) copySteps(n, f, new File(to, f.getName()));
			else n.replace(new File(to, f.getName()), SafeFiles.read(f));
		}
	}

	/** A send to Sandbox Mode's fleet done; if its note failed (and was undone), the empty folders it left there go too. */
	static void commitSandbox(ActionProtection.Note n, File to) throws IOException {
		try { n.commit(); }
		catch (IOException e) { if (OtherFleets.countFiles(to) == 0) SafeFiles.deleteTree(to); throw e; }
	}

	/**
	 * Destroy in Immersive Mode, the other way (heromedel, 6.10): she leaves the career as if destroyed (no reward, no
	 * refund; her fate there "transferred", to Sandbox Mode), and lives on in Sandbox Mode's Junkyard with her crew.
	 */
	static String sendToSandbox(Vault v, Ship s) throws IOException {
		if (Vault.SANDBOX.equals(v.slot)) throw new IOException(Words.She() + " is in Sandbox Mode already");
		if (s.state != Ship.State.JUNKED) throw new IOException(s.name + " isn't in the Junkyard");
		File dir = v.settleFolder(s);
		String from = v.place(s);
		ActionProtection.Note n = ActionProtection.begin(v, "SEND_TO_SANDBOX"); // one note (6.87): her files there, and her leaving here
		File to = toSandbox(v, n, dir, SafeFiles.read(v.fileOf(s)), s.name, s.id, Ship.State.JUNKED, null, "sent_from_career");
		File leftTo = v.leaveSteps(n, s, Vault.Fate.TRANSFERRED, "Sandbox Mode");
		commitSandbox(n, to);
		v.left(s, leftTo);
		v.ships.remove(s);
		v.saveManifest();
		StationLog.entry("SENT TO SANDBOX", s.name + "  " + from + " -> Sandbox Mode's Junkyard", null,
				Vault.shipEvent("SENT_TO_SANDBOX", s).put("from", from).put("to", Vault.rootOf(v.saves, Vault.SANDBOX).getName() + "/junkyard/" + to.getName()).put("career", v.slot));
		return s.name + " was sent to Sandbox Mode's Junkyard, " + Words.her() + " crew with " + Words.herObj() + ". This career counts " + Words.herObj() + " as gone.";
	}
}
