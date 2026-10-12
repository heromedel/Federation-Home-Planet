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
