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

	final JCheckBox immersiveBox = new JCheckBox("Immersive Mode: the station runs by The Federation Home Planet's rules (sets and locks the rules it decides)", HomePlanet.immersiveMode);
	final JCheckBox tradeBox = new JCheckBox("Trading and scrapping need a station (the ship must be at a beacon with a store)", HomePlanet.storeRequirement);
	final JCheckBox journeyBox = new JCheckBox("New Journey needs a station (the boarded ship must be at a beacon with a store)", HomePlanet.journeyStoreRequirement);
	final JCheckBox scrapBox = new JCheckBox("Scrapping a ship also moves her systems to the Cargo Bay", HomePlanet.scrapKeepsSystems);
	final JCheckBox sellBox = new JCheckBox("Allow selling missiles and drone parts (house rule: FTL's stores don't buy them; half the store price)", HomePlanet.sellSupplies);
	final JCheckBox sellSystemsBox = new JCheckBox("Allow selling stored systems (house rule: half the system's price, plus half the upgrades paid for)", HomePlanet.sellSystems);
	final JCheckBox lockedBox = new JCheckBox("Locked ship models cannot be commissioned (as unlocked in your FTL profile)", HomePlanet.commissionUnlockedOnly);
	final JCheckBox customLockedBox = new JCheckBox("Custom ships based on locked models cannot be commissioned", HomePlanet.commissionCustomUnlockedOnly);
	final JCheckBox costBox = new JCheckBox("Commissioning a ship costs scrap, paid from Spacedock Storage, at", HomePlanet.commissionCosts);
	final JComboBox<String> percentBox = new JComboBox<String>(new String[] {"100%", "75%", "50%"});
	private final JPanel costRow = row(0);
	private static final String[] FREE_KEYS = {"kestrel", "any", "relief"};
	final JComboBox<String> freeBox = new JComboBox<String>(new String[] {"a Kestrel A", "any ship", "a Federation relief ship"});
	private final JLabel freeLabel = new JLabel("When the shipyard is empty, The Federation Home Planet grants one free ship:  ");
	private final JPanel freeRow = row(22);
	final JCheckBox unlockBox = new JCheckBox("Each ship unlocked in FTL from now on can be commissioned free, once", HomePlanet.unlockFreeShips);

	/** The rules Immersive Mode sets, with their own tooltips (shown again when it's off). */
	private final JComponent[] locked = {tradeBox, journeyBox, sellBox, sellSystemsBox, costBox, percentBox, unlockBox, lockedBox, customLockedBox};
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
		immersiveBox.setToolTipText("<html>A fleet of its own: your current fleet is kept as it is and comes back when Immersive Mode is turned off.<br>"
				+ "Sets and locks: trading, scrapping and New Journey need a station; commissioning costs scrap at 100%; locked ships can't be commissioned;<br>"
				+ "each ship unlocked in FTL is free once; a New Journey costs " + HomePlanet.JOURNEY_FEE + " scrap from Spacedock Storage; missiles, drone parts and stored systems<br>"
				+ "sell at 25% of the store price; earlier versions of a ship can't be restored, and lost ships can't be recovered.<br>"
				+ "Your rank decides what you may commission: Captains, custom ships; Commodores, custom ships with artillery.</html>");
		scrapBox.setToolTipText("Optional systems only: standard equipment and damaged systems are lost with the hull");
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
		String freeTip = "No ship docked, boarded or in the Junkyard: this ship can be commissioned free. (Other... > Report for Reassignment empties the Junkyard.) "
				+ "The relief ship is a Kestrel A stripped to basics: one crew, a basic laser and an ion blast, every system at its minimum";
		freeBox.setToolTipText(freeTip);
		freeLabel.setToolTipText(freeTip);
		freeRow.add(freeLabel);
		freeRow.add(freeBox);
		unlockBox.setToolTipText("Only ships unlocked after this is turned on count, each layout (A, B, C) once. A Report for Reassignment doesn't reset it");
		unlockBox.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
		for (int i = 0; i < locked.length; i++) tips[i] = locked[i].getToolTipText();
		ActionListener sync = new ActionListener() { public void actionPerformed(ActionEvent e) { sync(); } };
		immersiveBox.addActionListener(sync);
		costBox.addActionListener(sync);
		lockedBox.addActionListener(sync);
		sync();
	}

	/** Greys out what depends on an unticked rule, and sets what Immersive Mode decides. */
	private void sync() {
		boolean im = immersiveBox.isSelected();
		if (!im && showingImmersive) showOwn();
		showingImmersive = im;
		if (im) {
			tradeBox.setSelected(true);
			journeyBox.setSelected(true);
			sellBox.setSelected(true);
			sellSystemsBox.setSelected(true);
			costBox.setSelected(true);
			percentBox.setSelectedItem("100%");
			unlockBox.setSelected(true);
			lockedBox.setSelected(true);
			customLockedBox.setSelected(true);
		}
		for (int i = 0; i < locked.length; i++) {
			locked[i].setEnabled(!im);
			locked[i].setToolTipText(im ? SET_BY_IMMERSIVE : tips[i]);
		}
		boolean cost = costBox.isSelected();
		if (!im) percentBox.setEnabled(cost);
		freeBox.setEnabled(cost);
		freeLabel.setEnabled(cost);
		if (!im) unlockBox.setEnabled(cost);
		if (!im) customLockedBox.setEnabled(lockedBox.isSelected());
	}

	/** The player's own rules in the boxes Immersive Mode sets. */
	private void showOwn() {
		HomePlanet.Rules r = HomePlanet.normalRules();
		tradeBox.setSelected(r.store);
		journeyBox.setSelected(r.journey);
		sellBox.setSelected(r.sellSupplies);
		sellSystemsBox.setSelected(r.sellSystems);
		costBox.setSelected(r.costs);
		percentBox.setSelectedItem(r.percent + "%");
		unlockBox.setSelected(r.unlockFree);
		lockedBox.setSelected(r.lockedOnly);
		customLockedBox.setSelected(r.customLockedOnly);
	}
	/** Is Immersive Mode ticked? */
	public boolean immersiveWanted() { return immersiveBox.isSelected(); }
	/** Puts the Immersive Mode tick back (a switch of fleets that didn't happen). */
	public void keepImmersive(boolean on) { immersiveBox.setSelected(on); sync(); }

	/** Adds the boxes one per row, starting at c's row and leaving c on the row after the last. */
	public void addTo(JPanel body, GridBagConstraints c) {
		for (JComponent b : new JComponent[] {immersiveBox, tradeBox, journeyBox, scrapBox, sellBox, sellSystemsBox, lockedBox, customLockedBox, costRow, freeRow, unlockBox}) {
			body.add(b, (GridBagConstraints) c.clone());
			c.gridy++;
		}
	}

	/** What apply() would change, for the history log. */
	public void describeChanges(java.util.List<String> changed) {
		if (immersiveBox.isSelected() != HomePlanet.immersiveMode) changed.add("Immersive Mode: " + immersiveBox.isSelected());
		if (tradeBox.isSelected() != HomePlanet.storeRequirement) changed.add("Trading requires a station: " + tradeBox.isSelected());
		if (journeyBox.isSelected() != HomePlanet.journeyStoreRequirement) changed.add("New Journey requires a station: " + journeyBox.isSelected());
		if (scrapBox.isSelected() != HomePlanet.scrapKeepsSystems) changed.add("Scrapping keeps systems: " + scrapBox.isSelected());
		if (sellBox.isSelected() != HomePlanet.sellSupplies) changed.add("Selling missiles and drone parts: " + sellBox.isSelected());
		if (lockedBox.isSelected() != HomePlanet.commissionUnlockedOnly) changed.add("Locked models cannot be commissioned: " + lockedBox.isSelected());
		if (customLockedBox.isSelected() != HomePlanet.commissionCustomUnlockedOnly) changed.add("Custom ships of locked models cannot be commissioned: " + customLockedBox.isSelected());
		if (sellSystemsBox.isSelected() != HomePlanet.sellSystems) changed.add("Selling stored systems: " + sellSystemsBox.isSelected());
		if (costBox.isSelected() != HomePlanet.commissionCosts) changed.add("Commissioning costs scrap: " + costBox.isSelected());
		if (percent() != HomePlanet.commissionPercent) changed.add("Commission price: " + percent() + "%");
		if (!FREE_KEYS[freeBox.getSelectedIndex()].equals(HomePlanet.freeShip)) changed.add("Free ship for an empty shipyard: " + freeBox.getSelectedItem());
		if (unlockBox.isSelected() != HomePlanet.unlockFreeShips) changed.add("A free ship for each new FTL unlock: " + unlockBox.isSelected());
		// (with Immersive Mode on, the locked rules above show its values; the player's own are kept apart)
	}

	/** Sets the rules from the boxes (the caller saves the config, and switches fleets first when Immersive Mode changes). */
	public void apply() {
		boolean unlockWasOn = HomePlanet.unlockFreeShips;
		// the rules Immersive Mode leaves to the player
		HomePlanet.scrapKeepsSystems = scrapBox.isSelected();
		HomePlanet.freeShip = FREE_KEYS[freeBox.getSelectedIndex()];
		if (!immersiveBox.isSelected()) {
			if (HomePlanet.immersiveMode) HomePlanet.leaveImmersive();
			HomePlanet.storeRequirement = tradeBox.isSelected();
			HomePlanet.journeyStoreRequirement = journeyBox.isSelected();
			HomePlanet.sellSupplies = sellBox.isSelected();
			HomePlanet.sellSystems = sellSystemsBox.isSelected();
			HomePlanet.commissionCosts = costBox.isSelected();
			HomePlanet.commissionPercent = percent();
			HomePlanet.unlockFreeShips = unlockBox.isSelected();
			HomePlanet.commissionUnlockedOnly = lockedBox.isSelected();
			HomePlanet.commissionCustomUnlockedOnly = customLockedBox.isSelected();
		} else if (!HomePlanet.immersiveMode) {
			HomePlanet.immersiveMode = true;
			HomePlanet.applyImmersive(); // the player's own rules are kept as they are
		}
		// unlocks from before the rule was (re)turned on never count
		if (HomePlanet.unlockFreeShips && !unlockWasOn && homeplanet.vault.Vault.isOpen())
			homeplanet.parser.UnlockGrants.turnedOn(homeplanet.parser.Unlocks.read());
	}
	private int percent() { String s = (String) percentBox.getSelectedItem(); return Integer.parseInt(s.substring(0, s.length() - 1)); }
}
