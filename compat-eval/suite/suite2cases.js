#!/usr/bin/env node
'use strict';
// Converts the jsonata-js test-suite (test/test-suite of a jsonata checkout) into diff-runner cases.
//   node suite/suite2cases.js <path-to-jsonata>/test/test-suite > suite-cases.jsonl
// Case ids follow the naming of test/test-overrides.json: "<group>/<file>.json" or "<group>/<file>.json_<n>".
const fs = require('fs');
const path = require('path');
const { stringify } = require('../diff-runner/js/canon');

const root = process.argv[2];
if (!root) { console.error('usage: suite2cases.js <test-suite dir>'); process.exit(2); }
const datasets = {};
for (const f of fs.readdirSync(path.join(root, 'datasets'))) {
    datasets[f.replace('.json', '')] = JSON.parse(fs.readFileSync(path.join(root, 'datasets', f), 'utf8'));
}
const groupsDir = path.join(root, 'groups');
const out = [];
for (const group of fs.readdirSync(groupsDir).sort()) {
    const gdir = path.join(groupsDir, group);
    if (!fs.statSync(gdir).isDirectory()) continue;
    for (const file of fs.readdirSync(gdir).filter(n => n.endsWith('.json')).sort()) {
        const spec = JSON.parse(fs.readFileSync(path.join(gdir, file), 'utf8'));
        const list = Array.isArray(spec) ? spec : [spec];
        list.forEach((t, i) => {
            const id = group + '/' + file + (Array.isArray(spec) ? '_' + i : '');
            let expr = t.expr;
            if (t['expr-file']) expr = fs.readFileSync(path.join(gdir, t['expr-file']), 'utf8');
            const c = { id, group, expr, tags: ['suite', 'group:' + group] };
            if ('data' in t) c.input = t.data;
            else if (t.dataset) c.input = datasets[t.dataset];
            if (c.input === undefined) delete c.input;
            if (t.bindings) c.bindings = t.bindings;
            if ('timelimit' in t && 'depth' in t) c.bounds = { timelimit: t.timelimit, depth: t.depth };
            const exp = {};
            if ('undefinedResult' in t) exp.undefinedResult = true;
            else if ('result' in t) exp.result = t.result;
            else if (t.error) { exp.code = t.error.code; if ('position' in t.error) exp.position = t.error.position; if ('token' in t.error) exp.token = t.error.token; }
            else if (t.code) { exp.code = t.code; if ('token' in t) exp.token = t.token; }
            c.expected = exp;
            out.push(stringify(c));
        });
    }
}
process.stdout.write(out.join('\n') + '\n');
process.stderr.write('cases: ' + out.length + '\n');
