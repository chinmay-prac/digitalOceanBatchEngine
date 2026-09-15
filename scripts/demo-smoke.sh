#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
RESULTS_FILE="$(mktemp)"
PROMPTS_FILE="$(mktemp -t prompts.XXXXXX.txt)"

cleanup() {
  rm -f "$RESULTS_FILE" "$PROMPTS_FILE"
}
trap cleanup EXIT

for command in curl python3; do
  command -v "$command" >/dev/null || {
    echo "Required command not found: $command" >&2
    exit 1
  }
done

echo "1. Health"
curl --fail --silent --show-error "$BASE_URL/actuator/health"
echo

echo "2. Mock endpoint returns HTTP 429"
MOCK_STATUS="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"rate limited","promptIndex":3,"attempt":1}' \
  "$BASE_URL/mock/v1/inference")"
if [[ "$MOCK_STATUS" != "429" ]]; then
  echo "Expected 429, received $MOCK_STATUS" >&2
  exit 1
fi
echo "HTTP $MOCK_STATUS"

echo "3. Submit JSON batch"
SUBMISSION="$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '["one","two","three","four"]' \
  "$BASE_URL/api/v1/batches")"
echo "$SUBMISSION"
BATCH_ID="$(printf '%s' "$SUBMISSION" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["batchId"])')"

echo "4. Poll progress and results"
for attempt in {1..60}; do
  PROGRESS="$(curl --fail --silent --show-error \
    "$BASE_URL/api/v1/batches/$BATCH_ID")"
  echo "$PROGRESS"
  STATUS_CODE="$(curl --silent --show-error \
    --output "$RESULTS_FILE" \
    --write-out '%{http_code}' \
    "$BASE_URL/api/v1/batches/$BATCH_ID/results")"
  if [[ "$STATUS_CODE" == "200" ]]; then
    break
  fi
  sleep 1
done
if [[ "$STATUS_CODE" != "200" ]]; then
  echo "Batch did not finish within 60 seconds" >&2
  exit 1
fi
cat "$RESULTS_FILE"
echo

echo "5. Submit TXT batch"
printf 'alpha\nbeta\ngamma\n' > "$PROMPTS_FILE"
curl --fail --silent --show-error \
  -F "file=@$PROMPTS_FILE;filename=prompts.txt;type=text/plain" \
  "$BASE_URL/api/v1/batches"
echo

echo "Demo smoke test passed"
