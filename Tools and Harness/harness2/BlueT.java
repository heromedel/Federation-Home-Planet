import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Blueprints that outlive their design: retired designs, and the blueprint backups. args: gamedir, world saves (from WorldT), work */
public class BlueT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 retired(v);
 backups(v);
 Setup.done();
}
 /** A small built design, with its built copy (what ships fly), added to the list. */
 static ShipDesign design(List<ShipDesign> all, String name) {
  ShipDesign d = ShipDesign.create(all); all.add(d);
  d.name = name;
  int[][] rooms = {{4,5,2,2},{6,5,2,2},{8,5,2,2},{6,4,2,1},{10,5,1,2}};
  for (int[] r : rooms) d.rooms.add(new ShipDesign.Room(r[0], r[1], r[2], r[3]));
  d.doors.add(d.doorFor(6,5,1)); d.doors.add(d.doorFor(8,5,1)); d.doors.add(d.doorFor(10,5,1)); d.doors.add(d.doorFor(6,5,0)); d.doors.add(d.doorFor(4,5,1));
  String[][] sys = {{"pilot","4"},{"engines","0"},{"oxygen","3"},{"shields","1"},{"weapons","2"}};
  for (String[] s : sys) { CompanionMod.Sys x = new CompanionMod.Sys(s[0]); x.room = Integer.parseInt(s[1]); x.power = CompanionMod.usualPower(s[0]); if (ShipDesign.manned(s[0])) { x.square = 0; x.dir = ShipDesign.defaultDir(s[0]); } d.systems.put(s[0], x); }
  ShipArt.adoptGameShip(d, "kestral");
  d.built = true; d.starter = true;
  CompanionMod.Loadout l = new CompanionMod.Loadout(); l.className = name + " Class"; l.shipName = "The " + name; l.crew.put("human", 2); l.weapons.add("LASER_BURST_2"); l.missiles = 5; d.loadout = l;
  ShipDesign snap = ShipDesign.copy(d); snap.snapshotOf = d.id; all.add(snap);
  return snap;
 }
 static ShipDesign copyOf(String bpId) { for (ShipDesign x : ShipDesign.load()) if (!x.isWorking() && DesignExport.bpId(x).equals(bpId)) return x; return null; }
 static boolean inMod(String bpId) { for (ShipDesign x : DesignExport.built()) if (DesignExport.bpId(x).equals(bpId)) return true; return false; }

 /** Every blueprint has a backup; a lost one a ship needs comes back from it, one no ship needs doesn't. */
 static void backups(Vault v) throws Exception {
  // a remodel of the Kestrel that a docked ship flies, and one nobody flies
  List<CompanionMod.Remodel> rs = CompanionMod.load();
  CompanionMod.Remodel flown = CompanionMod.create("PLAYER_SHIP_HARD", "Backup Test", rs); rs.add(flown);
  CompanionMod.Remodel idle = CompanionMod.create("PLAYER_SHIP_HARD", "Idle Remodel", rs); rs.add(idle);
  CompanionMod.save(rs); CompanionMod.register(CompanionMod.load());
  Ship k = null; for (Ship s : v.all()) if ("Test Kestrel".equals(s.name)) k = s;
  if (k.isBoarded()) v.dock();
  SavedGameState g = k.save(); Retrofit.switchTo(g, flown.id); v.write(k, g);
  Setup.chk("K: each remodel has a backup", new File(v.blueprintsDir(), flown.id + ".xml").isFile() && new File(v.blueprintsDir(), idle.id + ".xml").isFile());
  Setup.chk("K: the ship flies the remodel", v.usingBlueprint(flown.id).contains(k));
  // remodels.xml is lost
  CompanionMod.remodelsFile().delete();
  List<CompanionMod.Remodel> back = CompanionMod.load();
  Setup.chk("K: a lost remodel a ship flies comes back from its backup", CompanionMod.find(back, flown.id) != null);
  Setup.chk("K: one no ship flies stays gone", CompanionMod.find(back, idle.id) == null);
  CompanionMod.save(back);
  Setup.chk("K: the next save writes it back into remodels.xml", new String(SafeFiles.read(CompanionMod.remodelsFile()), "UTF-8").contains(flown.id));
  // designs.xml is lost: the retired design's built copy (from the test above) is gone with it... unless a ship needs it
  List<ShipDesign> all = ShipDesign.load();
  String bp = DesignExport.bpId(design(all, "Backup Design"));
  ShipDesign.save(all); Slipstream.writeMod(); CompanionMod.register(CompanionMod.load());
  v.adopt(Commission.build(bp, "Backup Ship", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(4)));
  ShipDesign.file().delete();
  Setup.chk("K: a lost design's built copy a ship flies comes back, retired", copyOf(bp) != null && copyOf(bp).retired && inMod(bp));
 }
 /** Deleting a design ships still fly retires it: out of the list and Commission, still in the mod, until nothing needs it. */
 static void retired(Vault v) throws Exception {
  List<ShipDesign> all = ShipDesign.load();
  String flown = DesignExport.bpId(design(all, "Retire Test")), idle = DesignExport.bpId(design(all, "Idle Test"));
  ShipDesign.save(all);
  Slipstream.writeMod(); CompanionMod.register(CompanionMod.load());
  Ship ship = v.adopt(Commission.build(flown, "Flown Ship", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(3)));
  Setup.chk("R: a ship built from the design flies its blueprint", v.usingBlueprint(flown).contains(ship));

  all = ShipDesign.load();
  String flownId = copyOf(flown).id, idleId = copyOf(idle).id;
  boolean modChanged = ShipDesign.deleteOrRetire(all, flownId, v.blueprintsInUseOrHistory());
  ShipDesign.save(all);
  ShipDesign kept = copyOf(flown);
  boolean working = false; for (ShipDesign x : ShipDesign.load()) if (x.id.equals(flownId) && x.isWorking()) working = true;
  Setup.chk("R: the flown design is retired, not deleted", kept != null && kept.retired && !working && !modChanged);
  Setup.chk("R: its blueprint stays in the mod", inMod(flown));
  all = ShipDesign.load();
  modChanged = ShipDesign.deleteOrRetire(all, idleId, v.blueprintsInUseOrHistory());
  ShipDesign.save(all);
  Setup.chk("R: a design no ship flies is deleted outright", copyOf(idle) == null && modChanged && !inMod(idle));

  // her ship is destroyed: her last save stays in her history, which still names the blueprint
  v.remove(ship, "DESTROY");
  Setup.chk("R: a destroyed ship's kept records still count", v.usingBlueprint(flown).isEmpty() && v.blueprintsInUseOrHistory().contains(flown));
  SafeFiles.deleteTree(v.historyOf(ship));
  Setup.chk("R: once nothing names it, it's free to clean up", !v.blueprintsInUseOrHistory().contains(flown));
  // reload keeps the flag
  Setup.chk("R: the retired flag survives a reload", copyOf(flown) != null && copyOf(flown).retired);
 }
}
