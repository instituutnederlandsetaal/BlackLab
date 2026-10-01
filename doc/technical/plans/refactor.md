# Refactoring ideas

## Indexing

### AnnotatedFieldWriter immutable?

Nu roepen we constructor aan met mainannotation; daarna maken we andere annotaties aan en moeten we doublechecken dat we mainannotation niet opnieuw doen. Smelly.

### Refer to input format by object reference internally, not by string

We instantieren IndexerImpl met een formatIdentifier. Beter om eerst de identifier te resolven naar een format, en dan de indexer aan te maken met reference naar het format-object. Wrinkle: config-based vs. legacy.

## AnnotatedFieldWriter / AnnotationWriter instancing

Beiden worden in de Doc instances aangemaakt, dus voor elke file opnieuw. Maar file kan meerdere documenten bevatten, dus clear() in startDocument() is noodzakelijk.

Beter om de meeste code onder te brengen in gedeelde immutable classes, en dan twee kleine, mutable classes te maken die wel per-doc geinstantieerd worden?


## Tests

### Indexing tests

Figure out a better way to do many indexing tests like DocIndexerSaxonTest.testSaxonTokenizer(), TestSpanQueryFromFragments, TestMetadataFragments. As much set-up code should be factored into reusable methods, making the actual tests smaller. Small tests are significantly less error-prone and easier to maintain.


## Searching

### TextPattern instantiation index-aware?

Currently we pass string annotation names to the TextPattern constructors. Why not pass Annotation objects instead? This does mean that parsing BCQL depends on the index structure, but is that a problem?

### TextPatternCompare vs TextPatternTerm

Why does the latter still exist?

### ResponseStreamer mixes API logic with producing output

API v4 and v5 have several differences, e.g. `left`/`right` becoming `before`/`after`. Managing these differences should be the responsibility of e.g. `ApiVersion`, not `ResponseStreamer`.

This would obviate the need for mocking DataStream in order to instantiate ResponseStreamer in TestWriteCsv, among other advantages.

### QueryParams and BLSConfig

QueryParams contains the BLSConfig object. It is mostly used to clamp values (window, context and snippet sizes, etc.). Either clamping should be applied before constructing QueryParams, or config should be passed to e.g.the Request* class constructor to do the clamping?

TestResultAutocomplete would not need to mock BLSConfig to test that QueryParams handles certain parameter defaults correctly.
