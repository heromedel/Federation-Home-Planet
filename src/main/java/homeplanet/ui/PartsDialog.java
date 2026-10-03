package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import homeplanet.core.HomePlanet;
import homeplanet.model.Items;
import homeplanet.parser.Parts;
import homeplanet.vault.Vault;

/**
 * The Junkyard's parts for sale: damaged systems pulled from wrecks, each with its level, its broken bars and its price,
 * and Buy; now and then a piece of salvage for the Cargo Hold. Bought parts go to the stored systems; the Dry Dock mends them once installed.
 */
final class PartsDialog extends JDialog {
	private final JPanel cols = new JPanel();
	private final JLabel foot = new JLabel();

	static void open(java.awt.Component owner) {
		PartsDialog d = new PartsDialog(owner);
		d.setVisible(true);
	}

	private PartsDialog(java.awt.Component owner) {
		super(javax.swing.SwingUtilities.getWindowAncestor(owner), "Parts for sale", ModalityType.APPLICATION_MODAL);
		JPanel body = new JPanel(new BorderLayout(0, 10));
		body.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
		body.add(new JLabel("<html><div style='width:640px'>The Junkyard foreman pulls systems out of the wrecks that come in. They're damaged, "
				+ "and priced to match. Whatever you buy goes to the Cargo Bay's stored systems, broken bars and all: install it, then have it repaired.</div></html>"), BorderLayout.NORTH);
		body.add(cols, BorderLayout.CENTER);
		JPanel south = new JPanel(new BorderLayout());
		south.add(foot, BorderLayout.WEST);
		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		south.add(close, BorderLayout.EAST);
		body.add(south, BorderLayout.SOUTH);
		getContentPane().add(body);
		getRootPane().setDefaultButton(close);
		fill();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setResizable(false);
		setLocationRelativeTo(getOwner());
		ScreenFit.keepOnScreen(this);
	}

	private void fill() {
		Vault v = Vault.get();
		List<Parts.Listing> all = Parts.current(v);
		int n = Math.max(1, Parts.count(v)), hold = v.storageScrap();
		cols.removeAll();
		cols.setLayout(new GridLayout(1, n, 10, 0));
		for (int i = 0; i < n; i++) {
			Parts.Listing l = null;
			for (Parts.Listing x : all) if (x.index == i) l = x;
			cols.add(l == null ? sold() : card(l, hold));
		}
		foot.setText("More parts come in as the fleet travels: check back in a few days.   The Cargo Hold holds " + hold + " scrap.");
		cols.revalidate();
		cols.repaint();
		pack();
	}
	private JPanel sold() {
		JPanel p = new JPanel(new BorderLayout());
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(MenuTheme.GREY_GREEN), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		JLabel l = new JLabel("<html><div style='width:110px'><font color='" + MenuTheme.HTML_GREY_GREEN + "'>Sold.</font></div></html>");
		l.setVerticalAlignment(JLabel.TOP);
		p.add(l, BorderLayout.CENTER);
		return p;
	}
	private JPanel card(final Parts.Listing l, int hold) {
		if (l.salvage()) return salvageCard(l, hold);
		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(MenuTheme.GREY_GREEN), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		BufferedImage img = LayoutEditor.image("img/icons/s_" + l.id + "_overlay.png");
		JLabel pic = new JLabel(img == null ? null : new ImageIcon(img), JLabel.CENTER);
		pic.setPreferredSize(new java.awt.Dimension(110, 40));
		p.add(pic, BorderLayout.NORTH);
		String gold = MenuTheme.HTML_GOLD, dim = MenuTheme.HTML_GREY_GREEN;
		int whole = Parts.worth(l.id, l.level);
		JLabel words = new JLabel("<html><div style='width:110px'><font color='" + gold + "'><b>" + Items.systemTitle(l.id) + "</b></font><br>"
				+ "Level " + l.level + "<br><font color='#d86a4a'>" + l.broken + " of " + l.level + " broken</font><br>"
				+ "<font color='" + dim + "'>New: " + whole + " scrap</font>"
				+ (l.clearance ? "<br><font color='" + gold + "'>Clearance: the foreman wants it gone</font>" : "") + "</div></html>");
		words.setVerticalAlignment(JLabel.TOP);
		p.add(words, BorderLayout.CENTER);
		JButton buy = new JButton("Buy: " + l.price + " scrap");
		buy.setEnabled(hold >= l.price);
		buy.setToolTipText(hold >= l.price ? "Paid from the Cargo Hold; it goes to the stored systems" : "It costs " + l.price + " scrap; the Cargo Hold holds " + hold);
		buy.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { buy(l); } });
		p.add(buy, BorderLayout.SOUTH);
		return p;
	}

	/** Salvage: an item or a bundle of supplies, its store price, and Buy. */
	private JPanel salvageCard(final Parts.Listing l, int hold) {
		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(MenuTheme.GOLD), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		javax.swing.Icon icon = Parts.ITEM.equals(l.kind) ? IconFactory.itemIcon(l.id)
				: IconFactory.supplyIcon(Parts.FUEL.equals(l.kind) ? "fuel" : Parts.MISSILES.equals(l.kind) ? "missiles" : "drones");
		JLabel pic = new JLabel(icon, JLabel.CENTER);
		pic.setPreferredSize(new java.awt.Dimension(110, 40));
		p.add(pic, BorderLayout.NORTH);
		String gold = MenuTheme.HTML_GOLD, dim = MenuTheme.HTML_GREY_GREEN;
		JLabel words = new JLabel("<html><div style='width:110px'><font color='" + gold + "'><b>" + homeplanet.parser.XmlText.text(l.title()) + "</b></font><br>"
				+ "Salvage<br><font color='" + dim + "'>In a store: " + l.storePrice() + " scrap</font></div></html>");
		words.setVerticalAlignment(JLabel.TOP);
		p.add(words, BorderLayout.CENTER);
		JButton buy = new JButton("Buy: " + l.price + " scrap");
		buy.setEnabled(hold >= l.price);
		buy.setToolTipText(hold >= l.price ? "Paid from the Cargo Hold; it goes to the Cargo Hold" : "It costs " + l.price + " scrap; the Cargo Hold holds " + hold);
		buy.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { buy(l); } });
		p.add(buy, BorderLayout.SOUTH);
		return p;
	}

	private void buy(Parts.Listing l) {
		String what = l.salvage() ? l.title() : Items.systemTitle(l.id) + " (level " + l.level + ", " + l.broken + " broken)";
		if (!HomePlanet.confirmNo(this, "Buy the " + what + " for " + l.price + " scrap from the Cargo Hold?\n"
				+ (l.salvage() ? "It goes to the Cargo Hold." : "It goes to the stored systems as it is."), "Parts for sale")) return;
		try {
			Parts.buy(Vault.get(), l);
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The sale was called off. Nothing was changed:\n" + e.getMessage());
			fill();
			return;
		}
		fill();
		JOptionPane.showMessageDialog(this, "The " + what + (l.salvage() ? " is in the Cargo Hold." : " is in the Cargo Bay's stored systems."), "Parts for sale", JOptionPane.INFORMATION_MESSAGE);
	}
}
