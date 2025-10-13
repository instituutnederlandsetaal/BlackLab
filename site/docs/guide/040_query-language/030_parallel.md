# Parallel corpus querying

::: tip Supported from v4.0
Indexing and searching parallel corpora is supported from BlackLab 4.0.
:::

A parallel corpus is a corpus that contains multiple versions of the corpus content, usually from different languages and/or time periods, and record the alignment between the versions at different levels (e.g. paragraph, sentence, word).

For example, you could have a parallel corpus of EU Parliament discussions in the various European languages, or a parallel corpus of different translations of a classic work such as Homer's Odyssey.

See [indexing parallel corpora](../index-your-data/parallel-corpora.md).

BlackLab's parallel corpus functionality uses cross-field relations to find alignments between the content versions available in your corpus.

The alignments operator `==>` is specifically to find alignments between versions in your corpus. It essentially means "capture all relations between (part of) the left and right span". It will capture a list of relations in the response.

## Basic parallel querying

For example, if your corpus contains fields `contents__en` (English version) and `contents__nl` (Dutch version), and English is the default field (the first one defined in your indexing config), you can find the Dutch translation of an English word using:

```
"cat" ==>nl _
```

The hit for this query will be `cat` in the English field, and the match info will contain a group named `rels` with all alignment relations found (just the one in this case, between the word `cat` and its Dutch equivalent). The hit response structure will also contain an `otherFields` section containing the corresponding Dutch content fragment. The location of the Dutch word aligned with the English word `cat` can be found from the relation in the `rel` capture, which includes `targetField`, `targetStart` and `targetEnd`.

Assuming your data has both sentence and word alignments, and you want to find all alignments for a sentence containing `cat`, you could use:

```
<s/> containing "cat" ==>nl _
```

This should find aligning English and Dutch sentences, including any word alignments between words in those sentences. You can also filter by alignment type, as we'll show later.

::: details Required versus optional alignment

The `==>` operator will _require_ that an alignment exists. If you wish to see all hits on the left side of the `==>nl` regardless of whether any alignments to the right side can be found, use `==>nl?`.

For example, if you're searching for translations of `cat` to Dutch, with `==>nl` you will _only_ see instances where `cat` is aligned to a Dutch word; on the other hand, with `==>nl?` you will see both English `cat` hits where the translation to Dutch was found, and `cat` hits where it wasn't.

:::

## Switching the main search field

If you want to search the Dutch version instead, and find alignments with the English version, you would use this query:

```
"kat" ==>en _
```

But of course, the main search field shouldn't be `contents__en` in this case; we want to switch it to `contents__nl`. You can specify a main search field other than the default with the BLS parameter `field`. In this case, if you specify `field=nl`. BlackLab will automatically recognize that you're specifying a version of the main annotated field and use the correct 'real' field, probably `contents__nl` in this case.

## Filtering the target span

In the previous example, we used `_` as the target span. This is the default, and means "the best matching span".

But you can also specify a different target span. For example, to find where _fluffy_ was translated to _pluizig_:

```
"fluffy" ==>nl "pluizig"
```

This will execute the left and right queries on their respective fields and match the hits by their alignment relations.

## Multiple alignment queries

You can also use multiple alignment operators in a single query to match to more than one other version:

```
"fluffy" ==>nl "pluizig" ;
         ==>de "flauschig"
```

## Only matching some (alignment) relations

Just like with other relations queries, you can filter by type:

```
"fluffy" =word=>nl "pluizig"
```

This will only find relations of type `word`. The type filter will automatically determine the capture name as well, so any relation(s) found will be captured as `word` in this case instead of `rels` (unless an explicit name is assigned, see below).

## Renaming the relations capture

You can use a override the default name `rels` for the alignment operator's captures:

```
<s/> alignments:==>nl _
```

Now the alignment relations will be captured in a group named `alignments`.


## Capturing in target fields

You can capture parts of the target query like normal, e.g.:

```
"and" w1:[] ==>nl "en" w2:[]
```

There will be one match info named `w1` for the primary field searched (English in this case), and one named `w2` for the target field (Dutch).

## rfield(): get only hits from a target field

If you only want to see hits from the target field, you can use the `rfield` operator:

```
rfield("fluffy" =word=>nl "pluizig", "nl")
```

This can be useful when, after running a parallel query, you want to show the highlighted contents of one of the target fields. In this case, you would like to only get the target hits (in `contents__nl`), not the source hits (in e.g. `contents__en`). 

