package homeplanet.parser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.blerf.ftl.xml.Achievement;

import homeplanet.core.SafeFiles;
import homeplanet.vault.Vault;

/**
 * The Immersive career (career.txt in the Immersive fleet's folder): the choices made when it began, fixed from then
 * on (whether the stipend counts every achievement in the FTL profile or only those earned since, and whether
 * Immersive Mode has an FTL profile of its own), and the stipend months paid. Also what comes after a final victory
 * (FinalVictory), which can change at any time.
 */
public final class Career {
	private static final Logger log = LoggerFactory.getLogger(Career.class);
	private Career() { }

	/** Scrap in the Cargo Hold when a Sandbox career begins (an Immersive one's is its difficulty's). */
	public static final int STARTING_SCRAP = 25;
	/** The stipend: this much, plus the rank's multiple for each achievement counted, every SECTORS_PER_MONTH sectors (an Immersive career's: its difficulty's). */
	public static final int STIPEND_BASE = 20, SECTORS_PER_MONTH = 4;
	/** Sectors between stipends in the fleet in use. */
	public static int sectorsPerMonth() {
		CareerRules r = CareerRules.current();
		return r != null ? r.stipendSectors() : SECTORS_PER_MONTH;
	}

	static File file(File fleetRoot) { return new File(fleetRoot, "career.txt"); }
	private static Properties read(File fleetRoot) {
		Properties p = new Properties();
		File f = file(fleetRoot);
		if (!f.isFile()) return p;
		try { p.load(new java.io.StringReader(new String(SafeFiles.read(f), StandardCharsets.UTF_8))); }
		catch (IOException e) { log.warn("Could not read {}: {}", f, e.toString()); }
		return p;
	}
	private static void write(File fleetRoot, Properties p) throws IOException {
		java.io.StringWriter w = new java.io.StringWriter();
		p.store(w, "The Immersive career: its choices are fixed once made");
		SafeFiles.writeText(file(fleetRoot), w.toString(), false);
	}

	/** Has a career begun in this Immersive fleet (its folder)? */
	public static boolean started(File immersiveRoot) { return file(immersiveRoot).isFile(); }
	/** Does it have an FTL profile of its own (chosen when it began)? */
	public static boolean ownProfile(File immersiveRoot) { return "true".equals(read(immersiveRoot).getProperty("ownProfile")); }
	/** Does the stipend count every achievement in the FTL profile (chosen when it began), rather than only those earned since? */
	public static boolean salaryAll(File immersiveRoot) { return "true".equals(read(immersiveRoot).getProperty("salaryAll")); }

	/**
	 * This Immersive fleet's difficulty, or null if no career has begun. A career from before difficulties is written
	 * down as it was (stripping as Settings had it), once, and fixed from then on.
	 */
	public static CareerRules rules(File immersiveRoot) {
		if (!started(immersiveRoot)) return null;
		Properties p = read(immersiveRoot);
		CareerRules r = CareerRules.read(p);
		if (r != null) return r;
		r = CareerRules.earlier(homeplanet.core.HomePlanet.stripAllowed);
		r.write(p);
		try { write(immersiveRoot, p); } catch (IOException e) { log.warn("Could not record the career's rules: {}", e.toString()); }
		return r;
	}

	/** What comes after a final victory in this Immersive fleet (FinalVictory.NOTHING, RESCUE or REWARD). */
	public static String finalVictory(File immersiveRoot) { return read(immersiveRoot).getProperty("finalVictory", FinalVictory.NOTHING); }
	/** Sets what comes after a final victory in this Immersive fleet (its career must have begun). */
	public static void setFinalVictory(File immersiveRoot, String choice) throws IOException {
		Properties p = read(immersiveRoot);
		p.setProperty("finalVictory", choice);
		write(immersiveRoot, p);
	}

	/** Begins a career in the Immersive fleet now open, at Normal difficulty. */
	public static void start(boolean salaryAll, boolean ownProfile) throws IOException { start(salaryAll, ownProfile, true, CareerRules.of(CareerRules.NORMAL)); }
	/** Begins a career in the Immersive fleet now open, at this difficulty: its choices, the starting scrap, and a Kestrel Type A to command. */
	public static void start(boolean salaryAll, boolean ownProfile, CareerRules rules) throws IOException { start(salaryAll, ownProfile, true, rules); }
	/** Begins a career in the fleet now open; a Sandbox fleet's (Career messages) brings no ship (it has its own) and no difficulty. */
	public static void start(boolean salaryAll, boolean ownProfile, boolean withShip) throws IOException { start(salaryAll, ownProfile, withShip, null); }
	private static void start(boolean salaryAll, boolean ownProfile, boolean withShip, CareerRules rules) throws IOException {
		Vault v = Vault.get();
		Properties p = new Properties();
		p.setProperty("salaryAll", Boolean.toString(salaryAll));
		p.setProperty("ownProfile", Boolean.toString(ownProfile));
		p.setProperty("paidMonths", "0");
		p.setProperty("sectorsAtStart", Integer.toString(v.sectorsSeen()));
		if (rules != null) rules.write(p);
		write(v.root, p);
		CareerRules.forget();
		int scrap = rules != null ? rules.startingScrap() : STARTING_SCRAP;
		v.depositToStorage(scrap);
		if (withShip) v.grantFreeCommand("an Immersive career began", FreeCommand.KESTREL); // a Kestrel Type A, as a new FTL game starts
		homeplanet.core.HistoryLog.entry("CAREER", (v.immersive ? "Immersive" : "Sandbox") + " career begun: stipend counts " + (salaryAll ? "every achievement" : "achievements earned from now on")
				+ (ownProfile ? "; its own FTL profile" : "") + "; " + scrap + " scrap in the Cargo Hold" + (rules != null ? "; difficulty " + rules.describe() : ""));
	}

	/** The achievements the stipend counts now (real ones, not FTL's hidden unlock markers). */
	static int achievementsCounted(Unlocks u) {
		if (u == null || u.problem() != null) return 0;
		java.util.Set<String> ids = salaryAll(Vault.get().root) ? u.achievements() : UnlockGrants.newAchievements(u);
		int n = 0;
		for (String id : ids) {
			Achievement a = net.blerf.ftl.parser.DataManager.get().getAchievement(id);
			if (a != null && !a.isVictory() && !a.isQuest()) n++;
		}
		return n;
	}
	/** One month's stipend at this rank (0 Commander, 1 Captain, 2 Commodore): 20, plus (rank + 1) for each achievement counted. */
	public static int stipend(int rank, int achievements) {
		return STIPEND_BASE + achievements * (rank + 1);
	}
	/** Whole months of travel not yet paid for (every SECTORS_PER_MONTH sectors since the career began). */
	static int unpaidMonths() {
		Vault v = Vault.get();
		Properties p = read(v.root);
		int start = Integer.parseInt(p.getProperty("sectorsAtStart", "0")), paid = Integer.parseInt(p.getProperty("paidMonths", "0"));
		return Math.max(0, (v.sectorsSeen() - start) / sectorsPerMonth() - paid);
	}
	/** Records months as paid (or, with a negative count, takes them back after a failed payment). */
	static void markPaid(int months) throws IOException {
		File root = Vault.get().root;
		Properties p = read(root);
		p.setProperty("paidMonths", Integer.toString(Integer.parseInt(p.getProperty("paidMonths", "0")) + months));
		write(root, p);
	}
}
