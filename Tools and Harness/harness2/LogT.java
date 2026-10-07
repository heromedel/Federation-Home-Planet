import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The master log and the Captain's Log (5.17): stardates, day lines and entry lines, Prior, the Cargo Bay's day. args: gamedir, world saves, work */
public class LogT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive(); HomePlanet.reputationOn = false;
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 Setup.chk("S: stardates: 1.1.1.1, a week on 1.1.2.1, a month on 1.2.1.1, a year on 2.1.1.1; before day 1, Prior",
   MasterLog.stardate(1).equals("1.1.1.1") && MasterLog.stardate(8).equals("1.1.2.1") && MasterLog.stardate(29).equals("1.2.1.1") && MasterLog.stardate(365).equals("2.1.1.1")
   && MasterLog.stardate(0).equals("Prior to 1.1.1.1") && MasterLog.stardate(-1).equals("Prior to 1.1.1.1"));
 Setup.chk("S: the fleet's day 1 is today (its first look on 5.17), written down once", MasterLog.today(v) == 1 && new File(v.root, "stardate.txt").isFile() && MasterLog.start(v) == v.beaconsSeen());
 HistoryLog.entry("CREW", "Ash signed on");
 HistoryLog.entry("LOADED", "(refresh)", Arrays.asList("a ship line"));
 Rest.rest(v);
 Setup.chk("S: a day of rest: day 2, noted with why", MasterLog.today(v) == 2 && "a day of rest in your quarters".equals(MasterLog.lastDayWhy(v)));
 HistoryLog.entry("EXPEDITION", "Ash sent to the Nebula");
 Map<Integer, List<MasterLog.Entry>> days = MasterLog.byDay(v);
 boolean ash1 = false, ash2 = false;
 for (MasterLog.Entry e : days.get(1)) if (e.text.contains("Ash signed on")) ash1 = true;
 for (MasterLog.Entry e : days.get(2)) if (e.text.contains("Ash sent to the Nebula")) ash2 = true;
 Setup.chk("S: every station log entry is copied, on its day, with the real time", ash1 && ash2 && days.get(1).get(0).real.matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d"));
 // Prior: no day, 0 or -1 are never in the Captain's Log
 FileOutputStream o = new FileOutputStream(new File(v.logsDir(), "master.log"), true);
 o.write("E\t2026-01-01 00:00:00\t0\tstation\tCREW  zero day\nE\t2026-01-01 00:00:00\t-1\tstation\tCREW  minus day\nE\t2026-01-01 00:00:00\t\tstation\tCREW  no day\nE\tbroken\n".getBytes("UTF-8")); o.close();
 boolean prior = false; for (List<MasterLog.Entry> l : MasterLog.byDay(v).values()) for (MasterLog.Entry e : l) if (e.text.contains(" day")) prior = true;
 Setup.chk("S: an entry with no day, 0 or -1 is Prior: left out", !prior);
 // the Cargo Bay's day: once, then not again until something else moves the clock
 int c0 = v.beaconsSeen();
 boolean first = MasterLog.businessDay(v), second = MasterLog.businessDay(v);
 Setup.chk("C: business in the Cargo Bay passes a day; a second Save right after passes none", first && !second && v.beaconsSeen() == c0 + 1);
 Rest.rest(v);
 Setup.chk("C: after something else moves the clock, it counts again", MasterLog.businessDay(v) && v.beaconsSeen() == c0 + 3);
 // 5.18: the Captain's Log as a story, on a fresh fleet of its own
 storyDays(game, new File(work, "story"));
 voyageDays(game, new File(work, "voyage"));
 beaconDays(game, new File(work, "beacon"));
 System.setProperty("game", game.getPath()); fixes520(v);
 // 5.53 (heromedel): the fleet's listing is the debug log's; old LOADED entries stay in the file, out of view, the stardates still in line
 HistoryLog.loaded("refresh");
 Setup.chk("L: a refresh's listing no longer goes in the station log", count(new String(SafeFiles.read(v.historyLog()), "UTF-8"), "LOADED") == 1);
 Class<?> rl = Class.forName("homeplanet.ui.RecordsLog");
 java.lang.reflect.Method st = rl.getDeclaredMethod("station", String.class, String.class, int[].class); st.setAccessible(true);
 Object view = st.invoke(null, "2026-01-01 00:00  CREW  Ash signed on\n2026-01-01 00:01  LOADED  (startup)\n  a ship line\n2026-01-01 00:02  CREW  Bree signed on\n", "", new int[] {1, 2, 3});
 java.lang.reflect.Field items = rl.getDeclaredField("items"); items.setAccessible(true);
 List<String> seen = new ArrayList<String>();
 for (Object it : (List<?>) items.get(view)) {
  Class<?> ic = it.getClass();
  java.lang.reflect.Field hf = ic.getDeclaredField("heading"), tf = ic.getDeclaredField("title"), gf = ic.getDeclaredField("tag"), df = ic.getDeclaredField("details");
  hf.setAccessible(true); tf.setAccessible(true); gf.setAccessible(true); df.setAccessible(true);
  seen.add((Boolean) hf.get(it) ? (String) tf.get(it) : gf.get(it) + "/" + ((List<?>) df.get(it)).size());
 }
 Setup.chk("L: an old LOADED entry is out of view, its lines with it; the next entry keeps its own stardate " + seen,
   seen.equals(Arrays.asList("Stardate " + MasterLog.stardate(1), "CREW/0", "Stardate " + MasterLog.stardate(3), "CREW/0")));
 Setup.done();
}
 static int count(String s, String w) { int n = 0, i = 0; while ((i = s.indexOf(w, i)) >= 0) { n++; i += w.length(); } return n; }
 static String page(Vault v, boolean details) throws Exception {
  java.lang.reflect.Method page = Class.forName("homeplanet.ui.CaptainsLogDialog").getDeclaredMethod("page", Vault.class, boolean.class); page.setAccessible(true);
  return (String) page.invoke(null, v, details);
 }
 /** Days aboard: On board, what happened, Then we jumped; back to the station; Set out (heromedel, 5.18). */
 static void voyageDays(File game, File dir) throws Exception {
  File saves = new File(dir, "saves"); saves.mkdirs();
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  String jump = "Jumped, hull 30/30, scrap 40 (+20), fuel 10 (-1), missiles 8, drone parts 2";
  Setup.voyage(v, "Kestrel", "1 ship defeated (1 in all)");
  Setup.voyage(v, "Kestrel", jump);
  v.countBeacon("a jump");
  Setup.voyage(v, "Kestrel", "Bought at a store: Burst Laser II");
  Setup.voyage(v, "Kestrel", jump);
  Setup.voyage(v, "Kestrel", "Arrived at a store");
  v.countBeacon("a jump");
  HistoryLog.entry("SELL", "1 item for 6 scrap", Arrays.asList("2 Missiles for 6 scrap  (Spacedock Storage)"));
  MasterLog.businessDay(v);
  Setup.voyage(v, "Kestrel", "Sector 3 reached (sectors visited: 3)");
  Setup.voyage(v, "Kestrel", jump);
  v.countBeacon("a jump");
  String p = page(v, false);
  int a1 = p.indexOf("On board the Kestrel:"), a2 = p.indexOf("The Kestrel defeated a ship."), a3 = p.indexOf("Then we jumped to a new beacon.");
  Setup.chk("V: a day aboard: On board, what happened, Then we jumped (" + a1 + " " + a2 + " " + a3 + ")", a1 >= 0 && a1 < a2 && a2 < a3);
  int b1 = p.indexOf("Bought a Burst Laser II at a station."), b2 = p.indexOf("Then we jumped to a station.");
  Setup.chk("V: gear bought at a store, and a jump that came to a station", b1 > a3 && b2 > b1);
  int c1 = p.indexOf("I returned to The Home Planet Station."), c2 = p.indexOf("Then I sold two missiles.");
  Setup.chk("V: back at the station after time aboard: I returned, Then I sold", c1 > b2 && c2 > c1);
  int d1 = p.indexOf("Set out on the Kestrel:"), d2 = p.indexOf("Then we jumped to sector 3.");
  Setup.chk("V: aboard again after the station: Set out, Then we jumped to sector 3", d1 > c2 && d2 > d1 && !p.contains("pressed on"));
 }
 /** What a beacon held (5.19): nebulas, storms, hazards, a ship met, the next day's news going to the jump before it. */
 static void beaconDays(File game, File dir) throws Exception {
  java.lang.reflect.Method ch = VoyageLog.class.getDeclaredMethod("changes", Properties.class, Properties.class, int.class, List.class); ch.setAccessible(true);
  Properties a = look("1", 2, 1, "", ""); List out = new ArrayList(); // the events, read as their human lines (5.63)
  ch.invoke(null, a, look("2", 3, 1, "", ""), 1, out); words(out);
  Setup.chk("B: FTL's nebula count rose with the jump: a nebula " + out, out.contains("Beacon: a nebula"));
  out.clear(); ch.invoke(null, a, look("2", 3, 2, "", ""), 1, out); words(out);
  Setup.chk("B: a jump into danger the save names none of: an ion storm, not a nebula " + out, out.contains("Beacon: an ion storm") && !out.toString().contains("nebula"));
  out.clear(); ch.invoke(null, a, look("2", 2, 2, "sun|pds", "a Rock pirate"), 1, out); words(out);
  Setup.chk("B: the save's own hazards, and the ship met " + out, out.contains("Beacon: a star, an Anti-Ship Battery") && out.contains("Ship met: a Rock pirate"));
  Properties old = look("1", 0, 0, "", ""); old.remove("nebulaJumps"); old.remove("dangerJumps"); old.remove("met");
  out.clear(); ch.invoke(null, old, look("2", 9, 9, "", ""), 1, out); words(out);
  Setup.chk("B: a last look from before 5.19 (no counts kept) reads no nebula or storm " + out, !out.toString().contains("Beacon"));
  out.clear(); ch.invoke(null, old, look("1", 0, 0, "", "a Mantis ship"), 1, out); words(out);
  Setup.chk("B: and no ship 'met' without a jump on that first look " + out, out.isEmpty());
  out.clear(); ch.invoke(null, a, look("1", 2, 1, "", "a Mantis ship"), 1, out); words(out);
  Setup.chk("B: a ship turning up after the jump: met, on its own " + out, out.size() == 1 && out.contains("Ship met: a Mantis ship"));
  Setup.chk("B: ships in words", VoyageLog.shipWords("ROCK_PIRATE", "SHIPS_ROCK_PIRATE", "rock").equals("a Rock pirate") && VoyageLog.shipWords("PIRATE", "SHIPS_PIRATE", "mantis").equals("a Mantis pirate")
    && VoyageLog.shipWords("REBEL", "SHIPS_REBEL", "human").equals("a rebel ship") && VoyageLog.shipWords("REBEL_AUTO", "SHIPS_AUTO", "").equals("an automated ship")
    && VoyageLog.shipWords("ENGI_SHIP", "SHIPS_CIRCLE", "engi").equals("an Engi ship") && VoyageLog.shipWords(null, null, "").equals("a ship") && VoyageLog.shipWords("MOD_EVENT", null, "energy").equals("a Zoltan ship"));
  File saves = new File(dir, "saves"); saves.mkdirs();
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  String jump = "Jumped, hull 30/30, scrap 40, fuel 10 (-1), missiles 8, drone parts 2";
  Setup.voyage(v, "Kestrel", jump); Setup.voyage(v, "Kestrel", "Beacon: a nebula"); v.countBeacon("a jump");
  Setup.voyage(v, "Kestrel", "1 ship defeated (1 in all)"); Setup.voyage(v, "Kestrel", jump); Setup.voyage(v, "Kestrel", "Beacon: an ion storm"); v.countBeacon("a jump");
  Setup.voyage(v, "Kestrel", "Ship met: a Rock pirate"); // learned a day after the jump
  Setup.voyage(v, "Kestrel", "Sector 3 reached (sectors visited: 3)"); Setup.voyage(v, "Kestrel", jump); Setup.voyage(v, "Kestrel", "Beacon: a red giant"); v.countBeacon("a jump");
  Setup.voyage(v, "Kestrel", jump); Setup.voyage(v, "Kestrel", "Arrived at a store"); Setup.voyage(v, "Kestrel", "Beacon: an asteroid field"); Setup.voyage(v, "Kestrel", "Ship met: a Mantis ship"); v.countBeacon("a jump");
  String p = page(v, false);
  Setup.chk("B: On board the Kestrel: with a colon", p.contains("On board the Kestrel:") && !p.contains("On board the Kestrel."));
  Setup.chk("B: Then we jumped into a nebula", p.contains("Then we jumped into a nebula."));
  Setup.chk("B: the next day's news goes to the jump before it: into an ion storm and met a Rock pirate", p.contains("Then we jumped into an ion storm and met a Rock pirate."));
  Setup.chk("B: a sector and a hazard: to sector 3, to a beacon near a star (5.19's \"a red giant\" read as FTL names it)", p.contains("Then we jumped to sector 3, to a beacon near a star."));
  Setup.chk("B: a station: in an asteroid field, and met a Mantis ship", p.contains("Then we jumped to a station in an asteroid field and met a Mantis ship."));
  Setup.chk("B: the beacon lines themselves never shown", !p.contains("Beacon:") && !p.contains("Ship met:"));
 }
 /** 5.20: work at a store told on the stop's own day; a sale from a ship's cargo reads cleanly. */
 static void fixes520(Vault v) throws Exception {
  Ship b = v.docked().get(0);
  net.blerf.ftl.parser.SavedGameParser.SavedGameState gs = v.readCopy(b).save;
  java.lang.reflect.Method nw = Vault.class.getDeclaredMethod("noteWork", Ship.class, net.blerf.ftl.parser.SavedGameParser.SavedGameState.class); nw.setAccessible(true);
  nw.invoke(v, b, gs); // the stop, first seen
  gs.setStateVar("store_purchase", (gs.hasStateVar("store_purchase") ? gs.getStateVar("store_purchase") : 0) + 1);
  int before = MasterLog.today(v);
  nw.invoke(v, b, gs); // work done there: a day passes
  int workDay = 0;
  for (Map.Entry<Integer, List<MasterLog.Entry>> e : MasterLog.byDay(v).entrySet()) for (MasterLog.Entry x : e.getValue()) if (x.text.startsWith("Time spent on work")) workDay = e.getKey();
  Setup.chk("F: work at a store is told on the stop's own day, then the day passes (" + workDay + ", " + before + " -> " + MasterLog.today(v) + ")", workDay == before && MasterLog.today(v) == before + 1);
  File saves = new File(v.root.getParentFile().getParentFile(), "sale/saves"); saves.mkdirs(); // the station log writes to the fleet opened last: a fresh one of its own
  v = Setup.open(new File(System.getProperty("game")), saves); v.storage(); v.takeStock();
  HistoryLog.entry("SELL", "2 items for 36 scrap", Arrays.asList("Burst Laser II for 30 scrap  (Kestrel (cargo))", "Ion Blast for 6 scrap  (The (Odd) Ship)"));
  MasterLog.businessDay(v);
  String p = page(v, false);
  HistoryLog.entry("SYSTEMS", "Test Kestrel", Arrays.asList("Bought Cloaking (level 1) into the Cargo Hold"));
  HistoryLog.entry("BUY", "1 purchase", Arrays.asList("Cloaking system (150 scrap) from the store at Test Kestrel's beacon -> The Cargo Bay"));
  MasterLog.businessDay(v);
  String q = page(v, false);
  Setup.chk("F: a system bought into the Cargo Hold: Bought a Cloaking system, and no Dry Dock work told for it (5.30)", q.contains("Bought a Cloaking system.") && !q.contains("Had the Dry Dock work on the Test Kestrel"));
  HistoryLog.entry("CREW", "Joel assigned to the Test Kestrel.");
  HistoryLog.entry("CREW", "Ferry assigned to the Test Kestrel.");
  HistoryLog.entry("CREW", "Kirkner assigned to the Cargo Hold.");
  HistoryLog.entry("CREW", "Ash signed on"); // another CREW entry: not a move, not told as one
  MasterLog.businessDay(v);
  String r = page(v, false);
  Setup.chk("F: crew moved in the Cargo Bay: one line a destination (5.40)", r.contains("Assigned Joel and Ferry to the Test Kestrel.") && r.contains("Moved Kirkner to the Cargo Hold.") && !r.contains("Ash"));
  Setup.chk("F: a sale from a ship's cargo, or a ship with brackets in her name, reads cleanly", p.contains("Sold a Burst Laser II and an Ion Blast.") && !p.contains("(cargo)"));
 }
 static Properties look(String beacon, int nebula, int danger, String hazards, String met) {
  Properties p = new Properties();
  p.setProperty("sector", "0"); p.setProperty("beacon", beacon); p.setProperty("beacons", beacon);
  p.setProperty("nebulaJumps", "" + nebula); p.setProperty("dangerJumps", "" + danger); p.setProperty("hazards", hazards); p.setProperty("met", met);
  return p;
 }
 static void storyDays(File game, File dir) throws Exception {
  File saves = new File(dir, "saves"); saves.mkdirs();
  HomePlanet.reputationOn = true;
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  // day 1: a crew back, a letter, then rest
  HistoryLog.entry("EXPEDITION", "Bob, Joe, Fred back from Nebula (Salvage): 20 scrap; killed: Fred");
  HistoryLog.entry("TRANSMISSION", "Expedition Command: Back from Nebula");
  HistoryLog.entry("TRANSMISSION", "Home Planet Shipyard: Commission order: Kestrel Cruiser, Type B");
  Rest.rest(v);
  // day 2: four missiles sold over two Saves, a ship boarded twice, then business moved the day
  HistoryLog.entry("BOARD", "Kestrel  ships/a.sav -> continue.sav");
  HistoryLog.entry("BOARD", "Hinata  ships/b.sav -> continue.sav");
  HistoryLog.entry("SELL", "1 item for 9 scrap", Arrays.asList("3 Missiles for 9 scrap  (Spacedock Storage)"));
  HistoryLog.entry("SELL", "1 item for 6 scrap", Arrays.asList("2 Missiles for 6 scrap  (Spacedock Storage)"));
  MasterLog.businessDay(v);
  // days 3 to 5 quiet, then day 6 a second rest in a row
  v.countBeacon(); v.countBeacon(); v.countBeacon();
  Rest.rest(v); Rest.rest(v);
  String p = page(v, false), d = page(v, true);
  Setup.chk("L: day one heads 'Stardate Today', later days 'Stardate 1.1.1.2'", p.contains("-- Captain's Log --") && p.contains("Stardate Today") && p.contains("Stardate 1.1.1.2") && !p.contains("StarDate TD"));
  Setup.chk("L: things that happened, as the captain tells them", p.contains("Bob and Joe came back from the expedition; Fred did not.") && p.contains("Got a letter from the Home Planet Shipyard: I can now commission a new Kestrel Cruiser, Type B."));
  Setup.chk("L: the action that moved the day on comes last, with Then", p.indexOf("Got a letter") < p.indexOf("Then I rested in my quarters.") && p.contains("Then I sold five missiles."));
  Setup.chk("L: repeats merged: five missiles over two Saves are one line; two boardings are one", count(p.toLowerCase(), "sold") == 1 && p.contains("Took command of the Hinata.") && !p.contains("Kestrel."));
  Setup.chk("L: a rest is one line on the day rested, nothing of it on the next", count(p, "rested in my quarters") + count(p, "Rested in my quarters") == 3 && p.indexOf("Then I rested") < p.indexOf("Stardate 1.1.1.2"));
  Setup.chk("L: quiet days fold into one 'Nothing to report'", p.contains("Stardates 1.1.1.3 \u2013 1.1.1.5") && p.contains("Nothing to report."));
  Setup.chk("L: the second rest in a row says so, and a lone line has no Then", p.contains("Rested in my quarters for the second day in a row.") && !p.contains("Then I rested in my quarters for"));
  Setup.chk("L: details only when asked: the costs and the reputation", !p.contains("reputation") && !p.contains("scrap") && d.contains("\u22121 reputation") && d.contains("Missiles, 9 scrap"));
  Setup.chk("L: never the letter that tells an expedition again, reputation as its own line, housekeeping, why a day passed, or beacons",
    !p.contains("Expedition Command") && !p.contains("Reputation") && !p.contains(MasterLog.CARGO_BAY) && !p.contains("day of rest") && !p.toLowerCase().contains("beacon"));
  String raw = new String(SafeFiles.read(new File(v.logsDir(), "master.log")), "UTF-8");
  Setup.chk("L: the master list keeps every raw line", count(raw, "SELL") == 2 && count(raw, "BOARD") == 2 && raw.contains("Expedition Command: Back from Nebula"));
 }
 @SuppressWarnings({"unchecked", "rawtypes"})
 static void words(List out) { for (int i = 0; i < out.size(); i++) if (out.get(i) instanceof homeplanet.core.Event) out.set(i, ((homeplanet.core.Event) out.get(i)).human()); }
}
