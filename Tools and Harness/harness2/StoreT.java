import java.io.*; import java.util.*; import homeplanet.core.*;
/** The storage helper (Overhaul 6.0, Phase 1 step 7): reads, writes, defaults, and the odd cases. args: work dir. */
public class StoreT { public static void main(String[] a) throws Exception {
 File work = new File(a[a.length - 1]); SafeFiles.deleteTree(work); work.mkdirs();
 File f = new File(new File(work, "deeper"), "one.txt");
 Properties p = new Properties();
 p.setProperty("name", "Shippy McShipface"); p.setProperty("n", " 42 "); p.setProperty("big", "4000000000"); p.setProperty("yes", "True"); p.setProperty("odd", "forty");
 Store.write(f, p, "a test");
 Setup.chk("W: written through a folder that didn't exist, with the comment", f.isFile() && new String(SafeFiles.read(f), "UTF-8").startsWith("#a test"));
 Properties q = Store.read(f);
 Setup.chk("R: read back the same", q.getProperty("name").equals("Shippy McShipface") && q.size() == 5);
 Setup.chk("R: a number, trimmed", Store.num(q, "n", -1) == 42);
 Setup.chk("R: not a number: the default", Store.num(q, "odd", -7) == -7 && Store.num(q, "none", 3) == 3);
 Setup.chk("R: a long", Store.longOf(q, "big", -1) == 4000000000L && Store.longOf(q, "odd", -1) == -1);
 Setup.chk("R: true or false, any capitals, or the default", Store.bool(q, "yes", false) && !Store.bool(q, "odd", false) && Store.bool(q, "none", true));
 Setup.chk("R: a missing file reads empty", Store.read(new File(work, "none.txt")).isEmpty() && Store.load(new File(work, "none.txt")).isEmpty());
 Setup.chk("R: bytes read back the same", Store.parse(Store.bytes(p, null)).getProperty("name").equals("Shippy McShipface"));
 boolean threw = false;
 try { Store.load(work); } catch (IOException e) { threw = true; } // a folder, not a file: load() reads it as missing
 Setup.chk("R: load() of a folder is empty, not an error", !threw && Store.load(work).isEmpty());
 Setup.chk("W: the file's text is the same text as its bytes", Store.text(p, "x").equals(new String(Store.bytes(p, "x"), "UTF-8")));
 // overwriting keeps nothing of the old
 Properties r = new Properties(); r.setProperty("only", "this");
 Store.write(f, r, null);
 Setup.chk("W: a rewrite replaces the file whole", Store.read(f).size() == 1 && !new File(f.getPath() + ".tmp").exists());
 Setup.done();
}
}
