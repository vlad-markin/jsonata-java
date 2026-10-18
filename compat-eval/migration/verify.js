#!/usr/bin/env node
'use strict';
// Verifies rewrite rules: every rewritten expression must give on the JVM engine the same result as
// the original expression on jsonata-js 2.2.2.
//   node migration/verify.js <cases-with-input.jsonl> <out-dir>
// Steps: ast.js -> Migrate --rewrite -> diff-runner with {"expr": original, "exprJvm": rewritten}.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const [casesFile, outDir] = process.argv.slice(2);
const CE = path.join(__dirname, '..');
fs.mkdirSync(outDir, { recursive: true });
const cases = fs.readFileSync(casesFile, 'utf8').split('\n').filter(l => l.trim()).map(JSON.parse);
const withAst = execFileSync(process.execPath, [path.join(__dirname, 'ast.js'), casesFile], { maxBuffer: 1 << 30 }).toString();
fs.writeFileSync(path.join(outDir, 'ast.jsonl'), withAst);
const mig = execFileSync('java', ['-cp', path.join(CE, 'jvm', 'target', 'classes'), 'compat.migration.Migrate', '--rewrite',
    path.join(outDir, 'ast.jsonl'), '-'], { maxBuffer: 1 << 30, env: Object.assign({}, process.env, { JAVA_TOOL_OPTIONS: '' }) }).toString();
fs.writeFileSync(path.join(outDir, 'migrate.jsonl'), mig);
const rewrites = new Map();
for (const l of mig.split('\n').filter(x => x.trim())) { const r = JSON.parse(l); if (r.type === 'rewrite') rewrites.set(String(r.id), r); }
const verify = [];
for (const c of cases) {
    const r = rewrites.get(String(c.id));
    if (!r) continue;
    verify.push(Object.assign({}, c, { exprJvm: r.rewritten, tags: (c.tags || []).concat(r.rules.map(x => 'rule:' + x)) }));
}
fs.writeFileSync(path.join(outDir, 'verify-cases.jsonl'), verify.map(x => JSON.stringify(x)).join('\n') + '\n');
execFileSync(process.execPath, [path.join(CE, 'diff-runner', 'js', 'run.js'), '--cases', path.join(outDir, 'verify-cases.jsonl'),
    '--out', path.join(outDir, 'run'), '--quiet'], { stdio: 'inherit' });
const diffs = fs.readFileSync(path.join(outDir, 'run', 'diff.jsonl'), 'utf8').split('\n').filter(x => x.trim()).map(JSON.parse);
const byRule = {};
for (const d of diffs) for (const t of d.tags.filter(t => t.startsWith('rule:'))) {
    const k = t.slice(5); byRule[k] = byRule[k] || { total: 0, equivalent: 0, failures: [] };
    byRule[k].total++;
    if (d.category === 'MATCH') byRule[k].equivalent++; else if (byRule[k].failures.length < 5) byRule[k].failures.push(d);
}
fs.writeFileSync(path.join(outDir, 'verify-summary.json'), JSON.stringify(byRule, null, 1));
for (const [k, v] of Object.entries(byRule)) console.log(k + ': ' + v.equivalent + '/' + v.total + ' equivalent');
console.log('cases: ' + cases.length + ', rewritten: ' + verify.length);
