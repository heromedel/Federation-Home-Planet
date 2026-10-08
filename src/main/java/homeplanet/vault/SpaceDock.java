package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.util.List;

import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;

import homeplanet.core.Event;
import homeplanet.core.HistoryLog;
import homeplanet.core.SafeFiles;
import homeplanet.model.Items;
import homeplanet.parser.SaveHelper;

/**
 * The Space Dock's business, written (heromedel's Plan O, 6.11: moved out of ui/SpaceDockUI, which keeps the
 * questions, the prices and the messages): a ship renamed, a new journey plotted, a hull scrapped or sold. Each writes
 * its files and its log entries as the Space Dock did; each throws, with nothing changed, if it can't be done.
 */
public final class SpaceDock {
	private SpaceDock() { }

	/** Renames her: her save written under the new name, then logged. If it can't be written her save keeps the old name. */
	public static void rename(Vault v, Ship ship, SavedGameState sgs, String newName) throws IOException {
		String oldName = sgs.getPlayerShipName();
		sgs.setPlayerShipName(newName);
		sgs.getPlayerShip().setShipName(newName);
		try {
			v.write(ship, sgs);
		} catch (IOException e) {
			sgs.setPlayerShipName(oldName);
			sgs.getPlayerShip().setShipName(oldName);
			throw e;
		}
		HistoryLog.entry("RENAME", oldName + " -> " + newName + "  (" + ship.id + ")", null, Vault.shipEvent("RENAME", ship).put("from", oldName).put("to", newName));
	}

	/**
	 * A new journey for the boarded ship, her save already set out ({@code gs}): the fee's scrap from the Cargo Hold and
	 * her save together, as one protection note (6.11: paid, then refunded by hand if her save failed, before), then
	 * set out from The Home Planet Station, logged, and the fee's reputation spent.
	 *
	 * @param difficulty the difficulty as the player chose it ("Easy", "Normal", "Hard")
	 * @param fee        the whole fee (0 for none); {@code scrap} and {@code rep} what of it is paid in each
	 * @param paidWords  the fee in words for the log ("20 scrap"), or null for none
	 */
	public static void newJourney(Vault v, Ship ship, SavedGameState gs, String difficulty, int fee, int scrap, int rep, String paidWords) throws IOException {
		try {
			Vault.Transaction tx = v.begin();
			if (scrap > 0) {
				Ship st = v.storage();
				Vault.Copy c = v.readCopy(st);
				int have = c.save.getPlayerShip().getScrapAmt();
				if (have < scrap) throw new IOException("The Cargo Hold has " + have + " scrap; " + scrap + " is needed");
				c.save.getPlayerShip().setScrapAmt(have - scrap);
				tx.put(st, c.save, c.hash);
			}
			tx.put(ship, gs);
			tx.commit();
			v.setOut(ship, gs, VoyageLog.NEW_JOURNEY); // at The Home Planet Station until she jumps
		} catch (IOException e) {
			ship.invalidate();
			throw e;
		}
		HistoryLog.entry("NEW JOURNEY", gs.getPlayerShipName() + "  difficulty " + difficulty + (fee > 0 ? ", fee " + paidWords + (scrap > 0 ? " (the scrap from the Cargo Hold)" : "") : ""), null,
				Vault.shipEvent("NEW_JOURNEY", ship).put("difficulty", difficulty).put("fee", fee).put("paid", fee > 0 ? paidWords : null));
		if (rep > 0) Reputation.spend(v, rep, "A new journey plotted for " + gs.getPlayerShipName()); // once her journey is saved
	}

	/**
	 * Scraps a junked ship: everything aboard to the Cargo Hold (her systems too, when stripped: their lines for the
	 * stored systems and for the log, from the Refit tab's rules), the stripping paid, the hull broken up; then logged
	 * and the stripping's reputation spent.
	 *
	 * @param systems      the stored systems' lines for her stripped systems, or null if she isn't stripped
	 * @param systemsSaid  the log's lines for them
	 */
	public static void scrap(Vault v, Ship wreckShip, SavedGameState wreck, List<String> systems, List<String> systemsSaid, int scrapPaid, int repPaid) throws IOException {
		String name = wreckShip.name;
		boolean strip = systems != null;
		List<String> scrapped;
		Ship storageShip = v.storage();
		// a fresh copy: the shared one must not keep the additions if anything below fails
		Vault.Copy storageCopy;
		try { storageCopy = v.readCopy(storageShip); } catch (IOException e) { throw new IOException("The Cargo Hold can't be read: " + e.getMessage()); }
		SavedGameState storage = storageCopy.save;
		// what the hold and the stored-systems list hold now, to put back if the wreck can't be removed after them
		File storageFile = storageShip.file(), systemsFile = v.systemsFile();
		byte[] storageBefore = SafeFiles.read(storageFile), systemsBefore = systemsFile.isFile() ? SafeFiles.read(systemsFile) : null;
		scrapped = HistoryLog.changes(new java.util.HashMap<String, Integer>(), HistoryLog.inventory(wreck));
		ShipState from = wreck.getPlayerShip();
		ShipState to = storage.getPlayerShip();
		to.setScrapAmt(to.getScrapAmt() + from.getScrapAmt());
		to.setFuelAmt(to.getFuelAmt() + from.getFuelAmt());
		to.setMissilesAmt(to.getMissilesAmt() + from.getMissilesAmt());
		to.setDronePartsAmt(to.getDronePartsAmt() + from.getDronePartsAmt());
		for (WeaponState w : from.getWeaponList()) to.getWeaponList().add(SaveHelper.newIdleWeapon(w.getWeaponId()));
		for (DroneState d : from.getDroneList()) to.getDroneList().add(SaveHelper.copyDroneForTransfer(d));
		to.getAugmentIdList().addAll(from.getAugmentIdList());
		// Storage keeps cargo sorted by kind
		for (String id : SaveHelper.cargo(wreck)) { // not the augment FTL was asking about: left behind (5.52)
			if (Items.isWeapon(id)) to.getWeaponList().add(SaveHelper.newIdleWeapon(id));
			else if (Items.isDrone(id)) to.getDroneList().add(SaveHelper.newIdleDrone(id));
			else if (Items.isAugment(id)) to.getAugmentIdList().add(id);
			else storage.getCargoIdList().add(id);
		}
		for (CrewState c : SaveHelper.getOwnCrew(from)) {
			if (SaveHelper.hasBody(c) && SaveHelper.placeCrew(to, c, true)) to.getCrewList().add(c);
		}
		Vault.Transaction tx = v.begin().put(storageShip, storage, storageCopy.hash);
		if (strip) {
			StoredSystems.add(tx, v, systems);
			scrapped.addAll(systemsSaid);
			if (scrapPaid > 0) {
				if (to.getScrapAmt() < scrapPaid) throw new IOException("the Cargo Hold holds " + to.getScrapAmt() + " scrap, short of the " + scrapPaid + " stripping costs");
				to.setScrapAmt(to.getScrapAmt() - scrapPaid);
				scrapped.add("- " + scrapPaid + " scrap (stripping her systems)");
			}
			if (repPaid > 0) scrapped.add("- " + repPaid + " reputation (stripping her systems)");
		}
		tx.commit();
		try {
			v.remove(wreckShip, null); // logged below, with what came off her
		} catch (IOException e) {
			// she's still in the Junkyard with everything aboard: the hold must not keep a second copy
			SafeFiles.write(storageFile, storageBefore);
			if (systemsBefore != null) SafeFiles.write(systemsFile, systemsBefore); else systemsFile.delete();
			storageShip.invalidate();
			throw e;
		}
		HistoryLog.entry("SCRAP", name + " stripped into storage, hull broken up", scrapped, Event.of("SCRAP").put("ship_name", name).put("stripped", strip).put("to", "hold").details(scrapped));
		if (strip && repPaid > 0) Reputation.spend(v, repPaid, "Stripping " + name + "'s systems when she was scrapped");
	}

	/** Sells a junked ship (traded in, or at auction, for {@code price}): her scrap and crew to the Cargo Hold with the payment; she leaves the fleet; logged. */
	public static void sell(Vault v, Ship ship, SavedGameState gs, int price, boolean auction) throws IOException {
		String name = ship.name;
		ShipState from = gs.getPlayerShip();
		Ship storageShip = v.storage();
		Vault.Copy storageCopy;
		try { storageCopy = v.readCopy(storageShip); } catch (IOException e) { throw new IOException("The Cargo Hold can't be read: " + e.getMessage()); }
		SavedGameState storage = storageCopy.save;
		File storageFile = storageShip.file();
		byte[] storageBefore = SafeFiles.read(storageFile);
		ShipState to = storage.getPlayerShip();
		to.setScrapAmt(to.getScrapAmt() + from.getScrapAmt() + price);
		for (CrewState c : SaveHelper.getOwnCrew(from)) {
			if (SaveHelper.hasBody(c) && SaveHelper.placeCrew(to, c, true)) to.getCrewList().add(c);
		}
		v.begin().put(storageShip, storage, storageCopy.hash).commit();
		try {
			v.remove(ship, null, Vault.Fate.SOLD);
		} catch (IOException e) {
			SafeFiles.write(storageFile, storageBefore); // she's still in the Junkyard: the hold mustn't keep her scrap and crew too
			storageShip.invalidate();
			throw e;
		}
		HistoryLog.entry("SELL", name + (auction ? " sold at auction" : " traded in") + " for " + price + " scrap; her scrap and crew to the Cargo Hold", null,
				Event.of("SELL").put("ship_name", name).put("how", auction ? "auction" : "trade_in").put("price", price).put("to", "hold"));
	}
}
