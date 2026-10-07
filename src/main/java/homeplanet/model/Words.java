package homeplanet.model;

/** The small turns of phrase every writer needs, in one place (Overhaul 6.0, Phase 1 step 10). */
public final class Words {
	private Words() { }

	/** With its first letter capitalised ("the Kestrel" to "The Kestrel"). */
	public static String cap(String s) { return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
	/** With "a" or "an" before it, by its first letter ("an Engi", "a Rockman"). */
	public static String a(String s) { return s == null || s.isEmpty() ? "" : ("AEIOUaeiou".indexOf(s.charAt(0)) >= 0 ? "an " : "a ") + s; }
}
