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
 ransoms(v);
 hiring(v);
 Setup.done();
}
 static void book() throws Exception {
  Setup.chk("B: the events file reads clean " + Expeditions.problems(), Expeditions.problems().isEmpty());
  Setup.chk("B: events for every kind of job (" + Expeditions.eventCount() + ")", Expeditions.eventCount() >= 12);
  // every choice has an outcome; a hurt outcome has a dead sister (a hurt is a coin flip with death)
  int rolled = 0, hurtOnly = 0, chained = 0;
  java.lang.reflect.Field outs = Expeditions.Choice.class.getDeclaredField("outcomes"); outs.setAccessible(true);
  java.lang.reflect.Field steps = Expeditions.Event.class.getDeclaredField("steps"); steps.setAccessible(true);
  for (Expeditions.Event e : Expeditions.events()) {
   Map<?, ?> st = (Map<?, ?>) steps.get(e); if (st.size() > 1) chained++;
   for (Object x : st.values()) for (Expeditions.Choice c : ((Expeditions.Step) x).choices) {
    List<?> os = (List<?>) outs.get(c); if (os.size() > 1) rolled++;
    boolean hurt = false, dead = false;
    for (Object o : os) { if (fieldInt(o, "injure") > 0) hurt = true; if (fieldInt(o, "lose") > 0 || fieldInt(o, "taken") > 0) dead = true; }
    if (hurt && !dead) hurtOnly++;
   }
  }
  Setup.chk("B: rolled choices (" + rolled + "), small chains (" + chained + "), and every hurt has a death beside it (" + hurtOnly + " without)", rolled >= 10 && chained >= 2 && hurtOnly == 0);
  // a broken event is caught
  Object b = construct("homeplanet.parser.Expeditions$Book");
  java.lang.reflect.Method parse = Expeditions.class.getDeclaredMethod("parse", String.class, b.getClass(), String.class); parse.setAccessible(true);
  java.lang.reflect.Method check = Expeditions.class.getDeclaredMethod("check", b.getClass()); check.setAccessible(true);
  parse.invoke(null, "event x rescue\nWords.\n* [rock] Only the Rock\n  = then nowhere | fine\n* Anyone\nstep lonely\n* A\n  = | a\n", b, "test");
  check.invoke(null, b);
  java.lang.reflect.Field pf = b.getClass().getDeclaredField("problems"); pf.setAccessible(true);
  List<?> probs = (List<?>) pf.get(b);
  boolean caught = false; for (Object p : probs) if (p.toString().contains("no outcome")) caught = true;
  Setup.chk("B: a broken event is caught: a choice with no outcome, a step that isn't there, one never reached, and the kinds with nothing " + probs, caught && probs.size() >= 3);
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
 /** A fixed board, so the runs don't depend on what was posted. */
 static void pinBoard(Vault v, String... events) throws Exception {
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
    Expeditions.Run r = Expeditions.start(v, 0, mixed, new Random(s)); Random pk = new Random(s);
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
  for (Expeditions.Choice c : Expeditions.start(v, 0, humans, new Random(1)).choices()) if (c.race != null) humanOnly = false;
  for (Expeditions.Choice c : Expeditions.start(v, 0, hold(v, "rock"), new Random(1)).choices()) if ("rock".equals(c.race)) sawRock = true;
  Setup.chk("R: a party of humans sees no race's options; a Rock's open with a Rock along", humanOnly && sawRock);
  // the odds: a bad outcome is rarer with the race the choice fits, and with the skill; the commander never dies
  java.lang.reflect.Method fit = Expeditions.Run.class.getDeclaredMethod("fit", Expeditions.Choice.class); fit.setAccessible(true);
  Expeditions.Run plain = Expeditions.start(v, 0, hold(v, "human"), new Random(1)), rock = Expeditions.start(v, 0, hold(v, "rock"), new Random(1));
  Expeditions.Choice vent = null; for (Expeditions.Choice c : plain.choices()) if (c.text.startsWith("Go down the vent")) vent = c;
  int fp = (Integer) fit.invoke(plain, vent), fr = (Integer) fit.invoke(rock, vent);
  CrewState skilled = Commission.volunteer("human", new Random(2)); skilled.setRepairMasteryOne(true); skilled.setRepairMasteryTwo(true);
  Vault.Copy cp = v.readCopy(v.storage()); cp.save.getPlayerShip().getCrewList().clear(); SaveHelper.placeCrew(cp.save.getPlayerShip(), skilled, true); cp.save.getPlayerShip().getCrewList().add(skilled); v.begin().put(v.storage(), cp.save, cp.hash).commit();
  int fs = (Integer) fit.invoke(Expeditions.start(v, 0, Expeditions.holdCrew(v), new Random(1)), vent);
  Setup.chk("R: the vent is safer for a Rock (" + fr + "% of the risk) and for a skilled repairer (" + fs + "%) than for anyone (" + fp + "%)", fp == 100 && fr < fp && fs < fp);
  // over many runs of a risky choice: deaths outnumber injuries, and a lone crew member can be lost (the commander goes on alone)
  int dead = 0, hurt = 0, fine = 0, alone = 0;
  for (int s = 0; s < 600; s++) {
   Expeditions.Run r = Expeditions.start(v, 0, hold(v, "human"), new Random(s));
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
  for (int s = 0; s < 200 && r == null; s++) { Expeditions.Run t = Expeditions.start(v, 0, Expeditions.holdCrew(v), new Random(s)); t.choose(t.choices().get(0)); if (t.over() && t.alive().size() == 2) r = t; }
  int beacons = v.beaconsSeen(); String old = Expeditions.board(v).get(0).text;
  int got = fieldInt(r, "scrap");
  String end = Expeditions.finish(v, r);
  Setup.chk("H: home: the scrap to the Cargo Hold (" + got + "), a beacon passed, the docking screen's words, a new posting in its place", v.storageScrap() == 100 + got && got > 0
    && v.beaconsSeen() == beacons + 1 && end.contains("docks at The Home Planet Station") && !Expeditions.board(v).get(0).text.equals(old));
  Properties bp = new Properties(); bp.load(new ByteArrayInputStream(SafeFiles.read(new File(v.root, "expeditions.txt"))));
  Setup.chk("H: the event is remembered, not to be met again soon", bp.getProperty("recent", "").contains("rock_shaft"));
  // a run where one was hurt and the other killed
  pinBoard(v, "rock_shaft");
  Expeditions.Run bad = null;
  for (int s = 0; s < 5000 && bad == null; s++) {
   Expeditions.Run t = Expeditions.start(v, 0, Expeditions.holdCrew(v), new Random(s));
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
  Setup.chk("H: " + hurtName + " hurt: to the infirmary (" + inf.size() + "), not among those who can be sent (" + canGo + "), the docking screen says so", end.contains(hurtName + " is carried to the infirmary")
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
   Expeditions.Run t = Expeditions.start(v, 0, Expeditions.holdCrew(v), new Random(s));
   Expeditions.Choice vent = null; for (Expeditions.Choice x : t.choices()) if (x.text.startsWith("Go down the vent")) vent = x;
   t.choose(vent);
   if (!fieldList(t, "lost").isEmpty()) death = t;
  }
  String deadName = ((CrewState) fieldList(death, "lost").get(0)).getName();
  before = Expeditions.holdCrew(v).size();
  end = Expeditions.finish(v, death);
  boolean gone = true; for (CrewState x : Expeditions.holdCrew(v)) if (x.getName().equals(deadName)) gone = false;
  String hist = new String(SafeFiles.read(HistoryLog.file()), "UTF-8");
  Setup.chk("H: " + deadName + " dead: gone from the Cargo Hold, not aboard at the docking, in the history log", gone && Expeditions.holdCrew(v).size() == before - 1 && end.contains(deadName + " is not aboard") && hist.contains("did not come back: " + deadName));
  // a party no longer in the hold is refused, nothing changed
  List<CrewState> party = hold(v, "human", "human");
  Expeditions.Run stale = Expeditions.start(v, 0, party, new Random(3)); while (!stale.over()) stale.choose(stale.choices().get(0));
  hold(v, "engi");
  boolean refused = false; try { Expeditions.finish(v, stale); } catch (IOException e) { refused = e.getMessage().contains("no longer in the Cargo Hold"); }
  Setup.chk("H: crew no longer in the Cargo Hold: the expedition can't be recorded, nothing changed", refused);
 }
 /** Captives: taken; a letter a few beacons later ("one month", never beacons); a reminder near the end; paid: home; refused or run out: the Ambassador's letter. */
 static void ransoms(Vault v) throws Exception {
  hold(v, "human");
  Vault.Copy c0 = v.readCopy(v.storage()); c0.save.getPlayerShip().setScrapAmt(200); v.begin().put(v.storage(), c0.save, c0.hash).commit();
  // the moon: "taken" on a real run
  pinBoard(v, "pilot_moon");
  Expeditions.Run taken = null;
  for (int s = 0; s < 5000 && taken == null; s++) {
   Expeditions.Run t = Expeditions.start(v, 0, Expeditions.holdCrew(v), new Random(s));
   Expeditions.Choice c = null; for (Expeditions.Choice x : t.choices()) if (x.text.startsWith("Head for the moon")) c = x;
   t.choose(c);
   for (Expeditions.Choice x : t.choices()) if (x.text.startsWith("Keep searching")) c = x;
   t.choose(c);
   if (!fieldList(t, "captured").isEmpty()) taken = t;
  }
  String name = ((CrewState) fieldList(taken, "captured").get(0)).getName();
  Expeditions.finish(v, taken);
  Setup.chk("W: " + name + " taken on the moon: nothing is asked at once", Expeditions.checkRansoms(v).isEmpty());
  java.lang.reflect.Method take = Expeditions.class.getDeclaredMethod("takeCaptive", Vault.class, CrewState.class, String.class); take.setAccessible(true);
  CrewState b = Commission.volunteer("slug", new Random(4)), c3 = Commission.volunteer("engi", new Random(5));
  take.invoke(null, v, b, "pirates"); take.invoke(null, v, c3, "pirates");
  ChainT.jump(v, 5);
  List<Expeditions.RansomNews> news = Expeditions.checkRansoms(v);
  int asks = 0; String words = ""; for (Expeditions.RansomNews x : news) { if (x.kind.equals("ask")) asks++; words += x.text(); }
  Setup.chk("W: a few beacons later all three ransoms are asked (" + asks + "), \"one month\", no beacons in the words, the rebels signing the moon's", asks == 3 && words.contains("one month") && !words.toLowerCase().contains("beacon") && words.contains("~ The rebels"));
  Expeditions.Captive pa = Expeditions.openRansom(v, "ransom:0"), pr = Expeditions.openRansom(v, "ransom:1");
  int inHold = Expeditions.holdCrew(v).size(), scrap = v.storageScrap();
  Expeditions.payRansom(v, pa);
  boolean home = false; for (CrewState x : Expeditions.holdCrew(v)) if (x.getName().equals(pa.name)) home = true;
  Setup.chk("W: a ransom paid: " + pa.name + " is back in the Cargo Hold, the ransom paid from it, and the letter's choices gone", home
    && Expeditions.holdCrew(v).size() == inHold + 1 && v.storageScrap() == scrap - pa.ransom && Expeditions.openRansom(v, "ransom:0") == null);
  Expeditions.refuseRansom(v, pr);
  String hist = new String(SafeFiles.read(HistoryLog.file()), "UTF-8");
  Setup.chk("W: a ransom refused: presumed dead, in the history log, the choices gone", hist.contains(pr.name + ", taken by") && hist.contains("ransom was refused") && Expeditions.openRansom(v, "ransom:1") == null);
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
