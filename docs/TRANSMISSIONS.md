# Transmissions: draft for review

The transmissions inbox (roadmap item 6). Every message and reward below is a draft for heromedel to approve,
change or deny. Nothing here is built yet.

## How it works

- A transmission icon on the Space Dock with a green light showing the unread count. Clicking it opens the inbox:
  sender, subject and date in a list, the message below, and a **Claim** button on messages with a reward.
- **Immersive Notifications** (its own setting) turns the inbox on. Immersive Mode ticks it and locks it on.
  Without it, the free-ship rules still work silently ("(free)" in Commission).
- **Rewards need Immersive Mode**, and count only for achievements earned after it was turned on.
- Claimed items, supplies, crew and systems go to Spacedock Storage (systems to its stored-systems list). A free ship
  becomes a **commission order**, used up in Commission.
- Checked at startup and on Refresh; each message is sent once.
- `{rank}` is the player's rank (Commander, Captain, Commodore); `{ship}` is the ship's class name.

## Senders

Titles, not names: **Federation Fleet Admiral** (promotions, the big news), **Home Planet Liaison** (most rewards),
**Home Planet Quartermaster** (supplies and scrap), **Federation Engineering Corps** (augments, systems),
**Office of Alien Affairs** (crew volunteers, alien technology), **Home Planet Shipyard** (commission orders).

---

## Standing messages

**Welcome to Immersive Mode** (Home Planet Liaison, when Immersive Mode is turned on)

> Commander,
>
> Welcome to the front. From today The Home Planet Station runs by The Federation Home Planet's rules: trade only
> where there's a station, pay your way at the shipyard, and fund your own journeys.
>
> The fleet is stretched thin against the Rebellion, but it looks after its own. Serve well and you will hear from
> us. Watch this channel.
>
> Home Planet Liaison

**The shipyard is empty** (Home Planet Shipyard)

> {rank},
>
> Our records show you without a command. That will not do. The Federation Home Planet has authorized a new ship in
> your name: a {ship}, at no cost to you. Present this order at Commission.
>
> Home Planet Shipyard

**A ship unlocked in FTL: commission orders** (Home Planet Shipyard / Office of Alien Affairs; one per ship type,
the layout letter filled in)

- **Kestrel:** "The Kestrel's shipyards have turned out another pattern of their workhorse, the Type {layout}.
  One is yours to commission, on the house."
- **Engi:** "The Engi have shared the plans of their {ship}. They say it is 'to better the mutual efficiency of
  allied peoples'. We say thank you, and the first one built from them is yours."
- **Zoltan:** "The Zoltan have offered a {ship} in thanks for the fleet's protection of their trade routes.
  The Federation Home Planet accepted on your behalf."
- **Mantis:** "Intelligence recovered a {ship} from a raiding party that thought better of it. The Engineering
  Corps has made her safe to fly. Mostly. She's yours."
- **Slug:** "A Slug consortium sold us the plans for their {ship}. The price was suspiciously fair. Engineering has
  checked her twice. Commission her free, and check her a third time."
- **Rock:** "A Rock clan that has broken with the pirates sends a {ship} as a gesture of good faith. It is a first.
  Treat her well."
- **Stealth:** "Federation Intelligence has cleared the {ship} for your use. Officially, she does not exist.
  Unofficially, enjoy her."
- **Crystal:** "First contact has been made with the Crystal. They have allowed our engineers to study one of their
  vessels, and the first {ship} built from that study is assigned to you."
- **Lanius:** "A Lanius vessel was captured intact, a first for the fleet. Engineering has learned enough to build
  a {ship}. Keep her away from your spare metal."

## Promotions (Federation Fleet Admiral)

**Commander → Captain** (when the Federation Cruiser A unlocks). Based on heromedel's draft:

> Priority message from the Federation Fleet Admiral:
>
> Commander, your ongoing efforts against the Rebellion have not gone unnoticed. It is my honor to inform you that
> The Federation Home Planet has selected you for commendation and promotion.
>
> From here on you will command your ships as a Federation Captain, with all the dignity and honor that comes with
> it, including a Federation Cruiser, Type A, of your very own.
>
> Keep up the good work and make excellent use of her. This class of ship is a real beauty.
>
> Godspeed,
> Your Fleet Admiral

**Captain → Commodore** (when the Federation Cruiser C unlocks)

> Priority message from the Federation Fleet Admiral:
>
> Captain, the fleet has taken notice again. Few officers hold the line the way you have. The Federation Home Planet
> has approved your promotion to Commodore, effective immediately.
>
> A Commodore needs a ship worthy of the rank. A Federation Cruiser, Type C, is waiting for you at the shipyard.
>
> Godspeed, Commodore.
> Your Fleet Admiral

---

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
| Rule Ten: Greed is Eternal | 10,000 scrap over all games | Scrap Recovery Arm (note 1) |
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

1. **Special artillery:** now earned by rank instead (Commodores may commission designs with artillery; see the
   roadmap, item 7). Open: should Rule Ten also clear it, as an earlier route?
2. **Warlord:** Adv. FTL Navigation already is the "jump back to a visited beacon" augment (it still costs fuel in
   FTL). I added the FTL Recharge Booster to make jumps easier too. Or 20 fuel?
3. **Living off the Land:** FTL doesn't record which ship earned a general achievement, so the player chooses when
   claiming: a Repair Arm, or a Hull Repair drone (for drone ships).
4. **Ancestry:** FTL's crystal weapons are Crystal Burst I/II and Heavy Crystal I/II. I picked Heavy Crystal Mark I.
5. **Loss of Cabin Pressure:** there is no Breach Missile Mark II. There's Breach Missiles, and Breach Bomb Mark I
   and II. I picked the Breach Bomb Mark II.

---

## Achievement messages

Each opens with the player's rank. Signatures as listed.

**Just Getting Started** (Quartermaster)
> Word from the frontier is that you've made sector 5 in one piece. Most don't. A little something for the road:
> scrap and fuel, waiting at The Home Planet Station.

**Federation Base in Range** (Quartermaster)
> You've carried the fight into the last sectors. The struggle against the Rebellion is far from over, and the fleet
> needs you supplied: scrap, missiles, drone parts and fuel, on us.

**Federation Victory (Easy)** (Federation Fleet Admiral)
> A victory for the Federation, and every station from here to the Home Planet is celebrating. The Rebellion will
> remember your name. Your share of the victory purse is waiting at The Home Planet Station.

**Federation Victory (Normal)** (Federation Fleet Admiral)
> That was no training exercise. You faced the Rebellion at its strongest and won. The Federation Home Planet has
> authorized a victory purse worthy of the deed.

**Your Own Fleet** (Federation Fleet Admiral)
> Every ship of the line has sailed under your command. That is a fleet, {rank}, and fleets need funding.
> The Federation Home Planet has allocated a fleet budget in your name.

**Rule Ten: Greed is Eternal** (Home Planet Liaison)
> Ten thousand scrap through your hands. The auditors are impressed, and a little worried. Take this Scrap Recovery
> Arm, and keep counting.

**Warlord** (Federation Fleet Admiral)
> A thousand enemy ships. There are Rebel captains who change course at the sound of your name. For a hunter who
> never stops moving: an Adv. FTL Navigation and an FTL Recharge Booster.

**I don't need no stinkin' upgrades!** (Federation Engineering Corps)
> Sector 5 on factory settings. Engineering wants to know how. Since you clearly don't need upgrades, here's
> something that isn't one: a Cloaking system, yours to install.

**Coming in for my Pacifism run!** (Home Planet Liaison)
> Not a single shot fired, and you still made sector 5. Some admirals call that cowardice. The ones who've fought
> the Rebellion call it survival. An FTL Recharge Booster, for leaving even faster.

**On a Wing and a Prayer** (Federation Engineering Corps)
> You flew to sector 5 without a single repair. Engineering is fascinated, and horrified. Please accept this Repair
> Arm. Please use it.

**Ballistophobia** (Federation Engineering Corps)
> All the way to sector 8 without a missile. A clean-shooting captain needs power for the guns: a Backup Battery,
> to keep your weapons firing when the reactor can't.

**Technophobia** (Federation Engineering Corps)
> No drones for you, and apparently none for anyone else either. This Defense Scrambler will make sure enemy
> defense drones don't bother you.

**Living off the Land** (Home Planet Quartermaster)
> Sector 8 without spending a scrap at a store. The Quartermaster salutes you. For a ship that fixes herself:
> a Repair Arm, which patches the hull every time you collect scrap.

**No Redshirts Here** (Home Planet Liaison)
> Every crew member who set out with you reached sector 8. That is rarer than it should be. The Federation Home
> Planet awards you a Backup DNA Bank, so it stays that way.

**Some people just like to watch ships burn** (Federation Engineering Corps)
> We saw the footage. Every square. Impressive and alarming. Since you know exactly how that's done, here's
> Fire Suppression, so nobody ever does it to you.

**Astronomically Low Odds** (Federation Engineering Corps)
> Five shots. Five hits. Fully powered engines. Engineering has studied the logs and concluded that you simply
> cannot dodge. So stop trying: here's a Shield Charge Booster. Let the shields do the work.

**They never saw it coming** (Home Planet Liaison)
> One volley, one ship, gone before it could fire. Beautiful. For the battles that don't end so quickly: a Chain
> Vulcan.

**BOARDING OBJECTIVE SUCCESSFUL** (Federation Engineering Corps)
> One boarding drone, four enemy crew. The enemy will be trying the same on you. An Anti-Personnel Drone, to
> return the favor.

**Trustworthy Auto-Pilot** (Home Planet Liaison)
> Your entire crew aboard the enemy ship, and your own ship flying herself. Bold. For crews who like to take
> risks: a Clone Bay, so they come back when it goes wrong.

**Slice and Dice** (Federation Engineering Corps)
> Every room in five seconds. Our beam specialists have printed the recording and hung it on the wall. A Weapon
> Pre-igniter, so your beams are ready the moment you arrive.

**Victory through Asphyxiation** (Home Planet Liaison)
> An enemy crew without air. Effective, if grim. Emergency Respirators, so it never happens to yours.

**The United Federation** (Office of Alien Affairs)
> Six peoples aboard one Kestrel, working as one. That is what the Federation is for. A Clone Bay, so none of them
> is ever lost for good.

**Full Arsenal** (Federation Engineering Corps)
> Eleven systems in one Kestrel. We didn't think she had the room. Titanium System Casing, to protect all of them.

**Tough Little Ship** (Federation Engineering Corps)
> One hull point left, and you brought her all the way back. She's a tough little ship, and so are you. Rock
> Plating, so next time she doesn't get that close.

**Shields Holding** (Office of Alien Affairs)
> The enemy never got through the Zoltan Shield. The Zoltan are pleased; you used it as intended. A Shield Charge
> Booster, with their compliments.

**Givin' her all she's got, Captain!** (Federation Engineering Corps)
> Twenty-nine power in systems at once. The reactor must have been singing. A Battery Charger, for when she needs
> even more.

**Manpower** (Office of Alien Affairs)
> Sector 5 without a single reactor upgrade: the Zoltan crew powered the ship themselves. One of them has asked to
> serve with you. They're waiting in Spacedock Storage.

**Bird of Prey** (Federation Intelligence, via Home Planet Liaison)
> A full-health ship destroyed before your cloak dropped. Intelligence would like to know your secret. For now,
> a Weapon Pre-igniter, so the strike comes even sooner.

**Phase Shift** (Federation Intelligence, via Home Planet Liaison)
> Nine points of damage, and none of it landed. An FTL Jammer, so the ones you're hunting can't run either.

**Tactical Approach** (Home Planet Quartermaster)
> Sector 8 without flying into a single storm, sun or asteroid field. Responsible flying saves the fleet a fortune
> in repairs. 500 scrap, from the money you saved us.

**Robotic Warfare** (Office of Alien Affairs)
> Three drones working at once. The Engi approve. A Drone Reactor Booster, with their compliments.

**I hardly lifted a finger** (Office of Alien Affairs)
> An enemy ship destroyed by drones alone. The Engi call it "elegant". A Drone Recovery Arm, so your drones come
> home too.

**The guns... They've stopped** (Office of Alien Affairs)
> Four enemy systems ioned at once. The Engi have shared their Reverse Ion Field so the same never happens to you.

**Is it warm in here?** (Office of Alien Affairs)
> Your Rock crew fought a burning enemy and won. They didn't even notice the fire. The rest of your crew might.
> Fire Suppression.

**Defense Drones Don't Do D'anything!** (Federation Engineering Corps)
> Missiles only, through a defense drone. The drone did not, in fact, do anything. A Defense Scrambler and six
> missiles, to keep proving it.

**Ancestry** (Office of Alien Affairs)
> You found the Crystal's hidden sector. Your Rock crew won't stop talking about it. The Crystal, surprisingly,
> sent a gift: a Heavy Crystal Mark I.

**Take no prisoners!** (Office of Alien Affairs)
> Twenty enemy crews by sector 6. Even the Mantis are impressed, and they're never impressed. Mantis Pheromones,
> to keep your crew moving.

**Avast, ye scurvy dogs!** (Office of Alien Affairs)
> Five enemy crew, no hull damage, no losses. A Mantis warrior heard about it and wants in. They love this sort of
> thing. They're waiting in Spacedock Storage.

**Battle Royale** (Office of Alien Affairs)
> One of yours against one of theirs, and yours walked away. Too close. The Slugs have "generously" offered an
> Anti-Bio Beam, so it's never that close again. We checked it for tricks. Twice.

**We're in Position!** (Office of Alien Affairs)
> Every room of the enemy ship seen without sensors. The Slugs keep their secrets, but they did sell us a
> Lifeform Scanner.

**Home Sweet Home** (Office of Alien Affairs)
> Thirty nebulas before sector 8. The Slugs would feel right at home. Slug Repair Gel, to keep the breaches sealed.

**Disintegration Ray** (Office of Alien Affairs)
> Three enemy crew, one shot. The Slugs are delighted, and sent another Anti-Bio Beam.

**Master of Patience** (Home Planet Liaison; heromedel's draft)
> Word got around about your recent victory using only the Artillery Beam, without taking a scratch. The Admirals
> are impressed. They have negotiated with the Zoltan and acquired a Zoltan Shield for your ship: more time to
> charge the artillery.
>
> Make good use of it.

**Diplomatic Immunity** (Office of Alien Affairs)
> Four delicate situations handled with tact by sector 5. The Diplomatic Corps has assigned one of its officers to
> serve with you. They're waiting in Spacedock Storage.

**Artillery Mastery** (Federation Engineering Corps)
> Sector 5 without upgrading your weapons: you trusted the artillery, and it carried you. A Weapon Pre-igniter, so
> it fires the moment you arrive.

**Sweet Revenge** (Office of Alien Affairs)
> Destroyed by its own damage, turned back on it. Poetic. Titanium System Casing, hardened on the Crystal pattern.

**No Escape** (Office of Alien Affairs)
> Four enemy crew locked in one room. Nowhere to run. The Crystal have sent a Crystal Lockdown Bomb, so you can do it
> again.

**Clash of the Titans** (Home Planet Liaison)
> Ten Rock ships broken up by the Crystal Cruiser. The salvage crews found good Rock Plating in the wreckage. It's
> yours.

**Advanced Mastery** (Federation Engineering Corps)
> Hacking, Mind Control and the Battery, all at once. The Lanius would be proud, if they were ever proud. Hacking
> Stun, to make the combination even nastier.

**Scrap Hoarder** (Home Planet Quartermaster)
> Six hundred scrap aboard, and not a scrap spent. The Quartermaster respects a saver. A Repair Arm and a Scrap
> Recovery Arm, to save more and find more.

**Loss of Cabin Pressure** (Office of Alien Affairs)
> Sector 8 with the air nearly gone. The Lanius manage; the rest of us marvel. A Breach Bomb Mark II, so your
> enemies can try breathing space too.
