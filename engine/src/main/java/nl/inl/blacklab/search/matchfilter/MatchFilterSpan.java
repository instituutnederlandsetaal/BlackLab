package nl.inl.blacklab.search.matchfilter;

import java.util.Objects;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

public class MatchFilterSpan extends MatchFilter {
    private final String groupName;

    private int groupIndex;

    public MatchFilterSpan(String groupName) {
        this.groupName = groupName;
    }

    @Override
    public String toString() {
        return groupName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        MatchFilterSpan that = (MatchFilterSpan) o;
        return groupIndex == that.groupIndex && Objects.equals(groupName, that.groupName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupName, groupIndex);
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
        return ConstraintValue.get(span);
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        // nothing to do here
    }

    @Override
    public MatchFilter rewrite() {
        return this;
    }

    public String getGroupName() {
        return groupName;
    }
}
