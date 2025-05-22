# Parallel corpora

A parallel corpus has multiple versions of the same documents, often multiple languages or maybe historical versions. We want to be able to match across versions, e.g. find the German translation of a Dutch word.

Documents can have several _alignments_, i.e. per sentence, per word, etc.

## Summary of (provisional) technical decisions

We've decided on the following initial approach:

- We'll use one annotated field per document version.
- Version-specific field names must all end with `__VERSION`, so e.g. annotated fields `contents__nl` and `contents__de`, or metadata fields `title__nl` and `title__de`. (Metadata) fields without a suffix apply to all versions of the document, e.g. `subject`.
- We'll index alignment relations both ways for now (e.g. `nl->de` and `de->nl`), so we don't need special logic to allow searching in both directions.
- Alignment relations should use the `al` relation class by convention, suffixed with the version code, e.g. `al__de` (this is needed to search for alignments with a specific version). Any relation class suffixed with a version code will automatically be treated as a cross-field relation to that version of the document. So for example, a sentence alignment to German would have full relation type `al__de::s`.
- We'll use the `==>` operator to find all relations between two spans. Target version can be indicated by a suffix, e.g. `==>de`.
- You can set the default relations class you're searching using the new "query settings" (unary prefix) operator e.g. `@rc=al`. If no default class is set, all classes are matched, so you don't need to set it if your corpus only contains one relation class.
- We'll provide (rudimentary for now) support for XInclude to link XML documents together (e.g. link a document with alignments to the two contents documents those alignments refer to), but internally it will be treated as a single XML document.
- The XML will be stored in the content store of the main annotated field (which is the first one in the configuration file). The other annotated fields don't get their own content store but will automatically use the content store of the main annotated field.

The general approach is to only add the minimum of features needed to support parallel corpora, so we don't have to make too many changes to the core of BlackLab.

If any of these don't work out, we'll try one of the other approaches mentioned. See the Indexing section at the end of the document for more details.

## BCQL

### Default relations class

Because it gets quite convoluted to specify relations class in the relations operators (e.g. for alignment you would get something like `=al::.*=>de` instead of just `==>de`) , it would be useful to set a default value once for the query. We want something that can be used as a prefix operator. On the other hand, if we want to add more of these settings in the future, we don't want an explosion of new operators either. Proposal:

    # Set default relation class to 'al'
    @relationclass=al 
        'als [] 'en' []' ==>de 'wie' [] 'und' []'

    # Abbreviated form
    @rc=al 'als [] 'en' []' ==>de 'wie' [] 'und' []'

So `@` starts a "settings operator", which is a unary prefix operator where you can change settings for the rest of (this part of) the query. Multiple settings would be comma-separated. Currently only `relationclass` or `rc` is supported, but other settings could be added if useful.

`@relationclass=al` or `@rc=al` means "set the default relation class to `al`". You can even set the default target version by setting `@rc=al__de`; then you don't need to specify the target version later in the alignment operator.

Note that in many cases, you won't need to specify `relationclass`. If the only versioned relations in your corpus are alignment relations, they will automatically be found by the `==>de` operator, so you don't need to specify `rc`. Similarly, if your corpus only contains dependency relations, everything will work as expected automatically. It's only when you have multiple relation classes in a corpus that you need to either change the default, or be more explicit in the relations operator.


### Querying alignments

We're trying to find phrases with two aligned words in two languages, Dutch (`nl`) and German (`de`). The phrases should have the structure `als ... en ...` in Dutch and `wie ... und ...` in German, with the requirement that words in the gaps are aligned. So we're looking for phrases like `als kat en hond` in Dutch and `wie Katze und Hund` in German.

Our approach is to find the phrase in Dutch and capturing alignment relations to German. It should also be possible to find the phrase in both languages and capture alignment relations between the hits.

Proposed syntax for the above:

    # Find German equivalent of Dutch phrase
    # (based on alignment relations with source within span from left side)
    'als [] 'en' []' ==>de _

    # Find aligned Dutch and German phrases, capturing alignment relations
    # (at least one matching alignment relation must exist, or the hit is skipped)
    'als [] 'en' []' ==>de 'wie' [] 'und' []'

The next section explains how these queries work.

### New operator: find all relations between two spans

The `==>` operator is a new type of relation operator that finds all relations where the source of the relation is part of the left side hit. It also finds a right side span that encompasses all the matching relations' targets. It also required that the right side span contains a hit for the given right side query (here `'wie' [] 'und' []`), if any such query was given.

The `de` at the end of the relation operator shows that the relations we're looking for must be cross-field relations pointing from the current field (`contents__nl`, as indicated by the `field` parameter) to the `contents__de` field. Of course, the `==>` operator still supports the same relation class/type filters if necessary, so you can specify a different class or type, e.g. `=s=>de` or `=al::s=>de`.

This operator returns the left span and two captures: the list of relations as `rels/de` and the right part as `target/de`. The captures will indicate relations pointing to the `contents__de` field, or the capture itself being from that field.

If the default capture names don't work for you, you rename them:

    'als [] 'en' []' A:==>de B:('wie [] 'und' []')

### Alignments between more than two versions

Find corresponding sentences:

```
'als [] 'en' []'
    ==>de 'wie' [] 'und' []' ;
    ==>en 'as' [] 'and' []'
```

### Alignments between sentences

Find corresponding sentences:

```
<s/> containing 'als [] 'en' []'
    =s=>de _ ;
    =s=>en _
```


## Indexing

### Annotated fields

There seem to be three obvious options, of which option 2 seems like the most viable at the moment:

1. everything in one annotated field, e.g. Dutch annotations and German annotations, with alignment relations between the two versions. Easy to do, but that would mean a single annotated field would have two very different tokenizations (i.e. the 3rd word in Dutch need not correspond to the 3rd word in German at all). Throughout BlackLab, it is generally assumed that an annotated field has a single tokenization, so annotations are all automatically aligned.
2. one annotated field per version, so e.g. one field for Dutch and one for German. Alignment relations between the two versions. Seems better to keep the two tokenizations in separate fields.
3. one Lucene document per version of an input document. This seems more difficult to implement because we would have to combine hits from different documents to resolve a single query.

If option 2 doesn't work out, we may try one of the others.

### Field naming convention

In order for BlackLab to smoothly work with alignment between different fields representing versions of the same document, we should adopt a naming convention for these fields. This convention should respect the existing restrictions on field names in BlackLab.

Each field should have the same prefix name (e.g. `contents`), then a special separator (we'll use two underscores `__`), then the version code (e.g. language). So the Dutch version of the contents field would be `contents__nl`, and the German version `contents__de`.

Metadata fields specific to a version should use the same convention, so `title__nl` is the title of the Dutch version, etc. There may also be metadata fields that are not specific to a version, e.g. `subject` (subject of these documents). These don't get a suffix.

Users should not use `__` in other field names to avoid issues.

### Alignment relations: index once or twice?

Is it enough to have the relations only go from e.g. the `nl` field to the `de` field, or do we need to index them both ways? The latter is the easier approach, but might waste some disk space and memory.

The user may want to search both ways, and regular relations search only works in the direction it was indexed. Maybe we could introduce two-way relations, so that queries that go in the other direction can be resolved using the single indexed relation. So e.g. if the relations are indexed in the `nl` field, and the query asks for the Dutch translation of a German word, BlackLab should know to use the two-way relations in the `nl` field "in reverse" to answer this question.

If we do this, it would need to be a general mechanism, not a hack just for parallel corpora.

E.g. a we're searching for a relation _R_ between a source field _A_ and target field _B_, but _R_ is indexed in field _B_. BlackLab should know to use the two-way relation in field _B_ to answer this question. In other words: for relations between two fields, BlackLab will check if the relation type exists in the source field first, and if it doesn't, check if it exists in the target field as a two-way relation.

For now, let's just index the relations both ways, to avoid unnecessary complexity. If necessary, we can add the two-way relation mechanism later.

### Linking or embedding?

The example data (see below) consist of alignment files that link to the different versions of the content files.

Do we stay with this approach of several linked files, or do we embed the different versions in a single file?

- A single files with everything seems easier to index, but may become very large and seems awkward to store and display (in pages) / highlight. At the very least we'd need to keep track of the character offsets where different versions start and end so we can 'cut' the separate versions.
- A separate file per version is a bit trickier to index, but easier when storing and displaying.

For now, we'll stick with separate files.

### Linking mechanism

If we stick with linked files, do we use XInclude or our own linking mechanism?

- XInclude is a standard mechanism, which is generally a good thing, but it might be trickier to track when we enter a new file, keep track of character offsets in each file separately, etc. It does make referencing by `xml:id` easier, though, because logically everything is the same document.

- A custom approach allows us more freedom to track which file we're in and which character position we're at. But it's nonstandard, and we'd have to resolve `xml:id` references across different files, which becomes a bit messier.

We've built rudimentary XInclude support for now. We may improve upon this implementation later, or if problems arise, we may resort to the custom approach.

> See [Saxon and XInclude](https://www.saxonica.com/html/documentation10/sourcedocs/XInclude.html). We don't use this though, because we need to be able to track the character positions in the combined documents, not the individual files. Our initial implementation just recognizes XInclude tags using a regular expression and replaced them with the contents of the linked file. This is primitive and requires the tag to be written a certain way, but works well enough for now.

### Content stores

The complete document (after integrating the linked documents) will be stored in the content store for the main annotated field. The other annotated fields will not get their own content store, but will automatically use the content store of the main annotated field.

Advantage is that we have access to all the content and alignment relations in XML; disadvantage is that we still need some way to keep track of which part of the XML belongs to which version (so we can display a specific version). It may also cause issues if documents get very large.

### Metadata

Does every version have its own metadata? Does the combined "superdocument" have its own metadata? Probably yes for both.

We can use the same suffixes as for annotated fields to distinguish between metadata for each version.

Open question: does BlackLab need to "understand" what metadata fields belong with which version? Or is that something left to the client? For now we'll assume the latter.

### Configuring the different versions

A corpus might have many versions of a document, all referring to one another. Configuring each annotated field separately, and configuring the relations to each other version separately would be very tedious.

Here's an idea for a more compact way to configure this. A problem with this is that it includes a variable mechanism that operates directly on the hierarchical YAML structure, which is not compatible with how we currently process the YAML files. Of course we could refactor the code to make this possible, but it'd need to be a more DOM-like approach vs. the SAX-like approach we have now (because the `versions` key doesn't have to be the first). One option is to require that `versions` appears first for the moment, and refactor later if necessary.

```yaml
  contents:

    # Create several parallel versions of the contents field, e.g. contents__en, contents__nl, etc.
    #
    # Causes this annotated field to be created with each version suffix.
    # Does a search and replace of:
    # - $$VKEY with the version key (e.g. 'en') and
    # - $$VINDEX with the (1-based) index of the version (e.g. en=1, nl=2).
    # - $$VNAME with the (display) name (the value after the key, e.g. English/Dutch)
    #
    # Of course, you don't have to use the versions block at all; you can also explicitly
    # define annotated fields contents__nl, contents__en, etc.; the result will be identical,
    # just with a longer configuration with more duplication.
    versions:
      en: English
      nl: Dutch

    displayName: $$VNAME document contents
    description: Contents of the $$VNAME documents
    containerPath: TEI[$$VINDEX]
    wordPath: .//w
    punctPath: .//text()[not(ancestor::w)]
    
    annotations:
    - name: word
      displayName: Word
      valuePath: .
      sensitivity: sensitive_insensitive

    standoffAnnotations:
    - type: relation
      # relations to index (filter using version name)
      path: /teiCorpus/standOff/linkGrp/link[matches(@target, concat('^#$$VKEY'))]
      # capture target version from relation @target target id
      # (e.g. target="#nl_bla.123 #en_bla.456" -> "en")
      targetVersionPath: "replace(./@target, '^.+ #([a-zA-Z])_.+$', '$1')"
      # relation type
      valuePath: "replace(@type, 'sentence-alignment', 'ab')"

      # Find the relation source and target by parsing the @target attribute
      # Make sure root relation is recognized as such (has no source in BL)
      sourcePath: "replace(./@target, '^#(.+) .+$', '$1')"
      targetPath: "replace(./@target, '^.+ #(.+)$', '$1')"

    inlineTags:
    - path: .//div # book/chapter
    - path: .//ab  # verse
      # capture verse ids so we can use them for sourcePath/targetPath above
      tokenIdPath: "@xml:id"   
    - path: .//s   # sentence
```
