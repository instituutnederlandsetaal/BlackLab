package nl.inl.blacklab.search.textpattern;

/** Visitor interface for TextPattern tree structures. */
public interface TextPatternVisitor<T> {

    T visitAnd(TextPatternAnd textPatternAnd);

    T visitAnyToken(TextPatternAnyToken textPatternAnyToken);

    T visitCaptureGroup(TextPatternCaptureGroup textPatternCaptureGroup);

    T visitCompare(TextPatternCompare textPatternCompare);

    T visitAdditiveOp(TextPatternAdditiveOp textPatternAdditiveOp);

    T visitConstrained(TextPatternConstrained textPatternConstrained);

    T visitDefaultValue(TextPatternDefaultValue textPatternDefaultValue);

    T visitFunctionCall(TextPatternFunctionCall textPatternFunctionCall);

    T visitImplication(TextPatternImplication textPatternImplication);

    T visitLook(TextPatternLook textPatternLook);

    T visitNot(TextPatternNot textPatternNot);

    T visitOr(TextPatternOr textPatternOr);

    T visitOverlapping(TextPatternOverlapping textPatternOverlapping);

    T visitPositionFilter(TextPatternPositionFilter textPatternPositionFilter);

    T visitPropertySelect(TextPatternPropertySelect textPatternPropertySelect);

    T visitRegex(TextPatternRegex textPatternRegex);

    T visitRelationMatch(TextPatternRelationMatch textPatternRelationMatch);

    T visitRepetition(TextPatternRepetition textPatternRepetition);

    T visitSequence(TextPatternSequence textPatternSequence);

    T visitSettings(TextPatternSettings textPatternSettings);

    T visitTags(TextPatternTags textPatternTags);

    T visitTerm(TextPatternTerm textPatternTerm);

    T visitValue(TextPatternValue textPatternValue);

    T visitWithinTagContext(TextPatternWithinTagContext textPatternWithinTagContext);

    T visitRelationTarget(RelationTarget relationTarget);
}
