package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import homeplanet.core.Event;
import homeplanet.core.EventLog;
import homeplanet.core.HistoryLog;
import homeplanet.core.SafeFiles;

/**
 * The fleet folder's layout, and a fleet from before 5.69 converted to it on opening (Overhaul 6.0, Phase 2; the
 * migration of Phase 5, brought forward). The old layout: manifest.xml, ships/&lt;id&gt;.sav, junkyard/&lt;id&gt;.sav,
 * history/&lt;id&gt;/ with her kept versions and side files. The new: a folder per ship under shipyard/, junkyard/ or
 * memorials_and_records/ships/ ({@link ShipStore}). The whole folder is zipped first, beside it; a conversion that
 * fails part way is put back from the zip, and the error says where the zip is.
 */
public final class Layout {
	private static final Logger log = LoggerFactory.getLogger(Layout.class);
	private Layout() { }

	/** The copy of the fleet taken before converting: beside the fleet folder, named for it and the moment. */
	public static File backup(File root) {
		String stamp;
		synchronized (STAMP) { stamp = STAMP.format(new Date()); }
		return new File(root.getParentFile(), root.getName() + "-before-6.0-" + stamp + ".zip");
	}
	private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyyMMdd-HHmmss");

	/** Converts the fleet in place. Nothing of hers is lost: every file goes somewhere, and the zip keeps it all as it was. */
	static void convert(Vault v) throws IOException {
		File root = v.root;
		File zip = backup(root);
		SafeFiles.zipFolder(root, zip, null);
		log.info("Converting {} to the 6.0 layout; a copy of it as it was is in {}", root, zip);
		Map<String, String[]> manifest = readManifest(new File(root, Vault.MANIFEST)); // id -> {name, state, dlc, hash, marks, stranger, fresh}
		int ships = 0, remembered = 0;
		try {
			for (Map.Entry<String, String[]> e : manifest.entrySet()) {
				String id = e.getKey(); String[] m = e.getValue();
				if (id.equals(Vault.STORAGE_ID)) continue; // the Cargo Hold's save stays at the root, its record beside it (saveManifest)
				Ship.State state = Ship.State.of(m[1]);
				File folder = new File(state == Ship.State.JUNKED ? v.junkyardDir() : v.shipyardDir(), ShipStore.stem(m[0], id));
				if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not create " + folder);
				File sav = state == Ship.State.JUNKED ? new File(new File(root, "junkyard"), id + ".sav") : new File(v.oldShipsDir(), id + ".sav");
				if (sav.isFile()) SafeFiles.move(sav, ShipStore.sav(folder));
				moveHistory(new File(v.oldHistoryDir(), id), folder);
				ShipStore.Record r = new ShipStore.Record(id);
				r.name = m[0]; r.state = state.key; r.dlc = "true".equals(m[2]); r.hash = m[3]; r.marks = m[4]; r.stranger = "true".equals(m[5]); r.fresh = m[6];
				ShipStore.write(folder, r);
				ships++;
			}
			File[] dirs = v.oldHistoryDir().listFiles();
			if (dirs != null) for (File d : dirs) {
				if (!d.isDirectory()) continue;
				String id = d.getName();
				if (manifest.containsKey(id)) { if (!SafeFiles.deleteTree(d)) log.warn("Could not remove the converted {}", d); continue; }
				String name = departedName(d, id);
				File folder = new File(v.memorialDir(), ShipStore.stem(name, id));
				if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not create " + folder);
				moveHistory(d, folder);
				ShipStore.Record r = new ShipStore.Record(id);
				r.name = name; r.state = "departed";
				ShipStore.write(folder, r);
				remembered++;
			}
			// the old folders go once they're empty; what's left in them (a stray save) is adopted from the shipyard instead
			File oldJunk = new File(root, "junkyard"); // the same folder serves the new layout: only the old files were in it
			for (File f : safeList(v.oldShipsDir())) if (f.isFile()) SafeFiles.move(f, new File(v.shipyardDir(), f.getName()));
			for (File f : safeList(oldJunk)) if (f.isFile()) SafeFiles.move(f, new File(v.junkyardDir(), f.getName()));
			if (v.oldShipsDir().isDirectory() && !v.oldShipsDir().delete()) log.warn("Could not remove the old {}", v.oldShipsDir());
			if (v.oldHistoryDir().isDirectory() && !SafeFiles.deleteTree(v.oldHistoryDir())) log.warn("Could not remove the old {}", v.oldHistoryDir());
			File manifestFile = new File(root, Vault.MANIFEST);
			if (manifest.containsKey(Vault.STORAGE_ID)) { // the hold's record, before the manifest goes
				String[] m = manifest.get(Vault.STORAGE_ID);
				ShipStore.Record r = new ShipStore.Record(Vault.STORAGE_ID);
				r.name = m[0]; r.state = "storage"; r.dlc = "true".equals(m[2]); r.hash = m[3];
				ShipStore.write(root, Vault.STORAGE_ID, r);
			}
			if (manifestFile.isFile() && !manifestFile.delete()) throw new IOException("Could not remove " + manifestFile);
			new File(root, Vault.MANIFEST + ".bak").delete();
		} catch (IOException e) {
			log.error("Converting " + root + " failed; putting it back from " + zip, e);
			restore(root, zip);
			throw new IOException("The Home Planet Station could not convert the fleet in " + root.getName() + " to its new layout: " + e.getMessage()
					+ ". The fleet was put back as it was; a copy is in " + zip);
		}
		String words = ships + (ships == 1 ? " ship" : " ships") + " and " + remembered + " remembered" + (remembered == 1 ? "" : "") + " into folders of their own";
		HistoryLog.entry("LAYOUT", "The station's records were rearranged: " + words + " (a copy of the fleet as it was is kept beside it)", null,
				Event.of("LAYOUT").put("what", "converted").put("to", "6.0").put("ships", ships).put("remembered", remembered).put("backup", zip.getName()));
		EventLog.write(v, Event.of("LAYOUT").put("what", "converted").put("to", "6.0").put("ships", ships).put("remembered", remembered).put("backup", zip.getName())
				.human("The station's records were rearranged, every ship into a folder of her own."));
	}
	private static File[] safeList(File d) { File[] fs = d.listFiles(); return fs == null ? new File[0] : fs; }

	/** history/&lt;id&gt;/ into her folder: versions into versions/ (the special copies by their new prefixes), everything else beside her record. */
	private static void moveHistory(File from, File folder) throws IOException {
		if (!from.isDirectory()) return;
		File versions = ShipStore.versions(folder);
		for (File f : safeList(from)) {
			if (!f.isFile()) continue;
			String n = f.getName();
			File to;
			if (n.endsWith(".sav") && !n.equals("final-battle.sav")) {
				if (!versions.isDirectory() && !versions.mkdirs()) throw new IOException("Could not create " + versions);
				to = new File(versions, n.startsWith("cloud-copy-") ? "cloud-" + n.substring("cloud-copy-".length()) : n);
			} else {
				to = new File(folder, n);
			}
			SafeFiles.move(f, to);
		}
		File[] left = from.listFiles();
		if (left != null) for (File f : left) if (f.isDirectory()) SafeFiles.move(f, new File(folder, f.getName())); // nothing of ours, but kept
		if (!from.delete()) log.warn("Could not remove {}", from);
	}
	/** A departed ship's name, from her fate or her museum record, else her id. */
	private static String departedName(File d, String id) {
		File fate = new File(d, "fate.txt");
		if (fate.isFile()) {
			try {
				String[] lines = new String(SafeFiles.read(fate), StandardCharsets.UTF_8).split("\r?\n");
				if (lines.length > 1 && !lines[1].trim().isEmpty()) return lines[1].trim();
			} catch (IOException e) { /* her id, then */ }
		}
		File museum = new File(d, "museum.txt");
		if (museum.isFile()) {
			String n = homeplanet.core.Store.read(museum).getProperty("name", "").trim();
			if (!n.isEmpty()) return n;
		}
		return id;
	}
	/** The old manifest's entries, by id. */
	static Map<String, String[]> readManifest(File manifest) {
		Map<String, String[]> out = new LinkedHashMap<String, String[]>();
		if (!manifest.isFile()) return out;
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest);
			NodeList ns = doc.getElementsByTagName("ship");
			for (int i = 0; i < ns.getLength(); i++) {
				Element e = (Element) ns.item(i);
				String id = e.getAttribute("id");
				if (id.isEmpty()) continue;
				out.put(id, new String[] {e.getAttribute("name"), e.getAttribute("state"), e.getAttribute("dlc"), e.getAttribute("hash"), e.getAttribute("marks"), e.getAttribute("stranger"), e.getAttribute("fresh")});
			}
		} catch (Exception e) {
			log.error("Could not read " + manifest + ": the fleet is rebuilt from its files", e);
		}
		return out;
	}
	/** Puts the fleet back from its zip: the folder emptied, then the zip unpacked into it. */
	private static void restore(File root, File zip) {
		try {
			SafeFiles.deleteTree(root);
			root.mkdirs();
			java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zip));
			try {
				for (java.util.zip.ZipEntry e; (e = z.getNextEntry()) != null; ) {
					File f = new File(root, e.getName());
					if (!f.getCanonicalPath().startsWith(root.getCanonicalPath())) continue;
					if (e.isDirectory()) { f.mkdirs(); continue; }
					f.getParentFile().mkdirs();
					java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
					byte[] buf = new byte[65536];
					for (int n; (n = z.read(buf)) > 0; ) b.write(buf, 0, n);
					SafeFiles.write(f, b.toByteArray());
				}
			} finally { z.close(); }
		} catch (IOException e) {
			log.error("Could not put " + root + " back from " + zip, e);
		}
	}
	/** For the harness: an old-layout fleet made from a converted one, so the conversion can be tested. */
	public static void unconvert(Vault v) throws IOException {
		File root = v.root;
		StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<manifest version=\"1\">\r\n");
		List<File> all = new ArrayList<File>(v.shipFolders());
		for (File d : all) {
			ShipStore.Record r = ShipStore.read(d);
			if (r == null) continue;
			boolean memorial = d.getParentFile().getAbsoluteFile().equals(v.memorialDir().getAbsoluteFile());
			boolean junk = d.getParentFile().getAbsoluteFile().equals(v.junkyardDir().getAbsoluteFile());
			File hist = new File(v.oldHistoryDir(), r.id);
			if (!hist.isDirectory() && !hist.mkdirs()) throw new IOException("Could not create " + hist);
			File sav = ShipStore.sav(d);
			if (!memorial && sav.isFile()) SafeFiles.move(sav, new File(junk ? new File(root, "junkyard") : v.oldShipsDir(), r.id + ".sav"));
			else if (sav.isFile()) SafeFiles.move(sav, new File(hist, "19700101-000000.sav"));
			for (File f : safeList(ShipStore.versions(d))) if (f.isFile()) SafeFiles.move(f, new File(hist, f.getName().startsWith("cloud-") ? "cloud-copy-" + f.getName().substring(6) : f.getName()));
			for (File f : safeList(d)) {
				if (f.isDirectory() || f.getName().equals(ShipStore.xml(d).getName())) continue;
				SafeFiles.move(f, new File(hist, f.getName().equals(ShipStore.logFile(d).getName()) ? "ship.log" : f.getName()));
			}
			if (!memorial) sb.append("\t<ship id=\"").append(r.id).append("\" name=\"").append(homeplanet.parser.XmlText.attr(r.name)).append("\" state=\"").append(junk ? "junked" : "boarded".equals(r.state) ? "boarded" : "docked")
					.append("\" dlc=\"").append(r.dlc).append("\" hash=\"").append(r.hash).append("\"").append(r.stranger ? " stranger=\"true\"" : "").append("/>\r\n");
			SafeFiles.deleteTree(d);
		}
		ShipStore.Record hold = ShipStore.read(root, Vault.STORAGE_ID);
		if (hold != null) { sb.append("\t<ship id=\"storage\" name=\"").append(homeplanet.parser.XmlText.attr(hold.name)).append("\" state=\"storage\" dlc=\"true\" hash=\"").append(hold.hash).append("\"/>\r\n"); new File(root, Vault.STORAGE_ID + ".xml").delete(); }
		sb.append("</manifest>\r\n");
		SafeFiles.writeText(new File(root, Vault.MANIFEST), sb.toString(), false);
		SafeFiles.deleteTree(v.shipyardDir());
		SafeFiles.deleteTree(new File(root, "memorials_and_records"));
	}
}
