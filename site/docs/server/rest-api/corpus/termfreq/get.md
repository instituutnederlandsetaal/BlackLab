# Term frequencies

Returns frequencies per term, sorted by descending frequency.

**NOTE:** consider using the [grouped hits](/server/rest-api/corpus/hits/get.md) operation instead, which can also return term frequencies (search for `patt=[]` and use e.g. `group=hit:word:s`) and e.g. allows sorting the results alphabetically (`sort=identity`) or by frequency (`sort=size`).

**URL** : `/blacklab-server/<corpus-name>/termfreq`

**Method** : `GET`

#### Parameters

| Parameter    | Description                                                                                                                                                                                                                                                     |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `annotation` | annotation to get term frequencies for. Default: main annotation (usually `word`)                                                                                                                                                                               |
| `sensitive`  | whether or not to list terms case/diacritics sensitively. If not (which is the default), capital letters and diacritics are ignored when counting frequencies, so `Het`, `hét` en `het` will be lumped together and the total reported as `het`. Default: `false` |
| `first`      | first result (0-based) to return. Use this to get a page of results from the total set. Default: `0`                                                                                                                                                            |
| `number`     | maximum number to return. Default: `20`.<br/>**NOTE:** this value is limited by the [`parameters.pageSize.max` setting](/server/configuration.md#complete-config-file) in `blacklab-server.yaml`. Pass `-1` to get the maximum allowed.                         |
| `filter`     | [Lucene Query Language](https://lucene.apache.org/core/8_8_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description) document filter query                                                                                  |
| `terms`      | comma-separated list of terms for which to get the frequencies. Default: all terms                                                                                                                                                                              |

**NOTE:** this operation always has to find the frequencies for all terms, even if it only needs to return one page.
Hence there is no `waitfortotal` parameter like some other operations have (you always have to wait). Results
are cached though, so after the first page is returned, using multiple requests to page through the results should be fast.

## Success Response

**Code** : `200 OK`

### Content examples

```json
{
  "termFreq": {
    "en": 14221,
    "de": 10540,
    "dat": 9546,
    "van": 9313,
    "te": 6922,
    "het": 6760,
    "met": 5468,
    "een": 5261,
    "in": 5101,
    "is": 5061,
    "ik": 4784,
    "mijn": 4649,
    "niet": 4001,
    "ick": 3773,
    "als": 3724,
    "ende": 3510,
    "den": 3439,
    "die": 3370,
    "soo": 3215,
    "op": 3083
  }
}
```

## Notes

Regular grouped hits could be used as well and should be decently fast, thanks to an optimization that recognizes this type of query (`patt` = any token (`[]`), group by match) and uses a faster path. However, that operation uses the forward index to find term frequencies, whereas this one uses Lucene's term dictionary. We should test for any differences and if there are none (which there shouldn't be), always use the fastest implementation.

After that, we could consider removing this endpoint, or we could keep it for convenience and backwards compatibility.
