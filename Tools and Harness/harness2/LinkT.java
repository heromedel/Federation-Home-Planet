import java.io.*; import java.util.*; import homeplanet.core.*; import homeplanet.comm.*; import homeplanet.vault.*;
/**
 * Long Range Comm. between two stations: A in this process, B (LinkPeer) in another, over a real localhost link.
 * Goods, supplies and crew both ways; a change withdrawing acceptance; a line the other station can't take; links cut
 * at each step of the exchange and settled afterwards; whole ships and their trade marks; garbled transmissions.
 * args: gamedir, world saves (from WorldT), work
 */
public class LinkT {
 static BufferedWriter toB; static BufferedReader fromB; static Process proc;
 static LinkPeer.Station A;
 static String b(String cmd) throws IOException {
  toB.write(cmd + "\n"); toB.flush();
  for (String l; (l = fromB.readLine()) != null;) if (l.startsWith("> ")) return l.substring(2);
  return "EOF";
 }
 static String a(String cmd) throws Exception { return A.exec(cmd); }
 static int num(String text, String key) { for (String p : text.split(" ")) if (p.startsWith(key + "=")) return Integer.parseInt(p.substring(key.length() + 1)); return -1; }
 static String field(String text, String key) { for (String p : text.split(" ")) if (p.startsWith(key + "=")) return p.substring(key.length() + 1); return ""; }
 static void both(String cmd) throws Exception { a(cmd); b(cmd); }
 static int bSettled() throws IOException { return num(b("state"), "settled"); }
 /** Both accept (A first), then wait until each has settled one more trade. */
 static void trade(String what) throws Exception {
  int as = A.settled, bs = bSettled();
  trade2();
  Setup.chk(what + ": settled at A", a("wait settled " + (as + 1)).equals("OK"));
  Setup.chk(what + ": settled at B", b("wait settled " + (bs + 1)).equals("OK"));
 }

 public static void main(String[] args) throws Exception {
  File game = new File(args[0]), work = new File(args[2]); SafeFiles.deleteTree(work);
  File sa = new File(work, "a/saves"), sb = new File(work, "b/saves");
  Setup.copyTree(new File(args[1]), sa); Setup.copyTree(new File(args[1]), sb);
  HomePlanet.propFile = new File(work, "a.cfg");
  A = new LinkPeer.Station(game, sa, "aaaaaaaaaaaaaaaa", "Captain Ash");
  proc = new ProcessBuilder("java", "-Djava.awt.headless=true", "-Dhomeplanet.noGameCheck=true", "-cp", System.getProperty("java.class.path"),
    "LinkPeer", game.getPath(), sb.getPath(), "bbbbbbbbbbbbbbbb", "Commander Bree").redirectErrorStream(true).start();
  toB = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream())); fromB = new BufferedReader(new InputStreamReader(proc.getInputStream()));
  try { run(); } finally { try { b("quit"); } catch (IOException e) { } proc.destroy(); }
  Setup.done();
  System.exit(0);
 }

 /** Does A's scan find this station? */
 static boolean finds(String station) {
  for (Beacon.Found f : Beacon.scan(1200, "aaaaaaaaaaaaaaaa")) if (f.station.equals(station)) return true;
  return false;
 }
 /** Asks the bare question an older station's scan asks (no id after it): does this station answer? */
 static boolean bareAnswer(String station) throws IOException {
  java.net.DatagramSocket s = new java.net.DatagramSocket();
  try {
   byte[] q = "FHP-LRC?".getBytes("UTF-8");
   for (int i = 0; i < Channel.PORTS; i++) s.send(new java.net.DatagramPacket(q, q.length, java.net.InetAddress.getLoopbackAddress(), Channel.PORT0 + i));
   s.setSoTimeout(1500);
   byte[] buf = new byte[1024];
   while (true) {
    java.net.DatagramPacket p = new java.net.DatagramPacket(buf, buf.length);
    try { s.receive(p); } catch (java.net.SocketTimeoutException e) { return false; }
    if (new String(p.getData(), 0, p.getLength(), "UTF-8").contains(station)) return true;
   }
  } finally { s.close(); }
 }
 static void run() throws Exception {
  // ---- hailing frequencies: a station is found and hailed only once they're open ----
  boolean early = false;
  for (Beacon.Found f : Beacon.scan(1200, "aaaaaaaaaaaaaaaa")) if (f.station.equals("bbbbbbbbbbbbbbbb")) early = true;
  Setup.chk("a station with its hailing frequencies closed isn't found", !early);
  boolean reached = false;
  for (int i = 0; i < Channel.PORTS; i++) { try { a("hail " + (Channel.PORT0 + i)); reached = true; } catch (IOException e) { } }
  Setup.chk("nor can it be hailed", !reached);
  boolean noted = false;
  for (int i = 0; i < Channel.PORTS; i++) if (a("note " + (Channel.PORT0 + i) + " normal aaaaaaaaaaaaaaaa Anyone there?").startsWith("OK")) noted = true;
  Setup.chk("nor sent a message", !noted);
  String port = b("listen").replace("PORT ", "");
  boolean seen = false;
  for (Beacon.Found f : Beacon.scan(1500, "aaaaaaaaaaaaaaaa")) if (f.station.equals("bbbbbbbbbbbbbbbb") && f.title.equals("Commander Bree") && ("" + f.port).equals(port)) seen = true;
  Setup.chk("A's scan finds B: its commander and where to hail it", seen);
  Setup.chk("A hails B", a("hail " + port).startsWith("OK Commander Bree"));
  Setup.chk("B answers", b("wait open").equals("OK"));

  // ---- goods, supplies and crew, both ways ----
  a("stock weapon LASER_BURST_2"); a("stock scrap 100");
  b("stock fuel 20"); b("stock crew engi Ripley");
  String aHold = a("hold"), bHold = b("hold");
  a("offer weapon LASER_BURST_2"); a("offer supply scrap 30");
  b("offer crew Ripley"); b("offer supply fuel 5");
  Setup.chk("each sees the other's lines", b("wait theirs 2").equals("OK") && a("wait theirs 2").equals("OK"));
  a("accept");
  Setup.chk("B sees A accept", b("wait they").equals("OK"));
  b("offer supply fuel 1");
  a("wait theirs 3");
  Setup.chk("a change on B withdraws A's acceptance", a("state").contains("accept=false") && b("wait notthey").equals("OK"));
  trade("goods, supplies and crew");
  String aNow = a("hold"), bNow = b("hold");
  Setup.chk("A gave the laser and 30 scrap", !field(aNow, "items").contains("LASER_BURST_2") && num(aNow, "scrap") == num(aHold, "scrap") - 30);
  Setup.chk("A received Ripley and 6 fuel", field(aNow, "crew").contains("Ripley") && num(aNow, "fuel") == num(aHold, "fuel") + 6);
  Setup.chk("B received the laser and 30 scrap", field(bNow, "items").contains("LASER_BURST_2") && num(bNow, "scrap") == num(bHold, "scrap") + 30);
  Setup.chk("B gave Ripley and 6 fuel", !field(bNow, "crew").contains("Ripley") && num(bNow, "fuel") == num(bHold, "fuel") - 6);
  Setup.chk("both records done", a("records").equals("done") && b("records").equals("done"));
  Setup.chk("offers empty after the trade", a("state").contains("mine=0 theirs=0"));

  // ---- a line the other station can't take ----
  a("offer raw NOT_A_WEAPON");
  Setup.chk("B refuses an unknown item; A can't accept", b("wait refused").equals("OK") && a("wait refused").equals("OK"));
  a("clear");

  // ---- B crashes as the exchange begins: A calls it off ----
  b("crash PREPARE");
  aHold = a("hold");
  a("offer supply scrap 10"); b("offer supply fuel 1");
  b("wait theirs 1"); a("wait theirs 1");
  a("accept"); b("wait they"); b("accept");
  Setup.chk("A sees the link lost", a("wait ended").equals("OK"));
  Setup.chk("A's scrap is back in the Cargo Hold", num(a("hold"), "scrap") == num(aHold, "scrap"));
  Setup.chk("A's record called off, none left unfinished", a("records").endsWith("called-off") && a("unfinished").equals("0"));
  Setup.chk("B made no record", b("records").equals("done"));

  // ---- B crashes before COMMIT: A completes, B settles on the next link ----
  b("crash COMMIT");
  Setup.chk("A hails again", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));
  aHold = a("hold"); bHold = b("hold");
  a("offer supply scrap 10"); b("offer supply fuel 2");
  trade2();
  Setup.chk("A completed", a("wait ended").equals("OK") && num(a("hold"), "scrap") == num(aHold, "scrap") - 10 && num(a("hold"), "fuel") == num(aHold, "fuel") + 2);
  Setup.chk("B left in escrow (fuel gone, no scrap yet)", b("unfinished").equals("1") && num(b("hold"), "fuel") == num(bHold, "fuel") - 2 && num(b("hold"), "scrap") == num(bHold, "scrap"));
  b("crash");
  int bs = bSettled();
  Setup.chk("A hails a third time", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));
  Setup.chk("B settles: done, the scrap arrives", b("wait settled " + (bs + 1)).equals("OK") && b("unfinished").equals("0") && num(b("hold"), "scrap") == num(bHold, "scrap") + 10);

  // ---- A (the leader) crashes before READY: both call it off on the next link ----
  a("crash READY");
  aHold = a("hold"); bHold = b("hold");
  a("offer supply scrap 5"); b("offer supply fuel 1");
  trade2();
  Setup.chk("B sees the link lost", b("wait ended").equals("OK"));
  Setup.chk("both left in escrow", a("unfinished").equals("1") && b("unfinished").equals("1"));
  a("crash");
  bs = bSettled();
  Setup.chk("A hails again after its crash", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));
  b("wait settled " + (bs + 1));
  Setup.chk("both called off: nothing changed hands", a("unfinished").equals("0") && b("unfinished").equals("0")
    && num(a("hold"), "scrap") == num(aHold, "scrap") && num(b("hold"), "fuel") == num(bHold, "fuel"));

  // ---- declining and blocking ----
  a("close"); b("wait ended");
  b("decline on");
  String dr = a("hail " + port);
  Setup.chk("a declined hail: the hailer is told the commander is busy", dr.startsWith("REFUSED") && dr.contains("Commander Bree is busy"));
  b("decline off");
  b("block aaaaaaaaaaaaaaaa Captain_Ash 127.0.0.1:50000");
  Setup.chk("a block keeps no address from this computer or a home network (another station there would go with it)", b("blocks").equals("aaaaaaaaaaaaaaaa|Captain Ash|"));
  Setup.chk("a blocked commander's search gets no answer", !finds("bbbbbbbbbbbbbbbb"));
  String br = a("hail " + port);
  Setup.chk("a blocked commander's hail goes unanswered, as if nobody were listening", br.startsWith("REFUSED") && br.contains("did not answer") && !br.contains("block"));
  Setup.chk("an older station's search (the bare question) is still answered", bareAnswer("bbbbbbbbbbbbbbbb"));
  b("unblock aaaaaaaaaaaaaaaa");
  Setup.chk("unblocked: found again", finds("bbbbbbbbbbbbbbbb"));
  Setup.chk("and hailed again", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));
  Blocks.block("cccccccccccccccc", "Captain Pest", "203.0.113.5:47610");
  Setup.chk("an address from beyond the home network is kept with a block, and blocks by itself", Blocks.blocked(null, "203.0.113.5") && Blocks.blocked("dddddddddddddddd", "203.0.113.5:47611") && !Blocks.blocked("dddddddddddddddd", "203.0.113.6"));
  Blocks.unblock("cccccccccccccccc");
  Setup.chk("unblocking clears it", !Blocks.blocked(null, "203.0.113.5") && Blocks.list().isEmpty());

  // ---- messages without a channel ----
  b("inbox on");
  boolean takes = false;
  for (Beacon.Found f : Beacon.scan(1200, "aaaaaaaaaaaaaaaa")) if (f.station.equals("bbbbbbbbbbbbbbbb")) takes = f.notes;
  Setup.chk("a station's search answer says it takes messages", takes);
  b("oldanswer on");
  boolean oldTakes = true;
  for (Beacon.Found f : Beacon.scan(1200, "aaaaaaaaaaaaaaaa")) if (f.station.equals("bbbbbbbbbbbbbbbb")) oldTakes = f.notes;
  Setup.chk("an older station's answer doesn't (so Send Message stays off for it)", !oldTakes);
  b("oldanswer off");
  int notes0 = Integer.parseInt(b("notes").split(" ")[0]);
  Setup.chk("a message goes to the inbox", a("note " + port + " normal aaaaaaaaaaaaaaaa Fancy a trade later?").equals("OK inbox"));
  String nb = b("notes");
  Setup.chk("from the commander who sent it, as they wrote it", Integer.parseInt(nb.split(" ")[0]) == notes0 + 1 && nb.contains("Captain Ash|Long Range message|Fancy a trade later?"));
  Setup.chk("and can be deleted", b("delnote").equals("OK") && Integer.parseInt(b("notes").split(" ")[0]) == notes0);
  Setup.chk("a priority message pops up", a("note " + port + " priority aaaaaaaaaaaaaaaa Are you there?").equals("OK popup") && b("popups").equals("1 Captain Ash|Are you there?"));
  Setup.chk("a second within the minute goes to the inbox", a("note " + port + " priority aaaaaaaaaaaaaaaa Hello?").equals("OK inbox") && b("popups").startsWith("1 ")
    && b("notes").contains("Long Range message (priority)"));
  b("popups off");
  Setup.chk("with priority pop-ups off, a priority message goes to the inbox", a("note " + port + " priority cccccccccccccccc Urgent!").equals("OK inbox") && b("popups").startsWith("1 "));
  b("popups on"); b("inbox off");
  int inboxed = Integer.parseInt(b("notes").split(" ")[0]);
  Setup.chk("with Immersive messages off, a commander's message still lands in the inbox", a("note " + port + " normal dddddddddddddddd Just saying hi").equals("OK inbox")
    && Integer.parseInt(b("notes").split(" ")[0]) == inboxed + 1 && b("popups").startsWith("1 "));
  b("inbox on");
  b("block eeeeeeeeeeeeeeee Captain_Pest");
  String nr = a("note " + port + " normal eeeeeeeeeeeeeeee Let me in");
  Setup.chk("a blocked commander's message is dropped: they hear only that nobody answered", nr.startsWith("FAILED") && nr.contains("did not answer") && !nr.contains("block"));
  b("unblock eeeeeeeeeeeeeeee");
  int ok = 0, busy = 0;
  for (int i = 0; i < 8; i++) { String x = a("note " + port + " normal ffffffffffffffff Spam " + i); if (x.startsWith("OK")) ok++; else if (x.contains("too many")) busy++; }
  Setup.chk("a flood is cut down (" + ok + " of 8 taken)", ok == 5 && busy == 3);
  Setup.chk("a message stays plain text, cut to length", Notes.clean("a\u0007b\n\n\n\nc" + new String(new char[600]).replace('\0', 'x')).startsWith("ab\n\nc") && Notes.clean(new String(new char[600]).replace('\0', 'x')).length() == Notes.MAX);

  // ---- the Outbox: messages wait for a station that can't be reached ----
  b("unlisten");
  Setup.chk("a message for a station with its frequencies closed can wait in the Outbox", a("outbox add bbbbbbbbbbbbbbbb Commander_Bree " + port + " Back later? I've got that laser.").equals("OK") && a("outbox count").equals("1"));
  Setup.chk("nothing goes while their frequencies stay closed", a("outbox deliver").equals("nothing") && a("outbox count").equals("1"));
  Setup.chk("it's kept on disk (a restart finds it)", Outbox.list().size() == 1 && Outbox.list().get(0).text.equals("Back later? I've got that laser."));
  a("outbox age 180");
  port = b("listen").replace("PORT ", "");
  int notesBefore = Integer.parseInt(b("notes").split(" ")[0]);
  String dl = a("outbox deliver");
  Setup.chk("once they open their frequencies, it's delivered and leaves the Outbox", dl.startsWith("Delivered to Commander Bree's inbox") && dl.contains("waited 3 hours") && a("outbox count").equals("0"));
  String arrived = b("notewith Back_later?");
  Setup.chk("it arrives saying when it was written", Integer.parseInt(b("notes").split(" ")[0]) == notesBefore + 1 && arrived.contains("Back later? I've got that laser.") && arrived.contains("it waited in their Outbox"));
  a("outbox add bbbbbbbbbbbbbbbb Commander_Bree " + port + " Never mind.");
  Setup.chk("Cancel takes it out, and nothing is sent", a("outbox cancel").equals("OK") && a("outbox count").equals("0") && a("outbox deliver").equals("nothing"));
  // a station that blocked you doesn't answer your search: the message just waits. One that answers and turns it away
  // (here, too many messages from that commander this minute) stops it trying
  for (int i = 0; i < 6; i++) a("note " + port + " normal abababababababab Hi " + i);
  a("outbox add bbbbbbbbbbbbbbbb Commander_Bree " + port + " Are you there?");
  String ta = a("outbox deliver as abababababababab");
  Setup.chk("a message their station turns away stops trying, and says why", ta.contains("turned away") && ta.contains("too many") && a("outbox refused").equals("1")
    && a("outbox count").equals("1") && a("outbox deliver").equals("nothing"));
  a("outbox again");
  Setup.chk("Try again sends it once more", a("outbox deliver").startsWith("Delivered") && a("outbox count").equals("0"));
  String full = "";
  for (int i = 0; i < 6; i++) full = a("outbox add cccccccccccccccc Captain_Pest " + port + " number " + i);
  Setup.chk("at most 5 wait for one commander", full.startsWith("FULL") && a("outbox count").equals("5"));
  for (int i = 0; i < 5; i++) a("outbox cancel");
  b("inbox off");

  // ---- versions: the protocol decides ----
  a("close"); b("wait ended");
  b("version 4B.99");
  Setup.chk("a station on another version of the program, same protocol: hailed and answered", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));
  b("offer unknown");
  Setup.chk("something a newer station offers that this one doesn't know: refused, not garbled", a("wait refused").equals("OK") && a("state").contains("theirs=1"));
  b("clear");
  a("close"); b("wait ended");
  b("protocol " + (Session.PROTOCOL + 1));
  int bEnds = Integer.parseInt(b("ends"));
  String rp = a("hail " + port);
  Setup.chk("a different protocol: refused, saying one of them needs to update", rp.startsWith("REFUSED") && rp.contains("update"));
  b("wait ends " + (bEnds + 1)); // B answered before A refused: its side of that channel closes first
  b("protocol " + Session.PROTOCOL); b("version " + HomePlanet.APP_VERSION);
  Setup.chk("A hails again", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));

  String bRecords0 = b("records");
  // ---- modes and levels: any two stations talk; only matching ones trade ----
  a("close"); b("wait ended");
  b("mode easy on");
  Setup.chk("Sandbox hails an Immersive career: the channel opens, to talk", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));
  String sa = a("state"), sb = b("state");
  Setup.chk("both know it's communications only, and why (a sector too distant for trade, then the mode)", sa.contains("talkonly=true") && sb.contains("talkonly=true")
    && sa.contains("too_distant_for_trade") && sa.contains("Sandbox_fleets_trade_only_with_Sandbox_fleets") && sb.contains("Immersive_Easy"));
  Setup.chk("messages go both ways", a("say Hello from Sandbox").equals("true") && waitHeard("Captain Ash|Hello from Sandbox")
    && b("say Hello from Easy").equals("true") && waitA("Commander Bree|Hello from Easy"));
  Setup.chk("neither can offer anything", a("offer supply scrap 5").equals("refused") && b("offer supply fuel 1").equals("refused") && a("state").contains("mine=0 theirs=0"));
  a("accept");
  Setup.chk("nor accept", a("state").contains("accept=false"));
  a("close"); b("wait ended");
  b("tradeanyway on");
  a("hail " + port); b("wait open");
  Setup.chk("a station that offers anyway: its lines are refused, and nothing can be accepted", b("offer supply fuel 1").startsWith("OK") && a("wait theirs 1").equals("OK")
    && b("wait refused").equals("OK") && a("state").contains("talkonly=true"));
  b("accept"); a("accept"); Thread.sleep(800);
  Setup.chk("so nothing changes hands", b("records").equals(bRecords0) && a("state").contains("accept=false"));
  a("close"); b("wait ended"); b("tradeanyway off");
  a("mode hard on");
  Setup.chk("an Immersive Hard career hails an Easy one (both allow any level): they trade", a("hail " + port).startsWith("OK") && b("wait open").equals("OK") && a("state").contains("talkonly=false"));
  a("close"); b("wait ended");
  b("mode easy off");
  Setup.chk("an Easy career that trades only within its level: Hard can still hail it, to talk", a("hail " + port).startsWith("OK") && b("wait open").equals("OK")
    && a("state").contains("different_levels") && b("state").contains("talkonly=true"));
  a("close"); b("wait ended");
  b("mode easy on"); a("mode easy off");
  Setup.chk("two Easy careers trade whatever the setting", a("hail " + port).startsWith("OK") && b("wait open").equals("OK") && a("state").contains("talkonly=false"));
  a("close"); b("wait ended");
  a("mode sandbox"); b("mode sandbox");
  Setup.chk("back to Sandbox: A hails B", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));

  // ---- who may trade whole ships ----
  boolean im = HomePlanet.immersiveMode, st = HomePlanet.immersiveShipTrading;
  HomePlanet.immersiveMode = false; HomePlanet.immersiveShipTrading = false;
  boolean normal = homeplanet.ui.LongRangeCommUI.shipsAllowed();
  HomePlanet.immersiveMode = true;
  boolean immersiveOff = homeplanet.ui.LongRangeCommUI.shipsAllowed();
  HomePlanet.immersiveShipTrading = true;
  boolean immersiveOn = homeplanet.ui.LongRangeCommUI.shipsAllowed();
  HomePlanet.immersiveMode = im; HomePlanet.immersiveShipTrading = st;
  Setup.chk("ships: a Sandbox fleet always may, an Immersive career by its setting", normal && !immersiveOff && immersiveOn);
  a("close"); b("wait ended"); b("noships");
  Setup.chk("A hails a station that doesn't allow ships", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));
  a("offer ship Test_Federation");
  Setup.chk("B refuses a whole ship; A can't accept", a("wait refused").equals("OK"));
  a("close"); b("wait ended"); b("noships off");
  Setup.chk("A hails again (ships allowed)", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));

  // ---- whole ships ----
  a("offer ship Test_Kestrel"); b("offer supply fuel 1");
  b("wait theirs 1"); a("wait theirs 1"); a("accept"); b("wait they"); b("accept");
  Thread.sleep(1500);
  Setup.chk("the boarded ship can't change hands", a("problems").contains("must be docked") && a("fleet").contains("Test_Kestrel"));
  a("clear"); b("clear");

  // ---- changing ships mid-offer: what's offered stays, taken from wherever she is now ----
  a("inbox on");
  int kFuel = num(a("ship Test_Kestrel"), "fuel");
  a("offer supply fuel 2 @Test_Kestrel"); b("offer supply scrap 3");
  a("board Test_Engi");
  Setup.chk("A boards another ship mid-offer (the Kestrel docked)", a("boarded").equals("Test_Engi"));
  trade("goods from a ship since docked");
  Setup.chk("the fuel came off the docked Kestrel", num(a("ship Test_Kestrel"), "fuel") == kFuel - 2);
  a("dock");
  Setup.chk("A docks her ship: none at command", a("boarded").equals("none"));
  a("board Test_Kestrel");
  String receipt = a("receipt");
  Setup.chk("the receipt is the Quartermaster's, titled as a receipt", receipt.startsWith("Home Planet Quartermaster|Receipt of Transfer: Signed by Quartermaster|"));
  Setup.chk("it names the other commander and what came and went", receipt.contains("Commander Bree") && receipt.contains("3 scrap") && receipt.contains("2 fuel"));
  int receipts = Integer.parseInt(a("receipts"));
  Setup.chk("a receipt can be deleted", a("delreceipt").equals("OK") && Integer.parseInt(a("receipts")) == receipts - 1);
  a("inbox off");
  a("rename Test_Engi Wanderer"); // B has a Test Engi of its own
  a("commissioned Wanderer 30 September 2026");
  String engiA = a("idof Wanderer");
  a("offer ship Wanderer"); b("offer supply fuel 1");
  trade("a whole ship");
  Setup.chk("A's Engi left her fleet", !a("fleet").contains("Wanderer"));
  Setup.chk("A records her transferred to Commander Bree", a("fate " + engiA).equals("TRANSFERRED|Wanderer|Commander Bree"));
  Setup.chk("A can't recover a traded ship", !a("recoverable").contains("Wanderer"));
  Setup.chk("B's fleet has her", b("fleet").contains("Wanderer"));
  Setup.chk("her mark: from and first commissioned by Captain Ash", b("mark Wanderer").startsWith("from=Captain_Ash original=Captain_Ash"));
  Setup.chk("her commission date came with her", b("commissioned Wanderer").equals("30 September 2026"));
  Setup.chk("a settled trade leaves no packages behind (A and B)", a("packages").equals("0") && b("packages").equals("0"));
  b("defeat Wanderer 3");
  String since = b("since Wanderer");
  Setup.chk("only what she does for B counts", num(since, "defeated") == 3 && num(since, "lifetime") >= 3);
  // and back again
  b("offer ship Wanderer"); a("offer supply fuel 1");
  trade("the ship back again");
  Setup.chk("she's back with A", a("fleet").contains("Wanderer") && !b("fleet").contains("Wanderer"));
  Setup.chk("her new mark: from Commander Bree, original owner still Captain Ash", a("mark Wanderer").startsWith("from=Commander_Bree original=Captain_Ash"));
  since = a("since Wanderer");
  Setup.chk("her kills for B don't count for A", num(since, "defeated") == 0 && num(since, "lifetime") >= 3);
  Setup.chk("a new id, not her old one", !a("idof Wanderer").equals(engiA));
  Setup.chk("her commission date survives the trip back", a("commissioned Wanderer").equals("30 September 2026"));

  // ---- custom ships: their papers travel, and fit in at the other station ----
  String aDesign = a("makedesign Hawk Kite"), aRemodel = a("makeremodel Alpha Lark");
  String bDesign = b("makedesign Owl Moth"), bRemodel = b("makeremodel Beta Gull");
  Setup.chk("both stations drew up blueprints under the same numbers (different ships)", aDesign.equals(bDesign) && aRemodel.equals(bRemodel) && aDesign.contains("DESIGN_"));
  String bBefore = b("blueprints");
  a("offer ship Kite"); a("offer ship Lark"); b("offer supply fuel 1");
  trade("a designed and a remodeled ship");
  String kiteBp = b("bp Kite"), larkBp = b("bp Lark");
  Setup.chk("B has both ships, readable", b("fleet").contains("Kite") && b("fleet").contains("Lark") && kiteBp.startsWith("PLAYER_SHIP_") && larkBp.startsWith("PLAYER_SHIP_"));
  Setup.chk("renumbered at B: neither takes B's own blueprint's number", !kiteBp.equals(bDesign) && !larkBp.equals(bRemodel) && kiteBp.contains("DESIGN_") && larkBp.contains("_R"));
  Setup.chk("B's own ships still fly their own blueprints", b("bp Moth").equals(bDesign) && b("bp Gull").equals(bRemodel));
  Setup.chk("the new blueprints are in B's game data", b("known " + kiteBp).equals("true") && b("known " + larkBp).equals("true"));
  Setup.chk("they arrived as ships, not plans: not commissionable", b("starter " + kiteBp).equals("starter=false retired=true") && b("starter " + larkBp).equals("starter=false"));
  Setup.chk("B now has one more design and one more remodel", !b("blueprints").equals(bBefore));
  Setup.chk("her hull art came with her, filed under her new number", b("artof " + kiteBp).startsWith("file:art/" + kiteBp.replace("PLAYER_SHIP_", "").replace("_HP", "")) && b("artof " + kiteBp).endsWith(" ok"));
  String aBefore = a("blueprints");
  b("offer ship Kite"); b("offer ship Lark"); a("offer supply fuel 1");
  trade("both ships home again");
  Setup.chk("home again, they fly A's own blueprints", a("bp Kite").equals(aDesign) && a("bp Lark").equals(aRemodel));
  Setup.chk("and A made no copies of them", a("blueprints").equals(aBefore));
  // papers that aren't right are refused before anything is installed
  java.util.Map<String, byte[]> papers = homeplanet.parser.ShipPapers.papersOf(aDesign);
  java.util.Map<String, byte[]> bad = new java.util.LinkedHashMap<String, byte[]>(papers);
  bad.put("blueprint.xml", new String(papers.get("blueprint.xml"), "UTF-8").replace("id=\"DESIGN_1\"", "id=\"DESIGN_9\"").getBytes("UTF-8"));
  boolean refused = false; try { homeplanet.parser.ShipPapers.check(bad, aDesign); } catch (IOException e) { refused = true; }
  Setup.chk("a blueprint that isn't the one her save names is refused", refused);
  bad = new java.util.LinkedHashMap<String, byte[]>(papers);
  for (String k : papers.keySet()) if (k.startsWith("art/")) bad.put(k, "not a picture".getBytes("UTF-8"));
  refused = false; try { homeplanet.parser.ShipPapers.check(bad, aDesign); } catch (IOException e) { refused = true; }
  Setup.chk("a picture that isn't a picture is refused", refused);
  bad = new java.util.LinkedHashMap<String, byte[]>(papers);
  for (String k : papers.keySet()) if (k.startsWith("art/")) bad.remove(k);
  refused = false; try { homeplanet.parser.ShipPapers.check(bad, aDesign); } catch (IOException e) { refused = true; }
  Setup.chk("a blueprint missing its pictures is refused", refused);
  String before = a("blueprints");
  refused = false; try { homeplanet.parser.ShipPapers.install(bad, new byte[0], aDesign); } catch (IOException e) { refused = true; }
  Setup.chk("a refused install leaves nothing behind", refused && a("blueprints").equals(before));

  // ---- messages between the commanders ----
  Setup.chk("a message reaches the other commander, with who sent it", a("say Hello Bree, fair trade?").equals("true") && waitHeard("Captain Ash|Hello Bree, fair trade?"));
  Setup.chk("an empty message isn't sent", a("say    ").equals("false"));
  StringBuilder longer = new StringBuilder(); for (int i = 0; i < 30; i++) longer.append("0123456789");
  a("say " + longer);
  Thread.sleep(500);
  Setup.chk("a long message is cut to " + Session.SAY_MAX + " letters", b("heard").length() == "Captain Ash|".length() + Session.SAY_MAX);
  int had = Integer.parseInt(b("heardcount"));
  for (int i = 0; i < 20; i++) a("say flood " + i);
  Thread.sleep(800);
  int got = Integer.parseInt(b("heardcount")) - had;
  Setup.chk("a flood of messages is cut down (" + got + " of 20 shown)", got > 0 && got <= 8);
  a("close"); b("wait ended"); b("nochat");
  Setup.chk("A hails a station too old for messages", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));
  Setup.chk("A can't send it messages (they'd go unseen)", a("say anyone there?").equals("false"));
  a("close"); b("wait ended"); b("nochat off");
  Setup.chk("A hails again", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));

  // ---- garbled transmissions ----
  Setup.chk("an oversized frame is garbled", garbled(new byte[] {0x7f, 0, 0, 0}));
  Setup.chk("a cut-short message is garbled", garbled(new byte[] {0, 0, 0, 3, 0, 5, 'H'}));
  Setup.chk("a bad message type is garbled", garbled(frame("bad type")));
  Wire.Msg crew = new Wire.Msg("OFFER").put("lines", 1).put("l0.n", 1).put("l0.kind", "crew").put("l0.c.name", "Huge").put("l0.c.race", "human")
    .put("l0.c.male", true).put("l0.c.health", 99999).put("l0.c.mastery", "000000000000").put("l0.c.repairs", 1).put("l0.c.kills", 1).put("l0.c.evasions", 1)
    .put("l0.c.jumps", 1).put("l0.c.masteries", 0).put("l0.c.tints", "");
  for (int i = 0; i < 6; i++) crew.put("l0.c.s" + i, 100000);
  Line huge = Line.readLines(crew).get(0);
  Setup.chk("crew from another station are kept within FTL's limits", huge.crew.getHealth() == 100 && huge.crew.getPilotSkill() < 100);
  crew.put("l0.c.race", "dragon");
  boolean caught = false; try { Line.readLines(crew); } catch (Wire.Garbled e) { caught = true; }
  Setup.chk("an unknown race is garbled", caught);
  a("close");
 }
 /** Waits for B to have heard this message. */
 static boolean waitA(String what) throws Exception {
  for (int i = 0; i < 100; i++) { if (!A.heard.isEmpty() && A.heard.get(A.heard.size() - 1).equals(what)) return true; Thread.sleep(50); }
  return false;
 }
 static boolean waitHeard(String what) throws Exception {
  for (int i = 0; i < 100; i++) { if (b("heard").equals(what)) return true; Thread.sleep(50); }
  return false;
 }
 /** Both accept (A first). */
 static void trade2() throws Exception {
  b("wait theirs " + A.session.mine().size());
  a("wait theirs " + Integer.parseInt(field(b("state"), "mine")));
  a("accept"); b("wait they"); b("accept");
  Thread.sleep(800);
 }
 static byte[] frame(String type) throws IOException {
  ByteArrayOutputStream bo = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(bo);
  ByteArrayOutputStream body = new ByteArrayOutputStream(); DataOutputStream db = new DataOutputStream(body);
  db.writeUTF(type); db.writeShort(0); db.writeShort(0);
  d.writeInt(body.size()); body.writeTo(d); return bo.toByteArray();
 }
 static boolean garbled(byte[] bytes) {
  try { Wire.read(new ByteArrayInputStream(bytes)); return false; }
  catch (Wire.Garbled e) { return true; }
  catch (IOException e) { return false; }
 }
}
