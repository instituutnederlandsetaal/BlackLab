package nl.inl.blacklab.webservice;

public enum BlsPath {
    EMPTY(""),
    AUTOCOMPLETE("autocomplete"),
    CACHE_CLEAR("cache-clear"),
    CACHE_INFO("cache-info"),
    COLLOCATIONS("collocations"),
    DOCS("docs"),
    FIELDS("fields"),
    HITS("hits"),
    INPUT_FORMATS("input-formats"),
    PARSE_PATTERN("parse-pattern"),
    PLUGINS("plugins"),
    RELATIONS("relations"),
    SCHEMA("schema"),
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
