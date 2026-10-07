import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.vault.*;
/**
 * The vault's and Reputation's locks (5.61): one thread holds the vault and asks Reputation (as the vault does when a
 * ship is lost), the other asks Reputation for a first review, which reads the vault. Two locks taken in opposite
 * orders froze the station; with one lock both finish. args: gamedir, world saves (from WorldT), work
 */
public class LockT {
 static void pause() { try { Thread.sleep(2); } catch (InterruptedException e) { } }
 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
  HomePlanet.immersiveNotifications = false; HomePlanet.careerMessages = false; HomePlanet.reputationOn = true;
  final Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  final File counted = new File(v.root, "reputation.txt");
  final int rounds = 300;
  final int[] done = new int[2];
  Thread vaultFirst = new Thread(new Runnable() { public void run() {
   for (int i = 0; i < rounds; i++) { synchronized (v) { pause(); Reputation.total(v); } done[0]++; } // holding the vault a moment: the other thread is then in Reputation, waiting for it
  } }, "vault-then-reputation");
  Thread reviewing = new Thread(new Runnable() { public void run() {
   for (int i = 0; i < rounds; i++) { counted.delete(); Reputation.total(v); done[1]++; } // never counted: a review, which reads the vault
  } }, "reputation-then-vault");
  vaultFirst.setDaemon(true); reviewing.setDaemon(true);
  vaultFirst.start(); reviewing.start();
  vaultFirst.join(60000); reviewing.join(60000);
  Setup.chk("L: both threads finish, " + rounds + " rounds each (" + done[0] + ", " + done[1] + ")", !vaultFirst.isAlive() && !reviewing.isAlive() && done[0] == rounds && done[1] == rounds);
  Setup.done();
  System.exit(Setup.fails == 0 ? 0 : 1); // a frozen thread would keep the test running
 }
}
