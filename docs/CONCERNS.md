# Concerns

Design debts heromedel and Claude have talked over: not bugs, not features, but ways the station is built that cost
something and may be worth changing later. Each entry says what it is, why it's that way, what it costs, and what a
change would look like. Started at 4B.97 (branch Cloud-C-Primary-Edit); add the version when an entry is added. An entry
is removed once it's fixed or no longer applies (heromedel, 6.07); the numbers of the rest are kept, since other files cite them.

## 7. Code kept for fleets from before 6.0 (noted 5.97)

**What it is.** Each fleet is brought across to the 6.0 layout the first time it opens, so the station carries the code that
reads every older shape: manifest.xml and history/ (before 5.69), the logs at the root (before 5.71), the Cargo Hold as a save
(before 5.84), the small files as .txt (before 5.86), the old prose logs (before 5.73), crew.txt (before 5.83), a ship's side files
(before 5.98). Since 5.97 it is
all in `homeplanet.convert`: `OldFleet` runs the steps in order, `Layout` and `LogConvert` do the two big ones, and the few
pieces too much a part of their class to move (the crew register's reading of crew.txt) are marked `@Before6`. `OldPackage` is
apart from the rest: a ship traded from a station older than 5.75 (her voyage log as prose); it goes with the protocol, not with
old fleets.

**What it costs.** About 1,000 lines that a 6.0 fleet never runs. Its tests were removed from the harness after 5.98
(heromedel) and the package is frozen: not edited, so nothing can break it; a change would bring its tests back from git history. Other fleets are converted only when they are opened, so a few readers still look at another
fleet in an older shape (its hold, its station log, its ships' blueprints, a ship sent to it); converting every fleet at start-up
would let those go sooner, but each fleet's conversion writes its own log, which today always goes to the fleet in use.

**What a change would look like.** Delete the package; the compiler then points at each call into it and each `@Before6`, every
one a line or a method to delete. Then delete the tests of old fleets. A fleet from before 6.0 would then have to be opened once
by a 6.x station first: the station could say so plainly when it finds `manifest.xml` or `ships/`. When is heromedel's call
(5.97: "at some point in a future build").

## 8. The reputation re-evaluation and its correction (noted 6.54)

**What it is.** `vault/Reputation`'s `offer`, `answer` and `rescored` (6.19) re-score a career's log once, for a score counted
before the rates (6.13, 6.17); `correct` and `Levels` (6.54) put right a re-evaluation that rated each ship at the difficulty she
flies now (a ship moved to Hard after her victory had her whole career re-scored at Hard), with its notice in `SpaceDockUI`
(`tellCorrected`) and RepuT's O, C and H checks.

**What it costs.** About 200 lines that only a career from before 6.17 ever runs, once: every career since is counted at the
rates from the start and marked so.

**What a change would look like.** heromedel, 6.54: it could probably be removed some day, once the careers that needed it have
opened on 6.54 or later. Delete the methods and the notice, keep `RATED` and `CORRECTED` so the marks in `reputation.xml` still
read, and drop RepuT's O, C and H checks.

## 9. Where the settings live (noted 6.55)

**What it is.** heromedel, 6.55 (Plan AI), in place of the 5.94 call that settings stay in the cfg beside the jar
(`docs/OVERHAUL-6.md` 12, removed at 6.66, in git history): the cfg beside the jar keeps only where things are (`ftlDatsPath`, `ftlSavePath`, `slipstream_dir`);
the station's own settings, the same for every career, are in `federation-home-planet.cfg` in FTL's saves folder (with a copy of
the three folders, so a new release folder finds FTL and Slipstream); Sandbox Mode's rules are in `FederationHomePlanet/rules.xml`
(`HomePlanet.SANDBOX_KEYS`); an Immersive career's are in its own `career.xml` as before, and how reputation can be used is each
career's own since 6.55 (`Career.reputationUse`, Sandbox Mode's standing in for a career from before). The crew's names (the race
lists, the human name style, the Engi translation) are each fleet's own since 6.62 (heromedel: they change the names given):
`CrewNames.set` keeps them with Sandbox Mode's rules (lists off unless chosen) or in the career's `career.xml` (lists on unless chosen).
A cfg from before is carried over on its first save. Startup now finds the saves folder before FTL's folder, and takes the station's own without asking.

**What it costs.** The one `HomePlanet.config` in memory still holds everything, so nothing that reads a setting changed; only
`loadConfig`, `loadSavesSettings` and `saveConfig` know the three files. They are written one after another, each whole on its
own (not through `vault/Journal`): a stop between them leaves the older copy of one file, never a broken one. A new setting goes
to the station's file unless its key is added to `SANDBOX_KEYS` (Sandbox Mode's own) or `PATH_KEYS` (this computer's).

**What a change would look like.** Moving another setting to a career means its key into `SANDBOX_KEYS` and, for the Immersive
careers, a `Career` getter and setter like `reputationUse` (CfgT's R checks show how it's tested).


## 10. The Relief Ship's weapons system (noted 6.64)

**What it is.** heromedel, 6.64: the Relief Ship Type A appears to have her weapons system at level 4, when she only needs
level 2 to start. `Commission.buildRelief` sets every system to its minimum, the weapons at 2 (two bars for her two guns, a
Burst Laser I and an Ion Blast), so something after it, or another way of building her, gives her more than it means to.

**What it costs.** Not looked into yet. A plea's ship is meant to be the bare minimum; two extra weapons bars are worth
scrap at the Dry Dock, and count in her price.

**What a change would look like.** Find where her level 4 comes from (the plea's own build, the price check, or FTL
reading her blueprint), and keep her at 2.
