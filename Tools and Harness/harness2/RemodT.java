import java.io.*; import java.util.*; import java.util.List; import java.awt.*; import java.awt.image.BufferedImage; import javax.swing.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*; import homeplanet.ui.*;
/**
 * Remodel (6.42, heromedel): Overhaul deck plan brings the room tools (it threw before and stopped halfway), never the
 * art tools, the flip or the whole-ship arrows; her art keeps its place under her rooms; any system can be lifted off;
 * one left without a place can be uninstalled the Refit tab's way. Needs a display (run.sh: xvfb-run). Screenshots go in
 * the work folder. args: gamedir, world saves (from WorldT), work
 */
public class RemodT {
 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
  final Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  HomePlanet.datsPath = game; HomePlanet.save_location = saves;
  if (v.boarded() == null) v.board(v.docked().get(0));
  GuiT.watchPopUps(); // a pop-up nobody answers would hold the test: each is closed, and printed
  final Object[] frame = new Object[1];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   MainFrame f = new MainFrame("FHP", "test"); f.setSize(1300, 800); f.setVisible(true); frame[0] = f;
  } catch (Exception e) { throw new RuntimeException(e); } } });
  final MainFrame f = (MainFrame) frame[0];
  for (String cls : new String[] {"PLAYER_SHIP_HARD", "PLAYER_SHIP_CIRCLE", "PLAYER_SHIP_ENERGY"}) { System.out.println("STEP overhaul " + cls); overhaul(v, f, cls, work); }
  System.out.println("STEP uninstall");
  uninstall(v, f);
  System.out.println("STEP refit");
  refit();
  for (String sh : GuiT.shown) System.out.println("POPUP " + sh.replace("\n", " / "));
  Setup.done();
  System.exit(0);
 }

 /** Boards a fresh ship of this class, retrofitted, and opens Remodel on her in the Cargo Bay. */
 static Object[] open(final Vault v, final MainFrame f, String cls) throws Exception {
  SavedGameState g = Commission.build(cls, "Remodel " + cls, net.blerf.ftl.constants.Difficulty.NORMAL, new Random(5));
  Retrofit.apply(g, false);
  Ship s = v.adoptJunked(g); v.takeStock();
  v.salvage(v.byId(s.id)); v.takeStock(); // to the Space Dock, to be boarded
  s = v.byId(s.id);
  if (v.boarded() != null) v.dock();
  v.board(s);
  final Object[] r = new Object[2];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   f.showCargoBay();
   CargoBayUI bay = (CargoBayUI) GuiT.field(f, MainFrame.class, "cargoBay");
   bay.init();
   String id = ((SavedGameState) GuiT.field(bay, CargoBayUI.class, "currentSave")).getPlayerShip().getShipBlueprintId();
   Object cur = DataManager.get().getShips().get(id), plain = DataManager.get().getShips().get(Retrofit.vanillaId(id) + Retrofit.SUFFIX);
   java.lang.reflect.Constructor<?> k = RemodelDialog.class.getDeclaredConstructor(Window.class, CargoBayUI.class, net.blerf.ftl.xml.ShipBlueprint.class, net.blerf.ftl.xml.ShipBlueprint.class);
   k.setAccessible(true);
   r[0] = k.newInstance(SwingUtilities.getWindowAncestor(bay), bay, cur, plain);
   r[1] = bay;
  } catch (Exception e) { throw new RuntimeException(e); } } });
  return r;
 }

 /** The art's left and top less the first room's, in pixels (the same before and after the overhaul starts: she doesn't jump). */
 static int[] artToRooms(Object editor, ShipDesign d) throws Exception {
  boolean design = (Boolean) GuiT.field(editor, LayoutEditor.class, "designArt");
  int ax = design ? d.artX : (Integer) GuiT.field(editor, LayoutEditor.class, "baseX"), ay = design ? d.artY : (Integer) GuiT.field(editor, LayoutEditor.class, "baseY");
  int mx = Integer.MAX_VALUE, my = Integer.MAX_VALUE;
  for (ShipDesign.Room r : d.rooms) { mx = Math.min(mx, r.x); my = Math.min(my, r.y); }
  return new int[] {ax - mx * LayoutEditor.SQ, ay - my * LayoutEditor.SQ};
 }
 static List<String> buttons(Container c) {
  List<String> out = new ArrayList<String>();
  for (Component x : c.getComponents()) { if (x instanceof AbstractButton) out.add(((AbstractButton) x).getText()); if (x instanceof Container) out.addAll(buttons((Container) x)); }
  return out;
 }
 static void shot(final JDialog d, File to) throws Exception {
  SwingUtilities.invokeAndWait(new Runnable() { public void run() {
   JRootPane rp = d.getRootPane();
   BufferedImage img = new BufferedImage(Math.max(1, rp.getWidth()), Math.max(1, rp.getHeight()), BufferedImage.TYPE_INT_RGB);
   Graphics2D g = img.createGraphics(); rp.paint(g); g.dispose();
   try { javax.imageio.ImageIO.write(img, "png", to); } catch (IOException e) { throw new RuntimeException(e); }
  } });
 }

 /** The window at its size, the ship fitted in view, painted to a file. */
 static void fitShot(final JDialog d, File to) throws Exception {
  SwingUtilities.invokeAndWait(new Runnable() { public void run() {
   d.pack(); d.setSize(1100, 760); d.validate();
  } });
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { // after the layout, as the window does it
   try { ((LayoutEditor) GuiT.field(d, ShipEditorDialog.class, "editor")).fitView(); } catch (Exception e) { throw new RuntimeException(e); }
  } });
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { d.validate(); } });
  shot(d, to);
 }
 static void overhaul(Vault v, MainFrame f, final String cls, File work) throws Exception {
  final Object[] o = open(v, f, cls);
  final Object[] r = new Object[8];
  fitShot((JDialog) o[0], new File(work, "remodel-" + cls + ".png"));
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   RemodelDialog rd = (RemodelDialog) o[0];
   Object editor = GuiT.field(rd, ShipEditorDialog.class, "editor");
   ShipDesign d = (ShipDesign) GuiT.field(rd, ShipEditorDialog.class, "d");
   r[0] = artToRooms(editor, d);
   java.lang.reflect.Method m = RemodelDialog.class.getDeclaredMethod("enterOverhaul", ShipDesign.class); m.setAccessible(true);
   Throwable thrown = null;
   try { m.invoke(rd, (Object) null); } catch (java.lang.reflect.InvocationTargetException e) { thrown = e.getCause(); }
   r[1] = thrown;
   r[2] = artToRooms(editor, d);
   r[3] = ((LayoutEditor) editor).roomsEditable();
   r[4] = buttons(((LayoutEditor) editor).side());
   r[5] = ((LayoutEditor) editor).artLocked();
   r[6] = GuiT.field(rd, ShipEditorDialog.class, "artPanel");
  } catch (Exception e) { throw new RuntimeException(e); } } });
  fitShot((JDialog) o[0], new File(work, "overhaul-" + cls + ".png"));
  int[] before = (int[]) r[0], after = (int[]) r[2];
  List<?> b = (List<?>) r[4];
  Setup.chk("O: " + cls + ": Overhaul starts without an error" + (r[1] == null ? "" : " (" + r[1] + ")"), r[1] == null);
  Setup.chk("O: " + cls + ": her art keeps its place under her rooms (" + before[0] + "," + before[1] + " -> " + after[0] + "," + after[1] + ")", before[0] == after[0] && before[1] == after[1]);
  Setup.chk("O: " + cls + ": the room tools show (Place 2 x 2, 2 x 1, 1 x 2, Move rooms, Add door)", Boolean.TRUE.equals(r[3]) && b.contains("Place 2 x 2") && b.contains("Place 2 x 1") && b.contains("Place 1 x 2") && b.contains("Move rooms") && b.contains("Add door"));
  Setup.chk("O: " + cls + ": no art tools, no flip, no whole-ship arrows; the art locked", r[6] == null && !b.contains("Flip top / bottom") && !b.contains("←") && Boolean.TRUE.equals(r[5]));
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { ((JDialog) o[0]).dispose(); } });
 }

 /**
  * The Refit tab's hover against FTL's own upgrade screen (6.42, read off FTL 1.6.14 under Wine, system by system): the
  * level lines and prices, the maximum, FTL's descriptions.
  */
 static void refit() throws Exception {
  Class<?> sp = Class.forName("homeplanet.ui.SystemsPanel");
  java.lang.reflect.Method label = sp.getDeclaredMethod("levelLabel", String.class, int.class); label.setAccessible(true);
  java.lang.reflect.Method info = sp.getDeclaredMethod("info", String.class, int.class, int.class, String.class); info.setAccessible(true);
  String[][] want = {
   {"engines", "1", "Dodge: 5 / FTL: 1x"}, {"engines", "4", "Dodge: 20 / FTL: 1.75x"}, {"engines", "8", "Dodge: 35 / FTL: 2.75x"},
   {"medbay", "2", "Healing Boost: 1.5x"}, {"oxygen", "3", "O2 Refill Boost: 6x"}, {"pilot", "3", "Auto: 80 percent evasion"},
   {"doors", "2", "Blast Doors"}, {"sensors", "3", "See enemy weapon charge"}, {"shields", "2", "One Shield Barrier"}, {"shields", "3", ""},
   {"shields", "8", "Four Shield Barriers"}, {"weapons", "5", "More System Power"}, {"drones", "1", "More System Power"},
   {"teleporter", "1", "20 sec cooldown"}, {"cloaking", "3", "Cloak: 15 seconds"}, {"hacking", "2", "7 second disruption"},
   {"battery", "2", "Provides 4 bonus power"}, {"clonebay", "1", "12 sec clone + 8 hp/jump"}, {"clonebay", "3", "7 sec clone + 25 hp/jump"},
   {"mind", "2", "Boosts health and damage"}};
  List<String> off = new ArrayList<String>();
  for (String[] w : want) { String got = (String) label.invoke(null, w[0], Integer.parseInt(w[1])); if (!w[2].equals(got)) off.add(w[0] + " " + w[1] + ": \"" + got + "\""); }
  Setup.chk("F: each level's line as FTL's upgrade screen has it (" + want.length + " read off FTL)" + (off.isEmpty() ? "" : " " + off), off.isEmpty());
  Object m = info.invoke(null, "drones", 3, 8, null);
  java.lang.reflect.Method plain = m.getClass().getDeclaredMethod("plain"); plain.setAccessible(true);
  String drones = (String) plain.invoke(m);
  Setup.chk("F: Drone Control at level 3: FTL's description, 100 80 60 45 30 for levels 8 to 4, levels 3 to 1 had (their prices green), level 1 no price, no \"hers\"",
    drones.contains("Powers all of the ship's drones.") && drones.contains("level 8: 100 ") && drones.contains("level 4: 30 ") && drones.contains("level 3: ") && drones.contains("level 3: " + homeplanet.parser.Pricing.upgrade("drones", 2) + " (had)")
    && drones.contains("level 1 (had)") && !drones.contains("level 1: ") && !drones.contains("level 4: 30 (had)") && !drones.contains("hers"));
 }
 /** A system lifted off the remodel: it can come off the blueprint, and Uninstall takes it into the Cargo Bay the Refit tab's way. */
 static void uninstall(Vault v, MainFrame f) throws Exception {
  final Object[] o = open(v, f, "PLAYER_SHIP_HARD");
  final Object[] r = new Object[6];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   RemodelDialog rd = (RemodelDialog) o[0];
   CargoBayUI bay = (CargoBayUI) o[1];
   r[0] = rd.cannotTakeOff("shields");
   ShipDesign d = (ShipDesign) GuiT.field(rd, ShipEditorDialog.class, "d");
   d.systems.remove("shields"); d.systems.remove("medbay");
   java.lang.reflect.Method un = RemodelDialog.class.getDeclaredMethod("unplaced"); un.setAccessible(true);
   r[1] = un.invoke(rd).toString();
   Object sys = GuiT.field(bay, CargoBayUI.class, "systems");
   r[2] = GuiT.call(sys, sys.getClass(), "uninstallReason", new Class<?>[] {SystemType.class}, SystemType.SHIELDS);
   r[3] = GuiT.call(sys, sys.getClass(), "uninstallForRemodel", new Class<?>[] {SystemType.class}, SystemType.SHIELDS);
   r[4] = ((SavedGameState) GuiT.field(bay, CargoBayUI.class, "currentSave")).getPlayerShip().getSystem(SystemType.SHIELDS).getCapacity();
   r[5] = String.valueOf(GuiT.call(sys, sys.getClass(), "storedList", new Class<?>[0]));
   rd.dispose();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Setup.chk("U: an installed system can be lifted off the remodel", r[0] == null);
  Setup.chk("U: the ones without a place are found (" + r[1] + ")", String.valueOf(r[1]).toLowerCase().contains("shields") && String.valueOf(r[1]).toLowerCase().contains("medbay"));
  Setup.chk("U: Uninstall takes the Shields off into the Cargo Bay (" + r[5] + ")", r[2] == null && Boolean.TRUE.equals(r[3]) && Integer.valueOf(0).equals(r[4]) && String.valueOf(r[5]).contains("Shields"));
 }
}
