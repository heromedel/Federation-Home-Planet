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
	public static String a(String s) { return s == null || s.isEmpty() ? "" : ("AEIOUaeiou".indexOf(s.charAt(0)) >= 0 ? "an " : "a ") + s; }
}
