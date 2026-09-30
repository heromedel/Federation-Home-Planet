import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.model.*; import net.blerf.ftl.xml.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Transmissions: what's sent when, once, and claiming rewards into storage. args: gamedir, world saves (from WorldT), work */
public class TransT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 texts();
 profile(saves, new String[] {"PLAYER_SHIP_HARD"}, new String[] {"ACH_SECTOR_5"});
 HomePlanet.immersiveMode = true; HomePlanet.applyImmersive();
 Vault.switchFleet(true);
 UnlockGrants.returning(Unlocks.read());
 flow(saves);
 clearance(saves);
 reentry();
 stipend();
 profiles(saves);
 Setup.done();
}
 static void profile(File saves, String[] unlockedA, String[] achievements) throws Exception {
  Profile p = Profile.createEmptyProfile(); p.setFileFormat(9);
  Map<String, ShipAvailability> m = new LinkedHashMap<String, ShipAvailability>();
  for (String base : DataManager.get().getPlayerShipBaseIds(true)) m.put(base, new ShipAvailability(base, false, false));
  for (String b : unlockedA) m.put(b, new ShipAvailability(b, true, false));
  p.setShipUnlockMap(m);
  List<AchievementRecord> recs = new ArrayList<AchievementRecord>();
  for (String id : achievements) recs.add(new AchievementRecord(id, net.blerf.ftl.constants.Difficulty.NORMAL));
  p.setAchievements(recs);
  OutputStream out = new FileOutputStream(new File(saves, "ae_prof.sav")); new ProfileParser().writeProfile(out, p); out.close();
 }
 /** Every real achievement has a message, and every reward names something the game has. */
 static void texts() throws Exception {
  List<String> missing = new ArrayList<String>(), bad = new ArrayList<String>();
  for (Achievement x : DataManager.get().getAchievements().values()) {
   if (x.isVictory() || x.isQuest()) continue;
   Transmissions.Message m = new Transmissions.Message();
   java.lang.reflect.Method t = Transmissions.class.getDeclaredMethod("templates"); t.setAccessible(true);
   Map<?, ?> all = (Map<?, ?>) t.invoke(null);
   if (!all.containsKey("ach:" + x.getId())) missing.add(x.getId());
  }
  java.lang.reflect.Method t = Transmissions.class.getDeclaredMethod("templates"); t.setAccessible(true);
  for (Map.Entry<?, ?> e : ((Map<?, ?>) t.invoke(null)).entrySet()) {
   java.lang.reflect.Field rf = e.getValue().getClass().getDeclaredField("reward"); rf.setAccessible(true);
   String reward = (String) rf.get(e.getValue());
   for (String p : reward.split(",")) {
    p = p.trim(); if (p.isEmpty()) continue;
    for (String q : p.startsWith("choice ") ? p.substring(7).split("\\|") : new String[] {p}) {
     String[] w = q.trim().split("\\s+", 2);
     boolean ok = w[0].equals("item") ? (homeplanet.model.Items.isWeapon(w[1]) || homeplanet.model.Items.isDrone(w[1]) || homeplanet.model.Items.isAugment(w[1]))
       : w[0].equals("system") ? SavedGameParser.SystemType.findById(w[1]) != null
       : w[0].equals("crew") ? SavedGameParser.CrewType.findById(w[1]) != null
       : Arrays.asList("scrap", "fuel", "missiles", "parts").contains(w[0]) && w[1].matches("\\d+");
     if (!ok) bad.add(e.getKey() + ": " + q);
    }
   }
  }
  Setup.chk("T: every achievement has a message " + missing, missing.isEmpty());
  Setup.chk("T: every reward names something FTL has " + bad, bad.isEmpty());
 }
 static Transmissions.Message find(String key) { for (Transmissions.Message m : Transmissions.load()) if (m.key.startsWith(key)) return m; return null; }
 static void flow(File saves) throws Exception {
  int n = Transmissions.check();
  Setup.chk("T: a new career: the welcome, and the empty shipyard's free command", n == 2 && find("welcome") != null && find("empty") != null && find("empty").body.contains("Kestrel"));
  Setup.chk("T: each is sent once", Transmissions.check() == 0 && Transmissions.unread() == 2);
  Setup.chk("T: an achievement from before Immersive Mode earns nothing", find("ach:ACH_SECTOR_5") == null);
  profile(saves, new String[] {"PLAYER_SHIP_HARD", "PLAYER_SHIP_MANTIS", "PLAYER_SHIP_FED"}, new String[] {"ACH_SECTOR_5", "ACH_TOUGH_SHIP", "ACH_NO_BUYING", "ACH_MANTIS_SLAUGHTER", "ACH_NO_UPGRADES"});
  Transmissions.check();
  Transmissions.Message order = find("order:PLAYER_SHIP_MANTIS 0"), promo = find("promo:1");
  Setup.chk("T: a new unlock brings a commission order", order != null && order.body.contains("Mantis") && order.isOrder());
  Setup.chk("T: the Federation Cruiser A brings a promotion, not an order", promo != null && find("order:PLAYER_SHIP_FED 0") == null && UnlockGrants.rank(Unlocks.read()) == 1);
  Transmissions.Message tough = find("ach:ACH_TOUGH_SHIP");
  Setup.chk("T: a new achievement brings its reward, addressed to the new rank", tough != null && tough.hasReward() && tough.body.startsWith("Captain,"));
  Vault v = Vault.get();
  Transmissions.claim(tough, -1);
  Setup.chk("T: claimed: Rock Plating in Spacedock Storage", v.storage().save().getPlayerShip().getAugmentIdList().contains("ROCK_ARMOR"));
  boolean again = false; try { Transmissions.claim(find("ach:ACH_TOUGH_SHIP"), -1); } catch (IOException e) { again = true; }
  Setup.chk("T: a reward is claimed once", again && find("ach:ACH_TOUGH_SHIP").claimed);
  Transmissions.claim(find("ach:ACH_NO_BUYING"), 1);
  boolean drone = false; for (SavedGameParser.DroneState d : v.storage().save().getPlayerShip().getDroneList()) if ("SHIP_REPAIR".equals(d.getDroneId())) drone = true;
  Setup.chk("T: a choice gives what was chosen", drone && !v.storage().save().getPlayerShip().getAugmentIdList().contains("REPAIR_ARM"));
  int crew = v.storage().save().getPlayerShip().getCrewList().size();
  Transmissions.claim(find("ach:ACH_MANTIS_SLAUGHTER"), -1);
  Setup.chk("T: a crew volunteer joins Spacedock Storage", v.storage().save().getPlayerShip().getCrewList().size() == crew + 1);
  Transmissions.claim(find("ach:ACH_NO_UPGRADES"), -1);
  Setup.chk("T: a system goes to the stored systems", new String(SafeFiles.read(v.systemsFile()), "UTF-8").contains("cloaking 1"));
 }
 static void clearance(File saves) throws Exception {
  // archive
  Transmissions.Message w = find("welcome");
  int unread = Transmissions.unread();
  Transmissions.setArchived(w, true);
  Setup.chk("A: an archived transmission leaves the inbox, and is kept", find("welcome").archived && Transmissions.unread() <= unread);
  Transmissions.setArchived(find("welcome"), false);
  Setup.chk("A: and can come back", !find("welcome").archived);
  // rank 1 (Captain) from the test above: custom ships yes, the Federation's artillery not yet
  Setup.chk("C: a Captain may build custom ships", Clearance.customReason() == null);
  Setup.chk("C: the Federation's artillery waits for a Commodore", Clearance.artilleryReason("ARTILLERY_FED") != null && Clearance.artilleryReason("ARTILLERY_FED_C") != null);
  Setup.chk("C: the Flagship's weapons wait for Rule Ten", Clearance.artilleryReason("ARTILLERY_BOSS_1") != null);
  profile(saves, new String[] {"PLAYER_SHIP_HARD", "PLAYER_SHIP_MANTIS", "PLAYER_SHIP_FED"}, new String[] {"ACH_SECTOR_5", "ACH_TOUGH_SHIP", "ACH_NO_BUYING", "ACH_MANTIS_SLAUGHTER", "ACH_NO_UPGRADES", "ACH_SCRAP"});
  Transmissions.check();
  Setup.chk("C: after Rule Ten, they're cleared", Clearance.artilleryReason("ARTILLERY_BOSS_1") == null && find("ach:ACH_SCRAP").body.contains("Flagship"));
  HomePlanet.immersiveMode = false;
  Setup.chk("C: outside Immersive Mode, everything is cleared", Clearance.customReason() == null && Clearance.artilleryReason("ARTILLERY_FED") == null);
  HomePlanet.immersiveMode = true;
  // prices
  Setup.chk("P: the Artillery Beam has a price", Pricing.artillery("ARTILLERY_FED") == 200 && Pricing.artillery("ARTILLERY_FED_C") == 150 && Pricing.artillery("ARTILLERY_BOSS_2") == 100);
  SavedGameParser.SavedGameState fed = Commission.build("PLAYER_SHIP_FED", "Fed", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(1));
  SavedGameParser.SavedGameState kes = Commission.build("PLAYER_SHIP_HARD", "Kes", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(1));
  Pricing.Quote q = Pricing.ship(fed, 0, 0, 100);
  System.out.println("Federation Cruiser A: " + q.total() + " " + q.lines);
  Setup.chk("P: a Federation Cruiser pays for her artillery's gun", String.join(" ", q.lines).contains("Weapons") && Commission.artilleryWeapon("PLAYER_SHIP_FED") != null);
 }
 /** Leaving Immersive Mode and coming back through Settings keeps the free ships still waiting there. */
 static void reentry() throws Exception {
  Unlocks u = Unlocks.read();
  Setup.chk("U: the Mantis Cruiser A waits, free", UnlockGrants.freeNow(u, "PLAYER_SHIP_MANTIS"));
  UnlockGrants.leaving(u);
  Vault.switchFleet(false);
  HomePlanet.leaveImmersive();
  Object r = Class.forName("homeplanet.ui.RuleBoxes").getConstructor().newInstance();
  java.lang.reflect.Field f = r.getClass().getDeclaredField("immersiveBox"); f.setAccessible(true);
  ((javax.swing.JCheckBox) f.get(r)).setSelected(true);
  Vault.switchFleet(true); // as ImmersiveDialog.enter does
  HomePlanet.immersiveMode = true; HomePlanet.applyImmersive();
  UnlockGrants.returning(Unlocks.read());
  r.getClass().getMethod("apply").invoke(r); // then OK in Settings
  Setup.chk("U: back in Immersive Mode through Settings, she's still free", HomePlanet.immersiveMode && UnlockGrants.freeNow(Unlocks.read(), "PLAYER_SHIP_MANTIS"));
 }
 static void stipend() throws Exception {
  Vault v = Vault.get();
  int before = v.storageScrap();
  if (!Career.started(v.root)) Career.start(false, false);
  Setup.chk("S: a career begins with 25 scrap in Spacedock Storage", Career.started(v.root) && v.storageScrap() == before + 25);
  if (v.boarded() == null) { Ship n = v.adopt(Commission.build("PLAYER_SHIP_HARD", "Stipend Kestrel", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(7))); v.board(n); }
  v.takeStock();
  int sectors = v.sectorsSeen();
  SavedGameParser.SavedGameState g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
  g.setSectorNumber(g.getSectorNumber() + 9); g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + 40);
  SaveHelper.writeSavedGame(v.continueFile(), g);
  v.takeStock();
  Setup.chk("S: FTL's progress is counted in sectors", v.sectorsSeen() == sectors + 9);
  int scrap = v.storageScrap();
  int achievements = 5; // earned in Immersive Mode above: TOUGH_SHIP, NO_BUYING, MANTIS_SLAUGHTER, NO_UPGRADES, SCRAP
  int each = Career.stipend(UnlockGrants.rank(Unlocks.read()), achievements);
  Transmissions.check();
  Transmissions.Message m = find("stipend:");
  Setup.chk("S: 9 sectors pay 2 months in one message", m != null && m.body.contains("stipend for the last 2 months") && m.body.contains((2 * each) + " scrap") && v.storageScrap() == scrap + 2 * each);
  System.out.println("Stipend: " + each + " a month (Captain, 5 achievements): " + m.body.replace("\n", " / "));
  Transmissions.check();
  int stipends = 0; for (Transmissions.Message x : Transmissions.load()) if (Transmissions.isStipend(x)) stipends++;
  Setup.chk("S: the odd sector waits for the next month", stipends == 1);
  Transmissions.delete(m);
  Setup.chk("S: a stipend's notice can be deleted", find("stipend:") == null);
  Setup.chk("S: the stipend's formula (20 + achievements x rank multiple)", Career.stipend(0, 51) == 71 && Career.stipend(1, 51) == 122 && Career.stipend(2, 51) == 173);
 }
 static void profiles(File saves) throws Exception {
  File normal = new File(saves, Vault.FOLDER), immersive = new File(saves, Vault.IMMERSIVE_FOLDER);
  File prof = new File(saves, "ae_prof.sav");
  String normalHash = SafeFiles.hash(prof);
  File copy = ProfileSwap.backup(saves, normal);
  Setup.chk("P: a backup copy of the FTL profile", copy.isFile() && SafeFiles.hash(copy).equals(normalHash) && prof.isFile());
  ProfileSwap.swap(saves, normal, immersive);
  Setup.chk("P: entering: the normal profile is set aside, and FTL has none (a fresh one next start)", !prof.exists() && new File(normal, "ftl-profile/ae_prof.sav").isFile());
  SafeFiles.writeText(prof, "immersive profile", false); // FTL made its own
  String immersiveHash = SafeFiles.hash(prof);
  ProfileSwap.swap(saves, immersive, normal);
  Setup.chk("P: leaving: the normal profile is back, the Immersive one kept", SafeFiles.hash(prof).equals(normalHash) && SafeFiles.hash(new File(immersive, "ftl-profile/ae_prof.sav")).equals(immersiveHash));
  ProfileSwap.swap(saves, normal, immersive);
  Setup.chk("P: entering again: the Immersive profile comes back", SafeFiles.hash(prof).equals(immersiveHash) && SafeFiles.hash(new File(normal, "ftl-profile/ae_prof.sav")).equals(normalHash));
  ProfileSwap.swap(saves, immersive, normal);
 }
}
