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
public class RelationInfoField {
    /** Field name, e.g. contents%_relation@s */
    private final String fieldName;

    /** Field offset in docs file */
    protected long docsOffset;

    protected RelationInfoField(String fieldName) {
        this.fieldName = fieldName;
    }

    /**
     * Read our values from the file
     */
    public RelationInfoField(IndexInput file) throws IOException {
        this.fieldName = file.readString();
        this.docsOffset = file.readLong();
    }

    public String getFieldName() {
        return fieldName;
    }

    public long getDocsOffset() {
        return docsOffset;
    }

    public void write(IndexOutput file) throws IOException {
        file.writeString(getFieldName());
        file.writeLong(getDocsOffset());
    }
}
