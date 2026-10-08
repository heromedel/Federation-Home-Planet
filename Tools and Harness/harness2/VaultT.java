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
 int k0 = v.history(d).size(); // 6.10: marking her save at the opening kept her unmarked version first
 v.board(d);
 Setup.chk("boarded swapped", v.boarded() == d && v.byId(bId).state == Ship.State.DOCKED);
 Setup.chk("continue.sav is hers", v.continueFile().isFile() && SafeFiles.hash(v.continueFile()).equals(d.hash));
 Setup.chk("old boarded ship's file is in her shipyard folder", v.fileOf(v.byId(bId)).isFile() && v.fileOf(v.byId(bId)).getParentFile().getParentFile().equals(v.shipyardDir()));
 Setup.chk("her old vault copy went to history", v.history(d).size() == k0 + 1);
 Setup.chk("fleet size unchanged", v.fleet().size() == n);
 // write with snapshot
 SavedGameState gs = d.save(); String oldName = gs.getPlayerShipName(); gs.setPlayerShipName("Renamed One"); gs.getPlayerShip().setShipName("Renamed One");
 v.write(d, gs);
 Setup.chk("write updates name", d.name.equals("Renamed One") && v.history(d).size() == k0 + 2);
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
 Setup.chk("cloud copy of a docked ship: set aside, not adopted", vc.boarded() == null && !vc.continueFile().exists() && vc.takeCloudCopy().contains(e.name) && vc.fleet().size() == before);
 // an older copy (5.97, concerns 6): one of her kept versions, not her save as it is now
 File older = vc.history(vc.byId(e.id)).get(0);
 Setup.chk("an older copy differs from her save as it is now", !SafeFiles.hash(older).equals(SafeFiles.hash(vc.fileOf(vc.byId(e.id)))));
 SafeFiles.copy(older, vc.continueFile());
 Vault vo = Vault.open(saves); vo.takeStock();
 Setup.chk("an older cloud copy of a docked ship (one of her versions): set aside, not adopted", vo.boarded() == null && !vo.continueFile().exists() && vo.takeCloudCopy().contains(e.name) && vo.fleet().size() == before);
 // 6.08: the ship mark (concerns 6 closed): Board writes her count and the day into her save; a marked copy is set aside whatever it matches
 String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
 Ship g = null; for (Ship x : vo.docked()) if (!x.id.equals(e.id)) { g = x; break; }
 ShipMark.Found m0 = ShipMark.read(vo.fileOf(g)); int b0 = m0 == null ? 0 : m0.boards; // marked already if the fleet's one-time check (6.10) marked her
 vo.board(g);
 ShipMark.Found m1 = ShipMark.read(vo.continueFile());
 Setup.chk("M: Board marks her save: this career, her id, boarded once more, today", m1 != null && m1.career.equals(vo.slot) && m1.id.equals(g.id) && m1.boards == b0 + 1 && Integer.toString(m1.day).equals(today));
 SavedGameState gm = HomePlanet.savedGameParser.readSavedGame(vo.continueFile());
 vo.dock(); vo.board(g);
 ShipMark.Found m2 = ShipMark.read(vo.continueFile());
 Setup.chk("M: boarded again: twice, and only her mark in the save", m2 != null && m2.boards == b0 + 2 && m2.id.equals(g.id));
 vo.dock();
 gm.getPlayerShip().setScrapAmt(gm.getPlayerShip().getScrapAmt() + 7); // a copy of her from her first boarding that matches nothing kept
 SaveHelper.writeSavedGame(vo.continueFile(), gm);
 int fleetNow = vo.fleet().size();
 Vault vm = Vault.open(saves); vm.takeStock();
 String told = vm.takeCloudCopy();
 boolean asCloud = false; for (File f : ShipStore.versions(vm.historyOf(vm.byId(g.id)), true)) if (f.getName().startsWith("cloud-")) asCloud = true;
 Setup.chk("M: a marked copy of her that matches nothing kept: set aside in her records, not adopted", vm.boarded() == null && !vm.continueFile().exists() && vm.fleet().size() == fleetNow
   && told != null && told.contains(g.name) && told.contains("already in your fleet") && asCloud);
 // a ship that has left the fleet (the one destroyed above)
 SavedGameState gd = HomePlanet.savedGameParser.readSavedGame(vm.fileOf(vm.byId(g.id)));
 gd.getStateVars().clear();
 gd.setStateVar("fhp.ship." + vm.slot + "." + dId, 3);
 SaveHelper.writeSavedGame(vm.continueFile(), gd);
 Vault vd = Vault.open(saves); vd.takeStock();
 told = vd.takeCloudCopy();
 boolean dCloud = false; for (File f : ShipStore.versions(vd.folderOfId(dId), true)) if (f.getName().startsWith("cloud-")) dCloud = true;
 Setup.chk("M: a marked copy of a ship that has left: set aside in her memorial folder, not adopted", vd.boarded() == null && !vd.continueFile().exists() && vd.fleet().size() == fleetNow
   && told != null && told.contains("has left your fleet") && dCloud);
 // another career's ship
 gd.getStateVars().clear();
 String otherCareer = vd.slot.equals(Vault.NORMAL) ? Vault.HARD : Vault.NORMAL;
 gd.setStateVar("fhp.ship." + otherCareer + ".0123456789abcdef", 5);
 SaveHelper.writeSavedGame(vd.continueFile(), gd);
 Vault vx = Vault.open(saves); vx.takeStock();
 told = vx.takeCloudCopy();
 File[] aside = new File(vx.root, Vault.SET_ASIDE).listFiles();
 Setup.chk("M: another career's ship: set aside in set-aside/, not adopted, the message names her career", vx.boarded() == null && !vx.continueFile().exists() && vx.fleet().size() == fleetNow
   && aside != null && aside.length == 1 && aside[0].getName().startsWith(otherCareer + ".0123456789abcdef.") && told != null && told.contains(Vault.title(otherCareer)));
 // a ship sent away keeps no station's mark (Vault.receive strips it)
 byte[] markedSave = SafeFiles.read(vx.fileOf(vx.byId(g.id)));
 File stripped = new File(work, "stripped.sav"); SafeFiles.write(stripped, ShipMark.strip(markedSave));
 File markedFile = new File(work, "marked.sav"); SafeFiles.write(markedFile, markedSave);
 Setup.chk("M: a trade takes the mark off her save", ShipMark.read(markedFile) != null && ShipMark.read(stripped) == null);
 // unknown continue.sav (a new game in FTL): adopted on reload
 SavedGameState other = HomePlanet.savedGameParser.readSavedGame(v.fileOf(e));
 other.getPlayerShip().setScrapAmt(other.getPlayerShip().getScrapAmt() + 1000);
 for (java.util.Iterator<String> it = other.getStateVars().keySet().iterator(); it.hasNext(); ) if (it.next().startsWith("fhp.")) it.remove(); // a New Game in FTL carries no station's mark (6.10)
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
 // 6.08 (docs/BUGS.md, found 6.01): versions in the order their names were stamped, whatever their file times say
 File vf = new File(work, "order"); File vv = ShipStore.versions(vf); vv.mkdirs();
 String[] names = {"20261005-192053.sav", "20261005-192052-2.sav", "20261005-192051.sav", "20261005-192052.sav", "20261005-192052-10.sav"};
 long t = 1759692052000L;
 for (int i = 0; i < names.length; i++) { File f = new File(vv, names[i]); SafeFiles.write(f, new byte[] {(byte) i}); f.setLastModified(t); }
 new File(vv, names[0]).setLastModified(t - 60000); // the newest name, the oldest file time
 List<File> ordered = ShipStore.versions(vf, false);
 StringBuilder got = new StringBuilder(); for (File f : ordered) got.append(f.getName()).append(' ');
 Setup.chk("O: versions ordered by their stamps, file times aside (" + got.toString().trim() + ")",
   got.toString().trim().equals("20261005-192051.sav 20261005-192052.sav 20261005-192052-2.sav 20261005-192052-10.sav 20261005-192053.sav"));
 Setup.done();
}}
