package homeplanet.core;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The words the station writes from data (6.0 step 9a, 5.89): XML files keyed by event kind, with {field} tokens filled
 * from the event's machine line. The jar carries the defaults (homeplanet/resource/lore/); a copy in lore/ beside the
 * program wins, entry by entry. An entry of the copy that breaks a hard rule or a voice rule is left out, and the jar's
 * words stand; one that names a field the event hasn't got is passed over when it's written, so a raw {token} never
 * reaches the player. The station always starts: what was left out is named in the debug log, by file, line and rule.
 * Since 5.991 the letters (letters.xml), the expedition words (expeditions.xml) and the accolades and deeds (deeds.xml)
 * live here too; the first two have shapes of their own, read by their readers (parser/Transmissions, parser/Assignments)
 * with this class's rules, jar, copy and problems.
 *
 * <pre>
 *   &lt;lore&gt;
 *     &lt;entry kind="HOLD_FILE"&gt;The Cargo Hold's inventory was written up in a new ledger.&lt;/entry&gt;
 *     &lt;entry kind="CREW_MOVE" when="reason=cargo_bay_save"&gt;{crew_name} was transferred to the {to_name}.&lt;/entry&gt;
 *   &lt;/lore&gt;
 * </pre>
 *
 * An entry with {@code when} ("field=value", several joined by "&amp;") is used only when the event's fields match; the
 * first match in the file wins, so the particular ones go before the plain one. The mechanics are Prime's; the words
 * are McCarthy's (heromedel, 5.82): entries move here from the Java one at a time, and the Java's own words stay as the
 * last fallback.
 */
public final class Lore {
	private static final Logger log = LoggerFactory.getLogger(Lore.class);
	private Lore() { }

	/** The station log's human lines: every event's second line. */
	public static final String STATION_LOG = "logs/station-log.xml";
	/** The Federation's letters (parser/Transmissions reads them, letter by letter). */
	public static final String LETTERS = "letters.xml";
	/** The expedition reports' words (parser/Assignments reads them, key by key). */
	public static final String EXPEDITIONS = "expeditions.xml";
	/** The rank letters' accolades and the achievements told as deeds (parser/Accolades reads them, as entries). */
	public static final String DEEDS = "deeds.xml";
	static final String JAR = "/homeplanet/resource/lore/";

	/** The player's lore folder: beside the program (a test may point it elsewhere). */
	public static File dir() {
		String d = System.getProperty("homeplanet.loreDir");
		return d != null ? new File(d) : new File(HomePlanet.appDir(), "lore");
	}

	/** One entry: its kind, its condition, its words, and where it came from (for the check's messages). */
	public static final class Entry {
		public final String kind, when, words, source;
		public final int line;
		Entry(String kind, String when, String words, String source, int line) { this.kind = kind; this.when = when; this.words = words; this.source = source; this.line = line; }
		boolean matches(Event e) {
			if (when == null || when.isEmpty()) return true;
			for (String c : when.split("&")) {
				c = c.trim();
				int eq = c.indexOf('=');
				if (eq < 0) { // "field": the event has it; "!field": it hasn't (a trade with the Cargo Hold has no partner_id, say)
					boolean not = c.startsWith("!");
					if ((e.get(not ? c.substring(1).trim() : c) != null) == not) return false;
					continue;
				}
				String v = e.get(c.substring(0, eq).trim());
				if (v == null || !v.equals(c.substring(eq + 1).trim())) return false;
			}
			return true;
		}
	}

	private static final Map<String, List<Entry>> CACHE = new LinkedHashMap<String, List<Entry>>();
	private static final Map<String, Long> STAMPS = new LinkedHashMap<String, Long>();
	private static final List<String> PROBLEMS = new ArrayList<String>();

	/** A file's entries in force: the copy's good ones first, then the jar's (so a copy's entry wins). */
	public static synchronized List<Entry> entries(String file) {
		File copy = new File(dir(), file);
		long stamp = copy.isFile() ? copy.lastModified() * 31 + copy.length() : -1;
		Long had = STAMPS.get(file);
		if (had != null && had == stamp && CACHE.containsKey(file)) return CACHE.get(file);
		List<Entry> out = new ArrayList<Entry>();
		List<String> problems = new ArrayList<String>();
		if (copy.isFile()) {
			try {
				for (Entry e : parse(SafeFiles.read(copy), "lore/" + file)) {
					String why = broken(e);
					if (why == null) out.add(e);
					else problems.add("lore/" + file + ", line " + e.line + " (" + e.kind + "): " + why + "; the station's own words are used");
				}
			} catch (IOException x) {
				problems.add("lore/" + file + " could not be read (" + x.getMessage() + "); the station's own words are used");
			}
		}
		out.addAll(jar(file));
		for (String p : problems) problem(p);
		CACHE.put(file, Collections.unmodifiableList(out));
		STAMPS.put(file, stamp);
		return CACHE.get(file);
	}
	private static List<Entry> jar(String file) {
		byte[] b = jarBytes(file);
		if (b == null) return new ArrayList<Entry>();
		try {
			return parse(b, "jar " + file);
		} catch (IOException e) {
			log.error("The station's own words in {} could not be read: {}", file, e.toString());
			return new ArrayList<Entry>();
		}
	}
	/** A lore file as the jar carries it, or null (the readers of letters and expedition words parse their own). */
	public static byte[] jarBytes(String file) {
		InputStream in = Lore.class.getResourceAsStream(JAR + file);
		if (in == null) return null;
		try {
			java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
			return b.toByteArray();
		} catch (IOException e) {
			log.error("The station's own words in {} could not be read: {}", file, e.toString());
			return null;
		} finally {
			try { in.close(); } catch (IOException e) { }
		}
	}
	/** The player's copy of a lore file, or null if there is none. */
	public static File copy(String file) {
		File f = new File(dir(), file);
		return f.isFile() ? f : null;
	}
	/** A stamp that changes when the player's copy does (-1 with none): a reader reads its file again when it moves. */
	public static long stamp(String file) {
		File f = copy(file);
		return f == null ? -1 : f.lastModified() * 31 + f.length();
	}
	/** Notes a problem with a player's copy, for the debug log and {@link #problems()}: once each. */
	public static void problem(String p) {
		synchronized (PROBLEMS) { if (!PROBLEMS.contains(p)) { PROBLEMS.add(p); log.warn("Lore: {}", p); } }
	}

	/** The words for this event from a lore file, its tokens filled; null when no entry fits (the caller's own words stand). */
	public static String words(String file, Event e) {
		for (Entry x : entries(file)) {
			if (!x.kind.equals(e.kind) || !x.matches(e)) continue;
			String filled = fill(x.words, e);
			if (filled != null) return filled;
		}
		return null;
	}
	/**
	 * An event's human line: the station log's lore entry for it, or the words its writer gave. An old log's entry read in
	 * keeps its old line exactly (6.0 §3.5), and a received ship's entry the words her own station gave it.
	 */
	public static String human(Event e) {
		if ("true".equals(e.get("converted")) || e.get("received_from") != null) return e.human();
		String w = words(STATION_LOG, e);
		return w != null ? w : e.human();
	}

	/**
	 * An entry already written, told in the station log's words as a reader shows it (6.05): worded afresh from its
	 * fields, so an entry from before the words were in lore/ reads like a new one; its own line when no entry's words
	 * fit (an old log's line read in with nothing but its headline, a kind with no words) or it came with a received ship.
	 * The stored line is never changed.
	 */
	public static String told(EventLog.Entry x) {
		if (x.get("received_from") != null) return x.human;
		Event e = Event.of(x.kind);
		for (String[] kv : x.fields()) e.put(kv[0], kv[1]);
		String w = words(STATION_LOG, e);
		return w != null ? w : x.human;
	}

	private static final Pattern TOKEN = Pattern.compile("\\{([a-z0-9_.]+)([+#]?)\\}");
	/**
	 * The words with every {field} filled from the event; null if one names a field the event hasn't got. {field+} is
	 * every value of a repeated field ("Ash, Bob and Cy": an expedition's crew); {field#} a count, up to ten in words
	 * ("two ships", docs/STYLE.md); "the {field}" is a ship's name by the
	 * station's one rule, never "the The Adjudicator" (ShipNames.the).
	 */
	static String fill(String words, Event e) {
		words = homeplanet.model.Words.ship(words); // {she}, {her}: the Ship Pronoun setting (6.42)
		Matcher m = TOKEN.matcher(words);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String v = m.group(2).equals("+") ? list(e.all(m.group(1))) : m.group(2).equals("#") ? count(e.get(m.group(1))) : e.get(m.group(1));
			if (v == null) return null;
			m.appendReplacement(sb, Matcher.quoteReplacement(v));
			int end = sb.length() - v.length(); // where the value begins
			if (end >= 4 && sb.substring(end - 4, end).equalsIgnoreCase("the ") && (end == 4 || !Character.isLetter(sb.charAt(end - 5)))) { // "the {ship_name}": the station's one rule
				boolean cap = sb.charAt(end - 4) == 'T';
				sb.setLength(end - 4);
				sb.append(cap ? homeplanet.parser.ShipNames.theStart(v) : homeplanet.parser.ShipNames.the(v));
			}
		}
		m.appendTail(sb);
		String s = homeplanet.model.Words.cap(sb.toString()); // a line can begin with a count in words: "Three items were sold"
		return s.contains("{") || s.contains("}") ? null : s;
	}
	/** A count as the style guide says it, up to ten in words ({count#}: "two ships"); the value as it is if it isn't a number; null for none. */
	static String count(String v) {
		if (v == null) return null;
		try { return homeplanet.model.Words.number(Integer.parseInt(v.trim())); } catch (NumberFormatException x) { return v; }
	}
	/** Every value, as a reader says a list: "Ash", "Ash and Bob", "Ash, Bob and Cy"; null for none. */
	static String list(List<String> v) {
		if (v.isEmpty()) return null;
		if (v.size() == 1) return v.get(0);
		return String.join(", ", v.subList(0, v.size() - 1)) + " and " + v.get(v.size() - 1);
	}

	/**
	 * Why an entry can't be used, or null: the hard rules (the Rebel Flagship never destroyed; time never told in
	 * beacons) and the voice rules a word can be checked for (the rebellion and the rebels in lower case, never "Home
	 * World", The Home Planet Station and The Federation Home Planet with a capital T). Checked on the player's copy; the jar's own words are held to it by the harness.
	 */
	public static String broken(Entry e) {
		String why = rule(e.words);
		return why != null ? why : fillCheck(e.words);
	}
	/** The hard and voice rules alone, for any words (a letter, an expedition line), or null when they keep them. */
	public static String rule(String w) {
		String low = w.toLowerCase();
		if (w.trim().isEmpty()) return "no words";
		if (low.contains("flagship") && low.matches("(?s).*\\b(destroy(ed|s)?|killed|blown up|blew up|wrecked|defeated)\\b.*")) return "hard rule 1: the Rebel Flagship is never destroyed (the war goes on)";
		if (low.matches("(?s).*(\\b\\d+|\\b(one|two|three|few|several|many))\\s+beacons?\\b.*") || low.matches("(?s).*\\bbeacons?\\s+(later|ago|passed|from now|since)\\b.*")) return "hard rule 2: time is never told in beacons";
		if (low.matches("(?s).*\\{[a-z0-9_.]*beacon[a-z0-9_.]*\\}.*")) return "hard rule 2: time is never told in beacons (a beacon count in a token)";
		// a title is the fault, not grammar: a sentence may start with "Rebels" (heromedel, 6.41)
		String asTitle = w.replace("Rebel Flagship", "").replaceAll("(^|[.!?]\\s+|\\n\\s*|\"\\s*)Rebel", "$1rebel");
		if (asTitle.matches("(?s).*\\bRebellion\\b.*") || asTitle.matches("(?s).*\\bRebels?\\b.*")) return "voice: the rebellion and the rebels are never capitalised as a title (only the Rebel Flagship)";
		if (w.contains("Home World")) return "voice: never \"Home World\""; // "FHP" is allowed: never a rule of heromedel's (6.04)
		if (w.matches("(?s).*\\bthe (Home Planet Station|Federation Home Planet)\\b.*")) return "voice: The Home Planet Station and The Federation Home Planet take a capital T, even mid-sentence";
		return null;
	}
	private static String fillCheck(String w) {
		String bare = TOKEN.matcher(homeplanet.model.Words.ship(w)).replaceAll("");
		return bare.contains("{") || bare.contains("}") ? "a { or } that isn't a {field}" : null;
	}

	/** Every problem found so far in the player's copies (the start-up check reads every file the jar has). */
	public static List<String> problems() { synchronized (PROBLEMS) { return new ArrayList<String>(PROBLEMS); } }
	/** The start-up check: every lore file read once, so a broken copy is named in the debug log from the start. Never throws. */
	public static List<String> check() {
		for (String f : ENTRY_FILES) {
			try { entries(f); } catch (RuntimeException e) { log.warn("Lore: {} could not be checked: {}", f, e.toString()); }
		}
		return problems();
	}
	/** The lore files the jar carries. */
	public static final String[] FILES = {STATION_LOG, LETTERS, EXPEDITIONS, DEEDS};
	/** Those made of entries (the letters and the expedition words have shapes of their own, checked by their readers). */
	public static final String[] ENTRY_FILES = {STATION_LOG, DEEDS};

	/** Parses a lore file, each entry with its line number. */
	static List<Entry> parse(byte[] bytes, String source) throws IOException {
		Element root;
		try {
			DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
			f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			f.setExpandEntityReferences(false);
			root = quiet(f.newDocumentBuilder()).parse(new ByteArrayInputStream(bytes)).getDocumentElement();
		} catch (Exception e) {
			throw new IOException("broken XML: " + e.getMessage(), e);
		}
		String text = new String(bytes, StandardCharsets.UTF_8);
		List<Entry> out = new ArrayList<Entry>();
		int from = 0;
		for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (!(n instanceof Element) || !((Element) n).getTagName().equals("entry")) continue;
			Element x = (Element) n;
			String kind = Event.normalize(x.getAttribute("kind"));
			if (kind.isEmpty()) continue;
			int at = text.indexOf("kind=\"" + x.getAttribute("kind") + "\"", from);
			int line = at < 0 ? 0 : lineOf(text, at);
			if (at >= 0) from = at + 1;
			out.add(new Entry(kind, x.hasAttribute("when") ? x.getAttribute("when") : null, x.getTextContent().trim().replaceAll("\\s+", " "), source, line));
		}
		return out;
	}
	private static int lineOf(String text, int at) {
		int n = 1;
		for (int i = 0; i < at; i++) if (text.charAt(i) == '\n') n++;
		return n;
	}

	/**
	 * Makes lore/ beside the program if it isn't there: a readme saying how a copy works, and the jar's files under
	 * lore/defaults/ (both rewritten each start when they've changed; defaults/ is never read) to copy entries from. Never throws.
	 */
	public static void prepare() {
		File d = dir();
		try {
			File defaults = new File(d, "defaults");
			List<String> all = new ArrayList<String>(java.util.Arrays.asList(FILES));
			all.addAll(java.util.Arrays.asList(homeplanet.parser.CrewNames.FILES)); // the crew names (6.33)
			for (String f : all) {
				InputStream in = Lore.class.getResourceAsStream(JAR + f);
				if (in == null) continue;
				java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
				byte[] buf = new byte[8192];
				try { for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n); } finally { in.close(); }
				File to = new File(defaults, f);
				if (to.isFile() && java.util.Arrays.equals(SafeFiles.read(to), b.toByteArray())) continue;
				if (!to.getParentFile().isDirectory() && !to.getParentFile().mkdirs()) throw new IOException("Could not create " + to.getParentFile());
				SafeFiles.write(to, b.toByteArray());
			}
			File readme = new File(d, "readme.txt"); // the station's own, kept up to date like defaults/
			if (!readme.isFile() || !README.equals(new String(SafeFiles.read(readme), java.nio.charset.StandardCharsets.UTF_8))) SafeFiles.writeText(readme, README, false);
		} catch (IOException e) {
			log.warn("Could not prepare the lore folder {}: {}", d, e.toString());
		}
	}
	static final String README = "The words The Home Planet Station writes: its log's lines, The Federation Home Planet's letters, the expedition\r\n"
			+ "reports and the rank letters' accolades.\r\n"
			+ "\r\n"
			+ "defaults/ holds the station's own words, rewritten each time it starts: read them, never edit them there.\r\n"
			+ "To change something, copy it into a file of the same name here and edit the copy:\r\n"
			+ "  logs/station-log.xml  the log's lines, entry by entry;\r\n"
			+ "  letters.xml           the letters, letter by letter (copy a <letter> whole);\r\n"
			+ "  expeditions.xml       the expedition reports' words: a copy's lines take the place of the station's own\r\n"
			+ "                        lines with the same marks;\r\n"
			+ "  deeds.xml             the accolades and deeds, entry by entry.\r\n"
			+ "Anything the copy leaves out keeps the station's own words.\r\n"
			+ "names/ holds the crew names, a file per race (Settings, Custom Name Lists Per Race): a copy replaces the station's\r\n"
			+ "whole list for that race; each file's top says how it is written.\r\n"
			+ "{field} fills in a value; each file's own notes say which ones it has.\r\n"
			+ "{she}, {her} (her crew), {her_obj} (flies her), {hers}, {herself}, {She} and {Her} speak of a ship as the Ship\r\n"
			+ "Pronoun setting says (Settings, General: her, him or it).\r\n"
			+ "Something that breaks a rule (the Rebel Flagship is never destroyed, the rebels are never capitalised, a {field}\r\n"
			+ "that isn't there) is left out, and the debug log says which file, which entry and which rule.\r\n";
	/** A builder that keeps quiet: its errors come back as the exception, not printed to the console. */
	private static javax.xml.parsers.DocumentBuilder quiet(javax.xml.parsers.DocumentBuilder b) {
		b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
			@Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
		});
		return b;
	}
}
