package nl.inl.blacklab.search.matchfilter;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

/**
 * A global constraint (or "match filter") for our matches.
 *
 * A global constraint is specified in Corpus Query Language using
 * the :: operator, e.g. <code>a:[] "and" b:[] :: a.word = b.word</code>
 * to find things like "more and more", "less and less", etc.
 */
public abstract class MatchFilter implements TextPatternStruct {

    // Node types
    public static final String NT_AND = "mf-and";
    public static final String NT_COMPARE = "mf-compare";
    public static final String NT_EQUALS = "mf-equals";
    public static final String NT_CALLFUNC = "mf-callfunc";
    public static final String NT_IMPLICATION = "mf-implication";
    public static final String NT_NOT = "mf-not";
    public static final String NT_OR = "mf-or";
    public static final String NT_STRING = "mf-string";
    public static final String NT_TOKEN_ANNOTATION_EQUAL = "mf-token-annotation-equal";
    public static final String NT_TOKEN_ANNOTATION = "mf-token-annotation";
    public static final String NT_TOKEN_ANNOTATION_STRING = "mf-token-annotation-string";

    @Override
    public abstract String toString();

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    /**
     * Pass the hit query context object to this constraint, so we can look up the
     * group numbers we need.
     * 
     * @param context hit query context object
     */
    public abstract void setHitQueryContext(HitQueryContext context);

    /**
     * Evaluate the constraint at the current match position.
     * 
     * @param fiDoc document we're matching in right now
     * @param matchInfo current captured groups state
     * @return value of the constraint at this position
     */
    public abstract ConstraintValue evaluate(ForwardIndexDocument fiDoc, MatchInfo[] matchInfo);

    /**
     * Let token annotation nodes look up the index of their annotation
     * 
     * @param fiAccessor forward index accessor
     */
    public abstract void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor);

    /**
     * Try to rewrite this filter into a more efficient version
     * 
     * @return the rewritten filter (might be the same object if no rewrites were
     *         done)
     */
    public abstract MatchFilter rewrite();

    /**
     * Rewrite a two-clause match filter.
     *
     * Applies the rewriter to both clauses. If both remain the same,
     * we just return the original object (parent). Otherwise, we create a new
     * object with the rewritten clauses.
     *
     * @param parent the original object (this) to return if no changes are made
     * @param child1 first clause
     * @param child2 second clause
     * @param rewriter function to apply to both clauses
     * @param creater function to create new object from the rewritten clauses
     * @return the rewritten object, or parent if no changes were made
     * @param <T> the type of the match filter
     */
    public static <T> T twoClauseRewrite(T parent, T child1, T child2, Function<T, T> rewriter, BiFunction<T, T, T> creater) {
        T rewritten1 = rewriter.apply(child1);
        T rewritten2 = rewriter.apply(child2);
        if (rewritten1 == child1 && rewritten2 == child2)
            return parent; // nothing changed; return the original object
        // One or both clauses were rewritten; create a new object
        return creater.apply(rewritten1, rewritten2);
    }

    /**
     * Create a copy of this MatchFilter with the given field.
     *
     * @param field the field to use
     * @return a copy of this MatchFilter with the given field
     */
    public MatchFilter withField(AnnotatedField field) {
        return this;
    }

    /**
     * Create a copy of this MatchFilter for the given leaf reader context.
     *
     * This will look up term ids for this segment.
     *
     * @param context leaf reader context to create a copy for
     * @return a copy of this MatchFilter for the given context
     */
    public MatchFilter forLeafReaderContext(LeafReaderContext context) {
        return this;
    }
}
