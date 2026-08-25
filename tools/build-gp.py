"""
Match monsters to the wiki's money making guides.

There is NO per-monster gp value anywhere on the wiki, and this script exists to record
WHY rather than to invent one:

  1. infobox_monster has no value/profit/loot field - checked, ten fields, none of them
     money.
  2. The ~138 "Money making guide/Killing X" pages don't store a profit number either.
     Their {{Mmgtable}} lists inputs and outputs and the wiki COMPUTES the hourly figure
     at render time from live GE prices. There is nothing static to scrape.
  3. Those guides are per-SETUP anyway - Vorkath alone has three (blowpipe, dragon
     hunter crossbow, dragon hunter lance) with different numbers. A single "Vorkath is
     worth X" figure would be a claim about the player's gear that we cannot make.

So what ships is a POINTER: this monster has a money making guide, here's its name and
setup, open it in the browser. That's true, useful, and needs no runtime network call.
Anything more would be a number the plugin can't stand behind.

Output is gp.json: monster name -> [{guide, setup}, ...].
"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(os.path.dirname(HERE), "data")
UA = "slayer-alternatives-dev/0.1 (contact@everykill.com)"


def api(**params):
    params.update(format="json", formatversion=2)
    url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def guide_pages():
    out, cont = [], None
    while True:
        p = dict(action="query", list="allpages", apprefix="Money making guide/",
                 aplimit=500, apfilterredir="nonredirects")
        if cont:
            p["apcontinue"] = cont
        d = api(**p)
        out += [x["title"] for x in d["query"]["allpages"]]
        cont = d.get("continue", {}).get("apcontinue")
        if not cont:
            return [t for t in out if "/Killing " in t]


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def main():
    with open(os.path.join(DATA, "slayer-data.json"), encoding="utf-8") as f:
        payload = json.load(f)
    monsters = sorted({m["monster"] for t in payload["tasks"] for m in t["monsters"]})
    index = {norm(m): m for m in monsters}

    titles = guide_pages()
    print(f"# {len(titles)} killing guides", file=sys.stderr)

    out = {}
    for title in titles:
        subject = title.split("/Killing ", 1)[1]

        # "Killing Vorkath (Dragon hunter lance)" - the bracket is the SETUP, and it's
        # the whole reason a single number would be a lie
        setup = ""
        m = re.match(r"^(.*?)\s*\(([^)]+)\)\s*$", subject)
        if m:
            subject, setup = m.group(1), m.group(2)

        key = norm(subject)
        name = index.get(key) or index.get(key.rstrip("s"))
        if not name:
            continue

        out.setdefault(name, []).append({"guide": title, "setup": setup})

    dest = os.path.join(DATA, "gp.json")
    with open(dest, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=1, sort_keys=True)

    print(f"monsters with a money making guide: {len(out)}/{len(monsters)}", file=sys.stderr)
    for name in sorted(out)[:10]:
        setups = ", ".join(e["setup"] or "default" for e in out[name])
        print(f"   {name:26} {setups}", file=sys.stderr)
    print(f"\n-> {dest}", file=sys.stderr)


if __name__ == "__main__":
    main()
