package homeplanet.model;

/** The small turns of phrase every writer needs, in one place (Overhaul 6.0, Phase 1 step 10). */
public final class Words {
	private Words() { }

	/** With its first letter capitalised ("the Kestrel" to "The Kestrel"). */
	public static String cap(String s) { return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
	/** With "a" or "an" before it, by its first letter ("an Engi", "a Rockman"). */
	/** "a rebel ship", "an automated ship" as one now known: "the rebel ship" (6.09); anything else with "the " before it. */
	public static String the(String s) {
		if (s == null || s.isEmpty()) return "";
		if (s.startsWith("a ")) return "the " + s.substring(2);
		if (s.startsWith("an ")) return "the " + s.substring(3);
		return s.startsWith("the ") ? s : "the " + s;
	}
	/** A count as the style guide says it (docs/STYLE.md): up to ten in words, then digits ("two ships", "1,024 scrap"). */
	public static String number(int n) { return n >= 0 && n < NUMBERS.length ? NUMBERS[n] : String.format("%,d", n); }
	private static final String[] NUMBERS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
	public static String a(String s) {
		if (s == null || s.isEmpty()) return "";
		String first = s.split(" ")[0];
		boolean initials = first.length() > 1 && first.matches("[A-Z]+"); // "an FTL Jammer": said letter by letter
		return ((initials ? "AEFHILMNORSX" : "AEIOUaeiou").indexOf(s.charAt(0)) >= 0 ? "an " : "a ") + s;
	}

	// ---- A ship's pronoun (6.42, heromedel: Ship Pronoun in Settings, General). "she" was never a rule: a ship can be
	// her, him or it, and every word the station says about a ship asks here. Crew keep their own (he or she by their sex).

	/** The cfg line: her (the default), him or it. */
	public static final String CFG_SHIP = "ship_pronoun";
	/** The choices, as the setting names them. */
	public static final String[] SHIP_PRONOUNS = {"her", "him", "it"};
	/** The setting: her, him or it. */
	public static String shipPronoun() {
		String p = homeplanet.core.HomePlanet.config.getProperty(CFG_SHIP, SHIP_PRONOUNS[0]);
		return java.util.Arrays.asList(SHIP_PRONOUNS).contains(p) ? p : SHIP_PRONOUNS[0];
	}
	private static String ship(String her, String him, String it) {
		String p = shipPronoun();
		return p.equals("him") ? him : p.equals("it") ? it : her;
	}
	/** A ship as the subject: she, he or it ("she was boarded"). Words.cap to begin a sentence. */
	public static String she() { return ship("she", "he", "it"); }
	/** A ship's, before a noun: her, his or its ("her crew"). */
	public static String her() { return ship("her", "his", "its"); }
	/** A ship as the object: her, him or it ("FTL flies her now"). */
	public static String herObj() { return ship("her", "him", "it"); }
	/** A ship's, standing alone: hers, his or its ("the choice is hers"). */
	public static String hers() { return ship("hers", "his", "its"); }
	/** A ship herself: herself, himself or itself. */
	public static String herself() { return ship("herself", "himself", "itself"); }
	/**
	 * The words with a ship's pronoun tokens filled, for the words files in lore/: {she}, {her} (her crew), {her_obj}
	 * (flies her), {hers}, {herself}, and {She} and {Her} to begin a sentence. Crew have their own ({he}, {him}, {his}).
	 */
	public static String ship(String words) {
		if (words == null || words.indexOf('{') < 0) return words;
		return words.replace("{she}", she()).replace("{She}", cap(she())).replace("{her_obj}", herObj()).replace("{hers}", hers())
				.replace("{herself}", herself()).replace("{her}", her()).replace("{Her}", cap(her()));
	}
	/** The ship pronoun tokens, for the checks that a words file names only tokens it has. */
	public static final String[] SHIP_TOKENS = {"she", "She", "her", "Her", "her_obj", "hers", "herself"};
}
