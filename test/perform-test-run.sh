#!/bin/sh

# This script is run inside the Docker container to run the tests.

set -o errexit  # Exit on error (set -e)

## Load overrides for the testing environment if any
if [ -f "${TEST_DATA_ROOT}"/environment ]; then
  echo "sourcing custom environment for tests"
  . "${TEST_DATA_ROOT}"/environment
fi

sleep 5

# Ensure the server is awake and the index has been opened.
wget -O - "${APP_URL:-http://localhost:8080/blacklab-server}"/corpora/"${CORPUS_NAME:-test}"/hits?patt=%22passport%22 > /dev/null

# Run the tests.
npm run test
