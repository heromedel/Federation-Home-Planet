package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import homeplanet.model.Crew;
import homeplanet.vault.CrewRegister;
import homeplanet.vault.CrewRegister.Member;
import homeplanet.vault.CrewRegister.Status;
import homeplanet.vault.MasterLog;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;
import net.blerf.ftl.parser.SavedGameParser.CrewState;

/**
 * The Crew Log (heromedel, 5.41), in the Records page's viewer: pick a crew member from one list (by ship, the Cargo
 * Hold, on assignment, then the discharged, KIA and MIA) and see their whole career: their portrait, skills and service
 * record as last seen, the ships they served on, and what happened to them, a stardate a line.
 */
final class CrewLogView extends JPanel {
	private final Vault vault;
	private final List<Member> members;
	private final JComboBox<Object> pick = new JComboBox<Object>();
	private final JPanel career = new JPanel();

	/** A section's name in the list: not a crew member, never picked. */
	private static final class Section {
		final String title;
		Section(String title) { this.title = title; }
		@Override public String toString() { return title; }
	}

	CrewLogView(Vault v, int select) {
		super(new BorderLayout(0, 0));
		setBackground(RecordsLog.BG);
		vault = v;
		CrewRegister.sweep(v); // as they are now
		members = CrewRegister.members(v);
		java.util.Set<String> laidUp = new java.util.HashSet<String>();
		for (homeplanet.parser.Expeditions.Patient p : homeplanet.parser.Expeditions.infirmary(v)) laidUp.add(p.name + "/" + p.race);
		for (Member m : members) m.laidUp = m.status == Status.PRESENT && m.where.equals("in the Cargo Hold") && laidUp.contains(m.name + "/" + m.race);

		// the sections: each ship (aboard first, then docked, then the Junkyard's), the hold, on assignment, then the gone
		Map<String, List<Member>> sections = new LinkedHashMap<String, List<Member>>();
		List<Ship> ships = new ArrayList<Ship>();
		if (v.boarded() != null) ships.add(v.boarded());
		ships.addAll(v.docked());
		ships.addAll(v.junked());
		for (Ship s : ships) sections.put("ship:" + s.id, new ArrayList<Member>());
		String[] rest = {"hold", "away", "TRANSFERRED", "RETIRED", "KILLED", "MIA"};
		for (String k : rest) sections.put(k, new ArrayList<Member>());
		for (Member m : members) {
			String k = m.status == Status.KILLED ? "KILLED" : m.status == Status.RETIRED ? "RETIRED" : m.status == Status.TRANSFERRED ? "TRANSFERRED" : m.status != Status.PRESENT ? "MIA"
					: m.where.startsWith("on an expedition") || m.where.startsWith("on assignment") ? "away" : m.where.equals("in the Cargo Hold") ? "hold" : shipKey(ships, m);
			if (!sections.containsKey(k)) sections.put(k, new ArrayList<Member>());
			sections.get(k).add(m);
		}
		Object chosen = null;
		for (Map.Entry<String, List<Member>> e : sections.entrySet()) {
			if (e.getValue().isEmpty()) continue;
			pick.addItem(new Section(title(e.getKey(), ships)));
			for (Member m : e.getValue()) { pick.addItem(m); if (m.id == select || chosen == null && select < 0) chosen = m; }
		}
		pick.setRenderer(new DefaultListCellRenderer() {
			@Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
				if (value instanceof Section) {
					JLabel l = (JLabel) super.getListCellRendererComponent(list, "  " + value, index, false, false);
					l.setFont(MenuTheme.LABEL_FONT.deriveFont(Font.BOLD));
					l.setForeground(MenuTheme.GOLD);
					l.setIcon(null);
					return l;
				}
				Member m = (Member) value;
				JLabel l = (JLabel) super.getListCellRendererComponent(list, m == null ? "" : "      " + m.name + " (" + m.raceTitle() + ")", index, selected, focus);
				CrewState c = m == null ? null : m.crew();
				l.setIcon(c == null || index < 0 ? null : IconFactory.crewIcon(c));
				return l;
			}
		});
		pick.setMaximumRowCount(20);
		pick.addActionListener(new java.awt.event.ActionListener() {
			private Object last;
			public void actionPerformed(java.awt.event.ActionEvent e) {
				Object o = pick.getSelectedItem();
				if (o instanceof Section) { // a section's name: on to its first crew member (or back up, coming from below)
					int i = pick.getSelectedIndex(), back = last == null ? -1 : indexOf(last);
					int j = back > i ? i - 1 : i + 1;
					while (j >= 0 && j < pick.getItemCount() && pick.getItemAt(j) instanceof Section) j += back > i ? -1 : 1;
					if (j >= 0 && j < pick.getItemCount()) pick.setSelectedIndex(j);
					return;
				}
				last = o;
				show((Member) o);
			}
		});
		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		top.setOpaque(false);
		top.setBorder(BorderFactory.createEmptyBorder(10, 14, 6, 14));
		JLabel crewLbl = new JLabel("Crew member:  ");
		crewLbl.setFont(MenuTheme.LABEL_FONT);
		crewLbl.setForeground(MenuTheme.GOLD);
		top.add(crewLbl);
		top.add(pick);
		add(top, BorderLayout.NORTH);
		career.setLayout(new BoxLayout(career, BoxLayout.Y_AXIS));
		career.setOpaque(false);
		career.setBorder(BorderFactory.createEmptyBorder(4, 16, 14, 16));
		add(career, BorderLayout.CENTER);
		if (members.isEmpty()) career.add(line("No crew on record yet.", RecordsLog.DIM));
		else if (chosen != null) pick.setSelectedItem(chosen);
	}
	private int indexOf(Object o) { for (int i = 0; i < pick.getItemCount(); i++) if (pick.getItemAt(i) == o) return i; return -1; }
	private static String shipKey(List<Ship> ships, Member m) {
		for (Ship s : ships) if (m.where.equals("aboard " + the(s.name)) || m.where.equals("aboard " + the(s.name) + ", in the Junkyard")) return "ship:" + s.id;
		return "hold";
	}
	private static String title(String key, List<Ship> ships) {
		if (key.startsWith("ship:")) {
			for (Ship s : ships) if (key.equals("ship:" + s.id)) return (s.isBoarded() ? "Aboard " : s.state == Ship.State.JUNKED ? "In the Junkyard: " : "Docked: ") + s.name;
		}
		if (key.equals("hold")) return "The Cargo Hold";
		if (key.equals("away")) return "On an expedition";
		if (key.equals("TRANSFERRED")) return "Transferred";
		if (key.equals("RETIRED")) return "Retired";
		if (key.equals("KILLED")) return "KIA";
		return "MIA";
	}
	private static String the(String ship) { return ship == null ? "" : ship.regionMatches(true, 0, "the ", 0, 4) ? ship : "the " + ship; }

	/** How many are on record, for the viewer's title row. */
	int count() { return members.size(); }

	/**
	 * Their career: the crew card the popup shows (portrait, skills with their bars, service record), as last seen for
	 * those gone; beside it where they stand, their ships and assignments; then what happened, by stardate, a coloured
	 * mark an event (heromedel, 5.41).
	 */
	private void show(Member m) {
		career.removeAll();
		CrewState c = m.crew();
		JPanel top = new JPanel(new BorderLayout(18, 0));
		top.setOpaque(false);
		top.setAlignmentX(LEFT_ALIGNMENT);
		if (c != null) {
			JPanel card = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			card.setOpaque(false);
			card.add(new CrewReport(c, m.laidUp));
			top.add(card, BorderLayout.WEST);
		}
		JPanel side = new JPanel();
		side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
		side.setOpaque(false);
		if (c == null) {
			JLabel name = line(m.name + " (" + m.raceTitle() + ")", MenuTheme.WHITE);
			name.setFont(MenuTheme.LABEL_FONT.deriveFont(Font.BOLD, 18f));
			side.add(name);
		}
		final int rank = CrewRegister.rankToGive(vault, m);
		if (rank >= 0) { // a rank they've earned, above where they stand (heromedel, 5.52): anyone, the KIA posthumously
			final Member who = m;
			String off = CrewRegister.cannotPromote(vault, m);
			javax.swing.JButton promote = new javax.swing.JButton("Promote");
			promote.setToolTipText(off == null ? homeplanet.model.Rank.TOOLTIP : "<html>" + off + "<br>" + homeplanet.model.Rank.TOOLTIP + "</html>");
			promote.setEnabled(off == null);
			promote.setAlignmentX(LEFT_ALIGNMENT);
			promote.addActionListener(new java.awt.event.ActionListener() { public void actionPerformed(java.awt.event.ActionEvent e) { promote(who, rank); } });
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			row.setOpaque(false);
			row.setAlignmentX(LEFT_ALIGNMENT);
			row.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
			row.add(promote);
			row.setMaximumSize(row.getPreferredSize());
			side.add(row);
		}
		side.add(line(status(m), m.status == Status.PRESENT ? RecordsLog.GOOD : m.status == Status.KILLED ? RecordsLog.BAD : MenuTheme.GOLD));
		if (m.status != Status.PRESENT && c != null) side.add(line("The card shows them as last seen.", RecordsLog.DIM));
		if (!m.events.isEmpty()) {
			side.add(heading("On the station's records"));
			side.add(line("Since Stardate " + MasterLog.stardate(Math.max(1, m.events.get(0).day)) + ".", MenuTheme.TEXT));
		}
		int sent = 0, back = 0;
		for (CrewRegister.Event e : m.events) { if (e.text.startsWith("Sent on an expedition") || e.text.startsWith("Sent on assignment")) sent++; if (e.text.startsWith("Back from an expedition") || e.text.startsWith("Back from an assignment")) back++; } // 5.46 records said "assignment"
		if (sent > 0 || back > 0) {
			side.add(heading("Expeditions"));
			side.add(line(sent + " sent out, " + back + " came back.", MenuTheme.TEXT));
		}
		if (!m.served.isEmpty()) {
			side.add(heading("Ships served on"));
			for (String s : m.served) {
				side.add(dotted(CrewRegister.shipOf(s), MenuTheme.TEXT, MenuTheme.GOLD));
				List<String> was = CrewRegister.formerNames(s); // a renamed ship, once, with the names she had (heromedel, 5.51)
				if (was.isEmpty()) continue;
				side.add(indented("(Previously Known as:", 26));
				for (int i = 0; i < was.size(); i++) side.add(indented(was.get(i) + (i == was.size() - 1 ? ")" : ""), 38));
			}
		}
		servedWith(side, m);
		side.add(Box.createVerticalGlue());
		top.add(side, BorderLayout.CENTER);
		top.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, top.getPreferredSize().height));
		career.add(top);

		career.add(heading("Career"));
		if (m.events.isEmpty()) career.add(line("Nothing recorded yet.", RecordsLog.DIM));
		int day = -1;
		for (CrewRegister.Event e : m.events) {
			int d = Math.max(1, e.day);
			if (d != day) {
				day = d;
				JLabel h = line("Stardate " + MasterLog.stardate(d), MenuTheme.GOLD);
				h.setFont(MenuTheme.LABEL_FONT);
				h.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
				career.add(h);
			}
			career.add(dotted(e.text, MenuTheme.TEXT, mark(e.text)));
		}
		career.add(Box.createVerticalGlue());
		career.revalidate();
		career.repaint();
	}
	/** Promotes them, and shows their page again under their new name. */
	private void promote(Member m, int rank) {
		try {
			CrewRegister.promote(vault, m.id);
		} catch (java.io.IOException e) {
			homeplanet.core.HomePlanet.showErrorDialog("The Home Planet Station could not promote " + m.name + ":\n" + e.getMessage());
			return;
		}
		LogViewer lv = (LogViewer) javax.swing.SwingUtilities.getAncestorOfClass(LogViewer.class, this);
		if (lv != null) lv.showCrew(m.id);
	}
	/**
	 * Served With (heromedel, 5.52): each crew member they served with, a link to their page with their portrait before
	 * it; under it the ships they shared ("Onboard:"), and "During Expeditions" if they went out together.
	 */
	private void servedWith(JPanel side, Member m) {
		if (m.with.isEmpty()) return;
		side.add(heading("Served With:"));
		for (Map.Entry<Integer, List<String>> e : m.with.entrySet()) {
			Member o = null;
			for (Member x : members) if (x.id == e.getKey()) o = x;
			if (o == null) continue;
			final Member to = o;
			JLabel name = line("<html><u>" + esc(o.name) + "</u></html>", new Color(150, 200, 255));
			CrewState oc = o.crew();
			if (oc != null) name.setIcon(IconFactory.crewIcon(oc));
			name.setIconTextGap(6);
			name.setToolTipText(o.name + " (" + o.raceTitle() + "): their Crew Log");
			name.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			name.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mouseClicked(java.awt.event.MouseEvent ev) { pick.setSelectedItem(to); } });
			side.add(name);
			List<String> ships = new ArrayList<String>();
			boolean away = false;
			for (String w : e.getValue()) { if (w.equals(CrewRegister.WITH_EXPEDITION)) away = true; else ships.add(w); }
			if (!ships.isEmpty()) {
				side.add(indented("Onboard:", 26));
				for (String sh : ships) { JLabel l = dotted(cap(the(sh)), MenuTheme.TEXT, MenuTheme.GOLD); l.setBorder(BorderFactory.createEmptyBorder(1, 38, 1, 0)); side.add(l); }
			}
			if (away) side.add(indented("During Expeditions", 26));
		}
	}
	private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
	private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
	/** An event's mark: green for joining and coming back, gold for moves and assignments, purple the infirmary, orange taken or missing, red killed. */
	private static Color mark(String t) {
		if (t.startsWith("Killed") || t.startsWith("Lost") || t.contains("presumed dead") || t.startsWith("Did not come back")) return RecordsLog.BAD;
		if (t.startsWith("Taken captive") || t.startsWith("Not found")) return new Color(240, 150, 70);
		if (t.contains("infirmary")) return new Color(170, 110, 230);
		if (t.startsWith("Let go") || t.startsWith("Retired") || t.startsWith("Left the fleet") || t.startsWith("Transferred to") || t.startsWith("Transferred with")) return RecordsLog.DIM;
		if (t.startsWith("Hired") || t.startsWith("Rescued") || t.startsWith("Transferred from") || t.startsWith("Joined") || t.startsWith("Came aboard") || t.startsWith("Back from")
				|| t.startsWith("Ransomed") || t.startsWith("Found again") || t.startsWith("On the station's records")) return RecordsLog.GOOD;
		return MenuTheme.GOLD;
	}
	/** A line with a small round mark before it. */
	private static JLabel indented(String text, int left) {
		JLabel l = line(text, RecordsLog.DIM);
		l.setBorder(BorderFactory.createEmptyBorder(0, left, 0, 0));
		return l;
	}
	private static JLabel dotted(String text, Color c, final Color dot) {
		JLabel l = line(text, c);
		l.setIcon(new Icon() {
			public int getIconWidth() { return 10; }
			public int getIconHeight() { return 10; }
			public void paintIcon(Component cmp, java.awt.Graphics g, int x, int y) {
				java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
				g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(dot);
				g2.fillOval(x + 1, y + 1, 8, 8);
				g2.dispose();
			}
		});
		l.setIconTextGap(8);
		l.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 0));
		return l;
	}
	private static String status(Member m) {
		switch (m.status) {
			case PRESENT: return "Serving " + m.where + (m.laidUp ? "; laid up in the infirmary" : "") + ".";
			case CAPTIVE: return "Captive: " + m.where.replaceFirst("^held captive by ", "held by ") + ".";
			case MISSING: return "MIA: whereabouts unknown.";
			case KILLED: return m.where.isEmpty() ? "KIA." : "KIA: " + m.where + ".";
			case TRANSFERRED: return "Transferred: " + m.where.replaceFirst("^transferred ", "") + ".";
			default: return "Retired from the station's service.";
		}
	}
	private static JLabel heading(String text) {
		JLabel h = new JLabel(text);
		h.setFont(MenuTheme.LABEL_FONT.deriveFont(Font.BOLD));
		h.setForeground(MenuTheme.GOLD);
		h.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(14, 0, 4, 0),
				BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(MenuTheme.GOLD.getRed(), MenuTheme.GOLD.getGreen(), MenuTheme.GOLD.getBlue(), 90))));
		h.setAlignmentX(LEFT_ALIGNMENT);
		h.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, h.getPreferredSize().height));
		return h;
	}
	private static JLabel line(String text, Color c) {
		JLabel l = new JLabel(text);
		l.setFont(MenuTheme.TEXT_FONT);
		l.setForeground(c);
		l.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}
}
