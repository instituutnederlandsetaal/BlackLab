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
  echo '  - BLACKLAB_VERSION   (optional) the BlackLab Docker image to use. Defaults to "dev",'
  echo '                       but it is recommended to use a specific tag, e.g. "4-alpha1".'
  echo '  - INDEXTOOL_OPTIONS  (optional) options to pass to IndexTool.'
  echo '                       Defaults to "--threads 4".'
  echo
  echo 'By default, a Java heap size of 6G is used. If you need more, set the environment'
  echo 'variable BL_JAVA_HEAP_MEM to the desired value (e.g. "10G").'
  echo
  echo 'Examples:'
  echo
  echo '  # Relative paths; Builtin format; Default Docker image version and IndexTool options'
  echo "  ./index-corpus.sh index input tei-p5"
  echo
  echo '  # Increase memory; absolute paths; format config file; Docker image version; IndexTool options'
  echo '  BL_JAVA_HEAP_MEM=10G ./index-corpus.sh /bl-corpora/mycorpus /input-data/mycorpus'
  echo "    /blacklab-formats/format.blf.yaml 4-alpha1 '--threads 2 --index-type external'"
  echo
  exit 1
fi

# Set this environment variable to increase the heap size if needed
BL_JAVA_HEAP_MEM=${BL_JAVA_HEAP_MEM:-6G}

# Absolute paths of our arguments
BL_CORPUS_TARGET_DIR=$(realpath "$1")
BL_CORPUS_INPUT_DIR=$(realpath $2)
BL_CORPUS_FORMAT="$3"
BL_CORPUS_FORMAT_FILE=$(realpath "$BL_CORPUS_FORMAT")
BL_VERSION="${4:-dev}"
BL_INDEXTOOL_OPTIONS="${5:---threads 4}"

# Base names to use inside the container
BL_CORPUS_NAME=$(basename $BL_CORPUS_TARGET_DIR)
BL_CORPUS_INPUT_DIR_NAME=$(basename $BL_CORPUS_INPUT_DIR)     # (so fromInputFile makes more sense)

# Full paths inside container
BL_CONTAINER_CORPUS_DIR="/data/index/$BL_CORPUS_NAME"
BL_CONTAINER_INPUT_DIR="/input/$BL_CORPUS_INPUT_DIR_NAME"

# Ensure target dir exists so we can bind mount
mkdir -p $BL_CORPUS_TARGET_DIR

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

# Run the indexer
docker run --user $BL_CONTAINER_USER_GROUP --rm \
    --name blacklab-indexer \
    --mount type=bind,src="$BL_CORPUS_TARGET_DIR",dst="$BL_CONTAINER_CORPUS_DIR" \
    --mount type=bind,src="$BL_CORPUS_INPUT_DIR",dst="$BL_CONTAINER_INPUT_DIR" \
    $BIND_FORMAT\
    instituutnederlandsetaal/blacklab:$BL_VERSION \
    /bin/bash -c "\
      cd /usr/local/lib/blacklab-tools && \
      java -Xmx$BL_JAVA_HEAP_MEM -cp '*' nl.inl.blacklab.tools.IndexTool $BL_INDEXTOOL_OPTIONS create \
        $BL_CONTAINER_CORPUS_DIR \
        $BL_CONTAINER_INPUT_DIR \
        $BL_CONTAINER_FORMAT"

exit 0
