package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureRelationsBetweenSpans;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.lucene.SpansAndFilterFactoryUniqueRelations;

/**
 * Relations operator, matching a source (parent) to one or more targets (children).
 */
public class TextPatternRelationMatch extends TextPattern {

    private final TextPattern parent;

    private final List<RelationTarget> children;

    public TextPatternRelationMatch(TextPattern parent, List<RelationTarget> children) {
        this.parent = parent;
        assert !children.isEmpty();
        this.children = new ArrayList<>(children);

        // If there's no parent, all children must be root relations
        // (and really it can only be 1 if parsed from CQL, but we don't check that here)
        if (parent == null && !children.stream()
                .allMatch(c -> c.getOperatorInfo().getDirection() == SpanQueryRelations.Direction.ROOT)) {
            throw new IllegalArgumentException("Relation match has no parent, so all children must be root relations");
        }

        // All children should either use alignment operators ==> or regular ones -->, but not a mix
        long numAlignment = children.stream().filter(c -> c.getOperatorInfo().isAlignment()).count();
        if (numAlignment > 0 && numAlignment < children.size()) {
            throw new IllegalArgumentException("Relation match has both alignment and regular operators");
        }
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        if (children.isEmpty())
            throw new InvalidQuery("Relation match has no children");
        if (children.get(0).getOperatorInfo().isAlignment()) {
            // Find all relations between source and target hits (parallel corpora)
            return createAlignmentQuery(context);
        } else {
            // Find a single matching relation.
            return createRelMatchQuery(context, parent, children);
        }
    }

    private BLSpanQuery createAlignmentQuery(QueryExecutionContext context) throws InvalidQuery {
        BLSpanQuery source = TextPatternDefaultValue.replaceWithAnyToken(parent).translate(context);
        List<SpanQueryCaptureRelationsBetweenSpans.Target> targets = new ArrayList<>();
        for (RelationTarget child: children) {
            targets.add(alignmentTarget(child, context));
        }
        return new SpanQueryCaptureRelationsBetweenSpans(source, targets);
    }

    private static SpanQueryCaptureRelationsBetweenSpans.Target alignmentTarget(RelationTarget target, QueryExecutionContext context)
            throws InvalidQuery {
        RelationOperatorInfo opInfo = target.getOperatorInfo();
        assert opInfo.isAlignment();

        String relationType = opInfo.getFullTypeRegex(context);

        // Auto-determine capture name from relation type if none was given
        String captureName = target.getCaptureAs();
        if (StringUtils.isEmpty(captureName))
            captureName = XFRelations.determineCaptureAs(context, relationType, true);

        // replace _ with any ngram
        TextPattern targetNoDefVal = TextPatternDefaultValue.replaceWithAnyToken(target.getTarget());
        QueryExecutionContext targetContext = context.withDocVersion(opInfo.getTargetVersion());
        BLSpanQuery targetQuery = targetNoDefVal.translate(targetContext);

        return SpanQueryCaptureRelationsBetweenSpans.Target.get(
                context.queryInfo(), context.withRelationAnnotation().luceneField(), targetQuery,
                targetContext.field().name(), captureName, relationType, opInfo.isOptionalMatch(),
                null, null);
    }

    private BLSpanQuery createRelMatchQuery(QueryExecutionContext context, TextPattern parent, List<RelationTarget> children) throws InvalidQuery {
        List<BLSpanQuery> clauses = new ArrayList<>();
        if (parent != null) { // might be a root relation operator, which has no parent
            BLSpanQuery translatedParent = TextPatternDefaultValue.replaceWithAnyToken(parent)
                    .translate(context);
            clauses.add(translatedParent);
        }
        for (RelationTarget child: children) {
            clauses.add(child.targetQuery(context));
        }

        return createRelMatchQuery(context, clauses);
    }

    public static BLSpanQuery createRelMatchQuery(QueryExecutionContext context, List<BLSpanQuery> clauses) {
        assert !clauses.isEmpty();
        // Filter out "any n-gram" arguments ([]*) because they don't do anything
        clauses = clauses.stream()
                .filter(q -> !BLSpanQuery.isAnyNGram(q))    // remove any []* clauses, which don't do anything
                .toList();

        if (clauses.isEmpty()) {
            // All clauses were []*; return any n-gram query (good luck with that...)
            return SpanQueryAnyToken.anyNGram(context.queryInfo(), context.luceneField());
        }
        if (clauses.size() == 1) {
            // Nothing to match, just return the clause
            return clauses.get(0);
        }
        SpanQueryAnd spanQueryAnd = new SpanQueryAnd(clauses);
        spanQueryAnd.setFilter(SpansAndFilterFactoryUniqueRelations.INSTANCE); // discard match if relation matched twice
        return spanQueryAnd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternRelationMatch that = (TextPatternRelationMatch) o;
        return Objects.equals(parent, that.parent)
                && Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent, children);
    }

    @Override
    public String toString() {
        return "RMATCH(" + parent + ", " + children + ")";
    }

    public TextPattern getParent() {
        return parent;
    }

    public List<RelationTarget> getChildren() {
        return children;
    }

    @Override
    public boolean isRelationsQuery() {
        return true;
    }

    @Override
    protected boolean hasWithSpans() {
        return parent.hasWithSpans() || children.stream()
                .anyMatch(ch -> ch.getTarget().hasWithSpans());
    }

    @Override
    public TextPattern applyWithSpans() {
        // Apply with-spans() to the parent and all children
        TextPattern newParent = parent.applyWithSpans();
        List<RelationTarget> newChildren = children.stream()
                .map(ch -> new RelationTarget(ch.getOperatorInfo(), ch.getTarget().applyWithSpans(), ch.getSpanMode(), ch.getCaptureAs()))
                .toList();
        return new TextPatternRelationMatch(newParent, newChildren);
    }

    @Override
    protected boolean hasRspanAll() {
        return parent.hasRspanAll() || children.stream()
                .anyMatch(ch -> ch.getTarget().hasRspanAll());
    }

    @Override
    public TextPattern applyRspanAll() {
        TextPattern newParent = parent.applyRspanAll();
        List<RelationTarget> newChildren = children.stream()
                .map(ch -> new RelationTarget(ch.getOperatorInfo(), ch.getTarget().applyRspanAll(), ch.getSpanMode(), ch.getCaptureAs()))
                .toList();
        return new TextPatternRelationMatch(newParent, newChildren);
    }
}
