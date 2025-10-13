package nl.inl.blacklab.search.textpattern;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * A TextPattern matching words that start with the specified prefix.
 * @deprecated use regex instead
 */
@Deprecated
public class TextPatternPrefix extends TextPatternTerm {
    public TextPatternPrefix(String value) {
        super(value);
    }

    public TextPatternPrefix(String value, String annotation, MatchSensitivity sensitivity) {
        super(value, annotation, sensitivity);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        try {
            context = context.withAnnotationAndSensitivity(annotation, sensitivity);
            return new BLSpanMultiTermQueryWrapper<>(context.queryInfo(),
                    new PrefixQuery(new Term(context.luceneField(),
                            context.optDesensitize(optInsensitive(context, value)))));
        } catch (InvalidQuery e) {
            throw e;
        } catch (StackOverflowError e) {
            // If we pass in a prefix expression matching a lot of words,
            // stack overflow may occur inside Lucene's automaton building
            // code and we may end up here.
            throw new RegexpTooLarge();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternPrefix) {
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
    public TextPatternPrefix withAnnotationAndSensitivity(String annotation, MatchSensitivity sensitivity) {
        if (annotation == null)
            annotation = this.annotation;
        if (sensitivity == null)
            sensitivity = this.sensitivity;
        return new TextPatternPrefix(value, annotation, sensitivity);
    }
}
