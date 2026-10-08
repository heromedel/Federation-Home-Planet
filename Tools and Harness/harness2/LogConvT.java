import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.vault.*; import homeplanet.convert.*;
/**
 * The old logs read into the event log once (Overhaul 6.0 §3.5, 5.73): a fleet with a station log, a voyage log, a reputation log
 * and days in its master log from before the event log, opened: each old entry gets an event with its kind, its fields, its own
 * time and day, and the old line as its human line; what already had an event is not doubled; it never runs twice. args: game, saves, work dir.
 * 5.81 (heromedel's Captain's Log): an entry the master log never copied is Prior, not the conversion's day; a common line
 * from before the master log doesn't take a later copy; a received ship's voyage from another station is Prior; and a
 * fleet converted before 5.81 has its converted entries put on their own days, once.
 */
public class LogConvT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 Ship d = v.docked().get(0);
 // the fleet as a 5.62 station left it: old entries with no events, then the events that came with 5.63
 File logs = v.logsDir();
 new File(logs, LogConvert.MARK).delete();
 List<EventLog.Entry> had = EventLog.read(v);
 String firstStation = null; for (EventLog.Entry e : had) if ("station".equals(e.get("log"))) { firstStation = e.time; break; }
 String old = "2026-01-02 10:00  COMMISSION  Old Glory  (abc123)\n  The Kestrel (PLAYER_SHIP_HARD), difficulty Easy\n  Crew: Starter (Human)\n"
   + "2026-01-02 10:05  UNDO REASSIGN  the Cargo Hold and 2 hull(s) returned from surrendered/x\n"
   + "2026-01-03 09:00  LOADED  Loaded (startup):\n  Old Glory\n"
   + "2026-01-01 08:00  LOADED  (refresh)\n  Old Glory  sector 1\n" // before the master log began (out of order on purpose): no copy of its own
   + "2026-01-02 10:10  LOADED  (refresh)\n  Old Glory  sector 1\n"; // its copy is the master log's at 10:10
 SafeFiles.writeText(v.historyLog(), old + (v.historyLog().isFile() ? new String(SafeFiles.read(v.historyLog()), "UTF-8") : ""), false); // a 5.x fleet's station log (5.93 writes none)
 File master = new File(logs, "master.log");
 String m = "# master\nD\t2026-01-02 09:00:00\t2\ta jump\nD\t2026-01-02 09:30:00\t3\ta jump (2 counted together)\n"
   + "E\t2026-01-02 10:00:00\t3\tstation\tCOMMISSION  Old Glory  (abc123)\n  The Kestrel\nE\t2026-01-02 10:05:00\t3\tstation\tUNDO REASSIGN  the Cargo Hold and 2 hull(s) returned from surrendered/x\n"
   + "E\t2026-01-02 11:00:00\t3\tvoyage: " + d.name + "\tSector 2 reached (sectors visited: 2)\nE\t2026-01-02 11:01:00\t3\tvoyage: " + d.name + "\tCrew lost: Stoneface (Rock)\n"
   + "E\t2026-01-02 12:00:00\t3\treputation\t+5  Expedition: the rescue of a Zoltan envoy (+5)\n"
   + "E\t2026-01-02 10:10:31\t3\tstation\tLOADED  (refresh) / Old Glory  sector 1\n";
 SafeFiles.writeText(master, m + (master.isFile() ? new String(SafeFiles.read(master), "UTF-8") : ""), false);
 File vl = new File(v.historyOf(d), "voyage.log");
 SafeFiles.writeText(vl, "2026-01-02 11:00  Sector 2 reached (sectors visited: 2)\n2026-01-02 11:01  Crew lost: Stoneface (Rock)\n2026-01-02 11:02  Crew joined: Bob (Human), Gracie Quill (Engi)\n"
   + "2026-01-02 11:03  Jumped, hull 28/30 (-2), scrap 61 (+14), fuel 12, missiles 4, drone parts 2\n2026-01-02 11:04  Aboard now: Burst Laser II, Defense Drone I\n2026-01-02 11:05  Commissioned at The Home Planet Station\n"
   + "2026-01-02 11:06  " + VoyageLog.NEW_JOURNEY + "\n2026-01-02 11:07  3 ships defeated (41 in all)\n" + (vl.isFile() ? new String(SafeFiles.read(vl), "UTF-8") : ""), false);
 File rl = new File(logs, "reputation.log");
 SafeFiles.writeText(rl, "2026-01-02 12:00  +5  Expedition: the rescue of a Zoltan envoy (+5)\n  Gracie Quill led it\n2026-01-02 12:30  −1  Rested in quarters again, a second day (−1)\n" + (rl.isFile() ? new String(SafeFiles.read(rl), "UTF-8") : ""), false);
 Vault v2 = Vault.open(saves); v2.takeStock();
 List<EventLog.Entry> all = EventLog.read(v2);
 List<EventLog.Entry> conv = new ArrayList<EventLog.Entry>(); for (EventLog.Entry e : all) if ("true".equals(e.get("converted"))) conv.add(e);
 Map<String, Integer> kinds = new TreeMap<String, Integer>(); for (EventLog.Entry e : conv) kinds.put(e.kind, (kinds.containsKey(e.kind) ? kinds.get(e.kind) : 0) + 1);
 System.out.println("  converted: " + kinds);
 Setup.chk("S: the old station entries are events, kinds normalised, with the old headline and details; the later ones not doubled", kinds.get("COMMISSION") != null && kinds.get("UNDO_REASSIGN") != null && kinds.get("LOADED") != null
   && count(conv, "station") == 5 && kindCount(all, "COMMISSION") == 1 && kindCount(all, "UNDO_REASSIGN") == 1);
 EventLog.Entry com = find(conv, "COMMISSION");
 Setup.chk("S: an entry's own time and stardate from the master log; the old line is its human line", com.time.equals("2026-01-02 10:00:00") && com.day == 3 && com.stardate.equals(MasterLog.stardate(3)) && "Old Glory  (abc123)".equals(com.get("headline"))
   && "The Kestrel (PLAYER_SHIP_HARD), difficulty Easy".equals(com.get("detail.1")) && com.human.equals("Old Glory  (abc123)") && "true".equals(com.get("converted")));
 EventLog.Entry loaded = at(conv, "LOADED", "2026-01-03 09:00:00"), early = at(conv, "LOADED", "2026-01-01 08:00:00"), later = at(conv, "LOADED", "2026-01-02 10:10:00");
 Setup.chk("S: an entry the master log never copied is Prior, not put on the conversion's day (5.81; it was the clock's day)", loaded != null && loaded.day == 0 && loaded.stardate.equals("prior"));
 Setup.chk("S: a common line from before the master log takes no later copy, and the later one keeps its own day (" + (early == null ? "?" : early.day) + ", " + (later == null ? "?" : later.day) + ")",
   early != null && later != null && early.day == 0 && later.day == 3);
 boolean outOfLog = true; for (List<MasterLog.Entry> l : MasterLog.byDay(v2).values()) for (MasterLog.Entry e : l) if (e.real.startsWith("2026-01-01 08:00") || e.real.startsWith("2026-01-03 09:00")) outOfLog = false;
 Setup.chk("S: Prior entries stay out of the Captain's Log's days", outOfLog);
 Setup.chk("V: the voyage lines: sector reached with its fields, crew lost and joined with each name and race, a jump, the items aboard, a new run, ships defeated, and a note for the rest",
   has(conv, "SECTOR_REACHED", "sector", "2") && has(conv, "SECTOR_REACHED", "visited", "2") && has(conv, "CREW_LOST", "crew", "Stoneface") && has(conv, "CREW_LOST", "race", "Rock")
   && find(conv, "CREW_JOINED").all("crew").equals(Arrays.asList("Bob", "Gracie Quill")) && find(conv, "CREW_JOINED").all("race").equals(Arrays.asList("Human", "Engi"))
   && find(conv, "JUMPED") != null && find(conv, "ITEMS_ABOARD").all("item").equals(Arrays.asList("Burst Laser II", "Defense Drone I")) && has(conv, "VOYAGE_NOTE", "text", "Commissioned at The Home Planet Station")
   && has(conv, "NEW_RUN", "sector", "1") && has(conv, "SHIPS_DEFEATED", "total", "41") && count(conv, "voyage") == 8);
 EventLog.Entry lost = find(conv, "CREW_LOST");
 Setup.chk("V: a voyage entry names her, keeps the old line, and has its day from the master log", (d.name + "." + d.id).equals(lost.get("ship")) && d.id.equals(lost.get("ship_id")) && lost.human.equals("Crew lost: Stoneface (Rock)") && lost.day == 3);
 EventLog.Entry rep = null; for (EventLog.Entry e : conv) if (e.kind.equals("REPUTATION") && e.num("points", 0) == 5) rep = e;
 EventLog.Entry rep2 = null; for (EventLog.Entry e : conv) if (e.kind.equals("REPUTATION") && e.num("points", 0) == -1) rep2 = e;
 Setup.chk("R: the reputation entries: the points (a minus sign as the log writes it), why, the details, the day", rep != null && rep2 != null && rep.get("detail.1").equals("Gracie Quill led it") && rep.human.startsWith("Expedition:") && rep.day == 3 && rep2.human.startsWith("Rested"));
 List<EventLog.Entry> days = new ArrayList<EventLog.Entry>(); for (EventLog.Entry e : conv) if (e.kind.equals("DAY")) days.add(e);
 Setup.chk("D: the clock's old days: one event each, with the day and why, the human line without the count", days.size() == 2 && days.get(0).day == 2 && "a jump".equals(days.get(0).get("why")) && days.get(1).day == 3 && days.get(1).human.equals("A day passed: a jump."));
 EventLog.Entry mark = find(all, "LOGS_CONVERTED");
 Setup.chk("M: logged once with the counts, and marked in logs/", mark != null && mark.num("entries_station", 0) == 5 && mark.num("entries_voyage", 0) == 8 && mark.num("entries_reputation", 0) == 2 && mark.num("days", 0) == 2 && LogConvert.done(v2));
 int before = EventLog.read(v2).size();
 Vault v3 = Vault.open(saves); v3.takeStock();
 int convAgain = 0; for (EventLog.Entry e : EventLog.read(v3)) if ("true".equals(e.get("converted"))) convAgain++;
 Setup.chk("M: never twice: opened again, nothing is converted again", convAgain == conv.size() && EventLog.read(v3).size() <= before + 3);
 Setup.chk("H: no converted human line counts beacons or destroys the Rebel Flagship", clean(conv));
 // her own log (5.76): every entry that names her, the converted ones among them, and each new one as it's written
 List<EventLog.Entry> hers = ShipStore.entries(v3.folderOf(v3.byId(d.id)));
 int voyageHers = 0; for (EventLog.Entry e : hers) if ("voyage".equals(e.get("log"))) voyageHers++;
 Setup.chk("L: her folder's log holds her voyage entries, the converted ones too (" + voyageHers + ")", voyageHers >= 8 && find(hers, "CREW_LOST") != null && find(hers, "SECTOR_REACHED") != null && EventLog.voyage(EventLog.read(v3), d.id).size() == voyageHers);
 Setup.voyage(v3, v3.byId(d.id), "A line of her own");
 Setup.chk("L: a new entry goes into her log and the fleet's alike", ShipStore.entries(v3.folderOf(v3.byId(d.id))).size() == hers.size() + 1 && EventLog.voyage(EventLog.read(v3), d.id).size() == voyageHers + 1);
 Setup.chk("L: filled once: marked, and not doubled on a later opening", "true".equals(Store.read(new File(v3.logsDir(), LogConvert.MARK)).getProperty("ship_logs")));
 // a ship from another station brings her voyage log (5.75): her lines are Prior here, not this career's today (5.81)
 OldPackage.voyage(v3, v3.byId(d.id), "Commander Elsewhere", "2025-12-01 10:00  Sector 3 reached (sectors visited: 3)\n2025-12-01 10:05  Crew joined: Farhand (Mantis)\n");
 EventLog.Entry came = null; for (EventLog.Entry e : EventLog.read(v3)) if ("Commander Elsewhere".equals(e.get("received_from")) && e.kind.equals("CREW_JOINED")) came = e;
 Setup.chk("I: a received ship's old voyage lines are Prior here, in the fleet's log", came != null && came.day == 0 && came.stardate.equals("prior"));
 // a fleet converted before 5.81: its converted entries put on the conversion's day, as 5.73 to 5.80 did
 int today = MasterLog.today(v3);
 File events = EventLog.file(v3), herLog = ShipStore.logFile(v3.folderOf(v3.byId(d.id)));
 for (File f : new File[] {events, herLog}) SafeFiles.writeText(f, onDay(new String(SafeFiles.read(f), "UTF-8"), today), false);
 File markFile = new File(v3.logsDir(), LogConvert.MARK);
 Properties mp = Store.read(markFile); mp.remove("days_fixed"); Store.write(markFile, mp, "as 5.80 left it");
 Setup.chk("F: the broken state is in place (the commission on day " + at(EventLog.read(v3), "COMMISSION", "2026-01-02 10:00:00").day + ")", at(EventLog.read(v3), "COMMISSION", "2026-01-02 10:00:00").day == today && today != 3);
 Vault v4 = Vault.open(saves); v4.takeStock();
 List<EventLog.Entry> fixedAll = EventLog.read(v4);
 EventLog.Entry fixedCom = at(fixedAll, "COMMISSION", "2026-01-02 10:00:00"), fixedEarly = at(fixedAll, "LOADED", "2026-01-01 08:00:00"), fixedLater = at(fixedAll, "LOADED", "2026-01-02 10:10:00"), fixedCame = null;
 for (EventLog.Entry e : fixedAll) if ("Commander Elsewhere".equals(e.get("received_from")) && e.kind.equals("CREW_JOINED")) fixedCame = e;
 Setup.chk("F: opened at 5.81, each converted entry is back on its own day: the commission on 3, the early refresh Prior, the later one on 3, the received voyage Prior",
   fixedCom != null && fixedCom.day == 3 && fixedCom.stardate.equals(MasterLog.stardate(3)) && fixedEarly != null && fixedEarly.day == 0 && fixedEarly.stardate.equals("prior")
   && fixedLater != null && fixedLater.day == 3 && fixedCame != null && fixedCame.day == 0);
 EventLog.Entry hersLost = find(ShipStore.entries(v4.folderOf(v4.byId(d.id))), "CREW_LOST");
 Setup.chk("F: her own log too (crew lost on day " + (hersLost == null ? "?" : hersLost.day) + ")", hersLost != null && hersLost.day == 3);
 EventLog.Entry rep2b = null; for (EventLog.Entry e : fixedAll) if (e.kind.equals("REPUTATION") && e.num("points", 0) == -1 && "true".equals(e.get("converted"))) rep2b = e;
 int convBroken = 0, convFixed = 0; for (EventLog.Entry e : EventLog.read(v3)) if ("true".equals(e.get("converted"))) convBroken++; for (EventLog.Entry e : fixedAll) if ("true".equals(e.get("converted"))) convFixed++;
 Setup.chk("F: no converted entry added or lost (" + convBroken + " -> " + convFixed + "), the uncopied reputation entry Prior", convFixed == convBroken && rep2b != null && rep2b.day == 0);
 EventLog.Entry fixedMark = find(fixedAll, "LOG_DAYS_REPAIRED");
 Setup.chk("F: logged once with the counts (" + (fixedMark == null ? "none" : fixedMark.get("entries_moved") + " moved, " + fixedMark.get("entries_prior") + " Prior") + "), through the journal (no note left), and marked",
   fixedMark != null && fixedMark.num("entries_moved", 0) > 0 && fixedMark.num("entries_prior", 0) > 0 && fixedMark.num("entries_unmatched", -1) == 0 && Store.read(markFile).getProperty("days_fixed") != null
   && Journal.dir(v4).list().length == 1);
 Vault v5 = Vault.open(saves); v5.takeStock();
 Setup.chk("F: never twice", kindCount(EventLog.read(v5), "LOG_DAYS_REPAIRED") == 1);
 Setup.done();
}
 static int count(List<EventLog.Entry> es, String log) { int n = 0; for (EventLog.Entry e : es) if (log.equals(e.get("log"))) n++; return n; }
 static int kindCount(List<EventLog.Entry> es, String kind) { int n = 0; for (EventLog.Entry e : es) if (e.kind.equals(kind)) n++; return n; }
 static EventLog.Entry find(List<EventLog.Entry> es, String kind) { for (EventLog.Entry e : es) if (e.kind.equals(kind)) return e; return null; }
 static EventLog.Entry at(List<EventLog.Entry> es, String kind, String time) { for (EventLog.Entry e : es) if (e.kind.equals(kind) && e.time.equals(time)) return e; return null; }
 /** Every converted entry put on this day, as 5.73 to 5.80 put the ones they couldn't place. */
 static String onDay(String text, int day) {
  StringBuilder sb = new StringBuilder();
  for (String l : text.split("\n", -1)) {
   if (l.contains("converted=true") && l.indexOf(" | ") == 19) {
    String[] h = l.split(" \\| ", 4);
    l = h[0] + " | " + MasterLog.stardate(day) + " | " + h[2] + " | " + h[3].replaceFirst("(^| )day=-?\\d+", "$1day=" + day);
   }
   sb.append(l).append('\n');
  }
  return sb.substring(0, sb.length() - 1);
 }
 static boolean has(List<EventLog.Entry> es, String kind, String k, String val) { for (EventLog.Entry e : es) if (e.kind.equals(kind) && val.equals(e.get(k))) return true; return false; }
 static boolean clean(List<EventLog.Entry> es) { for (EventLog.Entry e : es) if (e.human.matches("(?i).*\\b\\d+ beacons?\\b.*|.*\\bbeacons? \\d.*|(?i).*flagship[^.]*destroy.*")) return false; return true; }
}
