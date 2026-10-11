# Federation Home Planet: notes for Claude

A save-game manager for FTL: Faster Than Light 1.6.x (Java Swing, one runnable jar). See README.md for what
it does and CREDITS.md for where the code came from.

**Fitness log:** this project cannot access heromedel's fitness log. Don't ask about it and don't look for it.

## Working with heromedel

- The workflow: **discuss, create a plan, ask to write, ask to push.** When asked to "discuss" or "don't write
  yet", don't edit. Those two asks (write, push) are the only gates: the plan itself makes the sensible choices
  (from `docs/VOICES.md`, `docs/STYLE.md`, the roadmap) rather than stopping on each one. Ask only what can't be
  worked out; never about wording, capitals or details you can decide.
- In discussions, talk in normal paragraphs, like a conversation: start with what you think of the idea (what's good
  about it, what you'd build from it), and fold practical details in or handle them in the plan. Don't answer an idea
  with lists of problems, issues and questions; keep lists for the plans themselves.
- Plans and ideas are lettered; their steps are numbered:

      Idea A            Plan B
      1. ...            1. ...
      2. ...            2. ...

- Bug fixing takes precedence over feature creep. When a major bug turns up (game-breaking, or a hidden one that
  quietly damages a fleet, like crew being cloned), you may ask heromedel to hold off on the next plan so it can be
  fixed first: occasionally, not in a pushy way.
- heromedel's own text (letters, messages, names) goes in exactly as written, capitals included (they're often
  deliberate: "Lucky Duck" is a nickname). Suggested edits to it are a short list, only ones that matter, to answer
  yes or no.
- Answer questions without writing code, and answer just the question: don't tack status lists onto replies. Keep the
  list of small fixes and requests to yourself, show it only when asked, and do them as one batch; one version per
  batch, not per small change.
- Commit whenever the work is safe (built and tested); ask before pushing. One version on the branch at a time: once a
  version is pushed, the branch stays frozen while heromedel tests it, so what they test is exactly what they merge.
  Newer work stays committed locally until they say it's merged (or needs a fix), then goes up as the next version.
  Don't bring up commits in conversation; the automatic "uncommitted changes" check is answered in a few words.
- **Other branches:** before planning work on your branch, fetch and check whether `main` or another session's branch
  has moved on since you last looked (a newer version). If it has, look over what changed, especially files you'll
  touch, settings, names and shared tools. Plan to fit with it (the same patterns and paths, no clashing names or
  files), so the later merge is easy, and say what will need care when the branches are merged. Two branches may use
  the same version number meanwhile; the merge renumbers to follow `main`.
- Never commit or push to `main`. Work only on your own session branch; heromedel decides what goes into
  `main` (a pull request or their own merge).
- Do not make a pull requst without double checking. Hero prefers simple merges.
- heromedel tests on Windows: GitHub Desktop (Fetch, switch to the branch), then `Build The Federation Home Planet Station.bat`.
  They know how to fetch and build: don't repeat test steps after each commit. Mention what to test only when it's
  something unusual they wouldn't find on their own.
- `docs/ROADMAP.md` holds the owner's decisions and the build order: read it before planning features.
  `docs/CONCERNS.md` lists the design debts talked over (how the station is built, what it costs, what a change would
  take): add to it when one comes up, with the version; read it before planning anything that touches the vault's files
  or how crew are tracked.
  `docs/OVERHAUL-6.md` is the 6.0 plan (storage, logs and code rebuilt) with every session's notes: read it before planning
  anything that touches storage or logs, and add thoughts only under your own heading at its end, as it says.
  `docs/BUGS.md` lists bugs found and not yet fixed (heromedel, 6.01): add one there when a fix is put off.
- **The feedback sheet (Prime only):** Cloud-C-Primary-Edit reads the Send Feedback responses after every push
  (https://docs.google.com/spreadsheets/d/1QxIH-JnaGoCNnucmq1O6qU5eCSABymt-A5NHXfQgRFY), brings `docs/FEEDBACK.md` up to date
  (what each response asked and what became of it) and tells heromedel what's new. Other sessions leave it to Prime.
  `docs/EVENTS.md` lists every event kind the station logs and its fields: anything that writes a log adds its kind there
  first (the log rule below), and EventT checks the list.
- **Handoffs:** before fixing a bug, ask heromedel "Would you like me to work on this or prepare a handoff?". A handoff
  is written as `docs/HANDOFF.md` says (the parts in order, and a page template to publish).
- The version (4B.nn; after 4B.99 comes 5.00, then 5.01 to 5.99, then 6.00) goes up by one only with a commit: `<version>` in `pom.xml` and
  `APP_VERSION` in `HomePlanet.java`, always together.
- The repo is public. Never commit FTL's game files (ftl.dat, its pictures or music) or a link to them.

## Layout

- `src/main/java/homeplanet/`: the program. `core` (startup, config, Slipstream, music), `ui` (windows),
  `parser` (saves, blueprints, the companion mod, designs), `vault` (the ships on disk), `model`, `comm` (Long Range
  Comm.: trading with another station; see `docs/LONG-RANGE-COMM.md`), `convert` (a fleet from before 6.0 brought across).
- **A fleet on disk (6.0, since 5.69):** every ship is a folder (`shipyard/<Name>.<id>/`, `junkyard/`, `memorials_and_records/ships/`
  for the ones that left) with her record as xml (her notes are sections of it, 5.98: trade mark, journey, museum entry, last look, fate and the
  rest; `ShipStore.notes` reads one, `Vault.setNotes` writes one; since 6.10 it names her `career` and her `origin`, and her save carries her
  mark, `vault/ShipMark`: a save or folder the fleet can't prove is its own waits for the player's word, never taken in unasked), her save, her log, her `versions/` and her crew's files (`crew/`, one per crew member, 5.83; plain tags since 6.11);
  the Cargo Hold is `cargohold/` (`cargohold.xml` is what it holds, 5.84: no pretend ship); `expeditions/`, `infirmary/` and `captives/`
  have their own xml (5.85); the career's small files are xml, one per concern, the clock in `clock.xml` (5.86); everyone who left is in
  `memorials_and_records/` (ships and crew). The station's log is `logs/events.log` alone: since 5.93 nothing writes history.log,
  master.log, voyage.log or reputation.log (an older fleet keeps them, read only by the conversion). The words the station writes
  (the log's human lines, the letters, the expedition words, the accolades and deeds: `lore/logs/station-log.xml`, `letters.xml`,
  `expeditions.xml`, `deeds.xml`) can be overridden in `lore/` beside the jar (`core/Lore`, 5.89 and 5.991; the jar holds the
  defaults in `resource/lore/`; the words are McCarthy's, and LoreT holds every file to the hard and voice rules).
  `station-action-protection/` holds the notes of actions under way (its why.txt says so). The tree is drawn
  at the top of `vault/Vault.java`. A 5.x fleet is converted the first time it opens, a zip of it kept beside its folder: every step of that, and every
  reader of an old shape, goes in `homeplanet.convert` (`OldFleet` runs them in order; anything that must stay in its own class is
  marked `@Before6`), so the package can be deleted one day (`docs/CONCERNS.md` 7). It is frozen: not edited, and no longer tested. There is no
  index: the fleet is read from the folders on opening. **Anything that moves or writes more than one file goes through
  `vault/Journal`** (a note first, the steps, the note deleted; a note left behind is finished at the next opening): never a hand-written undo.
- **Long Range Comm.:** stations match on `Session.PROTOCOL`, not the version. Bump it only when an older station
  would trade wrongly; new fields, kinds and package files are ignored by older stations, so adding one is safe.
- **Traded ships:** anything that rewards or reacts to what a ship has done (events, rewards, letters, achievements)
  counts only what she did since her last trade: ask `homeplanet.vault.TradeMark`. Displays keep her whole life.
- **Without Slipstream:** at any time that content is not available because the player is not using Slipstream, the
  game should still work otherwise. A reward could (not must) come in a version that needs patching and one that doesn't; whichever
  is accepted, the content that isn't patched still works as normal. Players can accept a reward they won't patch in,
  with a message explaining that without the patch it may not be usable.
- `src/main/java/net/blerf/ftl`, `net/vhati`: Vhati's save parser and ftl.dat reader (GPL, lightly extended;
  each changed file says so at the top).
- `src/main/resources/homeplanet/resource/mod/`: the companion mod's base blueprints (`_HP` copies).
- `docs/LORE_COMPONENTS.md` is the facts file (real FTL lore with sources) for checking words against: it is never shipped
  and is not part of the 6.0 `lore/` words folder (`docs/OVERHAUL-6.md` §3.5).
- `docs/NAMES.md` is the style guide for crew names by race (heromedel, 6.28: the Slugs' drawn-out letters, the Engi's
  hex and the rest): every name in `lore/names/` follows it.
- Expeditions: `expedition_type` in the cfg (hidden) is 2, the crew expeditions (`Assignments`, `docs/ROADMAP.md` 31; the
  default), or 0, hiring alone. The infirmary, the captives and ransoms, and hiring live in `Expeditions`; the old board of
  jobs that used to be 1 went at 5.67 (heromedel), and 1 reads as 2.
- **One home each (6.0, step 10):** race names come from `model.Crew` (`raceTitle(id)`: FTL's title, "Rockman"; `racePeople(id)`:
  the people, "Rock"; `peopleOf(shipId)`), "the" before a ship's name from `parser.ShipNames.the`, capitals and a/an from
  `model.Words`, prices from `parser.Pricing`, a ship's gear from `parser.SaveHelper.gear`, a crew member on disk from `vault.CrewRecord`
  (6.11: every file that keeps crew; Long Range Comm. converts to its own names), the stored systems from `vault.StoredSystems`, a new crew member's name from `parser.CrewNames` (6.33: the race lists and the Settings):
  never a copy of any of them. The windows ask and show; what they save goes through the vault (6.11: the Cargo Bay's Save is
  `vault.CargoBaySave`, the Space Dock's business `vault.SpaceDock`).
- **Crew names:** avoiding duplicate names is impossible (heromedel): trades, Rename, FTL's own crew, hiring and
  recruits all make namesakes, down to the same looks. Never plan or test a fix that prevents them; anything that
  tracks crew has to work with namesakes.
- `Tools and Harness/harness2/`: the regression harness (Claude's test bench, not a user tool).
- `Tools and Harness/hw2fhp-converter/`: the FTL Homeworld to FHP converter (a separate jar) and its tests.
- `Build The Federation Home Planet Station.bat`: the Windows build (downloads a JDK and Maven into `tools\` once; the jar goes to
  `Current Build\`).

## Build

    mvn -q -B package -DskipTests      # makes target/Federation Home Planet.jar (Java 8 target)

The config (`federation-home-planet.cfg`) is a Java properties file beside the jar. First startup asks for the
FTL and saves folders, Steam launching (Steam installs only), the House Rules window, then offers
Slipstream once (`slipstream_offered`).

## Test

The harness needs FTL's `ftl.dat` (about 280 MB). It isn't in the repo: ask heromedel for it and keep it in the
scratchpad, never in the repo. Then, after building the jar:

    "Tools and Harness/harness2/run.sh" /path/to/folder-with-ftl.dat

It builds a fresh test world from ftl.dat alone (WorldT), then runs VaultT, RoundT, PicT, DesT, CommT and the rest on
copies of it (LinkT runs a second station in its own process, over localhost; HoldT, LoreT, CarryT and RegT test the 6.0 storage,
and the harness reads the station log and a voyage log from events with `Setup.stationLog` and `Setup.voyageLog`). Every test should print ALL PASSED, and RoundT "0 differ, 0 unreadable". Scratch goes in
`harness2/work/` (ignored). The converter tests (ConvT, StoT) run only when old Homeworld saves and program
folder are passed as the 2nd and 3rd arguments.

The kill test (KillT, which stopped the station before every file it wrote) and the old-fleet conversion's tests (MigT,
LogConvT, SmallT and the old-fleet parts of HoldT, ExpT, RegT, JournalT and ShipStoreT) were removed after 5.98 (heromedel:
they passed for the 6.0 conversion, which each player does once, and running them again only cost time). They are in git
history if ever wanted; Buggy Boy can run an overkill check on request.

**What to run before a commit (heromedel, 6.01):** the tests that cover the change (a handful, usually under a minute:
`cd "Tools and Harness/harness2/work"`, compile the harness, run each by name as `run.sh` does), plus a check on a copy of
heromedel's real fleet where the change touches what it holds. Not the full harness: that runs only when heromedel asks,
or on Buggy Boy's bench. Small UI or text changes (a button, a message, a tooltip) need only the build, plus a screenshot
or the one test that covers it.

To see a window without a display, run it under `xvfb-run -a java ...` and paint the dialog's root pane into
a BufferedImage.

**Testing in FTL itself:** heromedel's Windows copy of FTL (ask for it; keep it in the scratchpad, never in the repo)
runs under 32-bit Wine on an Xvfb display: `apt-get install wine wine32:i386 xdotool imagemagick`, a `win32` prefix in
the scratchpad, `settings.ini` (fullscreen 0, 1280x720) in the prefix's `Documents/My Games/FasterThanLight`. Slipstream
can't be downloaded here, so patch a *copy* of `ftl.dat` with a plain PKG rewriter (header, 20-byte entries, paths,
data, the entry table sorted by path hash: FTL searches it that way, and an entry out of order is simply not found; `.xml.append` files spliced before `</FTL>`; the station's `PkgPack` writer is not to be trusted for this).
Build a save with `Commission.build` against the patched copy, drop it in as `continue.sav`, start `wine FTLGame.exe`
with `LIBGL_ALWAYS_SOFTWARE=1`, drive the menus with `xdotool` (press and release with a short hold) and screenshot
with `import -window root`. The Steam build runs without Steam; sound fails harmlessly.

## Hard rules

Three rules with no exceptions, in anything the player sees:

1. Never imply the Rebel Flagship has been destroyed: the war goes on.
2. Never say to the player that time is measured in beacons. The station's clock counts them, hidden; the player hears
   "some time", "a while", "one month" (as the ransom letters say), never a number of beacons or "a beacon later".
   A beacon is a day (5.00): 28 beacons are a month (the ransom's month; `Career.BEACONS_PER_MONTH`), and the stipend
   comes every one, two or three months by difficulty (28, 56, 84). A day in Captain's Quarters is one beacon.
   Days are real now: a player may work out that a jump passes a day; never say time is only beacons underneath.
3. Check any system, outcome or message against `docs/LORE_COMPONENTS.md` (real FTL lore, each fact with its source):
   report any inconsistency to heromedel, and add no new ones. Don't edit that file without his permission; when new
   real FTL lore turns up, offer it to him as a numbered list and ask whether it should be added.

## Log lines (hard rule)

heromedel, 5.57: anything new that writes a log follows this from now on, and the 6.0 storage overhaul brings every
log to it. Every log entry is two lines:

1. A machine line: real time, stardate, the kind of event, then everything the program could ever need as `key=value`
   fields: ids, names, races, places and folders, classes, the systems involved, the station's version. The fields
   listed here are a minimum: more data than the rule names is always better than missing data.
2. A human line, written from the machine line (never separately, so the two can't disagree): simple, lore-friendly,
   and following the hard rules above (never a beacon count). Readers (the Captain's Log, the Crew Log, the station
   log) read the machine lines and word them their own way; nothing parses the human line.

For example:

    2026-10-07 14:02:11 | 1.2.3.4 | CREW_MOVE | crew=Bob.17 race=human sex=male tints=0.2 rank=Sgt. skills=s0:13,s1:0,s2:4,s3:58,s4:0,s5:7 record=repairs:3,kills:12,evasions:0,jumps:41,masteries:2 from=ship:Kestrel.a3f2 from_class=PLAYER_SHIP_HARD from_folder=shipyard/Kestrel.a3f2 to=ship:Shippy McShipface.c77a to_class=PLAYER_SHIP_FED to_folder=shipyard/Shippy McShipface.c77a reason=cargo_bay_save by=player station=6.00
    Bob was transferred to the Shippy McShipface.

## Voice (player-facing text)

- Immersion and understandability matter more than identical phrasing: messages may vary their wording for flavour.
- Fonts and colours: see `docs/STYLE.md` (a guide, not law).
- **The Home Planet Station** is the base: it builds, sends, searches, stores, and has systems, an interface,
  databases and communications that can fail ("The Home Planet Station could not…"). **The Federation Home
  Planet** is the authority: it approves, commissions, draws up blueprints. "The" is capitalized as part of
  these titles, even mid-sentence.
- **a station** (lowercase) is an FTL store beacon. Crew "stations" in the ship editor are FTL's manned squares.
- Slipstream is the transmission channel: mods and blueprints are "sent to FTL via Slipstream".
- **the rebellion** and **the rebels** are never capitalised as a title: the Federation won't dignify them with one (a
  sentence may still start with "Rebels"). Only the Rebel Flagship (FTL's name for that ship) keeps its capitals. Catch phrases: see `docs/STYLE.md`.
- Never imply the Rebel Flagship has been destroyed: the war goes on. Its weapons come from plans stolen from the
  Rebels, not salvage.
- Places: the Space Dock, the Cargo Bay, the Cargo Hold (the storage; its save keeps the internal name "Spacedock
  Storage"), the Dry Dock, the Junkyard. A ship is **decommissioned** (not disbanded). A ship is her, him or it as the Ship Pronoun setting says (Settings, General, 6.42):
  `model.Words` (`she()`, `her()`, `herObj()`) in code, `{she}`, `{her}` in lore; never a fixed "she". Never "Home World",
  never "FHP"* in player-facing text. Errors stay actionable (what failed, what to do, the file or path).
  *it may seem like bad form but it is not a hard rule that heromedel ever made

## Style

Match the surrounding code: tabs, `homeplanet.*` classes referenced by full name where the file already does,
short comments that say why. Keep Java 8 compatible (no `var`, no newer APIs).
