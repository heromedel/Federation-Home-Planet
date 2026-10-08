import java.io.*; import java.util.*; import homeplanet.core.*;
/**
 * Restart after patching (5.79): the old station lets go of the saves folder before it starts the new one, and a
 * restarted station waits for the folder a moment. It used to start the new one first, which found the folder taken
 * and stopped with "already open" (heromedel, 5.61). Stations are processes here, as LinkT runs one.
 * args: work. As a child: hold <saves> <ms> (claims, says "held", keeps it that long) or try <saves> (claims once: exit 0 if it got it).
 */
public class RestartT {
 public static void main(String[] a) throws Exception {
  if (a[0].equals("hold")) { boolean got = StationLock.claim(new File(a[1])); System.out.println(got ? "held" : "refused"); System.out.flush(); Thread.sleep(Long.parseLong(a[2])); System.exit(0); }
  if (a[0].equals("try")) System.exit(StationLock.claim(new File(a[1])) ? 0 : 1);
  File work = new File(a[0]); SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); saves.mkdirs();

  // the old station closing: it holds the folder 2 s longer
  Process old = child("hold", saves.getPath(), "2000");
  BufferedReader r = new BufferedReader(new InputStreamReader(old.getInputStream()));
  Setup.chk("R: the old station holds the saves folder", "held".equals(r.readLine()));
  Setup.chk("R: a plain claim meanwhile is refused (another copy is open)", !StationLock.claim(saves));
  long t0 = System.currentTimeMillis();
  boolean got = StationLock.claimWaiting(saves, 10000);
  long waited = System.currentTimeMillis() - t0;
  Setup.chk("R: a restarted station waits and gets it once the old one closes (" + waited + " ms)", got && waited < 9000);
  old.waitFor();

  // this station letting go before it closes: another can claim at once
  Setup.chk("R: while this station has it, another can't", child("try", saves.getPath()).waitFor() == 1);
  StationLock.letGo();
  Setup.chk("R: let go (as Restart does before starting the new one), another gets it at once", child("try", saves.getPath()).waitFor() == 0);
  Setup.done();
  System.exit(Setup.fails == 0 ? 0 : 1);
 }
 static Process child(String... args) throws IOException {
  List<String> cmd = new ArrayList<String>(Arrays.asList(new File(System.getProperty("java.home"), "bin/java").getPath(), "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"), "RestartT"));
  cmd.addAll(Arrays.asList(args));
  return new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.INHERIT).start();
 }
}
