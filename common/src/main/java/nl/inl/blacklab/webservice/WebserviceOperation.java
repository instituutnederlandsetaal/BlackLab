package nl.inl.blacklab.webservice;

import java.util.Optional;

/**
 * All of the operations a BlackLab webservice supports.
 * <p>
 * Also records the BlackLab Server URL path and HTTP method.
 * The path doesn't apply to Solr, because Solr always uses the same path
 * and gets all info via parameters.
 */
public enum WebserviceOperation {
    SERVER_INFO("server-info", BlsPath.EMPTY),
    CORPUS_INFO("corpus-info", BlsPath.EMPTY),
    CORPUS_STATUS("corpus-status", BlsPath.STATUS),
    CORPUS_SHARING("corpus-sharing", BlsPath.SHARING),
    FIELD_INFO("field-info", BlsPath.FIELDS),

    HITS("hits", BlsPath.HITS),
    HITS_CSV("hits-csv", BlsPath.HITS), // not ideal that this is a separate operation, but output is quite different
    HITS_GROUPED("hits-grouped", BlsPath.HITS),  // should -grouped be separate? (triggered by group/viewgroup params)
    PARSE_PATTERN("parse-pattern", BlsPath.PARSE_PATTERN),

    RELATIONS("relations", BlsPath.RELATIONS),

    DOCS("docs", BlsPath.DOCS),
    DOCS_CSV("docs-csv", BlsPath.DOCS), // not ideal that this is a separate operation, but output is quite different
    DOCS_GROUPED("docs-grouped", BlsPath.DOCS),  // should -grouped be separate? (triggered by group/viewgroup params)

    DOC_CONTENTS("doc-contents", BlsPath.DOCS),
    DOC_INFO("doc-info", BlsPath.DOCS),
    DOC_SNIPPET("doc-snippet", BlsPath.DOCS),

    TERM_FREQUENCIES("termfreq", BlsPath.TERMFREQ),
    AUTOCOMPLETE("autocomplete", BlsPath.AUTOCOMPLETE),

    LIST_INPUT_FORMATS("list-input-formats", BlsPath.INPUT_FORMATS),
    INPUT_FORMAT_INFO("input-format-info", BlsPath.INPUT_FORMATS),
    INPUT_FORMAT_XSLT("input-format-xslt", BlsPath.INPUT_FORMATS),
    WRITE_INPUT_FORMAT("write-input-format", HttpMethod.POST, BlsPath.INPUT_FORMATS),
    DELETE_INPUT_FORMAT("delete-input-format", HttpMethod.DELETE, BlsPath.INPUT_FORMATS),

    ADD_TO_CORPUS("add-to-corpus", HttpMethod.POST, BlsPath.EMPTY),
    DELETE_DOCUMENT("delete-document", HttpMethod.DELETE, BlsPath.DOCS),

    CACHE_INFO("cache-info", BlsPath.CACHE_INFO),
    CACHE_CLEAR("cache-clear", HttpMethod.POST, BlsPath.CACHE_CLEAR),

    CREATE_CORPUS("create-corpus", HttpMethod.POST, BlsPath.EMPTY),
    DELETE_CORPUS("delete-corpus", HttpMethod.DELETE, BlsPath.EMPTY),

    STATIC_RESPONSE("static-response", BlsPath.EMPTY), // internal, used by BLS
    NONE("none", BlsPath.EMPTY);

    private final String value;

    private final BlsPath blsPath;

    private final HttpMethod httpMethod;

    public static Optional<WebserviceOperation> fromValue(String name) {
        for (WebserviceOperation t: values()) {
            if (t.value.equals(name))
                return Optional.of(t);
        }
        return Optional.empty();
    }

    WebserviceOperation(String value, BlsPath blsPath) {
        this(value, HttpMethod.GET, blsPath);
    }

    WebserviceOperation(String value, HttpMethod httpMethod, BlsPath blsPath) {
        this.value = value;
        this.blsPath = blsPath;
        this.httpMethod = httpMethod;
    }

    public String value() { return value; }

    @Override
    public String toString() { return value(); }

    public boolean isDocsOperation() {
        return value().startsWith("doc");
    }

    public String path() {
        return blsPath.path();
    }

    @SuppressWarnings("unused")
    public HttpMethod getHttpMethod() {
        return httpMethod;
    }
}
