import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.SavedGameState; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Immersive Mode's own fleet: switching keeps both fleets whole, designs are shared, the player's rules come back. args: gamedir, world saves (from WorldT), work */
public class FleetT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 fleets(v);
 detection();
 rules();
 difficulties();
 slots();
 endCareer();
 Setup.done();
}
 /** Difficulties: each sets its rules, Custom any level of each, and a career from before them keeps what it had. */
 static void difficulties() throws Exception {
  Vault v = Vault.get();
  if (!v.immersive) v = Vault.switchFleet(true);
  HomePlanet.immersiveMode = true; HomePlanet.applyImmersive();
  File career = new File(v.root, "career.txt");
  String[] names = {CareerRules.EASY, CareerRules.NORMAL, CareerRules.HARD};
  int[][] want = {{200, 0, 0, 50, 2, 75, 50}, {500, 25, 10, 25, 3, 100, 25}, {1000, 50, -1, 0, 4, 100, 10}};
  String[] reassign = {FreeCommand.KESTREL, FreeCommand.VARIABLE, FreeCommand.RELIEF};
  String[] victory = {FinalVictory.RESCUE, FinalVictory.RESCUE, FinalVictory.MUSEUM};
  int[] museum = {100, 50, 50};
  for (int d = 0; d < 3; d++) {
   career.delete();
   int before = v.storageScrap();
   Career.start(false, false, CareerRules.of(names[d]));
   int[] w = want[d];
   boolean ok = Economy.journeyFee() == w[0] && Economy.removalFee() == w[1] && (w[2] < 0 ? !Economy.stripAllowed() : Economy.stripAllowed() && Economy.stripFee() == w[2])
     && Economy.supplyPercent() == w[3] && Career.sectorsPerMonth() == w[4] && Economy.commissionPercent() == w[5] && v.storageScrap() - before == w[6]
     && Economy.reassignment().equals(reassign[d]) && FinalVictory.choice().equals(victory[d]) && FinalVictory.museumPercent() == museum[d];
   Setup.chk("D: " + names[d] + ": its fees, prices, stipend, starting scrap, reassignment and final victory", ok);
  }
  Setup.chk("D: Hard: missiles and drone parts sell for 1 scrap each", Economy.supplySale(5, Pricing.MISSILE) == 5);
  career.delete();
  Career.start(false, false, new CareerRules(CareerRules.CUSTOM, new int[] {2, 0, 2, 1, 0, 2, 0, 0, 1}));
  Setup.chk("D: Custom: each rule at its own level", "Custom".equals(CareerRules.current().title()) && FinalVictory.choice().equals(FinalVictory.MUSEUM)
    && Economy.journeyFee() == 200 && Economy.reassignment().equals(FreeCommand.RELIEF) && Economy.removalFee() == 25 && Economy.stripFee() == 0
    && Economy.supplySale(5, Pricing.MISSILE) == 5 && Career.sectorsPerMonth() == 2 && Economy.commissionPercent() == 75);
  // a career from before difficulties: no difficulty in its career.txt
  Properties p = new Properties(); p.setProperty("salaryAll", "false"); p.setProperty("ownProfile", "false"); p.setProperty("finalVictory", FinalVictory.REWARD);
  p.setProperty("paidMonths", "0"); p.setProperty("sectorsAtStart", "0");
  java.io.StringWriter sw = new java.io.StringWriter(); p.store(sw, ""); SafeFiles.writeText(career, sw.toString(), false);
  Thread.sleep(20); career.setLastModified(System.currentTimeMillis());
  HomePlanet.stripAllowed = true;
  CareerRules e = Career.rules(v.root);
  Setup.chk("D: a career from before difficulties keeps its rules: journeys 200, Variable, free removal and stripping, 25%, every 4 sectors, full price",
    CareerRules.EARLIER.equals(e.name) && e.journeyFee() == 200 && FreeCommand.VARIABLE.equals(e.reassignment()) && e.removalFee() == 0 && e.stripAllowed() && e.stripFee() == 0
    && e.supplyPercent() == 25 && e.stipendSectors() == 4 && e.commissionPercent() == 100);
  HomePlanet.stripAllowed = false;
  Setup.chk("D: and its own final victory choice, written down once", FinalVictory.choice().equals(FinalVictory.REWARD) && FinalVictory.fixed() == null
    && Career.rules(v.root).stripAllowed() && new String(SafeFiles.read(career), "UTF-8").contains("difficulty=earlier"));
  HomePlanet.leaveImmersive();
  Vault.switchFleet(false);
 }
 /** Five modes, five fleets: each career its own folder; switching between careers goes by way of Sandbox Mode; one can end alone. */
 static void slots() throws Exception {
  Vault v = Vault.get();
  if (v.immersive) v = Vault.switchFleet(false);
  Setup.chk("M: each mode's folder", Vault.folderOf(Vault.SANDBOX).equals(Vault.FOLDER) && Vault.folderOf(Vault.CUSTOM).equals(Vault.IMMERSIVE_FOLDER)
    && Vault.folderOf(Vault.EASY).equals("FederationHomePlanet-Immersive-Easy") && Vault.folderOf(Vault.HARD).equals("FederationHomePlanet-Immersive-Hard")
    && "Immersive Normal".equals(Vault.title(Vault.NORMAL)) && "Sandbox Mode".equals(Vault.title(Vault.SANDBOX)) && Vault.CUSTOM.equals(Vault.slotOf("nonsense")));
  Vault easy = Vault.switchFleet(Vault.EASY);
  Setup.chk("M: the Easy career opens its own, empty fleet", easy.immersive && Vault.EASY.equals(easy.slot) && easy.root.getName().equals("FederationHomePlanet-Immersive-Easy") && easy.shipyardEmpty());
  Career.start(false, false, CareerRules.of(CareerRules.EASY));
  Ship e = easy.adopt(Commission.build("PLAYER_SHIP_CIRCLE", "Easy Engi", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(8)));
  Setup.chk("M: its difficulty is its own", CareerRules.EASY.equals(CareerRules.current().name));
  Vault.switchFleet(Vault.SANDBOX);
  Vault normal = Vault.switchFleet(Vault.NORMAL);
  Setup.chk("M: the Normal career doesn't see the Easy one's ships", normal.byId(e.id) == null && Vault.NORMAL.equals(normal.slot));
  File fake = new File(Vault.rootOf(normal.saves, Vault.EASY), "ships/retrofit.sav"); // a hull on the station's blueprint, as far as the scan cares
  SafeFiles.writeText(fake, "PLAYER_SHIP_CIRCLE" + Retrofit.SUFFIX, false);
  Setup.chk("M: but every other fleet's ships count for blueprints in use", normal.otherFleetUsing("PLAYER_SHIP_CIRCLE" + Retrofit.SUFFIX).contains("retrofit (Immersive Easy fleet)")
    && normal.otherFleetBlueprints().contains("PLAYER_SHIP_CIRCLE" + Retrofit.SUFFIX));
  fake.delete();
  boolean refused = false; Vault.switchFleet(Vault.EASY);
  try { Vault.endCareer(Vault.EASY); } catch (IOException x) { refused = true; }
  Setup.chk("M: the career in use can't be ended", refused);
  Vault.switchFleet(Vault.SANDBOX);
  int sandboxShips = Vault.get().all().size();
  File zip = Vault.endCareer(Vault.EASY);
  Setup.chk("M: ending the Easy career zips it, named, and leaves the others", zip.getName().startsWith("Immersive Easy career ") && !Vault.rootOf(Vault.get().saves, Vault.EASY).exists()
    && Vault.rootOf(Vault.get().saves, Vault.NORMAL).isDirectory() && Vault.get().all().size() == sandboxShips);
  Setup.chk("M: a fleet's ships are counted without opening it", Vault.shipCount(Vault.rootOf(Vault.get().saves, Vault.CUSTOM)) >= 0);
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
