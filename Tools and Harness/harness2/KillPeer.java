import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/**
 * The station in its own process, for KillT: opens a fleet, says READY, and on GO does one action ("save", a Cargo Bay
 * save between two ships and the Cargo Hold; "receive", a ship arriving in a trade; "home", an expedition's return;
 * "board"; "dock"; "disband"; "convert", a fleet from before 6.0 opened and so converted), then says DONE and stops dead. With a count N, it stops dead (Runtime.halt, as a crash or a power cut
 * would: no undo, no shutdown) just before the Nth file it would write, rename or delete once GO is given: a test-only
 * SecurityManager counts them, so nothing in the station is changed for the test (Java 8 to 23; on a Java without one, ops=-1
 * and only random kills are made). args: gamedir, saves, action, N (0:
 * never), and "trace" to print each file operation.
 */
public class KillPeer {
 static volatile boolean counting, trace;
 static volatile int at, n;

 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), saves = new File(a[1]);
  String action = a[2];
  at = Integer.parseInt(a[3]);
  trace = a.length > 4 && a[4].equals("trace");
  HomePlanet.propFile = new File(saves.getParentFile(), "station.cfg"); // its own settings, beside its saves
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive(); HomePlanet.immersiveNotifications = false;
  boolean convert = action.equals("convert"); // an old fleet: opening it is the action (its conversion), so it opens after GO
  Vault v = convert ? null : Setup.open(game, saves);
  if (v != null) { v.storage(); v.takeStock(); }
  boolean counts = true;
  try { System.setSecurityManager(new Killer()); }
  catch (UnsupportedOperationException e) { counts = false; } // Java 24 on: no SecurityManager, so no stop at a chosen file (KillT kills at random moments only)
  System.out.println("READY"); System.out.flush();
  new BufferedReader(new InputStreamReader(System.in)).readLine(); // GO
  long t0 = System.nanoTime();
  counting = true;
  System.out.println("GO"); System.out.flush();
  try { if (convert) { Vault w = Setup.open(game, saves); w.storage(); w.takeStock(); } else run(v, action, new File(saves.getParentFile(), "kill-inputs")); }
  catch (Throwable t) { counting = false; System.out.println("ERROR " + t); t.printStackTrace(System.out); System.out.flush(); Runtime.getRuntime().halt(2); }
  counting = false;
  System.out.println("DONE ops=" + (counts ? n : -1) + " ms=" + (System.nanoTime() - t0) / 1000000); System.out.flush();
  Runtime.getRuntime().halt(0);
 }

 static void run(Vault v, String action, File inputs) throws Exception {
  if (action.equals("save")) save(v);
  else if (action.equals("receive")) {
   byte[] pkg = SafeFiles.read(new File(inputs, "package.zip")), sav = SafeFiles.read(new File(inputs, "ship.sav"));
   SavedGameState gs = new SavedGameParser().readSavedGame(new File(inputs, "ship.sav"));
   v.receive(pkg, sav, gs, "kill-test#1", "Commander Bree");
  }
  else if (action.equals("home")) { for (Assignments.Away x : Assignments.away(v)) Assignments.bringHome(v, x, x.result()); }
  else if (action.equals("board")) { for (Ship s : v.docked()) if (s.save() != null) { v.board(s); return; } throw new IOException("no ship to board"); }
  else if (action.equals("dock")) v.dock();
  else if (action.equals("disband")) v.disband();
  else throw new IOException("unknown action " + action);
 }

 /** A Cargo Bay save as the Cargo Bay makes one (5.71: one Transaction, one note): a crew member and a weapon from one ship to the Cargo Hold, an augment from the hold to another ship, ten scrap the hold pays her, then its log entry. */
 static void save(Vault v) throws Exception {
  Ship from = null, to = null;
  for (Ship s : v.docked()) {
   SavedGameState g = s.save();
   if (g == null) continue;
   if (from == null && !SaveHelper.getOwnCrew(g.getPlayerShip()).isEmpty() && !g.getPlayerShip().getWeaponList().isEmpty()) from = s;
   else if (to == null && g.getPlayerShip().getAugmentIdList().size() < 3) to = s;
  }
  if (from == null || to == null) throw new IOException("no two ships to trade between");
  Vault.Copy ca = v.readCopy(from), cb = v.readCopy(to), ch = v.readCopy(v.storage());
  ShipState sa = ca.save.getPlayerShip(), sb = cb.save.getPlayerShip(), sh = ch.save.getPlayerShip();
  CrewState c = SaveHelper.getOwnCrew(sa).get(0);
  sa.getCrewList().remove(c);
  SaveHelper.placeCrew(sh, c, true); sh.getCrewList().add(c);
  WeaponState w = sa.getWeaponList().get(0);
  SaveHelper.removeWeapon(sa, w); sh.getWeaponList().add(w);
  String aug = sh.getAugmentIdList().remove(0); sb.getAugmentIdList().add(aug);
  sh.setScrapAmt(sh.getScrapAmt() - 10); sa.setScrapAmt(sa.getScrapAmt() + 10);
  v.begin().put(from, ca.save, ca.hash).put(to, cb.save, cb.hash).put(v.storage(), ch.save, ch.hash).commit();
  HistoryLog.entry("TRADE", from.name + " <-> Spacedock Storage", Arrays.asList(c.getName() + " and " + w.getWeaponId() + " to the Cargo Hold", aug + " to " + to.name),
    Event.of("TRADE").put("what", "kill_test").put("ship_id", from.id));
 }

 /** Counts the files written, renamed and deleted once GO is given, and stops the process dead at the Nth. */
 static final class Killer extends SecurityManager {
  @Override public void checkPermission(java.security.Permission p) { }
  @Override public void checkPermission(java.security.Permission p, Object context) { }
  @Override public void checkWrite(String f) { hit("write ", f); }
  @Override public void checkDelete(String f) { hit("delete ", f); }
  private void hit(String what, String f) {
   if (!counting) return;
   int k = ++n;
   if (trace) { System.out.println("op " + k + " " + what + f); System.out.flush(); }
   if (k == at) Runtime.getRuntime().halt(137);
  }
 }
}
