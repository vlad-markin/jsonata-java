#!/usr/bin/env node
// Deterministic generator of an "architecture as code" model.
// Usage: node gen.js <components> <outfile> [seed]
// Structure (depth 5): { systems: [ { id, name, owner, containers: [ { id, name, technology,
//   components: [ { id, name, type, technology, tags[], description,
//                   properties: { owner, cost, criticality, version },
//                   relations: [ { target, kind } ] } ] } ] } ] }
// 1 system = 5 containers x 20 components = 100 components.
'use strict';
const fs = require('fs');

function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const N = parseInt(process.argv[2] || '10000', 10);
const out = process.argv[3] || `model-${N}.json`;
const rnd = mulberry32(parseInt(process.argv[4] || '42', 10));
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
const int = (lo, hi) => lo + Math.floor(rnd() * (hi - lo + 1));

const TYPES = ['service', 'library', 'database', 'queue', 'ui'];
const TECH = ['java', 'kotlin', 'go', 'node', 'python', 'postgres', 'kafka', 'react'];
const TAGS = ['pci', 'gdpr', 'internal', 'external', 'legacy', 'critical', 'batch', 'realtime', 'beta', 'core'];
const CRIT = ['low', 'medium', 'high'];
const KINDS = ['uses', 'calls', 'reads', 'writes'];
const WORDS = ['handles', 'payment', 'requests', 'from', 'customer', 'accounts', 'billing', 'storage',
  'gateway', 'routing', 'messages', 'between', 'internal', 'services', 'reporting', 'analytics',
  'catalog', 'inventory', 'shipping', 'notifications', 'identity', 'session', 'tokens', 'pricing'];
const DOMAINS = ['billing', 'orders', 'catalog', 'identity', 'shipping', 'payments', 'reporting', 'search'];

const pad = (n, w) => String(n).padStart(w, '0');
const systemsCount = Math.ceil(N / 100);
let c = 0;
const systems = [];
for (let s = 0; s < systemsCount && c < N; s++) {
  const sys = { id: 's' + pad(s, 5), name: 'system-' + s + '-' + pick(DOMAINS), owner: 'team-' + int(1, 20), containers: [] };
  for (let k = 0; k < 5 && c < N; k++) {
    const cont = { id: sys.id + '.k' + k, name: 'container-' + s + '-' + k, technology: pick(TECH), components: [] };
    for (let m = 0; m < 20 && c < N; m++, c++) {
      const tags = [];
      const nt = int(1, 3);
      for (let t = 0; t < nt; t++) { const tg = pick(TAGS); if (!tags.includes(tg)) tags.push(tg); }
      const words = [];
      const nw = int(5, 12);
      for (let w = 0; w < nw; w++) words.push(pick(WORDS));
      const rels = [];
      const nr = int(0, 3);
      for (let r = 0; r < nr; r++) rels.push({ target: 'c' + pad(int(0, N - 1), 6), kind: pick(KINDS) });
      cont.components.push({
        id: 'c' + pad(c, 6),
        name: 'svc-' + c + '-' + pick(DOMAINS),
        type: pick(TYPES),
        technology: pick(TECH),
        tags,
        description: words.join(' '),
        properties: { owner: 'team-' + int(1, 20), cost: int(1, 1000), criticality: pick(CRIT), version: int(1, 5) + '.' + int(0, 20) + '.' + int(0, 9) },
        relations: rels,
      });
    }
    sys.containers.push(cont);
  }
  systems.push(sys);
}
fs.writeFileSync(out, JSON.stringify({ generated: { components: N, seed: 42 }, systems }));
console.error(`wrote ${out}: ${c} components, ${systems.length} systems, ${fs.statSync(out).size} bytes`);
