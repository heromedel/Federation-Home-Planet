import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** parse -> toBytes -> compare, for every ship in a vault plus any extra files. args: gamedir, saves, [extra.sav...] */
public class RoundT { public static void main(String[] a) throws Exception {
 Vault v = Setup.open(new File(a[0]), new File(a[1])); v.takeStock();
 List<File> files = new ArrayList<File>(); for (Ship s : v.all()) files.add(s.file());
 for (int i = 2; i < a.length; i++) files.add(new File(a[i]));
 int same = 0, diff = 0, bad = 0;
 for (File f : files) {
  try {
   byte[] orig = SafeFiles.read(f);
   boolean hold = HoldXml.isHold(orig); // the Cargo Hold's xml (5.84): read and written as itself
   SavedGameState g = hold ? HoldXml.read(orig) : new SavedGameParser().readSavedGame(f);
   byte[] out = hold ? HoldXml.toBytes(g) : SaveHelper.toBytes(g);
   if (Arrays.equals(orig, out)) { same++; continue; }
   diff++;
   int k = 0; while (k < Math.min(orig.length, out.length) && orig[k] == out[k]) k++;
   System.out.println("   DIFF " + f.getName() + ": " + orig.length + " -> " + out.length + " bytes, first difference at " + k + " (mystery bytes: " + g.getMysteryList().size() + ")");
   // second pass: is the rewrite at least stable?
   SavedGameState g2 = new SavedGameParser().readSavedGame(new ByteArrayInputStream(out) == null ? f : f);
  } catch (Exception e) { bad++; System.out.println("   FAIL " + f.getName() + ": " + e); }
 }
 System.out.println("round trip: " + same + " identical, " + diff + " differ, " + bad + " unreadable");
}}
