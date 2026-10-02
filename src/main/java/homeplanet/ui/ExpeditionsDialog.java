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
 * The expeditions board: three postings, each sent crew from the Cargo Hold, and hiring at the foot. An expedition
 * plays out as a few pop-ups, each with its choices (blue where a crew member's race helps, red where it may not).
 */
final class ExpeditionsDialog extends JDialog {
	private final JPanel cols = new JPanel(new GridLayout(1, Expeditions.POSTINGS, 12, 0));
	private final JLabel foot = new JLabel();
	private final JButton hireBtn = new JButton();
	/** Did anything change in the Cargo Hold (the Space Dock redraws)? */
	boolean changed = false;
	/** For the harness: the random rolls. */
	static Random rng = new Random();

	static boolean open(java.awt.Component owner) {
		ExpeditionsDialog d = new ExpeditionsDialog(owner);
		d.setVisible(true);
		return d.changed;
	}

	private ExpeditionsDialog(java.awt.Component owner) {
		super(javax.swing.SwingUtilities.getWindowAncestor(owner), "Expeditions", ModalityType.APPLICATION_MODAL);
		JPanel body = new JPanel(new BorderLayout(0, 10));
		body.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
		body.add(new JLabel("<html><div style='width:720px'>Jobs posted across the sectors for crew without a ship. Send up to "
				+ Expeditions.PARTY_MAX + " from the Cargo Hold: they bring back scrap, now and then some gear, and sometimes injuries. "
				+ "Not everyone comes back. Each expedition takes as long as a beacon.</div></html>"), BorderLayout.NORTH);
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
		int fleet = Expeditions.fleetCrew(v), cost = Expeditions.hireCost(fleet);
		foot.setText("<html>Crew in the Cargo Hold: " + inHold + ".&nbsp;&nbsp; The Cargo Hold holds " + v.storageScrap() + " scrap.</html>");
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
		String danger = x.sealed ? MenuTheme.HTML_GOLD : x.danger >= 3 ? "#d86a4a" : x.danger == 2 ? MenuTheme.HTML_GOLD : MenuTheme.HTML_GREY_GREEN;
		JLabel words = new JLabel("<html><div style='width:210px'><font color='" + MenuTheme.HTML_GOLD + "'><b>" + XmlText.text(x.sector()) + "</b></font><br><br>"
				+ "“" + XmlText.text(x.text) + "”<br><br><font color='" + danger + "'>Danger: " + x.dangerWord() + "</font></div></html>");
		words.setVerticalAlignment(JLabel.TOP);
		p.add(words, BorderLayout.CENTER);
		JButton send = new JButton("Send crew...");
		send.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { send(slot); } });
		p.add(send, BorderLayout.SOUTH);
		return p;
	}

	private void send(int slot) {
		Vault v = Vault.get();
		List<CrewState> crew;
		try { crew = Expeditions.holdCrew(v); } catch (IOException e) { HomePlanet.showErrorDialog("The Cargo Hold can't be read:\n" + e.getMessage()); return; }
		if (crew.isEmpty()) {
			JOptionPane.showMessageDialog(this, "There is no crew in the Cargo Hold to send.\nMove crew there in the Cargo Bay, or post for volunteers.", "Expeditions", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		List<CrewState> party = pickParty(crew);
		if (party == null || party.isEmpty()) return;
		Expeditions.Run run;
		try { run = Expeditions.start(v, slot, party, rng); }
		catch (IOException e) { HomePlanet.showErrorDialog("The expedition could not set out:\n" + e.getMessage()); return; }
		String title = run.posting.realSector();
		if (run.posting.sealed) // the sealed orders open once the shuttle is under way: no turning back now
			JOptionPane.showMessageDialog(this, wrap("The shuttle clears the dock, and the sealed orders unlock. The job is in the " + title + "."), "Sealed orders", JOptionPane.PLAIN_MESSAGE);
		while (!run.over()) {
			Expeditions.Step ev = run.current();
			List<Expeditions.Choice> choices = run.choices();
			int c = -1;
			while (c < 0) c = ask(run, ev, choices, title + " (" + run.number() + " of " + run.length() + ")"); // an expedition can't be walked away from halfway
			String said = run.choose(choices.get(c));
			if (!said.trim().isEmpty()) JOptionPane.showMessageDialog(this, wrap(said), title, JOptionPane.PLAIN_MESSAGE); // a step that only leads on has no words of its own
		}
		for (Expeditions.HomeShip h : run.ships()) { // each ship home: the Space Dock, or the Junkyard
			Object[] where = {"Space Dock", "Junkyard"};
			h.toDock = JOptionPane.showOptionDialog(this, wrap(h.stealth() ? "The cruiser is yours, if you want her. Where should she go?"
					: "Your crew brought a ship home. Send her to the Space Dock, or to the Junkyard?"), "A ship home",
					JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, where, where[0]) == 0;
		}
		try {
			String summary = Expeditions.finish(v, run);
			changed = true;
			JOptionPane.showMessageDialog(this, wrap(summary), "Expedition's end", JOptionPane.INFORMATION_MESSAGE);
			for (Expeditions.HomeShip h : run.ships()) { // a hull on the station's blank copy needs the companion mod before she flies
				if (!h.toDock || h.ship == null) continue;
				List<String> missing = homeplanet.parser.Retrofit.missingBlueprints(h.ship.file());
				if (missing.isEmpty()) continue;
				Object[] opts = {"Patch Now", "Later"};
				int r = JOptionPane.showOptionDialog(this, h.ship.name + " waits at the Space Dock, but can't fly until The Home Planet Station sends her blueprint ("
						+ String.join(", ", missing) + ") to FTL via Slipstream. Without it she can still be scrapped or sold in the Junkyard.", "A ship home",
						JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opts, opts[0]);
				if (r == 0) PatchDialog.open(this);
			}
		} catch (IOException e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not record the expedition; the Cargo Hold is as it was:\n" + e.getMessage());
		}
		fill();
	}
	/** An event: its words, and its choices one above the other, numbered, as FTL lists them. Returns the one taken, or -1. */
	private int ask(Expeditions.Run run, Expeditions.Step ev, List<Expeditions.Choice> choices, String title) {
		JPanel p = new JPanel(new BorderLayout(0, 12));
		p.add(wrap(run.fillEvent(ev.text)), BorderLayout.NORTH);
		JPanel list = new JPanel(new GridLayout(0, 1, 0, 4));
		final JOptionPane op = new JOptionPane(p, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[0]);
		for (int i = 0; i < choices.size(); i++) {
			final int n = i;
			JButton b = new JButton(label(run, choices.get(i), i + 1));
			b.setHorizontalAlignment(JButton.LEFT);
			b.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { op.setValue(Integer.valueOf(n)); } });
			list.add(b);
		}
		p.add(list, BorderLayout.CENTER);
		JDialog d = op.createDialog(this, title);
		d.setVisible(true);
		d.dispose();
		Object v = op.getValue();
		return v instanceof Integer && (Integer) v >= 0 && (Integer) v < choices.size() ? (Integer) v : -1;
	}
	/** A choice as its button shows it, numbered: blue for a race's help, red for its trouble. */
	static String label(Expeditions.Run run, Expeditions.Choice c, int n) {
		String t = n + ". " + XmlText.text(run.label(c));
		if (c.race == null) return "<html>" + t + "</html>";
		return "<html><font color='" + (c.red ? "#e05a4a" : "#6ab8ff") + "'>" + t + "</font></html>";
	}
	private static JLabel wrap(String text) {
		return new JLabel("<html><div style='width:420px'>" + XmlText.text(text).replace("\n", "<br>") + "</div></html>");
	}
	/** Up to three crew from the Cargo Hold, ticked. */
	private List<CrewState> pickParty(List<CrewState> crew) {
		JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
		p.add(new JLabel("Who goes? (up to " + Expeditions.PARTY_MAX + ")"));
		final List<JCheckBox> boxes = new ArrayList<JCheckBox>();
		for (CrewState c : crew) {
			int max = c.getRace() == null ? 100 : c.getRace().getMaxHealth();
			final JCheckBox b = new JCheckBox(c.getName() + " (" + homeplanet.model.Crew.raceTitle(c) + ")" + (c.getHealth() < max ? ", injured" : ""));
			b.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
				int n = 0; for (JCheckBox x : boxes) if (x.isSelected()) n++;
				if (n > Expeditions.PARTY_MAX) b.setSelected(false);
			} });
			boxes.add(b);
			p.add(b);
		}
		if (boxes.size() <= Expeditions.PARTY_MAX) for (JCheckBox b : boxes) b.setSelected(true);
		else for (int i = 0; i < Expeditions.PARTY_MAX; i++) boxes.get(i).setSelected(true);
		Object[] opts = {"Send them", "Cancel"};
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
