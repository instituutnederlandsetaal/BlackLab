#!/bin/bash

# This script is intended for use with the integration tests.
# It will remove previous data, index the test data and start Tomcat.

rm -rf /data/index
rm -rf /data/user-index

mkdir /data/index
mkdir /data/user-index

cd /usr/local/lib/blacklab-tools || exit
# NOTE: we intentionally add the documents in this order, so hits are not automatically sorted by document pid.
#       this way we actually test the sort operation.
java -cp '*' nl.inl.blacklab.tools.IndexTool create /data/index/test '/test-data/input/PBsve435.xml' voice-tei
java -cp '*' nl.inl.blacklab.tools.IndexTool add    /data/index/test '/test-data/input/PBsve430.xml' voice-tei
java -cp '*' nl.inl.blacklab.tools.IndexTool add    /data/index/test '/test-data/input/PRint602.xml' voice-tei
#cd /usr/local/tomcat && catalina.sh jpda run
cd /usr/local/tomcat && catalina.sh run
