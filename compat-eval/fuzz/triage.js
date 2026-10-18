#!/usr/bin/env node
'use strict';
// Clusters diff-runner output into candidate divergences.
//   node fuzz/triage.js out/run [--min 1] [--examples 3] [--category X] > triage.md
const fs = require('fs');
const path = require('path');
const args = process.argv.slice(2);
const dir = args[0];
const opt = (k, d) => { const i = args.indexOf(k); return i >= 0 ? args[i + 1] : d; };
const nEx = Number(opt('--examples', 3));
const onlyCat = opt('--category', null);
const diffs = fs.readFileSync(path.join(dir, 'diff.jsonl'), 'utf8').trim().split('\n').map(JSON.parse);
const IGN = new Set(['MATCH', 'BOTH_TIMEOUT', 'CRASH_BOTH']);
function signature(d) {
    const zone = (d.tags || []).find(t => t.startsWith('zone:')) || '';
    const fns = (d.expr.match(/\$[a-zA-Z]+(?=\()/g) || []).filter((v, i, a) => a.indexOf(v) === i).sort().slice(0, 3).join(',');
    let detail = '';
    if (d.category === 'ERROR_CODE' || d.category === 'ERROR_POSITION' || d.category === 'ERROR_TOKEN') {
        const x = d.details.find(z => z.kind === d.category) || {};
        detail = d.category === 'ERROR_CODE' ? (x.js + '->' + x.jvm) : (d.js.split(' ')[1] || '').replace(/@.*/, '');
    } else if (d.category === 'JVM_CRASH') {
        detail = (d.jvm.match(/crash (\S+)/) || [])[1] || '';
    } else if (d.category === 'JVM_NO_ERROR' || d.category === 'JVM_ERROR') {
        detail = (d.js.match(/error (\w+)/) || d.jvm.match(/error (\w+)/) || [])[1] || '';
    }
    return [d.category, zone, fns, String(detail).slice(0, 60)].join(' | ');
}
const clusters = new Map();
for (const d of diffs) {
    if (IGN.has(d.category)) continue;
    if (onlyCat && d.category !== onlyCat) continue;
    const s = signature(d);
    if (!clusters.has(s)) clusters.set(s, []);
    clusters.get(s).push(d);
}
const sorted = [...clusters.entries()].sort((a, b) => b[1].length - a[1].length);
let md = '# triage ' + dir + '\n\n';
const esc = s => String(s).replace(/\|/g, '\\|').replace(/\n/g, ' ');
for (const [s, list] of sorted) {
    md += '## ' + esc(s) + ' — ' + list.length + '\n';
    list.sort((a, b) => a.expr.length - b.expr.length);
    for (const d of list.slice(0, nEx)) md += '- `' + esc(d.expr).slice(0, 160) + '` [' + d.id + '] JS: ' + esc(d.js).slice(0, 120) + ' | JVM: ' + esc(d.jvm).slice(0, 160) + '\n';
}
process.stdout.write(md);
