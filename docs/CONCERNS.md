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

## 8. Undos written by hand (noted 6.11)

**What it is.** A few actions still write the Cargo Hold first and something else after, and put the hold back by hand if the
second step fails, against the Journal rule: Scrap and Sell (`vault/SpaceDock`: the hold, then the ship's removal), a commission
paid from the hold (`ui/CommissionDialog`) and a derelict bought (`parser/Derelicts`), both through `Vault.payFromStorage` and
`refundStorage`. New Journey did the same until 6.11, when its fee and her save became one protection note.

**What it costs.** If the station stops between the two steps, or the putting back fails too, the hold keeps what it was given
while the ship keeps it as well: a scrapped or sold ship's crew would be in both places (cloned). Rare: it needs a failure at
that moment.

**What a change would look like.** The ship's removal (`Vault.remove`) written into the same note as the hold, as Board does
with its files; the commission and the derelict paid in the same transaction as the ship they bring.
