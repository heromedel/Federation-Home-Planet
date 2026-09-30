import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Save safety: damaged files are never written over, and nothing is deleted on their say-so. args: gamedir, world saves (from WorldT), work */
public class SafeT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 damagedFiles(work);
 staleSaves(v);
 halfwayFailure(v, work);
 failedBoardAndDock(v);
 historyOrder(v);
 Setup.done();
}
 /** A: a designs.xml or remodels.xml that doesn't read in full is left alone, and the art sweep deletes nothing. */
 static void damagedFiles(File work) throws Exception {
  File png = new File(work, "art.png");
  { InputStream in = DataManager.get().getResourceInputStream("img/ship/stealth_base.png"); java.nio.file.Files.copy(in, png.toPath()); in.close(); }
  List<ShipDesign> all = ShipDesign.load();
  ShipDesign d = ShipDesign.create(all); d.name = "Safe Test"; all.add(d);
  d.rooms.add(new ShipDesign.Room(4, 5, 2, 2)); d.rooms.add(new ShipDesign.Room(6, 5, 2, 2));
  d.art = ShipArt.importFile(png, d.id, "base");
  ShipDesign.save(all);
  CompanionMod.Remodel r = new CompanionMod.Remodel(); r.id = "PLAYER_SHIP_HARD_R1_HP"; r.base = "PLAYER_SHIP_HARD"; r.file = "blueprints.xml"; r.ship = "Safe Test"; r.made = "2026-09-30";
  CompanionMod.save(new ArrayList<CompanionMod.Remodel>(Arrays.asList(r)));
  File art = ShipArt.file(d.art);
  Setup.chk("A: set up: design, art and remodel on file", ShipDesign.intact() && CompanionMod.intact() && art.isFile() && ShipDesign.load().size() == 1 && CompanionMod.load().size() == 1);

  // damage designs.xml: one room loses its number
  File df = ShipDesign.file(); String good = new String(SafeFiles.read(df), "UTF-8");
  SafeFiles.writeText(df, good.replaceFirst("<room x=\"4\"", "<room x=\"four\""), false);
  String damagedHash = SafeFiles.hash(df);
  Setup.chk("A: a damaged designs.xml is seen as damaged", !ShipDesign.intact());
  boolean refused = false; try { ShipDesign.save(ShipDesign.load()); } catch (IOException e) { refused = e.getMessage().contains(df.getName()); }
  Setup.chk("A: saving over a damaged designs.xml is refused, with the file named", refused);
  Setup.chk("A: the damaged designs.xml is untouched", SafeFiles.hash(df).equals(damagedHash));
  Setup.chk("A: the art sweep deletes nothing while designs.xml is damaged", ShipArt.sweep() == 0 && art.isFile());
  SafeFiles.writeText(df, good, false);

  // damage remodels.xml the same way
  File rf = CompanionMod.remodelsFile(); String goodR = new String(SafeFiles.read(rf), "UTF-8");
  SafeFiles.writeText(rf, goodR.replace("<remodel id=", "<remodel broken id="), false); // not well-formed XML any more
  Setup.chk("A: a damaged remodels.xml is seen as damaged", !CompanionMod.intact());
  refused = false; try { CompanionMod.save(new ArrayList<CompanionMod.Remodel>()); } catch (IOException e) { refused = true; }
  Setup.chk("A: saving over a damaged remodels.xml is refused", refused && new String(SafeFiles.read(rf), "UTF-8").contains("broken"));
  Setup.chk("A: the art sweep deletes nothing while remodels.xml is damaged", ShipArt.sweep() == 0 && art.isFile());
  SafeFiles.writeText(rf, goodR, false);
  Setup.chk("A: once repaired, both save again", saveOk());
 }
 /** B: a copy read for editing isn't written back over a file FTL changed or removed since. */
 static void staleSaves(Vault v) throws Exception {
  Ship d = v.docked().get(0);
  Vault.Copy mine = v.readCopy(d);
  mine.save.getPlayerShip().setScrapAmt(mine.save.getPlayerShip().getScrapAmt() + 500); // the Cargo Bay's change
  // meanwhile FTL writes her: a different scrap amount
  Vault.Copy ftl = v.readCopy(d); ftl.save.getPlayerShip().setScrapAmt(7); SafeFiles.write(d.file(), SaveHelper.toBytes(ftl.save));
  String ftlHash = SafeFiles.hash(d.file());
  boolean stale = false; try { v.begin().put(d, mine.save, mine.hash).commit(); } catch (Vault.StaleException e) { stale = e.ship == d; }
  Setup.chk("B: a save FTL changed since it was read isn't written over", stale && SafeFiles.hash(d.file()).equals(ftlHash));
  Vault.Copy fresh = v.readCopy(d);
  v.begin().put(d, fresh.save, fresh.hash).commit();
  Setup.chk("B: a fresh copy saves normally", fresh.save.getPlayerShip().getScrapAmt() == 7);
  // the boarded ship's run ends in FTL: continue.sav is deleted
  Ship b = v.boarded(); Vault.Copy run = v.readCopy(b);
  File cont = v.continueFile(); byte[] keep = SafeFiles.read(cont); cont.delete();
  boolean gone = false; try { v.begin().put(b, run.save, run.hash).commit(); } catch (Vault.StaleException e) { gone = e.getMessage().contains("gone"); }
  Setup.chk("B: a ship whose run ended isn't brought back by a stale save", gone && !cont.exists());
  SafeFiles.write(cont, keep);
 }
 /** F: when the second file of a save can't be replaced, the first gets its old contents back. */
 static void halfwayFailure(Vault v, File work) throws Exception {
  Ship d = v.docked().get(1);
  String before = SafeFiles.hash(d.file());
  Vault.Copy c = v.readCopy(d); c.save.getPlayerShip().setScrapAmt(c.save.getPlayerShip().getScrapAmt() + 99);
  File blocker = new File(work, "blocker"); new File(blocker, "inside").mkdirs(); // a folder with something in it can't be replaced by a file
  boolean failed = false; try { v.begin().put(d, c.save, c.hash).put(blocker, "x".getBytes("UTF-8")).commit(); } catch (IOException e) { failed = true; }
  Setup.chk("F: a save that fails halfway puts the first file back", failed && SafeFiles.hash(d.file()).equals(before));
  Setup.chk("F: and leaves no temporary files behind", !new File(d.file().getParentFile(), d.file().getName() + ".tx").exists() && !new File(work, "blocker.tx").exists());
 }
 /** D and H: a Board or Dock that fails partway leaves every ship as she was, with no stray continue.sav. */
 static void failedBoardAndDock(Vault v) throws Exception {
  Ship was = v.boarded(), next = v.docked().get(0);
  // H: Dock can't write her into the ships folder (a folder sits where her file goes)
  File where = new File(v.shipsDir(), was.id + ".sav"); new File(where, "x").mkdirs();
  boolean failed = false; try { v.dock(); } catch (IOException e) { failed = true; }
  Setup.chk("H: a failed Dock leaves her boarded, continue.sav in place", failed && v.boarded() == was && v.continueFile().isFile());
  SafeFiles.deleteTree(where);
  v.dock();
  Setup.chk("H: once cleared, she docks", v.boarded() == null && was.state == Ship.State.DOCKED && where.isFile());
  // D: Board can't move her vault copy into her history (a file sits where her history folder goes)
  File hist = v.historyOf(next); SafeFiles.deleteTree(hist); SafeFiles.write(hist, new byte[] {1});
  failed = false; try { v.board(next); } catch (IOException e) { failed = true; }
  Setup.chk("D: a failed Board leaves no copy in continue.sav, and her still docked", failed && !v.continueFile().exists() && next.state == Ship.State.DOCKED && next.file().isFile());
  hist.delete();
  v.board(was);
  Setup.chk("D: once cleared, boarding works", v.boarded() == was && v.continueFile().isFile());
 }
 /** Pruning keeps the newest versions, even when many are written in the same second (…-2.sav, …-10.sav). */
 static void historyOrder(Vault v) throws Exception {
  Ship e = v.docked().get(0);
  byte[] beforeLast = null;
  for (int i = 0; i < 15; i++) {
   SavedGameParser.SavedGameState g = e.save(); g.getPlayerShip().setScrapAmt(1000 + i);
   beforeLast = SafeFiles.read(e.file());
   v.write(e, g);
  }
  List<File> h = v.history(e);
  Setup.chk("history keeps " + Vault.KEEP + ", the newest last", h.size() == Vault.KEEP && Arrays.equals(SafeFiles.read(h.get(h.size() - 1)), beforeLast));
 }
 static boolean saveOk() { try { ShipDesign.save(ShipDesign.load()); CompanionMod.save(CompanionMod.load()); return true; } catch (IOException e) { return false; } }
}
