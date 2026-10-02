package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.SafeFiles;

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
		int best = Math.max(num(p, "best"), lastSector + 1);
		p.clear();
		if (best > 0) p.setProperty("best", Integer.toString(best));
		p.setProperty("defeated", Integer.toString(gs.getTotalShipsDefeated()));
		p.setProperty("beacons", Integer.toString(gs.getTotalBeaconsExplored()));
		p.setProperty("scrap", Integer.toString(gs.getTotalScrapCollected()));
		p.setProperty("hired", Integer.toString(gs.getTotalCrewHired()));
		for (String k : VARS) if (gs.hasStateVar(k)) p.setProperty(k, Integer.toString(gs.getStateVar(k)));
		try {
			StringWriter w = new StringWriter();
			p.store(w, "Where her current journey began: Federation Home Planet rewrites this file");
			File dir = new File(v.historyDir(), s.id);
			if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
			SafeFiles.writeText(new File(dir, FILE), w.toString(), false);
		} catch (IOException e) {
			log.warn("Could not note where {}'s journey began: {}", s.name, e.toString());
		}
	}
	/** Her journey's start, or empty if the station hasn't seen one begin (a ship from before 4B.75). */
	public static Properties read(Vault v, String id) {
		Properties p = new Properties();
		File f = new File(new File(v.historyDir(), id), FILE);
		try { if (f.isFile()) p.load(new StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); }
		return p;
	}
	static int num(Properties p, String k) {
		try { return Integer.parseInt(p.getProperty(k, "0").trim()); } catch (NumberFormatException e) { return 0; }
	}
}
