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
  Setup.chk("P: 2 to 5 parts for sale (" + l.size() + ")", l.size() >= 2 && l.size() <= 5 && Parts.count(v) == l.size());
  boolean ok = true;
  for (Parts.Listing x : l) {
   int worth = Math.max(0, Pricing.system(x.id, x.level) - x.broken * Pricing.brokenBarValue(x.id));
   if (x.broken < 1 || x.broken > x.level || "artillery".equals(x.id) || "clonebay".equals(x.id)) ok = false;
   if (x.price < Math.max(5, worth / 4 - 1) || x.price > Math.max(5, worth * 3 / 4 + 1)) ok = false;
  }
  Setup.chk("P: each damaged (1 to all bars broken), no artillery or Clone Bay, at 25-75% of its value less its damage", ok);
  int low = 0, n = 0; Random rng = new Random(5);
  for (int i = 0; i < 200; i++) { Parts.roll(v, rng); for (Parts.Listing x : Parts.current(v)) { n++; if (x.level <= 2) low++; } }
  Setup.chk("P: mostly low levels (" + low + " of " + n + " at 1 or 2)", low * 3 > n * 2);
  java.util.Set<Integer> waits = new java.util.TreeSet<Integer>(); Random wr = new Random(3);
  java.lang.reflect.Method m = Parts.class.getDeclaredMethod("interval", Random.class); m.setAccessible(true);
  for (int i = 0; i < 300; i++) waits.add((Integer) m.invoke(null, wr));
  Setup.chk("P: new parts every 5 to 15 beacons " + waits, ((java.util.TreeSet<Integer>) waits).first() == 5 && ((java.util.TreeSet<Integer>) waits).last() == 15);
  l = Parts.current(v);
  String first = l.get(0).id + l.get(0).level + l.get(0).broken;
  ChainT.jump(v, 16);
  List<Parts.Listing> again = Parts.current(v);
  Setup.chk("P: after the wait, a new set comes in", again.size() >= 2 && Parts.count(v) == again.size());

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
  File cf = new File(v.root, "career.txt");
  Properties p = new Properties(); p.load(new ByteArrayInputStream(SafeFiles.read(cf)));
  Setup.chk("S: a career begun now counts beacons from its start", Integer.toString(v.beaconsSeen()).equals(p.getProperty("beaconsAtStart")));
  Setup.chk("S: Sandbox careers: every 60 beacons", Career.beaconsPerMonth() == 60);
  // a career from before: 9 sectors travelled at 4 a month, 1 month paid: 1 month owed, a sector on to the next
  p.remove("beaconsAtStart"); p.setProperty("sectorsAtStart", "0"); p.setProperty("paidMonths", "1");
  SafeFiles.writeText(new File(v.root, "sectors.txt"), "9\n", false);
  ByteArrayOutputStream b = new ByteArrayOutputStream(); p.store(b, null); SafeFiles.write(cf, b.toByteArray());
  int owed = (Integer) unpaid.invoke(null);
  Setup.chk("S: a career from sectors: still 1 month owed after the switch (" + owed + ")", owed == 1);
  ChainT.jump(v, 44);
  Setup.chk("S: its odd sector carried over as 15 beacons: 44 more is a beacon short", (Integer) unpaid.invoke(null) == 1);
  ChainT.jump(v, 1);
  Setup.chk("S: and 45 make the next month", (Integer) unpaid.invoke(null) == 2);
  Setup.chk("S: the difficulties' stipends: 30, 45, 60 beacons", CareerRules.of(CareerRules.EASY).stipendBeacons() == 30
    && CareerRules.of(CareerRules.NORMAL).stipendBeacons() == 45 && CareerRules.of(CareerRules.HARD).stipendBeacons() == 60
    && CareerRules.LEVELS[CareerRules.STIPEND][1].equals("45 beacons"));
 }
 /** Buying, repairs or upgrades in FTL, with no jump, count as one beacon a stop; nothing else does. */
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
  Setup.chk("W: noted in her voyage log", VoyageLog.read(v, s).contains("counted as a beacon"));
  ChainT.jump(v, 1);
  g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
  g.setStateVar("store_repair", (g.hasStateVar("store_repair") ? g.getStateVar("store_repair") : 0) + 1);
  SaveHelper.writeSavedGame(v.continueFile(), g); v.takeStock();
  Setup.chk("W: the next beacon: a jump, then a repair, count two", v.beaconsSeen() == seen + 3);
 }
}
