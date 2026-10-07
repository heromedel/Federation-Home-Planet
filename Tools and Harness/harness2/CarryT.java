import java.io.*; import java.util.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** A traded ship's crew carry their files (5.90): her package holds them, and at the other station each crew member's past comes with them, before anything there. args: gamedir, world saves (from WorldT), work */
public class CarryT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive(); HomePlanet.immersiveNotifications = false;
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 Ship donor = null;
 for (Ship s : v.docked()) if (donor == null && s.save() != null && !ShipPapers.custom(s.save().getPlayerShipBlueprintId()) && !SaveHelper.getOwnCrew(s.save().getPlayerShip()).isEmpty()) donor = s;
 Setup.chk("A: a docked ship with crew", donor != null);
 File crewDir = new File(v.folderOf(donor), CrewRegister.CREW_DIR);
 File[] files = crewDir.listFiles();
 Setup.chk("A: her crew's files are in her folder", files != null && files.length > 0);
 Arrays.sort(files);
 File one = files[0];
 Properties p = new Properties(); InputStream in = new FileInputStream(one); p.loadFromXML(in); in.close();
 String name = p.getProperty("name");
 p.setProperty("served", "The Old Glory|" + p.getProperty("served", "")); p.setProperty("e.0", "3|Held the line at the Old Glory's last stand."); // her past at the other station
 OutputStream out = new FileOutputStream(one); p.storeToXML(out, null); out.close();
 byte[] pkg = v.packageOf(donor);
 Map<String, byte[]> got = Vault.unpack(pkg);
 int crewIn = 0; for (String k : got.keySet()) if (k.startsWith("crew/")) crewIn++;
 Setup.chk("B: her package carries her crew's files (" + crewIn + " of " + files.length + ")", crewIn == files.length);
 // a package can't put a file outside her folder
 ByteArrayOutputStream bo = new ByteArrayOutputStream(); java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(bo);
 z.putNextEntry(new java.util.zip.ZipEntry("ship.sav")); z.write(got.get("ship.sav")); z.closeEntry();
 for (String bad : new String[] {"crew/../evil.xml", "crew/sub/evil.xml", "crew/.hidden.xml"}) { z.putNextEntry(new java.util.zip.ZipEntry(bad)); z.write(new byte[] {1}); z.closeEntry(); }
 z.close();
 Setup.chk("B: a crew entry that would climb out of her folder is left out", Vault.unpack(bo.toByteArray()).size() == 1);
 // she arrives at a station (this one, standing for the other)
 SavedGameState gs = donor.save();
 Ship came = v.receive(pkg, SafeFiles.read(v.fileOf(donor)), gs, "carry-test#1", "Commander Bree");
 File arrived = new File(new File(v.folderOf(came), CrewRegister.CREW_DIR), CrewRegister.ARRIVED);
 Setup.chk("C: on arrival her crew's files wait in her crew/arrived/", arrived.isDirectory() && arrived.list().length == files.length);
 v.takeStock();
 CrewRegister.Member theirs = null; // the newcomer aboard the arrival: her deeds there carried in, as Prior (another career's days)
 for (CrewRegister.Member m : CrewRegister.members(v)) if (m.name.equals(name)) for (CrewRegister.Event e : m.events) if (e.text.startsWith("Held the line") && e.day == 0) theirs = m;
 Setup.chk("C: aboard the arrival, " + name + " comes with her deeds there, as Prior (another career's days)", theirs != null && theirs.status == CrewRegister.Status.PRESENT);
 Setup.chk("C: and the ships she served in there, then the arrival: " + (theirs == null ? "?" : theirs.served), theirs != null && theirs.served.indexOf("The Old Glory") == 0 && CrewRegister.shipOf(theirs.served.get(theirs.served.size() - 1)).equals(came.name));
 Setup.chk("C: each file taken in once: crew/arrived/ is empty", arrived.list() == null || arrived.list().length == 0);
 Setup.done();
}}
