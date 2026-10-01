package homeplanet.comm;

import java.util.ArrayList;
import java.util.List;

import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.CrewType;

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

	private static void writeCrew(Wire.Msg m, String p, CrewState c) {
		m.put(p + "name", c.getName()).put(p + "race", c.getRace().getId()).put(p + "male", c.isMale()).put(p + "health", c.getHealth());
		int[] skills = skills(c);
		for (int i = 0; i < 6; i++) m.put(p + "s" + i, skills[i]);
		boolean[] mastery = masteries(c);
		StringBuilder mb = new StringBuilder();
		for (boolean b : mastery) mb.append(b ? '1' : '0');
		m.put(p + "mastery", mb.toString());
		m.put(p + "repairs", c.getRepairs()).put(p + "kills", c.getCombatKills()).put(p + "evasions", c.getPilotedEvasions())
				.put(p + "jumps", c.getJumpsSurvived()).put(p + "masteries", c.getSkillMasteriesEarned());
		StringBuilder tb = new StringBuilder();
		for (Integer t : c.getSpriteTintIndeces()) tb.append(tb.length() == 0 ? "" : ",").append(t);
		m.put(p + "tints", tb.toString());
	}
	/**
	 * A crew member rebuilt from the fields, each kept within what FTL allows: a known race, health up to the race's
	 * most, skills up to their second level, counts that can't go negative. Everything else starts as a new
	 * crew member's would; where they stand is set when they come aboard.
	 */
	private static CrewState readCrew(Wire.Msg m, String p) throws Wire.Garbled {
		CrewType race = CrewType.findById(m.get(p + "race"));
		if (race == null) throw new Wire.Garbled("unknown crew race " + text(m.get(p + "race"), 32));
		String name = text(m.get(p + "name"), 32);
		if (name.isEmpty()) name = "Crew";
		CrewState c = new CrewState();
		c.setRace(race);
		c.setName(name);
		c.setMale(m.flag(p + "male"));
		c.setPlayerControlled(true);
		c.setHealth(Math.max(1, Math.min(race.getMaxHealth(), m.num(p + "health", 0, 100000))));
		int[] max = maxSkills(race);
		int[] s = new int[6];
		for (int i = 0; i < 6; i++) s[i] = Math.min(max[i], m.num(p + "s" + i, 0, 100000));
		c.setPilotSkill(s[0]); c.setEngineSkill(s[1]); c.setShieldSkill(s[2]); c.setWeaponSkill(s[3]); c.setRepairSkill(s[4]); c.setCombatSkill(s[5]);
		String mb = m.get(p + "mastery");
		if (!mb.matches("[01]{12}")) throw new Wire.Garbled("mastery flags");
		boolean[] f = new boolean[12];
		for (int i = 0; i < 12; i++) f[i] = mb.charAt(i) == '1';
		for (int i = 0; i < 6; i++) { if (f[i * 2 + 1]) f[i * 2] = true; } // a second level has the first
		c.setPilotMasteryOne(f[0]); c.setPilotMasteryTwo(f[1]); c.setEngineMasteryOne(f[2]); c.setEngineMasteryTwo(f[3]);
		c.setShieldMasteryOne(f[4]); c.setShieldMasteryTwo(f[5]); c.setWeaponMasteryOne(f[6]); c.setWeaponMasteryTwo(f[7]);
		c.setRepairMasteryOne(f[8]); c.setRepairMasteryTwo(f[9]); c.setCombatMasteryOne(f[10]); c.setCombatMasteryTwo(f[11]);
		int cap = 1000000;
		c.setRepairs(m.num(p + "repairs", 0, cap)); c.setCombatKills(m.num(p + "kills", 0, cap)); c.setPilotedEvasions(m.num(p + "evasions", 0, cap));
		c.setJumpsSurvived(m.num(p + "jumps", 0, cap)); c.setSkillMasteriesEarned(m.num(p + "masteries", 0, 12));
		List<Integer> tints = new ArrayList<Integer>();
		String ts = m.get(p + "tints");
		if (!ts.isEmpty()) {
			if (!ts.matches("\\d{1,3}(,\\d{1,3}){0,15}")) throw new Wire.Garbled("tints");
			for (String t : ts.split(",")) tints.add(Integer.parseInt(t));
		}
		c.setSpriteTintIndeces(fitTints(race, tints));
		return c;
	}
	/** Each tint within its layer's colours, and no more layers than the race has. */
	private static List<Integer> fitTints(CrewType race, List<Integer> tints) {
		List<Integer> out = new ArrayList<Integer>();
		try {
			net.blerf.ftl.xml.CrewBlueprint cb = net.blerf.ftl.parser.DataManager.get().getCrew(race.getId());
			if (cb == null || cb.getSpriteTintLayerList() == null) return out;
			List<net.blerf.ftl.xml.CrewBlueprint.SpriteTintLayer> layers = cb.getSpriteTintLayerList();
			for (int i = 0; i < layers.size() && i < tints.size(); i++) {
				int n = layers.get(i).tintList == null ? 0 : layers.get(i).tintList.size();
				out.add(n == 0 ? 0 : Math.min(tints.get(i), n - 1));
			}
		} catch (Exception e) {
			out.clear();
		}
		return out;
	}
	private static final net.blerf.ftl.constants.FTLConstants CONSTANTS = new net.blerf.ftl.constants.AdvancedFTLConstants();
	/** The most experience each skill holds (its second level): pilot, engines, shields, weapons, repair, combat. */
	private static int[] maxSkills(CrewType r) {
		return new int[] {2 * CONSTANTS.getMasteryIntervalPilot(r), 2 * CONSTANTS.getMasteryIntervalEngine(r), 2 * CONSTANTS.getMasteryIntervalShield(r),
				2 * CONSTANTS.getMasteryIntervalWeapon(r), 2 * CONSTANTS.getMasteryIntervalRepair(r), 2 * CONSTANTS.getMasteryIntervalCombat(r)};
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
