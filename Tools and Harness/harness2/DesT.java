import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Design Ship's back end on the test world: checks, export, art files, commissioning. args: gamedir, world saves (from WorldT), work */
public class DesT {
 static List<homeplanet.ui.NumberRow> rows(java.awt.Container c) { List<homeplanet.ui.NumberRow> out = new ArrayList<homeplanet.ui.NumberRow>(); for (java.awt.Component x : c.getComponents()) { if (x instanceof homeplanet.ui.NumberRow) out.add((homeplanet.ui.NumberRow) x); if (x instanceof java.awt.Container) out.addAll(rows((java.awt.Container) x)); } return out; }
 public static void main(String[] a) throws Exception {
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
   Setup.chk("placed where the game has her: the grid's middle is the game's centre, so her offsets (0, 2) put her at column 4, row 4: " + gb[0] + ", " + gb[1], gb[0] == DesignExport.ORIGIN_COL && gb[1] == DesignExport.ORIGIN_ROW + 2);
   Setup.chk("loadout and numbers taken", g.loadout != null && g.loadout.weapons.size() == 2 && g.hull == 30 && g.reactor == 8);
   ShipChecks.Report r = ShipChecks.check(g, ShipChecks.Context.DESIGN, null, null);
   Setup.chk("the copy is sound: " + r.problems, r.problems.isEmpty());
   int ax = g.artX, doors = g.doors.size();
   Setup.chk("shift left four times then a fifth fails at the edge", g.shift(-1, 0) && g.shift(-1, 0) && g.shift(-1, 0) && g.shift(-1, 0) && !g.shift(-1, 0) && g.bounds()[0] == 0 && g.artX == ax - 140);
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
 // F: FTL's System Limit: a design set to start with more than 8 systems is warned, not refused
 { ShipDesign c = ShipDesign.create(back);
   c.name = "Crowded copy";
   ShipDesign.fromGameShip(c, "PLAYER_SHIP_HARD");
   for (String id : new String[] {"drones", "teleporter", "cloaking", "hacking"}) c.notAtStart.remove(id);
   ShipChecks.Report r = ShipChecks.check(c, ShipChecks.Context.DESIGN, null, null);
   Setup.chk("starting with 9 systems: a warning of FTL's System Limit, not a problem: " + r.warnings, r.warnings.toString().contains("starts with 9 systems") && r.problems.isEmpty());
   c.notAtStart.add("hacking");
   Setup.chk("with 8: no such warning", !ShipChecks.check(c, ShipChecks.Context.DESIGN, null, null).warnings.toString().contains("System Limit"));
   c.notAtStart.remove("hacking"); c.notAtStart.remove("clonebay");
   Setup.chk("a Medbay and Clone Bay both ticked count once (9, not 10)", ShipChecks.check(c, ShipChecks.Context.DESIGN, null, null).warnings.toString().contains("starts with 9 systems"));
   Setup.chk("a remodel isn't warned (her systems are already aboard)", !ShipChecks.check(c, ShipChecks.Context.REMODEL, null, null).warnings.toString().contains("System Limit"));
 }
 // G: a Medbay and a Clone Bay take each other's place: Commission builds the Clone Bay alone, and Design Ship ticks one or the other
 { net.blerf.ftl.xml.ShipBlueprint.SystemList.SystemRoom cb = DataManager.get().getShip("PLAYER_SHIP_HARD").getSystemList().getSystemRoom(SystemType.CLONEBAY)[0];
   Boolean was = cb.getStart();
   cb.setStart(true); // the Kestrel A as a design ticked with both
   try {
     ShipState both = Commission.build("PLAYER_SHIP_HARD", "Both Bays", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(3)).getPlayerShip();
     SystemState mb = both.getSystem(SystemType.MEDBAY), cl = both.getSystem(SystemType.CLONEBAY);
     Setup.chk("both bays ticked: she's commissioned with the Clone Bay alone", (mb == null || mb.getCapacity() == 0) && cl != null && cl.getCapacity() > 0 && SaveHelper.systemCount(both) == 5);
   } finally { cb.setStart(was); }
   ShipState kestrel = Commission.build("PLAYER_SHIP_HARD", "Medbay Kestrel", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(3)).getPlayerShip();
   Setup.chk("a Kestrel A still starts with her Medbay", kestrel.getSystem(SystemType.MEDBAY).getCapacity() > 0 && kestrel.getSystem(SystemType.CLONEBAY).getCapacity() == 0);
   // the Loadout step (headless: the panel alone, no window) on a design with both bays placed and ticked
   ShipDesign bays = ShipDesign.copy(made[0]);
   for (String id : new String[] {"medbay", "clonebay"}) { CompanionMod.Sys s = new CompanionMod.Sys(id); s.room = id.equals("medbay") ? 3 : 4; s.power = 1; bays.systems.put(id, s); }
   bays.notAtStart.remove("medbay"); bays.notAtStart.remove("clonebay");
   homeplanet.ui.LayoutEditor.Host quiet = new homeplanet.ui.LayoutEditor.Host() { public void say(String m) { } public void changed() { } public String cannotTakeOff(String id) { return null; } public CompanionMod.Sys original(String id) { return null; } };
   homeplanet.ui.LoadoutPanel lp = new homeplanet.ui.LoadoutPanel(bays, quiet);
   homeplanet.ui.NumberRow med = null, clo = null;
   for (homeplanet.ui.NumberRow n : rows(lp)) { if (n.name().equals("Medbay")) med = n; if (n.name().equals("Clone Bay")) clo = n; }
   boolean opened = med != null && clo != null && !med.tick().isSelected() && clo.tick().isSelected() && bays.notAtStart.contains("medbay") && !bays.notAtStart.contains("clonebay");
   med.tick().doClick(); boolean onlyMed = med.tick().isSelected() && !clo.tick().isSelected() && !bays.notAtStart.contains("medbay") && bays.notAtStart.contains("clonebay");
   clo.tick().doClick(); boolean onlyClone = clo.tick().isSelected() && !med.tick().isSelected() && bays.notAtStart.contains("medbay") && !bays.notAtStart.contains("clonebay");
   clo.tick().doClick(); boolean neither = !clo.tick().isSelected() && !med.tick().isSelected() && bays.notAtStart.contains("medbay") && bays.notAtStart.contains("clonebay");
   Setup.chk("Design Ship's Loadout step: a design with both ticked opens with the Clone Bay alone; ticking one unticks the other; neither is fine", opened && onlyMed && onlyClone && neither);
 }
 // W: the floor and the art (Plan W): a floor the wrong size, a floor drawn from the rooms, missing art, the visible centre
 { ShipDesign f = ShipDesign.copy(made[1]);
   java.awt.image.BufferedImage hull = ShipArt.load(f.art, "");
   java.awt.image.BufferedImage small = new java.awt.image.BufferedImage(hull.getWidth() / 2, hull.getHeight() / 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
   f.floor = ShipArt.importImage(small, f.id, "floor"); f.floorX = 0; f.floorY = 0;
   List<String> p = ShipChecks.check(f, ShipChecks.Context.DESIGN, null, null).warnings;
   Setup.chk("W: a smaller floor inside the hull is fine (the game's floors are): " + p, !p.toString().contains("sticks out"));
   f.floorX = hull.getWidth() - 10;
   p = ShipChecks.check(f, ShipChecks.Context.DESIGN, null, null).warnings;
   Setup.chk("W: a floor sticking out of the hull is a warning: " + p, p.toString().contains("sticks out"));
   f.floorX = 0;
   p = ShipChecks.check(f, ShipChecks.Context.DESIGN, null, null).problems;
   java.awt.image.BufferedImage drawn = ShipArt.floorFromRooms(f, hull);
   f.floor = ShipArt.importImage(drawn, f.id, "floor");
   p = ShipChecks.check(f, ShipChecks.Context.DESIGN, null, null).problems;
   int grey = 0, clear = 0; for (int y = 0; y < drawn.getHeight(); y += 3) for (int x = 0; x < drawn.getWidth(); x += 3) { if (((drawn.getRGB(x, y) >>> 24) & 0xFF) > 0) grey++; else clear++; }
   ShipDesign.Room r0 = f.rooms.get(0); int rx = r0.x * SaveHelper.SQUARE_SIZE - f.artX + 5, ry = r0.y * SaveHelper.SQUARE_SIZE - f.artY + 5;
   boolean roomClear = rx >= 0 && ry >= 0 && rx < drawn.getWidth() && ry < drawn.getHeight() && ((drawn.getRGB(rx, ry) >>> 24) & 0xFF) == 0;
   boolean wallGrey = rx - 9 >= 0 && ((drawn.getRGB(rx - 9, ry) >>> 24) & 0xFF) > 0;
   Setup.chk("W: a floor drawn from the rooms is the hull's size, walls round the rooms (" + grey + " drawn, " + clear + " clear), the rooms left clear, and passes the checks: " + p,
     drawn.getWidth() == hull.getWidth() && drawn.getHeight() == hull.getHeight() && grey > 0 && clear > grey && roomClear && wallGrey && p.isEmpty());
   Setup.chk("W: the visible box of a picture with a clear margin is smaller than the picture", ShipArt.opaqueBounds(hull).width < hull.getWidth() || ShipArt.opaqueBounds(hull).height < hull.getHeight());
   // her hull picture gone: a warning, not a problem, and the Kestrel's picture stands in so the mod still has her
   ShipDesign m = ShipDesign.copy(made[1]); m.floor = ""; m.art = "file:art/nowhere-" + m.id + ".png";
   ShipChecks.Report rep = ShipChecks.check(m, ShipChecks.Context.DESIGN, null, null);
   Setup.chk("W: missing hull art is a warning (" + rep.warnings + "), not a problem (" + rep.problems + ")", rep.warnings.toString().contains("stands in") && !rep.problems.toString().contains("missing"));
   Map<String, byte[]> imgs = DesignExport.images(m);
   Setup.chk("W: her pictures are still written, the Kestrel's standing in", imgs.keySet().toString().contains("_base.png"));
   // a floor drawn from the rooms as a standing choice: drawn fresh each time, so it follows the rooms; exported; kept through a flip
   ShipDesign q = ShipDesign.copy(made[1]); q.floor = ShipDesign.FLOOR_ROOMS; q.floorX = 7; q.floorY = 7;
   java.awt.image.BufferedImage f1 = ShipArt.floorOf(q);
   p = ShipChecks.check(q, ShipChecks.Context.DESIGN, null, null).warnings;
   Setup.chk("W: 'rooms' as the floor: a hull-sized floor drawn now, no warning about it: " + p, f1 != null && f1.getWidth() == hull.getWidth() && f1.getHeight() == hull.getHeight() && !p.toString().contains("floor"));
   q.rooms.get(0).x += 1;
   java.awt.image.BufferedImage f2 = ShipArt.floorOf(q);
   boolean differs = false; for (int y = 0; y < f1.getHeight() && !differs; y += 2) for (int x = 0; x < f1.getWidth(); x += 2) if (f1.getRGB(x, y) != f2.getRGB(x, y)) { differs = true; break; }
   Setup.chk("W: a room moved: the floor follows (drawn again, not a stored picture)", differs && q.floorFromRooms());
   Setup.chk("W: the mod carries her drawn floor", DesignExport.images(q).keySet().toString().contains("_floor.png"));
   Setup.chk("W: a flip keeps the floor drawn from the rooms", ShipArt.flipVertically(q) && q.floorFromRooms());
 }
 // X: the weapon slots are hers (Plan X); the vanilla maxes come from the game; past them is a note, never a refusal
 { ShipDesign x = ShipDesign.copy(made[1]);
   Setup.chk("X: vanilla max read from the game's ships: 4 weapon slots, 3 drone slots, 30 hull, reactor 11 (the Mantis B's), 28 missiles, 25 drone parts; Shields to 8, Piloting to 3: "
     + VanillaMax.weaponSlots() + "/" + VanillaMax.droneSlots() + "/" + VanillaMax.hull() + "/" + VanillaMax.reactor() + "/" + VanillaMax.missiles() + "/" + VanillaMax.droneParts(),
     VanillaMax.weaponSlots() == 4 && VanillaMax.droneSlots() == 3 && VanillaMax.hull() == 30 && VanillaMax.reactor() == 11 && VanillaMax.missiles() == 28 && VanillaMax.droneParts() == 25
     && VanillaMax.system("shields") == 8 && VanillaMax.system("pilot") == 3);
   Setup.chk("X: a design from before (no weaponSlots on file) counts her mounts: " + x.slotsFromMounts(), x.slotsFromMounts() == 2);
   x.weaponSlots = 6; x.droneSlots = 4; x.systems.get("shields").power = 9;
   Setup.chk("X: her blueprint carries the slots she set", DesignExport.blueprintText(x).contains("<weaponSlots>6</weaponSlots>") && DesignExport.blueprintText(x).contains("<droneSlots>4</droneSlots>"));
   ShipChecks.Report xr = ShipChecks.check(x, ShipChecks.Context.DESIGN, null, null);
   Setup.chk("X: 6 slots on 2 mounts is a note (nowhere to draw), past vanilla is a note, nothing stops her: " + xr.problems + " / " + xr.warnings,
     xr.problems.isEmpty() && xr.warnings.toString().contains("nowhere to draw") && xr.warnings.toString().contains("Past vanilla") && xr.warnings.toString().contains("6 weapon slots") && xr.warnings.toString().contains("Shields at level 9"));
   x.weaponSlots = 2; x.droneSlots = 2; x.systems.get("shields").power = 2;
   xr = ShipChecks.check(x, ShipChecks.Context.DESIGN, null, null);
   Setup.chk("X: within vanilla: no such notes: " + xr.warnings, !xr.warnings.toString().contains("Past vanilla") && !xr.warnings.toString().contains("nowhere to draw"));
   x.loadout.crew.put("human", 9);
   Setup.chk("X: 9 crew is a problem (FTL's ships hold 8)", ShipChecks.check(x, ShipChecks.Context.DESIGN, null, null).problems.toString().contains("hold 8"));
   x.loadout.crew.clear();
   Setup.chk("X: no crew set is a note (she'd start with one human)", ShipChecks.check(x, ShipChecks.Context.DESIGN, null, null).warnings.toString().contains("one human"));
   // on file and back, with the attribute; and an older file without it
   ShipDesign y = ShipDesign.copy(made[1]); y.weaponSlots = 3; List<ShipDesign> keep = ShipDesign.load(); keep.add(y); ShipDesign.save(keep);
   ShipDesign back2 = null; for (ShipDesign s : ShipDesign.load()) if (s.id.equals(y.id) && s.snapshotOf == null && s.weaponSlots == 3) back2 = s;
   Setup.chk("X: the slots are saved with the design and read back", back2 != null);
   String xml = new String(java.nio.file.Files.readAllBytes(ShipDesign.file().toPath()), "UTF-8").replaceAll(" weaponSlots=\"\\d+\"", "");
   SafeFiles.writeText(ShipDesign.file(), xml, true);
   back2 = null; for (ShipDesign s : ShipDesign.load()) if (s.id.equals(y.id) && s.snapshotOf == null) back2 = s;
   Setup.chk("X: an older file without the attribute: one slot per mount, as it was counted then", back2 != null && back2.weaponSlots == back2.slotsFromMounts());
   // the grid's middle is the game's centre: where the rooms sit round it is her screen offset
   ShipDesign k = ShipDesign.create(all); ShipDesign.fromGameShip(k, "PLAYER_SHIP_HARD");
   int[] ko = DesignExport.offsets(k);
   Setup.chk("X: a Kestrel copied onto the grid lands where the game has her and gets the game's own offsets back (0, 2): " + ko[0] + ", " + ko[1], ko[0] == 0 && ko[1] == 2);
   ShipDesign m = ShipDesign.create(all); ShipDesign.fromGameShip(m, "PLAYER_SHIP_MANTIS");
   int[] mo = DesignExport.offsets(m);
   Setup.chk("X: the Mantis too (3, 0): " + mo[0] + ", " + mo[1], mo[0] == 3 && mo[1] == 0);
   for (ShipDesign.Room r : k.rooms) r.x += 2;
   Setup.chk("X: her rooms moved two squares right: offset 2 (she sits further right in the game)", DesignExport.offsets(k)[0] == 2);
   for (ShipDesign.Room r : k.rooms) r.x -= 5;
   Setup.chk("X: moved past the game's edge: offset 0, never negative", DesignExport.offsets(k)[0] == 0);
   // Y: her pictures turned and mirrored: the mounts keep their spots on the picture, the middle stays, the rooms don't move
   ShipDesign t = ShipDesign.copy(made[1]); t.ellipseW = 300; t.ellipseH = 200; t.ellipseX = 10; t.ellipseY = -4;
   java.awt.image.BufferedImage th = ShipArt.load(t.art, ""); int tw = th.getWidth(), tth = th.getHeight();
   double cx = t.artX + tw / 2.0, cy = t.artY + tth / 2.0; int r0x = t.rooms.get(0).x, m0x = t.mounts.get(0).x, m0y = t.mounts.get(0).y;
   Setup.chk("Y: rotated: the picture is turned (" + tw + "x" + tth + " to " + ShipArt.load(t.art, "").getHeight() + "x" + ShipArt.load(t.art, "").getWidth() + ")", ShipArt.rotate(t) && ShipArt.load(t.art, "").getWidth() == tth && ShipArt.load(t.art, "").getHeight() == tw);
   Setup.chk("Y: the mount turned with it (" + m0x + "," + m0y + " to " + t.mounts.get(0).x + "," + t.mounts.get(0).y + "), the shield's axes swapped, the middle kept, the rooms untouched",
     t.mounts.get(0).x == tth - m0y && t.mounts.get(0).y == m0x && t.ellipseW == 200 && t.ellipseH == 300 && Math.abs(t.artX + tth / 2.0 - cx) <= 1 && Math.abs(t.artY + tw / 2.0 - cy) <= 1 && t.rooms.get(0).x == r0x);
   for (int i = 0; i < 3; i++) ShipArt.rotate(t);
   Setup.chk("Y: four turns bring the mount round (" + t.mounts.get(0).x + "," + t.mounts.get(0).y + ")", t.mounts.get(0).x == m0x && t.mounts.get(0).y == m0y && ShipArt.load(t.art, "").getWidth() == tw);
   Setup.chk("Y: mirrored: the mount's x reflected", ShipArt.flipHorizontally(t) && t.mounts.get(0).x == tw - m0x && t.ellipseX == -10);
   Setup.chk("Y: mirrored again: back", ShipArt.flipHorizontally(t) && t.mounts.get(0).x == m0x && t.ellipseX == 10);
 }
 homeless();
 plan();
 Setup.done();
} /** An Undo Retrofit takes off a system her original model has no room for: artillery on a remodelled Kestrel. */
 static void homeless() throws Exception {
  SavedGameState g = Commission.build("PLAYER_SHIP_HARD", "Gunboat", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(1));
  Retrofit.apply(g, false);
  ShipState s = g.getPlayerShip();
  Setup.chk("U: a retrofitted Kestrel has nothing her model can't hold", Retrofit.homeless(s).isEmpty());
  SystemState art = s.getSystem(SystemType.ARTILLERY); if (art == null) { art = new SystemState(SystemType.ARTILLERY); s.addSystem(art); } art.setCapacity(1); art.setPower(1); // (a built ship carries every system's state, at 0)
  Setup.chk("U: with artillery installed, the artillery is what her original model can't hold", Retrofit.homeless(s).size() == 1 && Retrofit.homeless(s).get(0) == SystemType.ARTILLERY);
  SystemState cl = s.getSystem(SystemType.CLOAKING); if (cl == null) { cl = new SystemState(SystemType.CLOAKING); s.addSystem(cl); } cl.setCapacity(1); cl.setPower(1);
  Setup.chk("U: cloaking has a room on every model: not taken off", Retrofit.homeless(s).size() == 1);
 }
 /** The Refit tab's floor plan: a ship's rooms and system rooms from her blueprint, for a game ship and a design. */
 static void plan() throws Exception {
  homeplanet.ui.ShipPlanView v = new homeplanet.ui.ShipPlanView();
  List<String> have = new ArrayList<String>(); have.add("shields"); have.add("pilot");
  v.show("PLAYER_SHIP_HARD", have);
  Setup.chk("V: a Kestrel's plan knows her rooms: shields yes, artillery no", v.hasRoom("shields") && v.hasRoom("cloaking") && !v.hasRoom("artillery"));
  v.show("PLAYER_SHIP_FED", have);
  Setup.chk("V: a Federation Cruiser's plan has an artillery room", v.hasRoom("artillery"));
  v.show("NO_SUCH_SHIP", have);
  Setup.chk("V: an unknown blueprint shows nothing, quietly", !v.hasRoom("shields"));
  v.light("shields"); v.light(null);
 }
}
