import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.SavedGameState; import homeplanet.core.*; import homeplanet.vault.*;
/** Ship history and recovery: restoring a kept version, and bringing back destroyed and lost ships (not scrapped ones). args: gamedir, world saves (from WorldT), work */
public class HistT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 if (v.boarded() != null) v.dock();
 restore(v);
 oldNames(v);
 lost(v);
 destroyedAndScrapped(v);
 voyage(Vault.get());
 Setup.done();
}
 static SavedGameState cont(Vault v) throws Exception { return homeplanet.core.HomePlanet.savedGameParser.readSavedGame(v.continueFile()); }
 static String newLines(Vault v, Ship s, int from) { String all = VoyageLog.read(v, s); return all.length() > from ? all.substring(from) : ""; }
 /** The voyage log: FTL's doings between looks, logged; the station's own changes not. */
 static void voyage(Vault v) throws Exception {
  if (v.boarded() == null) v.board(v.docked().get(0));
  v.takeStock();
  Ship b = v.boarded();
  int at = VoyageLog.read(v, b).length();
  // FTL: a jump, a battle won, a crew member lost and one hired, a weapon found, damage
  SavedGameState g = cont(v);
  g.setCurrentBeaconId(g.getCurrentBeaconId() + 1); g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + 1); g.setTotalShipsDefeated(g.getTotalShipsDefeated() + 1);
  g.setTotalScrapCollected(g.getTotalScrapCollected() + 30); g.getPlayerShip().setScrapAmt(g.getPlayerShip().getScrapAmt() + 30); g.getPlayerShip().setHullAmt(g.getPlayerShip().getHullAmt() - 5);
  String lostName = homeplanet.parser.SaveHelper.getOwnCrew(g.getPlayerShip()).get(0).getName();
  homeplanet.parser.SaveHelper.getOwnCrew(g.getPlayerShip()).get(1).setName("Voyage Newcomer");
  g.getPlayerShip().getCrewList().remove(homeplanet.parser.SaveHelper.getOwnCrew(g.getPlayerShip()).get(0));
  g.getPlayerShip().getWeaponList().add(homeplanet.parser.SaveHelper.newIdleWeapon("LASER_BURST_2"));
  homeplanet.parser.SaveHelper.writeSavedGame(v.continueFile(), g); b.invalidate(); v.takeStock();
  String l = newLines(v, b, at);
  Setup.chk("Y: a jump is logged, with hull, scrap and fuel", l.contains("Jumped") && l.contains("(-5)") && l.contains("(+30)"));
  Setup.chk("Y: the battle, and the crew lost and joined", l.contains("1 ship defeated") && l.contains("Crew lost: " + lostName) && l.contains("Crew joined: Voyage Newcomer"));
  Setup.chk("Y: what came aboard", l.contains("Aboard now: " + homeplanet.model.Items.title("LASER_BURST_2")));
  at = VoyageLog.read(v, b).length();
  int visited = VoyageLog.visited(v, b);
  g = cont(v); g.setSectorNumber(g.getSectorNumber() + 1); g.setCurrentBeaconId(0); g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + 1);
  homeplanet.parser.SaveHelper.writeSavedGame(v.continueFile(), g);
  v.observeBoarded(); // as the save watcher does
  l = newLines(v, b, at);
  Setup.chk("Y: a new sector, and her sectors visited go up", l.contains("Sector " + (g.getSectorNumber() + 1) + " reached") && VoyageLog.visited(v, b) == visited + 1);
  at = VoyageLog.read(v, b).length();
  Vault.Copy c = v.readCopy(b); c.save.getPlayerShip().setScrapAmt(c.save.getPlayerShip().getScrapAmt() - 10); v.begin().put(b, c.save, c.hash).commit();
  b.invalidate(); v.takeStock();
  Setup.chk("Y: the station's own change (a trade) isn't in her voyage log", newLines(v, b, at).isEmpty());
  g = cont(v); g.getPlayerShip().setHullAmt(g.getPlayerShip().getHullAmt() + 3);
  homeplanet.parser.SaveHelper.writeSavedGame(v.continueFile(), g); b.invalidate(); v.takeStock();
  Setup.chk("Y: a repair at a store, no jump", newLines(v, b, at).contains("Hull repaired"));
  System.out.print(VoyageLog.read(v, b));
 }
 static Ship named(Vault v, String name) { for (Ship s : v.all()) if (name.equals(s.name)) return s; return null; }
 static boolean departed(Vault v, String id) { for (Vault.Departed d : v.recoverable()) if (d.id.equals(id)) return true; return false; }
 static int scrapOf(File f) throws Exception { return HomePlanet.savedGameParser.readSavedGame(f).getPlayerShip().getScrapAmt(); }
 static void addScrap(Vault v, Ship s, int n) throws Exception {
  SavedGameState g = v.readCopy(s).save; g.getPlayerShip().setScrapAmt(g.getPlayerShip().getScrapAmt() + n); v.write(s, g);
 }

 /** Restore: an earlier version comes back, and the one it replaced is kept. */
 static void restore(Vault v) throws Exception {
  Ship s = named(v, "Test Engi");
  int before = s.save().getPlayerShip().getScrapAmt(), kept0 = v.history(s).size();
  addScrap(v, s, 100);
  Setup.chk("H: a change keeps the version before it", v.history(s).size() == kept0 + 1);
  List<File> h = v.history(s); File old = h.get(h.size() - 1);
  Setup.chk("H: the kept version is the one before the change", scrapOf(old) == before);
  v.restore(s, old);
  s.invalidate();
  Setup.chk("H: restore puts it back", s.save().getPlayerShip().getScrapAmt() == before);
  h = v.history(s);
  Setup.chk("H: the version it replaced is kept, newest", scrapOf(h.get(h.size() - 1)) == before + 100);
  v.restore(s, h.get(h.size() - 1)); s.invalidate();
  Setup.chk("H: and can be restored in turn", s.save().getPlayerShip().getScrapAmt() == before + 100);
  v.snapshot(s); int n = v.history(s).size(); v.snapshot(s);
  Setup.chk("H: the same version isn't kept twice", v.history(s).size() == n);
 }

 /** Lost in action: FTL deletes continue.sav; she can come back as the station last wrote her. */
 static void lost(Vault v) throws Exception {
  Ship s = named(v, "Test Stealth");
  v.board(s);
  addScrap(v, s, 37);
  int latest = s.save().getPlayerShip().getScrapAmt();
  List<File> h = v.history(s);
  Setup.chk("H: the boarded ship's new version is kept too", scrapOf(h.get(h.size() - 1)) == latest);
  v.continueFile().delete();
  v.reload();
  Setup.chk("H: continue.sav gone: she leaves the fleet", v.byId(s.id) == null);
  Setup.chk("H: and is recoverable, lost in action", departed(v, s.id) && v.recoverable().get(0).fate == Vault.Fate.LOST);
  Vault.Departed d = null; for (Vault.Departed x : v.recoverable()) if (x.id.equals(s.id)) d = x;
  Ship back = v.recover(d);
  Setup.chk("H: recovered: docked, as the station last wrote her", back.state == Ship.State.DOCKED && back.save() != null && back.save().getPlayerShip().getScrapAmt() == latest && "Test Stealth".equals(back.name));
  Setup.chk("H: no longer on the recoverable list", !departed(v, s.id));
  v.reload();
  Setup.chk("H: she stays after a reload", v.byId(s.id) != null);
 }

 /** Destroyed ships can come back; scrapped ones can't. */
 static void destroyedAndScrapped(Vault v) throws Exception {
  Ship a = named(v, "Test Kestrel");
  v.board(a); v.disband();
  v.remove(a, "DESTROY");
  Setup.chk("H: a destroyed ship is recoverable", departed(v, a.id));
  Ship b = named(v, "Test Engi");
  v.board(b); v.disband();
  v.remove(b, null);
  Setup.chk("H: a scrapped one is not", !departed(v, b.id));
  Vault.Departed d = null; for (Vault.Departed x : v.recoverable()) if (x.id.equals(a.id)) d = x;
  v.recover(d);
  boolean refused = false; try { v.recover(d); } catch (IOException e) { refused = true; }
  Setup.chk("H: she can't be recovered twice", refused && v.byId(a.id) != null);
 }
 /** A kept version named in local time (before UTC names), ahead of UTC: still sorted by when it was kept. */
 static void oldNames(Vault v) throws Exception {
  Ship s = named(v, "Test Engi");
  File dir = v.historyOf(s); dir.mkdirs();
  File old = new File(dir, "20991231-235959.sav");
  SafeFiles.copy(s.file(), old);
  old.setLastModified(System.currentTimeMillis() - 86400000L);
  addScrap(v, s, 7);
  List<File> h = v.history(s);
  Setup.chk("H: an old local-time name doesn't pass for the newest version", !h.get(h.size() - 1).equals(old) && h.contains(old));
  old.delete();
 }
}
