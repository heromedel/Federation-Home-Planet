import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.SavedGameState; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Immersive Mode's own fleet: switching keeps both fleets whole, designs are shared, the player's rules come back. args: gamedir, world saves (from WorldT), work */
public class FleetT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 fleets(v);
 rules();
 Setup.done();
}
 static String continueName(Vault v) throws Exception { return HomePlanet.savedGameParser.readSavedGame(v.continueFile()).getPlayerShipName(); }
 static void fleets(Vault v) throws Exception {
  if (v.boarded() == null) v.board(v.docked().get(0));
  String normalBoarded = v.boarded().name; int normalShips = v.all().size();
  File designs = ShipDesign.file();
  Vault im = Vault.switchFleet(true);
  Setup.chk("V: the Immersive fleet is its own folder, and starts empty", im.immersive && im.root.getName().equals(Vault.IMMERSIVE_FOLDER) && im.shipyardEmpty() && !im.continueFile().exists());
  Setup.chk("V: designs are shared", ShipDesign.file().equals(designs) && im.artDir().getParentFile().equals(im.shared));
  Ship n = im.adopt(Commission.build("PLAYER_SHIP_STEALTH", "Immersive Stealth", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(5)));
  im.board(n);
  Vault back = Vault.switchFleet(false);
  Setup.chk("V: back in the normal fleet: its ship is boarded again, and all its ships are there", !back.immersive && normalBoarded.equals(continueName(back)) && back.boarded() != null && back.all().size() == normalShips);
  Setup.chk("V: the Immersive ship waits in her own fleet", new File(back.otherRoot(), "ships/" + n.id + ".sav").isFile());
  Setup.chk("V: the other fleet's ships count as flying their blueprints", back.otherFleetBlueprints().containsAll(nz(Retrofit.blueprintIds(new File(back.otherRoot(), "ships/" + n.id + ".sav")))));
  Vault again = Vault.switchFleet(true);
  Setup.chk("V: and in Immersive again, she's boarded again", "Immersive Stealth".equals(continueName(again)) && again.boarded() != null && again.all().size() >= 1);
  Setup.chk("V: the normal fleet's boarded ship was docked, not lost", new File(again.otherRoot(), "parked-boarded.txt").isFile());
  Vault.switchFleet(false);
 }
 static List<String> nz(List<String> l) { return l == null ? new ArrayList<String>() : l; }
 static void rules() {
  HomePlanet.immersiveMode = false;
  HomePlanet.storeRequirement = false; HomePlanet.commissionCosts = false; HomePlanet.commissionPercent = 50; HomePlanet.unlockFreeShips = false;
  HomePlanet.immersiveMode = true; HomePlanet.applyImmersive();
  Setup.chk("R: Immersive Mode sets its rules, the free ship per unlock too", HomePlanet.storeRequirement && HomePlanet.commissionCosts && HomePlanet.commissionPercent == 100 && HomePlanet.unlockFreeShips);
  HomePlanet.Rules own = HomePlanet.normalRules();
  Setup.chk("R: the player's own rules are kept apart", !own.store && !own.costs && own.percent == 50 && !own.unlockFree);
  HomePlanet.leaveImmersive();
  Setup.chk("R: and come back when it's turned off", !HomePlanet.immersiveMode && !HomePlanet.storeRequirement && !HomePlanet.commissionCosts && HomePlanet.commissionPercent == 50);
 }
}
