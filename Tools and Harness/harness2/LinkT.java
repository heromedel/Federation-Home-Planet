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

 static void run() throws Exception {
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

  // ---- versions: the protocol decides ----
  a("close"); b("wait ended");
  b("version 4B.99");
  Setup.chk("a station on another version of the program, same protocol: hailed and answered", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));
  b("offer unknown");
  Setup.chk("something a newer station offers that this one doesn't know: refused, not garbled", a("wait refused").equals("OK") && a("state").contains("theirs=1"));
  b("clear");
  a("close"); b("wait ended");
  b("protocol " + (Session.PROTOCOL + 1));
  String rp = a("hail " + port);
  Setup.chk("a different protocol: refused, saying one of them needs to update", rp.startsWith("REFUSED") && rp.contains("update"));
  b("protocol " + Session.PROTOCOL); b("version " + HomePlanet.APP_VERSION);
  Setup.chk("A hails again", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));

  // ---- modes and levels ----
  a("close"); b("wait ended");
  b("mode easy on");
  String r = a("hail " + port);
  Setup.chk("Sandbox and Immersive don't trade: refused, with the reason", r.startsWith("REFUSED") && r.contains("Sandbox Mode") && r.contains("Immersive Easy"));
  a("mode hard on");
  Setup.chk("an Immersive Hard career hails an Easy one (both allow any level)", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));
  a("close"); b("wait ended");
  b("mode easy off");
  r = a("hail " + port);
  Setup.chk("an Easy career that trades only within its level refuses Hard", r.startsWith("REFUSED") && r.contains("different levels"));
  b("mode easy on"); a("mode easy off");
  Setup.chk("two Easy careers trade whatever the setting", a("hail " + port).startsWith("OK") && b("wait open").equals("OK"));
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
