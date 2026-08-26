# Slayer Alternatives

Shows what else counts for your current slayer task, and how much slayer XP each one
gives. Greater demon task? Tormented Demons are 1,065 xp a kill against a standard
greater demon's 87 — a 12x difference nothing else on the Hub surfaces.

Auto-detects your current task, recommends the best monster you can actually reach
(dimming anything behind a quest you haven't finished), and lets you search by monster
name as well as task — type `vorkath` to find the blue dragon task. Also links the
wiki's money making guide where one exists.

Data is scraped from the OSRS Wiki and stays under the wiki's CC BY-NC-SA licence; see
`LICENSE-DATA.md`. No network calls at runtime — everything ships in the jar.

## Build

```bash
JAVA_HOME="/c/Program Files/Java/jdk-17" ./gradlew build
JAVA_HOME="/c/Program Files/Java/jdk-17" ./gradlew run
```

Developer notes (data pipeline, icon matching, verification) are in `DEVELOPMENT.md`.
