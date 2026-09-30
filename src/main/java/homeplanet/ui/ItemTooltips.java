package homeplanet.ui;

import net.blerf.ftl.parser.DataManager;
import net.blerf.ftl.xml.AugBlueprint;
import net.blerf.ftl.xml.DroneBlueprint;
import net.blerf.ftl.xml.WeaponBlueprint;

/**
 * Hover text for weapons, drones and augments, built from the game data in ftl.dat.
 * Returns null for anything unknown (placeholders, items from removed mods), so the plain name shows instead.
 */
public class ItemTooltips {

	public static String tooltip(String id) {
		if (id == null || id.isEmpty()) return null;
		WeaponBlueprint w = DataManager.get().getWeapons().get(id);
		if (w != null) return weapon(w);
		DroneBlueprint d = DataManager.get().getDrones().get(id);
		if (d != null) return drone(d);
		AugBlueprint a = DataManager.get().getAugments().get(id);
		if (a != null) return augment(a);
		return null;
	}

	private static String weapon(WeaponBlueprint w) {
		String desc = cleanDesc(text(w.getDescription()));
		String d = desc.toLowerCase();
		boolean flak = "BURST".equals(w.getType()) && w.getMissiles() == 0; // Swarm Missiles share the flak type
		StringBuilder sb = start(text(w.getTitle()), null);
		row(sb, "Power", "" + w.getPower());
		WeaponBlueprint.Boost boost = w.getBoost();
		boolean chainCooldown = boost != null && "cooldown".equals(boost.type) && boost.count > 0;
		boolean chainDamage = boost != null && "damage".equals(boost.type) && boost.count > 0;
		if (w.getCooldown() > 0) {
			String cd = secs(w.getCooldown()) + " s";
			if (chainCooldown) {
				cd += " \u2192 " + secs(Math.max(0f, w.getCooldown() - boost.amount * boost.count)) + " s after " + boost.count + " volleys";
			}
			row(sb, "Charge time", cd);
		}
		if (w.getDamage() > 0) {
			if (flak) row(sb, "Damage", w.getDamage() + " per projectile");
			else row(sb, "Damage", w.getDamage() + (w.getShots() > 1 ? " × " + w.getShots() + " shots" : ""));
		} else if (w.getShots() > 1) {
			row(sb, "Shots", "" + w.getShots());
		}
		// Rows the description already explains are left out
		if (w.getChargeLevels() > 0 && !d.contains("charge")) row(sb, "Charges", "up to " + w.getChargeLevels() + " shots");
		if (w.getIonDamage() > 0) {
			String ion = "" + w.getIonDamage();
			if (chainDamage) ion += " \u2192 " + Math.round(w.getIonDamage() + boost.amount * boost.count) + " after " + boost.count + " volleys";
			row(sb, "Ion damage", ion);
		}
		if (!"BOMB".equals(w.getType()) && !d.contains("shield")) {
			if (w.getShieldPiercing() >= 5) row(sb, "Shields", "ignores shields");
			else if (w.getShieldPiercing() > 0) row(sb, "Shield piercing", w.getShieldPiercing() + " layer" + (w.getShieldPiercing() > 1 ? "s" : ""));
		}
		if (w.getFireChance() > 0) row(sb, "Fire chance", w.getFireChance() * 10 + "%");
		if (w.getBreachChance() > 0) row(sb, "Breach chance", w.getBreachChance() * 10 + "%");
		if (w.getStunChance() > 0) row(sb, "Stun chance", w.getStunChance() * 10 + "%");
		if (w.getStun() > 0 && !d.contains("stun")) row(sb, "Stuns crew", w.getStun() + " s");
		// FTL adds these bonuses to the base damage; show the totals, and only when they do something
		int crew = w.getDamage() + w.getPersDamage();
		int sys = w.getDamage() + w.getSysDamage();
		if (w.getPersDamage() != 0 && crew > 0) row(sb, "Crew damage", "" + crew);
		if (w.getSysDamage() != 0 && sys > 0) row(sb, "System damage", "" + sys);
		if (w.isHullBust()) row(sb, "Hull bonus", "double hull damage on rooms without systems");
		if (w.getMissiles() > 0) row(sb, "Uses", w.getMissiles() + " missile" + (w.getMissiles() > 1 ? "s" : "") + " per volley"); // per firing, however many projectiles (Pegasus, Swarm)
		return finish(sb, desc, w.getCost());
	}

	// Some enemy-only items carry developer placeholder text; don't show it.
	private static String cleanDesc(String desc) {
		String d = desc.toLowerCase();
		if (d.contains("should never") || d.contains("should not be seen") || d.contains("bug")) return "";
		return desc;
	}

	private static String drone(DroneBlueprint d) {
		StringBuilder sb = start(text(d.getTitle()), null);
		row(sb, "Power", "" + d.getPower());
		return finish(sb, cleanDesc(text(d.getDescription())), d.getCost());
	}

	private static String augment(AugBlueprint a) {
		StringBuilder sb = start(text(a.getTitle()), null);
		return finish(sb, text(a.getDescription()), a.getCost());
	}

	private static StringBuilder start(String title, String kind) {
		StringBuilder sb = new StringBuilder("<html><b>").append(homeplanet.parser.XmlText.text(title)).append("</b>");
		return sb.append("<table cellpadding=0 cellspacing=0>");
	}

	private static void row(StringBuilder sb, String name, String value) {
		sb.append("<tr><td>").append(homeplanet.parser.XmlText.text(name)).append(":&nbsp;&nbsp;</td><td>").append(homeplanet.parser.XmlText.text(value)).append("</td></tr>");
	}

	private static String finish(StringBuilder sb, String desc, int cost) {
		sb.append("</table>");
		if (desc != null && desc.length() > 0) {
			sb.append("<div style='width:260px; margin-top:4px'>").append(homeplanet.parser.XmlText.text(desc)).append("</div>");
		}
		if (cost > 0) {
			sb.append("<div style='margin-top:4px'>Price: ").append(cost).append(" scrap &nbsp;·&nbsp; Sells for ").append(cost / 2).append("</div>");
		}
		return sb.append("</html>").toString();
	}

	private static String text(net.blerf.ftl.xml.DefaultDeferredText t) {
		return t == null ? "" : t.getTextValue();
	}


	// 16 -> "16", 11.1 -> "11.1"
	private static String secs(float f) {
		float r = Math.round(f * 10) / 10f;
		return (r == Math.round(r)) ? "" + Math.round(r) : "" + r;
	}
}
