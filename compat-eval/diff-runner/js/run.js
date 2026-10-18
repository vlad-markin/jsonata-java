#!/usr/bin/env node
'use strict';
// diff-runner orchestrator.
//
//   node diff-runner/js/run.js --cases cases.jsonl --out out/dir [--timeout 20000] [--only js|jvm]
//        [--jvm-cmd "java -cp ... compat.diff.RunCases"] [--engine my.EngineClass] [--reuse]
//
// Writes into --out: js.jsonl, jvm.jsonl, diff.jsonl, summary.json, summary.md
// With --reuse existing js.jsonl / jvm.jsonl in --out are reused instead of re-running.
const fs = require('fs');
const path = require('path');
const { runSupervised } = require('./supervise');
const { compareResults, checkExpected } = require('./compare');
const { renderOutcome } = require('./render');
const { stringify } = require('./canon');

const ROOT = path.join(__dirname, '..', '..');

function parseArgs(argv) {
    const a = { timeout: 20000 };
    for (let i = 2; i < argv.length; i++) {
        const k = argv[i];
        const v = () => argv[++i];
        if (k === '--cases') a.cases = v();
        else if (k === '--out') a.out = v();
        else if (k === '--timeout') a.timeout = Number(v());
        else if (k === '--only') a.only = v();
        else if (k === '--jvm-cmd') a.jvmCmd = v();
        else if (k === '--engine') a.engine = v();
        else if (k === '--reuse') a.reuse = true;
        else if (k === '--quiet') a.quiet = true;
        else throw new Error('unknown arg ' + k);
    }
    if (!a.cases || !a.out) throw new Error('usage: run.js --cases cases.jsonl --out dir');
    return a;
}

function readJsonl(file) {
    return fs.readFileSync(file, 'utf8').split('\n').map(l => l.trim()).filter(l => l && !l.startsWith('//'))
        .map(l => { const c = JSON.parse(l); c.__line = l; return c; });
}

// line by line: a run can exceed the maximum string length of V8
function writeJsonl(file, records) {
    const fd = fs.openSync(file, 'w');
    try {
        for (const r of records) fs.writeSync(fd, stringify(r) + '\n');
    } finally {
        fs.closeSync(fd);
    }
}

function jvmCommand(a) {
    if (a.jvmCmd) { const p = a.jvmCmd.split(' '); return { cmd: p[0], args: p.slice(1) }; }
    const cp = [path.join(ROOT, 'jvm', 'target', 'classes')];
    if (process.env.JVM_EXTRA_CP) cp.push(process.env.JVM_EXTRA_CP);
    const args = ['-Xss16m', '-Xmx2g', '-cp', cp.join(path.delimiter)];
    if (a.engine) args.push('-Dengine=' + a.engine);
    args.push('compat.diff.RunCases');
    return { cmd: 'java', args };
}

async function runEngine(name, cmd, args, cases, a, file) {
    if (a.reuse && fs.existsSync(file)) {
        const m = new Map(readJsonl(file).map(r => [r.id, r]));
        if (cases.every(c => m.has(String(c.id)))) return cases.map(c => m.get(String(c.id)));
    }
    let n = 0;
    const t0 = Date.now();
    const { results, restarts } = await runSupervised({
        cmd, args, cases, timeoutMs: a.timeout, engineName: name,
        env: { JAVA_TOOL_OPTIONS: '' },
        onResult: () => { n++; if (!a.quiet && n % 2000 === 0) process.stderr.write(name + ': ' + n + '/' + cases.length + '\n'); },
    });
    writeJsonl(file, results);
    if (!a.quiet) process.stderr.write(name + ': done ' + cases.length + ' cases in ' + (Date.now() - t0) + ' ms, restarts=' + restarts + '\n');
    return results;
}

async function main() {
    const a = parseArgs(process.argv);
    fs.mkdirSync(a.out, { recursive: true });
    const cases = readJsonl(a.cases);
    for (const c of cases) c.id = String(c.id);
    const tasks = [];
    if (a.only !== 'jvm') tasks.push(runEngine('js', process.execPath, [path.join(__dirname, 'worker-js.js')], cases, a, path.join(a.out, 'js.jsonl')));
    else tasks.push(Promise.resolve(null));
    if (a.only !== 'js') { const j = jvmCommand(a); tasks.push(runEngine('jvm', j.cmd, j.args, cases, a, path.join(a.out, 'jvm.jsonl'))); }
    else tasks.push(Promise.resolve(null));
    const [js, jvm] = await Promise.all(tasks);
    if (!js || !jvm) return;

    const diffs = [];
    const byCat = {};
    const byTag = {};
    const expectedStats = { js: { pass: 0, fail: 0 }, jvm: { pass: 0, fail: 0 } };
    for (let i = 0; i < cases.length; i++) {
        const c = cases[i];
        const cmp = compareResults(js[i], jvm[i]);
        const rec = { id: c.id, category: cmp.category, kinds: cmp.kinds, info: cmp.info, details: cmp.details.slice(0, 5),
            expr: c.expr, js: renderOutcome(js[i]), jvm: renderOutcome(jvm[i]) };
        if (c.tags) rec.tags = c.tags;
        if (c.expected) {
            const ej = checkExpected(c.expected, js[i]), ev = checkExpected(c.expected, jvm[i]);
            rec.expectedJs = ej; rec.expectedJvm = ev;
            expectedStats.js[ej.pass ? 'pass' : 'fail']++;
            expectedStats.jvm[ev.pass ? 'pass' : 'fail']++;
        }
        diffs.push(rec);
        byCat[cmp.category] = (byCat[cmp.category] || 0) + 1;
        for (const t of c.tags || ['untagged']) {
            byTag[t] = byTag[t] || { total: 0, match: 0 };
            byTag[t].total++;
            if (cmp.category === 'MATCH') byTag[t].match++;
        }
    }
    writeJsonl(path.join(a.out, 'diff.jsonl'), diffs);
    const summary = { cases: cases.length, categories: byCat, tags: byTag, expected: expectedStats,
        js: js[0] && js[0].engine, jvm: jvm[0] && jvm[0].engine };
    fs.writeFileSync(path.join(a.out, 'summary.json'), JSON.stringify(summary, null, 2) + '\n');
    let md = '# diff-runner summary\n\nCases: ' + cases.length + '\n\n| category | count |\n|---|---|\n';
    for (const [k, v] of Object.entries(byCat).sort((x, y) => y[1] - x[1])) md += '| ' + k + ' | ' + v + ' |\n';
    fs.writeFileSync(path.join(a.out, 'summary.md'), md);
    if (!a.quiet) process.stderr.write(JSON.stringify(byCat) + '\n');
}

main().catch(e => { console.error(e); process.exit(1); });
