"""
Parse the master assignment table on the "Slayer task" page.

This is the authoritative list of assignable tasks. Each row carries the Slayer level,
the task, its superiors and alternatives, locations, required items, other requirements
and which masters assign it.

Pairs with pull-variants.py, which gets the per-monster combat/xp detail off each
task's own page. This one establishes WHICH tasks exist; that one fills in the rows.
"""
import json
import re
import sys
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
UA = "slayer-alternatives-dev/0.1 (contact@everykill.com)"


def get(params):
    params["format"] = "json"
    params["formatversion"] = 2
    url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


LINK = re.compile(r"\[\[([^\]|]+)(?:\|([^\]]*))?\]\]")
# {{plink|Nose peg}} / {{plinkp|X|pic=Y}} / {{SCP|Slayer}} / {{chatlp|Duradel}}
TMPL = re.compile(r"\{\{(\w+)\|([^{}]*)\}\}")


def unlink(s):
    s = strip_footnotes(s)

    def pick(m):
        return (m.group(2) or m.group(1).split("#")[0]).strip()
    s = LINK.sub(pick, s)

    def tmpl(m):
        name, args = m.group(1).lower(), m.group(2)
        parts = [a for a in args.split("|") if "=" not in a]
        if name in ("plink", "plinkp", "chatlp", "chatl", "scp"):
            return parts[0].strip() if parts else ""
        if name == "na":
            return ""
        return " ".join(p.strip() for p in parts)

    for _ in range(3):  # nested templates
        s2 = TMPL.sub(tmpl, s)
        if s2 == s:
            break
        s = s2
    s = re.sub(r"\{\{[^{}]*\}\}", "", s)
    s = re.sub(r"'''?", "", s)
    s = re.sub(r"<ref[^>]*>.*?</ref>", "", s, flags=re.S)
    s = re.sub(r"<ref[^>]*/>", "", s)
    s = re.sub(r"<[^>]+>", " ", s)
    return re.sub(r"\s+", " ", s).strip()


# {{efn|...}} footnotes get stripped by the template pass above, but efn bodies can
# carry their own [[links]] and nested braces, so the naive strip leaves the prose
# glued to the cell. "Black dragons" came back as
# "Black dragonsThe King Black Dragon does not count towards..." because of it.
EFN = re.compile(r"\{\{\s*efn.*?\}\}\s*", re.S | re.I)


def strip_footnotes(s):
    prev = None
    while prev != s:
        prev = s
        # innermost {{efn ...}} first so nested braces unwind
        s = re.sub(r"\{\{\s*efn(?:[^{}]|\{\{[^{}]*\}\})*\}\}", "", s, flags=re.S | re.I)
    return s


def bullets(cell):
    out = []
    for line in cell.split("\n"):
        line = line.strip()
        if not line.startswith("*"):
            continue
        depth = len(line) - len(line.lstrip("*"))

        # several {{plink}}s on one line are ALTERNATIVES, not one item. expanding them
        # straight gave "Dragonfire ward Ancient wyvern shield Dragonfire shield" as a
        # single unreadable string. separate them before the templates get flattened.
        body = line.lstrip("*").strip()
        body = re.sub(r"\}\}\s*\{\{", "}} / {{", body)

        v = unlink(body)
        if v:
            # sub-bullets (e.g. slayer helmet listed under nose peg) are still a real,
            # independent alternative - "either works" - so they join the flat list
            # rather than carrying an indent marker nothing downstream ever consumed.
            out.append(v)
    if not out:
        v = unlink(cell)
        if v:
            out.append(v)
    return out


def superior(cell):
    """Split the alternatives column into superiors vs ordinary alternatives.

    Superiors are marked with the Bigger and Badder pic and bolded; everything else in
    that column is a monster that also counts for the task.
    """
    sups, alts = [], []
    for line in cell.split("\n"):
        line = line.strip()
        if not line.startswith("*"):
            continue
        is_sup = "Bigger and Badder" in line or "Superior slayer monster" in line
        v = unlink(line.lstrip("*").strip())
        if not v:
            continue
        (sups if is_sup else alts).append(v)
    return sups, alts


def rows(wikitext):
    i = wikitext.find("List of assignments")
    if i < 0:
        raise SystemExit("no assignment list on the page")
    t = re.search(r"\{\|(.*?)\n\|\}", wikitext[i:], re.S)
    if not t:
        raise SystemExit("no table after the heading")

    out = []
    for chunk in t.group(1).split("\n|-"):
        cells = re.findall(r"^\|([^-|].*?)(?=\n\||\Z)", chunk, re.S | re.M)
        if len(cells) >= 6:
            out.append([c.strip() for c in cells])
    return out


def main():
    wt = get({"action": "parse", "page": "Slayer task", "prop": "wikitext"})["parse"]["wikitext"]
    table = rows(wt)
    print(f"# {len(table)} assignment rows", file=sys.stderr)

    with open("slayer-tasks.tsv", "w", encoding="utf-8") as f:
        f.write("slayer_level\ttask\tsuperiors\talternatives\tlocations\titems\trequirements\tmasters\n")
        for r in table:
            lvl = unlink(r[0])
            task = unlink(r[1])
            sups, alts = superior(r[2])
            locs = bullets(r[3])
            items = bullets(r[4]) if len(r) > 4 else []
            reqs = bullets(r[5]) if len(r) > 5 else []
            masters = unlink(r[6]) if len(r) > 6 else ""
            if not task:
                continue
            f.write("\t".join([
                lvl, task, " | ".join(sups), " | ".join(alts),
                " | ".join(locs), " | ".join(items), " | ".join(reqs), masters,
            ]) + "\n")

    print("# -> slayer-tasks.tsv", file=sys.stderr)


if __name__ == "__main__":
    main()
