package homeplanet.vault;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;
import homeplanet.parser.SaveHelper;

/**
 * The ship mark (heromedel's idea at 5.95, built 6.08; docs/CONCERNS.md 6 closed): two of FTL's state variables written
 * into her save at every Board, which FTL keeps through everything it does (Buggy Boy's test, 5.80):
 *
 *     fhp.ship.<career>.<id>    = how many times she has been boarded (1, 2, 3...)
 *     fhp.boarded.<career>.<id> = the day she was last boarded, as a date to read (20261008)
 *
 * A continue.sav the station didn't board then says whose ship it is, and from which career's fleet, so an old copy
 * Steam Cloud brought back is set aside instead of adopted as a second ship: whether it matches anything kept or not,
 * and for a ship that has left the fleet too. The count is kept in her record (section "mark"). FTL's state variables
 * hold whole numbers only, hence a count and a date rather than a time.
 */
public final class ShipMark {
	private ShipMark() {}

	static final String COUNT = "fhp.ship.", DAY = "fhp.boarded.";
	/** Her record's section: boards (the count) and day (the date of the last). */
	static final String SECTION = "mark";

	/** A mark read from a save. */
	public static final class Found {
		public final String career, id;
		public final int boards, day;
		Found(String career, String id, int boards, int day) { this.career = career; this.id = id; this.boards = boards; this.day = day; }
	}

	/** Her save with this Board's mark (any other station's or career's taken off): the bytes, and the save as read. */
	static final class Marked {
		final byte[] bytes;
		final SavedGameState gs;
		Marked(byte[] bytes, SavedGameState gs) { this.bytes = bytes; this.gs = gs; }
	}
	static Marked mark(File save, String career, String id, int boards) throws IOException {
		SavedGameState gs = parse(save);
		strip(gs);
		gs.setStateVar(COUNT + career + "." + id, boards);
		gs.setStateVar(DAY + career + "." + id, today());
		return new Marked(SaveHelper.toBytes(gs), gs);
	}
	/** The date as a number to read: 20261008. */
	static int today() { return Integer.parseInt(new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date())); }

	/** The mark in a save, or null if it has none (a New Game in FTL, a ship not boarded since 6.08) or can't be read. */
	public static Found read(File save) {
		try {
			Map<String, Integer> vars = parse(save).getStateVars();
			for (Map.Entry<String, Integer> e : vars.entrySet()) {
				if (!e.getKey().startsWith(COUNT)) continue;
				String rest = e.getKey().substring(COUNT.length());
				int dot = rest.lastIndexOf('.');
				if (dot <= 0) continue;
				String career = rest.substring(0, dot), id = rest.substring(dot + 1);
				Integer day = vars.get(DAY + rest);
				return new Found(career, id, e.getValue(), day == null ? 0 : day);
			}
		} catch (IOException e) { /* unreadable: no mark to go by */ }
		return null;
	}

	/** Her save with every station's mark taken off (a ship arriving by trade: this station marks her at her first Board here). */
	public static byte[] strip(byte[] save) throws IOException {
		File tmp = File.createTempFile("fhp-mark", ".sav");
		try {
			SafeFiles.write(tmp, save);
			SavedGameState gs = parse(tmp);
			return strip(gs) ? SaveHelper.toBytes(gs) : save;
		} finally {
			if (!tmp.delete()) tmp.deleteOnExit();
		}
	}
	/** Takes every mark off; true if there was one. */
	private static boolean strip(SavedGameState gs) {
		boolean any = false;
		for (Iterator<String> it = gs.getStateVars().keySet().iterator(); it.hasNext(); ) {
			String k = it.next();
			if (k.startsWith(COUNT) || k.startsWith(DAY)) { it.remove(); any = true; }
		}
		return any;
	}

	private static SavedGameState parse(File save) throws IOException {
		FileInputStream in = new FileInputStream(save);
		try { return HomePlanet.savedGameParser.readSavedGame(in); }
		finally { in.close(); }
	}
}
