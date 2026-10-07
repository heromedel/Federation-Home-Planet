package homeplanet.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.SystemType;

import homeplanet.parser.CompanionMod;
import homeplanet.parser.CompanionMod.Sys;
import homeplanet.parser.ShipDesign;

/**
 * The layout editor shared by Remodel and Design Ship: the room grid (over the ship's art when she has some), systems
 * and their stations, doors and airlocks, and, when rooms may change (Design Ship), placing, moving and removing rooms.
 *
 * Systems: click one (in a room, or in the "Not on this ship" list), then an empty room to move it there; Alt+click another
 * system's room swaps the two; click a square in its own room for its station, click the station to turn it; right-click
 * takes a system off the ship. Doors: click one, then a wall to move it; right-click or Delete removes it; Add door places a
 * new one. Esc deselects.
 */
public class LayoutEditor {

	/** What the window using the editor decides. */
	public interface Host {
		void say(String message);
		/** After any edit. */
		void changed();
		/** Why this system can't come off the ship (Remodel: it's installed), or null if it can. */
		String cannotTakeOff(String id);
		/** Where the system was originally (Remodel: the model's own layout), or null. */
		Sys original(String id);
	}

	public static final int SQ = 35;
	private static final String[] DIRS = {"up", "right", "down", "left"};

	enum RoomTool { NONE, ROOM_22, ROOM_21, ROOM_12, MOVE, ART, MOUNTS }

	final ShipDesign d;
	private final Host host;
	private boolean roomsEditable;
	private final java.util.List<java.awt.Component> roomToolParts = new java.util.ArrayList<java.awt.Component>();
	private JPanel toolColumn; // the buttons under the list; the room tools go in and out of it (a GridLayout keeps room for hidden parts)
	private final int cols, rows;
	final Canvas canvas = new Canvas();
	private final DefaultListModel<String> offShip = new DefaultListModel<String>();
	private final JList<String> offList = new JList<String>(offShip);
	private final JPanel side = new JPanel(new BorderLayout(0, 4));
	private final JPanel extraButtons = new JPanel(new GridLayout(0, 1, 0, 4)); // the window's own buttons (Clear all, Overhaul...)
	private final Map<String, BufferedImage> icons = new HashMap<String, BufferedImage>();

	private String selected = null;             // system being placed / edited
	private CompanionMod.Door selDoor = null;   // door being moved
	private boolean addingDoor = false;
	private CompanionMod.Door hover = null;     // wall under the mouse while placing a door
	private RoomTool roomTool = RoomTool.NONE;
	private int selRoom = -1, dragDX, dragDY, hoverX = -1, hoverY = -1;
	private ShipDesign.Room dragFrom;                          // where the dragged room started
	private List<CompanionMod.Door> dragDoors;                 // the doors as they were then (a move through a bad spot mustn't lose them)
	private boolean dragging = false;
	// Design Ship's art: the design's own pictures, moved with the Move art tool; weapon mounts placed with the Mounts tool
	private boolean designArt = false;
	private ShipDesign.Mount selMount = null;
	private int artGrabX, artGrabY;
	static final int MARGIN = 4 * SQ;
	/** The design canvas's border round the grid: room for a big hull picture hanging over the grid's edge. */
	static final int PAD = 10 * SQ;

	// art under the rooms (optional): pictures and where they sit relative to the grid origin, in pixels
	BufferedImage baseImg, floorImg;
	int baseX, baseY, floorX, floorY;
	double scale = 1.0;                         // fits Remodel's art to the window
	double zoom = 1.0;                          // the player's zoom, on top of scale
	private int baseW, baseH;                   // the canvas's size before scaling
	int originX, originY;                       // where grid (0,0) lands on the canvas (unscaled)
	private boolean artDrag = false, nudging = false, panning = false;
	private java.awt.Point panFrom, panView;
	/** Called after undo/redo puts a snapshot back (the art panel reloads the pictures). */
	Runnable onRestore;
	// undo/redo: snapshots of what the editor changes; "last" is the state as of the latest commit
	private final java.util.ArrayDeque<ShipDesign> undoStack = new java.util.ArrayDeque<ShipDesign>(), redoStack = new java.util.ArrayDeque<ShipDesign>();
	private ShipDesign last;
	private String lastKey;
	private static final int HISTORY = 100;
	private final javax.swing.Timer nudgeDone = new javax.swing.Timer(500, new ActionListener() {
		public void actionPerformed(ActionEvent e) { nudging = false; commit(); }
	});
	private final Map<RoomTool, JToggleButton> toolButtons = new HashMap<RoomTool, JToggleButton>();
	private final ButtonGroup toolGroup = new ButtonGroup();

	/**
	 * @param d the rooms, doors and systems being edited (changed in place)
	 * @param roomsEditable whether rooms can be placed, moved and removed (Design Ship)
	 * @param cols, rows the grid's size when there's no art
	 */
	public LayoutEditor(ShipDesign d, Host host, boolean roomsEditable, int cols, int rows) {
		this.d = d;
		this.host = host;
		this.roomsEditable = roomsEditable;
		this.cols = cols;
		this.rows = rows;
		for (SystemType t : SystemType.values()) {
			BufferedImage i = image("img/icons/s_" + t.getId() + "_overlay.png");
			if (i != null) icons.put(t.getId(), i);
		}
		baseW = cols * SQ; baseH = rows * SQ;
		updateSize();
		nudgeDone.setRepeats(false);

		offList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		offList.setVisibleRowCount(8);
		offList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
			public void valueChanged(javax.swing.event.ListSelectionEvent e) {
				if (!e.getValueIsAdjusting() && offList.getSelectedValue() != null) {
					selected = idOf(offList.getSelectedValue());
					selDoor = null; addingDoor = false; setRoomTool(RoomTool.NONE);
					host.say("Click an empty room for the " + title(selected) + ".");
					canvas.repaint();
				}
			}
		});
		JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 4));
		JPanel view = new JPanel(new GridLayout(1, 3, 2, 0));
		{
			view.add(smallButton("\u2212", "Zoom out (Ctrl+minus, or the mouse wheel)", new ActionListener() { public void actionPerformed(ActionEvent e) { zoomBy(1 / 1.25, null); } }));
			view.add(smallButton("+", "Zoom in (Ctrl+plus, or the mouse wheel)", new ActionListener() { public void actionPerformed(ActionEvent e) { zoomBy(1.25, null); } }));
			view.add(smallButton("Fit", "Zoom to show the whole ship (Ctrl+0)", new ActionListener() { public void actionPerformed(ActionEvent e) { fitView(); } }));

			ButtonGroup g = toolGroup;
			roomToolParts.add(new JLabel("Rooms"));
			roomToolParts.add(toolButton(g, "Place 2 x 2", RoomTool.ROOM_22, "Click an empty spot to place a 2 x 2 room. Esc stops placing."));
			roomToolParts.add(toolButton(g, "Place 2 x 1", RoomTool.ROOM_21, "Click an empty spot to place a 2 x 1 room (wide). Esc stops placing."));
			roomToolParts.add(toolButton(g, "Place 1 x 2", RoomTool.ROOM_12, "Click an empty spot to place a 1 x 2 room (tall). Esc stops placing."));
			roomToolParts.add(toolButton(g, "Move rooms", RoomTool.MOVE, "Drag a room to move it; right-click a room to remove it. Esc stops."));
			roomToolParts.add(new JLabel("Whole ship"));
			JPanel shift = new JPanel(new GridLayout(1, 4, 2, 0));
			shift.add(smallButton("\u2190", "Move the whole ship (rooms, doors and art) one square left", new ActionListener() { public void actionPerformed(ActionEvent e) { shiftShip(-1, 0); } }));
			shift.add(smallButton("\u2191", "Move the whole ship one square up", new ActionListener() { public void actionPerformed(ActionEvent e) { shiftShip(0, -1); } }));
			shift.add(smallButton("\u2193", "Move the whole ship one square down", new ActionListener() { public void actionPerformed(ActionEvent e) { shiftShip(0, 1); } }));
			shift.add(smallButton("\u2192", "Move the whole ship one square right", new ActionListener() { public void actionPerformed(ActionEvent e) { shiftShip(1, 0); } }));
			roomToolParts.add(shift);
			roomToolParts.add(smallButton("Flip top / bottom", "Mirror the ship top to bottom: rooms, doors, stations, art and mounts (FTL's ships face right, so that's the one flip that keeps her flying forward)",
					new ActionListener() { public void actionPerformed(ActionEvent e) { flipShip(); } }));
			roomToolParts.add(new JLabel("Doors"));
			toolColumn = buttons;
			if (roomsEditable) for (java.awt.Component c : roomToolParts) buttons.add(c);
		}
		JButton addDoor = new JButton("Add door");
		addDoor.setToolTipText("Then click a wall between two rooms (or an outer wall, for an airlock)");
		addDoor.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				addingDoor = true; selDoor = null; selected = null; offList.clearSelection(); setRoomTool(RoomTool.NONE);
				host.say("Click walls to add doors (an outer wall makes an airlock). Esc or right-click stops.");
				canvas.repaint();
			}
		});
		buttons.add(addDoor);
		JPanel top = new JPanel(new BorderLayout(0, 2));
		JLabel systemsHead = new JLabel("Systems");
		systemsHead.setFont(MenuTheme.HEADING_FONT);
		systemsHead.setForeground(MenuTheme.GOLD);
		top.add(systemsHead, BorderLayout.NORTH);
		JLabel systemsHint = new JLabel("<html>Not on her yet. Click one, then an empty room to put it there.</html>");
		systemsHint.setFont(MenuTheme.TEXT_FONT);
		systemsHint.setForeground(MenuTheme.GREY_GREEN);
		top.add(systemsHint, BorderLayout.CENTER);
		side.add(top, BorderLayout.NORTH);
		JScrollPane sp = new JScrollPane(offList);
		sp.setPreferredSize(new Dimension(170, 150));
		side.add(sp, BorderLayout.CENTER);
		// the tools in the order they're used, the window's own buttons under them, and the view last as fine adjustment
		JPanel south = new JPanel(new BorderLayout(0, 4));
		south.add(buttons, BorderLayout.NORTH);
		south.add(extraButtons, BorderLayout.CENTER);
		JPanel fine = new JPanel(new GridLayout(0, 1, 0, 4));
		JLabel fineHead = new JLabel("View");
		fineHead.setForeground(MenuTheme.DIM);
		fine.add(fineHead);
		fine.add(view);
		south.add(fine, BorderLayout.SOUTH);
		side.add(south, BorderLayout.SOUTH);
		rebuildOffList();
	}

	/** The side column: the systems not on the ship, the room tools and Add door. Extra buttons go under it. */
	public JPanel side() { return side; }
	public void addSideButton(JButton b) {
		extraButtons.add(b);
	}
	public JComponent canvas() { return canvas; }

	/** Shows the ship's art under the rooms; positions are pixels from the grid origin. Sizes the canvas to the art. */
	public void setArt(BufferedImage base, int bx, int by, BufferedImage floor, int fx, int fy, int w, int h) {
		designArt = false;
		baseImg = base; baseX = bx; baseY = by;
		floorImg = floor; floorX = fx; floorY = fy;
		originX = -bx;
		originY = -by;
		scale = Math.min(1.0, Math.min(640.0 / w, 520.0 / h));
		baseW = w; baseH = h;
		updateSize();
	}

	/** Design Ship: the design's own art (moved by the Move art tool, mounts on it). Pictures may be null. */
	public void setDesignArt(BufferedImage base, BufferedImage floor) {
		designArt = true;
		baseImg = base;
		floorImg = floor;
		drawnFloorKey = null;
		scale = 1.0;
		relayoutDesign();
		rebuildOffList();
	}
	/** What the floor drawn from the rooms was last drawn for; it's drawn again when the rooms, the doors or the art move. */
	private String drawnFloorKey;
	private void drawnFloor() {
		if (baseImg == null) { floorImg = null; return; }
		StringBuilder k = new StringBuilder();
		k.append(d.artX).append(',').append(d.artY).append(',').append(baseImg.getWidth()).append('x').append(baseImg.getHeight());
		for (ShipDesign.Room r : d.rooms) k.append('|').append(r.x).append(',').append(r.y).append(',').append(r.w).append(',').append(r.h);
		for (homeplanet.parser.CompanionMod.Door x : d.doors) k.append('/').append(x.x).append(',').append(x.y).append(',').append(x.v);
		String key = k.toString();
		if (key.equals(drawnFloorKey)) return;
		floorImg = homeplanet.parser.ShipArt.floorFromRooms(d, baseImg.getWidth(), baseImg.getHeight());
		drawnFloorKey = key;
	}
	/** Sizes the design canvas around the rooms and the art (art hanging off the top or left moves the grid over). */
	/**
	 * The design canvas: the grid with a fixed border of {@link #PAD} all round for art that hangs over its edge. Nothing
	 * here moves when something is edited (heromedel): the anchor, the grid's middle, is where FTL puts the ship, and the
	 * rooms and the art sit where the player left them.
	 */
	private void relayoutDesign() {
		originX = PAD; originY = PAD;
		baseW = cols * SQ + 2 * PAD; baseH = rows * SQ + 2 * PAD;
		updateSize();
	}

	// ---- view: zoom, fit, centring ----

	double eff() { return scale * zoom; }
	private void updateSize() {
		canvas.setPreferredSize(new Dimension((int) (baseW * eff()) + 2, (int) (baseH * eff()) + 2));
		canvas.setSize(canvas.getPreferredSize());
		canvas.revalidate();
		canvas.repaint();
	}
	private javax.swing.JViewport viewport() {
		return canvas.getParent() instanceof javax.swing.JViewport ? (javax.swing.JViewport) canvas.getParent() : null;
	}
	private void scrollTo(int x, int y) {
		javax.swing.JViewport vp = viewport();
		if (vp == null) return;
		java.awt.Dimension ext = vp.getExtentSize(), size = canvas.getPreferredSize();
		x = Math.max(0, Math.min(x, size.width - ext.width));
		y = Math.max(0, Math.min(y, size.height - ext.height));
		vp.setViewPosition(new java.awt.Point(x, y));
	}
	/** Zooms by a factor, keeping the point under the mouse (canvas pixels; null: the middle of the view) where it is. */
	public void zoomBy(double f, java.awt.Point at) {
		double nz = Math.max(0.25, Math.min(4.0, zoom * f));
		if (Math.abs(nz - zoom) < 1e-6) return;
		javax.swing.JViewport vp = viewport();
		java.awt.Point pos = vp == null ? new java.awt.Point() : vp.getViewPosition();
		if (at == null) {
			java.awt.Dimension ext = vp == null ? canvas.getSize() : vp.getExtentSize();
			at = new java.awt.Point(pos.x + ext.width / 2, pos.y + ext.height / 2);
		}
		double ux = at.x / eff(), uy = at.y / eff();
		int offX = at.x - pos.x, offY = at.y - pos.y;
		zoom = nz;
		updateSize();
		scrollTo((int) (ux * eff()) - offX, (int) (uy * eff()) - offY);
		host.say("Zoom " + Math.round(zoom * 100) + "%.");
	}
	/** What there is to see, in unscaled canvas pixels: the rooms and the art. */
	private Rectangle content() {
		Rectangle r = null;
		for (ShipDesign.Room rm : d.rooms) r = union(r, roomRect(rm));
		if (baseImg != null) r = union(r, designArt ? new Rectangle(originX + d.artX, originY + d.artY, baseImg.getWidth(), baseImg.getHeight())
				: new Rectangle(originX + baseX, originY + baseY, baseImg.getWidth(), baseImg.getHeight()));
		if (designArt && baseImg != null) {
			int[] e = homeplanet.parser.ShipArt.ellipseOf(d, baseImg);
			if (e != null) {
				int cx = originX + d.artX + baseImg.getWidth() / 2 + e[2], cy = originY + d.artY + baseImg.getHeight() / 2 + e[3];
				r = union(r, new Rectangle(cx - e[0], cy - e[1], 2 * e[0], 2 * e[1]));
			}
		}
		return r == null ? new Rectangle(0, 0, baseW, baseH) : r;
	}
	private static Rectangle union(Rectangle a, Rectangle b) { return a == null ? new Rectangle(b) : a.union(b); }
	/** Zooms to show the whole ship and centres her. */
	public void fitView() {
		javax.swing.JViewport vp = viewport();
		Rectangle c = content();
		c.grow(SQ / 2, SQ / 2);
		if (vp != null && vp.getExtentSize().width > 0) {
			java.awt.Dimension ext = vp.getExtentSize();
			zoom = Math.max(0.25, Math.min(4.0, Math.min(ext.width / (c.width * scale), ext.height / (c.height * scale))));
			updateSize();
			scrollTo((int) (c.getCenterX() * eff()) - ext.width / 2, (int) (c.getCenterY() * eff()) - ext.height / 2);
		}
		host.say("Zoom " + Math.round(zoom * 100) + "%.");
	}
	/** Puts the picture's visible middle on the anchor (the grid's middle, where FTL puts her). Nothing else moves. */
	public boolean centerArt() {
		if (!designArt || baseImg == null) return false;
		double cx = cols * SQ / 2.0, cy = rows * SQ / 2.0;
		// the picture's visible part, not its box: a long nose or big engines leave the box's middle nowhere near the hull's
		Rectangle vis = homeplanet.parser.ShipArt.opaqueBounds(baseImg);
		d.artX = (int) Math.round(cx - (vis.x + vis.width / 2.0));
		d.artY = (int) Math.round(cy - (vis.y + vis.height / 2.0));
		canvas.repaint();
		host.changed();
		return true;
	}
	private static JButton smallButton(String t, String tip, ActionListener a) {
		JButton b = new JButton(t);
		b.setToolTipText(tip);
		b.setMargin(new java.awt.Insets(2, 4, 2, 4));
		b.addActionListener(a);
		return b;
	}

	// ---- undo / redo ----

	/** Records the design as it is now, if it changed since the last record (skipped mid-drag; the drop records it). */
	public void commit() {
		if (dragging || artDrag || nudging) return;
		String k = ShipDesign.editKey(d);
		if (k.equals(lastKey)) return;
		if (last != null) { undoStack.push(last); while (undoStack.size() > HISTORY) undoStack.removeLast(); }
		redoStack.clear();
		last = ShipDesign.copy(d);
		lastKey = k;
	}
	/** Starts the history over from the design as it is now (the window opened, or the kind of editing changed). */
	public void resetHistory() {
		undoStack.clear(); redoStack.clear();
		last = ShipDesign.copy(d);
		lastKey = ShipDesign.editKey(d);
	}
	public boolean undo() { return step(undoStack, redoStack, "Undone"); }
	public boolean redo() { return step(redoStack, undoStack, "Redone"); }
	private boolean step(java.util.ArrayDeque<ShipDesign> from, java.util.ArrayDeque<ShipDesign> to, String word) {
		nudgeDone.stop(); nudging = false; dragging = false; artDrag = false;
		commit();
		if (from.isEmpty()) { host.say("Nothing to " + word.toLowerCase().replace("one", "o") + "."); return false; }
		to.push(last);
		ShipDesign s = from.pop();
		ShipDesign.copyEditable(s, d);
		last = s;
		lastKey = ShipDesign.editKey(d);
		selected = null; selDoor = null; selMount = null; addingDoor = false; hover = null; selRoom = -1;
		offList.clearSelection();
		rebuildOffList();
		if (onRestore != null) onRestore.run();
		else if (designArt) relayoutDesign();
		canvas.repaint();
		host.changed();
		host.say(word + " (" + undoStack.size() + " more to undo, " + redoStack.size() + " to redo).");
		return true;
	}

	// ---- keys ----

	/**
	 * The editor's keys, in the window: Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z undo and redo, Delete, Esc, arrows nudge (Shift: 10 px),
	 * R turns the selected mount, S changes which way it slides, Ctrl+plus / minus / 0 zoom, and Ctrl+S (when save is given).
	 */
	public void installKeys(javax.swing.JRootPane root, final Runnable save) {
		javax.swing.InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		javax.swing.ActionMap am = root.getActionMap();
		int ctrl = java.awt.event.InputEvent.CTRL_DOWN_MASK, shift = java.awt.event.InputEvent.SHIFT_DOWN_MASK;
		bind(im, am, "undo", new int[][] {{java.awt.event.KeyEvent.VK_Z, ctrl}}, new Runnable() { public void run() { undo(); } }, false);
		bind(im, am, "redo", new int[][] {{java.awt.event.KeyEvent.VK_Y, ctrl}, {java.awt.event.KeyEvent.VK_Z, ctrl | shift}}, new Runnable() { public void run() { redo(); } }, false);
		bind(im, am, "delete", new int[][] {{java.awt.event.KeyEvent.VK_DELETE, 0}}, new Runnable() { public void run() { deleteSelected(); } }, true);
		bind(im, am, "esc", new int[][] {{java.awt.event.KeyEvent.VK_ESCAPE, 0}}, new Runnable() { public void run() { deselect(); host.say("Nothing selected."); } }, false);
		bind(im, am, "turn", new int[][] {{java.awt.event.KeyEvent.VK_R, 0}}, new Runnable() { public void run() { turnMount(1); } }, true);
		bind(im, am, "slide", new int[][] {{java.awt.event.KeyEvent.VK_S, 0}}, new Runnable() { public void run() { cycleSlide(); } }, true);
		bind(im, am, "zoomIn", new int[][] {{java.awt.event.KeyEvent.VK_EQUALS, ctrl}, {java.awt.event.KeyEvent.VK_PLUS, ctrl}, {java.awt.event.KeyEvent.VK_ADD, ctrl}},
				new Runnable() { public void run() { zoomBy(1.25, null); } }, false);
		bind(im, am, "zoomOut", new int[][] {{java.awt.event.KeyEvent.VK_MINUS, ctrl}, {java.awt.event.KeyEvent.VK_SUBTRACT, ctrl}},
				new Runnable() { public void run() { zoomBy(1 / 1.25, null); } }, false);
		bind(im, am, "fit", new int[][] {{java.awt.event.KeyEvent.VK_0, ctrl}, {java.awt.event.KeyEvent.VK_NUMPAD0, ctrl}}, new Runnable() { public void run() { fitView(); } }, false);
		if (save != null) bind(im, am, "save", new int[][] {{java.awt.event.KeyEvent.VK_S, ctrl}}, save, false);
		int[][] arrows = {{java.awt.event.KeyEvent.VK_LEFT, -1, 0}, {java.awt.event.KeyEvent.VK_RIGHT, 1, 0}, {java.awt.event.KeyEvent.VK_UP, 0, -1}, {java.awt.event.KeyEvent.VK_DOWN, 0, 1}};
		for (final int[] a : arrows) {
			bind(im, am, "nudge" + a[0], new int[][] {{a[0], 0}}, new Runnable() { public void run() { nudge(a[1], a[2]); } }, true);
			bind(im, am, "nudge10" + a[0], new int[][] {{a[0], shift}}, new Runnable() { public void run() { nudge(10 * a[1], 10 * a[2]); } }, true);
		}
	}
	/** A key for the whole window; plain keys are left alone while typing in a field, list or box. */
	private void bind(javax.swing.InputMap im, javax.swing.ActionMap am, String name, int[][] keys, final Runnable r, final boolean plain) {
		for (int[] k : keys) im.put(javax.swing.KeyStroke.getKeyStroke(k[0], k[1]), "le." + name);
		am.put("le." + name, new javax.swing.AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				if (plain && typing()) return;
				r.run();
			}
		});
	}
	private static boolean typing() {
		java.awt.Component f = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		if (f == null) return false;
		return f instanceof javax.swing.text.JTextComponent || f instanceof javax.swing.JList || f instanceof javax.swing.JComboBox
				|| SwingUtilities.getAncestorOfClass(javax.swing.JSpinner.class, f) != null || SwingUtilities.getAncestorOfClass(javax.swing.JComboBox.class, f) != null;
	}

	// ---- mounts ----

	/** The four ways a mount can face, in turning order: {rotate, mirror}. */
	private static final boolean[][] FACINGS = {{true, true}, {true, false}, {false, false}, {false, true}};
	public static String facing(ShipDesign.Mount m) {
		if (m.rotate) return m.mirror ? "forward, on the top edge" : "forward, on the bottom edge";
		return m.mirror ? "up, mirrored" : "up";
	}
	/** Turns the selected mount to the next (or previous) facing. */
	public boolean turnMount(int dir) {
		if (selMount == null || !d.mounts.contains(selMount)) { host.say("Select a mount first (Weapon mounts tool)."); return false; }
		int i = 0;
		for (int k = 0; k < FACINGS.length; k++) if (FACINGS[k][0] == selMount.rotate && FACINGS[k][1] == selMount.mirror) i = k;
		i = (i + (dir >= 0 ? 1 : FACINGS.length - 1)) % FACINGS.length;
		selMount.rotate = FACINGS[i][0];
		selMount.mirror = FACINGS[i][1];
		host.say("Mount " + (d.mounts.indexOf(selMount) + 1) + " points " + facing(selMount) + ".");
		canvas.repaint();
		host.changed();
		return true;
	}
	private static final String[] SLIDES = {"up", "right", "down", "left", "no"};
	/** Changes which way the selected mount's weapon slides out. */
	public boolean cycleSlide() {
		if (selMount == null || !d.mounts.contains(selMount)) { host.say("Select a mount first (Weapon mounts tool)."); return false; }
		int i = 0;
		for (int k = 0; k < SLIDES.length; k++) if (SLIDES[k].equals(selMount.slide)) i = k;
		selMount.slide = SLIDES[(i + 1) % SLIDES.length];
		host.say("Mount " + (d.mounts.indexOf(selMount) + 1) + (selMount.slide.equals("no") ? " doesn't slide." : " slides " + selMount.slide + "."));
		canvas.repaint();
		host.changed();
		return true;
	}
	/** The art tools: dragging the art, and placing weapon mounts on it. */
	public JToggleButton artToolButton(String text, final boolean mounts, String tip) {
		final RoomTool t = mounts ? RoomTool.MOUNTS : RoomTool.ART;
		JToggleButton b = toolButton(toolGroup, text, t, tip);
		return b;
	}
	public ShipDesign.Mount selectedMount() { return selMount; }
	private boolean placingArtilleryMount = false;
	boolean hasArtilleryMount() { for (ShipDesign.Mount m : d.mounts) if (m.artillery) return true; return false; }
	/** Artillery needs a mount for its gun: switch to placing it (or say why not yet). */
	public void startArtilleryMount() {
		if (!designArt || baseImg == null) { host.say("Artillery added. Once she has hull art, place the artillery gun's mount on it (Artillery mount)."); return; }
		setRoomTool(RoomTool.MOUNTS);
		toolButtons.get(RoomTool.MOUNTS).setSelected(true);
		placingArtilleryMount = true;
		selMount = null;
		host.say("Artillery needs a mount for its gun: click her art where the gun sits. Esc cancels.");
	}
	public void repaint() { canvas.repaint(); }

	private JToggleButton toolButton(ButtonGroup g, String text, final RoomTool t, String tip) {
		JToggleButton b = new JToggleButton(text);
		b.setToolTipText(tip);
		b.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setRoomTool(t);
				selected = null; selDoor = null; addingDoor = false; offList.clearSelection();
				host.say(((JToggleButton) e.getSource()).getToolTipText());
			}
		});
		g.add(b);
		toolButtons.put(t, b);
		return b;
	}
	private void setRoomTool(RoomTool t) {
		roomTool = t;
		if (t == RoomTool.NONE) {
			toolGroup.clearSelection();
		}
		selRoom = -1;
		canvas.repaint();
	}
	public boolean roomsEditable() { return roomsEditable; }
	/** A run of small edits (the art size spinner, say) that should undo as one step: the record is taken when the run ends. */
	public void burstEdit() { nudging = true; nudgeDone.restart(); }
	/** Remodel's overhaul: rooms become editable (their tools show). */
	public void setRoomsEditable(boolean on) {
		roomsEditable = on;
		if (on && roomToolParts.get(0).getParent() == null) { int at = 2; for (java.awt.Component c : roomToolParts) toolColumn.add(c, at++); } // after the View row
		else if (!on) for (java.awt.Component c : roomToolParts) toolColumn.remove(c);
		if (!on) setRoomTool(RoomTool.NONE);
		side.revalidate();
		canvas.repaint();
	}
	/** Removes a room unless it holds a system that can't come off; says why not. */
	private boolean removeRoom(int room) {
		for (String id : d.systemsIn(room)) {
			String why = host.cannotTakeOff(id);
			if (why != null) { host.say("Room " + room + " holds the " + title(id) + ": " + why); return false; }
		}
		d.removeRoom(room);
		return true;
	}

	/** Moves the whole ship a square; says so if she's at the grid's edge. */
	public void shiftShip(int dx, int dy) {
		if (!roomsEditable) return;
		if (!d.shift(dx, dy)) { host.say("She's at the edge of the grid already."); return; }
		host.say("Ship moved " + (dx < 0 ? "left" : dx > 0 ? "right" : dy < 0 ? "up" : "down") + " one square.");
		edited();
	}
	/** Mirrors the ship top to bottom, pictures included (copies of them go into the art folder). */
	public void flipShip() {
		if (!roomsEditable) return;
		if (d.rooms.isEmpty() && baseImg == null) { host.say("Nothing to flip yet."); return; }
		int h = designArt && baseImg != null ? baseImg.getHeight() : 0;
		try {
			if (designArt && baseImg != null) homeplanet.parser.ShipArt.flipVertically(d);
		} catch (Exception e) {
			host.say("The Home Planet Station couldn't flip her pictures: " + e.getMessage());
			return;
		}
		d.flipVertically(h);
		selMount = null;
		if (onRestore != null) onRestore.run(); // the art panel reloads the flipped pictures
		host.say("Ship flipped top to bottom." + (h > 0 ? " Her pictures are now copies of her own." : ""));
		edited();
	}
	/** Moves the selected mount earlier or later in the order (FTL's weapon slots follow it). */
	public boolean moveMount(int dir) {
		if (selMount == null || !d.mounts.contains(selMount)) { host.say("Select a mount first (Weapon mounts tool)."); return false; }
		int i = d.mounts.indexOf(selMount), j = i + (dir < 0 ? -1 : 1);
		if (j < 0 || j >= d.mounts.size()) { host.say("It's already the " + (dir < 0 ? "first" : "last") + " mount."); return false; }
		java.util.Collections.swap(d.mounts, i, j);
		host.say("Mount " + (i + 1) + " is now mount " + (j + 1) + ".");
		edited();
		return true;
	}

	/** Starts placing rooms of this size (Design Ship's starting tool). */
	public void startPlacing() { if (roomsEditable) { setRoomTool(RoomTool.ROOM_22); toolButtons.get(RoomTool.ROOM_22).setSelected(true); } }

	public void deselect() {
		selected = null; selDoor = null; addingDoor = false; hover = null; selMount = null; placingArtilleryMount = false;
		offList.clearSelection();
		setRoomTool(RoomTool.NONE);
		canvas.repaint();
	}
	/** Arrow keys: nudge the art (Move art) or the selected mount (Mounts) one pixel. */
	public boolean nudge(int dx, int dy) {
		boolean art = designArt && roomTool == RoomTool.ART && baseImg != null, mount = roomTool == RoomTool.MOUNTS && selMount != null;
		if (!art && !mount) return false;
		nudging = true;
		nudgeDone.restart(); // a run of nudges is one step to undo
		if (art) { d.artX += dx; d.artY += dy; }
		else { selMount.x += dx; selMount.y += dy; }
		canvas.repaint();
		host.changed();
		return true;
	}
	/** Delete key: the selected door (or, while moving rooms, the selected room). */
	public void deleteSelected() {
		if (selDoor != null) { d.doors.remove(selDoor); selDoor = null; host.say("Door removed."); edited(); }
		else if (roomTool == RoomTool.MOUNTS && selMount != null) { d.mounts.remove(selMount); selMount = null; host.say("Mount removed."); edited(); }
		else if (roomTool == RoomTool.MOVE && selRoom >= 0) { if (removeRoom(selRoom)) { host.say("Room " + selRoom + " removed."); selRoom = -1; edited(); } }
	}

	private void edited() {
		rebuildOffList();
		if (designArt) relayoutDesign(); // the canvas grows with the rooms
		canvas.repaint();
		host.changed();
	}
	/** Call after the host changes the layout itself (e.g. Restore original layout). */
	public void reset() {
		selected = null; selDoor = null; addingDoor = false; hover = null;
		rebuildOffList();
		canvas.repaint();
	}

	// ---- systems ----

	static boolean hasStation(String id) { return ShipDesign.manned(id); }
	static boolean isBay(String id) { return ShipDesign.bay(id); }
	static String title(String id) { return homeplanet.model.Items.systemTitle(id); }
	private String idOf(String title) {
		for (SystemType t : SystemType.values()) if (title(t.getId()).equals(title)) return t.getId();
		return title;
	}

	private void rebuildOffList() {
		offShip.clear();
		for (SystemType t : SystemType.values()) {
			String id = t.getId();
			if (id.equals("artillery") && !designArt) continue; // needs a weapon and a mount of its own: only where the art and mounts are edited
			if (!d.systems.containsKey(id)) offShip.addElement(title(id));
		}
	}

	private boolean roomFree(int room, String forId) {
		for (String o : d.systemsIn(room)) {
			if (o.equals(forId)) continue;
			if (isBay(forId) && isBay(o)) continue; // the two bays share a room
			return false;
		}
		return true;
	}

	private void place(String id, int room) {
		Sys s = d.systems.get(id);
		Sys orig = host.original(id);
		if (s == null) {
			s = new Sys(id);
			s.power = orig != null ? orig.power : CompanionMod.usualPower(id);
			d.systems.put(id, s);
		}
		s.room = room;
		if (hasStation(id)) {
			// back to the model's own station if it's the model's own room, else the first square facing the usual way
			if (orig != null && orig.room == room && orig.square != null) { s.square = orig.square; s.dir = orig.dir; }
			else { s.square = 0; s.dir = (orig != null && orig.dir != null) ? orig.dir : ShipDesign.defaultDir(id); }
		} else if (isBay(id)) {
			// the two bays share the kept-clear square
			Sys other = d.systems.get(id.equals("medbay") ? "clonebay" : "medbay");
			if (other != null && other.room == room && other.square != null) s.square = other.square;
			else if (orig != null && orig.room == room && orig.square != null) s.square = orig.square;
			else s.square = 0;
			s.dir = null;
		} else {
			s.square = null;
			s.dir = null;
		}
	}

	// ---- doors ----

	/**
	 * The wall segment nearest a canvas point (unscaled), as a door with its rooms filled in, or null if the point
	 * isn't on a wall. Rooms a/b are -2 when the wall isn't between two different rooms (or a room and space).
	 */
	private CompanionMod.Door wallAt(int px, int py) {
		double gx = px - originX, gy = py - originY;
		int vx = (int) Math.round(gx / SQ), vy = (int) Math.floor(gy / SQ);
		int hx = (int) Math.floor(gx / SQ), hy = (int) Math.round(gy / SQ);
		double dv = Math.abs(gx - vx * SQ) + Math.abs(gy - (vy * SQ + SQ / 2.0)) / 3;
		double dh = Math.abs(gy - hy * SQ) + Math.abs(gx - (hx * SQ + SQ / 2.0)) / 3;
		CompanionMod.Door w;
		int p, q;
		if (dv <= dh) {
			if (Math.abs(gx - vx * SQ) > 7) return null;
			p = d.roomAt(vx - 1, vy); q = d.roomAt(vx, vy);
			w = new CompanionMod.Door(vx, vy, 0, 0, 1);
		} else {
			if (Math.abs(gy - hy * SQ) > 7) return null;
			p = d.roomAt(hx, hy - 1); q = d.roomAt(hx, hy);
			w = new CompanionMod.Door(hx, hy, 0, 0, 0);
		}
		if (p == q) { w.a = -2; w.b = -2; }          // inside one room, or open space
		else if (p == -1) { w.a = q; w.b = -1; }    // airlock: the room comes first, as in FTL's files
		else if (q == -1) { w.a = p; w.b = -1; }
		else { w.a = Math.min(p, q); w.b = Math.max(p, q); } // the same order ShipDesign.doorFor uses
		return w;
	}
	private CompanionMod.Door doorOn(CompanionMod.Door wall) {
		if (wall == null) return null;
		for (CompanionMod.Door x : d.doors) if (x.sameWall(wall)) return x;
		return null;
	}
	private static boolean valid(CompanionMod.Door wall) { return wall != null && wall.a != -2; }
	private static String doorName(CompanionMod.Door x) { return x.b == -1 ? "airlock of room " + x.a : "door between rooms " + x.a + " and " + x.b; }

	// ---- geometry ----

	private Rectangle roomRect(ShipDesign.Room r) {
		return new Rectangle(originX + r.x * SQ, originY + r.y * SQ, r.w * SQ, r.h * SQ);
	}
	private Rectangle squareRect(ShipDesign.Room r, int square) {
		Rectangle rr = roomRect(r);
		return new Rectangle(rr.x + (square % r.w) * SQ, rr.y + (square / r.w) * SQ, SQ, SQ);
	}
	private int gridX(int x) { return (int) Math.floor((x - originX) / (double) SQ); }
	private int gridY(int y) { return (int) Math.floor((y - originY) / (double) SQ); }
	private int[] sizeOf(RoomTool t) {
		return t == RoomTool.ROOM_22 ? new int[] {2, 2} : t == RoomTool.ROOM_21 ? new int[] {2, 1} : t == RoomTool.ROOM_12 ? new int[] {1, 2} : null;
	}
	/** How far the grid reaches: the whole canvas past the origin (designs), or the art (Remodel). */
	private int gridCols() {
		if (designArt) return Math.max(cols, (baseW - originX) / SQ);
		return baseImg == null ? cols : Math.max(cols, (baseImg.getWidth() - originX) / SQ + 1);
	}
	private int gridRows() {
		if (designArt) return Math.max(rows, (baseH - originY) / SQ);
		return baseImg == null ? rows : Math.max(rows, (baseImg.getHeight() - originY) / SQ + 1);
	}

	// ---- clicks ----

	private void pressed(MouseEvent e) {
		int x = (int) (e.getX() / eff()), y = (int) (e.getY() / eff());
		int gx = gridX(x), gy = gridY(y);
		boolean right = SwingUtilities.isRightMouseButton(e);

		// middle button: drag the view around; Alt + middle button: drag the art, whatever the tool
		if (SwingUtilities.isMiddleMouseButton(e) && !e.isAltDown()) {
			javax.swing.JViewport vp = viewport();
			if (vp != null) { panning = true; panFrom = e.getLocationOnScreen(); panView = vp.getViewPosition(); }
			return;
		}
		if (SwingUtilities.isMiddleMouseButton(e)) {
			if (designArt && baseImg != null) {
				artDrag = true;
				artGrabX = x - originX - d.artX;
				artGrabY = y - originY - d.artY;
			} else host.say(designArt ? "Choose her hull art first." : "Her art moves only in an overhaul: Overhaul deck plan... unlocks it.");
			return;
		}

		// room tools (Design Ship)
		int[] size = sizeOf(roomTool);
		if (size != null) {
			if (right) { deselect(); host.say("Stopped placing rooms."); return; }
			if (d.fits(gx, gy, size[0], size[1], gridCols(), gridRows(), -1)) {
				d.rooms.add(new ShipDesign.Room(gx, gy, size[0], size[1]));
				d.refreshDoors();
				host.say("Room " + (d.rooms.size() - 1) + " placed.");
				edited();
			} else host.say("A " + size[0] + " x " + size[1] + " room doesn't fit there.");
			return;
		}
		if (roomTool == RoomTool.ART) {
			if (baseImg == null) { host.say("Choose her hull art first."); return; }
			dragging = true;
			artGrabX = x - originX - d.artX;
			artGrabY = y - originY - d.artY;
			host.say("Drag the art into place over the rooms. Arrow keys nudge it one pixel.");
			return;
		}
		if (roomTool == RoomTool.MOUNTS) {
			if (baseImg == null) { host.say("Choose her hull art first."); return; }
			int ax = x - originX - d.artX, ay = y - originY - d.artY;
			ShipDesign.Mount near = null;
			for (ShipDesign.Mount m : d.mounts) if (Math.abs(m.x - ax) <= 10 && Math.abs(m.y - ay) <= 10) near = m;
			if (right) {
				if (near != null) { d.mounts.remove(near); if (near == selMount) selMount = null; host.say("Mount removed."); edited(); }
				return;
			}
			if (near != null) {
				selMount = near;
				dragging = true;
				host.say("Mount " + (d.mounts.indexOf(near) + 1) + " selected: drag to move it; set its facing on the right. Right-click removes it.");
			} else {
				ShipDesign.Mount m = new ShipDesign.Mount(ax, ay);
				m.slide = ay < baseImg.getHeight() / 2 ? "up" : "down";
				m.mirror = ay < baseImg.getHeight() / 2;
				d.mounts.add(m);
				selMount = m;
				dragging = true;
				if (placingArtilleryMount) {
					m.artillery = true;
					m.rotate = false; // the Federation Cruiser's points up (as her file has it)
					m.slide = "no";
					placingArtilleryMount = false;
					host.say("Artillery mount placed. Drag to adjust; the Weapon mounts tool carries on with ordinary mounts.");
				} else host.say("Mount " + d.mounts.size() + " placed. Drag to adjust; set its facing on the right.");
			}
			edited();
			return;
		}
		if (roomTool == RoomTool.MOVE) {
			int room = d.roomAt(gx, gy);
			if (right) {
				if (room >= 0 && removeRoom(room)) { selRoom = -1; host.say("Room " + room + " removed (the rooms after it are renumbered)."); edited(); }
				return;
			}
			selRoom = room;
			if (room >= 0) {
				dragging = true;
				ShipDesign.Room r = d.rooms.get(room);
				dragDX = gx - r.x;
				dragDY = gy - r.y;
				dragFrom = new ShipDesign.Room(r.x, r.y, r.w, r.h);
				dragDoors = new ArrayList<CompanionMod.Door>();
				for (CompanionMod.Door dr : d.doors) dragDoors.add(new CompanionMod.Door(dr.x, dr.y, dr.a, dr.b, dr.v));
				host.say("Room " + room + ": drag to move it. Right-click or Delete removes it.");
			}
			canvas.repaint();
			return;
		}

		CompanionMod.Door wall = wallAt(x, y);
		CompanionMod.Door onWall = doorOn(wall);
		if (addingDoor) {
			if (right) { addingDoor = false; hover = null; host.say("Stopped adding doors."); canvas.repaint(); return; }
			if (!valid(wall)) { host.say("That isn't a wall between two rooms or an outer wall. Esc or right-click stops adding doors."); return; }
			if (onWall != null) { host.say("There's already a door there."); return; }
			d.doors.add(wall);
			selDoor = null;
			host.say("Added the " + doorName(wall) + ". Click another wall for the next door; Esc or right-click stops.");
			edited();
			return;
		}
		if (onWall != null) {
			if (right) {
				d.doors.remove(onWall);
				if (onWall == selDoor) selDoor = null;
				host.say("Removed the " + doorName(onWall) + ".");
				edited();
			} else {
				selDoor = onWall; selected = null; offList.clearSelection();
				host.say("Selected the " + doorName(onWall) + ". Click a wall to move it, Delete to remove it.");
				canvas.repaint();
			}
			return;
		}
		if (selDoor != null && wall != null) {
			if (!valid(wall)) { host.say("That isn't a wall between two rooms or an outer wall."); return; }
			d.doors.set(d.doors.indexOf(selDoor), wall);
			selDoor = null; hover = null;
			host.say("Moved it: now the " + doorName(wall) + ".");
			edited();
			return;
		}
		int room = d.roomAt(gx, gy);
		if (room < 0) {
			deselect();
			host.say("Nothing selected.");
			return;
		}
		selDoor = null;
		ShipDesign.Room r = d.rooms.get(room);
		int square = (gy - r.y) * r.w + (gx - r.x);
		List<String> here = d.systemsIn(room);
		if (right) {
			if (here.isEmpty()) return;
			String id = here.size() == 2 ? pickBay(here) : here.get(0);
			if (id == null) return;
			String why = host.cannotTakeOff(id);
			if (why != null) {
				host.say(why);
			} else {
				d.removeSystem(id);
				if (id.equals(selected)) selected = null;
				host.say(title(id) + " taken off the ship.");
				edited();
			}
			return;
		}
		if (selected != null && here.contains(selected)) {
			// same room: station work
			Sys s = d.systems.get(selected);
			if (hasStation(selected) || isBay(selected)) {
				if (s.square != null && s.square == square) {
					if (s.dir != null) { s.dir = DIRS[(indexOf(s.dir) + 1) % 4]; host.say(title(selected) + " station now faces " + s.dir + "."); }
				} else {
					s.square = square;
					if (isBay(selected)) {
						Sys other = d.systems.get(selected.equals("medbay") ? "clonebay" : "medbay");
						if (other != null && other.room == room) other.square = square;
					}
					host.say(title(selected) + (isBay(selected) ? " keeps square " + square + " clear." : " station moved to square " + square + "."));
				}
				edited();
			}
			return;
		}
		if (selected != null && e.isAltDown() && !here.isEmpty() && !here.contains(selected)) {
			Sys mine = d.systems.get(selected);
			if (mine == null) { host.say("Put the " + title(selected) + " in an empty room first; a system from the list has no room to swap."); return; }
			String other = here.size() == 2 ? pickBay(here) : here.get(0);
			if (other == null) return;
			int from = mine.room;
			place(selected, room);
			place(other, from);
			host.say(title(selected) + " and " + title(other) + " swapped rooms (" + from + " and " + room + ").");
			edited();
			return;
		}
		if (selected != null && roomFree(room, selected) && "artillery".equals(selected) && !d.systems.containsKey("artillery")) {
			// new artillery: which weapon it fires, then its gun's mount on the art
			String w = ArtilleryPicker.choose(canvas, null);
			if (w == null) { host.say("No artillery placed."); return; }
			place(selected, room);
			d.systems.get("artillery").weapon = w;
			offList.clearSelection();
			selected = null;
			edited();
			if (!hasArtilleryMount()) startArtilleryMount();
			else host.say("Artillery (" + ArtilleryPicker.label(w) + ") added to room " + room + ".");
			return;
		}
		if (selected != null && roomFree(room, selected)) {
			Sys before = d.systems.get(selected);
			int from = before == null ? -1 : before.room;
			place(selected, room);
			host.say(title(selected) + (from < 0 ? " added to room " + room + "." : " moved from room " + from + " to room " + room + ".")
					+ (hasStation(selected) ? " Click a square to move its station." : ""));
			offList.clearSelection();
			edited();
			return;
		}
		if (!here.isEmpty()) {
			selected = here.size() == 2 ? pickBay(here) : here.get(0);
			if (selected != null) host.say(title(selected) + " selected. Click an empty room to move it" + (hasStation(selected) ? ", or a square in this room for its station." : "."));
			offList.clearSelection();
		} else if (selected != null) {
			host.say("Room " + room + " is taken.");
		} else {
			host.say("Room " + room + " is empty. Pick a system from the list, then click it.");
		}
		canvas.repaint();
	}

	private void dragged(MouseEvent e) {
		if (panning) {
			java.awt.Point p = e.getLocationOnScreen();
			scrollTo(panView.x - (p.x - panFrom.x), panView.y - (p.y - panFrom.y));
			return;
		}
		int x = (int) (e.getX() / eff()), y = (int) (e.getY() / eff());
		if (artDrag) {
			d.artX = x - originX - artGrabX;
			d.artY = y - originY - artGrabY;
			canvas.repaint();
			host.changed();
			return;
		}
		if (roomTool == RoomTool.ART && dragging) {
			d.artX = x - originX - artGrabX;
			d.artY = y - originY - artGrabY;
			canvas.repaint();
			host.changed();
			return;
		}
		if (roomTool == RoomTool.MOUNTS && dragging && selMount != null) {
			selMount.x = x - originX - d.artX;
			selMount.y = y - originY - d.artY;
			canvas.repaint();
			host.changed();
			return;
		}
		if (roomTool != RoomTool.MOVE || !dragging || selRoom < 0) return;
		ShipDesign.Room r = d.rooms.get(selRoom);
		int nx = gridX(x) - dragDX, ny = gridY(y) - dragDY;
		if ((nx != r.x || ny != r.y) && d.fits(nx, ny, r.w, r.h, gridCols(), gridRows(), selRoom)) {
			// always move from where the drag started, with the doors it started with: a door lost on the way back comes back
			if (dragFrom != null) {
				r.x = dragFrom.x; r.y = dragFrom.y;
				d.doors.clear();
				for (CompanionMod.Door dr : dragDoors) d.doors.add(new CompanionMod.Door(dr.x, dr.y, dr.a, dr.b, dr.v));
			}
			d.moveRoom(selRoom, nx, ny);
			edited();
		}
	}

	private String pickBay(List<String> two) {
		Object[] options = {title(two.get(0)), title(two.get(1)), "Cancel"};
		int r = JOptionPane.showOptionDialog(canvas, "Which one?", "Systems", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		return r == 0 ? two.get(0) : r == 1 ? two.get(1) : null;
	}
	private static int indexOf(String dir) {
		for (int i = 0; i < DIRS.length; i++) if (DIRS[i].equals(dir)) return i;
		return 0;
	}

	/** Wheel: zoom; with a mount selected (Weapon mounts), turn it instead. Ctrl+wheel always zooms; Shift+wheel scrolls. */
	private void wheel(java.awt.event.MouseWheelEvent e) {
		int n = e.getWheelRotation();
		if (n == 0) return;
		if (e.isShiftDown()) {
			javax.swing.JScrollPane sp = (javax.swing.JScrollPane) SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, canvas);
			if (sp != null) { javax.swing.JScrollBar b = sp.getVerticalScrollBar(); b.setValue(b.getValue() + n * 40); }
			return;
		}
		if (!e.isControlDown() && roomTool == RoomTool.MOUNTS && selMount != null && d.mounts.contains(selMount)) { turnMount(n > 0 ? 1 : -1); return; }
		zoomBy(n < 0 ? 1.25 : 1 / 1.25, e.getPoint());
	}

	// ---- drawing ----

	class Canvas extends JPanel {
		Canvas() {
			setBackground(new Color(20, 24, 30));
			setFocusable(true);
			MouseAdapter m = new MouseAdapter() {
				public void mousePressed(MouseEvent e) { requestFocusInWindow(); LayoutEditor.this.pressed(e); } // keys go to the editor, not a text field
				public void mouseReleased(MouseEvent e) {
					boolean art = artDrag || (dragging && roomTool == RoomTool.ART);
					dragging = false; artDrag = false; panning = false;
					if (art) relayoutDesign();
					commit();
				}
				public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) { LayoutEditor.this.wheel(e); }
				public void mouseDragged(MouseEvent e) { LayoutEditor.this.dragged(e); }
				public void mouseMoved(MouseEvent e) {
					int x = (int) (e.getX() / eff()), y = (int) (e.getY() / eff());
					if (sizeOf(roomTool) != null) {
						int hx = gridX(x), hy = gridY(y);
						if (hx != hoverX || hy != hoverY) { hoverX = hx; hoverY = hy; repaint(); }
						return;
					}
					if (!addingDoor && selDoor == null) { if (hover != null) { hover = null; repaint(); } return; }
					CompanionMod.Door w = wallAt(x, y);
					if (w == null ? hover != null : !w.equals(hover) || w.a != (hover == null ? 0 : hover.a)) { hover = w; repaint(); }
				}
				public void mouseExited(MouseEvent e) { hoverX = hoverY = -1; repaint(); }
			};
			addMouseListener(m);
			addMouseMotionListener(m);
			addMouseWheelListener(m);
		}
		protected void paintComponent(Graphics g0) {
			super.paintComponent(g0);
			Graphics2D g = (Graphics2D) g0.create();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.scale(eff(), eff());
			if (designArt) {
				if (baseImg != null) g.drawImage(baseImg, originX + d.artX, originY + d.artY, null);
				if (d.floorFromRooms()) drawnFloor();
				if (floorImg != null) g.drawImage(floorImg, originX + d.artX + d.floorX, originY + d.artY + d.floorY, null);
			} else {
				if (baseImg != null) g.drawImage(baseImg, originX + baseX, originY + baseY, null);
				if (floorImg != null) g.drawImage(floorImg, originX + floorX, originY + floorY, null);
			}
			if (roomsEditable) {
				// the grid the rooms snap to
				g.setColor(new Color(255, 255, 255, 22));
				int c = gridCols(), rr = gridRows();
				for (int i = 0; i <= c; i++) g.drawLine(originX + i * SQ, originY, originX + i * SQ, originY + rr * SQ);
				for (int i = 0; i <= rr; i++) g.drawLine(originX, originY + i * SQ, originX + c * SQ, originY + i * SQ);
			}
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
			paintRooms(g);
			// doors: short bars across the walls
			g.setStroke(new BasicStroke(4f));
			for (CompanionMod.Door x : d.doors) {
				g.setColor(x == selDoor ? new Color(90, 220, 255) : x.b < 0 ? new Color(150, 210, 255) : new Color(255, 230, 150));
				drawDoor(g, x);
			}
			if (hover != null && (addingDoor || selDoor != null)) {
				g.setColor(valid(hover) && doorOn(hover) == null ? new Color(120, 255, 120, 200) : new Color(255, 90, 90, 200));
				drawDoor(g, hover);
			}
			g.setStroke(new BasicStroke(1f));
			if (designArt) paintArtExtras(g);
			if (designArt) { // the anchor: the grid's middle is where FTL puts the ship (DesignExport.SHIP_X / SHIP_Y)
				int cx = originX + cols * SQ / 2, cy = originY + rows * SQ / 2;
				// FTL has no further left or up than offset 0: rooms in this strip sit at the strip's edge in the game
				g.setColor(new Color(255, 120, 80, 42));
				g.fillRect(originX, originY, homeplanet.parser.DesignExport.ORIGIN_COL * SQ, rows * SQ);
				g.fillRect(originX, originY, cols * SQ, homeplanet.parser.DesignExport.ORIGIN_ROW * SQ);
				g.setColor(new Color(90, 220, 255, 230));
				g.setStroke(new BasicStroke(2f));
				g.drawLine(cx - 9, cy, cx + 9, cy); g.drawLine(cx, cy - 9, cx, cy + 9);
				g.drawOval(cx - 5, cy - 5, 10, 10);
				g.setStroke(new BasicStroke(1f));
				g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
				g.drawString("where FTL puts her", cx + 12, cy - 4);
			}
			// what to do first, written on the empty grid
			String hint = roomsEditable && d.rooms.isEmpty() ? "Place her first room: Place 2 x 2, then click the grid. The cross is where FTL puts her."
					: designArt && baseImg == null && !d.rooms.isEmpty() ? "She needs hull art: the Art step, Import PNG or From the game." : null;
			if (hint != null) {
				g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
				int tw = g.getFontMetrics().stringWidth(hint);
				int hx = originX + cols * SQ / 2 - tw / 2, hy = originY + (d.rooms.isEmpty() ? rows * SQ / 2 + 2 * SQ : -SQ / 2);
				g.setColor(new Color(0, 0, 0, 150));
				g.fillRoundRect(hx - 10, hy - 16, tw + 20, 24, 8, 8);
				g.setColor(new Color(230, 236, 232));
				g.drawString(hint, hx, hy);
			}
			int[] size = sizeOf(roomTool);
			if (size != null && hoverX != -1) {
				boolean ok = d.fits(hoverX, hoverY, size[0], size[1], gridCols(), gridRows(), -1);
				g.setColor(ok ? new Color(120, 255, 120, 80) : new Color(255, 90, 90, 80));
				g.fillRect(originX + hoverX * SQ, originY + hoverY * SQ, size[0] * SQ, size[1] * SQ);
			}
			g.dispose();
		}
		private void paintRooms(Graphics2D g) {
			for (int i = 0; i < d.rooms.size(); i++) {
				ShipDesign.Room r = d.rooms.get(i);
				Rectangle rr = roomRect(r);
				List<String> here = d.systemsIn(i);
				boolean sel = (selected != null && here.contains(selected)) || i == selRoom;
				g.setColor(sel ? new Color(196, 184, 120) : new Color(150, 154, 160)); // FTL paints room floors flat grey
				g.fillRect(rr.x, rr.y, rr.width, rr.height);
				g.setColor(new Color(128, 132, 138));
				for (int sx = 1; sx < r.w; sx++) g.drawLine(rr.x + sx * SQ, rr.y, rr.x + sx * SQ, rr.y + rr.height);
				for (int sy = 1; sy < r.h; sy++) g.drawLine(rr.x, rr.y + sy * SQ, rr.x + rr.width, rr.y + sy * SQ);
				g.setColor(sel ? new Color(255, 220, 90) : new Color(200, 210, 220));
				g.setStroke(new BasicStroke(sel ? 2f : 1f));
				g.drawRect(rr.x, rr.y, rr.width - 1, rr.height - 1);
				g.setColor(new Color(220, 220, 220));
				g.drawString(String.valueOf(i), rr.x + 3, rr.y + 11);
				// station / kept-clear square
				for (String id : here) {
					Sys s = d.systems.get(id);
					if (!hasStation(id) && !isBay(id)) continue; // unmanned: no station to show
					if (s.square == null || s.square < 0 || s.square >= r.w * r.h) continue;
					Rectangle sq = squareRect(r, s.square);
					g.setColor(isBay(id) ? new Color(120, 200, 255, 90) : new Color(255, 150, 60, 110));
					g.fillRect(sq.x + 2, sq.y + 2, sq.width - 4, sq.height - 4);
					if (s.dir != null) drawArrow(g, sq, s.dir);
				}
				// icon(s), centred (two bays: side by side)
				int n = here.size();
				for (int k = 0; k < n; k++) {
					BufferedImage ic = icons.get(here.get(k));
					int cx = rr.x + rr.width / 2 + (n == 2 ? (k == 0 ? -12 : 12) : 0), cy = rr.y + rr.height / 2;
					if (ic != null) g.drawImage(ic, cx - ic.getWidth() / 2, cy - ic.getHeight() / 2, null);
					else { g.setColor(Color.WHITE); g.drawString(here.get(k), cx - 10, cy); }
				}
			}
		}
	}

	/** A weapon mount's number among the weapon mounts (artillery mounts aren't counted). */
	private int weaponNumber(ShipDesign.Mount m) {
		int n = 0;
		for (ShipDesign.Mount x : d.mounts) { if (!x.artillery) n++; if (x == m) return n; }
		return n;
	}
	/** Design Ship: the shield ellipse (dashed) and the weapon mounts. */
	private void paintArtExtras(Graphics2D g) {
		if (baseImg != null) {
			int[] e = homeplanet.parser.ShipArt.ellipseOf(d, baseImg);
			if (e != null) {
				double cx = originX + d.artX + baseImg.getWidth() / 2.0 + e[2], cy = originY + d.artY + baseImg.getHeight() / 2.0 + e[3];
				g.setColor(new Color(90, 180, 255, 150));
				g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {8f, 6f}, 0f));
				g.draw(new java.awt.geom.Ellipse2D.Double(cx - e[0], cy - e[1], 2 * e[0], 2 * e[1]));
				g.setStroke(new BasicStroke(1f));
			}
		}
		for (int i = 0; i < d.mounts.size(); i++) {
			ShipDesign.Mount m = d.mounts.get(i);
			int mx = originX + d.artX + m.x, my = originY + d.artY + m.y;
			boolean sel = m == selMount;
			drawGun(g, m, mx, my, sel);
			g.setColor(m.artillery ? new Color(140, 200, 255) : Color.white);
			g.drawString(m.artillery ? "A" : String.valueOf(weaponNumber(m)), mx + 8, my - 8);
		}
	}

	/**
	 * A sketch of a weapon on its mount: the barrel points the way the mount faces (rotate: forward, else up), the body
	 * sits to the side mirror picks, and an arrow shows which way it slides out. Not the game's weapon art.
	 */
	private static void drawGun(Graphics2D g, ShipDesign.Mount m, int mx, int my, boolean sel) {
		// facing (fx, fy) and the side the body sits on (sx, sy)
		int fx = m.rotate ? 1 : 0, fy = m.rotate ? 0 : -1;
		int sx = m.rotate ? 0 : (m.mirror ? 1 : -1), sy = m.rotate ? (m.mirror ? -1 : 1) : 0;
		Color body = sel ? new Color(255, 220, 90) : m.artillery ? new Color(110, 170, 255) : new Color(255, 120, 80);
		// body: a box beside the mount point, barrel from the mount along the facing
		int bw = fx != 0 ? 18 : 9, bh = fx != 0 ? 9 : 18;
		int bx = mx + sx * 4 - (fx != 0 ? 4 : bw / 2) + (sx < 0 ? -4 : 0), by = my + sy * 4 - (fy != 0 ? bh - 4 : bh / 2) + (sy < 0 ? -4 : 0);
		g.setColor(body);
		g.fillRect(bx, by, bw, bh);
		g.setColor(Color.black);
		g.drawRect(bx, by, bw, bh);
		g.setStroke(new BasicStroke(3f));
		g.setColor(new Color(235, 235, 225));
		int ex = mx + fx * 28, ey = my + fy * 28;
		g.drawLine(mx, my, ex, ey);
		g.setStroke(new BasicStroke(1f));
		// the mount point itself
		g.setColor(Color.white);
		g.fillOval(mx - 3, my - 3, 6, 6);
		g.setColor(Color.black);
		g.drawOval(mx - 3, my - 3, 6, 6);
		// slide: a small arrow off the body
		int ax = 0, ay = 0;
		if ("up".equals(m.slide)) ay = -1; else if ("down".equals(m.slide)) ay = 1; else if ("left".equals(m.slide)) ax = -1; else if ("right".equals(m.slide)) ax = 1;
		if (ax != 0 || ay != 0) {
			int cx = bx + bw / 2, cy = by + bh / 2;
			int tx = cx + ax * 22, ty = cy + ay * 22;
			g.setColor(new Color(120, 220, 255));
			g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {3f, 3f}, 0f));
			g.drawLine(cx, cy, tx, ty);
			g.setStroke(new BasicStroke(1f));
			int[] xs = {tx + ax * 5, tx - ay * 4, tx + ay * 4}, ys = {ty + ay * 5, ty + ax * 4, ty - ax * 4};
			g.fillPolygon(xs, ys, 3);
		}
	}

	private void drawDoor(Graphics2D g, CompanionMod.Door x) {
		int dx = originX + x.x * SQ + (x.v == 1 ? 0 : SQ / 2);
		int dy = originY + x.y * SQ + (x.v == 1 ? SQ / 2 : 0);
		if (x.v == 1) g.drawLine(dx, dy - 9, dx, dy + 9); else g.drawLine(dx - 9, dy, dx + 9, dy);
	}

	private static void drawArrow(Graphics2D g, Rectangle sq, String dir) {
		int cx = sq.x + sq.width / 2, cy = sq.y + sq.height / 2, a = 6;
		int[] xs, ys;
		if (dir.equals("up")) { xs = new int[] {cx, cx - a, cx + a}; ys = new int[] {sq.y + 3, sq.y + 3 + a, sq.y + 3 + a}; }
		else if (dir.equals("down")) { xs = new int[] {cx, cx - a, cx + a}; ys = new int[] {sq.y + sq.height - 3, sq.y + sq.height - 3 - a, sq.y + sq.height - 3 - a}; }
		else if (dir.equals("left")) { xs = new int[] {sq.x + 3, sq.x + 3 + a, sq.x + 3 + a}; ys = new int[] {cy, cy - a, cy + a}; }
		else { xs = new int[] {sq.x + sq.width - 3, sq.x + sq.width - 3 - a, sq.x + sq.width - 3 - a}; ys = new int[] {cy, cy - a, cy + a}; }
		g.setColor(new Color(255, 240, 200));
		g.fillPolygon(xs, ys, 3);
	}

	static BufferedImage image(String path) {
		InputStream in = null;
		try {
			in = DataManager.get().getResourceInputStream(path);
			return ImageIO.read(in);
		} catch (Exception e) {
			return null;
		} finally {
			try { if (in != null) in.close(); } catch (Exception e) { }
		}
	}
}
