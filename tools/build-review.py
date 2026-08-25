"""
Render the scraped slayer data as a single self-contained HTML page.

This is a review tool, not a deliverable. The point is to eyeball 116 tasks and 400-odd
monster rows and catch the ones the scraper got wrong, because nothing downstream should
trust this data until a human has looked at it.
"""
import csv
import html
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(os.path.dirname(HERE), "data")


def load(name):
    with open(os.path.join(DATA, name), encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def key(s):
    """Task names differ in case and plural between the two pulls."""
    s = s.strip().lower()
    s = re.sub(r"[^a-z ]", "", s)
    return s.rstrip("s")


def num(s):
    try:
        return float(str(s).replace(",", "").strip())
    except (ValueError, AttributeError):
        return None


def build():
    tasks = load("slayer-tasks.tsv")
    variants = load("slayer-variants.tsv")

    by_task = {}
    for v in variants:
        by_task.setdefault(key(v["task"]), []).append(v)

    matched = set()
    out = []
    for t in tasks:
        k = key(t["task"])
        rows = by_task.get(k, [])
        if rows:
            matched.add(k)

        alts = [a for a in t["alternatives"].split(" | ") if a.strip()]
        sups = [s for s in t["superiors"].split(" | ") if s.strip()]

        # slayer xp range across the variant rows, for sorting/among-task comparison
        xps = [num(r["slayer_xp"]) for r in rows]
        xps = [x for x in xps if x is not None]

        out.append({
            "task": t["task"],
            "level": t["slayer_level"],
            "superiors": sups,
            "alternatives": alts,
            "locations": [x for x in t["locations"].split(" | ") if x.strip()],
            "items": [x for x in t["items"].split(" | ") if x.strip()],
            "requirements": [x for x in t["requirements"].split(" | ") if x.strip()],
            "masters": t["masters"],
            "maxXp": max(xps) if xps else None,
            "minXp": min(xps) if xps else None,
            "variants": [{
                "monster": r["monster"],
                "combat": r["combat"],
                "xp": r["slayer_xp"],
                "locations": [x for x in r["locations"].split(" | ") if x.strip()],
                "notes": [x for x in r["notes"].split(" | ") if x.strip()],
            } for r in rows],
        })

    orphans = sorted(k for k in by_task if k not in matched)
    return out, orphans


TEMPLATE = """<!doctype html>
<meta charset="utf-8">
<title>Slayer Alternatives - data review</title>
<style>
:root {
  --bg:#1b1b1b; --panel:#242424; --line:#333; --text:#e4e0d8; --dim:#9a948a;
  --accent:#d8a657; --good:#89b482; --warn:#e07b7b;
}
* { box-sizing:border-box; }
body { margin:0; background:var(--bg); color:var(--text);
  font:14px/1.5 "Segoe UI",system-ui,sans-serif; }
header { position:sticky; top:0; background:var(--bg); border-bottom:1px solid var(--line);
  padding:14px 20px; z-index:5; }
h1 { margin:0 0 4px; font-size:17px; font-weight:600; letter-spacing:.02em; }
.sub { color:var(--dim); font-size:12px; }
.controls { margin-top:10px; display:flex; gap:8px; flex-wrap:wrap; align-items:center; }
input[type=search] { flex:1; min-width:220px; background:var(--panel); border:1px solid var(--line);
  color:var(--text); padding:7px 10px; border-radius:3px; font-size:13px; }
input[type=search]:focus { outline:none; border-color:var(--accent); }
button { background:var(--panel); border:1px solid var(--line); color:var(--dim);
  padding:7px 11px; border-radius:3px; cursor:pointer; font-size:12px; }
button.on { color:var(--accent); border-color:var(--accent); }
main { padding:16px 20px 60px; }
.task { background:var(--panel); border:1px solid var(--line); border-radius:4px;
  margin-bottom:8px; overflow:hidden; }
.head { padding:10px 14px; display:flex; gap:10px; align-items:baseline; cursor:pointer; }
.head:hover { background:#2a2a2a; }
.name { font-weight:600; }
.lvl { color:var(--dim); font-size:12px; min-width:44px; }
.tags { margin-left:auto; display:flex; gap:6px; align-items:center; flex-wrap:wrap;
  justify-content:flex-end; }
.tag { font-size:11px; padding:1px 7px; border-radius:9px; border:1px solid var(--line);
  color:var(--dim); white-space:nowrap; }
.tag.alt { color:var(--accent); border-color:#5a4a2a; }
.tag.none { color:#6a6a6a; }
.tag.xp { color:var(--good); border-color:#3d5240; }
.body { display:none; padding:0 14px 12px; border-top:1px solid var(--line); }
.task.open .body { display:block; }
table { width:100%; border-collapse:collapse; margin-top:10px; font-size:13px; }
th { text-align:left; color:var(--dim); font-weight:500; font-size:11px;
  text-transform:uppercase; letter-spacing:.05em; padding:6px 8px;
  border-bottom:1px solid var(--line); }
td { padding:6px 8px; border-bottom:1px solid #2c2c2c; vertical-align:top; }
tr:last-child td { border-bottom:none; }
td.n { text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }
.best { color:var(--good); font-weight:600; }
.mon { font-weight:500; }
.note { color:var(--dim); font-size:12px; }
.meta { margin-top:10px; display:grid; grid-template-columns:repeat(auto-fit,minmax(230px,1fr));
  gap:10px; }
.meta h4 { margin:0 0 3px; font-size:11px; text-transform:uppercase; color:var(--dim);
  letter-spacing:.05em; font-weight:500; }
.meta ul { margin:0; padding-left:16px; }
.meta li { color:var(--text); }
.empty { color:#6a6a6a; font-style:italic; }
.warn { color:var(--warn); }
.orphans { margin-top:20px; padding:12px 14px; background:var(--panel);
  border:1px solid #4a3030; border-radius:4px; }
.count { color:var(--dim); font-size:12px; margin-bottom:10px; }
</style>

<header>
  <h1>Slayer Alternatives &mdash; data review</h1>
  <div class="sub">Scraped from the OSRS Wiki. <strong>Not verified by a human yet</strong> &mdash; this page exists so it can be.</div>
  <div class="controls">
    <input type="search" id="q" placeholder="Search task, monster or location...">
    <button id="fAlt">Has alternatives</button>
    <button id="fVar">Has variant data</button>
    <button id="fGap">Missing variants</button>
    <button id="expand">Expand all</button>
  </div>
</header>
<main>
  <div class="count" id="count"></div>
  <div id="list"></div>
  <div id="orphanBox"></div>
</main>

<script>
const DATA = __DATA__;
const ORPHANS = __ORPHANS__;

const esc = s => String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const wiki = n => 'https://oldschool.runescape.wiki/w/' + encodeURIComponent(String(n).replace(/ /g,'_'));

let filters = { alt:false, var:false, gap:false };
let q = '';

function xpNum(v){ const n = parseFloat(String(v).replace(/,/g,'')); return isNaN(n)?null:n; }

function taskHTML(t, i){
  // superiors are rare spawns you can't choose to farm, so they must not set the
  // headline number. best = best thing you can decide to go and kill.
  const supNames = new Set(t.superiors.map(s => s.replace(/^Superior slayer monster\s*/,'').trim().toLowerCase()));
  const choosable = t.variants.filter(v => !supNames.has(String(v.monster).trim().toLowerCase()));
  const best = choosable.reduce((m,v)=>{ const n=xpNum(v.xp); return n!==null&&(m===null||n>m)?n:m; }, null);

  const rows = t.variants.map(v => {
    const n = xpNum(v.xp);
    const isSup = supNames.has(String(v.monster).trim().toLowerCase());
    const isBest = !isSup && n !== null && n === best && choosable.length > 1;
    return `<tr>
      <td class="mon"><a href="${wiki(v.monster)}" target="_blank" rel="noopener">${esc(v.monster)}</a>${isSup?' <span class="tag">superior</span>':''}</td>
      <td class="n">${esc(v.combat||'-')}</td>
      <td class="n ${isBest?'best':''}">${esc(v.xp||'-')}</td>
      <td class="note">${v.locations.map(esc).join(', ')||'-'}</td>
      <td class="note">${v.notes.map(esc).join('<br>')||''}</td>
    </tr>`;
  }).join('');

  const table = t.variants.length ? `<table>
      <tr><th>Monster</th><th style="text-align:right">Cb</th><th style="text-align:right">Slayer XP</th><th>Locations</th><th>Notes</th></tr>
      ${rows}</table>`
    : `<p class="empty warn">No variants table on the wiki for this task &mdash; needs checking by hand.</p>`;

  const list = (label, arr) => arr.length
    ? `<div><h4>${label}</h4><ul>${arr.map(x=>`<li>${esc(x)}</li>`).join('')}</ul></div>` : '';

  const tags = [];
  if (t.alternatives.length) tags.push(`<span class="tag alt">${t.alternatives.length} alt${t.alternatives.length>1?'s':''}</span>`);
  else tags.push('<span class="tag none">no alts</span>');
  if (t.superiors.length) tags.push(`<span class="tag">${t.superiors.length} superior</span>`);
  if (best !== null) tags.push(`<span class="tag xp">best ${best.toLocaleString()} xp</span>`);
  if (!t.variants.length) tags.push('<span class="tag none">no variant data</span>');

  return `<div class="task" data-i="${i}">
    <div class="head">
      <span class="lvl">lv ${esc(t.level||'1')}</span>
      <span class="name">${esc(t.task)}</span>
      <span class="tags">${tags.join('')}</span>
    </div>
    <div class="body">
      ${table}
      <div class="meta">
        ${list('Alternatives (master table)', t.alternatives)}
        ${list('Superiors', t.superiors)}
        ${list('Locations', t.locations)}
        ${list('Required items', t.items)}
        ${list('Requirements', t.requirements)}
        ${t.masters ? `<div><h4>Masters</h4><ul><li>${esc(t.masters)}</li></ul></div>`:''}
      </div>
    </div>
  </div>`;
}

function match(t){
  if (filters.alt && !t.alternatives.length) return false;
  if (filters.var && !t.variants.length) return false;
  if (filters.gap && t.variants.length) return false;
  if (!q) return true;
  const hay = (t.task + ' ' + t.alternatives.join(' ') + ' ' + t.superiors.join(' ') + ' '
    + t.locations.join(' ') + ' ' + t.variants.map(v=>v.monster+' '+v.locations.join(' ')).join(' ')).toLowerCase();
  return hay.includes(q);
}

function render(){
  const shown = DATA.map((t,i)=>[t,i]).filter(([t])=>match(t));
  document.getElementById('list').innerHTML = shown.map(([t,i])=>taskHTML(t,i)).join('')
    || '<p class="empty">Nothing matches.</p>';
  const totalVar = DATA.reduce((n,t)=>n+t.variants.length,0);
  document.getElementById('count').textContent =
    `${shown.length} of ${DATA.length} tasks - ${totalVar} monster rows total`;
  document.querySelectorAll('.head').forEach(h =>
    h.onclick = () => h.parentElement.classList.toggle('open'));
}

document.getElementById('q').oninput = e => { q = e.target.value.toLowerCase().trim(); render(); };
for (const [id,kk] of [['fAlt','alt'],['fVar','var'],['fGap','gap']]) {
  document.getElementById(id).onclick = e => {
    filters[kk] = !filters[kk];
    e.target.classList.toggle('on', filters[kk]);
    render();
  };
}
let allOpen = false;
document.getElementById('expand').onclick = e => {
  allOpen = !allOpen;
  e.target.classList.toggle('on', allOpen);
  e.target.textContent = allOpen ? 'Collapse all' : 'Expand all';
  document.querySelectorAll('.task').forEach(t => t.classList.toggle('open', allOpen));
};

if (ORPHANS.length) {
  document.getElementById('orphanBox').innerHTML =
    `<div class="orphans"><h4 style="margin:0 0 6px;color:var(--warn)">Variant rows that matched no task in the master list (${ORPHANS.length})</h4>
     <div class="note">${ORPHANS.map(esc).join(', ')}</div></div>`;
}

render();
</script>
"""


def main():
    data, orphans = build()
    doc = (TEMPLATE
           .replace("__DATA__", json.dumps(data))
           .replace("__ORPHANS__", json.dumps(orphans)))
    out = os.path.join(DATA, "review.html")
    with open(out, "w", encoding="utf-8") as f:
        f.write(doc)

    n_var = sum(len(t["variants"]) for t in data)
    gaps = [t["task"] for t in data if not t["variants"]]
    print(f"# {len(data)} tasks, {n_var} monster rows -> {out}")
    print(f"# {len(gaps)} tasks with no variant data")
    print(f"# {len(orphans)} orphan variant groups: {orphans}")


if __name__ == "__main__":
    main()
