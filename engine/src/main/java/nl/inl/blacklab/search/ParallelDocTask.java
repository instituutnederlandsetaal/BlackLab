package nl.inl.blacklab.search;

/** A task to perform on a Lucene document. */
public interface ParallelDocTask extends DocTask{
    default boolean isThreadSafe() { return true; }
}
