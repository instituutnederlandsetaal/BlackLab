package nl.inl.blacklab.webservice;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import nl.inl.blacklab.Constants;

/**
 * The different webservice parameters and their default values.
 * <p>
 * Used by BLS, the Solr component and the proxy.
 * <p>
 * Note that there are still some parameters not covered here; those are parameters
 * used in operations that have not been extracted to the wslib module yet. They should
 * eventually be added.
 */
public enum WebserviceParameter {

    // Hits to search
    PATTERN("patt"),
    PATTERN_LANGUAGE("pattlang"),
    PATTERN_GAP_DATA("pattgapdata"),

    // Docs to search
    FILTER("filter"),
    FILTER_LANGUAGE("filterlang"),
    DOC_PID("docpid"),

    // What hits to select
    SAMPLE("sample"),
    SAMPLE_NUMBER("samplenum"),
    SAMPLE_SEED("sampleseed"),
    HIT_FILTER_CRITERIUM("hitfiltercrit"),
    HIT_FILTER_VALUE("hitfilterval"),

    // How to search (debug)
    FORWARD_INDEX_MATCHING_SETTING("fimatch"),
    USE_CACHE("usecache"),

    // How to present results
    SORT_BY("sort"),  // sorting (grouped) hits/docs
    FIRST_RESULT("first"), // results window
    NUMBER_OF_RESULTS("number"), // results window
    WORDS_AROUND_HIT("wordsaroundhit"), // (DEPRECATED, renamed to "context")
    CONTEXT("context"), // KWIC / concordances: words around hit or
    CREATE_CONCORDANCES_FROM("usecontent"), // create concs from forward index or original content (content store)?
    OMIT_EMPTY_CAPTURES("omitemptycaptures"),  // omit capture groups of length 0? (false)

    // Doc snippets
    HIT_START("hitstart"),
    HIT_END("hitend"),
    WORD_START("wordstart"),
    WORD_END("wordend"),

    EXPLAIN_QUERY_REWRITE("explain"),

    // on field info page, show (non-sub) values for annotation?
    // also controls which annotations' values are sent back with hits
    LIST_VALUES_FOR_ANNOTATIONS("listvalues"),

    // on document info page, list the values for which metadata fields?
    //also controls which metadata fields are sent back with search hits and document search results
    LIST_VALUES_FOR_METADATA_FIELDS("listmetadatavalues"),

    // How to process results
    INCLUDE_FACETS("facets"), // include facet information?
    SUBCORPUS_SIZE("subcorpussize", "includetokencount"), // include subcorpus size?
    INCLUDE_CUSTOM_INFO("custom"), // include custom metadata?
    MAX_HITS_TO_RETRIEVE("maxretrieve"),
    MAX_HITS_TO_COUNT("maxcount"), // limits to numbers of hits to process

    // Alternative views
    CALCULATE_STATS("calc"), // collocations, or other context-based calculations

    // Grouping
    GROUP_BY("group"),
    VIEW_GROUP("viewgroup"),
    INCLUDE_GROUP_CONTENTS("includegroupcontents"), // include hits with the group response? (false)

    // for term frequency
    PROPERTY("property"), // DEPRECATED, now called "annotation",
    ANNOTATION("annotation"),
    SENSITIVE("sensitive"),
    TERMS("terms"),

    // How to execute request
    WAIT_FOR_TOTAL_COUNT("waitfortotal"), // wait until total number of results known?
    TERM("term"), // term for autocomplete

    // CSV options
    CSV_INCLUDE_SUMMARY("csvsummary"), // include summary of search in the CSV output? [no]
    CSV_DECLARE_SEPARATOR("csvsepline"), // include separator declaration for Excel? [no]
    CSV_DESCRIPTION("csvdescription"), // description of search operation to include in the CSV file [none]

    // list relations options
    REL_CLASSES("classes"),               // what relation classes to report (default all)
    REL_ONLY_SPANS("onlyspans", "only-spans"),  // only report spans, not other relations [no]
    REL_SEPARATE_SPANS("separatespans", "separate-spans"), // report spans separately from other relations [yes]

    // for listing values (metadata, annotations, relations, attributes)
    LIMIT_VALUES("limitvalues"),        // truncate lists/maps of values to this length [200]

    // relations querying options
    REL_ADJUST_HITS("adjusthits", "adjust-hits"),  // adjust hits to cover all tokens involved in relation [no]
    WITH_SPANS("withspans", "with-spans"), // include all overlapping spans in the response? [no]

    DEBUG("debug"), // include debug info (cache)

    OPERATION("op"),
    CORPUS_NAME("indexname"),
    FIELD("field"), // (annotated) field to use for operation
    SEARCH_FIELD("searchfield"), // annotated field to search (parallel, if different from field)
    INPUT_FORMAT("inputformat"),
    API_VERSION("api");

    public static Optional<WebserviceParameter> fromValue(String str) {
        WebserviceParameter[] values = values();
        for (WebserviceParameter v: values) {
            if (v.name.equals(str))
                return Optional.of(v);
            if (v.synonyms.contains(str))
                return Optional.of(v);
        }
        return Optional.empty();
    }

    /**
     * Default values for request parameters
     */
    private static final Map<WebserviceParameter, String> defaultValues;

    /** Default value for limitvalues parameter (how many metadata/annotation values to return) */
    public static final int DEF_VAL_LIMIT_VALUES = 200;

    static {
        // Default values for the parameters. Note that if no default is set, the default will be the empty string.
        // (which for booleans will translate to false, etc.)
        defaultValues = new EnumMap<>(WebserviceParameter.class);
        defaultValues.put(CONTEXT, "5"); // previously "wordsaroundhit"
        defaultValues.put(CREATE_CONCORDANCES_FROM, "fi");
        defaultValues.put(CSV_DECLARE_SEPARATOR, "yes");
        defaultValues.put(CSV_INCLUDE_SUMMARY, "yes");
        defaultValues.put(DEBUG, "no");
        defaultValues.put(EXPLAIN_QUERY_REWRITE, "no");
        defaultValues.put(FILTER_LANGUAGE, "luceneql");
        defaultValues.put(FIRST_RESULT, "0");
        defaultValues.put(FORWARD_INDEX_MATCHING_SETTING, "-1");
        defaultValues.put(HIT_END, "1");
        defaultValues.put(HIT_START, "0");
        defaultValues.put(INCLUDE_GROUP_CONTENTS, "no");
        defaultValues.put(SUBCORPUS_SIZE, "no");
        defaultValues.put(INCLUDE_CUSTOM_INFO, "no");
        defaultValues.put(MAX_HITS_TO_COUNT, "10000000");
        defaultValues.put(MAX_HITS_TO_RETRIEVE, "1000000");
        defaultValues.put(NUMBER_OF_RESULTS, "50");
        defaultValues.put(OMIT_EMPTY_CAPTURES, "no");
        defaultValues.put(PATTERN_LANGUAGE, "default");
        defaultValues.put(PROPERTY, Constants.DEFAULT_MAIN_ANNOT_NAME); // deprecated, use "annotation" now
        defaultValues.put(REL_SEPARATE_SPANS, "yes");
        defaultValues.put(SENSITIVE, "no");
        defaultValues.put(LIMIT_VALUES, "" + DEF_VAL_LIMIT_VALUES);
        defaultValues.put(USE_CACHE, "yes");
        defaultValues.put(WAIT_FOR_TOTAL_COUNT, "no");
        defaultValues.put(WORD_END, "-1");
        defaultValues.put(WORD_START, "-1");
    }

    public static void setDefaultValue(WebserviceParameter par, String value) {
        defaultValues.put(par, value);
    }

    /** Canonical parameter name. */
    private final String name;

    /** Any alternative names for this parameter that will also be recognized. */
    private final List<String> synonyms;

    WebserviceParameter(String name) {
        this(name, new String[0]);
    }

    WebserviceParameter(String name, String... synonyms) {
        this.name = name;
        this.synonyms = Arrays.asList(synonyms);
    }

    public String value() { return name; }

    @Override
    public String toString() { return name; }

    public String getDefaultValue() {
        return defaultValues.getOrDefault(this, "");
    }

}
