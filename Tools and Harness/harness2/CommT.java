import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import net.blerf.ftl.xml.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Commissions every player ship variant, every remodel and every built design, and reads each save back. args: gamedir, world saves (from WorldT), work */
public class CommT { public static void main(String[] a) throws Exception {
 File work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(new File(a[0]), saves); v.takeStock();
 DefaultDataManager dm = (DefaultDataManager) DataManager.get();
 File dir = new File(work, "built"); dir.mkdirs(); int ok = 0, bad = 0;
 List<String> ids = new ArrayList<String>();
 for (String base : dm.getPlayerShipBaseIds(true)) for (int n = 0; n < 3; n++) { try { ShipBlueprint bp = dm.getPlayerShipVariant(base, n, true); if (bp != null) ids.add(bp.getId()); } catch (Exception e) {} }
 for (CompanionMod.Remodel r : CompanionMod.load()) ids.add(r.id);
 for (ShipDesign d : DesignExport.built()) ids.add(DesignExport.bpId(d));
 for (String id : ids) {
  try {
   SavedGameState g = Commission.build(id, "Test " + id, net.blerf.ftl.constants.Difficulty.NORMAL, new Random(id.hashCode()));
   File f = new File(dir, id + ".sav"); SaveHelper.writeSavedGame(f, g);
   SavedGameState b = HomePlanet.savedGameParser.readSavedGame(f); ShipState s = b.getPlayerShip();
   if (s.getCrewList().isEmpty() || s.getHullAmt() <= 0 || s.getRoomList().isEmpty()) throw new IllegalStateException("odd ship: crew " + s.getCrewList().size() + " hull " + s.getHullAmt() + " rooms " + s.getRoomList().size());
   ok++;
  } catch (Exception e) { bad++; System.out.println("FAIL  " + id + ": " + e); }
 }
 Setup.chk(ok + " ships commissioned and read back (" + bad + " failed)", bad == 0 && ok >= 28);
 setOut(v);
 Setup.done();
} /** A ship just commissioned counts as at The Home Planet Station (the station rule) until she leaves her first beacon. */
 static void setOut(Vault v) throws Exception {
  boolean was = HomePlanet.storeRequirement; HomePlanet.storeRequirement = true;
  SavedGameState g = Commission.build("PLAYER_SHIP_HARD", "Fresh Kestrel", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(11));
  Ship s = v.adopt(g); v.setOut(s, g, "Commissioned (test)");
  Setup.chk("O: a new ship may trade before her first jump, though no store is at her beacon", !SaveHelper.isAtStation(s.save()) && v.mayTrade(s) && v.stillAtHomePlanet(s));
  v.board(s);
  Setup.chk("O: still so once boarded", v.mayTrade(v.boarded()));
  v.reload(); v.takeStock();
  Setup.chk("O: and after the station reloads its records", v.mayTrade(v.boarded()));
  SavedGameState c = HomePlanet.savedGameParser.readSavedGame(v.continueFile()); c.setCurrentBeaconId(c.getCurrentBeaconId() + 1); c.setTotalBeaconsExplored(c.getTotalBeaconsExplored() + 1);
  SaveHelper.writeSavedGame(v.continueFile(), c); v.boarded().invalidate(); v.takeStock();
  Setup.chk("O: once she jumps, the station rule applies again", !v.mayTrade(v.boarded()));
  HomePlanet.storeRequirement = was;
 }
}
