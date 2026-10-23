# Events: the kinds and their fields

The list of everything the station logs (Overhaul 6.0, Phase 1 step 9; the rule is CLAUDE.md "Log lines"). Every
entry of every log goes to `events.log` in the career folder as two lines: a machine line and a human line written
from it. The words files (step 9a, McCarthy) are keyed by these kinds, and the readers (Phase 3) read these fields;
so a new kind or field is added here first, by whoever writes it. Started at 5.63; the only log since 6.0 (7.0 reads
nothing older).

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
  `StationLog.entry(kind, headline, details, event)` for an entry the station log shows too. `Event.put` leaves a null
  value out, so optional fields need no check.
- **Reading:** `EventLog.read(vault)` gives the entries, oldest first, each with `kind`, `time`, `stardate`, `day`,
  `get(key)`, `all(key)` and `human`. It is the one parser; nothing else reads a machine line.

## Fields every entry has

| Field | Meaning |
| --- | --- |
| `log` | Which of the old logs the entry also went to: `station`, `voyage`, `reputation`, `clock`; none when it went to the event log alone. |
| `day` | The career's day as a number (the stardate is this, shown). Never in a human line. |
| `station` | The station's version that wrote it. |
| (her own log) | Every entry that names a ship by `ship_id` is written into her folder's log as well as the fleet's (5.76): the same two lines. Her voyage views, the museum and her package read her log. |
| `received_from` | On a voyage entry that came with a ship over Long Range Comm. (5.75): the commander she came from. Her entries travel in her package under her new id; their time is their own, their day this career's. |
| `time`, `day` (given by the writer) | An entry about something that happened earlier (a journal note finished at start-up, 5.71): the writer gives the time (`yyyy-MM-dd HH:mm:ss`) and the day it happened, and the entry's own columns follow them instead of the clock. |

A ship is named by `ship=<name>.<id>` with `ship_name` and `ship_id` beside it (and `ship_state` where known). Since 6.18
(heromedel) every entry about a ship also says where she was when it happened: `ship_sector` (1 to 8, as the player counts)
and `ship_difficulty` (`easy`, `normal`, `hard`, FTL's difficulty she flies on), from her save at that moment; left out
where she has none (the Cargo Hold, a ship that has just left). Reputation entries about a ship carry all of these too. A crew
member, when crew files come (Phase 2), by `crew=<name>.<id>`; until then by `crew=<name>` and `race`.

## The clock (`log=clock`)

| Kind | Fields | Human line |
| --- | --- | --- |
| `DAY` | `clock` (the station's count after), `why` (a jump, time passed, business in the Cargo Bay, a day of rest in your quarters, work at a store in FTL) | "A day passed: a jump." |

## A ship's voyage (`log=voyage`; her log in her folder, `shipyard/<Name>.<id>/`)

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
| `SHIPS_DEFEATED` | `count`, `total`; `defeated` (6.09: the ship alongside at the look before, "the rebel ship", when one was beaten and the station saw who) | "Defeated the rebel ship." (before 6.09: "2 ships defeated (41 in all)") |
| `CREW_JOINED`, `CREW_LOST` | `crew` and `race` for each (repeated) | "Crew lost: Stoneface (Rock)" |
| `ITEMS_ABOARD`, `ITEMS_GONE` | `item` (repeated; " (cargo)" marks cargo) | "Aboard now: Burst Laser II" |
| `BOUGHT`, `PICKED_UP` | `item` (repeated) | "Bought at a store: Burst Laser II" |
| `STORE_ARRIVED` | `sector`, `beacon` | "Arrived at a store" |
| `BEACON_HAZARDS` | `hazard` (repeated: `asteroids`, `sun`, `pulsar`, `pds`, `nebula`, `storm`), `sector`, `beacon` | "Beacon: an asteroid field, a nebula" |
| `DISTRESS` | `sector`, `beacon` (6.21: she arrived at a distress beacon, known by the distress event's text on screen; before anything she met there) | "Responded to a distress signal." |
| `SHIP_MET` | `met` (in words), `sector`, `beacon` | "Encountered a Rock pirate." (before 6.09: "Ship met: a Rock pirate") |
| `SYSTEM_NEW`, `SYSTEM_UPGRADED`, `SYSTEM_REDUCED`, `SYSTEM_REMOVED` | `system` (its title), `level`, `was` | "Shields upgraded to 4" |
| `REACTOR_UPGRADED`, `REACTOR_REDUCED` | `level`, `was` | "Reactor upgraded to 12" |
| `FLAGSHIP_ALONGSIDE` | `battle` (1 to 3) | "The Rebel Flagship is alongside (battle 2)" |
| `FLAGSHIP_WITHDREW` | `battle`, `next` | "The Rebel Flagship withdrew after battle 1" |
| `VOYAGE_NOTE` | `text`; `difficulty`, `difficulty_chosen` (rescued, 6.02) | A line of the station's own in her log: commissioned, rescued after the final engagement (the difficulty of her next journey, and whether it was chosen or kept), time spent on work at a store. |

## Reputation (`log=reputation`)

| Kind | Fields | Human line today |
| --- | --- | --- |
| `REPUTATION` | `reason` (`achievement`, `cruiser`, `voyage`, `ship_lost`, `restored`, `expedition`, `captive`, `ransomed`, `spent`, `flagship`, `review`, `other`), `points` (signed, the whole points the total moved), `exact` (6.13: the change itself, to the ten-thousandth since 6.17: `4.5`, `2.0625`), `rate` (6.13: `1`, `1.5` or `2`, the career's rate), `ship_rate` and `sector_rate` (6.17, on what a ship earned in FTL: her difficulty's `1`, `1.25` or `1.5`; the sector's `1` to `1.7`), `total` (after, its whole part), `total_exact` (6.13), `detail.n` | The reputation log's own line: "Expedition: ... (+3)", each piece at what it added |

## The station log (`log=station`)

Every station-log entry carries `headline` (the entry's first line) and `detail.n` (its indented lines), plus the
fields below. A kind with several shapes says which in
`what` (or `stage`, `how`). A ship's fields are `ship`, `ship_name`, `ship_id`, `ship_state`, `stranger`; a file or
folder is given as it sits under the career folder (her folder, `shipyard/Kestrel.a3f2`, `junkyard/...` or
`memorials_and_records/ships/...`; `continue.sav`); `hold` is the
Cargo Hold, `junkyard` the Junkyard, `stored_systems` the stored-systems list. A kind not in this table is still
written (the writer never refuses one), but it is a bug to leave it undocumented.

### Ships: the Space Dock, FTL, the Junkyard, her history (Vault)

| Kind | Fields | Headline today |
| --- | --- | --- |
| `BOARD` | ship, `from`, `to`; `boards`, `mark` (6.08: her board count, and the state variable that carries it in her save) | "Kestrel  shipyard/Kestrel.a3f2 -> continue.sav" |
| `DOCK` | ship, `from`, `to` | "Kestrel  continue.sav -> shipyard/Kestrel.a3f2" |
| `DISBAND` | ship, `from`, `to` (junkyard) | "Kestrel  continue.sav -> junkyard/Kestrel.a3f2" |
| `SALVAGE` | ship, `from`, `to` | "Kestrel  junkyard/Kestrel.a3f2 -> shipyard/Kestrel.a3f2" |
| `DESTROY` | ship, `fate`, `from`, `to` (the memorial) | "Kestrel  junkyard/Kestrel.a3f2 -> memorials_and_records/ships/Kestrel.a3f2" |
| `RECOVER` | ship, `fate` (the fate she had), `from`, `to` | "Kestrel (scrapped)  memorials_and_records/ships/Kestrel.a3f2 -> shipyard/Kestrel.a3f2" |
| `RESTORE` | ship, `why` (`version`, `overwritten`), `from`, `to`, `reputation_back` | "Restored the Kestrel to an earlier version" |
| `OVERWRITTEN` | ship (the one lost), `versions`, `by`, `by_name`, `by_id`, `by_stranger` (the ship now in continue.sav) | "Kestrel (a3f2) was boarded, and continue.sav is now another ship: ..." |
| `VAULT` | `what` (`taking_stock`, `adopted_continue`, `set_aside`; 6.10: `arrived`, `taken_in`, `declined`, `rebuilt`, `save_restored`), ship (when one is named), `file`, `detail.n` (the notes); `set_aside` (6.08, the ship mark): `to`, `mark_career`, `mark_boards`, `mark_day`, `boards_now`, `in_fleet`, `other_career`; `checked` (6.10, a record from before 6.10 accepted on evidence): `origin`, `fingerprint`, `history`, `career`; `arrived` (sent here from a career): `from_career`, `from`, `how`, `origin`; `taken_in`: `to` (`here`, `sandbox`), `origin`, `record_career`; `declined`: `found` (`loose`, `unproven`, `other`, `no_save`); `rebuilt`/`save_restored`: `from` | "taking stock" |
| `JOURNAL` | `what` (`finished`, `stuck`), `action` (the note's kind: `SAVE`, `BOARD`, `DOCK`, `CREW_REGISTER`, `LEAVE`, `DISBAND`, `COME_HOME`, `RESTORE`, `RECEIVE`, `MARK` (6.10: her mark written into her save) `RESTORE_VERSION` (6.11: a kept version put back, already marked, with the save it replaced kept as her newest), `SEND_TO_SANDBOX` (6.87: a ship into Sandbox Mode's fleet, her leaving and her files there in one note) and `CLOSE_FINAL` (6.87: her final battle's copy kept as a version and its note closed)), `steps`, `note` (its file; since 6.87 under another fleet's folder when the note was left there, as every fleet's notes are settled by whichever opens first), `finished=startup`, `time` and `day` (the note's own), `left_by` (the station's version that wrote the note), `detail.n` (what couldn't be told, when stuck) | "The station finished what it had begun." |
| `FINAL_BATTLE` | ship, `copy`, `sector`, `victories_then`, `scores_then` | "Kestrel: the Rebel Flagship is on her way to the last battle. ..." |
| `VICTORY` | ship, `what` (`rescued`), or from FinalVictory: `victories_then`, `victories_now`, `top_scores`, `after` (the choice: `rescue` is Choice, `museum`), `museum_pay` (`full`, `half`, `none`; 6.47), `museum_price`, `value` | "Kestrel won the last battle (...)" |
| `MUSEUM` | ship, `scrap` (0 with the reward None), `pay` (`full`, `half`, `none`; 6.47), `to` | "Kestrel is honoured in the Federation Museum" |
| `SENT_TO_SANDBOX` | ship, `from`, `to`, `career` (6.10: Destroy in a career, the other way: on to Sandbox Mode's Junkyard) | "Kestrel  junkyard/Kestrel.a3f2 -> Sandbox Mode's Junkyard" |
| `SENT_AWAY` | ship, `to_commander`, `to` | "Kestrel (a3f2) to Vance's fleet, over Long Range Comm." |
| `RECEIVED` | ship, `from_commander`, `trade`, `to` | "Kestrel (a3f2) from Vance's fleet, over Long Range Comm.: docked" |
| `RETURNED` | ship, `why` (`trade_called_off`), `to`; or from the repair job: `what` (`to_owner`), `paid`, `late` | "Kestrel (a3f2): the trade was called off, and she is back at the Space Dock" |
| `SENT_BACK` | ship, `trade`, `to_commander` | "Kestrel (a3f2): the trade was called off, and she stays with Vance's fleet" |
| `TRADE_CALLED_OFF` | from Vault: ship, `what` (`stays_both`), `trade`, `peer`; from Exchange: `trade`, `peer`, `peer_station`, `why`, `came_back`, `sent_back` | "with Commander Vance  (trade t1)" |
| `LONG_RANGE_TRADE` | `trade`, `peer`, `peer_station`, `gave`, `received`, `received_to` | "with Commander Vance  (trade t1)" |
| `SENT` | ship, `from`, `to_fleet`, `to` | "Kestrel  shipyard/Kestrel.a3f2 -> the Sandbox fleet's Space Dock" |
| `HANDED_OVER` | ship, `file`, `to_fleet` | "Kestrel (continue.sav) to the Sandbox fleet, now in use" |
| `SWITCH_FLEET` | `stage` (`leaving`, `arrived`), `to_fleet`, `parked`, `boarded` | "to the Immersive fleet; Kestrel docked here, ..." |
| `CAREER_ENDED` | `fleet`, `copy`, `files` | "the Immersive career was ended; a copy is kept in ..." |
| `PLEAD`, `UNDO_PLEA` | `stage` (`agreed`, `hold_given`), `value`, `refund` | "The Federation Home Planet agreed to send a new ship ..." |

### The Space Dock, the Cargo Bay, the designs, Slipstream

| Kind | Fields | Headline today |
| --- | --- | --- |
| `COMMISSION` | ship, `detail.n` (her fittings and crew), `crew` and `race` once a crew member (6.30: the Captain's Log's "came aboard with her") | "Kestrel  (a3f2)" |
| `NEW_JOURNEY` | ship, `difficulty`, `fee`, `paid` | "Kestrel  difficulty Normal, fee ..." |
| `RENAME` | ship, `from`, `to` | "Old Glory -> Kestrel  (a3f2)" |
| `RENAME_CREW` | `what` (`renamed`, `promoted`), `from`, `to`, `crew_id`, `race`, `rank`, `posthumously`, `place`, `ship_name`, `ship_id`, `on_record` (`true` when a rank given on the record is put in her save) | "Gracie -> Sgt. Gracie  (Kestrel)" |
| `REMODEL` | `ship_name`, `to_class`, `detail.n` | "Kestrel -> PLAYER_SHIP_FED" |
| `SCRAP` | `ship_name`, `stripped`, `to`, `detail.n` (what went into storage) | "Kestrel stripped into storage, hull broken up" |
| `SELL` | from the Space Dock: `ship_name`, `how` (`auction`, `trade_in`), `price`, `to`; from the Cargo Bay: `what` (`cargo_bay`), `count`, `scrap`, `detail.n` | "Kestrel sold at auction for 80 scrap; ..." |
| `JUNK` | `what` (`cargo_bay`), `count`, `detail.n` (what was thrown out) | "2 items" |
| `BUY` | `what` (`cargo_bay`: `purchases`, `detail.n`; `derelict`: ship, `ship_class`, `price`, `oddity`; `salvage`: `item`, `title`, `price`; `part`: `system`, `title`, `level`, `broken`, `clearance`, `price`; `search` (6.66): `parts`, `price`), `from`, `to` | "2 purchases" |
| `TRADE` | `what` (`cargo_bay`), `ship_name`, `ship_id`, `partner_name`, `partner_id`, `detail.n` | "Kestrel <-> Spacedock Storage" |
| `SYSTEMS` | `ship_name`, `ship_id`, `detail.n` (the changes) | "Kestrel" |
| `CREW` | `what` (`assigned`), `crew`, `race`, `to` (`hold`, `ship`), `ship_name` | "Gracie assigned to the Kestrel." |
| `RETIRE` | `what` (`cargo_bay`), `count`, `detail.n` | "1 crew member" |
| `DESIGN` | `what` (`built`, `saved`, `retired`, `deleted`, `version_built`), `design`, `design_id`, `blueprint`, `version`, `kept_version`, `rooms`, `doors` | "Built Nightjar (PLAYER_SHIP_X_HP, v2)" |
| `BLUEPRINT` | `what` (`restored_from_backup`), `blueprint` | "PLAYER_SHIP_X_HP restored from its backup ..." |
| `BLUEPRINTS` | `changes`, `detail.n` | "3 change(s)" |
| `CLEAN` | `count`, `detail.n` (the blueprints removed) | "Removed 2 unused blueprint(s)" |
| `PATCH` | `ok`, `mods`, `launched_ftl`, `exit`, `detail.n` (the mods) | "Patched 2 mods with Slipstream" |

### Expeditions, the infirmary, Captain's Quarters, the repair job

| Kind | Fields | Headline today |
| --- | --- | --- |
| `EXPEDITION` | `what`: `sent` (`sector`, `sector_id`, `party`, `crew` and `race` repeated); `back` (`sector`, `sector_id`, `job`, `job_id`, `scrap`, `prize`, `prize_detail`, `captured`, `good`, `bad`, `crew` repeated, `killed` repeated, `role` repeated (6.39: `name:skill:kind:band:points`, kind `primary`, `secondary` or `other`; none on a job of race alone), `infirmary` repeated (6.61: laid up), `saved` repeated (6.61: `name:saver`, a life saved by a crewmate's outstanding success; the human line ends `; saved: Ann by Kenji`)); `prize_ship` (ship, `ship_class`, `to`); `recruit_declined`, `prize_ship_declined` (`name`); `job` (`job`, `job_kind`, `event_id`, `scrap`, `item`, `joined`, `lost`, `hurt` repeated); `out_of_infirmary` (`crew`); `captive_lost` (`crew`, `race`, `captors`, `why`); `ransomed` (`crew`, `race`, `captors`, `ransom`, `to`) | "Twin sent to Nebula" |
| `HIRE` | `how` (`rescued`, `promise`, `posted`), `crew`, `race`, `cost`, `reputation`, `to` | "Posted for volunteers, 40 scrap", then (6.65: posting ends the day) on the next day, if someone answered, "Answered the posting: Bob (human) joined, in the Cargo Hold" |
| `MEDBAY` | `crew`, `race`, `place` | "Bob's visited The Station's Medbay" |
| `REST` | `days_in_a_row`, `cost` | "Rested in quarters (2 days in a row)" |
| `REPAIR_JOB` | `stage` (`delivered`: ship, `to`, `cost`, `value`; `defied`: `ship_name`) | "The Nightjar delivered to the Junkyard (a3f2)" |
| `SEIZED` | `what` (`collected`: ship, `by`; `office`: `taken`) | "The Federation Office of Salvage and Claims took ..." |

### The inbox, the career, the settings

| Kind | Fields | Headline today |
| --- | --- | --- |
| `TRANSMISSION` | `key`, `from`, `subject`, `how` (`sent`, `posted`, `delivered`), `reward` | "Expedition Command: Back from Nebula" |
| `REPLY` | `key`, `from`, `subject`, `reply` | "Expedition Command: Yes" |
| `CLAIM` | `key`, `subject`, `what`, `to` | "Stipend: 120 scrap to the Cargo Hold" |
| `STIPEND` | `scrap`, `months` | "120 scrap issued, to claim from the inbox (one stipend)" |
| `OVERFLOW` | `what` (`shipped`, `shipped_home`, `lost`), `augment`, `title`, ship or `ship_name`, `parcel`, `to` | "Kestrel had no room for ...: her crew ship it home" |
| `GIFT` | `from`, `system`, `title`, `to` | "The Third Fleet Commander sent a ... system for the project ship, ..." |
| `CAREER` | `what` (`begun`, `rescued_to_hard`, `reputation_rate`), `mode`, `stipend`, `own_profile`, `scrap`, `difficulty`, `with_ship`; `rescued_to_hard` (6.03: a Custom career's choice, fixed); `rate` and `level` (6.13: a Custom career's reputation rate, `1`, `1.5` or `2`, level 0 to 2, fixed) | "Immersive career begun: ..." |
| `SETTINGS` | `commander_name`, `girlpower` (6.22; `/easter girlpower` since 6.35, `true` or `false`, with `by=easter`), `detail.n` (each setting changed) | "" |
| `PROFILE` | `what` (`removed`), `removed`, `keys` | "Removed from FTL's profile: ..." |
| `UPDATE` | `version`, `replaced`, `added`, `removed` | "New construction plans from main (5.64): ..." |

### Long Range Comm. (the trades are with the ships, above)

| Kind | Fields | Headline today |
| --- | --- | --- |
| `LONG_RANGE_OUTBOX` | `what` (`waiting`, `delivered`, `cancelled`), `to_commander`, `to_station`, `message_id`, `priority`, `waited`, `shipment` | "a message for Vance waits to go" |
| `SHIPMENT_PACKED`, `SHIPMENT_UNPACKED`, `SHIPMENT_SENT`, `SHIPMENT_ARRIVED`, `SHIPMENT_ACCEPTED`, `SHIPMENT_RETURNING`, `SHIPMENT_RETURNED` | `shipment`, `state`, `incoming`, `peer`, `peer_station`, `goods`, `to`, `to_fleet`, `to_commander` | "3 missiles and a Burst Laser I from Vance: in the Cargo Hold" |

### Old station logs only

| Kind | Fields |
| --- | --- |
| `LOADED` | the fleet's listing at a refresh, the debug log's since 5.53: `headline`, `detail.n` |
| `PROFILE` | FTL's profile backed up, set aside or brought back (Immersive Mode's own profile): `headline` |
| `SLIPSTREAM` | which Slipstream the station found and uses: `headline` (its folder) |
| `UNDO_RETROFIT` | a retrofit undone (4B): `headline` (her name, the blueprints, her file) |
| `CLAUDE` | a note a session wrote into a player's log by hand (a repair, a test): `headline`, `detail.n` |


## Adding a kind

1. Add its row here, with every field the program could ever need (more than named is better than missing).
2. Write it with `Event.of(...)`; give the human line, under the hard rules.
3. EventT checks every kind a test world writes is in this file, and that no human line counts beacons.
