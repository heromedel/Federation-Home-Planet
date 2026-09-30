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
  Setup.chk("P: reactor bars: 30 each to 5, then 35", Pricing.reactor(5) == 150 && Pricing.reactor(8) == 255 && Pricing.reactorBar(11) == 40);
  Setup.chk("P: an unknown system has the flat price", Pricing.system("no_such_system", 2) == Pricing.UNPRICED_SYSTEM);
  Setup.chk("D: an upgrade costs FTL's upgrade price", Pricing.upgrade("shields", 1) == sh.getUpgradeCosts().get(0) && Pricing.upgrade("shields", 2) == sh.getUpgradeCosts().get(1));
  Setup.chk("D: no upgrade past FTL's limit", Pricing.upgrade("shields", sh.getMaxPower()) == -1);
  Setup.chk("D: hull repairs cost more deeper in", Pricing.hullRepair(1) == 2 && Pricing.hullRepair(2) == 2 && Pricing.hullRepair(3) == 3 && Pricing.hullRepair(8) == 5);
  SavedGameState k = Commission.build("PLAYER_SHIP_HARD", "Price Kestrel", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(1));
  Pricing.Quote full = Pricing.ship(k, 0, 0, 100), half = Pricing.ship(k, 0, 0, 50), custom = Pricing.ship(k, 5, 4, 100);
  System.out.println("Kestrel A: " + full.total() + " " + full.lines);
  Setup.chk("P: a Kestrel A costs about 1000", full.total() > 900 && full.total() < 1100);
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
  Setup.chk("F: relief ship: one crew, a basic laser and an ion blast, no drones or augments", s.getCrewList().size() == 1 && w.equals(Arrays.asList("LASER_BURST_1", "ION_1")) && s.getDroneList().isEmpty() && s.getAugmentIdList().isEmpty());
  int power = 0; boolean minimal = true;
  for (SavedGameParser.SystemType t : SavedGameParser.SystemType.values()) {
   SavedGameParser.SystemState st = s.getSystem(t); if (st == null || st.getCapacity() <= 0) continue;
   int want = t == SavedGameParser.SystemType.SHIELDS || t == SavedGameParser.SystemType.WEAPONS ? 2 : 1;
   if (st.getCapacity() != want) minimal = false;
   if (!t.isSubsystem()) power += st.getPower();
  }
  Setup.chk("F: every system at its minimum, reactor 7, power within it", minimal && s.getReservePowerCapacity() == 7 && power + 2 <= 7);
  File tmp = File.createTempFile("relief", ".sav"); SafeFiles.write(tmp, SaveHelper.toBytes(r));
  SavedGameState back = HomePlanet.savedGameParser.readSavedGame(tmp); tmp.delete();
  Setup.chk("F: she reads back", back.getPlayerShip().getCrewList().size() == 1 && back.getPlayerShip().getWeaponList().size() == 2);
  int rp = Pricing.ship(r, 0, 0, 100).total(), kp = Pricing.ship(Commission.build("PLAYER_SHIP_HARD", "K", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(1)), 0, 0, 100).total();
  System.out.println("Relief ship: " + rp);
  Setup.chk("F: she costs less than a Kestrel A", rp < kp);
 }
 static void reassign(Vault v) throws Exception {
  Setup.chk("F: a shipyard with ships isn't empty", !v.shipyardEmpty());
  Ship x = null; for (Ship s : v.docked()) x = s;
  v.board(x); v.disband();
  String bp = x.save().getPlayerShipBlueprintId();
  SafeFiles.writeText(v.systemsFile(), SystemsPanelHeader.H + "\nteleporter 2\n", false);
  File dir = v.surrender();
  Setup.chk("F: surrender empties the hold and the Junkyard", v.junked().isEmpty() && v.storageScrap() == 0 && !v.systemsFile().exists());
  Setup.chk("F: what was surrendered is kept", new File(dir, x.id + ".sav").isFile() && new File(dir, "storage.sav").isFile() && new File(dir, "storage-systems.txt").isFile() && dir.equals(v.lastSurrender()));
  List<String> ids = Retrofit.blueprintIds(new File(dir, x.id + ".sav"));
  Setup.chk("F: a surrendered hull's blueprints still count", ids != null && v.blueprintsInUseOrHistory().containsAll(ids));
  boolean refused = false; try { v.undoSurrender(dir); } catch (IOException e) { refused = true; }
  Setup.chk("F: undo is refused once a ship is at the Space Dock", refused && v.junked().isEmpty());
  for (Ship s : v.docked()) v.remove(s, "DESTROY");
  Setup.chk("F: no ship docked, boarded or junked: the shipyard is empty", v.shipyardEmpty());
  v.undoSurrender(dir);
  Setup.chk("F: undo returns the hold and the hulls", v.storageScrap() == 300 && v.byId(x.id) != null && v.byId(x.id).state == Ship.State.JUNKED && v.systemsFile().isFile() && v.lastSurrender() == null);
  File dir2 = v.surrender();
  SavedGameState g = v.readCopy(v.storage()).save; g.getPlayerShip().setScrapAmt(5); v.write(v.storage(), g);
  refused = false; try { v.undoSurrender(dir2); } catch (IOException e) { refused = e.getMessage().contains("changed"); }
  Setup.chk("F: undo is refused once the hold has changed", refused && v.storageScrap() == 5);
 }
}
class SystemsPanelHeader { static final String H = "# Ship systems stored in the Cargo Bay"; }
