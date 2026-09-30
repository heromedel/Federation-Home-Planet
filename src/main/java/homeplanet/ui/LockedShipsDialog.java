package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.parser.XmlText;

/** The ships the FTL profile hasn't unlocked yet (Commission hides them), each with FTL's own hint for unlocking her. */
final class LockedShipsDialog extends JDialog {
	private static final String[] LETTERS = {"A", "B", "C"};

	/** A locked layout: her blueprint and which layout (0 A, 1 B, 2 C). */
	static final class Locked {
		final String base;
		final ShipBlueprint bp;
		final int n;
		Locked(String base, ShipBlueprint bp, int n) { this.base = base; this.bp = bp; this.n = n; }
	}

	static void open(Component owner, List<Locked> locked) {
		new LockedShipsDialog(owner, locked).setVisible(true);
	}

	private LockedShipsDialog(Component owner, List<Locked> locked) {
		super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), "Locked ships", ModalityType.APPLICATION_MODAL);
		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		for (Locked l : locked) rows.add(row(l));
		JScrollPane sp = new JScrollPane(rows, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sp.getVerticalScrollBar().setUnitIncrement(24);
		sp.setPreferredSize(new Dimension(640, Math.min(520, rows.getPreferredSize().height + 10)));
		JLabel intro = new JLabel("<html>Not yet unlocked in your FTL profile, so they can't be commissioned. Unlock them in FTL, as its hangar says:</html>");
		intro.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton close = new JButton("Close");
		close.addActionListener(new java.awt.event.ActionListener() { public void actionPerformed(java.awt.event.ActionEvent e) { dispose(); } });
		buttons.add(close);
		getContentPane().add(intro, BorderLayout.NORTH);
		getContentPane().add(sp, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(close);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setLocationRelativeTo(getOwner());
		ScreenFit.keepOnScreen(this);
	}

	private static JPanel row(Locked l) {
		JPanel r = new JPanel(new BorderLayout(12, 0));
		r.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
		r.setAlignmentX(LEFT_ALIGNMENT);
		BufferedImage pic = picture(l.bp);
		JLabel p = new JLabel(pic == null ? null : new ImageIcon(pic));
		p.setPreferredSize(new Dimension(130, 60));
		r.add(p, BorderLayout.WEST);
		String cls = CommissionDialog.classOf(l.bp);
		String hint = hint(l, cls);
		JLabel t = new JLabel("<html><div style='width:460px'><b>" + XmlText.text(cls) + ", Type " + LETTERS[Math.min(2, l.n)]
				+ "</b><br>" + XmlText.text(hint.trim()) + "</div></html>");
		t.setFont(t.getFont().deriveFont(Font.PLAIN));
		r.add(t, BorderLayout.CENTER);
		r.setMaximumSize(new Dimension(Integer.MAX_VALUE, r.getPreferredSize().height));
		return r;
	}
	/**
	 * FTL's own hangar hint for unlocking her (its text ids: ship_PLAYER_SHIP_STEALTH_unlock for a Type A, and
	 * ship_PLAYER_SHIP_STEALTH_2_unlock for B, with the Kestrel and Engi named KESTREL and ENGI there); FTL's general rule
	 * for the few it has no text for.
	 */
	static String hint(Locked l, String cls) {
		String name = l.base.startsWith("PLAYER_SHIP_") ? l.base.substring("PLAYER_SHIP_".length()) : l.base;
		if ("HARD".equals(name)) name = "KESTREL";
		if ("CIRCLE".equals(name) && l.n > 0) name = "ENGI";
		String key = l.n == 0 ? "ship_" + l.base + "_unlock" : "ship_PLAYER_SHIP_" + name + "_" + (l.n + 1) + "_unlock";
		String text = null;
		if (DataManager.get() instanceof net.blerf.ftl.parser.DefaultDataManager) text = ((net.blerf.ftl.parser.DefaultDataManager) DataManager.get()).getTextById(key);
		if (text != null && !text.trim().isEmpty()) return text.trim();
		if (l.n == 1) return "Complete 2/3 of the " + cls + " Achievements to unlock this ship.";
		if (l.n == 2) return "Get to Sector 8 with the " + cls + " Type B and Advanced Mode enabled to unlock this ship.";
		return "Unlock her in FTL's hangar.";
	}
	/** Her picture from the game art, greyed as FTL's hangar shows a locked ship; null if there's none. */
	private static BufferedImage picture(ShipBlueprint bp) {
		try {
			InputStream in = DataManager.get().getResourceInputStream("img/ship/" + bp.getGraphicsBaseName() + "_base.png");
			BufferedImage b;
			try { b = ImageIO.read(in); } finally { in.close(); }
			BufferedImage t = IconFactory.trim(b, 8);
			BufferedImage fit = SpaceDockUI.fitImage(t == null ? b : t, 120, 56);
			java.awt.Image g = javax.swing.GrayFilter.createDisabledImage(fit);
			BufferedImage out = new BufferedImage(fit.getWidth(), fit.getHeight(), BufferedImage.TYPE_INT_ARGB);
			out.getGraphics().drawImage(g, 0, 0, null);
			return out;
		} catch (Exception e) {
			return null;
		}
	}
}
