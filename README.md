# What is BlackLab?

[BlackLab](https://blacklab.ivdnt.org/) is a corpus search engine built on top of [Apache Lucene](http://lucene.apache.org/). It allows fast, complex searches on large, tagged and annotated, bodies of text. It supports both token-based and (dependency) relations querying. It was developed at the [Dutch Language Institute (INT)](https://ivdnt.org/) to provide a fast and feature-rich search interface on our contemporary and historical text corpora.

The main way to use BlackLab is through the BlackLab Server API, but there is also a Java library if you want to use that directly.

BlackLab is licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

To learn how to index and search your data, see the [official project site](https://blacklab.ivdnt.org/guide/getting-started.html).

To learn about BlackLab development, see the [dev docs](doc/#readme). 

If you wish to cite BlackLab, see [Citing BlackLab](https://blacklab.ivdnt.org/guide/about.html#citing-blacklab). Thank you!

## Branches

The default branch, **dev**, corresponds to the "bleeding edge" in-development version. It can offer new features and better performance, but could be less stable or polished.

There is also a [**maintenance**](https://github.com/instituutnederlandsetaal/BlackLab/tree/maintenance) branch where we backport bugfixes to the latest release.

There are additional branches related to in-development features. These are intended to be short-lived and will be merged into dev.


## Compatibility: Java, Lucene

The current version of BlackLab requires Java 17 or higher. It has been tested up to and including Java 25.

This version uses Lucene 9. This unfortunately means that corpora created with older BlackLab versions (up to 2.3) cannot be read and will need to be re-indexed.


## Roadmap

There is a high-level [roadmap](https://blacklab.ivdnt.org/roadmap.html) page on the documentation site. There are also [BlackLab Archives of Relevant Knowledge (BARKs)](doc/bark/#readme) that go into more detail.

For the next major version (4.0), we are focused on integrating BlackLab with Solr, with the goal of enabling distributed search. We will use this to scale our largest corpus to many billions of tokens. Status and plans for this can be found in the above-mentioned BARKs and in more technical detail [here](doc/technical/design/plan-distributed.md).


## Development workflow

We strive towards practicing Continuous Delivery.

Our intention is to:
- continuously improve both unit and integration tests (during development and whenever bugs are discovered)
- avoid long-lived feature branches but frequently merge to the `dev` branch
- create meaningful commits that fix a bug or add (part of) a feature
- use temporary feature flags to prevent issues with unfinished code
- deploy to our servers frequently


## Code style

BlackLab mostly uses the standard Java code style, with a few small tweaks.
There is an `.editorconfig` file in the project root that may be picked up by your IDE automatically (perhaps with a plugin).

## Building the site

The [BlackLab end-user documentation site](https://blacklab.ivdnt.org/) can be built locally if you want:

```bash
# Run the site locally with hot reloading
cd site
npm install
npm run start

# Build the final site (result will be in .vitepress/dist)
npm run docs:build
```

## Using BlackLab with Docker

Docker images for BlackLab are provided on [Docker Hub](https://hub.docker.com/r/instituutnederlandsetaal/blacklab). There are release tags (e.g. `v4.1.1`) and a `dev` tag that is always up-to-date with the `dev` branch. Use a numbered release for stability and reliabily, or `dev` for the latest features and best performance.

See https://blacklab.ivdnt.org/guide/getting-started.html to start using BlackLab with Docker.


## Special thanks

* ej-technologies for the [JProfiler Java profiler](https://www.ej-technologies.com/products/jprofiler/overview.html)
* Matthijs Brouwer, developer of [Mtas](https://github.com/meertensinstituut/mtas/), which we used for reference while developing the custom Lucene Codec and integrating with Solr.
* Everyone who contributed to the project. BlackLab wouldn't be where it is today without all of you.
