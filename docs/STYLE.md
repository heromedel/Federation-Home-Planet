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
