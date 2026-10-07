import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.vault.*;
/** The crew register reads the event log (5.91): its station entries, in the station log's form, are what the old station log said; a register from before carries its place across. args: gamedir, world saves (from WorldT), work */
public class RegT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 HistoryLog.entry("LONG RANGE TRADE", "with Commander Bree  (trade #7)", Arrays.asList("gave: Ash (Human), Bob (Rockman)", "received: Cy (Engi)"));
 HistoryLog.entry("RENAME", "Old Glory -> New Glory  (abc123)");
 String st = CrewRegister.stationText(v);
 Setup.chk("A: the station entries from the event log in the station log's form: minute, kind, headline, then each detail (" + st.split("\n").length + " lines)",
   st.matches("(?s).*\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d  LONG RANGE TRADE  with Commander Bree  \\(trade #7\\)\n  gave: Ash \\(Human\\), Bob \\(Rockman\\)\n  received: Cy \\(Engi\\)\n.*")
   && st.contains("  RENAME  Old Glory -> New Glory  (abc123)\n"));
 Setup.chk("A: history.log is no longer written (5.93)", !v.historyLog().isFile() || !new String(SafeFiles.read(v.historyLog()), "UTF-8").contains("Commander Bree"));
 // B: a register from before 5.91, its place in the old logs by character offset
 File reg = CrewRegister.registerFileOf(v);
 Properties p = Store.load(reg);
 // the old logs as a 5.90 fleet had them: written here by hand, since the station no longer writes them
 String hist = "2026-01-01 10:00  HIRE  Ash (Human)\n2026-01-01 10:05  CREW  Ash assigned to the Cargo Hold.\n", master = "E\t2026-01-01 10:00:00\t1\tstation\tHIRE  Ash (Human)\n";
 SafeFiles.writeText(v.historyLog(), hist, false); SafeFiles.writeText(new File(v.logsDir(), "master.log"), master, false);
 int eventsBefore = new String(SafeFiles.read(EventLog.file(v)), "UTF-8").length();
 p.remove("seen.at"); p.setProperty("seen.hist", Integer.toString(hist.length())); p.setProperty("seen.master", Integer.toString(master.length()));
 Store.write(reg, p, null);
 for (CrewRegister.Member m : CrewRegister.members(v)) {
  File f = CrewRegister.fileOf(v, m.id); Properties q = new Properties(); InputStream in = new FileInputStream(f); q.loadFromXML(in); in.close();
  q.remove("at"); q.setProperty("hist", Integer.toString(hist.length())); q.setProperty("master", Integer.toString(master.length()));
  OutputStream out = new FileOutputStream(f); q.storeToXML(out, null); out.close();
 }
 CrewRegister.convertPositions(v);
 Properties after = Store.load(reg);
 int at = Integer.parseInt(after.getProperty("seen.at", "-1"));
 Setup.chk("B: its place carried across, at or before where it had read to (" + at + " of " + eventsBefore + "), never past it", at >= 0 && at <= eventsBefore && after.getProperty("seen.hist") == null);
 Setup.chk("B: its place is at the first entry of the minute it had reached, or later (2026-01-01 10:05: every entry of the event log is later)", at == 0 || new String(SafeFiles.read(EventLog.file(v)), "UTF-8").substring(at).compareTo("2026-01-01 10:05") >= 0);
 boolean every = true; for (CrewRegister.Member m : CrewRegister.members(v)) { File f = CrewRegister.fileOf(v, m.id); Properties q = new Properties(); InputStream in = new FileInputStream(f); q.loadFromXML(in); in.close(); every &= q.getProperty("at") != null && q.getProperty("hist") == null; }
 Setup.chk("B: each crew member's place too", every);
 boolean logged = false; for (EventLog.Entry e : EventLog.read(v)) if (e.kind.equals("CREW_FILES") && "positions".equals(e.get("what"))) logged = true;
 Setup.chk("B: said in the event log", logged);
 int before = 0; for (EventLog.Entry e : EventLog.read(v)) if (e.kind.equals("CREW_FILES") && "positions".equals(e.get("what"))) before++;
 CrewRegister.convertPositions(v);
 int count = -before; for (EventLog.Entry e : EventLog.read(v)) if (e.kind.equals("CREW_FILES") && "positions".equals(e.get("what"))) count++;
 Setup.chk("B: once only", count == 0);
 v.takeStock();
 // C: the reputation log from the event log (5.92), as reputation.log wrote it
 HomePlanet.reputationOn = true;
 Reputation.expedition(v, "Nebula, Attack", 24, 0, 2);
 Reputation.captured(v, Arrays.asList("Ash", "Bob"));
 String rep = Reputation.log(v);
 Setup.chk("C: the reputation log from the event log, in its own form: minute, the change, why, details", rep.matches("(?s).*\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d  \\+\\d+  Expedition: Nebula, Attack.*") && rep.contains("Taken captive: Ash, Bob"));
 Setup.chk("C: reputation.log is no longer written (5.93)", !new File(v.logsDir(), "reputation.log").isFile());
 Setup.done();
}
 /** A log's lines, its kinds as the event log keeps them (underscores read as spaces), the time to the minute. */
 static List<String> lines(String text) {
  List<String> out = new ArrayList<String>();
  for (String l : text.replace("\r", "").split("\n")) {
   if (l.isEmpty()) continue;
   if (l.startsWith("  ")) l = "  " + l.trim(); // a detail's own indent: the 5.73 conversion kept details trimmed, and the register's readers trim them too
   if (l.length() > 18 && Character.isDigit(l.charAt(0))) { int k = l.indexOf("  ", 18); String kind = k < 0 ? l.substring(18) : l.substring(18, k); l = l.substring(0, 18) + kind.replace('_', ' ').toUpperCase().replaceAll("[^A-Z0-9]+", " ").trim() + (k < 0 ? "" : l.substring(k)); }
   out.add(l);
  }
  return out;
 }
 /** A log's lines as they are, a detail's own indent aside (the 5.73 conversion kept details trimmed). */
 static List<String> rawLines(String text) {
  List<String> out = new ArrayList<String>();
  for (String l : text.replace("\r", "").split("\n")) { if (l.isEmpty()) continue; out.add(l.startsWith("  ") ? "  " + l.trim() : l); }
  return out;
 }
}
