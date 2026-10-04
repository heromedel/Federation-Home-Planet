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
 prizes(v);
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
 static void words() {
  Setup.chk("W: the words file has an event and six bands for every job, every hazard, every sector's offer and the prizes " + Assignments.missingWords(), Assignments.missingWords().isEmpty());
 }
 static void board(Vault v) throws Exception {
  List<Assignments.Offer> b = Assignments.board(v);
  Set<String> ids = new HashSet<String>(); for (Assignments.Offer o : b) ids.add(o.sector);
  Setup.chk("B: three sectors on offer, all different, each with its words", b.size() == 3 && ids.size() == 3 && !b.get(0).words.isEmpty() && !b.get(0).title().isEmpty());
  List<Assignments.Offer> again = Assignments.board(v);
  Setup.chk("B: the same board until an offer comes down", again.get(0).sector.equals(b.get(0).sector) && again.get(2).sector.equals(b.get(2).sector));
  // Abandoned only with Advanced Edition
  Random rng = new Random(3); boolean abandoned = false, crystalRare = true; int crystal = 0;
  for (int i = 0; i < 400; i++) { String s = Assignments.drawSector(rng, new ArrayList<String>(), false); if ("abandoned".equals(s)) abandoned = true; if ("crystal".equals(s)) crystal++; }
  Setup.chk("B: Abandoned is never drawn without Advanced Edition; Crystal rarely (" + crystal + " of 400)", !abandoned && crystal > 0 && crystal < 40);
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
  // sent hurt: half their own bonuses
  List<CrewState> h = party("slug"); h.get(0).setHealth(30);
  Setup.chk("R: a crew member sent hurt counts as hurt", Assignments.roll("nebula", h, new Random(2)).fates.get(0).crew.getHealth() == 30);
 }
 static void report() {
  Random rng = new Random(21); Assignments.Result r = null;
  for (int i = 0; i < 500 && (r == null || r.hazard == null || !r.dead().isEmpty()); i++) r = Assignments.roll("nebula", party("slug", "human", "mantis"), rng);
  String t = r.report;
  System.out.println(t);
  Setup.chk("P: the frame: the heading, the sector, the crew line, a line a crew member, the total", t.startsWith("-- Expedition Report --\nSector: Nebula\nDue to events during the assignment the crew\n")
    && t.contains("\n" + r.fates.get(0).name() + " ") && t.contains("\n" + r.fates.get(2).name() + " ") && t.endsWith("Total Reward: " + r.scrap + " scrap"));
  Setup.chk("P: never a roll, a die or a percentage", !t.contains("%") && !t.toLowerCase().contains("d20") && !t.toLowerCase().contains("roll") && !t.contains("+1") && !t.contains("-1"));
  Setup.chk("P: a hazard gets its line", r.hazard != null && (t.contains("solar flare") || t.contains("asteroid") || t.contains("pulsar") || t.contains("plasma storm")));
 }
 static void awayAndBack(Vault v) throws Exception {
  List<CrewState> crew = ExpT.hold(v, "slug", "human", "engi", "rock");
  List<Assignments.Offer> b = Assignments.board(v);
  int beforeScrap = v.storageScrap(), beforeBeacons = v.beaconsSeen();
  Assignments.send(v, b.get(1).slot, crew.subList(0, 2), new Random(4));
  List<Assignments.Away> away = Assignments.away(v);
  Setup.chk("A: a detail away: out of the Cargo Hold, named in the file, due in 1 to 3 beacons; setting out took a beacon", away.size() == 1 && away.get(0).crew.size() == 2 && away.get(0).names().contains(crew.get(0).getName())
    && Assignments.holdCrew(v).size() == 2 && away.get(0).until >= v.beaconsSeen() + 1 && away.get(0).until <= v.beaconsSeen() + 3 && v.beaconsSeen() == beforeBeacons + 1);
  Setup.chk("A: the offer taken is replaced", Assignments.board(v).size() == 3 && Assignments.board(v).get(1).sector != null);
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
  int missiles = v.readCopy(v.storage()).save.getPlayerShip().getMissilesAmt();
  Assignments.bringHome(v, a, r);
  Setup.chk("Z: a Hijack's prize ship is in the Junkyard; the items came home", Derelicts.current(v).size() == listingsBefore + 1 && r.prizeDetail != null
    && v.readCopy(v.storage()).save.getPlayerShip().getMissilesAmt() == missiles + 4);
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
  Setup.chk("Z: a Rescue's recruit waits in the Cargo Hold, named in the report", r.recruit != null && Assignments.holdCrew(v).size() == 2 && rep.text.contains(r.recruit.getName()));
 }
}
