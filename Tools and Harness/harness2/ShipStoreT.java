import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.vault.*;
/** A ship's folder (Overhaul 6.0, Phase 2 step 13): her record written and read back, her folder named, renamed and moved, her log, her versions kept apart. args: game, saves, work dir. */
public class ShipStoreT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 File yard = new File(work, "shipyard");
 // names
 Setup.chk("N: the stem: her name cleaned and capped, a dot, her id", ShipStore.stem("Shippy McShipface", "c77a").equals("Shippy McShipface.c77a")
   && ShipStore.stem("A: name? with <bad> chars/slashes", "x1").indexOf('/') < 0 && ShipStore.stem("", "x1").equals("ship.x1")
   && ShipStore.stem("An Extraordinarily Long Ship Name That Goes On And On", "x1").length() <= ShipStore.NAME_MAX + 3);
 Setup.chk("N: the id read back from the folder", "c77a".equals(ShipStore.idOf(new File(yard, "Shippy McShipface.c77a"))) && "x1".equals(ShipStore.idOf(new File(yard, "A.B.x1"))) && ShipStore.idOf(new File(yard, "noid")) == null);
 // a record, round trip
 ShipStore.Record r = new ShipStore.Record("a3f2");
 r.name = "Kestrel \"Lucky\" & <Co>"; r.state = "docked"; r.dlc = true; r.hash = "abc"; r.marks = "m|1|2"; r.fresh = "3|4";
 r.owners.add("heromedel"); r.owners.add("Commander Vance"); r.pastNames.add("Old Glory");
 r.section("fate").setProperty("kind", "LOST"); r.section("fate").setProperty("name", "Kestrel");
 r.section("trade").setProperty("from", "Commander Vance"); r.section("trade").setProperty("date", "2026-10-07 14:02");
 r.section("last").setProperty("crew", "Bob (Human)|Gracie (Rockman)"); r.section("last").setProperty("odd key:here", "v=1 w=\"2\"");
 File folder = ShipStore.folder(yard, r);
 ShipStore.write(folder, r);
 ShipStore.Record b = ShipStore.read(folder);
 Setup.chk("R: written and read back: the attributes", b != null && b.id.equals("a3f2") && b.name.equals(r.name) && b.state.equals("docked") && b.dlc && b.hash.equals("abc") && b.marks.equals("m|1|2") && b.fresh.equals("3|4") && !b.stranger);
 Setup.chk("R: the owners and past names, in order", b.owners.equals(r.owners) && b.pastNames.equals(r.pastNames));
 Setup.chk("R: the sections, with odd keys and values whole", b.section("fate").getProperty("kind").equals("LOST") && b.section("trade").getProperty("date").equals("2026-10-07 14:02")
   && b.section("last").getProperty("crew").equals("Bob (Human)|Gracie (Rockman)") && b.section("last").getProperty("odd_key_here").equals("v=1 w=\"2\"") && !b.has("museum"));
 Setup.chk("R: no record: null; a folder listing finds hers alone", ShipStore.read(new File(yard, "none.x")) == null && ShipStore.folders(yard).size() == 1 && ShipStore.folders(yard).get(0).equals(folder));
 // her save, log and versions
 Ship d = v.docked().get(0);
 byte[] save = SafeFiles.read(v.fileOf(d));
 SafeFiles.write(ShipStore.sav(folder), save);
 ShipStore.log(v, folder, r, Event.of("TEST").put("what", "one").human("One."));
 ShipStore.log(v, folder, r, Event.of("TEST").put("what", "two").human("Two."));
 List<EventLog.Entry> es = ShipStore.entries(folder);
 Setup.chk("L: her log: two entries, her fields first, the stardate the fleet's", es.size() == 2 && es.get(0).get("ship").equals(r.name + ".a3f2") && es.get(1).get("what").equals("two") && es.get(0).day >= 1);
 File v1 = ShipStore.keepVersion(folder, save, null); File v2 = ShipStore.keepVersion(folder, save, null); File win = ShipStore.keepVersion(folder, save, "victory-"); File fb = ShipStore.keepVersion(folder, save, "final-battle-");
 Setup.chk("V: versions named by stamp, a counter in the same second, the special copies by prefix", v1.getName().matches("\\d{8}-\\d{6}\\.sav") && v2.getName().matches("\\d{8}-\\d{6}(-2)?\\.sav") && win.getName().startsWith("victory-") && ShipStore.isSpecial(fb) && !ShipStore.isSpecial(v1));
 for (int i = 0; i < 3; i++) ShipStore.keepVersion(folder, save, null);
 ShipStore.prune(folder, 2);
 Setup.chk("V: pruned to two ordinary versions, the special copies untouched", ShipStore.versions(folder, false).size() == 2 && ShipStore.versions(folder, true).size() == 2 && win.isFile() && fb.isFile());
 // renamed: the folder and her files follow, her id doesn't, the old name kept
 File renamed = ShipStore.rename(folder, b, "Nightjar");
 Setup.chk("M: renamed: folder, record, save and log follow the new name; the old one is among her past names", renamed.getName().equals("Nightjar.a3f2") && ShipStore.xml(renamed).isFile() && ShipStore.sav(renamed).isFile() && ShipStore.logFile(renamed).isFile()
   && !folder.exists() && ShipStore.read(renamed).pastNames.contains(r.name) && ShipStore.read(renamed).name.equals("Nightjar") && ShipStore.versions(renamed, false).size() == 2);
 File junk = new File(work, "junkyard");
 File moved = ShipStore.move(renamed, junk);
 Setup.chk("M: moved to the Junkyard as one rename, everything with her", moved.getParentFile().equals(junk) && ShipStore.read(moved).id.equals("a3f2") && ShipStore.entries(moved).size() == 2 && !renamed.exists());
 boolean refused = false; try { ShipStore.move(moved, junk); } catch (IOException e) { refused = true; }
 Setup.chk("M: a move onto a folder already there is refused", refused && moved.isDirectory());
 // her notes as sections of her record (5.98), read back through ShipStore.notes and ShipStore.fate
 ShipStore.Record t = ShipStore.read(v.historyOf(d));
 t.sections.put(ShipStore.FATE, ShipStore.fateNotes("TRANSFERRED", d.name, "Commander Vance"));
 t.section(ShipStore.TRADE).setProperty("trade", "t1"); t.section(ShipStore.TRADE).setProperty("from", "Commander Vance"); t.section(ShipStore.TRADE).setProperty("original", "heromedel");
 t.section("museum").setProperty("epitaph", "She came home.\nTwice, \"almost\" & <once> more.\tThe end");
 File tf = ShipStore.folder(yard, t); ShipStore.write(tf, t);
 Setup.chk("T: and written, she reads back the same: her mark, her fate, an epitaph with line breaks, quotes and tabs", ShipStore.notes(tf, ShipStore.TRADE).getProperty("original").equals("heromedel")
   && ShipStore.fate(tf)[0].equals("TRANSFERRED") && ShipStore.fate(tf)[2].equals("Commander Vance") && ShipStore.notes(tf, ShipStore.MUSEUM).getProperty("epitaph").equals("She came home.\nTwice, \"almost\" & <once> more.\tThe end"));
 Setup.done();
}
}
