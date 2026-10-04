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
  plus, per achievement counted, 1 (Commander), 2 (Captain), 3 (Commodore): 71 / 122 / 173 with all 51. One message
  for all the months due ("your stipend for the last 2 months"), its scrap issued as a reward: **Claim** puts it in
  Spacedock Storage (4B.96). Until claimed it can't be deleted or archived; after, **Delete**.
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

Her value is her price strictly counted, at the difficulty's rate (section 30). The messages come by Transmissions (the rescue offer waits in the inbox), or as a
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
| Plead for New Ship grants (4B.92) | any ship | Kestrel Type A or the Relief Ship | the Relief Ship Type A |
| Refit removal | free | 25 | 50 |
| Stripping when scrapping | allowed, free | allowed, 10 a system | not allowed |
| Missiles and drone parts sell for | half | a quarter | 1 scrap each |
| The stipend every | 2 sectors | 3 sectors | 4 sectors |
| Commissioning costs | 75% | 100% | 100% |
| Starting scrap | 50 | 25 | 10 |

The same at every difficulty: stored systems sell at half, hull repairs 4 a point, the stipend's 20 plus rank,
commissions cost scrap, station requirements, locked models, free unlock ships, rank clearances, the Relief Ship Type
A on every plea, a Kestrel Type A to start.

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

## 21. Reputation — built (4B.92, from the long-range-comm branch's 4B.86–4B.91; harness test RepuT)

A career's standing with The Federation Home Planet, earned by its ships' service in FTL and lost by their losses.
Every Immersive career has it (Settings' Reputation rule, locked on); in Sandbox Mode it's the player's choice
(the rule, off to begin with), with or without Career messages or the inbox. Shown in gold on the Space Dock, to the right of
the inbox (red below zero), as plain text: "REP: 179"; its tooltip has the latest changes, and clicking it opens the **Career
Reputation Log** (reputation.log in the fleet's folder, in the station log's style).

| Earned | | Lost | |
|---|---|---|---|
| Each new sector | +6 | Each crew member killed | −10 |
| Scrap collected (FTL's total: not sales) | a tenth | Each ship lost in action | −50 |
| Each ship defeated | +4 | Caught by the rebel fleet | −5 |
| A rebel ship defeated (REBEL_ or AUTO_) | +6 | A bad outcome | −1 |
| A good outcome | +2 | | |
| Each FTL achievement (real ones, earned in the fleet's service) | +10 | | |
| The Rebel Flagship defeated | +100 | | |

- **Counted as FTL plays:** at each look the station takes at a save FTL wrote, against the count kept for each ship
  (reputation.txt), so nothing counts twice. The station's own changes (a trade, a New Journey, commissioning, the
  Cargo Bay) move the count without scoring. A death is FTL's lost-crew count going up with the crew member gone: a
  clone that came back, or a dismissal, isn't one.
- **Outcomes:** FTL keeps no record of an event's choice, only its results, so an outcome is read from a jump within
  the sector to a beacon with no fight, no ship and no store (nor a store left behind): only gains (scrap, crew, gear,
  missiles or drone parts) is good; only losses (hull, crew, gear, scrap, missiles or drone parts) is bad; both or
  neither, nothing. The jump's own fuel doesn't count.
- **Caught:** the rebel fleet holds the beacon she's at (arrived at one, or overtaken while waiting), once a beacon.
- **Achievements:** counted as the career's rewards count them (earned since the fleet's record began), when the Space
  Dock takes stock.
- **The last stand:** nothing is ever lost in sector 8 (deaths, a ship lost, being caught, a bad outcome); gains still
  count.
- **The first count:** a career's service so far is reviewed once ("Service record reviewed"), from what the station
  keeps: each ship's FTL totals since she joined (her commissioning, or her trade), each ship lost before the last
  stand, each victory in the Hall of Victors, and the achievements earned in the fleet's service. Older records can't
  tell rebels apart (they count as ships), nor events or being caught (not counted). A
  traded ship's crew losses from before her trade aren't told apart, so her losses aren't counted in the review.
- **Traded ships** count only what they did since their trade (TradeMark), as everything else does.
- With the rule switched off, the total and the log are kept and each ship's count still moves on, so nothing done
  meanwhile scores later.

**Later:** a free crew member from a hiring post (−5; the posts are on another branch), and the rescue ship when no
ship can fly: a Kestrel Type A, with 10% of what the surrendered cargo didn't cover taken from reputation (a ship
requisitioned when one isn't needed would cost her full value).

**Back burner:** spending reputation, and promotions tied to it.

## 22. Plead for New Ship — built (4B.92, from the long-range-comm branch; harness checks in PriceT, TransT, FleetT)

Report for Reassignment is replaced (its sections above are history):

- **Plead for New Ship** (Other... at the Space Dock): whatever is docked, The Federation Home Planet agrees to send a
  ship; nothing is taken yet. Her order waits at Commission (the Shipyard's letter, "Your plea was heard"); **Withdraw
  Plea** cancels it until she's built. One order at a time; only while commissioning costs scrap.
- **What it offers:** Settings' choice in Sandbox Mode (Kestrel Type A, Relief Ship Type A, or Any), the difficulty's in
  a career: Easy any ship, Normal a Kestrel Type A, Hard the Relief Ship. **The Relief Ship Type A is always offered.**
  Variable is gone (read as the Kestrel Type A).
- **Paying, at Commission**, once she's chosen: give up the Cargo Hold (everything but the crew, who stay, at what the
  Cargo Bay would pay: scrap, gear at half, missiles and drone parts and stored systems only where they can be sold;
  the Junkyard is untouched), or, with Reputation on, keep it. With Reputation on, a tenth of what the hold doesn't cover
  of her value comes off reputation (keeping it: a tenth of her whole value). A hold worth more than her asks "Are you
  sure?": give up the extra, or refund the difference to the emptied hold.
- **Relief Ship Type A** ("Hinata" by default): a Kestrel A with one human, a Burst Laser I and an Ion Blast, no missiles,
  drones or augments, every system at its minimum, reactor 6. Listed in Commission at all times; the shipyard and a plea
  price her at a fixed 600; everywhere else she's valued by what's on her.
- **Ship values count fuel, missiles and drone parts** at store price (3, 6, 8) everywhere (commission, rewards, the
  museum, sales): a Kestrel A comes to 997.
- **Later:** with the companion mod, a blueprint of her own so FTL names her class (her id needs its own place in the
  mod's naming of _HP and remodel ids).

## 23. FTL's System Limit — built (4B.93; harness checks in PriceT, GuiT, DerT, DesT)

Found by heromedel in play: FTL's store greys out a system once a ship is full, but The Home Planet Station's shop
fitted one anyway; FTL then runs her, but her system bar slides along and its icons risk overlapping.

- **The limit** (`SaveHelper.SYSTEMS_MAX`): 8 systems, the game's own and the same for every ship; no blueprint in
  ftl.dat sets one (its store's tooltip: "You've reached the System Limit"). Piloting, Sensors, Doors and the Backup
  Battery are subsystems and don't count; Artillery does (it has its place on the system bar); a Medbay and a Clone Bay
  take each other's place. Every player ship has a room for every system and none starts with more than 7, so the limit
  is reached by buying.
- **A custom work order** (heromedel): past the limit, a system is fitted for a flat 100 scrap (`Pricing.WORK_ORDER`),
  after this pop-up, heromedel's text exactly as written (Install or Cancel, Cancel the default):

      This ship is at maximum capacity for systems.
      Home Planet Station can fit it in as a custom work order.
      But it will cost 100 scrap.

      (This would excede the Vanilla FTL system Limit)

  - **The Dry Dock shop:** the store's price and the 100, both from her scrap; greyed only when she can't pay both.
  - **Install from the Cargo Bay:** the 100 on the Dry Dock's bill (the Cargo Hold pays on Save; Reset drops it).
  - **On hover:** "You've reached the System Limit. Home Planet Station can fit it in as a custom work order. But it
    will cost 100 scrap."
  - Nothing comes back when the system comes off, and the work order is never part of a ship's value.
- **Elsewhere:** derelicts get their extra system only while under the limit; Design Ship warns when a design would start
  past it; with commissioning costs on, Commission charges 100 for each system past it, outside the commission rate (a
  plea values her the same way).
- **A Medbay and a Clone Bay** take each other's place (4B.94, heromedel): in Design Ship, ticking one to start unticks
  the other, and a design ticked with both (from before) is commissioned with the Clone Bay alone.
## 24. The Junkyard update — built (4B.81; harness test PartT, checks in GuiT and TransT)

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
- The stipend is claimed (4B.96): its letter carries the scrap as a reward, and **Claim** moves it to the Cargo Hold.
  An unclaimed stipend stays in the inbox: no Delete, no Archive. Stipends paid in before 4B.96 delete as before.
- Work in FTL counts: when the boarded ship's `store_purchase`, `store_repair`, `system_upgrade` or
  `reactor_upgrade` count has gone up with no jump since the station last looked, one beacon is counted, once a
  beacon stop, and noted in her voyage log. Nothing else (crew walking about) counts.

## 25. Expeditions, hiring, folding headings — rebuilt (4B.92; harness test ExpT, checks in GuiT)

Expeditions were built at 4B.83 and grown to 4B.91 (sectors, risk grades, chains of events, outfitting, ships home,
the lost Stealth Cruiser). heromedel found the result incoherent, un-FTL and over-paid, and asked for a rebuild from
the ground up in FTL's own style. 4B.92 is that rebuild; nothing of the old events survives.

- **The idea**: the commander signs on to a posted job with up to 3 crew from the Cargo Hold, and the job plays as FTL
  plays a beacon: a situation at one place, numbered choices (blue where a crew member's race opens one), the outcome,
  "1. Continue...". One posting is one event. Crews without a ship can earn a little toward a Junkyard derelict,
  then a little more toward its parts and repairs: several expeditions for one system is the intended pace.
- **Postings**: three on the board, each a kind of job (Rescue, Escort, Salvage, Survey, Delivery, Repair,
  Security), its words a job advert, no sector, no danger grade, no pay named. An untaken posting comes down after 1 to
  7 beacons (hidden); a finished one is replaced at once, never by the same job.
- **Events** (`resource/expeditions.txt`, the header gives the format): one place and one situation, in FTL's tone
  (second person, short, dry); a choice is one sure outcome or a hidden roll between several, by weight; an outcome
  may lead on to one more choice of the same moment (`then <step>`), never a new scene. Rewards come only at the end
  of a branch and on FTL's first-sector scale: 5 to 15 scrap, a dangerous choice up to 20, fuel, missiles or drone
  parts in twos, a weapon, drone or augment rarely; many branches pay nothing. The game adds "You receive 9 scrap."
  after the words, as FTL does, and nothing about odds or arithmetic: the screens show nothing the crew wouldn't know.
- **Danger**: the only thing at risk is crew, so a risky choice can kill. Every choice that can hurt someone kills at
  least as often (the harness checks the weights); a few take them instead (`taken`: a ransom follows, signed by the
  event's `foe`). The commander always comes home. Who is along changes the odds, unseen (4B.94): a choice says what it
  takes, a race that's good at it (fight Mantis, tech Engi, heat Rock, power Zoltan, airless Lanius, mind Slug) and a
  skill (pilot, engines, shields, weapons, repair, combat), and the crew member best suited takes it on. Its risk
  falls on them first, its experience is theirs, and its bad outcomes are rarer for them: half for the race, a fifth
  or two fifths less for the skill at level 1 or 2. A race's own option is taken by the best of that race, and the
  button names them. A choice nothing picks for falls to anyone. A bigger party is not a safer one.
- **The infirmary** (`infirmary.txt` in the vault): a crew member hurt on an expedition stays in the Cargo Hold's save
  but can't be sent again for 3 to 6 beacons (hidden; the Expeditions screen lists who is laid up). When their time is
  up, the next visit to the Space Dock says so in a pop-up (never a letter), and they are whole again.
- **The station's care, skill and the clone bay** (4B.93, Plan GG):
  - The Cargo Bay's crew rows carry a thin bar under the crew icon, as FTL draws health under a portrait (4B.94; it
    sat under the name at 4B.93 and read as an underline): none when whole, green with the rest red for a hurt from
    the game, purple (FTL's "not yours to command") and full for the infirmary; the tooltip says which. A crew member
    in the infirmary can't be moved onto a ship, offered over the Long Range, or renamed (the infirmary knows them by
    name) until they're out; one who leaves the Cargo Hold anyway (retired) is let go quietly when their time is up.
  - A crew member in the Cargo Hold hurt in the game is healed once a beacon has passed (a station heals fast), at
    no cost. The infirmary costs a point of skill for each beacon laid up, taken from a random skill they have points
    in; a point that crosses a level's line takes the level with it (rusty after a long lay-up). Not whole levels:
    heromedel judged that too harsh with a hurt one trip in three.
  - `clone 1`: an outcome where the crew member dies and the hiring ship's clone bay brings them back, a level down in
    every skill they held (FTL's clone bay price), the run's experience gone with it; used where the hiring ship could
    have one (the pilot's freighter, the liner). It counts as a hurt: its weight comes out of the hurt's share, never
    the death's (4B.94; at 4B.93 it had come out of the death's, making the clone bay a reprieve).
  - `xp <skill> <points>`: experience for whoever took the choice on, a few points on the choices that exercise the
    skill, more on the risky ones; FTL's levels come when the points add up (`homeplanet.model.Skills` keeps points and
    mastery flags together). A choice anyone can take must take the skill it teaches, so the right person earns it
    (the harness refuses one that doesn't). Specialist postings are still to come (IDEAS, Idea E).
  - The station's daily round writes the Cargo Hold's save only when someone's health or skill changed.
  - The station's medbay (4B.95, Plan LL): crew hurt in the game, in the Cargo Hold or aboard a docked ship (never the
    boarded one, which may be in FTL), are healed a full beacon after the station first sees them hurt there; a move to
    another place starts the beacon again. Before, the station healed anyone hurt whenever the clock had moved since its
    last look, which could be at once. Each heal writes heromedel's line in the history log: "<name>'s visited The
    Station's Medbay". Each crew member a Cargo Bay save brings aboard a ship or into the Cargo Hold writes "<name>
    assigned to the Cargo Hold." or "<name> assigned to the <ship>." The tooltip on a hurt crew member says they'll
    heal "after some time here" (on the boarded ship: once she's docked), never in beacons (CLAUDE.md's second hard
    rule).
- **The events, reviewed against FTL** (4B.94, no new events): each read beside FTL's own for tone, lore and copied
  ideas. Escort jobs said "your ship" though the commander has none: their postings now lend a cutter. A Rock in the
  hot vent was "burned to the bone" (the Rock don't burn): the vent now collapses. The Mantis raider's Mantis option
  (two Mantis talking behind a locked door, one leaving) was too close to FTL's captured-commando scene: the Mantis now
  offers to be what the starving Mantis goes for. The Engi option there, which only led to the same as leaving the bay
  sealed, now vents the bay. Experience moved to the person doing the work (no more piloting for a Slug's telepathy or
  shields for walking on ice), a Slug can find the moon's pilot by mind, the freighter's clone bay also reaches the
  Mantis's raid and the search on the ground, and the sleeping station's do-nothing choice is gone. Ships are "she"
  throughout.
- Each expedition counts as one beacon of the fleet's time; events met in the last twelve aren't met again while others
  of the kind are left (`recent` in the fleet's expeditions.txt). The history log keeps each job's event, scrap, the
  dead and the laid up.
- Gone with the rebuild: sectors and risk grades, chains of several events, sealed and outfitted postings, the job's
  own pay, asides, ships home from expeditions and the lost Stealth Cruiser expedition (to be rewritten later as its own
  special event, once the ordinary ones feel right).
- **Ransoms** (4B.88, kept): a crew member taken is asked for a few beacons later (the fleet's captives.txt), handled in
  the inbox: the captors' letter has Pay and Refuse, says "You have one month" and never counts beacons (28 since
  5.00, hidden); a reminder comes six beacons before the end. Refused or run out, the Federation Ambassador writes that they are
  missing, presumed dead. With the inbox off, the ask and reminder come up at the Space Dock (Pay, Refuse, Later).
- **Hiring** on the same screen: with no crew anywhere, "Post a promise of adventure" is free and answered half the
  time; otherwise "Post for volunteers" costs 5 scrap a crew member in the fleet (every ship, the Junkyard's hulls and
  the Cargo Hold), at most 60, spent either way, answered three times in four. The race is one of the unlocked
  ships' crews; new crew wait in the Cargo Hold. With no FTL profile yet (a fresh Immersive one), only the Kestrel's
  humans answer, as Commission has it (4B.90).
- While an expedition is under way its pop-ups can't be closed, only answered; a priority Long Range message pops up
  over it and the expedition carries on after; a hail is told the commander is away and listed as missed (4B.87).
- The job's windows (4B.95, redrawn before the merge from heromedel's FTL screenshots): FTL's event layout on the station's
  own dark panel and pale rim (not FTL's mauve), in FTL's own type (JustinFont from ftl.dat, chosen over the style
  guide's Sans Serif from a side-by-side; where ftl.dat's font can't be read, or lacks a letter on the screen, the
  whole screen falls back to the style guide's Sans Serif 12), the words wrapped with room between the lines, and
  two lines under them the numbered choices as plain lines of words (gold under the pointer, blue for a race's
  option), picked by a click or by their number key. The box is as tall as what's in it. The board steps aside when the commander signs on and
  opens again, fresh, when the job is over. The last outcome is the end of the job: no docking screen after it (it only
  repeated the outcome); anyone hurt gets one line under that outcome ("Marek is carried to the infirmary when the
  shuttle docks."). The asteroid belt's line is heromedel's: "you can hear a small asteroid glancing off the shuttle's
  shields" (no sound carried through the hull from outside). The twelve events read again for sound, smell or wind
  where there's no air: none else.
- 4B.95: the event box loses its rim (the words sit straight on the dark panel). Picking who goes: three seats side by
  side, each a drop-down of the Cargo Hold's crew (or no one), and under each a card of the one picked: portrait, name,
  race, a health bar if hurt, and the six skills (a pip a level, green then gold as FTL marks them, and a thin bar toward
  the next). Picking someone for one seat takes them out of another; the first three are picked to begin with.
  The crew report (Plan KK) is drawn the same way wherever it opens (the Cargo Bay, with Rename; a Long Range offer;
  a click on a name in the ship report, from the Space Dock or the Cargo Bay): portrait, name, race and sex, health or
  the infirmary, the six skills with pips, a bar and the points, and the service record. Health bars also show on the
  ship report's crew and the Long Range trade lists; not the Museum (finished runs) or the Dry Dock's crew for hire.
- In the Cargo Bay's system rows, broken bars are drawn red at the end of the level bar (4B.91).
- **Folding headings**: a click on a gold heading of the Space Dock's controls folds its buttons away or back,
  lighter under the mouse, a small arrow when folded; remembered in the cfg (`fold_station`, ...).
- 4B.95: main merged in (Reputation, Plead for New Ship, the Relief Ship Type A, the System Limit, Medbay or Clone Bay).
  The Space Dock is main's (the centred Docked and Aboard headings, REP to the inbox's right), with Expeditions under
  Station and the control headings folding as before; the centred headings don't fold. The stipend reads in months
  everywhere (one, two or three since 5.00), never sectors or beacons.
- A one-sided Long Range trade's log no longer says "received ():".
- The harness (ExpT) reads the events file clean, plays every event through every choice, checks every hurt has a
  death beside it, holds every line against FTL's own event text (no run of six words the same), and walks the
  infirmary and the ransoms through their clocks.
- (McCarthy's branch numbered its last steps 4B.96 to 4B.98; they reached main together as 4B.95.)

## 26. Every beacon counts, and the review's fixes — built (4B.97; harness checks in PartT, ExpT, PriceT, TransT)

From a review of 4B.95 and 4B.96 in main, and heromedel's answers (Plan U).

- **The fleet's clock** (`clock.txt` in the fleet's folder): every beacon and sector the boarded ship flies counts,
  once. Before, the station counted only when the Space Dock was rebuilt, so a voyage flown with the station open and
  then docked (or written by the Cargo Bay) was never counted, and the Junkyard's Parts, the stipend, ransoms and the
  infirmary barely moved. Now FTL's own saves move it as she flies, and a dock or any station write counts her
  progress first. Boarding counts nothing (her past is her own; a traded ship counts from her trade). FTL's New Game
  counts the new ship's run so far. A fleet from before carries on from her last marks.
- Work at a beacon is remembered for each ship, so switching ships at a stop doesn't count it again; the voyage log
  says "Time spent on work at the beacon", never that it counted as a beacon.
- Parts prices stay as they are (heromedel: occasional flips are fine). An expedition stays one beacon: one event,
  one passage of time.
- Plead for New Ship values a damaged stored system as the Cargo Bay would sell it, its broken bars off.
- The stipend's letter is saved before its months are marked paid (taken back if the save fails): a failed save can't
  lose it. The station log says one stipend or two, as the letter's months do. (One stipend a pay period; the letter's
  months are how long the period was: two, three or four by difficulty.)
- **Postings**: each is written for one event (`posting <kind>:<event>` in expeditions.txt) and plays it; the
  harness refuses an event without its posting. A new posting avoids events met lately.
- **Signing on** takes the job off the board at once (a new posting in its place, the event remembered), so closing
  the station mid-job can't play it again.
- **The clone bay** takes a level from the skills held before the job, and the job's experience goes with the body.
- **Ransoms**: the month runs from the letter, however late the station sees it; the captive's whole record (skills,
  mastery, service record, looks) is kept and comes back, whole, when the ransom is paid; payment and its mark are one
  write, so it can't be paid twice. The expedition's end writes the Cargo Hold, the infirmary and the captives
  together; a side file that can't be read is an error, never written back empty.

## 27. Bug squashing — in progress (4B.98)

Fixes only, from heromedel's testing and the review; no new features. Design debts found on the way go to
`docs/CONCERNS.md`, not here.

- Two crew of one name and race no longer get mixed up in the infirmary or at an expedition's end: a mark (sex,
  colouring, service record) kept beside the name tells them apart. A band-aid until crew who are away leave the hold's
  save (CONCERNS.md, 2).
- A ransom's two steps (the payment or refusal, then the note on the letter) report separately: "Nothing was changed"
  only when the ransom itself failed; a note that couldn't be saved after a payment says the payment stands.
- Jobs taken one after another without closing the board: the station's round now runs after each job (whoever's time
  is up leaves the infirmary, with the pop-up; a ransom asked or run out), not only at a look at the Space Dock. Before,
  a laid-up crew member stayed laid up, and the pop-up waited, until the board was closed.
- The Space Dock keeps itself current (Plan V): every file the station writes passes one place (`SafeFiles`), which
  tells the Space Dock; a write in the fleet's folder (a letter read, a job finished, a parcel landed over the Long
  Range, a ransom settled) rebuilds it a moment later, behind whatever window is open, so the inbox's count and the rest
  are live without the window being closed. Several writes in a row make one rebuild; its own rebuild's writes are
  ignored; when another screen is showing, the return rebuilds it as before. Every return from a window (Expeditions,
  Derelicts, Ship Records) rebuilds it, whatever the window reports. Pop-ups a rebuild can raise (New Game noticed, a
  final victory, a ransom, the infirmary) now come while another window is open, rather than waiting for it to close.
- The ship report's "Content: Advanced Edition / Original" line is gone (heromedel; Commission has its own switch).
- Locked ships: the descriptions wrap at a width the screen can show (the picture, the words, the scrollbar and the
  frame within the usable screen), the window no wider than that, and no taller than the screen less a margin.
- The "FTL is running" question (boarding, docking, a save to the boarded ship...) no longer reads as a warning that
  something is wrong: it says why the station would rather not, that FTL at its main menu is safe, and its first
  answer is heromedel's "Nevermind, save her in the Space Dock" for boarding, "Nevermind" elsewhere; the other is
  "Go ahead, FTL is at its menu".
- Commission orders for a Type B or C: heromedel's shared letter (the ship's people have contacted Federation
  Command, impressed, and shared another model's blueprints; `{race}` and `{cruiser}` filled from the ship) in place of
  the ship's own, which told her Type A's unlock story (the Zoltan Council's offer for a Zoltan B, say). The
  Kestrel's and the Federation Cruiser's letters read right for any type and stay. To be given more of each race's
  character later.
- Custom designs in FTL (Plan W, tested in FTL itself): the station's own ships draw where the editor shows them (hull
  and rooms together; the editor's cyan crosshair marks the rooms' centre, and Center art centres the picture's visible
  part, not its canvas). A re-finalized remodel keeps the layout the ship was finalized against. The Loadout's top line
  says what it is: her place on FTL's screen.
- One answer to "is she in FTL's data?" (`PatchState`): her blueprint, layout, chassis and pictures compared with
  `ftl.dat` as patched, read fresh after a patch, in place of a "patched this session" flag and a rooms-and-doors
  compare that disagreed with each other. Launch FTL always checks the boarded ship's blueprints; Commission lists a
  remodel or design that isn't in FTL yet with " - not in FTL yet" and asks before commissioning her; a bought derelict
  with a layout of her own goes into the mod at once.
- The floor is a choice of three (heromedel): no floor (FTL tiles the rooms plain), drawn from her rooms (grey walls
  round each room, open at the doors, the way the game's ships look; drawn again whenever the rooms, the doors or the
  art move, so it can't go stale, and sent to FTL with her pictures), or a picture of her own, for decorated floors. A
  new hull picture drops a floor picture made for the old one, never a drawn floor. A floor that sticks out of the
  hull is a warning, not a refusal (the game's own floors are smaller than their hulls). Missing hull art is a warning,
  the Kestrel's standing in. An older station reading `floor="rooms"` over the Long Range shows her with no floor.

## 28. The design screen as three steps — built (4B.99; harness checks in DesT and GuiT)

heromedel's ask: a design screen that's more intuitive, looks nicer, and explains the Loadout (whose top line, her place
on FTL's screen, nobody could use). The mockup it was built to: the ship stays on the left the whole time; the work on
the right is three steps in build order.

- **The steps.** The right column is a tabbed panel: 1. Rooms, 2. Art, 3. Loadout. Remodel keeps Rooms, and Art once
  the overhaul is on, unnumbered. The separate Loadout window is gone: its contents are the Loadout step, and Build
  blueprint opens the build screen directly (its Loadout... comes back here with the step in front). Hull, Reactor and
  Drone slots left the title bar for the Loadout step.
- **Rooms.** Place and Move, then Systems (the old "Not on this ship", with a line saying what to do with it), Doors,
  Whole ship, the window's own buttons, and the zoom last. An empty grid says what to do first ("Place her first room:
  Place 2 x 2, then click the grid"); a ship with rooms but no art says to import or pick one. The checks line gains a
  "Next:" hint when nothing needs fixing, and wraps rather than running off the window.
- **Art, and the grid's middle as the game's centre** (heromedel: the one place a person expects it). Measured over
  all 28 of the game's player ships, the middle of the room block sits at the same spot in FTL's frame, 8 squares
  across and 5 down (`DesignExport.SHIP_X/SHIP_Y`); the design grid's middle stands for that point, marked by the cyan
  cross "where FTL puts her", which never moves. Where the rooms sit round it is her screen offset (`offsets(d)`, read
  off the rooms; the stored offsets and the art-based guess are gone), so rooms left of the middle sit left in the game,
  and a game ship copied in lands where the game has her and gets the game's own offsets back. FTL has no further left
  or up than offset 0, so the strip past it is shaded faintly. The canvas is fixed (the grid with a wide border for art
  hanging over): editing one thing never slides another. "Center on the anchor" puts the picture's visible middle on
  the cross and moves nothing else. The shield ellipse and gibs sit under "Fine adjustment".
- **Loadout, one row shape for every number** (heromedel's sketch): hers/max, the name, minus, a typed field, plus, a
  bar of green segments to the vanilla max and amber ones past it, "Over vanilla max" beside. Hull, reactor, weapon
  slots, drone slots, missiles and drone parts under "Her numbers"; each placed system under "Systems at the start" with
  the installed tick in front (a Medbay and a Clone Bay still take each other's place; the artillery's weapon button
  stays on its row). Nothing is capped. The crew stays a grid of race counts with "of 8" fixed, the one ceiling FTL
  itself sets (no blueprint or save can move it). Then what she carries, in as many boxes as she has slots.
- **Weapon slots are hers to set** (`ShipDesign.weaponSlots`, saved with the design; a design from before counts her
  mounts as it did). The export writes it; the one-to-four clamp is gone. Two notes, never refusals: more slots than
  mounts ("a weapon in a slot past her mounts has nowhere to draw"), and anything past vanilla ("FTL's weapon bar is
  drawn for 4, the rest sit off its edge"; a system past its top level: the upgrade screen won't show it). More than 8
  crew is a problem; no crew set is a note.
- **Vanilla max read from the game** (`VanillaMax`): the highest any of the game's player ships has of each number, and
  each system's top level from its blueprint, so the column is literally what it says and a modded game shows its own.
- Later: the Retrofit tab taking the same row shape, so the two screens match.

## 29. The Cargo Bay without boarding, art turned, and two rules — built (5.00; harness checks in GuiT, DesT, ExpT)

From heromedel's notes after 4B.99. The version after 4B.99 is 5.00 (the numbering's rule).

- **Aboard means the launch pad only.** Boarding stays a Space Dock act and decides which ship FTL loads. The Cargo
  Bay works on whichever ship is picked on it: it opens on the boarded ship (the Cargo Hold alone with none aboard, as
  before), as the Long Range does, and from then on everything on the screen, the Trade, the Shop and the Refit, follows
  the pick and nothing follows the boarded ship. Picking switches at once: no report, no Board question, nobody boarded
  or docked. The pick is made afresh each time the screen opens.
- **Art: Rotate and Flip left / right**, on the Art step beside the hull art: for a picture drawn facing the wrong way.
  Her pictures turn (the hull, a floor picture, her gib pictures, as copies of her own); the mounts and the shield
  ellipse turn with the picture so they keep their spots on it; the rooms stay, and the picture's middle stays where it
  is. A game ship's own gibs no longer fit a turned hull, so she's cut from the hull art instead. Size stays as it was.
- **A promise of adventure costs reputation.** With no crew anywhere, posting cost nothing; now it costs 15 reputation
  (with Reputation on; free as before with it off), spent whether or not anyone answers, in the Reputation log. The
  scrap ladder for paid postings (5 a crew member, at most 60) is unchanged.
- **The Relief Ship is free only when the rules say so.** With an empty shipyard she was marked free whatever the
  difficulty or plea, so a new Hard fleet was offered her and the Kestrel both free. Now a new fleet's free ship is the
  Kestrel; the Relief Ship is free when the free command names her (a plea under Hard, or Settings) or any ship is
  free (Easy), otherwise at the Federation's price. Her heading reads "Special Federation Ships".
- **The Relief Ship at her minimum, priced like any ship (Plan Z).** No sensors (the Junkyard sells them; FTL's stores
  don't), 10 fuel, no scrap on any difficulty; medbay, doors, the two guns and a reactor of 6 for her seven bars kept,
  so the medbay runs at something's cost. The written-in 600 is gone: she's priced by the formula at the full rate
  whatever the commission rate, and the plea's reputation cost follows. Priced step by step with heromedel: as she
  was 700; her strippings 682, 642; a single gun was ruled out (the Ion Blast drops the shield, the laser gets
  through), the cheapest crew is already the human.
- **Every hull pays for its rooms and doors**, 5 a room and 2 a door, read off the save: a game hull as a design's
  (designs alone paid before, 10 and 5). Superseded the same version by section 30, the price strictly counted.

## 30. The price of a ship, strictly counted, at the difficulty's rate — built (5.00; harness checks in PriceT, PartT, DerT, FleetT)

heromedel's decision, after the Relief Ship's pricing went round in circles (the ship price used FTL's 1 scrap for
Piloting and Engines and nothing for Oxygen, while the Junkyard's parts had them at 150: two prices for one thing):
start again, count everything on her, one price everywhere, and let the difficulty set the rate.

- **The formula** (`Pricing.ship`): her model's hull at 10 a point; the reactor as FTL's upgrade screen charges it
  (15 a bar to 5, then 5 more every 5); her systems and their levels at FTL's prices and upgrade costs, with Piloting,
  Oxygen and Engines at 150 for level 1 (`Pricing.CORE_SYSTEM`; FTL's upgrade steps on top) everywhere; weapons,
  drones and augments (her cargo too) at store price; crew at hiring price (a crew member never comes with skill);
  fuel, missiles and drone parts at store price; the scrap aboard at face value; each room her blueprint reserves for a
  system she has at a tenth of that system's level-1 price; each other room 2; each door 2. The Kestrel A: hull 300,
  reactor 135, systems 920, gear 118, crew 135, supplies 112, scrap 10, 8 system rooms 74, 9 other rooms 18, 26 doors
  52: 1,874. The Relief Ship Type A: 1,507. Damage stays a separate deduction (Trade In, Auction, derelicts).
- **The rate** (`Pricing.rate`, the commission percent): an Immersive career's difficulty sets it, Easy 50%, Normal
  75%, Hard 100% (the old 75/100/100); Custom picks one of the three; Sandbox chooses in Settings (100, 75, 50). A
  career from before difficulties keeps its full price. The Kestrel A costs 937, 1,406 or 1,874; the Relief Ship 754,
  1,130 or 1,507, and she's no longer at a rate of her own.
- **Where it applies:** wherever a ship is priced. Commission (the Relief Ship like any ship), the plea's value of her,
  what she's worth at Trade In and Auction (`Pricing.saleValue`), a final victory's reward and the museum, and the
  Junkyard: a part's worth is its price at the rate before the Junkyard's own rolls (the share by how broken it is,
  the clearance, the broken bars off), and a derelict's value is at the rate before her 25 to 75 percent and her
  damage. The Junkyard's tweaks themselves are unchanged (15 points off per missing core system, the clearance, the
  damage). Never the stores: buying and repairing in the Cargo Bay cost the same on every difficulty. On Easy a ship
  sells for less at a victory, and her replacement costs less: heromedel's call, fair both ways.
- **The ship report, tidied (Plan AA).** The panel Commission, Build Ship and the Ship's report share
  (`SpaceDockUI.shipSummaryPanel`) is two columns from the top: her picture, supplies and crew on the left; weapons,
  drones, augments, cargo and systems on the right, so nothing sits beside an empty half and nothing scrolls off.
  Each system has FTL's icon, its level and a bar of it to the vanilla max (`LevelBar`, the design screen's bar moved
  out of `NumberRow` so both screens draw the same: green, amber past the max, red for broken bars); the reactor too.
  Commission's price breakdown is three columns of name and price instead of one wrapped line. Past ten segments
  (a big reactor, a design past the max) the bar stands down and the row says it in words, "12 / 2 broken", so nothing
  runs off the screen; every vanilla max is 8 or under, so a stock ship always has bars.

## 31. Crew expeditions, a second system behind a hidden switch — built (5.00; harness test AsgT)

heromedel's design, tried beside the old board rather than in its place: `expedition_type` in the cfg (never in
Settings), 0 hides expeditions (the Space Dock's button becomes Hire Crew, the volunteer board alone), 1 is the board
of jobs of section 25, 2 is this (the default since heromedel's go-ahead). The old system's infirmary and ransoms settle under any value; the two
share those files, the crew-card picker and the hire button, and nothing else (`parser/Assignments.java`,
`ui/AssignmentsDialog.java`, the fleet's `assignments.txt`, the words in `resource/assignments.txt`).

- **The board** offers three sectors of the ten (Civilian, Engi, Zoltan, Mantis, Pirate, Rebel, Rock, Nebula,
  Abandoned with Advanced Edition only, Crystal rarely), each with a line of words that says nothing of the odds; an
  offer not taken comes down after a few beacons (hidden). Pick one and one to three crew from the Cargo Hold; they
  leave the hold's save for the assignments file (so nothing is matched back by name: CONCERNS 2's real fix, for this
  system), setting out counts a beacon, and they're due in 1 to 3 more (hidden). No ratings, warnings or hints
  anywhere: what suits whom, the player learns from the reports.
- **The roll**, heromedel's tables as given: the job from the sector's weights (sixteen jobs, 136 a sector, each
  sector +5 to two and -5 to two); one hazard in ten (a solar flare, an asteroid field, a pulsar, a plasma storm in a
  nebula only; shrugged off by a race each); a d20 a head for the band (1 died, 2-5 injured, 6-9 failed, 10-15
  successful, 16-19 very, 20 extremely), one reroll of a 9 or under when the sector or the job suits the race (one in
  four each, two in four both, one good and one bad cancel). The pot is 2d10 times 100% + 10% a head + each one's band
  (-30 to +30), race by sector (±10, Lanius +20 in Engi and Mantis space, Abandoned -10 to all but Crystal and Lanius,
  a race's own bonus winning), race by job (±10), the job's skill (+10 a level: Attack weapons, Defend shields,
  Repair, Salvage and Infection repair, Scout, Got Lost and Hijack piloting, Escort and Transport engines, the fights
  combat, Negotiate and Rescue none) and a hazard not shrugged off (-10); a crew member sent hurt counts half their
  own bonuses; never under 1. Three Slugs in a nebula average about 18 and top out near 50; three Lanius in Mantis
  space out-earn three Slugs there two to one.
- **What comes of it:** a 1 is a death; an injury halves health (resting in the hold heals, as always); an injury on
  Get Boarded is a coin toss for capture (a ransom follows, the old letters); an injury on a job and in a sector both
  bad for the race is the infirmary at a quarter health; an injury on Giant Spiders is a death. Each natural 20 rolls
  again, and a 10 or more finds an item worth up to double the pot (a weapon, drone or augment the stores sell, else
  supplies). A Hijack, Salvage or Rescue that went well (no deaths, someone at 16 or better) rolls one more d20 for
  the mission: a 20 brings a ship home (kept in `assignments/` until the commander answers: to the Space Dock, to
  the Junkyard, or not taken; set out at the station as she is), a part to the stored systems (level 1, a bar broken) or a
  rescued one who asks to sign on (one in twenty with a skill already; take them into the Cargo Hold or send them on
  their way); a Hijack's 15 to 19 brings a part. With Immersive Notifications on the report's letter carries the
  question with its own buttons (the recruit waits in the station's lounge, the ship is moored at the station, until
  you answer); otherwise the Space Dock asks, and asks again at the next look if the box was closed. A chosen No is
  for good. The job's skill pays points by band, so a long campaign levels people
  up.
- **How long they're away** (`Assignments.days`): 1 to 3 days, then a day more for a nebula (half the time), Abandoned
  or Crystal space (always), a Mantis sector (half the time); Got Lost a day and a day for every failed roll on it;
  on any other job a failed roll a day half the time; each item found a day, a part or a recruit one more, a ship
  two; each Rock sent one time in three; a Scout a day less; never under 1 nor over 10. So the result is rolled when
  they set out (the record keeps the seed and the crew as they left; it rolls the same when they're back) and told
  only then: a detail that's late is a tell that something happened, which the report never says.
- **The report**, when the Space Dock next sees them due (a pop-up; a letter in the inbox with Immersive
  Notifications on), in heromedel's frame: the heading, the sector, "Due to events during the assignment the crew"
  and a line for the job, a hazard's line, a line a crew member ("was injured in the attack", "was extremely
  successful and brought back an Artemis Missile"), the prize's line, Total Reward. Never a roll, a die or a
  percentage. The lines are in `resource/assignments.txt` (several per job, one picked), editable without a build.

## 32. One day a beacon — built (5.00; harness checks in PartT, FleetT, TransT, ExpT)

heromedel's clock: a beacon is a day, 28 to the month, for everything the station times. The stipend comes every
month on Easy, two on Normal, three on Hard (28, 56, 84 beacons; `Career.BEACONS_PER_MONTH`), Sandbox every two
months, a career from before difficulties every two (the nearest to its old 60). Careers already running keep their
stipends paid; only the next one's length changes. The ransom's month is 28 beacons and the reminder comes six before
the end; the infirmary keeps the hurt 6 to 12 days (the same stretch as before). The Junkyard's intervals stand and
their footers say what they are in days ("a week or two", "two weeks to six"). The old board's postings and the crew
expeditions' days were days already. The words everywhere say months, never beacons.

## 33. Captain's Quarters, and Refresh as a button beside Helm — built (5.00; harness test RestT)

heromedel's idea: a way for time to pass without flying, now that details come back and ransoms run on the station's
clock. Under Station, above Settings, "Quarters" ("Click here to head to quarters for a quick rest."): the question
"Would you like to spend the rest of today in your quarters." with No to begin with; Yes passes one beacon of the
fleet's time (`Rest.rest`), runs the station's round (reports, the prizes' questions, ransoms, the infirmary) and
rebuilds the screen; "Rested in quarters" in the history. Resting on and on isn't honourable (heromedel): the first
day is free, then each day in a row costs reputation, 1, 2, 3, 4, 5 and 5 from there, with Reputation shown;
anything else that moves the clock ends the run. From the second day the question reads "...as you did yesterday."
and, on a line of its own, "What will people think.", from the third "...as you have for the last N days.", with
"-N reputation." under it on its own line when there's a cost and nothing when there's none. A day to get a detail
back costs nothing; sleeping to a stipend costs about 125. The Refresh button left the column: a small square with
the big buttons' rim and two chasing arrows sits at the top right beside Helm, past the column's edge, with the old
tooltip and the same action.

## 34. The quick-fix batch — built (5.00; harness checks in AsgT)

From heromedel's notes: the Long Range offer's amount starts at 1 as the Cargo Bay's does, with Offer all beside it;
the Cargo Bay's arrows get "< all" and "all >" under them; an Info button beside every list on the Long Range screen,
both sides, opening the crew report or the item's card (the tooltips stay); and crew expeditions score reputation as
the game's events do (`Reputation.expedition`): the pot a tenth, each crew member killed -10, everyone successful
+2, nobody successful -1, nothing for items, prizes or captures (a refused or lost ransom already counts as a death).
Also from the notes, found done already: the Undo Retrofit confirm lists what moves back; the From the game art
picker has a preview; the Content line is gone from the ship report; the Locked ships panel wraps to the screen.

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
