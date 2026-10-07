import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The Junkyard's parts, damaged systems' value, the stipend by beacons (a career from sectors carried over), and work in FTL counted as a beacon. args: gamedir, world saves (from WorldT), work */
public class PartT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 if (v.boarded() == null) v.board(v.docked().get(0));
 v.takeStock(); v.takeStock();
 damage();
 parts(v);
 stipend(v);
 work(v);
 clock(v);
 Setup.done();
}
 /** Broken bars: 5 each, 10 for Piloting, Oxygen and Engines; stored lines keep them. */
 static void damage() throws Exception {
  SavedGameState k = Commission.build("PLAYER_SHIP_HARD", "Damage Kestrel", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(1));
  ShipState s = k.getPlayerShip();
  int base = Pricing.damage(s);
  s.getSystem(SystemType.SHIELDS).setDamagedBars(2);
  s.getSystem(SystemType.ENGINES).setDamagedBars(1);
  s.getSystem(SystemType.PILOT).setDamagedBars(1);
  Setup.chk("D: 2 broken Shields bars 10, a broken Engines and Piloting bar 10 each (" + (Pricing.damage(s) - base) + ")", Pricing.damage(s) - base == 30);
  Setup.chk("D: a bar's value by system", Pricing.brokenBarValue("oxygen") == 10 && Pricing.brokenBarValue("weapons") == 5);
  Setup.chk("D: stored lines: a damaged system keeps its broken bars, an undamaged one is as before",
    "shields 3 2".equals(homeplanet.ui.SystemsPanel.line("shields", 3, 2)) && "shields 3".equals(homeplanet.ui.SystemsPanel.line("shields", 3, 0)) && "clonebay".equals(homeplanet.ui.SystemsPanel.line("clonebay", 0, 0)));
 }
 static void parts(Vault v) throws Exception {
  List<Parts.Listing> l = Parts.current(v);
  int sys = 0; for (Parts.Listing x : l) if (!x.salvage()) sys++;
  Setup.chk("P: 2 to 5 parts for sale (" + sys + "), and at most one piece of salvage", sys >= 2 && sys <= 5 && l.size() - sys <= 1 && Parts.count(v) == l.size());
  int was = HomePlanet.commissionPercent; boolean im = HomePlanet.immersiveMode;
  HomePlanet.immersiveMode = false; HomePlanet.commissionPercent = 100;
  Setup.chk("P: Piloting, Oxygen and Engines parts are worth 150 at level 1, FTL's upgrades on top", Parts.worth("pilot", 1) == 150 && Parts.worth("oxygen", 1) == 150
    && Parts.worth("engines", 3) == 150 + Pricing.system("engines", 3) - Pricing.system("engines", 1) && Parts.worth("shields", 2) == Pricing.system("shields", 2)
    && Parts.worth("oxygen", 3) == 150 + DataManager.get().getSystem("oxygen").getUpgradeCosts().get(0) + DataManager.get().getSystem("oxygen").getUpgradeCosts().get(1));
  HomePlanet.commissionPercent = 50;
  Setup.chk("S: a part's worth is at the difficulty's rate, before the rolls", Parts.worth("pilot", 1) == 75 && Parts.worth("shields", 2) == Pricing.rated(Pricing.system("shields", 2))
    && Parts.price("shields", 2, 1, 80, false) == (Parts.worth("shields", 2) - 5) * 80 / 100);
  HomePlanet.commissionPercent = was; HomePlanet.immersiveMode = im;
  boolean ok = true;
  for (Parts.Listing x : l) {
   if (x.salvage()) continue;
   int worth = Math.max(0, Parts.worth(x.id, x.level) - x.broken * Pricing.brokenBarValue(x.id));
   if (x.broken < 1 || x.broken > x.level || "artillery".equals(x.id) || "clonebay".equals(x.id)) ok = false;
   double f = (double) x.broken / x.level; int lo = (int) Math.round(75 - 45 * f), hi = (int) Math.round(95 - 45 * f), off = x.clearance ? 90 : 100;
   if (x.price < Math.max(5, worth * lo / 100 * off / 100 - 1) || x.price > Math.max(5, worth * hi / 100 * off / 100 + 1)) ok = false;
  }
  Setup.chk("P: each damaged (1 to all bars broken), no artillery or Clone Bay, priced by how broken it is (75-45f to 95-45f% of its value less its damage, 10% off a clearance)", ok);
  int low = 0, n = 0; Random rng = new Random(5);
  for (int i = 0; i < 200; i++) { Parts.roll(v, rng); for (Parts.Listing x : Parts.current(v)) { if (x.salvage()) continue; n++; if (x.level <= 2) low++; } }
  int low2 = 0, n2 = 0, clear = 0, nearly = 0, nearlyCheap = 0, sets = 0, salvage = 0, gear = 0; boolean salvagePriced = true; Random rng2 = new Random(9);
  for (int i = 0; i < 300; i++) { Parts.roll(v, rng2); sets++; for (Parts.Listing x : Parts.current(v)) {
   if (x.salvage()) {
    salvage++; if (Parts.ITEM.equals(x.kind)) gear++;
    if (x.price < Math.max(3, x.storePrice() * 40 / 100) || x.price > Math.max(3, x.storePrice() * 70 / 100)) salvagePriced = false;
    continue;
   }
   n2++; if (x.clearance) clear++;
   int worth = Math.max(0, Parts.worth(x.id, x.level) - x.broken * Pricing.brokenBarValue(x.id));
   if (x.level >= 4 && x.broken == 1 && worth >= 100) { nearly++; if (x.price * 2 < worth) nearlyCheap++; }
  } }
  Setup.chk("P: mostly low levels (" + low + " of " + n + " at 1 or 2)", low * 3 > n * 2);
  Setup.chk("P: about one set in five has salvage (" + salvage + " of " + sets + "), a weapon, drone or augment about one time in four (" + gear + ")",
    salvage * 8 > sets && salvage * 3 < sets && gear > 0 && gear * 2 < salvage);
  Setup.chk("P: salvage at 40-70% of FTL's store price", salvagePriced);
  // buying salvage: to the Cargo Hold
  Parts.Listing sv = null;
  for (int i = 0; sv == null && i < 100; i++) { Parts.roll(v, rng2); for (Parts.Listing x : Parts.current(v)) if (x.salvage() && !Parts.ITEM.equals(x.kind)) sv = x; }
  SavedGameState hh = v.readCopy(v.storage()).save; hh.getPlayerShip().setScrapAmt(sv.price + 1); v.write(v.storage(), hh);
  ShipState hb = v.readCopy(v.storage()).save.getPlayerShip();
  int had = Parts.FUEL.equals(sv.kind) ? hb.getFuelAmt() : Parts.MISSILES.equals(sv.kind) ? hb.getMissilesAmt() : hb.getDronePartsAmt();
  Parts.buy(v, sv);
  ShipState ha = v.readCopy(v.storage()).save.getPlayerShip();
  int has = Parts.FUEL.equals(sv.kind) ? ha.getFuelAmt() : Parts.MISSILES.equals(sv.kind) ? ha.getMissilesAmt() : ha.getDronePartsAmt();
  Setup.chk("P: salvage bought: " + sv.title() + " in the Cargo Hold, paid from it", has == had + sv.count && v.storageScrap() == 1);
  Setup.chk("P: about 1 in 12 a clearance (" + clear + " of " + n2 + ")", clear * 20 > n2 && clear * 7 < n2);
  Setup.chk("P: a part with one bar broken of 4 or more never sells under half its worth (" + nearlyCheap + " of " + nearly + ")", nearly > 0 && nearlyCheap == 0);
  java.util.Set<Integer> waits = new java.util.TreeSet<Integer>(); Random wr = new Random(3);
  java.lang.reflect.Method m = Parts.class.getDeclaredMethod("interval", Random.class); m.setAccessible(true);
  for (int i = 0; i < 300; i++) waits.add((Integer) m.invoke(null, wr));
  Setup.chk("P: new parts every 5 to 15 beacons " + waits, ((java.util.TreeSet<Integer>) waits).first() == 5 && ((java.util.TreeSet<Integer>) waits).last() == 15);
  l = Parts.current(v);
  String first = l.get(0).id + l.get(0).level + l.get(0).broken;
  ChainT.jump(v, 16);
  List<Parts.Listing> again = Parts.current(v);
  Setup.chk("P: after the wait, a new set comes in", again.size() >= 2 && Parts.count(v) == again.size());
  if (again.get(0).salvage()) throw new IllegalStateException("salvage comes after the systems");

  // buying: the hold pays, the part goes to the stored systems with its broken bars
  Parts.Listing pick = again.get(0);
  SavedGameState hold = v.readCopy(v.storage()).save; hold.getPlayerShip().setScrapAmt(pick.price + 3); v.write(v.storage(), hold);
  Parts.buy(v, pick);
  String file = new String(SafeFiles.read(v.systemsFile()), "UTF-8");
  Setup.chk("P: bought: the price from the Cargo Hold, the part stored broken (" + homeplanet.ui.SystemsPanel.line(pick.id, pick.level, pick.broken) + ")",
    v.storageScrap() == 3 && file.contains("\n" + homeplanet.ui.SystemsPanel.line(pick.id, pick.level, pick.broken) + "\n"));
  boolean gone = true; for (Parts.Listing x : Parts.current(v)) if (x.index == pick.index) gone = false;
  boolean twice = false; try { Parts.buy(v, pick); } catch (IOException e) { twice = true; }
  Setup.chk("P: sold once only", gone && twice && v.storageScrap() == 3);
  Parts.Listing dear = Parts.current(v).get(0);
  String before = file; boolean refused = false;
  if (dear.price > 3) { try { Parts.buy(v, dear); } catch (IOException e) { refused = e.getMessage().contains("3 scrap"); } }
  Setup.chk("P: too dear for the hold: refused, nothing changed", refused && v.storageScrap() == 3 && new String(SafeFiles.read(v.systemsFile()), "UTF-8").equals(before));
 }
 /** The stipend comes every 15 beacons to a sector of the old rule; a career from sectors keeps what it's owed and its progress. */
 static void stipend(Vault v) throws Exception {
  if (!Career.started(v.root)) Career.start(false, false, false);
  java.lang.reflect.Method unpaid = Career.class.getDeclaredMethod("unpaidMonths"); unpaid.setAccessible(true);
  File cf = Store.file(v.root, "career");
  Properties p = Store.load(cf);
  Setup.chk("S: a career begun now counts beacons from its start", Integer.toString(v.beaconsSeen()).equals(p.getProperty("beaconsAtStart")));
  Setup.chk("S: Sandbox careers: every two months, 56 beacons", Career.beaconsPerStipend() == 56);
  // a career from before: 9 sectors travelled at 4 a month, 1 month paid: 1 month owed, a sector on to the next
  p.remove("beaconsAtStart"); p.setProperty("sectorsAtStart", "0"); p.setProperty("paidMonths", "1");
  Clock.set(v, "sectors", "9");
  Store.write(cf, p, null);
  int owed = (Integer) unpaid.invoke(null);
  Setup.chk("S: a career from sectors: still 1 month owed after the switch (" + owed + ")", owed == 1);
  ChainT.jump(v, 32);
  Setup.chk("S: its 9 sectors carried over as 135 beacons: 32 more is a beacon short of the third 56", (Integer) unpaid.invoke(null) == 1);
  ChainT.jump(v, 1);
  Setup.chk("S: and 33 make the next stipend", (Integer) unpaid.invoke(null) == 2);
  Setup.chk("S: the difficulties' stipends: 28, 56, 84 beacons (one, two, three months)", CareerRules.of(CareerRules.EASY).stipendBeacons() == 28
    && CareerRules.of(CareerRules.NORMAL).stipendBeacons() == 56 && CareerRules.of(CareerRules.HARD).stipendBeacons() == 84
    && CareerRules.LEVELS[CareerRules.STIPEND][1].equals("two months") && Career.BEACONS_PER_MONTH == 28);
 }
 /** Buying, repairs or upgrades in FTL, with no jump, count as one beacon a stop; nothing else does. */
 /** Every beacon the boarded ship flies counts, once: seen by FTL's saves, a dock, the station's writes, a look. */
 static void clock(Vault v) throws Exception {
  if (v.boarded() == null) v.board(v.docked().get(0));
  v.takeStock();
  int b0 = v.beaconsSeen(), s0 = v.sectorsSeen();
  fly(v, 20, 1); Ship b = v.boarded(); Vault.Copy c = v.readCopy(b); v.begin().put(b, c.save, c.hash).commit(); v.takeStock();
  Setup.chk("K: flown 20 beacons and a sector, then the station writes her before a look: counted (" + (v.beaconsSeen() - b0) + ", " + (v.sectorsSeen() - s0) + ")", v.beaconsSeen() == b0 + 20 && v.sectorsSeen() == s0 + 1);
  fly(v, 20, 1); v.dock(); v.takeStock();
  Setup.chk("K: flown 20 more, then docked before a look: counted (" + (v.beaconsSeen() - b0) + ")", v.beaconsSeen() == b0 + 40 && v.sectorsSeen() == s0 + 2);
  v.board(v.docked().get(0)); v.takeStock();
  Setup.chk("K: boarding a ship counts nothing (her own past is hers)", v.beaconsSeen() == b0 + 40);
  fly(v, 5, 0); v.observeBoarded();
  Setup.chk("K: FTL's own save moves the clock as she flies", v.beaconsSeen() == b0 + 45);
  v.takeStock(); v.observeBoarded(); v.takeStock();
  Setup.chk("K: and nothing counts twice", v.beaconsSeen() == b0 + 45);
  Parts.current(v);
  Properties pp = new Properties(); pp.load(new ByteArrayInputStream(SafeFiles.read(new File(v.cargoHoldDir(), "parts.txt"))));
  Setup.chk("K: the Junkyard's Parts restock as the fleet flies", Integer.parseInt(pp.getProperty("rolledAt")) > b0);
  // FTL's New Game writes over her: the new ship's run so far was flown in the fleet's time
  SavedGameState n = Commission.build("PLAYER_SHIP_HARD", "Newcomer", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(3));
  n.setTotalBeaconsExplored(6); n.setSectorNumber(1);
  SaveHelper.writeSavedGame(v.continueFile(), n); v.takeStock();
  Setup.chk("K: New Game in FTL: the new ship's 6 beacons count (" + (v.beaconsSeen() - b0 - 45) + ")", v.beaconsSeen() == b0 + 51 && v.sectorsSeen() == s0 + 3);
 }
 static void fly(Vault v, int beacons, int sectors) throws Exception {
  SavedGameState g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
  g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + beacons); g.setSectorNumber(g.getSectorNumber() + sectors);
  SaveHelper.writeSavedGame(v.continueFile(), g);
 }
 static void work(Vault v) throws Exception {
  Ship s = v.boarded();
  v.takeStock();
  int seen = v.beaconsSeen();
  SavedGameState g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
  g.setStateVar("fired_shot", 3); g.getPlayerShip().setHullAmt(g.getPlayerShip().getHullAmt() - 1);
  SaveHelper.writeSavedGame(v.continueFile(), g); v.takeStock();
  Setup.chk("W: other changes at a beacon count for nothing", v.beaconsSeen() == seen);
  g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
  g.setStateVar("store_purchase", (g.hasStateVar("store_purchase") ? g.getStateVar("store_purchase") : 0) + 1);
  SaveHelper.writeSavedGame(v.continueFile(), g); v.takeStock();
  Setup.chk("W: a purchase at the store counts as a beacon", v.beaconsSeen() == seen + 1);
  g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
  g.setStateVar("system_upgrade", (g.hasStateVar("system_upgrade") ? g.getStateVar("system_upgrade") : 0) + 2);
  SaveHelper.writeSavedGame(v.continueFile(), g); v.takeStock();
  Setup.chk("W: more work at the same stop: no more", v.beaconsSeen() == seen + 1);
  String vl = Setup.voyageLog(v, s);
  Setup.chk("W: noted in her voyage log, never as a beacon (the second hard rule)", vl.contains("Time spent on work at the beacon") && !vl.contains("counted as a beacon"));
  Ship other = v.docked().get(0);
  v.board(other); v.takeStock(); v.board(s); v.takeStock();
  g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
  g.setStateVar("store_repair", (g.hasStateVar("store_repair") ? g.getStateVar("store_repair") : 0) + 1);
  SaveHelper.writeSavedGame(v.continueFile(), g); v.takeStock();
  Setup.chk("W: switching ships at the stop and back, more work counts nothing more", v.beaconsSeen() == seen + 1);
  ChainT.jump(v, 1);
  g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
  g.setStateVar("store_repair", (g.hasStateVar("store_repair") ? g.getStateVar("store_repair") : 0) + 1);
  SaveHelper.writeSavedGame(v.continueFile(), g); v.takeStock();
  Setup.chk("W: the next beacon: a jump, then a repair, count two", v.beaconsSeen() == seen + 3);
 }
}
