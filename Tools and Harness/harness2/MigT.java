import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.vault.*; import homeplanet.convert.*;
/**
 * A 5.x fleet converted to the 6.0 layout on opening (Overhaul 6.0, Phase 2 step 2): a fleet with some history is put
 * back into the old layout (manifest, ships/, junkyard/, history/), reopened, and comes out as it was: every ship, her
 * versions and special copies, her side files and her log; the departed in the memorial; the hold's record; a zip of the
 * fleet as it was beside it. And every path stays short enough for Windows. args: game, saves, work dir.
 */
public class MigT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 File root = v.root;
 // a fleet with some history: versions, a victory and a cloud copy, side files, a renamed ship, a hull in the Junkyard, a ship destroyed
 Ship b = v.boarded(); List<Ship> docked = v.docked();
 Ship d1 = docked.get(0), d2 = docked.get(1), d3 = docked.get(2);
 for (int i = 0; i < 3; i++) { SavedGameState g = v.readCopy(d1).save; g.getPlayerShip().setScrapAmt(100 + i); v.write(d1, g); Thread.sleep(2); }
 ShipStore.keepVersion(v.historyOf(d1), SafeFiles.read(v.fileOf(d1)), "victory-");
 ShipStore.keepVersion(v.historyOf(d1), SafeFiles.read(v.fileOf(d1)), "cloud-");
 Setup.side(v, v.historyOf(d1), "traded.txt", "trade=t1\ndate=2026-01-01 00:00\nfrom=Commander Bree\noriginal=Captain Ash\ndefeated=0\nbeacons=0\nscrap=0\nsectors=0\n");
 SavedGameState rn = v.readCopy(d1).save; rn.setPlayerShipName("Nightjar Renamed"); rn.getPlayerShip().setShipName("Nightjar Renamed"); v.write(d1, rn);
 Setup.chk("A: renamed, her folder follows her name", v.folderOf(d1).getName().equals("Nightjar Renamed." + d1.id) && v.fileOf(d1).isFile());
 v.board(d2); v.disband();
 v.board(d3); v.disband(); v.remove(d3, "DESTROY");
 v.board(b); // the first ship boarded again
 Setup.chk("A: a hull in the Junkyard, a ship destroyed and remembered", d2.state == Ship.State.JUNKED && v.byId(d3.id) == null && !Setup.fateText(v.folderOfId(d3.id)).isEmpty()
   && v.folderOfId(d3.id).getParentFile().equals(v.memorialDir()));
 v.takeStock(); // the register caught up with the rename, the decommission and the loss before its picture is taken
 Map<String, String> before = picture(v);
 List<String> crewBefore = crew(v);
 int eventsBefore = EventLog.read(v).size();
 // back to the old layout, as a 5.x station left it
 Layout.unconvert(v);
 Setup.chk("B: the old layout: a manifest, ships/, junkyard/ and history/ of files, the hold and the logs at the root; no folders", new File(root, "manifest.xml").isFile() && new File(root, "storage.sav").isFile() && new File(root, "events.log").isFile() && !new File(root, "cargohold").exists() && !new File(root, "logs").exists() && new File(root, "ships/" + d1.id + ".sav").isFile()
   && new File(root, "junkyard/" + d2.id + ".sav").isFile() && new File(root, "history/" + d3.id + "/fate.txt").isFile() && new File(root, "history/" + d1.id + "/cloud-copy-" + "").getParentFile().isDirectory()
   && !new File(root, "shipyard").exists() && !new File(root, "memorials_and_records").exists() && !new File(root, "storage.xml").exists());
 // a conversion that fails part way (a ship's folder can't be made: a file stands where it would go) is put back from its zip (step 24)
 String oldLook = sorted(layoutLook(root).replace("logs/\n", "").replace("logs/", ""));
 File shipyard = new File(root, "shipyard"); shipyard.mkdirs();
 File block = new File(shipyard, ShipStore.stem(d1.name, d1.id)); SafeFiles.writeText(block, "in the way", false);
 String why = null;
 try { Vault.open(saves); } catch (IOException x) { why = x.getMessage(); }
 block.delete(); shipyard.delete();
 Setup.chk("C: a conversion that fails part way says so, naming the zip (" + why + ")", why != null && why.contains("put back as it was") && why.contains("-before-6.0-"));
 String nowLook = sorted(layoutLook(root).replace("logs/\n", "").replace("logs/", "")); // the logs moved into logs/ before the zip was taken (moveLogs, 5.71): the same files
 if (!nowLook.equals(oldLook)) { List<String> o = Arrays.asList(oldLook.split("\n")), n = Arrays.asList(nowLook.split("\n")); for (String x : o) if (!n.contains(x)) System.out.println("  gone: " + x); for (String x : n) if (!o.contains(x)) System.out.println("  new:  " + x); }
 Setup.chk("C: and the fleet is put back from it, every file as it was", nowLook.equals(oldLook));
 for (File z : root.getParentFile().listFiles()) if (z.getName().startsWith(root.getName() + "-before-6.0-")) z.delete(); // the failed try's zip
 Thread.sleep(1100); // the next zip's name is its second's
 // opened again: converted
 Vault v2 = Vault.open(saves); v2.takeStock();
 File[] zips = root.getParentFile().listFiles(new FilenameFilter() { public boolean accept(File d, String n) { return n.startsWith(root.getName() + "-before-6.0-") && n.endsWith(".zip"); } });
 Setup.chk("C: converted on opening: the old files gone, a zip of the fleet as it was beside it", !new File(root, "manifest.xml").exists() && !new File(root, "ships").exists() && !new File(root, "history").exists()
   && zips != null && zips.length == 1 && zips[0].length() > 1000);
 Map<String, String> after = picture(v2);
 List<String> diff = new ArrayList<String>();
 for (Map.Entry<String, String> e : before.entrySet()) if (!e.getValue().equals(after.get(e.getKey()))) diff.add(e.getKey() + ": " + e.getValue() + " -> " + after.get(e.getKey()));
 for (String k : after.keySet()) if (!before.containsKey(k)) diff.add(k + ": new");
 for (String d : diff) System.out.println("  differs: " + d);
 Setup.chk("C: every ship as she was: her folder, her record, her versions and special copies, her side files, her log", diff.isEmpty());
 int loose = 0; for (File d : v2.shipFolders()) for (String n : new String[] {"traded.txt", "journey.txt", "museum.txt", "borrowed.txt", "voyage.txt", "final-battle.txt", "overwritten.txt", "fate.txt"}) if (new File(d, n).isFile()) loose++;
 TradeMark tm = TradeMark.of(v2, d1.id); String[] f3 = ShipStore.fate(v2.folderOfId(d3.id));
 boolean folded = false; for (EventLog.Entry e : EventLog.read(v2)) if (e.kind.equals("SHIP_FILES") && "folded".equals(e.get("what"))) folded = true;
 Setup.chk("C: each ship's side files folded into her record (5.98): her trade mark, the destroyed one's fate, none left loose, logged (" + loose + " loose)",
   loose == 0 && tm != null && tm.original.equals("Captain Ash") && f3 != null && f3[0].equals("DESTROYED") && folded);
 Setup.chk("C: the fleet as it was: the same ships (" + v.all().size() + "), the renamed one, the hull in the Junkyard, the destroyed one gone", v2.all().size() == v.all().size() && v2.byId(d1.id).name.equals("Nightjar Renamed")
   && v2.byId(d2.id).state == Ship.State.JUNKED && v2.byId(d3.id) == null);
 List<String> crewAfter = crew(v2);
 for (int i = 0; i < Math.max(crewBefore.size(), crewAfter.size()); i++) { String cb = i < crewBefore.size() ? crewBefore.get(i) : "-", ca = i < crewAfter.size() ? crewAfter.get(i) : "-"; if (!cb.equals(ca)) System.out.println("  crew differs: " + cb + "  ->  " + ca); }
 Setup.chk("C: the crew register back from crew.txt into a file each, nobody changed (" + crewAfter.size() + ")", crewAfter.equals(crewBefore) && !crewBefore.isEmpty() && !new File(root, "crew.txt").exists()
   && CrewRegister.registerFileOf(v2).isFile());
 Setup.chk("C: the same ship boarded, the hold with the same fingerprint", v2.boarded() != null && v2.boarded().id.equals(v.boarded().id) && v2.storage().hash.equals(v.storage().hash));
 Setup.chk("C: the Cargo Hold in its folder: its xml there (no pretend ship's save), its stored-systems list with it, nothing of it left at the root", v2.fileOf(v2.storage()).equals(new File(v2.cargoHoldDir(), Vault.HOLD_FILE)) && v2.fileOf(v2.storage()).isFile()
   && homeplanet.parser.HoldXml.isHold(new File(v2.cargoHoldDir(), "cargohold.xml")) && !new File(v2.cargoHoldDir(), "cargohold.sav").exists() && v2.systemsFile().getParentFile().equals(v2.cargoHoldDir()) && !new File(root, "storage.sav").exists() && !new File(root, "storage.xml").exists() && !new File(root, "storage-systems.txt").exists());
 Setup.chk("C: her kept versions read back in order, the victory copy and the cloud copy among her special copies", v2.history(v2.byId(d1.id)).size() == v.history(d1).size()
   && ShipStore.versions(v2.folderOf(v2.byId(d1.id)), true).size() == 2 && v2.kept(v2.byId(d1.id)).size() == v.kept(d1).size());
 List<EventLog.Entry> es = EventLog.read(v2); EventLog.Entry layout = null;
 for (EventLog.Entry e : es) if (e.kind.equals("LAYOUT")) layout = e;
 Setup.chk("C: the conversion is logged, with the count of ships and of the remembered, and the zip's name", layout != null && layout.num("ships", 0) == 4 && layout.num("remembered", 0) == 1 && layout.get("backup").equals(zips[0].getName())
   && es.size() >= eventsBefore + 1);
 // the path budget: the longest path under the fleet's folder, with the longest name and id, stays well under Windows' 260 with a Documents folder before it
 String longest = ""; for (String p : paths(root, "")) if (p.length() > longest.length()) longest = p;
 int budget = "memorials_and_records/ships/".length() + ShipStore.NAME_MAX + 1 + 16 + Math.max("/versions/".length() + "final-battle-20261007-065935-10.sav".length(), ("/" + CrewRegister.CREW_DIR + "/").length() + ShipStore.NAME_MAX + ".99999.xml".length());
 System.out.println("  longest path: " + longest.length() + " (" + longest + "), the budget " + budget);
 Setup.chk("P: every path under the fleet's folder is within the budget (" + budget + ")", longest.length() <= budget && budget <= 130);
 Setup.done();
}
 /** Every ship folder as a line: where she is, her record's attributes, her versions, her other files, her log's entries. */
 static Map<String, String> picture(Vault v) throws Exception {
  Map<String, String> out = new TreeMap<String, String>();
  for (File d : v.shipFolders()) {
   ShipStore.Record r = ShipStore.read(d);
   String where = d.getParentFile().equals(v.memorialDir()) ? "memorial" : d.getParentFile().getName();
   // a 5.x fleet kept nothing of a departed ship but her folder: her record is her id and name alone
   StringBuilder sb = new StringBuilder(where + " " + r.id + " " + r.name + (where.equals("memorial") ? "" : " " + r.state + " dlc=" + r.dlc + " hash=" + r.hash));
   sb.append(" save=").append(ShipStore.sav(d).isFile() ? SafeFiles.hash(ShipStore.sav(d)) : "none");
   List<String> vs = new ArrayList<String>(); for (File f : ShipStore.versions(d, false)) vs.add(f.getName());
   List<String> special = new ArrayList<String>(); for (File f : ShipStore.versions(d, true)) special.add(f.getName()); Collections.sort(special); vs.addAll(special); // the special copies by name: a zip keeps times to the second
   sb.append(" versions=").append(vs);
   List<String> side = new ArrayList<String>(); File[] fs = d.listFiles(); Arrays.sort(fs);
   for (File f : fs) if (f.isFile() && !f.getName().equals(ShipStore.xml(d).getName()) && !f.getName().equals(ShipStore.sav(d).getName())) side.add(f.getName() + ":" + f.length());
   sb.append(" files=").append(side);
   out.put(d.getName(), sb.toString());
  }
  return out;
 }
 /** Every crew member as a line: id, name, status, where their file is and what it holds. */
 static List<String> crew(Vault v) throws IOException {
  List<String> out = new ArrayList<String>();
  for (CrewRegister.Member m : CrewRegister.members(v)) { File f = CrewRegister.fileOf(v, m.id); out.add(m.id + " " + m.name + " " + m.status + " " + m.where + " " + m.served + " " + m.events.size() + " " + (f == null ? "-" : f.getParentFile().getParentFile().getName() + "/" + f.getParentFile().getName())); }
  return out;
 }
 static List<String> paths(File d, String rel) {
  List<String> out = new ArrayList<String>(); File[] fs = d.listFiles(); if (fs == null) return out;
  for (File f : fs) { String p = rel.isEmpty() ? f.getName() : rel + "/" + f.getName(); out.add(p); if (f.isDirectory()) out.addAll(paths(f, p)); }
  return out;
 }
 /** Every file under a folder with its size and fingerprint, the folders too (the zip's restore leaves none behind or out). */
 static String layoutLook(File root) throws IOException {
  List<String> out = new ArrayList<String>(); look(root, "", out); Collections.sort(out); return String.join("\n", out) + "\n";
 }
 static void look(File d, String at, List<String> out) throws IOException {
  File[] fs = d.listFiles(); if (fs == null) return;
  for (File f : fs) { String p = at + f.getName(); if (f.isDirectory()) { out.add(p + "/"); look(f, p + "/", out); } else out.add(p + " " + f.length() + " " + SafeFiles.hash(f)); }
 }
 static String sorted(String lines) { List<String> l = new ArrayList<String>(Arrays.asList(lines.split("\n"))); Collections.sort(l); return String.join("\n", l); }
}
