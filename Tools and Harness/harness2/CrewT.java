import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The crew register and the Crew Log (5.41): ids that hold through namesakes, renames and moves; what became of those no longer found. args: gamedir, world saves, work */
public class CrewT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive(); HomePlanet.expeditionType = 2;
 Vault v = Setup.open(game, saves); v.storage();
 HistoryLog.entry("CREW", "Old Hand assigned to the Cargo Hold."); // before the register: read in once
 MasterLog.entry(v, "voyage: Test Kestrel", "Crew lost: Old Hand (Human)"); // a namesake lost long ago: never pinned on the living one
 List<CrewState> hold = ExpT.hold(v, "human", "human", "engi", "rock", "human");
 // two namesakes, told apart by their colouring and service record
 Vault.Copy c = v.readCopy(v.storage());
 List<CrewState> h = SaveHelper.getOwnCrew(c.save.getPlayerShip());
 h.get(0).setName("Twin"); h.get(1).setName("Twin"); h.get(1).setJumpsSurvived(h.get(0).getJumpsSurvived() + 5);
 h.get(4).setName("Old Hand");
 v.begin().put(v.storage(), c.save, c.hash).commit();
 new File(v.root, "crew.txt").delete(); // as a fleet updating to 5.41: its logs already written, its register new
 v.takeStock();
 List<CrewRegister.Member> m = CrewRegister.members(v);
 int fleet = 0;
 for (Ship s : v.all()) { if (s.save() != null && s.save().getPlayerShip() != null) fleet += SaveHelper.getOwnCrew(s.save().getPlayerShip()).size(); }
 Setup.chk("R: a new register: everyone in the fleet has an id, all present", count(m, CrewRegister.Status.PRESENT) == fleet && new File(v.root, "crew.txt").isFile());
 CrewRegister.Member oldHand = find(m, "Old Hand", CrewRegister.Status.PRESENT);
 Setup.chk("R: the logs read once: the living Old Hand keeps the move, the one lost long ago has an entry of their own",
   oldHand != null && said(oldHand, "Moved to the Cargo Hold.") && !said(oldHand, "Lost aboard") && find(m, "Old Hand", CrewRegister.Status.KILLED) != null);
 List<CrewRegister.Member> twins = all(m, "Twin");
 Setup.chk("R: two namesakes, two ids", twins.size() == 2 && twins.get(0).id != twins.get(1).id);

 // one Twin sent away: the other stays, and each keeps their own id
 List<CrewState> now = Expeditions.holdCrew(v);
 CrewState veteran = null; for (CrewState x : now) if (x.getName().equals("Twin") && (veteran == null || x.getJumpsSurvived() > veteran.getJumpsSurvived())) veteran = x;
 Assignments.send(v, Assignments.board(v).get(0).slot, Collections.singletonList(veteran), new Random(3));
 v.takeStock();
 m = CrewRegister.members(v);
 CrewRegister.Member away = null, stayed = null;
 for (CrewRegister.Member x : all(m, "Twin")) { if (x.where.startsWith("on an expedition")) away = x; else if (x.where.equals("in the Cargo Hold")) stayed = x; }
 Setup.chk("A: the veteran Twin is away, the other in the Cargo Hold, each with their own history", away != null && stayed != null && away.id != stayed.id
   && said(away, "Sent on an expedition") && !said(stayed, "Sent on an expedition"));

 // a rename: the same id
 c = v.readCopy(v.storage());
 CrewState engi = null; for (CrewState x : SaveHelper.getOwnCrew(c.save.getPlayerShip())) if (x.getRace() != null && x.getRace().getId().equals("engi")) engi = x;
 int engiId = idIn(m, engi.getName(), "in the Cargo Hold"); // another Engi of that name serves aboard a ship of the test world
 String was = engi.getName();
 engi.setName("Sparky");
 v.begin().put(v.storage(), c.save, c.hash).commit();
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("N: renamed in the Cargo Hold: the same id, the old name in their history", idOf(m, "Sparky") == engiId && said(byId(m, engiId), "Now known as Sparky (was " + was + ")."));

 // taken captive, then never ransomed
 c = v.readCopy(v.storage());
 CrewState rock = null; for (CrewState x : SaveHelper.getOwnCrew(c.save.getPlayerShip())) if (x.getRace() != null && x.getRace().getId().equals("rock")) rock = x;
 int rockId = idIn(m, rock.getName(), "in the Cargo Hold");
 Properties cap = new Properties();
 cap.setProperty("0.name", rock.getName()); cap.setProperty("0.race", "rock"); cap.setProperty("0.male", Boolean.toString(rock.isMale()));
 cap.setProperty("0.captors", "pirates"); cap.setProperty("0.state", "held");
 for (Map.Entry<String, String> e : homeplanet.comm.Line.crewFields(rock).entrySet()) cap.setProperty("0.crew." + e.getKey(), e.getValue());
 c.save.getPlayerShip().getCrewList().remove(rock);
 v.begin().put(v.storage(), c.save, c.hash).commit();
 writeProps(new File(v.root, "captives.txt"), cap);
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("C: taken captive: the same id, missing, held by pirates", byId(m, rockId).status == CrewRegister.Status.CAPTIVE && byId(m, rockId).where.contains("pirates"));
 cap.setProperty("0.state", "gone");
 writeProps(new File(v.root, "captives.txt"), cap);
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("C: never ransomed: killed, presumed dead", byId(m, rockId).status == CrewRegister.Status.KILLED && said(byId(m, rockId), "presumed dead"));

 // lost aboard the boarded ship in FTL
 Ship b = v.boarded();
 Setup.chk("F: the world has a ship aboard", b != null);
 c = v.readCopy(b);
 CrewState gone = SaveHelper.getOwnCrew(c.save.getPlayerShip()).get(0);
 int goneId = idOf(m, gone.getName());
 c.save.getPlayerShip().getCrewList().remove(gone);
 v.begin().put(b, c.save, c.hash).commit();
 MasterLog.entry(v, "voyage: " + b.name, "Crew lost: " + gone.getName() + " (" + homeplanet.model.Crew.raceTitle(gone) + ")");
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("F: lost aboard her in FTL: killed", byId(m, goneId).status == CrewRegister.Status.KILLED && said(byId(m, goneId), "Lost aboard"));

 // found nowhere, with nothing to say why: missing; then found again
 Ship d = v.docked().get(0);
 c = v.readCopy(d);
 CrewState stray = SaveHelper.getOwnCrew(c.save.getPlayerShip()).get(0);
 int strayId = idOf(m, stray.getName());
 c.save.getPlayerShip().getCrewList().remove(stray);
 v.begin().put(d, c.save, c.hash).commit();
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("M: found nowhere, nothing logged: missing, whereabouts unknown", byId(m, strayId).status == CrewRegister.Status.MISSING);
 c = v.readCopy(d);
 c.save.getPlayerShip().getCrewList().add(stray);
 v.begin().put(d, c.save, c.hash).commit();
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("M: back where they were: the same id, found again", byId(m, strayId).status == CrewRegister.Status.PRESENT && said(byId(m, strayId), "Found again"));

 // retired in the Cargo Bay
 c = v.readCopy(v.storage());
 CrewState last = null; for (CrewState x : SaveHelper.getOwnCrew(c.save.getPlayerShip())) if (x.getName().equals("Sparky")) last = x;
 c.save.getPlayerShip().getCrewList().remove(last);
 v.begin().put(v.storage(), c.save, c.hash).commit();
 HistoryLog.entry("RETIRE", "1 crew member", Arrays.asList("Sparky (Engi)  (Spacedock Storage)"));
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("D: retired: retired, the same id", byId(m, engiId).status == CrewRegister.Status.RETIRED && said(byId(m, engiId), "Retired from the station's service."));

 // the crew popup's Crew Log...: the one in the popup, never their namesake
 CrewState home = null; for (CrewState x : Expeditions.holdCrew(v)) if (x.getName().equals("Twin")) home = x;
 m = CrewRegister.members(v);
 int homeId = -1; for (CrewRegister.Member x : all(m, "Twin")) if (x.where.equals("in the Cargo Hold")) homeId = x.id;
 Setup.chk("P: the popup's Twin is the Twin in the Cargo Hold, not the one away", home != null && CrewRegister.identify(v, home) == homeId);
 CrewRegister.Member old = find(m, "Old Hand", CrewRegister.Status.KILLED);
 Setup.chk("P: known only from the old logs: a look of their race, made once and kept", old != null && old.crew() != null && old.crew().getRace().getId().equals("human")
   && Line_name(old).equals("Old Hand") && CrewRegister.members(v).get(m.indexOf(old)).crew().getSpriteTintIndeces().equals(old.crew().getSpriteTintIndeces()));
 Setup.chk("P: the ships they served on, in order", byId(m, goneId).served.size() >= 1);

 // traded away over the Long Range (Cloud-C-BugsandFeedback's handoff, 5.47): discharged as traded; a namesake stays
 c = v.readCopy(v.storage());
 for (int k = 0; k < 3; k++) {
  CrewState t = Commission.volunteer("human", new Random(40 + k)); t.setName(k < 2 ? "Tradewell" : "Partner"); t.setJumpsSurvived(k * 7);
  SaveHelper.placeCrew(c.save.getPlayerShip(), t, true); c.save.getPlayerShip().getCrewList().add(t);
 }
 v.begin().put(v.storage(), c.save, c.hash).commit();
 v.takeStock();
 m = CrewRegister.members(v);
 c = v.readCopy(v.storage());
 CrewState sent = null; for (CrewState x : SaveHelper.getOwnCrew(c.save.getPlayerShip())) if (x.getName().equals("Tradewell") && x.getJumpsSurvived() == 7) sent = x;
 int sentId = -1, keptId = -1; for (CrewRegister.Member x : all(m, "Tradewell")) { if (x.crew() != null && x.crew().getJumpsSurvived() == 7) sentId = x.id; else keptId = x.id; }
 c.save.getPlayerShip().getCrewList().remove(sent);
 v.begin().put(v.storage(), c.save, c.hash).commit();
 HistoryLog.entry("LONG RANGE TRADE", "with Commander Vance  (trade t1)", Arrays.asList("gave: Tradewell (Human)", "received (in the Cargo Hold): 20 scrap"));
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("L: traded away alone: transferred to Commander Vance's fleet; the namesake who stayed still present", sentId > 0 && byId(m, sentId).status == CrewRegister.Status.TRANSFERRED
   && said(byId(m, sentId), "Transferred to Commander Vance's fleet.") && byId(m, keptId).status == CrewRegister.Status.PRESENT);
 c = v.readCopy(v.storage());
 CrewState partner = null; for (CrewState x : SaveHelper.getOwnCrew(c.save.getPlayerShip())) if (x.getName().equals("Partner")) partner = x;
 int partnerId = idIn(m, "Partner", "in the Cargo Hold");
 c.save.getPlayerShip().getCrewList().remove(partner);
 CrewState arrived = Commission.volunteer("engi", new Random(77)); arrived.setName("Newcomer");
 SaveHelper.placeCrew(c.save.getPlayerShip(), arrived, true); c.save.getPlayerShip().getCrewList().add(arrived);
 v.begin().put(v.storage(), c.save, c.hash).commit();
 HistoryLog.entry("LONG RANGE TRADE", "with Commander Vance  (trade t2)", Arrays.asList("gave: 3 missiles and Partner (Human)", "received (in the Cargo Hold): 10 scrap, a Burst Laser I and Newcomer (Engi)"));
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("L: traded along with other things (\"3 missiles and Partner (Human)\"): transferred", byId(m, partnerId).status == CrewRegister.Status.TRANSFERRED && said(byId(m, partnerId), "Transferred to Commander Vance's fleet."));
 Setup.chk("L: received in a trade: their first line says from whose fleet", said(find(m, "Newcomer", CrewRegister.Status.PRESENT), "Transferred from Commander Vance's fleet; in the Cargo Hold."));

 // nothing changed: nothing written
 long before = new File(v.root, "crew.txt").lastModified();
 Thread.sleep(1100);
 v.takeStock();
 Setup.chk("W: a look that finds nothing new leaves the register as it was", new File(v.root, "crew.txt").lastModified() == before);

 // the ships served on, from before the master log began (fhp-c-local-session's handoff, 5.51): a ship's voyage log,
 // a trade off her, her starting crew; a renamed ship once, with the name she had; namesakes on a trade never credited
 Ship x0 = v.docked().get(0), y0 = v.boarded();
 c = v.readCopy(y0);
 CrewState gracie = Commission.volunteer("human", new Random(91)); gracie.setName("Gracie");
 CrewState norwyn = Commission.volunteer("rock", new Random(92)); norwyn.setName("Norwyn Schultze");
 CrewState starter = Commission.volunteer("human", new Random(93)); starter.setName("Starter");
 CrewState joiner = Commission.volunteer("engi", new Random(94)); joiner.setName("Joiner");
 for (CrewState t : new CrewState[] {gracie, norwyn, starter, joiner}) { SaveHelper.placeCrew(c.save.getPlayerShip(), t, true); c.save.getPlayerShip().getCrewList().add(t); }
 v.begin().put(y0, c.save, c.hash).commit();
 File vl = new File(v.historyOf(x0), "voyage.log"); vl.getParentFile().mkdirs();
 String vlOld = vl.isFile() ? new String(SafeFiles.read(vl), "UTF-8") : "";
 SafeFiles.writeText(vl, "2000-01-01 00:01  Crew joined: Norwyn Schultze (Rock)\r\n2000-01-01 00:02  Crew joined: Joiner (Engi)\r\n" + vlOld, false); // Windows line endings
 String hl = new String(SafeFiles.read(v.historyLog()), "UTF-8");
 SafeFiles.writeText(v.historyLog(), hl
   + "2000-01-01 00:00  COMMISSION  " + x0.name + "  (" + x0.id + ")\n  The Kestrel (PLAYER_SHIP_HARD), difficulty Easy\n  Crew: Starter (Human)\n"
   + "2000-01-01 00:05  TRADE  " + x0.name + " <-> Spacedock Storage\n  " + x0.name + ":\n    - Crew Gracie\n    - Crew Norwyn Schultze\n    - Crew Starter\n    - Crew Twin\n"
   + "2000-01-01 00:06  CREW  Gracie assigned to the Old Glory.\n2000-01-01 00:06  CREW  Norwyn Schultze assigned to the Old Glory.\n2000-01-01 00:06  CREW  Starter assigned to the Old Glory.\n", false);
 HistoryLog.entry("RENAME", "Old Glory -> " + y0.name + "  (" + y0.id + ")");
 new File(v.root, "crew.txt").delete(); // read in afresh, logs and all
 v.takeStock();
 m = CrewRegister.members(v);
 List<String> both = Arrays.asList(x0.name, y0.name + "\tOld Glory");
 CrewRegister.Member g = find(m, "Gracie", CrewRegister.Status.PRESENT), n = find(m, "Norwyn Schultze", CrewRegister.Status.PRESENT), st = find(m, "Starter", CrewRegister.Status.PRESENT);
 Setup.chk("S: traded off a ship before the master log: she's on the list, then the renamed ship once " + (g == null ? "" : g.served), g != null && g.served.equals(both));
 Setup.chk("S: joined a ship in her voyage log before the master log: on the list first " + (n == null ? "" : n.served), n != null && n.served.equals(both));
 CrewRegister.Member jo = find(m, "Joiner", CrewRegister.Status.PRESENT);
 Setup.chk("S: known only from her voyage log before the master log: she's on the list " + (jo == null ? "" : jo.served), jo != null && jo.served.equals(Arrays.asList(x0.name, y0.name + "\tOld Glory")));
 Setup.chk("S: a starting crew member named in her COMMISSION: came aboard her, newly commissioned " + (st == null ? "" : st.served), st != null && st.served.equals(both) && said(st, "newly commissioned"));
 Setup.chk("S: the renamed ship shows her old name", CrewRegister.shipOf(g.served.get(1)).equals(y0.name) && CrewRegister.formerNames(g.served.get(1)).equals(Arrays.asList("Old Glory")));
 boolean twinOn = false; for (CrewRegister.Member t : all(m, "Twin")) for (String sh : t.served) if (CrewRegister.shipOf(sh).equals(x0.name)) twinOn = true;
 Setup.chk("S: two namesakes on a trade's line: neither credited with her", !twinOn);

 // a register written before 5.51: its ships rebuilt once
 File cf = new File(v.root, "crew.txt");
 String reg = new String(SafeFiles.read(cf), "UTF-8");
 Setup.chk("U: the register says its ships are kept the 5.51 way", reg.contains("served.v=2"));
 reg = reg.replace("served.v=2\n", "").replaceAll("(?m)^" + g.id + "\\.served=.*$", g.id + ".served=The Adjudicator|" + java.util.regex.Matcher.quoteReplacement(y0.name));
 SafeFiles.writeText(cf, reg, false);
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("U: an older register: the ships read again from the logs, one only it knew kept " + byId(m, g.id).served,
   byId(m, g.id).served.equals(Arrays.asList(x0.name, "The Adjudicator", y0.name + "\tOld Glory")) && new String(SafeFiles.read(cf), "UTF-8").contains("served.v=2"));
 Setup.done();
}
 static String Line_name(CrewRegister.Member x) { return x.crew().getName(); }
 static int count(List<CrewRegister.Member> m, CrewRegister.Status s) { int n = 0; for (CrewRegister.Member x : m) if (x.status == s) n++; return n; }
 static CrewRegister.Member find(List<CrewRegister.Member> m, String name, CrewRegister.Status s) { for (CrewRegister.Member x : m) if (x.name.equals(name) && x.status == s) return x; return null; }
 static List<CrewRegister.Member> all(List<CrewRegister.Member> m, String name) { List<CrewRegister.Member> o = new ArrayList<CrewRegister.Member>(); for (CrewRegister.Member x : m) if (x.name.equals(name)) o.add(x); return o; }
 static int idOf(List<CrewRegister.Member> m, String name) { for (CrewRegister.Member x : m) if (x.name.equals(name) && x.status == CrewRegister.Status.PRESENT) return x.id; return -1; }
 static int idIn(List<CrewRegister.Member> m, String name, String where) { for (CrewRegister.Member x : m) if (x.name.equals(name) && x.where.equals(where) && x.status == CrewRegister.Status.PRESENT) return x.id; return -1; }
 static CrewRegister.Member byId(List<CrewRegister.Member> m, int id) { for (CrewRegister.Member x : m) if (x.id == id) return x; return null; }
 static boolean said(CrewRegister.Member x, String text) { if (x == null) return false; for (CrewRegister.Event e : x.events) if (e.text.contains(text)) return true; return false; }
 static void writeProps(File f, Properties p) throws IOException { StringWriter w = new StringWriter(); p.store(w, null); SafeFiles.writeText(f, w.toString(), false); }
}
