package nl.inl.blacklab.search.matchfilter;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

public class MatchFilterSameTokens extends MatchFilter {

    private final String annotationName;

    private int annotIndex = -1;

    private final String[] groupName;

    private final int[] groupIndex;

    private final MatchSensitivity sensitivity;

    public MatchFilterSameTokens(String leftGroup, String rightGroup, String annotationName, MatchSensitivity sensitivity) {
        this.groupName = new String[] { leftGroup, rightGroup };
        this.groupIndex = new int[] { -1, -1 };

        if (annotationName == null)
            throw new IllegalArgumentException("annotationName cannot be null");
        this.annotationName = annotationName;
        this.sensitivity = sensitivity;
    }

    @Override
    public String toString() {
        String annotPart = "." + annotationName;
        return groupName[0] + annotPart + " = " + groupName[1] + annotPart;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(groupIndex);
        result = prime * result + Arrays.hashCode(groupName);
        result = prime * result + annotIndex;
        result = prime * result + annotationName.hashCode();
        result = prime * result + ((sensitivity == null) ? 0 : sensitivity.hashCode());
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
        MatchFilterSameTokens other = (MatchFilterSameTokens) obj;
        if (!Arrays.equals(groupIndex, other.groupIndex))
            return false;
        if (!Arrays.equals(groupName, other.groupName))
            return false;
        if (annotIndex != other.annotIndex)
            return false;
        if (!annotationName.equals(other.annotationName))
            return false;
        return sensitivity == other.sensitivity;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        for (int i = 0; i < groupName.length; i++)
            groupIndex[i] = context.registerMatchInfo(groupName[i], null);
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, MatchInfo[] matchInfo) {
        int[] segmentTermIds = new int[2];
        for (int i = 0; i < 2; i++) {
            MatchInfo span = groupIndex[i] < matchInfo.length ? matchInfo[groupIndex[i]] : null;
            if (span == null)
                return ConstraintValue.get(false); // if either side is undefined, they are not equal
            int tokenPosition = span.getSpanStart();
            if (annotIndex < 0) {
                // strange... (if not annotation given, compare positions..!? shouldn't happen, but...)
                segmentTermIds[i] = tokenPosition;
            } else {
                segmentTermIds[i] = fiDoc.getTokenSegmentTermId(annotIndex, tokenPosition);
            }
        }
        if (sensitivity == MatchSensitivity.SENSITIVE)
            return ConstraintValue.get(segmentTermIds[0] == segmentTermIds[1]);
        // (Somewhat) insensitive; let Terms determine if term ids have the same sort position or not
        return ConstraintValue.get(fiDoc.segmentTermsEqual(annotIndex, segmentTermIds, sensitivity));
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        annotIndex = fiAccessor.getAnnotationIndex(annotationName);
    }

    @Override
    public MatchFilter rewrite() {
        return this;
    }

    public List<String> getCaptures() {
        return List.of(groupName);
    }

    public String getAnnotation() {
        return annotationName;
    }

    public MatchSensitivity getSensitivity() {
        return sensitivity;
    }
}
