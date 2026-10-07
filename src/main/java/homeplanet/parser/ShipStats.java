package homeplanet.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import homeplanet.core.Store;

import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;

import homeplanet.vault.JourneyStart;
import homeplanet.vault.Ship;
import homeplanet.vault.TradeMark;
import homeplanet.vault.Vault;
import homeplanet.vault.VoyageLog;

/**
 * Her report's Stats tab: this journey (from where it began), her whole service (the station's records: FTL's save
 * knows only the current journey's sector), and her crew's standouts. Displays keep her whole life (a traded ship's
 * earlier service included, with a note of where she came from). Nothing the records lack is shown as 0: it's left out.
 */
public final class ShipStats {
	/** A stat's label and value; {@code tone} colours the value: 0 as usual, 1 a gain, -1 a loss. */
	public static final class Line {
		public final String label, value;
		public final int tone;
		Line(String label, String value, int tone) { this.label = label; this.value = value; this.tone = tone; }
	}
	/** A crew member who stands out: her title ("Best Pilot") and what earned it ("214 evasions"). */
	public static final class Standout {
		public final CrewState crew;
		public final String title, earned;
		Standout(CrewState crew, String title, String earned) { this.crew = crew; this.title = title; this.earned = earned; }
	}

	public final List<Line> journey = new ArrayList<Line>(), service = new ArrayList<Line>();
	public final List<Standout> crew = new ArrayList<Standout>();
	/** For a traded ship: where she came from, and since when. Null otherwise. */
	public String traded;
	/** Did the station see this journey begin (else the journey's counts can't be told from her service's)? */
	public boolean journeyKnown;

	private ShipStats() {}

	/** Her stats; {@code s} null (a ship not in the fleet) gives this journey's counts and her crew only. */
	public static ShipStats of(Vault v, Ship s, SavedGameState gs) {
		ShipStats st = new ShipStats();
		Properties start = s == null ? new Properties() : JourneyStart.read(v, s.id);
		st.journeyKnown = start.getProperty("defeated") != null;

		// this journey
		st.journey.add(new Line("Sector", Integer.toString(gs.getSectorNumber() + 1), 0));
		if (gs.getDifficulty() != null) st.journey.add(new Line("Difficulty", title(gs.getDifficulty().toString()), 0));
		if (st.journeyKnown) {
			count(st.journey, "Beacons explored", gs.getTotalBeaconsExplored() - Store.num(start, "beacons", 0), 0);
			count(st.journey, "Ships defeated", gs.getTotalShipsDefeated() - Store.num(start, "defeated", 0), 0);
			count(st.journey, "Scrap collected", gs.getTotalScrapCollected() - Store.num(start, "scrap", 0), 0);
			count(st.journey, "Crew hired", gs.getTotalCrewHired() - Store.num(start, "hired", 0), 0);
			String[][] vars = {{"killed_crew", "Enemy crew killed"}, {"lost_crew", "Crew lost"}, {"fired_shot", "Shots fired"}, {"used_missile", "Missiles fired"}};
			for (String[] k : vars) {
				if (!gs.hasStateVar(k[0])) continue;
				count(st.journey, k[1], gs.getStateVar(k[0]) - Store.num(start, k[0], 0), "lost_crew".equals(k[0]) ? -1 : 0);
			}
		}

		// her service
		if (s != null) {
			String commissioned = Museum.commissioned(v, s.id);
			if (!commissioned.isEmpty()) st.service.add(new Line("Commissioned", commissioned, 0));
			TradeMark m = TradeMark.of(v, s.id);
			if (m != null) {
				st.service.add(new Line("First commissioned by", m.original, 0));
				st.traded = "With you since " + m.date + ", from " + m.from + (m.original.equals(m.from) ? "" : "; first commissioned by " + m.original)
						+ ". Her service before is counted here too.";
			}
			int journeys = VoyageLog.journeys(v, s);
			if (journeys > 0) st.service.add(new Line("Journeys", Integer.toString(journeys), 0));
			st.service.add(new Line("Sectors visited", Integer.toString(VoyageLog.visited(v, s)), 0));
			int best = Math.max(Store.num(start, "best", 0), gs.getSectorNumber() + 1);
			st.service.add(new Line("Furthest sector", Integer.toString(best), 0));
			int wins = Museum.victories(v, s.id);
			if (wins > 0) st.service.add(new Line("Final victories", Integer.toString(wins), 1));
		}
		// FTL's totals run on across her journeys: they're her service's
		count(st.service, "Beacons explored", gs.getTotalBeaconsExplored(), 0);
		count(st.service, "Ships defeated", gs.getTotalShipsDefeated(), 0);
		count(st.service, "Scrap collected", gs.getTotalScrapCollected(), 0);
		count(st.service, "Crew hired", gs.getTotalCrewHired(), 0);

		// her crew's standouts: one title each, the most deserving first
		List<CrewState> own = SaveHelper.getOwnCrew(gs.getPlayerShip());
		standout(st, own, "Best Pilot", 0, "evasion");
		standout(st, own, "Best Gunner", 1, "kill");
		standout(st, own, "Best Engineer", 2, "repair");
		standout(st, own, "Longest Serving", 3, "jump survived", "jumps survived");
		standout(st, own, "Most Skilled", 4, "skill mastered", "skills mastered");
		return st;
	}

	private static void count(List<Line> to, String label, int n, int tone) {
		if (n <= 0) return; // nothing yet: left out rather than shown as 0
		to.add(new Line(label, String.format("%,d", n), tone));
	}
	private static String title(String s) { return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase(); }

	private static int stat(CrewState c, int which) {
		switch (which) {
			case 0: return c.getPilotedEvasions();
			case 1: return c.getCombatKills();
			case 2: return c.getRepairs();
			case 3: return c.getJumpsSurvived();
			default: return c.getSkillMasteriesEarned();
		}
	}
	private static void standout(ShipStats st, List<CrewState> own, String title, int which, String one) { standout(st, own, title, which, one, one + "s"); }
	private static void standout(ShipStats st, List<CrewState> own, String title, int which, String one, String many) {
		CrewState best = null;
		for (CrewState c : own) if (stat(c, which) > 0 && (best == null || stat(c, which) > stat(best, which))) best = c;
		if (best == null) return;
		int n = stat(best, which);
		st.crew.add(new Standout(best, title, String.format("%,d", n) + " " + (n == 1 ? one : many)));
	}
}
