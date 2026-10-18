#!/usr/bin/env node
'use strict';
// node divergences/build.js cases            -> writes cases/divergences.jsonl
// node divergences/build.js report <run-dir> -> writes divergences.md (catalog + JS/JVM results)
const fs = require('fs');
const path = require('path');
const catalog = require('./catalog');
const CE = path.join(__dirname, '..');
const mode = process.argv[2];

function caseList() {
    const out = [];
    for (const d of catalog) d.cases.forEach((c, i) => out.push(Object.assign({ id: d.id + '#' + (i + 1), tags: ['div', 'div:' + d.id] }, c)));
    return out;
}
if (mode === 'cases') {
    const cs = caseList();
    fs.writeFileSync(path.join(CE, 'cases', 'divergences.jsonl'), cs.map(c => JSON.stringify(c)).join('\n') + '\n');
    console.error('cases: ' + cs.length);
} else if (mode === 'report') {
    const run = process.argv[3];
    const diffs = new Map(fs.readFileSync(path.join(run, 'diff.jsonl'), 'utf8').trim().split('\n').map(l => { const d = JSON.parse(l); return [d.id, d]; }));
    const SOL = { engine: 'исправить движок', rewrite: 'переписать выражение', exception: 'согласованное исключение', question: 'решение за вами (см. «Вопросы»)' };
    const esc = s => String(s).replace(/\|/g, '\\|').replace(/\n/g, '\\n');
    const cnt = {}; for (const d of catalog) cnt[d.solution] = (cnt[d.solution] || 0) + 1;
    let md = fs.readFileSync(path.join(__dirname, 'header.md'), 'utf8');
    md += '\n## Сводка\n\n| id | категория | серьёзность | решение | сложность | статус | кратко |\n|---|---|---|---|---|---|---|\n';
    const status = {};
    for (const d of catalog) {
        const res = d.cases.map((c, i) => diffs.get(d.id + '#' + (i + 1))).filter(Boolean);
        // ERROR_TOKEN is ERR-03 (structure of the error object); ERR-04 is about messages
        const ok = new Set(['MATCH', 'CRASH_BOTH']);
        if (d.id !== 'ERR-03') ok.add('ERROR_TOKEN');
        const relevant = res.filter(r => !ok.has(r.category) || (d.id === 'ERR-04' && (r.info || []).includes('ERROR_MESSAGE')));
        status[d.id] = d.cases.length === 0 ? (d.fixedBy ? 'устранено (' + d.fixedBy + ')' : 'см. evidence') : (relevant.length === 0 ? 'устранено' : 'воспроизводится ' + relevant.length + '/' + res.length);
        md += `| [${d.id}](#${d.id.toLowerCase()}) | ${d.cat} | ${d.severity} | ${SOL[d.solution]} | ${d.complexity} | ${status[d.id]} | ${esc(d.title).slice(0, 110)}${d.title.length > 110 ? '…' : ''} |\n`;
    }
    md += '\n## Подробно\n';
    for (const d of catalog) {
        md += `\n### ${d.id}\n\n**${esc(d.title)}**\n\nКатегория: ${d.cat}; серьёзность: ${d.severity}; решение: **${SOL[d.solution]}**; сложность: ${d.complexity}; статус: ${status[d.id]}.\n\n`;
        if (d.js) md += `- JS 2.2.2: ${d.js}\n`;
        if (d.jvm) md += `- JVM: ${d.jvm}\n`;
        if (d.evidence) md += `- Доказательство: ${d.evidence}\n`;
        if (d.note) md += `- Примечание: ${d.note}\n`;
        if (d.cases.length) {
            md += '\n| пример | вход | JS 2.2.2 | JVM | категория |\n|---|---|---|---|---|\n';
            d.cases.forEach((c, i) => {
                const r = diffs.get(d.id + '#' + (i + 1));
                const extra = [c.input !== undefined ? JSON.stringify(c.input) : '', c.functions ? 'host: ' + c.functions.map(f => f.name + (f.signature || '')).join(',') : '', c.options ? 'options ' + JSON.stringify(c.options) : '', c.bounds ? 'bounds ' + JSON.stringify(c.bounds) : ''].filter(Boolean).join('; ');
                md += `| \`${esc(c.expr)}\` | ${esc(extra)} | ${r ? esc(r.js).slice(0, 140) : '—'} | ${r ? esc(r.jvm).slice(0, 140) : '—'} | ${r ? r.category : '—'} |\n`;
            });
        }
    }
    fs.writeFileSync(path.join(CE, 'divergences.md'), md);
    console.error('written divergences.md');
} else {
    console.error('usage: build.js cases | report <run-dir>');
    process.exit(2);
}
