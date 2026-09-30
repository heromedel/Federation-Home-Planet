package homeplanet.model;

import net.blerf.ftl.constants.AdvancedFTLConstants;
import net.blerf.ftl.constants.FTLConstants;
import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.CrewType;
import net.blerf.ftl.xml.CrewBlueprint;

/** A crew member's race name, skills and report, as players see them. */
public final class Crew {
	private Crew() { }

	private static final FTLConstants CONSTANTS = new AdvancedFTLConstants();

	/** The race's display name from the game data (e.g. "Zoltan" for energy), or its id. */
	public static String raceTitle(CrewState cs) {
		CrewType race = cs.getRace();
		if (race == null) return "?";
		CrewBlueprint b = DataManager.get().getCrews().get(race.getId());
		if (b != null && b.getTitle() != null && b.getTitle().getTextValue() != null && b.getTitle().getTextValue().length() > 0) {
			return b.getTitle().getTextValue();
		}
		return race.getId();
	}
	// e.g. "level 1 (70/130)": levels come from the save's mastery flags; FTL:AE needs interval xp for level 1, twice that for level 2
	private static String skillText(int xp, int interval, boolean one, boolean two) {
		int level = two ? 2 : (one ? 1 : 0);
		String progress = (level >= 2) ? "max" : (xp + "/" + (interval * (level + 1)));
		return "level " + level + " (" + progress + ")";
	}
	private static String[][] skillRows(CrewState cs) {
		CrewType r = cs.getRace();
		return new String[][] {
			{"Pilot", skillText(cs.getPilotSkill(), CONSTANTS.getMasteryIntervalPilot(r), cs.getPilotMasteryOne(), cs.getPilotMasteryTwo())},
			{"Engines", skillText(cs.getEngineSkill(), CONSTANTS.getMasteryIntervalEngine(r), cs.getEngineMasteryOne(), cs.getEngineMasteryTwo())},
			{"Shields", skillText(cs.getShieldSkill(), CONSTANTS.getMasteryIntervalShield(r), cs.getShieldMasteryOne(), cs.getShieldMasteryTwo())},
			{"Weapons", skillText(cs.getWeaponSkill(), CONSTANTS.getMasteryIntervalWeapon(r), cs.getWeaponMasteryOne(), cs.getWeaponMasteryTwo())},
			{"Repair", skillText(cs.getRepairSkill(), CONSTANTS.getMasteryIntervalRepair(r), cs.getRepairMasteryOne(), cs.getRepairMasteryTwo())},
			{"Combat", skillText(cs.getCombatSkill(), CONSTANTS.getMasteryIntervalCombat(r), cs.getCombatMasteryOne(), cs.getCombatMasteryTwo())}};
	}
	/** Her skill levels (0, 1 or 2, from the save's mastery flags): pilot, engines, shields, weapons, repair, combat. */
	public static int[] skillLevels(CrewState cs) {
		return new int[] {lv(cs.getPilotMasteryOne(), cs.getPilotMasteryTwo()), lv(cs.getEngineMasteryOne(), cs.getEngineMasteryTwo()),
				lv(cs.getShieldMasteryOne(), cs.getShieldMasteryTwo()), lv(cs.getWeaponMasteryOne(), cs.getWeaponMasteryTwo()),
				lv(cs.getRepairMasteryOne(), cs.getRepairMasteryTwo()), lv(cs.getCombatMasteryOne(), cs.getCombatMasteryTwo())};
	}
	private static int lv(boolean one, boolean two) { return two ? 2 : one ? 1 : 0; }
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
