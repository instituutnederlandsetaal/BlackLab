package nl.inl.blacklab.search.lucene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A (variable-size) list of captured relations, e.g. all relations in a sentence.
 */
public class RelationListInfo extends MatchInfo implements RelationLikeInfo {

    public static RelationListInfo create(List<RelationInfo> relations, String overriddenField) {
        return new RelationListInfo(relations, overriddenField);
    }

    List<RelationInfo> relations;

    private final Integer spanStart;

    private final Integer spanEnd;

    private RelationListInfo(List<RelationInfo> relations, String overriddenField) {
        super(overriddenField);
        this.relations = new ArrayList<>(relations);
        spanStart = relations.stream().map(RelationInfo::getSpanStart).min(Integer::compare).orElse(-1);
        spanEnd = relations.stream().map(RelationInfo::getSpanEnd).max(Integer::compare).orElse(-1);
    }

    public int getSpanStart() {
        return spanStart;
    }

    public int getSpanEnd() {
        return spanEnd;
    }

    @Override
    public Type getType() {
        return Type.LIST_OF_RELATIONS;
    }

    @Override
    public String toString(String defaultField) {
        return "listrel(" + relations.stream().map(r -> r.toString(defaultField)).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public int compareTo(MatchInfo o) {
        if (o instanceof RelationListInfo rli) {
            // compare the items in the list of relations
            int n = Integer.compare(relations.size(), rli.relations.size());
            if (n != 0)
                return n;
            for (int i = 0; i < relations.size(); i++) {
                n = relations.get(i).compareTo(rli.relations.get(i));
                if (n != 0)
                    return n;
            }
            return 0;
        }
        return super.compareTo(o);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RelationListInfo that = (RelationListInfo) o;
        return Objects.equals(relations, that.relations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relations);
    }

    public List<RelationInfo> getRelations() {
        return Collections.unmodifiableList(relations);
    }

    @Override
    public boolean isRoot() {
        return relations.stream().allMatch(RelationInfo::isRoot);
    }

    @Override
    public int getSourceStart() {
        return relations.stream()
                .filter(r -> !r.isRoot())
                .map(RelationInfo::getSourceStart)
                .min(Comparator.naturalOrder())
                .orElse(0);
    }

    @Override
    public int getSourceEnd() {
        return relations.stream()
                .filter(r -> !r.isRoot())
                .map(RelationInfo::getSourceEnd)
                .max(Comparator.naturalOrder())
                .orElse(0);
    }

    @Override
    public int getTargetStart() {
        return relations.stream()
                .map(RelationInfo::getTargetStart)
                .min(Comparator.naturalOrder())
                .orElse(0);
    }

    @Override
    public int getTargetEnd() {
        return relations.stream()
                .map(RelationInfo::getTargetEnd)
                .max(Comparator.naturalOrder())
                .orElse(0);
    }

    @Override
    public String getField() {
        return relations.isEmpty() ? super.getField() : relations.get(0).getField();
    }

    @Override
    public String getTargetField() {
        return relations.isEmpty() ? null : relations.get(0).getTargetField();
    }

    @Override
    public boolean isCrossFieldRelation() {
        String targetField = getTargetField();
        return targetField != null && !targetField.equals(getField());
    }
}
