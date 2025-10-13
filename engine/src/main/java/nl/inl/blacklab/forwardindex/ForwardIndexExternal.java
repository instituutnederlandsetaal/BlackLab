package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Forward index implementation for external forward index files.
 *
 * External forward indexes are a legacy format. They will be deprecated and eventually removed.
 */
public class ForwardIndexExternal extends ForwardIndexAbstract {

    public ForwardIndexExternal(BlackLabIndex index, AnnotatedField field) {
        super(index, field);
    }

    private static File determineAfiDir(File indexDir, Annotation annotation) {
        return new File(indexDir, "fi_" + annotation.luceneFieldPrefix());
    }

    @Override
    protected AnnotationForwardIndex openAnnotationForwardIndex(Annotation annotation, BlackLabIndex index) {
        File dir = determineAfiDir(index.indexDirectory(), annotation);
        boolean create = index.indexMode() && index.isEmpty();
        AnnotationForwardIndexExternalAbstract afi = AnnotationForwardIndexExternalAbstract.open(
                index.reader(), dir, index.indexMode(), index.collator(), create, annotation);
        add(annotation, afi);
        return afi;
    }

    public void addDocument(
            Map<Annotation, List<String>> content, Map<Annotation, List<Integer>> posIncr, Document document) {
        for (Entry<Annotation, List<String>> e: content.entrySet()) {
            Annotation annotation = e.getKey();
            AnnotationForwardIndexExternalWriter afi = (AnnotationForwardIndexExternalWriter)get(annotation);
            List<Integer> posIncrThisAnnot = posIncr.get(annotation);
            int fiid = afi.addDocument(e.getValue(), posIncrThisAnnot);
            String fieldName = annotation.forwardIndexIdField();
            document.add(new IntPoint(fieldName, fiid));
            document.add(new StoredField(fieldName, fiid));
            document.add(new NumericDocValuesField(fieldName, fiid)); // for fast retrieval (FiidLookup)
        }
    }

}
