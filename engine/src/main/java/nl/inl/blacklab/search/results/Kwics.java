package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;

/** KWICs ("key words in context") for a list of hits.
 *
 * Instances of this class are immutable.
 */
public class Kwics {
    
    /** The KWIC data. */
    private final Map<Hit, Kwic> kwics;

    /** KWICs in other fields (for parallel corpora), or null if none. */
    private Map<Hit, Map<String, Kwic>> foreignKwics = null;

    public Kwics(Hits hits, ContextSize contextSize) {
        if (contextSize.before() < 0 || contextSize.after() < 0)
            throw new IllegalArgumentException("contextSize cannot be negative: " + contextSize);
    
        // Get the concordances
        kwics = retrieveKwics(hits, contextSize, hits.field());

        // Get the concordances for other fields (for parallel corpora), if there are any
        foreignKwics = retrieveForeignKwics(hits, contextSize);
    }

    private Map<Hit, Map<String, Kwic>> retrieveForeignKwics(Hits hits, ContextSize contextSize) {
        Map<Hit, Map<String, Kwic>> foreignKwics = null;
        Map<String, List<AnnotationForwardIndex>> afisPerField = new HashMap<>();
        String defaultField = hits.field().name();
        for (Iterator<EphemeralHit> it = hits.ephemeralIterator(); it.hasNext(); ) {
            EphemeralHit hit = it.next();
            Map<String, int[]> minMaxPerField = null; // start and end of the "foreign match"
            MatchInfo[] matchInfo = hit.matchInfo();
            if (matchInfo != null) {
                for (MatchInfo mi: matchInfo) {
                    if (mi == null)
                        continue; // not captured for this hit
                    minMaxPerField = updateMinMaxForMatchInfo(hits.index(), mi, defaultField, minMaxPerField,
                            afisPerField);
                }

                if (minMaxPerField != null) {
                    Map<String, Kwic> kwics = new HashMap<>();
                    for (Map.Entry<String, int[]> e: minMaxPerField.entrySet()) {
                        int[] minMax = e.getValue();
                        int matchStart = minMax[0];
                        int matchEnd = minMax[1];
                        int snippetStart = Math.max(0, Math.min(minMax[0], matchStart - contextSize.before()));
                        int snippetEnd = Math.max(minMax[1], matchEnd + contextSize.after());
                        if (snippetEnd - snippetStart > contextSize.getMaxSnippetLength()) {
                            snippetEnd = matchStart + contextSize.getMaxSnippetLength() / 2;
                            snippetStart = matchStart - contextSize.getMaxSnippetLength() / 2;
                        }

                        String field = e.getKey();
                        List<AnnotationForwardIndex> afis = afisPerField.get(field);
                        Hits singleHit = Hits.singleton(hits.queryInfo(), hit.doc(), matchStart, matchEnd);
                        ContextSize thisContext = ContextSize.get(matchStart - snippetStart, snippetEnd - matchEnd, true,
                                contextSize.getMaxSnippetLength());
                        Contexts.makeKwicsSingleDocForwardIndex(singleHit, afis, thisContext,
                                (__, kwic) -> kwics.put(field, kwic));
                    }
                    if (foreignKwics == null)
                        foreignKwics = new HashMap<>();
                    foreignKwics.put(hit.toHit(), kwics);
                }
            }
        }
        return foreignKwics;
    }

    /**
     * Determine minimum and maximum position for each foreign field.
     *
     * The min/max positions depend on match info in the foreign field. For cross-field relations,
     * that may just be the target span.
     *
     * Also retrieve AnnotationForwardIndex for each foreign field.
     *
     * @param index the index
     * @param mi match info to update min/max for
     * @param defaultField default (source / "non-foreign") field for this query
     * @param minMaxPerField map of min/max positions per field, or null if no foreign fields have been seen yet
     * @param afisPerField the AnnotationForwardIndex for each foreign field we've seen will be added to this
     * @return updated map of min/max positions per field
     */
    private static Map<String, int[]> updateMinMaxForMatchInfo(BlackLabIndex index, MatchInfo mi, String defaultField,
            Map<String, int[]> minMaxPerField, Map<String, List<AnnotationForwardIndex>> afisPerField) {
        String field = mi.getField();
        boolean isTag = mi.getType() == MatchInfo.Type.INLINE_TAG; // not "real" relations, don't influence foreign hits
        if (!field.equals(defaultField) && !isTag) { // foreign KWICs only
            // By default, just use the match info span
            // (which, in case of a cross-field relation, is the source span)
            minMaxPerField = updateMinMaxPerField(minMaxPerField, field, mi.getSpanStart(), mi.getSpanEnd());
            afisPerField.computeIfAbsent(field, k -> getAnnotationForwardIndexes(
                    index.forwardIndex(index.annotatedField(field))));
        }
        if (mi instanceof RelationInfo && !isTag) {


            // Relation targets (not just sources) should influence field context
            RelationInfo rmi = (RelationInfo) mi;
            String targetField = rmi.getTargetField() == null ? field : rmi.getTargetField();
            if (!targetField.equals(defaultField)) { // foreign KWICs only
                int targetStart = rmi.getTargetStart();
                int targetEnd = rmi.getTargetEnd();
                // Use foreign targets for match position as well
                minMaxPerField = updateMinMaxPerField(minMaxPerField, targetField, targetStart, targetEnd);
                afisPerField.computeIfAbsent(targetField, k ->
                        getAnnotationForwardIndexes(
                                index.forwardIndex(index.annotatedField(targetField))));
            }
        }
        /* else if (mi instanceof RelationListInfo) {

            // SKIP; use the single (auto)capture for the right side of the operator as the min/max of the foreign
            // hit. Don't expand it using the list of overlapping relations, because this might expand it to a full
            // sentence or verse, even if you only matched by one or two words.

            // For a list of relations, update min/max for each relation
            RelationListInfo l = (RelationListInfo) mi;
            for (RelationInfo rmi: l.getRelations()) {
                minMaxPerField = updateMinMaxForMatchInfo(index, rmi, defaultField, minMaxPerField, afisPerField);
            }
        } */
        return minMaxPerField;
    }

    /**
     * Update the min/max positions of the match in each foreign field.
     *
     * @param minMaxPerField for each foreign field, the min/max positions we've seen so far
     * @param field field to update min/max for
     * @param start start position of the match
     * @param end end position of the match
     * @return updated minMaxPerField
     */
    private static Map<String, int[]> updateMinMaxPerField(Map<String, int[]> minMaxPerField, String field,
            int start, int end) {
        // Keep track of the min/max positions of the match in each foreign field
        if (minMaxPerField == null)
            minMaxPerField = new HashMap<>();
        minMaxPerField.compute(field, (k, v) -> {
            if (v == null) {
                return new int[] { start, end };
            } else {
                assert start >= 0 && end >= 0 && v[0] >= 0 && v[1] >= 0 : "All values should be valid";
                v[0] = Math.min(v[0], start);
                v[1] = Math.max(v[1], end);
                return v;
            }
        });
        return minMaxPerField;
    }

    /**
     * Return the KWIC for the specified hit.
     *
     * The first call to this method will fetch the KWICs for all the hits in this
     * Hits object. So make sure to select an appropriate HitsWindow first: don't
     * call this method on a Hits set with >1M hits unless you really want to
     * display all of them in one go.
     *
     * @param h the hit
     * @return KWIC for this hit
     */
    public Kwic get(Hit h) {
        return kwics.get(h);
    }

    /**
     * Return the foreign KWICs for a hit, if any.
     *
     * Foerign KWICs are KWICs in another than the primary field. This only
     * applies to parallel corpora.
     *
     * @param hit the hit
     * @return foreign KWICs for this hit, or null if none
     */
    public Map<String, Kwic> getForeignKwics(Hit hit) {
        return foreignKwics == null ? null : foreignKwics.get(hit);
    }

    /**
     * Retrieve KWICs for a (sub)list of hits.
     *
     * KWICs ("key words in context") are the hit words 'centered' with a
     * certain number of context words around them.
     *
     * @param hits hits to retrieve kwics for
     * @param contextSize how many words around the hit to retrieve
     * @param field field to use for building KWICs
     *
     * @return the KWICs
     */
    private static Map<Hit, Kwic> retrieveKwics(Hits hits, ContextSize contextSize, AnnotatedField field) {
        // Collect FIs, with punct being the first and the main annotation (e.g. word) being the last.
        // (this convention originates from how we write our XML structure)
        ForwardIndex forwardIndex = hits.index().forwardIndex(field);
        List<AnnotationForwardIndex> forwardIndexes = getAnnotationForwardIndexes(forwardIndex);

        // Iterate over hits and fetch KWICs per document
        int lastDocId = -1;
        int firstIndexWithCurrentDocId = 0;
        Map<Hit, Kwic> kwics = new HashMap<>();
        BiConsumer<Hit, Kwic> kwicAdder = (hit, kwic) -> kwics.put(hit, kwic);
        for (int i = 0; i < hits.size(); ++i) {
            int curDocId = hits.doc(i);
            if (lastDocId != -1 && curDocId != lastDocId) {
                // We've reached a new document, so process the previous one
                Contexts.makeKwicsSingleDocForwardIndex(
                        hits.window(firstIndexWithCurrentDocId, i - firstIndexWithCurrentDocId),
                        forwardIndexes, contextSize, kwicAdder);
                firstIndexWithCurrentDocId = i; // remember start of the new document
            }
            lastDocId = curDocId;
        }
        // Last document
        Contexts.makeKwicsSingleDocForwardIndex(
                hits.window(firstIndexWithCurrentDocId, hits.size() - firstIndexWithCurrentDocId),
                forwardIndexes, contextSize, kwicAdder);

        return kwics;
    }

    private static List<AnnotationForwardIndex> getAnnotationForwardIndexes(ForwardIndex forwardIndex) {
        AnnotatedField field = forwardIndex.field();
        List<AnnotationForwardIndex> forwardIndexes = new ArrayList<>(field.annotations().size());
        forwardIndexes.add(forwardIndex.get(field.annotation(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME)));
        for (Annotation annotation: field.annotations()) {
            if (annotation.hasForwardIndex() && !annotation.equals(field.mainAnnotation()) && !annotation.name().equals(
                    AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME)) {
                forwardIndexes.add(forwardIndex.get(annotation));
            }
        }
        forwardIndexes.add(forwardIndex.get(field.mainAnnotation()));
        return forwardIndexes;
    }
}
