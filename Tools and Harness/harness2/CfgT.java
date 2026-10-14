import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/**
 * Where the settings live (6.55, heromedel, Plan AI): beside the program only where things are (FTL, its saves, Slipstream);
 * the station's own in FTL's saves folder; Sandbox Mode's rules in its fleet; how reputation can be used, each career its own.
 * A cfg from before is carried over; a new release folder finds everything and asks nothing (headless: a question would throw).
 * args: gamedir, world saves, work
 */
public class CfgT {
 static Object call(String name) throws Exception { java.lang.reflect.Method m = HomePlanet.class.getDeclaredMethod(name); m.setAccessible(true); return m.invoke(null); }
 static Properties props(File f) throws IOException { return Store.load(f); }
 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();

  // C: a cfg from before 6.55 beside the program, every setting in it
  File rel1 = new File(work, "release1"); rel1.mkdirs();
  File old = new File(rel1, HomePlanet.STATION_CFG);
  Properties o = new Properties();
  o.setProperty("ftlDatsPath", game.getAbsolutePath()); o.setProperty("ftlSavePath", saves.getAbsolutePath()); o.setProperty(Slipstream.CFG_DIR, work.getAbsolutePath());
  o.setProperty(Slipstream.CFG_OFFERED, "true"); o.setProperty("launch_through_steam", "true"); o.setProperty(homeplanet.model.Words.CFG_SHIP, "him"); o.setProperty("window_bounds", "1,2,3,4");
  for (String k : new String[] {"store_requirement", "strip_when_scrapping", "commission_unlocked_only", "commission_custom_unlocked_only"}) o.setProperty(k, "false");
  o.setProperty("new_journey_store_requirement", "true"); o.setProperty("sell_supplies", "true"); o.setProperty("commission_price_percent", "75"); o.setProperty("reputation_use", "3");
  FileOutputStream out = new FileOutputStream(old); o.store(out, "before 6.55"); out.close();
  HomePlanet.config.clear(); HomePlanet.propFile = old;
  call("loadConfig");
  boolean had = (Boolean) call("loadSavesSettings");
  call("applySettings");
  HomePlanet.saveConfig();
  Properties here = props(old), st = props(HomePlanet.stationFile()), ru = props(HomePlanet.sandboxRulesFile());
  Setup.chk("C: nothing in the saves folder yet: the old cfg is carried over", !had);
  Setup.chk("C: beside the program, only where things are: " + new TreeSet<String>(here.stringPropertyNames()),
    here.stringPropertyNames().equals(new HashSet<String>(Arrays.asList("ftlDatsPath", "ftlSavePath", Slipstream.CFG_DIR))));
  Setup.chk("C: the station's own in the saves folder (" + HomePlanet.stationFile().getName() + "): Steam, the pronoun, the window, Slipstream offered, where FTL is; no House Rule",
    "true".equals(st.getProperty("launch_through_steam")) && "him".equals(st.getProperty(homeplanet.model.Words.CFG_SHIP)) && "1,2,3,4".equals(st.getProperty("window_bounds"))
    && "true".equals(st.getProperty(Slipstream.CFG_OFFERED)) && game.getAbsolutePath().equals(st.getProperty("ftlDatsPath")) && st.getProperty("store_requirement") == null);
  Setup.chk("C: Sandbox Mode's rules in its fleet (" + Vault.FOLDER + "/" + HomePlanet.sandboxRulesFile().getName() + "), and nothing else there",
    HomePlanet.sandboxRulesFile().getName().equals("rules.xml") && HomePlanet.sandboxRulesFile().getParentFile().getName().equals(Vault.FOLDER)
    && "false".equals(ru.getProperty("store_requirement")) && "75".equals(ru.getProperty("commission_price_percent")) && "3".equals(ru.getProperty("reputation_use"))
    && ru.getProperty(homeplanet.model.Words.CFG_SHIP) == null && ru.getProperty("ftlDatsPath") == null);

  // F: a new release folder: nothing beside it; FTL's saves where FTL keeps them, with the station's settings and fleet in them
  File home = new File(work, "home"), ftlSaves = new File(home, "Documents/My Games/FasterThanLight");
  Setup.copyTree(saves, ftlSaves);
  String userHome = System.getProperty("user.home");
  File rel2 = new File(work, "release2"); rel2.mkdirs();
  HomePlanet.config.clear(); HomePlanet.propFile = new File(rel2, HomePlanet.STATION_CFG); HomePlanet.save_location = null; HomePlanet.datsPath = null;
  HomePlanet.launchThroughSteam = false; HomePlanet.storeRequirement = true; HomePlanet.commissionPercent = 100;
  File found, gameDir;
  try {
   System.setProperty("user.home", home.getAbsolutePath());
   call("loadConfig");
   found = (File) call("savesFolder"); // the station's own there: not asked
   HomePlanet.save_location = found;
   boolean there = (Boolean) call("loadSavesSettings");
   call("applySettings");
   gameDir = (File) call("gameFolder"); // kept in the saves folder's settings: not asked
   Setup.chk("F: the saves folder where FTL keeps it, taken without asking (the station's own is in it)", found != null && found.getCanonicalPath().equals(ftlSaves.getCanonicalPath()));
   Setup.chk("F: its settings read: Steam launching, the pronoun, Sandbox Mode's rules", there && HomePlanet.launchThroughSteam && "him".equals(HomePlanet.config.getProperty(homeplanet.model.Words.CFG_SHIP))
     && !HomePlanet.storeRequirement && HomePlanet.commissionPercent == 75);
   Setup.chk("F: FTL's folder from the saves folder's settings, not asked: " + gameDir, gameDir != null && gameDir.getCanonicalPath().equals(game.getCanonicalPath()));
   java.lang.reflect.Field rk = HomePlanet.class.getDeclaredField("RULE_KEYS"); rk.setAccessible(true);
   int missing = 0; for (String k : (String[]) rk.get(null)) if (HomePlanet.config.getProperty(k) == null) missing++;
   Setup.chk("F: nothing left to ask: Steam answered, every House Rule set (no rules window, no mode choice), Slipstream offered before",
     HomePlanet.config.getProperty("launch_through_steam") != null && missing == 0 && HomePlanet.config.getProperty(Slipstream.CFG_OFFERED) != null);
  } finally {
   System.setProperty("user.home", userHome);
  }

  // R: how reputation can be used, each career its own
  HomePlanet.save_location = saves; HomePlanet.datsPath = game;
  HomePlanet.config.clear(); HomePlanet.propFile = old; call("loadConfig"); call("loadSavesSettings"); call("applySettings");
  HomePlanet.fleetOpened(Vault.get());
  Setup.chk("R: Sandbox Mode's: 3, as the old cfg had it", HomePlanet.reputationUse == 3);
  Vault imm = Vault.switchFleet(Vault.NORMAL); Vault.immersiveSlot = Vault.NORMAL; HomePlanet.immersiveMode = true;
  HomePlanet.saveConfig();
  Setup.chk("R: a career not yet begun: Sandbox Mode's stands in, and saving the settings doesn't begin it", HomePlanet.reputationUse == 3 && !Career.started(imm.root));
  Career.start(false, false, CareerRules.of(CareerRules.NORMAL));
  HomePlanet.reputationUse = 1; HomePlanet.saveConfig();
  Setup.chk("R: the career keeps its own (career.xml), Sandbox Mode's stays 3", Career.reputationUse(imm.root, -1) == 1 && "3".equals(props(HomePlanet.sandboxRulesFile()).getProperty("reputation_use")));
  Vault.switchFleet(Vault.SANDBOX); HomePlanet.immersiveMode = false;
  Setup.chk("R: back in Sandbox Mode: 3", HomePlanet.reputationUse == 3);
  Vault.switchFleet(Vault.NORMAL); HomePlanet.immersiveMode = true;
  Setup.chk("R: in the career again: 1", HomePlanet.reputationUse == 1);
  Vault.switchFleet(Vault.SANDBOX); HomePlanet.immersiveMode = false;
  Setup.chk("R: a career from before 6.55 (no choice of its own) has Sandbox Mode's, as it shared it", HomePlanet.reputationUseOf(Vault.rootOf(saves, Vault.HARD), true) == 3);

  // N: the infirmary (heromedel, 6.61), each fleet its own: Sandbox Mode's off unless chosen, a career's on unless chosen
  Setup.chk("N: Sandbox Mode's infirmary is off unless chosen", !HomePlanet.infirmary);
  HomePlanet.infirmary = true; HomePlanet.saveConfig();
  Setup.chk("N: Sandbox Mode's choice is kept in its rules (rules.xml)", "true".equals(props(HomePlanet.sandboxRulesFile()).getProperty("infirmary")));
  Vault.switchFleet(Vault.NORMAL); HomePlanet.immersiveMode = true;
  Setup.chk("N: a career that never chose has it on", HomePlanet.infirmary);
  HomePlanet.infirmary = false; HomePlanet.saveConfig();
  Setup.chk("N: the career keeps its own off (career.xml), Sandbox Mode's stays on", !Career.infirmary(imm.root, true) && "true".equals(props(HomePlanet.sandboxRulesFile()).getProperty("infirmary")));
  Vault.switchFleet(Vault.SANDBOX); HomePlanet.immersiveMode = false;
  Setup.chk("N: back in Sandbox Mode: on", HomePlanet.infirmary);
  Vault.switchFleet(Vault.NORMAL); HomePlanet.immersiveMode = true;
  Setup.chk("N: in the career again: off", !HomePlanet.infirmary);
  Vault.switchFleet(Vault.SANDBOX); HomePlanet.immersiveMode = false;
  Setup.done();
 }
}
