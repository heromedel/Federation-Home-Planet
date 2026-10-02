package homeplanet.comm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Messages between commanders without a channel: a short link opened for one message (a NOTE, in place of the hello)
 * and closed once the other station says where it went. The station receiving it decides: its inbox, or a pop-up for
 * a priority message (one a minute from each commander; the rest go to the inbox). A station says in its search
 * answer that it takes messages ("notes"), so older ones are never sent one.
 */
public final class Notes {
	private Notes() { }

	/** The most a message can say. */
	public static final int MAX = 500;
	/** Where a message went, as the receiving station answers. */
	public static final String INBOX = "inbox", POPUP = "popup";

	/** A message as it arrived: who sent it, what it says, and where to reply (port 0: their frequencies are closed). */
	public static final class Note {
		public String station, title, text;
		public boolean priority;
		public int replyPort;
		/** When it was written, if it waited in the sender's Outbox (0: just now). */
		public long written;
	}
	/** The other station answered, and turned the message away (blocked, or too many): said in its own words. */
	public static final class Refused extends IOException {
		public Refused(String why) { super(why); }
	}

	/** Plain text: letters and line breaks, no more than two breaks in a row, cut to length. */
	public static String clean(String s) {
		if (s == null) return "";
		StringBuilder b = new StringBuilder();
		int breaks = 0;
		for (int i = 0; i < s.length() && b.length() < MAX; i++) {
			char c = s.charAt(i);
			if (c == '\r') continue;
			if (c == '\n') { if (++breaks <= 2) b.append('\n'); continue; }
			if (c < ' ' || c == 0x7f) continue;
			breaks = 0;
			b.append(c);
		}
		return b.toString().trim();
	}

	/** The message as it goes: the sender's own particulars (as a hello carries them) and the text. */
	public static Wire.Msg note(String version, String station, String title, String text, boolean priority, int replyPort) {
		return new Wire.Msg("NOTE").put("protocol", Session.PROTOCOL).put("version", version).put("station", station).put("title", title)
				.put("text", clean(text)).put("priority", priority).put("replyPort", replyPort);
	}
	/** The message from its NOTE; Garbled if it isn't one. */
	public static Note read(Wire.Msg m) throws Wire.Garbled {
		if (!m.type.equals("NOTE")) throw new Wire.Garbled("expected a message, got " + m.type);
		Note n = new Note();
		n.station = m.get("station");
		if (!n.station.matches("[0-9a-f]{16}")) throw new Wire.Garbled("station id");
		n.title = Line.text(m.get("title"), 48);
		if (n.title.isEmpty()) n.title = "An unnamed commander";
		n.text = clean(m.get("text"));
		if (n.text.isEmpty()) throw new Wire.Garbled("an empty message");
		n.priority = m.flag("priority");
		n.replyPort = m.has("replyPort") ? m.num("replyPort", 0, 65535) : 0;
		try { n.written = m.has("written") ? m.longNum("written") : 0; } catch (Wire.Garbled e) { n.written = 0; }
		if (n.replyPort != 0 && (n.replyPort < Channel.PORT0 || n.replyPort >= Channel.PORT0 + Channel.PORTS)) n.replyPort = 0;
		return n;
	}

	// ---- receiving ----

	/** When each commander's recent messages arrived, and their last pop-up (shared by the whole station). */
	private static final Map<String, List<Long>> arrived = new HashMap<String, List<Long>>();
	private static final Map<String, Long> lastPopup = new HashMap<String, Long>();
	/** At most this many messages a minute from one commander, and from everyone together. */
	static final int PER_MINUTE = 5, ALL_PER_MINUTE = 20;
	static final long POPUP_GAP = 60000;
	/** Said by a station taking too many messages: a sender's Outbox tries again later rather than giving up. */
	public static final String TOO_MANY = "is receiving too many messages";

	/**
	 * Why this message is turned away, or null to take it: a blocked commander hears only that nobody answered; one
	 * sending too many is told to wait. myTitle: this station's commander, as the answer names them.
	 */
	public static synchronized String refuse(Note n, String host, String myTitle) {
		if (Blocks.blocked(n.station, host)) return Session.notAnswered(myTitle);
		long now = System.currentTimeMillis();
		int all = 0;
		for (List<Long> l : arrived.values()) { while (!l.isEmpty() && now - l.get(0) > 60000) l.remove(0); all += l.size(); }
		List<Long> mine = arrived.get(n.station);
		if (mine == null) arrived.put(n.station, mine = new ArrayList<Long>());
		if (mine.size() >= PER_MINUTE || all >= ALL_PER_MINUTE) return myTitle + "'s station " + TOO_MANY + ". Try again in a minute.";
		mine.add(now);
		return null;
	}
	/**
	 * Where a taken message goes. A priority one pops up, if this station lets them (popups) and that commander's
	 * last pop-up was a minute ago or more; otherwise the inbox (always there for a commander's mail).
	 */
	public static synchronized String where(Note n, boolean popups) {
		long now = System.currentTimeMillis();
		Long last = lastPopup.get(n.station);
		boolean pop = n.priority && popups && (last == null || now - last >= POPUP_GAP);
		if (pop) lastPopup.put(n.station, now);
		return pop ? POPUP : INBOX;
	}
	/** Files a message in the inbox: from its commander, with where to reply kept in its key. */
	public static void toInbox(Note n, String host) {
		String key = "note:" + n.station + "|" + host + "|" + n.replyPort + "|" + System.currentTimeMillis();
		homeplanet.parser.Transmissions.deliver(key, n.title, n.priority ? "Long Range message (priority)" : "Long Range message", n.text + "\n~ " + n.title + waitedNote(n));
	}
	/** For a message that waited in its sender's Outbox: when it was written. */
	public static String waitedNote(Note n) {
		if (n.written <= 0 || System.currentTimeMillis() - n.written < 120000) return "";
		return "\n\n(Written " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(n.written))
				+ ", while your station was out of range: it waited in their Outbox.)";
	}
	/** Answers the sender: where the message went (or why it didn't), and closes the link. */
	public static void answer(Channel ch, String where, String refused) {
		if (refused != null) { ch.close(refused); return; }
		ch.trySend(new Wire.Msg("NOTED").put("where", where));
		ch.close("");
	}

	// ---- sending ----

	/**
	 * Sends a message (off the event thread: it connects and waits for the answer). Returns where it went (INBOX or
	 * POPUP); throws, saying why, if it didn't arrive.
	 */
	public static String send(String host, int port, Wire.Msg note, String whom) throws IOException {
		Channel ch;
		try {
			ch = Channel.connect(host, port);
		} catch (IOException e) {
			throw new IOException(whom + "'s station did not answer: their hailing frequencies may be closed.");
		}
		try {
			ch.send(note);
			Wire.Msg r = ch.readFirst(15000);
			if (r.type.equals("BYE")) {
				String why = r.get("why").trim();
				throw new Refused(why.isEmpty() ? whom + "'s station did not take the message." : why);
			}
			if (!r.type.equals("NOTED")) throw new IOException(whom + "'s station gave an answer this one doesn't understand.");
			return POPUP.equals(r.get("where")) ? POPUP : INBOX;
		} catch (java.net.SocketTimeoutException e) {
			throw new IOException(whom + "'s station did not answer in time.");
		} finally {
			ch.close("");
		}
	}
}
