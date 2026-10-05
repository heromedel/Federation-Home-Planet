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
  from their expedition; Fred did not." "Got a letter from the Home Planet Shipyard: I can now commission a new
  Kestrel Cruiser, Type B."
- **Headings:** the first day is "Stardate Today", every later one "Stardate 1.1.1.2" (year.month.week.day: 7-day
  weeks, 28-day months, 13-month years, year 1 first). Quiet days fold together: "Stardates 1.1.1.4 – 1.1.1.5",
  "Nothing to report."
- **"Then":** on a day of more than one line, the action that moved the day on comes last and begins "Then" ("Then I
  rested in my quarters."). Several things done in one visit to the Cargo Bay are one sentence ("Then I had a Cloaking
  system fitted to the Hinata and sold five missiles."). A jump in FTL is the crew's: "Then we jumped to a new beacon",
  "…to a station", "…to sector 3".
- **Where the captain is:** a day aboard opens "On board the Kestrel."; time at the station after time aboard opens "I
  returned to The Home Planet Station."; time aboard after the station opens "Set out on the Kestrel.". Gear that comes
  aboard is "Bought a Burst Laser II at a station." (at a store, scrap spent) or "We picked up a Burst Laser II.".
- **Merging:** the same action on the same day is one line ("Sold five missiles", not five sales); boarding several
  times is one "Took command of the …"; an event and its letter or receipt are one line.
- **Numbers:** up to ten in words, then digits. No prices, scrap or reputation in the story: they're the details,
  shown only with "Detailed Log Entries" ticked.
- **Never told:** how a day is counted (no jumps as time, no beacons), reputation as its own lines (it has its log and
  tally), the station's housekeeping (loads, profiles, settings, patches, fleet switches, Medbay visits, moving one's own
  things about), and letters that only repeat an event already told.
