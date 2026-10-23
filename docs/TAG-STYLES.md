# Tag Styles

Going forward, use these tags when adding to or editing game design documents, such as the roadmap, to show how firmly
heromedel has agreed to each point. Ai-Generated-Proposal is assumed not agreed until heromedel reviews it.

## The tags, strongest first

- **Hard Rule:** work within it; never plan around it or suggest changing it.
- **Decided:** follow it; if a real problem turns up, raise it, but don't change it.
- **Likely:** follow it. Claude can suggest a change if it thinks of a better option, without turning it into nitpicking
  or an argument.
- **Proposed:** heromedel's stated direction, details pending. Use it inside asked-for work when it fits cleanly (see
  below).
- **Ai-Generated-Proposal:** written by Claude, not agreed until heromedel reviews it. Used the same way as Proposed.
- **Open:** not discussed yet; nothing is built on it.
- **Unlikely:** kept on record, but not expected to be added; nothing is built on it unless heromedel raises it.
- **Rejected:** turned down, with a line saying why; never brought back unless heromedel raises it.

## When to set them

A tag comes from how heromedel said it, and Claude sets it from those words:

- "This is a hard rule", "never do X": **Hard Rule**.
- "We are going to go with X": **Decided**.
- "We will likely go with X": **Likely**.
- "I was thinking we could do X, but let's worry about that later": **Proposed**.
- "X is unlikely", "probably not": **Unlikely**.
- "No", "we're not doing X": **Rejected**, with heromedel's reason.
- Something named but not talked through yet: **Open**.
- Anything Claude came up with itself: **Ai-Generated-Proposal**. It moves up only when heromedel's answer agrees to the
  point itself, and only as far as the answer goes. Agreeing to use proposals is not agreeing to what they say:
  - "Should we go with X, and assume that's how it will be going forward?" "Sure." X is **Decided**.
  - "We could do X for now." "I guess." X is **Proposed**, or **Likely** at most.
  - "I've finished the AI proposals for the task; would you like to use these for the next step?" "Yes." They stay
    **Ai-Generated-Proposal**: heromedel agreed to the next step, not to every line in them.

When in doubt, use a weaker tag: a tag never claims more than heromedel said.

Tags follow who wrote what: text heromedel wrote or agreed to is tagged from his words; only Claude's own additions
are Ai-Generated-Proposal.

## How a tag is written

The tag in bold at the start of the point, with the version it was given, so old points can be seen at a glance:

    - **Hard Rule (6.82):** The term "Dry Dock" is never used; the repairs and upgrades are rows on the Refit tab.
    - **Rejected (7.00):** Support for fleets from before 6.0. Nobody plays them; the first release came after 6.0.

When a point's tag changes, its version changes with it.

## Working with proposals

Proposed and Ai-Generated-Proposal points may be used when they're part of a job heromedel asked for, or a system
heromedel agreed to, and they make sense there without complicating the job or adding just to add. They're never a
reason to start something new on their own.

"Ask before writing" means asking before the work starts. Once heromedel has agreed to the work, don't keep stopping
to ask; stopping for a question mid-job is a light guideline, kept for real blockers.

Work built on proposals is no firmer than they are: it carries the Ai-Generated-Proposal tag until heromedel reviews it,
and so does any gap the work filled in itself.

When the work is done, the report opens with the split of what it was based on: count the points used, then give
the percentages, so heromedel can see at once whether it matched what was expected, for example:

    Based on: 20 points. 1 agreed (Hard Rule, Decided, Likely), 16 proposals, 3 filled in as I went
    (5% / 80% / 15%).

Then whatever needs reporting: which proposal points the work leaned on, any concerns about them (or that there were
none), and the gaps it filled in itself.
