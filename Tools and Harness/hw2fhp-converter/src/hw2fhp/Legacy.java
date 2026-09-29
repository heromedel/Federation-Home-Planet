package hw2fhp;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import homeplanet.core.SafeFiles;

/**
 * FTL Homeworld 3.x / 4.x named their blueprints PLAYER_SHIP_X_HW and their pictures hw_design_1; Federation Home
 * Planet names them _HP and hp_. The replacements are the same length, so a save file can be converted in place
 * without touching its structure (FTL's strings are length-prefixed).
 */
public final class Legacy {
	private Legacy() { }

	public static final String OLD_CFG = "ftl-homeworld.cfg";
	public static final String[] OLD_MOD_AUTHORS = {"FTL Homeworld"};
	public static final String[] OLD_MOD_TITLES = {"Homeworld Companion Mod", "Homeworld Any System Removal"};

	/** The same text with every old blueprint id, layout id and picture name renamed. Same length as the input. */
	public static String convert(String text) {
		String s = text;
		// Blueprint ids: PLAYER_SHIP_KESTRAL_HW, PLAYER_SHIP_KESTRAL_R2_HW, PLAYER_SHIP_DESIGN_1_V2_HW. In a save the id is
		// followed by binary bytes (the next string's length), so nothing may depend on what comes after it.
		s = s.replaceAll("PLAYER_SHIP_([A-Z0-9_]*?)_HW", "PLAYER_SHIP_$1_HP");
		// layout ids in text files: kestral_r2_hw, design_1_hw (never inside a save)
		s = s.replaceAll("(?<=[a-z0-9])_hw(?![A-Za-z0-9])", "_hp");
		// picture names: hw_design_1, hw_remodel_player_ship_kestral, hw_player_ship_circle_r2_hw (an overhauled remodel's)
		s = s.replaceAll("hw_(?=(design|remodel|player_ship)_)", "hp_");
		return s;
	}

	/** Converts a save (or any file) in place, byte for byte. Returns true if anything changed. */
	public static boolean convertFile(File f) throws IOException {
		byte[] bytes = SafeFiles.read(f);
		String raw = new String(bytes, StandardCharsets.ISO_8859_1);
		String out = convert(raw);
		if (out.equals(raw)) return false;
		if (out.length() != raw.length()) throw new IOException("Conversion changed the length of " + f.getName());
		SafeFiles.write(f, out.getBytes(StandardCharsets.ISO_8859_1));
		return true;
	}
	/** Converts a UTF-8 text file (designs, remodels, logs) in place, plus the old art folder name in picture paths. */
	public static boolean convertText(File f) throws IOException {
		String raw = new String(SafeFiles.read(f), StandardCharsets.UTF_8);
		String out = convert(raw).replace("file:homeworld-art/", "file:art/");
		if (out.equals(raw)) return false;
		SafeFiles.writeText(f, out, false);
		return true;
	}
}
