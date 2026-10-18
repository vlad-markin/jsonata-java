#!/usr/bin/env node
'use strict';
// Builds the phase-1 tables from a diff-runner run over suite cases.
//   node suite/report.js <run-dir> <test-overrides.json> > suite/RESULTS.md
const fs = require('fs');
const path = require('path');
const [runDir, overridesFile] = process.argv.slice(2);
const diffs = fs.readFileSync(path.join(runDir, 'diff.jsonl'), 'utf8').trim().split('\n').map(JSON.parse);
const overrides = JSON.parse(fs.readFileSync(overridesFile, 'utf8')).override;
// same matching rule as JsonataTest.getOverrideForTest: substring match of the override name in the test name
function overrideFor(id) {
    // JsonataTest names sub-cases "<path>/<file>.json_<n>" and whole files "<path>/<file>.json"
    return overrides.find(o => id.indexOf(o.name) >= 0) || null;
}
const groups = {};
const rows = [];
const usedOverrides = new Set();
for (const d of diffs) {
    const g = d.id.split('/')[0];
    const G = groups[g] = groups[g] || { total: 0, pass: 0, fail: 0, soft: 0, ovr: 0, ovrPassing: 0, legacyPass: 0 };
    G.total++;
    const ev = d.expectedJvm;
    const ov = overrideFor(d.id);
    if (ov) { G.ovr++; usedOverrides.add(ov.name); if (ev.pass) G.ovrPassing++; }
    if (ev.pass) G.pass++;
    else if (ev.soft) G.soft++;
    else G.fail++;
    if (!ev.pass) rows.push({ id: d.id, why: ev.why, soft: !!ev.soft, override: ov, expr: d.expr, js: d.js, jvm: d.jvm, category: d.category });
}
const T = Object.values(groups).reduce((a, g) => { for (const k in g) a[k] = (a[k] || 0) + g[k]; return a; }, {});
let md = '# Фаза 1: test-suite jsonata-js v2.2.2 на JVM-движке (строгая проверка)\n\n';
md += 'Сгенерировано `suite/report.js`. Проверка строгая: значение (порядок ключей не важен, как chai deep.equal), ' +
    'код ошибки, а также `token`/`position`, если они указаны в тесте. Overrides НЕ применяются; колонка «в overrides» показывает, ' +
    'сколько случаев группы покрыто записью `test/test-overrides.json`.\n\n';
md += `Итого: всего **${T.total}**, прошло **${T.pass}**, упало **${T.fail}** (+ ${T.soft} только по token/position), в overrides ${T.ovr} (из них сейчас проходят ${T.ovrPassing}).\n\n`;
md += '| группа | всего | прошло | упало | упало (только token/position) | в overrides |\n|---|---:|---:|---:|---:|---:|\n';
for (const [g, v] of Object.entries(groups).sort()) {
    md += `| ${g} | ${v.total} | ${v.pass} | ${v.fail} | ${v.soft} | ${v.ovr} |\n`;
}
md += `| **итого** | **${T.total}** | **${T.pass}** | **${T.fail}** | **${T.soft}** | **${T.ovr}** |\n\n`;
md += '## Упавшие случаи\n\n| id | override | причина | выражение | JS 2.2.2 | JVM |\n|---|---|---|---|---|---|\n';
const esc = s => String(s).replace(/\|/g, '\\|').replace(/\n/g, ' ').slice(0, 150);
for (const r of rows.filter(r => !r.soft)) md += `| ${r.id} | ${r.override ? 'да' : ''} | ${esc(r.why)} | \`${esc(r.expr)}\` | ${esc(r.js)} | ${esc(r.jvm)} |\n`;
md += '\n## Расхождения только по token/position ошибки\n\n| id | причина | выражение |\n|---|---|---|\n';
for (const r of rows.filter(r => r.soft)) md += `| ${r.id} | ${esc(r.why)} | \`${esc(r.expr)}\` |\n`;
// override coverage: exact vs accidental (substring) matches, stale entries
md += '\n## Покрытие overrides\n\nJsonataTest сопоставляет запись по подстроке (`name.indexOf(override)`), поэтому запись `..._1` покрывает и `..._11`, `..._12` и т.д.\n\n';
md += '| override | случаи, к которым применяется | сейчас проходят строго |\n|---|---|---|\n';
for (const o of overrides) {
    const hit = diffs.filter(d => d.id.indexOf(o.name) >= 0);
    md += `| ${o.name} | ${hit.map(d => d.id === o.name ? 'точно' : d.id.replace(/.*\//, '')).join(', ') || '—'} | ${hit.filter(d => d.expectedJvm.pass).map(d => d.id.replace(/.*\//, '')).join(', ')} |\n`;
}
process.stdout.write(md);
fs.writeFileSync(path.join(runDir, 'suite-failures.json'), JSON.stringify(rows, null, 1));
