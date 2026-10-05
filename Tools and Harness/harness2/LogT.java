import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The master log and the Captain's Log (5.17): stardates, day lines and entry lines, Prior, the Cargo Bay's day. args: gamedir, world saves, work */
public class LogT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive(); HomePlanet.reputationOn = false;
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 Setup.chk("S: stardates: 1.1.1.1, a week on 1.1.2.1, a month on 1.2.1.1, a year on 2.1.1.1; before day 1, Prior",
   MasterLog.stardate(1).equals("1.1.1.1") && MasterLog.stardate(8).equals("1.1.2.1") && MasterLog.stardate(29).equals("1.2.1.1") && MasterLog.stardate(365).equals("2.1.1.1")
   && MasterLog.stardate(0).equals("Prior to 1.1.1.1") && MasterLog.stardate(-1).equals("Prior to 1.1.1.1"));
 Setup.chk("S: the fleet's day 1 is today (its first look on 5.17), written down once", MasterLog.today(v) == 1 && new File(v.root, "stardate.txt").isFile() && MasterLog.start(v) == v.beaconsSeen());
 HistoryLog.entry("CREW", "Ash signed on");
 HistoryLog.entry("LOADED", "(refresh)", Arrays.asList("a ship line"));
 Rest.rest(v);
 Setup.chk("S: a day of rest: day 2, noted with why", MasterLog.today(v) == 2 && "a day of rest in your quarters".equals(MasterLog.lastDayWhy(v)));
 HistoryLog.entry("EXPEDITION", "Ash sent to the Nebula");
 Map<Integer, List<MasterLog.Entry>> days = MasterLog.byDay(v);
 boolean ash1 = false, ash2 = false;
 for (MasterLog.Entry e : days.get(1)) if (e.text.contains("Ash signed on")) ash1 = true;
 for (MasterLog.Entry e : days.get(2)) if (e.text.contains("Ash sent to the Nebula")) ash2 = true;
 Setup.chk("S: every station log entry is copied, on its day, with the real time", ash1 && ash2 && days.get(1).get(0).real.matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d"));
 // Prior: no day, 0 or -1 are never in the Captain's Log
 FileOutputStream o = new FileOutputStream(new File(v.root, "master.log"), true);
 o.write("E\t2026-01-01 00:00:00\t0\tstation\tCREW  zero day\nE\t2026-01-01 00:00:00\t-1\tstation\tCREW  minus day\nE\t2026-01-01 00:00:00\t\tstation\tCREW  no day\nE\tbroken\n".getBytes("UTF-8")); o.close();
 boolean prior = false; for (List<MasterLog.Entry> l : MasterLog.byDay(v).values()) for (MasterLog.Entry e : l) if (e.text.contains(" day")) prior = true;
 Setup.chk("S: an entry with no day, 0 or -1 is Prior: left out", !prior);
 // the Cargo Bay's day: once, then not again until something else moves the clock
 int c0 = v.beaconsSeen();
 boolean first = MasterLog.businessDay(v), second = MasterLog.businessDay(v);
 Setup.chk("C: business in the Cargo Bay passes a day; a second Save right after passes none", first && !second && v.beaconsSeen() == c0 + 1);
 Rest.rest(v);
 Setup.chk("C: after something else moves the clock, it counts again", MasterLog.businessDay(v) && v.beaconsSeen() == c0 + 3);
 // the page
 java.lang.reflect.Method page = Class.forName("homeplanet.ui.CaptainsLogDialog").getDeclaredMethod("page", Vault.class); page.setAccessible(true);
 String p = (String) page.invoke(null, v);
 Setup.chk("L: the Captain's Log opens on 'Captains Log: Stardate Today', then 'StarDate TD 1.1.1.2'", p.contains("-- Captain's Log --") && p.contains("Captains Log: Stardate Today") && p.contains("StarDate TD 1.1.1.2") && p.indexOf("Stardate Today") < p.indexOf("TD 1.1.1.2"));
 Setup.chk("L: the story, not the housekeeping, nor why a day passed", p.contains("Ash signed on") && !p.contains("a ship line") && !p.contains("(refresh)") && !p.contains("day of rest in your quarters") && !p.contains(MasterLog.CARGO_BAY) && !p.toLowerCase().contains("beacon"));
 Setup.done();
}}
