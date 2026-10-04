package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import homeplanet.parser.ShipChecks;
import homeplanet.parser.ShipDesign;

/**
 * The window both Design Ship and Remodel are built on: the layout editor in the middle, the work on the right as
 * steps in build order (Rooms; Art when the art is editable; Loadout for a design), the status and live checks along
 * the bottom, and a help window (Help, or F1) that describes exactly the tools in use. The keys, the window's size, the unsaved-changes guard and
 * the checks line are all here, so the two windows can't drift apart.
 */
public abstract class ShipEditorDialog extends JDialog implements LayoutEditor.Host {

	protected final ShipDesign d;
	protected LayoutEditor editor;
	protected ArtPanel artPanel;
	protected LoadoutPanel loadoutPanel;
	/** The steps on the right: Rooms, Art, Loadout, each a tab, in build order. */
	private JTabbedPane steps;
	private JScrollPane canvasScroll;
	private JDialog helpWindow;
	private javax.swing.JEditorPane helpPane;
	private final JLabel status = new JLabel(" ");
	private final JLabel checks = new JLabel(" ");
	private final JPanel north = new JPanel(new BorderLayout(6, 4));
	private final JPanel sideButtons = new JPanel(new GridLayout(0, 1, 0, 4));
	private final JPanel bottomButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
	private final JPanel helpRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private boolean built = false;

	protected ShipEditorDialog(Window owner, String title, ShipDesign d) {
		super(owner, title, ModalityType.APPLICATION_MODAL);
		this.d = d;
	}

	/**
	 * Lays the window out. Called once by the subclass after it has made the editor.
	 * @param above a row above the editor (the name field), or null
	 * @param artNow whether the art tools show from the start
	 */
	protected void buildUi(JComponent above, boolean artNow) {
		if (above != null) {
			north.add(above, BorderLayout.CENTER);
			north.setBorder(BorderFactory.createEmptyBorder(6, 8, 0, 8));
		}

		JPanel body = new JPanel(new BorderLayout(8, 8));
		body.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
		canvasScroll = new JScrollPane(editor.canvas());
		body.add(canvasScroll, BorderLayout.CENTER);
		steps = new JTabbedPane();
		steps.addTab(stepTitle(1, "Rooms"), null, editor.side(), "Her rooms, doors and systems");
		if (loadoutPanel != null) steps.addTab(stepTitle(3, "Loadout"), null, loadoutPanel, "Who she is, her numbers, her crew, what she carries, what's installed at the start");
		steps.addChangeListener(new javax.swing.event.ChangeListener() {
			public void stateChanged(javax.swing.event.ChangeEvent e) {
				if (loadoutPanel != null && steps.getSelectedComponent() == loadoutPanel) loadoutPanel.fill(); // as the design is now, after the other steps
				refreshHelp();
			}
		});
		body.add(steps, BorderLayout.EAST);
		for (java.awt.Component c : sideButtons.getComponents()) editor.addSideButton((JButton) c);
		sideButtons.removeAll();

		JPanel bottom = new JPanel(new BorderLayout());
		JPanel msgs = new JPanel(new GridLayout(0, 1));
		msgs.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
		msgs.add(status);
		msgs.add(checks);
		bottom.add(msgs, BorderLayout.CENTER);
		helpRow.add(button("Help", "How the editor works: the tools, the mouse and the keys (F1)", new ActionListener() {
			public void actionPerformed(ActionEvent e) { showHelp(); }
		}), 0);
		JPanel buttonsRow = new JPanel(new BorderLayout());
		buttonsRow.add(helpRow, BorderLayout.WEST);
		buttonsRow.add(bottomButtons, BorderLayout.EAST);
		bottom.add(buttonsRow, BorderLayout.SOUTH);

		if (above != null) getContentPane().add(north, BorderLayout.NORTH);
		getContentPane().add(body, BorderLayout.CENTER);
		getContentPane().add(bottom, BorderLayout.SOUTH);
		if (artNow) showArtPanel(true);
		editor.installKeys(getRootPane(), new Runnable() { public void run() { primaryAction(); } });
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0), "editor.help");
		getRootPane().getActionMap().put("editor.help", new javax.swing.AbstractAction() {
			public void actionPerformed(ActionEvent e) { showHelp(); }
		});
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			public void windowOpened(java.awt.event.WindowEvent e) {
				String was = status.getText();
				if (!d.rooms.isEmpty() || !d.art.isEmpty()) editor.fitView();
				status.setText(was); // the opening message, not "Zoom n%"
			}
			public void windowClosing(java.awt.event.WindowEvent e) { closeGuarded(); }
			public void windowClosed(java.awt.event.WindowEvent e) { if (helpWindow != null) helpWindow.dispose(); }
		});
		built = true;
	}
	/** A button under the editor's tools (Clear all, Overhaul...). Add before {@link #buildUi}. */
	protected void addSideButton(JButton b) { sideButtons.add(b); }
	/** Design Ship's Loadout step. Set before {@link #buildUi}. */
	protected void setLoadoutStep(LoadoutPanel p) { loadoutPanel = p; }
	/** "1. Rooms" when the window has all three steps (a design); the bare name when it hasn't (Remodel). */
	private String stepTitle(int n, String name) { return loadoutPanel != null ? n + ". " + name : name; }
	/** Brings a step to the front: "Rooms", "Art" or "Loadout". */
	public void showStep(String name) {
		for (int i = 0; i < steps.getTabCount(); i++) if (steps.getTitleAt(i).endsWith(name)) { steps.setSelectedIndex(i); return; }
	}
	/** The step in front. */
	public String stepShown() { String t = steps.getTitleAt(steps.getSelectedIndex()); return t.substring(t.lastIndexOf(' ') + 1); }
	/** A button along the bottom (Save, Finalize, Close). Add before {@link #buildUi}. */
	protected void addBottomButton(JButton b) { bottomButtons.add(b); }
	/** A button at the bottom left, after Help (Preview files). Add before {@link #buildUi}. */
	protected void addHelpRowButton(JButton b) { helpRow.add(b); }
	/** A long text in a window of its own (an export preview). */
	protected void showText(String title, String text) { showTextWindow(this, title, text); }
	/** A monospace text window (a files preview) beside any window. */
	static void showTextWindow(Window owner, String title, String text) {
		javax.swing.JTextArea ta = new javax.swing.JTextArea(text, 30, 90);
		ta.setEditable(false);
		ta.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
		ta.setCaretPosition(0);
		JScrollPane sp = new JScrollPane(ta);
		JDialog w = sideWindow(owner, title, sp);
		w.pack();
		w.setLocationRelativeTo(owner);
		w.setVisible(true);
	}
	private JDialog sideWindow(String title, JComponent content) { return sideWindow(this, title, content); }
	/** A modeless window beside the editor with a Close button (and Esc): the help, a preview. */
	private static JDialog sideWindow(Window owner, String title, JComponent content) {
		final JDialog w = new JDialog(owner, title, ModalityType.MODELESS);
		w.getContentPane().add(content, BorderLayout.CENTER);
		JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		row.add(button("Close", null, new ActionListener() { public void actionPerformed(ActionEvent e) { w.setVisible(false); } }));
		w.getContentPane().add(row, BorderLayout.SOUTH);
		w.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "close");
		w.getRootPane().getActionMap().put("close", new javax.swing.AbstractAction() { public void actionPerformed(ActionEvent e) { w.setVisible(false); } });
		return w;
	}
	protected static JButton button(String text, String tip, ActionListener a) {
		JButton b = new JButton(text);
		if (tip != null) b.setToolTipText(tip);
		b.addActionListener(a);
		return b;
	}

	/** Shows or hides the art tools (the overhaul brings them in). */
	protected void showArtPanel(boolean on) {
		if (on && artPanel == null) {
			artPanel = new ArtPanel(d, editor, this);
			steps.insertTab(stepTitle(2, "Art"), null, artPanel, "Her hull picture, floor, weapon mounts and shield", 1);
			artPanel.loadArt(false);
		} else if (!on && artPanel != null) {
			steps.remove(artPanel);
			artPanel = null;
			editor.onRestore = null;
		}
		steps.revalidate();
		refreshHelp();
	}

	/** Sizes the window to its contents (the view shows the ship, up to a design grid's worth), never bigger than the screen, over its owner. */
	protected void fitToScreen() {
		Dimension c = editor.canvas().getPreferredSize();
		canvasScroll.setPreferredSize(new Dimension(Math.max(520, Math.min(DesignDialog.COLS * LayoutEditor.SQ + 40, c.width + 4)),
				Math.max(400, Math.min(DesignDialog.ROWS * LayoutEditor.SQ + 40, c.height + 4))));
		pack();
		java.awt.Rectangle scr = getGraphicsConfiguration().getBounds();
		java.awt.Insets in = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
		setSize(Math.min(getWidth(), scr.width - in.left - in.right), Math.min(getHeight(), scr.height - in.top - in.bottom));
		setLocationRelativeTo(getOwner());
	}

	// ---- the help window: written from the tools that are on ----

	/** What the primary action is called ("saves the design", "finalizes the blueprint"): for the help. */
	protected abstract String primaryVerb();
	/** The help for the tools in use now. */
	protected String helpText() {
		boolean rooms = editor.roomsEditable(), art = artPanel != null, loadout = loadoutPanel != null;
		StringBuilder sb = new StringBuilder("<html><body style='margin:10px 14px'>");
		if (loadout) sb.append("<h3>The steps</h3><p>The tabs on the right are the steps, in build order: <b>Rooms</b>, <b>Art</b>, <b>Loadout</b>. The ship stays in view through all three; "
				+ "the line under her says what's left to fix and what comes next.</p>");
		if (rooms) sb.append("<h3>Rooms</h3><p>Pick a size on the right, then click the grid to place a room. <b>Move rooms</b> drags a room; right-click (or Delete) removes it. "
				+ "A room's own airlocks move with it; a door to another room stays where the wall is, and is lost if the wall goes.</p>");
		sb.append("<h3>Systems</h3><p>Click a system (in a room, or in the <i>Systems</i> list), then an empty room to put it there. "
				+ "Click a square in its own room for its station; click the station to turn it. Alt+click another system's room swaps the two. "
				+ "Right-click a system to take it off the ship.</p>");
		sb.append("<h3>Doors</h3><p><b>Add door</b>, then click walls: between two rooms for a door, on an outer wall for an airlock. "
				+ "Click a door, then a wall, to move it; right-click or Delete removes it. Esc stops.</p>");
		if (art) sb.append("<h3>Art</h3><p><b>Move art</b> drags the hull picture over the rooms (arrow keys nudge it a pixel; Shift: ten). The cyan cross is the anchor: where FTL puts her. "
				+ "Art centred on it looks centred in the game; art pushed off it sits that way in the game too. "
				+ "<b>Weapon mounts</b>: click the hull to place a mount, drag to move it, right-click to remove it. R turns the selected mount and S changes which way its weapon slides; the wheel turns it too. "
				+ "FTL draws weapon slot n on mount n, so give her as many mounts as slots. Alt + middle-drag moves the art whatever tool is on.</p>");
		if (loadout) sb.append("<h3>Loadout</h3><p>Who she is, her numbers, her starting crew, what she carries and which systems are installed at the start. "
				+ "Each number shows hers over the vanilla max (the most any of the game's ships has) with a bar to that ceiling; type what you like, the bar turns amber past it. "
				+ "Past the vanilla numbers FTL still takes her, but its bars and upgrade screen are drawn for the vanilla ones.</p>");
		sb.append("<h3>Keys</h3><p>Ctrl+Z and Ctrl+Y undo and redo. Esc deselects. Delete removes the selected door, mount or room. Ctrl+S " + primaryVerb() + ". F1 opens this help.</p>");
		sb.append("<h3>View</h3><p>The wheel zooms (Ctrl+plus, Ctrl+minus); middle-drag moves the view; Shift+wheel scrolls; Ctrl+0 (or Fit) shows the whole ship.</p>");
		sb.append("<h3>Checks</h3><p>The line under the editor says what would stop her working (<i>To fix</i>) and what's worth knowing (<i>Note</i>); it updates as you edit."
				+ (loadout ? " Notes about her loadout (drone parts, reactor power) are set right on the <b>Loadout</b> step." : "") + "</p>");
		return sb.append("</body></html>").toString();
	}
	/** Opens the help window (or refreshes it). */
	protected void showHelp() {
		if (helpWindow == null) {
			helpPane = new javax.swing.JEditorPane("text/html", "");
			helpPane.setEditable(false);
			helpPane.setBorder(null);
			helpPane.putClientProperty(javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE); // the window's font, not HTML's
			helpPane.setFont(MenuTheme.TEXT_FONT);
			JScrollPane sp = new JScrollPane(helpPane, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			sp.setPreferredSize(new Dimension(600, 560));
			sp.getVerticalScrollBar().setUnitIncrement(16);
			helpWindow = sideWindow(getTitle() + ": help", sp);
			helpWindow.pack();
			helpWindow.setLocationRelativeTo(this);
		}
		refreshHelp();
		helpWindow.setVisible(true);
		helpWindow.toFront();
	}
	/** Keeps an open help window in step with the tools (the overhaul brings the art tools in). */
	protected void refreshHelp() {
		if (helpPane == null) return;
		helpPane.setText(helpText());
		helpPane.setCaretPosition(0);
	}

	// ---- LayoutEditor.Host ----

	public void say(String s) { status.setText(s); }
	/** After any edit: the undo record, the art controls and the checks line. */
	public void changed() {
		if (editor == null) return;
		editor.commit();
		if (artPanel != null) artPanel.refreshArtControls();
		if (loadoutPanel != null) loadoutPanel.systemsChanged();
		refreshChecks();
	}
	/** The checks for this window's kind of editing. */
	protected abstract ShipChecks.Report check();
	/** What comes next when nothing needs fixing ("Next: the Art step"), or null. */
	protected String nextHint() { return null; }
	protected void refreshChecks() {
		ShipChecks.Report r = check();
		String counts = "Rooms " + d.rooms.size() + ", doors " + d.doors.size() + ".  ";
		String next = r.problems.isEmpty() && nextHint() != null ? "  " + nextHint() : "";
		if (r.problems.isEmpty() && r.warnings.isEmpty()) {
			checks.setText(wrapped(counts + "All good." + next));
			checks.setForeground(new Color(140, 220, 150));
		} else if (r.problems.isEmpty()) {
			checks.setText(wrapped(counts + "Note: " + String.join("  ", r.warnings) + next));
			checks.setForeground(new Color(230, 210, 120));
		} else {
			checks.setText(wrapped(counts + "To fix: " + String.join("  ", r.problems)));
			checks.setForeground(new Color(255, 170, 90));
		}
		checks.revalidate();
	}

	/** The checks line as HTML, so a long one wraps under the ship rather than running off the window. */
	private static String wrapped(String s) {
		return "<html>" + s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</html>";
	}

	// ---- leaving ----

	/** Save design / Finalize blueprint: what Ctrl+S does. */
	protected abstract void primaryAction();
	/** The primary action's name on the guard's button. */
	protected abstract String primaryName();
	/** Whether there are changes the player hasn't kept. */
	protected abstract boolean dirty();
	/** Closes, asking first when there are unsaved changes. */
	protected void closeGuarded() {
		if (dirty()) {
			Object[] opts = {primaryName(), "Discard", "Keep working"};
			int r = JOptionPane.showOptionDialog(this, "Keep the changes made here?", getTitle(), JOptionPane.DEFAULT_OPTION,
					JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
			if (r == 0) { primaryAction(); return; }
			if (r != 1) return;
		}
		dispose();
	}
}
