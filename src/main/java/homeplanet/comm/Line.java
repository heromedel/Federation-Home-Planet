package homeplanet.comm;

import java.util.ArrayList;
import java.util.List;

import net.blerf.ftl.parser.SavedGameParser.CrewState;

/**
 * One line of an offer: an item, an amount of a supply, a crew member, or a whole ship. Each side numbers its own
 * lines; where a line came from (which ship, fitted or in her cargo) stays on this side and never travels.
 */
public final class Line {
	public enum Kind {
		WEAPON("weapon"), DRONE("drone"), AUGMENT("augment"), CREW("crew"), SCRAP("scrap"), FUEL("fuel"), MISSILES("missiles"), PARTS("parts"), SHIP("ship"),
		/** Something a newer station offers that this one doesn't know: shown, refused, never traded. */
		OTHER("other");
		public final String key;
		Kind(String key) { this.key = key; }
		public boolean isSupply() { return this == SCRAP || this == FUEL || this == MISSILES || this == PARTS; }
		public boolean isItem() { return this == WEAPON || this == DRONE || this == AUGMENT; }
		static Kind of(String key) {
			for (Kind k : values()) if (k.key.equals(key)) return k;
			return null;
		}
	}
	/** The most of one supply a line can carry. */
	public static final int MAX_AMOUNT = 99999;

	public final int n;
	public final Kind kind;
	/** The item's blueprint id, the ship's blueprint id; empty for supplies and crew. */
	public final String id;
	/** A supply's amount; 1 otherwise. */
	public final int amount;
	/** A crew member (whole, as the save has them, less where they stood). */
	public final CrewState crew;
	/** A ship's name and class, for showing. */
	public final String name, shipClass;

	// ---- this side only ----
	/** The ship (vault id) it comes from; the Cargo Hold's id for the hold. */
	public String from = "";
	/** The source's name, for showing ("Lucky Duck", "Cargo Hold"). */
	public String fromName = "";
	/** An item in a ship's own cargo hold (FTL's four slots), rather than fitted. */
	public boolean inCargo;
	/** Why the other station can't take it (shown grey), or null. */
	public String refused;

	public Line(int n, Kind kind, String id, int amount, CrewState crew, String name, String shipClass) {
		this.n = n; this.kind = kind; this.id = id == null ? "" : id; this.amount = amount; this.crew = crew;
		this.name = name == null ? "" : name; this.shipClass = shipClass == null ? "" : shipClass;
	}
	public static Line item(int n, Kind kind, String id) { return new Line(n, kind, id, 1, null, null, null); }
	public static Line supply(int n, Kind kind, int amount) { return new Line(n, kind, null, amount, null, null, null); }
	public static Line crew(int n, CrewState c) { return new Line(n, Kind.CREW, null, 1, new CrewState(c), c.getName(), null); }
	public static Line ship(int n, String blueprint, String name, String shipClass) { return new Line(n, Kind.SHIP, blueprint, 1, null, name, shipClass); }

	/** What it is, in words: "Burst Laser II", "30 scrap", "Ripley (Engi)", "The Nightjar (Zoltan Cruiser)". */
	public String title() {
		switch (kind) {
			case SCRAP: return amount + " scrap";
			case FUEL: return amount + " fuel";
			case MISSILES: return amount + (amount == 1 ? " missile" : " missiles");
			case PARTS: return amount + (amount == 1 ? " drone part" : " drone parts");
			case CREW: return crew.getName() + " (" + homeplanet.model.Crew.raceTitle(crew) + ")";
			case SHIP: return name + (shipClass.isEmpty() ? "" : " (" + shipClass + ")");
			case OTHER: return "Something new (" + name + ")";
			default: return homeplanet.model.Items.title(id);
		}
	}
	/** The short note at the right of its row. */
	public String note() {
		switch (kind) {
			case CREW: return "crew";
			case SHIP: return "whole ship";
			case WEAPON: return "weapon";
			case DRONE: return "drone";
			case AUGMENT: return "augment";
			case OTHER: return "unknown";
			default: return "supply";
		}
	}

	// ---- on the wire ----

	void write(Wire.Msg m, String p) {
		m.put(p + "n", n).put(p + "kind", kind.key);
		if (!id.isEmpty()) m.put(p + "id", id);
		if (kind.isSupply()) m.put(p + "amount", amount);
		if (kind == Kind.SHIP) m.put(p + "name", name).put(p + "class", shipClass);
		if (crew != null) writeCrew(m, p + "c.", crew);
	}
	/** A line as the other side sent it: its shape checked here, its contents against the game data by {@link Exchange}. */
	static Line read(Wire.Msg m, String p) throws Wire.Garbled {
		int n = m.num(p + "n", 0, 1000000);
		Kind k = Kind.of(m.get(p + "kind"));
		if (k == null || k == Kind.OTHER) return new Line(n, Kind.OTHER, "", 1, null, text(m.get(p + "kind"), 24), null); // a newer station's: refused, not garbled
		String id = m.get(p + "id");
		if (id.length() > 128 || !id.matches("[A-Za-z0-9_]*")) throw new Wire.Garbled("item id");
		int amount = k.isSupply() ? m.num(p + "amount", 1, MAX_AMOUNT) : 1;
		CrewState c = k == Kind.CREW ? readCrew(m, p + "c.") : null;
		String name = k == Kind.SHIP ? text(m.get(p + "name"), 64) : null, cls = k == Kind.SHIP ? text(m.get(p + "class"), 64) : null;
		if ((k.isItem() || k == Kind.SHIP) && id.isEmpty()) throw new Wire.Garbled("no id");
		return new Line(n, k, id, amount, c, c != null ? c.getName() : name, cls);
	}
	/** Text from the other side: control characters out, cut to length. */
	public static String text(String s, int max) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length() && b.length() < max; i++) { char c = s.charAt(i); if (c >= ' ' && c != 0x7f) b.append(c); }
		return b.toString().trim();
	}

	public static void writeLines(Wire.Msg m, List<Line> lines) {
		m.put("lines", lines.size());
		for (int i = 0; i < lines.size(); i++) lines.get(i).write(m, "l" + i + ".");
	}
	public static List<Line> readLines(Wire.Msg m) throws Wire.Garbled { return readLines(m, MAX_LINES); }
	/** The most lines an offer can have; a shown hold can list more. */
	public static final int MAX_LINES = 200, MAX_SHOWN = 1000;
	static List<Line> readLines(Wire.Msg m, int max) throws Wire.Garbled {
		int count = m.num("lines", 0, max);
		List<Line> out = new ArrayList<Line>();
		for (int i = 0; i < count; i++) out.add(read(m, "l" + i + "."));
		return out;
	}

	// ---- a crew member, field by field ----

	/** A crew member on the wire: their record in the station's names ({@link homeplanet.vault.CrewRecord}), sent in the wire's. */
	private static void writeCrew(Wire.Msg m, String p, CrewState c) {
		for (java.util.Map.Entry<String, String> e : homeplanet.vault.CrewRecord.toWire(homeplanet.vault.CrewRecord.of(c)).entrySet()) m.put(p + e.getKey(), e.getValue());
	}
	/** A crew member from the wire, kept within what FTL allows ({@link homeplanet.vault.CrewRecord#crew}); garbled if they can't be one. */
	private static CrewState readCrew(Wire.Msg m, String p) throws Wire.Garbled {
		java.util.Map<String, String> f = new java.util.LinkedHashMap<String, String>();
		for (java.util.Map.Entry<String, String> e : m.fields().entrySet()) if (e.getKey().startsWith(p)) f.put(e.getKey().substring(p.length()), e.getValue());
		for (String k : new String[] {"name", "race", "male", "health", "mastery", "repairs", "kills", "evasions", "jumps", "masteries", "tints"}) if (!f.containsKey(k)) f.put(k, "");
		for (int i = 0; i < 6; i++) if (!f.containsKey("s" + i)) f.put("s" + i, "");
		try { return homeplanet.vault.CrewRecord.crew(f); }
		catch (Wire.Garbled e) { throw e; }
		catch (java.io.IOException e) { throw new Wire.Garbled(e.getMessage()); }
	}
	static int[] skills(CrewState c) {
		return new int[] {c.getPilotSkill(), c.getEngineSkill(), c.getShieldSkill(), c.getWeaponSkill(), c.getRepairSkill(), c.getCombatSkill()};
	}
	static boolean[] masteries(CrewState c) {
		return new boolean[] {c.getPilotMasteryOne(), c.getPilotMasteryTwo(), c.getEngineMasteryOne(), c.getEngineMasteryTwo(), c.getShieldMasteryOne(), c.getShieldMasteryTwo(),
				c.getWeaponMasteryOne(), c.getWeaponMasteryTwo(), c.getRepairMasteryOne(), c.getRepairMasteryTwo(), c.getCombatMasteryOne(), c.getCombatMasteryTwo()};
	}
	/** Who they are, for finding them again in a fresh read of the save: name, race, skills and record. */
	public static String signature(CrewState c) {
		StringBuilder b = new StringBuilder(c.getName()).append('|').append(c.getRace().getId()).append('|').append(c.isMale());
		for (int s : skills(c)) b.append('|').append(s);
		for (boolean f : masteries(c)) b.append(f ? '1' : '0');
		b.append('|').append(c.getRepairs()).append('|').append(c.getCombatKills()).append('|').append(c.getPilotedEvasions()).append('|').append(c.getJumpsSurvived());
		return b.toString();
	}
}
