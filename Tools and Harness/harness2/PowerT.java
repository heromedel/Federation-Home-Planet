import java.io.*; import java.util.*; import java.lang.reflect.*; import javax.swing.JComponent; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.parser.SaveHelper; import homeplanet.core.SafeFiles;
/**
 * The Refit tab's Power Distribution Simulation (6.43, docs/retrofit-screen-redux-roadmap.md): it opens as the save left
 * the ship, and clicking follows FTL's rules as FTL 1.6.14 showed them under Wine: shields in pairs, weapons and drones
 * all or nothing within their system's level and the reactor, FTL's own warnings, a Zoltan's free bar, Reset.
 * args: gamedir, world saves (from WorldT), work
 */
public class PowerT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Setup.open(game, saves);
 // the Kestrel FTL was played with: Shields 4 (one bar broken), Engines 3, Weapons 4, Drones 3, Cloaking, Teleporter, a 12-bar reactor
 SavedGameState g = homeplanet.parser.Commission.build("PLAYER_SHIP_HARD", "Sim Test", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(5));
 ShipState s = g.getPlayerShip();
 for (String w : new String[] {"LASER_HEAVY_1", "ION_1"}) s.addWeapon(SaveHelper.newIdleWeapon(w));
 for (String d : new String[] {"COMBAT_1", "DEFENSE_1"}) s.addDrone(SaveHelper.newIdleDrone(d));
 int[][] caps = {{SystemType.WEAPONS.ordinal(), 4}, {SystemType.DRONE_CTRL.ordinal(), 3}, {SystemType.SHIELDS.ordinal(), 4}, {SystemType.CLOAKING.ordinal(), 1}, {SystemType.TELEPORTER.ordinal(), 1}, {SystemType.ENGINES.ordinal(), 3}};
 for (int[] c : caps) { SystemType t = SystemType.values()[c[0]]; SystemState st = s.getSystem(t); if (st == null) { st = new SystemState(t); s.addSystem(st); } st.setCapacity(c[1]); }
 s.getSystem(SystemType.SHIELDS).setDamagedBars(1);
 s.getSystem(SystemType.SHIELDS).setPower(2);
 s.getSystem(SystemType.ENGINES).setPower(1);
 s.setReservePowerCapacity(12);

 Class<?> pc = Class.forName("homeplanet.ui.PowerPanel");
 Constructor<?> k = pc.getDeclaredConstructor(); k.setAccessible(true);
 final Object p = k.newInstance();
 call(p, "show", new Class<?>[] {ShipState.class}, s);
 List<?> systems = (List<?>) field(p, "systems"), weaponSlots = (List<?>) field(p, "weaponSlots"), droneSlots = (List<?>) field(p, "droneSlots");
 Object shields = find(systems, "SHIELDS"), engines = find(systems, "ENGINES"), cloak = find(systems, "CLOAKING"), tele = find(systems, "TELEPORTER");
 Object weapons = field(p, "weapons"), drones = field(p, "drones");
 Object artemis = weaponSlots.get(0), burst = weaponSlots.get(1), heavy = weaponSlots.get(2), ion = weaponSlots.get(3), combat = droneSlots.get(0), defense = droneSlots.get(1);
 int saveLeft = left(p);
 Setup.chk("A: it opens as the save left her: reactor 12, Shields 2 of 4 (one broken), the weapons off, 7 bars free (Shields 2, Engines 1, Medbay 1, Oxygen 1)",
   (Integer) field(p, "reactor") == 12 && power(shields) == 2 && power(weapons) == 0 && saveLeft == 7 && systems.size() == 6);
 Setup.chk("A: no subsystems on the panel (no Piloting, Doors, Sensors)", find(systems, "PILOT") == null && find(systems, "DOORS") == null && find(systems, "SENSORS") == null);

 click(p, shields, false);
 Setup.chk("B: Shields go two bars at a time: with one bar broken, the third won't take", power(shields) == 2 && left(p) == 7);

 click(p, burst, false); click(p, heavy, false); click(p, ion, false);
 Setup.chk("C: Burst Laser II, Heavy Laser I and Ion Blast on: Weapon Control's 4 bars full, 3 left in the reactor", power(weapons) == 4 && on(burst) && on(heavy) && on(ion) && left(p) == 3);
 click(p, artemis, false);
 Setup.chk("C: Artemis refused with FTL's own warning: NOT ENOUGH SYSTEM POWER", !on(artemis) && power(weapons) == 4 && String.valueOf(field(p, "warning")).contains("SYSTEM POWER"));
 click(p, ion, true); click(p, artemis, false);
 Setup.chk("D: Ion Blast off (right click), then Artemis fits: the player chooses which, all or nothing", !on(ion) && on(artemis) && power(weapons) == 4);

 click(p, defense, false);
 Setup.chk("E: Defense I on: Drone Control 2 of 3, 1 bar left", on(defense) && power(drones) == 2 && left(p) == 1);
 click(p, combat, false);
 Setup.chk("E: Combat I refused (Drone Control's 3 can't hold both): NOT ENOUGH SYSTEM POWER", !on(combat) && String.valueOf(field(p, "warning")).contains("SYSTEM POWER"));

 click(p, cloak, false);
 Setup.chk("F: Cloaking takes the last bar", power(cloak) == 1 && left(p) == 0);
 click(p, tele, false);
 Setup.chk("F: the Teleporter refused with the reactor empty: NOT ENOUGH POWER, at the reactor", power(tele) == 0 && String.valueOf(field(p, "warning")).contains("ENOUGH") && (Boolean) field(p, "warningAtReactor"));

 click(p, shields, true);
 Setup.chk("G: Shields off a barrier at a time (right click): two bars back", power(shields) == 0 && left(p) == 2);
 click(p, shields, false);
 Setup.chk("G: and on again, two bars", power(shields) == 2 && left(p) == 0);

 click(p, weapons, true);
 Setup.chk("H: right click on Weapons' own icon: the rightmost one on goes off (Heavy Laser I)", !on(heavy) && on(burst) && on(artemis) && power(weapons) == 3);
 click(p, engines, true);
 Setup.chk("H: Engines off with a right click", power(engines) == 0 && left(p) == 2);

 call(p, "reset", new Class<?>[0]);
 Setup.chk("I: Reset: back as the save had it", power(shields) == 2 && power(weapons) == 0 && !on(burst) && !on(defense) && power(cloak) == 0 && left(p) == saveLeft);

 String tip = (String) call(p, "tip", new Class<?>[] {Object.class}, shields);
 Setup.chk("J: the hover in FTL's words: its tooltip, the level, the status, Damaged, how to add power (" + tip.replace("\n", " / ") + ")",
   tip.startsWith("Shields: Sustains projectile-blocking shields.") && tip.contains("Level 4: Two Shield Barriers") && tip.contains("Partially Powered") && tip.contains("Damaged") && tip.contains("Left Click"));

 // a Zoltan in the shield room: a free bar, not the reactor's
 net.blerf.ftl.xml.ShipBlueprint bp = DataManager.get().getShip(s.getShipBlueprintId());
 int shieldRoom = bp.getSystemList().getSystemRoom(SystemType.SHIELDS)[0].getRoomId();
 CrewState z = s.getCrewList().get(0);
 z.setRace(CrewType.ENERGY); z.setRoomId(shieldRoom);
 call(p, "show", new Class<?>[] {ShipState.class}, s);
 systems = (List<?>) field(p, "systems"); shields = find(systems, "SHIELDS");
 Setup.chk("K: a Zoltan in the shield room: Shields 3 lit (2 from the reactor, 1 the Zoltan's), the reactor's count unchanged", power(shields) == 3 && (Integer) field(shields, "zoltan") == 1 && left(p) == saveLeft);
 click(p, shields, true);
 Setup.chk("K: right click takes the reactor's two, never the Zoltan's", power(shields) == 1 && left(p) == saveLeft + 2);
 click(p, shields, true);
 Setup.chk("K: nothing more to take", power(shields) == 1);

 // it draws, at the tab's width and at FTL's
 for (int w : new int[] {1248, 640}) {
  ((JComponent) p).setSize(w, 160);
  java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, 160, java.awt.image.BufferedImage.TYPE_INT_ARGB);
  java.awt.Graphics2D gg = img.createGraphics(); ((JComponent) p).paint(gg); gg.dispose();
 }
 Object hit = call(p, "at", new Class<?>[] {int.class, int.class}, 20, 60);
 Setup.chk("L: it draws, and the reactor is where the mouse finds it", "reactor".equals(hit));
 Setup.done();
}
 static Object find(List<?> systems, String type) throws Exception { for (Object x : systems) if (((Enum<?>) field(x, "type")).name().equals(type)) return x; return null; }
 static int power(Object sys) throws Exception { return (Integer) field(sys, "power"); }
 static boolean on(Object slot) throws Exception { return (Boolean) field(slot, "on"); }
 static int left(Object p) throws Exception { return (Integer) call(p, "left", new Class<?>[0]); }
 static void click(Object p, Object what, boolean right) throws Exception { call(p, "click", new Class<?>[] {Object.class, boolean.class}, what, right); }
 static Object field(Object o, String name) throws Exception { Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
 static Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception { Method m = o.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(o, args); }
}
