#!/bin/bash

if [ $# -lt 3 ]; then
  echo
  echo 'Index a corpus using BlackLab IndexTool'
  echo '---------------------------------------'
  echo
  echo 'This runs IndexTool inside a Docker container using bind mounts, providing an easy '
  echo 'way to index a corpus.'
  echo
  echo 'Usage:'
  echo
  echo '  ./index-corpus.sh <TARGET_DIR> <INPUT> <FORMAT> [BLACKLAB_VERSION] [INDEXTOOL_OPTIONS]'
  echo
  echo 'Arguments:'
  echo '  - TARGET_DIR         the directory where the index will be created.'
  echo '  - INPUT              the directory or single file to index (globs not supported).'
  echo '                       Note that symlinks will generally not work inside the container.'
  echo '  - FORMAT             the format to use, either a builtin format (e.g. tei-p5)'
  echo '                       or a path to a format file (.blf.yaml).'
  echo '  - INDEXTOOL_OPTIONS  (optional) options to pass to IndexTool.'
  echo
  echo 'By default, the dev version of BlackLab is used. If you would prefer a specific numbered version,'
  echo 'pass it in environment variable BL_VERSION (e.g. "4.1.1")'
  echo
  echo 'By default, a Java heap size of 6G is used. If you need more, set the environment'
  echo 'variable BL_JAVA_HEAP_MEM to the desired value (e.g. "10G").'
  echo
  echo 'Examples:'
  echo
  echo '  # Relative paths; format installed in BL formats dir; Default Docker image version and IndexTool options'
  echo "  ./index-corpus.sh index input tei-p5"
  echo
  echo '  # Increase memory; absolute paths; format config file; Docker image version; IndexTool options'
  echo '  BL_VERSION=4.1.1 BL_JAVA_HEAP_MEM=10G ./index-corpus.sh /bl-corpora/mycorpus /input-data/mycorpus'
  echo "    /blacklab-formats/format.blf.yaml '--threads 2 --index-type external'"
  echo
  exit 1
fi

# BlackLab version to use
BL_VERSION="${BL_VERSION:-dev}"

# Set this environment variable to increase the heap size if needed
BL_JAVA_HEAP_MEM=${BL_JAVA_HEAP_MEM:-6G}

# Ensure target dir exists so we can find realpath and bind mount later
mkdir -p $1

# Absolute paths of our arguments
BL_CORPUS_TARGET_DIR=$(realpath "$1")
BL_CORPUS_INPUT_DIR=$(realpath $2)
BL_CORPUS_FORMAT="$3"
BL_CORPUS_FORMAT_FILE=$(realpath "$BL_CORPUS_FORMAT")
BL_INDEXTOOL_OPTIONS="$4"

# Base names to use inside the container
BL_CORPUS_NAME=$(basename $BL_CORPUS_TARGET_DIR)
BL_CORPUS_INPUT_DIR_NAME=$(basename $BL_CORPUS_INPUT_DIR)     # (so fromInputFile makes more sense)

# Full paths inside container
BL_CONTAINER_CORPUS_DIR="/data/index/$BL_CORPUS_NAME"
BL_CONTAINER_INPUT_DIR="/input/$BL_CORPUS_INPUT_DIR_NAME"

# Determine the right permissions to use inside the container,
# so our user owns the resulting files.
BL_CONTAINER_USER_GROUP="$(id -u):$(id -g)"

# See if we need to bind the format file (if it doesn't seem to exist, it's a builtin format)
BIND_FORMAT=
BL_CONTAINER_FORMAT="$BL_CORPUS_FORMAT"
if [ -f "$BL_CORPUS_FORMAT_FILE" ]; then
  BL_CORPUS_FORMAT_FILE_NAME=$(basename $BL_CORPUS_FORMAT_FILE)
  BL_CONTAINER_FORMAT="/tmp/blacklab-formats/$BL_CORPUS_FORMAT_FILE_NAME"
  BIND_FORMAT="--mount type=bind,src=$BL_CORPUS_FORMAT_FILE,dst=$BL_CONTAINER_FORMAT "
fi

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

# Run the indexer
docker run --user $BL_CONTAINER_USER_GROUP --rm \
    --name blacklab-indexer \
    --mount type=bind,src="$BL_CORPUS_TARGET_DIR",dst="$BL_CONTAINER_CORPUS_DIR" \
    --mount type=bind,src="$BL_CORPUS_INPUT_DIR",dst="$BL_CONTAINER_INPUT_DIR" \
    $BIND_FORMAT\
    instituutnederlandsetaal/blacklab:$BL_VERSION \
    /bin/bash -c "\
      cd /usr/local/lib/blacklab-tools && \
      java $JAVA_OPTS -cp '*' nl.inl.blacklab.tools.IndexTool \
        $BL_INDEXTOOL_OPTIONS create \
        $BL_CONTAINER_CORPUS_DIR \
        $BL_CONTAINER_INPUT_DIR \
        $BL_CONTAINER_FORMAT"

exit 0
