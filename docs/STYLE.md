# Style guide

A guide to consider, not law. More consistency where it helps, never at the cost of looking worse; special
exceptions can be made (the Museum's plates, say, are sized artistically).

## Fonts

| Use | Font |
|---|---|
| Normal text | Sans Serif 12 |
| Labels | Sans Serif 12 Bold (10 only if 12 won't fit) |
| Page titles | Dialog Bold 16, gold |
| Section headings | Sans Serif 14 Bold |
| Log text | Sans Serif 12 (Monospaced 12 only where columns must line up) |
| Tooltips | Dialog 12 |

- Prefer clean sizes: 12 for reading, 14 to 16 for titles.
- Give 12 a few pixels of extra line spacing, so it doesn't feel cramped.
- On the main screens, leave about 4 pixels between a stat's label ("Sector") and its white value.
- FTL's own pixel fonts (`FtlFont.MENU` and `FtlFont.BODY`) stay where they are: headings, buttons, ship names,
  the Cargo Bay, the Dry Dock. That's the game's look.

## Colours

One of each, in `MenuTheme`:

| Colour | Used for |
|---|---|
| Gold `GOLD` (250,210,120) | titles, headings, prices |
| Pure White `WHITE` | values, names |
| Grey-Green `GREY_GREEN` (170,185,180) | labels, notes, times |
| Green `GREEN` (120,215,140) | gains, good news |
| Theme `TEXT` (228,237,232) on `BG` (28,36,44) | dialog text |
| Orange `ORANGE` (255,170,90) | warnings |
| Red `RED` (235,110,95), an exception | damage and losses only |

`MenuTheme.DIM` (140,152,150) stays for disabled text, which should look dimmer than a note.

## Layout of text

Messages, letters, events and other text based screens should have sentences, lines, spaces and content organized in
an aesthetic way, not just lumped together or running on continuously. (heromedel, 5.00.) A long line is broken where
the sense breaks; a thing that stands apart (a prize, a question, a cost) gets a line or a blank line of its own.

## The Federation's voice

The rebellion and the rebels are never capitalised (the Federation won't dignify them with a title); only the Rebel
Flagship keeps its capitals. Messages may use `{rank}` where a phrase names the player ("Hold the line, {rank}.").

Catch phrases. The most common of all: **"The Federation endures."**

| Formal (Fleet Admiral, Liaison) | Warm (sign-offs) | Fighting words (Quartermaster, battle orders) |
|---|---|---|
| Hold the line, Captain. | Godspeed, and come home. | Give the rebellion nothing. |
| For the Federation, and all its worlds. | Fair flights between the stars. | Not one more sector. |
| Many worlds, one Federation. | The Home Planet stands with you. | Stand fast. The Federation endures. |
| Until every beacon is free. | Safe jumps, Captain. | |

## The Captain's Log

The Captain's Log (Captain's Quarters) is a story told from the master log, not a list of it (heromedel, 5.18;
`vault/CaptainsLog`). Its rules:

- **Voice:** the captain's own, first person and past tense, short: "Rested in my quarters for the third day in a
  row." "Sold five missiles." Things that simply happened go in too, from the captain's side: "Bob and Joe came back
  from the expedition; Fred did not." "Got a letter from the Home Planet Shipyard: I can now commission a new
  Kestrel Cruiser, Type B."
- **Headings:** the first day is "Stardate Today", every later one "Stardate 1.1.1.2" (year.month.week.day: 7-day
  weeks, 28-day months, 13-month years, year 1 first). Quiet days fold together: "Stardates 1.1.1.4 – 1.1.1.5",
  "Nothing to report."
- **"Then":** on a day of more than one line, the action that moved the day on comes last and begins "Then" ("Then I
  rested in my quarters."). Several things done in one visit to the Cargo Bay are one sentence ("Then I had a Cloaking
  system fitted to the Hinata and sold five missiles."). A jump in FTL is the crew's: "Then we jumped to a new beacon",
  "…to a station", "…to sector 3".
- **Where the captain is:** a day aboard opens "On board the Kestrel:"; time at the station after time aboard opens "I
  returned to The Home Planet Station."; time aboard after the station opens "Set out on the Kestrel:". Gear that comes
  aboard is "Bought a Burst Laser II at a station." (at a store, scrap spent) or "We picked up a Burst Laser II.".
- **The beacon (5.19):** a jump tells what was there and who she met: "Then we jumped into a nebula", "…into an ion
  storm", "…into an asteroid field", "…to a beacon near a star (a pulsar)", "…within range of an Anti-Ship
  Battery", "…and met a Rock pirate" (a Mantis ship, a rebel ship, an automated ship). What's learned of a beacon later,
  even on a later day, goes into that jump's line, never a line of its own.
- **Merging:** the same action on the same day is one line ("Sold five missiles", not five sales); boarding several
  times is one "Took command of the …"; an event and its letter or receipt are one line.
- **Numbers:** up to ten in words, then digits. No prices, scrap or reputation in the story: they're the details,
  shown only with "Detailed Log Entries" ticked.
- **Never told:** how a day is counted (no jumps as time, no beacons), reputation as its own lines (it has its log and
  tally), the station's housekeeping (loads, profiles, settings, patches, fleet switches, Medbay visits, moving one's own
  things about), and letters that only repeat an event already told.

## Expedition result lines

An expedition's crew are given roles by skill (`<role>` in `lore/expeditions.xml`, 6.39), but a skill is a proxy for
what the crew member did, not the station they sat at (heromedel, 6.39). The player never sees the skill's name in a
result line. Each skill stands for:

- **Piloting:** leading, navigating, making the calls. "Ann led the negotiations", "Ann found the way through".
- **Shields:** defending and protecting others. "Ann kept the spiders at bay", "Ann held the perimeter".
- **Weapons:** firepower, marksmanship, covering fire. "Bob picked off the raiders from cover".
- **Engines:** keeping things running and moving: power, logistics, speed. "Kenji kept the convoy moving", "Kenji got
  the power back on".
- **Repair:** hands-on technical work: fixing, rigging, patching, cutting through. "Kenji cut through the bulkhead",
  "Kenji rigged the doors shut".
- **Combat:** hand-to-hand fighting, toe to toe. "Ashlee met the boarders at the airlock".

The result lines are written per job and per skill, and most never name a station: "Ann led the negotiations", never
"Ann, on piloting, negotiated well". A line may name the place when it adds something ("Ann, on the perimeter…"), and
one day some may be written for a sector as well. Every new batch of lines goes to heromedel numbered (1., 2., 3.) to
check for sense and tone before it goes in.

**How the report shows them (heromedel, 6.40):** the result is marked, not said: ✗ for a failure, ★, ★★ or ★★★ for a
success, a very good and an outstanding one, in gold before the line ("was successful", "was very successful" are no
longer written). The crew member's name is bold wherever it falls (`{name}` in the line), so a line is a whole sentence
told whichever way reads best. The killed, the hurt and the taken get no mark: their lines already say it.

**Writing them (heromedel):**

- **Whoever acts is the subject.** When the crew member did it, they lead: "Kenji cut through the wreckage blocking the
  way to the survivors." When something happened first and the crew member answered, that comes first: "Two of the
  survivors wandered off. Ann couldn't get everyone back together." Never move the name just to move it, and never
  tack it on at the end ("A knife was hidden near the proceedings, found by Kenji" makes the knife the subject).
- **Each sentence is its own thing.** Write what that line is saying, not the shape of the line before it. Read a batch
  back for accidental patterns: every line opening with the name, every line joined with ", and", every line two short
  sentences, the same word ("kept", "twice", "halfway", "made sure") over and over.
- **Don't lean on ", and".** Often the second half is a "but", a "because", or a sentence of its own.
- **Every line fits every event of its job** (a convoy, a station, a mine, two captains who won't meet), and nothing in
  it says more of the others than it must ("nobody was hurt" can sit beside someone who was).
- **FTL's rules hold through the proxies:** missiles go through shields (a shield line says lasers, or nothing that
  claims every shot), ships don't chase each other down in a fight (an enemy that runs jumps away), and Attack is the
  crew's ship against another ship; Board is the boarding job.

## Records agree with each other

The station keeps the same events in several records: each ship's voyage log, the station log, the master log, the
crew register (crew.txt), the Captain's Log, the Crew Log, expedition reports and letters. A player who knows Gracie
started on the Unyielding notices a Crew Log that forgets it as surely as a Slug weathering a storm (heromedel, 5.50).

- **Any log, report or letter built from records** is checked this way as well as for voice and lore
  (`docs/LORE_COMPONENTS.md`): take a few real crew and ships and follow each through every record that mentions them.
  Every ship served on, every rename, death, capture, trade and return should be there, in the same order, under one
  name for one ship.
- **What doesn't match** goes to heromedel as a finding, or as a handoff (`docs/HANDOFF.md`): what is missing or
  contradicted, where, and the lines from both records that show it. Never a silent fix.
