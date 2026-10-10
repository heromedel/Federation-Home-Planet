package homeplanet.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import net.blerf.ftl.parser.DataManager;

import homeplanet.core.HomePlanet;
import homeplanet.core.Lore;
import homeplanet.core.SafeFiles;

/**
 * The one home for the names the station gives crew (heromedel and McCarthy, 6.33; docs/NAMES.md). With Custom Name
 * Lists Per Race on (the default), each race is named from its list in lore/names/ (a copy beside the jar replaces the
 * jar's whole file; a copy that won't read falls back to the jar's and is named in Lore.problems()); a race with no
 * list (a mod's) keeps FTL's names. Human Name Gen shapes the human names, and with the lists off every race's, since
 * then every race has FTL's human names. The reading and the female forms follow the harness's NamesT, the reference.
 */
public final class CrewNames {
	private CrewNames() { }

	/** cfg: "false" turns the lists off; missing or anything else is on. */
	public static final String CFG_LISTS = "custom_crew_names";
	/** cfg: normal, first or first_last (missing is normal). */
	public static final String CFG_HUMAN = "human_name_gen";
	public static final String NORMAL = "normal", FIRST = "first", FIRST_LAST = "first_last";

	public static boolean listsOn() { return !"false".equals(HomePlanet.config.getProperty(CFG_LISTS)); }
	public static String humanGen() {
		String g = HomePlanet.config.getProperty(CFG_HUMAN, NORMAL);
		return FIRST.equals(g) || FIRST_LAST.equals(g) ? g : NORMAL;
	}

	/** The names files, by FTL race id (the Zoltan are energy, the Lanius anaerobic), as lore/ holds them. */
	public static final String[] FILES = {"names/human.xml", "names/zoltan.xml", "names/engi.xml", "names/mantis.xml", "names/rock.xml", "names/crystal.xml", "names/lanius.xml", "names/slug.xml"};
	static String fileOf(String raceId) {
		if (raceId == null) return null;
		if (raceId.equals("energy")) return "names/zoltan.xml";
		if (raceId.equals("anaerobic")) return "names/lanius.xml";
		String f = "names/" + raceId + ".xml";
		for (String x : FILES) if (x.equals(f)) return f;
		return null;
	}

	// ---- picking ----

	/** A name for a new crew member of this race and sex under the settings, tried against the names so far (namesakes stay possible). */
	public static String unique(String raceId, boolean male, Random rng, Set<String> used) {
		String n = null;
		for (int tries = 0; tries < 50; tries++) {
			n = pick(raceId, male, rng);
			if (n != null && used.add(n)) return n;
		}
		return n == null ? "Crew" : n;
	}

	/** A name for a crew member of this race and sex under the settings (the dice in Rename crew roll this). */
	public static String pick(String raceId, boolean male, Random rng) {
		String gen = humanGen();
		if (listsOn()) {
			if ("human".equals(raceId)) {
				Lists h = lists("names/human.xml");
				if (h != null && h.first != null && h.last != null) {
					String first = pick(h.first, male, rng);
					boolean last = FIRST_LAST.equals(gen) || NORMAL.equals(gen) && rng.nextBoolean(); // Normal: about half with a last name (heromedel, 6.33)
					return last ? first + " " + pick(h.last, male, rng) : first;
				}
			} else {
				Lists l = lists(fileOf(raceId));
				if (l != null && l.one != null) return pick(l.one, male, rng);
			}
		}
		return ftl(male, gen); // FTL's own names, shaped by Human Name Gen: every race has them when the lists are off
	}

	private static final Random FTL_RNG = new Random();
	private static final Set<String> TITLES = new HashSet<String>(java.util.Arrays.asList("mr", "mrs", "ms", "dr", "sir", "miss"));
	/** One of FTL's names: as it is (Normal), its first word (a title keeps the whole name: Mr Buga), or with a last word borrowed from FTL's two-word names. */
	static String ftl(boolean male, String gen) {
		DataManager dm = DataManager.get();
		String n = dm.getCrewName(male);
		if (n == null || NORMAL.equals(gen)) return n;
		String[] w = n.trim().split("\\s+");
		if (FIRST.equals(gen)) return w.length > 1 && !TITLES.contains(w[0].replace(".", "").toLowerCase()) ? w[0] : n.trim();
		if (w.length > 1) return n.trim();
		List<String> lasts = new ArrayList<String>();
		try {
			for (boolean m : new boolean[] {true, false}) for (String x : dm.getCrewNames(m)) {
				String[] p = x.trim().split("\\s+");
				if (p.length > 1 && p[p.length - 1].length() > 1) lasts.add(p[p.length - 1]); // a surname, not an initial (Bomfy M)
			}
		} catch (UnsupportedOperationException e) { return n; }
		return lasts.isEmpty() ? n : n.trim() + " " + lasts.get(FTL_RNG.nextInt(lasts.size()));
	}

	/** By weight of rarity, a man an M or B name, a woman an F or B one, a B name in one of its female forms (NamesT.pick). */
	static String pick(List_ l, boolean male, Random rng) {
		List<Name> ok = new ArrayList<Name>();
		long total = 0;
		for (Name n : l.names) if (n.sex.equals("B") || n.sex.equals(male ? "M" : "F")) { ok.add(n); total += l.weight(n); }
		if (ok.isEmpty() || total <= 0) return null;
		long r = (long) (rng.nextDouble() * total);
		Name got = ok.get(ok.size() - 1);
		for (Name n : ok) { r -= l.weight(n); if (r < 0) { got = n; break; } }
		if (male) return shown(l, got);
		List<String> f = female(l, got);
		return f.isEmpty() ? shown(l, got) : f.get(rng.nextInt(f.size()));
	}

	/** The name as shown: the Engi's word in hex, split in half; anyone else's as written. */
	static String shown(List_ l, Name n) {
		if (!l.hex) return n.text;
		StringBuilder h = new StringBuilder();
		for (char c : n.text.toCharArray()) h.append(String.format("%02X", (int) c));
		return h.substring(0, h.length() / 2) + "-" + h.substring(h.length() / 2);
	}
	/**
	 * Every female form of a name (NamesT.female): itself for an F name; for a B name, the file's replace, or one per
	 * ending not left out (a name ending in X takes only the rest of an ending with an X; otherwise a name already
	 * ending in one is its own; a drop ending goes first; a final E drops before a vowel).
	 */
	static List<String> female(List_ l, Name n) {
		String s = shown(l, n);
		List<String> o = new ArrayList<String>();
		if (n.sex.equals("M")) return o;
		if (n.sex.equals("F")) { o.add(s); return o; }
		if (l.replFrom != null) { o.add(s.replace(l.replFrom, l.replTo)); return o; }
		if (l.suffix.isEmpty()) { o.add(s); return o; }
		String low = s.toLowerCase();
		boolean xRule = false;
		for (String e : l.suffix) if (e.contains("x") && low.endsWith("x")) xRule = true;
		if (!xRule) for (String e : l.suffix) if (low.endsWith(e)) { o.add(s); return o; }
		String base = s;
		for (String d : l.drop) if (low.endsWith(d)) { base = s.substring(0, s.length() - d.length()); break; }
		for (String e : l.suffix) {
			if (n.ex.contains(e)) continue;
			String b = base, end = e;
			if (b.toLowerCase().endsWith("x") && end.contains("x")) { end = end.substring(end.indexOf('x') + 1); if (end.isEmpty()) continue; }
			if (b.toLowerCase().endsWith("e") && "aeiou".indexOf(end.charAt(0)) >= 0) b = b.substring(0, b.length() - 1);
			if (!o.contains(b + end)) o.add(b + end);
		}
		return o;
	}

	// ---- reading ----

	static final class Name {
		String text, sex, rarity;
		final Set<String> ex = new HashSet<String>();
	}
	static final class List_ {
		final LinkedHashMap<String, Integer> levels = new LinkedHashMap<String, Integer>();
		List<String> suffix = new ArrayList<String>(), drop = new ArrayList<String>();
		String replFrom, replTo;
		boolean hex;
		final List<Name> names = new ArrayList<Name>();
		int weight(Name n) {
			Integer w = n.rarity == null ? null : levels.get(n.rarity);
			if (w == null) w = levels.isEmpty() ? 1 : levels.values().iterator().next(); // no rarity, or one the ladder lacks: the first level
			return Math.max(0, w);
		}
	}
	/** A file read: a race's one list, or the human file's first and last names. */
	static final class Lists { List_ one, first, last; }

	private static final Map<String, Lists> CACHE = new HashMap<String, Lists>();
	private static final Map<String, Long> STAMPS = new HashMap<String, Long>();

	/** A names file: the player's copy when it reads, else the jar's; cached until the copy changes. Null if neither reads. */
	static synchronized Lists lists(String file) {
		if (file == null) return null;
		long stamp = Lore.stamp(file);
		if (CACHE.containsKey(file) && STAMPS.get(file) == stamp) return CACHE.get(file);
		Lists got = null;
		File copy = Lore.copy(file);
		if (copy != null) {
			try { got = read(SafeFiles.read(copy)); }
			catch (Exception e) { Lore.problem("lore/" + file + " could not be read (" + e.getMessage() + "); the station's own names are used"); }
		}
		if (got == null) {
			try (InputStream in = CrewNames.class.getResourceAsStream("/homeplanet/resource/lore/" + file)) {
				if (in != null) got = read(readAll(in));
			} catch (Exception e) { org.slf4j.LoggerFactory.getLogger(CrewNames.class).warn("The station's own {} could not be read: {}", file, e.toString()); }
		}
		CACHE.put(file, got);
		STAMPS.put(file, stamp);
		return got;
	}
	static Lists read(byte[] bytes) throws Exception {
		DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
		f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		f.setExpandEntityReferences(false);
		javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
		b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
			@Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
		});
		Element root = b.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
		Lists out = new Lists();
		Element first = child(root, "first"), last = child(root, "last");
		if (first != null || last != null) {
			if (first == null || last == null) throw new IOException("a human file needs both <first> and <last>");
			out.first = list(first);
			out.last = list(last);
		} else out.one = list(root);
		return out;
	}
	private static List_ list(Element root) throws IOException {
		List_ l = new List_();
		NodeList lv = root.getElementsByTagName("level");
		for (int i = 0; i < lv.getLength(); i++) {
			Element e = (Element) lv.item(i);
			try { l.levels.put(e.getAttribute("name"), Integer.parseInt(e.getAttribute("weight").trim())); }
			catch (NumberFormatException x) { throw new IOException("level " + e.getAttribute("name") + " has no whole-number weight"); }
		}
		Element f = child(root, "female");
		if (f != null) {
			l.suffix = split(f.getAttribute("suffix"));
			l.drop = split(f.getAttribute("drop"));
			if (f.hasAttribute("replace")) { l.replFrom = f.getAttribute("replace"); l.replTo = f.getAttribute("with"); }
		}
		Element w = child(root, "write");
		l.hex = w != null && "hex".equals(w.getAttribute("as"));
		NodeList ns = root.getElementsByTagName("name");
		for (int i = 0; i < ns.getLength(); i++) {
			Element e = (Element) ns.item(i);
			Name n = new Name();
			n.text = e.getTextContent().trim();
			if (n.text.isEmpty()) continue;
			n.sex = e.hasAttribute("sex") ? e.getAttribute("sex").trim().toUpperCase() : "B";
			if (!n.sex.matches("[MFB]")) n.sex = "B";
			n.rarity = e.hasAttribute("rarity") ? e.getAttribute("rarity") : null;
			n.ex.addAll(split(e.getAttribute("ex")));
			l.names.add(n);
		}
		if (l.names.isEmpty()) throw new IOException("no names");
		return l;
	}
	private static Element child(Element e, String tag) {
		NodeList n = e.getElementsByTagName(tag);
		return n.getLength() == 0 ? null : (Element) n.item(0);
	}
	private static List<String> split(String s) {
		List<String> o = new ArrayList<String>();
		for (String p : s.split(",")) if (!p.trim().isEmpty()) o.add(p.trim().toLowerCase());
		return o;
	}
	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		byte[] b = new byte[8192];
		for (int n; (n = in.read(b)) > 0;) o.write(b, 0, n);
		return o.toByteArray();
	}
	/** For the tests: every name a list could give (shown forms and female forms). */
	public static List<String> every(String file) {
		Lists ls = lists(file);
		List<String> out = new ArrayList<String>();
		if (ls == null) return out;
		for (List_ l : ls.one != null ? Collections.singletonList(ls.one) : java.util.Arrays.asList(ls.first, ls.last))
			for (Name n : l.names) { out.add(shown(l, n)); out.addAll(female(l, n)); }
		return out;
	}
}
