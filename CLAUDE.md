# Federation Home Planet: notes for Claude

A save-game manager for FTL: Faster Than Light 1.6.x (Java Swing, one runnable jar). See README.md for what
it does and CREDITS.md for where the code came from.

## Working with heromedel

- Discuss first, and ask before changing code. When asked to "discuss" or "don't write yet", don't edit.
- Never commit or push to `main`. Work only on your own session branch; heromedel decides what goes into
  `main` (a pull request or their own merge).
- heromedel tests on Windows: GitHub Desktop (Fetch, switch to the branch), then `Build - FHP.bat`.
  Say what to test and how, in plain steps.
- The repo is public. Never commit FTL's game files (ftl.dat, its pictures or music) or a link to them.

## Layout

- `src/main/java/homeplanet/`: the program. `core` (startup, config, Slipstream, music), `ui` (windows),
  `parser` (saves, blueprints, the companion mod, designs), `vault` (the ships on disk), `model`.
- `src/main/java/net/blerf/ftl`, `net/vhati`: Vhati's save parser and ftl.dat reader (GPL, lightly extended;
  each changed file says so at the top).
- `src/main/resources/homeplanet/resource/mod/`: the companion mod's base blueprints (`_HP` copies).
- `Tools and Harness/harness2/`: the regression harness (Claude's test bench, not a user tool).
- `Tools and Harness/hw2fhp-converter/`: the FTL Homeworld to FHP converter (a separate jar) and its tests.
- `Build - FHP.bat`: the Windows build (downloads a JDK and Maven into `tools\` once; the jar goes to
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

It builds a fresh test world from ftl.dat alone (WorldT), then runs VaultT, RoundT, PicT, DesT and CommT on copies
of it. Every test should print ALL PASSED, and RoundT "0 differ, 0 unreadable". Scratch goes in
`harness2/work/` (ignored). The converter tests (ConvT, StoT) run only when old Homeworld saves and program
folder are passed as the 2nd and 3rd arguments.

To see a window without a display, run it under `xvfb-run -a java ...` and paint the dialog's root pane into
a BufferedImage.

## Style

Match the surrounding code: tabs, `homeplanet.*` classes referenced by full name where the file already does,
short comments that say why. Keep Java 8 compatible (no `var`, no newer APIs).
