import java.io.*; import java.util.*; import javax.swing.SwingUtilities; import net.blerf.ftl.parser.*; import net.blerf.ftl.parser.SavedGameParser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*; import homeplanet.comm.*;
/**
 * One Home Planet Station for LinkT, driven by text commands: LinkT runs station A in its own process and this as
 * station B in another (each JVM has one vault), over a real localhost link. args: gamedir, saves, station id, title.
 * Reads commands on stdin, answers each with one line "> ...".
 */
public class LinkPeer {
 public static void main(String[] a) throws Exception {
  Station s = new Station(new File(a[0]), new File(a[1]), a[2], a[3]);
  BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
  for (String line; (line = in.readLine()) != null;) {
   String r;
   try { r = s.exec(line.trim()); } catch (Throwable t) { r = "ERROR " + t; }
   System.out.println("> " + r);
   System.out.flush();
   if (line.trim().equals("quit")) break;
  }
  System.exit(0);
 }

 /** A station: its vault, its link, and a record of what the session told it. */
 public static class Station implements Session.View {
  final Vault v; final String id, title;
  volatile Session session; Channel.Post post; Beacon.Responder responder;
  final List<String> notices = Collections.synchronizedList(new ArrayList<String>()), problems = Collections.synchronizedList(new ArrayList<String>());
  volatile int settled = 0; volatile String ended = null;

  public Station(File game, File saves, String id, String title) throws Exception {
   v = Setup.open(game, saves); v.takeStock();
   this.id = id; this.title = title;
  }
  public void changed() { }
  public void notice(String t) { notices.add(t); }
  final List<String> heard = Collections.synchronizedList(new ArrayList<String>());
  public void said(String who, String text) { heard.add(who + "|" + text); }
  volatile boolean chat = true;
  public void problem(String t) { problems.add(t); }
  public void settled(Exchange.Record r) { settled++; }
  public void ended(String why) { ended = why; session = null; ends++; }
  /** How many channels have ended here: a test waits for one to be over before the next hail. */
  volatile int ends = 0;

  /** Whether this station lets whole ships change hands (a normal fleet always does; "noships" plays an Immersive one that doesn't). */
  volatile boolean ships = true;
  /** Its mode as the hello gives it ("mode hard on" plays an Immersive Hard career that trades with any level). */
  volatile String mode = Vault.SANDBOX; volatile boolean anyLevel = true;
  /** The version and protocol its hello claims ("version 4B.99", "protocol 2": a newer station). */
  volatile String version = HomePlanet.APP_VERSION; volatile int protocol = Session.PROTOCOL;
  Wire.Msg hello() { return Session.hello(version, id, title, "", mode, ships, anyLevel).put("protocol", protocol).put("chat", chat); }
  String why(Session.Peer p) { return Session.incompatible(p, HomePlanet.APP_VERSION, id, mode, anyLevel); }

  /** Runs on the event thread, returning what it returns. */
  static <T> T edt(final java.util.concurrent.Callable<T> c) throws Exception {
   final Object[] out = new Object[1]; final Exception[] err = new Exception[1];
   SwingUtilities.invokeAndWait(new Runnable() { public void run() { try { out[0] = c.call(); } catch (Exception e) { err[0] = e; } } });
   if (err[0] != null) throw err[0];
   @SuppressWarnings("unchecked") T t = (T) out[0]; return t;
  }

  public String exec(String cmd) throws Exception {
   final String[] w = cmd.split("\\s+");
   String c = w[0];
   if (c.equals("listen")) {
    post = new Channel.Post(new Channel.Post.Handler() { public void hailed(final Channel ch) {
     try {
      final Session.Peer p = Session.peerOf(ch.readFirst(10000));
      String no = why(p);
      if (no != null) { ch.close(no); return; } // as the screen does: refused, with the reason
      ch.send(hello());
      SwingUtilities.invokeLater(new Runnable() { public void run() { ended = null; session = new Session(ch, false, p, id, ships); session.start(Station.this); } });
     } catch (IOException e) { ch.close(""); }
    } });
    final int port = post.port;
    responder = new Beacon.Responder(new Beacon.Self() { public String answer() { return Beacon.answer(port, HomePlanet.APP_VERSION, id, title, "Wanderer", mode); } });
    return "PORT " + post.port;
   }
   if (c.equals("hail")) {
    Channel ch = Channel.connect("127.0.0.1", Integer.parseInt(w[1]));
    ch.send(hello());
    Wire.Msg r = ch.readFirst(10000);
    if (r.type.equals("BYE")) { ch.close(""); return "REFUSED " + r.get("why"); }
    final Session.Peer p = Session.peerOf(r);
    String no = why(p);
    if (no != null) { ch.close(no); return "REFUSED " + no; }
    final Channel fch = ch;
    edt(new java.util.concurrent.Callable<Void>() { public Void call() { ended = null; session = new Session(fch, true, p, id, ships); session.start(Station.this); return null; } });
    return "OK " + p.title;
   }
   if (c.equals("packages")) { int n = 0; File[] fs = Exchange.dir().listFiles(); if (fs != null) for (File f : fs) if (f.isDirectory()) n++; return "" + n; }
   if (c.equals("commissioned")) { Ship sh = shipNamed(w[1]); if (w.length > 2) Museum.setCommissioned(v, sh.id, cmd.substring(cmd.indexOf(w[2]))); return Museum.commissioned(v, sh.id); }
   if (c.equals("makedesign")) return makeDesign(w[1], w[2]);
   if (c.equals("makeremodel")) return makeRemodel(w[1], w[2]);
   if (c.equals("bp")) { Ship sh = shipNamed(w[1]); if (sh == null) return "none"; sh.invalidate(); SavedGameState g = sh.save(); return g == null ? "unreadable" : g.getPlayerShipBlueprintId(); }
   if (c.equals("blueprints")) { int d = 0, r = CompanionMod.load().size(); for (ShipDesign x : ShipDesign.load()) if (x.built && !x.isWorking()) d++; return "designs=" + d + " remodels=" + r; }
   if (c.equals("known")) return "" + (DataManager.get().getShip(w[1]) != null);
   if (c.equals("starter")) { for (ShipDesign x : ShipDesign.load()) if (DesignExport.bpId(x).equals(w[1]) && !x.isWorking()) return "starter=" + x.starter + " retired=" + x.retired; CompanionMod.Remodel r = CompanionMod.find(CompanionMod.load(), w[1]); return r == null ? "none" : "starter=" + r.starter; }
   if (c.equals("artof")) { for (ShipDesign x : ShipDesign.load()) if (!x.isWorking() && DesignExport.bpId(x).equals(w[1])) return x.art + (ShipArt.load(x.art, "") != null ? " ok" : " missing"); return "none"; }
   if (c.equals("say")) { final String t = cmd.length() > 4 ? cmd.substring(4) : ""; return edt(new java.util.concurrent.Callable<String>() { public String call() { return "" + session.say(t); } }); }
   if (c.equals("heard")) return heard.isEmpty() ? "none" : heard.get(heard.size() - 1);
   if (c.equals("heardcount")) return "" + heard.size();
   if (c.equals("nochat")) { chat = w.length > 1 && w[1].equals("off"); return "OK"; }
   if (c.equals("ends")) return "" + ends;
   if (c.equals("board")) { v.board(shipNamed(w[1])); return "OK"; }
   if (c.equals("dock")) { v.dock(); return "OK"; }
   if (c.equals("boarded")) { Ship b = v.boarded(); return b == null ? "none" : b.name.replace(' ', '_'); }
   if (c.equals("inbox")) { HomePlanet.immersiveNotifications = w[1].equals("on"); return "OK"; }
   if (c.equals("receipts")) { int n = 0; for (Transmissions.Message m : Transmissions.load()) if (Transmissions.isReceipt(m)) n++; return "" + n; }
   if (c.equals("receipt")) { for (Transmissions.Message m : Transmissions.load()) if (Transmissions.isReceipt(m)) return m.from + "|" + m.subject + "|" + m.body.replace('\n', ' '); return "none"; }
   if (c.equals("delreceipt")) { for (Transmissions.Message m : Transmissions.load()) if (Transmissions.isReceipt(m)) { Transmissions.delete(m); return "OK"; } return "none"; }
   if (c.equals("version")) { version = w[1]; return "OK"; }
   if (c.equals("protocol")) { protocol = Integer.parseInt(w[1]); return "OK"; }
   if (c.equals("mode")) { mode = w[1]; anyLevel = w.length < 3 || w[2].equals("on"); return "OK"; }
   if (c.equals("noships")) { ships = w.length > 1 && w[1].equals("off"); return "OK ships=" + ships; }
   if (c.equals("crash")) { Session.crashAt = w.length > 1 ? w[1] : null; return "OK"; }
   if (c.equals("wait")) return waitFor(w[1], w.length > 2 ? w[2] : "");
   if (c.equals("state")) return edt(new java.util.concurrent.Callable<String>() { public String call() {
    Session s = session;
    if (s == null) return "none ended=" + ended + " settled=" + settled;
    return "accept=" + s.iAccepted() + " they=" + s.theyAccepted() + " mine=" + s.mine().size() + " theirs=" + s.theirs().size() + " exch=" + s.exchanging()
      + " why=" + (s.whyNotAccept() == null ? "-" : s.whyNotAccept().replace(' ', '_')) + " settled=" + settled;
   } });
   if (c.equals("hold")) { Ship st = v.storage(); st.invalidate(); return holdText(st.save()); }
   if (c.equals("ship")) { Ship sh = shipNamed(w[1]); if (sh == null) return "none"; sh.invalidate(); return holdText(sh.save()); }
   if (c.equals("fleet")) { List<String> n = new ArrayList<String>(); for (Ship x : v.fleet()) n.add(x.name.replace(' ', '_')); Collections.sort(n); return String.join(",", n); }
   if (c.equals("records")) { List<String> n = new ArrayList<String>(); for (File f : listTrades()) { Exchange.Record r = Exchange.find(f.getName().substring(6, f.getName().length() - 4)); n.add(r == null ? "?" : r.state); } return n.isEmpty() ? "none" : String.join(",", n); }
   if (c.equals("unfinished")) return "" + Exchange.unfinished().size();
   if (c.equals("mark")) { Ship sh = shipNamed(w[1]); TradeMark m = sh == null ? null : TradeMark.of(sh); return m == null ? "none" : "from=" + m.from.replace(' ', '_') + " original=" + m.original.replace(' ', '_') + " defeated=" + m.defeated; }
   if (c.equals("since")) { Ship sh = shipNamed(w[1]); return "defeated=" + TradeMark.defeatedSince(sh, sh.save()) + " beacons=" + TradeMark.beaconsSince(sh, sh.save()) + " lifetime=" + sh.save().getTotalShipsDefeated(); }
   if (c.equals("fate")) { File f = new File(new File(v.historyDir(), w[1]), "fate.txt"); return f.isFile() ? new String(SafeFiles.read(f), "UTF-8").trim().replace('\n', '|') : "none"; }
   if (c.equals("idof")) { Ship sh = shipNamed(w[1]); return sh == null ? "none" : sh.id; }
   if (c.equals("recoverable")) { List<String> n = new ArrayList<String>(); for (Vault.Departed d : v.recoverable()) n.add(d.name.replace(' ', '_')); return n.isEmpty() ? "none" : String.join(",", n); }
   if (c.equals("defeat")) { // FTL's progress on a docked ship: more ships defeated
    Ship sh = shipNamed(w[1]); SavedGameState g = v.readCopy(sh).save; g.setTotalShipsDefeated(g.getTotalShipsDefeated() + Integer.parseInt(w[2])); v.write(sh, g); return "OK";
   }
   if (c.equals("rename")) { Ship sh = shipNamed(w[1]); SavedGameState g = v.readCopy(sh).save; g.setPlayerShipName(w[2]); g.getPlayerShip().setShipName(w[2]); v.write(sh, g); return "OK"; }
   if (c.equals("show")) return edt(new java.util.concurrent.Callable<String>() { public String call() throws Exception { // what the hold has to offer, as the screen shows it
    Session.Show sh = new Session.Show(); sh.hold = true; Ship st = v.storage(); st.invalidate(); ShipState s = st.save().getPlayerShip();
    if (s.getFuelAmt() > 0) sh.lines.add(Line.supply(0, Line.Kind.FUEL, s.getFuelAmt()));
    if (s.getScrapAmt() > 0) sh.lines.add(Line.supply(0, Line.Kind.SCRAP, s.getScrapAmt()));
    for (WeaponState x : s.getWeaponList()) sh.lines.add(Line.item(0, Line.Kind.WEAPON, x.getWeaponId()));
    for (CrewState x : SaveHelper.getOwnCrew(s)) sh.lines.add(Line.crew(0, x));
    session.show(sh); return "OK";
   } });
   if (c.equals("stock")) return stock(w);
   if (c.equals("offer")) return offer(w);
   if (c.equals("clear")) return edt(new java.util.concurrent.Callable<String>() { public String call() { session.clearMine(); return "OK"; } });
   if (c.equals("accept") || c.equals("unaccept")) { final boolean on = c.equals("accept"); return edt(new java.util.concurrent.Callable<String>() { public String call() { session.accept(on); return "OK " + session.iAccepted(); } }); }
   if (c.equals("close")) return edt(new java.util.concurrent.Callable<String>() { public String call() { if (session != null) session.close(title + " closed the channel."); session = null; return "OK"; } });
   if (c.equals("problems")) { String p = problems.isEmpty() ? "none" : problems.get(problems.size() - 1).replace('\n', ' '); return p; }
   if (c.equals("notices")) { return notices.isEmpty() ? "none" : notices.get(notices.size() - 1); }
   if (c.equals("quit")) { if (post != null) post.close(); if (responder != null) responder.close(); return "bye"; }
   return "unknown command " + c;
  }
  /** A design of this station's own (its own hull art, a class name to tell it apart), built, and a ship of it docked. */
  String makeDesign(String cls, String shipName) throws Exception {
   File png = File.createTempFile("hull-", ".png");
   { InputStream in = DataManager.get().getResourceInputStream("img/ship/stealth_base.png"); java.nio.file.Files.copy(in, png.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); in.close(); }
   List<ShipDesign> all = ShipDesign.load();
   ShipDesign d = ShipDesign.create(all); all.add(d);
   d.name = cls;
   int[][] rooms = {{4,5,2,2},{6,5,2,2},{8,5,2,2},{6,4,2,1},{10,5,1,2}};
   for (int[] r : rooms) d.rooms.add(new ShipDesign.Room(r[0], r[1], r[2], r[3]));
   d.doors.add(d.doorFor(6,5,1)); d.doors.add(d.doorFor(8,5,1)); d.doors.add(d.doorFor(10,5,1)); d.doors.add(d.doorFor(6,5,0)); d.doors.add(d.doorFor(4,5,1));
   String[][] sys = {{"pilot","4"},{"engines","0"},{"oxygen","3"},{"shields","1"},{"weapons","2"}};
   for (String[] x : sys) { CompanionMod.Sys y = new CompanionMod.Sys(x[0]); y.room = Integer.parseInt(x[1]); y.power = CompanionMod.usualPower(x[0]); if (ShipDesign.manned(x[0])) { y.square = 0; y.dir = ShipDesign.defaultDir(x[0]); } d.systems.put(x[0], y); }
   d.art = ShipArt.importFile(png, d.id, "base"); d.artX = 4*35 - 80; d.artY = 5*35 - 150; d.mounts.add(new ShipDesign.Mount(400, 120)); d.mounts.add(new ShipDesign.Mount(400, 320));
   d.built = true; d.starter = true;
   CompanionMod.Loadout l = new CompanionMod.Loadout(); l.className = cls + " Class"; l.shipName = shipName; l.crew.put("human", 2); l.weapons.add("LASER_BURST_2"); d.loadout = l;
   ShipDesign snap = ShipDesign.copy(d); snap.snapshotOf = d.id; all.add(snap);
   ShipDesign.save(all);
   CompanionMod.register(CompanionMod.load());
   png.delete();
   return commission(DesignExport.bpId(snap), shipName);
  }
  /** A remodel of the Kestrel A (with a class name of its own, so two stations' differ), and a ship of it docked. */
  String makeRemodel(String cls, String shipName) throws Exception {
   List<CompanionMod.Remodel> all = CompanionMod.load();
   CompanionMod.Remodel r = CompanionMod.create("PLAYER_SHIP_HARD", shipName, all);
   r.loadout = CompanionMod.loadoutOf("PLAYER_SHIP_HARD"); r.loadout.className = cls + " Class";
   all.add(r);
   CompanionMod.save(all);
   CompanionMod.register(CompanionMod.load());
   return commission(r.id, shipName);
  }
  String commission(String bpId, String shipName) throws Exception {
   SavedGameState g = Commission.build(bpId, shipName.replace('_', ' '), net.blerf.ftl.constants.Difficulty.NORMAL, new Random(3));
   Ship sh = v.adopt(g);
   v.setOut(sh, g, "Commissioned at The Home Planet Station");
   return bpId;
  }
  List<File> listTrades() { List<File> out = new ArrayList<File>(); File[] fs = Exchange.dir().listFiles(); if (fs != null) for (File f : fs) if (f.isFile() && f.getName().startsWith("trade-")) out.add(f); Collections.sort(out); return out; }
  Ship shipNamed(String n) { for (Ship s : v.all()) if (s.name.replace(' ', '_').equals(n)) return s; return null; }
  static String holdText(SavedGameState g) {
   ShipState s = g.getPlayerShip();
   List<String> wp = new ArrayList<String>(), cr = new ArrayList<String>();
   for (WeaponState x : s.getWeaponList()) wp.add(x.getWeaponId());
   for (DroneState x : s.getDroneList()) wp.add(x.getDroneId());
   wp.addAll(s.getAugmentIdList());
   for (CrewState x : SaveHelper.getOwnCrew(s)) cr.add(x.getName().replace(' ', '_'));
   Collections.sort(wp); Collections.sort(cr);
   return "scrap=" + s.getScrapAmt() + " fuel=" + s.getFuelAmt() + " missiles=" + s.getMissilesAmt() + " parts=" + s.getDronePartsAmt() + " items=" + String.join(",", wp) + " crew=" + String.join(",", cr);
  }
  /** Puts things in the Cargo Hold directly (the test's setup): stock scrap N | stock weapon ID | stock crew RACE NAME */
  String stock(String[] w) throws Exception {
   Ship st = v.storage(); Vault.Copy cp = v.readCopy(st); ShipState s = cp.save.getPlayerShip();
   if (w[1].equals("scrap")) s.setScrapAmt(s.getScrapAmt() + Integer.parseInt(w[2]));
   else if (w[1].equals("fuel")) s.setFuelAmt(s.getFuelAmt() + Integer.parseInt(w[2]));
   else if (w[1].equals("weapon")) s.getWeaponList().add(SaveHelper.newIdleWeapon(w[2]));
   else if (w[1].equals("crew")) { CrewState c = Commission.volunteer(w[2], new Random(1)); c.setName(w[3]); SaveHelper.placeCrew(s, c, true); s.getCrewList().add(c); }
   v.begin().put(st, cp.save, cp.hash).commit();
   return "OK";
  }
  /** offer weapon ID [@SHIP] | offer supply KIND N [@SHIP] | offer crew NAME [@SHIP] | offer ship NAME | offer raw ID (an id the game doesn't have). From the Cargo Hold unless @SHIP. */
  String offer(String[] w0) throws Exception {
   List<String> w = new ArrayList<String>(Arrays.asList(w0));
   Ship src = v.storage();
   if (w.get(w.size() - 1).startsWith("@")) { src = shipNamed(w.remove(w.size() - 1).substring(1)); }
   if (w.get(1).equals("ship")) src = shipNamed(w.get(2));
   if (src == null) return "no such ship";
   src.invalidate();
   SavedGameState g = src.save();
   Line l;
   String k = w.get(1);
   if (k.equals("weapon") || k.equals("raw")) l = Line.item(0, Line.Kind.WEAPON, w.get(2));
   else if (k.equals("supply")) l = Line.supply(0, Line.Kind.valueOf(w.get(2).toUpperCase()), Integer.parseInt(w.get(3)));
   else if (k.equals("crew")) { CrewState found = null; for (CrewState c : SaveHelper.getOwnCrew(g.getPlayerShip())) if (c.getName().replace(' ', '_').equals(w.get(2))) found = c; if (found == null) return "no such crew"; l = Line.crew(0, found); }
   else if (k.equals("ship")) l = Line.ship(0, g.getPlayerShipBlueprintId(), src.name, "");
   else if (k.equals("unknown")) l = new Line(0, Line.Kind.OTHER, "", 1, null, "telescope", null);
   else return "unknown offer";
   l.from = src.id; l.fromName = src.name;
   final Line fl = l;
   return edt(new java.util.concurrent.Callable<String>() { public String call() { Line added = session.add(fl); return added == null ? "refused" : "OK " + added.n; } });
  }
  String waitFor(String what, String arg) throws Exception {
   long end = System.currentTimeMillis() + 15000;
   while (System.currentTimeMillis() < end) {
    final String w = what, a = arg;
    boolean ok = edt(new java.util.concurrent.Callable<Boolean>() { public Boolean call() {
     Session s = session;
     if (w.equals("open")) return s != null;
     if (w.equals("ended")) return s == null && ended != null;
     if (w.equals("settled")) return settled >= Integer.parseInt(a);
     if (w.equals("ends")) return ends >= Integer.parseInt(a);
     if (s == null) return false;
     if (w.equals("theirs")) return s.theirs().size() == Integer.parseInt(a);
     if (w.equals("they")) return s.theyAccepted();
     if (w.equals("notthey")) return !s.theyAccepted();
     if (w.equals("refused")) return s.whyNotAccept() != null && s.whyNotAccept().contains("can't take");
     return false;
    } });
    if (ok) return "OK";
    Thread.sleep(50);
   }
   return "TIMEOUT " + what;
  }
 }
}
