#!/bin/sh

# This script is run inside the Docker container to run the tests.

set -o errexit  # Exit on error (set -e)

## Load overrides for the testing environment if any
if [ -f "${TEST_DATA_ROOT}"/environment ]; then
  echo "sourcing custom environment for tests"
  . "${TEST_DATA_ROOT}"/environment
fi

APP_URL="${APP_URL:-http://localhost:8080/blacklab-server}"
CORPUS_NAME="${CORPUS_NAME:-test}"
READY_TIMEOUT="${BLACKLAB_TEST_READY_TIMEOUT:-120}"
READY_INTERVAL="${BLACKLAB_TEST_READY_INTERVAL:-2}"
READY_URL="${APP_URL}/corpora/${CORPUS_NAME}/hits?patt=%22passport%22"

echo "Waiting for BlackLab Server at ${APP_URL}..."
elapsed=0
while ! wget -q -O /dev/null "${READY_URL}" 2>/dev/null; do
  if [ "${elapsed}" -ge "${READY_TIMEOUT}" ]; then
    echo "BlackLab Server did not become ready within ${READY_TIMEOUT}s."
    echo "Last readiness check failed for: ${READY_URL}"
    exit 1
  fi
  sleep "${READY_INTERVAL}"
  elapsed=$((elapsed + READY_INTERVAL))
done
echo "BlackLab Server is ready after ${elapsed}s."

# Run the tests.
npm run test
