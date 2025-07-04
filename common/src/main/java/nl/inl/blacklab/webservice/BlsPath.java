package nl.inl.blacklab.webservice;

public enum BlsPath {
    EMPTY(""),
    AUTOCOMPLETE("autocomplete"),
    CACHE_CLEAR("cache-clear"),
    CACHE_INFO("cache-info"),
    DOCS("docs"),
    FIELDS("fields"),
    HITS("hits"),
    INPUT_FORMATS("input-formats"),
    PARSE_PATTERN("parse-pattern"),
    RELATIONS("relations"),
    SHARED_WITH_ME("shared-with-me"),
    SHARING("sharing"),
    STATUS("status"),
    TERMFREQ("termfreq");

    private String path;

    BlsPath(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
