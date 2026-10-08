package homeplanet.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One thing that happened, for the event log (CLAUDE.md, "Log lines"; Overhaul 6.0, Phase 1 step 9): its kind, every
 * field the program could ever need as key=value, and the human line written from them. The kinds and their fields
 * are listed in docs/EVENTS.md. Built with {@code Event.of("CREW_MOVE").put("crew", ...).human("Bob was ...")} and
 * written with {@link EventLog#write}.
 */
public final class Event {
	public final String kind;
	private final List<String[]> fields = new ArrayList<String[]>();
	private String human = "";
	private int details;

	private Event(String kind) { this.kind = kind; }
	/** A new event of this kind (upper case, words joined by underscores: "RENAME CREW" becomes RENAME_CREW). */
	public static Event of(String kind) { return new Event(normalize(kind)); }
	/** A kind as the log writes it: upper case, letters, digits and underscores only. */
	public static String normalize(String kind) {
		return kind == null || kind.trim().isEmpty() ? "EVENT" : kind.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
	}

	/** A field; a null value is left out, so optional data can be put without a check. Keys repeat for lists. */
	public Event put(String key, Object value) {
		if (value != null) fields.add(new String[] {key, String.valueOf(value)});
		return this;
	}
	public Event put(String key, int value) { return put(key, Integer.toString(value)); }
	public Event put(String key, long value) { return put(key, Long.toString(value)); }
	public Event put(String key, boolean value) { return put(key, Boolean.toString(value)); }
	/** The fields of another event, after this one's (a ship's or a crew member's description, say). */
	public Event putAll(Event other) {
		if (other != null) fields.addAll(other.fields);
		return this;
	}
	/** A detail line of the old logs' kind, numbered: detail.1, detail.2, ... */
	public Event detail(String line) { return put("detail." + (++details), line); }
	public Event details(List<String> lines) {
		if (lines != null) for (String l : lines) detail(l);
		return this;
	}
	/** The human line, written from the fields: one line, lore-friendly, under the hard rules (never a beacon count). */
	public Event human(String line) { this.human = line == null ? "" : line; return this; }
	public String human() { return human; }

	/** The first value of a key, or null. */
	public String get(String key) {
		for (String[] f : fields) if (f[0].equals(key)) return f[1];
		return null;
	}
	/** Every value of a key, in order. */
	public List<String> all(String key) {
		List<String> out = new ArrayList<String>();
		for (String[] f : fields) if (f[0].equals(key)) out.add(f[1]);
		return out;
	}
	/** The fields, in order, as {key, value}. */
	public List<String[]> fields() { return new ArrayList<String[]>(fields); }

	/** The fields as the machine line carries them: key=value, values quoted when they need it. */
	String fieldText() {
		StringBuilder sb = new StringBuilder();
		for (String[] f : fields) {
			if (sb.length() > 0) sb.append(' ');
			sb.append(f[0]).append('=').append(quote(f[1]));
		}
		return sb.toString();
	}
	/**
	 * A value as the machine line writes it: plain when it has no space, quote, backslash, '|', '=' or line break, else
	 * in double quotes with those escaped (\" \\ \n), so the line splits on spaces and '|' without a thought.
	 */
	public static String quote(String v) {
		if (v == null) return "\"\"";
		boolean plain = !v.isEmpty();
		for (int i = 0; plain && i < v.length(); i++) {
			char c = v.charAt(i);
			if (c <= ' ' || c == '"' || c == '\\' || c == '|' || c == '=') plain = false;
		}
		if (plain) return v;
		StringBuilder sb = new StringBuilder("\"");
		for (int i = 0; i < v.length(); i++) {
			char c = v.charAt(i);
			if (c == '"' || c == '\\') sb.append('\\').append(c);
			else if (c == '\n') sb.append("\\n");
			else if (c == '\r') sb.append("\\r");
			else if (c == '\t') sb.append("\\t");
			else sb.append(c);
		}
		return sb.append('"').toString();
	}
}
