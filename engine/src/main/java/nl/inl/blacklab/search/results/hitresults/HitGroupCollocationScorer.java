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
    public static final String KEY_COLL_TYPE = "colltype";

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
        TextPatternDefaultValue defVal = TextPatternDefaultValue.get();
        if (findRelations) {
            // Relation-based collocations. Find how often this relation occurs.
            // (essentially the query _ -relType-> _)
            totalFrequency = determineRelationFrequency(field, defVal, relType, defVal, docFilter);
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
        CollocationType collocationType = CollocationType.PROXIMITY;
        if (findRelations) {
            // See if we're looking for sources or targets
            String strCollType = (String) parameters.getOrDefault(KEY_COLL_TYPE, CollocationType.RELATION_TARGETS.toString());
            collocationType = CollocationType.fromStringValue(strCollType);
        }
        if (findRelations || findAnnot == null) {
            // Not a simple term query. Perform search and count number of results.
            if (findPattern == null)
                throw new IllegalArgumentException("Collocation scorer needs " + KEY_PATTERN + " parameter");
            if (findRelations) {
                // Find how often pattern occurs *in this relation type*.
                if (collocationType == CollocationType.RELATION_SOURCES) {
                    termFrequency = determineRelationFrequency(field, defVal, relType, findPattern, docFilter);
                } else {
                    termFrequency = determineRelationFrequency(field, findPattern, relType, defVal, docFilter);
                }
            } else {
                // Find how often pattern occurs.
                termFrequency = index.countHits(field, new CompleteQuery(findPattern, docFilter));
            }
        } else {
            // Simple term query. Use getTermFrequency.
            AnnotationSensitivity findAnnotSen = field.annotation(findAnnot).sensitivity(sensitivity);
            termFrequency = LuceneUtil.getTermFrequency(findAnnotSen, findValue, docFilter, ACCURATE_TERM_FREQ);
        }
        return type.getCollocationScorer(annotSensitivity, docFilter, totalFrequency, termFrequency, collocationType,
                relType);
    }

    static long determineRelationFrequency(AnnotatedField field,
            TextPattern source, String relType, TextPattern target,
            Query docFilter) {
        RelationOperatorInfo relOpInfo = new RelationOperatorInfo(relType,
                SpanQueryRelations.Direction.BOTH_DIRECTIONS,
                null, false, false, false);
        RelationTarget relationTarget = new RelationTarget(relOpInfo, target,
                RelationInfo.SpanMode.TARGET, null);
        TextPattern relations = new TextPatternRelationMatch(source, List.of(relationTarget));
        return field.index().countHits(field, new CompleteQuery(relations, docFilter));
    }

    protected long getCollocateFrequency(PropertyValue identity, CollocationType collocationType,
            String relationType) {
        if (identity instanceof PropertyValueContextWords pvcw) {
            List<String> terms = pvcw.terms();
            if (terms.size() == 1) {
                // Determine the term's frequency
                String term = pvcw.getSensitivity().desensitize(identity.toString());
                if (collocationType == CollocationType.PROXIMITY)
                    return LuceneUtil.getTermFrequency(collocateAnnotation, term, filter, ACCURATE_TERM_FREQ);
                else {
                    TextPatternDefaultValue defVal = TextPatternDefaultValue.get();
                    if (collocationType == CollocationType.RELATION_SOURCES) {
                        return determineRelationFrequency(collocateAnnotation.annotation().field(),
                            TextPattern.term(term), relationType, defVal, filter);
                    } else {
                        return determineRelationFrequency(collocateAnnotation.annotation().field(),
                                defVal, relationType, TextPattern.term(term), filter);
                    }
                }
            }
            throw new UnsupportedOperationException("Only single-term collocates are supported for now");
        }
        throw new UnsupportedOperationException("Group identity is not context-based");
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
