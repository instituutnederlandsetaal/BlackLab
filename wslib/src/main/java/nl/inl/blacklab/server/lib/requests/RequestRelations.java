package nl.inl.blacklab.server.lib.requests;

import java.util.Objects;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.server.BlsMain;
import nl.inl.blacklab.server.exceptions.BadRequest;

public final class RequestRelations {

    private final String corpusName;
    private final String annotatedFieldName;
    private final long limitValues;
    private final String relClasses;
    private final boolean separateSpans;
    private final boolean onlySpans;

    /** Determined from constructor parameters */
    private BlackLabIndex index;
    private final AnnotatedField annotatedField;

    public RequestRelations(String corpusName, String annotatedFieldName, long limitValues, String relClasses,
            boolean separateSpans, boolean onlySpans) {
        this.corpusName = corpusName;
        this.annotatedFieldName = annotatedFieldName;
        this.limitValues = limitValues;
        this.relClasses = relClasses;
        this.separateSpans = separateSpans;
        this.onlySpans = onlySpans;

        index = BlsMain.get().getSearchManager().getIndexManager().getIndex(corpusName).blIndex();
        annotatedField = this.annotatedFieldName == null ? index.mainAnnotatedField() :
                index.annotatedField(this.annotatedFieldName);
        if (annotatedField == null) {
            throw new BadRequest("FIELD_NOT_FOUND", "Annotated field '" + this.annotatedFieldName + "' not found in corpus '"
                    + corpusName + "'");
        }
    }

    public BlackLabIndex index() {
        return index;
    }

    public AnnotatedField annotatedField() {
        return annotatedField;
    }

    public boolean separateSpans() {
        return separateSpans;
    }

    public long limitValues() {
        return limitValues;
    }

    public String relClasses() {
        return relClasses;
    }

    public boolean onlySpans() {
        return onlySpans;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (RequestRelations) obj;
        return Objects.equals(this.corpusName, that.corpusName) &&
                Objects.equals(this.annotatedFieldName, that.annotatedFieldName) &&
                this.limitValues == that.limitValues &&
                Objects.equals(this.relClasses, that.relClasses) &&
                this.separateSpans == that.separateSpans &&
                this.onlySpans == that.onlySpans;
    }

    @Override
    public int hashCode() {
        return Objects.hash(corpusName, annotatedFieldName, limitValues, relClasses, separateSpans, onlySpans);
    }

    @Override
    public String toString() {
        return "RequestRelations[" +
                "corpusName=" + corpusName + ", " +
                "annotatedFieldName=" + annotatedFieldName + ", " +
                "limitValues=" + limitValues + ", " +
                "relClasses=" + relClasses + ", " +
                "separateSpans=" + separateSpans + ", " +
                "onlySpans=" + onlySpans + ']';
    }

    public RequestRelations withAnnotatedField(String fieldName) {
        return new RequestRelations(corpusName, fieldName, limitValues, relClasses, separateSpans, onlySpans);
    }
}
