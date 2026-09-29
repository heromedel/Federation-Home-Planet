import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*; import hw2fhp.*;
/** Converts a copy of the 4.23 test world and checks what came out. args: gamedir, oldSaves, oldApp, work, mode (fresh | afterFirstLaunch) */
public class ConvT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[3]); SafeFiles.deleteTree(work);
 boolean firstLaunch = a.length > 4 && a[4].equals("afterFirstLaunch");
 File saves = new File(work, "saves"), app = new File(work, "app"), slip = new File(work, "slipstream");
 Setup.copyTree(new File(a[1]), saves); app.mkdirs(); new File(slip, "mods").mkdirs(); new File(slip, "modman.jar").createNewFile();
 for (String n : new String[]{"homeworld-designs.xml","homeworld-remodels.xml","ftl-homeworld.cfg","Removed Blueprints.log"}) { File f = new File(a[2], n); if (f.isFile()) java.nio.file.Files.copy(f.toPath(), new File(app, n).toPath()); }
 if (new File(a[2], "homeworld-art").isDirectory()) Setup.copyTree(new File(a[2], "homeworld-art"), new File(app, "homeworld-art"));
 // an old companion mod in Slipstream's mods folder, to be moved out of the way
 File oldMod = new File(slip, "mods/FTL Homeworld Companion Mod.ftl");
 { java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new FileOutputStream(oldMod)); z.putNextEntry(new java.util.zip.ZipEntry("mod-appendix/metadata.xml"));
   z.write("<metadata><title>Homeworld Companion Mod</title><author>FTL Homeworld</author><version>4.23</version><description>old</description></metadata>".getBytes("UTF-8")); z.closeEntry(); z.close(); }
 int oldShips = 0; for (File f : saves.listFiles()) if (f.getName().matches("continue(_\\d+)?\\.sav")) oldShips++;
 Setup.chk("something to convert", Convert.anythingToConvert(saves, app));
 if (firstLaunch) {
  // what Home Planet's first start does: opens the vault, adopts continue.sav as boarded, makes an empty storage hold
  Vault v0 = Setup.open(game, saves); v0.takeStock(); v0.storage();
  Setup.chk("first launch: continue.sav adopted, empty hold", v0.boarded() != null && v0.fileOf(v0.storageEntry()).isFile() && v0.fleet().size() == 1);
 }
 final List<String> lines = new ArrayList<String>();
 Convert.run(saves, app, game, slip, new Convert.Log() { public void line(String s) { lines.add(s); System.out.println("   " + s); } });
 Vault v = Vault.get(); v.takeStock();
 File backup = new File(saves, Convert.BACKUP);
 File[] zips = backup.listFiles(new FilenameFilter() { public boolean accept(File d, String n) { return n.startsWith("before-conversion") && n.endsWith(".zip"); } });
 Setup.chk("backup zip exists", zips != null && zips.length == 1 && zips[0].length() > 1000);
 Setup.chk("ships converted = " + oldShips + " (fleet " + v.fleet().size() + ")", v.fleet().size() == oldShips);
 Setup.chk("one boarded", v.boarded() != null && v.boarded().file().getName().equals("continue.sav"));
 boolean allRead = true; for (Ship s : v.all()) { if (s.state != Ship.State.STORAGE && s.save() == null) { allRead = false; System.out.println("   unreadable: " + s + " " + s.readError()); } }
 Setup.chk("every converted ship parses", allRead);
 boolean noHw = true; for (Ship s : v.all()) { String raw = new String(SafeFiles.read(s.file()), "ISO-8859-1"); if (raw.contains("_HW") || raw.contains("hw_design")) { noHw = false; System.out.println("   still HW: " + s); } }
 Setup.chk("no _HW left in any save", noHw);
 Setup.chk("converted blueprint ids are registered", v.blueprintsInUse().size() > 0 && allRead);
 Setup.chk("one storage hold, merged, AE", v.storage().save() != null && v.storage().save().isDLCEnabled() && !v.storage().save().getPlayerShip().getWeaponList().isEmpty());
 Setup.chk("old files moved to the backup folder", !new File(saves, "Homeworld.sav").exists() && !new File(saves, "continue_1.sav").exists() && !new File(saves, "Junkyard").exists()
   && new File(backup, "Homeworld.sav").isFile() && new File(backup, "continue_1.sav").isFile() && new File(backup, "continue.sav").isFile());
 Setup.chk("old program files moved to their backup folder", !new File(app, "homeworld-designs.xml").exists() && new File(app, "Homeworld backup/homeworld-designs.xml").isFile());
 Setup.chk("designs.xml in vault", v.designsFile().isFile() && !ShipDesign.load().isEmpty());
 Setup.chk("remodels.xml in vault, converted", v.remodelsFile().isFile() && !new String(SafeFiles.read(v.remodelsFile()), "UTF-8").contains("_HW"));
 Setup.chk("old companion mod moved out, new one written", !oldMod.exists() && new File(backup, oldMod.getName()).isFile() && new File(slip, "mods/Federation Home Planet Mod.ftl").isFile());
 Setup.chk("nothing left to convert", !Convert.anythingToConvert(saves, app));
 Setup.chk("history log merged", v.historyLog().isFile() && new String(SafeFiles.read(v.historyLog()), "UTF-8").contains("CONVERTED"));
 Vault v2 = Vault.open(saves); v2.takeStock();
 Setup.chk("reopen keeps ships", v2.fleet().size() == oldShips && v2.boarded() != null);
 Setup.chk("legacy names convert (overhauled remodel picture too)", Legacy.convert("hw_player_ship_circle_r2_hw hw_design_1 circle_r2_hw PLAYER_SHIP_CIRCLE_R2_HW PLAYER_SHIP_DESIGN_1_V2_HW")
   .equals("hp_player_ship_circle_r2_hp hp_design_1 circle_r2_hp PLAYER_SHIP_CIRCLE_R2_HP PLAYER_SHIP_DESIGN_1_V2_HP"));
 Setup.done();
}}
