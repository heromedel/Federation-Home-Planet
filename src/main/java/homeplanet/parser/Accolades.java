package homeplanet.parser;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.core.SafeFiles;
import homeplanet.vault.CrewRegister;
import homeplanet.vault.Vault;

/**
 * A rank letter's {accolade} (heromedel, 5.57): one line naming something the career really did, so the Admiralty
 * sounds like it has been reading about you, with a figure or two, never a score. Drawn from the station's records and
 * the ships' own saves (their real counts, 5.60): the ship that has done the most (her jumps), the latest achievement,
 * the model commissioned most, the ships defeated, a veteran crew member, the expeditions. Never the Rebel Flagship, nor
 * a victory achievement (heromedel). Each kind is used once a career, so no two letters say the same thing; with
 * nothing left to say, the line is left out.
 */
public final class Accolades {
	private static final Logger log = LoggerFactory.getLogger(Accolades.class);
	private Accolades() {}

	public static final String TOKEN = "{accolade}";
	/** The vault's events: "accolade:<kind>" -> the letter it went in. */
	static final String USED = "accolade:";

	/** The tokens an accolade or a deed may use: {ship} "the Kestrel", {n} a count (in words up to ten), {achievement}, {model}, {crew}. */
	static final List<String> TOKENS = java.util.Arrays.asList("ship", "n", "achievement", "model", "crew");
	/**
	 * The phrases for a kind of accolade (6.0 step 9a, 5.991: lore/deeds.xml, a map here before): phrases, not sentences,
	 * as they follow "Of particular note in the discussions was:" (heromedel's template, 5.57). The player's copy's
	 * phrases for a kind take the place of the station's own; none for an unknown kind.
	 */
	public static String[] words(String kind) { return lore("ACCOLADE", "of=" + kind, true); }
	/** An achievement told as the deed itself (heromedel, 5.57), by FTL's id; null for one not listed (the general words). */
	public static String deed(String achievementId) {
		String[] d = lore("DEED", "achievement=" + achievementId, false);
		return d.length == 0 ? null : d[0];
	}
	/** The words of deeds.xml's entries of this kind and condition, from the first source that has any (the copy wins), tokens checked. */
	private static String[] lore(String kind, String when, boolean all) {
		List<String> out = new ArrayList<String>();
		String from = null;
		for (homeplanet.core.Lore.Entry e : homeplanet.core.Lore.entries(homeplanet.core.Lore.DEEDS)) {
			if (!e.kind.equals(kind) || !when.equals(e.when == null ? "" : e.when.trim())) continue;
			if (from != null && !from.equals(e.source)) break; // the copy's entries come first: they take the kind's place
			String bad = null;
			Matcher m = Pattern.compile("\\{([a-z0-9_.]+)\\}").matcher(e.words);
			while (m.find()) if (!TOKENS.contains(m.group(1)) && !java.util.Arrays.asList(homeplanet.model.Words.SHIP_TOKENS).contains(m.group(1))) bad = m.group(1);
			if (bad != null) { homeplanet.core.Lore.problem(e.source + ", line " + e.line + " (" + when + "): {" + bad + "} isn't an accolade's token; left out"); continue; }
			from = e.source;
			out.add(homeplanet.model.Words.ship(e.words)); // {she}, {her}: the Ship Pronoun setting
			if (!all) break;
		}
		return out.toArray(new String[0]);
	}

	/** The letter's text with its {accolade} filled, or that paragraph taken out when there's nothing left to say. */
	public static String fill(Vault v, String letterKey, String body) { return fill(v, letterKey, body, 1); }
	/** As above, with up to this many accolades in the one paragraph (an old career confirmed three ranks up or more). */
	public static String fill(Vault v, String letterKey, String body, int many) {
		if (!body.contains(TOKEN)) return body;
		StringBuilder said = new StringBuilder();
		Random rng = new Random();
		try {
			for (int i = 0; i < many; i++) {
				String one = pick(v, letterKey, rng);
				if (one == null) break;
				said.append(said.length() == 0 ? "" : "\n").append(one); // one to a line, under "were:"
			}
		} catch (RuntimeException e) { log.warn("Could not find an accolade for {}: {}", letterKey, e.toString()); }
		String line = said.length() == 0 ? null : said.toString();
		if (line != null) return (line.contains("\n") ? body.replace("was:\n" + TOKEN, "were:\n" + TOKEN) : body).replace(TOKEN, line);
		return body.replaceAll("(?m)^[^\\n]*:\\n\\Q" + TOKEN + "\\E\\n*", "").replaceAll("\\n*\\Q" + TOKEN + "\\E\\n*", "\n\n").trim(); // its heading goes with it
	}

	/** One unused kind the fleet has something for, chosen at random and marked used; its words; or null. */
	static String pick(Vault v, String letterKey, Random rng) {
		if (v == null) return null;
		Map<String, String[]> have = new LinkedHashMap<String, String[]>(); // kind -> {ship, n, achievement, model, crew}
		java.util.List<homeplanet.core.EventLog.Entry> events = homeplanet.core.EventLog.read(v); // the logs as events (5.74), in the old logs' form for the readers below
		StringBuilder repText = new StringBuilder(), histText = new StringBuilder();
		for (homeplanet.core.EventLog.Entry e : homeplanet.core.EventLog.ofLog(events, "reputation")) {
			repText.append(e.time.length() >= 16 ? e.time.substring(0, 16) : e.time).append("  ").append(homeplanet.vault.Reputation.signed(e.num("points", 0))).append("  ").append(e.human).append('\n');
			for (int i = 1; e.get("detail." + i) != null; i++) repText.append("  ").append(e.get("detail." + i)).append('\n');
		}
		for (homeplanet.core.EventLog.Entry e : homeplanet.core.EventLog.ofLog(events, "station")) {
			histText.append(e.time.length() >= 16 ? e.time.substring(0, 16) : e.time).append("  ").append(e.kind.replace('_', ' ')).append("  ").append(e.get("headline", e.human)).append('\n');
			for (int i = 1; e.get("detail." + i) != null; i++) histText.append("  ").append(e.get("detail." + i)).append('\n');
		}
		String rep = repText.toString();
		String hist = histText.toString();
		homeplanet.vault.Ship best = mostJumps(v);
		int jumps = best == null ? 0 : jumps(v, best);
		if (jumps >= 2) have.put("ship", new String[] {best.name, Integer.toString(jumps), null, null, null});
		String ach = latestAchievement(rep);
		if (ach != null) have.put("achievement", new String[] {null, null, ach, null, null});
		String[] model = mostFlown(hist);
		if (model != null) have.put("model", new String[] {null, model[1], null, model[0], null});
		int fights = homeplanet.vault.Reputation.defeatedInService(v);
		if (fights >= 3) have.put("fights", new String[] {null, Integer.toString(fights), null, null, null});
		String crew = veteran(v);
		if (crew != null) have.put("crew", new String[] {null, null, null, null, crew});
		int trips = count(rep, "(?m)^\\S+ \\S+  \\S+  Expedition: ");
		if (trips >= 2) have.put("expeditions", new String[] {null, Integer.toString(trips), null, null, null});
		List<String> open = new ArrayList<String>();
		for (String k : have.keySet()) if (v.event(USED + k) == null) open.add(k);
		if (open.isEmpty()) return null;
		String kind = open.get(rng.nextInt(open.size()));
		String[] say = words(kind);
		if (say.length == 0) return null; // no words for it (a copy of deeds.xml can't take a kind's words away: the station's own stand)
		String[] x = have.get(kind);
		String line = say[rng.nextInt(say.length)];
		if (kind.equals("achievement")) { String deed = deed(achievementId(x[2])); if (deed != null) line = deed; }
		if (x[0] != null) line = line.replace("{ship}", ShipNames.the(x[0]));
		if (x[1] != null) line = line.replace("{n}", number(Integer.parseInt(x[1])));
		if (x[2] != null) line = line.replace("{achievement}", x[2]);
		if (x[3] != null) line = line.replace("{model}", x[3]);
		if (x[4] != null) line = line.replace("{crew}", x[4]);
		line = homeplanet.model.Words.cap(line); // "Twelve jumps…", "The Kestrel…"
		v.recordEvent(USED + kind, letterKey);
		return line;
	}
	/** Up to ten in words, then digits (docs/STYLE.md). */
	static String number(int n) { return homeplanet.model.Words.number(n); }
	private static int count(String s, String regex) { int n = 0; Matcher m = Pattern.compile(regex).matcher(s); while (m.find()) n++; return n; }
	/**
	 * Her jumps in the career's service, from her own save (heromedel, 5.60): beacons explored since she joined the fleet,
	 * less the one she started at, or since her last trade (CLAUDE.md, traded ships). Her voyage log can't count them: it
	 * has a line for each save the station saw, and FTL flown with the station closed is one line for many jumps.
	 */
	static int jumps(Vault v, homeplanet.vault.Ship s) {
		net.blerf.ftl.parser.SavedGameParser.SavedGameState gs = s.save();
		if (gs == null) return 0;
		homeplanet.vault.TradeMark m = homeplanet.vault.TradeMark.of(v, s.id);
		return Math.max(0, gs.getTotalBeaconsExplored() - (m == null ? 1 : m.beacons));
	}
	/** The fleet's ship (still on the books, not the Cargo Hold) with the most jumps in the career's service, or null. */
	static homeplanet.vault.Ship mostJumps(Vault v) {
		homeplanet.vault.Ship best = null;
		int most = 0;
		for (homeplanet.vault.Ship s : v.all()) {
			if (s.isStorage() || v.ignoring(s)) continue; // an ignored one isn't the career's ship (5.54)
			int n = jumps(v, s);
			if (n > most) { most = n; best = s; }
		}
		return best;
	}
	/**
	 * The latest FTL achievement the career was credited with ("An achievement: X" or "2 achievements: X, Y"), never a
	 * victory: a career's log from before 5.60 may name one, and after its own letter the war goes on (hard rule 1).
	 */
	static String latestAchievement(String rep) {
		String last = null;
		Matcher m = Pattern.compile("(?m)achievements?: (.+?) \\(\\+\\d+\\)\\s*$").matcher(rep);
		while (m.find()) { String one = lastNamed(m.group(1)); if (one != null) last = one; }
		return last;
	}
	/**
	 * The last of FTL's achievements named in a log line's list, never a victory. Found by FTL's names, not by splitting
	 * at commas: one has a comma of its own ("Givin' her all she's got, Captain!").
	 */
	private static String lastNamed(String said) {
		String best = null;
		int at = -1;
		try {
			for (Map.Entry<String, net.blerf.ftl.xml.Achievement> e : DataManager.get().getAchievements().entrySet()) {
				if (e.getValue().getName() == null || homeplanet.vault.Reputation.victory(e.getKey())) continue;
				String n = e.getValue().getName().getTextValue();
				int i = n == null || n.isEmpty() ? -1 : said.lastIndexOf(n);
				if (i < 0 || (i > 0 && !said.startsWith(", ", i - 2)) || (i + n.length() < said.length() && !said.startsWith(", ", i + n.length()))) continue; // a whole name in the list
				if (i > at || (i == at && n.length() > best.length())) { at = i; best = n; }
			}
		} catch (RuntimeException e) { log.debug("Could not look up FTL's achievements: {}", e.toString()); }
		return best;
	}
	/** FTL's id for an achievement's name, or null. */
	static String achievementId(String name) {
		try {
			for (Map.Entry<String, net.blerf.ftl.xml.Achievement> e : DataManager.get().getAchievements().entrySet())
				if (e.getValue().getName() != null && name.equals(e.getValue().getName().getTextValue())) return e.getKey();
		} catch (RuntimeException e) { log.debug("Could not look up the achievement {}: {}", name, e.toString()); }
		return null;
	}
	/** The model (FTL's class, "Kestrel Cruiser") commissioned most often, if more than once: {class, count}. */
	static String[] mostFlown(String hist) {
		Map<String, Integer> n = new LinkedHashMap<String, Integer>();
		Matcher m = Pattern.compile("(?m)COMMISSION  .*\\r?\\n  .*?\\(([A-Z0-9_]+)\\)").matcher(hist);
		while (m.find()) {
			String cls = className(m.group(1));
			if (cls != null) n.put(cls, (n.containsKey(cls) ? n.get(cls) : 0) + 1);
		}
		String best = null;
		for (Map.Entry<String, Integer> e : n.entrySet()) if (e.getValue() > 1 && (best == null || e.getValue() > n.get(best))) best = e.getKey();
		return best == null ? null : new String[] {best, Integer.toString(n.get(best))};
	}
	private static String className(String id) {
		try {
			ShipBlueprint bp = DataManager.get().getShip(id);
			String c = bp == null || bp.getShipClass() == null ? null : bp.getShipClass().getTextValue();
			return c == null || c.isEmpty() ? null : c;
		} catch (RuntimeException e) { return null; }
	}
	/** The serving crew member with the most milestones on their record (two at least), or null. */
	static String veteran(Vault v) {
		String best = null;
		int most = 1;
		for (CrewRegister.Member m : CrewRegister.members(v)) {
			if (m.status != CrewRegister.Status.PRESENT) continue;
			int n = 0;
			for (CrewRegister.Event e : m.events)
				if (e.text.startsWith("Mastered ") || e.text.startsWith("Earned the first ") || e.text.startsWith("First kill")
						|| e.text.startsWith("Survived a hundred jumps") || e.text.startsWith("Reached sector ")) n++;
			if (n > most) { most = n; best = m.name; }
		}
		return best;
	}
	private static String text(File f) {
		try { return f.isFile() ? new String(SafeFiles.read(f), java.nio.charset.StandardCharsets.UTF_8) : ""; }
		catch (Exception e) { return ""; }
	}
}
