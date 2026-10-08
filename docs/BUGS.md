# Bugs

Known bugs not yet fixed: what happens, where, and how it was found. Add one with the version it was found in; when it's
fixed, say so with the version, or move it to a handoff (`docs/HANDOFF.md`).

## 1. A departed ship's "newest" version can be the wrong one when her versions share a file time (found 6.01)

**What happens.** For a ship that has left the fleet, the station takes her newest kept version as her last save
(`Vault.newestKept`, read by Reputation's ships defeated in service, recovering her, and the like). It orders her versions
by file time and only falls back to their names when two times are exactly equal. When her versions all carry the same
time to the second but differ by microseconds (a fleet folder copied with a tool that doesn't keep file times, or one
unzipped), which one counts as newest comes down to chance.

**How it was found.** Comparing heromedel's Sandbox fleet under 6.00 and 6.01: the Shrapnel R.U.'s four versions all read
2026-10-05 19:20:52 in the original folder. Two copies of the same fleet picked different versions as her newest, and the
ships defeated in service came out 52 in one and 50 in the other (the reputation total the same). Running both again
flipped the result, so it was the copies, not either version.

**The fix.** Order versions by the stamp in their names (`yyyyMMdd-HHmmss`, written when the version was kept), and use
the file time only for a name without a stamp. Small, in `Vault` (the `OLDEST_FIRST` comparator and `ShipStore.versions`'
callers).
