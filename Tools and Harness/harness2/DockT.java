import java.io.*; import java.util.*; import homeplanet.core.*;
/** Playing FTL docked (5.29): FTL's settings.ini edited in place, one key only, and put back only if still the station's. args: gamedir, world saves, work */
public class DockT { public static void main(String[] a) throws Exception {
 File work = new File(a[2]); SafeFiles.deleteTree(work); work.mkdirs();
 File saves = new File(work, "saves"), appData = new File(work, "AppData/Roaming/FasterThanLight"); saves.mkdirs(); appData.mkdirs();
 HomePlanet.propFile = new File(work, "t.cfg"); HomePlanet.save_location = saves; HomePlanet.config.remove(FtlDock.CFG_WAS);
 File ini = new File(appData, "settings.ini"); // FTL's own: %APPDATA%\FasterThanLight (5.31), not beside the saves
 System.setProperty("homeplanet.ftlSettings", ini.getPath());
 // 5.29 wrote a one-line file beside the saves, where FTL never reads it: it goes, and so does the value kept for it
 File stray = new File(saves, "settings.ini"); SafeFiles.writeText(stray, "fullscreen=0\n", false); HomePlanet.config.setProperty(FtlDock.CFG_WAS, "(none)");
 String theirs = "fullscreen=1\r\nlast_fullscreen=1\r\nsound=60\r\nmusic=40\r\n#Key bindings shoudn't be edited within this file. Please use the in-game options\r\nhotkey_pause=32\r\n";
 SafeFiles.writeText(ini, theirs, false);
 FtlDock.prepareSettings();
 String now = read(ini);
 Setup.chk("S: the stray one-line settings.ini beside the saves is gone", !stray.exists());
 Setup.chk("S: a docked launch sets fullscreen to 0 and touches nothing else", now.equals(theirs.replace("fullscreen=1\r\nlast", "fullscreen=0\r\nlast")) && "1".equals(HomePlanet.config.getProperty(FtlDock.CFG_WAS)));
 FtlDock.prepareSettings();
 Setup.chk("S: a second docked launch keeps the player's own value, not the station's", "1".equals(HomePlanet.config.getProperty(FtlDock.CFG_WAS)) && read(ini).equals(now));
 SafeFiles.writeText(ini, read(ini).replace("sound=60", "sound=80"), false); // the player changes another setting in FTL meanwhile
 FtlDock.restoreSettings();
 Setup.chk("S: the option turned off puts back fullscreen alone, keeping what the player changed since", read(ini).equals(theirs.replace("sound=60", "sound=80")) && HomePlanet.config.getProperty(FtlDock.CFG_WAS) == null);
 FtlDock.prepareSettings();
 SafeFiles.writeText(ini, read(ini).replace("fullscreen=0", "fullscreen=2"), false); // the player chose fullscreen themselves since
 FtlDock.restoreSettings();
 Setup.chk("S: a fullscreen the player changed since is left alone", read(ini).contains("fullscreen=2") && !read(ini).contains("fullscreen=1\r\nlast"));
 SafeFiles.writeText(ini, "sound=60\n", false);
 FtlDock.prepareSettings();
 Setup.chk("S: no fullscreen line: one is added", read(ini).equals("sound=60\nfullscreen=0\n"));
 FtlDock.restoreSettings();
 Setup.chk("S: and taken away again", read(ini).equals("sound=60\n"));
 HomePlanet.config.setProperty(FtlDock.CFG_SIZE, "1600x900");
 SafeFiles.writeText(stray, "fullscreen=0\nsound=40\n", false); FtlDock.cleanUpStray();
 Setup.chk("S: a settings.ini beside the saves holding more than the station's line is left alone", stray.isFile());
 ini.delete(); appData.delete(); FtlDock.prepareSettings();
 Setup.chk("S: no FTL settings folder at all: nothing is made", !ini.exists() && !appData.exists());
 Setup.chk("Z: the size chosen", FtlDock.size().width == 1600 && FtlDock.size().height == 900);
 Setup.chk("Z: docking is offered only on Windows (here: " + System.getProperty("os.name") + ")", FtlDock.supported() == System.getProperty("os.name").startsWith("Windows"));
 Setup.done();
}
 static String read(File f) throws IOException { return new String(SafeFiles.read(f), "UTF-8"); }
}
