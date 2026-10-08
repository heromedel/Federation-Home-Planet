import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/**
 * Who a ship is (6.10, heromedel's Plan N): her career and origin in her record, the one-time check of a fleet from
 * before 6.10 (her fingerprint or her history), her mark in every save, saves and records found where no ship holds
 * them (and the player's word on them), Restore marking again, and Destroy in a career sending her on to Sandbox Mode.
 * args: gamedir, world saves (from WorldT; their records are made as before 6.10 here), work
 */
public class IdT {
 static File saves;
 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
  saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
  HomePlanet.savedGameParser = new SavedGameParser(); if (DataManager.get() == null) { DefaultDataManager dm = new DefaultDataManager(game); DataManager.setInstance(dm); dm.setDLCEnabledByDefault(true); }
  before610(Vault.rootOf(saves, Vault.SANDBOX)); // the world as a station before 6.10 left it, whatever made it
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();

  // 1. the one-time check: every ship of a fleet from before 6.10 accepted, her career and origin written, her save marked
  boolean all = true, pre = true, marked = true; String origins = "";
  for (Ship s : ships(v)) {
   ShipStore.Record r = ShipStore.read(v.folderOf(s));
   if (r == null || !Vault.SANDBOX.equals(r.career) || r.origin.isEmpty()) all = false;
   else { if (!r.origin.contains(".pre-6.10.") || !r.origin.contains(".verified-")) pre = false; origins += r.origin + " "; }
   ShipMark.Found m = ShipMark.read(v.fileOf(s));
   if (m == null || !s.id.equals(m.id) || !Vault.SANDBOX.equals(m.career)) marked = false;
  }
  Setup.chk("C: nothing waits on the player: every ship of the fleet is its own", v.found().isEmpty());
  Setup.chk("C: every record has her career and origin", all);
  Setup.chk("C: origins from before 6.10 say so, and what proved them (" + origins.trim() + ")", pre);
  Setup.chk("C: every save carries her mark", marked);
  Setup.chk("C: the check is logged, ship by ship", log(v).contains("what=checked"));

  // 2. a save edited elsewhere (her fingerprint gone), with her history in the log: accepted by her history
  Ship x = ships(v).get(0); String xId = x.id;
  unknow(v.folderOf(x));
  editSave(v.fileOf(x));
  v = reopen(game);
  ShipStore.Record xr = ShipStore.read(v.folderOf(v.byId(xId)));
  Setup.chk("H: her save edited elsewhere, her history in the log: accepted by it (" + xr.origin + ")", v.byId(xId) != null && v.found().isEmpty() && xr.origin.contains("verified-log-"));
  Setup.chk("H: her edited save marked again", xId.equals(ShipMark.read(v.fileOf(v.byId(xId))).id));

  // 3. a ship's folder with no evidence (a copy made by hand, under an id this fleet never knew): offered, never listed
  Ship y = ships(v).get(1);
  String ghostId = "0123456789abcdef";
  File ghost = new File(v.shipyardDir(), ShipStore.stem("Ghost", ghostId));
  copyAs(v.folderOf(y), ghost, ghostId, "Ghost");
  editSave(ShipStore.sav(ghost));
  int before = v.fleet().size();
  v = reopen(game);
  Vault.Found g = only(v, Vault.Found.Kind.UNPROVEN);
  Setup.chk("U: a folder nothing proves: offered, not in the fleet", g != null && ghostId.equals(g.id) && v.byId(ghostId) == null && v.fleet().size() == before);
  v.decline(g);
  v = reopen(game);
  Setup.chk("U: said no to: not asked again, still not in the fleet", only(v, Vault.Found.Kind.UNPROVEN) == null && v.byId(ghostId) == null);

  // 4. a record with no save: her newest version put back; then with none, rebuilt from her records
  Ship z = ships(v).get(2); String zId = z.id, zName = z.name; File zDir = v.folderOf(z);
  List<String> crewNames = new ArrayList<String>();
  File[] cf = new File(zDir, "crew").listFiles(); if (cf != null) for (File c : cf) if (c.getName().endsWith(".xml")) crewNames.add(CrewRegister.readFile(c).getProperty("name"));
  String zClass = HomePlanet.savedGameParser.readSavedGame(v.fileOf(z)).getPlayerShipBlueprintId();
  v.fileOf(z).delete();
  v = reopen(game);
  Vault.Found ns = only(v, Vault.Found.Kind.NO_SAVE);
  Setup.chk("S: her save gone: asked about, her newest version offered, off the list meanwhile", ns != null && zId.equals(ns.id) && "version".equals(ns.fix()) && v.byId(zId) == null);
  v.accept(ns);
  Setup.chk("S: put back: she's in the fleet with a save, marked", v.byId(zId) != null && v.fileOf(v.byId(zId)).isFile() && zId.equals(ShipMark.read(v.fileOf(v.byId(zId))).id));
  v.fileOf(v.byId(zId)).delete(); SafeFiles.deleteTree(ShipStore.versions(zDir));
  v = reopen(game);
  ns = only(v, Vault.Found.Kind.NO_SAVE);
  Setup.chk("S: nothing of her save left: rebuild offered", ns != null && "rebuild".equals(ns.fix()));
  v.accept(ns);
  SavedGameState rz = v.byId(zId) == null ? null : v.byId(zId).save();
  List<String> rebuilt = new ArrayList<String>(); if (rz != null) for (CrewState c : rz.getPlayerShip().getCrewList()) rebuilt.add(c.getName());
  Setup.chk("S: rebuilt: her class, her name, her crew from their files (" + rebuilt + " for " + crewNames + ")", rz != null && zClass.equals(rz.getPlayerShipBlueprintId()) && zName.equals(rz.getPlayerShipName())
    && (crewNames.isEmpty() || new HashSet<String>(rebuilt).equals(new HashSet<String>(crewNames))));
  Setup.chk("S: the rebuild logged", log(v).contains("what=rebuilt"));

  // 5. Restore puts an older save back marked
  Ship r5 = ships(v).get(0);
  List<File> hist = v.history(r5);
  File unmarked = null; for (File f : hist) if (ShipMark.read(f) == null) { unmarked = f; break; }
  if (unmarked != null) { v.restore(r5, unmarked); }
  Setup.chk("R: an unmarked version restored comes back marked", unmarked != null && r5.id.equals(ShipMark.read(v.fileOf(r5)).id));

  // 6. a loose save in a career's shipyard: never taken into the career; sent to Sandbox Mode's fleet when the player says
  Vault n = Vault.switchFleet(Vault.NORMAL); HomePlanet.immersiveMode = true;
  Career.start(false, false, CareerRules.of(CareerRules.NORMAL));
  n.takeStock();
  int careerShips = n.fleet().size();
  File loose = new File(n.shipyardDir(), "Dropped In.sav");
  SaveHelper.writeSavedGame(loose, Commission.build("PLAYER_SHIP_ROCK", "Dropped In", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(5)));
  n.takeStock();
  Vault.Found lf = only(n, Vault.Found.Kind.LOOSE);
  Setup.chk("L: a loose save in a career: offered, not adopted", lf != null && "Dropped In".equals(lf.name) && n.fleet().size() == careerShips);
  String said = n.accept(lf);
  File sandShipyard = new File(Vault.rootOf(saves, Vault.SANDBOX), "shipyard");
  File sent = null; for (File d : sandShipyard.listFiles()) if (d.getName().startsWith("Dropped In.")) sent = d;
  ShipStore.Record sr = sent == null ? null : ShipStore.read(sent);
  Setup.chk("L: sent to Sandbox Mode's fleet, the career untouched (" + said + ")", !loose.exists() && n.fleet().size() == careerShips && sr != null && Vault.SANDBOX.equals(sr.career)
    && sr.origin.startsWith("taken_in." + HomePlanet.APP_VERSION + ".") && "true".equals(ShipStore.notes(sent, "mark").getProperty("marked")));

  // 7. Destroy in a career, the other way: on to Sandbox Mode's Junkyard with her crew; gone from the career
  Ship wreck = n.adoptJunked(Commission.build("PLAYER_SHIP_CIRCLE", "Old Faithful", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(6)));
  n.takeStock();
  String wId = wreck.id;
  String told = n.sendToSandbox(wreck);
  String[] fate = ShipStore.fate(n.folderOfId(wId));
  File sandJunk = new File(Vault.rootOf(saves, Vault.SANDBOX), "junkyard");
  File moved = null; for (File d : sandJunk.listFiles()) if (d.getName().startsWith("Old Faithful.")) moved = d;
  Setup.chk("D: gone from the career, her fate there transferred to Sandbox Mode (" + told + ")", n.byId(wId) == null && fate != null && "TRANSFERRED".equals(fate[0]) && "Sandbox Mode".equals(fate[2]));
  Setup.chk("D: in Sandbox Mode's Junkyard, her record Sandbox's, her origin kept, her save marked for it", moved != null && Vault.SANDBOX.equals(ShipStore.read(moved).career)
    && ShipStore.read(moved).origin.startsWith("derelict." + HomePlanet.APP_VERSION + ".") && Vault.SANDBOX.equals(ShipMark.read(ShipStore.sav(moved)).career));
  Setup.chk("D: logged in the career", log(n).contains("SENT_TO_SANDBOX"));

  // and Sandbox Mode, opened: both there, their arrival logged
  Vault s = Vault.switchFleet(Vault.SANDBOX); HomePlanet.immersiveMode = false; s.takeStock();
  boolean dropped = false, faithful = false;
  for (Ship q : ships(s)) { if ("Dropped In".equals(q.name) && q.state == Ship.State.DOCKED) dropped = true; if ("Old Faithful".equals(q.name) && q.state == Ship.State.JUNKED) faithful = true; }
  String foundNow = ""; for (Vault.Found f : s.found()) foundNow += f.kind + " " + f.name + " (" + f.file.getName() + ") ";
  Setup.chk("A: Sandbox Mode has them: Dropped In docked, Old Faithful in the Junkyard, nothing else waiting (" + foundNow.trim() + ")", dropped && faithful && s.found().isEmpty());
  Setup.chk("A: their arrival logged in Sandbox Mode's log", log(s).contains("what=arrived"));
  Setup.done();
 }
 static List<Ship> ships(Vault v) { List<Ship> out = new ArrayList<Ship>(v.docked()); out.addAll(v.junked()); return out; }
 static Vault reopen(File game) throws Exception { Vault v = Vault.open(saves); v.takeStock(); return v; }
 static Vault.Found only(Vault v, Vault.Found.Kind k) { for (Vault.Found f : v.found()) if (f.kind == k) return f; return null; }
 static String log(Vault v) throws IOException { File f = new File(v.root, "logs/events.log"); return f.isFile() ? new String(SafeFiles.read(f), "UTF-8") : ""; }
 /** A fleet as a station before 6.10 left it: no career or origin in any record, no mark in any save (her fingerprint its save's). */
 static void before610(File root) throws IOException {
  for (String where : new String[] {"shipyard", "junkyard"}) for (File d : ShipStore.folders(new File(root, where))) {
   ShipStore.Record r = ShipStore.read(d); if (r == null) continue;
   File sav = ShipStore.sav(d);
   if (sav.isFile()) { SafeFiles.write(sav, ShipMark.strip(SafeFiles.read(sav))); r.hash = SafeFiles.hash(sav); }
   r.career = ""; r.origin = ""; r.sections.remove("mark");
   SafeFiles.write(ShipStore.xml(d), ShipStore.bytes(r));
  }
 }
 /** Her record as from before 6.10: no career, no origin. */
 static void unknow(File dir) throws IOException { ShipStore.Record r = ShipStore.read(dir); r.career = ""; r.origin = ""; SafeFiles.write(ShipStore.xml(dir), ShipStore.bytes(r)); }
 /** Her save changed by another tool (scrap up one), the station's fingerprint of it no longer matching. */
 static void editSave(File f) throws IOException { SavedGameState gs = HomePlanet.savedGameParser.readSavedGame(f); gs.getPlayerShip().setScrapAmt(gs.getPlayerShip().getScrapAmt() + 1); SaveHelper.writeSavedGame(f, gs); }
 /** A ship's folder copied by hand under another name and id, her record from before 6.10. */
 static void copyAs(File from, File to, String id, String name) throws IOException {
  to.mkdirs();
  SafeFiles.write(ShipStore.sav(to), SafeFiles.read(ShipStore.sav(from)));
  ShipStore.Record r = ShipStore.read(from), c = new ShipStore.Record(id);
  c.name = name; c.state = r.state; c.dlc = r.dlc; c.hash = r.hash; c.marks = r.marks;
  SafeFiles.write(ShipStore.xml(to), ShipStore.bytes(c));
 }
}
