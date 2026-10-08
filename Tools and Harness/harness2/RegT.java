import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.vault.*;
/** The crew register reads the event log (5.91): its station entries, in the station log's form, are what the old station log said. args: gamedir, world saves (from WorldT), work */
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
 // C: the reputation log from the event log (5.92), as reputation.log wrote it
 HomePlanet.reputationOn = true;
 Reputation.expedition(v, "Nebula, Attack", 24, 0, 2);
 Reputation.captured(v, Arrays.asList("Ash", "Bob"));
 String rep = Reputation.log(v);
 Setup.chk("C: the reputation log from the event log, in its own form: minute, the change, why, details", rep.matches("(?s).*\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d  \\+\\d+  Expedition: Nebula, Attack.*") && rep.contains("Taken captive: Ash, Bob"));
 Setup.chk("C: reputation.log is no longer written (5.93)", !new File(v.logsDir(), "reputation.log").isFile());
 // D (6.01): the station's history read bit by bit is the history read whole; a look rewrites no crew file that didn't change
 for (int i = 0; i < 3; i++) { Ship sh = v.docked().get(i % v.docked().size()); v.board(sh); v.takeStock(); v.dock(); v.takeStock(); }
 String bitByBit = CrewRegister.stationText(v);
 java.lang.reflect.Field whole = CrewRegister.class.getDeclaredField("WHOLE"); whole.setAccessible(true); Object[] w = (Object[]) whole.get(null);
 synchronized (w) { java.util.Arrays.fill(w, null); }
 Setup.chk("D: the station's history read bit by bit, as new entries came, is the same as read whole", bitByBit.equals(CrewRegister.stationText(v)) && bitByBit.length() > 0);
 Map<File, Long> crewTimes = new HashMap<File, Long>(); for (File f : crewFilesUnder(v.root)) crewTimes.put(f, f.lastModified());
 Thread.sleep(1100);
 Ship sh = v.docked().get(0); v.board(sh); v.takeStock(); v.dock(); v.takeStock();
 int rewritten = 0, aboard = 0; for (File f : crewFilesUnder(v.root)) if (crewTimes.containsKey(f) && crewTimes.get(f) != f.lastModified()) rewritten++;
 for (CrewRegister.Member m : CrewRegister.members(v)) if (m.status == CrewRegister.Status.PRESENT && m.where.contains(sh.name)) aboard++;
 Setup.chk("D: a Board and Dock rewrites only her crew's files, not everyone's (" + rewritten + " rewritten, " + aboard + " aboard her, " + crewTimes.size() + " in all)", rewritten <= aboard && crewTimes.size() > aboard);
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
 static List<File> crewFilesUnder(File d) { List<File> out = new ArrayList<File>(); File[] fs = d.listFiles(); if (fs == null) return out;
  for (File f : fs) { if (f.isDirectory()) out.addAll(crewFilesUnder(f)); else if (f.getParentFile().getName().equals("crew") && f.getName().endsWith(".xml")) out.add(f); } return out; }
}
