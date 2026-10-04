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
 * goes to the stored systems, broken bars and all. A part's worth is its price as Commission counts it, at the difficulty's
 * rate (Pricing.rate; the rolls and the clearance come off that). One set in SALVAGE_ONE_IN also has a piece of salvage: most
 * often missiles, fuel or drone parts, sometimes a weapon, drone or augment, at 40-70% of FTL's store price, to the
 * Cargo Hold. New ones come in after 5 to 15 beacons the fleet travels.
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
	/** A Piloting, Oxygen or Engines part at level 1 (FTL's upgrade costs on top): the same price everywhere. */
	public static final int CORE_PART = Pricing.CORE_SYSTEM;
	/** One set in this many has a piece of salvage; one piece in GEAR_ONE_IN is a weapon, drone or augment. */
	public static final int SALVAGE_ONE_IN = 5, GEAR_ONE_IN = 4;
	/** Salvage sells at this share of FTL's store price. */
	public static final int SALVAGE_MIN = 40, SALVAGE_MAX = 70;
	/** Kinds of listing: a damaged system, or salvage (an item, or a bundle of a supply). */
	public static final String SYSTEM = "system", ITEM = "item", FUEL = "fuel", MISSILES = "missiles", DRONE_PARTS = "parts";

	/** One part for sale. */
	public static final class Listing {
		public final int index;
		public final String id;
		public final int level, broken, price;
		public final boolean clearance;
		/** SYSTEM, or salvage: ITEM (id is the weapon, drone or augment), FUEL, MISSILES or DRONE_PARTS (count of them). */
		public final String kind;
		public final int count;
		Listing(int index, String id, int level, int broken, int price, boolean clearance) { this(index, SYSTEM, id, level, broken, 0, price, clearance); }
		Listing(int index, String kind, String id, int level, int broken, int count, int price, boolean clearance) {
			this.index = index; this.kind = kind; this.id = id; this.level = level; this.broken = broken; this.count = count; this.price = price; this.clearance = clearance;
		}
		public boolean salvage() { return !SYSTEM.equals(kind); }
		/** Salvage in words: "Missiles (3)", or the item's name. */
		public String title() {
			if (ITEM.equals(kind)) return homeplanet.model.Items.title(id);
			if (FUEL.equals(kind)) return "Fuel (" + count + ")";
			if (MISSILES.equals(kind)) return "Missiles (" + count + ")";
			if (DRONE_PARTS.equals(kind)) return "Drone parts (" + count + ")";
			return homeplanet.model.Items.systemTitle(id);
		}
		/** FTL's store price for the salvage. */
		public int storePrice() { return Parts.storePrice(kind, id, count); }
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
			String kind = p.getProperty(i + ".kind", SYSTEM);
			if (!SYSTEM.equals(kind)) {
				String id = p.getProperty(i + ".id", "");
				int count = Math.max(1, intOf(p, i + ".count", 1));
				if (ITEM.equals(kind) && Pricing.item(id) <= 0) continue;
				int sp = storePrice(kind, id, count);
				out.add(new Listing(i, kind, id, 0, 0, count, Math.max(3, sp * intOf(p, i + ".percent", SALVAGE_MAX) / 100), false));
				continue;
			}
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
		if (n > 0 && rng.nextInt(SALVAGE_ONE_IN) == 0) { // a piece of salvage after the systems
			String kind, id = "";
			int count = 1;
			if (rng.nextInt(GEAR_ONE_IN) == 0) {
				kind = ITEM;
				id = gear(rng);
				if (id == null) { kind = FUEL; count = 3; }
			} else {
				int k = rng.nextInt(3);
				kind = k == 0 ? FUEL : k == 1 ? MISSILES : DRONE_PARTS;
				count = k == 0 ? 3 + rng.nextInt(4) : k == 1 ? 2 + rng.nextInt(4) : 2 + rng.nextInt(3);
			}
			p.setProperty(n + ".open", "true");
			p.setProperty(n + ".kind", kind);
			p.setProperty(n + ".id", id);
			p.setProperty(n + ".count", Integer.toString(count));
			p.setProperty(n + ".percent", Integer.toString(SALVAGE_MIN + rng.nextInt(SALVAGE_MAX - SALVAGE_MIN + 1)));
			p.setProperty("count", Integer.toString(n + 1));
		}
		write(v, p);
	}
	static int maxLevel(String id) {
		SystemBlueprint b = DataManager.get().getSystem(id);
		return b == null || b.getMaxPower() <= 0 ? 2 : b.getMaxPower();
	}

	/** A weapon, drone or augment FTL's stores sell (no artillery, nothing unpriced). */
	static String gear(Random rng) {
		List<String> from = new ArrayList<String>();
		int k = rng.nextInt(3);
		if (k == 0) { for (net.blerf.ftl.xml.WeaponBlueprint w : DataManager.get().getWeapons().values()) if (w.getCost() > 0 && w.getRarity() > 0 && !w.getId().startsWith("ARTILLERY")) from.add(w.getId()); }
		else if (k == 1) { for (net.blerf.ftl.xml.DroneBlueprint d : DataManager.get().getDrones().values()) if (d.getCost() > 0 && d.getRarity() > 0) from.add(d.getId()); }
		else { for (net.blerf.ftl.xml.AugBlueprint a : DataManager.get().getAugments().values()) if (a.getCost() > 0 && a.getRarity() > 0) from.add(a.getId()); }
		java.util.Collections.sort(from);
		return from.isEmpty() ? null : from.get(rng.nextInt(from.size()));
	}
	static int storePrice(String kind, String id, int count) {
		if (ITEM.equals(kind)) return Pricing.item(id);
		return count * (FUEL.equals(kind) ? Pricing.FUEL : MISSILES.equals(kind) ? Pricing.MISSILE : Pricing.DRONE_PART);
	}
	/** What a part is worth whole and new: its price as Commission counts it (CORE_PART and upgrades for Piloting, Oxygen and Engines), at the difficulty's rate. */
	public static int worth(String id, int level) {
		return Pricing.rated(Pricing.system(id, level));
	}

	/** Its worth with its broken bars off, at this share, 10% less for a clearance; never under 5. */
	public static int price(String id, int level, int broken, int percent, boolean clearance) {
		int p = Math.max(0, worth(id, level) - broken * Pricing.brokenBarValue(id)) * percent / 100;
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
		if (have < l.price) throw new IOException("The Cargo Hold holds " + have + " scrap; " + (l.salvage() ? "it costs " : "the part costs ") + l.price);
		c.save.getPlayerShip().setScrapAmt(have - l.price);
		if (l.salvage()) {
			net.blerf.ftl.parser.SavedGameParser.ShipState h = c.save.getPlayerShip();
			if (FUEL.equals(l.kind)) h.setFuelAmt(h.getFuelAmt() + l.count);
			else if (MISSILES.equals(l.kind)) h.setMissilesAmt(h.getMissilesAmt() + l.count);
			else if (DRONE_PARTS.equals(l.kind)) h.setDronePartsAmt(h.getDronePartsAmt() + l.count);
			else if (homeplanet.model.Items.isWeapon(l.id)) h.getWeaponList().add(SaveHelper.newIdleWeapon(l.id));
			else if (homeplanet.model.Items.isDrone(l.id)) h.getDroneList().add(SaveHelper.newIdleDrone(l.id));
			else h.getAugmentIdList().add(l.id);
			v.begin().put(st, c.save, c.hash).commit();
			p.setProperty(l.index + ".open", "false");
			try { write(v, p); } catch (IOException e) { log.warn("Could not mark salvage {} sold: {}", l.index, e.toString()); }
			HistoryLog.entry("BUY", l.title() + ", salvage from the Junkyard, for " + l.price + " scrap from the Cargo Hold");
			return;
		}
		File f = v.systemsFile();
		List<String> lines = new ArrayList<String>();
		if (f.isFile()) lines.addAll(java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
		else lines.add(homeplanet.ui.SystemsPanel.HEADER);
		lines.add(homeplanet.ui.SystemsPanel.line(l.id, l.level, l.broken));
		v.begin().put(st, c.save, c.hash).put(f, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8)).commit();
		p.setProperty(l.index + ".open", "false");
		try { write(v, p); }
		catch (IOException e) { log.warn("Could not mark part {} sold: {}", l.index, e.toString()); } // bought all the same: at worst it's offered again
		ThirdFleet.partBought(v); // the Third Fleet Commander needn't point the way to them
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
