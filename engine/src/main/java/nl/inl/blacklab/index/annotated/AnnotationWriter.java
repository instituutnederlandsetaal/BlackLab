package nl.inl.blacklab.index.annotated;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.util.BytesRef;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import nl.inl.blacklab.analysis.AddIsPrimaryValueToPayloadFilter;
import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.index.BLFieldType;
import nl.inl.blacklab.index.BLIndexObjectFactory;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.util.CollUtil;

/**
 * An annotation in an annotated field (while indexing). See AnnotatedFieldWriter for details.
 */
public class AnnotationWriter {

    private final AnnotatedFieldWriter fieldWriter;

    private final AnnotationSensitivities sensitivitySetting;

    protected boolean includeOffsets;

    /**
     * Term values for this annotation.
     */
    protected List<String> values = new ArrayList<>();

    /**
     * Token position increments. This allows us to index multiple terms at a single
     * token position (just set the token increments of the additional tokens to 0).
     */
    protected MutableIntList increments = new IntArrayList();

    /**
     * Payloads for this annotation, if any.
     */
    protected List<BytesRef> payloads = null;

    /**
     * Position of the last value added
     */
    protected int lastValuePosition = -1;

    /** If this is the _relation annotation: unique id to be assigned to the next relation added.
     * This is stored in the payload and can be used to look up information about the relation, such as
     * attributes.
     */
    private int nextRelationId = 0;

    /**
     * A annotation may be indexed in different ways (sensitivities). This specifies
     * names and filters for each way.
     */
    private final Map<String, TokenFilterAdder> sensitivities = new HashMap<>();

    /** The main sensitivity (the one that gets character offsets if desired) */
    private String mainSensitivity;

    /** The annotation name */
    private final String annotationName;

    /** The annotation descriptor */
    private Annotation annotation;

    /** Does this annotation get its own forward index? */
    private boolean hasForwardIndex = true;

    /** Should the payload indicate whether this token is primary or secondary? (see PayloadUtils) */
    private boolean needsPrimaryValuePayload = false;

    public String mainSensitivity() {
        return mainSensitivity;
    }

    public void setAnnotation(Annotation annotation) {
        this.annotation = annotation;
    }

    public Annotation annotation() {
        return annotation;
    }

    public boolean includeOffsets() {
        return includeOffsets;
    }

    /**
     * Construct a AnnotationWriter object
     *
     * @param fieldWriter fieldwriter for our field
     * @param name annotation name
     * @param sensitivity ways to index this annotation, with respect to case- and
     *            diacritics-sensitivity.
     * @param includeOffsets whether to include character offsets in the main
     *            sensitivity variant
     * @param includePayloads will this annotation include payloads?
     * @param needsPrimaryValuePayloads should payloads indicate whether this is a primary value? (forces payloads)
     */
    public AnnotationWriter(AnnotatedFieldWriter fieldWriter, String name, AnnotationSensitivities sensitivity,
            boolean includeOffsets, boolean includePayloads, boolean needsPrimaryValuePayloads) {
        super();
        this.fieldWriter = fieldWriter;
        annotationName = name;
        this.sensitivitySetting = sensitivity;
        if (fieldWriter.field() != null) {
            annotation = fieldWriter.field().annotation(annotationName);
        }

        mainSensitivity = null;
        if (sensitivity != AnnotationSensitivities.ONLY_INSENSITIVE) {
            // Add sensitive sensitivity
            mainSensitivity = MatchSensitivity.SENSITIVE.luceneFieldSuffix();
            sensitivities.put(mainSensitivity, null);
        }
        if (sensitivity != AnnotationSensitivities.ONLY_SENSITIVE) {
            // Add insensitive sensitivity
            sensitivities.put(MatchSensitivity.INSENSITIVE.luceneFieldSuffix(), new DesensitizerAdder(true, true));
            if (mainSensitivity == null)
                mainSensitivity = MatchSensitivity.INSENSITIVE.luceneFieldSuffix();
        }
        if (sensitivity == AnnotationSensitivities.CASE_AND_DIACRITICS_SEPARATE) {
            // Add case-insensitive and diacritics-insensitive sensitivity
            sensitivities.put(MatchSensitivity.CASE_INSENSITIVE.luceneFieldSuffix(), new DesensitizerAdder(true, false));
            sensitivities.put(MatchSensitivity.DIACRITICS_INSENSITIVE.luceneFieldSuffix(), new DesensitizerAdder(false, true));
        }

        this.includeOffsets = includeOffsets;
        this.needsPrimaryValuePayload = needsPrimaryValuePayloads;
        if (!includePayloads && needsPrimaryValuePayloads)
            includePayloads = true;
        if (includePayloads)
            payloads = new ArrayList<>();
    }

    public Collection<String> sensitivitySuffixes() {
        return Collections.unmodifiableCollection(sensitivities.keySet());
    }

    TokenStream tokenStream(String sensitivityName, IntList startChars, IntList endChars) {
        TokenStream ts;
        if (includeOffsets) {
            ts = new TokenStreamWithOffsets(values, increments, startChars, endChars);
        } else {
            ts = new TokenStreamFromList(values, increments, payloads);
        }
        TokenFilterAdder filterAdder = sensitivities.get(sensitivityName);
        if (filterAdder != null)
            return filterAdder.addFilters(ts);

        if (hasForwardIndex && needsPrimaryValuePayload) {
            // When writing the segment, we'll need to know which of our values was our "primary"
            // value (the original word, to be used in concordances, sort, group, etc., to be stored
            // in the forward index) and which were the secondary ones (e.g. stemmed, synonyms).
            // This information is encoded into the payloads. When using payloads for an annotation that
            // has these indicator, you should check if the indicator is there and skip it (see PayloadUtils).
            ts = new AddIsPrimaryValueToPayloadFilter(ts);
        }

        return ts;
    }

    BLFieldType getFieldType(BLIndexObjectFactory indexObjectFactory, String sensitivityName) {
        boolean isMainSensitivity = sensitivityName.equals(mainSensitivity);

        // Main sensitivity of main annotation gets character offsets
        // (if it's the main annotation of an annotated field)
        boolean offsets = includeOffsets && isMainSensitivity;

        // Main sensitivity of main annotation may get content store
        return indexObjectFactory.fieldTypeAnnotationSensitivity(offsets, hasForwardIndex && isMainSensitivity);
    }

    public void addToDoc(BLInputDocument doc, String annotatedFieldName, IntList startChars,
            IntList endChars) {
        for (String sensitivityName : sensitivities.keySet()) {
            BLFieldType fieldType = getFieldType(doc.indexObjectFactory(), sensitivityName);
            TokenStream tokenStream = tokenStream(sensitivityName, startChars, endChars);
            String luceneFieldName = AnnotatedFieldNameUtil.annotationField(annotatedFieldName,
                    annotationName, sensitivityName);
            doc.addAnnotationField(luceneFieldName, tokenStream, fieldType);
        }
    }

    public List<String> values() {
        return Collections.unmodifiableList(values);
    }

    public List<Integer> positionIncrements() {
        return CollUtil.toJavaList(increments);
    }

    public int lastValuePosition() {
        return lastValuePosition;
    }

    public String name() {
        return annotationName;
    }

    public boolean hasForwardIndex() {
        return hasForwardIndex;
    }

    public void setHasForwardIndex(boolean b) {
        hasForwardIndex = b;
    }

    /**
     * Add a value to the annotation.
     *
     * @param value value to add
     * @return position of the token added
     */
    final public int addValue(String value) {
        return addValue(value, 1, null);
    }

    /**
     * Add a value to the annotation.
     *
     * @param value the value to add
     * @param increment number of tokens distance from the last token added
     * @return position of the token added
     */
    public int addValue(String value, int increment) {
        return addValue(value, increment, null);
    }

    /**
     * Add a value to the annotation.
     *
     * @param value the value to add
     * @param increment number of tokens distance from the last token added
     * @param payload payload to store (or null if none)
     * @return position of the token added
     */
    public int addValue(String value, int increment, BytesRef payload) {
        return addValueAtPosition(value, lastValuePosition + increment, payload);
    }

    /**
     * Add a value to the annotation at a specific position.
     *
     * Please note that if you add a value beyond the current position, the next
     * call to addValue() will add from this new position! This is not an issue if
     * you add a value at a lower position (that operation doesn't change the
     * current last token position used for addValue()).
     *
     * @param value the value to add
     * @param position the position to put it at
     * @param payload payload (or null if none)
     * @return new position of the last token, in case it changed.
     */
    public int addValueAtPosition(String value, int position, BytesRef payload) {
        assert position >= 0;

        if (fieldWriter.getIndexType() == BlackLabIndex.IndexType.INTEGRATED &&
                AnnotatedFieldNameUtil.isRelationAnnotation(annotationName) &&
                !value.isEmpty() && !value.contains("\u0001")) {

            // This is the _relation annotation in the integrated index format, but not the right sort of value
            // is being indexed. This is likely an old DocIndexer that wasn't updated to use indexInlineTag.
            // Warn the user.
            System.err.println("===== WARNING: your DocIndexer is using AnnotationWriter.addValuePosition() to index " +
                    "inline tags. To work properly with the new index format, update it to use " +
                    "AnnotationWriter.indexInlineTag() instead. Until you do this, inline tags will not work.");

        }

        // Make sure we don't keep duplicates of strings in memory, but re-use earlier instances.
        value = value.intern();

        if (position >= lastValuePosition) {
            // Beyond the last position; just add at the end.
            int increment = position - lastValuePosition;

            // Special case: if previous value was the empty string, there was no payload and position increment is 0,
            // replace the previous value. This is convenient to keep all the annotations synched
            // up while indexing (by adding an empty string if we don't have a value for a
            // annotation), while still being able to add a value to this position later (for example,
            // when we encounter an XML close tag. Note that we don't do this if we store character offsets, or we
            // lose the offsets for some positions.
            int lastIndex = values.size() - 1;
            if (lastIndex >= 0 && values.get(lastIndex).length() == 0 && !includeOffsets &&
                    (!hasPayload() || payloads.get(lastIndex) == null)) {
                // Change the last value and its position increment
                values.set(lastIndex, value);
                if (hasPayload())
                    payloads.set(lastIndex, payload);
                if (increment > 0)
                    increments.set(lastIndex, increments.get(lastIndex) + increment);
            } else {
                // Just add the new value
                values.add(value);
                if (hasPayload())
                    payloads.add(payload);
                increments.add(increment);
            }
            lastValuePosition += increment; // keep track of position of last token

        } else {
            // Before the last position.
            // Find the index where the value should be inserted.
            int curPos = this.lastValuePosition;
            int n = 0; // if we go through the whole loop without breaking out, value should go at position 0
            for (int i = values.size() - 1; i >= 0; i--) {
                if (curPos <= position) {
                    // Value should be inserted after this index.
                    n = i + 1;
                    break;
                }
                curPos -= increments.get(i); // go to previous value position
            }
            insertValueAtIndex(n, value, position - curPos, payload);
        }

        return lastValuePosition;
    }

    /**
     * Add a value at a specific token position.
     *
     * @param index             index in the arrays where this value goes
     * @param value             value to add
     * @param positionIncrement position increment to store (and also adjust next position increment)
     * @param payload           payload to add (or null if no payload)
     */
    private void insertValueAtIndex(int index, String value, int positionIncrement, BytesRef payload) {
        values.add(index, value);
        if (positionIncrement < 0)
            throw new RuntimeException("ERROR insertValueAtPosition(" + index + ", " + value + ", " + positionIncrement + ", payload): Negative position increment!");
        increments.addAtIndex(index, positionIncrement);
        if (hasPayload())
            payloads.add(index, payload);
        // Do we need to adjust the position increment of the next value?
        if (increments.size() > index + 1 && positionIncrement > 0) {
            // Inserted value wasn't the last value, so the
            // increment for the value after this is now wrong;
            // correct it.
            int newPosIncr = increments.get(index + 1) - positionIncrement;
            if (newPosIncr < 0)
                throw new RuntimeException("ERROR insertValueAtPosition(" + index + ", " + value + ", " + positionIncrement + ", payload): Next token got a negative posIncrement: " + newPosIncr);
            increments.set(index + 1, newPosIncr);
        }
    }

    public void addPayload(BytesRef payload) {
        payloads.add(payload);
    }

    public int lastValueIndex() {
        return values.size() - 1;
    }

    public void setPayloadAtIndex(int i, BytesRef payload) {
        payloads.set(i, payload);
    }

    public void clear() {
        lastValuePosition = -1;
        // Don't reuse buffers, reclaim memory so we don't run out
        values = new ArrayList<>();
        increments = new IntArrayList();
        if (payloads != null) {
            payloads = new ArrayList<>();
        }
    }

    public boolean hasPayload() {
        return payloads != null;
    }

    public AnnotatedField field() {
        return fieldWriter.field();
    }

    @Override
    public String toString() {
        return "AnnotationWriter(" + field() + "." + annotationName + ")";
    }

    public AnnotationSensitivities getSensitivitySetting() {
        return sensitivitySetting;
    }

    /**
     * Index an inline tag in this annotation.
     *
     * Writes the tags differently depending on the index type.
     *
     * If endPos is not known yet, we don't write the payload yet; it will
     * have to be written later, at the index returned by this method.
     *
     * @param tagName the tag name
     * @param startPos the start position of the tag
     * @param endPos the end position of the tag, or -1 if we don't know it yet
     * @param attributes the tag attributes
     * @param indexType index type (external files or integrated)
     * @return index the tag was stored at (so we can add payload later if needed).
     *         Note that if this is a negative value, it is the index of the second
     *         term indexed for this tag. We should update the payloads of both later.
     */
    public int indexInlineTag(String tagName, int startPos, int endPos,
            Map<String, String> attributes, BlackLabIndex.IndexType indexType, int relationId) {
        RelationInfo matchInfo = RelationInfo.create(false, startPos, startPos,
                endPos, endPos, relationId);
        String fullRelationType;
        fullRelationType = indexType == BlackLabIndex.IndexType.EXTERNAL_FILES ?
                tagName :
                RelationUtil.fullType(RelationUtil.CLASS_INLINE_TAG, tagName);
        return indexRelation(fullRelationType, attributes, indexType, matchInfo);
    }

    public void indexRelation(String fullRelationType, boolean onlyHasTarget, int sourceStartPos, int sourceEnd,
            int targetStart, int targetEnd, Map<String, String> attributes, BlackLabIndex.IndexType indexType) {
        RelationInfo matchInfo = RelationInfo.create(onlyHasTarget, sourceStartPos, sourceEnd, targetStart, targetEnd,
                getNextRelationId());

        // We index relations at the source start position. This way, we don't have to sort
        // if we need the source (which is what we usually use), but we will have to sort
        // for the target or full span (because target position can be before source).
        // (we also might not even need to decode the payload if we ONLY need the source
        //  start position)
        indexRelation(fullRelationType, attributes, indexType, matchInfo);
    }

    private int indexRelation(String fullRelationType, Map<String, String> attributes,
            BlackLabIndex.IndexType indexType, RelationInfo relationInfo) {
        int tagIndexInAnnotation;
        BytesRef payload;
        if (indexType == BlackLabIndex.IndexType.EXTERNAL_FILES) {
            if (relationInfo.getType() != MatchInfo.Type.INLINE_TAG) {
                // Classic external index doesn't support relations; ignore
                return relationInfo.getSourceStart();
            }
            // classic external index; tag name and attributes are indexed separately
            payload = relationInfo.getSpanEnd() >= 0 ?
                    PayloadUtils.inlineTagPayload(relationInfo.getSpanStart(), relationInfo.getSpanEnd(),
                            BlackLabIndex.IndexType.EXTERNAL_FILES, getNextRelationId()) :
                    null;
            addValueAtPosition(fullRelationType, relationInfo.getSourceStart(), payload);
            tagIndexInAnnotation = lastValueIndex();
            for (Map.Entry<String, String> e: attributes.entrySet()) {
                String term = RelationUtil.tagAttributeIndexValue(e.getKey(), e.getValue(),
                        BlackLabIndex.IndexType.EXTERNAL_FILES);
                addValueAtPosition(term, relationInfo.getSourceStart(), null);
            }
        } else {
            // integrated index; everything is indexed as a single term
            // We only add the payload if we know the complete relation info;
            // for inline tags, we'll only know it when we encounter the closing tag,
            // and we'll add the payload then.
            payload = relationInfo.hasTarget() ? relationInfo.serialize() : null;
            addValueAtPosition(RelationUtil.indexTerm(fullRelationType, attributes, false),
                    relationInfo.getSourceStart(), payload);
            boolean indexedTwice = false;
            if (attributes != null && !attributes.isEmpty()) {
                // Also index a version without attributes. We'll use this for faster search if we don't filter on
                // attributes. The indexed term will be tagged as an optimization term, so it won't be counted when we
                // determine statistics.
                // NOTE: this should always be indexed AFTER the version with attributes; that version will be
                //       used to store the attribute info when writing the index files. When we encounter this second
                //       version, we've already seen any attributes the relation has and can just skip this.
                addValueAtPosition(RelationUtil.indexTerm(fullRelationType, null, true),
                        relationInfo.getSourceStart(), payload);
                indexedTwice = true;
            }

            tagIndexInAnnotation = lastValueIndex();
            if (indexedTwice) {
                // HACK so we will be able to update both payloads if this is an open tag and we have yet to
                // encounter the close tag
                tagIndexInAnnotation = -tagIndexInAnnotation;
            }
        }
        return tagIndexInAnnotation;
    }

    public int getNextRelationId() {
        return nextRelationId++;
    }
}
