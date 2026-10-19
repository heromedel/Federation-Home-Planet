package homeplanet.ui;

import java.util.regex.Pattern;

/**
 * One way of searching words, for every search box: the manuals' Find and the logs' Search (heromedel, 6.76). Any case;
 * parts of words; the words in their order, with any spaces or line breaks between them (the text manual is wrapped);
 * spaces at the ends ignored. A log that gains a search box filters its entries with {@link #matches}.
 */
public final class TextSearch {
	private TextSearch() { }

	/** What was typed, as a pattern to find; null when it's empty or only spaces. */
	public static Pattern of(String typed) {
		String[] words = typed == null ? new String[0] : typed.trim().split("[\\s\\u00a0]+");
		if (words.length == 0 || words[0].isEmpty()) return null;
		StringBuilder re = new StringBuilder();
		for (String w : words) re.append(re.length() == 0 ? "" : "[\\s\\u00a0]+").append(Pattern.quote(w));
		return Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	/**
	 * A log entry's words as a log shows them: its line and its detail lines (what a log's Search looks through). The
	 * station log tells its entries in the lore's words ({@code told}); a voyage log shows the line as written.
	 */
	public static String words(homeplanet.core.EventLog.Entry e, boolean told) {
		String line = told ? homeplanet.core.Lore.told(e) : e.human;
		StringBuilder w = new StringBuilder(line == null ? "" : line);
		for (int i = 1; e.get("detail." + i) != null; i++) w.append('\n').append(told ? e.get("detail." + i).replace("Spacedock Storage", "the Cargo Hold") : e.get("detail." + i)); // as RecordsLog.station shows it
		return w.toString();
	}
	/** The entries that hold what was typed (all of them when nothing was typed), in their order. */
	public static java.util.List<homeplanet.core.EventLog.Entry> filter(java.util.List<homeplanet.core.EventLog.Entry> es, String typed, boolean told) {
		if (of(typed) == null) return es;
		java.util.List<homeplanet.core.EventLog.Entry> out = new java.util.ArrayList<homeplanet.core.EventLog.Entry>();
		for (homeplanet.core.EventLog.Entry e : es) if (matches(words(e, told), typed)) out.add(e);
		return out;
	}
	/** Whether the text holds what was typed; true when nothing was typed (a filter that keeps everything). */
	public static boolean matches(String text, String typed) {
		Pattern p = of(typed);
		return p == null || text != null && p.matcher(text).find();
	}
}
