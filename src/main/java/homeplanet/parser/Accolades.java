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

	/** The words, by kind: {ship} "the Kestrel", {n} a count (in words up to ten), {achievement}, {model}, {crew}. */
	static final Map<String, String[]> WORDS = new LinkedHashMap<String, String[]>();
	static {
		// phrases, not sentences: they follow "Of particular note in the discussions was:" (heromedel's template, 5.57)
		WORDS.put("ship", new String[] {
			"Your {n} jumps aboard {ship}.",
			"{ship}'s remarkable service, {n} jumps and counting."});
		WORDS.put("achievement", new String[] {
			"Word of \"{achievement}\" reaching the Admiralty.",
			"Your achievement, \"{achievement}\"."});
		WORDS.put("model", new String[] {
			"Your {n} {model}s, every one of them made your own.",
			"Getting more out of the {model} than anyone, across {n} of them."});
		WORDS.put("fights", new String[] {
			"Your defeat of {n} enemy ships.",
			"The {n} enemy ships fallen to your fleet's guns."});
		WORDS.put("crew", new String[] {
			"{crew}'s service at your side.",
			"The crew you chose, {crew} first among them."});
		WORDS.put("expeditions", new String[] {
			"Your {n} expeditions, and every crew that went.",
			"Your reports from {n} expeditions."});
	}

	/** An achievement told as the deed itself (heromedel, 5.57), by FTL's id; one not listed gets the general words. */
	static final Map<String, String> DEEDS = new LinkedHashMap<String, String>();
	static {
		DEEDS.put("ACH_SECTOR_5", "One of your ships fighting her way to sector 5.");
		DEEDS.put("ACH_SECTOR_8", "One of your ships making it all the way to sector 8, within reach of the Federation's own base.");
		DEEDS.put("ACH_UNLOCK_ALL", "Flying every kind of cruiser the Federation knows of.");
		DEEDS.put("ACH_SCRAP", "Your fleet hauling in over 10,000 scrap since you took command.");
		DEEDS.put("ACH_SHIPS", "Your defeat of a thousand enemy ships.");
		DEEDS.put("ACH_NO_UPGRADES", "Taking a ship to sector 5 without a single upgrade.");
		DEEDS.put("ACH_PACIFIST", "Reaching sector 5 without firing a shot.");
		DEEDS.put("ACH_NO_REPAIR", "Flying to sector 5 without once stopping for repairs at a store.");
		DEEDS.put("ACH_NO_MISSILES", "Reaching sector 8 without firing a single missile or bomb.");
		DEEDS.put("ACH_NO_DRONES", "Reaching sector 8 without launching a single drone.");
		DEEDS.put("ACH_NO_BUYING", "Reaching sector 8 without buying a thing at a store.");
		DEEDS.put("ACH_NO_DEATH", "Bringing every one of your crew through to sector 8 alive.");
		DEEDS.put("ACH_BURNING", "Setting every room of an enemy ship ablaze at once.");
		DEEDS.put("ACH_BAD_DODGING", "Taking five shots in a row with your engines at full, and flying on anyway.");
		DEEDS.put("ACH_ONE_VOLLEY", "Destroying an enemy ship in one volley, before she could fire a single shot.");
		DEEDS.put("ACH_BOARDING_DRONE", "A single boarding drone of yours clearing four enemy crew off their own ship.");
		DEEDS.put("ACH_INVADE_SHIP", "Your whole crew going across and taking an enemy ship with their own hands.");
		DEEDS.put("ACH_SLICE_DICE", "Your beams sweeping every room of an enemy ship in a matter of seconds.");
		DEEDS.put("ACH_SUFFOCATE", "Draining the air from an enemy ship until there was none left to breathe.");
		DEEDS.put("ACH_UNITED_FEDERATION", "Six peoples serving side by side aboard your Kestrel Cruiser.");
		DEEDS.put("ACH_FULL_ARSENAL", "Running eleven systems aboard one Kestrel Cruiser.");
		DEEDS.put("ACH_TOUGH_SHIP", "Bringing a Kestrel Cruiser back from a single point of hull to full strength.");
		DEEDS.put("ACH_ENERGY_SHIELDS", "Finishing a fight in your Zoltan Cruiser before the enemy ever got through her shield.");
		DEEDS.put("ACH_ENERGY_POWER", "Powering a ton of systems on that Zoltan Cruiser, all at once.");
		DEEDS.put("ACH_ENERGY_MANPOWER", "Reaching sector 5 in a Zoltan Cruiser on her original reactor.");
		DEEDS.put("ACH_STEALTH_DESTROY", "Taking an enemy from full strength to nothing in a single cloak of your Stealth Cruiser.");
		DEEDS.put("ACH_STEALTH_AVOID", "Slipping a storm of fire under a single cloak of your Stealth Cruiser.");
		DEEDS.put("ACH_STEALTH_TACTICAL", "Taking your Stealth Cruiser to sector 8 without once flying into a hazard.");
		DEEDS.put("ACH_ROBOTIC", "Keeping three drones at work at once from your Engi Cruiser.");
		DEEDS.put("ACH_ONLY_DRONES", "Your Engi Cruiser's drones winning a fight on their own, without a single weapon fired.");
		DEEDS.put("ACH_IONED", "Ioning four enemy systems at once from your Engi Cruiser.");
		DEEDS.put("ACH_ROCK_FIRE", "Your Rock crew fighting on through the flames aboard an enemy ship, and winning.");
		DEEDS.put("ACH_ROCK_MISSILES", "Your Rock Cruiser's missiles getting past an enemy's defense drone to finish her.");
		DEEDS.put("ACH_ROCK_CRYSTAL", "Your Rock Cruiser finding the hidden Crystal worlds, home of the Rock's ancient ancestors.");
		DEEDS.put("ACH_MANTIS_CREW_DEAD", "Your Mantis Cruiser's boarders clearing twenty enemy crews before sector 6.");
		DEEDS.put("ACH_MANTIS_SLAUGHTER", "Taking down five enemy crew without a scratch to your Mantis Cruiser or her crew.");
		DEEDS.put("ACH_MANTIS_SURVIVOR", "Your last Mantis standing winning the fight aboard the enemy's own ship.");
		DEEDS.put("ACH_SLUG_VISION", "Your Slug Cruiser having eyes in every room of an enemy ship, sensors or not.");
		DEEDS.put("ACH_SLUG_NEBULA", "Your Slug Cruiser visiting thirty nebulas before sector 8.");
		DEEDS.put("ACH_SLUG_BIO", "Taking down three enemy crew with one shot from your Anti-Bio Beam.");
		DEEDS.put("ACH_FED_PATIENCE", "Winning a fight with the Artillery Beam alone, without a scratch to the hull.");
		DEEDS.put("ACH_FED_DIPLOMACY", "Your Federation Cruiser's crew talking their way through four tight spots before sector 5.");
		DEEDS.put("ACH_FED_UPGRADE", "Reaching sector 5 in a Federation Cruiser without upgrading her weapons.");
		DEEDS.put("ACH_CRYSTAL_SHARD", "Finishing an enemy ship with a shard of Crystal Vengeance.");
		DEEDS.put("ACH_CRYSTAL_LOCKDOWN", "Sealing four enemy crew in a single room from your Crystal Cruiser.");
		DEEDS.put("ACH_CRYSTAL_CLASH", "Your Crystal Cruiser's defeat of ten Rock ships.");
		DEEDS.put("ACH_LANIUS_ADVANCED", "Running Hacking, Mind Control and the Battery all at once aboard your Lanius Cruiser.");
		DEEDS.put("ACH_LANIUS_SCRAP", "Filling one Lanius Cruiser's hold with six hundred scrap.");
		DEEDS.put("ACH_LANIUS_OXYGEN", "Taking your Lanius Cruiser to sector 8 on barely a breath of air.");
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
		String rep = text(new File(v.root, "reputation.log"));
		String hist = text(new File(v.root, "history.log"));
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
		String[] say = WORDS.get(kind);
		String[] x = have.get(kind);
		String line = say[rng.nextInt(say.length)];
		if (kind.equals("achievement")) { String deed = DEEDS.get(achievementId(x[2])); if (deed != null) line = deed; }
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
	static String number(int n) {
		String[] w = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
		return n >= 0 && n < w.length ? w[n] : String.format("%,d", n);
	}
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
