package homeplanet.core;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

import net.blerf.ftl.parser.DataManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FTL's title theme, looped while the station is open.
 * Started on launch and on Refresh (when enabled and FTL isn't running); stopped by Launch FTL.
 */
public class Music {
	private static final Logger log = LoggerFactory.getLogger(Music.class);
	private static final String TRACK = "audio/music/bp_MUS_TitleScreen.ogg";
	private static final float GAIN_DB = -8f; // a moderate background level

	/** "title_music" in the cfg; on by default. */
	public static boolean enabled = true;

	private static volatile Thread player = null;
	private static volatile int generation = 0; // bumped on stop, so an old player thread always winds down
	private static byte[] trackData = null;

	/** Plays the theme if music is on, FTL isn't running, and it isn't already playing. Checks in the background. */
	public static void refresh() {
		Thread t = new Thread(new Runnable() {
			public void run() {
				if (!enabled) { stop(); return; }
				if (isFtlRunning()) { log.debug("FTL is running; no title music"); stop(); return; }
				start();
			}
		}, "music-check");
		t.setDaemon(true);
		t.start();
	}

	public static synchronized void start() {
		if (player != null && player.isAlive()) return; // already playing: keep going
		final int gen = ++generation;
		player = new Thread(new Runnable() {
			public void run() { playLoop(gen); }
		}, "music");
		player.setDaemon(true);
		player.start();
	}

	public static synchronized void stop() {
		generation++;
		Thread p = player;
		if (p != null) p.interrupt();
		player = null;
	}

	private static void playLoop(int gen) {
		SourceDataLine line = null;
		try {
			byte[] data = loadTrack();
			if (data == null) return;
			while (gen == generation) {
				AudioInputStream ogg = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
				AudioFormat src = ogg.getFormat();
				AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16,
						src.getChannels(), src.getChannels() * 2, src.getSampleRate(), false);
				AudioInputStream in = AudioSystem.getAudioInputStream(pcm, ogg);
				if (line == null) {
					line = AudioSystem.getSourceDataLine(pcm);
					line.open(pcm);
					if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
						((FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN)).setValue(GAIN_DB);
					}
					line.start();
				}
				byte[] buf = new byte[8192];
				int n;
				// The decoder can return 0 bytes mid-stream; only -1 means the track ended
				while (gen == generation && (n = in.read(buf, 0, buf.length)) != -1) {
					if (n > 0) line.write(buf, 0, n);
				}
				in.close();
			}
		} catch (Exception e) {
			if (gen == generation) log.warn("Title music stopped: " + e);
		} finally {
			if (line != null) {
				line.stop();
				line.flush();
				line.close();
			}
		}
	}

	private static synchronized byte[] loadTrack() {
		if (trackData != null) return trackData;
		InputStream in = null;
		try {
			in = DataManager.get().getResourceInputStream(TRACK);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
			trackData = out.toByteArray();
		} catch (Exception e) {
			log.warn("Could not load the title music from ftl.dat: " + e);
		} finally {
			try { if (in != null) in.close(); } catch (Exception e) { }
		}
		return trackData;
	}

	/** True if FTLGame.exe is running (Windows task list). Elsewhere, assumes not. */
	public static boolean isFtlRunning() {
		if (!System.getProperty("os.name", "").startsWith("Windows")) return false;
		try {
			Process p = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq FTLGame.exe", "/NH").redirectErrorStream(true).start();
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String l;
			boolean found = false;
			while ((l = r.readLine()) != null) {
				if (l.toLowerCase().contains("ftlgame.exe")) found = true;
			}
			p.waitFor();
			return found;
		} catch (Exception e) {
			log.debug("Could not check for FTL: " + e);
			return false;
		}
	}
}
