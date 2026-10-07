import java.io.*; import java.util.*; import java.util.List; import net.blerf.ftl.parser.SavedGameParser.ShipState; import java.awt.*; import javax.swing.*; import net.blerf.ftl.parser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*; import homeplanet.ui.*; import homeplanet.parser.Career; import homeplanet.parser.CareerRules;
/**
 * The station's windows, driven as a player would (needs a display: run.sh runs it under xvfb-run): the Dry Dock's bill
 * paid from the Cargo Hold on Save and dropped on Reset (the hold as the trade partner, and another ship as it); FTL's
 * System Limit in the shop and the Cargo Bay (the custom work order); the auction's three steps; the Junkyard's Info button and list tooltips. args: gamedir, world saves (from WorldT), work
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
  systemLimit(v, f);
  auction(v, f);
  junkyardInfo(v, f);
  modes();
  switched(f);
  holdAlone(v, f);
  damaged(f);
  folding(f);
  infirmaryBetweenJobs(f);
  liveDock(f);
  ransomPopUp(f);
  infirmaryBay(f);
  designSteps(f);
  Setup.done();
  System.exit(0);
 }

 static void hold(Vault v, int scrap) throws Exception { int s = v.storageScrap(); if (s < scrap) v.depositToStorage(scrap - s); else if (s > scrap) v.payFromStorage(s - scrap); }
 static Object field(Object o, Class<?> c, String name) throws Exception { java.lang.reflect.Field fd = c.getDeclaredField(name); fd.setAccessible(true); return fd.get(o); }
 static Object call(Object o, Class<?> c, String name, Class<?>[] types, Object... args) throws Exception { java.lang.reflect.Method m = c.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(o, args); }

 /** Design Ship as three steps (Plan X): the tabs, the Loadout step's rows writing into the design, the notes past vanilla, the anchor's label. */
 static void designSteps(final MainFrame f) throws Exception {
  List<ShipDesign> all = ShipDesign.load();
  final ShipDesign d = ShipDesign.create(all); d.name = "Steps Test";
  int[][] rooms = {{4,5,2,2},{6,5,2,2},{8,5,2,2},{6,4,2,1},{10,5,1,2}};
  for (int[] r : rooms) d.rooms.add(new ShipDesign.Room(r[0], r[1], r[2], r[3]));
  d.doors.add(d.doorFor(6,5,1)); d.doors.add(d.doorFor(8,5,1)); d.doors.add(d.doorFor(10,5,1)); d.doors.add(d.doorFor(6,5,0)); d.doors.add(d.doorFor(4,5,1));
  String[][] sys = {{"pilot","4"},{"engines","0"},{"oxygen","3"},{"shields","1"},{"weapons","2"}};
  for (String[] s : sys) { CompanionMod.Sys x = new CompanionMod.Sys(s[0]); x.room = Integer.parseInt(s[1]); x.power = CompanionMod.usualPower(s[0]); if (ShipDesign.manned(s[0])) { x.square = 0; x.dir = ShipDesign.defaultDir(s[0]); } d.systems.put(s[0], x); }
  ShipArt.adoptGameShip(d, "kestral");
  new Thread(new Runnable() { public void run() { DesignDialog.open(f, d, new ArrayList<String>()); } }).start();
  ShipEditorDialog dlg = null;
  for (int i = 0; i < 100 && dlg == null; i++) { Thread.sleep(100); for (Window w : Window.getWindows()) if (w instanceof ShipEditorDialog && w.isShowing()) dlg = (ShipEditorDialog) w; }
  Setup.chk("Steps: Design Ship opens", dlg != null);
  if (dlg == null) return;
  final ShipEditorDialog ed = dlg;
  final Object[] r = new Object[8];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   JTabbedPane steps = (JTabbedPane) field(ed, ShipEditorDialog.class, "steps");
   ShipDesign d = (ShipDesign) field(ed, ShipEditorDialog.class, "d"); // the editor works on a copy
   r[0] = steps.getTitleAt(0) + "|" + steps.getTitleAt(1) + "|" + steps.getTitleAt(2);
   r[1] = ed.stepShown();
   ed.showStep("Loadout");
   r[2] = ed.stepShown();
   NumberRow slots = null, shields = null;
   for (NumberRow n : rows(ed)) { if (n.name().equals("Weapon slots")) slots = n; if (n.name().equals("Shields")) shields = n; }
   r[3] = slots != null && shields != null && shields.tick() != null && slots.tick() == null;
   if (slots != null) slots.set(6, true);
   if (shields != null) shields.set(9, true);
   r[4] = ((JLabel) field(ed, ShipEditorDialog.class, "checks")).getText();
   r[5] = d.weaponSlots + "/" + d.systems.get("shields").power + "/" + (slots != null && slots.overMax());
   if (shields != null) { shields.tick().setSelected(false); for (java.awt.event.ActionListener al : shields.tick().getActionListeners()) al.actionPerformed(new java.awt.event.ActionEvent(shields.tick(), 0, "")); }
   r[6] = d.notAtStart.contains("shields");
   ed.showStep("Art");
   r[7] = ed.stepShown();
   ed.dispose();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Setup.chk("Steps: three steps in build order, Rooms in front: " + r[0] + ", " + r[1], "1. Rooms|2. Art|3. Loadout".equals(r[0]) && "Rooms".equals(r[1]));
  Setup.chk("Steps: the Loadout step comes to the front", "Loadout".equals(r[2]));
  Setup.chk("Steps: her numbers and her systems are rows; a system's has the tick, a number's hasn't", Boolean.TRUE.equals(r[3]));
  Setup.chk("Steps: 6 weapon slots and Shields at 9 typed in go straight into the design, the row marked over the max: " + r[5], "6/9/true".equals(r[5]));
  Setup.chk("Steps: the checks line says so, a note not a stop: " + r[4], String.valueOf(r[4]).contains("Past vanilla") && String.valueOf(r[4]).contains("nowhere to draw") && !String.valueOf(r[4]).contains("To fix"));
  Setup.chk("Steps: unticking a system's row takes it off the starting set", Boolean.TRUE.equals(r[6]));
  Setup.chk("Steps: the Art step comes to the front", "Art".equals(r[7]));
 }
 static List<NumberRow> rows(java.awt.Container c) { List<NumberRow> out = new ArrayList<NumberRow>(); for (java.awt.Component x : c.getComponents()) { if (x instanceof NumberRow) out.add((NumberRow) x); if (x instanceof java.awt.Container) out.addAll(rows((java.awt.Container) x)); } return out; }

 /** Repairs on the Refit tab: paid from the Cargo Hold on Save, nothing on Reset; the hold as the partner or another ship. */
 static void bill(final Vault v, final MainFrame f, final boolean otherPartner) throws Exception {
  final String tag = otherPartner ? " (another ship the partner)" : " (the Cargo Hold the partner)";
  SavedGameParser.SavedGameState g = v.readCopy(v.boarded()).save; g.getPlayerShip().setHullAmt(21); g.getPlayerShip().setScrapAmt(0); v.write(v.boarded(), g);
  hold(v, 100);
  final Object[] r = new Object[8];
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
   r[6] = v.readCopy(v.storage()).save.getPlayerShip().getFuelAmt();
   if (otherPartner) { // the shop puts seven fuel into its own copy of the Cargo Hold in the same Save (5.61: the bill's fresh copy replaced it, and the purchase vanished)
    Object shop = field(bay, CargoBayUI.class, "shop");
    SavedGameParser.SavedGameState hc = (SavedGameParser.SavedGameState) call(shop, shop.getClass(), "resolve", new Class<?>[] {Ship.class}, v.storage());
    hc.getPlayerShip().setFuelAmt(hc.getPlayerShip().getFuelAmt() + 7);
    call(shop, shop.getClass(), "markDirty", new Class<?>[] {SavedGameParser.SavedGameState.class}, hc);
   }
   r[2] = bay.saveAll();
   SavedGameParser.SavedGameState after = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
   r[3] = v.storageScrap(); r[4] = after.getPlayerShip().getScrapAmt(); r[5] = after.getPlayerShip().getHullAmt();
   r[7] = v.readCopy(v.storage()).save.getPlayerShip().getFuelAmt();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Setup.chk("B: the partner is as set" + tag, Boolean.valueOf(!otherPartner).equals(r[0]));
  Setup.chk("B: 9 hull points on the bill: the hold shows 64 before Save" + tag, Integer.valueOf(64).equals(r[1]));
  Setup.chk("B: Save pays the bill from the Cargo Hold, not her" + tag, Boolean.TRUE.equals(r[2]) && Integer.valueOf(64).equals(r[3]) && Integer.valueOf(0).equals(r[4]) && Integer.valueOf(30).equals(r[5]));
  if (otherPartner) Setup.chk("B: what the shop put into the Cargo Hold in the same Save is there too (fuel " + r[6] + " -> " + r[7] + ")" + tag, r[6] != null && Integer.valueOf((Integer) r[6] + 7).equals(r[7]));
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

 /**
  * FTL's System Limit on a Kestrel at 8 systems. The shop's Hacking shows the custom work order on hover, and Buy asks first
  * (heromedel's words; Install or Cancel, Cancel the default): Cancel changes nothing, Install takes the store's price and 100.
  * Install from the Cargo Bay asks the same and puts 100 on the Dry Dock's bill (paid from the Cargo Hold on Save, dropped on
  * Reset); a Clone Bay for her Medbay, or any system at 7, asks nothing and costs nothing.
  */
 static void systemLimit(final Vault v, final MainFrame f) throws Exception {
  final String ask = "This ship is at maximum capacity for systems.\nHome Planet Station can fit it in as a custom work order.\nBut it will cost 100 scrap.\n\n(This would excede the Vanilla FTL system Limit)";
  final String tip = "You've reached the System Limit. Home Planet Station can fit it in as a custom work order. But it will cost 100 scrap.";
  final SavedGameParser.SystemType HACK = SavedGameParser.SystemType.HACKING, MIND = SavedGameParser.SystemType.MIND, CLOAK = SavedGameParser.SystemType.CLOAKING;
  final Ship b = v.boarded();
  SavedGameParser.SavedGameState g = v.readCopy(b).save;
  ShipState s = g.getPlayerShip();
  for (SavedGameParser.SystemType t : new SavedGameParser.SystemType[] {SavedGameParser.SystemType.DRONE_CTRL, SavedGameParser.SystemType.TELEPORTER, CLOAK}) PriceT.fit(s, t, 1);
  SaveHelper.ensureAdvancedInfo(s, g.getFileFormat()); Retrofit.syncStations(s);
  int at = g.getCurrentBeaconId();
  while (g.getBeaconList().size() <= at) g.getBeaconList().add(new SavedGameParser.BeaconState());
  SavedGameParser.StoreState store = new SavedGameParser.StoreState(); SavedGameParser.StoreShelf shelf = new SavedGameParser.StoreShelf();
  SavedGameParser.StoreItem item = new SavedGameParser.StoreItem("hacking"); item.setAvailable(true);
  shelf.setItemType(SavedGameParser.StoreItemType.SYSTEM); shelf.addItem(item); store.addShelf(shelf);
  g.getBeaconList().get(at).setStore(store);
  final int price = DataManager.get().getSystem("hacking").getCost();
  s.setScrapAmt(price + homeplanet.core.Economy.commissionWorkOrder() + 7);
  v.write(b, g);
  hold(v, 150);
  shown.clear(); optionsShown.clear(); defaults.clear(); presses.clear();
  presses.addAll(Arrays.asList(1, 0)); // Cancel, then Install
  final Object[] r = new Object[8];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   f.showCargoBay();
   CargoBayUI bay = f.cargoBay;
   bay.init();
   Object shop = field(bay, CargoBayUI.class, "shop");
   Object hack = null;
   for (Object e : (List<?>) call(shop, shop.getClass(), "buildEntries", new Class<?>[0])) if ("hacking".equals(field(e, e.getClass(), "id"))) hack = e;
   r[0] = hack != null && SaveHelper.systemCount(mine(bay)) == 8;
   if (hack == null) return; // (the checks below then fail, rather than leave the window open)
   for (Component c : ((JComponent) field(shop, shop.getClass(), "content")).getComponents()) {
    if (!c.getClass().getSimpleName().equals("StoreRow")) continue;
    Object e = field(c, c.getClass(), "e");
    if ("hacking".equals(field(e, e.getClass(), "id"))) r[1] = ((JComponent) c).getToolTipText();
   }
   call(shop, shop.getClass(), "buy", new Class<?>[] {hack.getClass()}, hack); // Cancel
   r[2] = mine(bay).getScrapAmt(); r[3] = level(mine(bay), HACK);
   call(shop, shop.getClass(), "buy", new Class<?>[] {hack.getClass()}, hack); // Install
   r[4] = mine(bay).getScrapAmt(); r[5] = level(mine(bay), HACK);
   r[6] = bay.saveAll();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  ShipState saved = HomePlanet.savedGameParser.readSavedGame(v.continueFile()).getPlayerShip();
  Setup.chk("X: at 8 systems the shop sells her Hacking, the custom work order on hover", Boolean.TRUE.equals(r[0]) && tip.equals(r[1]));
  Setup.chk("X: Buy asks first, in heromedel's words, Install or Cancel (Cancel the default)", shown.size() >= 1 && ask.equals(shown.get(0))
    && Arrays.asList(optionsShown.get(0)).equals(Arrays.asList("Install", "Cancel")) && "Cancel".equals(String.valueOf(defaults.get(0))));
  Setup.chk("X: Cancel changes nothing", Integer.valueOf(price + homeplanet.core.Economy.commissionWorkOrder() + 7).equals(r[2]) && Integer.valueOf(0).equals(r[3]));
  Setup.chk("X: Install fits it for the store's price and 100", shown.size() == 2 && Integer.valueOf(7).equals(r[4]) && Integer.valueOf(1).equals(r[5]));
  Setup.chk("X: Save writes her so", Boolean.TRUE.equals(r[6]) && saved.getScrapAmt() == 7 && level(saved, HACK) == 1 && SaveHelper.systemCount(saved) == 9);

  // a system she hasn't got, bought with the Cargo Hold's scrap (5.79): FTL's System Limit is a ship's, never the hold's,
  // but the row priced in her custom work order and greyed Buy out (another player's report, 5.26)
  {
   SavedGameParser.SavedGameState g2 = v.readCopy(b).save;
   SavedGameParser.StoreState st2 = new SavedGameParser.StoreState(); SavedGameParser.StoreShelf sh2 = new SavedGameParser.StoreShelf();
   SavedGameParser.StoreItem again = new SavedGameParser.StoreItem("mind"); again.setAvailable(true);
   sh2.setItemType(SavedGameParser.StoreItemType.SYSTEM); sh2.addItem(again); st2.addShelf(sh2);
   g2.getBeaconList().get(g2.getCurrentBeaconId()).setStore(st2);
   v.write(b, g2);
  }
  final int mindPrice = DataManager.get().getSystem("mind").getCost();
  hold(v, mindPrice + 3); // the store's price, not a work order's more
  shown.clear(); optionsShown.clear(); defaults.clear(); presses.clear();
  presses.addAll(Arrays.asList(0, 0));
  final Object[] h = new Object[6];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   CargoBayUI bay = f.cargoBay;
   bay.init();
   Object shop = field(bay, CargoBayUI.class, "shop");
   java.lang.reflect.Field ts = shop.getClass().getDeclaredField("toStorage"); ts.setAccessible(true); ts.set(shop, true);
   call(shop, shop.getClass(), "rebuild", new Class<?>[0]);
   Object hack = null;
   for (Object e : (List<?>) call(shop, shop.getClass(), "buildEntries", new Class<?>[0])) if ("mind".equals(field(e, e.getClass(), "id"))) hack = e;
   for (Component c : ((JComponent) field(shop, shop.getClass(), "content")).getComponents()) {
    if (!c.getClass().getSimpleName().equals("StoreRow")) continue;
    Object e = field(c, c.getClass(), "e");
    if ("mind".equals(field(e, e.getClass(), "id"))) { h[0] = field(c, c.getClass(), "can"); h[1] = ((JComponent) c).getToolTipText(); }
   }
   if (hack == null) return;
   call(shop, shop.getClass(), "buy", new Class<?>[] {hack.getClass()}, hack);
   SavedGameParser.SavedGameState hs = (SavedGameParser.SavedGameState) call(shop, shop.getClass(), "resolve", new Class<?>[] {Ship.class}, v.storage());
   h[2] = hs.getPlayerShip().getScrapAmt();
   h[3] = bay.saveAll();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Setup.chk("X: for the Cargo Hold, her past the System Limit, a Mind Control (not hers) can be bought: no work order on the row", Boolean.TRUE.equals(h[0]) && h[1] != null && !String.valueOf(h[1]).toLowerCase().contains("work order"));
  Setup.chk("X: bought for the store's price alone, and saved", Integer.valueOf(3).equals(h[2]) && Boolean.TRUE.equals(h[3]) && v.storageScrap() == 3);

  // FTL's ship is asked about only when the Cargo Bay's save changes her, whichever ship is picked (heromedel, 5.81):
  // fuel for the Cargo Hold from another ship's store, her picked: no question, her file untouched; fuel for the hold
  // from her own store (its stock is in her save), another ship picked: the question, Nevermind saves nothing, Go ahead saves
  {
   Ship other = null; for (Ship d2 : v.docked()) if (d2 != b) { other = d2; break; }
   SavedGameParser.SavedGameState herMap = v.readCopy(b).save;
   for (Ship withStore : new Ship[] {b, other}) {
    SavedGameParser.SavedGameState gs = v.readCopy(withStore).save;
    if (gs.getBeaconList().isEmpty()) { // a ship the test world made has no map: hers, so she sits at a beacon too
     for (SavedGameParser.BeaconState bs : herMap.getBeaconList()) gs.getBeaconList().add(new SavedGameParser.BeaconState(bs));
     gs.setCurrentBeaconId(herMap.getCurrentBeaconId());
    }
    SavedGameParser.StoreState st = gs.getBeaconList().get(gs.getCurrentBeaconId()).getStore();
    if (st == null) { st = new SavedGameParser.StoreState(); gs.getBeaconList().get(gs.getCurrentBeaconId()).setStore(st); }
    st.setFuel(3);
    v.write(withStore, gs);
   }
   hold(v, 50);
   System.setProperty("homeplanet.ftlRunning", "true");
   final Ship store2 = other;
   final Object[] k = new Object[8];
   String herHash = SafeFiles.hash(v.fileOf(b));
   shown.clear(); optionsShown.clear(); presses.clear(); presses.addAll(Arrays.asList(1, 1));
   SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
    CargoBayUI bay = f.cargoBay;
    bay.init(); // her, FTL's ship, picked
    k[0] = buyFuel(bay, store2);
    k[1] = bay.saveAll();
   } catch (Exception e) { throw new RuntimeException(e); } } });
   int otherFuel = v.readCopy(other).save.getBeaconList().get(v.readCopy(other).save.getCurrentBeaconId()).getStore().getFuel();
   Setup.chk("Q: fuel for the Cargo Hold from another ship's store, FTL's ship picked: no question " + shown + ", her file untouched, the hold and that store saved",
     Boolean.TRUE.equals(k[0]) && Boolean.TRUE.equals(k[1]) && !asked() && herHash.equals(SafeFiles.hash(v.fileOf(b))) && otherFuel == 2 && v.storage().save().getPlayerShip().getFuelAmt() >= 1);
   int holdFuel = v.storage().save().getPlayerShip().getFuelAmt();
   shown.clear(); optionsShown.clear(); presses.clear(); presses.addAll(Arrays.asList(0, 1)); // Nevermind, then Go ahead
   final Ship herShip = b;
   SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
    CargoBayUI bay = f.cargoBay;
    bay.init();
    call(bay, CargoBayUI.class, "pick", new Class<?>[] {Ship.class}, store2); // the other ship picked
    k[2] = buyFuel(bay, herShip);
    k[3] = bay.saveAll(); // Nevermind
    k[4] = asked();
    k[5] = bay.saveAll(); // Go ahead
    java.lang.reflect.Field pk = CargoBayUI.class.getDeclaredField("picked"); pk.setAccessible(true); pk.set(bay, null); bay.init(); // no pick: back on FTL's ship, as the checks after these expect
   } catch (Exception e) { throw new RuntimeException(e); } } });
   SavedGameParser.SavedGameState herNow = v.readCopy(b).save;
   Setup.chk("Q: fuel for the hold from her own store, another ship picked: FTL is asked about " + shown + "; Nevermind saves nothing",
     Boolean.TRUE.equals(k[2]) && Boolean.FALSE.equals(k[3]) && Boolean.TRUE.equals(k[4]));
   Setup.chk("Q: and Go ahead saves it: her store one fuel short, the hold one more",
     Boolean.TRUE.equals(k[5]) && herNow.getBeaconList().get(herNow.getCurrentBeaconId()).getStore().getFuel() == 2 && v.storage().save().getPlayerShip().getFuelAmt() == holdFuel + 1);
   System.clearProperty("homeplanet.ftlRunning");
   shown.clear(); optionsShown.clear(); presses.clear();
  }

  // from the Cargo Bay: a Mind Control past the limit, then a Clone Bay for her Medbay
  SafeFiles.writeText(v.systemsFile(), "# stored\nmind 2\nclonebay\n", false);
  hold(v, 150);
  shown.clear(); optionsShown.clear(); defaults.clear(); presses.clear();
  presses.addAll(Arrays.asList(1, 0, 0)); // Cancel, Install; after Reset, Install again
  final Object[] q = new Object[16];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   CargoBayUI bay = f.cargoBay;
   bay.init();
   Object sys = field(bay, CargoBayUI.class, "systems");
   Object mind = stored(sys, "mind"), clone = stored(sys, "clonebay");
   q[0] = call(sys, sys.getClass(), "installReason", new Class<?>[] {String.class}, "mind");
   if (mind == null || clone == null) { q[0] = "not stored"; return; }
   call(sys, sys.getClass(), "installSystem", new Class<?>[] {mind.getClass()}, mind); // Cancel
   q[1] = level(mine(bay), MIND); q[2] = call(sys, sys.getClass(), "hold", new Class<?>[0]);
   call(sys, sys.getClass(), "installSystem", new Class<?>[] {mind.getClass()}, mind); // Install
   q[3] = level(mine(bay), MIND); q[4] = call(sys, sys.getClass(), "hold", new Class<?>[0]);
   int asked = shown.size();
   call(sys, sys.getClass(), "installSystem", new Class<?>[] {clone.getClass()}, clone);
   q[5] = shown.size() - asked; q[6] = level(mine(bay), SavedGameParser.SystemType.CLONEBAY); q[7] = level(mine(bay), SavedGameParser.SystemType.MEDBAY);
   q[8] = call(sys, sys.getClass(), "hold", new Class<?>[0]);
   bay.init(); // Reset
   q[9] = call(sys, sys.getClass(), "hold", new Class<?>[0]); q[10] = level(mine(bay), MIND);
   mind = stored(sys, "mind");
   call(sys, sys.getClass(), "installSystem", new Class<?>[] {mind.getClass()}, mind); // Install, and Save
   q[11] = bay.saveAll();
   q[13] = Vault.get().storageScrap();
   bay.init();
   q[12] = call(sys, sys.getClass(), "installReason", new Class<?>[] {String.class}, "clonebay"); // a swap: the hold's 50 doesn't matter
  } catch (Exception e) { throw new RuntimeException(e); } } });
  saved = HomePlanet.savedGameParser.readSavedGame(v.continueFile()).getPlayerShip();
  String file = new String(SafeFiles.read(v.systemsFile()), "UTF-8");
  Setup.chk("X: the Cargo Bay's Install: the hold can pay, so it may", q[0] == null);
  Setup.chk("X: Install from the Cargo Bay asks the same, Cancel the default", shown.size() >= 1 && ask.equals(shown.get(0)) && "Cancel".equals(String.valueOf(defaults.get(0))));
  Setup.chk("X: Cancel changes nothing", Integer.valueOf(0).equals(q[1]) && Integer.valueOf(150).equals(q[2]));
  Setup.chk("X: Install fits it at its level, 100 on the Dry Dock's bill", Integer.valueOf(2).equals(q[3]) && Integer.valueOf(50).equals(q[4]));
  Setup.chk("X: a Clone Bay for her Medbay asks nothing and costs nothing", Integer.valueOf(0).equals(q[5]) && Integer.valueOf(1).equals(q[6]) && Integer.valueOf(0).equals(q[7]) && Integer.valueOf(50).equals(q[8]));
  Setup.chk("X: Reset drops the bill", Integer.valueOf(150).equals(q[9]) && Integer.valueOf(0).equals(q[10]));
  Setup.chk("X: Save pays it from the Cargo Hold", Boolean.TRUE.equals(q[11]) && Integer.valueOf(50).equals(q[13]) && level(saved, MIND) == 2 && !file.contains("mind") && shown.size() == 3);
  Setup.chk("X: a swap needs no work order, whatever the hold has (50 now)", q[12] == null);

  // short of 100, the Install waits; at 7 systems nothing asks
  final Object[] w = new Object[6];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   GuiT.hold(Vault.get(), 60);
   SafeFiles.writeText(Vault.get().systemsFile(), "# stored\ncloaking 1\nclonebay\n", false);
   CargoBayUI bay = f.cargoBay;
   ShipState now = v.readCopy(b).save.getPlayerShip();
   w[0] = SaveHelper.systemCount(now);
   bay.init();
   Object sys = field(bay, CargoBayUI.class, "systems");
   PriceT.fit(mine(bay), CLOAK, 0); // she's carrying 9 then: Cloaking makes it 10, past the limit
   w[1] = call(sys, sys.getClass(), "installReason", new Class<?>[] {String.class}, "cloaking");
   PriceT.fit(mine(bay), HACK, 0); PriceT.fit(mine(bay), MIND, 0); // down to 7
   int asked = shown.size();
   Object cloak = stored(sys, "cloaking");
   if (cloak != null) call(sys, sys.getClass(), "installSystem", new Class<?>[] {cloak.getClass()}, cloak);
   w[2] = shown.size() - asked; w[3] = level(mine(bay), CLOAK); w[4] = call(sys, sys.getClass(), "hold", new Class<?>[0]);
   bay.init(); // Reset: nothing of this is kept
   f.showSpaceDock();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Setup.chk("X: past the limit with 60 in the Cargo Hold, the Install waits, saying why (" + w[1] + ")", Integer.valueOf(10).equals(w[0]) && String.valueOf(w[1]).contains("custom work order costs 100") && String.valueOf(w[1]).contains("has 60"));
  Setup.chk("X: at 7, a stored system goes in without asking, for nothing", Integer.valueOf(0).equals(w[2]) && Integer.valueOf(1).equals(w[3]) && Integer.valueOf(60).equals(w[4]));
  v.systemsFile().delete();
 }
 /** One fuel for the Cargo Hold from the store at this ship's beacon, through the shop as its button does; false if there's no such row. */
 static boolean buyFuel(CargoBayUI bay, Ship at) throws Exception {
  Object shop = field(bay, CargoBayUI.class, "shop");
  java.lang.reflect.Field ts = shop.getClass().getDeclaredField("toStorage"); ts.setAccessible(true); ts.set(shop, true);
  call(shop, shop.getClass(), "rebuild", new Class<?>[0]);
  for (Object e : (List<?>) call(shop, shop.getClass(), "buildEntries", new Class<?>[0]))
   if ("FUEL".equals(String.valueOf(field(e, e.getClass(), "kind"))) && field(e, e.getClass(), "ship") == at) { call(shop, shop.getClass(), "buy", new Class<?>[] {e.getClass()}, e); return true; }
  return false;
 }
 /** Whether the "FTL is running" question was put since shown was last cleared. */
 static boolean asked() { for (String m : shown) if (m.startsWith("FTL is running")) return true; return false; }
 static ShipState mine(CargoBayUI bay) throws Exception { return ((SavedGameParser.SavedGameState) field(bay, CargoBayUI.class, "currentSave")).getPlayerShip(); }
 static int level(ShipState s, SavedGameParser.SystemType t) { SavedGameParser.SystemState st = s.getSystem(t); return st == null ? 0 : st.getCapacity(); }
 static Object stored(Object sys, String id) throws Exception {
  for (Object x : (List<?>) call(sys, sys.getClass(), "storedList", new Class<?>[0])) if (id.equals(field(x, x.getClass(), "id"))) return x;
  return null;
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
    v.byId(id) == null && new String(SafeFiles.read(new File(v.folderOfId(id), "fate.txt")), "UTF-8").startsWith("SOLD")
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
  // a retrofitted ship aboard, whose systems can come off: which ship the earlier steps leave aboard changed when the
  // Space Dock went to listing by name (5.69), and a plain one's are all standard equipment
  if (!v.boarded().save().getPlayerShipBlueprintId().endsWith(Retrofit.SUFFIX))
   for (Ship s : v.docked()) { SavedGameParser.SavedGameState sg = s.save(); if (sg != null && sg.getPlayerShipBlueprintId().endsWith(Retrofit.SUFFIX)) { v.board(s); break; } }
  Vault.Copy c = v.readCopy(v.boarded());
  ShipState bs = c.save.getPlayerShip();
  final SavedGameParser.SystemType[] pick = new SavedGameParser.SystemType[1];
  Class<?> sp = Class.forName("homeplanet.ui.SystemsPanel");
  for (SavedGameParser.SystemType t : SavedGameParser.SystemType.values()) {
   SavedGameParser.SystemState st = bs.getSystem(t);
   if (pick[0] != null || st == null || st.getCapacity() < 2 || t == SavedGameParser.SystemType.WEAPONS || t == SavedGameParser.SystemType.DRONE_CTRL || t == SavedGameParser.SystemType.CLONEBAY || t == SavedGameParser.SystemType.MEDBAY) continue;
   if (call(null, sp, "refitReason", new Class<?>[] {ShipState.class, SavedGameParser.SystemType.class}, bs, t) == null) pick[0] = t;
  }
  if (pick[0] == null) { // which ship, and why none of hers can go
   StringBuilder why = new StringBuilder();
   for (SavedGameParser.SystemType t : SavedGameParser.SystemType.values()) { SavedGameParser.SystemState st = bs.getSystem(t); if (st != null && st.getCapacity() > 0) why.append("; ").append(t).append(' ').append(st.getCapacity()).append(": ").append(call(null, sp, "refitReason", new Class<?>[] {ShipState.class, SavedGameParser.SystemType.class}, bs, t)); }
   Setup.chk("R: a system on her that can be stored (" + v.boarded().name + ", " + c.save.getPlayerShipBlueprintId() + why + ")", false); return;
  }
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
   r[3] = call(null, CargoBayUI.class, "unreadableNote", new Class<?>[0]); // no ship aboard: nothing to say, the Cargo Bay opens
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
  // and from there, pick a docked ship to work on: the Cargo Bay follows the pick, nobody is boarded (Plan Y)
  final Ship next = v.docked().get(0);
  final Object[] b = new Object[4];
  shown.clear(); presses.clear(); presses.addAll(Arrays.asList(1, 1, 1)); // nothing should ask; if something does, its second button, and the check says what it was
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   CargoBayUI bay = f.cargoBay;
   call(bay, CargoBayUI.class, "pick", new Class<?>[] {Ship.class}, next);
   b[0] = call(bay, CargoBayUI.class, "holdOnly", new Class<?>[0]);
   b[1] = field(bay, CargoBayUI.class, "currentShip");
   f.showCargoBay(); // opened again: back to the boarded ship (none), the Cargo Hold alone
   b[2] = call(bay, CargoBayUI.class, "holdOnly", new Class<?>[0]);
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Setup.chk("Y: a docked ship picked in the Cargo Bay: the screen works on her, nobody is boarded, no pop-up " + shown, v.boarded() == null && Boolean.FALSE.equals(b[0]) && b[1] == next && shown.isEmpty());
  Setup.chk("Y: opened again, the pick is fresh: the boarded ship (none), so the Cargo Hold alone", Boolean.TRUE.equals(b[2]));
  v.board(next); // the tests after this one work on a boarded ship, as before
  // her save can't be read (heromedel's "comes and goes", 5.81): the Cargo Bay opens anyway, without her, and says why
  final byte[] keep = SafeFiles.read(v.continueFile());
  SafeFiles.write(v.continueFile(), "not a save".getBytes("UTF-8"));
  final Object[] u = new Object[4];
  shown.clear(); optionsShown.clear(); presses.clear(); presses.addAll(Arrays.asList(0, 0));
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   f.showSpaceDock();
   Object btn = field(f.spaceDock, SpaceDockUI.class, "cargoBtn");
   f.spaceDock.actionPerformed(new java.awt.event.ActionEvent(btn, java.awt.event.ActionEvent.ACTION_PERFORMED, "cargo"));
   CargoBayUI bay = f.cargoBay;
   u[0] = bay.isShowing();
   u[1] = field(bay, CargoBayUI.class, "currentShip");
   u[2] = ((List<?>) call(bay, CargoBayUI.class, "tradeableShips", new Class<?>[0])).contains(next);
  } catch (Exception e) { throw new RuntimeException(e); } } });
  boolean said = false; for (String m : shown) if (m.contains("could not load " + homeplanet.parser.ShipNames.the(next.name)) && m.contains("opens without her")) said = true;
  Setup.chk("U: the boarded ship's save can't be read: the Cargo Bay opens anyway " + shown, Boolean.TRUE.equals(u[0]));
  Setup.chk("U: without her (not worked on, not in range for trade or her store), and a note says why", u[1] == null && Boolean.FALSE.equals(u[2]) && said);
  SafeFiles.write(v.continueFile(), keep);
  shown.clear(); optionsShown.clear(); presses.clear();
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
  final File inf = Expeditions.infirmaryFile(v);
  Store.write(inf, Store.parse(("healed_at=" + v.beaconsSeen() + "\n0.name=Laid Ulm\n0.race=human\n0.until=" + (v.beaconsSeen() + 4) + "\n0.drained=" + v.beaconsSeen() + "\n").getBytes("UTF-8")), null);
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

 /** The Space Dock keeps itself current: a letter delivered or read (a write in the fleet's folder) changes the inbox's count behind any window, with no rebuild asked for. */
 static void liveDock(final MainFrame f) throws Exception {
  HomePlanet.immersiveNotifications = true;
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { f.showSpaceDock(); } });
  int before = inboxCount(f);
  Transmissions.deliver("live:test", "Home Planet Liaison", "A test of the live dock", "Just checking the dock keeps up.");
  int after = waitCount(f, before + 1);
  Transmissions.Message m = null; for (Transmissions.Message x : Transmissions.load()) if (x.key.equals("live:test")) m = x;
  Transmissions.markRead(m);
  int read = waitCount(f, before);
  Transmissions.delete(m);
  Setup.chk("L: a letter delivered: the inbox's count behind the window goes " + before + " -> " + after + " by itself; read: -> " + read, after == before + 1 && read == before);
 }
 static int inboxCount(final MainFrame f) throws Exception {
  final int[] n = {-1};
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   Object btn = field(f.spaceDock, SpaceDockUI.class, "inboxBtn");
   n[0] = btn == null ? -1 : ((Integer) field(btn, btn.getClass(), "unread")).intValue();
  } catch (Exception e) { throw new RuntimeException(e); } } });
  return n[0];
 }
 /** The count once the Space Dock has rebuilt itself (within a few seconds), or whatever it shows then. */
 static int waitCount(MainFrame f, int want) throws Exception {
  int n = -1;
  for (int t = 0; t < 40; t++) { n = inboxCount(f); if (n == want) return n; Thread.sleep(100); }
  return n;
 }
 /** Jobs follow one another without a look at the Space Dock: whoever's time is up leaves the infirmary between them, with the pop-up. */
 static void infirmaryBetweenJobs(final MainFrame f) throws Exception {
  final Vault v = Vault.get();
  SavedGameParser.CrewState hurt = Expeditions.holdCrew(v).get(0);
  Properties inf = new Properties();
  inf.setProperty("0.name", hurt.getName()); inf.setProperty("0.race", hurt.getRace().getId()); inf.setProperty("0.until", Integer.toString(v.beaconsSeen())); inf.setProperty("0.drained", Integer.toString(v.beaconsSeen()));
  Store.write(Expeditions.infirmaryFile(v), inf, null);
  int free = Expeditions.holdCrew(v).size();
  shown.clear(); presses.clear(); presses.add(0);
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   call(f.spaceDock, SpaceDockUI.class, "timeRound", new Class<?>[] {boolean.class}, false); // the station's round (the job board's afterJob went with it at 5.67)
  } catch (Exception e) { throw new RuntimeException(e); } } });
  boolean word = false; for (String t : shown) if (t.contains(hurt.getName()) && t.contains("out of the infirmary")) word = true;
  Setup.chk("X: at the station's round, " + hurt.getName() + "'s time up: out of the infirmary with the pop-up, free to send again (" + free + " -> " + Expeditions.holdCrew(v).size() + ")",
    word && Expeditions.holdCrew(v).size() == free + 1 && Expeditions.infirmary(v).isEmpty());
 }
 /** With the inbox off, a ransom comes up at the Space Dock: Pay brings them home. */
 static void ransomPopUp(final MainFrame f) throws Exception {
  final Vault v = Vault.get();
  ExpT.take(v, Commission.volunteer("energy", new Random(8)));
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
