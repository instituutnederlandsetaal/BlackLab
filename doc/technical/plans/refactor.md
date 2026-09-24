# Refactoring ideas

## AnnotatedFieldWriter immutable?

Nu roepen we constructor aan met mainannotation; daarna maken we andere annotaties aan en moeten we doublechecken dat we mainannotation niet opnieuw doen. Smelly.

## Refer to input format by object reference internally, not by string

We instantieren IndexerImpl met een formatIdentifier. Beter om eerst de identifier te resolven naar een format, en dan de indexer aan te maken met reference naar het format-object. Wrinkle: config-based vs. legacy.

## Tests

Figure out a better way to do many small indexing tests like DocIndexerSaxonTest.testSaxonTokenizer(), TestSpanQueryFromFragments, TestMetadataFragments. Small tests are significantly less error-prone and easier to maintain.

## AnnotatedFieldWriter / AnnotationWriter instancing

Beiden worden in de Doc instances aangemaakt, dus voor elke file opnieuw. Maar file kan meerdere documenten bevatten, dus clear() in startDocument() is noodzakelijk.

Beter om de meeste code onder te brengen in gedeelde immutable classes, en dan twee kleine, mutable classes te maken die wel per-doc geinstantieerd worden?

## TextPattern instantiation index-aware?

Currently we pass string annotation names to the TextPattern constructors. Why not pass Annotation objects instead? This does mean that parsing BCQL depends on the index structure, but is that a problem?

## TextPatternCompare vs TextPatternTerm

Why does the latter still exist?

