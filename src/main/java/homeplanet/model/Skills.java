package homeplanet.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.blerf.ftl.constants.AdvancedFTLConstants;
import net.blerf.ftl.constants.FTLConstants;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.CrewType;

/**
 * A crew member's skill points, as FTL:AE keeps them: each skill's points climb to the race's interval for level one
 * and twice it for level two, and the save's mastery flags say which level they hold. Expeditions add points for
 * work done, the infirmary takes them away, and a clone bay takes a level. Order: pilot, engines, shields, weapons,
 * repair, combat (Crew.skillLevels' order).
 */
public final class Skills {
	private Skills() { }
	private static final FTLConstants CONSTANTS = new AdvancedFTLConstants();

	public static int points(CrewState c, int skill) {
		switch (skill) {
			case 0: return c.getPilotSkill();
			case 1: return c.getEngineSkill();
			case 2: return c.getShieldSkill();
			case 3: return c.getWeaponSkill();
			case 4: return c.getRepairSkill();
			default: return c.getCombatSkill();
		}
	}
	/** The points for level one; twice it for level two. */
	public static int interval(CrewState c, int skill) {
		CrewType r = c.getRace();
		switch (skill) {
			case 0: return CONSTANTS.getMasteryIntervalPilot(r);
			case 1: return CONSTANTS.getMasteryIntervalEngine(r);
			case 2: return CONSTANTS.getMasteryIntervalShield(r);
			case 3: return CONSTANTS.getMasteryIntervalWeapon(r);
			case 4: return CONSTANTS.getMasteryIntervalRepair(r);
			default: return CONSTANTS.getMasteryIntervalCombat(r);
		}
	}
	/** Sets the points (kept between 0 and twice the interval) and the mastery flags they earn. */
	public static void set(CrewState c, int skill, int points) {
		int iv = interval(c, skill);
		int p = Math.max(0, Math.min(2 * iv, points));
		boolean one = p >= iv, two = p >= 2 * iv;
		switch (skill) {
			case 0: c.setPilotSkill(p); c.setPilotMasteryOne(one); c.setPilotMasteryTwo(two); break;
			case 1: c.setEngineSkill(p); c.setEngineMasteryOne(one); c.setEngineMasteryTwo(two); break;
			case 2: c.setShieldSkill(p); c.setShieldMasteryOne(one); c.setShieldMasteryTwo(two); break;
			case 3: c.setWeaponSkill(p); c.setWeaponMasteryOne(one); c.setWeaponMasteryTwo(two); break;
			case 4: c.setRepairSkill(p); c.setRepairMasteryOne(one); c.setRepairMasteryTwo(two); break;
			default: c.setCombatSkill(p); c.setCombatMasteryOne(one); c.setCombatMasteryTwo(two); break;
		}
	}
	public static void add(CrewState c, int skill, int points) { set(c, skill, points(c, skill) + points); }
	/** A point off one skill they have points in, at random (the infirmary's price for a beacon); none to lose, nothing. */
	public static void drain(CrewState c, Random rng) {
		List<Integer> have = new ArrayList<Integer>();
		for (int i = 0; i < 6; i++) if (points(c, i) > 0) have.add(i);
		if (have.isEmpty()) return;
		int s = have.get(rng.nextInt(have.size()));
		set(c, s, points(c, s) - 1);
	}
	/** Back from a clone bay: a level off each skill they held, their points at the start of what's left. */
	public static void cloned(CrewState c) {
		int[] levels = Crew.skillLevels(c);
		for (int i = 0; i < 6; i++) if (levels[i] > 0) set(c, i, (levels[i] - 1) * interval(c, i));
	}
}
