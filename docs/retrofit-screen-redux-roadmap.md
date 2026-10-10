# Retrofit screen redux: roadmap

heromedel, 6.42: the Refit tab gets a power distribution simulation under the ship's picture, so a player can see how
the ship's reactor will go round in FTL before taking her out. It is a simulation: nothing it does is saved.

## What it is

- The ship's picture as now, the words under it (name, model, Cargo Hold) moved up to make room; a gap, then a heading,
  "Power Distribution Simulation".
- Below it, FTL's own bottom panel, drawn almost exactly as the game draws it: the reactor column and each powered
  system with its bars. **No subsystems** (heromedel): no Doors, Piloting, Sensors or Backup Battery.
- The weapon and drone slots, as in FTL: one box for each slot the ship's model has, empty ones drawn empty, a
  weapon that isn't powered grey (as the game draws it, not orange).
- It opens as the save left the ship: each system's power, which weapons and drones were on, damaged bars in red,
  Zoltan crew powering their room.
- Clicking works as in FTL: a system takes a bar from the reactor (left click) or gives one back (right click), up
  to its level, never into a damaged bar. A weapon or drone is chosen by the player and is all or nothing: clicked on,
  it takes its full power if it fits within Weapon Control's (or Drone Control's) level and the reactor bars left,
  else nothing happens; clicked again, it's off. Any combination that fits, in any order. A Reset puts it back as the
  save had it.
- The "Weapons and drones" list in the systems column goes: the panel shows it.

## The order (heromedel: take each step slow, don't move on until it's right)

1. **Study FTL.** Run it under Wine and screenshot the bottom panel as it starts, systems clicked up and down,
   weapons and drones powered and not, empty slots, damaged bars, a weapon dragged between slots; list the panel's
   pictures in ftl.dat (drawn from the player's own ftl.dat at runtime, never committed) and its sounds.
2. **The look.** The reactor column and the powered systems' panels, side by side with FTL's, until they match.
3. **Weapons and drones in their slots,** side by side with FTL's.
4. **The feel.** Clicking: what lights, what refuses, what FTL does at the edges (no power left, a full system).
5. **Sound,** if FTL's own clicks can be played from its ftl.dat.
6. **Later:** dragging weapons and drones between slots as in FTL. That one changes the ship (the slot order is in
   her save), so it would be kept by the Cargo Bay's Save, not the simulation.

## Notes from the study (6.42, FTL 1.6.14 under Wine)

**The look**
- The panel runs along the bottom: the reactor column at the far left, then each powered system's icon in a row,
  each with its bars stacked above it, a thin white wire running under them all to the weapons box, the drones icon
  and the drones box, then the subsystems box (left out here).
- The order is fixed: Shields, Engines, Medbay or Clone Bay, Oxygen, Teleporter, Cloaking, Artillery, Mind Control,
  Hacking, Weapons (its box beside it), Drones (its box beside it). A system the ship hasn't got leaves no gap.
- The reactor column shows the power **left**: unused bars green, used ones empty outlines, the top ones too. A hatched
  strip runs down its right side (`img/wireUI/wire_full.png`). With nothing left, "NOT ENOUGH POWER" sits above it
  in grey.
- A system's bars: powered green, unpowered an empty white outline, a damaged bar red with a slash at the top of the
  stack (a yellow part on it while it's being repaired). Its round icon is green when powered, grey when not, orange
  when damaged (`img/icons/s_<id>_green1/grey1/orange1/red1.png`, 64 pixels). A crew marker sits above a manned,
  powered system.
- Teleporter, Cloaking, Mind Control and Hacking have a small button panel beside the icon (send and return, the
  power button, the hacking drone). Artillery has a tall charge meter beside its bar.
- The weapons box: one slot per weapon slot of the model, each with the weapon's name, a small picture
  (`img/systemUI/weapbox_icon_W_*`), its power as small bars at the bottom left, a charge meter down the left edge,
  and its number key (1 to 4) at the bottom right. An empty slot is a dark box, outlined. Under it a "WEAPONS" tab
  and the Autofire button. The drones box is the same (keys 5 and 6, `weapbox_icon_D_*`), with a "DRONES" tab.
  Frames: `img/box_weapons_bottom2/3/4.png`, `img/box_weapons_bottom_label.png`.
- Unpowered weapon or drone: grey text, grey frame, outlined bars. Powered: white text and frame, bars filled white
  (charging, as FTL shows it paused); it turns green only when charged (not a simulation's business).

**The feel**
- A system: left click adds a bar, right click takes one off. Shields go two bars at a time (a barrier), and won't
  take a lone bar (with one bar damaged and two powered, nothing more goes in). Mind Control and Hacking take one bar a
  click.
- A weapon or drone: a click switches it on with its whole power, or off. Its control system's bars fill to match.
  When it doesn't fit, "NOT ENOUGH SYSTEM POWER" flashes above the panel and nothing changes.
- With the reactor empty, a system won't take a bar: "NOT ENOUGH POWER" over the reactor.
- Dragging a weapon slides the others along as it goes; the number keys follow the position, and power goes with
  the weapon.
- Hovering a system: a black box with a white border: "Shields: <FTL's description>", "Level 2: One Shield Barrier",
  "Status: -Partially Powered -Damaged" (or "-Fully Powered", "-Unpowered"), and its hotkeys. A weapon slot: "Hotkey:
  1 / Click and drag to rearrange".

**Sound** (`audio/waves/ui/`): `select_up1.wav` (a bar on), `select_down2.wav` (a bar off), `select_b_fail1.wav`
(refused).
