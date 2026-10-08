package homeplanet.vault;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.CrewType;

/**
 * A crew member on disk (heromedel's Plan O, 6.11): the one home for how the station keeps someone's whole record as
 * FTL's save has them (name, race, sex, health, skills, masteries, service record, looks), in plain names of its own:
 *
 *     name, race, sex (male or female), health, tints (one per layer of their looks, commas between)
 *     skills.pilot, skills.engines, skills.shields, skills.weapons, skills.repair, skills.combat   (experience)
 *     mastery.pilot ... mastery.combat                                                             (0, 1 or 2)
 *     record.repairs, record.kills, record.evasions, record.jumps, record.masteries
 *
 * In a file of tags each dot is a tag inside a tag: {@code <skills><pilot>13</pilot>...</skills>}. Every file that keeps
 * crew uses it: the crew files, the captives, the infirmary, the expeditions (crew away, recruits waiting, the faces on
 * a report) and the rebuild. Long Range Comm. keeps its own names on the wire and converts ({@link #toWire},
 * {@link #crew}); a file written before 6.11 has the wire's names, read as they are and written this way when it is
 * next saved.
 */
public final class CrewRecord {
	private CrewRecord() { }

	/** The six skills, in FTL's order. */
	public static final String[] SKILLS = {"pilot", "engines", "shields", "weapons", "repair", "combat"};
	/** The service record's counts. */
	public static final String[] COUNTS = {"repairs", "kills", "evasions", "jumps", "masteries"};

	/** Their whole record, in the station's names. */
	public static Map<String, String> of(CrewState c) {
		Map<String, String> f = new LinkedHashMap<String, String>();
		f.put("name", c.getName());
		f.put("race", c.getRace().getId());
		f.put("sex", c.isMale() ? "male" : "female");
		f.put("health", Integer.toString(c.getHealth()));
		int[] s = {c.getPilotSkill(), c.getEngineSkill(), c.getShieldSkill(), c.getWeaponSkill(), c.getRepairSkill(), c.getCombatSkill()};
		for (int i = 0; i < 6; i++) f.put("skills." + SKILLS[i], Integer.toString(s[i]));
		boolean[][] m = {{c.getPilotMasteryOne(), c.getPilotMasteryTwo()}, {c.getEngineMasteryOne(), c.getEngineMasteryTwo()}, {c.getShieldMasteryOne(), c.getShieldMasteryTwo()},
				{c.getWeaponMasteryOne(), c.getWeaponMasteryTwo()}, {c.getRepairMasteryOne(), c.getRepairMasteryTwo()}, {c.getCombatMasteryOne(), c.getCombatMasteryTwo()}};
		for (int i = 0; i < 6; i++) f.put("mastery." + SKILLS[i], m[i][1] ? "2" : m[i][0] ? "1" : "0");
		int[] n = {c.getRepairs(), c.getCombatKills(), c.getPilotedEvasions(), c.getJumpsSurvived(), c.getSkillMasteriesEarned()};
		for (int i = 0; i < COUNTS.length; i++) f.put("record." + COUNTS[i], Integer.toString(n[i]));
		StringBuilder t = new StringBuilder();
		if (c.getSpriteTintIndeces() != null) for (Integer x : c.getSpriteTintIndeces()) t.append(t.length() == 0 ? "" : ",").append(x);
		f.put("tints", t.toString());
		return f;
	}

	/** The names in their order (name, race, sex, health, skills, mastery, record, tints), then any others as they came. */
	static Map<String, String> ordered(Map<String, String> f) {
		List<String> order = new ArrayList<String>(java.util.Arrays.asList("name", "race", "sex", "health"));
		for (String s : SKILLS) order.add("skills." + s);
		for (String s : SKILLS) order.add("mastery." + s);
		for (String k : COUNTS) order.add("record." + k);
		order.add("tints");
		Map<String, String> out = new LinkedHashMap<String, String>();
		for (String k : order) if (f.containsKey(k)) out.put(k, f.get(k));
		List<String> rest = new ArrayList<String>(f.keySet());
		java.util.Collections.sort(rest);
		for (String k : rest) if (!out.containsKey(k)) out.put(k, f.get(k));
		return out;
	}

	/** True if these are the wire's names (a file from before 6.11, or the Long Range's fields). */
	static boolean wire(Map<String, String> f) { return !f.containsKey("skills.pilot") && (f.containsKey("s0") || f.containsKey("mastery")); }

	/** The station's names from the wire's (s0..s5, a mastery flag each for the twelve levels, male), unchanged if they are the station's already. */
	public static Map<String, String> fromWire(Map<String, String> w) throws IOException {
		if (!wire(w)) return w;
		Map<String, String> f = new LinkedHashMap<String, String>();
		f.put("name", get(w, "name"));
		f.put("race", get(w, "race"));
		f.put("sex", "true".equals(w.get("male")) ? "male" : "female");
		f.put("health", get(w, "health"));
		for (int i = 0; i < 6; i++) f.put("skills." + SKILLS[i], get(w, "s" + i));
		String mb = get(w, "mastery");
		if (!mb.matches("[01]{12}")) throw new IOException("mastery flags");
		for (int i = 0; i < 6; i++) f.put("mastery." + SKILLS[i], mb.charAt(i * 2 + 1) == '1' ? "2" : mb.charAt(i * 2) == '1' ? "1" : "0");
		for (String k : COUNTS) f.put("record." + k, get(w, k));
		f.put("tints", get(w, "tints"));
		return f;
	}
	/** The wire's names (Long Range Comm., and a crew file in a ship's package, which an older station reads as it always has). */
	public static Map<String, String> toWire(Map<String, String> f) {
		if (wire(f)) return f;
		Map<String, String> w = new LinkedHashMap<String, String>();
		w.put("name", get(f, "name"));
		w.put("race", get(f, "race"));
		w.put("male", Boolean.toString(!"female".equals(f.get("sex"))));
		w.put("health", get(f, "health"));
		for (int i = 0; i < 6; i++) w.put("s" + i, get(f, "skills." + SKILLS[i]));
		StringBuilder mb = new StringBuilder();
		for (String s : SKILLS) { String l = get(f, "mastery." + s); mb.append(l.equals("1") || l.equals("2") ? '1' : '0').append(l.equals("2") ? '1' : '0'); }
		w.put("mastery", mb.toString());
		for (String k : COUNTS) w.put(k, get(f, "record." + k));
		w.put("tints", get(f, "tints"));
		return w;
	}
	private static String get(Map<String, String> f, String k) { String v = f.get(k); return v == null ? "" : v; }

	/**
	 * A crew member rebuilt from their record (the station's names or the wire's), each field kept within what FTL
	 * allows: a known race, health up to the race's most, skills up to their second level, counts that can't go
	 * negative. Everything else starts as a new crew member's would; where they stand is set when they come aboard.
	 * Throws if the record can't be a crew member (an unknown race, a field that isn't a number).
	 */
	public static CrewState crew(Map<String, String> fields) throws IOException {
		Map<String, String> f = fromWire(fields);
		CrewType race = CrewType.findById(get(f, "race"));
		if (race == null) throw new IOException("unknown crew race " + homeplanet.comm.Line.text(get(f, "race"), 32));
		String name = homeplanet.comm.Line.text(get(f, "name"), 32);
		if (name.isEmpty()) name = "Crew";
		CrewState c = new CrewState();
		c.setRace(race);
		c.setName(name);
		c.setMale(!"female".equals(f.get("sex")));
		c.setPlayerControlled(true);
		c.setHealth(Math.max(1, Math.min(race.getMaxHealth(), num(f, "health", 0, 100000))));
		int[] max = maxSkills(race);
		int[] s = new int[6];
		for (int i = 0; i < 6; i++) s[i] = Math.min(max[i], num(f, "skills." + SKILLS[i], 0, 100000));
		c.setPilotSkill(s[0]); c.setEngineSkill(s[1]); c.setShieldSkill(s[2]); c.setWeaponSkill(s[3]); c.setRepairSkill(s[4]); c.setCombatSkill(s[5]);
		int[] l = new int[6];
		for (int i = 0; i < 6; i++) l[i] = num(f, "mastery." + SKILLS[i], 0, 2); // a second level has the first
		c.setPilotMasteryOne(l[0] >= 1); c.setPilotMasteryTwo(l[0] == 2); c.setEngineMasteryOne(l[1] >= 1); c.setEngineMasteryTwo(l[1] == 2);
		c.setShieldMasteryOne(l[2] >= 1); c.setShieldMasteryTwo(l[2] == 2); c.setWeaponMasteryOne(l[3] >= 1); c.setWeaponMasteryTwo(l[3] == 2);
		c.setRepairMasteryOne(l[4] >= 1); c.setRepairMasteryTwo(l[4] == 2); c.setCombatMasteryOne(l[5] >= 1); c.setCombatMasteryTwo(l[5] == 2);
		int cap = 1000000;
		c.setRepairs(num(f, "record.repairs", 0, cap)); c.setCombatKills(num(f, "record.kills", 0, cap)); c.setPilotedEvasions(num(f, "record.evasions", 0, cap));
		c.setJumpsSurvived(num(f, "record.jumps", 0, cap)); c.setSkillMasteriesEarned(num(f, "record.masteries", 0, 12));
		List<Integer> tints = new ArrayList<Integer>();
		String ts = get(f, "tints");
		if (!ts.isEmpty()) {
			if (!ts.matches("\\d{1,3}(,\\d{1,3}){0,15}")) throw new IOException("tints");
			for (String t : ts.split(",")) tints.add(Integer.parseInt(t));
		}
		c.setSpriteTintIndeces(fitTints(race, tints));
		return c;
	}
	private static int num(Map<String, String> f, String k, int min, int max) throws IOException {
		try {
			int v = Integer.parseInt(get(f, k).trim());
			if (v < min || v > max) throw new IOException("crew." + k + " out of range: " + v);
			return v;
		} catch (NumberFormatException e) {
			throw new IOException("crew." + k + " is not a number");
		}
	}
	/** Each tint within its layer's colours, and no more layers than the race has. */
	private static List<Integer> fitTints(CrewType race, List<Integer> tints) {
		List<Integer> out = new ArrayList<Integer>();
		try {
			net.blerf.ftl.xml.CrewBlueprint cb = net.blerf.ftl.parser.DataManager.get().getCrew(race.getId());
			if (cb == null || cb.getSpriteTintLayerList() == null) return out;
			List<net.blerf.ftl.xml.CrewBlueprint.SpriteTintLayer> layers = cb.getSpriteTintLayerList();
			for (int i = 0; i < layers.size() && i < tints.size(); i++) {
				int n = layers.get(i).tintList == null ? 0 : layers.get(i).tintList.size();
				out.add(n == 0 ? 0 : Math.min(tints.get(i), n - 1));
			}
		} catch (Exception e) {
			out.clear();
		}
		return out;
	}
	private static final net.blerf.ftl.constants.FTLConstants CONSTANTS = new net.blerf.ftl.constants.AdvancedFTLConstants();
	/** The most experience each skill holds (its second level): pilot, engines, shields, weapons, repair, combat. */
	private static int[] maxSkills(CrewType r) {
		return new int[] {2 * CONSTANTS.getMasteryIntervalPilot(r), 2 * CONSTANTS.getMasteryIntervalEngine(r), 2 * CONSTANTS.getMasteryIntervalShield(r),
				2 * CONSTANTS.getMasteryIntervalWeapon(r), 2 * CONSTANTS.getMasteryIntervalRepair(r), 2 * CONSTANTS.getMasteryIntervalCombat(r)};
	}

	// ---- in a file of tags ----

	/** The record as tags, one per line at this indent: a dot in a name is a tag inside a tag. */
	static void tags(StringBuilder sb, String indent, Map<String, String> f) {
		String open = null;
		for (Map.Entry<String, String> e : f.entrySet()) {
			String k = e.getKey();
			int dot = k.indexOf('.');
			String group = dot < 0 ? null : k.substring(0, dot);
			if (open != null && !open.equals(group)) { sb.append(indent).append("</").append(open).append(">\r\n"); open = null; }
			if (group != null && open == null) { sb.append(indent).append('<').append(group).append(">\r\n"); open = group; }
			String tag = dot < 0 ? k : k.substring(dot + 1);
			tag(sb, group == null ? indent : indent + "\t", tag, e.getValue());
		}
		if (open != null) sb.append(indent).append("</").append(open).append(">\r\n");
	}
	/** One tag and its text: {@code <name>Bob</name>}. */
	static void tag(StringBuilder sb, String indent, String tag, String text) {
		sb.append(indent).append('<').append(tag).append('>').append(safe(text)).append("</").append(tag).append(">\r\n");
	}
	/** Text XML 1.0 can hold: the control characters a name could carry dropped, & < > escaped. */
	static String safe(String s) {
		StringBuilder out = new StringBuilder();
		for (char ch : (s == null ? "" : s).toCharArray()) if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') out.append(ch);
		return homeplanet.parser.XmlText.text(out.toString());
	}
	/** The record read back from its tags (the tags inside a tag joined by a dot). */
	static Map<String, String> fromTags(Element e) {
		Map<String, String> out = new LinkedHashMap<String, String>();
		for (Element c : children(e)) {
			List<Element> inner = children(c);
			if (inner.isEmpty()) out.put(c.getTagName(), c.getTextContent());
			else for (Element d : inner) out.put(c.getTagName() + "." + d.getTagName(), d.getTextContent());
		}
		return out;
	}
	/** A tag's tags, in order. */
	static List<Element> children(Element e) {
		List<Element> out = new ArrayList<Element>();
		for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element) out.add((Element) n);
		return out;
	}
}
