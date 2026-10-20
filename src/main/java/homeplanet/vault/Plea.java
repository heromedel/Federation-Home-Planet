package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import homeplanet.core.Event;
import homeplanet.core.StationLog;
import homeplanet.core.SafeFiles;
import homeplanet.model.Words;
import homeplanet.parser.SaveHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The free command (HR2; 7.02, out of Vault): once when the fleet starts, and again with each plea for a new ship; the hold's scrap and what it pays. */
final class Plea {
	private Plea() { }
	private static final Logger log = LoggerFactory.getLogger(Plea.class);

	static File freeCommandFile(Vault v) { return new File(v.root, "free-command.txt"); }

	/**
	 * Is a free command granted and not yet taken? An empty shipyard alone never grants one: the captain commissions a
	 * ship with scrap, or pleads for a new one. (A fleet from before this was recorded: granted if its shipyard is
	 * empty now, once.)
	 */
	static boolean freeCommandOpen(Vault v) {
		File f = freeCommandFile(v);
		if (!f.isFile()) {
			boolean open = v.shipyardEmpty();
			setFreeCommand(v, open, "recorded: " + (open ? "the shipyard is empty" : "the fleet has a ship"));
			return open;
		}
		try { return new String(SafeFiles.read(f), java.nio.charset.StandardCharsets.UTF_8).trim().startsWith("open"); }
		catch (IOException e) { return false; }
	}

	/** Grants the free command (a new fleet or career); its ship is Settings' or the career's. */
	static void grantFreeCommand(Vault v, String why) { setFreeCommand(v, true, why, null); }

	/** Grants the free command with the ship it brings ({@link homeplanet.parser.FreeCommand}: an Immersive career). */
	static void grantFreeCommand(Vault v, String why, String ship) { setFreeCommand(v, true, why, ship); }

	/** The ship the open free command names, or null: the plea's or Settings' (see FreeCommand). */
	static String freeCommandShip(Vault v) { return freeCommandValue(v, "ship "); }

	/** Why the open free command was granted. */
	static final String PLEA = "pleaded for a new ship";

	/** Was the open free command granted by a plea for a new ship (rather than a new fleet or career)? */
	static boolean freeCommandReassigned(Vault v) { String[] l = freeCommandLines(v); return l.length > 1 && l[0].startsWith("open") && l[1].startsWith(PLEA); }

	/** A line of the open free command that starts with this, the rest of it; or null. */
	static String freeCommandValue(Vault v, String key) {
		String[] l = freeCommandLines(v);
		if (l.length == 0 || !l[0].startsWith("open")) return null;
		for (int i = 2; i < l.length; i++) if (l[i].startsWith(key)) return l[i].substring(key.length()).trim();
		return null;
	}

	static String[] freeCommandLines(Vault v) {
		try { return new String(SafeFiles.read(freeCommandFile(v)), java.nio.charset.StandardCharsets.UTF_8).trim().split("\\r?\\n"); }
		catch (IOException e) { return new String[0]; }
	}

	/** The free command is taken (her commission), or taken back (an undone report). */
	static void useFreeCommand(Vault v, String why) { setFreeCommand(v, false, why, null); }

	static void setFreeCommand(Vault v, boolean open, String why) { setFreeCommand(v, open, why, null); }

	static void setFreeCommand(Vault v, boolean open, String why, String ship) { setFreeCommand(v, open, why, ship, null); }

	static void setFreeCommand(Vault v, boolean open, String why, String ship, String more) {
		try { SafeFiles.writeText(freeCommandFile(v), (open ? "open" : "used") + "\n" + why + "\n" + (ship == null ? "" : "ship " + ship + "\n") + (more == null ? "" : more + "\n"), false); }
		catch (IOException e) { log.warn("Could not record the free command: {}", e.toString()); }
	}

	/** The scrap in the storage hold (0 if it can't be read). */
	static int storageScrap(Vault v) {
		try {
			SavedGameState g = v.storage().save();
			return g == null ? 0 : g.getPlayerShip().getScrapAmt();
		} catch (IOException e) {
			return 0;
		}
	}

	/**
	 * Pays scrap from the storage hold (a commission, a journey's fee). Refuses, changing nothing, if the hold has less.
	 * Returns the hold's file as it was. Paying for something written as well: {@link Transaction#pay}, in the same note.
	 */
	static byte[] payFromStorage(Vault v, int scrap) throws IOException {
		log.debug("Cargo Hold pays {} scrap", scrap);
		Ship st = v.storage();
		Vault.Copy c = v.readCopy(st);
		int have = c.save.getPlayerShip().getScrapAmt();
		if (have < scrap) throw new IOException("The Cargo Hold has " + have + " scrap; " + scrap + " is needed");
		byte[] before = SafeFiles.read(v.fileOf(st));
		c.save.getPlayerShip().setScrapAmt(have - scrap);
		v.begin().put(st, c.save, c.hash).commit();
		return before;
	}

	/**
	 * Plead for New Ship: The Federation Home Planet agrees to send a new ship. Nothing is taken yet: at Commission the
	 * captain chooses her, and whether the Cargo Hold pays for her ({@link #forfeitHold}) or the career's reputation does.
	 */
	static void plead(Vault v) {
		StationLog.entry("PLEAD", "The Federation Home Planet agreed to send a new ship: " + Words.her() + " order waits at Commission", null, Event.of("PLEAD").put("stage", "agreed"));
		grantFreeCommand(v, PLEA);
	}

	/**
	 * The plea's ship is paid for with the Cargo Hold: everything in it (scrap, supplies, items, stored systems) goes to
	 * The Federation Home Planet, and the hold starts again with the refund (the difference, if the captain asked for it).
	 * The crew in it stay (they aren't counted, and aren't given up); the Junkyard is untouched. Written with this
	 * transaction (6.11: the new ship's, so the hold is given only if she comes; put back by hand before), logged once it stands.
	 */
	static void forfeitHold(Vault v, final int saleValue, final int refund, Vault.Transaction tx) throws IOException {
		Ship st = v.storage();
		v.snapshot(st);
		Vault.Copy was = v.readCopy(st);
		SavedGameState fresh = SaveHelper.createStorageSave(st.name, true);
		fresh.getPlayerShip().setScrapAmt(Math.max(0, refund));
		fresh.getPlayerShip().getCrewList().addAll(was.save.getPlayerShip().getCrewList()); // the crew stay
		tx.put(st, fresh, was.hash);
		tx.delete(v.systemsFile());
		tx.then(new Runnable() { public void run() {
			StationLog.entry("PLEAD", "the Cargo Hold given for the new ship (worth " + saleValue + " scrap at sale)" + (refund > 0 ? "; " + refund + " scrap refunded to it" : ""), null,
					Event.of("PLEAD").put("stage", "hold_given").put("value", saleValue).put("refund", refund));
		} });
	}

	/** Withdraws a waiting plea (nothing was taken: her order is simply cancelled). */
	static void withdrawPlea(Vault v) throws IOException {
		if (!freeCommandOpen(v) || !freeCommandReassigned(v)) throw new IOException("There is no plea waiting: the new ship has been commissioned since");
		StationLog.entry("UNDO PLEA", "the plea for a new ship was withdrawn", null, Event.of("UNDO_PLEA"));
		useFreeCommand(v, "the plea was withdrawn");
	}
}
