"""
Pull every Slayer task page off the wiki and pick out the monsters that also count.

The wiki writes these as prose, not a table, so this reads the wikitext and looks for
the sentences that name alternates. Output is a TSV for a human to check - it is NOT
authoritative until someone reads it.
"""
import json
import re
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
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def task_pages():
    """Every Slayer task/ subpage, redirects dropped."""
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
    """Batched fetch, 40 pages a call."""
    pages = {}
    for i in range(0, len(titles), 40):
        batch = titles[i:i + 40]
        d = get({"action": "query", "titles": "|".join(batch),
                 "prop": "revisions", "rvprop": "content", "rvslots": "main"})
        for pg in d["query"]["pages"]:
            revs = pg.get("revisions")
            if revs:
                pages[pg["title"]] = revs[0]["slots"]["main"]["content"]
        time.sleep(0.5)
    return pages


# sentences that mean "this other thing counts for the task"
COUNTS = re.compile(
    r"(also count|count(?:s)? (?:towards|toward|as|for)|may (?:also )?(?:be killed|kill)"
    r"|can (?:also )?be killed|for the progression of|count(?:s)? in place of)",
    re.I)

LINK = re.compile(r"\[\[([^\]|#]+)(?:\|[^\]]*)?\]\]")

# links that are never a monster
SKIP = re.compile(
    r"^(Slayer|Slayer task|Combat|Wilderness|Konar|Krystilia|Chaeldar|Nieve|Steve|"
    r"Duradel|Kuradal|Vannaka|Mazchna|Turael|Spria|Aya|Achtryn|Boss|Multicombat|"
    r"Cannon|Dwarf multicannon|File:|Category:|Update:|Money making|Superior|"
    r"Slayer master|Experience|Hitpoints|Attack|Strength|Defence|Magic|Ranged|Prayer|"
    r"Quest|Members|Free-to-play|Ironman|Drop|Clue scroll)", re.I)


def alternates(text):
    """Monster links sitting in a sentence that says they count."""
    found = []
    # crude sentence split; wiki prose is full of abbreviations but this is for review
    for sent in re.split(r"(?<=[.!?])\s+", text):
        if not COUNTS.search(sent):
            continue
        for link in LINK.findall(sent):
            link = link.strip()
            if SKIP.match(link) or "/" in link:
                continue
            if link and link not in found:
                found.append(link)
    return found


def main():
    titles = task_pages()
    print(f"# {len(titles)} task pages")
    pages = wikitext(titles)
    print(f"# {len(pages)} fetched")

    rows = []
    for title in sorted(pages):
        task = title.split("/", 1)[1]
        alts = alternates(pages[title])
        if alts:
            rows.append((task, alts))

    with open("slayer-alternates.tsv", "w", encoding="utf-8") as f:
        f.write("task\talternates\n")
        for task, alts in rows:
            f.write(f"{task}\t{'|'.join(alts)}\n")

    print(f"# {len(rows)} tasks with alternates -> slayer-alternates.tsv")
    for task, alts in rows[:25]:
        print(f"  {task}: {', '.join(alts)}")


if __name__ == "__main__":
    main()
