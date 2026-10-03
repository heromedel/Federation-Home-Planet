package homeplanet.parser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import homeplanet.core.HistoryLog;
import homeplanet.core.SafeFiles;
import homeplanet.vault.Vault;

/**
 * A copy of every blueprint the station has drawn up, one file each in the vault's blueprints folder: remodels (as
 * remodels.xml has them) and designs' built copies (as designs.xml has them). remodels.xml and designs.xml stay the
 * master copies; a backup is only read when a ship needs a blueprint the master lacks (a damaged or lost file), and
 * then it's added back, to be written into the master with its next save. It never overrides the master.
 */
public final class BlueprintBackup {
	private static final Logger log = LoggerFactory.getLogger(BlueprintBackup.class);
	private static final String CRLF = "\r\n";
	private BlueprintBackup() { }

	private static File dir() { return Vault.get().blueprintsDir(); }
	private static File fileOf(String bpId) { return new File(dir(), bpId + ".xml"); }

	/** After remodels.xml is written: a backup of each remodel (only the ones that changed are rewritten). */
	static void keepRemodels(List<CompanionMod.Remodel> remodels) {
		for (CompanionMod.Remodel r : remodels) keep(r.id, "<remodels>" + CRLF + CompanionMod.remodelXml(r) + "</remodels>" + CRLF);
	}
	/** After designs.xml is written: a backup of each built copy (what ships fly; working drafts aren't blueprints yet). */
	static void keepDesigns(List<ShipDesign> designs) {
		for (ShipDesign d : designs) {
			if (!d.built || d.isWorking()) continue;
			keep(DesignExport.bpId(d), "<designs>" + CRLF + ShipDesign.xmlOf(d) + "</designs>" + CRLF);
		}
	}
	private static void keep(String bpId, String xml) {
		File f = fileOf(bpId);
		byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
		try {
			if (f.isFile() && java.util.Arrays.equals(SafeFiles.read(f), bytes)) return;
			SafeFiles.write(f, bytes);
		} catch (Exception e) {
			log.warn("Could not keep a backup of blueprint {}: {}", bpId, e.toString()); // the master copy was written: not fatal
		}
	}

	/** Adds to {@code out} each backed-up remodel a ship needs that {@code out} lacks. */
	static void restoreRemodels(List<CompanionMod.Remodel> out) {
		Set<String> have = new java.util.HashSet<String>();
		for (CompanionMod.Remodel r : out) have.add(r.id);
		for (File f : missing(have, "<remodels>")) {
			try {
				List<CompanionMod.Remodel> got = new ArrayList<CompanionMod.Remodel>();
				CompanionMod.readInto(f, got);
				for (CompanionMod.Remodel r : got) if (have.add(r.id)) { out.add(r); restored(r.id); }
			} catch (Exception e) {
				log.warn("Could not read the blueprint backup {}: {}", f, e.toString());
			}
		}
	}
	/** Adds to {@code out} each backed-up built design copy a ship needs that {@code out} lacks. */
	static void restoreDesigns(List<ShipDesign> out) {
		Set<String> have = new java.util.HashSet<String>();
		for (ShipDesign d : out) if (d.built && !d.isWorking()) have.add(DesignExport.bpId(d));
		for (File f : missing(have, "<designs>")) {
			try {
				List<ShipDesign> got = new ArrayList<ShipDesign>();
				ShipDesign.readInto(f, got);
				for (ShipDesign d : got) {
					String bpId = DesignExport.bpId(d);
					if (!have.add(bpId)) continue;
					if (!d.isWorking()) { boolean designKept = false; for (ShipDesign x : out) if (x.id.equals(d.id)) designKept = true; if (!designKept) d.retired = true; } // its design is gone: keep it as retired
					out.add(d);
					restored(bpId);
				}
			} catch (Exception e) {
				log.warn("Could not read the blueprint backup {}: {}", f, e.toString());
			}
		}
	}
	/** The backups of blueprints the master lacks that a ship (or a ship's kept records) still names. Empty, cheaply, in the usual case. */
	private static List<File> missing(Set<String> have, String kind) {
		List<File> out = new ArrayList<File>();
		if (!Vault.isOpen()) return out;
		File[] files = dir().listFiles();
		if (files == null) return out;
		List<File> candidates = new ArrayList<File>();
		for (File f : files) {
			String name = f.getName();
			if (!name.endsWith(".xml") || have.contains(name.substring(0, name.length() - 4))) continue;
			// only backups of this kind: a remodel's backup is always "missing" from the designs, and must not
			// set off the scan of every save below each time designs are read
			try {
				if (new String(SafeFiles.read(f), StandardCharsets.UTF_8).startsWith(kind)) candidates.add(f);
			} catch (Exception e) { log.debug("Blueprint backup {} could not be read: {}", f, e.toString()); }
		}
		if (candidates.isEmpty()) return out;
		Set<String> needed = Vault.get().blueprintsInUseOrHistory();
		for (File f : candidates) {
			String id = f.getName().substring(0, f.getName().length() - 4);
			if (needed.contains(id)) out.add(f);
		}
		return out;
	}
	private static final Set<String> told = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
	/** Logged once a session: designs.xml is read often, and the restore repeats until its next save writes it in. */
	private static void restored(String bpId) {
		if (!told.add(bpId)) return;
		log.warn("Blueprint {} was missing from the station's files: restored from its backup", bpId);
		HistoryLog.entry("BLUEPRINT", bpId + " restored from its backup (a ship still needs it)");
	}
}
