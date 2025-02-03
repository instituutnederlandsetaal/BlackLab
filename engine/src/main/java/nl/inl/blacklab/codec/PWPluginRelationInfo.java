package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.lucene.RelationInfo;

/**
 * Hook into the postings writer to write the relation info.
 *
 * Keeps track of attributes per unique relation id and writes them to the relation info
 * files so we can look them up later.
 */
class PWPluginRelationInfo implements PWPlugin {

    /** Lucene fields that we'll store relation info for */
    private Map<String, RelationInfoFieldMutable> riFields = new HashMap<>();

    /** Information about different fields we store relations for. */
    private final IndexOutput outFieldsFile;

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

    /** Attributes per relationId, per document (docId -> (relationId -> (attrIndex -> valueOffset))) */
    SortedMap<Integer, SortedMap<Integer, SortedMap<Integer, Long>>> attrPerRelationIdPerDoc;

    /** For current document: attributes per relationId (relationId -> (attrIndex -> valueOffset)) */
    SortedMap<Integer, SortedMap<Integer, Long>> attrPerRelationId;

    /** Field we're currently processing. */
    private RelationInfoFieldMutable currentField;

    /** Offsets of attribute set in the attrsets file.
     * The key is a sorted map of attribute name index to attribute value offset.
     * The value is the offset in the attrsets file.
     */
    private Map<SortedMap<Integer, Long>, Long> attributeSetOffsets = new HashMap<>();


    // PER FIELD


    // PER TERM

    /** Should we ignore the current term? (because it's a duplicate "optimization" term) */
    private boolean ignoreCurrentTerm = false;

    /** Attribute(s) found in current term (key is attribute index, value is offset in value file)
     *
     * NOTE: once we drop support for {@link nl.inl.blacklab.search.indexmetadata.RelationsStrategySingleTerm},
     * this doesn't need to be a map anymore, as there can only be 1 attribute per term.
     */
    private final SortedMap<Integer, Long> currentTermAttributes = new TreeMap<>();


    // PER DOCUMENT

    /** How we should index the relations */
    private final RelationsStrategy relationsStrategy;

    /** How to encode/decode payload for relations */
    private final RelationsStrategy.PayloadCodec relPayloadCodec;

    PWPluginRelationInfo(BlackLab40PostingsWriter postingsWriter, RelationsStrategy relationsStrategy) throws IOException {
        this.relationsStrategy = relationsStrategy;
        this.relPayloadCodec = relationsStrategy.getPayloadCodec();

        outFieldsFile = postingsWriter.createOutput(BlackLab40PostingsFormat.RI_FIELDS_EXT);
        outDocsFile = postingsWriter.createOutput(BlackLab40PostingsFormat.RI_DOCS_EXT);
        outRelationsFile = postingsWriter.createOutput(BlackLab40PostingsFormat.RI_RELATIONS_EXT);
        outAttrSetsFile = postingsWriter.createOutput(BlackLab40PostingsFormat.RI_ATTR_SETS_EXT);
        outAttrNamesFile = postingsWriter.createOutput(BlackLab40PostingsFormat.RI_ATTR_NAMES_EXT);
        outAttrValuesFile = postingsWriter.createOutput(BlackLab40PostingsFormat.RI_ATTR_VALUES_EXT);
    }

    @Override
    public boolean startField(FieldInfo fieldInfo) {
        // Is this the relation annotation? Then we want to store relation info such as attribute values,
        // so we can look them up for individual relations matched.
        if (!BlackLabIndexIntegrated.isRelationsField(fieldInfo))
            return false;

        currentField = riFields.computeIfAbsent(fieldInfo.name, RelationInfoFieldMutable::new);
        attrPerRelationIdPerDoc = new TreeMap<>();

        {
            // make sure this is instantiated if assertions are enabled
            //noinspection ConstantValue
            assert (relationIdsSeenPerDoc = new HashMap<>()) != null;
        }

        return true;
    }

    /** Look up this attribute set's offset, storing the attribute set if this is the first time we see it */
    private long getAttributeSetOffset(SortedMap<Integer, Long> currentTermAttributes) {
        return attributeSetOffsets.computeIfAbsent(currentTermAttributes, k -> {
            try {
                long attributeSetOffset = outAttrSetsFile.getFilePointer();
                outAttrSetsFile.writeVInt(currentTermAttributes.size());
                for (Entry<Integer, Long> e: currentTermAttributes.entrySet()) {
                    assert e.getKey() >= 0 : "negative attribute name id";
                    assert e.getValue() >= 0 : "negative attribute value offset";
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
        return attrValueOffsets.computeIfAbsent(attrValue, k -> {
            try {
                long offset = outAttrValuesFile.getFilePointer();
                outAttrValuesFile.writeString(attrValue);
                return offset;
            } catch (IOException e1) {
                throw new BlackLabRuntimeException(e1);
            }
        });
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
        this.ignoreCurrentTerm = relationsStrategy.isOptimizationTerm(termStr);
        if (!ignoreCurrentTerm) {
            // Decode the term so we have the attribute(s) index and value offset. We need these for each occurrence.
            this.currentTermAttributes.clear();
            relationsStrategy.attributesInTerm(termStr).forEach(e -> {
                int attributeIndex = getAttributeIndex(e.getKey());
                assert !currentTermAttributes.containsKey(attributeIndex) : "duplicate attribute index";
                long attributeValueOffset = getAttributeValueOffset(e.getValue());
                assert attributeValueOffset >= 0 : "negative attribute value offset";
                currentTermAttributes.put(attributeIndex, attributeValueOffset);
            });
        }
    }

    @Override
    public void startDocument(int docId, int nOccurrences) {
        // Keep track of relation ids in relations file
        attrPerRelationId = attrPerRelationIdPerDoc.computeIfAbsent(docId, __ -> new TreeMap<>());
        if (relationIdsSeenPerDoc != null)
            relationIdsSeen = relationIdsSeenPerDoc.computeIfAbsent(docId, __ -> new TreeSet<>());
    }

    /** Relation Ids seen for all documents. Only used when assertions are enabled. */
    Map<Integer, SortedSet<Integer>> relationIdsSeenPerDoc;

    /** Relation ids seen for current doc. Only used when assertions are enabled. */
    SortedSet<Integer> relationIdsSeen;

    @Override
    public void termOccurrence(int position, BytesRef payload) throws IOException {
        if (payload == null)
            return;

        // Get the relation id from the payload and check that it's valid
        ByteArrayDataInput dataInput = PayloadUtils.getDataInput(payload.bytes, false);
        int relationId = relPayloadCodec.readRelationId(dataInput);
        assert relationId >= 0 || relationId == RelationInfo.RELATION_ID_NO_INFO;

        if (ignoreCurrentTerm) {
            // This is an optimization term that we cannot extract attributes from.
            // We need the non-optimization terms to create the relation id index.
            // Skip this.
            return;
        }

        // Store the offset to this term's attribute value set.
        // We could also store other info about this occurrence here, such as info about an inline tag's parent and
        // children.
        if (relationId >= 0) {
            if (relationIdsSeen != null) {
                relationIdsSeen.add(relationId);
                // Note that duplicates are normal (for attributes, using RelationsStrategyMultipleTerms)
            }

            // Add the attributes from this term to those for this relationId
            attrPerRelationId.computeIfAbsent(relationId, __ -> new TreeMap<>())
                    .putAll(currentTermAttributes);
        }
    }

    @Override
    public void endDocument() throws IOException {
        attrPerRelationId = null;
        relationIdsSeen = null;
    }

    @Override
    public void endTerm() {
        ignoreCurrentTerm = false;
    }

    @Override
    public void endField() throws IOException {
        // Check that relationIdsSeen contains all relation ids (if assertions are enabled)
        if (relationIdsSeenPerDoc != null) {
            for (var e: relationIdsSeenPerDoc.entrySet()) {
                relationIdsSeen = e.getValue();
                int expectedRelationId = 0;
                for (int relationId: relationIdsSeen) {
                    assert relationId == expectedRelationId :
                            "Not all relationIds found in docId " + e.getKey() + " (expected " + expectedRelationId +
                                    ", got " + relationId + ")";
                    expectedRelationId++;
                }
            }
            relationIdsSeenPerDoc = null;
        }

        // Record info about field: name and offset to docs file
        currentField.setDocsOffset(outDocsFile.getFilePointer());
        currentField.write(outFieldsFile);

        // For each doc...
        int expectedDocId = 0;
        for (var docEntry: attrPerRelationIdPerDoc.entrySet()) {
            // Some documents may not have relations. For example, the special internal metadata document never has any)
            // We still need to write an entry for them in the docs file, or everything will break.
            while (expectedDocId < docEntry.getKey()) {
                outDocsFile.writeLong(-1); // no relations for this doc
                expectedDocId++;
            }
            assert docEntry.getKey() == expectedDocId : "Wrong docId!?";

            outDocsFile.writeLong(outRelationsFile.getFilePointer());
            int expectedRelationId = 0;
            // For each relationId...
            SortedMap<Integer, SortedMap<Integer, Long>> relationId2AttributeSet = docEntry.getValue();
            for (var rel: relationId2AttributeSet.entrySet()) {
                // Get attribute set offset (storing it if this is the first time we've seen it)
                // and write it to the relations file
                assert rel.getKey() == expectedRelationId : "Not all relationIds found (expected " + expectedRelationId + ", got " + rel.getKey() + ")";
                // mitigation for when assertions are disabled: write invalid values to the file to prevent desynching
                while (expectedRelationId < rel.getKey()) {
                    outRelationsFile.writeLong(RelationInfo.RELATION_ID_NO_INFO); // no info for this relation
                    expectedRelationId++;
                }
                long attrSetOffset = getAttributeSetOffset(rel.getValue());
                outRelationsFile.writeLong(attrSetOffset);
                expectedRelationId++;
            }
            expectedDocId++;
        }

        currentField = null;
        attrPerRelationIdPerDoc = null;
    }

    @Override
    public void finish() throws IOException {
        CodecUtil.writeFooter(outFieldsFile);
        CodecUtil.writeFooter(outDocsFile);
        CodecUtil.writeFooter(outRelationsFile);
        CodecUtil.writeFooter(outAttrSetsFile);
        CodecUtil.writeFooter(outAttrNamesFile);
        CodecUtil.writeFooter(outAttrValuesFile);

    }

    @Override
    public void close() throws IOException {
        outFieldsFile.close();
        outDocsFile.close();
        outRelationsFile.close();
        outAttrSetsFile.close();
        outAttrNamesFile.close();
        outAttrValuesFile.close();
    }
}
