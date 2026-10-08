package homeplanet.vault;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;

import homeplanet.core.Event;
import homeplanet.core.HistoryLog;

/**
 * A Save in the Cargo Bay (heromedel's Plan O, 6.11: moved out of ui/CargoBayUI, which keeps the confirmations, the
 * bill and what's shown). The bay puts every file it changed in one transaction (the ships, the shops bought from,
 * the stored systems, the Dry Dock's bill) and hands it here with what changed: it is written as one protection note,
 * then logged, in this order: the systems' work, the purchases, renamed ships, renamed crew, crew assigned, what was
 * junked, sold or retired, the trade, and the business day.
 */
public final class CargoBaySave {
	private CargoBaySave() { }

	/** Something junked, sold or retired in the bay: its own entry, not a line in the trade. */
	public static final class Disposal {
		/** "JUNK", "SELL" or "RETIRE". */
		final String kind, invKey, line;
		final int scrap, amount;
		/** From the trade partner (else from the ship picked). */
		final boolean fromPartner;
		public Disposal(String kind, String invKey, int scrap, String line, int amount, boolean fromPartner) {
			this.kind = kind; this.invKey = invKey; this.scrap = scrap; this.line = line; this.amount = amount; this.fromPartner = fromPartner;
		}
	}

	/** What a Save changed: the ships as their files had them (read when it's made), and what the bay did. */
	public static final class Changes {
		final Ship current, partner;
		final SavedGameState currentSave, partnerSave;
		final ShipState currentState, partnerState;
		final boolean partnerIsHold;
		/** Their goods, crew and supplies before, as the history log counts them (the bay adds its purchases to them). */
		public final Map<String, Integer> currentBefore, partnerBefore;
		final String nameBefore, partnerNameBefore;
		/** The Dry Dock's work and the purchases, a line each. */
		public final List<String> systems = new ArrayList<String>(), purchases = new ArrayList<String>();
		/** Crew renamed (their record in the save, and the name they had): their own lines, not a "left / joined" pair. */
		public Map<CrewState, String> crewRenames = new HashMap<CrewState, String>();
		public final List<Disposal> disposals = new ArrayList<Disposal>();

		/**
		 * The ship picked (null for the Cargo Hold alone) and the partner (null for none), each with the save the bay
		 * changed and its player ship (her crew).
		 */
		public Changes(Ship current, SavedGameState currentSave, ShipState currentState, Ship partner, SavedGameState partnerSave, ShipState partnerState, boolean partnerIsHold) {
			this.current = current; this.currentSave = currentSave; this.currentState = currentState;
			this.partner = partner; this.partnerSave = partnerSave; this.partnerState = partnerState; this.partnerIsHold = partnerIsHold;
			Map<String, Integer> cb = null, pb = null;
			String nb = null, pnb = null;
			try {
				// the ships as their files have them: the vault's own parse (a fresh read could fail after a remodel changed her layout)
				SavedGameState onDisk = current == null ? null : current.save();
				if (onDisk != null) { nb = onDisk.getPlayerShipName(); cb = HistoryLog.inventory(onDisk); }
				SavedGameState partnerDisk = partner != null ? partner.save() : null;
				if (partnerDisk != null) { pnb = partnerDisk.getPlayerShipName(); pb = HistoryLog.inventory(partnerDisk); }
			} catch (Exception e) {
				log.warn("Could not read saves for the history log", e);
			}
			currentBefore = cb; partnerBefore = pb; nameBefore = nb; partnerNameBefore = pnb;
		}
	}
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CargoBaySave.class);

	/**
	 * Writes the bay's transaction as one protection note, then {@code paid} (what the save spent beyond its files: the
	 * Dry Dock's reputation), then the log entries. Throws, with nothing written, if the transaction can't be.
	 */
	public static void save(Vault v, Vault.Transaction tx, Changes c, Runnable paid) throws IOException {
		tx.commit();
		if (paid != null) paid.run();
		// real business at the station passes a day (heromedel, 5.17): buying, selling, the Dry Dock's work, a system in
		// or out; never moving your own things about. Not twice running: a Save after a Cargo Bay day, with nothing else
		// moving the clock between, passes none (selling one missile at a time can't run the clock)
		boolean business = !c.systems.isEmpty() || !c.purchases.isEmpty();
		for (Disposal dp : c.disposals) if ("SELL".equals(dp.kind)) business = true;
		if (!c.systems.isEmpty())
			HistoryLog.entry("SYSTEMS", c.currentSave.getPlayerShipName(), new ArrayList<String>(c.systems),
					Event.of("SYSTEMS").put("ship_name", c.currentSave.getPlayerShipName()).put("ship_id", c.current == null ? null : c.current.id).details(new ArrayList<String>(c.systems)));
		for (String s : c.systems) if (s.startsWith("Installed ")) { homeplanet.parser.ThirdFleet.partInstalled(v); break; } // the technicians tell the Third Fleet Commander
		if (!c.purchases.isEmpty())
			HistoryLog.entry("BUY", c.purchases.size() == 1 ? "1 purchase" : c.purchases.size() + " purchases",
					new ArrayList<String>(c.purchases), Event.of("BUY").put("what", "cargo_bay").put("purchases", c.purchases.size()).details(new ArrayList<String>(c.purchases)));
		if (c.current != null && c.nameBefore != null && !c.nameBefore.equals(c.currentSave.getPlayerShipName()))
			HistoryLog.entry("RENAME", c.nameBefore + " -> " + c.currentSave.getPlayerShipName() + "  (" + c.current.id + ")", null,
					Vault.shipEvent("RENAME", c.current).put("from", c.nameBefore).put("to", c.currentSave.getPlayerShipName()));
		if (c.partnerNameBefore != null && !c.partnerIsHold && !c.partnerNameBefore.equals(c.partnerSave.getPlayerShipName()))
			HistoryLog.entry("RENAME", c.partnerNameBefore + " -> " + c.partnerSave.getPlayerShipName() + "  (" + c.partner.id + ")", null,
					Vault.shipEvent("RENAME", c.partner).put("from", c.partnerNameBefore).put("to", c.partnerSave.getPlayerShipName()));
		Map<String, Integer> curBefore = c.currentBefore, partnerBefore = c.partnerBefore;
		// crew renames get their own lines, not a "left / joined" pair in the trade
		for (Map.Entry<CrewState, String> r : c.crewRenames.entrySet()) {
			String oldN = r.getValue(), newN = r.getKey().getName();
			if (oldN.equals(newN)) continue;
			String ship = c.currentState != null && c.currentState.getCrewList().contains(r.getKey()) ? c.currentSave.getPlayerShipName() : (c.partnerSave != null ? c.partnerSave.getPlayerShipName() : "");
			for (Map<String, Integer> m : java.util.Arrays.asList(curBefore, partnerBefore)) {
				if (m != null && m.containsKey("Crew " + oldN)) {
					int n = m.remove("Crew " + oldN);
					m.put("Crew " + newN, (m.containsKey("Crew " + newN) ? m.get("Crew " + newN) : 0) + n);
				}
			}
			HistoryLog.entry("RENAME CREW", oldN + " -> " + newN + "  (" + ship + ")", null, Event.of("RENAME_CREW").put("what", "renamed").put("from", oldN).put("to", newN).put("ship_name", ship));
		}
		// crew who came aboard a ship or into the Cargo Hold: "Lisandra assigned to the Kestrel." (their arrival at the
		// station's medbay counts from here)
		assigned(c.current != null ? c.currentState : null, curBefore, c.currentSave, false);
		if (c.partner != null) assigned(c.partnerState, partnerBefore, c.partnerSave, c.partnerIsHold);
		// junked, sold and retired get entries of their own, not lines in the trade
		Map<String, List<String>> byKind = new LinkedHashMap<String, List<String>>();
		Map<String, Integer> countByKind = new LinkedHashMap<String, Integer>();
		int sellTotal = 0;
		for (Disposal dp : c.disposals) {
			Map<String, Integer> m = dp.fromPartner ? partnerBefore : curBefore;
			if (m != null) {
				Integer n = m.get(dp.invKey);
				if (n != null) { if (n <= dp.amount) m.remove(dp.invKey); else m.put(dp.invKey, n - dp.amount); }
				if (dp.scrap > 0) m.put("Scrap", (m.containsKey("Scrap") ? m.get("Scrap") : 0) + dp.scrap);
			}
			sellTotal += dp.scrap;
			if (!byKind.containsKey(dp.kind)) byKind.put(dp.kind, new ArrayList<String>());
			byKind.get(dp.kind).add(dp.line);
			countByKind.put(dp.kind, (countByKind.containsKey(dp.kind) ? countByKind.get(dp.kind) : 0) + dp.amount);
		}
		for (Map.Entry<String, List<String>> k : byKind.entrySet()) {
			int n = countByKind.get(k.getKey());
			String head = k.getKey().equals("RETIRE") ? (n == 1 ? "1 crew member" : n + " crew members") : (n == 1 ? "1 item" : n + " items");
			if (k.getKey().equals("SELL")) head += " for " + sellTotal + " scrap";
			HistoryLog.entry(k.getKey(), head, k.getValue(),
					Event.of(k.getKey()).put("what", "cargo_bay").put("count", n).put("scrap", k.getKey().equals("SELL") ? String.valueOf(sellTotal) : null).details(k.getValue()));
		}
		List<String> lines = new ArrayList<String>();
		if (curBefore != null) {
			List<String> ch = HistoryLog.changes(curBefore, HistoryLog.inventory(c.currentSave));
			if (!ch.isEmpty()) { lines.add(c.currentSave.getPlayerShipName() + ":"); for (String l : ch) lines.add("  " + l); }
		}
		if (partnerBefore != null) {
			List<String> ch = HistoryLog.changes(partnerBefore, HistoryLog.inventory(c.partnerSave));
			if (!ch.isEmpty()) { lines.add(c.partnerSave.getPlayerShipName() + ":"); for (String l : ch) lines.add("  " + l); }
		}
		if (!lines.isEmpty())
			HistoryLog.entry("TRADE", c.current == null ? c.partnerSave.getPlayerShipName() : c.currentSave.getPlayerShipName() + (c.partnerSave != null ? " <-> " + c.partnerSave.getPlayerShipName() : ""), lines,
					Event.of("TRADE").put("what", "cargo_bay").put("ship_name", c.current == null ? null : c.currentSave.getPlayerShipName()).put("ship_id", c.current == null ? null : c.current.id)
							.put("partner_name", c.partnerSave == null ? null : c.partnerSave.getPlayerShipName()).put("partner_id", c.partner == null ? null : c.partner.id).details(lines));
		if (business) MasterLog.businessDay(v); // after its entries: they belong to the day the business ended
	}

	/** Crew who came aboard her (or into the Cargo Hold) since her file was read: a CREW entry each. */
	private static void assigned(ShipState now, Map<String, Integer> before, SavedGameState save, boolean hold) {
		if (now == null || before == null) return;
		Map<String, Integer> seen = new HashMap<String, Integer>();
		for (CrewState c : homeplanet.parser.SaveHelper.getOwnCrew(now)) {
			int n = seen.containsKey(c.getName()) ? seen.get(c.getName()) + 1 : 1;
			seen.put(c.getName(), n);
			Integer had = before.get("Crew " + c.getName());
			if (had != null && n <= had) continue;
			String ship = save.getPlayerShipName();
			String place = hold ? "the Cargo Hold" : homeplanet.parser.ShipNames.the(ship);
			HistoryLog.entry("CREW", c.getName() + " assigned to " + place + ".", null,
					Event.of("CREW").put("what", "assigned").put("crew", c.getName()).put("race", c.getRace() == null ? null : c.getRace().getId()).put("to", hold ? "hold" : "ship").put("ship_name", hold ? null : ship));
		}
	}
}
