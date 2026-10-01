# What the station can see in FTL's save

The homework for Idea B (our own achievements and triggers): everything The Home Planet Station can see change in
FTL's save, so achievement ideas can be picked from what's actually there. Built from the save parser and from
heromedel's save-logger session of 2026-09-30 (a Lanius cruiser in the last sector, the Rebel Flagship's three stages).

How sure each line is:
**seen** = it changed in the logged session; **parser** = the save has it, but we haven't watched it change yet;
**test** = worth one logged run before building on it.

## When FTL writes the save

The station only sees what FTL writes, and FTL doesn't write during a fight. From the logged session:

1. **Arriving at a beacon** (after each jump): the new beacon, the nearby ship if there is one (her hull, crew,
   weapons, AI), the event text. *seen*
2. **Each event choice**: the choice is added to "Last Event Choices". *seen*
3. **When a fight ends**: the nearby ship is gone, the player's hull, crew, supplies and FTL's counters are updated. *seen*
4. **Collecting the reward** (scrap, fuel...). *seen*
5. **Quitting to the menu**. *parser*
6. **Losing or winning**: continue.sav is deleted; the profile (ae_prof.sav) keeps the victory, Top Scores and FTL's
   own achievements. *seen*

So any trigger is "something changed between two saves", usually one beacon or one event apart. Exactly how
something happened mid-fight isn't visible; what it left behind is.

## FTL's own counters (State Vars)

FTL keeps running totals for its achievements and events, per run, in the save. They're the best source: FTL
counts them itself. Values from the logged Lanius run, deep in sector 8:

| Counter | What it counts | Logged value | Sure? |
|---|---|---|---|
| `dead_crew` | Ships defeated by killing all their crew | 5 | parser (needs test) |
| `killed_crew` | Enemy crew killed (any way) | 41 → 43 after one fight | seen |
| `lost_crew` | Your crew lost (killed, left behind, taken by events), even if cloned later | 4 → 5 | seen |
| `suffocated_crew` | Unknown: 3,665 on an airless Lanius ship, so probably time spent suffocating, not a head count | 3665 | test |
| `destroyed_rock` | Rock ships destroyed, pirates included | 1 | parser |
| `fired_shot` | Shots, beams and projectiles fired | 410 → 434 | seen |
| `used_missile` | Missiles and bombs fired | 59 → 64 | seen |
| `used_drone` | Drone parts used | 4 | parser |
| `offensive_drone` | Attack drones powered up | (not in this run) | parser |
| `teleported` | Teleporter uses, either way | (not in this run) | parser |
| `store_purchase` | Things bought at stores (not repairs) | 7 | parser |
| `store_repair` | Store repair clicks | 6 | parser |
| `system_upgrade` | System upgrades past the ship's start | 17 | parser |
| `reactor_upgrade` | Reactor bars bought past the start | 10 | parser |
| `weapon_upgrade` | Weapons system upgrades past the start | 2 | parser |
| `env_danger` | Jumps into hazards (sun, pulsar, asteroids...) | 18 | parser |
| `nebula` | Jumps into nebulas | 20 | parser |
| `blue_alien` | Blue (race-only) event choices taken | 2 | parser |
| `higho2` | Arrivals with oxygen over 20% | (not in this run) | parser |

## The run's totals

Ships defeated, beacons explored, scrap collected, crew hired; sector number; the difficulty; Advanced Edition
on or off. All *seen*. The station already uses beacons and sectors (the stipend, reply chains).

## Each crew member

Name, race, health, skills (piloting, engines, shields, weapons, repair, combat), and her own career counts:
**repairs, combat kills, piloted evasions, jumps survived, skill masteries**. *seen* (piloted evasions went 87 → 102
in one fight). A crew member missing from the next save is gone; together with `lost_crew` going up, she died or was
left behind.

## The ship

Hull, fuel, missiles, drone parts, scrap; every system's level, power, damage and ionization; weapons, drones,
augments, cargo; rooms' oxygen, fires, hull breaches; the reactor. All *seen*. (The One Point of Hull trigger uses
hull and the nearby ship.)

## The nearby ship (an enemy, or a friendly)

Present only while she's at the beacon, so at arrival and at event choices: her class, hull, hostile or not, crew
(names, races, health, where they stand), systems, weapons, and her AI: **surrender offered** (FTL sets it the moment
the surrender event fires, before the player answers), the hull at which she'll surrender or run, the escape timer.
*seen* (all of it at arrival; the flag itself: test).

## Events

The current event's text id (e.g. `event_BOSS_ESCAPED_text`), the last event id, and the choices made at this beacon
("0", then "0,0"...). A surrender comes as an event with two choices, so **accepting** and **refusing** look
different. *seen* for the text and choices; the surrender ids: test.

## The profile (ae_prof.sav)

FTL's own achievements (with difficulty), ship unlocks, victories, Top Scores, and lifetime totals. *seen*. Already
used for promotions, Immersive rank and the museum.

## What it can't see

- How each enemy died: a crew kill is counted (`dead_crew`), but not whether by fire, suffocation, boarders or
  weapons.
- Anything during a fight that leaves nothing behind (a dodge, a near miss, a moment at 1 hull that was repaired
  before the fight ended).
- Which store the scrap was spent at, or for what, beyond the counters and what's now aboard.
- Anything between two saves, if the player closes FTL without it saving.

## Your two examples

**50 surrenders accepted in a row (a Kestrel holding up Federation standards).** Workable. When a surrender is offered,
the nearby ship's AI says so; the next save has the player's choice on the surrender event. Accepted adds one to the
streak; refused, or the ship destroyed after an offer, resets it. Needs one logged run with a surrender accepted and
one refused, to be sure of the event ids.

**25 crews killed by asphyxiation on a Mantis ship.** Mostly workable. `dead_crew` counts ships won by killing their
whole crew; "by asphyxiation" isn't recorded. Close enough: at the last save before the win, the enemy's crew stood in
rooms with low oxygen (or the oxygen system was down). Simpler and honest: **25 crew kills on a Mantis ship**, and the
letter can still accuse you of fighting like a Slug. Needs a logged crew kill to confirm `dead_crew` and to see what
`suffocated_crew` really counts.

## Other achievements this makes possible

1. **Pacifist's purse**: reach a sector with `fired_shot` still at 0 (or very low).
2. **Sharpshooter**: a run's `fired_shot` passes 1,000.
3. **Nebula runner**: 20 nebula jumps in one run (`nebula`).
4. **Danger seeker**: 15 jumps into hazards (`env_danger`).
5. **Upgrader**: 20 system upgrades in a run (`system_upgrade`).
6. **No one left behind**: reach sector 8 with `lost_crew` at 0.
7. **Veteran**: a crew member with 100 jumps survived, or 50 combat kills, or every skill mastered.
8. **Rock breaker**: 10 Rock ships destroyed (`destroyed_rock`).
9. **Repairman**: a crew member with 200 repairs.
10. **Big spender**: 30 store purchases in one run.

Each would need its reward and letter, and the counters are per run: a fleet-wide achievement adds them up across
ships, as the station already does for beacons.
