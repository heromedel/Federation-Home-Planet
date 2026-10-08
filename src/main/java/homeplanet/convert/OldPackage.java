package homeplanet.convert;

import java.util.regex.Matcher;

import homeplanet.core.Event;
import homeplanet.core.EventLog;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;
import homeplanet.vault.VoyageLog;

/**
 * A ship received in a trade from a station older than 5.75, whose package carries her voyage log as prose rather than
 * events (5.75). Unlike the rest of this package it isn't about this station's own fleets: it is wanted while such
 * stations can still trade ({@code Session.PROTOCOL}), and goes when the protocol stops them.
 */
public final class OldPackage {
	private OldPackage() { }

	/**
	 * Her voyage log: each line an event under her id here, its time its own, Prior in this career (5.81): it was
	 * another commander's, and on the day she arrived it filled this Captain's Log.
	 */
	public static void voyage(Vault v, Ship s, String from, String text) {
		Event who = VoyageLog.shipFields(s).put("received_from", from).put("converted", true);
		for (String line : text.split("\r?\n")) {
			Matcher m = LogConvert.STAMP.matcher(line);
			if (!m.matches()) continue;
			Event e = LogConvert.voyageEvent(m.group(2).trim());
			EventLog.write(v, Event.of(e.kind).put("log", "voyage").put("time", m.group(1) + ":00").put("day", 0).putAll(who).putAll(e).human(m.group(2).trim()));
		}
	}
}
