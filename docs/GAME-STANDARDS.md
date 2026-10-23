# Game Project Standards

## Game Design Projects Standard Rules

- Plans get letters, steps get numbers, and questions get numbers with a Q in front (Q1, Q2).
- Don't push to main without being told. Once Claude has its own branch, it can push to that branch when things check out.
- Don't waste usage on unnecessary checks and tests, but do check for bugs where it's actually needed.
- Don't suggest edits or solutions for code or features you haven't looked at.
- Don't repeat Hero's explanations or ideas back as if you came up with them, and don't say something will work better than he thought. He's usually well aware of how the design interacts with other systems.
- The workflow is always: discuss first, then plan, ask before writing, check, then push.
- When building pieces of the game, consider balance and how they tie into other systems and features.
- More logs, debug logs and admin commands are better than fewer, especially when they let Hero see real play.

## Elegant Code Principles

1. **A class does what it's supposed to do, nothing more and nothing less, and its name should make that obvious.** A class called `Input` implies it handles all input, so keeping all input there is fine no matter how big it gets. If it starts handling what happens *after* the input, that part probably belongs somewhere else. Size alone is never a reason to break up a class, and splitting one for no other reason usually isn't a good idea.
2. **Each function's name makes clear what it does, and functions are used cleanly to avoid duplicate code.** Merge duplicates when they mean the same thing, not when they only look alike. Code that looks the same now but changes for different reasons later shouldn't share a function.
3. **Someone reading the main class from top to bottom should understand the program's flow without jumping around or reading other classes.** It reads like a table of contents. A reader opens another class only to see *how* a step works, never to follow *what* happens next.
4. **Unused code is labeled, commented out or removed.**
5. **Special cases dissolve.** Prefer a way of solving the problem where edge cases become ordinary cases, rather than adding a separate branch for each one.
6. **Clever only with a purpose.** A trick is welcome if it makes the problem simpler, but not if the reader has to stop and decode it.
7. **Prefer human readability when possible.** Shorter or slightly faster code is fine if it's clearly labeled and commented. Names are spelled out in plain words, with no cryptic abbreviations, and say exactly what the thing is or does. A name should never imply something the code doesn't do, such as calling crash protection a "journal." Even where jargon uses a term that way, prefer the plain name a reader would expect, like `CrashProtection`.
8. **Write it elegantly now, not later.** When writing or adding code, put things where they belong from the start. Don't put input handling in main planning to move it into an `Input` class later. Small moves and renames each build are cleaner than untangling or rewriting the code later. Elegant early means right place, not more structure. Focus on making the part of the engine you're building now elegant, rather than sketching many crude structures for later.
9. **Consistency.** The same kind of thing is done the same way everywhere: same naming patterns, same structure, same way of handling similar problems.
10. **Extend what exists before building new.** If a system already does something close, extend or share it rather than building a parallel version.
11. **One owner for each piece of state.** Every piece of data has one home, and everything else asks that home rather than keeping its own copy.
12. **Comments explain why and what.** Say what a section does and why it's done that way, and keep comments up to date when the code changes.

## Heromedel's Game Design Philosophy

### Tutorials & Onboarding

- Never make it obvious that you are in a tutorial and don't take hours to make the majority of features available. Teach through play.

### Information & Feedback

- Too much info makes a player feel like they need a college class to be good at a game. Not enough info is when the player doesn't know or cannot figure out why their choices were good or bad.

### Failure & Loss

- Loss or death should be due to attrition, not instant.
- The player should feel they lost because of their own choices or skill, not due to being punished, especially not unavoidably or unfairly.

### Engagement & Discovery

- Players are far more likely to stay engaged if they always have something to experiment with. Especially if it's something new.
- Boring items and effects typically are unnoticeable; ones that players get excited about feel game changing to them.

### Progression

- Gameplay loops and features that are central during early game can be automated or moved out to the side as new gameplay unlocks later in the game. In fact the late game could be almost like an entirely different game from the early game. This is not mandatory, but neither should the game cling to systems no longer relevant to late gameplay.

### Items, Equipment & Crafting

- If a crafting ingredient has only one use, it has no point in being in the game. Further, when adding a new ingredient, see if an existing one could already work.
- Social/cosmetic slots and skins will never feel as important to a player as real equipment and thus should be avoided in games that have real equipment. In other words, items with real effect should not be hidden by items with no effect just to "look cool," unless it makes plausible sense, like a robe of armor, not fake glittering wings or weak/different armor over powerful armor.

### Systems & Code

- Elegant systems share as much clean and concise code as they can; it doesn't just avoid bloat, it encourages consistency.
- Complex features in a game can feel nice and simple if made elegantly.
- Making accomplishing things too simple or automatic can actually remove the drive to play and the immersion. Unlike elegant systems, having buttons that auto-do everything for a player may not improve the game.

### Immersion, Atmosphere & Art

- Immersion and atmosphere can make or break a game.
- Although art style is important and should be seen from the perspective of art, it should not be focused on to the point of limiting the gameplay, i.e. graphics over a crappy game is still a crappy game.

### Content & Revision

- Don't add more solely just to be adding more.
- A revision should focus on refinement, not on adding and reinventing what is already working or loved by players.
