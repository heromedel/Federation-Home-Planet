import java.io.*; import java.util.*; import java.util.zip.*; import homeplanet.core.*;
/** Check for Updates: versions compared, and an update put in place on a made-up install folder (only the program's files change; a bad download or a failed write changes nothing). args: work */
public class UpdT {
 static File work;
 public static void main(String[] a) throws Exception {
  work = new File(a[0]); SafeFiles.deleteTree(work); work.mkdirs();
  versions();
  update();
  badDownloads();
  failedWrite();
  Setup.done();
 }

 static void versions() {
  Setup.chk("V: 4B.67 is older than 4B.68", Updater.compare("4B.67", "4B.68") < 0 && Updater.compare("4B.68", "4B.67") > 0);
  Setup.chk("V: 4B.9 is older than 4B.10 (numbers, not text)", Updater.compare("4B.9", "4B.10") < 0);
  Setup.chk("V: 4C.1 is newer than 4B.99, 5A.1 newer than 4C.99", Updater.compare("4C.1", "4B.99") > 0 && Updater.compare("5A.1", "4C.99") > 0);
  Setup.chk("V: the same version is the same", Updater.compare("4B.68", "4B.68") == 0);
  Setup.chk("V: an unreadable version counts as oldest", Updater.compare("nonsense", "4B.1") < 0);
  Setup.chk("V: the pom's own version is read", "4B.70".equals(Updater.pomVersion("<project><modelVersion>4.0.0</modelVersion><version>4B.70</version><dependency><version>1.2</version></dependency></project>")));
 }

 static void put(File root, String path, String text) throws IOException { SafeFiles.write(new File(root, path), text.getBytes("UTF-8")); }
 static String get(File root, String path) throws IOException { File f = new File(root, path); return f.isFile() ? new String(SafeFiles.read(f), "UTF-8") : null; }
 /** A zip as GitHub sends main: one top folder, then the repository's files. */
 static File zip(String name, String top, String... pathsAndTexts) throws IOException {
  File z = new File(work, name);
  ZipOutputStream out = new ZipOutputStream(new FileOutputStream(z));
  out.putNextEntry(new ZipEntry(top + "/")); out.closeEntry();
  for (int i = 0; i < pathsAndTexts.length; i += 2) { out.putNextEntry(new ZipEntry(top + "/" + pathsAndTexts[i])); out.write(pathsAndTexts[i + 1].getBytes("UTF-8")); out.closeEntry(); }
  out.close();
  return z;
 }
 static final String BAT = Updater.BUILD_BAT, MAIN = "src/main/java/homeplanet/core/HomePlanet.java";
 /** An install as the Construction Yard leaves it: the source, tools, the built jar, settings and logs. */
 static File install(String name) throws IOException {
  File root = new File(work, name);
  put(root, "pom.xml", "<version>4B.68</version>"); put(root, BAT, "old bat"); put(root, MAIN, "old main");
  put(root, "src/main/java/homeplanet/Gone.java", "old, dropped by main"); put(root, "src/main/java/homeplanet/Same.java", "same");
  put(root, "tools/jdk/bin/javac.exe", "jdk"); put(root, "Current Build/Federation Home Planet.jar", "jar");
  put(root, "Current Build/federation-home-planet.cfg", "settings"); put(root, "logs/run.log", "log");
  put(root, "my notes.txt", "mine");
  put(root, Updater.MANIFEST, "pom.xml\nBuild The Federation Home Planet Station.bat\n" + MAIN + "\nsrc/main/java/homeplanet/Gone.java\nsrc/main/java/homeplanet/Same.java\n");
  return root;
 }

 static void update() throws Exception {
  File root = install("install");
  File z = zip("main.zip", "Federation-Home-Planet-main", "pom.xml", "<version>4B.70</version>", BAT, "new bat", MAIN, "new main",
    "src/main/java/homeplanet/Same.java", "same", "src/main/java/homeplanet/New.java", "new file",
    "tools/jdk/bin/javac.exe", "a zip may not touch the tools", "Current Build/federation-home-planet.cfg", "nor settings");
  Updater.Result r = Updater.apply(root, z);
  Setup.chk("U: brings 4B.70: 3 replaced (pom, bat, main), 1 added, 1 removed (" + r.replaced + ", " + r.added + ", " + r.removed + ")", "4B.70".equals(r.version) && r.replaced == 3 && r.added == 1 && r.removed == 1);
  Setup.chk("U: the program's files are main's", "new main".equals(get(root, MAIN)) && "new bat".equals(get(root, BAT)) && "new file".equals(get(root, "src/main/java/homeplanet/New.java")));
  Setup.chk("U: a file main dropped is removed", get(root, "src/main/java/homeplanet/Gone.java") == null);
  Setup.chk("U: tools, the jar, settings, logs and the player's own files untouched", "jdk".equals(get(root, "tools/jdk/bin/javac.exe")) && "jar".equals(get(root, "Current Build/Federation Home Planet.jar"))
    && "settings".equals(get(root, "Current Build/federation-home-planet.cfg")) && "log".equals(get(root, "logs/run.log")) && "mine".equals(get(root, "my notes.txt")));
  Setup.chk("U: the old files are kept, and the new one listed, for the Construction Yard to put back", "old main".equals(get(root, "update-backup/files/" + MAIN))
    && "old, dropped by main".equals(get(root, "update-backup/files/src/main/java/homeplanet/Gone.java")) && get(root, "update-backup/files/src/main/java/homeplanet/Same.java") == null
    && get(root, "update-backup/added.txt").contains("src\\main\\java\\homeplanet\\New.java") && get(root, "update-backup/files/" + Updater.MANIFEST) != null);
  Setup.chk("U: the new list of installed files", get(root, Updater.MANIFEST).contains("src/main/java/homeplanet/New.java") && !get(root, Updater.MANIFEST).contains("Gone.java"));
  Setup.chk("U: a folder with the source and the Construction Yard is an install; a .git folder makes it a checkout", installed(root) && !Updater.isGitCheckout(root));
  new File(root, ".git").mkdirs();
  Setup.chk("U: with .git, a checkout (fetched, never replaced)", Updater.isGitCheckout(root));
 }
 static boolean installed(File root) throws Exception {
  java.lang.reflect.Method m = Updater.class.getDeclaredMethod("isInstall", File.class); m.setAccessible(true); return (Boolean) m.invoke(null, root);
 }

 static void badDownloads() throws Exception {
  File root = install("install-bad");
  File[] bad = {
   zip("escape.zip", "top", "pom.xml", "x", BAT, "x", MAIN, "x", "../outside.txt", "escape"),
   zip("partial.zip", "top", BAT, "x", MAIN, "x"),
   zip("two.zip", "top", "pom.xml", "x", BAT, "x", MAIN, "x"),
  };
  // the third again, with a second top folder
  ZipOutputStream out = new ZipOutputStream(new FileOutputStream(bad[2]));
  for (String p : new String[] {"a/pom.xml", "a/" + BAT, "a/" + MAIN, "b/extra.txt"}) { out.putNextEntry(new ZipEntry(p)); out.write("x".getBytes()); out.closeEntry(); }
  out.close();
  String[] why = {"outside", "isn't whole", "two top folders"};
  for (int i = 0; i < bad.length; i++) {
   String msg = null; try { Updater.apply(root, bad[i]); } catch (IOException e) { msg = e.getMessage(); }
   Setup.chk("B: refused (" + why[i] + "), nothing changed", msg != null && msg.contains(why[i]) && "old main".equals(get(root, MAIN)) && !new File(work, "outside.txt").exists());
  }
 }

 /** A write that fails partway: everything already written goes back. */
 static void failedWrite() throws Exception {
  File root = install("install-fail");
  new File(root, "src/main/java/homeplanet/zz").mkdirs();
  put(root, "src/main/java/homeplanet/zz/Blocked.java/inside.txt", "x"); // a folder (not empty) where a file must go: that write fails
  File z = zip("fail.zip", "top", "pom.xml", "<version>4B.70</version>", BAT, "new bat", MAIN, "new main", "src/main/java/homeplanet/New.java", "new",
    "src/main/java/homeplanet/zz/Blocked.java", "cannot");
  String msg = null; try { Updater.apply(root, z); } catch (IOException e) { msg = e.getMessage(); }
  Setup.chk("F: a failed write: refused, and the files written before it put back", msg != null && "old main".equals(get(root, MAIN)) && "old bat".equals(get(root, BAT))
    && "<version>4B.68</version>".equals(get(root, "pom.xml")) && get(root, "src/main/java/homeplanet/New.java") == null && "old, dropped by main".equals(get(root, "src/main/java/homeplanet/Gone.java")));
 }
}
