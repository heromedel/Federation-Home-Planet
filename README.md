~ Incoming transmission from the Federation Home Planet ~

Despite the ongoing war with the Rebellion, the Federation has restored its long-range trade and communication network, carried by official stores and stations across the sectors.

From the Home Planet, the Federation can once more reach beacons in many star systems: moving goods and crew between ships almost instantly, refitting hulls in its dry docks, and commissioning new ships wherever a captain needs one.

Welcome home, Captain, and godspeed.

-----

What is it? It is an external hub that turns FTL's runs into a campaign or career. It is "The home base FTL never had."

Federation Home Planet (FHP)
An external application for use with FTL: Faster Than Light that allows you to keep a fleet between runs: Switch ships, start a new journey with the same ship, or trade gear, crew and scrap through a storage hold or between ships, shop at any other ships' beacons, refit, remodel, design and commission new ships. New blueprints work with Slipstream.

Slipstream can be downloaded through this application and choosing which mods to bundle with your new blueprints can be handled inside of it as well.

You can board a ship, go to the cargo bay, trade with other ships or the stations storage, buy and sell parts based on stores your ships are near and then launch FTL with or without steam without even leaving the app and only hitting refresh when you come back. 

-----

Options to: 
Restrict docking and trading to only when you are at a store beacon.
Limit Commisioning new ships to those unlocked in game.
Limit Commissioning Custom ships based on Vanilla Designs to only those unlocked in game.
Rename Ships or Crew Members
Disband Ships to a junkyard where you can Scrap them removing the parts or destroy them.
Refit or Remodel ships completely, moving systems, rooms even doors. 
Design a ship from scratch using your own art or the ships from the game. 
     The Federation Home Planet then bundles the new blueprints into a companion mod, 
     and sends them through Slipstream to patch the blueprints and any other .ftl mod you choose into the game.
And More. 

-----

## What it does

- **The Space Dock:** your fleet between runs. Board any docked ship, send her on a New Journey from sector 1 with her
  crew and cargo, or decommission her to the Junkyard (salvage her, scrap her for parts, trade her in, auction her off, or destroy her).
- **The Cargo Bay:** trade crew, weapons, drones, augments, systems and supplies with the Cargo Hold (the station's
  warehouse) or another docked ship; shop at the stores your ships are docked at; repair and upgrade in the Dry Dock;
  refit, retrofit, remodel or overhaul a ship's layout.
- **Long Range Comm.:** trade with another commander's Home Planet Station over the local network, a virtual LAN or
  the internet: items, supplies, crew and whole ships, Sandbox with Sandbox and Immersive with Immersive (any level,
  unless a career keeps to its own); any two commanders can hail and talk. Both commanders build the offer and accept it; any change withdraws acceptance.
  Goods arrive in the Cargo Hold, ships at the Space Dock. Nothing is open to the network until you open your
  hailing frequencies; stay powered up to be hailed from any screen, send a message to a commander's inbox (or, marked
  priority, onto their screen), and block a commander you'd rather not hear from.
- **The shipyard:** commission any unlocked ship, a remodel, or a ship you designed yourself. New blueprints are sent
  to FTL via Slipstream.
- **House rules** (Settings): trading and New Journey only at a store beacon, commissioning that costs scrap, locked
  ships that can't be commissioned, selling supplies and systems, a free commission for each ship you unlock, and
  what happens after a final victory (rescue the ship, a reward of her value, or nothing).
- **Immersive Mode:** four careers (Easy, Normal, Hard, and Custom, which sets each rule's level), each with its own
  fleet and FTL profile. The Federation Home Planet's rules are locked, you rise in rank (Commander, Captain,
  Commodore), and transmissions bring commission orders, promotions, achievement rewards and a stipend. Switch Game
  Mode (Settings) moves between Sandbox Mode and the careers at any time, and ends a career.
- **The Federation Museum:** every ship that won in the Hall of Victors (preserved in the museum, still in service,
  honoured in memory, or lost in action later), and the ships lost in action in the Memorial: her record, honours,
  crew, voyage and loadout, an epitaph of your own, and a picture of the exhibit to save.
- **Commission's Locked ships list:** the ships your FTL profile hasn't unlocked yet, with FTL's own hint for each.
- **Ship's records:** the last versions of every ship (restore one, or recover a ship that was lost), her voyage log
  (jumps, battles, crew, upgrades, damage, written as FTL saves while the station is open), and the sectors she has
  visited in all her journeys.

## Running and building

Java 8 or newer runs it: `Current Build\Federation Home Planet Interface.bat` (or `java -jar "Federation Home Planet.jar"`). On first start it
asks where FTL is (the folder with `ftl.dat`) and where the saves are. Quit FTL before boarding, docking or saving in
the Cargo Bay. Keep the station open while you play: it notices FTL's saves as they're written (for the voyage log and
final victories) and takes stock when you switch back to it.

If your FTL is the Steam version, turn off Steam Cloud for FTL (in your Steam library, right-click FTL, Properties,
General): it can bring back an old copy of a docked ship, or an old FTL profile.

To build from source, double-click `Build The Federation Home Planet Station.bat` and choose 1 (or 2, which also puts a
Quick Link to the Interface on your desktop). The first run downloads a JDK and Maven into `tools\` (once); then every
run puts a fresh `Federation Home Planet.jar` in `Current Build\`. Or with your own Maven: `mvn package` →
`target/Federation Home Planet.jar`.

## Layout

* `src/`, `pom.xml` – the application. `net.blerf.ftl` is Vhati's FTL Profile Editor parser; `homeplanet.*` is the
  station (`core` start-up and settings, `vault` the ships folder, `parser` save editing and blueprints, `ui`, `model`).
* `Current Build/` – where the built jar lands, with its start script (settings and logs stay beside it, untracked).
* `docs/` – the roadmap (decisions and build order) and the transmissions.
* `Tools and Harness/harness2/` – headless regression tests. `Tools and Harness/hw2fhp-converter/` – the one-off
  converter from FTL Homeworld's files (not shipped with the app).

## Where your ships live

Everything the station keeps is in `FederationHomePlanet` inside FTL's saves folder: `ships/`, `junkyard/`, `history/`
(each ship's last ten versions and her voyage log), `storage.sav` (the Cargo Hold), `manifest.xml`, `designs.xml`,
`remodels.xml`, `art/`, `history.log`. Each Immersive career keeps its own fleet in `FederationHomePlanet-Immersive-Easy`,
`-Normal`, `-Hard`, or `FederationHomePlanet-Immersive` for Custom (with its own FTL profile while it isn't in use); ended careers are zipped into `FederationHomePlanet/old-immersive-careers`.
The ship you are flying is FTL's own `continue.sav`, as always. Custom blueprints reach the game through
`Federation Home Planet Mod.ftl`, which the station rebuilds and sends to FTL via Slipstream.

## Licence and credits

GPL-2.0 (the licence of Vhati's parser). See LICENSE and CREDITS.md.

The Space Dock and Cargo Bay backdrops are built when the station starts, from your own copy of FTL's
game files, so none of FTL's art is in this repository. The station's icon is original to Federation Home Planet.
