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
 Setup.chk("A: no voyage log kept for her", Setup.voyageLog(im, st).isEmpty());
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
 // Plan G (5.55): what came into FTL's profile while she was boarded, noted as hers; taken out on request, a backup first
 File prof = new File(HomePlanet.save_location, "ae_prof.sav");
 net.blerf.ftl.model.Profile p0 = net.blerf.ftl.model.Profile.createEmptyProfile(); p0.setFileFormat(9);
 p0.getShipUnlockMap().put("PLAYER_SHIP_MANTIS", new net.blerf.ftl.model.ShipAvailability("PLAYER_SHIP_MANTIS", true, false)); // the career's own, from before
 writeProfile(prof, p0);
 UnlockGrants.turnedOn(Unlocks.read());
 String ach = DataManager_firstAchievement();
 p0.getShipUnlockMap().put("PLAYER_SHIP_CIRCLE", new net.blerf.ftl.model.ShipAvailability("PLAYER_SHIP_CIRCLE", true, false));
 p0.getAchievements().add(new net.blerf.ftl.model.AchievementRecord(ach, Difficulty.NORMAL));
 writeProfile(prof, p0);
 g = read(im.continueFile()); g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + 1);
 SaveHelper.writeSavedGame(im.continueFile(), g); im.observeBoarded();
 Set<String> hers = UnlockGrants.strangers();
 Setup.chk("G: a layout and an achievement earned while she was boarded are noted as hers, the career's own not " + hers,
   hers.contains("PLAYER_SHIP_CIRCLE 0") && hers.contains("ACH:" + ach) && !hers.contains("PLAYER_SHIP_MANTIS 0") && hers.size() == 2);
 File backup = UnlockGrants.removeFromProfile(hers);
 UnlockGrants.strangersAnswered(hers);
 Unlocks after = Unlocks.read();
 Setup.chk("G: Remove: exactly those out of FTL's profile, the career's own kept, a backup made first",
   !after.unlocked("PLAYER_SHIP_CIRCLE", 0) && !after.achievements().contains(ach) && after.unlocked("PLAYER_SHIP_MANTIS", 0) && backup.isFile());
 Setup.chk("G: answered: not asked again; earned again by the career, they'd count", UnlockGrants.strangers().isEmpty() && UnlockGrants.newAchievements(Unlocks.read()).isEmpty() && !new String(SafeFiles.read(new File(im.root, "unlock-grants.txt")), "UTF-8").contains("seen PLAYER_SHIP_CIRCLE 0"));

 // Plan H (5.55): a career ship FTL's New Game wrote over, out of battle: offered back
 im.sendToOtherFleet(im.boarded(), false);
 Ship lucky = im.adopt(Commission.build("PLAYER_SHIP_HARD", "Lucky Kestrel", Difficulty.NORMAL, new Random(5)));
 im.board(lucky); im.takeStock();
 g = read(im.continueFile()); g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + 3);
 SaveHelper.writeSavedGame(im.continueFile(), g); im.observeBoarded(); im.takeStock();
 int repBefore = Reputation.total(im), kb = im.beaconsSeen(), crewOn = aboard(im, "Lucky Kestrel");
 SaveHelper.writeSavedGame(im.continueFile(), Commission.build("PLAYER_SHIP_STEALTH", "Stray Stealth", Difficulty.NORMAL, new Random(6)));
 im.takeStock();
 Vault.Departed luckyBack = offered(im, lucky.id);
 Setup.chk("H: written over out of battle: offered back; the career ship written over earlier too", luckyBack != null && offered(im, ours.id) != null);
 Setup.chk("H: meanwhile she's lost, her crew with her", Reputation.total(im) < repBefore && aboard(im, "Lucky Kestrel") == 0 && crewOn > 0);
 Ship back = im.restoreBack(luckyBack);
 CrewRegister.shipBack(im, back);
 Setup.chk("H: Yes: she's boarded again, the New Game's ship gone to the Sandbox", im.boarded() != null && im.boarded().id.equals(lucky.id) && !im.boarded().stranger
   && "Lucky Kestrel".equals(read(im.continueFile()).getPlayerShipName()));
 boolean listed = false; for (Vault.Departed d : im.recoverable()) if (d.id.equals(lucky.id)) listed = true;
 Setup.chk("H: her loss undone: reputation as before, no longer lost, not offered again", Reputation.total(im) == repBefore && !listed && offered(im, lucky.id) == null);
 boolean lostLine = false; for (CrewRegister.Member m : CrewRegister.members(im)) for (CrewRegister.Event e : m.events) if (e.text.startsWith("Lost with the Lucky Kestrel")) lostLine = true;
 Setup.chk("H: her crew back aboard, the loss out of their careers", aboard(im, "Lucky Kestrel") == crewOn && !lostLine);
 Setup.chk("H: the clock untouched by it all", im.beaconsSeen() == kb);
 SaveHelper.writeSavedGame(im.continueFile(), Commission.build("PLAYER_SHIP_STEALTH", "Stray Stealth", Difficulty.NORMAL, new Random(7)));
 im.takeStock();
 im.declineBack(offered(im, lucky.id));
 listed = false; for (Vault.Departed d : im.recoverable()) if (d.id.equals(lucky.id) && d.fate == Vault.Fate.LOST) listed = true;
 Setup.chk("H: No: she stays lost, and isn't asked about again", offered(im, lucky.id) == null && listed);
 // the check, on the version that would be restored
 SavedGameState t = Commission.build("PLAYER_SHIP_HARD", "Test", Difficulty.NORMAL, new Random(8));
 ShipState enemy = Commission.build("PLAYER_SHIP_MANTIS", "Raider", Difficulty.NORMAL, new Random(9)).getPlayerShip(); enemy.setHostile(true);
 t.getPlayerShip().setHullAmt(4);
 Setup.chk("H: no enemy alongside: offered, whatever her hull", Vault.restorable(t));
 t.setNearbyShip(enemy); t.setSectorNumber(2); t.getPlayerShip().setHullAmt(12);
 Setup.chk("H: in a battle in sector 3 at hull 12: offered, back into that battle", Vault.restorable(t));
 t.setSectorNumber(7);
 Setup.chk("H: the same battle in sector 8: not offered", !Vault.restorable(t));
 t.setSectorNumber(2); t.getPlayerShip().setHullAmt(5);
 Setup.chk("H: in a battle at hull 5: not offered", !Vault.restorable(t));
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
 static void writeProfile(File f, net.blerf.ftl.model.Profile p) throws Exception { ByteArrayOutputStream o = new ByteArrayOutputStream(); new net.blerf.ftl.parser.ProfileParser().writeProfile(o, p); SafeFiles.write(f, o.toByteArray()); }
 static String DataManager_firstAchievement() { for (String id : net.blerf.ftl.parser.DataManager.get().getAchievements().keySet()) return id; return "ACH_SECTOR_5"; }
 static Vault.Departed offered(Vault v, String id) { for (Vault.Departed d : v.offeredBack()) if (d.id.equals(id)) return d; return null; }
 static int aboard(Vault v, String ship) { int n = 0; for (CrewRegister.Member m : CrewRegister.members(v)) if (m.status == CrewRegister.Status.PRESENT && m.where.equals("aboard the " + ship)) n++; return n; }
}
