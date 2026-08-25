"""
Work out which in-game item icon best represents each monster.

Monster images are NOT in the RuneLite API - NPCComposition gives you 3D model ids, not
pictures. But the game cache is full of items that depict monsters: the slayer guide
icons, boss pets, heads, masks, and the Arceuus corpse items. ItemManager.getImage()
renders those from the player's own cache, so there is nothing to redistribute and no
network call. This is exactly what core's own slayer plugin does for its task icons.

Output is icons.json, monster name -> item id, bundled into the jar.
"""
import difflib
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DATA = os.path.join(ROOT, "data")

ITEMID = os.path.join(os.path.dirname(ROOT), "runelite",
                      "runelite-api", "src", "main", "java", "net", "runelite",
                      "api", "gameval", "ItemID.java")

# Families of item that depict a monster, in order of how good they look at 18px.
# Slayer guide icons are purpose-drawn monster portraits, so they win.
PREFIXES = ["SLAYERGUIDE_", "POH_", "ARCEUUS_CORPSE_", "PICKPOCKET_GUIDE_", "RAIDS_"]
SUFFIXES = ["_HEAD", "_MASK", "_PET", "PET", "_INITIAL", "_COOKED", "HEAD", "MASK"]

# Hand-picked where the name match can't work. Core does the same thing - its Birds task
# uses a feather. Keep these obvious: the icon has to read as the monster at a glance.
# Every constant here is checked against ItemID.java at build time; a typo fails loudly
# rather than silently dropping the icon.
OVERRIDES = {
    "Bat": "BAT_BONES",
    "Albino bat": "BAT_BONES",
    "Bird": "FEATHER",
    "Cow": "COW_HIDE",
    "Cow calf": "COW_HIDE",
    "Goblin": "GOBLIN_MASK",
    "Rat": "RATS_TAIL",
    "Giant rat": "RATS_TAIL",
    "Wolf": "WOLF_BONES",
    "Big Wolf": "WOLF_BONES",
    "Jungle wolf": "WOLF_BONES",
    "Scorpion": "SCORPIONCAGEFULL",
    "Ghost": "GHOSTSKULL",
    "Hill Giant": "GIANT_BONES",
    "Hill giant": "GIANT_BONES",
    "Fire giant": "GIANT_BONES",
    "Ice giant": "GIANT_BONES",
    "Moss giant": "GIANT_BONES",
    "Blue dragon": "DRAGONHIDE_BLUE",
    "Baby blue dragon": "DRAGONHIDE_BLUE",
    "Black dragon": "DRAGONHIDE_BLACK",
    "Baby black dragon": "DRAGONHIDE_BLACK",
    "Red dragon": "DRAGONHIDE_RED",
    "Baby red dragon": "DRAGONHIDE_RED",
    "Green dragon": "DRAGONHIDE_GREEN",
    "Baby green dragon": "DRAGONHIDE_GREEN",
    "Bronze dragon": "BRONZE_BAR",
    "Iron dragon": "IRON_BAR",
    "Steel dragon": "STEEL_BAR",
    "Mithril dragon": "MITHRIL_BAR",
    "Adamant dragon": "ADAMANTITE_BAR",
    "Rune dragon": "RUNITE_BAR",
    "Lava dragon": "LAVA_SCALE",
}


def item_constants():
    txt = open(ITEMID, encoding="utf-8", errors="replace").read()
    return {m.group(1): int(m.group(2)) for m in
            re.finditer(r"^\tpublic static final int ([A-Z][A-Z0-9_]*) = (\d+);", txt, re.M)}


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def build_index(items):
    """item name with its family markers stripped -> (constant, id)."""
    idx = {}
    for k, v in items.items():
        s = k
        for pre in PREFIXES:
            if s.startswith(pre):
                s = s[len(pre):]
                break
        for suf in SUFFIXES:
            if s.endswith(suf):
                s = s[:-len(suf)]
                break
        n = norm(s)
        if n and n not in idx:
            idx[n] = (k, v)
    return idx


def match(name, idx, keys):
    n = norm(name)
    if n in idx:
        return idx[n]
    if n.endswith("s") and n[:-1] in idx:
        return idx[n[:-1]]
    # the cache spells it SPECTER, the wiki spells it spectre
    close = difflib.get_close_matches(n, keys, n=1, cutoff=0.87)
    if close:
        return idx[close[0]]

    # "Brutal black dragon" is a black dragon wearing a hat. drop the qualifier and
    # let it inherit the base monster's icon rather than showing nothing.
    words = name.split()
    for cut in range(1, len(words)):
        stem = " ".join(words[cut:])
        s = norm(stem)
        if len(s) < 4:
            break
        if s in idx:
            return idx[s]
        if s.endswith("s") and s[:-1] in idx:
            return idx[s[:-1]]
    return None


def main():
    if not os.path.exists(ITEMID):
        raise SystemExit("ItemID.java not found - needs the runelite source checked out "
                         "next to this repo")

    items = item_constants()

    # a typo in OVERRIDES silently costs an icon, so fail on it instead
    typos = [v for v in OVERRIDES.values() if v not in items]
    if typos:
        raise SystemExit("OVERRIDES names that aren't in ItemID.java: " + ", ".join(typos))

    idx = build_index(items)
    keys = list(idx)

    with open(os.path.join(DATA, "slayer-data.json"), encoding="utf-8") as f:
        payload = json.load(f)

    monsters = sorted({m["monster"] for t in payload["tasks"] for m in t["monsters"]})
    tasks = [t["task"] for t in payload["tasks"]]

    icons = {}
    how = {"override": 0, "matched": 0}

    for name in monsters + tasks:
        if name in icons:
            continue
        if name in OVERRIDES and OVERRIDES[name] in items:
            icons[name] = items[OVERRIDES[name]]
            how["override"] += 1
            continue
        hit = match(name, idx, keys)
        if hit:
            icons[name] = hit[1]
            how["matched"] += 1

    dest = os.path.join(DATA, "icons.json")
    with open(dest, "w", encoding="utf-8") as f:
        json.dump(icons, f, indent=1, sort_keys=True)

    mon_hits = sum(1 for m in monsters if m in icons)
    task_hits = sum(1 for t in tasks if t in icons)
    print(f"monsters with an icon : {mon_hits}/{len(monsters)} "
          f"({100 * mon_hits // len(monsters)}%)")
    print(f"tasks with an icon    : {task_hits}/{len(tasks)} "
          f"({100 * task_hits // len(tasks)}%)")
    print(f"  by hand-picked override: {how['override']}")
    print(f"  by name match          : {how['matched']}")
    print(f"\n-> {dest}")

    missing = [m for m in monsters if m not in icons]
    print(f"\n{len(missing)} monsters have no icon and will render without one.")
    print("first 20:", missing[:20])


if __name__ == "__main__":
    main()
