#!/usr/bin/env node
'use strict';
// Adds the jsonata-js 2.2.2 ast() to each case: reads JSONL {"id","expr",...}, writes {"id","expr","ast"}.
//   node migration/ast.js cases.jsonl > with-ast.jsonl
// Expressions that do not compile get {"astError": {...}}.
const fs = require('fs');
const path = require('path');
const jsonata = require(path.join(__dirname, '..', 'node_modules', 'jsonata'));
const lines = fs.readFileSync(process.argv[2] || 0, 'utf8').split('\n').filter(l => l.trim());
for (const l of lines) {
    const c = JSON.parse(l);
    const out = { id: c.id, expr: c.expr };
    try { out.ast = jsonata(c.expr).ast(); } catch (e) { out.astError = { code: e.code, position: e.position, message: e.message }; }
    process.stdout.write(JSON.stringify(out) + '\n');
}
