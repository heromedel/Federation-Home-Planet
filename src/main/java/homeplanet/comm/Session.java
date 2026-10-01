package homeplanet.comm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A trade over an open channel: both offers, who has accepted what, and the exchange once both have.
 *
 * <p>Each side owns its own lines and numbers its versions of them (revisions). Accepting names both revisions, so a
 * change on either side withdraws every acceptance. When both accept the same pair, the station that hailed (the
 * leader) takes its goods into escrow and asks the other to do the same; once the other is ready, the leader
 * completes and tells it to complete (see {@link Exchange}). Everything here runs on the event thread.
 */
public final class Session implements Channel.Listener {
	private static final Logger log = LoggerFactory.getLogger(Session.class);
	/**
	 * The shape of Long Range Comm.'s messages and ships' packages. Stations must match on this, not on the program's
	 * version: a Laser Cannon is a Laser Cannon in any version. Each side ignores fields and files it doesn't know, so
	 * adding one is safe; bump this only when an older station would trade wrongly (a new step, a field it must read).
	 */
	public static final int PROTOCOL = 2; // 2: custom ships travel with their papers

	/** What the screen hears about. All on the event thread. */
	public interface View {
		/** The offers, the acceptances or what the other station shows changed. */
		void changed();
		/** A line for the notice strip (an offer changed, acceptance withdrawn, a trade settled). */
		void notice(String text);
		/** Something went wrong that the commander should read (a dialog). */
		void problem(String text);
		/** A trade went through, or was called off: the sources are read again. */
		void settled(Exchange.Record r);
		/** The channel closed. */
		void ended(String why);
	}

	/** The other station, as its hello describes it. */
	public static final class Peer {
		public String station, title, version, ship;
		/** Its Long Range Comm. protocol (see {@link Session#PROTOCOL}). */
		public int protocol;
		/** Its mode: the vault's slot (sandbox, easy, normal, hard, custom). */
		public String mode = homeplanet.vault.Vault.SANDBOX;
		public boolean ships, anyLevel;
		public boolean immersive() { return !homeplanet.vault.Vault.SANDBOX.equals(mode); }
		/** "Sandbox Mode", "Immersive Hard". */
		public String modeTitle() { return homeplanet.vault.Vault.title(mode); }
	}

	/** What the other station shows of the ship (or hold) it offers from. */
	public static final class Show {
		public String name = "", shipClass = "", blueprint = "";
		public boolean hold;
		public List<Line> lines = new ArrayList<Line>();
	}

	public final Channel channel;
	public final boolean leader;
	public final Peer peer;
	private final String self;
	private View view;
	private final List<Line> mine = new ArrayList<Line>(), theirs = new ArrayList<Line>();
	private int myRev = 0, theirRev = 0, nextN = 1;
	/** My acceptance (of these revisions), and theirs (their revision, then mine as they saw it). */
	private boolean iAccept, theyAccept;
	private int theyMine, theyYours;
	private Show shown = new Show();
	private Exchange.Record pending;
	private boolean exchanging, over;
	private boolean ships;

	public Session(Channel channel, boolean leader, Peer peer, String self, boolean shipsAllowedHere) {
		this.channel = channel;
		this.leader = leader;
		this.peer = peer;
		this.self = self;
		this.ships = shipsAllowedHere && peer.ships;
	}

	/** Starts listening, settles any trade the last link left unfinished, and sends the empty offer. */
	public void start(View v) {
		view = v;
		channel.start(this);
		resolveUnfinished();
		sendOffer();
	}

	// ---- hellos ----

	/** This station's hello: who it is, its mode (Sandbox, or an Immersive career's level) and what it allows. */
	public static Wire.Msg hello(String version, String station, String title, String ship, String mode, boolean ships, boolean anyLevel) {
		return new Wire.Msg("HELLO").put("protocol", PROTOCOL).put("version", version).put("station", station).put("title", title)
				.put("ship", ship == null ? "" : ship).put("mode", mode).put("ships", ships).put("anyLevel", anyLevel);
	}
	/** The other station from its hello; Garbled if it isn't one. */
	public static Peer peerOf(Wire.Msg m) throws Wire.Garbled {
		if (!m.type.equals("HELLO")) throw new Wire.Garbled("expected a hello, got " + m.type);
		Peer p = new Peer();
		p.station = m.get("station");
		if (!p.station.matches("[0-9a-f]{16}")) throw new Wire.Garbled("station id");
		p.title = Line.text(m.get("title"), 48);
		if (p.title.isEmpty()) p.title = "An unnamed commander";
		p.version = Line.text(m.get("version"), 16);
		p.ship = Line.text(m.get("ship"), 64);
		p.mode = m.get("mode");
		if (!java.util.Arrays.asList(homeplanet.vault.Vault.SLOTS).contains(p.mode)) throw new Wire.Garbled("mode " + Line.text(p.mode, 16));
		p.ships = m.flag("ships");
		p.anyLevel = m.flag("anyLevel");
		try { p.protocol = Integer.parseInt(m.get("protocol").trim()); } catch (NumberFormatException e) { throw new Wire.Garbled("protocol"); }
		return p;
	}
	/**
	 * Why these two stations can't trade (shown to both), or null if they can. Sandbox trades with Sandbox, Immersive
	 * with Immersive; two careers of different levels when both allow trading with any level.
	 */
	public static String incompatible(Peer p, String myVersion, String myStation, String myMode, boolean myAnyLevel) {
		if (p.station.equals(myStation)) return "That is this station's own signal.";
		if (p.protocol != PROTOCOL)
			return (p.protocol < PROTOCOL ? p.title + "'s station uses an older Long Range Comm. (Federation Home Planet " + p.version + "; this one is " + myVersion + "). "
					: "This station uses an older Long Range Comm. than " + p.title + "'s (Federation Home Planet " + myVersion + "; theirs is " + p.version + "). ")
					+ "One of you needs to update to trade.";
		boolean meImmersive = !homeplanet.vault.Vault.SANDBOX.equals(myMode);
		if (p.immersive() != meImmersive)
			return p.title + "'s station is in " + p.modeTitle() + "; this one is in " + homeplanet.vault.Vault.title(myMode)
					+ ". Sandbox fleets trade only with Sandbox fleets, and Immersive careers only with Immersive careers.";
		if (meImmersive && !p.mode.equals(myMode) && !(myAnyLevel && p.anyLevel))
			return p.title + "'s career is " + p.modeTitle() + "; this one is " + homeplanet.vault.Vault.title(myMode)
					+ ". Careers of different levels trade only when both allow trading with any Immersive level (Settings, General).";
		return null;
	}

	// ---- what the screen reads ----

	public List<Line> mine() { return Collections.unmodifiableList(mine); }
	public List<Line> theirs() { return Collections.unmodifiableList(theirs); }
	public Show shown() { return shown; }
	public boolean iAccepted() { return iAccept; }
	public boolean theyAccepted() { return theyAccept && theyMine == theirRev && theyYours == myRev; }
	public boolean exchanging() { return exchanging; }
	public boolean shipsAllowed() { return ships; }
	public boolean isOver() { return over; }
	/** Why Accept can't be pressed now, or null. */
	public String whyNotAccept() {
		if (over) return "The channel is closed.";
		if (exchanging) return "The exchange is under way.";
		if (mine.isEmpty() && theirs.isEmpty()) return "Nothing is on offer yet.";
		for (Line l : theirs) if (l.refused != null) return "The Home Planet Station can't take " + l.title() + ": " + l.refused;
		for (Line l : mine) if (l.refused != null) return peer.title + "'s station can't take " + l.title() + ": " + l.refused;
		return null;
	}

	// ---- changing my offer ----

	/** Adds a line (numbered here) to my offer. */
	public Line add(Line l) {
		if (over || exchanging || mine.size() >= Line.MAX_LINES) return null;
		Line n = new Line(nextN++, l.kind, l.id, l.amount, l.crew, l.name, l.shipClass);
		n.from = l.from; n.fromName = l.fromName; n.inCargo = l.inCargo;
		mine.add(n);
		changedMine();
		return n;
	}
	public void remove(int n) {
		if (over || exchanging) return;
		for (int i = 0; i < mine.size(); i++) if (mine.get(i).n == n) { mine.remove(i); changedMine(); return; }
	}
	public void clearMine() {
		if (over || exchanging || mine.isEmpty()) return;
		mine.clear();
		changedMine();
	}
	private void changedMine() {
		myRev++;
		boolean was = iAccept || theyAccepted();
		iAccept = false;
		theyAccept = false;
		sendOffer();
		if (was) view.notice("You changed the offer: every acceptance is withdrawn.");
		view.changed();
	}
	private void sendOffer() {
		Wire.Msg m = new Wire.Msg("OFFER").put("rev", myRev);
		Line.writeLines(m, mine);
		channel.trySend(m);
	}
	/** Shows the other station what the chosen ship (or the hold) has to offer. */
	public void show(Show s) {
		Wire.Msg m = new Wire.Msg("SHOW").put("name", s.name).put("class", s.shipClass).put("blueprint", s.blueprint).put("hold", s.hold);
		List<Line> ls = s.lines.size() > Line.MAX_SHOWN ? s.lines.subList(0, Line.MAX_SHOWN) : s.lines;
		Line.writeLines(m, ls);
		channel.trySend(m);
	}

	/** Accepts the offer as it stands, or takes acceptance back. */
	public void accept(boolean on) {
		if (over || exchanging) return;
		if (on && whyNotAccept() != null) return;
		iAccept = on;
		channel.trySend(new Wire.Msg("ACCEPT").put("on", on).put("mine", myRev).put("yours", theirRev));
		view.changed();
		maybeExchange();
	}

	/** Ends the session, telling the other station why. */
	public void close(String why) {
		if (over) return;
		over = true;
		channel.close(why);
		afterLoss();
	}

	// ---- messages ----

	/**
	 * For the regression harness only (null otherwise): the station "crashes" when this message type arrives, before
	 * handling it ("PREPARE") or after ("PREPARE+"): the link is cut and nothing more runs, as if the program had died.
	 */
	public static volatile String crashAt = null;

	public void received(Wire.Msg m) {
		if (over) return;
		if (crashAt != null && crashAt.equals(m.type)) { crash(); return; }
		try {
			String t = m.type;
			if (t.equals("OFFER")) onOffer(m);
			else if (t.equals("SHOW")) onShow(m);
			else if (t.equals("CANT")) onCant(m);
			else if (t.equals("ACCEPT")) onAccept(m);
			else if (t.equals("PREPARE")) onPrepare(m);
			else if (t.equals("READY")) onReady(m);
			else if (t.equals("REFUSE")) onRefuse(m);
			else if (t.equals("COMMIT")) onCommit(m);
			else if (t.equals("ABORT")) onAbort(m);
			else if (t.equals("DONE")) { /* the follower has it: nothing more to do */ }
			else if (t.equals("ASK")) onAsk(m);
			else if (t.equals("OUTCOME")) onOutcome(m);
			else log.debug("Ignored a {} message", t);
			if (crashAt != null && crashAt.equals(m.type + "+")) crash();
		} catch (Wire.Garbled e) {
			log.warn("Garbled {} from {}: {}", m.type, peer.title, e.getMessage());
			close("The other station sent a garbled transmission, and the channel was closed.");
			view.ended("The Home Planet Station received a garbled transmission from " + peer.title + " and closed the channel.");
		}
	}

	private void crash() {
		over = true;
		pending = null;
		channel.kill();
		view.ended("crashed (harness)");
	}

	public void closed(String why) {
		if (over) return;
		over = true;
		afterLoss();
		view.ended(why == null || why.isEmpty() ? "The link to " + peer.title + " was lost." : why);
	}
	/** The link is gone: a trade the leader had taken into escrow, unanswered, is called off at once; the follower's waits. */
	private void afterLoss() {
		if (pending != null && Exchange.ESCROW.equals(pending.state)) {
			if (leader) {
				try {
					Exchange.callOff(pending, "the link was lost before " + peer.title + "'s station was ready");
					view.settled(pending);
				} catch (IOException e) {
					view.problem("The trade with " + peer.title + " was cut off, and The Home Planet Station could not return your goods:\n" + e.getMessage()
							+ "\n\nThe trade's record is kept: it will be called off the next time you connect, or in Other... at the Space Dock.");
				}
			} else {
				view.problem("The link to " + peer.title + " was lost in the middle of the exchange. What you gave is held in escrow until the two stations talk again:"
						+ " connect to " + peer.title + " to finish it, or see Other... at the Space Dock.");
			}
		}
		pending = null;
		exchanging = false;
	}

	private void onOffer(Wire.Msg m) throws Wire.Garbled {
		int rev = m.num("rev", 0, Integer.MAX_VALUE);
		List<Line> now = Line.readLines(m);
		List<String> added = new ArrayList<String>(), removed = new ArrayList<String>();
		Map<Integer, Line> before = new HashMap<Integer, Line>();
		for (Line l : theirs) before.put(l.n, l);
		for (Line l : now) if (before.remove(l.n) == null) added.add(l.title());
		for (Line l : before.values()) removed.add(l.title());
		boolean was = iAccept || theyAccepted();
		theirs.clear();
		theirs.addAll(now);
		theirRev = rev;
		iAccept = false;
		theyAccept = false;
		// what this station can't take: the lines show grey, and the other station is told
		Wire.Msg cant = new Wire.Msg("CANT").put("rev", rev);
		int k = 0;
		for (Line l : theirs) {
			l.refused = Exchange.refuses(l);
			if (l.kind == Line.Kind.SHIP && l.refused == null && !ships) l.refused = "Whole ships need \"allow trading whole ships\" on at both Immersive careers (Settings, General)";
			if (l.refused != null) cant.put("n" + k, l.n).put("why" + k++, l.refused);
		}
		cant.put("count", k);
		channel.trySend(cant);
		if (!added.isEmpty() || !removed.isEmpty()) {
			String who = shortName(peer.title);
			String s = !added.isEmpty() ? who + " added " + Exchange.wordsOf(added) : who + " took back " + Exchange.wordsOf(removed);
			if (!added.isEmpty() && !removed.isEmpty()) s += " and took back " + Exchange.wordsOf(removed);
			view.notice(s + (was ? ", so your acceptance was withdrawn." : "."));
		}
		view.changed();
	}
	private void onCant(Wire.Msg m) throws Wire.Garbled {
		int rev = m.num("rev", 0, Integer.MAX_VALUE);
		if (rev != myRev) return; // about an offer since changed
		int count = m.num("count", 0, Line.MAX_LINES);
		for (Line l : mine) l.refused = null;
		for (int i = 0; i < count; i++) {
			int n = m.num("n" + i, 0, Integer.MAX_VALUE);
			for (Line l : mine) if (l.n == n) l.refused = Line.text(m.get("why" + i), 160);
		}
		if (count > 0) iAccept = false;
		view.changed();
	}
	private void onShow(Wire.Msg m) throws Wire.Garbled {
		Show s = new Show();
		s.name = Line.text(m.get("name"), 64);
		s.shipClass = Line.text(m.get("class"), 64);
		s.blueprint = Line.text(m.get("blueprint"), 128);
		s.hold = m.flag("hold");
		s.lines = Line.readLines(m, Line.MAX_SHOWN);
		shown = s;
		view.changed();
	}
	private void onAccept(Wire.Msg m) throws Wire.Garbled {
		theyAccept = m.flag("on");
		theyMine = m.num("mine", 0, Integer.MAX_VALUE);
		theyYours = m.num("yours", 0, Integer.MAX_VALUE);
		if (theyAccept && !theyAccepted()) theyAccept = false; // for an offer since changed
		view.changed();
		if (theyAccepted() && !iAccept) view.notice(shortName(peer.title) + " has accepted the offer.");
		maybeExchange();
	}

	// ---- the exchange ----

	private boolean bothAccept() { return iAccept && theyAccepted() && whyNotAccept() == null; }

	/** The leader starts the exchange once both have accepted the same offer. */
	private void maybeExchange() {
		if (!leader || exchanging || over || !bothAccept()) return;
		exchanging = true;
		view.changed();
		String id = Exchange.newId(self);
		try {
			pending = Exchange.escrow(id, true, peer.station, peer.title, new ArrayList<Line>(mine), new ArrayList<Line>(theirs));
		} catch (IOException e) {
			exchanging = false;
			iAccept = false;
			channel.trySend(new Wire.Msg("ACCEPT").put("on", false).put("mine", myRev).put("yours", theirRev));
			view.problem("The trade couldn't go ahead:\n" + e.getMessage());
			view.changed();
			return;
		}
		Wire.Msg prep = new Wire.Msg("PREPARE").put("trade", id).put("mine", myRev).put("yours", theirRev);
		try {
			withShips(prep, pending);
		} catch (IOException e) {
			Exchange.Record r = pending;
			try { Exchange.callOff(r, "her papers couldn't be read: " + e.getMessage()); } catch (IOException again) { log.error("Could not call off trade " + r.id, again); }
			finish(r);
			view.problem("The trade couldn't go ahead:\n" + e.getMessage());
			return;
		}
		channel.trySend(prep);
		view.notice("Both stations accepted. Exchanging...");
	}
	/** Puts this station's ships in the trade into a message, as their packages. */
	private static void withShips(Wire.Msg m, Exchange.Record r) throws IOException {
		int k = 0;
		for (Map.Entry<Integer, byte[]> e : Exchange.outPackages(r).entrySet()) { m.put("ship" + k++, e.getKey()); m.blob(e.getValue()); }
		m.put("ships", k);
	}
	/** Keeps the other station's ships from a message; every ship line must have hers. Throws a reason if not. */
	private static void takeShips(Wire.Msg m, Exchange.Record r) throws IOException {
		int k = m.num("ships", 0, Line.MAX_LINES);
		if (k != m.blobs().size()) throw new Wire.Garbled("ships and blobs differ");
		java.util.Set<Integer> got = new java.util.HashSet<Integer>();
		for (int i = 0; i < k; i++) {
			int n = m.num("ship" + i, 0, Integer.MAX_VALUE);
			Exchange.keepIncoming(r, n, m.blobs().get(i));
			got.add(n);
		}
		for (Line l : r.in) if (l.kind == Line.Kind.SHIP && !got.contains(l.n)) throw new IOException(l.name + "'s papers didn't arrive");
	}
	private void onPrepare(Wire.Msg m) throws Wire.Garbled {
		String id = m.get("trade");
		if (leader || !id.startsWith(peer.station + "-") || !id.matches("[0-9a-z-]{1,64}")) throw new Wire.Garbled("prepare");
		int theirs = m.num("mine", 0, Integer.MAX_VALUE), mineSeen = m.num("yours", 0, Integer.MAX_VALUE);
		if (exchanging || theirs != theirRev || mineSeen != myRev || !bothAccept()) {
			channel.trySend(new Wire.Msg("REFUSE").put("trade", id).put("why", "The offer changed before the exchange."));
			return;
		}
		exchanging = true;
		view.changed();
		try {
			pending = Exchange.escrow(id, false, peer.station, peer.title, new ArrayList<Line>(mine), new ArrayList<Line>(this.theirs));
		} catch (IOException e) {
			exchanging = false;
			iAccept = false;
			channel.trySend(new Wire.Msg("REFUSE").put("trade", id).put("why", e.getMessage()));
			view.problem("The trade couldn't go ahead:\n" + e.getMessage());
			view.changed();
			return;
		}
		Wire.Msg ready = new Wire.Msg("READY").put("trade", id);
		try {
			takeShips(m, pending);
			withShips(ready, pending);
		} catch (IOException e) {
			Exchange.Record r = pending;
			try { Exchange.callOff(r, e.getMessage()); } catch (IOException again) { log.error("Could not call off trade " + r.id, again); }
			channel.trySend(new Wire.Msg("REFUSE").put("trade", id).put("why", e.getMessage()));
			finish(r);
			view.problem("The trade couldn't go ahead:\n" + e.getMessage());
			return;
		}
		channel.trySend(ready);
	}
	private void onReady(Wire.Msg m) {
		if (!leader || pending == null || !pending.id.equals(m.get("trade"))) return;
		Exchange.Record r = pending;
		try {
			takeShips(m, r);
		} catch (IOException e) {
			try { Exchange.callOff(r, e.getMessage()); } catch (IOException again) { log.error("Could not call off trade " + r.id, again); }
			channel.trySend(new Wire.Msg("ABORT").put("trade", r.id).put("why", peerSide("refused a ship: " + e.getMessage())));
			finish(r);
			view.problem("The trade was called off:\n" + e.getMessage());
			return;
		}
		try {
			Exchange.complete(r);
		} catch (IOException e) {
			// this station couldn't take delivery: call it off instead, and the other station follows
			log.error("Could not complete trade " + r.id, e);
			try { Exchange.callOff(r, "this station could not take delivery: " + e.getMessage()); } catch (IOException again) { log.error("Could not call off trade " + r.id, again); }
			channel.trySend(new Wire.Msg("ABORT").put("trade", r.id).put("why", peerSide("could not take delivery")));
			finish(r);
			view.problem("The Home Planet Station could not take delivery, so the trade was called off:\n" + e.getMessage());
			return;
		}
		channel.trySend(new Wire.Msg("COMMIT").put("trade", r.id));
		finish(r);
		view.notice(completeNotice(r));
	}
	/** "Trade complete: ..." with where what arrived went. */
	private static String completeNotice(Exchange.Record r) {
		String where = Exchange.whereTheyGo(r.in);
		return where.isEmpty() ? "Trade complete." : where.startsWith("in the") ? "Trade complete: arrivals are " + where + "." : "Trade complete: " + where + ".";
	}
	private void onRefuse(Wire.Msg m) {
		if (!leader || pending == null || !pending.id.equals(m.get("trade"))) return;
		Exchange.Record r = pending;
		String why = Line.text(m.get("why"), 300);
		try {
			Exchange.callOff(r, peer.title + "'s station refused: " + why);
		} catch (IOException e) {
			log.error("Could not call off trade " + r.id, e);
			view.problem("The trade was refused, and The Home Planet Station could not return your goods:\n" + e.getMessage()
					+ "\n\nThe trade's record is kept: see Other... at the Space Dock.");
		}
		finish(r);
		view.notice(shortName(peer.title) + "'s station couldn't go ahead: " + why + (r.out.isEmpty() ? "" : " Yours came back: " + Exchange.whereTheyGo(r.out) + "."));
	}
	private void onCommit(Wire.Msg m) {
		if (leader || pending == null || !pending.id.equals(m.get("trade"))) return;
		Exchange.Record r = pending;
		try {
			Exchange.complete(r);
		} catch (IOException e) {
			log.error("Could not complete trade " + r.id, e);
			view.problem("The trade went through at " + peer.title + "'s station, but The Home Planet Station could not take delivery:\n" + e.getMessage()
					+ "\n\nThe trade's record is kept: finish it in Other... at the Space Dock once the problem is fixed.");
			pending = null;
			exchanging = false;
			return;
		}
		channel.trySend(new Wire.Msg("DONE").put("trade", r.id));
		finish(r);
		view.notice(completeNotice(r));
	}
	private void onAbort(Wire.Msg m) {
		if (leader || pending == null || !pending.id.equals(m.get("trade"))) return;
		Exchange.Record r = pending;
		String why = Line.text(m.get("why"), 300);
		try {
			Exchange.callOff(r, why);
		} catch (IOException e) {
			log.error("Could not call off trade " + r.id, e);
			view.problem("The trade was called off, and The Home Planet Station could not return your goods:\n" + e.getMessage() + "\n\nSee Other... at the Space Dock.");
		}
		finish(r);
		view.notice("The trade was called off: " + why + (r.out.isEmpty() ? "" : " Yours came back: " + Exchange.whereTheyGo(r.out) + "."));
	}
	private String peerSide(String what) { return peer.title + "'s station " + what + "."; }
	/** After a trade settles: both offers start again empty. */
	private void finish(Exchange.Record r) {
		pending = null;
		exchanging = false;
		iAccept = false;
		theyAccept = false;
		mine.clear();
		theirs.clear();
		myRev++;
		sendOffer();
		view.settled(r);
		view.changed();
	}

	// ---- trades a lost link left unfinished ----

	/**
	 * Any trade with this station still in escrow: one this station led is called off now (it never decided, or the
	 * other station would have heard); for one it followed, it asks the leader what became of it.
	 */
	private void resolveUnfinished() {
		for (Exchange.Record r : Exchange.unfinished()) {
			if (!r.peerStation.equals(peer.station)) continue;
			if (r.leader) {
				try {
					Exchange.callOff(r, "the link was lost before the trade was settled");
					view.settled(r);
					channel.trySend(new Wire.Msg("OUTCOME").put("trade", r.id).put("outcome", Exchange.CALLED_OFF));
					view.notice("An unfinished trade with " + shortName(peer.title) + " was called off.");
				} catch (IOException e) {
					view.problem("The Home Planet Station could not settle an unfinished trade with " + peer.title + ":\n" + e.getMessage());
				}
			} else {
				channel.trySend(new Wire.Msg("ASK").put("trade", r.id));
			}
		}
	}
	private void onAsk(Wire.Msg m) {
		String id = m.get("trade");
		if (!id.startsWith(self + "-")) return; // only the leader answers, and this station led only trades named after it
		Exchange.Record r = Exchange.find(id);
		String outcome = Exchange.CALLED_OFF;
		if (r != null && r.peerStation.equals(peer.station) && r.leader) {
			if (Exchange.ESCROW.equals(r.state)) {
				try { Exchange.callOff(r, "the link was lost before the trade was settled"); view.settled(r); }
				catch (IOException e) { view.problem("The Home Planet Station could not settle an unfinished trade with " + peer.title + ":\n" + e.getMessage()); return; }
			}
			outcome = r.state;
		}
		channel.trySend(new Wire.Msg("OUTCOME").put("trade", id).put("outcome", outcome));
	}
	private void onOutcome(Wire.Msg m) {
		Exchange.Record r = Exchange.find(m.get("trade"));
		if (r == null || r.leader || !r.peerStation.equals(peer.station) || !Exchange.ESCROW.equals(r.state)) return;
		String outcome = m.get("outcome");
		try {
			if (outcome.equals(Exchange.DONE)) {
				Exchange.complete(r);
				view.notice("The unfinished trade with " + shortName(peer.title) + " is complete.");
			} else if (outcome.equals(Exchange.CALLED_OFF)) {
				Exchange.callOff(r, peer.title + "'s station called it off");
				view.notice("The unfinished trade with " + shortName(peer.title) + " was called off.");
			} else {
				return;
			}
			view.settled(r);
		} catch (IOException e) {
			view.problem("The Home Planet Station could not settle an unfinished trade with " + peer.title + ":\n" + e.getMessage());
		}
	}

	/** "Vex" from "Captain Vex": the name alone reads better in a running notice. */
	static String shortName(String title) {
		for (String r : homeplanet.parser.UnlockGrants.RANKS) if (title.startsWith(r + " ")) return title.substring(r.length() + 1);
		return title;
	}
}
