# Events: the kinds and their fields

The list of everything the station logs (Overhaul 6.0, Phase 1 step 9; the rule is CLAUDE.md "Log lines"). Every
entry of every log goes to `events.log` in the career folder as two lines: a machine line and a human line written
from it. The words files (step 9a, McCarthy) are keyed by these kinds, and the readers (Phase 3) read these fields;
so a new kind or field is added here first, by whoever writes it. Started at 5.63; the old logs (`history.log`,
`master.log`, `voyage.log`, `reputation.log`) keep their own files and wording beside it until 6.0.

## The lines

    2026-10-07 14:02:11 | 1.2.3.4 | CREW_MOVE | crew=Bob.17 race=human ... to="ship:Shippy McShipface.c77a" day=43 station=6.00
    Bob was transferred to the Shippy McShipface.

- **The machine line:** the real time (`yyyy-MM-dd HH:mm:ss`), the stardate (`year.month.week.day`, or `prior` for an
  entry before the career's day 1), the kind, then the fields, separated by ` | `. A field is `key=value`; a value with
  a space, a quote, a backslash, `|`, `=` or a line break is in double quotes, with `\"`, `\\`, `\n`, `\r` and `\t`
  escaped. A key may repeat, for a list (`item=... item=...`). The order is the writer's, and nothing depends on it.
- **The human line:** one line, simple and lore-friendly, under the three hard rules: never a beacon count, never the
  Rebel Flagship destroyed, every outcome true to `docs/LORE_COMPONENTS.md`. Nothing parses it.
- **Writing:** `Event.of("KIND").put("key", value)...human("...")`, then `EventLog.write(vault, event)`; or through
  `HistoryLog.entry(kind, headline, details, event)` for an entry the station log shows too. `Event.put` leaves a null
  value out, so optional fields need no check.
- **Reading:** `EventLog.read(vault)` gives the entries, oldest first, each with `kind`, `time`, `stardate`, `day`,
  `get(key)`, `all(key)` and `human`. It is the one parser; nothing else reads a machine line.

## Fields every entry has

| Field | Meaning |
| --- | --- |
| `log` | Which of the old logs the entry also went to: `station`, `voyage`, `reputation`, `clock`; none when it went to the event log alone. |
| `day` | The career's day as a number (the stardate is this, shown). Never in a human line. |
| `station` | The station's version that wrote it. |

A ship is named by `ship=<name>.<id>` with `ship_name` and `ship_id` beside it (and `ship_state` where known). A crew
member, when crew files come (Phase 2), by `crew=<name>.<id>`; until then by `crew=<name>` and `race`.

## The clock (`log=clock`)

| Kind | Fields | Human line |
| --- | --- | --- |
| `DAY` | `clock` (the station's count after), `why` (a jump, time passed, business in the Cargo Bay, a day of rest in your quarters, work at a store in FTL) | "A day passed: a jump." |

## A ship's voyage (`log=voyage`; her log in `history/<id>/voyage.log`)

Every voyage entry carries the ship's fields. Those marked **state** also carry her state at the look: `hull`,
`max_hull`, `hull_change`, `scrap`, `scrap_change`, `fuel`, `fuel_change`, `missiles`, `missiles_change`,
`drone_parts`, `drone_parts_change`, `sector`, `beacon` (FTL's beacon id at the look), `beacons_total` (FTL's own
count), `beacons_jumped` (since the last look), `at_store`.

| Kind | Fields | Human line today |
| --- | --- | --- |
| `SECTOR_REACHED` | `sector`, `visited` (sectors visited in all her journeys) | "Sector 3 reached (sectors visited: 7)" |
| `NEW_RUN` | `sector`, `was` | "Back to sector 1: a new run" |
| `JUMPED` | state | "Jumped, hull 28/30 (-2), scrap 61 (+14), fuel 12, missiles 4, drone parts 2" |
| `WAITED` | state | "Waited, hull ..." (FTL's count moved, her beacon didn't) |
| `HULL_REPAIRED`, `HULL_DAMAGED` | state | "Hull repaired to 30/30 (+2)" |
| `SUPPLIES` | state | "Scrap 61 (+14), fuel 12 (-1)" |
| `SHIPS_DEFEATED` | `count`, `total` | "2 ships defeated (41 in all)" |
| `CREW_JOINED`, `CREW_LOST` | `crew` and `race` for each (repeated) | "Crew lost: Stoneface (Rock)" |
| `ITEMS_ABOARD`, `ITEMS_GONE` | `item` (repeated; " (cargo)" marks cargo) | "Aboard now: Burst Laser II" |
| `BOUGHT`, `PICKED_UP` | `item` (repeated) | "Bought at a store: Burst Laser II" |
| `STORE_ARRIVED` | `sector`, `beacon` | "Arrived at a store" |
| `BEACON_HAZARDS` | `hazard` (repeated: `asteroids`, `sun`, `pulsar`, `pds`, `nebula`, `storm`), `sector`, `beacon` | "Beacon: an asteroid field, a nebula" |
| `SHIP_MET` | `met` (in words), `sector`, `beacon` | "Ship met: a Rock pirate" |
| `SYSTEM_NEW`, `SYSTEM_UPGRADED`, `SYSTEM_REDUCED`, `SYSTEM_REMOVED` | `system` (its title), `level`, `was` | "Shields upgraded to 4" |
| `REACTOR_UPGRADED`, `REACTOR_REDUCED` | `level`, `was` | "Reactor upgraded to 12" |
| `FLAGSHIP_ALONGSIDE` | `battle` (1 to 3) | "The Rebel Flagship is alongside (battle 2)" |
| `FLAGSHIP_WITHDREW` | `battle`, `next` | "The Rebel Flagship withdrew after battle 1" |
| `VOYAGE_NOTE` | `text` | A line of the station's own in her log: commissioned, rescued after the final engagement, time spent on work at a store. |

## Reputation (`log=reputation`)

| Kind | Fields | Human line today |
| --- | --- | --- |
| `REPUTATION` | `reason` (`achievement`, `cruiser`, `voyage`, `ship_lost`, `restored`, `expedition`, `captive`, `ransomed`, `spent`, `flagship`, `review`, `other`), `points` (signed), `total` (after), `detail.n` | The reputation log's own line: "Expedition: ... (+3)" |

## The station log (`log=station`; `history.log`)

The station log's kinds, as `HistoryLog.entry` is called today, with a space in a kind written as `_`
(`RENAME CREW` is `RENAME_CREW`). At 5.63 they carry `headline` (the entry's first line) and `detail.n` (its indented
lines), which is the old wording and not yet fields: each call site gets its own fields in the next version, and its
row here fills in then. A kind not in this table is still written (the writer never refuses one), but it is a bug to
leave it undocumented.

| Kind | Where it comes from | Fields at 5.63 |
| --- | --- | --- |
| `BOARD`, `DOCK`, `DISBAND`, `SALVAGE`, `RECOVER`, `RESTORE`, `DESTROY` | Vault: a ship's moves between the Space Dock, FTL, the Junkyard and her history | `headline`, `detail.n` |
| `COMMISSION`, `NEW_JOURNEY`, `RENAME`, `RENAME_CREW`, `REMODEL`, `DESIGN`, `BLUEPRINT`, `BLUEPRINTS`, `CLEAN`, `PATCH` | the Space Dock, the Cargo Bay, the designs, Slipstream | `headline`, `detail.n` |
| `BUY`, `SELL`, `SCRAP`, `TRADE`, `CREW`, `SYSTEMS`, `RETIRE`, `OVERFLOW`, `CLAIM`, `GIFT`, `REWARD`, `STIPEND` | the Cargo Bay, the Junkyard's stores, the inbox's deliveries | `headline`, `detail.n` |
| `EXPEDITION`, `HIRE`, `MEDBAY`, `REST`, `REPAIR_JOB`, `SEIZED`, `RETURNED` | expeditions, the infirmary, Captain's Quarters, the repair job | `headline`, `detail.n` |
| `TRANSMISSION`, `REPLY`, `CAREER`, `CAREER_ENDED`, `PLEAD`, `UNDO_PLEA`, `REASSIGN`, `UNDO_REASSIGN`, `SETTINGS`, `PROFILE`, `UPDATE` | the inbox, the career, the settings | `headline`, `detail.n` |
| `FINAL_BATTLE`, `VICTORY`, `MUSEUM` | the final battle and the museum | `headline`, `detail.n` |
| `LONG_RANGE_TRADE`, `TRADE_CALLED_OFF`, `SENT_AWAY`, `SENT_BACK`, `RECEIVED`, `SENT`, `HANDED_OVER`, `SWITCH_FLEET`, `LONG_RANGE_OUTBOX`, `SHIPMENT_PACKED`, `SHIPMENT_UNPACKED`, `SHIPMENT_SENT`, `SHIPMENT_ARRIVED`, `SHIPMENT_ACCEPTED`, `SHIPMENT_RETURNING`, `SHIPMENT_RETURNED` | Long Range Comm. and the fleets | `headline`, `detail.n` |
| `VAULT`, `OVERWRITTEN` | taking stock | `headline`, `detail.n` |
| `LOADED` | old station logs only: the fleet's listing at a refresh, the debug log's since 5.53 | `headline`, `detail.n` |

## Adding a kind

1. Add its row here, with every field the program could ever need (more than named is better than missing).
2. Write it with `Event.of(...)`; give the human line, under the hard rules.
3. EventT checks every kind a test world writes is in this file, and that no human line counts beacons.
