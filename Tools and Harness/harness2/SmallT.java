import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.vault.*;
/** The career's small files as xml, one per concern (5.86): a 5.x fleet's moved on opening, the clock's five files made one, nothing in them changed. args: gamedir, world saves (from WorldT), work */
public class SmallT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 Setup.chk("A: a fleet opened now keeps its clock in clock.xml, and none of the old files", Clock.file(v).isFile() && !new File(v.root, "beacons.txt").exists() && !new File(v.root, "clock.txt").exists());
 // B: a 5.x fleet's files, as 5.85 left them
 Clock.file(v).delete();
 SafeFiles.writeText(new File(v.root, "sectors.txt"), "7\n", false);
 SafeFiles.writeText(new File(v.root, "beacons.txt"), "40\n", false);
 SafeFiles.writeText(new File(v.root, "clock.txt"), "# The boarded ship's progress, as last counted into sectors.txt and beacons.txt\nship=abc\nsector=2\nbeacons=5\n", false);
 SafeFiles.writeText(new File(v.root, "work.txt"), "#Each boarded ship's work\nabc.beacons=5\nabc.work=3\nabc.credited=true\n", false);
 SafeFiles.writeText(new File(v.root, "stardate.txt"), "# The career's day 1\nstart=3\n", false);
 Properties rest = new Properties(); rest.setProperty("last", "23"); rest.setProperty("run", "2");
 Store.file(v.root, "rest").delete(); Store.write(new File(v.root, "rest.txt"), rest, "Captain's Quarters: the last day rested, and the days in a row");
 Properties events = new Properties(); events.setProperty("one-hull", "Lucky Duck & <Co>");
 Store.file(v.root, "events").delete(); Store.write(new File(v.root, "events.txt"), events, "What this fleet has been through");
 Setup.chk("B: before it opens, each owner still finds its 5.x file", Store.file(v.root, "rest").getName().equals("rest.txt"));
 v = Setup.open(game, saves); v.takeStock();
 Properties c = Clock.read(v);
 Setup.chk("B: the clock's five files made one: sectors 7, beacons 40, last counted abc at 2/5, her work, day 1 at 3 (" + c + ")",
   v.sectorsSeen() == 7 && v.beaconsSeen() >= 40 && "abc".equals(c.getProperty("last.ship")) && "2".equals(c.getProperty("last.sector")) && "5".equals(c.getProperty("last.beacons"))
   && "3".equals(c.getProperty("work.abc.work")) && "true".equals(c.getProperty("work.abc.credited")) && MasterLog.start(v) == 3);
 boolean gone = true; for (String n : new String[] {"sectors.txt", "beacons.txt", "clock.txt", "work.txt", "stardate.txt", "rest.txt", "events.txt"}) gone &= !new File(v.root, n).exists();
 Setup.chk("B: the old files gone", gone);
 Setup.chk("B: rest.xml and events.xml hold what their .txt held, the comment kept", Store.load(new File(v.root, "rest.xml")).equals(rest) && Store.load(new File(v.root, "events.xml")).equals(events)
   && new String(SafeFiles.read(new File(v.root, "rest.xml")), "UTF-8").contains("<comment>Captain's Quarters") && "Lucky Duck & <Co>".equals(v.event("one-hull")));
 boolean logged = false; for (EventLog.Entry e : EventLog.read(v)) if (e.kind.equals("SMALL_FILES") && Integer.parseInt(e.get("files", "0")) == 7) logged = true;
 Setup.chk("B: said in the event log, every file named; no protection note left", logged && Journal.dir(v).list().length == 1);
 // C: the clock moves on, written through its one file
 int was = v.beaconsSeen(); v.countBeacon("a test");
 Setup.chk("C: a day passes on the clock, read back at once", v.beaconsSeen() == was + 1 && Clock.num(v, "beacons", 0) == was + 1 && MasterLog.today(v) == was + 1 - 3 + 1);
 Setup.done();
}}
