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

    public static final String KEY_DOC_FILTER = "filter";
    public static final String KEY_PATTERN = "patt";
    public static final String KEY_ANNOTATION = "annotation";
    public static final String KEY_SENSITIVITY = "sensitivity";
    public static final String KEY_REL_TYPE = "reltype";
    public static final String KEY_COLL_TYPE = "colltype";

    private static final TextPattern ALL_TOKENS = new TextPatternAnyToken(1);

    private final AnnotationSensitivity collocateAnnotation;

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

    /**
     * Instantiate a collocation scorer from its configuration parameters
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
        String relationType = (String)parameters.get(KEY_REL_TYPE);
        CollocationType collocationType = CollocationType.PROXIMITY;
        TextPattern population = ALL_TOKENS;
        if (relationType != null) {
            collocationType = CollocationType.fromStringValue((String)parameters.getOrDefault(KEY_COLL_TYPE,
                    CollocationType.RELATION_TARGETS.toString()));
            TextPattern any = TextPatternDefaultValue.get();
            population = relationPattern(any, any, collocationType, relationType);
            pattern = relationPattern(pattern, any, collocationType, relationType);
        }

        long totalFrequency = type.needsTotalFrequency()
                ? countFrequency(field, population, sensitivity, filter)
                : -1;
        long patternFrequency = countFrequency(field, pattern, sensitivity, filter);
        return type.getCollocationScorer(annotSensitivity, filter, totalFrequency, patternFrequency, collocationType,
                relationType);
    }

    protected long getCollocateFrequency(PropertyValue identity, CollocationType collocationType,
            String relationType) {
        if (!(identity instanceof PropertyValueContextWords words))
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

    /** Count any frequency pattern, using index statistics where the filter permits it. */
    private static long countFrequency(AnnotatedField field, TextPattern pattern, MatchSensitivity sensitivity,
            Query filter) {
        if (pattern instanceof TextPatternCompare comparison &&
                comparison.getOperator() == MatchFilterCompare.Operator.EQUAL &&
                comparison.getLeftClause() instanceof TextPatternValue left &&
                left.getValue() instanceof ConstraintValueSymbol symbol &&
                comparison.getRightClause() instanceof TextPatternValue right &&
                right.getValue() instanceof ConstraintValueString value &&
                !StringUtil.containsRegexCharacters(value.getValue())) {
            // Treat a simple [annot="value"] query just like a collocate term.
            pattern = TextPattern.term(value.getValue(), symbol.getValue(), sensitivity);
        }

        BlackLabIndex index = field.index();
        // Document statistics cannot restrict counts to fragment spans; those need an actual search.
        if (filter == null || !index.isFragmentQuery(filter)) {
            if (ALL_TOKENS.equals(pattern)) {
                if (filter == null)
                    return index.metadata().countPerField().get(field.name()).getTokens();
                CorpusSize.Count fieldSize = index.queryDocuments(filter).subcorpusSize()
                        .getCountsPerField().get(field.name());
                return fieldSize == null ? 0 : fieldSize.getTokens();
            }
            // TextPatternRegex also extends TextPatternTerm, but cannot use term statistics.
            if (pattern instanceof TextPatternTerm term && term.getClass() == TextPatternTerm.class &&
                    term.getAnnotation() != null && term.getSensitivity() != null) {
                Annotation annotation = field.annotation(term.getAnnotation());
                if (annotation == null)
                    throw new InvalidQuery("Annotation doesn't exist: " + term.getAnnotation() +
                            " on field " + field.name());
                return LuceneUtil.getTermFrequency(annotation.sensitivity(term.getSensitivity()),
                        term.getSensitivity().desensitize(term.getValue()), filter, ACCURATE_TERM_FREQ);
            }
        }
        return index.countHits(field, new CompleteQuery(pattern, filter));
    }

    /** Build a relation pattern, mapping the search word and collocate to their physical endpoints. */
    private static TextPattern relationPattern(TextPattern searchPattern, TextPattern collocatePattern,
            CollocationType collocationType, String relationType) {
        if (collocationType == CollocationType.PROXIMITY)
            throw new IllegalArgumentException("Expected a relation collocation type");
        TextPattern source = collocationType == CollocationType.RELATION_SOURCES ? collocatePattern : searchPattern;
        TextPattern target = collocationType == CollocationType.RELATION_SOURCES ? searchPattern : collocatePattern;
        RelationOperatorInfo relOpInfo = new RelationOperatorInfo(relationType,
                SpanQueryRelations.Direction.BOTH_DIRECTIONS,
                null, false, false, false);
        RelationTarget relationTarget = new RelationTarget(relOpInfo, target,
                RelationInfo.SpanMode.SOURCE, null);
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
