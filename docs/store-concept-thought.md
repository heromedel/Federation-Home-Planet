# Store concept: a thought (heromedel, 6.64)

Not decided, not planned: a thought to come back to.

## From heromedel

Some beacons, especially in CE, have events specifically about being at a station of some kind. If these events were
kept in a list and checked for, it could count as being at a store for the Cargo Bay.

The only issue is whether the event would actually make sense to have a connection back to a Federation station. To be
fair, some of the standard stores might not, but for example refueling stations at an exit beacon would seem to make
sense. Plus it could be a setting, like:

    Federation Station Access: Stores / Stores and Some Events / Stores and all Store like Events

That might actually be a bad example of the settings, but it would be something close to that, like:

    Stores / Stores and Refueling Stations / Stores and Station like Events

In other words: trading with a pirate, maybe not; a black market dealer on a planet, maybe; a CE station event,
probably.

The assumption for how it works now is that all official stores have a Federation connection; in fact it's even
mentioned in the lore.

## Notes

- How it works now: a ship may trade at the Cargo Bay when the beacon she's at has a store (`SaveHelper.isAtStation`:
  the save's current beacon carries a store), or with the store requirement off. The New Journey's own station rule
  asks the same.
- The lore: README.md's opening, "the Federation has restored its long-range trade and communication network, carried
  by official stores and stations across the sectors."
- What the station could check: the save names the event at her beacon (the encounter's text id), as the distress
  signal check already reads it (6.21), so a list of event ids, one list for each level of the setting, would be
  enough. Lists for CE would be read from the player's own patched ftl.dat event files by id.
