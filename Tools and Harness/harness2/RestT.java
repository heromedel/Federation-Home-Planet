import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** Captain's Quarters: a day's rest passes a beacon; days in a row cost reputation, 1 to 5; the question's words. args: gamedir, world saves, work */
public class RestT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive(); HomePlanet.reputationOn = false;
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 int b = v.beaconsSeen();
 Setup.chk("Q: the first day is free and asks plainly", Rest.run(v) == 0 && Rest.cost(v) == 0 && Rest.question(v).equals("Would you like to spend the rest of today in your quarters."));
 Rest.rest(v);
 Setup.chk("Q: a day's rest passes one beacon; the next day knows it was yesterday", v.beaconsSeen() == b + 1 && Rest.run(v) == 1
   && Rest.question(v).equals("Would you like to spend the rest of today in your quarters as you did yesterday. What will people think."));
 Setup.chk("Q: without Reputation it costs nothing, and says no number", Rest.cost(v) == 0 && !Rest.question(v).contains("reputation"));
 Rest.rest(v); Rest.rest(v);
 Setup.chk("Q: three days in a row", Rest.run(v) == 3 && Rest.question(v).startsWith("Would you like to spend the rest of today in your quarters as you have for the last 3 days. What will people think."));
 v.countBeacon(); // something else moved the clock: a jump, a detail sent
 Setup.chk("Q: anything else that moves the clock ends the run", Rest.run(v) == 0 && Rest.cost(v) == 0);
 // with Reputation: 0, 1, 2, 3, 4, 5, 5...
 HomePlanet.reputationOn = true;
 int rep = Reputation.total(v);
 int[] costs = new int[8];
 for (int i = 0; i < 8; i++) { costs[i] = Rest.cost(v); Rest.rest(v); }
 Setup.chk("Q: the days in a row cost 0, 1, 2, 3, 4, 5, 5, 5 " + Arrays.toString(costs), Arrays.equals(costs, new int[] {0, 1, 2, 3, 4, 5, 5, 5}));
 Setup.chk("Q: charged to the reputation (" + (rep - Reputation.total(v)) + " for eight days) and logged", Reputation.total(v) == rep - 25 && Reputation.log(v).contains("Rested in quarters again"));
 Setup.chk("Q: the question shows the cost on its own line", Rest.question(v).endsWith("What will people think.\n\n-5 reputation."));
 HomePlanet.reputationOn = false;
 Setup.done();
}}
