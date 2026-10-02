package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.blerf.ftl.parser.SavedGameParser.CrewState;

import homeplanet.core.HomePlanet;
import homeplanet.parser.Expeditions;
import homeplanet.parser.XmlText;
import homeplanet.vault.Vault;

/**
 * The expeditions board: three postings the commander can sign on to with crew from the Cargo Hold, and hiring at the
 * foot. An expedition plays out as FTL plays a beacon: the situation, numbered choices (blue where a crew member's
 * race opens one), the outcome, "Continue...".
 */
final class ExpeditionsDialog extends JDialog {
	private final JPanel cols = new JPanel(new GridLayout(1, Expeditions.POSTINGS, 12, 0));
	private final JLabel foot = new JLabel();
	private final JButton hireBtn = new JButton();
	/** Did anything change in the Cargo Hold (the Space Dock redraws)? */
	boolean changed = false;
	/** Marks an expedition's own pop-ups (for the harness, which can't go by their titles). */
	static final String EXPEDITION = "homeplanet.expedition";
	/** For the harness: the random rolls. */
	static Random rng = new Random();
	/** An expedition is under way: Long Range messages pop up over it, and hails are turned away (it can't be left halfway). */
	private static volatile boolean underWay = false;
	static boolean underWay() { return underWay; }
	/** What a hailing commander is told while an expedition is under way, or null. */
	static String awayNotice(String commander) { return underWay ? commander + " is away on an expedition. Hail again shortly." : null; }
	/** FTL's blue for an option a crew member's race opens. */
	static final String BLUE = "#6ab8ff";

	/** An event's window, as FTL's: narrow (its words wrap at TEXT_W), and at least EVENT_H tall on every screen of the job. */
	static final int EVENT_H = 380, TEXT_W = 380;

	/**
	 * The board; when the commander signs on, it closes while the job plays (in its own windows), and opens again,
	 * fresh, when the job is over. Did anything change in the Cargo Hold?
	 */
	static boolean open(java.awt.Component owner) {
		boolean changed = false;
		while (true) {
			ExpeditionsDialog d = new ExpeditionsDialog(owner);
			d.setVisible(true);
			changed |= d.changed;
			if (d.signedOn == null) return changed;
			changed |= play(owner, d.signedOn, Vault.get());
		}
	}
	/** The job signed on for, to play once the board has closed (null: the board was just closed). */
	Expeditions.Run signedOn;

	private ExpeditionsDialog(java.awt.Component owner) {
		super(javax.swing.SwingUtilities.getWindowAncestor(owner), "Expeditions", ModalityType.APPLICATION_MODAL);
		JPanel body = new JPanel(new BorderLayout(0, 10));
		body.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
		body.add(new JLabel("<html><div style='width:720px'>Jobs posted for crews without a ship of their own. Sign on, and take up to "
				+ Expeditions.PARTY_MAX + " from the Cargo Hold with you. The pay is what the job pays, if it pays. Not everyone comes back.</div></html>"), BorderLayout.NORTH);
		body.add(cols, BorderLayout.CENTER);
		JPanel south = new JPanel(new BorderLayout(10, 0));
		south.add(foot, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
		hireBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { hire(); } });
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
		List<Expeditions.Posting> board = Expeditions.board(v);
		cols.removeAll();
		for (int i = 0; i < board.size(); i++) cols.add(card(i, board.get(i)));
		int inHold = 0;
		try { inHold = Expeditions.holdCrew(v).size(); } catch (IOException e) { }
		List<String> laidUp = new ArrayList<String>();
		for (Expeditions.Patient x : Expeditions.infirmary(v)) laidUp.add(x.name);
		int fleet = Expeditions.fleetCrew(v), cost = Expeditions.hireCost(fleet);
		foot.setText("<html>Crew in the Cargo Hold: " + inHold + ".&nbsp;&nbsp; The Cargo Hold holds " + v.storageScrap() + " scrap."
				+ (laidUp.isEmpty() ? "" : "<br>In the infirmary: " + XmlText.text(String.join(", ", laidUp)) + ".") + "</html>");
		hireBtn.setText(fleet == 0 ? "Post a promise of adventure" : "Post for volunteers: " + cost + " scrap");
		hireBtn.setToolTipText(fleet == 0 ? "Free: with no crew anywhere, a promise of adventure is all you can offer. Someone may answer."
				: "5 scrap for each crew member in your fleet (" + fleet + "), at most 60: paid whether or not anyone answers. New crew wait in the Cargo Hold.");
		hireBtn.setEnabled(cost <= v.storageScrap());
		cols.revalidate();
		cols.repaint();
		pack();
	}
	private JPanel card(final int slot, Expeditions.Posting x) {
		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(MenuTheme.GREY_GREEN), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		JLabel words = new JLabel("<html><div style='width:210px'><font color='" + MenuTheme.HTML_GOLD + "'><b>" + XmlText.text(x.title()) + "</b></font><br><br>"
				+ XmlText.text(x.text) + "</div></html>");
		words.setVerticalAlignment(JLabel.TOP);
		p.add(words, BorderLayout.CENTER);
		JButton send = new JButton("Sign on...");
		send.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { send(slot); } });
		p.add(send, BorderLayout.SOUTH);
		return p;
	}

	private void send(int slot) {
		Vault v = Vault.get();
		List<CrewState> crew;
		try { crew = Expeditions.holdCrew(v); } catch (IOException e) { HomePlanet.showErrorDialog("The Cargo Hold can't be read:\n" + e.getMessage()); return; }
		if (crew.isEmpty()) {
			JOptionPane.showMessageDialog(this, "There is no crew in the Cargo Hold to take along.\nMove crew there in the Cargo Bay, or post for volunteers.", "Expeditions", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		List<CrewState> party = pickParty(crew);
		if (party == null || party.isEmpty()) return;
		try { signedOn = Expeditions.start(v, slot, party, rng); }
		catch (IOException e) { HomePlanet.showErrorDialog("The expedition could not set out:\n" + e.getMessage()); return; }
		dispose(); // the board steps aside while the job plays
	}
	/**
	 * An expedition, start to finish: the situation and its choices, each outcome, the last with word of anyone carried
	 * to the infirmary. Its windows can't be closed, only answered. Did it reach the Cargo Hold?
	 */
	static boolean play(java.awt.Component owner, Expeditions.Run run, Vault v) {
		String title = run.posting.title();
		underWay = true;
		try {
			while (!run.over()) {
				List<Expeditions.Choice> choices = run.choices();
				int c = -1;
				while (c < 0) c = ask(owner, run.text(), labels(run, choices), title); // an expedition can't be walked away from halfway
				String said = run.choose(choices.get(c));
				if (!run.over()) continue;
				String home;
				try { home = Expeditions.finish(v, run); }
				catch (IOException e) {
					ask(owner, said, new String[] {"1. Continue..."}, title);
					HomePlanet.showErrorDialog("The Home Planet Station could not record the expedition; the Cargo Hold is as it was:\n" + e.getMessage());
					return false;
				}
				ask(owner, home.isEmpty() ? said : said + "\n\n" + home, new String[] {"1. Continue..."}, title);
				return true;
			}
			return false;
		} finally { underWay = false; }
	}
	private static String[] labels(Expeditions.Run run, List<Expeditions.Choice> choices) {
		String[] out = new String[choices.size()];
		for (int i = 0; i < out.length; i++) out[i] = label(run, choices.get(i), i + 1);
		return out;
	}
	/**
	 * A screen of the job, laid out as FTL's: the words at the top, the numbered choices one above the other under them,
	 * in a window that's the same size every time (taller only if the words need it). No closing it, only a choice.
	 * Returns the one taken, or -1.
	 */
	private static int ask(java.awt.Component owner, String text, String[] choices, String title) {
		JPanel p = new JPanel(new BorderLayout(0, 14));
		p.add(wrap(text), BorderLayout.NORTH);
		JPanel list = new JPanel(new GridLayout(0, 1, 0, 6));
		final JOptionPane op = new JOptionPane(p, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[0]);
		for (int i = 0; i < choices.length; i++) {
			final int n = i;
			JButton b = new JButton(choices[i]);
			b.setHorizontalAlignment(JButton.LEFT);
			b.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { op.setValue(Integer.valueOf(n)); } });
			list.add(b);
		}
		JPanel under = new JPanel(new BorderLayout());
		under.add(list, BorderLayout.NORTH); // the choices keep their own height, under the words
		p.add(under, BorderLayout.CENTER);
		java.awt.Dimension want = p.getPreferredSize();
		p.setPreferredSize(new java.awt.Dimension(want.width, Math.max(EVENT_H, want.height))); // as wide as the words need, never shorter
		JDialog d = op.createDialog(owner, title);
		d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE); // a choice must be made
		d.getRootPane().putClientProperty(EXPEDITION, Boolean.TRUE);
		d.setVisible(true);
		d.dispose();
		Object v = op.getValue();
		return v instanceof Integer && (Integer) v >= 0 && (Integer) v < choices.length ? (Integer) v : -1;
	}
	/** A choice as its button shows it, numbered, wrapped to the window: blue where a crew member's race opens it. */
	static String label(Expeditions.Run run, Expeditions.Choice c, int n) {
		String t = n + ". " + XmlText.text(run.label(c));
		String div = "<div style='width:" + (TEXT_W - 40) + "px'>";
		if (c.race == null) return "<html>" + div + t + "</div></html>";
		return "<html>" + div + "<font color='" + BLUE + "'>" + t + "</font></div></html>";
	}
	private static JLabel wrap(String text) {
		return new JLabel("<html><div style='width:" + TEXT_W + "px'>" + XmlText.text(text).replace("\n", "<br>") + "</div></html>");
	}
	/** Up to three crew from the Cargo Hold, ticked. */
	private List<CrewState> pickParty(List<CrewState> crew) {
		JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
		p.add(new JLabel("Who goes with you? (up to " + Expeditions.PARTY_MAX + ")"));
		final List<JCheckBox> boxes = new ArrayList<JCheckBox>();
		for (CrewState c : crew) {
			final JCheckBox b = new JCheckBox(c.getName() + " (" + homeplanet.model.Crew.raceTitle(c) + ")");
			b.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
				int n = 0; for (JCheckBox x : boxes) if (x.isSelected()) n++;
				if (n > Expeditions.PARTY_MAX) b.setSelected(false);
			} });
			boxes.add(b);
			p.add(b);
		}
		if (boxes.size() <= Expeditions.PARTY_MAX) for (JCheckBox b : boxes) b.setSelected(true);
		else for (int i = 0; i < Expeditions.PARTY_MAX; i++) boxes.get(i).setSelected(true);
		Object[] opts = {"Set out", "Cancel"};
		if (JOptionPane.showOptionDialog(this, p, "Expeditions", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]) != 0) return null;
		List<CrewState> out = new ArrayList<CrewState>();
		for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).isSelected()) out.add(crew.get(i));
		return out;
	}

	private void hire() {
		Vault v = Vault.get();
		int fleet = Expeditions.fleetCrew(v), cost = Expeditions.hireCost(fleet);
		if (cost > 0 && !HomePlanet.confirmNo(this, "Post for volunteers for " + cost + " scrap from the Cargo Hold?\nThe scrap is spent whether or not anyone answers.", "Expeditions")) return;
		CrewState c;
		try { c = Expeditions.hire(v, rng); }
		catch (IOException e) { HomePlanet.showErrorDialog("The posting was called off. Nothing was changed:\n" + e.getMessage()); fill(); return; }
		changed = true;
		JOptionPane.showMessageDialog(this, c == null ? (cost == 0 ? "No one answered the promise of adventure. It costs nothing to try again." : "No one answered this time.")
				: c.getName() + " (" + homeplanet.model.Crew.raceTitle(c) + ") answered, and is waiting in the Cargo Hold.", "Expeditions", JOptionPane.INFORMATION_MESSAGE);
		fill();
	}
}
