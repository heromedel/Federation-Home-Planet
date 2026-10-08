import java.io.*; import java.nio.charset.StandardCharsets; import java.util.*; import net.blerf.ftl.parser.SavedGameParser; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/**
 * Everything a fleet owns, counted from its files (Overhaul 6.0 step 25a; Buggy Boy's bench ledger, brought into the
 * harness for KillT): every ship folder (shipyard, Junkyard, memorials and records) by name, class and place; every crew
 * member by name, race, sex, looks and record, wherever they are (the ships' saves and continue.sav, the Cargo Hold, away
 * on an expedition, held captive); every weapon, drone, augment, cargo item, stored system and parcel; the supplies;
 * each counted where it is, so a move half made shows as well as a thing lost or doubled.
 * And what is wrong whatever was done: a ship id twice, a ship's record without her save, a crew file twice, a note
 * left in station-action-protection/, half an event log entry. Two ledgers of the same fleet compare by {@link #key}.
 */
public class FleetLedger {
 /** "kind|what" -> how many, all places together. */
 public final TreeMap<String, Integer> counts = new TreeMap<String, Integer>();
 public final List<String> problems = new ArrayList<String>();
 public int crewFiles;

 public static FleetLedger of(Vault v) throws IOException {
  FleetLedger l = new FleetLedger();
  File root = v.root;
  Set<String> ids = new HashSet<String>();
  boolean boarded = false;
  for (String dir : new String[] {"shipyard", "junkyard", "memorials_and_records/ships"}) {
   for (File d : ShipStore.folders(new File(root, dir))) {
    ShipStore.Record r = ShipStore.read(d);
    if (r == null) { l.problems.add(dir + "/" + d.getName() + ": no record"); continue; }
    if (!ids.add(r.id)) l.problems.add("ship id " + r.id + " in two folders (" + d.getName() + ")");
    File sav = new File(d, d.getName() + ".sav");
    l.at = "fleet";
    if (dir.startsWith("memorials")) { l.add("gone", r.name + " " + fate(d)); continue; }
    String place = dir.equals("junkyard") ? "junked" : r.state;
    if ("boarded".equals(place)) {
     boarded = true;
     File cont = v.continueFile();
     if (!cont.isFile()) { l.problems.add(r.name + " is boarded but continue.sav is missing"); continue; }
     if (sav.isFile()) l.problems.add(r.name + " is boarded but her folder still has her save");
     l.save(cont, r.name + " boarded", false);
     continue;
    }
    if (!sav.isFile()) { l.problems.add(dir + "/" + d.getName() + ": " + r.name + " (" + place + ") has no save"); continue; }
    l.save(sav, r.name + " " + place, false);
   }
  }
  if (!boarded && v.continueFile().isFile()) l.problems.add("continue.sav is there, but no ship is boarded");
  File hf = new File(v.cargoHoldDir(), Vault.HOLD_FILE); // cargohold.xml (5.84), else the pretend ship's save from before
  if (!HoldXml.isHold(hf)) hf = new File(v.cargoHoldDir(), "cargohold.sav");
  if (hf.isFile()) l.state(HoldXml.read(hf), "hold", true); else l.problems.add("the Cargo Hold has no file");
  l.at = "hold";
  for (String line : lines(v.systemsFile())) { String[] w = line.trim().split("\\s+"); if (w.length >= 2 && !line.startsWith("#")) l.add("stored", w[0] + " L" + w[1]); }
  Properties over = props(new File(v.cargoHoldDir(), "overflow.txt"));
  for (String k : over.stringPropertyNames()) if (k.matches("parcel\\.\\d+")) { String[] w = over.getProperty(k).split("\\|", 3); l.add("augment", w.length == 3 ? w[1] : "?"); }
  for (String line : lines(new File(v.cargoHoldDir(), "parts.txt"))) if (!line.trim().isEmpty() && !line.startsWith("#")) l.add("part", line.trim());
  l.at = "away";
  for (Assignments.Away a : Assignments.away(v)) for (CrewState c : a.crew) l.add("crew", crewKey(c));
  l.at = "captive";
  Properties cap = Expeditions.captivesAsIs(v);
  for (int i = 0; cap.getProperty(i + ".name") != null; i++) {
   String st = cap.getProperty(i + ".state", "");
   if (st.equals("ransomed") || st.equals("lost")) continue;
   l.add("crew", cap.getProperty(i + ".name") + "|" + cap.getProperty(i + ".race", "?") + "|" + ("true".equals(cap.getProperty(i + ".male")) ? "m" : "f"));
  }
  // the crew register's files: one per crew member, never two for one id
  Map<String, String> fileOf = new HashMap<String, String>();
  List<File> dirs = new ArrayList<File>();
  for (File d : v.shipFolders()) dirs.add(new File(d, CrewRegister.CREW_DIR));
  for (File d : new File[] {v.cargoHoldDir(), v.expeditionsDir(), v.captivesDir(), v.memorialDir().getParentFile()}) dirs.add(new File(d, CrewRegister.CREW_DIR));
  for (File d : dirs) {
   File[] fs = d.listFiles();
   if (fs != null) for (File f : fs) {
    if (!f.isFile() || !f.getName().endsWith(".xml")) continue;
    String n = f.getName().substring(0, f.getName().length() - 4);
    String id = n.substring(n.lastIndexOf('.') + 1);
    l.crewFiles++;
    String was = fileOf.put(id, f.getParentFile().getParentFile().getName());
    if (was != null) l.problems.add("crew id " + id + " has two files (" + was + ", " + f.getParentFile().getParentFile().getName() + ")");
   }
  }
  File[] notes = Journal.dir(v).listFiles();
  if (notes != null) for (File n : notes) if (!n.getName().equals(Journal.WHY)) l.problems.add("a note left in station-action-protection/: " + n.getName());
  for (EventLog.Entry e : EventLog.read(v)) if (e.kind.equals("JOURNAL") && "stuck".equals(e.get("what"))) l.problems.add("a note the opening couldn't finish: " + e.human);
  l.checkEvents(EventLog.file(v));
  return l;
 }

 /** The fleet's events.log: every entry two whole lines, the file ending with a line end. */
 void checkEvents(File f) throws IOException {
  if (!f.isFile()) return;
  String text = new String(SafeFiles.read(f), StandardCharsets.UTF_8);
  if (!text.isEmpty() && !text.endsWith("\n")) problems.add("events.log ends in the middle of a line");
  String[] ls = text.split("\r?\n");
  for (int i = 0; i < ls.length; i++) {
   boolean machine = ls[i].length() > 22 && ls[i].indexOf(" | ") == 19;
   if (!machine) continue;
   if (i + 1 >= ls.length || (ls[i + 1].length() > 22 && ls[i + 1].indexOf(" | ") == 19)) problems.add("events.log entry without its human line: " + ls[i].substring(0, Math.min(80, ls[i].length())));
  }
 }

 void save(File f, String place, boolean hold) {
  try { state(new SavedGameParser().readSavedGame(f), place, hold); }
  catch (Exception e) { problems.add("unreadable save " + f.getName() + " (" + place + "): " + e); }
 }
 void state(SavedGameState g, String place, boolean hold) {
  ShipState s = g.getPlayerShip();
  at = "fleet";
  if (!hold) add("ship", s.getShipBlueprintId() + " \"" + g.getPlayerShipName() + "\" " + place.substring(place.lastIndexOf(' ') + 1));
  at = place;
  for (CrewState c : SaveHelper.getOwnCrew(s)) add("crew", crewKey(c));
  for (WeaponState w : s.getWeaponList()) add("weapon", w.getWeaponId());
  for (DroneState d : s.getDroneList()) add("drone", d.getDroneId());
  for (String a : s.getAugmentIdList()) add("augment", a);
  for (String c : g.getCargoIdList()) add("cargo", c);
  amount("scrap", s.getScrapAmt()); amount("fuel", s.getFuelAmt()); amount("missiles", s.getMissilesAmt()); amount("droneparts", s.getDronePartsAmt());
 }
 /** Where the things being counted are: a ship by her name and state, the hold, away, captive (a move from one to another is a change, half a move is caught). */
 String at = "fleet";
 void amount(String k, int n) { if (n != 0) { String key = k + " @" + at; counts.put(key, (counts.containsKey(key) ? counts.get(key) : 0) + n); } }
 void add(String kind, String what) { String k = kind + "|" + what + " @" + at; counts.put(k, (counts.containsKey(k) ? counts.get(k) : 0) + 1); }

 /** Who someone is, as the files can tell: name, race, sex, looks, service record. */
 static String crewKey(CrewState c) {
  StringBuilder t = new StringBuilder();
  for (Integer i : c.getSpriteTintIndeces()) t.append(t.length() == 0 ? "" : ",").append(i);
  return c.getName() + "|" + (c.getRace() == null ? "?" : c.getRace().getId()) + "|" + (c.isMale() ? "m" : "f") + "|" + t + "|" + c.getRepairs() + "/" + c.getCombatKills() + "/" + c.getPilotedEvasions() + "/" + c.getJumpsSurvived();
 }
 static String fate(File d) { String[] f = ShipStore.fate(d); return f == null ? "?" : f[0]; } // her record's fate (5.98; fate.txt before)
 static List<String> lines(File f) {
  try { return f.isFile() ? Arrays.asList(new String(SafeFiles.read(f), StandardCharsets.UTF_8).split("\r?\n")) : new ArrayList<String>(); }
  catch (IOException e) { return Collections.singletonList("#unreadable " + e); }
 }
 static Properties props(File f) {
  Properties p = new Properties();
  if (f.isFile()) try (InputStream in = new FileInputStream(f)) { p.load(new InputStreamReader(in, StandardCharsets.UTF_8)); } catch (IOException e) { p.setProperty("#unreadable", e.toString()); }
  return p;
 }

 /** What two ledgers compare on: the counts (ships, crew, items, supplies) and how many crew files. */
 public String key() { return counts + " crewfiles=" + crewFiles; }
 /** The things that differ from another ledger, for a failure's message. */
 public String diff(FleetLedger o) {
  StringBuilder sb = new StringBuilder();
  Set<String> ks = new TreeSet<String>(counts.keySet()); ks.addAll(o.counts.keySet());
  for (String k : ks) {
   Integer a = counts.get(k), b = o.counts.get(k);
   if (a == null ? b != null : !a.equals(b)) sb.append(sb.length() == 0 ? "" : "; ").append(k).append(" ").append(b).append(" -> ").append(a);
  }
  if (crewFiles != o.crewFiles) sb.append(sb.length() == 0 ? "" : "; ").append("crew files ").append(o.crewFiles).append(" -> ").append(crewFiles);
  return sb.length() == 0 ? "nothing" : sb.toString();
 }
}
