"""
Attack style per monster, straight from the wiki's own infobox field.

infobox_monster.attack_style is a real, structured field - not prose to parse - and it
already covers the full monster list (verified: every monster in our data checked
against it during build-data.py's normal merge pass). Values seen in the data:
'Stab', 'Slash', 'Crush', 'Magic', 'Ranged', 'Dragonfire', 'Icy breath', 'Melee',
'Magical melee', 'Magical ranged', 'Ranged melee', 'N/A', 'None', and combinations
where a monster switches style per phase or has more than one attack.

Collapsed down to the three things a player actually needs to know for gearing up:
melee / ranged / magic. A monster with more than one gets more than one symbol - a
mithril dragon hits with stab AND dragonfire (magic) AND ranged, and showing only one
would be a lie about what to bring.
"""
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

# wiki attack_style value -> our three combat styles. deliberately explicit rather than
# a substring match, so an unrecognised value fails loud instead of silently vanishing.
#
# "Typeless" and prayer-penetration/special-mechanic suffixes still tell you what to
# GEAR for (a typeless magic attack still needs mage gear to hit back with, prayer
# penetration doesn't change which combat style you bring) - so those map through to
# the underlying style rather than being dropped. Bare "Typeless" with no style named
# and "Various"/"All"/"Area of effect" carry no gearing information at all and stay
# unmapped on purpose; a monster that hits with everything doesn't need "bring melee,
# ranged AND magic" cluttering every row.
STYLE_MAP = {
    "stab": {"melee"},
    "slash": {"melee"},
    "crush": {"melee"},
    "melee": {"melee"},
    "ranged": {"ranged"},
    "range": {"ranged"},
    "ranged melee": {"melee", "ranged"},
    "ranged magic": {"ranged", "magic"},
    "magic": {"magic"},
    "magical melee": {"melee", "magic"},
    "magic melee": {"melee", "magic"},
    "magical ranged": {"ranged", "magic"},
    "dragonfire": {"magic"},
    "icy breath": {"magic"},
    "n/a": set(),
    "none": set(),
    "melee (crush?)": {"melee"},
    "melee (slash)": {"melee"},
    "typeless crush": {"melee"},
    "typeless slash": {"melee"},
    "typeless stab": {"melee"},
    "typeless melee": {"melee"},
    "typeless ranged": {"ranged"},
    "typeless magic": {"magic"},
    "ranged <br/> typeless": {"ranged"},
    "single and multi-target ranged": {"ranged"},
}

# style names with a bracketed/suffixed mechanic note that doesn't change what to bring.
# stripped before lookup rather than added as more STYLE_MAP entries, since these are
# modifiers on a style ("100% prayer penetration") not new styles of their own.
_STRIP_SUFFIXES = (
    " (100% prayer penetration)",
    " (bleed)",
    " (fire waves)",
    " (special)",
)


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
             ".select('page_name','name','attack_style')"
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


def main():
    rows = bucket_all()
    print(f"# {len(rows)} infobox rows", file=sys.stderr)

    unmapped = set()
    out = {}
    for r in rows:
        styles = set()
        for raw in (r.get("attack_style") or []):
            v = raw.strip()

            # wiki footnote refs leak into infobox fields as a UNIQ marker wrapped in
            # 0x7f bytes - strip it rather than let it break the lookup on an otherwise
            # perfectly normal style name.
            v = re.sub(r"\x7f'\"`UNIQ--ref-[0-9A-F]+-QINU`\"'\x7f", "", v).strip()

            key = v.lower()
            for suffix in _STRIP_SUFFIXES:
                if key.endswith(suffix):
                    key = key[: -len(suffix)]
                    break

            if key in STYLE_MAP:
                styles |= STYLE_MAP[key]
            elif key:
                unmapped.add(v)

        if not styles:
            continue

        for nm in (r.get("page_name"), r.get("name")):
            if nm:
                out[norm(nm)] = sorted(styles)

    if unmapped:
        print(f"# WARNING: unmapped attack_style values, add to STYLE_MAP: {sorted(unmapped)}",
              file=sys.stderr)

    path = os.path.join(DATA, "combat-styles.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, separators=(",", ":"), sort_keys=True)
    print(f"# {len(out)} monsters -> {path}", file=sys.stderr)


if __name__ == "__main__":
    main()
