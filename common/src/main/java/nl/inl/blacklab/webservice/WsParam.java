package nl.inl.blacklab.webservice;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The different webservice parameters and their default values.
 * <p>
 * Used by BLS, the Solr component and the proxy.
 * <p>
 * Note that there are still some parameters not covered here; those are parameters
 * used in operations that have not been extracted to the wslib module yet. They should
 * eventually be added.
 */
public enum WsParam {

    // Hits to search
    PATTERN("patt", Type.STRING_OR_JSON_OBJECT),
    PATTERN_LANGUAGE("pattlang"),
    PATTERN_GAP_DATA("pattgapdata"),

    // Collocations parametesr
    COLLOCATE_PATTERN("collpatt"), // collocations: any restrictions on the collocates (if any)
    COLLOCATION_TYPE("colltype"), // type of collocations (proximity or relation)
    RELATION_TYPE("reltype"), // regex filter on relation type (for relation-based collocations)

    // Docs to search
    FILTER("filter"),
    FILTER_LANGUAGE("filterlang"),
    DOC_PID("docpid"),

    // What hits to select
    SAMPLE("sample", Type.FLOAT),
    SAMPLE_NUMBER("samplenum", Type.FLOAT),
    SAMPLE_SEED("sampleseed", Type.INTEGER),
    HIT_FILTER_CRITERIUM("hitfiltercrit"),
    HIT_FILTER_VALUE("hitfilterval"),

    // How to search (debug)
    FORWARD_INDEX_MATCHING_SETTING("fimatch", Type.INTEGER),
    USE_CACHE("usecache", Type.BOOLEAN),

    // How to present results
    SORT_BY("sort"),  // sorting (grouped) hits/docs
    FIRST_RESULT("first", Type.INTEGER), // results window
    NUMBER_OF_RESULTS("number", Type.INTEGER), // results window
    WORDS_AROUND_HIT("wordsaroundhit"), // (DEPRECATED, renamed to "context")
    CONTEXT("context"), // KWIC / concordances / collocations: words around hit or
    WITHIN("within"), // collocations, e.g. to find collocs within sentence
    USE_CONTENT("usecontent"), // create concs from forward index or original content (content store)?
    OMIT_EMPTY_CAPTURES("omitemptycaptures", Type.BOOLEAN),  // omit capture groups of length 0? (false)

    // Doc snippets
    HIT_START("hitstart", Type.INTEGER),
    HIT_END("hitend", Type.INTEGER),
    WORD_START("wordstart", Type.INTEGER),
    WORD_END("wordend", Type.INTEGER),

    EXPLAIN_QUERY_REWRITE("explain", Type.BOOLEAN),

    // on field info page, show (non-sub) values for annotation?
    // also controls which annotations' values are sent back with hits
    LIST_VALUES_FOR_ANNOTATIONS("listvalues"),

    // on document info page, list the values for which metadata fields?
    //also controls which metadata fields are sent back with search hits and document search results
    LIST_VALUES_FOR_METADATA_FIELDS("listmetadatavalues"),

    // include which span attributes with CSV hits?
    LIST_VALUES_FOR_SPAN_ATTR("listspanattributes"),

    // How to process results
    FACETS("facets"), // facet(s) to include, if any
    INCLUDE_CUSTOM_INFO("custom", Type.BOOLEAN), // include custom metadata?
    MAX_HITS_TO_RETRIEVE("maxretrieve", Type.INTEGER),
    MAX_HITS_TO_COUNT("maxcount", Type.INTEGER), // limits to numbers of hits to process
    // (2 params below: use ParamUtil.includeSubcorpusSize() to check so both parameters are tried)
    SUBCORPUS_SIZE("subcorpussize", Type.BOOLEAN), // include subcorpus size?
    INCLUDE_TOKEN_COUNT("includetokencount", Type.BOOLEAN), // (deprecated, now "subcorpussize")

    // Alternative views
    CALCULATE_STATS("calc"), // collocations, or other context-based calculations

    // Grouping
    GROUP_BY("group"),
    VIEW_GROUP("viewgroup"),
    INCLUDE_GROUP_CONTENTS("includegroupcontents", Type.BOOLEAN), // include hits with the group response? (false)

    // for term frequency
    ANNOTATION("annotation"),
    SENSITIVE("sensitive", Type.BOOLEAN),
    TERMS("terms"),

    // How to execute request
    WAIT_FOR_TOTAL_COUNT("waitfortotal", Type.BOOLEAN), // wait until total number of results known?
    TERM("term"), // term (for autocomplete, collocations)
    AUTOCOMPLETE_TYPE("complete"), // for autocomplete on metadata fields, return the original value or an indexed term?

    // CSV options
    CSV_INCLUDE_SUMMARY("csvsummary", Type.BOOLEAN), // include summary of search in the CSV output? [no]
    CSV_DECLARE_SEPARATOR("csvsepline", Type.BOOLEAN), // include separator declaration for Excel? [no]
    CSV_DESCRIPTION("csvdescription"), // description of search operation to include in the CSV file [none]

    // list relations options
    REL_CLASSES("classes"), // what relation classes to report (default all)
    REL_ONLY_SPANS("onlyspans", Type.BOOLEAN),  // only report spans, not other relations [no]
    REL_SEPARATE_SPANS("separatespans", Type.BOOLEAN), // report spans separately from other relations [yes]

    // for listing values (metadata, annotations, relations, attributes)
    LIMIT_VALUES("limitvalues", Type.INTEGER), // truncate lists/maps of values to this length [200]

    // relations querying options
    REL_ADJUST_HITS("adjusthits", Type.BOOLEAN), // adjust hits to cover all tokens involved in relation [no]
    WITH_SPANS("withspans", Type.BOOLEAN), // include all overlapping spans in the response? [no]

    DEBUG("debug", Type.BOOLEAN), // include debug info (cache)

    OPERATION("op"),
    CORPUS_NAME("indexname"),
    FIELD("field"), // (annotated) field to use for operation
    SEARCH_FIELD("searchfield"), // annotated field to search (parallel, if different from field)
    INPUT_FORMAT("inputformat"),
    CONVERTERS("converters", Type.JSON), // extra FileConverts to apply to uploaded file(s)
    SCORER("scorer", Type.STRING_OR_JSON_OBJECT), // scorer to apply (to grouped results for now, maybe also hits in the future)
    SCORER_TYPE("scorertype"), // scorer type id to use (for collocations)
    API("api"),
    JSON_REQUEST("req", Type.JSON); // a BLS request may be passed as a JSON structure


    /** Parameter type */
    public enum Type {
        /** string value (default) */
        STRING,
        /** a JSON object (if it starts with '{'}) or a string.
         *  e.g. used for "patt" */
        STRING_OR_JSON_OBJECT,
        /** boolean value */
        BOOLEAN,
        /** long integer value */
        INTEGER,
        /** double precision float value */
        FLOAT,
        /** JSON structure */
        JSON
    }

    public static Optional<WsParam> fromValue(String str) {
        WsParam[] values = values();
        for (WsParam v: values) {
            if (v.name.equals(str))
                return Optional.of(v);
        }
        return Optional.empty();
    }

    /**
     * Default values for request parameters
     */
    private static final Map<WsParam, Object> defaultValues;

    /** Default value for limitvalues parameter (how many metadata/annotation values to return) */
    public static final int DEF_VAL_LIMIT_VALUES = 200;

    static {
        // Default values for the parameters. Note that if no default is set, the default will be the empty string.
        // (which for booleans will translate to false, etc.)
        defaultValues = new EnumMap<>(WsParam.class);
        defaultValues.put(CONTEXT, "5"); // previously "wordsaroundhit"
        defaultValues.put(USE_CONTENT, "fi");
        defaultValues.put(CSV_DECLARE_SEPARATOR, true);
        defaultValues.put(CSV_INCLUDE_SUMMARY, true);
        defaultValues.put(DEBUG, false);
        defaultValues.put(EXPLAIN_QUERY_REWRITE, false);
        defaultValues.put(FILTER_LANGUAGE, "luceneql");
        defaultValues.put(FIRST_RESULT, 0);
        defaultValues.put(FORWARD_INDEX_MATCHING_SETTING, -1);
        defaultValues.put(HIT_END, 1);
        defaultValues.put(HIT_START, 0);
        defaultValues.put(INCLUDE_GROUP_CONTENTS, false);
        defaultValues.put(SUBCORPUS_SIZE, false);
        defaultValues.put(INCLUDE_CUSTOM_INFO, false);
        defaultValues.put(MAX_HITS_TO_COUNT, 10000000);
        defaultValues.put(MAX_HITS_TO_RETRIEVE, 1000000);
        defaultValues.put(NUMBER_OF_RESULTS, 50);
        defaultValues.put(OMIT_EMPTY_CAPTURES, false);
        defaultValues.put(PATTERN_LANGUAGE, "default");
        defaultValues.put(REL_SEPARATE_SPANS, true);
        defaultValues.put(SENSITIVE, false);
        defaultValues.put(AUTOCOMPLETE_TYPE, "term");
        defaultValues.put(LIMIT_VALUES, DEF_VAL_LIMIT_VALUES);
        defaultValues.put(USE_CACHE, true);
        defaultValues.put(WAIT_FOR_TOTAL_COUNT, false);
        defaultValues.put(WORD_END, -1);
        defaultValues.put(WORD_START, -1);
    }

    /** Canonical parameter name. */
    private final String name;

    /** Parameter type */
    private final Type type;

    WsParam(String name) {
        this(name, Type.STRING);
    }

    WsParam(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public String value() {
        return name;
    }

    public Type type() {
        return type;
    }

    @Override
    public String toString() { return name; }

    public void setDefaultValue(Object value) {
        defaultValues.put(this, value);
    }

    public String getDefaultString() {
        return defaultValues.getOrDefault(this, "").toString();
    }

    public boolean getDefaultBool() {
        if (type != Type.BOOLEAN)
            throw new IllegalStateException("Parameter " + this + " is not of type BOOLEAN");
        return (boolean)defaultValues.getOrDefault(this, false);
    }

    public long getDefaultLong() {
        if (type != Type.INTEGER)
            throw new IllegalStateException("Parameter " + this + " is not of type INTEGER");
        Object v = defaultValues.getOrDefault(this, 0L);
        if (v instanceof Integer)
            return (long)(int)v;
        return (long)v;
    }

    public double getDefaultFloat() {
        if (type != Type.FLOAT)
            throw new IllegalStateException("Parameter " + this + " is not of type FLOAT");
        return (double)defaultValues.getOrDefault(this, 0.0);
    }

}
