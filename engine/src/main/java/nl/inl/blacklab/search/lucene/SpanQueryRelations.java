package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.codec.BlackLabPostingsReader;
import nl.inl.blacklab.forwardindex.RelationInfoSegmentReader;
import nl.inl.blacklab.index.BLFieldTypeLucene;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 *
 * Returns relation spans matching the given type and (optionally) attributes.
 * <p>
 * This version works with the integrated index and the _relation annotation.
 */
public class SpanQueryRelations extends BLSpanQuery {

    public enum Direction {
        // Only return root relations (relations without a source)
        ROOT("root"),

        // Only return relations where target occurs after source
        FORWARD("forward"),

        // Only return relations where target occurs before source
        BACKWARD("backward"),

        // Return any relation
        BOTH_DIRECTIONS("both");

        private final String code;

        Direction(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        @Override
        public String toString() {
            return getCode();
        }

        public static Direction fromCode(String code) {
            for (Direction dir : values()) {
                if (dir.getCode().equals(code)) {
                    return dir;
                }
            }
            throw new IllegalArgumentException("Unknown relation direction: " + code);
        }
    }

    public static SpanGuarantees createGuarantees(SpanGuarantees clause, Direction direction,
            RelationInfo.SpanMode spanMode) {
        if (!clause.hitsStartPointSorted())
            return clause;
        boolean sorted = switch (spanMode) {
            case SOURCE ->
                // All relations are indexed at the source.
                // Root relations don't have a source and are indexed at the target, therefore also sorted.
                    true;
            case FULL_SPAN ->
                // All relations are indexed at the source.
                // Only forward relations will be sorted.
                // Root relations only have target and are indexed there, therefore also sorted.
                    direction == Direction.FORWARD || direction == Direction.ROOT;
            default ->
                // Target may be anywhere before or after source, so we don't know if these will be sorted.
                // Exception: root relations only have target and are indexed there, so they will be sorted.
                    direction == Direction.ROOT;
        };
        return new SpanGuaranteesAdapter(clause) {
            @Override
            public boolean hitsStartPointSorted() {
                return sorted;
            }

            @Override
            public boolean hitsEndPointSorted() {
                return false;
            }

            @Override
            public boolean hitsAllSameLength() {
                return false;
            }

            @Override
            public int hitsLengthMin() {
                return 0;
            }

            @Override
            public int hitsLengthMax() {
                return MAX_UNLIMITED;
            }

            @Override
            public boolean hitsHaveUniqueStart() {
                return false;
            }

            @Override
            public boolean hitsHaveUniqueEnd() {
                return false;
            }
        };
    }

    private BLSpanQuery clause;

    private String relationType;

    private Map<String, String> attributes;

    private AnnotatedField baseField;

    private AnnotationSensitivity relationField;

    private Direction direction;

    private RelationInfo.SpanMode spanMode;

    private String captureAs;

    private AnnotatedField targetField;

    private final RelationsStrategy relationsStrategy;

    public SpanQueryRelations(QueryInfo queryInfo, AnnotationSensitivity relationField, String relationTypeRegex,
            Map<String, String> attributes, Direction direction, RelationInfo.SpanMode spanMode, String captureAs,
            AnnotatedField targetField) {
        super(queryInfo);
        this.relationsStrategy = queryInfo.index().getRelationsStrategy();
        if (relationField == null)
            throw new IllegalArgumentException("relationFieldName must be non-empty");
        if (spanMode == RelationInfo.SpanMode.ALL_SPANS)
            throw new IllegalArgumentException("ALL_SPANS makes no sense for SpanQueryRelations");

        // Do any sanitization (e.g. replace regex dot with "any non-special character" to avoid unintended matches)
        // (should hopefully not be necessary anymore with better relations strategy)
        relationTypeRegex = relationsStrategy.sanitizeTagNameRegex(relationTypeRegex);

        BLSpanQuery clause = relationsStrategy.getRelationsQuery(queryInfo, relationField, relationTypeRegex, attributes);
        //BLSpanQuery clause = getRelationsQuery(queryInfo, relationFieldName, relationTypeRegex, attributes);
        init(relationField, relationTypeRegex, attributes, clause, direction, spanMode, captureAs, targetField);
    }

    public SpanQueryRelations(QueryInfo queryInfo, AnnotationSensitivity relationField, String relationTypeRegex,
            Map<String, String> attributes, BLSpanQuery clause, Direction direction, RelationInfo.SpanMode spanMode,
            String captureAs, AnnotatedField targetField) {
        super(queryInfo);
        this.relationsStrategy = queryInfo.index().getRelationsStrategy();
        init(relationField, relationTypeRegex, attributes, clause, direction, spanMode, captureAs, targetField);
    }

    private void init(AnnotationSensitivity relationField, String relationType, Map<String, String> attributes, BLSpanQuery clause, Direction direction,
            RelationInfo.SpanMode spanMode, String captureAs, AnnotatedField targetField) {
        this.relationField = relationField;
        baseField = relationField.annotation().field();
        this.relationType = relationType;
        this.attributes = new HashMap<>(attributes == null ? Collections.emptyMap() : attributes);
        this.clause = clause;
        this.direction = direction;
        this.spanMode = spanMode;
        this.captureAs = captureAs == null ? "" : captureAs;
        this.guarantees = createGuarantees(clause.guarantees(), direction, spanMode);
        this.targetField = targetField;
    }

    public BLSpanQuery withSpanMode(RelationInfo.SpanMode mode) {
        if (this.spanMode == mode)
            return this;
        return new SpanQueryRelations(queryInfo, relationField, relationType, attributes, clause, direction, mode,
                captureAs, targetField);
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = clause.rewrite(reader);
        if (rewritten == clause)
            return this;
        return new SpanQueryRelations(queryInfo, relationField, relationType, attributes, rewritten, direction,
                spanMode, captureAs, targetField);
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField())) {
            clause.visit(visitor.getSubVisitor(Occur.MUST, this));
        }
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight weight = clause.createWeight(searcher, scoreMode, boost);
        return new Weight(weight, searcher, scoreMode.needsScores() ? getTermStates(weight) : null, boost);
    }

    class Weight extends BLSpanWeight {

        final BLSpanWeight weight;

        public Weight(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryRelations.this, searcher, terms, boost);
            this.weight = weight;
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return weight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            weight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spans = weight.getSpans(context, requiredPostings);
            if (spans == null)
                return null;
            FieldInfo fieldInfo = context.reader().getFieldInfos().fieldInfo(relationField.luceneField());
            boolean primaryIndicator = BLFieldTypeLucene.doesFieldHaveForwardIndex(fieldInfo);
            RelationInfoSegmentReader relInfo = BlackLabPostingsReader.forSegment(context).relationInfo();
            spans = new SpansRelations(baseField, relationType, spans, primaryIndicator,
                    direction, spanMode, captureAs, relInfo, relationsStrategy);
            if (spanMode == RelationInfo.SpanMode.TARGET && targetField != null && !targetField.equals(field))
                spans = new SpansOverrideField(spans, targetField);
            return spans;
        }

    }

    public RelationInfo.SpanMode getSpanMode() {
        return spanMode;
    }

    public boolean isTagQuery() {
        String relationClass = RelationUtil.classFromFullType(relationType);
        return relationClass.equals(RelationUtil.CLASS_INLINE_TAG);
    }

    @Override
    public String toString(String field) {
        String inlineTagsPrefix = RelationUtil.CLASS_INLINE_TAG + RelationUtil.CLASS_TYPE_SEPARATOR;
        String optCaptureAs = captureAs.isEmpty() ? "" : ", cap:" + captureAs;
        if (relationType.startsWith(inlineTagsPrefix)) {
            String type = relationType.substring(inlineTagsPrefix.length());
            String optAttr = attributes != null && !attributes.isEmpty() ? ", " + attributes : "";
            return "TAGS(" + type + optAttr + optCaptureAs + ")";
        } else {
            // relations query
            String optDirection = direction != Direction.BOTH_DIRECTIONS ? ", dir:" + direction : "";
            return "REL(" + relationType + ", " + spanMode + optDirection + optCaptureAs + ")";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        SpanQueryRelations that = (SpanQueryRelations) o;
        return Objects.equals(clause, that.clause) && Objects.equals(relationType, that.relationType)
                && Objects.equals(attributes, that.attributes) && Objects.equals(baseField,
                that.baseField) && Objects.equals(relationField, that.relationField)
                && direction == that.direction && spanMode == that.spanMode && Objects.equals(captureAs,
                that.captureAs) && Objects.equals(targetField, that.targetField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clause, relationType, attributes, baseField, relationField, direction, spanMode,
                captureAs, targetField);
    }

    /**
     * Returns the name of the search field. In the case of a annotated field, the
     * clauses may actually query different annotations of the same annotated field
     * (e.g. "description" and "description__pos"). That's why only the prefix is
     * returned.
     *
     * @return name of the search field
     */
    @Override
    public String getField() {
        if (spanMode == RelationInfo.SpanMode.TARGET && targetField != null)
            return targetField.name();
        return baseField.name();
    }

    @Override
    public String getRealField() {
        if (spanMode == RelationInfo.SpanMode.TARGET && targetField != null)
            return targetField.name(); // not strictly correct (should be a full Lucene field name with annotation and sensitivity)
        return relationField.luceneField();
    }

    public String getElementNameRegex() {
        return RelationUtil.typeFromFullType(relationType);
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clause.reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clause.forwardMatchingCost();
    }

    @Override
    public void setQueryInfo(QueryInfo queryInfo) {
        super.setQueryInfo(queryInfo);
        clause.setQueryInfo(queryInfo);
    }
}
