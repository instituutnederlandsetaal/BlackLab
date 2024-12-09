package nl.inl.blacklab.server.lib.results;

import java.util.Map;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

public class ResultAnnotatedField {

    BlackLabIndex index;

    private String indexName;

    private AnnotatedField fieldDesc;

    private Map<String, ResultAnnotationInfo> annotInfos;

    private long tokenCount;

    ResultAnnotatedField(BlackLabIndex index, String indexName, AnnotatedField fieldDesc,
            Map<String, ResultAnnotationInfo> annotInfos) {
        this.index = index;
        this.indexName = indexName;
        this.fieldDesc = fieldDesc;
        this.annotInfos = annotInfos;
        Long tokenCount = index.metadata().tokenCountPerField().get(fieldDesc.name());
        this.tokenCount = tokenCount == null ? 0 : tokenCount;
    }

    public String getIndexName() {
        return indexName;
    }

    public AnnotatedField getFieldDesc() {
        return fieldDesc;
    }

    public Map<String, ResultAnnotationInfo> getAnnotInfos() {
        return annotInfos;
    }

    public int compare(ResultAnnotatedField resultAnnotatedField) {
        // sort main at the top
        if (index.mainAnnotatedField() == fieldDesc)
            return -1;
        else if (index.mainAnnotatedField() == resultAnnotatedField.fieldDesc)
            return 1;
        return fieldDesc.name().compareTo(resultAnnotatedField.fieldDesc.name());
    }

    public long getTokenCount() {
        return tokenCount;
    }
}
