import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** A career's reputation: the service so far reviewed once, then counted as FTL writes saves (sectors, scrap, ships, rebels, deaths), nothing lost in sector 8, the station's own changes not scored, ships lost, the Rebel Flagship, the Reputation rule (Immersive Mode always), nothing counted while it is off. args: gamedir, world saves (from WorldT), work */
public class RepuT {
 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
  HomePlanet.immersiveNotifications = false; HomePlanet.careerMessages = false; HomePlanet.reputationOn = false;
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  for (Ship s : v.all()) { // the fleet flown on Easy (6.17: her own difficulty's rate x1), so the base points can be checked
   if (s.state == Ship.State.STORAGE || s.save() == null) continue;
   SavedGameState e = v.readCopy(s).save; e.setDifficulty(net.blerf.ftl.constants.Difficulty.EASY); v.write(s, e);
  }
  Setup.chk("R: Sandbox Mode without its Reputation rule: none", !Reputation.shown());
  HomePlanet.reputationOn = true; // Settings' Reputation rule
  Setup.chk("R: with the rule, a reputation (no career or inbox needed)", Reputation.shown());
  HomePlanet.immersiveMode = true; HomePlanet.reputationOn = false;
  Setup.chk("R: Immersive Mode always has one", Reputation.shown());
  HomePlanet.immersiveMode = false; HomePlanet.reputationOn = true;

  // the service so far: a ship with a record, a traded ship (only since her trade), two lost ships (one in the last stand)
  Ship engi = named(v, "Test Engi");
  SavedGameState g = v.readCopy(engi).save;
  g.setTotalShipsDefeated(10); g.setTotalScrapCollected(500); g.setSectorNumber(3); g.setStateVar("lost_crew", 1);
  v.write(engi, g); // the station's write, before any count: nothing scores
  Ship stealth = named(v, "Test Stealth");
  g = v.readCopy(stealth).save;
  g.setTotalShipsDefeated(8); g.setTotalScrapCollected(300); g.setSectorNumber(3); g.setStateVar("lost_crew", 4);
  v.write(stealth, g);
  Setup.side(v, v.historyOf(stealth), "traded.txt", "trade=x\ndate=2026-01-01 00:00\nfrom=Commander Bree\ndefeated=5\nbeacons=0\nscrap=200\nsectors=2\n");
  departed(v, "lost-early", "Lost One", 2);
  departed(v, "lost-last", "Last Stand", 7);
  int total = Reputation.total(v);
  // Engi: 3 sectors 18, 500 scrap 50, 10 ships 40, 1 crew -10 = 98; Stealth since her trade: 2 sectors 12, 100 scrap 10, 3 ships 12 = 34; Lost One -50
  Setup.chk("R: the review: 98 + 34 - 50 = 82 (got " + total + ")", total == 82);
  String log = Reputation.log(v);
  Setup.chk("R: the review is one entry, its details under it", log.contains("Service record reviewed") && log.contains("Test Engi: 3 sectors (+18), 500 scrap (+50), 10 ships defeated (+40), 1 crew lost (−10)")
    && log.contains("Lost One: lost in action (−50)") && !log.contains("Last Stand"));
  Setup.chk("R: a traded ship counts only since her trade, her losses before it not told apart", log.contains("Test Stealth: 2 sectors (+12), 100 scrap (+10), 3 ships defeated (+12)  = +34"));
  Setup.chk("R: the review runs once", Reputation.total(v) == 82 && count(Reputation.log(v), "Service record reviewed") == 1);

  // FTL plays: a jump to a new sector, 85 scrap, a rebel ship defeated, a crew member killed
  Ship b = v.boarded();
  g = cont(v);
  g.setSectorNumber(g.getSectorNumber() + 1); g.setTotalScrapCollected(g.getTotalScrapCollected() + 85); g.setTotalShipsDefeated(g.getTotalShipsDefeated() + 1);
  g.setNearbyShip(Commission.build("REBEL_FAT", "Rebel", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(1)).getPlayerShip()); g.setNearbyShipAI(new NearbyShipAIState()); g.setUnknownXi(0); // a rebel alongside
  String dead = SaveHelper.getOwnCrew(g.getPlayerShip()).get(0).getName();
  g.getPlayerShip().getCrewList().remove(SaveHelper.getOwnCrew(g.getPlayerShip()).get(0));
  g.setStateVar("lost_crew", (g.hasStateVar("lost_crew") ? g.getStateVar("lost_crew") : 0) + 1);
  ftl(v, g);
  int sec = g.getSectorNumber(); // the sector bonus from here (6.17): 1 + 0.1 a sector after the first
  long eu = 82 * U + inFtl(6 + 8 + 6, sec) - 10 * U;
  total = Reputation.total(v);
  Setup.chk("R: a sector 6, 85 scrap 8, a rebel ship 6, at sector " + (sec + 1) + "'s bonus, a death -10 as it is: " + eu / U + " (got " + total + ")", total == eu / U);
  String last = Reputation.recent(v, 1).get(0);
  Setup.chk("R: said in the log, the death by name (" + last.trim() + ")", last.contains("a rebel ship defeated (+") && last.contains(dead + " died (−10)") && last.contains("85 scrap collected (+"));
  // the scrap left over counts when it makes up ten
  g = cont(v); g.setTotalScrapCollected(g.getTotalScrapCollected() + 5); g.setNearbyShip(null); ftl(v, g);
  eu += inFtl(1, sec);
  Setup.chk("R: 5 more scrap, with the 5 left over: 1 more", Reputation.total(v) == eu / U);
  // a clone came back (FTL counts it lost, but she's aboard), and a dismissal (gone, but not counted lost): no deaths
  g = cont(v); g.setStateVar("lost_crew", g.getStateVar("lost_crew") + 1); ftl(v, g);
  g = cont(v); g.getPlayerShip().getCrewList().remove(SaveHelper.getOwnCrew(g.getPlayerShip()).get(0)); ftl(v, g);
  Setup.chk("R: a clone and a dismissal cost nothing", Reputation.total(v) == eu / U);
  // the station's own change (a trade at the Cargo Bay, say): not scored
  g = v.readCopy(b).save; g.setTotalScrapCollected(g.getTotalScrapCollected() + 1000); v.write(b, g);
  g = cont(v); ftl(v, g);
  Setup.chk("R: the station's own change isn't scored", Reputation.total(v) == eu / U);
  // events: a jump within the sector to a quiet beacon (no fight, no ship, no store)
  g = cont(v); g.setCurrentBeaconId(g.getCurrentBeaconId() + 1); g.setTotalScrapCollected(g.getTotalScrapCollected() + 20); g.getPlayerShip().setScrapAmt(g.getPlayerShip().getScrapAmt() + 20);
  ftl(v, g);
  eu += inFtl(2 + 2, sec);
  Setup.chk("R: a good outcome 2 (with its 20 scrap 2): " + eu / U + " (got " + Reputation.total(v) + ")", Reputation.total(v) == eu / U && Reputation.recent(v, 1).get(0).contains("a good outcome (+"));
  g = cont(v); g.setCurrentBeaconId(g.getCurrentBeaconId() + 1); g.getPlayerShip().setHullAmt(g.getPlayerShip().getHullAmt() - 3);
  ftl(v, g);
  eu -= U;
  Setup.chk("R: a bad outcome -1 (hull lost), as it is", Reputation.total(v) == eu / U && Reputation.recent(v, 1).get(0).contains("a bad outcome (\u22121)"));
  g = cont(v); g.setCurrentBeaconId(g.getCurrentBeaconId() + 1); g.getPlayerShip().setHullAmt(g.getPlayerShip().getHullAmt() - 2); g.getPlayerShip().setMissilesAmt(g.getPlayerShip().getMissilesAmt() + 2);
  ftl(v, g);
  Setup.chk("R: gains and losses both: no outcome", Reputation.total(v) == eu / U);
  // caught: the rebel fleet holds the beacon she jumps to
  g = cont(v); int at = g.getCurrentBeaconId() + 1; g.setCurrentBeaconId(at);
  while (g.getBeaconList().size() <= at) g.getBeaconList().add(new BeaconState());
  g.getBeaconList().get(at).setFleetPresence(FleetPresence.REBEL);
  ftl(v, g);
  eu -= 5 * U;
  Setup.chk("R: caught by the rebel fleet -5: " + eu / U + " (got " + Reputation.total(v) + ")", Reputation.total(v) == eu / U && Reputation.recent(v, 1).get(0).contains("caught by the rebel fleet (\u22125)"));
  g = cont(v); g.getPlayerShip().setHullAmt(g.getPlayerShip().getHullAmt() + 1); ftl(v, g); // still there, a repair
  Setup.chk("R: caught once, not again while she stays", Reputation.total(v) == eu / U);
  // an FTL achievement earned in the fleet's service: +10, once
  TransT.profile(saves, new String[] {"PLAYER_SHIP_HARD"}, new String[] {"ACH_SECTOR_5"});
  eu += 10 * U; // away from FTL: the career's rate alone
  Setup.chk("R: a new achievement +10, no ship or sector rate: " + eu / U + " (got " + Reputation.total(v) + ")", Reputation.total(v) == eu / U && Reputation.recent(v, 1).get(0).contains("An achievement: ") && Reputation.recent(v, 1).get(0).contains("(+10)"));
  Setup.chk("R: counted once", Reputation.total(v) == eu / U);
  // the last stand: sector 8 reached (+6 a sector), a death there costs nothing
  g = cont(v); int from = g.getSectorNumber(); g.setSectorNumber(7);
  g.getPlayerShip().getCrewList().remove(SaveHelper.getOwnCrew(g.getPlayerShip()).get(0)); g.setStateVar("lost_crew", g.getStateVar("lost_crew") + 1);
  ftl(v, g);
  eu += inFtl((7 - from) * 6, 7);
  int expect = (int) (eu / U);
  Setup.chk("R: sector 8 reached at its bonus (x1.7), a death there costs nothing: " + expect + " (got " + Reputation.total(v) + ")", Reputation.total(v) == expect);
  // lost in the last stand: no loss; lost before it: -50
  v.continueFile().delete(); v.reload();
  Setup.chk("R: lost in sector 8: no loss", v.byId(b.id) == null && Reputation.total(v) == expect);
  Ship fed = named(v, "Test Federation");
  v.board(fed);
  g = cont(v); g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + 1); ftl(v, g);
  v.continueFile().delete(); v.reload();
  Setup.chk("R: lost in action before the last stand: -50", v.byId(fed.id) == null && Reputation.total(v) == expect - 50
    && Reputation.recent(v, 1).get(0).contains("Test Federation was lost in action (−50)"));
  // the Rebel Flagship
  Reputation.flagship(v, "Test Kestrel");
  eu += -50 * U + inFtl(100, 7);
  Setup.chk("R: the Rebel Flagship driven off, in sector 8: +170 (" + Reputation.recent(v, 1).get(0).trim() + ")", Reputation.total(v) == eu / U && Reputation.recent(v, 1).get(0).contains("Test Kestrel drove off the Rebel Flagship (+170)"));
  // the rule off: nothing counts
  HomePlanet.reputationOn = false;
  Ship lan = named(v, "Test Lanius"); v.board(lan);
  g = cont(v); g.setTotalShipsDefeated(g.getTotalShipsDefeated() + 3); ftl(v, g);
  HomePlanet.reputationOn = true;
  Setup.chk("R: with the rule off nothing was counted", Reputation.total(v) == eu / U);
  g = cont(v); ftl(v, g); // the next look, the rule on again: what was done meanwhile isn't scored now either
  Setup.chk("R: nor later, when the rule is back on", Reputation.total(v) == eu / U);
  Setup.chk("R: signs: +5, −50, 0", "+5".equals(Reputation.signed(5)) && "−50".equals(Reputation.signed(-50)) && "0".equals(Reputation.signed(0)));
  // 5.13: reputation as a currency (heromedel)
  HomePlanet.reputationOn = true;
  int now = Reputation.total(v);
  Setup.chk("R: a fee may spend down to zero, never below", Reputation.canSpend(v, now) && !Reputation.canSpend(v, now + 1));
  Reputation.captured(v, java.util.Arrays.asList("Ash", "Birch"));
  Setup.chk("R: two crew taken captive: -8", Reputation.total(v) == now - 8 && Reputation.recent(v, 1).get(0).contains("Taken captive: Ash, Birch"));
  Reputation.ransomed(v, "Ash");
  Setup.chk("R: a ransom paid: +2", Reputation.total(v) == now - 6 && Reputation.recent(v, 1).get(0).contains("Ransomed: Ash"));
  Reputation.expedition(v, "Test sector, test job", 0, 0, 1, 0);
  Setup.chk("R: an expedition's captive counted in its entry: -4", Reputation.total(v) == now - 10 && Reputation.log(v).contains("a crew member taken captive (\u22124)"));
  Reputation.spend(v, Reputation.total(v) + 7, "A plea, more than there is");
  Setup.chk("R: a plea may go below zero", Reputation.total(v) == -7 && !Reputation.canSpend(v, 1));
  Setup.chk("R: a plea in Sandbox Mode costs a tenth of what the hold doesn't cover", FreeCommand.reputationCost(1000, 400) == 60 && Economy.pleaPercent() == 10);
  // 5.15: How Reputation Can be Used (heromedel): 1 New Journeys and Pleads (the default), 2 Vanillas Breaking Actions, 3 Only as a score
  Setup.chk("U: the default is 1: journeys and pleas, not vanilla-breaking; the work order all in scrap; promise and rest cost reputation",
    HomePlanet.reputationUse == 1 && Economy.repForJourneysAndPleas() && !Economy.repForVanillaBreaking() && Economy.workOrderRep() == 0 && Economy.workOrderScrap() == 100 && Economy.repSpends());
  HomePlanet.reputationUse = 2;
  Setup.chk("U: 2: vanilla-breaking too; the work order half scrap, half reputation", Economy.repForJourneysAndPleas() && Economy.repForVanillaBreaking() && Economy.workOrderScrap() == 50 && Economy.workOrderRep() == 50);
  HomePlanet.reputationUse = 3;
  Setup.chk("U: 3: only a score: nothing spends it, the promise and rest are free", !Economy.repForJourneysAndPleas() && !Economy.repForVanillaBreaking() && !Economy.repSpends()
    && Economy.workOrderScrap() == 100 && Expeditions.promiseRep(v) == 0 && Rest.cost(v) == 0);
  HomePlanet.reputationUse = 1;
  // 5.15: the tally under the log: each piece in its pool, the pools adding up to the total
  Map<String, Integer> t = Reputation.tally(v);
  int sum = 0; for (int x : t.values()) sum += x;
  Setup.chk("T: the pools add up to the total " + t, sum == Reputation.total(v) && !t.containsKey("Other"));
  Setup.chk("T: the plea is Spent, captives and the ransom are Crew, sectors Travel, ships Combat, achievements their own",
    t.get("Spent") < 0 && t.containsKey("Crew") && t.get("Travel") > 0 && t.containsKey("Combat") && t.get("Achievements") > 0 && t.containsKey("Scrap") && t.containsKey("Events"));

  // 6.13: the rate (heromedel): what's earned counts x1 (Easy), x1.5 (Normal), x2 (Hard); losses and spending as they are;
  // a half point kept, never shown; each piece in the log at what it added, never a bonus of its own
  Ship lx = v.boarded(); // an Easy run in sector 1 (6.17: her rate and the sector's x1), so the career's rate shows alone
  g = v.readCopy(lx).save; g.setDifficulty(net.blerf.ftl.constants.Difficulty.EASY); g.setSectorNumber(0); v.write(lx, g);
  g = cont(v); ftl(v, g);
  long u0 = units(v);
  HomePlanet.reputationRate = 1; // Sandbox Mode's own, as on Normal
  g = cont(v); g.setTotalScrapCollected(g.getTotalScrapCollected() + 10); g.setTotalShipsDefeated(g.getTotalShipsDefeated() + 1); g.setNearbyShip(null); ftl(v, g);
  String line = Reputation.recent(v, 1).get(0);
  Setup.chk("X: x1.5: 10 scrap (1) and a ship (4) earn exactly 7.5, the rest kept hidden (got " + Reputation.total(v) + ")", units(v) == u0 + 75000 && Reputation.total(v) == Math.floorDiv(u0 + 75000, U));
  Setup.chk("X: each piece at what it added, adding up, no bonus shown (" + line.trim() + ")", line.contains("10 scrap collected (+") && line.contains("a ship defeated (+") && !line.toLowerCase().contains("bonus") && !line.contains(".5"));
  String ev = new String(SafeFiles.read(new File(v.root, "logs/events.log")), "UTF-8");
  Setup.chk("X: the machine line keeps the rate and the exact change", ev.contains("rate=1.5") && ev.contains("exact=7.5"));
  Reputation.captured(v, java.util.Arrays.asList("Cedar"));
  Setup.chk("X: a loss as it is (-4), the rest still kept", units(v) == u0 + 35000);
  g = cont(v); g.setTotalScrapCollected(g.getTotalScrapCollected() + 10); ftl(v, g);
  Setup.chk("X: the next 1.5 adds to it, nothing lost to rounding (got " + Reputation.total(v) + ")", units(v) == u0 + 50000 && Reputation.total(v) == Math.floorDiv(u0 + 50000, U));
  HomePlanet.reputationRate = 2; // as on Hard
  Reputation.ransomed(v, "Cedar");
  Setup.chk("X: x2: a ransom 4 (" + Reputation.recent(v, 1).get(0).trim() + ")", units(v) == u0 + 90000 && Reputation.recent(v, 1).get(0).contains("Ransomed: Cedar brought home (+"));
  Reputation.spend(v, 3, "A plea, at x2");
  Setup.chk("X: spending as it is", units(v) == u0 + 60000);
  HomePlanet.reputationRate = 0;
  t = Reputation.tally(v); sum = 0; for (int x : t.values()) sum += x;
  Setup.chk("X: the tally still adds up to the total " + t, sum == Reputation.total(v) && !t.containsKey("Other"));
  // the rate's rule: Sandbox Mode's own; a career's difficulty; Custom chooses once (asked if it never did)
  int[] lv = new int[CareerRules.RULES.length]; Arrays.fill(lv, 1);
  CareerRules custom = new CareerRules(CareerRules.CUSTOM, lv);
  // 6.17: in FTL, her own difficulty (Easy 1, Normal 1.25, Hard 1.5) and the sector's bonus (1 + 0.1 a sector after the first) too
  Ship lb = v.boarded();
  g = v.readCopy(lb).save; g.setDifficulty(net.blerf.ftl.constants.Difficulty.HARD); g.setSectorNumber(7); v.write(lb, g); // the station's change: not scored
  g = cont(v); ftl(v, g);
  int h0 = Reputation.total(v);
  g = cont(v); g.setTotalScrapCollected(g.getTotalScrapCollected() + 10); ftl(v, g);
  String hard = Reputation.recent(v, 1).get(0);
  ev = new String(SafeFiles.read(new File(v.root, "logs/events.log")), "UTF-8");
  Setup.chk("Z: a Hard run in sector 8: 10 scrap earns 1 x 1.5 x 1.7 = 2.55 (" + hard.trim() + ")", ev.contains("exact=2.55") && ev.contains("ship_rate=1.5") && ev.contains("sector_rate=1.7") && Reputation.total(v) >= h0 + 2);
  g = v.readCopy(lb).save; g.setDifficulty(net.blerf.ftl.constants.Difficulty.NORMAL); g.setSectorNumber(1); v.write(lb, g);
  g = cont(v); ftl(v, g);
  g = cont(v); g.setTotalShipsDefeated(g.getTotalShipsDefeated() + 1); g.setNearbyShip(null); ftl(v, g);
  ev = new String(SafeFiles.read(new File(v.root, "logs/events.log")), "UTF-8");
  Setup.chk("Z: a Normal run in sector 2: a ship defeated earns 4 x 1.25 x 1.1 = 5.5", ev.contains("exact=5.5") && ev.contains("ship_rate=1.25") && ev.contains("sector_rate=1.1"));
  int c0 = Reputation.total(v);
  g = cont(v); int cat = g.getCurrentBeaconId() + 1; g.setCurrentBeaconId(cat);
  while (g.getBeaconList().size() <= cat) g.getBeaconList().add(new BeaconState());
  g.getBeaconList().get(cat).setFleetPresence(FleetPresence.REBEL);
  ftl(v, g);
  Setup.chk("Z: a loss in FTL as it is, whatever her rates: caught -5", Reputation.total(v) == c0 - 5 || Reputation.total(v) == c0 - 4); // -5 exactly; the total's hidden remainder may carry it to -4
  // 6.18 (heromedel): every entry about a ship says where she was: her sector and her difficulty
  ev = new String(SafeFiles.read(new File(v.root, "logs/events.log")), "UTF-8");
  boolean boards = false, boardsOk = true, voyage = false, repOk = false;
  for (String l : ev.split("\n")) {
   if (l.contains(" | BOARD | ") && l.contains("station=" + HomePlanet.APP_VERSION)) { boards = true; if (!l.contains("ship_sector=") || !l.contains("ship_difficulty=")) boardsOk = false; } // this test's Boards
   if (l.contains("log=voyage") && l.contains("ship_sector=") && l.contains("ship_difficulty=")) voyage = true;
   if (l.contains(" | REPUTATION | ") && l.contains("reason=voyage") && l.contains("ship_id=") && l.contains("ship_sector=2") && l.contains("ship_difficulty=normal")) repOk = true;
  }
  Setup.chk("W: Board entries say her sector and difficulty", boards && boardsOk);
  Setup.chk("W: her voyage entries too", voyage);
  Setup.chk("W: a reputation entry about her: her id, sector and difficulty", repOk);
  Reputation.ransomed(v, "Dune");
  Setup.chk("Z: away from FTL, the career's rate alone: a ransom +2", Reputation.recent(v, 1).get(0).contains("brought home (+2)"));
  Setup.chk("X: the rule: Sandbox free; Easy x1, Normal x1.5, Hard x2 whatever is saved; Custom asks, then keeps its choice",
    Reputation.RATE_FREE.equals(Reputation.rateRule(null, null)) && Reputation.rateLevel(CareerRules.of(CareerRules.EASY), "2") == 0 && Reputation.rateLevel(CareerRules.of(CareerRules.NORMAL), null) == 1
    && Reputation.rateLevel(CareerRules.of(CareerRules.HARD), "0") == 2 && Reputation.RATE_FIXED.equals(Reputation.rateRule(CareerRules.of(CareerRules.HARD), null))
    && Reputation.RATE_ASK.equals(Reputation.rateRule(custom, null)) && Reputation.rateLevel(custom, null) == 0
    && Reputation.RATE_CHOSEN.equals(Reputation.rateRule(custom, "2")) && Reputation.rateLevel(custom, "2") == 2 && Reputation.RATE_ASK.equals(Reputation.rateRule(custom, "7")));
  Setup.done();
 }
 /** Ten-thousandths of a point (the total's hidden remainder, 6.17). */
 static final long U = 10000;
 /** What a point earned in FTL is worth on an Easy run in Sandbox Mode at x1, in this sector (0 is sector 1): 1 + 0.1 a sector after the first. */
 static long inFtl(int points, int sector) { return points * 1000L * (10 + Math.max(0, Math.min(7, sector))); }
 /** The total exactly, in ten-thousandths, as its file keeps it. */
 static long units(Vault v) { Properties p = Store.read(Store.file(v.root, "reputation")); long rest = Store.num(p, "rest", "1".equals(p.getProperty("half")) ? 5000 : 0); return Store.num(p, "total", 0) * U + rest; }
 static Ship named(Vault v, String n) { for (Ship s : v.all()) if (n.equals(s.name)) return s; throw new IllegalStateException(n); }
 static SavedGameState cont(Vault v) throws Exception { return HomePlanet.savedGameParser.readSavedGame(v.continueFile()); }
 /** FTL writes continue.sav, and the save watcher has the station look. */
 static void ftl(Vault v, SavedGameState g) throws Exception { SaveHelper.writeSavedGame(v.continueFile(), g); v.observeBoarded(); }
 static void departed(Vault v, String id, String name, int sector) throws Exception {
  File d = Setup.departed(v, id, name);
  Setup.side(v, d, "fate.txt", "LOST\n" + name + "\n");
  Setup.side(v, d, "voyage.txt", "sector=" + sector + "\nvisited=" + (sector + 1) + "\n");
 }
 static int count(String s, String what) { int n = 0, i = 0; while ((i = s.indexOf(what, i)) >= 0) { n++; i += what.length(); } return n; }
}
