import java.io.*; import java.util.*; import net.blerf.ftl.parser.*; import homeplanet.core.*; import homeplanet.vault.*;
public class StoT { public static void main(String[] a) throws Exception {
 Vault v = Setup.open(new File(a[0]), new File(a[1])); v.takeStock();
 SavedGameParser p = new SavedGameParser();
 Map<String,Integer> std = HistoryLog.inventory(p.readSavedGame(new File(a[2]))), ae = HistoryLog.inventory(p.readSavedGame(new File(a[3]))), merged = HistoryLog.inventory(v.storage().save());
 System.out.println("std: " + std); System.out.println("ae:  " + ae); System.out.println("merged: " + merged);
 Map<String,Integer> sum = new LinkedHashMap<String,Integer>(ae); for (Map.Entry<String,Integer> e : std.entrySet()) sum.put(e.getKey(), (sum.containsKey(e.getKey()) ? sum.get(e.getKey()) : 0) + e.getValue());
 Setup.chk("merged = std + ae", sum.equals(merged)); Setup.done();
}}
