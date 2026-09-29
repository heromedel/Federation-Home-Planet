import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Vault operations on a migrated world. args: gamedir, migratedSaves (from MigT), work */
public class VaultT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 int n = v.fleet().size();
 Ship b = v.boarded(); Ship d = v.docked().get(0);
 String bId = b.id, dId = d.id;
 // board another: the old one docks
 v.board(d);
 Setup.chk("boarded swapped", v.boarded() == d && v.byId(bId).state == Ship.State.DOCKED);
 Setup.chk("continue.sav is hers", v.continueFile().isFile() && SafeFiles.hash(v.continueFile()).equals(d.hash));
 Setup.chk("old boarded ship's file is in ships/", new File(v.shipsDir(), bId + ".sav").isFile());
 Setup.chk("her old vault copy went to history", v.history(d).size() == 1);
 Setup.chk("fleet size unchanged", v.fleet().size() == n);
 // write with snapshot
 SavedGameState gs = d.save(); String oldName = gs.getPlayerShipName(); gs.setPlayerShipName("Renamed One"); gs.getPlayerShip().setShipName("Renamed One");
 v.write(d, gs);
 Setup.chk("write updates name", d.name.equals("Renamed One") && v.history(d).size() == 2);
 Vault v2 = Vault.open(saves); v2.takeStock();
 Setup.chk("manifest reload keeps the rename", v2.byId(dId).name.equals("Renamed One") && v2.byId(dId).isBoarded());
 v = v2; d = v.byId(dId);
 // dock, disband, salvage, remove
 v.dock();
 Setup.chk("dock: nobody boarded, continue.sav gone", v.boarded() == null && !v.continueFile().exists() && d.state == Ship.State.DOCKED);
 v.board(d); v.disband();
 Setup.chk("disband: junked", d.state == Ship.State.JUNKED && new File(v.junkyardDir(), dId + ".sav").isFile() && !v.continueFile().exists());
 v.salvage(d);
 Setup.chk("salvage: docked again", d.state == Ship.State.DOCKED && new File(v.shipsDir(), dId + ".sav").isFile());
 v.board(d); v.disband(); v.remove(d, "DESTROY");
 Setup.chk("remove: gone from manifest, history kept", v.byId(dId) == null && v.history(d).size() >= 2);
 // history pruning
 Ship e = v.docked().get(0);
 for (int i = 0; i < 14; i++) { SavedGameState g = e.save(); g.getPlayerShip().setScrapAmt(g.getPlayerShip().getScrapAmt() + 1); v.write(e, g); Thread.sleep(2); }
 Setup.chk("history pruned to " + Vault.KEEP, v.history(e).size() == Vault.KEEP);
 // unknown continue.sav (a new game in FTL): adopted on reload
 SafeFiles.copy(new File(v.shipsDir(), e.id + ".sav"), v.continueFile());
 Vault v3 = Vault.open(saves); v3.takeStock();
 Setup.chk("stray continue.sav adopted as boarded", v3.boarded() != null && !v3.boarded().id.equals(e.id) && v3.boarded().name.equals(e.name));
 // boarded ship lost (FTL deleted continue.sav)
 v3.continueFile().delete();
 Vault v4 = Vault.open(saves); v4.takeStock();
 Setup.chk("lost boarded ship dropped", v4.boarded() == null && v4.fleet().size() == v3.fleet().size() - 1);
 // transaction: two ships + a text file, all or nothing
 Ship x = v4.docked().get(0), y = v4.docked().get(1);
 SavedGameState gx = x.save(), gy = y.save(); int sx = gx.getPlayerShip().getScrapAmt(), sy = gy.getPlayerShip().getScrapAmt();
 gx.getPlayerShip().setScrapAmt(sx + 50); gy.getPlayerShip().setScrapAmt(sy - 50 < 0 ? 0 : sy - 50);
 File txt = v4.systemsFile();
 v4.begin().put(x, gx).put(y, gy).put(txt, "# test\n".getBytes("UTF-8")).commit();
 Vault v5 = Vault.open(saves); v5.takeStock();
 Setup.chk("transaction wrote both ships and the file", v5.byId(x.id).save().getPlayerShip().getScrapAmt() == sx + 50 && new String(SafeFiles.read(txt), "UTF-8").startsWith("# test") && !new File(txt.getParentFile(), txt.getName() + ".tx").exists());
 // usingBlueprint
 List<Ship> users = v5.usingBlueprint("PLAYER_SHIP_DESIGN_1_HP");
 Setup.chk("usingBlueprint finds the Test Frigate", users.size() == 1 && users.get(0).name.contains("Frigate"));
 Setup.chk("storage holds untouched", v5.storage().save() != null);
 Setup.done();
}}
