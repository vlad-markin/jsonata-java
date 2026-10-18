#!/usr/bin/env bash
# Phase 1: strict run of the jsonata-js test-suite (from a jsonata checkout, default ../../jsonata at v2.2.2)
# on jsonata-js 2.2.2 (npm) and on the JVM engine. Produces suite/RESULTS-<ver>.md.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
CE="$HERE/.."
SUITE="${1:-$CE/../jsonata/test/test-suite}"
VER="${2:-2.2.2}"
OUT="${OUT:-$CE/out/suite-$VER}"
(cd "$CE/jvm" && mvn -q -B compile)
node "$HERE/suite2cases.js" "$SUITE" > "$HERE/suite-cases-$VER.jsonl"
node "$CE/diff-runner/js/run.js" --cases "$HERE/suite-cases-$VER.jsonl" --out "$OUT"
node "$HERE/report.js" "$OUT" "$CE/../test/test-overrides.json" > "$HERE/RESULTS-$VER.md"
echo "written $HERE/RESULTS-$VER.md"
