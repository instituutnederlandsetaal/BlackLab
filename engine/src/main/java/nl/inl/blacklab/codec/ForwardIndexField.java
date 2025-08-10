package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

/**
 * Information about a Lucene field that represents a BlackLab annotation in the forward index.
 * A Field's information is only valid for the segment (leafreadercontext) of the index it was read from.
 * Contains offsets into files comprising the terms strings and forward index information.
 * Such as where in the term strings file the strings for this field begin.
 * See integrated.md
 */
public class ForwardIndexField {
    private final String fieldName;
    protected int numberOfTerms;
    protected long termOrderOffset;
    protected long termIndexOffset;
    protected long tokensIndexOffset;

    protected ForwardIndexField(String fieldName) {
        this.fieldName = fieldName;
    }

    /**
     * Read our values from the file
     */
    public ForwardIndexField(IndexInput file) throws IOException {
        this.fieldName = file.readString();
        this.numberOfTerms = file.readInt();
        this.termOrderOffset = file.readLong();
        this.termIndexOffset = file.readLong();
        this.tokensIndexOffset = file.readLong();
    }

    public String getFieldName() {
        return fieldName;
    }

    public int getNumberOfTerms() {
        return numberOfTerms;
    }

    public long getTermIndexOffset() {
        return termIndexOffset;
    }

    public long getTermOrderOffset() {
        return termOrderOffset;
    }

    public long getTokensIndexOffset() {
        return tokensIndexOffset;
    }

    public void write(IndexOutput file) throws IOException {
        file.writeString(getFieldName());
        file.writeInt(getNumberOfTerms());
        file.writeLong(getTermOrderOffset());
        file.writeLong(getTermIndexOffset());
        file.writeLong(getTokensIndexOffset());
    }
}
