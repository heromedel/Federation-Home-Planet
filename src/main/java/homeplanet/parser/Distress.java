package homeplanet.parser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

/**
 * Distress beacons (heromedel, 6.21). FTL places them from the sector's seed and saves no mark of one, and the save's last
 * event id is blank for them (heromedel's save at a distress beacon, 6.21); but the encounter keeps the id of the text on
 * screen ("event_DISTRESS_SATELLITE_DEFENSE_text"). FTL's event files mark each distress event with {@code <distressBeacon/>}:
 * this reads them for those events' text ids (a text list's every text, where an event picks one), so a save can be told
 * as one at a distress beacon.
 */
public final class Distress {
	private static final Logger log = LoggerFactory.getLogger(Distress.class);
	private Distress() { }

	/** FTL's event files, as its data manager reads them. */
	static final String[] FILES = {"data/events.xml", "data/newEvents.xml", "data/events_crystal.xml", "data/events_engi.xml", "data/events_mantis.xml",
			"data/events_rock.xml", "data/events_slug.xml", "data/events_zoltan.xml", "data/events_nebula.xml", "data/events_pirate.xml", "data/events_rebel.xml",
			"data/events_fuel.xml", "data/events_boss.xml", "data/events_ships.xml", "data/dlcEvents.xml", "data/dlcEventsOverwrite.xml", "data/dlcEvents_anaerobic.xml"};
	private static Set<String> texts;
	private static DataManager readFrom;

	/** Is she at a distress beacon: the text on screen one of a distress event's? False if the game data isn't loaded. */
	public static boolean arrivedAt(SavedGameState gs) {
		if (gs == null || gs.getEncounter() == null) return false;
		String t = gs.getEncounter().getText();
		return t != null && !t.isEmpty() && textIds().contains(t);
	}
	/** The text ids of FTL's distress events (empty if the game data isn't loaded). */
	public static synchronized Set<String> textIds() {
		DataManager dm = DataManager.get();
		if (dm == null) return new HashSet<String>();
		if (texts == null || readFrom != dm) {
			Map<String, String> files = new LinkedHashMap<String, String>();
			for (String f : FILES) {
				try { files.put(f, read(dm.getResourceInputStream(f))); }
				catch (Exception e) { log.debug("No {} in FTL's data: {}", f, e.toString()); }
			}
			texts = scan(files.values());
			readFrom = dm;
			log.debug("Distress events' texts: {}", texts.size());
		}
		return texts;
	}

	private static final Pattern TAG = Pattern.compile("<(/?)(event|choice|text|distressBeacon)\\b([^>]*?)(/?)>");
	private static final Pattern ATTR = Pattern.compile("(\\w+)\\s*=\\s*\"([^\"]*)\"");
	private static final Pattern LIST = Pattern.compile("<textList\\s+name\\s*=\\s*\"([^\"]*)\"[^>]*>(.*?)</textList>", Pattern.DOTALL);

	/**
	 * The text ids of the distress events in these files: each top-level event with {@code <distressBeacon/>} among its own
	 * tags (not a choice's), its own text's id, or every text of the list it loads.
	 */
	static Set<String> scan(Iterable<String> files) {
		Set<String> ids = new HashSet<String>(), lists = new HashSet<String>();
		Map<String, Set<String>> listTexts = new LinkedHashMap<String, Set<String>>();
		for (String raw : files) {
			String t = raw.replaceAll("(?s)<!--.*?-->", "");
			Matcher l = LIST.matcher(t);
			while (l.find()) {
				Set<String> in = listTexts.containsKey(l.group(1)) ? listTexts.get(l.group(1)) : new HashSet<String>();
				Matcher tx = Pattern.compile("<text\\b[^>]*\\bid\\s*=\\s*\"([^\"]*)\"").matcher(l.group(2));
				while (tx.find()) in.add(tx.group(1));
				listTexts.put(l.group(1), in);
			}
			int depth = 0, choices = 0;
			boolean distress = false;
			String textId = null, textList = null;
			Matcher m = TAG.matcher(t);
			while (m.find()) {
				boolean close = !m.group(1).isEmpty(), self = !m.group(4).isEmpty();
				String tag = m.group(2);
				if (tag.equals("event")) {
					if (close) { depth--; if (depth == 0) { if (distress) { if (textId != null) ids.add(textId); if (textList != null) lists.add(textList); } } }
					else if (!self) { depth++; if (depth == 1) { distress = false; textId = null; textList = null; choices = 0; } }
				} else if (tag.equals("choice")) {
					if (depth == 1) { if (close) choices--; else if (!self) choices++; }
				} else if (depth == 1 && choices == 0 && !close) {
					if (tag.equals("distressBeacon")) distress = true;
					else if (tag.equals("text") && textId == null && textList == null) {
						Matcher a = ATTR.matcher(m.group(3));
						while (a.find()) { if (a.group(1).equals("id")) textId = a.group(2); else if (a.group(1).equals("load")) textList = a.group(2); }
					}
				}
			}
		}
		for (String n : lists) if (listTexts.containsKey(n)) ids.addAll(listTexts.get(n));
		return ids;
	}
	private static String read(InputStream in) throws java.io.IOException {
		try {
			ByteArrayOutputStream o = new ByteArrayOutputStream();
			byte[] b = new byte[8192];
			for (int n; (n = in.read(b)) > 0;) o.write(b, 0, n);
			return new String(o.toByteArray(), StandardCharsets.UTF_8);
		} finally {
			in.close();
		}
	}
}
