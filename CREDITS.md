# Credits

**Subset Games** for making FTL: Faster Than Light and its artwork. Federation Home Planet reads the
game's own files (ftl.dat) at run time for ship pictures, icons, fonts and music; none of them are
bundled with the program. A few things derived from the game's data are bundled, and are listed
below.

**Vhati** for his work on the save game parser (the FTL Profile Editor) and the Slipstream Mod
Manager. Everything under `net.blerf.ftl` is Vhati's parser, from the final FTL Profile Editor
source (v28 master, 2020), used under the GPL-2.0. Five of those files were lightly extended for
this program; each carries a notice at the top saying what changed. Slipstream is not bundled:
the program can download it, and hands it the mods to patch.

**ManApart (iceburg333)** for the original FTL Homeworld ship manager, which Federation Home
Planet was inspired by and originally built on, before converting to new code. The Space Dock
and the Cargo Bay began there, and have been heavily changed since, both in code and in
appearance. Of FTL Homeworld 3.1's own code almost nothing is left verbatim (about one line in
a thousand of this program).

**heromedel**: Designer, Producer, Coordinator and Tester of Federation Home Planet. With use of
Claude Opus 5.5 and Fable 5.1 for coding and programming.

## What is bundled

* `homeplanet/resource/mod/*.xml.append` – the three "plain copy" blueprint files that make up
  the companion mod's base. They are derived from FTL's own blueprint data (every player ship,
  renamed with the `_HP` suffix and with every system made optional). They are data about the
  game, not game code.
* `icon/` – The Home Planet Station's icon (every size, and the `.ico` for the desktop Quick Link):
  original pixel art made for Federation Home Planet.
* The Space Dock and Cargo Bay backdrops are not bundled: they're put together when the program
  starts, from the player's own `ftl.dat` (FTL's starfield, planet, ships, weapons, drones, hull
  pieces and room fittings) and from shapes drawn in code (the Cargo Hold's containers, crates,
  pallets and barrels).
* Fonts, music, ship art, item icons and every other picture are read from `ftl.dat` when the
  program runs, and are never copied out of it except into the companion mod for ships you have
  designed yourself (their pictures are copied or cut from the game's, or are your own PNGs).

## Where the code comes from

Counted at version 4B.83 (about 55,000 lines of Java in all):

| Part | Share |
|---|---|
| Vhati's parser, unchanged | 41% |
| Our additions to Vhati's files (five files, notices at the top) | under 1% |
| Lines still verbatim from FTL Homeworld 3.1 | under 0.1% |
| New or rewritten for Federation Home Planet | 58% |

The Java, Swing and the bundled libraries (SLF4J, Logback, JDOM 2, JAXB, java-vorbis-support, JNA)
are their authors', under their own licences; see the pom.xml.
