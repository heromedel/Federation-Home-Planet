package homeplanet.ui;

import java.awt.BasicStroke;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.comm.Beacon;
import homeplanet.comm.Channel;
import homeplanet.comm.Commander;
import homeplanet.comm.Exchange;
import homeplanet.comm.Line;
import homeplanet.comm.Session;
import homeplanet.comm.Wire;
import homeplanet.core.HomePlanet;
import homeplanet.model.Crew;
import homeplanet.model.Items;
import homeplanet.parser.SaveHelper;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Long Range Comm.: trading with another commander's Home Planet Station. Built like the Cargo Bay: your side on the
 * left (a ship at a station, or the Cargo Hold), the other station's on the right, and between them the offer
 * both commanders build and accept. Nothing changes hands until both accept the same offer; what arrives goes into
 * the Cargo Hold. The station listens for hails only while this screen is open.
 */
public class LongRangeCommUI extends JPanel implements Scrollable, Session.View {
	private static final Logger log = LoggerFactory.getLogger(LongRangeCommUI.class);
	static final int W = CargoBayUI.W, H = CargoBayUI.H;
	private static final int HELP_Y = 646;
	private static final int LX = 16, LW = 404, MX = 436, MW = 408, RX = 860, RW = 404;
	private static final String CFG_FIREWALL = "long_range_comm_noted";

	final MainFrame parent;
	private final JPanel stage = new JPanel(null);
	private final CargoParts.Label help = new CargoParts.Label("", FtlFont.BODY, CargoParts.TEXT, -1);

	// ---- the link ----
	private Channel.Post post;
	private Beacon.Responder responder;
	private Session session;
	/** A hail going out, or one being answered: no second one meanwhile. */
	private boolean hailing;

	// ---- your side ----
	private Ship source;
	private final JLabel myPic = new JLabel();
	private final FtlButton sourceBtn = CargoBayUI.dropButton();
	private final FtlButton boardBtn = new FtlButton("Board", FtlFont.BODY, 74, 22);
	private final CargoParts.Label sourceNote = new CargoParts.Label("", FtlFont.BODY, CargoParts.DIM, -1);
	private final SupplyBox[] mySupply = new SupplyBox[4];
	private int supplyIdx = 0;
	private final JSpinner amount = new JSpinner(new SpinnerNumberModel(10, 1, Line.MAX_AMOUNT, 1));
	private final FtlButton offerSupplyBtn = new FtlButton("Offer >", FtlFont.BODY, 98, 22);
	private final CargoParts.RowList[] myLists = new CargoParts.RowList[4];
	private final FtlButton[] offerBtns = new FtlButton[4];
	private final FtlButton offerShipBtn = new FtlButton("Offer the whole ship", FtlFont.BODY, LW, 24);
	/** What the chosen source has that isn't in the offer yet. */
	private List<Line> available = new ArrayList<Line>();

	// ---- the middle ----
	private final CardLayout middleCards = new CardLayout();
	private final JPanel middle = new JPanel(middleCards);
	private final CargoParts.Label theyOfferLabel = new CargoParts.Label("", FtlFont.BODY, CargoParts.GOLD, -1);
	private final CargoParts.Label theirShipLabel = new CargoParts.Label("", FtlFont.BODY, CargoParts.DIM, 1);
	private final CargoParts.RowList myOffer = new CargoParts.RowList(), theirOffer = new CargoParts.RowList();
	private final FtlButton takeBackBtn = new FtlButton("< Take back", FtlFont.BODY, 110, 20);
	private final NoticeStrip notice = new NoticeStrip();
	private final Lamp myLamp = new Lamp(), theirLamp = new Lamp();
	private final FtlButton acceptBtn = new FtlButton("Accept", FtlFont.MENU, MW - 120, 40);
	private final FtlButton clearBtn = new FtlButton("Clear my offer", FtlFont.BODY, 160, 22);
	private final CargoParts.RowList messages = new CargoParts.RowList();
	private final List<CargoParts.Row> messageRows = new ArrayList<CargoParts.Row>();
	private final JTextField message = new JTextField();
	private final FtlButton sendBtn = new FtlButton("Send", FtlFont.BODY, 92, 26);

	// ---- their side ----
	private final CardLayout rightCards = new CardLayout();
	private final JPanel right = new JPanel(rightCards);
	private final CargoParts.RowList found = new CargoParts.RowList();
	private final CargoParts.Label scanNote = new CargoParts.Label("", FtlFont.BODY, CargoParts.DIM, 1);
	/** The port this station listens on, for a hail by address. */
	private final CargoParts.Label portNote = new CargoParts.Label("", FtlFont.BODY, CargoParts.DIM, 1);
	private final FtlButton establishBtn = new FtlButton("Establish Connection", FtlFont.MENU, RW - 44, 34);
	private final FtlButton hailBtn = new FtlButton("Hail", FtlFont.MENU, 146, 30), hailAddrBtn = new FtlButton("Hail", FtlFont.MENU, 146, 30);
	private final JTextField address = new JTextField();
	private final JLabel theirPic = new JLabel();
	/** "CONNECTED TO", with the other station's mode. */
	private final CargoParts.Label connectedTo = new CargoParts.Label("CONNECTED TO", FtlFont.BODY, CargoParts.DIM, 1);
	private final FtlButton who = new FtlButton("", FtlFont.MENU, RW - 122, 30);
	private final CargoParts.Label theirNote = new CargoParts.Label("", FtlFont.BODY, CargoParts.DIM, 1);
	private final SupplyBox[] theirSupply = new SupplyBox[4];
	private final CargoParts.Header[] theirHeads = new CargoParts.Header[4];
	private final CargoParts.RowList[] theirLists = new CargoParts.RowList[4];
	private final FtlButton disconnectBtn = new FtlButton("Disconnect", FtlFont.MENU, 146, 34);

	private static final String[] CAT = {"Weapons", "Drones", "Augments", "Crew"};
	private static final Line.Kind[] SUPPLY = {Line.Kind.SCRAP, Line.Kind.FUEL, Line.Kind.MISSILES, Line.Kind.PARTS};
	private static final String[][] SUPPLY_NAMES = {{"scrap", "Scrap"}, {"fuel", "Fuel"}, {"missiles", "Missiles"}, {"drones", "Parts"}};

	public LongRangeCommUI(MainFrame p) {
		parent = p;
		setLayout(null);
		setOpaque(true);
		setBackground(Color.black);
		stage.setOpaque(false);
		add(stage);
		buildTop();
		buildLeft();
		buildMiddle();
		buildRight();
		JComponent strip = new JComponent() {
			@Override protected void paintComponent(Graphics g) { g.setColor(new Color(10, 14, 20, 210)); g.fillRect(0, 0, getWidth(), getHeight()); }
		};
		// just under the panels, not at the very bottom: a window a few pixels short of the screen still shows it
		help.setBounds(16, HELP_Y, W - 32, 24);
		stage.add(help);
		strip.setBounds(0, HELP_Y, W, 24);
		stage.add(strip);
	}

	// ---- layout and backdrop: as the Cargo Bay's ----

	@Override public void doLayout() {
		int x = Math.max(-CargoBayUI.MARGIN, (getWidth() - W) / 2 - CargoBayUI.SHIFT), y = Math.max(0, (getHeight() - H) / 2);
		stage.setBounds(x, y, W, H);
	}
	@Override public Dimension getPreferredSize() { return new Dimension(W - 2 * CargoBayUI.MARGIN, H); }
	public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
	public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 20; }
	public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 200; }
	public boolean getScrollableTracksViewportWidth() { return getParent() != null && getParent().getWidth() >= W - 2 * CargoBayUI.MARGIN; }
	public boolean getScrollableTracksViewportHeight() { return getParent() != null && getParent().getHeight() >= H; }
	private BufferedImage hangar, hangarScaled;
	@Override protected void paintComponent(Graphics g0) {
		super.paintComponent(g0);
		BufferedImage hold = CargoBayUI.holdBackdrop();
		if (hold != null && hangar != hold) { hangar = hold; hangarScaled = null; }
		if (hangar == null) return;
		int w = getWidth(), h = getHeight();
		double s = Math.max(w / (double) hangar.getWidth(), h / (double) hangar.getHeight());
		int dw = (int) Math.ceil(hangar.getWidth() * s), dh = (int) Math.ceil(hangar.getHeight() * s);
		if (hangarScaled == null || hangarScaled.getWidth() != dw || hangarScaled.getHeight() != dh) {
			hangarScaled = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
			Graphics2D sg = hangarScaled.createGraphics();
			sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			sg.drawImage(hangar, 0, 0, dw, dh, null);
			sg.setColor(new Color(6, 10, 16, 150));
			sg.fillRect(0, 0, dw, dh);
			sg.dispose();
		}
		g0.drawImage(hangarScaled, (w - dw) / 2, 0, null);
	}

	// ============================================================== building

	private void buildTop() {
		FtlButton back = new FtlButton("", FtlFont.MENU, 262, 34) {
			@Override protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				boolean hot = getModel().isRollover() && !getModel().isPressed();
				BufferedImage t = FtlFont.MENU.render("RETURN TO DOCK", hot ? FtlButton.TEXT_HOT : FtlButton.TEXT);
				g.drawImage(t, 48, (getHeight() - t.getHeight()) / 2 + 1, null);
				g.setColor(CargoParts.GOLD);
				g.fillPolygon(new int[] {14, 26, 26}, new int[] {17, 8, 26}, 3);
				g.fillRect(26, 14, 12, 7);
			}
		};
		back.setToolTipText("Back to the Space Dock (an open channel is closed)");
		back.setBounds(16, 12, 262, 34);
		back.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { if (confirmLeave("return to the Space Dock")) parent.showSpaceDock(); }
		});
		stage.add(back);
		CargoParts.Label title = new CargoParts.Label("LONG RANGE COMM.", FtlFont.MENU, CargoParts.GOLD, 0);
		title.setBounds(440, 12, 400, 34);
		stage.add(title);
		disconnectBtn.setBounds(1118, 12, 146, 34);
		disconnectBtn.setToolTipText("Close the channel: nothing changes hands unless both stations have already accepted");
		disconnectBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (session == null) return;
				if (!HomePlanet.confirmNo(LongRangeCommUI.this, "Close the channel with " + session.peer.title + "?", "Disconnect")) return;
				session.close(Commander.title() + " closed the channel.");
				disconnected(null);
			}
		});
		stage.add(disconnectBtn);
	}

	private void buildLeft() {
		myPic.setBounds(LX, 60, 110, 62);
		myPic.setHorizontalAlignment(JLabel.CENTER);
		myPic.setToolTipText("Click for her report");
		myPic.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		myPic.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) { if (source != null && !source.isStorage() && source.save() != null) parent.spaceDock.showReport(source.save()); }
		});
		stage.add(myPic);
		label("OFFERING FROM", FtlFont.BODY, CargoParts.DIM, -1, LX + 122, 58, 282, 16);
		sourceBtn.setBounds(LX + 122, 74, 282, 30);
		sourceBtn.setToolTipText("Offer from the Cargo Hold, or from one of your ships at a station");
		sourceBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { pickSource(); } });
		stage.add(sourceBtn);
		boardBtn.setBounds(LX + 330, 108, 74, 22);
		boardBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { boardOrDock(); } });
		stage.add(boardBtn);
		sourceNote.setBounds(LX + 122, 110, 200, 16);
		stage.add(sourceNote);

		header("Supplies", false, LX, 136, LW);
		for (int i = 0; i < 4; i++) {
			final int idx = i;
			mySupply[i] = new SupplyBox(i);
			mySupply[i].setBounds(LX + i * 101, 162, 96, 44);
			mySupply[i].setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			mySupply[i].setToolTipText("Click, set the amount below, then Offer >");
			mySupply[i].addMouseListener(new MouseAdapter() { @Override public void mousePressed(MouseEvent e) { selectSupply(idx); } });
			stage.add(mySupply[i]);
		}
		amount.setBounds(LX, 212, 88, 26);
		amount.setToolTipText("How much to offer");
		styleSpinner(amount);
		stage.add(amount);
		offerSupplyBtn.setBounds(LX + 96, 214, 98, 22);
		offerSupplyBtn.setToolTipText("Put that much of the chosen supply in your offer");
		offerSupplyBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { offerSupply(); } });
		stage.add(offerSupplyBtn);

		int y = 246;
		for (int k = 0; k < 4; k++) {
			final int kind = k;
			header(CAT[k], false, LX, y, LW - 106);
			offerBtns[k] = new FtlButton("Offer >", FtlFont.BODY, 98, 22);
			offerBtns[k].setBounds(LX + LW - 98, y, 98, 22);
			offerBtns[k].setToolTipText(k == 3 ? "Put the chosen crew member in your offer" : "Put the chosen item in your offer");
			offerBtns[k].addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { offerSelected(kind); } });
			stage.add(offerBtns[k]);
			myLists[k] = new CargoParts.RowList();
			myLists[k].setEmptyText(k == 3 ? "No crew" : "None");
			myLists[k].setBounds(LX, y + 24, LW, 62);
			myLists[k].onChange(new Runnable() { public void run() { if (myLists[kind].selectedValue() != null) clearOtherSelections(kind); updateButtons(); } });
			myLists[k].onDoubleClick(new Runnable() { public void run() { info((Line) myLists[kind].selectedValue()); } });
			stage.add(myLists[k]);
			y += 92;
		}
		offerShipBtn.setBounds(LX, 618, LW, 24);
		offerShipBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { offerShip(); } });
		stage.add(offerShipBtn);
	}

	private void buildMiddle() {
		middle.setOpaque(false);
		middle.setBounds(MX, 58, MW, 586);
		stage.add(middle);

		JPanel idle = new JPanel(null);
		idle.setOpaque(false);
		JComponent box = new JComponent() {
			@Override protected void paintComponent(Graphics g0) {
				Graphics2D g = (Graphics2D) g0.create();
				CargoParts.paintBox(g, 0, 0, getWidth(), getHeight(), CargoParts.BOX_LINE);
				String[] l = {"NO CHANNEL OPEN", "", "Establish a connection with another", "commander's Home Planet Station to trade."};
				for (int i = 0; i < l.length; i++) {
					FtlFont f = i == 0 ? FtlFont.MENU : FtlFont.BODY;
					CargoParts.text(g, l[i], f, i == 0 ? CargoParts.GOLD : CargoParts.DIM, (getWidth() - CargoParts.width(l[i], f)) / 2, 200 + i * 20);
				}
				g.dispose();
			}
		};
		box.setBounds(0, 26, MW, 540);
		idle.add(box);
		header("The Offer", false, 0, 0, MW, idle);
		middle.add(idle, "idle");

		JPanel open = new JPanel(null);
		open.setOpaque(false);
		header("The Offer", false, 0, 0, MW, open);
		CargoParts.Label you = new CargoParts.Label("YOU OFFER", FtlFont.BODY, CargoParts.GOLD, -1);
		you.setBounds(0, 28, 200, 16);
		open.add(you);
		takeBackBtn.setBounds(MW - 110, 26, 110, 20);
		takeBackBtn.setToolTipText("Take the chosen line back out of your offer");
		takeBackBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { takeBack(); } });
		open.add(takeBackBtn);
		myOffer.setEmptyText("Nothing offered");
		myOffer.setBounds(0, 48, MW, 115);
		myOffer.onChange(new Runnable() { public void run() { updateButtons(); } });
		myOffer.onDoubleClick(new Runnable() { public void run() { takeBack(); } });
		open.add(myOffer);
		theyOfferLabel.setBounds(0, 170, 150, 16);
		open.add(theyOfferLabel);
		theirShipLabel.setBounds(150, 170, MW - 150, 16);
		open.add(theirShipLabel);
		theirOffer.setEmptyText("Nothing offered");
		theirOffer.setBounds(0, 190, MW, 115);
		theirOffer.onDoubleClick(new Runnable() { public void run() { info((Line) theirOffer.selectedValue()); } });
		open.add(theirOffer);
		notice.setBounds(0, 312, MW, 28);
		open.add(notice);
		myLamp.setBounds(0, 346, MW / 2 - 4, 30);
		open.add(myLamp);
		theirLamp.setBounds(MW / 2 + 4, 346, MW / 2 - 4, 30);
		open.add(theirLamp);
		acceptBtn.setBounds(60, 384, MW - 120, 36);
		acceptBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { pressAccept(); } });
		open.add(acceptBtn);
		clearBtn.setBounds(0, 426, 160, 22);
		clearBtn.setToolTipText("Take everything back out of your offer");
		clearBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { if (session != null) session.clearMine(); } });
		open.add(clearBtn);
		// messages between the two commanders: the latest at the bottom, a line to write in under them
		messages.setEmptyText("No messages");
		messages.setBounds(0, 456, MW, 92);
		open.add(messages);
		message.setBounds(0, 554, MW - 100, 26);
		message.setBackground(new Color(16, 20, 26));
		message.setForeground(CargoParts.TEXT);
		message.setCaretColor(CargoParts.GOLD);
		message.setFont(message.getFont().deriveFont(java.awt.Font.PLAIN, 13f));
		message.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(CargoParts.BOX_LINE),
				javax.swing.BorderFactory.createEmptyBorder(0, 6, 0, 6)));
		message.setDocument(new javax.swing.text.PlainDocument() {
			@Override public void insertString(int offs, String str, javax.swing.text.AttributeSet a) throws javax.swing.text.BadLocationException {
				if (str != null && getLength() + str.length() <= Session.SAY_MAX) super.insertString(offs, str, a);
			}
		});
		message.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { sendMessage(); } });
		open.add(message);
		sendBtn.setBounds(MW - 92, 554, 92, 26);
		sendBtn.setToolTipText("Send the message to the other commander (Enter)");
		sendBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { sendMessage(); } });
		open.add(sendBtn);
		middle.add(open, "open");
	}

	private void buildRight() {
		right.setOpaque(false);
		right.setBounds(RX, 58, RW, 586);
		stage.add(right);

		JPanel connect = new JPanel(null);
		connect.setOpaque(false);
		CargoParts.Label none = new CargoParts.Label("NO CONNECTION", FtlFont.BODY, CargoParts.DIM, 1);
		none.setBounds(0, 0, RW, 16);
		connect.add(none);
		establishBtn.setBounds(44, 16, RW - 44, 34);
		establishBtn.setToolTipText("Search the local network for other Home Planet Stations with Long Range Comm. open");
		establishBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { scan(); } });
		connect.add(establishBtn);
		header("Commanders in range", true, 0, 78, RW, connect);
		found.setEmptyText("Press Establish Connection to search");
		found.setBounds(0, 102, RW, 150);
		found.onChange(new Runnable() { public void run() { updateButtons(); } });
		found.onDoubleClick(new Runnable() { public void run() { hailFound(); } });
		connect.add(found);
		scanNote.setBounds(0, 256, RW, 16);
		connect.add(scanNote);
		hailBtn.setBounds(RW - 146, 278, 146, 30);
		hailBtn.setToolTipText("Open a channel to the chosen commander's station: they must answer");
		hailBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { hailFound(); } });
		connect.add(hailBtn);
		header("By address", true, 0, 326, RW, connect);
		address.setBounds(0, 352, RW - 156, 28);
		address.setBackground(new Color(16, 20, 26));
		address.setForeground(CargoParts.GOLD);
		address.setCaretColor(CargoParts.GOLD);
		address.setFont(address.getFont().deriveFont(java.awt.Font.BOLD, 15f));
		address.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(CargoParts.BOX_LINE),
				javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 8)));
		address.setToolTipText("The other computer's address, as 100.64.12.7 or name, with :port if their station uses another than " + Channel.PORT0);
		address.setText(HomePlanet.config.getProperty("long_range_comm_address", ""));
		address.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { hailAddress(); } });
		connect.add(address);
		hailAddrBtn.setBounds(RW - 146, 351, 146, 30);
		hailAddrBtn.setToolTipText("Open a channel to the station at that address: they must answer");
		hailAddrBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { hailAddress(); } });
		connect.add(hailAddrBtn);
		CargoParts.Label addrNote = new CargoParts.Label("For a station on a virtual LAN.", FtlFont.BODY, CargoParts.DIM, 1);
		addrNote.setBounds(0, 386, RW, 16);
		connect.add(addrNote);
		portNote.setBounds(0, 420, RW, 16);
		portNote.setToolTipText("To be hailed over the internet, forward this port (TCP) on your router to this computer, and give the other commander your public address. "
				+ "On a virtual LAN (Tailscale, Hamachi, ZeroTier) nothing needs setting up.");
		connect.add(portNote);
		CargoParts.Label portHint = new CargoParts.Label("Online: forward it on your router.", FtlFont.BODY, CargoParts.DIM, 1);
		portHint.setBounds(0, 438, RW, 16);
		portHint.setToolTipText(portNote.getToolTipText());
		connect.add(portHint);
		right.add(connect, "connect");

		JPanel partner = new JPanel(null);
		partner.setOpaque(false);
		theirPic.setBounds(RW - 110, 2, 110, 62);
		theirPic.setHorizontalAlignment(JLabel.CENTER);
		partner.add(theirPic);
		connectedTo.setBounds(0, 0, RW - 122, 16);
		partner.add(connectedTo);
		who.setBounds(0, 16, RW - 122, 30);
		who.setFocusable(false);
		who.setCursor(java.awt.Cursor.getDefaultCursor());
		who.setRolloverEnabled(false);
		partner.add(who);
		theirNote.setBounds(0, 52, RW - 122, 16);
		partner.add(theirNote);
		header("Supplies", true, 0, 78, RW, partner);
		for (int i = 0; i < 4; i++) {
			theirSupply[i] = new SupplyBox(i);
			theirSupply[i].setBounds(2 + i * 101, 104, 96, 44);
			partner.add(theirSupply[i]);
		}
		CargoParts.Label shared = new CargoParts.Label("Theirs to offer: only they can add it.", FtlFont.BODY, CargoParts.DIM, 1);
		shared.setBounds(0, 158, RW, 16);
		partner.add(shared);
		int y = 188;
		for (int k = 0; k < 4; k++) {
			final int kind = k;
			theirHeads[k] = header(CAT[k], true, 0, y, RW, partner);
			theirLists[k] = new CargoParts.RowList();
			theirLists[k].setEmptyText(k == 3 ? "No crew" : "None");
			theirLists[k].setBounds(0, y + 24, RW, 62);
			theirLists[k].onDoubleClick(new Runnable() { public void run() { info((Line) theirLists[kind].selectedValue()); } });
			partner.add(theirLists[k]);
			y += 92;
		}
		right.add(partner, "partner");
	}

	private CargoParts.Label label(String text, FtlFont f, Color c, int align, int x, int y, int w, int h) {
		CargoParts.Label l = new CargoParts.Label(text, f, c, align);
		l.setBounds(x, y, w, h);
		stage.add(l);
		return l;
	}
	private CargoParts.Header header(String text, boolean right, int x, int y, int w) { return header(text, right, x, y, w, stage); }
	private CargoParts.Header header(String text, boolean right, int x, int y, int w, JPanel into) {
		CargoParts.Header h = new CargoParts.Header(text, right);
		h.setBounds(x, y, w, 22);
		into.add(h);
		return h;
	}
	private static void styleSpinner(JSpinner sp) {
		sp.setOpaque(false);
		sp.setBorder(javax.swing.BorderFactory.createLineBorder(CargoParts.BOX_LINE));
		javax.swing.JFormattedTextField tf = ((JSpinner.DefaultEditor) sp.getEditor()).getTextField();
		tf.setHorizontalAlignment(javax.swing.JTextField.CENTER);
		tf.setFont(tf.getFont().deriveFont(java.awt.Font.BOLD, 16f));
		tf.setForeground(CargoParts.GOLD);
		tf.setBackground(new Color(16, 20, 26));
		tf.setCaretColor(CargoParts.GOLD);
		tf.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		for (java.awt.Component c : sp.getComponents()) if (c instanceof javax.swing.JButton) c.setBackground(new Color(28, 36, 44));
	}

	// ============================================================== opening and leaving

	/** Opens the screen: asks for a name the first time, starts listening, reads your side. False if it shouldn't open. */
	public boolean init() {
		if (!Commander.ensure(this)) return false;
		if (!HomePlanet.config.containsKey(CFG_FIREWALL)) {
			JOptionPane.showMessageDialog(this, "<html><div style='width:420px'>Long Range Comm. listens for other commanders' stations on your local network while this screen is open.<br><br>"
					+ "The first time, Windows Firewall may ask whether to let Java (\"OpenJDK Platform binary\") use the network. Allow it on private networks, "
					+ "or other stations won't be able to reach this one.</div></html>", "Long Range Comm.", JOptionPane.INFORMATION_MESSAGE);
			HomePlanet.config.setProperty(CFG_FIREWALL, "true");
			HomePlanet.saveConfig();
		}
		openPost();
		if (source == null || Vault.get().byId(source.id) == null) source = null;
		readSource();
		if (session == null) {
			middleCards.show(middle, "idle");
			rightCards.show(right, "connect");
		}
		refreshAll();
		help(session == null ? "Establish Connection searches the local network for other Home Planet Stations. Choose a commander, then Hail." : helpOpen());
		revalidate();
		repaint();
		return true;
	}
	/** Asks before closing an open channel; true to go on. */
	boolean confirmLeave(String doing) {
		if (session == null || session.isOver()) { closePost(); return true; }
		if (!HomePlanet.confirmNo(this, "Close the channel with " + session.peer.title + " and " + doing + "?", "Long Range Comm.")) return false;
		session.close(Commander.title() + " closed the channel.");
		disconnected(null);
		closePost();
		return true;
	}
	private void openPost() {
		if (post != null) return;
		try {
			post = new Channel.Post(new Channel.Post.Handler() { public void hailed(Channel c) { incoming(c); } });
			responder = new Beacon.Responder(new Beacon.Self() {
				public String answer() {
					final String[] a = new String[1];
					try {
						SwingUtilities.invokeAndWait(new Runnable() { public void run() { a[0] = selfAnswer(); } });
					} catch (Exception e) {
						a[0] = selfAnswer();
					}
					return a[0];
				}
			});
			portNote.setText("This station listens on port " + post.port + ".");
		} catch (IOException e) {
			log.warn("Long Range Comm. could not listen: {}", e.toString());
			closePost();
			scanNote.setText("Can't listen (ports busy): you can still hail.");
			portNote.setText("Not listening: every port is in use.");
		}
	}
	private String selfAnswer() {
		Ship b = Vault.get().boarded();
		return Beacon.answer(post == null ? Channel.PORT0 : post.port, HomePlanet.APP_VERSION, Commander.stationId(), Commander.title(), b == null ? "" : b.name, Vault.get().slot);
	}
	private void closePost() {
		if (post != null) post.close();
		if (responder != null) responder.close();
		post = null;
		responder = null;
	}

	// ============================================================== finding and hailing

	private List<Beacon.Found> foundList = new ArrayList<Beacon.Found>();
	private void scan() {
		establishBtn.setEnabled(false);
		scanNote.setText("Scanning the local network...");
		final String self = Commander.stationId();
		new Thread(new Runnable() {
			public void run() {
				final List<Beacon.Found> f = Beacon.scan(1500, self);
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						establishBtn.setEnabled(true);
						foundList = f;
						List<CargoParts.Row> rows = new ArrayList<CargoParts.Row>();
						for (Beacon.Found x : f) {
							boolean same = x.compatible();
							String mode = Vault.title(x.mode);
							rows.add(new CargoParts.Row(null, x.title + (x.ship.isEmpty() ? "" : ", aboard " + x.ship), same ? mode : "needs an update", x,
									same ? x.title + "'s Home Planet Station (" + mode + ", Federation Home Planet " + x.version + "), at " + x.host + ":" + x.port
											: "Federation Home Planet " + x.version + ": its Long Range Comm. is " + (x.protocol < homeplanet.comm.Session.PROTOCOL ? "older" : "newer") + " than this station's. One of you needs to update to trade.", !same));
						}
						found.setRows(rows);
						if (!rows.isEmpty()) found.list.setSelectedIndex(0);
						found.setEmptyText("No stations answered");
						scanNote.setText(f.isEmpty() ? "No answer: is their Long Range Comm. open?" : f.size() == 1 ? "1 station in range." : f.size() + " stations in range.");
						updateButtons();
					}
				});
			}
		}, "Long Range Comm. scan").start();
	}
	private void hailFound() {
		Object v = found.selectedValue();
		if (!(v instanceof Beacon.Found)) return;
		Beacon.Found f = (Beacon.Found) v;
		hail(f.host, new int[] {f.port}, f.title);
	}
	private void hailAddress() {
		String a = address.getText().trim();
		if (a.isEmpty()) { address.requestFocusInWindow(); return; }
		String host = a;
		int[] ports = new int[Channel.PORTS];
		for (int i = 0; i < ports.length; i++) ports[i] = Channel.PORT0 + i;
		int colon = a.lastIndexOf(':');
		if (colon > 0 && a.indexOf(':') == colon) {
			try { ports = new int[] {Integer.parseInt(a.substring(colon + 1).trim())}; host = a.substring(0, colon).trim(); }
			catch (NumberFormatException e) { HomePlanet.showErrorDialog("\"" + a.substring(colon + 1) + "\" isn't a port number."); return; }
		}
		HomePlanet.config.setProperty("long_range_comm_address", a);
		HomePlanet.saveConfig();
		hail(host, ports, host);
	}
	/** Hails a station: connects (trying each port until one answers), says hello, waits for their commander to answer. */
	private void hail(final String host, final int[] ports, final String whom) {
		if (session != null || hailing) return;
		hailing = true;
		updateButtons();
		scanNote.setText("Hailing " + whom + "...");
		final Wire.Msg hello = myHello();
		new Thread(new Runnable() {
			public void run() {
				Channel ch = null;
				String fail = null;
				Wire.Msg reply = null;
				for (int p : ports) {
					try { ch = Channel.connect(host, p); break; }
					catch (java.net.SocketTimeoutException e) { fail = "No answer from " + host + ". Is the address right, and their Long Range Comm. screen open?"; break; }
					catch (java.net.UnknownHostException e) { fail = "There's no computer called " + host + " on the network."; break; }
					catch (IOException e) { fail = "No Home Planet Station answered at " + host + ". Their Long Range Comm. screen must be open."; }
				}
				if (ch != null) {
					try {
						ch.send(hello);
						reply = ch.readFirst(90000);
					} catch (java.net.SocketTimeoutException e) {
						fail = whom + " didn't answer the hail.";
					} catch (Wire.Garbled e) {
						fail = "The Home Planet Station received a garbled transmission and closed the channel.";
					} catch (IOException e) {
						fail = "The link to " + whom + " was lost.";
					}
				}
				final Channel c = ch;
				final Wire.Msg r = reply;
				final String why = fail;
				SwingUtilities.invokeLater(new Runnable() { public void run() { hailAnswered(c, r, why); } });
			}
		}, "Long Range Comm. hail").start();
	}
	private void hailAnswered(Channel ch, Wire.Msg reply, String fail) {
		hailing = false;
		scanNote.setText("");
		if (fail == null && reply != null && reply.type.equals("BYE")) fail = byeReason(reply.get("why"));
		if (fail == null && reply != null) {
			try {
				Session.Peer p = Session.peerOf(reply);
				String why = Session.incompatible(p, HomePlanet.APP_VERSION, Commander.stationId(), Vault.get().slot, HomePlanet.immersiveAnyLevel);
				if (why != null) { ch.close(why); fail = why; }
				else { begin(new Session(ch, true, p, Commander.stationId(), shipsAllowed())); return; }
			} catch (Wire.Garbled e) {
				fail = "The Home Planet Station received a garbled transmission and closed the channel.";
			}
		}
		if (ch != null) ch.close("");
		updateButtons();
		JOptionPane.showMessageDialog(this, fail == null ? "The hail went unanswered." : fail, "Long Range Comm.", JOptionPane.INFORMATION_MESSAGE);
	}
	private static String byeReason(String s) { return s == null || s.trim().isEmpty() ? "The other station closed the channel." : s.trim(); }

	/** Another station hails this one (off the event thread): read its hello, then ask the commander. */
	private void incoming(final Channel ch) {
		Wire.Msg m;
		try { m = ch.readFirst(10000); } catch (IOException e) { ch.close(""); return; }
		final Session.Peer p;
		try { p = Session.peerOf(m); } catch (Wire.Garbled e) { ch.close("The other station's hail was garbled."); return; }
		SwingUtilities.invokeLater(new Runnable() { public void run() { answer(ch, p); } });
	}
	private void answer(Channel ch, Session.Peer p) {
		if (session != null || hailing || !isShowing()) { ch.close(Commander.title() + " is busy with another channel."); return; }
		String why = Session.incompatible(p, HomePlanet.APP_VERSION, Commander.stationId(), Vault.get().slot, HomePlanet.immersiveAnyLevel);
		if (why != null) { ch.close(why); notice.set(p.title + " hailed this station, but: " + why); return; }
		hailing = true;
		Object[] opts = {"Answer", "Ignore"};
		int r = JOptionPane.showOptionDialog(this, p.title + " (" + p.modeTitle() + ")" + (p.ship.isEmpty() ? "" : ", aboard " + p.ship) + ", is hailing The Home Planet Station.\nAnswer the hail?",
				"Incoming hail", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
		hailing = false;
		if (r != 0 || ch.isClosed()) { ch.close(Commander.title() + " did not answer the hail."); return; }
		try { ch.send(myHello()); }
		catch (IOException e) { JOptionPane.showMessageDialog(this, "The link to " + p.title + " was lost.", "Long Range Comm.", JOptionPane.INFORMATION_MESSAGE); return; }
		begin(new Session(ch, false, p, Commander.stationId(), shipsAllowed()));
	}
	private Wire.Msg myHello() {
		Ship b = Vault.get().boarded();
		return Session.hello(HomePlanet.APP_VERSION, Commander.stationId(), Commander.title(), b == null ? "" : b.name, Vault.get().slot, shipsAllowed(), HomePlanet.immersiveAnyLevel);
	}
	/** Whether this station lets whole ships change hands: a Sandbox fleet always; an Immersive career by its own setting. */
	public static boolean shipsAllowed() { return !HomePlanet.immersiveMode || HomePlanet.immersiveShipTrading; }

	private void begin(Session s) {
		session = s;
		lastShow = null;
		notice.set("");
		messageRows.clear();
		messages.setRows(messageRows);
		message.setText("");
		who.setText(s.peer.title);
		connectedTo.setText("CONNECTED TO  (" + s.peer.modeTitle().toUpperCase() + ")");
		theyOfferLabel.setText(shortName(s.peer.title).toUpperCase() + " OFFERS");
		middleCards.show(middle, "open");
		rightCards.show(right, "partner");
		s.start(this);
		readSource();
		refreshAll();
		notice.set("Channel open with " + s.peer.title + ".");
		help(helpOpen());
	}
	private static String shortName(String title) {
		for (String r : homeplanet.parser.UnlockGrants.RANKS) if (title.startsWith(r + " ")) return title.substring(r.length() + 1);
		return title;
	}
	private String helpOpen() {
		return "Choose something on your side, then Offer >. Both stations must Accept the same offer; any change withdraws acceptance.";
	}
	/** The channel is gone: back to finding a station. */
	private void disconnected(String why) {
		session = null;
		middleCards.show(middle, "idle");
		rightCards.show(right, "connect");
		readSource();
		refreshAll();
		help("Establish Connection searches the local network for other Home Planet Stations. Choose a commander, then Hail.");
		if (why != null) JOptionPane.showMessageDialog(this, why, "Long Range Comm.", JOptionPane.INFORMATION_MESSAGE);
	}

	// ---- Session.View ----

	public void changed() { refreshAll(); }
	public void notice(String text) { notice.set(text); }
	public void said(String who, String text) { addMessage(shortName(who), text, false); }
	/** A line in the message log: who, what (cut to fit; the whole of it on hover), and when. */
	private void addMessage(String who, String text, boolean mine) {
		String time = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
		messageRows.add(new CargoParts.Row(null, who + ": " + text, time, null, "<html><div style='width:320px'>" + who + ": " + htmlText(text) + "</div></html>", mine));
		while (messageRows.size() > 100) messageRows.remove(0);
		messages.setRows(messageRows);
		messages.list.ensureIndexIsVisible(messageRows.size() - 1);
	}
	private static String htmlText(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
	private void sendMessage() {
		if (session == null) return;
		String t = message.getText();
		if (!session.say(t)) return;
		addMessage("You", Line.text(t, Session.SAY_MAX), true);
		message.setText("");
	}
	public void problem(String text) { JOptionPane.showMessageDialog(this, text, "Long Range Comm.", JOptionPane.WARNING_MESSAGE); }
	public void settled(Exchange.Record r) {
		lastShow = null;
		if (Exchange.DONE.equals(r.state)) help("Trade with " + r.peerTitle + " complete. Received " + r.inWords() + "; gave " + r.outWords() + ".");
		else help("Trade with " + r.peerTitle + " called off: " + r.outWords() + " came back: " + Exchange.whereTheyGo(r.out) + ".");
		if (r.needsPatch) {
			// a custom ship's blueprint came with her: FTL needs it before she can fly
			final Exchange.Record rec = r;
			SwingUtilities.invokeLater(new Runnable() { public void run() {
				Object[] options = {"Patch Now", "Later"};
				int p = JOptionPane.showOptionDialog(LongRangeCommUI.this, "A ship from " + rec.peerTitle + " flies on a blueprint of her own, and it is now in the "
						+ homeplanet.parser.CompanionMod.TITLE + ".\nSend it to FTL via Slipstream before you board her.", "Long Range Comm.",
						JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
				if (p == 0) PatchDialog.open(LongRangeCommUI.this);
			} });
		}
		readSource();
		refreshAll();
	}
	public void ended(String why) { disconnected(why); }

	// ============================================================== your side

	/** The ships that can offer: the Cargo Hold, then each ship at the Space Dock (those away from a station greyed). */
	private void pickSource() {
		JPopupMenu m = new JPopupMenu();
		List<Ship> list = new ArrayList<Ship>();
		try { list.add(Vault.get().storage()); } catch (IOException e) { log.warn("No Cargo Hold: {}", e.toString()); }
		for (Ship s : Vault.get().fleet()) if (s.save() != null) list.add(s);
		for (final Ship s : list) {
			boolean ok = s.isStorage() || Vault.get().mayTrade(s);
			JMenuItem it = new JMenuItem(nameOf(s) + (s == source ? "   (now)" : "") + (ok ? "" : "   (away from a station)"));
			it.setEnabled(ok && (session == null || !session.exchanging()));
			it.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) { source = s; readSource(); refreshAll(); }
			});
			m.add(it);
		}
		CargoParts.darkPopup(m);
		m.show(sourceBtn, 0, sourceBtn.getHeight());
	}
	private static String nameOf(Ship s) { return s.isStorage() ? "Cargo Hold" : s.name; }

	/** The chosen source as its file is now (the Cargo Hold if none was chosen, or the chosen one left). */
	private SavedGameState sourceSave;
	private void readSource() {
		Vault v = Vault.get();
		if (source != null && (v.byId(source.id) == null || (!source.isStorage() && !v.mayTrade(source)))) source = null;
		if (source == null) {
			try { source = v.storage(); } catch (IOException e) { log.warn("No Cargo Hold: {}", e.toString()); }
		}
		if (source != null) source.invalidate(); // the Cargo Bay or FTL may have changed it
		sourceSave = source == null ? null : source.save();
	}

	/** Everything the source has, as offer lines (supplies whole; items and crew one line each). */
	private List<Line> contents() {
		List<Line> out = new ArrayList<Line>();
		if (sourceSave == null) return out;
		ShipState s = sourceSave.getPlayerShip();
		int[] sup = {s.getScrapAmt(), s.getFuelAmt(), s.getMissilesAmt(), s.getDronePartsAmt()};
		for (int i = 0; i < 4; i++) if (sup[i] > 0) out.add(mark(Line.supply(0, SUPPLY[i], sup[i]), false));
		for (WeaponState w : s.getWeaponList()) out.add(mark(Line.item(0, Line.Kind.WEAPON, w.getWeaponId()), false));
		for (DroneState d : s.getDroneList()) out.add(mark(Line.item(0, Line.Kind.DRONE, d.getDroneId()), false));
		for (String a : s.getAugmentIdList()) out.add(mark(Line.item(0, Line.Kind.AUGMENT, a), false));
		if (!source.isStorage() && sourceSave.getCargoIdList() != null) {
			for (String id : sourceSave.getCargoIdList()) {
				Line.Kind k = Items.isWeapon(id) ? Line.Kind.WEAPON : Items.isDrone(id) ? Line.Kind.DRONE : Items.isAugment(id) ? Line.Kind.AUGMENT : null;
				if (k != null) out.add(mark(Line.item(0, k, id), true));
			}
		}
		for (CrewState c : SaveHelper.getOwnCrew(s)) if (SaveHelper.hasBody(c)) out.add(mark(Line.crew(0, c), false));
		return out;
	}
	private Line mark(Line l, boolean inCargo) {
		l.from = source.id;
		l.fromName = nameOf(source);
		l.inCargo = inCargo;
		return l;
	}
	/** Is the chosen source offered whole (everything aboard goes with her)? */
	private boolean sourceOfferedWhole() {
		if (session == null || source == null) return false;
		for (Line o : session.mine()) if (o.kind == Line.Kind.SHIP && o.from.equals(source.id)) return true;
		return false;
	}
	/** What the source has less what's already offered from it (nothing, when she's offered whole). */
	private List<Line> computeAvailable() {
		List<Line> have = contents();
		if (session == null) return have;
		if (sourceOfferedWhole()) return new ArrayList<Line>();
		for (Line o : session.mine()) {
			if (!o.from.equals(source.id)) continue;
			for (int i = 0; i < have.size(); i++) {
				Line h = have.get(i);
				if (h.kind != o.kind) continue;
				if (o.kind.isSupply()) {
					int left = h.amount - o.amount;
					if (left <= 0) have.remove(i); else have.set(i, mark(Line.supply(0, h.kind, left), false));
					break;
				}
				boolean same = o.kind == Line.Kind.CREW ? Line.signature(o.crew).equals(Line.signature(h.crew)) : o.id.equals(h.id) && o.inCargo == h.inCargo;
				if (same) { have.remove(i); break; }
			}
		}
		return have;
	}

	private void selectSupply(int i) {
		supplyIdx = i;
		for (int k = 0; k < 4; k++) { mySupply[k].selected = k == i; mySupply[k].repaint(); }
		updateButtons();
		help("Offering " + SUPPLY_NAMES[i][1].toLowerCase() + ": set the amount, then Offer >.");
	}
	private int availableSupply(int i) {
		for (Line l : available) if (l.kind == SUPPLY[i]) return l.amount;
		return 0;
	}
	private void offerSupply() {
		if (session == null) return;
		int have = availableSupply(supplyIdx);
		if (have <= 0) { help(nameOf(source) + " has no " + SUPPLY_NAMES[supplyIdx][1].toLowerCase() + " left to offer."); return; }
		int n = Math.min((Integer) amount.getValue(), have);
		// one line per supply and source: an earlier one is folded into this
		int already = 0;
		for (Line l : new ArrayList<Line>(session.mine())) if (l.kind == SUPPLY[supplyIdx] && l.from.equals(source.id)) { already += l.amount; session.remove(l.n); }
		session.add(mark(Line.supply(0, SUPPLY[supplyIdx], Math.min(Line.MAX_AMOUNT, already + n)), false));
		help("Offered " + n + " " + SUPPLY_NAMES[supplyIdx][1].toLowerCase() + ".");
	}
	private void offerSelected(int kind) {
		if (session == null) return;
		Object v = myLists[kind].selectedValue();
		if (!(v instanceof Line)) return;
		Line l = (Line) v;
		if (l.kind == Line.Kind.CREW && !source.isStorage()) {
			int crewLeft = 0;
			for (Line a : available) if (a.kind == Line.Kind.CREW) crewLeft++;
			if (crewLeft <= 1) { HomePlanet.showErrorDialog("At least one crew member must stay aboard " + source.name + "."); return; }
		}
		Line added = session.add(l);
		if (added == null) { help("The offer can't take more lines."); return; }
		help("Offered " + l.title() + ".");
	}
	/** Why the chosen source can't be offered whole, or null if she can. */
	private String whyNotShip() { return whyNotShip(false); }
	/** forShip: asked by Offer the whole ship itself, which can dock her first. */
	private String whyNotShip(boolean forShip) {
		if (session == null) return "Open a channel first.";
		if (!session.shipsAllowed())
			return !shipsAllowed() ? "Allow trading whole ships first (Settings, General)."
					: session.peer.title + "'s career doesn't allow trading whole ships.";
		if (source == null || source.isStorage()) return "Choose one of your ships under Offering From.";
		if (source.isBoarded() && !forShip) return source.name + " is the ship at your command: Offer the whole ship docks her first.";
		if (sourceOfferedWhole()) return source.name + " is already in the offer.";
		if (Vault.get().finalBattlePending(source)) return source.name + " has a final battle still to settle.";
		if (sourceSave == null) return source.name + "'s save can't be read.";
		return Exchange.shipRefused(sourceSave.getPlayerShipBlueprintId());
	}
	private void offerShip() {
		String why = whyNotShip(true);
		if (why != null) { help(why); return; }
		if (source.isBoarded()) {
			// she has to be docked to change hands: the Space Dock's own Dock does it (FTL closed, her save back in the vault)
			if (!HomePlanet.confirmNo(this, source.name + " is the ship at your command.\nDock her and offer her whole? Her crew, weapons, systems and cargo go with her.", "Offer the whole ship")) return;
			if (!dockFromHere()) return;
			why = whyNotShip();
			if (why != null) { help(why); return; }
		} else if (!HomePlanet.confirmNo(this, "Offer " + source.name + " whole?\nHer crew, weapons, systems and cargo go with her.", "Offer the whole ship")) return;
		for (Line l : new ArrayList<Line>(session.mine())) if (l.from.equals(source.id)) session.remove(l.n); // all of it goes with her now
		Line l = Line.ship(0, sourceSave.getPlayerShipBlueprintId(), source.name, CargoBayUI.shipClass(sourceSave.getPlayerShip()));
		session.add(mark(l, false));
		help("Offered " + source.name + ", whole.");
	}
	/** Why the chosen source can't be boarded (or docked, if she's boarded) from here, or null if she can. */
	private String whyNotBoardOrDock() {
		if (source == null || source.isStorage()) return "Choose one of your ships under Offering From to board her";
		if (session != null && session.exchanging()) return "Not while the exchange is under way";
		if (!source.isBoarded() && sourceOfferedWhole()) return source.name + " is offered whole: take her back to board her";
		if (!source.isBoarded() && sourceSave == null) return source.name + "'s save can't be read";
		return null;
	}
	/**
	 * Boards the chosen ship (the ship at your command is docked), or docks her if she's the one boarded: the Space
	 * Dock's own Board and Dock, FTL's check and all. What's offered from either ship stays (the station finds a ship's
	 * save wherever she is), but your acceptance is withdrawn: accept again, once FTL is in order.
	 */
	private void boardOrDock() {
		String why = whyNotBoardOrDock();
		if (why != null) { help(why + "."); return; }
		Ship s = source;
		Ship before = Vault.get().boarded();
		if (s.isBoarded()) {
			if (!dockFromHere()) return;
			help(s.name + " is docked. No ship is at your command.");
			return;
		}
		if (!HomePlanet.confirmNo(this, "Board " + s.name + "?" + (before != null ? "\n" + before.name + " is docked." : ""), "Board Ship")) return;
		withdrawMine();
		boolean ok = parent.spaceDock.board(s); // docks the boarded ship, boards this one, redraws the Space Dock
		readSource();
		refreshAll();
		if (ok) help("Boarded " + s.name + "." + (before != null ? " " + before.name + " is docked." : ""));
	}
	/** Docks the ship at your command with the Space Dock's own Dock. False if she wasn't (it said why). */
	private boolean dockFromHere() {
		withdrawMine();
		boolean ok = parent.spaceDock.dock();
		readSource();
		refreshAll();
		return ok;
	}
	/** A ship moving withdraws your acceptance: accepting again checks FTL's state once more. */
	private void withdrawMine() {
		if (session != null && session.iAccepted()) session.accept(false);
	}
	private void takeBack() {
		if (session == null) return;
		Object v = myOffer.selectedValue();
		if (!(v instanceof Line)) return;
		session.remove(((Line) v).n);
		help("Took back " + ((Line) v).title() + ".");
	}
	private void pressAccept() {
		if (session == null) return;
		if (session.iAccepted()) { session.accept(false); help("You withdrew your acceptance."); return; }
		String why = session.whyNotAccept();
		if (why != null) { help(why); return; }
		for (Line l : session.mine()) {
			Ship s = Vault.get().byId(l.from);
			if (s != null && s.isBoarded() && !homeplanet.core.GameGuard.allows(this, "trade from " + s.name)) return;
		}
		session.accept(true);
		help(session.theyAccepted() ? "Both accepted: exchanging." : "Accepted. Waiting for " + session.peer.title + " to accept the same offer.");
	}
	private void info(Line l) {
		if (l == null) return;
		if (l.kind == Line.Kind.CREW) {
			JOptionPane.showMessageDialog(this, Crew.summary(l.crew), "Report for crewman " + l.crew.getName(), JOptionPane.PLAIN_MESSAGE, IconFactory.crewPortrait(l.crew, 48));
		} else if (l.kind.isItem()) {
			String t = ItemTooltips.tooltip(l.id);
			JOptionPane.showMessageDialog(this, new JLabel(t != null ? t : Items.title(l.id)), Items.title(l.id), JOptionPane.PLAIN_MESSAGE, IconFactory.itemIcon(l.id));
		}
	}
	private void clearOtherSelections(int keep) {
		for (int k = 0; k < 4; k++) if (k != keep) myLists[k].list.clearSelection();
	}

	// ============================================================== showing it all

	/** What was last shown to the other station, so it's sent again only when it changed. */
	private String lastShow;
	private void refreshAll() {
		available = computeAvailable();
		// your side
		sourceBtn.setText(source == null ? "No Cargo Hold" : nameOf(source));
		if (source == null || source.isStorage()) {
			myPic.setIcon(null);
			sourceNote.setText("Arrivals are stowed here.");
		} else {
			myPic.setIcon(shipIcon(sourceSave == null ? null : sourceSave.getPlayerShipBlueprintId()));
			sourceNote.setText(sourceSave == null ? "" : CargoBayUI.shipClass(sourceSave.getPlayerShip()));
		}
		for (SupplyBox b : mySupply) { b.value = availableSupply(b.idx); b.repaint(); }
		for (int k = 0; k < 4; k++) myLists[k].setRows(rows(available, k));
		// the middle
		if (session != null) {
			List<CargoParts.Row> mine = new ArrayList<CargoParts.Row>(), theirs = new ArrayList<CargoParts.Row>();
			for (Line l : session.mine()) mine.add(offerRow(l, true));
			for (Line l : session.theirs()) theirs.add(offerRow(l, false));
			myOffer.setRows(mine);
			theirOffer.setRows(theirs);
			Session.Show sh = session.shown();
			theirShipLabel.setText(sh.hold ? "from their Cargo Hold" : sh.name.isEmpty() ? "" : "from " + sh.name);
			boolean theirWhole = false;
			for (Line l : session.theirs()) if (l.kind == Line.Kind.SHIP && l.name.equals(sh.name)) theirWhole = true;
			boolean exch = session.exchanging();
			myLamp.set("YOU: " + (exch ? "EXCHANGING" : session.iAccepted() ? "ACCEPTED" : "NOT YET"), session.iAccepted());
			theirLamp.set(shortName(session.peer.title).toUpperCase() + ": " + (session.theyAccepted() ? "ACCEPTED" : "NOT YET"), session.theyAccepted());
			acceptBtn.setText(session.iAccepted() ? "Withdraw acceptance" : "Accept");
			acceptBtn.setLit(!session.iAccepted() && session.theyAccepted());
			acceptBtn.setToolTipText(session.whyNotAccept() != null ? session.whyNotAccept() : session.iAccepted() ? "Take your acceptance back" : "Accept the offer as it stands: when both stations accept, the trade goes through");
			// their side
			theirPic.setIcon(sh.hold ? null : shipIcon(sh.blueprint));
			theirNote.setText(sh.hold ? "Their Cargo Hold" : (sh.name + (sh.shipClass.isEmpty() ? "" : "  -  " + sh.shipClass) + (theirWhole ? "  -  offered whole" : "")));
			for (SupplyBox b : theirSupply) {
				b.value = 0;
				for (Line l : sh.lines) if (l.kind == SUPPLY[b.idx]) b.value = l.amount;
				b.repaint();
			}
			for (int k = 0; k < 4; k++) theirLists[k].setRows(rows(sh.lines, k));
			// show the other station what's left to offer, when it changed
			boolean whole = sourceOfferedWhole();
			StringBuilder sb = new StringBuilder(source == null ? "" : source.id).append(whole ? "|whole" : "");
			for (Line l : whole ? contents() : available) sb.append('|').append(l.kind.key).append(l.id).append(l.amount).append(l.crew == null ? "" : l.crew.getName());
			if (!sb.toString().equals(lastShow)) {
				lastShow = sb.toString();
				Session.Show me = new Session.Show();
				me.hold = source == null || source.isStorage();
				me.name = source == null ? "" : nameOf(source);
				me.shipClass = me.hold || sourceSave == null ? "" : CargoBayUI.shipClass(sourceSave.getPlayerShip());
				me.blueprint = me.hold || sourceSave == null ? "" : sourceSave.getPlayerShipBlueprintId();
				me.lines = sourceOfferedWhole() ? contents() : available; // offered whole: what comes with her
				session.show(me);
			}
		}
		updateButtons();
		stage.repaint();
	}
	private List<CargoParts.Row> rows(List<Line> lines, int cat) {
		// items grouped with a count; crew one row each
		Map<String, Integer> count = new LinkedHashMap<String, Integer>();
		Map<String, Line> first = new LinkedHashMap<String, Line>();
		List<CargoParts.Row> out = new ArrayList<CargoParts.Row>();
		for (Line l : lines) {
			int c = l.kind == Line.Kind.WEAPON ? 0 : l.kind == Line.Kind.DRONE ? 1 : l.kind == Line.Kind.AUGMENT ? 2 : l.kind == Line.Kind.CREW ? 3 : -1;
			if (c != cat) continue;
			if (c == 3) {
				out.add(new CargoParts.Row(IconFactory.crewIcon(l.crew), l.crew.getName(), Crew.raceTitle(l.crew), l, Crew.tooltip(l.crew), false));
				continue;
			}
			String k = l.id + (l.inCargo ? "|cargo" : "");
			count.put(k, count.containsKey(k) ? count.get(k) + 1 : 1);
			if (!first.containsKey(k)) first.put(k, l);
		}
		for (Map.Entry<String, Line> e : first.entrySet()) {
			Line l = e.getValue();
			int n = count.get(e.getKey());
			String note = (n > 1 ? "x" + n : "") + (l.inCargo ? (n > 1 ? "  " : "") + "in cargo" : "");
			String tip = ItemTooltips.tooltip(l.id);
			out.add(new CargoParts.Row(IconFactory.itemIcon(l.id), Items.title(l.id), note.isEmpty() ? null : note, l, tip != null ? tip : Items.title(l.id), false));
		}
		return out;
	}
	private CargoParts.Row offerRow(Line l, boolean mine) {
		javax.swing.Icon icon = l.kind == Line.Kind.CREW ? IconFactory.crewIcon(l.crew) : l.kind.isItem() ? IconFactory.itemIcon(l.id)
				: l.kind.isSupply() ? IconFactory.supplyIcon(SUPPLY_NAMES[supplyIndex(l.kind)][0]) : null;
		String note = l.refused != null ? "refused" : mine && !l.fromName.isEmpty() && !"Cargo Hold".equals(l.fromName) ? l.fromName : l.note();
		String tip = l.refused != null ? (mine ? "The other station can't take it: " : "This station can't take it: ") + l.refused
				: l.kind == Line.Kind.CREW ? Crew.tooltip(l.crew) : l.kind.isItem() ? ItemTooltips.tooltip(l.id) : null;
		return new CargoParts.Row(icon, l.title(), note, l, tip, l.refused != null);
	}
	private static int supplyIndex(Line.Kind k) { for (int i = 0; i < 4; i++) if (SUPPLY[i] == k) return i; return 0; }
	private javax.swing.Icon shipIcon(String blueprint) {
		if (blueprint == null || blueprint.isEmpty()) return null;
		ShipBlueprint bp = DataManager.get().getShips().get(blueprint);
		if (bp == null) bp = DataManager.get().getAutoShips().get(blueprint);
		if (bp == null) return null;
		BufferedImage img = parent.getResourceImage("img/ship/" + bp.getGraphicsBaseName() + "_base.png", false);
		return img == null ? null : new ImageIcon(SpaceDockUI.fitImage(img, 110, 62));
	}
	private void updateButtons() {
		boolean open = session != null && !session.isOver() && !session.exchanging();
		offerSupplyBtn.setEnabled(open && availableSupply(supplyIdx) > 0);
		for (int k = 0; k < 4; k++) offerBtns[k].setEnabled(open && myLists[k].selectedValue() != null);
		takeBackBtn.setEnabled(open && myOffer.selectedValue() != null);
		String noShip = whyNotShip(true);
		offerShipBtn.setEnabled(open && noShip == null);
		offerShipBtn.setToolTipText(noShip != null ? noShip : "Offer " + (source == null ? "her" : source.name) + " whole: her crew, weapons, systems and cargo go with her"
				+ (source != null && source.isBoarded() ? " (she's docked first)" : ""));
		String noBoard = whyNotBoardOrDock();
		boolean ship = source != null && !source.isStorage();
		boardBtn.setText(ship && source.isBoarded() ? "Dock" : "Board");
		boardBtn.setEnabled(noBoard == null);
		boardBtn.setToolTipText(noBoard != null ? noBoard : source.isBoarded() ? "Dock " + source.name + " at the Space Dock (no ship at your command)"
				: "Take command of " + source.name + (Vault.get().boarded() != null ? " (" + Vault.get().boarded().name + " is docked)" : ""));
		clearBtn.setEnabled(open && session.mine().size() > 0);
		acceptBtn.setEnabled(session != null && !session.isOver() && !session.exchanging() && (session.iAccepted() || session.whyNotAccept() == null));
		disconnectBtn.setEnabled(session != null);
		boolean chat = session != null && !session.isOver() && session.peer.chat;
		message.setEnabled(chat);
		sendBtn.setEnabled(chat);
		String chatTip = session != null && !session.peer.chat ? session.peer.title + "'s station can't show messages (an older version)" : "A message to the other commander (" + Session.SAY_MAX + " letters at most)";
		message.setToolTipText(chatTip);
		boolean idle = session == null && !hailing;
		Object f = found.selectedValue();
		hailBtn.setEnabled(idle && f instanceof Beacon.Found && ((Beacon.Found) f).compatible());
		hailAddrBtn.setEnabled(idle);
		establishBtn.setEnabled(idle);
		sourceBtn.setEnabled(session == null || !session.exchanging());
	}
	private void help(String s) { help.setText(s == null ? "" : s); }

	// ============================================================== unfinished trades (Other...)

	/**
	 * Trades a lost link left in escrow, one at a time. One this station led can be called off safely: it never told
	 * the other station to complete. One it followed is the other station's to decide; settling it by hand is for when
	 * the two stations can't talk again, and the commander has to know how it ended.
	 */
	static void reviewUnfinished(java.awt.Component owner) {
		for (Exchange.Record r : Exchange.unfinished()) {
			String what = "Trade with " + r.peerTitle + ", " + r.date + ".\n\nYou gave: " + r.outWords() + "\nYou were to receive: " + r.inWords() + "\n\n";
			try {
				if (r.leader) {
					Object[] opts = {"Call it off", "Leave it"};
					int c = JOptionPane.showOptionDialog(owner, what + "The link was lost before this station completed the trade, so " + r.peerTitle
							+ " received nothing.\nCalling it off brings back what you gave: ships to the Space Dock, the rest to the Cargo Hold. (It is also called off the next time you connect to them.)",
							"Unfinished trade", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
					if (c == 0) { Exchange.callOff(r, "called off by hand"); JOptionPane.showMessageDialog(owner, "Called off. " + r.outWords() + " came back: " + Exchange.whereTheyGo(r.out) + ".", "Unfinished trade", JOptionPane.INFORMATION_MESSAGE); }
				} else {
					Object[] opts = {"Leave it", "Finish it", "Call it off"};
					int c = JOptionPane.showOptionDialog(owner, what + r.peerTitle + "'s station led this trade, and only it knows whether it went through.\n"
							+ "The simplest way to settle it: connect to " + r.peerTitle + " with Long Range Comm. It settles by itself.\n\n"
							+ "If that can't happen, ask them how it ended:\n  Finish it: only if it went through at their end (you receive what was offered).\n"
							+ "  Call it off: only if it didn't (what you gave comes back).",
							"Unfinished trade", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
					if (c == 1 && HomePlanet.confirmNo(owner, "Finish the trade with " + r.peerTitle + "?\nYou receive " + r.inWords() + ".", "Unfinished trade")) {
						Exchange.complete(r);
					} else if (c == 2 && HomePlanet.confirmNo(owner, "Call off the trade with " + r.peerTitle + "?\n" + r.outWords() + " comes back: " + Exchange.whereTheyGo(r.out) + ".", "Unfinished trade")) {
						Exchange.callOff(r, "called off by hand");
					}
				}
			} catch (IOException e) {
				HomePlanet.showErrorDialog("The Home Planet Station could not settle the trade with " + r.peerTitle + ":\n" + e.getMessage());
			}
		}
	}

	// ============================================================== small parts

	/** A supply box: icon, name, the amount there is to offer. */
	private static final class SupplyBox extends CargoParts.Cell {
		final int idx;
		int value;
		SupplyBox(int idx) { this.idx = idx; }
		@Override protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			javax.swing.Icon ic = IconFactory.supplyIcon(SUPPLY_NAMES[idx][0]);
			if (ic != null) ic.paintIcon(this, g, 8, 6);
			CargoParts.text(g, SUPPLY_NAMES[idx][1], FtlFont.BODY, CargoParts.DIM, 28, 7);
			String v = "" + value;
			CargoParts.text(g, v, FtlFont.MENU, selected ? CargoParts.GOLD : CargoParts.TEXT, getWidth() - 8 - CargoParts.width(v, FtlFont.MENU), 20);
		}
	}
	/** One side's acceptance: a lamp and its words. */
	private static final class Lamp extends JComponent {
		private String text = "";
		private boolean lit;
		void set(String t, boolean on) { text = t; lit = on; repaint(); }
		@Override protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Color c = MenuTheme.GREEN;
			CargoParts.paintBox(g, 0, 0, getWidth(), getHeight(), lit ? c : CargoParts.BOX_LINE);
			int d = 14, y = (getHeight() - d) / 2;
			g.setColor(lit ? c : new Color(60, 66, 70));
			g.fillOval(10, y, d, d);
			g.setColor(Color.black);
			g.drawOval(10, y, d, d);
			CargoParts.text(g, FtlFont.BODY.fit(text, getWidth() - 40), FtlFont.BODY, lit ? c : CargoParts.DIM, 32, y);
			g.dispose();
		}
	}
	/** The orange line that says what just changed. */
	private static final class NoticeStrip extends JComponent {
		private String text = "";
		void set(String t) { text = t == null ? "" : t; repaint(); }
		@Override public String getToolTipText(MouseEvent e) { return text.isEmpty() ? null : text; }
		NoticeStrip() { setToolTipText(""); }
		@Override protected void paintComponent(Graphics g0) {
			if (text.isEmpty()) return;
			Graphics2D g = (Graphics2D) g0.create();
			CargoParts.paintBox(g, 0, 0, getWidth(), getHeight(), CargoParts.ORANGE);
			String s = FtlFont.BODY.fit(text, getWidth() - 16);
			CargoParts.text(g, s, FtlFont.BODY, CargoParts.ORANGE, (getWidth() - CargoParts.width(s, FtlFont.BODY)) / 2, (getHeight() - 14) / 2);
			g.setStroke(new BasicStroke(1f));
			g.dispose();
		}
	}
}
