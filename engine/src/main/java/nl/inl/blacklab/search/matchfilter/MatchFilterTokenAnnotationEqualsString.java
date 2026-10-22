package nl.inl.blacklab.search.matchfilter;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.BLTerms;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.SpansContext;

public class MatchFilterTokenAnnotationEqualsString extends MatchFilter {
    private final String groupName;

    private int groupIndex;

    private final BlackLabIndex index;

    private final String field;

    private final String annotationName;

    private final String luceneField;

    private int annotationIndex = -1;

    private final String compareToTermString;

    private final int compareToSortPosition;

    private final MatchSensitivity sensitivity;

    public MatchFilterTokenAnnotationEqualsString(BlackLabIndex index, String field, String groupName, String annotationName, String termString,
                                                MatchSensitivity sensitivity, String forwardIndexLuceneField) {
        this(index, field, groupName, annotationName, termString, sensitivity, -1, forwardIndexLuceneField);
    }

    public MatchFilterTokenAnnotationEqualsString(BlackLabIndex index, String field, String groupName, String annotationName, String termString,
            MatchSensitivity sensitivity, int compareToSortPosition, String forwardIndexLuceneField) {
        this.index = index;
        this.field = field;
        this.groupName = groupName;
        this.compareToTermString = termString;
        this.sensitivity = sensitivity;

        // We either get a name and will look up the annotation later, or we get an annotation and will use its name.
        this.annotationName = annotationName;// == null ? annotation.name() : annotationName;
//        this.annotation = annotation;
        this.luceneField = forwardIndexLuceneField; //annotation == null ? null : annotation.forwardIndexLuceneField();

        this.compareToSortPosition = compareToSortPosition;
    }

    @Override
    public String toString() {
        return "TOK_ANNOT_STR(" + groupName + (annotationName() == null ? "" : ", " + annotationName()) + ", \"" + compareToTermString + "\")";
    }

    private String annotationName() {
        return /*annotation != null ? annotation.name() : */annotationName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((groupName == null) ? 0 : groupName.hashCode());
        result = prime * result + ((annotationName() == null) ? 0 : annotationName().hashCode());
        result = prime * result + ((compareToTermString == null) ? 0 : compareToTermString.hashCode());
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
        MatchFilterTokenAnnotationEqualsString other = (MatchFilterTokenAnnotationEqualsString) obj;
        if (groupName == null) {
            if (other.groupName != null)
                return false;
        } else if (!groupName.equals(other.groupName))
            return false;
        if (annotationName() == null) {
            if (other.annotationName() != null)
                return false;
        } else if (!annotationName().equals(other.annotationName()))
            return false;
        if (compareToTermString == null) {
            if (other.compareToTermString != null)
                return false;
        } else if (!compareToTermString.equals(other.compareToTermString))
            return false;
        return true;
    }

    @Override
    public void setSpansContext(SpansContext context) {
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
        int leftTermGlobalId = fiDoc.getTokenSegmentSortPosition(annotationIndex, tokenPosition, sensitivity);
        return ConstraintValue.get(compareToSortPosition == leftTermGlobalId);
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        if (annotationName != null) {
            annotationIndex = fiAccessor.getAnnotationIndex(annotationName);
        }
    }

    @Override
    public MatchFilter forSegment(LeafReaderContext context) {
        // Look up the sort position for the value we're comparing to.
        assert annotationName != null;
        Terms terms = BLTerms.forSegment(context, luceneField).reader();
        int sortPosition = terms.termToSortPosition(compareToTermString, sensitivity);

        MatchFilterTokenAnnotationEqualsString mf = new MatchFilterTokenAnnotationEqualsString(index, field,
                groupName, annotationName, compareToTermString, sensitivity,
                sortPosition, luceneField);
        mf.annotationIndex = annotationIndex;
        return mf;
    }

    @Override
    public MatchFilter rewrite() {
        return this;
    }

    public String getCapture() {
        return groupName;
    }

    public String getAnnotation() {
        return annotationName;
    }

    public MatchSensitivity getSensitivity() {
        return sensitivity;
    }

    public String getValue() {
        return compareToTermString;
    }
}
