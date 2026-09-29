import java.io.*; import homeplanet.vault.*;
/** Builds the test world the other tests copy, and checks it. args: gamedir, work (the world ends up in work/saves) */
public class WorldT { public static void main(String[] a) throws Exception {
 File saves = Setup.world(new File(a[0]), new File(a[1]));
 Vault v = Vault.open(saves); v.takeStock();
 Setup.chk("fleet of " + Setup.WORLD.length, v.fleet().size() == Setup.WORLD.length);
 Setup.chk("the Kestrel is boarded", v.boarded() != null && v.boarded().name.equals("Test Kestrel") && v.continueFile().isFile());
 boolean allRead = true; for (Ship s : v.all()) if (s.save() == null) { allRead = false; System.out.println("   unreadable: " + s + " " + s.readError()); }
 Setup.chk("the Stealth is retrofitted", v.usingBlueprint("PLAYER_SHIP_STEALTH_HP").size() == 1);
 Setup.chk("every save parses, the storage hold too", allRead && v.storage().save() != null);
 Setup.done();
}}
