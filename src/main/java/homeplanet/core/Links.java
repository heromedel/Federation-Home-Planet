package homeplanet.core;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * Opening a link (a web page, Steam's link that launches FTL) or a folder the way the system does (7.13). Java's own way
 * can't do it on every Linux desktop: there, the system's own command (xdg-open, which every Linux desktop has; open
 * on a Mac) does it instead.
 */
public final class Links {
	private Links() { }

	/** Opens a link in the program the system gives it: the browser for a web page, Steam for a steam:// link. */
	public static void browse(URI uri) throws IOException {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			try { Desktop.getDesktop().browse(uri); return; }
			catch (IOException | UnsupportedOperationException e) { if (!systemOpen(uri.toString())) throw new IOException(e.getMessage(), e); return; }
		}
		if (!systemOpen(uri.toString())) throw new IOException("this system has no way to open " + uri.getScheme() + " links");
	}
	/** Opens a folder or file in the program the system gives it (a folder in the file manager). */
	public static void open(File f) throws IOException {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			try { Desktop.getDesktop().open(f); return; }
			catch (IOException | UnsupportedOperationException e) { if (!systemOpen(f.getAbsolutePath())) throw new IOException(e.getMessage(), e); return; }
		}
		if (!systemOpen(f.getAbsolutePath())) throw new IOException("this system has no way to open " + f.getPath());
	}
	/** The system's own command, outside Windows (where Java's way always works): true if it started. */
	private static boolean systemOpen(String target) {
		String os = System.getProperty("os.name", "");
		if (os.startsWith("Windows")) return false;
		String cmd = os.contains("OS X") || os.startsWith("Mac") ? "open" : "xdg-open";
		try {
			new ProcessBuilder(cmd, target).redirectErrorStream(true).redirectOutput(new File("/dev/null")).start(); // never Windows here
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
