package nl.inl.blacklab.contentstore;

public interface ContentStore {

    /**
     * Retrieve substrings from a document.
     *
     * @param id    document id
     * @param start start of the substring
     * @param end   end of the substring
     * @return the substrings
     */
    String[] retrieveParts(int id, int[] start, int[] end);

    /**
     * Close the content store
     */
    void close();

    /** Initialize the content store. May be run in a background thread. */
    void initialize();

}
