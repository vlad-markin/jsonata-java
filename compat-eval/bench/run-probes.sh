#!/usr/bin/env bash
# Evaluation-control probes (cancellation, time budgets, memory budget, threads, virtual threads).
# Usage: ./run-probes.sh [outfile]   (default ../phase5/results/control-probes.txt)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"
OUT="${1:-$HERE/../phase5/results/control-probes.txt}"
(cd jvm && mvn -q package -DskipTests)
[ -f data/model-10000.json ] || node gen.js 10000 data/model-10000.json
java -Xmx2g -cp jvm/target/classes compat.bench.ControlProbes data/model-10000.json 2>&1 | grep -v JAVA_TOOL | tee "$OUT"
