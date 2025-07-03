package nl.inl.blacklab.codec;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategySeparateTerms;
import nl.inl.blacklab.search.lucene.RelationInfo;

/**
 * Hook into the postings writer to write the relation info.
 * <p>
 * Keeps track of attributes per unique relation id and writes them to the relation info
 * files so we can look them up later.
 */
public class PWPluginRelationInfo implements PWPlugin {

    /** Log all events to a log file? Useful while debugging. */
    private static final boolean ENABLE_DEBUG_LOG = false;

    /** Our log file (if enabled) */
    private PrintWriter logFile = null;

    /** Lucene fields that we'll store relation info for */
    private final Map<String, RelationInfoFieldMutable> riFields = new HashMap<>();

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
    private final Map<SortedMap<Integer, Long>, Long> attributeSetOffsets = new HashMap<>();


    // PER FIELD


    // PER TERM

    /** Should we ignore the current term? (because it's a duplicate "optimization" term) */
    private boolean ignoreCurrentTerm = false;

    /** Attribute(s) found in current term (key is attribute index, value is offset in value file)
     * <p>
     * NOTE: once we drop support for {@link nl.inl.blacklab.search.indexmetadata.RelationsStrategySingleTerm},
     * this doesn't need to be a map anymore, as there can only be 1 attribute per term.
     */
    private final SortedMap<Integer, Long> currentTermAttributes = new TreeMap<>();


    // PER DOCUMENT

    /** How we should index the relations */
    private final RelationsStrategySeparateTerms relationsStrategy;

    /** How to encode/decode payload for relations */
    private final RelationsStrategy.PayloadCodec relPayloadCodec;

    public PWPluginRelationInfo(BlackLabPostingsWriter postingsWriter, RelationsStrategySeparateTerms relationsStrategy) throws IOException {
        this.relationsStrategy = relationsStrategy;
        this.relPayloadCodec = relationsStrategy.getPayloadCodec();

        outFieldsFile = postingsWriter.createOutput(BlackLabPostingsFormat.RI_FIELDS_EXT);
        outDocsFile = postingsWriter.createOutput(BlackLabPostingsFormat.RI_DOCS_EXT);
        outRelationsFile = postingsWriter.createOutput(BlackLabPostingsFormat.RI_RELATIONS_EXT);
        outAttrSetsFile = postingsWriter.createOutput(BlackLabPostingsFormat.RI_ATTR_SETS_EXT);
        outAttrNamesFile = postingsWriter.createOutput(BlackLabPostingsFormat.RI_ATTR_NAMES_EXT);
        outAttrValuesFile = postingsWriter.createOutput(BlackLabPostingsFormat.RI_ATTR_VALUES_EXT);

        // Open a log file
        if (ENABLE_DEBUG_LOG) {
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            String name = "relation_info_" + postingsWriter.getSegmentName() + ".log";
            logFile = new PrintWriter(new FileWriter(new File(tempDir, name)));
        }
    }

    public void log(String msg) {
        if (logFile != null) {
            logFile.println(msg);
            logFile.flush();
        }
    }

    @Override
    public boolean startField(FieldInfo fieldInfo) {

        // Is this the relation annotation? Then we want to store relation info such as attribute values,
        // so we can look them up for individual relations matched.
        if (!BlackLabIndexIntegrated.isRelationsField(fieldInfo)) {
            log("startField: skip non-rel field " + fieldInfo.name);
            return false;
        }

        log("startField: processing field " + fieldInfo.name);

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
                throw new RuntimeException(e1);
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
                throw new RuntimeException(e1);
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
                throw new RuntimeException(e1);
            }
        });
    }

    @Override
    public void startTerm(BytesRef term) {
        String termStr = term.utf8ToString();
        this.ignoreCurrentTerm = !termStr.startsWith(RelationsStrategySeparateTerms.RELATION_INFO_TERM_PREFIX);
        if (!ignoreCurrentTerm) {
            log("  startTerm: processing term '" + termStr + "'");
            // Decode the term so we have the attribute(s) index and value offset. We need these for each occurrence.
            this.currentTermAttributes.clear();
            relationsStrategy.parseRelationInfoTerm(termStr, (attrName, attrValues) -> {
                int attributeIndex = getAttributeIndex(attrName);
                assert !currentTermAttributes.containsKey(attributeIndex) : "duplicate attribute index";
                String value = StringUtils.join(attrValues, RelationsStrategySeparateTerms.ATTR_VALUE_SEPARATOR);
                long attributeValueOffset = getAttributeValueOffset(value);
                assert attributeValueOffset >= 0 : "negative attribute value offset";
                currentTermAttributes.put(attributeIndex, attributeValueOffset);
            });
        } else {
            log("  startTerm: ignoring: " + termStr);
        }
    }

    @Override
    public void startDocument(int docId, int nOccurrences) {
        log("    startDocument: " + docId + " (" + nOccurrences + " occurrences)");
        // Keep track of relation ids in relations file
        attrPerRelationId = attrPerRelationIdPerDoc.computeIfAbsent(docId, __ -> new TreeMap<>());
        if (relationIdsSeenPerDoc != null)
            relationIdsSeen = relationIdsSeenPerDoc.computeIfAbsent(docId, __ -> new TreeMap<>());
    }

    /** Relation Ids seen for all documents, and their position in doc. Only used when assertions are enabled. */
    Map<Integer, SortedMap<Integer, Integer>> relationIdsSeenPerDoc;

    /** Relation ids (and position in doc) seen for current doc. Only used when assertions are enabled. */
    SortedMap<Integer, Integer> relationIdsSeen;

    @Override
    public void termOccurrence(int position, BytesRef payload) {
        if (payload == null) {
            log("      occurrence at position " + position + " has null payload");
            return;
        }

        if (ignoreCurrentTerm) {
            // This is an optimization term that we cannot extract attributes from.
            // We need the non-optimization terms to create the relation id index.
            // Skip this.
            return;
        }

        // Get the relation id from the payload and check that it's valid
        ByteArrayDataInput dataInput = PayloadUtils.getDataInput(payload, false);
        int relationId = relPayloadCodec.readRelationId(dataInput);
        log("      occurrence at position " + position + " has relationId " + relationId);
        assert relationId >= 0 || relationId == RelationInfo.RELATION_ID_NO_INFO;

        // Store the offset to this term's attribute value set.
        // We could also store other info about this occurrence here, such as info about an inline tag's parent and
        // children.
        if (relationId >= 0) {
            if (relationIdsSeen != null) {
                Integer prevPos = relationIdsSeen.get(relationId);
                if (prevPos != null && prevPos != position) {
                    log("ERROR: Duplicate relationId " + relationId + " at position " + position);
                }
                relationIdsSeen.put(relationId, position);
                // Note that duplicates are normal (for attributes, using RelationsStrategyMultipleTerms)
            }

            // Add the attributes from this term to those for this relationId
            attrPerRelationId.computeIfAbsent(relationId, __ -> new TreeMap<>())
                    .putAll(currentTermAttributes);
        }
    }

    @Override
    public void endDocument() throws IOException {
        log("    endDocument");
        attrPerRelationId = null;
        relationIdsSeen = null;
    }

    @Override
    public void endTerm() {
        log("  endTerm");
        ignoreCurrentTerm = false;
    }

    @Override
    public void endField() throws IOException {
        log("endField");
        // Check that relationIdsSeen contains all relation ids (if assertions are enabled)
        if (relationIdsSeenPerDoc != null) {
            for (var e: relationIdsSeenPerDoc.entrySet()) {
                relationIdsSeen = e.getValue();
                int expectedRelationId = 0;
                for (int relationId: relationIdsSeen.keySet()) {
                    if (relationId != expectedRelationId) {
                        throw new AssertionError(
                                "Not all relationIds found in docId " + e.getKey() + " (expected " + expectedRelationId
                                        +
                                        ", got " + relationId + ")");
                    }
                    expectedRelationId++;
                }
            }
            relationIdsSeen = null;
            relationIdsSeenPerDoc = null;
        }

        // Record info about field: name and offset to docs file
        currentField.setDocsOffset(outDocsFile.getFilePointer());
        currentField.write(outFieldsFile);

        // For each doc...
        int expectedDocId = 0;
        for (var docEntry: attrPerRelationIdPerDoc.entrySet()) {
            // Some documents may not have relations. For example, the special internal metadata document never has any.
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
                if (rel.getKey() != expectedRelationId)
                    throw new AssertionError(
                            "Not all relationIds found (expected " + expectedRelationId + ", got " + rel.getKey()
                                    + ")");
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
        if (logFile != null)
            logFile.close();
        outFieldsFile.close();
        outDocsFile.close();
        outRelationsFile.close();
        outAttrSetsFile.close();
        outAttrNamesFile.close();
        outAttrValuesFile.close();
    }
}
