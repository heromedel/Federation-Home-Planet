import java.io.*; import java.awt.image.*; import net.blerf.ftl.parser.*; import net.blerf.ftl.xml.*; import homeplanet.core.*; import homeplanet.vault.*;
public class PicT { public static void main(String[] a) throws Exception {
 Vault v = Setup.open(new File(a[0]), new File(a[1])); v.takeStock();
 for (Ship s : v.fleet()) {
  net.blerf.ftl.parser.SavedGameParser.SavedGameState gs = s.save();
  ShipBlueprint bp = gs == null ? null : DataManager.get().getShips().get(gs.getPlayerShipBlueprintId());
  String gfx = bp == null ? null : bp.getGraphicsBaseName();
  InputStream in = gfx == null ? null : DataManager.get().getResourceInputStream("img/ship/" + gfx + "_base.png");
  BufferedImage img = in == null ? null : javax.imageio.ImageIO.read(in);
  System.out.println(s.name + "  bp=" + (bp == null ? null : bp.getId()) + " gfx=" + gfx + " img=" + (img == null ? null : img.getWidth() + "x" + img.getHeight()) + " state=" + s.state);
 }
}}
