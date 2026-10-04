import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.model.*; import net.blerf.ftl.xml.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Transmissions: what's sent when, once, and claiming rewards into storage. args: gamedir, world saves (from WorldT), work */
public class TransT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 texts();
 profile(saves, new String[] {"PLAYER_SHIP_HARD"}, new String[] {"ACH_SECTOR_5"});
 HomePlanet.immersiveMode = true;
 Vault.switchFleet(true);
 UnlockGrants.returning(Unlocks.read());
 flow(saves);
 clearance(saves);
 reentry();
 stipend();
 profiles(saves);
 freeCommand(game, new File(work, "free"));
 Setup.done();
}
 static int orders() { int n = 0; for (Transmissions.Message m : Transmissions.load()) if (m.key.startsWith("empty:")) n++; return n; }
 /** The free command: once when a fleet starts, again with each plea for a new ship; an empty shipyard alone never sends one. */
 static void freeCommand(File game, File work) throws Exception {
  File saves = new File(work, "saves"); saves.mkdirs();
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
  HomePlanet.commissionCosts = true; HomePlanet.immersiveNotifications = true;
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  Transmissions.check();
  Setup.chk("F: a new fleet: the free command, and one order for it", v.freeCommandOpen() && orders() == 1);
  String setting = HomePlanet.freeShip;
  HomePlanet.freeShip = FreeCommand.ANY;
  Setup.chk("F: a new fleet starts with a Kestrel Type A, whatever a report would grant", FreeCommand.KESTREL.equals(FreeCommand.ship()));
  HomePlanet.freeShip = FreeCommand.VARIABLE;
  Setup.chk("F: the old Variable reads as a Kestrel Type A (or the Relief Ship)", FreeCommand.KESTREL.equals(Economy.reassignment())
    && FreeCommand.offered(Economy.reassignment()).contains("Relief Ship Type A"));
  HomePlanet.freeShip = setting;
  Ship stranger = v.adopt(Commission.build("PLAYER_SHIP_HARD", "New Game Kestrel", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(3)));
  v.board(stranger); Transmissions.check();
  v.remove(v.boarded(), "DESTROY"); Transmissions.check();
  Setup.chk("F: a ship came and was destroyed: still the one order, the command still waiting", orders() == 1 && v.freeCommandOpen());
  v.useFreeCommand("commissioned"); Ship k = v.adopt(Commission.build("PLAYER_SHIP_HARD", "Free Kestrel", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(4)));
  v.board(k); Transmissions.check();
  v.remove(v.boarded(), "DESTROY"); Transmissions.check();
  Setup.chk("F: the command taken, then the shipyard empty again: no new order, nothing free", orders() == 1 && !v.freeCommandOpen() && v.shipyardEmpty());
  Setup.chk("F: the order for a command since taken is deleted, not archived", Transmissions.deletable(find("empty")));
  Transmissions.check(); Transmissions.check();
  int stranded = 0; for (Transmissions.Message m : Transmissions.load()) if (m.key.startsWith("stranded:")) stranded++;
  Setup.chk("F: no ship and no free command: the Liaison's letter, once", stranded == 1 && "Without a ship".equals(find("stranded").subject)
    && find("stranded").body.contains("Plead for New Ship") && !find("stranded").body.contains("Junkyard: Salvage"));
  Ship again = v.adopt(Commission.build("PLAYER_SHIP_HARD", "Short Lived", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(5)));
  Transmissions.check(); v.remove(again, "DESTROY"); Transmissions.check(); Transmissions.check();
  int stranded2 = 0; for (Transmissions.Message m : Transmissions.load()) if (m.key.startsWith("stranded:")) stranded2++;
  Setup.chk("F: a ship again, then none again: no second letter (only the first time)", stranded2 == 1);
  Setup.chk("F: a plea's ship outside Immersive Mode is Settings'", FreeCommand.norm(HomePlanet.freeShip).equals(Economy.reassignment()));
  Ship docked = v.adopt(Commission.build("PLAYER_SHIP_HARD", "Still Here", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(6)));
  int scrapBefore = v.storageScrap();
  v.plead(); Transmissions.check();
  Setup.chk("F: a plea grants an order with a ship docked, its order replaces the old one, nothing taken yet", v.freeCommandOpen() && v.freeCommandReassigned()
    && orders() == 1 && !Transmissions.deletable(find("empty")) && v.storageScrap() == scrapBefore && v.byId(docked.id) != null);
  Setup.chk("F: and it's the Shipyard's answer to the plea, naming what it offers", "Your plea was heard".equals(find("empty").subject)
    && find("empty").body.contains(FreeCommand.offered(Economy.reassignment())));
  v.withdrawPlea(); Transmissions.pleaWithdrawn();
  Setup.chk("F: withdrawing the plea takes the order back, out of the inbox too, and the Shipyard says so", !v.freeCommandOpen() && orders() == 0
    && find("withdrawn") != null && "Order cancelled".equals(find("withdrawn").subject));
  v.remove(docked, "DESTROY");
  // Career messages in Sandbox Mode: the career begins once, with its letter and scrap, and no free ship
  int scrap = v.storageScrap(), ordersBefore = orders();
  HomePlanet.careerMessages = true;
  Transmissions.check(); Transmissions.check();
  int welcomes = 0; for (Transmissions.Message m : Transmissions.load()) if (m.key.equals("welcome:career")) welcomes++;
  Setup.chk("C: Career messages begin a Sandbox career: the Liaison's letter, once, and 25 scrap", welcomes == 1 && Career.started(v.root) && v.storageScrap() == scrap + Career.STARTING_SCRAP);
  Setup.chk("C: and no free ship with it (the Sandbox fleet has its own)", orders() == ordersBefore && find("welcome") != null && find("welcome").key.equals("welcome:career"));
  HomePlanet.careerMessages = false;
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
  // every letter: a sender, a subject and text; the lore held (the rebellion lowercase, the Rebel Flagship never destroyed)
  List<String> loose = new ArrayList<String>();
  for (Map.Entry<?, ?> e : ((Map<?, ?>) t.invoke(null)).entrySet()) {
   Object tp = e.getValue();
   String from = (String) field(tp, "from"), subject = (String) field(tp, "subject"), body = field(tp, "body").toString();
   if (from.isEmpty() || subject.isEmpty() || body.trim().isEmpty()) loose.add(e.getKey() + ": missing sender, subject or text");
   if (body.contains("Rebellion") || body.contains("Rebels")) loose.add(e.getKey() + ": the rebellion capitalised");
   if (java.util.regex.Pattern.compile("Flagship[^.\\n]*(destroyed|killed|wrecked|blown up)").matcher(body).find()) loose.add(e.getKey() + ": the Rebel Flagship destroyed");
  }
  Setup.chk("T: every letter has a sender, subject and text, and keeps the lore " + loose, loose.isEmpty());
 }
  static Object field(Object o, String name) throws Exception { java.lang.reflect.Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
 static Transmissions.Message find(String key) { for (Transmissions.Message m : Transmissions.load()) if (m.key.startsWith(key)) return m; return null; }
 static void flow(File saves) throws Exception {
  int n = Transmissions.check();
  Setup.chk("T: a new career: the welcome, and the empty shipyard's free command", n == 2 && find("welcome") != null && find("empty") != null && find("empty").body.contains("Kestrel"));
  Setup.chk("T: the welcome names the career's own sign-on bonus", find("welcome").body.contains("bonus of " + Career.startingScrap() + " scrap") && !find("welcome").body.contains("{start}"));
  Setup.chk("T: the welcome is on top of the inbox (sent last)", Transmissions.load().get(0).key.equals("welcome"));
  Setup.chk("T: each is sent once", Transmissions.check() == 0 && Transmissions.unread() == 2);
  Setup.chk("T: an achievement from before Immersive Mode earns nothing", find("ach:ACH_SECTOR_5") == null);
  profile(saves, new String[] {"PLAYER_SHIP_HARD", "PLAYER_SHIP_MANTIS", "PLAYER_SHIP_FED"}, new String[] {"ACH_SECTOR_5", "ACH_TOUGH_SHIP", "ACH_NO_BUYING", "ACH_MANTIS_SLAUGHTER", "ACH_NO_UPGRADES"});
  Transmissions.check();
  Transmissions.Message order = find("order:PLAYER_SHIP_MANTIS 0"), promo = find("promo:1");
  Setup.chk("T: a new unlock brings a commission order", order != null && order.body.contains("Mantis") && order.isOrder());
  Setup.chk("T: the Federation Cruiser A brings a promotion, not an order", promo != null && find("order:PLAYER_SHIP_FED 0") == null && UnlockGrants.rank(Unlocks.read()) == 1);
  // a Type B (two of her achievements): the shared letter (her makers open the next model), not her Type A's story
  profile(saves, new String[] {"PLAYER_SHIP_HARD", "PLAYER_SHIP_MANTIS", "PLAYER_SHIP_FED", "PLAYER_SHIP_ENERGY"}, new String[] {"ACH_SECTOR_5", "ACH_TOUGH_SHIP", "ACH_NO_BUYING", "ACH_MANTIS_SLAUGHTER", "ACH_NO_UPGRADES", "ACH_ENERGY_SHIELDS", "ACH_ENERGY_POWER"});
  Transmissions.check();
  Transmissions.Message zoltanA = find("order:PLAYER_SHIP_ENERGY 0"), zoltanB = find("order:PLAYER_SHIP_ENERGY 1");
  Setup.chk("T: the Zoltan Cruiser A's order tells the Council's story; her Type B's is the shared letter, naming her", zoltanA != null && zoltanA.body.contains("Zoltan Council")
    && zoltanB != null && zoltanB.body.contains("The Zoltan have contacted") && zoltanB.body.contains("achieve with Zoltan Cruiser") && !zoltanB.body.contains("Council") && !zoltanB.body.contains("{") && zoltanB.subject.contains("Type B"));
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
  Pricing.Quote q = Pricing.ship(fed, 100);
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
  HomePlanet.immersiveMode = true;
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
  Transmissions.check(); Transmissions.Message owed = find("stipend:"); if (owed != null) Transmissions.delete(owed); // anything owed already, paid first
  int sectors = v.sectorsSeen();
  java.util.Properties cp = new java.util.Properties(); cp.load(new java.io.ByteArrayInputStream(SafeFiles.read(new File(v.root, "career.txt"))));
  int month = Career.beaconsPerStipend(), into = (v.beaconsSeen() - Integer.parseInt(cp.getProperty("beaconsAtStart"))) % month;
  int jump = 2 * month - into + month / 2; // two months and half another, at the career's difficulty
  SavedGameParser.SavedGameState g = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
  g.setSectorNumber(g.getSectorNumber() + 1); g.setTotalBeaconsExplored(g.getTotalBeaconsExplored() + jump);
  SaveHelper.writeSavedGame(v.continueFile(), g);
  v.takeStock();
  Setup.chk("S: FTL's progress is counted in sectors and beacons", v.sectorsSeen() == sectors + 1);
  int scrap = v.storageScrap();
  int achievements = 5; // earned in Immersive Mode above: TOUGH_SHIP, NO_BUYING, MANTIS_SLAUGHTER, NO_UPGRADES, SCRAP
  int each = Career.stipend(UnlockGrants.rank(Unlocks.read()), achievements);
  Transmissions.check();
  Transmissions.Message m = find("stipend:");
  Setup.chk("S: " + jump + " beacons (" + month + " a month) pay 2 months in one message", m != null && m.body.contains("stipend for the last " + 2 * Career.monthsPerStipend() + " months") && m.body.contains((2 * each) + " scrap") && ("scrap " + 2 * each).equals(m.reward));
  System.out.println("Stipend: " + each + " a month (Captain, 5 achievements): " + m.body.replace("\n", " / "));
  Setup.chk("S: the stipend waits to be claimed: the Cargo Hold is untouched", v.storageScrap() == scrap && Transmissions.unclaimedStipend(m));
  Setup.chk("S: an unclaimed stipend can't be deleted", !Transmissions.deletable(m));
  boolean refused = false;
  try { Transmissions.setArchived(m, true); } catch (java.io.IOException e) { refused = e.getMessage().contains("Claim the stipend first"); }
  Setup.chk("S: an unclaimed stipend can't be archived", refused && !m.archived);
  Transmissions.check();
  int stipends = 0; for (Transmissions.Message x : Transmissions.load()) if (Transmissions.isStipend(x)) stipends++;
  Setup.chk("S: the odd beacons wait for the next month", stipends == 1);
  Transmissions.claim(m, -1);
  Setup.chk("S: Claim puts the stipend in the Cargo Hold", v.storageScrap() == scrap + 2 * each && m.claimed);
  Setup.chk("S: a claimed stipend can be deleted", Transmissions.deletable(m));
  Transmissions.Message old = new Transmissions.Message(); old.key = "stipend:old";
  Setup.chk("S: a stipend paid in before claims (no reward) can be deleted", Transmissions.deletable(old));
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
