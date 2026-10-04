import java.io.*; import java.nio.charset.StandardCharsets; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/**
 * The Third Fleet Commander (heromedel's chain), one path a run (a fresh fleet each: the letters go once).
 * args: gamedir, world saves (from WorldT), work, path: yes (Interested, the part, the parts word), no (Not interested,
 * a part bought first: no parts word), early (a derelict bought before his first word), off (the inbox off).
 */
public class ThirdT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 String path = a[3];
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 HomePlanet.immersiveNotifications = !"off".equals(path);
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 if (v.boarded() == null) v.board(v.docked().get(0));
 v.takeStock();

 if ("early".equals(path)) {
  Ship s = buyDerelict(v);
  Transmissions.check();
  Setup.chk("E: a derelict bought before his first word: no first word, straight to her part", find(ThirdFleet.HELLO) == null && find(ThirdFleet.PART) != null);
  part(v, s, find(ThirdFleet.PART));
  Setup.done(); return;
 }

 Transmissions.check(); ThirdFleet.due(v); // the Space Dock's look (with the inbox off, only that starts his clock)
 int due = Integer.parseInt(v.event("fleet3-due")), now = v.beaconsSeen();
 Setup.chk("H: his first word is due 7 to 21 days on (" + (due - now) + ")", due - now >= ThirdFleet.DUE_MIN && due - now <= ThirdFleet.DUE_MAX);
 Setup.chk("H: nothing before it's due", find(ThirdFleet.HELLO) == null);
 while (v.beaconsSeen() < due) v.countBeacon();

 if ("off".equals(path)) {
  Transmissions.check();
  Setup.chk("O: the inbox off: nothing in the inbox, and his first word waits for the Space Dock's pop-up", find(ThirdFleet.HELLO) == null && ThirdFleet.due(v).contains(ThirdFleet.HELLO));
  ThirdFleet.markSent(v, ThirdFleet.HELLO);
  Setup.chk("O: once shown, it isn't due again", !ThirdFleet.due(v).contains(ThirdFleet.HELLO));
  Setup.done(); return;
 }

 Transmissions.check();
 Transmissions.Message hello = find(ThirdFleet.HELLO);
 String cls = DataManager.get().getShip(v.readCopy(v.boarded()).save.getPlayerShip().getShipBlueprintId()).getShipClass().getTextValue();
 Setup.chk("H: his first word, naming the boarded ship's class (" + cls + "), with two replies", hello != null && hello.body.contains("flying that " + cls + ".") && Transmissions.replyTexts(hello).size() == 2 && !hello.body.contains("{"));
 Transmissions.check();
 int n = 0; for (Transmissions.Message m : Transmissions.load()) if (m.key.equals(ThirdFleet.HELLO)) n++;
 Setup.chk("H: it comes once", n == 1);

 boolean yes = "yes".equals(path);
 Transmissions.reply(hello, yes ? 1 : 0);
 v.countBeacon(); v.countBeacon(); Transmissions.check();
 Setup.chk("R: his answer to " + (yes ? "Interested" : "Not interested"), find(yes ? ThirdFleet.YES : ThirdFleet.NO) != null && find(yes ? ThirdFleet.NO : ThirdFleet.YES) == null);
 Setup.chk("R: no part yet: no derelict bought", find(ThirdFleet.PART) == null && find(ThirdFleet.PART_NO) == null);

 if (!yes) { // a part bought from the Junkyard first: the parts word will never be needed
  Parts.Listing pl = null; for (Parts.Listing l : Parts.current(v)) if (!l.salvage()) { pl = l; break; }
  if (pl != null) { if (v.storageScrap() < pl.price) v.depositToStorage(pl.price - v.storageScrap()); Parts.buy(v, pl); }
  Setup.chk("R: a part bought from the Junkyard (" + (pl != null) + ")", pl != null);
 }
 ThirdFleet.partInstalled(v); // before his part: doesn't count
 Ship s = buyDerelict(v);
 Transmissions.check();
 String key = yes ? ThirdFleet.PART : ThirdFleet.PART_NO;
 Transmissions.Message pm = find(key);
 Setup.chk("P: the derelict bought: " + key + (yes ? "" : ", \"even though you said you didn't want to\""), pm != null && find(yes ? ThirdFleet.PART_NO : ThirdFleet.PART) == null
   && (yes || pm.body.contains("even though you said you didn't want to")));
 part(v, s, pm);
 Setup.chk("P: no parts word before a part is installed", find(ThirdFleet.PARTS) == null);
 ThirdFleet.partInstalled(v); // the Cargo Bay's Save, with an install in it
 Transmissions.check();
 Setup.chk(yes ? "W: a part installed, none bought from the Junkyard: his word about the Junkyard's parts" : "W: a part installed, but one was bought from the Junkyard already: no parts word",
   yes ? find(ThirdFleet.PARTS) != null && find(ThirdFleet.PARTS).body.contains("cheap parts at the junkyard too") : find(ThirdFleet.PARTS) == null);
 Transmissions.check();
 int parts = 0; for (Transmissions.Message m : Transmissions.load()) if (m.key.startsWith("fleet3:part")) parts++;
 Setup.chk("W: each letter once (" + parts + ")", parts == (yes ? 2 : 1));
 Setup.done();
}
 static Transmissions.Message find(String key) { for (Transmissions.Message m : Transmissions.load()) if (m.key.equals(key)) return m; return null; }
 static Ship buyDerelict(Vault v) throws Exception {
  Derelicts.Listing pick = Derelicts.current(v).get(0);
  if (v.storageScrap() < pick.price) v.depositToStorage(pick.price - v.storageScrap());
  return Derelicts.buy(v, pick);
 }
 /** His part: in the stored systems, in working order, one her rooms allow and she lacked (the ones she can't fly without first), named in the letter. */
 static void part(Vault v, Ship s, Transmissions.Message letter) throws Exception {
  ShipState her = v.readCopy(s).save.getPlayerShip();
  List<String> lines = java.nio.file.Files.readAllLines(v.systemsFile().toPath(), StandardCharsets.UTF_8);
  String last = lines.get(lines.size() - 1).trim(); String[] w = last.split("\\s+");
  SystemType t = SystemType.findById(w[0]);
  boolean room = DataManager.get().getShip(her.getShipBlueprintId()).getSystemList().getSystemRoom(t) != null;
  boolean lacked = her.getSystem(t) == null || her.getSystem(t).getCapacity() <= 0;
  boolean firstOrder = true; // if she lacked one she can't fly without, that's the one
  for (SystemType core : new SystemType[] {SystemType.PILOT, SystemType.ENGINES, SystemType.OXYGEN}) {
   if (core == t) break;
   boolean r = DataManager.get().getShip(her.getShipBlueprintId()).getSystemList().getSystemRoom(core) != null;
   if (r && (her.getSystem(core) == null || her.getSystem(core).getCapacity() <= 0)) firstOrder = false;
  }
  String title = homeplanet.model.Items.systemTitle(t.getId());
  Setup.chk("P: his part (" + last + "): in the stored systems at level 1, unbroken, a system her rooms allow and she lacked, the core ones first, named in the letter",
    w.length == 2 && "1".equals(w[1]) && room && lacked && firstOrder && letter.body.contains("she was missing a" + ("aeiouAEIOU".indexOf(title.charAt(0)) >= 0 ? "n " : " ") + title + " system.") && !letter.body.contains("{"));
 }
}
