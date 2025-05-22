package nl.inl.blacklab.search.matchfilter;

import java.util.Objects;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

public class MatchFilterFunctionCall extends MatchFilter {
    private final String functionName;

    private final String groupName;

    private int groupIndex;

    public MatchFilterFunctionCall(String functionName, String groupName) {
        this.functionName = functionName;
        if (!functionName.matches("start|end"))
            throw new IllegalArgumentException("Unknown function: " + functionName);
        this.groupName = groupName;
    }

    @Override
    public String toString() {
        return functionName + "(" + groupName + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MatchFilterFunctionCall that = (MatchFilterFunctionCall) o;
        return Objects.equals(functionName, that.functionName) && Objects.equals(groupName, that.groupName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(functionName, groupName);
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        groupIndex = context.registerMatchInfo(groupName, null);
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, MatchInfo[] matchInfo) {
        MatchInfo span = groupIndex < matchInfo.length ? matchInfo[groupIndex] : null;
        if (span == null)
            return ConstraintValue.undefined();
        switch (functionName) {
        case "start":
            return ConstraintValue.get(span.getSpanStart());
        case "end":
            return ConstraintValue.get(span.getSpanEnd());
        }
        throw new UnsupportedOperationException("Unknown function: " + functionName);
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        // NOP
    }

    @Override
    public MatchFilter rewrite() {
        return this;
    }

    public String getName() {
        return functionName;
    }

    public String getCapture() {
        return groupName;
    }
}
