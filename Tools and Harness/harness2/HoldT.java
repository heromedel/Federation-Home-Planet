import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The Cargo Hold as xml (5.84): what it holds read back whole, a hand edit read, a 5.x hold's save converted once, its versions kept as xml, another fleet's hold found. args: gamedir, world saves (from WorldT), work */
public class HoldT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 // A: a full hold, written and read back
 Vault.Copy c = v.readCopy(v.storage()); SavedGameState gs = c.save; ShipState h = gs.getPlayerShip();
 h.setScrapAmt(123); h.setFuelAmt(4); h.setMissilesAmt(5); h.setDronePartsAmt(6);
 h.getWeaponList().add(SaveHelper.newIdleWeapon("LASER_BURST_3")); h.getWeaponList().add(SaveHelper.newIdleWeapon("MISSILES_1"));
 h.getDroneList().add(SaveHelper.newIdleDrone("COMBAT_1")); h.getDroneList().add(SaveHelper.newIdleDrone("DEFENSE_1"));
 h.getAugmentIdList().add("SCRAP_COLLECTOR"); h.getAugmentIdList().add("O2_MASKS");
 if (gs.getCargoIdList() != null) gs.getCargoIdList().add("BOMB_1");
 Random rng = new Random(7);
 for (String r : new String[] {"human", "engi", "anaerobic", "crystal"}) { CrewState x = Commission.volunteer(r, rng); SaveHelper.placeCrew(h, x, true); h.getCrewList().add(x); }
 h.getCrewList().get(0).setName("Lucky Duck & \"Co\" <1>"); // what xml must escape
 h.getCrewList().get(1).setPilotSkill(17); h.getCrewList().get(1).setCombatKills(9);
 String before = picture(gs);
 v.begin().put(v.storage(), gs, c.hash).commit();
 File f = v.fileOf(v.storage());
 Setup.chk("A: the hold's file is its xml, in cargohold/, no save beside it", f.equals(new File(v.cargoHoldDir(), "cargohold.xml")) && HoldXml.isHold(f) && !new File(v.cargoHoldDir(), "cargohold.sav").exists());
 v.storage().invalidate();
 String after = picture(v.storage().save());
 Setup.chk("A: she reads back whole: supplies, weapons, drones, augments, cargo, every crew member to the byte", after.equals(before));
 if (!after.equals(before)) System.out.println("  before: " + before + "\n  after:  " + after);
 Setup.chk("A: and a fresh copy reads the same", picture(v.readCopy(v.storage()).save).equals(before));
 // B: an edit by hand, with the station closed: the named values win over FTL's bytes
 String x = new String(SafeFiles.read(f), "UTF-8");
 String edited = x.replace("<scrap>123</scrap>", "<scrap>500</scrap>").replaceFirst("pilot=\"17\"", "pilot=\"30\"");
 SafeFiles.write(f, edited.getBytes("UTF-8"));
 v.storage().invalidate();
 ShipState e = v.storage().save().getPlayerShip();
 Setup.chk("B: a hand edit is read: 500 scrap, her pilot skill 30, the rest as it was", e.getScrapAmt() == 500 && e.getCrewList().get(1).getPilotSkill() == 30 && e.getCrewList().get(1).getCombatKills() == 9 && e.getCrewList().size() == 4);
 // C: versions are kept as xml, and pruned like any ship's
 for (int i = 0; i < 3; i++) { Vault.Copy k = v.readCopy(v.storage()); k.save.getPlayerShip().setScrapAmt(600 + i); v.begin().put(v.storage(), k.save, k.hash).commit(); Thread.sleep(1100); }
 List<File> kept = v.history(v.storage());
 boolean allXml = !kept.isEmpty(); for (File k : kept) allXml &= k.getName().endsWith(".xml") && HoldXml.isHold(k);
 Setup.chk("C: its earlier versions kept as xml (" + kept.size() + "), the newest the one before the last change", allXml && HoldXml.read(kept.get(kept.size() - 1)).getPlayerShip().getScrapAmt() == 601);
 // D: a 5.x hold: its save converted once on opening, its contents the same, said in the log
 Vault.Copy last = v.readCopy(v.storage());
 String was = picture(last.save);
 File sav = new File(v.cargoHoldDir(), "cargohold.sav");
 SafeFiles.write(sav, SaveHelper.toBytes(last.save));
 String record = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<ship version=\"1\" id=\"storage\" name=\"Spacedock Storage\" state=\"storage\" dlc=\"true\" hash=\"x\">\r\n</ship>\r\n";
 SafeFiles.write(f, record.getBytes("UTF-8"));
 Setup.chk("D: another fleet's hold, not yet opened since 5.84, is found by its save", Vault.holdFileIn(v.root).equals(sav) && picture(HoldXml.read(Vault.holdFileIn(v.root))).equals(was));
 v = Setup.open(game, saves); v.takeStock();
 Setup.chk("D: on opening the save becomes the xml, the record it replaced gone, the save gone", HoldXml.isHold(f) && !sav.exists() && picture(v.storage().save()).equals(was));
 boolean logged = false; for (EventLog.Entry en : EventLog.read(v)) if (en.kind.equals("HOLD_FILE") && "converted".equals(en.get("what")) && "602".equals(en.get("scrap")) && "4".equals(en.get("crew"))) logged = true;
 Setup.chk("D: and the event log says so, with what it held", logged);
 Setup.chk("D: no note of it left in station-action-protection", Journal.dir(v).list().length == 1);
 // E: a fleet from before 5.72, its hold at the root
 File other = new File(work, "old"); other.mkdirs();
 SafeFiles.write(new File(other, "storage.sav"), SaveHelper.toBytes(last.save));
 Setup.chk("E: a fleet from before 5.72: its hold found at the root and read", Vault.holdFileIn(other).getName().equals("storage.sav") && picture(HoldXml.read(Vault.holdFileIn(other))).equals(was));
 Setup.done();
}
 /** What a hold holds, in one line: supplies, the ids of its goods, every crew member's FTL bytes. */
 static String picture(SavedGameState gs) throws IOException {
  ShipState s = gs.getPlayerShip();
  StringBuilder b = new StringBuilder(s.getScrapAmt() + "/" + s.getFuelAmt() + "/" + s.getMissilesAmt() + "/" + s.getDronePartsAmt());
  for (WeaponState w : s.getWeaponList()) b.append(" w:").append(w.getWeaponId());
  for (DroneState d : s.getDroneList()) b.append(" d:").append(d.getDroneId()).append('@').append(d.getHealth());
  b.append(" a:").append(s.getAugmentIdList()).append(" c:").append(gs.getCargoIdList());
  SavedGameParser p = new SavedGameParser();
  for (CrewState c : s.getCrewList()) { ByteArrayOutputStream o = new ByteArrayOutputStream(); p.writeCrewMember(o, c, gs.getFileFormat()); b.append(" crew:").append(c.getName()).append('=').append(Base64.getEncoder().encodeToString(o.toByteArray()).hashCode()); }
  return b.toString();
 }
}
