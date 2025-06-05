# Parse a pattern without searching

Parse a text pattern, returning the JSON query structure without actually executing the search.

This operation can be useful when e.g. building a query parser, especially if you want to be able to switch back and forth between the query parser and an input field where the user can manually enter BlackLab Corpus Query Language.

If you don't specify `pattlang`, the pattern type passed in `patt` will be autodetected (CorpusQL or JSON query structure). But if you know you're passing in JSON, it's best to set `pattlang` to `json`. This will give a relevant error message if parsing fails.

**NOTE:** this operation only supports the JSON response type. Requesting XML will result in an error.

**URL** : `/blacklab-server/<corpus-name>/parse-pattern`

**Method** : `GET`

#### Parameters

The `patt` parameter is required.

| Parameter    | Description                                                                                                                                                                                                                                     |
|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `patt`       | [BlackLab Corpus Query Language](/guide/corpus-query-language.md) (BCQL) pattern to search for                                                                                                                                                  |
| `pattlang`   | pattern language to use for `patt`. Defaults to `bcql` (BlackLab Corpus Query Language). The other values (`contextql` and `luceneql`) have very limited support at the moment.  Other, more useful query languages may be added in the future. |
| `adjusthits` | (relations queries only) should query hits be adjusted so all matched relations are inside the hit? Default: `no`                                                                                                                                                                                                                                                                                                                                                                               |

**NOTE:** `pattgapdata` is not supported for this endpoint! (this is a rarely-used feature that might be removed in the future)

## Success Response

**Code** : `200 OK`

Note: if parsing the pattern fails, the request will still succeed, but the response will contain an error message (in `parsed.error`).

### Content examples

In the result object, `params` contains the parameters passed in, and `parsed` contains the parsed pattern, both in JSON query structure form and as re-serialized CorpusQL.

The re-serialized CorpusQL may have slight differences from your input CorpusQL, e.g. extra or fewer parentheses, but they should be semantically identical.

Result for `/parse-pattern?patt=%27de%27`:

```json
{
    "params": {
        "patt": "[word='de']",
        "pattlang": "default"
    },
    "parsed": {
        "bcql": "[word='de']",
        "json": {
            "type": "regex",
            "value": "de",
            "annotation": "word"
        }
    }
}
```

Result for `/parse-pattern?outputformat=json&patt=%7B"type"%3A"posfilter"%2C"producer"%3A%7B"type"%3A%20"regex"%2C"value"%3A%20"de"%7D%2C"filter"%3A%20%7B"type"%3A%20"tags"%2C"name"%3A%20"s"%7D%2C"operation"%3A%20"within"%7D`:

```json
{
  "params": {
    "patt": "{\"type\":\"posfilter\",\"producer\":{\"type\": \"regex\",\"value\": \"de\"},\"filter\": {\"type\": \"tags\",\"name\": \"s\"},\"operation\": \"within\"}",
    "pattlang": "default"
  },
  "parsed": {
    "bcql": "'de' within <s/>",
    "json": {
      "type": "posfilter",
      "producer": {
        "type": "regex",
        "value": "de"
      },
      "filter": {
        "type": "tags",
        "name": "s"
      },
      "operation": "within"
    }
  }
}
```

## Notes

- it is in theory possible to create a JSON query structure that cannot be (re-)serialized to BlackLab Corpus Query Language, although structures from parsing CorpusQL can always be reserialized of course. Should serialization fail, the `corpusql` key will be missing from the response and the `corpusql-error` key will indicate why the pattern couldn't be serialized.

## JSON query structure

| `type`        | properties                                                     | CorpusQL equivalent                                                                                | Notes                                                                                                                                                                                                         |
|---------------|----------------------------------------------------------------|----------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `and`         | `clauses`                                                      | `clauses[0] & ...`                                                                                 |                                                                                                                                                                                                               |
| `anytoken`    | `min`[,`max`]                                                  | `[]{min,max}`<br>`[]{min,`}<br>`[]{*\|+\|?}`                                                       | `max` omitted if there is no limit                                                                                                                                                                            |
| `capture`     | `clause`,`capture`                                             | `capture:(clause)`                                                                                 | `clause` will be captured using name `capture`                                                                                                                                                                |
| `constrained` | `clause`,`constraint`                                          | `clause :: constraint`                                                                             | e.g. `A:[] 'and' B:[] :: A.word = B.word`                                                                                                                                                                     |
| `defval`      |                                                                | `_`                                                                                                | used in relations queries ("don't care" value) and function calls ("use default value")                                                                                                                       |
| `not`         | `clause`                                                       | `!clause`                                                                                          |                                                                                                                                                                                                               |
| `or`          | `clauses`                                                      | `clauses[0] \| ...`                                                                                |                                                                                                                                                                                                               |
| `posfilter`   | `producer`,`filter`,`operation`[, ...]                         | `producer within filter`<br>`producer containing filter`                                           | When parsing CorpusQL, you will only get `producer,`filter` and `operation` (which will be `within` or `containing`). Other parameters are used internally.                                                   |
| `callfunc`    | `name`,`args`                                                  | `name(...args)`                                                                                    | E.g. `rel('det', [])`                                                                                                                                                                                         |
| `regex`       | `value`[,`annotation`,`sensitivity`]                           | `[annotation="value"]`<br>`"value"`                                                                | `sensitivity` is used internally.                                                                                                                                                                             |
| `relmatch`    | `parent`,`children`                                            | `parent -->children[0] ; ...`                                                                      | See `reltarget` for the child clauses.                                                                                                                                                                        |
| `reltarget`   | `reltype`,`clause`[,`negate`,`spanmode`,`direction`,`capture`] | `-reltype-> clause`<br>`!-reltype-> clause`<br>`^-reltype-> clause`<br>`capture:-reltype-> clause` | `negate` defaults to `false`, `spanmode` to `source`, `direction` to `both`, `capture` to empty string. Set `direction` to `root` and `spanmode` to `target` for root relations (relations without a source). |
| `repeat`      | `clause`,`min`[,`max`]                                         | `clause{min,max}`<br>`clause{min,`}<br>`clause{*\|+\|?}`                                           |                                                                                                                                                                                                               | `max` omitted if there is no limit
| `sequence`    | `clauses`                                                      | `clauses[0] ...`                                                                                   |                                                                                                                                                                                                               |
| `tags`        | `name`[,`attributes`,`adjust`,`capture`]                       | `<name att0key="att0value" ... />`<br>`<name>`<br>`</name>`<br>`capture:<name/>`                   |                                                                                                                                                                                                               | `adjust` defaults to `full_tag`; use `leading_edge` and `trailing_edge` for the open/close tag positions.


The following should never be produced by parsing a CorpusQL query, but are still available if needed:

| `type`         | properties                         | CorpusQL equivalent       | Notes                                                                                |
|----------------|------------------------------------|---------------------------|--------------------------------------------------------------------------------------|
| `edge`         | `clause`,`trailingEdge`            | (none)                    | Returm leading edge of `clause` if `trailingEdge` is false, trailing edge otherwise. |
| `expansion`    | `clause`,`direction`,`min`[,`max`] | e.g. `clause []{min,max}` | `max` omitted if there is no limit                                                   |
| `filterngrams` |                                    |                           | Used for optimization of certain queries, e.g. `[]{2,3} within <s/>`.                |
| `fixedspan`    |                                    |                           | Used to get a specific span from `start` to `end`                                    |
| `term`         |                                    |                           | Terms in CorpusQL queries are always parsed as regexes.                              |


## TODO

- Document JSON query structure (probably on a separate page)
