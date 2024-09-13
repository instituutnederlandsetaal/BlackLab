package nl.inl.blacklab.codec;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.IndexInput;

import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.forwardindex.RelationInfoSegmentReader;

/**
 * Manages read access to forward indexes for a single segment.
 */
@ThreadSafe
public class SegmentRelationInfo implements AutoCloseable {

    /** Our fields producer */
    private final BlackLabPostingsReader fieldsProducer;

    /** Contains field names and offsets to term index file, where the terms for the field can be found */
    private final Map<String, RelationInfoField> fieldsByName = new LinkedHashMap<>();


    /** Where relations for each doc can be found in relations file */
    private IndexInput _docsFile;

    /** Where attributes for each relation can be found in attrsets file */
    private IndexInput _relationsFile;

    /** Where name and value of attributes can be found */
    private IndexInput _attrSetsFile;

    /** Where name and value of attributes can be found */
    private IndexInput _attrValuesFile;

    /** Attribute names */
    private List<String> attributeNames = new ArrayList<>();

    public static SegmentRelationInfo openIfPresent(BlackLab40PostingsReader postingsReader) throws IOException {
        try (IndexInput fieldsFile = postingsReader.openIndexFile(BlackLabPostingsFormat.RI_FIELDS_EXT)) {
            return new SegmentRelationInfo(postingsReader, fieldsFile);
        } catch (NoSuchFileException | FileNotFoundException e) {
            // No relation info stored; that's okay
            return null;
        }
    }

    private SegmentRelationInfo(BlackLabPostingsReader postingsReader, IndexInput fieldsFile) throws IOException {
        this.fieldsProducer = postingsReader;

        // Read fields file
        while (fieldsFile.getFilePointer() < (fieldsFile.length() - CodecUtil.footerLength())) {
            RelationInfoField f = new RelationInfoField(fieldsFile);
            this.fieldsByName.put(f.getFieldName(), f);
        }

        // Read attribute names file
        try (IndexInput attrNamesFile = postingsReader.openIndexFile(BlackLabPostingsFormat.RI_ATTR_NAMES_EXT)) {
            while (attrNamesFile.getFilePointer() < (attrNamesFile.length() - CodecUtil.footerLength())) {
                attributeNames.add(attrNamesFile.readString());
            }
        }

        _docsFile = postingsReader.openIndexFile(BlackLabPostingsFormat.RI_DOCS_EXT);
        _relationsFile = postingsReader.openIndexFile(BlackLabPostingsFormat.RI_RELATIONS_EXT);
        _attrSetsFile = postingsReader.openIndexFile(BlackLabPostingsFormat.RI_ATTR_SETS_EXT);
        _attrValuesFile = postingsReader.openIndexFile(BlackLabPostingsFormat.RI_ATTR_VALUES_EXT);
    }

    private synchronized IndexInput getCloneOfDocsFile() {
        // synchronized because clone() is not thread-safe
        return _docsFile.clone();
    }

    private synchronized IndexInput getCloneOfRelationsFile() {
        // synchronized because clone() is not thread-safe
        return _relationsFile.clone();
    }

    private synchronized IndexInput getCloneOfAttrSetsFile() {
        // synchronized because clone() is not thread-safe
        return _attrSetsFile.clone();
    }

    private synchronized IndexInput getCloneOfAttrValuesFile() {
        // synchronized because clone() is not thread-safe
        return _attrValuesFile.clone();
    }

    @Override
    public void close() {
        try {
            _docsFile.close();
            _relationsFile.close();
            _attrSetsFile.close();
            _attrValuesFile.clone();
            _docsFile = _relationsFile = _attrSetsFile = _attrValuesFile = null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** 
     * Get a new ForwardIndexSegmentReader on this segment. 
     * Though the reader is not Threadsafe, a new instance is returned every time, 
     * So this function can be used from multiple threads. 
     */
    public RelationInfoSegmentReader reader() {
        return new Reader();
    }

    /**
     * A forward index reader for a single segment.
     *
     * This can be used by a single thread to read from a forward index segment.
     * Not thread-safe because it contains state (file pointers, doc offset/length).
     */
    @NotThreadSafe
    public class Reader implements RelationInfoSegmentReader {

        private IndexInput _docs;

        private IndexInput _relations;

        private IndexInput _attrSets;

        private IndexInput _attrValues;

        private IndexInput docs() {
            if (_docs == null)
                _docs = getCloneOfDocsFile();
            return _docs;
        }

        private IndexInput relations() {
            if (_relations == null)
                _relations = getCloneOfRelationsFile();
            return _relations;
        }

        private IndexInput attrSets() {
            if (_attrSets == null)
                _attrSets = getCloneOfAttrSetsFile();
            return _attrSets;
        }

        private IndexInput attrValues() {
            if (_attrValues == null)
                _attrValues = getCloneOfAttrValuesFile();
            return _attrValues;
        }

        /** Retrieve the attributes for a specific relation in a document.
         *
         * @param luceneField lucene field to retrieve snippet from
         * @param docId segment-local docId of document to retrieve snippet from
         * @param relationId relation id
         * @return attributes
         */
        public Map<String, String> getAttributes(String luceneField, int docId, int relationId) {
            RelationInfoField f = fieldsByName.get(luceneField);
            long docsOffset = f.getDocsOffset(); // offset in docs file for this field
            try {
                // Determine where the relations for this doc start
                docs().seek(docsOffset + docId * (Long.BYTES + Integer.BYTES));
                long relationsOffset = _docs.readLong();
                // Find the attribute set offset for this relation
                relations().seek(relationsOffset + relationId);
                long attrSetOffset = _relations.readLong();
                // Find the attribute set
                attrSets().seek(attrSetOffset);
                int nAttr = attrSets().readVInt();
                Map<String, String> attrMap = new LinkedHashMap<>();
                for (int i = 0; i < nAttr; i++) {
                    int attrNameIndex = attrSets().readVInt();
                    long attrValueOffse = attrSets().readLong();
                    attrValues().seek(attrValueOffse);
                    String attrValue = attrValues().readString();
                    attrMap.put(attributeNames.get(attrNameIndex), attrValue);
                }
                return attrMap;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
