package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import homeplanet.core.Event;
import homeplanet.core.EventLog;
import homeplanet.core.SafeFiles;
import homeplanet.core.Store;
import homeplanet.parser.XmlText;

/**
 * A ship's folder (Overhaul 6.0, Phase 2 step 13; docs/OVERHAUL-6.md §3.1 and §3.3): everything about her in one
 * place, so moving her is moving the folder.
 *
 * <pre>
 * shipyard/&lt;Name&gt;.&lt;id&gt;/
 *   &lt;Name&gt;.&lt;id&gt;.xml      her record: what the manifest and her side files hold today (§3.1), as attributes
 *                         and sections (fate, trade, journey, museum, borrowed, last, final, overwritten, owners, names)
 *   &lt;Name&gt;.&lt;id&gt;.sav      her FTL save, exactly as FTL wrote it
 *   &lt;Name&gt;.&lt;id&gt;.log      her log, two-line entries (CLAUDE.md "Log lines")
 *   versions/             her kept versions, named by their stamp; victory-, final-battle- and cloud- copies kept apart
 * </pre>
 *
 * The name in the folder's name is for people (cleaned for Windows and capped, docs/OVERHAUL-6.md §3.1); the id is
 * the key, and {@link #idOf} reads it from the folder. This is the store alone: the Vault moves onto it in the steps
 * after (each fleet converted on opening).
 */
public final class ShipStore {
	private static final Logger log = LoggerFactory.getLogger(ShipStore.class);
	private ShipStore() { }

	/** The most of her name that goes into folder and file names (the whole name lives in her record). */
	public static final int NAME_MAX = 32;
	public static final String VERSIONS = "versions";
	/** The special copies' prefixes in versions/: never pruned with the ordinary versions. */
	public static final String[] KEPT_PREFIXES = {"victory-", "final-battle-", "cloud-"};

	/** Her record: the attributes of her ship element, and her sections, each a set of key=value fields. */
	public static final class Record {
		public final String id;
		public String name = "", state = "docked";
		public boolean dlc, stranger;
		public String hash = "", marks = "", fresh = "";
		/** Her owners, the first her original (as her trade mark has them today), and her past names, oldest first. */
		public final List<String> owners = new ArrayList<String>(), pastNames = new ArrayList<String>();
		/** Her sections by name: fate, trade, journey, museum, borrowed, last, final, overwritten; anything else is kept as read. */
		public final Map<String, Properties> sections = new LinkedHashMap<String, Properties>();
		public Record(String id) { this.id = id; }
		/** A section, made if she has none. */
		public Properties section(String name) {
			Properties p = sections.get(name);
			if (p == null) sections.put(name, p = new Properties());
			return p;
		}
		public boolean has(String section) { return sections.containsKey(section) && !sections.get(section).isEmpty(); }
	}

	// ---- names ----

	/** Her folder's and files' stem: her name cleaned for Windows and capped, a dot, her id. */
	public static String stem(String name, String id) {
		String n = SafeFiles.safeName(name == null ? "" : name.trim()); // "ship" when she has no name
		if (n.length() > NAME_MAX) n = n.substring(0, NAME_MAX).trim();
		return n + "." + id;
	}
	/** The id a folder (or file stem) names: what follows its last dot, or null if it names none. */
	public static String idOf(File folder) {
		String n = folder.getName();
		int dot = n.lastIndexOf('.');
		return dot < 0 || dot == n.length() - 1 ? null : n.substring(dot + 1);
	}
	public static File folder(File parent, Record r) { return new File(parent, stem(r.name, r.id)); }
	public static File xml(File folder) { return new File(folder, folder.getName() + ".xml"); }
	public static File sav(File folder) { return new File(folder, folder.getName() + ".sav"); }
	public static File logFile(File folder) { return new File(folder, folder.getName() + ".log"); }
	public static File versions(File folder) { return new File(folder, VERSIONS); }

	// ---- the record ----

	/** Writes her record into her folder (made if need be), safely. Her save, log and versions are left as they are. */
	public static void write(File folder, Record r) throws IOException {
		if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not create " + folder);
		writeFile(xml(folder), r);
	}
	private static void writeFile(File to, Record r) throws IOException { SafeFiles.write(to, bytes(r)); }
	/** Her record as its file's bytes (for a package, 5.75). */
	public static byte[] bytes(Record r) {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
		sb.append("<!-- ").append(XmlText.attr(r.name)).append(": her record. Federation Home Planet rewrites this file; edit it by hand only when the station is closed. -->\r\n");
		sb.append("<ship version=\"1\" id=\"").append(XmlText.attr(r.id)).append("\" name=\"").append(XmlText.attr(r.name)).append("\" state=\"").append(XmlText.attr(r.state))
				.append("\" dlc=\"").append(r.dlc).append("\" hash=\"").append(XmlText.attr(r.hash == null ? "" : r.hash)).append("\"");
		if (r.marks != null && !r.marks.isEmpty()) sb.append(" marks=\"").append(XmlText.attr(r.marks)).append("\"");
		if (r.stranger) sb.append(" stranger=\"true\"");
		if (r.fresh != null && !r.fresh.isEmpty()) sb.append(" fresh=\"").append(XmlText.attr(r.fresh)).append("\"");
		sb.append(">\r\n");
		if (!r.owners.isEmpty()) {
			sb.append("\t<owners>\r\n");
			for (String o : r.owners) sb.append("\t\t<owner>").append(XmlText.text(o)).append("</owner>\r\n");
			sb.append("\t</owners>\r\n");
		}
		if (!r.pastNames.isEmpty()) {
			sb.append("\t<names>\r\n");
			for (String n : r.pastNames) sb.append("\t\t<name>").append(XmlText.text(n)).append("</name>\r\n");
			sb.append("\t</names>\r\n");
		}
		for (Map.Entry<String, Properties> e : r.sections.entrySet()) {
			if (e.getValue().isEmpty()) continue;
			sb.append("\t<").append(e.getKey());
			for (String k : new java.util.TreeSet<String>(e.getValue().stringPropertyNames())) {
				sb.append(" ").append(attrName(k)).append("=\"").append(XmlText.attr(e.getValue().getProperty(k))).append("\"");
			}
			sb.append("/>\r\n");
		}
		sb.append("</ship>\r\n");
		return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}
	/** A key as an attribute name: letters, digits, dots, dashes and underscores only (a space or a colon becomes an underscore). */
	private static String attrName(String k) { return k.replaceAll("[^A-Za-z0-9._-]", "_"); }

	/** Reads her record from her folder; null if the folder has none, or it can't be read (the log says why). */
	public static Record read(File folder) { return readFile(xml(folder), idOf(folder)); }
	/** A record kept on its own, as a file named by a stem in a folder (the Cargo Hold's at the career's root). */
	public static Record read(File dir, String stem) { return readFile(new File(dir, stem + ".xml"), stem); }
	public static void write(File dir, String stem, Record r) throws IOException { writeFile(new File(dir, stem + ".xml"), r); }
	private static Record readFile(File f, String idIfNone) {
		if (!f.isFile()) return null;
		try { return parse(SafeFiles.read(f), idIfNone); }
		catch (Exception e) { log.warn("Could not read {}: {}", f, e.toString()); return null; }
	}
	/** A record from its file's bytes (a package's, 5.75): the id given stands if the record names none. */
	public static Record parse(byte[] xml, String idIfNone) throws IOException {
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));
			Element ship = doc.getDocumentElement();
			if (!"ship".equals(ship.getTagName())) throw new IOException("not a ship record");
			String id = ship.getAttribute("id");
			if (id.isEmpty()) id = idIfNone;
			Record r = new Record(id);
			r.name = ship.getAttribute("name");
			r.state = ship.hasAttribute("state") ? ship.getAttribute("state") : "docked";
			r.dlc = "true".equals(ship.getAttribute("dlc"));
			r.hash = ship.getAttribute("hash");
			r.marks = ship.getAttribute("marks");
			r.stranger = "true".equals(ship.getAttribute("stranger"));
			r.fresh = ship.getAttribute("fresh");
			NodeList kids = ship.getChildNodes();
			for (int i = 0; i < kids.getLength(); i++) {
				if (!(kids.item(i) instanceof Element)) continue;
				Element e = (Element) kids.item(i);
				String tag = e.getTagName();
				if (tag.equals("owners")) { for (Element o : children(e, "owner")) r.owners.add(o.getTextContent()); continue; }
				if (tag.equals("names")) { for (Element n : children(e, "name")) r.pastNames.add(n.getTextContent()); continue; }
				Properties p = r.section(tag);
				NamedNodeMap attrs = e.getAttributes();
				for (int a = 0; a < attrs.getLength(); a++) { Node n = attrs.item(a); p.setProperty(n.getNodeName(), n.getNodeValue()); }
			}
			return r;
		} catch (Exception e) {
			throw new IOException("not a ship record: " + e.getMessage(), e);
		}
	}
	private static List<Element> children(Element e, String tag) {
		List<Element> out = new ArrayList<Element>();
		NodeList kids = e.getChildNodes();
		for (int i = 0; i < kids.getLength(); i++) if (kids.item(i) instanceof Element && ((Element) kids.item(i)).getTagName().equals(tag)) out.add((Element) kids.item(i));
		return out;
	}

	// ---- her folder ----

	/** Every ship folder under a parent (one with a record in it), in name order. */
	public static List<File> folders(File parent) {
		List<File> out = new ArrayList<File>();
		File[] fs = parent.listFiles();
		if (fs == null) return out;
		java.util.Arrays.sort(fs);
		for (File f : fs) if (f.isDirectory() && xml(f).isFile()) out.add(f);
		return out;
	}
	/**
	 * Renames her folder and the files in it that carry her stem (her record, save and log) for a new name; her id
	 * never changes. Returns the folder as it is after. A rename refused (a file in use) throws, with nothing changed.
	 */
	public static File rename(File folder, Record r, String newName) throws IOException {
		r.pastNames.add(r.name);
		r.name = newName;
		return renameTo(folder, r);
	}
	/** Renames her folder and files to the record's name (the record written into it); the caller keeps her past names. */
	public static File renameTo(File folder, Record r) throws IOException {
		String was = folder.getName(), now = stem(r.name, r.id);
		if (now.equals(was)) { write(folder, r); return folder; }
		for (String ext : new String[] {".xml", ".sav", ".log"}) {
			File f = new File(folder, was + ext);
			if (f.isFile()) SafeFiles.replace(f, new File(folder, now + ext));
		}
		File to = new File(folder.getParentFile(), now);
		SafeFiles.replace(folder, to);
		write(to, r);
		return to;
	}
	/** Moves her folder under another parent (the Junkyard, the memorial), as one rename. */
	public static File move(File folder, File parent) throws IOException {
		if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
		File to = new File(parent, folder.getName());
		if (to.exists()) throw new IOException(to + " is already there");
		SafeFiles.replace(folder, to);
		return to;
	}

	// ---- her log ----

	/** An entry in her log: the event's two lines, with her fields first. */
	public static void log(Vault v, File folder, Record r, Event e) {
		Event who = Event.of(e.kind).put("ship", r.name + "." + r.id).put("ship_name", r.name).put("ship_id", r.id).put("ship_state", r.state).putAll(e).human(e.human());
		EventLog.write(v, logFile(folder), who);
	}
	/** Her log's entries, oldest first. */
	public static List<EventLog.Entry> entries(File folder) { return EventLog.read(logFile(folder)); }

	// ---- her versions ----

	/** Her kept versions (the ordinary ones; not the special copies), oldest first: by the file's time, the name's order deciding a tie (a name from before UTC stamps sorts by when it was kept). */
	public static List<File> versions(File folder, boolean special) {
		List<File> out = new ArrayList<File>();
		File[] fs = versions(folder).listFiles();
		if (fs == null) return out;
		for (File f : fs) if (f.isFile() && f.getName().endsWith(".sav") && isSpecial(f) == special) out.add(f);
		java.util.Collections.sort(out, new java.util.Comparator<File>() { public int compare(File a, File b) { int t = Long.compare(a.lastModified(), b.lastModified()); return t != 0 ? t : order(a).compareTo(order(b)); } });
		return out;
	}
	/** A version's place in time, from her name: the stamp, then the counter as a number (so -10 follows -9, not -1). */
	public static String order(File f) {
		String n = f.getName().replace(".sav", "");
		int dash = n.indexOf('-', 9); // past the date-time's own dash
		int count = 1;
		if (dash > 0) { try { count = Integer.parseInt(n.substring(dash + 1)); } catch (NumberFormatException e) { } n = n.substring(0, dash); }
		return n + String.format("%06d", count);
	}
	public static boolean isSpecial(File f) {
		for (String p : KEPT_PREFIXES) if (f.getName().startsWith(p)) return true;
		return false;
	}
	/** Keeps a copy of her save as a version, named by the stamp (UTC, so the order survives clock changes), a counter after it when two fall in a second. */
	public static File keepVersion(File folder, byte[] save, String prefix) throws IOException {
		File f = versionFile(folder, prefix);
		SafeFiles.write(f, save);
		return f;
	}
	/** The name her next version gets (not written: for a journal note that writes it). */
	public static File versionFile(File folder, String prefix) throws IOException {
		File dir = versions(folder);
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		String stamp;
		synchronized (STAMP) { stamp = STAMP.format(new java.util.Date()); }
		String base = (prefix == null ? "" : prefix) + stamp;
		// the counter follows the highest one there, not the first free name: a number pruning freed would sort as old
		int next = 1;
		File[] fs = dir.listFiles();
		if (fs != null) for (File x : fs) {
			String n = x.getName();
			if (!n.startsWith(base) || !n.endsWith(".sav")) continue;
			String rest = n.substring(base.length(), n.length() - 4);
			int c = rest.isEmpty() ? 1 : rest.startsWith("-") ? count(rest.substring(1)) : 0;
			if (c > next) next = c;
			if (c == next && c >= 1) next = c + 1;
		}
		return new File(dir, base + (next == 1 ? "" : "-" + next) + ".sav");
	}
	private static int count(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } }
	private static final java.text.SimpleDateFormat STAMP = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss");
	static { STAMP.setTimeZone(java.util.TimeZone.getTimeZone("UTC")); }
	/** Prunes her ordinary versions to the newest {@code keep}; the special copies are never touched. */
	public static void prune(File folder, int keep) {
		List<File> all = versions(folder, false);
		for (int i = 0; i < all.size() - keep; i++) if (!all.get(i).delete()) log.warn("Could not prune {}", all.get(i));
	}

	// ---- from today's files ----

	/**
	 * Her record built from what the station keeps today: her manifest entry and the side files in history/&lt;id&gt;/
	 * (fate.txt, traded.txt, journey.txt, museum.txt, borrowed.txt, voyage.txt, final-battle.txt, overwritten.txt).
	 * The conversion's first half; nothing is moved.
	 */
	public static Record fromToday(Vault v, Ship s) {
		Record r = new Record(s.id);
		r.name = s.name == null ? "" : s.name;
		r.state = s.state == null ? "docked" : s.state.key;
		r.dlc = s.dlc; r.hash = s.hash == null ? "" : s.hash; r.marks = s.marks == null ? "" : s.marks; r.stranger = s.stranger; r.fresh = s.fresh == null ? "" : s.fresh;
		File dir = v.historyOf(s);
		for (String[] side : new String[][] {{"trade", "traded.txt"}, {"journey", "journey.txt"}, {"museum", "museum.txt"}, {"borrowed", "borrowed.txt"},
				{"last", "voyage.txt"}, {"final", "final-battle.txt"}, {"overwritten", "overwritten.txt"}}) {
			File f = new File(dir, side[1]);
			if (f.isFile()) r.section(side[0]).putAll(Store.read(f));
		}
		File fate = new File(dir, "fate.txt");
		if (fate.isFile()) {
			try {
				String[] lines = new String(SafeFiles.read(fate), StandardCharsets.UTF_8).split("\r?\n");
				Properties p = r.section("fate");
				if (lines.length > 0) p.setProperty("kind", lines[0].trim());
				if (lines.length > 1) p.setProperty("name", lines[1].trim());
				if (lines.length > 2) p.setProperty("detail", lines[2].trim());
			} catch (IOException e) { log.warn("Could not read {}: {}", fate, e.toString()); }
		}
		Properties trade = r.sections.get("trade");
		if (trade != null) { // her owners as her trade mark knows them: the original first, then who sent her
			String original = trade.getProperty("original", "").trim(), from = trade.getProperty("from", "").trim();
			if (!original.isEmpty()) r.owners.add(original);
			if (!from.isEmpty() && !from.equals(original)) r.owners.add(from);
		}
		return r;
	}
}
