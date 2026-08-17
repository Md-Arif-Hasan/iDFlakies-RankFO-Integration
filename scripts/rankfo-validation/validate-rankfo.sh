#!/usr/bin/env bash
# Usage: bash scripts/rankfo-validation/validate-rankfo.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
IDFLAKIES_DIR="$SCRIPT_DIR/../.."
SUBJECT_DIR="$IDFLAKIES_DIR/validation-subject"

STRATEGIES=(RANKFO_PLUS_ONE RANKFO_METHODS RANKFO_DISTANCE_D RANKFO_COMBINED_P1_D RANKFO_COMBINED_M_D)

echo "=== [1/4] Building iDFlakies ==="
mvn -f "$IDFLAKIES_DIR/pom.xml" install -DskipTests -q

echo "=== [2/4] Compiling validation-subject ==="
mvn -f "$SUBJECT_DIR/pom.xml" test-compile -q

echo "=== [3/4] Generating fixtures (4 tests, 10 sampled orders) ==="
bash "$SCRIPT_DIR/generate-fixtures.sh"

echo "=== [4/4] Running minimize + verifying scores for all ${#STRATEGIES[@]} heuristics ==="
FAIL=0
for STRATEGY in "${STRATEGIES[@]}"; do
  echo "--- $STRATEGY ---"
  (cd "$SUBJECT_DIR" && mvn idflakies:minimize -Ddt.minimizer.strategy="$STRATEGY" -Ddt.verify=true) \
    2>&1 | tee "/tmp/rankfo-minimize-$STRATEGY.log"

  if ! python3 "$SCRIPT_DIR/verify_scores.py" --strategy "$STRATEGY"; then
    FAIL=1
  fi
done

[ "$FAIL" -eq 1 ] && echo "VALIDATION FAILED" && exit 1
echo "VALIDATION PASSED"
