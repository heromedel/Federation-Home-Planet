# Federation Home Planet: roadmap

Decisions made with heromedel (the owner), and the order to build them in. Anything marked **Open** still needs
the owner's call. Player-facing text follows the Voice section of CLAUDE.md.

## Build order

1. Save safety
2. Retired designs, and a blueprint copy with each ship
3. Ship history and restore
4. Pricing and house rules (HR1, HR2, Report for Reassignment, free unlock ships, Immersive Mode)
5. Dry Dock: repairs and upgrades
6. Keep ships after victory

---

## 1. Save safety — done (harness test SafeT)

Found in a code review; each was traced through the code. Common thread: when a later step fails, undo what the
earlier steps did, and never save over data that didn't load properly. Add a harness test for each failure case.

- **A. A damaged `designs.xml` or `remodels.xml` can wipe designs, remodels and art.** A parse error is only logged
  (`ShipDesign.load`, `CompanionMod.parse`), the short list is saved back over the file, and `ShipArt.sweep()` then
  deletes every picture the short list doesn't name. Refuse to save or sweep when a file exists but didn't load.
- **B. Cargo Bay Save writes stale copies.** Saves are read once when the Cargo Bay opens and written back without
  checking. Play FTL with FHP open, then Save: the progress is overwritten, and a ship that died comes back. Record
  each file's fingerprint when read; the transaction refuses if a file changed or vanished.
- **C. The window's close button drops unsaved Cargo Bay changes** (`MainFrame`, `EXIT_ON_CLOSE`). Ask Save /
  Discard / Cancel, as Return to Dock does.
- **D. A failed Board leaves a copy in `continue.sav`** (`Vault.board`), which becomes a duplicate ship on Refresh.
  Remove it if a later step fails.
- **E. Remodel writes the blueprint before the ship's save and ignores a failed save** (`RemodelDialog.finalizeBlueprint`).
  Check for FTL running first; roll the remodel back if the save fails.
- **F–H.** A multi-file save failing halfway (`Vault.Transaction.commit`), Scrap after an error (it edits the cached
  storage), Dock with a full disk: restore the files already replaced, work on fresh copies, undo state changes.
- Smaller: startup file choosers still run off the event thread (`HomePlanet.main`); the config path should use
  `appDir()`; title music ignores FTL on Mac/Linux (`Music.isFtlRunning`); history pruning can drop the newest
  snapshot (sort by time, not name); a failed Commission leaves a ghost entry (`Vault.adopt`).

## 2. Retired designs, and blueprint backups — done (harness test BlueT)

Built as a backup per blueprint (the vault's `blueprints/` folder) rather than per ship: the same protection, and a
ship moving between the Space Dock, Junkyard and history needs no bookkeeping.


- Deleting a design that ships still use **retires** it: hidden from the Design list and from Commission, kept in
  `designs.xml`, still built into the Federation Home Planet Mod while any ship uses it (Space Dock, Junkyard or
  history). "Clean up blueprints" removes it once nothing uses it. This replaces the "disband them first" message,
  which was wrong anyway (junked ships still count).
- Each ship also keeps **a copy of her blueprint and layout** in the vault, refreshed whenever her blueprint changes.
  The design and remodel files stay the master copy; a ship's copy is only a backup, used to rebuild a missing
  master, and never overrides it.

## 3. Ship history and restore — done (harness test HistT)

- The ship report has a **Records** button: her kept versions (newest first, with sector, beacons, hull, scrap and
  fuel), **Restore this version**, and her entries from `history.log`. Restoring keeps the version it replaces, so
  it can be undone.
- **Other… > Recover a ship…** on the Space Dock brings back a destroyed ship, or one lost in action, from her last
  kept version. Why each ship left is recorded in her history folder (`fate.txt`).
- **Scrapped ships can't be recovered**: everything aboard went into storage, so she'd come back with a second copy
  of it. (The Scrap message already says the hull "can never be recovered".)
- While a ship is boarded, each change the station makes to her is kept in her records too, so a ship lost in
  action comes back as the station last saw her. Identical versions aren't kept twice.
- Still to do with Immersive Mode (item 4): turn off Restore and Recover.

## 4. Pricing and house rules

Progress: pricing (`parser/Pricing`), HR1 and HR2 are built (harness test PriceT). A Kestrel A comes to 1005 scrap
under HR2 (systems 497, reactor 255, gear 118, crew 135). Reactor bars are priced 30 each for the first 5, then 5
more every 5 bars. Selling a stored system pays the boarded ship, as a store would.

Also built: the empty-shipyard free ship (a setting under HR2: a Kestrel A by default, any ship, or the relief ship,
which comes to 777 under HR2), and Report for Reassignment under Other…. What was surrendered is kept in the vault's
`surrendered/` folder; **Undo Reassignment** works only until the new command is taken (no ship at the Space Dock)
and while the hold is untouched, so undoing never keeps both. The free ship only matters with HR2 on (otherwise every
commission is free), so the setting sits under it. The relief ship's reactor is 7: enough for a shield layer, both
guns, engines, oxygen and medbay.

Prices come from FTL's blueprints (system cost and upgrade costs, weapon, drone, augment and crew costs). Measured
from the game data: a Kestrel A is about 590 scrap for systems, starting upgrades and gear, roughly 950 with reactor
and crew; the 28 player ships average 656 before reactor and crew. The Federation artillery weapon has no price (0);
the artillery system costs 150, upgrades 30/50/80.

- **HR1: Sell systems** (off by default). Price: half the system's base cost plus half the upgrades paid for.
- **HR2: Commissioning costs scrap** (off by default), for any ship. Price: systems at store price + each upgrade
  level + reactor power + weapons, drones, augments at store price + crew at hiring price. Custom designs add: 25 per
  system with no known price, 10 per room, 5 per door, 100 for an artillery weapon with no price. Paid from the
  storage hold; the Commission window shows the price. A **price multiplier** (50% / 75% / 100%) sits beside it.
- **Empty shipyard.** When no ship is at the Space Dock, boarded, or in the Junkyard, a free ship is available. A
  setting chooses which: any ship / only a Kestrel A / only a **Federation relief ship** (a Kestrel A save stripped to
  basics: one human crew, a basic laser and a basic ion, missiles, no drones, every system at its minimum, a small
  reactor; no custom blueprint needed).
- **Report for Reassignment.** How a captain claims that free ship: surrender the storage hold (items, crew, scrap,
  stored systems) and every hull in the Junkyard to The Federation Home Planet in exchange for a new command. What was
  surrendered is kept in a backup so it can be undone. Lives behind a new **Other…** button on the Space Dock for
  rarely used actions. **Open:** the button's name (Other… / Operations…).
- **Unlocks grant a free ship once** (off by default). Each ship type unlocked in the FTL profile **after the setting
  is turned on** can be commissioned free once. Claimed types are recorded in the vault; Report for Reassignment
  doesn't reset them. Unlocks from before the setting was turned on never count.
- **Immersive Mode.** One switch that sets and locks the rules (greyed out, "Set by Immersive Mode"):
  trading and scrapping need a station; New Journey needs a station; commissioning costs scrap at 100%; **a New
  Journey costs 200 scrap, paid from the storage hold** (so a stranded ship that reached a station can be rescued);
  selling missiles, drone parts and systems is **on, at 25%** of the store price; restoring old versions of a ship
  is off. Keep ships after victory stays available. Turning Immersive Mode off unlocks the settings.

## 5. Dry Dock: repairs and upgrades

- **Upgrades:** system levels and reactor power, at FTL's upgrade prices.
- **Repairs:** hull, at FTL's store price.
- Both follow the one trading rule, like the rest of the Cargo Bay (a split rule doesn't work: if trading is free,
  another ship could buy for you).

## 6. Keep ships after victory

Off by default: "Keep ships after victory (The Home Planet Station must stay open while you play)."

- While FHP is open it watches `continue.sav` for changes (no timed polling) and keeps the latest copy in the boarded
  ship's records. If FTL crashes before saving, there is nothing new to keep, and that's fine.
- FTL saves on each jump, and the flagship's three battles are separated by jumps; the save records the flagship's
  state. Keep the copy written **on arriving for the third battle**, the latest point FTL saves.
- When `continue.sav` disappears and the FTL profile shows a new victory, offer to bring her home from that copy.
  A death stays "lost in action".
- **To confirm with a real run first:** that FTL writes `continue.sav` on every jump around the flagship, and how
  the flagship stage reads in the save.

## Naming decisions still open

- The storage hold has several names ("Spacedock Storage", "storage hold", "the Space Dock's Cargo Hold", "the
  storage"). Pick one. ("Spacedock Storage" is also stored in save files, so renaming the save needs care.)
- "Disband" vs "Decommission" for the same action ("Decommission" pairs with "Commission").
- "Patch" buttons vs "sent to FTL via Slipstream" in messages ("Send to FTL" / "Transmitting…"?).
- Which tab is the Dry Dock (the "Shop" tab?), and one line explaining Refit → Retrofit → Remodel → Overhaul.
- README: out of date (the rules list, "docking" for trading); refresh when features land.
