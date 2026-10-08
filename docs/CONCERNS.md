# Concerns

Design debts heromedel and Claude have talked over: not bugs, not features, but ways the station is built that cost
something and may be worth changing later. Each entry says what it is, why it's that way, what it costs, and what a
change would look like. Started at 4B.97 (branch Cloud-C-Primary-Edit); add the version when an entry is added or
settled.

## 1. The Cargo Hold and the ships are still FTL saves (noted 4B.97; closed at 6.00)

_Closed at 6.00: the Cargo Hold is its own xml (5.84); a ship stays her FTL save by design, kept in her folder beside her record (see the 5.94 note below)._

**What it is.** The 4B rewrite moved the bookkeeping to XML (`manifest.xml` as the index of the fleet, `designs.xml`,
`remodels.xml`, `transmissions.xml`), but the things themselves are still FTL save files: a docked ship is
`ships/<id>.sav`, a Junkyard ship `junkyard/<id>.sav`, and the Cargo Hold is `storage.sav`, a save whose ship is named
"Spacedock Storage". Everything a save can't hold lives in a side file next to it: `storage-systems.txt` (stored
systems), `infirmary.txt`, `captives.txt`, `work.txt`, `clock.txt`, `beacons.txt`, `sectors.txt`, `parts.txt`,
`expeditions.txt`, `career.txt`, `reputation.txt`.

_Overhaul 6.0, Phase 2 (5.69 to 5.77): a ship is a folder now (`shipyard/<Name>.<id>/`, `junkyard/`, `memorials_and_records/ships/`), with her record as xml in it in place of the manifest, her save, her log and her versions; the Cargo Hold is `cargohold/` (its save still the pretend ship, its record beside it, until the crew files exist); the station's logs are `logs/`. The rest of this concern (the hold's contents and the crew as xml, the small files one per concern) waits on the crew files (docs/OVERHAUL-6.md, Phase 2 steps 15 to 18)._

**Why.** A ship has to be a save at the moment she's boarded: Board is "copy her file to continue.sav" and FTL reads
it unchanged, with no converting step to get wrong. Keeping the hold as a save too meant one reader for ships and the
hold (the Cargo Bay, the ship report, pricing, the Long Range trade), and everything moving between them is already an
FTL object: a weapon stays a weapon, a crew member keeps every skill and mastery flag.

**What it costs.** The hold carries baggage it doesn't need: a hull, rooms, crew standing on squares (they share
squares when the rooms fill; nobody sees it). Anything the save can't express becomes another side file, each written
on its own; 4B.97 made the expedition ones write together with the hold, but the pattern remains. It confuses: the
station looks like records on the surface and saves underneath. And every feature that touches the hold reads an FTL
`ShipState`, so the save format shapes code that has nothing to do with FTL.

**The direction heromedel had in mind (records).** A permanent record for each thing: every ship (name, class, where
she is: docked, boarded, the Junkyard), the hold, every blueprint, every crew member. A save is then something
*written from* the records when a ship is boarded, and *read back into* them when she docks, rather than being the
record itself. Open choices: one file for all ships or one per ship (a Junkyard manifest if needed); how much of a crew
member's changing data (skills) the record keeps, against the busywork of keeping it current.

**What a change would take.** Large: a second description of weapons, drones, augments, crew and their skills (the
Long Range's `Line.crewFields` is a start: it already writes a crew member as fields and reads one back); a migration
for every existing fleet; every reader of the hold (the Cargo Bay, trading, rewards, expeditions, ransoms,
commissioning, the plea, their tests). Best done in one go, after the current features settle, not half and half. The
player sees no difference on the day it lands.

_Where it stands at 5.94 (the overhaul, nearly done): a ship is still her FTL save, kept in her folder, since Board is a copy FTL reads unchanged; everything else is a record of its own. The Cargo Hold is `cargohold.xml` (5.84), with no hull, rooms or squares: what it holds by name, its crew by name, race, skills and record with FTL's bytes beside them; in memory it is still read as the storage save, so its readers didn't change. Its crew have files in `cargohold/crew/`; the side files are xml in folders of their own (expeditions, infirmary, captives, 5.85) or one per concern at the root (5.86). For heromedel to close at 6.00._

## 2. Crew identity (noted 4B.97; band-aid 4B.98; the crew register, 5.41)

**Where it stands (5.41): solved for the records.** Every crew member has an id of the station's own in the fleet's
`crew.txt` (`vault/CrewRegister`). FTL's saves carry no id, so each time the station takes stock it finds everyone where
they are (the ships, the Junkyard's hulls, the Cargo Hold, away on assignment, held captive) and matches them to the
register on what never changes or only grows: race, sex and colouring must agree, the service record (repairs, kills,
evasions, jumps, masteries) may only have grown; the same name and the same place then decide between the rest. A
rename keeps the id (the same record, and the same colouring or place). Two of a name and race are two ids, each with
their own history, and the Crew Log shows them apart. Someone no longer found anywhere is killed, retired or transferred only on solid
evidence (a ship's fate, the captives file, the station's own log since they were last seen), otherwise missing until
found again. The harness test CrewT holds it: namesakes sent apart, a rename, a capture, a loss in FTL, a retirement, a transfer over the Long Range (5.47).

**What still goes by name, and why it's fine.**

- The crew expeditions (`expedition_type` 2, the default) never did: a detail leaves the Cargo Hold's save whole and
  comes back whole (5.00), and the one picked from a list is matched on the whole record (5.33).
- The infirmary still keeps the laid up by name and race, with the band-aid (`Expeditions.mark`: sex, colouring and
  service record beside the name, used to pick between namesakes; marked BAND-AID in the code). A real clash there needs
  two of the same name, race, sex, colouring and record in the hold at once, which in practice means none. Its way out
  is 6.0's crew files (`docs/OVERHAUL-6.md` §3.4): nothing matched by name at all. The old board of jobs that shared the
  file went at 5.67.
- The one-time reading of the old logs when a fleet's register is new (the past only, by name): an old loss is never
  pinned on someone alive now; it gets an entry of its own.

_5.82, a later improvement, not built: Buggy Boy's test in FTL 1.6.14 (5.80) found that the save's state variables survive everything FTL does (a death and clone, a save at the menu, a rename, a store, a hire, jumps, a dismissal), while no field of the crew record can safely carry an id. So an id-to-position list written into the save on Board (`fhp.crew.<id> = <position>`, with a list version, dropped when a ship arrives by trade) would let the station line crew up by place and match by looks only those who joined or left. heromedel's ship marks are the same idea for ships (`fhp.ship.<career>.<id> = <board count>`: a save from another career, or an old Steam Cloud copy, told apart). heromedel, 5.82: not for 6.0 (the goal is files and logs; this adds risk and testing); 6.0's crew files keep the register's matching._

_Where it stands at 5.94: every crew member the register knows has a file of their own in the folder of whatever holds them (5.83), and a traded ship's crew bring theirs with them (5.90: their past ships and deeds there, as Prior). The register reads the event log (5.91), never a log's prose, and how far it has read is an offset in it. Matching is unchanged: by race, sex, colouring and a record that only grows, as above; namesakes stay namesakes, each with their own id._

## 3. Side files are written one at a time (noted 4B.97; closed at 6.00)

_Closed at 6.00: every action that writes more than one file goes on a protection note (5.71 to 5.88), and a ship's side files are sections of her record (5.98)._

Most of the small files in concern 1 are written on their own, with a failure only logged; a failure between two of
them leaves them disagreeing. `Vault.Transaction` can write side files together with a save (`put(File, byte[])`), and
4B.97 used it for the expedition's end and a ransom's payment. The others (the stipend's `career.txt` and the inbox,
`parts.txt` and the hold, the clock files) still go one by one. Small to fix where it matters; goes away with
concern 1.

_5.71: the journal (docs/OVERHAUL-6.md §3.2): an action that touches more than one file is written as a note first and finished at the next opening if the station stops partway; every Transaction, Board and Dock go through it. The small files that still go one by one join it as their steps are touched._

_Where it stands at 5.94: solved, for heromedel to close at 6.00. Every action that moves or writes more than one file goes on a protection note (5.71 to 5.88): saves, Board, Dock, Decommission, a ship leaving or coming home, a trade received, the crew register's files, the conversions. The clock's five files are one (5.86). What is still written on its own is one file to one owner (a single move or a single write), which can't disagree with itself. Buggy Boy's kill test (6.0 step 25a) is the last check._

## 4. The master log only grows (noted 5.17 by Claude, not yet talked over)

`master.log` (each career's copy of every log entry, and every day counted) is appended to and never trimmed, and the
Captain's Log and the Cargo Bay's day read it whole. A week of heromedel's testing wrote about 300 KB of station log,
so a long career could reach several megabytes: still quick to read, but each Cargo Bay Save reads it to find the last
day line. If it ever matters: keep the last day line's reason in a small file of its own, and let the Captain's Log
read the file a page at a time (or split it by year). Claude's concern, raised while building it; heromedel hasn't weighed in.

_5.74: the Captain's Log and the station log view read the event log now (`logs/events.log`, every entry two lines), not the master log; the master log's E lines are still written, for the crew register, until the crew files (6.0, step 15), and then they can stop. The event log grows the same way; it is read whole at each opening (heromedel's 2026-10-05 fleet, 1,150 entries, opens in about two seconds), and the Cargo Bay's day reads the master log's last D line still._

_Where it stands at 5.94: the master log is no longer written (5.93); the Cargo Bay's day reads the DAY events. The event log grows the same way, append-only, read whole by the Captain's Log and the register; if it ever matters, it can be read from the end or split by year. Still not talked over._

## 5. Docked play needs FTL in a window, and FTL's OpenGL is slow there on some PCs (noted 5.64)

**What it is.** Docked play (Settings, "Option to Play FTL, docked") sets FTL to windowed, since a fullscreen FTL can't
sit in the station's window. On heromedel's PC (Windows, NVIDIA) FTL's loading bar then took 88 seconds instead of 6:
FTL.log's "Resource Preload: 88.474" windowed against 5.956 in native fullscreen. Windowed and borderless fullscreen were
both slow, V-Sync off didn't help, and a vanilla ftl.dat, Steam, the profile and the station's files made no difference.
FTL redraws its loading bar about 250 times while it loads, and each redraw waits until it's shown, so a slow way to the
screen stretches the whole load (tested under Wine: each frame held 100 ms made the load 33 s instead of 8).

**The way out (5.64).** FTL 1.6 has two renderers, picked by its `-directx` and `-opengl` switches. With `-directx`
(Direct3D 11) windowed FTL loaded fast on heromedel's PC and docked as usual. Settings has "Launch FTL with DirectX"
beside the docked option: the station starts FTLGame.exe with `-directx`, or Steam with `steam://run/212680//-directx/`.

**Still open.** Whether Steam asks before passing the switch (heromedel to test); and the main-menu route for players
it doesn't help: FTL reads continue.sav only when Continue is pressed, so a player who goes back to FTL's main menu
(not Save + Quit) can dock and board while it waits there and loads FTL once a session (tested under Wine, 5.63).
FTL rewrites its profile (ae_prof.sav) at the menu and on quitting, so anything that changes the profile still waits
for FTL to close.

## 6. An old Steam Cloud copy can come back as a second ship (noted 5.95 by Buggy Boy; heromedel: written up for now; half fixed 5.97)

**5.97:** the check looks in her `versions/` (`Vault.kept`), so a copy identical to one of her kept versions is set aside,
not adopted (VaultT: a copy of her oldest version put back as `continue.sav`; it fails without the fix). Still open: a copy
identical to nothing kept, and a copy of a ship that has left the fleet. The ship mark in the save (below) is held back for
now (heromedel, 5.97).

**What it is.** When the station opens and finds a `continue.sav` that no boarded ship owns, it asks whether Steam
Cloud brought back a copy of a ship it already has (`Vault.cloudCopyOf`, Vault.java:1142). It compares the file with
each docked or Junkyard ship's current save, and is meant to compare it with her kept versions too, which is what
catches an old copy. Since 5.69 a ship's versions are in `versions/` inside her folder, but the check still lists her
folder itself (`historyOf`), so it finds her current save again and never sees a version. A copy older than her current
save isn't recognised, and it's adopted as a stranger: the same ship twice, and her crew twice in the register.
Confirmed with a scratch test at 5.95: the Test Kestrel docked, a Cargo Bay change to her (5 scrap), the old
`continue.sav` put back; on opening the fleet went from 5 ships to 6, with two Test Kestrels, the copy boarded as a
stranger. Ships that have left the fleet (the Museum's, traded away, lost) aren't checked at all, so an old copy of one
of them would come back the same way (from reading the code, not tested). VaultT's check passes because its copy is
identical to her current save.

**What it costs.** It's rare: Steam Cloud has to restore a `continue.sav` the station already took in. When it does
happen, it quietly doubles a ship and her crew, the kind of damage that's hard to notice and to undo (the player would
have to decommission the double by hand).

**What a change would look like.**

- The check pointed at her versions (`Vault.kept`: her ordinary versions and the copies kept for a reason). This is
  exact and quick, but it only catches a copy that is identical to something the station kept.
- heromedel's check (5.95): whichever has fewer jumps is older. A copy that is hers, with the same or fewer jumps
  than her current save, is an old copy. "The same" matters: a Cargo Bay change adds no jump, so the copy in the test
  above has exactly as many jumps as her current save. This catches copies the station never kept, but jumps only say
  which is older, not whose save it is. Without an id, "is it her?" has to go by name, class and crew (name and race),
  so that a New Game in the same ship, with FTL's default name and 0 jumps, isn't taken for an old copy of one adopted
  earlier.
- With ship ids in the save (heromedel, 5.95: probably coming), the guessing goes. The save's state variables (FTL's
  own list of names with a number each, kept for the run) survive everything FTL does (Buggy Boy's crew id test, 5.80;
  concern 2's note). A mark written on Board, `fhp.ship.<career>.<id> = <board count>`, says whose ship a save is, and
  the board count or her jumps say which copy is older. It covers ships that have left too, since the id names her
  folder in the memorial. The copy is then put aside in her folder (as `cloud-`), never adopted.

Whichever way it's done, a harness check: dock a ship, change her in the Cargo Bay, put her old `continue.sav` back,
open the station. There must be one of her, with the copy put aside. Add a second check for a copy that isn't
identical to anything kept, and a third for one of a ship that has left. The scratch test is CloudCheck on Buggy
Boy's bench.

## 7. Code kept for fleets from before 6.0 (noted 5.97)

**What it is.** Each fleet is brought across to the 6.0 layout the first time it opens, so the station carries the code that
reads every older shape: manifest.xml and history/ (before 5.69), the logs at the root (before 5.71), the Cargo Hold as a save
(before 5.84), the small files as .txt (before 5.86), the old prose logs (before 5.73), crew.txt (before 5.83), a ship's side files
(before 5.98). Since 5.97 it is
all in `homeplanet.convert`: `OldFleet` runs the steps in order, `Layout` and `LogConvert` do the two big ones, and the few
pieces too much a part of their class to move (the crew register's reading of crew.txt) are marked `@Before6`. `OldPackage` is
apart from the rest: a ship traded from a station older than 5.75 (her voyage log as prose); it goes with the protocol, not with
old fleets.

**What it costs.** About 1,000 lines that a 6.0 fleet never runs. Its tests were removed from the harness after 5.98
(heromedel) and the package is frozen: not edited, so nothing can break it; a change would bring its tests back from git history. Other fleets are converted only when they are opened, so a few readers still look at another
fleet in an older shape (its hold, its station log, its ships' blueprints, a ship sent to it); converting every fleet at start-up
would let those go sooner, but each fleet's conversion writes its own log, which today always goes to the fleet in use.

**What a change would look like.** Delete the package; the compiler then points at each call into it and each `@Before6`, every
one a line or a method to delete. Then delete the tests of old fleets. A fleet from before 6.0 would then have to be opened once
by a 6.x station first: the station could say so plainly when it finds `manifest.xml` or `ships/`. When is heromedel's call
(5.97: "at some point in a future build").

