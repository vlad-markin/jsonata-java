#!/usr/bin/env node
// Compare JS and JVM result dumps: node compare.js <dumpdir>  -> prints table, exit 1 on mismatch
'use strict';
const fs = require('fs');
const path = require('path');
const dir = process.argv[2];
function eq(a, b) {
  if (a === b) return true;
  if (typeof a === 'number' && typeof b === 'number') return Math.abs(a - b) <= 1e-9 * Math.max(1, Math.abs(a));
  if (Array.isArray(a)) return Array.isArray(b) && a.length === b.length && a.every((x, i) => eq(x, b[i]));
  if (a && b && typeof a === 'object' && typeof b === 'object' && !Array.isArray(b)) {
    const ka = Object.keys(a), kb = Object.keys(b);
    return ka.length === kb.length && ka.every((k) => Object.prototype.hasOwnProperty.call(b, k) && eq(a[k], b[k]));
  }
  return false;
}
let bad = 0;
const rows = [];
for (const f of fs.readdirSync(dir).filter((f) => f.endsWith('.js.json')).sort()) {
  const base = f.replace(/\.js\.json$/, '');
  const jf = path.join(dir, base + '.jvm.json');
  if (!fs.existsSync(jf)) { rows.push([base, 'missing jvm']); continue; }
  const a = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
  const b = JSON.parse(fs.readFileSync(jf, 'utf8'));
  const ok = eq(a, b);
  if (!ok) bad++;
  const sz = fs.statSync(path.join(dir, f)).size;
  rows.push([base, ok ? 'EQUAL' : 'DIFF', sz]);
}
for (const r of rows) console.log(r.join('\t'));
process.exit(bad ? 1 : 0);
