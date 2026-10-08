import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Augments with no room aboard: four seen away from a store (the fourth in the cargo list, as FTL keeps it), the one gone after the jump shipped home. args: gamedir, world saves, work */
public class OverT {
 static Vault v;
 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive(); HomePlanet.reputationOn = false; HomePlanet.careerMessages = false;
  HomePlanet.immersiveNotifications = true; HomePlanet.augmentsHome = true;
  v = Setup.open(game, saves); v.storage(); v.takeStock();
  Setup.chk("O: a ship is boarded", v.boarded() != null);
  Transmissions.check();

  // a fourth augment, away from a store: FTL asks which to throw away; the new one goes
  fly(0, false, "SCRAP_COLLECTOR", "DRONE_RECOVERY", "AUTO_COOLDOWN", "SHIELD_RECHARGE");
  Setup.chk("O: four aboard: nothing shipped before the jump", shipped().isEmpty());
  fly(1, false, "SCRAP_COLLECTOR", "DRONE_RECOVERY", "AUTO_COOLDOWN");
  List<Transmissions.Message> s = shipped();
  Setup.chk("O: after the jump, the one thrown away comes home by one letter (FTL writes it twice in cargo; the Burst Laser there isn't an augment)", s.size() == 1 && s.get(0).reward.equals("item SHIELD_RECHARGE")
    && !s.get(0).body.contains("{") && !s.get(0).subject.contains("{"));
  Setup.chk("O: sent by one of her crew, for the rest (6.02)", s.get(0).from.endsWith(" and the rest of the crew") && !s.get(0).from.contains("{"));
  int before = count("SHIELD_RECHARGE");
  Transmissions.claim(s.get(0), -1);
  Setup.chk("O: claimed, it is in the Cargo Hold", count("SHIELD_RECHARGE") == before + 1);
  { // the station log: her crew's note, then the letter with its words filled in, then the claim (6.02: the letter was logged before its words were filled)
   String log = new String(java.nio.file.Files.readAllBytes(new File(v.root, "logs/events.log").toPath()), "UTF-8");
   int o = log.indexOf("what=shipped augment=SHIELD_RECHARGE"), t = log.indexOf("| TRANSMISSION | log=station key=shipped:", Math.max(0, o)), c = log.indexOf("| CLAIM |", Math.max(0, t));
   String letter = t < 0 ? "" : log.substring(log.lastIndexOf('\n', t) + 1, log.indexOf('\n', log.indexOf('\n', t) + 1));
   Setup.chk("O: the station log has her crew's note, the letter, then the claim, in order", o >= 0 && t > o && c > t);
   Setup.chk("O: the letter's log lines have its words filled in", letter.contains("TRANSMISSION") && !letter.contains("{") && letter.contains("reward=\"item SHIELD_RECHARGE\""));
  }
  fly(2, false, "SCRAP_COLLECTOR", "DRONE_RECOVERY", "AUTO_COOLDOWN");
  Setup.chk("O: shipped once", shipped().size() == 1);

  // an old one thrown away to keep the new
  fly(2, false, "SCRAP_COLLECTOR", "DRONE_RECOVERY", "AUTO_COOLDOWN", "O2_MASKS");
  fly(3, false, "SCRAP_COLLECTOR", "AUTO_COOLDOWN", "O2_MASKS");
  Setup.chk("O: an old augment thrown away to keep the new one comes home", shipped().size() == 2 && shipped().get(0).reward.equals("item DRONE_RECOVERY"));

  // two of the same: one thrown away
  fly(3, false, "SCRAP_COLLECTOR", "SCRAP_COLLECTOR", "AUTO_COOLDOWN", "O2_MASKS");
  fly(4, false, "SCRAP_COLLECTOR", "AUTO_COOLDOWN", "O2_MASKS");
  Setup.chk("O: two of one augment: the copy thrown away comes home", shipped().size() == 3 && shipped().get(0).reward.equals("item SCRAP_COLLECTOR"));

  // at a store: it could have been sold there
  fly(4, true, "SCRAP_COLLECTOR", "AUTO_COOLDOWN", "O2_MASKS", "DRONE_RECOVERY");
  fly(5, false, "SCRAP_COLLECTOR", "AUTO_COOLDOWN", "O2_MASKS");
  Setup.chk("O: at a store, nothing is shipped", shipped().size() == 3);

  // three aboard and one gone: sold, or taken by an event, never shipped
  fly(6, false, "SCRAP_COLLECTOR", "O2_MASKS");
  Setup.chk("O: with three aboard, one gone is not shipped", shipped().size() == 3);

  // without the rule: lost, as in FTL
  HomePlanet.augmentsHome = false;
  fly(6, false, "SCRAP_COLLECTOR", "O2_MASKS", "AUTO_COOLDOWN", "DRONE_RECOVERY");
  fly(7, false, "SCRAP_COLLECTOR", "O2_MASKS", "AUTO_COOLDOWN");
  Setup.chk("O: with the rule off, it is lost", shipped().size() == 3 && Overflow.take(v).isEmpty());
  HomePlanet.augmentsHome = true;

  // without the inbox: straight to the Cargo Hold
  HomePlanet.immersiveNotifications = false;
  before = count("DRONE_RECOVERY");
  fly(7, false, "SCRAP_COLLECTOR", "O2_MASKS", "AUTO_COOLDOWN", "DRONE_RECOVERY");
  fly(8, false, "SCRAP_COLLECTOR", "O2_MASKS", "AUTO_COOLDOWN");
  Setup.chk("O: without the inbox, it goes straight to the Cargo Hold", count("DRONE_RECOVERY") == before + 1 && shipped().size() == 3);
  HomePlanet.immersiveNotifications = true;

  // the rule by difficulty
  CareerRules easy = CareerRules.of(CareerRules.EASY), normal = CareerRules.of(CareerRules.NORMAL), hard = CareerRules.of(CareerRules.HARD);
  Setup.chk("O: shipped home on Easy and Normal, lost on Hard", easy.augmentsHome() && normal.augmentsHome() && !hard.augmentsHome()
    && CareerRules.earlier(true).augmentsHome() && CareerRules.LEVELS.length == CareerRules.RULES.length);
  Setup.done();
 }
 /** FTL writes her save: these augments, at this many beacons past where she started, at a store or not; the station looks. */
 static int start = -1;
 static void fly(int jumps, boolean store, String... augs) throws Exception {
  SavedGameState gs = HomePlanet.savedGameParser.readSavedGame(v.continueFile());
  if (start < 0) start = gs.getTotalBeaconsExplored();
  gs.setTotalBeaconsExplored(start + jumps);
  // as FTL writes it (seen in FTL 1.6.14): three in her augment list; the one over capacity in the cargo list, twice,
  // beside whatever weapons or drones are in cargo
  List<String> all = Arrays.asList(augs);
  gs.getPlayerShip().getAugmentIdList().clear();
  gs.getPlayerShip().getAugmentIdList().addAll(all.subList(0, Math.min(Overflow.SLOTS, all.size())));
  gs.getCargoIdList().clear();
  gs.getCargoIdList().add("LASER_BURST_1");
  for (String over : all.subList(Math.min(Overflow.SLOTS, all.size()), all.size())) { gs.getCargoIdList().add(over); gs.getCargoIdList().add(over); }
  if (gs.getBeaconList().isEmpty()) gs.getBeaconList().add(new BeaconState());
  gs.setCurrentBeaconId(0);
  gs.getBeaconList().get(0).setStore(store ? new StoreState() : null);
  SaveHelper.writeSavedGame(v.continueFile(), gs);
  v.observeBoarded();
  Transmissions.check();
 }
 static List<Transmissions.Message> shipped() {
  List<Transmissions.Message> out = new ArrayList<Transmissions.Message>();
  for (Transmissions.Message m : Transmissions.load()) if (m.key.startsWith("shipped:")) out.add(m);
  return out;
 }
 static int count(String aug) throws IOException {
  int n = 0; for (String x : v.readCopy(v.storage()).save.getPlayerShip().getAugmentIdList()) if (x.equals(aug)) n++; return n;
 }
}
