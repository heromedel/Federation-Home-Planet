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

	public static JCheckBox steamBox(boolean on) { return new JCheckBox("Launch FTL through Steam", on); }

	public RuleBoxes() {
		scrapBox.setToolTipText("Optional systems only: standard equipment and damaged systems are lost with the hull");
		sellBox.setToolTipText("Shows a sell button under the supplies in the Cargo Bay: 3 scrap a missile, 4 a drone part. Junking them is always possible");
		lockedBox.setToolTipText("Commission only offers the layouts (A, B, C) you have unlocked in FTL");
		customLockedBox.setToolTipText("A starter blueprint is offered only once the layout she was remodeled from is unlocked");
		customLockedBox.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0)); // nested under the rule above
		customLockedBox.setEnabled(lockedBox.isSelected());
		lockedBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { customLockedBox.setEnabled(lockedBox.isSelected()); }
		});
	}

	/** Adds the boxes one per row, starting at c's row and leaving c on the row after the last. */
	public void addTo(JPanel body, GridBagConstraints c) {
		for (JCheckBox b : new JCheckBox[] {tradeBox, journeyBox, scrapBox, sellBox, lockedBox, customLockedBox}) {
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
	}

	/** Sets the rules from the boxes (the caller saves the config). */
	public void apply() {
		HomePlanet.storeRequirement = tradeBox.isSelected();
		HomePlanet.journeyStoreRequirement = journeyBox.isSelected();
		HomePlanet.scrapKeepsSystems = scrapBox.isSelected();
		HomePlanet.sellSupplies = sellBox.isSelected();
		HomePlanet.commissionUnlockedOnly = lockedBox.isSelected();
		HomePlanet.commissionCustomUnlockedOnly = customLockedBox.isSelected();
	}
}
