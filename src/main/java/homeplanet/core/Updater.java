package homeplanet.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Check for Updates: compares this version with main's on GitHub, and puts main's files in place of the program's own
 * (only the files the repository has: the JDK and Maven in tools\, the config, the logs and the jar are never touched).
 * The Construction Yard (the build .bat, with its "update" switch) then rebuilds, and puts the old files back if the
 * build fails. Only when asked: nothing contacts GitHub on its own.
 */
public final class Updater {
	private static final Logger log = LoggerFactory.getLogger(Updater.class);
	private Updater() {}

	public static final String REPO = "heromedel/Federation-Home-Planet";
	public static final String POM_URL = "https://raw.githubusercontent.com/" + REPO + "/main/pom.xml";
	public static final String ZIP_URL = "https://codeload.github.com/" + REPO + "/zip/refs/heads/main";
	public static final String PAGE_URL = "https://github.com/" + REPO;
	public static final String BUILD_BAT = "Build The Federation Home Planet Station.bat";
	/** Beside the source: what the last update installed (so files main later drops can be removed), and the old files it replaced. */
	public static final String MANIFEST = "update-manifest.txt", BACKUP = "update-backup", DOWNLOAD = "update-download.zip";
	/** A zip of main is a few megabytes: anything far bigger isn't it. */
	static final long ZIP_MAX = 64L * 1024 * 1024;
	/** Never written, whatever a zip holds: the tools, the build, git, the jar, settings and logs. */
	private static final Pattern PROTECTED = Pattern.compile("(?i)^(tools|target|\\.git|logs|" + BACKUP + ")(/.*)?$|\\.(cfg|jar|ico)$|^" + Pattern.quote(MANIFEST) + "$|^" + Pattern.quote(DOWNLOAD) + "$");

	// ---- versions ----

	private static final Pattern VERSION = Pattern.compile("(\\d+)([A-Za-z]*)\\.(\\d+)");
	/** Compares two versions like 4B.68, or 5.00 (which follows 4B.99): below 0 if a is older, 0 the same, above 0 newer. Unreadable ones count as oldest. */
	public static int compare(String a, String b) {
		long x = rank(a), y = rank(b);
		return x < y ? -1 : x > y ? 1 : 0;
	}
	private static long rank(String v) {
		Matcher m = v == null ? null : VERSION.matcher(v.trim());
		if (m == null || !m.matches()) return -1;
		long letters = 0;
		for (char ch : m.group(2).toUpperCase().toCharArray()) letters = letters * 27 + (ch - 'A' + 1);
		return (Long.parseLong(m.group(1)) * 100000L + letters) * 100000L + Long.parseLong(m.group(3));
	}
	/** The project's version from a pom.xml (the first version tag, the project's own). */
	public static String pomVersion(String pom) {
		Matcher m = Pattern.compile("<version>\\s*([^<\\s]+)\\s*</version>").matcher(pom);
		return m.find() ? m.group(1) : null;
	}
	/** Main's version on GitHub; throws with the reason if it can't be read. */
	public static String latest() throws IOException {
		String v = pomVersion(new String(fetch(POM_URL, 1024 * 1024), StandardCharsets.UTF_8));
		if (v == null) throw new IOException("main's pom.xml at " + POM_URL + " has no version");
		return v;
	}

	// ---- where it can update ----

	/**
	 * The folder the program was built in: the jar sits in its "Current Build", beside the source and the Construction
	 * Yard. Null when it wasn't built that way (run from target\, or a jar on its own).
	 */
	public static File installRoot() {
		File dir = HomePlanet.appDir();
		File root = dir == null ? null : dir.getParentFile();
		return root != null && "Current Build".equalsIgnoreCase(dir.getName()) && isInstall(root) ? root : null;
	}
	static boolean isInstall(File root) {
		return new File(root, "pom.xml").isFile() && new File(root, BUILD_BAT).isFile() && new File(root, "src").isDirectory();
	}
	/** A git checkout (GitHub Desktop's): updated by fetching, never by replacing its files. */
	public static boolean isGitCheckout(File root) {
		return new File(root, ".git").exists();
	}

	// ---- the download ----

	/** Downloads main as a zip into the install folder (replacing an earlier download), and checks it. */
	public static File download(File root) throws IOException {
		File zip = new File(root, DOWNLOAD);
		SafeFiles.write(zip, fetch(ZIP_URL, ZIP_MAX));
		check(zip);
		return zip;
	}
	private static byte[] fetch(String url, long max) throws IOException {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(15000);
		c.setReadTimeout(60000);
		c.setInstanceFollowRedirects(true);
		c.setRequestProperty("User-Agent", HomePlanet.APP_NAME + "/" + HomePlanet.APP_VERSION);
		try {
			int code = c.getResponseCode();
			if (code != 200) throw new IOException(url + " answered " + code + (c.getResponseMessage() == null ? "" : " " + c.getResponseMessage()));
			InputStream in = c.getInputStream();
			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buf = new byte[65536];
				long total = 0;
				for (int n; (n = in.read(buf)) > 0;) {
					total += n;
					if (total > max) throw new IOException(url + " sent more than " + (max / 1024 / 1024) + " MB: that isn't the program");
					out.write(buf, 0, n);
				}
				return out.toByteArray();
			} finally {
				in.close();
			}
		} finally {
			c.disconnect();
		}
	}

	/** The zip's files by their path in the repository (its single top folder taken off). Refused if it isn't whole. */
	static Map<String, ZipEntry> check(File zip) throws IOException {
		ZipFile z = new ZipFile(zip);
		try {
			return files(z);
		} finally {
			z.close();
		}
	}
	private static Map<String, ZipEntry> files(ZipFile z) throws IOException {
		Map<String, ZipEntry> out = new LinkedHashMap<String, ZipEntry>();
		String top = null;
		for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements();) {
			ZipEntry e = en.nextElement();
			String name = e.getName().replace('\\', '/');
			int slash = name.indexOf('/');
			if (slash <= 0) throw new IOException("the download isn't a copy of the program (" + name + " is outside its folder)");
			String folder = name.substring(0, slash);
			if (top == null) top = folder;
			else if (!top.equals(folder)) throw new IOException("the download isn't a copy of the program (two top folders)");
			String path = name.substring(slash + 1);
			if (e.isDirectory() || path.isEmpty()) continue;
			for (String part : path.split("/")) if (part.equals("..") || part.equals(".") || part.isEmpty() || part.contains(":")) throw new IOException("the download names a file outside the program's folder: " + path);
			out.put(path, e);
		}
		for (String need : new String[] {"pom.xml", BUILD_BAT, "src/main/java/homeplanet/core/HomePlanet.java"})
			if (!out.containsKey(need)) throw new IOException("the download isn't whole: it has no " + need);
		return out;
	}

	// ---- putting it in place ----

	/** What an update did: the version it brought, and how many files it replaced, added and removed. */
	public static final class Result {
		public final String version;
		public final int replaced, added, removed;
		Result(String version, int replaced, int added, int removed) { this.version = version; this.replaced = replaced; this.added = added; this.removed = removed; }
	}

	/**
	 * Puts the zip's files in place of the program's own: each one the zip has is written (the old one kept in
	 * update-backup\files first), and each one the last update installed that the zip no longer has is removed (kept
	 * too). update-backup\added.txt lists the new ones, so the Construction Yard can put everything back if the build
	 * fails. Nothing protected is ever written. On any failure everything is put back as it was, and the reason thrown.
	 */
	public static synchronized Result apply(File root, File zip) throws IOException {
		File backup = new File(root, BACKUP);
		SafeFiles.deleteTree(backup);
		File kept = new File(backup, "files");
		List<String> added = new ArrayList<String>(), done = new ArrayList<String>();
		Set<String> before = manifest(root);
		int replaced = 0, removed = 0;
		String version;
		ZipFile z = new ZipFile(zip);
		try {
			Map<String, ZipEntry> files = files(z);
			version = pomVersion(new String(read(z, files.get("pom.xml")), StandardCharsets.UTF_8));
			try {
				for (Map.Entry<String, ZipEntry> e : files.entrySet()) {
					String path = e.getKey();
					if (PROTECTED.matcher(path).find()) continue;
					File target = new File(root, path);
					byte[] bytes = read(z, e.getValue());
					if (target.isFile()) {
						if (java.util.Arrays.equals(SafeFiles.read(target), bytes)) continue; // the same: left alone
						SafeFiles.write(new File(kept, path), SafeFiles.read(target));
						replaced++;
					} else {
						added.add(path);
					}
					done.add(path);
					SafeFiles.write(target, bytes);
				}
				for (String path : before) {
					if (files.containsKey(path) || PROTECTED.matcher(path).find()) continue;
					File target = new File(root, path);
					if (!target.isFile()) continue;
					SafeFiles.write(new File(kept, path), SafeFiles.read(target));
					done.add(path);
					if (!target.delete()) throw new IOException("Could not remove " + target);
					removed++;
				}
				// the list itself goes back too, so a failed build leaves the last update's list as it was
				File manifest = new File(root, MANIFEST);
				List<String> undo = new ArrayList<String>(added);
				if (manifest.isFile()) SafeFiles.write(new File(kept, MANIFEST), SafeFiles.read(manifest));
				else undo.add(MANIFEST);
				writeList(new File(backup, "added.txt"), undo);
				writeList(manifest, new ArrayList<String>(files.keySet()));
			} catch (IOException ex) {
				putBack(root, done, added);
				throw ex;
			}
		} finally {
			z.close();
		}
		HistoryLog.entry("UPDATE", "New construction plans from main (" + version + "): " + replaced + " files replaced, " + added.size() + " added, " + removed + " removed");
		return new Result(version, replaced, added.size(), removed);
	}
	/** Every file touched, back as it was: the kept copies return, the new ones go. */
	private static void putBack(File root, List<String> done, List<String> added) {
		File kept = new File(new File(root, BACKUP), "files");
		for (String path : done) {
			try {
				File k = new File(kept, path), target = new File(root, path);
				if (added.contains(path)) target.delete();
				else if (k.isFile()) SafeFiles.write(target, SafeFiles.read(k));
			} catch (IOException e) {
				log.error("Could not put back {} after a failed update: {}", path, e.toString());
			}
		}
	}
	/** The files the last update installed (empty before the first). */
	static Set<String> manifest(File root) {
		Set<String> out = new LinkedHashSet<String>();
		File f = new File(root, MANIFEST);
		if (!f.isFile()) return out;
		try {
			for (String line : new String(SafeFiles.read(f), StandardCharsets.UTF_8).split("\r?\n")) {
				line = line.trim();
				if (!line.isEmpty() && !line.startsWith("#") && !line.contains("..")) out.add(line);
			}
		} catch (IOException e) {
			log.warn("Could not read {}: {}", f, e.toString());
		}
		return out;
	}
	/** One path a line, Windows separators (the Construction Yard reads added.txt). */
	private static void writeList(File f, List<String> paths) throws IOException {
		StringBuilder sb = new StringBuilder();
		for (String p : paths) sb.append(f.getName().equals(MANIFEST) ? p : p.replace('/', '\\')).append("\r\n");
		SafeFiles.writeText(f, sb.toString(), false);
	}
	private static byte[] read(ZipFile z, ZipEntry e) throws IOException {
		InputStream in = z.getInputStream(e);
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[65536];
			for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
			return out.toByteArray();
		} finally {
			in.close();
		}
	}

	/** Starts the Construction Yard's update: it rebuilds, then opens the station (or puts the old files back). */
	public static void startRebuild(File root) throws IOException {
		// one argument: Java quotes it whole, and cmd /c takes off the outer quotes, leaving start's own
		new ProcessBuilder("cmd", "/c", "start \"Construction Yard\" \"" + new File(root, BUILD_BAT).getAbsolutePath() + "\" update")
				.directory(root).start();
	}
}
