import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.model.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The player's ranks (heromedel, 5.56): Ranks From Rep, From Cruiser, or none; Immersive Mode locked to Ranks From Rep; an old career's rank checked once. args: gamedir, world saves, work */
public class RankT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 // the ladder
 Setup.chk("L: Major 0, Colonel 250, Commander 500, Captain 1,000, Commodore 2,500, Admiral 10,000", PlayerRank.forReputation(0) == 0 && PlayerRank.forReputation(249) == 0 && PlayerRank.forReputation(250) == 1
   && PlayerRank.forReputation(500) == 2 && PlayerRank.forReputation(1000) == 3 && PlayerRank.forReputation(2500) == 4 && PlayerRank.forReputation(9999) == 4 && PlayerRank.forReputation(10000) == 5);
 PlayerRank.setting = PlayerRank.FROM_REP; HomePlanet.reputationOn = true;
 int[] mult = new int[6]; for (int r = 0; r < 6; r++) mult[r] = PlayerRank.multiple(r);
 Setup.chk("L: the stipend's multiple: 1, 2, 2, 3, 3, 4", Arrays.equals(mult, new int[] {1, 2, 2, 3, 3, 4}) && PlayerRank.name(5).equals("Admiral") && PlayerRank.name(0).equals("Major"));
 // the setting
 PlayerRank.setting = PlayerRank.NONE; HomePlanet.immersiveMode = true;
 Setup.chk("S: Immersive Mode is locked to Ranks From Rep", PlayerRank.mode() == PlayerRank.FROM_REP);
 HomePlanet.immersiveMode = false;
 Setup.chk("S: Sandbox Mode: the player's choice", PlayerRank.mode() == PlayerRank.NONE && PlayerRank.rank(Unlocks.read()) == -1 && PlayerRank.multiple(-1) == 1 && PlayerRank.name(-1).equals("Commander"));
 PlayerRank.setting = PlayerRank.FROM_REP; HomePlanet.reputationOn = false;
 Setup.chk("S: Ranks From Rep without Reputation: the cruiser's ranks meanwhile", PlayerRank.mode() == PlayerRank.FROM_CRUISER);
 HomePlanet.reputationOn = true;
 // a Sandbox career from before 5.56: a Captain by the Federation Cruiser A
 HomePlanet.immersiveNotifications = true; HomePlanet.careerMessages = true;
 PlayerRank.setting = PlayerRank.FROM_CRUISER;
 TransT.profile(saves, new String[] {"PLAYER_SHIP_HARD"}, new String[0]);
 Transmissions.check(); // the career begins
 UnlockGrants.turnedOn(Unlocks.read());
 TransT.profile(saves, new String[] {"PLAYER_SHIP_HARD", "PLAYER_SHIP_FED"}, new String[0]);
 Setup.chk("O: under the cruiser's ranks, a Captain", PlayerRank.rank(Unlocks.read()) == 1 && Transmissions.rank().equals("Captain"));
 Reputation.expedition(v, "an old expedition", 0, 0, 1); Reputation.expedition(v, "another old expedition", 0, 0, 1); // something for its letter to recall
 Store.file(v.root, "rank").delete(); // as a career from before 5.56
 PlayerRank.setting = PlayerRank.FROM_REP;
 Transmissions.check();
 int captain = Arrays.asList(PlayerRank.REP_RANKS).indexOf("Captain");
 Setup.chk("O: checked once: still a Captain, one letter about the new ranks, no letter for each step passed, custom ships kept",
   PlayerRank.rank(Unlocks.read()) == captain && TransT.find("rank:ladder") != null && TransT.find("rank:ladder").body.contains("Our Sincerest Congratulations Captain.")
   && TransT.find("rank:1") == null && TransT.find("rank:3") == null && PlayerRank.kept(v, "custom") && !PlayerRank.kept(v, "artillery"));
 Transmissions.check();
 int ladders = 0; for (Transmissions.Message m : Transmissions.load()) if (m.key.equals("rank:ladder")) ladders++;
 Setup.chk("O: and only once", ladders == 1);
 // reputation lost: never a rank
 Reputation.expedition(v, "a hard expedition", 0, 30, -1);
 Transmissions.check();
 Setup.chk("R: reputation lost, the rank kept", PlayerRank.rank(Unlocks.read()) == captain);
 // climbing on
 Reputation.expedition(v, "test standing", 10 * (PlayerRank.REP_STEPS[PlayerRank.COMMODORE] - Reputation.total(v)), 0, 0);
 Transmissions.check();
 Setup.chk("R: 2,500 reputation: Commodore, its letter alone", PlayerRank.rank(Unlocks.read()) == PlayerRank.COMMODORE && TransT.find("rank:4") != null && TransT.find("rank:3") == null
   && TransT.find("rank:4").body.startsWith("The Federation Home Planet Admiralty is thrilled to inform you"));
 // what the career did, once each (heromedel, 5.57): no token left, never the same line twice, never the Rebel Flagship
 String l0 = TransT.find("rank:ladder").body, l4 = TransT.find("rank:4").body;
 Setup.chk("A: the letters' accolades filled or left out, no two alike: [" + l0.replace("\n", " / ") + "] [" + l4.replace("\n", " / ") + "]",
   !l0.contains("{") && !l4.contains("{") && !l0.contains("\n\n\n") && !l4.contains("\n\n\n") && !l0.contains("Flagship") && !l4.contains("Flagship"));
 int ladderSaid = 0; for (String k : new String[] {"ship", "achievement", "model", "fights", "crew", "expeditions"}) if ("rank:ladder".equals(v.event("accolade:" + k))) ladderSaid++;
 Setup.chk("A: a career confirmed as Captain (three ranks up): up to three accolades in its letter, as many as the fleet has (" + ladderSaid + ")", ladderSaid >= 1 && ladderSaid <= 3);
 java.lang.reflect.Method aid = Accolades.class.getDeclaredMethod("achievementId", String.class); aid.setAccessible(true);
 Setup.chk("A: an achievement is told as its deed: FTL's name found (" + aid.invoke(null, "Givin' her all she's got, Captain!") + ")", "ACH_ENERGY_POWER".equals(aid.invoke(null, "Givin' her all she's got, Captain!")) && "ACH_SECTOR_5".equals(aid.invoke(null, "Just Getting Started")));
 String rest = "A\n\n{accolade}\n\nB", once = null; java.util.Set<String> said = new java.util.HashSet<String>(); boolean twice = false;
 for (int i = 0; i < 10; i++) { once = Accolades.fill(v, "test:" + i, rest); if (!once.equals("A\n\nB") && !said.add(once)) twice = true; }
 Setup.chk("A: once every kind is used, the line is left out cleanly (" + once.replace("\n", "/") + ")", once.equals("A\n\nB") && !twice);
 // switching back and forth in Sandbox Mode loses nothing
 PlayerRank.setting = PlayerRank.FROM_CRUISER;
 Setup.chk("W: switched to the cruiser's ranks: a Captain there, x2", PlayerRank.rank(Unlocks.read()) == 1 && PlayerRank.multiple(PlayerRank.rank(Unlocks.read())) == 2);
 PlayerRank.setting = PlayerRank.FROM_REP;
 Setup.chk("W: and back: still a Commodore, x3", PlayerRank.rank(Unlocks.read()) == PlayerRank.COMMODORE && PlayerRank.multiple(PlayerRank.rank(Unlocks.read())) == 3);
 // the cruiser's +100, once each layout, never for one from before the record
 Transmissions.check(); Reputation.total(v);
 int lines = 0; for (String l : Reputation.recent(v, 50)) if (l.contains("Federation Cruiser, Type A unlocked (+100)")) lines++;
 Setup.chk("C: the Federation Cruiser A unlocked in the career's service: +100 reputation, once (" + lines + ")", lines == 1);
 Setup.done();
}
}
