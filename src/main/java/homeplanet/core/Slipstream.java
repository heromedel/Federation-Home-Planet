package homeplanet.core;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Slipstream Mod Manager, as far as the station uses it: where it is, what's in its mods folder,
 * and patching the game data from the command line (java -jar modman.jar --patch ...).
 */
public class Slipstream {
	private static final Logger log = LoggerFactory.getLogger(Slipstream.class);

	/** Config key: Slipstream's folder (has modman.jar and a mods folder). */
	public static final String CFG_DIR = "slipstream_dir";
	/** Config key: Slipstream was offered at startup (so "Not now" isn't asked again). */
	public static final String CFG_OFFERED = "slipstream_offered";
	/** Config keys for the Patch window: remembered mod file names (in order, separated by |), and the toggles. */
	public static final String CFG_MODS = "patch_mods", CFG_REMEMBER = "patch_remember", CFG_RUN = "patch_run_ftl";

	public static final String VERSION = "1.9.1";
	public static final String ZIP_NAME = "SlipstreamModManager_" + VERSION + "-Win.zip";
	public static final String DOWNLOAD_URL = "https://sourceforge.net/projects/slipstreammodmanager/files/Slipstream/"
			+ VERSION + "/" + ZIP_NAME + "/download";
	public static final String DOWNLOAD_PAGE = "https://sourceforge.net/projects/slipstreammodmanager/files/Slipstream/" + VERSION + "/";

	/** The companion mod's file name in Slipstream's mods folder. */
	public static final String MOD_FILE = homeplanet.parser.CompanionMod.FILE;

	/** A mod in Slipstream's mods folder. */
	public static class Mod {
		public final File file;
		public final String title, author;
		Mod(File file, String title, String author) { this.file = file; this.title = title; this.author = author; }
		public String name() { return file.getName(); }
		/** The companion mod itself (the file the station writes). */
		public boolean isCompanionMod() { return MOD_FILE.equals(file.getName()) && homeplanet.parser.CompanionMod.AUTHOR.equals(author); }
		/** Another copy of our mod (an older version, a test build): never patched alongside the real one. */
		public boolean isStrayCopy() {
			return !isCompanionMod() && homeplanet.parser.CompanionMod.AUTHOR.equals(author);
		}
		public String toString() { return title; }
	}

	/** Slipstream's folder from the config, or null if unset or no longer valid. */
	public static File dir() {
		String s = HomePlanet.config.getProperty(CFG_DIR);
		if (s == null || s.length() == 0) return null;
		File f = new File(s);
		return valid(f) ? f : null;
	}
	public static boolean valid(File f) {
		return f != null && f.isDirectory() && new File(f, "modman.jar").isFile();
	}
	public static File modsDir(File dir) { return new File(dir, "mods"); }

	/**
	 * Finds Slipstream, asking the user (Browse / Download / Cancel) if the config doesn't say.
	 * Returns null if the user gives up. Saves the answer in the config.
	 */
	public static File locate(Component owner) {
		File d = dir();
		if (d != null) return d;
		return ask(owner, "Where is Slipstream Mod Manager?\n\n"
				+ "Browse to its folder (the one with modman.jar and a mods folder),\n"
				+ "or let the station download Slipstream " + VERSION + " into its own folder.",
				"Slipstream", "Cancel");
	}

	/**
	 * First run: offers Slipstream once, before anything needs it. Asked only while no Slipstream folder is set and it
	 * hasn't been offered yet; "Not now" isn't asked again at startup (locate() still asks when a ship needs the mod).
	 * Returns true if the config changed.
	 */
	public static boolean offerAtStart() {
		if (dir() != null || HomePlanet.config.getProperty(CFG_OFFERED) != null) return false;
		ask(null, "Retrofitted, remodeled and designed ships fly on the station's own blueprints,\n"
				+ "and those reach FTL through Slipstream Mod Manager. Everything else works without it.\n\n"
				+ "Point the station at your Slipstream folder (the one with modman.jar),\n"
				+ "or have the Federation Home Planet download Slipstream " + VERSION + " for you.\n\n"
				+ "You can also do this later in Settings.",
				"Slipstream Mod Manager", "Not now");
		HomePlanet.config.setProperty(CFG_OFFERED, "true");
		return true;
	}

	/** Browse, download, or the last option (which returns null). Remembers the folder chosen. */
	private static File ask(Component owner, String message, String title, String noLabel) {
		File d;
		while (true) {
			Object[] options = {"Browse...", "Download it for me", noLabel};
			int choice = JOptionPane.showOptionDialog(owner, message, title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
			if (choice == 0) {
				d = browse(owner);
			} else if (choice == 1) {
				d = download(owner);
			} else {
				return null;
			}
			if (d != null) {
				prepareConfig(d);
				HomePlanet.config.setProperty(CFG_DIR, d.getAbsolutePath());
				HomePlanet.saveConfig();
				HistoryLog.entry("SLIPSTREAM", "Using Slipstream at " + d.getAbsolutePath());
				return d;
			}
		}
	}

	private static File browse(Component owner) {
		javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
		fc.setDialogTitle("Find Slipstream's folder (modman.jar)");
		fc.setFileSelectionMode(javax.swing.JFileChooser.FILES_AND_DIRECTORIES);
		fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
			public String getDescription() { return "Slipstream Mod Manager (modman.jar)"; }
			public boolean accept(File f) { return f.isDirectory() || f.getName().equalsIgnoreCase("modman.jar"); }
		});
		if (fc.showOpenDialog(owner) != javax.swing.JFileChooser.APPROVE_OPTION) return null;
		File f = fc.getSelectedFile();
		if (f == null) f = fc.getCurrentDirectory();
		if (f.isFile()) f = f.getParentFile();
		if (valid(f)) return f;
		// a folder chosen by name from inside itself can come back doubled (...\Slipstream\Slipstream): try around it
		if (valid(f.getParentFile())) return f.getParentFile();
		if (valid(fc.getCurrentDirectory())) return fc.getCurrentDirectory();
		JOptionPane.showMessageDialog(owner, "That folder has no modman.jar:\n" + f.getPath(), "Slipstream", JOptionPane.WARNING_MESSAGE);
		return null;
	}

	/** Progress shown while downloading: a modal dialog the worker thread updates and closes. Cancel stops the download. */
	static class Progress {
		final javax.swing.JDialog dialog;
		final javax.swing.JProgressBar bar = new javax.swing.JProgressBar(0, 100);
		final javax.swing.JLabel note = new javax.swing.JLabel("Connecting to SourceForge...");
		volatile boolean cancelled = false;
		Progress(Component owner, String title) {
			java.awt.Window w = owner == null ? null : javax.swing.SwingUtilities.getWindowAncestor(owner);
			dialog = new javax.swing.JDialog(w, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
			javax.swing.JPanel p = new javax.swing.JPanel(new java.awt.BorderLayout(0, 8));
			p.setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 20, 12, 20));
			bar.setPreferredSize(new java.awt.Dimension(320, bar.getPreferredSize().height));
			p.add(note, java.awt.BorderLayout.NORTH);
			p.add(bar, java.awt.BorderLayout.CENTER);
			javax.swing.JButton cancel = new javax.swing.JButton("Cancel");
			cancel.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) { cancelled = true; note.setText("Cancelling..."); }
			});
			javax.swing.JPanel b = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
			b.add(cancel);
			p.add(b, java.awt.BorderLayout.SOUTH);
			dialog.getContentPane().add(p);
			dialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
			dialog.pack();
			dialog.setLocationRelativeTo(owner);
		}
		void update(final int percent, final String text) {
			javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() {
				if (percent >= 0) bar.setValue(percent); else bar.setIndeterminate(true);
				if (text != null) note.setText(text);
			}});
		}
		void close() {
			javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() { dialog.dispose(); }});
		}
	}

	/** Downloads the Windows zip from SourceForge and unzips it into the station's own folder. Returns the folder, or null. */
	private static File download(final Component owner) {
		final File target = new File(ZIP_NAME.replace(".zip", "")).getAbsoluteFile();
		if (valid(target)) return target; // already there from an earlier download
		final File zip = new File(ZIP_NAME).getAbsoluteFile();
		final Progress pm = new Progress(owner, "Downloading Slipstream " + VERSION);
		final String[] error = {null};
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					fetch(DOWNLOAD_URL, zip, pm);
					if (!pm.cancelled) {
						pm.update(-1, "Unpacking...");
						unzip(zip, target.getParentFile());
					}
				} catch (Exception e) {
					log.warn("Slipstream download failed", e);
					error[0] = e.toString();
				} finally {
					zip.delete();
					pm.close();
				}
			}
		}, "slipstream-download");
		t.start();
		pm.dialog.setVisible(true); // blocks (in a nested event loop) until the worker closes it
		if (pm.cancelled) return null;
		if (error[0] == null && valid(target)) return target;
		Object[] options = {"Open download page", "OK"};
		int r = JOptionPane.showOptionDialog(owner,
				"Slipstream couldn't be downloaded" + (error[0] == null ? "." : ":\n" + error[0]) + "\n\n"
				+ "You can download it yourself from SourceForge, unzip it anywhere, and use Browse.",
				"Slipstream", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
		if (r == 0) {
			try { java.awt.Desktop.getDesktop().browse(new java.net.URI(DOWNLOAD_PAGE)); } catch (Exception e) { }
		}
		return null;
	}

	/** GET with redirects (SourceForge bounces to a mirror). Insists on a zip: a web page instead is an error. */
	static void fetch(String url, File out, Progress pm) throws IOException {
		HttpURLConnection c = null;
		for (int hop = 0; hop < 10; hop++) {
			c = (HttpURLConnection) new URL(url).openConnection();
			c.setInstanceFollowRedirects(false);
			c.setConnectTimeout(20000);
			c.setReadTimeout(60000);
			c.setRequestProperty("User-Agent", "Federation-Home-Planet");
			int code = c.getResponseCode();
			if (code / 100 == 3 && c.getHeaderField("Location") != null) {
				url = new URL(new URL(url), c.getHeaderField("Location")).toString();
				c.disconnect();
				continue;
			}
			if (code != 200) throw new IOException("HTTP " + code + " from " + url);
			break;
		}
		long total = c.getContentLengthLong();
		InputStream in = c.getInputStream();
		OutputStream o = new FileOutputStream(out);
		try {
			byte[] buf = new byte[65536];
			long done = 0;
			int n = in.read(buf);
			if (n < 2 || buf[0] != 'P' || buf[1] != 'K') throw new IOException("The download wasn't a zip file (got a web page?)");
			while (n > 0) {
				if (pm != null && pm.cancelled) return;
				o.write(buf, 0, n);
				done += n;
				if (pm != null && total > 0) pm.update((int) (done * 100 / total), (done / 1024) + " of " + (total / 1024) + " KB");
				n = in.read(buf);
			}
		} finally {
			o.close();
			in.close();
		}
	}

	static void unzip(File zip, File into) throws IOException {
		ZipInputStream z = new ZipInputStream(new FileInputStream(zip));
		try {
			ZipEntry e;
			byte[] buf = new byte[65536];
			while ((e = z.getNextEntry()) != null) {
				File f = new File(into, e.getName());
				if (!f.getCanonicalPath().startsWith(into.getCanonicalPath())) throw new IOException("Bad zip entry: " + e.getName());
				if (e.isDirectory()) { f.mkdirs(); continue; }
				f.getParentFile().mkdirs();
				OutputStream o = new FileOutputStream(f);
				try {
					int n;
					while ((n = z.read(buf)) > 0) o.write(buf, 0, n);
				} finally { o.close(); }
			}
		} finally { z.close(); }
	}

	/**
	 * Slipstream's command line needs the FTL folder in modman.cfg ("No FTL dats path previously set" otherwise).
	 * Fills it in from the station's own setting when it's missing, and leaves everything else alone.
	 */
	public static void prepareConfig(File dir) {
		File cfg = new File(dir, "modman.cfg");
		Properties p = new Properties();
		if (cfg.isFile()) {
			try {
				InputStream in = new FileInputStream(cfg);
				try { p.load(in); } finally { in.close(); }
			} catch (IOException e) {
				log.warn("Could not read " + cfg, e);
				return;
			}
		}
		String have = p.getProperty("ftl_dats_path");
		if (have != null && have.length() > 0 && new File(have, "ftl.dat").isFile()) return;
		p.setProperty("ftl_dats_path", HomePlanet.datsPath.getAbsolutePath());
		try {
			OutputStream out = new FileOutputStream(cfg);
			try { p.store(out, "Slipstream Mod Manager config (FTL folder filled in by Federation Home Planet)"); } finally { out.close(); }
			log.debug("Wrote ftl_dats_path to {}", cfg);
		} catch (IOException e) {
			log.warn("Could not write " + cfg, e);
		}
	}

	/** The .ftl files in Slipstream's mods folder, in the order of mods/modorder.txt (unknown ones after, by name). */
	public static List<Mod> mods(File dir) {
		File mdir = modsDir(dir);
		List<Mod> out = new ArrayList<Mod>();
		File[] files = mdir.listFiles();
		if (files == null) return out;
		java.util.Arrays.sort(files);
		for (File f : files) {
			if (f.isFile() && f.getName().toLowerCase().endsWith(".ftl")) {
				String[] meta = metadataOf(f);
				out.add(new Mod(f, meta[0], meta[1]));
			}
		}
		List<String> order = readOrder(mdir);
		if (!order.isEmpty()) {
			List<Mod> sorted = new ArrayList<Mod>();
			for (String name : order) {
				for (Mod m : out) if (m.name().equals(name) && !sorted.contains(m)) sorted.add(m);
			}
			for (Mod m : out) if (!sorted.contains(m)) sorted.add(m);
			out = sorted;
		}
		return out;
	}

	static List<String> readOrder(File mdir) {
		List<String> order = new ArrayList<String>();
		File f = new File(mdir, "modorder.txt");
		if (!f.isFile()) return order;
		try {
			BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
			try {
				String line;
				while ((line = r.readLine()) != null) {
					line = line.trim();
					if (line.length() > 0) order.add(line);
				}
			} finally { r.close(); }
		} catch (IOException e) {
			log.warn("Could not read " + f, e);
		}
		return order;
	}

	private static final Pattern TITLE = Pattern.compile("<title>\\s*(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?\\s*</title>", Pattern.DOTALL);
	private static final Pattern AUTHOR = Pattern.compile("<author>\\s*(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?\\s*</author>", Pattern.DOTALL);

	public static String titleOf(File ftl) { return metadataOf(ftl)[0]; }

	/** {title, author} from mod-appendix/metadata.xml; the title falls back to the file name without .ftl, the author to "". */
	public static String[] metadataOf(File ftl) {
		String fallback = ftl.getName().replaceFirst("(?i)\\.ftl$", "");
		String author = "";
		try {
			ZipFile z = new ZipFile(ftl);
			try {
				ZipEntry e = z.getEntry("mod-appendix/metadata.xml");
				if (e == null) return new String[] {fallback, author};
				InputStream in = z.getInputStream(e);
				byte[] b = new byte[(int) Math.min(e.getSize() < 0 ? 65536 : e.getSize(), 1 << 20)];
				int n = 0, k;
				while (n < b.length && (k = in.read(b, n, b.length - n)) > 0) n += k;
				in.close();
				String xml = new String(b, 0, n, "UTF-8");
				Matcher a = AUTHOR.matcher(xml);
				if (a.find()) author = a.group(1).trim();
				Matcher m = TITLE.matcher(xml);
				if (m.find() && m.group(1).trim().length() > 0) return new String[] {m.group(1).trim(), author};
			} finally { z.close(); }
		} catch (Exception e) {
			log.debug("No readable metadata in {}: {}", ftl.getName(), e.toString());
		}
		return new String[] {fallback, author};
	}

	/** Builds the companion mod (plain copies plus every remodel) into Slipstream's mods folder. Returns the file, or null. */
	public static File installMod(File dir) {
		File dst = new File(modsDir(dir), MOD_FILE);
		HomePlanet.modPatchedThisSession = false; // a new build isn't in the game until it's patched
		try {
			modsDir(dir).mkdirs();
			homeplanet.parser.CompanionMod.build(dst, homeplanet.parser.CompanionMod.load(), HomePlanet.version());
			return dst;
		} catch (IOException e) {
			log.warn("Could not write the companion mod to " + dst, e);
			return null;
		}
	}

	/** Builds the companion mod where it belongs: Slipstream's mods folder if known, else beside the station. Returns the file, or null. */
	public static File writeMod() {
		HomePlanet.modPatchedThisSession = false;
		File d = dir();
		if (d != null) return installMod(d);
		File dst = new File(MOD_FILE).getAbsoluteFile();
		try {
			homeplanet.parser.CompanionMod.build(dst, homeplanet.parser.CompanionMod.load(), HomePlanet.version());
			return dst;
		} catch (IOException e) {
			log.warn("Could not write the companion mod to " + dst, e);
			return null;
		}
	}

	/** What a patch run produced. */
	public static class Result {
		public final int exitCode;
		public final String output;
		Result(int exitCode, String output) { this.exitCode = exitCode; this.output = output; }
		public boolean ok() { return exitCode == 0; }
	}

	/** The java that runs the station: Slipstream is a jar too, so it runs with the same one (no admin prompt). */
	static String javaExe() {
		String home = System.getProperty("java.home");
		boolean win = System.getProperty("os.name").startsWith("Windows");
		File j = new File(home, "bin" + File.separator + (win ? "java.exe" : "java"));
		return j.isFile() ? j.getAbsolutePath() : "java";
	}

	/** Runs "java -jar modman.jar --patch [mods] [--runftl]" in Slipstream's folder and waits for it. Blocks: call off the UI thread. */
	public static Result patch(File dir, List<String> modNames, boolean runFtl) {
		List<String> cmd = new ArrayList<String>();
		cmd.add(javaExe());
		cmd.add("-jar");
		cmd.add("modman.jar");
		cmd.add("--patch");
		cmd.addAll(modNames);
		if (runFtl) cmd.add("--runftl");
		log.debug("Running Slipstream: {}", cmd);
		StringBuilder out = new StringBuilder();
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.directory(dir);
			pb.redirectErrorStream(true);
			Process p = pb.start();
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			while ((line = r.readLine()) != null) {
				if (line.startsWith("Picked up ")) continue; // JVM chatter about JAVA_TOOL_OPTIONS
				out.append(line).append('\n');
			}
			int code = p.waitFor();
			log.debug("Slipstream exited with {}", code);
			return new Result(code, out.toString());
		} catch (Exception e) {
			log.warn("Could not run Slipstream", e);
			return new Result(-1, out.toString() + e);
		}
	}

	/** Writes mods/modorder.txt the way Slipstream's GUI does (one file name per line), so both agree on the order. */
	public static void writeOrder(File dir, List<String> modNames) {
		File f = new File(modsDir(dir), "modorder.txt");
		try {
			java.io.Writer w = new java.io.OutputStreamWriter(new FileOutputStream(f), "UTF-8");
			try {
				for (String n : modNames) w.write(n + "\r\n");
			} finally { w.close(); }
		} catch (IOException e) {
			log.warn("Could not write " + f, e);
		}
	}

	/** Starts a fresh copy of the station (same jar, same folder) and exits this one. */
	public static void restart() {
		try {
			File jar = new File(HomePlanet.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			ProcessBuilder pb = new ProcessBuilder(javaExe(), "-jar", jar.getAbsolutePath());
			pb.directory(new File(".").getAbsoluteFile().getParentFile());
			pb.start();
		} catch (Exception e) {
			log.warn("Could not restart", e);
			JOptionPane.showMessageDialog(null, "Federation Home Planet couldn't restart itself. Please start it again yourself.", "Restart", JOptionPane.WARNING_MESSAGE);
			return;
		}
		System.exit(0);
	}
}
