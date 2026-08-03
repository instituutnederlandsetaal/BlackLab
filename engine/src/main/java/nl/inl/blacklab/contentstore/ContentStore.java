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

}
