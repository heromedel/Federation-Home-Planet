# Bugs

Known bugs not yet fixed: what happens, where, and how it was found. Add one with the version it was found in; when it's
fixed, remove it (heromedel, 6.08), or move it to a handoff (`docs/HANDOFF.md`).

## FTL docked, attached to the station (found 6.56, Buggy Boy, from the code; Windows only, not tried there yet)

- **F11 or Alt+Enter while attached.** Borderless full screen closes the station's window and opens a new one while the old
  one still owns FTL's (`MainFrame.setBorderless`: `dispose()` first, `FtlDock.stationReopened` after). The new one owns it
  again, but FTL's window isn't let go of before: if Windows takes an owner's windows down with it, FTL's goes. Fix: let go
  of FTL's window before the `dispose()`, attach it again after.
- **A failed attach still counts as attached.** `Win.own` returns a message, not whether it worked: when the station's window
  has no handle at that moment (that same F11 blink), FTL isn't owned, but `attachedRun` stays true, so the station stops
  lifting FTL and cutting the viewport for the run, and FTL can sit hidden behind it. Fix: fall back to the own-window way
  when the attach didn't take.
- **Not confirmed:** attached, the station's pop-ups and FTL share an owner; clicking FTL while a pop-up is open may bring
  FTL over it, the station locked until the pop-up is found. Needs a try on Windows.

## Docking FTL again after the station gave it back (found 6.60, heromedel on Windows; likely unimportant)

**Likely unimportant (heromedel):** it takes a lot of back and forth to make it happen, and it may act differently on
other systems. Leave it unless players run into it.

- **Steps:** launch FTL in its own window; with the attached way chosen, dock it (it docks); close the station (FTL gets
  its own window back, as 6.57 has it); start the station again and dock FTL. The viewport shows Unpause, and neither
  clicking it nor Refresh brings FTL back.
- **Not the cause:** FTL running before the station opens. Launching FTL with the station closed, then starting the
  station and docking it, works. So it's a window the station docked once and handed back on closing (`MainFrame.closeNow`
  calls `FtlDock.release`: its title bar and border back, the station's ownership taken off, the viewport's hole closed),
  docked again by a new station. Something of that handover stays with it.
- **Where to look:** the station's log from that run, with debug on, says what Windows answered at each step: the release
  (`FTL undocked: ...`), the new attach (`FTL docked, attached: ...`) and `tuckUnder` (`not, FTL is behind another
  program's window (Unpause shown)`). 6.60's bring-to-front once docked didn't change it.

## The crew register with identical namesakes on two ships (found 7.02, Prime, in the harness; not seen by a player)

- **Seen:** a test world whose random names gave two crew members the same name, race and sex (one aboard each of two ships,
  fresh crew with the same service record and no colouring). CrewT's M check removes one from her ship: the register marked
  the *other* one missing. A rebuilt world (different names) passed. The pairing (`CrewRegister.score`) ties on everything
  but the place, which should break it; worth a look at why it didn't (the records' `tints` or counts?). Nothing is lost
  either way (both are tracked, one id swapped), so it waits for Buggy Boy.

