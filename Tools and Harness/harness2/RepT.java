import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The repair job (the Nightjar): the trigger, her build, Return Her, the demand, the claims office, the Junkyard loophole. args: gamedir, world saves (from WorldT), work */
public class RepT {
 static File game, work; static int fleets = 0;
 public static void main(String[] a) throws Exception {
  game = new File(a[0]); work = new File(a[2]); SafeFiles.deleteTree(work);
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
  HomePlanet.commissionCosts = true; HomePlanet.immersiveNotifications = true;
  returned();
  build();
  sentByReply();
  seizedDocked();
  lateAndBoarded();
  hidden();
  anotherShip();
  Setup.done();
 }

 static Transmissions.Message find(String key) { return ChainT.find(key); }
 static ShipState st(Vault v, Ship s) throws Exception { return HomePlanet.savedGameParser.readSavedGame(v.fileOf(s)).getPlayerShip(); }

 /** A new fleet with a boarded ship (to travel), the offer earned by making three wrecks fly, accepted, the Nightjar delivered. */
 static Vault fleet(boolean accept) throws Exception {
  File saves = new File(work, "saves" + (++fleets)); saves.mkdirs();
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  Ship run = v.adopt(Commission.build("PLAYER_SHIP_HARD", "Runner", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(5)));
  v.board(run); v.takeStock();
  List<Ship> wrecks = new ArrayList<Ship>();
  for (int i = 0; i < 3; i++) {
   SavedGameState g = Commission.build("PLAYER_SHIP_CIRCLE", "Wreck " + i, net.blerf.ftl.constants.Difficulty.NORMAL, new Random(i));
   SystemState e = g.getPlayerShip().getSystem(SystemType.ENGINES); e.setDamagedBars(e.getCapacity());
   wrecks.add(v.adopt(g));
  }
  v.takeStock(); Transmissions.check();
  for (int i = 0; i < 3; i++) {
   Ship w = wrecks.get(i);
   SavedGameState g = v.readCopy(w).save; g.getPlayerShip().getSystem(SystemType.ENGINES).setDamagedBars(0); v.write(w, g);
   Transmissions.check();
   if (fleets == 1 && i == 1) Setup.chk("T: two made to fly: no offer yet", find(RepairJob.OFFER) == null);
  }
  Transmissions.Message offer = find(RepairJob.OFFER);
  if (fleets == 1) {
   Setup.chk("T: the third: the collector's offer, two replies", offer != null && Transmissions.replyTexts(offer).size() == 2);
   Setup.chk("T: her class, name and bonus filled in (" + offer.body.substring(offer.body.indexOf("classic"), offer.body.indexOf("wrecked")) + ")",
     offer.body.contains("a classic Stealth Cruiser, the Nightjar, but my idiot brother took her out") && !offer.body.contains("{") && offer.body.matches("(?s).*plus [2-5]\\d\\d scrap.*"));
   Transmissions.check();
   int n = 0; for (Transmissions.Message m : Transmissions.load()) if (m.key.equals(RepairJob.OFFER)) n++;
   Setup.chk("T: the offer comes once", n == 1);
  }
  if (!accept) return v;
  Transmissions.reply(offer, 0);
  ChainT.jump(v, 5); Transmissions.check();
  return v;
 }

 static void build() throws Exception {
  SavedGameState g = RepairJob.build(new Random(1));
  ShipState s = g.getPlayerShip();
  Setup.chk("N: a Stealth Cruiser on the station's blank copy, named Nightjar", (RepairJob.BLUEPRINT + Retrofit.SUFFIX).equals(s.getShipBlueprintId()) && "Nightjar".equals(g.getPlayerShipName()));
  SystemState tp = s.getSystem(SystemType.TELEPORTER);
  Setup.chk("N: a teleporter (broken) and the Zoltan shield aboard", tp != null && tp.getCapacity() == 1 && tp.getDamagedBars() == 1 && s.getAugmentIdList().contains("ENERGY_SHIELD"));
  Setup.chk("N: no crew, can't fly, breaches", s.getCrewList().isEmpty() && !RepairJob.flyable(s) && s.getBreachMap().size() == 2);
  byte[] once = SaveHelper.toBytes(g);
  File f = File.createTempFile("nightjar", ".sav"); SafeFiles.write(f, once);
  byte[] twice = SaveHelper.toBytes(HomePlanet.savedGameParser.readSavedGame(f)); f.delete();
  Setup.chk("N: her save round-trips byte for byte", Arrays.equals(once, twice));
 }

 /** Mends her completely: hull, bars, breaches. */
 static void mend(Vault v, Ship s) throws Exception {
  SavedGameState g = v.readCopy(s).save; ShipState x = g.getPlayerShip();
  x.setHullAmt(x.getHullAmt() + Pricing.missingHull(x));
  for (SystemType t : SystemType.values()) { SystemState y = x.getSystem(t); if (y != null) y.setDamagedBars(0); }
  x.getBreachMap().clear();
  v.write(s, g);
 }

 static void returned() throws Exception {
  Vault v = fleet(true);
  Ship n = RepairJob.ship(v);
  Setup.chk("R: accepted: She's on her way, and the Nightjar in the Junkyard", find(RepairJob.ACCEPTED) != null && n != null && n.state == Ship.State.JUNKED);
  Transmissions.check(); Transmissions.check();
  int count = 0; for (Ship s : v.all()) if ("Nightjar".equals(s.name)) count++;
  Setup.chk("R: delivered once", count == 1);
  v.salvage(n);
  Setup.chk("R: salvaged but broken: Return Her isn't offered", !RepairJob.ready(v, n));
  mend(v, n);
  Setup.chk("R: whole again: she can be returned; she's marked as borrowed from the collector", RepairJob.ready(v, n)
    && Borrowed.of(v, n.id) != null && RepairJob.OWNER.equals(Borrowed.of(v, n.id).owner));
  Transmissions.check(); Transmissions.check();
  Transmissions.Message ready = find(RepairJob.READY);
  int letters = 0; for (Transmissions.Message x : Transmissions.load()) if (x.key.equals(RepairJob.READY)) letters++;
  Setup.chk("R: The Home Planet Station's letter: ready to return, two replies, the payment named, once", ready != null && letters == 1
    && Transmissions.replyTexts(ready).equals(Arrays.asList("Send her home.", "Not yet.")) && ready.body.contains(RepairJob.payment(v, false) + " scrap"));
  Transmissions.reply(ready, 1);
  Setup.chk("R: Not yet: she stays, the letter's answered, she can still be returned", v.byId(n.id) != null && !Transmissions.canReply(find(RepairJob.READY)) && RepairJob.ready(v, n));
  int pay = RepairJob.payment(v, false), before = v.storageScrap();
  Setup.chk("R: the payment is the repair cost and the bonus (" + pay + ")", pay >= 200 + 4 * 10);
  int paid = RepairJob.returnHer(v, n);
  Transmissions.Message m = find(RepairJob.PAID);
  Setup.chk("R: returned: paid into the Cargo Hold, gone from the fleet, fate RETURNED", paid == pay && v.storageScrap() == before + pay && v.byId(n.id) == null
    && new String(SafeFiles.read(new File(new File(v.historyDir(), n.id), "fate.txt")), "UTF-8").startsWith("RETURNED"));
  Setup.chk("R: She's home, with the sum paid", m != null && m.body.contains(pay + " scrap"));
  ChainT.jump(v, 250); Transmissions.check();
  Setup.chk("R: returned: no demand ever comes", find(RepairJob.OVERDUE_LETTER) == null);
 }

 /** Send her home by reply: refused while she's damaged (try again later), then from aboard her: she leaves, no ship boarded. */
 static void sentByReply() throws Exception {
  Vault v = fleet(true);
  Ship n = RepairJob.ship(v);
  v.salvage(n); mend(v, n); Transmissions.check();
  Transmissions.Message ready = find(RepairJob.READY);
  SavedGameState g = v.readCopy(n).save; g.getPlayerShip().setHullAmt(g.getPlayerShip().getHullAmt() - 3); v.write(n, g);
  boolean refused = false; try { Transmissions.reply(ready, 0); } catch (IOException e) { refused = e.getMessage().contains("damaged since"); }
  Setup.chk("Y: damaged since the letter: Send her home is refused, with the reason; the letter still answerable", refused && v.byId(n.id) != null && Transmissions.canReply(find(RepairJob.READY)));
  mend(v, n); v.dock(); v.board(n);
  int pay = RepairJob.payment(v, false), before = v.storageScrap();
  Transmissions.reply(find(RepairJob.READY), 0);
  Setup.chk("Y: mended, aboard her: Send her home returns her and pays; no ship boarded", v.byId(n.id) == null && v.boarded() == null && v.storageScrap() == before + pay
    && !v.continueFile().exists());
  Transmissions.Message m = find(RepairJob.PAID);
  Setup.chk("Y: She's home, with the sum paid", m != null && m.body.contains(pay + " scrap"));
 }

 /** Defied; the Cargo Hold holds her value; she's docked: her value and she are taken. */
 static void seizedDocked() throws Exception {
  Vault v = fleet(true);
  Ship n = RepairJob.ship(v);
  v.salvage(n);
  ChainT.jump(v, 199); Transmissions.check();
  Setup.chk("S: 199 beacons on: no demand yet", find(RepairJob.OVERDUE_LETTER) == null);
  ChainT.jump(v, 1); Transmissions.check();
  Transmissions.Message d = find(RepairJob.OVERDUE_LETTER);
  Setup.chk("S: 200 beacons: Where is my ship?, two replies", d != null && Transmissions.replyTexts(d).size() == 2);
  int value = Integer.parseInt(RepairJob.fills(v).get("value"));
  GuiT.hold(v, value + 50);
  Transmissions.reply(d, 1);
  ChainT.jump(v, 4); Transmissions.check();
  Setup.chk("S: defied: Very well", find(RepairJob.DEFIED) != null && find(RepairJob.SEIZED) == null);
  ChainT.jump(v, 2); Transmissions.check();
  Setup.chk("S: 6 beacons on: the claims office hasn't come yet (7-14)", find(RepairJob.SEIZED) == null && v.byId(n.id) != null);
  ChainT.jump(v, 8); Transmissions.check();
  Transmissions.Message s = find(RepairJob.SEIZED);
  Setup.chk("S: 14 beacons on: Notice of recovery", s != null);
  Setup.chk("S: her value from the Cargo Hold (" + value + "), and she's taken: fate SEIZED", v.storageScrap() == 50 && v.byId(n.id) == null
    && new String(SafeFiles.read(new File(new File(v.historyDir(), n.id), "fate.txt")), "UTF-8").startsWith("SEIZED"));
  Setup.chk("S: the notice says what was taken", s.body.contains(value + " scrap from the Cargo Hold") && s.body.contains("the Nightjar herself"));
 }

 /** Sent back late, unfinished: nothing paid. Boarded, she can't be sent. */
 static void lateAndBoarded() throws Exception {
  Vault v = fleet(true);
  Ship n = RepairJob.ship(v), run = v.boarded();
  ChainT.jump(v, 200); Transmissions.check();
  Transmissions.Message d = find(RepairJob.OVERDUE_LETTER);
  v.salvage(n); v.dock(); v.board(n);
  int before = v.storageScrap();
  Transmissions.reply(d, 0);
  Setup.chk("L: sent back unfinished from aboard her: she leaves, nothing paid, no ship boarded", v.byId(n.id) == null && v.storageScrap() == before && v.boarded() == null);
  Setup.chk("L: no station letter once her owner had to ask", find(RepairJob.READY) == null);
  v.board(run);
  ChainT.jump(v, 2); ChainT.jump(v, 2); Transmissions.check(); // the first jump after boarding again sets where she starts counting from
  Transmissions.Message r = find(RepairJob.LATE);
  Setup.chk("L: Received: she came back unfinished", r != null && r.body.contains("unfinished"));
  ChainT.jump(v, 20); Transmissions.check();
  Setup.chk("L: no claims office", find(RepairJob.SEIZED) == null);
 }

 /** Defied with her in the Junkyard and nothing to pay with: the Cargo Hold is emptied (its crew stay), she isn't found; salvaged, the foreman writes. */
 static void hidden() throws Exception {
  Vault v = fleet(true);
  Ship n = RepairJob.ship(v);
  for (Ship s : v.docked()) v.remove(s, null); // nothing else docked
  GuiT.hold(v, 3);
  Vault.Copy c = v.readCopy(v.storage()); c.save.getPlayerShip().setFuelAmt(7); v.begin().put(v.storage(), c.save, c.hash).commit();
  ChainT.jump(v, 200); Transmissions.check();
  Transmissions.reply(find(RepairJob.OVERDUE_LETTER), 1);
  ChainT.jump(v, 14); Transmissions.check();
  Transmissions.Message s = find(RepairJob.SEIZED);
  ShipState hold = v.readCopy(v.storage()).save.getPlayerShip();
  Setup.chk("H: nothing docked to take: everything in the Cargo Hold", s != null && hold.getScrapAmt() == 0 && hold.getFuelAmt() == 0 && s.body.contains("everything in the Cargo Hold (3 scrap, 7 fuel"));
  Setup.chk("H: in the Junkyard, she isn't found", v.byId(n.id) != null && n.state == Ship.State.JUNKED && !s.body.contains("herself"));
  Transmissions.check();
  Setup.chk("H: no foreman while she stays hidden", find(RepairJob.HIDDEN) == null);
  v.salvage(n); Transmissions.check();
  Transmissions.Message h = find(RepairJob.HIDDEN);
  Setup.chk("H: salvaged: the foreman knows", h != null && h.body.contains("I know what you did, hiding the Nightjar in my yard. Sneaky, but smart."));
 }

 /** Defied, too little scrap, another ship docked: she's taken in its place. */
 static void anotherShip() throws Exception {
  Vault v = fleet(true);
  GuiT.hold(v, 0);
  int docked = v.docked().size();
  ChainT.jump(v, 200); Transmissions.check();
  Transmissions.reply(find(RepairJob.OVERDUE_LETTER), 1);
  ChainT.jump(v, 14); Transmissions.check();
  Transmissions.Message s = find(RepairJob.SEIZED);
  Setup.chk("O: too little scrap: a docked ship taken in its place, the boarded one kept", s != null && s.body.contains(", in its place") && v.docked().size() == docked - 1 && v.boarded() != null);
 }
}
