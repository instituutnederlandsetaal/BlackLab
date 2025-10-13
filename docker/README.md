# Docker image (experimental)

This is an experimental Docker image for BlackLab Server.

A Docker version supporting [BuildKit](https://docs.docker.com/develop/develop-images/build_enhancements/) is required (18.09 or higher). Make sure BuildKit is enabled, e.g. by setting the `DOCKER_BUILDKIT` environment variable to `1`.

The `make` utility is required if you want to use the included `Makefile` for convenience.

To build the Docker images, from the repository root, run:

```bash
# Using the included Makefile:
make build

# or, using Docker Compose directly:
docker compose build
```

This image has a default configuration file in `/etc/blacklab/blacklab-server.yaml`; if necessary, you can overwrite this with your own version (using the `$CONFIG_ROOT` environment variable, or by deriving your own image, or some other way).

## Making indexes available

The default configuration will look for indexes in `/data/`.

### OPTION 1: Index files inside the container

This is the easiest way to use this image (especially using Docker Compose; see below), and probably preferable to using bind mounts (although those can have their uses as well).

It is strongly recommended to use a named volume for `/data/`, where your indexes should be stored to be read by BlackLab Server. Otherwise, an anonymous volume will be created, making it difficult to keep track of your data and increasing the risk of accidental deletion.

To index files in the container, bind mount the input files and run IndexTool:

```bash
# Run the indexing command in the container,
# Start the container with bind mounts for your input files and format config file,
# and a named volume /data where your indexes will be stored.
docker run --rm \
    --name blacklab-indexer \
    --mount 'type=bind,src=/path/to/my/input-files,dst=/input' \
    --mount 'type=bind,src=/path/to/my/formats,dst=/etc/blacklab/formats' \
    --mount 'type=volume,src=blacklab-data,dst=/data' \
    instituutnederlandsetaal/blacklab \
    /bin/bash -c "cd /usr/local/lib/blacklab-tools && \
    java -cp '*' nl.inl.blacklab.tools.IndexTool create /data/index/my-index /input/my-input-files/ my-file-format"

# Run a BlackLab server container with port 8080 mapped and the named volume with your index
docker run -d -p 8080:8080 --name blacklab --mount 'type=volume,src=blacklab-data,dst=/data' instituutnederlandsetaal/blacklab
```

Of course, it makes sense to capture this in a Docker Compose file. The included Compose file does just that (Compose version 1.27.1 or higher required). It can be configured using environment variables. The easiest is to create an environment variable file and pass that to Compose.

Create a file named `test.env` with your indexing configuration:

```ini
# What version of the blacklab image to use
IMAGE_VERSION=dev

# Where your .blf.yaml (input format configuration) files live.
BLACKLAB_FORMATS_DIR=/path/to/my/formats

# Name for your index
INDEX_NAME=my-index

# Input file format (refers to the corresponding .blf.yaml file)
INDEX_FORMAT=my-file-format

# Where your input files are located
INDEX_INPUT_DIR=/path/to/my/input-files

# Ensure JVM has enough heap memory
JAVA_OPTS=-Xmx10G
```

Pass this to Docker Compose to index your data:

```bash
docker compose --env-file test.env run --rm indexer
```

Your data will be indexed just like with the above command. Now start the server:

```bash
docker compose up -d
```

Your index should now be accessible at `http://localhost:8080/blacklab-server/my-index`.

**Please note:** the Compose override file `docker-compose.override.yml` enables
'remote' debugging, allowing you to easily debug BlackLab even while
running in a container. Compose automatically uses this file if you don't
tell it otherwise. To start a server without remote debugging (e.g. in production), use:

```bash
docker compose -f docker-compose.yml up -d 
```

### OPTION 2: Add indexes using a bind mount

If you already have some indexes on the local machine and want to use a bind mount to access them from a BlackLab Server container, use:

```bash
docker run -d -p 8080:8080 --name blacklab --mount 'type=bind,src=/path/to/my/indexes,dst=/data' instituutnederlandsetaal/blacklab
```

(this assumes your indexes are in `/path/to/my/indexes`, so e.g.  `/path/to/my/indexes/my-first-index`; change this to your location)

If you want to go this route, you can take the provided Compose file, remove the `indexer` service and add your bind mount(s) to the `server` service.
