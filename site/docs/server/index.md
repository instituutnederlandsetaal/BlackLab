---
order: -1
---

# BlackLab Server

BlackLab Server is the REST API (web service) for accessing BlackLab corpora. For most users, it is the best way to use BlackLab. It can be used from any programming language, and for anything from quick analysis scripts to full-featured corpus search applications (such as [BlackLab Frontend](https://blacklab-frontend.ivdnt.org/)).

See [Getting started](/guide/getting-started) for the basics. Below, we'll assume you have BlackLab Server up and running.

What you might want to look at next:

- Learn the [API by example](rest-api/by-example)
- Consult the [API reference](/server/rest-api/)
- Use BlackLab from [different programming languages](from-different-languages)
- Configure BlackLab through [blacklab-server.yaml](configuration).

Below we'll look at a few specific subjects.

## Configuration directory

BlackLab's configuration directory contains its `blacklab-server.yaml` as well as other files (mentioned below).

For Docker users, this directory is always `/etc/blacklab`. If you don't use Docker, and want to use a different configuration directory, here's where BlackLab will look:

- the directory specified in `$BLACKLAB_CONFIG_DIR`
- `$HOME/.blacklab/` (if you're running Tomcat under your own user account, e.g. on a development machine; `$HOME` refers to your home directory)

::: details Passing `$BLACKLAB_CONFIG_DIR` to Tomcat

To pass `$BLACKLAB_CONFIG_DIR` to Tomcat, create or edit `setenv.sh` in the Tomcat `bin` directory to set environment variables. You can also set them in `/etc/sysconfig/tomcat` if using systemd. Check the Tomcat documentation for details

:::

There can be various subdirectories in the configuration directory:
- `formats/` (`.blf.yaml` input format configuration files)
- `plugins/` ([plugins](/development/customization/) with their configuration and any files they might need) should be if they exist

[BlackLab Frontend](https://blacklab-frontend.ivdnt.org/) will look for its main configuration file and per-corpus configuration files (`projectconfigs`) here.


## Memory usage

If your memory settings are suboptimal, performance may suffer.

This is assuming your machine has enough memory for what you're trying to do, of course. As a rough indication: we run a 4.5 billion token corpus on a (virtual) machine with 50 GB of memory, with few simultaneous users that generally perform simple queries, with the occasional heavier query.

### Heap memory vs. disk cache

For larger corpora, it is important to give Tomcat's JVM enough (heap) memory. (if memory is low and/or fragmented, the JVM garbage collector might start taking 100% CPU moving objects in order to recover enough free space, slowing things down to a crawl)

On the other hand, do not assign all of the system's memory to JVM's heap, either. You should leave a significant amount for the operating system's disk cache, which can greatly speed up certain operations.

The optimum way to divide up memory depends on many factors, but a good starting point is to assign no more than 50% of the system memory to the JVM. You can then experiment with increasing or decreasing the heap size to see what works best in your case.

**NOTE:** If you are indexing unique ids for each word, you may also be able to save memory by [disabling the forward index](/guide/index-your-data/annotations.md#disable-the-forward-index) for that 'unique id' annotation.

### How do I configure heap memory?

For Docker users, this is done by setting the `JAVA_OPTS` environment variable, e.g. in your `docker-compose.yml`:

```yaml
  blacklab:
    image: instituutnederlandsetaal/blacklab:dev
    environment:
      # Set the JVM's maximum heap size to 10 GB
      - "JAVA_OPTS=-Xmx10G"
    # (... volumes, etc.)
```

For non-Docker usage, it's similar; for example, see [here](http://crunchify.com/how-to-change-jvm-heap-setting-xms-xmx-of-tomcat/).

## Docker images

Docker images are available on [Docker Hub](https://hub.docker.com/orgs/instituutnederlandsetaal/repositories?search=blacklab).

These are the two blacklab Docker images available:
- `blacklab` is the base image, with BlackLab running inside Tomcat
- `blacklab-frontend` adds BlackLab Frontend as well

The are numbered release tags such as `4.1.1`, `4.1` and `4`. For the most stable experience, use a numbered release, especially a specific patch version. (`4` is always the latest minor/patch, `4.1` the latest patch)

There is also a `dev` tag that is always up to date with the `dev` branch. This provides more features and often better performance, but obviously less stability. We do aim to always keep the `dev` version in a releasable state, though.

If you want to build your own image from source, use a recent Docker version (at least version 23).

### Paths in the images

The images use the following paths:

- `/etc/blacklab`: BlackLab's configuration directory. This is where main configuration file `blacklab-server.yaml` goes, plus the `formats` and `plugins` directories.
- `/data/index`: where BlackLab looks for indexed corpora.
- `/data/user-index`: where private user corpora are stored (only available if authentication is enabled)

To extend the image's built-in `blacklab-server.yaml` file, bind mount a file at `/etc/blacklab/blacklab-server.override.yaml` (dev/future v5) and it will be read after `blacklab-server.yaml`. So in `docker-compose.yml`:

```yaml
    volumes:
      # BlackLab will look for corpora here 
      - /data/blacklab-corpora:/data/index
      # Some configuration overrides
      - ./my-bls-settings.yaml:/etc/blacklab/blacklab-server.override.yaml
```

### Build your own image

To build the Docker image yourself, run this from the `docker` subdirectory:

```bash
docker compose build
```
