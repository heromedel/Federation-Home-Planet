# Achievement and event ideas (works in progress)

Achievements FTL doesn't have, and the letters or reply chains they could start. None is decided: when one is
chosen it moves to `ROADMAP.md` and gets a plan. Triggers come from what FTL's save records (`SAVE_SIGNALS.md`);
"test" means one logged run is needed before building on it. FTL's counters are per run; anything about a ship's
whole life counts only what she did since her last trade (`homeplanet.vault.TradeMark`).

Senders are the cast in `VOICES.md`; a new voice is marked as one.

## Idea E: achievements of our own (2026-10-01)

Nine kept from the first ten (one on hold); Old Hand (a crew member offered her own command) was dropped.

### 1. Federation Standards

- **Trigger:** 25 surrenders accepted in a row, none refused (the surrender event's choice). *test* (the surrender
  event ids).
- **From:** the Home Planet Liaison: proud, a little moved.
- **Chain:** a rebel captain you spared later writes to defect. Take him aboard (a skilled human volunteer), or hand
  him to Federation Intelligence (a scrap bounty).

### 2. The Slug's Chart

- **Trigger:** 40 or more nebula jumps in one run (`nebula`). heromedel: 20 isn't much if you hit one Slug sector,
  double that or more. (The logged Lanius run had 20 by sector 8.)
- **From:** the Office of Alien Affairs, relaying an admiring message from a Slug trader (Slugs live in nebulas, and
  in FTL they're tricksters).
- **Chain:** he offers a "complete chart of the nebula lanes" for scrap. Buy it: a Slug volunteer and Slug Gel, or a
  worthless fake and a smug second letter. Refuse: he sulks in writing.

### 3. Sunburned

- **Trigger, as first proposed:** 15 jumps into suns, pulsars and asteroid fields in one run (`env_danger`). That's
  not a lot: the logged run had 18 by sector 8. heromedel's alternative: tie it to deaths by fire, e.g. 3 crew deaths
  from fire in one run.
- **Note:** the save doesn't record how crew died. A fire death could only be inferred: a crew member gone from the
  next save (`lost_crew` up by one) who stood in a burning room at the last save. Fights aren't saved while they
  happen, so many fire deaths would be missed. *test* before deciding.
- **From:** the Federation Engineering Corps, deadpan.
- **Reward:** a Fire Suppression augment.

### 4. The Missile Budget

- **Trigger:** run out of missiles (0 left, with a missile weapon aboard) 5 times in one run. heromedel: firing a lot
  may just mean they can afford it; constantly running out is the better check. Seen as the missile count reaching 0
  between two saves.
- **From:** two voices. The Home Planet Quartermaster writes first, counting every missile ("Give the rebellion
  nothing", even your missiles), then the Federation Engineering Corps quietly answers him.
- **Reward:** an Explosive Replicator (sometimes fires without using a missile).

### 5. The Auditor

- **Trigger:** jump to a new sector carrying 500 scrap or more.
- **From:** a new voice: an auditor of the Federation Audit Authority. A threatening letter: the scrap has been
  noticed, an audit is required.
- **Chain:**
  1. Refuse the audit: fined 25 scrap.
  2. Agree: the auditor replies that the audit is finished and he was wrong, and apologizes. It turns out you were
     due a tax credit of 50 scrap (or the Authority issues 50 scrap as a reward: to choose).

### 6. A Diplomatic Incident

- **Trigger:** 15 Rock ships destroyed in one run (`destroyed_rock`, pirates included).
- **From:** the Office of Alien Affairs, mortified: the Rock homeworlds have complained.
- **Chain:** two replies.
  1. Apologize: reparations (a goodwill Rock volunteer follows).
  2. Insist they were pirates: the next letter shows the record, this many were pirates and this many weren't. Most
     pirates: a reward (the Rocks respect the truth, and strength). Most not: a consequence.
- **Note:** telling pirates apart needs the station to note each Rock ship at arrival (the nearby ship's class and
  name are in the save) and count her when `destroyed_rock` goes up. *test* whether pirate ships are marked.

### 7. Speaks Their Language

- **Trigger:** taking diplomatic race-only (blue) choices, specifically: not every blue option. Needs a chosen list
  of FTL's diplomatic events (talking down a Zoltan patrol, negotiating with the Mantis, and so on), each counted when
  its event id and choice show up in the save. `blue_alien` alone counts every blue choice, so it can't be used.
- **From:** the Office of Alien Affairs: the other peoples of the galaxy have noticed.
- **Reward:** a choice of volunteer from the races you spoke to.

### 8. Patience Flies (on hold)

- heromedel: not right now, and maybe not like this.
- As first proposed: Patience (the Engi Restoration Collective's derelict) flies 25 beacons after her restoration;
  the Collective writes back ("We will ask."), with a Repair Drone, and asks whether you'd take another vessel.

### 9. Old Faithful

- **Trigger:** your first free Kestrel only (the fleet's first free command), at her fourth New Journey.
- **From:** the Home Planet Shipyard Comm. Officer, sentimental about a hull she remembers.
- **Chain:** the Museum offers twice her current value for her. Sell her to the Museum, or keep flying her.
