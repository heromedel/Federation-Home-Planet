import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import net.blerf.ftl.constants.Difficulty; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** A ship the station didn't commission is ignored in Immersive Mode (heromedel, 5.54): FTL's New Game counts for nothing until the player decides; Sandbox Mode takes any ship. args: gamedir, world saves, work */
public class StrT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive(); HomePlanet.expeditionType = 2;
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 HomePlanet.immersiveMode = true;
 Vault im = Vault.switchFleet(true);
 if (im.boarded() != null) im.dock();
 im.storage();
 Ship ours = im.adopt(Commission.build("PLAYER_SHIP_HARD", "Career Kestrel", Difficulty.NORMAL, new Random(1)));
 im.board(ours); im.takeStock();
 // the control: FTL flies the career's own ship, and her beacons count
 SavedGameState g = read(im.continueFile()); g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + 2);
 SaveHelper.writeSavedGame(im.continueFile(), g); im.observeBoarded(); im.takeStock();
 int b0 = im.beaconsSeen();
 Setup.chk("C: the career's own ship: her beacons count", b0 >= 2);
 // FTL's New Game over her, part-way into a run
 SavedGameState ng = Commission.build("PLAYER_SHIP_CIRCLE", "Stray Engi", Difficulty.NORMAL, new Random(2));
 ng.setTotalBeaconsExplored(9); ng.setSectorNumber(2); ng.setTotalShipsDefeated(4); ng.setTotalScrapCollected(120);
 SaveHelper.writeSavedGame(im.continueFile(), ng); im.takeStock();
 Ship st = im.boarded();
 Setup.chk("A: FTL's New Game: an uncommissioned ship, ignored", st != null && st != ours && st.stranger && im.ignoring(st));
 Setup.chk("A: none of her run so far on the career's clock", im.beaconsSeen() == b0);
 int rep = Reputation.total(im);
 // she flies on: FTL's saves, and the station's looks
 g = read(im.continueFile()); g.setTotalBeaconsExplored(14); g.setSectorNumber(4); g.setTotalShipsDefeated(9); g.setTotalScrapCollected(300);
 SaveHelper.writeSavedGame(im.continueFile(), g); im.observeBoarded(); im.takeStock();
 Setup.chk("A: as she flies: no beacons, no days", im.beaconsSeen() == b0);
 Setup.chk("A: no reputation for her sectors, kills or scrap", Reputation.total(im) == rep);
 File vl = new File(im.historyOf(st), "voyage.log");
 Setup.chk("A: no voyage log kept for her", !vl.isFile() || vl.length() == 0);
 boolean signed = false; for (CrewRegister.Member m : CrewRegister.members(im)) if (m.where.contains("Stray Engi")) signed = true;
 Setup.chk("A: her crew aren't signed on", !signed);
 Setup.chk("A: no final-battle copy kept for her", !im.watchContinue(0, (n, b) -> 0));
 // sent to the Sandbox: gone from the career, which never counted her
 im.sendToOtherFleet(st, false);
 Setup.chk("A: sent to the Sandbox Space Dock: gone from this fleet", im.boarded() == null && !im.continueFile().exists() && im.beaconsSeen() == b0);
 // nothing boarded, and FTL starts a New Game: noticed on the next look, not only on opening the fleet
 SaveHelper.writeSavedGame(im.continueFile(), Commission.build("PLAYER_SHIP_ROCK", "Stray Rock", Difficulty.NORMAL, new Random(3)));
 im.takeStock();
 Setup.chk("B: nothing boarded, a New Game: an uncommissioned ship on the next look, ignored", im.boarded() != null && im.boarded().stranger && im.ignoring(im.boarded()) && "Stray Rock".equals(im.boarded().name));
 g = read(im.continueFile()); g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + 5);
 SaveHelper.writeSavedGame(im.continueFile(), g); im.observeBoarded(); im.takeStock();
 Setup.chk("B: and her flying counts for nothing", im.beaconsSeen() == b0);
 // Sandbox Mode: any ship is the fleet's, as before
 im.sendToOtherFleet(im.boarded(), false);
 HomePlanet.leaveImmersive();
 Vault sb = Vault.switchFleet(false);
 if (sb.boarded() != null) sb.dock();
 SaveHelper.writeSavedGame(sb.continueFile(), Commission.build("PLAYER_SHIP_MANTIS", "Stray Mantis", Difficulty.NORMAL, new Random(4)));
 sb.takeStock();
 int s0 = sb.beaconsSeen();
 g = read(sb.continueFile()); g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + 3);
 SaveHelper.writeSavedGame(sb.continueFile(), g); sb.observeBoarded(); sb.takeStock();
 Setup.chk("S: Sandbox Mode: a New Game's ship is the fleet's, and her beacons count", sb.boarded() != null && sb.boarded().stranger && !sb.ignoring(sb.boarded()) && sb.beaconsSeen() == s0 + 3);
 Setup.done();
}
 static SavedGameState read(File f) throws Exception { return HomePlanet.savedGameParser.readSavedGame(f); }
}
