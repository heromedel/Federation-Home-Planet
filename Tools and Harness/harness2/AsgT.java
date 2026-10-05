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
 twoDueAtOnce(v);
 prizes(v);
 experience(v);
 Setup.done();
}
 static void tables() {
  boolean same = true;
  for (String s : Assignments.sectors()) if (Assignments.weightTotal(s) != 136) same = false;
  Setup.chk("T: every sector's job weights total 136", same && Assignments.jobWeight("civilian", "negotiate") == 15 && Assignments.jobWeight("civilian", "spiders") == 1 && Assignments.jobWeight("nebula", "lost") == 13);
  Setup.chk("T: races by job: Engi good at repair, bad at attack; a Mantis bad at negotiating", Assignments.raceJob("engi", "repair") == 10 && Assignments.raceJob("engi", "attack") == -10 && Assignments.raceJob("mantis", "negotiate") == -10 && Assignments.raceJob("human", "scout") == 0);
  Setup.chk("T: races by sector: Slug in a nebula +10, Lanius in Engi space +20, everyone but Crystal and Lanius -10 in Abandoned, Rock's own +10 wins there, nobody penalised in rebel or Crystal space",
    Assignments.raceSector("slug", "nebula") == 10 && Assignments.raceSector("anaerobic", "engi") == 20 && Assignments.raceSector("human", "abandoned") == -10 && Assignments.raceSector("crystal", "abandoned") == 0
    && Assignments.raceSector("anaerobic", "abandoned") == 0 && Assignments.raceSector("rock", "abandoned") == 10 && Assignments.raceSector("mantis", "rebel") == 0 && Assignments.raceSector("slug", "crystal") == 0);
  Setup.chk("T: the d20's bands", Assignments.band(1) == 0 && Assignments.band(5) == 1 && Assignments.band(9) == 2 && Assignments.band(15) == 3 && Assignments.band(19) == 4 && Assignments.band(20) == 5);
 }
 static void words() throws Exception {
  Setup.chk("W: the words file has an event and six bands for every job, every hazard, every sector's offer and the prizes " + Assignments.missingWords(), Assignments.missingWords().isEmpty());
  Setup.chk("W: every marked line is marked for a real sector (captured: real captors) " + Assignments.strayWords(), Assignments.strayWords().isEmpty());
  // held against FTL's own event text: no run of six words the same
  String ftl = new String(ExpT.readAll(net.blerf.ftl.parser.DataManager.get().getResourceInputStream("data/text_events.xml")), "UTF-8").replaceAll("<[^>]*>", " ");
  Set<String> shingles = new HashSet<String>(); List<String> w = ExpT.words(ftl);
  for (int i = 0; i + 6 <= w.size(); i++) shingles.add(String.join(" ", w.subList(i, i + 6)));
  List<String> copied = new ArrayList<String>();
  for (String line : Assignments.allWords()) { List<String> m = ExpT.words(line); for (int i = 0; i + 6 <= m.size(); i++) if (shingles.contains(String.join(" ", m.subList(i, i + 6)))) { copied.add(String.join(" ", m.subList(i, i + 6))); break; } }
  Setup.chk("W: no run of six words copied from FTL's events (" + Assignments.allWords().size() + " lines) " + copied, copied.isEmpty());
  // captured lines know who took them, and say she for a woman
  Random rng = new Random(31); int pirate = 0, slaver = 0, she = 0, stray = 0;
  for (int i = 0; i < 40000 && (pirate == 0 || she == 0); i++) {
   String sector = i % 2 == 0 ? "pirate" : "mantis";
   List<CrewState> p = party("human", "human", "human"); for (CrewState c : p) c.setMale(false);
   Assignments.Result r = Assignments.roll(sector, p, rng);
   String t = r.report;
   if (t.contains("{")) stray++;
   if (t.contains("pirate ship before it broke away")) pirate++;
   if ("pirate".equals(sector) && t.contains("taken by slavers")) slaver++;
   if (t.contains("she's all right out there") || t.contains("never see her again")) she++;
  }
  // a race that shrugs off the hazard says so; nobody else does
  rng = new Random(32); int rockSaid = 0, rockFlares = 0, humanSaid = 0, rockHurt = 0, hurtSaid = 0;
  for (int i = 0; i < 40000 && (rockFlares < 30 || rockHurt < 10); i++) {
   Assignments.Result rr = Assignments.roll("civilian", party("rock"), rng);
   Assignments.Result hr = Assignments.roll("civilian", party("human"), rng);
   if ("flare".equals(hr.hazard) && (hr.report.contains("Rock doesn't burn") || hr.report.contains("straight through it"))) humanSaid++;
   if (!"flare".equals(rr.hazard)) continue;
   Assignments.Fate f = rr.fates.get(0);
   if (f.died || f.captured || f.infirmary) continue;
   boolean said = rr.report.contains("Rock doesn't burn") || rr.report.contains("straight through it");
   if (f.band == 1) { rockHurt++; if (said || rr.report.contains("through a fire")) hurtSaid++; continue; } // an injury trumps it, and names another cause
   rockFlares++;
   if (said) rockSaid++;
  }
  Setup.chk("W: a Rock in a solar flare's job says the fire didn't touch them (" + rockSaid + " of " + rockFlares + "); a human never does (" + humanSaid + ")", rockFlares > 0 && rockSaid == rockFlares && humanSaid == 0);
  Setup.chk("W: an injured Rock in a flare says nothing of the fire, and isn't hurt by one (" + hurtSaid + " of " + rockHurt + ")", rockHurt > 0 && hurtSaid == 0);
  // the rebels' Anti-Ship Battery: rebel space with Advanced Edition only; each Engi a chance in three of hacking it for everyone
  rng = new Random(33); int battOff = 0, battElsewhere = 0, batt1 = 0, hack1 = 0, batt3 = 0, hack3 = 0, wrongPay = 0, saidHack = 0;
  for (int i = 0; i < 60000 && (batt1 < 300 || batt3 < 30); i++) {
   if ("battery".equals(Assignments.roll("rebel", party("human"), rng, false).hazard)) battOff++;
   if ("battery".equals(Assignments.roll("pirate", party("human"), rng, true).hazard)) battElsewhere++;
   Assignments.Result one = Assignments.roll("rebel", party("engi", "human"), rng, true);
   if ("battery".equals(one.hazard)) { batt1++; if (one.hacker != null) { hack1++; if (one.report.contains(one.hacker.getName() + " hacked") || one.report.contains(one.hacker.getName() + " spent an hour")) saidHack++; } }
   Assignments.Result three = Assignments.roll("rebel", party("engi", "engi", "engi"), rng, true);
   if ("battery".equals(three.hazard)) { batt3++; if (three.hacker != null) hack3++; }
  }
  Setup.chk("W: the Anti-Ship Battery never for a detail sent before it existed (" + battOff + ") nor outside rebel space (" + battElsewhere + "); one Engi hacks it about a third of the time (" + hack1 + " of " + batt1 + "), and the report names them (" + saidHack + "); three always (" + hack3 + " of " + batt3 + ")",
    battOff == 0 && battElsewhere == 0 && batt1 > 0 && hack1 > batt1 / 5 && hack1 < batt1 / 2 && saidHack == hack1 && batt3 > 0 && hack3 == batt3);
  // the reward: hacked, nobody pays the battery's 10; not hacked, both do: about 20 points of the pot between them, on average
  Random same = new Random(34); long hackedSum = 0, plainSum = 0; int hackedN = 0, plainN = 0;
  for (int i = 0; i < 200000 && (hackedN < 400 || plainN < 400); i++) {
   Assignments.Result a1 = Assignments.roll("rebel", party("engi", "human"), same, true);
   if (!"battery".equals(a1.hazard)) continue;
   if (a1.hacker != null) { hackedSum += a1.multiplier; hackedN++; } else { plainSum += a1.multiplier; plainN++; }
  }
  double gap = hackedSum / (double) Math.max(1, hackedN) - plainSum / (double) Math.max(1, plainN);
  Setup.chk("W: hacked, nobody pays the battery; not hacked, both do (on average " + Math.round(gap) + " points of the pot between them)", hackedN > 0 && plainN > 0 && gap > 12 && gap < 28);
  Setup.chk("W: a capture in pirate space can say pirates (" + pirate + "), never slavers (" + slaver + "); a woman taken is she (" + she + "); no token left unfilled (" + stray + ")", pirate > 0 && slaver == 0 && she > 0 && stray == 0);
 }
 static void board(Vault v) throws Exception {
  List<Assignments.Offer> b = Assignments.board(v);
  Set<String> ids = new HashSet<String>(); for (Assignments.Offer o : b) ids.add(o.sector);
  Setup.chk("B: three sectors on offer, all different, each with its words", b.size() == 3 && ids.size() == 3 && !b.get(0).words.isEmpty() && !b.get(0).title().isEmpty());
  List<Assignments.Offer> again = Assignments.board(v);
  Setup.chk("B: the same board until an offer comes down", again.get(0).sector.equals(b.get(0).sector) && again.get(2).sector.equals(b.get(2).sector));
  // every sector can come up, Crystal rarely
  Random rng = new Random(3); boolean abandoned = false; int crystal = 0;
  for (int i = 0; i < 400; i++) { String s = Assignments.drawSector(rng, new ArrayList<String>()); if ("abandoned".equals(s)) abandoned = true; if ("crystal".equals(s)) crystal++; }
  Setup.chk("B: Abandoned comes up; Crystal rarely (" + crystal + " of 400)", abandoned && crystal > 0 && crystal < 40);
 }
 static List<CrewState> party(String... races) { List<CrewState> l = new ArrayList<CrewState>(); Random r = new Random(5); for (String x : races) l.add(Commission.volunteer(x, r)); return l; }
 static void rolls() {
  Random rng = new Random(11);
  int[] bands = new int[6]; int min = 999, max = 0, deaths = 0, spiderInjured = 0, captured = 0, items = 0, prizes = 0; long sum = 0; int n = 3000;
  for (int i = 0; i < n; i++) {
   Assignments.Result r = Assignments.roll("nebula", party("slug", "slug", "slug"), rng);
   for (Assignments.Fate f : r.fates) { bands[f.band]++; if (f.died) deaths++; if (f.captured) captured++; if (f.item != null) items++; if ("spiders".equals(r.job) && f.band == 1 && !f.died) spiderInjured++; }
   if (r.prize != null) prizes++;
   min = Math.min(min, r.scrap); max = Math.max(max, r.scrap); sum += r.scrap;
  }
  System.out.println("three Slugs in a nebula, " + n + " runs: average " + (sum / n) + " scrap, " + min + " to " + max + "; bands " + Arrays.toString(bands) + "; items " + items + ", prizes " + prizes);
  Setup.chk("R: the pot is never under 1, averages in the twenties for a good detail, tops out past 40", min >= 1 && sum / n >= 15 && sum / n <= 32 && max >= 40);
  Setup.chk("R: every band comes up; deaths are rare (" + deaths + " of " + 3 * n + "); a 20 sometimes finds an item (" + items + ")", bands[0] > 0 && bands[5] > 0 && deaths < 3 * n / 10 && items > 0);
  Setup.chk("R: an injury on Giant Spiders is a death; a Get Boarded injury can mean capture (" + captured + ")", spiderInjured == 0 && captured > 0);
  // a lazy detail earns less than a fitting one
  rng = new Random(12); long lazy = 0, fit = 0;
  for (int i = 0; i < n; i++) { lazy += Assignments.roll("mantis", party("slug", "slug", "slug"), rng).scrap; fit += Assignments.roll("mantis", party("anaerobic", "anaerobic", "anaerobic"), rng).scrap; }
  Setup.chk("R: three Lanius in Mantis space out-earn three Slugs there (" + fit / n + " to " + lazy / n + ")", fit > lazy);
  // skills add: a level-2 fighter on a combat job
  rng = new Random(13); long raw = 0, skilled = 0;
  for (int i = 0; i < n; i++) {
   List<CrewState> p = party("human"); raw += Assignments.roll("rock", p, rng).scrap;
   List<CrewState> q = party("human"); q.get(0).setCombatMasteryOne(true); q.get(0).setCombatMasteryTwo(true); q.get(0).setPilotMasteryOne(true); q.get(0).setPilotMasteryTwo(true);
   q.get(0).setEngineMasteryOne(true); q.get(0).setEngineMasteryTwo(true); q.get(0).setShieldMasteryOne(true); q.get(0).setShieldMasteryTwo(true);
   q.get(0).setWeaponMasteryOne(true); q.get(0).setWeaponMasteryTwo(true); q.get(0).setRepairMasteryOne(true); q.get(0).setRepairMasteryTwo(true);
   skilled += Assignments.roll("rock", q, rng).scrap;
  }
  Setup.chk("R: skill in the job's skill pays (" + skilled / n + " to " + raw / n + ")", skilled > raw * 11 / 10);
  // hurt twice: sent wounded and wounded again, a third each: die, the infirmary, half what they had; never for the unhurt
  Random tw = new Random(14); int twice = 0, twDied = 0, twInf = 0, twWorn = 0, freshOdd = 0;
  for (int i = 0; i < 40000 && twice < 2000; i++) {
   List<CrewState> wounded = party("human"); wounded.get(0).setHealth(30);
   Assignments.Result x = Assignments.roll("civilian", wounded, tw);
   Assignments.Fate f = x.fates.get(0);
   if (f.band == 1 && !"spiders".equals(x.job) && !f.captured) { twice++; if (f.died) twDied++; else if (f.infirmary) twInf++; else if (f.worn) twWorn++; }
   Assignments.Result y = Assignments.roll("civilian", party("human"), tw);
   Assignments.Fate g = y.fates.get(0);
   if (g.worn || (g.band == 1 && g.died && !"spiders".equals(y.job))) freshOdd++;
  }
  Setup.chk("R: hurt twice, a third each: " + twDied + " died, " + twInf + " to the infirmary, " + twWorn + " worn down, of " + twice + "; never for someone sent whole (" + freshOdd + ")",
    twice > 0 && twDied > twice / 4 && twDied < twice * 4 / 10 && twInf > twice / 4 && twInf < twice * 45 / 100 && twWorn > twice / 4 && twWorn < twice * 4 / 10 && freshOdd == 0);
  // sent hurt: half their own bonuses
  List<CrewState> h = party("slug"); h.get(0).setHealth(30);
  Setup.chk("R: a crew member sent hurt counts as hurt", Assignments.roll("nebula", h, new Random(2)).fates.get(0).crew.getHealth() == 30);
 }
 static void report() {
  Random rng = new Random(21); Assignments.Result r = null;
  for (int i = 0; i < 500 && (r == null || r.hazard == null || !r.dead().isEmpty()); i++) r = Assignments.roll("nebula", party("slug", "human", "mantis"), rng);
  String t = r.report;
  System.out.println(t);
  Setup.chk("P: the frame: the heading, the sector, a blank line, a setup ending on the crew, a line a crew member, the total", t.startsWith("-- Expedition Report --\nSector: Nebula\n\n")
    && t.split("\n")[3].endsWith(" the crew")
    && t.contains("\n" + r.fates.get(0).name() + " ") && t.contains("\n" + r.fates.get(2).name() + " ") && t.endsWith("Total Reward: " + r.scrap + " scrap"));
  // the setups: heromedel's, the general ones and the job's own all come up; two of a detail never share an outcome line
  Random fr = new Random(41); int his = 0, other = 0, same = 0, pairs = 0;
  for (int i = 0; i < 3000; i++) {
   Assignments.Result x = Assignments.roll("civilian", party("human", "human"), fr);
   String setup = x.report.split("\n")[3];
   if (setup.equals("Due to events during the assignment the crew")) his++; else other++;
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
  Assignments.Result r = new Assignments.Result(); r.sector = "civilian"; r.job = "repair";
  for (int i = 0; i < 3; i++) r.fates.add(new Assignments.Fate(party("human").get(0)));
  for (Assignments.Fate f : r.fates) f.band = 3;
  int lo = 99, hi = 0; Random rng = new Random(5);
  for (int i = 0; i < 300; i++) { int d = Assignments.days(r, party("human", "human", "human"), rng); lo = Math.min(lo, d); hi = Math.max(hi, d); }
  Setup.chk("D: a quiet job in a quiet sector takes a week or two, 7 to 14 days (" + lo + " to " + hi + ")", lo == 7 && hi == 14);
  r.sector = "abandoned"; lo = 99; hi = 0;
  for (int i = 0; i < 300; i++) { int d = Assignments.days(r, party("human", "human", "human"), rng); lo = Math.min(lo, d); hi = Math.max(hi, d); }
  Setup.chk("D: Abandoned always adds a day (" + lo + " to " + hi + ")", lo == 8 && hi == 15);
  r.sector = "civilian"; r.job = "lost"; for (Assignments.Fate f : r.fates) f.band = 2; lo = 99; hi = 0;
  for (int i = 0; i < 300; i++) { int d = Assignments.days(r, party("human", "human", "human"), rng); lo = Math.min(lo, d); hi = Math.max(hi, d); }
  Setup.chk("D: Got Lost with three failures is four days late, always (" + lo + " to " + hi + ")", lo == 11 && hi == 18);
  r.job = "repair"; lo = 99; hi = 0;
  for (int i = 0; i < 300; i++) { int d = Assignments.days(r, party("human", "human", "human"), rng); lo = Math.min(lo, d); hi = Math.max(hi, d); }
  Setup.chk("D: on another job a failure adds a day only sometimes (" + lo + " to " + hi + ")", lo == 7 && hi == 17);
  for (Assignments.Fate f : r.fates) f.band = 3; r.job = "scout"; lo = 99; hi = 0;
  for (int i = 0; i < 300; i++) { int d = Assignments.days(r, party("human", "human", "human"), rng); lo = Math.min(lo, d); hi = Math.max(hi, d); }
  Setup.chk("D: a Scout is a day quicker (" + lo + " to " + hi + ")", lo == 6 && hi == 13);
  r.job = "hijack"; r.prize = "ship"; r.fates.get(0).item = "fuel:3"; lo = 99; hi = 0;
  for (int i = 0; i < 300; i++) { int d = Assignments.days(r, party("human", "human", "human"), rng); lo = Math.min(lo, d); hi = Math.max(hi, d); }
  Setup.chk("D: a ship towed home and an item carried: three days more (" + lo + " to " + hi + ")", lo == 10 && hi == 17);
  r.prize = null; r.fates.get(0).item = null; int rocks = 0, humans = 0;
  for (int i = 0; i < 600; i++) { rocks += Assignments.days(r, party("rock", "rock", "rock"), rng); humans += Assignments.days(r, party("human", "human", "human"), rng); }
  Setup.chk("D: three Rocks are a day slower on average, one in three each (" + rocks / 600.0 + " to " + humans / 600.0 + " days)", rocks - humans > 400 && rocks - humans < 800);
  Setup.chk("D: never past the cap", Assignments.AWAY_CAP == 21);
 }
 /** 5.16 (heromedel's save): two details due on the same look each come home once; a third, not yet due, stays away. */
 static void twoDueAtOnce(Vault v) throws Exception {
  List<CrewState> crew = ExpT.hold(v, "human", "engi", "mantis", "slug", "rock", "human");
  for (int i = 0; i < 3; i++) Assignments.send(v, Assignments.board(v).get(0).slot, crew.subList(i * 2, i * 2 + 2), new Random(i));
  List<Assignments.Away> aw = Assignments.away(v);
  int[] u = new int[aw.size()]; for (int i = 0; i < u.length; i++) u[i] = aw.get(i).until; Arrays.sort(u);
  Setup.chk("M: three details away", aw.size() == 3 && u[1] < u[2]);
  while (v.beaconsSeen() < u[1]) v.countBeacon();
  List<Assignments.Report> back = Assignments.checkReturns(v);
  List<Assignments.Away> still = Assignments.away(v);
  Setup.chk("M: the two due come home on one look, the third stays away", back.size() == 2 && still.size() == 1 && still.get(0).until == u[2]);
  Setup.chk("M: and nothing comes home twice", Assignments.checkReturns(v).isEmpty());
  while (v.beaconsSeen() < u[2]) v.countBeacon();
  Setup.chk("M: the third comes home in its time", Assignments.checkReturns(v).size() == 1 && Assignments.away(v).isEmpty());
  Map<String, Integer> seen = new HashMap<String, Integer>();
  boolean twice = false;
  for (CrewState c : Assignments.holdCrew(v)) { String k = c.getName() + "/" + c.getRace(); if (seen.containsKey(k)) twice = true; seen.put(k, 1); }
  Setup.chk("M: nobody in the Cargo Hold twice", !twice);
 }
 static void awayAndBack(Vault v) throws Exception {
  List<CrewState> crew = ExpT.hold(v, "slug", "human", "engi", "rock");
  List<Assignments.Offer> b = Assignments.board(v);
  int beforeScrap = v.storageScrap(), beforeBeacons = v.beaconsSeen();
  Assignments.send(v, b.get(1).slot, crew.subList(0, 2), new Random(4));
  List<Assignments.Away> away = Assignments.away(v);
  Setup.chk("A: a detail away: out of the Cargo Hold, named in the file, due in a week to three; setting out passes no time (5.16)", away.size() == 1 && away.get(0).crew.size() == 2 && away.get(0).names().contains(crew.get(0).getName())
    && Assignments.holdCrew(v).size() == 2 && away.get(0).until >= v.beaconsSeen() + 6 && away.get(0).until <= v.beaconsSeen() + Assignments.AWAY_CAP && v.beaconsSeen() == beforeBeacons);
  Assignments.Result first = away.get(0).result(), same = away.get(0).result();
  Setup.chk("A: the result was rolled at setting out and rolls the same every time (the seed)", first.report.equals(same.report) && first.scrap == same.scrap && away.get(0).seed != 0);
  Setup.chk("A: the offer taken is replaced, by another sector", Assignments.board(v).size() == 3 && Assignments.board(v).get(1).sector != null && !Assignments.board(v).get(1).sector.equals(b.get(1).sector));
  Setup.chk("A: not back before their time", Assignments.checkReturns(v).isEmpty() && Assignments.away(v).size() == 1);
  while (v.beaconsSeen() < away.get(0).until) v.countBeacon();
  // a known result: both successful, no items
  Assignments.Result r = null; Random rng = new Random(1);
  while (r == null || r.dead().size() > 0 || r.fates.get(0).band < 3 || r.fates.get(1).band < 3 || r.fates.get(0).item != null || r.fates.get(1).item != null || r.prize != null || r.fates.get(0).captured || r.fates.get(1).captured)
   r = Assignments.roll(away.get(0).sector, away.get(0).crew, rng);
  Assignments.Report rep = Assignments.bringHome(v, away.get(0), r);
  Setup.chk("A: back: both in the Cargo Hold again, the scrap paid, the record gone, the report says it", Assignments.holdCrew(v).size() == 4 && v.storageScrap() == beforeScrap + r.scrap && Assignments.away(v).isEmpty()
    && rep.text.equals(r.report) && rep.title().startsWith("Back from "));
  int skill = 0; for (CrewState c : Assignments.holdCrew(v)) for (int lv : homeplanet.model.Crew.skillLevels(c)) skill += lv;
  Setup.chk("A: the station's round brings a due detail home by itself", Assignments.checkReturns(v).isEmpty());
  // the round's own report is the one rolled at setting out
  crew = ExpT.hold(v, "human", "engi");
  Assignments.send(v, Assignments.board(v).get(2).slot, crew, new Random(8));
  Assignments.Away due = Assignments.away(v).get(0); String expected = due.result().report;
  while (v.beaconsSeen() < due.until) v.countBeacon();
  List<Assignments.Report> reps = Assignments.checkReturns(v);
  Setup.chk("A: the round tells the report rolled at setting out", reps.size() == 1 && reps.get(0).text.equals(expected));
  for (Assignments.Pending x : Assignments.pending(v)) Assignments.decline(v, x);
  days();
  // an injury, a death and a capture written home
  crew = ExpT.hold(v, "slug", "human", "engi");
  Assignments.send(v, Assignments.board(v).get(0).slot, crew, new Random(4));
  away = Assignments.away(v);
  while (v.beaconsSeen() < away.get(0).until) v.countBeacon();
  r = Assignments.roll("mantis", away.get(0).crew, new Random(1));
  r.job = "boarded"; r.fates.get(0).died = true; r.fates.get(0).band = 0; r.fates.get(1).died = false; r.fates.get(1).captured = true; r.fates.get(1).band = 1; r.fates.get(1).infirmary = false;
  r.fates.get(2).died = false; r.fates.get(2).captured = false; r.fates.get(2).infirmary = true; r.fates.get(2).band = 1;
  Assignments.bringHome(v, away.get(0), r);
  List<Expeditions.Patient> inf = Expeditions.infirmary(v);
  File cap = new File(v.root, "captives.txt"); Properties cp = new Properties(); cp.load(new ByteArrayInputStream(SafeFiles.read(cap)));
  Setup.chk("A: the dead stay gone, the taken are among the captives (a ransom to come), the badly hurt in the infirmary at a quarter health",
    Assignments.holdCrew(v).size() == 0 && SaveHelper.getOwnCrew(v.readCopy(v.storage()).save.getPlayerShip()).size() == 1 && inf.size() == 1 && inf.get(0).name.equals(crew.get(2).getName())
    && cp.getProperty("0.name", "").equals(crew.get(1).getName()) && v.readCopy(v.storage()).save.getPlayerShip().getCrewList().get(0).getHealth() <= 25);
 }
 static void prizes(Vault v) throws Exception {
  List<CrewState> crew = ExpT.hold(v, "rock", "engi");
  int listingsBefore = Derelicts.current(v).size();
  Assignments.send(v, Assignments.board(v).get(0).slot, crew, new Random(4));
  Assignments.Away a = Assignments.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  Assignments.Result r = Assignments.roll("pirate", a.crew, new Random(1));
  for (Assignments.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 3; f.item = "missiles:2"; }
  r.job = "hijack"; r.prize = "ship";
  int missiles = v.readCopy(v.storage()).save.getPlayerShip().getMissilesAmt(), junked = v.junked().size(), docked = v.docked().size();
  Assignments.Report shipRep = Assignments.bringHome(v, a, r);
  List<Assignments.Pending> pend = Assignments.pending(v);
  int missilesNow = v.readCopy(v.storage()).save.getPlayerShip().getMissilesAmt(), listingsNow = Derelicts.current(v).size();
  boolean shipOk = pend.size() == 1 && "ship".equals(pend.get(0).kind) && pend.get(0).save.isFile()
    && r.prizeDetail != null && shipRep.text.contains(r.prizeDetail) && listingsNow == listingsBefore && missilesNow == missiles + 4;
  String why = shipOk ? "" : " [" + r.prizeDetail + " named: " + (r.prizeDetail != null && shipRep.text.contains(r.prizeDetail)) + "; pending " + pend.size() + (pend.isEmpty() ? "" : " " + pend.get(0).kind + " file " + pend.get(0).save.isFile())
    + "; missiles " + missiles + " -> " + missilesNow + "; listings " + listingsBefore + " -> " + listingsNow + "]"; // said in the FAIL line itself (the harness keeps only PASS and FAIL lines)
  Setup.chk("Z: a Hijack's prize ship waits on the commander's word, named in the report; the items came home" + why, shipOk);
  Assignments.accept(v, pend.get(0), true);
  Setup.chk("Z: taken to the Space Dock, she's docked and the question is gone", v.docked().size() == docked + 1 && v.junked().size() == junked && Assignments.pending(v).isEmpty() && !pend.get(0).save.isFile());
  crew = ExpT.hold(v, "rock", "engi");
  Assignments.send(v, Assignments.board(v).get(0).slot, crew, new Random(4));
  a = Assignments.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  r = Assignments.roll("pirate", a.crew, new Random(1));
  for (Assignments.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 3; f.item = null; }
  r.job = "hijack"; r.prize = "ship";
  Assignments.bringHome(v, a, r);
  Assignments.accept(v, Assignments.pending(v).get(0), false);
  Setup.chk("Z: or to the Junkyard", v.junked().size() == junked + 1 && v.docked().size() == docked + 1 && Assignments.pending(v).isEmpty());
  crew = ExpT.hold(v, "rock", "engi");
  Assignments.send(v, Assignments.board(v).get(0).slot, crew, new Random(4));
  a = Assignments.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  r = Assignments.roll("abandoned", a.crew, new Random(1));
  for (Assignments.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 4; f.item = null; }
  r.job = "salvage"; r.prize = "part";
  Assignments.bringHome(v, a, r);
  String sys = v.systemsFile().isFile() ? new String(SafeFiles.read(v.systemsFile()), "UTF-8") : "";
  Setup.chk("Z: a Salvage's prize part is among the stored systems, a bar broken", r.prizeDetail != null && sys.trim().split("\n").length >= 2 && sys.contains(" 1 1"));
  crew = ExpT.hold(v, "human");
  Assignments.send(v, Assignments.board(v).get(0).slot, crew, new Random(4));
  a = Assignments.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  r = Assignments.roll("civilian", a.crew, new Random(1));
  for (Assignments.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 5; f.item = null; }
  r.job = "rescue"; r.prize = "recruit";
  Assignments.Report rep = Assignments.bringHome(v, a, r);
  pend = Assignments.pending(v);
  Setup.chk("Z: a Rescue's recruit asks, named in the report, and isn't in the hold yet", r.recruit != null && pend.size() == 1 && "recruit".equals(pend.get(0).kind) && pend.get(0).crew.getName().equals(r.recruit.getName())
    && rep.text.contains(r.recruit.getName()) && Assignments.holdCrew(v).size() == 1 && pend.get(0).question().contains("sign on"));
  Assignments.decline(v, pend.get(0));
  Setup.chk("Z: sent on their way: not in the hold, no question left", Assignments.holdCrew(v).size() == 1 && Assignments.pending(v).isEmpty());
  // with Immersive Notifications on, the letter carries the question: the Space Dock doesn't ask
  HomePlanet.immersiveNotifications = true;
  crew = ExpT.hold(v, "human");
  Assignments.send(v, Assignments.board(v).get(0).slot, crew, new Random(4));
  a = Assignments.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  r = Assignments.roll("civilian", a.crew, new Random(1));
  for (Assignments.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 5; f.item = null; }
  r.job = "rescue"; r.prize = "recruit";
  rep = Assignments.bringHome(v, a, r);
  pend = Assignments.pending(v);
  String letterKey = "expedition:" + a.sentAt + ":" + a.index + ":" + String.join(",", a.names());
  Setup.chk("Z: the letter carries the question: the prize names it, the Space Dock leaves it be, the words say they wait in the lounge", pend.size() == 1 && letterKey.equals(pend.get(0).letter)
    && Assignments.pendingToAsk(v).isEmpty() && Assignments.pendingFor(v, letterKey) != null && rep.text.contains("The Station Lounge"));
  boolean delivered = false; for (Transmissions.Message m : Transmissions.load()) if (m.key.equals(letterKey)) delivered = true;
  Setup.chk("Z: and the letter is in the inbox", delivered);
  Assignments.accept(v, pend.get(0), false);
  Setup.chk("Z: answered from the letter, they're in the hold", Assignments.holdCrew(v).size() == 2 && Assignments.pendingFor(v, letterKey) == null);
  HomePlanet.immersiveNotifications = false;
  // the reputation, as the game scores it: the scrap a tenth, a death -10, everyone successful +2, nobody -1
  HomePlanet.reputationOn = true;
  int total = Reputation.total(v);
  crew = ExpT.hold(v, "slug", "slug");
  Assignments.send(v, Assignments.board(v).get(0).slot, crew, new Random(4));
  a = Assignments.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  r = Assignments.roll("nebula", a.crew, new Random(1));
  for (Assignments.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 4; f.item = null; }
  r.job = "attack"; r.prize = null; r.scrap = 24;
  Assignments.bringHome(v, a, r);
  Setup.chk("Z: 24 scrap and everyone successful: +4 reputation, logged", Reputation.total(v) == total + 4 && Reputation.log(v).contains("Expedition: Nebula, Attack (+4)"));
  total = Reputation.total(v);
  crew = ExpT.hold(v, "slug", "slug");
  Assignments.send(v, Assignments.board(v).get(0).slot, crew, new Random(4));
  a = Assignments.away(v).get(0);
  while (v.beaconsSeen() < a.until) v.countBeacon();
  r = Assignments.roll("rock", a.crew, new Random(1));
  for (Assignments.Fate f : r.fates) { f.died = false; f.captured = false; f.infirmary = false; f.band = 2; f.item = null; }
  r.fates.get(0).died = true; r.fates.get(0).band = 0; r.job = "spiders"; r.prize = null; r.scrap = 3;
  Assignments.bringHome(v, a, r);
  Setup.chk("Z: a death and nobody successful: -11", Reputation.total(v) == total - 11);
  HomePlanet.reputationOn = false;
  Random rr = new Random(7); int skilled = 0;
  for (int i = 0; i < 400; i++) { CrewState c = Assignments.recruit(rr); int lv = 0; for (int x : homeplanet.model.Crew.skillLevels(c)) lv += x; if (lv > 0) skilled++; }
  Setup.chk("Z: about one recruit in twenty comes with a skill (" + skilled + " of 400)", skilled >= 8 && skilled <= 40);
 }

 /** Experience and the report's faces: the job's skill gains by outcome (none on Negotiate or Rescue); each face as they came home. */
 static void experience(Vault v) throws Exception {
  List<CrewState> crew = ExpT.hold(v, "human", "human", "human");
  Assignments.send(v, Assignments.board(v).get(0).slot, crew, new Random(9));
  List<Assignments.Away> aways = Assignments.away(v); Assignments.Away aw = aways.get(aways.size() - 1);
  Assignments.Result r = aw.result(); r.job = "repair"; r.prize = null; r.hazard = null; r.hacker = null;
  int[] bands = {1, 3, 5};
  for (int i = 0; i < 3; i++) { Assignments.Fate f = r.fates.get(i); f.band = bands[i]; f.died = f.captured = f.infirmary = f.worn = false; f.item = null; f.crew.setHealth(100); }
  int[] before = new int[3]; for (int i = 0; i < 3; i++) before[i] = homeplanet.model.Skills.points(r.fates.get(i).crew, 4);
  Assignments.Report rep = Assignments.bringHome(v, aw, r);
  int[] gain = new int[3]; for (int i = 0; i < 3; i++) gain[i] = homeplanet.model.Skills.points(r.fates.get(i).crew, 4) - before[i];
  Setup.chk("X: repair experience by outcome: injured " + gain[0] + ", successful " + gain[1] + ", extremely " + gain[2] + "; the injured at half health (" + r.fates.get(0).crew.getHealth() + ")",
    gain[0] == 1 && gain[1] == 4 && gain[2] == 8 && r.fates.get(0).crew.getHealth() == 50);
  Setup.chk("X: the report's faces, as they came home", rep.faces.size() == 3 && "".equals(rep.faces.get(0).state) && rep.faces.get(0).crew.getHealth() == 50);
  // Negotiate teaches nothing; the dead and the infirmary are faces too, and the letter keeps them
  crew = ExpT.hold(v, "human", "human", "human");
  Assignments.send(v, Assignments.board(v).get(0).slot, crew, new Random(10));
  aways = Assignments.away(v); aw = aways.get(aways.size() - 1);
  r = aw.result(); r.job = "negotiate"; r.prize = null; r.hazard = null; r.hacker = null;
  for (int i = 0; i < 3; i++) { Assignments.Fate f = r.fates.get(i); f.band = 5; f.died = f.captured = f.infirmary = f.worn = false; f.item = null; f.crew.setHealth(100); }
  r.fates.get(0).band = 0; r.fates.get(0).died = true; r.fates.get(1).band = 1; r.fates.get(1).infirmary = true;
  int pts = 0; for (int k = 0; k < 6; k++) pts += homeplanet.model.Skills.points(r.fates.get(2).crew, k);
  String letter = "expedition:" + aw.sentAt + ":" + aw.index + ":" + String.join(",", aw.names());
  rep = Assignments.bringHome(v, aw, r);
  int ptsAfter = 0; for (int k = 0; k < 6; k++) ptsAfter += homeplanet.model.Skills.points(r.fates.get(2).crew, k);
  List<Assignments.Face> kept = Assignments.facesFor(v, letter);
  Setup.chk("X: Negotiate teaches no skill (" + pts + " -> " + ptsAfter + "); the dead, the infirmary and the well each a face, kept for the letter (" + kept.size() + ")",
    pts == ptsAfter && rep.faces.size() == 3 && "dead".equals(rep.faces.get(0).state) && "infirmary".equals(rep.faces.get(1).state) && "".equals(rep.faces.get(2).state)
    && kept.size() == 3 && "dead".equals(kept.get(0).state) && kept.get(1).crew.getName().equals(rep.faces.get(1).crew.getName()));
 }
}
