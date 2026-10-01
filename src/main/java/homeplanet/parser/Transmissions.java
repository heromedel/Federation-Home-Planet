package homeplanet.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.SystemType;
import net.blerf.ftl.xml.ShipBlueprint;

import homeplanet.core.HistoryLog;
import homeplanet.core.HomePlanet;
import homeplanet.core.SafeFiles;
import homeplanet.model.Items;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * Transmissions from The Federation Home Planet (Immersive Notifications): the messages, kept per fleet in
 * transmissions.xml, and the rewards some carry, claimed into the Cargo Hold. The texts and rewards are in the
 * resource transmissions.txt. {@link #check} sends what's due; each message is sent once.
 */
public final class Transmissions {
	private static final Logger log = LoggerFactory.getLogger(Transmissions.class);
	private Transmissions() { }

	/** A message as the resource file has it. */
	static final class Template {
		String key, from = "", subject = "", reward = "";
		/** Reply chains: the replies offered ("text -> next-key 5-10 | ..."), the letter that follows by itself some beacons later ("next-key 5-7"), a price for the reward ("scrap 25"), and something done when it's sent ("derelict"). */
		String replies = "", then = "", cost = "", action = "";
		final StringBuilder body = new StringBuilder();
	}
	/** A letter due some beacons from now (the answer to a reply, or the next of a chain). */
	static final class Pending {
		String template, name;
		int due;
	}
	/** A message sent. */
	public static final class Message {
		public String key, date, from, subject, body, reward;
		public boolean read, claimed;
		/** Stored in the Archive tab, out of the inbox. */
		public boolean archived;
		/** What was claimed, in words (after a claim). */
		public String claimedWhat = "";
		/** The replies it offers (as the template has them), the one chosen, and the reward's price. */
		public String replies = "", replied = "", cost = "";
		public boolean hasReward() { return reward != null && !reward.trim().isEmpty(); }
		/** A commission order: its free ship waits in Commission. */
		public boolean isOrder() { return key.startsWith("order:") || key.startsWith("empty") || key.startsWith("promo:"); }
	}

	// ---- the texts ----

	private static Map<String, Template> templates;
	static synchronized Map<String, Template> templates() {
		if (templates != null) return templates;
		templates = new LinkedHashMap<String, Template>();
		InputStream in = Transmissions.class.getResourceAsStream("/homeplanet/resource/transmissions.txt");
		if (in == null) { log.error("transmissions.txt is missing from the program"); return templates; }
		try {
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			Template t = null;
			boolean inBody = false;
			String line;
			while ((line = r.readLine()) != null) {
				if (line.startsWith("#") && t == null) continue;
				if (line.startsWith("== ")) {
					t = new Template();
					t.key = line.substring(3).trim();
					templates.put(t.key, t);
					inBody = false;
					continue;
				}
				if (t == null) continue;
				if (!inBody) {
					if (line.trim().isEmpty()) { inBody = true; continue; }
					int colon = line.indexOf(':');
					if (colon < 0) continue;
					String k = line.substring(0, colon).trim(), v = line.substring(colon + 1).trim();
					if (k.equals("from")) t.from = v;
					else if (k.equals("subject")) t.subject = v;
					else if (k.equals("reward")) t.reward = v;
					else if (k.equals("replies")) t.replies = v;
					else if (k.equals("then")) t.then = v;
					else if (k.equals("cost")) t.cost = v;
					else if (k.equals("action")) t.action = v;
					continue;
				}
				t.body.append(line).append('\n');
			}
			r.close();
		} catch (IOException e) {
			log.error("Could not read transmissions.txt", e);
		}
		return templates;
	}

	// ---- the inbox (per fleet) ----

	static File file() { return new File(Vault.get().root, "transmissions.xml"); }
	private static boolean emptyOpen = false; // an empty-shipyard order is out while the shipyard stays empty (read with load())
	private static boolean strandedOpen = false; // the Liaison's stranded letter is out while the fleet stays without a ship (read with load())
	/** Letters due later (read with load(), written with save()). */
	private static List<Pending> pending = new ArrayList<Pending>();

	public static synchronized List<Message> load() {
		List<Message> out = new ArrayList<Message>();
		emptyOpen = false;
		strandedOpen = false;
		pending = new ArrayList<Pending>();
		File f = file();
		if (!f.isFile()) return out;
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
			emptyOpen = "true".equals(doc.getDocumentElement().getAttribute("emptyOpen"));
			strandedOpen = "true".equals(doc.getDocumentElement().getAttribute("strandedOpen"));
			NodeList ns = doc.getElementsByTagName("message");
			for (int i = 0; i < ns.getLength(); i++) {
				Element e = (Element) ns.item(i);
				Message m = new Message();
				m.key = e.getAttribute("key");
				m.date = e.getAttribute("date");
				m.from = e.getAttribute("from");
				m.subject = e.getAttribute("subject");
				m.reward = e.getAttribute("reward");
				m.read = "true".equals(e.getAttribute("read"));
				m.claimed = "true".equals(e.getAttribute("claimed"));
				m.archived = "true".equals(e.getAttribute("archived"));
				m.claimedWhat = e.getAttribute("claimedWhat");
				m.replies = e.getAttribute("replies");
				m.replied = e.getAttribute("replied");
				m.cost = e.getAttribute("cost");
				m.body = e.getTextContent();
				out.add(m);
			}
			NodeList ps = doc.getElementsByTagName("pending");
			for (int i = 0; i < ps.getLength(); i++) {
				Element e = (Element) ps.item(i);
				Pending p = new Pending();
				p.template = e.getAttribute("template");
				p.name = e.getAttribute("name");
				try { p.due = Integer.parseInt(e.getAttribute("due")); } catch (NumberFormatException x) { continue; }
				pending.add(p);
			}
		} catch (Exception e) {
			log.error("Could not read " + f, e);
		}
		return out;
	}
	public static synchronized void save(List<Message> all) throws IOException {
		StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
		sb.append("<!-- Transmissions from The Federation Home Planet. Federation Home Planet rewrites this file. -->\r\n");
		sb.append("<transmissions emptyOpen=\"").append(emptyOpen).append("\" strandedOpen=\"").append(strandedOpen).append("\">\r\n");
		for (Message m : all) {
			sb.append("\t<message key=\"").append(XmlText.attr(m.key)).append("\" date=\"").append(XmlText.attr(m.date))
					.append("\" from=\"").append(XmlText.attr(m.from)).append("\" subject=\"").append(XmlText.attr(m.subject))
					.append("\" reward=\"").append(XmlText.attr(m.reward)).append("\" read=\"").append(m.read)
					.append("\" claimed=\"").append(m.claimed).append("\" archived=\"").append(m.archived).append("\" claimedWhat=\"").append(XmlText.attr(m.claimedWhat))
					.append("\" replies=\"").append(XmlText.attr(m.replies)).append("\" replied=\"").append(XmlText.attr(m.replied)).append("\" cost=\"").append(XmlText.attr(m.cost)).append("\">")
					.append(XmlText.text(m.body)).append("</message>\r\n");
		}
		for (Pending p : pending)
			sb.append("\t<pending template=\"").append(XmlText.attr(p.template)).append("\" due=\"").append(p.due).append("\" name=\"").append(XmlText.attr(p.name)).append("\"/>\r\n");
		sb.append("</transmissions>\r\n");
		SafeFiles.writeText(file(), sb.toString(), true);
	}
	public static int unread() {
		int n = 0;
		for (Message m : load()) if (!m.read && !m.archived) n++;
		return n;
	}

	// ---- what's due ----

	/** The player's rank, as messages name it. */
	/** The player's rank now, as messages name it (Captain outside Immersive Mode). */
	public static String rank() {
		Unlocks u = Unlocks.read();
		return rankName(u.problem() != null ? null : u);
	}
	private static String rankName(Unlocks u) {
		return HomePlanet.career() ? UnlockGrants.rankName(UnlockGrants.rank(u)) : UnlockGrants.RANKS[0];
	}
	/** A layout's name for messages: "Engi Cruiser, Type A". */
	static String layoutName(String base, int n) {
		String cls = base;
		try {
			ShipBlueprint bp = DataManager.get().getPlayerShipVariant(base, n, true);
			if (bp != null && bp.getShipClass() != null && bp.getShipClass().getTextValue() != null) cls = bp.getShipClass().getTextValue();
		} catch (Exception e) { }
		return cls + ", Type " + "ABC".charAt(Math.max(0, Math.min(n, 2)));
	}
	private static String freeShipWords() {
		return FreeCommand.words(FreeCommand.ship());
	}

	/**
	 * Sends what's due (Immersive Notifications on): in Immersive Mode the welcome, promotions and achievement
	 * rewards; with commissioning costs, the empty shipyard's free command and an order for each free unlock.
	 * Returns how many were sent.
	 */
	public static synchronized int check() {
		if (!HomePlanet.immersiveNotifications() || !Vault.isOpen()) return 0;
		List<Message> all = load();
		Set<String> sent = new java.util.HashSet<String>();
		for (Message m : all) sent.add(m.key);
		Unlocks u = Unlocks.read();
		if (u.problem() != null) u = null;
		String rank = rankName(u);
		int before = all.size();
		boolean wasOpen = emptyOpen, wasStranded = strandedOpen;
		int replaced = 0;
		Vault v = Vault.get();
		// Sandbox Mode's Career messages, first ticked: the career begins (no free ship: the fleet has its own), counting
		// only achievements earned from now on
		if (HomePlanet.career() && !v.immersive && !Career.started(v.root)) {
			try {
				UnlockGrants.achievementsSeen(u);
				Career.start(false, false, false);
				send(all, sent, "welcome:career", "welcome:career", rank, null);
			} catch (IOException e) { log.warn("Could not begin the Sandbox career: {}", e.toString()); }
		}
		if (HomePlanet.career()) {
			int r = UnlockGrants.rank(u);
			for (int i = 1; i <= r; i++) send(all, sent, "promo:" + i, "promo:" + i, rank, null);
		}
		// one order per free command (the fleet's start, a report for reassignment), never for an empty shipyard alone
		boolean granted = v.freeCommandOpen();
		if (HomePlanet.commissionCosts() && granted && v.shipyardEmpty()) {
			if (!emptyOpen) {
				String key = "empty:" + stamp();
				for (int i = 2; sent.contains(key); i++) key = "empty:" + stamp() + "-" + i; // two in one second
				// after a Report for Reassignment, the Shipyard's other letter (in Immersive Mode, the one for the ship it earned)
				String letter = !v.freeCommandReassigned() ? "empty" : v.immersive ? "reassigned:" + FreeCommand.ship() : "reassigned";
				// the new order replaces the last one still in the inbox (it's done with: one order per free command)
				for (java.util.Iterator<Message> it = all.iterator(); it.hasNext();) {
					Message old = it.next();
					if (old.key.startsWith("empty:") && !old.archived) { it.remove(); replaced++; }
				}
				send(all, sent, key, letter, rank, freeShipWords());
				emptyOpen = true;
			}
		} else if (!granted) {
			emptyOpen = false; // taken: the next grant sends its own order
		}
		// no ship to command and no free command waiting: the Liaison says what can be done, the first time only (each fleet)
		boolean stranded = HomePlanet.commissionCosts() && !granted && v.docked().isEmpty() && v.boarded() == null;
		if (stranded && !strandedOpen && v.event("stranded-letter") == null) {
			send(all, sent, "stranded:" + stamp(), v.junked().isEmpty() ? "stranded" : "stranded:junkyard", rank, null);
			v.recordEvent("stranded-letter", stamp());
			strandedOpen = true;
		} else if (!stranded) {
			strandedOpen = false;
		}
		if (HomePlanet.commissionCosts() && HomePlanet.unlockFreeShips() && u != null) {
			for (String base : DataManager.get().getPlayerShipBaseIds(true)) {
				for (int n = 0; n < 3; n++) {
					if (HomePlanet.immersiveMode && "PLAYER_SHIP_FED".equals(base) && n != 1) continue; // the Type A and C come with a promotion
					ShipBlueprint bp;
					try { bp = DataManager.get().getPlayerShipVariant(base, n, true); } catch (Exception e) { bp = null; }
					if (bp == null || !UnlockGrants.freeNow(u, bp.getId())) continue;
					send(all, sent, "order:" + base + " " + n, "order:" + base, rank, layoutName(base, n));
				}
			}
		}
		if (HomePlanet.career() && u != null) {
			for (String a : UnlockGrants.newAchievements(u)) send(all, sent, "ach:" + a, "ach:" + a, rank, null);
		}
		if (HomePlanet.career() && Career.started(Vault.get().root)) payStipend(all, sent, u, rank);
		// reply chains: a letter for what the fleet has been through, and the letters now due
		boolean chained = false;
		String oneHull = v.event(Vault.EVENT_ONE_HULL);
		if (oneHull != null && !sent.contains(ONE_HULL)) chained |= chain(all, sent, ONE_HULL, rank, oneHull);
		for (Pending p : new ArrayList<Pending>(pending)) {
			if (p.due > v.beaconsSeen()) continue;
			if (chain(all, sent, p.template, rank, p.name)) { pending.remove(p); chained = true; }
		}
		// the welcome last: the inbox shows the newest first, so it tops everything that arrives with it
		if (HomePlanet.immersiveMode) send(all, sent, "welcome", "welcome", rank, null);
		int added = all.size() - before + replaced;
		if (added > 0 || replaced > 0 || wasOpen != emptyOpen || wasStranded != strandedOpen || chained) {
			try { save(all); } catch (IOException e) { log.error("Could not save the transmissions", e); }
		}
		return added;
	}
	/** The first letter of the chain for a ship that came out of a battle with one point of hull. */
	static final String ONE_HULL = "chain:one-hull";
	/**
	 * Sends a chain letter (its key is its template's, so each goes once), first doing what it carries (the derelict's
	 * delivery), and schedules the letter that follows it. False if it must wait (its action failed: tried again later).
	 */
	private static boolean chain(List<Message> all, Set<String> sent, String templateKey, String rank, String name) {
		if (sent.contains(templateKey)) return true; // sent before: nothing more to do
		Template t = templates().get(templateKey);
		if (t == null) return true; // no letter written for it
		if ("derelict".equals(t.action)) {
			try { Derelict.deliver(Vault.get()); }
			catch (Exception e) { log.warn("Could not deliver the derelict (tried again next time): {}", e.toString()); return false; }
		}
		send(all, sent, templateKey, templateKey, rank, null, name);
		if (!t.then.isEmpty()) schedule(t.then, name);
		return true;
	}
	/** Schedules "next-key 5-7": that letter, a random 5 to 7 beacons from now. */
	private static void schedule(String next, String name) {
		String[] w = next.trim().split("\\s+");
		Pending p = new Pending();
		p.template = w[0];
		p.name = name == null ? "" : name;
		int min = 0, max = 0;
		if (w.length > 1) {
			String[] r = w[1].split("-");
			try { min = Integer.parseInt(r[0].trim()); max = r.length > 1 ? Integer.parseInt(r[1].trim()) : min; } catch (NumberFormatException e) { }
		}
		p.due = Vault.get().beaconsSeen() + min + (max > min ? new Random().nextInt(max - min + 1) : 0);
		pending.add(p);
	}
	/** A letter's replies: the words of each. Empty if it offers none. */
	public static List<String> replyTexts(Message m) {
		List<String> out = new ArrayList<String>();
		if (m.replies == null || m.replies.trim().isEmpty()) return out;
		for (String r : m.replies.split("\\|")) out.add(r.split("->")[0].trim());
		return out;
	}
	/** Can the player still reply to this letter? */
	public static boolean canReply(Message m) { return !replyTexts(m).isEmpty() && (m.replied == null || m.replied.isEmpty()); }
	/** Sends the chosen reply: recorded on the letter, and its answer scheduled some beacons from now. */
	public static synchronized void reply(Message m, int option) throws IOException {
		if (!canReply(m)) throw new IOException("This transmission has been answered already");
		String[] options = m.replies.split("\\|");
		if (option < 0 || option >= options.length) throw new IOException("Choose a reply first");
		String[] parts = options[option].split("->");
		String words = parts[0].trim();
		List<Message> all = load(); // also reads the letters already due
		String name = "";
		for (Pending p : pending) if (p.name != null && !p.name.isEmpty()) name = p.name;
		if (name.isEmpty() && Vault.isOpen()) { String n = Vault.get().event(Vault.EVENT_ONE_HULL); if (n != null) name = n; }
		if (parts.length > 1 && !parts[1].trim().isEmpty()) schedule(parts[1].trim(), name);
		for (Message x : all) if (x.key.equals(m.key)) { x.replied = words; x.read = true; }
		save(all);
		m.replied = words;
		m.read = true;
		HistoryLog.entry("REPLY", m.from + ": " + words);
	}
	/** The stipend for whole months travelled (every 4 sectors), paid into the Cargo Hold, in one message. */
	private static void payStipend(List<Message> all, java.util.Set<String> sent, Unlocks u, String rank) {
		int months = Career.unpaidMonths();
		if (months <= 0) return;
		int amount = months * Career.stipend(UnlockGrants.rank(u), Career.achievementsCounted(u));
		try {
			Career.markPaid(months); // marked first: a payment whose mark was lost would be paid again
			try {
				Vault.get().depositToStorage(amount);
			} catch (IOException e) {
				Career.markPaid(-months);
				throw e;
			}
		} catch (IOException e) {
			log.warn("Could not pay the stipend (tried again next time): {}", e.toString());
			return;
		}
		String period = months == 1 ? "monthly stipend" : "stipend for the last " + months + " months";
		Template t = templates().get("stipend");
		if (t == null) return;
		Message m = new Message();
		m.key = "stipend:" + stamp();
		m.date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
		m.from = t.from;
		m.subject = Character.toUpperCase(period.charAt(0)) + period.substring(1) + ": " + amount + " scrap";
		m.body = fill(t.body.toString().trim(), rank, null).replace("{period}", period).replace("{amount}", Integer.toString(amount));
		all.add(0, m);
		sent.add(m.key);
		HistoryLog.entry("STIPEND", amount + " scrap to the Cargo Hold (" + months + " month" + (months == 1 ? "" : "s") + ")");
	}
	/** A stipend's notice: deleted rather than archived, so they don't pile up. */
	public static boolean isStipend(Message m) { return m.key.startsWith("stipend:"); }
	/** A notice with nothing left to keep, deleted rather than archived: a stipend (paid already), an order for a free command since taken. */
	public static boolean deletable(Message m) {
		if (isStipend(m)) return true;
		return m.key.startsWith("empty:") && Vault.isOpen() && !Vault.get().freeCommandOpen();
	}
	/** Deletes a message for good. */
	public static synchronized void delete(Message m) throws IOException {
		List<Message> all = load();
		for (java.util.Iterator<Message> it = all.iterator(); it.hasNext();) if (it.next().key.equals(m.key)) it.remove();
		save(all);
	}
	// ---- a final victory's messages ----

	/** A message's from, subject and text from its template, with {rank} and the other {placeholders} filled. */
	public static String[] text(String templateKey, Map<String, String> fills) {
		Template t = templates().get(templateKey);
		if (t == null) return null;
		Map<String, String> f = new LinkedHashMap<String, String>(fills);
		if (!f.containsKey("rank")) {
			Unlocks u = Unlocks.read();
			f.put("rank", rankName(u.problem() == null ? u : null));
		}
		String[] out = {t.from, t.subject, t.body.toString().trim()};
		for (int i = 0; i < out.length; i++) for (Map.Entry<String, String> e : f.entrySet()) out[i] = out[i].replace("{" + e.getKey() + "}", e.getValue());
		return out;
	}
	/** Sends one message now, from its template with the {placeholders} filled; nothing if one with this key was sent before. */
	public static synchronized void post(String key, String templateKey, Map<String, String> fills) throws IOException {
		List<Message> all = load();
		for (Message m : all) if (m.key.equals(key)) return;
		String[] t = text(templateKey, fills);
		if (t == null) return;
		Message m = new Message();
		m.key = key;
		m.date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
		m.from = t[0];
		m.subject = t[1];
		m.body = t[2];
		m.reward = "";
		all.add(0, m);
		save(all);
		HistoryLog.entry("TRANSMISSION", m.from + ": " + m.subject);
	}
	/** A rescued ship's offer (keep her, or the museum's price), until it's decided. */
	public static boolean isRescue(Message m) { return m.key.startsWith("rescue:"); }
	/** The ship id a rescue offer is about. */
	public static String rescueId(Message m) { return m.key.substring("rescue:".length()); }
	/** Records a rescue offer as decided, with what came of it in words. */
	public static synchronized void decided(Message m, String what) throws IOException {
		m.claimed = true;
		m.read = true;
		m.claimedWhat = what;
		List<Message> all = load();
		for (Message x : all) if (x.key.equals(m.key)) { x.claimed = true; x.read = true; x.claimedWhat = what; }
		save(all);
	}

	/**
	 * A letter written by the station itself rather than from a template (a Long Range Comm. receipt): sent once per
	 * key, only while the inbox is on.
	 */
	public static synchronized void deliver(String key, String from, String subject, String body) {
		if (!HomePlanet.immersiveNotifications() || !Vault.isOpen()) return;
		List<Message> all = load();
		for (Message x : all) if (x.key.equals(key)) return;
		Message m = new Message();
		m.key = key;
		m.date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
		m.from = from;
		m.subject = subject;
		m.body = body;
		m.reward = "";
		all.add(0, m);
		try {
			save(all);
			HistoryLog.entry("TRANSMISSION", m.from + ": " + m.subject);
		} catch (IOException e) {
			log.warn("Could not deliver {}: {}", key, e.toString());
		}
	}
	private static String stamp() { return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()); }
	private static void send(List<Message> all, Set<String> sent, String key, String templateKey, String rank, String ship) {
		send(all, sent, key, templateKey, rank, ship, null);
	}
	/** As above, with {name}: a ship's own name (the one a chain is about). */
	private static void send(List<Message> all, Set<String> sent, String key, String templateKey, String rank, String ship, String name) {
		if (sent.contains(key)) return;
		Template t = templates().get(templateKey);
		if (t == null) return; // no message written for it
		Message m = new Message();
		m.key = key;
		m.date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
		m.from = t.from;
		m.subject = fill(t.subject, rank, ship).replace("{name}", name == null ? "" : name);
		m.body = fill(t.body.toString().trim(), rank, ship).replace("{name}", name == null ? "" : name);
		m.reward = t.reward;
		m.replies = t.replies;
		m.cost = t.cost;
		all.add(0, m); // newest first
		sent.add(key);
		HistoryLog.entry("TRANSMISSION", m.from + ": " + m.subject);
	}
	private static String fill(String s, String rank, String ship) {
		if (s.contains("{start}")) s = s.replace("{start}", Integer.toString(Career.startingScrap())); // the career's sign-on bonus, by difficulty
		return s.replace("{rank}", rank).replace("{ship}", ship == null ? "" : ship);
	}

	// ---- rewards ----

	/** A reward's parts ("scrap 25", "item ROCK_ARMOR"...); a "choice" part is one entry. */
	static List<String> parts(String reward) {
		List<String> out = new ArrayList<String>();
		if (reward == null) return out;
		for (String p : reward.split(",")) if (!p.trim().isEmpty()) out.add(p.trim());
		return out;
	}
	/** The options of a choice reward, or empty if it has none. */
	public static List<String> choices(Message m) {
		for (String p : parts(m.reward)) {
			if (!p.startsWith("choice ")) continue;
			List<String> out = new ArrayList<String>();
			for (String o : p.substring(7).split("\\|")) out.add(o.trim());
			return out;
		}
		return new ArrayList<String>();
	}
	/** One reward part in words: "25 scrap", "Rock Plating", "a Zoltan volunteer", "a Cloaking system". */
	public static String describe(String part) {
		String[] w = part.trim().split("\\s+", 2);
		String kind = w[0], v = w.length > 1 ? w[1] : "";
		if (kind.equals("scrap") || kind.equals("fuel") || kind.equals("missiles")) return v + " " + kind;
		if (kind.equals("parts")) return v + " drone parts";
		if (kind.equals("item")) return Items.title(v);
		if (kind.equals("crew")) {
			String race = v.equals("energy") ? "Zoltan" : v.equals("anaerobic") ? "Lanius" : Character.toUpperCase(v.charAt(0)) + v.substring(1);
			return "a " + race + " crew volunteer";
		}
		if (kind.equals("system")) return "a " + Items.systemTitle(v) + " system";
		if (kind.equals("choice")) {
			List<String> names = new ArrayList<String>();
			for (String o : v.split("\\|")) names.add(describe(o));
			return String.join(" or ", names);
		}
		return part;
	}
	/** The scrap a reward costs to claim (its "cost: scrap N"), or 0. */
	public static int price(Message m) {
		if (m.cost == null) return 0;
		String[] w = m.cost.trim().split("\\s+");
		if (w.length == 2 && w[0].equals("scrap")) { try { return Math.max(0, Integer.parseInt(w[1])); } catch (NumberFormatException e) { } }
		return 0;
	}
	/** The whole reward in words. */
	public static String describeReward(Message m) {
		List<String> names = new ArrayList<String>();
		for (String p : parts(m.reward)) names.add(describe(p));
		return String.join(", ", names);
	}

	/**
	 * Claims a message's reward into the Cargo Hold (with the choice made, for a choice reward): one save, all or
	 * nothing. Returns what was claimed, in words.
	 */
	public static synchronized String claim(Message m, int choice) throws IOException {
		if (m.claimed || !m.hasReward()) throw new IOException("There's nothing left to claim in this transmission");
		List<String> give = new ArrayList<String>();
		for (String p : parts(m.reward)) {
			if (p.startsWith("choice ")) {
				List<String> cs = choices(m);
				if (choice < 0 || choice >= cs.size()) throw new IOException("Choose one of the rewards first");
				give.add(cs.get(choice));
			} else {
				give.add(p);
			}
		}
		Vault v = Vault.get();
		Ship st = v.storage();
		Vault.Copy c = v.readCopy(st);
		ShipState s = c.save.getPlayerShip();
		int price = price(m);
		if (price > s.getScrapAmt()) throw new IOException("This costs " + price + " scrap, and the Cargo Hold has " + s.getScrapAmt() + ". Store more scrap in the Cargo Hold (the Cargo Bay), then claim it.");
		s.setScrapAmt(s.getScrapAmt() - price); // paid in the same save as the delivery: all or nothing
		List<String> systems = new ArrayList<String>();
		Random rng = new Random();
		for (String p : give) {
			String[] w = p.split("\\s+", 2);
			String kind = w[0], val = w.length > 1 ? w[1].trim() : "";
			if (kind.equals("scrap")) s.setScrapAmt(s.getScrapAmt() + Integer.parseInt(val));
			else if (kind.equals("fuel")) s.setFuelAmt(s.getFuelAmt() + Integer.parseInt(val));
			else if (kind.equals("missiles")) s.setMissilesAmt(s.getMissilesAmt() + Integer.parseInt(val));
			else if (kind.equals("parts")) s.setDronePartsAmt(s.getDronePartsAmt() + Integer.parseInt(val));
			else if (kind.equals("item")) {
				if (Items.isWeapon(val)) s.getWeaponList().add(SaveHelper.newIdleWeapon(val));
				else if (Items.isDrone(val)) s.getDroneList().add(SaveHelper.newIdleDrone(val));
				else if (Items.isAugment(val)) s.getAugmentIdList().add(val);
				else throw new IOException("Unknown item in the reward: " + val);
			} else if (kind.equals("crew")) {
				CrewState crew = Commission.volunteer(val, rng);
				if (crew == null) throw new IOException("Unknown crew race in the reward: " + val);
				if (!SaveHelper.placeCrew(s, crew, true)) throw new IOException("The Cargo Hold has no room for another crew member");
				s.getCrewList().add(crew);
			} else if (kind.equals("system")) {
				SystemType t = SystemType.findById(val);
				if (t == null) throw new IOException("Unknown system in the reward: " + val);
				systems.add(t == SystemType.CLONEBAY ? val : val + " 1");
			} else {
				throw new IOException("Unknown reward: " + p);
			}
		}
		Vault.Transaction tx = v.begin().put(st, c.save, c.hash);
		if (!systems.isEmpty()) {
			File f = v.systemsFile();
			List<String> lines = new ArrayList<String>();
			if (f.isFile()) lines.addAll(java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
			else lines.add("# Ship systems stored in the Cargo Bay: <system id> <level> (a Clone Bay has no level: it uses the Medbay's)");
			lines.addAll(systems);
			tx.put(f, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
		}
		List<String> words = new ArrayList<String>();
		for (String p : give) words.add(describe(p));
		String what = String.join(", ", words) + (price > 0 ? " (" + price + " scrap paid)" : "");
		// marked claimed before delivery: if the delivery fails the mark is taken back, but a failure to save the mark
		// after a delivery could never be undone, and the reward would be offered again
		List<Message> all = load();
		for (Message x : all) if (x.key.equals(m.key)) { x.claimed = true; x.read = true; x.claimedWhat = what; }
		save(all);
		try {
			tx.commit();
		} catch (IOException e) {
			for (Message x : all) if (x.key.equals(m.key)) { x.claimed = false; x.claimedWhat = ""; }
			try { save(all); } catch (IOException again) { log.error("Could not take back the claim on " + m.key, again); }
			throw e;
		}
		m.claimed = true;
		m.claimedWhat = what;
		HistoryLog.entry("CLAIM", m.subject + ": " + what + " to the Cargo Hold");
		return what;
	}
	/** Moves a message to the Archive (read), or back to the inbox. */
	public static synchronized void setArchived(Message m, boolean archived) throws IOException {
		m.archived = archived;
		if (archived) m.read = true;
		List<Message> all = load();
		for (Message x : all) if (x.key.equals(m.key)) { x.archived = archived; if (archived) x.read = true; }
		save(all);
	}
	/** Marks a message read. */
	public static synchronized void markRead(Message m) {
		if (m.read) return;
		m.read = true;
		List<Message> all = load();
		for (Message x : all) if (x.key.equals(m.key)) x.read = true;
		try { save(all); } catch (IOException e) { log.warn("Could not save the transmissions: {}", e.toString()); }
	}
}
