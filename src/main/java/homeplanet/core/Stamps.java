package homeplanet.core;

import java.text.SimpleDateFormat;
import java.util.Date;

/** The date and time as the station's small files and letters write it: "2026-10-07 14:02" (7.01: one format, one place). */
public final class Stamps {
	private Stamps() { }

	public static final String MINUTE = "yyyy-MM-dd HH:mm";

	/** Now, to the minute. */
	public static String now() { return at(System.currentTimeMillis()); }
	/** A moment (milliseconds since the epoch, as a file's last-modified time), to the minute. */
	public static String at(long millis) { return new SimpleDateFormat(MINUTE).format(new Date(millis)); }
}
