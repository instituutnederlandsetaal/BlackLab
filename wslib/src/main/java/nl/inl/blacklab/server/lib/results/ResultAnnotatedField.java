package nl.inl.blacklab.server.lib.results;

import java.util.Map;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.CorpusSize;

public class ResultAnnotatedField {

    BlackLabIndex index;

    private final String indexName;

    private final AnnotatedField fieldDesc;

    private final Map<String, ResultAnnotationInfo> annotInfos;

    private CorpusSize.Count count;

    ResultAnnotatedField(BlackLabIndex index, String indexName, AnnotatedField fieldDesc,
            Map<String, ResultAnnotationInfo> annotInfos) {
        this.index = index;
        this.indexName = indexName;
        this.fieldDesc = fieldDesc;
        this.annotInfos = annotInfos;
        count = index.metadata().countPerField().get(fieldDesc.name());
        if (count == null)
            count = new CorpusSize.Count(0, 0);
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

    public CorpusSize.Count getCount() {
        return count;
    }
}
