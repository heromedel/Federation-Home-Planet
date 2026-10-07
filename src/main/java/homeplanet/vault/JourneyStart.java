package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.Store;

/**
 * Where her current journey began (history/&lt;id&gt;/journey.txt): FTL's running totals at that moment (they carry on
 * across a New Journey), so her report can tell this journey from her whole service; and the furthest sector any of
 * her journeys reached. Written each time the station sets her out: commissioned, a New Journey, received, rescued.
 */
public final class JourneyStart {
	private static final Logger log = LoggerFactory.getLogger(JourneyStart.class);
	private JourneyStart() {}

	static final String FILE = "journey.txt";
	/** FTL's own counts kept from the journey's start (each only when her save has it). */
	public static final String[] VARS = {"killed_crew", "lost_crew", "fired_shot", "used_missile"};

	/** Her journey begins now: the totals as they stand, and the last journey's furthest sector kept as her best. */
	static void begin(Vault v, Ship s, SavedGameState gs, int lastSector) {
		if (s == null || gs == null || s.state == Ship.State.STORAGE) return;
		Properties p = read(v, s.id);
		int best = Math.max(Store.num(p, "best", 0), lastSector + 1);
		p.clear();
		if (best > 0) p.setProperty("best", Integer.toString(best));
		p.setProperty("defeated", Integer.toString(gs.getTotalShipsDefeated()));
		p.setProperty("beacons", Integer.toString(gs.getTotalBeaconsExplored()));
		p.setProperty("scrap", Integer.toString(gs.getTotalScrapCollected()));
		p.setProperty("hired", Integer.toString(gs.getTotalCrewHired()));
		for (String k : VARS) if (gs.hasStateVar(k)) p.setProperty(k, Integer.toString(gs.getStateVar(k)));
		try {
			Store.write(new File(v.historyOf(s), FILE), p, "Where her current journey began: Federation Home Planet rewrites this file");
		} catch (IOException e) {
			log.warn("Could not note where {}'s journey began: {}", s.name, e.toString());
		}
	}
	/** Her journey's start, or empty if the station hasn't seen one begin (a ship from before 4B.75). */
	public static Properties read(Vault v, String id) { return Store.read(new File(v.folderOfId(id), FILE)); }
}
