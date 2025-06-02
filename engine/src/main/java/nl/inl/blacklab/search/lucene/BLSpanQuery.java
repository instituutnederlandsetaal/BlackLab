package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queries.spans.BLSpanOrQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.queries.spans.SpanOrQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.fimatch.NfaTwoWay;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A required interface for a BlackLab SpanQuery. All our queries must be
 * derived from this so we know they will produce BLSpans (which contains extra
 * methods necessary for functionality such as capture groups, relatoins, etc.
 * <p>
 * Is able to give extra guarantees about the hits this query will produce, such as
 * if every hit is equal in length, if there may be duplicates, etc. This information
 * will help us optimize certain operations, such as sequence queries, in certain cases.
 */
public abstract class BLSpanQuery extends SpanQuery implements SpanGuaranteeGiver {

    public static final int MAX_UNLIMITED = Integer.MAX_VALUE;

    protected SpanGuarantees guarantees;

    public BLSpanQuery(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
    }
    
    /**
     * Rewrite a SpanQuery after rewrite() to a BLSpanQuery equivalent.
     * <p>
     * This is used for BLSpanOrQuery and BLSpanMultiTermQueryWrapper: we let Lucene
     * rewrite these for us, but the result needs to be BL-ified so we know we'll
     * get BLSpans (which contain extra methods for optimization).
     *
     * @param spanQuery the SpanQuery to BL-ify (if it isn't a BLSpanQuery already)
     * @return resulting BLSpanQuery, or the input query if it was one already
     */
    public static BLSpanQuery wrap(QueryInfo queryInfo, SpanQuery spanQuery) {
        if (spanQuery instanceof BLSpanQuery) {
            // Already BL-derived, no wrapper needed.
            return (BLSpanQuery) spanQuery;
        } else if (spanQuery instanceof SpanOrQuery) {
            // Translate to a BLSpanOrQuery, recursively translating the clauses.
            return BLSpanOrQuery.from(queryInfo, (SpanOrQuery) spanQuery);
        } else if (spanQuery instanceof SpanTermQuery) {
            // Translate to a BLSpanTermQuery.
            return BLSpanTermQuery.from(queryInfo, (SpanTermQuery) spanQuery);
        } else {
            // After rewrite, we shouldn't encounter any other non-BLSpanQuery classes.
            throw new UnsupportedOperationException("Cannot BL-ify " + spanQuery.getClass().getSimpleName());
        }
    }

    /**
     * Add two values for maximum number of repetitions, taking "infinite" into
     * account.
     * <p>
     * -1 or Integer.MAX_VALUE means infinite. Adding infinite to any other value
     * produces infinite again (-1 if either value is -1; otherwise,
     * Integer.MAX_VALUE if either value is Integer.MAX_VALUE).
     *
     * @param a first max. repetitions value
     * @param b first max. repetitions value
     * @return sum of the max. repetitions values
     */
    public static int addMaxValues(int a, int b) {
        if (a < 0 || b < 0)
            throw new BlackLabRuntimeException("max values cannot be negative");
        // Is either value infinite?
        if (a == MAX_UNLIMITED || b == MAX_UNLIMITED)
            return MAX_UNLIMITED; // Yes, result is infinite
        // Add regular values
        return a + b;
    }

    protected static <T extends SpanQuery> String clausesToString(String field, List<T> clauses) {
        return clausesToString(field, clauses, "");
    }

    protected static <T extends SpanQuery> String clausesToString(String field, List<T> clauses, String prefix) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (T clause : clauses) {
            if (b.length() > 0)
                b.append(", ");
            b.append(prefix);
            b.append(clause.toString(field));
            n++;
            if (n > 100) {
                b.append("...");
                break;
            }
        }
        return b.toString();
    }

    @SafeVarargs
    protected static <T extends SpanQuery> String clausesToString(String field, T... clauses) {
        return clausesToString(field, Arrays.asList(clauses));
    }

    public static String inf(int max) {
        return max == MAX_UNLIMITED ? "INF" : Integer.toString(max);
    }

    /** Information such as our index, our search logger, etc. */
    protected QueryInfo queryInfo;

    public static boolean isAnyNGram(BLSpanQuery matchTarget) {
        return isAnyNGram(matchTarget, 0);
    }

    public static boolean isAnyNGram(BLSpanQuery matchTarget, int atLeastLength) {
        boolean isAnyNGram = false;
        if (matchTarget instanceof SpanQueryAnyToken any) {
            if (any.getMin() <= atLeastLength && any.getMax() == MAX_UNLIMITED) {
                // No restrictions on target.
                isAnyNGram = true;
            }
        }
        return isAnyNGram;
    }

    @Override
    public abstract String toString(String field);

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract void visit(QueryVisitor visitor);

    /**
     * Called before rewrite() to optimize certain parts of the query before they
     * are rewritten (e.g. match regex terms using NFA instead of OR).
     * <p>
     * For now, only SpanQuerySequence overrides this to make sure certain clause
     * combinations are performed before rewrite().
     *
     * @param reader index reader
     * @return optimized query
     * @throws IOException on error
     */
    public BLSpanQuery optimize(IndexReader reader) throws IOException {
        return this; // by default, don't do any optimization
    }

    @Override
    public abstract BLSpanQuery rewrite(IndexReader reader) throws IOException;

    @Override
    public abstract BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException;

    /**
     * Does this query match the empty sequence?
     * <p>
     * For example, the query [word="cow"]* matches the empty sequence. We need to
     * know this so we can rewrite to the appropriate queries. A query of the form
     * "AB*" would be translated into "A|AB+", so each component of the query
     * actually generates non-empty matches.
     * <p>
     * We default to no because most queries don't match the empty sequence.
     *
     * @return true if this query matches the empty sequence, false otherwise
     */
    public boolean matchesEmptySequence() {
        return false;
    }

    /**
     * Return a version of this clause that cannot match the empty sequence.
     * 
     * @return a version that doesn't match the empty sequence
     */
    public BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        throw new UnsupportedOperationException("noEmpty() must be implemented!");
    }

    /**
     * Check that clauses all search in compatible fields.
     *
     * NOTE: for parallel corpora, checks if clauses search in the same base field
     * WITHOUT the document version, so one query may search both contents__nl and
     * contents__en. The return value will be one of the versioned field names. It
     * doesn't matter which because this value is only ever used to run this check
     * again on a higher level in the query tree.
     *
     * @param clauses clauses to check
     * @return base field name
     * @throws if clauses search in incompatible fields
     */
    public static String checkAllCompatibleFields(Collection<BLSpanQuery> clauses) {
        String baseFieldNameWithVersion = null;
        String baseFieldName = null;
        for (BLSpanQuery clause: clauses) {
            if (clause == null)
                continue; // some operations have optional clauses
            // Get base field name (i.e. without annotation and sensitivity suffixes,
            // so "contents" instead of "contents%lemma@i")
            String baseWithVersion = AnnotatedFieldNameUtil.getBaseName(clause.getField());
            // In parallel corpora, a query may contain clauses that search in different
            // versions of the same field (e.g. "contents__en" and "contents__nl").
            String base = AnnotatedFieldNameUtil.baseFromParallelFieldName(baseWithVersion);
            if (baseFieldName == null) {
                baseFieldName = base;
                baseFieldNameWithVersion = baseWithVersion;
            }
            else if (!baseFieldName.equals(base))
                throw new BlackLabRuntimeException("Mix of incompatible fields in query ("
                        + baseFieldName + " and " + base + ")");
        }
        return baseFieldNameWithVersion;
    }

    /**
     * Return an inverted version of this query.
     *
     * @return the inverted query
     */
    public BLSpanQuery inverted() {
        return new SpanQueryNot(this);
    }

    /**
     * Can this query "internalize" the given neighbouring clause?
     * <p>
     * Internalizing means adding the clause to its children, which is often more
     * efficient because we create longer sequences that match fewer hits and may
     * themselves be further optimized. An example is SpanQueryPosFilter, which can
     * be combined with fixed-length neighbouring clauses (updating the
     * SpanQueryPosFilters' left or right adjustment setting to match) to reduce the
     * number of hits that have to be filtered.
     *
     * @param clause clause we want to internalize
     * @param onTheRight if true, clause is a following clause of this query; if
     *            false, a preceding clause
     * @return true iff clause can be internalized
     */
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        return false;
    }

    /**
     * Internalize the given clause.
     * <p>
     * See canInternalizeNeighbour() for more information.
     *
     * @param clause clause we want to internalize
     * @param onTheRight if true, clause is a right neighbour of this query; if
     *            false, a left neighbour
     * @return new query with clause internalized
     */
    public BLSpanQuery internalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        throw new UnsupportedOperationException(
                "Neighbouring clause " + clause + " can not be internalized by " + this);
    }

    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        throw new UnsupportedOperationException(
                "Cannot create NFA; query should have been rewritten or cannot be matched using forward index");
    }

    public boolean canMakeNfa() {
        return false;
    }

    public NfaTwoWay getNfaTwoWay(ForwardIndexAccessor fiAccessor, int nativeDirection) {
        Nfa nfa = getNfa(fiAccessor, nativeDirection);
        Nfa nfaRev = getNfa(fiAccessor, -nativeDirection);
        return new NfaTwoWay(nfa, nfaRev);
    }

    /**
     * Return an (very rough) indication of how many hits this clause might return.
     * <p>
     * Used to decide what parts of the query to match using the forward index.
     * <p>
     * Based on term frequency, which are combined using simple rules of thumb.
     * <p>
     * Another way to think of this is an indication of how much computation this
     * clause will require when matching using the reverse index.
     *
     * @param reader the index reader
     *
     * @return rough estimation of the number of hits
     */
    public abstract long reverseMatchingCost(IndexReader reader);

    /**
     * Return an (very rough) indication of how expensive finding a match for this
     * query using an NFA would be.
     * <p>
     * Used to decide what parts of the query to match using the forward index.
     *
     * @return rough estimation of the NFA complexity
     */
    public abstract int forwardMatchingCost();

    @Override
    public String getField() {
        // Return only base name of annotated field!
        return AnnotatedFieldNameUtil.getBaseName(getRealField());
    }

    /**
     * Return the full Lucene field name including annotation and sensitivity suffixes.
     *
     * @return the real field name
     */
    public abstract String getRealField();

    /**
     * Set the query info object.
     *
     * @param queryInfo query info object
     */
    public void setQueryInfo(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
    }

    public QueryInfo queryInfo() {
        return queryInfo;
    }

    @Override
    public SpanGuarantees guarantees() {
        return this.guarantees;
    }

}
