# Federation-Home-Planet

Federation-Home-Planet (FHP)
An external application for use with FTL: Faster Than Light that allows you to keep a fleet between runs: Switch ships, start a new journey with the same ship, or trade gear, crew and scrap through a storage hold or between ships, shop at any ships' beacons, refit, remodel, design and commission new ships. New blueprints work with Slipstream.

Slipstream can be downloaded through this application and choosing which mods to bundle with your new blueprints can be handled inside of it as well.

You can board a ship, go to the cargo bay, trade with other ships or the stations storage, buy and sell parts based on stores your ships are near and then launch FTL with or without steam without even leaving the app and only hitting refresh when you come back. 

Options to: 
Restrict docking and trading to only when you are at a store beacon.
Limit Commisioning new ships to those unlocked in game.
Limit Commissioning Custom ships based on Vanilla Designs to only those unlocked in game.
Refit or Remodel ships completely, moving systems, rooms even doors. 
Design a ship from scratch using your own art or the ships from the game. 
     FHP bundles the new blueprints into a companion mod, 
     can launch Slipstream and patch the blueprints and any other .ftl mod you choose into the game
Rename Ships or Crew Members
Disband Ships to a junkyard where you can Scrap them removing the parts or destroy them. 
And More. 


## Running and building

Java 8 or newer runs it: `Current Build\Start - FHP.bat` (or `java -jar "Federation Home Planet.jar"`). On first start it asks where FTL is (the folder with `ftl.dat`) and where the saves are. Quit FTL before docking, boarding or saving.

To build from source, double-click `Build - FHP.bat`: the first run downloads a JDK and Maven into `tools\` (once), then every run puts a fresh `Federation Home Planet.jar` in `Current Build\`. Or with your own Maven: `mvn package` → `target/Federation Home Planet.jar`.

## Layout

* `src/`, `pom.xml` – the application. `net.blerf.ftl` is Vhati's FTL Profile Editor parser; `homeplanet.*` is the station (`core` start-up and settings, `vault` the ships folder, `parser` save editing and blueprints, `ui`, `model`).
* `Current Build/` – where the built jar lands, with its start script (settings and logs stay beside it, untracked).
* `Tools and Harness/harness2/` – headless regression tests. `Tools and Harness/hw2fhp-converter/` – the one-off converter from FTL Homeworld's files (not shipped with the app).

## Where your ships live

Everything the station keeps is in `FederationHomePlanet` inside FTL's saves folder: `ships/`, `junkyard/`, `history/` (the last ten versions of every ship), `storage.sav`, `manifest.xml`, `designs.xml`, `remodels.xml`, `art/`, `history.log`. The ship you are flying is FTL's own `continue.sav`, as always. Custom blueprints reach the game through `Federation Home Planet Mod.ftl`, which the station rebuilds and Slipstream patches in.

## Licence and credits

GPL-2.0 (the licence of Vhati's parser). See LICENSE and CREDITS.md.
