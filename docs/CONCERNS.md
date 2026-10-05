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

## 2. Crew are identified by name and race in the station's records (noted 4B.97; band-aid 4B.98)

**What it is.** The records that remember a crew member between looks key them by name and race: the infirmary
(`infirmary.txt`), the captives (`captives.txt`), an expedition party when the job ends (matched back into the hold),
and the medbay's "first seen hurt" notes. The Cargo Bay numbers duplicate names on its own screen, for display only.

**Why.** FTL's save format has a fixed set of fields per crew member and no spare one for an id, and FTL rewrites the
whole save every time a ship flies, so any id the station invented would be gone the first time they left the hold.
Names were the fallback.

**How duplicates happen.** Hiring and expedition recruits (a volunteer's name is only checked against itself), Rename
in the Cargo Bay, a trade over the Long Range, a commissioned ship whose crew share a name with the hold's, FTL's own
random names.

**What it costs.** With two of a name and race, the wrong one can count as laid up, be hurt or removed at an
expedition's end, or have a hurt applied twice.

**The second system does it right (5.00).** The crew expeditions of `expedition_type` 2 (`Assignments`) take a detail
out of the Cargo Hold's save when it sets out and keep it in `assignments.txt` until it's back, so nothing is matched
by name there; the old board still matches, with the band-aid below.

**The band-aid (4B.98).** `Expeditions.mark`: sex, colouring and the service record (repairs, kills, evasions, jumps),
none of which change while a crew member sits in the hold, kept beside the name in the infirmary's records and used to
pick between namesakes when an expedition's party is matched back into the hold. A record without a mark (from before)
matches any namesake, as before. Marked BAND-AID in the code; it goes when either way out below is built.

**Two ways out.**

- *The smaller one:* crew who are away aren't in the hold's save at all. A laid-up crew member is taken out of the hold
  and kept, whole record and all, in the infirmary file (as captives are kept since 4B.97), and put back when their
  time is up; an expedition party leaves the hold at sign-on and comes back at the end. Then nothing is matched by
  name: the hold holds who's there, the infirmary who's laid up, the captives who's taken, and the party is the run's
  own list. The Cargo Bay still shows the laid-up, read from the infirmary, greyed as now. The medbay note stays by
  name (a clash there only heals someone a beacon early or late). Medium: the expedition code, the Cargo Bay's crew
  list, the tests. Also closes the last gap of 4B.97's sign-on fix (a closed station can't bring a party member back).
- *The records one (part of concern 1):* a crew manifest with a station id and a location for each crew member ("Bob
  the human, id 4, in the Cargo Hold"; then "boarded: continue.sav, slot 3"). When a ship docks, the station reconciles
  her crew against the manifest (who came back, who didn't), so identity holds across a flight even though the save
  carries no id.

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
