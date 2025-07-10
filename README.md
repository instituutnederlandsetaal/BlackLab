# What is BlackLab?

[BlackLab](https://blacklab.ivdnt.org/) is a corpus retrieval engine built on top of [Apache Lucene](http://lucene.apache.org/). It allows fast, complex searches with accurate hit highlighting on large, tagged and annotated, bodies of text. It was developed at the [Dutch Language Institute (INT)](https://ivdnt.org/) to provide a fast and feature-rich search interface on our contemporary and historical text corpora.

In addition to the Java library (BlackLab Core), there is also a web service (BlackLab Server), so you can access it from any programming language.

BlackLab is licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

To learn how to index and search your data, see the [official project site](https://blacklab.ivdnt.org/guide/getting-started.html).

To learn about BlackLab development, see the [dev docs](doc/#readme). 

If you wish to cite BlackLab, see [Citing BlackLab](https://blacklab.ivdnt.org/guide/about.html#citing-blacklab). Thank you!

## Branches

The default branch, **dev**, corresponds to the "bleeding edge" in-development version. You can certainly run it (we do), but if you need maximum stability, it might be better to stay on a stable release instead. 

The branch that corresponds to BlackLab's latest release is called **main**.

There are additional branches related to in-development features. These are intended to be short-lived and will be merged into dev.


## Compatibility: Java, Lucene

The current version of BlackLab requires Java 17 or higher. It has been tested up to and including Java 21.

This version uses Lucene 8. This unfortunately means that corpora created with older BlackLab versions (up to 2.3) cannot be read and will need to be re-indexed.


## Roadmap

There is a high-level [roadmap](https://blacklab.ivdnt.org/roadmap.html) page on the documentation site. There are also [BlackLab Archives of Relevant Knowledge (BARKs)](doc/bark/#readme) that go into more detail.

For the next major version (4.0), we are focused on integrating BlackLab with Solr, with the goal of enabling distributed search. We will use this to scale our largest corpus to many billions of tokens. Status and plans for this can be found in the above-mentioned BARKs and in more technical detail [here](doc/technical/design/plan-distributed.md).


## Development workflow

We strive towards practicing Continuous Delivery.

Our intention is to:
- continuously improve both unit and integration tests (during development and whenever bugs are discovered)
- avoid long-lived feature branches but frequently merge to the dev branch
- create meaningful commits that fix a bug or add (part of) a feature
- use temporary feature flags to prevent issues with unfinished code
- deploy to our servers frequently


## Code style

BlackLab mostly uses the standard Java code style, with a few small tweaks.
There is an `.editorconfig` file in the project root that may be picked up by your IDE automatically.
If not, configurations for IDE code formatters can be found in the `doc/` directory.

## Building the site

The [BlackLab end-user documentation site](https://blacklab.ivdnt.org/) can be built locally if you want:

```bash
# Contains the configurations for various checking plugins shared by multiple modules
cd build-tools
mvn install

# Build the actual site (result will be in core/target/site)
cd ..
mvn site
```

## Using BlackLab with Docker

An alpha version of the Docker setup is provided on [Docker Hub](https://hub.docker.com/r/instituutnederlandsetaal/blacklab). For each upcoming release, we will publish a corresponding Docker image.

A Docker version supporting [BuildKit](https://docs.docker.com/develop/develop-images/build_enhancements/) is required (18.09 or higher), as well as Docker Compose version 1.27.1 or higher. 

See the [Docker README](docker/#readme) for more details.

### Indexing with Docker 

We assume here that you are familiar with the BlackLab indexing process; see [indexing with BlackLab](https://blacklab.ivdnt.org/indexing-with-blacklab.html) to learn more.

The easiest is to use the [`index-corpus.sh`](./index-corpus.sh) Bash script in the root of the repository. It will download Docker image and run IndexTool in a container, using bind mounts for the input data and writing the indexed corpus. Run the script without arguments for documentation.

Alternatively, you can use Docker Compose to run the indexer. This will create your index on a named volume defined by the Compose file.

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

Your index should now be accessible at http://localhost:8080/blacklab-server/my-index.

If you want to be able to use the corpus frontend as well, create a file named `.env` in the root of the repository with the following contents:

```ini
DOCKER_IMAGE=blacklab-frontend
```

Then run:

```bash
docker compose up -d --no-build
```

## Special thanks

* ej-technologies for the [JProfiler Java profiler](https://www.ej-technologies.com/products/jprofiler/overview.html)
* Matthijs Brouwer, developer of [Mtas](https://github.com/meertensinstituut/mtas/), which we used for reference while developing the custom Lucene Codec and integrating with Solr.
* Everyone who contributed to the project. BlackLab wouldn't be where it is today without all of you.
