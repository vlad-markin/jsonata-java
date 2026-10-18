#!/usr/bin/env bash
# Phase 5 benchmark: jsonata-js 2.2.2 vs this engine (JVM), same generated data, same queries.
# Usage: ./run-bench.sh [results-dir]      (default ../phase5/results)
# Env:   SIZES="10000 50000 200000"  XMX=4g  BUDGET_MS=20000  SKIP_MODES=1  SKIP_GEN=1
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/../phase5/results}"
SIZES="${SIZES:-10000 50000 200000}"
XMX="${XMX:-4g}"
BUDGET_MS="${BUDGET_MS:-20000}"
mkdir -p "$OUT" "$HERE/data"
cd "$HERE"

[ -d ../node_modules/jsonata ] || (cd .. && npm ci)
(cd jvm && mvn -q package -DskipTests)
JAVA="java -Xms$XMX -Xmx$XMX -XX:+UseG1GC -cp jvm/target/classes"

iters_for() { case "$1" in 10000) echo "10 30";; 50000) echo "5 15";; *) echo "3 10";; esac; }

{
  echo "date: $(date -Is)"; echo "host: $(uname -srm)"; nproc | sed 's/^/cpus: /'
  grep -m1 'model name' /proc/cpuinfo || true; free -g | sed -n 2p | sed 's/^/mem: /'
  echo "node: $(node -v)"; java -version 2>&1 | grep -v JAVA_TOOL | head -2
  echo "jvm flags: -Xms$XMX -Xmx$XMX -XX:+UseG1GC"; echo "budget_ms: $BUDGET_MS"
} > "$OUT/env.txt" 2>&1

rm -f "$OUT/js.jsonl" "$OUT/jvm.jsonl" "$OUT/jvm-modes.jsonl"
for n in $SIZES; do
  [ -n "${SKIP_GEN:-}" ] && [ -f "data/model-$n.json" ] || node gen.js "$n" "data/model-$n.json"
  read -r W I <<<"$(iters_for "$n")"
  node --expose-gc js/bench.js --queries queries.json --data "data/model-$n.json" --size "$n" \
       --warmup "$W" --iters "$I" --budget-ms "$BUDGET_MS" --out "$OUT/js.jsonl" --dump "$OUT/dump"
  $JAVA compat.bench.Bench --queries queries.json --data "data/model-$n.json" --size "$n" \
       --warmup "$W" --iters "$I" --budget-ms "$BUDGET_MS" --out "$OUT/jvm.jsonl" --dump "$OUT/dump" 2>&1 | grep -v JAVA_TOOL
done
node compare.js "$OUT/dump" | tee "$OUT/equality.tsv" || echo "WARNING: result mismatch, see equality.tsv"

# overhead of frame / callbacks / setRuntimeBounds / Options guardrails / input validation (JVM, 50k)
if [ -z "${SKIP_MODES:-}" ]; then
  Q=path_filter,group_nested,map_filter_reduce,strings,recursion_tree,tail_loop
  for m in plain frame novalidate callbacks bounds guardrails; do
    $JAVA compat.bench.Bench --queries queries.json --data data/model-50000.json --size 50000 \
         --warmup 5 --iters 15 --budget-ms "$BUDGET_MS" --only "$Q" --mode "$m" --out "$OUT/jvm-modes.jsonl" 2>&1 | grep -v JAVA_TOOL
  done
fi
rm -rf "$OUT/dump"   # large; equality.tsv keeps the verdict
node report.js "$OUT" > "$OUT/summary.md"
cat "$OUT/summary.md"
