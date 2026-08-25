"""
Cross-check the scraped variant rows against the wiki's own Bucket API.

The rows in slayer-variants.tsv came out of a hand-written regex over article tables.
The Bucket API is a completely different path to the same facts - it reads the infobox
templates, not the article body - so where the two agree, the row is corroborated by
two independent sources. Where they disagree, a human looks.

This is the accuracy gate. A row nobody has checked is not data.
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
    """Every monster the wiki has an infobox for. One bulk pull, then index locally.

    Do NOT query per monster - that's 400 round trips for something one call answers.
    """
    rows = []
    offset = 0
    while True:
        q = ("bucket('infobox_monster')"
             ".select('page_name','name','combat_level','slayer_experience','slayer_level')"
             f".limit(2000).offset({offset}).run()")
        d = get({"action": "bucket", "query": q})
        if "error" in d:
            raise SystemExit("bucket error: " + d["error"])
        batch = d["bucket"]
        rows += batch
        if len(batch) < 2000:
            break
        offset += 2000
    return rows


def norm(s):
    """Monster names differ in case and punctuation between the two sources."""
    s = (s or "").strip().lower()
    s = s.replace("'", "'").replace("\u2019", "'")
    s = re.sub(r"\s*\(.*?\)\s*", " ", s)   # drop "(Final form)" etc
    s = re.sub(r"[^a-z0-9' ]", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def num(s):
    try:
        return float(str(s).replace(",", "").strip())
    except (ValueError, AttributeError):
        return None


def main():
    with open(os.path.join(DATA, "slayer-variants.tsv"), encoding="utf-8") as f:
        scraped = list(csv.DictReader(f, delimiter="\t"))

    print(f"# scraped rows: {len(scraped)}", file=sys.stderr)
    api = bucket_all()
    print(f"# bucket monsters: {len(api)}", file=sys.stderr)

    # a monster name can carry several statblocks (Greater Demon at cb 92/100/101/...)
    index = {}
    for r in api:
        for nm in (r.get("page_name"), r.get("name")):
            if nm:
                index.setdefault(norm(nm), []).append(r)

    agree, mismatch, absent = [], [], []

    for row in scraped:
        key = norm(row["monster"])
        cands = index.get(key)
        if not cands:
            absent.append(row)
            continue

        want_xp = num(row["slayer_xp"])
        want_cb = num(row["combat"])

        xps = {num(c.get("slayer_experience")) for c in cands}
        cbs = {num(c.get("combat_level")) for c in cands}
        xps.discard(None)
        cbs.discard(None)

        xp_ok = want_xp is None or want_xp in xps
        cb_ok = want_cb is None or want_cb in cbs

        if xp_ok and cb_ok:
            agree.append(row)
        else:
            mismatch.append({
                "task": row["task"],
                "monster": row["monster"],
                "scraped_xp": row["slayer_xp"],
                "api_xp": sorted(x for x in xps),
                "scraped_cb": row["combat"],
                "api_cb": sorted(c for c in cbs),
                "xp_ok": xp_ok,
                "cb_ok": cb_ok,
            })

    out = os.path.join(DATA, "verify-report.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump({
            "checked": len(scraped),
            "agree": len(agree),
            "mismatch": mismatch,
            "absent": [{"task": r["task"], "monster": r["monster"]} for r in absent],
        }, f, indent=1)

    n = len(scraped)
    print(f"\ncorroborated by the Bucket API : {len(agree):3}/{n}  ({100*len(agree)//n}%)")
    print(f"disagree, need a human         : {len(mismatch):3}")
    print(f"not in the bucket at all       : {len(absent):3}")
    print(f"\n-> {out}")

    if mismatch:
        print("\nfirst disagreements:")
        for m in mismatch[:15]:
            bad = []
            if not m["xp_ok"]:
                bad.append(f"xp {m['scraped_xp']} vs {m['api_xp']}")
            if not m["cb_ok"]:
                bad.append(f"cb {m['scraped_cb']} vs {m['api_cb']}")
            print(f"  {m['task']:22} {m['monster']:26} {'; '.join(bad)}")

    if absent:
        print("\nnot in the bucket (first 15):")
        for a in absent[:15]:
            print(f"  {a['task']:22} {a['monster']}")


if __name__ == "__main__":
    main()
