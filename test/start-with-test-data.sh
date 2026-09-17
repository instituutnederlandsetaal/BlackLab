#!/bin/bash

# This script is intended for use with the integration tests.
# It will remove previous data, index the test data and start Tomcat.

rm -rf /data/index
rm -rf /data/user-index

mkdir /data/index
mkdir /data/user-index

cd /usr/local/lib/blacklab-tools || exit

run_index_tool() {
    label="$1"
    shift
    log="/tmp/blacklab-index-${label}.log"
    echo "Indexing ${label}..."
    if ! java -cp '*' nl.inl.blacklab.tools.IndexTool "$@" > "${log}" 2>&1; then
        echo "Indexing ${label} failed. Log follows:"
        cat "${log}"
        exit 1
    fi
}

# NOTE: we intentionally add the documents in this order, so hits are not automatically sorted by document pid.
#       this way we actually test the sort operation.
# It also prevents changes in the responses because document order is not guaranteed to be the same every time.
run_index_tool test-1 create /data/index/test '/test-data/input/PBsve435.xml' voice-tei
run_index_tool test-2 add    /data/index/test '/test-data/input/PBsve430.xml' voice-tei
run_index_tool test-3 add    /data/index/test '/test-data/input/PRint602.xml' voice-tei

# Small parallel corpus.
run_index_tool parallel create /data/index/parallel '/test-data/input/parallel/minimal-parallel.xml' '/test-data/input/parallel/minimal-parallel.blf.yaml'

# Small corpus with fragment metadata.
run_index_tool fragments create /data/index/fragments '/test-data/input/fragments/fragments1.xml' '/test-data/input/fragments/fragments.blf.yaml'
run_index_tool fragments add    /data/index/fragments '/test-data/input/fragments/fragments2.xml' '/test-data/input/fragments/fragments.blf.yaml'

#cd /usr/local/tomcat && catalina.sh jpda run
cd /usr/local/tomcat && catalina.sh run
