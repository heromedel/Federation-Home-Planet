package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.xml.WeaponBlueprint;

/** Choosing the weapon an artillery system fires: the Federation Cruiser's, or (untested on player ships) the Flagship's. */
final class ArtilleryPicker {
	private ArtilleryPicker() { }

	/** Every artillery weapon in the game data, the player ones first. */
	static List<String> weapons() {
		List<String> fed = new ArrayList<String>(), other = new ArrayList<String>();
		for (WeaponBlueprint w : DataManager.get().getWeapons().values()) {
			if (!w.getId().startsWith("ARTILLERY")) continue;
			(playerOne(w.getId()) ? fed : other).add(w.getId());
		}
		java.util.Collections.sort(fed);
		java.util.Collections.sort(other);
		fed.addAll(other);
		return fed;
	}
	/** The Federation Cruiser's artillery (types A/B, and type C's flak). */
	static boolean playerOne(String id) { return id.startsWith("ARTILLERY_FED"); }
	/** Which ships carry it, from ftl.dat 1.6.14: ARTILLERY_FED on Federation Cruiser A and B, ARTILLERY_FED_C on C, the BOSS set on the Flagship. */
	static String note(String id) {
		if (!playerOne(id)) return "Not normally available to player ships: this is the Rebel Flagship's. Untested on a player ship.";
		return "A special weapon usually reserved for the Federation Cruiser " + (id.endsWith("_C") ? "C" : "A and B") + ".";
	}
	static String label(String id) {
		WeaponBlueprint w = DataManager.get().getWeapons().get(id);
		String t = null;
		try { t = w == null || w.getTitle() == null ? null : w.getTitle().getTextValue(); } catch (Exception e) { }
		String name = t == null || t.isEmpty() ? id : t;
		return name + (w != null && w.getPower() > 0 ? "  ·  " + w.getPower() + " power" : "") + (playerOne(id) ? "" : "  (Flagship)");
	}
	static String tip(String id) {
		String t = ItemTooltips.tooltip(id);
		String n = "<div style='margin-top:4px; width:260px; color:#c8a000'><i>" + note(id) + "</i></div>";
		return t == null ? "<html>" + label(id) + n + "</html>" : t.replace("</html>", n + "</html>");
	}

	/** Asks which weapon; null if cancelled. */
	static String choose(Component parent, String current) {
		final List<String> ids = weapons();
		if (ids.isEmpty()) {
			JOptionPane.showMessageDialog(parent, "The game data has no artillery weapons.", "Artillery", JOptionPane.INFORMATION_MESSAGE);
			return null;
		}
		final JList<String> list = new JList<String>(ids.toArray(new String[0]));
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer() {
			@Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean f) {
				super.getListCellRendererComponent(l, label((String) v), i, sel, f);
				setIcon(IconFactory.itemIcon((String) v));
				setToolTipText(tip((String) v));
				return this;
			}
		});
		final JLabel about = new JLabel();
		about.setVerticalAlignment(JLabel.TOP);
		about.setPreferredSize(new Dimension(380, 230));
		about.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
		list.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
			public void valueChanged(javax.swing.event.ListSelectionEvent e) {
				String id = list.getSelectedValue();
				about.setText(id == null ? "" : tip(id));
			}
		});
		list.setSelectedIndex(Math.max(0, current == null ? 0 : ids.indexOf(current)));
		JScrollPane sp = new JScrollPane(list);
		sp.setPreferredSize(new Dimension(300, 230));
		JPanel p = new JPanel(new BorderLayout(0, 6));
		JLabel q = new JLabel("Which weapon does her artillery fire?");
		q.setToolTipText("Artillery is not normally available as a stock part.");
		list.setToolTipText("Artillery is not normally available as a stock part.");
		p.add(q, BorderLayout.NORTH);
		p.add(sp, BorderLayout.WEST);
		p.add(about, BorderLayout.CENTER);
		int r = JOptionPane.showConfirmDialog(parent, p, "Artillery", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		return r == JOptionPane.OK_OPTION ? list.getSelectedValue() : null;
	}
}
