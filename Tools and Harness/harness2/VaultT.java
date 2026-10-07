import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Vault operations on the test world. args: gamedir, world saves (from WorldT), work */
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
 Setup.chk("old boarded ship's file is in her shipyard folder", v.fileOf(v.byId(bId)).isFile() && v.fileOf(v.byId(bId)).getParentFile().getParentFile().equals(v.shipyardDir()));
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
 Setup.chk("disband: junked", d.state == Ship.State.JUNKED && v.fileOf(d).isFile() && v.fileOf(d).getParentFile().getParentFile().equals(v.junkyardDir()) && !v.continueFile().exists());
 v.salvage(d);
 Setup.chk("salvage: docked again", d.state == Ship.State.DOCKED && v.fileOf(d).isFile() && v.fileOf(d).getParentFile().getParentFile().equals(v.shipyardDir()));
 v.board(d); v.disband(); v.remove(d, "DESTROY");
 Setup.chk("remove: gone from manifest, history kept", v.byId(dId) == null && v.history(d).size() >= 2);
 // history pruning
 Ship e = v.docked().get(0);
 for (int i = 0; i < 14; i++) { SavedGameState g = e.save(); g.getPlayerShip().setScrapAmt(g.getPlayerShip().getScrapAmt() + 1); v.write(e, g); Thread.sleep(2); }
 Setup.chk("history pruned to " + Vault.KEEP, v.history(e).size() == Vault.KEEP);
 // Steam Cloud's copy of a docked ship in continue.sav: set aside in her records, not a second ship
 int before = v.fleet().size();
 SafeFiles.copy(v.fileOf(e), v.continueFile());
 Vault vc = Vault.open(saves); vc.takeStock();
 Setup.chk("cloud copy of a docked ship: set aside, not adopted", vc.boarded() == null && !vc.continueFile().exists() && e.name.equals(vc.takeCloudCopy()) && vc.fleet().size() == before);
 // unknown continue.sav (a new game in FTL): adopted on reload
 SavedGameState other = HomePlanet.savedGameParser.readSavedGame(v.fileOf(e));
 other.getPlayerShip().setScrapAmt(other.getPlayerShip().getScrapAmt() + 1000);
 SaveHelper.writeSavedGame(v.continueFile(), other);
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
 List<Ship> users = v5.usingBlueprint("PLAYER_SHIP_STEALTH_HP");
 Setup.chk("usingBlueprint finds the Test Stealth", users.size() == 1 && users.get(0).name.equals("Test Stealth"));
 Setup.chk("storage holds untouched", v5.storage().save() != null);
 // kept versions (5.61): a victory copy and a final battle's waiting copy are never pruned with her ordinary versions
 Ship kv = v5.docked().get(0);
 File kd = ShipStore.versions(v5.historyOf(kv)); kd.mkdirs();
 File victory = new File(kd, "victory-20200101-000000.sav"), waiting = new File(v5.historyOf(kv), "final-battle.sav"), cloud = new File(kd, "cloud-20200101-000000.sav");
 for (File f : new File[] {victory, waiting, cloud}) { SafeFiles.copy(v5.fileOf(kv), f); f.setLastModified(946684800000L); } // the oldest files there
 for (int i = 0; i < 11; i++) { SavedGameState kg = v5.readCopy(kv).save; kg.getPlayerShip().setScrapAmt(1000 + i); v5.write(kv, kg); }
 int ordinaryLeft = 0; for (File f : kd.listFiles()) if (f.getName().matches("\\d{8}-\\d{6}(-\\d+)?\\.sav")) ordinaryLeft++;
 Setup.chk("V: eleven new versions: the victory copy, the waiting final battle copy and the cloud copy stay; ten ordinary versions (" + ordinaryLeft + ")",
   victory.isFile() && waiting.isFile() && cloud.isFile() && ordinaryLeft == Vault.KEEP);
 boolean special = false; for (File f : v5.history(kv)) if (!f.getName().matches("\\d{8}-\\d{6}(-\\d+)?\\.sav")) special = true;
 int savs = 1; for (File f : kd.listFiles()) if (f.getName().endsWith(".sav")) savs++; // and the waiting copy
 Setup.chk("V: her versions are the ordinary ones; the Records list shows the special copies too (" + v5.history(kv).size() + " of " + v5.kept(kv).size() + ")",
   v5.history(kv).size() == Vault.KEEP && !special && v5.kept(kv).size() == savs && v5.kept(kv).contains(victory) && v5.kept(kv).contains(waiting));
 Setup.done();
}}
