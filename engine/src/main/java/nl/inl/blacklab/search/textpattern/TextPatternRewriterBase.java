package nl.inl.blacklab.search.textpattern;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Base class for TextPattern rewrite operations, i.e. optimization passes. */
public abstract class TextPatternRewriterBase implements TextPatternVisitor<TextPattern> {
    @Override
    public TextPattern visitAnd(TextPatternAnd tp) {
        List<TextPattern> rewrittenClauses = tp.getClauses().stream().map(c -> c.accept(this)).toList();
        return rewrittenClauses.equals(tp.getClauses()) ? tp : new TextPatternAnd(rewrittenClauses);
    }

    @Override
    public TextPattern visitAnyToken(TextPatternAnyToken tp) {
        return tp;
    }

    @Override
    public TextPattern visitCaptureGroup(TextPatternCaptureGroup tp) {
        TextPattern rewrittenClause = tp.getClause().accept(this);
        return rewrittenClause.equals(tp.getClause()) ? tp : new TextPatternCaptureGroup(rewrittenClause, tp.getCaptureName());
    }

    @Override
    public TextPattern visitCompare(TextPatternCompare tp) {
        TextPattern rewrittenLeft = tp.getLeftClause().accept(this);
        TextPattern rewrittenRight = tp.getRightClause().accept(this);
        if (!rewrittenLeft.equals(tp.getLeftClause()) || !rewrittenRight.equals(tp.getRightClause())) {
            return new TextPatternCompare(rewrittenLeft, rewrittenRight, tp.getOperator());
        }
        return tp;
    }

    @Override
    public TextPattern visitConstrained(TextPatternConstrained tp) {
        TextPattern rewrittenClause = tp.getClause().accept(this);
        TextPattern rewrittenConstraint = tp.getConstraint().accept(this);
        if (!rewrittenClause.equals(tp.getClause()) || !rewrittenConstraint.equals(tp.getConstraint())) {
            return new TextPatternConstrained(rewrittenClause, rewrittenConstraint);
        }
        return tp;
    }

    @Override
    public TextPattern visitDefaultValue(TextPatternDefaultValue tp) {
        return tp;
    }

    @Override
    public TextPattern visitFunctionCall(TextPatternFunctionCall tp) {
        List<TextPattern> rewrittenArgs = tp.getArgs().stream().map(a -> a.accept(this)).toList();
        if (!rewrittenArgs.equals(tp.getArgs())) {
            return new TextPatternFunctionCall(tp.getFunctionName(), rewrittenArgs);
        }
        return tp;
    }

    @Override
    public TextPattern visitImplication(TextPatternImplication tp) {
        TextPattern rewrittenAntecedent = tp.getAntecedent().accept(this);
        TextPattern rewrittenConsequent = tp.getConsequent().accept(this);
        if (!rewrittenAntecedent.equals(tp.getAntecedent()) || !rewrittenConsequent.equals(tp.getConsequent())) {
            return new TextPatternImplication(rewrittenAntecedent, rewrittenConsequent);
        }
        return tp;
    }

    @Override
    public TextPattern visitLook(TextPatternLook tp) {
        TextPattern rewrittenClause = tp.getClause().accept(this);
        if (!rewrittenClause.equals(tp.getClause())) {
            return new TextPatternLook(rewrittenClause, tp.isLookBehind(), tp.isNegate());
        }
        return tp;
    }

    @Override
    public TextPattern visitNot(TextPatternNot tp) {
        TextPattern rewrittenClause = tp.getClause().accept(this);
        if (!rewrittenClause.equals(tp.getClause())) {
            return new TextPatternNot(rewrittenClause);
        }
        return tp;
    }

    @Override
    public TextPattern visitOr(TextPatternOr tp) {
        List<TextPattern> rewrittenClauses = tp.getClauses().stream().map(c -> c.accept(this)).toList();
        return rewrittenClauses.equals(tp.getClauses()) ? tp : new TextPatternOr(rewrittenClauses);
    }

    @Override
    public TextPattern visitOverlapping(TextPatternOverlapping tp) {
        TextPattern rewrittenLeft = tp.getLeft().accept(this);
        TextPattern rewrittenRight = tp.getRight().accept(this);
        if (!rewrittenLeft.equals(tp.getLeft()) || !rewrittenRight.equals(tp.getRight())) {
            return new TextPatternOverlapping(rewrittenLeft, rewrittenRight, tp.getOperation());
        }
        return tp;
    }

    @Override
    public TextPattern visitPositionFilter(TextPatternPositionFilter tp) {
        TextPattern rewrittenProducer = tp.getProducer().accept(this);
        TextPattern rewrittenFilter = tp.getFilter().accept(this);
        if (!rewrittenProducer.equals(tp.getProducer()) || !rewrittenFilter.equals(tp.getFilter())) {
            return new TextPatternPositionFilter(rewrittenProducer, rewrittenFilter, tp.getOperation(), tp.isInvert());
        }
        return tp;
    }

    @Override
    public TextPattern visitPropertySelect(TextPatternPropertySelect tp) {
        TextPattern rewrittenLabel = tp.getLabel().accept(this);
        TextPattern rewrittenAnnotation = tp.getAnnotation().accept(this);
        if (!rewrittenLabel.equals(tp.getLabel()) || !rewrittenAnnotation.equals(tp.getAnnotation())) {
            return new TextPatternPropertySelect(rewrittenLabel, rewrittenAnnotation);
        }
        return tp;
    }

    @Override
    public TextPattern visitRegex(TextPatternRegex tp) {
        return tp;
    }

    @Override
    public TextPattern visitRelationTarget(RelationTarget relationTarget) {
        TextPattern rewrittenTarget = relationTarget.getTarget().accept(this);
        if (!rewrittenTarget.equals(relationTarget.getTarget())) {
            return new RelationTarget(relationTarget.getOperatorInfo(), rewrittenTarget, relationTarget.getSpanMode(), relationTarget.getCaptureAs());
        }
        return relationTarget;
    }

    @Override
    public TextPattern visitRelationMatch(TextPatternRelationMatch tp) {
        TextPattern rewrittenParent = tp.getParent().accept(this);
        List<RelationTarget> rewrittenChildren = tp.getChildren().stream().map(ch -> (RelationTarget)ch.accept(this)).toList();
        if (!rewrittenParent.equals(tp.getParent()) || !rewrittenChildren.equals(tp.getChildren())) {
            return new TextPatternRelationMatch(rewrittenParent, rewrittenChildren);
        }
        return tp;
    }

    @Override
    public TextPattern visitRepetition(TextPatternRepetition tp) {
        TextPattern rewrittenClause = tp.getClause().accept(this);
        if (!rewrittenClause.equals(tp.getClause())) {
            return new TextPatternRepetition(rewrittenClause, tp.getMin(), tp.getMax());
        }
        return tp;
    }

    @Override
    public TextPattern visitSequence(TextPatternSequence tp) {
        List<TextPattern> rewrittenClauses = tp.getClauses().stream().map(c -> c.accept(this)).toList();
        return rewrittenClauses.equals(tp.getClauses()) ? tp : new TextPatternSequence(rewrittenClauses);
    }

    @Override
    public TextPattern visitSettings(TextPatternSettings tp) {
        TextPattern rewrittenClause = tp.getClause().accept(this);
        if (!rewrittenClause.equals(tp.getClause())) {
            return new TextPatternSettings(tp.getSettings(), rewrittenClause);
        }
        return tp;
    }

    @Override
    public TextPattern visitTags(TextPatternTags tp) {
        Map<String, TextPattern> rewrittenAttributes = tp.getAttributes().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().accept(this)));
        if (!rewrittenAttributes.equals(tp.getAttributes())) {
            return new TextPatternTags(tp.getElementNameRegex(), rewrittenAttributes, tp.getAdjust(), tp.getCaptureAs());
        }
        return tp;
    }

    @Override
    public TextPattern visitTerm(TextPatternTerm tp) {
        return tp;
    }

    @Override
    public TextPattern visitValue(TextPatternValue tp) {
        return tp;
    }

    @Override
    public TextPattern visitWithinTagContext(TextPatternWithinTagContext tp) {
        TextPattern rewrittenProducer = tp.getProducer().accept(this);
        TextPattern rewrittenFilter = tp.getFilter().accept(this);
        if (!rewrittenProducer.equals(tp.getProducer()) || !rewrittenFilter.equals(tp.getFilter())) {
            return new TextPatternWithinTagContext(rewrittenProducer, rewrittenFilter, tp.getCaptureAs());
        }
        return tp;
    }
}
