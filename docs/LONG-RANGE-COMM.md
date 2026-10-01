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
  the right (what they have to offer, read-only). Before a channel opens the right side finds stations.
- **Finding and hailing** (`comm/Beacon`, `comm/Channel`): a station listens only while the screen is open, on the
  first free port of 47610 to 47619 (TCP for the channel, UDP for the scan), so two stations can share a computer.
  Establish Connection broadcasts on each local network and to this computer; By address hails an IP (with :port, or
  each port in turn). The hailed commander must answer. One channel at a time. Stations must run the same version;
  Immersive fleets trade only with Immersive fleets. A one-time note warns about the Windows Firewall prompt.
- **The offer** (`comm/Session`): each side owns its lines and numbers each version of them. Accept names both
  versions, so any change withdraws every acceptance (the notice line says what changed). A line the other station
  can't take (an item or race its game data lacks) shows grey, and Accept stays off.
- **The exchange** (`comm/Exchange`): the station that hailed leads. Once both accept: the leader takes its goods off
  its ships into escrow (one save, with a trade record in the vault's `comm/` folder) and sends PREPARE; the other does
  the same and answers READY; the leader completes (what arrives goes into the Cargo Hold, which has no slot limits)
  and sends COMMIT; the other completes. A refusal or failure calls it off: goods return to the Cargo Hold, ships to
  the Space Dock. A link lost mid-exchange leaves a record in escrow: the leader calls its own off at once (it never
  told the other to complete); the follower's is settled the next time the two stations connect (it asks the leader),
  or by hand in Other... > Unfinished trades.
- **Distrust:** sizes are checked before anything is read; item ids must be in this station's game data; crew are
  rebuilt field by field within FTL's limits; a ship's package holds only its four files, and her save must read and be
  the ship offered. A malformed message closes the channel.
- **History and letters:** a LONG RANGE TRADE (or TRADE CALLED OFF) entry in history.log; with the inbox on, a receipt
  from the Home Planet Quartermaster.
- **Testing alone:** `java -jar "Federation Home Planet.jar" --station <folder>` runs a second station with its own
  settings in that folder; it asks for a saves folder of its own (a copy of the FTL saves folder).

## Whole ships

- Only between Immersive fleets, and only when both have **Allow trading immersive ships** on (Settings, Rules, off by
  default). The ship must be docked (not boarded), at a station, with no final battle to settle. Everything aboard
  goes with her.
- What travels: her save, her voyage log and its summary, and her last trade mark. Her museum record, kept versions and
  fate stay with the fleet she leaves, where her history is kept as a record (fate TRANSFERRED: she can't be recovered,
  and a victor's exhibit reads "Transferred to another fleet").
- She arrives docked under a new id, set out at The Home Planet Station as a newly commissioned ship is.
- **Not yet:** remodeled ships and ships from Design Ship. Their blueprints are numbered by the station that drew them
  up, so another station may have a different ship under the same id. Taking them across means sending the blueprint
  (and art), re-numbering it on arrival and rewriting her save to match.

## The trade mark (`vault/TradeMark`, history/&lt;id&gt;/traded.txt)

- Written when a ship arrives: the trade, the date, who sent her, her **original owner** (whoever first commissioned
  her, carried through every trade), and her FTL totals at that moment (ships defeated, beacons, scrap, sectors).
- **Displays show her whole life** (Space Dock, Ship Records, the Museum), plus "Original owner" (and "Received from"
  when that's someone else).
- **Anything that rewards or reacts to what a ship has done** (events, rewards, letters, achievements, and anything
  built later) **counts only what she did since her last trade**, and asks `TradeMark` (`defeatedSince`,
  `beaconsSince`, `scrapSince`, `sectorsSince`, `countsFrom`). A never-traded ship counts from her commissioning.
- What already works this way: Museum honours start from her arrival (`achievementsAtStart`), and the fleet counters
  (sectors and beacons seen, the stipend, reply chains) count only changes after she was first seen.

## Harness

LinkT runs station A in its own process and station B (LinkPeer) in another, over localhost: goods, supplies and crew
both ways; a change withdrawing acceptance; a refused line; each side crashing at each step of the exchange and the
trade settling on the next link; whole ships there and back with their marks; garbled messages and out-of-range crew.
