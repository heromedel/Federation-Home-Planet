import java.io.*; import java.util.*; import homeplanet.core.*;
/**
 * Launch FTL with DirectX (5.64): FTL's -directx switch on its own program, and on Steam's link (steam://run carries it,
 * steam://rungameid can't), only when the option is on, and never off Windows. args: none
 */
public class LaunchT {
 public static void main(String[] a) throws Exception {
  File exe = new File("FTLGame.exe").getAbsoluteFile();
  Setup.chk("D: option off, FTL's program alone", HomePlanet.exeCommand(exe, false).equals(Arrays.asList(exe.getAbsolutePath())));
  Setup.chk("D: option on, FTL's program with -directx", HomePlanet.exeCommand(exe, true).equals(Arrays.asList(exe.getAbsolutePath(), "-directx")));
  Setup.chk("D: option off, Steam's usual link (" + HomePlanet.steamUri(false) + ")", HomePlanet.steamUri(false).equals("steam://rungameid/212680"));
  Setup.chk("D: option on, Steam's run link carrying -directx (" + HomePlanet.steamUri(true) + ")", HomePlanet.steamUri(true).equals("steam://run/212680//-directx/"));
  boolean windows = System.getProperty("os.name", "").startsWith("Windows");
  HomePlanet.launchDirectX = false;
  Setup.chk("D: option off, no DirectX", !HomePlanet.directX());
  HomePlanet.launchDirectX = true;
  Setup.chk("D: option on, DirectX on Windows only (FTL's Mac and Linux builds have none)", HomePlanet.directX() == windows);
  Setup.done();
 }
}
