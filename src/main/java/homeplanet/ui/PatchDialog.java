package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import homeplanet.core.HomePlanet;
import homeplanet.core.HistoryLog;
import homeplanet.core.Slipstream;
import homeplanet.core.Slipstream.Mod;
import homeplanet.parser.Retrofit;

/**
 * Patch mods: tick the mods in Slipstream's mods folder, set their order, and have Slipstream patch the game data.
 * The companion mod is always in (last), so retrofitted ships keep working.
 */
public class PatchDialog extends JDialog {

	/** A row in the list. */
	private static class Row {
		final Mod mod;
		boolean on;
		final boolean locked;
		Row(Mod mod, boolean on, boolean locked) { this.mod = mod; this.on = on; this.locked = locked; }
	}

	private final File dir;
	private final DefaultListModel<Row> model = new DefaultListModel<Row>();
	private final JList<Row> list = new JList<Row>(model);
	private final JCheckBox rememberBox = new JCheckBox("Remember my picks", Boolean.parseBoolean(HomePlanet.config.getProperty(Slipstream.CFG_REMEMBER, "true")));
	private final JCheckBox runBox = new JCheckBox("Launch FTL after patching", Boolean.parseBoolean(HomePlanet.config.getProperty(Slipstream.CFG_RUN, "false")));
	private final JButton upBtn = new JButton("Up"), downBtn = new JButton("Down");
	private boolean patched = false;

	/** Opens the window (finding Slipstream first, if needed). Returns true if a patch succeeded. */
	public static boolean open(Component owner) {
		File dir = Slipstream.locate(owner);
		if (dir == null) return false;
		Window w = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
		PatchDialog d = new PatchDialog(w, dir);
		d.setVisible(true);
		return d.patched;
	}

	private PatchDialog(Window owner, File dir) {
		super(owner, "Patch mods", ModalityType.APPLICATION_MODAL);
		this.dir = dir;
		fill();

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new RowRenderer());
		list.setVisibleRowCount(10);
		list.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				int i = list.locationToIndex(e.getPoint());
				if (i < 0 || !list.getCellBounds(i, i).contains(e.getPoint())) return;
				Row r = model.get(i);
				if (r.locked) return;
				// A click on the box toggles; a click on the name only selects (for Up/Down)
				if (e.getX() - list.getCellBounds(i, i).x < 24) {
					r.on = !r.on;
					list.repaint();
				}
			}
		});
		list.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
			public void valueChanged(javax.swing.event.ListSelectionEvent e) { updateButtons(); }
		});

		JPanel body = new JPanel(new BorderLayout(8, 8));
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		JTextArea intro = note("Tick the mods to install in FTL. They load top to bottom.\n"
				+ "Slipstream starts from a clean copy of FTL every time, so unticked mods are removed.\n"
				+ "The " + Retrofit.MOD_NAME + " is always installed, last, so retrofitted ships keep working.\n"
				+ "Close FTL before patching.");
		body.add(intro, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(420, 220));
		body.add(scroll, BorderLayout.CENTER);

		JPanel side = new JPanel();
		side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
		upBtn.setToolTipText("Load the selected mod sooner (mods lower in the list override the ones above)");
		downBtn.setToolTipText("Load the selected mod later (mods lower in the list override the ones above)");
		upBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { move(-1); } });
		downBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { move(1); } });
		Dimension bd = new Dimension(Math.max(upBtn.getPreferredSize().width, downBtn.getPreferredSize().width) + 12, upBtn.getPreferredSize().height);
		upBtn.setPreferredSize(bd); downBtn.setPreferredSize(bd);
		upBtn.setMaximumSize(bd); downBtn.setMaximumSize(bd);
		side.add(upBtn);
		side.add(Box.createVerticalStrut(6));
		side.add(downBtn);
		body.add(side, BorderLayout.EAST);

		JPanel toggles = new JPanel();
		toggles.setLayout(new BoxLayout(toggles, BoxLayout.Y_AXIS));
		rememberBox.setToolTipText("Tick the same mods, in the same order, next time this window opens");
		runBox.setToolTipText("Start FTL once Slipstream has sent the mods (uses the Launch FTL setting in Settings)");
		toggles.add(rememberBox);
		toggles.add(runBox);
		JLabel where = new JLabel("Slipstream: " + dir.getPath());
		where.setToolTipText(dir.getPath());
		where.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		toggles.add(where);
		body.add(toggles, BorderLayout.SOUTH);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton patch = new JButton("Patch");
		JButton cancel = new JButton("Cancel");
		patch.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { patch(); } });
		cancel.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } });
		buttons.add(patch);
		buttons.add(cancel);
		getRootPane().setDefaultButton(patch);

		getContentPane().add(body, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		updateButtons();
		pack();
		setMinimumSize(getSize());
		setLocationRelativeTo(owner);
	}

	/** Lists the mods folder: remembered picks first (ticked, in their order), then the rest unticked, the companion mod locked at the end. */
	private void fill() {
		Slipstream.installMod(dir); // the copy in mods/ matches the one shipped with this build
		List<Mod> mods = Slipstream.mods(dir);
		List<String> remembered = new ArrayList<String>();
		String saved = HomePlanet.config.getProperty(Slipstream.CFG_MODS, "");
		if (saved.length() > 0) remembered.addAll(Arrays.asList(saved.split("\\|")));
		Mod homeworld = null;
		List<Mod> rest = new ArrayList<Mod>();
		for (Mod m : mods) {
			if (m.isCompanionMod()) homeworld = m;
			else if (!m.isStrayCopy()) rest.add(m);
		}
		for (String name : remembered) {
			for (Mod m : rest) if (m.name().equals(name)) { model.addElement(new Row(m, true, false)); rest.remove(m); break; }
		}
		for (Mod m : rest) model.addElement(new Row(m, false, false));
		if (homeworld != null) model.addElement(new Row(homeworld, true, true));
	}

	private void updateButtons() {
		int i = list.getSelectedIndex();
		int last = model.size() - 1;
		boolean movable = i >= 0 && !model.get(i).locked;
		upBtn.setEnabled(movable && i > 0);
		downBtn.setEnabled(movable && i < last && !model.get(i + 1).locked);
	}

	private void move(int by) {
		int i = list.getSelectedIndex();
		if (i < 0) return;
		int j = i + by;
		if (j < 0 || j >= model.size() || model.get(j).locked) return;
		Row r = model.remove(i);
		model.add(j, r);
		list.setSelectedIndex(j);
		updateButtons();
	}

	private List<String> picked() {
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < model.size(); i++) if (model.get(i).on) names.add(model.get(i).mod.name());
		return names;
	}

	private void patch() {
		boolean hasCompanionMod = model.size() > 0 && model.get(model.size() - 1).locked;
		if (!hasCompanionMod) {
			int r = JOptionPane.showConfirmDialog(this, "The Home Planet Station couldn't write the " + Retrofit.MOD_NAME + " to Slipstream's mods folder.\n"
					+ "Retrofitted ships can't fly without it. Send the other mods to FTL anyway?", "Patch mods", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (r != JOptionPane.YES_OPTION) return;
		}
		final List<String> names = picked();
		HomePlanet.config.setProperty(Slipstream.CFG_REMEMBER, Boolean.toString(rememberBox.isSelected()));
		HomePlanet.config.setProperty(Slipstream.CFG_RUN, Boolean.toString(runBox.isSelected()));
		if (rememberBox.isSelected()) {
			List<String> keep = new ArrayList<String>();
			for (int i = 0; i < model.size(); i++) if (model.get(i).on && !model.get(i).locked) keep.add(model.get(i).mod.name());
			HomePlanet.config.setProperty(Slipstream.CFG_MODS, String.join("|", keep));
		} else {
			HomePlanet.config.remove(Slipstream.CFG_MODS);
		}
		HomePlanet.saveConfig();
		List<String> order = new ArrayList<String>();
		for (int i = 0; i < model.size(); i++) order.add(model.get(i).mod.name());
		Slipstream.writeOrder(dir, order);

		final boolean runFtl = runBox.isSelected();
		final JDialog wait = new JDialog(this, "Patching", ModalityType.APPLICATION_MODAL);
		JLabel l = new JLabel("Slipstream is installing the mods into FTL... this takes a moment.");
		l.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
		wait.getContentPane().add(l);
		wait.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		wait.pack();
		wait.setLocationRelativeTo(this);
		final Slipstream.Result[] result = {null};
		Thread t = new Thread(new Runnable() {
			public void run() {
				result[0] = Slipstream.patch(dir, names, false); // the station launches FTL itself, with its own settings and checks
				SwingUtilities.invokeLater(new Runnable() { public void run() { wait.dispose(); } });
			}
		}, "slipstream-patch");
		t.start();
		wait.setVisible(true); // blocks until the thread closes it
		Slipstream.Result res = result[0];
		List<String> details = new ArrayList<String>(names);
		if (res != null && res.ok()) {
			HistoryLog.entry("PATCH", "Patched " + count(names.size()) + " with Slipstream" + (runFtl ? ", then launched FTL" : ""), details);
			patched = true;
			homeplanet.parser.PatchState.refresh(); // ftl.dat changed: what's in the game is read again from it
			if (runFtl) HomePlanet.launchFTL();
			Object[] options = {"Restart now", "Later"};
			int r = JOptionPane.showOptionDialog(this,
					"Done. FTL now has " + count(names.size()) + " installed" + (runFtl ? ", and FTL is starting." : ".") + "\n\n"
					+ "The Home Planet Station reads FTL's files only when it opens. Restart it to pick up the new mods.",
					"Patch mods", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
			dispose();
			if (r == 0) Slipstream.restart();
		} else {
			String out = res == null ? "" : res.output.trim();
			HistoryLog.entry("PATCH", "Slipstream patch failed" + (res == null ? "" : " (exit " + res.exitCode + ")"), details);
			JTextArea ta = new JTextArea(out.length() == 0 ? "(no output)" : out, 12, 70);
			ta.setEditable(false);
			ta.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
			JPanel p = new JPanel(new BorderLayout(0, 6));
			p.add(note("Slipstream ran into a problem. It starts every patch from a clean copy of FTL, so FTL probably has no mods "
					+ "installed right now. Retrofitted ships can't fly until a patch reaches FTL via Slipstream.\n\nWhat Slipstream said:"), BorderLayout.NORTH);
			p.add(new JScrollPane(ta), BorderLayout.CENTER);
			JOptionPane.showMessageDialog(this, p, "Patch failed", JOptionPane.ERROR_MESSAGE);
		}
	}

	private static String count(int n) { return n == 1 ? "1 mod" : n + " mods"; }

	private static JTextArea note(String text) {
		JTextArea a = new JTextArea(text);
		a.setEditable(false);
		a.setLineWrap(true);
		a.setWrapStyleWord(true);
		a.setOpaque(false);
		a.setFocusable(false);
		a.setFont(new JLabel().getFont());
		a.setBorder(null);
		return a;
	}

	/** A checkbox per mod: the mod's title, with the file name as tooltip. The companion mod is greyed (locked on). */
	private static class RowRenderer extends JCheckBox implements ListCellRenderer<Row> {
		public Component getListCellRendererComponent(JList<? extends Row> l, Row r, int index, boolean selected, boolean focus) {
			setText(r.mod.title + (r.locked ? "  (always included)" : ""));
			setSelected(r.on);
			setEnabled(!r.locked);
			setToolTipText(r.mod.name());
			setOpaque(true);
			setBackground(selected ? l.getSelectionBackground() : l.getBackground());
			setForeground(selected ? l.getSelectionForeground() : l.getForeground());
			setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
			return this;
		}
	}
}
