package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.core.GameGuard;
import homeplanet.core.HomePlanet;
import homeplanet.model.Items;
import homeplanet.parser.Derelicts;
import homeplanet.parser.Retrofit;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * The Junkyard's derelicts for sale: each one's picture and state, her price, and Buy. New ones come in as the fleet
 * travels; nothing tells you, you come and look.
 */
final class DerelictsDialog extends JDialog {
	private final SpaceDockUI dock;
	private final JPanel cols = new JPanel(new GridLayout(1, Derelicts.LISTINGS, 12, 0));
	private final JLabel foot = new JLabel();
	/** Did the player buy one (the Space Dock redraws)? */
	boolean bought = false;

	static boolean open(SpaceDockUI dock) {
		DerelictsDialog d = new DerelictsDialog(dock);
		d.setVisible(true);
		return d.bought;
	}

	private DerelictsDialog(SpaceDockUI dock) {
		super(javax.swing.SwingUtilities.getWindowAncestor(dock), "Derelicts for sale", ModalityType.APPLICATION_MODAL);
		this.dock = dock;
		JPanel body = new JPanel(new BorderLayout(0, 10));
		body.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
		body.add(new JLabel("<html><div style='width:780px'>The Junkyard foreman has hulls nobody wanted, sold as they are: no crew, little or nothing aboard, "
				+ "and often put together strangely. Whatever you buy goes to the Junkyard; salvage her, then make her fly.</div></html>"), BorderLayout.NORTH);
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
		List<Derelicts.Listing> all = Derelicts.current(v);
		cols.removeAll();
		int hold = v.storageScrap();
		for (int i = 0; i < Derelicts.LISTINGS; i++) {
			Derelicts.Listing l = null;
			for (Derelicts.Listing x : all) if (x.index == i) l = x;
			cols.add(l == null ? sold() : column(l, hold));
		}
		// never a count: the foreman doesn't know either
		foot.setText("More hulls come in from time to time: check back in a week to a month, after some time spent exploring the stars.   The Cargo Hold holds " + hold + " scrap.");
		cols.revalidate();
		cols.repaint();
		pack();
	}
	private JPanel sold() {
		JPanel p = new JPanel(new BorderLayout());
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(MenuTheme.GREY_GREEN), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		JLabel l = new JLabel("<html><div style='width:240px'><font color='" + MenuTheme.HTML_GREY_GREEN + "'>Sold. The space is empty until new derelicts come in.</font></div></html>");
		l.setVerticalAlignment(JLabel.TOP);
		p.add(l, BorderLayout.CENTER);
		return p;
	}
	private JPanel column(final Derelicts.Listing l, int hold) {
		ShipState s = l.save.getPlayerShip();
		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(l.locked ? MenuTheme.GOLD : MenuTheme.GREY_GREEN), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		JLabel pic = new JLabel(picture(s), JLabel.CENTER);
		pic.setPreferredSize(new java.awt.Dimension(240, 130));
		p.add(pic, BorderLayout.NORTH);
		JLabel words = new JLabel("<html><div style='width:240px'>" + report(l) + "</div></html>");
		words.setVerticalAlignment(JLabel.TOP);
		p.add(words, BorderLayout.CENTER);
		JButton buy = new JButton("Buy her: " + l.price + " scrap");
		buy.setEnabled(hold >= l.price);
		buy.setToolTipText(hold >= l.price ? "Paid from the Cargo Hold; she goes to the Junkyard" : "She costs " + l.price + " scrap; the Cargo Hold holds " + hold);
		buy.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { buy(l); } });
		p.add(buy, BorderLayout.SOUTH);
		return p;
	}
	private ImageIcon picture(ShipState s) {
		ShipBlueprint bp = DataManager.get().getShip(s.getShipBlueprintId());
		BufferedImage img = bp == null ? null : dock.parent.getResourceImage("img/ship/" + bp.getGraphicsBaseName() + "_base.png", false);
		if (img == null) return null;
		BufferedImage t = IconFactory.trim(img, 8);
		return new ImageIcon(SpaceDockUI.fitImage(t == null ? img : t, 240, 130));
	}

	/** Her state, as the foreman would tell it. */
	private static String report(Derelicts.Listing l) {
		ShipState s = l.save.getPlayerShip();
		String gold = MenuTheme.HTML_GOLD, dim = MenuTheme.HTML_GREY_GREEN;
		StringBuilder sb = new StringBuilder();
		sb.append("<font size='+1' color='").append(gold).append("'><b>").append(homeplanet.parser.XmlText.text(l.save.getPlayerShipName())).append("</b></font><br>");
		sb.append(homeplanet.parser.XmlText.text(CargoBayUI.shipClass(s)));
		if (l.locked) sb.append("<br><font color='").append(gold).append("'>A model your FTL profile hasn't unlocked</font>");
		ShipBlueprint model = DataManager.get().getShip(Retrofit.vanillaId(s.getShipBlueprintId()));
		int max = model == null || model.getHealth() == null ? s.getHullAmt() : model.getHealth().amount;
		sb.append("<br><br>Hull ").append(s.getHullAmt()).append(" / ").append(max);
		int breaches = s.getBreachMap().size();
		if (breaches > 0) sb.append(", ").append(breaches).append(breaches == 1 ? " breach" : " breaches");
		sb.append("<br>Reactor ").append(s.getReservePowerCapacity()).append(", fuel ").append(s.getFuelAmt());
		List<String> sys = new ArrayList<String>();
		for (SystemType t : SystemType.values()) {
			SystemState st = s.getSystem(t);
			if (st == null || st.getCapacity() <= 0) continue;
			String line = Items.systemTitle(t.getId()) + " " + st.getCapacity();
			if (st.getDamagedBars() > 0) line += " <font color='#d86a4a'>(" + st.getDamagedBars() + " broken)</font>";
			if (!standard(model, t)) line += " <font color='" + dim + "'>(not hers)</font>";
			sys.add(line);
		}
		sb.append("<br><br>Systems: ").append(sys.isEmpty() ? "none" : String.join(", ", sys));
		List<String> missing = Retrofit.missingStandard(s);
		if (!missing.isEmpty()) {
			List<String> names = new ArrayList<String>();
			for (String id : missing) names.add(Items.systemTitle(id)
					+ ("engines".equals(id) || "pilot".equals(id) ? " (can't fly)" : "oxygen".equals(id) ? " (no air)" : ""));
			sb.append("<br><font color='#d86a4a'>Missing: ").append(String.join(", ", names)).append("</font>");
			int core = homeplanet.parser.Pricing.missingCore(s).size();
			if (core > 0) sb.append("<br><font color='").append(dim).append("'>Sells for ").append(core * homeplanet.parser.Pricing.CORE_PENALTY)
					.append(" points less until ").append(core == 1 ? "it's" : "they're").append(" put back</font>");
		}
		List<String> aboard = new ArrayList<String>();
		for (WeaponState w : s.getWeaponList()) aboard.add(Items.title(w.getWeaponId()));
		for (DroneState d : s.getDroneList()) aboard.add(Items.title(d.getDroneId()));
		for (String a : s.getAugmentIdList()) aboard.add(Items.title(a));
		sb.append("<br>Aboard: ").append(aboard.isEmpty() ? "nothing" : homeplanet.parser.XmlText.text(String.join(", ", aboard)));
		if (!l.oddityWords().isEmpty()) sb.append("<br><br><font color='").append(gold).append("'>").append(l.oddityWords()).append("</font>")
				.append("<br><font color='").append(dim).append("'>She'll need her own blueprint sent to FTL via Slipstream.</font>");
		return sb.toString();
	}
	/** Did her model come with this system? */
	private static boolean standard(ShipBlueprint model, SystemType t) {
		ShipBlueprint.SystemList.SystemRoom[] r = model == null || model.getSystemList() == null ? null : model.getSystemList().getSystemRoom(t);
		return r != null && r.length > 0 && (r[0].getStart() == null || r[0].getStart().booleanValue());
	}

	private void buy(Derelicts.Listing l) {
		String name = l.save.getPlayerShipName();
		if (!HomePlanet.confirmNo(this, "Buy " + name + " for " + l.price + " scrap from the Cargo Hold?\nShe goes to the Junkyard as she is.", "Derelicts for sale")) return;
		if (!l.oddity.isEmpty() && !GameGuard.allows(this, "write her blueprint")) return;
		Ship s;
		try {
			s = Derelicts.buy(Vault.get(), l);
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The sale was called off. Nothing was changed:\n" + e.getMessage());
			fill();
			return;
		}
		bought = true;
		fill();
		List<String> missing = Retrofit.missingBlueprints(s.file());
		if (missing.isEmpty()) {
			JOptionPane.showMessageDialog(this, name + " is in the Junkyard. Salvage her there to bring her to the Space Dock.", "Derelicts for sale", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Object[] opts = {"Patch Now", "Later"};
		int r = JOptionPane.showOptionDialog(this, name + " is in the Junkyard. She can't fly until The Home Planet Station sends her blueprint ("
				+ String.join(", ", missing) + ") to FTL via Slipstream.", "Derelicts for sale", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opts, opts[0]);
		if (r == 0) PatchDialog.open(this);
	}
}
