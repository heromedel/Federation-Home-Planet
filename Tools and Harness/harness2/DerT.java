import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The Junkyard's derelicts: built wrecks that FTL can read back, listings every 30 beacons, buying, strange rebuilds. args: gamedir, world saves (from WorldT), work */
public class DerT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 wrecks();
 listings(v);
 rebuilds(v);
 refinalize(v);
 Setup.done();
}
 /** An overhaul re-finalized under the same remodel id: her room states follow the new shape (the old layout is read before the new one takes its id). */
 static void refinalize(Vault v) throws Exception {
  SavedGameState save = Commission.build("PLAYER_SHIP_HARD", "Reshaped", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(5));
  ShipDesign g = ShipDesign.create(new ArrayList<ShipDesign>()); g.name = "Reshape";
  Setup.chk("R: a design from the Kestrel", ShipDesign.fromGameShip(g, "PLAYER_SHIP_HARD"));
  List<CompanionMod.Remodel> all = CompanionMod.load();
  CompanionMod.Remodel r = CompanionMod.create("PLAYER_SHIP_HARD", "Reshaped", all);
  r.ship = "Reshaped"; r.geometry = ShipDesign.copy(g); r.geometry.id = r.id;
  for (CompanionMod.Sys x : g.systems.values()) r.systems.put(x.id, x.copy());
  int nx = Integer.MAX_VALUE, ny = Integer.MAX_VALUE; for (ShipDesign.Room rm : g.rooms) { nx = Math.min(nx, rm.x); ny = Math.min(ny, rm.y); }
  r.doors = new ArrayList<CompanionMod.Door>(); for (CompanionMod.Door x : g.doors) r.doors.add(new CompanionMod.Door(x.x - nx, x.y - ny, x.a, x.b, x.v));
  all.add(r); CompanionMod.save(all);
  net.blerf.ftl.model.shiplayout.ShipLayout oldLay = DataManager.get().getShipLayout(save.getPlayerShip().getShipLayoutId());
  CompanionMod.register(all);
  Retrofit.switchTo(save, r.id, 0, 0, oldLay);
  net.blerf.ftl.model.shiplayout.ShipLayout lay1 = DataManager.get().getShipLayout(save.getPlayerShip().getShipLayoutId());
  boolean fit1 = true; for (int i = 0; i < lay1.getRoomCount(); i++) if (save.getPlayerShip().getRoomList().get(i).getSquareList().size() != lay1.getRoom(i).squaresH * lay1.getRoom(i).squaresV) fit1 = false;
  Setup.chk("R: finalized as an overhaul of the Kestrel's own shape: every room state fits its room", fit1 && save.getPlayerShip().getRoomList().size() == lay1.getRoomCount());
  // re-finalize under the same id: one 2x2 room becomes 2x1 (the doors and stations of the lost row go)
  int pick = -1; for (int i = 0; i < r.geometry.rooms.size(); i++) { ShipDesign.Room rm = r.geometry.rooms.get(i); if (rm.w == 2 && rm.h == 2) pick = i; }
  ShipDesign.Room rm = r.geometry.rooms.get(pick); int lostRow = rm.y + 1; rm.h = 1;
  List<CompanionMod.Door> keep = new ArrayList<CompanionMod.Door>();
  for (CompanionMod.Door x : r.geometry.doors) { boolean mine = x.a == pick || x.b == pick; boolean onLost = (x.v == 0 && x.y == rm.y + 2) || (x.v == 1 && x.y == lostRow); if (!(mine && onLost)) keep.add(x); }
  r.geometry.doors.clear(); r.geometry.doors.addAll(keep);
  for (CompanionMod.Sys x : r.geometry.systems.values()) if (x.room == pick && x.square != null && x.square >= 2) { x.square = null; x.dir = null; }
  r.systems.clear(); for (CompanionMod.Sys x : r.geometry.systems.values()) r.systems.put(x.id, x.copy());
  nx = Integer.MAX_VALUE; ny = Integer.MAX_VALUE; for (ShipDesign.Room q : r.geometry.rooms) { nx = Math.min(nx, q.x); ny = Math.min(ny, q.y); }
  r.doors = new ArrayList<CompanionMod.Door>(); for (CompanionMod.Door x : r.geometry.doors) r.doors.add(new CompanionMod.Door(x.x - nx, x.y - ny, x.a, x.b, x.v));
  CompanionMod.save(all);
  oldLay = DataManager.get().getShipLayout(save.getPlayerShip().getShipLayoutId()); // as the dialog does: before the id names the new rooms
  CompanionMod.register(all);
  Retrofit.switchTo(save, r.id, 0, 0, oldLay);
  net.blerf.ftl.model.shiplayout.ShipLayout lay2 = DataManager.get().getShipLayout(save.getPlayerShip().getShipLayoutId());
  boolean fit2 = true; for (int i = 0; i < lay2.getRoomCount(); i++) if (save.getPlayerShip().getRoomList().get(i).getSquareList().size() != lay2.getRoom(i).squaresH * lay2.getRoom(i).squaresV) fit2 = false;
  Setup.chk("R: re-finalized with room " + pick + " shrunk to 2x1: its room state has 2 squares, every room fits (" + save.getPlayerShip().getRoomList().get(pick).getSquareList().size() + ")", fit2 && lay2.getRoom(pick).squaresV == 1);
  File t = File.createTempFile("reshape", ".sav"); SafeFiles.write(t, SaveHelper.toBytes(save));
  SavedGameState back = HomePlanet.savedGameParser.readSavedGame(t); t.delete();
  Setup.chk("R: she reads back whole", back.getPlayerShip().getRoomList().size() == lay2.getRoomCount() && back.getPlayerShip().getCrewList().size() == save.getPlayerShip().getCrewList().size());
  all.remove(r); CompanionMod.save(all); CompanionMod.register(all);
 }
 /** Many derelicts of every model: wrecked as promised, and each save the same when read back and written again. */
 static void wrecks() throws Exception {
  Random rng = new Random(11);
  int missiles = 0, parts = 0;
  int n = 0, roundTrip = 0, wrecked = 0, stripped = 0, armed = 0, odd = 0, levels = 0, missing = 0, added = 0, breaches = 0, pastLimit = 0, most = 0;
  for (String base : DataManager.get().getPlayerShipBaseIds(true)) for (int k = 0; k < 3; k++) {
   String id = k == 0 ? base : base + "_" + (k + 1);
   if (DataManager.get().getShips().get(id) == null || CompanionMod.fileOf(id) == null) continue;
   for (int r = 0; r < 6; r++) {
    SavedGameState gs = Derelicts.build(id, "Wreck " + n, rng);
    ShipState s = gs.getPlayerShip(); n++;
    byte[] once = SaveHelper.toBytes(gs);
    File f = File.createTempFile("derelict", ".sav"); SafeFiles.write(f, once);
    byte[] twice = SaveHelper.toBytes(HomePlanet.savedGameParser.readSavedGame(f)); f.delete();
    if (Arrays.equals(once, twice)) roundTrip++;
    int max = DataManager.get().getShip(s.getShipBlueprintId()).getHealth().amount;
    if (s.getHullAmt() <= max / 2 && s.getHullAmt() >= 1 && s.getCrewList().isEmpty() && s.getShipBlueprintId().endsWith(Retrofit.SUFFIX)) wrecked++;
    int launchers = 0; for (SavedGameParser.WeaponState w : s.getWeaponList()) if (DataManager.get().getWeapons().get(w.getWeaponId()).getMissiles() > 0) launchers++;
    SystemState hk = s.getSystem(SystemType.HACKING); boolean hacking = hk != null && hk.getCapacity() > 0;
    int slots = DataManager.get().getShip(s.getShipBlueprintId()).getWeaponSlots() == null ? 4 : DataManager.get().getShip(s.getShipBlueprintId()).getWeaponSlots();
    if (s.getScrapAmt() == 0 && gs.getCargoIdList().isEmpty() && (launchers > 0 || s.getMissilesAmt() == 0) && s.getMissilesAmt() <= 2 + launchers
      && (!s.getDroneList().isEmpty() || hacking || s.getDronePartsAmt() == 0) && s.getWeaponList().size() <= slots) stripped++;
    if (s.getMissilesAmt() > 0) missiles++;
    if (s.getDronePartsAmt() > 0) parts++;
    if (!s.getWeaponList().isEmpty() || !s.getDroneList().isEmpty()) armed++;
    if (!s.getBreachMap().isEmpty()) breaches++;
    if (SaveHelper.systemCount(s) > SaveHelper.SYSTEMS_MAX) pastLimit++;
    most = Math.max(most, SaveHelper.systemCount(s));
    SavedGameState fresh = Commission.build(id, "x", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(1));
    for (SystemType t : SystemType.values()) {
     SystemState was = fresh.getPlayerShip().getSystem(t), now = s.getSystem(t);
     int w = was == null ? 0 : was.getCapacity(), c = now == null ? 0 : now.getCapacity();
     if (w > 0 && c == 0) missing++; else if (w == 0 && c > 0) added++; else if (w != c) levels++;
     if (now != null && now.getCapacity() > 0 && !t.isSubsystem() && now.getPower() > now.getCapacity() - now.getDamagedBars()) odd++;
    }
   }
  }
  System.out.println("derelicts: " + n + " built; " + armed + " with a weapon or drone, " + missiles + " with missiles, " + parts + " with drone parts; systems missing " + missing + ", added " + added + ", re-levelled " + levels);
  Setup.chk("W: every derelict reads back byte for byte", n > 100 && roundTrip == n);
  Setup.chk("W: on the blank copy, no crew, hull 1 to half", wrecked == n);
  Setup.chk("W: no scrap or cargo; missiles only with a launcher (1-3, +1 a launcher more), drone parts only with drones or hacking; never past her slots", stripped == n);
  Setup.chk("W: weapons and drones only now and then (each of hers 1 in 12, another 1 in 12)", armed > 0 && armed * 2 < n);
  Setup.chk("W: oddities: systems missing, added and re-levelled; breaches", missing > 0 && added > 0 && levels > 0 && breaches == n);
  Setup.chk("W: no broken bar carries power", odd == 0);
  Setup.chk("W: never past FTL's System Limit (the most: " + most + ")", pastLimit == 0 && most <= SaveHelper.SYSTEMS_MAX);
  int patience = SaveHelper.systemCount(Derelict.build(new Random(1)).getPlayerShip()), nightjar = SaveHelper.systemCount(RepairJob.build(new Random(1)).getPlayerShip());
  Setup.chk("W: Patience (" + patience + ") and the Nightjar (" + nightjar + ") within it", patience <= SaveHelper.SYSTEMS_MAX && nightjar <= SaveHelper.SYSTEMS_MAX);
 }
 static void listings(Vault v) throws Exception {
  List<Derelicts.Listing> l = Derelicts.current(v);
  Setup.chk("L: three for sale", l.size() == 3);
  String first = l.get(0).save.getPlayerShipName();
  int wait = Derelicts.beaconsToNext(v);
  Setup.chk("L: looking again brings the same ones; new ones in 15-45 beacons, a multiple of 5 (" + wait + ")", Derelicts.current(v).get(0).save.getPlayerShipName().equals(first)
    && wait >= 15 && wait <= 45 && wait % 5 == 0);
  ChainT.jump(v, wait - 1);
  Setup.chk("L: a beacon short: still the same", Derelicts.current(v).get(0).save.getPlayerShipName().equals(first) && Derelicts.beaconsToNext(v) == 1);
  ChainT.jump(v, 1);
  List<Derelicts.Listing> again = Derelicts.current(v);
  Setup.chk("L: then new ones", again.size() == 3 && Derelicts.beaconsToNext(v) >= 15 && !again.get(0).save.getPlayerShipName().equals(first));
  java.util.Set<Integer> waits = new java.util.TreeSet<Integer>(); Random wr = new Random(3);
  for (int i = 0; i < 500; i++) { java.lang.reflect.Method m = Derelicts.class.getDeclaredMethod("interval", Random.class); m.setAccessible(true); waits.add((Integer) m.invoke(null, wr)); }
  Setup.chk("L: the waits run 15, 20 ... 45 " + waits, waits.equals(new java.util.TreeSet<Integer>(Arrays.asList(15, 20, 25, 30, 35, 40, 45))));
  for (Derelicts.Listing x : again) {
   int base = Pricing.auctionBase(x.save);
   Setup.chk("L: priced at 25-75% of her value as she is (at the rate), missing systems or not (" + x.price + " of " + base + ")", x.price >= Math.max(10, base / 4) - 1 && x.price <= Math.max(10, base * 3 / 4) + 1);
  }
  // the list keeps each one's share, not a price, so a change to the prices reaches listings already in; one from before shares keeps its price
  File idx = new File(Derelicts.dir(v), "listings.txt"); byte[] kept = SafeFiles.read(idx);
  Properties lp = new Properties(); lp.load(new ByteArrayInputStream(kept));
  int i0 = again.get(0).index;
  Setup.chk("L: the list keeps her share of the price, not a price", lp.getProperty(i0 + ".percent") != null && lp.getProperty(i0 + ".price") == null);
  lp.remove(i0 + ".percent"); lp.setProperty(i0 + ".price", "7");
  ByteArrayOutputStream lb = new ByteArrayOutputStream(); lp.store(lb, null); SafeFiles.write(idx, lb.toByteArray());
  int oldPrice = -1; for (Derelicts.Listing x : Derelicts.current(v)) if (x.index == i0) oldPrice = x.price;
  Setup.chk("L: a listing from before shares keeps the price it had (" + oldPrice + ")", oldPrice == 7);
  SafeFiles.write(idx, kept);
  Derelicts.Listing pick = again.get(1);
  int scrap = v.storageScrap();
  if (scrap < pick.price) v.depositToStorage(pick.price - scrap);
  int before = v.storageScrap(), junk = v.junked().size();
  Ship s = Derelicts.buy(v, pick);
  Setup.chk("L: bought: paid from the Cargo Hold, she's in the Junkyard", v.storageScrap() == before - pick.price && v.junked().size() == junk + 1 && s.state == Ship.State.JUNKED);
  Setup.chk("L: and gone from the listings", Derelicts.current(v).size() == 2);
  Setup.chk("L: she counts as set out at The Home Planet Station (she may be traded or scrapped without a store)", v.stillAtHomePlanet(s) && v.mayTrade(s));
  boolean refused = false; try { Derelicts.buy(v, pick); } catch (IOException e) { refused = true; }
  Setup.chk("L: she can't be bought twice", refused);
  Derelicts.Listing other = Derelicts.current(v).get(0);
  v.payFromStorage(v.storageScrap());
  refused = false; try { Derelicts.buy(v, other); } catch (IOException e) { refused = e.getMessage().contains("Cargo Hold"); }
  Setup.chk("L: the hold short: refused, nothing taken", refused && Derelicts.current(v).size() == 2);
  // locked models: 1 in 30 a listing
  Unlocks u = Unlocks.read(); int locked = 0, tries = 3000; Random rng = new Random(4);
  for (int i = 0; i < tries; i++) { String id = Derelicts.pickModel(rng, u, rng.nextInt(Derelicts.LOCKED_ONE_IN) == 0); if (!u.unlockedBlueprint(id)) locked++; }
  System.out.println("locked models: " + locked + " in " + tries);
  Setup.chk("L: a locked model about one listing in 30", u.problem() != null || (locked > tries / 60 && locked < tries / 15));
 }
 /** A strange rebuild: written as her own blueprint only when she's bought, and FTL's data reads her. */
 static void rebuilds(Vault v) throws Exception {
  Random rng = new Random(21);
  int swaps = 0, welds = 0, good = 0;
  for (int i = 0; i < 40 && (swaps < 2 || welds < 2); i++) {
   List<String> ids = new ArrayList<String>();
   for (String b : DataManager.get().getPlayerShipBaseIds(true)) if (CompanionMod.fileOf(b) != null) ids.add(b);
   SavedGameState gs = Derelicts.build(ids.get(rng.nextInt(ids.size())), "Odd " + i, rng);
   String odd = Derelicts.oddity(gs, rng);
   if (odd.isEmpty()) continue;
   if (odd.startsWith("swap:") ? swaps >= 2 : welds >= 2) continue;
   int remodelsBefore = CompanionMod.load().size();
   File f = new File(Derelicts.dir(v), "listing-0.sav"); SafeFiles.write(f, SaveHelper.toBytes(gs));
   v.depositToStorage(50);
   Ship s = Derelicts.buy(v, newListing(gs, odd));
   List<CompanionMod.Remodel> all = CompanionMod.load();
   SavedGameState back = HomePlanet.savedGameParser.readSavedGame(v.fileOf(s));
   String bp = back.getPlayerShip().getShipBlueprintId();
   boolean ok = all.size() == remodelsBefore + 1 && CompanionMod.isRemodelId(bp) && DataManager.get().getShip(bp) != null
     && DataManager.get().getShipLayout(back.getPlayerShip().getShipLayoutId()) != null;
   if (odd.startsWith("door:")) ok &= DataManager.get().getShipLayout(back.getPlayerShip().getShipLayoutId()).getDoorMap().size()
     == back.getPlayerShip().getDoorMap().size();
   byte[] once = SafeFiles.read(v.fileOf(s)); File t = File.createTempFile("odd", ".sav"); SafeFiles.write(t, once);
   ok &= Arrays.equals(once, SaveHelper.toBytes(HomePlanet.savedGameParser.readSavedGame(t))); t.delete();
   if (ok) good++;
   if (odd.startsWith("swap:")) swaps++; else welds++;
   System.out.println("rebuild: " + odd + " -> " + bp + (ok ? "" : "  BAD"));
  }
  Setup.chk("R: swapped rooms and welded doors each become a blueprint of her own, read back whole", swaps == 2 && welds == 2 && good == 4);
  Setup.chk("R: the mod builds with them", buildMod());
 }
 static Derelicts.Listing newListing(SavedGameState gs, String odd) throws Exception {
  java.lang.reflect.Constructor<Derelicts.Listing> c = Derelicts.Listing.class.getDeclaredConstructor(int.class, SavedGameState.class, String.class, int.class, boolean.class);
  c.setAccessible(true);
  File idx = new File(Derelicts.dir(Vault.get()), "listings.txt");
  Properties p = new Properties(); p.load(new FileInputStream(idx)); p.setProperty("0.open", "true"); FileOutputStream o = new FileOutputStream(idx); p.store(o, ""); o.close();
  return c.newInstance(0, gs, odd, 10, false);
 }
 static boolean buildMod() { try { File f = File.createTempFile("mod", ".ftl"); CompanionMod.build(f, CompanionMod.load(), "test"); boolean ok = f.length() > 0; f.delete(); return ok; } catch (Exception e) { e.printStackTrace(); return false; } }
}
