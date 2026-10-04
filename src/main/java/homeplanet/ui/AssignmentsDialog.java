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
		JOptionPane.showMessageDialog(this, String.join(", ", names) + (names.size() > 1 ? " have" : " has") + " set out for " + where + ". Word comes when they're back.", "Expeditions", JOptionPane.INFORMATION_MESSAGE);
		if (owner instanceof SpaceDockUI) ((SpaceDockUI) owner).timeRound(false); // setting out takes a beacon: an earlier detail may be back
		fill();
	}

	/** A report, read in the station's event box (the words as the crew gave them, no rolls). */
	public static void showReport(java.awt.Component owner, Assignments.Report r) {
		javax.swing.JTextArea t = new javax.swing.JTextArea(r.text);
		t.setEditable(false); t.setLineWrap(true); t.setWrapStyleWord(true); t.setOpaque(false);
		t.setFont(MenuTheme.TEXT_FONT);
		t.setColumns(52);
		t.setSize(new Dimension(520, 10));
		JOptionPane.showMessageDialog(owner, t, r.title(), JOptionPane.PLAIN_MESSAGE);
	}
}
