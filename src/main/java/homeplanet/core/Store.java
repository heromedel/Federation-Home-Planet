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
 * are read with a default. The format on disk is {@link Properties}' own, unchanged. Each file has one owning class,
 * which keeps its name and is the only one to open it.
 */
public final class Store {
	private static final Logger log = LoggerFactory.getLogger(Store.class);
	private Store() { }

	/** The file's properties; empty if it doesn't exist, and empty with a warning in the log if it can't be read. */
	public static Properties read(File f) {
		try { return load(f); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); return new Properties(); }
	}
	/** As {@link #read}, but an unreadable file is the caller's problem (never written back from a failed read). */
	public static Properties load(File f) throws IOException {
		Properties p = new Properties();
		if (f != null && f.isFile()) p.load(new StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8)));
		return p;
	}
	/** Properties from bytes already in hand (a package entry, a file read for its hash). */
	public static Properties parse(byte[] bytes) throws IOException {
		Properties p = new Properties();
		if (bytes != null) p.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
		return p;
	}
	/** Writes the properties to the file (its folder made if need be), safely, with this comment at the top. */
	public static void write(File f, Properties p, String comment) throws IOException {
		File dir = f.getAbsoluteFile().getParentFile();
		if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		SafeFiles.writeText(f, text(p, comment), false);
	}
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
