package homeplanet.parser;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.SafeFiles;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * The Federation Museum: the Hall of Victors (every ship that won, whatever came after) and the Memorial (ships lost
 * in action without a victory). What the saves can't say is kept in each ship's history folder, museum.txt: her
 * victories (date, score, difficulty, the honours earned during her command), whether she was kept or preserved, her
 * epitaph, when she was commissioned, and the profile's achievements when she was last set out.
 */
public final class Museum {
	private static final Logger log = LoggerFactory.getLogger(Museum.class);
	private Museum() { }

	static final String FILE = "museum.txt";
	public enum Status { PRESERVED, IN_SERVICE, MEMORY, LOST, MEMORIAL, TRANSFERRED }

	/** One ship on show. */
	public static final class Exhibit {
		public final String id, name;
		public final boolean victor;
		public final Status status;
		/** Her save to show: the last victory's copy, or her last kept version. */
		public final File save;
		public final int victories;
		/** The sector she was lost in (Lost in Action, the Memorial), 0 if not known. */
		public final int lostSector;
		final Properties p;
		Exhibit(String id, String name, boolean victor, Status status, File save, int victories, int lostSector, Properties p) {
			this.id = id; this.name = name; this.victor = victor; this.status = status; this.save = save; this.victories = victories; this.lostSector = lostSector; this.p = p;
		}
		public String get(String key) { return p.getProperty(key, ""); }
		/** The victories' details, oldest first: date, score, difficulty, sector, honours (names, | between). */
		public List<String[]> victoryDetails() {
			List<String[]> out = new ArrayList<String[]>();
			for (int k = 1; k <= victories; k++) out.add(new String[] {get("victory." + k + ".date"), get("victory." + k + ".score"),
					get("victory." + k + ".difficulty"), get("victory." + k + ".sector"), get("victory." + k + ".honours")});
			return out;
		}
		public String epitaph() { return get("epitaph"); }
	}

	// ---- the records ----

	static File dir(Vault v, String id) { return new File(v.historyDir(), id); }
	static Properties read(Vault v, String id) {
		Properties p = new Properties();
		File f = new File(dir(v, id), FILE);
		if (!f.isFile()) return p;
		try { p.load(new StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); }
		return p;
	}
	static void write(Vault v, String id, Properties p) {
		File d = dir(v, id);
		try {
			if (!d.isDirectory() && !d.mkdirs()) throw new IOException("Could not create " + d);
			StringWriter w = new StringWriter();
			p.store(w, "Her place in the Federation Museum");
			SafeFiles.writeText(new File(d, FILE), w.toString(), false);
		} catch (IOException e) {
			log.warn("Could not keep {}'s museum record: {}", id, e.toString());
		}
	}
	private static String today() { return new SimpleDateFormat("d MMMM yyyy").format(new Date()); }

	/** The station set her out (commissioned, a New Journey, rescued): the profile's achievements now start her command's honours. */
	public static void setOut(Vault v, Ship s, boolean commissioned) {
		Properties p = read(v, s.id);
		if (commissioned && p.getProperty("commissioned", "").isEmpty()) p.setProperty("commissioned", today());
		Unlocks u = Unlocks.read();
		if (u.problem() == null) p.setProperty("achievementsAtStart", String.join("|", u.achievements()));
		p.setProperty("name", s.name == null ? "" : s.name);
		write(v, s.id, p);
	}
	/**
	 * She won: a victory in her record, with the date, her Top Scores entry (score, difficulty) and the honours earned
	 * during her command. Recorded once for each copy (settling again doesn't count it twice).
	 */
	static void recordVictory(Vault v, Vault.FinalBattle f, SavedGameState gs, Unlocks u) {
		String key;
		try { key = SafeFiles.hash(f.copy); } catch (IOException e) { key = f.copy.getName() + f.copy.lastModified(); }
		Properties p = read(v, f.id);
		int n = intOf(p, "victories");
		for (int k = 1; k <= n; k++) if (key.equals(p.getProperty("victory." + k + ".key"))) return;
		n++;
		p.setProperty("victories", Integer.toString(n));
		p.setProperty("name", f.name);
		p.setProperty("victory." + n + ".key", key);
		p.setProperty("victory." + n + ".date", today());
		p.setProperty("victory." + n + ".sector", Integer.toString(gs.getSectorNumber() + 1));
		net.blerf.ftl.model.Score best = u.bestVictoriousScore(gs.getPlayerShipName(), gs.getPlayerShipBlueprintId());
		p.setProperty("victory." + n + ".score", best == null ? "" : Integer.toString(best.getValue()));
		p.setProperty("victory." + n + ".difficulty", best == null || best.getDifficulty() == null ? difficulty(gs) : title(best.getDifficulty().toString()));
		Set<String> before = new LinkedHashSet<String>(Arrays.asList(p.getProperty("achievementsAtStart", "").split("\\|")));
		boolean known = !p.getProperty("achievementsAtStart", "").isEmpty();
		List<String> honours = new ArrayList<String>();
		if (known) for (String a : u.achievements()) {
			if (before.contains(a)) continue;
			net.blerf.ftl.xml.Achievement ach = net.blerf.ftl.parser.DataManager.get().getAchievement(a);
			if (ach == null || ach.getName() == null) continue;
			honours.add(ach.getName().getTextValue());
		}
		p.setProperty("victory." + n + ".honours", String.join("|", honours));
		p.setProperty("kept", "false");
		write(v, f.id, p);
	}
	private static String difficulty(SavedGameState gs) { return gs.getDifficulty() == null ? "" : title(gs.getDifficulty().toString()); }
	private static String title(String s) { return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase(); }
	/** She was rescued and kept. */
	static void kept(Vault v, String id) { Properties p = read(v, id); p.setProperty("kept", "true"); write(v, id, p); }
	/** She was sold to the museum, for this much. */
	static void preserved(Vault v, String id, int price) {
		Properties p = read(v, id);
		p.setProperty("preserved", "true");
		p.setProperty("price", Integer.toString(price));
		p.setProperty("preservedOn", today());
		write(v, id, p);
	}
	/** Her epitaph: one line on her plate (empty removes it). */
	public static void setEpitaph(Vault v, String id, String line) {
		Properties p = read(v, id);
		if (line == null || line.trim().isEmpty()) p.remove("epitaph"); else p.setProperty("epitaph", line.trim());
		write(v, id, p);
	}

	// ---- what's on show ----

	/** Every exhibit: the Hall of Victors first (most victories, then name), then the Memorial (by name). */
	public static List<Exhibit> exhibits(Vault v) {
		List<Exhibit> victors = new ArrayList<Exhibit>(), memorial = new ArrayList<Exhibit>();
		File[] dirs = v.historyDir().listFiles();
		if (dirs != null) for (File d : dirs) {
			if (!d.isDirectory()) continue;
			String id = d.getName();
			Properties p = read(v, id);
			File[] wins = d.listFiles(new java.io.FileFilter() { public boolean accept(File f) { return f.isFile() && f.getName().startsWith("victory-") && f.getName().endsWith(".sav"); } });
			int victories = Math.max(intOf(p, "victories"), wins == null ? 0 : wins.length);
			String[] fate = fate(d);
			Ship inFleet = v.byId(id);
			String name = inFleet != null ? inFleet.name : !p.getProperty("name", "").isEmpty() ? p.getProperty("name") : fate[1].isEmpty() ? id : fate[1];
			File last = newest(d, false);
			if (victories > 0) {
				Status st = "true".equals(p.getProperty("preserved")) || "MUSEUM".equals(fate[0]) ? Status.PRESERVED
						: inFleet != null ? Status.IN_SERVICE
						: "TRANSFERRED".equals(fate[0]) ? Status.TRANSFERRED
						: "LOST".equals(fate[0]) && "true".equals(p.getProperty("kept")) ? Status.LOST : Status.MEMORY;
				File show = newest(d, true);
				if (st == Status.TRANSFERRED) p.setProperty("transferredTo", fate[2]); // shown with her record (never written back)
				victors.add(new Exhibit(id, name, true, st, show != null ? show : last, victories, st == Status.LOST ? sectorOf(last) : 0, p));
			} else if ("LOST".equals(fate[0]) && inFleet == null && last != null) {
				memorial.add(new Exhibit(id, name, false, Status.MEMORIAL, last, 0, sectorOf(last), p));
			}
		}
		java.util.Collections.sort(victors, new Comparator<Exhibit>() {
			public int compare(Exhibit a, Exhibit b) { return a.victories != b.victories ? b.victories - a.victories : a.name.compareToIgnoreCase(b.name); }
		});
		java.util.Collections.sort(memorial, new Comparator<Exhibit>() {
			public int compare(Exhibit a, Exhibit b) { return a.name.compareToIgnoreCase(b.name); }
		});
		List<Exhibit> out = new ArrayList<Exhibit>(victors);
		out.addAll(memorial);
		return out;
	}
	/** Is there anything to show (the Space Dock's Museum button)? */
	public static boolean anything(Vault v) { return !exhibits(v).isEmpty(); }
	/** The fleet's victories, all ships together. */
	public static int totalVictories(List<Exhibit> all) {
		int n = 0;
		for (Exhibit e : all) n += e.victories;
		return n;
	}

	private static String[] fate(File d) {
		try {
			String[] l = new String(SafeFiles.read(new File(d, "fate.txt")), StandardCharsets.UTF_8).split("\n");
			return new String[] {l[0].trim(), l.length > 1 ? l[1].trim() : "", l.length > 2 ? l[2].trim() : ""};
		} catch (IOException e) {
			return new String[] {"", "", ""};
		}
	}
	/** Her newest kept save: a victory's copy (victory) or any version. */
	private static File newest(File d, final boolean victory) {
		File[] fs = d.listFiles(new java.io.FileFilter() {
			public boolean accept(File f) { return f.isFile() && f.getName().endsWith(".sav") && (!victory || f.getName().startsWith("victory-")); }
		});
		if (fs == null || fs.length == 0) return null;
		Arrays.sort(fs, new Comparator<File>() {
			public int compare(File a, File b) { int c = Long.compare(a.lastModified(), b.lastModified()); return c != 0 ? c : a.getName().compareTo(b.getName()); }
		});
		return fs[fs.length - 1];
	}
	private static int sectorOf(File save) {
		if (save == null) return 0;
		try { return homeplanet.core.HomePlanet.savedGameParser.readSavedGame(save).getSectorNumber() + 1; } catch (Exception e) { return 0; }
	}
	private static int intOf(Properties p, String k) {
		try { return Integer.parseInt(p.getProperty(k, "0").trim()); } catch (NumberFormatException e) { return 0; }
	}
}
