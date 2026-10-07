import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.vault.*;
/**
 * The old logs read into the event log once (Overhaul 6.0 §3.5, 5.73): a fleet with a station log, a voyage log, a reputation log
 * and days in its master log from before the event log, opened: each old entry gets an event with its kind, its fields, its own
 * time and day, and the old line as its human line; what already had an event is not doubled; it never runs twice. args: game, saves, work dir.
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
   + "2026-01-03 09:00  LOADED  Loaded (startup):\n  Old Glory\n";
 SafeFiles.writeText(v.historyLog(), old + new String(SafeFiles.read(v.historyLog()), "UTF-8"), false);
 File master = new File(logs, "master.log");
 String m = "# master\nD\t2026-01-02 09:00:00\t2\ta jump\nD\t2026-01-02 09:30:00\t3\ta jump (2 counted together)\n"
   + "E\t2026-01-02 10:00:00\t3\tstation\tCOMMISSION  Old Glory  (abc123)\n  The Kestrel\nE\t2026-01-02 10:05:00\t3\tstation\tUNDO REASSIGN  the Cargo Hold and 2 hull(s) returned from surrendered/x\n"
   + "E\t2026-01-02 11:00:00\t3\tvoyage: " + d.name + "\tSector 2 reached (sectors visited: 2)\nE\t2026-01-02 11:01:00\t3\tvoyage: " + d.name + "\tCrew lost: Stoneface (Rock)\n"
   + "E\t2026-01-02 12:00:00\t3\treputation\t+5  Expedition: the rescue of a Zoltan envoy (+5)\n";
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
 Setup.chk("S: the three old station entries are events, kinds normalised, with the old headline and details; the later ones not doubled", kinds.get("COMMISSION") != null && kinds.get("UNDO_REASSIGN") != null && kinds.get("LOADED") != null
   && count(conv, "station") == 3 && kindCount(all, "COMMISSION") == 1 && kindCount(all, "UNDO_REASSIGN") == 1);
 EventLog.Entry com = find(conv, "COMMISSION");
 Setup.chk("S: an entry's own time and stardate from the master log; the old line is its human line", com.time.equals("2026-01-02 10:00:00") && com.day == 3 && com.stardate.equals(MasterLog.stardate(3)) && "Old Glory  (abc123)".equals(com.get("headline"))
   && "The Kestrel (PLAYER_SHIP_HARD), difficulty Easy".equals(com.get("detail.1")) && com.human.equals("Old Glory  (abc123)") && "true".equals(com.get("converted")));
 EventLog.Entry loaded = find(conv, "LOADED");
 Setup.chk("S: an entry the master log never copied has no day of its own (the clock's day stands in)", loaded.time.equals("2026-01-03 09:00:00") && loaded.day >= 1);
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
 Setup.chk("M: logged once with the counts, and marked in logs/", mark != null && mark.num("entries_station", 0) == 3 && mark.num("entries_voyage", 0) == 8 && mark.num("entries_reputation", 0) == 2 && mark.num("days", 0) == 2 && LogConvert.done(v2));
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
 Setup.done();
}
 static int count(List<EventLog.Entry> es, String log) { int n = 0; for (EventLog.Entry e : es) if (log.equals(e.get("log"))) n++; return n; }
 static int kindCount(List<EventLog.Entry> es, String kind) { int n = 0; for (EventLog.Entry e : es) if (e.kind.equals(kind)) n++; return n; }
 static EventLog.Entry find(List<EventLog.Entry> es, String kind) { for (EventLog.Entry e : es) if (e.kind.equals(kind)) return e; return null; }
 static boolean has(List<EventLog.Entry> es, String kind, String k, String val) { for (EventLog.Entry e : es) if (e.kind.equals(kind) && val.equals(e.get(k))) return true; return false; }
 static boolean clean(List<EventLog.Entry> es) { for (EventLog.Entry e : es) if (e.human.matches("(?i).*\\b\\d+ beacons?\\b.*|.*\\bbeacons? \\d.*|(?i).*flagship[^.]*destroy.*")) return false; return true; }
}
