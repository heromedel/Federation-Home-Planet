package homeplanet.ui;

import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import homeplanet.core.HomePlanet;

/**
 * The station's rules as checkboxes, shared by Settings and the first-run House Rules window so both read the same.
 * Nothing changes until apply().
 */
public class RuleBoxes {

	final JCheckBox tradeBox = new JCheckBox("Trading and scrapping need a station (the ship must be at a beacon with a store)", HomePlanet.storeRequirement);
	final JCheckBox journeyBox = new JCheckBox("New Journey needs a station (the boarded ship must be at a beacon with a store)", HomePlanet.journeyStoreRequirement);
	final JCheckBox scrapBox = new JCheckBox("Scrapping a ship also moves her systems to the Cargo Bay", HomePlanet.scrapKeepsSystems);
	final JCheckBox sellBox = new JCheckBox("Allow selling missiles and drone parts (house rule: FTL's stores don't buy them; half the store price)", HomePlanet.sellSupplies);
	final JCheckBox lockedBox = new JCheckBox("Locked ship models cannot be commissioned (as unlocked in your FTL profile)", HomePlanet.commissionUnlockedOnly);
	final JCheckBox customLockedBox = new JCheckBox("Custom ships based on locked models cannot be commissioned", HomePlanet.commissionCustomUnlockedOnly);
	final JCheckBox sellSystemsBox = new JCheckBox("Allow selling stored systems (house rule: half the system's price, plus half the upgrades paid for)", HomePlanet.sellSystems);
	final JCheckBox costBox = new JCheckBox("Commissioning a ship costs scrap, paid from Spacedock Storage, at", HomePlanet.commissionCosts);
	final javax.swing.JComboBox<String> percentBox = new javax.swing.JComboBox<String>(new String[] {"100%", "75%", "50%"});
	private final JPanel costRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));

	public RuleBoxes() {
		scrapBox.setToolTipText("Optional systems only: standard equipment and damaged systems are lost with the hull");
		sellBox.setToolTipText("Shows a sell button under the supplies in the Cargo Bay: 3 scrap a missile, 4 a drone part. Junking them is always possible");
		lockedBox.setToolTipText("Commission only offers the layouts (A, B, C) you have unlocked in FTL");
		customLockedBox.setToolTipText("A starter blueprint is offered only once the layout she was remodeled from is unlocked");
		customLockedBox.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0)); // nested under the rule above
		customLockedBox.setEnabled(lockedBox.isSelected());
		sellSystemsBox.setToolTipText("Shows a Sell button beside each system stored in the Cargo Bay (Refit tab). The boarded ship is paid");
		costBox.setToolTipText("Her systems and levels, reactor, weapons, drones, augments and crew at FTL's prices; custom designs also pay for rooms and doors. The Commission window shows the price");
		percentBox.setToolTipText("The share of the full price the shipyard charges");
		percentBox.setSelectedItem(HomePlanet.commissionPercent + "%");
		percentBox.setEnabled(costBox.isSelected());
		costBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { percentBox.setEnabled(costBox.isSelected()); }
		});
		costRow.setOpaque(false);
		costRow.add(costBox);
		costRow.add(javax.swing.Box.createHorizontalStrut(6));
		costRow.add(percentBox);
		costRow.add(new javax.swing.JLabel("  of her price"));
		lockedBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { customLockedBox.setEnabled(lockedBox.isSelected()); }
		});
	}

	/** Adds the boxes one per row, starting at c's row and leaving c on the row after the last. */
	public void addTo(JPanel body, GridBagConstraints c) {
		for (javax.swing.JComponent b : new javax.swing.JComponent[] {tradeBox, journeyBox, scrapBox, sellBox, sellSystemsBox, lockedBox, customLockedBox, costRow}) {
			body.add(b, (GridBagConstraints) c.clone());
			c.gridy++;
		}
	}

	/** What apply() would change, for the history log. */
	public void describeChanges(java.util.List<String> changed) {
		if (tradeBox.isSelected() != HomePlanet.storeRequirement) changed.add("Trading requires a station: " + tradeBox.isSelected());
		if (journeyBox.isSelected() != HomePlanet.journeyStoreRequirement) changed.add("New Journey requires a station: " + journeyBox.isSelected());
		if (scrapBox.isSelected() != HomePlanet.scrapKeepsSystems) changed.add("Scrapping keeps systems: " + scrapBox.isSelected());
		if (sellBox.isSelected() != HomePlanet.sellSupplies) changed.add("Selling missiles and drone parts: " + sellBox.isSelected());
		if (lockedBox.isSelected() != HomePlanet.commissionUnlockedOnly) changed.add("Locked models cannot be commissioned: " + lockedBox.isSelected());
		if (customLockedBox.isSelected() != HomePlanet.commissionCustomUnlockedOnly) changed.add("Custom ships of locked models cannot be commissioned: " + customLockedBox.isSelected());
		if (sellSystemsBox.isSelected() != HomePlanet.sellSystems) changed.add("Selling stored systems: " + sellSystemsBox.isSelected());
		if (costBox.isSelected() != HomePlanet.commissionCosts) changed.add("Commissioning costs scrap: " + costBox.isSelected());
		if (percent() != HomePlanet.commissionPercent) changed.add("Commission price: " + percent() + "%");
	}

	/** Sets the rules from the boxes (the caller saves the config). */
	public void apply() {
		HomePlanet.storeRequirement = tradeBox.isSelected();
		HomePlanet.journeyStoreRequirement = journeyBox.isSelected();
		HomePlanet.scrapKeepsSystems = scrapBox.isSelected();
		HomePlanet.sellSupplies = sellBox.isSelected();
		HomePlanet.commissionUnlockedOnly = lockedBox.isSelected();
		HomePlanet.commissionCustomUnlockedOnly = customLockedBox.isSelected();
		HomePlanet.sellSystems = sellSystemsBox.isSelected();
		HomePlanet.commissionCosts = costBox.isSelected();
		HomePlanet.commissionPercent = percent();
	}
	private int percent() { String s = (String) percentBox.getSelectedItem(); return Integer.parseInt(s.substring(0, s.length() - 1)); }
}
