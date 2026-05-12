package nl.inl.blacklab.search.textpattern;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.RegexpQuery;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.util.StringUtil;

/**
 * A TextPattern matching a regular expression.
 */
public class TextPatternRegex extends TextPatternTerm {

    protected TextPatternRegex(String value, String annotation, MatchSensitivity sensitivity) {
        super(value, annotation, sensitivity);
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) throws InvalidQuery {
        // Rewrite pseudo-annotation to extension function call
        TextPattern rewrittenPseudoAnnot = rewriteIfPseudoAnnotation(context, false);
        if (rewrittenPseudoAnnot != null)
            return rewrittenPseudoAnnot.toQuery(context);

        // See if this is really a regex query or just a term query maskerading as one...
        TextPattern result = TextPatternCompare.rewriteToSimplerTextPattern(annotation, sensitivity, value);
        if (result != null) {
            // Rewritten into a regular term query; translate that instead
            return result.toQuery(context);
        }
        // We're dealing with an actual regex query.
        context = context.withAnnotationAndSensitivity(annotation, sensitivity);
        String valueDesensitized = context.optDesensitize(value);

        // Lucene's regex engine requires double quotes to be escaped, unlike most others.
        // Escape double quotes
        valueDesensitized = StringUtil.escapeQuoteForBcql(valueDesensitized, "\"");

        try {
            Term term = new Term(context.luceneField(), valueDesensitized);
            RegexpQuery regexpQuery = new RegexpQuery(term); //, RegExp.COMPLEMENT); causes issues with NFA matching!
            return new BLSpanMultiTermQueryWrapper<>(context.queryInfo(), regexpQuery);
        } catch (IllegalArgumentException e) {
            throw new InvalidQuery("Invalid query: " + e.getMessage() + " (while parsing regex)");
        } catch (StackOverflowError e) {
            // If we pass in a really large regular expression, like a huge
            // list of words combined with OR, stack overflow occurs inside
            // Lucene's automaton building code and we may end up here.
            throw new RegexpTooLarge();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternRegex) {
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
    public TextPatternRegex withAnnotationAndSensitivity(String annotation, MatchSensitivity sensitivity) {
        if (annotation == null)
            annotation = this.annotation;
        if (sensitivity == null)
            sensitivity = this.sensitivity;
        return new TextPatternRegex(value, annotation, sensitivity);
    }

    @Override
    public <T> T accept(TextPatternVisitor<T> visitor) {
        return visitor.visitRegex(this);
    }
}
