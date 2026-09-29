import java.io.*; import net.blerf.ftl.parser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Shared harness bootstrap: game data from gamedir, the vault on a saves folder. */
public class Setup {
 public static Vault open(File gamedir, File saves) throws Exception {
  System.setProperty("homeplanet.noGameCheck", "true");
  HomePlanet.savedGameParser = new SavedGameParser();
  HomePlanet.save_location = saves;
  Vault v = Vault.open(saves);
  if (DataManager.get() == null) { DefaultDataManager dm = new DefaultDataManager(gamedir); DataManager.setInstance(dm); dm.setDLCEnabledByDefault(true); }
  CompanionMod.register(CompanionMod.load());
  return v;
 }
 public static void copyTree(File from, File to) throws IOException {
  File[] fs = from.listFiles(); to.mkdirs(); if (fs == null) return;
  for (File f : fs) { File t = new File(to, f.getName()); if (f.isDirectory()) copyTree(f, t); else java.nio.file.Files.copy(f.toPath(), t.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
 }
 public static int fails = 0;
 public static void chk(String n, boolean ok) { System.out.println((ok ? "PASS  " : "FAIL  ") + n); if (!ok) fails++; }
 public static void done() { System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED"); }
}
