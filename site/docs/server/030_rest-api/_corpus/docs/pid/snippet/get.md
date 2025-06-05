# Document snippet

Get a snippet of the document.

**URL** : `/blacklab-server/<corpus-name>/docs/<pid>/snippet`

**Method** : `GET`

#### Parameters

All position elements refer to token position in a document, 0-based.

You should either specify `wordstart` and `wordend` for a snippet without a hit, or `hitstart`, `hitend` and (optionally) `context` for a snippet around a hit.

Partial contents XML output will be wrapped in `<blacklabResponse/>` element to ensure a single root element.

| Parameter          | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `wordstart`        | First word of the snippet/part of the document we want.                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `wordend`          | First word after the snippet/part of the document we want. -1 for the last word.                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `hitstart`         | First word of the hit we want a snippet around (default: 0)                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `hitend`           | First word after the hit we want a snippet around (default: 1)                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `context`          | (formerly `wordsaroundhit`) When specifying `hitstart` and `hitend`, this specifies how much context around the hit we want in our snippet. Examples: `5` gives 5 words around hit, `5:10` gives 5 before and 10 after, `s` returns full sentences (if `s` is an inline tag in your data)                                                                                                                                                                                                         |
| `field`            | Annotated field to get the contents for.  (default: main annotated field)<br>(**NOTE:** in case of a parallel corpus, you can shorten this to only the version, e.g. `nl` if you want to get the contents for field `contents__nl`)                                                                                                                                                                                                                                                               |
| `usecontent`       | `fi` or `orig`. `fi` (default) uses the forward index to reconstruct document content (for snippets and concordances; inline tags are lost in the process), `orig` uses the original XML from the content store (slower but more accurate).<br/>**NOTE:** using the original content may cause problems with well-formedness; these are fixed automatically, but the fix may result in inline tags in strange places (e.g. a start-sentence tag that is not at the start of the sentence anymore) |
| `escapexmlfragment` | when using `usecontent=orig` and XML response, should fragments be escaped as CDATA or not? Default: `false` for API v4 and older, `true` for v5+.                                                                                                                                                                                                                                                                                                                                                |
| `listvalues`       | Comma-separated list of annotation names to include. By default, all annotations are included.                                                                                                                                                                                                                                                                                                                                                                                                    |


## Success Response

**Code** : `200 OK`


### Content examples

Response with `hitstart` and `hitend`:

```json
{
  "docPid": "bab0604",
  "start": 50,
  "end": 51,
  "left": {
    "punct":[" "],
    "lemma":["de"],
    "grouping_lemma":["de"],
    "grouping_pos_full":["PD(subtype=art)"],
    "grouping_pos":["PD"],
    "word_or_lemma":["den"],
    "wordform_group_id":[""],
    "pos":["PD"],
    "isclitic":["nonclitic"],
    "iswordpart":["complete"],
    "word_xml":["den"],
    "lempos":["de \/ PD(subtype=art)"],
    "word":["den"]
  },
  "match": {
    "punct":[" "],
    "lemma":["negende"],
    "grouping_lemma":["negende"],
    "grouping_pos_full":["NUM"],
    "grouping_pos":["NUM"],
    "word_or_lemma":["9"],
    "wordform_group_id":[""],
    "pos":["NUM"],
    "isclitic":["nonclitic"],
    "iswordpart":["complete"],
    "word_xml":["9"],
    "lempos":["negende \/ NUM"],
    "word":["9"]
  },
  "right": {
    "punct":[" "],
    "lemma":["augustus"],
    "grouping_lemma":["augustus"],
    "grouping_pos_full":["NOU-C"],
    "grouping_pos":["NOU-C"],
    "word_or_lemma":["achustie"],
    "wordform_group_id":[""],
    "pos":["NOU-C"],
    "isclitic":["nonclitic"],
    "iswordpart":["complete"],
    "word_xml":["achustie"],
    "lempos":["augustus \/ NOU-C"],
    "word":["achustie"]
  }
}
```
