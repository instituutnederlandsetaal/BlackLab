# Find hits / group hits

Find occurrences of a text pattern in the corpus, optionally filtered on document metadata fields.

This resource can also group hits (returning a list of groups), or show the contents of one of the resulting groups.

This is generally the most-used endpoint for BlackLab Server, and includes the most features.  For a more gentle introduction, see the [overview](/server/overview.md).

**URL** : `/blacklab-server/<corpus-name>/hits`

**Method** : `GET`

All parameters except `patt` are optional.

#### Basic parameters

Use these to find text patterns in the corpus and control which results are returned.

| Parameter     | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `patt`        | [BlackLab Corpus Query Language](/guide/corpus-query-language.md) (BCQL) pattern to search for                                                                                                                                                                                                                                                                                                                                                                                                  |
| `filter`      | [Lucene Query Language](https://lucene.apache.org/core/8_8_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description) document filter query                                                                                                                                                                                                                                                                                                                  |
| `context`     | (formerly `wordsaroundhit`) how much context to return around hits. Examples: `5` gives 5 words around hit, `5:10` gives 5 before and 10 after, `s` returns full sentences (if `s` is an inline tag in your data). Default: `5`                                                                                                                                                                                                                                                                 |
| `first`       | first result (0-based) to return with this request. Use this to get a page of results from the total set. Default: `0`                                                                                                                                                                                                                                                                                                                                                                          |
| `number`      | number of results to return (if available) with this request. Use this to get a page of results from the total set. Default: `50`.<br/>**NOTE:** this value is limited by the [`parameters.pageSize.max` setting](/server/configuration.md#complete-config-file) in `blacklab-server.yaml`.<br/>**NOTE2:** if you are only interested in the total number of results, not the results themselves, set this to 0. The total number of results will be in the response as `summary.numberOfHits`. |

#### Parameters for sampling

Take a random sampling of hits. Note that this has to retrieve all hits (or at least as many hits as the `maxretrieve` setting allows), then perform the sampling, so it may take a while.

| Parameter    | Description                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sample`     | Percentage of hits to select. Chooses a random sample of all the hits found.                                                                                                                                                                                                                                                                                                                                                                           |
| `samplenum`  | Exact number of hits to select. Chooses a random sample of all the hits found.                                                                                                                                                                                                                                                                                                                                                                         |
| `sampleseed` | Signed long seed number for sampling. If given, uses this value to seed the random number generator, ensuring identical sampling results next time. Please note that, without sorting, hit order is undefined (if the same data is re-indexed, hits may be produced in a different order). So if you want true reproducability, you should always sort hits that you want to sample, ideally with multiple sort criteria so the sort is fully defined. |



#### Parameters for sorting and grouping hits and faceting documents

Hits can be grouped by various criteria (see below). Faceting can be performed on the matching documents.

| Parameter                          | Description                                                                                                                                                                                                                                               |
|------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sort`                             | One or more sort criteria, for example: `hit:word:s` to sort by matched words, case-sensitively. Detailed explanation below.                                                                                                                              |
| `group`                            | One or more grouping criteria, for example: `before:lemma:i:1` to group on the lemma before the matched words, case-insensitively. Detailed explanation below.                                                                                            |
| `includegroupcontents`             | Whether to include the hits with each group.<br/>**NOTE:** only works for `/hits` requests for now. Default: `false`                                                                                                                                      |
| `viewgroup`                        | Identity of one of the groups to view (`identity` values are returned with the grouping results).<br/>**NOTE:** may not return all hits in the group because there is a limit to how many are stored. Use `hitfiltercrit`/`hitfilterval` to get all hits. |
| `hitfiltercrit` and `hitfilterval` | Filter hits by a criterium and value. See below.                                                                                                                                                                                                          |
| `facets`                           | Document faceting criteria, comma-separated. For example: `field:author` to facet matched documents by author. Detailed explanation below.                                                                                                                |

::: details <b>The <code>hitfiltercrit</code> / <code>hitfilterval</code> parameters explained</b>
These parameters can be used to view all hits in a "group", and even perform another grouping on the results. For example: `after:pos:i:1` to filter hits by the `pos` value directly following the hit, case-insensitively. Detailed explanation below. Also needs `hitfilterval` (the value to keep when filtering) to work.

This is useful if you want to view hits in a group, and then be able to group on those hits again. These two parameters essentially do something similar to the `viewgroup` parameter: that parameter also allows you to view the hits in a group, but won't allow you to group that subset of hits again. By specifying multiple criteria and values to `hitfiltercrit`/`hitfilterval`, you can keep diving deeper into your result set.

Note that this may be slow at present because it finds all hits, then filters them by this criterium.

(TECHNICAL NOTE: performance could be improved by internally extending the specified `patt` with the `hitfiltercrit`/`hitfilterval` so we only find the matches we're interested in, but this requires lookahead/lookbehind to be implemented).
:::

::: warning PLEASE NOTE
`sort`, `group` and `hitfiltercrit`/`hitfilterval` all require all results to be retrieved (or at least as much as allowed by the `maxretrieve` value), so they may take a lot of time and memory.
:::

Criteria for sorting, grouping and faceting are explained in "Criteria for sorting, grouping and faceting" below.

#### Criteria for sorting, grouping and faceting

The `sort`, `group`, `hitfiltercrit` and `facets` parameters receive one or more criteria (comma-separated) that indicate what to sort, group, filter or facet on.

These are the basic criteria:

| Criterium                  | Meaning                                                                                                                                                              |
|----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| <code>field:_name_</code>  | Metadata field. Example: `field:author`                                                                                                                              |
| <code>decade:_name_</code> | Sort/group by the decade of the year given in specified metadata field (rounds the value of the field down to the nearest multiple of 10, so `1976` becomes `1970`). |
| `identity`                 | (for sorting results of a grouping request) Sort by group identity.                                                                                                  |
| `size`                     | (for sorting results of a grouping request) Sort by group size, descending by default.                                                                               |

Any sort criterium can be reversed by prefixing it with a dash, e.g. `-field:year` to sort by the `year` field, descending. If multiple properties are combined with commas, each may be individually reversed or not, e.g. `-prop1,prop2,-prop3` to only reverse `prop1` and `prop3`. It is also possible to reverse all properties together: `-(prop1,prop2,prop3)`.

In addition to the basic criteria, it is also possible to sort/group on context words, such as the words matched by your query, or the words before or after the matched words:

| Criterium                                                                                                         | Meaning                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|-------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| <code>hit:_annot_:_c_</code>                                                                                      | Sort/group/facet on matched text. If `annot` is omitted, the default annotation (usually `word`) is used. `c` can specify case-sensitivity: either `s` (sensitive) or `i` (insensitive). Examples: `hit`, `hit:lemma`, `hit:lemma:s`.                                                                                                                                                                                                                                                                              |
| <code>before:_annot_:_c_:_n_</code><br><code>left:_annot_:_c_:_n_</code>                                          | Context words before the hit (if number `n` not specified, uses default before-hit context size). For grouping, you might use `n==1`, and for sorting a larger value (or the default, usually `5`) might be more suitable. Examples: `before`, `before:pos`, `before:pos:s`, `before:pos:s:3`                                                                                                                                                                                                                      |
| <code>after:_annot_:_c_:_n_</code><br><code>right:_annot_:_c_:_n_</code>                                          | Context words after the hit (if number `n` not specified, uses default after-hit context size). For grouping, you might use `n==1`, and for sorting a larger value (or the default, usually `5`) might be more suitable. Examples: `after`, `after:pos`, `after:pos:s`, `after:pos:s:3`                                                                                                                                                                                                                            |
| <code>wordleft:_annot_:_c_</code>                                                                                 | Single word of before-context. Deprecated, use <code>before:_annot_:_c_:1</code> instead.                                                                                                                                                                                                                                                                                                                                                                                                                          |
| <code>wordright:_annot_:_c_</code>                                                                                | Single word of after-context. Deprecated, use <code>after:_annot_:_c_:1</code> instead.                                                                                                                                                                                                                                                                                                                                                                                                                            |
| <code>context:_annot_:_c_:_spec_</code>                                                                           | More generic context words expression, giving the user more control at the cost of a bit of speed. Example: `context:word:s:H1-2` (first two matched words). See below for a complete specification.                                                                                                                                                                                                                                                                                                               |
| <code>capture:_annot_:_c_:_groupname_</code><br><code>capture:_annot_:_c_:_groupname_:_spanmode_</code>           | Contents of a named capture group in your text pattern. Example: `capture:word:s:PERSON` for the contents of the capture group named `PERSON` (your pattern might be `"talk" "to" PERSON:[]`). <br/><br/>When grouping by matched (dependency) relations, (i.e. with query `_ -nsubj-> _` to find subject relations), you can add `source` or `target` as the `spanmode` to group by either the source or target of the relation (default `spanmode` is `full`, which for relations spans both source and target). |
| <code>span-attribute:_spanName_:_attribute_:_c_</code><br><code> | Attribute of a span captured by your query. Example: `span-attribute:speech:lang:c` for the contents of the span named `speech` (your pattern might be `"test" within <speech />`).                                                                                                                                                                                                                                                                                                                                |

::: details <b>The <code>context</code> criterium explained</b>
Criteria like `context:word:s:H1-2` ("the first two matched words") allow fine control over what to group or sort on.

Like with criteria such as `left`, `right` or `hit`, you can vary the annotation to group or sort on (e.g. `word`/`lemma`/`pos`, or whatever annotations your data set has). You may specify whether to sort/group case- and accent-sensitively (`s`) or insensitively (`i`).

The final parameter to a `context` criterium is the "specification". This consists of one or more parts separated by a semicolon. Each part consists of an "anchor" (starting point) and number(s) to indicate a stretch of words. The anchor can be `H` (hit text), `E` (hit text, but counted from the end of the hit backwards), `L` (words to the left of the hit) or `R` (words to the right of the hit). The number or numbers after the anchor specify what words you want from this part. A single number indicates a single word; `1` is the first word, `2` the second word, etc. So `E2` means "the second-to-last word of the hit". Two numbers separated by a dash indicate a stretch of words. So `H1-2` means "the first two words of the hit", and `E2-1` means "the second-to-last word followed by the last word". A single number followed by a dash means "as much as possible from this part, starting from this word". So `H2-` means "the entire hit text except the first word".

A few more examples:
- `context:word:s:H1;E1`: the first and last matched word)
- `context:word:s:A2-3`: second and third word after the match)
- `context:word:s:B1-`: words before the match, starting from first word before the match, i.e. the same as `before:word:s`. How many words of context are used depends on the `context` parameter (default: 5)
:::

::: details Parallel corpora and grouping/sorting on context

In parallel corpora, you can prefix the annotation name with the version or field name and a `%` to specify what version you're referring to.

For example, if your parallel corpus has versions `en` and `nl`, you can 
specify `hit:en%word:s` to sort/group on the English version of the matched
text, or `before:nl%word:s:1` to sort/group on the Dutch version of the word.

You may also use the full field name, e.g. `hit:contents__en%word:s` or `before:contents__nl%word:s:1`.

:::

#### Miscellaneous parameters

Some less commonly used parameters for advanced use cases.

| Parameter           | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `waitfortotal`      | Whether or not to wait for the total number of results to be known. If no (the default), subsequent requests (with number=0 if you don’t need more hits) can be used to monitor the total count progress. Default: `false`                                                                                                                                                                                                                                                                        |
| `listvalues`        | Comma-separated list of annotation names to return for each result. By default, all annotations are included.                                                                                                                                                                                                                                                                                                                                                                                     |
| `field`             | the annotated field to search using `patt`, if your corpus contains multiple annotated fields. Most corpora only contain one. Defaults to the first (or only) annotated field defined. (NOTE: for a parallel corpus, if you only specify a version, e.g. `en`, BlackLab will automatically use the `contents__en` field if that exists)                                                                                                                                                           |
| `pattlang`          | pattern language to use for `patt`. Defaults to `bcql` (BlackLab Corpus Query Language). The other values (`contextql` and `luceneql`) have very limited support at the moment.  Other, more useful query languages may be added in the future.                                                                                                                                                                                                                                                   |
| `pattgapdata`       | Data (TSV, tab-separated values) to put in gaps in query. Explained below.                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `filterlang`        | filter language to use for `filter`. Defaults to `luceneql`. `contextql` is also supported, but very limited. More options may be added in the future.                                                                                                                                                                                                                                                                                                                                            |
| `docpid`            | filter on a single document pid.                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `maxretrieve`       | Maximum number of hits to retrieve. `-1` means "no limit". Also affects documents-containing-pattern queries and grouped-hits queries. Default and maximum allowed value configurable. Very large values (millions, or unlimited) may cause server problems.                                                                                                                                                                                                                                      |
| `maxcount`          | Maximum number of hits to count. `-1` means "no limit". Default and maximum allowed value configurable. Even when BlackLab stops retrieving hits, it still keeps counting them. For large results sets this may take a long time.                                                                                                                                                                                                                                                                 |
| `usecontent`        | `fi` or `orig`. `fi` (default) uses the forward index to reconstruct document content (for snippets and concordances; inline tags are lost in the process), `orig` uses the original XML from the content store (slower but more accurate).<br/>**NOTE:** using the original content may cause problems with well-formedness; these are fixed automatically, but the fix may result in inline tags in strange places (e.g. a start-sentence tag that is not at the start of the sentence anymore) |
| `escapexmlfragment`  | when using `usecontent=orig` and XML response, should fragments be escaped as CDATA or not? Default: `false` for API v4 and older, `true` for v5+.                                                                                                                                                                                                                                                                                                                                                |
| `calc`              | specify the value `colloc` to calculate collocations (frequency lists of words near hits). Experimental feature.                                                                                                                                                                                                                                                                                                                                                                                  |
| `omitemptycaptures` | if true, will omit capture groups of length 0 (default `false`, configurable in blacklab-server.yaml)                                                                                                                                                                                                                                                                                                                                                                                             |
| `adjusthits`        | (relations queries only) should query hits be adjusted so all matched relations are inside the hit? Default: `no`                                                                                                                                                                                                                                                                                                                                                                                 |

::: details <b>The <code>pattgapdata</code> parameter explained</b>
You may leave 'gaps' in the double-quoted strings in your BCQL query that can be filled in from tabular data. The gaps should be denoted by `@@`, e.g. `[lemma="@@"]` or `[word="@@cat"]`. For each row in your TSV data, will fill in the row data in the gaps. The queries resulting from all the rows are combined using OR. For example, if your query is `"The" "@@" "@@"` and your TSV data is `white\tcat\nblack\tdog`, this will execute the query `("The" "white" "cat") | ("The" "black" "dog")`. Please note that if you want to pass a large amount of data, you should use a `POST` request as the amount of data you can pass in a `GET` request is limited.  
:::


## Success Response

**Code** : `200 OK`

### Content examples

TODO

```json

```

## TODO

- This endpoint does a lot of different things, producing different responses. This can be confusing. We could consider moving the grouping operations to one or more new endpoints.
