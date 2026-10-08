import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*; import homeplanet.convert.*;
/**
 * The kill test (Overhaul 6.0 step 25a, Buggy Boy, 5.95): the station, in its own process (KillPeer), is stopped dead in
 * the middle of an action that moves or writes more than one file, as a crash, a closed window or a power cut would
 * stop it; then the fleet is opened again here, which finishes or undoes any protection note left behind, and taken
 * stock of. Every ship, crew member and item must be as before the action or as after it, never half of each, and
 * nothing may be wrong (FleetLedger: a ship or crew file twice, a record without its save, a note left, half a log entry).
 * Each action is stopped just before each file it writes, renames or deletes, every one in turn, and at random moments
 * (a fixed seed, so a failure can be run again). Actions: a Cargo Bay save, a ship received in a trade, an expedition's
 * return, Board, Dock, Decommission, and a fleet from before 6.0 converted on opening (5.992: opened again, a stopped
 * conversion must come out as a whole one does, its event log too, and a whole one as the fleet was). Board docks the ship boarded first, an action of its own, so a Board stopped between the two may
 * also leave both ships docked. And a fleet caught by a Board or Dock stopped before 5.95 must open whole. args: gamedir, world saves (from WorldT), work dir, [random rounds per action, default 6
 * | "survey"], [actions, comma-separated].
 */
public class KillT {
 static File game, work, base;
 static boolean trace;
 static int failures = 0;

 public static void main(String[] a) throws Exception {
  game = new File(a[0]); work = new File(a[2]);
  boolean survey = a.length > 3 && a[3].equals("survey"); // each action once, every file it touches listed
  int randomRounds = a.length > 3 && !survey ? Integer.parseInt(a[3]) : 6;
  SafeFiles.deleteTree(work);
  base = new File(work, "base");
  prepare(new File(a[1]));
  FleetLedger before = settled(copyRound("before"));
  Setup.chk("the fleet before any action is whole " + before.problems, before.problems.isEmpty());
  Random rng = new Random(25);
  String[] actions = a.length > 4 ? a[4].split(",") : new String[] {"save", "receive", "home", "board", "dock", "disband", "convert"};
  FleetLedger oldWas = Arrays.asList(actions).contains("convert") ? prepareOld(new File(a[1])) : null;
  boolean moves = !survey && (Arrays.asList(actions).contains("board") || Arrays.asList(actions).contains("dock"));
  // the fleet after Dock alone: where a Board stopped between its Dock and itself leaves it, and where a Dock caught by 5.94 must come back to
  File dockedRound = moves ? copyRound("docked") : null;
  FleetLedger docked = null;
  if (moves) { peer(dockedRound, "dock", 0, -1); docked = settled(dockedRound); }
  FleetLedger boardAfter = null;
  String boardedFolder = null; // the folder of the ship Board boards
  for (String action : actions) {
   boolean convert = action.equals("convert");
   FleetLedger between = action.equals("board") ? docked : null;
   File r0 = copyRound(action + "-whole", convert);
   if (survey) { trace = true; peer(r0, action, 0, -1); trace = false; continue; }
   String[] done = peer(r0, action, 0, -1);
   FleetLedger after = settled(r0);
   kindsWhole = convert ? kinds() : null;
   if (action.equals("board")) { boardAfter = after; boardedFolder = folderIn(new File(r0, "saves"), "boarded").getName(); }
   boolean changed = !after.key().equals(before.key());
   String d = after.diff(convert ? oldWas : before);
   if (convert) Setup.chk("convert: done whole in " + done[1] + " ms, " + done[0] + " files touched, and the fleet as it was before it was put back in the old layout (" + d + ")", done[0] != null && d.equals("nothing") && after.problems.isEmpty() && converted(r0) == null);
   else Setup.chk(action + ": done whole in " + done[1] + " ms, " + done[0] + " files touched, and the fleet changed (" + (d.length() > 300 ? d.substring(0, 300) + "..." : d) + ")", done[0] != null && changed && after.problems.isEmpty());
   if (done[0] == null) continue;
   int ops = Integer.parseInt(done[0]), dur = Integer.parseInt(done[1]);
   FleetLedger was = convert ? after : before; // opened again, a stopped conversion is finished: only its end is whole
   int every = convert ? Math.max(1, ops / 120) : 1; // a conversion touches hundreds of files: a spread of them, its first and last all
   if (ops < 0) System.out.println("  (this Java can't stop the station at a chosen file: its SecurityManager is gone, Java 24 on; random moments only)");
   int bad = 0, kills = 0;
   StringBuilder why = new StringBuilder(), failedAt = new StringBuilder();
   for (int k = 1; k <= ops + 1; k++) { // stopped before each file it touches, then at its end
    if (k > 20 && k < ops - 10 && k % every != 0) continue;
    File r = copyRound(action + "-step" + k, convert);
    peer(r, action, k, -1);
    kills++;
    String w = check(settled(r), was, after, between);
    if (w == null && convert) w = converted(r);
    if (w != null) { bad++; failedAt.append(failedAt.length() == 0 ? "" : ",").append(k); if (why.length() < 1500) why.append("\n      before file ").append(k).append(": ").append(w); }
    SafeFiles.deleteTree(r);
   }
   for (int k = 0; k < randomRounds; k++) { // and at random moments, the file half-written among them
    File r = copyRound(action + "-random" + k, convert);
    int delay = rng.nextInt(Math.max(1, dur * 3 / 2 + 1));
    peer(r, action, 0, delay);
    kills++;
    String w = check(settled(r), was, after, between);
    if (w == null && convert) w = converted(r);
    if (w != null) { bad++; failedAt.append(failedAt.length() == 0 ? "" : ",").append("random ").append(delay).append(" ms"); if (why.length() < 1500) why.append("\n      killed after ").append(delay).append(" ms: ").append(w); }
    SafeFiles.deleteTree(r);
   }
   if (convert && !linesLost.isEmpty()) System.out.println("  (stopped just after a step's work, before its own line in the log: the line missing, the work whole: " + linesLost + ")");
   Setup.chk(action + ": stopped dead " + kills + " times (" + (ops < 0 ? "" : (every > 1 ? "before " + (kills - randomRounds) + " of its " + ops + " files" : "before each of its " + ops + " files") + ", its end, and ") + randomRounds + " random moments): the fleet as " + (convert ? "a whole conversion leaves it" : "before or as after") + " every time"
     + (bad == 0 ? "" : " (" + bad + " not: " + failedAt + ")") + why, bad == 0);
   SafeFiles.deleteTree(r0);
  }
  if (moves) caught(docked, dockedRound, boardAfter, boardedFolder);
  Setup.done();
  System.exit(Setup.fails == 0 ? 0 : 1);
 }

 /** null if the fleet is whole and as before or as after (or as between, for an action that is two; null if not); else what's wrong. */
 static String check(FleetLedger l, FleetLedger before, FleetLedger after, FleetLedger between) {
  if (!l.problems.isEmpty()) return l.problems.toString();
  if (l.key().equals(before.key()) || l.key().equals(after.key()) || (between != null && l.key().equals(between.key()))) return null;
  return "half done: against before " + l.diff(before) + "; against after " + l.diff(after);
 }

 /**
  * A fleet caught by a Dock or a Board stopped before 5.95, as its next opening left it: the note finished, her record
  * still saying where her save was. Opened now, she must be where her save is: docked, not lost; boarded, not a stranger
  * with her folder sent to the memorial.
  */
 static void caught(FleetLedger docked, File dockedRound, FleetLedger boardAfter, String boardedFolder) throws Exception {
  File r = copyRound("caught-dock"), saves = new File(r, "saves"), cont = new File(saves, "continue.sav");
  File her = folderIn(saves, "boarded");
  SafeFiles.write(ShipStore.sav(her), SafeFiles.read(cont));
  if (!cont.delete()) throw new IOException("Could not remove " + cont);
  String w = check(settled(r), docked, docked, null);
  Setup.chk("a Dock caught by 5.94 (her save in her folder, continue.sav gone, her record saying boarded): opened, she is docked, not lost" + (w == null ? "" : ": " + w), w == null);
  SafeFiles.deleteTree(r);
  if (boardAfter == null) return;
  r = new File(work, "caught-board"); saves = new File(r, "saves"); cont = new File(saves, "continue.sav");
  Setup.copyTree(new File(dockedRound, "saves"), saves);
  her = new File(new File(new File(saves, Vault.FOLDER), "shipyard"), boardedFolder);
  SafeFiles.write(cont, SafeFiles.read(ShipStore.sav(her)));
  if (!ShipStore.sav(her).delete()) throw new IOException("Could not remove " + ShipStore.sav(her));
  w = check(settled(r), boardAfter, boardAfter, null);
  Setup.chk("a Board caught by 5.94 (continue.sav her save, her own gone, her record saying docked): opened, she is boarded, not a stranger" + (w == null ? "" : ": " + w), w == null);
  SafeFiles.deleteTree(r);
 }
 /** The shipyard folder whose record is in this state (the first, by name). */
 static File folderIn(File saves, String state) throws IOException {
  File[] ds = new File(new File(saves, Vault.FOLDER), "shipyard").listFiles();
  if (ds != null) { Arrays.sort(ds); for (File d : ds) { ShipStore.Record r = ShipStore.read(d); if (r != null && state.equals(r.state)) return d; } }
  throw new IOException("no ship " + state + " in " + saves);
 }

 /**
  * A fleet as a 5.x station left it, for the conversion (as MigT makes one): the world with a ship decommissioned and
  * destroyed (a departed ship to name and fold), put back into the old layout, with old prose logs to read in. Returns
  * the fleet as it was before it was put back: a whole conversion must give it back.
  */
 static FleetLedger prepareOld(File world) throws Exception {
  File saves = new File(base, "old-saves");
  Setup.copyTree(world, saves);
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  Ship b = v.boarded(), d = v.docked().get(v.docked().size() - 1);
  v.board(d); v.disband(); v.remove(d, "DESTROY");
  v.board(b);
  v.takeStock();
  FleetLedger was = FleetLedger.of(v);
  Layout.unconvert(v);
  StringBuilder h = new StringBuilder(), voy = new StringBuilder();
  for (int i = 0; i < 150; i++) h.append(String.format("2026-01-%02d %02d:%02d  LOADED  (refresh)%n  %s  sector %d%n", 1 + i / 48, (i / 2) % 24, (i * 7) % 60, b.name, 1 + i % 8));
  for (int i = 0; i < 40; i++) voy.append(String.format("2026-01-%02d %02d:%02d  Jumped, hull 28/30 (-2), scrap %d (+3), fuel 12, missiles 4, drone parts 2%n", 1 + i / 20, i % 24, (i * 11) % 60, 40 + i));
  File root = v.root;
  SafeFiles.writeText(new File(root, "history.log"), h.toString(), false);
  SafeFiles.writeText(new File(new File(new File(root, "history"), b.id), "voyage.log"), voy.toString(), false);
  Setup.chk("an old fleet to convert: a manifest, ships/, history/, the hold's save, an old station log of 150 entries and a voyage log of 40",
    new File(root, "manifest.xml").isFile() && new File(root, "storage.sav").isFile() && new File(root, "history/" + d.id + "/fate.txt").isFile() && !new File(root, "shipyard").exists());
  return was;
 }
 /** The event log's entries by kind, of the fleet open now (JOURNAL left out: a note finished on opening is told there). */
 static TreeMap<String, Integer> kinds() {
  TreeMap<String, Integer> out = new TreeMap<String, Integer>();
  for (EventLog.Entry e : EventLog.read(Vault.get())) if (!e.kind.equals("JOURNAL")) out.put(e.kind, (out.containsKey(e.kind) ? out.get(e.kind) : 0) + 1);
  return out;
 }
 static TreeMap<String, Integer> kindsWhole;
 /** null if a converted round (opened just now) has nothing of the old layout left and the event log a whole conversion writes; else what's wrong. */
 static String converted(File round) {
  File root = Vault.get().root;
  List<String> left = new ArrayList<String>();
  for (String f : new String[] {"manifest.xml", "ships", "history", "storage.sav", "history.log"}) if (new File(root, f).exists()) left.add(f);
  if (!left.isEmpty()) return "the old layout left: " + left;
  TreeMap<String, Integer> k = kinds();
  if (kindsWhole == null || k.equals(kindsWhole)) return null;
  StringBuilder d = new StringBuilder();
  Set<String> ks = new TreeSet<String>(k.keySet()); ks.addAll(kindsWhole.keySet());
  for (String x : ks) {
   if (String.valueOf(k.get(x)).equals(String.valueOf(kindsWhole.get(x)))) continue;
   if (STEP_LINES.contains(x) && Integer.valueOf(1).equals(kindsWhole.get(x)) && k.get(x) == null) { linesLost.put(x, (linesLost.containsKey(x) ? linesLost.get(x) : 0) + 1); continue; }
   d.append(d.length() == 0 ? "" : ", ").append(x).append(" ").append(kindsWhole.get(x)).append(" -> ").append(k.get(x));
  }
  return d.length() == 0 ? null : "the event log not as a whole conversion leaves it: " + d;
 }
 /**
  * Each conversion step's own line, written once its work is done: a stop between the two leaves the work whole and the
  * line unwritten (as for Board's and Dock's). Counted and told, not failed: the work is what must be whole.
  */
 static final Set<String> STEP_LINES = new HashSet<String>(Arrays.asList("SMALL_FILES", "LAYOUT", "HOLD_FILE", "EXPEDITION_FILES", "SHIP_FILES", "LOGS_CONVERTED", "LOG_DAYS_REPAIRED", "CREW_FILES"));
 static final TreeMap<String, Integer> linesLost = new TreeMap<String, Integer>();

 /** The world made ready once: something in the Cargo Hold to move (an augment, scrap, crew), a detail away to come home, and a ship's package to receive. */
 static void prepare(File world) throws Exception {
  File saves = new File(base, "saves");
  Setup.copyTree(world, saves);
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive(); HomePlanet.immersiveNotifications = false;
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  Vault.Copy c = v.readCopy(v.storage());
  ShipState h = c.save.getPlayerShip();
  h.getAugmentIdList().add("SCRAP_COLLECTOR");
  h.setScrapAmt(h.getScrapAmt() + 100);
  Random rng = new Random(9);
  for (String race : new String[] {"human", "engi", "rock"}) { CrewState x = Commission.volunteer(race, rng); SaveHelper.placeCrew(h, x, true); h.getCrewList().add(x); }
  v.begin().put(v.storage(), c.save, c.hash).commit();
  v.takeStock();
  List<CrewState> two = new ArrayList<CrewState>(Expeditions.holdCrew(v).subList(0, 2));
  Assignments.send(v, Assignments.board(v).get(0).slot, two, new Random(7));
  Ship donor = null;
  for (Ship s : v.docked()) if (donor == null && s.save() != null && !SaveHelper.getOwnCrew(s.save().getPlayerShip()).isEmpty()) donor = s;
  File in = new File(base, "kill-inputs"); in.mkdirs();
  SafeFiles.write(new File(in, "package.zip"), v.packageOf(donor));
  SafeFiles.write(new File(in, "ship.sav"), SafeFiles.read(v.fileOf(donor)));
  v.takeStock();
  Setup.chk("prepared: a detail away (" + Assignments.away(v).size() + "), a ship boarded (" + (v.boarded() == null ? "none" : v.boarded().name) + "), " + donor.name + "'s package to receive",
    Assignments.away(v).size() == 1 && v.boarded() != null);
 }
 static File copyRound(String name) throws IOException { return copyRound(name, false); }
 static File copyRound(String name, boolean old) throws IOException {
  File r = new File(work, name);
  SafeFiles.deleteTree(r);
  Setup.copyTree(new File(base, old ? "old-saves" : "saves"), new File(r, "saves"));
  Setup.copyTree(new File(base, "kill-inputs"), new File(r, "kill-inputs"));
  return r;
 }
 /** The fleet opened again here (any note left is finished or undone) and taken stock of, as the station does on opening; its ledger. */
 static FleetLedger settled(File round) throws Exception {
  Vault v = Setup.open(game, new File(round, "saves")); v.storage(); v.takeStock();
  return FleetLedger.of(v);
 }
 /**
  * The peer run on a round: stopped before its Nth file (N > 0), killed delay ms after GO (delay >= 0), or left to finish.
  * Returns {files touched, ms} when it finished, else {null, null}.
  */
 static String[] peer(File round, String action, int n, int delay) throws Exception {
  List<String> cmd = new ArrayList<String>(Arrays.asList(new File(new File(System.getProperty("java.home"), "bin"), "java").getPath(), "-Djava.awt.headless=true", "-Dhomeplanet.noGameCheck=true"));
  if (!System.getProperty("java.specification.version").startsWith("1.")) cmd.add("-Djava.security.manager=allow"); // Java 12 to 23 let KillPeer set its own; 8 always does
  cmd.addAll(Arrays.asList("-cp", System.getProperty("java.class.path"), "KillPeer", game.getPath(), new File(round, "saves").getPath(), action, Integer.toString(n), trace ? "trace" : ""));
  Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
  BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream()));
  Writer to = new OutputStreamWriter(p.getOutputStream());
  String[] result = {null, null};
  StringBuilder said = new StringBuilder();
  for (String l; (l = out.readLine()) != null;) {
   if (l.equals("READY")) { to.write("GO\n"); to.flush(); continue; }
   if (l.equals("GO") && delay >= 0) { Thread.sleep(delay); p.destroyForcibly(); break; }
   if (l.startsWith("DONE ")) { for (String w : l.split(" ")) { if (w.startsWith("ops=")) result[0] = w.substring(4); if (w.startsWith("ms=")) result[1] = w.substring(3); } }
   if (l.startsWith("ERROR") || l.startsWith("\tat ") || l.startsWith("Exception")) said.append(l).append('\n');
   if (trace && (l.startsWith("op ") || l.startsWith("DONE"))) System.out.println("  " + action + " " + l.replace(round.getPath(), "~"));
  }
  p.waitFor();
  if (said.length() > 0) System.out.println("  peer (" + action + ", " + n + "): " + said.toString().trim().replace("\n", "\n    "));
  return result;
 }
}
