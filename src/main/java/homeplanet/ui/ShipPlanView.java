package homeplanet.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

import homeplanet.parser.CompanionMod;
import homeplanet.parser.ShipArt;
import homeplanet.parser.ShipDesign;

/**
 * A ship's floor plan, read-only: her rooms, doors and the icons of the systems she has, drawn over her picture at the
 * picture's scale, the way the design screen draws a ship (heromedel: the Refit tab shows her while you look at what
 * can go on or come off). One room can be lit: the system a row on the right is about. Nothing here is clickable.
 */
public class ShipPlanView extends JComponent {
	private static final int SQ = LayoutEditor.SQ;
	private ShipDesign d;
	private BufferedImage base, floor;
	/** The systems installed (ids), for their icons; others with a room get a dim icon. */
	private java.util.Set<String> installed = new java.util.HashSet<String>();
	private String lit;
	private final Map<String, BufferedImage> icons = new HashMap<String, BufferedImage>();
	private Rectangle bounds;

	public ShipPlanView() { setOpaque(false); }

	/** Shows this blueprint's ship (null: nothing), with these systems installed. */
	public void show(String bpId, java.util.Collection<String> installedIds) {
		d = null; base = null; floor = null; bounds = null;
		installed = new java.util.HashSet<String>(installedIds);
		if (bpId != null) {
			ShipDesign x = new ShipDesign();
			if (ShipDesign.fromGameShip(x, bpId)) {
				d = x;
				base = ShipArt.scaled(ShipArt.load(x.art, x.art.startsWith("game:") ? "_base" : ""), x.artScale);
				floor = ShipArt.floorOf(x);
				for (String id : x.systems.keySet()) if (!icons.containsKey(id)) icons.put(id, LayoutEditor.image("img/icons/s_" + id + "_overlay.png"));
				bounds = null;
				for (ShipDesign.Room r : x.rooms) bounds = union(bounds, new Rectangle(r.x * SQ, r.y * SQ, r.w * SQ, r.h * SQ));
				if (base != null) bounds = union(bounds, new Rectangle(x.artX, x.artY, base.getWidth(), base.getHeight()));
			}
		}
		repaint();
	}
	private static Rectangle union(Rectangle a, Rectangle b) { return a == null ? b : a.union(b); }
	/** Lights the room of this system (null: none). */
	public void light(String systemId) { if (systemId == null ? lit != null : !systemId.equals(lit)) { lit = systemId; repaint(); } }
	/** Whether she has a room for this system. */
	public boolean hasRoom(String systemId) { return d != null && d.systems.containsKey(systemId) && d.systems.get(systemId).room >= 0; }

	@Override protected void paintComponent(Graphics g0) {
		super.paintComponent(g0);
		if (d == null || bounds == null || bounds.width <= 0 || bounds.height <= 0) return;
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		double s = Math.min(1.0, Math.min((getWidth() - 8.0) / bounds.width, (getHeight() - 8.0) / bounds.height));
		g.translate((getWidth() - bounds.width * s) / 2, (getHeight() - bounds.height * s) / 2);
		g.scale(s, s);
		g.translate(-bounds.x, -bounds.y);
		if (base != null) g.drawImage(base, d.artX, d.artY, null);
		if (floor != null) g.drawImage(floor, d.artX + d.floorX, d.artY + d.floorY, null);
		int litRoom = lit != null && d.systems.containsKey(lit) ? d.systems.get(lit).room : -1;
		for (int i = 0; i < d.rooms.size(); i++) {
			ShipDesign.Room r = d.rooms.get(i);
			int x = r.x * SQ, y = r.y * SQ, w = r.w * SQ, h = r.h * SQ;
			boolean on = i == litRoom;
			g.setColor(on ? new Color(230, 200, 90, 150) : new Color(150, 154, 160, 150)); // FTL paints room floors flat grey
			g.fillRect(x, y, w, h);
			g.setColor(new Color(128, 132, 138, 160));
			for (int sx = 1; sx < r.w; sx++) g.drawLine(x + sx * SQ, y, x + sx * SQ, y + h);
			for (int sy = 1; sy < r.h; sy++) g.drawLine(x, y + sy * SQ, x + w, y + sy * SQ);
			g.setColor(on ? new Color(255, 220, 90) : new Color(200, 210, 220));
			g.setStroke(new BasicStroke(on ? 3f : 1.5f));
			g.drawRect(x, y, w - 1, h - 1);
			List<String> here = d.systemsIn(i);
			int n = here.size();
			for (int k = 0; k < n; k++) {
				BufferedImage ic = icons.get(here.get(k));
				if (ic == null) continue;
				boolean have = installed.contains(here.get(k));
				int cx = x + w / 2 + (n == 2 ? (k == 0 ? -12 : 12) : 0), cy = y + h / 2;
				java.awt.Composite was = g.getComposite();
				if (!have) g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.3f)); // a room reserved for a system she doesn't have
				g.drawImage(ic, cx - ic.getWidth() / 2, cy - ic.getHeight() / 2, null);
				g.setComposite(was);
			}
		}
		g.setStroke(new BasicStroke(4f));
		g.setColor(new Color(255, 230, 150));
		for (CompanionMod.Door x : d.doors) {
			int dx = x.x * SQ + (x.v == 1 ? 0 : SQ / 2), dy = x.y * SQ + (x.v == 1 ? SQ / 2 : 0);
			if (x.v == 1) g.drawLine(dx, dy - 9, dx, dy + 9); else g.drawLine(dx - 9, dy, dx + 9, dy);
		}
		g.dispose();
	}
	@Override public Dimension getPreferredSize() { return new Dimension(640, 470); }
}
