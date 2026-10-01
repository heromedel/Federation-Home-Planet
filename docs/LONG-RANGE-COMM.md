# Long Range Comm.: decisions

Trading with another commander's Home Planet Station. Decided with heromedel; this file belongs to the
`claude/long-range-comm` branch (the main roadmap is left alone). Player-facing text follows CLAUDE.md's Voice.

## The pieces

- **The commander's name** (`commander_name` in the cfg, Settings > Commander): 1 to 24 letters, numbers, spaces and
  `' - .`. Others see the rank in front: Immersive Mode's rank, or Commander. Asked for the first time Long Range Comm.
  opens. Stations have no names: every one is The Home Planet Station. The letters in the inbox still use titles only.
- **The button:** "Long Range" (the full name doesn't fit FTL's font on the Space Dock's buttons), in the Station group under Cargo Bay. It needs no boarded ship.
- **The screen** (`ui/LongRangeCommUI`): the Cargo Bay's look. Your side on the left (the Cargo Hold, or a ship at a
  station), the offer in the middle (yours, theirs, a notice line, two acceptance lamps, Accept), the other station on
  the right (what they have to offer, read-only). Before a channel opens the right side finds stations. Board / Dock
  beside the chosen ship takes command of her, or docks her, with the Space Dock's own steps (FTL closed), so ships
  change without leaving the screen: off during an exchange, and it withdraws your acceptance. What's already
  offered from either ship stays: the station finds a ship's save wherever she is.
- **Finding and hailing** (`comm/Beacon`, `comm/Channel`): a station listens only while the screen is open, on the
  first free port of 47610 to 47619 (TCP for the channel, UDP for the scan), so two stations can share a computer.
  Establish Connection broadcasts on each local network and to this computer; By address hails an IP (with :port, or
  each port in turn); the screen shows the port this station listens on, to forward on a router for a hail over the
  internet (a virtual LAN needs nothing). The hailed commander must answer. One channel at a time. Stations must match
  on the Long Range Comm. protocol (`Session.PROTOCOL`), not on the program's version: a Laser Cannon is a Laser
  Cannon in any version, and each line is checked against this station's own game data anyway. A mismatch is refused,
  saying one of them needs to update; the scan shows each station's version for information.
- **Modes and levels:** each station's hello and scan answer give its mode: Sandbox Mode, or an Immersive career's
  level (Easy, Normal, Hard, Custom), shown beside the commander's name. Sandbox trades only with Sandbox, Immersive
  only with Immersive. Two careers of different levels trade when both have **Allow trading with any Immersive level**
  on (Settings, General; on by default): one player on Hard and another on Easy can trade, and a purist can keep a
  career to its own level. A refused hail says why.
- **The offer** (`comm/Session`): each side owns its lines and numbers each version of them. Accept names both
  versions, so any change withdraws every acceptance (the notice line says what changed). A line the other station
  can't take (an item or race its game data lacks) shows grey, and Accept stays off. Notices are short enough for
  one line ("Wolfy added 10 scrap: accept again."); the whole of a long one shows on hover.
- **The exchange** (`comm/Exchange`): the station that hailed leads. Once both accept: the leader takes its goods off
  its ships into escrow (one save, with a trade record in the vault's `comm/` folder) and sends PREPARE; the other does
  the same and answers READY; the leader completes (what arrives goes into the Cargo Hold, which has no slot limits)
  and sends COMMIT; the other completes. A refusal or failure calls it off: goods return to the Cargo Hold, ships to
  the Space Dock. A link lost mid-exchange leaves a record in escrow: the leader calls its own off at once (it never
  told the other to complete); the follower's is settled the next time the two stations connect (it asks the leader),
  or by hand in Other... > Unfinished trades.
- **Messages:** a one-line box under the offer (Enter or Send), up to 200 letters a message. Each shows who sent it
  and when; the screen keeps the last 100 and starts empty on each new channel. Nothing is logged or saved. A station
  takes at most 8 messages in 4 seconds (a flood is dropped, not queued). The hello says whether a station shows
  messages (`chat`); an older one doesn't, so the box stays off rather than sending what wouldn't be seen.
- **Distrust:** sizes are checked before anything is read; item ids must be in this station's game data; crew are
  rebuilt field by field within FTL's limits; a ship's package holds only its four files, and her save must read and be
  the ship offered. A malformed message closes the channel.
- **History and letters:** a LONG RANGE TRADE (or TRADE CALLED OFF) entry in history.log; with the inbox on, a receipt
  from the Home Planet Quartermaster, "Receipt of Transfer: Signed by Quartermaster" (a title nobody takes for the other
  commander's own message), in a few wordings. Receipts pile up, so they can be deleted as well as archived: the trade
  stays in history.log and its record.
- **Testing alone:** `java -jar "Federation Home Planet.jar" --station <folder>` runs a second station with its own
  settings in that folder; it asks for a saves folder of its own (a copy of the FTL saves folder).

## Whole ships

- Between two Sandbox fleets, always (there are no commissioning rules to get around). Between Immersive careers, only
  when both have **allow trading whole ships** on (Settings, General, off by default). The ship must be docked, at a
  station, with no final battle to settle: Offer the whole ship docks the ship at your command first, if you say so. Everything aboard
  goes with her.
- What travels: her save, her voyage log and its summary, and her last trade mark. Her museum record, kept versions and
  fate stay with the fleet she leaves, where her history is kept as a record (fate TRANSFERRED: she can't be recovered,
  and a victor's exhibit reads "Transferred to another fleet").
- She arrives docked under a new id, set out at The Home Planet Station as a newly commissioned ship is. Her papers
  carry her original commission date, which goes into her museum record (and her trade mark), through every trade.
- Once a trade settles, the ships' packages in `comm/trade-<id>/` are deleted; the trade record stays as a receipt.
- **Custom ships** (remodels and Design Ship ships) carry their papers (`parser/ShipPapers`): the blueprint as the
  sending station keeps it (a remodel, or the design's built copy for her version) and the pictures it uses. On arrival
  the papers are checked (one blueprint, the one her save names, read by the station's own readers; real pictures, all
  of them there) before anything is installed. A blueprint with the same content as one here is used as it is (a ship
  coming home finds her own); otherwise she gets the next free number (`_R` or `DESIGN_`), her pictures are filed under
  it, and the three names her save gives (her blueprint, twice, and her picture set) are renamed in the save's bytes
  before it's read, since a different ship may have her old number here. A received blueprint isn't commissionable (she
  came as a ship, not plans): a design is filed as retired, a remodel as a non-starter, both kept in the mod while ships
  fly them. The mod is rebuilt and the station offers Patch Now / Later.

## The trade mark (`vault/TradeMark`, history/&lt;id&gt;/traded.txt)

- Written when a ship arrives: the trade, the date, who sent her, her commission date, her **original owner** (whoever first commissioned
  her, carried through every trade), and her FTL totals at that moment (ships defeated, beacons, scrap, sectors).
- **Displays show her whole life** (Space Dock, Ship Records, the Museum), plus "Original owner" (and "Received from"
  when that's someone else).
- **Anything that rewards or reacts to what a ship has done** (events, rewards, letters, achievements, and anything
  built later) **counts only what she did since her last trade**, and asks `TradeMark` (`defeatedSince`,
  `beaconsSince`, `scrapSince`, `sectorsSince`, `countsFrom`). A never-traded ship counts from her commissioning.
- What already works this way: Museum honours start from her arrival (`achievementsAtStart`), and the fleet counters
  (sectors and beacons seen, the stipend, reply chains) count only changes after she was first seen.

## Changing Long Range Comm.

Each side ignores message fields, offer kinds and package files it doesn't know, so adding one is safe. Bump
`Session.PROTOCOL` only when an older station would trade wrongly (a new step in the exchange, a field it must read).
Protocol 2: custom ships' papers.

## Harness

LinkT runs station A in its own process and station B (LinkPeer) in another, over localhost: goods, supplies and crew
both ways; a change withdrawing acceptance; a refused line; each side crashing at each step of the exchange and the
trade settling on the next link; modes and levels refused or allowed; whole ships there and back with their marks,
commission dates and no packages left behind; custom ships and tampered papers; boarding another ship mid-offer; the receipt and deleting it; messages (cut, flooded, and kept
from an older station); garbled messages and out-of-range crew.
