import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Save safety: damaged files are never written over, and nothing is deleted on their say-so. args: gamedir, world saves (from WorldT), work */
public class SafeT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 damagedFiles(work);
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
 static boolean saveOk() { try { ShipDesign.save(ShipDesign.load()); CompanionMod.save(CompanionMod.load()); return true; } catch (IOException e) { return false; } }
}
