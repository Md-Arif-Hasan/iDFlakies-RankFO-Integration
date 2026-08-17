#!/usr/bin/env bash
# Usage: bash scripts/validate-rankfo.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SUBJECT_DIR="$SCRIPT_DIR/../validation-subject"
IDFLAKIES_DIR="$SCRIPT_DIR/.."

EXPECTED_POLLUTER="com.example.PolluterTest.pollute"
EXPECTED_VICTIM="com.example.VictimTest.victim"

echo "=== [1/4] Building iDFlakies ==="
mvn -f "$IDFLAKIES_DIR/pom.xml" install -DskipTests -q

echo "=== [2/4] Compiling validation-subject ==="
mvn -f "$SUBJECT_DIR/pom.xml" test-compile -q

echo "=== [3/4] Generating fixtures ==="
bash "$SCRIPT_DIR/generate-fixtures.sh"

echo "=== [4/4] Running minimize ==="
cd "$SUBJECT_DIR"
mvn idflakies:minimize -Ddt.minimizer.strategy=RANKFO_PLUS_ONE -Ddt.verify=true 2>&1 | tee /tmp/rankfo-minimize.log

MINIMIZED_DIR="$SUBJECT_DIR/.dtfixingtools/minimized"

if [ ! -d "$MINIMIZED_DIR" ]; then
  echo "FAIL: minimized/ directory not created"
  exit 1
fi

OUTPUT_FILE=$(ls "$MINIMIZED_DIR"/*.json 2>/dev/null | head -1)
if [ -z "$OUTPUT_FILE" ]; then
  echo "FAIL: no output file in $MINIMIZED_DIR"
  exit 1
fi

FLAKY_CLASS=$(python3 -c "import json; d=json.load(open('$OUTPUT_FILE')); print(d['flakyClass'])")
DEPENDENT_TEST=$(python3 -c "import json; d=json.load(open('$OUTPUT_FILE')); print(d['dependentTest'])")
POLLUTER=$(python3 -c "
import json
d = json.load(open('$OUTPUT_FILE'))
print(d['polluters'][0]['deps'][0] if d.get('polluters') else 'NONE')
")

echo "  dependentTest : $DEPENDENT_TEST"
echo "  flakyClass    : $FLAKY_CLASS"
echo "  polluter      : $POLLUTER"

FAIL=0
[ "$FLAKY_CLASS" != "OD" ] && echo "FAIL: flakyClass='$FLAKY_CLASS', expected 'OD'" && FAIL=1
[ "$DEPENDENT_TEST" != "$EXPECTED_VICTIM" ] && echo "FAIL: dependentTest='$DEPENDENT_TEST'" && FAIL=1
[ "$POLLUTER" != "$EXPECTED_POLLUTER" ] && echo "FAIL: polluter='$POLLUTER', expected '$EXPECTED_POLLUTER'" && FAIL=1

[ "$FAIL" -eq 1 ] && echo "VALIDATION FAILED" && exit 1
echo "VALIDATION PASSED"
