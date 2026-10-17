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
	private final JButton search = new JButton("Search for more parts");

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
		search.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { search(); } });
		JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
		buttons.add(search);
		buttons.add(close);
		south.add(buttons, BorderLayout.EAST);
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
		boolean again = Parts.canSearch(v);
		search.setEnabled(again && hold >= Parts.SEARCH_COST);
		search.setToolTipText(!again ? "Come back later" : hold < Parts.SEARCH_COST ? "It costs " + Parts.SEARCH_COST + " scrap; the Cargo Hold holds " + hold
				: Parts.SEARCH_COST + " scrap from the Cargo Hold: the foreman's crew turn over the wrecks for other parts. It takes the day.");
		foot.setText("More parts come in as the fleet travels: check back in a week or two.   The Cargo Hold holds " + hold + " scrap.");
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
		p.add(buttons(l, buy), BorderLayout.SOUTH);
		String tip = systemInfo(l, false);
		pic.setToolTipText(tip);
		words.setToolTipText(tip);
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
		p.add(buttons(l, buy), BorderLayout.SOUTH);
		String tip = salvageInfo(l);
		pic.setToolTipText(tip);
		words.setToolTipText(tip);
		return p;
	}

	/** Info above Buy (heromedel, 6.66: what a part is, at a click). */
	private JPanel buttons(final Parts.Listing l, JButton buy) {
		JPanel b = new JPanel(new GridLayout(2, 1, 0, 4));
		JButton info = new JButton("Info");
		info.setToolTipText("What it is and what it does");
		info.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { info(l); } });
		b.add(info);
		b.add(buy);
		return b;
	}
	private void info(Parts.Listing l) {
		if (Parts.ITEM.equals(l.kind)) { ItemTooltips.show(this, l.id); return; }
		javax.swing.Icon icon = l.salvage() ? IconFactory.supplyIcon(Parts.FUEL.equals(l.kind) ? "fuel" : Parts.MISSILES.equals(l.kind) ? "missiles" : "drones")
				: LayoutEditor.image("img/icons/s_" + l.id + "_overlay.png") == null ? null : new ImageIcon(LayoutEditor.image("img/icons/s_" + l.id + "_overlay.png"));
		JOptionPane.showMessageDialog(this, new JLabel(l.salvage() ? salvageInfo(l) : systemInfo(l, true)), homeplanet.parser.XmlText.text(l.title()), JOptionPane.PLAIN_MESSAGE, icon);
	}
	/** A system part: what the system does (FTL's own words), its level and broken bars, and what it's worth whole; in full for Info, shorter as a hover. */
	static String systemInfo(Parts.Listing l, boolean full) {
		net.blerf.ftl.xml.SystemBlueprint b = net.blerf.ftl.parser.DataManager.get().getSystem(l.id);
		String desc = b == null || b.getDescription() == null ? "" : b.getDescription().getTextValue();
		if (!full) { int dot = desc.indexOf(". "); if (dot > 0) desc = desc.substring(0, dot + 1); }
		StringBuilder sb = new StringBuilder("<html><b>").append(Items.systemTitle(l.id)).append("</b>");
		if (!desc.isEmpty()) sb.append("<div style='width:260px; margin-top:4px'>").append(homeplanet.parser.XmlText.text(desc)).append("</div>");
		sb.append("<div style='margin-top:4px'>Level ").append(l.level).append(", ").append(l.broken).append(" of ").append(l.level).append(" bars broken")
				.append("<br>Whole and new: ").append(Parts.worth(l.id, l.level)).append(" scrap");
		if (full) sb.append("<br><br>It goes to the Cargo Bay's stored systems as it is.<br>Install it on a ship, then have the Dry Dock repair it.");
		return sb.append("</div></html>").toString();
	}
	/** Salvage: a weapon, drone or augment's card, or what a bundle of supplies is. */
	static String salvageInfo(Parts.Listing l) {
		if (Parts.ITEM.equals(l.kind)) { String t = ItemTooltips.tooltip(l.id); if (t != null) return t; }
		String what = Parts.FUEL.equals(l.kind) ? "Fuel for the FTL drive: one jump for each."
				: Parts.MISSILES.equals(l.kind) ? "Missiles, for the weapons that fire them."
				: Parts.DRONE_PARTS.equals(l.kind) ? "Drone parts, for deploying drones." : "";
		return "<html><b>" + homeplanet.parser.XmlText.text(l.title()) + "</b><div style='width:260px; margin-top:4px'>" + what
				+ "</div><div style='margin-top:4px'>In a store: " + l.storePrice() + " scrap<br>It goes to the Cargo Hold.</div></html>";
	}

	private void search() {
		if (!HomePlanet.confirmNo(this, "Search the Junkyard for more parts, for " + Parts.SEARCH_COST + " scrap from the Cargo Hold?\n"
				+ "The parts and salvage for sale now go back on the heap, and the search takes the day.", "Parts for sale")) return;
		try {
			Parts.search(Vault.get());
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The search was called off. Nothing was changed:\n" + e.getMessage());
			fill();
			return;
		}
		fill();
		JOptionPane.showMessageDialog(this, "The foreman's crew turned over the wrecks: these are what came out of them.", "Parts for sale", JOptionPane.INFORMATION_MESSAGE);
		Vault.get().countBeacon("a day searching the Junkyard"); // the search takes the day, as a purchase does (heromedel, 6.66)
		fill();
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
		Vault.get().countBeacon("a day at the Junkyard"); // a purchase there takes the day, once its message is closed (heromedel, 6.66)
	}
}
