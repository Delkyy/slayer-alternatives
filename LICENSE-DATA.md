# Licensing

## Code

BSD 2-Clause, see `LICENSE`. Copyright (c) 2026, Delkyy.

## Bundled data — different licence, read this

These files are **not** covered by the BSD licence above:

- `src/main/resources/com/slayeralts/slayer-data.json`
- `src/main/resources/com/slayeralts/gp.json`

They are derived from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki)
and are licensed **CC BY-NC-SA 3.0**, the same terms the wiki publishes under
(confirmed from its own API: `action=query&meta=siteinfo&siprop=rightsinfo`).

That means:

- **BY** — attribution is required. The plugin credits the wiki in its side panel, this
  file records it, and every data file carries a `source` field naming the wiki and the
  licence.
- **NC** — non-commercial only. The plugin is free, on a free plugin hub, with no ads,
  no payment and no telemetry.
- **SA** — share-alike. Anyone redistributing the data files, or a derivative of them,
  must do so under CC BY-NC-SA 3.0. The code around them stays BSD.

`icons.json` is a map of monster names to **RuneLite item ids**. Those ids come from
RuneLite's own `ItemID` class, not from the wiki, so that file follows the code licence.

## Regenerating rather than trusting the bundle

Everything under `data/` is gitignored on purpose. The scrapers in `tools/` rebuild it
from the wiki, so nobody has to take the committed copy on faith:

```bash
cd data && python ../tools/pull-tasks.py && python ../tools/pull-variants.py
cd .. && python tools/build-data.py && python tools/build-icons.py && python tools/build-gp.py
```

## Why the split rather than one licence

The Hub asks for BSD 2-Clause and the wiki requires share-alike, so a single licence
can't satisfy both. Splitting them is the honest reading: the code is ours and permissive,
the data is the wiki's and stays under the wiki's terms.
