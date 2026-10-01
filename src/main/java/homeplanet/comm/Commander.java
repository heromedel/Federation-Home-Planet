package homeplanet.comm;

import java.awt.Component;

import javax.swing.JOptionPane;

import homeplanet.core.HomePlanet;

/**
 * The player as other commanders know them over Long Range Comm.: a name of their choosing, with the rank in front
 * ("Captain Vex"), and an id for this station so two stations can recognise each other again (an unfinished trade).
 * Every Home Planet Station is The Home Planet Station: stations have no names of their own, commanders do.
 */
public final class Commander {
	private Commander() { }

	public static final String CFG_NAME = "commander_name", CFG_STATION = "station_id";
	public static final int MAX = 24;

	/** Why this name won't do, or null if it will (after {@link #clean}). It travels to other computers, so it's kept plain. */
	public static String check(String name) {
		if (name == null || name.isEmpty()) return "Enter a name.";
		if (name.length() > MAX) return "A name can be " + MAX + " characters at most.";
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (!Character.isLetterOrDigit(c) && " '-.".indexOf(c) < 0) return "A name can use letters, numbers, spaces and ' - . only.";
		}
		if (!Character.isLetterOrDigit(name.charAt(0))) return "A name starts with a letter or a number.";
		return null;
	}
	/** Trimmed, with runs of spaces made one. */
	public static String clean(String raw) {
		return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
	}

	/** The commander's name, or null if none was given yet. */
	public static String name() {
		String n = clean(HomePlanet.config.getProperty(CFG_NAME));
		return check(n) == null ? n : null;
	}
	/** Sets the name (already checked) and writes the cfg. */
	public static void setName(String name) {
		HomePlanet.config.setProperty(CFG_NAME, clean(name));
		HomePlanet.saveConfig();
	}
	/** The title others see: the rank (Immersive Mode's, or Commander) and the name. "Commander" alone with no name. */
	public static String title() {
		String n = name();
		String rank = homeplanet.parser.Transmissions.rank();
		return n == null ? rank : rank + " " + n;
	}

	/** This station's id: made once, kept in the cfg. */
	public static synchronized String stationId() {
		String id = HomePlanet.config.getProperty(CFG_STATION, "").trim();
		if (!id.matches("[0-9a-f]{16}")) {
			id = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
			HomePlanet.config.setProperty(CFG_STATION, id);
			HomePlanet.saveConfig();
		}
		return id;
	}

	/** Asks for a name when there is none yet. True once there is one (false if the player cancelled). */
	public static boolean ensure(Component owner) {
		if (name() != null) return true;
		String msg = "The Home Planet Station needs a name for your long-range transmissions.\nOther commanders will know you as "
				+ homeplanet.parser.Transmissions.rank() + " ...";
		String value = "";
		while (true) {
			Object r = JOptionPane.showInputDialog(owner, msg, "Commander", JOptionPane.QUESTION_MESSAGE, null, null, value);
			if (r == null) return false;
			value = clean(r.toString());
			String why = check(value);
			if (why == null) break;
			JOptionPane.showMessageDialog(owner, why, "Commander", JOptionPane.INFORMATION_MESSAGE);
		}
		setName(value);
		homeplanet.core.HistoryLog.entry("SETTINGS", "", java.util.Collections.singletonList("Commander name: " + value));
		return true;
	}
}
