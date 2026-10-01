package homeplanet.core;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches FTL's saves folder while the station is open (the system tells it of changes; no timed reading of files).
 * When FTL has written continue.sav and gone quiet for a moment, the final-victory watch looks at it (on the event
 * thread, like everything else that touches the vault). When FTL deletes it, the Space Dock is refreshed the next time
 * its window comes to the front.
 */
public final class SaveWatcher implements Runnable {
	private static final Logger log = LoggerFactory.getLogger(SaveWatcher.class);
	/** How long FTL must be quiet before continue.sav is read (it writes in bursts, and a save mid-write doesn't read). */
	private static final long QUIET_MS = 800;
	private static Thread thread;
	private static volatile boolean gone = false;

	private SaveWatcher() { }

	/** Starts watching (once). */
	public static synchronized void start() {
		if (thread != null) return;
		thread = new Thread(new SaveWatcher(), "Save watcher");
		thread.setDaemon(true);
		thread.start();
	}
	/** Did continue.sav disappear since this was last asked? */
	public static boolean takeGone() {
		boolean g = gone;
		gone = false;
		return g;
	}

	@Override
	public void run() {
		WatchService ws;
		try {
			ws = FileSystems.getDefault().newWatchService();
		} catch (Exception e) {
			log.warn("Could not watch FTL's saves folder (final victories need it): {}", e.toString());
			return;
		}
		File dir = null;
		WatchKey key = null;
		long changedAt = 0;
		while (true) {
			try {
				File want = HomePlanet.save_location; // Settings may change it
				if (want != null && !want.equals(dir) && want.isDirectory()) {
					if (key != null) key.cancel();
					key = want.toPath().register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
					dir = want;
					log.debug("Watching {}", dir);
				}
				WatchKey k = ws.poll(250, TimeUnit.MILLISECONDS);
				if (k != null) {
					for (WatchEvent<?> e : k.pollEvents()) {
						if (!(e.context() instanceof Path) || !"continue.sav".equalsIgnoreCase(((Path) e.context()).toString())) continue;
						if (e.kind() == StandardWatchEventKinds.ENTRY_DELETE) { gone = true; changedAt = 0; }
						else changedAt = System.currentTimeMillis();
					}
					k.reset();
				}
				if (changedAt != 0 && System.currentTimeMillis() - changedAt >= QUIET_MS) {
					changedAt = 0;
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							try { if (homeplanet.vault.Vault.isOpen()) homeplanet.vault.Vault.get().observeBoarded(); } catch (Exception e) { log.warn("The voyage log's look failed: {}", e.toString()); }
							try { homeplanet.parser.FinalVictory.watch(); } catch (Exception e) { log.warn("The final-victory watch failed: {}", e.toString()); }
						}
					});
				}
			} catch (InterruptedException e) {
				return;
			} catch (Exception e) {
				log.warn("Watching the saves folder: {}", e.toString());
				dir = null; // registers again
				try { Thread.sleep(2000); } catch (InterruptedException ie) { return; }
			}
		}
	}
}
