import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.SavedGameState; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Immersive Mode's own fleet: switching keeps both fleets whole, designs are shared, the player's rules come back. args: gamedir, world saves (from WorldT), work */
public class FleetT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 fleets(v);
 detection();
 rules();
 endCareer();
 Setup.done();
}
 /** Ending the Immersive career: the folder zipped into old-immersive-careers, then gone; the normal fleet untouched. */
 static void endCareer() throws Exception {
  Vault v = Vault.get();
  if (!v.immersive) v = Vault.switchFleet(true);
  if (!Career.started(v.root)) Career.start(false, false);
  File im = v.root;
  int files = 0; for (File f : listAll(im)) files++;
  Vault n = Vault.switchFleet(false);
  int normalShips = n.all().size();
  File zip = Vault.endImmersiveCareer();
  java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip); int zipped = z.size(); boolean hasCareer = z.getEntry("career.txt") != null; z.close();
  Setup.chk("E: ending the career keeps the whole of it, zipped, in old-immersive-careers", zip.getParentFile().getName().equals(Vault.OLD_CAREERS) && zipped == files && hasCareer);
  Setup.chk("E: then its folder is gone, and the normal fleet untouched", !im.exists() && Vault.get().all().size() == normalShips && !Vault.get().immersive);
  Vault again = Vault.switchFleet(true);
  Setup.chk("E: entering again: a new career, an empty shipyard", !Career.started(again.root) && again.shipyardEmpty());
  Vault.switchFleet(false);
 }
 static List<File> listAll(File d) { List<File> out = new ArrayList<File>(); File[] fs = d.listFiles(); if (fs != null) for (File f : fs) { if (f.isDirectory()) out.addAll(listAll(f)); else out.add(f); } return out; }
 static String continueName(Vault v) throws Exception { return HomePlanet.savedGameParser.readSavedGame(v.continueFile()).getPlayerShipName(); }
 static void fleets(Vault v) throws Exception {
  if (v.boarded() == null) v.board(v.docked().get(0));
  String normalBoarded = v.boarded().name; int normalShips = v.all().size();
  File designs = ShipDesign.file();
  Vault im = Vault.switchFleet(true);
  Setup.chk("V: the Immersive fleet is its own folder, and starts empty", im.immersive && im.root.getName().equals(Vault.IMMERSIVE_FOLDER) && im.shipyardEmpty() && !im.continueFile().exists());
  Setup.chk("V: designs are shared", ShipDesign.file().equals(designs) && im.artDir().getParentFile().equals(im.shared));
  Ship n = im.adopt(Commission.build("PLAYER_SHIP_STEALTH", "Immersive Stealth", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(5)));
  im.board(n);
  Vault back = Vault.switchFleet(false);
  Setup.chk("V: back in the normal fleet: its ship is boarded again, and all its ships are there", !back.immersive && normalBoarded.equals(continueName(back)) && back.boarded() != null && back.all().size() == normalShips);
  Setup.chk("V: the Immersive ship waits in her own fleet", new File(back.otherRoot(), "ships/" + n.id + ".sav").isFile());
  Setup.chk("V: the other fleet's ships count as flying their blueprints", back.otherFleetBlueprints().containsAll(nz(Retrofit.blueprintIds(new File(back.otherRoot(), "ships/" + n.id + ".sav")))));
  Vault again = Vault.switchFleet(true);
  Setup.chk("V: and in Immersive again, she's boarded again", "Immersive Stealth".equals(continueName(again)) && again.boarded() != null && again.all().size() >= 1);
  Setup.chk("V: the normal fleet's boarded ship was docked, not lost", new File(again.otherRoot(), "parked-boarded.txt").isFile());
  Vault.switchFleet(false);
 }
 static List<String> nz(List<String> l) { return l == null ? new ArrayList<String>() : l; }
 static void rules() {
  HomePlanet.immersiveMode = false;
  HomePlanet.storeRequirement = false; HomePlanet.commissionCosts = false; HomePlanet.commissionPercent = 50; HomePlanet.unlockFreeShips = false;
  HomePlanet.immersiveMode = true; HomePlanet.applyImmersive();
  Setup.chk("R: Immersive Mode sets its rules, the free ship per unlock too", HomePlanet.storeRequirement && HomePlanet.commissionCosts && HomePlanet.commissionPercent == 100 && HomePlanet.unlockFreeShips);
  HomePlanet.Rules own = HomePlanet.normalRules();
  Setup.chk("R: the player's own rules are kept apart", !own.store && !own.costs && own.percent == 50 && !own.unlockFree);
  HomePlanet.leaveImmersive();
  Setup.chk("R: and come back when it's turned off", !HomePlanet.immersiveMode && !HomePlanet.storeRequirement && !HomePlanet.commissionCosts && HomePlanet.commissionPercent == 50);
 }
 static SavedGameState read(File f) throws Exception { return HomePlanet.savedGameParser.readSavedGame(f); }
 static void detection() throws Exception {
  Vault v = Vault.get();
  if (v.immersive) v = Vault.switchFleet(false);
  if (v.boarded() == null) v.board(v.docked().get(0));
  v.takeStock();
  Ship a = v.boarded(); String aName = a.name;
  // FTL plays on: one more beacon
  SavedGameState g = read(v.continueFile()); g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + 1);
  int kept = v.history(a).size();
  SaveHelper.writeSavedGame(v.continueFile(), g); v.takeStock();
  Setup.chk("N: FTL's own progress is still her, and kept in her records", v.boarded() == a && v.takeOverwritten() == null && v.history(a).size() == kept + 1);
  // the station renames her: its own change
  SavedGameState r = v.readCopy(a).save; r.setPlayerShipName(aName + " II"); r.getPlayerShip().setShipName(aName + " II"); v.write(a, r); v.takeStock();
  Setup.chk("N: the station's own changes are her too", v.boarded() == a && v.takeOverwritten() == null);
  aName = aName + " II";
  // FTL's New Game, same model and name: the totals went down
  SavedGameState fresh = Commission.build(read(v.continueFile()).getPlayerShipBlueprintId(), aName, net.blerf.ftl.constants.Difficulty.NORMAL, new Random(9));
  SaveHelper.writeSavedGame(v.continueFile(), fresh); v.takeStock();
  Setup.chk("N: a New Game with the same name and model is noticed", aName.equals(v.takeOverwritten()) && v.boarded() != a && v.boarded().stranger);
  boolean lost = false; for (Vault.Departed d : v.recoverable()) if (d.id.equals(a.id) && d.fate == Vault.Fate.LOST) lost = true;
  Setup.chk("N: the overwritten ship is recorded lost, and recoverable", lost);
  // Immersive: an uncommissioned ship is sent to the normal Space Dock
  Ship st = v.boarded(); v.dock(); // (leave the stranger docked in the normal fleet)
  Vault im = Vault.switchFleet(true);
  if (im.boarded() != null) im.dock();
  SaveHelper.writeSavedGame(im.continueFile(), Commission.build("PLAYER_SHIP_CIRCLE", "Stray Engi", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(2)));
  im.reload(); im.takeStock();
  Setup.chk("I: a continue.sav Immersive Mode didn't commission is an uncommissioned ship", im.boarded() != null && im.boarded().stranger);
  im.sendToOtherFleet(im.boarded(), false);
  Setup.chk("I: sent to the normal fleet, she's gone from this one", im.boarded() == null && !im.continueFile().exists());
  SaveHelper.writeSavedGame(im.continueFile(), Commission.build("PLAYER_SHIP_ROCK", "Stray Rock", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(3)));
  im.reload(); im.takeStock();
  Vault norm = Vault.handOverBoarded(im.boarded());
  boolean engi = false; for (Ship x : norm.docked()) if ("Stray Engi".equals(x.name)) engi = true;
  Setup.chk("I: switching now: she's boarded in the normal fleet", !norm.immersive && norm.boarded() != null && "Stray Rock".equals(read(norm.continueFile()).getPlayerShipName()) && !norm.boarded().stranger);
  norm.takeStock(); engi = false; for (Ship x : norm.docked()) if ("Stray Engi".equals(x.name)) engi = true;
  Setup.chk("I: and the one sent over waits at the normal Space Dock", engi);
 }
}
