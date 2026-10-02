import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** A career's reputation: the service so far reviewed once, then counted as FTL writes saves (sectors, scrap, ships, rebels, deaths), nothing lost in sector 8, the station's own changes not scored, ships lost, the Rebel Flagship, the Reputation rule (Immersive Mode always), nothing counted while it is off. args: gamedir, world saves (from WorldT), work */
public class RepuT {
 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
  HomePlanet.immersiveNotifications = false; HomePlanet.careerMessages = false; HomePlanet.reputationOn = false;
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
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
  SafeFiles.writeText(new File(new File(v.historyDir(), stealth.id), "traded.txt"), "trade=x\ndate=2026-01-01 00:00\nfrom=Commander Bree\ndefeated=5\nbeacons=0\nscrap=200\nsectors=2\n", false);
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
  total = Reputation.total(v);
  Setup.chk("R: a sector +6, 85 scrap +8, a rebel ship +6, a death -10: 92 (got " + total + ")", total == 92);
  String last = Reputation.recent(v, 1).get(0);
  Setup.chk("R: said in the log, the death by name", last.contains("a rebel ship defeated (+6)") && last.contains(dead + " died (−10)") && last.contains("85 scrap collected (+8)"));
  // the scrap left over counts when it makes up ten
  g = cont(v); g.setTotalScrapCollected(g.getTotalScrapCollected() + 5); g.setNearbyShip(null); ftl(v, g);
  Setup.chk("R: 5 more scrap, with the 5 left over: +1", Reputation.total(v) == 93);
  // a clone came back (FTL counts it lost, but she's aboard), and a dismissal (gone, but not counted lost): no deaths
  g = cont(v); g.setStateVar("lost_crew", g.getStateVar("lost_crew") + 1); ftl(v, g);
  g = cont(v); g.getPlayerShip().getCrewList().remove(SaveHelper.getOwnCrew(g.getPlayerShip()).get(0)); ftl(v, g);
  Setup.chk("R: a clone and a dismissal cost nothing", Reputation.total(v) == 93);
  // the station's own change (a trade at the Cargo Bay, say): not scored
  g = v.readCopy(b).save; g.setTotalScrapCollected(g.getTotalScrapCollected() + 1000); v.write(b, g);
  g = cont(v); ftl(v, g);
  Setup.chk("R: the station's own change isn't scored", Reputation.total(v) == 93);
  // the last stand: sector 8 reached (+6 a sector), a death there costs nothing
  g = cont(v); int from = g.getSectorNumber(); g.setSectorNumber(7);
  g.getPlayerShip().getCrewList().remove(SaveHelper.getOwnCrew(g.getPlayerShip()).get(0)); g.setStateVar("lost_crew", g.getStateVar("lost_crew") + 1);
  ftl(v, g);
  int expect = 93 + (7 - from) * 6;
  Setup.chk("R: sector 8 reached, a death there costs nothing: " + expect + " (got " + Reputation.total(v) + ")", Reputation.total(v) == expect);
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
  Setup.chk("R: the Rebel Flagship defeated: +100", Reputation.total(v) == expect + 50 && Reputation.recent(v, 1).get(0).contains("  +100  Test Kestrel defeated the Rebel Flagship (+100)"));
  // the rule off: nothing counts
  HomePlanet.reputationOn = false;
  Ship lan = named(v, "Test Lanius"); v.board(lan);
  g = cont(v); g.setTotalShipsDefeated(g.getTotalShipsDefeated() + 3); ftl(v, g);
  HomePlanet.reputationOn = true;
  Setup.chk("R: with the rule off nothing was counted", Reputation.total(v) == expect + 50);
  g = cont(v); ftl(v, g); // the next look, the rule on again: what was done meanwhile isn't scored now either
  Setup.chk("R: nor later, when the rule is back on", Reputation.total(v) == expect + 50);
  Setup.chk("R: signs: +5, −50, 0", "+5".equals(Reputation.signed(5)) && "−50".equals(Reputation.signed(-50)) && "0".equals(Reputation.signed(0)));
  Setup.done();
 }
 static Ship named(Vault v, String n) { for (Ship s : v.all()) if (n.equals(s.name)) return s; throw new IllegalStateException(n); }
 static SavedGameState cont(Vault v) throws Exception { return HomePlanet.savedGameParser.readSavedGame(v.continueFile()); }
 /** FTL writes continue.sav, and the save watcher has the station look. */
 static void ftl(Vault v, SavedGameState g) throws Exception { SaveHelper.writeSavedGame(v.continueFile(), g); v.observeBoarded(); }
 static void departed(Vault v, String id, String name, int sector) throws Exception {
  File d = new File(v.historyDir(), id); d.mkdirs();
  SafeFiles.writeText(new File(d, "fate.txt"), "LOST\n" + name + "\n", false);
  SafeFiles.writeText(new File(d, "voyage.txt"), "sector=" + sector + "\nvisited=" + (sector + 1) + "\n", false);
 }
 static int count(String s, String what) { int n = 0, i = 0; while ((i = s.indexOf(what, i)) >= 0) { n++; i += what.length(); } return n; }
}
