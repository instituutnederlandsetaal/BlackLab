package nl.inl.blacklab.resultproperty;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.index.LeafReaderContext;

import it.unimi.dsi.fastutil.BigList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.FieldForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureRelationsBetweenSpans;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hit;
import nl.inl.blacklab.util.PropertySerializeUtil;
import nl.inl.util.ThreadAborter;

/**
 * Base class for HitPropertyHitText, LeftContext, RightContext.
 */
public abstract class HitPropertyContextBase extends HitProperty {

    /** Should we compare context words in reverse?
     *
     * (this actually reverses the arrays containing the context words, and reverses them back
     *  when we construct a displayable value)
     */
    protected boolean compareInReverse;

    /** Lucene field we're looking at */
    private String luceneField;

    /** [SEGMENT/GLOBAL] forward index */
    private AnnotationForwardIndex forwardIndex;

    /** [SEGMENT/GLOBAL] Stores the relevant context tokens for each hit index */
    private BigList<int[]> contextTermId;

    /** [SEGMENT/GLOBAL] Stores the sort order for the relevant context tokens for each hit index */
    private BigList<int[]> contextSortOrder;

    protected Annotation annotation;

    private final MatchSensitivity sensitivity;

    private final String name;

    private final String serializeName;

    protected final BlackLabIndex index;

    /**
     * Find a "foreign hit" in a parallel corpus.
     *
     * A foreign hit is a hit in another field than the one that was searched.
     * E.g. for a query like <pre>"the" ==>nl "de"</pre>, the right side would
     * be the foreign field (likely named <pre>contents__nl</pre>).
     *
     * The start and end are determined by scanning the match info for captures
     * and relations in to the field we're asking about. (for relations, either
     * the source or the target, or both, might be in this field)
     *
     * @param hit our hit
     * @param fieldName foreign field we're interested in
     * @return array of length 2, containing start and end positions for the hit in this field
     */
    protected int[] getForeignHitStartEnd(Hit hit, AnnotatedField field) {
        assert hit != null : "Need a hit";
        MatchInfo[] matchInfos = hit.matchInfos();
        if (matchInfos == null)
            return new int[] { 0, 0 };
        int[] startEnd = { Integer.MAX_VALUE, Integer.MIN_VALUE };
        for (int i = 0; i < matchInfos.length; i++) {
            MatchInfo mi = matchInfos[i];
            if (mi == null)
                continue;
            if (mi.getField().equals(field) && mi.getType() == MatchInfo.Type.SPAN &&
                    context.hits().matchInfoDefs().get(i).getName().endsWith(
                    SpanQueryCaptureRelationsBetweenSpans.TAG_MATCHINFO_TARGET_HIT)) {
                // This is the special target field capture. Adjust the hit boundaries.
                startEnd[0] = Math.min(startEnd[0], mi.getSpanStart());
                startEnd[1] = Math.max(startEnd[1], mi.getSpanEnd());
            }
        }
        // Set fallback values if no match info for this target field was found
        if (startEnd[0] == Integer.MAX_VALUE)
            startEnd[0] = 0;
        if (startEnd[1] == Integer.MIN_VALUE)
            startEnd[1] = 0;
        return startEnd;
    }

    /** Used by fetchContext() to get required context part boundaries for a hit */
    @FunctionalInterface
    public interface StartEndSetter {
        void setStartEnd(int[] starts, int[] ends, int indexInArrays, Hit hit);
    }

    /** Information deserialized from extra parameters.
     *
     * E.g. for before:lemma:s:1, this would be the annotation (lemma), sensitivity (s) and
     * the extra parameter 1, the number of words.
     */
    protected static class DeserializeInfos {
        Annotation annotation;
        MatchSensitivity sensitivity;

        /** One extra parameter: e.g. capture group name or number of tokens (before/after hit) */
        List<String> extraParams;

        public DeserializeInfos(Annotation annotation, MatchSensitivity sensitivity, List<String> extraParams) {
            this.annotation = annotation;
            this.sensitivity = sensitivity;
            this.extraParams = extraParams;
        }

        public String extraParam(int index) {
            return extraParam(index, "");
        }

        public String extraParam(int index, String defaultValue) {
            return index >= extraParams.size() ? defaultValue : extraParams.get(index);
        }

        public int extraIntParam(int index, int defaultValue) {
            try {
                return Integer.parseInt(extraParam(index));
            } catch (NumberFormatException e) {
                // ok, just return default
            }
            return defaultValue;
        }
    }

    protected static DeserializeInfos deserializeInfos(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        Annotation annotation = infos.isEmpty() ? field.mainAnnotation() :
                index.metadata().annotationFromFieldAndName(infos.get(0), field);
        MatchSensitivity sensitivity = infos.size() > 1 ? MatchSensitivity.fromLuceneFieldSuffix(infos.get(1))
                : MatchSensitivity.SENSITIVE;
        List<String> params = infos.size() > 2 ? infos.subList(2, infos.size()) : Collections.emptyList();
        return new DeserializeInfos(annotation, sensitivity, params);
    }

    /**
     * Choose either the specified annotation, or the equivalent in the overrideField if given.
     * @param annotation annotation to use (or annotation name, if overrideField is given)
     * @param overrideField field to use instead of the annotation's field, or null to return annotation unchanged
     * @return the annotation to use
     */
    protected static Annotation annotationOverrideField(Annotation annotation, AnnotatedField overrideField) {
        if (overrideField != null && !overrideField.equals(annotation.field())) {
            // Switch fields if necessary (e.g. for match info in a different annotated field, in a parallel corpus)
            annotation = overrideField.annotation(annotation.name());
        }
        return annotation;
    }

    /** Copy constructor, used to create a copy with e.g. a different Hits object. */
    protected HitPropertyContextBase(HitPropertyContextBase prop, PropContext context, boolean invert, AnnotatedField overrideField) {
        super(prop, context, invert);
        this.index = context.hits() == null ? prop.index : context.hits().index();
        this.annotation = annotationOverrideField(prop.annotation, overrideField);
        this.sensitivity = prop.sensitivity;
        this.name = prop.name;
        this.serializeName = prop.serializeName;
        this.compareInReverse = prop.compareInReverse;
        if (prop.context.hits() == context.hits()) {
            // Same hits object; reuse context arrays
            copyContext(prop);
        } else {
            initForwardIndex();
        }
    }

    void initForwardIndex() {
        luceneField = annotation.forwardIndexSensitivity().luceneField();
        if (isGlobal() || context.toGlobal()) {
            // To produce global term ids, we need the global forward index
            forwardIndex = index.forwardIndex(annotation);
        } else {
            // Use the forward index for the segment we're in
            forwardIndex = FieldForwardIndex.get(context.lrc(), luceneField);
        }
    }

    private void copyContext(HitPropertyContextBase prop) {
        luceneField = prop.luceneField;
        forwardIndex = prop.forwardIndex;
        contextTermId = prop.contextTermId;
        contextSortOrder = prop.contextSortOrder;
    }

    protected HitPropertyContextBase(String name, String serializeName, BlackLabIndex index, Annotation annotation,
            MatchSensitivity sensitivity, boolean compareInReverse) {
        super();
        this.name = name;
        this.serializeName = serializeName;
        this.index = index;
        this.annotation = annotation == null ? index.mainAnnotatedField().mainAnnotation() : annotation;
        this.sensitivity = sensitivity == null ? index.defaultMatchSensitivity() : sensitivity;
        this.compareInReverse = compareInReverse;
        initForwardIndex();
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public MatchSensitivity getSensitivity() {
        return sensitivity;
    }

    @Override
    public String name() {
        return name + ": " + annotation.name();
    }

    public List<String> serializeParts() {
        return List.of(serializeName, annotation.fieldAndAnnotationName(), sensitivity.luceneFieldSuffix());
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropertySerializeUtil.combineParts(serializeParts());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((annotation == null) ? 0 : annotation.hashCode());
        result = prime * result + ((index == null) ? 0 : index.hashCode());
        result = prime * result + ((sensitivity == null) ? 0 : sensitivity.hashCode());
        return result;
    }

    protected synchronized void fetchContext(StartEndSetter setStartEnd) {
        final long size = context.hits().size();
        contextTermId = new ObjectBigArrayBigList<>(size);
        contextSortOrder = new ObjectBigArrayBigList<>(size);
        int prevDoc = size == 0 ? -1 : context.resultDocIdForHit(0);
        long firstHitInCurrentDoc = 0;
        if (size > 0) {
            for (long i = 1; i < size; ++i) { // start at 1: variables already have correct values for primed for hit 0
                final int curDoc = context.resultDocIdForHit(i);
                if (curDoc != prevDoc) {
                    try { ThreadAborter.checkAbort(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InterruptedSearch(e); }
                    // Process hits in preceding document:
                    fetchContextForDoc(setStartEnd, prevDoc, firstHitInCurrentDoc, i);
                    // start a new document
                    prevDoc = curDoc;
                    firstHitInCurrentDoc = i;
                }
            }
            // Process hits in final document
            fetchContextForDoc(setStartEnd, prevDoc, firstHitInCurrentDoc, size);
        }
    }

    @Override
    public synchronized void disposeContext() {
        forwardIndex = null;
        contextTermId = null;
        contextSortOrder = null;
    }

    private synchronized void fetchContextForDoc(StartEndSetter setStartEnd, int docId, long fromIndex, long toIndexExclusive) {
        assert fromIndex >= 0 && toIndexExclusive > 0;
        assert fromIndex < toIndexExclusive;
        if (toIndexExclusive - fromIndex > Constants.JAVA_MAX_ARRAY_SIZE)
            throw new UnsupportedOperationException("Cannot handle more than " + Constants.JAVA_MAX_ARRAY_SIZE + " hits in a single doc");
        int n = (int)(toIndexExclusive - fromIndex);

        // Determine which bits of context to get
        int[] starts = new int[n];
        int[] ends = new int[n];
        EphemeralHit hit = new EphemeralHit();
        long hitIndex = fromIndex;
        for (int j = 0; j < n; ++j, ++hitIndex) {
            context.hits().getEphemeral(hitIndex, hit);
            setStartEnd.setStartEnd(starts, ends, j, hit);
        }

        if (isGlobal() || context.toGlobal()) {
            // [GLOBAL]
            LeafReaderContext lrc = index.getLeafReaderContext(docId);
            AnnotationForwardIndex segmentForwardIndex = getFieldForwardIndex(lrc);
            Terms globalTerms = forwardIndex.terms();
            int segmentDocId = docId - lrc.docBase;
            for (int[] termIds: segmentForwardIndex.retrieveParts(segmentDocId, starts, ends)) {
                globalTerms.convertToGlobalTermIds(lrc, termIds);
                if (compareInReverse)
                    ArrayUtils.reverse(termIds);
                contextTermId.add(termIds);
                int[] sortOrder = new int[termIds.length];
                globalTerms.idsToSortOrder(termIds, sortOrder, sensitivity);
                contextSortOrder.add(sortOrder);
            }
        } else {
            // [SEGMENT] Retrieve term ids
            // Also determine sort orders so we don't have to do that for each compare
            for (int[] termIds: forwardIndex.retrieveParts(docId, starts, ends)) {
                if (compareInReverse)
                    ArrayUtils.reverse(termIds);
                contextTermId.add(termIds);
                int[] sortOrder = new int[termIds.length];
                forwardIndex.terms().idsToSortOrder(termIds, sortOrder, sensitivity);
                contextSortOrder.add(sortOrder);
            }
        }
    }

    private Map<LeafReaderContext, AnnotationForwardIndex> fieldForwardIndexes = new ConcurrentHashMap<>();

    private AnnotationForwardIndex getFieldForwardIndex(LeafReaderContext lrc) {
        return fieldForwardIndexes.computeIfAbsent(lrc,
                key -> FieldForwardIndex.get(key, luceneField));
    }

    private boolean isGlobal() {
        return context.lrc() == null;
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueContextWords.class;
    }

    @Override
    public PropertyValueContextWords get(long hitIndex) {
        ensureContextFetched();
        int[] termIds = contextTermId.get(hitIndex);
        int[] sortPositions = contextSortOrder.get(hitIndex);
        return new PropertyValueContextWords(annotation, sensitivity, forwardIndex.terms(), termIds, sortPositions,
                compareInReverse, context.toGlobal() ? null : context.lrc()
        );
    }

    private boolean isContextAvailable() {
        return contextTermId != null;
    }

    private void ensureContextFetched() {
        // First check without locking (fast path)
        // (if context has been fetched, this will work fine, but it doesn't guard against two threads
        //  fetching the context simultaneously)
        if (!isContextAvailable()) {
            // Now lock and check again (slow path), so we know only one thread fetches the context
            synchronized (this) {
                if (!isContextAvailable()) {
                    fetchContext();
                }
            }
        }
    }

    @Override
    public int compare(long indexA, long indexB) {
        ensureContextFetched();
        int[] ca = contextSortOrder.get(indexA);
        int[] cb = contextSortOrder.get(indexB);
        return reverse ? Arrays.compare(cb, ca) : Arrays.compare(ca, cb);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        HitPropertyContextBase other = (HitPropertyContextBase) obj;
        if (annotation == null) {
            if (other.annotation != null)
                return false;
        } else if (!annotation.equals(other.annotation))
            return false;
        if (index == null) {
            if (other.index != null)
                return false;
        } else if (!index.equals(other.index))
            return false;
        if (sensitivity != other.sensitivity)
            return false;
        return true;
    }
}
