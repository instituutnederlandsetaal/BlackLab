# Collocations

<!-- @include: ../../../_from_v5.md -->

A _collocation_ is a series of words or terms that co-occur more often than would be expected by chance. For example, _bark_ and _yap_ are collocates of the keyword _dog_.

Collocations in BlackLab can be proximity-based (i.e. words that occur within 5 positions of _dog_) or relation-based (i.e. words that have some relation with _dog_).

You can find collocations using a regular `/hits` request with the `group` parameter, but there is also a `/collocations` endpoint that makes it a bit more convenient. It takes the components that make up a collocation request and constructs the correct query, grouping property, and group scorer configuration.

**URL**
- `/blacklab-server/<corpus-name>/collocations` (API `v4`)
- `/blacklab-server/corpora/<corpus-name>/collocations` (future API `v5`)

**Method** : `GET`

#### Parameters

Only `patt` is required; other parameters are optional.

| Parameter    | Description                                                                                                                                                                                                                |
|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `filter`     | [Lucene Query Language](https://lucene.apache.org/core/8_8_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description) document filter query, to find collocations in part of the corpus |
| `patt`       | [BlackLab Corpus Query Language](/guide/query-language/) (BCQL) collocation keyword: the pattern to find collocations for, e.g. `[lemma="dog"]` to find collocates of _dog(s)_.                                            |
| `collpatt`   | Collocate filtering (BCQL) pattern, e.g. `[pos="NOUN"]` to find only noun collocates                                                                                                                                       |
| `colltype`   | type of collocations to find: `proximity` (default) or `relsources`/`reltargets` (for relation-based collocations; see below)                                                                                              |
| `context`    | (proximity-based collocations) how close a word has to occur to be considered a collocate. Examples: `5` gives 5 words around the word, `5:10` gives 5 before and 10 after. Default: `5`                                   |
| `reltype`    | (relation-based collocations; optional) a specific relation type to match, or a regular expression to filter relation types.                                                                                               |
| `annotation` | annotation to use for gathering collocates (i.e. annotation to group on). Default: main annotation, usually `word`.                                                                                                        |
| `sensitive`  | whether to group case- and accent-sensitively or not. Default: `false`                                                                                                                                                     |
| `scorertype` | collocations found will be scored. This gives the hit group scorer to use. Builtin scorers are `coll-dice` (default) and `coll-salience`. See below.                                                  |

In addition to these basic parameters, any parameter that can be added to a regular grouped hits request can be used (e.g. `first`, `number`, etc.). See [here](find-hits).

## Scorers

Collocation groups are scored using a scorer formula:

| name             | description                               | formula                                    |
|------------------|-------------------------------------------|--------------------------------------------|
| `coll-dice`      | calculates the Dice Coefficient           | `(2 * f / (double) (f1 + f2))`             |
| `coll-salience`  | calculates a log-based salience measure   | `log(f) * log(f * N / (f1 * f2)) / log(2)` |

In the above formulas, `f` is the frequency of the keyword and collocate occurring together; `f1` and `f2` are the frequencies of the words separately; `N` is the total corpus size (or total cardinality of the relation type you searched for).

You can also use hit group scorers with a regular grouped `/hits` request. In this case, you should pass the `scorer` parameter with this JSON structure (commented for clarity):

```jsonc
{
  // id of the scorer to instantiate
  "id": "coll-dice",
  
  // document filter (if any; Lucene query language)
  "filter": "title:sea",
  
  // keyword BCQL pattern (used to determine keyword frequency)
  "patt": "\"boot\"",
  
  // relation type filter (if any, and if relation-based collocations) 
  "reltype": "nsubj",
  
  // annotation used for grouping
  "annotation": "word",
  
  // whether grouping was (case- and accent-)sensitive or not
  "sensitive": false
}
```

You can implement a custom collocation scorer by writing a plugin of type `HitGroupScorer`. See [plugins](/development/customization/). Note that in the future, other types of hit group scorer might be added, but currently, collocation scorers are the only possible type.

## Equivalent /hits requests

`/collocations` is just a convenience endpoint that performs a regular hits grouping. It exists because the `/hits` URL requires the user to understand the specific BCQL query needed, and the scorer configuration can be a bit cryptic.

### Proximity-based

This proximity collocations request (shown without URL encoding for readability):

    /CORPUSNAME/collocations?
        patt="boot"&
        collpatt=[pos="N.*"]&
        colltype=proximity&
        context=3:4&
        annotation=word&
        sensitive=false&
        scorertype=coll-dice

is exactly equivalent to this (shorter but more cryptic) grouped hits request:

    /CORPUSNAME/hits?
        patt=meet([pos="N.*"],"boot",-3,4)&
        group=hit:word:i&
        scorer={"id":"coll-dice","patt":"\"boot\"","annotation":"word","sensitive":false}

### Relation-based

This proximity collocations request (shown without URL encoding for readability):

    /CORPUSNAME/collocations?
        patt="boot"&
        collpatt=[pos="N.*"]&
        colltype=reltargets&
        reltype=nsubj&
        annotation=word&
        sensitive=false&
        scorertype=coll-dice

is exactly equivalent to this grouped hits request:

    /CORPUSNAME/hits?
        patt=rspan("boot" -nsubj-> [pos="N.*"], "target")&
        group=hit:word:i&
        scorer={"id":"coll-dice","patt":"\"boot\"","annotation":"word","sensitive":false}



## Success Response

**HTTP response code**: `200 OK`

The response will have the same structure as a `/hits` request with the `group` parameter. See [here](find-hits).
