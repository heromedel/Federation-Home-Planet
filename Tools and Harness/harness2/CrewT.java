import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The crew register and the Crew Log (5.41): ids that hold through namesakes, renames and moves; what became of those no longer found. args: gamedir, world saves, work */
public class CrewT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive(); HomePlanet.expeditionType = 2;
 Vault v = Setup.open(game, saves); v.storage();
 HistoryLog.entry("CREW", "Old Hand assigned to the Cargo Hold."); // before the register: read in once
 Setup.voyage(v, "Test Kestrel", "Crew lost: Old Hand (Human)"); // a namesake lost long ago: never pinned on the living one
 List<CrewState> hold = ExpT.hold(v, "human", "human", "engi", "rock", "human");
 // two namesakes, told apart by their colouring and service record
 Vault.Copy c = v.readCopy(v.storage());
 List<CrewState> h = SaveHelper.getOwnCrew(c.save.getPlayerShip());
 h.get(0).setName("Twin"); h.get(1).setName("Twin"); h.get(1).setJumpsSurvived(h.get(0).getJumpsSurvived() + 5);
 h.get(4).setName("Old Hand");
 v.begin().put(v.storage(), c.save, c.hash).commit();
 Setup.forgetCrew(v); // as a fleet updating to 5.41: its logs already written, its register new
 v.takeStock();
 List<CrewRegister.Member> m = CrewRegister.members(v);
 int fleet = 0;
 for (Ship s : v.all()) { if (s.save() != null && s.save().getPlayerShip() != null) fleet += SaveHelper.getOwnCrew(s.save().getPlayerShip()).size(); }
 Setup.chk("R: a new register: everyone in the fleet has an id, all present", count(m, CrewRegister.Status.PRESENT) == fleet && CrewRegister.registerFileOf(v).isFile() && CrewRegister.fileOf(v, m.get(0).id) != null);
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
 for (Map.Entry<String, String> e : homeplanet.vault.CrewRecord.of(rock).entrySet()) cap.setProperty("0.crew." + e.getKey(), e.getValue());
 c.save.getPlayerShip().getCrewList().remove(rock);
 v.begin().put(v.storage(), c.save, c.hash).commit();
 Store.write(Expeditions.captivesFile(v), cap, null);
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("C: taken captive: the same id, missing, held by pirates", byId(m, rockId).status == CrewRegister.Status.CAPTIVE && byId(m, rockId).where.contains("pirates"));
 cap.setProperty("0.state", "gone");
 Store.write(Expeditions.captivesFile(v), cap, null);
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
 Setup.voyage(v, b, "Crew lost: " + gone.getName() + " (" + homeplanet.model.Crew.raceTitle(gone) + ")");
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("F: lost aboard her in FTL: killed", byId(m, goneId).status == CrewRegister.Status.KILLED && said(byId(m, goneId), "Lost aboard"));
 // a Rock lost aboard her, in her voyage log's word for the race (Rock), FTL's title being Rockman (5.61: listed missing)
 c = v.readCopy(b);
 CrewState stoneface = Commission.volunteer("rock", new Random(5));
 stoneface.setName("Stoneface");
 SaveHelper.placeCrew(c.save.getPlayerShip(), stoneface, false);
 c.save.getPlayerShip().getCrewList().add(stoneface);
 v.begin().put(b, c.save, c.hash).commit();
 v.takeStock();
 m = CrewRegister.members(v);
 int rockLostId = idOf(m, "Stoneface");
 c = v.readCopy(b);
 for (CrewState x : new ArrayList<CrewState>(c.save.getPlayerShip().getCrewList())) if (x.getName().equals("Stoneface")) c.save.getPlayerShip().getCrewList().remove(x);
 v.begin().put(b, c.save, c.hash).commit();
 Setup.voyage(v, b, "Crew lost: Stoneface (Rock)");
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("F: a Rock lost aboard her, said as her voyage log says it (Rock): killed, lost aboard " + ShipNames.the(b.name), rockLostId >= 0 && byId(m, rockLostId).status == CrewRegister.Status.KILLED
   && said(byId(m, rockLostId), "Lost aboard " + ShipNames.the(b.name) + "."));

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
 String before = Setup.crewStamp(v);
 Thread.sleep(1100);
 v.takeStock();
 Setup.chk("W: a look that finds nothing new leaves the register as it was (its file and every crew file)", Setup.crewStamp(v).equals(before));

 // the ships served on, from before the master log began (fhp-c-local-session's handoff, 5.51): a ship's voyage log,
 // a trade off her, her starting crew; a renamed ship once, with the name she had; namesakes on a trade never credited
 Ship x0 = v.docked().get(0), y0 = v.boarded();
 c = v.readCopy(y0);
 CrewState gracie = Commission.volunteer("human", new Random(91)); gracie.setName("Gracie Quill");
 CrewState norwyn = Commission.volunteer("rock", new Random(92)); norwyn.setName("Norwyn Schultze");
 CrewState starter = Commission.volunteer("human", new Random(93)); starter.setName("Starter");
 CrewState joiner = Commission.volunteer("engi", new Random(94)); joiner.setName("Joiner");
 for (CrewState t : new CrewState[] {gracie, norwyn, starter, joiner}) { SaveHelper.placeCrew(c.save.getPlayerShip(), t, true); c.save.getPlayerShip().getCrewList().add(t); }
 v.begin().put(y0, c.save, c.hash).commit();
 // the old logs as the 5.73 conversion read them into the event log (5.91: the register reads only that): a voyage's crew, a commission, a trade, the crew assigned
 EventLog.write(v, old("CREW_JOINED", "voyage", "2000-01-01 00:01:00").put("ship", x0.name + "." + x0.id).put("ship_name", x0.name).put("ship_id", x0.id).put("crew", "Norwyn Schultze").put("race", "Rock").human("Crew joined: Norwyn Schultze (Rock)"));
 EventLog.write(v, old("CREW_JOINED", "voyage", "2000-01-01 00:02:00").put("ship", x0.name + "." + x0.id).put("ship_name", x0.name).put("ship_id", x0.id).put("crew", "Joiner").put("race", "Engi").human("Crew joined: Joiner (Engi)"));
 EventLog.write(v, old("COMMISSION", "station", "2000-01-01 00:00:00").put("headline", x0.name + "  (" + x0.id + ")").detail("The Kestrel (PLAYER_SHIP_HARD), difficulty Easy").detail("Crew: Starter (Human)").human(x0.name));
 EventLog.write(v, old("TRADE", "station", "2000-01-01 00:05:00").put("headline", x0.name + " <-> Spacedock Storage").detail(x0.name + ":").detail("  - Crew Gracie Quill").detail("  - Crew Norwyn Schultze").detail("  - Crew Starter").detail("  - Crew Twin").human("trade"));
 for (String who : new String[] {"Gracie Quill", "Norwyn Schultze", "Starter"}) EventLog.write(v, old("CREW", "station", "2000-01-01 00:06:00").put("headline", who + " assigned to the Old Glory.").human(who));
 HistoryLog.entry("RENAME", "Old Glory -> " + y0.name + "  (" + y0.id + ")");
 Setup.forgetCrew(v); // read in afresh, logs and all
 v.takeStock();
 m = CrewRegister.members(v);
 List<String> both = Arrays.asList(x0.name, y0.name + "\tOld Glory");
 CrewRegister.Member g = find(m, "Gracie Quill", CrewRegister.Status.PRESENT), n = find(m, "Norwyn Schultze", CrewRegister.Status.PRESENT), st = find(m, "Starter", CrewRegister.Status.PRESENT);
 Setup.chk("S: traded off a ship before the master log: she's on the list, then the renamed ship once " + (g == null ? "" : g.served), g != null && g.served.equals(both));
 Setup.chk("S: joined a ship in her voyage log before the master log: on the list first " + (n == null ? "" : n.served), n != null && n.served.equals(both));
 CrewRegister.Member jo = find(m, "Joiner", CrewRegister.Status.PRESENT);
 Setup.chk("S: known only from her voyage log before the master log: she's on the list " + (jo == null ? "" : jo.served), jo != null && jo.served.equals(Arrays.asList(x0.name, y0.name + "\tOld Glory")));
 Setup.chk("S: a starting crew member named in her COMMISSION: came aboard her, newly commissioned " + (st == null ? "" : st.served), st != null && st.served.equals(both) && said(st, "newly commissioned"));
 Setup.chk("S: the renamed ship shows her old name", CrewRegister.shipOf(g.served.get(1)).equals(y0.name) && CrewRegister.formerNames(g.served.get(1)).equals(Arrays.asList("Old Glory")));
 boolean twinOn = false; for (CrewRegister.Member t : all(m, "Twin")) for (String sh : t.served) if (CrewRegister.shipOf(sh).equals(x0.name)) twinOn = true;
 Setup.chk("S: two namesakes on a trade's line: neither credited with her", !twinOn);

 // a register written before 5.51: its ships rebuilt once
 File cf = CrewRegister.registerFileOf(v), gf = CrewRegister.fileOf(v, g.id);
 String reg = new String(SafeFiles.read(cf), "UTF-8");
 Setup.chk("U: the register says its ships are kept the 5.51 way", reg.contains("<entry key=\"served.v\">2</entry>"));
 SafeFiles.writeText(cf, reg.replace("<entry key=\"served.v\">2</entry>\r\n", ""), false);
 String gx = new String(SafeFiles.read(gf), "UTF-8"); // her own file (5.83): the served entry as an older register would have it
 SafeFiles.writeText(gf, gx.replaceAll("(?s)<served>.*?</served>", java.util.regex.Matcher.quoteReplacement("<served><ship><name>The Adjudicator</name></ship><ship><name>" + homeplanet.parser.XmlText.text(y0.name) + "</name></ship></served>")), false);
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("U: an older register: the ships read again from the logs, one only it knew kept " + byId(m, g.id).served,
   byId(m, g.id).served.equals(Arrays.asList(x0.name, "The Adjudicator", y0.name + "\tOld Glory")) && new String(SafeFiles.read(cf), "UTF-8").contains("<entry key=\"served.v\">2</entry>"));
 // ranks (heromedel, 5.52): a prefix on the name, worn with or without the dot
 Setup.chk("K: ranks read from a name: Lt Gracie, sgt. gracie, none", homeplanet.model.Rank.worn("Lt Gracie") == 1 && homeplanet.model.Rank.worn("sgt. gracie") == 0 && homeplanet.model.Rank.worn("Gracie") == -1 && homeplanet.model.Rank.worn("Lt.") == -1);
 Setup.chk("K: a promotion swaps the rank, never stacks it", homeplanet.model.Rank.promoted("Sgt. Gracie", 1).equals("Lt. Gracie") && "Lieutenant".equals(homeplanet.model.Rank.promotion("Sgt Gracie", "Lt. Gracie")) && homeplanet.model.Rank.promotion("Gracie", "Grace") == null);

 // served with: everyone aboard together has each other, by id
 m = CrewRegister.members(v);
 g = find(m, "Gracie Quill", CrewRegister.Status.PRESENT); n = find(m, "Norwyn Schultze", CrewRegister.Status.PRESENT);
 Setup.chk("W: aboard together: each has the other, on that ship", g.with.containsKey(n.id) && g.with.get(n.id).contains(y0.name) && n.with.containsKey(g.id));
 c = v.readCopy(x0);
 for (int k = 0; k < 2; k++) { CrewState t = Commission.volunteer("human", new Random(60 + k)); t.setName("Pair"); t.setJumpsSurvived(k * 3); SaveHelper.placeCrew(c.save.getPlayerShip(), t, true); c.save.getPlayerShip().getCrewList().add(t); }
 v.begin().put(x0, c.save, c.hash).commit();
 v.takeStock();
 m = CrewRegister.members(v);
 List<CrewRegister.Member> pairs = all(m, "Pair");
 Setup.chk("W: two namesakes aboard together: two ids, each with the other, not themselves", pairs.size() == 2 && pairs.get(0).with.containsKey(pairs.get(1).id) && pairs.get(1).with.containsKey(pairs.get(0).id)
   && !pairs.get(0).with.containsKey(pairs.get(0).id));
 List<CrewState> party = new ArrayList<CrewState>();
 for (CrewState x : Expeditions.holdCrew(v)) if (party.size() < 2 && !x.getName().equals("Twin")) party.add(x);
 int pa = idIn(m, party.get(0).getName(), "in the Cargo Hold"), pb = idIn(m, party.get(1).getName(), "in the Cargo Hold");
 List<Assignments.Offer> board = Assignments.board(v);
 Assignments.send(v, board.get(board.size() - 1).slot, party, new Random(5));
 v.takeStock();
 m = CrewRegister.members(v);
 Setup.chk("W: sent out together: During Expeditions, both ways", pa > 0 && pb > 0 && byId(m, pa).with.containsKey(pb) && byId(m, pa).with.get(pb).contains(CrewRegister.WITH_EXPEDITION) && byId(m, pb).with.get(pa).contains(CrewRegister.WITH_EXPEDITION));

 // milestones: a mastery, the first kill, sector 5; each once
 c = v.readCopy(y0);
 CrewState gs0 = null; for (CrewState x : SaveHelper.getOwnCrew(c.save.getPlayerShip())) if (x.getName().equals("Gracie Quill")) gs0 = x;
 gs0.setCombatKills(2); homeplanet.model.Skills.set(gs0, 3, 2 * homeplanet.model.Skills.interval(gs0, 3)); c.save.setSectorNumber(2);
 v.begin().put(y0, c.save, c.hash).commit();
 v.takeStock();
 c = v.readCopy(y0); c.save.setSectorNumber(4);
 v.begin().put(y0, c.save, c.hash).commit();
 v.takeStock(); v.takeStock();
 g = byId(CrewRegister.members(v), g.id);
 Setup.chk("H: a mastery, the first kill, sector 5 for the first time: each written once", count(g, "Mastered Weapons.") == 1 && count(g, "Earned the first Weapons mastery.") == 1 && count(g, "First kill, aboard") == 1
   && count(g, "Reached sector 5 for the first time, aboard " + homeplanet.parser.ShipNames.the(y0.name)) == 1 && count(g, "Reached sector 8") == 0);

 // promotions: one skill fully mastered is Sgt.; promoted in her save, the same id
 Setup.chk("K: Weapons fully mastered: Sergeant due", CrewRegister.rankDue(g) == 0);
 String gName = CrewRegister.promote(v, g.id);
 m = CrewRegister.members(v);
 c = v.readCopy(y0);
 boolean inSave = false; for (CrewState x : SaveHelper.getOwnCrew(c.save.getPlayerShip())) if (x.getName().equals("Sgt. Gracie Quill")) inSave = true;
 Setup.chk("K: promoted: Sgt. Gracie Quill in her ship's save, the same id, Promoted to Sergeant, nothing more due", gName.equals("Sgt. Gracie Quill") && inSave && byId(m, g.id).name.equals("Sgt. Gracie Quill")
   && said(byId(m, g.id), "Promoted to Sergeant.") && !said(byId(m, g.id), "Now known as") && CrewRegister.rankDue(byId(m, g.id)) == -1);
 // the KIA: posthumously, on the record alone
 c = v.readCopy(y0);
 CrewState nw = null; for (CrewState x : SaveHelper.getOwnCrew(c.save.getPlayerShip())) if (x.getName().equals("Norwyn Schultze")) nw = x;
 homeplanet.model.Skills.set(nw, 0, 2 * homeplanet.model.Skills.interval(nw, 0)); homeplanet.model.Skills.set(nw, 1, 2 * homeplanet.model.Skills.interval(nw, 1));
 v.begin().put(y0, c.save, c.hash).commit();
 v.takeStock();
 c = v.readCopy(y0);
 for (CrewState x : SaveHelper.getOwnCrew(c.save.getPlayerShip())) if (x.getName().equals("Norwyn Schultze")) nw = x;
 c.save.getPlayerShip().getCrewList().remove(nw);
 v.begin().put(y0, c.save, c.hash).commit();
 Setup.voyage(v, y0, "Crew lost: Norwyn Schultze (" + homeplanet.model.Crew.raceTitle(nw) + ")");
 v.takeStock();
 n = byId(CrewRegister.members(v), n.id);
 Setup.chk("K: two skills mastered, killed: Lieutenant due posthumously", n.status == CrewRegister.Status.KILLED && CrewRegister.rankDue(n) == 1 && CrewRegister.cannotPromote(v, n) == null);
 CrewRegister.promote(v, n.id);
 n = byId(CrewRegister.members(v), n.id);
 Setup.chk("K: promoted posthumously: Lt. Norwyn Schultze on the record, still KIA", n.name.equals("Lt. Norwyn Schultze") && n.status == CrewRegister.Status.KILLED && said(n, "Promoted posthumously to Lieutenant."));
 Setup.chk("K: still known to those they served with", byId(CrewRegister.members(v), g.id).with.containsKey(n.id));
 // MIA (heromedel: no one is left out): on the record; found again under their old name, the record keeps it, and Promote puts it in their save
 Ship dk = v.docked().get(0);
 c = v.readCopy(dk);
 CrewState mia = Commission.volunteer("engi", new Random(71)); mia.setName("Wanderer");
 homeplanet.model.Skills.set(mia, 4, 2 * homeplanet.model.Skills.interval(mia, 4));
 SaveHelper.placeCrew(c.save.getPlayerShip(), mia, true); c.save.getPlayerShip().getCrewList().add(mia);
 v.begin().put(dk, c.save, c.hash).commit();
 v.takeStock();
 int wid = idOf(CrewRegister.members(v), "Wanderer");
 c = v.readCopy(dk);
 for (CrewState x : SaveHelper.getOwnCrew(c.save.getPlayerShip())) if (x.getName().equals("Wanderer")) mia = x;
 c.save.getPlayerShip().getCrewList().remove(mia);
 v.begin().put(dk, c.save, c.hash).commit();
 v.takeStock();
 CrewRegister.Member wm = byId(CrewRegister.members(v), wid);
 Setup.chk("K: MIA, a skill mastered: Sergeant due, nothing in the way", wm.status == CrewRegister.Status.MISSING && CrewRegister.rankToGive(v, wm) == 0 && CrewRegister.cannotPromote(v, wm) == null);
 CrewRegister.promote(v, wid);
 c = v.readCopy(dk);
 SaveHelper.placeCrew(c.save.getPlayerShip(), mia, true); c.save.getPlayerShip().getCrewList().add(mia);
 v.begin().put(dk, c.save, c.hash).commit();
 v.takeStock();
 wm = byId(CrewRegister.members(v), wid);
 Setup.chk("K: promoted on the record, found again as Wanderer: still Sgt. Wanderer, no new name in the career, Promote offers to put it in FTL",
   wm.status == CrewRegister.Status.PRESENT && wm.name.equals("Sgt. Wanderer") && count(wm, "Promoted to Sergeant.") == 1 && !said(wm, "Now known as") && CrewRegister.rankToGive(v, wm) == 0);
 CrewRegister.promote(v, wid);
 c = v.readCopy(dk);
 boolean worn = false; for (CrewState x : SaveHelper.getOwnCrew(c.save.getPlayerShip())) if (x.getName().equals("Sgt. Wanderer")) worn = true;
 wm = byId(CrewRegister.members(v), wid);
 Setup.chk("K: and in her save now, the career unchanged, nothing more to give", worn && count(wm, "Promoted to Sergeant.") == 1 && CrewRegister.rankToGive(v, wm) == -1);
 String cl = LogT.page(v, false);
 Setup.chk("K: the Captain's Log: I promoted Gracie Quill to Sergeant; Norwyn Schultze posthumously", cl.contains("I promoted Gracie Quill to Sergeant.") && cl.contains("I promoted Norwyn Schultze to Lieutenant, posthumously."));
 Setup.chk("K: the Captain's Log tells Wanderer's promotion once: putting it in her save later is no second promotion", cl.split("I promoted Wanderer to Sergeant\\.", -1).length == 2);

 // skill levels from the points as they stand, not FTL's marks (5.62): FTL marks only a level earned in play
 CrewState envoy = Commission.volunteer("energy", new Random(7)); // the Zoltan peace quest's Envoy: every skill full, no marks
 CrewState charlie = Commission.volunteer("human", new Random(8)); // the event's Charlie: every skill at level one, no marks
 CrewState cloned = Commission.volunteer("engi", new Random(9)); // marks for level two kept, points back to level one (a Clone Bay)
 for (int i = 0; i < 6; i++) {
  int iv = homeplanet.model.Skills.interval(envoy, i);
  setRaw(envoy, i, 2 * iv); setRaw(charlie, i, homeplanet.model.Skills.interval(charlie, i));
  homeplanet.model.Skills.set(cloned, i, 2 * homeplanet.model.Skills.interval(cloned, i)); setRaw(cloned, i, homeplanet.model.Skills.interval(cloned, i));
 }
 setMarks(envoy, false); setMarks(charlie, false);
 Setup.chk("M: full points with no marks: mastered (" + Arrays.toString(homeplanet.model.Crew.skillLevels(envoy)) + "), the hover text says so",
   Arrays.equals(homeplanet.model.Crew.skillLevels(envoy), new int[] {2, 2, 2, 2, 2, 2}) && homeplanet.model.Crew.tooltip(envoy).contains("level 2 (max)"));
 Setup.chk("M: level-one points with no marks: level one (" + Arrays.toString(homeplanet.model.Crew.skillLevels(charlie)) + ")", Arrays.equals(homeplanet.model.Crew.skillLevels(charlie), new int[] {1, 1, 1, 1, 1, 1}));
 Setup.chk("M: marks kept after a Clone Bay, points at level one: level one (" + Arrays.toString(homeplanet.model.Crew.skillLevels(cloned)) + ")", Arrays.equals(homeplanet.model.Crew.skillLevels(cloned), new int[] {1, 1, 1, 1, 1, 1}));
 Setup.chk("M: and the rank agrees: six skills mastered, a Captain due", homeplanet.model.Rank.mastered(envoy) == 6 && homeplanet.model.Rank.due(envoy) == 5);

 // a crew file from pre611 6.11 (Java's properties, the wire's names): read as it is, written as tags at its next save, every field the same (Plan O)
 v.takeStock();
 List<CrewRegister.Member> pre611 = CrewRegister.members(v);
 int olds = 0;
 for (CrewRegister.Member x : pre611) {
  File f = CrewRegister.fileOf(v, x.id); if (f == null) continue;
  Properties p = CrewRegister.readFile(f), wire = new Properties(); wire.setProperty("id", Integer.toString(x.id));
  Map<String, String> rec = new LinkedHashMap<String, String>();
  for (String k : p.stringPropertyNames()) { if (k.startsWith("rec.")) rec.put(k.substring(4), p.getProperty(k)); else wire.setProperty(k, p.getProperty(k)); }
  for (Map.Entry<String, String> e : CrewRecord.toWire(rec).entrySet()) wire.setProperty("rec." + e.getKey(), e.getValue());
  OutputStream o = new FileOutputStream(f); wire.storeToXML(o, null); o.close(); olds++;
 }
 Setup.chk("F: every crew file written the old way (" + olds + ")", olds == pre611.size() && olds > 0);
 Setup.chk("F: read as they are, every field the same", same(pre611, CrewRegister.members(v)));
 HistoryLog.entry("CREW", "A test entry: the log grows, the register writes at its next look.");
 v.takeStock();
 int tags = 0; for (CrewRegister.Member x : pre611) { String t = new String(SafeFiles.read(CrewRegister.fileOf(v, x.id)), "UTF-8"); if (t.contains("<crew>") && t.contains("<last_seen>") && !t.contains("<entry")) tags++; }
 Setup.chk("F: written as tags at the next save (" + tags + " of " + pre611.size() + ")", tags == pre611.size());
 Setup.chk("F: and every field still the same", same(pre611, CrewRegister.members(v)));
 Setup.done();
}
 /** Two reads of the register the same, member by member: who, where, their ships, who they served with, their days, their whole record. */
 static boolean same(List<CrewRegister.Member> a, List<CrewRegister.Member> b) {
  if (a.size() != b.size()) { System.out.println("  members " + a.size() + " vs " + b.size()); return false; }
  for (int i = 0; i < a.size(); i++) if (!describe(a.get(i)).equals(describe(b.get(i)))) { System.out.println("  differs:\n   " + describe(a.get(i)) + "\n   " + describe(b.get(i))); return false; }
  return true;
 }
 static String describe(CrewRegister.Member x) {
  StringBuilder sb = new StringBuilder().append(x.id).append('|').append(x.name).append('|').append(x.race).append('|').append(x.title).append('|').append(x.male).append('|').append(x.where).append('|').append(x.status).append('|').append(x.served).append('|');
  for (Map.Entry<Integer, List<String>> w : x.with.entrySet()) sb.append(w.getKey()).append(w.getValue());
  for (CrewRegister.Event e : x.events) sb.append('|').append(e.day).append(':').append(e.text);
  CrewState c = x.crew(); sb.append('|').append(c == null ? "none" : new TreeMap<String, String>(CrewRecord.of(c)).toString());
  return sb.toString();
 }
 static int count(CrewRegister.Member x, String text) { int k = 0; for (CrewRegister.Event e : x.events) if (e.text.contains(text)) k++; return k; }
 static String Line_name(CrewRegister.Member x) { return x.crew().getName(); }
 static int count(List<CrewRegister.Member> m, CrewRegister.Status s) { int n = 0; for (CrewRegister.Member x : m) if (x.status == s) n++; return n; }
 static CrewRegister.Member find(List<CrewRegister.Member> m, String name, CrewRegister.Status s) { for (CrewRegister.Member x : m) if (x.name.equals(name) && x.status == s) return x; return null; }
 static List<CrewRegister.Member> all(List<CrewRegister.Member> m, String name) { List<CrewRegister.Member> o = new ArrayList<CrewRegister.Member>(); for (CrewRegister.Member x : m) if (x.name.equals(name)) o.add(x); return o; }
 static int idOf(List<CrewRegister.Member> m, String name) { for (CrewRegister.Member x : m) if (x.name.equals(name) && x.status == CrewRegister.Status.PRESENT) return x.id; return -1; }
 static int idIn(List<CrewRegister.Member> m, String name, String where) { for (CrewRegister.Member x : m) if (x.name.equals(name) && x.where.equals(where) && x.status == CrewRegister.Status.PRESENT) return x.id; return -1; }
 static CrewRegister.Member byId(List<CrewRegister.Member> m, int id) { for (CrewRegister.Member x : m) if (x.id == id) return x; return null; }
 static boolean said(CrewRegister.Member x, String text) { if (x == null) return false; for (CrewRegister.Event e : x.events) if (e.text.contains(text)) return true; return false; }
 /** A skill's points as FTL can leave them, the marks untouched. */
 static void setRaw(CrewState c, int skill, int p) {
  switch (skill) { case 0: c.setPilotSkill(p); break; case 1: c.setEngineSkill(p); break; case 2: c.setShieldSkill(p); break; case 3: c.setWeaponSkill(p); break; case 4: c.setRepairSkill(p); break; default: c.setCombatSkill(p); }
 }
 static void setMarks(CrewState c, boolean b) {
  c.setPilotMasteryOne(b); c.setPilotMasteryTwo(b); c.setEngineMasteryOne(b); c.setEngineMasteryTwo(b); c.setShieldMasteryOne(b); c.setShieldMasteryTwo(b);
  c.setWeaponMasteryOne(b); c.setWeaponMasteryTwo(b); c.setRepairMasteryOne(b); c.setRepairMasteryTwo(b); c.setCombatMasteryOne(b); c.setCombatMasteryTwo(b);
 }
 static void writeProps(File f, Properties p) throws IOException { StringWriter w = new StringWriter(); p.store(w, null); SafeFiles.writeText(f, w.toString(), false); }
 /** An entry as the 5.73 conversion read one in from the old logs: its own time, Prior, converted. */
 static Event old(String kind, String log, String time) { return Event.of(kind).put("log", log).put("time", time).put("day", "0").put("converted", "true"); }
}
