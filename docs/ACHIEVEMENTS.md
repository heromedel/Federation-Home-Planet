# FTL's achievements

Every achievement in FTL 1.6.x (`data/achievements.xml` and `data/text_achievements.xml` in ftl.dat), with what The
Home Planet Station says about it. Kept so no session has to dig them out of ftl.dat again (heromedel, 5.57).

- **Name** and **What FTL asks** are FTL's own words.
- **Our deed line** is how a rank letter's accolade tells it (`homeplanet.parser.Accolades.DEEDS`): the deed itself, as a
  phrase under "Of particular note in the discussions was:", never the rule. Victories (beating the Rebel Flagship) are never scored or
  told (hard rule 1); an achievement with no line here gets the general words.
- A ship's achievements say which cruiser earned them; ship classes are FTL's (the Kestrel Cruiser, the Zoltan Cruiser…).
- Check new lines against `docs/LORE_COMPONENTS.md` before adding them.

| Id | Ship | Name | What FTL asks | Our deed line |
|---|---|---|---|---|
| `ACH_SECTOR_5` | (any) | Just Getting Started | Get to sector 5. | One of your ships fighting her way to sector 5. |
| `ACH_SECTOR_8` | (any) | Federation Base in Range | Get to sector 8. | One of your ships making it all the way to sector 8, within reach of the Federation's own base. |
| `ACH_WIN_EASY` | (any) | Federation Victory (Easy) | Beat the boss on Easy. | (not scored: a victory) |
| `ACH_WIN_NORMAL` | (any) | Federation Victory (Normal) | Beat the boss on Normal. | (not scored: a victory) |
| `ACH_UNLOCK_ALL` | (any) | Your Own Fleet | Unlock the Type A layout for every playable ship. | Flying every kind of cruiser the Federation knows of. |
| `ACH_SCRAP` | (any) | Rule Ten: Greed is Eternal | Collect 10,000 scrap across all games. | Your fleet hauling in over 10,000 scrap since you took command. |
| `ACH_SHIPS` | (any) | Warlord | Defeat 1000 ships across all playthroughs. | Your defeat of a thousand enemy ships. |
| `ACH_NO_UPGRADES` | (any) | I don't need no stinkin' upgrades! | Get to sector 5 with no system/reactor upgrades. | Taking a ship to sector 5 without a single upgrade. |
| `ACH_PACIFIST` | (any) | Coming in for my Pacifism run! | Get to sector 5 without firing a shot, using an offensive drone, or teleporting. | Reaching sector 5 without firing a shot. |
| `ACH_NO_REPAIR` | (any) | On a Wing and a Prayer | Get to sector 5 without repairing at a store. | Flying to sector 5 without once stopping for repairs at a store. |
| `ACH_NO_MISSILES` | (any) | Ballistophobia | Get to sector 8 without using missiles/bombs. | Reaching sector 8 without firing a single missile or bomb. |
| `ACH_NO_DRONES` | (any) | Technophobia | Get to sector 8 without using drones. | Reaching sector 8 without launching a single drone. |
| `ACH_NO_BUYING` | (any) | Living off the Land | Get to sector 8 without buying at a store (Repairs are ok). | Reaching sector 8 without buying a thing at a store. |
| `ACH_NO_DEATH` | (any) | No Redshirts Here | Get to sector 8 without losing a crewmember. | Bringing every one of your crew through to sector 8 alive. |
| `ACH_BURNING` | (any) | Some people just like to watch ships burn | Have every square of an enemy ship on fire simultaneously. | Setting every room of an enemy ship ablaze at once. |
| `ACH_BAD_DODGING` | (any) | Astronomically Low Odds | Fail to evade 5 shots in a row with a fully powered and upgraded engine. | Taking five shots in a row with your engines at full, and flying on anyway. |
| `ACH_ONE_VOLLEY` | (any) | They never saw it coming | Use the Weapon Pre-Igniter augmentation to destroy an enemy ship in one volley before the enemy can get a single shot off. | Destroying an enemy ship in one volley, before she could fire a single shot. |
| `ACH_BOARDING_DRONE` | (any) | BOARDING OBJECTIVE SUCCESSFUL | Have a single boarding drone kill 4 crewmembers on one ship. | A single boarding drone of yours clearing four enemy crew off their own ship. |
| `ACH_INVADE_SHIP` | (any) | Trustworthy Auto-Pilot | Defeat an enemy ship with all of your crew aboard it. | Your whole crew going across and taking an enemy ship with their own hands. |
| `ACH_SLICE_DICE` | (any) | Slice and Dice | Hit every room of a ship with at least one beam in under 5 seconds. | Your beams sweeping every room of an enemy ship in a matter of seconds. |
| `ACH_SUFFOCATE` | (any) | Victory through Asphyxiation | Empty the oxygen (Net level less than 5 percent) of a non-automated, hostile enemy ship. | Draining the air from an enemy ship until there was none left to breathe. |
| `ACH_UNITED_FEDERATION` | Kestrel | The United Federation | Have six unique aliens on the Kestrel Cruiser simultaneously. | Six peoples serving side by side aboard your Kestrel Cruiser. |
| `ACH_FULL_ARSENAL` | Kestrel | Full Arsenal | Have 11 systems installed on the Kestrel Cruiser at one time. | Running eleven systems aboard one Kestrel Cruiser. |
| `ACH_TOUGH_SHIP` | Kestrel | Tough Little Ship | As the Kestrel Cruiser, repair back to full health when it only has 1 HP remaining. | Bringing a Kestrel Cruiser back from a single point of hull to full strength. |
| `ACH_ENERGY_SHIELDS` | Zoltan | Shields Holding | Destroy a ship before it gets through the Zoltan Shield. | Finishing a fight in your Zoltan Cruiser before the enemy ever got through her shield. |
| `ACH_ENERGY_POWER` | Zoltan | Givin' her all she's got, Captain! | With the Zoltan Cruiser, have 29 power in systems at the same time. | Powering a ton of systems on that Zoltan Cruiser, all at once. |
| `ACH_ENERGY_MANPOWER` | Zoltan | Manpower | Get to sector 5 without upgrading your reactor in the Zoltan Cruiser. | Reaching sector 5 in a Zoltan Cruiser on her original reactor. |
| `ACH_STEALTH_DESTROY` | Stealth | Bird of Prey | Destroy a ship at full health during a single cloak in the Stealth Cruiser. | Taking an enemy from full strength to nothing in a single cloak of your Stealth Cruiser. |
| `ACH_STEALTH_AVOID` | Stealth | Phase Shift | With the Stealth Cruiser, avoid 9 points of damage during a single cloak. | Slipping a storm of fire under a single cloak of your Stealth Cruiser. |
| `ACH_STEALTH_TACTICAL` | Stealth | Tactical Approach | With the Stealth Cruiser, get to sector 8 without jumping to a beacon with an environmental danger. | Taking your Stealth Cruiser to sector 8 without once flying into a hazard. |
| `ACH_ROBOTIC` | Engi | Robotic Warfare | With the Engi Cruiser, have 3 drones functioning at the same time. | Keeping three drones at work at once from your Engi Cruiser. |
| `ACH_ONLY_DRONES` | Engi | I hardly lifted a finger | With the Engi Cruiser, destroy an enemy ship using only drones (no weapons). | Your Engi Cruiser's drones winning a fight on their own, without a single weapon fired. |
| `ACH_IONED` | Engi | The guns... They've stopped | Have 4 enemy systems or subsystems ioned at the same time while using the Engi Cruiser. | Ioning four enemy systems at once from your Engi Cruiser. |
| `ACH_ROCK_FIRE` | Rock | Is it warm in here? | Have your crew kill a burning enemy on their ship while using the Rock Cruiser. | Your Rock crew fighting on through the flames aboard an enemy ship, and winning. |
| `ACH_ROCK_MISSILES` | Rock | Defense Drones Don't Do D'anything! | While using the Rock Cruiser, destroy an enemy ship which has a defense drone deployed using only missiles. | Your Rock Cruiser's missiles getting past an enemy's defense drone to finish her. |
| `ACH_ROCK_CRYSTAL` | Rock | Ancestry | Find the secret sector with the Rock Cruiser. | Your Rock Cruiser finding the hidden Crystal worlds, home of the Rock's ancient ancestors. |
| `ACH_MANTIS_CREW_DEAD` | Mantis | Take no prisoners! | Kill the crew of 20 ships by sector 6 in the Mantis Cruiser. | Your Mantis Cruiser's boarders clearing twenty enemy crews before sector 6. |
| `ACH_MANTIS_SLAUGHTER` | Mantis | Avast, ye scurvy dogs! | Kill 5 enemy crew in a fight without taking hull damage or losing a crewmember while using the Mantis Cruiser. | Taking down five enemy crew without a scratch to your Mantis Cruiser or her crew. |
| `ACH_MANTIS_SURVIVOR` | Mantis | Battle Royale | While using the Mantis Cruiser, kill the last enemy with your last crewmember on their ship. | Your last Mantis standing winning the fight aboard the enemy's own ship. |
| `ACH_SLUG_VISION` | Slug | We're in Position! | While using the Slug Cruiser, have vision of every room of the enemy ship without functioning sensors. | Your Slug Cruiser having eyes in every room of an enemy ship, sensors or not. |
| `ACH_SLUG_NEBULA` | Slug | Home Sweet Home | Jump to 30 nebula locations before sector 8. | Your Slug Cruiser visiting thirty nebulas before sector 8. |
| `ACH_SLUG_BIO` | Slug | Disintegration Ray | While using the Slug Cruiser, kill 3 enemy crewmembers with one shot from the Anti-Bio Beam. | Taking down three enemy crew with one shot from your Anti-Bio Beam. |
| `ACH_FED_PATIENCE` | Federation | Master of Patience | Use only the Artillery Beam to destroy an enemy ship while taking no hull damage. | Winning a fight with the Artillery Beam alone, without a scratch to the hull. |
| `ACH_FED_DIPLOMACY` | Federation | Diplomatic Immunity | While using the Federation Cruiser, use your crew in 4 special blue event choices by sector 5. | Your Federation Cruiser's crew talking their way through four tight spots before sector 5. |
| `ACH_FED_UPGRADE` | Federation | Artillery Mastery | Get to sector 5 in the Federation Cruiser without upgrading your Weapons system. | Reaching sector 5 in a Federation Cruiser without upgrading her weapons. |
| `ACH_CRYSTAL_SHARD` | Crystal | Sweet Revenge | Destroy an enemy ship with a shard from the Crystal Vengeance augment (unique to the Crystal Cruiser). | Finishing an enemy ship with a shard of Crystal Vengeance. |
| `ACH_CRYSTAL_LOCKDOWN` | Crystal | No Escape | While using the Crystal Cruiser, trap 4 enemy crew in a single room using the Crystal Being power or a Lockdown Bomb. | Sealing four enemy crew in a single room from your Crystal Cruiser. |
| `ACH_CRYSTAL_CLASH` | Crystal | Clash of the Titans | Destroy 10 Rock Ships (pirates count) using the Crystal Cruiser. | Your Crystal Cruiser's defeat of ten Rock ships. |
| `ACH_LANIUS_ADVANCED` | Lanius | Advanced Mastery | Have Hacking, Mind Control and the Battery active at once. | Running Hacking, Mind Control and the Battery all at once aboard your Lanius Cruiser. |
| `ACH_LANIUS_SCRAP` | Lanius | Scrap Hoarder | Have at least 600 scrap in your ship storage. | Filling one Lanius Cruiser's hold with six hundred scrap. |
| `ACH_LANIUS_OXYGEN` | Lanius | Loss of Cabin Pressure | Get to sector 8 without your ship's net oxygen levels exceeding 20 percent (starts after the first jump). | Taking your Lanius Cruiser to sector 8 on barely a breath of air. |
| `ACH_LANIUS_SUFFOCATION` | Lanius |  |  | (general words) |
