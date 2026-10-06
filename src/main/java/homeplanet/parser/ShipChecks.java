package homeplanet.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.blerf.ftl.parser.DataManager;

/**
 * The one set of checks a ship layout goes through, whether she's a design from scratch, a remodel of a game ship or
 * an overhaul. Problems stop her being built (or finalized); warnings are worth knowing but she'd still fly.
 */
public final class ShipChecks {
	private ShipChecks() { }

	public enum Context {
		/** Design Ship: everything is hers, including the art and mounts. */
		DESIGN,
		/** Remodel: the model's rooms and art stay; systems and doors move. */
		REMODEL,
		/** Remodel's overhaul: rooms, art and mounts editable, but her weapon slots stay the model's. */
		OVERHAUL
	}

	public static final class Report {
		public final List<String> problems = new ArrayList<String>();
		public final List<String> warnings = new ArrayList<String>();
		public boolean sound() { return problems.isEmpty(); }
	}

	/**
	 * Checks the layout. {@code requiredWeaponSlots} is the model's weapon slots for an overhaul (each needs a mount),
	 * or null. {@code otherNames} are the other designs' names, to catch a duplicate (or null).
	 */
	public static Report check(ShipDesign d, Context ctx, Integer requiredWeaponSlots, List<String> otherNames) {
		Report r = new Report();
		List<String> p = r.problems, w = r.warnings;
		boolean art = ctx != Context.REMODEL;
		if (ctx == Context.DESIGN && d.name.trim().isEmpty()) p.add("She needs a name.");
		if (otherNames != null && !d.name.trim().isEmpty()) {
			for (String n : otherNames) if (n.trim().equalsIgnoreCase(d.name.trim())) { p.add("Another design is already called " + d.name.trim() + "."); break; }
		}
		if (d.rooms.isEmpty()) { p.add("There are no rooms."); return r; }
		if (!d.systems.containsKey("pilot")) p.add("No Piloting: she can't dodge or jump without it.");
		if (!d.systems.containsKey("engines")) p.add("No Engines: she can't jump without them.");
		// one system per room (a Medbay and a Clone Bay may share: only one of them is ever installed)
		for (int i = 0; i < d.rooms.size(); i++) {
			List<String> here = d.systemsIn(i);
			if (here.size() > 1 && !(here.size() == 2 && here.contains("medbay") && here.contains("clonebay")))
				p.add("Room " + i + " holds " + here + ": one system per room.");
		}
		// every system sits in a room that exists, with its station on a square the room has
		for (CompanionMod.Sys s : d.systems.values()) {
			if (s.room < 0 || s.room >= d.rooms.size()) { p.add(title(s.id) + " is in room " + s.room + ", which doesn't exist."); continue; }
			ShipDesign.Room room = d.rooms.get(s.room);
			if (s.square != null && (s.square < 0 || s.square >= room.w * room.h))
				p.add(title(s.id) + "'s station is on square " + s.square + ", but room " + s.room + " has only " + room.w * room.h + ".");
		}
		for (String id : new ArrayList<String>(d.notAtStart)) if (!d.systems.containsKey(id)) d.notAtStart.remove(id); // a stray entry: mended, not reported
		// artillery: its weapon, its mount
		int artilleryMounts = 0;
		for (ShipDesign.Mount m : d.mounts) if (m.artillery) artilleryMounts++;
		if (d.systems.containsKey("artillery")) {
			if (art && !d.art.isEmpty() && artilleryMounts == 0) p.add("Artillery needs its gun's mount: press Artillery mount, then click the art.");
			if (d.systems.get("artillery").weapon == null) p.add("Artillery needs a weapon: choose it with Artillery weapon (or in Build blueprint).");
		} else if (artilleryMounts > 0) {
			p.add("She has an artillery mount but no Artillery system: remove the mount, or add the system.");
		}
		// weapon mounts
		if (art) {
			int weaponMounts = d.mounts.size() - artilleryMounts;
			if (d.art.isEmpty()) w.add("No hull art yet.");
			else if (!ShipArt.available(d.art, d.art.startsWith("game:") ? "_base" : "")) w.add("Her hull art is missing (" + d.art + "): the Kestrel's stands in until it's back, so ships built from her still fly.");
			else if (weaponMounts == 0) p.add("She needs at least one weapon mount.");
			if (!d.floor.isEmpty() && !d.floorFromRooms() && ShipArt.available(d.art, d.art.startsWith("game:") ? "_base" : "") && ShipArt.available(d.floor, d.floor.startsWith("game:") ? "_floor" : "")) {
				// FTL draws the floor at the hull's corner plus its offset: one that sticks out of the hull was most likely drawn for another hull
				java.awt.image.BufferedImage hull = ShipArt.scaled(ShipArt.load(d.art, d.art.startsWith("game:") ? "_base" : ""), d.artScale), fl = ShipArt.scaled(ShipArt.load(d.floor, d.floor.startsWith("game:") ? "_floor" : ""), d.artScale);
				if (hull != null && fl != null && (d.floorX < 0 || d.floorY < 0 || d.floorX + fl.getWidth() > hull.getWidth() || d.floorY + fl.getHeight() > hull.getHeight()))
					w.add("Her floor picture (" + fl.getWidth() + " x " + fl.getHeight() + " at " + d.floorX + ", " + d.floorY + ") sticks out of her hull picture (" + hull.getWidth() + " x " + hull.getHeight()
							+ "): it may have been drawn for another hull. Choose no floor, or one drawn from the rooms.");
			}
			// FTL draws slot n's weapon on mount n: a slot past her mounts has nowhere to draw (the game's own ships carry spare mounts, the Kestrel 8 for 4 slots)
			if (ctx == Context.DESIGN && !d.art.isEmpty() && weaponMounts < DesignExport.weaponSlots(d))
				w.add("She has " + DesignExport.weaponSlots(d) + " weapon slots but " + weaponMounts + " weapon mount" + (weaponMounts == 1 ? "" : "s") + ": a weapon in a slot past her mounts has nowhere to draw. Add mounts, or fewer slots.");
			if (requiredWeaponSlots != null && weaponMounts < requiredWeaponSlots)
				p.add("She has " + requiredWeaponSlots + " weapon slots but " + weaponMounts + " weapon mount" + (weaponMounts == 1 ? "" : "s") + ": each slot needs a mount.");
		}
		// the doors: every room reachable; the biggest group of connected rooms is "the ship", the rest are cut off
		List<Set<Integer>> groups = new ArrayList<Set<Integer>>();
		Set<Integer> seen = new HashSet<Integer>();
		for (int start = 0; start < d.rooms.size(); start++) {
			if (!seen.add(start)) continue;
			Set<Integer> group = new java.util.TreeSet<Integer>();
			group.add(start);
			Deque<Integer> todo = new ArrayDeque<Integer>();
			todo.add(start);
			while (!todo.isEmpty()) {
				int room = todo.poll();
				for (CompanionMod.Door door : d.doors) {
					if (door.b < 0) continue;
					int o = door.a == room ? door.b : door.b == room ? door.a : -1;
					if (o >= 0 && seen.add(o)) { group.add(o); todo.add(o); }
				}
			}
			groups.add(group);
		}
		if (groups.size() > 1) {
			Set<Integer> main = groups.get(0);
			for (Set<Integer> g : groups) if (g.size() > main.size()) main = g;
			List<Integer> cut = new ArrayList<Integer>();
			for (int i = 0; i < d.rooms.size(); i++) if (!main.contains(i)) cut.add(i);
			w.add((cut.size() == 1 ? "Room " + cut.get(0) + " has" : "Rooms " + cut + " have") + " no door to the rest of the ship: her crew can't get in (FTL allows it).");
		}
		if (!d.systems.containsKey("oxygen")) w.add("No Oxygen: her crew will suffocate.");
		if (!d.systems.containsKey("shields")) w.add("No Shields.");
		if (!d.systems.containsKey("weapons") && !d.systems.containsKey("drones")) w.add("No Weapons or Drone Control: she can't fight.");
		int starting = startingSystems(d);
		if (ctx == Context.DESIGN && starting > SaveHelper.SYSTEMS_MAX)
			w.add("She starts with " + starting + " systems; FTL's System Limit is " + SaveHelper.SYSTEMS_MAX + " (subsystems aside). Each one past it is a custom work order ("
					+ homeplanet.core.Economy.commissionWorkOrder() + " scrap) when commissioning costs scrap.");
		boolean airlock = false;
		for (CompanionMod.Door door : d.doors) if (door.b < 0) airlock = true;
		if (!airlock) w.add("No airlocks: she can't vent fires or boarders.");
		// past the game's own numbers: FTL takes her, but its bars and upgrade screen are drawn for the vanilla ones
		if (ctx == Context.DESIGN) {
			List<String> past = new ArrayList<String>();
			if (d.weaponSlots > VanillaMax.weaponSlots()) past.add(d.weaponSlots + " weapon slots (the game's ships have up to " + VanillaMax.weaponSlots() + "; its weapon bar is drawn for that many, the rest sit off its edge)");
			if (d.droneSlots > VanillaMax.droneSlots()) past.add(d.droneSlots + " drone slots (up to " + VanillaMax.droneSlots() + " in the game; its drone bar is drawn for that many)");
			for (CompanionMod.Sys s : d.systems.values())
				if (s.power > VanillaMax.system(s.id)) past.add(title(s.id) + " at level " + s.power + " (the game's top is " + VanillaMax.system(s.id) + "; its upgrade screen won't show past that)");
			if (!past.isEmpty()) w.add("Past vanilla: " + String.join("; ", past) + ".");
			if (d.loadout != null && d.loadout.crewTotal() > VanillaMax.CREW) p.add("She starts with " + d.loadout.crewTotal() + " crew: FTL's ships hold " + VanillaMax.CREW + ".");
			if (d.loadout != null && d.loadout.crewTotal() == 0) w.add("No starting crew set: she'd start with one human.");
		}
		// the loadout, against the ship
		if (d.loadout != null) {
			int slots = DesignExport.weaponSlots(d);
			if (art && d.loadout.weapons.size() > slots) w.add("She starts with " + d.loadout.weapons.size() + " weapons but has " + slots + " weapon slots: the extra ones go in her cargo hold.");
			if (d.loadout.drones.size() > d.droneSlots) w.add("She starts with " + d.loadout.drones.size() + " drones but has " + d.droneSlots + " drone slots: the extra ones go in her cargo hold.");
			if (!d.loadout.drones.isEmpty() && !d.systems.containsKey("drones")) w.add("She starts with drones but has no Drone Control: they go in her cargo hold.");
			// hacking launches its drone with a drone part (FTL wiki, Hacking); a ship that starts without any can't use it until she buys some
			if (d.loadout.droneParts == 0 && d.systems.containsKey("hacking") && !d.notAtStart.contains("hacking"))
				w.add("Hacking needs a drone part to launch, and she starts with none.");
			String power = powerShort(d);
			if (power != null) w.add(power);
		}
		return r;
	}

	/** The systems she starts with, as FTL's System Limit counts them (subsystems aside; a Medbay and Clone Bay are one: with both ticked, Commission builds the Clone Bay alone). */
	static int startingSystems(ShipDesign d) {
		int n = 0;
		for (String id : d.systems.keySet()) {
			net.blerf.ftl.parser.SavedGameParser.SystemType t = net.blerf.ftl.parser.SavedGameParser.SystemType.findById(id);
			if (t != null && !t.isSubsystem() && !d.notAtStart.contains(id)) n++;
		}
		if (d.systems.containsKey("medbay") && d.systems.containsKey("clonebay") && !d.notAtStart.contains("medbay") && !d.notAtStart.contains("clonebay")) n--;
		return n;
	}

	/** When her reactor can't power Oxygen, Shields and her starting weapons and drones together: the note, else null. */
	static String powerShort(ShipDesign d) {
		if (d.loadout == null) return null;
		int need = 0;
		CompanionMod.Sys o = d.systems.get("oxygen"), sh = d.systems.get("shields");
		if (o != null && !d.notAtStart.contains("oxygen")) need += 1;
		if (sh != null && !d.notAtStart.contains("shields")) need += Math.max(1, sh.power);
		try {
			DataManager dm = DataManager.get();
			for (String w : d.loadout.weapons) { net.blerf.ftl.xml.WeaponBlueprint b = dm.getWeapon(w); if (b != null) need += b.getPower(); }
			for (String w : d.loadout.drones) { net.blerf.ftl.xml.DroneBlueprint b = dm.getDrone(w); if (b != null) need += b.getPower(); }
		} catch (Exception e) { return null; }
		return need > d.reactor ? "Her reactor (" + d.reactor + ") can't power Oxygen, Shields and her starting weapons and drones together (they need " + need + ")." : null;
	}

	/** A design that can go into the companion mod: built, and free of problems as a design. */
	public static boolean buildable(ShipDesign d) {
		return d.built && check(d, Context.DESIGN, null, null).sound();
	}

	static String title(String id) { return homeplanet.model.Items.systemTitle(id); }
}
