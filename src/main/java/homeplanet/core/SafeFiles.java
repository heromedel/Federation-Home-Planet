package homeplanet.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Every file Federation Home Planet writes goes through here, so a crash mid-write can never leave a half-written
 * file where a good one was: the bytes go to a temporary file beside the target first, then replace it in one move.
 * Whoever asks can also keep the previous version as "<name>.bak".
 */
public final class SafeFiles {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SafeFiles.class);
	private SafeFiles() { }

	/** Told of every file the station writes, once it's in place (from whatever thread wrote it): the Space Dock keeps itself current by it. */
	public interface Listener { void written(File f); }
	private static volatile Listener listener;
	public static void setListener(Listener l) { listener = l; }
	private static void written(File f) {
		Listener l = listener;
		if (l == null) return;
		try { l.written(f); } catch (RuntimeException e) { log.debug("A write listener failed on {}: {}", f, e.toString()); }
	}

	/** Writes the bytes to a temporary file beside {@code target}, then moves it into place (replacing any old file). */
	public static void write(File target, byte[] bytes) throws IOException {
		write(target, bytes, false);
	}
	/** As {@link #write(File, byte[])}; with {@code keepBackup}, the previous file is kept beside it as "<name>.bak" first. */
	public static void write(File target, byte[] bytes, boolean keepBackup) throws IOException {
		File dir = target.getAbsoluteFile().getParentFile();
		if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		File tmp = new File(dir, target.getName() + ".tmp");
		writeSynced(tmp, bytes);
		if (keepBackup && target.isFile()) {
			File bak = new File(dir, target.getName() + ".bak");
			Files.copy(target.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		replace(tmp, target);
	}
	/** Writes the bytes and waits until they're on the disk (so a power cut can't leave an empty file behind the move). */
	public static void writeSynced(File f, byte[] bytes) throws IOException {
		FileOutputStream out = new FileOutputStream(f);
		try {
			out.write(bytes);
			out.flush();
			out.getFD().sync();
		} finally {
			out.close();
		}
	}
	/** Writes UTF-8 text the same way. */
	public static void writeText(File target, String text, boolean keepBackup) throws IOException {
		write(target, text.getBytes(StandardCharsets.UTF_8), keepBackup);
	}

	/** Moves {@code from} over {@code to} in one step where the file system allows it (the usual case on one disk). */
	public static void replace(File from, File to) throws IOException {
		try {
			Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			// Not every file system can do the atomic form (a network drive, say): fall back to a plain replace
			log.debug("Atomic replace of {} failed ({}): a plain replace instead", to, e.toString());
			Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		written(to);
	}
	/** Copies a file, replacing any file already at {@code to}. The parent folder is created if needed. */
	public static void copy(File from, File to) throws IOException {
		File dir = to.getAbsoluteFile().getParentFile();
		if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
		written(to);
	}
	/** Moves a file, creating the target's folder and replacing any file already there. */
	public static void move(File from, File to) throws IOException {
		File dir = to.getAbsoluteFile().getParentFile();
		if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		replace(from, to);
	}

	public static byte[] read(File f) throws IOException {
		return Files.readAllBytes(f.toPath());
	}
	public static byte[] read(InputStream in) throws IOException {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		byte[] buf = new byte[65536];
		int n;
		try {
			while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
		} finally {
			in.close();
		}
		return out.toByteArray();
	}

	/** A short fingerprint of a file's contents (SHA-1, hex), used to notice when the game changed a save. */
	public static String hash(File f) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			InputStream in = new FileInputStream(f);
			try {
				byte[] buf = new byte[65536];
				int n;
				while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
			} finally {
				in.close();
			}
			StringBuilder sb = new StringBuilder();
			for (byte b : md.digest()) sb.append(String.format("%02x", b & 0xff));
			return sb.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	/** Zips a folder (recursively) into {@code zip}, skipping {@code skip} (a sub-folder to leave out, or null). */
	public static void zipFolder(File folder, File zip, File skip) throws IOException {
		File dir = zip.getAbsoluteFile().getParentFile();
		if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		File tmp = new File(dir, zip.getName() + ".tmp");
		ZipOutputStream z = new ZipOutputStream(new FileOutputStream(tmp));
		try {
			addToZip(z, folder, "", skip == null ? null : skip.getAbsoluteFile(), tmp.getAbsoluteFile());
		} finally {
			z.close();
		}
		replace(tmp, zip);
	}
	private static void addToZip(ZipOutputStream z, File dir, String prefix, File skip, File self) throws IOException {
		File[] files = dir.listFiles();
		if (files == null) return;
		java.util.Arrays.sort(files);
		for (File f : files) {
			File abs = f.getAbsoluteFile();
			if (abs.equals(skip) || abs.equals(self)) continue;
			if (f.isDirectory()) {
				addToZip(z, f, prefix + f.getName() + "/", skip, self);
			} else if (f.isFile()) {
				z.putNextEntry(new ZipEntry(prefix + f.getName()));
				z.write(read(f));
				z.closeEntry();
			}
		}
	}

	/** Deletes a folder and everything in it. Returns false if anything could not be removed. */
	public static boolean deleteTree(File dir) {
		File[] files = dir.listFiles();
		boolean ok = true;
		if (files != null) for (File f : files) ok &= f.isDirectory() ? deleteTree(f) : f.delete();
		return dir.delete() && ok;
	}

	/** A file name safe on every file system: the ship's name with the characters Windows forbids replaced. */
	public static String safeName(String name) {
		String s = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
		return s.isEmpty() ? "ship" : s;
	}
	/** Everything a stream has, then closes it. */
	public static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
		try {
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
			return out.toByteArray();
		} finally { in.close(); }
	}
}
