# Blacklab webservice API evolution

::: warning OLDER CONTENT
This page contains ideas that are partially obsolete.
See [API versions](../../server/rest-api/api-versions.md) for the current state of the API.
:::

The BLS API has quite a few quirks that can make it confusing and annoying to work with.

We intend to evolve the API over time, with new versions that gradually move away from the bad parts of the old API. This can be done using the `api` parameter to switch between versions, or by adding endpoints or response keys, while supporting the old ones for a allow time to transition.

For a comparison between the different API versions currently available, see [API versions](../../server/rest-api/api-versions.md).

For some older ideas for example requests and responses, see [here](API.md).


## API evolution TODO

General guidelines:
- Publish a clear and complete migration guide
- Publish complete reference documentation
- Use `corpus`/`corpora` in favor of `index`/`indices`.
- Be consistent: if information is given in multiple places, e.g. on the server info page as well as on the corpus info page, use the same structure and element names (except one page may give additional details).
- Return helpful error messages.<br>
  (if an illegal value is passed, explain or list legal values, and/or refer to online docs)
- JSON should probably be our primary output format<br>
  (the XML structure should just be a dumb translation from JSON, for those who need it, e.g. to pass through XSLT). So e.g. no difference in concordance structure between JSON and XML)
- Avoid custom encodings (e.g. strings with specific separator characters, such as used for HitProperty and related values); prefer a standard encoding such as JSON.

Already fixed in v4/5:
- Ensure correct data types, e.g. `fieldValues` should have integer values, but are strings
- Fix `blacklabBuildTime` vs. `blackLabBuildTime`
- Added `before`/`after` in addition to `left`/`right` for parameters (response structure unchanged)
- Don't include static info on dynamic (results) pages.<br>
  (e.g. don't send display names for all metadata fields with each hits results;
  the client can request those once if needed)
- Avoid attributes; use elements for everything.
- Avoid dynamic XML element names<br>(e.g. don't use map keys for XML element names.
  Not an issue if we copy JSON structure)
- add `/corpora/*` endpoints. Avoid ambiguity with e.g. `/blacklab-server/input-formats`, and also provide a place to update the API in parallel. That is, these new endpoints will not be 100% compatible but use a newer, cleaner version.

TODO v4:
- Make functionality more orthogonal. E.g. `subcorpusSize` can be included in grouped responses, but not in ungrouped ones.
- Add a way to pass HitProperty as JSON in addition to custom encoding

DONE IN /corpora ENDPOINTS (e.g. v5):
- Replace `left`/`right` in response with `before`/`after`<br>
  (makes more sense for RTL languages)
- XML: same concordance structure as in JSON
- Handle custom information better. <br>
  Custom information, ignored by Blacklab but useful for e.g. the frontend,
  like displayName, uiType, etc. is polluting the response structure.
  We should isolate it (e.g. in a `custom` section for each field, annotation, etc.),
  just pass it along unchecked, and include it only if requested.<br>
  This includes the so-called "special fields" except for `pidField` (so author, title, date).
  (Blacklab uses the `pidField` to refer to documents)
- Change confusing names.<br>
  (e.g. the name `stoppedRetrievingHits` prompts the question "why did you stop?".
  `limitReached` might be easier to understand, especially if it's directly
  related to a configuration setting `hitLimit`)
- Group related values.<br>
  (e.g. numberOfHitsRetrieved / numberOfDocsRetrieved / stoppedRetrievingHits
  would be better as a structure `"retrieved": { "hits": 100, "docs": 10, "reachedHitLimit": true }` ).
- Separate unrelated parts.<br>
  (e.g. in DocInfo, arbitrary document metadata values such as `title` or `author` should probably be in a separate subobject, not alongside special values like `lengthInTokens` and `mayView`. Also, `metadataFieldGroups` shouldn't be alongside DocInfo structures.)

DONE API v5:
- remove `/blacklab-server/CORPUSNAME` endpoints.
- XML: When using `usecontent=orig`, don't make the content part of the XML anymore.<br>
  (escape it using CDATA (again, same as in JSON). Also consider just returning both
  the FI concordances as well as the original content (if requested), so the response
  structure doesn't fundamentally change because of one parameter value)
  (optionally have a parameter to include it as part of the XML if desired, to simplify response handling?)
- Return HitPropertyValues as JSON instead of current custom encoding?


TODO v5:
- remove old custom encodings for HitProperty in favour of the JSON format?

Possible new endpoints/features:
- If you're interested in stats like total number of results, subcorpus size, etc., it's kind of confusing to have to do `/hits?number=0&waitfortotal=true`; maybe have separate endpoints for this kind of application? (calculating stats vs. paging through hits)


This might be harder to do without breaking compatibility:
- Try to use consistent terminology between parameters, response and configuration files.<br>
 (e.g. use the term `hitLimit` everywhere for the same concept)

Maybe?
- Support Solr's common query parameters, e.g. `start`,`rows`,`fq`, etc.
  as the preferred version.<br>
  Support the `lowerCamelCase` version of query parameter names for consistency 
  with responses and configuration options.<br>
  Support the old query parameter names (but issue deprecation warning when first 
  encountered?)
- Don't send `mayView` for each document (until we implement such granular authorization), include it in corpus info. Although keeping it there doesn't hurt and prepares us for this feature.
- Be stricter about parameter values.<br>
  (if an illegal value is passed, return an error instead of silently using a default value)
- Consider adding a JSON request option in addition to regular query parameters.
  There should be an easy-to-use test interface so there's no need to
  manually type URL-encoded JSON requests into the browser address bar.
