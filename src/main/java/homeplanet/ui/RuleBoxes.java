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
	final JCheckBox scrapBox = new JCheckBox("Allow stripping when scrapping: her systems can go to the Cargo Bay, at a discount on the removal fee", HomePlanet.stripAllowed);
	final JComboBox<String> removalBox = new JComboBox<String>(new String[] {"not allowed", "free", "25 scrap", "50 scrap"});
	private final JLabel removalLabel = new JLabel("Refit: taking a system off a ship is  ");
	private final JPanel removalRow = row(21);
	final JComboBox<String> journeyFeeBox = new JComboBox<String>(new String[] {"free", "200 scrap", "500 scrap", "1000 scrap"});
	private final JLabel journeyFeeLabel = new JLabel("A New Journey costs  ");
	private final JLabel journeyFeeAfter = new JLabel("  from the Cargo Hold");
	private final JPanel journeyFeeRow = row(21);
	final JCheckBox sellBox = new JCheckBox("Allow selling missiles and drone parts (house rule: FTL's stores don't buy them; half the store price)", HomePlanet.sellSupplies);
	final JCheckBox sellSystemsBox = new JCheckBox("Allow selling stored systems (house rule: half the system's price, plus half the upgrades paid for)", HomePlanet.sellSystems);
	final JCheckBox lockedBox = new JCheckBox("Locked ship models cannot be commissioned (as unlocked in your FTL profile)", HomePlanet.commissionUnlockedOnly);
	final JCheckBox customLockedBox = new JCheckBox("Custom ships based on locked models cannot be commissioned", HomePlanet.commissionCustomUnlockedOnly);
	final JCheckBox costBox = new JCheckBox("Commissioning a ship costs scrap, paid from the Cargo Hold, at", HomePlanet.commissionCosts);
	final JComboBox<String> percentBox = new JComboBox<String>(new String[] {"100%", "75%", "50%"});
	private final JPanel costRow = row(0);
	private static final String[] FREE_KEYS = {"kestrel", "relief", "any", "variable"};
	final JComboBox<String> freeBox = new JComboBox<String>(new String[] {"Kestrel Type A", "Relief Ship", "Any", "Variable (Depending on how much you gave up)"});
	/** Each choice's explanation, shown as the list is open. */
	private static final String[] FREE_TIPS = {
		"A standard Kestrel Type A, as a new FTL game starts",
		"The Federation relief ship: a Kestrel Type A stripped to basics (one crew, a Basic Laser and an Ion Blast, every system at its minimum, a reactor of 7)",
		"Any ship you choose at Commission",
		"By what the report surrenders (the Cargo Hold and the Junkyard, at full value): " + homeplanet.parser.FreeCommand.ANY_FROM
				+ " scrap or more, any ship; " + homeplanet.parser.FreeCommand.KESTREL_FROM + " or more, a Kestrel Type A; less, the relief ship"};
	private String freeTip = "";
	private String careerTip = null;
	private boolean showingCareerOwn = !HomePlanet.immersiveMode;
	private final JLabel freeLabel = new JLabel("Report for Reassignment grants:  ");
	private final JPanel freeRow = row(22);
	final JCheckBox notifyBox = new JCheckBox("Immersive Notifications: transmissions from The Federation Home Planet (commission orders, news), in an inbox on the Space Dock", HomePlanet.immersiveNotifications);
	final JCheckBox careerBox = new JCheckBox("Career messages: a welcome, promotions, rewards for FTL achievements and a monthly stipend, in Sandbox Mode too", HomePlanet.careerMessages);
	final JCheckBox unlockBox = new JCheckBox("Each ship unlocked in FTL from now on can be commissioned free, once", HomePlanet.unlockFreeShips);

	/** The rules Immersive Mode sets, with their own tooltips (shown again when it's off). */
	private final JComponent[] locked = {tradeBox, journeyBox, sellBox, sellSystemsBox, costBox, percentBox, unlockBox, lockedBox, customLockedBox, notifyBox,
			removalBox, removalLabel, journeyFeeBox, journeyFeeLabel, scrapBox};
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
		scrapBox.setToolTipText("Optional systems only, each for 10 scrap (20 when Refit charges 50; free when Refit is free). Standard equipment and damaged systems are lost with the hull. "
				+ "Off: her systems are lost with the hull");
		removalBox.setSelectedIndex(indexOf(homeplanet.core.Economy.REMOVAL_FEES, HomePlanet.removalFee));
		removalBox.setToolTipText("The Refit tab's Store button: what the boarded ship pays to take one of her systems off, or whether she can at all");
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
		sellSystemsBox.setToolTipText("Shows a Sell button beside each system stored in the Cargo Bay (Refit tab). The boarded ship is paid");
		lockedBox.setToolTipText("Commission only offers the layouts (A, B, C) you have unlocked in FTL");
		customLockedBox.setToolTipText("A starter blueprint is offered only once the layout she was remodeled from is unlocked");
		customLockedBox.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
		costBox.setToolTipText("Her systems and levels, reactor, weapons, drones, augments and crew at FTL's prices; custom designs also pay for rooms and doors. The Commission window shows the price");
		percentBox.setToolTipText("The share of the full price the shipyard charges");
		percentBox.setSelectedItem(HomePlanet.commissionPercent + "%");
		costRow.add(costBox);
		costRow.add(javax.swing.Box.createHorizontalStrut(6));
		costRow.add(percentBox);
		costRow.add(new JLabel("  of her price"));
		int free = java.util.Arrays.asList(FREE_KEYS).indexOf(HomePlanet.freeShip);
		freeBox.setSelectedIndex(free < 0 ? 0 : free);
		freeTip = "The free ship a Report for Reassignment (Other... at the Space Dock) earns, in exchange for the Cargo Hold and the Junkyard. "
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
		notifyBox.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0)); // under Immersive Mode, which turns it on
		careerBox.setToolTipText("Your rank rises as you unlock FTL's Federation Cruisers; achievements earned from now on are rewarded, and the stipend comes every "
				+ homeplanet.parser.Career.SECTORS_PER_MONTH + " sectors. Your fleet and rules stay your own. (Always on in Immersive Mode, at its difficulty.)");
		careerBox.setBorder(BorderFactory.createEmptyBorder(0, 44, 0, 0)); // under Immersive Notifications, which it needs
		careerTip = careerBox.getToolTipText();
		unlockBox.setToolTipText("Only ships unlocked after this is turned on count, each layout (A, B, C) once. A Report for Reassignment doesn't reset it");
		unlockBox.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
		for (int i = 0; i < locked.length; i++) tips[i] = locked[i].getToolTipText();
		ActionListener sync = new ActionListener() { public void actionPerformed(ActionEvent e) { sync(); } };
		immersiveBox.addActionListener(sync);
		immersiveRow.add(immersiveButton);
		immersiveRow.add(immersiveLabel);
		immersiveButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!SwitchModeDialog.show(immersiveButton)) return;
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
			removalBox.setSelectedIndex(indexOf(homeplanet.core.Economy.REMOVAL_FEES, homeplanet.core.Economy.removalFee()));
			scrapBox.setSelected(homeplanet.core.Economy.stripAllowed());
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
		String byValue = "Set by Immersive Mode: " + freeBox.getSelectedItem() + (freeBox.getSelectedIndex() == 3 ? ". " + FREE_TIPS[3] : "");
		freeBox.setToolTipText(im ? byValue : freeTip);
		freeLabel.setToolTipText(im ? byValue : freeTip);
		if (!im) unlockBox.setEnabled(cost);
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
		removalBox.setSelectedIndex(indexOf(homeplanet.core.Economy.REMOVAL_FEES, HomePlanet.removalFee));
		journeyFeeBox.setSelectedIndex(indexOf(homeplanet.core.Economy.JOURNEY_FEES, HomePlanet.journeyFee));
		scrapBox.setSelected(HomePlanet.stripAllowed);
		freeBox.setSelectedIndex(Math.max(0, java.util.Arrays.asList(FREE_KEYS).indexOf(HomePlanet.freeShip)));
	}
	private static int indexOf(int[] list, int v) { for (int i = 0; i < list.length; i++) if (list[i] == v) return i; return 0; }

	/** Adds the boxes one per row, starting at c's row and leaving c on the row after the last. */
	public void addTo(JPanel body, GridBagConstraints c) { addTo(body, c, true); }
	/** As {@link #addTo(JPanel, GridBagConstraints)}; without the Immersive Mode row for Sandbox Mode's first setup. */
	public void addTo(JPanel body, GridBagConstraints c, boolean withImmersive) {
		if (!withImmersive) { notifyBox.setBorder(BorderFactory.createEmptyBorder()); careerBox.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0)); } // nothing above it to sit under
		for (JComponent b : new JComponent[] {immersiveRow, notifyBox, careerBox, tradeBox, journeyBox, journeyFeeRow, removalRow, scrapBox, sellBox, sellSystemsBox, lockedBox, customLockedBox, costRow, freeRow, unlockBox}) {
			if (b == immersiveRow && !withImmersive) continue;
			body.add(b, (GridBagConstraints) c.clone());
			c.gridy++;
		}
	}

	/** What apply() would change, for the history log. */
	public void describeChanges(java.util.List<String> changed) {
		if (!HomePlanet.immersiveMode) { // (in Immersive Mode the locked boxes show the career's rules; the player's own don't change)
			if (tradeBox.isSelected() != HomePlanet.storeRequirement) changed.add("Trading requires a station: " + tradeBox.isSelected());
			if (journeyBox.isSelected() != HomePlanet.journeyStoreRequirement) changed.add("New Journey requires a station: " + journeyBox.isSelected());
			if (scrapBox.isSelected() != HomePlanet.stripAllowed) changed.add("Stripping when scrapping: " + scrapBox.isSelected());
			if (removalFee() != HomePlanet.removalFee) changed.add("Refit removal: " + removalBox.getSelectedItem());
			if (journeyFee() != HomePlanet.journeyFee) changed.add("New Journey fee: " + journeyFeeBox.getSelectedItem());
			if (sellBox.isSelected() != HomePlanet.sellSupplies) changed.add("Selling missiles and drone parts: " + sellBox.isSelected());
			if (lockedBox.isSelected() != HomePlanet.commissionUnlockedOnly) changed.add("Locked models cannot be commissioned: " + lockedBox.isSelected());
			if (customLockedBox.isSelected() != HomePlanet.commissionCustomUnlockedOnly) changed.add("Custom ships of locked models cannot be commissioned: " + customLockedBox.isSelected());
			if (sellSystemsBox.isSelected() != HomePlanet.sellSystems) changed.add("Selling stored systems: " + sellSystemsBox.isSelected());
			if (costBox.isSelected() != HomePlanet.commissionCosts) changed.add("Commissioning costs scrap: " + costBox.isSelected());
			if (percent() != HomePlanet.commissionPercent) changed.add("Commission price: " + percent() + "%");
			if (!FREE_KEYS[freeBox.getSelectedIndex()].equals(HomePlanet.freeShip)) changed.add("Report for Reassignment grants: " + freeBox.getSelectedItem());
			if (notifyBox.isSelected() != HomePlanet.immersiveNotifications) changed.add("Immersive Notifications: " + notifyBox.isSelected());
			if (careerBox.isSelected() != HomePlanet.careerMessages) changed.add("Career messages: " + careerBox.isSelected());
			if (unlockBox.isSelected() != HomePlanet.unlockFreeShips) changed.add("A free ship for each new FTL unlock: " + unlockBox.isSelected());
		}
	}

	/** Sets the rules from the boxes (the caller saves the config, and switches fleets first when Immersive Mode changes). */
	public void apply() {
		boolean unlockWasOn = HomePlanet.unlockFreeShips;
		if (!HomePlanet.immersiveMode) HomePlanet.freeShip = FREE_KEYS[freeBox.getSelectedIndex()]; // (Immersive Mode shows its own, Variable)
		if (!HomePlanet.immersiveMode) HomePlanet.careerMessages = careerBox.isSelected();
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
