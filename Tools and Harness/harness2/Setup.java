import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Shared harness bootstrap: game data from gamedir, the vault on a saves folder. */
public class Setup {
 public static Vault open(File gamedir, File saves) throws Exception {
  System.setProperty("homeplanet.noGameCheck", "true");
  HomePlanet.savedGameParser = new SavedGameParser();
  HomePlanet.save_location = saves;
  HomePlanet.datsPath = gamedir;
  // a stand-in Slipstream folder beside the saves, so the companion mod is written there and not into the current folder
  File slip = new File(saves.getAbsoluteFile().getParentFile(), "slipstream");
  new File(slip, "mods").mkdirs(); new File(slip, "modman.jar").createNewFile();
  HomePlanet.config.setProperty(Slipstream.CFG_DIR, slip.getAbsolutePath());
  Vault v = Vault.open(saves);
  if (DataManager.get() == null) { DefaultDataManager dm = new DefaultDataManager(gamedir); DataManager.setInstance(dm); dm.setDLCEnabledByDefault(true); }
  CompanionMod.register(CompanionMod.load());
  return v;
 }
 /** The test world's ships: blueprint id and name. The first is boarded, the rest docked; the Stealth is retrofitted (onto PLAYER_SHIP_STEALTH_HP), the Lanius is an AE ship. */
 public static final String[][] WORLD = {{"PLAYER_SHIP_HARD", "Test Kestrel"}, {"PLAYER_SHIP_CIRCLE", "Test Engi"}, {"PLAYER_SHIP_STEALTH", "Test Stealth"},
   {"PLAYER_SHIP_FED", "Test Federation"}, {"PLAYER_SHIP_ANAEROBIC", "Test Lanius"}};
 /** A fresh 4B world in work/saves, made from the game data alone: the WORLD ships commissioned, one boarded, an empty storage hold. */
 public static File world(File gamedir, File work) throws Exception {
  SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); saves.mkdirs();
  Vault v = open(gamedir, saves);
  List<Ship> made = new ArrayList<Ship>();
  for (String[] w : WORLD) {
   SavedGameState g = Commission.build(w[0], w[1], net.blerf.ftl.constants.Difficulty.NORMAL, new Random(w[0].hashCode()));
   if (w[0].equals("PLAYER_SHIP_STEALTH")) Retrofit.apply(g, false);
   made.add(v.adopt(g));
  }
  v.board(made.get(0));
  v.storage();
  v.takeStock();
  return saves;
 }
 public static void copyTree(File from, File to) throws IOException {
  File[] fs = from.listFiles(); to.mkdirs(); if (fs == null) return;
  for (File f : fs) { File t = new File(to, f.getName()); if (f.isDirectory()) copyTree(f, t); else java.nio.file.Files.copy(f.toPath(), t.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
 }
 public static int fails = 0;
 /** A ship's save in a folder of ship folders (a shipyard, a Junkyard, a surrender), by her id; null if she isn't there. */
 public static File savIn(File parent, String id) { for (File d : ShipStore.folders(parent)) if (id.equals(ShipStore.idOf(d))) return ShipStore.sav(d).isFile() ? ShipStore.sav(d) : null; return null; }
 /**
  * What a side file of hers held before 5.98, written as the station keeps it now: the section of her record it became
  * (fate.txt's lines as her fate). Tests written against the files keep their text.
  */
 public static void side(Vault v, File folder, String file, String text) throws IOException {
  if (file.equals("fate.txt")) { String[] l = text.split("\n"); v.setNotes(folder, ShipStore.FATE, ShipStore.fateNotes(l[0].trim(), l.length > 1 ? l[1].trim() : "", l.length > 2 ? l[2].trim() : null)); return; }
  for (String[] f : new String[][] {{"traded.txt", ShipStore.TRADE}, {"journey.txt", ShipStore.JOURNEY}, {"museum.txt", ShipStore.MUSEUM}, {"borrowed.txt", ShipStore.BORROWED},
    {"voyage.txt", ShipStore.LAST}, {"final-battle.txt", ShipStore.FINAL}, {"overwritten.txt", ShipStore.OVERWRITTEN}})
   if (f[0].equals(file)) { v.setNotes(folder, f[1], Store.parse(text.getBytes("UTF-8"))); return; }
  throw new IllegalArgumentException(file);
 }
 /** Her fate as fate.txt said it, "KIND\nname\ndetail" (5.98: her record's fate section); "" if none. */
 public static String fateText(File folder) { String[] f = ShipStore.fate(folder); return f == null ? "" : f[0] + "\n" + f[1] + (f[2].isEmpty() ? "" : "\n" + f[2]); }
 /** A ship the fleet remembers: her folder in the memorial, with a record, as if she had left (fate.txt is the test's to write). */
 public static File departed(Vault v, String id, String name) throws IOException {
  ShipStore.Record r = new ShipStore.Record(id); r.name = name; r.state = "docked";
  File d = new File(v.memorialDir(), ShipStore.stem(name, id)); ShipStore.write(d, r); ShipStore.versions(d).mkdirs(); return d;
 }
 /** A voyage line as the station writes one: her event (every reader since 5.74; the master log's copy went at 5.93). */
 public static void voyage(Vault v, Ship s, String text) { voyage(v, s.name, s.id, text); }
 /** The same for a ship the test only names (no ship of the fleet). */
 public static void voyage(Vault v, String name, String text) { voyage(v, name, name.toLowerCase().replaceAll("[^a-z0-9]", "") + "x", text); }
 static void voyage(Vault v, String name, String id, String text) {
  Event e = Event.of("VOYAGE_NOTE").put("text", text);
  for (String[] k : new String[][] {{"Crew joined: ", "CREW_JOINED"}, {"Crew lost: ", "CREW_LOST"}}) { // as VoyageLog writes them: each crew member and race a field
   if (!text.startsWith(k[0])) continue;
   e = Event.of(k[1]);
   for (String one : text.substring(k[0].length()).split(", ")) { int c = one.lastIndexOf(" ("); e.put("crew", c > 0 ? one.substring(0, c) : one).put("race", c > 0 && one.endsWith(")") ? one.substring(c + 2, one.length() - 1) : null); }
  }
  EventLog.write(v, Event.of(e.kind).put("log", "voyage").put("ship", name + "." + id).put("ship_name", name).put("ship_id", id).putAll(e).human(text));
 }
 /** The crew register forgotten, as a fleet updating to 5.41 had none: its own file, any 5.x crew.txt, and every crew file. */
 public static void forgetCrew(Vault v) {
  CrewRegister.registerFileOf(v).delete(); new File(v.root, "crew.txt").delete();
  for (CrewRegister.Member m : CrewRegister.members(v)) { File f = CrewRegister.fileOf(v, m.id); if (f != null) f.delete(); }
 }
 /** When the register's files last changed: its own file and every crew file (to tell a look that wrote nothing). */
 public static String crewStamp(Vault v) {
  StringBuilder sb = new StringBuilder(Long.toString(CrewRegister.registerFileOf(v).lastModified()));
  for (CrewRegister.Member m : CrewRegister.members(v)) { File f = CrewRegister.fileOf(v, m.id); sb.append(',').append(f == null ? 0 : f.lastModified()); }
  return sb.toString();
 }
 /** The station log as it would have read (5.93: history.log is no longer written): the event log's station entries, in its form. */
 public static String stationLog(Vault v) { return CrewRegister.stationText(v); }
 /** A ship's voyage log as it would have read (5.93: voyage.log is no longer written): her own log's voyage entries, each "time  line". */
 public static String voyageLog(Vault v, Ship s) {
  StringBuilder sb = new StringBuilder();
  for (EventLog.Entry e : EventLog.voyage(ShipStore.entries(v.folderOf(s)), s.id)) sb.append(e.time.length() >= 16 ? e.time.substring(0, 16) : e.time).append("  ").append(e.human).append('\n');
  return sb.toString();
 }
 public static void chk(String n, boolean ok) { System.out.println((ok ? "PASS  " : "FAIL  ") + n); if (!ok) fails++; }
 public static void done() { System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED"); }
}
