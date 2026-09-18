package nl.inl.blacklab.index.annotated;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldImpl;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.indexmetadata.SourceUnitCodec;

/**
 * An annotated field is like a Lucene field, but in addition to its "normal"
 * value, it can have multiple annnotations per word (not just a single token).
 * The annotations might be "headword", "pos" (part of speech), "namedentity"
 * (whether or not the word is (part of) a named entity like a location or
 * place), etc.
 * <p>
 * Annotated fields are implemented by indexing a field in Lucene for each
 * annotation. For example, if annotated field "contents" has annotations "headword"
 * and "pos", there would be 3 Lucene fields for the annotated field: "contents",
 * "contents%headword@s" and "contents%pos@s" (@s means sensitive; multiple sensitivity
 * alternatives may be indexed for an annotation).
 * <p>
 * The main field ("contents" in the above example) may include offset
 * information if you want (e.g. for highlighting). All Lucene fields will
 * include position information (for use with SpanQueries).
 * <p>
 * N.B. It is crucial that everything stays in synch, so you should call all the
 * appropriate add*() methods for each annotation and each token, or use the
 * correct position increments to keep everything synched up. The same goes for
 * addStartChar() and addEndChar() (although, if you don't want any offsets, you
 * need not call these).
 */
public class AnnotatedFieldWriter {

    public enum TokenOffsetStorage {
        NATIVE_OFFSETS,
        SOURCE_RANGE_VECTORS
    }

    protected static final Logger logger = LogManager.getLogger(AnnotatedFieldWriter.class);

    public static final String MSG_IS_DISCOURAGED_REASON = "' is discouraged (field/annotation names should be valid XML element names)";

    private final Map<String, AnnotationWriter> annotations = new HashMap<>();

    private final DocWriter docWriter;

    private MutableIntList start = new IntArrayList();

    private MutableIntList end = new IntArrayList();

    private MutableIntList sourceUnits;

    private final String fieldName;

    private final AnnotationWriter mainAnnotation;

    private final String defaultSearchAnnotation;

    private final Set<String> noForwardIndexAnnotations = new HashSet<>();

    private final boolean needsPrimaryValuePayloads;

    private final boolean sourceRangeVectors;

    private int realTokenCount;

    private AnnotatedField field;

    /** The name of the annotation where relations and spans are stored. */
    private final String relationAnnotationName;

    /** How we index relations */
    private final RelationsStrategy relationsStrategy;

    public void setNoForwardIndexProps(Set<String> noForwardIndexAnnotations) {
        this.noForwardIndexAnnotations.clear();
        this.noForwardIndexAnnotations.addAll(noForwardIndexAnnotations);
    }

    /**
     * Construct a AnnotatedFieldWriter object with a main annotation.
     * <p>
     * NOTE: right now, the main annotation will always have a forward index.
     * Maybe make this configurable?
     *
     * @param name field name
     * @param mainAnnotationName main annotation name (e.g. "word")
     * @param sensitivity ways to index main annotation, with respect to case- and
     *            diacritics-sensitivity.
     * @param mainPropHasPayloads does the main annotation have payloads?
     * @param needsPrimaryValuePayloads should payloads indicate whether value is primary or not?
     */
    public AnnotatedFieldWriter(DocWriter docWriter, String name, String mainAnnotationName, AnnotationSensitivities sensitivity,
            boolean mainPropHasPayloads, boolean needsPrimaryValuePayloads, String defaultSearchAnnotation) {
        this(docWriter, name, mainAnnotationName, sensitivity, docWriter.metadata().usesSourceRangeVectors() ?
                        TokenOffsetStorage.SOURCE_RANGE_VECTORS : TokenOffsetStorage.NATIVE_OFFSETS,
                mainPropHasPayloads, needsPrimaryValuePayloads, defaultSearchAnnotation);
    }

    /** Construct a writer with explicit control over token source-range storage. */
    public AnnotatedFieldWriter(DocWriter docWriter, String name, String mainAnnotationName,
            AnnotationSensitivities sensitivity, TokenOffsetStorage tokenOffsetStorage, boolean mainPropHasPayloads,
            boolean needsPrimaryValuePayloads, String defaultSearchAnnotation) {
        this.docWriter = docWriter;
        relationsStrategy = docWriter.getRelationsStrategy();
        relationAnnotationName = AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME;
        fieldName = name;
        this.needsPrimaryValuePayloads = needsPrimaryValuePayloads;
        sourceRangeVectors = tokenOffsetStorage == TokenOffsetStorage.SOURCE_RANGE_VECTORS;
        if (sourceRangeVectors && !docWriter.indexObjectFactory().supportsSourceRangeVectors())
            throw new UnsupportedOperationException("Index backend does not support source-range vectors");
        mainAnnotation = new AnnotationWriter(this, mainAnnotationName, sensitivity, !sourceRangeVectors,
                mainPropHasPayloads, needsPrimaryValuePayloads);
        annotations.put(mainAnnotationName, mainAnnotation);
        this.defaultSearchAnnotation = defaultSearchAnnotation == null ? mainAnnotationName : defaultSearchAnnotation;
    }

    public int numberOfTokens() {
        return sourceRangeVectors ? realTokenCount + 1 : start.size();
    }

    public AnnotationWriter addAnnotation(String name, AnnotationSensitivities sensitivity, boolean includePayloads,
            boolean createForwardIndex) {
        AnnotationWriter p = new AnnotationWriter(this, name, sensitivity, false, includePayloads,
                needsPrimaryValuePayloads);
        if (noForwardIndexAnnotations.contains(name) || !createForwardIndex) {
            p.setHasForwardIndex(false);
        }
        annotations.put(name, p);
        return p;
    }

    /**
     * Add a final start/end char identical to the one before.
     * <p>
     * Used for the final dummy token. When this is added, we've processed other
     * fields in the meantime, so our character position is unavailable.
     */
    public void addFinalStartEndChars() {
        if (!sourceRangeVectors) {
            start.add(start.isEmpty() ? 0 : start.get(start.size() - 1));
            end.add(end.isEmpty() ? 0 : end.get(end.size() - 1));
        }
    }

    public void addStartChar(int startChar) {
        assert sourceRangeVectors || start.isEmpty() || startChar >= start.get(start.size() - 1);
        start.add(startChar);
    }

    public void addEndChar(int endChar) {
        // Nested word tags can cause decreasing legacy ends, which native Lucene offsets cannot represent.
        if (!sourceRangeVectors && !end.isEmpty() && endChar < end.get(end.size() - 1))
            endChar = end.get(end.size() - 1);

        end.add(endChar);
        if (sourceRangeVectors)
            realTokenCount++;
    }

    public void addSourceUnit(int sourceStart, int sourceEnd, int tokenEnd) {
        assert sourceRangeVectors;
        assert sourceUnits != null;
        sourceUnits.add(sourceStart);
        sourceUnits.add(sourceEnd);
        sourceUnits.add(tokenEnd);
    }

    public void enableSourceUnits() {
        assert sourceRangeVectors;
        if (sourceUnits == null)
            sourceUnits = new IntArrayList();
    }

    public void addToDoc(BLInputDocument doc) {
        if (sourceRangeVectors) {
            if (start.size() != realTokenCount || end.size() != realTokenCount)
                throw new IllegalStateException("Source range count does not match real token count for field " + fieldName);
            if (realTokenCount > 0) {
                doc.addAnnotationField(AnnotatedFieldNameUtil.sourceRangesField(fieldName),
                        new TokenStreamSourceRanges(start, end), docWriter.indexObjectFactory().fieldTypeSourceRanges());
            }
            if (sourceUnits != null)
                doc.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(fieldName),
                        SourceUnitCodec.encode(sourceUnits));
        }
        for (AnnotationWriter p : annotations.values()) {
            p.addToDoc(doc, fieldName, start, end);
        }

        // Add number of tokens in annotated field as a stored field,
        // because we need to be able to find this annotation quickly
        // for SpanQueryNot.
        // (Also note that this is the actual number of words + 1,
        //  because we always store a "extra closing token" at the end
        //  that doesn't contain a word but may contain trailing punctuation)
        String lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);
        int lengthTokensValue = numberOfTokens();
        doc.addStoredNumericField(lengthTokensFieldName, lengthTokensValue, true);
    }

    /**
     * Clear the internal state for reuse.
     */
    public void clear() {
        // Don't reuse buffers, reclaim memory so we don't run out
        start = new IntArrayList();
        end = new IntArrayList();
        sourceUnits = null;
        realTokenCount = 0;

        for (AnnotationWriter p : annotations.values()) {
            p.clear();
        }
    }

    public AnnotationWriter annotation(String name) {
        return annotations.get(name);
    }

    public boolean hasAnnotation(String name) {
        return annotations.containsKey(name);
    }

    public AnnotationWriter mainAnnotation() {
        return mainAnnotation;
    }

    public String defaultSearchAnnotation() {
        return defaultSearchAnnotation;
    }

    public AnnotationWriter tagsAnnotation() {
        AnnotationWriter rv = annotation(relationAnnotationName);
        if (rv == null) {
            throw new IllegalArgumentException("Undefined annotation '" + relationAnnotationName + "'");
        }
        return rv;
    }

    public AnnotationWriter punctAnnotation() {
        AnnotationWriter rv = annotation(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME);
        if (rv == null) {
            throw new IllegalArgumentException("Undefined annotation '" + AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME + "'");
        }
        return rv;
    }

    public String name() {
        return fieldName;
    }

    public Collection<AnnotationWriter> annotationWriters() {
        return annotations.values();
    }

    public void setAnnotatedField(AnnotatedField field) {
        this.field = field;
        // If the indexmetadata file specified a list of annotations that shouldn't get a forward
        // index, we need to know.
        AnnotatedFieldImpl fieldImpl = (AnnotatedFieldImpl)field;
        setNoForwardIndexProps(fieldImpl.getNoForwardIndexAnnotations());
    }

    public AnnotatedField field() {
        return field;
    }

    @Override
    public String toString() {
        return "AnnotatedFieldWriter(" + fieldName + ")";
    }

    public RelationsStrategy getRelationsStrategy() {
        return relationsStrategy;
    }

    public DocWriter getDocWriter() {
        return docWriter;
    }
}
