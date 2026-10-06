package homeplanet.core;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One station to a saves folder (heromedel, 5.45). Two copies of the station on the same saves each keep their own
 * list of the fleet and save it whole, so one could undo the other's changes (a ship docked in one, still boarded in
 * the other). The first copy claims the folder: a lock on a small file there, which the system lets go the moment that
 * copy closes, however it closes. A second copy started on the same saves says so and closes. A second station
 * (--station) has saves of its own. If the lock can't be made at all (a folder it can't write to), the station opens
 * as before: this guard never keeps it from starting.
 */
public final class StationLock {
	private static final Logger log = LoggerFactory.getLogger(StationLock.class);
	private StationLock() { }

	static final String FILE = "FederationHomePlanet.lock";
	/** The byte locked: far past the note, so a backup or sync program can still read the file; only stations ask for it. */
	private static final long AT = 1L << 40;
	private static final String NOTE = "The Home Planet Station keeps this file while it is open, so that two copies of it never work on one fleet at once.\r\n";
	private static FileChannel channel;
	private static FileLock lock;
	private static File held;

	/** Claims this saves folder for this station, letting go of the one claimed before: false if another copy has it. */
	public static synchronized boolean claim(File saves) {
		File dir = folder(saves);
		if (dir.equals(held)) return true;
		FileChannel ch = null;
		try {
			ch = FileChannel.open(new File(dir, FILE).toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			FileLock l = ch.tryLock(AT, 1, false);
			if (l == null) { ch.close(); log.info("{} is open in another copy of the station", dir); return false; }
			release();
			channel = ch; lock = l; held = dir;
			try { ch.truncate(0); ch.write(ByteBuffer.wrap(NOTE.getBytes(StandardCharsets.UTF_8))); }
			catch (IOException e) { log.debug("Could not write the note in {}: {}", FILE, e.toString()); } // the lock holds without it
			log.debug("This station has claimed {}", dir);
			return true;
		} catch (OverlappingFileLockException e) { // this program has it through another claim: it's ours
			close(ch);
			return true;
		} catch (IOException e) {
			close(ch);
			log.warn("Could not claim {} for this station ({}); open without the guard against a second copy", dir, e.toString());
			return true;
		}
	}
	/** Another copy of the station has this saves folder (looked at, not claimed). */
	public static synchronized boolean inUse(File saves) {
		File dir = folder(saves);
		if (dir.equals(held) || !new File(dir, FILE).isFile()) return false; // no station has opened on it
		FileChannel ch = null;
		try {
			ch = FileChannel.open(new File(dir, FILE).toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			FileLock l = ch.tryLock(AT, 1, false);
			if (l == null) return true;
			l.release();
			return false;
		} catch (OverlappingFileLockException e) {
			return false;
		} catch (IOException e) {
			return false;
		} finally {
			close(ch);
		}
	}
	/** What a second copy says before it closes, or when the folder chosen in Settings is another copy's. */
	public static String inUseMessage(File saves) {
		return "The Home Planet Station is already open on these saves:\n" + saves.getAbsolutePath() + "\n\n"
				+ "Two at once would undo each other's changes to the fleet. Switch to the one that's open.\n"
				+ "If you can't find it, it may still be closing: wait a moment and try again.";
	}
	private static synchronized void release() {
		try { if (lock != null) lock.release(); } catch (IOException e) { log.debug("Could not let go of {}: {}", held, e.toString()); }
		close(channel);
		lock = null; channel = null; held = null;
	}
	private static void close(FileChannel ch) {
		try { if (ch != null) ch.close(); } catch (IOException e) { }
	}
	private static File folder(File saves) {
		try { return saves.getCanonicalFile(); } catch (IOException e) { return saves.getAbsoluteFile(); }
	}
}
