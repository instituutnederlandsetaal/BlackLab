package nl.inl.blacklab.resultproperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.results.HitsForHitProps;

/**
 * A hit property for grouping on an attribute of a matched span.
 *
 * E.g. for a query like <tt>"water" within &lt;speech speaker="Obama" /&gt;</tt>, you can capture the
 * <tt>speaker</tt> attribute of the <tt>speech</tt> element.
 */
public class HitPropertyAlignments extends HitProperty {

    public static final String ID = "alignments";

    private List<Integer> targetHitGroupIndexes = null;

    static HitPropertyAlignments deserializeProp(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        return new HitPropertyAlignments();
    }

    HitPropertyAlignments(HitPropertyAlignments prop, HitsForHitProps hits, boolean invert) {
        super(prop, hits, invert);
    }

    private synchronized List<Integer> getTargetHitGroupIndexes() {
        if (targetHitGroupIndexes == null) {
            // We look this up dynamically, because we can only do this after all hits have been fetched
            // (actually, after all Spans have been initialized and therefore all capture groups registered)
            if (this.hits != null) {
                // Find indexes of foreign hits in matchInfo
                targetHitGroupIndexes = hits.matchInfoDefs().currentListFiltered(MatchInfo.Def::isForeignHit).stream()
                        .map(MatchInfo.Def::getIndex)
                        .toList();
            } else {
                targetHitGroupIndexes = Collections.emptyList();
            }
        }
        return targetHitGroupIndexes;
    }

    public HitPropertyAlignments() {
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueInt.class;
    }

    @Override
    protected boolean sortDescendingByDefault() {
        return true; // default to sorting in reverse (most alignments first)
    }

    @Override
    public HitProperty copyWith(HitsForHitProps newHits, boolean invert) {
        return new HitPropertyAlignments(this, newHits, invert);
    }

    @Override
    public boolean isDocPropOrHitText() {
        // we use match info attributes which are already part of the hit; no extra context needed
        return true;
    }

    @Override
    public PropertyValue get(long hitIndex) {
        MatchInfo[] matchInfos = hits.matchInfos(hitIndex);
        int n = 0;
        if (matchInfos != null) {
            // NOTE: below conditional should prevent synchronized method call if not necessary.
            //       should be safe because once this thread sees a non-null value, it will stay that way.
            List<Integer> targetHitGroupIndexes1 = targetHitGroupIndexes == null ?
                    getTargetHitGroupIndexes() :
                    targetHitGroupIndexes;
            // Count the number of alignments found for this hit
            for (int targetHitGroupIndex: targetHitGroupIndexes1) {
                MatchInfo targetMatchInfo = MatchInfo.get(matchInfos, targetHitGroupIndex);
                if (targetMatchInfo != null)
                    n++;
            }
        }
        return new PropertyValueInt(n);
    }

    @Override
    public String name() {
        return "alignments";
    }

    @Override
    public String serialize() {
        return "alignments";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        HitPropertyAlignments that = (HitPropertyAlignments) o;
        return Objects.equals(targetHitGroupIndexes, that.targetHitGroupIndexes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), targetHitGroupIndexes);
    }
}
