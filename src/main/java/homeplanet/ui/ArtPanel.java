package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;

import homeplanet.parser.ShipArt;
import homeplanet.parser.ShipDesign;

/**
 * The art tools shared by Design Ship and Remodel's overhaul: hull and floor art (her own PNG or the game's), lining the
 * art up with the rooms, weapon mounts, the shield ellipse and the gibs.
 */
public class ArtPanel extends JPanel {
	private final ShipDesign d;
	private final LayoutEditor editor;
	private final LayoutEditor.Host host;

	public ArtPanel(ShipDesign d, LayoutEditor editor, LayoutEditor.Host host) {
		this.d = d;
		this.editor = editor;
		this.host = host;
		build();
		editor.onRestore = new Runnable() { public void run() { loadArt(false); } };
	}


	private BufferedImage baseArt, floorArt;
	private final JLabel artName = new JLabel("none yet"), floorName = new JLabel("none");
	private final JLabel mountFacing = new JLabel(" ");
	private final JButton mountTurn = new JButton("Turn (R)");
	private JButton centerBtn, artilleryBtn, mountEarlier, mountLater;
	private final JRadioButton floorNone = new JRadioButton("No floor"), floorRooms = new JRadioButton("Drawn from her rooms"),
			floorFile = new JRadioButton("A picture of my own...");
	private final JComboBox<String> mountSlide = new JComboBox<String>(new String[] {"up", "down", "left", "right", "no"});
	private final JLabel mountLabel = new JLabel("No mount selected");
	private final JSpinner ellW = new JSpinner(new SpinnerNumberModel(0, 0, 2000, 2)), ellH = new JSpinner(new SpinnerNumberModel(0, 0, 2000, 2)),
			ellX = new JSpinner(new SpinnerNumberModel(0, -1000, 1000, 2)), ellY = new JSpinner(new SpinnerNumberModel(0, -1000, 1000, 2));
	private final JRadioButton gibGame = new JRadioButton("The game ship's own gibs"), gibAuto = new JRadioButton("Cut from the hull art"),
			gibFiles = new JRadioButton("My gib pictures...");
	private boolean refreshing = false;
	private final JSpinner artSize = new JSpinner(new SpinnerNumberModel(100, 25, 400, 1));

	// what scales with the art (mounts, the floor's offset, the shield ellipse), as it was at refScale: every resize
	// works from these, so stepping the size up and down doesn't round the positions away a pixel at a time
	private int[] refVals;
	private int refScale;
	private int[] scaledValues() {
		int[] v = new int[d.mounts.size() * 2 + 6];
		int i = 0;
		for (ShipDesign.Mount m : d.mounts) { v[i++] = m.x; v[i++] = m.y; }
		v[i++] = d.floorX; v[i++] = d.floorY; v[i++] = d.ellipseW; v[i++] = d.ellipseH; v[i++] = d.ellipseX; v[i++] = d.ellipseY;
		return v;
	}
	private static int[] at(int[] ref, int refScale, int percent) {
		int[] v = new int[ref.length];
		for (int i = 0; i < ref.length; i++) v[i] = (int) Math.round(ref[i] * (double) percent / refScale);
		return v;
	}
	/**
	 * Resizes the art, keeping its middle where it is; mounts, the floor's offset and the shield ellipse scale with it,
	 * so they stay on the same spots of the picture. A run of size changes undoes as one step.
	 */
	private void resize(int percent) {
		int old = d.artScale;
		if (percent == old || baseArt == null) { if (baseArt == null) refreshArtControls(); return; }
		int[] cur = scaledValues();
		if (refVals == null || !java.util.Arrays.equals(at(refVals, refScale, old), cur)) { refVals = cur; refScale = old; } // edited since: start from now
		int[] v = at(refVals, refScale, percent);
		int i = 0;
		for (ShipDesign.Mount m : d.mounts) { m.x = v[i++]; m.y = v[i++]; }
		d.floorX = v[i++]; d.floorY = v[i++]; d.ellipseW = v[i++]; d.ellipseH = v[i++]; d.ellipseX = v[i++]; d.ellipseY = v[i++];
		double cx = d.artX + baseArt.getWidth() / 2.0, cy = d.artY + baseArt.getHeight() / 2.0;
		d.artScale = percent;
		loadArt(false);
		if (baseArt != null) { d.artX = (int) Math.round(cx - baseArt.getWidth() / 2.0); d.artY = (int) Math.round(cy - baseArt.getHeight() / 2.0); }
		editor.setDesignArt(baseArt, floorArt);
		editor.burstEdit();
		host.changed();
		host.say("Art at " + percent + "%.");
	}

	private void build() {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.add(heading("Hull art"));
		artName.setAlignmentX(LEFT_ALIGNMENT);
		p.add(artName);
		p.add(row(button("Import PNG...", "Use a picture of your own (a copy is kept with your ships)", new ActionListener() {
			public void actionPerformed(ActionEvent e) { importArt(false); }
		}), button("From the game...", "Use one of the game's ship pictures", new ActionListener() {
			public void actionPerformed(ActionEvent e) { pickGameArt(); }
		})));
		JPanel size = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
		size.add(new JLabel("Size: "));
		artSize.setToolTipText("The art's size in percent; the floor, weapon mounts, shield and gibs follow it. Sizes between 100% steps soften the pixels a little.");
		((JSpinner.DefaultEditor) artSize.getEditor()).getTextField().setColumns(3);
		size.add(artSize);
		size.add(new JLabel(" %"));
		size.setAlignmentX(LEFT_ALIGNMENT);
		p.add(size);
		artSize.addChangeListener(new javax.swing.event.ChangeListener() {
			public void stateChanged(javax.swing.event.ChangeEvent e) { if (!refreshing) resize((Integer) artSize.getValue()); }
		});
		p.add(heading("Floor"));
		ButtonGroup fg = new ButtonGroup();
		fg.add(floorNone); fg.add(floorRooms); fg.add(floorFile);
		floorNone.setAlignmentX(LEFT_ALIGNMENT); floorRooms.setAlignmentX(LEFT_ALIGNMENT); floorFile.setAlignmentX(LEFT_ALIGNMENT);
		floorNone.setToolTipText("FTL tiles the rooms plain, with no walls drawn round them");
		floorRooms.setToolTipText("Walls round each room, open at the doors, the way the game's own ships look; drawn again whenever the rooms or the art move");
		floorFile.setToolTipText("A floor picture of your own (decorated floors, say), the hull picture's size, drawn over the hull at its corner");
		p.add(floorNone);
		p.add(floorRooms);
		p.add(floorFile);
		floorName.setAlignmentX(LEFT_ALIGNMENT);
		p.add(floorName);
		floorNone.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { if (!refreshing) setFloor(""); } });
		floorRooms.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { if (!refreshing) setFloor(ShipDesign.FLOOR_ROOMS); } });
		floorFile.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { if (!refreshing) importArt(true); } });
		p.add(heading("Line up"));
		ButtonGroup none = new ButtonGroup();
		JToggleButton move = editor.artToolButton("Move art", false, "Drag the hull art into place over the rooms. Arrow keys nudge it a pixel.");
		JToggleButton mounts = editor.artToolButton("Weapon mounts", true, "Click the hull to place a mount, drag to move it, right-click to remove it. Arrow keys nudge.");
		p.add(row(move, mounts));
		p.add(row(centerBtn = button("Center art", "Put the middle of the art over the middle of the rooms", new ActionListener() {
			public void actionPerformed(ActionEvent e) { if (editor.centerArt()) host.say("Art centred over the rooms."); else host.say("Choose her hull art first."); }
		}), artilleryBtn = button("Artillery mount", "Place the mount for the artillery gun (ships with an artillery system need one)", new ActionListener() {
			public void actionPerformed(ActionEvent e) { editor.startArtilleryMount(); }
		})));
		JLabel tips = new JLabel("<html><div style='width:230px'>Middle-drag moves the view; Alt + middle-drag moves the art in any tool. The wheel zooms; with a mount selected it turns the mount (Ctrl+wheel still zooms).</div></html>");
		tips.setFont(tips.getFont().deriveFont(11f));
		tips.setAlignmentX(LEFT_ALIGNMENT);
		p.add(tips);
		p.add(heading("Selected mount"));
		mountLabel.setAlignmentX(LEFT_ALIGNMENT);
		p.add(mountLabel);
		mountFacing.setAlignmentX(LEFT_ALIGNMENT);
		p.add(mountFacing);
		mountTurn.setToolTipText("Next facing: forward on the top edge, forward on the bottom edge, up, up mirrored (also R or the mouse wheel)");
		mountTurn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { editor.turnMount(1); } });
		mountEarlier = button("Earlier", "Make this an earlier mount: FTL's weapon slots follow the mounts' order (slot 1 is mount 1)", new ActionListener() {
			public void actionPerformed(ActionEvent e) { editor.moveMount(-1); }
		});
		mountLater = button("Later", "Make this a later mount: FTL's weapon slots follow the mounts' order", new ActionListener() {
			public void actionPerformed(ActionEvent e) { editor.moveMount(1); }
		});
		p.add(row(mountTurn, mountEarlier, mountLater));
		JPanel slide = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		slide.add(new JLabel("Slides out (S): "));
		slide.add(mountSlide);
		slide.setAlignmentX(LEFT_ALIGNMENT);
		p.add(slide);
		ActionListener mountEdit = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ShipDesign.Mount m = editor.selectedMount();
				if (m == null || refreshing) return;
				m.slide = (String) mountSlide.getSelectedItem();
				editor.repaint();
				host.changed();
			}
		};
		mountSlide.addActionListener(mountEdit);
		p.add(heading("Shield ellipse (check in game)"));
		JPanel ell = new JPanel(new GridLayout(2, 4, 4, 2));
		ell.add(new JLabel("Width")); ell.add(ellW); ell.add(new JLabel("Height")); ell.add(ellH);
		ell.add(new JLabel("Across")); ell.add(ellX); ell.add(new JLabel("Down")); ell.add(ellY);
		for (JSpinner sp : new JSpinner[] {ellW, ellH, ellX, ellY}) ((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setColumns(4);
		ell.setAlignmentX(LEFT_ALIGNMENT);
		p.add(ell);
		javax.swing.event.ChangeListener ellEdit = new javax.swing.event.ChangeListener() {
			public void stateChanged(javax.swing.event.ChangeEvent e) {
				if (refreshing) return;
				d.ellipseW = (Integer) ellW.getValue(); d.ellipseH = (Integer) ellH.getValue();
				d.ellipseX = (Integer) ellX.getValue(); d.ellipseY = (Integer) ellY.getValue();
				editor.repaint();
				host.changed();
			}
		};
		ellW.addChangeListener(ellEdit); ellH.addChangeListener(ellEdit); ellX.addChangeListener(ellEdit); ellY.addChangeListener(ellEdit);
		p.add(row(button("Fit to the art", "Size the ellipse from the hull art again", new ActionListener() {
			public void actionPerformed(ActionEvent e) { d.ellipseW = d.ellipseH = d.ellipseX = d.ellipseY = 0; fitEllipse(); editor.repaint(); host.changed(); }
		})));
		p.add(heading("Gibs (the pieces she breaks into)"));
		ButtonGroup gg = new ButtonGroup();
		gg.add(gibGame); gg.add(gibAuto); gg.add(gibFiles);
		gibGame.setAlignmentX(LEFT_ALIGNMENT);
		gibAuto.setAlignmentX(LEFT_ALIGNMENT);
		gibFiles.setAlignmentX(LEFT_ALIGNMENT);
		gibGame.setToolTipText("The pieces the game ship with this art breaks into");
		gibFiles.setToolTipText("Pictures the same size as the hull art, each showing one piece in its place (up to 6)");
		p.add(gibGame);
		p.add(gibAuto);
		p.add(gibFiles);
		gibGame.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { if (!refreshing) { d.gibs = "game"; host.changed(); } } });
		gibAuto.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { if (!refreshing) { d.gibs = "cut"; host.changed(); } } });
		gibFiles.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { chooseGibs(); } });
		p.add(row(button("Preview gibs", "See the pieces she'd break into", new ActionListener() {
			public void actionPerformed(ActionEvent e) { previewGibs(); }
		})));
		// the panel scrolls when the window is too short for all of it
		JPanel holder = new JPanel(new BorderLayout());
		holder.add(p, BorderLayout.NORTH);
		JScrollPane sp = new JScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.getVerticalScrollBar().setUnitIncrement(16);
		setLayout(new BorderLayout());
		add(sp, BorderLayout.CENTER);
		setPreferredSize(new java.awt.Dimension(310, 10));
	}
	private static JLabel heading(String t) {
		JLabel l = new JLabel(t);
		l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD));
		l.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}
	private static JButton button(String t, String tip, ActionListener a) {
		JButton b = new JButton(t);
		b.setToolTipText(tip);
		b.addActionListener(a);
		return b;
	}
	private static JPanel row(javax.swing.JComponent... cs) {
		JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
		for (int i = 0; i < cs.length; i++) { if (i > 0) r.add(javax.swing.Box.createHorizontalStrut(4)); r.add(cs[i]); }
		r.setAlignmentX(LEFT_ALIGNMENT);
		return r;
	}

	/** No floor, or one drawn from her rooms (a picture of her own comes through importArt). */
	private void setFloor(String floor) {
		if (floor.equals(d.floor)) return;
		d.floor = floor; d.floorX = 0; d.floorY = 0;
		loadArt(false);
		host.changed();
		host.say(floor.isEmpty() ? "No floor: FTL tiles her rooms plain." : "Her floor is drawn from her rooms, and follows them.");
	}
	/** Reads the design's pictures and hands them to the editor. */
	void loadArt() { loadArt(true); }
	void loadArt(boolean fit) {
		baseArt = ShipArt.scaled(ShipArt.load(d.art, d.art.startsWith("game:") ? "_base" : ""), d.artScale);
		floorArt = ShipArt.floorOf(d);
		editor.setDesignArt(baseArt, floorArt);
		artName.setText(d.art.isEmpty() ? "none yet" : baseArt == null ? "missing: " + d.art : describe(d.art) + "  (" + baseArt.getWidth() + " x " + baseArt.getHeight() + ")");
		floorName.setText(d.floor.isEmpty() ? " " : d.floorFromRooms() ? (baseArt == null ? "drawn once she has hull art" : "walls round the rooms, open at the doors")
				: floorArt == null ? "missing: " + d.floor : describe(d.floor) + "  (" + floorArt.getWidth() + " x " + floorArt.getHeight() + ")");
		if (fit) fitEllipse(); else refreshArtControls();
	}
	private static String describe(String src) {
		return src.startsWith("game:") ? "the game's " + src.substring(5) : ShipArt.file(src).getName();
	}
	private void fitEllipse() {
		if (d.ellipseW == 0 && d.ellipseH == 0 && baseArt != null) {
			int[] e = ShipArt.ellipseOf(d, baseArt);
			d.ellipseW = e[0]; d.ellipseH = e[1]; d.ellipseX = e[2]; d.ellipseY = e[3];
		}
		refreshArtControls();
	}
	void refreshArtControls() {
		refreshing = true;
		try {
			ShipDesign.Mount m = editor.selectedMount();
			boolean on = m != null && d.mounts.contains(m);
			mountTurn.setEnabled(on); mountSlide.setEnabled(on);
			mountEarlier.setEnabled(on && d.mounts.indexOf(m) > 0); mountLater.setEnabled(on && d.mounts.indexOf(m) < d.mounts.size() - 1);
			mountLabel.setText(on ? (m.artillery ? "Artillery mount" : "Mount " + (d.mounts.indexOf(m) + 1) + " of " + d.mounts.size()) + "  (" + m.x + ", " + m.y + ")" : "No mount selected (" + d.mounts.size() + " placed)");
			artilleryBtn.setEnabled(baseArt != null && d.systems.containsKey("artillery"));
			mountFacing.setText(on ? "Points " + LayoutEditor.facing(m) : " ");
			if (on) mountSlide.setSelectedItem(m.slide);
			ellW.setValue(d.ellipseW); ellH.setValue(d.ellipseH); ellX.setValue(d.ellipseX); ellY.setValue(d.ellipseY);
			artSize.setValue(d.artScale);
			artSize.setEnabled(baseArt != null);
			floorNone.setSelected(d.floor.isEmpty());
			floorRooms.setSelected(d.floorFromRooms());
			floorFile.setSelected(!d.floor.isEmpty() && !d.floorFromRooms());
			centerBtn.setEnabled(baseArt != null);
			boolean game = d.art.startsWith("game:") && ShipArt.gameExtras(d.art.substring(5))[2];
			gibGame.setVisible(game);
			gibGame.setSelected(game && d.gameGibs());
			gibAuto.setSelected(!"files".equals(d.gibs) && !(game && d.gameGibs()));
			gibFiles.setSelected("files".equals(d.gibs));
		} finally {
			refreshing = false;
		}
	}

	private java.io.File lastDir = null;
	private java.io.File choosePng(String title, boolean many, java.util.List<java.io.File> out) {
		javax.swing.JFileChooser fc = new javax.swing.JFileChooser(lastDir);
		fc.setDialogTitle(title);
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG pictures", "png"));
		fc.setMultiSelectionEnabled(many);
		if (fc.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return null;
		lastDir = fc.getCurrentDirectory();
		if (many && out != null) { for (java.io.File f : fc.getSelectedFiles()) out.add(f); return out.isEmpty() ? null : out.get(0); }
		return fc.getSelectedFile();
	}
	private void importArt(boolean floor) {
		java.io.File f = choosePng(floor ? "Floor picture" : "Hull art", false, null);
		if (f == null) { if (floor) refreshArtControls(); return; } // the choice stays what it was
		try {
			if (floor) {
				// FTL draws the floor over the hull at the hull's corner plus the floor's offset (the game's floors are smaller than
				// their hulls); it lands at the corner here, so a hull-sized picture is simplest
				java.awt.image.BufferedImage fl = javax.imageio.ImageIO.read(f);
				if (fl == null) throw new java.io.IOException(f.getName() + " isn't a picture the station can read.");
			}
			String src = ShipArt.importFile(f, d.id, floor ? "floor" : "base");
			if (floor) { d.floor = src; d.floorX = 0; d.floorY = 0; }
			else {
				boolean hadPicture = !d.floor.isEmpty() && !d.floorFromRooms();
				d.art = src; d.ellipseW = d.ellipseH = 0; d.artScale = 100;
				if (hadPicture) { d.floor = ""; d.floorX = d.floorY = 0; } // a floor picture was made for the old hull: it goes with it (one drawn from the rooms follows)
				if (hadPicture) host.say("Her floor picture went with the old hull: choose one for this hull, or a floor drawn from the rooms.");
			}
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "The Home Planet Station couldn't use that picture:\n" + ex.getMessage(), floor ? "Floor picture" : "Hull art", JOptionPane.WARNING_MESSAGE);
			if (floor) refreshArtControls(); // the choice stays what it was
			return;
		}
		loadArt();
		if (!floor) editor.centerArt();
		host.changed();
		host.say(floor ? "Floor picture imported." : "Hull art imported. It's centred over the rooms; Move art (or Alt + middle-drag) lines it up.");
	}
	private void pickGameArt() {
		final java.util.List<String> names = ShipArt.gameArt();
		final JList<String> list = new JList<String>(names.toArray(new String[0]));
		final JLabel preview = new JLabel();
		preview.setPreferredSize(new java.awt.Dimension(360, 240));
		preview.setHorizontalAlignment(JLabel.CENTER);
		list.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
			public void valueChanged(javax.swing.event.ListSelectionEvent e) {
				BufferedImage img = ShipArt.load("game:" + list.getSelectedValue(), "_base");
				preview.setIcon(img == null ? null : new javax.swing.ImageIcon(SpaceDockUI.fitImage(img, 350, 230)));
			}
		});
		JScrollPane sp = new JScrollPane(list);
		sp.setPreferredSize(new java.awt.Dimension(200, 240));
		JPanel p = new JPanel(new BorderLayout(8, 0));
		p.add(sp, BorderLayout.WEST);
		p.add(preview, BorderLayout.CENTER);
		final JCheckBox takeFloor = new JCheckBox("Her floor art", true), takeMounts = new JCheckBox("Her weapon mounts and shield ellipse", true),
				takeGibs = new JCheckBox("Her gibs (the pieces she breaks into)", true);
		JPanel takes = new JPanel(new GridLayout(0, 1));
		takes.add(new JLabel("Also take from that ship:"));
		takes.add(takeFloor); takes.add(takeMounts); takes.add(takeGibs);
		p.add(takes, BorderLayout.SOUTH);
		list.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
			public void valueChanged(javax.swing.event.ListSelectionEvent e) {
				if (list.getSelectedValue() == null) return;
				boolean[] has = ShipArt.gameExtras(list.getSelectedValue());
				JCheckBox[] boxes = {takeFloor, takeMounts, takeGibs};
				for (int i = 0; i < 3; i++) {
					if (!has[i]) boxes[i].setSelected(false);
					else if (!boxes[i].isEnabled()) boxes[i].setSelected(true); // was greyed out for the last ship
					boxes[i].setEnabled(has[i]);
					boxes[i].setToolTipText(has[i] ? null : "This ship has none");
				}
			}
		});
		String was = d.art.startsWith("game:") ? d.art.substring(5) : null;
		if (was != null && names.contains(was)) { list.setSelectedValue(was, true); }
		else if (!names.isEmpty()) list.setSelectedIndex(0);
		int r = JOptionPane.showConfirmDialog(this, p, "Hull art from the game", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (r != JOptionPane.OK_OPTION || list.getSelectedValue() == null) return;
		String gfx = list.getSelectedValue();
		d.artScale = 100;
		ShipArt.useGameArt(d, gfx, takeFloor.isSelected() && takeFloor.isEnabled(), takeMounts.isSelected() && takeMounts.isEnabled(),
				takeGibs.isSelected() && takeGibs.isEnabled());
		loadArt();
		editor.centerArt();
		host.changed();
		host.say("Hull art: the game's " + gfx + ", centred over the rooms. Move art (or Alt + middle-drag) lines it up.");
	}
	private void chooseGibs() {
		java.util.List<java.io.File> files = new java.util.ArrayList<java.io.File>();
		if (choosePng("Gib pictures (hull-sized, up to 6)", true, files) == null) { refreshArtControls(); return; }
		d.gibFiles.clear();
		try {
			for (int i = 0; i < files.size() && i < 6; i++) d.gibFiles.add(ShipArt.importFile(files.get(i), d.id, "gib" + (i + 1)));
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "The Home Planet Station couldn't use those pictures:\n" + ex.getMessage(), "Gib pictures", JOptionPane.WARNING_MESSAGE);
			d.gibFiles.clear();
		}
		d.gibs = d.gibFiles.isEmpty() ? "cut" : "files";
		refreshArtControls();
		host.changed();
		host.say(d.gibFiles.isEmpty() ? "No gib pictures: she'll be cut up from the hull art." : d.gibFiles.size() + " gib pictures in use.");
	}
	private void previewGibs() {
		if (baseArt == null) { host.say("Choose her hull art first."); return; }
		java.util.List<ShipArt.Gib> gibs = homeplanet.parser.DesignExport.gibs(d, baseArt); // what the export will use
		// spread the pieces a little from the middle, as if she's just come apart
		int pad = 90;
		BufferedImage out = new BufferedImage(baseArt.getWidth() + 2 * pad, baseArt.getHeight() + 2 * pad, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = out.createGraphics();
		g.setColor(new java.awt.Color(18, 22, 28));
		g.fillRect(0, 0, out.getWidth(), out.getHeight());
		double cx = baseArt.getWidth() / 2.0, cy = baseArt.getHeight() / 2.0;
		for (ShipArt.Gib gb : gibs) {
			double gx = gb.x + gb.img.getWidth() / 2.0 - cx, gy = gb.y + gb.img.getHeight() / 2.0 - cy;
			double len = Math.max(1, Math.hypot(gx, gy));
			g.drawImage(gb.img, pad + gb.x + (int) (gx / len * 45), pad + gb.y + (int) (gy / len * 45), null);
		}
		g.dispose();
		JLabel l = new JLabel(new javax.swing.ImageIcon(SpaceDockUI.fitImage(out, 800, 500)));
		JOptionPane.showMessageDialog(this, l, gibs.size() + " gibs" + ("files".equals(d.gibs) ? " (your pictures)" : d.gameGibs() ? " (the game ship's own)" : " (cut from the hull art)"), JOptionPane.PLAIN_MESSAGE);
	}

}
