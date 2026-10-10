package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.parser.Retrofit;
import homeplanet.parser.ShipArt;

/** Picks one of the game's ships (player ships first, then the rest), with her picture: for a design that starts from her. */
final class GameShipPicker {
	private GameShipPicker() { }

	/** Shows the picker; returns the chosen blueprint id, or null. */
	static String choose(Component owner) {
		DataManager dm = DataManager.get();
		// label -> id, player ships first
		final Map<String, String> players = new TreeMap<String, String>(), others = new TreeMap<String, String>();
		List<Map<String, ShipBlueprint>> all = new ArrayList<Map<String, ShipBlueprint>>();
		all.add(dm.getShips());
		all.add(dm.getAutoShips());
		for (Map<String, ShipBlueprint> m : all) {
			for (ShipBlueprint bp : m.values()) {
				String id = bp.getId();
				if (id.endsWith(Retrofit.SUFFIX) || bp.getLayoutId() == null) continue; // the game's own ships, not the station's copies
				String cls = text(bp.getShipClass()), name = text(bp.getName());
				String label = (cls.isEmpty() ? id : cls + (name.isEmpty() ? "" : " - " + name)) + "   (" + id + ")";
				(id.startsWith("PLAYER_SHIP_") ? players : others).put(label, id);
			}
		}
		final List<String> labels = new ArrayList<String>(players.keySet());
		labels.addAll(others.keySet());
		final Map<String, String> ids = new java.util.HashMap<String, String>(players);
		ids.putAll(others);
		final JList<String> list = new JList<String>(labels.toArray(new String[0]));
		final JLabel preview = new JLabel();
		preview.setPreferredSize(new java.awt.Dimension(380, 300));
		preview.setHorizontalAlignment(JLabel.CENTER);
		list.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
			public void valueChanged(javax.swing.event.ListSelectionEvent e) {
				String id = ids.get(list.getSelectedValue());
				ShipBlueprint bp = id == null ? null : DataManager.get().getShips().get(id);
				if (bp == null && id != null) bp = DataManager.get().getAutoShips().get(id);
				BufferedImage img = bp == null || bp.getGraphicsBaseName() == null ? null : ShipArt.load("game:" + bp.getGraphicsBaseName(), "_base");
				preview.setIcon(img == null ? null : new javax.swing.ImageIcon(SpaceDockUI.fitImage(img, 370, 250)));
				preview.setText(img == null ? "(no picture)" : null);
			}
		});
		JScrollPane sp = new JScrollPane(list);
		sp.setPreferredSize(new java.awt.Dimension(460, 300));
		JPanel p = new JPanel(new BorderLayout(8, 6));
		p.add(new JLabel(homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " rooms, doors, systems, art and loadout become the new design's; change what you like."), BorderLayout.NORTH);
		p.add(sp, BorderLayout.WEST);
		p.add(preview, BorderLayout.CENTER);
		if (!labels.isEmpty()) list.setSelectedIndex(0);
		int r = JOptionPane.showConfirmDialog(owner, p, "A design from a game ship", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		return r == JOptionPane.OK_OPTION ? ids.get(list.getSelectedValue()) : null;
	}
	private static String text(net.blerf.ftl.xml.DefaultDeferredText t) {
		try { String v = t == null ? null : t.getTextValue(); return v == null ? "" : v.trim(); } catch (Exception e) { return ""; }
	}
}
