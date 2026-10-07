package homeplanet.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.core.HomePlanet;
import homeplanet.parser.Museum;
import homeplanet.parser.SaveHelper;
import homeplanet.parser.XmlText;
import homeplanet.vault.Vault;
import homeplanet.vault.VoyageLog;

/**
 * The Federation Museum: the Hall of Victors and the Memorial. One ship at a time on the stage (‹ › and the Left/Right
 * keys, or the gallery strip below), her plate and status under her, and on the right her Record, Crew, Voyage and
 * Loadout.
 */
public class MuseumUI extends JPanel {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MuseumUI.class);
	static final Color GOLD = FtlButton.GOLD, TXT = new Color(228, 237, 232), DIMC = MenuTheme.GREY_GREEN, SILVER = new Color(200, 210, 215),
			GREEN = MenuTheme.GREEN, PANEL = new Color(20, 28, 36, 215), LINEC = new Color(214, 230, 222, 120);
	private static final int INFO_W = 470;

	private final MainFrame parent;
	private List<Museum.Exhibit> all = new ArrayList<Museum.Exhibit>();
	private boolean memorial = false;
	private int index = 0;
	private String tab = "Record";
	private final Map<String, BufferedImage> pictures = new HashMap<String, BufferedImage>();

	public MuseumUI(MainFrame parent) {
		this.parent = parent;
		setLayout(null);
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prev");
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next");
		getActionMap().put("prev", new AbstractAction() { public void actionPerformed(ActionEvent e) { step(-1); } });
		getActionMap().put("next", new AbstractAction() { public void actionPerformed(ActionEvent e) { step(1); } });
	}

	/** Takes stock of the museum and shows it (the wing and ship shown before, if they're still there). */
	public void init() {
		String shownId = current() == null ? null : current().id;
		all = Museum.exhibits(Vault.get());
		if (wing().isEmpty()) memorial = !memorial;
		index = 0;
		List<Museum.Exhibit> w = wing();
		for (int i = 0; i < w.size(); i++) if (w.get(i).id.equals(shownId)) index = i;
		build();
	}
	private List<Museum.Exhibit> wing() {
		List<Museum.Exhibit> out = new ArrayList<Museum.Exhibit>();
		for (Museum.Exhibit e : all) if (e.victor != memorial) out.add(e);
		return out;
	}
	private Museum.Exhibit current() {
		List<Museum.Exhibit> w = wing();
		return w.isEmpty() ? null : w.get(Math.max(0, Math.min(index, w.size() - 1)));
	}
	private void step(int d) {
		List<Museum.Exhibit> w = wing();
		if (w.size() < 2 || !isShowing()) return;
		index = (index + d + w.size()) % w.size();
		build();
	}

	// ---- the screen ----

	@Override
	protected void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0;
		g.setPaint(new GradientPaint(0, 0, new Color(8, 11, 18), 0, getHeight(), new Color(24, 30, 44)));
		g.fillRect(0, 0, getWidth(), getHeight());
		Random r = new Random(3);
		for (int i = 0; i < getWidth() * getHeight() / 3800; i++) {
			int b = 90 + r.nextInt(150);
			g.setColor(new Color(b, b, Math.min(255, b + 10), 120 + r.nextInt(120)));
			int s = r.nextInt(10) == 0 ? 2 : 1;
			g.fillRect(r.nextInt(Math.max(1, getWidth())), r.nextInt(Math.max(1, getHeight())), s, s);
		}
	}

	private void build() {
		removeAll();
		int W = Math.max(1000, getWidth() > 0 ? getWidth() : 1280), H = Math.max(640, getHeight() > 0 ? getHeight() : 760);
		int total = Museum.totalVictories(all);
		FtlButton.Header head = new FtlButton.Header("The Federation Museum" + (total > 0 ? "  -  " + total + (total == 1 ? " victory" : " victories") : ""), W - INFO_W - 60);
		place(head, 20, 14, head.getPreferredSize());
		FtlButton back = new FtlButton("Space Dock", FtlFont.MENU, 190, 40);
		back.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { parent.showSpaceDock(); } });
		place(back, W - 210, 12, new Dimension(190, 40));
		// the wings
		int victors = 0, lost = 0;
		for (Museum.Exhibit e : all) { if (e.victor) victors++; else lost++; }
		wingButton("Hall of Victors (" + victors + ")", false, victors > 0, 20, 54);
		wingButton("Memorial (" + lost + ")", true, lost > 0, 230, 54);
		Museum.Exhibit ex = current();
		int stageW = W - INFO_W - 60;
		if (ex == null) {
			JLabel none = label("No ship is on show yet.", 14, DIMC, false);
			place(none, 20 + stageW / 2 - none.getPreferredSize().width / 2, H / 2, none.getPreferredSize());
		} else {
			SavedGameState gs = read(ex.save);
			stage(ex, gs, 20, 100, stageW, H - 270);
			gallery(20, H - 158, stageW, 146);
			info(ex, gs, W - INFO_W - 20, 70, INFO_W, H - 90);
		}
		revalidate();
		repaint();
	}
	private void wingButton(String text, final boolean mem, boolean enabled, int x, int y) {
		FtlButton b = new FtlButton(text, FtlFont.BODY, 200, 32);
		b.setEnabled(enabled);
		b.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { memorial = mem; index = 0; build(); } });
		place(b, x, y, new Dimension(200, 32));
		if (mem == memorial) place(bar(200), x, y + 34, new Dimension(200, 3));
	}
	private static JComponent bar(final int w) {
		return new JComponent() { protected void paintComponent(Graphics g) { g.setColor(GOLD); g.fillRect(0, 0, w, 3); } };
	}
	private void place(Component c, int x, int y, Dimension d) {
		c.setBounds(x, y, d.width, d.height);
		add(c);
	}

	/** The stage: a victor under the spotlight on her plinth; a memorial plate in silver. */
	private void stage(final Museum.Exhibit ex, SavedGameState gs, int x, int y, final int w, final int h) {
		final int cx = w / 2, plinthY = h - 140;
		JPanel st = new JPanel(null) {
			protected void paintComponent(Graphics g0) {
				Graphics2D g = (Graphics2D) g0;
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				if (ex.victor) {
					Polygon beam = new Polygon(new int[] {cx - 70, cx + 70, cx + 300, cx - 300}, new int[] {0, 0, plinthY + 10, plinthY + 10}, 4);
					g.setPaint(new GradientPaint(0, 0, new Color(255, 235, 190, 40), 0, plinthY, new Color(255, 235, 190, 6)));
					g.fill(beam);
					g.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Float(cx, plinthY + 10), 300, new float[] {0f, 1f},
							new Color[] {new Color(255, 225, 160, 70), new Color(255, 225, 160, 0)}));
					g.fillOval(cx - 300, plinthY - 30, 600, 80);
				}
				g.setColor(new Color(34, 42, 54));
				g.fillRoundRect(cx - 210, plinthY - 4, 420, 40, 14, 14);
				g.setColor(ex.victor ? GOLD : SILVER);
				g.setStroke(new BasicStroke(2f));
				g.drawRoundRect(cx - 210, plinthY - 4, 420, 40, 14, 14);
			}
		};
		st.setOpaque(false);
		st.setBounds(x, y, w, h);
		BufferedImage pic = picture(gs, Math.min(560, w - 160), Math.max(120, plinthY - 30), !ex.victor);
		if (pic != null) {
			JLabel p = new JLabel(new ImageIcon(pic));
			p.setBounds(cx - pic.getWidth() / 2, Math.max(10, (plinthY - 20 - pic.getHeight()) / 2 + 10), pic.getWidth(), pic.getHeight());
			st.add(p);
		}
		if (wing().size() > 1) {
			FtlButton prev = new FtlButton("<", FtlFont.MENU, 54, 64), next = new FtlButton(">", FtlFont.MENU, 54, 64);
			prev.setToolTipText("The ship before (Left key)");
			next.setToolTipText("The next ship (Right key)");
			prev.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { step(-1); } });
			next.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { step(1); } });
			prev.setBounds(4, plinthY / 2 - 32, 54, 64);
			next.setBounds(w - 58, plinthY / 2 - 32, 54, 64);
			st.add(prev);
			st.add(next);
		}
		JComponent plate = text(ex.name.toUpperCase(), FtlFont.MENU, ex.victor ? GOLD : SILVER);
		plate.setBounds(cx - plate.getWidth() / 2, plinthY + 16 - plate.getHeight() / 2, plate.getWidth(), plate.getHeight());
		st.add(plate);
		String cls = gs == null ? "" : classOf(gs);
		List<String[]> vd = ex.victoryDetails();
		String[] last = vd.isEmpty() ? null : vd.get(vd.size() - 1);
		String sub = cls + (ex.victor && last != null && !last[2].isEmpty() ? "   •   Victory on " + last[2] : "")
				+ (ex.victor && last != null && !last[1].isEmpty() ? "   •   Score " + String.format("%,d", Integer.parseInt(last[1])) : "");
		JLabel s = label(sub, 14, TXT, false);
		center(st, s, cx, plinthY + 46);
		JComponent status = statusLine(ex);
		status.setBounds(cx - status.getPreferredSize().width / 2, plinthY + 72, status.getPreferredSize().width, status.getPreferredSize().height);
		st.add(status);
		JLabel count = label((ex.victor ? "Exhibit " : "Plate ") + (index + 1) + " of " + wing().size() + (ex.victor ? "   •   Victories: " + ex.victories : ""), 12, DIMC, false);
		center(st, count, cx, plinthY + 100);
		if (!ex.epitaph().isEmpty()) {
			JLabel ep = label("“" + ex.epitaph() + "”", 14, TXT, false);
			ep.setFont(ep.getFont().deriveFont(Font.ITALIC));
			center(st, ep, cx, 14 > plinthY ? 0 : 6);
		}
		JButton epitaph = smallButton(ex.epitaph().isEmpty() ? "Write epitaph..." : "Change epitaph...", "One line of your own, on her plate");
		epitaph.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { writeEpitaph(ex); } });
		JButton picture = smallButton("Save as picture", "Save this exhibit as a picture (PNG)");
		picture.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { saveAsPicture(ex); } });
		Dimension ed = epitaph.getPreferredSize(), pd = picture.getPreferredSize();
		epitaph.setBounds(4, h - ed.height - 2, ed.width, ed.height);
		picture.setBounds(w - pd.width - 4, h - pd.height - 2, pd.width, pd.height);
		st.add(epitaph);
		st.add(picture);
		add(st);
	}
	private static void center(JPanel p, JComponent c, int cx, int y) {
		Dimension d = c.getPreferredSize();
		c.setBounds(cx - d.width / 2, y, d.width, d.height);
		p.add(c);
	}
	/** Preserved in the Museum (gold, stars), Still in Service (green), Honoured in Memory, Lost in Action, Sector N (silver). */
	private static JComponent statusLine(Museum.Exhibit ex) {
		String t;
		Color c;
		switch (ex.status) {
			case PRESERVED: t = "PRESERVED IN THE MUSEUM"; c = GOLD; break;
			case IN_SERVICE: t = "STILL IN SERVICE"; c = GREEN; break;
			case TRANSFERRED: t = "TRANSFERRED TO ANOTHER FLEET"; c = SILVER; break;
			case RETURNED: t = "RETURNED TO HER OWNER"; c = SILVER; break;
			case SEIZED: t = "SEIZED BY THE CLAIMS OFFICE"; c = SILVER; break;
			case LOST: t = "LOST IN ACTION" + (ex.lostSector > 0 ? ", SECTOR " + ex.lostSector : ""); c = SILVER; break;
			case MEMORIAL: t = "LOST IN ACTION" + (ex.lostSector > 0 ? ", SECTOR " + ex.lostSector : ""); c = SILVER; break;
			default: t = "HONOURED IN MEMORY"; c = SILVER;
		}
		final BufferedImage words = FtlFont.MENU.render(t, c);
		final boolean stars = ex.status == Museum.Status.PRESERVED;
		final BufferedImage star = stars ? star(GOLD) : null;
		JComponent line = new JComponent() {
			protected void paintComponent(Graphics g) {
				int x = stars ? 30 : 0;
				if (stars) { g.drawImage(star, 0, 0, null); g.drawImage(star, x + words.getWidth() + 8, 0, null); }
				g.drawImage(words, x, 2, null);
			}
		};
		line.setPreferredSize(new Dimension(words.getWidth() + (stars ? 60 : 0), Math.max(22, words.getHeight() + 2)));
		return line;
	}
	static BufferedImage star(Color c) {
		BufferedImage b = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = b.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Polygon s = new Polygon();
		for (int i = 0; i < 10; i++) {
			double r = i % 2 == 0 ? 10 : 4.2, a = -Math.PI / 2 + i * Math.PI / 5;
			s.addPoint((int) Math.round(11 + r * Math.cos(a)), (int) Math.round(11 + r * Math.sin(a)));
		}
		g.setColor(c);
		g.fill(s);
		g.dispose();
		return b;
	}

	/** The gallery strip: every ship in this wing, the one shown framed in gold; click one to show her. */
	private void gallery(int x, int y, int w, int h) {
		JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 8));
		strip.setOpaque(false);
		final List<Museum.Exhibit> ws = wing();
		for (int i = 0; i < ws.size(); i++) {
			final int n = i;
			final Museum.Exhibit e = ws.get(i);
			final boolean sel = i == index;
			final BufferedImage t = picture(read(e.save), 150, 66, !e.victor);
			final BufferedImage name = FtlFont.BODY.render(FtlFont.BODY.fit(e.name, 170), sel ? (e.victor ? GOLD : SILVER) : TXT);
			JComponent frame = new JComponent() {
				protected void paintComponent(Graphics g0) {
					Graphics2D g = (Graphics2D) g0;
					g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g.setColor(new Color(30, 40, 50));
					g.fillRoundRect(0, 0, 179, 111, 10, 10);
					g.setColor(sel ? (e.victor ? GOLD : SILVER) : LINEC);
					g.setStroke(new BasicStroke(sel ? 2.5f : 1.2f));
					g.drawRoundRect(1, 1, 177, 109, 10, 10);
					if (t != null) g.drawImage(t, 90 - t.getWidth() / 2, 6 + (66 - t.getHeight()) / 2, null);
					g.drawImage(name, 90 - name.getWidth() / 2, 84, null);
				}
			};
			frame.setPreferredSize(new Dimension(180, 112));
			frame.setToolTipText(e.name);
			frame.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			frame.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent ev) { index = n; build(); } });
			strip.add(frame);
		}
		JScrollPane sp = new JScrollPane(strip, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		sp.setOpaque(false);
		sp.getViewport().setOpaque(false);
		sp.setBorder(BorderFactory.createLineBorder(new Color(40, 52, 64)));
		sp.getHorizontalScrollBar().setUI(new MenuTheme.DarkScrollBarUI(new Color(214, 230, 222, 150), new Color(0, 0, 0, 0)));
		sp.getHorizontalScrollBar().setUnitIncrement(40);
		sp.getHorizontalScrollBar().setOpaque(false);
		sp.setBounds(x, y, w, h);
		add(sp);
	}

	// ---- the right-hand panel ----

	private void info(final Museum.Exhibit ex, SavedGameState gs, int x, int y, int w, int h) {
		JPanel box = new JPanel(null) {
			protected void paintComponent(Graphics g0) {
				Graphics2D g = (Graphics2D) g0;
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(PANEL);
				g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
				g.setColor(LINEC);
				g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
			}
		};
		box.setOpaque(false);
		box.setBounds(x, y, w, h);
		String[] tabs = {"Record", "Crew", "Voyage", "Loadout"};
		for (int i = 0; i < tabs.length; i++) {
			final String t = tabs[i];
			FtlButton b = new FtlButton(t, FtlFont.BODY, 104, 32);
			b.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { tab = t; build(); } });
			b.setBounds(12 + i * 112, 12, 104, 32);
			box.add(b);
			if (t.equals(tab)) { JComponent u = bar(104); u.setBounds(12 + i * 112, 46, 104, 3); box.add(u); }
		}
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		content.setBorder(BorderFactory.createEmptyBorder(4, 14, 10, 14));
		if (gs == null) content.add(label("Her record can't be read: " + (ex.save == null ? "no save kept" : ex.save.getName()), 12, DIMC, false));
		else if ("Crew".equals(tab)) crew(content, ex, gs);
		else if ("Voyage".equals(tab)) voyage(content, ex);
		else if ("Loadout".equals(tab)) loadout(content, gs);
		else record(content, ex, gs);
		JScrollPane sp = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sp.setOpaque(false);
		sp.getViewport().setOpaque(false);
		sp.setBorder(null);
		sp.getVerticalScrollBar().setUI(new MenuTheme.DarkScrollBarUI(new Color(214, 230, 222, 150), new Color(0, 0, 0, 0)));
		sp.getVerticalScrollBar().setUnitIncrement(24);
		sp.setBounds(2, 56, w - 4, h - 60);
		box.add(sp);
		add(box);
	}
	private static void heading(JPanel p, String s) {
		FtlButton.Header hd = new FtlButton.Header(s, INFO_W - 44);
		p.add(Box.createRigidArea(new Dimension(1, 8)));
		p.add(hd);
		p.add(Box.createRigidArea(new Dimension(1, 6)));
	}
	private static void row(JPanel p, String k, String v) {
		JPanel r = new JPanel(new BorderLayout());
		r.setOpaque(false);
		r.setAlignmentX(LEFT_ALIGNMENT);
		JLabel kl = label(k, 12, DIMC, false);
		kl.setPreferredSize(new Dimension(170, kl.getPreferredSize().height));
		r.add(kl, BorderLayout.WEST);
		r.add(label(v, 14, TXT, true), BorderLayout.CENTER);
		r.setMaximumSize(new Dimension(INFO_W, r.getPreferredSize().height + 6));
		p.add(r);
		p.add(Box.createRigidArea(new Dimension(1, 6)));
	}
	private static void mark(JPanel p, String sym, Color c, String text) {
		JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		r.setOpaque(false);
		r.setAlignmentX(LEFT_ALIGNMENT);
		r.add(label(sym, 14, c, true));
		r.add(label(text, 12, TXT, false));
		r.setMaximumSize(new Dimension(INFO_W, r.getPreferredSize().height + 4));
		p.add(r);
	}

	private void record(JPanel p, Museum.Exhibit ex, SavedGameState gs) {
		heading(p, "Service record");
		if (!ex.get("commissioned").isEmpty()) row(p, "Commissioned", ex.get("commissioned"));
		homeplanet.vault.TradeMark mark = homeplanet.vault.TradeMark.of(Vault.get(), ex.id);
		if (mark != null) {
			row(p, "Original owner", mark.original);
			if (!mark.from.equals(mark.original)) row(p, "Received from", mark.from);
		}
		if (!ex.get("transferredTo").isEmpty()) row(p, "Transferred to", ex.get("transferredTo") + "'s fleet");
		List<String[]> vd = ex.victoryDetails();
		if (ex.victor) {
			String[] last = vd.isEmpty() ? null : vd.get(vd.size() - 1);
			if (last != null && !last[0].isEmpty()) row(p, ex.victories == 1 ? "Final victory" : "Last victory", last[0] + (last[3].isEmpty() ? "" : ", sector " + last[3]));
			if (ex.victories > 1) row(p, "Victories", String.valueOf(ex.victories));
			if (last != null && !last[2].isEmpty()) row(p, "Difficulty", last[2]);
			if (last != null && !last[1].isEmpty()) row(p, "Final score", String.format("%,d", Integer.parseInt(last[1])));
		} else {
			row(p, "Lost in action", ex.lostSector > 0 ? "Sector " + ex.lostSector : "Where, isn't known");
		}
		row(p, "Sectors visited", String.valueOf(VoyageLog.visited(Vault.get(), ex.id, gs)));
		row(p, "Beacons explored", String.valueOf(gs.getTotalBeaconsExplored()));
		row(p, "Ships defeated", String.valueOf(gs.getTotalShipsDefeated()));
		row(p, "Scrap collected", String.format("%,d", gs.getTotalScrapCollected()));
		int lostCrew = lostCrew(ex).size();
		row(p, "Crew at the end", SaveHelper.getOwnCrew(gs.getPlayerShip()).size() + (lostCrew > 0 ? "  (" + lostCrew + " lost on the way)" : ""));
		if (ex.status == Museum.Status.PRESERVED && !ex.get("price").isEmpty()) row(p, "Museum's price", String.format("%,d scrap", Integer.parseInt(ex.get("price"))));
		if (!ex.victor) return;
		heading(p, "Honours");
		mark(p, "★", GOLD, ex.victories == 1 ? "Drove off the Rebel Flagship" : "Drove off the Rebel Flagship " + ex.victories + " times");
		for (String[] v : vd) for (String h : v[4].split("\\|")) if (!h.isEmpty()) mark(p, "★", GOLD, h);
		List<String> recs = records(ex);
		if (!recs.isEmpty()) {
			heading(p, "Museum records");
			for (String r : recs) mark(p, "▲", GREEN, r);
		}
	}
	/** What she holds among the honoured ships (with two or more victors). */
	private List<String> records(Museum.Exhibit ex) {
		List<String> out = new ArrayList<String>();
		List<Museum.Exhibit> vs = new ArrayList<Museum.Exhibit>();
		for (Museum.Exhibit e : all) if (e.victor) vs.add(e);
		if (vs.size() < 2) return out;
		String[][] what = {{"score", "Highest score of any honoured ship"}, {"defeated", "Most ships defeated"}, {"beacons", "Fewest beacons to victory"}, {"crew", "Most crew at the end"}};
		for (String[] w : what) {
			Museum.Exhibit best = null;
			long bestV = 0;
			for (Museum.Exhibit e : vs) {
				Long val = measure(e, w[0]);
				if (val == null) continue;
				boolean better = "beacons".equals(w[0]) ? val < bestV : val > bestV;
				if (best == null || better) { best = e; bestV = val; }
			}
			if (best == ex) out.add(w[1]);
		}
		return out;
	}
	private Long measure(Museum.Exhibit e, String what) {
		if ("score".equals(what)) {
			long max = -1;
			for (String[] v : e.victoryDetails()) try { max = Math.max(max, Long.parseLong(v[1])); } catch (NumberFormatException x) { }
			return max < 0 ? null : max;
		}
		SavedGameState gs = read(e.save);
		if (gs == null) return null;
		if ("defeated".equals(what)) return (long) gs.getTotalShipsDefeated();
		if ("beacons".equals(what)) return (long) gs.getTotalBeaconsExplored();
		return (long) SaveHelper.getOwnCrew(gs.getPlayerShip()).size();
	}

	private void crew(JPanel p, Museum.Exhibit ex, SavedGameState gs) {
		heading(p, ex.victor ? "At the final engagement" : "Her crew at the end");
		String[] sk = {"Pilot", "Eng", "Shld", "Weap", "Rep", "Cmbt"};
		for (CrewState c : SaveHelper.getOwnCrew(gs.getPlayerShip())) {
			JPanel r = new JPanel(null);
			r.setOpaque(false);
			r.setAlignmentX(LEFT_ALIGNMENT);
			Icon ic = IconFactory.crewPortrait(c, 44);
			if (ic != null) { JLabel pl = new JLabel(ic); pl.setBounds(0, 2, 46, 46); r.add(pl); }
			JLabel nm = label(FtlFont.BODY.fit(c.getName(), 150), 14, TXT, true);
			nm.setBounds(54, 0, 150, 18);
			r.add(nm);
			JLabel race = label(homeplanet.model.Crew.raceTitle(c), 12, DIMC, false);
			race.setBounds(54, 18, 150, 14);
			r.add(race);
			final int[] lv = homeplanet.model.Crew.skillLevels(c);
			for (int i = 0; i < 6; i++) {
				JLabel k = label(sk[i], 10, DIMC, false);
				k.setBounds(210 + i * 38, 0, 38, 13);
				r.add(k);
				final int level = lv[i];
				JComponent pips = new JComponent() {
					protected void paintComponent(Graphics g) {
						for (int q = 0; q < 2; q++) { g.setColor(q < level ? (level == 2 ? GOLD : GREEN) : new Color(60, 72, 80)); g.fillRect(q * 10, 0, 8, 8); }
					}
				};
				pips.setBounds(210 + i * 38, 16, 20, 8);
				r.add(pips);
			}
			JLabel st = label("Repairs " + c.getRepairs() + "  •  Kills " + c.getCombatKills() + "  •  Evasions " + c.getPilotedEvasions()
					+ "  •  Jumps " + c.getJumpsSurvived() + (c.getSkillMasteriesEarned() > 0 ? "  •  Masteries " + c.getSkillMasteriesEarned() : ""), 11, TXT, false);
			st.setBounds(54, 34, INFO_W - 90, 15);
			r.add(st);
			Dimension d = new Dimension(INFO_W - 40, 58);
			r.setPreferredSize(d);
			r.setMaximumSize(d);
			p.add(r);
		}
		List<String> lost = lostCrew(ex);
		if (!lost.isEmpty()) {
			heading(p, "Lost on the way");
			for (String l : lost) mark(p, "✝", DIMC, l);
		}
		if (ex.victor) {
			p.add(Box.createRigidArea(new Dimension(1, 10)));
			p.add(label("As she turned for the final engagement: FTL saves nothing during the last fight.", 12, DIMC, false));
		}
	}
	/** Her crew lost on the way, from her voyage log: "Name (Race), lost in sector N". */
	private static List<String> lostCrew(Museum.Exhibit ex) {
		List<String> out = new ArrayList<String>();
		int sector = 0;
		for (homeplanet.core.EventLog.Entry e : homeplanet.core.EventLog.voyage(homeplanet.core.EventLog.read(Vault.get()), ex.id)) { // her events (5.74)
			if (e.kind.equals("SECTOR_REACHED")) sector = e.num("sector", sector);
			if (!e.kind.equals("CREW_LOST")) continue;
			java.util.List<String> crew = e.all("crew"), race = e.all("race");
			for (int i = 0; i < crew.size(); i++) out.add(crew.get(i) + (i < race.size() ? " (" + race.get(i) + ")" : "") + (sector > 0 ? ", lost in sector " + sector : ""));
		}
		return out;
	}
	private void voyage(JPanel p, Museum.Exhibit ex) {
		heading(p, "Voyage");
		StringBuilder log = new StringBuilder();
		for (homeplanet.core.EventLog.Entry e : homeplanet.core.EventLog.voyage(homeplanet.core.EventLog.read(Vault.get()), ex.id)) log.append(e.time.length() >= 16 ? e.time.substring(0, 16) : e.time).append("  ").append(e.human).append('\n');
		JTextArea a = new JTextArea(log.length() == 0 ? "No voyage log was kept for her: the station started keeping them in 4B.29." : log.toString());
		a.setEditable(false);
		a.setLineWrap(true);
		a.setWrapStyleWord(true);
		a.setOpaque(false);
		a.setForeground(TXT);
		a.setFont(MenuTheme.TEXT_FONT);
		a.setAlignmentX(LEFT_ALIGNMENT);
		a.setSize(new Dimension(INFO_W - 44, 10));
		p.add(a);
	}
	private void loadout(JPanel p, SavedGameState gs) {
		ShipState s = gs.getPlayerShip();
		heading(p, "Weapons and drones");
		for (WeaponState w : s.getWeaponList()) item(p, w.getWeaponId());
		for (DroneState d : s.getDroneList()) item(p, d.getDroneId());
		if (s.getWeaponList().isEmpty() && s.getDroneList().isEmpty()) p.add(label("None", 12, DIMC, false));
		heading(p, "Augments");
		for (String a : s.getAugmentIdList()) item(p, a);
		if (s.getAugmentIdList().isEmpty()) p.add(label("None", 12, DIMC, false));
		heading(p, "Systems");
		for (SystemType t : SystemType.values()) {
			SystemState st = s.getSystem(t);
			if (st != null && st.getCapacity() > 0) row(p, homeplanet.model.Items.systemTitle(t.getId()), "level " + st.getCapacity());
		}
		row(p, "Reactor", s.getReservePowerCapacity() + " power");
	}
	private static void item(JPanel p, String id) {
		JLabel l = label(homeplanet.model.Items.title(id), 12, TXT, false);
		Icon ic = IconFactory.itemIcon(id);
		if (ic != null) l.setIcon(ic);
		l.setIconTextGap(10);
		l.setAlignmentX(LEFT_ALIGNMENT);
		p.add(l);
		p.add(Box.createRigidArea(new Dimension(1, 4)));
	}

	// ---- epitaph and picture ----

	private void writeEpitaph(Museum.Exhibit ex) {
		Object a = JOptionPane.showInputDialog(this, "One line for " + ex.name + "'s plate (leave it empty to remove it):", "Epitaph",
				JOptionPane.PLAIN_MESSAGE, null, null, ex.epitaph());
		if (a == null) return;
		String line = a.toString().trim();
		if (line.length() > 90) line = line.substring(0, 90);
		Museum.setEpitaph(Vault.get(), ex.id, line);
		init();
	}
	private void saveAsPicture(Museum.Exhibit ex) {
		BufferedImage img = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		paint(g);
		g.dispose();
		File dir = new File(Vault.get().root, "museum-pictures");
		File f = new File(dir, homeplanet.core.SafeFiles.safeName(ex.name) + " " + new SimpleDateFormat("yyyy-MM-dd HHmmss").format(new Date()) + ".png");
		try {
			if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Could not create " + dir);
			ImageIO.write(img, "png", f);
			JOptionPane.showMessageDialog(this, "Saved:\n" + f, "Save as picture", JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not save the picture:\n" + f + "\n\n" + e.getMessage());
		}
	}

	// ---- helpers ----

	private static SavedGameState read(File f) {
		if (f == null) return null;
		try { return HomePlanet.savedGameParser.readSavedGame(f); } catch (Exception e) { return null; }
	}
	private static String classOf(SavedGameState gs) {
		ShipBlueprint bp = DataManager.get().getShip(gs.getPlayerShipBlueprintId());
		return bp == null ? gs.getPlayerShipBlueprintId() : CommissionDialog.classOf(bp);
	}
	/** Her picture from the game art, fitted to the box (greyed for the Memorial); cached. */
	private BufferedImage picture(SavedGameState gs, int w, int h, boolean grey) {
		if (gs == null) return null;
		ShipBlueprint bp = DataManager.get().getShip(gs.getPlayerShipBlueprintId());
		if (bp == null) return null;
		String key = bp.getGraphicsBaseName() + w + "x" + h + grey;
		if (pictures.containsKey(key)) return pictures.get(key);
		BufferedImage out = null;
		try {
			InputStream in = DataManager.get().getResourceInputStream("img/ship/" + bp.getGraphicsBaseName() + "_base.png");
			BufferedImage b;
			try { b = ImageIO.read(in); } finally { in.close(); }
			BufferedImage t = IconFactory.trim(b, 8);
			out = SpaceDockUI.fitImage(t == null ? b : t, w, h);
			if (grey) {
				java.awt.Image gi = javax.swing.GrayFilter.createDisabledImage(out);
				BufferedImage g2 = new BufferedImage(out.getWidth(), out.getHeight(), BufferedImage.TYPE_INT_ARGB);
				g2.getGraphics().drawImage(gi, 0, 0, null);
				out = g2;
			}
		} catch (Exception e) { log.debug("Museum: an exhibit's picture could not be drawn: {}", e.toString()); }
		pictures.put(key, out);
		return out;
	}
	private static JComponent text(String s, FtlFont f, Color c) {
		final BufferedImage i = f.render(s, c);
		JComponent comp = new JComponent() { protected void paintComponent(Graphics g) { g.drawImage(i, 0, 0, null); } };
		comp.setSize(i.getWidth(), i.getHeight());
		comp.setPreferredSize(new Dimension(i.getWidth(), i.getHeight()));
		return comp;
	}
	static JLabel label(String s, int size, Color c, boolean bold) {
		JLabel l = new JLabel(s);
		l.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
		l.setForeground(c);
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}
	private static JButton smallButton(String text, String tip) {
		FtlButton b = new FtlButton(text, FtlFont.BODY, FtlFont.BODY.render(text, Color.white).getWidth() + 28, 28);
		b.setToolTipText(tip);
		b.setPreferredSize(new Dimension(FtlFont.BODY.render(text, Color.white).getWidth() + 28, 28));
		return b;
	}

	@Override
	public void doLayout() {
		// the stage is laid out for the screen's size: rebuilt when that changes
		if (getWidth() != lastW || getHeight() != lastH) { lastW = getWidth(); lastH = getHeight(); if (lastW > 0) build(); }
	}
	private int lastW = -1, lastH = -1;
}
