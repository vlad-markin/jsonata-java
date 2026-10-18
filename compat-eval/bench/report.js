#!/usr/bin/env node
// Build markdown summary tables + CSV from results/{js,jvm,jvm-modes}.jsonl
// Usage: node report.js <results-dir>  (writes <dir>/results.csv, prints markdown)
'use strict';
const fs = require('fs');
const path = require('path');
const dir = process.argv[2];
const load = (f) => (fs.existsSync(path.join(dir, f)) ? fs.readFileSync(path.join(dir, f), 'utf8').trim().split('\n').filter(Boolean).map(JSON.parse) : []);
const js = load('js.jsonl'), jvm = load('jvm.jsonl'), modes = load('jvm-modes.jsonl');
const f = (x, d = 1) => (x == null ? '—' : x >= 100 ? x.toFixed(0) : x.toFixed(d));
const key = (r) => r.query + '@' + r.size;
const jvmBy = Object.fromEntries(jvm.map((r) => [key(r), r]));
const jsBy = Object.fromEntries(js.map((r) => [key(r), r]));
const order = [...new Set([...js, ...jvm].map((r) => r.query))];
const rowsAll = [...new Set([...js, ...jvm].map(key))].map((k) => jsBy[k] || { query: jvmBy[k].query, size: jvmBy[k].size, jsMissing: true })
  .sort((x, y) => x.size - y.size || order.indexOf(x.query) - order.indexOf(y.query));
const eqPath = path.join(dir, 'equality.tsv');
const eq = fs.existsSync(eqPath) ? Object.fromEntries(fs.readFileSync(eqPath, 'utf8').trim().split('\n').map((l) => l.split('\t')).map(([k, v]) => [k, v])) : {};

const out = [];
const csv = ['query,size,js_median_ms,js_p95_ms,js_iters,jvm_median_ms,jvm_p95_ms,jvm_iters,ratio_js_over_jvm,js_heap_delta_mb,js_gc_during_eval,jvm_alloc_mb,jvm_heap_peak_mb,js_compile_ms,jvm_compile_ms,equal'];
out.push('| query | N | JS median | JS p95 | JVM median | JVM p95 | JS/JVM | JS heapΔ MB | JVM alloc MB/eval | JVM peak heap MB | equal |');
out.push('|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|');
for (const a of rowsAll) {
  const b = jvmBy[key(a)] || {};
  const ratio = a.eval_ms_median && b.eval_ms_median ? a.eval_ms_median / b.eval_ms_median : null;
  const e = eq[`${a.query}-${a.size}`] || (a.jsMissing ? 'JS not run' : '?');
  out.push(`| ${a.query} | ${a.size / 1000}k | ${f(a.eval_ms_median)} | ${f(a.eval_ms_p95)} | ${f(b.eval_ms_median)} | ${f(b.eval_ms_p95)} | ${ratio == null ? '—' : ratio.toFixed(2) + '×'} | ${f(a.heap_delta_mb_median)}${a.gc_during_eval_total ? '*' : ''} | ${f(b.alloc_mb_median)} | ${f(b.heap_peak_mb)} | ${e} |`);
  csv.push([a.query, a.size, a.eval_ms_median, a.eval_ms_p95, a.iters_done, b.eval_ms_median, b.eval_ms_p95, b.iters_done, ratio, a.heap_delta_mb_median, a.gc_during_eval_total, b.alloc_mb_median, b.heap_peak_mb, a.compile_ms_median, b.compile_ms_median, e].join(','));
}
out.push('');
out.push('Times in ms. JS/JVM > 1 means the JVM engine is faster. `*` = V8 ran GC during the measured evaluation, so heapΔ underestimates allocation.');
out.push('');
out.push('### Compile (parse) time, median of 200, ms');
out.push('');
out.push('| query | JS | JVM |');
out.push('|---|---:|---:|');
for (const a of js.filter((r) => r.size === Math.min(...js.map((x) => x.size)))) {
  const b = jvmBy[key(a)] || {};
  out.push(`| ${a.query} | ${f(a.compile_ms_median, 3)} | ${f(b.compile_ms_median, 3)} |`);
}
if (modes.length) {
  out.push('');
  out.push('### JVM overhead of evaluation-control features (N=50k), median ms (Δ vs plain)');
  out.push('');
  const ms = [...new Set(modes.map((r) => r.mode))];
  const qs = [...new Set(modes.map((r) => r.query))];
  out.push('| query | ' + ms.join(' | ') + ' |');
  out.push('|---|' + ms.map(() => '---:').join('|') + '|');
  for (const q of qs) {
    const base = modes.find((r) => r.query === q && r.mode === 'plain');
    out.push(`| ${q} | ` + ms.map((m) => {
      const r = modes.find((x) => x.query === q && x.mode === m);
      if (!r) return '—';
      if (r.error) return 'ERR: ' + r.error.slice(0, 40);
      if (m === 'plain' || !base) return f(r.eval_ms_median);
      return `${f(r.eval_ms_median)} (${((r.eval_ms_median / base.eval_ms_median - 1) * 100).toFixed(0)}%)`;
    }).join(' | ') + ' |');
  }
  const mcsv = ['mode,query,size,median_ms,p95_ms,alloc_mb,error'];
  for (const r of modes) mcsv.push([r.mode, r.query, r.size, r.eval_ms_median, r.eval_ms_p95, r.alloc_mb_median, JSON.stringify(r.error || '')].join(','));
  fs.writeFileSync(path.join(dir, 'modes.csv'), mcsv.join('\n') + '\n');
}
fs.writeFileSync(path.join(dir, 'results.csv'), csv.join('\n') + '\n');
console.log(out.join('\n'));
