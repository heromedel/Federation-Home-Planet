package homeplanet.convert;

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
import homeplanet.core.HistoryLog;
import homeplanet.core.SafeFiles;
import homeplanet.vault.CrewRegister;
import homeplanet.vault.Ship;
import homeplanet.vault.ShipStore;
import homeplanet.vault.Vault;

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
		return new File(root.getParentFile(), root.getName() + "-before-conversion-" + stamp + ".zip");
	}
	private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyyMMdd-HHmmss");

	/** Converts the fleet in place. Nothing of hers is lost: every file goes somewhere, and the zip keeps it all as it was. */
	static void convert(Vault v) throws IOException {
		File root = v.root;
		File zip = backup(root);
		SafeFiles.zipFolder(root, zip, null);
		log.info("Converting {} to the folder layout; a copy of it as it was is in {}", root, zip);
		Map<String, String[]> manifest = readManifest(OldFleet.manifest(root)); // id -> {name, state, dlc, hash, marks, stranger, fresh}
		int ships = 0, remembered = 0;
		try {
			for (Map.Entry<String, String[]> e : manifest.entrySet()) {
				String id = e.getKey(); String[] m = e.getValue();
				if (id.equals(Vault.STORAGE_ID)) continue; // the Cargo Hold's save stays at the root, its record beside it (saveManifest)
				Ship.State state = Ship.State.of(m[1]);
				File folder = new File(state == Ship.State.JUNKED ? v.junkyardDir() : v.shipyardDir(), ShipStore.stem(m[0], id));
				if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not create " + folder);
				File sav = state == Ship.State.JUNKED ? new File(new File(root, "junkyard"), id + ".sav") : new File(OldFleet.shipsDir(v.root), id + ".sav");
				if (sav.isFile()) SafeFiles.move(sav, ShipStore.sav(folder));
				moveHistory(new File(OldFleet.historyDir(v.root), id), folder);
				ShipStore.Record r = new ShipStore.Record(id);
				r.name = m[0]; r.state = state.key; r.dlc = "true".equals(m[2]); r.hash = m[3]; r.marks = m[4]; r.stranger = "true".equals(m[5]); r.fresh = m[6];
				ShipStore.write(folder, r);
				ships++;
			}
			File[] dirs = OldFleet.historyDir(v.root).listFiles();
			if (dirs != null) for (File d : dirs) {
				if (!d.isDirectory()) continue;
				String id = d.getName();
				if (manifest.containsKey(id)) { if (!SafeFiles.deleteTree(d)) log.warn("Could not remove the converted {}", d); continue; }
				// a conversion stopped while moving her (5.992): carry on in the folder it began, named from her fate before it moved there
				File folder = begun(v.memorialDir(), id);
				String name = folder != null ? nameIn(folder, id) : departedName(d, id);
				if (folder == null) folder = new File(v.memorialDir(), ShipStore.stem(name, id));
				if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not create " + folder);
				moveHistory(d, folder);
				ShipStore.Record r = new ShipStore.Record(id);
				r.name = name; r.state = "departed";
				ShipStore.write(folder, r);
				remembered++;
			}
			// and one stopped after her history was moved but before her record was written: her folder is all there is of her now
			for (File f : safeList(v.memorialDir())) {
				String id = ShipStore.idOf(f);
				if (!f.isDirectory() || id == null || ShipStore.read(f) != null) continue;
				ShipStore.Record r = new ShipStore.Record(id);
				r.name = nameIn(f, id); r.state = "departed";
				ShipStore.write(f, r);
				remembered++;
			}
			// the old folders go once they're empty; what's left in them (a stray save) is adopted from the shipyard instead
			File oldJunk = new File(root, "junkyard"); // the same folder serves the new layout: only the old files were in it
			for (File f : safeList(OldFleet.shipsDir(v.root))) if (f.isFile()) SafeFiles.move(f, new File(v.shipyardDir(), f.getName()));
			for (File f : safeList(oldJunk)) if (f.isFile()) SafeFiles.move(f, new File(v.junkyardDir(), f.getName()));
			if (OldFleet.shipsDir(v.root).isDirectory() && !OldFleet.shipsDir(v.root).delete()) log.warn("Could not remove the old {}", OldFleet.shipsDir(v.root));
			if (OldFleet.historyDir(v.root).isDirectory() && !SafeFiles.deleteTree(OldFleet.historyDir(v.root))) log.warn("Could not remove the old {}", OldFleet.historyDir(v.root));
			File manifestFile = OldFleet.manifest(root);
			if (manifest.containsKey(Vault.STORAGE_ID)) { // the hold's record, before the manifest goes
				String[] m = manifest.get(Vault.STORAGE_ID);
				ShipStore.Record r = new ShipStore.Record(Vault.STORAGE_ID);
				r.name = m[0]; r.state = "storage"; r.dlc = "true".equals(m[2]); r.hash = m[3];
				ShipStore.write(root, Vault.STORAGE_ID, r);
			}
			if (manifestFile.isFile() && !manifestFile.delete()) throw new IOException("Could not remove " + manifestFile);
			new File(root, OldFleet.MANIFEST + ".bak").delete();
		} catch (IOException e) {
			log.error("Converting " + root + " failed; putting it back from " + zip, e);
			restore(root, zip);
			throw new IOException("The Home Planet Station could not convert the fleet in " + root.getName() + " to its new layout: " + e.getMessage()
					+ ". The fleet was put back as it was; a copy is in " + zip);
		}
		String words = ships + (ships == 1 ? " ship" : " ships") + " and " + remembered + " remembered" + (remembered == 1 ? "" : "") + " into folders of their own";
		HistoryLog.entry("LAYOUT", "The station's records were rearranged: " + words + " (a copy of the fleet as it was is kept beside it)", null,
				Event.of("LAYOUT").put("what", "converted").put("to", "folders").put("ships", ships).put("remembered", remembered).put("backup", zip.getName())
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
				to = n.equals("ship.log") ? ShipStore.logFile(folder) : new File(folder, n); // her log under her own name (unconvert names it ship.log)
			}
			SafeFiles.move(f, to);
		}
		File[] left = from.listFiles();
		if (left != null) for (File f : left) if (f.isDirectory()) SafeFiles.move(f, new File(folder, f.getName())); // nothing of ours, but kept
		if (!from.delete()) log.warn("Could not remove {}", from);
	}
	/** The folder a stopped conversion began for her in the memorial, or null. */
	private static File begun(File memorial, String id) {
		for (File f : safeList(memorial)) if (f.isDirectory() && id.equals(ShipStore.idOf(f))) return f;
		return null;
	}
	/** Her name in a folder already begun: from her fate or museum record, moved there by now, else as the folder has it ("Red-Tail.a9ee…" is Red-Tail). */
	private static String nameIn(File folder, String id) {
		String fromNotes = departedName(folder, id);
		if (!fromNotes.equals(id)) return fromNotes;
		String n = folder.getName();
		return n.endsWith("." + id) && n.length() > id.length() + 1 ? n.substring(0, n.length() - id.length() - 1) : id;
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
					if (!f.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) continue;
					if (e.isDirectory()) { f.mkdirs(); continue; }
					f.getParentFile().mkdirs();
					java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
					byte[] buf = new byte[65536];
					for (int n; (n = z.read(buf)) > 0; ) b.write(buf, 0, n);
					SafeFiles.write(f, b.toByteArray());
					long t = e.getLastModifiedTime() != null ? e.getLastModifiedTime().toMillis() : e.getTime();
					if (t > 0) f.setLastModified(t); // its own time back: a ship's versions are ordered by it (5.94)
				}
			} finally { z.close(); }
		} catch (IOException e) {
			log.error("Could not put " + root + " back from " + zip, e);
		}
	}
	/** For the harness: an old-layout fleet made from a converted one, so the conversion can be tested. */
	public static void unconvert(Vault v) throws IOException {
		CrewRegister.unconvert(v); // the crew files back into one crew.txt, before the folders that hold them go
		File root = v.root;
		StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<manifest version=\"1\">\r\n");
		List<File> all = new ArrayList<File>(v.shipFolders());
		for (File d : all) {
			ShipStore.Record r = ShipStore.read(d);
			if (r == null) continue;
			boolean memorial = d.getParentFile().getAbsoluteFile().equals(v.memorialDir().getAbsoluteFile());
			boolean junk = d.getParentFile().getAbsoluteFile().equals(v.junkyardDir().getAbsoluteFile());
			File hist = new File(OldFleet.historyDir(v.root), r.id);
			if (!hist.isDirectory() && !hist.mkdirs()) throw new IOException("Could not create " + hist);
			File sav = ShipStore.sav(d);
			if (!memorial && sav.isFile()) SafeFiles.move(sav, new File(junk ? new File(root, "junkyard") : OldFleet.shipsDir(v.root), r.id + ".sav"));
			else if (sav.isFile()) SafeFiles.move(sav, new File(hist, "19700101-000000.sav"));
			for (File f : safeList(ShipStore.versions(d))) if (f.isFile()) SafeFiles.move(f, new File(hist, f.getName().startsWith("cloud-") ? "cloud-copy-" + f.getName().substring(6) : f.getName()));
			for (File f : safeList(d)) {
				if (f.isDirectory() || f.getName().equals(ShipStore.xml(d).getName())) continue;
				SafeFiles.move(f, new File(hist, f.getName().equals(ShipStore.logFile(d).getName()) ? "ship.log" : f.getName()));
			}
			for (String[] side : OldFleet.SIDE_FILES) if (r.has(side[0])) homeplanet.core.Store.write(new File(hist, side[1]), r.sections.get(side[0]), "as before 5.98"); // her record's sections back into side files
			String[] fate = ShipStore.fate(d);
			if (fate != null) SafeFiles.writeText(new File(hist, OldFleet.FATE_FILE), fate[0] + "\n" + fate[1] + "\n" + (fate[2].isEmpty() ? "" : fate[2] + "\n"), false);
			if (!memorial) sb.append("\t<ship id=\"").append(r.id).append("\" name=\"").append(homeplanet.parser.XmlText.attr(r.name)).append("\" state=\"").append(junk ? "junked" : "boarded".equals(r.state) ? "boarded" : "docked")
					.append("\" dlc=\"").append(r.dlc).append("\" hash=\"").append(r.hash).append("\"").append(r.stranger ? " stranger=\"true\"" : "").append("/>\r\n");
			SafeFiles.deleteTree(d);
		}
		File holdDir = v.cargoHoldDir();
		File holdXml = new File(holdDir, Vault.HOLD_FILE);
		if (homeplanet.parser.HoldXml.isHold(holdXml)) { // its xml back into the pretend ship's save (5.84)
			net.blerf.ftl.parser.SavedGameParser.SavedGameState gs = homeplanet.parser.HoldXml.read(holdXml);
			File sav = new File(holdDir, OldFleet.HOLD_SAV);
			SafeFiles.write(sav, homeplanet.parser.SaveHelper.toBytes(gs));
			sb.append("\t<ship id=\"storage\" name=\"").append(homeplanet.parser.XmlText.attr(gs.getPlayerShipName())).append("\" state=\"storage\" dlc=\"true\" hash=\"").append(SafeFiles.hash(sav)).append("\"/>\r\n");
			holdXml.delete();
		}
		String[][] back = {{OldFleet.HOLD_SAV, OldFleet.STORAGE_FILE}, {"systems.txt", "storage-systems.txt"}, {"parts.txt", "parts.txt"}, {"overflow.txt", "overflow.txt"}};
		for (String[] f : back) { File now = new File(holdDir, f[0]); if (now.isFile()) SafeFiles.move(now, new File(root, f[1])); }
		SafeFiles.deleteTree(holdDir);
		for (String name : OldFleet.LOG_FILES) { File now = new File(v.logsDir(), name); if (now.isFile()) SafeFiles.move(now, new File(root, name)); } // the logs at the root, as before 5.71
		SafeFiles.deleteTree(v.logsDir());
		sb.append("</manifest>\r\n");
		SafeFiles.writeText(OldFleet.manifest(root), sb.toString(), false);
		SafeFiles.deleteTree(v.shipyardDir());
		SafeFiles.deleteTree(new File(root, "memorials_and_records"));
	}
}
