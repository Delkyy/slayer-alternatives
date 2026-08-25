"""
Merge the scraped variant rows with the Bucket API and write the plugin's data file.

Two sources, and they are not equal:

  - the article's == Monster variants == table gives us WHICH monsters count for a task,
    plus locations and requirements. Nothing else has that mapping.
  - the Bucket API gives us the infobox numbers - slayer xp, combat level. These come
    straight from the monster's own infobox.

Where they disagree on a NUMBER, the API wins. Checked by hand: the Nechryael article
table says 128 slayer xp, the infobox says slayxp=105, and the API says 105. The table
was stale. Article tables are maintained by hand and drift; infoboxes are what the rest
of the wiki computes from.

Where the API has no entry at all, the scraped value is kept and the row is flagged
unverified, because a missing bucket entry is usually a plural or a compound name
("Wolves", "Dagannoth Prime Dagannoth Rex Dagannoth Supreme"), not a missing monster.
"""
import csv
import json
import os
import re
import sys
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
UA = "slayer-alternatives-dev/0.1 (contact@everykill.com)"

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(os.path.dirname(HERE), "data")


def get(params):
    params["format"] = "json"
    params["formatversion"] = 2
    url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def bucket_all():
    rows, offset = [], 0
    while True:
        q = ("bucket('infobox_monster')"
             ".select('page_name','name','combat_level','slayer_experience','slayer_level')"
             f".limit(2000).offset({offset}).run()")
        d = get({"action": "bucket", "query": q})
        if "error" in d:
            raise SystemExit("bucket error: " + d["error"])
        rows += d["bucket"]
        if len(d["bucket"]) < 2000:
            return rows
        offset += 2000


def norm(s):
    s = (s or "").strip().lower().replace("\u2019", "'")
    s = re.sub(r"\s*\(.*?\)\s*", " ", s)
    s = re.sub(r"[^a-z0-9' ]", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def num(s):
    try:
        return float(str(s).replace(",", "").strip())
    except (ValueError, AttributeError):
        return None


def fmt(x):
    if x is None:
        return ""
    return str(int(x)) if float(x).is_integer() else str(x)


def load(name):
    with open(os.path.join(DATA, name), encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def taskkey(s):
    s = s.strip().lower()
    s = re.sub(r"[^a-z ]", "", s)
    return s.rstrip("s")


def main():
    variants = load("slayer-variants.tsv")
    tasks = load("slayer-tasks.tsv")
    api = bucket_all()
    print(f"# {len(variants)} scraped rows, {len(api)} bucket monsters", file=sys.stderr)

    index = {}
    for r in api:
        for nm in (r.get("page_name"), r.get("name")):
            if nm:
                index.setdefault(norm(nm), []).append(r)

    # superiors per task, so the plugin can mark them - they're rare spawns, not a choice
    sup_by_task = {}
    for t in tasks:
        sups = {norm(re.sub(r"^Superior slayer monster\s*", "", s))
                for s in t["superiors"].split(" | ") if s.strip()}
        sup_by_task[taskkey(t["task"])] = sups

    out = []
    stats = {"api": 0, "scraped": 0}
    for v in variants:
        key = norm(v["monster"])
        cands = index.get(key, [])

        want_xp, want_cb = num(v["slayer_xp"]), num(v["combat"])

        # pick the statblock closest to the scraped combat level - a name like
        # "Greater Demon" has several, and the row is about one of them
        pick = None
        if cands:
            if want_cb is not None:
                pick = min(cands, key=lambda c: abs((num(c.get("combat_level")) or 0) - want_cb))
            else:
                pick = cands[0]

        api_xp = num(pick.get("slayer_experience")) if pick else None
        api_cb = num(pick.get("combat_level")) if pick else None

        verified = api_xp is not None
        xp = api_xp if verified else want_xp
        cb = api_cb if api_cb is not None else want_cb
        stats["api" if verified else "scraped"] += 1

        out.append({
            "task": v["task"],
            "monster": v["monster"],
            "combat": fmt(cb),
            "slayerXp": fmt(xp),
            "locations": [x for x in v["locations"].split(" | ") if x.strip()],
            "notes": [x for x in v["notes"].split(" | ") if x.strip()],
            "superior": key in sup_by_task.get(taskkey(v["task"]), set()),
            "verified": verified,
        })

    by_task = {}
    for r in out:
        by_task.setdefault(taskkey(r["task"]), []).append(r)

    payload = {
        "source": "OSRS Wiki (CC BY-NC-SA 3.0): Slayer task article tables + infobox_monster bucket",
        "tasks": [{
            "task": t["task"],
            "slayerLevel": t["slayer_level"],
            "alternatives": [x for x in t["alternatives"].split(" | ") if x.strip()],
            "superiors": [x for x in t["superiors"].split(" | ") if x.strip()],
            "locations": [x for x in t["locations"].split(" | ") if x.strip()],
            "items": [x for x in t["items"].split(" | ") if x.strip()],
            "requirements": [x for x in t["requirements"].split(" | ") if x.strip()],
            "masters": t["masters"],
            "monsters": by_task.get(taskkey(t["task"]), []),
        } for t in tasks],
    }

    dest = os.path.join(DATA, "slayer-data.json")
    with open(dest, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=1)

    n = len(out)
    placed = sum(len(t["monsters"]) for t in payload["tasks"])
    if placed != n:
        print(f"\n!! {n - placed} of {n} rows did not attach to any task - check the task keys",
              file=sys.stderr)

    print(f"\nnumbers taken from the Bucket API : {stats['api']:3}/{n} ({100*stats['api']//n}%)")
    print(f"kept from the article table       : {stats['scraped']:3}/{n}  (flagged unverified)")
    print(f"\n-> {dest}")


if __name__ == "__main__":
    main()
