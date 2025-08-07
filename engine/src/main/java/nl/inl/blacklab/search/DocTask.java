package nl.inl.blacklab.search;

/** A task to perform on a Lucene document. */
public interface DocTask {
    void perform(BlackLabIndex index, int id);
}
