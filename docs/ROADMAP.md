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
16. Derelicts for sale in the Junkyard, and Dry Dock system and breach repairs (4B.61)
17. Settings in tabs, and the Station Log window (4B.61)

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
  missing hull, 5 for each broken system bar and 5 for each breach. Her value is her commission price at 100% without her crew, plus her fuel, missiles and drone parts at
  store price.
- **Auction** (Junkyard): explained first (Cancel highlighted); Hold Auction sells her at once for the highest bid, 25% to 75% of her value less her damage (as Trade In), and the result shows with only Accept Bid.
  The same save always draws the same bid.
- **Missing core systems** (Engines, Piloting or Oxygen not installed, whatever her design): each takes 15 points off what Trade In
  and Auction pay (Trade In 50/35/20/5%; bids 25-75% with both ends 15 points lower each, never under 5%). A
  derelict's price ignores them, so a cheap one flips only if she's whole, and restoring one pays.
- Both send her scrap and crew to the Cargo Hold with the payment; everything else goes with her. She leaves the
  fleet as **sold** (kept in her history, never recoverable). Both follow the station rule, as Scrap does: a beacon with a store, or not yet gone from The Home Planet Station (a bought derelict that hasn't jumped).
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

## 16. Derelicts in the Junkyard — built (4B.61; harness test DerT)

The Junkyard window's **Derelicts...** (also offered when none of your ships is there) shows three hulls for sale,
kept in the fleet's `derelicts/` folder. New ones come in after 15-45 beacons the fleet travels (5 times 3 to 9, rolled each time; counted when you look:
no letters, you come and check).

- Each is on the station's blank copy of her model: no crew, hull 15-50%, a system or two missing, at odd levels or
  added where she has a room for it, many broken bars, 1-3 breaches, thin air, the reactor down a little, fuel 0-3,
  no scrap or cargo. Each of her own weapons and drones survives 1 time in 12, and 1 time in 12 there's another
  (a store weapon or drone she didn't come with) if a slot is free; an augment 1 in 8. With a missile weapon, half the
  time 1-3 missiles (+1 a further launcher); with drones, half the time 1-3 drone parts (+1 a further drone); with a
  hacking system, half the time 1 more part.
- Her model is one the FTL profile has unlocked; one listing in 30 is a locked model (about once in 10 rerolls).
- One in 15 is rebuilt strangely: two unmanned systems' rooms swapped, or an inner door welded shut (every room still
  reachable). Only when she's bought does this become a remodel of her own; the patch prompt follows.
- Price: 25-75% of her value as she is (less 5 a missing hull point and 5 a broken bar), from the Cargo Hold. She goes to the Junkyard,
  set out at The Home Planet Station so she can take on crew once salvaged.
- The Dry Dock bills the Cargo Hold (4B.64): every price on the Refit tab (upgrades, reactor bars, hull, Fix, Seal,
  removal fees) goes on a running bill, checked against the Cargo Hold's scrap and paid in the same save as the ship;
  Reset drops it. Selling a stored system pays the Cargo Hold.
- The Dry Dock now mends broken system bars (5 scrap a bar, shown as Fix instead of the upgrade) and seals breaches
  (5 each), beside hull repairs.
- Tidying (4B.67): Immersive Mode no longer writes its rules over the player's own Sandbox settings; each rule asks
  the mode (Immersive always on, Sandbox by its setting), so leaving a career finds Sandbox as it was. New Journey,
  Sell and Scrap use the same store check as the Cargo Bay (a ship still at The Home Planet Station's beacon may).
  Fuel, missile and drone part prices live in one place. A derelict's listing keeps her share (25-75%), so her price
  follows any change to the prices. The Refit bill, the auction and the Junkyard window are tested in the harness
  (GuiT, on a virtual display).
- The repair job (4B.69): after the fleet makes three unflyable ships fly again (no working Engines or Piloting, then
  both), a collector in the Civilian Sector offers 200-500 scrap over the repair cost to restore her Stealth,
  the Nightjar (on the blank copy: a teleporter, and FTL's Zoltan shield augment). Accepted, she's delivered to the
  Junkyard, marked as borrowed (history/<id>/borrowed.txt, beside TradeMark). Whole again (full hull, no breaches,
  flyable), The Home Planet Station writes that she's ready, with two replies: Send her home (at once, from the Space
  Dock or aboard her with FTL closed and at a station; refused with the reason otherwise, and the letter stays
  answerable) or Not yet. Aboard her, the Cargo Bay shows Return (her name) beside her: it returns the borrowed ship
  you're aboard, as decommissioning does (FTL closed), so a ship on loan over Long Range Comm. could use it later
  (IDEAS.md, Idea D). Either way she goes home for the cost (assessed at delivery at Dry Dock prices) plus the bonus. Not returned 200 beacons after she came, for any reason,
  her owner demands her: sent back then, she pays the cost only if she's whole; refused, 7-14 beacons later the
  Federation Office of Salvage and Claims takes her value from the Cargo Hold's scrap, else a docked ship, else
  everything in the Cargo Hold but its crew, and her too if docked (boarded: when she next docks). Hidden in the
  Junkyard she isn't found; salvaged later, the Junkyard Foreman writes. New fates: RETURNED, SEIZED. Without
  Slipstream the whole job still works at The Home Planet Station; only flying her in FTL needs the patch (a note
  says so when the offer is taken). To test: whether FTL takes the Zoltan shield as a fourth augment beyond the
  three slots; if it does, the shield could stop counting as a slot.


- **Thruster glow for designs.** FTL lights the engines of some of its own ships (the Kestrel's, when piloted and
  ready to jump) from positions written into the game itself; nothing in a blueprint, layout or art file sets them, and
  only the Hyperspace mod lets custom ships have them. Designs need just their one PNG; glowing engines can be painted
  into the art. Settled with heromedel: not worth pursuing.

## 17. Check for Updates — built (4B.69; harness test UpdT)

- **Settings, About: Check for Updates...** reads main's `pom.xml` on GitHub and compares versions (4B.9 < 4B.10 <
  4C.1). Only when asked: nothing contacts GitHub on its own. Up to date, unreachable (with the address), or newer.
- **Newer:** a copy not built by the Construction Yard (no source beside `Current Build\`) is pointed to GitHub; a git
  checkout (a `.git` folder) is told to fetch in GitHub Desktop. Otherwise **Update Now** downloads main's zip,
  checks it's whole (one top folder, the pom, the Construction Yard, the program's main class; nothing outside the
  folder), and puts its files in place: only files the repository has, never `tools\`, `target\`, `.git`, logs, a
  `.cfg`, `.jar` or `.ico`. Files the last update installed that main no longer has are removed
  (`update-manifest.txt`). Every file replaced or removed is kept in `update-backup\`, with the new ones listed; a
  failure partway puts everything back at once.
- **Then** the station closes (asking first, as closing does) and starts the Construction Yard with `update`: it
  builds without the menu (waiting up to half a minute for the old jar to be let go) and opens the station. If the
  build fails, the old files go back (`:restore`) and the old jar is still in `Current Build\`.
- **The Construction Yard** now offers 0: Exit / 1: Launch the Station Interface after a successful build.
- In an update the Construction Yard says "Reconstructing Station..." and "Station Reconstructed" (4B.74).
- A first construction (no JDK or Maven in `tools\` yet) opens with a box saying it takes a while, and that later
  constructions are much faster (4B.77).
- When Maven can't check a website's certificate (`PKIX`, `CertPath`, `bad_certificate` in its messages, kept in
  `tools\last-build.log`), the Construction Yard explains the two usual causes: the computer's date or time is wrong,
  or an antivirus scans HTTPS connections (4B.74).
- Tested on Windows by hand: the rebuild and relaunch (UpdT covers the versions, the file replacement, bad downloads
  and the put-back).

## 18. The welcome screen shows what's there — built (4B.74; checks in GuiT)

- The first-startup welcome lists the four careers as Switch Game Mode does (`ui.ModeRows`, shared by both): each
  one's name, its line and its state (Not begun / Begun: N ships, its own FTL profile), with **Begin...** or
  **Continue...**; the Sandbox card shows the Sandbox fleet's ships, and a line says when fleets were found. The first
  Immersive career (from before difficulties) stays in the Custom slot, described as such on both screens.
- A begun career's briefing says how to begin it afresh: end it in Settings > Switch Game Mode (a copy is kept).

## 19. Her particulars, and a Stats tab — built (4B.75; harness test StatT)

- The Space Dock: when her particulars don't fit beside her picture, she moves right a little to make room (they
  stand beside the picture itself, which is centred in her berth); hidden only if the window is too narrow for both.
- The ship's report (Info, or her picture) has two tabs, **Report** and **Stats**. Stats, under her picture:
  **This Journey** (sector, difficulty; beacons, ships defeated, scrap, crew hired, enemy crew killed, crew lost in
  red, shots and missiles fired, counted from where the journey began), **Her Service** (commissioned, first
  commissioned by for a traded ship, journeys, sectors visited, furthest sector, final victories in green, and FTL's
  totals, which run on across journeys), and **Her Crew** (the standouts: Best Pilot, Best Gunner, Best Engineer,
  Longest Serving, Most Skilled, with what earned it). Nothing the records lack is shown as 0.
- Where each journey began is kept in history/<id>/journey.txt (`vault.JourneyStart`, written when the station sets
  her out), with the furthest sector of her earlier journeys; a ship from before 4B.75 counts her journey from her
  next New Journey, and says so.
- Switching game mode closes every window of the station's (Switch Game Mode, Settings, any report) and shows the
  Space Dock with the new mode's fleet, so nothing of the old mode is left on screen; Settings' unsaved choices for
  the old mode are dropped with it (4B.75).

## 20. The Cargo Hold with no ship aboard — built (4B.76; checks in GuiT)

- With no ship boarded, the Cargo Bay opens on the Trade tab with the Cargo Hold as its partner: its items and
  supplies can be sold or junked (paying the hold), and **Stored Systems** sells the systems kept there; Save writes
  the hold (and the stored-systems list) alone. The left side says "No ship aboard: board one at the Space Dock";
  Send and Take, the Shop and the Refit tab wait for a ship. The stranded letters say so.
- A derelict rebuilt with two systems' rooms swapped now says "the rooms built for her X and Y have been swapped",
  true whether or not those systems are installed.

## 21. The Junkyard update — built (4B.81; harness test PartT, checks in GuiT and TransT)

- The Space Dock's Salvage button is now **Junkyard**.
- Damaged systems can be stored, installed and uninstalled, keeping their broken bars (`<id> <level> <broken>` in
  storage-systems.txt); stripping when scrapping keeps damaged systems too. A stored system sells for less its damage.
- Ship value: each broken bar takes 5 off, 10 for Piloting, Oxygen and Engines.
- **Parts...** in the Junkyard window (beside Derelicts...): 2 to 5 damaged systems, mostly low levels (no artillery
  or Clone Bay), paid from the Cargo Hold into the stored systems. New ones every 5 to 15 beacons (the fleet's
  parts.txt). Standard prices for now; core parts at about 150 is undecided.
- Parts are priced by how broken they are (4B.82): of its worth less its damage, one bar of five broken sells for
  about 66-86%, half broken about 52-72%, broken through 30-50%. One in 12 is a clearance, 10% off, and says so.
- Piloting, Oxygen and Engines parts are worth 150 at level 1, FTL's upgrade costs on top (4B.84): FTL prices them as
  next to nothing. One set in five also lists a piece of salvage for the Cargo Hold: mostly missiles, fuel or drone
  parts, one time in four a weapon, drone or augment, at 40-70% of FTL's store price.
- The stipend counts beacons: 15 to each sector of the old rule (Easy 30, Normal 45, Hard 60, Sandbox careers 60).
  A career under way carries its progress over (its sectors so far become beacons).
- Work in FTL counts: when the boarded ship's `store_purchase`, `store_repair`, `system_upgrade` or
  `reactor_upgrade` count has gone up with no jump since the station last looked, one beacon is counted, once a
  beacon stop, and noted in her voyage log. Nothing else (crew walking about) counts.

## 22. Expeditions, hiring, folding headings — built (4B.83; harness test ExpT, checks in GuiT)

- **Expeditions** (under Station): a board of three postings, each in one of FTL's sector types (one in ten somewhere
  rare: a homeworld, the Hidden Crystal Worlds); a finished one is replaced at once. Up to 3 crew from the Cargo Hold
  go; two or three events from the sector's pool, each with several ways through, some open only with a race in the
  party (blue where it helps, red where it makes things worse). Pay about 5 to 50 scrap, now and then cheap gear,
  missiles, fuel or drone parts; crew can come back injured (half their health; hurt twice, lost) or not at all,
  recorded in the history log. Each finished expedition counts as one beacon. Events live in
  `resource/expeditions.txt` (its header gives the format); the harness checks every event has two ways through
  without a race and every sector has at least three events.
- **Hiring** on the same screen: with no crew anywhere, "Post a promise of adventure" is free and answered half the
  time; otherwise "Post for volunteers" costs 5 scrap a crew member in the fleet (every ship, the Junkyard's hulls and
  the Cargo Hold), at most 60, spent either way, answered three times in four. The race is one of the unlocked
  ships' crews; new crew wait in the Cargo Hold.
- **Folding headings**: a click on a gold heading of the Space Dock's controls folds its buttons away or back,
  lighter under the mouse, a small arrow when folded; remembered in the cfg (`fold_station`, ...).
- A one-sided Long Range trade's log no longer says "received ():".
- Deeper expeditions (4B.85): about 100 events, six to eight of each sector's own (a homeworld three, besides its
  race's; the Hidden Crystal Worlds seven), split into one file per sector (`expeditions_<sector>.txt`, included from
  `expeditions.txt`), written after reading FTL's own events in ftl.dat: its tone, never its events or words (the
  harness holds every line against FTL's event text and fails on any run of six words the same).
  - Multi-part events: a choice can lead into a further pop-up (`then <step>`), chains of three or four; many pop-ups
    have four or more choices.
  - Every event is graded low, moderate or high risk; a posting draws events of its danger, one in five a level
    riskier. Low risk rarely injures and never kills.
  - Odds: each crew member beyond the first adds 5 to every gamble, each injured one (before or during) takes 3 off;
    in a fight (`[fight]`) each Mantis adds 8, each Rock 4, each Engi takes 5 off.
  - Outcomes vary: scrap, gear, supplies, a new crew member (`join`), a lead into the next step, or nothing.
  - One run never meets a theme twice; events met in about the last eight expeditions aren't met again while others
    are left (`recent` in the fleet's expeditions.txt).
  - Sealed postings, one in eight: "Destination undisclosed", danger "Unknown", the sector rolled when posted (now and
    then a rare one) and told only once the shuttle is under way; they pay half again.
- Ships home and the lost expedition (4B.86):
  - `aside <chance> | <words>` after an outcome: an extra line only that often; about 40 across the events, the
    Mantis convoy raid's "body soldier" among them (one time in ten when the airlocks fail). The Mantis posting reads
    "Need mercenaries for defense through Mantis territory".
  - `ship <sector>:<condition>[:<system>]`: a ship comes home, of a model fitting the sector (Engi, Zoltan, Mantis,
    Rock, Slug, Lanius and Crystal cruisers in their own space, a Kestrel from civilian space, a Federation Cruiser
    from rebel space, a Stealth or Slug cruiser from the nebula, a mix from pirate space), wrecked, towed (most of her
    hull, no breaches) or limping (the named system broken through or gone). Each sector, and the Hidden Crystal
    Worlds, has an event that can bring one home, each a different story. The player sends her to the Space Dock or
    the Junkyard; a docked hull on the station's blank copy says so, with Patch Now.
  - The lost expedition (`event lost_expedition saga`): one in 300 high-danger expeditions of two or more crew goes
    missing at its end (even one that turned for home early). Four beacons of silence, a rumour of a battle with the
    sector's enemy, an alert as a ship decloaks off the station's bow: fire or wait (firing saves her one time in
    four, waiting three in four, whoever is left). The survivor comes home at the helm of a Federation Stealth Cruiser
    on her own blueprint (no companion mod), near new or, if hit, dented (some hull, a bar or two); the others are
    lost. She goes to the Space Dock or the Junkyard as chosen, the survivor aboard.
- Expeditions and the Long Range (4B.87): an expedition's pop-ups (events, outcomes, sealed orders, the ship
  question) can't be closed, only answered. While one is under way a priority message pops up over it (and the
  expedition carries on after), and a hail is told the commander is away on an expedition and listed as missed. The
  lost expedition's choices read "Demand The Station Commander to give the order to fire" and "Wait and watch as
  The Station opens hailing frequencies".
- Higher stakes (4B.88):
  - Injuries on moderate and high-risk events can be fatal (never on low-risk ones), and gambles there are harder
    (the numbers are kept in Expeditions.java, not here or in the game's text: heromedel asked not to see them).
    Someone sent out already injured who is hurt again is lost. Across all expeditions about one in four loses
    someone (it was one in seven).
  - Outfitted jobs, one posting in four: "Outfitted crew wanted: N scrap a head" (5-12), paid from the Cargo Hold
    for each crew member sent; +10 on every gamble and a quarter more scrap from the events. A finished job returns
    50-200% of the outfitting on top of its pay; one not finished (turned back, or no one left) pays nothing and
    returns nothing. Over many outfitted jobs the payback averages about what was spent.
  - The job's own pay comes only for a job finished.
  - One loss in three is a capture: a few beacons later a ransom (25-50 scrap) is asked (the fleet's captives.txt).
    Since 4B.89 it's handled only in the inbox: the captors' letter has Pay and Refuse; it says "You have one month"
    and never counts beacons (the real count, 14 beacons, stays hidden); a reminder ("Time is running short") comes
    three beacons before the end. Refused or run out, the Federation Ambassador (a new voice) writes that the crew
    member is missing, presumed dead. With the inbox off, the ask and the reminder come up at the Space Dock (Pay,
    Refuse, Later) and the Ambassador's word as a notice.
  - Choices reviewed so none is better than another on every count, except FTL-style moral ones (the cold choice
    may pay more, without comment) and where a ship or a new crew member is the reward.

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
