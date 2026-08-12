--- 
title: API by example
---
# BlackLab Server API by example

Let's look at some examples of how you use the API.

This assumes you have a BlackLab Server instance running and have a corpus available. See [Getting started](/guide/getting-started.html) if you don't have that yet.

**NOTE:** for readability, we won't URL-encode parameter values on this page.

## API 4 vs. 5

This page shows the newer API v5 URLs (that include `/corpora/`), which became available in BlackLab 4 and will be the default in BlackLab 5 (and current `dev` versions). If you get an error about the API version, try adding `api=5` to the URL.

## JSON vs. XML

Browsers show the XML response by default. Add `outputformat=json` to the URLs below to get the JSON response. (The `Accept` header also works, of course). For more details, see [here](api-info).

## Information about the server and corpora

The simplest request is the "server info" request: `/blacklab-server/`. The response will include:

- BlackLab version information 
- available corpora
- information about the [logged-in user](/server/user-corpora), if any
- available [plugins](/development/customization/)

You can get information about the `test` corpus: `/blacklab-server/corpora/test`. This includes:

- time of creation/modification, BlackLab version used
- corpus size in documents and tokens
- annotated fields and metadata fields

If you add `listvalues=pos`, it will include all values for the `pos` annotation (up to a maximum of `limitvalues`, default 200). There's also `listmetadatavalues` for metadata fields. More information [here](information/corpus-info).

## Find matches

Finding matches by searching for [BCQL query](/guide/query-language/) patterns is done with the `/blacklab-server/corpora/test/hits` endpoint.

Below will leave out the first part and just write `/hits?...`

To find all verbs and sort by document author, first 100 matches: `/hits?patt=[pos="VERB"]&sort=field:author&first=0&number=100`. The response will include:

- the `hits` found
- information about the documents these hits occur in (`docInfos`)
- a `summary` of what was searched and how many results were found

Much more is possible with `/hits`; see [here](search/find-hits).

You can group these matches by various properties, such as the word before each hit: `/hits?patt=[pos="VERB"]&group=before:word:i:1`. See all sort and group criteria [here](search/find-hits#criteria-for-sorting-grouping-and-faceting).

::: details Running results count

When you're requesting a page of results (say the first 20), and there are more results to the query, BlackLab Server will keep processing these results in the background. When it returns the requested page of hits, it will also report how many it has processing so far and whether it has finished or is still processing/counting. This information is in the `summary.results.stats` section.

If you need the total number of results right away, you can also pass `waitfortotal=true` and BlackLab will not respond until it has found all results.

:::

## Find documents

Finding documents that match some filter can be done with the `/blacklab-server/corpora/test/docs` endpoint. We will again just write `/docs?...` below.

To find documents with _guide_ in the title, sorted by author and date, results 60-89: `/docs?filter=title:guide&sort=field:author,field:date&first=60&number=30`

Documents matching a BCQL query, grouped by author: `/docs?patt=[lemma="bank" & pos="VERB"]&group=field:author`

More [here](search/find-documents).

## Information about a document

Each document has a PID (persistent identifier). (actually, it is only persistent if your `.blf.yaml` defines a `pidField`; see [here](/guide/index-your-data/metadata#corpus-metadata))

This identifier can be used to retrieve information about the document. If we're interested in a document with PID 0345391802, here's what we can find:

- `/docs/0345391802`: document metadata
- `/docs/0345391802/contents`: original (XML) document contents
- `/docs/0345391802/contents?patt=[lemma="bank" & pos="VERB"]`: document contents with matches highlighted (using `<hl>..</hl>`)
- `/docs/0345391802/contents?wordstart=1000&wordend=2000`: part of the original document, so you can page through large documents
- `/docs/0345391802/snippet?hitstart=120&hitend=121&context=50`: annotation values in a snippet of the document (similar to the `/hits` response, not the original document contents)

More information [here](./documents/).

## This concludes our tour

Those are the most important endpoints in the BlackLab Server API.
Now you can dig into the various endpoints using the [reference](index).
