# Slayer Alternatives

RuneLite plugin. Answers one question: **what else can I kill for this task?**

Greater demon task? Tormented Demons, Skotizo, K'ril and Tstanon Karlak all count.
The plugin puts that in the sidebar, with the slayer XP each one gives and what you
need to get at it.

Nothing else on the Hub does this. Slayer Helper, Slayer Wiki, Configurable Slayer Task
Overlay and SlayerAIO all answer "tell me about the monster I was assigned" - none of
them answers "what else counts".

## Data

Two pulls, two different pages.

**`tools/pull-tasks.py`** reads the master assignment table on the `Slayer task` page.
This is the authoritative list of what can be assigned: **116 tasks**, each with its
Slayer level, superiors, alternatives, locations, required items, other requirements and
which masters give it. 66 tasks have alternatives, 31 have superiors.

**`tools/pull-variants.py`** reads the `== Monster variants ==` table on each
`Slayer task/<name>` page for the per-monster detail - combat level, slayer XP,
locations, requirements. **401 rows across 73 tasks.**

**Do not parse the prose on those pages.** The first version of the puller read the
sentences looking for "also counts towards" and dragged in dungeons, quests, keys and
combat styles as if they were monsters - a greater demon task came back listing
Brimhaven Dungeon, Larran's key and "kill count". The tables are the source.

```bash
cd data && python ../tools/pull-tasks.py && python ../tools/pull-variants.py
```

### GP rates - what's actually available

There is **no GP figure on any monster**. Checked the Bucket API directly: the
`infobox_monster` bucket exposes only `page_name`, `name`, `combat_level`,
`slayer_experience`, `slayer_level`, `hitpoints`, `examine`, `max_hit`, `attack_speed`
and `id`. Nothing about value, loot or profit.

GP exists in exactly one place: the **Money making guide** pages, 138 of which are
`Killing <monster>`. Those carry a real computed hourly profit (frost dragons off-task:
1,953,997/hr) but:

- they are **per-guide, not per-monster** - they assume a specific gear setup, and the
  frost dragon one prices in Torva, an Ultor ring and a dragon hunter lance
- there is **no bucket** for them (`bucket('mmg')` does not exist), so the number has to
  be scraped out of rendered HTML
- coverage is partial - 138 guides against 116 tasks with far more monsters than that
- the figure moves with GE prices, so a bundled copy goes stale

So GP is possible for a subset, with caveats, and it is a second phase. Slayer XP is
solid, universal, and already pulled.

### Not done yet

The scraped data has **not been checked against the wiki by a human**. It is not
authoritative until it has.

## Build

Gradle needs JDK 22 or older here. The default JDK on this box is 25 and both Gradle and
Lombok fall over on it with errors that look like code faults.

```bash
JAVA_HOME="/c/Program Files/Java/jdk-17" ./gradlew build
JAVA_HOME="/c/Program Files/Java/jdk-17" ./gradlew run
```

## How it works

The panel shows your current task automatically - read from the `slayer` RS-profile
config that core's own Slayer plugin writes, rather than re-deriving it from the
DBTable rows. Search finds any task, and searches **monsters too**, so typing `vorkath`
finds the blue dragon task. "What task do I need for this boss" is the same question
backwards.

`slayer-data.json` ships inside the jar (147KB). **No network calls at runtime.**

Superiors are listed but greyed out and never set a task's headline XP - they're rare
spawns, not something you can choose to go and farm.

## Verified

Compiles clean, **15/15 unit tests** - and they run against the real bundled data file,
not a fixture, so they prove the shipped data parses and matches.

**Not yet run in a game client.** The panel has never been looked at, and task
auto-detection has never fired against a real slayer task.
