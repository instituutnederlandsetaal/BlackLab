package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.index.LeafReaderContext;

import it.unimi.dsi.fastutil.BigList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexSegmentReader;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.forwardindex.TermsSegment;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureRelationsBetweenSpans;
import nl.inl.blacklab.search.results.EphemeralHit;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.HitsSimple;
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

    /** [GLOBAL] forward index for annotation */
    protected AnnotationForwardIndex globalAnnotationForwardIndex;

    /** [GLOBAL] Terms for our annotation */
    protected Terms globalAnnotationTerms;

    /** [SEGMENT] Lucene field containing the forward index */
    private String segmentLuceneField;

    /** [SEGMENT] forward index */
    private ForwardIndexSegmentReader segmentForwardIndex;

    /** [SEGMENT] terms for our annotation */
    private TermsSegment segmentAnnotationTerms;

    /** Stores the relevant context tokens for each hit index */
    protected BigList<int[]> contextTermId;

    /** Stores the sort order for the relevant context tokens for each hit index */
    protected BigList<int[]> contextSortOrder;

    protected Annotation annotation;

    protected final MatchSensitivity sensitivity;

    protected final String name;

    protected final String serializeName;

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
    protected int[] getForeignHitStartEnd(Hit hit, String fieldName) {
        assert hit != null : "Need a hit";
        MatchInfo[] matchInfos = hit.matchInfos();
        if (matchInfos == null)
            return new int[] { 0, 0 };
        int[] startEnd = { Integer.MAX_VALUE, Integer.MIN_VALUE };
        for (int i = 0; i < matchInfos.length; i++) {
            MatchInfo mi = matchInfos[i];
            if (mi == null)
                continue;
            if (mi.getField().equals(fieldName) && mi.getType() == MatchInfo.Type.SPAN &&
                    hits.matchInfoDefs().get(i).getName().endsWith(
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
     * @param index index to use
     * @param annotation annotation to use (or annotation name, if overrideField is given)
     * @param overrideField field to use instead of the annotation's field, or null to return annotation unchanged
     * @return the annotation to use
     */
    protected static Annotation annotationOverrideField(BlackLabIndex index, Annotation annotation, AnnotatedField overrideField) {
        if (overrideField != null && !overrideField.equals(annotation.field().name())) {
            // Switch fields if necessary (e.g. for match info in a different annotated field, in a parallel corpus)
            annotation = overrideField.annotation(annotation.name());
        }
        return annotation;
    }

    /** Some context properties, e.g. context before, can get an extra parameter (number of tokens).
     *
     * This method should deserialize that parameter if applicable.
     *
     * @param param extra parameter to deserialize
     */
    void deserializeParam(String param) {
        // just ignore extra param by default when deserializing
    }

    /** Copy constructor, used to create a copy with e.g. a different Hits object. */
    protected HitPropertyContextBase(HitPropertyContextBase prop, HitsSimple hits, LeafReaderContext lrc, boolean invert, AnnotatedField overrideField) {
        super(prop, hits, lrc, invert);
        this.index = hits == null ? prop.index : hits.index();
        this.annotation = annotationOverrideField(prop.index, prop.annotation, overrideField);
        this.sensitivity = prop.sensitivity;
        this.name = prop.name;
        this.serializeName = prop.serializeName;
        this.compareInReverse = prop.compareInReverse;
        initForwardIndex();
        if (prop.hits == hits) {
            // Same hits object; reuse context arrays
            contextTermId = prop.contextTermId;
            contextSortOrder = prop.contextSortOrder;
        }
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

    void initForwardIndex() {
        if (lrc != null) {
            segmentLuceneField = annotation.forwardIndexSensitivity().luceneField();
            segmentForwardIndex = BlackLabIndexIntegrated.forwardIndex(lrc);
            segmentAnnotationTerms = segmentForwardIndex.terms(annotation);
        } else {
            globalAnnotationForwardIndex = index.annotationForwardIndex(annotation);
            globalAnnotationTerms = globalAnnotationForwardIndex.terms();
        }
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
        final long size = hits.size();
        contextTermId = new ObjectBigArrayBigList<>(size);
        contextSortOrder = new ObjectBigArrayBigList<>(size);
        int prevDoc = size == 0 ? -1 : hits.doc(0);
        long firstHitInCurrentDoc = 0;
        if (size > 0) {
            for (long i = 1; i < size; ++i) { // start at 1: variables already have correct values for primed for hit 0
                final int curDoc = hits.doc(i);
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
        contextTermId = contextSortOrder = null;
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
            hits.getEphemeral(hitIndex, hit);
            setStartEnd.setStartEnd(starts, ends, j, hit);
        }

        if (globalAnnotationForwardIndex != null) {
            // [GLOBAL] Retrieve term ids
            List<int[]> listTermIds = new ArrayList<>();
            LeafReaderContext lrc = index.getLeafReaderContext(docId);
            ForwardIndexSegmentReader forwardIndex = BlackLabIndexIntegrated.forwardIndex(lrc);
            String luceneField = globalAnnotationForwardIndex.annotation().forwardIndexSensitivity().luceneField();
            int segmentDocId = docId - lrc.docBase;
            Terms terms = globalAnnotationForwardIndex.terms();
            for (int[] snippet: forwardIndex.retrieveParts(luceneField, segmentDocId, starts, ends)) {
                listTermIds.add(terms.segmentIdsToGlobalIds(lrc.ord, snippet));
            }

            // Also determine sort orders so we don't have to do that for each compare
            for (int[] termIds: listTermIds) {
                if (compareInReverse)
                    ArrayUtils.reverse(termIds);
                contextTermId.add(termIds);
                int[] sortOrder = new int[termIds.length];
                globalAnnotationTerms.toSortOrder(termIds, sortOrder, sensitivity);
                contextSortOrder.add(sortOrder);
            }
        } else {
            // [SEGMENT] Retrieve term ids
            List<int[]> listTermIds = segmentForwardIndex.retrieveParts(segmentLuceneField, docId,
                    starts, ends);
            // Also determine sort orders so we don't have to do that for each compare
            for (int[] termIds: listTermIds) {
                if (compareInReverse)
                    ArrayUtils.reverse(termIds);
                contextTermId.add(termIds);
                int[] sortOrder = new int[termIds.length];
                segmentAnnotationTerms.toSortOrder(termIds, sortOrder, sensitivity);
                contextSortOrder.add(sortOrder);
            }
        }
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueContext.class;
    }

    @Override
    public PropertyValueContext get(long hitIndex) {
        if (contextTermId == null)
            fetchContext();
        return new PropertyValueContextWords(index, annotation, sensitivity,
                contextTermId.get(hitIndex), contextSortOrder.get(hitIndex), compareInReverse);
    }

    @Override
    public int compare(long indexA, long indexB) {
        if (contextTermId == null)
            fetchContext();
        int[] ca = contextSortOrder.get(indexA);
        int[] cb = contextSortOrder.get(indexB);
        return reverse ?
                Arrays.compare(cb, ca) :
                Arrays.compare(ca, cb);
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
