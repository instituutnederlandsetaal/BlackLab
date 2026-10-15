package nl.inl.blacklab.search.results.hitresults;

import java.util.List;
import java.util.Map;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.FeatureNotPresentInCorpus;
import nl.inl.blacklab.plugins.HitGroupScorerType;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.RegexStripped;
import nl.inl.blacklab.search.textpattern.RelationOperatorInfo;
import nl.inl.blacklab.search.textpattern.RelationTarget;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternAnyToken;
import nl.inl.blacklab.search.textpattern.TextPatternCompare;
import nl.inl.blacklab.search.textpattern.TextPatternDefaultValue;
import nl.inl.blacklab.search.textpattern.TextPatternFunctionCall;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;
import nl.inl.blacklab.search.textpattern.TextPatternValue;
import nl.inl.util.LuceneUtil;

public abstract class HitGroupCollocationScorer implements HitGroupScorer {

    // Configuration parameter keys
    public static final String KEY_DOC_FILTER = "filter";

    /** Pattern we've found collocations for */
    public static final String KEY_PATTERN = "patt";

    /** Collocate pattern (used to filter collocates, e.g. "only nouns") */
    public static final String KEY_COLL_PATTERN = "collpatt";

    /** Annotation we've grouped the collocates on */
    public static final String KEY_GROUP_BY = "group";

    /** Relation type we've required when finding collocates [relation-based collocations only] */
    public static final String KEY_REL_TYPE = "reltype";

    /** Collocation type: proximity, relsources or reltargets */
    public static final String KEY_COLL_TYPE = "colltype";

    /** Loose score calculation? (i.e. terms in some deleted docs might still be counted) [false] */
    public static final String KEY_LOOSE = "loose";

    /** If a fragment filter is used, do we upcast it so it only has to look at whole documents?
     * (we probably don't want to do this, as it would arguably miscalculate the scores) */
    private static final boolean UPCAST_FRAGMENT_FILTERS = false;

    public static final String STR_NEED_GROUP_BY = "Collocation scorer needs " + KEY_GROUP_BY + " parameter";

    /** From what annotation should collocates come? */
    private final HitProperty hitProperty;

    /** The documents filter to apply, or null for none */
    private final Query filter;

    public HitGroupCollocationScorer(HitProperty hitProperty, Query filter) {
        this.hitProperty = hitProperty;
        this.filter = filter;
    }

    /** Instantiate a collocation scorer from its configuration parameters
     *
     * @param field the annotated field to search
     * @param type the type of collocation scorer to create
     * @param parameters the configuration parameters
     * @return a collocation scorer
     */
    public static HitGroupScorer get(AnnotatedField field, HitGroupScorerType type,
            Map<String, Object> parameters) {
        Object parGroupBy = parameters.get(KEY_GROUP_BY);
        HitProperty hitProperty;
        if (parGroupBy instanceof HitProperty hp)
            hitProperty = hp;
        else
            throw new IllegalArgumentException(STR_NEED_GROUP_BY + (parGroupBy == null ? "" : " of type HitProperty"));

        //AnnotationSensitivity annotSensitivity = field.annotation(annotation).sensitivity(sensitivity);

        TextPattern keywordPattern = (TextPattern)parameters.get(KEY_PATTERN);
        if (keywordPattern == null)
            throw new IllegalArgumentException("Collocation scorer needs " + KEY_PATTERN + " parameter");

        TextPattern collocatePattern = (TextPattern)parameters.get(KEY_COLL_PATTERN);
        if (collocatePattern == null)
            collocatePattern = TextPatternAnyToken.ANY_SINGLE_TOKEN;

        Query filter = (Query)parameters.get(KEY_DOC_FILTER);

        if (UPCAST_FRAGMENT_FILTERS && filter != null && field.index().isFragmentQuery(filter)) {
            // Convert the filter to a full-document filter.
            filter = DocResults.upcastFragmentsToFullDocuments(filter);
        }

        String relationType = (String)parameters.get(KEY_REL_TYPE);
        CollocationType collocationType = CollocationType.PROXIMITY;
        TextPattern population = collocatePattern; // potential collocates
        if (relationType != null) {
            collocationType = CollocationType.fromStringValue((String)parameters.getOrDefault(KEY_COLL_TYPE,
                            CollocationType.RELATION_TARGETS.toString()));
            TextPattern any = TextPatternDefaultValue.get();
            population = relationPattern(any, any, collocationType, relationType);
            keywordPattern = relationPattern(keywordPattern, any, collocationType, relationType);
        }

        // Find the "total frequency" N, which depends on the collocations type.
        boolean loose = Boolean.parseBoolean(parameters.getOrDefault(KEY_LOOSE, "true").toString());
        long totalFrequency = type.needsTotalFrequency()
                ? countFrequency(field, population, /*sensitivity,*/ filter, loose)
                : -1;
        long patternFrequency = countFrequency(field, keywordPattern, /*sensitivity,*/ filter, loose);
        return type.getCollocationScorer(field, hitProperty, filter, totalFrequency, patternFrequency, collocationType,
                relationType, loose);
    }

    /**
     * Get the frequency of the given collocate.
     * <p>
     * Takes the collocation type into account (and relation type if applicable).
     *
     * @param collocate the collocate to get the frequency for
     * @param collocationType collocation type (proximity or relation sources/targets)
     * @param relationType if relation-based collocation, the relation type to use
     * @param loose allow fast-and-loose calculation (terms in some deleted docs may still be counted)?
     * @return the frequency of the collocate
     */
    protected long getCollocateFrequency(AnnotatedField field, PropertyValue collocate, CollocationType collocationType,
            String relationType, boolean loose) {
        boolean isContextBased = collocate instanceof PropertyValueContextWords ||
                (collocate instanceof PropertyValueMultiple && collocate.valuesList().stream()
                        .allMatch(v -> v instanceof PropertyValueContextWords));
        if (!isContextBased)
            throw new UnsupportedOperationException("Group identity is not context-based");
        if (!collocate.valuesList().stream().allMatch(v -> ((PropertyValueContextWords)v).terms().size() == 1))
            throw new UnsupportedOperationException("Only single-term collocates are supported for now");

        CompleteQuery cq = new CompleteQuery(null, filter);
        cq = hitProperty.refine(null, cq, collocate).orElseThrow();
        TextPattern collocatePattern  = cq.pattern();
        if (collocationType != CollocationType.PROXIMITY)
            collocatePattern = relationPattern(TextPatternDefaultValue.get(), collocatePattern, collocationType, relationType);
        return countFrequency(field, collocatePattern, filter, loose);
    }

    /** Count the number of hits for the given pattern.
     *
     * Will use Lucene term frequency statistics if possible, otherwise executes the query to count hits.
     *
     * @param field the annotated field to search
     * @param pattern the text pattern to count hits for
     * @param filter the query filter
     * @param loose allow fast-and-loose calculation (terms in some deleted docs may still be counted)?
     * @return the frequency count
     */
    private static long countFrequency(AnnotatedField field, TextPattern pattern,
            Query filter, boolean loose) {
        BlackLabIndex index = field.index();

        boolean searchingFullDocuments = filter == null || !index.isFragmentQuery(filter);
        if (searchingFullDocuments) {
            // See if we can optimize.

            // Do we simply need to know the number of tokens?
            if (pattern.equals(TextPatternAnyToken.ANY_SINGLE_TOKEN)) {
                if (filter == null) {
                    // Literally all tokens in the field. Get from the metadata.
                    return index.metadata().countPerField().get(field.name()).getTokens();
                } else {
                    // Tokens in subcorpus
                    DocResults docResults = index.queryDocuments(filter);
                    CorpusSize.Count fieldSize = docResults.subcorpusSize()
                            .getCountsPerField().get(field.name());
                    return fieldSize == null ? 0 : fieldSize.getTokens();
                }
            }

            // Is this a simple annotation=value query?
            if (pattern instanceof TextPatternCompare compare && compare.isSimpleAnnotationValueComparison()) {
                RegexStripped rightString = compare.getRightString();
                if (rightString.isLettersAndDigits()) {
                    // Simple non-regex annotation=value; use Lucene term frequency statistics for speed.
                    String annotationName = compare.getLeftSymbol();
                    Annotation annotation = annotationName == null ? field.mainAnnotation() : field.annotation(annotationName);
                    if (annotation == null)
                        throw new FeatureNotPresentInCorpus(
                                "Annotation doesn't exist: " + annotationName +
                                        " on field " + field.name());
                    String term = rightString.regex();
                    MatchSensitivity sensitivity = rightString.sensitivity();
                    if (sensitivity == null)
                        sensitivity = field.index().defaultMatchSensitivity();
                    return LuceneUtil.getTermFrequency(annotation.sensitivity(sensitivity),
                            sensitivity.desensitize(term),
                            filter, !loose);
                }
            }
        }

        // Execute query and count hits.
        return index.countHits(field, new CompleteQuery(pattern, filter));
    }

    /** Build a relation pattern, mapping the search word and collocate to their physical endpoints.
     *
     * @param searchPattern pattern to find collocates for (e.g. find collocates for "cow")
     * @param collocatePattern type of collocates to find (e.g. nouns only)
     * @param collocationType type of collocation (PROXIMITY, RELATION_SOURCES, RELATION_TARGETS)
     * @param relationType if we want relation-based collocations, the relation type to use (e.g. "obj")
     * @return the constructed relation pattern
     */
    private static TextPattern relationPattern(TextPattern searchPattern, TextPattern collocatePattern,
            CollocationType collocationType, String relationType) {
        if (collocationType == CollocationType.PROXIMITY)
            throw new IllegalArgumentException("Expected a relation collocation type");
        TextPattern source = collocationType == CollocationType.RELATION_SOURCES ? collocatePattern : searchPattern;
        TextPattern target = collocationType == CollocationType.RELATION_SOURCES ? searchPattern : collocatePattern;
        RelationOperatorInfo relOpInfo = new RelationOperatorInfo(relationType,
                SpanQueryRelations.Direction.BOTH_DIRECTIONS,
                null, false, false, false);
        RelationTarget relationTarget = new RelationTarget(relOpInfo, target, RelationInfo.SpanMode.SOURCE, null);
        TextPattern relMatch = new TextPatternRelationMatch(source, List.of(relationTarget));
        if (collocationType == CollocationType.RELATION_TARGETS) {
            // Make sure we return the target of the relation as requested.
            TextPattern tpSpanType = TextPatternValue.fromObject("target");
            relMatch = new TextPatternFunctionCall(XFRelations.FUNC_RSPAN, List.of(relMatch, tpSpanType));
        }
        return relMatch;
    }

    /** Type of collocations to find */
    public enum CollocationType {
        /** Proximity-based collocations (i.e. words occurring near specified word) */
        PROXIMITY("proximity"),

        /** Find all relation sources for the specified target.
         *  That is: find words that are the source of the specified relation and have the specified relation target. */
        RELATION_SOURCES("relsources"),

        /** Find all relation targets for the specified source.
         *  That is: find words that are the target of the specified relation and have the specified relation source. */
        RELATION_TARGETS("reltargets");

        private final String stringValue;

        CollocationType(String stringValue) {
            this.stringValue = stringValue;
        }

        public static CollocationType fromStringValue(String v) {
            v = v.toLowerCase();
            for (CollocationType t : CollocationType.values()) {
                if (t.stringValue.equals(v) || v.equals(t.name().toLowerCase()))
                    return t;
            }
            throw new IllegalArgumentException("Unrecognized value for collocation type: " + v);
        }
    }
}
