import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.vault.*;
/** The words folder (5.89): the jar's words keep the rules, a copy in lore/ wins entry by entry, and a broken or rule-breaking entry falls back to the station's own words. args: gamedir, world saves (from WorldT), work */
public class LoreT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 File lore = new File(work, "lore"); System.setProperty("homeplanet.loreDir", lore.getAbsolutePath());
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves);
 // A: the jar's own words keep every rule, and lore/ is prepared
 List<String> bad = new ArrayList<String>();
 for (String f : Lore.FILES) for (Lore.Entry e : Lore.entries(f)) if (Lore.broken(e) != null) bad.add(f + " " + e.kind + ": " + Lore.broken(e));
 Setup.chk("A: the station's own words keep the hard and voice rules " + bad, bad.isEmpty() && !Lore.entries(Lore.STATION_LOG).isEmpty());
 Lore.prepare();
 Setup.chk("A: lore/ beside the program: a readme, and the defaults to copy from", new File(lore, "readme.txt").isFile() && new File(lore, "defaults/logs/station-log.xml").isFile());
 Event hold = Event.of("HOLD_FILE").put("what", "converted").put("scrap", 12).human("the writer's words");
 Setup.chk("A: an event with an entry gets the lore's words; one without keeps its writer's", Lore.human(hold).equals("The Cargo Hold's inventory was written up in a new ledger.")
   && Lore.human(Event.of("NO_SUCH_KIND").human("its own")).equals("its own"));
 // B: a copy wins entry by entry, with tokens, conditions, and its broken entries left out
 File copy = new File(lore, "logs/station-log.xml"); copy.getParentFile().mkdirs();
 SafeFiles.writeText(copy, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<lore>\n"
   + "\t<entry kind=\"HOLD_FILE\" when=\"what=converted\">{scrap} scrap counted into the new ledger.</entry>\n"
   + "\t<entry kind=\"TEST_MISSING\">{no_such_field} went missing.</entry>\n"
   + "\t<entry kind=\"TEST_FLAG\">The Rebel Flagship was destroyed at last.</entry>\n"
   + "\t<entry kind=\"TEST_BEACONS\">She came home 3 beacons later.</entry>\n"
   + "\t<entry kind=\"TEST_REBELS\">The Rebels fled.</entry>\n"
   + "\t<entry kind=\"TEST_GOOD\">{name} came aboard at a beacon with a store, and the rebels fled from the Rebel Flagship's path.</entry>\n"
   + "</lore>\n", false);
 Setup.chk("B: the copy's entry wins, its {scrap} filled", Lore.human(hold).equals("12 scrap counted into the new ledger."));
 Setup.chk("B: its condition is kept: another what is no entry's (the jar's is for what=converted too): the writer's own words", Lore.human(Event.of("HOLD_FILE").put("what", "other").human("x")).equals("x"));
 Setup.chk("B: a {field} the event hasn't got: the writer's words, never a raw token", Lore.human(Event.of("TEST_MISSING").human("fallback")).equals("fallback"));
 Setup.chk("B: hard rule 1, the Flagship destroyed: left out", Lore.human(Event.of("TEST_FLAG").human("fallback")).equals("fallback"));
 Setup.chk("B: hard rule 2, time in beacons: left out", Lore.human(Event.of("TEST_BEACONS").human("fallback")).equals("fallback"));
 Setup.chk("B: the rebels capitalised: left out", Lore.human(Event.of("TEST_REBELS").human("fallback")).equals("fallback"));
 Setup.chk("B: a beacon as a place, the rebels in lower case and the Rebel Flagship by name are fine", Lore.human(Event.of("TEST_GOOD").put("name", "Bob").human("fallback")).startsWith("Bob came aboard"));
 String probs = Lore.check().toString();
 Setup.chk("B: the check names the file, the line and the rule (" + probs + ")", probs.contains("lore/logs/station-log.xml, line 5 (TEST_FLAG): hard rule 1") && probs.contains("line 6 (TEST_BEACONS): hard rule 2") && probs.contains("(TEST_REBELS): voice"));
 // C: the event log writes the copy's words
 EventLog.write(v, Event.of("HOLD_FILE").put("what", "converted").put("scrap", 7).human("the writer's words"));
 String text = new String(SafeFiles.read(EventLog.file(v)), "UTF-8");
 Setup.chk("C: the event log's human line comes from the copy", text.contains("\n7 scrap counted into the new ledger."));
 // D: a copy that's broken XML: the station's own words, and it says so
 Thread.sleep(1100);
 SafeFiles.writeText(copy, "<lore><entry kind=\"HOLD_FILE\">unclosed", false);
 Setup.chk("D: broken XML: the station's own words stand", Lore.human(hold).equals("The Cargo Hold's inventory was written up in a new ledger.") && Lore.problems().toString().contains("could not be read"));
 Setup.done();
}}
