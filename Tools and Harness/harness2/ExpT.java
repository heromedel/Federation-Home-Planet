import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Expeditions: the events file, the board, a run's choices and outcomes, what comes home, a beacon of time; and hiring. args: gamedir, world saves (from WorldT), work */
public class ExpT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 book();
 board(v);
 runs(v);
 stakes(v);
 asides(v);
 ships(v);
 lost(v);
 hiring(v);
 Setup.done();
}
 static final String[] KINDS = {"civilian", "engi", "zoltan", "mantis", "rock", "slug", "nebula", "pirate", "rebel", "abandoned", "engi_home", "mantis_home", "rock_home", "zoltan_home", "slug_home", "crystal"};
 static void book() throws Exception {
  Setup.chk("B: the events files read clean " + Expeditions.problems(), Expeditions.problems().isEmpty());
  Setup.chk("B: plenty of events (" + Expeditions.eventCount() + ")", Expeditions.eventCount() >= 90);
  java.lang.reflect.Method pool = Expeditions.class.getDeclaredMethod("pool", String.class); pool.setAccessible(true);
  boolean deep = true, own = true; String thin = "";
  for (String k : KINDS) {
   int n = ((List<?>) pool.invoke(null, k)).size(), o = Expeditions.ownEvents(k);
   int want = k.endsWith("_home") ? 3 : 6;
   if (n < 6) deep = false;
   if (o < want) { own = false; thin += k + "=" + o + " "; }
  }
  Setup.chk("B: every sector has at least six events of its own (a homeworld three, besides its race's) " + thin, own && deep);
  // risk grades: each common sector, with the general events, has enough of each risk for its postings
  boolean graded = true; String short_ = "";
  for (String k : KINDS) for (int r = 1; r <= 3; r++) {
   int n = 0; for (Object e : (List<?>) pool.invoke(null, k)) if (((Expeditions.Event) e).risk == r) n++;
   if (n < 2 && !k.endsWith("_home")) { graded = false; short_ += k + ":" + r + "=" + n + " "; }
  }
  Setup.chk("B: low, moderate and high risk events in every sector " + short_, graded);
  // multi-part events
  int chained = 0, deepest = 0, wide = 0;
  java.lang.reflect.Field steps = Expeditions.Event.class.getDeclaredField("steps"); steps.setAccessible(true);
  java.lang.reflect.Field evs = Class.forName("homeplanet.parser.Expeditions$Book").getDeclaredField("events"); evs.setAccessible(true);
  java.lang.reflect.Method bk = Expeditions.class.getDeclaredMethod("book"); bk.setAccessible(true);
  for (Object o : (List<?>) evs.get(bk.invoke(null))) {
   Expeditions.Event e = (Expeditions.Event) o; Map<?, ?> st = (Map<?, ?>) steps.get(e);
   if (st.size() > 1) chained++;
   deepest = Math.max(deepest, st.size());
   for (Object x : st.values()) if (((Expeditions.Step) x).choices.size() >= 4) wide++;
  }
  Setup.chk("B: multi-part events (" + chained + "), and pop-ups of four choices or more (" + wide + ")", chained >= 20 && wide >= 15);
  // a broken event is caught
  Object b = construct("homeplanet.parser.Expeditions$Book");
  java.lang.reflect.Method parse = Expeditions.class.getDeclaredMethod("parse", String.class, b.getClass(), String.class); parse.setAccessible(true);
  java.lang.reflect.Method check = Expeditions.class.getDeclaredMethod("check", b.getClass()); check.setAccessible(true);
  parse.invoke(null, "event x civilian low\nWords.\n* [rock] Only the Rock\n  ok then nowhere | fine\n* Anyone\n  roll 50\n  win | yes\nstep lonely\nNobody comes here.\n* A\n  ok | a\n* B\n  ok | b\n", b, "test");
  check.invoke(null, b);
  java.lang.reflect.Field pf = b.getClass().getDeclaredField("problems"); pf.setAccessible(true);
  List<?> probs = (List<?>) pf.get(b);
  Setup.chk("B: a broken event is caught: one way through without a race, a gamble with no losing side, a step that isn't there, one never reached " + probs, probs.size() == 4);
  // the words, held against FTL's own event text: no run of six words the same
  String ftl = new String(readAll(DataManager.get().getResourceInputStream("data/text_events.xml")), "UTF-8").replaceAll("<[^>]*>", " ");
  Set<String> shingles = new HashSet<String>(); List<String> w = words(ftl);
  for (int i = 0; i + 6 <= w.size(); i++) shingles.add(String.join(" ", w.subList(i, i + 6)));
  List<String> copied = new ArrayList<String>();
  for (String line : Expeditions.allWords()) { List<String> m = words(line); for (int i = 0; i + 6 <= m.size(); i++) if (shingles.contains(String.join(" ", m.subList(i, i + 6)))) { copied.add(String.join(" ", m.subList(i, i + 6))); break; } }
  System.out.println("expeditions: " + Expeditions.allWords().size() + " lines held against " + shingles.size() + " runs of six words in FTL's events");
  Setup.chk("B: no run of six words copied from FTL's events " + copied, copied.isEmpty());
 }
 static List<String> words(String s) { List<String> out = new ArrayList<String>(); for (String x : s.toLowerCase().replaceAll("\\{[a-z]+\\}", " ").split("[^a-z']+")) if (!x.isEmpty()) out.add(x); return out; }
 static byte[] readAll(InputStream in) throws IOException { ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[8192]; for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n); in.close(); return o.toByteArray(); }
 static Object construct(String cls) throws Exception { java.lang.reflect.Constructor<?> c = Class.forName(cls).getDeclaredConstructor(); c.setAccessible(true); return c.newInstance(); }
 static void board(Vault v) throws Exception {
  List<Expeditions.Posting> b = Expeditions.board(v);
  Setup.chk("P: three postings, all different", b.size() == 3 && !b.get(0).text.equals(b.get(1).text) && !b.get(1).text.equals(b.get(2).text) && !b.get(0).text.equals(b.get(2).text));
  List<Expeditions.Posting> again = Expeditions.board(v);
  Setup.chk("P: the board stays as it is until a job is done", again.get(0).text.equals(b.get(0).text) && again.get(2).text.equals(b.get(2).text));
  java.lang.reflect.Method pick = Expeditions.class.getDeclaredMethod("pick", Random.class, List.class); pick.setAccessible(true);
  int rare = 0; Random rng = new Random(4);
  for (int i = 0; i < 2000; i++) { Expeditions.Posting x = (Expeditions.Posting) pick.invoke(null, rng, new ArrayList<Expeditions.Posting>()); if (x.kind.endsWith("_home") || x.kind.equals("crystal")) rare++; }
  Setup.chk("P: about one posting in ten somewhere rare (" + rare + " of 2000)", rare > 120 && rare < 300);
  int sealed = 0; boolean hidden = true; Random sr = new Random(6);
  for (int i = 0; i < 2000; i++) { Expeditions.Posting x = (Expeditions.Posting) pick.invoke(null, sr, new ArrayList<Expeditions.Posting>());
   if (x.sealed) { sealed++; if (!x.sector().equals("Destination undisclosed") || !x.dangerWord().equals("Unknown") || x.realSector().equals(x.sector())) hidden = false; } }
  Setup.chk("P: about one posting in eight sealed (" + sealed + " of 2000), its sector and danger not shown", sealed > 170 && sealed < 340 && hidden);
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
 static void runs(Vault v) throws Exception {
  pinBoard(v); // a fixed board, so the runs don't depend on what was posted
  // what race options a party sees: a Rock's only with a Rock
  boolean rockOnly = true, sawRock = false, sawRed = false;
  List<CrewState> humans = hold(v, "human", "human");
  for (int s = 0; s < 300; s++) {
   Expeditions.Run r = Expeditions.start(v, s % 3, humans, new Random(s));
   while (!r.over()) { for (Expeditions.Choice c : r.choices()) if (c.race != null && !c.race.equals("human")) rockOnly = false; r.choose(r.choices().get(0)); }
  }
  Setup.chk("R: a party of humans sees no other race's options", rockOnly);
  // the odds: +5 for each crew member beyond the first, -3 for each injured one (hurt before or during), and who fights
  Expeditions.Choice gamble = null, fight = null;
  java.lang.reflect.Field rollF = Expeditions.Choice.class.getDeclaredField("roll"); rollF.setAccessible(true);
  for (Object o : (List<?>) evsOf()) for (Object st : ((Map<?, ?>) stepsOf(o)).values()) for (Expeditions.Choice c : ((Expeditions.Step) st).choices) {
   if (c.race == null && !c.fight && rollF.getInt(c) == 50 && gamble == null) gamble = c;
   if (c.race == null && c.fight && rollF.getInt(c) == 50 && fight == null) fight = c;
  }
  List<CrewState> one = hold(v, "human"), three = hold(v, "human", "human", "human");
  Expeditions.Event lowEv = event("harvest"); // a low-risk event: no penalty for danger
  int o1 = with(v, 0, one, lowEv, 1).odds(gamble), o3 = with(v, 0, three, lowEv, 1).odds(gamble);
  Vault.Copy hc = v.readCopy(v.storage()); for (CrewState x : hc.save.getPlayerShip().getCrewList()) x.setHealth(x.getHealth() / 2); v.begin().put(v.storage(), hc.save, hc.hash).commit();
  int o3hurt = with(v, 0, Expeditions.holdCrew(v), lowEv, 1).odds(gamble);
  Setup.chk("R: odds: 50 alone, 60 with three, 51 with three injured (" + o1 + ", " + o3 + ", " + o3hurt + ")", o1 == 50 && o3 == 60 && o3hurt == 51);
  int fm = with(v, 0, hold(v, "mantis", "mantis"), lowEv, 1).odds(fight), fe = with(v, 0, hold(v, "engi", "engi"), lowEv, 1).odds(fight);
  Setup.chk("R: in a fight, two Mantis help and two Engi hinder (" + fm + ", " + fe + ")", fm == 50 + 5 + 16 && fe == 50 + 5 - 10);
  Expeditions.Event highEv = event("asteroid_criminals"), modEv = event("fugitive");
  int oh = with(v, 0, hold(v, "human"), highEv, 1).odds(gamble), om = with(v, 0, hold(v, "human"), modEv, 1).odds(gamble);
  Setup.chk("R: dangerous events are harder: 5 off on moderate risk, 10 off on high (" + om + ", " + oh + ")", om == 45 && oh == 40);
  // a run never meets a theme twice
  boolean themesOk = true; List<CrewState> trio = hold(v, "human", "rock", "slug");
  for (int s = 0; s < 300; s++) { Expeditions.Run r = Expeditions.start(v, s % 3, trio, new Random(s)); Set<String> th = new HashSet<String>(); for (Expeditions.Event e : r.events()) if (!th.add(e.theme)) themesOk = false; }
  Setup.chk("R: no run meets the same theme twice", themesOk);
  // risk: a Low posting draws low-risk events, now and then one a level riskier
  int lowRisk = 0, other = 0;
  for (int s = 0; s < 300; s++) { Expeditions.Run r = Expeditions.start(v, 2, trio, new Random(s)); for (Expeditions.Event e : r.events()) if (e.risk == 1) lowRisk++; else other++; }
  Setup.chk("R: a Low posting meets mostly low-risk events (" + lowRisk + " low, " + other + " riskier)", lowRisk > other * 3 && other > 0);
  List<CrewState> mixed = hold(v, "rock", "mantis", "human");
  int total = 0, n = 0, min = 999, max = 0, over50 = 0, lostSome = 0, hurtSome = 0, gear = 0; boolean lengths = true;
  for (int s = 0; s < 400; s++) {
   Expeditions.Run r = Expeditions.start(v, s % 3, mixed, new Random(1000 + s));
   int len = r.length(); if (len < 2 || len > 3) lengths = false;
   Random pick = new Random(s);
   while (!r.over()) {
    List<Expeditions.Choice> cs = r.choices();
    for (Expeditions.Choice c : cs) { if ("rock".equals(c.race)) sawRock = true; if (c.red) sawRed = true; }
    String said = r.choose(cs.get(pick.nextInt(cs.size())));
    if (said.contains("{")) lengths = false; // a placeholder left in the words
   }
   java.lang.reflect.Field sf = r.getClass().getDeclaredField("scrap"); sf.setAccessible(true);
   java.lang.reflect.Method pay = r.getClass().getDeclaredMethod("pay"); pay.setAccessible(true);
   int got = (Integer) sf.get(r) + (Integer) pay.invoke(r);
   total += got; n++; min = Math.min(min, got); max = Math.max(max, got); if (got > 50) over50++;
   if (r.alive().size() < 3) lostSome++;
   java.lang.reflect.Field hf = r.getClass().getDeclaredField("hurt"); hf.setAccessible(true); if (!((Map<?, ?>) hf.get(r)).isEmpty()) hurtSome++;
   java.lang.reflect.Field itf = r.getClass().getDeclaredField("items"); itf.setAccessible(true); if (!((List<?>) itf.get(r)).isEmpty()) gear++;
  }
  System.out.println("expeditions: " + n + " runs, scrap " + min + "-" + max + " (average " + total / n + "), " + hurtSome + " with injuries, " + lostSome + " with losses, " + gear + " with gear");
  Setup.chk("R: two or three events each, every placeholder filled", lengths);
  Setup.chk("R: a Rock and a Mantis bring their options, blue and red", sawRock && sawRed);
  Setup.chk("R: modest pay: about 5 to 50 scrap, averaging 15 to 35, over 50 one time in ten at most (" + min + "-" + max + ", " + total / n + ", " + over50 + " over 50)",
    min >= 0 && max <= 90 && total / n >= 15 && total / n <= 35 && over50 * 10 <= n);
  Setup.chk("R: some come back hurt, some not at all, gear now and then", hurtSome > 20 && lostSome > 20 && gear > 5 && gear < 120);

  // a real one, finished: everything to the Cargo Hold in one write, a beacon of time, a new posting
  mixed = hold(v, "rock", "mantis", "human");
  Vault.Copy c = v.readCopy(v.storage()); c.save.getPlayerShip().setScrapAmt(10); v.begin().put(v.storage(), c.save, c.hash).commit();
  int beacons = v.beaconsSeen();
  String before = Expeditions.board(v).get(1).text;
  Expeditions.Run r = null; int seed = 0;
  for (; seed < 500; seed++) { // one where someone is hurt and someone lost, to see both recorded
   Expeditions.Run t = Expeditions.start(v, 1, mixed, new Random(seed)); Random pk = new Random(seed);
   while (!t.over()) { List<Expeditions.Choice> cs = t.choices(); t.choose(cs.get(pk.nextInt(cs.size()))); }
   java.lang.reflect.Field hf = t.getClass().getDeclaredField("hurt"); hf.setAccessible(true);
   if (t.alive().size() == 2) { r = t; break; }
  }
  if (r == null) { Setup.chk("R: found a run with a loss", false); return; }
  java.lang.reflect.Field hurtF = r.getClass().getDeclaredField("hurt"); hurtF.setAccessible(true);
  int hurtCount = 0; for (Object k : ((Map<?, ?>) hurtF.get(r)).keySet()) if (r.alive().contains(k)) hurtCount++;
  java.lang.reflect.Field sf = r.getClass().getDeclaredField("scrap"); sf.setAccessible(true);
  java.lang.reflect.Method pay = r.getClass().getDeclaredMethod("pay"); pay.setAccessible(true);
  int expect = 10 + (Integer) sf.get(r) + (Integer) pay.invoke(r);
  String summary = Expeditions.finish(v, r);
  ShipState h = v.readCopy(v.storage()).save.getPlayerShip();
  int injured = 0; for (CrewState x : h.getCrewList()) if (x.getHealth() < x.getRace().getMaxHealth()) injured++;
  Setup.chk("F: the scrap is in the Cargo Hold (" + h.getScrapAmt() + " of " + expect + ")", h.getScrapAmt() == expect);
  Setup.chk("F: one did not come back, the injured are hurt (" + injured + " of " + hurtCount + "): " + summary.replace("\n", " / "), h.getCrewList().size() == 2 && injured == hurtCount
    && summary.contains("Did not come back") && summary.contains("Injured") == (hurtCount > 0));
  Setup.chk("F: it took a beacon of the fleet's time", v.beaconsSeen() == beacons + 1);
  Properties bp = new Properties(); bp.load(new ByteArrayInputStream(SafeFiles.read(new File(v.root, "expeditions.txt"))));
  boolean remembered = true; for (Expeditions.Event e : r.events()) if (!bp.getProperty("recent", "").contains(e.id)) remembered = false;
  boolean avoided = true;
  for (int s = 0; s < 50; s++) { Expeditions.Run again = Expeditions.start(v, 1, mixed.subList(0, 1), new Random(s)); for (Expeditions.Event e : again.events()) if (r.events().contains(e)) avoided = false; }
  Setup.chk("F: its events are remembered, and not met again soon", remembered && avoided);
  Setup.chk("F: a new job in its place", !Expeditions.board(v).get(1).text.equals(before));
  String hist = new String(SafeFiles.read(HistoryLog.file()), "UTF-8");
  Setup.chk("F: the history log says who didn't come back", hist.contains("EXPEDITION") && hist.contains("did not come back"));
  // someone sent who has since left the hold: nothing changes
  List<CrewState> two = Expeditions.holdCrew(v);
  Expeditions.Run gone = Expeditions.start(v, 0, two, new Random(3));
  while (!gone.over()) gone.choose(gone.choices().get(0));
  hold(v, "engi");
  int scrap = v.storageScrap(); boolean refused = false;
  try { Expeditions.finish(v, gone); } catch (IOException e) { refused = e.getMessage().contains("no longer in the Cargo Hold"); }
  Setup.chk("F: crew who left the hold meanwhile: refused, nothing changed", refused && v.storageScrap() == scrap);
 }
 /** A fixed board: a dangerous job, a moderate one, a safe one. */
 static void pinBoard(Vault v) throws Exception {
  SafeFiles.writeText(new File(v.root, "expeditions.txt"), "0.kind=mantis\n0.danger=3\n0.text=Need mercenaries for defense through Mantis territory\n"
    + "1.kind=pirate\n1.danger=2\n1.text=Ransom to be delivered to a pirate den; steady nerves required\n"
    + "2.kind=civilian\n2.danger=1\n2.text=Hands wanted to escort a grain convoy between two farming colonies\n", false);
 }
 static List<?> evsOf() throws Exception {
  java.lang.reflect.Field evs = Class.forName("homeplanet.parser.Expeditions$Book").getDeclaredField("events"); evs.setAccessible(true);
  java.lang.reflect.Method bk = Expeditions.class.getDeclaredMethod("book"); bk.setAccessible(true);
  return (List<?>) evs.get(bk.invoke(null));
 }
 static Object stepsOf(Object e) throws Exception { java.lang.reflect.Field f = Expeditions.Event.class.getDeclaredField("steps"); f.setAccessible(true); return f.get(e); }
 /** Plan W: fatal injuries, the walking wounded, outfitted jobs, pay only for a job done, captives and ransoms. */
 static void stakes(Vault v) throws Exception {
  pinBoard(v);
  // an injury on a high-risk event is sometimes fatal; on a low-risk one, never
  Expeditions.Event high = event("asteroid_criminals"), low = event("harvest");
  Expeditions.Choice hiInj = null; for (Expeditions.Choice c : stepChoices(high, "")) if (c.text.startsWith("Slip past")) hiInj = c;
  int deaths = 0, hurts = 0;
  for (int s = 0; s < 2000; s++) {
   Expeditions.Run r = with(v, 0, hold(v, "human", "human"), high, s);
   java.lang.reflect.Field rf = Expeditions.Choice.class.getDeclaredField("roll"); rf.setAccessible(true);
   String said = r.choose(hiInj);
   if (said.contains("is injured")) hurts++; if (said.contains("worse than anyone knew")) deaths++;
  }
  Setup.chk("W: on a high-risk event, an injury is fatal more than half the time (" + deaths + " of " + (deaths + hurts) + ")", deaths > hurts && hurts > 0);
  // someone sent out injured, hurt again, is lost
  Vault.Copy hc = v.readCopy(v.storage()); hc.save.getPlayerShip().getCrewList().clear();
  CrewState hurtOne = Commission.volunteer("human", new Random(1)); hurtOne.setHealth(hurtOne.getHealth() / 2); SaveHelper.placeCrew(hc.save.getPlayerShip(), hurtOne, true); hc.save.getPlayerShip().getCrewList().add(hurtOne);
  v.begin().put(v.storage(), hc.save, hc.hash).commit();
  Expeditions.Choice lowHurt = null; for (Expeditions.Choice c : stepChoices(low, "")) if (c.text.startsWith("Try to repair")) lowHurt = c;
  Expeditions.Event recl = event("reclaimer"); Expeditions.Choice strip = null; for (Expeditions.Choice c : stepChoices(recl, "")) if (c.text.startsWith("Strip it down")) strip = c;
  boolean woundedLost = false;
  for (int s = 0; s < 500 && !woundedLost; s++) { Expeditions.Run r = with(v, 0, Expeditions.holdCrew(v), event("market_thief"), s);
   Expeditions.Choice chase = null; for (Expeditions.Choice c : r.choices()) if (c.text.startsWith("Give chase")) chase = c;
   String said = r.choose(chase); if (said.contains("hurt again")) woundedLost = r.alive().isEmpty(); }
  Setup.chk("W: someone sent out injured and hurt again doesn't come back, even on a low-risk job", woundedLost);
  // outfitted jobs: the card's price a head, paid; better odds and scrap; finish and the outfitting comes back 50-200%
  SafeFiles.writeText(new File(v.root, "expeditions.txt"), "0.kind=civilian\n0.danger=1\n0.outfit=10\n0.text=Outfitted crew wanted\n"
    + "1.kind=pirate\n1.danger=2\n1.text=b\n2.kind=civilian\n2.danger=1\n2.text=c\n", false);
  List<CrewState> two = hold(v, "human", "human");
  Vault.Copy c0 = v.readCopy(v.storage()); c0.save.getPlayerShip().setScrapAmt(15); v.begin().put(v.storage(), c0.save, c0.hash).commit();
  boolean refused = false; try { Expeditions.start(v, 0, two, new Random(1)); } catch (IOException e) { refused = e.getMessage().contains("20 scrap"); }
  Setup.chk("W: outfitting two at 10 a head needs 20 scrap: refused with 15 in the hold", refused);
  c0 = v.readCopy(v.storage()); c0.save.getPlayerShip().setScrapAmt(100); v.begin().put(v.storage(), c0.save, c0.hash).commit();
  int plain = with(v, 2, two, low, 1).odds(gamble()), kitted = with(v, 0, two, low, 1).odds(gamble());
  Setup.chk("W: outfitted crew get 10 on every gamble (" + plain + ", " + kitted + ")", kitted == plain + 10);
  long spent = 0, back = 0; int finished = 0, turned = 0, n = 0;
  for (int s = 0; s < 400; s++) {
   two = hold(v, "human", "human");
   Vault.Copy cc = v.readCopy(v.storage()); cc.save.getPlayerShip().setScrapAmt(1000); v.begin().put(v.storage(), cc.save, cc.hash).commit();
   SafeFiles.writeText(new File(v.root, "expeditions.txt"), "0.kind=civilian\n0.danger=1\n0.outfit=10\n0.text=Outfitted crew wanted\n1.kind=pirate\n1.danger=2\n1.text=b\n2.kind=civilian\n2.danger=1\n2.text=c\n", false);
   Expeditions.Run r = Expeditions.start(v, 0, two, new Random(s)); Random pk = new Random(s);
   while (!r.over()) { List<Expeditions.Choice> cs = r.choices(); r.choose(cs.get(pk.nextInt(cs.size()))); }
   java.lang.reflect.Field sf = r.getClass().getDeclaredField("scrap"); sf.setAccessible(true);
   java.lang.reflect.Method pay = r.getClass().getDeclaredMethod("pay"); pay.setAccessible(true);
   int gain = (Integer) sf.get(r) + (Integer) pay.invoke(r);
   Expeditions.finish(v, r);
   int got = v.storageScrap() - 1000 - gain + 20; // what the outfitting brought back
   n++; spent += 20; back += got;
   if (r.finished()) finished++; else { turned++; if (got != 0 || (Integer) pay.invoke(r) != 0) { Setup.chk("W: a job not finished pays nothing and returns nothing", false); return; } }
  }
  Setup.chk("W: finished, the outfitting comes back 50-200%; over many jobs about what was spent (" + back * 100 / spent + "%, " + finished + " finished, " + turned + " not)", back * 100 / spent >= 80 && back * 100 / spent <= 130 && turned > 0);
  // captives: taken, a ransom asked a few beacons later, paid: home; unpaid: lost for good
  java.lang.reflect.Method take = Expeditions.class.getDeclaredMethod("takeCaptive", Vault.class, CrewState.class, Expeditions.Posting.class); take.setAccessible(true);
  Expeditions.Posting pst = Expeditions.board(v).get(1);
  CrewState a = Commission.volunteer("rock", new Random(3)), b = Commission.volunteer("slug", new Random(4));
  take.invoke(null, v, a, pst); take.invoke(null, v, b, pst);
  Setup.chk("W: nothing is asked at once", Expeditions.ransoms(v).isEmpty());
  ChainT.jump(v, 5);
  List<Expeditions.Captive> asked = Expeditions.ransoms(v);
  Setup.chk("W: a few beacons later both ransoms are asked (" + asked.size() + ")", asked.size() == 2);
  int inHold = Expeditions.holdCrew(v).size(), scrap = v.storageScrap();
  Expeditions.Captive first = asked.get(0);
  Expeditions.payRansom(v, first);
  boolean home = false; for (CrewState x : Expeditions.holdCrew(v)) if (x.getName().equals(first.name)) home = true;
  Setup.chk("W: a ransom paid: " + first.name + " is back in the Cargo Hold, the ransom paid from it", home && Expeditions.holdCrew(v).size() == inHold + 1 && v.storageScrap() == scrap - first.ransom);
  ChainT.jump(v, Expeditions.RANSOM_STANDS + 1);
  String hist = new String(SafeFiles.read(HistoryLog.file()), "UTF-8");
  List<Expeditions.Captive> left = Expeditions.ransoms(v); hist = new String(SafeFiles.read(HistoryLog.file()), "UTF-8");
  Setup.chk("W: a ransom left unpaid runs out: lost for good, and the history log says so (" + left.size() + " left, beacons " + v.beaconsSeen() + ")", left.isEmpty() && hist.contains("ransom went unpaid"));
  // and some losses on real runs are captures
  java.lang.reflect.Field capF = Expeditions.Run.class.getDeclaredField("captured"); capF.setAccessible(true);
  int lostN = 0, capN = 0;
  for (int s = 0; s < 3000; s++) { Expeditions.Run r = with(v, 0, hold(v, "human", "human"), high, s); for (Expeditions.Choice c : r.choices()) if (c.text.startsWith("Hit the front")) { r.choose(c); break; } capN += ((List<?>) capF.get(r)).size(); lostN += 2 - r.alive().size(); }
  Setup.chk("W: about one loss in three is a capture (" + capN + " of " + lostN + ")", lostN > 200 && capN * 4 > lostN && capN * 2 < lostN);
  pinBoard(v);
 }
 static List<Expeditions.Choice> stepChoices(Expeditions.Event e, String step) throws Exception { return ((Expeditions.Step) ((Map<?, ?>) stepsOf(e)).get(step)).choices; }
 static Expeditions.Choice gamble() throws Exception {
  java.lang.reflect.Field rollF = Expeditions.Choice.class.getDeclaredField("roll"); rollF.setAccessible(true);
  for (Expeditions.Choice c : stepChoices(event("harvest"), "")) if (rollF.getInt(c) >= 0 && !c.fight && c.race == null) return c;
  return null;
 }
 /** A run made to meet this one event (the harness picks; the board's posting stays as it is). */
 static Expeditions.Run with(Vault v, int slot, List<CrewState> party, Expeditions.Event e, long seed) throws Exception {
  Expeditions.Run r = Expeditions.start(v, slot, party, new Random(seed));
  java.lang.reflect.Field f = Expeditions.Run.class.getDeclaredField("events"); f.setAccessible(true);
  List<Expeditions.Event> l = (List<Expeditions.Event>) f.get(r); l.clear(); l.add(e);
  return r;
 }
 static Expeditions.Event event(String id) throws Exception { for (Object o : evsOf()) if (((Expeditions.Event) o).id.equals(id)) return (Expeditions.Event) o; return null; }
 /** Asides: an extra line now and then, at its chance; the body soldier among them. */
 static void asides(Vault v) throws Exception {
  int n = 0; java.lang.reflect.Field ac = Class.forName("homeplanet.parser.Expeditions$Outcome").getDeclaredField("aside"); ac.setAccessible(true);
  for (Object o : evsOf()) for (Object st : ((Map<?, ?>) stepsOf(o)).values()) for (Expeditions.Choice c : ((Expeditions.Step) st).choices)
   for (String k : new String[] {"sure", "win", "lose"}) { java.lang.reflect.Field f = Expeditions.Choice.class.getDeclaredField(k); f.setAccessible(true); Object out = f.get(c); if (out != null && ac.get(out) != null) n++; }
  Setup.chk("A: asides across the events (" + n + ")", n >= 30);
  // the convoy raid's airlock fight, made to go wrong: the body soldier one time in ten
  Expeditions.Event raid = event("mantis_raid");
  List<CrewState> two = hold(v, "human", "human");
  int lost = 0, soldier = 0;
  for (int s = 0; s < 3000; s++) {
   Expeditions.Run r = with(v, 0, two, raid, s);
   Expeditions.Choice hold = null; for (Expeditions.Choice c : r.choices()) if (c.text.startsWith("Hold the airlocks")) hold = c;
   String said = r.choose(hold);
   if (said.contains("airlocks hold until one doesn't")) { lost++; if (said.contains("body soldier")) soldier++; }
  }
  Setup.chk("A: the body soldier turns up about one time in ten when the airlocks fail (" + soldier + " of " + lost + ")", lost > 500 && soldier * 100 > lost * 6 && soldier * 100 < lost * 14);
 }
 /** Ships home: a fitting model, in the condition her story says, to the Space Dock or the Junkyard as asked. */
 static void ships(Vault v) throws Exception {
  // every common sector, and the Hidden Crystal Worlds, has an event that can bring one home
  java.lang.reflect.Field shipF = Class.forName("homeplanet.parser.Expeditions$Outcome").getDeclaredField("ship"); shipF.setAccessible(true);
  Set<String> withShip = new HashSet<String>();
  for (Object o : evsOf()) { Expeditions.Event e = (Expeditions.Event) o; boolean has = false;
   for (Object st : ((Map<?, ?>) stepsOf(o)).values()) for (Expeditions.Choice c : ((Expeditions.Step) st).choices)
    for (String k : new String[] {"sure", "win", "lose"}) { java.lang.reflect.Field f = Expeditions.Choice.class.getDeclaredField(k); f.setAccessible(true); Object out = f.get(c); if (out != null && shipF.get(out) != null && !String.valueOf(shipF.get(out)).startsWith("stealth")) has = true; }
   if (has) { java.lang.reflect.Field kf = Expeditions.Event.class.getDeclaredField("kinds"); kf.setAccessible(true); withShip.addAll((Set<String>) kf.get(e)); } }
  List<String> missing = new ArrayList<String>();
  for (String k : KINDS) if (!k.endsWith("_home") && !withShip.contains(k)) missing.add(k);
  Setup.chk("S: every sector has an event that can bring a ship home " + missing, missing.isEmpty());
  // built to fit: the sector's models, and the condition
  Class<?> hs = Class.forName("homeplanet.parser.Expeditions$HomeShip");
  java.lang.reflect.Constructor<?> hc = hs.getDeclaredConstructor(String.class, String.class); hc.setAccessible(true);
  java.lang.reflect.Method build = Expeditions.class.getDeclaredMethod("build", hs, java.util.Collection.class, Random.class); build.setAccessible(true);
  java.lang.reflect.Method models = Expeditions.class.getDeclaredMethod("shipModels", String.class); models.setAccessible(true);
  boolean fits = true, limping = true, towed = true; String bad = "";
  for (String k : new String[] {"civilian", "engi", "zoltan", "mantis", "rock", "slug", "nebula", "pirate", "rebel", "abandoned", "crystal"}) for (int s = 0; s < 6; s++) {
   String sys = new String[] {"engines", "pilot", "oxygen", "shields", "sensors", "weapons"}[s];
   SavedGameParser.SavedGameState g = (SavedGameParser.SavedGameState) build.invoke(null, hc.newInstance(k + ":limping:" + sys, k), new ArrayList<String>(), new Random(s));
   String bp = Retrofit.vanillaId(g.getPlayerShip().getShipBlueprintId()); boolean ok = false;
   for (String m : (String[]) models.invoke(null, k)) if (bp.equals(m) || bp.startsWith(m + "_")) ok = true;
   if (!ok) { fits = false; bad += k + "=" + bp + " "; }
   SystemState st = g.getPlayerShip().getSystem(SystemType.findById(sys));
   if (st != null && st.getCapacity() > 0 && st.getDamagedBars() < st.getCapacity()) limping = false;
   SavedGameParser.SavedGameState t = (SavedGameParser.SavedGameState) build.invoke(null, hc.newInstance(k + ":towed", k), new ArrayList<String>(), new Random(100 + s));
   ShipState ts = t.getPlayerShip(); int max = DataManager.get().getShip(ts.getShipBlueprintId()).getHealth().amount;
   if (ts.getHullAmt() * 100 < max * 55 || !ts.getBreachMap().isEmpty()) towed = false;
  }
  Setup.chk("S: each sector's ship is one of its own models " + bad, fits);
  Setup.chk("S: a limping ship's failed system is broken through, or gone", limping);
  Setup.chk("S: a towed prize has most of her hull and no breaches", towed);
  // the lost expedition's cruiser: her own blueprint, near new; dented, some hull and a bar or two
  SavedGameParser.SavedGameState nw = (SavedGameParser.SavedGameState) build.invoke(null, hc.newInstance("stealth:new", "rebel"), new ArrayList<String>(), new Random(1));
  SavedGameParser.SavedGameState dn = (SavedGameParser.SavedGameState) build.invoke(null, hc.newInstance("stealth:dented", "rebel"), new ArrayList<String>(), new Random(1));
  int nb = 0, db = 0; for (SystemType t : SystemType.values()) { SystemState a1 = nw.getPlayerShip().getSystem(t), b1 = dn.getPlayerShip().getSystem(t); if (a1 != null) nb += a1.getDamagedBars(); if (b1 != null) db += b1.getDamagedBars(); }
  int smax = DataManager.get().getShip(nw.getPlayerShip().getShipBlueprintId()).getHealth().amount;
  Setup.chk("S: the Stealth Cruiser comes on her own blueprint (no companion mod), whole, weapons aboard (" + nw.getPlayerShip().getShipBlueprintId() + ")",
    nw.getPlayerShip().getShipBlueprintId().startsWith("PLAYER_SHIP_STEALTH") && !nw.getPlayerShip().getShipBlueprintId().endsWith(Retrofit.SUFFIX) && nb == 0
    && nw.getPlayerShip().getHullAmt() == smax && !nw.getPlayerShip().getWeaponList().isEmpty());
  Setup.chk("S: dented, she has lost some hull and a bar or two (" + dn.getPlayerShip().getHullAmt() + " of " + smax + ", " + db + " bars)",
    dn.getPlayerShip().getHullAmt() < smax && dn.getPlayerShip().getHullAmt() * 100 >= smax * 60 && db >= 1 && db <= 2);
  // a real one home: the Space Dock or the Junkyard, as asked
  Expeditions.Event pay = event("payment_in_kind");
  for (boolean dock : new boolean[] {true, false}) {
   List<CrewState> one = hold(v, "human");
   Expeditions.Run r = with(v, 0, one, pay, 4);
   r.choose(r.choices().get(0));
   r.ships().get(0).toDock = dock;
   int docked = v.docked().size(), junked = v.junked().size();
   Expeditions.finish(v, r);
   Ship home = r.ships().get(0).ship;
   Setup.chk("S: a ship home goes to the " + (dock ? "Space Dock" : "Junkyard") + " when asked (" + home.name + ")",
     home != null && (dock ? v.docked().size() == docked + 1 && home.state == Ship.State.DOCKED : v.junked().size() == junked + 1 && home.state == Ship.State.JUNKED));
  }
 }
 /** The lost expedition: very rare, weeks of waiting, one survivor home in a Stealth Cruiser, fire or wait. */
 static void lost(Vault v) throws Exception {
  java.lang.reflect.Field al = Expeditions.class.getDeclaredField("alwaysLost"); al.setAccessible(true);
  pinBoard(v); // finished expeditions above replaced postings: the Mantis job (High) back in the first place
  List<CrewState> three = hold(v, "human", "rock", "mantis");
  int gone = 0;
  for (int s = 0; s < 30000; s++) if (Expeditions.start(v, 0, three, new Random(s)).lostExpedition()) gone++; // slot 0: the Mantis job, High danger
  int low = 0; for (int s = 0; s < 3000; s++) if (Expeditions.start(v, 2, three, new Random(s)).lostExpedition()) low++;
  int alone = 0; List<CrewState> one = hold(v, "human"); for (int s = 0; s < 3000; s++) if (Expeditions.start(v, 0, one, new Random(s)).lostExpedition()) alone++;
  Setup.chk("L: about one high-danger expedition in 300 goes missing (" + gone + " of 30000); never a low one, never one crew member alone", gone > 60 && gone < 140 && low == 0 && alone == 0);
  al.setBoolean(null, true);
  Expeditions.Event saga = event("lost_expedition");
  int fireNew = 0, waitNew = 0;
  for (int s = 0; s < 2000; s++) for (int pick = 0; pick < 2; pick++) {
   Expeditions.Run r = with(v, 0, three, saga, s);
   r.choose(r.choices().get(0)); r.choose(r.choices().get(0)); // waiting; the rumour
   Expeditions.Choice c = r.choices().get(pick); // fire, or wait
   r.choose(c);
   if (r.ships().get(0).condition().equals("new")) { if (pick == 0) fireNew++; else waitNew++; }
  }
  Setup.chk("L: firing saves her one time in four, waiting three in four, whoever is aboard (" + fireNew + ", " + waitNew + " of 2000)",
    fireNew > 420 && fireNew < 580 && waitNew > 1420 && waitNew < 1580);
  // played through and home
  three = hold(v, "human", "rock", "mantis");
  int beacons = v.beaconsSeen();
  Expeditions.Run r = null; boolean met = false;
  for (int s = 0; s < 300 && !met; s++) { // one where someone lives to the end, and so meets the lost expedition's ending
   r = Expeditions.start(v, 0, three, new Random(s)); Random pk = new Random(s);
   while (!r.over()) { if (r.event() == saga) met = true; List<Expeditions.Choice> cs = r.choices(); r.choose(cs.get(pk.nextInt(cs.size()))); }
  }
  int alive = r.alive().size();
  for (Expeditions.HomeShip h : r.ships()) h.toDock = true;
  String summary = Expeditions.finish(v, r);
  Ship cruiser = null; for (Expeditions.HomeShip h : r.ships()) if (h.stealth()) cruiser = h.ship;
  List<CrewState> aboard = cruiser == null ? null : SaveHelper.getOwnCrew(cruiser.save().getPlayerShip());
  Setup.chk("L: played through: one comes home, aboard her, at the Space Dock; the others are lost; weeks pass (" + (v.beaconsSeen() - beacons) + " beacons)",
    met && alive == 1 && cruiser != null && cruiser.state == Ship.State.DOCKED && aboard.size() == 1 && Expeditions.holdCrew(v).size() == 0
    && v.beaconsSeen() - beacons == 5 && summary.contains("Did not come back"));
  al.setBoolean(null, false);
 }
 static void hiring(Vault v) throws Exception {
  Setup.chk("H: 5 a crew member, at most 60", Expeditions.hireCost(0) == 0 && Expeditions.hireCost(1) == 5 && Expeditions.hireCost(2) == 10 && Expeditions.hireCost(12) == 60 && Expeditions.hireCost(20) == 60);
  List<String> races = Expeditions.hireableRaces();
  Setup.chk("H: races from the unlocked ships, the Kestrel's among them " + races, races.contains("human"));
  int crew = Expeditions.fleetCrew(v);
  // crew in a Junkyard hull still count
  Ship d = v.docked().get(0); int aboard = SaveHelper.getOwnCrew(d.save().getPlayerShip()).size();
  v.board(d); v.disband();
  Setup.chk("H: crew stashed in the Junkyard still count (" + Expeditions.fleetCrew(v) + ")", Expeditions.fleetCrew(v) == crew && aboard > 0);
  Vault.Copy c = v.readCopy(v.storage()); c.save.getPlayerShip().setScrapAmt(500); v.begin().put(v.storage(), c.save, c.hash).commit();
  int cost = Expeditions.hireCost(crew), inHold = Expeditions.holdCrew(v).size();
  CrewState got = null; int paid = 0, tries = 0;
  for (int s = 0; got == null && s < 20; s++) { int before = v.storageScrap(); got = Expeditions.hire(v, new Random(s)); paid += before - v.storageScrap(); tries++; }
  Setup.chk("H: paid each time, answered or not (" + paid + " for " + tries + " at " + cost + ")", paid == cost * tries);
  Setup.chk("H: the volunteer waits in the Cargo Hold, of a hireable race", got != null && Expeditions.holdCrew(v).size() == inHold + 1 && races.contains(got.getRace().getId()));
  // no crew anywhere: the promise of adventure is free, and half the time someone comes
  for (Ship s : new ArrayList<Ship>(v.all())) {
   if (s == v.storage() || s.save() == null) continue;
   Vault.Copy sc = v.readCopy(s); sc.save.getPlayerShip().getCrewList().clear(); v.begin().put(s, sc.save, sc.hash).commit();
  }
  hold(v);
  Setup.chk("H: no crew anywhere: it's free", Expeditions.fleetCrew(v) == 0 && Expeditions.hireCost(Expeditions.fleetCrew(v)) == 0);
  int answered = 0, before = v.storageScrap();
  for (int s = 0; s < 200; s++) { if (Expeditions.hire(v, new Random(500 + s)) != null) answered++; hold(v); }
  Setup.chk("H: about half answer the promise (" + answered + " of 200), and it costs nothing", answered > 70 && answered < 130 && v.storageScrap() == before);
 }
}
