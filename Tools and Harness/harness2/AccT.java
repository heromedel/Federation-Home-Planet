import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The rank letters' accolades from the real counts (5.60): a ship's jumps from her save (FTL flown with the station closed, since her trade), the ships defeated by every ship that served (one gone from the fleet; an uncommissioned one sent away left out), FTL's victory achievements never scored or told. args: gamedir, world saves (from WorldT), work */
public class AccT {
 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
  HomePlanet.immersiveNotifications = false; HomePlanet.careerMessages = false; HomePlanet.reputationOn = true;
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  Reputation.total(v); // the service reviewed, FTL's profile as it stands seen

  // FTL's victories: told in their own letter when won, never scored
  int rep = Reputation.total(v);
  TransT.profile(saves, new String[] {"PLAYER_SHIP_HARD"}, new String[] {"ACH_SECTOR_5", "ACH_WIN_NORMAL", "ACH_WIN_EASY"});
  int now = Reputation.total(v);
  String log = Reputation.log(v);
  Setup.chk("V: three achievements, two of them victories: +10 for the one (" + rep + " -> " + now + ")", now == rep + 10);
  Setup.chk("V: the reputation log names it, never a victory", log.contains("An achievement: Just Getting Started (+10)") && !log.contains("Victory"));
  // a career's log from before 5.60 may name one, after two others (one with a comma in its name)
  File repLog = new File(v.root, "reputation.log");
  SafeFiles.writeText(repLog, new String(SafeFiles.read(repLog), "UTF-8") + "2026-10-06 11:00  +20  2 achievements: Just Getting Started, Givin' her all she's got, Captain! (+20)\n"
    + "2026-10-06 12:00  +10  An achievement: Federation Victory (Normal) (+10)\n", false);

  // her jumps: she came by trade after four beacons, then flew ten with the station closed, five at a time
  Ship b = v.boarded();
  SavedGameState g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
  int start = g.getTotalBeaconsExplored(), herDefeats = g.getTotalShipsDefeated(), defeatedBefore = Reputation.defeatedInService(v) - herDefeats, linesBefore = jumped(v, b);
  SafeFiles.writeText(new File(v.historyOf(b), "traded.txt"), "trade=x\ndate=2026-01-01 00:00\nfrom=Commander Bree\ndefeated=" + g.getTotalShipsDefeated()
    + "\nbeacons=" + (start + 4) + "\nscrap=0\nsectors=1\n", false);
  for (int i = 1; i <= 2; i++) {
   g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
   g.setTotalBeaconsExplored(start + 5 * i); g.setCurrentBeaconId(g.getCurrentBeaconId() == 1 ? 2 : 1);
   g.setTotalShipsDefeated(g.getTotalShipsDefeated() + 2); // four in all, since her trade
   SaveHelper.writeSavedGame(v.continueFile(), g);
   v.takeStock();
  }
  int lines = jumped(v, b) - linesBefore;
  Setup.chk("J: two looks, so two Jumped lines in her voyage log for ten jumps (" + lines + ")", lines == 2);

  // ships defeated: one gone from the fleet (lost, seven, two of them before her trade), one sent to the other fleet (never the career's)
  Ship donor = v.docked().get(0);
  SavedGameState gone = v.readCopy(donor).save;
  gone.setTotalShipsDefeated(7);
  File goneDir = Setup.departed(v, "acc-gone", "Gone Before");
  SafeFiles.writeText(new File(goneDir, "fate.txt"), "LOST\nGone Before\n", false);
  SafeFiles.writeText(new File(goneDir, "traded.txt"), "trade=y\ndate=2026-01-01 00:00\nfrom=Commander Bree\ndefeated=2\nbeacons=0\nscrap=0\nsectors=0\n", false);
  SaveHelper.writeSavedGame(new File(ShipStore.versions(goneDir), "20261001-120000.sav"), gone);
  SavedGameState away = v.readCopy(donor).save;
  away.setTotalShipsDefeated(50);
  File awayDir = Setup.departed(v, "acc-away", "Sent Away");
  SafeFiles.writeText(new File(awayDir, "voyage.txt"), "sector=3\nvisited=4\n", false);
  SaveHelper.writeSavedGame(new File(ShipStore.versions(awayDir), "20261001-120000.sav"), away);
  int defeated = Reputation.defeatedInService(v);
  Setup.chk("D: her four since her trade, and five of the gone ship's seven; the one sent away not counted (" + defeatedBefore + " -> " + defeated + ")", defeated == defeatedBefore + 9);

  // the letters: only these three to tell
  for (String k : new String[] {"model", "crew", "expeditions"}) v.recordEvent("accolade:" + k, "test");
  List<String> said = new ArrayList<String>();
  for (int i = 0; i < 4; i++) { String body = Accolades.fill(v, "acc:" + i, "Of particular note in the discussions was:\n" + Accolades.TOKEN); if (!body.isEmpty()) said.add(body.replace("Of particular note in the discussions was:\n", "")); }
  System.out.println("   said: " + said);
  String all = String.join(" / ", said);
  Setup.chk("J: her jumps are her save's: six since her trade (her voyage log's lines would say " + words(lines) + ")",
    all.contains(words(6) + " jumps") && all.contains(b.name));
  Setup.chk("D: the ships defeated are every ship's that served (" + defeated + ")", all.contains(words(defeated) + " enemy ships"));
  Setup.chk("V: the achievement told is the last real one, comma and all, never the victory after it", all.contains("Powering a ton of systems on that Zoltan Cruiser, all at once.") && !all.contains("Victory") && !all.contains("\"Captain!\""));
  Setup.chk("A: three things to say, then the line left out", said.size() == 3);
  Setup.done();
 }
 static int jumped(Vault v, Ship b) throws IOException {
  File f = new File(v.historyOf(b), "voyage.log"); if (!f.isFile()) return 0;
  int n = 0; for (String l : new String(SafeFiles.read(f), "UTF-8").split("\r?\n")) if (l.matches("^\\S+ \\S+  Jumped.*")) n++; return n;
 }
 static String words(int n) { String[] w = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"}; return n >= 0 && n < w.length ? w[n] : String.format("%,d", n); }
}
