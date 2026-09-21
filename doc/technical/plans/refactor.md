# Refactoring ideas

## Indexing

### Allow in-memory indexing

`TestSpanQueryFromFragments` creates an index in a `ByteBuffersDirectory`, but this can't be done through regular BlackLab code.

### Refer to input format by object reference internally, not by string

We instantiate `IndexerImpl` with a `formatIdentifier`. Better to resolve the identifier to a format object first, then create the indexer with a reference to that object. Wrinkle: config-based vs. legacy formats.

## AnnotatedFieldWriter / AnnotationWriter instancing

Both are created in the `Doc` instances, so again for each file. But a file can contain multiple documents, so calling `clear()` in `startDocument()` is required.

Better to put most of the code in shared immutable classes, and have two smaller, mutable classes that are still instantiated per-doc?

### AnnotatedFieldWriter / AnnotationWriter construction

Now we call a the `AnnotatedFieldWriter` constructor with `mainAnnotation`, then create and add other annotations and have to double check that we don't re-add the main annotation. Smelly. Better to pass all the annotations to the constructor right away?


## Tests

### Indexing tests

Figure out a better way to do many indexing tests like `DocIndexerSaxonTest.testSaxonTokenizer()`, `TestSpanQueryFromFragments`, `TestMetadataFragments`.

As much set-up code as possible should be factored into reusable methods, making the actual tests smaller. Small tests are significantly less error-prone and easier to maintain.


## Searching

### TextPattern instantiation index-aware?

Currently we pass string annotation names to the TextPattern constructors. Why not pass Annotation objects instead? This does mean that parsing BCQL depends on the index structure, but is that a problem?

### TextPatternCompare vs TextPatternTerm

Why does the latter still exist?

## BLS

### QueryParams and BLSConfig

QueryParams contains the BLSConfig object. It is mostly used to clamp values (window, context and snippet sizes, etc.). Either clamping should be applied before constructing QueryParams, or config should be passed to e.g.the Request* class constructor to do the clamping?

TestResultAutocomplete would not need to mock BLSConfig to test that QueryParams handles certain parameter defaults correctly.

### Strong coupling between classes

Much of the BlackLab Server code relies on the availability of various objects: `BlsMain`, `BLSConfig`, `SearchManager`, `IndexManager`, `Index`.

For actual querying: `QueryInfo` which links to `BlackLabIndex`, `AnnotatedField` and `SearchCache`.

All this makes testing classes separately challenging, requiring mocks or special code for tests (i.e. `index == null ? null : index.mainAnnotation()`).

To make them more testable, classes should only depend on directly injected objects with small, specific interfaces.


### ResponseStreamer mixes API logic with producing output

API v4 and v5 have several differences, e.g. `left`/`right` becoming `before`/`after`. Managing these differences should be the responsibility of e.g. `ApiVersion`, not `ResponseStreamer`.

This would obviate the need for mocking DataStream in order to instantiate ResponseStreamer in TestWriteCsv, among other advantages.
