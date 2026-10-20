package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import homeplanet.core.Event;
import homeplanet.core.StationLog;
import homeplanet.core.SafeFiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The other fleets (7.02, out of Vault): switching between Sandbox Mode and the Immersive careers, ending a career, sending a ship to the other fleet, and the boarded ship handed over with the switch. */
final class OtherFleets {
	private OtherFleets() { }
	private static final Logger log = LoggerFactory.getLogger(OtherFleets.class);

	/** In a fleet's folder: the ship that was boarded when the player switched to the other fleet, boarded again on return. */
	static final String PARKED = "parked-boarded.txt";

	/**
	 * Switches to the other fleet: the boarded ship is docked into this one (and noted, to be boarded again on
	 * return), then the other fleet's vault is opened and its noted ship boarded. FTL must be closed (the caller
	 * checks). Returns the vault now in use. On a failure before the other fleet opens, nothing has changed but a dock.
	 */
	static Vault switchFleet(boolean toImmersive) throws IOException {
		return switchFleet(toImmersive ? Vault.immersiveSlot : Vault.SANDBOX);
	}

	/** As {@link #switchFleet(boolean)}, to this mode's fleet. */
	static Vault switchFleet(String toSlot) throws IOException {
		Vault from = Vault.get();
		if (from.slot.equals(toSlot)) return from;
		homeplanet.core.StationConsole.lock(); // another career: dev commands never carried across (heromedel, 6.35)
		from.reload();
		Ship b = from.boarded();
		File park = new File(from.root, PARKED);
		if (b != null) {
			from.dock();
			SafeFiles.writeText(park, b.id + "\n", false);
		} else if (park.isFile() && !park.delete()) {
			log.warn("Could not remove {}", park);
		}
		StationLog.entry("SWITCH FLEET", "to the " + Vault.title(toSlot) + " fleet" + (b == null ? "" : "; " + b.name + " docked here, to be boarded again on return"), null,
				Event.of("SWITCH_FLEET").put("stage", "leaving").put("to_fleet", Vault.title(toSlot)).put("parked", b == null ? null : b.name + "." + b.id));
		Vault to = Vault.open(from.saves, toSlot);
		File back = new File(to.root, PARKED);
		if (back.isFile()) {
			String id = new String(SafeFiles.read(back), java.nio.charset.StandardCharsets.UTF_8).trim();
			Ship s = to.byId(id);
			if (s != null && s.state == Ship.State.DOCKED && !to.continueFile().exists()) to.board(s);
			if (!back.delete()) log.warn("Could not remove {}", back);
		}
		StationLog.entry("SWITCH FLEET", "now the " + Vault.title(toSlot) + " fleet" + (to.boarded() == null ? "" : "; " + to.boarded().name + " boarded again"), null,
				Event.of("SWITCH_FLEET").put("stage", "arrived").put("to_fleet", Vault.title(toSlot)).put("boarded", to.boarded() == null ? null : to.boarded().name + "." + to.boarded().id));
		return to;
	}

	/** Where ended Immersive careers are kept, zipped (in the normal fleet's folder). */

	/**
	 * Ends the Immersive career (from the normal fleet, Immersive Mode already left): its whole folder (ships, storage,
	 * records, its own FTL profile) is zipped into old-immersive-careers/, checked, and only then deleted. Returns the zip.
	 */
	static File endImmersiveCareer() throws IOException {
		return endCareer(Vault.immersiveSlot);
	}

	/** Ends this Immersive career, as {@link #endImmersiveCareer()}; its fleet mustn't be the one in use. */
	static File endCareer(String slot) throws IOException {
		Vault v = Vault.get();
		if (Vault.SANDBOX.equals(slot)) throw new IOException("Sandbox Mode has no career to end");
		if (v.slot.equals(slot)) throw new IOException("Switch to another mode first");
		File im = Vault.rootOf(v.saves, slot);
		if (!im.isDirectory()) throw new IOException("There is no " + Vault.title(slot) + " career to end");
		String stamp;
		synchronized (ShipStore.STAMP) { stamp = ShipStore.STAMP.format(new Date()); }
		File zip = new File(new File(v.shared, Vault.OLD_CAREERS), Vault.title(slot) + " career " + stamp + ".zip");
		SafeFiles.zipFolder(im, zip, null);
		int files = countFiles(im), zipped;
		java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip);
		try { zipped = 0; for (java.util.Enumeration<? extends java.util.zip.ZipEntry> en = z.entries(); en.hasMoreElements(); ) if (!en.nextElement().isDirectory()) zipped++; } finally { z.close(); } // its files (its folders are entries too, 5.94)
		if (zipped != files) throw new IOException("The copy in " + zip + " is incomplete (" + zipped + " of " + files + " files): nothing was deleted");
		if (!SafeFiles.deleteTree(im))
			throw new IOException("Some of " + im + " could not be deleted (a file in use?). The whole career is kept in " + zip
					+ "; delete the folder by hand once The Home Planet Station is closed");
		StationLog.entry("CAREER ENDED", "the " + Vault.title(slot) + " career was ended; a copy is kept in " + Vault.OLD_CAREERS + "/" + zip.getName(), null,
				Event.of("CAREER_ENDED").put("fleet", Vault.title(slot)).put("copy", Vault.OLD_CAREERS + "/" + zip.getName()).put("files", files));
		return zip;
	}

	static int countFiles(File dir) {
		File[] fs = dir.listFiles();
		int n = 0;
		if (fs != null) for (File f : fs) n += f.isDirectory() ? countFiles(f) : f.isFile() ? 1 : 0;
		return n;
	}

	static void sendToOtherFleet(Vault v, Ship s, boolean junkyard) throws IOException {
		File other = v.otherRoot();
		if (v.immersive) { // a ship of her own there, never a loose save (6.10: loose saves wait for the player's word)
			String from = s.isBoarded() ? "continue.sav" : v.place(s);
			File her = v.fileOf(s), dir = v.settleFolder(s), memorial = new File(v.memorialDir(), dir.getName());
			boolean move = !dir.getParentFile().getAbsoluteFile().equals(v.memorialDir().getAbsoluteFile());
			ActionProtection.Note n = ActionProtection.begin(v, "SEND_TO_SANDBOX"); // one note (6.87): her files there, her save gone here, her folder to the memorial
			File to = Strays.toSandbox(v, n, dir, SafeFiles.read(her), s.name == null || s.name.trim().isEmpty() ? "Unknown ship" : s.name.trim(), s.id,
					junkyard ? Ship.State.JUNKED : Ship.State.DOCKED, Strays.originNow("adopted"), "uncommissioned");
			n.delete(her);
			if (move) n.rename(dir, memorial);
			Strays.commitSandbox(n, to);
			if (move) v.folders.put(s.id, memorial);
			v.ships.remove(s);
			v.saveManifest();
			StationLog.entry("SENT", s.name + "  " + from + " -> the Sandbox fleet's " + (junkyard ? "Junkyard" : "Space Dock"), null,
					Vault.shipEvent("SENT", s).put("from", from).put("to_fleet", "Sandbox").put("to", to.getParentFile().getName() + "/" + to.getName()));
			return;
		}
		File dir = new File(other, junkyard ? "junkyard" : "shipyard");
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		String stem = (s.name == null || s.name.trim().isEmpty() ? "Unknown ship" : s.name.trim()).replaceAll("[\\\\/:*?\"<>|]", "_");
		File to = new File(dir, stem + ".sav");
		for (int i = 2; to.exists(); i++) to = new File(dir, stem + " " + i + ".sav");
		String from = s.isBoarded() ? "continue.sav" : v.place(s);
		SafeFiles.move(v.fileOf(s), to);
		v.ships.remove(s);
		v.toMemorial(s); // her folder stays here, as a record (a boarded one's too: her record must not read as boarded next time)
		v.saveManifest();
		StationLog.entry("SENT", s.name + "  " + from + " -> the "
				+ (v.immersive ? "Sandbox" : Vault.title(Vault.immersiveSlot)) + " fleet's " + (junkyard ? "Junkyard" : "Space Dock"), null,
				Vault.shipEvent("SENT", s).put("from", from).put("to_fleet", v.immersive ? "Sandbox" : Vault.title(Vault.immersiveSlot)).put("to", dir.getName() + "/" + to.getName()));
	}

	/**
	 * The player takes this boarded ship to the other fleet and switches to it (an uncommissioned ship in Immersive
	 * Mode, flown in the normal fleet instead): she leaves this fleet's records, continue.sav stays, and the other
	 * fleet opens with her boarded. Its own parked ship stays docked. Returns the vault now in use.
	 */
	static Vault handOverBoarded(Ship s) throws IOException {
		Vault from = Vault.get();
		if (s.state != Ship.State.BOARDED || !from.ships.contains(s)) throw new IOException(s.name + " isn't the boarded ship");
		synchronized (from) {
			from.ships.remove(s);
			from.toMemorial(s); // her folder stays as a record; not read as boarded next time (continue.sav is the other fleet's now)
			from.saveManifest();
		}
		StationLog.entry("HANDED OVER", s.name + " (continue.sav) to the " + (from.immersive ? "Sandbox" : Vault.title(Vault.immersiveSlot)) + " fleet, now in use", null,
				Vault.shipEvent("HANDED_OVER", s).put("file", "continue.sav").put("to_fleet", from.immersive ? "Sandbox" : Vault.title(Vault.immersiveSlot)));
		Vault to = Vault.open(from.saves, from.immersive ? Vault.SANDBOX : Vault.immersiveSlot);
		File park = new File(to.root, PARKED);
		if (park.isFile() && !park.delete()) log.warn("Could not remove {}", park);
		Ship b = to.boarded();
		if (b != null) b.stranger = false; // she's this fleet's now
		to.saveManifest();
		return to;
	}

	/** True if any ship's save couldn't even be scanned (so "unused" can't be trusted). */
	static boolean anyUnscannable(Vault v) {
		for (Ship s : v.ships) {
			if (s.state == Ship.State.STORAGE) continue;
			if (homeplanet.parser.Retrofit.blueprintIds(v.fileOf(s)) == null) return true;
		}
		return false;
	}
}
