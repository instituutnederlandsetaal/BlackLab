package nl.inl.blacklab.resultproperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on an attribute of a matched span.
 *
 * E.g. for a query like <tt>"water" within &lt;speech speaker="Obama" /&gt;</tt>, you can capture the
 * <tt>speaker</tt> attribute of the <tt>speech</tt> element.
 */
public class HitPropertyAlignments extends HitProperty {

    public static final String ID = "alignments";

    private List<Integer> targetHitGroupIndexes = Collections.emptyList();

    static HitPropertyAlignments deserializeProp(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        return new HitPropertyAlignments();
    }

    private Hits hits;

    HitPropertyAlignments(HitPropertyAlignments prop, Hits hits, boolean invert) {
        super();
        this.hits = hits;
        reverse = prop.reverse ? !invert : invert;

        // Find indexes of foreign hits in matchInfo
        if (hits != null) {
            targetHitGroupIndexes = hits.matchInfoDefs().currentListFiltered(MatchInfo.Def::isForeignHit).stream()
                    .map(MatchInfo.Def::getIndex)
                    .collect(Collectors.toList());
        }
    }

    public HitPropertyAlignments() {
        targetHitGroupIndexes = Collections.emptyList();
    }

    @Override
    protected boolean sortDescendingByDefault() {
        return true; // default to sorting in reverse (most alignments first)
    }

    @Override
    public HitProperty copyWith(Hits newHits, boolean invert) {
        return new HitPropertyAlignments(this, newHits, invert);
    }

    @Override
    public boolean isDocPropOrHitText() {
        // we use match info attributes which are already part of the hit; no extra context needed
        return true;
    }

    @Override
    public PropertyValue get(long hitIndex) {
        MatchInfo[] matchInfos = hits.get(hitIndex).matchInfo();
        int n = 0;
        if (matchInfos != null) {
            // Count the number of alignments found for this hit
            for (int targetHitGroupIndex: targetHitGroupIndexes) {
                MatchInfo targetMatchInfo = targetHitGroupIndex < matchInfos.length ? matchInfos[targetHitGroupIndex] : null;
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
        return Objects.equals(targetHitGroupIndexes, that.targetHitGroupIndexes) && Objects.equals(hits,
                that.hits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), targetHitGroupIndexes, hits);
    }
}
