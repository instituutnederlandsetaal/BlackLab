# API versions

We're evolving the BlackLab web service API over time, using versioning.

There's currently three major versions:
- `3.0`: legacy API version, used by BlackLab up to 3.x
- `4.0`: newer, mostly compatible API version, used by BlackLab 4.x. Adds some keys and endpoints and makes slight changes, mostly to improve consistency and reduce redundancy. The additions are still experimental, just like v5 of the API.
- `5.0`: STILL EXPERIMENTAL. stricter, cleaner version of the `4.0` API, removing parameters and response keys from `3.0`. Because this is still experimental, we may make breaking changes. Useful for testing.

To know which API version your BLS defaults to, check the server info page (`/`). Look for the key `blacklabResponse.apiVersion`. If this doesn't exist, BLS is using API v3 or lower. If it exists and has the value `4.0` or `5.0`, BLS is using that API version.

On any BLS request, include the `api` parameter to specify which API version to use. So if you want to keep using the older API for now, add `api=3`. If you don't specify an API version, the default version will be used. If you specify an unsupported API version, e.g. `api=2`, you will get an error.

To set the default API version, configure `parameters.api` in your `blacklab-server.yaml` (or `blacklab-webservice.yaml` for Solr).

Valid values for API version include:
- Exact version, e.g. `4.0`
- Major version, e.g. `4` (most recent "regular" release in the 4.x series, i.e. without a `-..` suffix)
- `cur` or `current`, the current stable version (currently `4.0`)
- `exp` or `experimental`, always points to the highest version available (currently `5.0`)

New versions may be added in the future, and support for older versions will eventually be dropped (see the [API support roadmap](#api-support-roadmap) below).

API version numbers roughly match the BlackLab version where they became (or will become) the default API version.

Note that until BlackLab 4.0 is released, any additions to API v4 should be considered experimental. So where it states "you should use this for future compatibility", that really only applies after 4.0 is released.


## API changes from 3.0 to 4.0

API v4.0 is the default API on the development branch and starting from BlackLab 4.0.

Below are all the differences between API version 3.0 and 4.0.

To prepare for API version 5.0 (which will likely be the default in BlackLab 5.0), you should stop using the deprecated features.

### Changed

- Corpus info page (`/CORPUSNAME`):
    - Two keys were renamed to be more consistent: in the `versionInfo` block,
      `blacklabVersion` and `blacklabBuildTime` are now spelled with a lowercase `l`, just like on the server info page. This is unlikely to break any clients.
    - A key was added: `blacklabScmRevision`, giving the short Git commit hash for this version of BlackLab.
- Annotated fields:
    - `displayOrder` will no longer include internal annotations (e.g. `punct` and `_relation`, previously called `starttag`), as these are generally not meant to be displayed as search fields.

### Added

- Server info page:
  - New key added (`/`): `apiVersion` (valid values: `3.0`, `4.0`, `5.0`; assume `3.0` if missing)
  - In addition to `indices`, the new `corpora` key was added that provides the same information in a slightly different format. You should use `corpora` instead of `indices` for future compatibility.
  - In addition to being reported under `fieldInfo`, `pidField` is now also a top-level key. You should use this version of the key for future compatibility. (the other special fields in `fieldInfo` will be moved to `custom` in v5)
- Corpus info page:
  - `mainAnnotatedField` indicates which of the annotated fields is the main search field. This is useful if there's more than one annotated field, e.g. in case of parallel corpora.
- Search (hits) operations:
  - The `patt` parameter may also be specified as a JSON query structure. This will be detected automatically, or you can set `pattlang` to `json` to make it explicit.
  - In the JSON response, `summary` will now include a `pattern` object containing: a `json` key that giving the JSON query structure; a `bcql` key giving the (re-)serialized pattern in BlackLab Corpus Query Language; a `fieldName` key giving the search field, e.g. `"contents"`; and a `matchInfos` key giving the match info groups from the query with their types (span, tag, relation, list). You can use this to convert between the two representations, e.g. for query builders (or use the new `/parse-pattern` endpoint, see below). The XML response does not contain the `json` object.
  - In addition to `captureGroups`, `matchInfos` will be reported that includes the same information as well as any inline tags and relations matched. You should use this instead of `captureGroups` for future compatibility.
  - `before`/`after` are the new, preferred alternatives to `left`/`right`,e.g. when sorting/grouping on context. Not all languages are LTR, so this makes more sense. Existing endpoints still use `left`/`right` in the response for compatibility, but new endpoints have been updated as well. These properties can now get a number of tokens as an extra parameter, e.g. `before:lemma:i:2`.
  - For grouping on context, `wordleft`/`wordright` have been deprecated. Use `before`/`after` with 1 token instead.
  - Grouped results also include a `properties` key that gives the values for the individual properties used for grouping. This is an alternative to `identityDisplay`, which clients would sometimes have to parse to display it in their preferred way.
  - `context` is the new name for the `wordsaroundhit` parameter and supports more options (separate before/after, whole sentence, etc.)
- Pages that list values for fields, tags, etc. now support the `limitvalues` parameter. This parameter defaults to `200`, but can be set higher if you need really long value lists.
- New endpoints were added for all operations on corpora, at `/corpora/CORPUSNAME/...` (for now alongside existing endpoints `/CORPUSNAME`). These endpoints are available in BlackLab v4 but only "speak" API v5 (see below). You should move to these endpoints for future compatibility.
- A new endpoint `/parse-pattern` was added that allows you to parse a CorpusQL or JSON query structure pattern without actually executing the search.
- A new endpoint `.../CORPUSNAME/relations` that will return all the spans ("inline tags") and relations indexed in the corpus.
- Doc info on results pages and document info page: the new `tokenCounts` array gives token counts for all annotated fields. The first annotated field is the main one, which has the same value as `lengthInTokens`.
- When using `usecontent=orig`, you can now specify `escapexmlfragment` to control whether XML fragments are escaped as CDATA or not. This defaults to `false` for API v4 and older, but will be `true` for API v5+.

### Deprecated

These features still work for now, but will be removed in the future.

- Server info page:
  - The `indices` object. Use `corpora` instead.
  - The top-level `fieldInfo` object. Instead, use the top-level `pidField` to find the persistent identifier field. For other special fields, you still have to use `fieldInfo`, but that will move to `custom` in API v5.
- Document info page (`/docs/DOC_PID`) and results pages: `metadataFieldDisplayNames`, `metadataFieldGroups` and `docFields` are deprecated. This information can be found on the corpus info page, so it should be retrieved from there once.
- Doc info on results pages and document info page: `lengthInTokens` is deprecated; use the first object in the `tokenCounts` array instead, which gives the `tokenCount` for the main annotated field.
- Search operations:
  - the `wordsaroundhit` parameter (use `context` instead)
  - sort/group properties `wordleft`,`wordright`. Use `before`/`after` instead with `1` as the number of tokens, e.g. `before:lemma:i:1` instead of `wordleft:lemma:i`.
  - The `captureGroups` response key. Use `matchInfos` instead.
- The old `/CORPUSNAME/...` endpoints. Use the new `/corpora/CORPUSNAME/...` endpoints (that respond with API v5 responses) instead.

## API changes from 4.0 to 5.0

API v5.0 will become the default in BlackLab 5.0. Right now it's experimental and can be used for testing. Use `api=exp` to test that your client works with this API version.

Note that the new endpoints (like `/corpora/CORPUSNAME/...`) in BlackLab 4.0 always "speak" API v5, so those won't change when going from API v4 to v5. So where we say "changed" or "removed" below, we usually mean that compared to the equivalent old endpoints.

### Removed

All these were deprecated in API v4.0, and v5.0 removes them:

- Old endpoints related to corpora have been removed. Use the new `/corpora/...` endpoints introduced in API v4 instead.
- Server info page removed `indices`. Use `corpora` instead.
- Document info page (`/docs/DOC_PID`) and results pages no longer include `metadataFieldDisplayNames`, `metadataFieldGroups` or `docFields`. This information can be found on the corpus info page and need not be sent with each document info request.
- Results pages don't include `captureGroups` anymore. Use `matchInfos` instead.

### Changed

These are breaking changes compared to v4.0. Make sure you update your client accordingly.

- All XML responses:
    - XML responses don't use dynamic values as element names anymore, but instead adopt a `<entry><key>...</key><value>...</value></entry>` structure. This avoids problems with and simplifies maintaining the (less-used) XML format. Mainly affects `summary/searchParams`, `docInfos`, `users[]` in `/sharing` response.
    - Wherever the XML used attributes for map entries, e.g. `<entry key="key">value</entry>`, this was changed to `<entry><key>...</key><value>...</value></entry>` as well.
- server, corpus, field info:
    - `indexName` in responses has been replaced with `corpusName`.
    - Corpora, metadata and annotated field and annotations report certain properties (such as `displayName`, `description`) inside a `custom` block now etc. These are all ignored by BlackLab but may be useful for client applications such as BlackLab Frontend. They are only included in responses if you specify `custom=true`.
- Results pages:
    - `summary` has been restructured to group related values together. Keys have been renamed for clarity.
    - response keys `left`/`right` have been replaced with `before`/`after` in the `/hits` response.
    - `docInfos` now have a `metadata` subobject instead of mixing metadata with `mayView` and `lengthInTokens`.
    - When using `usecontent=orig`, the value of `escapexmlfragment` now defaults to `true`, so XML fragments from the document will be escaped as CDATA. Set it to `false` to include them as part of the XML structure instead (the old default).


## API support roadmap

This is how we intend to evolve BlackLab Server and Frontend with respect to API version support:

- [x] **BlackLab v4.0 alpha/beta**
    - [ ] deprecate API v3 (responses include a "deprecation warning key")
    - [x] add API v4 (adds endpoints, transitional; default API)
    - [x] add API v5 experimental (enforces exclusive use of new endpoints)
- [ ] **Frontend v4.0**: start using API v4: use new endpoints if available, fall back to v3 endpoints if not (for now). So _if_ the BLS you're talking to supports v4 (check server info page if blacklabResponse.apiVersion exists and is >= 4):
    - [ ] use `/relations` to get list of tags
    - [ ] from server info page, use `corpora` key instead of deprecated `indices` key.
    - [ ] use `/corpora/CORPUSNAME/...` endpoints
- [ ] **BlackLab v4.0 release**
    - [ ] API v4/5 finalized
- [ ] **BlackLab v5.0**:
    - [ ] switch default to API v5.
    - [ ] deprecate API v4.
    - [ ] remove deprecated API v3.
- [ ] **Frontend v5.0**: drop support for API v3 and v4 (use new endpoints exclusively). Test this by passing `api=5` to BLS (enforces only new endpoints). Frontend should still work with BlackLab 4.0 at this point (because that already supported API v5; it just wasn't the default).
- [ ] **BlackLab v6.0**:
    - [ ] remove deprecated API v4


