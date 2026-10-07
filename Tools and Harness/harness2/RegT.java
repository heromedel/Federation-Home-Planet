import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.vault.*;
/** The crew register reads the event log (5.91): its station entries, in the station log's form, are what the old station log said; a register from before carries its place across. args: gamedir, world saves (from WorldT), work */
public class RegT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 HistoryLog.entry("LONG RANGE TRADE", "with Commander Bree  (trade #7)", Arrays.asList("gave: Ash (Human), Bob (Rockman)", "received: Cy (Engi)"));
 HistoryLog.entry("RENAME", "Old Glory -> New Glory  (abc123)");
 List<String> was = lines(new String(SafeFiles.read(v.historyLog()), "UTF-8")), now = lines(CrewRegister.stationText(v));
 int diff = -1; for (int i = 0; i < Math.max(was.size(), now.size()); i++) if (i >= was.size() || i >= now.size() || !was.get(i).equals(now.get(i))) { diff = i; break; }
 if (diff >= 0) System.out.println("  first difference at line " + diff + ":\n    old: " + (diff < was.size() ? was.get(diff) : "-") + "\n    new: " + (diff < now.size() ? now.get(diff) : "-"));
 Setup.chk("A: the station entries from the event log read as the station log wrote them (" + was.size() + " lines)", diff < 0 && was.size() > 3);
 // B: a register from before 5.91, its place in the old logs by character offset
 File reg = CrewRegister.registerFileOf(v);
 Properties p = Store.load(reg);
 String hist = new String(SafeFiles.read(v.historyLog()), "UTF-8"), master = new String(SafeFiles.read(new File(v.logsDir(), "master.log")), "UTF-8");
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
 String lastMinute = null; for (String l : lines(hist)) if (l.length() > 16 && Character.isDigit(l.charAt(0))) lastMinute = l.substring(0, 16);
 String tail = new String(SafeFiles.read(EventLog.file(v)), "UTF-8").substring(at);
 Setup.chk("B: what follows its place starts in the minute it had reached (" + lastMinute + ")", tail.startsWith(lastMinute));
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
 File rl = new File(v.logsDir(), "reputation.log");
 List<String> rWas = rl.isFile() ? rawLines(new String(SafeFiles.read(rl), "UTF-8")) : new ArrayList<String>(), rNow = rawLines(Reputation.log(v));
 Setup.chk("C: the reputation log read from the event log is reputation.log, line for line (" + rWas.size() + " lines)" + (rWas.equals(rNow) ? "" : "\n    old " + rWas + "\n    new " + rNow), rWas.equals(rNow) && !rWas.isEmpty());
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
