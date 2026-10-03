import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.model.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** House rules: Immersive Mode's settings, and the unlock-once free ships. args: gamedir, world saves (from WorldT), work */
public class RuleT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 immersive();
 sales(v);
 unlocks(saves);
 ranks(saves);
 Setup.done();
}
 static void immersive() {
  HomePlanet.storeRequirement = false; HomePlanet.commissionCosts = false; HomePlanet.commissionPercent = 50; HomePlanet.sellSupplies = false;
  HomePlanet.immersiveMode = false;
  Setup.chk("I: off, the rules stay as set", !HomePlanet.storeRequirement() && !HomePlanet.commissionCosts() && Economy.supplyPercent() == 50 && Economy.commissionPercent() == 50);
  HomePlanet.immersiveMode = true;
  Setup.chk("I: on, its rules are in force", HomePlanet.storeRequirement() && HomePlanet.journeyStoreRequirement() && HomePlanet.commissionCosts() && Economy.commissionPercent() == 100
    && HomePlanet.sellSupplies() && HomePlanet.sellSystems() && HomePlanet.unlockFreeShips() && HomePlanet.immersiveNotifications() && HomePlanet.commissionUnlockedOnly());
  Setup.chk("I: and the player's own settings are untouched", !HomePlanet.storeRequirement && !HomePlanet.commissionCosts && HomePlanet.commissionPercent == 50 && !HomePlanet.sellSupplies);
  Setup.chk("I: missiles and drone parts sell at 25%, stored systems still at half", Economy.supplyPercent() == 25 && Economy.supplySale(4, Pricing.MISSILE) == 6
    && Economy.SYSTEM_SALE_PERCENT == 50);
  HomePlanet.journeyFee = 1000; HomePlanet.removalFee = Economy.NOT_ALLOWED;
  Setup.chk("I: its own New Journey fee and Refit removal, whatever Sandbox Mode's are", Economy.journeyFee() == 200 && Economy.removalFee() == 0 && Economy.stripFee() == 0);
  HomePlanet.immersiveMode = false;
  Setup.chk("E: Sandbox Mode's fees: journey 1000, removal not allowed, stripping 10 a system", Economy.journeyFee() == 1000 && Economy.removalFee() == Economy.NOT_ALLOWED && Economy.stripFee() == 10);
  HomePlanet.removalFee = 25; boolean s25 = Economy.stripFee() == 10;
  HomePlanet.removalFee = 50; boolean s50 = Economy.stripFee() == 20;
  HomePlanet.removalFee = 0;
  Setup.chk("E: stripping is a discount on removal: 10 for 25, 20 for 50, free for free", s25 && s50 && Economy.stripFee() == 0);
  HomePlanet.journeyFee = 0;
 }
 /** Trade In and Auction prices, and a sold ship's fate. */
 static void sales(Vault v) throws Exception {
  SavedGameParser.SavedGameState g = Commission.build("PLAYER_SHIP_HARD", "For Sale", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(3));
  SavedGameParser.ShipState s = g.getPlayerShip();
  int crew = 0; for (SavedGameParser.CrewState c : SaveHelper.getOwnCrew(s)) crew += Pricing.crew(c.getRace().getId());
  int value = Pricing.saleValue(g);
  Setup.chk("S: her value is her price less her crew, with fuel, missiles and drone parts at store price",
    value == Pricing.ship(g, 100).subtotal - crew && Pricing.supplies(s) == s.getFuelAmt() * 3 + s.getMissilesAmt() * 6 + s.getDronePartsAmt() * 8
    && Pricing.ship(g, 100).lines.toString().contains("Fuel, missiles and drone parts: " + Pricing.supplies(s)) && crew > 0); // her price counts her supplies
  Setup.chk("S: whole hull: Trade In is half her value", Pricing.missingHull(s) == 0 && Pricing.tradeIn(g) == value / 2);
  s.setHullAmt(s.getHullAmt() - 6);
  Setup.chk("S: 6 hull missing: 30 scrap off", Pricing.missingHull(s) == 6 && Pricing.tradeIn(g) == value / 2 - 30 && Pricing.auctionBase(g) == value - 30);
  s.getSystem(SavedGameParser.SystemType.SHIELDS).setDamagedBars(2);
  Setup.chk("S: two broken system bars: 10 more off", Pricing.brokenBars(s) == 2 && Pricing.tradeIn(g) == value / 2 - 40 && Pricing.auctionBase(g) == value - 40);
  s.getSystem(SavedGameParser.SystemType.SHIELDS).setDamagedBars(0);
  s.setBreach(1, 1, 100);
  Setup.chk("S: a breach: 5 more off", Pricing.tradeIn(g) == value / 2 - 35 && Pricing.auctionBase(g) == value - 35);
  s.getBreachMap().clear();
  // a missing Engines, Piloting or Oxygen: 15 points off what each buyer pays
  int full = Pricing.tradeIn(g);
  SavedGameParser.SystemState pil = s.getSystem(SavedGameParser.SystemType.PILOT); int pilLevel = pil.getCapacity(); pil.setCapacity(0);
  int v1 = Pricing.saleValue(g), dmg1 = Pricing.damage(s);
  Setup.chk("S: no Piloting: Trade In pays 35%, bids run 10-60%", Pricing.missingCore(s).size() == 1 && Pricing.tradeIn(g) == Math.max(0, v1 * 35 / 100 - dmg1)
    && Arrays.equals(Pricing.auctionRange(s), new int[] {10, 60}));
  SavedGameParser.SystemState eng = s.getSystem(SavedGameParser.SystemType.ENGINES); int engLevel = eng.getCapacity(); eng.setCapacity(0);
  SavedGameParser.SystemState oxy = s.getSystem(SavedGameParser.SystemType.OXYGEN); int oxyLevel = oxy.getCapacity(); oxy.setCapacity(0);
  int v3 = Pricing.saleValue(g), dmg3 = Pricing.damage(s);
  Setup.chk("S: all three missing: Trade In 5%, bids 5-30% (the low end at its floor)", Pricing.missingCore(s).size() == 3 && Pricing.tradeIn(g) == Math.max(0, v3 * 5 / 100 - dmg3)
    && Arrays.equals(Pricing.auctionRange(s), new int[] {5, 30}));
  pil.setCapacity(pilLevel); eng.setCapacity(engLevel); oxy.setCapacity(oxyLevel);
  Setup.chk("S: put back, the prices are whole again", Pricing.missingCore(s).isEmpty() && Pricing.tradeIn(g) == full && Arrays.equals(Pricing.auctionRange(s), new int[] {25, 75}));
  int lo = Integer.MAX_VALUE, hi = 0; boolean same = true;
  for (long seed = 0; seed < 400; seed++) { int b = Pricing.auction(g, seed); lo = Math.min(lo, b); hi = Math.max(hi, b); same &= b == Pricing.auction(g, seed); }
  int base = Pricing.auctionBase(g);
  Setup.chk("S: auction bids run from 25% to 75% of that, the same bid for the same save", lo >= base / 4 && hi <= base * 3 / 4 && lo < base * 30 / 100 && hi > base * 70 / 100 && same);
  s.setHullAmt(1);
  Setup.chk("S: never below nothing", Pricing.tradeIn(g) >= 0 && Pricing.auctionBase(g) >= 0);
  Ship sold = v.adopt(g);
  v.remove(sold, "SELL", Vault.Fate.SOLD);
  boolean back = false; for (Vault.Departed d : v.recoverable()) back |= d.name.equals("For Sale");
  Setup.chk("S: a sold ship leaves the fleet and can't be recovered", v.byId(sold.id) == null && !back);
 }
 static void profile(File saves, String... unlockedA) throws Exception {
  Profile p = Profile.createEmptyProfile();
  Map<String, ShipAvailability> m = new LinkedHashMap<String, ShipAvailability>();
  for (String base : DataManager.get().getPlayerShipBaseIds(true)) m.put(base, new ShipAvailability(base, false, false));
  for (String base : unlockedA) m.put(base, new ShipAvailability(base, true, false));
  p.setShipUnlockMap(m);
  OutputStream out = new FileOutputStream(new File(saves, "ae_prof.sav")); new ProfileParser().writeProfile(out, p); out.close();
 }
 static void unlocks(File saves) throws Exception {
  Setup.chk("U: no profile, no free ships", !UnlockGrants.freeNow(Unlocks.read(), "PLAYER_SHIP_MANTIS"));
  Unlocks none = Unlocks.read();
  Setup.chk("U: no profile yet is FTL's fresh one: only the Kestrel A unlocked", none.missing() && none.problem() == null && none.unlocked("PLAYER_SHIP_HARD", 0)
    && !none.unlocked("PLAYER_SHIP_HARD", 1) && !none.unlocked("PLAYER_SHIP_STEALTH", 0) && !none.unlocked("PLAYER_SHIP_FED", 2));
  Setup.chk("U: but its victories are unknown, not zero (a final victory is judged by them)", none.victories() == -1);
  new File(saves, "ae_prof.sav").delete();
  profile(saves, "PLAYER_SHIP_HARD", "PLAYER_SHIP_STEALTH");
  Unlocks u = Unlocks.read();
  Setup.chk("U: the test profile reads", u.problem() == null && u.unlocked("PLAYER_SHIP_STEALTH", 0) && !u.unlocked("PLAYER_SHIP_MANTIS", 0));
  UnlockGrants.turnedOn(u);
  Setup.chk("U: ships unlocked before the rule was on don't count", !UnlockGrants.freeNow(u, "PLAYER_SHIP_STEALTH") && !UnlockGrants.freeNow(u, "PLAYER_SHIP_HARD"));
  profile(saves, "PLAYER_SHIP_HARD", "PLAYER_SHIP_STEALTH", "PLAYER_SHIP_MANTIS");
  u = Unlocks.read();
  Setup.chk("U: a ship unlocked since is free", UnlockGrants.freeNow(u, "PLAYER_SHIP_MANTIS"));
  Setup.chk("U: its other layouts aren't (still locked)", !UnlockGrants.freeNow(u, "PLAYER_SHIP_MANTIS_3"));
  UnlockGrants.claim("PLAYER_SHIP_MANTIS");
  Setup.chk("U: once claimed, it isn't free again", !UnlockGrants.freeNow(u, "PLAYER_SHIP_MANTIS"));
  // the rule is turned off, an unlock happens, and it's turned back on: that unlock never counts
  profile(saves, "PLAYER_SHIP_HARD", "PLAYER_SHIP_STEALTH", "PLAYER_SHIP_MANTIS", "PLAYER_SHIP_CIRCLE");
  UnlockGrants.turnedOn(Unlocks.read());
  Setup.chk("U: an unlock while the rule was off never counts", !UnlockGrants.freeNow(Unlocks.read(), "PLAYER_SHIP_CIRCLE"));
  Setup.chk("U: and the claim is kept", !UnlockGrants.freeNow(Unlocks.read(), "PLAYER_SHIP_MANTIS"));
  Vault.get().surrender();
  profile(saves, "PLAYER_SHIP_HARD", "PLAYER_SHIP_STEALTH", "PLAYER_SHIP_MANTIS", "PLAYER_SHIP_CIRCLE", "PLAYER_SHIP_FED");
  Setup.chk("U: a report for reassignment doesn't reset it; later unlocks still count", !UnlockGrants.freeNow(Unlocks.read(), "PLAYER_SHIP_MANTIS") && UnlockGrants.freeNow(Unlocks.read(), "PLAYER_SHIP_FED"));
 }
 static void ranks(File saves) throws Exception {
  SafeFiles.deleteTree(new File(Vault.get().root, "unlock-grants.txt"));
  new File(Vault.get().root, "unlock-grants.txt").delete();
  profile(saves, "PLAYER_SHIP_HARD");
  Setup.chk("K: a new career starts as Commander", UnlockGrants.rank(Unlocks.read()) == 0 && "Commander".equals(UnlockGrants.rankName(0)));
  // the Type C first: still one step
  Profile p = Profile.createEmptyProfile(); p.setFileFormat(9); // Advanced Edition: it keeps the C layouts
  Map<String, ShipAvailability> m = new LinkedHashMap<String, ShipAvailability>();
  for (String base : DataManager.get().getPlayerShipBaseIds(true)) m.put(base, new ShipAvailability(base, false, false));
  m.put("PLAYER_SHIP_HARD", new ShipAvailability("PLAYER_SHIP_HARD", true, false));
  m.put("PLAYER_SHIP_FED", new ShipAvailability("PLAYER_SHIP_FED", false, true));
  p.setShipUnlockMap(m);
  OutputStream out = new FileOutputStream(new File(saves, "ae_prof.sav")); new ProfileParser().writeProfile(out, p); out.close();
  Setup.chk("K: one promotion, whichever Federation cruiser came first", UnlockGrants.rank(Unlocks.read()) == 1);
  m.put("PLAYER_SHIP_FED", new ShipAvailability("PLAYER_SHIP_FED", true, true));
  out = new FileOutputStream(new File(saves, "ae_prof.sav")); new ProfileParser().writeProfile(out, p); out.close();
  Setup.chk("K: both: Commodore", UnlockGrants.rank(Unlocks.read()) == 2 && "Commodore".equals(UnlockGrants.rankName(2)));
  profile(saves, "PLAYER_SHIP_HARD");
  Setup.chk("K: promotions stay whatever the profile does later", UnlockGrants.rank(Unlocks.read()) == 2);
  new File(Vault.get().root, "unlock-grants.txt").delete();
  profile(saves, "PLAYER_SHIP_HARD", "PLAYER_SHIP_FED");
  Setup.chk("K: a Federation cruiser unlocked before the record began earns nothing", UnlockGrants.rank(Unlocks.read()) == 0);
 }
}
