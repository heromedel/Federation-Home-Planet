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
10. After a final victory: rescue her (or the museum), or a reward of her value
11. The museum
12. Reply chains, and the first one: the One Point of Hull (Idea C)
13. The station's economy: Trade In and Auction, Refit removal, stripping, fees (4B.58)
14. Immersive difficulties: Easy, Normal, Hard and Custom (4B.59)
15. Switch Game Mode: Sandbox and four careers, each with its own fleet and profile (4B.60)

All built. What remains is testing in real play and bug checks.

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
- In Immersive Mode, Restore and Recover are off (done).

## 4. Pricing and house rules — done (harness tests PriceT, RuleT)

Pricing (`parser/Pricing`), HR1 and HR2 are built (harness test PriceT). A Kestrel A comes to 885 scrap
under HR2 (systems 497, reactor 135, gear 118, crew 135). Reactor bars are priced 15 each for the first 5, then 5
more every 5 bars. Selling a stored system pays the boarded ship, as a store would.

Also built: the empty-shipyard free ship (a setting under HR2: a Kestrel A by default, any ship, or the relief ship,
which comes to 777 under HR2), and Report for Reassignment under Other…. What was surrendered is kept in the vault's
`surrendered/` folder; **Undo Reassignment** works only until the new command is taken (no ship at the Space Dock)
and while the hold is untouched, so undoing never keeps both. The free ship only matters with HR2 on (otherwise every
commission is free), so the setting sits under it. The relief ship's reactor is 7: enough for a shield layer, both
guns, engines, oxygen and medbay. Settings' free ship defaults to the relief ship. In Immersive Mode the setting doesn't
apply: a new career starts on a Kestrel Type A, as a new FTL game does, and a Report for Reassignment earns a ship by
everything of value surrendered (the Cargo Hold and the Junkyard's hulls): 1000 scrap or more, any ship; 500 or more,
a Kestrel Type A; less, a relief ship. The Shipyard Comm. Officer's letters say which (`docs/VOICES.md`).

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
- **The free command** (heromedel, 4B.23: "an empty shipyard should always require you to either commission a new one
  or report for reassignment"). Granted **once when the fleet starts** (a new fleet, an Immersive career) and **again
  with each Report for Reassignment**; one transmission per grant. An empty shipyard alone never grants one, and an
  uncommissioned ship coming and going changes nothing. Undoing a report takes its grant back. A setting chooses which
  ship: any ship / only a Kestrel A / only a **Federation relief ship** (a Kestrel A save stripped to
  basics: one human crew, a basic laser and a basic ion, missiles, no drones, every system at its minimum, a small
  reactor; no custom blueprint needed).
- **Report for Reassignment.** How a captain claims that free ship: surrender the storage hold (items, crew, scrap,
  stored systems) and every hull in the Junkyard to The Federation Home Planet in exchange for a new command. What was
  surrendered is kept in a backup so it can be undone. Lives behind a new **Other…** button on the Space Dock for
  rarely used actions. Named **Other…**; it opens the Other Orders window (each order with what it does, and why not).
- **Unlocks grant a free ship once** (off by default). Each ship type unlocked in the FTL profile **after the setting
  is turned on** can be commissioned free once. Claimed types are recorded in the vault; Report for Reassignment
  doesn't reset them. Unlocks from before the setting was turned on never count.
- **Immersive Mode.** One switch that sets and locks the rules (greyed out, "Set by Immersive Mode"):
  trading and scrapping need a station; New Journey needs a station; commissioning costs scrap at 100%; **a New
  Journey costs 200 scrap, paid from the storage hold** (so a stranded ship that reached a station can be rescued);
  selling missiles, drone parts and systems is **on, at 25%** of the store price; restoring old versions of a ship
  is off. The choice after a final victory stays available (each fleet its own). Turning Immersive Mode off unlocks the settings.

## 5. Dry Dock: repairs and upgrades — done (checks in PriceT)

Built into the Cargo Bay's Refit tab, so it follows the one trading rule with the rest of the Cargo Bay; the boarded
ship pays, and Save makes it official (Reset undoes it).

- **Upgrades:** an **Up: price** button on each installed system (FTL's upgrade cost for the next level, up to FTL's
  limit or her room's), and a Reactor row (15 a bar up to 5, then 5 more every 5 bars, up to 25).
- **Repairs:** a Hull row; Repair fixes as many points as she can afford. **Check:** the price is 2 scrap a point in
  sectors 1-2 and one more every two sectors after; FTL's own store rate should be confirmed in a real run.

## 6. Fixes from testing, and the settings moves — done

- **Hull repairs at FTL's rate:** 2 scrap a point in sectors 1-3, 3 in sectors 4-6, 4 in sectors 7-8 (FTL wiki).
- **Reactor prices:** heromedel's numbers from FTL's upgrade screen: 15 a bar for bars 1-5, 20 for 6-10, 25 for
  11-15, 30 for 16-20, 35 for 21-25 (confirmed by heromedel). The price table is in one place
  (`Pricing.reactorBar`).
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

- **Ending a career** (heromedel, 4B.24): Return to Normal Mode offers "Return and keep my career" (the default) or
  "Return and end my career...", with a second confirmation (Cancel the default) listing what's lost in the career's
  own numbers. The whole Immersive folder (its own FTL profile too) is zipped into
  `FederationHomePlanet/old-immersive-careers/`, checked, and only then deleted; the next entry begins a new career.

## 10. After a final victory — built

A setting (Settings, and the Immersive briefing), each fleet its own choice, default Nothing:

- **Nothing** (she is lost with the run).
- **Rescue the ship** (with an offer to sell her to the museum): she comes back as she was moments before the final
  engagement. Keep her (docked, ready for a new journey from sector 1: the run itself is over), or accept The Federation
  Home Planet's offer of her full value for the Federation museum (scrap to Spacedock Storage; her fate is MUSEUM, and
  she can't be recovered).
- **Receive a reward equal to her value**: her full value to Spacedock Storage, and she stays lost.

Her value is the full commission price (systems and levels, reactor, weapons, drones, augments, cargo, crew, a custom
hull's rooms and doors), always 100%. The messages come by Transmissions (the rescue offer waits in the inbox), or as a
notice on the Space Dock when Transmissions are off. The lore holds: the Rebel Flagship withdraws, never destroyed.

How it works (confirmed with heromedel's run of the Shrapnel R.U. and a save logger's record of the last two battles):

- FTL writes `continue.sav` as the flagship heads for each battle (her pending stage 1-3, not alongside), again as she
  arrives (alongside) and as the player closes her message, then **nothing during the fight**. A win writes the profile
  (Total Victories +1, a Top Scores entry naming the ship with Victory: true) and deletes `continue.sav` 58 ms later.
  Waiting in place also counts as a beacon explored.
- While the station is open it watches the saves folder (the system's change notices, no timed polling) and keeps the
  latest copy written with the flagship **on her way to the last battle** (stage 3, not alongside): a calm save, no
  battle in it. `history/<id>/final-battle.sav`, with the profile's victory count then.
- When the ship is found lost (the run ended), a victory count gone up means she won; then the choice applies. The
  Space Dock takes stock as soon as its window comes to the front after FTL deletes the save.
- Harness test VicT (synthetic saves), and a replay of a logger's folder when VICLOG is set.

## 11. The museum — built (4B.31; harness checks in VicT)

A screen of its own (the Space Dock's **Museum** button, shown once there's a victory or a ship lost), in the
station's style: the FTL fonts, gold headings, ships from the game art. The heading shows the fleet's total victories.

- **Two wings, as tabs:**
  - **Hall of Victors:** every ship that won, whatever the choice after a final victory. She stands on a lit plinth
    with a gold name plate; her victories are counted (a kept ship can win again). Under the plate, her status:
    **★ Preserved in the Museum ★** (gold, sold to the museum), **Still in Service** (green, kept and flying),
    **Honoured in Memory** (silver, won but not kept), **Lost in Action, Sector N** (silver, kept and lost later).
  - **Memorial:** ships lost in action without a victory: a plain silver plate, her class, where she was lost, her
    beacons and ships defeated. No trophies, stars or honours.
- **Moving around:** ‹ › arrows beside her (and the Left/Right keys), and a gallery strip of framed thumbnails along
  the bottom (the one shown framed in gold), within the wing shown.
- **Tabs for each ship:**
  - **Record:** commissioned and victory dates, difficulty, final score, sectors visited, beacons, ships defeated,
    scrap collected, crew at the end, the museum's price or her victories. Victors also get **Honours** (gold stars:
    FTL achievements earned during her command, the flagship driven off, a free commission) and **Museum records**
    (what she holds among the honoured ships: highest score, most ships defeated, fewest beacons to victory...).
  - **Crew:** at the final engagement (portrait, name, species, six skill meters, FTL's counts: repairs, kills,
    evasions, jumps, masteries), and **Lost on the way** (from her voyage log). The crew is as the kept copy has
    them: as she turned for the final engagement (FTL saves nothing during the last fight).
  - **Voyage:** her voyage log.
  - **Loadout:** weapons, drones, augments, systems and reactor, with the Cargo Bay's item icons.
- **Extras:** an **epitaph**, one line the player writes on her plate; and **Save as picture** (the exhibit as a PNG).
- **To record from now on** (ships from before have less): the profile's achievements when a ship is set out and at
  victory (the difference is her honours), and the victory's date and Top Scores entry.

## 12. Reply chains: the One Point of Hull — built (4B.38; harness test ChainT)

From IDEAS.md (Ideas A and C). Letters can carry a **Reply** button: the player picks an answer, and the next letter
comes a random number of beacons later (the fleet counts every beacon its ships jump to). The machinery is all in
`transmissions.txt` (`replies:`, `then:`, `cost:`, `action:`), so later chains (Ancestry first) need only letters.

- **The trigger:** once per fleet, the first time the boarded ship comes out of a battle with one point of hull (no
  hostile ship alongside). The Engi Restoration Collective writes, naming her.
- **Reply "Tell me about these derelict vessels.":** 5-10 beacons, then *A vessel in need*; 5-7 more, then
  *Delivered*, which puts **Patience** in the Junkyard: a Slug Cruiser A on the station's blank copy, no crew, medbay
  torn out, a broken hacking bay, broken oxygen, piloting 2 and engines 1, a damaged weapons system with only a Mini
  Beam, hull 6 of 30, breaches in the airlock and weapons room, thin air, no scrap. Salvage
  brings her to the Space Dock, set out at The Home Planet Station, so she may take on crew.
- **Reply "I could definitely use a repair tool.":** 3-5 beacons, then a Repair Arm, claimed for 25 scrap from the
  Cargo Hold (refused, changing nothing, when the hold is short).
- **No one aboard:** she can be boarded empty (to take on crew in the Cargo Bay), but FTL won't launch with a boarded
  ship that has no crew (FTL would end her journey at once); the station says to move someone aboard first.

## 13. The station's economy — built (4B.58; harness checks in RuleT)

Every fee and sale price is read from one place (`core.Economy`): Sandbox Mode's from Settings, Immersive Mode's
from the career.

- **Trade In** (Junkyard): The Federation Home Planet's shipyard pays half her value, less 5 scrap for each point of
  missing hull. Her value is her commission price at 100% without her crew, plus her fuel, missiles and drone parts at
  store price.
- **Auction** (Junkyard): the best bid is 25% to 75% of her value less 5 scrap a missing hull point, shown before it's
  accepted. The same save always draws the same bid, so declining and asking again doesn't help.
- Both send her scrap and crew to the Cargo Hold with the payment; everything else goes with her. She leaves the
  fleet as **sold** (kept in her history, never recoverable). Neither needs a station: the buyer comes to the Junkyard.
- **Refit removal** (Sandbox setting): taking a system off at Refit is not allowed, free (the default), 25 or 50
  scrap, paid by the boarded ship (given back by Reset, as all Refit changes are).
- **Stripping when scrapping** (allowed or not; replaces "scrapping keeps systems", carried over from the old cfg):
  each storable, undamaged system costs a discount on removal (free for free, 10 for 25, 20 for 50, 10 where removal
  isn't allowed), paid from the Cargo Hold and her own scrap together. The scrap window offers to strip or not.
  Off: her systems are lost with the hull.
- **Selling:** stored systems always sell for half their price and upgrades; missiles and drone parts follow the mode
  (half in Sandbox Mode).
- **New Journey fee** (Sandbox setting): free (the default), 200, 500 or 1000 scrap from the Cargo Hold.
- **Without a ship** (the Liaison's letter): commission a ship if the hold can pay; with a hull in the Junkyard,
  salvage her, or trade in or auction a ship that can't be repaired; Report for Reassignment as the last resort.

## 14. Immersive difficulties — built (4B.59; harness checks in FleetT)

Chosen on the briefing's career page, fixed for the career's life, and shown on the Space Dock heading and in
Settings (the rules it sets are locked there). Custom picks any level of each rule. Kept in the career's
`career.txt` (`difficulty`, and `rules` for Custom); read through `parser.CareerRules`.

| Rule | Easy | Normal | Hard |
|---|---|---|---|
| After a final victory | save her, or the museum at full value | save her, or the museum at half value | the museum takes her, at half value |
| New Journey | 200 | 500 | 1000 |
| Report for Reassignment grants | Kestrel Type A | Variable | relief ship |
| Refit removal | free | 25 | 50 |
| Stripping when scrapping | allowed, free | allowed, 10 a system | not allowed |
| Missiles and drone parts sell for | half | a quarter | 1 scrap each |
| The stipend every | 2 sectors | 3 sectors | 4 sectors |
| Commissioning costs | 75% | 100% | 100% |
| Starting scrap | 50 | 25 | 10 |

The same at every difficulty: stored systems sell at half, hull repairs 4 a point, the stipend's 20 plus rank,
commissions cost scrap, station requirements, locked models, free unlock ships, rank clearances, a final
reassignment, a Kestrel Type A to start.

A career from before difficulties becomes **Custom (from before difficulties)**, written down once with what it had:
its final victory choice (still changeable in Settings), journeys 200, Variable, removal free, stripping as Settings
had it (free), a quarter, every 4 sectors, full price. On Hard the museum letter ("Into the Museum") replaces the
rescue offer.

## 15. Switch Game Mode — built (4B.60; harness checks in FleetT)

Settings' **Switch Game Mode...** opens a window of the five modes: Sandbox Mode, and the Immersive careers Easy,
Normal, Hard and Custom. Each has a fleet folder of its own (`FederationHomePlanet`, `FederationHomePlanet-Immersive-Easy`,
`-Normal`, `-Hard`, and `FederationHomePlanet-Immersive` for Custom, so the first Immersive fleet carries on as Custom),
and each career can keep an FTL profile of its own. The window shows what each holds (in use, not begun, its ships,
its own profile), switches to one (a career not begun is briefed first, its difficulty set by its slot; Custom picks
each rule), and ends a career (the one in use: Sandbox Mode first). Switching from one career to another goes by way
of Sandbox Mode, so profiles are swapped the same tested way. The first-startup choice offers the same: Sandbox
Mode, or Easy, Normal, Hard or Custom. The Space Dock heading and Settings name the mode in use; blueprints flown by
any other fleet stay protected. The cfg remembers the career last used (`immersive_slot`).

## Not doing

- **Thruster glow for designs.** FTL lights the engines of some of its own ships (the Kestrel's, when piloted and
  ready to jump) from positions written into the game itself; nothing in a blueprint, layout or art file sets them, and
  only the Hyperspace mod lets custom ships have them. Designs need just their one PNG; glowing engines can be painted
  into the art. Settled with heromedel: not worth pursuing.

## Naming decisions — settled (4B.30)

- The storage is **the Cargo Hold** (in full, The Federation Home Planet Station's Cargo Hold; also the Station's Cargo
  Hold). The storage save keeps its old internal name, "Spacedock Storage", so old saves still load. Transmissions may
  vary the wording for flavour.
- **Decommission** (not Disband): the boarded ship goes to the Junkyard. The same word as for an uncommissioned ship in
  Immersive Mode (send her to the normal Junkyard, or destroy her).
- Buttons say **Patch** ("Patch mods...", "Patch Now"): players know what patching is. Sentences describe it as the
  patch being **sent to FTL via Slipstream** ("The Home Planet Station is sending the patch to FTL via Slipstream").
- The Dry Dock and the Refit → Retrofit → Remodel → Overhaul steps keep their names.
- README: the owner's part (above the last dashed line) is kept as written; the part below is updated with the features.
