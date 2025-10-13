---
title: How to use
---
# Using BlackLab Server

This assumes you have a BlackLab Server instance running and have a corpus available. You can either:

- [index a corpus yourself](/guide/getting-started.html) 
- experiment with one of our [online corpora](/guide/#try-it-online) (use F12 > Network to find the API URL; usually just `/blacklab-server/`).

We'll assume you have a BlackLab Server instance running on your local machine below.

## See what corpora are available

The simplest request is the "server info" request:

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;http://localhost:8080/blacklab-server/

This will return a list of available corpora and some information about the server itself.

::: details JSON, XML or CSV?

The webservice answers in JSON or XML. We generally recommend using JSON, as it is generally easier to work with from various programming languages.

Selecting between them can be done by either:

- passing the HTTP header `Accept` with the value `application/json`, `application/xml` or `text/csv`
- passing an extra parameter `outputformat` with the value `json`, `xml` or `csv`

If both are specified, the `outputformat` parameter is used.

In the browser, you will get XML by default, because the default `Accept` header includes `application/xml`. Just add `?outputformat=json` to the URL to see the JSON response.

In Chrome-based browsers, the debug console's network tab provides a convenient Preview subtab for JSON responses. 

:::

## Overview

::: details Running results count

BlackLab Server is mostly stateless: a particular URL will always result in the same response. An exception to this is the running result count. When you're requesting a page of results (say the first 20), and there are more results to the query, BlackLab Server will keep retrieving these results in the background. When it returns the requested page of hits, it will also report how many it has retrieved so far and whether it has finished or is still retrieving.

A note about "retrieving" versus "counting". BLS has two limits for processing results: maximum number of hits to retrieve/process and maximum number of hits to count. Retrieving or processing hits means the hit is stored and will appear on the results page, is sorted, grouped, faceted, etc. If the retrieval limit is reached, BLS will still keep _counting_ hits (to determine the total number of hits) but will no longer store them.

:::

## Examples

There's code examples of using BlackLab Server from [a number of different programming languages](from-different-languages.md).

Below are examples of individual requests to BlackLab Server.

**NOTE:** for clarity, double quotes have not been URL-encoded.

#### Searches

All occurrences of “test” in the “opensonar” corpus (CorpusQL query)

    http://blacklab.ivdnt.org/blacklab-server/opensonar/hits?patt="test"

All documents having “guide” in the title and “test” in the contents, sorted by author and date, results 61-90

    http://blacklab.ivdnt.org/blacklab-server/opensonar/docs?filter=title:guide&patt="test"& sort=field:author,field:date&first=61&number=30

Occurrences of “test”, grouped by the word left of each hit

    http://blacklab.ivdnt.org/blacklab-server/opensonar/hits?patt="test"&group=wordleft

Documents containing “test”, grouped by author

    http://blacklab.ivdnt.org/blacklab-server/opensonar/docs?patt="test"&group=field:author

Larger snippet around a hit:

    http://blacklab.ivdnt.org/blacklab-server/opensonar/docs/0345391802/snippet?hitstart=120&hitend=121&context=50

#### Information about a document

Metadata of document with specific PID

    http://blacklab.ivdnt.org/blacklab-server/opensonar/docs/0345391802

The entire original document

    http://blacklab.ivdnt.org/blacklab-server/opensonar/docs/0345391802/contents

The entire document, with occurrences of “test” highlighted (with <hl/\> tags)

    http://blacklab.ivdnt.org/blacklab-server/opensonar/docs/0345391802/contents?patt="test"

Part of the document (embedded in a `<blacklabResponse>` root element; BlackLab makes sure the resulting XML is well-formed)

    http://blacklab.ivdnt.org/blacklab-server/opensonar/docs/0345391802/contents?wordstart=1000&wordend=2000


#### Information about corpora

Information about the webservice; list of available corpora

    http://blacklab.ivdnt.org/blacklab-server/ (trailing slash optional)

Information about the “opensonar” corpus (structure, fields, (sub)annotations, human-readable names)

    http://blacklab.ivdnt.org/blacklab-server/opensonar/ (trailing slash optional)

Information about the “opensonar” corpus, include all values for "pos" annotation (listvalues is a comma-separated list of annotation names):

    http://blacklab.ivdnt.org/blacklab-server/opensonar/?listvalues=pos

Information about the “opensonar” corpus, include all values for "pos" annotation and any subannotations (listvalues may contain regexes):

    http://blacklab.ivdnt.org/blacklab-server/opensonar/?listvalues=pos.*

Autogenerated XSLT stylesheet for transforming whole documents (only available for configfile-based XML formats):

    http://blacklab.ivdnt.org/blacklab-server/input-formats/folia/xslt

