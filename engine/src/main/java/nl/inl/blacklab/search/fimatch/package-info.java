/**
 * This package deals with forward-index matching. Lucene is a reverse index library, like
 * you find at the back of a book. However, in certain cases it could be more efficient to
 * use a forward index to match part of a query. This would be true if the reverse index
 * approach would be very slow (e.g. because you would need to fetch a huge list of matches from the
 * reverse index and check each against the partial matches you already have), but it would be
 * relatively quick to check the forward index for each of the partial matches you already have.
 * <p>
 * In practice, it is very difficult to predict when forward-index matching is beneficial.
 * We also use it for "global constraints" though, e.g. after matching a query, checking that
 * two captures have captured the same word (<code>A:[] "and" B:[] :: A.word = B.word</code>).
 */
package nl.inl.blacklab.search.fimatch;
