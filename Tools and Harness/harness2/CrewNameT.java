import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*;
/**
 * Crew named by race (6.33, heromedel and McCarthy; docs/NAMES.md): the station's picks agree with NamesT's reading,
 * come only from the race's list in the right sex, rarer levels rarer; Commission and volunteers use them; Human Name
 * Gen on the lists and on FTL's names; the lists off as before; a player's copy in lore/names, and a broken one;
 * the looks of crew already on record unchanged. args: gamedir, world saves, work
 */
public class CrewNameT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work); work.mkdirs();
 File lore = new File(work, "lore"); System.setProperty("homeplanet.loreDir", lore.getPath());
 HomePlanet.propFile = new File(work, "test.cfg");
 HomePlanet.config.remove(CrewNames.CFG_LISTS); HomePlanet.config.remove(CrewNames.CFG_HUMAN); HomePlanet.config.remove(StationConsole.GIRL_POWER);
 Setup.open(game, Setup.world(game, new File(work, "w")));
 Setup.chk("S: with no cfg lines, the lists are off (vanilla, 6.34) and humans Normal", !CrewNames.listsOn() && CrewNames.NORMAL.equals(CrewNames.humanGen()));
 HomePlanet.config.setProperty(CrewNames.CFG_LISTS, "true"); // on for the checks below, until O

 String[][] races = {{"human", "human"}, {"energy", "zoltan"}, {"engi", "engi"}, {"mantis", "mantis"}, {"rock", "rock"}, {"crystal", "crystal"}, {"anaerobic", "lanius"}, {"slug", "slug"}};
 for (String[] r : races) {
  // the reference: NamesT's reading of the jar's file
  InputStream in = CrewNameT.class.getResourceAsStream("/homeplanet/resource/lore/names/" + r[1] + ".xml");
  org.w3c.dom.Element root = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in).getDocumentElement();
  List<NamesT.List_> ls = new ArrayList<NamesT.List_>();
  if (r[1].equals("human")) { ls.add(NamesT.read("human", NamesT.child(root, "first"))); ls.add(NamesT.read("human", NamesT.child(root, "last"))); }
  else ls.add(NamesT.read(r[1], root));
  Set<String> ref = new HashSet<String>(); for (NamesT.List_ l : ls) ref.addAll(NamesT.every(l));
  Setup.chk("A: " + r[1] + ": the station's names and female forms are NamesT's (" + ref.size() + ")", ref.equals(new HashSet<String>(CrewNames.every("names/" + r[1] + ".xml"))));
  // men and women, a thousand each: only their sex's names, women's B names in a female form
  NamesT.List_ l0 = ls.get(0);
  Set<String> men = new HashSet<String>(), women = new HashSet<String>();
  for (NamesT.Name n : l0.names) { if (!n.sex.equals("F")) men.add(NamesT.shown(l0, n)); women.addAll(NamesT.female(l0, n)); }
  Random rng = new Random(33); List<String> wrong = new ArrayList<String>();
  Map<String, Integer> byLevel = new HashMap<String, Integer>(); Map<String, String> levelOf = new HashMap<String, String>();
  for (NamesT.Name n : l0.names) levelOf.put(NamesT.shown(l0, n), n.rarity == null ? l0.levels.keySet().iterator().next() : n.rarity);
  HomePlanet.config.setProperty(CrewNames.CFG_HUMAN, CrewNames.FIRST);
  for (int i = 0; i < 2000; i++) {
   boolean male = i % 2 == 0; String n = CrewNames.pick(r[0], male, rng);
   if (!(male ? men : women).contains(n)) wrong.add((male ? "M " : "F ") + n);
   if (male) { String lv = levelOf.get(n); if (lv != null) byLevel.put(lv, (byLevel.containsKey(lv) ? byLevel.get(lv) : 0) + 1); }
  }
  HomePlanet.config.remove(CrewNames.CFG_HUMAN);
  Setup.chk("B: " + r[1] + ": 2000 picks, each from the list in the right sex" + (wrong.isEmpty() ? "" : " " + wrong.subList(0, Math.min(5, wrong.size()))), wrong.isEmpty());
  // rarer is rarer: per name, the first level comes up more than the last
  List<String> lvls = new ArrayList<String>(l0.levels.keySet());
  String top = lvls.get(0), bottom = null; for (int i = lvls.size() - 1; i > 0 && bottom == null; i--) if (count(l0, lvls.get(i)) > 0) bottom = lvls.get(i);
  if (bottom != null) {
   double perTop = get(byLevel, top) / (double) count(l0, top), perBottom = get(byLevel, bottom) / (double) count(l0, bottom);
   Setup.chk("B: " + r[1] + ": a " + top + " name comes up more often than a " + bottom + " one (" + String.format("%.1f", perTop) + " to " + String.format("%.1f", perBottom) + " each)", perTop > perBottom);
  }
 }

 // Human Name Gen on the lists
 Random rng = new Random(5); int two = 0, n = 400;
 HomePlanet.config.setProperty(CrewNames.CFG_HUMAN, CrewNames.FIRST); boolean firstOnly = true; for (int i = 0; i < n; i++) if (CrewNames.pick("human", i % 2 == 0, rng).contains(" ")) firstOnly = false;
 HomePlanet.config.setProperty(CrewNames.CFG_HUMAN, CrewNames.FIRST_LAST); boolean always = true; for (int i = 0; i < n; i++) if (!CrewNames.pick("human", i % 2 == 0, rng).contains(" ")) always = false;
 HomePlanet.config.remove(CrewNames.CFG_HUMAN); for (int i = 0; i < n; i++) if (CrewNames.pick("human", i % 2 == 0, rng).contains(" ")) two++;
 Setup.chk("H: lists on: First Names Only gives one word, First and Last Always two, Normal about half (" + two + " of " + n + ")", firstOnly && always && two > n / 3 && two < n * 2 / 3);
 Set<String> zoltan = new HashSet<String>(CrewNames.every("names/zoltan.xml"));
 HomePlanet.config.setProperty(CrewNames.CFG_HUMAN, CrewNames.FIRST); boolean zWhole = true; for (int i = 0; i < 300; i++) if (!zoltan.contains(CrewNames.pick("energy", i % 2 == 0, rng))) zWhole = false;
 HomePlanet.config.remove(CrewNames.CFG_HUMAN);
 Setup.chk("H: lists on: Human Name Gen leaves the aliens' names whole", zWhole);

 // Commission and volunteers, lists on
 Set<String> rock = new HashSet<String>(CrewNames.every("names/rock.xml"));
 boolean rocks = true; int rockWomen = 0, rockCrew = 0;
 for (int s = 0; s < 6; s++) for (CrewState c : Commission.build("PLAYER_SHIP_ROCK", "Rocks", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(s)).getPlayerShip().getCrewList()) {
  rockCrew++; if (!c.isMale()) rockWomen++; if (!rock.contains(c.getName())) rocks = false; }
 Setup.chk("C: a commissioned Rock ship's crew have Rock names, and some are women (" + rockWomen + " of " + rockCrew + ")", rocks && rockWomen > 0 && rockWomen < rockCrew);
 boolean vol = true; int volWomen = 0; for (int s = 0; s < 40; s++) { CrewState c = Commission.volunteer("energy", new Random(s)); if (!zoltan.contains(c.getName())) vol = false; if (!c.isMale()) volWomen++; }
 Setup.chk("C: Zoltan volunteers have Zoltan names, men and women (" + volWomen + " of 40 women)", vol && volWomen > 5 && volWomen < 35);
 HomePlanet.config.setProperty(StationConsole.GIRL_POWER, "true");
 boolean allWomen = true; for (int s = 0; s < 20; s++) { CrewState c = Commission.volunteer("rock", new Random(s)); if (c.isMale() || !rock.contains(c.getName())) allWomen = false; }
 HomePlanet.config.remove(StationConsole.GIRL_POWER);
 Setup.chk("C: girl power still makes every one a woman, named from her list", allWomen);

 // the looks of crew already on record: rolled as before, whatever the settings
 boolean asBefore = true; for (int s = 0; s < 30; s++) if (!Commission.lookOf("rock", new Random(s)).isMale()) asBefore = false;
 Setup.chk("R: crew already on record keep the old roll (a Rock from the old logs is still a man)", asBefore);

 // lists off: FTL's names, aliens men, Human Name Gen for every race
 HomePlanet.config.remove(CrewNames.CFG_LISTS);
 boolean ftl = true, men = true; Set<String> ftlM = new HashSet<String>(net.blerf.ftl.parser.DataManager.get().getCrewNames(true)), ftlF = new HashSet<String>(net.blerf.ftl.parser.DataManager.get().getCrewNames(false));
 for (int s = 0; s < 20; s++) { CrewState c = Commission.volunteer("rock", new Random(s)); if (!c.isMale()) men = false; if (!ftlM.contains(c.getName())) ftl = false; }
 Setup.chk("O: lists off: a Rock volunteer is a man with one of FTL's names, as before", ftl && men);
 HomePlanet.config.setProperty(CrewNames.CFG_HUMAN, CrewNames.FIRST); boolean ftlFirst = true; for (int i = 0; i < 400; i++) { String x = CrewNames.pick(i % 3 == 0 ? "rock" : "human", i % 2 == 0, rng); if (x.contains(" ") && !x.startsWith("Mr ")) ftlFirst = false; }
 HomePlanet.config.setProperty(CrewNames.CFG_HUMAN, CrewNames.FIRST_LAST); boolean ftlTwo = true; for (int i = 0; i < 400; i++) if (!CrewNames.pick(i % 3 == 0 ? "engi" : "human", i % 2 == 0, rng).contains(" ")) ftlTwo = false;
 HomePlanet.config.remove(CrewNames.CFG_HUMAN); boolean ftlAsIs = true; for (int i = 0; i < 400; i++) { boolean m = i % 2 == 0; if (!(m ? ftlM : ftlF).contains(CrewNames.pick("rock", m, rng))) ftlAsIs = false; }
 Setup.chk("O: lists off: First Names Only cuts FTL's names to a word (Mr Buga kept), First and Last Always gives two, Normal FTL's own, for every race", ftlFirst && ftlTwo && ftlAsIs);
 HomePlanet.config.setProperty(CrewNames.CFG_LISTS, "true");

 // a player's copy replaces the whole list; a broken one falls back and is named
 File copy = new File(lore, "names/rock.xml"); copy.getParentFile().mkdirs();
 SafeFiles.writeText(copy, "<names race=\"rock\"><rarity><level name=\"common\" weight=\"1\"/></rarity><female suffix=\"ite\"/><name sex=\"B\">Pebble</name></names>", false);
 boolean mine = true; for (int i = 0; i < 20; i++) { String x = CrewNames.pick("rock", i % 2 == 0, rng); if (!x.equals(i % 2 == 0 ? "Pebble" : "Pebblite")) mine = false; }
 Setup.chk("L: a copy in lore/names replaces the Rock list (Pebble, Pebblite: the final E drops)", mine);
 SafeFiles.writeText(copy, "<names race=\"rock\"><name>Broken", false); copy.setLastModified(copy.lastModified() + 5000);
 String back = CrewNames.pick("rock", true, rng); boolean named = false; for (String p : Lore.problems()) if (p.contains("names/rock.xml")) named = true;
 Setup.chk("L: a broken copy: the station's own Rock names (" + back + "), the problem named", rock.contains(back) && named);
 Lore.prepare();
 Setup.chk("L: lore/defaults/names holds the station's lists", new File(lore, "defaults/names/rock.xml").isFile() && new File(lore, "defaults/names/human.xml").isFile());
 Setup.done();
}
 static int count(NamesT.List_ l, String level) { int n = 0; String first = l.levels.keySet().iterator().next(); for (NamesT.Name x : l.names) if (!x.sex.equals("F") && level.equals(x.rarity == null ? first : x.rarity)) n++; return n; }
 static int get(Map<String, Integer> m, String k) { return m.containsKey(k) ? m.get(k) : 0; }
}
