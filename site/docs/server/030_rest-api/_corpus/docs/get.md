# Find documents / group documents

Find documents in which a text pattern occurs and/or matching a metadata filter query. Each document result will include some snippets of matches.

Without a `filter` query, it will list all documents in the corpus.

This resource can also group documents (returning a list of groups), or show the contents of one of the resulting groups.

**URL** : `/blacklab-server/<corpus-name>/docs`

**Method** : `GET`

All parameters are optional. If no parameters are supplied, the default is to return all documents (with a maximum number of document per page).

#### Basic parameters

Use these to find text patterns in the corpus and control which results are returned.

| Parameter | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `patt`    | [BlackLab Corpus Query Language](/guide/corpus-query-language.md) (BCQL) pattern to search for                                                                                                                                                                                                                                                                                                                                                                                                  |
| `filter`  | [Lucene Query Language](https://lucene.apache.org/core/8_8_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description) document filter query                                                                                                                                                                                                                                                                                                                  |
| `context` | (formerly `wordsaroundhit`) how much context to return around hits. Examples: `5` gives 5 words around hit, `5:10` gives 5 before and 10 after, `s` returns full sentences (if `s` is an inline tag in your data). Default: `5`                                                                                                                                                                                                                                                                 |
| `first`   | first result (0-based) to return with this request. Use this to get a page of results from the total set. Default: `0`                                                                                                                                                                                                                                                                                                                                                                          |
| `number`  | number of results to return (if available) with this request. Use this to get a page of results from the total set. Default: `50`.<br/>**NOTE:** this value is limited by the [`parameters.pageSize.max` setting](/server/configuration.md#complete-config-file) in `blacklab-server.yaml`.<br/>**NOTE2:** if you are only interested in the total number of results, not the results themselves, set this to 0. The total number of results will be in the response as `summary.numberOfDocs`. |

#### Parameters for sampling

Take a random sampling of results. Note that this has to retrieve all results (or at least as many as the `maxretrieve` setting allows), then perform the sampling, so it may take a while.

| Parameter    | Description                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sample`     | Percentage of results to select. Chooses a random sample of all the results found.                                                                                                                                                                                                                                                                                                                                                                     |
| `samplenum`  | Exact number of results to select. Chooses a random sample of all the results found.                                                                                                                                                                                                                                                                                                                                                                   |
| `sampleseed` | Signed long seed number for sampling. If given, uses this value to seed the random number generator, ensuring identical sampling results next time. Please note that, without sorting, hit order is undefined (if the same data is re-indexed, hits may be produced in a different order). So if you want true reproducability, you should always sort hits that you want to sample, ideally with multiple sort criteria so the sort is fully defined. |



#### Parameters for sorting and grouping hits and faceting documents

Results can be grouped by various criteria (see below). Faceting can be performed on the matching documents.

| Parameter                          | Description                                                                                                                                                                                              |
|------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sort`                             | One or more sort criteria, for example: `field:title` to sort by the `title` metadata field, case-sensitively. Detailed explanation below.                                                               |
| `group`                            | One or more grouping criteria, for example: `field:author` to group by author, case-sensitively. Detailed explanation below.                                                                             |
| `viewgroup`                        | Identity of one of the groups to view (`identity` values are returned with the grouping results).<br/>**NOTE:** may not return all results in the group because there is a limit to how many are stored. |
| `facets`                           | Document faceting criteria, comma-separated. For example: `field:author` to facet matched documents by author. Detailed explanation below.                                                               |

::: warning PLEASE NOTE
`sort`, `group` and `hitfiltercrit`/`hitfilterval` all require all results to be retrieved (or at least as much as allowed by the `maxretrieve` value), so they may take a lot of time and memory.
:::

Criteria for sorting, grouping and faceting are explained in "Criteria for sorting, grouping and faceting" below.

#### Criteria for sorting, grouping and faceting

The `sort`, `group` and `facets` parameters receive one or more criteria (comma-separated) that indicate what to sort, group, filter or facet on.

These are the basic criteria:

| Criterium                  | Meaning                                                                                                                                                              |
|----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| <code>field:_name_</code>  | Metadata field. Example: `field:author`                                                                                                                              |
| <code>decade:_name_</code> | Sort/group by the decade of the year given in specified metadata field (rounds the value of the field down to the nearest multiple of 10, so `1976` becomes `1970`). |
| `numhits`                  | (for sorting per-document results) Sort by the number of hits in the document.                                                                                       |
| `identity`                 | (for sorting results of a grouping request) Sort by group identity.                                                                                                  |
| `size`                     | (for sorting results of a grouping request) Sort by group size, descending by default.                                                                               |


#### Miscellaneous parameters

Some less commonly used parameters for advanced use cases.

| Parameter          | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `waitfortotal`     | Whether or not to wait for the total number of results to be known. If no (the default), subsequent requests (with number=0 if you don’t need more hits) can be used to monitor the total count progress. Default: `false`                                                                                                                                                                                                                                                                        |
| `listvalues`       | Comma-separated list of annotation names to return for each result. By default, all annotations are included.                                                                                                                                                                                                                                                                                                                                                                                     |
| `field`            | the annotated field to search using `patt`, if your corpus contains multiple annotated fields. Most corpora only contain one. Defaults to the first (or only) annotated field defined.  (NOTE: for a parallel corpus, if you only specify a version, e.g. `en`, BlackLab will automatically use the `contents__en` field if that exists)                                                                                                                                                          |
| `pattlang`         | pattern language to use for `patt`. Defaults to `bcql` (BlackLab Corpus Query Language). The other values (`contextql` and `luceneql`) have very limited support at the moment.  Other, more useful query languages may be added in the future.                                                                                                                                                                                                                                                   |
| `pattgapdata`      | Data (TSV, tab-separated values) to put in gaps in query. Explained below.                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `filterlang`       | filter language to use for `filter`. Defaults to `luceneql`. `contextql` is also supported, but very limited. More options may be added in the future.                                                                                                                                                                                                                                                                                                                                            |
| `docpid`           | filter on a single document pid.                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `maxretrieve`      | Maximum number of hits to retrieve. `-1` means "no limit". Also affects documents-containing-pattern queries and grouped-hits queries. Default and maximum allowed value configurable. Very large values (millions, or unlimited) may cause server problems.                                                                                                                                                                                                                                      |
| `maxcount`         | Maximum number of hits to count. `-1` means "no limit". Default and maximum allowed value configurable. Even when BlackLab stops retrieving hits, it still keeps counting them. For large results sets this may take a long time.                                                                                                                                                                                                                                                                 |
| `usecontent`       | `fi` or `orig`. `fi` (default) uses the forward index to reconstruct document content (for snippets and concordances; inline tags are lost in the process), `orig` uses the original XML from the content store (slower but more accurate).<br/>**NOTE:** using the original content may cause problems with well-formedness; these are fixed automatically, but the fix may result in inline tags in strange places (e.g. a start-sentence tag that is not at the start of the sentence anymore) |
| `escapexmlfragment` | when using `usecontent=orig` and XML response, should fragments be escaped as CDATA or not? Default: `false` for API v4 and older, `true` for v5+.                                                                                                                                                                                                                                                                                                                                                |
| `calc`             | specify the value `colloc` to calculate collocations (frequency lists of words near hits). Experimental feature.                                                                                                                                                                                                                                                                                                                                                                                  |
| `adjusthits`       | (relations queries only) should query hits be adjusted so all matched relations are inside the hit? Default: `no`                                                                                                                                                                                                                                                                                                                                                                                 |


::: details <b>The <code>pattgapdata</code> parameter explained</b>
You may leave 'gaps' in the double-quoted strings in your BCQL query that can be filled in from tabular data. The gaps should be denoted by `@@`, e.g. `[lemma="@@"]` or `[word="@@cat"]`. For each row in your TSV data, will fill in the row data in the gaps. The queries resulting from all the rows are combined using OR. For example, if your query is `"The" "@@" "@@"` and your TSV data is `white\tcat\nblack\tdog`, this will execute the query `("The" "white" "cat") | ("The" "black" "dog")`. Please note that if you want to pass a large amount of data, you should use a `POST` request as the amount of data you can pass in a `GET` request is limited.  
:::


## Success Response

**Code** : `200 OK`

### Content examples

TODO

```json
{
  "summary": {
    "searchParam": {
      "filter": "afz_naam_norm:(Machiel Jochems)",
      "first": "0",
      "indexname": "BaB",
      "number": "20"
    },
    "searchTime": 1,
    "countTime": 1,
    "windowFirstResult": 0,
    "requestedWindowSize": 20,
    "actualWindowSize": 3,
    "windowHasPrevious": false,
    "windowHasNext": false,
    "stillCounting": false,
    "numberOfDocs": 3,
    "numberOfDocsRetrieved": 3,
    "docFields": {
      "pidField": "pid",
      "titleField": "title",
      "authorField": "author",
      "dateField": "witnessYear_from"
    },
    "metadataFieldDisplayNames": {
      "adr_loc_land_norm": "Country",
      "adr_loc_plaats_norm": "Place",
      ...
    }
  },
  "docs": [
    {
      "docPid": "bab0604",
      "docInfo": {
        "afz_loc_regio_norm": [
          "Noord-Europa"
        ],
        "afz_klasse": [
          "unknown"
        ],
        "afz_naam_norm": [
          "Machiel Jochems"
        ],
        "author": [
          "Machiel Jochems"
        ],
        "afz_loc_plaats_norm": [
          "Frederiksstad"
        ],
        "witnessYear_from": [
          "1666"
        ],
        "pid": [
          "bab0604"
        ],
        "adr_loc_land_norm": [
          "Nederland"
        ],
        "adr_naam_norm": [
          "Jannetje Alberts"
        ],
        "title": [
          "To Jannetje Alberts, 9 augustus 1666"
        ],
        "afz_loc_land_norm": [
          "Duitsland"
        ],
        "afz_geslacht": [
          "male"
        ],
        "afz_rel_tot_adr": [
          "friend (m)"
        ],
        "datum_jaar": [
          "1666"
        ],
        "fromInputFile": [
          "\/2.8TDN\/05-01-2009_001-002.exported.xml"
        ],
        "adr_loc_regio_norm": [
          "Noord-Holland"
        ],
        "autograaf": [
          "uncertain"
        ],
        "type_brief": [
          "private"
        ],
        "adr_loc_plaats_norm": [
          "Amsterdam"
        ],
        "signatuur": [
          "HCA 30-643"
        ],
        "lengthInTokens": 355,
        "mayView": true
      }
    },
    ...
  ]
}
```
