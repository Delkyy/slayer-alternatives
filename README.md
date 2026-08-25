# Slayer Alternatives

RuneLite plugin. Answers one question: **what else can I kill for this task?**

Greater demon task? Tormented Demons, Skotizo, K'ril and Tstanon Karlak all count.
The plugin puts that in the sidebar, with the slayer XP each one gives and what you
need to get at it.

Nothing else on the Hub does this. Slayer Helper, Slayer Wiki, Configurable Slayer Task
Overlay and SlayerAIO all answer "tell me about the monster I was assigned" - none of
them answers "what else counts".

## Data

Everything comes from the `== Monster variants ==` table on each `Slayer task/<name>`
wiki page. That table is structured and carries combat level, slayer XP, locations and
requirements per row.

**Do not parse the prose on those pages.** The first version of the puller read the
sentences looking for "also counts towards" and dragged in dungeons, quests, keys and
combat styles as if they were monsters - a greater demon task came back listing
Brimhaven Dungeon, Larran's key and "kill count". The table is the source.

```bash
cd data && python ../tools/pull-variants.py
```

Writes `data/slayer-variants.tsv`. Currently **401 rows across 73 tasks**.

### Not done yet

Seven canonical task pages have no variants table and need doing by hand:
Aquanites, Dark beast, Dragons, Drakes, Fire giants, Suqah, Wall beast.

The scraped data has **not been checked against the wiki by a human**. It is not
authoritative until it has.

## Build

Gradle needs JDK 22 or older here. The default JDK on this box is 25 and both Gradle and
Lombok fall over on it with errors that look like code faults.

```bash
JAVA_HOME="/c/Program Files/Java/jdk-17" ./gradlew build
JAVA_HOME="/c/Program Files/Java/jdk-17" ./gradlew run
```
