import java.awt.*; import java.awt.event.*; import javax.swing.*; import homeplanet.ui.EscClose;
/** Esc as the close box (heromedel, 6.20): the same close request as the X, so each window closes, asks or stays as its X would; an Esc something else used is left alone. Needs a display (xvfb-run). */
public class EscT { public static void main(String[] a) throws Exception {
 final int[] asked = {0};
 SwingUtilities.invokeAndWait(new Runnable() { public void run() {
  JFrame owner = new JFrame("owner"); owner.setSize(200, 100); owner.setVisible(true);
  JDialog plain = new JDialog(owner, "closes on X"); JTextField f = new JTextField(10); plain.add(f); plain.pack(); plain.setVisible(true);
  Setup.chk("E: a window that closes on X closes on Esc", EscClose.handle(esc(f)) && !plain.isVisible());
  JDialog stays = new JDialog(owner, "refuses X"); stays.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
  stays.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { asked[0]++; } });
  JButton b = new JButton("x"); stays.add(b); stays.pack(); stays.setVisible(true);
  EscClose.handle(esc(b));
  Setup.chk("E: a window that refuses X stays, its own closing asked once (" + asked[0] + ")", stays.isVisible() && asked[0] == 1);
  KeyEvent used = esc(b); used.consume();
  Setup.chk("E: an Esc something else used (a list, a menu, a message box) is left alone", !EscClose.handle(used) && asked[0] == 1);
  KeyEvent shift = new KeyEvent(b, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
  Setup.chk("E: only Esc itself, not with Shift or Ctrl", !EscClose.handle(shift));
  stays.dispose(); owner.dispose();
 } });
 Setup.done();
 System.exit(0);
}
 static KeyEvent esc(Component c) { return new KeyEvent(c, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED); }
}
