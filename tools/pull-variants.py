"""
Pull the "Monster variants" table off every Slayer task page.

The prose on these pages does not parse - a first attempt at reading the sentences
dragged in dungeons, quests and keys as if they were monsters. The variants table is
structured and carries the requirements in its Notes column, so read that instead.

Output is a TSV for a human to check. It is NOT authoritative until someone has read it
against the wiki.
"""
import json
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
UA = "slayer-alternatives-dev/0.1 (contact@everykill.com)"


def get(params):
    params["format"] = "json"
    params["formatversion"] = 2
    url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.load(r)
        except Exception as e:
            if attempt == 2:
                raise
            print(f"# retry {attempt+1}: {e}", file=sys.stderr)
            time.sleep(2 * (attempt + 1))


def task_pages():
    out = []
    cont = None
    while True:
        p = {"action": "query", "list": "allpages", "apprefix": "Slayer task/",
             "aplimit": 500, "apfilterredir": "nonredirects"}
        if cont:
            p["apcontinue"] = cont
        d = get(p)
        out += [x["title"] for x in d["query"]["allpages"]]
        cont = d.get("continue", {}).get("apcontinue")
        if not cont:
            break
    return [t for t in out if t != "Slayer task/"]


def wikitext(titles):
    pages = {}
    for i in range(0, len(titles), 40):
        d = get({"action": "query", "titles": "|".join(titles[i:i + 40]),
                 "prop": "revisions", "rvprop": "content", "rvslots": "main"})
        for pg in d["query"]["pages"]:
            revs = pg.get("revisions")
            if revs:
                pages[pg["title"]] = revs[0]["slots"]["main"]["content"]
        time.sleep(0.4)
    return pages


LINK = re.compile(r"\[\[([^\]|]+)(?:\|([^\]]*))?\]\]")


def unlink(s):
    """[[A|b]] -> b, [[A]] -> A, [[A#sec|b]] -> b. Then strip the rest of the markup."""
    def pick(m):
        label = m.group(2)
        if label is not None:
            return label.strip()
        # no label, so use the target with any #section anchor dropped
        return m.group(1).split("#")[0].strip()

    s = LINK.sub(pick, s)
    s = re.sub(r"\{\{[^}]*\}\}", "", s)
    s = re.sub(r"'''?", "", s)
    s = re.sub(r"<[^>]+>", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def variants_table(text):
    """The == Monster variants == section's first wikitable, as raw rows."""
    m = re.search(r"==\s*Monster variants\s*==(.*?)(?=\n==[^=]|\Z)", text, re.S | re.I)
    if not m:
        return []
    section = m.group(1)

    t = re.search(r"\{\|(.*?)\n\|\}", section, re.S)
    if not t:
        return []
    body = t.group(1)

    rows = []
    for chunk in body.split("\n|-"):
        # a row's cells: leading "|" lines, but not "!" headers or the {| attrs
        cells = re.findall(r"^\|([^-|].*?)(?=\n\||\Z)", chunk, re.S | re.M)
        if len(cells) >= 3:
            rows.append([c.strip() for c in cells])
    return rows


def split_stacked(cell):
    """
    One table cell can hold several values, one per line.

    The dagannoth page puts Prime, Rex and Supreme in a single cell as three [[links]]
    on separate lines, with three matching xp values stacked the same way in the xp
    cell. Flattening that gave the monster "Dagannoth Prime Dagannoth Rex Dagannoth
    Supreme" with no xp at all, because "331.5 331.5 255" doesn't parse as a number.

    Three different ways a cell stacks its values, all seen in real pages:
      - one per newline          (dagannoth kings)
      - separated by <br>        (zombie pirate variants)
      - as a bullet list         (white wolves' two combat levels)

    Returns the individual values, blank ones dropped.
    """
    # <br> is a line break like any other
    cell = re.sub(r"<\s*br\s*/?\s*>", "\n", cell, flags=re.I)

    parts = []
    for line in cell.split("\n"):
        line = line.strip().lstrip("*").strip()
        v = unlink(line)
        # a trailing separator is left over from "[[A]] /<br> [[B]]"
        v = v.strip(" /,;")
        if v:
            parts.append(v)
    return parts


def looks_numeric(s):
    """
    A combat level or xp value, rather than prose that leaked in from a neighbouring
    cell. "124", "331.5", "1,708", "40/42" and "96, 146" are all real; "Escape Caves"
    and a bare "|" are not.
    """
    return bool(s) and bool(re.search(r"\d", s)) and not re.search(r"[A-Za-z]{3}", s)


def bullets(cell):
    out = []
    for line in cell.split("\n"):
        line = line.strip()
        if line.startswith("*"):
            v = unlink(line.lstrip("*").strip())
            if v:
                out.append(v)
    if not out:
        v = unlink(cell)
        if v:
            out.append(v)
    return out


def main():
    titles = task_pages()
    print(f"# {len(titles)} task pages", file=sys.stderr)
    pages = wikitext(titles)
    print(f"# {len(pages)} fetched", file=sys.stderr)

    with open("slayer-variants.tsv", "w", encoding="utf-8") as f:
        f.write("task\tmonster\tcombat\tslayer_xp\tlocations\tnotes\n")

        # keyed on (task, monster, combat, xp) -> the row dict itself, not a bool.
        # the wiki genuinely repeats a monster with the SAME name/level/xp but a
        # DIFFERENT location as a second table row - the abyssal demon page lists it
        # once for its normal spawns and again, separately, for the Catacombs of
        # Kourend version. dropping the second occurrence as "already seen" silently
        # threw its location away; merging locations into the first row keeps it.
        rows_by_key = {}
        order = []
        n_tasks = 0
        for title in sorted(pages):
            task = title.split("/", 1)[1]
            rows = variants_table(pages[title])
            if rows:
                n_tasks += 1
            for r in rows:
                names = split_stacked(r[0])
                combats = split_stacked(r[1]) if len(r) > 1 else []
                xps = split_stacked(r[2]) if len(r) > 2 else []
                locs = bullets(r[3]) if len(r) > 3 else []
                notes = " | ".join(bullets(r[4])) if len(r) > 4 else ""

                if not names:
                    continue

                # a single cell naming several npcs with commas, e.g. the three goblin
                # sergeants. only split when every piece repeats a word - "Sergeant
                # X, Sergeant Y" is a list; "Kalphite Queen, first form" is not.
                if len(names) == 1 and re.search(r"[,;]", names[0]):
                    # the goblin row separates with a comma AND a stray full stop:
                    # "Sergeant Strongstack, Sergeant Steelwill. Sergeant Grimspike"
                    pieces = [p.strip(" .") for p in re.split(r"[,;]|\.\s+", names[0])
                              if p.strip(" .")]
                    heads = [p.split()[0] for p in pieces if p.split()]
                    if len(pieces) > 1 and len(set(heads)) == 1:
                        names = pieces

                # stacked cells line up positionally: name[i] goes with xp[i]. when the
                # other cells hold a single value it applies to all of them (one combat
                # level, three kings).
                #
                # white wolves is the mirror case - ONE name with two statblocks stacked
                # under it. iterate the longest column so neither gets dropped.
                #
                # only the NUMERIC columns may extend the row count. a stray line in a
                # prose cell used to invent rows with xp="Escape Caves".
                combats = [c for c in combats if looks_numeric(c)]
                xps = [x for x in xps if looks_numeric(x)]

                count = max(len(names), len(combats), len(xps))
                for i in range(count):
                    monster = names[i] if i < len(names) else names[0]
                    combat = combats[i] if i < len(combats) else (
                        combats[0] if len(combats) == 1 else "")
                    xp = xps[i] if i < len(xps) else (
                        xps[0] if len(xps) == 1 else "")
                    key = (task, monster, combat, xp)

                    if key in rows_by_key:
                        existing = rows_by_key[key]
                        for loc in locs:
                            if loc not in existing["locs"]:
                                existing["locs"].append(loc)
                        continue

                    entry = {"locs": list(locs), "notes": notes}
                    rows_by_key[key] = entry
                    order.append(key)

        n_rows = 0
        for key in order:
            task, monster, combat, xp = key
            entry = rows_by_key[key]
            locs = " | ".join(entry["locs"])
            f.write(f"{task}\t{monster}\t{combat}\t{xp}\t{locs}\t{entry['notes']}\n")
            n_rows += 1

    print(f"# {n_tasks} tasks with a variants table, {n_rows} rows", file=sys.stderr)
    print(f"# {len(pages) - n_tasks} pages had no table - check those by hand", file=sys.stderr)


if __name__ == "__main__":
    main()
