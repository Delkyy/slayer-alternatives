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
        n_rows = 0
        n_tasks = 0
        for title in sorted(pages):
            task = title.split("/", 1)[1]
            rows = variants_table(pages[title])
            if rows:
                n_tasks += 1
            for r in rows:
                monster = unlink(r[0])
                combat = unlink(r[1]) if len(r) > 1 else ""
                xp = unlink(r[2]) if len(r) > 2 else ""
                locs = " | ".join(bullets(r[3])) if len(r) > 3 else ""
                notes = " | ".join(bullets(r[4])) if len(r) > 4 else ""
                if not monster:
                    continue
                f.write(f"{task}\t{monster}\t{combat}\t{xp}\t{locs}\t{notes}\n")
                n_rows += 1

    print(f"# {n_tasks} tasks with a variants table, {n_rows} rows", file=sys.stderr)
    print(f"# {len(pages) - n_tasks} pages had no table - check those by hand", file=sys.stderr)


if __name__ == "__main__":
    main()
