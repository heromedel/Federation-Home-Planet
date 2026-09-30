# Transmissions

The transmissions inbox: the letters The Federation Home Planet's people send in Immersive Mode, and their rewards.
The letters themselves live in `src/main/resources/homeplanet/resource/transmissions.txt`, the one place their text is
kept; this page covers how they work, who sends them, and the rewards.

## How it works

- A transmission icon on the Space Dock with a green light showing the unread count. Clicking it opens the inbox:
  sender, subject and date in a list, the message below, and a **Claim** button on messages with a reward.
- **Immersive Notifications** (its own setting) turns the inbox on. Immersive Mode ticks it and locks it on.
  Without it, the free-ship rules still work silently ("(free)" in Commission).
- **Rewards need Immersive Mode**, and count only for achievements earned after it was turned on.
- Claimed items, supplies, crew and systems go to the Cargo Hold (systems to its stored-systems list). A free ship
  becomes a **commission order**, used up in Commission.
- Checked at startup and on Refresh; each message is sent once.
- `{rank}` is the player's rank (Commander, Captain, Commodore); `{ship}` is the ship's class name.

## Senders

Titles, not names. Each has a voice of their own, described in `VOICES.md`: the **Home Planet Liaison** (the welcome,
advice, and rewards for how the player fights), the **Home Planet Shipyard Comm. Officer** (the free command,
commission orders, and ship achievements), the **Office of Alien Affairs** (alien ships, crew volunteers, alien
technology), the **Federation Engineering Corps** (systems and technical feats), the **Home Planet Quartermaster**
(supplies, scrap and salvage), the **Federation Fleet Admiral** (promotions and the great victories), **Federation
Fleet Command** (the rescue after the final engagement) and **The Federation Home Planet** itself (the stipend and
formal awards).

heromedel's own letters are kept exactly as written: the welcome, "A new command", "Back from nothing", the first
promotion, the stipend, "Master of Patience" and the Zoltan commission order.

**Lore rule:** never imply the Rebel Flagship has been destroyed. The war goes on; its weapons come from stolen plans.

## Which letter, when

- **welcome**: Immersive Mode turned on (sent last, so it's on top of the inbox).
- **empty**: the free command, when the fleet or an Immersive career starts. **reassigned**: the free command after a
  Report for Reassignment; in Immersive Mode **reassigned:any / :kestrel / :relief**, by everything of value
  surrendered (1000 scrap or more, any ship; 500 or more, a Kestrel Type A; less, a relief ship).
- **order:<ship>**: a ship unlocked in FTL (with free unlock ships on), one per ship type.
- **promo:1 / promo:2**: promoted to Captain (the Federation Cruiser A unlocks) and to Commodore (the C).
- **stipend**: every 4 sectors travelled.
- **ach:<achievement>**: an FTL achievement earned after Immersive Mode began.
- **rescue / reward**: after a final victory, when the fleet's choice is a rescue or a reward (shown as a notice on
  the Space Dock instead when Transmissions are off).

## Achievement rewards

Approved by heromedel, with the exact FTL item names checked in ftl.dat. **Notes** mark changes or questions.

### General

| Achievement | What FTL asks | Reward |
|---|---|---|
| Just Getting Started | Reach sector 5 | 25 scrap, 4 fuel |
| Federation Base in Range | Reach sector 8 | 60 scrap, 4 missiles, 4 drone parts, 4 fuel |
| Federation Victory (Easy) | Beat the boss on Easy | 300 scrap |
| Federation Victory (Normal) | Beat the boss on Normal | 400 scrap |
| Your Own Fleet | Every Type A unlocked | 500 scrap |
| Rule Ten: Greed is Eternal | 10,000 scrap over all games | Scrap Recovery Arm, and clearance for the Rebel Flagship's weapons as artillery (note 1) |
| Warlord | Defeat 1000 ships | Adv. FTL Navigation + FTL Recharge Booster (note 2) |
| I don't need no stinkin' upgrades! | Sector 5, no upgrades | A Cloaking system |
| Coming in for my Pacifism run! | Sector 5 without firing | FTL Recharge Booster |
| On a Wing and a Prayer | Sector 5, no store repairs | Repair Arm |
| Ballistophobia | Sector 8, no missiles | A Backup Battery system |
| Technophobia | Sector 8, no drones | Defense Scrambler |
| Living off the Land | Sector 8, no buying | Repair Arm or Hull Repair drone, the player's choice (note 3) |
| No Redshirts Here | Sector 8, no crew lost | Backup DNA Bank |
| Some people just like to watch ships burn | Every enemy square on fire | Fire Suppression |
| Astronomically Low Odds | Fail 5 dodges, engines maxed | Shield Charge Booster |
| They never saw it coming | One-volley kill with the Pre-Igniter | Chain Vulcan |
| BOARDING OBJECTIVE SUCCESSFUL | Boarding drone kills 4 crew | Anti-Personnel Drone (FTL's anti-boarder drone) |
| Trustworthy Auto-Pilot | Win with all crew aboard the enemy | A Clone Bay system |
| Slice and Dice | Beam every room in 5 seconds | Weapon Pre-igniter |
| Victory through Asphyxiation | Empty an enemy's oxygen | Emergency Respirators |

### Ships

| Ship | Achievement | What FTL asks | Reward |
|---|---|---|---|
| Kestrel | The United Federation | 6 alien races aboard | A Clone Bay system |
| Kestrel | Full Arsenal | 11 systems at once | Titanium System Casing |
| Kestrel | Tough Little Ship | Repair from 1 HP to full | Rock Plating |
| Zoltan | Shields Holding | Kill before the Zoltan Shield falls | Shield Charge Booster |
| Zoltan | Givin' her all she's got, Captain! | 29 power in systems | Battery Charger |
| Zoltan | Manpower | Sector 5, no reactor upgrades | A Zoltan crew volunteer |
| Stealth | Bird of Prey | Kill during one cloak | Weapon Pre-igniter |
| Stealth | Phase Shift | Avoid 9 damage in one cloak | FTL Jammer |
| Stealth | Tactical Approach | Sector 8 avoiding hazards | 500 scrap |
| Engi | Robotic Warfare | 3 drones at once | Drone Reactor Booster |
| Engi | I hardly lifted a finger | Win with drones only | Drone Recovery Arm |
| Engi | The guns... They've stopped | 4 enemy systems ioned | Reverse Ion Field |
| Rock | Is it warm in here? | Kill a burning enemy aboard | Fire Suppression |
| Rock | Defense Drones Don't Do D'anything! | Missiles past a defense drone | Defense Scrambler + 6 missiles |
| Rock | Ancestry | Find the secret sector | Heavy Crystal Mark I (note 4) |
| Mantis | Take no prisoners! | Crew of 20 ships killed by sector 6 | Mantis Pheromones |
| Mantis | Avast, ye scurvy dogs! | 5 kills, no damage or losses | A Mantis crew volunteer |
| Mantis | Battle Royale | Last crew kills last enemy | Anti-Bio Beam, offered by the Slugs |
| Slug | We're in Position! | Vision without sensors | Lifeform Scanner |
| Slug | Home Sweet Home | 30 nebulas before sector 8 | Slug Repair Gel |
| Slug | Disintegration Ray | 3 kills, one Anti-Bio shot | Anti-Bio Beam |
| Federation | Master of Patience | Artillery-only kill, no hull damage | Zoltan Shield |
| Federation | Diplomatic Immunity | 4 blue options by sector 5 | A human crew volunteer |
| Federation | Artillery Mastery | Sector 5, no weapon upgrades | Weapon Pre-igniter |
| Crystal | Sweet Revenge | Kill with a Vengeance shard | Titanium System Casing |
| Crystal | No Escape | Trap 4 crew in one room | Crystal Lockdown Bomb |
| Crystal | Clash of the Titans | Destroy 10 Rock ships | Rock Plating |
| Lanius | Advanced Mastery | Hacking, Mind Control, Battery at once | Hacking Stun |
| Lanius | Scrap Hoarder | 600 scrap aboard | Repair Arm + Scrap Recovery Arm |
| Lanius | Loss of Cabin Pressure | Sector 8, oxygen at or under 20% | Breach Bomb Mark II (note 5) |

### Notes

1. **Artillery:** the Federation's artillery (Artillery Beam, Flak Artillery) is cleared for Commodores; the Rebel
   Flagship's weapons (Boss Laser, Missile, Beam, Ion) by Rule Ten. Artillery guns are a luxury: the Artillery Beam 200
   scrap, Flak Artillery 150, each Flagship weapon 100.
2. **Warlord:** Adv. FTL Navigation already is the "jump back to a visited beacon" augment (it still costs fuel in
   FTL). The FTL Recharge Booster comes with it to make jumps easier too (heromedel: keep both).
3. **Living off the Land:** FTL doesn't record which ship earned a general achievement, so the player chooses when
   claiming: a Repair Arm, or a Hull Repair drone (for drone ships).
4. **Ancestry:** FTL's crystal weapons are Crystal Burst I/II and Heavy Crystal I/II. I picked Heavy Crystal Mark I.
5. **Loss of Cabin Pressure:** there is no Breach Missile Mark II. There's Breach Missiles, and Breach Bomb Mark I
   and II. I picked the Breach Bomb Mark II.
