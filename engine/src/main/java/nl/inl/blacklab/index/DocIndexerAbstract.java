package nl.inl.blacklab.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldImpl;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.indexmetadata.UnknownCondition;
import nl.inl.util.StringUtil;

/**
 * Indexes a file.
 */
public abstract class DocIndexerAbstract implements DocIndexer {

    protected static final Logger logger = LogManager.getLogger(DocIndexerAbstract.class);

    private DocWriter docWriter;

    private RelationsStrategy relationsStrategy;

    /**
     * File we're currently parsing. This can be useful for storing the original
     * filename in the index.
     */
    protected String documentName;

    /**
     * The Lucene Document we're currently constructing (corresponds to the document
     * we're indexing)
     */
    protected BLInputDocument currentDoc;

    /**
     * Document metadata. Added at the end to deal with unknown values, multiple occurrences
     * (only the first is actually indexed, because of DocValues, among others), etc.
     */
    protected Map<String, List<String>> metadataFieldValues = new HashMap<>();

    /** How many documents we've processed */
    private int numberOfDocsDone = 0;

    /** How many tokens we've processed */
    private int numberOfTokensDone = 0;

    @Override
    public BLInputDocument getCurrentDoc() {
        return currentDoc;
    }

    /**
     * Returns our DocWriter object
     *
     * @return the DocWriter object
     */
    @Override
    public DocWriter getDocWriter() {
        return docWriter;
    }

    /**
     * Set the DocWriter object.
     *
     * We use this to add documents to the index.
     *
     * Called by Indexer when the DocIndexer is instantiated.
     *
     * @param docWriter our DocWriter object
     */
    @Override
    public void setDocWriter(DocWriter docWriter) {
        this.docWriter = docWriter;
        this.relationsStrategy = docWriter.getRelationsStrategy();
    }

    /**
     * Set the file name of the document to index.
     *
     * @param documentName name of the document
     */
    @Override
    public void setDocumentName(String documentName) {
        this.documentName = documentName == null ? "?" : documentName;
    }

   protected BLFieldType luceneTypeFromIndexMetadataType(FieldType type) {
       return switch (type) {
           case NUMERIC -> throw new IllegalArgumentException("Numeric types should be indexed using IntField, etc.");
           case TOKENIZED -> getDocWriter().metadataFieldType(true);
           case UNTOKENIZED -> getDocWriter().metadataFieldType(false);
           default -> throw new IllegalArgumentException("Unknown field type: " + type);
       };
   }

    @Override
    public boolean continueIndexing() {
        return getDocWriter().continueIndexing();
    }

    protected void warn(String msg) {
        getDocWriter().listener().warning(msg);
    }

    @Override
    public List<String> getMetadataField(String name) {
        return metadataFieldValues.get(name);
    }

    @Override
    public void addMetadataField(String name, String value) {
        name = optTranslateFieldName(name);

        if (!AnnotatedFieldNameUtil.isValidXmlElementName(name))
            logger.warn("Field name '" + name
                    + "' is discouraged (field/annotation names should be valid XML element names)");

        if (name == null || value == null) {
            warn("Incomplete metadata field: " + name + "=" + value + " (skipping)");
            return;
        }

        value = StringUtil.trimWhitespace(value);
        if (!value.isEmpty()) {
            metadataFieldValues.computeIfAbsent(name, __ -> new ArrayList<>()).add(value);
            IndexMetadataWriter indexMetadata = getDocWriter().metadata();
            indexMetadata.registerMetadataField(name);
        }
    }

    /**
     * Translate a field name before adding it.
     *
     * By default, simply returns the input. May be overridden to change the name of
     * a metadata field as it is indexed.
     *
     * @param from original metadata field name
     * @return new name
     */
    protected String optTranslateFieldName(String from) {
        return from;
    }

    /**
     * When all metadata values have been set, call this to add the to the Lucene document.
     *
     * We do it this way because we don't want to add multiple values for a field (DocValues and
     * Document.get() only deal with the first value added), and we want to set an "unknown value"
     * in certain conditions, depending on the configuration.
     */
    @Override
    public void addMetadataToDocument() {
        // See what metadatafields are missing or empty and add unknown value if desired.
        IndexMetadataWriter indexMetadata = getDocWriter().metadata();
        Map<String, String> unknownValuesToUse = new HashMap<>();
        List<String> fields = indexMetadata.metadataFields().names();
        for (String field: fields) {
            MetadataField fd = indexMetadata.metadataField(field);
            if (fd.type() == FieldType.NUMERIC)
                continue;
            boolean missing = false, empty = false;
            List<String> currentValue = getMetadataField(fd.name());
            if (currentValue == null)
                missing = true;
            else if (currentValue.isEmpty() || currentValue.stream().allMatch(String::isEmpty))
                empty = true;
            UnknownCondition cond = UnknownCondition.fromStringValue(fd.custom().get("unknownCondition", "never"));
            boolean useUnknownValue = false;
            switch (cond) {
            case EMPTY:
                useUnknownValue = empty;
                break;
            case MISSING:
                useUnknownValue = missing;
                break;
            case MISSING_OR_EMPTY:
                useUnknownValue = missing || empty;
                break;
            case NEVER:
                // (useUnknownValue is already false)
                break;
            }
            if (useUnknownValue) {
                if (empty) {
                    // Don't count this as a value, count the unknown value
                    for (String value: currentValue) {
                        ((MetadataFieldImpl) indexMetadata.metadataFields().get(fd.name())).removeValue(value);
                    }
                }
                unknownValuesToUse.put(fd.name(), fd.custom().get("unknownValue", "unknown"));
            }
        }
        for (Entry<String, String> e: unknownValuesToUse.entrySet()) {
            metadataFieldValues.put(e.getKey(), List.of(e.getValue()));
        }
        // Index the metadata fields in order of increasing size, so that the largest
        // field is last.
        // (see https://lucene.apache.org/core/9_0_0/changes/Changes.html
        // LUCENE-6898: In the default codec, the last stored field value will not be fully read from disk if the supplied
        // StoredFieldVisitor doesn't want it. So put your largest text field value last to benefit.)
        List<Entry<String, List<String>>> entries = metadataFieldValues.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().stream().map(String::length).reduce(0, Integer::sum)))
                .toList();
        for (Entry<String, List<String>> e: entries) {
            addMetadataFieldToDocument(e.getKey(), e.getValue());
        }
        metadataFieldValues.clear();
    }

    public void addMetadataFieldToDocument(String name, List<String> values) {
        IndexMetadataWriter indexMetadata = getDocWriter().metadata();
        //indexMetadata.registerMetadataField(name);

        MetadataFieldImpl desc = (MetadataFieldImpl) indexMetadata.metadataFields().get(name);

        FieldType type = desc.type();
        if (type != FieldType.NUMERIC) {
            for (String value: values) {
                currentDoc.addTextualMetadataField(name, value, this.luceneTypeFromIndexMetadataType(type));
            }
        }
        if (type == FieldType.NUMERIC) {
            String numFieldName = name;
            if (type != FieldType.NUMERIC) {
                numFieldName += "Numeric";
            }

            boolean firstValue = true;
            for (String value: values) {
                // Index these fields as numeric too, for faster range queries
                // (we do both because fields sometimes aren't exclusively numeric)
                int n;
                try {
                    n = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    // This just happens sometimes, e.g. given multiple years, or
                    // descriptive text like "around 1900". OK to ignore.
                    n = 0;
                }
                currentDoc.addStoredNumericField(numFieldName, n, firstValue);
                if (!firstValue) {
                    warn(documentName + " contains multiple values for single-valued numeric field " + numFieldName
                            + "(values: " + StringUtils.join(values, "; ") + ")");
                }
                firstValue = false;
            }
        }
    }

    /**
     * Character position within the current document.
     */
    protected abstract int getCharacterPosition();

    /** For parallel corpora where a document has multiple versions,
      * this is the character position within the version. For other
      * corpora, this is the same as {@link #getCharacterPosition()}.
      */
    protected int getCharacterPositionWithinVersion() {
        return getCharacterPosition();
    }

    /**
     * Keep track of how many tokens have been processed.
     */
    @Override
    public void documentDone(String documentName) {
        numberOfDocsDone++;
        getDocWriter().listener().documentDone(documentName);

        // Force a merge after each document? (debug feature)
        if (Boolean.parseBoolean(BlackLab.featureFlag(BlackLab.FEATURE_DEBUG_FORCE_MERGE)))
            docWriter.debugForceMerge();
    }

    /**
     * Keep track of how many tokens have been processed.
     */
    @Override
    public void tokensDone(int n) {
        numberOfTokensDone += n;
        getDocWriter().listener().tokensDone(n);
    }

    @Override
    public int numberOfDocsDone() {
        return numberOfDocsDone;
    }

    @Override
    public long numberOfTokensDone() {
        return numberOfTokensDone;
    }

    protected BLInputDocument createNewDocument() {
        return getDocWriter().indexObjectFactory().createInputDocument();
    }

    /** Get the strategy to use for indexing relations. */
    public RelationsStrategy getRelationsStrategy() {
        return relationsStrategy;
    }

    public RelationsStrategy.PayloadCodec getPayloadCodec() {
        return relationsStrategy.getPayloadCodec();
    }
}
