import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.model.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** House rules: Immersive Mode's settings, and the unlock-once free ships. args: gamedir, world saves (from WorldT), work */
public class RuleT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 immersive();
 unlocks(saves);
 ranks(saves);
 Setup.done();
}
 static void immersive() {
  HomePlanet.storeRequirement = false; HomePlanet.commissionCosts = false; HomePlanet.commissionPercent = 50; HomePlanet.sellSupplies = false;
  HomePlanet.immersiveMode = false; HomePlanet.applyImmersive();
  Setup.chk("I: off, the rules stay as set", !HomePlanet.storeRequirement && !HomePlanet.commissionCosts && HomePlanet.sellPercent() == 50);
  HomePlanet.immersiveMode = true; HomePlanet.applyImmersive();
  Setup.chk("I: on, it sets its rules", HomePlanet.storeRequirement && HomePlanet.journeyStoreRequirement && HomePlanet.commissionCosts && HomePlanet.commissionPercent == 100 && HomePlanet.sellSupplies && HomePlanet.sellSystems);
  Setup.chk("I: selling pays 25%", HomePlanet.sellPercent() == 25 && Pricing.systemSale("shields", 2, HomePlanet.sellPercent()) == Pricing.system("shields", 2) / 4);
  HomePlanet.immersiveMode = false;
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
