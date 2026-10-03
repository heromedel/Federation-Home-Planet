import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.SavedGameState; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Prices from FTL's blueprints (HR1, HR2) and paying from the storage hold. args: gamedir, world saves (from WorldT), work */
public class PriceT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 prices();
 paying(v);
 relief();
 reassign(v);
 Setup.done();
}
 static void prices() throws Exception {
  net.blerf.ftl.xml.SystemBlueprint sh = DataManager.get().getSystem("shields");
  Setup.chk("P: a system at level 1 is its price", Pricing.system("shields", 1) == sh.getCost());
  Setup.chk("P: each level adds its upgrade cost", Pricing.system("shields", 3) == sh.getCost() + sh.getUpgradeCosts().get(0) + sh.getUpgradeCosts().get(1));
  Setup.chk("P: HR1 sells for half", Pricing.systemSale("shields", 3) == Pricing.system("shields", 3) / 2);
  Setup.chk("P: a Clone Bay (no level) sells as level 1", Pricing.systemSale("clonebay", 0) == Pricing.system("clonebay", 1) / 2);
  Setup.chk("P: reactor bars: 15 each to 5, then 20", Pricing.reactor(5) == 75 && Pricing.reactor(8) == 135 && Pricing.reactorBar(11) == 25 && Pricing.reactor(25) == 625);
  Setup.chk("P: an unknown system has the flat price", Pricing.system("no_such_system", 2) == Pricing.UNPRICED_SYSTEM);
  Setup.chk("D: an upgrade costs FTL's upgrade price", Pricing.upgrade("shields", 1) == sh.getUpgradeCosts().get(0) && Pricing.upgrade("shields", 2) == sh.getUpgradeCosts().get(1));
  Setup.chk("D: no upgrade past FTL's limit", Pricing.upgrade("shields", sh.getMaxPower()) == -1);
  Setup.chk("D: hull repairs are a flat 4 a point", Pricing.hullRepair() == 4);
  SavedGameState k = Commission.build("PLAYER_SHIP_HARD", "Price Kestrel", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(1));
  Pricing.Quote full = Pricing.ship(k, 0, 0, 100), half = Pricing.ship(k, 0, 0, 50), custom = Pricing.ship(k, 5, 4, 100);
  System.out.println("Kestrel A: " + full.total() + " " + full.lines);
  Setup.chk("P: a Kestrel A costs about 1,000 (885 and her supplies)", full.total() > 900 && full.total() < 1100);
  Setup.chk("P: the multiplier scales the total", half.total() == (full.subtotal * 50 + 50) / 100);
  Setup.chk("P: a custom hull adds rooms and doors", custom.total() == full.total() + 5 * Pricing.PER_ROOM + 4 * Pricing.PER_DOOR);
 }
 static void paying(Vault v) throws Exception {
  Ship st = v.storage();
  SavedGameState g = v.readCopy(st).save; g.getPlayerShip().setScrapAmt(300); v.write(st, g);
  Setup.chk("S: the hold's scrap reads back", v.storageScrap() == 300);
  String hash = SafeFiles.hash(st.file());
  boolean refused = false; try { v.payFromStorage(301); } catch (IOException e) { refused = e.getMessage().contains("300"); }
  Setup.chk("S: paying more than the hold has is refused, saying what it has", refused && SafeFiles.hash(st.file()).equals(hash));
  byte[] before = v.payFromStorage(120);
  Setup.chk("S: paying takes the scrap", v.storageScrap() == 180);
  v.refundStorage(before);
  Setup.chk("S: a refund puts it back", v.storageScrap() == 300);
 }
 static void relief() throws Exception {
  SavedGameState r = Commission.buildRelief("Relief", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(2));
  SavedGameParser.ShipState s = r.getPlayerShip();
  List<String> w = new ArrayList<String>(); for (SavedGameParser.WeaponState x : s.getWeaponList()) w.add(x.getWeaponId());
  Setup.chk("F: Relief Ship Type A: one crew, a Burst Laser I and an Ion Blast, no missiles, drones or augments", s.getCrewList().size() == 1 && w.equals(Arrays.asList("LASER_BURST_1", "ION_1"))
    && s.getMissilesAmt() == 0 && s.getDroneList().isEmpty() && s.getAugmentIdList().isEmpty());
  int power = 0; boolean minimal = true;
  for (SavedGameParser.SystemType t : SavedGameParser.SystemType.values()) {
   SavedGameParser.SystemState st = s.getSystem(t); if (st == null || st.getCapacity() <= 0) continue;
   int want = t == SavedGameParser.SystemType.SHIELDS || t == SavedGameParser.SystemType.WEAPONS ? 2 : 1;
   if (st.getCapacity() != want) minimal = false;
   if (!t.isSubsystem()) power += st.getPower();
  }
  Setup.chk("F: every system at its minimum, reactor 6, power within it", minimal && s.getReservePowerCapacity() == 6 && power <= 6);
  File tmp = File.createTempFile("relief", ".sav"); SafeFiles.write(tmp, SaveHelper.toBytes(r));
  SavedGameState back = HomePlanet.savedGameParser.readSavedGame(tmp); tmp.delete();
  Setup.chk("F: she reads back", back.getPlayerShip().getCrewList().size() == 1 && back.getPlayerShip().getWeaponList().size() == 2);
  int rp = Pricing.ship(r, 0, 0, 100).total(), kp = Pricing.ship(Commission.build("PLAYER_SHIP_HARD", "K", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(1)), 0, 0, 100).total();
  System.out.println("Relief ship: " + rp);
  Setup.chk("F: she's worth less than a Kestrel A", rp < kp);
  Setup.chk("F: a ship's value counts her fuel, missiles and drone parts", Pricing.ship(r, 0, 0, 100).lines.toString().contains("Fuel, missiles and drone parts")
    && Pricing.supplies(s) == s.getFuelAmt() * Pricing.FUEL);
  Setup.chk("F: a plea's reputation cost: a tenth of the shortfall, nothing when covered", FreeCommand.reputationCost(885, 0) == 89 && FreeCommand.reputationCost(600, 100) == 50
    && FreeCommand.reputationCost(600, 700) == 0);
 }
 static void reassign(Vault v) throws Exception {
  Setup.chk("F: a shipyard with ships isn't empty", !v.shipyardEmpty());
  Ship x = null; for (Ship s : v.docked()) x = s;
  v.board(x); v.disband();
  String bp = x.save().getPlayerShipBlueprintId();
  SafeFiles.writeText(v.systemsFile(), SystemsPanelHeader.H + "\nteleporter 2\n", false);
  // the hold can't be emptied (its records folder is blocked by a file): the hulls stay in the Junkyard
  File block = v.historyOf(v.storage()); SafeFiles.deleteTree(block); SafeFiles.writeText(block, "x", false);
  int junked = v.junked().size(); boolean failed = false;
  try { v.surrender(); } catch (IOException e) { failed = true; }
  boolean still = true; for (Ship j : v.junked()) if (!j.file().isFile()) still = false;
  Setup.chk("F: a failed surrender leaves the hulls in the Junkyard", failed && v.junked().size() == junked && still && v.lastSurrender() == null);
  block.delete();
  v.reload();
  Setup.chk("F: and they're still there after a reload", v.junked().size() == junked);
    int worth = homeplanet.parser.FreeCommand.surrenderValue(v);
  Setup.chk("F: what a surrender gives up is valued: the hold's scrap, its stored systems and the Junkyard's hulls", worth > 300 + homeplanet.parser.Pricing.system("teleporter", 2));
  File dir = v.surrender();
  Setup.chk("F: surrender empties the hold and the Junkyard", v.junked().isEmpty() && v.storageScrap() == 0 && !v.systemsFile().exists());
  Setup.chk("F: what was surrendered is kept", new File(dir, x.id + ".sav").isFile() && new File(dir, "storage.sav").isFile() && new File(dir, "storage-systems.txt").isFile() && dir.equals(v.lastSurrender()));
  List<String> ids = Retrofit.blueprintIds(new File(dir, x.id + ".sav"));
  Setup.chk("F: a surrendered hull's blueprints still count", ids != null && v.blueprintsInUseOrHistory().containsAll(ids));
  for (Ship s : v.docked()) v.remove(s, "DESTROY");
  Setup.chk("F: no ship docked, boarded or junked: the shipyard is empty", v.shipyardEmpty());
  v.undoSurrender(dir);
  Setup.chk("F: undo returns the hold and the hulls", v.storageScrap() == 300 && v.byId(x.id) != null && v.byId(x.id).state == Ship.State.JUNKED && v.systemsFile().isFile() && v.lastSurrender() == null);
  File dir2 = v.surrender();
  SavedGameState g = v.readCopy(v.storage()).save; g.getPlayerShip().setScrapAmt(5); v.write(v.storage(), g);
  boolean refused = false; try { v.undoSurrender(dir2); } catch (IOException e) { refused = e.getMessage().contains("changed"); }
  Setup.chk("F: undo is refused once the hold has changed", refused && v.storageScrap() == 5);
  v.useFreeCommand("test");
  plea(v);
 }
 /** Plead for New Ship: the hold's sale value, given up for her (the crew stay), with or without the difference refunded. */
 static void plea(Vault v) throws Exception {
  HomePlanet.sellSupplies = false; HomePlanet.sellSystems = false;
  SavedGameState g = v.readCopy(v.storage()).save;
  g.getPlayerShip().setScrapAmt(400); g.getPlayerShip().setMissilesAmt(10);
  g.getPlayerShip().getWeaponList().clear(); g.getPlayerShip().addWeapon(SaveHelper.newIdleWeapon("LASER_BURST_3"));
  SavedGameParser.CrewState aboard = Commission.build("PLAYER_SHIP_HARD", "K", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(9)).getPlayerShip().getCrewList().get(0);
  aboard.setName("Stays Aboard"); g.getPlayerShip().getCrewList().clear(); g.getPlayerShip().getCrewList().add(aboard);
  v.write(v.storage(), g);
  SafeFiles.writeText(v.systemsFile(), SystemsPanelHeader.H + "\nteleporter 2\n", false);
  int laser = Pricing.item("LASER_BURST_3") / 2;
  Setup.chk("F: the hold's sale value: scrap and gear at half, no supplies or systems while they can't be sold, no crew", FreeCommand.holdSaleValue(v) == 400 + laser);
  HomePlanet.sellSupplies = true; HomePlanet.sellSystems = true;
  int withAll = FreeCommand.holdSaleValue(v);
  Setup.chk("F: with selling allowed, missiles and stored systems count at their sale price", withAll == 400 + laser + Economy.supplySale(10, Pricing.MISSILE)
    + Pricing.systemSale("teleporter", 2, Economy.SYSTEM_SALE_PERCENT));
  HomePlanet.sellSupplies = false; HomePlanet.sellSystems = false;
  v.plead();
  Setup.chk("F: a plea takes nothing", v.storageScrap() == 400 && v.freeCommandOpen() && v.freeCommandReassigned());
  int junked = v.junked().size();
  byte[][] before = v.forfeitHold(500, 100);
  SavedGameState after = v.readCopy(v.storage()).save;
  Setup.chk("F: giving up the hold: emptied but for the refund, the crew stay, the Junkyard untouched", v.storageScrap() == 100 && after.getPlayerShip().getMissilesAmt() == 0
    && after.getPlayerShip().getWeaponList().isEmpty() && !v.systemsFile().exists() && v.junked().size() == junked
    && SaveHelper.getOwnCrew(after.getPlayerShip()).size() == 1 && "Stays Aboard".equals(SaveHelper.getOwnCrew(after.getPlayerShip()).get(0).getName()));
  v.unforfeitHold(before);
  Setup.chk("F: and put back as it was when her commission fails", v.storageScrap() == 400 && v.systemsFile().isFile());
  v.withdrawPlea();
 }
}
class SystemsPanelHeader { static final String H = "# Ship systems stored in the Cargo Bay"; }
