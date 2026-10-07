import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*; import homeplanet.comm.*;
/** A Long Range trade that fails after its first ship was received (5.61): calling it off sends her back, since the other station keeps her, so no ship is in both fleets. args: gamedir, world saves (from WorldT), work */
public class CallOffT {
 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive(); HomePlanet.immersiveNotifications = false;
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  Ship donor = null;
  for (Ship s : v.docked()) if (donor == null && s.save() != null && !ShipPapers.custom(s.save().getPlayerShipBlueprintId())) donor = s;
  Setup.chk("T: a docked ship of FTL's own to stand for the other station's", donor != null);
  String bp = donor.save().getPlayerShipBlueprintId();
  byte[] pkg = v.packageOf(donor); // her package, as the other station sends one
  int before = v.fleet().size();
  List<Line> in = new ArrayList<Line>();
  in.add(Line.ship(1, bp, "Arrival One", ""));
  in.add(Line.ship(2, bp, "Arrival Two", ""));
  Exchange.Record r = Exchange.escrow("calloff-test", true, "peer-station", "Commander Bree", new ArrayList<Line>(), in);
  Exchange.keepIncoming(r, 1, pkg); // the second ship's papers never arrive
  String why = null;
  try { Exchange.complete(r); } catch (IOException e) { why = e.getMessage(); }
  v.takeStock();
  Ship came = null;
  for (Ship s : v.fleet()) { TradeMark m = TradeMark.of(v, s.id); if (m != null && m.trade.equals("calloff-test#1")) came = s; }
  Setup.chk("T: completing fails on the second ship's papers, after the first was received (" + why + ")", why != null && why.contains("papers") && came != null && v.fleet().size() == before + 1);
  Exchange.callOff(r, "this station could not take delivery: " + why);
  v.takeStock();
  boolean still = false; for (Ship s : v.fleet()) if (s.id.equals(came.id)) still = true;
  String fate = "";
  File ff = new File(new File(v.historyDir(), came.id), "fate.txt");
  if (ff.isFile()) fate = new String(SafeFiles.read(ff), "UTF-8").split("\n")[0].trim();
  Setup.chk("T: called off: the ship received for it goes back (the other station keeps her), the fleet as it was (" + v.fleet().size() + ")", !still && v.fleet().size() == before);
  Setup.chk("T: her record says she went to the other fleet (" + fate + ")", fate.equals("TRANSFERRED"));
  String hist = new String(SafeFiles.read(v.historyLog()), "UTF-8");
  Setup.chk("T: the station log says so", hist.contains("SENT BACK") && hist.contains("sent back (received before it was called off)"));
  Setup.done();
 }
}
