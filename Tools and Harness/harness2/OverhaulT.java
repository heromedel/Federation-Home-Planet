import java.io.*; import java.util.*; import java.util.List; import java.awt.*; import javax.swing.*;
import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import net.blerf.ftl.constants.*;
import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*; import homeplanet.ui.*;
/**
 * Remodel's overhaul taken to the end on whole ships (Buggy Boy, 6.47): two rooms that hold systems removed, two new rooms
 * added and the lifted systems put in them, then Finalize pressed as a player would (the blueprint dialog and the pop-ups
 * answered). Her save against her save before: every crew member still aboard, standing in a room on FTL's own squares
 * (the layout's plus its X_OFFSET and Y_OFFSET: before 6.47 the crew were put that far off, out in space); one room state
 * per new room; the doors the new layout's; every system at its level; her goods as they were; the save read back whole.
 * The models whose blueprint gives the Medbay FTL's slot -2 (Engi B and C, Federation B, Slug C, Rock C, Stealth B) are
 * among them: before 6.47 the check refused them. Then a ship overhauled before 6.47, her crew off by the offset, has them
 * put back aboard at the station's next look; and the Refit tab's Retrofit and Remodel take their own clicks (her
 * picture reached under them). Needs a display (run.sh: xvfb-run).
 * args: gamedir, world saves (from WorldT), work, [a saves folder with more ships: each shipyard save under it is tried too]
 */
public class OverhaulT {
 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
  final Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  HomePlanet.datsPath = game; HomePlanet.save_location = saves;
  GuiT.watchPopUps();
  clickFinalize();
  final Object[] frame = new Object[1];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { MainFrame f = new MainFrame("FHP", "test"); f.setSize(1300, 800); f.setVisible(true); frame[0] = f; } });
  MainFrame f = (MainFrame) frame[0];
  List<Object[]> cases = new ArrayList<Object[]>();
  for (String cls : new String[] {"PLAYER_SHIP_HARD", "PLAYER_SHIP_CIRCLE", "PLAYER_SHIP_FED", "PLAYER_SHIP_MANTIS", "PLAYER_SHIP_ROCK",
    "PLAYER_SHIP_CRYSTAL", "PLAYER_SHIP_STEALTH", "PLAYER_SHIP_JELLY", "PLAYER_SHIP_ENERGY", "PLAYER_SHIP_ANAEROBIC",
    "PLAYER_SHIP_CIRCLE_2", "PLAYER_SHIP_CIRCLE_3", "PLAYER_SHIP_FED_2", "PLAYER_SHIP_JELLY_3", "PLAYER_SHIP_ROCK_3", "PLAYER_SHIP_STEALTH_2"}) {
   cases.add(new Object[] {cls, Commission.build(cls, "Overhaul " + cls, Difficulty.NORMAL, new Random(5))});
  }
  if (a.length > 3) for (File sf : savs(new File(a[3]), new ArrayList<File>())) {
   try {
    SavedGameState g = HomePlanet.savedGameParser.readSavedGame(sf);
    String id = g.getPlayerShip().getShipBlueprintId();
    if (DataManager.get().getShips().get(Retrofit.vanillaId(id)) == null) { System.out.println("SKIP " + sf.getName() + ": " + id + " is no model of FTL's"); continue; }
    cases.add(new Object[] {"real " + g.getPlayerShipName() + " (" + id + ")", g});
   } catch (Exception e) { System.out.println("SKIP " + sf.getName() + ": " + e); }
  }
  String only = System.getProperty("only");
  Ship last = null;
  for (Object[] c : cases) {
   if (only != null && !((String) c[0]).contains(only)) continue;
   try { Ship s = one(v, f, (String) c[0], (SavedGameState) c[1], work); if (s != null) last = s; }
   catch (Throwable t) { t.printStackTrace(System.out); Setup.chk(c[0] + ": ran to the end (" + t + ")", false); }
  }
  if (last != null) stranded(v, last);
  refitButtons(v, f);
  for (String sh : GuiT.shown) System.out.println("POPUP " + sh.replace("\n", " / "));
  Setup.done();
  System.exit(0);
 }
 static List<File> savs(File d, List<File> out) {
  File[] fs = d.listFiles(); if (fs == null) return out; Arrays.sort(fs);
  for (File x : fs) { if (x.isDirectory() && !x.getName().equals("versions")) savs(x, out); else if (x.getName().endsWith(".sav") && x.getParentFile().getParentFile().getName().equals("shipyard")) out.add(x); }
  return out;
 }
 /** The blueprint dialog's Finalize, pressed whenever it shows. */
 static void clickFinalize() {
  new javax.swing.Timer(200, new java.awt.event.ActionListener() { public void actionPerformed(java.awt.event.ActionEvent e) {
   for (Window w : Window.getWindows()) {
    if (!(w instanceof JDialog) || !w.isShowing() || w instanceof RemodelDialog) continue;
    if (GuiT.find((Container) w, JOptionPane.class) != null) continue;
    for (JButton b : GuiT.all((Container) w, JButton.class)) if ("Finalize".equals(b.getText()) && b.isEnabled()) { b.doClick(); return; }
   }
  } }).start();
 }

 static String crewKey(CrewState c) { return c.getName() + "/" + (c.getRace() == null ? "?" : c.getRace().getId()) + "/" + c.isMale() + "/" + c.getHealth(); }
 static List<String> crew(ShipState s) { List<String> o = new ArrayList<String>(); for (CrewState c : s.getCrewList()) o.add(crewKey(c)); Collections.sort(o); return o; }
 static Map<String, Integer> systems(ShipState s) { Map<String, Integer> o = new TreeMap<String, Integer>(); for (SystemType t : SystemType.values()) { SystemState st = s.getSystem(t); if (st != null && st.getCapacity() > 0) o.put(t.getId(), st.getCapacity()); } return o; }
 static String goods(SavedGameState g) {
  ShipState s = g.getPlayerShip(); StringBuilder b = new StringBuilder();
  for (WeaponState w : s.getWeaponList()) b.append("w:").append(w.getWeaponId()).append(' ');
  for (DroneState d : s.getDroneList()) b.append("d:").append(d.getDroneId()).append(' ');
  for (String x : s.getAugmentIdList()) b.append("a:").append(x).append(' ');
  for (String x : g.getCargoIdList()) b.append("c:").append(x).append(' ');
  b.append("scrap ").append(s.getScrapAmt()).append(" fuel ").append(s.getFuelAmt()).append(" missiles ").append(s.getMissilesAmt()).append(" parts ").append(s.getDronePartsAmt()).append(" hull ").append(s.getHullAmt()).append(" reactor ").append(s.getReservePowerCapacity());
  return b.toString();
 }
 /** Crew not standing in a room, by FTL's squares (the layout's plus its offset), with where they are. */
 static List<String> offRoom(ShipState s, net.blerf.ftl.model.shiplayout.ShipLayout lay) {
  List<String> bad = new ArrayList<String>(); int sq = SaveHelper.SQUARE_SIZE;
  for (CrewState c : s.getCrewList()) {
   int room = c.getRoomId();
   if (room < 0 || room >= lay.getRoomCount()) { bad.add(c.getName() + " in room " + room); continue; }
   net.blerf.ftl.model.shiplayout.ShipLayoutRoom lr = lay.getRoom(room);
   int tx = Math.floorDiv(c.getSpriteX(), sq) - lay.getOffsetX(), ty = Math.floorDiv(c.getSpriteY(), sq) - lay.getOffsetY();
   if (tx < lr.locationX || tx >= lr.locationX + lr.squaresH || ty < lr.locationY || ty >= lr.locationY + lr.squaresV) bad.add(c.getName() + " at " + tx + "," + ty + " outside room " + room);
   if (c.getRoomSquare() < 0 || c.getRoomSquare() >= lr.squaresH * lr.squaresV) bad.add(c.getName() + " on square " + c.getRoomSquare() + " of room " + room);
  }
  return bad;
 }

 static Ship one(final Vault v, final MainFrame f, final String what, SavedGameState g, File work) throws Exception {
  System.out.println("STEP " + what);
  if (!CompanionMod.isRemodelId(g.getPlayerShip().getShipBlueprintId()) && !g.getPlayerShip().getShipBlueprintId().endsWith(Retrofit.SUFFIX)) Retrofit.apply(g, false);
  Ship s = v.adoptJunked(g); v.takeStock();
  v.salvage(v.byId(s.id)); v.takeStock();
  s = v.byId(s.id);
  if (v.boarded() != null) v.dock();
  v.board(s);
  final SavedGameState before = HomePlanet.savedGameParser.readSavedGame(v.fileOf(v.boarded()));
  final String others = othersNow(v);
  final Object[] r = new Object[2];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   f.showCargoBay();
   CargoBayUI bay = (CargoBayUI) GuiT.field(f, MainFrame.class, "cargoBay");
   bay.init();
   String id = ((SavedGameState) GuiT.field(bay, CargoBayUI.class, "currentSave")).getPlayerShip().getShipBlueprintId();
   Object cur = DataManager.get().getShips().get(id), plain = DataManager.get().getShips().get(Retrofit.vanillaId(id) + Retrofit.SUFFIX);
   java.lang.reflect.Constructor<?> k = RemodelDialog.class.getDeclaredConstructor(Window.class, CargoBayUI.class, net.blerf.ftl.xml.ShipBlueprint.class, net.blerf.ftl.xml.ShipBlueprint.class);
   k.setAccessible(true);
   final RemodelDialog rd = (RemodelDialog) k.newInstance(SwingUtilities.getWindowAncestor(bay), bay, cur, plain);
   java.lang.reflect.Method m = RemodelDialog.class.getDeclaredMethod("enterOverhaul", ShipDesign.class); m.setAccessible(true);
   m.invoke(rd, (Object) null);
   ShipDesign d = (ShipDesign) GuiT.field(rd, ShipEditorDialog.class, "d");
   Object editor = GuiT.field(rd, ShipEditorDialog.class, "editor");
   int rooms0 = d.rooms.size();
   // two rooms that hold systems (not Piloting or Engines, which the check needs placed): removed, their systems lifted off
   List<String> lifted = new ArrayList<String>();
   for (int pass = 0; pass < 2; pass++) {
    int pick = -1;
    for (int i = d.rooms.size() - 1; i >= 0 && pick < 0; i--) {
     List<String> in = d.systemsIn(i);
     if (in.isEmpty() || in.contains("pilot") || in.contains("engines")) continue;
     pick = i;
    }
    if (pick < 0) break;
    lifted.addAll(d.systemsIn(pick));
    d.removeRoom(pick);
   }
   // two new 2 x 2 rooms beside her, on her right, touching her rightmost room
   int[] bb = d.bounds();
   int ry = bb[1];
   for (ShipDesign.Room x : d.rooms) if (x.x + x.w == bb[2]) { ry = x.y; break; }
   d.rooms.add(new ShipDesign.Room(bb[2], ry, 2, 2));
   d.rooms.add(new ShipDesign.Room(bb[2] + 2, ry, 2, 2));
   d.refreshDoors();
   // the lifted systems into the new rooms (the bays may share), as the editor places them
   java.lang.reflect.Method place = LayoutEditor.class.getDeclaredMethod("place", String.class, int.class); place.setAccessible(true);
   int next = d.rooms.size() - 2;
   for (int i = 0; i < lifted.size() && next < d.rooms.size(); i++) {
    String sys = lifted.get(i);
    place.invoke(editor, sys, next);
    if (!(ShipDesign.bay(sys) && i + 1 < lifted.size() && ShipDesign.bay(lifted.get(i + 1)))) next++; // the Medbay and Clone Bay share, as they may
   }
   r[0] = rooms0 + " rooms -> " + d.rooms.size() + ", lifted " + lifted;
   r[1] = rd;
  } catch (Exception e) { throw new RuntimeException(e); } } });
  final RemodelDialog rd = (RemodelDialog) r[1];
  GuiT.presses.clear(); GuiT.presses.add(1); // "Blueprint finalized": OK, not Patch Now
  int popups = GuiT.shown.size();
  final Throwable[] err = new Throwable[1];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   java.lang.reflect.Method fin = RemodelDialog.class.getDeclaredMethod("finalizeBlueprint"); fin.setAccessible(true);
   fin.invoke(rd);
  } catch (Throwable t) { err[0] = t; } } });
  boolean done = (Boolean) GuiT.field(rd, RemodelDialog.class, "finalized");
  String said = GuiT.shown.size() > popups ? " (" + GuiT.shown.get(popups).replace("\n", " / ") + ")" : "";
  Setup.chk(what + ": Finalize goes through (" + r[0] + ")" + (done ? "" : said) + (err[0] == null ? "" : " threw " + err[0]), done && err[0] == null);
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { if (rd.isDisplayable()) rd.dispose(); } });
  if (!done) return null;
  Ship b = v.boarded(); b.invalidate();
  SavedGameState after = HomePlanet.savedGameParser.readSavedGame(v.fileOf(b));
  ShipState sa = after.getPlayerShip(), sb = before.getPlayerShip();
  net.blerf.ftl.model.shiplayout.ShipLayout lay = DataManager.get().getShipLayout(sa.getShipLayoutId());
  Setup.chk(what + ": on her remodel's blueprint " + sa.getShipBlueprintId(), CompanionMod.isRemodelId(sa.getShipBlueprintId()) && lay != null);
  if (lay == null) return null;
  Setup.chk(what + ": every crew member still aboard (" + sb.getCrewList().size() + " -> " + sa.getCrewList().size() + ")", crew(sb).equals(crew(sa)));
  List<String> bad = offRoom(sa, lay);
  Setup.chk(what + ": each crew member in a room on FTL's own squares (offset " + lay.getOffsetX() + "," + lay.getOffsetY() + ")" + (bad.isEmpty() ? "" : " " + bad), bad.isEmpty());
  boolean roomsOk = sa.getRoomList().size() == lay.getRoomCount();
  for (int i = 0; roomsOk && i < lay.getRoomCount(); i++) roomsOk = sa.getRoomList().get(i).getSquareList().size() == lay.getRoom(i).squaresH * lay.getRoom(i).squaresV;
  Setup.chk(what + ": one room state per room of the new layout (" + sa.getRoomList().size() + " for " + lay.getRoomCount() + "), each its squares", roomsOk);
  Setup.chk(what + ": the doors the new layout's (" + sa.getDoorMap().size() + " for " + lay.getDoorMap().size() + ")", sa.getDoorMap().keySet().equals(lay.getDoorMap().keySet()));
  Setup.chk(what + ": every system at its level " + systems(sa), systems(sb).equals(systems(sa)));
  Setup.chk(what + ": weapons, drones, augments, cargo and supplies as they were", goods(before).equals(goods(after)));
  File tmp = new File(work, "reread.sav"); SaveHelper.writeSavedGame(tmp, after);
  SavedGameState again = HomePlanet.savedGameParser.readSavedGame(tmp);
  Setup.chk(what + ": her save writes and reads back the same", crew(again.getPlayerShip()).equals(crew(sa)) && goods(again).equals(goods(after)));
  Setup.chk(what + ": the fleet's other ships untouched", othersNow(v).equals(others));
  return b;
 }
 static String othersNow(Vault v) throws IOException {
  StringBuilder b = new StringBuilder();
  for (Ship s : v.all()) { if (s.isBoarded() || s.isStorage()) continue; File x = v.fileOf(s); b.append(s.id).append('=').append(x.isFile() ? SafeFiles.hash(x) : "-").append(' '); }
  return b.toString();
 }

 /** A ship overhauled before 6.47: her crew put down without the layout's offset, as it did then; the next look puts them back aboard. */
 static void stranded(Vault v, Ship s) throws Exception {
  System.out.println("STEP stranded");
  if (s.isBoarded()) v.dock();
  s = v.byId(s.id);
  Vault.Copy c = v.readCopy(s);
  ShipState sh = c.save.getPlayerShip();
  net.blerf.ftl.model.shiplayout.ShipLayout lay = DataManager.get().getShipLayout(sh.getShipLayoutId());
  int sq = SaveHelper.SQUARE_SIZE;
  for (CrewState cr : sh.getCrewList()) { cr.setSpriteX(cr.getSpriteX() - lay.getOffsetX() * sq); cr.setSpriteY(cr.getSpriteY() - lay.getOffsetY() * sq); }
  List<String> off = new ArrayList<String>(); for (CrewState x : Retrofit.stranded(sh, lay)) off.add(x.getName());
  SafeFiles.write(v.fileOf(s), SaveHelper.toBytes(c.save)); // as the old station left her
  int versions = versions(v, s);
  int logged0 = reseated(v);
  v.takeStock();
  s = v.byId(s.id); s.invalidate();
  SavedGameState now = HomePlanet.savedGameParser.readSavedGame(v.fileOf(s));
  List<String> bad = offRoom(now.getPlayerShip(), lay);
  Setup.chk("R: a ship overhauled before 6.47 had crew outside her rooms (" + off + ")", !off.isEmpty() && (lay.getOffsetX() != 0 || lay.getOffsetY() != 0));
  Setup.chk("R: the station's next look puts them back aboard" + (bad.isEmpty() ? "" : " " + bad), bad.isEmpty() && crew(now.getPlayerShip()).equals(crew(sh)));
  Setup.chk("R: her save as it was kept as a version first (" + versions + " -> " + versions(v, s) + ")", versions(v, s) > versions);
  String ev = new String(SafeFiles.read(EventLog.file(v)), "UTF-8");
  Setup.chk("R: the station log says so, in its two lines", reseated(v) == logged0 + 1 && ev.contains("crew were returned to"));
  int before = versions(v, s);
  v.takeStock();
  Setup.chk("R: and leaves her alone at the next look", versions(v, s) == before);
 }
 static int reseated(Vault v) throws IOException { File f = EventLog.file(v); int n = 0; if (f.isFile()) for (String l : new String(SafeFiles.read(f), "UTF-8").split("\n")) if (l.contains(" | CREW_RESEATED | ")) n++; return n; }
 static int versions(Vault v, Ship s) { File[] fs = new File(v.folderOf(s), "versions").listFiles(); return fs == null ? 0 : fs.length; }

 /** The Refit tab: Retrofit and Remodel take their own clicks; her name opens her report only where it's written. */
 static void refitButtons(final Vault v, final MainFrame f) throws Exception {
  final Object[] r = new Object[4];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   f.showCargoBay();
   CargoBayUI bay = (CargoBayUI) GuiT.field(f, MainFrame.class, "cargoBay");
   bay.init();
   GuiT.call(bay, CargoBayUI.class, "showTab", new Class<?>[] {String.class}, "refit");
   Object sys = GuiT.field(bay, CargoBayUI.class, "systems");
   JPanel panel = (JPanel) GuiT.field(sys, sys.getClass(), "panel");
   for (String b : new String[] {"remodelBtn", "retrofitBtn"}) {
    JComponent btn = (JComponent) GuiT.field(sys, sys.getClass(), b);
    Rectangle bnd = btn.getBounds();
    Component hit = SwingUtilities.getDeepestComponentAt(panel, bnd.x + 12, bnd.y + bnd.height / 2);
    r["remodelBtn".equals(b) ? 0 : 1] = hit == btn || SwingUtilities.isDescendingFrom(hit, btn) ? "button" : String.valueOf(hit == null ? null : hit.getClass().getSimpleName());
   }
   JComponent name = (JComponent) GuiT.field(sys, sys.getClass(), "name");
   r[2] = name.getBounds().width;
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Setup.chk("B: a click at Remodel's left reaches Remodel (" + r[0] + ")", "button".equals(r[0]));
  Setup.chk("B: a click at Retrofit's left reaches Retrofit (" + r[1] + ")", "button".equals(r[1]));
  Setup.chk("B: her name is as wide as it's written (" + r[2] + "), not the whole line under her picture", (Integer) r[2] < 640);
 }
}
