package homeplanet.core;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one way the station reads and writes its small properties files (Overhaul 6.0, Phase 1 step 7): a missing file
 * reads empty, writes go through {@link SafeFiles} (a temporary file, synced, then moved into place), and the numbers
 * are read with a default. The format on disk is {@link Properties}' own: its plain text, or its XML for a file whose
 * name ends in .xml (6.0's folders, 5.85), the keys in order. Each file has one owning class, which keeps its name and is
 * the only one to open it.
 */
public final class Store {
	private static final Logger log = LoggerFactory.getLogger(Store.class);
	private Store() { }

	/**
	 * A small file of a fleet's by its stem: stem.xml (5.86), or the 5.x stem.txt while a fleet not yet opened by 5.86
	 * still has it (another career's, say). Written by its name, so either kind stays the kind it is.
	 */
	public static File file(File dir, String stem) {
		File xml = new File(dir, stem + ".xml"), txt = new File(dir, stem + ".txt");
		return !xml.exists() && txt.isFile() ? txt : xml;
	}
	/** The file's properties; empty if it doesn't exist, and empty with a warning in the log if it can't be read. */
	public static Properties read(File f) {
		try { return load(f); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); return new Properties(); }
	}
	/** As {@link #read}, but an unreadable file is the caller's problem (never written back from a failed read). */
	public static Properties load(File f) throws IOException {
		Properties p = new Properties();
		if (f != null && f.isFile()) return parse(SafeFiles.read(f));
		return p;
	}
	/** Properties from bytes already in hand (a package entry, a file read for its hash). */
	public static Properties parse(byte[] bytes) throws IOException {
		Properties p = new Properties();
		if (bytes == null) return p;
		String text = new String(bytes, StandardCharsets.UTF_8);
		if (text.startsWith("<?xml")) p.loadFromXML(new java.io.ByteArrayInputStream(bytes));
		else p.load(new StringReader(text));
		return p;
	}
	/** Writes the properties to the file (its folder made if need be), safely, with this comment at the top. */
	public static void write(File f, Properties p, String comment) throws IOException {
		File dir = f.getAbsoluteFile().getParentFile();
		if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		byte[] b = bytes(f, p, comment);
		if (f.isFile() && java.util.Arrays.equals(SafeFiles.read(f), b)) return; // unchanged: not written again (6.01)
		SafeFiles.write(f, b);
	}
	/** The properties as this file's bytes: its XML if the name ends in .xml, else the plain text. */
	public static byte[] bytes(File f, Properties p, String comment) throws IOException {
		return f.getName().endsWith(".xml") ? xml(p, comment) : bytes(p, comment);
	}
	/** The properties as {@link Properties#loadFromXML} reads them, the keys in order (numbers by their value), so a file reads top to bottom. */
	public static byte[] xml(Properties p, String comment) {
		java.util.List<String> keys = new java.util.ArrayList<String>(p.stringPropertyNames());
		java.util.Collections.sort(keys, KEY_ORDER);
		StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\r\n<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\r\n<properties>\r\n");
		if (comment != null) sb.append("<comment>").append(safe(comment)).append("</comment>\r\n");
		for (String k : keys) sb.append("<entry key=\"").append(safe(k).replace("\"", "&quot;")).append("\">").append(safe(p.getProperty(k))).append("</entry>\r\n");
		sb.append("</properties>\r\n");
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}
	/** Text XML 1.0 can hold: the control characters dropped, & < > escaped. */
	private static String safe(String s) {
		StringBuilder out = new StringBuilder();
		for (char ch : (s == null ? "" : s).toCharArray()) {
			if (ch == '&') out.append("&amp;"); else if (ch == '<') out.append("&lt;"); else if (ch == '>') out.append("&gt;");
			else if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') out.append(ch);
		}
		return out.toString();
	}
	/** Keys compared part by part (between dots), a number by its value: face.2 before face.10. */
	private static final java.util.Comparator<String> KEY_ORDER = new java.util.Comparator<String>() {
		public int compare(String a, String b) {
			String[] x = a.split("\\."), y = b.split("\\.");
			for (int i = 0; i < Math.min(x.length, y.length); i++) {
				int c = x[i].matches("\\d{1,9}") && y[i].matches("\\d{1,9}") ? Integer.compare(Integer.parseInt(x[i]), Integer.parseInt(y[i])) : x[i].compareTo(y[i]);
				if (c != 0) return c;
			}
			return Integer.compare(x.length, y.length);
		}
	};
	/** The properties as the file's text, for writing elsewhere (into a package, say). */
	public static String text(Properties p, String comment) throws IOException {
		StringWriter w = new StringWriter();
		p.store(w, comment);
		return w.toString();
	}
	/** The properties as the file's bytes. */
	public static byte[] bytes(Properties p, String comment) throws IOException {
		return text(p, comment).getBytes(StandardCharsets.UTF_8);
	}

	/** A number, or the default when the key is missing or isn't one. */
	public static int num(Properties p, String key, int dflt) {
		try { return Integer.parseInt(p.getProperty(key, "").trim()); } catch (NumberFormatException e) { return dflt; }
	}
	/** A long, or the default. */
	public static long longOf(Properties p, String key, long dflt) {
		try { return Long.parseLong(p.getProperty(key, "").trim()); } catch (NumberFormatException e) { return dflt; }
	}
	/** True or false, or the default when the key is missing. */
	public static boolean bool(Properties p, String key, boolean dflt) {
		String s = p.getProperty(key);
		return s == null ? dflt : "true".equalsIgnoreCase(s.trim());
	}
}
