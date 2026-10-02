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
 * Settings, in four tabs: General (folders, launching, mods, audio), Rules (the game mode, the rules, after a final
 * victory), Records (the station and ship logs, shown in the page; the debug log folder and debug logging) and About.
 * Nothing changes until OK; OK writes the cfg file.
 */
public class SettingsDialog extends JDialog {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SettingsDialog.class);
	private File saves = HomePlanet.save_location;
	private File game = HomePlanet.datsPath;
	private final JLabel savesLabel = new JLabel();
	private final JLabel gameLabel = new JLabel();
	private final JCheckBox steamBox = new JCheckBox("Launch FTL through Steam", HomePlanet.launchThroughSteam);
	private final RuleBoxes rules = new RuleBoxes();
	private final JCheckBox musicBox = new JCheckBox("Play title music while the game is not open", homeplanet.core.Music.enabled);
	private final JCheckBox debugBox = new JCheckBox("Debug logging", HomePlanet.debugLogging);
	private boolean savesChanged = false;
	/** After a final victory: nothing, rescue her, or a reward of her value (the fleet in use has its own choice). */
	private final javax.swing.JRadioButton[] victoryButtons = new javax.swing.JRadioButton[homeplanet.parser.FinalVictory.CHOICES.length];
	private String victoryWas = homeplanet.parser.FinalVictory.choice();
	private final JLabel victoryHeading = new JLabel();
	private final String commanderWas = homeplanet.comm.Commander.name() == null ? "" : homeplanet.comm.Commander.name();
	private final javax.swing.JTextField commanderField = new javax.swing.JTextField(commanderWas, 18);
	private final JCheckBox shipTradeBox = new JCheckBox("Immersive careers: allow trading whole ships (with a career that allows it too; Sandbox fleets always may)", HomePlanet.immersiveShipTrading);
	private final JCheckBox popupBox = new JCheckBox("Priority messages from other commanders pop up (off: they go to the inbox, marked priority)", HomePlanet.longRangePopups);
	private final JCheckBox anyLevelBox = new JCheckBox("Immersive careers: allow trading with any Immersive level: Easy, Normal, Hard, Custom (when the other allows it too)", HomePlanet.immersiveAnyLevel);
	private final javax.swing.ButtonGroup victoryGroup = new javax.swing.ButtonGroup();

	/** Shows the dialog. Returns true if the saves folder changed (so the Space Dock should reload). */
	public static boolean open(java.awt.Component owner) {
		Window w = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
		SettingsDialog d = new SettingsDialog(w);
		d.setVisible(true);
		return d.savesChanged;
	}

	private SettingsDialog(Window owner) {
		super(owner, "Settings", ModalityType.APPLICATION_MODAL);
		// four tabs: General (folders, launching, mods, audio), Rules, Records, About
		JPanel general = page(), rulesPage = page(), recordsPage = page(), aboutPage = page();
		JPanel body = general;
		GridBagConstraints c = constraints();

		heading(body, c, "Commander");
		JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		JLabel nameLabel = new JLabel("Name:");
		nameLabel.setPreferredSize(new java.awt.Dimension(103, nameLabel.getPreferredSize().height)); // lines up with the folder paths below
		nameRow.add(nameLabel);
		nameRow.add(commanderField);
		JLabel nameNote = new JLabel("   How other commanders know you over Long Range Comm. (your rank goes in front)");
		nameNote.setForeground(MenuTheme.GREY_GREEN);
		nameRow.add(nameNote);
		commanderField.setToolTipText("Up to " + homeplanet.comm.Commander.MAX + " letters, numbers, spaces and ' - . (your rank goes in front of it)");
		body.add(nameRow, next(c));
		shipTradeBox.setToolTipText("Sandbox fleets always may. A ship traded in arrives commissioned, and only what she does in your fleet counts toward letters, rewards and achievements");
		anyLevelBox.setToolTipText("Off: your career trades only with careers of its own difficulty");
		heading(body, c, "Long Range Comm.");
		body.add(shipTradeBox, next(c));
		body.add(anyLevelBox, next(c));
		popupBox.setToolTipText("A commander can tick Priority on a message to you; at most one pops up a minute from each, whatever this says");
		body.add(popupBox, next(c));
		JPanel blockRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		final JLabel blockedLabel = new JLabel();
		final JButton unblockBtn = new JButton("Blocked commanders...");
		unblockBtn.setToolTipText("Commanders whose hails go unanswered: unblock them here");
		final Runnable showBlocked = new Runnable() {
			public void run() {
				int n = homeplanet.comm.Blocks.list().size();
				blockedLabel.setText("   " + (n == 0 ? "Nobody is blocked." : n == 1 ? "1 commander blocked." : n + " commanders blocked."));
				unblockBtn.setEnabled(n > 0);
			}
		};
		unblockBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { unblock(); showBlocked.run(); } });
		blockedLabel.setForeground(MenuTheme.GREY_GREEN);
		showBlocked.run();
		blockRow.add(unblockBtn);
		blockRow.add(blockedLabel);
		body.add(blockRow, next(c));

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
		openJunk.setToolTipText("Open the Junkyard folder (decommissioned ships) in Windows Explorer");
		openJunk.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				File junk = homeplanet.vault.Vault.get().junkyardDir();
				if (!junk.isDirectory() || homeplanet.vault.Vault.get().junked().isEmpty()) {
					JOptionPane.showMessageDialog(SettingsDialog.this, "The Junkyard is empty. No ship has been decommissioned yet.",
							"Open Junkyard", JOptionPane.INFORMATION_MESSAGE);
					return;
				}
				openFolder(junk);
			}
		});
		JButton openLogs = new JButton("Debug Log Folder");
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
		body.add(openRow, next(c));
		JPanel slipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); // Slipstream's folder, filled below
		body.add(slipRow, next(c));

		heading(body, c, "Launching");
		body.add(steamBox, next(c));
		JLabel cloud = new JLabel("<html><div style='width:560px; color:" + MenuTheme.HTML_ORANGE + "'>Steam version: turn off Steam Cloud for FTL (in your Steam library, right-click FTL, Properties, General). "
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
		slipRow.add(slipBtn);
		slipRow.add(javax.swing.Box.createHorizontalStrut(8));
		slipRow.add(modsBtn);
		JButton starterBtn = new JButton("Blueprints...");
		starterBtn.setToolTipText("Your own blueprints (remodels and designs): which can be commissioned, and their names and starting loadouts");
		starterBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { starterShips(); }
		});
		modRow.add(starterBtn);
		body.add(modRow, next(c));

		heading(body, c, "Audio");
		body.add(musicBox, next(c));

		body = rulesPage;
		c = constraints();
		heading(body, c, "Game mode and rules");
		rules.addTo(body, c);

		victoryHeading.setFont(MenuTheme.HEADING_FONT);
		victoryHeading.setForeground(MenuTheme.GOLD);
		victoryHeading.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		body.add(victoryHeading, next(c));
		for (int i = 0; i < victoryButtons.length; i++) {
			String ch = homeplanet.parser.FinalVictory.CHOICES[i];
			victoryButtons[i] = new javax.swing.JRadioButton(homeplanet.parser.FinalVictory.label(ch), ch.equals(victoryWas));
			victoryGroup.add(victoryButtons[i]);
			body.add(victoryButtons[i], next(c));
		}
		refreshVictory();
		rules.afterFleetChange = new Runnable() { public void run() { refreshVictory(); } };
		victoryButtons[1].setToolTipText("She comes back as she was moments before the final engagement, ready for a new journey; or take her full value for the museum");
		victoryButtons[2].setToolTipText("Her full value, as the shipyard would charge for her, goes to the Cargo Hold");
		JLabel victoryNote = new JLabel("<html><div style='width:520px'><font color='" + MenuTheme.HTML_GREY_GREEN + "'>For a rescue or a reward, The Home Planet Station must be open while you play: "
				+ "it keeps her as the Rebel Flagship heads for the last battle. Each fleet has its own choice.</font></div></html>");
		victoryNote.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
		body.add(victoryNote, next(c));

		body = recordsPage;
		c = constraints();
		heading(body, c, "Records");
		final LogViewer logViewer = new LogViewer();
		body.add(logViewer, next(c)); // the station log, the ships' logs and the debug log, shown here (never in a text editor)
		// under the viewer, one row: the folders and the debug toggle
		JPanel folderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		JButton recordsStation = new JButton("Open Station Folder");
		recordsStation.setToolTipText("The fleet in use's own folder: its station log, and a folder for each ship's records");
		recordsStation.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { openFolder(homeplanet.vault.Vault.get().root); }
		});
		folderRow.add(recordsStation);
		folderRow.add(javax.swing.Box.createHorizontalStrut(8));
		folderRow.add(openLogs);
		folderRow.add(javax.swing.Box.createHorizontalStrut(14));
		folderRow.add(debugBox);
		body.add(folderRow, next(c));
		JLabel debugNote = new JLabel("<html><div style='width:560px'><font color='" + MenuTheme.HTML_GREY_GREEN + "'>The program's own logs, one per run: "
				+ "send them along with a bug report. Debug logging adds detail to them.</font></div></html>");
		debugNote.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
		body.add(debugNote, next(c));

		body = aboutPage;
		c = constraints();
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
		JPanel updateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		JButton updateBtn = new JButton("Check for Updates...");
		updateBtn.setToolTipText("Ask The Federation Home Planet for newer construction plans (the main branch on GitHub)");
		updateBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { checkForUpdates(); }
		});
		updateRow.add(updateBtn);
		body.add(updateRow, next(c));

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
		javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
		JPanel[] pages = {general, rulesPage, recordsPage, aboutPage};
		String[] names = {"General", "Rules", "Records", "About"};
		for (int i = 0; i < pages.length; i++) {
			JPanel holder = new JPanel(new BorderLayout());
			holder.add(pages[i], BorderLayout.NORTH); // each page at the top of its tab
			tabs.addTab(names[i], holder);
		}
		final javax.swing.JTabbedPane t = tabs;
		final java.awt.Color normal = new java.awt.Color(220, 228, 235); // as the theme draws the others
		javax.swing.event.ChangeListener mark = new javax.swing.event.ChangeListener() { // the open tab's name in dark on its light tab
			public void stateChanged(javax.swing.event.ChangeEvent e) {
				for (int i = 0; i < t.getTabCount(); i++) t.setForegroundAt(i, i == t.getSelectedIndex() ? new java.awt.Color(20, 28, 40) : normal);
			}
		};
		tabs.addChangeListener(mark);
		mark.stateChanged(null);
		getContentPane().add(ScreenFit.wrap(tabs, 120, owner), BorderLayout.CENTER); // scrolls on a short screen
		getContentPane().add(buttons, BorderLayout.SOUTH);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setResizable(false);
		setLocationRelativeTo(owner);
		ScreenFit.keepOnScreen(this); // all of it on its screen, never under the taskbar
	}

	private void apply() {
		String commander = homeplanet.comm.Commander.clean(commanderField.getText());
		if (!commander.equals(commanderWas)) {
			String why = commander.isEmpty() && !commanderWas.isEmpty() ? "Your commander name can be changed, but not left empty." : homeplanet.comm.Commander.check(commander);
			if (why != null) {
				JOptionPane.showMessageDialog(this, why, "Commander name", JOptionPane.INFORMATION_MESSAGE);
				commanderField.requestFocusInWindow();
				return;
			}
		}
		boolean gameChanged = !game.equals(HomePlanet.datsPath);
		java.util.List<String> changed = new java.util.ArrayList<String>();
		if (shipTradeBox.isSelected() != HomePlanet.immersiveShipTrading) changed.add("Trading immersive ships: " + shipTradeBox.isSelected());
		if (anyLevelBox.isSelected() != HomePlanet.immersiveAnyLevel) changed.add("Trading with any Immersive level: " + anyLevelBox.isSelected());
		HomePlanet.immersiveShipTrading = shipTradeBox.isSelected();
		HomePlanet.immersiveAnyLevel = anyLevelBox.isSelected();
		if (popupBox.isSelected() != HomePlanet.longRangePopups) changed.add("Priority messages pop up: " + popupBox.isSelected());
		HomePlanet.longRangePopups = popupBox.isSelected();
		if (!commander.equals(commanderWas)) {
			changed.add("Commander name: " + commander);
			HomePlanet.config.setProperty(homeplanet.comm.Commander.CFG_NAME, commander); // written with the rest below
		}
		if (!saves.equals(HomePlanet.save_location)) changed.add("Saves folder: " + saves.getPath());
		if (gameChanged) changed.add("Game folder: " + game.getPath());
		if (steamBox.isSelected() != HomePlanet.launchThroughSteam) changed.add("Launch through Steam: " + steamBox.isSelected());
		rules.describeChanges(changed);
		if (!victoryChoice().equals(victoryWas)) changed.add("After a final victory: " + victoryChoice());
		if (debugBox.isSelected() != HomePlanet.debugLogging) changed.add("Debug logging: " + debugBox.isSelected());
		if (musicBox.isSelected() != homeplanet.core.Music.enabled) changed.add("Title music: " + musicBox.isSelected());
		log.debug("Settings saved: {}", changed.isEmpty() ? "nothing changed" : changed);
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
			+ "Despite the ongoing war with the rebellion, the Federation has restored its long-range trade and communication network, "
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
		ta.setFont(MenuTheme.TEXT_FONT);
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

	/** The fleet in use's choice after a final victory (entering or leaving Immersive Mode here switches fleets). */
	private void refreshVictory() {
		victoryWas = homeplanet.parser.FinalVictory.choice();
		homeplanet.parser.CareerRules career = homeplanet.parser.FinalVictory.fixed() != null ? homeplanet.parser.CareerRules.current() : null;
		homeplanet.vault.Vault v = homeplanet.vault.Vault.get();
		victoryHeading.setText("After a final victory" + (career != null ? " (" + homeplanet.vault.Vault.title(v.slot) + "): " + career.words(homeplanet.parser.CareerRules.VICTORY)
				: v.immersive ? " (" + homeplanet.vault.Vault.title(v.slot) + ")" : ""));
		victoryGroup.clearSelection();
		for (int i = 0; i < victoryButtons.length; i++) {
			victoryButtons[i].setSelected(homeplanet.parser.FinalVictory.CHOICES[i].equals(victoryWas));
			victoryButtons[i].setEnabled(career == null); // the career's difficulty decides it
		}
	}
	private String victoryChoice() {
		if (homeplanet.parser.FinalVictory.fixed() != null) return victoryWas;
		for (int i = 0; i < victoryButtons.length; i++) if (victoryButtons[i].isSelected()) return homeplanet.parser.FinalVictory.CHOICES[i];
		return homeplanet.parser.FinalVictory.NOTHING;
	}

	private static JPanel page() {
		JPanel p = new JPanel(new GridBagLayout());
		p.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		return p;
	}
	private static GridBagConstraints constraints() {
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(2, 0, 2, 0);
		c.weightx = 1;
		return c;
	}
	/**
	 * Check for Updates: main's version on GitHub against this one. Newer: in a git checkout, fetch it with GitHub
	 * Desktop; otherwise Update Now puts the new files in place, and the station closes for the Construction Yard to
	 * rebuild and reopen it.
	 */
	private void checkForUpdates() {
		String title = "Check for Updates";
		String latest;
		setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
		try {
			latest = homeplanet.core.Updater.latest();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "The Home Planet Station could not reach The Federation Home Planet:\n" + e.getMessage()
					+ "\n\nCheck the connection and try again. The address it asked: " + homeplanet.core.Updater.POM_URL, title, JOptionPane.WARNING_MESSAGE);
			return;
		} finally {
			setCursor(null);
		}
		String mine = HomePlanet.APP_VERSION;
		if (homeplanet.core.Updater.compare(latest, mine) <= 0) {
			JOptionPane.showMessageDialog(this, "The Home Planet Station is up to date: " + mine + " (the latest is " + latest + ").", title, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		java.io.File root = homeplanet.core.Updater.installRoot();
		String news = "The Federation Home Planet has newer construction plans: " + latest + " (this station is " + mine + ").";
		if (root == null) {
			JOptionPane.showMessageDialog(this, news + "\n\nThis copy wasn't built by the Construction Yard, so it can't update itself.\nDownload the new version from "
					+ homeplanet.core.Updater.PAGE_URL, title, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (homeplanet.core.Updater.isGitCheckout(root)) {
			JOptionPane.showMessageDialog(this, news + "\n\nThis station's folder is a git checkout: fetch the new version (GitHub Desktop: Fetch, then Pull),\n"
					+ "then run the Construction Yard.", title, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Object[] opts = {"Update Now", "Later"};
		if (JOptionPane.showOptionDialog(this, news + "\n\nUpdate Now downloads them and rebuilds the station: it closes, the Construction Yard builds the new version,"
				+ "\nthen opens it again. Your fleets, settings, mods and the downloaded JDK and Maven aren't touched.", title,
				JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]) != 0) return;
		final java.awt.Window owner = getOwner();
		if (owner instanceof MainFrame && !((MainFrame) owner).mayClose("update the station")) return;
		homeplanet.core.Updater.Result r;
		setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
		try {
			java.io.File zip = homeplanet.core.Updater.download(root);
			r = homeplanet.core.Updater.apply(root, zip);
			zip.delete();
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The Home Planet Station could not take on the new construction plans. Nothing was changed:\n" + e.getMessage());
			return;
		} finally {
			setCursor(null);
		}
		try {
			homeplanet.core.Updater.startRebuild(root);
		} catch (Exception e) {
			HomePlanet.showErrorDialog("The new construction plans (" + r.version + ") are in place, but the Construction Yard could not be started:\n" + e.getMessage()
					+ "\n\nClose the station and run \"" + homeplanet.core.Updater.BUILD_BAT + "\" in " + root + ".");
			return;
		}
		dispose();
		if (owner instanceof MainFrame) ((MainFrame) owner).closeNow();
		else System.exit(0);
	}

	static void heading(JPanel body, GridBagConstraints c, String text) {
		JLabel h = new JLabel(text);
		h.setFont(MenuTheme.HEADING_FONT);
		h.setForeground(MenuTheme.GOLD);
		if (c.gridy > 0) h.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		body.add(h, next(c));
	}

	private static GridBagConstraints next(GridBagConstraints c) {
		GridBagConstraints copy = (GridBagConstraints) c.clone();
		c.gridy++;
		return copy;
	}

	/** The blocked commanders, to unblock one at a time (at once: it isn't one of the settings Save waits for). */
	private void unblock() {
		while (true) {
			java.util.List<homeplanet.comm.Blocks.Entry> l = homeplanet.comm.Blocks.list();
			if (l.isEmpty()) return;
			String[] names = new String[l.size()];
			for (int i = 0; i < names.length; i++) {
				homeplanet.comm.Blocks.Entry e = l.get(i);
				names[i] = (e.name.isEmpty() ? "A commander" : e.name) + "  (station " + e.station + (e.address.isEmpty() ? "" : ", " + e.address) + ")";
			}
			javax.swing.JList<String> list = new javax.swing.JList<String>(names);
			list.setSelectedIndex(0);
			list.setVisibleRowCount(Math.min(8, names.length));
			JPanel p = new JPanel(new java.awt.BorderLayout(0, 8));
			p.add(new JLabel("Their hails go unanswered, and your station doesn't answer their searches."), java.awt.BorderLayout.NORTH);
			p.add(new javax.swing.JScrollPane(list), java.awt.BorderLayout.CENTER);
			Object[] opts = {"Unblock", "Close"};
			int r = javax.swing.JOptionPane.showOptionDialog(this, p, "Blocked commanders", javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE, null, opts, opts[1]);
			if (r != 0 || list.getSelectedIndex() < 0) return;
			homeplanet.comm.Blocks.unblock(l.get(list.getSelectedIndex()).station);
		}
	}

}
