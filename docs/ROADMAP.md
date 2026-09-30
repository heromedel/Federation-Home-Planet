# Federation Home Planet: roadmap

Decisions made with heromedel (the owner), and the order to build them in. Anything marked **Open** still needs
the owner's call. Player-facing text follows the Voice section of CLAUDE.md.

## Build order

1. Save safety
2. Retired designs, and a blueprint copy with each ship
3. Ship history and restore
4. Pricing and house rules (HR1, HR2, Report for Reassignment, free unlock ships, Immersive Mode)
5. Dry Dock: repairs and upgrades
6. Fixes from testing, and the settings moves
7. Immersive Mode, part 2: its own vault, rules kept apart, uncommissioned-ship detection, rank and locks
8. Transmissions: the inbox, commission orders, promotions, achievement rewards (draft: docs/TRANSMISSIONS.md)
9. Immersive Mode, part 3: the briefing, its own FTL profile, the stipend, unlock hints, Steam Cloud
10. Keep ships after victory (save timing confirmed; waiting on the flagship stages 2 and 3)

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

## 4. Pricing and house rules — done (harness tests PriceT, RuleT)

Pricing (`parser/Pricing`), HR1 and HR2 are built (harness test PriceT). A Kestrel A comes to 1005 scrap
under HR2 (systems 497, reactor 255, gear 118, crew 135). Reactor bars are priced 30 each for the first 5, then 5
more every 5 bars. Selling a stored system pays the boarded ship, as a store would.

Also built: the empty-shipyard free ship (a setting under HR2: a Kestrel A by default, any ship, or the relief ship,
which comes to 777 under HR2), and Report for Reassignment under Other…. What was surrendered is kept in the vault's
`surrendered/` folder; **Undo Reassignment** works only until the new command is taken (no ship at the Space Dock)
and while the hold is untouched, so undoing never keeps both. The free ship only matters with HR2 on (otherwise every
commission is free), so the setting sits under it. The relief ship's reactor is 7: enough for a shield layer, both
guns, engines, oxygen and medbay.

Unlock-once free ships and Immersive Mode are built too. The unlock record is `unlock-grants.txt` in the vault
(layouts seen when the rule was turned on, and free ships claimed); turning the rule off and on again adds what's
unlocked by then to "seen". Both rules sit under HR2 in the rules window, since without HR2 every ship is free.
Immersive Mode also makes a Report for Reassignment final (no Undo). It leaves "Scrapping moves her systems" and the
unlock rules to the player, as the list below doesn't name them.

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

## 5. Dry Dock: repairs and upgrades — done (checks in PriceT)

Built into the Cargo Bay's Refit tab, so it follows the one trading rule with the rest of the Cargo Bay; the boarded
ship pays, and Save makes it official (Reset undoes it).

- **Upgrades:** an **Up: price** button on each installed system (FTL's upgrade cost for the next level, up to FTL's
  limit or her room's), and a Reactor row (30 a bar up to 5, then 5 more every 5 bars, up to 25).
- **Repairs:** a Hull row; Repair fixes as many points as she can afford. **Check:** the price is 2 scrap a point in
  sectors 1-2 and one more every two sectors after; FTL's own store rate should be confirmed in a real run.

## 6. Fixes from testing, and the settings moves — done, except the reactor prices

- **Hull repairs at FTL's rate:** 2 scrap a point in sectors 1-3, 3 in sectors 4-6, 4 in sectors 7-8 (FTL wiki).
- **Reactor prices:** waiting on heromedel's numbers from FTL's upgrade screen (he remembers about 25, then 30, then
  35, changing every 10 bars or so). The price table is in one place (`Pricing.reactorBar`).
- **Open the station's folder:** a button in Settings, beside the folder fields.
- **Clean up blueprints:** moves from Settings to Other…, with an explanation of what it's for: it removes
  blueprints no ship flies any more (retired designs included) from the Federation Home Planet Mod.
- **Rules kept apart:** today Immersive Mode overwrites the normal rules, and they stay changed when it's turned off.
  The normal rules are kept separately and come back untouched (done with item 7).

## 7. Immersive Mode, part 2 — done (harness tests FleetT, RuleT)

- **Its own vault.** `FederationHomePlanet-Immersive` beside the normal one, with its own Space Dock, Junkyard,
  storage hold, stored systems, ship records, surrenders, unlock grants, rank and transmissions. The first switch
  into it starts with an empty shipyard (a new career).
- **Shared by both vaults:** designs, remodels and their blueprint backups (FTL has only one Federation Home Planet
  Mod, so it carries both fleets' blueprints). "Clean up blueprints" and "in use" checks look at both vaults.
- **Switching modes** (the Immersive Mode tick): only while FTL is closed. The boarded ship is docked into the vault
  being left; then the other vault's boarded ship, if it had one, goes back into `continue.sav`.
- **Uncommissioned-ship detection** (both modes). The station knows every ship it commissioned and which one is
  boarded. `continue.sav` is not her if the name or model differs, or if any number that only goes up during a
  journey (sector, beacons explored, ships defeated, scrap collected) is lower than the station last saw. FTL's New
  Game overwrites the boarded ship, so she is then recorded lost and can be recovered (normal mode).
  - Normal mode: the new ship is taken in, as now, and the player is told if a boarded ship was overwritten.
  - Immersive Mode asks: **Send her to the normal Space Dock** / **Decommission her** (then: send to the normal
    Junkyard, or Destroy, which keeps a copy in her records) / **Switch to normal mode now** / **Close The Home
    Planet Station**.
- **Rank** (Immersive vault only): Commander at the start; Captain when the Federation Cruiser A unlocks; Commodore
  when the Federation Cruiser C unlocks. Each unlock raises one rank, whatever the order. Only unlocks after
  Immersive Mode is on count.
- **Locks by rank** (Immersive Mode): locked standard ships are hidden (as the lock rule does); **Captains** may
  design ships, remodel and overhaul, and commission custom ships; **Commodores** may fit the Federation's artillery
  (Artillery Beam, Flak Artillery); the **Rebel Flagship's weapons** as artillery are cleared by "Rule Ten: Greed is
  Eternal" earned in Immersive Mode. Marked in Commission and the artillery picker with the reason.
- **Artillery prices (HR2), a luxury:** the Artillery Beam 200 scrap, Flak Artillery 150, each Rebel Flagship weapon 100.
- **Unlock-once free ships:** Immersive Mode turns this rule on and locks it, with the two "locked ships" rules.
- As built: the rank shows in the Space Dock's "Docked Ships" header; the rank record lives with the unlock record
  (`unlock-grants.txt`, "promoted" lines). Commission marks custom ships "(Captains only)" / "(Commodores only)".

## 8. Transmissions — done (harness test TransT)

Draft of every message and reward: `docs/TRANSMISSIONS.md` (rewards approved by heromedel; messages for review).

- **The inbox:** a transmission icon on the Space Dock, a green light with the unread count; the inbox lists
  sender, subject and date; messages with a reward have **Claim**.
- **Immersive Notifications:** a setting of its own; Immersive Mode ticks and locks it. Without it, the free-ship
  rules still work, silently ("(free)" in Commission).
- **Messages:** welcome to Immersive Mode; the empty shipyard's free command; a commission order for each ship
  unlocked in FTL (a lore reason per race); promotions from the Fleet Admiral.
- **Achievement rewards** (Immersive Mode only; achievements earned after it was turned on): scrap, supplies, items,
  crew volunteers and systems, into Spacedock Storage; a free ship becomes a commission order used up in Commission.
- Checked at startup and on Refresh; each message is sent once.
- As built: the texts and rewards live in `src/main/resources/homeplanet/resource/transmissions.txt` (edit it to
  change wording or rewards; TransT checks every achievement has a message and every reward exists in FTL). The inbox
  is kept per fleet (`transmissions.xml`). Commission orders and promotions have a **Commission…** button; the free
  ship itself follows the free-ship rules. Achievements count from when the fleet's record began, and not while away
  in the other fleet.

## 9. Immersive Mode, part 3 — done (harness tests TransT, VaultT)

- **The button:** Settings shows **Enter Immersive Mode…** / **Return to Normal Mode…** instead of a tick box. Entering
  opens the briefing (everything Immersive Mode does, with how to earn each rank) and the career's choices, fixed once
  made: whether the stipend counts **every achievement** in the FTL profile or **only those earned from now on**, and
  whether Immersive Mode gets **its own FTL profile** (ticked by default; the salary choice is then "from now on").
  Confirm (Cancel is the default) switches at once; FTL must be closed.
- **Its own FTL profile:** `ae_prof.sav` (or `prof.sav`) is moved into the normal fleet's `ftl-profile` folder and FTL
  starts a fresh one; leaving moves the Immersive one into its fleet's folder and brings the normal one back. Nothing
  is deleted. The briefing names the file and folder, and **Back up my FTL profile** copies it to
  `FederationHomePlanet/profile-backups`.
- **A career** begins with 25 scrap in Spacedock Storage (`career.txt` in the Immersive fleet's folder).
- **The stipend:** every 4 sectors the fleet's ships travel (FTL's own progress, counted in `sectors.txt`), 20 scrap
  plus, per achievement counted, 1 (Commander), 2 (Captain), 3 (Commodore): 71 / 122 / 173 with all 51. Paid into
  Spacedock Storage; one message for all the months due ("your stipend for the last 2 months"), with **Delete**.
- **Unlock hints:** Design Ship, Remodel, Commission rows and the artillery picker say what they need and how to earn
  it (the promotion conditions are from the FTL wiki, since FTL keeps them in the game itself).
- **Hull repairs:** a flat 4 scrap a point (The Federation charges a premium), in both modes.
- **Steam Cloud:** a warning at first start (Steam installs), in Settings and in the briefing. And a `continue.sav`
  that's a byte-for-byte copy of a docked ship or one of her kept versions (Steam Cloud restoring its last upload) is
  set aside in her records with a notice, instead of becoming a second ship.

## 10. Keep ships after victory

Off by default: "Keep ships after victory (The Home Planet Station must stay open while you play)."

- While FHP is open it watches `continue.sav` for changes (no timed polling) and keeps the latest copy in the boarded
  ship's records. If FTL crashes before saving, there is nothing new to keep, and that's fine.
- FTL saves on each jump, and the flagship's three battles are separated by jumps; the save records the flagship's
  state. Keep the copy written **on arriving for the third battle**, the latest point FTL saves.
- When `continue.sav` disappears and the FTL profile shows a new victory, offer to bring her home from that copy.
  A death stays "lost in action".
- **Confirmed** with heromedel's run (the Shrapnel R.U., sector 8): FTL writes `continue.sav` on arriving at a beacon,
  with the game still running, and saving and exiting there adds nothing (the files were identical). The save holds
  the sector (7 = sector 8) and the flagship's pending stage (1 = the first battle next).
- **Still to see:** the stage reading 2 and 3 before the second and third battles.

## Naming decisions still open

- The storage hold has several names ("Spacedock Storage", "storage hold", "the Space Dock's Cargo Hold", "the
  storage"). Pick one. ("Spacedock Storage" is also stored in save files, so renaming the save needs care.)
- "Disband" vs "Decommission" for the same action ("Decommission" pairs with "Commission").
- "Patch" buttons vs "sent to FTL via Slipstream" in messages ("Send to FTL" / "Transmitting…"?).
- Which tab is the Dry Dock (the "Shop" tab?), and one line explaining Refit → Retrofit → Remodel → Overhaul.
- README: out of date (the rules list, "docking" for trading); refresh when features land.
