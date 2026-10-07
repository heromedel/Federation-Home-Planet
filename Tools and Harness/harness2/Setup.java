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
 /** A ship the fleet remembers: her folder in the memorial, with a record, as if she had left (fate.txt is the test's to write). */
 public static File departed(Vault v, String id, String name) throws IOException {
  ShipStore.Record r = new ShipStore.Record(id); r.name = name; r.state = "docked";
  File d = new File(v.memorialDir(), ShipStore.stem(name, id)); ShipStore.write(d, r); ShipStore.versions(d).mkdirs(); return d;
 }
 public static void chk(String n, boolean ok) { System.out.println((ok ? "PASS  " : "FAIL  ") + n); if (!ok) fails++; }
 public static void done() { System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED"); }
}
