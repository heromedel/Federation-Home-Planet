package homeplanet.model;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.CrewType;
import net.blerf.ftl.xml.CrewBlueprint;

/** A crew member's race name, skills and report, as players see them. */
public final class Crew {
	private Crew() { }

	/** The race's display name from the game data (e.g. "Zoltan" for energy), or its id. */
	public static String raceTitle(CrewState cs) {
		CrewType race = cs.getRace();
		return race == null ? "?" : raceTitle(race.getId());
	}
	/**
	 * A crew member's title for a race id, as FTL shows it: "Rockman", "Zoltan", "Lanius", "Human" (the game data's title;
	 * without the data, the id capitalised with the two FTL names by name). The one home for race names, with
	 * {@link #racePeople} (Overhaul 6.0, step 10).
	 */
	public static String raceTitle(String id) {
		if (id == null || id.isEmpty()) return "";
		try {
			CrewBlueprint b = DataManager.get().getCrews().get(id);
			if (b != null && b.getTitle() != null && b.getTitle().getTextValue() != null && b.getTitle().getTextValue().length() > 0) return b.getTitle().getTextValue();
		} catch (RuntimeException e) { /* no game data loaded: the names below */ }
		String p = racePeople(id);
		return p.equals("Rock") ? "Rockman" : p;
	}
	/**
	 * The people's name for a race id, as the lore speaks of them: "the Rock", "Rock pirates", "a Rock crew volunteer";
	 * "Zoltan", "Lanius", "Engi", "Mantis", "Slug", "Crystal", "Human". The voyage log writes this one (a Rock is "Rock"
	 * there, "Rockman" in FTL's title: the crew register reads both).
	 */
	public static String racePeople(String id) {
		if (id == null || id.isEmpty()) return "";
		String r = id.toLowerCase();
		if (r.equals("energy")) return "Zoltan";
		if (r.equals("anaerobic")) return "Lanius";
		if (r.equals("rockman")) return "Rock";
		return Character.toUpperCase(r.charAt(0)) + r.substring(1);
	}
	/**
	 * The people a ship belongs to, from a blueprint id or a ship-list id (PLAYER_SHIP_JELLY, SHIPS_ROCK_PIRATE,
	 * CIRCLE...): "Engi" (the Circle and the Stealth cruiser), "Zoltan", "Mantis", "Slug", "Rock", "Crystal", "Lanius";
	 * null when it names none (a Federation, rebel, automated or civilian ship).
	 */
	public static String peopleOf(String shipId) {
		String l = shipId == null ? "" : shipId.toUpperCase();
		if (l.contains("CIRCLE") || l.contains("ENGI") || l.contains("STEALTH")) return "Engi";
		if (l.contains("ENERGY") || l.contains("ZOLTAN")) return "Zoltan";
		if (l.contains("MANTIS")) return "Mantis";
		if (l.contains("JELLY") || l.contains("SLUG")) return "Slug";
		if (l.contains("ROCK")) return "Rock";
		if (l.contains("CRYSTAL")) return "Crystal";
		if (l.contains("ANAEROBIC") || l.contains("LANIUS")) return "Lanius";
		return null;
	}
	// e.g. "level 1 (70/130)": the level from the points as they stand (skillLevels); FTL:AE needs interval xp for level 1, twice that for level 2
	private static String skillText(int xp, int interval) {
		int level = level(xp, interval);
		String progress = (level >= 2) ? "max" : (xp + "/" + (interval * (level + 1)));
		return "level " + level + " (" + progress + ")";
	}
	private static String[][] skillRows(CrewState cs) {
		String[] names = {"Pilot", "Engines", "Shields", "Weapons", "Repair", "Combat"};
		String[][] out = new String[6][];
		for (int i = 0; i < 6; i++) out[i] = new String[] {names[i], skillText(Skills.points(cs, i), Skills.interval(cs, i))};
		return out;
	}
	/**
	 * Each skill's level as it stands, from its points: the race's interval for level one, twice it for level two (pilot,
	 * engines, shields, weapons, repair, combat). Not FTL's mastery marks (5.62): FTL sets those only for a level earned in
	 * play, so a crew member an event gives fully skilled (the Zoltan peace quest's Envoy) has none, and a skill a Clone
	 * Bay lowered keeps its marks.
	 */
	public static int[] skillLevels(CrewState cs) {
		int[] out = new int[6];
		for (int i = 0; i < 6; i++) out[i] = level(Skills.points(cs, i), Skills.interval(cs, i));
		return out;
	}
	private static int level(int points, int interval) { return points >= 2 * interval ? 2 : points >= interval ? 1 : 0; }
	/** Hover text for a crew member: race and skill levels. */
	public static String tooltip(CrewState cs) {
		if (cs == null || cs.getRace() == null) return null;
		StringBuilder sb = new StringBuilder("<html><b>" + cs.getName() + "</b> (" + raceTitle(cs) + ")");
		for (String[] row : skillRows(cs)) sb.append("<br>").append(row[0]).append(": ").append(row[1]);
		return sb.append("</html>").toString();
	}
	/** The crew member's report: race, sex, skills and service record. */
	public static String summary(CrewState cs) {
		StringBuilder result = new StringBuilder();
		result.append(String.format("Race:              %s\n", raceTitle(cs)));
		result.append(String.format("Sex:               %s\n", (cs.isMale() ? "Male" : "Female")));
		result.append("\n");
		for (String[] row : skillRows(cs)) result.append(String.format("%-9s %s\n", row[0] + ":", row[1]));
		result.append(String.format("\nRepairs:           %3d\n", cs.getRepairs()));
		result.append(String.format("Combat Kills:      %3d\n", cs.getCombatKills()));
		result.append(String.format("Piloted Evasions:  %3d\n", cs.getPilotedEvasions()));
		result.append(String.format("Jumps Survived:    %3d\n", cs.getJumpsSurvived()));
		result.append(String.format("Skill Masteries:   %3d\n", cs.getSkillMasteriesEarned()));
		return result.toString();
	}
}
