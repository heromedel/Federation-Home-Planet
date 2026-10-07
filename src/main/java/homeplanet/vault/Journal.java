package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import homeplanet.core.Event;
import homeplanet.core.HistoryLog;
import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;
import homeplanet.core.Store;

/**
 * The journal (docs/OVERHAUL-6.md §3.2; 5.71): an action that moves or writes more than one file is written as a note
 * first, in {@code journal/} under the fleet's folder, then its steps are done, then the note is deleted. A step is a
 * replacement (the new bytes wait beside the file as {@code <name>.tx} before the note is written), a rename (a file
 * or a whole folder, all-or-nothing on one drive) or a deletion; each file once, with one owner. A step refused by
 * Windows (a file held open by an antivirus scan or a backup program) is tried again, as {@link SafeFiles#replace}
 * does. If a step fails, the steps done are undone and the note deleted; if the undo fails too, the note stays, and
 * the next opening {@link #settle settles} it: each step is finished where it can be, else left for a person, and the
 * entry it then writes keeps the note's own time and stardate ({@code finished=startup}).
 */
public final class Journal {
	private static final Logger log = LoggerFactory.getLogger(Journal.class);
	private Journal() { }

	public static final String DIR = "journal";
	/** The new bytes of a file to replace, waiting beside it. */
	static final String PENDING = ".tx";
	private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final SimpleDateFormat NAME = new SimpleDateFormat("yyyyMMdd-HHmmss");
	private static final String REPLACE = "replace", RENAME = "rename", DELETE = "delete";

	public static File dir(Vault v) { return new File(v.root, DIR); }

	/** Begins a note for an action of this kind (an event kind, UPPER_SNAKE: what the note is for, told at start-up if it's found there). */
	public static Note begin(Vault v, String kind) { return new Note(v, kind); }

	/** One action's note: its steps, in order, done by {@link #commit}. */
	public static final class Note {
		private final Vault v;
		private final String kind;
		private final List<String[]> steps = new ArrayList<String[]>(); // {op, from, to}, paths relative to the fleet's folder
		private final Map<String, byte[]> pending = new LinkedHashMap<String, byte[]>(); // a replacement's new bytes, by target
		private final List<String> owned = new ArrayList<String>(); // every path a step touches, each once
		private Note(Vault v, String kind) { this.v = v; this.kind = kind; }

		/** The file gets these bytes (made or replaced). A second, different write to the same file is refused: that is the shape of a lost purchase. */
		public Note replace(File target, byte[] bytes) throws IOException {
			String p = rel(target);
			byte[] had = pending.get(p);
			if (had != null) {
				if (java.util.Arrays.equals(had, bytes)) return this; // the same contents twice: one write
				throw new IOException("The Home Planet Station refused to write " + target.getName() + " twice with different contents in one action (" + kind + ")");
			}
			own(target);
			pending.put(p, bytes);
			steps.add(new String[] {REPLACE, p, ""});
			return this;
		}
		/** A file or folder renamed (moved) to a place where nothing is. */
		public Note rename(File from, File to) throws IOException {
			own(from); own(to);
			steps.add(new String[] {RENAME, rel(from), rel(to)});
			return this;
		}
		public Note delete(File f) throws IOException {
			own(f);
			steps.add(new String[] {DELETE, rel(f), ""});
			return this;
		}
		private void own(File f) throws IOException {
			String p = rel(f);
			if (owned.contains(p)) throw new IOException("The Home Planet Station refused to touch " + f.getName() + " twice in one action (" + kind + ")");
			owned.add(p);
		}
		private String rel(File f) throws IOException {
			// relative to the saves folder: the fleet's folder is in it, and so is continue.sav (FTL's, beside it)
			String base = v.saves.getAbsoluteFile().getCanonicalPath(), path = f.getAbsoluteFile().getCanonicalPath();
			if (path.equals(base) || !path.startsWith(base + File.separator)) throw new IOException(f + " is not in the saves folder " + base);
			return path.substring(base.length() + 1).replace(File.separatorChar, '/');
		}
		public boolean isEmpty() { return steps.isEmpty(); }

		/** Writes the pending bytes and the note, does the steps, deletes the note. On a failure the steps done are undone (the note stays if that fails too). */
		public void commit() throws IOException {
			if (steps.isEmpty()) return;
			File jdir = dir(v);
			if (!jdir.isDirectory() && !jdir.mkdirs()) throw new IOException("Could not create " + jdir);
			// the new bytes first, beside their files, so the note never names bytes that aren't there
			List<File> written = new ArrayList<File>();
			try {
				for (Map.Entry<String, byte[]> e : pending.entrySet()) {
					File tx = pendingFile(v, e.getKey());
					File parent = tx.getAbsoluteFile().getParentFile();
					if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
					SafeFiles.writeSynced(tx, e.getValue());
					written.add(tx);
				}
			} catch (IOException e) {
				for (File t : written) t.delete();
				throw e;
			}
			File note = noteFile(jdir, kind);
			try {
				SafeFiles.writeSynced(note, text().getBytes("UTF-8"));
			} catch (IOException e) {
				for (File t : written) t.delete();
				throw e;
			}
			// what each replaced file held, to put back if a later step fails
			Map<String, byte[]> before = new LinkedHashMap<String, byte[]>();
			for (String p : pending.keySet()) { File f = new File(v.saves, p); before.put(p, f.isFile() ? SafeFiles.read(f) : null); }
			List<String[]> done = new ArrayList<String[]>();
			try {
				for (String[] s : steps) { doStep(v, s); done.add(s); }
			} catch (IOException e) {
				boolean undone = true;
				Collections.reverse(done);
				for (String[] s : done) {
					try { undoStep(v, s, before.get(s[1])); }
					catch (IOException again) { undone = false; log.error("Could not undo " + s[0] + " " + s[1] + " after a failed " + kind, again); }
				}
				for (File t : written) t.delete();
				if (undone) { note.delete(); throw e; }
				throw new IOException(e.getMessage() + ". The action was left half done; its note is " + note + ", and the station finishes it the next time it opens", e);
			}
			if (!note.delete()) log.warn("Could not remove the finished note {}", note);
		}
		private String text() {
			StringBuilder sb = new StringBuilder();
			synchronized (STAMP) { sb.append("time=").append(STAMP.format(new Date())).append('\n'); }
			sb.append("day=").append(MasterLog.today(v)).append('\n');
			sb.append("kind=").append(kind).append('\n');
			sb.append("station=").append(HomePlanet.version()).append('\n');
			int n = 1;
			for (String[] s : steps) sb.append("step.").append(n++).append('=').append(s[0]).append('\t').append(s[1]).append('\t').append(s[2]).append('\n');
			return sb.toString();
		}
	}

	static File pendingFile(Vault v, String rel) { File f = new File(v.saves, rel); return new File(f.getAbsoluteFile().getParentFile(), f.getName() + PENDING); }
	private static File noteFile(File jdir, String kind) {
		String stamp;
		synchronized (NAME) { stamp = NAME.format(new Date()); }
		File f = new File(jdir, stamp + "-" + kind.toLowerCase() + ".txt");
		for (int i = 2; f.exists(); i++) f = new File(jdir, stamp + "-" + i + "-" + kind.toLowerCase() + ".txt");
		return f;
	}

	/** Does one step; a rename or a replacement refused by Windows is tried again a few times. */
	private static void doStep(Vault v, String[] s) throws IOException {
		File from = new File(v.saves, s[1]);
		if (s[0].equals(REPLACE)) {
			SafeFiles.replace(pendingFile(v, s[1]), from);
		} else if (s[0].equals(RENAME)) {
			File to = new File(v.saves, s[2]);
			if (to.exists()) throw new IOException("Could not move " + from.getName() + ": something is already at " + to);
			File parent = to.getAbsoluteFile().getParentFile();
			if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
			SafeFiles.replace(from, to);
		} else if (s[0].equals(DELETE)) {
			if (from.exists() && !(from.isDirectory() ? SafeFiles.deleteTree(from) : from.delete())) throw new IOException("Could not remove " + from);
		}
	}
	private static void undoStep(Vault v, String[] s, byte[] before) throws IOException {
		File f = new File(v.saves, s[1]);
		if (s[0].equals(REPLACE)) {
			if (before == null) { if (f.exists() && !f.delete()) throw new IOException("Could not remove " + f); }
			else SafeFiles.write(f, before);
		} else if (s[0].equals(RENAME)) {
			File to = new File(v.saves, s[2]);
			if (to.exists() && !f.exists()) SafeFiles.replace(to, f);
		}
		// a deletion can't be undone: it comes last in any sensible note
	}

	// ---- at start-up ----

	/** A note left by a station that stopped partway: its kind, time and day, and how it ended. */
	public static final class Settled {
		public final String kind, time, note;
		public final int day;
		public final boolean finished;
		Settled(String kind, String time, int day, String note, boolean finished) { this.kind = kind; this.time = time; this.day = day; this.note = note; this.finished = finished; }
	}

	/**
	 * Finishes the notes left in the journal (a station that stopped partway through an action): each step where it
	 * can be (a replacement whose bytes still wait, a rename not yet made, a deletion not yet done; one already done is
	 * left as it is). A step that can't be told either way (a rename with both ends present, or neither) leaves the
	 * note in place for a person, and the log says so. Each note finished is logged with its own time and stardate.
	 */
	public static List<Settled> settle(Vault v) {
		List<Settled> out = new ArrayList<Settled>();
		File[] notes = dir(v).listFiles();
		if (notes == null) return out;
		java.util.Arrays.sort(notes);
		for (File note : notes) {
			if (!note.isFile() || !note.getName().endsWith(".txt")) continue;
			Properties p = Store.read(note);
			String kind = p.getProperty("kind", "ACTION"), time = p.getProperty("time", ""), station = p.getProperty("station", "");
			int day = Store.num(p, "day", 0);
			List<String> trouble = new ArrayList<String>();
			int n = 0;
			for (int i = 1; p.getProperty("step." + i) != null; i++) {
				String[] s = p.getProperty("step." + i).split("\t", -1);
				if (s.length < 3) { trouble.add("step " + i + " can't be read"); continue; }
				n++;
				try {
					String why = finishStep(v, s);
					if (why != null) trouble.add(why);
				} catch (IOException e) {
					trouble.add(s[0] + " " + s[1] + ": " + e.getMessage());
				}
			}
			boolean finished = trouble.isEmpty();
			if (finished && !note.delete()) log.warn("Could not remove the finished note {}", note);
			Event e = Event.of("JOURNAL").put("what", finished ? "finished" : "stuck").put("action", kind).put("steps", n).put("note", DIR + "/" + note.getName())
					.put("finished", "startup").put("time", time).put("day", day > 0 ? Integer.valueOf(day) : null).put("left_by", station).details(trouble);
			String words = finished ? "an action the station had begun (" + kind.toLowerCase().replace('_', ' ') + ") was finished at start-up"
					: "an action the station had begun (" + kind.toLowerCase().replace('_', ' ') + ") could not be finished at start-up: its note is kept in " + DIR + "/" + note.getName();
			HistoryLog.entry("JOURNAL", words, trouble.isEmpty() ? null : trouble, e.human(finished ? "The station finished what it had begun." : "The station could not finish what it had begun; its note is kept."));
			if (!finished) log.warn("The note {} could not be finished: {}", note, trouble);
			out.add(new Settled(kind, time, day, note.getName(), finished));
		}
		return out;
	}
	/** Finishes one step if it isn't done; null when it is done (or was already), else why it can't be told. */
	private static String finishStep(Vault v, String[] s) throws IOException {
		File from = new File(v.saves, s[1]);
		if (s[0].equals(REPLACE)) {
			File tx = pendingFile(v, s[1]);
			if (tx.isFile()) SafeFiles.replace(tx, from); // the bytes still wait: not yet done
			return null;
		}
		if (s[0].equals(RENAME)) {
			File to = new File(v.saves, s[2]);
			if (from.exists() && !to.exists()) { doStep(v, s); return null; }
			if (!from.exists() && to.exists()) return null; // done
			if (from.exists() && to.exists()) return "both " + s[1] + " and " + s[2] + " are there: which is hers can't be told";
			return "neither " + s[1] + " nor " + s[2] + " is there";
		}
		if (s[0].equals(DELETE)) { doStep(v, s); return null; }
		return "step " + s[0] + " isn't one the station knows";
	}
}
