# Downloads

## Releases

See the [GitHub releases page](https://github.com/instituutnederlandsetaal/BlackLab/releases/) for the complete list. This may also include development versions you can try out. If you're looking for the BlackLab library or commandline tools (i.e. not BlackLab Server), choose the version with libraries included.

Also see the [Change log](changelog.html).

## Versions and compatibility

Here's a list of BlackLab versions with their minimum Java version, the Lucene version they use and 
the corpora they support.

The reason for not all older corpora being usable with a newer BlackLab version is mostly that Lucene drops support for older index formats.

| BlackLab            | 1st release | Java      | Lucene   | Solr     | Supports corpora...     |
|---------------------|:------------|-----------|----------|----------|-------------------------|
| 4.x (future)        | future      | likely 11 | likely 9 | likely 9 | created with BL 3-4     |
| 3.x                 | Jul 2022    | 11        | 8        | -        | created with BL 3       |
| 2.x                 | Jan 2020    | 8         | 5        | -        | created with BL 1.2-2.x |
| 1.7-1.9 (obsolete)  | Jun 2018    | 8         | 5        | -        | created with BL 1.2-2.x |
| 1.0-1.2 (obsolete)  | Apr 2014    | 6         | 3/5      | -        | created with BL 1.x     |

You can stay on 2.x for now to avoid reindexing your corpora, but you'll miss out on performance improvements and new features. We do appreciate any help backporting bugfixes to this version.


## Build your own

To download and build the development version (bleeding edge, may be unstable), clone the repository and build it 
using Maven:

```bash
git clone git://github.com/instituutnederlandsetaal/BlackLab.git
cd BlackLab
# git checkout dev    # (the default branch)
mvn clean package
```

To instead build the most recent release of BlackLab yourself:

```bash
git checkout main
mvn clean package
```
