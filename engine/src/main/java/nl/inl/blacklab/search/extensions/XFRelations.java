package nl.inl.blacklab.search.extensions;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureRelationsWithinSpan;
import nl.inl.blacklab.search.lucene.SpanQueryOtherFieldHits;
import nl.inl.blacklab.search.lucene.SpanQueryRelationSpanAdjust;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.lucene.SpansAndFilterFactoryUniqueRelations;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;

/**
 * Extension functions for working with relations (dependency, parallel corpus).
 */
public class XFRelations implements ExtensionFunctionClass {

    public static final String FUNC_REL = "rel";
    public static final String FUNC_RSPAN = "rspan";
    public static final String FUNC_RCAPTURE = "rcapture";

    /** Default name for match info if no explicit capture name is set for a relation operator, and none could be
        derived from the relation type filter expression. */
    public static final String DEFAULT_CAPTURE_NAME = "rel";

    /** Default for relations captured using rcapture() */
    public static final String DEFAULT_RCAP_NAME = "captured_rels";

    /** Default for relations captured using e.g. context=s */
    public static final String DEFAULT_CONTEXT_REL_NAME = "context_rels";

    /**
     * Find relations matching type and target.
     * <p>
     * You can also set spanMode (defaults to "source").
     *
     * @param queryInfo query info
     * @param context query execution context
     * @param args function arguments: relation type, target, spanMode
     * @return relations query
     */
    private static BLSpanQuery rel(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        String relationType = (String) args.get(0);
        BLSpanQuery matchTarget = (BLSpanQuery) args.get(1);
        RelationInfo.SpanMode spanMode = RelationInfo.SpanMode.fromCode((String)args.get(2));
        String captureAs = (String)args.get(3);
        SpanQueryRelations.Direction direction = SpanQueryRelations.Direction.fromCode((String)args.get(4));

        // Make sure relationType has a relation class
        relationType = RelationUtil.optPrependDefaultClass(relationType, context);

        // Auto-determine capture name if none was given
        if (StringUtils.isEmpty(captureAs))
            captureAs = determineCaptureAs(context, relationType, false);

        return createRelationQuery(queryInfo, context, relationType, matchTarget, direction, captureAs, spanMode,
                null);
    }

    public static BLSpanQuery createRelationQuery(QueryInfo queryInfo, QueryExecutionContext context, String relationType,
            BLSpanQuery matchTarget, SpanQueryRelations.Direction direction, String captureAs,
            RelationInfo.SpanMode spanMode, AnnotatedField targetField) {
        // Do we need to match a target, or don't we care?
        AnnotationSensitivity field = context.withRelationAnnotation()
                .luceneFieldRef();
        if (BLSpanQuery.isAnyNGram(matchTarget))
            matchTarget = null;
        if (matchTarget != null) {
            // Ensure relation matches given target, then adjust to the requested span mode
            BLSpanQuery rel = new SpanQueryRelations(queryInfo, field, relationType, null,
                    direction, RelationInfo.SpanMode.TARGET, captureAs, targetField);
            BLSpanQuery relAndTarget = new SpanQueryAnd(List.of(rel, matchTarget));
            ((SpanQueryAnd) relAndTarget).setFilter(SpansAndFilterFactoryUniqueRelations.INSTANCE); // don't match the same relation twice
            if (spanMode != RelationInfo.SpanMode.TARGET) {
                // Not in the target but the source field. Adjust spans accordingly.
                relAndTarget = new SpanQueryRelationSpanAdjust(relAndTarget, spanMode, context.field());
            }
            return relAndTarget;
        } else {
            // No target to match; we can just return the relation matches with the correct span mode right away
            return new SpanQueryRelations(queryInfo, field, relationType, null,
                    direction, spanMode, captureAs, targetField);
        }
    }

    public static String determineCaptureAs(QueryExecutionContext context, String relationType, boolean multiple) {
        // Autodetermine capture name if no explicit name given.
        // Discard relation class if specified, keep Unicode letters from relationType, and add unique number
        String targetVersion = AnnotatedFieldNameUtil.versionFromParallelFieldName(RelationUtil.classFromFullType(relationType));
        String captureName = RelationUtil.typeFromFullType(relationType).replaceAll("[^\\p{L}-]", "");
        if (captureName.isEmpty())
            captureName = DEFAULT_CAPTURE_NAME + (multiple ? "s" : "");
        if (!targetVersion.isEmpty())
            captureName += AnnotatedFieldNameUtil.PARALLEL_VERSION_SEPARATOR + targetVersion;
        return context.ensureUniqueCapture(captureName);
    }

    /**
     * Change span mode of a query with an active relation.
     * <p>
     * That is, change the spans the query produces to the source or target
     * spans of the active relation, or the full relation span, or to a span
     * covering all matched relations.
     *
     * @param queryInfo query info
     * @param context query execution context
     * @param args function arguments: query, spanMode
     * @return span-adjusted query
     */
    private static BLSpanQuery rspan(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        if (args.size() < 2)
            throw new IllegalArgumentException("rspan() requires a query and a span mode as arguments");
        BLSpanQuery relations = (BLSpanQuery) args.get(0);
        RelationInfo.SpanMode mode = RelationInfo.SpanMode.fromCode((String)args.get(1));
        return new SpanQueryRelationSpanAdjust(relations, mode, null);
    }

    /**
     * Get the hits from a specific parallel field/version.
     *
     * This is useful to e.g. highlight one of the versions with the hits from a parallel query.
     *
     * @param queryInfo query info
     * @param context query execution context
     * @param args function arguments: query, field or version name
     * @return query with hits from the specified field
     */
    private static BLSpanQuery rfield(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        if (args.size() < 2)
            throw new IllegalArgumentException("rfield() requires a query and a field or version name as arguments");
        BLSpanQuery relations = (BLSpanQuery) args.get(0);
        String fieldOrVersion = (String)args.get(1);
        AnnotatedField field = queryInfo.index().annotatedFields().getByFieldOrVersionName(fieldOrVersion);
        if (relations.getAnnotatedField() == field) {
            // Nothing to do, just return query unchanged
            return relations;
        }
        return new SpanQueryOtherFieldHits(relations, field);
    }

    /**
     * Perform an AND operation with the additional requirement that clauses match unique relations.
     *
     * @param queryInfo query info
     * @param context query execution context
     * @param args function arguments: clauses
     * @return AND query with unique relations requirement
     */
    private static BLSpanQuery rmatch(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        if (args.isEmpty())
            throw new IllegalArgumentException("rmatch() requires one or more queries as arguments");
        List<BLSpanQuery> tps = args.stream().map(o -> (BLSpanQuery)o).toList();
        return TextPatternRelationMatch.createRelMatchQuery(context, tps);
    }

    /**
     * Capture relations inside a span.
     *
     * Will capture all relations matching the specified type regex as a list
     * under the specified capture name.
     *
     * @param queryInfo
     * @param context
     * @param args function arguments: query, captureAs, relationType
     * @return
     */
    private static BLSpanQuery rcapture(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        if (args.isEmpty())
            throw new IllegalArgumentException("rcapture() requires at least a query");
        BLSpanQuery query = (BLSpanQuery)args.get(0);
        String captureAs = context.ensureUniqueCapture((String)args.get(1));
        String relationType = RelationUtil.optPrependDefaultClass((String) args.get(2), context);
        AnnotationSensitivity field = context.withRelationAnnotation().luceneFieldRef();
        return new SpanQueryCaptureRelationsWithinSpan(queryInfo, field, query, null, captureAs, relationType);
    }

    @Override
    public void register() {
        QueryExtensions.registerRelationsFunction(FUNC_REL, ARGS_SQSSS,
                Arrays.asList(".+", QueryExtensions.VALUE_QUERY_ANY_NGRAM, "source", "", "both"),
                XFRelations::rel);
        QueryExtensions.register("rmatch", ARGS_VAR_Q,
                List.of(QueryExtensions.VALUE_QUERY_ANY_NGRAM), XFRelations::rmatch);
        QueryExtensions.register(FUNC_RSPAN, ARGS_QS, Arrays.asList(null, "full"),
                XFRelations::rspan);
        QueryExtensions.register("rfield", ARGS_QS, NO_DEFAULT_VALUES,
                XFRelations::rfield);
        QueryExtensions.registerRelationsFunction(FUNC_RCAPTURE, ARGS_QSS,
                Arrays.asList(null, DEFAULT_RCAP_NAME, ".+"), XFRelations::rcapture);
    }

}
