import java.io.*; import java.util.*; import java.util.List; import net.blerf.ftl.parser.SavedGameParser.ShipState; import java.awt.*; import javax.swing.*; import net.blerf.ftl.parser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*; import homeplanet.ui.*; import homeplanet.parser.Career; import homeplanet.parser.CareerRules;
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
 /** Each expedition pop-up's close operation (they carry the "homeplanet.expedition" mark), and a hook run once at the first. */
 static final List<Integer> expeditionCloseOps = new ArrayList<Integer>();
 static Runnable atExpedition = null;

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
  holdAlone(v, f);
  damaged(f);
  folding(f);
  expedition(f);
  ransomPopUp(f);
  infirmaryBay(f);
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
  Setup.chk("J: the Junkyard window offers Parts... beside Derelicts...", !optionsShown.isEmpty() && Arrays.asList(optionsShown.get(0)).contains("Parts...") && Arrays.asList(optionsShown.get(0)).contains("Derelicts..."));
 }

 /** A damaged system stored from the Cargo Bay keeps its broken bars, and comes aboard again with them. */
 static void damaged(final MainFrame f) throws Exception {
  final Vault v = Vault.get();
  Vault.Copy c = v.readCopy(v.boarded());
  ShipState bs = c.save.getPlayerShip();
  final SavedGameParser.SystemType[] pick = new SavedGameParser.SystemType[1];
  Class<?> sp = Class.forName("homeplanet.ui.SystemsPanel");
  for (SavedGameParser.SystemType t : SavedGameParser.SystemType.values()) {
   SavedGameParser.SystemState st = bs.getSystem(t);
   if (pick[0] != null || st == null || st.getCapacity() < 2 || t == SavedGameParser.SystemType.WEAPONS || t == SavedGameParser.SystemType.DRONE_CTRL || t == SavedGameParser.SystemType.CLONEBAY || t == SavedGameParser.SystemType.MEDBAY) continue;
   if (call(null, sp, "refitReason", new Class<?>[] {ShipState.class, SavedGameParser.SystemType.class}, bs, t) == null) pick[0] = t;
  }
  if (pick[0] == null) { Setup.chk("R: a system on her that can be stored", false); return; }
  final int level = bs.getSystem(pick[0]).getCapacity();
  bs.getSystem(pick[0]).setDamagedBars(1); bs.getSystem(pick[0]).setPower(0);
  v.begin().put(v.boarded(), c.save, c.hash).commit();
  SafeFiles.writeText(v.systemsFile(), "# stored\n", false);
  hold(v, 200);
  presses.clear(); presses.addAll(Arrays.asList(0, 0, 0, 0));
  final Object[] r = new Object[3];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   f.showCargoBay();
   CargoBayUI bay = f.cargoBay; bay.init();
   Object sys = field(bay, CargoBayUI.class, "systems");
   call(sys, sys.getClass(), "storeSystem", new Class<?>[] {SavedGameParser.SystemType.class}, pick[0]);
   r[0] = bay.saveAll();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Thread.sleep(400);
  String file = new String(SafeFiles.read(v.systemsFile()), "UTF-8");
  SavedGameParser.SystemState off = v.readCopy(v.boarded()).save.getPlayerShip().getSystem(pick[0]);
  Setup.chk("R: a damaged " + pick[0].getId() + " stored: it keeps its broken bar (" + file.trim().replace("\n", " / ") + ")", Boolean.TRUE.equals(r[0])
    && file.contains(pick[0].getId() + " " + level + " 1") && (off == null || off.getCapacity() == 0));
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   CargoBayUI bay = f.cargoBay; bay.init();
   Object sys = field(bay, CargoBayUI.class, "systems");
   java.util.List<?> stored = (java.util.List<?>) call(sys, sys.getClass(), "storedList", new Class<?>[0]);
   Object it = null; for (Object o : stored) if (String.valueOf(o).contains("broken")) it = o;
   r[1] = String.valueOf(it);
   call(sys, sys.getClass(), "installSystem", new Class<?>[] {it.getClass()}, it);
   r[2] = bay.saveAll();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Thread.sleep(400);
  SavedGameParser.SystemState on = v.readCopy(v.boarded()).save.getPlayerShip().getSystem(pick[0]);
  Setup.chk("R: the stored row says so (" + r[1] + "); installed again, still broken: level " + level + ", 1 bar broken", String.valueOf(r[1]).contains("1 broken") && Boolean.TRUE.equals(r[2])
    && on != null && on.getCapacity() == level && on.getDamagedBars() == 1 && !new String(SafeFiles.read(v.systemsFile()), "UTF-8").contains(pick[0].getId()));
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

 /** No ship aboard: the Cargo Bay opens on the Cargo Hold; an item and a stored system sold from it pay the hold on Save. */
 static void holdAlone(Vault stale, final MainFrame f) throws Exception {
  final Vault v = Vault.get(); // the mode checks switched fleets: the vault in use now
  if (v.boarded() != null) v.dock();
  Vault.Copy c = v.readCopy(v.storage()); c.save.getPlayerShip().getWeaponList().clear(); c.save.getPlayerShip().getWeaponList().add(SaveHelper.newIdleWeapon("LASER_BURST_2"));
  v.begin().put(v.storage(), c.save, c.hash).commit();
  SafeFiles.writeText(v.systemsFile(), "# stored\ncloaking 1\n", false);
  hold(v, 10);
  final Object[] r = new Object[6];
  presses.clear(); presses.addAll(Arrays.asList(0, 0)); // Yes to selling the weapon, Yes to selling the system
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   r[3] = call(f.spaceDock, SpaceDockUI.class, "cargoBayClosedReason", new Class<?>[0]); // the Space Dock's Cargo Bay button lets her in
   f.showCargoBay();
   CargoBayUI bay = f.cargoBay;
   r[0] = call(bay, CargoBayUI.class, "holdOnly", new Class<?>[0]);
   r[1] = call(bay, CargoBayUI.class, "partnerIsStorage", new Class<?>[0]);
   Object[] cats = (Object[]) field(bay, CargoBayUI.class, "cats");
   Object theirs = field(cats[0], cats[0].getClass(), "theirs");
   ((JList<?>) field(theirs, theirs.getClass(), "list")).setSelectedIndex(0);
   call(bay, CargoBayUI.class, "dispose", new Class<?>[] {boolean.class, int.class, boolean.class}, false, 0, true);
   Object sys = field(bay, CargoBayUI.class, "systems");
   java.util.List<?> stored = (java.util.List<?>) call(sys, sys.getClass(), "storedList", new Class<?>[0]);
   call(sys, sys.getClass(), "sell", new Class<?>[] {stored.get(0).getClass()}, stored.get(0));
   r[2] = bay.saveAll();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Thread.sleep(400);
  ShipState hold = v.readCopy(v.storage()).save.getPlayerShip();
  String file = new String(SafeFiles.read(v.systemsFile()), "UTF-8");
  Setup.chk("H: no ship aboard: the Space Dock's Cargo Bay button opens it (" + r[3] + "), on the Cargo Hold", r[3] == null && Boolean.TRUE.equals(r[0]) && Boolean.TRUE.equals(r[1]));
  Setup.chk("H: a weapon and a stored system sold from it: Save pays the hold, both are gone (" + hold.getScrapAmt() + " scrap)", Boolean.TRUE.equals(r[2])
    && hold.getWeaponList().isEmpty() && !file.contains("cloaking") && hold.getScrapAmt() > 10);
  // and from there, board a docked ship without going back to the Space Dock
  final Ship next = v.docked().get(0);
  final Object[] b = new Object[2];
  presses.clear(); presses.add(0); shown.clear(); // Board her
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   CargoBayUI bay = f.cargoBay;
   call(bay, CargoBayUI.class, "boardFromHere", new Class<?>[] {Ship.class}, next);
   b[0] = call(bay, CargoBayUI.class, "holdOnly", new Class<?>[0]);
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Setup.chk("H: no ship aboard, a docked ship boarded from the Cargo Bay: she's aboard, and the Cargo Bay shows her", v.boarded() == next && Boolean.FALSE.equals(b[0]));
 }

 /** The Space Dock's gold headings fold their buttons away on a click, and stay folded after a redraw. */
 static void folding(final MainFrame f) throws Exception {
  final Object[] r = new Object[5];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   f.showSpaceDock();
   JButton cargo = (JButton) field(f.spaceDock, SpaceDockUI.class, "cargoBtn"), exp = (JButton) field(f.spaceDock, SpaceDockUI.class, "expeditionsBtn");
   r[4] = exp != null && exp.getParent() == cargo.getParent();
   FtlButton.Header station = heading(cargo);
   station.dispatchEvent(new java.awt.event.MouseEvent(station, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false));
   r[0] = cargo.isShowing();
   r[1] = HomePlanet.config.getProperty("fold_station");
   f.spaceDock.init();
   JButton cargo2 = (JButton) field(f.spaceDock, SpaceDockUI.class, "cargoBtn");
   r[2] = cargo2.getParent().isVisible();
   FtlButton.Header again = heading(cargo2);
   again.dispatchEvent(new java.awt.event.MouseEvent(again, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false));
   r[3] = cargo2.getParent().isVisible();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Setup.chk("F: Expeditions sits under Station, beside the Cargo Bay", Boolean.TRUE.equals(r[4]));
  Setup.chk("F: a click on Station folds its buttons away, remembered (" + r[1] + ")", Boolean.FALSE.equals(r[0]) && "true".equals(r[1]));
  Setup.chk("F: still folded after the Space Dock redraws; a second click opens it", Boolean.FALSE.equals(r[2]) && Boolean.TRUE.equals(r[3]) && "false".equals(HomePlanet.config.getProperty("fold_station")));
 }
 /** The heading just above this button's group. */
 static FtlButton.Header heading(JComponent b) {
  Container body = b.getParent(), column = body.getParent();
  Component last = null;
  for (Component c : column.getComponents()) { if (c == body) return (FtlButton.Header) last; if (c instanceof FtlButton.Header) last = c; }
  return null;
 }

 /** An expedition played through its pop-ups: crew picked, each event's choice, its outcome, the end; the Cargo Hold paid. */
 static void expedition(final MainFrame f) throws Exception {
  final Vault v = Vault.get();
  Vault.Copy c = v.readCopy(v.storage()); ShipState h = c.save.getPlayerShip(); h.getCrewList().clear();
  for (String race : new String[] {"rock", "human"}) { SavedGameParser.CrewState x = Commission.volunteer(race, new Random(2)); SaveHelper.placeCrew(h, x, true); h.getCrewList().add(x); }
  h.setScrapAmt(0);
  v.begin().put(v.storage(), c.save, c.hash).commit();
  final int beacons = v.beaconsSeen();
  // a known job in the first place: the Rock shaft, whose first choice always pays
  SafeFiles.writeText(new File(v.root, "expeditions.txt"), "0.kind=rescue\n0.event=rock_shaft\n0.until=999999\n0.text=A Rock mining colony has lost a work crew in a shaft collapse.\n"
    + "1.kind=escort\n1.until=999999\n1.text=b\n2.kind=delivery\n2.until=999999\n2.text=c\n", false);
  Class<?> k = Class.forName("homeplanet.ui.ExpeditionsDialog");
  java.lang.reflect.Field rf = k.getDeclaredField("rng"); rf.setAccessible(true); rf.set(null, new Random(8));
  shown.clear(); optionsShown.clear(); presses.clear();
  for (int i = 0; i < 30; i++) presses.add(0); // Send them; then each event's first choice; Continue; the message's Close; the end
  expeditionCloseOps.clear();
  final Object[] during = new Object[1];
  final Object[] dlg = new Object[1]; final boolean[] done = {false};
  final java.awt.Dimension[] jobSize = new java.awt.Dimension[1]; final boolean[] boardUp = {true};
  atExpedition = new Runnable() { public void run() { try { // a priority message arrives, and a hail, while an expedition is under way
   boardUp[0] = ((JDialog) dlg[0]).isShowing(); // the board stepped aside
   for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isShowing() && Boolean.TRUE.equals(((JDialog) w).getRootPane().getClientProperty("homeplanet.expedition"))) jobSize[0] = w.getSize();
   Class<?> ed = Class.forName("homeplanet.ui.ExpeditionsDialog");
   java.lang.reflect.Method away = ed.getDeclaredMethod("awayNotice", String.class); away.setAccessible(true);
   during[0] = away.invoke(null, "Commander Test");
   homeplanet.comm.Notes.Note n = new homeplanet.comm.Notes.Note(); n.station = "x"; n.title = "Commander Bree"; n.text = "Testing the long range set, over."; n.priority = true; n.replyPort = 0;
   call(f.comm, LongRangeCommUI.class, "showNote", new Class<?>[] {homeplanet.comm.Notes.Note.class, String.class}, n, "localhost");
  } catch (Exception e) { throw new RuntimeException(e); } } };
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   java.lang.reflect.Constructor<?> ctor = Class.forName("homeplanet.ui.ExpeditionsDialog").getDeclaredConstructor(Component.class); ctor.setAccessible(true);
   dlg[0] = ctor.newInstance(f);
  } catch (Exception e) { throw new RuntimeException(e); } } });
  SwingUtilities.invokeLater(new Runnable() { public void run() { try {
   call(dlg[0], dlg[0].getClass(), "send", new Class<?>[] {int.class}, 0); // signs on: the board closes
   Object run = field(dlg[0], dlg[0].getClass(), "signedOn");
   call(null, dlg[0].getClass(), "play", new Class<?>[] {Component.class, Expeditions.Run.class, Vault.class}, f, run, v);
  } catch (Exception e) { throw new RuntimeException(e); } finally { done[0] = true; } } });
  for (int t = 0; t < 600 && !done[0]; t++) Thread.sleep(100);
  presses.clear();
  boolean picker = !shown.isEmpty() && shown.get(0).contains("Who goes");
  boolean events = false; for (Object[] o : optionsShown) if (o.length >= 2) events = true;
  String end = ""; boolean docked = false; // the job's last screen (a priority message may come up after it)
  for (String t : shown) { if (t.contains("You dig beside")) end = t; if (t.contains("docks at")) docked = true; }
  Setup.chk("X: the crew picker, then events with their choices (" + shown.size() + " pop-ups)", done[0] && picker && events);
  Setup.chk("X: the last outcome is the end, no docking screen after it; the Cargo Hold has the scrap (" + v.storageScrap() + "); a beacon passed", end.contains("You receive") && !docked
    && v.storageScrap() > 0 && v.beaconsSeen() >= beacons + 1);
  Setup.chk("X: the board steps aside during the job (" + boardUp[0] + "), and a fresh one has a new job in its place", !boardUp[0] && !Expeditions.board(v).get(0).text.startsWith("A Rock mining colony"));
  Setup.chk("X: the job's window is FTL's event box, sized to its words, not the whole screen (" + jobSize[0] + ")", jobSize[0] != null && jobSize[0].width < 700 && jobSize[0].height < 600);
  boolean noX = !expeditionCloseOps.isEmpty(); for (int op : expeditionCloseOps) if (op != JDialog.DO_NOTHING_ON_CLOSE) noX = false;
  Setup.chk("X: the expedition's pop-ups can't be closed, only answered (" + expeditionCloseOps.size() + ")", noX);
  boolean note = false; for (String t : shown) if (t.contains("Commander Bree, priority")) note = true;
  Setup.chk("X: a priority message comes through over the expedition, and the expedition carries on after it", note && done[0] && end.contains("You receive"));
  Object after = null; try { java.lang.reflect.Method away = Class.forName("homeplanet.ui.ExpeditionsDialog").getDeclaredMethod("awayNotice", String.class); away.setAccessible(true); after = away.invoke(null, "Commander Test"); } catch (Exception e) { }
  Setup.chk("X: a hail during the expedition is told the commander is away (" + during[0] + "); after it, hails are answered as usual", String.valueOf(during[0]).contains("away on an expedition") && after == null);
 }

 /**
  * The infirmary in the Cargo Bay: no bar for the whole, green with the rest red for a hurt from the game, purple and full
  * for the laid up; the laid up can't be moved onto a ship, and aren't offered over the Long Range.
  */
 static void infirmaryBay(final MainFrame f) throws Exception {
  final Vault v = Vault.get();
  if (v.boarded() == null) v.board(v.docked().get(0));
  Vault.Copy c = v.readCopy(v.storage()); ShipState h = c.save.getPlayerShip(); h.getCrewList().clear();
  final String[] names = {"Whole Wren", "Hurt Hale", "Laid Ulm"};
  for (int i = 0; i < 3; i++) {
   SavedGameParser.CrewState x = Commission.volunteer("human", new Random(40 + i)); x.setName(names[i]);
   if (i == 1) x.setHealth(40); if (i == 2) x.setHealth(25);
   SaveHelper.placeCrew(h, x, true); h.getCrewList().add(x);
  }
  v.begin().put(v.storage(), c.save, c.hash).commit();
  final File inf = new File(v.root, "infirmary.txt");
  SafeFiles.writeText(inf, "healed_at=" + v.beaconsSeen() + "\n0.name=Laid Ulm\n0.race=human\n0.until=" + (v.beaconsSeen() + 4) + "\n0.drained=" + v.beaconsSeen() + "\n", false);
  final Map<String, Object[]> bars = new HashMap<String, Object[]>();
  final Object[] r = new Object[3];
  presses.clear(); presses.add(0); shown.clear(); // OK, to the infirmary's word
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   f.showCargoBay();
   CargoBayUI bay = f.cargoBay;
   List<?> ships = (List<?>) field(bay, CargoBayUI.class, "shipSelect"); Object home = field(bay, CargoBayUI.class, "homeSave");
   java.lang.reflect.Field pi = CargoBayUI.class.getDeclaredField("partnerIndex"); pi.setAccessible(true); pi.setInt(bay, ships.indexOf(home));
   call(bay, CargoBayUI.class, "loadPartner", new Class<?>[0]);
   call(bay, CargoBayUI.class, "refreshTrade", new Class<?>[0]);
   ShipState hold = (ShipState) field(bay, CargoBayUI.class, "tradeState");
   for (Object row : (List<?>) call(bay, CargoBayUI.class, "crewRows", new Class<?>[] {ShipState.class}, hold)) {
    Class<?> rc = row.getClass();
    bars.put((String) field(row, rc, "name"), new Object[] {field(row, rc, "bar"), field(row, rc, "barColor"), field(row, rc, "barRest"), field(row, rc, "tip")});
   }
   // the laid up one, sent aboard: refused, still in the Cargo Hold
   Object[] cats = (Object[]) field(bay, CargoBayUI.class, "cats");
   Object theirs = field(cats[3], cats[3].getClass(), "theirs");
   JList<?> list = (JList<?>) field(theirs, theirs.getClass(), "list");
   for (int i = 0; i < list.getModel().getSize(); i++) { Object row = list.getModel().getElementAt(i); if ("Laid Ulm".equals(field(row, row.getClass(), "name"))) list.setSelectedIndex(i); }
   call(bay, CargoBayUI.class, "sendCrew", new Class<?>[] {boolean.class}, false);
   boolean still = false; for (SavedGameParser.CrewState x : hold.getCrewList()) if (x.getName().equals("Laid Ulm")) still = true;
   r[0] = still;
   // the whole one taken aboard and saved: "Whole Wren assigned to the <her name>."
   for (int i = 0; i < list.getModel().getSize(); i++) { Object row = list.getModel().getElementAt(i); if ("Whole Wren".equals(field(row, row.getClass(), "name"))) list.setSelectedIndex(i); }
   call(bay, CargoBayUI.class, "sendCrew", new Class<?>[] {boolean.class}, false);
   bay.saveAll();
   String ship = ((SavedGameParser.SavedGameState) field(bay, CargoBayUI.class, "currentSave")).getPlayerShipName();
   r[2] = "Whole Wren assigned to " + (ship.startsWith("The ") ? ship : "the " + ship) + ".";
   // the Long Range's offer from the Cargo Hold leaves them out
   LongRangeCommUI comm = f.comm;
   java.lang.reflect.Field src = LongRangeCommUI.class.getDeclaredField("source"); src.setAccessible(true); src.set(comm, v.storage());
   java.lang.reflect.Field ss = LongRangeCommUI.class.getDeclaredField("sourceSave"); ss.setAccessible(true); ss.set(comm, v.readCopy(v.storage()).save);
   StringBuilder offered = new StringBuilder();
   for (Object l : (List<?>) call(comm, LongRangeCommUI.class, "contents", new Class<?>[0])) offered.append(((homeplanet.comm.Line) l).title()).append("; ");
   r[1] = offered.toString();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Object[] whole = bars.get("Whole Wren"), hurt = bars.get("Hurt Hale"), laid = bars.get("Laid Ulm");
  Setup.chk("I: in the Cargo Bay, the whole have no bar (" + whole[0] + ")", ((Float) whole[0]) < 0);
  Setup.chk("I: a hurt from the game: green for what's left (" + hurt[0] + "), red for the rest, and the tooltip says the station heals it", Math.abs((Float) hurt[0] - 0.4f) < 0.01f
    && ((Color) hurt[1]).getGreen() > 200 && ((Color) hurt[2]).getRed() > 200 && String.valueOf(hurt[3]).contains("Injured"));
  Setup.chk("I: the laid up: purple and full (" + laid[0] + "), and the tooltip says the infirmary", ((Float) laid[0]) == 1f && ((Color) laid[1]).getBlue() > 200 && ((Color) laid[1]).getRed() > 150
    && String.valueOf(laid[3]).contains("In the infirmary"));
  boolean said = false; for (String t : shown) if (t.contains("Laid Ulm is in the infirmary")) said = true;
  Setup.chk("I: sent aboard, the laid up are refused with a word, and stay in the Cargo Hold", Boolean.TRUE.equals(r[0]) && said);
  Setup.chk("I: the Long Range offers the Cargo Hold's crew but not the laid up (" + r[1] + ")", String.valueOf(r[1]).contains("Hurt Hale") && !String.valueOf(r[1]).contains("Laid Ulm"));
  Setup.chk("I: taken aboard and saved, the history log says so (" + r[2] + ")", new String(SafeFiles.read(HistoryLog.file()), "UTF-8").contains(String.valueOf(r[2])));
  inf.delete();
  // the ship report: each crew member's name opens their report; a hurt one has a bar under the icon
  final Object[] rep = new Object[3];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   SavedGameParser.SavedGameState sg = v.readCopy(v.boarded()).save;
   SavedGameParser.CrewState first = SaveHelper.getOwnCrew(sg.getPlayerShip()).get(0);
   first.setHealth(first.getRace().getMaxHealth() / 2);
   JPanel panel = f.spaceDock.shipSummaryPanel(sg);
   int clickable = 0, barred = 0;
   for (JLabel l : all(panel, JLabel.class)) {
    if (l.getToolTipText() != null && l.getToolTipText().startsWith("Click for ")) clickable++;
    if (l.getIcon() != null && l.getText() != null && l.getText().startsWith(first.getName() + " (") && l.getIcon().getIconHeight() > IconFactory.crewIcon(first).getIconHeight()) barred++;
   }
   rep[0] = clickable; rep[1] = SaveHelper.getOwnCrew(sg.getPlayerShip()).size(); rep[2] = barred;
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Setup.chk("I: in the ship report every crew member's name opens their report (" + rep[0] + " of " + rep[1] + "), and the hurt one has a health bar (" + rep[2] + ")",
    rep[0].equals(rep[1]) && Integer.valueOf(1).equals(rep[2]));
 }

 /** With the inbox off, a ransom comes up at the Space Dock: Pay brings them home. */
 static void ransomPopUp(final MainFrame f) throws Exception {
  final Vault v = Vault.get();
  java.lang.reflect.Method take = Expeditions.class.getDeclaredMethod("takeCaptive", Vault.class, SavedGameParser.CrewState.class, String.class); take.setAccessible(true);
  take.invoke(null, v, Commission.volunteer("energy", new Random(8)), "pirates");
  for (int i = 0; i < 6; i++) { SavedGameParser.SavedGameState g = HomePlanet.savedGameParser.readSavedGame(v.continueFile()); g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + 1); SaveHelper.writeSavedGame(v.continueFile(), g); v.takeStock(); }
  final List<Expeditions.RansomNews> news = Expeditions.checkRansoms(v);
  hold(v, 200);
  final String name = news.isEmpty() ? "?" : news.get(0).captive.name;
  shown.clear(); optionsShown.clear(); presses.clear(); presses.addAll(Arrays.asList(0, 0)); // Pay; the note that they're home
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   call(f.spaceDock, SpaceDockUI.class, "ransomNotice", new Class<?>[] {Expeditions.RansomNews.class}, news.get(0));
  } catch (Exception e) { throw new RuntimeException(e); } } });
  boolean home = false; for (SavedGameParser.CrewState x : Expeditions.holdCrew(v)) if (x.getName().equals(name)) home = true;
  Setup.chk("R: with the inbox off, the ransom comes up at the Space Dock with Pay, Refuse and Later; Pay brings " + name + " home",
    news.size() == 1 && !optionsShown.isEmpty() && Arrays.asList(optionsShown.get(0)).contains("Refuse") && home);
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
    String title = ((JDialog) w).getTitle();
    if (Boolean.TRUE.equals(((JDialog) w).getRootPane().getClientProperty("homeplanet.expedition"))) {
     expeditionCloseOps.add(((JDialog) w).getDefaultCloseOperation());
     if (atExpedition != null) { Runnable r = atExpedition; atExpedition = null; r.run(); }
    }
    shown.add(text(op.getMessage()));
    List<JButton> inMessage = op.getMessage() instanceof Container ? all((Container) op.getMessage(), JButton.class) : new ArrayList<JButton>();
    boolean ownButtons = op.getOptions() != null && op.getOptions().length == 0 && !inMessage.isEmpty(); // choices laid out in the message itself
    optionsShown.add(ownButtons ? inMessage.toArray() : op.getOptions() == null ? new Object[0] : op.getOptions());
    defaults.add(op.getInitialValue());
    JButton info = null; JComboBox<?> list = find((Container) w, JComboBox.class);
    for (JButton bt : all((Container) w, JButton.class)) if ("Info...".equals(bt.getText())) info = bt;
    extras.add(new Object[] {info, list == null ? null : list.getToolTipText()});
    Integer p = presses.isEmpty() ? -1 : presses.removeFirst();
    if (ownButtons && p >= 0) { inMessage.get(p).doClick(); return; }
    op.setValue(p < 0 || op.getOptions() == null ? Integer.valueOf(JOptionPane.CLOSED_OPTION) : op.getOptions()[p]);
    w.dispose();
    return;
   }
  } }).start();
 }
 static String text(Object m) {
  if (m instanceof String) return (String) m;
  if (m instanceof JLabel) return ((JLabel) m).getText();
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
