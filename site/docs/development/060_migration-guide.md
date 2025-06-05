# Migration guide

## Migrating from BlackLab 1.7 to 2.0

BlackLab Server 2.0 contains one significant change to the API: metadata values are now always reported as lists, even if there is only one value. Because it is now possible to index multiple values for a single metadata field (for example, to associate two authors or two images with one document), this change was required. 

Where BlackLab 2.0 does differ significantly is in its Java API and configuration files. If you use the Java API, for example because you've written your own DocIndexer class, this page helps you migrate it to 2.0. 

### Terminology

- "Searcher" -> "BlackLab index"
- "complex field" -> "annotated field"
- "property on a complex field" -> "annotation on an annotated field"
- "an indexed alternative for a property" (e.g. case- and diacritics-insensitive) -> "an indexed sensitivity for an annotation"

So, for example, an annotated field "contents" might have annotations "word", "lemma" and "pos" (part of speech), and the "word" annotation might have two sensitivities indexed: (case- and diacritics-) sensitive, and (case- and diacritics-) insensitive.

### Migrating the configuration file(s)

Usually you will use either a file `blacklab-server.yaml` (for BlackLab Serer), or `blacklab.yaml` (for e.g. IndexTool, QueryTool or other BlackLab applications). (JSON works too if you prefer)

A new, cleaner format was added in BlackLab 2.0. The old format still works, but it is a good idea to convert to the new format as the old format will eventually be removed.

For more information about the config file format, see [Configuration files](/server/configuration.md).

### Migrating DocIndexers

If you have a custom implementation of DocIndexer for your own input format, please ensure that it has a default constructor. If instead if has a constructor that takes an `Indexer`, change `Indexer` to `DocWriter`. 

### Method naming

For many classes, methods were renamed from getSomeThing() to simply someThing(). While this may not be the convention in Java, it makes for less noisy, more natural-sounding code, especially when chaining methods. It also saves on typing. For example, compare these two examples:

```java
String luceneField = index
    .getAnnotatedField("contents")
    .getAnnotation("word")
    .getSensitivity(MatchSensitivity.SENSITIVE)
    .getLuceneField();

String luceneField = index
    .annotatedField("contents")
    .annotation("word")
    .sensitivity(MatchSensitivity.SENSITIVE)
    .luceneField();
```

### Important renamed packages and classes

General:
- Searcher -> BlackLabIndex

Classes used while indexing:
- ComplexField -> AnnotatedFieldWriter
- ComplexFieldProperty -> AnnotationWriter
- ComplexFieldUtil -> AnnotatedFieldNameUtil

Index structure:
- IndexStructure -> IndexMetadata
- ComplexFieldDesc -> AnnotatedField
- PropertyDesc -> Annotation

Packages:
- nl.inl.blacklab.search.indexstructure -> .search.indexmetadata
- nl.inl.blacklab.externalstorage -> .contentstore

### Migrating BlackLab programs

Methods:
- instead of BlackLabIndex.open(), use BlackLab.open()
