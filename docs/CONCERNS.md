# Concerns

Design debts heromedel and Claude have talked over: not bugs, not features, but ways the station is built that cost
something and may be worth changing later. Each entry says what it is, why it's that way, what it costs, and what a
change would look like. Started at 4B.97 (branch Cloud-C-Primary-Edit); add the version when an entry is added or
settled.

## 1. The Cargo Hold and the ships are still FTL saves (noted 4B.97)

**What it is.** The 4B rewrite moved the bookkeeping to XML (`manifest.xml` as the index of the fleet, `designs.xml`,
`remodels.xml`, `transmissions.xml`), but the things themselves are still FTL save files: a docked ship is
`ships/<id>.sav`, a Junkyard ship `junkyard/<id>.sav`, and the Cargo Hold is `storage.sav`, a save whose ship is named
"Spacedock Storage". Everything a save can't hold lives in a side file next to it: `storage-systems.txt` (stored
systems), `infirmary.txt`, `captives.txt`, `work.txt`, `clock.txt`, `beacons.txt`, `sectors.txt`, `parts.txt`,
`expeditions.txt`, `career.txt`, `reputation.txt`.

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
- The old board of jobs (`expedition_type` 1) still keeps the infirmary and an expedition's party by name and race, with
  the band-aid (`Expeditions.mark`: sex, colouring and service record beside the name, used to pick between namesakes;
  marked BAND-AID in the code). A real clash there needs two of the same name, race, sex, colouring and record in the
  hold at once, which in practice means none. Its way out, when that board is next worked on: the laid-up kept out of
  the hold's save in the infirmary file, as the captives are, so nothing there is matched at all.
- The one-time reading of the old logs when a fleet's register is new (the past only, by name): an old loss is never
  pinned on someone alive now; it gets an entry of its own.

## 3. Side files are written one at a time (noted 4B.97)

Most of the small files in concern 1 are written on their own, with a failure only logged; a failure between two of
them leaves them disagreeing. `Vault.Transaction` can write side files together with a save (`put(File, byte[])`), and
4B.97 used it for the expedition's end and a ransom's payment. The others (the stipend's `career.txt` and the inbox,
`parts.txt` and the hold, the clock files) still go one by one. Small to fix where it matters; goes away with
concern 1.

## 4. The master log only grows (noted 5.17 by Claude, not yet talked over)

`master.log` (each career's copy of every log entry, and every day counted) is appended to and never trimmed, and the
Captain's Log and the Cargo Bay's day read it whole. A week of heromedel's testing wrote about 300 KB of station log,
so a long career could reach several megabytes: still quick to read, but each Cargo Bay Save reads it to find the last
day line. If it ever matters: keep the last day line's reason in a small file of its own, and let the Captain's Log
read the file a page at a time (or split it by year). Claude's concern, raised while building it; heromedel hasn't weighed in.
