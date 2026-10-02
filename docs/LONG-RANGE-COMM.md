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
- **Hailing frequencies:** opening the screen opens nothing to the network. **Open Hailing Frequencies** starts
  listening, on the first free port of 47610 to 47619 (TCP for the channel, UDP for the scan; two stations can share a
  computer), and searches; the list of stations then searches again every few seconds while the screen is showing,
  and lists only stations with their frequencies open. Hailing by address waits for them to be open too. Leaving the
  screen closes them, unless the commander chose **Stay Powered Up** (asked first, with the port: "available for
  other commanders to find and hail, from any screen"); then **Power Down** closes them, and the Space Dock's Long
  Range button shows a green lamp. Every start of the program begins powered down. The Windows Firewall note comes
  with the first Open Hailing Frequencies.
- **Commanders met before** (`comm/Contacts`, the vault's `comm/contacts.txt`, per career): everyone the station has
  found, hailed, been hailed by or had a message from stays in the list. Those in range come first; the rest are greyed,
  "seen 2 days ago", and Send Message to them goes straight to the Outbox (Hail is off: a channel needs both stations
  there). Right-click: Block / Unblock, and Remove from the list (asked first; they come back when met again, and what
  waits for them in the Outbox stays). At most 100 are kept, the longest unseen dropped first.
- **Hailing** opens a small window, "Hailing Commander Wolfy. Waiting for their answer...", with Cancel. Cancel
  withdraws the hail: a goodbye goes to their station, which sees it while its question is open (the only thing a
  hailing station sends before an answer), closes the question and lists a missed hail "(withdrew the hail)". The
  window closes on an answer, a decline or no answer.
- **Finding and hailing** (`comm/Beacon`, `comm/Channel`): the search broadcasts on each local network and to this
  computer; By address hails an IP (with :port, or each port in turn); the screen shows the port ("your hailing
  frequency") to forward on a router for a hail over the internet (a virtual LAN needs nothing). The hailed commander
  must answer: Answer, Decline (the hailer hears "... is busy. Try again shortly.") or Block. One channel at a time.
- **A hail on another screen** (powered up): the same question pops up wherever the commander is. A window open over
  the station (Settings, a report) is finished first: the hail waits for it to close. Answering from the Cargo Bay
  with unsaved work asks Save first / Discard changes / Cancel before the screen changes; Cancel declines the hail. A
  hail unanswered after 80 seconds (the hailer waits 90) is closed as not answered, listed as a missed hail on the
  Long Range screen, and lights the Space Dock's lamp orange until the screen is opened.
- **Blocking** (`comm/Blocks`): from a commander's right-click menu in the list (Block... / Unblock: kept off the
  everyday buttons, since it's hopefully rare and a bit negative) or the hail's question. A blocked station's hails
  and messages are turned away as not answered (never "blocked"), and its searches get no answer. There are no accounts, so a block
  holds the station's id, the name it went by, and its address when that's from beyond this computer and the home
  network (another station there would be blocked with it). Unblock in Settings, General (Blocked commanders...). A
  search asks with its station's id, and asks the bare question too for stations older than 4B.70; a newer station
  answers the first and ignores the bare one from the same search, so a block can't be slipped that way. Stations must match
  on the Long Range Comm. protocol (`Session.PROTOCOL`), not on the program's version: a Laser Cannon is a Laser
  Cannon in any version, and each line is checked against this station's own game data anyway. A mismatch is refused,
  saying one of them needs to update; the scan shows each station's version for information.
- **Modes and levels:** each station's hello and scan answer give its mode: Sandbox Mode, or an Immersive career's
  level (Easy, Normal, Hard, Custom), shown beside the commander's name. Sandbox trades only with Sandbox, Immersive
  only with Immersive. Two careers of different levels trade when both have **Allow trading with any Immersive level**
  on (Settings, General; on by default): one player on Hard and another on Easy can trade, and a purist can keep a
  career to its own level. These rules stop trading, not talking: any two stations on the same protocol hail and
  message each other (`Session.incompatible` refuses a channel, `Session.cantTrade` only the offer). Such a channel is
  **Communications Only**: the list says "comms only", the notice "Communications Only with Wolfy.", and the offer
  side stays shut on both stations. Every Offer button and Accept say why, in the lore first: "Commander Wolfy is in a
  sector that is too distant for trade. (Sandbox fleets trade only with Sandbox fleets...: they are in Immersive Easy,
  you are in Sandbox Mode.)". Lines that arrive anyway are refused. Stations older than 4B.71 still refuse such a
  hail outright.
- **Messages** (`comm/Notes`, `ui/MessageDialog`): Send Message, under the list beside Hail, writes to a commander
  without a channel (500 letters, line breaks kept). A short link opens for the one message and closes once the other
  station says where it went: their inbox (a transmission from that commander: Reply writes back while their
  frequencies are open, Delete or Archive like a receipt), or, with **Priority** ticked (unticked to begin with), a
  pop-up on whatever screen they're on (it waits for an open window, never changes the screen, and offers Reply). A
  station takes priority pop-ups only when **Priority messages from other commanders pop up** is on (Settings,
  General; on by default), and one a minute from each commander; the rest go to the inbox, marked priority.
  **Long Range mail always reaches the inbox** (commanders' messages, the Quartermaster's receipts): the Immersive
  messages setting is about The Federation Home Planet's own letters, not a commander's mail. The Space Dock shows the
  inbox while Long Range Comm. is in use (hailing frequencies open or powered up, Long Range mail in it, or something in
  the Outbox), whatever that setting says. At most 5 messages a minute from one commander (20 from
  everyone) are taken; the rest are told to try again in a minute. A station's search answer adds "notes" when it
  takes messages, so Send Message stays off for older ones; the message itself is a NOTE in place of the hello, which
  an older station would refuse as garbled.
- **The Outbox** (`comm/Outbox`, the inbox's Outbox tab, `ui/OutboxPanel`): a message for a station that can't be reached (its frequencies
  closed, or a Reply to a commander whose were closed when they wrote) can wait in the Outbox, asked first. It's kept in
  the vault's `comm/outbox/` (one file an item: it survives a restart) and delivered the next time this station, its
  hailing frequencies open, finds theirs: by the search on the Long Range screen, or, powered up on another screen, a
  quiet search every 30 seconds while something waits. Stations are matched by id, not address. A message that waited
  arrives saying when it was written. One their station is too busy for (too many messages this minute) waits for a
  later search; one it turns away for another reason stops trying and says why, until Try again; a station that blocked you never answers the search, so the message just waits. Cancel
  takes an item out. At most 20 wait, 5 for any one commander. It's the inbox's third tab (Inbox / Archive / Outbox),
  and the Long Range screen's "Outbox (n)" opens the inbox on it.
- **Shipments** (`comm/Shipments`, a `parcel-ID.txt` record each in the vault's `comm/`): goods sent with a message,
  no channel needed. **Prepare Shipment** (the middle panel, with no channel open) opens the offer side with nobody on
  the other (a draft `Session`): items, supplies and crew, not whole ships. **Package** takes them off their ships into
  escrow, as a trade does (one packed shipment at a time); **Unpack** brings them back. The middle panel follows the
  shipment: "SHIPMENT PACKED" (what to do next), "SHIPMENT IN THE OUTBOX" once its message waits there (Unpack then
  takes it out of the Outbox, its message cancelled, asked first), and "Sent to ..." once delivered. A message waiting
  with a shipment that was unpacked some other way isn't sent without it: it stops, saying so. Send Message then offers
  **Attach shipment**, for a station whose search answer says it takes them ("shipments", 4B.81 on). The parcel
  travels in the NOTE (its id, its lines, the sender's mode). The other station checks every line against its game
  data (a line it can't take turns the whole parcel away), files it in its inbox as "Shipment from ...", and answers;
  only then does the sender's escrow settle (sent). A parcel that arrives again (an answer lost) is acknowledged, not
  filed twice. In the inbox a held parcel has **Accept** (into this fleet's Cargo Hold), **Deliver to another fleet...**
  (another of the commander's fleets the trading rules allow, its Cargo Hold file written directly, that fleet not in
  use) and **Return to sender** (it waits in the Outbox, addressed back, under a new id, and arrives as a shipment for
  them to accept). The mode rules don't turn a parcel away: one from a mode this fleet doesn't trade with is held,
  saying why (the lore line first), to deliver elsewhere or return. A held parcel can't be deleted. A message with a
  shipment that can't reach its commander waits in the Outbox with it; Cancel there unpacks it. Every move changes the
  parcel's state in the same transaction as the save its goods go into.
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

## Later

- More things to do with a commander in the list, beside Hail and Send Message: **View Offers** (offers left standing
  for anyone who finds you, to trade without both commanders at the screen), **Return Ship** (sending a traded ship
  home to her original owner, or lending ships).
- **Trading with your own careers:** sending a shipment, or trading, between your own fleets (Sandbox and the
  Immersive careers), where the trading rules allow it.

## Changing Long Range Comm.

Each side ignores message fields, offer kinds and package files it doesn't know, so adding one is safe. Bump
`Session.PROTOCOL` only when an older station would trade wrongly (a new step in the exchange, a field it must read).
Protocol 2: custom ships' papers.

## Harness

LinkT runs station A in its own process and station B (LinkPeer) in another, over localhost: goods, supplies and crew
both ways; a change withdrawing acceptance; a refused line; each side crashing at each step of the exchange and the
trade settling on the next link; modes and levels refused or allowed; whole ships there and back with their marks,
commission dates and no packages left behind; custom ships and tampered papers; boarding another ship mid-offer; the receipt and deleting it; a station with its
frequencies closed neither found nor hailed; Sandbox and Immersive, and careers of different levels, talking but not
trading (and a station offering anyway refused); a declined hail told the commander is busy; blocking (search unanswered,
hail not answered, an older station's bare search still answered, which addresses a block keeps) and unblocking; messages (cut, flooded, and kept
from an older station); messages without a channel (inbox, priority pop-up and its once-a-minute rule, pop-ups turned
off, an inbox that's off, blocked, flooded, plain text, an older station's answer, frequencies closed); the Outbox
(waiting while frequencies are closed, kept on disk, delivered once found and saying when it was written, Cancel, a
busy station leaving it to go later, Try again, the limit per commander); shipments (packing and unpacking, sent and held, a duplicate arrival, accepted, held for another mode and delivered
to another fleet's Cargo Hold, returned through the Outbox and accepted home, Cancel in the Outbox unpacking);
commanders remembered out of range, written
to through the Outbox, removed and found again; a hail withdrawn while the other station still asks; garbled
messages and out-of-range crew.
