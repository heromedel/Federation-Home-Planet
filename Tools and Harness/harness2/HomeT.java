import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.model.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** One home each (Overhaul 6.0, Phase 1 step 10): race names in both forms, words, store prices, a ship's gear. args: game, saves, work dir. */
public class HomeT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 // race names: the crew member's title and the people's name
 Setup.chk("R: FTL's titles: Rockman, Zoltan, Lanius, Human, Engi", Crew.raceTitle("rock").equals("Rockman") && Crew.raceTitle("energy").equals("Zoltan") && Crew.raceTitle("anaerobic").equals("Lanius")
   && Crew.raceTitle("human").equals("Human") && Crew.raceTitle("engi").equals("Engi") && Crew.raceTitle("").isEmpty() && Crew.raceTitle((String) null).isEmpty());
 Setup.chk("R: the people's names: Rock, Zoltan, Lanius, Slug, Crystal", Crew.racePeople("rock").equals("Rock") && Crew.racePeople("energy").equals("Zoltan") && Crew.racePeople("anaerobic").equals("Lanius")
   && Crew.racePeople("slug").equals("Slug") && Crew.racePeople("crystal").equals("Crystal") && Crew.racePeople("Rockman").equals("Rock"));
 Setup.chk("R: a ship's people from her blueprint or a ship list: Engi (Circle, Stealth), Zoltan, Slug (Jelly), Rock pirates; none for the Federation and the rebels",
   "Engi".equals(Crew.peopleOf("PLAYER_SHIP_CIRCLE")) && "Engi".equals(Crew.peopleOf("PLAYER_SHIP_STEALTH")) && "Zoltan".equals(Crew.peopleOf("PLAYER_SHIP_ENERGY_2")) && "Slug".equals(Crew.peopleOf("PLAYER_SHIP_JELLY"))
   && "Rock".equals(Crew.peopleOf("SHIPS_ROCK_PIRATE")) && Crew.peopleOf("PLAYER_SHIP_FED") == null && Crew.peopleOf("SHIPS_REBEL") == null && Crew.peopleOf(null) == null);
 CrewState c = Commission.volunteer("rock", new Random(1));
 Setup.chk("R: a crew member's title reads the same way", c == null || Crew.raceTitle(c).equals("Rockman"));
 Setup.chk("R: the voyage log's words and the Captain's Log's agree with the home", VoyageLog.shipWords("ROCK_PIRATE", "SHIPS_ROCK_PIRATE", "rock").equals("a Rock pirate") && VoyageLog.shipWords("PIRATE", "SHIPS_PIRATE", "engi").equals("an Engi pirate")
   && VoyageLog.shipWords("", "SHIPS_FED", "human").equals("a Federation ship") && VoyageLog.shipWords("", "", "").equals("a ship"));
 // words
 Setup.chk("W: cap and a/an", Words.cap("the Kestrel").equals("The Kestrel") && Words.cap("").isEmpty() && Words.cap(null) == null && Words.a("Engi").equals("an Engi") && Words.a("Rockman").equals("a Rockman") && Words.a("").isEmpty());
 Setup.chk("W: the, as ShipNames has it", ShipNames.the("Kestrel").equals("the Kestrel") && ShipNames.the("The Adjudicator").equals("The Adjudicator") && ShipNames.the("the Nightjar").equals("the Nightjar"));
 // prices: FTL's own store prices under Pricing, as the Dry Dock's shop used to read them itself
 Setup.chk("P: a weapon, a drone, an augment at store price; unknown is -1 for the shop and 0 for the sums", Pricing.store("LASER_BURST_1") > 0 && Pricing.store("COMBAT_1") > 0 && Pricing.store("SHIELD_RECHARGE") > 0
   && Pricing.store("NO_SUCH_THING") == -1 && Pricing.item("NO_SUCH_THING") == 0 && Pricing.item("LASER_BURST_1") == Pricing.store("LASER_BURST_1"));
 Setup.chk("P: a crew member and a system at store price", Pricing.crewStore("rock") > 0 && Pricing.crewStore("no_such_race") == -1 && Pricing.crew("no_such_race") == 0 && Pricing.systemStore("shields") > 0 && Pricing.systemStore("no_such") == -1);
 Setup.chk("P: an artillery weapon keeps its luxury price", Pricing.item("ARTILLERY_FED") >= Pricing.UNPRICED_ARTILLERY);
 // gear
 Ship d = v.docked().get(0);
 SavedGameState gs = v.readCopy(d).save; ShipState s = gs.getPlayerShip();
 List<String> gear = SaveHelper.gear(s);
 Setup.chk("G: her gear: weapons, drones, augments, in order, and with her cargo after", gear.size() == s.getWeaponList().size() + s.getDroneList().size() + s.getAugmentIdList().size()
   && (s.getWeaponList().isEmpty() || gear.get(0).equals(s.getWeaponList().get(0).getWeaponId()))
   && SaveHelper.gearAndCargo(gs).size() == gear.size() + SaveHelper.cargo(gs).size() && SaveHelper.gearAndCargo(gs).subList(0, gear.size()).equals(gear));
 Setup.done();
}
}
