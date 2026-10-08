package homeplanet.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.constants.Difficulty;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.core.Event;
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

	/** Her value: her price as she was kept, strictly counted, at the difficulty's rate (custom work orders aside). */
	public static int value(SavedGameState gs) {
		return Pricing.ship(gs, Pricing.rate()).total();
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
					if (HomePlanet.immersiveNotifications()) Transmissions.post("rescue:" + f.id, "rescue", fills); // nothing if it's there already
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
				if (won) homeplanet.vault.Reputation.flagship(v, f.name); // the career's standing, if one runs
				if (!won || NOTHING.equals(c)) {
					v.closeFinal(f, won);
					if (won) HistoryLog.entry("VICTORY", f.name + " won the last battle and was lost with the run (after a final victory: nothing)", null, battle("VICTORY", f).put("after", "nothing"));
					continue;
				}
				boolean named = u.victoriousScores(gs.getPlayerShipName(), gs.getPlayerShipBlueprintId()) > f.scoresThen;
				HistoryLog.entry("VICTORY", f.name + " won the last battle (the profile's victories " + f.victoriesThen + " -> " + u.victories()
						+ (named ? ", and a Top Scores entry names her" : "") + "); after a final victory: " + c + ", her value " + value + " scrap", null,
						battle("VICTORY", f).put("victories_then", f.victoriesThen).put("victories_now", u.victories()).put("top_scores", named).put("after", c).put("value", value));
				if (MUSEUM.equals(c)) { // Hard: no keeping her; the museum takes her at its price
					fills.put("value", Integer.toString(museumPrice(value)));
					museum(f);
					if (HomePlanet.immersiveNotifications()) Transmissions.post("museum:" + f.id, "museum", fills);
					else out.add(notice("museum", fills, null, museumPrice(value)));
				} else if (RESCUE.equals(c)) {
					fills.put("value", Integer.toString(museumPrice(value)));
					v.finalOffered(f);
					if (HomePlanet.immersiveNotifications()) Transmissions.post("rescue:" + f.id, "rescue", fills);
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
					HistoryLog.entry("REWARD", value + " scrap to the Cargo Hold for " + f.name, null, battle("REWARD", f).put("scrap", value).put("to", "hold"));
					if (HomePlanet.immersiveNotifications()) Transmissions.post("reward:" + f.id, "reward", fills);
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
	// Rescued Ships after Victory moved to Hard difficulty (heromedel, 6.02 and 6.03), as the fleet in use has it:
	/** The player's, in Settings: Sandbox Mode (the cfg) and Easy (its career). */
	public static final String TO_HARD_FREE = "free";
	/** Always: Normal, or a Custom career that chose it. */
	public static final String TO_HARD_ON = "on";
	/** Never: a Custom career that chose not to (she gets the question of her difficulty). */
	public static final String TO_HARD_OFF = "off";
	/** Doesn't apply: the museum takes every victor (Hard, or a Custom career with that rule). */
	public static final String TO_HARD_NONE = "none";
	/** A Custom career that hasn't chosen yet: the Space Dock asks, as the briefing would have. */
	public static final String TO_HARD_ASK = "ask";
	/** How it stands for the fleet in use. */
	public static String toHardRule() {
		CareerRules r = CareerRules.current();
		return toHardRule(r, r == null ? null : Career.rescuedToHard(Vault.get().root));
	}
	/** How it stands for a career with these rules and this saved answer (null: none); null rules, Sandbox Mode. */
	public static String toHardRule(CareerRules r, String saved) {
		if (r == null) return TO_HARD_FREE;
		if (MUSEUM.equals(r.victory())) return TO_HARD_NONE;
		if (CareerRules.NORMAL.equals(r.name)) return TO_HARD_ON; // no saved answer is read: deleting one changes nothing
		if (CareerRules.CUSTOM.equals(r.name)) return saved == null ? TO_HARD_ASK : Boolean.parseBoolean(saved) ? TO_HARD_ON : TO_HARD_OFF;
		return TO_HARD_FREE; // Easy, or a career from before difficulties
	}
	/** Whether a rescued ship, kept, sets out on Hard without asking. */
	public static boolean toHard() {
		String rule = toHardRule();
		if (TO_HARD_ON.equals(rule)) return true;
		if (!TO_HARD_FREE.equals(rule)) return false;
		return CareerRules.current() == null ? HomePlanet.rescuedToHard : Boolean.parseBoolean(Career.rescuedToHard(Vault.get().root));
	}
	/** The player's own answer, where it is theirs (Sandbox Mode's in the cfg: the caller saves it; Easy's in its career). */
	public static void setToHard(boolean on) throws IOException {
		if (CareerRules.current() == null) HomePlanet.rescuedToHard = on;
		else if (TO_HARD_FREE.equals(toHardRule())) Career.setRescuedToHard(Vault.get().root, on);
	}
	/** A Custom career's answer, chosen once (the briefing, or the Space Dock's question). */
	public static void chooseToHard(java.io.File immersiveRoot, boolean on) throws IOException {
		Career.setRescuedToHard(immersiveRoot, on);
		HistoryLog.entry("CAREER", "Rescued Ships after Victory moved to Hard difficulty: " + (on ? "yes" : "no") + " (chosen for the career, fixed)", null,
				Event.of("CAREER").put("what", "rescued_to_hard").put("rescued_to_hard", on));
	}
	/** Keep her: she docks, ready for a new journey at this difficulty (null: the one she won on). Returns what came of it, in words. */
	public static String keep(Vault.FinalBattle f, Difficulty difficulty) throws IOException {
		Ship s = Vault.get().bringHome(f, difficulty);
		Museum.kept(Vault.get(), f.id);
		return s.name + " is docked at the Space Dock, ready for her next journey" + (difficulty == null ? "." : ", on " + title(difficulty) + ".");
	}
	/** "Easy", "Normal" or "Hard". */
	public static String title(Difficulty d) { return d == null ? "" : d.toString().substring(0, 1) + d.toString().substring(1).toLowerCase(); }
	/** The museum's offer: its price (her value, or half on harder careers) to the Cargo Hold, and she goes to the museum. Returns what came of it, in words. */
	/** An event about the ship of a final battle (she may have left the fleet). */
	private static Event battle(String kind, Vault.FinalBattle f) { return Event.of(kind).put("ship", f.name + "." + f.id).put("ship_name", f.name).put("ship_id", f.id); }
	public static String museum(Vault.FinalBattle f) throws IOException {
		int value = museumPrice(value(HomePlanet.savedGameParser.readSavedGame(f.copy)));
		Vault v = Vault.get();
		v.depositToStorage(value);
		v.toMuseum(f);
		Museum.preserved(v, f.id, value);
		HistoryLog.entry("MUSEUM", value + " scrap to the Cargo Hold for " + f.name, null, battle("MUSEUM", f).put("scrap", value).put("to", "hold"));
		return f.name + " is honoured in the Federation Museum. " + value + " scrap is waiting in the Cargo Hold.";
	}
}
