package nl.inl.blacklab.search.extensions;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PEnum;
import nl.inl.blacklab.plugins.param.PList;
import nl.inl.blacklab.plugins.param.PMatchInfo;
import nl.inl.blacklab.plugins.param.PMultiple;
import nl.inl.blacklab.plugins.param.PQuery;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureRelationsWithinSpan;
import nl.inl.blacklab.search.lucene.SpanQueryOtherFieldHits;
import nl.inl.blacklab.search.lucene.SpanQueryRelationSpanAdjust;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.lucene.SpansAndFilterFactoryUniqueRelations;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;

/**
 * Extension functions for working with relations (dependency, parallel corpus).
 */
public class XFRelations implements ExtensionFunctionClass {

    public static final String FUNC_REL = "rel";
    public static final String FUNC_RSPAN = "rspan";

    /** Default for relations captured using e.g. context=s */
    public static final String DEFAULT_CONTEXT_REL_NAME = "context_rels";

    /** Regex for matching all relations (default for rcapture) */
    public static final String REGEX_RELATIONS_ALL_CLASSES_ALL_TYPE = RelationUtil.fullTypeRegex(".+", ".+");

    /** Default name for match info if no explicit capture name is set for a relation operator, and none could be
     derived from the relation type filter expression. */
    private static final String DEFAULT_CAPTURE_NAME = "rel";

    /** Default for relations captured using rcapture() */
    private static final String DEFAULT_RCAP_NAME = "captured_rels";

    public static BLSpanQuery createRelationQuery(QueryInfo queryInfo, QueryExecutionContext context, String relationType,
            BLSpanQuery matchTarget, SpanQueryRelations.Direction direction, String captureAs, boolean implicitCapture,
            RelationInfo.SpanMode spanMode, AnnotatedField targetField) {
        // Do we need to match a target, or don't we care?
        AnnotationSensitivity field = context.withRelationAnnotation()
                .luceneFieldRef();
        if (BLSpanQuery.isAnyNGram(matchTarget))
            matchTarget = null;
        if (matchTarget != null) {
            // Ensure relation matches given target, then adjust to the requested span mode
            BLSpanQuery rel = new SpanQueryRelations(queryInfo, field, relationType, null,
                    direction, RelationInfo.SpanMode.TARGET, captureAs, implicitCapture, targetField);
            BLSpanQuery relAndTarget = new SpanQueryAnd(List.of(rel, matchTarget));
            ((SpanQueryAnd) relAndTarget).setFilter(SpansAndFilterFactoryUniqueRelations.INSTANCE); // don't match the same relation twice
            if (spanMode != RelationInfo.SpanMode.TARGET) {
                // Not in the target but the source field. Adjust spans accordingly.
                relAndTarget = new SpanQueryRelationSpanAdjust(relAndTarget, context.field(), spanMode, null);
            }
            return relAndTarget;
        } else {
            // No target to match; we can just return the relation matches with the correct span mode right away
            return new SpanQueryRelations(queryInfo, field, relationType, null,
                    direction, spanMode, captureAs, implicitCapture, targetField);
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

    @Override
    public void register() {
        // rel: Find relations matching type and target.
        QueryExtensions.registerRelationsFunction(FUNC_REL,
                "Find relations matching type and target",
                List.of(PString.any("relType"), PQuery.required("query"),
                        PEnum.of("spanMode", RelationInfo.SpanMode.class),
                        PString.identifier("captureAs"),
                        PEnum.of("direction", SpanQueryRelations.Direction.class)),
                Arrays.asList(".+", QueryFunction.VALUE_QUERY_ANY_NGRAM, "source", "", "both"),
                (queryInfo, context, args) -> {
                    String relationType = (String) args.get(0);
                    BLSpanQuery matchTarget = (BLSpanQuery) args.get(1);
                    RelationInfo.SpanMode spanMode = RelationInfo.SpanMode.fromCode((String)args.get(2));
                    String captureAs = (String)args.get(3);
                    SpanQueryRelations.Direction direction = SpanQueryRelations.Direction.fromCode((String)args.get(4));

                    // Make sure relationType has a relation class
                    relationType = RelationUtil.optPrependDefaultClass(relationType, context);

                    // Auto-determine capture name if none was given
                    boolean implicitCapture = false;
                    if (StringUtils.isEmpty(captureAs)) {
                        captureAs = determineCaptureAs(context, relationType, false);
                        implicitCapture = true;
                    }

                    return createRelationQuery(queryInfo, context, relationType, matchTarget, direction, captureAs, implicitCapture, spanMode,
                            null);
                });

        // rmatch: Perform an AND operation with the additional requirement that clauses match unique relations.
        QueryExtensions.register(
            "rmatch",
            "Perform and AND operation with the additional requirement that clauses match unique relations",
            List.of(PList.optional("queries", PList.Validator.ALL_QUERIES)),
            List.of(QueryFunction.VALUE_QUERY_ANY_NGRAM),
            (QueryInfo queryInfo, QueryExecutionContext context, List<Object> parameters) -> {
                    if (parameters.isEmpty())
                        throw new IllegalArgumentException("rmatch() requires one or more queries as arguments");
                    List<BLSpanQuery> tps = ((List<?>)parameters.get(0)).stream().map(o -> {
                        if (o instanceof BLSpanQuery p)
                            return p;
                        throw new InvalidQuery("Non-query parameter to rmatch(): " + o);
                    }).toList();
                    return TextPatternRelationMatch.createRelMatchQuery(context, tps);
            }
        );

        /*
         * rspan: change span mode of a query with an active relation.
         * <p>
         * That is, change the spans the query produces to the source or target
         * spans of the active relation, or the full relation span, or to a span
         * covering all matched relations.
         */
        PMultiple queryOrMatchInfo = PMultiple.required("subject",
                List.of(PQuery.required("query"), PMatchInfo.required("matchInfo")));
        QueryExtensions.register(FUNC_RSPAN,
                "Change the hit to the source, target or full span of the active relation",
                List.of(queryOrMatchInfo,
                        PEnum.of("spanMode", RelationInfo.SpanMode.class)),
                Arrays.asList(null, "full"),
                (queryInfo, context, args) -> {
                    if (args.size() < 2)
                        throw new IllegalArgumentException("rspan() requires a query and a span mode as arguments");
                    Object subject = args.get(0);
                    RelationInfo.SpanMode mode = RelationInfo.SpanMode.fromCode((String) args.get(1));
                    if (subject instanceof BLSpanQuery relations) {
                        return new SpanQueryRelationSpanAdjust(relations, null, mode, null);
                    } else if (subject instanceof MatchInfo mi) {
                        if (mi instanceof RelationInfo ri) {
                            if (mode == RelationInfo.SpanMode.SOURCE)
                                return ConstraintValue.get(ri.sourceMatchInfo());
                            else if (mode == RelationInfo.SpanMode.TARGET)
                                return ConstraintValue.get(ri.targetMatchInfo());
                            else if (mode == RelationInfo.SpanMode.FULL_SPAN)
                                return ConstraintValue.get(ri);
                            else
                                throw new IllegalArgumentException("Invalid span mode for rspan(matchInfo, mode): " + mode);
                        }
                        throw new IllegalArgumentException("rspan(matchInfo, mode) should get a relation, got " + mi.getClass().getSimpleName());
                    } else {
                        throw new IllegalArgumentException("rspan(query|matchInfo, mode) called with argument type " + subject.getClass().getSimpleName());
                    }
                });

        /*
         * rspan: change span mode of a query with an active relation.
         * <p>
         * That is, change the spans the query produces to the source or target
         * spans of the active relation, or the full relation span, or to a span
         * covering all matched relations.
         */
        QueryExtensions.register("cspan", "Change the hit to the captured span of a relation",
                List.of(PQuery.required("query"),
                        PString.identifier("captureName", true)),
                Arrays.asList(null, null),
                (queryInfo, context, args) -> {
                    if (args.size() < 2)
                        throw new IllegalArgumentException("cspan() requires a query and a capture name as arguments");
                    BLSpanQuery relations = (BLSpanQuery) args.get(0);
                    String captureName = (String) args.get(1);
                    return new SpanQueryRelationSpanAdjust(relations, null, null, captureName);
                });

        /*
         * rfield: get the hits from a specific parallel field/version.
         *
         * This is useful to e.g. highlight one of the versions with the hits from a parallel query.
         */
        QueryExtensions.register("rfield", "Get the hits from a specific parallel field/version",
                List.of(PQuery.required("query"),
                        PString.identifier("fieldOrVersion", true)), List.of(),
                (queryInfo, context, args) -> {
                    if (args.size() < 2)
                        throw new IllegalArgumentException("rfield() requires a query and a field or version name as arguments");
                    BLSpanQuery relations = (BLSpanQuery) args.get(0);
                    String fieldOrVersion = (String) args.get(1);
                    AnnotatedField field = queryInfo.index().annotatedFields().getByFieldOrVersionName(fieldOrVersion);
                    if (relations.getAnnotatedField() == field) {
                        // Nothing to do, just return query unchanged
                        return relations;
                    }
                    return new SpanQueryOtherFieldHits(relations, field);
                });

        /*
         * rcapture: Capture relations inside a span.
         *
         * Will capture all relations matching the specified type regex as a list
         * under the specified capture name.
         */
        QueryExtensions.registerRelationsFunction("rcapture",
                "Capture relations inside a span",
                List.of(PQuery.required("query"),
                        PString.identifier("captureAs"), PString.any("relationType")),
                Arrays.asList(null, DEFAULT_RCAP_NAME, REGEX_RELATIONS_ALL_CLASSES_ALL_TYPE),
                (queryInfo, context, args) -> {
                    if (args.isEmpty())
                        throw new IllegalArgumentException("rcapture() requires at least a query");
                    BLSpanQuery query = (BLSpanQuery) args.get(0);
                    String captureAs = context.ensureUniqueCapture((String) args.get(1));
                    String relationType = RelationUtil.optPrependDefaultClass((String) args.get(2), context);
                    AnnotationSensitivity field = context.withRelationAnnotation().luceneFieldRef();
                    return new SpanQueryCaptureRelationsWithinSpan(queryInfo, field, query, null, captureAs, relationType);
                });
    }

}
