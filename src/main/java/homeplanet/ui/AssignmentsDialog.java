package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.blerf.ftl.parser.SavedGameParser.CrewState;

import homeplanet.core.HomePlanet;
import homeplanet.parser.Assignments;
import homeplanet.parser.XmlText;
import homeplanet.vault.Vault;

/**
 * The crew expeditions board (expedition_type 2): three sectors with work on offer; pick one and who goes, and they're
 * away a while. Nothing here rates a race or a sector: what suits whom, the player learns from the reports. Hiring at
 * the foot, as the other board has it.
 */
public final class AssignmentsDialog extends JDialog {
	private final JPanel cols = new JPanel(new GridLayout(1, Assignments.OFFERS, 12, 0));
	private final JLabel foot = new JLabel();
	private final JButton hireBtn = new JButton();
	/** For the harness: the random rolls. */
	static Random rng = new Random();
	private final java.awt.Component owner;

	/** Opens the board; every return rebuilds the Space Dock, so nothing is reported back. */
	public static void open(java.awt.Component owner) {
		new AssignmentsDialog(owner).setVisible(true);
	}

	private AssignmentsDialog(java.awt.Component owner) {
		super(javax.swing.SwingUtilities.getWindowAncestor(owner), "Expeditions", ModalityType.APPLICATION_MODAL);
		this.owner = owner;
		JPanel body = new JPanel(new BorderLayout(0, 10));
		body.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
		body.add(new JLabel("<html><div style='width:720px'>Crews without a ship of their own find work where it's offered. Pick where to send them and who goes, up to "
				+ Assignments.PARTY_MAX + " from the Cargo Hold. They're away a while, and what they find there is theirs to tell when they're back. Not everyone comes back.</div></html>"), BorderLayout.NORTH);
		body.add(cols, BorderLayout.CENTER);
		JPanel south = new JPanel(new BorderLayout(10, 0));
		south.add(foot, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
		hireBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { ExpeditionsDialog.hire(AssignmentsDialog.this); fill(); } });
		buttons.add(hireBtn);
		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
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
		cols.removeAll();
		for (Assignments.Offer o : Assignments.board(v)) cols.add(card(o));
		int inHold = 0;
		try { inHold = Assignments.holdCrew(v).size(); } catch (IOException e) { }
		List<String> away = new ArrayList<String>(), laidUp = new ArrayList<String>();
		for (Assignments.Away a : Assignments.away(v)) away.addAll(a.names());
		for (homeplanet.parser.Expeditions.Patient x : homeplanet.parser.Expeditions.infirmary(v)) laidUp.add(x.name);
		foot.setText("<html>Crew in the Cargo Hold: " + inHold + ".&nbsp;&nbsp; The Cargo Hold holds " + v.storageScrap() + " scrap."
				+ (away.isEmpty() ? "" : "<br>Away: " + XmlText.text(String.join(", ", away)) + ".")
				+ (laidUp.isEmpty() ? "" : "<br>In the infirmary: " + XmlText.text(String.join(", ", laidUp)) + ".") + "</html>");
		ExpeditionsDialog.hireButton(hireBtn, v);
		cols.revalidate();
		cols.repaint();
		pack();
	}
	private JPanel card(final Assignments.Offer o) {
		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(MenuTheme.GREY_GREEN), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		JLabel words = new JLabel("<html><div style='width:210px'><font color='" + MenuTheme.HTML_GOLD + "'><b>" + XmlText.text(o.title()) + "</b></font><br><br>"
				+ XmlText.text(o.words) + "</div></html>");
		words.setVerticalAlignment(JLabel.TOP);
		words.setPreferredSize(new Dimension(230, 96));
		p.add(words, BorderLayout.CENTER);
		JButton send = new JButton("Send crew...");
		send.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { send(o); } });
		p.add(send, BorderLayout.SOUTH);
		return p;
	}

	private void send(Assignments.Offer o) {
		Vault v = Vault.get();
		List<CrewState> crew;
		try { crew = Assignments.holdCrew(v); } catch (IOException e) { HomePlanet.showErrorDialog("The Cargo Hold can't be read:\n" + e.getMessage()); return; }
		if (crew.isEmpty()) {
			JOptionPane.showMessageDialog(this, "There is no crew in the Cargo Hold to send.\nMove crew there in the Cargo Bay, or post for volunteers.", "Expeditions", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String where = "nebula".equals(o.sector) ? "the nebula" : "the " + o.title();
		List<CrewState> party = ExpeditionsDialog.pickParty(this, "Who goes to " + where + "? Up to " + Assignments.PARTY_MAX + " from the Cargo Hold.", crew);
		if (party == null || party.isEmpty()) return;
		try { Assignments.send(v, o.slot, party, rng); }
		catch (IOException e) { HomePlanet.showErrorDialog("The detail could not set out:\n" + e.getMessage()); return; }
		List<String> names = new ArrayList<String>();
		for (CrewState c : party) names.add(c.getName());
		String who = names.size() == 1 ? names.get(0) : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
		boolean one = names.size() == 1, male = party.get(0).isMale();
		JOptionPane.showMessageDialog(this, who + (one ? " has" : " have") + " set out for " + where + ".\n" + (one ? male ? "He" : "She" : "They")
				+ " should be back in a week or two.", "Expeditions", JOptionPane.INFORMATION_MESSAGE);
		askPending(this);
		fill();
	}

	/** The prizes waiting on the commander's word: a recruit to take on, a ship to the Space Dock, the Junkyard or not at all. */
	public static void askPending(java.awt.Component owner) {
		Vault v = Vault.get();
		for (Assignments.Pending x : Assignments.pendingToAsk(v)) { // a letter's own question is answered in the inbox
			boolean ship = "ship".equals(x.kind);
			Object[] opts = ship ? new Object[] {"Space Dock", "Junkyard", "Don't take her"} : new Object[] {"Sign them on", "Send them on their way"};
			int pick = JOptionPane.showOptionDialog(owner, x.question(), "Expeditions", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
			if (pick < 0) continue; // asked again at the next look
			try { if (pick == opts.length - 1) Assignments.decline(v, x); else Assignments.accept(v, x, ship && pick == 0); }
			catch (IOException e) { HomePlanet.showErrorDialog("That could not be done:\n" + e.getMessage()); }
		}
	}
	/** A report, read in the station's event box (the words as the crew gave them, no rolls), each crew member's face beside their line. */
	public static void showReport(java.awt.Component owner, Assignments.Report r) {
		JOptionPane.showMessageDialog(owner, reportPane(r.text, r.faces), r.title(), JOptionPane.PLAIN_MESSAGE);
	}
	/** The report's words with the crew's faces, wrapped to the event box's width. */
	static javax.swing.JTextPane reportPane(String text, java.util.List<Assignments.Face> faces) {
		javax.swing.JTextPane t = new javax.swing.JTextPane();
		t.setEditable(false); t.setOpaque(false);
		javax.swing.text.SimpleAttributeSet base = new javax.swing.text.SimpleAttributeSet();
		javax.swing.text.StyleConstants.setFontFamily(base, MenuTheme.TEXT_FONT.getFamily());
		javax.swing.text.StyleConstants.setFontSize(base, MenuTheme.TEXT_FONT.getSize());
		try { ReportFaces.insert(t.getStyledDocument(), text, faces, base); }
		catch (javax.swing.text.BadLocationException e) { t.setText(text); } // not expected: the words, at least
		t.setSize(new Dimension(520, Short.MAX_VALUE));
		t.setPreferredSize(new Dimension(520, t.getPreferredSize().height));
		return t;
	}
}
