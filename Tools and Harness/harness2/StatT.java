import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Her report's Stats tab: this journey counted from where it began, her service across journeys, her crew's standouts, a traded ship's note; nothing shown that the records lack. args: gamedir, world saves (from WorldT), work */
public class StatT {
 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
  Vault v = Setup.open(game, saves); v.storage(); v.takeStock();

  // a ship the station never saw begin a journey: only the sector and difficulty for this journey
  Ship old = v.docked().get(0);
  new File(new File(v.historyDir(), old.id), "journey.txt").delete();
  ShipStats o = ShipStats.of(v, old, old.save());
  Setup.chk("S: a journey the station didn't see begin: no journey counts, said so", !o.journeyKnown && labels(o.journey).equals(Arrays.asList("Sector", "Difficulty")));

  // a first journey to sector 7, then a New Journey, then this one under way
  Ship s = v.docked().get(1);
  SavedGameState g = v.readCopy(s).save;
  g.setTotalShipsDefeated(40); g.setTotalScrapCollected(900); g.setTotalBeaconsExplored(120); g.setTotalCrewHired(12); g.setSectorNumber(6);
  g.setStateVar("killed_crew", 80); g.setStateVar("lost_crew", 3);
  v.write(s, g); v.setOut(s, g, "Set out for the test");
  g = v.readCopy(s).save; g.setSectorNumber(0); v.write(s, g); v.setOut(s, g, VoyageLog.NEW_JOURNEY);
  g = v.readCopy(s).save;
  g.setTotalShipsDefeated(47); g.setTotalScrapCollected(1210); g.setTotalBeaconsExplored(142); g.setSectorNumber(2);
  g.setStateVar("killed_crew", 95); g.setStateVar("lost_crew", 3);
  List<CrewState> crew = SaveHelper.getOwnCrew(g.getPlayerShip());
  crew.get(0).setPilotedEvasions(214); crew.get(0).setJumpsSurvived(160);
  crew.get(1).setCombatKills(28);
  v.write(s, g);
  ShipStats st = ShipStats.of(v, s, s.save());
  Setup.chk("S: this journey counts from its start: 7 ships, 310 scrap, 22 beacons, 15 enemy crew", st.journeyKnown
    && "7".equals(value(st.journey, "Ships defeated")) && "310".equals(value(st.journey, "Scrap collected")) && "22".equals(value(st.journey, "Beacons explored"))
    && "15".equals(value(st.journey, "Enemy crew killed")));
  Setup.chk("S: nothing gained this journey is left out, not shown as 0 (crew hired, crew lost)", value(st.journey, "Crew hired") == null && value(st.journey, "Crew lost") == null);
  Setup.chk("S: her service: 2 journeys, furthest sector 7 (the last journey's), 47 ships, 1,210 scrap", "2".equals(value(st.service, "Journeys"))
    && "7".equals(value(st.service, "Furthest sector")) && "47".equals(value(st.service, "Ships defeated")) && "1,210".equals(value(st.service, "Scrap collected")));
  Setup.chk("S: no final victories line without a victory", value(st.service, "Final victories") == null);
  SafeFiles.write(new File(new File(v.historyDir(), s.id), "victory-1.sav"), SafeFiles.read(s.file()));
  Setup.chk("S: a victory: Final victories 1, as a gain", "1".equals(value(ShipStats.of(v, s, s.save()).service, "Final victories")) && line(ShipStats.of(v, s, s.save()).service, "Final victories").tone == 1);
  Setup.chk("S: her crew's standouts: Best Pilot 214 evasions and Longest Serving to one, Best Gunner 28 kills to the other, none for repairs",
    standout(st, "Best Pilot", crew.get(0).getName(), "214 evasions") && standout(st, "Longest Serving", crew.get(0).getName(), "160 jumps survived")
    && standout(st, "Best Gunner", crew.get(1).getName(), "28 kills") && !titles(st).contains("Best Engineer"));

  // a traded ship: where she came from, and her original owner
  Ship t = v.docked().get(2);
  SafeFiles.writeText(new File(new File(v.historyDir(), t.id), "traded.txt"), "trade=x\ndate=2026-10-01 18:40\nfrom=Commander Bree\noriginal=Captain Ash\ndefeated=0\nbeacons=0\nscrap=0\nsectors=0\n", false);
  ShipStats ts = ShipStats.of(v, t, t.save());
  Setup.chk("S: a traded ship: first commissioned by Captain Ash; with you since the trade, from Commander Bree", "Captain Ash".equals(value(ts.service, "First commissioned by"))
    && ts.traded != null && ts.traded.contains("Commander Bree") && ts.traded.contains("2026-10-01 18:40"));
  Setup.done();
 }
 static List<String> labels(List<ShipStats.Line> l) { List<String> out = new ArrayList<String>(); for (ShipStats.Line x : l) out.add(x.label); return out; }
 static ShipStats.Line line(List<ShipStats.Line> l, String label) { for (ShipStats.Line x : l) if (x.label.equals(label)) return x; return null; }
 static String value(List<ShipStats.Line> l, String label) { ShipStats.Line x = line(l, label); return x == null ? null : x.value; }
 static List<String> titles(ShipStats st) { List<String> out = new ArrayList<String>(); for (ShipStats.Standout o : st.crew) out.add(o.title); return out; }
 static boolean standout(ShipStats st, String title, String who, String earned) {
  for (ShipStats.Standout o : st.crew) if (o.title.equals(title)) return o.crew.getName().equals(who) && o.earned.equals(earned);
  return false;
 }
}
