import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The crew expeditions' shared parts: the infirmary, ransoms, namesakes and hiring (the old board of jobs went at 5.67). args: gamedir, world saves (from WorldT), work */
public class ExpT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 care(v);
 ransoms(v);
 lateLook(v);
 namesakes(v);
 hiring(v);
 promise(v);
 Setup.done();
}
 /** Crew of these races, in the Cargo Hold (any there before are moved out of the way first). */
 static List<CrewState> hold(Vault v, String... races) throws Exception {
  Vault.Copy c = v.readCopy(v.storage()); ShipState h = c.save.getPlayerShip();
  h.getCrewList().clear();
  Random rng = new Random(1);
  for (String r : races) { CrewState x = Commission.volunteer(r, rng); SaveHelper.placeCrew(h, x, true); h.getCrewList().add(x); }
  v.begin().put(v.storage(), c.save, c.hash).commit();
  return Expeditions.holdCrew(v);
 }
 /** The station's care: in-game hurts heal after a beacon; the infirmary costs a point of skill a beacon; a clone bay a level; events give experience. */
 static void care(Vault v) throws Exception {
  List<CrewState> two = hold(v, "human", "human");
  Expeditions.checkInfirmary(v); // the station's last look is now
  Vault.Copy c = v.readCopy(v.storage()); ShipState h = c.save.getPlayerShip();
  CrewState a = h.getCrewList().get(0), b = h.getCrewList().get(1);
  a.setHealth(30); homeplanet.model.Skills.set(a, 0, 20); homeplanet.model.Skills.set(a, 4, 16); // a hurt pilot (level 1, 20/26) and repairer (level 1, 16/32)
  v.begin().put(v.storage(), c.save, c.hash).commit();
  Setup.chk("C: hurt in the game, nothing happens until a beacon passes (" + hp(v, a.getName()) + ")", Expeditions.checkInfirmary(v).isEmpty() && hp(v, a.getName()) == 30);
  ChainT.jump(v, 1);
  Expeditions.checkInfirmary(v);
  Setup.chk("C: a beacon later the station has healed her, no skill lost", hp(v, a.getName()) == 100 && homeplanet.model.Skills.points(crew(v, a.getName()), 0) == 20);
  String hist0 = Setup.stationLog(v);
  Setup.chk("C: and says so in the history log, in heromedel's words", hist0.contains(a.getName() + "'s visited The Station's Medbay"));
  // heromedel's case: beacons pass while she's away, then she arrives hurt: the station sees her, and heals her only a beacon later
  ChainT.jump(v, 2);
  Vault.Copy c2 = v.readCopy(v.storage()); for (CrewState x : c2.save.getPlayerShip().getCrewList()) if (x.getName().equals(b.getName())) x.setHealth(25);
  v.begin().put(v.storage(), c2.save, c2.hash).commit();
  Expeditions.checkInfirmary(v);
  boolean waits = hp(v, b.getName()) == 25;
  ChainT.jump(v, 1);
  Expeditions.checkInfirmary(v);
  Setup.chk("C: arriving hurt after beacons away: not healed at once (" + waits + "), healed a beacon after arriving (" + hp(v, b.getName()) + ")", waits && hp(v, b.getName()) == 100);
  // docked ships' crew too, a beacon after they're first seen; the boarded ship is never touched
  if (v.boarded() == null) v.board(v.docked().get(0));
  Ship dock = v.docked().get(0), aboard = v.boarded();
  Vault.Copy dc = v.readCopy(dock); CrewState dh = SaveHelper.getOwnCrew(dc.save.getPlayerShip()).get(0); dh.setHealth(20); v.write(dock, dc.save);
  Vault.Copy bc = v.readCopy(aboard); CrewState bh = SaveHelper.getOwnCrew(bc.save.getPlayerShip()).get(0); bh.setHealth(20); v.write(aboard, bc.save);
  v.takeStock();
  Expeditions.checkInfirmary(v);
  boolean dockWaits = shipHp(v, dock, dh.getName()) == 20;
  ChainT.jump(v, 1);
  Expeditions.checkInfirmary(v);
  Setup.chk("C: a docked ship's hurt crew: seen, then healed a beacon later (" + dockWaits + ", " + shipHp(v, dock, dh.getName()) + "); the boarded ship's left alone (" + shipHp(v, v.boarded(), bh.getName()) + ")",
    dockWaits && shipHp(v, dock, dh.getName()) == 100 && shipHp(v, v.boarded(), bh.getName()) == 20);
  ChainT.jump(v, 1);
  byte[] was = SafeFiles.read(v.storage().file());
  Expeditions.checkInfirmary(v);
  Setup.chk("C: a beacon with nobody hurt and nobody laid up: the Cargo Hold's file is left as it was", Arrays.equals(was, SafeFiles.read(v.storage().file())));
  // the infirmary: a point a beacon off a skill she has, and a level can go with it
  admit(v, a);
  int stay = Expeditions.infirmary(v).get(0).until - v.beaconsSeen();
  ChainT.jump(v, stay);
  Expeditions.checkInfirmary(v);
  CrewState after = crew(v, a.getName());
  int lostPts = 36 - homeplanet.model.Skills.points(after, 0) - homeplanet.model.Skills.points(after, 4);
  Setup.chk("C: " + stay + " beacons in the infirmary cost " + lostPts + " points of skill, from the skills she had (pilot " + homeplanet.model.Skills.points(after, 0) + ", repair " + homeplanet.model.Skills.points(after, 4) + ")",
    lostPts == stay && homeplanet.model.Skills.points(after, 2) == 0 && (homeplanet.model.Crew.skillLevels(after)[4] == 1) == (homeplanet.model.Skills.points(after, 4) >= 16));
  // someone laid up who leaves the Cargo Hold (retired, say) is let go quietly when their time is up: no word of them
  List<CrewState> pair = hold(v, "human", "human");
  String leaver = pair.get(0).getName();
  admit(v, pair.get(0));
  Vault.Copy lc = v.readCopy(v.storage()); for (Iterator<CrewState> it = lc.save.getPlayerShip().getCrewList().iterator(); it.hasNext(); ) if (it.next().getName().equals(leaver)) it.remove();
  v.begin().put(v.storage(), lc.save, lc.hash).commit();
  ChainT.jump(v, Expeditions.HEAL_MAX + 1);
  Setup.chk("C: " + leaver + " left the Cargo Hold while laid up: when their time is up, no word of them, and the infirmary is empty", Expeditions.checkInfirmary(v).isEmpty() && Expeditions.infirmary(v).isEmpty());
  // a clone bay: a level off each skill held; experience: points on, a level when they add up
  CrewState z = Commission.volunteer("human", new Random(9)); homeplanet.model.Skills.set(z, 5, 14); homeplanet.model.Skills.set(z, 0, 13); homeplanet.model.Skills.set(z, 3, 10);
  homeplanet.model.Skills.cloned(z);
  Setup.chk("C: cloned: combat 2 to 1 (points at the level's start), pilot 1 to 0, weapons stays 0 with its points", homeplanet.model.Crew.skillLevels(z)[5] == 1 && homeplanet.model.Skills.points(z, 5) == 7
    && homeplanet.model.Crew.skillLevels(z)[0] == 0 && homeplanet.model.Skills.points(z, 0) == 0 && homeplanet.model.Skills.points(z, 3) == 10);
  homeplanet.model.Skills.add(z, 3, 50);
  boolean lvl = homeplanet.model.Crew.skillLevels(z)[3] == 1 && homeplanet.model.Skills.points(z, 3) == 60;
  homeplanet.model.Skills.add(z, 3, 999);
  Setup.chk("C: 50 points of weapons on 10: level 1 (58), and never past the top", lvl && homeplanet.model.Skills.points(z, 3) == 116 && homeplanet.model.Crew.skillLevels(z)[3] == 2);
 }
 static CrewState crew(Vault v, String name) throws Exception { for (CrewState x : SaveHelper.getOwnCrew(v.readCopy(v.storage()).save.getPlayerShip())) if (x.getName().equals(name)) return x; return null; }
 static int hp(Vault v, String name) throws Exception { return crew(v, name).getHealth(); }
 static int shipHp(Vault v, Ship s, String name) throws Exception { for (CrewState x : SaveHelper.getOwnCrew(v.readCopy(s).save.getPlayerShip())) if (x.getName().equals(name)) return x.getHealth(); return -1; }
 /** Captives: taken; a letter a few beacons later ("one month", never beacons); a reminder near the end; paid: home; refused or run out: the Ambassador's letter. */
 static void ransoms(Vault v) throws Exception {
  hold(v, "human");
  Vault.Copy c0 = v.readCopy(v.storage()); c0.save.getPlayerShip().setScrapAmt(200); v.begin().put(v.storage(), c0.save, c0.hash).commit();
  CrewState a1 = Commission.volunteer("human", new Random(3)); String name = a1.getName();
  take(v, a1, "the rebels"); // taken, as the crew expeditions report it
  Setup.chk("W: " + name + " taken: nothing is asked at once", Expeditions.checkRansoms(v).isEmpty());
  CrewState b = Commission.volunteer("slug", new Random(4)), c3 = Commission.volunteer("engi", new Random(5));
  b.setPilotSkill(15); b.setPilotMasteryOne(true); b.setRepairs(42); // a record to come back with
  take(v, b); take(v, c3);
  ChainT.jump(v, 5);
  List<Expeditions.RansomNews> news = Expeditions.checkRansoms(v);
  int asks = 0; String words = ""; for (Expeditions.RansomNews x : news) { if (x.kind.equals("ask")) asks++; words += x.text(); }
  Setup.chk("W: a few beacons later all three ransoms are asked (" + asks + "), \"one month\", no beacons in the words, the rebels signing the moon's", asks == 3 && words.contains("one month") && !words.toLowerCase().contains("beacon") && words.contains("~ The rebels"));
  Expeditions.Captive pa = Expeditions.openRansom(v, "ransom:1"), pr = Expeditions.openRansom(v, "ransom:0"); // the slug, with a record, is paid for
  int inHold = Expeditions.holdCrew(v).size(), scrap = v.storageScrap();
  Expeditions.payRansom(v, pa);
  boolean home = false; for (CrewState x : Expeditions.holdCrew(v)) if (x.getName().equals(pa.name)) home = true;
  Setup.chk("W: a ransom paid: " + pa.name + " is back in the Cargo Hold, the ransom paid from it, and the letter's choices gone", home
    && Expeditions.holdCrew(v).size() == inHold + 1 && v.storageScrap() == scrap - pa.ransom && Expeditions.openRansom(v, "ransom:1") == null);
  CrewState backAgain = null; for (CrewState x : Expeditions.holdCrew(v)) if (x.getName().equals(pa.name)) backAgain = x;
  Setup.chk("W: " + pa.name + " comes back as they were taken: their skill, its mastery, their service record, whole", backAgain != null && backAgain.getPilotSkill() == 15 && backAgain.getPilotMasteryOne()
    && backAgain.getRepairs() == 42 && backAgain.getHealth() == backAgain.getRace().getMaxHealth());
  boolean twice = false; try { Expeditions.payRansom(v, pa); twice = true; } catch (IOException e) { }
  Setup.chk("W: a ransom paid can't be paid again", !twice);
  Expeditions.refuseRansom(v, pr);
  String hist = Setup.stationLog(v);
  Setup.chk("W: a ransom refused: presumed dead, in the history log, the choices gone", hist.contains(pr.name + ", taken by") && hist.contains("ransom was refused") && Expeditions.openRansom(v, "ransom:0") == null);
  Expeditions.Captive held = Expeditions.openRansom(v, "ransom:2");
  ChainT.jump(v, held.until - Expeditions.REMINDER_BEFORE - 1 - v.beaconsSeen());
  Setup.chk("W: a beacon before the reminder is due, nothing", Expeditions.checkRansoms(v).isEmpty());
  ChainT.jump(v, 1);
  news = Expeditions.checkRansoms(v);
  boolean reminded = news.size() == 1 && news.get(0).kind.equals("remind") && news.get(0).text().contains("running short") && !news.get(0).text().toLowerCase().contains("beacon");
  Setup.chk("W: near the end, a reminder for the one still held, and only then", reminded);
  ChainT.jump(v, Expeditions.REMINDER_BEFORE + 1);
  news = Expeditions.checkRansoms(v);
  hist = Setup.stationLog(v);
  Setup.chk("W: unpaid, it runs out: the Ambassador's letter (presumed dead), the history log, the choices gone", news.size() == 1 && news.get(0).kind.equals("lost")
    && news.get(0).text().contains("presumed dead") && news.get(0).text().contains(c3.getName()) && hist.contains("ransom went unpaid") && Expeditions.openRansom(v, "ransom:2") == null);
 }
 /** Two crew of one name and race (the infirmary's band-aid): the one hurt is the one laid up; the one lost is the one gone. */
 static void namesakes(Vault v) throws Exception {
  Expeditions.infirmaryFile(v).delete();
  List<CrewState> two = hold(v, "human", "human");
  Vault.Copy c = v.readCopy(v.storage()); List<CrewState> crew = c.save.getPlayerShip().getCrewList();
  crew.get(0).setName("Bob"); crew.get(0).setRepairs(7); crew.get(1).setName("Bob"); crew.get(1).setRepairs(8);
  v.begin().put(v.storage(), c.save, c.hash).commit();
  CrewState hurtBob = crew.get(0); int hurtRepairs = hurtBob.getRepairs();
  admit(v, hurtBob); // hurt on an expedition, as the crew expeditions report it
  List<CrewState> free = Expeditions.holdCrew(v);
  Setup.chk("N: two Bobs, one hurt: the other Bob (repairs " + (free.isEmpty() ? "?" : free.get(0).getRepairs()) + ") is free to send, the hurt one (" + hurtRepairs + ") laid up",
    free.size() == 1 && free.get(0).getRepairs() != hurtRepairs);
  Expeditions.infirmaryFile(v).delete();
  int lostRepairs = crew.get(1).getRepairs();
  Vault.Copy lc = v.readCopy(v.storage()); for (Iterator<CrewState> it = lc.save.getPlayerShip().getCrewList().iterator(); it.hasNext(); ) if (it.next().getRepairs() == lostRepairs) { it.remove(); break; }
  v.begin().put(v.storage(), lc.save, lc.hash).commit();
  List<CrewState> left = SaveHelper.getOwnCrew(v.readCopy(v.storage()).save.getPlayerShip());
  Setup.chk("N: two Bobs, one lost (repairs " + lostRepairs + "): the other one is the one still in the Cargo Hold", left.size() == 1 && left.get(0).getRepairs() != lostRepairs);
 }
 /** A station that isn't looked at for a long while: the ransom's month still runs from its letter. */
 static void lateLook(Vault v) throws Exception {
  CrewState late = Commission.volunteer("human", new Random(11));
  take(v, late);
  ChainT.jump(v, 40); // a long voyage without a look at the Space Dock
  List<Expeditions.RansomNews> news = Expeditions.checkRansoms(v);
  boolean asked = false, lost = false; for (Expeditions.RansomNews x : news) { if (x.captive.name.equals(late.getName()) && x.kind.equals("ask")) asked = true; if (x.captive.name.equals(late.getName()) && x.kind.equals("lost")) lost = true; }
  Expeditions.Captive open = null;
  for (int i = 0; i < 10 && open == null; i++) { Expeditions.Captive c = Expeditions.openRansom(v, "ransom:" + i); if (c != null && c.name.equals(late.getName())) open = c; }
  Setup.chk("W: seen late, the ransom is asked, not lost: its month runs from the letter", asked && !lost && open != null && open.until == v.beaconsSeen() + Expeditions.RANSOM_STANDS);
 }
 /** Holds a crew member as an expedition's foes would, a ransom to follow. */
 static void take(Vault v, CrewState c) throws Exception { take(v, c, "pirates"); }
 static void take(Vault v, CrewState c, String captors) throws Exception {
  File f = Expeditions.captivesFile(v); Properties p = Store.load(f);
  java.lang.reflect.Method take = Expeditions.class.getDeclaredMethod("takeCaptive", Properties.class, CrewState.class, String.class, int.class, Random.class); take.setAccessible(true);
  take.invoke(null, p, c, captors, v.beaconsSeen(), new Random(9));
  Store.write(f, p, null);
 }
 /** Admits a crew member to the infirmary, as the crew expeditions do for the hurt. */
 static void admit(Vault v, CrewState c) throws Exception {
  java.lang.reflect.Method m = Expeditions.class.getDeclaredMethod("admitAndTake", Vault.Transaction.class, Vault.class, List.class, List.class, String.class, int.class, Random.class); m.setAccessible(true);
  Vault.Transaction tx = v.begin();
  m.invoke(null, tx, v, new ArrayList<CrewState>(Collections.singletonList(c)), new ArrayList<CrewState>(), "pirates", v.beaconsSeen(), new Random(3));
  tx.commit();
 }
 static void hiring(Vault v) throws Exception {
  hold(v);
  Setup.chk("I: 5 scrap a crew member, at most 60, no scrap with none", Expeditions.hireCost(0) == 0 && Expeditions.hireCost(3) == 15 && Expeditions.hireCost(40) == 60);
  Setup.chk("Y: a promise of adventure costs 15 reputation with no crew anywhere, nothing with crew (" + Expeditions.fleetCrew(v) + " now)", Expeditions.PROMISE_REP == 15 && (Expeditions.fleetCrew(v) == 0 || Expeditions.promiseRep(v) == 0));
  int crew = Expeditions.fleetCrew(v), cost = Expeditions.hireCost(crew);
  Vault.Copy c0 = v.readCopy(v.storage()); c0.save.getPlayerShip().setScrapAmt(500); v.begin().put(v.storage(), c0.save, c0.hash).commit();
  int answered = 0, tries = 0; for (int s = 0; s < 40 && answered == 0; s++) { tries++; if (Expeditions.hire(v, new Random(s)) != null) answered++; }
  Setup.chk("I: a posting is answered now and then (" + tries + " tries), the volunteer in the Cargo Hold, the scrap paid each time", answered == 1 && Expeditions.holdCrew(v).size() == 1 && v.storageScrap() == 500 - cost * tries);
  List<String> races = Expeditions.hireableRaces();
  Setup.chk("I: volunteers come from the ships the commander has unlocked " + races, !races.isEmpty() && races.contains("human"));
 }
 /** No crew anywhere and no reputation to spare: a promise of adventure is never refused, and may take reputation below zero (heromedel, 5.13; the button and the posting refused it before 6.16). */
 static void promise(Vault v) throws Exception {
  boolean repWas = HomePlanet.reputationOn; HomePlanet.reputationOn = true;
  for (Ship s : v.all()) { // every crew member gone: ships, Junkyard and the Cargo Hold
   if (s.save() == null || !v.fileOf(s).isFile()) continue;
   Vault.Copy c = v.readCopy(s); c.save.getPlayerShip().getCrewList().removeAll(SaveHelper.getOwnCrew(c.save.getPlayerShip())); v.begin().put(s, c.save, c.hash).commit();
  }
  Reputation.spend(v, Reputation.total(v) + 3, "Test: down to -3");
  int before = Reputation.total(v);
  boolean refused = false; try { Expeditions.hire(v, new Random(1)); } catch (IOException e) { refused = true; System.out.println("  refused: " + e.getMessage()); }
  Setup.chk("Y: no crew, reputation " + before + ": the promise is posted all the same, 15 below (now " + Reputation.total(v) + ")", Expeditions.fleetCrew(v) <= 1 && !refused && Reputation.total(v) == before - 15);
  // 6.26 (heromedel): crew away on an expedition are the fleet's: sending everyone out doesn't make the promise free
  List<CrewState> two = hold(v, "human", "human");
  Assignments.send(v, Assignments.board(v).get(0).slot, two, new Random(5));
  Vault.Copy c = v.readCopy(v.storage()); c.save.getPlayerShip().setScrapAmt(100); v.begin().put(v.storage(), c.save, c.hash).commit();
  int rep0 = Reputation.total(v);
  Expeditions.hire(v, new Random(1));
  Setup.chk("Y: everyone away on an expedition: still crew (" + Expeditions.fleetCrew(v) + "), no promise; posting for volunteers costs 10 scrap (" + v.storageScrap() + " left)",
    Expeditions.holdCrew(v).size() <= 1 && Assignments.away(v).size() == 1 && Expeditions.promiseRep(v) == 0 && v.storageScrap() == 90 && Reputation.total(v) == rep0);
  HomePlanet.reputationOn = repWas;
 }
 /** For AsgT's check of the words against FTL's own: a stream read whole, and a line split into lower-case words. */
 static byte[] readAll(InputStream in) throws IOException { ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[8192]; for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n); in.close(); return o.toByteArray(); }
 static List<String> words(String s) { List<String> out = new ArrayList<String>(); for (String x : s.toLowerCase().replaceAll("\\{[a-z]+\\}", " ").split("[^a-z']+")) if (!x.isEmpty()) out.add(x); return out; }
}
