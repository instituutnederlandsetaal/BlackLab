# BARK 2 - Evolution and backward compatibility

- **type:** process

How we evolve BlackLab over time and deal with backward compatibility.

## Policy

### General

We prefer to support older index versions and existing applications making use of the BlackLab Server REST API and BlackLab Java API. Minor versions should not break compatibility for any of these. Major versions sometimes have to make changes and remove older functionality. We try to minimize the impact on users and will usually provide a migration guide.


### Index format versions

We generally support index formats for as long as possible. For example, for version 3.0, we removed support for index formats that were replaced more than 6 years ago.

However, Lucene eventually drops support for older index formats, forcing us to do so as well. See also [BARK 5 - Lucene](bark005-lucene.md).


### BlackLab Server REST API

We do our best to keep the BlackLab Server REST API stable. We may add a parameter, or a key in a response object in a minor version, but will do our best not to remove anything or make other breaking changes until a major version update.

We've introduced a way to deal with minor API changes: the `api` request parameter or `parameters.api` setting. See [API compatibility](https://blacklab.ivdnt.org/server/rest-api/). We will support "old" versions of the API for some time, perhaps a year or so, so users have time to update their software.

If we decide to do a major overhaul of the REST API, we will use this parameter as well to make the transition easier.


### BlackLab Java API

Methods slated for removal will generally be deprecated first, with a recommended alternative, then removed in the next major version. There may occasionally be exceptions to this, e.g. if the interface changes so much that supporting the deprecated method is not practical.

We do generally assume that most users are using the web service, not the Java code, so we are less worried about making Java changes than changing the REST API.


## Impact on users

Users wishing to upgrade to a new major version of BlackLab may be confrontend with changes to the index format or API. They may have to re-index their data, and may have to consult the migration guide to update their applications.

Users can always opt to delay upgrading and stick with the older version, although if they do that for too long, they may have to backport library upgrades, bugfixes and other enhancements they might want. PRs for backports are appreciated.
