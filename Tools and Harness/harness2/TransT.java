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
}
