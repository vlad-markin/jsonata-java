#!/usr/bin/env bash
# A/B of the JVM engine vs a locally patched copy (alternating processes, 2 rounds each).
# Usage: ./ab.sh <classesA> <classesB> <out.jsonl> <size> <queries-csv> [rounds]
# Build a patched copy with:  (cd jvm && mvn -q package -Dengine.src=/path/to/patched/src -Dbuild.dir=/path/to/build)
set -euo pipefail
cd "$(dirname "$0")"
A=$1; B=$2; OUT=$3; N=$4; Q=$5; R=${6:-2}
for r in $(seq 1 "$R"); do
  for v in A B; do
    cp=$A; [ $v = B ] && cp=$B
    nice -n -10 java -Xms3g -Xmx3g -cp "$cp" compat.bench.Bench --queries queries.json --data data/model-$N.json --size $N \
      --only "$Q" --warmup 5 --iters 10 --budget-ms 10000 --mode plain --out "$OUT.$v" 2>&1 | grep -v JAVA_TOOL | sed "s/^/[$v r$r] /"
  done
done
