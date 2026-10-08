import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/**
 * The Space Dock's business, written (heromedel's Plan O, 6.11: moved out of ui/SpaceDockUI into vault/SpaceDock): Rename,
 * New Journey with its fee and her save as one, Scrap with her systems stripped, Sell. args: gamedir, world saves, work
 */
public class SpaceT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();

 // Rename
 Ship s = v.docked().get(0); String sId = s.id, was = s.name;
 SpaceDock.rename(v, s, v.readCopy(s).save, "Lucky Duck");
 v.takeStock();
 Setup.chk("R: renamed: her save has the new name, logged", "Lucky Duck".equals(v.byId(sId).save().getPlayerShipName()) && kinds(v).contains("RENAME") && log(v).contains(was));

 // New Journey: the fee from the Cargo Hold and her save together
 if (v.boarded() == null) v.board(v.docked().get(0));
 Ship b = v.boarded();
 v.depositToStorage(50);
 int hold0 = v.storageScrap();
 SavedGameState gs = v.readCopy(b).save;
 SaveHelper.startJourney(gs, net.blerf.ftl.constants.Difficulty.EASY);
 String bHash = SafeFiles.hash(v.fileOf(b));
 boolean refused = false;
 try { SpaceDock.newJourney(v, b, gs, "Easy", hold0 + 1, hold0 + 1, 0, (hold0 + 1) + " scrap"); } catch (IOException e) { refused = true; }
 v = Vault.open(saves); v.takeStock(); b = v.boarded();
 Setup.chk("J: a fee the Cargo Hold can't pay: refused, nothing changed (her save, the hold)", refused && v.storageScrap() == hold0 && bHash.equals(SafeFiles.hash(v.fileOf(b))) && !kinds(v).contains("NEW_JOURNEY"));
 gs = v.readCopy(b).save; SaveHelper.startJourney(gs, net.blerf.ftl.constants.Difficulty.EASY);
 SpaceDock.newJourney(v, b, gs, "Easy", 20, 20, 0, "20 scrap");
 Setup.chk("J: plotted: 20 scrap from the Cargo Hold, her save set out, logged", v.storageScrap() == hold0 - 20 && !bHash.equals(SafeFiles.hash(v.fileOf(v.boarded()))) && kinds(v).contains("NEW_JOURNEY"));

 // Scrap, her systems stripped: everything aboard to the Cargo Hold, her systems to the stored systems, the hull gone
 Ship w = v.adoptJunked(Commission.build("PLAYER_SHIP_HARD", "Old Hulk", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(3)));
 v.takeStock();
 String wId = w.id; int wCrew = SaveHelper.getOwnCrew(w.save().getPlayerShip()).size(), wScrap = w.save().getPlayerShip().getScrapAmt();
 int crew0 = SaveHelper.getOwnCrew(v.storage().save().getPlayerShip()).size(), scrap0 = v.storageScrap(), stored0 = StoredSystems.read(v).size();
 SpaceDock.scrap(v, w, w.save(), Arrays.asList("shields 2"), Arrays.asList("+ Shields (level 2) (system)"), 5, 0);
 v.takeStock();
 int crew1 = SaveHelper.getOwnCrew(v.storage().save().getPlayerShip()).size();
 List<StoredSystems.Entry> st = StoredSystems.read(v);
 Setup.chk("S: scrapped: her crew (" + wCrew + ") and scrap in the Cargo Hold, less the stripping", crew1 == crew0 + wCrew && v.storageScrap() == scrap0 + wScrap - 5);
 Setup.chk("S: her stripped system stored", st.size() == stored0 + 1 && st.get(st.size() - 1).id.equals("shields") && st.get(st.size() - 1).level == 2);
 Setup.chk("S: the hull gone, logged with what came off her", v.byId(wId) == null && kinds(v).contains("SCRAP") && log(v).contains("Shields (level 2)"));

 // Sell (trade in): her scrap, her crew and the price to the Cargo Hold; she leaves the fleet, sold
 Ship x = v.adoptJunked(Commission.build("PLAYER_SHIP_HARD", "For Sale", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(4)));
 v.takeStock();
 String xId = x.id; int xCrew = SaveHelper.getOwnCrew(x.save().getPlayerShip()).size(), xScrap = x.save().getPlayerShip().getScrapAmt();
 crew0 = crew1; scrap0 = v.storageScrap();
 SpaceDock.sell(v, x, x.save(), 100, false);
 v.takeStock();
 String[] fate = ShipStore.fate(v.folderOfId(xId));
 Setup.chk("T: sold: her scrap, crew and the price in the Cargo Hold", SaveHelper.getOwnCrew(v.storage().save().getPlayerShip()).size() == crew0 + xCrew && v.storageScrap() == scrap0 + xScrap + 100);
 Setup.chk("T: she left the fleet, sold, logged", v.byId(xId) == null && fate != null && "SOLD".equals(fate[0]) && kinds(v).contains("SELL"));
 Setup.done();
}
 static String log(Vault v) throws IOException { File f = new File(v.root, "logs/events.log"); return f.isFile() ? new String(SafeFiles.read(f), "UTF-8") : ""; }
 /** The kinds of event in the station's log. */
 static Set<String> kinds(Vault v) throws IOException { Set<String> out = new HashSet<String>(); for (String l : log(v).split("\n")) { String[] p = l.split(" \\| "); if (p.length > 2) out.add(p[2].trim()); } return out; }
}
