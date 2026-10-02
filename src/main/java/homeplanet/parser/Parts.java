package homeplanet.parser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.xml.SystemBlueprint;

import homeplanet.core.HistoryLog;
import homeplanet.core.SafeFiles;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * The Junkyard's parts for sale: two to five damaged systems pulled from wrecks, mostly low levels, priced by how
 * broken they are: a part with one bar of five broken sells near its worth (its broken bars off), a part broken through
 * for a third to a half of it. One in CLEARANCE_ONE_IN is a clearance, 10% off. Bought with scrap from the Cargo Hold, a part
 * goes to the stored systems, broken bars and all. New ones come in after 5 to 15 beacons the fleet travels.
 * Kept in the fleet's parts.txt.
 */
public final class Parts {
	private static final Logger log = LoggerFactory.getLogger(Parts.class);
	private Parts() { }

	/** Parts at a time: at least, at most. */
	public static final int MIN = 2, MAX = 5;
	/** Beacons until new ones come in: 5 to 15, rolled with each set. */
	static int interval(Random rng) { return 5 + rng.nextInt(11); }
	/** The share of its worth a part sells for, by how much of it is broken (0 to 1): from 75 - 45f to 95 - 45f percent. */
	static int shareMin(double broken) { return (int) Math.round(75 - 45 * broken); }
	static int shareMax(double broken) { return (int) Math.round(95 - 45 * broken); }
	/** One part in this many is a clearance (the foreman wants it gone): this much off its price. */
	public static final int CLEARANCE_ONE_IN = 12, CLEARANCE_OFF = 10;

	/** One part for sale. */
	public static final class Listing {
		public final int index;
		public final String id;
		public final int level, broken, price;
		public final boolean clearance;
		Listing(int index, String id, int level, int broken, int price, boolean clearance) {
			this.index = index; this.id = id; this.level = level; this.broken = broken; this.price = price; this.clearance = clearance;
		}
	}

	private static File file(Vault v) { return new File(v.root, "parts.txt"); }

	/** What's for sale now: new parts first if it's time (or there have never been any). */
	public static synchronized List<Listing> current(Vault v) {
		Properties p = read(v);
		int at = intOf(p, "rolledAt", -1);
		if (at < 0 || v.beaconsSeen() >= at + intOf(p, "interval", 10)) {
			try { roll(v, new Random()); p = read(v); }
			catch (Exception e) { log.warn("Could not bring in new parts: {}", e.toString()); }
		}
		List<Listing> out = new ArrayList<Listing>();
		for (int i = 0; i < intOf(p, "count", 0); i++) {
			if (!"true".equals(p.getProperty(i + ".open"))) continue;
			String id = p.getProperty(i + ".id", "");
			if (SystemType.findById(id) == null) continue;
			int level = Math.max(1, intOf(p, i + ".level", 1)), broken = Math.max(1, Math.min(level, intOf(p, i + ".broken", 1)));
			boolean clearance = "true".equals(p.getProperty(i + ".clearance"));
			int pct = intOf(p, i + ".percent", shareMax((double) broken / level));
			out.add(new Listing(i, id, level, broken, price(id, level, broken, pct, clearance), clearance));
		}
		return out;
	}
	/** How many parts the current set had, sold ones included (for the empty spaces). */
	public static int count(Vault v) { return intOf(read(v), "count", 0); }

	/** New parts, now (the old ones go). */
	public static synchronized void roll(Vault v, Random rng) throws IOException {
		List<SystemType> kinds = new ArrayList<SystemType>();
		for (SystemType t : SystemType.values())
			if (t != SystemType.ARTILLERY && t != SystemType.CLONEBAY && DataManager.get().getSystem(t.getId()) != null) kinds.add(t); // a Clone Bay's damage is the Medbay's
		Properties p = new Properties();
		p.setProperty("rolledAt", Integer.toString(v.beaconsSeen()));
		p.setProperty("interval", Integer.toString(interval(rng)));
		int n = kinds.isEmpty() ? 0 : MIN + rng.nextInt(MAX - MIN + 1);
		p.setProperty("count", Integer.toString(n));
		for (int i = 0; i < n; i++) {
			SystemType t = kinds.get(rng.nextInt(kinds.size()));
			int max = maxLevel(t.getId());
			int level = 1 + Math.min(rng.nextInt(max), rng.nextInt(max)); // the lower of two rolls: mostly low levels
			p.setProperty(i + ".open", "true");
			p.setProperty(i + ".id", t.getId());
			p.setProperty(i + ".level", Integer.toString(level));
			int broken = 1 + rng.nextInt(level);
			double f = (double) broken / level;
			p.setProperty(i + ".broken", Integer.toString(broken));
			p.setProperty(i + ".percent", Integer.toString(shareMin(f) + rng.nextInt(shareMax(f) - shareMin(f) + 1)));
			p.setProperty(i + ".clearance", Boolean.toString(rng.nextInt(CLEARANCE_ONE_IN) == 0));
		}
		write(v, p);
	}
	static int maxLevel(String id) {
		SystemBlueprint b = DataManager.get().getSystem(id);
		return b == null || b.getMaxPower() <= 0 ? 2 : b.getMaxPower();
	}

	/** Its worth with its broken bars off, at this share, 10% less for a clearance; never under 5. */
	public static int price(String id, int level, int broken, int percent, boolean clearance) {
		int p = Math.max(0, Pricing.system(id, level) - broken * Pricing.brokenBarValue(id)) * percent / 100;
		if (clearance) p = p * (100 - CLEARANCE_OFF) / 100;
		return Math.max(5, p);
	}

	/** Buys a part: the price from the Cargo Hold and the part into the stored systems, together or not at all. */
	public static synchronized void buy(Vault v, Listing l) throws IOException {
		Properties p = read(v);
		if (!"true".equals(p.getProperty(l.index + ".open"))) throw new IOException("It has already been sold");
		Ship st = v.storage();
		Vault.Copy c = v.readCopy(st);
		int have = c.save.getPlayerShip().getScrapAmt();
		if (have < l.price) throw new IOException("The Cargo Hold holds " + have + " scrap; the part costs " + l.price);
		c.save.getPlayerShip().setScrapAmt(have - l.price);
		File f = v.systemsFile();
		List<String> lines = new ArrayList<String>();
		if (f.isFile()) lines.addAll(java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
		else lines.add(homeplanet.ui.SystemsPanel.HEADER);
		lines.add(homeplanet.ui.SystemsPanel.line(l.id, l.level, l.broken));
		v.begin().put(st, c.save, c.hash).put(f, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8)).commit();
		p.setProperty(l.index + ".open", "false");
		try { write(v, p); }
		catch (IOException e) { log.warn("Could not mark part {} sold: {}", l.index, e.toString()); } // bought all the same: at worst it's offered again
		HistoryLog.entry("BUY", homeplanet.model.Items.systemTitle(l.id) + " level " + l.level + " (" + l.broken + " broken), a part from the Junkyard" + (l.clearance ? " on clearance" : "") + ", for " + l.price + " scrap from the Cargo Hold");
	}

	// ---- parts.txt ----

	private static Properties read(Vault v) {
		Properties p = new Properties();
		File f = file(v);
		if (!f.isFile()) return p;
		try { p.load(new java.io.StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); }
		return p;
	}
	private static void write(Vault v, Properties p) throws IOException {
		java.io.StringWriter w = new java.io.StringWriter();
		p.store(w, "The Junkyard's parts for sale");
		SafeFiles.writeText(file(v), w.toString(), false);
	}
	private static int intOf(Properties p, String key, int dflt) {
		try { return Integer.parseInt(p.getProperty(key, "").trim()); } catch (NumberFormatException e) { return dflt; }
	}
}
