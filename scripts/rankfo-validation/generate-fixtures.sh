#!/usr/bin/env bash
set -euo pipefail

SUBJECT_DIR="$(cd "$(dirname "$0")/../validation-subject" && pwd)"
DT_DIR="$SUBJECT_DIR/.dtfixingtools"

P="com.example.PolluterTest.pollute"
I="com.example.InnocentTest.innocent"
V="com.example.VictimTest.victim"

rm -rf "$DT_DIR"
mkdir -p "$DT_DIR/detection-results/random-class-method"
mkdir -p "$DT_DIR/test-runs/results"

cat > "$DT_DIR/test-runs/results/run0" <<EOF
{
  "id": "run0",
  "testOrder": ["$P", "$I", "$V"],
  "results": {
    "$P": {"name": "$P", "result": "PASS", "time": 0.001, "stackTrace": []},
    "$I": {"name": "$I", "result": "PASS", "time": 0.001, "stackTrace": []},
    "$V": {"name": "$V", "result": "ERROR", "time": 0.002, "stackTrace": ["at com.example.VictimTest.victim(VictimTest.java:8)"]}
  }
}
EOF

cat > "$DT_DIR/test-runs/results/run1" <<EOF
{
  "id": "run1",
  "testOrder": ["$I", "$P", "$V"],
  "results": {
    "$I": {"name": "$I", "result": "PASS", "time": 0.001, "stackTrace": []},
    "$P": {"name": "$P", "result": "PASS", "time": 0.001, "stackTrace": []},
    "$V": {"name": "$V", "result": "ERROR", "time": 0.002, "stackTrace": ["at com.example.VictimTest.victim(VictimTest.java:8)"]}
  }
}
EOF

cat > "$DT_DIR/detection-results/random-class-method/round0.json" <<EOF
{"testRunIds": ["run0"]}
EOF

cat > "$DT_DIR/detection-results/random-class-method/round1.json" <<EOF
{"testRunIds": ["run1"]}
EOF

cat > "$DT_DIR/detection-results/flaky-lists.json" <<EOF
{
  "dts": [
    {
      "name": "$V",
      "intended": {
        "order": [],
        "result": "PASS",
        "testRunId": "intended-isolation"
      },
      "revealed": {
        "order": ["$P", "$I", "$V"],
        "result": "ERROR",
        "testRunId": "run0"
      },
      "type": "OD"
    }
  ]
}
EOF

echo "Fixtures written to $DT_DIR"
