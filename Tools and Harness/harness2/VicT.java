import java.io.*; import java.util.*; import net.blerf.ftl.model.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/**
 * After a final victory: the copy kept while the Rebel Flagship is on her way to the last battle, and the rescue (keep
 * her, or the museum), the reward, "nothing", and a loss. args: gamedir, world saves (from WorldT), work, and
 * optionally a save logger's folder (copies/ of continue.sav and ae_prof.sav, in order, ending in a victory) to replay.
 */
public class VicT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 HomePlanet.immersiveNotifications = false;
 copies(v);
 loss(v);
 rescueKeep(v);
 rescueMuseum(v);
 reward(v);
 inbox(v);
 museum(v);
 if (a.length > 3) replay(game, new File(a[3]), new File(work, "replay"));
 // the honours (5.87, heromedel's Flagarino): the save reader's victory markers aren't honours, and one kept before shows without its id
 net.blerf.ftl.xml.Achievement real = null;
 for (net.blerf.ftl.xml.Achievement x : DataManager.get().getGeneralAchievements()) if (!x.isVictory() && x.getName() != null) { real = x; break; }
 java.util.List<String> hon = Museum.honours(new java.util.HashSet<String>(), java.util.Arrays.asList("PLAYER_SHIP_ENERGY_VICTORY", real.getId()));
 Setup.chk("V: a victory marker gained during her command is no honour; a real achievement is, by name (" + hon + ")", hon.equals(java.util.Arrays.asList(real.getName().getTextValue())));
 Setup.chk("V: a victory kept before 5.87 with the marker's id shows without it", Museum.shownHonours("Ballistophobia|PLAYER_SHIP_ENERGY_VICTORY|Federation Victory (Easy)").equals(java.util.Arrays.asList("Ballistophobia", "Federation Victory (Easy)")));
 Setup.done();
}
 static int victories = 0;
 static void profile(File saves, int wins, String shipName, String shipId) throws Exception {
  Profile p = Profile.createEmptyProfile(); p.setFileFormat(9);
  p.getStats().setTotalVictories(wins);
  if (shipName != null) p.getStats().getTopScores().add(new Score(shipName, shipId, 4431, 8, net.blerf.ftl.constants.Difficulty.NORMAL, true));
  OutputStream out = new FileOutputStream(new File(saves, "ae_prof.sav")); new ProfileParser().writeProfile(out, p); out.close();
 }
 static SavedGameState read(File f) throws Exception { return HomePlanet.savedGameParser.readSavedGame(f); }
 /** FTL writes continue.sav: the flagship's pending stage, whether she's alongside, the hull. */
 static void ftlWrites(Vault v, int stage, boolean nearby, int hull) throws Exception {
  SavedGameState g = read(v.continueFile());
  if (g.getRebelFlagshipState() == null) g.setRebelFlagshipState(new RebelFlagshipState());
  g.getRebelFlagshipState().setPendingStage(stage);
  g.setRebelFlagshipNearby(nearby);
  // FTL records "the flagship is alongside" only with a ship alongside: a stand-in (a copy of her own hull) and its AI
  g.setNearbyShip(nearby ? read(v.continueFile()).getPlayerShip() : null);
  g.setNearbyShipAI(nearby ? new NearbyShipAIState() : null);
  g.setUnknownXi(nearby ? Integer.valueOf(0) : null); // as FTL's own alongside saves have it
  g.getPlayerShip().setHullAmt(hull);
  g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + 1);
  SaveHelper.writeSavedGame(v.continueFile(), g);
  FinalVictory.watch();
 }
 static File copyOf(Vault v, Ship s) { return new File(v.historyOf(s), "final-battle.sav"); }
 /** Her special copies by prefix (in her folder's versions/, 5.69). */
 static List<File> kept(Vault v, String id, String prefix) { List<File> out = new ArrayList<File>(); for (File f : ShipStore.versions(v.folderOfId(id), true)) if (f.getName().startsWith(prefix)) out.add(f); return out; }
 static final Set<String> used = new HashSet<String>();
 /** Boards a docked ship not sent out before (the fleet reads in name order since 5.69, so a ship brought home would be first again). */
 static Ship boardNext(Vault v) throws Exception {
  if (v.boarded() == null) for (Ship d : v.docked()) if (used.add(d.id)) { v.board(d); break; }
  v.takeStock();
  return v.boarded();
 }
 /** FTL ends the run: continue.sav deleted (after a win, the profile first), and the station takes stock. */
 static void runEnds(Vault v, boolean won, Ship s) throws Exception {
  if (won) { victories++; profile(v.saves, victories, s.name, read(v.continueFile()).getPlayerShipBlueprintId()); }
  v.continueFile().delete();
  v.reload(); v.takeStock();
 }
 static void copies(Vault v) throws Exception {
  profile(v.saves, victories, null, null);
  HomePlanet.finalVictory = FinalVictory.NOTHING;
  Ship s = boardNext(v);
  ftlWrites(v, 3, false, 20);
  Setup.chk("C: with \"nothing\" chosen, no copy is kept", !copyOf(v, s).exists());
  HomePlanet.finalVictory = FinalVictory.RESCUE;
  ftlWrites(v, 1, true, 20);
  ftlWrites(v, 2, false, 20);
  Setup.chk("C: no copy for the first two battles, alongside or on her way", !copyOf(v, s).exists());
  ftlWrites(v, 3, false, 19);
  Setup.chk("C: a copy as the flagship heads for the last battle", copyOf(v, s).isFile() && read(copyOf(v, s)).getPlayerShip().getHullAmt() == 19);
  ftlWrites(v, 3, false, 17);
  Setup.chk("C: a later write on her way replaces it (repairs, jumps)", read(copyOf(v, s)).getPlayerShip().getHullAmt() == 17);
  ftlWrites(v, 3, true, 12);
  Setup.chk("C: once the flagship is alongside, the copy stays the calm one", read(copyOf(v, s)).getPlayerShip().getHullAmt() == 17 && !read(copyOf(v, s)).isRebelFlagshipNearby());
 }
 static void loss(Vault v) throws Exception {
  Ship s = v.boarded(); String id = s.id;
  runEnds(v, false, s);
  List<FinalVictory.Notice> n = FinalVictory.settle();
  Setup.chk("L: lost in the last battle (no victory): nothing to tell, the copy is closed", n.isEmpty() && v.byId(id) == null && v.finalBattles().isEmpty()
    && kept(v, id, "final-battle-").size() == 1);
 }
 static Ship toVictory(Vault v, String choice) throws Exception {
  HomePlanet.finalVictory = choice;
  Ship s = boardNext(v);
  ftlWrites(v, 3, false, 11);
  ftlWrites(v, 3, true, 11);
  runEnds(v, true, s);
  return s;
 }
 static void rescueKeep(Vault v) throws Exception {
  Ship s = toVictory(v, FinalVictory.RESCUE); String id = s.id, name = s.name;
  List<FinalVictory.Notice> n = FinalVictory.settle();
  Setup.chk("K: a victory with rescue chosen: an offer, with her value", n.size() == 1 && n.get(0).offer != null && n.get(0).value > 0 && n.get(0).text.contains(name) && n.get(0).text.contains(n.get(0).value + " scrap"));
  Setup.chk("K: the lore holds: the flagship withdraws", n.get(0).text.contains("withdrawn") && !n.get(0).text.toLowerCase().contains("destroyed"));
  Setup.chk("K: the offer stays open until decided", FinalVictory.settle().size() == 1 && FinalVictory.offer(id) != null);
  String what = FinalVictory.keep(FinalVictory.offer(id));
  Ship back = v.byId(id);
  SavedGameState g = back == null ? null : back.save();
  Setup.chk("K: keep her: docked under her own id, as she was kept (hull 11)", back != null && back.state == Ship.State.DOCKED && g.getPlayerShip().getHullAmt() == 11 && what.contains(name));
  Setup.chk("K: ready for a new journey: sector 1, no flagship alongside or on her way", g.getSectorNumber() == 0 && !g.isRebelFlagshipNearby() && g.getRebelFlagshipState().getPendingStage() < 3);
  Setup.chk("K: settled: no offer left, her victory kept in her history", FinalVictory.settle().isEmpty() && FinalVictory.offer(id) == null
    && kept(v, id, "victory-").size() == 1);
 }
 static void rescueMuseum(Vault v) throws Exception {
  Ship s = toVictory(v, FinalVictory.RESCUE); String id = s.id;
  List<FinalVictory.Notice> n = FinalVictory.settle();
  int before = v.storageScrap(), value = n.get(0).value;
  FinalVictory.museum(FinalVictory.offer(id));
  boolean recoverable = false; for (Vault.Departed d : v.recoverable()) if (d.id.equals(id)) recoverable = true;
  Setup.chk("M: the museum's offer: her full value to storage, and she doesn't come back", v.storageScrap() == before + value && v.byId(id) == null && !recoverable);
  Setup.chk("M: her fate is the museum", Setup.fateText(v.folderOfId(id)).startsWith("MUSEUM") && FinalVictory.settle().isEmpty());
 }
 static void reward(Vault v) throws Exception {
  Ship s = toVictory(v, FinalVictory.REWARD); String id = s.id;
  int value = FinalVictory.value(read(copyOf(v, s)));
  int before = v.storageScrap();
  List<FinalVictory.Notice> n = FinalVictory.settle();
  Setup.chk("R: a reward: her full value to storage, a notice to read, and she stays lost", n.size() == 1 && n.get(0).offer == null && v.storageScrap() == before + value && v.byId(id) == null && n.get(0).text.contains(value + " scrap"));
  Setup.chk("R: paid once", FinalVictory.settle().isEmpty() && v.storageScrap() == before + value);
  SavedGameState g = read(kept(v, id, "victory-").get(0));
  Setup.chk("R: her value is her price at the rate", value == Pricing.ship(g, Pricing.rate()).total() && value > 0);
 }
 static void inbox(Vault v) throws Exception {
  HomePlanet.immersiveNotifications = true;
  Ship s = toVictory(v, FinalVictory.RESCUE); String id = s.id;
  List<FinalVictory.Notice> n = FinalVictory.settle();
  Transmissions.Message m = null; for (Transmissions.Message x : Transmissions.load()) if (x.key.equals("rescue:" + id)) m = x;
  Setup.chk("I: with Transmissions on, the offer goes to the inbox, not a notice", n.isEmpty() && m != null && Transmissions.isRescue(m) && !m.claimed && m.body.contains(s.name));
  String what = FinalVictory.keep(FinalVictory.offer(Transmissions.rescueId(m)));
  Transmissions.decided(m, what);
  m = null; for (Transmissions.Message x : Transmissions.load()) if (x.key.equals("rescue:" + id)) m = x;
  Setup.chk("I: decided in the inbox: she's docked, the message says so", v.byId(id) != null && m.claimed && m.claimedWhat.contains("docked"));
  HomePlanet.immersiveNotifications = false;
 }
 /** The museum: every victor in the Hall of Victors with her status, the ship lost without a victory in the Memorial. */
 static void museum(Vault v) throws Exception {
  List<Museum.Exhibit> all = Museum.exhibits(v);
  Map<Museum.Status, Integer> n = new HashMap<Museum.Status, Integer>();
  for (Museum.Exhibit e : all) n.put(e.status, (n.containsKey(e.status) ? n.get(e.status) : 0) + 1);
  System.out.println("museum: " + n);
  Setup.chk("U: the ship lost without a victory is in the Memorial, and only there", n.containsKey(Museum.Status.MEMORIAL) && n.get(Museum.Status.MEMORIAL) == 1);
  Setup.chk("U: the kept victors are Still in Service", n.containsKey(Museum.Status.IN_SERVICE) && n.get(Museum.Status.IN_SERVICE) == 2);
  Setup.chk("U: the one sold to the museum is Preserved", n.containsKey(Museum.Status.PRESERVED) && n.get(Museum.Status.PRESERVED) == 1);
  Setup.chk("U: the one rewarded (not kept) is Honoured in Memory", n.containsKey(Museum.Status.MEMORY) && n.get(Museum.Status.MEMORY) == 1);
  Setup.chk("U: victories counted, and the Hall before the Memorial", Museum.totalVictories(all) == 4 && all.get(0).victor && !all.get(all.size() - 1).victor);
  Museum.Exhibit kept = null; for (Museum.Exhibit e : all) if (e.status == Museum.Status.IN_SERVICE) kept = e;
  String[] vd = kept.victoryDetails().get(0);
  Setup.chk("U: a victory's details are kept (date, sector, score from the profile)", !vd[0].isEmpty() && !vd[3].isEmpty() && "4431".equals(vd[1]));
  Museum.setEpitaph(v, kept.id, "She held the line.");
  Museum.Exhibit again = null; for (Museum.Exhibit e : Museum.exhibits(v)) if (e.id.equals(kept.id)) again = e;
  Setup.chk("U: an epitaph stays on her plate", "She held the line.".equals(again.epitaph()));
  // a kept victor later lost in action
  Ship s = v.byId(kept.id); v.board(s); v.takeStock(); v.continueFile().delete(); v.reload(); v.takeStock();
  Museum.Exhibit lost = null; for (Museum.Exhibit e : Museum.exhibits(v)) if (e.id.equals(kept.id)) lost = e;
  Setup.chk("U: a victor kept and later lost: Lost in Action, still in the Hall", lost != null && lost.victor && lost.status == Museum.Status.LOST);
 }
 /** Replays a save logger's files in order into a fresh saves folder, then settles: the logged run ended in a victory. */
 static void replay(File game, File logDir, File work) throws Exception {
  File[] fs = new File(logDir, "copies").listFiles(); Arrays.sort(fs);
  File saves = new File(work, "saves"); saves.mkdirs();
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  HomePlanet.finalVictory = FinalVictory.RESCUE;
  SavedGameState first = null; File lastOnHerWay = null;
  for (File f : fs) if (f.getName().endsWith("continue.sav")) { first = read(f); break; }
  for (File f : fs) if (f.getName().endsWith("ae_prof.sav")) { SafeFiles.copy(f, new File(saves, "ae_prof.sav")); break; }
  Ship s = v.adopt(first); v.board(s); v.takeStock();
  for (File f : fs) {
   if (f.getName().endsWith("ae_prof.sav")) SafeFiles.copy(f, new File(saves, "ae_prof.sav"));
   else if (f.getName().endsWith("continue.sav")) {
    SafeFiles.copy(f, v.continueFile());
    FinalVictory.watch();
    SavedGameState g = read(f);
    if (!g.isRebelFlagshipNearby() && g.getRebelFlagshipState().getPendingStage() >= 3) lastOnHerWay = f;
   }
  }
  Setup.chk("P: replay: the copy is the last one written on her way to the last battle (" + (lastOnHerWay == null ? "none" : lastOnHerWay.getName()) + ")",
    lastOnHerWay != null && SafeFiles.hash(lastOnHerWay).equals(SafeFiles.hash(copyOf(v, s))));
  v.continueFile().delete(); v.reload(); v.takeStock();
  List<FinalVictory.Notice> n = FinalVictory.settle();
  Setup.chk("P: replay: the logged victory is found, and she's offered back", n.size() == 1 && n.get(0).offer != null);
  System.out.println("replay: " + s.name + " valued at " + (n.isEmpty() ? 0 : n.get(0).value) + " scrap");
 }
}
