import java.io.*; import java.util.*; import java.util.List; import java.awt.*; import javax.swing.*; import net.blerf.ftl.parser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*; import homeplanet.ui.*; import homeplanet.parser.Career; import homeplanet.parser.CareerRules;
/**
 * The station's windows, driven as a player would (needs a display: run.sh runs it under xvfb-run): the Dry Dock's bill
 * paid from the Cargo Hold on Save and dropped on Reset (the hold as the trade partner, and another ship as it); the
 * auction's three steps; the Junkyard's Info button and list tooltips. args: gamedir, world saves (from WorldT), work
 */
public class GuiT {
 /** What each pop-up showed, and the button pressed on it (an index into its options; -1 closes it). */
 static final List<String> shown = new ArrayList<String>();
 static final List<Object[]> optionsShown = new ArrayList<Object[]>();
 static final List<Object> defaults = new ArrayList<Object>();
 static final List<Object[]> extras = new ArrayList<Object[]>(); // per pop-up: the Info button and the list, if any
 static final LinkedList<Integer> presses = new LinkedList<Integer>();

 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
  final Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  HomePlanet.datsPath = game; HomePlanet.save_location = saves;
  if (v.boarded() == null) v.board(v.docked().get(0));
  Ship b = v.boarded(); SavedGameParser.SavedGameState g = v.readCopy(b).save; g.getPlayerShip().setScrapAmt(0); g.getPlayerShip().setHullAmt(21); v.write(b, g);
  hold(v, 100);
  watchPopUps();
  final Object[] frame = new Object[1];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   MainFrame f = new MainFrame("FHP", "test"); f.setSize(1300, 800); f.setVisible(true); frame[0] = f;
  } catch (Exception e) { throw new RuntimeException(e); } } });
  final MainFrame f = (MainFrame) frame[0];
  bill(v, f, false);
  bill(v, f, true);
  auction(v, f);
  junkyardInfo(v, f);
  modes();
  switched(f);
  Setup.done();
  System.exit(0);
 }

 static void hold(Vault v, int scrap) throws Exception { int s = v.storageScrap(); if (s < scrap) v.depositToStorage(scrap - s); else if (s > scrap) v.payFromStorage(s - scrap); }
 static Object field(Object o, Class<?> c, String name) throws Exception { java.lang.reflect.Field fd = c.getDeclaredField(name); fd.setAccessible(true); return fd.get(o); }
 static Object call(Object o, Class<?> c, String name, Class<?>[] types, Object... args) throws Exception { java.lang.reflect.Method m = c.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(o, args); }

 /** Repairs on the Refit tab: paid from the Cargo Hold on Save, nothing on Reset; the hold as the partner or another ship. */
 static void bill(final Vault v, final MainFrame f, final boolean otherPartner) throws Exception {
  final String tag = otherPartner ? " (another ship the partner)" : " (the Cargo Hold the partner)";
  SavedGameParser.SavedGameState g = v.readCopy(v.boarded()).save; g.getPlayerShip().setHullAmt(21); g.getPlayerShip().setScrapAmt(0); v.write(v.boarded(), g);
  hold(v, 100);
  final Object[] r = new Object[6];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   f.showCargoBay();
   CargoBayUI bay = (CargoBayUI) field(f, MainFrame.class, "cargoBay");
   bay.init();
   if (otherPartner) {
    List<?> ships = (List<?>) field(bay, CargoBayUI.class, "shipSelect"); Object home = field(bay, CargoBayUI.class, "homeSave"); int idx = -1;
    for (int i = 0; i < ships.size(); i++) if (ships.get(i) != home) idx = i;
    java.lang.reflect.Field pi = CargoBayUI.class.getDeclaredField("partnerIndex"); pi.setAccessible(true); pi.setInt(bay, idx);
    call(bay, CargoBayUI.class, "loadPartner", new Class<?>[0]);
   }
   Object sys = field(bay, CargoBayUI.class, "systems");
   r[0] = call(bay, CargoBayUI.class, "partnerIsStorage", new Class<?>[0]);
   call(sys, sys.getClass(), "repairHull", new Class<?>[] {int.class}, Integer.MAX_VALUE);
   r[1] = call(sys, sys.getClass(), "hold", new Class<?>[0]);
   r[2] = bay.saveAll();
   SavedGameParser.SavedGameState after = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
   r[3] = v.storageScrap(); r[4] = after.getPlayerShip().getScrapAmt(); r[5] = after.getPlayerShip().getHullAmt();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Setup.chk("B: the partner is as set" + tag, Boolean.valueOf(!otherPartner).equals(r[0]));
  Setup.chk("B: 9 hull points on the bill: the hold shows 64 before Save" + tag, Integer.valueOf(64).equals(r[1]));
  Setup.chk("B: Save pays the bill from the Cargo Hold, not her" + tag, Boolean.TRUE.equals(r[2]) && Integer.valueOf(64).equals(r[3]) && Integer.valueOf(0).equals(r[4]) && Integer.valueOf(30).equals(r[5]));
  // damaged again: a repair, then Reset, takes nothing
  g = v.readCopy(v.boarded()).save; g.getPlayerShip().setHullAmt(25); v.write(v.boarded(), g);
  final Object[] q = new Object[2];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   CargoBayUI bay = (CargoBayUI) field(f, MainFrame.class, "cargoBay");
   bay.init();
   Object sys = field(bay, CargoBayUI.class, "systems");
   call(sys, sys.getClass(), "repairHull", new Class<?>[] {int.class}, 1);
   bay.init(); // Reset
   q[0] = v.storageScrap(); q[1] = call(sys, sys.getClass(), "hold", new Class<?>[0]);
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Setup.chk("B: a repair, then Reset: nothing taken" + tag, Integer.valueOf(64).equals(q[0]) && Integer.valueOf(64).equals(q[1]));
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try { f.showSpaceDock(); } catch (Exception e) { throw new RuntimeException(e); } } });
 }

 /** The auction: explained first (Cancel the default), Hold Auction sells her, the result offers only Accept Bid. */
 static void auction(final Vault v, final MainFrame f) throws Exception {
  Ship s = v.boarded();
  String name = s.name, id = s.id;
  v.disband();
  int before = v.storageScrap(), scrapAboard = HomePlanet.savedGameParser.readSavedGame(v.fileOf(v.byId(id))).getPlayerShip().getScrapAmt();
  shown.clear(); optionsShown.clear(); defaults.clear();
  presses.addAll(Arrays.asList(3, 0, 0)); // Auction, Hold Auction, Accept Bid
  salvage(f);
  Setup.chk("A: three pop-ups: the Junkyard, the explanation, the result", shown.size() == 3);
  Setup.chk("A: the explanation says the highest bid is accepted, with Hold Auction and Cancel (the default)",
    shown.get(1).contains("accepts the highest bid automatically") && Arrays.asList(optionsShown.get(1)).contains("Hold Auction") && "Cancel".equals(String.valueOf(defaults.get(1))));
  Setup.chk("A: the result offers only Accept Bid", optionsShown.get(2).length == 1 && "Accept Bid".equals(String.valueOf(optionsShown.get(2)[0])));
  int bid = Integer.parseInt(shown.get(2).replaceAll("(?s).*highest bid for [^:]*: (\\d+) scrap.*", "$1"));
  Setup.chk("A: she's sold: gone from the fleet, fate SOLD, the bid and her scrap in the Cargo Hold",
    v.byId(id) == null && new String(SafeFiles.read(new File(new File(v.historyDir(), id), "fate.txt")), "UTF-8").startsWith("SOLD")
    && v.storageScrap() == before + bid + scrapAboard);
  System.out.println("auction: " + name + " for " + bid);
 }

 /** The Junkyard window: an Info button beside the list, and each hull's short report as the list's tooltip. */
 static void junkyardInfo(final Vault v, final MainFrame f) throws Exception {
  v.board(v.docked().get(0)); v.disband();
  shown.clear(); optionsShown.clear(); defaults.clear(); extras.clear();
  presses.add(-1); // close it
  salvage(f);
  Object[] x = extras.isEmpty() ? null : extras.get(0);
  Setup.chk("J: an Info... button beside the list", x != null && x[0] != null);
  Setup.chk("J: the list's tooltip is her short report, with what Trade In pays", x != null && x[1] != null && String.valueOf(x[1]).contains("Trade In:") && String.valueOf(x[1]).contains("Hull "));
 }

 /** The welcome screen and Switch Game Mode describe each career alike; one already begun offers Continue, not Begin. */
 static void modes() throws Exception {
  Vault.switchFleet(Vault.CUSTOM); Career.start(false, false, CareerRules.earlier(false));
  Vault.switchFleet(Vault.SANDBOX);
  final String[] texts = new String[2]; final List<String> buttons = new ArrayList<String>();
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   java.lang.reflect.Constructor<?> k = ModeChoiceDialog.class.getDeclaredConstructor(); k.setAccessible(true);
   JDialog welcome = (JDialog) k.newInstance();
   Class<?> sw = Class.forName("homeplanet.ui.SwitchModeDialog");
   java.lang.reflect.Constructor<?> k2 = sw.getDeclaredConstructor(Component.class); k2.setAccessible(true);
   JDialog switcher = (JDialog) k2.newInstance((Component) null);
   JDialog[] ds = {welcome, switcher};
   for (int i = 0; i < 2; i++) {
    StringBuilder sb = new StringBuilder();
    for (JLabel l : all(ds[i].getContentPane(), JLabel.class)) sb.append(l.getText().replaceAll("<[^>]*>", " ")).append('\n');
    texts[i] = sb.toString();
   }
   for (JButton b : all(welcome.getContentPane(), JButton.class)) buttons.add(b.getText());
   welcome.dispose(); switcher.dispose();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  String first = "Your first Immersive career, from before difficulties, with the rules it had.";
  Setup.chk("M: the welcome screen and Switch Game Mode both say Custom holds the first career, begun", texts[0].contains(first) && texts[1].contains(first)
    && texts[0].contains("Begun: 0 ships") && texts[1].contains("Begun: 0 ships"));
  Setup.chk("M: the others not begun, on both", texts[0].split("Not begun", -1).length == 4 && texts[1].split("Not begun", -1).length == 4);
  Setup.chk("M: the welcome screen offers Continue for the begun career, Begin for the rest " + buttons, Collections.frequency(buttons, "Continue...") == 1 && Collections.frequency(buttons, "Begin...") == 3);
 }

 /** After a switch of game mode: the station's open windows close, and the Space Dock shows. */
 static void switched(final MainFrame f) throws Exception {
  final boolean[] r = new boolean[3];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   f.showCargoBay();
   JDialog open = new JDialog(f, "Settings"); open.setSize(200, 100); open.setVisible(true);
   r[0] = MainFrame.modeSwitched();
   r[1] = !open.isDisplayable();
   r[2] = Boolean.TRUE.equals(field(f, MainFrame.class, "atSpaceDock"));
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Setup.chk("W: a new game mode: the open windows close and the Space Dock shows", r[0] && r[1] && r[2]);
 }

 static void salvage(final MainFrame f) throws Exception {
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   Object dock = field(f, MainFrame.class, "spaceDock");
   call(dock, dock.getClass(), "salvageShip", new Class<?>[0]);
  } catch (Exception e) { throw new RuntimeException(e); } } });
 }

 /** Answers each pop-up as it opens: records it, then presses the next scripted button. */
 static void watchPopUps() {
  final Set<Window> seen = Collections.newSetFromMap(new WeakHashMap<Window, Boolean>());
  new javax.swing.Timer(150, new java.awt.event.ActionListener() { public void actionPerformed(java.awt.event.ActionEvent e) {
   for (Window w : Window.getWindows()) {
    if (!(w instanceof JDialog) || !w.isShowing() || seen.contains(w)) continue;
    JOptionPane op = find((Container) w, JOptionPane.class);
    if (op == null) continue;
    seen.add(w);
    shown.add(text(op.getMessage()));
    optionsShown.add(op.getOptions() == null ? new Object[0] : op.getOptions());
    defaults.add(op.getInitialValue());
    JButton info = null; JComboBox<?> list = find((Container) w, JComboBox.class);
    for (JButton bt : all((Container) w, JButton.class)) if ("Info...".equals(bt.getText())) info = bt;
    extras.add(new Object[] {info, list == null ? null : list.getToolTipText()});
    Integer p = presses.isEmpty() ? -1 : presses.removeFirst();
    op.setValue(p < 0 || op.getOptions() == null ? Integer.valueOf(JOptionPane.CLOSED_OPTION) : op.getOptions()[p]);
    w.dispose();
    return;
   }
  } }).start();
 }
 static String text(Object m) {
  if (m instanceof String) return (String) m;
  if (m instanceof Container) { StringBuilder sb = new StringBuilder(); for (JLabel l : all((Container) m, JLabel.class)) sb.append(l.getText()).append('\n'); return sb.toString(); }
  return String.valueOf(m);
 }
 static <T> T find(Container c, Class<T> k) { List<T> l = all(c, k); return l.isEmpty() ? null : l.get(0); }
 static <T> List<T> all(Container c, Class<T> k) {
  List<T> out = new ArrayList<T>();
  for (Component x : c.getComponents()) { if (k.isInstance(x)) out.add(k.cast(x)); if (x instanceof Container) out.addAll(all((Container) x, k)); }
  return out;
 }
}
