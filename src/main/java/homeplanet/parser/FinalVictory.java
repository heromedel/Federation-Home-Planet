package homeplanet.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.HistoryLog;
import homeplanet.core.HomePlanet;
import homeplanet.vault.Ship;
import homeplanet.vault.Vault;

/**
 * After a final victory: each fleet's choice (nothing, rescue her, or a reward of her value), the copy kept while the
 * Rebel Flagship is on her way to the last battle (the station must be open then), and settling it once the ship is
 * found lost: the profile's victory count gone up means she won.
 */
public final class FinalVictory {
	private static final Logger log = LoggerFactory.getLogger(FinalVictory.class);
	private FinalVictory() { }

	public static final String NOTHING = "nothing", RESCUE = "rescue", REWARD = "reward";
	/** An Immersive career on Hard: the museum takes her, at half her value (never a choice in Settings). */
	public static final String MUSEUM = "museum";
	public static final String[] CHOICES = {NOTHING, RESCUE, REWARD};

	/** A choice as Settings and the Immersive briefing offer it. */
	public static String label(String choice) {
		if (RESCUE.equals(choice)) return "Rescue the ship (with an offer to sell her to the museum)";
		if (REWARD.equals(choice)) return "Receive a reward equal to her value";
		return "Nothing (she is lost with the run)";
	}
	private static String norm(String c) { return RESCUE.equals(c) || REWARD.equals(c) ? c : NOTHING; }
	/** The choice of the fleet in use (the Immersive fleet's is its difficulty's, or, from before difficulties, kept in its career). */
	public static String choice() {
		Vault v = Vault.get();
		String fixed = fixed();
		if (fixed != null) return fixed;
		return norm(v.immersive ? Career.finalVictory(v.root) : HomePlanet.finalVictory);
	}
	/** The Immersive career's difficulty decides it (RESCUE or MUSEUM), or null where the player chooses. */
	public static String fixed() {
		CareerRules r = CareerRules.current();
		return r == null ? null : r.victory();
	}
	/** What the museum pays, as a share of her value: the difficulty's (full or half), otherwise full. */
	public static int museumPercent() {
		CareerRules r = CareerRules.current();
		return r == null ? 100 : r.museumPercent();
	}
	/** The museum's price for her. */
	public static int museumPrice(int value) { return value * museumPercent() / 100; }
	/** "her full value" or "half her value", for the letters. */
	static String worth() { return museumPercent() >= 100 ? "her full value" : "half her value"; }
	/** Sets the choice of the fleet in use (the normal fleet's is in the cfg: the caller saves it). */
	public static void setChoice(String c) throws IOException {
		Vault v = Vault.get();
		if (v.immersive) Career.setFinalVictory(v.root, norm(c));
		else HomePlanet.finalVictory = norm(c);
	}

	/** Watching continue.sav: keeps the boarded ship's copy if the flagship is on her way to the last battle. */
	public static void watch() {
		if (!Vault.isOpen() || NOTHING.equals(choice())) return;
		final Unlocks u = Unlocks.read();
		Vault.get().watchContinue(u.victories(), new java.util.function.BiFunction<String, String, Integer>() {
			public Integer apply(String name, String bpId) { return u.victoriousScores(name, bpId); }
		});
	}

	/** Her full value: the commission price of her as she was kept (systems, reactor, gear, crew, a custom hull), at 100%. */
	public static int value(SavedGameState gs) {
		return Pricing.ship(gs, 100).total();
	}

	/** What settling found, for the Space Dock to tell the player when Transmissions are off. */
	public static final class Notice {
		public final String title, text;
		/** A rescue offer, still to decide: keep her, or the museum. Null for a notice to read. */
		public final Vault.FinalBattle offer;
		public final int value;
		Notice(String title, String text, Vault.FinalBattle offer, int value) { this.title = title; this.text = text; this.offer = offer; this.value = value; }
	}

	/**
	 * Settles the lost ships with a copy kept before the last battle: a victory rescues her (an offer), rewards her
	 * value, or with the choice "nothing" only notes it; no victory closes the copy. Rescue offers still open come
	 * back each time. With Transmissions on, the messages go to the inbox; otherwise they're returned as notices.
	 */
	public static List<Notice> settle() {
		List<Notice> out = new ArrayList<Notice>();
		if (!Vault.isOpen()) return out;
		Vault v = Vault.get();
		List<Vault.FinalBattle> finals = v.finalBattles();
		if (finals.isEmpty()) return out;
		Unlocks u = Unlocks.read();
		for (Vault.FinalBattle f : finals) {
			try {
				SavedGameState gs = HomePlanet.savedGameParser.readSavedGame(f.copy);
				int value = value(gs);
				Map<String, String> fills = new LinkedHashMap<String, String>();
				fills.put("ship", f.name);
				fills.put("value", Integer.toString(value));
				fills.put("worth", worth());
				if ("offered".equals(f.outcome)) {
					fills.put("value", Integer.toString(museumPrice(value)));
					if (HomePlanet.immersiveNotifications) Transmissions.post("rescue:" + f.id, "rescue", fills); // nothing if it's there already
					else out.add(notice("rescue", fills, f, museumPrice(value)));
					continue;
				}
				if ("rewarded".equals(f.outcome)) { v.closeFinal(f, true); continue; } // paid; only the closing was left
				if (u.problem() != null || u.missing()) continue; // the profile can't say: asked again next time
				// the victory count gone up; if it couldn't be read then, a victorious Top Scores entry naming her
				boolean won = f.victoriesThen >= 0 ? u.victories() > f.victoriesThen
						: u.victoriousScores(gs.getPlayerShipName(), gs.getPlayerShipBlueprintId()) > Math.max(0, f.scoresThen);
				String c = choice();
				if (won) Museum.recordVictory(v, f, gs, u); // every victor has a place in the Hall of Victors
				if (!won || NOTHING.equals(c)) {
					v.closeFinal(f, won);
					if (won) HistoryLog.entry("VICTORY", f.name + " won the last battle and was lost with the run (after a final victory: nothing)");
					continue;
				}
				boolean named = u.victoriousScores(gs.getPlayerShipName(), gs.getPlayerShipBlueprintId()) > f.scoresThen;
				HistoryLog.entry("VICTORY", f.name + " won the last battle (the profile's victories " + f.victoriesThen + " -> " + u.victories()
						+ (named ? ", and a Top Scores entry names her" : "") + "); after a final victory: " + c + ", her value " + value + " scrap");
				if (MUSEUM.equals(c)) { // Hard: no keeping her; the museum takes her at its price
					fills.put("value", Integer.toString(museumPrice(value)));
					museum(f);
					if (HomePlanet.immersiveNotifications) Transmissions.post("museum:" + f.id, "museum", fills);
					else out.add(notice("museum", fills, null, museumPrice(value)));
				} else if (RESCUE.equals(c)) {
					fills.put("value", Integer.toString(museumPrice(value)));
					v.finalOffered(f);
					if (HomePlanet.immersiveNotifications) Transmissions.post("rescue:" + f.id, "rescue", fills);
					else out.add(notice("rescue", fills, v.finalBattle(f.id), museumPrice(value)));
				} else {
					v.finalRewarded(f); // noted before paying: a payment whose note was lost would be paid again
					try {
						v.depositToStorage(value);
					} catch (IOException e) {
						v.finalUnsettled(f);
						throw e;
					}
					v.closeFinal(f, true);
					HistoryLog.entry("REWARD", value + " scrap to the Cargo Hold for " + f.name);
					if (HomePlanet.immersiveNotifications) Transmissions.post("reward:" + f.id, "reward", fills);
					else out.add(notice("reward", fills, null, value));
				}
			} catch (Exception e) {
				log.warn("Could not settle {}'s final battle (tried again next time): {}", f.name, e.toString());
			}
		}
		return out;
	}
	private static Notice notice(String templateKey, Map<String, String> fills, Vault.FinalBattle offer, int value) {
		String[] t = Transmissions.text(templateKey, fills);
		return new Notice(t == null ? "Final victory" : t[1], t == null ? "" : t[2], offer, value);
	}

	/** The rescue offer still open for this ship id, or null. */
	public static Vault.FinalBattle offer(String id) {
		Vault.FinalBattle f = Vault.get().finalBattle(id);
		return f != null && "offered".equals(f.outcome) ? f : null;
	}
	/** Keep her: she docks, ready for a new journey. Returns what came of it, in words. */
	public static String keep(Vault.FinalBattle f) throws IOException {
		Ship s = Vault.get().bringHome(f);
		Museum.kept(Vault.get(), f.id);
		return s.name + " is docked at the Space Dock, ready for her next journey.";
	}
	/** The museum's offer: its price (her value, or half on harder careers) to the Cargo Hold, and she goes to the museum. Returns what came of it, in words. */
	public static String museum(Vault.FinalBattle f) throws IOException {
		int value = museumPrice(value(HomePlanet.savedGameParser.readSavedGame(f.copy)));
		Vault v = Vault.get();
		v.depositToStorage(value);
		v.toMuseum(f);
		Museum.preserved(v, f.id, value);
		HistoryLog.entry("MUSEUM", value + " scrap to the Cargo Hold for " + f.name);
		return f.name + " is honoured in the Federation museum. " + value + " scrap is waiting in the Cargo Hold.";
	}
}
