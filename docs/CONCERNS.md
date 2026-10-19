# Concerns

Design debts heromedel and Claude have talked over: not bugs, not features, but ways the station is built that cost
something and may be worth changing later. Each entry says what it is, why it's that way, what it costs, and what a
change would look like. Started at 4B.97 (branch Cloud-C-Primary-Edit); add the version when an entry is added. An entry
is removed once it's fixed or no longer applies (heromedel, 6.07); the numbers of the rest are kept, since other files cite them.

## 7. Code kept for fleets from before 6.0 (noted 5.97; removed 7.00)

**What it was.** Each fleet was brought across to the 6.0 layout the first time it opened, so the station carried the code that
read every older shape, in `homeplanet.convert`, with the few pieces that had to stay in their class marked `@Before6`.

**What became of it.** Deleted at 7.00 (heromedel: 7.0 is the station stripped of everything that only supported old play-tester
careers). A fleet from before 6.0 is not read; a 6.x station must open it once first. The converter from FTL Homeworld's files
(`Tools and Harness/hw2fhp-converter`) went with it, and the tests of old fleets.

## 8. The reputation re-evaluation and its correction (noted 6.54; removed 7.00)

**What it was.** `vault/Reputation`'s `offer`, `answer` and `rescored` (6.19) re-scored a career's log once, for a score counted
before the rates; `correct` and `Levels` (6.54) put right a re-evaluation that rated each ship at the difficulty she flew now,
with its notices in `SpaceDockUI`.

**What became of it.** Deleted at 7.00, with the `rated` and `corrected` marks in `reputation.xml` (nothing reads them), the
Space Dock's questions to a Custom career from before 6.13 (its reputation rate and Rescued Ships to Hard: a career chooses both
in its briefing now), and RepuT's O, C and H checks.

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
own (not through `vault/ActionProtection`): a stop between them leaves the older copy of one file, never a broken one. A new setting goes
to the station's file unless its key is added to `SANDBOX_KEYS` (Sandbox Mode's own) or `PATH_KEYS` (this computer's).

**What a change would look like.** Moving another setting to a career means its key into `SANDBOX_KEYS` and, for the Immersive
careers, a `Career` getter and setter like `reputationUse` (CfgT's R checks show how it's tested).


## 10. The Relief Ship's weapons system (noted 6.64)

**What it is.** heromedel, 6.64: the Relief Ship Type A appears to have her weapons system at level 4, when she only needs
level 2 to start. `Commission.buildRelief` sets every system to its minimum, the weapons at 2 (two bars for her two guns, a
Burst Laser I and an Ion Blast), so something after it, or another way of building her, gives her more than it means to.

**What it costs.** Not looked into yet. A plea's ship is meant to be the bare minimum; two extra weapons bars are worth
scrap at the Refit, and count in her price.

**What a change would look like.** Find where her level 4 comes from (the plea's own build, the price check, or FTL
reading her blueprint), and keep her at 2.
