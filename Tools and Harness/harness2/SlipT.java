import java.io.*; import java.util.*; import homeplanet.core.*;
/**
 * Slipstream follows FTL (5.63): before a patch, Slipstream's modman.cfg is pointed at the FTL the station plays, when it
 * names none, a folder that's gone, or another copy of FTL still there (FTL moved: the patch failed, or went into the old
 * copy). Its other settings are kept. prepareConfig is called by reflection, so this compiles against 5.62 (void) too.
 * args: gamedir, work
 */
public class SlipT {
 static String prepare(File slip) throws Exception {
  Object said = Slipstream.class.getMethod("prepareConfig", File.class).invoke(null, slip);
  return said == null ? null : said.toString();
 }
 static Properties read(File cfg) throws IOException {
  Properties p = new Properties(); InputStream in = new FileInputStream(cfg);
  try { p.load(in); } finally { in.close(); }
  return p;
 }
 static void write(File cfg, String dats) throws IOException {
  Properties p = new Properties();
  if (dats != null) p.setProperty("ftl_dats_path", dats);
  p.setProperty("update_catalog", "true"); p.setProperty("never_run_ftl", "false"); p.setProperty("manager_update_interval", "4");
  OutputStream out = new FileOutputStream(cfg);
  try { p.store(out, "Slipstream's own settings"); } finally { out.close(); }
 }
 static boolean points(File cfg, File game) throws IOException {
  String d = read(cfg).getProperty("ftl_dats_path");
  return d != null && new File(d).getCanonicalFile().equals(game.getCanonicalFile());
 }
 static boolean kept(File cfg) throws IOException {
  Properties p = read(cfg);
  return "true".equals(p.getProperty("update_catalog")) && "false".equals(p.getProperty("never_run_ftl")) && "4".equals(p.getProperty("manager_update_interval"));
 }
 public static void main(String[] a) throws Exception {
  File game = new File(a[0]).getAbsoluteFile(), work = new File(a[1]).getAbsoluteFile(); SafeFiles.deleteTree(work);
  File slip = new File(work, "slipstream"); slip.mkdirs();
  File cfg = new File(slip, "modman.cfg");
  HomePlanet.datsPath = game;

  write(cfg, null);
  prepare(slip);
  Setup.chk("S: no FTL folder in Slipstream's settings: the station's is filled in, the rest kept", points(cfg, game) && kept(cfg));

  write(cfg, game.getPath());
  byte[] before = java.nio.file.Files.readAllBytes(cfg.toPath());
  String said = prepare(slip);
  Setup.chk("S: already the station's FTL: the file isn't touched, nothing to note", said == null && Arrays.equals(before, java.nio.file.Files.readAllBytes(cfg.toPath())));

  File old = new File(work, "old FTL"); old.mkdirs(); new File(old, "ftl.dat").createNewFile();
  write(cfg, old.getPath());
  said = prepare(slip);
  Setup.chk("S: FTL moved, its old copy still there: Slipstream pointed at the FTL the station plays, the rest kept (" + said + ")",
    points(cfg, game) && kept(cfg) && said != null && said.contains(game.getPath()) && said.contains(old.getPath()));

  File gone = new File(work, "gone FTL");
  write(cfg, gone.getPath());
  said = prepare(slip);
  Setup.chk("S: FTL moved, its old folder gone: Slipstream pointed at the FTL the station plays, the change noted (" + said + ")",
    points(cfg, game) && kept(cfg) && said != null && said.contains(game.getPath()));

  Setup.done();
 }
}
