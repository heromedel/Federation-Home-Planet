import java.io.*; import java.util.*; import java.util.regex.*; import homeplanet.core.*; import homeplanet.vault.*;
/**
 * The event log (Overhaul 6.0, Phase 1 step 9): the two-line entries, the one parser, and every events.log the other
 * tests left behind checked against docs/EVENTS.md and the hard rules. Runs last. args: game, saves, work dir.
 */
public class EventT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 // the lines, taken apart again
 Event e = Event.of("rename crew").put("crew", "Bob.17").put("from", "Sgt. \"Lucky\" Duck").put("path", "C:\\Users\\x").put("pipe", "a|b").put("eq", "k=v")
   .put("empty", "").put("lines", "one\ntwo").put("tab", "a\tb").put("unicode", "Zoltan \u2212 Lanius").put("n", 7).put("item", "A").put("item", "B").put("none", null)
   .human("Bob became \"Lucky\" Duck.");
 Setup.chk("K: the kind, normalised", e.kind.equals("RENAME_CREW") && Event.normalize("Long Range  Comm.").equals("LONG_RANGE_COMM_"));
 String machine = "2026-10-07 14:02:11 | 1.2.3.4 | " + e.kind + " | " + e.get("crew") + "x";
 List<EventLog.Entry> one = EventLog.parse("2026-10-07 14:02:11 | 1.2.3.4 | RENAME_CREW | " + fieldsOf(e) + " day=43 station=5.63\n" + e.human() + "\n");
 Setup.chk("P: one entry, its head read", one.size() == 1 && one.get(0).kind.equals("RENAME_CREW") && one.get(0).time.equals("2026-10-07 14:02:11") && one.get(0).stardate.equals("1.2.3.4") && one.get(0).day == 43);
 EventLog.Entry x = one.get(0);
 Setup.chk("P: quoted values back as they were", x.get("from").equals("Sgt. \"Lucky\" Duck") && x.get("path").equals("C:\\Users\\x") && x.get("pipe").equals("a|b") && x.get("eq").equals("k=v"));
 Setup.chk("P: empty, line breaks, tabs, unicode", x.get("empty").equals("") && x.get("lines").equals("one\ntwo") && x.get("tab").equals("a\tb") && x.get("unicode").equals("Zoltan \u2212 Lanius"));
 Setup.chk("P: numbers, lists, missing", x.num("n", 0) == 7 && x.all("item").equals(Arrays.asList("A", "B")) && x.get("none") == null && x.num("none", -1) == -1);
 Setup.chk("P: the human line", x.human.equals("Bob became \"Lucky\" Duck."));
 Setup.chk("P: a machine line with no human line, and stray lines, are taken in stride",
   EventLog.parse("junk\n2026-10-07 14:02:11 | prior | A | k=v\n2026-10-07 14:02:12 | 1.1.1.1 | B |\nhuman b\n\n").size() == 2
   && EventLog.parse("2026-10-07 14:02:11 | prior | A | k=v\n2026-10-07 14:02:12 | 1.1.1.1 | B |\nhuman b\n").get(0).human.isEmpty());
 // written to a fleet
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 int before = EventLog.read(v).size();
 EventLog.write(v, Event.of("TEST").put("what", "a test entry").human("A test entry."));
 HistoryLog.entry("TEST TWO", "two  (x)", Arrays.asList("d1", "d2"));
 HistoryLog.entry("TEST THREE", "three", null, Event.of("TEST_THREE").put("ship", "Kestrel.a3f2").human("The Kestrel: three."));
 List<EventLog.Entry> all = EventLog.read(v);
 Setup.chk("W: three entries more, in order, each with day and station", all.size() == before + 3 && all.get(before).kind.equals("TEST") && all.get(before + 1).kind.equals("TEST_TWO") && all.get(before + 2).kind.equals("TEST_THREE")
   && all.get(before).day >= 1 && all.get(before).get("station").equals(HomePlanet.version()));
 Setup.chk("W: the station log's bridge keeps the headline and details as fields, and the headline as the human line",
   all.get(before + 1).get("headline").equals("two  (x)") && all.get(before + 1).get("detail.2").equals("d2") && all.get(before + 1).human.equals("two  (x)") && all.get(before + 1).get("log").equals("station"));
 Setup.chk("W: an event given to the station log keeps its fields and its human line", all.get(before + 2).get("ship").equals("Kestrel.a3f2") && all.get(before + 2).human.equals("The Kestrel: three."));
 Setup.chk("W: a day's entry", countKind(all, "DAY") >= 1 || v.beaconsSeen() == 0);
 // every events.log the harness wrote, against the list and the hard rules
 File repo = work.getAbsoluteFile().getParentFile().getParentFile().getParentFile().getParentFile();
 File doc = new File(repo, "docs/EVENTS.md");
 Set<String> documented = new TreeSet<String>();
 Matcher m = Pattern.compile("`([A-Z][A-Z0-9_]+)`").matcher(new String(SafeFiles.read(doc), "UTF-8"));
 while (m.find()) documented.add(m.group(1));
 Set<String> seen = new TreeSet<String>(), undocumented = new TreeSet<String>();
 List<String> bad = new ArrayList<String>();
 int entries = 0, logs = 0;
 Pattern beacons = Pattern.compile("(?i)\\b\\d+ beacons?\\b|\\bbeacons? \\d"), destroyed = Pattern.compile("(?i)flagship[^.]*destroy|destroy[^.]*flagship");
 for (File f : findLogs(work.getAbsoluteFile().getParentFile())) {
  logs++;
  for (EventLog.Entry en : EventLog.read(f)) {
   entries++; seen.add(en.kind);
   if (!en.kind.startsWith("TEST") && !documented.contains(en.kind)) undocumented.add(en.kind);
   if (en.human.isEmpty() || en.get("day") == null || en.get("station") == null) bad.add(f.getParentFile().getName() + ": " + en.kind + " lacks a human line, day or station");
   if (beacons.matcher(en.human).find()) bad.add("beacon count in a human line: " + en.human);
   if (destroyed.matcher(en.human).find()) bad.add("the Rebel Flagship destroyed in a human line: " + en.human);
   if (en.human.contains("\n")) bad.add("a human line of more than one line: " + en.kind);
  }
 }
 System.out.println(logs + " event logs, " + entries + " entries, " + seen.size() + " kinds: " + seen);
 Setup.chk("H: every kind the tests wrote is in docs/EVENTS.md" + (undocumented.isEmpty() ? "" : ": " + undocumented), undocumented.isEmpty() && entries > 50);
 for (String b : bad) System.out.println("  " + b);
 Setup.chk("H: every entry has its human line, day and station; no human line counts beacons or destroys the Rebel Flagship", bad.isEmpty());
 Setup.done();
}
 static String fieldsOf(Event e) { StringBuilder sb = new StringBuilder(); for (String[] f : e.fields()) sb.append(sb.length() == 0 ? "" : " ").append(f[0]).append('=').append(Event.quote(f[1])); return sb.toString(); }
 static int countKind(List<EventLog.Entry> all, String k) { int n = 0; for (EventLog.Entry e : all) if (e.kind.equals(k)) n++; return n; }
 static List<File> findLogs(File dir) {
  List<File> out = new ArrayList<File>();
  File[] fs = dir.listFiles(); if (fs == null) return out;
  for (File f : fs) { if (f.isDirectory()) out.addAll(findLogs(f)); else if (f.getName().equals(EventLog.FILE)) out.add(f); }
  return out;
 }
}
