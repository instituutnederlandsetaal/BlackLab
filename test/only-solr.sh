#!/bin/bash

# Run the integration tests.
#
#   testrunner.sh <servicename>
#
# For <servicename>, use "test" for CI testing,
# "test-local" to run locally (and automatically save
# missing responses).

set -o errexit  # Exit on error (set -e)

# Get the servicename (or default to "test", the regular CI test)
SERVICE_NAME=test

if [ "$1" = "test-local" ]; then
    export BLACKLAB_TEST_SAVE_MISSING_RESPONSES=true
elif [ "$1" != "" ]; then
    echo "Unknown action '$1'. Use 'test' or 'test-local'."
    exit 1
fi

# Go to the test dir
cd $( dirname -- "$0"; )/

# Check how to call Compose
#COMPOSE=docker-compose
#if ! command -v $COMPOSE &> /dev/null
#then
    COMPOSE="docker compose"
#fi

# Enable BuildKit
export DOCKER_BUILDKIT=1

##----------------------------------------------------------
## Re-run the same tests using Solr+proxy
echo === Testing Solr \(with integrated index format\)...
$COMPOSE build proxy solr "$SERVICE_NAME"
export BLACKLAB_FEATURE_defaultIndexType=integrated
export INDEX_TYPE=solr
$COMPOSE down -v  # delete previous index so it updates if it was changed in the repo
$COMPOSE up --force-recreate -d proxy solr
export APP_URL=http://proxy:8080/blacklab-server
export CORPUS_NAME=test
export SKIP_INDEXING_TESTS=true   # not yet implemented for Solr
sleep 15 # allow Solr a little time to start up
$COMPOSE run --rm "$SERVICE_NAME"
$COMPOSE stop # (stop then rm -v instead of down -v, otherwise we get an error about the volume being in use)
$COMPOSE rm -fv
