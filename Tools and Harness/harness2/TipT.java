import java.io.*; import java.util.*; import java.util.List; import java.awt.*; import java.awt.image.BufferedImage; import javax.swing.*; import homeplanet.core.*; import homeplanet.ui.*; import homeplanet.vault.*;
/**
 * Tooltips as heromedel asked for them (6.57): hovered with the mouse as a player would, on the Space Dock's buttons,
 * the Cargo Bay's Refit rows and the Settings: up after the short wait, never under the mouse nor over a button-sized
 * control, still there after Swing's old four seconds, gone the moment the mouse leaves. Screenshots of each, and one
 * tip at 10%, 15% and 25% see-through, in shots/. Needs a display (xvfb-run). args: gamedir, world saves, work
 */
public class TipT {
 static Robot robot;
 static File shots;
 public static void main(String[] a) throws Exception {
  File game = new File(a[0]), work = new File(a[2]); SafeFiles.deleteTree(work);
  File saves = new File(work, "saves"); Setup.copyTree(new File(a[1]), saves);
  shots = new File(work, "shots"); shots.mkdirs();
  HomePlanet.immersiveMode = false; HomePlanet.leaveImmersive();
  final Vault v = Setup.open(game, saves); v.storage(); v.takeStock();
  HomePlanet.datsPath = game; HomePlanet.save_location = saves;
  if (v.boarded() == null) v.board(v.docked().get(0));
  final Object[] frame = new Object[1];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   MenuTheme.install(); if (!Boolean.getBoolean("noTips")) Tips.install(); // -DnoTips=true: Swing's own tips, to compare
   MainFrame f = new MainFrame("FHP", "test"); f.setBounds(0, 0, 1300, 800); f.setVisible(true); frame[0] = f;
  } catch (Exception e) { throw new RuntimeException(e); } } });
  final MainFrame f = (MainFrame) frame[0];
  robot = new Robot(); robot.setAutoDelay(5);
  Thread.sleep(1500);
  System.out.println("  tooltip mode: " + UIManager.get("ToolTipManager.enableToolTipMode") + ", focused: " + f.isFocused());
  Setup.chk("S: the wait 0.4 seconds, never taken away while the mouse stays", ToolTipManager.sharedInstance().getInitialDelay() == 400 && ToolTipManager.sharedInstance().getDismissDelay() == Integer.MAX_VALUE);
  List<JComponent> dock = buttons(f.getContentPane(), 3);
  Setup.chk("S: the Space Dock has buttons with tips to hover (" + dock.size() + ")", dock.size() >= 2);
  boolean first = true;
  for (JComponent c : dock) { hover("Space Dock", c, first); first = false; }
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { try {
   f.showCargoBay();
   Object bay = field(f, MainFrame.class, "cargoBay");
   bay.getClass().getMethod("init").invoke(bay);
   java.lang.reflect.Method m = bay.getClass().getDeclaredMethod("showTab", String.class); m.setAccessible(true); m.invoke(bay, "refit");
  } catch (Exception e) { throw new RuntimeException(e); } } });
  Thread.sleep(1200);
  List<JComponent> rows = new ArrayList<JComponent>();
  for (JComponent c : buttons(f.getContentPane(), 99)) if (c.getClass().getName().endsWith("SysRow") && rows.size() < 2) rows.add(c);
  Setup.chk("R: the Refit tab has system rows to hover (" + rows.size() + ")", rows.size() >= 1);
  for (JComponent c : rows) hover("Refit row", c, false);
  JComponent power = null;
  for (JComponent c : all(f.getContentPane())) if (c.getClass().getSimpleName().equals("PowerPanel") && c.isShowing()) power = c;
  if (power != null) panelHover(power);
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { f.showSpaceDock(); f.setLocation(1100, 0); } }); // out from under Settings: with no window manager here, the frame can stay on top of it
  SwingUtilities.invokeLater(new Runnable() { public void run() { SettingsDialog.open(f); } });
  Thread.sleep(2000);
  JDialog settings = null;
  for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isShowing() && "Settings".equals(((JDialog) w).getTitle())) settings = (JDialog) w;
  Setup.chk("T: Settings opened", settings != null);
  if (settings != null) { final JDialog d = settings; SwingUtilities.invokeAndWait(new Runnable() { public void run() { d.toFront(); } }); Thread.sleep(800); } // no window manager here: on top only when asked
  if (settings != null) {
   for (JComponent c : buttons(settings.getContentPane(), 2)) hover("Settings", c, false);
   final JDialog d = settings;
   SwingUtilities.invokeAndWait(new Runnable() { public void run() { d.dispose(); } });
  }
  Setup.done();
  System.exit(0);
 }

 /** Hovers a control: its tip's place, its staying, its going; a screenshot (and the see-through versions, for the first). */
 static void hover(String where, JComponent c, boolean shades) throws Exception {
  Rectangle ctl = onScreen(c);
  Point m = new Point(ctl.x + ctl.width / 2, ctl.y + ctl.height / 2);
  away();
  BufferedImage before = robot.createScreenCapture(screen());
  final boolean[] heard = {false};
  java.awt.event.MouseAdapter ear = new java.awt.event.MouseAdapter() { public void mouseEntered(java.awt.event.MouseEvent e) { heard[0] = true; } };
  c.addMouseListener(ear);
  Window t = null;
  long up = -1;
  for (int attempt = 0; attempt < 2 && t == null; attempt++) { // with no window manager, a dialog over the station sometimes never hears the mouse arrive (Swing's own tips as much): once more
   if (attempt > 0) { System.out.println("  no tip: hovered again"); away(); Thread.sleep(600); }
   long t0 = System.currentTimeMillis();
   robot.mouseMove(m.x, m.y);
   while (t == null && System.currentTimeMillis() - t0 < 3000) { Thread.sleep(20); t = tip(); }
   if (t != null) up = System.currentTimeMillis() - t0;
  }
  c.removeMouseListener(ear);
  String name = where + " \"" + shortTip(c).replace('\n', ' ') + "\"";
  if (t == null && !heard[0]) { System.out.println("  " + name + ": not checked: this display never told the window the mouse arrived (no window manager; Swing's own tips miss it the same)"); return; }
  Setup.chk("P: " + name + ": up after the wait (" + (up < 0 ? "never came" : up + " ms") + ")", up >= 380 && up <= 1200);
  if (t == null) return;
  Rectangle tb = t.getBounds();
  Setup.chk("P: " + name + ": not under the mouse " + tb + " mouse " + m, !grow(tb, 2).contains(m));
  Setup.chk("P: " + name + ": not over its control " + tb + " control " + ctl, ctl.height > 64 || !tb.intersects(ctl));
  Setup.chk("P: " + name + ": on the screen", screen().contains(tb));
  Thread.sleep(300); // drawn: a new window is grey for its first moment
  BufferedImage with = robot.createScreenCapture(screen());
  String file = (where + "-" + shortTip(c)).replaceAll("[^A-Za-z0-9]+", "-");
  javax.imageio.ImageIO.write(crop(with, tb, ctl), "png", new File(shots, file + ".png"));
  if (shades) {
   System.out.println("  see-through on this display: " + (t.getOpacity() < 1f ? "yes (" + t.getOpacity() + ")" : "no (the screenshots below are blended to show it)"));
   for (int pct : new int[] {10, 15, 25}) javax.imageio.ImageIO.write(crop(blend(before, with, tb, 1f - pct / 100f), tb, ctl), "png", new File(shots, file + "-" + pct + "pct.png"));
  }
  Thread.sleep(4500);
  Setup.chk("P: " + name + ": still up after five seconds with the mouse on it", tip() != null);
  long t1 = System.currentTimeMillis();
  away();
  while (tip() != null && System.currentTimeMillis() - t1 < 3000) Thread.sleep(10);
  long gone = System.currentTimeMillis() - t1;
  System.out.println("  the event queue, as the tip went: " + ping() + " ms to answer");
  Setup.chk("P: " + name + ": gone as soon as the mouse leaves (" + gone + " ms)", gone <= 600); // this display alone takes up to half a second to tell Swing the mouse has gone, a plain Swing tip as much (6.57)
  Thread.sleep(600);
 }
 /** The power panel places its own tips beside the mouse: kept there, never under it. */
 static void panelHover(JComponent p) throws Exception {
  Rectangle r = onScreen(p);
  for (int x = r.x + 20; x < r.x + r.width; x += 12) {
   for (int y = r.y + 20; y < r.y + r.height; y += 12) {
    Point local = new Point(x - r.x, y - r.y);
    if (p.getToolTipText(new java.awt.event.MouseEvent(p, 0, 0, 0, local.x, local.y, 0, false)) == null) continue;
    away(); robot.mouseMove(x, y); Thread.sleep(800);
    Window t = tip();
    Setup.chk("Q: the power panel's own hover, beside the mouse, never under it", t != null && !grow(t.getBounds(), 2).contains(new Point(x, y)));
    Thread.sleep(300);
    if (t != null) javax.imageio.ImageIO.write(crop(robot.createScreenCapture(screen()), t.getBounds(), new Rectangle(x - 4, y - 4, 8, 8)), "png", new File(shots, "Refit-power-panel.png"));
    away(); Thread.sleep(150);
    Setup.chk("Q: gone as soon as the mouse leaves", tip() == null);
    return;
   }
  }
  Setup.chk("Q: something in the power panel to hover", false);
 }

 /** How long the station's event queue takes to answer: what any tip, or anything else on the screen, waits for. */
 static long ping() throws Exception { long t = System.currentTimeMillis(); SwingUtilities.invokeAndWait(new Runnable() { public void run() { } }); return System.currentTimeMillis() - t; }
 static void away() throws Exception { Rectangle s = screen(); robot.mouseMove(s.x + s.width - 5, s.y + s.height - 5); }
 /** The tooltip's own window, if one is showing. */
 static Window tip() throws Exception {
  final Window[] r = new Window[1];
  SwingUtilities.invokeAndWait(new Runnable() { public void run() {
   for (Window w : Window.getWindows()) if (w.isShowing()) for (Component c : allIn(w)) if (c instanceof JToolTip && c.isShowing()) r[0] = w instanceof JWindow ? w : new TipBounds(c);
  } });
  return r[0];
 }
 static List<Component> allIn(Container c) { List<Component> out = new ArrayList<Component>(); for (Component k : c.getComponents()) { out.add(k); if (k instanceof Container) out.addAll(allIn((Container) k)); } return out; }
 /** A tip drawn inside its window (Swing's own way, without 6.57's Tips): stands in for a window with the tip's bounds on the screen. */
 static final class TipBounds extends Window { final Component tip; TipBounds(Component tip) { super((Window) null); this.tip = tip; Point p = tip.getLocationOnScreen(); setBounds(p.x, p.y, tip.getWidth(), tip.getHeight()); } }
 static Rectangle screen() { return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds(); }
 static Rectangle onScreen(JComponent c) throws Exception {
  final Rectangle[] r = new Rectangle[1]; final JComponent k = c;
  SwingUtilities.invokeAndWait(new Runnable() { public void run() { Point p = k.getLocationOnScreen(); r[0] = new Rectangle(p.x, p.y, k.getWidth(), k.getHeight()); } });
  return r[0];
 }
 static Rectangle grow(Rectangle r, int n) { return new Rectangle(r.x - n, r.y - n, r.width + 2 * n, r.height + 2 * n); }
 static String shortTip(JComponent c) { String t = c.getToolTipText(); t = t == null ? "" : t.replaceAll("<[^>]+>", ""); return t.length() > 30 ? t.substring(0, 30) : t; }
 /** Small showing controls with a tip, fully on the screen and the window, at most n. */
 static List<JComponent> buttons(Container root, int n) {
  List<JComponent> out = new ArrayList<JComponent>();
  for (JComponent c : all(root)) {
   if (out.size() >= n) break;
   if (!c.isShowing() || c.getToolTipText() == null || c.getHeight() > 64 || c.getHeight() < 10 || c.getWidth() < 10) continue;
   Point p = c.getLocationOnScreen();
   Rectangle w = SwingUtilities.getWindowAncestor(c).getBounds();
   if (!w.contains(new Rectangle(p.x, p.y, c.getWidth(), c.getHeight())) || p.y < w.y + 40) continue;
   out.add(c);
  }
  return out;
 }
 static List<JComponent> all(Container c) {
  List<JComponent> out = new ArrayList<JComponent>();
  for (Component k : c.getComponents()) { if (k instanceof JComponent) out.add((JComponent) k); if (k instanceof Container) out.addAll(all((Container) k)); }
  return out;
 }
 /** The tip over what was under it, at this opacity. */
 static BufferedImage blend(BufferedImage under, BufferedImage with, Rectangle tip, float a) {
  BufferedImage out = new BufferedImage(with.getWidth(), with.getHeight(), BufferedImage.TYPE_INT_RGB);
  out.getGraphics().drawImage(with, 0, 0, null);
  for (int y = Math.max(0, tip.y); y < Math.min(out.getHeight(), tip.y + tip.height); y++)
   for (int x = Math.max(0, tip.x); x < Math.min(out.getWidth(), tip.x + tip.width); x++) {
    int p = with.getRGB(x, y), q = under.getRGB(x, y), rgb = 0;
    for (int s = 0; s <= 16; s += 8) rgb |= Math.round(((p >> s) & 255) * a + ((q >> s) & 255) * (1 - a)) << s;
    out.setRGB(x, y, rgb);
   }
  return out;
 }
 /** The tip and its control, with some room round them. */
 static BufferedImage crop(BufferedImage img, Rectangle tip, Rectangle ctl) {
  Rectangle r = grow(tip.union(ctl), 40).intersection(new Rectangle(0, 0, img.getWidth(), img.getHeight()));
  return img.getSubimage(r.x, r.y, r.width, r.height);
 }
 static Object field(Object o, Class<?> k, String name) throws Exception { java.lang.reflect.Field f = k.getDeclaredField(name); f.setAccessible(true); return f.get(o); }
}
