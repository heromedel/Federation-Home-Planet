import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The console (5.22): admin commands locked, then unlocked; /passtime passes exactly that many days, each with the station's round. args: gamedir, world saves, work */
public class ConT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive(); HomePlanet.expeditionType = 2;
 HomePlanet.propFile = new File(work, "test.cfg"); HomePlanet.config.remove(StationConsole.DEV);
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 StationConsole.Reply r = StationConsole.answer("/admin");
 Setup.chk("A: locked, /admin answers 'No admin commands available.'", r.text.equals("No admin commands available.") && r.days == 0);
 r = StationConsole.answer("/passtime 3");
 Setup.chk("A: a locked command stays hidden: unknown, nothing passes", r.text.startsWith("Unknown command") && r.days == 0);
 r = StationConsole.answer("  /ADMIN  dc   on ");
 String cfg = new String(SafeFiles.read(HomePlanet.propFile), "UTF-8");
 Setup.chk("A: /admin dc on unlocks them, kept in the cfg, and lists them", StationConsole.devOn() && r.text.contains("/passtime") && cfg.contains("dev_commands=true"));
 Setup.chk("A: /admin lists them now", StationConsole.answer("/admin").text.contains("/passtime"));
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
 String hist = new String(SafeFiles.read(new File(v.root, "history.log")), "UTF-8");
 Setup.chk("P: the history log notes it", hist.contains("DEV  Dev command used: passing " + n + " days"));
 java.lang.reflect.Method page = Class.forName("homeplanet.ui.CaptainsLogDialog").getDeclaredMethod("page", Vault.class, boolean.class); page.setAccessible(true);
 String p = (String) page.invoke(null, v, false);
 Setup.chk("P: the Captain's Log tells the quiet days as quiet, the homecoming on its day, and never the command", p.contains("Nothing to report.") && p.contains("came back from their expedition") && !p.toLowerCase().contains("dev command"));
 r = StationConsole.answer("/admin dc off");
 Setup.chk("A: /admin dc off locks them again", !StationConsole.devOn() && StationConsole.answer("/admin").text.equals("No admin commands available.") && StationConsole.answer("/passtime 2").days == 0);
 Setup.done();
}}
