package homeplanet.model;

import net.blerf.ftl.parser.SavedGameParser.CrewState;

/**
 * Crew ranks (heromedel, 5.52): a prefix on a crew member's name and nothing more, offered for skills fully mastered
 * (both levels of one skill) as they stand now: one for Sgt., up to six for Cpt. A skill lost (the Clone Bay) never
 * takes a rank off a name; it only holds back the next one until it's won back.
 */
public final class Rank {
	private Rank() { }

	public static final String[] PREFIX = {"Sgt.", "Lt.", "Maj.", "Col.", "Cmd.", "Cpt."};
	public static final String[] TITLE = {"Sergeant", "Lieutenant", "Major", "Colonel", "Commander", "Captain"};
	/** heromedel's words for the Promote button. */
	public static final String TOOLTIP = "This does nothing but add a Rank Prefix onto their name. Sometimes FTL may cut off the rest of the name or not show the rank.";

	/** Skills fully mastered as they stand: both levels' points (not FTL's marks, which stay when a skill is lost). */
	public static int mastered(CrewState c) {
		int n = 0;
		for (int i = 0; i < 6; i++) if (Skills.points(c, i) >= 2 * Skills.interval(c, i)) n++;
		return n;
	}
	/** The rank worn at the start of this name (0 Sgt. to 5 Cpt.), or -1: with or without the dot, in any capitals. */
	public static int worn(String name) { return prefix(name)[0]; }
	/** The rank they've earned and don't wear yet (one already wearing it or higher: none), or -1. */
	public static int due(CrewState c) {
		int earned = Math.min(PREFIX.length, mastered(c)) - 1;
		return earned > worn(c.getName()) ? earned : -1;
	}
	/** Their name with this rank in front, in place of any rank it wore. */
	public static String promoted(String name, int rank) { return PREFIX[rank] + " " + bare(name); }
	/** Their name without a rank. */
	public static String bare(String name) {
		int[] p = prefix(name);
		return p[0] < 0 ? name : name.substring(p[1]).trim();
	}
	/** The rank's title ("Lieutenant") if going from one name to the other only put on a higher rank, else null. */
	public static String promotion(String was, String now) {
		int w = worn(now);
		if (w < 0 || w <= worn(was) || !bare(was).equals(bare(now))) return null;
		return TITLE[w];
	}
	/** {rank, length of its prefix}, or {-1, 0}. */
	private static int[] prefix(String name) {
		if (name == null) return new int[] {-1, 0};
		for (int i = 0; i < PREFIX.length; i++) {
			for (String p : new String[] {PREFIX[i], PREFIX[i].substring(0, PREFIX[i].length() - 1)}) {
				if (name.length() > p.length() + 1 && name.regionMatches(true, 0, p, 0, p.length()) && name.charAt(p.length()) == ' ') return new int[] {i, p.length()};
			}
		}
		return new int[] {-1, 0};
	}
}
