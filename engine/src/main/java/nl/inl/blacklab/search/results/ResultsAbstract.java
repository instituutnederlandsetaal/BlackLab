package nl.inl.blacklab.search.results;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * A list of results of some type.
 *
 * All subclasses should be thread-safe.
 */
public abstract class ResultsAbstract implements Results {

    /** Index these results are from. */
    protected final BlackLabIndex index;

    /** Field these results are from. */
    protected final String field;

    protected ResultsAbstract(BlackLabIndex index, String field) {
        this.index = index;
        this.field = field;
    }

    @Override
    public String fieldName() {
        return field;
    }

    @Override
    public BlackLabIndex index() {
        return index;
    }

    public abstract String toString();

    @Override
    public long size() {
        return resultsStats().processedTotal();
    }

}
