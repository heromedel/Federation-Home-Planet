package homeplanet.ui;

import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import homeplanet.core.HomePlanet;

/**
 * The station's rules as checkboxes, shared by Settings and the first-run House Rules window so both read the same.
 * Nothing changes until apply(). Immersive Mode sets and greys out the rules it decides.
 */
public class RuleBoxes {
	/** Run after the Immersive button switched fleets (Settings refreshes what each fleet keeps apart). */
	Runnable afterFleetChange;


	/** Immersive Mode, as the boxes show it (switched by the button, at once: see ImmersiveDialog). */
	final JCheckBox immersiveBox = new JCheckBox("", HomePlanet.immersiveMode);
	private final javax.swing.JButton immersiveButton = new javax.swing.JButton();
	private final JLabel immersiveLabel = new JLabel();
	private final JPanel immersiveRow = row(0);
	final JCheckBox tradeBox = new JCheckBox("Trading and scrapping need a station (the ship must be at a beacon with a store)", HomePlanet.storeRequirement);
	final JCheckBox journeyBox = new JCheckBox("New Journey needs a station (the boarded ship must be at a beacon with a store)", HomePlanet.journeyStoreRequirement);
	final JCheckBox scrapBox = new JCheckBox("Allow stripping when scrapping: " + homeplanet.model.Words.her() + " systems can go to the Cargo Hold, at a discount on the removal fee", HomePlanet.stripAllowed);
	final JComboBox<String> removalBox = new JComboBox<String>(new String[] {"not allowed", "free", "25 scrap", "50 scrap", "75 scrap"});
	private final JLabel removalLabel = new JLabel("Refit: taking a system off a ship is  ");
	private final JPanel removalRow = row(21);
	final JComboBox<String> journeyFeeBox = new JComboBox<String>(new String[] {"free", "200 scrap", "500 scrap", "1000 scrap"});
	private final JLabel journeyFeeLabel = new JLabel("A New Journey costs  ");
	private final JLabel journeyFeeAfter = new JLabel("  from the Cargo Hold");
	private final JPanel journeyFeeRow = row(21);
	final JCheckBox augmentBox = new JCheckBox("An augment thrown away for want of room is shipped home by " + homeplanet.model.Words.her() + " crew, to the inbox (or the Cargo Hold)", HomePlanet.augmentsHome);
	final JCheckBox sellBox = new JCheckBox("Allow selling missiles and drone parts (house rule: FTL's stores don't buy them; half the store price)", HomePlanet.sellSupplies);
	final JCheckBox sellSystemsBox = new JCheckBox("Allow selling stored systems (house rule: half the system's price, plus half the upgrades paid for)", HomePlanet.sellSystems);
	final JCheckBox lockedBox = new JCheckBox("Locked ship models cannot be commissioned (as unlocked in your FTL profile)", HomePlanet.commissionUnlockedOnly);
	final JCheckBox customLockedBox = new JCheckBox("Custom ships based on locked models cannot be commissioned", HomePlanet.commissionCustomUnlockedOnly);
	final JCheckBox costBox = new JCheckBox("Commissioning a ship costs scrap, paid from the Cargo Hold, at", HomePlanet.commissionCosts);
	final JComboBox<String> percentBox = new JComboBox<String>(new String[] {"100%", "75%", "50%"});
	private final JPanel costRow = row(0);
	private static final String[] FREE_KEYS = {"kestrel", "relief", "any"};
	final JComboBox<String> freeBox = new JComboBox<String>(new String[] {"Kestrel Type A or the Relief Ship", "Relief Ship Type A", "Any"});
	/** Each choice's explanation, shown as the list is open. */
	private static final String[] FREE_TIPS = {
		"A standard Kestrel Type A, as a new FTL game starts, or the Relief Ship Type A",
		"The Relief Ship Type A: a Kestrel Type A stripped to basics (one crew, a Burst Laser I and an Ion Blast, every system at its minimum, a reactor of 6)",
		"Any ship you choose at Commission, the Relief Ship Type A among them"};
	private String freeTip = "";
	private String careerTip = null;
	private boolean showingCareerOwn = !HomePlanet.immersiveMode;
	private final JLabel freeLabel = new JLabel("Plead for New Ship grants:  ");
	private final JPanel freeRow = row(22);
	final JCheckBox notifyBox = new JCheckBox("Immersive Notifications: transmissions from The Federation Home Planet (commission orders, news), in an inbox on the Space Dock", HomePlanet.immersiveNotifications);
	final JCheckBox careerBox = new JCheckBox("Career messages: a welcome, promotions, rewards for FTL achievements and a stipend, in Sandbox Mode too", HomePlanet.careerMessages);
	final JCheckBox repBox = new JCheckBox("Reputation: earn and lose reputation points for your ships' service, shown on the Space Dock", HomePlanet.reputationOn);
	/** How Reputation Can be Used (heromedel's words): any mode, never locked by Immersive Mode. */
	final JComboBox<String> repUseBox = new JComboBox<String>(REP_USE_OPTIONS);
	private final JLabel repUseLabel = new JLabel("How Reputation Can be Used:  ");
	/** What each option does, one per option (heromedel, 5.26): the open list's tooltips, and the info window's paragraphs. */
	static final String[] REP_USE_TIPS = {
		"<html>Reputation can pay a New Journey's fee, and a plea for a new ship can keep the Cargo Hold.<br>A promise of adventure and a day's rest cost reputation. Everything else is paid in scrap.</html>",
		"<html>Everything in New Journeys and Pleads, plus what vanilla FTL can't do:<br>taking a system off at Refit, stripping systems when scrapping, and a custom work order's reputation share.</html>",
		"<html>Reputation is never spent. A plea gives up the Cargo Hold;<br>a promise of adventure and rest are free. Everything is paid in scrap.</html>"};
	private final CargoParts.IconButton repUseInfo = new CargoParts.IconButton(CargoParts.infoIcon(), "What each option does", new ActionListener() {
		public void actionPerformed(ActionEvent e) { repUseInfo(repUseBox); }
	});
	/** Ranks (heromedel, 5.56): from reputation, from the Federation Cruiser, or none; Immersive Mode is locked to Ranks From Rep. */
	final JComboBox<String> ranksBox = new JComboBox<String>(homeplanet.parser.PlayerRank.OPTIONS);
	private final JLabel ranksLabel = new JLabel("Ranks:  ");
	private final JPanel ranksRow = row(22); // under Reputation, which Ranks From Rep needs
	private boolean showingRanksOwn = !HomePlanet.immersiveMode;
	private static final String RANKS_TIP = "<html>Your rank as a career climbs: by reputation (Major to Admiral), by the Federation Cruiser layouts you unlock<br>"
			+ "(Commander, Captain, Commodore), or none. It sets the stipend's share for each achievement.</html>";
	/** The three options, in heromedel's words (the setting's own list, and the Immersive briefing's). */
	static final String[] REP_USE_OPTIONS = {"New Journeys and Pleads", "Vanilla-Breaking Actions", "Only as a score"};
	/** A list whose open options say what each does (Settings, and the Immersive briefing, 5.27). */
	static void explainOptions(final JComboBox<String> box) {
		box.setRenderer(new javax.swing.DefaultListCellRenderer() {
			@Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus) {
				java.awt.Component c = super.getListCellRendererComponent(list, value, index, selected, focus);
				int i = index >= 0 ? index : box.getSelectedIndex();
				if (c instanceof JComponent && i >= 0 && i < REP_USE_TIPS.length) ((JComponent) c).setToolTipText(REP_USE_TIPS[i]);
				return c;
			}
		});
	}
	/** The info window: each option and what it changes, then what holds in all of them. */
	static void repUseInfo(java.awt.Component near) {
		String gold = MenuTheme.HTML_GOLD;
		String[] names = REP_USE_OPTIONS;
		String[][] effects = {
			{"A New Journey's fee can be paid in reputation, or in whatever scrap the Cargo Hold has, with reputation for the rest.",
				"A plea for a new ship can keep the Cargo Hold, at a share of reputation set by the difficulty.",
				"A promise of adventure (hiring with no crew left) and a day's rest in quarters cost reputation.",
				"Refit removal, stripping when scrapping and custom work orders are paid in scrap."},
			{"Everything New Journeys and Pleads allows.",
				"Taking a system off at Refit, at the fee your rules set, in scrap or reputation.",
				"Stripping systems when scrapping a ship, at the fee for each system, in scrap or reputation.",
				"A custom work order past FTL's System Limit is paid half in scrap, half in reputation."},
			{"Reputation is a score only: nothing ever spends it.",
				"A plea for a new ship gives up the Cargo Hold.",
				"A promise of adventure and a day's rest are free.",
				"Everything else is paid in scrap."}};
		StringBuilder h = new StringBuilder("<html><body style='width:460px'>");
		for (int i = 0; i < names.length; i++) {
			h.append("<p style='margin-top:").append(i == 0 ? 0 : 10).append("px'><font color='").append(gold).append("'><b>").append(names[i]).append("</b></font></p><ul style='margin-left:16px'>");
			for (String x : effects[i]) h.append("<li>").append(x).append("</li>");
			h.append("</ul>");
		}
		h.append(costTable());
		h.append("<p style='margin-top:10px'><font color='").append(MenuTheme.HTML_GREY_GREEN).append("'>A plea, a promise of adventure and a day's rest can take reputation below zero; nothing else can.</font></p></body></html>");
		javax.swing.JOptionPane.showMessageDialog(javax.swing.SwingUtilities.getWindowAncestor(near), new JLabel(h.toString()), "How Reputation Can be Used", javax.swing.JOptionPane.PLAIN_MESSAGE);
	}
	/**
	 * What things cost by difficulty (heromedel, 5.42), from the difficulties' own rules: Easy, Normal and Hard, the
	 * career's own column too when it's Custom; the difficulty in use in gold. Sandbox Mode's come from its house rules.
	 */
	private static String costTable() {
		homeplanet.parser.CareerRules[] d = {homeplanet.parser.CareerRules.of("easy"), homeplanet.parser.CareerRules.of("normal"), homeplanet.parser.CareerRules.of("hard")};
		String[] heads = {"Easy", "Normal", "Hard"};
		homeplanet.parser.CareerRules now = HomePlanet.immersiveMode ? homeplanet.parser.CareerRules.current() : null;
		java.util.List<homeplanet.parser.CareerRules> cols = new java.util.ArrayList<homeplanet.parser.CareerRules>(java.util.Arrays.asList(d));
		java.util.List<String> names = new java.util.ArrayList<String>(java.util.Arrays.asList(heads));
		if (now != null && "custom".equals(now.name)) { cols.add(now); names.add("Custom"); }
		String gold = MenuTheme.HTML_GOLD, dim = MenuTheme.HTML_GREY_GREEN;
		StringBuilder t = new StringBuilder("<p style='margin-top:10px'><font color='").append(gold).append("'><b>What they cost, by difficulty</b></font></p>");
		t.append("<table cellspacing='0' cellpadding='2'><tr><td></td>");
		for (int i = 0; i < cols.size(); i++) {
			boolean inUse = now != null && now.name.equals(cols.get(i).name);
			t.append("<td align='right'><font color='").append(inUse ? gold : dim).append("'>").append(inUse ? "<b>" + names.get(i) + "</b>" : names.get(i)).append("</font></td>");
		}
		t.append("</tr>");
		String[] rows = {"New Journey fee", "A plea that keeps the Cargo Hold (% of the shortfall)", "Refit, each system taken off", "Stripping, each system", "Custom work order (scrap and reputation, each)"};
		for (int r = 0; r < rows.length; r++) {
			t.append("<tr><td>").append(rows[r]).append("&nbsp;&nbsp;</td>");
			for (int i = 0; i < cols.size(); i++) {
				homeplanet.parser.CareerRules c = cols.get(i);
				int n = r == 0 ? c.journeyFee() : r == 1 ? c.pleaPercent() : r == 2 ? c.removalFee() : r == 3 ? c.stripFee() : c.workOrder();
				boolean inUse = now != null && now.name.equals(c.name);
				String v = r == 1 ? n + "%" : Integer.toString(n);
				t.append("<td align='right'>").append(inUse ? "<font color='" + gold + "'><b>" + v + "</b></font>" : v).append("</td>");
			}
			t.append("</tr>");
		}
		t.append("</table>");
		if (!HomePlanet.immersiveMode) t.append("<p style='margin-top:4px'><font color='").append(dim).append("'>In Sandbox Mode, your house rules set the New Journey and Refit fees.</font></p>");
		return t.toString();
	}
	private final JPanel repUseRow = row(22);
	/** Reputation earned (heromedel, 6.13): Sandbox Mode's own, changeable anytime; a career's shown, as its difficulty (or Custom's choice) has it. */
	private final JPanel rateRow = row(22);
	private final JLabel rateLabel = new JLabel("Reputation earned:  ");
	final JComboBox<String> rateBox = new JComboBox<String>(homeplanet.vault.Reputation.RATE_WORDS);
	final JCheckBox unlockBox = new JCheckBox("Each ship unlocked in FTL from now on can be commissioned free, once", HomePlanet.unlockFreeShips);

	/** The rules Immersive Mode sets, with their own tooltips (shown again when it's off). */
	private final JComponent[] locked = {tradeBox, journeyBox, sellBox, sellSystemsBox, costBox, percentBox, unlockBox, lockedBox, customLockedBox, notifyBox, repBox,
			removalBox, removalLabel, journeyFeeBox, journeyFeeLabel, scrapBox, augmentBox};
	private final String[] tips = new String[locked.length];
	private static final String SET_BY_IMMERSIVE = "Set by Immersive Mode";

	private static JPanel row(int indent) {
		JPanel p = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		p.setOpaque(false);
		if (indent > 0) p.setBorder(BorderFactory.createEmptyBorder(0, indent, 0, 0)); // nested under the rule above
		return p;
	}

	/** Whether the boxes show Immersive Mode's rules (so unticking it puts the player's own back). */
	private boolean showingImmersive = HomePlanet.immersiveMode;

	public RuleBoxes() {
		if (HomePlanet.immersiveMode) showOwn(); // the boxes start from the player's own rules; sync() sets Immersive Mode's over them
		scrapBox.setToolTipText("Optional systems only, each for 10 scrap (20 when Refit charges 50, 30 when it charges 75; free when Refit is free). Standard equipment and damaged systems are lost with the hull. "
				+ "Off: " + homeplanet.model.Words.her() + " systems are lost with the hull");
		removalBox.setSelectedIndex(indexOf(homeplanet.core.Economy.REMOVAL_FEES, HomePlanet.removalFee));
		removalBox.setToolTipText("The Refit tab's Uninstall button: what the boarded ship pays to take one of " + homeplanet.model.Words.her() + " systems off, or whether " + homeplanet.model.Words.she() + " can at all");
		removalLabel.setToolTipText(removalBox.getToolTipText());
		removalRow.add(removalLabel);
		removalRow.add(removalBox);
		journeyFeeBox.setSelectedIndex(indexOf(homeplanet.core.Economy.JOURNEY_FEES, HomePlanet.journeyFee));
		journeyFeeBox.setToolTipText("What The Federation Home Planet charges to plot a New Journey, paid from the Cargo Hold");
		journeyFeeLabel.setToolTipText(journeyFeeBox.getToolTipText());
		journeyFeeRow.add(journeyFeeLabel);
		journeyFeeRow.add(journeyFeeBox);
		journeyFeeRow.add(journeyFeeAfter);
		sellBox.setToolTipText("Shows a sell button under the supplies in the Cargo Bay: 3 scrap a missile, 4 a drone part. Junking them is always possible");
		augmentBox.setToolTipText("FTL asks which augment to throw away when a fourth comes aboard away from a store: the one thrown away comes home after " + homeplanet.model.Words.her() + " next jump. Off: it's lost, as in FTL");
		sellSystemsBox.setToolTipText("Shows a Sell button beside each system stored in the Cargo Bay (Refit tab). The boarded ship is paid");
		lockedBox.setToolTipText("Commission only offers the layouts (A, B, C) you have unlocked in FTL");
		customLockedBox.setToolTipText("A starter blueprint is offered only once the layout " + homeplanet.model.Words.she() + " was remodeled from is unlocked");
		customLockedBox.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
		costBox.setToolTipText(homeplanet.model.Words.cap(homeplanet.model.Words.her()) + " hull, reactor, systems and levels, weapons, drones, augments, crew, supplies, scrap, rooms and doors, strictly counted. The Commission window shows the price");
		percentBox.setToolTipText("The share of " + homeplanet.model.Words.her() + " price that counts, wherever a ship is priced: Commission, Trade In, Auction, the Junkyard's parts and derelicts, a final victory. Never the stores' prices (an Immersive career: Easy 50%, Normal 75%, Hard 100%)");
		percentBox.setSelectedItem(HomePlanet.commissionPercent + "%");
		costRow.add(costBox);
		costRow.add(javax.swing.Box.createHorizontalStrut(6));
		costRow.add(percentBox);
		costRow.add(new JLabel("  of " + homeplanet.model.Words.her() + " price"));
		int free = java.util.Arrays.asList(FREE_KEYS).indexOf(homeplanet.parser.FreeCommand.norm(HomePlanet.freeShip));
		freeBox.setSelectedIndex(free < 0 ? 0 : free);
		freeTip = "The ship Plead for New Ship (Other... at the Space Dock) offers. The Relief Ship Type A is always offered too. "
				+ "A new fleet always starts with a Kestrel Type A";
		freeBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
			@Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus) {
				java.awt.Component c = super.getListCellRendererComponent(list, value, index, selected, focus);
				if (index >= 0 && index < FREE_TIPS.length) list.setToolTipText(selected ? FREE_TIPS[index] : list.getToolTipText());
				return c;
			}
		});
		freeBox.setToolTipText(freeTip);
		freeLabel.setToolTipText(freeTip);
		freeRow.add(freeLabel);
		freeRow.add(freeBox);
		notifyBox.setToolTipText("<html>Orders for the free ships the rules grant, the Liaison's word when you're left without a ship, and letters you can reply to."
				+ "<br>With Career messages (always, in Immersive Mode), also the welcome, promotions, achievement rewards and the stipend.</html>");
		careerBox.setToolTipText("Your rank rises as you unlock FTL's Federation Cruisers; achievements earned from now on are rewarded, and the stipend comes every "
				+ homeplanet.parser.Career.MONTHS_PER_STIPEND + " months. Your fleet and rules stay your own. (Always on in Immersive Mode, at its difficulty.)");
		careerBox.setBorder(BorderFactory.createEmptyBorder(0, 44, 0, 0)); // under Immersive Notifications, which it needs
		careerTip = careerBox.getToolTipText();
		repBox.setToolTipText("<html>Your standing with The Federation Home Planet: earned by sectors, scrap, ships defeated and the Rebel Flagship,"
				+ "<br>lost by crew killed and ships lost in action (never in sector 8). Click it on the Space Dock for the Career Reputation Log.</html>");
		repUseBox.setSelectedIndex(Math.max(0, Math.min(2, HomePlanet.reputationUse - 1)));
		String repUseTip = "Hover an option for what it does, or click the info icon";
		repUseBox.setToolTipText(repUseTip);
		repUseLabel.setToolTipText(repUseTip);
		// each option says what it does while the list is open (heromedel, 5.26)
		explainOptions(repUseBox);
		repUseInfo.setEnabled(false);
		repUseRow.add(repUseLabel);
		repUseRow.add(repUseBox);
		repUseRow.add(javax.swing.Box.createHorizontalStrut(6));
		repUseRow.add(repUseInfo);
		repBox.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { sync(); } });
		rateRow.add(rateLabel);
		rateRow.add(rateBox);
		rateBox.setSelectedIndex(Math.max(0, Math.min(2, HomePlanet.reputationRate)));
		ranksRow.setOpaque(false);
		ranksRow.add(ranksLabel);
		ranksRow.add(ranksBox);
		ranksBox.setSelectedIndex(homeplanet.parser.PlayerRank.setting);
		ranksBox.setToolTipText(RANKS_TIP);
		ranksLabel.setToolTipText(RANKS_TIP);
		ranksBox.setRenderer(new javax.swing.DefaultListCellRenderer() { // Ranks From Rep greyed out without Reputation, with heromedel's tooltip
			@Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean sel, boolean focus) {
				java.awt.Component c = super.getListCellRendererComponent(list, value, index, sel, focus);
				boolean rep = index == 0 || (index < 0 && ranksBox.getSelectedIndex() == 0);
				if (rep && !repBox.isSelected()) c.setForeground(java.awt.Color.GRAY);
				list.setToolTipText(index == 0 ? homeplanet.parser.PlayerRank.REP_TIP : null);
				return c;
			}
		});
		ranksBox.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
			if (ranksBox.isEnabled() && ranksBox.getSelectedIndex() == 0 && !repBox.isSelected()) ranksBox.setSelectedIndex(1); // needs Reputation
		} });
		unlockBox.setToolTipText("Only ships unlocked after this is turned on count, each layout (A, B, C) once. A plea for a new ship doesn't reset it");
		unlockBox.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
		for (int i = 0; i < locked.length; i++) tips[i] = locked[i].getToolTipText();
		ActionListener sync = new ActionListener() { public void actionPerformed(ActionEvent e) { sync(); } };
		immersiveBox.addActionListener(sync);
		immersiveRow.add(immersiveButton);
		immersiveRow.add(immersiveLabel);
		immersiveButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String was = homeplanet.vault.Vault.get().slot;
				if (!SwitchModeDialog.show(immersiveButton)) return;
				if (!homeplanet.vault.Vault.get().slot.equals(was) && MainFrame.modeSwitched()) return; // Settings closed with everything else
				immersiveBox.setSelected(HomePlanet.immersiveMode);
				sync();
				if (afterFleetChange != null) afterFleetChange.run(); // Settings shows the other fleet's choices now
			}
		});
		costBox.addActionListener(sync);
		notifyBox.addActionListener(sync); // Career messages need it
		lockedBox.addActionListener(sync);
		sync();
	}

	private boolean showingRateCareer;
	/** Greys out what depends on an unticked rule, and sets what Immersive Mode decides. */
	private void sync() {
		boolean im = immersiveBox.isSelected();
		boolean vaultOpen = homeplanet.vault.Vault.isOpen();
		immersiveButton.setText("Switch Game Mode...");
		immersiveButton.setEnabled(vaultOpen);
		immersiveButton.setToolTipText(!vaultOpen ? "Once The Home Planet Station is set up, switch modes from Settings"
				: "Sandbox Mode, or an Immersive career (Easy, Normal, Hard, Custom): each has a fleet of its own");
		homeplanet.parser.CareerRules career = im ? homeplanet.parser.CareerRules.current() : null;
		String mode = vaultOpen ? homeplanet.vault.Vault.title(homeplanet.vault.Vault.get().slot) : "Sandbox Mode";
		immersiveLabel.setText(im ? "   " + mode + " is in use" + (career != null && homeplanet.vault.Vault.CUSTOM.equals(homeplanet.vault.Vault.get().slot) ? " (" + career.title() + ")" : "")
				+ ": The Federation Home Planet's rules below are locked." : "   Sandbox Mode is in use: the rules below are yours. An Immersive career runs by The Federation Home Planet's.");
		if (!im && showingImmersive) showOwn();
		showingImmersive = im;
		if (im) {
			tradeBox.setSelected(true);
			journeyBox.setSelected(true);
			sellBox.setSelected(true);
			sellSystemsBox.setSelected(true);
			costBox.setSelected(true);
			percentBox.setSelectedItem(homeplanet.core.Economy.commissionPercent() + "%");
			unlockBox.setSelected(true);
			lockedBox.setSelected(true);
			customLockedBox.setSelected(true);
			notifyBox.setSelected(true);
			repBox.setSelected(true);
			removalBox.setSelectedIndex(indexOf(homeplanet.core.Economy.REMOVAL_FEES, homeplanet.core.Economy.removalFee()));
			scrapBox.setSelected(homeplanet.core.Economy.stripAllowed());
			augmentBox.setSelected(homeplanet.core.Economy.augmentsHome());
			journeyFeeBox.setSelectedIndex(indexOf(homeplanet.core.Economy.JOURNEY_FEES, homeplanet.core.Economy.journeyFee()));
		}
		journeyFeeAfter.setEnabled(!im);
		for (int i = 0; i < locked.length; i++) {
			locked[i].setEnabled(!im);
			locked[i].setToolTipText(im ? SET_BY_IMMERSIVE : tips[i]);
		}
		// Career messages: Sandbox Mode's own choice, with Immersive Notifications on; Immersive Mode always has them
		careerBox.setEnabled(!im && notifyBox.isSelected());
		if (im) careerBox.setSelected(true);
		else if (!showingCareerOwn) { careerBox.setSelected(HomePlanet.careerMessages); }
		showingCareerOwn = !im;
		careerBox.setToolTipText(im ? SET_BY_IMMERSIVE : careerTip);
		boolean cost = costBox.isSelected();
		if (!im) percentBox.setEnabled(cost);
		// Immersive Mode: the ship a report earns goes by what it surrenders, not by this choice
		freeBox.setEnabled(cost && !im);
		freeLabel.setEnabled(cost && !im);
		if (im) freeBox.setSelectedIndex(Math.max(0, java.util.Arrays.asList(FREE_KEYS).indexOf(homeplanet.core.Economy.reassignment()))); // the career's
		String byValue = "Set by Immersive Mode: " + freeBox.getSelectedItem();
		freeBox.setToolTipText(im ? byValue : freeTip);
		freeLabel.setToolTipText(im ? byValue : freeTip);
		if (!im) unlockBox.setEnabled(cost);
		// Ranks: Immersive Mode is locked to Ranks From Rep; in Sandbox Mode, Ranks From Rep needs Reputation (5.56)
		if (im) { ranksBox.setSelectedIndex(homeplanet.parser.PlayerRank.FROM_REP); }
		else if (!showingRanksOwn) ranksBox.setSelectedIndex(homeplanet.parser.PlayerRank.setting);
		showingRanksOwn = !im;
		if (!im && ranksBox.getSelectedIndex() == homeplanet.parser.PlayerRank.FROM_REP && !repBox.isSelected()) ranksBox.setSelectedIndex(homeplanet.parser.PlayerRank.FROM_CRUISER);
		ranksBox.setEnabled(!im);
		ranksLabel.setEnabled(!im);
		ranksBox.setToolTipText(im ? SET_BY_IMMERSIVE : RANKS_TIP);
		repUseBox.setEnabled(repBox.isSelected()); // never locked: only the Reputation rule itself
		repUseLabel.setEnabled(repBox.isSelected());
		repUseInfo.setEnabled(repBox.isSelected());
		// the rate: Sandbox Mode's own; a career's as its difficulty (or Custom's choice) has it, shown and fixed
		String rateTip = "What your ships earn (sectors, ships defeated, scrap, good outcomes, achievements, expeditions) counts at this rate. Losses and spending count as they are";
		if (im) {
			rateBox.setSelectedIndex(homeplanet.vault.Reputation.rateLevel());
			String rule = homeplanet.vault.Reputation.rateRule();
			rateBox.setToolTipText(homeplanet.vault.Reputation.RATE_ASK.equals(rule) ? "This career hasn't chosen yet: the Space Dock will ask"
					: homeplanet.vault.Reputation.RATE_CHOSEN.equals(rule) ? "Chosen when this career began, and fixed" : "The career's difficulty sets it");
		} else {
			if (showingRateCareer) rateBox.setSelectedIndex(Math.max(0, Math.min(2, HomePlanet.reputationRate)));
			rateBox.setToolTipText(rateTip + ". Sandbox Mode's to change anytime");
		}
		showingRateCareer = im;
		rateBox.setEnabled(!im && repBox.isSelected());
		rateLabel.setEnabled(repBox.isSelected());
		if (!im) customLockedBox.setEnabled(lockedBox.isSelected());
	}

	/** The player's own rules in the boxes Immersive Mode sets. */
	private void showOwn() {
		// the fields are always the player's own: Immersive Mode never writes over them
		tradeBox.setSelected(HomePlanet.storeRequirement);
		journeyBox.setSelected(HomePlanet.journeyStoreRequirement);
		sellBox.setSelected(HomePlanet.sellSupplies);
		sellSystemsBox.setSelected(HomePlanet.sellSystems);
		costBox.setSelected(HomePlanet.commissionCosts);
		percentBox.setSelectedItem(HomePlanet.commissionPercent + "%");
		unlockBox.setSelected(HomePlanet.unlockFreeShips);
		lockedBox.setSelected(HomePlanet.commissionUnlockedOnly);
		customLockedBox.setSelected(HomePlanet.commissionCustomUnlockedOnly);
		notifyBox.setSelected(HomePlanet.immersiveNotifications);
		repBox.setSelected(HomePlanet.reputationOn);
		removalBox.setSelectedIndex(indexOf(homeplanet.core.Economy.REMOVAL_FEES, HomePlanet.removalFee));
		journeyFeeBox.setSelectedIndex(indexOf(homeplanet.core.Economy.JOURNEY_FEES, HomePlanet.journeyFee));
		scrapBox.setSelected(HomePlanet.stripAllowed);
		augmentBox.setSelected(HomePlanet.augmentsHome);
		freeBox.setSelectedIndex(Math.max(0, java.util.Arrays.asList(FREE_KEYS).indexOf(homeplanet.parser.FreeCommand.norm(HomePlanet.freeShip))));
	}
	private static int indexOf(int[] list, int v) { for (int i = 0; i < list.length; i++) if (list[i] == v) return i; return 0; }

	/** Adds the boxes one per row, starting at c's row and leaving c on the row after the last. */
	public void addTo(JPanel body, GridBagConstraints c) { addTo(body, c, true); }
	/** As {@link #addTo(JPanel, GridBagConstraints)}; without the Immersive Mode row for Sandbox Mode's first setup. */
	public void addTo(JPanel body, GridBagConstraints c, boolean withImmersive) {
		careerBox.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0)); // under Immersive Notifications, which it needs
		// the game mode, then the rules in groups, each under a small heading
		Object[] rows = {immersiveRow,
				"The Federation Home Planet", notifyBox, careerBox, repBox, repUseRow, rateRow, ranksRow,
				"Journeys and trading", tradeBox, journeyBox, journeyFeeRow, augmentBox,
				"Refit, scrapping and selling", removalRow, scrapBox, sellBox, sellSystemsBox,
				"Shipyard", lockedBox, customLockedBox, costRow, freeRow, unlockBox};
		for (Object r : rows) {
			if (r == immersiveRow && !withImmersive) continue;
			JComponent b = r instanceof String ? group((String) r, c.gridy > 0) : (JComponent) r;
			body.add(b, (GridBagConstraints) c.clone());
			c.gridy++;
		}
	}
	/** A group's heading, as the Settings window's own (gold), with room above it unless it's the first row. */
	private static JLabel group(String title, boolean roomAbove) {
		JLabel h = new JLabel(title);
		h.setFont(MenuTheme.HEADING_FONT);
		h.setForeground(MenuTheme.GOLD);
		h.setBorder(BorderFactory.createEmptyBorder(roomAbove ? 8 : 0, 0, 2, 0));
		return h;
	}

	/** What apply() would change, for the history log. */
	public void describeChanges(java.util.List<String> changed) {
		if (repUseBox.getSelectedIndex() + 1 != HomePlanet.reputationUse) changed.add("How Reputation Can be Used: " + repUseBox.getSelectedItem());
		if (!HomePlanet.immersiveMode && ranksBox.getSelectedIndex() != homeplanet.parser.PlayerRank.setting) changed.add("Ranks: " + ranksBox.getSelectedItem());
		if (!HomePlanet.immersiveMode) { // (in Immersive Mode the locked boxes show the career's rules; the player's own don't change)
			if (tradeBox.isSelected() != HomePlanet.storeRequirement) changed.add("Trading requires a station: " + tradeBox.isSelected());
			if (journeyBox.isSelected() != HomePlanet.journeyStoreRequirement) changed.add("New Journey requires a station: " + journeyBox.isSelected());
			if (scrapBox.isSelected() != HomePlanet.stripAllowed) changed.add("Stripping when scrapping: " + scrapBox.isSelected());
			if (augmentBox.isSelected() != HomePlanet.augmentsHome) changed.add("Augments with no room aboard shipped home: " + augmentBox.isSelected());
			if (removalFee() != HomePlanet.removalFee) changed.add("Refit removal: " + removalBox.getSelectedItem());
			if (journeyFee() != HomePlanet.journeyFee) changed.add("New Journey fee: " + journeyFeeBox.getSelectedItem());
			if (sellBox.isSelected() != HomePlanet.sellSupplies) changed.add("Selling missiles and drone parts: " + sellBox.isSelected());
			if (lockedBox.isSelected() != HomePlanet.commissionUnlockedOnly) changed.add("Locked models cannot be commissioned: " + lockedBox.isSelected());
			if (customLockedBox.isSelected() != HomePlanet.commissionCustomUnlockedOnly) changed.add("Custom ships of locked models cannot be commissioned: " + customLockedBox.isSelected());
			if (sellSystemsBox.isSelected() != HomePlanet.sellSystems) changed.add("Selling stored systems: " + sellSystemsBox.isSelected());
			if (costBox.isSelected() != HomePlanet.commissionCosts) changed.add("Commissioning costs scrap: " + costBox.isSelected());
			if (percent() != HomePlanet.commissionPercent) changed.add("Commission price: " + percent() + "%");
			if (!FREE_KEYS[freeBox.getSelectedIndex()].equals(HomePlanet.freeShip)) changed.add("Plead for New Ship grants: " + freeBox.getSelectedItem());
			if (notifyBox.isSelected() != HomePlanet.immersiveNotifications) changed.add("Immersive Notifications: " + notifyBox.isSelected());
			if (careerBox.isSelected() != HomePlanet.careerMessages) changed.add("Career messages: " + careerBox.isSelected());
			if (repBox.isSelected() != HomePlanet.reputationOn) changed.add("Reputation: " + repBox.isSelected());
			if (rateBox.getSelectedIndex() != HomePlanet.reputationRate) changed.add("Reputation earned: " + rateBox.getSelectedItem());
			if (unlockBox.isSelected() != HomePlanet.unlockFreeShips) changed.add("A free ship for each new FTL unlock: " + unlockBox.isSelected());
		}
	}

	/** Sets the rules from the boxes (the caller saves the config, and switches fleets first when Immersive Mode changes). */
	public void apply() {
		boolean unlockWasOn = HomePlanet.unlockFreeShips;
		if (!HomePlanet.immersiveMode) HomePlanet.freeShip = FREE_KEYS[freeBox.getSelectedIndex()]; // (Immersive Mode shows its own, Variable)
		if (!HomePlanet.immersiveMode) HomePlanet.careerMessages = careerBox.isSelected();
		if (!HomePlanet.immersiveMode) HomePlanet.reputationOn = repBox.isSelected();
		if (!HomePlanet.immersiveMode) HomePlanet.reputationRate = rateBox.getSelectedIndex(); // Sandbox Mode's own (6.13)
		HomePlanet.reputationUse = repUseBox.getSelectedIndex() + 1; // any mode
		if (!HomePlanet.immersiveMode) homeplanet.parser.PlayerRank.setting = ranksBox.getSelectedIndex(); // (Immersive Mode is always Ranks From Rep)
		if (!HomePlanet.immersiveMode) { // (Immersive Mode's own rules are set by it; the button switched it already)
			HomePlanet.storeRequirement = tradeBox.isSelected();
			HomePlanet.journeyStoreRequirement = journeyBox.isSelected();
			HomePlanet.sellSupplies = sellBox.isSelected();
			HomePlanet.sellSystems = sellSystemsBox.isSelected();
			HomePlanet.commissionCosts = costBox.isSelected();
			HomePlanet.commissionPercent = percent();
			HomePlanet.unlockFreeShips = unlockBox.isSelected();
			HomePlanet.commissionUnlockedOnly = lockedBox.isSelected();
			HomePlanet.commissionCustomUnlockedOnly = customLockedBox.isSelected();
			HomePlanet.immersiveNotifications = notifyBox.isSelected();
			HomePlanet.stripAllowed = scrapBox.isSelected();
			HomePlanet.augmentsHome = augmentBox.isSelected();
			HomePlanet.removalFee = removalFee();
			HomePlanet.journeyFee = journeyFee();
		}
		// unlocks from before the rule was turned on never count (in Immersive Mode its fleet keeps its own record:
		// see UnlockGrants.returning; marking everything seen there would lose the free ships still waiting)
		if (HomePlanet.unlockFreeShips && !unlockWasOn && !HomePlanet.immersiveMode && homeplanet.vault.Vault.isOpen())
			homeplanet.parser.UnlockGrants.turnedOn(homeplanet.parser.Unlocks.read());
	}
	private int removalFee() { return homeplanet.core.Economy.REMOVAL_FEES[removalBox.getSelectedIndex()]; }
	private int journeyFee() { return homeplanet.core.Economy.JOURNEY_FEES[journeyFeeBox.getSelectedIndex()]; }
	private int percent() { String s = (String) percentBox.getSelectedItem(); return Integer.parseInt(s.substring(0, s.length() - 1)); }
}
