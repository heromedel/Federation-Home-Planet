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
  // a fixed board, so the runs don't depend on what was posted: a dangerous job, a moderate one, a safe one
  SafeFiles.writeText(new File(v.root, "expeditions.txt"), "0.kind=mantis\n0.danger=3\n0.text=Need body soldiers for defense through Mantis territory\n"
    + "1.kind=pirate\n1.danger=2\n1.text=Ransom to be delivered to a pirate den; steady nerves required\n"
    + "2.kind=civilian\n2.danger=1\n2.text=Hands wanted to escort a grain convoy between two farming colonies\n", false);
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
  int o1 = Expeditions.start(v, 0, one, new Random(1)).odds(gamble), o3 = Expeditions.start(v, 0, three, new Random(1)).odds(gamble);
  Vault.Copy hc = v.readCopy(v.storage()); for (CrewState x : hc.save.getPlayerShip().getCrewList()) x.setHealth(x.getHealth() / 2); v.begin().put(v.storage(), hc.save, hc.hash).commit();
  int o3hurt = Expeditions.start(v, 0, Expeditions.holdCrew(v), new Random(1)).odds(gamble);
  Setup.chk("R: odds: 50 alone, 60 with three, 51 with three injured (" + o1 + ", " + o3 + ", " + o3hurt + ")", o1 == 50 && o3 == 60 && o3hurt == 51);
  int fm = Expeditions.start(v, 0, hold(v, "mantis", "mantis"), new Random(1)).odds(fight), fe = Expeditions.start(v, 0, hold(v, "engi", "engi"), new Random(1)).odds(fight);
  Setup.chk("R: in a fight, two Mantis help and two Engi hinder (" + fm + ", " + fe + ")", fm == 50 + 5 + 16 && fe == 50 + 5 - 10);
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
  Setup.chk("R: some come back hurt, a few not at all, gear now and then", hurtSome > 20 && lostSome > 5 && lostSome < hurtSome && gear > 5 && gear < 120);

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
 static List<?> evsOf() throws Exception {
  java.lang.reflect.Field evs = Class.forName("homeplanet.parser.Expeditions$Book").getDeclaredField("events"); evs.setAccessible(true);
  java.lang.reflect.Method bk = Expeditions.class.getDeclaredMethod("book"); bk.setAccessible(true);
  return (List<?>) evs.get(bk.invoke(null));
 }
 static Object stepsOf(Object e) throws Exception { java.lang.reflect.Field f = Expeditions.Event.class.getDeclaredField("steps"); f.setAccessible(true); return f.get(e); }
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
