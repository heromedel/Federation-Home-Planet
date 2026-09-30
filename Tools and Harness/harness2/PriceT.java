import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.SavedGameState; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Prices from FTL's blueprints (HR1, HR2) and paying from the storage hold. args: gamedir, world saves (from WorldT), work */
public class PriceT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 prices();
 paying(v);
 Setup.done();
}
 static void prices() throws Exception {
  net.blerf.ftl.xml.SystemBlueprint sh = DataManager.get().getSystem("shields");
  Setup.chk("P: a system at level 1 is its price", Pricing.system("shields", 1) == sh.getCost());
  Setup.chk("P: each level adds its upgrade cost", Pricing.system("shields", 3) == sh.getCost() + sh.getUpgradeCosts().get(0) + sh.getUpgradeCosts().get(1));
  Setup.chk("P: HR1 sells for half", Pricing.systemSale("shields", 3) == Pricing.system("shields", 3) / 2);
  Setup.chk("P: a Clone Bay (no level) sells as level 1", Pricing.systemSale("clonebay", 0) == Pricing.system("clonebay", 1) / 2);
  Setup.chk("P: reactor bars: 30 each to 5, then 35", Pricing.reactor(5) == 150 && Pricing.reactor(8) == 255 && Pricing.reactorBar(11) == 40);
  Setup.chk("P: an unknown system has the flat price", Pricing.system("no_such_system", 2) == Pricing.UNPRICED_SYSTEM);
  SavedGameState k = Commission.build("PLAYER_SHIP_HARD", "Price Kestrel", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(1));
  Pricing.Quote full = Pricing.ship(k, 0, 0, 100), half = Pricing.ship(k, 0, 0, 50), custom = Pricing.ship(k, 5, 4, 100);
  System.out.println("Kestrel A: " + full.total() + " " + full.lines);
  Setup.chk("P: a Kestrel A costs about 1000", full.total() > 900 && full.total() < 1100);
  Setup.chk("P: the multiplier scales the total", half.total() == (full.subtotal * 50 + 50) / 100);
  Setup.chk("P: a custom hull adds rooms and doors", custom.total() == full.total() + 5 * Pricing.PER_ROOM + 4 * Pricing.PER_DOOR);
 }
 static void paying(Vault v) throws Exception {
  Ship st = v.storage();
  SavedGameState g = v.readCopy(st).save; g.getPlayerShip().setScrapAmt(300); v.write(st, g);
  Setup.chk("S: the hold's scrap reads back", v.storageScrap() == 300);
  String hash = SafeFiles.hash(st.file());
  boolean refused = false; try { v.payFromStorage(301); } catch (IOException e) { refused = e.getMessage().contains("300"); }
  Setup.chk("S: paying more than the hold has is refused, saying what it has", refused && SafeFiles.hash(st.file()).equals(hash));
  byte[] before = v.payFromStorage(120);
  Setup.chk("S: paying takes the scrap", v.storageScrap() == 180);
  v.refundStorage(before);
  Setup.chk("S: a refund puts it back", v.storageScrap() == 300);
 }
}
