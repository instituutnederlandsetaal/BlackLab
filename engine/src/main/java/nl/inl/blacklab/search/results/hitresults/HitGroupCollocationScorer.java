package nl.inl.blacklab.search.results.hitresults;

import java.util.List;
import java.util.Map;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.plugins.HitGroupScorerType;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.matchfilter.ConstraintValueString;
import nl.inl.blacklab.search.matchfilter.ConstraintValueSymbol;
import nl.inl.blacklab.search.matchfilter.MatchFilterCompare;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.RelationOperatorInfo;
import nl.inl.blacklab.search.textpattern.RelationTarget;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternCompare;
import nl.inl.blacklab.search.textpattern.TextPatternDefaultValue;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;
import nl.inl.blacklab.search.textpattern.TextPatternValue;
import nl.inl.util.LuceneUtil;
import nl.inl.util.StringUtil;

public abstract class HitGroupCollocationScorer implements HitGroupScorer {

    public static final String KEY_DOC_FILTER = "filter";
    public static final String KEY_PATTERN = "patt";
    public static final String KEY_ANNOTATION = "annotation";
    public static final String KEY_SENSITIVITY = "sensitivity";
    public static final String KEY_REL_TYPE = "reltype";

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
        // Total number of tokens in this field
        String annotation = parameters.getOrDefault(KEY_ANNOTATION, "").toString();
        if (annotation.isEmpty())
            throw new IllegalArgumentException("Collocation scorer needs annotation");
        MatchSensitivity sensitivity = MatchSensitivity.fromName(
                parameters.getOrDefault(KEY_SENSITIVITY, MatchSensitivity.INSENSITIVE.toString()).toString());
        AnnotationSensitivity annotSensitivity = field.annotation(annotation).sensitivity(sensitivity);
        BlackLabIndex index = field.index();

        // See if these relation-based collocations
        String relType = (String)parameters.getOrDefault(KEY_REL_TYPE, null);
        boolean findRelations = relType != null;

        // Find the "total frequency" N, which depends on the collocations type.
        long totalFrequency;
        Query docFilter = (Query)parameters.getOrDefault(KEY_DOC_FILTER, null);
        if (findRelations) {
            // Relation-based collocations. Find how often this relation occurs.
            // (essentially the query _ -relType-> _)
            RelationOperatorInfo relOpInfo = new RelationOperatorInfo(relType,
                    SpanQueryRelations.Direction.BOTH_DIRECTIONS,
                    null, false, false, false);
            RelationTarget target = new RelationTarget(relOpInfo, TextPatternDefaultValue.get(),
                    RelationInfo.SpanMode.TARGET, null);
            TextPattern relations = new TextPatternRelationMatch(TextPatternDefaultValue.get(), List.of(target));
            totalFrequency = index.countHits(field, new CompleteQuery(relations, docFilter));
        } else {
            // Proximity-based collocations. Find the total corpus size for this field.
            totalFrequency = index.metadata().countPerField().get(field.name()).getTokens();
        }

        String findAnnot = null, findValue = null;
        TextPattern findPattern = (TextPattern)parameters.getOrDefault(KEY_PATTERN, null);
        if (findPattern != null) {
            if (findPattern instanceof TextPatternCompare tpc && tpc.getOperator() == MatchFilterCompare.Operator.EQUAL &&
                    tpc.getLeftClause() instanceof TextPatternValue tpv1 && tpv1.getValue() instanceof ConstraintValueSymbol symb &&
                    tpc.getRightClause() instanceof TextPatternValue tpv2 && tpv2.getValue() instanceof ConstraintValueString str &&
                    !StringUtil.containsRegexCharacters(str.getValue())) {
                // Simple [annot="value"] query. Extract so we can use LuceneUtil.getTermFrequency() below.
                findAnnot = symb.getValue();
                findValue = str.getValue();
            }
        }
        long termFrequency;
        if (findAnnot == null) {
            // Not a simple term query. Perform search and count number of results.
            if (findPattern == null)
                throw new IllegalArgumentException("Collocation scorer needs " + KEY_PATTERN + " parameter");
            termFrequency = index.countHits(field, new CompleteQuery(findPattern, docFilter));
        } else {
            AnnotationSensitivity findAnnotSen = field.annotation(findAnnot).sensitivity(sensitivity);
            termFrequency = LuceneUtil.getTermFrequency(findAnnotSen, findValue, docFilter, ACCURATE_TERM_FREQ);
        }
        return type.getCollocationScorer(annotSensitivity, docFilter, totalFrequency, termFrequency);
    }

    protected long getCollocateFrequency(PropertyValue identity) {
        if (identity instanceof PropertyValueContextWords pvcw) {
            List<String> terms = pvcw.terms();
            if (terms.size() == 1) {
                // Determine the term's frequency
                String term = pvcw.getSensitivity().desensitize(identity.toString());
                return LuceneUtil.getTermFrequency(collocateAnnotation, term, filter, ACCURATE_TERM_FREQ);
            }
            throw new UnsupportedOperationException("Only single-term collocates are supported for now");
        }
        throw new UnsupportedOperationException("Group identity is not context-based");
    }
}
