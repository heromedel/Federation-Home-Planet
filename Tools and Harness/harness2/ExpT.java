import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Expeditions: the events file, the board, a run's choices and outcomes, what comes home, the infirmary, ransoms; and hiring. args: gamedir, world saves (from WorldT), work */
public class ExpT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 book();
 board(v);
 runs(v);
 home(v);
 care(v);
 ransoms(v);
 lateLook(v);
 hiring(v);
 Setup.done();
}
 static void book() throws Exception {
  Setup.chk("B: the events file reads clean " + Expeditions.problems(), Expeditions.problems().isEmpty());
  Setup.chk("B: events for every kind of job (" + Expeditions.eventCount() + ")", Expeditions.eventCount() >= 12);
  // every choice has an outcome; a choice that can hurt can kill at least as often (a clone bay's return counts as a hurt:
  // its share comes out of the hurt's, never the death's)
  int rolled = 0, chained = 0, clones = 0; List<String> soft = new ArrayList<String>();
  java.lang.reflect.Field outs = Expeditions.Choice.class.getDeclaredField("outcomes"); outs.setAccessible(true);
  java.lang.reflect.Field steps = Expeditions.Event.class.getDeclaredField("steps"); steps.setAccessible(true);
  for (Expeditions.Event e : Expeditions.events()) {
   Map<?, ?> st = (Map<?, ?>) steps.get(e); if (st.size() > 1) chained++;
   for (Object x : st.values()) for (Expeditions.Choice c : ((Expeditions.Step) x).choices) {
    List<?> os = (List<?>) outs.get(c); if (os.size() > 1) rolled++;
    int hurt = 0, dead = 0;
    for (Object o : os) {
     int w = fieldInt(o, "weight");
     if (fieldInt(o, "injure") > 0 || fieldInt(o, "clone") > 0) hurt += w;
     if (fieldInt(o, "lose") > 0 || fieldInt(o, "taken") > 0) dead += w;
     if (fieldInt(o, "clone") > 0) clones++;
    }
    if (dead < hurt) soft.add(e.id + ": " + c.text);
   }
  }
  Setup.chk("B: rolled choices (" + rolled + "), small chains (" + chained + "), clone bays (" + clones + "); every choice that can hurt kills at least as often " + soft,
    rolled >= 10 && chained >= 2 && clones >= 3 && soft.isEmpty());
  // a broken event is caught
  Object b = construct("homeplanet.parser.Expeditions$Book");
  java.lang.reflect.Method parse = Expeditions.class.getDeclaredMethod("parse", String.class, b.getClass(), String.class); parse.setAccessible(true);
  java.lang.reflect.Method check = Expeditions.class.getDeclaredMethod("check", b.getClass()); check.setAccessible(true);
  parse.invoke(null, "event x rescue\nWords.\n* [rock] Only the Rock\n  = then nowhere | fine\n* Anyone\nstep lonely\n* A\n  = | a\n"
    + "event y rescue\nMore words.\n* {who} does it\n  = | done\n* [fight] Fight it\n  = xp pilot 3 | fought\n* [pilot] Fly it\n  = xp pilot 3 | flown\n", b, "test");
  check.invoke(null, b);
  java.lang.reflect.Field pf = b.getClass().getDeclaredField("problems"); pf.setAccessible(true);
  List<?> probs = (List<?>) pf.get(b);
  String all = probs.toString();
  Setup.chk("B: a broken event is caught: a choice with no outcome, a step that isn't there, one never reached " + probs,
    all.contains("has no outcome") && all.contains("no step nowhere") && all.contains("step lonely is never reached"));
  Setup.chk("B: and an anyone's button that names someone, and experience in a skill the choice doesn't take (but not the one that does)",
    all.contains("\"{who} does it\" names someone") && all.contains("\"Fight it\" gives pilot experience") && !all.contains("\"Fly it\""));
  // the words, held against FTL's own event text: no run of six words the same
  String ftl = new String(readAll(DataManager.get().getResourceInputStream("data/text_events.xml")), "UTF-8").replaceAll("<[^>]*>", " ");
  Set<String> shingles = new HashSet<String>(); List<String> w = words(ftl);
  for (int i = 0; i + 6 <= w.size(); i++) shingles.add(String.join(" ", w.subList(i, i + 6)));
  List<String> copied = new ArrayList<String>();
  for (String line : Expeditions.allWords()) { List<String> m = words(line); for (int i = 0; i + 6 <= m.size(); i++) if (shingles.contains(String.join(" ", m.subList(i, i + 6)))) { copied.add(String.join(" ", m.subList(i, i + 6))); break; } }
  System.out.println("expeditions: " + Expeditions.allWords().size() + " lines held against " + shingles.size() + " runs of six words in FTL's events");
  Setup.chk("B: no run of six words copied from FTL's events " + copied, copied.isEmpty());
 }
 static int fieldInt(Object o, String name) throws Exception { java.lang.reflect.Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.getInt(o); }
 static List<String> words(String s) { List<String> out = new ArrayList<String>(); for (String x : s.toLowerCase().replaceAll("\\{[a-z]+\\}", " ").split("[^a-z']+")) if (!x.isEmpty()) out.add(x); return out; }
 static byte[] readAll(InputStream in) throws IOException { ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[8192]; for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n); in.close(); return o.toByteArray(); }
 static Object construct(String cls) throws Exception { java.lang.reflect.Constructor<?> c = Class.forName(cls).getDeclaredConstructor(); c.setAccessible(true); return c.newInstance(); }
 static void board(Vault v) throws Exception {
  List<Expeditions.Posting> b = Expeditions.board(v);
  Setup.chk("P: three postings, all different, of three kinds", b.size() == 3 && !b.get(0).text.equals(b.get(1).text) && !b.get(1).text.equals(b.get(2).text) && !b.get(0).text.equals(b.get(2).text)
    && !b.get(0).kind.equals(b.get(1).kind) && !b.get(1).kind.equals(b.get(2).kind) && !b.get(0).kind.equals(b.get(2).kind));
  List<Expeditions.Posting> again = Expeditions.board(v);
  Setup.chk("P: looking again, the board is the same", again.get(0).text.equals(b.get(0).text) && again.get(2).text.equals(b.get(2).text));
  // untaken postings come down after 1-7 beacons each (hidden), and others take their place
  Properties bp0 = new Properties(); bp0.load(new ByteArrayInputStream(SafeFiles.read(new File(v.root, "expeditions.txt"))));
  int now = v.beaconsSeen(); boolean ranged = true; int soonest = 999;
  for (int i = 0; i < 3; i++) { int u = Integer.parseInt(bp0.getProperty(i + ".until")); if (u - now < 1 || u - now > 7) ranged = false; soonest = Math.min(soonest, u); }
  Setup.chk("P: each posting stays 1 to 7 beacons", ranged);
  ChainT.jump(v, soonest - now - 1);
  List<Expeditions.Posting> before = Expeditions.board(v);
  ChainT.jump(v, 1);
  List<Expeditions.Posting> after = Expeditions.board(v);
  Properties bp1 = new Properties(); bp1.load(new ByteArrayInputStream(SafeFiles.read(new File(v.root, "expeditions.txt"))));
  int changed = 0, kept = 0;
  for (int i = 0; i < 3; i++) { int u0 = Integer.parseInt(bp0.getProperty(i + ".until")); if (u0 == soonest) { if (!after.get(i).text.equals(before.get(i).text) || Integer.parseInt(bp1.getProperty(i + ".until")) > soonest) changed++; } else if (after.get(i).text.equals(before.get(i).text)) kept++; }
  int due = 0; for (int i = 0; i < 3; i++) if (Integer.parseInt(bp0.getProperty(i + ".until")) == soonest) due++;
  Setup.chk("P: when its time comes a posting is replaced (" + changed + " of " + due + "); the others stay (" + kept + " of " + (3 - due) + ")", changed == due && kept == 3 - due);
  // each posting plays the event it was written for, and signing on takes it off the board
  java.lang.reflect.Field ef = Expeditions.Posting.class.getDeclaredField("event"); ef.setAccessible(true);
  List<CrewState> one = hold(v, "human");
  boolean matched = true, offBoard = true; int tried = 0;
  for (int k = 0; k < 30; k++) {
   new File(v.root, "expeditions.txt").delete();
   List<Expeditions.Posting> fresh = Expeditions.board(v);
   for (int i = 0; i < 3; i++) {
    Expeditions.Posting x = Expeditions.board(v).get(i);
    Expeditions.Run r = Expeditions.start(v, i, one, new Random(k));
    tried++;
    if (ef.get(x) == null || !r.event().id.equals(ef.get(x))) matched = false;
    Expeditions.Posting now2 = Expeditions.board(v).get(i);
    if (now2.text.equals(x.text) || !Expeditions.recentEvents(v).contains(r.event().id)) offBoard = false;
   }
  }
  Setup.chk("P: every posting plays the event written for it (" + tried + " sign-ons)", matched);
  Setup.chk("P: signing on takes the job off the board at once, its event remembered: closing the station mid-job can't play it again", offBoard);
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
 /** These crew, in the Cargo Hold (any there before are moved out of the way first). */
 static List<CrewState> holdOf(Vault v, CrewState... crew) throws Exception {
  Vault.Copy c = v.readCopy(v.storage()); ShipState h = c.save.getPlayerShip();
  h.getCrewList().clear();
  for (CrewState x : crew) { SaveHelper.placeCrew(h, x, true); h.getCrewList().add(x); }
  v.begin().put(v.storage(), c.save, c.hash).commit();
  return Expeditions.holdCrew(v);
 }
 /** A fixed board, so the runs don't depend on what was posted. */
 static String[] pinned;
 /** Signs on to the pinned board's first job (signing on takes it off the board: pinned again first, for the next try). */
 static Expeditions.Run start(Vault v, List<CrewState> crew, Random rng) throws Exception {
  if (pinned != null) pinBoard(v, pinned);
  return Expeditions.start(v, 0, crew, rng);
 }
 static void pinBoard(Vault v, String... events) throws Exception {
  pinned = events;
  StringBuilder sb = new StringBuilder();
  for (int i = 0; i < 3; i++) sb.append(i).append(".kind=").append(Expeditions.events().get(0).kind).append("\n").append(i).append(".until=999999\n").append(i).append(".text=job ").append(i).append("\n").append(i).append(".event=").append(events[i % events.length]).append("\n");
  SafeFiles.writeText(new File(v.root, "expeditions.txt"), sb.toString(), false);
 }
 static void runs(Vault v) throws Exception {
  // every event, every choice, played through many times: no crash, every screen has words and choices
  List<CrewState> mixed = hold(v, "human", "rock", "mantis");
  boolean words = true, numbered = true; int screens = 0;
  for (Expeditions.Event e : Expeditions.events()) {
   pinBoard(v, e.id);
   for (int s = 0; s < 40; s++) {
    Expeditions.Run r = start(v, mixed, new Random(s)); Random pk = new Random(s);
    while (!r.over()) {
     List<Expeditions.Choice> cs = r.choices(); screens++;
     if (r.text().trim().isEmpty() || cs.isEmpty()) words = false;
     if (r.text().contains("{") || r.label(cs.get(0)).contains("{")) numbered = false;
     r.choose(cs.get(pk.nextInt(cs.size())));
    }
    if (r.text().trim().isEmpty() || r.text().contains("{")) words = false;
   }
  }
  Setup.chk("R: every event plays through from every choice (" + screens + " screens), every screen with words, names filled in", words && numbered);
  // a party of humans sees no other race's options; a Rock's open with a Rock
  List<CrewState> humans = hold(v, "human", "human");
  boolean humanOnly = true, sawRock = false;
  pinBoard(v, "rock_shaft");
  for (Expeditions.Choice c : start(v, humans, new Random(1)).choices()) if (c.race != null) humanOnly = false;
  for (Expeditions.Choice c : start(v, hold(v, "rock"), new Random(1)).choices()) if ("rock".equals(c.race)) sawRock = true;
  Setup.chk("R: a party of humans sees no race's options; a Rock's open with a Rock along", humanOnly && sawRock);
  // who takes a choice on, and how much safer they make it: the relay's "find the fault yourselves" wants an Engi (tech)
  // and repair; the best suited does it, takes its risk first, and earns its experience
  pinBoard(v, "engi_relay");
  java.lang.reflect.Method fit = Expeditions.Run.class.getDeclaredMethod("fit", Expeditions.Choice.class); fit.setAccessible(true);
  java.lang.reflect.Method doer = Expeditions.Run.class.getDeclaredMethod("doer", Expeditions.Choice.class); doer.setAccessible(true);
  CrewState plain = Commission.volunteer("human", new Random(11)), fixer = Commission.volunteer("human", new Random(12)), engi = Commission.volunteer("engi", new Random(13)), ace = Commission.volunteer("engi", new Random(14));
  plain.setName("Ada Plain"); fixer.setName("Bo Fixer"); engi.setName("Cog"); ace.setName("Dial"); // names apart, whatever the dice gave
  homeplanet.model.Skills.set(fixer, 4, 20); homeplanet.model.Skills.set(ace, 4, 36); // repair: a human at level 1, an Engi at level 2
  Expeditions.Run r0 = start(v, holdOf(v, plain), new Random(1));
  Expeditions.Choice tech = null, told = null; for (Expeditions.Choice c : r0.choices()) { if (c.text.startsWith("Go into the reactor room")) tech = c; if (c.text.startsWith("Do exactly")) told = c; }
  int fPlain = (Integer) fit.invoke(r0, tech);
  Expeditions.Run r1 = start(v, holdOf(v, plain, fixer), new Random(1));
  int fFixer = (Integer) fit.invoke(r1, tech); String d1 = ((CrewState) doer.invoke(r1, tech)).getName();
  Expeditions.Run r2 = start(v, holdOf(v, fixer, engi), new Random(1));
  int fEngi = (Integer) fit.invoke(r2, tech); String d2 = ((CrewState) doer.invoke(r2, tech)).getName();
  Expeditions.Run r3 = start(v, holdOf(v, engi, ace, plain), new Random(1));
  int fAce = (Integer) fit.invoke(r3, tech); String d3 = ((CrewState) doer.invoke(r3, tech)).getName();
  Setup.chk("R: the reactor room's risk: anyone " + fPlain + "%, a level 1 repairer " + fFixer + "%, an Engi " + fEngi + "%, an Engi at level 2 " + fAce + "%",
    fPlain == 100 && fFixer == 80 && fEngi == 50 && fAce == 30);
  Setup.chk("R: the best suited takes it on: the repairer over a beginner (" + d1 + "), an Engi over a level 1 human (" + d2 + "), the skilled Engi over the other (" + d3 + ")",
    d1.equals(fixer.getName()) && d2.equals(engi.getName()) && d3.equals(ace.getName()));
  // the experience is theirs: doing as the attendant says, the level 1 repairer does it, not the beginner
  Expeditions.Run r4 = start(v, holdOf(v, plain, fixer), new Random(2));
  r4.choose(told); Expeditions.finish(v, r4);
  Setup.chk("R: the repairer earns the repair experience (" + homeplanet.model.Skills.points(crew(v, fixer.getName()), 4) + "), the beginner none (" + homeplanet.model.Skills.points(crew(v, plain.getName()), 4) + ")",
    homeplanet.model.Skills.points(crew(v, fixer.getName()), 4) == 22 && homeplanet.model.Skills.points(crew(v, plain.getName()), 4) == 0);
  // a race's option names on its button the one who takes it on, and the outcome is about the same one
  pinBoard(v, "mantis_raider");
  CrewState m1 = Commission.volunteer("mantis", new Random(21)), m2 = Commission.volunteer("mantis", new Random(22));
  m1.setName("Kriss"); m2.setName("Vetch");
  boolean same = true;
  for (int s = 0; s < 30; s++) {
   Expeditions.Run r = start(v, holdOf(v, m1, m2), new Random(s));
   Expeditions.Choice mc = null; for (Expeditions.Choice c : r.choices()) if ("mantis".equals(c.race)) mc = c;
   String named = r.label(mc).replace("(Mantis) ", "").split(" says")[0];
   if (!r.choose(mc).contains(named)) same = false;
  }
  Setup.chk("R: a race's option: the button and the outcome name the same crew member", same);
  // over many runs of a risky choice: deaths outnumber injuries, and a lone crew member can be lost (the commander goes on alone)
  pinBoard(v, "rock_shaft");
  int dead = 0, hurt = 0, fine = 0, alone = 0;
  for (int s = 0; s < 600; s++) {
   Expeditions.Run r = start(v, hold(v, "human"), new Random(s));
   Expeditions.Choice c = null; for (Expeditions.Choice x : r.choices()) if (x.text.startsWith("Go down the vent")) c = x;
   r.choose(c);
   if (r.alive().isEmpty()) { dead++; alone++; } else if (!fieldList(r, "hurt").isEmpty()) hurt++; else fine++;
   if (!r.over()) while (!r.over()) r.choose(r.choices().get(0));
  }
  Setup.chk("R: the vent over 600 tries: " + fine + " fine, " + hurt + " hurt, " + dead + " dead; death the likelier bad end, and the run goes on without crew", dead > hurt && fine > dead && alone > 0);
 }
 static List<?> fieldList(Object o, String name) throws Exception { java.lang.reflect.Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return (List<?>) f.get(o); }
 /** Home: what came of it to the Cargo Hold, the hurt to the infirmary (and out again), the dead gone, a beacon, the history log, a new posting, the event remembered. */
 static void home(Vault v) throws Exception {
  pinBoard(v, "rock_shaft", "belt_freighter", "ice_moon");
  List<CrewState> two = hold(v, "human", "human");
  Vault.Copy c0 = v.readCopy(v.storage()); c0.save.getPlayerShip().setScrapAmt(100); v.begin().put(v.storage(), c0.save, c0.hash).commit();
  String name0 = two.get(0).getName(), name1 = two.get(1).getName();
  // a run where the first choice paid and nobody was hurt
  Expeditions.Run r = null;
  for (int s = 0; s < 200 && r == null; s++) { Expeditions.Run t = start(v, Expeditions.holdCrew(v), new Random(s)); t.choose(t.choices().get(0)); if (t.over() && t.alive().size() == 2) r = t; }
  int beacons = v.beaconsSeen(); String old = "job 0"; // signing on took it off the board
  int got = fieldInt(r, "scrap");
  String end = Expeditions.finish(v, r);
  Setup.chk("H: home: the scrap to the Cargo Hold (" + got + "), a beacon passed, nothing more to say (the outcome said it), a new posting in its place", v.storageScrap() == 100 + got && got > 0
    && v.beaconsSeen() == beacons + 1 && end.isEmpty() && !Expeditions.board(v).get(0).text.equals(old));
  Properties bp = new Properties(); bp.load(new ByteArrayInputStream(SafeFiles.read(new File(v.root, "expeditions.txt"))));
  Setup.chk("H: the event is remembered, not to be met again soon", bp.getProperty("recent", "").contains("rock_shaft"));
  // a run where one was hurt and the other killed
  pinBoard(v, "rock_shaft");
  Expeditions.Run bad = null;
  for (int s = 0; s < 5000 && bad == null; s++) {
   Expeditions.Run t = start(v, Expeditions.holdCrew(v), new Random(s));
   Expeditions.Choice vent = null; for (Expeditions.Choice x : t.choices()) if (x.text.startsWith("Go down the vent")) vent = x;
   t.choose(vent);
   if (!fieldList(t, "hurt").isEmpty()) { // one hurt: send the other down the vent of a second run? no: one run, one choice; take a hurt one and kill the other by a lost outcome elsewhere
    bad = t;
   }
  }
  String hurtName = ((CrewState) fieldList(bad, "hurt").get(0)).getName();
  int before = Expeditions.holdCrew(v).size();
  end = Expeditions.finish(v, bad);
  List<String> canGo = new ArrayList<String>(); for (CrewState x : Expeditions.holdCrew(v)) canGo.add(x.getName());
  List<Expeditions.Patient> inf = Expeditions.infirmary(v);
  Setup.chk("H: " + hurtName + " hurt: to the infirmary (" + inf.size() + "), not among those who can be sent (" + canGo + "), the docking screen says so", end.equals(hurtName + " is carried to the infirmary when the shuttle docks.")
    && inf.size() == 1 && inf.get(0).name.equals(hurtName) && !canGo.contains(hurtName) && canGo.size() == before - 1);
  Setup.chk("H: the infirmary keeps them " + Expeditions.HEAL_MIN + " to " + Expeditions.HEAL_MAX + " beacons", inf.get(0).until - v.beaconsSeen() >= Expeditions.HEAL_MIN && inf.get(0).until - v.beaconsSeen() <= Expeditions.HEAL_MAX);
  ChainT.jump(v, inf.get(0).until - v.beaconsSeen() - 1);
  Setup.chk("H: a beacon early, still laid up", Expeditions.checkInfirmary(v).isEmpty() && Expeditions.infirmary(v).size() == 1);
  ChainT.jump(v, 1);
  List<String> up = Expeditions.checkInfirmary(v);
  boolean whole = false; for (CrewState x : Expeditions.holdCrew(v)) if (x.getName().equals(hurtName) && x.getHealth() == x.getRace().getMaxHealth()) whole = true;
  Setup.chk("H: when their time is up they're out, whole, and can be sent again (" + up + ")", up.size() == 1 && up.get(0).equals(hurtName) && whole && Expeditions.infirmary(v).isEmpty());
  // a death: gone from the Cargo Hold, in the history log
  pinBoard(v, "rock_shaft");
  Expeditions.Run death = null;
  for (int s = 0; s < 5000 && death == null; s++) {
   Expeditions.Run t = start(v, Expeditions.holdCrew(v), new Random(s));
   Expeditions.Choice vent = null; for (Expeditions.Choice x : t.choices()) if (x.text.startsWith("Go down the vent")) vent = x;
   t.choose(vent);
   if (!fieldList(t, "lost").isEmpty()) death = t;
  }
  String deadName = ((CrewState) fieldList(death, "lost").get(0)).getName();
  before = Expeditions.holdCrew(v).size();
  end = Expeditions.finish(v, death);
  boolean gone = true; for (CrewState x : Expeditions.holdCrew(v)) if (x.getName().equals(deadName)) gone = false;
  String hist = new String(SafeFiles.read(HistoryLog.file()), "UTF-8");
  Setup.chk("H: " + deadName + " dead: gone from the Cargo Hold, in the history log, and no more said of it (the outcome said it)", gone && Expeditions.holdCrew(v).size() == before - 1 && !end.contains(deadName) && hist.contains("did not come back: " + deadName));
  // a party no longer in the hold is refused, nothing changed
  List<CrewState> party = hold(v, "human", "human");
  Expeditions.Run stale = start(v, party, new Random(3)); while (!stale.over()) stale.choose(stale.choices().get(0));
  hold(v, "engi");
  boolean refused = false; try { Expeditions.finish(v, stale); } catch (IOException e) { refused = e.getMessage().contains("no longer in the Cargo Hold"); }
  Setup.chk("H: crew no longer in the Cargo Hold: the expedition can't be recorded, nothing changed", refused);
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
  String hist0 = new String(SafeFiles.read(HistoryLog.file()), "UTF-8");
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
  pinBoard(v, "rock_shaft");
  Expeditions.Run bad = null;
  for (int s = 0; s < 5000 && bad == null; s++) {
   Expeditions.Run t = start(v, Expeditions.holdCrew(v), new Random(s));
   Expeditions.Choice vent = null; for (Expeditions.Choice x : t.choices()) if (x.text.startsWith("Go down the vent")) vent = x;
   t.choose(vent);
   if (!fieldList(t, "hurt").isEmpty() && ((CrewState) fieldList(t, "hurt").get(0)).getName().equals(a.getName())) bad = t;
  }
  Expeditions.finish(v, bad);
  int stay = Expeditions.infirmary(v).get(0).until - v.beaconsSeen();
  ChainT.jump(v, stay);
  Expeditions.checkInfirmary(v);
  CrewState after = crew(v, a.getName());
  int lostPts = 36 - homeplanet.model.Skills.points(after, 0) - homeplanet.model.Skills.points(after, 4);
  Setup.chk("C: " + stay + " beacons in the infirmary cost " + lostPts + " points of skill, from the skills she had (pilot " + homeplanet.model.Skills.points(after, 0) + ", repair " + homeplanet.model.Skills.points(after, 4) + ")",
    lostPts == stay && homeplanet.model.Skills.points(after, 2) == 0 && (homeplanet.model.Crew.skillLevels(after)[4] == 1) == (homeplanet.model.Skills.points(after, 4) >= 16));
  // someone laid up who leaves the Cargo Hold (retired, say) is let go quietly when their time is up: no word of them
  pinBoard(v, "rock_shaft");
  List<CrewState> pair = hold(v, "human", "human");
  Expeditions.Run gone = null;
  for (int s = 0; s < 5000 && gone == null; s++) {
   Expeditions.Run t = start(v, Expeditions.holdCrew(v), new Random(s));
   Expeditions.Choice vent = null; for (Expeditions.Choice x : t.choices()) if (x.text.startsWith("Go down the vent")) vent = x;
   t.choose(vent);
   if (fieldList(t, "hurt").size() == 1 && fieldList(t, "lost").isEmpty()) gone = t;
  }
  String leaver = ((CrewState) fieldList(gone, "hurt").get(0)).getName();
  Expeditions.finish(v, gone);
  Vault.Copy lc = v.readCopy(v.storage()); for (Iterator<CrewState> it = lc.save.getPlayerShip().getCrewList().iterator(); it.hasNext(); ) if (it.next().getName().equals(leaver)) it.remove();
  v.begin().put(v.storage(), lc.save, lc.hash).commit();
  ChainT.jump(v, Expeditions.HEAL_MAX + 1);
  Setup.chk("C: " + leaver + " left the Cargo Hold while laid up: when their time is up, no word of them, and the infirmary is empty", Expeditions.checkInfirmary(v).isEmpty() && Expeditions.infirmary(v).isEmpty());
  // a clone bay on a real run: home alive and whole, not to the infirmary, a level down in every skill (the experience
  // of the run goes too)
  pinBoard(v, "pilot_moon");
  CrewState gunner = Commission.volunteer("human", new Random(31)); gunner.setName("Gunner Ames");
  homeplanet.model.Skills.set(gunner, 3, 60); homeplanet.model.Skills.set(gunner, 5, 14); // weapons level 1, combat level 2
  Expeditions.Run cl = null;
  for (int s = 0; s < 5000 && cl == null; s++) {
   Expeditions.Run t = start(v, holdOf(v, gunner), new Random(s));
   Expeditions.Choice eng = null; for (Expeditions.Choice x : t.choices()) if (x.text.startsWith("Move in and engage")) eng = x;
   t.choose(eng);
   if (!fieldList(t, "cloned").isEmpty()) cl = t;
  }
  String clEnd = Expeditions.finish(v, cl);
  CrewState back = crew(v, gunner.getName());
  int[] lv = homeplanet.model.Crew.skillLevels(back);
  Setup.chk("C: cloned on the moon: home alive (" + clEnd + "), weapons 1 to 0, combat 2 to 1, not laid up", back != null && lv[3] == 0 && homeplanet.model.Skills.points(back, 3) == 0
    && lv[5] == 1 && !clEnd.contains("infirmary") && !Expeditions.laidUp(v, back) && Expeditions.holdCrew(v).size() == 1);
  // a clone bay: a level off each skill held; experience: points on, a level when they add up
  CrewState z = Commission.volunteer("human", new Random(9)); homeplanet.model.Skills.set(z, 5, 14); homeplanet.model.Skills.set(z, 0, 13); homeplanet.model.Skills.set(z, 3, 10);
  homeplanet.model.Skills.cloned(z);
  Setup.chk("C: cloned: combat 2 to 1 (points at the level's start), pilot 1 to 0, weapons stays 0 with its points", homeplanet.model.Crew.skillLevels(z)[5] == 1 && homeplanet.model.Skills.points(z, 5) == 7
    && homeplanet.model.Crew.skillLevels(z)[0] == 0 && homeplanet.model.Skills.points(z, 0) == 0 && homeplanet.model.Skills.points(z, 3) == 10);
  homeplanet.model.Skills.add(z, 3, 50);
  boolean lvl = homeplanet.model.Crew.skillLevels(z)[3] == 1 && homeplanet.model.Skills.points(z, 3) == 60;
  homeplanet.model.Skills.add(z, 3, 999);
  Setup.chk("C: 50 points of weapons on 10: level 1 (58), and never past the top", lvl && homeplanet.model.Skills.points(z, 3) == 116 && homeplanet.model.Crew.skillLevels(z)[3] == 2);
  // on a run: the relay's attendant choice gives repair experience to the one it's about
  pinBoard(v, "engi_relay");
  List<CrewState> one = hold(v, "human");
  int before = homeplanet.model.Skills.points(one.get(0), 4);
  Expeditions.Run r = start(v, one, new Random(1)); r.choose(r.choices().get(0));
  Expeditions.finish(v, r);
  Setup.chk("C: doing as the attendant says: 2 points of repair", homeplanet.model.Skills.points(crew(v, one.get(0).getName()), 4) == before + 2);
 }
 static CrewState crew(Vault v, String name) throws Exception { for (CrewState x : SaveHelper.getOwnCrew(v.readCopy(v.storage()).save.getPlayerShip())) if (x.getName().equals(name)) return x; return null; }
 static int hp(Vault v, String name) throws Exception { return crew(v, name).getHealth(); }
 static int shipHp(Vault v, Ship s, String name) throws Exception { for (CrewState x : SaveHelper.getOwnCrew(v.readCopy(s).save.getPlayerShip())) if (x.getName().equals(name)) return x.getHealth(); return -1; }
 /** Captives: taken; a letter a few beacons later ("one month", never beacons); a reminder near the end; paid: home; refused or run out: the Ambassador's letter. */
 static void ransoms(Vault v) throws Exception {
  hold(v, "human");
  Vault.Copy c0 = v.readCopy(v.storage()); c0.save.getPlayerShip().setScrapAmt(200); v.begin().put(v.storage(), c0.save, c0.hash).commit();
  // the moon: "taken" on a real run
  pinBoard(v, "pilot_moon");
  Expeditions.Run taken = null;
  for (int s = 0; s < 5000 && taken == null; s++) {
   Expeditions.Run t = start(v, Expeditions.holdCrew(v), new Random(s));
   Expeditions.Choice c = null; for (Expeditions.Choice x : t.choices()) if (x.text.startsWith("Head for the moon")) c = x;
   t.choose(c);
   for (Expeditions.Choice x : t.choices()) if (x.text.startsWith("Keep searching")) c = x;
   t.choose(c);
   if (!fieldList(t, "captured").isEmpty()) taken = t;
  }
  String name = ((CrewState) fieldList(taken, "captured").get(0)).getName();
  Expeditions.finish(v, taken);
  Setup.chk("W: " + name + " taken on the moon: nothing is asked at once", Expeditions.checkRansoms(v).isEmpty());
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
  String hist = new String(SafeFiles.read(HistoryLog.file()), "UTF-8");
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
  hist = new String(SafeFiles.read(HistoryLog.file()), "UTF-8");
  Setup.chk("W: unpaid, it runs out: the Ambassador's letter (presumed dead), the history log, the choices gone", news.size() == 1 && news.get(0).kind.equals("lost")
    && news.get(0).text().contains("presumed dead") && news.get(0).text().contains(c3.getName()) && hist.contains("ransom went unpaid") && Expeditions.openRansom(v, "ransom:2") == null);
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
 static void take(Vault v, CrewState c) throws Exception {
  File f = new File(v.root, "captives.txt"); Properties p = new Properties(); if (f.isFile()) p.load(new ByteArrayInputStream(SafeFiles.read(f)));
  java.lang.reflect.Method take = Expeditions.class.getDeclaredMethod("takeCaptive", Properties.class, CrewState.class, String.class, int.class, Random.class); take.setAccessible(true);
  take.invoke(null, p, c, "pirates", v.beaconsSeen(), new Random(9));
  StringWriter w = new StringWriter(); p.store(w, null); SafeFiles.writeText(f, w.toString(), false);
 }
 static void hiring(Vault v) throws Exception {
  hold(v);
  Setup.chk("I: 5 scrap a crew member, at most 60, free with none", Expeditions.hireCost(0) == 0 && Expeditions.hireCost(3) == 15 && Expeditions.hireCost(40) == 60);
  int crew = Expeditions.fleetCrew(v), cost = Expeditions.hireCost(crew);
  Vault.Copy c0 = v.readCopy(v.storage()); c0.save.getPlayerShip().setScrapAmt(500); v.begin().put(v.storage(), c0.save, c0.hash).commit();
  int answered = 0, tries = 0; for (int s = 0; s < 40 && answered == 0; s++) { tries++; if (Expeditions.hire(v, new Random(s)) != null) answered++; }
  Setup.chk("I: a posting is answered now and then (" + tries + " tries), the volunteer in the Cargo Hold, the scrap paid each time", answered == 1 && Expeditions.holdCrew(v).size() == 1 && v.storageScrap() == 500 - cost * tries);
  List<String> races = Expeditions.hireableRaces();
  Setup.chk("I: volunteers come from the ships the commander has unlocked " + races, !races.isEmpty() && races.contains("human"));
 }
}
