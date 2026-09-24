package nl.inl.blacklab.search.results.hitresults;

import java.util.List;
import java.util.Map;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.HitGroupScorerType;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.matchfilter.ConstraintValueString;
import nl.inl.blacklab.search.matchfilter.ConstraintValueSymbol;
import nl.inl.blacklab.search.matchfilter.MatchFilterCompare;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.RelationOperatorInfo;
import nl.inl.blacklab.search.textpattern.RelationTarget;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternAnyToken;
import nl.inl.blacklab.search.textpattern.TextPatternCompare;
import nl.inl.blacklab.search.textpattern.TextPatternDefaultValue;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;
import nl.inl.blacklab.search.textpattern.TextPatternValue;
import nl.inl.util.LuceneUtil;
import nl.inl.util.StringUtil;

public abstract class HitGroupCollocationScorer implements HitGroupScorer {

    // Configuration parameter keys
    public static final String KEY_DOC_FILTER = "filter";
    public static final String KEY_PATTERN = "patt";
    public static final String KEY_ANNOTATION = "annotation";
    public static final String KEY_SENSITIVITY = "sensitivity";
    public static final String KEY_REL_TYPE = "reltype";
    public static final String KEY_COLL_TYPE = "colltype";

    private static final TextPattern ANY_TOKEN = new TextPatternAnyToken(1);

    /** If a fragment filter is used, do we upcast it so it only has to look at whole documents? */
    private static final boolean UPCAST_FRAGMENT_FILTERS = false;

    /** From what annotation should collocates come? */
    private final AnnotationSensitivity collocateAnnotation;

    /** The documents filter to apply, or null for none */
    private final Query filter;

    public HitGroupCollocationScorer(AnnotationSensitivity collocateAnnotation, Query filter) {
        this.collocateAnnotation = collocateAnnotation;
        this.filter = filter;
    }

    /**
     * Should getTermFrequency calculate accurate term frequency slowly?
     * If false, and if possible, uses totalTermFrequency which doesn't take deleted documents into account.
     */
    public static final boolean ACCURATE_TERM_FREQ = false;

    /** Instantiate a collocation scorer from its configuration parameters
     *
     * @param field the annotated field to search
     * @param type the type of collocation scorer to create
     * @param parameters the configuration parameters
     * @return a collocation scorer
     */
    public static HitGroupScorer get(AnnotatedField field, HitGroupScorerType type,
            Map<String, Object> parameters) {
        String annotation = parameters.getOrDefault(KEY_ANNOTATION, "").toString();
        if (annotation.isEmpty())
            throw new IllegalArgumentException("Collocation scorer needs annotation");
        MatchSensitivity sensitivity = MatchSensitivity.fromName(
                parameters.getOrDefault(KEY_SENSITIVITY, MatchSensitivity.INSENSITIVE.toString()).toString());
        AnnotationSensitivity annotSensitivity = field.annotation(annotation).sensitivity(sensitivity);

        TextPattern pattern = (TextPattern)parameters.get(KEY_PATTERN);
        if (pattern == null)
            throw new IllegalArgumentException("Collocation scorer needs " + KEY_PATTERN + " parameter");

        Query filter = (Query)parameters.get(KEY_DOC_FILTER);

        if (UPCAST_FRAGMENT_FILTERS && filter != null && field.index().isFragmentQuery(filter)) {
            // We don't support precisely scoring collocations on fragments (yet);
            // convert the filter to a full-document filter.
            filter = DocResults.upcastFragmentsToFullDocuments(filter);
        }

        String relationType = (String)parameters.get(KEY_REL_TYPE);
        CollocationType collocationType = CollocationType.PROXIMITY;
        TextPattern population = ANY_TOKEN; // potential collocates
        if (relationType != null) {
            collocationType = CollocationType.fromStringValue((String)parameters.getOrDefault(KEY_COLL_TYPE,
                            CollocationType.RELATION_TARGETS.toString()));
            TextPattern any = TextPatternDefaultValue.get();
            population = relationPattern(any, any, collocationType, relationType);
            pattern = relationPattern(pattern, any, collocationType, relationType);
        }

        // Find the "total frequency" N, which depends on the collocations type.
        long totalFrequency = type.needsTotalFrequency()
                ? countFrequency(field, population, sensitivity, filter)
                : -1;
        long patternFrequency = countFrequency(field, pattern, sensitivity, filter);
        return type.getCollocationScorer(annotSensitivity, filter, totalFrequency, patternFrequency, collocationType,
                relationType);
    }

    /**
     * Get the frequency of the given collocate.
     * <p>
     * Takes the collocation type into account (and relation type if applicable).
     *
     * @param collocate the collocate to get the frequency for
     * @param collocationType collocation type (proximity or relation sources/targets)
     * @param relationType if relation-based collocation, the relation type to use
     * @return the frequency of the collocate
     */
    protected long getCollocateFrequency(PropertyValue collocate, CollocationType collocationType,
            String relationType) {
        if (!(collocate instanceof PropertyValueContextWords words))
            throw new UnsupportedOperationException("Group identity is not context-based");
        if (words.terms().size() != 1)
            throw new UnsupportedOperationException("Only single-term collocates are supported for now");

        TextPattern pattern = TextPattern.term(words.toString(), collocateAnnotation.annotation().name(),
                collocateAnnotation.sensitivity());
        if (collocationType != CollocationType.PROXIMITY)
            pattern = relationPattern(TextPatternDefaultValue.get(), pattern, collocationType, relationType);
        return countFrequency(collocateAnnotation.annotation().field(), pattern, collocateAnnotation.sensitivity(),
                filter);
    }

    /** Count the number of hits for the given pattern.
     *
     * Will use Lucene term frequency statistics if possible, otherwise executes the query to count hits.
     *
     * @param field the annotated field to search
     * @param pattern the text pattern to count hits for
     * @param sensitivity the match sensitivity
     * @param filter the query filter
     * @return the frequency count
     */
    private static long countFrequency(AnnotatedField field, TextPattern pattern, MatchSensitivity sensitivity,
            Query filter) {
        // Convert TextPatternCompare to TextPatternTerm if possible
        if (pattern instanceof TextPatternCompare comparison &&
                comparison.getOperator() == MatchFilterCompare.Operator.EQUAL &&
                comparison.getLeftClause() instanceof TextPatternValue left &&
                left.getValue() instanceof ConstraintValueSymbol annotation &&
                comparison.getRightClause() instanceof TextPatternValue right &&
                right.getValue() instanceof ConstraintValueString value &&
                !StringUtil.containsRegexCharacters(value.getValue())) {
            pattern = TextPattern.term(value.getValue(), annotation.getValue(), sensitivity);
        }

        BlackLabIndex index = field.index();

        if (/*FIXME below code doesn't work right with fragments!?*/!index.isFragmentQuery(filter)) {

            // Do we simply need to know the number of tokens?
            if (pattern.equals(ANY_TOKEN)) {
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
            if (pattern instanceof TextPatternTerm term &&
                    term.getClass() == TextPatternTerm.class && // NOT TextPatternRegex!
                    term.getAnnotation() != null && term.getSensitivity() != null) {
                // Simple annotation=value; use Lucene term frequency statistics for speed.
                Annotation annotation = field.annotation(term.getAnnotation());
                if (annotation == null)
                    throw new InvalidQuery("Annotation doesn't exist: " + term.getAnnotation() +
                            " on field " + field.name());
                return LuceneUtil.getTermFrequency(annotation.sensitivity(term.getSensitivity()),
                        term.getSensitivity().desensitize(term.getValue()), filter, ACCURATE_TERM_FREQ);
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
        RelationInfo.SpanMode spanMode = collocationType == CollocationType.RELATION_TARGETS ?
                RelationInfo.SpanMode.TARGET : RelationInfo.SpanMode.SOURCE;
        RelationTarget relationTarget = new RelationTarget(relOpInfo, target, spanMode, null);
        return new TextPatternRelationMatch(source, List.of(relationTarget));
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
