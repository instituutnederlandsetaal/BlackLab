package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.ByteArrayDataOutput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.RelationInfo;

/**
 * Hook into the postings writer to write the relation info.
 *
 * Keeps track of attributes per unique relation id and writes them to the relation info
 * files so we can look them up later.
 */
class PWPluginRelationInfo implements PWPlugin {

    /**
     * Offset in attribute set file per relation id for a single field and doc.
     *
     * Keeps track of position in the attribute set file where the occurrences of each relation id
     * are stored for a single document and field.
     */
    private static class RelationIdPerAttrSetOffset extends HashMap<Long, Long> {
        // intentionally blank; this is just a typedef for readability
    }

    /**
     * Keeps track of number of relations (max relation id) and term vector file offsets for a single Lucene field.
     *
     * Tokens length is shared between all Lucene fields belonging to a single annotated
     * field, because those all have the same length.
     */
    private static class OffsetsAndMaxRelationIdPerDocument {
        /** For each document id (key), the length in tokens of the annotated field this Lucene field is a part of.
         *  (shared with other annotations on this annotated field) */
        private final Map<Integer, Integer> docId2MaxRelationId;

        /** For each document id (key), record the term vector file offsets (value). */
        private final SortedMap<Integer, RelationIdPerAttrSetOffset> docId2TermVecFileOffsets = new TreeMap<>();

        OffsetsAndMaxRelationIdPerDocument(Map<Integer, Integer> docId2MaxRelationId) {
            this.docId2MaxRelationId = docId2MaxRelationId;
        }

        void putTempFileOffset(int docId, long attributeSetOffset, long tempFilePointer) {
            RelationIdPerAttrSetOffset vecFileOffsetsPerTermId =
                    docId2TermVecFileOffsets.computeIfAbsent(docId, k -> new RelationIdPerAttrSetOffset());
            vecFileOffsetsPerTermId.put(attributeSetOffset, tempFilePointer);
        }

        void updateMaxRelationId(int docId, int relationId) {
            docId2MaxRelationId.compute(docId, (k, v) -> v == null ? relationId : Math.max(v, relationId));
        }

        RelationIdPerAttrSetOffset get(int docId) {
            RelationIdPerAttrSetOffset termVecRelationIdPerAttrSetOffset = docId2TermVecFileOffsets.get(docId);
            if (termVecRelationIdPerAttrSetOffset == null) {
                termVecRelationIdPerAttrSetOffset = new RelationIdPerAttrSetOffset();
            }
            return termVecRelationIdPerAttrSetOffset;
        }
    }

    /** Our PostingsWriter, used for reading/writing our files */
    private final BlackLab40PostingsWriter postingsWriter;

    /** Lucene fields that we'll store relation info for */
    private Map<String, RelationInfoFieldMutable> riFields = new HashMap<>();

    /**
     * Information per unique relation id.
     * for each document and relation id: offset in attrset file
     */
    private final IndexOutput outDocsFile;

    /**
     * Information per unique relation id.
     * for each document and relation id: offset in attrset file
     */
    private final IndexOutput outRelationsFile;

    /**
     * Attribute sets files.
     * Contains:
     * - list of attribute names (will be read into memory on index opening)
     * - for each attribute in each set: attr name index and index in attr string offsets file
     */
    private final IndexOutput outAttrSetsFile;

    /**
     * All the attribute names (will be read into memory on index opening)
     */
    private final IndexOutput outAttrNamesFile;

    /**
     * Attribute values (strings)
     */
    private final IndexOutput outAttrValuesFile;

    /**
     * Index of attribute name in attrnames file
     */
    private final Map<String, Integer> indexFromAttributeName = new HashMap<>();

    /** Offsets of attribute values in attrvalues file */
    private final Map<String, Long> attrValueOffsets = new HashMap<>();

    /** Temporary file with attribute set id per term that will be converted into relations file per doc later. */
    private IndexOutput outTempRelationsFile;

    /** Field we're currently processing. */
    private RelationInfoFieldMutable currentField;

    /** Offsets of attribute set in the attrsets file.
     * The key is a sorted map of attribute name index to attribute value offset.
     * The value is the offset in the attrsets file.
     */
    private Map<SortedMap<Integer, Long>, Long> attributeSetOffsets = new HashMap<>();

    /**
     * Number of relations in each doc, per field (e.g. "contents%_relation@s").
     *
     * We keep track of number of relations so we can preallocate our relations offsets structure
     * when writing the final relations file.
     */
    private final Map<String, Map<Integer, Integer>> maxRelationIdsPerAnnotatedField = new HashMap<>();

    /** For each field and document id, record the temp relations file offsets (list of longs). */
    private final Map<String, OffsetsAndMaxRelationIdPerDocument> field2docTempRelFileOffsets = new LinkedHashMap<>();


    // PER FIELD

    /** List of offsets in temp relations file for each doc id */
    private OffsetsAndMaxRelationIdPerDocument offsetsAndMaxRelationIdPerDocument;


    // PER TERM

    /** Offset of attribute set for current term in attrset file */
    private long currentTermAttrSetOffset = -1;


    // PER DOCUMENT

    /** Doc id of current document */
    private int currentDocId;

    private byte[] currentDocPositionsArray;

    private DataOutput currentDocPositionsOutput;

    private int currentDocOccurrencesWritten;

    PWPluginRelationInfo(BlackLab40PostingsWriter postingsWriter) throws IOException {
        this.postingsWriter = postingsWriter;

        outDocsFile = postingsWriter.createOutput(BlackLab40PostingsFormat.RI_DOCS_EXT);
        outRelationsFile = postingsWriter.createOutput(BlackLab40PostingsFormat.RI_RELATIONS_EXT);
        outAttrSetsFile = postingsWriter.createOutput(BlackLab40PostingsFormat.RI_ATTR_SETS_EXT);
        outAttrNamesFile = postingsWriter.createOutput(BlackLab40PostingsFormat.RI_ATTR_NAMES_EXT);
        outAttrValuesFile = postingsWriter.createOutput(BlackLab40PostingsFormat.RI_ATTR_VALUES_EXT);
        outTempRelationsFile = postingsWriter.createOutput(BlackLab40PostingsFormat.RI_RELATIONS_TMP_EXT);
    }

    @Override
    public boolean startField(FieldInfo fieldInfo) {
        // Is this the relation annotation? Then we want to store relation info such as attribute values,
        // so we can look them up for individual relations matched.
        if (!BlackLabIndexIntegrated.isRelationsField(fieldInfo))
            return false;

        currentField = riFields.computeIfAbsent(fieldInfo.name, RelationInfoFieldMutable::new);

        Map<Integer, Integer> maxRelationIds = maxRelationIdsPerAnnotatedField.computeIfAbsent(
                fieldInfo.name, __ -> new HashMap<>());

        offsetsAndMaxRelationIdPerDocument = field2docTempRelFileOffsets.computeIfAbsent(fieldInfo.name, __ -> new OffsetsAndMaxRelationIdPerDocument(maxRelationIds));

        return true;
    }

    /** Look up this attribute set's offset, storing the attribute set if this is the first time we see it */
    private long getAttributeSetOffset(SortedMap<Integer, Long> currentTermAttributes) {
        return attributeSetOffsets.computeIfAbsent(currentTermAttributes, k -> {
            try {
                long attributeSetOffset = outAttrSetsFile.getFilePointer();
                outAttrSetsFile.writeVInt(currentTermAttributes.size());
                for (Entry<Integer, Long> e: currentTermAttributes.entrySet()) {
                    outAttrSetsFile.writeVInt(e.getKey());    // attribute name id
                    outAttrSetsFile.writeLong(e.getValue()); // attribute value offset
                }
                return attributeSetOffset;
            } catch (IOException e1) {
                throw new BlackLabRuntimeException(e1);
            }
        });
    }

    /** Look up the attribute value offset, storing the attribute name if this is the first time we see it */
    private long getAttributeValueOffset(String attrValue) {
        long attrValueOffset = attrValueOffsets.computeIfAbsent(attrValue, k -> {
            try {
                long offset = outAttrValuesFile.getFilePointer();
                outAttrValuesFile.writeString(attrValue);
                return offset;
            } catch (IOException e1) {
                throw new BlackLabRuntimeException(e1);
            }
        });
        return attrValueOffset;
    }

    /** Look up the attribute name index, storing the attribute name if this is the first time we see it */
    private int getAttributeIndex(String attrName) {
        return indexFromAttributeName.computeIfAbsent(attrName, k -> {
            try {
                outAttrNamesFile.writeString(attrName);
                return indexFromAttributeName.size(); // map size before adding == attribute index
            } catch (IOException e1) {
                throw new BlackLabRuntimeException(e1);
            }
        });
    }

    @Override
    public void startTerm(BytesRef term) {
        String termStr = term.utf8ToString();
        boolean ignoreTerm = RelationUtil.isOptimizationTerm(termStr);
        if (!ignoreTerm) {
            // Decode the term so we can store the attribute values and refer to them from each occurrence by relation id.
            // (we determine the correct offset to look up the attributes starting from the attribute set file)
            Map<String, String> attributes = RelationUtil.attributesFromIndexedTerm(termStr);
            SortedMap<Integer, Long> currentTermAttributes = new TreeMap<>();
            for (Entry<String, String> e: attributes.entrySet()) {
                int attributeIndex = getAttributeIndex(e.getKey());
                long attributeValueOffset = getAttributeValueOffset(e.getValue());
                currentTermAttributes.put(attributeIndex, attributeValueOffset);
            }
            // determine offset in attribute set file which we can refer to from each occurrence
            currentTermAttrSetOffset = getAttributeSetOffset(currentTermAttributes);
        }
    }

    @Override
    public void startDocument(int docId, int nOccurrences) {
        // Keep track of term positions offsets in term vector file
        this.currentDocId = docId;
        currentDocPositionsArray = new byte[Integer.BYTES * nOccurrences];
        currentDocPositionsOutput = new ByteArrayDataOutput(currentDocPositionsArray);
        currentDocOccurrencesWritten = 0;
    }

    @Override
    public void termOccurrence(int position, BytesRef payload) throws IOException {
        if (payload == null)
            return;
        // Get the relation id from the payload and store the offset to this term's attribute value set.
        // We could also store other info about this occurrence here, such as info about an inline tag's parent and
        // children.
        ByteArrayDataInput dataInput = PayloadUtils.getDataInput(payload.bytes, false);
        int relationId = RelationInfo.getRelationId(dataInput);
        if (relationId >= 0) {
            offsetsAndMaxRelationIdPerDocument.updateMaxRelationId(currentDocId, relationId);
            currentDocPositionsOutput.writeInt(relationId);
            currentDocOccurrencesWritten++;
        }
    }

    @Override
    public void endDocument() throws IOException {
        // Write the unique relation ids for this term in this document to the temp file
        // (will be reversed later to create the final relations file)
        offsetsAndMaxRelationIdPerDocument.putTempFileOffset(currentDocId, currentTermAttrSetOffset,
                outTempRelationsFile.getFilePointer());
        outTempRelationsFile.writeInt(currentDocOccurrencesWritten);
        if (currentDocOccurrencesWritten > 0) {
            outTempRelationsFile.writeBytes(currentDocPositionsArray, 0,
                    currentDocOccurrencesWritten * Integer.BYTES);
        }
    }

    @Override
    public void endTerm() {
        currentTermAttrSetOffset = -1;
    }

    @Override
    public void endField() throws IOException {
    }

    @Override
    public void finish() throws IOException {
        CodecUtil.writeFooter(outTempRelationsFile);
        outTempRelationsFile.close();
        outTempRelationsFile = null;

        // Reverse the reverse index to create forward index
        // (this time we iterate per field and per document first, then reconstruct the document by
        //  looking at each term's occurrences. This produces our forward index)
        try (IndexInput inTempRelationsFile = postingsWriter.openInput(BlackLab40PostingsFormat.RI_RELATIONS_TMP_EXT);
            IndexOutput fieldsFile = postingsWriter.createOutput(BlackLabPostingsFormat.RI_FIELDS_EXT)) {

            // For each field...
            for (Entry<String, OffsetsAndMaxRelationIdPerDocument> fieldEntry: field2docTempRelFileOffsets.entrySet()) {
                String luceneField = fieldEntry.getKey();
                OffsetsAndMaxRelationIdPerDocument docOffsets = fieldEntry.getValue();

                // Record starting offset of field in docs file
                currentField.setDocsOffset(outDocsFile.getFilePointer());
                currentField.write(fieldsFile);

                // Make sure we know our document lengths
                Map<Integer, Integer> maxRelationIds = maxRelationIdsPerAnnotatedField.get(luceneField);

                // For each document...
                for (int docId = 0; docId < postingsWriter.maxDoc(); docId++) {
                    // Reverse the temp relations file to create the final relations file
                    final int maxRelationId = maxRelationIds.getOrDefault(docId, 0);
                    writeRelationAttrSetOffsets(inTempRelationsFile, maxRelationId, docOffsets.get(docId));
                }
            }
        } finally {
            // Clean up after ourselves
            postingsWriter.deleteIndexFile(BlackLab40PostingsFormat.RI_RELATIONS_TMP_EXT);
        }

        // Write the footers to our files
        CodecUtil.writeFooter(outDocsFile);
        CodecUtil.writeFooter(outRelationsFile);
        CodecUtil.writeFooter(outAttrSetsFile);
        CodecUtil.writeFooter(outAttrNamesFile);
        CodecUtil.writeFooter(outAttrValuesFile);
    }

    /**
     * Find the attribute set offsets for each term in a document,
     * by reversing the temp relations file.
     *
     * This is aided by the map relationIdsPerAttrSet that maps from an "attribute
     * set offset" (that corresponds to a unique set of attribute values) to the offset
     * in the temporary relations file where the associated relationIds for those
     * attribute set offsets are stored.
     *
     * This is analogous to the forward index, where we have a temporary term vector file
     * (which records list of positions for each term) that we reverse to create the final tokens file
     * (which records term id for each position in each document).
     */
    private void writeRelationAttrSetOffsets(IndexInput inTempRelationsFile, int maxRelationId,
            RelationIdPerAttrSetOffset relationIdsPerAttrSet)
            throws IOException {

        // Will records the attribute set offset for each relation id in this document
        // (the "relation forward index" for the document, not position-based but relationId-based)
        final long[] offsetsInDoc = new long[maxRelationId + 1];

        // NOTE: sometimes docs won't have any values for a field, but we'll
        //   still write all NO_TERMs in this case. This is similar to sparse
        //   fields (e.g. the field that stores <p> <s> etc.) which also have a
        //   lot of NO_TERMs.
        Arrays.fill(offsetsInDoc, Terms.NO_TERM);

        // For each term...
        for (Entry<Long, Long> entry: relationIdsPerAttrSet.entrySet()) {
            long attributeSetOffset = entry.getKey();

            // Position the tmep relations file at the offset where the occurrences (relation ids) for
            // this attribute set in this document are stored.
            Long tempRelationsFileOffset = entry.getValue();
            inTempRelationsFile.seek(tempRelationsFileOffset);

            // Record the attribute set offset at each of the relation ids it occurs at in this document.
            int nOccurrences = inTempRelationsFile.readInt();
            for (int i = 0; i < nOccurrences; i++) {
                int relationId = inTempRelationsFile.readInt();
                offsetsInDoc[relationId] = attributeSetOffset;
            }
        }

        // Write offset in the relations file for this document.
        outDocsFile.writeLong(outRelationsFile.getFilePointer());

        // Write the attribute set offsets per relation to the relations file
        for (long offset: offsetsInDoc) {
            outRelationsFile.writeLong(offset);
        }
    }

    @Override
    public void close() throws IOException {
        // Close the index files we've been writing to
        if (outTempRelationsFile != null) {
            outTempRelationsFile.close();
            outTempRelationsFile = null;
        }
        outDocsFile.close();
        outRelationsFile.close();
        outAttrSetsFile.close();
        outAttrNamesFile.close();
        outAttrValuesFile.close();
    }
}
