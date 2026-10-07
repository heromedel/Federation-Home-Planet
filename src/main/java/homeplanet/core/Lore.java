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
				int eq = c.indexOf('=');
				if (eq < 0) return false;
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
		synchronized (PROBLEMS) {
			for (String p : problems) { if (!PROBLEMS.contains(p)) PROBLEMS.add(p); log.warn("Lore: {}", p); }
		}
		CACHE.put(file, Collections.unmodifiableList(out));
		STAMPS.put(file, stamp);
		return CACHE.get(file);
	}
	private static List<Entry> jar(String file) {
		InputStream in = Lore.class.getResourceAsStream(JAR + file);
		if (in == null) return new ArrayList<Entry>();
		try {
			java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
			return parse(b.toByteArray(), "jar " + file);
		} catch (IOException e) {
			log.error("The station's own words in {} could not be read: {}", file, e.toString());
			return new ArrayList<Entry>();
		} finally {
			try { in.close(); } catch (IOException e) { }
		}
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
	/** An event's human line: the station log's lore entry for it, or the words its writer gave. */
	public static String human(Event e) {
		String w = words(STATION_LOG, e);
		return w != null ? w : e.human();
	}

	private static final Pattern TOKEN = Pattern.compile("\\{([a-z0-9_.]+)\\}");
	/** The words with every {field} filled from the event; null if one names a field the event hasn't got. */
	static String fill(String words, Event e) {
		Matcher m = TOKEN.matcher(words);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String v = e.get(m.group(1));
			if (v == null) return null;
			m.appendReplacement(sb, Matcher.quoteReplacement(v));
		}
		m.appendTail(sb);
		String s = sb.toString();
		return s.contains("{") || s.contains("}") ? null : s;
	}

	/**
	 * Why an entry can't be used, or null: the hard rules (the Rebel Flagship never destroyed; time never told in
	 * beacons) and the voice rules a word can be checked for (the rebellion and the rebels in lower case, never "Home
	 * World" or "FHP"). Checked on the player's copy; the jar's own words are held to it by the harness.
	 */
	public static String broken(Entry e) {
		String w = e.words, low = w.toLowerCase();
		if (w.trim().isEmpty()) return "no words";
		if (low.contains("flagship") && low.matches("(?s).*\\b(destroy(ed|s)?|killed|blown up|blew up|wrecked|defeated)\\b.*")) return "hard rule 1: the Rebel Flagship is never destroyed (the war goes on)";
		if (low.matches("(?s).*(\\b\\d+|\\b(one|two|three|few|several|many))\\s+beacons?\\b.*") || low.matches("(?s).*\\bbeacons?\\s+(later|ago|passed|from now|since)\\b.*")) return "hard rule 2: time is never told in beacons";
		if (low.matches("(?s).*\\{[a-z0-9_.]*beacon[a-z0-9_.]*\\}.*")) return "hard rule 2: time is never told in beacons (a beacon count in a token)";
		if (w.matches("(?s).*\\bRebellion\\b.*") || w.replace("Rebel Flagship", "").matches("(?s).*\\bRebels?\\b.*")) return "voice: the rebellion and the rebels are never capitalised (only the Rebel Flagship)";
		if (w.contains("Home World") || w.matches("(?s).*\\bFHP\\b.*")) return "voice: never \"Home World\" or \"FHP\"";
		if (fillCheck(w) != null) return fillCheck(w);
		return null;
	}
	private static String fillCheck(String w) {
		String bare = TOKEN.matcher(w).replaceAll("");
		return bare.contains("{") || bare.contains("}") ? "a { or } that isn't a {field}" : null;
	}

	/** Every problem found so far in the player's copies (the start-up check reads every file the jar has). */
	public static List<String> problems() { synchronized (PROBLEMS) { return new ArrayList<String>(PROBLEMS); } }
	/** The start-up check: every lore file read once, so a broken copy is named in the debug log from the start. Never throws. */
	public static List<String> check() {
		for (String f : FILES) {
			try { entries(f); } catch (RuntimeException e) { log.warn("Lore: {} could not be checked: {}", f, e.toString()); }
		}
		return problems();
	}
	/** The lore files the jar carries. */
	public static final String[] FILES = {STATION_LOG};

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
	 * lore/defaults/ (rewritten each start, never read) to copy entries from. Never throws.
	 */
	public static void prepare() {
		File d = dir();
		try {
			File defaults = new File(d, "defaults");
			for (String f : FILES) {
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
			File readme = new File(d, "readme.txt");
			if (!readme.isFile()) SafeFiles.writeText(readme, README, false);
		} catch (IOException e) {
			log.warn("Could not prepare the lore folder {}: {}", d, e.toString());
		}
	}
	static final String README = "The words The Home Planet Station writes from what happens: its log's lines, and in time its letters and deeds.\r\n"
			+ "\r\n"
			+ "defaults/ holds the station's own words, rewritten each time it starts: read them, never edit them there.\r\n"
			+ "To change an entry, copy it into a file of the same name here (logs/station-log.xml, say) and edit the copy.\r\n"
			+ "The copy wins entry by entry; anything it leaves out keeps the station's own words.\r\n"
			+ "{field} fills in a value from the event (its fields are listed in the station's own log, logs/events.log).\r\n"
			+ "An entry that breaks a rule (the Rebel Flagship is never destroyed, time is never told in beacons, the rebels\r\n"
			+ "are never capitalised) is left out, and the debug log says which file, line and rule.\r\n";
	/** A builder that keeps quiet: its errors come back as the exception, not printed to the console. */
	private static javax.xml.parsers.DocumentBuilder quiet(javax.xml.parsers.DocumentBuilder b) {
		b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
			@Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
		});
		return b;
	}
}
