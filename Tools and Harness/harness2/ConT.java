import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*; import net.blerf.ftl.model.*;
/** The console (5.22): admin commands locked, then unlocked; /passtime passes exactly that many days, each with the station's round. args: gamedir, world saves, work */
public class ConT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive(); HomePlanet.expeditionType = 2;
 HomePlanet.propFile = new File(work, "test.cfg"); HomePlanet.config.remove(StationConsole.DEV);
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 StationConsole.Reply r = StationConsole.answer("/admin");
 Setup.chk("A: locked, /admin, /admin ? and /admin dc are unknown commands: nothing says there is anything to unlock", r.text.equals("Unknown command: /admin") && r.days == 0
   && StationConsole.answer("/admin ?").text.startsWith("Unknown command") && StationConsole.answer("/admin dc").text.startsWith("Unknown command"));
 r = StationConsole.answer("/admin girlpower");
 Setup.chk("G: locked, /admin girlpower is unknown and changes nothing", r.text.startsWith("Unknown command") && !StationConsole.girlPower());
 r = StationConsole.answer("/passtime 3");
 Setup.chk("A: a locked command stays hidden: unknown, nothing passes", r.text.startsWith("Unknown command") && r.days == 0);
 r = StationConsole.answer("  /ADMIN  dc   on ");
 String cfg = new String(SafeFiles.read(HomePlanet.propFile), "UTF-8");
 Setup.chk("A: /admin dc on unlocks them, kept in the cfg, without listing them", StationConsole.devOn() && r.text.equals("Dev commands on.") && cfg.contains("dev_commands=true"));
 Setup.chk("A: /admin ? lists them now, /admin points to it", StationConsole.answer("/admin ?").text.contains("/passtime") && StationConsole.answer("/admin").text.contains("/admin ?"));
 girlPower(v);
 Setup.chk("P: /passtime with no number, 0, too many or words: how to use it, nothing passes",
   StationConsole.answer("/passtime").days == 0 && StationConsole.answer("/passtime 0").days == 0 && StationConsole.answer("/passtime 366").days == 0 && StationConsole.answer("/passtime ten").text.startsWith("Usage"));
 // an expedition due in the span comes home on its own day
 List<CrewState> crew = ExpT.hold(v, "human", "engi");
 Assignments.send(v, Assignments.board(v).get(0).slot, crew, new Random(7));
 int due = Assignments.away(v).get(0).until, now = v.beaconsSeen(), n = due - now + 3;
 r = StationConsole.answer("/passtime " + n);
 Setup.chk("P: /passtime " + n + " asks for " + n + " days", r.days == n);
 StationConsole.noted(n);
 StationConsole.Round round = new StationConsole.Round();
 int home = -1;
 for (int i = 0; i < r.days; i++) { int had = round.back.size(); StationConsole.passDay(v, round); if (round.back.size() > had && home < 0) home = v.beaconsSeen(); }
 Setup.chk("P: exactly " + n + " days passed (" + now + " -> " + v.beaconsSeen() + ")", v.beaconsSeen() == now + n);
 Setup.chk("P: the expedition came home on its own day (due " + due + ", home " + home + "), once", home == due && round.back.size() == 1 && Assignments.away(v).isEmpty());
 Map<Integer, String> why = MasterLog.dayReasons(v);
 int dev = 0; for (String w : why.values()) if (StationConsole.DAY_WHY.equals(w)) dev++;
 Setup.chk("P: each day noted in the master log as passed by a dev command (" + dev + ")", dev == n);
 String hist = Setup.stationLog(v);
 Setup.chk("P: the history log never mentions the dev command (5.23: the debug log only)", !hist.contains("Dev command") && !hist.contains("DEV  "));
 java.lang.reflect.Method page = Class.forName("homeplanet.ui.CaptainsLogDialog").getDeclaredMethod("page", Vault.class, boolean.class); page.setAccessible(true);
 String p = (String) page.invoke(null, v, false);
 Setup.chk("P: the Captain's Log tells the quiet days as quiet, the homecoming on its day, and never the command", p.contains("Nothing to report.") && p.contains("came back from the expedition") && !p.toLowerCase().contains("dev command"));
 stipend(saves);
 r = StationConsole.answer("/admin dc off");
 Setup.chk("A: /admin dc off locks them again", !StationConsole.devOn() && StationConsole.answer("/admin ?").text.startsWith("Unknown command") && StationConsole.answer("/passtime 2").days == 0);
 Setup.done();
}
 /** /admin girlpower (6.22): every crew member the station makes is a woman while it's on; the cfg has the line only then; crew already made are as they were. */
 static void girlPower(Vault v) throws Exception {
  Setup.chk("G: /admin ? lists /admin girlpower", StationConsole.answer("/admin ?").text.contains("/admin girlpower"));
  SavedGameState before = Commission.build("PLAYER_SHIP_HARD", "Before", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(5));
  StationConsole.Reply r = StationConsole.answer("/admin girlpower");
  String cfg = new String(SafeFiles.read(HomePlanet.propFile), "UTF-8");
  Setup.chk("G: on: said, kept in the cfg as girlpower=true", StationConsole.girlPower() && r.text.startsWith("Girl power on") && cfg.contains("girlpower=true"));
  boolean women = true;
  for (String race : new String[] {"human", "engi", "mantis", "rock", "slug", "crystal", "energy", "anaerobic"})
   for (int i = 0; i < 10; i++) { CrewState c = Commission.volunteer(race, new Random(i)); if (c == null || c.isMale()) women = false; }
  for (CrewState c : Commission.build("PLAYER_SHIP_HARD", "After", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(5)).getPlayerShip().getCrewList()) if (c.isMale()) women = false;
  for (CrewState c : Commission.build("PLAYER_SHIP_ROCK", "Rocks", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(6)).getPlayerShip().getCrewList()) if (c.isMale()) women = false;
  Setup.chk("G: on: volunteers of every race and a commissioned ship's crew are all women", women);
  boolean menStill = false; for (int i = 0; i < 10; i++) if (Commission.lookOf("human", new Random(i)).isMale()) menStill = true;
  int beforeMen = 0; for (CrewState c : before.getPlayerShip().getCrewList()) if (c.isMale()) beforeMen++;
  Setup.chk("G: on: the look of someone who already served is unchanged; crew made before keep theirs (" + beforeMen + " men)", menStill && beforeMen > 0);
  String log = SpaceT.log(v);
  Setup.chk("G: logged as a settings change", log.contains("| SETTINGS |") && log.contains("girlpower=true"));
  r = StationConsole.answer("/admin girlpower");
  cfg = new String(SafeFiles.read(HomePlanet.propFile), "UTF-8");
  boolean men = false; for (int i = 0; i < 10; i++) if (Commission.volunteer("human", new Random(i)).isMale()) men = true;
  boolean nonHumanMen = Commission.volunteer("engi", new Random(1)).isMale();
  Setup.chk("G: again: off, the line gone from the cfg, crew rolled as before", !StationConsole.girlPower() && r.text.equals("Girl power off.") && !cfg.contains("girlpower") && men && nonHumanMen
    && SpaceT.log(v).contains("girlpower=false"));
 }
 /** A stipend due partway through a run is issued on its own day, once (5.23: it came only when the run was over). */
 static void stipend(File saves) throws Exception {
  TransT.profile(saves, new String[] {"PLAYER_SHIP_HARD"}, new String[] {"ACH_SECTOR_5"});
  HomePlanet.immersiveMode = true;
  Vault.switchFleet(true);
  UnlockGrants.returning(Unlocks.read());
  Vault v = Vault.get();
  if (!Career.started(v.root)) Career.start(false, false);
  Transmissions.check(); Transmissions.Message owed = TransT.find("stipend:"); if (owed != null) Transmissions.delete(owed); // anything owed already, paid first
  Properties cp = Store.load(Store.file(v.root, "career"));
  int month = Career.beaconsPerStipend(), into = (v.beaconsSeen() - Integer.parseInt(cp.getProperty("beaconsAtStart"))) % month;
  int start = v.beaconsSeen(), dueOn = start + (month - into), n = month - into + 3, issued = -1;
  StationConsole.Round round = new StationConsole.Round();
  for (int i = 0; i < n; i++) { StationConsole.passDay(v, round); if (issued < 0 && TransT.find("stipend:") != null) issued = v.beaconsSeen(); }
  int stipends = 0; for (Transmissions.Message m : Transmissions.load()) if (Transmissions.isStipend(m)) stipends++;
  Setup.chk("P: a stipend due partway through a run is issued on its own day (due " + dueOn + ", issued " + issued + "), once (" + stipends + ")", issued == dueOn && stipends == 1);
  HomePlanet.immersiveMode = false;
 }
}
