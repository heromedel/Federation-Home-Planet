package homeplanet.ui;

import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JComponent;

/**
 * Who pays: a little "PAY FROM" over a Ship | Cargo switch, the same on the Shop and the Refit tabs (6.45, heromedel).
 * Each tab keeps its own, as it was left; what the goods are for is the Shop's drop-down, not this.
 */
final class PayFrom extends JComponent {
	private static final int CAPTION_H = 11;
	private final FlipSwitch flip = new FlipSwitch("Ship", "Cargo");

	PayFrom() {
		setLayout(null);
		setOpaque(false);
		add(flip);
	}

	/** True while the Cargo Hold pays. */
	boolean cargo() { return flip.right(); }
	void setCargo(boolean c) { flip.setRight(c); }
	void onChange(Runnable r) { flip.onChange(r); }
	@Override public void setToolTipText(String t) { super.setToolTipText(t); flip.setToolTipText(t); }

	@Override public Dimension getPreferredSize() {
		Dimension f = flip.getPreferredSize();
		return new Dimension(Math.max(f.width, CargoParts.width("PAY FROM", FtlFont.SLOT) + 2), CAPTION_H + 1 + f.height);
	}
	@Override public void doLayout() {
		Dimension f = flip.getPreferredSize();
		flip.setBounds(0, CAPTION_H + 1, f.width, f.height);
	}
	@Override protected void paintComponent(Graphics g) {
		CargoParts.text(g, "PAY FROM", FtlFont.SLOT, CargoParts.DIM, 1, 0);
	}
}
