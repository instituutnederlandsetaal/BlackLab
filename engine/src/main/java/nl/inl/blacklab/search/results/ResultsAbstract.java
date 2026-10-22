package nl.inl.blacklab.search.results;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * A list of results of some type.
 *
 * All subclasses should be thread-safe.
 */
public abstract class ResultsAbstract implements Results {

    /** Information about the original query: index, field, max settings, max stats. */
    protected final IndexAndField indexAndField;

    protected ResultsAbstract(IndexAndField indexAndField) {
        this.indexAndField = indexAndField;
    }

    /**
     * Get index and field information.
     * @return index and field information
     */
    @Override
    public IndexAndField indexAndField() {
        return indexAndField;
    }

    @Override
    public AnnotatedField field() {
        return indexAndField.annotatedField();
    }

    @Override
    public BlackLabIndex index() {
        return indexAndField.index();
    }

    public abstract String toString();

    @Override
    public long size() {
        return resultsStats().processedTotal();
    }

}
