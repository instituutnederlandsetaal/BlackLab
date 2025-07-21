/**
 * Our own extensions to Lucene.
 *
 * This package mostly contains the {@link org.apache.lucene.queries.spans.SpanQuery} classes, with their associated
 * {@link org.apache.lucene.queries.spans.SpanWeight} and {@link org.apache.lucene.queries.spans.Spans} classes.
 *
 * These are added to Lucene's built-in {@link org.apache.lucene.queries.spans.SpanQuery} classes to be able to resolve
 * certain Corpus Query Language constructs efficiently.
 *
 * Other notable classes in this package:
 *
 * - {@link nl.inl.blacklab.search.lucene.HitQueryContext} contains capture group information
 * - {@link nl.inl.blacklab.search.lucene.DocFieldLengthGetter} allows us to get the document's length in tokens, needed for some classes
 */
package nl.inl.blacklab.search.lucene;
