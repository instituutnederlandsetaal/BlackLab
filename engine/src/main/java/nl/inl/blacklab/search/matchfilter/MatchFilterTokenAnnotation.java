package nl.inl.blacklab.search.matchfilter;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

public class MatchFilterTokenAnnotation extends MatchFilter {
    private final String groupName;

    private int groupIndex;

    private final String annotationName;

    private int annotationIndex = -1;

    public MatchFilterTokenAnnotation(String groupName, String annotationName) {
        this.groupName = groupName;
        this.annotationName = annotationName;
    }

    @Override
    public String toString() {
        return groupName + (annotationName == null ? "" : "." + annotationName);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((groupName == null) ? 0 : groupName.hashCode());
        result = prime * result + ((annotationName == null) ? 0 : annotationName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MatchFilterTokenAnnotation other = (MatchFilterTokenAnnotation) obj;
        if (groupName == null) {
            if (other.groupName != null)
                return false;
        } else if (!groupName.equals(other.groupName))
            return false;
        if (annotationName == null) {
            if (other.annotationName != null)
                return false;
        } else if (!annotationName.equals(other.annotationName))
            return false;
        return true;
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
        int tokenPosition = span.getSpanStart();
        if (annotationIndex < 0)
            return ConstraintValue.get(tokenPosition);
        int segmentTermId = fiDoc.getTokenSegmentTermId(annotationIndex, tokenPosition);
        String term = fiDoc.getTermString(annotationIndex, segmentTermId);
        return ConstraintValue.get(term);
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        if (annotationName != null)
            annotationIndex = fiAccessor.getAnnotationNumber(annotationName);
    }

    @Override
    public MatchFilter rewrite() {
        return this;
    }

    public MatchFilter matchTokenString(String str, MatchSensitivity sensitivity) {
        return new MatchFilterTokenAnnotationEqualsString(groupName, annotationName, str, sensitivity);
    }

    public MatchFilter matchOtherTokenSameProperty(String otherGroupName, MatchSensitivity sensitivity) {
        return new MatchFilterSameTokens(groupName, otherGroupName, annotationName, sensitivity);
    }

    public boolean hasAnnotation() {
        return annotationName != null;
    }

    public String getAnnotationName() {
        return annotationName;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getCapture() {
        return groupName;
    }

    public String getAnnotation() {
        return annotationName;
    }
}
