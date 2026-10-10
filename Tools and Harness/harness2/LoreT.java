import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The words folder (5.89; the letters, expedition words and deeds in it, 5.991): the jar's words keep the rules, every station kind has words, a copy in lore/ wins entry by entry (letter by letter, key by key), and a broken or rule-breaking one falls back to the station's own words. args: gamedir, world saves (from WorldT), work */
public class LoreT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 File lore = new File(work, "lore"); System.setProperty("homeplanet.loreDir", lore.getAbsolutePath());
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves);
 // A: the jar's own words keep every rule, and lore/ is prepared
 List<String> bad = new ArrayList<String>();
 for (String f : Lore.FILES) for (Lore.Entry e : Lore.entries(f)) if (Lore.broken(e) != null) bad.add(f + " " + e.kind + ": " + Lore.broken(e));
 for (String l : Transmissions.allLetters()) if (Lore.rule(l) != null) bad.add("letter " + l.substring(0, l.indexOf(':')) + ": " + Lore.rule(l));
 for (String l : Assignments.allWords()) if (Lore.rule(l) != null) bad.add("expedition \"" + l + "\": " + Lore.rule(l));
 Setup.chk("A: the station's own words keep the hard and voice rules: the log, the letters, the expedition words, the deeds " + bad, bad.isEmpty() && !Lore.entries(Lore.STATION_LOG).isEmpty()
   && Transmissions.allLetters().size() > 100 && Assignments.allWords().size() > 400 && Accolades.words("ship").length == 2 && Accolades.deed("ACH_SECTOR_5") != null);
 // every station kind docs/EVENTS.md lists has its words in the station log's file
 File repo = work.getAbsoluteFile().getParentFile().getParentFile().getParentFile().getParentFile();
 String doc = new String(SafeFiles.read(new File(repo, "docs/EVENTS.md")), "UTF-8");
 doc = doc.substring(doc.indexOf("## The station log"), doc.indexOf("### Old station logs only"));
 Set<String> kinds = new TreeSet<String>(), worded = new TreeSet<String>();
 for (String row : doc.split("\n")) if (row.startsWith("| `")) { java.util.regex.Matcher k = java.util.regex.Pattern.compile("`([A-Z][A-Z0-9_]+)`").matcher(row.split("\\|")[1]); while (k.find()) kinds.add(k.group(1)); }
 for (Lore.Entry e : Lore.entries(Lore.STATION_LOG)) worded.add(e.kind);
 Set<String> unworded = new TreeSet<String>(kinds); unworded.removeAll(worded);
 Setup.chk("A: every station kind has words (" + kinds.size() + " kinds)" + (unworded.isEmpty() ? "" : ": none for " + unworded), unworded.isEmpty() && kinds.size() > 60);
 // the words' own rules: a field there or not, every value of a repeated one, "the" before a ship's name by the station's rule, an old line read in kept
 Setup.chk("A: a trade with a ship, and one with the Cargo Hold (no partner_id), read apart",
   Lore.human(Event.of("TRADE").put("what", "cargo_bay").put("ship_name", "Kestrel").put("partner_name", "Red-Tail").put("partner_id", "2")).equals("Crew and cargo were moved between the Kestrel and the Red-Tail in the Cargo Bay.")
   && Lore.human(Event.of("TRADE").put("what", "cargo_bay").put("ship_name", "Kestrel").put("partner_name", "Spacedock Storage")).equals("Crew and cargo were moved between the Kestrel and the Cargo Hold in the Cargo Bay."));
 Setup.chk("A: {crew+} names every one of them",
   Lore.human(Event.of("EXPEDITION").put("what", "sent").put("sector", "Nebula").put("sector_id", "nebula").put("crew", "Ash").put("crew", "Bob").put("crew", "Cy")).equals("Ash, Bob and Cy set out on an expedition to a Nebula."));
 Setup.chk("A: the Kestrel, but The Adjudicator as she is, starting a sentence or not",
   Lore.human(Event.of("DOCK").put("ship_name", "Kestrel")).equals("The Kestrel came back to the Space Dock.") && Lore.human(Event.of("DOCK").put("ship_name", "The Adjudicator")).equals("The Adjudicator came back to the Space Dock.")
   && Lore.human(Event.of("CREW").put("what", "assigned").put("crew", "Bob").put("to", "ship").put("ship_name", "The Adjudicator")).equals("Bob was assigned to The Adjudicator."));
 Setup.chk("A: an old log's entry read in, and a received ship's, keep the words they came with",
   Lore.human(Event.of("DOCK").put("ship_name", "Kestrel").put("converted", true).human("Kestrel  continue.sav -> ships/a.sav")).equals("Kestrel  continue.sav -> ships/a.sav")
   && Lore.human(Event.of("DOCK").put("ship_name", "Kestrel").put("received_from", "Vance").human("her own words")).equals("her own words"));
 Lore.prepare();
 Setup.chk("A: lore/ beside the program: a readme, and the defaults to copy from", new File(lore, "readme.txt").isFile() && new File(lore, "defaults/logs/station-log.xml").isFile()
   && new File(lore, "defaults/letters.xml").isFile() && new File(lore, "defaults/expeditions.xml").isFile() && new File(lore, "defaults/deeds.xml").isFile());
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
 Setup.chk("B: a sentence may start with Rebels; a title mid-sentence may not (6.41)", Lore.rule("Rebel guards patrolled the dock.") == null && Lore.rule("They ran. Rebels followed.") == null
   && Lore.rule("The Rebels fled.") != null && Lore.rule("They fought the Rebellion.") != null);
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
 // E: a copy of the letters wins letter by letter; one with a {token} the letter hasn't got, or breaking a rule, is left out
 Map<String, String> fills = new HashMap<String, String>(); fills.put("ship", "The Adjudicator"); fills.put("rank", "Captain"); fills.put("value", "300"); fills.put("worth", "her worth");
 String ownMuseum = Transmissions.text("museum", fills)[2], ownReward = Transmissions.text("reward", fills)[2];
 Setup.chk("E: the station's own museum letter, \"the {ship}\" by the rule", ownMuseum.contains("Recovery teams reached The Adjudicator in time.") && ownMuseum.contains("the Federation Museum"));
 File letters = new File(lore, Lore.LETTERS);
 SafeFiles.writeText(letters, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<letters>\n"
   + "\t<letter key=\"museum\" from=\"Federation Fleet Command\" subject=\"Into the Museum\"><![CDATA[\n{rank},\n\nThe {ship} is in the Museum now: {value} scrap.\n<u>Approved</u>.\n]]></letter>\n"
   + "\t<letter key=\"reward\" from=\"Federation Fleet Command\" subject=\"A reward\"><![CDATA[\n{rank}, {no_such_token}.\n]]></letter>\n"
   + "\t<letter key=\"rescue\" from=\"Federation Fleet Command\" subject=\"Rescued\"><![CDATA[\nThe Rebel Flagship was destroyed.\n]]></letter>\n"
   + "</letters>\n", false);
 String[] museum = Transmissions.text("museum", fills);
 Setup.chk("E: the copy's letter wins, its tokens filled and its text kept as written", museum[2].equals("Captain,\n\nThe Adjudicator is in the Museum now: 300 scrap.\n<u>Approved</u>."));
 Setup.chk("E: a {token} the letter hasn't got, or the Flagship destroyed: the station's own letter", Transmissions.text("reward", fills)[2].equals(ownReward)
   && !Transmissions.text("rescue", fills)[2].contains("destroyed") && Lore.problems().toString().contains("letter reward: {no_such_token}") && Lore.problems().toString().contains("letter rescue: hard rule 1"));
 // F: a copy of the expedition words takes a key's place; a line with a {token} its key doesn't use is left out
 List<String> jarZoltan = new ArrayList<String>(); for (String l : Assignments.allWords()) jarZoltan.add(l);
 SafeFiles.writeText(new File(lore, Lore.EXPEDITIONS), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<expeditions>\n"
   + "\t<line kind=\"offer\" sector=\"zoltan\">A Zoltan monastery wants its bells polished.</line>\n"
   + "\t<line kind=\"band\" job=\"defend\" band=\"died\">fell holding the line for {client}</line>\n"
   + "\t<line kind=\"band\" job=\"defend\" band=\"died\">fell holding the line, and {his} name is on the wall</line>\n"
   + "</expeditions>\n", false);
 List<String> all = Assignments.allWords();
 Setup.chk("F: the copy's lines take their key's place, a bad one left out, every other key the station's own",
   all.contains("A Zoltan monastery wants its bells polished.") && all.contains("fell holding the line, and {his} name is on the wall") && !all.contains("fell holding the line for {client}")
   && all.size() < jarZoltan.size() && Assignments.missingWords().isEmpty() && Lore.problems().toString().contains("band defend died: {client}"));
 // G: a copy of the deeds: its phrases take an accolade kind's place, its deed an achievement's
 SafeFiles.writeText(new File(lore, Lore.DEEDS), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<lore>\n"
   + "\t<entry kind=\"ACCOLADE\" when=\"of=ship\">{ship}, {n} jumps without a scratch.</entry>\n"
   + "\t<entry kind=\"DEED\" when=\"achievement=ACH_SECTOR_5\">Sector 5, the hard way.</entry>\n"
   + "\t<entry kind=\"DEED\" when=\"achievement=ACH_SECTOR_8\">Sector 8 for {nobody}.</entry>\n"
   + "</lore>\n", false);
 Setup.chk("G: the copy's accolade and deed win; a {token} an accolade hasn't got is left out", Arrays.asList(Accolades.words("ship")).equals(Arrays.asList("{ship}, {n} jumps without a scratch."))
   && Accolades.words("fights").length == 2 && "Sector 5, the hard way.".equals(Accolades.deed("ACH_SECTOR_5")) && Accolades.deed("ACH_SECTOR_8").startsWith("One of your ships"));
 Setup.done();
}}
