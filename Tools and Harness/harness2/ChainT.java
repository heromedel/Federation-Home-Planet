import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Reply chains (the One Point of Hull): the trigger, replies counted in beacons, the paid claim, the derelict. args: gamedir, world saves (from WorldT), work */
public class ChainT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); saves.mkdirs();
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 HomePlanet.commissionCosts = true; HomePlanet.immersiveNotifications = true;
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 derelictShip();

 Ship k = v.adopt(Commission.build("PLAYER_SHIP_HARD", "Battered", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(5)));
 v.board(k); v.takeStock(); Transmissions.check();
 Setup.chk("C: no letter before the hull drops", find("chain:one-hull") == null);
 SavedGameParser.SavedGameState g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
 g.getPlayerShip().setHullAmt(1);
 g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + 1);
 SaveHelper.writeSavedGame(v.continueFile(), g);
 v.takeStock(); Transmissions.check();
 Transmissions.Message first = find("chain:one-hull");
 Setup.chk("C: one point of hull, the fight over: the Collective writes, naming her", first != null && first.body.contains("the Battered returned"));
 Setup.chk("C: it offers two replies", Transmissions.canReply(first) && Transmissions.replyTexts(first).size() == 2);
 Transmissions.check(); Transmissions.check();
 int n = 0; for (Transmissions.Message m : Transmissions.load()) if (m.key.equals("chain:one-hull")) n++;
 Setup.chk("C: the letter comes once", n == 1);

 Transmissions.reply(first, 0);
 Setup.chk("C: the reply is recorded and can't be sent twice", !Transmissions.canReply(find("chain:one-hull")) && "Tell me about these derelict vessels.".equals(find("chain:one-hull").replied));
 jump(v, 4); Transmissions.check();
 Setup.chk("C: 4 beacons on: no answer yet (5-10)", find("chain:one-hull:derelict") == null);
 jump(v, 6); Transmissions.check();
 Setup.chk("C: 10 beacons on: A vessel in need", find("chain:one-hull:derelict") != null);
 Setup.chk("C: no derelict yet", v.junked().isEmpty());
 jump(v, 4); Transmissions.check();
 Setup.chk("C: 4 beacons later: not delivered yet (5-7)", find("chain:one-hull:delivered") == null && v.junked().isEmpty());
 jump(v, 3); Transmissions.check();
 Setup.chk("C: 7 beacons later: Delivered, and Patience in the Junkyard", find("chain:one-hull:delivered") != null && v.junked().size() == 1 && Derelict.NAME.equals(v.junked().get(0).name));
 Transmissions.check(); Transmissions.check();
 Setup.chk("C: delivered once", v.junked().size() == 1);
 Setup.chk("C: the other reply's letter never came", find("chain:one-hull:tool") == null);

 Ship p = v.junked().get(0);
 SavedGameParser.SavedGameState pg = HomePlanet.savedGameParser.readSavedGame(v.fileOf(p));
 Setup.chk("C: her save reads back: no crew, hull 6", pg.getPlayerShip().getCrewList().isEmpty() && pg.getPlayerShip().getHullAmt() == 6);
 v.salvage(p);
 Setup.chk("C: salvaged, she's docked and may take on crew (set out at The Home Planet Station)", p.state == Ship.State.DOCKED && v.mayTrade(p));

 // with no one aboard she may be boarded, but FTL isn't launched
 v.board(p);
 Setup.chk("C: boarded with no one aboard, FTL won't launch", HomePlanet.noOneAboard(v.continueFile()) != null);
 v.dock(); v.board(k);
 Setup.chk("C: a ship with crew launches as ever", HomePlanet.noOneAboard(v.continueFile()) == null);

 // FTL closed and continue.sav gone: she's lost; a different ship in it afterwards is uncommissioned (no take-backs)
 v.takeStock();
 Ship was = v.boarded();
 v.continueFile().delete();
 v.reload();
 Setup.chk("G: FTL closed, continue.sav gone: recorded lost", v.boarded() == null && new File(new File(v.historyDir(), was.id), "fate.txt").exists());
 SavedGameParser.SavedGameState other = Commission.build("PLAYER_SHIP_STEALTH", "Somebody Else", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(9));
 SaveHelper.writeSavedGame(v.continueFile(), other);
 v.reload(); v.takeStock();
 Setup.chk("G: another ship in continue.sav afterwards is an uncommissioned stranger", v.boarded() != null && v.boarded().stranger && !v.boarded().id.equals(was.id));

 // the paid claim, on the other branch (a fresh fleet's own letters)
 paidClaim(game, new File(work, "tool"));
 Setup.done();
}
 static Transmissions.Message find(String key) { for (Transmissions.Message m : Transmissions.load()) if (m.key.equals(key)) return m; return null; }
 /** The boarded ship jumps n beacons (FTL's count goes up), and the station looks. */
 static void jump(Vault v, int n) throws Exception {
  SavedGameParser.SavedGameState g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
  g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + n);
  g.getPlayerShip().setHullAmt(10);
  SaveHelper.writeSavedGame(v.continueFile(), g);
  v.takeStock();
 }
 /** The derelict as the letter describes her, and a save FTL can read back the same. */
 static void derelictShip() throws Exception {
  SavedGameParser.SavedGameState g = Derelict.build(new Random(1));
  SavedGameParser.ShipState s = g.getPlayerShip();
  Setup.chk("D: a Slug Cruiser A on the station's blank copy", (Derelict.BLUEPRINT + Retrofit.SUFFIX).equals(s.getShipBlueprintId()));
  SavedGameParser.SystemState med = s.getSystem(SavedGameParser.SystemType.MEDBAY), hack = s.getSystem(SavedGameParser.SystemType.HACKING);
  SavedGameParser.SystemState oxy = s.getSystem(SavedGameParser.SystemType.OXYGEN), pil = s.getSystem(SavedGameParser.SystemType.PILOT), eng = s.getSystem(SavedGameParser.SystemType.ENGINES);
  Setup.chk("D: medbay out, hacking in and broken, oxygen broken", (med == null || med.getCapacity() == 0) && hack != null && hack.getCapacity() == 1 && hack.getDamagedBars() == 1 && oxy.getDamagedBars() == 1);
  Setup.chk("D: piloting 2, engines 1", pil.getCapacity() == 2 && eng.getCapacity() == 1);
  Setup.chk("D: one Mini Beam, no crew, hull 6, two breaches", s.getWeaponList().size() == 1 && "BEAM_1".equals(s.getWeaponList().get(0).getWeaponId())
    && s.getCrewList().isEmpty() && s.getHullAmt() == 6 && s.getBreachMap().size() == 2);
  byte[] once = SaveHelper.toBytes(g);
  File f = File.createTempFile("derelict", ".sav"); SafeFiles.write(f, once);
  byte[] twice = SaveHelper.toBytes(HomePlanet.savedGameParser.readSavedGame(f)); f.delete();
  Setup.chk("D: her save round-trips byte for byte", Arrays.equals(once, twice));
 }
 static void paidClaim(File game, File work) throws Exception {
  File saves = new File(work, "saves"); saves.mkdirs();
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  Ship k = v.adopt(Commission.build("PLAYER_SHIP_HARD", "Scraped", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(6)));
  v.board(k); v.takeStock();
  SavedGameParser.SavedGameState g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
  g.getPlayerShip().setHullAmt(1); SaveHelper.writeSavedGame(v.continueFile(), g);
  v.takeStock(); Transmissions.check();
  Transmissions.reply(find("chain:one-hull"), 1);
  jump(v, 2); Transmissions.check();
  Setup.chk("T: 2 beacons on: no tool yet (3-5)", find("chain:one-hull:tool") == null);
  jump(v, 3); Transmissions.check();
  Transmissions.Message tool = find("chain:one-hull:tool");
  Setup.chk("T: 5 beacons on: the tool, for 25 scrap", tool != null && Transmissions.price(tool) == 25 && tool.hasReward());
  int scrap = v.storageScrap();
  if (scrap > 10) v.payFromStorage(scrap - 10); else if (scrap < 10) v.depositToStorage(10 - scrap); // 10 in the hold: short of 25
  boolean refused = false; try { Transmissions.claim(tool, -1); } catch (IOException e) { refused = e.getMessage().contains("25 scrap"); }
  Setup.chk("T: the hold short: refused, nothing taken", refused && v.storageScrap() == 10 && !find("chain:one-hull:tool").claimed);
  v.depositToStorage(30);
  Transmissions.claim(find("chain:one-hull:tool"), -1);
  Transmissions.Message after = find("chain:one-hull:tool");
  Setup.chk("T: paid: 25 scrap taken, the Repair Arm claimed", v.storageScrap() == 15 && after.claimed && after.claimedWhat.contains("25 scrap paid"));
 }
}
