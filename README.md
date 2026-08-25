# Slayer Alternatives

RuneLite plugin. Answers one question: **what else can I kill for this task?**

Greater demon task? Tormented Demons are 1,065 slayer xp a kill against a greater
demon's 87. That is 12x, and nothing in the game tells you. The plugin puts every
monster that counts in the sidebar, with its slayer xp, where it lives, and what stands
between you and it.

Nothing else on the Hub does this. Slayer Helper, Slayer Wiki, Configurable Slayer Task
Overlay and SlayerAIO all answer "tell me about the monster I was assigned" - none of
them answers "what else counts".

## What it does

- **Reads your current task automatically.** Taken from the `slayer` RS-profile config
  that core's own Slayer plugin writes, rather than re-deriving it from DBTable rows.
- **Recommends the best thing you can actually reach.** It reads your finished quests
  off the client, so a monster behind a quest you haven't done is dimmed and never
  headlined. Advice you can't act on is worse than none.
- **Searches monsters as well as tasks.** Type `vorkath` and it finds the blue dragon
  task - "what task do I need for this boss" is the same question backwards.
- **Ranks all 116 tasks** by best available xp when you have no task assigned.
- **Points at the wiki's money making guides** for the 56 monsters that have one.
- Everything ships in the jar. **No network calls at runtime.**

## Data

Two pulls, two different pages, then a merge that prefers the better source.

**`tools/pull-tasks.py`** reads the master assignment table on the `Slayer task` page:
**116 tasks**, each with Slayer level, superiors, alternatives, locations, required
items and which masters give it.

**`tools/pull-variants.py`** reads the `== Monster variants ==` table on each
`Slayer task/<name>` page for per-monster detail. **448 rows.**

**`tools/build-data.py`** merges them and cross-checks every number against the wiki's
Bucket API (`infobox_monster`), which is a completely independent source - infoboxes,
not article tables. **434 of 448 rows (96%) carry infobox-verified numbers.** Where the
two disagree the infobox wins: the Nechryael article table says 128 slayer xp and the
infobox says 105, and the infobox is right.

**`tools/build-icons.py`** maps monsters to item icons (see below).
**`tools/build-gp.py`** maps monsters to money making guides.

```bash
cd data && python ../tools/pull-tasks.py && python ../tools/pull-variants.py
cd .. && python tools/build-data.py && python tools/build-icons.py && python tools/build-gp.py
```

### Lessons the hard way

**Do not parse the prose.** The first puller read sentences looking for "also counts
towards" and dragged in dungeons, quests and keys as monsters - a greater demon task
came back listing Brimhaven Dungeon, Larran's key and "kill count". Tables only.

**One cell can hold several monsters.** The dagannoth page puts Prime, Rex and Supreme
in a single cell, one per line, with their xp stacked the same way. Others use `<br>`,
bullets, or commas (the three goblin sergeants). Flattening any of those produces one
fake monster with no xp. `split_stacked()` handles all four shapes.

**Several `{{plink}}`s on one line are alternatives, not one item.** Expanding them
straight gave `Dragonfire ward Ancient wyvern shield Dragonfire shield`.

**Footnotes glue themselves to names.** An `{{efn}}` whose body contains its own links
survives a naive template strip, and "Black dragons" came out with a sentence attached.

### Icons

There is **no NPC image API** - `NPCComposition` gives you model ids, not pictures. So
icons come from items in the game cache that happen to depict the monster: slayer guide
portraits, boss pets, heads and masks, mounted trophies, godwars models, slayer dummies,
rag-and-bone jars. That covers **168 of 355 monsters (47%)**; the rest draw a lettered
chip, because for them the cache genuinely has nothing.

Two rules learned by shipping them wrong:

- **Never fuzzy match against the whole item list.** "Black bear" matched `BLACK_BEAD`
  and "Ice troll" matched `CERT_ROLL`. A near-miss on a word is not a near-miss on a
  monster, so fuzzy matching only ever runs against items that depict monsters.
- **`PICKPOCKET_GUIDE_*` is not monster art.** Those are generic human portraits from
  the thieving guide, and they were landing on dwarves and elemental warriors - a city
  guard's face on a Black Guard, which is a dwarf. A wrong icon is worse than none.

### GP

There is **no per-monster gp value anywhere on the wiki**, and this was checked
properly rather than assumed:

1. `infobox_monster` exposes ten fields - `page_name`, `name`, `combat_level`,
   `slayer_experience`, `slayer_level`, `hitpoints`, `examine`, `max_hit`,
   `attack_speed`, `id`. None of them is money.
2. The 138 `Money making guide/Killing X` pages don't store a profit number either.
   `{{Mmgtable}}` lists inputs and outputs and the wiki computes the hourly figure at
   render time from live GE prices. There is nothing static to scrape.
3. Those guides are per-**setup** anyway. Vorkath alone has three - blowpipe, dragon
   hunter crossbow, dragon hunter lance - with different answers.

So the plugin links to the guide instead of inventing a number. A gp figure that
silently assumes gear you don't own is worse than no figure.

## Build

Gradle needs JDK 22 or older here. The default JDK on this box is 25 and both Gradle and
Lombok fall over on it with errors that look like code faults.

```bash
JAVA_HOME="/c/Program Files/Java/jdk-17" ./gradlew build
JAVA_HOME="/c/Program Files/Java/jdk-17" ./gradlew run
```

## Verified

**48/48 unit tests**, run against the real bundled data file rather than a fixture, so
they prove the shipped data parses and still says what it should. They pin the things
that broke before: no separators left in monster names, no prose in numeric columns, no
duplicate statblocks, no human portraits used as monster icons, and at least 420 of 448
rows still infobox-verified.

Run in a live client and checked by eye: task auto-detection, the search, the
recommendation strip, the locations dropdowns, the icons, and the account gating.
