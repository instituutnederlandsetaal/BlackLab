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
SERVICE_NAME="${1:-test}"

# Go to the test dir
cd "$( dirname -- "$0"; )"/

# Ensure latest-test-output dirs exist
mkdir -p data/latest-test-output data/latest-test-output-integrated

# Check how to call Compose
#COMPOSE=docker-compose
#if ! command -v $COMPOSE &> /dev/null
#then
    COMPOSE="docker compose"
#fi

# Enable BuildKit
export DOCKER_BUILDKIT=1

#----------------------------------------------------------
# Build and run BlackLab Server
echo '=== Testing classic index format...'
$COMPOSE build testserver "$SERVICE_NAME"
export BLACKLAB_FEATURE_defaultIndexType=external
$COMPOSE up -d --force-recreate testserver # (--force-recreate to avoid error 'network not found')
$COMPOSE run --rm "$SERVICE_NAME"
$COMPOSE stop testserver # (stop then rm -v instead of down -v, otherwise we get an error about the volume being in use)
$COMPOSE rm -fv testserver

#----------------------------------------------------------
# Re-run to test the other index format as well
echo '=== Testing integrated index format...'
export BLACKLAB_FEATURE_defaultIndexType=integrated
export INDEX_TYPE=integrated
$COMPOSE up -d testserver
$COMPOSE run --rm "$SERVICE_NAME"
$COMPOSE stop testserver # (stop then rm -v instead of down -v, otherwise we get an error about the volume being in use)
$COMPOSE rm -fv testserver

##----------------------------------------------------------
## Re-run the same tests using Solr+proxy
#echo '=== Testing Solr (with integrated index format)...'
#$COMPOSE build proxy solr "$SERVICE_NAME"
#$COMPOSE down -v  # delete previous index so it updates if it was changed in the repo
#$COMPOSE up --force-recreate -d proxy solr
#export APP_URL=http://proxy:8080/blacklab-server
#export CORPUS_NAME=test
#export SKIP_INDEXING_TESTS=true   # not yet implemented for Solr
#sleep 15 # allow Solr a little time to start up
#$COMPOSE run --rm "$SERVICE_NAME"
#$COMPOSE stop # (stop then rm -v instead of down -v, otherwise we get an error about the volume being in use)
#$COMPOSE rm -fv
