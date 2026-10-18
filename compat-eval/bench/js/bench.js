#!/usr/bin/env node
// JS side of the benchmark (jsonata-js 2.2.2). Run with: node --expose-gc bench.js ...
// Usage: node --expose-gc bench.js --queries q.json --data model.json --size N
//        --warmup W --iters I --out results.jsonl [--dump dir] [--only name,name]
'use strict';
const fs = require('fs');
const path = require('path');
const { PerformanceObserver } = require('perf_hooks');
const jsonata = require(path.join(__dirname, '..', '..', 'node_modules', 'jsonata'));

const args = {};
for (let i = 2; i < process.argv.length; i += 2) args[process.argv[i].replace(/^--/, '')] = process.argv[i + 1];
const queries = JSON.parse(fs.readFileSync(args.queries, 'utf8'));
const size = parseInt(args.size, 10);
const warmup = parseInt(args.warmup || '5', 10);
const iters = parseInt(args.iters || '20', 10);
const budgetMs = parseFloat(args['budget-ms'] || '20000');      // measured phase budget per query
const warmBudgetMs = parseFloat(args['warm-budget-ms'] || '15000'); // warmup budget per query
const minIters = parseInt(args['min-iters'] || '3', 10);
const only = args.only ? new Set(args.only.split(',')) : null;
if (typeof global.gc !== 'function') { console.error('run with --expose-gc'); process.exit(2); }

let gcCount = 0;
new PerformanceObserver((list) => { gcCount += list.getEntries().length; }).observe({ entryTypes: ['gc'] });

function pct(sorted, p) { return sorted[Math.min(sorted.length - 1, Math.ceil(p * sorted.length) - 1)]; }
const median = (s) => (s.length % 2 ? s[(s.length - 1) / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2);
const tick = () => new Promise((r) => setImmediate(r));

(async () => {
  const t0 = process.hrtime.bigint();
  const data = JSON.parse(fs.readFileSync(args.data, 'utf8'));
  const loadMs = Number(process.hrtime.bigint() - t0) / 1e6;
  global.gc();
  const baseHeap = process.memoryUsage().heapUsed;
  console.error(`[js] size=${size} load=${loadMs.toFixed(0)}ms dataHeap=${(baseHeap / 1048576).toFixed(0)}MB`);
  for (const q of queries) {
    if (q.sizes && !q.sizes.includes(size)) continue;
    if (only && !only.has(q.name)) continue;
    // compile time: median of 200 compilations
    const ct = [];
    let expr;
    for (let i = 0; i < 200; i++) {
      const s = process.hrtime.bigint();
      expr = jsonata(q.expr);
      ct.push(Number(process.hrtime.bigint() - s) / 1e6);
    }
    ct.sort((a, b) => a - b);
    let result;
    let error = null;
    const times = [], heapDeltas = [], gcs = [];
    let warmDone = 0, warmSpent = 0, measSpent = 0;
    try {
      for (let i = 0; ; i++) {
        const measuring = warmDone >= warmup || (warmDone >= 1 && warmSpent > warmBudgetMs);
        if (measuring && (times.length >= iters || (times.length >= minIters && measSpent > budgetMs))) break;
        global.gc();
        await tick();
        const g0 = gcCount;
        const h0 = process.memoryUsage().heapUsed;
        const s = process.hrtime.bigint();
        const r = await expr.evaluate(data);
        const e = Number(process.hrtime.bigint() - s) / 1e6;
        const h1 = process.memoryUsage().heapUsed;
        if (i === 0) result = r;
        if (!measuring) { warmDone++; warmSpent += e; } else { measSpent += e; times.push(e); heapDeltas.push(h1 - h0); gcs.push(gcCount - g0); }
      }
    } catch (e) {
      error = (e.code || '') + ' ' + e.message;
    }
    if (args.dump) {
      fs.mkdirSync(args.dump, { recursive: true });
      fs.writeFileSync(path.join(args.dump, `${q.name}-${size}.js.json`), JSON.stringify(error ? { __error: error } : (result === undefined ? null : result)));
    }
    const ts = times.slice().sort((a, b) => a - b);
    const hs = heapDeltas.slice().sort((a, b) => a - b);
    const rec = {
      engine: 'jsonata-js-2.2.2', node: process.version, query: q.name, size, warmup_done: warmDone, iters_done: times.length,
      compile_ms_median: median(ct), error,
      eval_ms_median: ts.length ? median(ts) : null, eval_ms_p95: ts.length ? pct(ts, 0.95) : null,
      eval_ms_min: ts.length ? ts[0] : null, eval_ms_max: ts.length ? ts[ts.length - 1] : null,
      heap_delta_mb_median: hs.length ? median(hs) / 1048576 : null,
      gc_during_eval_total: gcs.reduce((a, b) => a + b, 0),
      rss_peak_mb: process.resourceUsage().maxRSS / 1024,
      times_ms: times,
    };
    fs.appendFileSync(args.out, JSON.stringify(rec) + '\n');
    console.error(`[js] ${q.name.padEnd(18)} n=${size} median=${rec.eval_ms_median?.toFixed(2)}ms p95=${rec.eval_ms_p95?.toFixed(2)}ms heapΔ=${rec.heap_delta_mb_median?.toFixed(1)}MB${error ? ' ERROR ' + error : ''}`);
  }
})().catch((e) => { console.error(e); process.exit(1); });
