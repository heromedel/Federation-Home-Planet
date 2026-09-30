package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import homeplanet.core.HomePlanet;

/**
 * Settings: folders, launching, trading/journey rules and debug logging.
 * Nothing changes until OK; OK writes the cfg file.
 */
public class SettingsDialog extends JDialog {

	private File saves = HomePlanet.save_location;
	private File game = HomePlanet.datsPath;
	private final JLabel savesLabel = new JLabel();
	private final JLabel gameLabel = new JLabel();
	private final JCheckBox steamBox = new JCheckBox("Launch FTL through Steam", HomePlanet.launchThroughSteam);
	private final RuleBoxes rules = new RuleBoxes();
	private final JCheckBox musicBox = new JCheckBox("Play title music while the game is not open", homeplanet.core.Music.enabled);
	private final JCheckBox debugBox = new JCheckBox("Debug logging (shown in the console window)", HomePlanet.debugLogging);
	private boolean savesChanged = false;
	/** After a final victory: nothing, rescue her, or a reward of her value (the fleet in use has its own choice). */
	private final javax.swing.JRadioButton[] victoryButtons = new javax.swing.JRadioButton[homeplanet.parser.FinalVictory.CHOICES.length];
	private final String victoryWas = homeplanet.parser.FinalVictory.choice();

	/** Shows the dialog. Returns true if the saves folder changed (so the Space Dock should reload). */
	public static boolean open(java.awt.Component owner) {
		Window w = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
		SettingsDialog d = new SettingsDialog(w);
		d.setVisible(true);
		return d.savesChanged;
	}

	private SettingsDialog(Window owner) {
		super(owner, "Settings", ModalityType.APPLICATION_MODAL);
		JPanel body = new JPanel(new GridBagLayout());
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(2, 0, 2, 0);
		c.weightx = 1;

		heading(body, c, "Folders");
		body.add(folderRow("Saves folder:", savesLabel, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				File f = HomePlanet.promptForSavePath();
				if (f != null) { saves = f; refreshLabels(); }
			}
		}), next(c));
		body.add(folderRow("Game folder:", gameLabel, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				File f = HomePlanet.promptForFtlPath();
				if (f != null) { game = f; refreshLabels(); }
			}
		}), next(c));
		JPanel openRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		JButton openSaves = new JButton("Open saves folder");
		openSaves.setToolTipText("Open the saves folder in Windows Explorer");
		openSaves.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { openFolder(saves); }
		});
		JButton openStation = new JButton("Open the station's folder");
		openStation.setToolTipText("Open The Home Planet Station's own folder (its ships, Junkyard, records and blueprints) in Windows Explorer: for backups, or a look around");
		openStation.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { openFolder(homeplanet.vault.Vault.get().root); }
		});
		JButton openJunk = new JButton("Open Junkyard");
		openJunk.setToolTipText("Open the Junkyard folder (disbanded ships) in Windows Explorer");
		openJunk.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				File junk = homeplanet.vault.Vault.get().junkyardDir();
				if (!junk.isDirectory() || homeplanet.vault.Vault.get().junked().isEmpty()) {
					JOptionPane.showMessageDialog(SettingsDialog.this, "The Junkyard is empty. No ship has been disbanded yet.",
							"Open Junkyard", JOptionPane.INFORMATION_MESSAGE);
					return;
				}
				openFolder(junk);
			}
		});
		JButton openLog = new JButton("Open history log");
		openLog.setToolTipText("Open history.log: what The Home Planet Station loaded and did");
		openLog.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!homeplanet.core.HistoryLog.file().exists()) {
					JOptionPane.showMessageDialog(SettingsDialog.this, "Nothing has been logged yet.", "History log", JOptionPane.INFORMATION_MESSAGE);
					return;
				}
				try {
					Desktop.getDesktop().open(homeplanet.core.HistoryLog.file().getAbsoluteFile());
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(SettingsDialog.this, "The Home Planet Station could not open its history log:\n" + homeplanet.core.HistoryLog.file().getAbsolutePath(),
							"History log", JOptionPane.WARNING_MESSAGE);
				}
			}
		});
		JButton openLogs = new JButton("Open log folder");
		openLogs.setToolTipText("The program's own logs (one file per run): what to send along with a bug report");
		openLogs.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				File d = HomePlanet.logDir();
				if (d == null || !d.isDirectory()) { JOptionPane.showMessageDialog(SettingsDialog.this, "No log folder could be made beside Federation Home Planet.jar.", "Logs", JOptionPane.INFORMATION_MESSAGE); return; }
				openFolder(d);
			}
		});
		openRow.add(openSaves);
		openRow.add(javax.swing.Box.createHorizontalStrut(8));
		openRow.add(openStation);
		openRow.add(javax.swing.Box.createHorizontalStrut(8));
		openRow.add(openJunk);
		openRow.add(javax.swing.Box.createHorizontalStrut(8));
		openRow.add(openLog);
		openRow.add(javax.swing.Box.createHorizontalStrut(8));
		openRow.add(openLogs);
		body.add(openRow, next(c));

		heading(body, c, "Launching");
		body.add(steamBox, next(c));
		JLabel cloud = new JLabel("<html><div style='width:560px; color:#a05a00'>Steam version: turn off Steam Cloud for FTL (in your Steam library, right-click FTL, Properties, General). "
				+ "With it on, Steam can bring back a docked ship as a copy, or an old FTL profile.</div></html>");
		cloud.setBorder(BorderFactory.createEmptyBorder(0, 24, 4, 0));
		body.add(cloud, next(c));

		heading(body, c, "Mods");
		JPanel modRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		JButton patchBtn = new JButton("Patch mods...");
		patchBtn.setToolTipText("Choose which mods to send to FTL; Slipstream carries them (the Federation Home Planet Mod is always included)");
		patchBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { PatchDialog.open(SettingsDialog.this); }
		});
		JButton slipBtn = new JButton("Set Slipstream folder...");
		slipBtn.setToolTipText("Choose the Slipstream folder, or download Slipstream into one");
		slipBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String old = HomePlanet.config.getProperty(homeplanet.core.Slipstream.CFG_DIR);
				HomePlanet.config.remove(homeplanet.core.Slipstream.CFG_DIR);
				java.io.File d = homeplanet.core.Slipstream.locate(SettingsDialog.this);
				if (d == null && old != null) HomePlanet.config.setProperty(homeplanet.core.Slipstream.CFG_DIR, old); // cancelled: keep the old one
			}
		});
		JButton modsBtn = new JButton("Open mods folder");
		modsBtn.setToolTipText("Open Slipstream's mods folder in Windows Explorer (drop new .ftl files there)");
		modsBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				java.io.File d = homeplanet.core.Slipstream.locate(SettingsDialog.this);
				if (d != null) openFolder(homeplanet.core.Slipstream.modsDir(d));
			}
		});
		modRow.add(patchBtn);
		modRow.add(javax.swing.Box.createHorizontalStrut(8));
		modRow.add(modsBtn);
		modRow.add(javax.swing.Box.createHorizontalStrut(8));
		JButton starterBtn = new JButton("Blueprints...");
		starterBtn.setToolTipText("Your own blueprints (remodels and designs): which can be commissioned, and their names and starting loadouts");
		starterBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { starterShips(); }
		});
		modRow.add(starterBtn);
		modRow.add(javax.swing.Box.createHorizontalStrut(8));
		modRow.add(slipBtn);
		body.add(modRow, next(c));

		heading(body, c, "Audio");
		body.add(musicBox, next(c));

		heading(body, c, "Rules");
		rules.addTo(body, c);

		heading(body, c, "After a final victory" + (homeplanet.vault.Vault.get().immersive ? " (Immersive fleet)" : ""));
		javax.swing.ButtonGroup victoryGroup = new javax.swing.ButtonGroup();
		for (int i = 0; i < victoryButtons.length; i++) {
			String ch = homeplanet.parser.FinalVictory.CHOICES[i];
			victoryButtons[i] = new javax.swing.JRadioButton(homeplanet.parser.FinalVictory.label(ch), ch.equals(victoryWas));
			victoryGroup.add(victoryButtons[i]);
			body.add(victoryButtons[i], next(c));
		}
		victoryButtons[1].setToolTipText("She comes back as she was moments before the final engagement, ready for a new journey; or take her full value for the museum");
		victoryButtons[2].setToolTipText("Her full value, as the shipyard would charge for her, goes to Spacedock Storage");
		JLabel victoryNote = new JLabel("<html><div style='width:520px'><font color='#777777'>For a rescue or a reward, The Home Planet Station must be open while you play: "
				+ "it keeps her as the Rebel Flagship heads for the last battle. Each fleet has its own choice.</font></div></html>");
		victoryNote.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
		body.add(victoryNote, next(c));

		heading(body, c, "Troubleshooting");
		body.add(debugBox, next(c));

		heading(body, c, "About");
		JPanel about = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		about.add(new JLabel(HomePlanet.APP_NAME + " " + HomePlanet.APP_VERSION + "  -  GPL-2.0.  FTL by Subset Games; save parser by Vhati; after ManApart's FTL Homeworld; made by heromedel with Claude.  "));
		JButton loreBtn = new JButton("Lore...");
		loreBtn.setToolTipText("A transmission from the Federation Home Planet");
		loreBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { showLore(); }
		});
		about.add(loreBtn);
		about.add(javax.swing.Box.createHorizontalStrut(8));
		JButton creditsBtn = new JButton("Credits...");
		creditsBtn.setToolTipText("Who made what, and what is bundled");
		creditsBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { showBundledText("CREDITS.md", "Credits"); }
		});
		about.add(creditsBtn);
		about.add(javax.swing.Box.createHorizontalStrut(8));
		JButton licenceBtn = new JButton("Licence...");
		licenceBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { showBundledText("LICENSE", "Licence (GPL-2.0)"); }
		});
		about.add(licenceBtn);
		body.add(about, next(c));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton ok = new JButton("OK");
		JButton cancel = new JButton("Cancel");
		ok.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { apply(); }
		});
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { dispose(); }
		});
		buttons.add(ok);
		buttons.add(cancel);
		getRootPane().setDefaultButton(ok);

		refreshLabels();
		getContentPane().add(body, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setResizable(false);
		setLocationRelativeTo(owner);
	}

	private void apply() {
		boolean gameChanged = !game.equals(HomePlanet.datsPath);
		java.util.List<String> changed = new java.util.ArrayList<String>();
		if (!saves.equals(HomePlanet.save_location)) changed.add("Saves folder: " + saves.getPath());
		if (gameChanged) changed.add("Game folder: " + game.getPath());
		if (steamBox.isSelected() != HomePlanet.launchThroughSteam) changed.add("Launch through Steam: " + steamBox.isSelected());
		rules.describeChanges(changed);
		if (!victoryChoice().equals(victoryWas)) changed.add("After a final victory: " + victoryChoice());
		if (debugBox.isSelected() != HomePlanet.debugLogging) changed.add("Debug logging: " + debugBox.isSelected());
		if (musicBox.isSelected() != homeplanet.core.Music.enabled) changed.add("Title music: " + musicBox.isSelected());
		if (!changed.isEmpty()) homeplanet.core.HistoryLog.entry("SETTINGS", "", changed);
		savesChanged = !saves.equals(HomePlanet.save_location);
		HomePlanet.save_location = saves;
		if (savesChanged) {
			// another saves folder is another vault (its own ships, designs and remodels)
			try {
				homeplanet.vault.Vault.open(saves, HomePlanet.immersiveMode);
				homeplanet.parser.CompanionMod.register(homeplanet.parser.CompanionMod.load());
				homeplanet.vault.Vault.get().takeStock();
			} catch (java.io.IOException e) {
				HomePlanet.showErrorDialog("The Home Planet Station could not open its fleet records in:\n" + saves + "\n\n" + e);
			}
		}
		HomePlanet.datsPath = game;
		HomePlanet.launchThroughSteam = steamBox.isSelected();
		rules.apply();
		if (!savesChanged && !victoryChoice().equals(victoryWas)) {
			try { homeplanet.parser.FinalVictory.setChoice(victoryChoice()); }
			catch (java.io.IOException e) { HomePlanet.showErrorDialog("The Home Planet Station could not record the choice after a final victory:\n" + e.getMessage()); }
		}
		HomePlanet.setDebugLogging(debugBox.isSelected());
		homeplanet.core.Music.enabled = musicBox.isSelected();
		homeplanet.core.Music.refresh(); // starts or stops right away
		java.awt.Window owner = getOwner();
		if (owner instanceof MainFrame) ((MainFrame) owner).cargoBay.updateSupplyButtons(); // the sell buttons follow the rule at once
		if (HomePlanet.saveConfig() && gameChanged) {
			JOptionPane.showMessageDialog(this, "The Home Planet Station will use the new FTL game folder the next time it starts.",
					"Settings", JOptionPane.INFORMATION_MESSAGE);
		}
		dispose();
	}

	/** The station's own blueprints: tick the starter ships (they can be commissioned), and Edit a blueprint's name and loadout. */
	private void starterShips() {
		final java.util.List<homeplanet.parser.CompanionMod.Remodel> all = homeplanet.parser.CompanionMod.load();
		if (all.isEmpty()) {
			JOptionPane.showMessageDialog(this, "No ship has been remodeled yet: there are no blueprints of your own.", "Blueprints", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JPanel list = new JPanel(new java.awt.GridBagLayout());
		java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
		g.anchor = java.awt.GridBagConstraints.WEST;
		g.insets = new Insets(2, 0, 2, 8);
		final java.util.List<JCheckBox> boxes = new java.util.ArrayList<JCheckBox>();
		final java.util.Set<homeplanet.parser.CompanionMod.Remodel> edited = new java.util.HashSet<homeplanet.parser.CompanionMod.Remodel>();
		for (int i = 0; i < all.size(); i++) {
			final homeplanet.parser.CompanionMod.Remodel r = all.get(i);
			final JCheckBox b = new JCheckBox(blueprintLabel(r), r.starter);
			b.setToolTipText("Ticked: a starter ship, available to commission");
			boxes.add(b);
			JButton edit = new JButton("Edit...");
			edit.setToolTipText("Her class, default name, and what a newly commissioned ship starts with");
			edit.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					homeplanet.parser.CompanionMod.Loadout start = r.loadout != null ? homeplanet.parser.CompanionMod.copy(r.loadout)
							: homeplanet.parser.CompanionMod.loadoutOf(r.id);
					BlueprintDialog.Result res = BlueprintDialog.open(SettingsDialog.this, "Blueprint: " + r.id, null, start, null, r.id, false, r.starter, "OK");
					if (res == null) return;
					r.loadout = res.loadout;
					edited.add(r);
					b.setText(blueprintLabel(r));
				}
			});
			g.gridy = i; g.gridx = 0; g.weightx = 1;
			list.add(b, g);
			g.gridx = 1; g.weightx = 0;
			list.add(edit, g);
		}
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.add(new JLabel("<html>Ticked blueprints are starter ships: they can be commissioned at the Space Dock.<br>Edit sets a blueprint's class, default name and starting loadout.</html>"), BorderLayout.NORTH);
		javax.swing.JScrollPane sp = new javax.swing.JScrollPane(list);
		sp.setPreferredSize(new java.awt.Dimension(560, Math.min(340, 34 * all.size() + 12)));
		panel.add(sp, BorderLayout.CENTER);
		int r = JOptionPane.showConfirmDialog(this, panel, "Blueprints", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (r != JOptionPane.OK_OPTION) return;
		java.util.List<String> changed = new java.util.ArrayList<String>();
		for (int i = 0; i < all.size(); i++) {
			boolean v = boxes.get(i).isSelected();
			if (v != all.get(i).starter) changed.add(all.get(i).id + ": " + (v ? "starter ship" : "not a starter ship"));
			all.get(i).starter = v;
			if (edited.contains(all.get(i))) changed.add(all.get(i).id + ": class, name and loadout edited");
		}
		if (changed.isEmpty()) return;
		try {
			homeplanet.parser.CompanionMod.save(all);
		} catch (Exception ex) {
			HomePlanet.showErrorDialog("The Home Planet Station could not update the blueprint list:\n" + ex);
			return;
		}
		homeplanet.parser.CompanionMod.register(all); // Commission sees the changes at once
		if (!edited.isEmpty()) homeplanet.core.Slipstream.writeMod(); // keep the mod's copy in step
		homeplanet.core.HistoryLog.entry("BLUEPRINTS", changed.size() + " change(s)", changed);
	}
	private static String blueprintLabel(homeplanet.parser.CompanionMod.Remodel r) {
		String own = r.loadout != null && r.loadout.className.length() > 0 ? r.loadout.className : null;
		net.blerf.ftl.xml.ShipBlueprint bp = net.blerf.ftl.parser.DataManager.get().getShip(r.id);
		String cls = own != null ? own : bp != null ? CommissionDialog.classOf(bp) + " " + homeplanet.parser.CompanionMod.numberOf(r.id) : r.id;
		String text = cls + "  (" + r.ship + "'s layout, " + r.made + ")";
		if (!homeplanet.parser.CompanionMod.inGameData(r.id)) text += "  - not patched in yet";
		return text;
	}

	private void refreshLabels() {
		savesLabel.setText(saves.getPath());
		savesLabel.setToolTipText(saves.getPath());
		gameLabel.setText(game.getPath());
		gameLabel.setToolTipText(game.getPath() + " (where FTLGame.exe and ftl.dat are)");
		pack();
	}

	private void openFolder(File dir) {
		try {
			Desktop.getDesktop().open(dir);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "The Home Planet Station could not open the folder:\n" + dir.getPath(),
					"Open folder", JOptionPane.WARNING_MESSAGE);
		}
	}

	private static JPanel folderRow(String name, JLabel path, ActionListener change) {
		JPanel row = new JPanel(new BorderLayout(8, 0));
		JLabel n = new JLabel(name);
		n.setPreferredSize(new java.awt.Dimension(95, n.getPreferredSize().height));
		JButton b = new JButton("Change...");
		b.addActionListener(change);
		row.add(n, BorderLayout.WEST);
		row.add(path, BorderLayout.CENTER);
		row.add(b, BorderLayout.EAST);
		return row;
	}

	/** Shows a text file bundled in the jar (CREDITS.md, LICENSE) in a window of its own. */
	static final String LORE = "~ Incoming transmission from the Federation Home Planet ~\n\n"
			+ "Despite the ongoing war with the Rebellion, the Federation has restored its long-range trade and communication network, "
			+ "carried by official stores and stations across the sectors.\n\n"
			+ "From the Home Planet, the Federation can once more reach beacons in many star systems: moving goods and crew between ships "
			+ "almost instantly, refitting hulls in its dry docks, and commissioning new ships wherever a captain needs one.\n\n"
			+ "Welcome home, Captain, and godspeed.";

	private void showLore() {
		javax.swing.JTextArea ta = new javax.swing.JTextArea(LORE, 11, 52);
		ta.setEditable(false);
		ta.setLineWrap(true);
		ta.setWrapStyleWord(true);
		ta.setOpaque(false);
		ta.setFont(new JLabel().getFont().deriveFont(13f));
		JOptionPane.showMessageDialog(this, ta, "Lore", JOptionPane.PLAIN_MESSAGE);
	}

	private void showBundledText(String name, String title) {
		String text;
		try {
			java.io.InputStream in = getClass().getResourceAsStream("/" + name);
			if (in == null) throw new java.io.IOException("not bundled");
			text = new String(homeplanet.core.SafeFiles.readAll(in), "UTF-8");
			if (name.endsWith(".md")) text = text.replaceAll("(?<!\n)\n(?![\n*|#-])", " "); // the file is wrapped by hand; let the window wrap it
		} catch (Exception ex) {
			text = name + " couldn't be read from this build. It's in the source download, on the project's page.";
		}
		javax.swing.JTextArea ta = new javax.swing.JTextArea(text, 32, 92);
		ta.setEditable(false);
		ta.setLineWrap(true);
		ta.setWrapStyleWord(true);
		ta.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
		ta.setCaretPosition(0);
		javax.swing.JScrollPane sp = new javax.swing.JScrollPane(ta);
		JOptionPane.showMessageDialog(this, sp, title, JOptionPane.PLAIN_MESSAGE);
	}

	private String victoryChoice() {
		for (int i = 0; i < victoryButtons.length; i++) if (victoryButtons[i].isSelected()) return homeplanet.parser.FinalVictory.CHOICES[i];
		return homeplanet.parser.FinalVictory.NOTHING;
	}

	static void heading(JPanel body, GridBagConstraints c, String text) {
		JLabel h = new JLabel(text);
		h.setFont(h.getFont().deriveFont(Font.BOLD));
		if (c.gridy > 0) h.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		body.add(h, next(c));
	}

	private static GridBagConstraints next(GridBagConstraints c) {
		GridBagConstraints copy = (GridBagConstraints) c.clone();
		c.gridy++;
		return copy;
	}
}
