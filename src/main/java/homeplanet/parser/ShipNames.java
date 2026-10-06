package homeplanet.parser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ship names for the dice at Commission, from the resource shipnames.txt: whole names, two-word names put together,
 * and each model's own (a Mantis cruiser may roll "Bloodfang"). Never one the fleet already has.
 */
public final class ShipNames {
	private static final Logger log = LoggerFactory.getLogger(ShipNames.class);
	private ShipNames() {}

	/** As SpaceDockUI's long-name warning: FTL may cut off longer names. */
	public static final int MAX = 20;

	/**
	 * A ship's name after "the", mid-sentence: "the Kestrel", but a name that begins with "The" as she is, never
	 * "the The Adjudicator" (a built ship's name starts with "The" by default; heromedel's screenshot, 5.31).
	 */
	public static String the(String name) { return startsWithThe(name) ? name : "the " + name; }
	/** As {@link #the}, starting a sentence: "The Kestrel", "The Adjudicator". */
	public static String theStart(String name) {
		return startsWithThe(name) ? Character.toUpperCase(name.charAt(0)) + name.substring(1) : "The " + name;
	}
	private static boolean startsWithThe(String name) { return name != null && name.regionMatches(true, 0, "the ", 0, 4); }
	/** A letter's "the {key}" and "The {key}" filled with a ship's name by those rules, then any bare {key}. */
	public static String fill(String text, String key, String name) {
		String n = name == null ? "" : name, k = java.util.regex.Pattern.quote("{" + key + "}");
		text = text.replaceAll("(?<![A-Za-z])the " + k, java.util.regex.Matcher.quoteReplacement(n.isEmpty() ? "the " : the(n)));
		text = text.replaceAll("(?<![A-Za-z])The " + k, java.util.regex.Matcher.quoteReplacement(n.isEmpty() ? "The " : theStart(n)));
		return text.replace("{" + key + "}", n);
	}
	private static Map<String, List<String>> lists;

	/** The model's section in shipnames.txt for a blueprint id (PLAYER_SHIP_MANTIS_2, PLAYER_SHIP_JELLY_HP...), or null. */
	static String race(String blueprintId) {
		if (blueprintId == null) return null;
		String id = blueprintId.endsWith(Retrofit.SUFFIX) ? Retrofit.vanillaId(blueprintId) : blueprintId;
		String[][] models = {{"PLAYER_SHIP_HARD", "kestrel"}, {"PLAYER_SHIP_STEALTH", "stealth"}, {"PLAYER_SHIP_MANTIS", "mantis"},
				{"PLAYER_SHIP_CIRCLE", "engi"}, {"PLAYER_SHIP_FED", "federation"}, {"PLAYER_SHIP_JELLY", "slug"}, {"PLAYER_SHIP_ROCK", "rock"},
				{"PLAYER_SHIP_ENERGY", "zoltan"}, {"PLAYER_SHIP_CRYSTAL", "crystal"}, {"PLAYER_SHIP_ANAEROBIC", "lanius"}};
		for (String[] m : models) if (id.equals(m[0]) || id.startsWith(m[0] + "_")) return m[1];
		return null;
	}
	/** The layout's own section, as "kestrel a" (Type A: the model's id itself; B ends _2, C _3), or null. */
	static String layout(String blueprintId) {
		String race = race(blueprintId);
		if (race == null) return null;
		String id = blueprintId.endsWith(Retrofit.SUFFIX) ? Retrofit.vanillaId(blueprintId) : blueprintId;
		return race + " " + (id.endsWith("_2") ? "b" : id.endsWith("_3") ? "c" : "a");
	}

	/** A new name for a ship of this blueprint, unlike any in {@code taken} (ignoring case); null if none is left. */
	public static String roll(String blueprintId, Collection<String> taken, Random rng) {
		Map<String, List<String>> l = lists();
		Set<String> used = new HashSet<String>();
		if (taken != null) for (String t : taken) if (t != null) used.add(t.trim().toLowerCase());
		List<String> own = new java.util.ArrayList<String>();
		if (l.get(race(blueprintId)) != null) own.addAll(l.get(race(blueprintId)));
		if (l.get(layout(blueprintId)) != null) own.addAll(l.get(layout(blueprintId))); // a layout's own names join its model's
		for (int tries = 0; tries < 200; tries++) {
			String n;
			int pick = rng.nextInt(10);
			if (!own.isEmpty() && pick < 3) n = any(own, rng);              // the model's own, 3 in 10
			else if (pick < 7) n = any(l.get("single"), rng);                              // a whole name
			else { String a = any(l.get("first"), rng), b = any(l.get("second"), rng); n = a == null || b == null ? null : a + " " + b; }
			if (n == null || n.isEmpty() || n.length() > MAX || used.contains(n.toLowerCase())) continue;
			return n;
		}
		return null;
	}
	private static String any(List<String> l, Random rng) { return l == null || l.isEmpty() ? null : l.get(rng.nextInt(l.size())); }

	/** Every section of shipnames.txt, read once. */
	static synchronized Map<String, List<String>> lists() {
		if (lists != null) return lists;
		lists = new LinkedHashMap<String, List<String>>();
		InputStream in = ShipNames.class.getResourceAsStream("/homeplanet/resource/shipnames.txt");
		if (in == null) { log.error("shipnames.txt is missing from the program"); return lists; }
		try {
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			List<String> cur = null;
			for (String line; (line = r.readLine()) != null;) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				if (line.startsWith("==")) {
					cur = new ArrayList<String>();
					lists.put(line.substring(2).trim().toLowerCase(), cur);
				} else if (cur != null) {
					cur.add(line);
				}
			}
			r.close();
		} catch (Exception e) {
			log.error("Could not read shipnames.txt", e);
		}
		return lists;
	}
}
