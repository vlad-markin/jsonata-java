#!/usr/bin/env bash
# JFR profiling of the JVM engine on selected benchmark queries.
# Usage: ./prof.sh [outdir]   (default ../phase5/results/profile)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"
OUT="${1:-$HERE/../phase5/results/profile}"; mkdir -p "$OUT" prof
(cd jvm && mvn -q package -DskipTests)
run() { # query size seconds
  local q=$1 n=$2 s=$3
  nice -n -10 java -Xms3g -Xmx3g -XX:+UseG1GC -XX:StartFlightRecording=filename=prof/$q-$n.jfr,settings=profile \
       -cp jvm/target/classes compat.bench.Bench --queries queries.json --data data/model-$n.json --size $n \
       --only $q --warmup 3 --iters 100000 --min-iters 3 --budget-ms $((s*1000)) --out prof/prof.jsonl 2>&1 | grep -v JAVA_TOOL | grep -v '^\[0' || true
  python3 jfr_agg.py prof/$q-$n.jfr 25 Jsonata.evaluate > "$OUT/$q-$n.txt"
}
run path_filter 50000 20
run group_nested 50000 15
run group_agg 10000 15
run join_scan 50000 15
run sort_fn 10000 15
run strings 50000 15
run recursion_tree 50000 20
run map_filter_reduce 50000 15
run tail_loop 200000 15
