# Concerns

Design debts heromedel and Claude have talked over: not bugs, not features, but ways the station is built that cost
something and may be worth changing later. Each entry says what it is, why it's that way, what it costs, and what a
change would look like. Started at 4B.97 (branch Cloud-C-Primary-Edit); add the version when an entry is added. An entry
is removed once it's fixed or no longer applies (heromedel, 6.07); the numbers of the rest are kept, since other files cite them.

## 6. An old Steam Cloud copy can come back as a second ship (noted 5.95 by Buggy Boy; heromedel: written up for now; half fixed 5.97)

**5.97:** the check looks in her `versions/` (`Vault.kept`), so a copy identical to one of her kept versions is set aside,
not adopted (VaultT: a copy of her oldest version put back as `continue.sav`; it fails without the fix). Still open: a copy
identical to nothing kept, and a copy of a ship that has left the fleet. The ship mark in the save (below) is held back for
now (heromedel, 5.97).

**What it is.** When the station opens and finds a `continue.sav` that no boarded ship owns, it asks whether Steam
Cloud brought back a copy of a ship it already has (`Vault.cloudCopyOf`, Vault.java:1142). It compares the file with
each docked or Junkyard ship's current save, and is meant to compare it with her kept versions too, which is what
catches an old copy. Since 5.69 a ship's versions are in `versions/` inside her folder, but the check still lists her
folder itself (`historyOf`), so it finds her current save again and never sees a version. A copy older than her current
save isn't recognised, and it's adopted as a stranger: the same ship twice, and her crew twice in the register.
Confirmed with a scratch test at 5.95: the Test Kestrel docked, a Cargo Bay change to her (5 scrap), the old
`continue.sav` put back; on opening the fleet went from 5 ships to 6, with two Test Kestrels, the copy boarded as a
stranger. Ships that have left the fleet (the Museum's, traded away, lost) aren't checked at all, so an old copy of one
of them would come back the same way (from reading the code, not tested). VaultT's check passes because its copy is
identical to her current save.

**What it costs.** It's rare: Steam Cloud has to restore a `continue.sav` the station already took in. When it does
happen, it quietly doubles a ship and her crew, the kind of damage that's hard to notice and to undo (the player would
have to decommission the double by hand).

**What a change would look like.**

- The check pointed at her versions (`Vault.kept`: her ordinary versions and the copies kept for a reason). This is
  exact and quick, but it only catches a copy that is identical to something the station kept.
- heromedel's check (5.95): whichever has fewer jumps is older. A copy that is hers, with the same or fewer jumps
  than her current save, is an old copy. "The same" matters: a Cargo Bay change adds no jump, so the copy in the test
  above has exactly as many jumps as her current save. This catches copies the station never kept, but jumps only say
  which is older, not whose save it is. Without an id, "is it her?" has to go by name, class and crew (name and race),
  so that a New Game in the same ship, with FTL's default name and 0 jumps, isn't taken for an old copy of one adopted
  earlier.
- With ship ids in the save (heromedel, 5.95: probably coming), the guessing goes. The save's state variables (FTL's
  own list of names with a number each, kept for the run) survive everything FTL does (Buggy Boy's crew id test, 5.80). A mark written on Board, `fhp.ship.<career>.<id> = <board count>`, says whose ship a save is, and
  the board count or her jumps say which copy is older. It covers ships that have left too, since the id names her
  folder in the memorial. The copy is then put aside in her folder (as `cloud-`), never adopted.

Whichever way it's done, a harness check: dock a ship, change her in the Cargo Bay, put her old `continue.sav` back,
open the station. There must be one of her, with the copy put aside. Add a second check for a copy that isn't
identical to anything kept, and a third for one of a ship that has left. The scratch test is CloudCheck on Buggy
Boy's bench.

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

