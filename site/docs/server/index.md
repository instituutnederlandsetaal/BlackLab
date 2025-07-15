---
order: -1
---

# BlackLab Server

## What is it?

BlackLab Server is a web service (REST API) for accessing BlackLab corpora. It is the preferred way of using BlackLab from any programming language. It can be used for anything from quick analysis scripts to full-featured corpus search applications (such as [BlackLab Frontend](https://blacklab-frontend.ivdnt.org/)).

## Basic installation, configuration

### Using Docker

Images are available on [Docker Hub](https://hub.docker.com/r/instituutnederlandsetaal/blacklab). The current image should be considered somewhat experimental: details may change. Suggestions for improving the image (and this guide) are welcome.

Since `v4.0.0`, we provide stable release images. We also provide a `dev` tag, which is always up to date with the `dev` branch.

To build a version from source, a Docker version supporting [BuildKit](https://docs.docker.com/develop/develop-images/build_enhancements/) is required (18.09 or higher), as well as Docker Compose version 1.27.1 or higher.

We assume here that you are familiar with the BlackLab indexing process; see [Indexing with BlackLab](/guide/index-your-data/create-an-index.md) to learn more.

Create a file named `test.env` with your indexing configuration:

```ini
BLACKLAB_FORMATS_DIR=/path/to/my/formats
INDEX_NAME=my-index
INDEX_FORMAT=my-file-format
INDEX_INPUT_DIR=/path/to/my/input-files
JAVA_OPTS=-Xmx10G
```

To index your data:

```bash
docker compose --env-file test.env run --rm indexer
```

Now start the server:

```bash
docker compose up -d
```

Your corpus should now be accessible at http://localhost:8080/blacklab-server/my-index.


See the [Docker README](https://github.com/instituutnederlandsetaal/BlackLab/tree/dev/docker#readme) for more details.

### Manual installation (without Docker)

#### Java JRE

Install a JRE (Java runtime environment). BlackLab requires at least version 17, but version 21 or newer versions should work as well.

#### Tomcat

BlackLab Server needs a Java application server to run. We will use Apache Tomcat.

Install Tomcat 9 on your machine. See the [official docs](https://tomcat.apache.org/tomcat-9.0-doc/setup.html) or an OS-specific guide like [this one for Ubuntu](https://linuxize.com/post/how-to-install-tomcat-9-on-ubuntu-20-04/).

::: warning Tomcat 10 not yet supported
BlackLab currently uses Java EE and therefore runs in Tomcat 8 and 9, but not in Tomcat 10 (which migrated to [Jakarta EE](https://eclipse-foundation.blog/2020/06/23/jakarta-ee-is-taking-off/)). If you try to run BlackLab Server on Tomcat 10, you will get a [ClassNotFoundException](https://stackoverflow.com/questions/66711660/tomcat-10-x-throws-java-lang-noclassdeffounderror-on-javax-servlet-servletreques/66712199#66712199). A future release of BlackLab will migrate to Jakarta EE. There is an experimental branch `jakarta` that you can try if you want.
:::

### Configuration file

Create a configuration file `/etc/blacklab/blacklab-server.yaml`.

::: details <b>TIP:</b> Other locations for the configuration file

If `/etc/blacklab` is not practical for you, you can also place `blacklab-server.yaml` here:

- the directory specified in `$BLACKLAB_CONFIG_DIR`, if Tomcat is started with this environment variable set (create or edit `setenv.sh` in the Tomcat `bin` directory to set environment variables, or e.g. put it in `/etc/sysconfig/tomcat` on a system using systemd)
- somewhere on Tomcat's Java classpath, e.g. in its `lib` directory
- `$HOME/.blacklab/` (if you're running Tomcat under your own user account, e.g. on a development machine; `$HOME` refers to your home directory)  

:::

The minimal configuration file only needs to specify a location for your corpora. Create a directory for your corpora, e.g. `/data/index` and refer to it in your `blacklab-server.yaml` file:

```yaml
---
configVersion: 2

# Where BlackLab can find corpora
indexLocations:
- /data/index
```

Your corpora would be in directories `/data/index/corpus1`, `/data/index/corpus2`, etc.


### BlackLab Server WAR

Download the BlackLab Server WAR (Java web application archive). You can either:
- download the binary attached to the [latest release](https://github.com/instituutnederlandsetaal/BlackLab/releases) (the file should be called `blacklab-server-<VERSION>.war`) or
- clone the [repository](https://github.com/instituutnederlandsetaal/BlackLab) and build it using Maven (`mvn package`; WAR file will be in `server/target/blacklab-server-<VERSION>.war` ).

Place `blacklab-server.war` in Tomcat’s `webapps` directory (`$TOMCAT/webapps/`, where `$TOMCAT` is the directory where Tomcat is installed). Tomcat should automatically discover and deploy it, and you should be able to go to [http://servername:8080/blacklab-server/](http://servername:8080/blacklab-server/ "http://servername:8080/blacklab-server/") and see the BlackLab Server information page, which includes a list of available corpora.

::: details <b>TIP:</b> Unicode URLs
To ensure the correct handling of accented characters in (search) URLs, you should [configure Tomcat](https://tomcat.apache.org/tomcat-9.0-doc/config/http.html#Common_Attributes) to interpret URLs as UTF-8 (by default, it does ISO-8859-1) by adding an attribute `URIEncoding="UTF-8"` to the `<Connector/>` element with the attribute `port="8080"` in Tomcat's `server.xml` file.

Of course, make sure that URLs you send to BlackLab are URL-encoded using UTF-8 (so e.g. searching for `"señor"` corresponds to a request like `http://myserver/blacklab-server/mycorpus/hits?patt=%22se%C3%B1or%22`). [BlackLab Frontend](https://blacklab-frontend.ivdnt.org/) does this by default.
:::

## Memory usage

For larger corpora, it is important to [give Tomcat's JVM enough heap memory](http://crunchify.com/how-to-change-jvm-heap-setting-xms-xmx-of-tomcat/). If heap memory is low and/or fragmented, the JVM garbage collector might start taking 100% CPU moving objects in order to recover enough free space, slowing things down to a crawl.

On the other hand, do not assign all of the system's memory to the JVM either. You should leave a significant amount for the operating system's disk cache, which can greatly speed up certain operations.

The optimum way to divide up memory depends on many factors, but a good starting point is to assign no more than 50% of the system memory to the JVM. You can then experiment with increasing or decreasing the heap size to see what works best in your case.

**NOTE:** If you are indexing unique ids for each word, you may also be able to save memory by [disabling the forward index](/guide/index-your-data/annotations.md#disable-the-forward-index) for that 'unique id' annotation.


## Indexing data

You can index your data using the provided commandline tool IndexTool. See [Indexing with BlackLab](/guide/index-your-data/create-an-index.md).

Another option is to configure user authentication to allow users to create corpora and add their data using BlackLab Server. See [here](/server/user-corpora.html) to get started.

There is currently no way to use BlackLab Server to add data to non-user ("global" or regular) corpora. In the future, this will be available using Solr.

## Searching your corpus

You can try most BlackLab Server requests out by typing URLs into your browser. See [How to use](./overview) and the [API reference](/server/rest-api/) for more information.

We also have a full-featured corpus search application available. See [BlackLab Frontend](https://blacklab-frontend.ivdnt.org/) for more information.


## What's next?

- [Take a guided tour](overview.md)
- [See all the API endpoints](/server/rest-api/)
- [Learn how to use it from your favourite language](from-different-languages.md)
- [Configuration options for BlackLab Server](configuration.md).

