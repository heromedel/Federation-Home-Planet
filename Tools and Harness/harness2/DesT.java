import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Design Ship's back end on a migrated world: checks, export, art files, commissioning. args: gamedir, migratedSaves, work */
public class DesT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 // a hull picture of our own, from the game's files
 File png = new File(work, "stealth.png");
 { InputStream in = DataManager.get().getResourceInputStream("img/ship/stealth_base.png"); java.nio.file.Files.copy(in, png.toPath()); in.close(); }
 List<ShipDesign> all = ShipDesign.load(); int had = all.size();
 List<String> names = new ArrayList<String>(); for (ShipDesign x : all) names.add(x.name);
 ShipDesign[] made = new ShipDesign[2];
 for (int k = 0; k < 2; k++) {
  ShipDesign d = ShipDesign.create(all); all.add(d); made[k] = d;
  d.name = k == 0 ? "Hawk Test" : "Shadow Test";
  int[][] rooms = {{4,5,2,2},{6,5,2,2},{8,5,2,2},{6,4,2,1},{10,5,1,2}};
  for (int[] r : rooms) d.rooms.add(new ShipDesign.Room(r[0], r[1], r[2], r[3]));
  d.doors.add(d.doorFor(6,5,1)); d.doors.add(d.doorFor(8,5,1)); d.doors.add(d.doorFor(10,5,1)); d.doors.add(d.doorFor(6,5,0)); d.doors.add(d.doorFor(4,5,1));
  String[][] sys = {{"pilot","4"},{"engines","0"},{"oxygen","3"},{"shields","1"},{"weapons","2"}};
  for (String[] s : sys) { CompanionMod.Sys x = new CompanionMod.Sys(s[0]); x.room = Integer.parseInt(s[1]); x.power = CompanionMod.usualPower(s[0]); if (ShipDesign.manned(s[0])) { x.square = 0; x.dir = ShipDesign.defaultDir(s[0]); } d.systems.put(s[0], x); }
  if (k == 0) ShipArt.adoptGameShip(d, "kestral");
  else { d.art = ShipArt.importFile(png, d.id, "base"); d.artX = 4*35 - 80; d.artY = 5*35 - 150; d.mounts.add(new ShipDesign.Mount(400, 120)); d.mounts.add(new ShipDesign.Mount(400, 320)); }
  d.built = true; d.starter = true;
  d.notAtStart.add("shields");
  CompanionMod.Loadout l = new CompanionMod.Loadout(); l.className = d.name + " Class"; l.shipName = "The " + d.name; l.crew.put("human", 2); l.crew.put("engi", 1); l.weapons.add("LASER_BURST_2"); l.missiles = 5; l.droneParts = 1; d.loadout = l;
  ShipDesign snap = ShipDesign.copy(d); snap.snapshotOf = d.id; all.add(snap); // Build takes the copy the mod is made from
  ShipChecks.Report r = ShipChecks.check(d, ShipChecks.Context.DESIGN, null, names);
  Setup.chk(d.name + " is sound: " + r.problems, r.problems.isEmpty());
  Setup.chk(d.name + " has nothing to warn about: " + r.warnings, r.warnings.isEmpty());
 }
 // the checker: names, artillery, doors
 { ShipDesign d = ShipDesign.copy(made[1]); d.name = "hawk test";
   Setup.chk("duplicate name (any case) is a problem", ShipChecks.check(d, ShipChecks.Context.DESIGN, null, Arrays.asList("Hawk Test")).problems.toString().contains("already called"));
   d.name = "Other"; d.rooms.add(new ShipDesign.Room(8, 4, 2, 1)); CompanionMod.Sys art = new CompanionMod.Sys("artillery"); art.room = 5; art.power = 1; d.systems.put("artillery", art);
   List<String> p = ShipChecks.check(d, ShipChecks.Context.DESIGN, null, null).problems;
   Setup.chk("artillery without weapon and mount: two problems " + p, p.size() == 2);
   art.weapon = "ARTILLERY_FED"; ShipDesign.Mount m = new ShipDesign.Mount(300, 200); m.artillery = true; d.mounts.add(m);
   Setup.chk("artillery with both: sound", ShipChecks.check(d, ShipChecks.Context.DESIGN, null, null).problems.isEmpty());
   d.removeSystem("artillery");
   boolean anyArt = false; for (ShipDesign.Mount x : d.mounts) if (x.artillery) anyArt = true;
   Setup.chk("removing artillery removes its mount", !anyArt && d.mounts.size() == 2);
   ShipDesign fed = new ShipDesign(); ShipArt.adoptGameShip(fed, "fed_cruiser");
   int fa = 0; for (ShipDesign.Mount x : fed.mounts) if (x.artillery) fa++;
   Setup.chk("game mounts: no artillery mount without the system", fa == 0);
   fed.systems.put("artillery", art); ShipArt.adoptGameShip(fed, "fed_cruiser");
   fa = 0; for (ShipDesign.Mount x : fed.mounts) if (x.artillery) fa++;
   Setup.chk("game mounts: the artillery mount with the system", fa == 1);
   Setup.chk("doors equal either way round", new CompanionMod.Door(1,2,3,1,1).equals(new CompanionMod.Door(1,2,1,3,1)) && !new CompanionMod.Door(1,2,3,1,1).equals(new CompanionMod.Door(1,2,3,2,1)));
   ShipDesign mv = ShipDesign.copy(made[1]); int doors = mv.doors.size();
   mv.moveRoom(4, 12, 5); // room 4 away from room 2: their door goes
   Setup.chk("moving a room away drops the door to it", mv.doors.size() == doors - 1);
   mv.moveRoom(4, 10, 5);
   Setup.chk("moving it back doesn't bring the door back by itself", mv.doors.size() == doors - 1);
   p = ShipChecks.check(mv, ShipChecks.Context.DESIGN, null, null).warnings;
   Setup.chk("a cut-off room is a warning: " + p, p.toString().contains("no door to the rest"));
 }
 // the element writer: start flags
 String bp = DesignExport.blueprintText(made[0]);
 Setup.chk("shields start=false, pilot start=true", bp.contains("<shields power=\"2\" room=\"1\" start=\"false\"") && bp.contains("<pilot power=\"1\" room=\"4\" start=\"true\""));
 Setup.chk("remodel elements start=false", CompanionMod.element(made[0].systems.get("pilot"), false).contains("start=\"false\""));
 // save, reload
 ShipDesign.save(all);
 List<ShipDesign> back = ShipDesign.load();
 Setup.chk("reloaded " + back.size(), back.size() == had + 4 && ShipDesign.xmlOf(all.get(had + 2)).equals(ShipDesign.xmlOf(back.get(had + 2))));
 ShipDesign b = back.get(had + 2);
 Setup.chk("own art exported", DesignExport.images(b).keySet().toString().contains("_base.png"));
 Setup.chk("game art needs no pictures", DesignExport.images(back.get(had)).isEmpty());
 Setup.chk("the mod exports the built copies only", DesignExport.built().size() >= 2 && !DesignExport.built().get(0).isWorking());
 // art files: every import is a new file; the sweep keeps what's used
 String again = ShipArt.importFile(png, b.id, "base");
 Setup.chk("a second import is a new file", !again.equals(b.art) && ShipArt.file(again).isFile() && ShipArt.file(b.art).isFile());
 int gone = ShipArt.sweep();
 Setup.chk("sweep removed the orphan only (" + gone + ")", gone == 1 && !ShipArt.file(again).isFile() && ShipArt.file(b.art).isFile());
 // into the mod and commissioned
 File mod = Slipstream.writeMod();
 Setup.chk("mod written", mod != null && mod.isFile());
 CompanionMod.register(CompanionMod.load());
 for (ShipDesign d : back) {
  if (!d.isWorking() || !d.built) continue;
  String id = DesignExport.bpId(d);
  SavedGameState g = Commission.build(id, "Built " + d.name, net.blerf.ftl.constants.Difficulty.NORMAL, new Random(2));
  File f = new File(work, d.id + ".sav"); SaveHelper.writeSavedGame(f, g); SavedGameState rb = HomePlanet.savedGameParser.readSavedGame(f);
  Setup.chk(id + " commissioned: rooms " + rb.getPlayerShip().getRoomList().size() + " doors " + rb.getPlayerShip().getDoorMap().size() + " crew " + rb.getPlayerShip().getCrewList().size(),
   rb.getPlayerShip().getRoomList().size() == d.rooms.size() && rb.getPlayerShip().getDoorMap().size() == d.doors.size());
  if (d == back.get(had)) Setup.chk("commissioned without shields (start=false)", rb.getPlayerShip().getSystem(SystemType.SHIELDS).getCapacity() == 0);
 }
 // E: a design from a game ship, moved and flipped
 { ShipDesign g = ShipDesign.create(back);
   g.name = "Kestrel copy";
   Setup.chk("copied the Kestrel", ShipDesign.fromGameShip(g, "PLAYER_SHIP_HARD") && g.rooms.size() == 17 && g.systems.containsKey("pilot") && g.art.equals("game:kestral") && g.doors.size() > 10);
   int[] gb = g.bounds();
   Setup.chk("placed two squares in", gb[0] == 2 && gb[1] == 2);
   Setup.chk("loadout and numbers taken", g.loadout != null && g.loadout.weapons.size() == 2 && g.hull == 30 && g.reactor == 8);
   ShipChecks.Report r = ShipChecks.check(g, ShipChecks.Context.DESIGN, null, null);
   Setup.chk("the copy is sound: " + r.problems, r.problems.isEmpty());
   int ax = g.artX, doors = g.doors.size();
   Setup.chk("shift left twice then a third fails at the edge", g.shift(-1, 0) && g.shift(-1, 0) && !g.shift(-1, 0) && g.bounds()[0] == 0 && g.artX == ax - 70);
   // flipping keeps every door on a wall and every station in its room
   String before = ShipDesign.editKey(g);
   ShipDesign f = ShipDesign.copy(g);
   java.awt.image.BufferedImage base = ShipArt.load(f.art, "_base");
   Setup.chk("art flipped into own files", ShipArt.flipVertically(f) && f.art.startsWith("file:") && ShipArt.file(f.art).isFile() && f.floor.startsWith("file:"));
   f.flipVertically(base.getHeight());
   List<CompanionMod.Door> was = new ArrayList<CompanionMod.Door>(f.doors); f.refreshDoors();
   Setup.chk("flipped doors all still on walls between the same rooms", f.doors.size() == doors && new HashSet<CompanionMod.Door>(was).equals(new HashSet<CompanionMod.Door>(f.doors)));
   boolean ok = true; for (CompanionMod.Sys x : f.systems.values()) { ShipDesign.Room rm = f.rooms.get(x.room); if (x.square != null && (x.square < 0 || x.square >= rm.w * rm.h)) ok = false; }
   Setup.chk("flipped stations inside their rooms: " + ShipChecks.check(f, ShipChecks.Context.DESIGN, null, null).problems, ok && ShipChecks.check(f, ShipChecks.Context.DESIGN, null, null).problems.isEmpty());
   int[] fb = f.bounds();
   Setup.chk("flip keeps the ship's box", fb[0] == g.bounds()[0] && fb[1] == g.bounds()[1] && fb[3] == g.bounds()[3]);
   ShipDesign ff = ShipDesign.copy(f); ff.flipVertically(base.getHeight());
   String[] twice = ShipDesign.editKey(ff).split("\\|"), once = ShipDesign.editKey(g).split("\\|");
   Setup.chk("flipping twice gives the rooms, doors and systems back", twice[7].equals(once[7]) && twice[8].equals(once[8]) && twice[9].equals(once[9]));
   Setup.chk("preview has the three files", DesignExport.preview(g).contains("== data/") && DesignExport.preview(g).contains("<shipBlueprint"));
 }
 Setup.done();
}}
