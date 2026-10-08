package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import homeplanet.core.SafeFiles;

/**
 * The ship systems stored in the Cargo Hold (heromedel's Plan O, 6.11: the one home for its file, which the Cargo
 * Bay's Refit tab, scrapping, the Junkyard's parts, the Third Fleet Commander, expeditions and rewards all add to):
 * cargohold/systems.txt, a line each, {@code <system id> <level> [<broken bars>]}. A Clone Bay has no level (it uses
 * the Medbay's); broken bars go third, so an older station reads the first two and keeps the rest of its lines.
 */
public final class StoredSystems {
	private StoredSystems() { }

	public static final String HEADER = "# Ship systems stored in the Cargo Bay: <system id> <level> [<broken bars>] (a Clone Bay has no level: it uses the Medbay's)";

	/** One stored system, as its line has it. */
	public static final class Entry {
		public final String id;
		/** Its level as written (1 if none or unreadable). */
		public final int level;
		/** Its broken bars as written (0 if none). */
		public final int broken;
		/** The line itself, trimmed: written back as it was when this station doesn't know the system. */
		public final String line;
		Entry(String id, int level, int broken, String line) { this.id = id; this.level = level; this.broken = broken; this.line = line; }
	}

	/** A stored system's line. */
	public static String line(String id, int level, int broken) {
		if (broken > 0) return id + " " + level + " " + broken;
		return level > 0 ? id + " " + level : id;
	}

	/** The file. */
	public static File file(Vault v) { return v.systemsFile(); }

	/** Every system stored, in the file's order (none if there's no file); throws if it can't be read. */
	public static List<Entry> read(Vault v) throws IOException { return read(file(v)); }
	public static List<Entry> read(File f) throws IOException {
		List<Entry> out = new ArrayList<Entry>();
		if (f == null || !f.isFile()) return out;
		for (String line : new String(SafeFiles.read(f), StandardCharsets.UTF_8).split("\r?\n")) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			String[] p = line.split("\\s+");
			int level = 1, broken = 0;
			try { if (p.length > 1) level = Math.max(1, Integer.parseInt(p[1])); } catch (NumberFormatException e) { }
			try { if (p.length > 2) broken = Math.max(0, Integer.parseInt(p[2])); } catch (NumberFormatException e) { }
			out.add(new Entry(p[0], level, broken, line));
		}
		return out;
	}

	/** Adds these lines (from {@link #line}) to the stored systems, in this transaction: the file as it is, then them. */
	public static void add(Vault.Transaction tx, Vault v, List<String> lines) throws IOException {
		if (lines.isEmpty()) return;
		File f = file(v);
		List<String> all = new ArrayList<String>();
		if (f.isFile()) all.addAll(java.util.Arrays.asList(new String(SafeFiles.read(f), StandardCharsets.UTF_8).split("\r?\n", -1)));
		else all.add(HEADER);
		while (!all.isEmpty() && all.get(all.size() - 1).isEmpty()) all.remove(all.size() - 1); // its last line break
		all.addAll(lines);
		tx.put(f, bytes(all));
	}
	/** The whole list written anew, in this transaction: these lines, then those this station couldn't read, as they were. */
	public static void write(Vault.Transaction tx, Vault v, List<String> lines, List<String> unknown) {
		List<String> all = new ArrayList<String>();
		all.add(HEADER);
		all.addAll(lines);
		all.addAll(unknown);
		tx.put(file(v), bytes(all));
	}
	private static byte[] bytes(List<String> lines) { return (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8); }
}
