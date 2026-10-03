# Ideas for later

Ideas heromedel has had but not yet decided to build. When one is chosen, it moves to `ROADMAP.md` and gets a plan.
Lettered as in our plans; heromedel's own words first, notes after.

## Idea A: reply chains in the inbox

Some letters come with a Reply button, which brings up a choice of two or more replies. Sometimes replies go back and
forth. For realism, each reply chain waits a set or semi-random number of beacons before the next letter.

Example, from the Federation Shipyard:

> {rank},
>
> We recently got access to some alien technologies and you were nominated to try one of them out. Hopefully it
> doesn't blow up in your face.
>
> Currently we are looking at testing out a new type of swarm missile, or maybe you would be interested in one of
> those crystal guys' weapons we have been reverse engineering.
>
> Hit us back if you wanna volunteer.

The player hits Reply and chooses:

- the swarm missile: 2-5 beacons, then a reply comes in with a claim;
- the crystal weapon: 5-10 beacons before the reply, perhaps offering two crystal choices. Better weapons take longer
  to get; the letter could say it took a while to finish the reverse engineering.

Notes: the station already counts the beacons she jumps to (the voyage log), so the delays can be measured in beacons.
The rescue letter already carries a choice (keep her, or the museum), which a Reply button would build on.

First candidate: **Ancestry** (the Rock crew finds the Crystal's hidden sector; today it simply sends a Heavy Crystal
Mark I). As a chain, the Office of Alien Affairs writes that the Crystal have offered a gift, and asks how to answer:
accept it graciously (the Heavy Crystal arrives in a few beacons), or ask whether the Federation's engineers may study
their weaponry (5-10 beacons while the Crystal deliberate and Engineering works, then a choice of two crystal weapons).

## Idea B: our own achievements and event triggers

Custom triggers for letters or letter chains (ties into Idea A). Examples:

1. A Federation ship (a Kestrel, say) has accepted 50 surrenders in a row without refusing one: a thank-you for
   holding up Federation standards, as a chain of letters or a single achievement letter.
2. A Mantis ship has killed 25 ships' crews through asphyxiation: a random Mantis writes to accuse you of fighting
   like a Slug, and offers to join your crew and bring a crew teleporter to show you how it's done, but only if you
   pay 100 scrap from the Cargo Hold. Possibly a chain of letters, or just one angry one.

The homework is done: `SAVE_SIGNALS.md` lists what the save records and how sure we are of each.
Ideas for the achievements themselves, with their letters and chains: `achievement-and-events-ideas.md` (Idea E).

Notes: triggers have to come from what FTL's save records. Ships defeated, crew joined and lost, sectors, scrap, what's
aboard and the ship type are all visible; how an enemy crew died, or a surrender accepted, may not be, and would need a
nearby trigger the save does show. Letters from aliens writing directly (the Mantis) would widen the cast beyond the
Federation's own offices.

## Idea C: the One Point of Hull — built (ROADMAP 12)

An Engi writes when a ship comes out of a battle with one point of hull, and offers a derelict to restore or a repair
tool. Built as the first reply chain, so Idea A's mechanism exists now; Ancestry is next in line for it.

## Idea D: ships on loan over Long Range Comm.

Players across Long Range Comm. can loan each other ships. The Return button (built for the repair job's Nightjar)
would prep her for an automatic return on the next connection.

Ships loaned to a player cannot be scrapped, sold or auctioned; they can be lost in game and decommissioned. This
applies to human players, not NPC loans or commissions. Not sure about not being able to destroy them, but to get rid
of a borrowed ship there are three options:

1. Hit Return: she's hidden but saved, ready to be sent automatically on the next reconnect with the right player
   (maybe each player's profile has a unique id).
2. Lose her in game.
3. Decommission her to the Junkyard, then open the junk folder manually and delete her outside of the program.

Notes: the repair job's Return button is set up for this. It asks whether the boarded ship is borrowed and from whom
(a mark in her history folder, beside Long Range Comm.'s TradeMark), not whether she's the Nightjar; a loan would
use the same mark, with Long Range Comm.'s escrow as her way home. "The right player" is already possible: each
station has its own random id (`homeplanet.comm.Commander.stationId()`), sent when two stations connect.

## Idea E: crew skills and specialist expeditions

From heromedel, for later (after testing 4B.83 onwards):

- **Skill levels matter, for the right events.** FTL keeps each crew member's skills (piloting, engines, shields,
  weapons, repair, combat), and an expedition's odds could use them where the event calls for one: a pilot's skill
  in a blind jump, a repair skill on a broken construct, combat in a fight.
- **New kinds of posting, seeking a specialist:** a pilot, an engineer, a repair hand, a tactical officer, a
  hand-to-hand expert, and so on. Skills and race both count: Mantis are trained in melee, Engi in repair. These
  would be new categories of events, still set in sectors where they make lore sense. Example: a pilot needed for the
  Uncharted Nebula, the best result going to a Slug with level 2 piloting.

Notes: the crew's skill levels are in the save (`CrewState`), so the engine could read them the way it reads race;
an event choice might be tagged with the skill it tests, the way `[fight]` is now.

Done in part at 4B.93/94: a choice says what it takes (a race that's good at it: fight, tech, heat, power, airless,
mind; and a skill by name), the crew member best suited takes it on, earns its experience, and makes its bad outcomes
rarer. Specialist postings (a pilot wanted, an engineer wanted) are still to come.

## Idea F: the Refresh button as a small icon (noted 4B.98)

From heromedel: now that the Space Dock keeps itself current (ROADMAP 27, Plan V), the full labelled **Refresh** button
under the Station heading is rarely needed. Make it a little refresh icon beside the word "Station" in the heading
instead, out of the way but there for the odd time (a file changed by hand, a doubt). The heading folds on a click
(ROADMAP 25), so the icon has to be its own target, not part of the fold.

## When we get to them

Idea A first: the mechanism is small (a Reply button, a choice, a countdown in beacons), and then each chain is just
letters, so two or three chains like the Shipyard's can come early. Idea B is a much larger job (a trigger we can
detect, a balanced reward, letters and a test for each), so it starts with homework: a list of everything the station
can see change in FTL's save from beacon to beacon, to pick achievements from. B's chains can then reuse A's replies.
