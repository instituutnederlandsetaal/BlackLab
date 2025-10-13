package nl.inl.blacklab.search.textpattern;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.WildcardQuery;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * A textpattern matching a simple wildcard expression.
 *
 * Internally, the wildcard expression is translated into a regular expression.
 * @deprecated use regex instead
 */
@Deprecated
public class TextPatternWildcard extends TextPatternTerm {

    public TextPatternWildcard(String value) {
        super(value);
    }

    public TextPatternWildcard(String value, String annotation, MatchSensitivity sensitivity) {
        super(value, annotation, sensitivity);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        TextPattern result = rewriteForQuery();
        if (result != this)
            return result.translate(context);
        try {
            context = context.withAnnotationAndSensitivity(annotation, sensitivity);
            return new BLSpanMultiTermQueryWrapper<>(context.queryInfo(), new WildcardQuery(new Term(context.luceneField(),
                    context.optDesensitize(optInsensitive(context, value)))));
        } catch (StackOverflowError e) {
            // If we pass in a really large wildcard expression,
            // stack overflow might occurs inside Lucene's automaton building
            // code and we may end up here.
            throw new RegexpTooLarge();
        }
    }

    /**
     * Rewrite to the "best" TextPattern class for the given wildcard query. Tries
     * to make a TextPatternTerm or TextPatternPrefix because those tend to be
     * faster than TextPatternWildcard in Lucene.
     *
     * @return the TextPattern
     */
    private TextPattern rewriteForQuery() {
        // Hey, maybe it doesn't even contain wildcards?
        if (!value.contains("*") && !value.contains("?")) {
            // Woot!
            return new TextPatternTerm(value);
        }

        // Replace multiple consecutive asterisks with a single one
        String newValue = value.replaceAll("\\*+", "*");

        // Is is "any word"?
        if (newValue.equals("*"))
            return new TextPatternAnyToken(1, 1);

        // Is it a prefix query? ("bla*")
        if (newValue.indexOf('*') == newValue.length() - 1 && newValue.indexOf('?') < 0) {
            // Yes!
            String prefix = newValue.substring(0, newValue.length() - 1);
            return new TextPatternPrefix(prefix);
        }

        if (!newValue.equals(value))
            return new TextPatternWildcard(newValue);

        // Can't simplify, just return ourselves
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternWildcard) {
            return super.equals(obj);
        }
        return false;
    }

    // appease PMD
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public TextPatternWildcard withAnnotationAndSensitivity(String annotation, MatchSensitivity sensitivity) {
        if (annotation == null)
            annotation = this.annotation;
        if (sensitivity == null)
            sensitivity = this.sensitivity;
        return new TextPatternWildcard(value, annotation, sensitivity);
    }
}
