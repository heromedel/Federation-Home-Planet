import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Crew expeditions (expedition_type 2): the tables, the words, the board, the roll, the report, and a detail away and back. args: gamedir, world saves (from WorldT), work */
public class AsgT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 HomePlanet.expeditionType = 2;
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 tables();
 words();
 board(v);
 rolls();
 report();
 awayAndBack(v);
 rescues(v);
 twoDueAtOnce(v);
 prizes(v);
 twoPrizesOneDay(v);
 experience(v);
 skillOnRoll();
 roles();
 Setup.done();
}
 /** The job's skill on the d20 (5.43): +2 a level, +3 with a suited race; naturals 1 and 20 untouched; 2 to 19 otherwise. */
 static void skillOnRoll() {
  Setup.chk("K: a natural 1 and a natural 20 stay; untrained changes nothing", Expeditions.skilled(1, 2, true) == 1 && Expeditions.skilled(20, 2, true) == 20 && Expeditions.skilled(9, 0, true) == 9);
  Setup.chk("K: +2 a level, +3 a level when the race suits the job", Expeditions.skilled(9, 1, false) == 11 && Expeditions.skilled(9, 2, false) == 13 && Expeditions.skilled(9, 1, true) == 12 && Expeditions.skilled(9, 2, true) == 15);
  Setup.chk("K: a changed roll stays between 2 and 19: only a natural 20 is the top", Expeditions.skilled(18, 2, true) == 19 && Expeditions.skilled(19, 1, false) == 19 && Expeditions.skilled(2, 1, false) == 4);
  CrewState ace = Commission.volunteer("human", new Random(5));
  master(ace); // every skill's points full (5.62: a level is read from the points, not FTL's marks)
  boolean all = true; int seen = 0, raised = 0;
  for (int seed = 0; seed < 200; seed++) {
   Expeditions.Result r = Expeditions.roll("civilian", Collections.singletonList(ace), new Random(seed));
   Expeditions.Fate f = r.fates.get(0);
   int want = f.skill < 0 ? f.natural : Expeditions.skilled(f.natural, 2, Expeditions.raceJob("human", r.job) > 0);
   if (f.roll != want || f.band != Expeditions.band(f.roll)) all = false;
   seen++; if (f.roll > f.natural) raised++;
  }
  Setup.chk("K: a master's rolls take their role's skill (" + raised + " of " + seen + " raised), none for a job of race alone, the band from the changed roll", all && raised > 0);
 }
 static void tables() {
  boolean same = true;
  for (String s : Expeditions.sectors()) if (Expeditions.weightTotal(s) != 136) same = false;
  Setup.chk("T: every sector's job weights total 136", same && Expeditions.jobWeight("civilian", "negotiate") == 15 && Expeditions.jobWeight("civilian", "spiders") == 1 && Expeditions.jobWeight("nebula", "lost") == 13);
  Setup.chk("T: races by job: Engi good at repair, bad at attack; a Mantis bad at negotiating", Expeditions.raceJob("engi", "repair") == 10 && Expeditions.raceJob("engi", "attack") == -10 && Expeditions.raceJob("mantis", "negotiate") == -10 && Expeditions.raceJob("human", "scout") == 0);
  Setup.chk("T: races by sector: Slug in a nebula +10, Lanius in Engi space +20, everyone but Crystal and Lanius -10 in Abandoned, Rock's own +10 wins there, nobody penalised in rebel or Crystal space",
    Expeditions.raceSector("slug", "nebula") == 10 && Expeditions.raceSector("anaerobic", "engi") == 20 && Expeditions.raceSector("human", "abandoned") == -10 && Expeditions.raceSector("crystal", "abandoned") == 0
    && Expeditions.raceSector("anaerobic", "abandoned") == 0 && Expeditions.raceSector("rock", "abandoned") == 10 && Expeditions.raceSector("mantis", "rebel") == 0 && Expeditions.raceSector("slug", "crystal") == 0);
  Setup.chk("T: the d20's bands", Expeditions.band(1) == 0 && Expeditions.band(5) == 1 && Expeditions.band(9) == 2 && Expeditions.band(15) == 3 && Expeditions.band(19) == 4 && Expeditions.band(20) == 5);
 }
 static void words() throws Exception {
  Setup.chk("W: the words file has an event and six bands for every job, every hazard, every sector's offer and the prizes " + Expeditions.missingWords(), Expeditions.missingWords().isEmpty());
  Setup.chk("W: every marked line is marked for a real sector (captured: real captors) " + Expeditions.strayWords(), Expeditions.strayWords().isEmpty());
  // held against FTL's own event text: no run of six words the same
  String ftl = new String(ExpT.readAll(net.blerf.ftl.parser.DataManager.get().getResourceInputStream("data/text_events.xml")), "UTF-8").replaceAll("<[^>]*>", " ");
  Set<String> shingles = new HashSet<String>(); List<String> w = ExpT.words(ftl);
  for (int i = 0; i + 6 <= w.size(); i++) shingles.add(String.join(" ", w.subList(i, i + 6)));
  List<String> copied = new ArrayList<String>();
  for (String line : Expeditions.allWords()) { List<String> m = ExpT.words(line); for (int i = 0; i + 6 <= m.size(); i++) if (shingles.contains(String.join(" ", m.subList(i, i + 6)))) { copied.add(String.join(" ", m.subList(i, i + 6))); break; } }
  Setup.chk("W: no run of six words copied from FTL's events (" + Expeditions.allWords().size() + " lines) " + copied, copied.isEmpty());
  // captured lines know who took them, and say she for a woman
  Random rng = new Random(31); int pirate = 0, slaver = 0, she = 0, stray = 0;
  for (int i = 0; i < 40000 && (pirate == 0 || she == 0); i++) {
   String sector = i % 2 == 0 ? "pirate" : "mantis";
   List<CrewState> p = party("human", "human", "human"); for (CrewState c : p) c.setMale(false);
   Expeditions.Result r = Expeditions.roll(sector, p, rng);
   String t = r.report;
   if (t.contains("{")) stray++;
   if (t.contains("pirate ship before it broke away")) pirate++;
   if ("pirate".equals(sector) && t.contains("taken by slavers")) slaver++;
   if (t.contains("she's all right out there") || t.contains("never see her again")) she++;
  }
  // a race that shrugs off the hazard says so; nobody else does
  rng = new Random(32); int rockSaid = 0, rockFlares = 0, humanSaid = 0, rockHurt = 0, hurtSaid = 0;
  for (int i = 0; i < 40000 && (rockFlares < 30 || rockHurt < 10); i++) {
   Expeditions.Result rr = Expeditions.roll("civilian", party("rock"), rng);
   Expeditions.Result hr = Expeditions.roll("civilian", party("human"), rng);
   if ("flare".equals(hr.hazard) && (hr.report.contains("Rock doesn't burn") || hr.report.contains("straight through it"))) humanSaid++;
   if (!"flare".equals(rr.hazard)) continue;
   Expeditions.Fate f = rr.fates.get(0);
   if (f.died || f.captured || f.infirmary) continue;
   boolean said = rr.report.contains("Rock doesn't burn") || rr.report.contains("straight through it");
   if (f.band == 1) { rockHurt++; if (said || rr.report.contains("through a fire")) hurtSaid++; continue; } // an injury trumps it, and names another cause
   rockFlares++;
   if (said) rockSaid++;
  }
  Setup.chk("W: a Rock in a solar flare's job says the fire didn't touch them (" + rockSaid + " of " + rockFlares + "); a human never does (" + humanSaid + ")", rockFlares > 0 && rockSaid == rockFlares && humanSaid == 0);
  Setup.chk("W: an injured Rock in a flare says nothing of the fire, and isn't hurt by one (" + hurtSaid + " of " + rockHurt + ")", rockHurt > 0 && hurtSaid == 0);
  // the rebels' Anti-Ship Battery: rebel space with Advanced Edition only; each Engi a chance in three of hacking it for everyone
  rng = new Random(33); int battElsewhere = 0, batt1 = 0, hack1 = 0, batt3 = 0, hack3 = 0, wrongPay = 0, saidHack = 0;
  for (int i = 0; i < 60000 && (batt1 < 300 || batt3 < 30); i++) {
   if ("battery".equals(Expeditions.roll("pirate", party("human"), rng).hazard)) battElsewhere++;
   Expeditions.Result one = Expeditions.roll("rebel", party("engi", "human"), rng);
   if ("battery".equals(one.hazard)) { batt1++; if (one.hacker != null) { hack1++; if (one.report.contains(one.hacker.getName() + " turned the ship's Hacking system")) saidHack++; } }
   Expeditions.Result three = Expeditions.roll("rebel", party("engi", "engi", "engi"), rng);
   if ("battery".equals(three.hazard)) { batt3++; if (three.hacker != null) hack3++; }
  }
  Setup.chk("W: the Anti-Ship Battery never outside rebel space (" + battElsewhere + "); one Engi hacks it about a third of the time (" + hack1 + " of " + batt1 + "), and the report names them (" + saidHack + "); three always (" + hack3 + " of " + batt3 + ")",
    battElsewhere == 0 && batt1 > 0 && hack1 > batt1 / 5 && hack1 < batt1 / 2 && saidHack == hack1 && batt3 > 0 && hack3 == batt3);
  // the reward: hacked, nobody pays the battery's 10; not hacked, both do: about 20 points of the pot between them, on average
  Random same = new Random(34); long hackedSum = 0, plainSum = 0; int hackedN = 0, plainN = 0;
  for (int i = 0; i < 200000 && (hackedN < 400 || plainN < 400); i++) {
   Expeditions.Result a1 = Expeditions.roll("rebel", party("engi", "human"), same);
   if (!"battery".equals(a1.hazard)) continue;
   if (a1.hacker != null) { hackedSum += a1.multiplier; hackedN++; } else { plainSum += a1.multiplier; plainN++; }
  }
  double gap = hackedSum / (double) Math.max(1, hackedN) - plainSum / (double) Math.max(1, plainN);
  Setup.chk("W: hacked, nobody pays the battery; not hacked, both do (on average " + Math.round(gap) + " points of the pot between them)", hackedN > 0 && plainN > 0 && gap > 12 && gap < 28);
  Setup.chk("W: a capture in pirate space can say pirates (" + pirate + "), never slavers (" + slaver + "); a woman taken is she (" + she + "); no token left unfilled (" + stray + ")", pirate > 0 && slaver == 0 && she > 0 && stray == 0);
 }
 static void board(Vault v) throws Exception {
  List<Expeditions.Offer> b = Expeditions.board(v);
  Set<String> ids = new HashSet<String>(); for (Expeditions.Offer o : b) ids.add(o.sector);
  Setup.chk("B: three sectors on offer, all different, each with its words", b.size() == 3 && ids.size() == 3 && !b.get(0).words.isEmpty() && !b.get(0).title().isEmpty());
  List<Expeditions.Offer> again = Expeditions.board(v);
  Setup.chk("B: the same board until an offer comes down", again.get(0).sector.equals(b.get(0).sector) && again.get(2).sector.equals(b.get(2).sector));
  // every sector can come up, Crystal rarely
  Random rng = new Random(3); boolean abandoned = false; int crystal = 0;
  for (int i = 0; i < 400; i++) { String s = Expeditions.drawSector(rng, new ArrayList<String>()); if ("abandoned".equals(s)) abandoned = true; if ("crystal".equals(s)) crystal++; }
  Setup.chk("B: Abandoned comes up; Crystal rarely (" + crystal + " of 400)", abandoned && crystal > 0 && crystal < 40);
 }
 static List<CrewState> party(String... races) { List<CrewState> l = new ArrayList<CrewState>(); Random r = new Random(5); for (String x : races) l.add(Commission.volunteer(x, r)); return l; }
 static void rolls() {
  Random rng = new Random(11);
  int[] bands = new int[6]; int min = 999, max = 0, deaths = 0, spiderInjured = 0, captured = 0, items = 0, prizes = 0; long sum = 0; int n = 3000;
  for (int i = 0; i < n; i++) {
   Expeditions.Result r = Expeditions.roll("nebula", party("slug", "slug", "slug"), rng);
   for (Expeditions.Fate f : r.fates) { bands[f.band]++; if (f.died) deaths++; if (f.captured) captured++; if (f.item != null) items++; if ("spiders".equals(r.job) && f.band == 1 && !f.died) spiderInjured++; }
   if (r.prize != null) prizes++;
   min = Math.min(min, r.scrap); max = Math.max(max, r.scrap); sum += r.scrap;
  }
  System.out.println("three Slugs in a nebula, " + n + " runs: average " + (sum / n) + " scrap, " + min + " to " + max + "; bands " + Arrays.toString(bands) + "; items " + items + ", prizes " + prizes);
  Setup.chk("R: the pot is never under 1, averages in the twenties for a good detail, tops out past 40", min >= 1 && sum / n >= 15 && sum / n <= 32 && max >= 40);
  Setup.chk("R: every band comes up; deaths are rare (" + deaths + " of " + 3 * n + "); a 20 sometimes finds an item (" + items + ")", bands[0] > 0 && bands[5] > 0 && deaths < 3 * n / 10 && items > 0);
  Setup.chk("R: an injury on Giant Spiders is a death; a Get Boarded injury can mean capture (" + captured + ")", spiderInjured == 0 && captured > 0);
  // a lazy detail earns less than a fitting one
  rng = new Random(12); long lazy = 0, fit = 0;
  for (int i = 0; i < n; i++) { lazy += Expeditions.roll("mantis", party("slug", "slug", "slug"), rng).scrap; fit += Expeditions.roll("mantis", party("anaerobic", "anaerobic", "anaerobic"), rng).scrap; }
  Setup.chk("R: three Lanius in Mantis space out-earn three Slugs there (" + fit / n + " to " + lazy / n + ")", fit > lazy);
  // skills add: a level-2 fighter on a combat job
  rng = new Random(13); long raw = 0, skilled = 0;
  for (int i = 0; i < n; i++) {
   List<CrewState> p = party("human"); raw += Expeditions.roll("rock", p, rng).scrap;
   List<CrewState> q = party("human"); master(q.get(0));
   skilled += Expeditions.roll("rock", q, rng).scrap;
  }
  Setup.chk("R: skill in the job's skill pays (" + skilled / n + " to " + raw / n + ")", skilled > raw * 11 / 10);
  // hurt twice: sent wounded and wounded again, a third each: die, the infirmary, half what they had; never for the unhurt
  boolean infWas = HomePlanet.infirmary; HomePlanet.infirmary = true; // (with it off, the infirmary's third is worn down: rescues() checks off)
  Random tw = new Random(14); int twice = 0, twDied = 0, twInf = 0, twWorn = 0, freshOdd = 0;
  for (int i = 0; i < 40000 && twice < 2000; i++) {
   List<CrewState> wounded = party("human"); wounded.get(0).setHealth(30);
   Expeditions.Result x = Expeditions.roll("civilian", wounded, tw);
   Expeditions.Fate f = x.fates.get(0);
   if (f.band == 1 && !"spiders".equals(x.job) && !f.captured) { twice++; if (f.died) twDied++; else if (f.infirmary) twInf++; else if (f.worn) twWorn++; }
   Expeditions.Result y = Expeditions.roll("civilian", party("human"), tw);
   Expeditions.Fate g = y.fates.get(0);
   if (g.worn || (g.band == 1 && g.died && !"spiders".equals(y.job))) freshOdd++;
  }
  Setup.chk("R: hurt twice, a third each: " + twDied + " died, " + twInf + " to the infirmary, " + twWorn + " worn down, of " + twice + "; never for someone sent whole (" + freshOdd + ")",
    twice > 0 && twDied > twice / 4 && twDied < twice * 4 / 10 && twInf > twice / 4 && twInf < twice * 45 / 100 && twWorn > twice / 4 && twWorn < twice * 4 / 10 && freshOdd == 0);
  HomePlanet.infirmary = infWas;
  // sent hurt: half their own bonuses
  List<CrewState> h = party("slug"); h.get(0).setHealth(30);
  Setup.chk("R: a crew member sent hurt counts as hurt", Expeditions.roll("nebula", h, new Random(2)).fates.get(0).crew.getHealth() == 30);
 }
 static void report() {
  Random rng = new Random(21); Expeditions.Result r = null;
  for (int i = 0; i < 500 && (r == null || r.hazard == null || !r.dead().isEmpty()); i++) r = Expeditions.roll("nebula", party("slug", "human", "mantis"), rng);
  String t = r.report;
  System.out.println(t);
  Setup.chk("P: the frame: the heading, the sector, a blank line, a setup ending on the crew, a line a crew member, the total", t.startsWith("-- Expedition Report --\nSector: Nebula\n\n")
    && t.split("\n")[3].contains(" the crew ") && t.split("\n")[3].endsWith(".")
    && t.contains(" " + r.fates.get(0).name() + " ") && t.contains(" " + r.fates.get(2).name() + " ") && t.endsWith("Total Reward: " + r.scrap + " scrap"));
  // the setups: heromedel's, the general ones and the job's own all come up; two of a detail never share an outcome line
  Random fr = new Random(41); int his = 0, other = 0, same = 0, pairs = 0;
  for (int i = 0; i < 3000; i++) {
   Expeditions.Result x = Expeditions.roll("civilian", party("human", "human"), fr);
   String setup = x.report.split("\n")[3];
   if (setup.startsWith("Due to events during the assignment the crew ")) his++; else other++;
   if (x.fates.get(0).band == x.fates.get(1).band && !x.fates.get(0).died && !x.fates.get(0).captured && !x.fates.get(0).infirmary && !x.fates.get(1).captured && !x.fates.get(1).infirmary) {
    pairs++;
    String[] ls = x.report.split("\n"); String a1 = null, b1 = null;
    for (String l : ls) { if (l.startsWith(x.fates.get(0).name() + " ")) a1 = l.substring(x.fates.get(0).name().length()); else if (l.startsWith(x.fates.get(1).name() + " ")) b1 = l.substring(x.fates.get(1).name().length()); }
    if (a1 != null && a1.equals(b1)) same++;
   }
  }
  Setup.chk("P: heromedel's setup is one of the general ones (" + his + " of 3000, about one in twelve), the others the rest; two with the same outcome never read the same (" + same + " of " + pairs + ")", his > 150 && his < 400 && other > 0 && pairs > 0 && same == 0);
  Setup.chk("P: never a roll, a die or a percentage", !t.contains("%") && !t.toLowerCase().contains("d20") && !t.toLowerCase().matches("(?s).*\\b(rolls?|rolled (a|an|\\d))\\b.*") && !t.contains("+1") && !t.contains("-1"));
  Setup.chk("P: a hazard gets its line", r.hazard != null && (t.toLowerCase().contains("flare") || t.toLowerCase().contains("asteroid") || t.contains("pulsar") || t.contains("plasma storm")));
 }
 static void days() {
  // a quiet run: 1 to 3; the table on top
  Expeditions.Result r = new Expeditions.Result(); r.sector = "civilian"; r.job = "repair";
  for (int i = 0; i < 3; i++) r.fates.add(new Expeditions.Fate(party("human").get(0)));
  for (Expeditions.Fate f : r.fates) f.band = 3;
  int lo = 99, hi = 0; Random rng = new Random(5);
  for (int i = 0; i < 300; i++) { int d = Expeditions.days(r, party("human", "human", "human"), rng); lo = Math.min(lo, d); hi = Math.max(hi, d); }
  Setup.chk("D: a quiet job in a quiet sector takes a week or two, 7 to 14 days (" + lo + " to " + hi + ")", lo == 7 && hi == 14);
  r.sector = "abandoned"; lo = 99; hi = 0;
  for (int i = 0; i < 300; i++) { int d = Expeditions.days(r, party("human", "human", "human"), rng); lo = Math.min(lo, d); hi = Math.max(hi, d); }
  Setup.chk("D: Abandoned always adds a day (" + lo + " to " + hi + ")", lo == 8 && hi == 15);
  r.sector = "civilian"; r.job = "lost"; for (Expeditions.Fate f : r.fates) f.band = 2; lo = 99; hi = 0;
  for (int i = 0; i < 300; i++) { int d = Expeditions.days(r, party("human", "human", "human"), rng); lo = Math.min(lo, d); hi = Math.max(hi, d); }
  Setup.chk("D: Got Lost with three failures is four days late, always (" + lo + " to " + hi + ")", lo == 11 && hi == 18);
  r.job = "repair"; lo = 99; hi = 0;
  for (int i = 0; i < 300; i++) { int d = Expeditions.days(r, party("human", "human", "human"), rng); lo = Math.min(lo, d); hi = Math.max(hi, d); }
  Setup.chk("D: on another job a failure adds a day only sometimes (" + lo + " to " + hi + ")", lo == 7 && hi == 17);
  for (Expeditions.Fate f : r.fates) f.band = 3; r.job = "scout"; lo = 99; hi = 0;
  for (int i = 0; i < 300; i++) { int d = Expeditions.days(r, party("human", "human", "human"), rng); lo = Math.min(lo, d); hi = Math.max(hi, d); }
  Setup.chk("D: a Scout is a day quicker (" + lo + " to " + hi + ")", lo == 6 && hi == 13);
  r.job = "hijack"; r.prize = "ship"; r.fates.get(0).item = "fuel:3"; lo = 99; hi = 0;
  for (int i = 0; i < 300; i++) { int d = Expeditions.days(r, party("human", "human", "human"), rng); lo = Math.min(lo, d); hi = Math.max(hi, d); }
  Setup.chk("D: a ship towed home and an item carried: three days more (" + lo + " to " + hi + ")", lo == 10 && hi == 17);
  r.prize = null; r.fates.get(0).item = null; int rocks = 0, humans = 0;
  for (int i = 0; i < 600; i++) { rocks += Expeditions.days(r, party("rock", "rock", "rock"), rng); humans += Expeditions.days(r, party("human", "human", "human"), rng); }
  Setup.chk("D: three Rocks are a day slower on average, one in three each (" + rocks / 600.0 + " to " + humans / 600.0 + " days)", rocks - humans > 400 && rocks - humans < 800);
  Setup.chk("D: never past the cap", Expeditions.AWAY_CAP == 21);
 }
 /** 5.16 (heromedel's save): two details due on the same look each come home once; a third, not yet due, stays away. */
 static void twoDueAtOnce(Vault v) throws Exception {
  List<CrewState> crew = ExpT.hold(v, "human", "engi", "mantis", "slug", "rock", "human");
  for (int i = 0; i < 3; i++) Expeditions.send(v, Expeditions.board(v).get(0).slot, crew.subList(i * 2, i * 2 + 2), new Random(i));
  List<Expeditions.Away> aw = Expeditions.away(v);
  int[] u = new int[aw.size()]; for (int i = 0; i < u.length; i++) u[i] = aw.get(i).until; Arrays.sort(u);
  Setup.chk("M: three details away", aw.size() == 3 && u[1] < u[2]);
  while (v.beaconsSeen() < u[1]) v.countBeacon();
  List<Expeditions.Report> back = Expeditions.checkReturns(v);
  List<Expeditions.Away> still = Expeditions.away(v);
  Setup.chk("M: the two due come home on one look, the third stays away", back.size() == 2 && still.size() == 1 && still.get(0).until == u[2]);
  Setup.chk("M: and nothing comes home twice", Expeditions.checkReturns(v).isEmpty());
  while (v.beaconsSeen() < u[2]) v.countBeacon();
  Setup.chk("M: the third comes home in its time", Expeditions.checkReturns(v).size() == 1 && Expeditions.away(v).isEmpty());
  Map<String, Integer> seen = new HashMap<String, Integer>();
  boolean twice = false;
  for (CrewState c : Expeditions.holdCrew(v)) { String k = c.getName() + "/" + c.getRace(); if (seen.containsKey(k)) twice = true; seen.put(k, 1); }
  Setup.chk("M: nobody in the Cargo Hold twice", !twice);
  for (Expeditions.Pending x : Expeditions.pending(v)) Expeditions.decline(v, x); // a recruit they brought home (6.69's odds): answered, so the prize checks start with nothing waiting
 }
 static void awayAndBack(Vault v) throws Exception {
  List<CrewState> crew = ExpT.hold(v, "slug", "human", "engi", "rock");
  List<Expeditions.Offer> b = Expeditions.board(v);
  int beforeScrap = v.storageScrap(), beforeBeacons = v.beaconsSeen();
  Expeditions.send(v, b.get(1).slot, crew.subList(0, 2), new Random(4));
  List<Expeditions.Away> away = Expeditions.away(v);
  Setup.chk("A: a detail away: out of the Cargo Hold, named in the file, due in a week to three; setting out passes no time (5.16)", away.size() == 1 && away.get(0).crew.size() == 2 && away.get(0).names().contains(crew.get(0).getName())
    && Expeditions.holdCrew(v).size() == 2 && away.get(0).until >= v.beaconsSeen() + 6 && away.get(0).until <= v.beaconsSeen() + Expeditions.AWAY_CAP && v.beaconsSeen() == beforeBeacons);
  Expeditions.Result first = away.get(0).result(), same = away.get(0).result();
  Setup.chk("A: the result was rolled at setting out and rolls the same every time (the seed)", first.report.equals(same.report) && first.scrap == same.scrap && away.get(0).seed != 0);
  Setup.chk("A: the offer taken is replaced, by another sector", Expeditions.board(v).size() == 3 && Expeditions.board(v).get(1).sector != null && !Expeditions.board(v).get(1).sector.equals(b.get(1).sector));
  Setup.chk("A: not back before their time", Expeditions.checkReturns(v).isEmpty() && Expeditions.away(v).size() == 1);
  while (v.beaconsSeen() < away.get(0).until) v.countBeacon();
  // a known result: both successful, no items
  Expeditions.Result r = null; Random rng = new Random(1);
  while (r == null || r.dead().size() > 0 || r.fates.get(0).band < 3 || r.fates.get(1).band < 3 || r.fates.get(0).item != null || r.fates.get(1).item != null || r.prize != null || r.fates.get(0).captured || r.fates.get(1).captured)
   r = Expeditions.roll(away.get(0).sector, away.get(0).crew, rng);
  Expeditions.Report rep = Expeditions.bringHome(v, away.get(0), r);
  Setup.chk("A: back: both in the Cargo Hold again, the scrap paid, the record gone, the report says it", Expeditions.holdCrew(v).size() == 4 && v.storageScrap() == beforeScrap + r.scrap && Expeditions.away(v).isEmpty()
    && rep.text.equals(r.report) && rep.title().startsWith("Back from "));
  int skill = 0; for (CrewState c : Expeditions.holdCrew(v)) for (int lv : homeplanet.model.Crew.skillLevels(c)) skill += lv;
  Setup.chk("A: the station's round brings a due detail home by itself", Expeditions.checkReturns(v).isEmpty());
  // the round's own report is the one rolled at setting out
  crew = ExpT.hold(v, "human", "engi");
  Expeditions.send(v, Expeditions.board(v).get(2).slot, crew, new Random(8));
  Expeditions.Away due = Expeditions.away(v).get(0); String expected = due.result().report;
  while (v.beaconsSeen() < due.until) v.countBeacon();
  List<Expeditions.Report> reps = Expeditions.checkReturns(v);
  Setup.chk("A: the round tells the report rolled at setting out", reps.size() == 1 && reps.get(0).text.equals(expected));
  for (Expeditions.Pending x : Expeditions.pending(v)) Expeditions.decline(v, x);
  days();
  // an injury, a death and a capture written home
  crew = ExpT.hold(v, "slug", "human", "engi");
  Expeditions.send(v, Expeditions.board(v).get(0).slot, crew, new Random(4));
  away = Expeditions.away(v);
  while (v.beaconsSeen() < away.get(0).until) v.countBeacon();
  r = Expeditions.roll("mantis", away.get(0).crew, new Random(1));
  r.job = "boarded"; r.fates.get(0).died = true; r.fates.get(0).band = 0; r.fates.get(1).died = false; r.fates.get(1).captured = true; r.fates.get(1).band = 1; r.fates.get(1).infirmary = false;
  r.fates.get(2).died = false; r.fates.get(2).captured = false; r.fates.get(2).infirmary = true; r.fates.get(2).band = 1;
  Expeditions.bringHome(v, away.get(0), r);
  List<AwayCrew.Patient> inf = AwayCrew.infirmary(v);
  File cap = AwayCrew.captivesFile(v); Properties cp = Store.load(cap);
  Setup.chk("A: the dead stay gone, the taken are among the captives (a ransom to come), the badly hurt in the infirmary at a quarter health",
    Expeditions.holdCrew(v).size() == 0 && SaveHelper.getOwnCrew(v.readCopy(v.storage()).save.getPlayerShip()).size() == 1 && inf.size() == 1 && inf.get(0).name.equals(crew.get(2).getName())
    && cp.getProperty("0.name", "").equals(crew.get(1).getName()) && v.readCopy(v.storage()).save.getPlayerShip().getCrewList().get(0).getHealth() <= 25);
 }
 /**
  * A life saved (heromedel, 6.61): with the infirmary on, a death may be turned by a crewmate who had an outstanding
  * success (the top band), once each, on a second d20 with their role's skill (10 or more); off, never, and no one is
  * laid up at all. The saved crew member comes home to the infirmary a level down, and every record tells it.
  */
 static void rescues(Vault v) throws Exception {
  boolean was = HomePlanet.infirmary;
  java.lang.reflect.Method rescue = Expeditions.class.getDeclaredMethod("rescue", Expeditions.Result.class, Random.class); rescue.setAccessible(true);
  List<CrewState> four = party("human", "human", "human", "human");
  // the rules, on made-up results: who may try, how often, and what a pass does
  HomePlanet.infirmary = true;
  int passed = 0, onlyHigh = 0, twoDead = 0, deadSaver = 0;
  for (int seed = 0; seed < 400; seed++) {
   Expeditions.Result r = made(four, 0, 5, 3, 3);
   rescue.invoke(null, r, new Random(seed));
   Expeditions.Fate d = r.fates.get(0);
   if (!d.died) { passed++; if (!d.infirmary || d.savedBy != r.fates.get(1)) passed = -100000; }
   Expeditions.Result hi = made(four, 0, 4, 4, 3); rescue.invoke(null, hi, new Random(seed)); if (!hi.fates.get(0).died) onlyHigh++;
   Expeditions.Result two = made(four, 0, 0, 5, 3); rescue.invoke(null, two, new Random(seed)); if (!two.fates.get(0).died && !two.fates.get(1).died) twoDead++;
   Expeditions.Result gone = made(four, 0, 5, 3, 3); gone.fates.get(1).captured = true; rescue.invoke(null, gone, new Random(seed)); if (!gone.fates.get(0).died) deadSaver++;
  }
  Setup.chk("V: a top-band crewmate saves a death about half the time with no skill (" + passed + " of 400), and the one saved is laid up, saved by them", passed > 140 && passed < 260);
  Setup.chk("V: never a crewmate short of the top band (" + onlyHigh + "), never two lives for one (" + twoDead + "), never a crewmate taken (" + deadSaver + ")", onlyHigh == 0 && twoDead == 0 && deadSaver == 0);
  List<CrewState> skilled = party("human", "human"); master(skilled.get(1));
  int skilledPass = 0;
  for (int seed = 0; seed < 400; seed++) {
   Expeditions.Result r = made(skilled, 0, 5); r.fates.get(1).skill = 0; rescue.invoke(null, r, new Random(seed)); if (!r.fates.get(0).died) skilledPass++;
  }
  Setup.chk("V: their role's skill counts on the second roll (" + skilledPass + " of 400 at level 2, " + passed + " with none)", skilledPass > passed + 40);
  // off: no one saved, and the dice untouched
  HomePlanet.infirmary = false;
  Expeditions.Result off = made(four, 0, 5, 3, 3); Random fixed = new Random(9); rescue.invoke(null, off, fixed);
  Setup.chk("V: with the infirmary off, no rescue and no dice rolled", off.fates.get(0).died && fixed.nextInt() == new Random(9).nextInt());
  // the rate over real rolls, and off: no one laid up at all
  int deaths = 0, saved = 0, laidOff = 0, n = 6000;
  String[] sectors = {"civilian", "engi", "zoltan", "mantis", "pirate", "rebel", "rock", "nebula", "abandoned", "crystal"};
  String[] races = {"human", "engi", "energy", "mantis", "rock", "slug", "crystal", "anaerobic"};
  Random rr = new Random(21);
  for (int i = 0; i < n; i++) {
   String sec = sectors[rr.nextInt(sectors.length)];
   List<CrewState> p = party(races[rr.nextInt(races.length)], races[rr.nextInt(races.length)], races[rr.nextInt(races.length)]);
   for (CrewState c : p) if (rr.nextInt(4) == 0) c.setHealth(c.getRace().getMaxHealth() / 2);
   long seed = rr.nextLong();
   HomePlanet.infirmary = true;
   Expeditions.Result on = Expeditions.roll(sec, p, new Random(seed));
   for (Expeditions.Fate f : on.fates) { if (f.died) deaths++; if (f.savedBy != null) { saved++; deaths++; } }
   HomePlanet.infirmary = false;
   for (CrewState c : p) if (rr.nextInt(4) == 0) c.setHealth(c.getRace().getMaxHealth() / 2);
   for (Expeditions.Fate f : Expeditions.roll(sec, p, new Random(seed)).fates) if (f.infirmary) laidOff++;
  }
  Setup.chk("V: over " + n + " expeditions, " + saved + " of " + deaths + " deaths saved (a few in a hundred); with the infirmary off, no one laid up (" + laidOff + ")",
    saved > 0 && saved * 100 < deaths * 12 && laidOff == 0);
  // the report: the rescue line, both named, the infirmary said
  HomePlanet.infirmary = true;
  java.lang.reflect.Method tell = Expeditions.class.getDeclaredMethod("report", Expeditions.Result.class, Random.class); tell.setAccessible(true);
  int good = 0, jobs = 0; String sample = null;
  for (String job : new String[] {"defend", "attack", "negotiate", "boarded", "rescue", "salvage", "scout", "repair", "transport", "lost", "escort", "capture", "board", "hijack", "infection", "spiders"}) {
   Expeditions.Result r = made(four, 0, 5, 3, 3); r.job = job; r.sector = "civilian"; r.fates.get(1).skill = 0;
   r.fates.get(0).died = false; r.fates.get(0).infirmary = true; r.fates.get(0).savedBy = r.fates.get(1);
   String text = (String) tell.invoke(null, r, new Random(job.hashCode()));
   String line = null; for (String l : text.split("\n")) if (l.contains(four.get(0).getName()) && l.contains(four.get(1).getName()) && !l.startsWith("\u2605")) line = l;
   jobs++;
   if (line != null && line.contains("infirmary") && !line.contains("{") && !text.contains(four.get(0).getName() + " was killed")) good++;
   if (sample == null) sample = line;
  }
  Setup.chk("V: on every job the one saved gets a rescue line naming both, and saying the infirmary (" + good + " of " + jobs + "): " + sample, good == jobs);
  // brought home: laid up at a quarter health, a level down in one skill; the station log, the Crew Log and the Captain's Log tell it
  List<CrewState> crew = ExpT.hold(v, "human", "human");
  homeplanet.model.Skills.set(crew.get(0), 0, 2 * homeplanet.model.Skills.interval(crew.get(0), 0));
  homeplanet.model.Skills.set(crew.get(0), 4, 1 * homeplanet.model.Skills.interval(crew.get(0), 4));
  Vault.Copy hc = v.readCopy(v.storage());
  for (CrewState x : hc.save.getPlayerShip().getCrewList()) if (x.getName().equals(crew.get(0).getName())) { homeplanet.model.Skills.set(x, 0, 2 * homeplanet.model.Skills.interval(x, 0)); homeplanet.model.Skills.set(x, 4, homeplanet.model.Skills.interval(x, 4)); }
  v.begin().put(v.storage(), hc.save, hc.hash).commit();
  v.takeStock(); // on the crew register, as the station's look does
  crew = Expeditions.holdCrew(v);
  CrewState ann = null, ken = null; for (CrewState x : crew) if (homeplanet.model.Crew.skillLevels(x)[0] == 2) ann = x; else ken = x;
  int levelsBefore = 0; for (int lv : homeplanet.model.Crew.skillLevels(ann)) levelsBefore += lv;
  Expeditions.send(v, Expeditions.board(v).get(0).slot, Arrays.asList(ann, ken), new Random(4));
  v.takeStock(); // the station's look sees them away
  List<Expeditions.Away> away = Expeditions.away(v);
  while (v.beaconsSeen() < away.get(0).until) v.countBeacon();
  Expeditions.Result r = Expeditions.roll("civilian", away.get(0).crew, new Random(1));
  Expeditions.Fate fa = null, fk = null; for (Expeditions.Fate f : r.fates) if (homeplanet.model.Crew.skillLevels(f.crew)[0] == 2) fa = f; else fk = f;
  ann = fa.crew; ken = fk.crew; // as the detail's record has them
  fa.band = 0; fa.died = false; fa.captured = false; fa.worn = false; fa.item = null; fa.infirmary = true; fa.savedBy = fk;
  fk.band = 5; fk.died = false; fk.captured = false; fk.infirmary = false; fk.item = null; r.prize = null;
  Expeditions.Report rep = Expeditions.bringHome(v, away.get(0), r);
  v.takeStock();
  CrewState home = null; for (CrewState x : SaveHelper.getOwnCrew(v.readCopy(v.storage()).save.getPlayerShip())) if (x.getName().equals(ann.getName())) home = x;
  int levelsAfter = 0; for (int lv : homeplanet.model.Crew.skillLevels(home)) levelsAfter += lv;
  List<AwayCrew.Patient> inf = AwayCrew.infirmary(v);
  Setup.chk("V: the one saved is laid up at a quarter health, a level down in one skill (" + levelsBefore + " to " + levelsAfter + ")",
    inf.size() >= 1 && inf.get(inf.size() - 1).name.equals(ann.getName()) && home.getHealth() <= 25 && levelsAfter == levelsBefore - 1);
  String log = Setup.stationLog(v), raw = new String(SafeFiles.read(EventLog.file(v)), "UTF-8");
  Setup.chk("V: the station log keeps who saved whom, in both lines", (raw.contains("saved=" + ann.getName() + ":" + ken.getName()) || raw.contains("saved=\"" + ann.getName() + ":" + ken.getName() + "\"")) && log.contains("; saved: " + ann.getName() + " by " + ken.getName()));
  String annSays = null, kenSays = null;
  for (CrewRegister.Member m : CrewRegister.members(v)) for (CrewRegister.Event e : m.events) {
   if (m.name.equals(ann.getName()) && e.text.contains("saved from death by " + ken.getName())) annSays = e.text;
   if (m.name.equals(ken.getName()) && e.text.contains("saved " + ann.getName() + "'s life")) kenSays = e.text;
  }
  StringBuilder seen = new StringBuilder();
  if (annSays == null || kenSays == null) for (CrewRegister.Member m : CrewRegister.members(v)) for (CrewRegister.Event e : m.events) if (m.name.equals(ann.getName()) || m.name.equals(ken.getName()) || e.text.contains("saved")) seen.append(" [").append(m.name).append(" ").append(m.id).append(": ").append(e.text).append("]");
  if (annSays == null) seen.append(" ann=" + ann.getName() + " ken=" + ken.getName());
  Setup.chk("V: the Crew Log tells it for both: \"" + annSays + "\" / \"" + kenSays + "\"" + seen, annSays != null && annSays.contains("infirmary") && kenSays != null);
  String cap = null;
  for (CaptainsLog.Day d : CaptainsLog.days(v)) for (CaptainsLog.Line l : d.lines) if (l.text.contains(ken.getName() + " saved " + ann.getName() + "'s life")) cap = l.text;
  Setup.chk("V: the Captain's Log tells it: " + cap, cap != null && cap.contains(ann.getName() + " went to the infirmary"));
  Setup.chk("V: the report the player reads is the rescue: " + rep.text.replace("\n", " | "), rep.text.contains(ann.getName()) && rep.text.contains(ken.getName()) && !rep.text.contains(ann.getName() + " was killed"));
  for (Expeditions.Pending x : Expeditions.pending(v)) Expeditions.decline(v, x);
  HomePlanet.infirmary = was;
 }
 /** A made-up result: these crew, these bands (0 dead), no roles, nothing found. */
 static Expeditions.Result made(List<CrewState> crew, int... bands) {
  Expeditions.Result r = new Expeditions.Result(); r.sector = "civilian"; r.job = "salvage";
  for (int i = 0; i < bands.length; i++) { Expeditions.Fate f = new Expeditions.Fate(crew.get(i)); f.band = bands[i]; f.roll = bands[i] == 5 ? 20 : bands[i] == 0 ? 1 : 10; f.natural = f.roll; f.died = bands[i] == 0; r.fates.add(f); }
  return r;
 }
 /** Two hijacked ships won by details sent on the same day: two prizes, two files (5.20: the renumbered one wrote over the first's). */
 static void twoPrizesOneDay(Vault v) throws Exception {
  int junked = v.junked().size();
  for (int i = 0; i < 2; i++) Expeditions.send(v, Expeditions.board(v).get(0).slot, ExpT.hold(v, "rock", "engi"), new Random(10 + i));
  List<Expeditions.Away> aw = Expeditions.away(v);
  Setup.chk("Z: two details sent on one day", aw.size() == 2 && aw.get(0).sentAt == aw.get(1).sentAt);
  while (v.beaconsSeen() < Math.max(aw.get(0).until, aw.get(1).until)) v.countBeacon();
  for (int i = 0; i < 2; i++) {
   Expeditions.Away a = Expeditions.away(v).get(0); // the second is renumbered to 0 once the first is home
   Expeditions.Result r = Expeditions.roll("pirate", a.crew, new Random(1));
   for (Expeditions.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 3; f.item = null; }
   r.job = "hijack"; r.prize = "ship";
   Expeditions.bringHome(v, a, r);
  }
  List<Expeditions.Pending> pend = Expeditions.pending(v);
  boolean apart = pend.size() == 2 && pend.get(0).save.isFile() && pend.get(1).save.isFile() && !pend.get(0).save.equals(pend.get(1).save);
  Setup.chk("Z: two prize ships wait, each in her own file " + (pend.size() == 2 ? pend.get(0).save.getName() + " " + pend.get(1).save.getName() : "" + pend.size()), apart);
  for (Expeditions.Pending p : pend) Expeditions.accept(v, p, false);
  Setup.chk("Z: and both reach the Junkyard, two ships", v.junked().size() == junked + 2 && Expeditions.pending(v).isEmpty());
 }
 static void prizes(Vault v) throws Exception {
  List<CrewState> crew = ExpT.hold(v, "rock", "engi");
  int listingsBefore = Derelicts.current(v).size();
  Expeditions.send(v, Expeditions.board(v).get(0).slot, crew, new Random(4));
  Expeditions.Away a = Expeditions.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  Expeditions.Result r = Expeditions.roll("pirate", a.crew, new Random(1));
  for (Expeditions.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 3; f.item = "missiles:2"; }
  r.job = "hijack"; r.prize = "ship";
  int missiles = v.readCopy(v.storage()).save.getPlayerShip().getMissilesAmt(), junked = v.junked().size(), docked = v.docked().size();
  Expeditions.Report shipRep = Expeditions.bringHome(v, a, r);
  List<Expeditions.Pending> pend = Expeditions.pending(v);
  int missilesNow = v.readCopy(v.storage()).save.getPlayerShip().getMissilesAmt(), listingsNow = Derelicts.current(v).size();
  boolean shipOk = pend.size() == 1 && "ship".equals(pend.get(0).kind) && pend.get(0).save.isFile()
    && r.prizeDetail != null && shipRep.text.contains(r.prizeDetail) && listingsNow == listingsBefore && missilesNow == missiles + 4;
  String why = shipOk ? "" : " [" + r.prizeDetail + " named: " + (r.prizeDetail != null && shipRep.text.contains(r.prizeDetail)) + "; pending " + pend.size() + (pend.isEmpty() ? "" : " " + pend.get(0).kind + " file " + (pend.get(0).save != null && pend.get(0).save.isFile()))
    + "; missiles " + missiles + " -> " + missilesNow + "; listings " + listingsBefore + " -> " + listingsNow + "]"; // said in the FAIL line itself (the harness keeps only PASS and FAIL lines)
  Setup.chk("Z: a Hijack's prize ship waits on the commander's word, named in the report; the items came home" + why, shipOk);
  Expeditions.accept(v, pend.get(0), true);
  Setup.chk("Z: taken to the Space Dock, she's docked and the question is gone", v.docked().size() == docked + 1 && v.junked().size() == junked && Expeditions.pending(v).isEmpty() && !pend.get(0).save.isFile());
  crew = ExpT.hold(v, "rock", "engi");
  Expeditions.send(v, Expeditions.board(v).get(0).slot, crew, new Random(4));
  a = Expeditions.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  r = Expeditions.roll("pirate", a.crew, new Random(1));
  for (Expeditions.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 3; f.item = null; }
  r.job = "hijack"; r.prize = "ship";
  Expeditions.bringHome(v, a, r);
  Expeditions.accept(v, Expeditions.pending(v).get(0), false);
  Setup.chk("Z: or to the Junkyard", v.junked().size() == junked + 1 && v.docked().size() == docked + 1 && Expeditions.pending(v).isEmpty());
  crew = ExpT.hold(v, "rock", "engi");
  Expeditions.send(v, Expeditions.board(v).get(0).slot, crew, new Random(4));
  a = Expeditions.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  r = Expeditions.roll("abandoned", a.crew, new Random(1));
  for (Expeditions.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 4; f.item = null; }
  r.job = "salvage"; r.prize = "part";
  Expeditions.bringHome(v, a, r);
  String sys = v.systemsFile().isFile() ? new String(SafeFiles.read(v.systemsFile()), "UTF-8") : "";
  Setup.chk("Z: a Salvage's prize part is among the stored systems, a bar broken", r.prizeDetail != null && sys.trim().split("\n").length >= 2 && sys.contains(" 1 1"));
  crew = ExpT.hold(v, "human");
  Expeditions.send(v, Expeditions.board(v).get(0).slot, crew, new Random(4));
  a = Expeditions.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  r = Expeditions.roll("civilian", a.crew, new Random(1));
  for (Expeditions.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 5; f.item = null; }
  r.job = "rescue"; r.prize = "recruit";
  Expeditions.Report rep = Expeditions.bringHome(v, a, r);
  pend = Expeditions.pending(v);
  Setup.chk("Z: a Rescue's recruit asks, named in the report, and isn't in the hold yet", r.recruit != null && pend.size() == 1 && "recruit".equals(pend.get(0).kind) && pend.get(0).crew.getName().equals(r.recruit.getName())
    && rep.text.contains(r.recruit.getName()) && Expeditions.holdCrew(v).size() == 1 && pend.get(0).question().contains("sign on"));
  Expeditions.decline(v, pend.get(0));
  Setup.chk("Z: sent on their way: not in the hold, no question left", Expeditions.holdCrew(v).size() == 1 && Expeditions.pending(v).isEmpty());
  // with Immersive Notifications on, the letter carries the question: the Space Dock doesn't ask
  HomePlanet.immersiveNotifications = true;
  crew = ExpT.hold(v, "human");
  Expeditions.send(v, Expeditions.board(v).get(0).slot, crew, new Random(4));
  a = Expeditions.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  r = Expeditions.roll("civilian", a.crew, new Random(1));
  for (Expeditions.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 5; f.item = null; }
  r.job = "rescue"; r.prize = "recruit";
  rep = Expeditions.bringHome(v, a, r);
  pend = Expeditions.pending(v);
  String letterKey = Expeditions.letterKey(a);
  Setup.chk("Z: the letter carries the question: the prize names it, the Space Dock leaves it be, the words say they wait in the lounge", pend.size() == 1 && letterKey.equals(pend.get(0).letter)
    && Expeditions.pendingToAsk(v).isEmpty() && Expeditions.pendingFor(v, letterKey) != null && rep.text.contains("The Station Lounge"));
  boolean delivered = false; for (Transmissions.Message m : Transmissions.load()) if (m.key.equals(letterKey)) delivered = true;
  Setup.chk("Z: and the letter is in the inbox", delivered);
  Expeditions.accept(v, pend.get(0), false);
  Setup.chk("Z: answered from the letter, they're in the hold", Expeditions.holdCrew(v).size() == 2 && Expeditions.pendingFor(v, letterKey) == null);
  { // what they bring back comes with the letter, claimed like anything shipped home (heromedel, 6.02)
   a = null; Expeditions.send(v, Expeditions.board(v).get(0).slot, ExpT.hold(v, "human"), new Random(5));
   a = Expeditions.away(v).get(0);
   while (v.beaconsSeen() < a.until) v.countBeacon();
   r = Expeditions.roll("civilian", a.crew, new Random(2));
   for (Expeditions.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 5; f.item = null; }
   r.fates.get(0).item = "SCRAP_COLLECTOR"; r.scrap = 37; r.prize = null;
   int scrapBefore = v.storageScrap();
   rep = Expeditions.bringHome(v, a, r);
   Transmissions.Message back = null; for (Transmissions.Message m : Transmissions.load()) if (m.key.equals(Expeditions.letterKey(a))) back = m;
   Setup.chk("Z: the loot rides the letter, not yet in the hold", back != null && back.hasReward() && back.reward.contains("scrap 37") && back.reward.contains("item SCRAP_COLLECTOR")
     && v.storageScrap() == scrapBefore);
   Transmissions.claim(back, -1);
   Setup.chk("Z: claimed from the letter, it's in the hold", v.storageScrap() == scrapBefore + 37);
  }
  HomePlanet.immersiveNotifications = false;
  // the reputation, as the game scores it: the scrap a tenth, a death -10, everyone successful +2, nobody -1
  HomePlanet.reputationOn = true;
  int total = Reputation.total(v);
  crew = ExpT.hold(v, "slug", "slug");
  Expeditions.send(v, Expeditions.board(v).get(0).slot, crew, new Random(4));
  a = Expeditions.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  r = Expeditions.roll("nebula", a.crew, new Random(1));
  for (Expeditions.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 4; f.item = null; }
  r.job = "attack"; r.prize = null; r.scrap = 24;
  Expeditions.bringHome(v, a, r);
  Setup.chk("Z: 24 scrap and everyone successful: +4 reputation, logged", Reputation.total(v) == total + 4 && Reputation.log(v).contains("Expedition: Nebula, Attack (+4)"));
  total = Reputation.total(v);
  crew = ExpT.hold(v, "slug", "slug");
  Expeditions.send(v, Expeditions.board(v).get(0).slot, crew, new Random(4));
  a = Expeditions.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  r = Expeditions.roll("rock", a.crew, new Random(1));
  for (Expeditions.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 2; f.item = null; }
  r.fates.get(0).died = true; r.fates.get(0).band = 0; r.job = "spiders"; r.prize = null; r.scrap = 3;
  Expeditions.bringHome(v, a, r);
  Setup.chk("Z: a death and nobody successful: -11", Reputation.total(v) == total - 11);
  HomePlanet.reputationOn = false;
  Random rr = new Random(7); int skilled = 0;
  for (int i = 0; i < 400; i++) { CrewState c = Expeditions.recruit(rr); int lv = 0; for (int x : homeplanet.model.Crew.skillLevels(c)) lv += x; if (lv > 0) skilled++; }
  Setup.chk("Z: about one recruit in twenty comes with a skill (" + skilled + " of 400)", skilled >= 8 && skilled <= 40);
 }

 /** Experience and the report's faces: the job's skill gains by outcome (none on Negotiate or Rescue); each face as they came home. */
 static void experience(Vault v) throws Exception {
  List<CrewState> crew = ExpT.hold(v, "human", "human", "human");
  Expeditions.send(v, Expeditions.board(v).get(0).slot, crew, new Random(9));
  List<Expeditions.Away> aways = Expeditions.away(v); Expeditions.Away aw = aways.get(aways.size() - 1);
  Expeditions.Result r = aw.result(); r.job = "repair"; r.prize = null; r.hazard = null; r.hacker = null;
  int[] bands = {1, 3, 5};
  for (int i = 0; i < 3; i++) { Expeditions.Fate f = r.fates.get(i); f.band = bands[i]; f.died = f.captured = f.infirmary = f.worn = false; f.item = null; f.crew.setHealth(100); f.skill = 4; f.other = false; }
  int[] before = new int[3]; for (int i = 0; i < 3; i++) before[i] = homeplanet.model.Skills.points(r.fates.get(i).crew, 4);
  Expeditions.Report rep = Expeditions.bringHome(v, aw, r);
  int[] gain = new int[3]; for (int i = 0; i < 3; i++) gain[i] = homeplanet.model.Skills.points(r.fates.get(i).crew, 4) - before[i];
  Setup.chk("X: repair experience by outcome: injured " + gain[0] + ", successful " + gain[1] + ", extremely " + gain[2] + "; the injured at half health (" + r.fates.get(0).crew.getHealth() + ")",
    gain[0] == 1 && gain[1] == 2 && gain[2] == 4 && r.fates.get(0).crew.getHealth() == 50); // 6.39: fixed points by skill
  Setup.chk("X: the report's faces, as they came home", rep.faces.size() == 3 && "".equals(rep.faces.get(0).state) && rep.faces.get(0).crew.getHealth() == 50);
  // Infection (6.40: repair first, then pilot and shields) teaches the well; the dead and the infirmary are faces too, and the letter keeps them
  crew = ExpT.hold(v, "human", "human", "human");
  Expeditions.send(v, Expeditions.board(v).get(0).slot, crew, new Random(10));
  aways = Expeditions.away(v); aw = aways.get(aways.size() - 1);
  r = aw.result(); r.job = "infection"; r.prize = null; r.hazard = null; r.hacker = null;
  for (int i = 0; i < 3; i++) { Expeditions.Fate f = r.fates.get(i); f.band = 5; f.died = f.captured = f.infirmary = f.worn = false; f.item = null; f.crew.setHealth(100); f.skill = -1; }
  Expeditions.assignRoles("infection", r.fates, new Random(3));
  r.fates.get(0).band = 0; r.fates.get(0).died = true; r.fates.get(1).band = 1; r.fates.get(1).infirmary = true;
  int pts = 0; for (int k = 0; k < 6; k++) pts += homeplanet.model.Skills.points(r.fates.get(2).crew, k);
  String letter = Expeditions.letterKey(aw);
  rep = Expeditions.bringHome(v, aw, r);
  int ptsAfter = 0; for (int k = 0; k < 6; k++) ptsAfter += homeplanet.model.Skills.points(r.fates.get(2).crew, k);
  List<Expeditions.Face> kept = Expeditions.facesFor(v, letter);
  Setup.chk("X: Infection teaches the well (" + pts + " -> " + ptsAfter + "); the dead, the infirmary and the well each a face, kept for the letter (" + kept.size() + ")",
    pts < ptsAfter && rep.faces.size() == 3 && "dead".equals(rep.faces.get(0).state) && "infirmary".equals(rep.faces.get(1).state) && "".equals(rep.faces.get(2).state)
    && kept.size() == 3 && "dead".equals(kept.get(0).state) && kept.get(1).crew.getName().equals(rep.faces.get(1).crew.getName()));
 }
 /** The roles (6.39, heromedel and McCarthy): a role each, never NA; the best at the primary gets it most of the time; fixed points; every skill at about the same pace. */
 static void roles() {
  String[] jobs = {"defend", "attack", "negotiate", "boarded", "rescue", "salvage", "scout", "repair", "transport", "lost", "escort", "capture", "board", "hijack", "infection", "spiders"};
  boolean three = true; List<String> bad = new ArrayList<String>();
  for (String job : jobs) for (int seed = 0; seed < 50; seed++) {
   List<Expeditions.Fate> fs = new ArrayList<Expeditions.Fate>();
   for (int i = 0; i < 3; i++) fs.add(new Expeditions.Fate(Commission.volunteer("human", new Random(seed * 7 + i))));
   Expeditions.assignRoles(job, fs, new Random(seed));
   Expeditions.Roles r = Expeditions.roles(job);
   Set<Integer> got = new HashSet<Integer>();
   for (Expeditions.Fate f : fs) { if (f.skill >= 0 && r.na[f.skill]) bad.add(job + " NA " + f.skill); if (f.skill >= 0) got.add(f.skill); if (f.other) bad.add(job + " other with three"); }
   if (got.size() != 3) three = false;
  }
  Setup.chk("R: three crew get the three roles of every job, never an NA skill" + (bad.isEmpty() ? "" : " " + bad.subList(0, Math.min(5, bad.size()))), three && bad.isEmpty());
  // five crew: two left over get "other" skills, never NA (Giant Spiders: only its own non-NA skills)
  boolean others = true;
  for (int seed = 0; seed < 50; seed++) for (String job : new String[] {"board", "spiders"}) {
   List<Expeditions.Fate> fs = new ArrayList<Expeditions.Fate>();
   for (int i = 0; i < 5; i++) fs.add(new Expeditions.Fate(Commission.volunteer("human", new Random(seed * 11 + i))));
   Expeditions.assignRoles(job, fs, new Random(seed)); int o = 0;
   for (Expeditions.Fate f : fs) { if (f.skill < 0 || Expeditions.roles(job).na[f.skill]) others = false; if (f.other) o++; }
   if (o != 2) others = false;
  }
  Setup.chk("R: anyone left over gets one of the job's other skills, never NA", others);
  // a clear best fighter on Board gets combat about 3 times in 4 (or more: a missed roll goes to anyone, him too)
  int combat = 0, n = 4000;
  for (int seed = 0; seed < n; seed++) {
   CrewState ace = Commission.volunteer("human", new Random(1)); homeplanet.model.Skills.set(ace, 5, 2 * homeplanet.model.Skills.interval(ace, 5));
   List<Expeditions.Fate> fs = new ArrayList<Expeditions.Fate>(); fs.add(new Expeditions.Fate(Commission.volunteer("human", new Random(2)))); fs.add(new Expeditions.Fate(ace)); fs.add(new Expeditions.Fate(Commission.volunteer("human", new Random(3))));
   Expeditions.assignRoles("board", fs, new Random(seed * 31L + 7));
   if (fs.get(1).skill == 5) combat++;
  }
  Setup.chk("R: the best fighter on Board gets combat about 3 times in 4 (" + combat + " of " + n + ")", combat > n * 0.72 && combat < n * 0.9);
  // 6.40 (heromedel): a crew member in a role gets their role's own line, a whole sentence, the stars before it
  Random rq = new Random(41); int starred = 0, old = 0, stray = 0, named = 0;
  for (int i = 0; i < 3000; i++) {
   Expeditions.Result rr = Expeditions.roll(i % 2 == 0 ? "civilian" : "zoltan", party("human", "engi", "mantis"), rq);
   if (rr.report.contains("{")) stray++;
   for (String l : rr.report.split("\n")) {
    if (!l.startsWith("\u2605") && !l.startsWith("\u2717")) continue;
    starred++;
    if (l.matches(".*\\bwas (very |extremely )?successful.*")) old++;
    for (Expeditions.Fate f : rr.fates) if (l.contains(f.name())) { named++; break; }
   }
  }
  Setup.chk("S: crew in a role get their role's own line, stars first, their name in it (" + starred + " starred, " + named + " named, " + old + " the band's own)", starred > 1000 && named == starred && old == 0 && stray == 0);
  // fixed points by skill and roll: a natural 20, a success, a failure; "other" half, at least 1
  Setup.chk("P: the points: a natural 20 pays pilot 4, shields 14, weapons 20, combat 2; a success 2, 7, 10, 1; failed 1, 3, 5, 0; the dead nothing",
    Expeditions.training(0, 5, false) == 4 && Expeditions.training(2, 5, false) == 14 && Expeditions.training(3, 5, false) == 20 && Expeditions.training(5, 5, false) == 2
    && Expeditions.training(0, 3, false) == 2 && Expeditions.training(2, 4, false) == 7 && Expeditions.training(3, 3, false) == 10 && Expeditions.training(5, 3, false) == 1
    && Expeditions.training(0, 2, false) == 1 && Expeditions.training(2, 1, false) == 3 && Expeditions.training(5, 2, false) == 0 && Expeditions.training(3, 0, false) == 0);
  Setup.chk("P: an other role earns half, rounded down, at least 1 (combat success 1, weapons 5, a failed combat still 0)",
    Expeditions.training(5, 3, true) == 1 && Expeditions.training(3, 3, true) == 5 && Expeditions.training(2, 5, true) == 7 && Expeditions.training(5, 2, true) == 0 && Expeditions.training(0, 2, true) == 1);
  // thousands of expeditions with fresh crew: each skill's points against its interval, all within a factor of two
  double[] pts = new double[6]; Random rr = new Random(39); String[] sectors = Expeditions.sectors().toArray(new String[0]);
  for (int i = 0; i < 4000; i++) {
   List<CrewState> party = new ArrayList<CrewState>(); String[] races = {"human", "engi", "mantis", "rock", "slug", "energy", "crystal", "anaerobic"};
   for (int k = 0; k < 3; k++) party.add(Commission.volunteer(races[rr.nextInt(races.length)], new Random(rr.nextLong())));
   Expeditions.Result r = Expeditions.roll(sectors[rr.nextInt(sectors.length)], party, new Random(rr.nextLong()));
   for (Expeditions.Fate f : r.fates) if (!f.died && f.skill >= 0) pts[f.skill] += Expeditions.training(f.skill, f.band, f.other) / (double) homeplanet.model.Skills.interval(f.crew, f.skill);
  }
  double lo = Double.MAX_VALUE, hi = 0; StringBuilder each = new StringBuilder();
  String[] names = {"pilot", "engines", "shields", "weapons", "repair", "combat"};
  for (int k = 0; k < 6; k++) { lo = Math.min(lo, pts[k]); hi = Math.max(hi, pts[k]); each.append(names[k]).append(' ').append(String.format("%.0f", pts[k])).append(k < 5 ? ", " : ""); }
  Setup.chk("P: 4000 expeditions, levels gained per skill within a factor of two of each other (" + each + ")", hi <= 2 * lo);
 }
 /** Every skill mastered: both levels' points, and FTL's marks with them. */
 static void master(CrewState c) { for (int i = 0; i < 6; i++) homeplanet.model.Skills.set(c, i, 2 * homeplanet.model.Skills.interval(c, i)); }
}
