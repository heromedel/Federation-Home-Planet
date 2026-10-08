~ Incoming transmission from The Federation Home Planet ~

Despite the ongoing war with the rebellion, the Federation has restored its long-range trade and communication network, carried by official stores and stations across the sectors.

From the Home Planet, the Federation can once more reach beacons in many star systems: moving goods and crew between ships almost instantly, refitting hulls in its dry docks, and commissioning new ships wherever a captain needs one.

Welcome home, Captain, and godspeed.

-----

What is it? It is an external hub that turns FTL's runs into a campaign or career. It is "The home base FTL never had."

Federation Home Planet (FHP)
An external application for use with FTL: Faster Than Light that allows you to keep a fleet between runs: Switch ships, start a new journey with the same ship, or trade gear, crew and scrap through a storage hold or between ships, shop at any other ships' beacons, refit, remodel, design and commission new ships. New blueprints work with Slipstream.

Slipstream can be downloaded through this application and choosing which mods to bundle with your new blueprints can be handled inside of it as well.

You can board a ship, go to the Cargo Bay, trade with other ships or the station's storage, buy and sell parts based on stores your ships are near and then launch FTL with or without Steam without even leaving the app and only hitting refresh when you come back. 

-----

Options to: 
Restrict docking and trading to only when you are at a store beacon.
Limit Commissioning new ships to those unlocked in game.
Limit Commissioning Custom ships based on Vanilla Designs to only those unlocked in game.
Rename Ships or Crew Members
Decommission Ships to the Junkyard where you can Scrap them removing the parts or destroy them.
Refit or Remodel ships completely, moving systems, rooms even doors. 
Design a ship from scratch using your own art or the ships from the game. 
     The Federation Home Planet then bundles the new blueprints into a companion mod, 
     and sends them through Slipstream to patch the blueprints and any other .ftl mod you choose into the game.
And More. 

-----

## What it does

- **The Space Dock:** your fleet between runs. Board any docked ship, send her on a New Journey from sector 1 with her
  crew and cargo, or decommission her to the Junkyard. Launch FTL from the station, in its own window or (an option on
  Windows) docked inside the station's.
- **The Cargo Bay:** trade crew, weapons, drones, augments, systems and supplies with the Cargo Hold (the station's
  warehouse) or another docked ship; shop at the stores your ships are docked at; repair and upgrade in the Dry Dock;
  refit, retrofit, remodel or overhaul a ship's layout. A ship at FTL's System Limit (8 systems) can still have one
  fitted, as a custom work order (scrap, and reputation as well).
- **The Junkyard:** salvage a decommissioned ship, scrap her for parts, trade her in, auction her off, or destroy her;
  buy derelicts to make fly again, and salvaged parts.
- **Expeditions:** send crew from the Cargo Hold on jobs in the sectors on offer. They come back with scrap, prizes and
  recruits, hurt (a while in the infirmary), or not at all; some are taken captive, and their captors write asking for a
  ransom. Hire Crew posts for volunteers.
- **Captain's Quarters:** a day's rest at the station.
- **Long Range Comm.:** trade with another commander's Home Planet Station over the local network, a virtual LAN or
  the internet: items, supplies, crew and whole ships, Sandbox with Sandbox and Immersive with Immersive (any level,
  unless a career keeps to its own); any two commanders can hail and talk. Both commanders build the offer and accept
  it; any change withdraws acceptance. Goods arrive in the Cargo Hold, ships at the Space Dock. Nothing is open to the
  network until you open your hailing frequencies; stay powered up to be hailed from any screen, send a message to a
  commander's inbox (or, marked priority, onto their screen; one who's away gets it from the Outbox when they're back),
  send them a shipment of goods with a message, and block a commander you'd rather not hear from.
- **The shipyard:** commission any unlocked ship, a remodel, or a ship you designed yourself. New blueprints are sent
  to FTL via Slipstream. **Plead for New Ship** (Other...) asks The Federation Home Planet for one, paid for with the
  Cargo Hold or answered for with your reputation; the Relief Ship Type A is always on offer.
- **House rules** (Settings): trading and New Journey only at a store beacon, commissioning that costs scrap, locked
  ships that can't be commissioned, selling supplies and systems, a free commission for each ship you unlock, and
  what happens after a final victory (rescue the ship, a reward of her value, or nothing).
- **Immersive Mode:** four careers (Easy, Normal, Hard, and Custom, which sets each rule's level), each with its own
  fleet and FTL profile. The Federation Home Planet's rules are locked, your reputation lifts you through the ranks
  (Colonel, Commander, Captain, Commodore, Admiral), and the inbox brings commission orders, promotions, achievement
  rewards, a stipend, and letters from the people of the Federation. Switch Game Mode (Settings) moves between Sandbox
  Mode and the careers at any time, and ends a career.
- **Reputation:** your standing with The Federation Home Planet (always in Immersive Mode; a Settings rule in Sandbox
  Mode), in gold on the Space Dock: earned by sectors, scrap, ships defeated (rebels more), good outcomes, FTL
  achievements and driving off the Rebel Flagship; lost by crew killed, ships lost in action, being caught by the rebel
  fleet and bad outcomes (never in the last stand of sector 8). Click it for the Career Reputation Log.
- **The Federation Museum:** every ship that won in the Hall of Victors (preserved in the museum, still in service,
  honoured in memory, or lost in action later), and the ships lost in action in the Memorial: her record, honours,
  crew, voyage and loadout, an epitaph of your own, and a picture of the exhibit to save.
- **The logs:** the Captain's Log tells the career day by day; the Crew Log follows every crew member, from where they
  joined to where they are now, and promotes those whose skills have earned a rank; the Station Log keeps everything the station did. Each ship keeps her own log (jumps,
  battles, crew, upgrades, damage, written as FTL saves while the station is open).
- **Ship's records:** the last versions of every ship (restore one, or recover a ship that was lost), her log, and the
  sectors she has visited in all her journeys. **Commission's Locked ships list:** the ships your FTL profile hasn't
  unlocked yet, with FTL's own hint for each.
- **The station's words** (the log's lines, the letters, the expedition reports and the rank letters' accolades) are in
  `lore/` beside the program, and a copy of any of them there changes what the station says: `lore/readme.txt` says how.

## Running and building

Java 8 or newer runs it: `Current Build\Federation Home Planet Interface.bat` opens it without a console window, using the
Java the Construction Yard gathered or the one on your computer (or run `java -jar "Federation Home Planet.jar"`). On first start it
asks where FTL is (the folder with `ftl.dat`) and where the saves are. Quit FTL before boarding, docking or saving in
the Cargo Bay. Keep the station open while you play: it notices FTL's saves as they're written (for the ships' logs and
final victories) and takes stock when you switch back to it.

If your FTL is the Steam version, turn off Steam Cloud for FTL (in your Steam library, right-click FTL, Properties,
General): it can bring back an old copy of a docked ship, or an old FTL profile.

To build from source, double-click `Build The Federation Home Planet Station.bat` and choose 1 (2 also puts a Quick Link to
the Interface on your desktop; 3 launches the station once it's built). The first run downloads a JDK and Maven into `tools\` (once); then every
run puts a fresh `Federation Home Planet.jar` in `Current Build\`, then offers to launch it. Or with your own Maven:
`mvn package` → `target/Federation Home Planet.jar`.

To update, use **Check for Updates...** (Settings, About). It compares your version with the main branch on GitHub; if
main is newer, Update Now puts the new source files in place (only the program's own: `tools\`, your settings, logs
and fleets aren't touched), closes the station, rebuilds it and opens it again. If the build fails, the old files go
back and the version you had stays in `Current Build\`. A git checkout (GitHub Desktop) is updated by fetching
instead.

Something wrong, or an idea? **Send Feedback** (Settings, About) opens the feedback form with your version filled in.

## Layout

* `src/`, `pom.xml` – the application. `net.blerf.ftl` is Vhati's FTL Profile Editor parser; `homeplanet.*` is the
  station (`core` start-up, settings and the words in `lore/`, `vault` the fleet on disk, `parser` save editing,
  blueprints, letters and expeditions, `comm` Long Range Comm., `convert` bringing a fleet from before 6.0 across,
  `ui`, `model`). The station's own words are in `src/main/resources/homeplanet/resource/lore/`.
* `Current Build/` – where the built jar lands, with its start script (settings, logs and `lore/` stay beside it, untracked).
* `docs/` – the roadmap (decisions and build order), the 6.0 overhaul, the events the station logs, the letters, the
  style guide, the FTL lore the words are checked against, bugs and feedback.
* `Tools and Harness/harness2/` – headless regression tests. `Tools and Harness/hw2fhp-converter/` – the one-off
  converter from FTL Homeworld's files (not shipped with the app).

## Where your ships live

Everything the station keeps is in `FederationHomePlanet` inside FTL's saves folder:

* `shipyard/` – a folder for each docked ship (`<Name>.<id>`): her record, her save, her log, her crew's files and her
  last versions. `junkyard/` the same for the ships decommissioned there, and `memorials_and_records/` the ships and crew
  who have left the fleet, remembered.
* `cargohold/` – the Cargo Hold: what it holds, its crew and its stored systems.
* `expeditions/`, `infirmary/`, `captives/` – the crew away, the hurt and the taken.
* `logs/events.log` – the station's log, every entry in two lines: one for the program, one to read.
* `designs.xml`, `remodels.xml`, `art/` – your designs and remodels, shared by every fleet.
* The career's own small files (its clock, rank, reputation and the rest), one for each concern.

Each Immersive career keeps its own fleet in `FederationHomePlanet-Immersive-Easy`, `-Normal`, `-Hard`, or
`FederationHomePlanet-Immersive` for Custom (with its own FTL profile while it isn't in use); ended careers are zipped
into `FederationHomePlanet/old-immersive-careers`. A fleet from before 6.0 is brought into this layout the first time
it opens, and a zip of it as it was is kept beside its folder (`…-before-conversion-…`).
The ship you are flying is FTL's own `continue.sav`, as always. Custom blueprints reach the game through
`Federation Home Planet Mod.ftl`, which the station rebuilds and sends to FTL via Slipstream.

## Licence and credits

GPL-2.0 (the licence of Vhati's parser). See LICENSE and CREDITS.md.

The Space Dock and Cargo Bay backdrops are built when the station starts, from your own copy of FTL's
game files, so none of FTL's art is in this repository. The station's icon is original to Federation Home Planet.
