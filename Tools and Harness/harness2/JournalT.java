import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.vault.*;
/**
 * The journal (Overhaul 6.0, Phase 2, §3.2; 5.71): an action's note written first, its steps done, the note deleted; a second write
 * to one file refused; a failed action undone; a note left by a station that stopped partway finished at the next opening, with its
 * own time and stardate in the log; one that can't be told left for a person; the station's logs moved into logs/. args: game, saves, work dir.
 */
public class JournalT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
 Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
 File root = v.root, jdir = Journal.dir(v);
 // L: the station's logs in logs/
 Setup.chk("L: the station's logs live in logs/: history, master, events; none at the root", v.historyLog().isFile() && v.historyLog().getParentFile().equals(v.logsDir())
   && new File(v.logsDir(), "master.log").isFile() && EventLog.file(v).isFile() && !new File(root, "history.log").exists() && !new File(root, "events.log").exists());
 Setup.chk("L: a fleet's station log is found wherever that fleet keeps it", Vault.historyLogIn(root).equals(v.historyLog()) && Vault.historyLogIn(new File(work, "nowhere")).getName().equals("history.log"));
 // A: a note of every kind of step, committed
 File fresh = new File(root, "jt-fresh.txt"), old = new File(root, "jt-old.txt"), dirFrom = new File(root, "jt-folder"), dirTo = new File(root, "jt-moved"), gone = new File(root, "jt-gone.txt");
 SafeFiles.writeText(old, "old\n", false); SafeFiles.writeText(gone, "x\n", false); dirFrom.mkdirs(); SafeFiles.writeText(new File(dirFrom, "inside.txt"), "in\n", false);
 Journal.Note n = Journal.begin(v, "TEST_ACTION");
 n.replace(fresh, "fresh\n".getBytes("UTF-8")).replace(old, "new\n".getBytes("UTF-8")).rename(dirFrom, dirTo).delete(gone);
 n.commit();
 Setup.chk("A: a file made, one replaced, a folder renamed whole, a file deleted; the note gone after", text(fresh).equals("fresh\n") && text(old).equals("new\n") && !dirFrom.exists() && text(new File(dirTo, "inside.txt")).equals("in\n") && !gone.exists()
   && (jdir.listFiles() == null || jdir.listFiles().length == 0) && !new File(root, "jt-old.txt.tx").exists());
 // B: each file once
 boolean refused = false; try { Journal.begin(v, "TEST_TWICE").replace(old, "a\n".getBytes("UTF-8")).replace(old, "b\n".getBytes("UTF-8")); } catch (IOException e) { refused = e.getMessage().contains("twice"); }
 boolean same = true; try { Journal.begin(v, "TEST_TWICE").replace(old, "a\n".getBytes("UTF-8")).replace(old, "a\n".getBytes("UTF-8")); } catch (IOException e) { same = false; }
 boolean outside = false; try { Journal.begin(v, "TEST_OUT").delete(new File(work, "elsewhere.txt")); } catch (IOException e) { outside = true; }
 Setup.chk("B: a second, different write to one file is refused; the same bytes twice pass; a file outside the fleet's folder is refused", refused && same && outside);
 // C: a failed action is undone
 File blocker = new File(root, "jt-blocker"); blocker.mkdirs();
 n = Journal.begin(v, "TEST_FAIL").replace(old, "changed\n".getBytes("UTF-8")).rename(dirTo, blocker);
 boolean failed = false; try { n.commit(); } catch (IOException e) { failed = true; }
 Setup.chk("C: a rename onto something already there fails, and the file replaced before it is put back; no note, no waiting bytes left", failed && text(old).equals("new\n") && dirTo.isDirectory()
   && (jdir.listFiles() == null || jdir.listFiles().length == 0) && !new File(root, "jt-old.txt.tx").exists());
 // D: a note left by a station that stopped partway: the bytes of one replacement still wait, the rename is done, the delete isn't
 File d1 = new File(root, "jt-d1.txt"), d2 = new File(root, "jt-d2.txt"), dRen = new File(root, "jt-ren-from"), dRenTo = new File(root, "jt-ren-to"), dDel = new File(root, "jt-del.txt");
 SafeFiles.writeText(d1, "before\n", false); SafeFiles.writeText(new File(root, "jt-d1.txt.tx"), "after\n", false);
 SafeFiles.writeText(d2, "done\n", false); // its bytes were already moved in: nothing waits
 dRenTo.mkdirs(); SafeFiles.writeText(dDel, "x\n", false);
 jdir.mkdirs();
 String r = root.getName() + "/"; // a note's paths are relative to the saves folder
 SafeFiles.writeText(new File(jdir, "20260101-090000-test_left.txt"), "time=2026-01-01 09:00:00\nday=3\nkind=TEST_LEFT\nstation=5.70\nstep.1=replace\t" + r + "jt-d1.txt\t\nstep.2=replace\t" + r + "jt-d2.txt\t\nstep.3=rename\t" + r + "jt-ren-from\t" + r + "jt-ren-to\nstep.4=delete\t" + r + "jt-del.txt\t\n", false);
 // E: and one that can't be told: both ends of a rename are there
 File e1 = new File(root, "jt-e-from"), e2 = new File(root, "jt-e-to"); e1.mkdirs(); e2.mkdirs();
 SafeFiles.writeText(new File(jdir, "20260101-090100-test_stuck.txt"), "time=2026-01-01 09:01:00\nday=3\nkind=TEST_STUCK\nstation=5.70\nstep.1=rename\t" + r + "jt-e-from\t" + r + "jt-e-to\n", false);
 Vault v2 = Vault.open(saves); v2.takeStock();
 Setup.chk("D: at the next opening the note is finished: the waiting bytes moved in, the done ones left alone, the delete done; the note gone", text(d1).equals("after\n") && !new File(root, "jt-d1.txt.tx").exists() && text(d2).equals("done\n")
   && dRenTo.isDirectory() && !dRen.exists() && !dDel.exists() && !new File(jdir, "20260101-090000-test_left.txt").exists());
 EventLog.Entry fin = null, stuck = null;
 for (EventLog.Entry e : EventLog.read(v2)) if (e.kind.equals("JOURNAL")) { if ("finished".equals(e.get("what"))) fin = e; else stuck = e; }
 Setup.chk("D: logged with the note's own time and stardate, finished at start-up", fin != null && fin.time.equals("2026-01-01 09:00:00") && fin.day == 3 && fin.stardate.equals(MasterLog.stardate(3))
   && "startup".equals(fin.get("finished")) && "TEST_LEFT".equals(fin.get("action")) && "5.70".equals(fin.get("left_by")) && fin.num("steps", 0) == 4);
 Setup.chk("E: a note that can't be told stays for a person, and the log says which step and why", stuck != null && "stuck".equals(stuck.get("what")) && new File(jdir, "20260101-090100-test_stuck.txt").isFile()
   && e1.isDirectory() && e2.isDirectory() && stuck.get("detail.1") != null && stuck.get("detail.1").contains("both"));
 Setup.chk("E: the hard rules hold in the journal's words", !fin.human.toLowerCase().contains("beacon") && !stuck.human.toLowerCase().contains("beacon"));
 // S: a Cargo Bay save, Board and Dock go through the journal and leave nothing behind
 Ship d = v2.docked().get(0);
 net.blerf.ftl.parser.SavedGameParser.SavedGameState g = v2.readCopy(d).save; g.getPlayerShip().setScrapAmt(g.getPlayerShip().getScrapAmt() + 5);
 v2.begin().put(d, g).commit();
 Setup.chk("S: a save through the journal: written, no note or waiting bytes left", d.save().getPlayerShip().getScrapAmt() == g.getPlayerShip().getScrapAmt() && !new File(v2.fileOf(d).getParentFile(), v2.fileOf(d).getName() + ".tx").exists() && Journal.dir(v2).listFiles().length == 1);
 int versions = v2.history(d).size();
 v2.board(d);
 Setup.chk("S: Board: continue.sav hers, her file gone, a version kept", v2.boarded() == d && v2.continueFile().isFile() && !ShipStore.sav(v2.folderOf(d)).exists() && v2.history(d).size() == versions + 1 && Journal.dir(v2).listFiles().length == 1);
 v2.dock();
 Setup.chk("S: Dock: her file back, continue.sav gone, nothing left behind", d.state == Ship.State.DOCKED && v2.fileOf(d).isFile() && !v2.continueFile().exists() && Journal.dir(v2).listFiles().length == 1);
 Setup.done();
}
 static String text(File f) throws IOException { return new String(SafeFiles.read(f), "UTF-8"); }
}
