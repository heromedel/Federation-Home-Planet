package homeplanet.vault;

import java.io.IOException;
import java.util.Properties;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.Store;

/**
 * The mark a ship gets when she changes hands over Long Range Comm. (her record's trade section, 5.98; traded.txt before): when, from whom,
 * who first commissioned her, and her lifetime totals at that moment.
 *
 * <p>The rule it serves: <b>anything that rewards or reacts to what a ship has done</b> (events, rewards, letters,
 * achievements, and whatever comes later) <b>counts only what she did since her last trade</b>, and asks here. What
 * the station shows (the Space Dock, her records, the Museum) keeps her whole life; this only adds who she came from.
 * A ship never traded has no mark, and everything since her commissioning counts.
 */
public final class TradeMark {
	/** Her mark as a file in a ship's package, as older stations read it. */
	static final String FILE = "traded.txt";
	private static final String NOTE = "She joined this fleet over Long Range Comm. Rewards, letters, events and achievements count only what she did after this.";

	public final String trade, date, from, original;
	/** When her original owner commissioned her ("1 October 2026"), or "" if not known. */
	public final String commissioned;
	/** Her FTL totals (they only go up) and the sectors she had visited, at the trade. */
	public final int defeated, beacons, scrap, sectors;

	private TradeMark(Properties p) {
		trade = p.getProperty("trade", "");
		date = p.getProperty("date", "");
		from = p.getProperty("from", "");
		original = p.getProperty("original", from);
		commissioned = p.getProperty("commissioned", "");
		defeated = num(p, "defeated");
		beacons = num(p, "beacons");
		scrap = num(p, "scrap");
		sectors = num(p, "sectors");
	}
	private static int num(Properties p, String k) { return Math.max(0, Store.num(p, k, 0)); }

	/** Her last trade's mark, or null if she was never traded. */
	public static TradeMark of(Ship s) { return s == null ? null : of(Vault.get(), s.id); }
	/** The mark of a ship by id (she may have left the fleet), or null. */
	public static TradeMark of(Vault v, String id) {
		Properties p = ShipStore.notes(v.folderOfId(id), ShipStore.TRADE);
		return p.isEmpty() ? null : new TradeMark(p);
	}

	// ---- counting from her last trade ----

	/** Ships she has defeated since her last trade (all of them if never traded). */
	public static int defeatedSince(Ship s, SavedGameState gs) {
		TradeMark m = of(s);
		return Math.max(0, gs.getTotalShipsDefeated() - (m == null ? 0 : m.defeated));
	}
	/** Beacons she has explored since her last trade. */
	public static int beaconsSince(Ship s, SavedGameState gs) {
		TradeMark m = of(s);
		return Math.max(0, gs.getTotalBeaconsExplored() - (m == null ? 0 : m.beacons));
	}

	// ---- writing ----

	/** Her mark, for a ship arriving: this trade, this sender, the original owner carried along. */
	static Properties mark(String trade, String from, String original, String commissioned, SavedGameState gs, int sectors) throws IOException {
		Properties p = new Properties();
		p.setProperty("trade", trade);
		p.setProperty("date", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date()));
		p.setProperty("from", from);
		p.setProperty("original", original);
		if (commissioned != null && !commissioned.isEmpty()) p.setProperty("commissioned", commissioned);
		p.setProperty("defeated", Integer.toString(gs.getTotalShipsDefeated()));
		p.setProperty("beacons", Integer.toString(gs.getTotalBeaconsExplored()));
		p.setProperty("scrap", Integer.toString(gs.getTotalScrapCollected()));
		p.setProperty("sectors", Integer.toString(sectors));
		return p;
	}
	/** A mark as its file in a package (older stations read her mark from it). */
	static byte[] fileBytes(Properties p) throws IOException { return Store.bytes(p, NOTE); }
	/** The original owner a mark file names, or null (for a ship arriving with her old mark). */
	static String originalIn(byte[] markFile) {
		if (markFile == null) return null;
		Properties p;
		try { p = Store.parse(markFile); } catch (IOException e) { return null; }
		String o = p.getProperty("original", p.getProperty("from", "")).trim();
		return o.isEmpty() ? null : o;
	}
}
