# Federation Home Planet: notes for Claude

A save-game manager for FTL: Faster Than Light 1.6.x (Java Swing, one runnable jar). See README.md for what
it does and CREDITS.md for where the code came from.

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
- Never commit or push to `main`. Work only on your own session branch; heromedel decides what goes into
  `main` (a pull request or their own merge).
- heromedel tests on Windows: GitHub Desktop (Fetch, switch to the branch), then `Build The Federation Home Planet Station.bat`.
  They know how to fetch and build: don't repeat test steps after each commit. Mention what to test only when it's
  something unusual they wouldn't find on their own.
- `docs/ROADMAP.md` holds the owner's decisions and the build order: read it before planning features.
- The version (4B.nn) goes up by one only with a commit: `<version>` in `pom.xml` and
  `APP_VERSION` in `HomePlanet.java`, always together.
- The repo is public. Never commit FTL's game files (ftl.dat, its pictures or music) or a link to them.

## Layout

- `src/main/java/homeplanet/`: the program. `core` (startup, config, Slipstream, music), `ui` (windows),
  `parser` (saves, blueprints, the companion mod, designs), `vault` (the ships on disk), `model`, `comm` (Long Range
  Comm.: trading with another station; see `docs/LONG-RANGE-COMM.md`).
- **Long Range Comm.:** stations match on `Session.PROTOCOL`, not the version. Bump it only when an older station
  would trade wrongly; new fields, kinds and package files are ignored by older stations, so adding one is safe.
- **Traded ships:** anything that rewards or reacts to what a ship has done (events, rewards, letters, achievements)
  counts only what she did since her last trade: ask `homeplanet.vault.TradeMark`. Displays keep her whole life.
- `src/main/java/net/blerf/ftl`, `net/vhati`: Vhati's save parser and ftl.dat reader (GPL, lightly extended;
  each changed file says so at the top).
- `src/main/resources/homeplanet/resource/mod/`: the companion mod's base blueprints (`_HP` copies).
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
copies of it (LinkT runs a second station in its own process, over localhost). Every test should print ALL PASSED, and RoundT "0 differ, 0 unreadable". Scratch goes in
`harness2/work/` (ignored). The converter tests (ConvT, StoT) run only when old Homeworld saves and program
folder are passed as the 2nd and 3rd arguments.

To see a window without a display, run it under `xvfb-run -a java ...` and paint the dialog's root pane into
a BufferedImage.

## Voice (player-facing text)

- Immersion and understandability matter more than identical phrasing: messages may vary their wording for flavour.
- Fonts and colours: see `docs/STYLE.md` (a guide, not law).
- **The Home Planet Station** is the base: it builds, sends, searches, stores, and has systems, an interface,
  databases and communications that can fail ("The Home Planet Station could not…"). **The Federation Home
  Planet** is the authority: it approves, commissions, draws up blueprints. "The" is capitalized as part of
  these titles, even mid-sentence.
- **a station** (lowercase) is an FTL store beacon. Crew "stations" in the ship editor are FTL's manned squares.
- Slipstream is the transmission channel: mods and blueprints are "sent to FTL via Slipstream".
- **the rebellion** and **the rebels** are never capitalised: the Federation won't dignify them with a title. Only
  the Rebel Flagship (FTL's name for that ship) keeps its capitals. Catch phrases: see `docs/STYLE.md`.
- Never imply the Rebel Flagship has been destroyed: the war goes on. Its weapons come from plans stolen from the
  Rebels, not salvage.
- Places: the Space Dock, the Cargo Bay, the Cargo Hold (the storage; its save keeps the internal name "Spacedock
  Storage"), the Dry Dock, the Junkyard. A ship is **decommissioned** (not disbanded). Ships are "she". Never "Home World",
  never "FHP" in player-facing text. Errors stay actionable (what failed, what to do, the file or path).

## Style

Match the surrounding code: tabs, `homeplanet.*` classes referenced by full name where the file already does,
short comments that say why. Keep Java 8 compatible (no `var`, no newer APIs).
