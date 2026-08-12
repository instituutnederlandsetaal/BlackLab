#!/bin/bash

if [ $# -lt 1 ]; then
  echo
  echo 'Query a corpus using BlackLab QueryTool'
  echo '---------------------------------------'
  echo
  echo 'This runs QueryTool inside a Docker container using bind mounts, providing an easy '
  echo 'way to query a corpus.'
  echo
  echo 'Usage:'
  echo
  echo '  ./query-corpus.sh <TARGET_DIR> [BLACKLAB_VERSION]'
  echo
  echo 'Arguments:'
  echo '  - TARGET_DIR         the directory where the index will be created.'
  echo
  echo 'By default, the dev version of BlackLab is used. If you would prefer a specific numbered version,'
  echo 'pass it in environment variable BL_VERSION (e.g. "4.1.1")'
  echo
  echo 'By default, a Java heap size of 6G is used. If you need more, set the environment'
  echo 'variable BL_JAVA_HEAP_MEM to the desired value (e.g. "10G").'
  echo
  echo 'Examples:'
  echo
  echo '  # Relative paths; Default Docker image version'
  echo "  ./query-corpus.sh index"
  echo
  echo '  # Increase memory; absolute paths; Docker image version'
  echo '  BL_VERSION=4.1.1 BL_JAVA_HEAP_MEM=10G ./query-corpus.sh /bl-corpora/mycorpus'
  echo
  exit 1
fi

# BlackLab version to use
BL_VERSION="${BL_VERSION:-dev}"

# Set this environment variable to increase the heap size if needed
BL_JAVA_HEAP_MEM=${BL_JAVA_HEAP_MEM:-6G}

# Absolute paths of our arguments
BL_CORPUS_TARGET_DIR=$(realpath "$1")

# Base names to use inside the container
BL_CORPUS_NAME=$(basename $BL_CORPUS_TARGET_DIR)

# Full paths inside container
BL_CONTAINER_CORPUS_DIR="/data/index/$BL_CORPUS_NAME"

# Ensure target dir exists so we can bind mount
mkdir -p $BL_CORPUS_TARGET_DIR

# Set Java options for the container:
# - set the maximum heap size
# - enable native access (Lucene mmap support)
# - add incubator vector module (performance)
# - route JUL (used by Lucene) through Log4j2
JAVA_OPTS="\
    -Xmx$BL_JAVA_HEAP_MEM \
    --enable-native-access=ALL-UNNAMED \
    --add-modules jdk.incubator.vector"

# Make sure we have the latest version of the image
docker pull instituutnederlandsetaal/blacklab:$BL_VERSION

# Run the QueryTool
docker run -it --rm \
    --name blacklab-querytool \
    --mount type=bind,src="$BL_CORPUS_TARGET_DIR",dst="$BL_CONTAINER_CORPUS_DIR" \
    instituutnederlandsetaal/blacklab:$BL_VERSION \
    /bin/bash -c "\
      cd /usr/local/lib/blacklab-tools && \
      java $JAVA_OPTS -cp '*' nl.inl.blacklab.tools.QueryTool \
        $BL_CONTAINER_CORPUS_DIR"

exit 0
