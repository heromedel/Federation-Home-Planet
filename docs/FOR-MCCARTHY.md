# For McCarthy

Requests for McCarthy's session (the words in `lore/` and the expeditions' outcomes), left by other sessions for
heromedel to pass on. Mark one done with the version when it's in.

## From the Send Feedback sheet (10/16, at 6.65)

1. **Done 6.69.** **"Twice the shields went down, ..."** (an expedition outcome). Shields go down far more than twice in an FTL fight,
   and starting the sentence that way is abrupt. The feedback's suggestions: "The shields went down twice," or "Every
   time the shields went down,".
2. **Done 6.70.** **Expeditions bringing back supplies.** An expedition could occasionally bring back a missile, fuel or a drone part,
   especially from salvage operations, or from derelicts that didn't make it back.

## From the code review for 7.0 (Prime, 6.87)

Four wordings the review turned up, left to McCarthy's judgement (none is a bug):

1. **"a Engi crew volunteer", "a Oxygen system"** (`parser/Transmissions.java`, `words()`: a reward's words in a letter). The article
   is always "a", whatever follows; `model.Words` has a/an if wanted.
2. **"PLAYER_SHIP_DESIGN_3_HP (the The Spear of Artemis design)"** (`parser/Retrofit.java`, `known()`): a design whose name
   starts with "The" gets a second one. `parser.ShipNames.the` knows which names carry their own.
3. **"Answered the posting: Bob (engi) joined"** (`parser/Expeditions.java`, the HIRE entry) and **"Bob (engi), rescued on an
   expedition, signed on"** (`parser/Assignments.java`, `accept`): the race in the human line is FTL's id (engi, rock), not
   the people's name (`model.Crew.racePeople`).
