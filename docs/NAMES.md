# Crew names by race: style guide

FTL gives every race the same human names. The Home Planet Station names the crew it brings aboard by race: the
starting crew at Commission, volunteers, recruits and hires (and the dice in Rename crew). Crew that FTL hires in its
own stores, or meets in its events, keep FTL's names: FTL has one list for everyone, and a mod can't split it by race.

The lists are written by hand from this guide (McCarthy's, with heromedel's choices; 6.28), finished: every rule
below, the Slugs' letters, the Engi's hex, every female form, is already applied in the file. The station only picks
a name that fits the crew member's race and sex; it changes nothing. The lists are words like the station's others:
the jar holds the defaults in `resource/lore/names/`, and a copy in `lore/names/` beside the jar overrides them. A
test holds every list to this guide.

## Every race

- **Fits the lore.** Check a name against `docs/LORE_COMPONENTS.md` like any other word: nothing that contradicts it.
- **Nobody else's, mostly.** No real people (the unit, not the scientist: Volt, never Volta) and no brands. An
  occasional easter egg from another game or story is welcome (Zerg, Adamantium), as a nod, never a whole set of them.
- **Short.** Twelve letters at most where it can be, a two-word name fourteen: a rank adds up to five ("Cmd. "), and
  FTL cuts long names off on screen.
- **Never starts with a rank** (Sgt., Lt., Maj., Col., Cmd., Cpt.), with or without the point. "Cap Acitor" is fine;
  it is not Cpt. Acitor.
- **Capitals:** each word starts with one, the rest small (except the Engi's hex, below).
- **Namesakes are fine.** Two crew can share a name, as they always could; nothing has to be unique.
- **M, F or B.** FTL marks every crew member male or female, whatever the race, and the letters say "he" or "she"
  from it. Every name carries one of three tags: M (male), F (female) or B (both: Jesse). A male crew member gets an
  M or B name, a female an F or B one. Where a race makes its female names with an ending or a mark (below), the plain
  name is M and its female form, written out beside it, is F.

## Humans

FTL's own names, and more from public-domain lists: first names from the US Social Security baby-name data, last
names from the US Census surname list. Tagged as modern American practice has it: the baby-name data counts each name
by sex, and a name given often to both (Jesse, Riley, Avery) is B. Settings, Human Name Gen:

| Choice | Gives |
|---|---|
| Normal | as FTL mixes them: mostly a first name, sometimes first and last |
| First Names Only | a first name |
| First and Last Always | a first name and a last name |

## Zoltan

Electricity. Short words first: Zolt, Bolt, Jolt, Amp, Volt, Ohm, Watt, Arc, Flux, Coil, Spark. A long word is cut
down or split into a first and last name (Resistor is Resi, Capacitor is Cap Acitor).

Female: the name ends in Y (Volty, Ampy), or in I or IE where a Y reads wrong ("Ohmy" reads "oh my", so Ohmie). A
name already ending in Y, I or IE stays as it is, and is B.

## Rock

Rocks, not single crystals (those are the Crystal's). Preferably not plain English: Basalt, Gabbro, Schist, Gneiss,
Breccia, Tuff, Scoria, Pumice, Dacite, Diorite, Andesite, Rhyolite, Obsidian (a glass, not a crystal), Slate. Not
Agate (a quartz), not Tiger Eye (plain English).

Female: sorted by hand, by how a name sounds, and the mineral endings: -ite (the way minerals are really named) and
-ine. Some come out real (Pumicite is a real rock), the rest plausible; the ending that reads best is the one used
(Basaltine, Gabbroite). Where neither reads well (Breccia), the rock stays M only.

## Crystal

Crystals, minerals and their formations: Quartz, Beryl, Zircon, Galena, Spinel, Pyrite, Calcite, Fluorite, Selenite,
Geode, Druse, Prism, Lattice, Facet. The Crystal are the ancient ancestors of the Rock (LORE_COMPONENTS 12): the two
lists are kin but share no names.

Female: sorted by hand (Onyx and Rose Quartz are F), and the same endings as the Rock's, -ite and -ine (Quartzite and
Jadeite, both real). A name already ending in -ite (Pyrite) takes -ine or stays M only.

## Mantis

Made-up words that sound like insects (clicks, buzzing, scraping), never an insect's name: Skrit, Zerg, Cuttler,
Chitch, Vrask, Kessik, Ix. The one real insect allowed is Scarab. Nothing soft or human-sounding (not Thrum).

Female: one of IX, XA, IXI or IXA on the end (Skritix, Zergxa, Vraskixi, Kessikixa), the one that reads best, spread
over the list so all four are used. A name ending in X takes only the rest of an ending (Ix is Ixa or Ixi).

## Engi

One four-letter English word, capital first, written in hexadecimal: each letter is two hex digits (its ASCII code,
capital hex), the word split in half, so every Engi name is the same shape, `xxxx-xxxx`.

| Word | Name |
|---|---|
| Byte | 4279-7465 |
| Kilo | 4B69-6C6F |

Words of their trade: Byte, Gram, Code, Data, Node, Core, Gear, Bolt, Wire. Four letters only, never longer.

Female: the hyphen is a tilde, so a female Byte is 4279~7465.

## Lanius

Metals, alloys and materials, the longer and heavier the better, one word or two: Wrought Iron, Carbon Fiber,
Damascus, Titanium, Adamantine (and the easter eggs, Adamantium and Captain America's Vibranium). Not the plain short
ones (Steel, Tin). Not Mercury (a god, and a planet).

Female: sorted by hand (Quicksilver is F), and the endings -ia (the old Latin way metals were named: Titanium is
Titania, Ferria, Cobaltia, Chromia) and -ine. Not -ite: it sounds like a mineral, not a metal.

## Slug

Two kinds of name, sorted by hand: slimy ones (Slime, Slither, Crawls, Snail, and Flowers for her) and a salesman's
(Glim, Mirrow, Squill, Shimmer, Glib). Both are drawn out the same way, by these rules, in order:

1. **Soft consonants double:** L, M, N, W, Z, V and H, every one of them. The rules never add an R (heromedel). A
   double the word already has (the rr of Mirrow, the mm of Shimmer) stays as it is; a W at the end of a name stays
   single.
2. **No vowel drags**, and a word's own double vowels go single. The one exception: the vowel after SH draws out
   instead (Shimmer is Shiimmer), because a doubled SH is a snake.
3. **Two-letter sounds never double:** SH, TH, CH, PH.
4. **No three doubles in a row:** two doubled letters side by side is the most; a third stays single.
5. **No snake:** an S, or a C said as S, doubles only when the letter after it doubles too. Alone, it stays single.
6. **Hard stops never double:** G, K, T, B, P, D, Q, X, and a C said as K.

| Word | Name | Why |
|---|---|---|
| Slime | Ssllimme | the S doubles because the L after it does |
| Slither | Ssllither | the TH stays as it is; R never doubles |
| Crawls | Crawwlls | the C is hard; the S would be a third double |
| Snail | Ssnnaill | |
| Flowers | Fllowwers | F isn't a soft letter; no R is added |
| Glim | Gllimm | |
| Mirrow | Mmirrow | its own rr; the final W stays single |
| Shimmer | Shiimmer | the vowel after SH draws out |
| Glib | Gllib | |
| Squill | Squill | the S is before a hard Q |
