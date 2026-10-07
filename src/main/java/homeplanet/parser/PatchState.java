package homeplanet.parser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.DefaultDataManager;
import net.vhati.ftldat.PkgPack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Is a blueprint of the companion mod, as the station would build it now, what FTL's data holds? The one answer every
 * screen asks (Launch FTL, Commission, the Space Dock's tags, Derelicts, Salvage, the Long Range), read from ftl.dat on
 * disk, fresh after a patch, blueprint by blueprint: her blueprint text, and for a ship with her own layout the layout
 * and chassis files and her pictures, compared with the game's copies. Before, a session flag ("patched this session")
 * and a rooms-and-doors compare disagreed with each other and with the mod's real content (art, mounts and a loadout
 * changed in place read as patched).
 *
 * With the "homeplanet.dataOverlay" test property (a folder laid out like a mod), its files count as the game's, as
 * DefaultDataManager's overlay does for the blueprint files.
 */
public final class PatchState {
	private static final Logger log = LoggerFactory.getLogger(PatchState.class);
	private PatchState() { }

	/** The dat's blueprint files and other entries as last read, with the dat's length and date, so a patch is noticed. */
	private static File datFile;
	private static long datLength = -1, datModified = -1;
	private static final Map<String, byte[]> entries = new HashMap<String, byte[]>();
	/** The station's blueprint text per file, as of {@link #stamp}: rebuilt when the remodels or designs change. */
	private static final Map<String, String> ours = new HashMap<String, String>();
	private static int oursStamp = -1;
	private static int stamp = 0;
	/** The remodels or designs changed (CompanionMod.register): the station's side is read again. */
	public static synchronized void changed() { stamp++; }
	/** Forgets what was read of the dat (after a patch); it's read again at the next question. */
	public static synchronized void refresh() { datLength = -1; datModified = -1; entries.clear(); }

	/** True if the game data (ftl.dat as patched) has this blueprint as the station would write it now. */
	public static synchronized boolean inGame(String id, List<CompanionMod.Remodel> remodels) {
		DataManager dm = DataManager.get();
		if (!(dm instanceof DefaultDataManager)) return false;
		String file = null, mine = null;
		for (String f : CompanionMod.FILES) {
			String block = block(ourText(f, remodels), id);
			if (block != null) { file = f; mine = block; break; }
		}
		if (mine == null) return ((DefaultDataManager) dm).shipInGameData(id); // not the station's blueprint: the game's own, or nobody's
		String game = block(gameText("data/" + file), id);
		if (game == null || !sameXml(game, mine)) return false;
		// her own layout, chassis and pictures, where she has them
		CompanionMod.Remodel r = CompanionMod.find(remodels, id);
		if (r != null && CompanionMod.ownLayout(r)) {
			String lid = CompanionMod.layoutIdOf(r);
			if (!sameText("data/" + lid + ".txt", CompanionMod.layoutText(r)) || !sameText("data/" + lid + ".xml", CompanionMod.chassisText(r))) return false;
			if (r.geometry != null && !samePictures(r.geometry)) return false;
		}
		for (ShipDesign d : DesignExport.built()) {
			if (!DesignExport.bpId(d).equals(id)) continue;
			String lid = DesignExport.layoutId(d);
			if (!sameText("data/" + lid + ".txt", DesignExport.layoutText(d)) || !sameText("data/" + lid + ".xml", DesignExport.chassisText(d))) return false;
			if (!samePictures(d)) return false;
		}
		return true;
	}
	private static boolean sameText(String path, String mine) {
		if (mine == null) return false;
		String game = gameText(path);
		if (game == null) return false;
		return path.endsWith(".xml") ? sameXml(game, mine) : norm(game).equals(norm(mine));
	}
	private static boolean samePictures(ShipDesign d) {
		try {
			for (Map.Entry<String, byte[]> e : DesignExport.images(d).entrySet()) {
				byte[] game = gameBytes(e.getKey());
				if (game == null || !Arrays.equals(game, e.getValue())) return false;
			}
		} catch (IOException e) {
			log.debug("Her pictures could not be built to compare: {}", e.toString());
			return false;
		}
		return true;
	}

	/** The station's text of one blueprint file, as of the last change. */
	private static String ourText(String file, List<CompanionMod.Remodel> remodels) {
		if (oursStamp != stamp) { ours.clear(); oursStamp = stamp; }
		String t = ours.get(file);
		if (t == null) { t = CompanionMod.fullText(file, remodels); ours.put(file, t); }
		return t;
	}
	/** One ship's blueprint element out of a blueprint file's text, or null: the last of that name, as FTL takes the last it reads (a patch appends). */
	static String block(String xml, String id) {
		if (xml == null) return null;
		Matcher m = Pattern.compile("<shipBlueprint\\s+name=\"" + Pattern.quote(id) + "\"[\\s\\S]*?</shipBlueprint>").matcher(xml);
		String last = null;
		while (m.find()) last = m.group();
		return last;
	}
	/** Comments and whitespace aside. */
	static String norm(String s) {
		return s.replaceAll("<!--[\\s\\S]*?-->", "").replaceAll("\\s+", " ").trim();
	}
	/**
	 * The same XML however it's written (5.79): Slipstream reads each file it patches and writes it back in its own style
	 * ("<x />" for "<x/>", amount="10" for amount ="10", "&gt;" for ">" in text), so the station's text and the patched
	 * game's never matched and every retrofitted ship read as unpatched, patch as you might (heromedel, 5.70). Elements,
	 * attributes and text are compared; comments and the spaces between elements aren't. If either side won't parse,
	 * the text decides as before (comments and whitespace aside).
	 */
	static boolean sameXml(String a, String b) {
		String ca = canonical(a), cb = canonical(b);
		if (ca == null || cb == null) return norm(a).equals(norm(b));
		return ca.equals(cb);
	}
	/** Elements in order, each with its attributes sorted and its text trimmed; null if it isn't XML. */
	private static String canonical(String xml) {
		try {
			javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
			f.setIgnoringComments(true);
			try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception e) { } // no outside entities in a game file
			javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
			b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() { // quiet: a file that won't parse is answered by the text
				@Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
			});
			String body = xml.replaceFirst("^\\s*<\\?xml[^>]*\\?>", ""); // FTL's files may hold several elements: one root round them
			org.w3c.dom.Document d = b.parse(new org.xml.sax.InputSource(new java.io.StringReader("<r>" + body + "</r>")));
			StringBuilder sb = new StringBuilder();
			canonical(d.getDocumentElement(), sb);
			return sb.toString();
		} catch (Exception e) {
			return null;
		}
	}
	private static void canonical(org.w3c.dom.Element e, StringBuilder sb) {
		sb.append('<').append(e.getTagName());
		java.util.TreeMap<String, String> attrs = new java.util.TreeMap<String, String>();
		org.w3c.dom.NamedNodeMap at = e.getAttributes();
		for (int i = 0; i < at.getLength(); i++) attrs.put(at.item(i).getNodeName(), at.item(i).getNodeValue());
		for (Map.Entry<String, String> a : attrs.entrySet()) sb.append(' ').append(a.getKey()).append("=\"").append(a.getValue()).append('"');
		sb.append('>');
		org.w3c.dom.NodeList kids = e.getChildNodes();
		for (int i = 0; i < kids.getLength(); i++) {
			org.w3c.dom.Node n = kids.item(i);
			if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) canonical((org.w3c.dom.Element) n, sb);
			else if (n.getNodeType() == org.w3c.dom.Node.TEXT_NODE || n.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE) {
				String t = n.getNodeValue().replaceAll("\\s+", " ").trim();
				if (!t.isEmpty()) sb.append('"').append(t).append('"');
			}
		}
		sb.append("</").append(e.getTagName()).append('>');
	}

	private static File datFileOf() {
		File dir = homeplanet.core.HomePlanet.datsPath;
		if (dir == null) return null;
		File f = new File(dir, "ftl.dat");
		return f.isFile() ? f : null;
	}
	private static String gameText(String path) {
		byte[] b = gameBytes(path);
		if (b == null) return null;
		try { return new String(b, "UTF-8"); } catch (java.io.UnsupportedEncodingException e) { return null; }
	}
	/** An entry of the dat on disk (read again if the dat changed), the test overlay's copy first. */
	private static byte[] gameBytes(String path) {
		String overlay = System.getProperty("homeplanet.dataOverlay");
		if (overlay != null) {
			File f = new File(overlay, path);
			try { if (f.isFile()) return java.nio.file.Files.readAllBytes(f.toPath()); } catch (IOException e) { }
			File app = new File(overlay, path + ".append"); // a blueprint file: the game's with the overlay's appended, as the manager reads it
			if (app.isFile()) {
				byte[] base = datBytes(path);
				try {
					String b = base == null ? "" : new String(base, "UTF-8"), add = new String(java.nio.file.Files.readAllBytes(app.toPath()), "UTF-8");
					int end = b.lastIndexOf("</FTL>");
					return (end < 0 ? b + add : b.substring(0, end) + add + "\n" + b.substring(end)).getBytes("UTF-8");
				} catch (IOException e) { }
			}
		}
		return datBytes(path);
	}
	private static byte[] datBytes(String path) {
		File dat = datFileOf();
		if (dat == null) { // no dat on disk known (a test world): the loaded data's copy
			DataManager dm = DataManager.get();
			if (!(dm instanceof DefaultDataManager) || !((DefaultDataManager) dm).hasResourceInputStream(path)) return null;
			try { ByteArrayOutputStream o = new ByteArrayOutputStream(); java.io.InputStream in = ((DefaultDataManager) dm).getResourceInputStream(path); byte[] buf = new byte[65536]; for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n); in.close(); return o.toByteArray(); }
			catch (IOException e) { return null; }
		}
		if (!dat.equals(datFile) || dat.length() != datLength || dat.lastModified() != datModified) { entries.clear(); datFile = dat; datLength = dat.length(); datModified = dat.lastModified(); }
		if (entries.containsKey(path)) return entries.get(path);
		byte[] out = null;
		PkgPack pack = null;
		try {
			pack = new PkgPack(dat, "r");
			if (pack.contains(path)) { ByteArrayOutputStream o = new ByteArrayOutputStream(); pack.extractTo(path, o); out = o.toByteArray(); }
		} catch (IOException e) {
			log.warn("Could not read {} from {}: {}", path, dat, e.toString());
		} finally {
			try { if (pack != null) pack.close(); } catch (IOException e) { }
		}
		entries.put(path, out);
		return out;
	}
}
