import java.io.*; import java.util.*; import java.util.zip.*; import net.blerf.ftl.parser.*; import homeplanet.core.*; import homeplanet.parser.*; import homeplanet.vault.*;
/** The patch state (PatchState): is a blueprint in FTL's data as the station would write it now? A built design with the game's art, one with her own pictures, a plain copy; the mod laid over the game data as Slipstream would. args: gamedir, world saves (from WorldT), work */
public class PatchT { public static void main(String[] a) throws Exception {
 File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
 File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
 Vault v = Setup.open(game, saves); v.takeStock();
 File png = new File(work, "hull.png");
 { InputStream in = DataManager.get().getResourceInputStream("img/ship/stealth_base.png"); java.nio.file.Files.copy(in, png.toPath()); in.close(); }
 List<ShipDesign> all = ShipDesign.load();
 // two built designs: one on the Kestrel's own art, one with her own picture (and so her own files in the mod)
 ShipDesign[] built = new ShipDesign[2];
 for (int k = 0; k < 2; k++) {
  ShipDesign d = ShipDesign.create(all); d.name = k == 0 ? "Patch Kestrel" : "Patch Stealth";
  ShipDesign.fromGameShip(d, "PLAYER_SHIP_HARD");
  if (k == 1) { d.art = ShipArt.importFile(png, d.id, "base"); d.floor = ""; d.artScale = 100; d.mounts.clear(); d.mounts.add(new ShipDesign.Mount(400, 120)); d.mounts.add(new ShipDesign.Mount(400, 320)); }
  d.built = true; d.starter = true;
  all.add(d);
  ShipDesign snap = ShipDesign.copy(d); snap.snapshotOf = d.id; all.add(snap);
  built[k] = snap;
 }
 ShipDesign.save(all);
 List<CompanionMod.Remodel> remodels = CompanionMod.load();
 CompanionMod.register(remodels);
 String kid = DesignExport.bpId(built[0]), sid = DesignExport.bpId(built[1]);
 Setup.chk("P: before any patch: the new designs aren't in the game", !CompanionMod.inGameData(kid) && !CompanionMod.inGameData(sid)); // (the plain copies may be: the test's ftl.dat can be a patched one)
 Setup.chk("P: the game's own Kestrel is", CompanionMod.inGameData("PLAYER_SHIP_HARD"));
 File overlay = new File(work, "overlay");
 patch(remodels, overlay);
 Setup.chk("P: patched (the mod laid over the game data): all three are in", CompanionMod.inGameData(kid) && CompanionMod.inGameData(sid) && CompanionMod.inGameData("PLAYER_SHIP_HARD_HP"));
 net.blerf.ftl.parser.SavedGameParser.SavedGameState gs = Commission.build(sid, "Patched", net.blerf.ftl.constants.Difficulty.NORMAL, new Random(2));
 File sav = new File(work, "patched.sav"); SafeFiles.write(sav, SaveHelper.toBytes(gs));
 Setup.chk("P: a save of her: no blueprint missing", Retrofit.missingBlueprints(sav).isEmpty());
 // a mount moved in place (the kind of change the old rooms-and-doors compare never saw)
 built[1].mounts.get(0).x += 30; ShipDesign.save(all); CompanionMod.register(remodels);
 Setup.chk("P: a weapon mount moved in place: she's no longer what the game has; the Kestrel design still is", !CompanionMod.inGameData(sid) && CompanionMod.inGameData(kid) && !Retrofit.missingBlueprints(sav).isEmpty());
 patch(remodels, overlay);
 Setup.chk("P: patched again: she's in", CompanionMod.inGameData(sid));
 // her art moved a pixel: her chassis file changes, so does the answer
 built[1].artX += 1; ShipDesign.save(all); CompanionMod.register(remodels);
 Setup.chk("P: her art nudged: not in the game until patched", !CompanionMod.inGameData(sid));
 patch(remodels, overlay);
 Setup.chk("P: and in again after", CompanionMod.inGameData(sid));
 // a different picture under the same name: not the same ship
 java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png); img.setRGB(5, 5, 0xFFFF00FF); javax.imageio.ImageIO.write(img, "png", ShipArt.file(built[1].art));
 CompanionMod.register(remodels); PatchState.changed();
 Setup.chk("P: a pixel changed in her hull picture: not in the game", !CompanionMod.inGameData(sid));
 patch(remodels, overlay);
 Setup.chk("P: patched: in", CompanionMod.inGameData(sid));
 System.clearProperty("homeplanet.dataOverlay"); PatchState.refresh();
 Setup.chk("P: the overlay gone (FTL's data as it was): out again", !CompanionMod.inGameData(sid));
 Setup.done();
}
 /** Builds the mod and lays it out as Slipstream would patch it, where PatchState and the data manager read it as the game's. */
 static void patch(List<CompanionMod.Remodel> remodels, File overlay) throws Exception {
  SafeFiles.deleteTree(overlay); overlay.mkdirs();
  File zip = new File(overlay.getParentFile(), "mod.ftl");
  CompanionMod.build(zip, remodels, "test");
  ZipInputStream z = new ZipInputStream(new FileInputStream(zip));
  for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
   if (e.isDirectory()) continue;
   File f = new File(overlay, e.getName()); f.getParentFile().mkdirs();
   ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; for (int n; (n = z.read(buf)) > 0; ) o.write(buf, 0, n);
   SafeFiles.write(f, o.toByteArray());
  }
  z.close();
  System.setProperty("homeplanet.dataOverlay", overlay.getAbsolutePath());
  PatchState.refresh();
 }
}
